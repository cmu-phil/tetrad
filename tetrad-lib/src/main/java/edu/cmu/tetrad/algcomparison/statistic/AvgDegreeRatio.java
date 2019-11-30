package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The average degree of the true graph.
 *
 * @author jdramsey
 */
public class AvgDegreeRatio implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AvgDRatio";
    }

    @Override
    public String getDescription() {
        return "Average degree ratio";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double avgDegreeEst = 2 * estGraph.getNumEdges() / (double) estGraph.getNumNodes();
        double avgDegreeTrue = 2 * trueGraph.getNumEdges() / (double) trueGraph.getNumNodes();
        return avgDegreeEst / avgDegreeTrue;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
