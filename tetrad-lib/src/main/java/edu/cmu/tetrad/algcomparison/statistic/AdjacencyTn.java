package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 */
public class AdjacencyTn implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AdjacencyTn() {

    }

    /**
     * Returns the name of the statistic.
     * @return The name.
     */
    @Override
    public String getAbbreviation() {
        return "ATN";
    }

    /**
     * Returns the description of the statistic.
     * @return The description.
     */
    @Override
    public String getDescription() {
        return "Adjacency True Negatives";
    }

    /**
     * Returns the value of the statistic, given the true graph and the estimated graph.
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return The value of the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        return adjConfusion.getTn();
    }

    /**
     * Returns a mapping of the statistic to the interval [0, 1], with higher being better. This is used for a
     * @param value The value of the statistic.
     * @return The normalized value.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
