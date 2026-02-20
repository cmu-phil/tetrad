///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;

/**
 * Jan 21, 2020 11:03:09 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class JunctionTreeUpdater implements ManipulatingBayesUpdater {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The BayesIm which this updater modifies.
     */
    private final BayesIm bayesIm;

    /**
     * Stores evidence for all variables.
     */
    private Evidence evidence;

    /**
     * The last manipulated BayesIm.
     */
    private BayesIm manipulatedBayesIm;

    /**
     * The BayesIm after update, if this was calculated.
     */
    private BayesIm updatedBayesIm;

    /**
     * Junction tree (message passing) engine built on the manipulated BayesIm
     * and calibrated to the (hard) observational evidence.
     */
    private JunctionTreeAlgorithm jta;

    /**
     * <p>Constructor for JunctionTreeUpdater.</p>
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public JunctionTreeUpdater(BayesIm bayesIm) {
        this(bayesIm, Evidence.tautology(bayesIm));
    }

    /**
     * <p>Constructor for JunctionTreeUpdater.</p>
     *
     * @param bayesIm  a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @param evidence a {@link edu.cmu.tetrad.bayes.Evidence} object
     */
    public JunctionTreeUpdater(BayesIm bayesIm, Evidence evidence) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(evidence);
    }

    @Override
    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    @Override
    public Graph getManipulatedGraph() {
        return getManipulatedBayesIm().getDag();
    }

    @Override
    public Evidence getEvidence() {
        return new Evidence(this.evidence);
    }

    @Override
    public void setEvidence(Evidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        if (evidence.isIncompatibleWith(this.bayesIm)) {
            throw new IllegalArgumentException("The variable list for the "
                    + "given bayesIm must be compatible with the variable list "
                    + "for this evidence.");
        }

        // Keep the exact object (as other updaters do), but be aware it may be mutable externally.
        this.evidence = evidence;

        // 1) Apply do-manipulations via graph surgery.
        Graph graph = this.bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedPm = createUpdatedBayesPm(manipulatedGraph);

        // Build a BayesIm with the manipulated structure + copied CPTs from the original.
        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedPm);
        copyCptsFromSourceIntoManipulatedIm();

        // 2) For manipulated nodes, overwrite their (now row-0) distribution to match the proposition.
        //    This is CPT surgery, not message passing.
        for (int i = 0; i < evidence.getNumNodes(); i++) {
            if (evidence.isManipulated(i)) {
                double sum = 0.0;
                for (int j = 0; j < evidence.getNumCategories(i); j++) {
                    double v = evidence.getProposition().isAllowed(i, j) ? 1.0 : 0.0;

                    int dst = toManipulatedIndex(i);
                    this.manipulatedBayesIm.setProbability(dst, 0, j, v);

                    sum += v;
                }

                // Normalize row 0 if it’s a multi-valued “allowed set”.
                if (sum > 0.0) {
                    for (int j = 0; j < evidence.getNumCategories(i); j++) {
                        this.manipulatedBayesIm.setProbability(i, 0, j,
                                this.manipulatedBayesIm.getProbability(i, 0, j) / sum);
                    }
                } else {
                    // If manipulation disallows everything, it’s inconsistent.
                    // Keep as all zeros; downstream will yield NaNs.
                }
            }
        }

        // 3) Build the message-passing engine on the manipulated BayesIm and ENTER observational evidence.
        //    NOTE: Kevin’s JTA supports *hard* evidence only (single category).
        this.jta = new JunctionTreeAlgorithm(this.manipulatedBayesIm);

        for (int i = 0; i < evidence.getNumNodes(); i++) {
            if (evidence.isManipulated(i)) {
                continue; // already handled by CPT surgery
            }

            int fixed = getFixedCategoryOrMinusOne(i);
            if (fixed >= 0) {
                // Message passing / calibration step.
//                this.jta.setEvidence(i, fixed);
                int dst = toManipulatedIndex(i);
                this.jta.setEvidence(dst, fixed);
            } else {
                // Not hard evidence (either tautology or multi-valued allowed set).
                // We intentionally do NOT enter anything rather than enter something incorrect.
            }
        }

        // Invalidate cached updated BayesIm.
        this.updatedBayesIm = null;
    }

    /**
     * Copy CPTs from the source BayesIm into manipulatedBayesIm for all nodes,
     * matching nodes (and parents) by name. This is required because some
     * MlBayesIm constructors do NOT reliably copy CPTs when the BayesPm/DAG differs
     * or when MANUAL initialization is used.
     */
    private void copyCptsFromSourceIntoManipulatedIm() {
        BayesIm src = this.bayesIm;
        BayesIm dst = this.manipulatedBayesIm;

        for (int dstNode = 0; dstNode < dst.getNumNodes(); dstNode++) {
            String name = dst.getNode(dstNode).getName();

            Node srcNodeObj = src.getNode(name);
            if (srcNodeObj == null) {
                throw new IllegalStateException("Node '" + name + "' not found in source BayesIm.");
            }
            int srcNode = src.getNodeIndex(srcNodeObj);

            int srcCols = src.getNumColumns(srcNode);
            int dstCols = dst.getNumColumns(dstNode);
            if (srcCols != dstCols) {
                throw new IllegalStateException("Category count mismatch for node '" + name +
                        "': source has " + srcCols + ", manipulated has " + dstCols + ".");
            }

            int[] srcParents = src.getParents(srcNode);
            int[] dstParents = dst.getParents(dstNode);

            int[] srcPosToDstPos = new int[srcParents.length];
            for (int k = 0; k < srcParents.length; k++) {
                String pName = src.getNode(srcParents[k]).getName();
                int dstPos = -1;
                for (int t = 0; t < dstParents.length; t++) {
                    if (dst.getNode(dstParents[t]).getName().equals(pName)) {
                        dstPos = t;
                        break;
                    }
                }
                if (dstPos < 0) {
                    throw new IllegalStateException(
                            "Parent mismatch while copying CPTs for node '" + name +
                                    "': expected parent '" + pName + "' not found in manipulated IM.");
                }
                srcPosToDstPos[k] = dstPos;
            }

            for (int dstRow = 0; dstRow < dst.getNumRows(dstNode); dstRow++) {
                int[] dstParentValuesInDstOrder = dst.getParentValues(dstNode, dstRow);

                int[] srcParentValuesInSrcOrder = new int[srcParents.length];
                for (int k = 0; k < srcParents.length; k++) {
                    srcParentValuesInSrcOrder[k] = dstParentValuesInDstOrder[srcPosToDstPos[k]];
                }

                int srcRow = src.getRowIndex(srcNode, srcParentValuesInSrcOrder);

                for (int col = 0; col < dstCols; col++) {
                    dst.setProbability(dstNode, dstRow, col,
                            src.getProbability(srcNode, srcRow, col));
                }
            }
        }
    }

    @Override
    public BayesIm getUpdatedBayesIm() {
        if (this.updatedBayesIm == null) {
            updateAll();
        }
        return this.updatedBayesIm;
    }

    @Override
    public double getMarginal(int variable, int category) {
        int dstVar = toManipulatedIndex(variable);
        return this.jta.getMarginalProbability(dstVar, category);
    }

    private int toManipulatedIndex(int srcIndex) {
        String name = this.bayesIm.getNode(srcIndex).getName();
        Node dstNode = this.manipulatedBayesIm.getNode(name);
        if (dstNode == null) {
            throw new IllegalStateException("Node '" + name + "' not found in manipulated BayesIm.");
        }
        return this.manipulatedBayesIm.getNodeIndex(dstNode);
    }

    @Override
    public boolean isJointMarginalSupported() {
        return true;
    }

    @Override
    public double getJointMarginal(int[] variables, int[] values) {
        if (variables.length != values.length) {
            throw new IllegalArgumentException("Values must match variables.");
        }

        // Kevin’s JTA getJointProbability(nodes,values) is essentially P(nodes=values AND enteredEvidence)
        // if we temporarily add those assignments as additional evidence. But JTA mutates internal state.
        // We avoid that here by using the calibrated tree and multiplying marginals only when safe.
        //
        // NOTE: Exact joint queries given evidence generally require either:
        //   - querying clique potentials directly, or
        //   - temporarily adding evidence and computing P(E'), or
        //   - a dedicated joint-query method.
        //
        // Since you mainly use marginals in the wizard, this is conservative:
        // if user requests joint marginal, we delegate to JTA’s joint probability on a fresh engine.

        JunctionTreeAlgorithm tmp = new JunctionTreeAlgorithm(this.manipulatedBayesIm);

        // Re-enter hard observational evidence.
        for (int i = 0; i < evidence.getNumNodes(); i++) {
            if (evidence.isManipulated(i)) continue;
            int fixed = getFixedCategoryOrMinusOne(i);
            if (fixed >= 0) tmp.setEvidence(i, fixed);
        }

        // Now treat (variables,values) as additional evidence and return P(variables=values | enteredEvidence)
        // by computing P(variables=values AND E) / P(E).
        double pE = tmp.getJointProbability(new int[]{variables[0]}, new int[]{values[0]}); // placeholder; will replace below

        // Compute P(E) by summing any node’s unnormalized margins after entering evidence:
        // We don’t have direct access, so we approximate P(E) by using a “dummy query”:
        // P(E) = sum_x P(X=x AND E) for any X. We can get that by requesting marginals and summing.
        // (getMarginalProbability returns normalized; so instead we use getJointProbability treating X=x as evidence,
        // which returns P(X=x AND E) in this implementation of JTA.)
        //
        // We pick X = 0 and sum over its categories.
        int x = 0;
        double sum = 0.0;
        for (int val = 0; val < this.manipulatedBayesIm.getNumColumns(x); val++) {
            // fresh per val to avoid accumulating evidence in tmp
            JunctionTreeAlgorithm tmp2 = new JunctionTreeAlgorithm(this.manipulatedBayesIm);
            for (int i = 0; i < evidence.getNumNodes(); i++) {
                if (evidence.isManipulated(i)) continue;
                int fixed = getFixedCategoryOrMinusOne(i);
                if (fixed >= 0) tmp2.setEvidence(i, fixed);
            }
            sum += tmp2.getJointProbability(new int[]{x}, new int[]{val});
        }
        pE = sum;

        if (!(pE > 0.0) || !Double.isFinite(pE)) {
            return Double.NaN;
        }

        // P(variables=values AND E)
        JunctionTreeAlgorithm tmp3 = new JunctionTreeAlgorithm(this.manipulatedBayesIm);
        for (int i = 0; i < evidence.getNumNodes(); i++) {
            if (evidence.isManipulated(i)) continue;
            int fixed = getFixedCategoryOrMinusOne(i);
            if (fixed >= 0) tmp3.setEvidence(i, fixed);
        }
        double pEV = tmp3.getJointProbability(variables, values);

        return (Double.isFinite(pEV) ? (pEV / pE) : Double.NaN);
    }

    @Override
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    @Override
    public double[] calculatePriorMarginals(int nodeIndex) {
        Evidence saved = getEvidence();
        setEvidence(Evidence.tautology(saved.getVariableSource()));
        double[] marginals = calculateUpdatedMarginals(nodeIndex);
        setEvidence(saved);
        return marginals;
    }

    @Override
    public double[] calculateUpdatedMarginals(int nodeIndex) {
        int dst = toManipulatedIndex(nodeIndex);
        return this.jta.getMarginalProbability(dst);
    }

    @Override
    public String toString() {
        return "Junction tree updater, evidence = " + this.evidence;
    }

    /**
     * Build a posterior BayesIm whose CPT entries are:
     *   P(Node=value | Parents=parentValues, enteredEvidence, do(manipulations)).
     *
     * This is computed by querying conditional distributions from the calibrated junction tree.
     */
    private void updateAll() {
        BayesIm out = new MlBayesIm(this.manipulatedBayesIm);
        int numNodes = out.getNumNodes();

        for (int node = 0; node < numNodes; node++) {
            int numRows = out.getNumRows(node);
            int numCols = out.getNumColumns(node);
            int[] parents = out.getParents(node);

            for (int row = 0; row < numRows; row++) {
                int[] parentValues = out.getParentValues(node, row);

                double[] rowProbs;
                if (parents.length == 0) {
                    rowProbs = this.jta.getMarginalProbability(node); // already normalized
                } else {
                    rowProbs = this.jta.getConditionalProbabilities(node, parents, parentValues); // should be normalized
                }

                // Defensive: ensure normalization (kid gloves).
                double sum = 0.0;
                boolean ok = true;
                for (double v : rowProbs) {
                    if (!Double.isFinite(v)) { ok = false; break; }
                    sum += v;
                }
                if (!ok || !(sum > 0.0)) {
                    for (int col = 0; col < numCols; col++) out.setProbability(node, row, col, Double.NaN);
                } else {
                    for (int col = 0; col < numCols; col++) out.setProbability(node, row, col, rowProbs[col] / sum);
                }
            }
        }

        this.updatedBayesIm = out;
    }

    private BayesIm createdUpdatedBayesIm(BayesPm updatedBayesPm) {
        // MANUAL because the initial values don’t matter; we overwrite manipulated nodes anyway.
        return new MlBayesIm(updatedBayesPm, this.bayesIm, MlBayesIm.InitializationMethod.MANUAL);
    }

    private BayesPm createUpdatedBayesPm(Dag updatedGraph) {
        return new BayesPm(updatedGraph, this.bayesIm.getBayesPm());
    }

    private Dag createManipulatedGraph(Graph graph) {
        Dag updatedGraph = new Dag(graph);

        // Graph surgery: remove incoming edges to manipulated nodes.
        for (int i = 0; i < this.evidence.getNumNodes(); ++i) {
            if (this.evidence.isManipulated(i)) {
                Node node = updatedGraph.getNode(this.evidence.getNode(i).getName());
                List<Node> parents = updatedGraph.getParents(node);
                for (Node parent : parents) {
                    updatedGraph.removeEdge(parent, node); // parent -> node
                }
            }
        }

        return updatedGraph;
    }

    /**
     * If proposition fixes node i to exactly one category, return it; else return -1.
     */
    private int getFixedCategoryOrMinusOne(int nodeIndex) {
        int allowed = -1;
        int count = 0;
        for (int j = 0; j < this.evidence.getNumCategories(nodeIndex); j++) {
            if (this.evidence.getProposition().isAllowed(nodeIndex, j)) {
                allowed = j;
                count++;
                if (count > 1) return -1;
            }
        }
        return (count == 1) ? allowed : -1;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }
}