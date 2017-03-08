package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * Prints the number of edges in the true graph.
 *
 * @author jdramsey
 */
public class NumberOfEdgesTrue implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "EdgesT";
    }

    @Override
    public String getDescription() {
        return "Number of Edges in the True Graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        return trueGraph.getNumEdges();
    }

    @Override
    public double getNormValue(double value) {
        return Math.tanh(value);
    }
}
