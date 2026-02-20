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
 * A JunctionTreeUpdater that uses {@link RobustJunctionTreeInference} (a fresh message-passing implementation)
 * instead of Kevin's {@link JunctionTreeAlgorithm}.
 *
 * Signature intentionally matches Kevin's JunctionTreeUpdater so you can swap it in.
 */
public class RobustJunctionTreeUpdater implements ManipulatingBayesUpdater {
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
     *
     * IMPORTANT: This engine is always built from updatedBayesIm, which is
     * manipulatedBayesIm plus the Evidence restrictions (via UpdatedBayesIm).
     */
    private RobustJunctionTreeInference jti;

    /**
     * <p>Constructor for RobustJunctionTreeUpdater.</p>
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public RobustJunctionTreeUpdater(BayesIm bayesIm) {
        this(bayesIm, Evidence.tautology(bayesIm));
    }

    /**
     * <p>Constructor for RobustJunctionTreeUpdater.</p>
     *
     * @param bayesIm  a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @param evidence a {@link edu.cmu.tetrad.bayes.Evidence} object
     */
    public RobustJunctionTreeUpdater(BayesIm bayesIm, Evidence evidence) {
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

        this.evidence = evidence;

        // Build manipulated graph (for interventions), then a BayesPm/BayesIm with the same CPTs where possible.
        Graph graph = this.bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedPm = createUpdatedBayesPm(manipulatedGraph);
        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedPm);

        // Apply evidence as restrictions via UpdatedBayesIm (this is Kevin's convention).
        Evidence evidence2 = new Evidence(evidence, this.manipulatedBayesIm);
        this.updatedBayesIm = new UpdatedBayesIm(this.manipulatedBayesIm, evidence2);

        // Build inference engine from the *updated* BayesIm.
        this.jti = new RobustJunctionTreeInference(this.updatedBayesIm);

        // Push hard evidence into the inference engine.
        // Evidence in Tetrad is "allowed categories"; here we translate to hard evidence iff the node
        // has exactly one allowed category. Otherwise we do nothing (soft restrictions are already
        // handled inside UpdatedBayesIm).
        //
        // This matches Kevin's semantics: UpdatedBayesIm encodes the proposition; the JT engine
        // just does inference on that BayesIm.
        Proposition prop = evidence2.getProposition();
        for (int i = 0; i < evidence2.getNumNodes(); i++) {
            int only = getOnlyAllowedCategoryOrMinusOne(prop, i, evidence2.getNumCategories(i));
            if (only >= 0) {
                this.jti.setEvidence(i, only);
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
        Proposition assertion = Proposition.tautology(this.manipulatedBayesIm);
        Proposition condition = new Proposition(this.manipulatedBayesIm, this.evidence.getProposition());
        assertion.setCategory(variable, category);

        if (condition.existsCombination()) {
            // IMPORTANT: use inference over updatedBayesIm; the inference already reflects allowed-category restrictions.
            return this.jti.getMarginal(variable, category);
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
            // Kevin's original implementation multiplies marginals, which is not a true joint unless independent.
            // We'll keep the behavior consistent with the API expectation in this class hierarchy by using the
            // inference engine's "evidence probability proxy" (may be proportional if the engine normalizes messages).
            // If you need exact joints, we can extend RobustJunctionTreeInference with scaling constants.
            return this.jti.getJointProbability(variables, values);
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
        Evidence e = getEvidence();
        setEvidence(Evidence.tautology(e.getVariableSource()));

        double[] marginals = new double[e.getNumCategories(nodeIndex)];
        for (int i = 0; i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        setEvidence(e);
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
        return "Robust junction tree updater, evidence = " + this.evidence;
    }

    // =========================================================
    // Kevin-compatible "updateAll" CPT rewriting (kept verbatim-ish)
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

    // =========================================================
    // Helpers mirroring Kevin’s plumbing
    // =========================================================

    private BayesIm createdUpdatedBayesIm(BayesPm updatedBayesPm) {
        return new MlBayesIm(updatedBayesPm, this.bayesIm, MlBayesIm.InitializationMethod.MANUAL);
    }

    private BayesPm createUpdatedBayesPm(Dag updatedGraph) {
        return new BayesPm(updatedGraph, this.bayesIm.getBayesPm());
    }

    private Dag createManipulatedGraph(Graph graph) {
        Dag updatedGraph = new Dag(graph);

        // alters graph for manipulated evidenceItems
        for (int i = 0; i < this.evidence.getNumNodes(); ++i) {
            if (this.evidence.isManipulated(i)) {
                Node node = updatedGraph.getNode(this.evidence.getNode(i).getName());
                List<Node> parents = updatedGraph.getParents(node);

                for (Node parent1 : parents) {
                    updatedGraph.removeEdge(node, parent1);
                }
            }
        }

        return updatedGraph;
    }

    /**
     * If exactly one category is allowed for the node, return it; else return -1.
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

    // =========================================================
    // Serialization hooks
    // =========================================================

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