package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class AverageDegreeTrue implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AvgDegTrue";
    }

    @Override
    public String getDescription() {
        return "Average Degree for True Graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return 2.0 * trueGraph.getNumEdges() / trueGraph.getNumNodes();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
