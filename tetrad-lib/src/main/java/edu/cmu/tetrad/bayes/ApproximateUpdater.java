///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.graph.Paths;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * Calculates updated marginals for a Bayes net by simulating data and calculating likelihood ratios. The method is as
 * follows. For P(A | B), enough sample points are simulated from the underlying BayesIm so that 1000 satisfy the
 * condition B. Then the maximum likelihood estimate of condition A is calculated.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ApproximateUpdater implements ManipulatingBayesUpdater {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The IM which this updater modifies.
     *
     * @serial Cannot be null.
     */
    private final BayesIm bayesIm;

    /**
     * Stores evidence for all variables.
     *
     * @serial Cannot be null.
     */
    private Evidence evidence;

    /**
     * Counts of data points for each variable value.
     *
     * @serial
     */
    private int[][] counts;

    /**
     * This is the source BayesIm after manipulation; all data simulations should be taken from this.
     *
     * @serial
     */
    private BayesIm manipulatedBayesIm;

    //==============================CONSTRUCTORS===========================//

    /**
     * Constructs a new updater for the given Bayes net.
     *
     * @param bayesIm the Bayes net to be updated.
     */
    public ApproximateUpdater(BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(Evidence.tautology(bayesIm));
    }

    /**
     * Constructs a new updater for the given Bayes net.
     *
     * @param bayesIm  the Bayes net to be updated.
     * @param evidence the evidence for the update.
     */
    public ApproximateUpdater(BayesIm bayesIm, Evidence evidence) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(evidence);
    }

    /**
     * Returns a simple exemplar of this class to test serialization.
     *
     * @return a simple exemplar of this class to test serialization.
     */
    public static ApproximateUpdater serializableInstance() {
        return new ApproximateUpdater(MlBayesIm.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    private static int[] getSinglePoint(BayesIm bayesIm, int[] tiers) {
        int[] point = new int[bayesIm.getNumNodes()];
        int[] combination = new int[bayesIm.getNumNodes()];
        RandomUtil randomUtil = RandomUtil.getInstance();

        for (int nodeIndex : tiers) {
            double cutoff = randomUtil.nextDouble();

            for (int k = 0; k < bayesIm.getNumParents(nodeIndex); k++) {
                combination[k] = point[bayesIm.getParent(nodeIndex, k)];
            }

            int rowIndex = bayesIm.getRowIndex(nodeIndex, combination);
            double sum = 0.0;

            for (int k = 0; k < bayesIm.getNumColumns(nodeIndex); k++) {
                double probability =
                        bayesIm.getProbability(nodeIndex, rowIndex, k);

                if (Double.isNaN(probability)) {
                    throw new IllegalStateException("Some probability " +
                                                    "values in the BayesIm are not filled in; " +
                                                    "cannot simulate data to do approximate updating.");
                }

                sum += probability;

                if (sum >= cutoff) {
                    point[nodeIndex] = k;
                    break;
                }
            }
        }

        return point;
    }

    /**
     * <p>Getter for the field <code>bayesIm</code>.</p>
     *
     * @return the Bayes instantiated model that is being updated.
     */
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    /**
     * <p>Getter for the field <code>manipulatedBayesIm</code>.</p>
     *
     * @return the Bayes instantiated model after manipulations have been applied.
     */
    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    /**
     * <p>getManipulatedGraph.</p>
     *
     * @return the graph for getManipulatedBayesIm().
     */
    public Graph getManipulatedGraph() {
        return this.manipulatedBayesIm.getDag();
    }

    /**
     * <p>getUpdatedBayesIm.</p>
     *
     * @return the updated Bayes IM, or null if there is no updated Bayes IM.
     */
    public BayesIm getUpdatedBayesIm() {
        return null;
    }

    /**
     * <p>Getter for the field <code>evidence</code>.</p>
     *
     * @return a copy of the getModel evidence.
     */
    public Evidence getEvidence() {
        return new Evidence(this.evidence);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets new evidence for the next update operation.
     */
    public void setEvidence(Evidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        if (evidence.isIncompatibleWith(this.bayesIm)) {
            throw new IllegalArgumentException("The variables for the given " +
                                               "evidence must be compatible with the Bayes IM being updated.");
        }

        this.evidence = new Evidence(evidence);

        Graph graph = this.bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedBayesPm = createUpdatedBayesPm(manipulatedGraph);
        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedBayesPm);

        this.counts = null;
    }

    /**
     * {@inheritDoc}
     */
    public double getMarginal(int variable, int value) {
        doUpdate();
        int sum = 0;

        for (int i = 0; i < this.manipulatedBayesIm.getNumColumns(variable); i++) {
            sum += this.counts[variable][i];
        }

        return this.counts[variable][value] / (double) sum;
    }

    /**
     * <p>isJointMarginalSupported.</p>
     *
     * @return a boolean
     */
    public boolean isJointMarginalSupported() {
        return false;
    }

    /**
     * Computes the joint marginal probability for the specified variables and their corresponding values.
     *
     * @param variables an array of integers representing the indices of the variables for which the joint marginal is to be computed.
     * @param values an array of integers representing the corresponding values of the variables for which the joint marginal is to be computed.
     * @return the joint marginal probability for the specified variables and values.
     */
    public double getJointMarginal(int[] variables, int[] values) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    public double[] calculateUpdatedMarginals(int nodeIndex) {
        double[] marginals = new double[this.evidence.getNumCategories(nodeIndex)];

        for (int i = 0;
             i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        return marginals;
    }

    //==============================PRIVATE METHODS=======================//

    /**
     * Prints out the most recent marginal.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Approximate updater, evidence = " + this.evidence;
    }

    private void doUpdate() {
        if (this.counts != null) {
            return;
        }

        this.counts = new int[this.manipulatedBayesIm.getNumNodes()][];

        for (int i = 0; i < this.manipulatedBayesIm.getNumNodes(); i++) {
            this.counts[i] = new int[this.manipulatedBayesIm.getNumColumns(i)];
        }

        // Get a tier ordering and convert it to an int array.
        Graph graph = getManipulatedGraph();
        Paths paths = graph.paths();
        List<Node> initialOrder = graph.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);
        int[] tiers = new int[tierOrdering.size()];

        for (int i = 0; i < tierOrdering.size(); i++) {
            tiers[i] = getManipulatedBayesIm().getNodeIndex(tierOrdering.get(i));
        }

        int numCounted = 0;

        // Adding an "ur"-counter--if the counting procedure exceeds this many
        // iterations, just give up. Handles the case there there simply aren't
        // enough (say, none) legitimate combinations to count, which was
        // leading to infinite loops. jdramsey 2/7/12
        int numSurveyed = 0;

        // Construct the sample.
        while (numCounted < 1000 && ++numSurveyed < 10000) {
            int[] point = ApproximateUpdater.getSinglePoint(getBayesIm(), tiers);

            if (this.evidence.getProposition().isPermissibleCombination(point)) {
                numCounted++;

                for (int j = 0; j < getManipulatedBayesIm().getNumNodes(); j++) {
                    this.counts[j][point[j]]++;
                }
            }
        }
    }

    private BayesIm createdUpdatedBayesIm(BayesPm updatedBayesPm) {
        return new MlBayesIm(updatedBayesPm, this.bayesIm, MlBayesIm.InitializationMethod.RANDOM);
    }

    private BayesPm createUpdatedBayesPm(Dag updatedGraph) {
        return new BayesPm(updatedGraph, this.bayesIm.getBayesPm());
    }

    private Dag createManipulatedGraph(Graph graph) {
        Dag updatedGraph = new Dag(graph);

        // alters graph for manipulated evidenceItems
        for (int i = 0; i < this.evidence.getNumNodes(); ++i) {
            if (this.evidence.isManipulated(i)) {
                Node node = this.evidence.getNode(i);
                node = updatedGraph.getNode(node.getName());
                Collection<Node> parents = updatedGraph.getParents(node);

                for (Node parent1 : parents) {
                    updatedGraph.removeEdge(node, parent1);
                }
            }
        }

        return updatedGraph;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
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
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
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




