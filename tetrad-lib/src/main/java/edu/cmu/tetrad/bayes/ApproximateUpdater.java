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
import edu.cmu.tetrad.util.RandomUtil;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.List;

/**
 * Calculates updated marginals for a Bayes net by simulating data and
 * calculating likelihood ratios. The method is as follows. For P(A | B), enough
 * sample points are simulated from the underlying BayesIm so that 1000 satisfy
 * the condition B. Then the maximum likelihood estimate of condition A is
 * calculated.
 *
 * @author Joseph Ramsey
 */
public final class ApproximateUpdater implements ManipulatingBayesUpdater {
    static final long serialVersionUID = 23L;

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
     * This is the source BayesIm after manipulation; all data simulations
     * should be taken from this.
     *
     * @serial
     */
    private BayesIm manipulatedBayesIm;

    //==============================CONSTRUCTORS===========================//

    public ApproximateUpdater(final BayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(Evidence.tautology(bayesIm));
    }

    /**
     * Constructs a new updater for the given Bayes net.
     */
    public ApproximateUpdater(final BayesIm bayesIm, final Evidence evidence) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        this.bayesIm = bayesIm;
        setEvidence(evidence);
    }

    /**
     * @return a simple exemplar of this class to test serialization.
     */
    public static ApproximateUpdater serializableInstance() {
        return new ApproximateUpdater(MlBayesIm.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    /**
     * @return the Bayes instantiated model that is being updated.
     */
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    /**
     * @return the Bayes instantiated model after manipulations have been
     * applied.
     */
    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    /**
     * @return the graph for getManipulatedBayesIm().
     */
    public Graph getManipulatedGraph() {
        return this.manipulatedBayesIm.getDag();
    }

    /**
     * @return the updated Bayes IM, or null if there is no updated Bayes IM.
     */
    public BayesIm getUpdatedBayesIm() {
        return null;
    }

    /**
     * @return a copy of the getModel evidence.
     */
    public Evidence getEvidence() {
        return new Evidence(this.evidence);
    }

    /**
     * Sets new evidence for the next update operation.
     */
    public void setEvidence(final Evidence evidence) {
        if (evidence == null) {
            throw new NullPointerException();
        }

        if (evidence.isIncompatibleWith(this.bayesIm)) {
            throw new IllegalArgumentException("The variables for the given " +
                    "evidence must be compatible with the Bayes IM being updated.");
        }

        this.evidence = new Evidence(evidence);

        final Graph graph = this.bayesIm.getBayesPm().getDag();
        final Dag manipulatedGraph = createManipulatedGraph(graph);
        final BayesPm manipulatedBayesPm = createUpdatedBayesPm(manipulatedGraph);
        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedBayesPm);

        this.counts = null;
    }

    public double getMarginal(final int variable, final int value) {
        doUpdate();
        int sum = 0;

        for (int i = 0; i < this.manipulatedBayesIm.getNumColumns(variable); i++) {
            sum += this.counts[variable][i];
        }

        return this.counts[variable][value] / (double) sum;
    }

    public boolean isJointMarginalSupported() {
        return false;
    }

    /**
     * @return the joint marginal.
     */
    public double getJointMarginal(final int[] variables, final int[] values) {
        throw new UnsupportedOperationException();
    }

    public double[] calculatePriorMarginals(final int nodeIndex) {
        final Evidence evidence = getEvidence();
        setEvidence(Evidence.tautology(evidence.getVariableSource()));

        final double[] marginals = new double[evidence.getNumCategories(nodeIndex)];

        for (int i = 0;
             i < getBayesIm().getNumColumns(nodeIndex); i++) {
            marginals[i] = getMarginal(nodeIndex, i);
        }

        setEvidence(evidence);
        return marginals;
    }

    public double[] calculateUpdatedMarginals(final int nodeIndex) {
        final double[] marginals = new double[this.evidence.getNumCategories(nodeIndex)];

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
        return "Approximate updater, evidence = " + this.evidence;
    }

    //==============================PRIVATE METHODS=======================//

    private void doUpdate() {
        if (this.counts != null) {
            return;
        }

        this.counts = new int[this.manipulatedBayesIm.getNumNodes()][];

        for (int i = 0; i < this.manipulatedBayesIm.getNumNodes(); i++) {
            this.counts[i] = new int[this.manipulatedBayesIm.getNumColumns(i)];
        }

        // Get a tier ordering and convert it to an int array.
        final Graph graph = getManipulatedGraph();
        final List<Node> tierOrdering = graph.getCausalOrdering();
        final int[] tiers = new int[tierOrdering.size()];

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
            final int[] point = ApproximateUpdater.getSinglePoint(getBayesIm(), tiers);

            if (this.evidence.getProposition().isPermissibleCombination(point)) {
                numCounted++;

                for (int j = 0; j < getManipulatedBayesIm().getNumNodes(); j++) {
                    this.counts[j][point[j]]++;
                }
            }
        }
    }

    private BayesIm createdUpdatedBayesIm(final BayesPm updatedBayesPm) {
        return new MlBayesIm(updatedBayesPm, this.bayesIm, MlBayesIm.RANDOM);
    }

    private BayesPm createUpdatedBayesPm(final Dag updatedGraph) {
        return new BayesPm(updatedGraph, this.bayesIm.getBayesPm());
    }

    private Dag createManipulatedGraph(final Graph graph) {
        final Dag updatedGraph = new Dag(graph);

        // alters graph for manipulated evidenceItems
        for (int i = 0; i < this.evidence.getNumNodes(); ++i) {
            if (this.evidence.isManipulated(i)) {
                Node node = this.evidence.getNode(i);
                node = updatedGraph.getNode(node.getName());
                final Collection<Node> parents = updatedGraph.getParents(node);

                for (final Node parent1 : parents) {
                    updatedGraph.removeEdge(node, parent1);
                }
            }
        }

        return updatedGraph;
    }

    private static int[] getSinglePoint(final BayesIm bayesIm, final int[] tiers) {
        final int[] point = new int[bayesIm.getNumNodes()];
        final int[] combination = new int[bayesIm.getNumNodes()];
        final RandomUtil randomUtil = RandomUtil.getInstance();

        for (final int nodeIndex : tiers) {
            final double cutoff = randomUtil.nextDouble();

            for (int k = 0; k < bayesIm.getNumParents(nodeIndex); k++) {
                combination[k] = point[bayesIm.getParent(nodeIndex, k)];
            }

            final int rowIndex = bayesIm.getRowIndex(nodeIndex, combination);
            double sum = 0.0;

            for (int k = 0; k < bayesIm.getNumColumns(nodeIndex); k++) {
                final double probability =
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
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     */
    private void readObject(final ObjectInputStream s)
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




