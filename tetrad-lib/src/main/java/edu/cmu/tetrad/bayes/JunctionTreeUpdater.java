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
 * <p>
 * Kid-gloves fixes:
 * <ul>
 *   <li>Fix graph-surgery edge direction: remove parent -> manipulatedNode (not node -> parent).</li>
 *   <li>MANUAL initialization does NOT guarantee CPT copy; explicitly copy CPTs from source BayesIm.</li>
 *   <li>Apply do()-manipulation CPT surgery by NAME to avoid index mismatches.</li>
 *   <li>Build JTA on UpdatedBayesIm (as original), but ensure its base IM has correct CPTs.</li>
 *   <li>No reflection; no lazy-init hacks.</li>
 * </ul>
 * </p>
 */
public class JunctionTreeUpdater implements ManipulatingBayesUpdater {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The BayesIm which this updater modifies.
     */
    private final BayesIm bayesIm;

    /**
     * Stores evidence for all variables (indexed to SOURCE BayesIm).
     */
    private Evidence evidence;

    /**
     * The BayesIm after do()-graph surgery and CPT copying / manipulation surgery.
     */
    private BayesIm manipulatedBayesIm;

    /**
     * The BayesIm after update, if this was calculated.
     */
    private BayesIm updatedBayesIm;

    /**
     * Junction tree algorithm built over the UpdatedBayesIm (as in the original code).
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

        // Kid-gloves: defensive copy (Evidence/Proposition can be mutated externally in some flows).
        this.evidence = new Evidence(evidence);

        // 1) Apply do()-manipulations via graph surgery.
        Graph graph = this.bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedPm = createUpdatedBayesPm(manipulatedGraph);

        // 2) Create manipulated IM with MANUAL init, then explicitly copy CPTs.
        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedPm);

        // CRITICAL: MANUAL init does not guarantee CPT copy. Copy explicitly.
        copyCptsFromSourceIntoManipulatedIm(this.bayesIm, this.manipulatedBayesIm);

        // 3) CPT surgery for manipulated nodes (do()) by NAME.
        applyManipulationCptSurgeryByName(this.evidence, this.manipulatedBayesIm);

        // 4) Build UpdatedBayesIm using Evidence aligned to manipulated IM.
        Evidence evidence2 = new Evidence(this.evidence, this.manipulatedBayesIm);
        this.updatedBayesIm = new UpdatedBayesIm(this.manipulatedBayesIm, evidence2);

        // 5) Build junction tree algorithm on updated IM (Kevin’s original approach).
        this.jta = new JunctionTreeAlgorithm(this.updatedBayesIm);
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
        // In this updater, "variable" is expected to be the manipulatedBayesIm index.
        // (This is how the original code behaved.)
        Proposition assertion = Proposition.tautology(this.manipulatedBayesIm);
        Proposition condition = new Proposition(this.manipulatedBayesIm, this.evidence.getProposition());
        assertion.setCategory(variable, category);

        if (condition.existsCombination()) {
            return this.jta.getMarginalProbability(variable, category);
        } else {
            return Double.NaN;
        }
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

        Proposition assertion = Proposition.tautology(this.manipulatedBayesIm);
        Proposition condition = new Proposition(this.manipulatedBayesIm, this.evidence.getProposition());

        for (int i = 0; i < variables.length; i++) {
            assertion.setCategory(variables[i], values[i]);
        }

        if (condition.existsCombination()) {
            // NOTE: original implementation multiplies marginals (not a true joint unless independent given evidence).
            // Keeping original behavior for backwards compatibility.
            double joint = 1.0;
            for (int i = 0; i < variables.length; i++) {
                joint *= this.jta.getMarginalProbability(variables[i], values[i]);
            }
            return joint;
        } else {
            return Double.NaN;
        }
    }

    @Override
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    @Override
    public double[] calculatePriorMarginals(int nodeIndex) {
        Evidence evidence = getEvidence();
        setEvidence(Evidence.tautology(evidence.getVariableSource()));

        double[] marginals = new double[evidence.getNumCategories(nodeIndex)];

        for (int i = 0; i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        setEvidence(evidence);
        return marginals;
    }

    @Override
    public double[] calculateUpdatedMarginals(int nodeIndex) {
        double[] marginals = new double[this.evidence.getNumCategories(nodeIndex)];

        for (int i = 0; i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        return marginals;
    }

    @Override
    public String toString() {
        return "Junction tree updater, evidence = " + this.evidence;
    }

    /**
     * Build a posterior BayesIm by querying JTA for each CPT entry, as in original.
     */
    private void updateAll() {
        this.updatedBayesIm = new MlBayesIm(this.manipulatedBayesIm);
        int numNodes = this.manipulatedBayesIm.getNumNodes();

        Proposition assertion = Proposition.tautology(this.manipulatedBayesIm);
        Proposition condition = Proposition.tautology(this.manipulatedBayesIm);
        Evidence evidence2 = new Evidence(this.evidence, this.manipulatedBayesIm);

        for (int node = 0; node < numNodes; node++) {
            int numRows = this.manipulatedBayesIm.getNumRows(node);
            int numCols = this.manipulatedBayesIm.getNumColumns(node);
            int[] parents = this.manipulatedBayesIm.getParents(node);

            for (int row = 0; row < numRows; row++) {
                int[] parentValues = this.manipulatedBayesIm.getParentValues(node, row);

                for (int col = 0; col < numCols; col++) {
                    assertion.setToTautology();
                    condition.setToTautology();

                    for (int i = 0; i < numNodes; i++) {
                        for (int j = 0; j < evidence2.getNumCategories(i); j++) {
                            if (!evidence2.getProposition().isAllowed(i, j)) {
                                condition.removeCategory(i, j);
                            }
                        }
                    }

                    assertion.disallowComplement(node, col);

                    for (int k = 0; k < parents.length; k++) {
                        condition.disallowComplement(parents[k], parentValues[k]);
                    }

                    if (condition.existsCombination()) {
                        double p = (parents.length > 0)
                                ? this.jta.getConditionalProbability(node, col, parents, parentValues)
                                : this.jta.getMarginalProbability(node, col);
                        this.updatedBayesIm.setProbability(node, row, col, p);
                    } else {
                        this.updatedBayesIm.setProbability(node, row, col, Double.NaN);
                    }
                }
            }
        }
    }

