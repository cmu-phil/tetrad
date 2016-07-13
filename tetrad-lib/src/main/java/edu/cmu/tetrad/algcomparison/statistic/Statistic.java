package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.graph.Graph;

/**
 * The interface that each statistic needs to implement.
 * @author jdramsey
 */
public interface Statistic {

    /**
     * The abbreviation for the statistic. This will be printed at the top of each
     * column.
     * @return Thsi abbreviation.
     */
    String getAbbreviation();

    /**
     * Returns a short one-line description of this statistic. This will be printed at the
     * beginning of the report.
     * @return This description.
     */
    String getDescription();

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     * @param trueGraph The true graph (DAG, Pattern, PAG).
     * @param estGraph The estimated graph (same type).
     * @return The value of the statistic.
     */
    double getValue(Graph trueGraph, Graph estGraph);

    /**
     * Returns the utility value of this statistic. Ideally, this is a number between 0 and 1
     * that indicates how well the algorithm did on this statistic. If the statistic is already
     * between 0 and 1, you can just return the statistic. If the sense of the statistic is
     * negative, return the negation of the statistic.
     * @param value The value of the statistic.
     * @return The utility value of the statistic.
     */
    double getUtility(double value);
}
