package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 7/10/16.
 */
public interface Statistic {
    String getAbbreviation();
    String getDescription();
    double getValue(Graph trueGraph, Graph estGraph);
    double getUtility(double value);
}
