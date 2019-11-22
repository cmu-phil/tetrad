package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.TriangleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class UnshieldedTripleFP implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "UTFP";
    }

    @Override
    public String getDescription() {
        return "Unshielded triple false positives";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        TriangleConfusion confusion = new TriangleConfusion(trueGraph, estGraph, 1, 1);
        return confusion.getFp();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
