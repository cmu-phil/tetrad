package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AdjacencyFn implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AdjacencyFn() {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "AFN";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Adjacency False Negatives";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the value of the statistic, given the true graph and the estimated graph.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        return adjConfusion.getFn();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a mapping of the statistic to the interval [0, 1], with higher being better. This is used for a
     * calculation of a utility for an algorithm.If the statistic is already between 0 and 1, you can just return the
     * statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
