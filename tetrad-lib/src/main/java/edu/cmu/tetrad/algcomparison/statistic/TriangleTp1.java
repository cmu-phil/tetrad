package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.TriangleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class TriangleTp1 implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "TriangTp1";
    }

    @Override
    public String getDescription() {
        return "Number of triangle true positives missing one edge";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        TriangleConfusion confusion = new TriangleConfusion(trueGraph, estGraph, 0, 1);
        return confusion.getTp();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
