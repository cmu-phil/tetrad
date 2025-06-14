package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serializable;

/**
 * The interface that each statistic needs to implement.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Statistic extends Serializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * The abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return This abbreviation.
     */
    String getAbbreviation();

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return This description.
     */
    String getDescription();

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model (can be null).
     * @param parameters The parameters (can be null).
     * @return The value of the statistic.
     */
    double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters);

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model (can be null).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return getValue(trueGraph, estGraph, dataModel, null);
    }

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param parameters The parameters (can be null).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph, Parameters parameters) {
        return getValue(trueGraph, estGraph, null, parameters);
    }

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph) {
        return getValue(trueGraph, estGraph, null, null);
    }

    /**
     * Returns a mapping of the statistic to the interval [0, 1], with higher being better. This is used for a
     * calculation of a utility for an algorithm. If the statistic is already between 0 and 1, you can just return the
     * statistic.
     *
     * @param value The value of the statistic.
     * @return The weight of the statistic, 0 to 1, higher is better.
     */
    double getNormValue(double value);
}
