package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The average degree of the true graph.
 *
 * @author jdramsey
 */
public class AvgDegreeTrueGraph implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AvgDTrue";
    }

    @Override
    public String getDescription() {
        return "Average degree of True Graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int V = trueGraph.getNumNodes();
        int E = trueGraph.getNumEdges();
        return 2 * E / (double) V;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
