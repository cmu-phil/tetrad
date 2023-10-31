package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 */
public class AdjacencyFn implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AdjacencyFn() {
    }

    /**
     * Returns the name of the statistic.
     *
     * @return The name.
     */
    @Override
    public String getAbbreviation() {
        return "AFN";
    }

    /**
     * Returns the description of the statistic.
     *
     * @return The description.
     */
    @Override
    public String getDescription() {
        return "Adjacency False Negatives";
    }

    /**
     * Returns the value of the statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The value of the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        return adjConfusion.getFn();
    }

    /**
     * Returns a mapping of the statistic to the interval [0, 1], with higher being better. This is used for a
     * calculation of a utility for an algorithm.If the statistic is already between 0 and 1, you can just return the
     * statistic.
     *
     * @param value The value of the statistic.
     * @return The weight of the statistic, 0 to 1, higher is better.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
