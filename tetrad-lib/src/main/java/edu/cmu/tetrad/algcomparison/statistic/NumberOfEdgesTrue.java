package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import org.apache.commons.math3.util.FastMath;

/**
 * Prints the number of edges in the true graph.
 *
 * @author josephramsey
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
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return trueGraph.getNumEdges();
    }

    @Override
    public double getNormValue(double value) {
        return FastMath.tanh(value);
    }
}
