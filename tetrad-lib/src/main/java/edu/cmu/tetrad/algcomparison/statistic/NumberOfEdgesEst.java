package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * Prints the number of edges in the true graph.
 *
 * @author jdramsey
 */
public class NumberOfEdgesEst implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "EdgesEst";
    }

    @Override
    public String getDescription() {
        return "Number of Edges in the Estimated Graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return estGraph.getNumEdges();
    }

    @Override
    public double getNormValue(double value) {
        return Math.tanh(value);
    }
}
