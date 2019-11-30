package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The average degree of the true graph.
 *
 * @author jdramsey
 */
public class AvgDegreeEstGraph implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AvgDEst";
    }

    @Override
    public String getDescription() {
        return "Average degree of Estimated Graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int V = estGraph.getNumNodes();
        int E = estGraph.getNumEdges();
        return 2 * E / (double) V;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
