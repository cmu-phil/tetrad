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
 * Calculates updated probabilities for variables conditional on their parents
 * as well as single-variable updated marginals for a Bayes IM using an
 * algorithm that restricts expensive updating summations only to conditional
 * probabilities of variables with respect to their parents that change from
 * non-updated to updated values.
 *
 * @author Joseph Ramsey
 */
public final class CptInvariantUpdater implements ManipulatingBayesUpdater {
    static final long serialVersionUID = 23L;

    /**
     * The IM which this updater modifies.
     *
     * @serial Cannot be null.
     */
    private BayesIm bayesIm;

    /**
     * The manipulated Bayes IM--that is, bayesIm after the manipulations in
     * evidence have been applied to it.
     *
     * @serial
     */
    private BayesIm manipulatedBayesIm;

    /**
     * The IM after update.
     *
     * @serial
     */
    private UpdatedBayesIm updatedBayesIm;

    /**
     * Stores evidence for all variables.
     *
     * @serial Cannot be null
     */
    private Evidence evidence;

    /**
     * The gadget that calculates marginals.
     *
     * @serial
     */
    private CptInvariantMarginalCalculator cptInvariantMarginalCalculator;

    //==============================CONSTRUCTORS===========================//

    public CptInvariantUpdater(BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(Evidence.tautology(this.bayesIm));
    }

    /**
     * Constructs a new updater for the given Bayes net.
     */
    public CptInvariantUpdater(BayesIm bayesIm, Evidence evidence) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(evidence);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static CptInvariantUpdater serializableInstance() {
        return new CptInvariantUpdater(MlBayesIm.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    public Graph getManipulatedGraph() {
        return getManipulatedBayesIm().getDag();
    }

    public BayesIm getUpdatedBayesIm() {
        return this.updatedBayesIm;
    }

    public Evidence getEvidence() {
        return new Evidence(this.evidence);
    }

    public void setEvidence(Evidence evidence) {
        System.out.println("*** " + evidence);


        if (evidence == null) {
            throw new NullPointerException();
        }

        if (!evidence.isCompatibleWith(bayesIm)) {
            throw new IllegalArgumentException("The variable list for this evidence " +
                    "must be compatible with the variable list of the stored IM.");
        }

        this.evidence = evidence;

        // Create the manipulated Bayes Im.
        Dag graph = bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedBayesPm = createManipulatedBayesPm(manipulatedGraph);
        this.manipulatedBayesIm = createdManipulatedBayesIm(manipulatedBayesPm);

        // Create the updated BayesIm from the manipulated Bayes Im.
        Evidence evidence2 = new Evidence(evidence, manipulatedBayesIm);
        this.updatedBayesIm = new UpdatedBayesIm(bayesIm, evidence2);

        // Create the marginal calculator from the updated Bayes Im.
        this.cptInvariantMarginalCalculator =
                new CptInvariantMarginalCalculator(bayesIm,
                        evidence2);
    }

    public double getMarginal(int variable, int value) {
        return cptInvariantMarginalCalculator.getMarginal(variable, value);
    }

    public boolean isJointMarginalSupported() {
        return false;
    }

    public double getJointMarginal(int[] variables, int[] values) {
        throw new UnsupportedOperationException();
    }

    public double[] calculatePriorMarginals(int nodeIndex) {
        Evidence evidence = getEvidence();
        setEvidence(Evidence.tautology(evidence.getVariableSource()));

        double[] marginals = new double[evidence.getNumCategories(nodeIndex)];

        for (int i = 0;
             i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        setEvidence(evidence);
        return marginals;
    }

    public double[] calculateUpdatedMarginals(int nodeIndex) {
        double[] marginals = new double[evidence.getNumCategories(nodeIndex)];

        for (int i = 0;
             i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        return marginals;
    }

    /**
     * Prints out the most recent marginal.
     */
    public String toString() {
        return "CPT Invariant Updater, evidence = " + evidence;
    }

    //==============================PRIVATE METHODS=======================//

    private BayesIm createdManipulatedBayesIm(BayesPm updatedBayesPm) {
        return new MlBayesIm(updatedBayesPm, bayesIm, MlBayesIm.RANDOM);
    }

    private BayesPm createManipulatedBayesPm(Dag updatedGraph) {
        return new BayesPm(updatedGraph, bayesIm.getBayesPm());
    }

    private Dag createManipulatedGraph(Graph graph) {
        Dag updatedGraph = new Dag(graph);

        for (int i = 0; i < bayesIm.getNumNodes(); ++i) {
            if (evidence.isManipulated(i)) {
                Node node = evidence.getNode(i);
                List<Node> parents = updatedGraph.getParents(node);

                for (Node parent1 : parents) {
                    updatedGraph.removeEdge(node, parent1);
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




