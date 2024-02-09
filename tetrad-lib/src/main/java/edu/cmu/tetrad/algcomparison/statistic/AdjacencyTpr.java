package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency true positive rate. The true positives are the number of adjacencies in both the true and estimated
 * graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AdjacencyTpr implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AdjacencyTpr() {

    }

    /**
     * {@inheritDoc}
     *
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "ATPR";
    }

    /**
     * {@inheritDoc}
     *
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Adjacency True Positive Rate";
    }

    /**
     * {@inheritDoc}
     *
     * Returns the value of the statistic, given the true graph and the estimated graph.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFn = adjConfusion.getFn();
//        int adjTn = adjConfusion.getAdjTn();
        return adjTp / (double) (adjTp + adjFn);
    }

    /**
     * {@inheritDoc}
     *
     * Returns a mapping of the statistic to the interval [0, 1], with higher being better. This is used for a
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
