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

import java.io.IOException;
import java.io.ObjectInputStream;
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
     * Calculates probabilities from the manipulated Bayes IM.
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

    /**
     * {@inheritDoc}
     */
    @Override
    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getManipulatedGraph() {
        return getManipulatedBayesIm().getDag();
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
            throw new IllegalArgumentException("The variable list for the "
                                               + "given bayesIm must be compatible with the variable list "
                                               + "for this evidence.");
        }

        this.evidence = evidence;

        Graph graph = this.bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedPm = createUpdatedBayesPm(manipulatedGraph);

        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedPm);

        Evidence evidence2 = new Evidence(evidence, this.manipulatedBayesIm);
        this.updatedBayesIm = new UpdatedBayesIm(this.manipulatedBayesIm, evidence2);

        this.jta = new JunctionTreeAlgorithm(this.updatedBayesIm);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BayesIm getUpdatedBayesIm() {
        if (this.updatedBayesIm == null) {
            updateAll();
        }

        return this.updatedBayesIm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getMarginal(int variable, int category) {
        Proposition assertion = Proposition.tautology(this.manipulatedBayesIm);
        Proposition condition
                = new Proposition(this.manipulatedBayesIm, this.evidence.getProposition());
        assertion.setCategory(variable, category);

        if (condition.existsCombination()) {
            return this.jta.getMarginalProbability(variable, category);
        } else {
            return Double.NaN;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isJointMarginalSupported() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getJointMarginal(int[] variables, int[] values) {
        if (variables.length != values.length) {
            throw new IllegalArgumentException("Values must match variables.");
        }

        Proposition assertion = Proposition.tautology(this.manipulatedBayesIm);
        Proposition condition
                = new Proposition(this.manipulatedBayesIm, this.evidence.getProposition());

        for (int i = 0; i < variables.length; i++) {
            assertion.setCategory(variables[i], values[i]);
        }

        if (condition.existsCombination()) {
            double joint = 1.0;
            for (int i = 0; i < variables.length; i++) {
                joint *= this.jta.getMarginalProbability(variables[i], values[i]);
            }

            return joint;
        } else {
            return Double.NaN;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] calculateUpdatedMarginals(int nodeIndex) {
        double[] marginals = new double[this.evidence.getNumCategories(nodeIndex)];

        for (int i = 0; i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        return marginals;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Junction tree updater, evidence = " + this.evidence;
    }

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
                int[] parentValues
                        = this.manipulatedBayesIm.getParentValues(node, row);

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
                        condition.disallowComplement(parents[k],
                                parentValues[k]);
                    }

                    if (condition.existsCombination()) {
                        double p = (parents.length > 0)
                                ? this.jta.getConditionalProbability(node, col, parents, parentValues)
                                : this.jta.getMarginalProbability(node, col);
                        this.updatedBayesIm.setProbability(node, row, col, p);
                    } else {
                        this.updatedBayesIm.setProbability(node, row, col,
                                Double.NaN);
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
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
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