    private BayesIm createdUpdatedBayesIm(BayesPm updatedBayesPm) {
        return new MlBayesIm(updatedBayesPm, this.bayesIm, MlBayesIm.InitializationMethod.MANUAL);
    }

    private BayesPm createUpdatedBayesPm(Dag updatedGraph) {
        return new BayesPm(updatedGraph, this.bayesIm.getBayesPm());
    }

    /**
     * Graph surgery: remove incoming edges to manipulated nodes (parent -> node).
     * This fixes the original bug which removed the wrong direction.
     */
    private Dag createManipulatedGraph(Graph graph) {
        Dag updatedGraph = new Dag(graph);

        for (int i = 0; i < this.evidence.getNumNodes(); ++i) {
            if (this.evidence.isManipulated(i)) {
                Node node = updatedGraph.getNode(this.evidence.getNode(i).getName());
                List<Node> parents = updatedGraph.getParents(node);

                for (Node parent : parents) {
                    updatedGraph.removeEdge(parent, node); // FIXED
                }
            }
        }

        return updatedGraph;
    }

    /**
     * Copy CPTs from source BayesIm to destination BayesIm by matching nodes/parents by name
     * and matching parent-value rows via getRowIndex.
     *
     * This prevents MANUAL initialization from leaving uniform/default rows in the manipulated IM.
     */
    private static void copyCptsFromSourceIntoManipulatedIm(BayesIm src, BayesIm dst) {
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
                throw new IllegalStateException("Category count mismatch for node '" + name
                        + "': source has " + srcCols + ", manipulated has " + dstCols + ".");
            }

            int[] srcParents = src.getParents(srcNode);
            int[] dstParents = dst.getParents(dstNode);

            // Map each src-parent position -> dst-parent position (by name).
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
                    throw new IllegalStateException("Parent mismatch while copying CPTs for node '" + name
                            + "': expected parent '" + pName + "' not found in manipulated IM.");
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

    /**
     * Overwrite CPT rows for do()-manipulated nodes to match evidence proposition,
     * using name-based mapping to avoid index mismatches.
     */
    private static void applyManipulationCptSurgeryByName(Evidence evidence, BayesIm manipulated) {
        for (int srcNode = 0; srcNode < evidence.getNumNodes(); srcNode++) {
            if (!evidence.isManipulated(srcNode)) continue;

            String name = evidence.getNode(srcNode).getName();
            Node dstNodeObj = manipulated.getNode(name);
            if (dstNodeObj == null) {
                throw new IllegalStateException("Manipulated node '" + name + "' not found in manipulated BayesIm.");
            }
            int dstNode = manipulated.getNodeIndex(dstNodeObj);

            if (manipulated.getNumRows(dstNode) != 1) {
                throw new IllegalStateException("Expected exactly one row for manipulated node '" + name
                        + "' after graph surgery, but found " + manipulated.getNumRows(dstNode) + ".");
            }

            int numCats = manipulated.getNumColumns(dstNode);
            int evCats = evidence.getNumCategories(srcNode);
            if (numCats != evCats) {
                throw new IllegalStateException("Category count mismatch for manipulated node '" + name
                        + "': evidence has " + evCats + ", manipulated has " + numCats + ".");
            }

            double sum = 0.0;
            for (int cat = 0; cat < numCats; cat++) {
                double v = evidence.getProposition().isAllowed(srcNode, cat) ? 1.0 : 0.0;
                manipulated.setProbability(dstNode, 0, cat, v);
                sum += v;
            }

            if (sum > 0.0) {
                for (int cat = 0; cat < numCats; cat++) {
                    manipulated.setProbability(dstNode, 0, cat,
                            manipulated.getProbability(dstNode, 0, cat) / sum);
                }
            } else {
                // No allowed categories => inconsistent manipulation. Signal undefined.
                for (int cat = 0; cat < numCats; cat++) {
                    manipulated.setProbability(dstNode, 0, cat, Double.NaN);
                }
            }
        }
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