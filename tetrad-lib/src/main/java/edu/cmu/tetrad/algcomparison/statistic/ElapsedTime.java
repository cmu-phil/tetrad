package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.graph.Graph;

/**
 * Records the elapsed time of the algorithm in seconds. This is a placeholder, really;
 * the elapsed time is calculated by the comparison class and recorded if this statistic is
 * used.
 *
 * @author jdramsey
 */
public class ElapsedTime implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "E";
    }

    @Override
    public String getDescription() {
        return "Elapsed Time in Seconds";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        return Double.NaN; // This has to be handled separately.
    }

    @Override
    public double getNormValue(double value) {
        return 1 - Math.tanh(0.001 * value);
    }
}
