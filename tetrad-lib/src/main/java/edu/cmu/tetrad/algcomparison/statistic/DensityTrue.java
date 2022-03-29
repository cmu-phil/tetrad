package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class DensityTrue implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DensTrue";
    }

    @Override
    public String getDescription() {
        return "Density of True Graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return new AverageDegreeTrue().getValue(trueGraph, estGraph, dataModel)
                / (double) (trueGraph.getNumNodes() - 1);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
