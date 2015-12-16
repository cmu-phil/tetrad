///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Performs updating operations on a BayesIm by summing over cells in the joint
 * probability table for the BayesIm. Quite flexible and fast if almost all of
 * the variables in the Bayes net are in evidence. Can be excruciatingly slow if
 * numVars - numVarsInEvidence is more than 15.
 *
 * @author Joseph Ramsey
 */
public final class RowSummingExactUpdater implements ManipulatingBayesUpdater {
    static final long serialVersionUID = 23L;

    /**
     * The BayesIm which this updater modifies.
     *
     * @serial Cannot be null.
     */
    private BayesIm bayesIm;

    /**
     * Stores evidence for all variables.
     *
     * @serial Cannot be null.
     */
    private Evidence evidence;

    /**
     * The last manipulated BayesIm.
     *
     * @serial Can be null.
     */
    private BayesIm manipulatedBayesIm;

    /**
     * The BayesIm after update, if this was calculated.
     *
     * @serial Can be null.
     */
    private BayesIm updatedBayesIm;

    /**
     * Calculates probabilities from the manipulated Bayes IM.
     *
     * @serial Can be null.
     */
    private BayesImProbs bayesImProbs;

    //==============================CONSTRUCTORS===========================//

    /**
     * Constructs a new updater for the given Bayes net.
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
     */
    public static RowSummingExactUpdater serializableInstance() {
        return new RowSummingExactUpdater(MlBayesIm.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    /**
     * The BayesIm that this updater bases its update on. This BayesIm is not
     * modified; rather, a new BayesIm is created and updated.
     */
    public BayesIm getBayesIm() {
        return bayesIm;
    }

    /**
     * @return the updated BayesIm.
     */
    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    public Graph getManipulatedGraph() {
        return getManipulatedBayesIm().getDag();
    }

    /**
     * The updated BayesIm. This is a different object from the source BayesIm.
     *
     * @see #getBayesIm
     */
    public BayesIm getUpdatedBayesIm() {
        if (updatedBayesIm == null) {
            updateAll();
        }

        return updatedBayesIm;
    }

    /**
     * @return a defensive copy of the evidence.
     */
    public Evidence getEvidence() {
        return new Evidence(this.evidence);
    }

    public final void setEvidence(Evidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        if (evidence.isIncompatibleWith(bayesIm)) {
            throw new IllegalArgumentException("The variable list for the " +
                    "given bayesIm must be compatible with the variable list " +
                    "for this evidence.");
        }

        this.evidence = evidence;

        Dag graph = bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedPm = createUpdatedBayesPm(manipulatedGraph);

        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedPm);
        this.bayesImProbs = new BayesImProbs(bayesIm);
        this.updatedBayesIm = null;
    }

    public boolean isJointMarginalSupported() {
        return true;
    }

    public double getJointMarginal(int[] variables, int[] values) {
        if (variables.length != values.length) {
            throw new IllegalArgumentException("Values must match variables.");
        }

        Proposition assertion = Proposition.tautology(manipulatedBayesIm);
        Proposition condition =
                new Proposition(manipulatedBayesIm, evidence.getProposition());

        for (int i = 0; i < variables.length; i++) {
            assertion.setCategory(variables[i], values[i]);
        }

        if (condition.existsCombination()) {
            return bayesImProbs.getConditionalProb(assertion, condition);
        } else {
            return Double.NaN;
        }
    }

    /**
     * @return P&lpar;variable&equals;value &vbar; evidence&rpar; where evidence is getEvidence().
     */
    public double getMarginal(int variable, int value) {
        Proposition assertion = Proposition.tautology(manipulatedBayesIm);
        Proposition condition =
                new Proposition(manipulatedBayesIm, evidence.getProposition());
        assertion.setCategory(variable, value);

        if (condition.existsCombination()) {
            return bayesImProbs.getConditionalProb(assertion, condition);
        } else {
            return Double.NaN;
        }
    }

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

    public double[] calculateUpdatedMarginals(int nodeIndex) {
        double[] marginals = new double[evidence.getNumCategories(nodeIndex)];

        for (int i = 0; i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        return marginals;
    }

    /**
     * Prints out the most recent marginal.
     */
    public String toString() {
        return "Row summing exact updater, evidence = " + evidence;
    }

    //==============================PRIVATE METHODS=======================//

    private void updateAll() {
        BayesIm updatedBayesIm = new MlBayesIm(manipulatedBayesIm);
        int numNodes = manipulatedBayesIm.getNumNodes();

        Proposition assertion = Proposition.tautology(manipulatedBayesIm);
        Proposition condition = Proposition.tautology(manipulatedBayesIm);
        Evidence evidence2 = new Evidence(evidence, manipulatedBayesIm);

        for (int node = 0; node < numNodes; node++) {
            int numRows = manipulatedBayesIm.getNumRows(node);
            int numCols = manipulatedBayesIm.getNumColumns(node);
            int[] parents = manipulatedBayesIm.getParents(node);

            for (int row = 0; row < numRows; row++) {
                int[] parentValues =
                        manipulatedBayesIm.getParentValues(node, row);

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
                        double p = bayesImProbs.getConditionalProb(assertion,
                                condition);
                        updatedBayesIm.setProbability(node, row, col, p);
                    } else {
                        updatedBayesIm.setProbability(node, row, col,
                                Double.NaN);
                    }
                }
            }
        }

        this.updatedBayesIm = updatedBayesIm;
    }

    private BayesIm createdUpdatedBayesIm(BayesPm updatedBayesPm) {

        // Switching this to MANUAL since the initial values don't matter.
        return new MlBayesIm(updatedBayesPm, bayesIm, MlBayesIm.MANUAL);
    }

    private BayesPm createUpdatedBayesPm(Dag updatedGraph) {
        return new BayesPm(updatedGraph, bayesIm.getBayesPm());
    }

    private Dag createManipulatedGraph(Graph graph) {
        Dag updatedGraph = new Dag(graph);

        // alters graph for manipulated evidenceItems
        for (int i = 0; i < evidence.getNumNodes(); ++i) {
            if (evidence.isManipulated(i)) {
                Node node = updatedGraph.getNode(evidence.getNode(i).getName());
                List<Node> parents = updatedGraph.getParents(node);

                for (Object parent1 : parents) {
                    Node parent = (Node) parent1;
                    updatedGraph.removeEdge(node, parent);
                }
            }
        }

        return updatedGraph;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (bayesIm == null) {
            throw new NullPointerException();
        }

        if (evidence == null) {
            throw new NullPointerException();
        }
    }
}




