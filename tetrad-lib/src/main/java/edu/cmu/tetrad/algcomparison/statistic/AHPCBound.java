package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class AHPCBound implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AHPCBound";
    }

    @Override
    public String getDescription() {
        return "Bound for AHPC";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int V = trueGraph.getNumNodes();
        int E = trueGraph.getNumEdges();
        double a = 2 * E / (double) V;
        return 1.0 - a / (double) (V - 1);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
