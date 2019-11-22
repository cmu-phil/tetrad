package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.TriangleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The number of triangle false negatives.
 *
 * @author jdramsey
 */
public class TriangleFn implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "TriangFn";
    }

    @Override
    public String getDescription() {
        return "Number of triangle false negatives";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        TriangleConfusion confusion = new TriangleConfusion(trueGraph, estGraph, 0, 0);
        return confusion.getFn();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
