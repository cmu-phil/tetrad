package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.interfaces.Statistic;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 7/10/16.
 */
public class ElapsedTimeStat implements Statistic {

    public ElapsedTimeStat() {
    }

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
    public double getUtility(double value) {
        return -value;
    }
}
