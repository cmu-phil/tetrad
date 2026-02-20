/*
 * Copyright (C) 2020 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
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
 * A JunctionTreeUpdater that uses {@link JunctionTreeInference}, a fresh message-passing implementation
 * of the original JunctionTreeAlgorithm, with discrepancies from RowSummingUpdater fixed.
 *
 * 2026-2-20 jdramsey
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
     * Inference engine (message passing) over the updated BayesIm.
     */
    private JunctionTreeInference jti;

    /**
     * <p>Constructor for RobustJunctionTreeUpdater.</p>
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public JunctionTreeUpdater(BayesIm bayesIm) {
        this(bayesIm, Evidence.tautology(bayesIm));
    }

    /**
     * <p>Constructor for RobustJunctionTreeUpdater.</p>
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

    /**
     * Returns the only allowed category for a given node if exactly one category is allowed.
     * If multiple categories are allowed or none are allowed, returns -1.
     *
     * @param p       the Proposition instance used to check if a category is allowed for the node
     * @param node    the index of the node being evaluated
     * @param numCats the total number of categories to consider
     * @return the index of the only allowed category, or -1 if multiple categories are allowed or none are allowed
     */
    private static int getOnlyAllowedCategoryOrMinusOne(Proposition p, int node, int numCats) {
        int only = -1;
        for (int c = 0; c < numCats; c++) {
            if (p.isAllowed(node, c)) {
                if (only >= 0) return -1; // multiple allowed
                only = c;
            }
        }
        return only;
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

        this.evidence = evidence;

        // Build manipulated graph (for interventions), then a BayesPm/BayesIm with the same CPTs where possible.
        Graph graph = this.bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedPm = createUpdatedBayesPm(manipulatedGraph);
        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedPm);

        // Ensure manipulatedBayesIm matches source BayesIm everywhere except manipulated nodes.
        copyCptsFromSourceIntoManipulatedIm();

        // Override manipulated nodes to match do() intervention distribution.
        applyDoInterventionsToManipulatedIm();

        // Build inference on manipulated IM (not UpdatedBayesIm)
        this.updatedBayesIm = this.manipulatedBayesIm;
        this.jti = new JunctionTreeInference(this.manipulatedBayesIm);

        // Evidence re-indexed to manipulated IM
        Evidence evidence2 = new Evidence(evidence, this.manipulatedBayesIm);
        Proposition prop2 = evidence2.getProposition();

        // Push soft evidence (allowed categories)
        this.jti.setAllowedCategories(prop2);

        // Optional: also push singleton hard evidence
        for (int i = 0; i < evidence2.getNumNodes(); i++) {
            int only = getOnlyAllowedCategoryOrMinusOne(prop2, i, evidence2.getNumCategories(i));
            if (only >= 0) this.jti.setEvidence(i, only);
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
        // variable is SOURCE index (per Evidence / UI conventions); map to manipulated/updated index.
        int dstVar = mapSrcToManipulated(variable);
        if (dstVar < 0) return Double.NaN;

        // Condition must live in manipulated space too.
        Proposition condition = new Proposition(this.manipulatedBayesIm, this.evidence.getProposition());
        if (!condition.existsCombination()) return Double.NaN;

        // IMPORTANT: query inference engine using dstVar (updatedBayesIm shares indices with manipulatedBayesIm)
        return this.jti.getMarginal(dstVar, category);
    }

    /**
     * Map SOURCE BayesIm node index -> manipulatedBayesIm node index by name.
     */
    private int mapSrcToManipulated(int srcIndex) {
        String name = this.bayesIm.getNode(srcIndex).getName();
        Node dstNode = this.manipulatedBayesIm.getNode(name);
        if (dstNode == null) return -1;
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

        int[] dstVars = new int[variables.length];
        for (int i = 0; i < variables.length; i++) {
            dstVars[i] = mapSrcToManipulated(variables[i]);
            if (dstVars[i] < 0) return Double.NaN;
        }

        Proposition condition = new Proposition(this.manipulatedBayesIm, this.evidence.getProposition());
        if (!condition.existsCombination()) return Double.NaN;

        // Use inference engine’s joint proxy on dst indices.
        return this.jti.getJointProbability(dstVars, values);
    }

    /**
     * Copy CPTs from the source BayesIm into manipulatedBayesIm for all NON-manipulated nodes.
     * For manipulated nodes, caller will override row 0 using evidence.
     * <p>
     * This avoids subtle bugs where MlBayesIm constructors fail to copy CPTs correctly
     * when the BayesPm/DAG differs (even if only by removing parents of manipulated nodes).
     * <p>
     * All mapping is done by node name to avoid index mismatches.
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

            // Evidence is indexed to the SOURCE variable set.
            boolean manipulated = this.evidence.isManipulated(srcNode);
            if (manipulated) {
                // leave for applyDoInterventionsToManipulatedIm() to set row 0
                continue;
            }

            // Sanity: same category count.
            int srcCols = src.getNumColumns(srcNode);
            int dstCols = dst.getNumColumns(dstNode);
            if (srcCols != dstCols) {
                throw new IllegalStateException("Category count mismatch for node '" + name +
                        "': source has " + srcCols + ", manipulated has " + dstCols + ".");
            }

            // Copy every row/col by matching parent-values in SOURCE parent order.
            int[] srcParents = src.getParents(srcNode);
            int[] dstParents = dst.getParents(dstNode);

            // Parent set must match by name for non-manipulated nodes.
            if (srcParents.length != dstParents.length) {
                throw new IllegalStateException("Parent count mismatch for node '" + name +
                        "': source has " + srcParents.length + ", manipulated has " + dstParents.length + ".");
            }

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
                    throw new IllegalStateException(
                            "Parent mismatch while copying CPTs for node '" + name +
                                    "': expected parent '" + pName + "' not found in manipulated IM.");
                }
                srcPosToDstPos[k] = dstPos;
            }

            for (int dstRow = 0; dstRow < dst.getNumRows(dstNode); dstRow++) {
                int[] dstParentValuesInDstOrder = dst.getParentValues(dstNode, dstRow);

                // Build parent-value vector in SOURCE parent order.
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
     * For each manipulated variable, force its CPT in manipulatedBayesIm to match
     * the do() distribution implied by Evidence: an indicator distribution over allowed
     * categories, normalized. This assumes do()-surgery has removed its parents, so there
     * is exactly one row (row 0).
     * <p>
     * Mapping is by node name to avoid index mismatches.
     */
    private void applyDoInterventionsToManipulatedIm() {
        BayesIm dst = this.manipulatedBayesIm;
        BayesIm src = this.bayesIm;
        Proposition evProp = this.evidence.getProposition();

        for (int srcNode = 0; srcNode < this.evidence.getNumNodes(); srcNode++) {
            if (!this.evidence.isManipulated(srcNode)) continue;

            String name = this.evidence.getNode(srcNode).getName();
            Node dstNodeObj = dst.getNode(name);
            if (dstNodeObj == null) {
                throw new IllegalStateException("Manipulated node '" + name + "' not found in manipulated BayesIm.");
            }
            int dstNode = dst.getNodeIndex(dstNodeObj);

            // After surgery, manipulated node should have no parents => exactly one row.
            if (dst.getNumRows(dstNode) != 1) {
                throw new IllegalStateException("Expected exactly one row for manipulated node '" + name +
                        "' after do()-surgery, but found " + dst.getNumRows(dstNode) + ".");
            }

            int dstCats = dst.getNumColumns(dstNode);
            int srcCats = this.evidence.getNumCategories(srcNode);
            if (dstCats != srcCats) {
                throw new IllegalStateException("Category count mismatch for manipulated node '" + name +
                        "': evidence has " + srcCats + ", manipulated BayesIm has " + dstCats + ".");
            }

            // Set unnormalized indicator distribution from Evidence proposition.
            for (int cat = 0; cat < dstCats; cat++) {
                dst.setProbability(dstNode, 0, cat, evProp.isAllowed(srcNode, cat) ? 1.0 : 0.0);
            }

            // Normalize row 0.
            double sum = 0.0;
            for (int cat = 0; cat < dstCats; cat++) {
                sum += dst.getProbability(dstNode, 0, cat);
            }

            if (sum > 0.0) {
                for (int cat = 0; cat < dstCats; cat++) {
                    dst.setProbability(dstNode, 0, cat, dst.getProbability(dstNode, 0, cat) / sum);
                }
            } else {
                // No allowed categories => inconsistent manipulation; signal undefined.
                for (int cat = 0; cat < dstCats; cat++) {
                    dst.setProbability(dstNode, 0, cat, Double.NaN);
                }
            }
        }
    }

    @Override
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    @Override
    public double[] calculatePriorMarginals(int nodeIndex) {
        Evidence e = getEvidence();
        setEvidence(Evidence.tautology(e.getVariableSource()));

        int k = this.bayesIm.getNumColumns(nodeIndex);
        double[] marginals = new double[k];
        for (int c = 0; c < k; c++) {
            marginals[c] = getMarginal(nodeIndex, c); // now maps correctly
        }

        setEvidence(e);
        return marginals;
    }

    @Override
    public double[] calculateUpdatedMarginals(int nodeIndex) {
        int k = this.bayesIm.getNumColumns(nodeIndex);
        double[] marginals = new double[k];
        for (int c = 0; c < k; c++) {
            marginals[c] = getMarginal(nodeIndex, c); // now maps correctly
        }
        return marginals;
    }

    // =========================================================
    // Kevin-compatible "updateAll" CPT rewriting (kept verbatim-ish)
    // =========================================================

    @Override
    public String toString() {
        return "Robust junction tree updater, evidence = " + this.evidence;
    }

    // =========================================================
    // Helpers mirroring Kevin’s plumbing
    // =========================================================

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
                        double p;
                        if (parents.length > 0) {
                            p = this.jti.getConditional(node, parents, parentValues)[col];
                        } else {
                            p = this.jti.getMarginal(node, col);
                        }
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

    private Dag createManipulatedGraph(Graph graph) {
        Dag updatedGraph = new Dag(graph);

        for (int i = 0; i < this.evidence.getNumNodes(); ++i) {
            if (this.evidence.isManipulated(i)) {
                Node node = updatedGraph.getNode(this.evidence.getNode(i).getName());
                List<Node> parents = updatedGraph.getParents(node);

                for (Node parent : parents) {
                    updatedGraph.removeEdge(parent, node);
                }
            }
        }

        return updatedGraph;
    }

    // =========================================================
    // Serialization hooks
    // =========================================================

    /**
     * Serializes the state of the JunctionTreeUpdater object to an ObjectOutputStream.
     * This method ensures that the default serialization process is executed and logs
     * any serialization-related errors for debugging purposes.
     *
     * @param out the ObjectOutputStream to which the object state is written
     * @throws IOException if an I/O error occurs during the serialization process
     */
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

    /**
     * Deserializes the state of the JunctionTreeUpdater object from an ObjectInputStream.
     * This method ensures that the default deserialization behavior is executed while also
     * logging any deserialization issues for debugging purposes.
     *
     * @param in the ObjectInputStream from which the object state is read
     * @throws IOException if an I/O error occurs during the deserialization process
     * @throws ClassNotFoundException if the class of a serialized object cannot be found
     */
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