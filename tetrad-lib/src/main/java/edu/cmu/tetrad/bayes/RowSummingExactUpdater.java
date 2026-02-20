/// ////////////////////////////////////////////////////////////////////////////
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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.List;

/**
 * Performs updating operations on a BayesIm by summing over cells in the joint probability table for the BayesIm. Quite
 * flexible and fast if almost all of the variables in the Bayes net are in evidence. Can be excruciatingly slow if
 * numVars - numVarsInEvidence is more than 15.
 *
 * <p>
 * This implementation is careful about index alignment between:
 *  </p>
 * <ul>
 *   <li>the SOURCE BayesIm (this.bayesIm) and Evidence (which is indexed to the source variable list), and</li>
 *   <li>the MANIPULATED BayesIm (this.manipulatedBayesIm) created by do()-surgery.</li>
 * </ul>
 * All cross-object references are done by node name to avoid subtle mismatches.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class RowSummingExactUpdater implements ManipulatingBayesUpdater {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The BayesIm which this updater modifies.
     */
    private final BayesIm bayesIm;

    /**
     * Stores evidence for all variables. (Indexed to the SOURCE variable list.)
     */
    private Evidence evidence;

    /**
     * The last manipulated BayesIm (after do()-surgery).
     */
    private BayesIm manipulatedBayesIm;

    /**
     * The BayesIm after update, if this was calculated.
     */
    private BayesIm updatedBayesIm;

    /**
     * Calculates probabilities from the manipulated Bayes IM.
     */
    private BayesImProbs bayesImProbs;

    //==============================CONSTRUCTORS===========================//

    /**
     * Constructs a new updater for the given Bayes net.
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public RowSummingExactUpdater(BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(Evidence.tautology(bayesIm));
    }

    /**
     * Constructs a new updater for the given Bayes net.
     *
     * @param bayesIm  a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @param evidence a {@link edu.cmu.tetrad.bayes.Evidence} object
     */
    public RowSummingExactUpdater(BayesIm bayesIm, Evidence evidence) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(evidence);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.bayes.RowSummingExactUpdater} object
     */
    public static RowSummingExactUpdater serializableInstance() {
        return new RowSummingExactUpdater(MlBayesIm.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    /**
     * The BayesIm that this updater bases its update on. This BayesIm is not modified; rather, a new BayesIm is created
     * and updated.
     *
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    /**
     * <p>Getter for the field <code>manipulatedBayesIm</code>.</p>
     *
     * @return the manipulated BayesIm.
     */
    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    /**
     * <p>getManipulatedGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getManipulatedGraph() {
        return getManipulatedBayesIm().getDag();
    }

    /**
     * The updated BayesIm. This is a different object from the source BayesIm.
     *
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @see #getBayesIm
     */
    public BayesIm getUpdatedBayesIm() {
        if (this.updatedBayesIm == null) {
            updateAll();
        }

        return this.updatedBayesIm;
    }

    /**
     * <p>Getter for the field <code>evidence</code>.</p>
     *
     * @return a defensive copy of the evidence.
     */
    public Evidence getEvidence() {
        return new Evidence(this.evidence);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEvidence(Evidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        if (evidence.isIncompatibleWith(this.bayesIm)) {
            throw new IllegalArgumentException("The variable list for the " +
                    "given bayesIm must be compatible with the variable list " +
                    "for this evidence.");
        }

        this.evidence = evidence;

        // Build manipulated graph via do()-surgery.
        Graph graph = this.bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedPm = createUpdatedBayesPm(manipulatedGraph);

        // Create manipulated IM (we will copy CPTs explicitly).
        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedPm);

        // Ensure manipulatedBayesIm matches source BayesIm everywhere except manipulated nodes.
        copyCptsFromSourceIntoManipulatedIm();

        // Override manipulated nodes to match the do() intervention distribution.
        // Do it by name to avoid any indexing mismatch between evidence/source and manipulated IM.
        for (int srcNode = 0; srcNode < evidence.getNumNodes(); srcNode++) {
            if (!evidence.isManipulated(srcNode)) continue;

            String name = evidence.getNode(srcNode).getName();
            Node dstNodeObj = this.manipulatedBayesIm.getNode(name);
            if (dstNodeObj == null) {
                throw new IllegalStateException("Manipulated node '" + name + "' not found in manipulated BayesIm.");
            }
            int dstNode = this.manipulatedBayesIm.getNodeIndex(dstNodeObj);

            // Manipulated nodes should have no parents after surgery, hence exactly one row (row 0).
            if (this.manipulatedBayesIm.getNumRows(dstNode) != 1) {
                throw new IllegalStateException("Expected exactly one row for manipulated node '" + name +
                        "' after graph surgery, but found " + this.manipulatedBayesIm.getNumRows(dstNode) + ".");
            }

            int numCats = this.manipulatedBayesIm.getNumColumns(dstNode);
            if (numCats != evidence.getNumCategories(srcNode)) {
                throw new IllegalStateException("Category count mismatch for manipulated node '" + name +
                        "': evidence has " + evidence.getNumCategories(srcNode) +
                        ", manipulated BayesIm has " + numCats + ".");
            }

            // Set unnormalized indicator distribution.
            for (int cat = 0; cat < numCats; cat++) {
                this.manipulatedBayesIm.setProbability(dstNode, 0, cat,
                        evidence.getProposition().isAllowed(srcNode, cat) ? 1.0 : 0.0);
            }

            // Normalize row 0 (important if multiple categories are allowed).
            double sum = 0.0;
            for (int cat = 0; cat < numCats; cat++) {
                sum += this.manipulatedBayesIm.getProbability(dstNode, 0, cat);
            }
            if (sum > 0.0) {
                for (int cat = 0; cat < numCats; cat++) {
                    this.manipulatedBayesIm.setProbability(dstNode, 0, cat,
                            this.manipulatedBayesIm.getProbability(dstNode, 0, cat) / sum);
                }
            } else {
                // No allowed categories => inconsistent manipulation; keep as NaN to signal undefined.
                for (int cat = 0; cat < numCats; cat++) {
                    this.manipulatedBayesIm.setProbability(dstNode, 0, cat, Double.NaN);
                }
            }
        }

        this.bayesImProbs = new BayesImProbs(this.manipulatedBayesIm);
        this.updatedBayesIm = null;
    }

    /**
     * Copy CPTs from the source BayesIm into manipulatedBayesIm for all NON-manipulated nodes.
     * For manipulated nodes, caller will override row 0 using evidence.
     *
     * This avoids subtle bugs where MlBayesIm constructors fail to copy CPTs correctly
     * when the BayesPm/DAG differs (even if only by removing parents of manipulated nodes).
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
                // leave for setEvidence() to set row 0
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

    @Override
    public boolean isJointMarginalSupported() {
        return true;
    }

    @Override
    public double getJointMarginal(int[] variables, int[] values) {
        if (variables.length != values.length) {
            throw new IllegalArgumentException("Values must match variables.");
        }

        // Map SOURCE variable indices -> manipulated variable indices by name.
        int[] dstVars = new int[variables.length];
        for (int i = 0; i < variables.length; i++) {
            String name = this.bayesIm.getNode(variables[i]).getName();
            Node dstNodeObj = this.manipulatedBayesIm.getNode(name);
            if (dstNodeObj == null) return Double.NaN;
            dstVars[i] = this.manipulatedBayesIm.getNodeIndex(dstNodeObj);
        }

        Proposition assertion = Proposition.tautology(this.manipulatedBayesIm);
        Proposition condition = conditionFromEvidence();

        for (int i = 0; i < dstVars.length; i++) {
            assertion.setCategory(dstVars[i], values[i]);
        }

        if (condition.existsCombination()) {
            return this.bayesImProbs.getConditionalProb(assertion, condition);
        } else {
            return Double.NaN;
        }
    }

    @Override
    public double getMarginal(int variable, int value) {
        // Interpret "variable" as SOURCE index, map by name to manipulatedBayesIm index.
        String name = this.bayesIm.getNode(variable).getName();

        Node dstNodeObj = this.manipulatedBayesIm.getNode(name);
        if (dstNodeObj == null) return Double.NaN;

        int dstVar = this.manipulatedBayesIm.getNodeIndex(dstNodeObj);

        Proposition assertion = Proposition.tautology(this.manipulatedBayesIm);
        Proposition condition = conditionFromEvidence();

        assertion.setCategory(dstVar, value);

        if (condition.existsCombination()) {
            return this.bayesImProbs.getConditionalProb(assertion, condition);
        } else {
            return Double.NaN;
        }
    }

    /**
     * Build a condition Proposition that belongs to manipulatedBayesIm and encodes
     * the Evidence's allowed/disallowed categories, matched by node name.
     *
     * <p>
     * Note: This assumes category indices for a node are consistent between the source
     * BayesIm (and Evidence) and the manipulated BayesIm (same DiscreteVariable ordering).
     * </p>
     */
    private Proposition conditionFromEvidence() {
        Proposition condition = Proposition.tautology(this.manipulatedBayesIm);
        Proposition evProp = this.evidence.getProposition();
        BayesIm src = this.bayesIm;

        for (int dstNode = 0; dstNode < this.manipulatedBayesIm.getNumNodes(); dstNode++) {
            String name = this.manipulatedBayesIm.getNode(dstNode).getName();

            Node srcNodeObj = src.getNode(name);
            if (srcNodeObj == null) {
                throw new IllegalStateException("Node '" + name + "' not found in source BayesIm.");
            }
            int srcNode = src.getNodeIndex(srcNodeObj);

            int dstCats = this.manipulatedBayesIm.getNumColumns(dstNode);
            int srcCats = this.evidence.getNumCategories(srcNode);
            if (dstCats != srcCats) {
                throw new IllegalStateException("Category count mismatch for node '" + name +
                        "': evidence has " + srcCats + ", manipulated has " + dstCats + ".");
            }

            for (int cat = 0; cat < dstCats; cat++) {
                if (!evProp.isAllowed(srcNode, cat)) {
                    condition.removeCategory(dstNode, cat);
                }
            }
        }

        return condition;
    }

    @Override
    public double[] calculatePriorMarginals(int nodeIndex) {
        Evidence oldEvidence = getEvidence();

        // Reset to tautology on the same variable source (source BayesIm / evidence indexing).
        setEvidence(Evidence.tautology(oldEvidence.getVariableSource()));

        int numCats = this.bayesIm.getNumColumns(nodeIndex);
        double[] marginals = new double[numCats];

        for (int cat = 0; cat < numCats; cat++) {
            marginals[cat] = getMarginal(nodeIndex, cat);
        }

        setEvidence(oldEvidence);
        return marginals;
    }

    @Override
    public double[] calculateUpdatedMarginals(int nodeIndex) {
        int numCats = this.bayesIm.getNumColumns(nodeIndex);
        double[] marginals = new double[numCats];

        for (int cat = 0; cat < numCats; cat++) {
            marginals[cat] = getMarginal(nodeIndex, cat);
        }

        // Only normalize if everything is finite and sum>0.
        double sum = 0.0;
        for (double v : marginals) {
            if (!Double.isFinite(v)) {
                return marginals;
            }
            sum += v;
        }

        if (!(sum > 0.0)) {
            for (int i = 0; i < marginals.length; i++) {
                marginals[i] = Double.NaN;
            }
            return marginals;
        }

        for (int i = 0; i < marginals.length; i++) {
            marginals[i] /= sum;
        }

        return marginals;
    }

    @Override
    public String toString() {
        return "Row summing exact updater, evidence = " + this.evidence;
    }

    //==============================PRIVATE METHODS=======================//

    private void updateAll() {
        BayesIm updatedBayesIm = new MlBayesIm(this.manipulatedBayesIm);
        int numNodes = this.manipulatedBayesIm.getNumNodes();

        for (int node = 0; node < numNodes; node++) {
            int numRows = this.manipulatedBayesIm.getNumRows(node);
            int numCols = this.manipulatedBayesIm.getNumColumns(node);
            int[] parents = this.manipulatedBayesIm.getParents(node);

            for (int row = 0; row < numRows; row++) {
                int[] parentValues = this.manipulatedBayesIm.getParentValues(node, row);

                for (int col = 0; col < numCols; col++) {

                    // ALWAYS build fresh propositions from the SAME BayesIm
                    Proposition assertion = Proposition.tautology(this.manipulatedBayesIm);
                    Proposition condition = Proposition.tautology(this.manipulatedBayesIm);

                    // Apply evidence restrictions directly (by name mapping)
                    applyEvidenceToCondition(condition);

                    // X_node = col
                    assertion.disallowComplement(node, col);

                    // Fix parents
                    for (int k = 0; k < parents.length; k++) {
                        condition.disallowComplement(parents[k], parentValues[k]);
                    }

                    if (condition.existsCombination()) {
                        double p = this.bayesImProbs.getConditionalProb(assertion, condition);
                        updatedBayesIm.setProbability(node, row, col, p);
                    } else {
                        updatedBayesIm.setProbability(node, row, col, Double.NaN);
                    }
                }
            }
        }

        this.updatedBayesIm = updatedBayesIm;
    }

    /**
     * Applies evidence restrictions (by name mapping) into a condition proposition.
     * The proposition must belong to manipulatedBayesIm.
     */
    private void applyEvidenceToCondition(Proposition condition) {
        Proposition evProp = this.evidence.getProposition();
        BayesIm src = this.bayesIm;

        for (int dstNode = 0; dstNode < this.manipulatedBayesIm.getNumNodes(); dstNode++) {
            String name = this.manipulatedBayesIm.getNode(dstNode).getName();

            Node srcNodeObj = src.getNode(name);
            int srcNode = src.getNodeIndex(srcNodeObj);

            int dstCats = this.manipulatedBayesIm.getNumColumns(dstNode);

            for (int cat = 0; cat < dstCats; cat++) {
                if (!evProp.isAllowed(srcNode, cat)) {
                    condition.removeCategory(dstNode, cat);
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

    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.bayesIm == null) {
            throw new NullPointerException();
        }

        if (this.evidence == null) {
            throw new NullPointerException();
        }
    }
}