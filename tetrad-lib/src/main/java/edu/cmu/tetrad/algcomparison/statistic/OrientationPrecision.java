package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.OrientationConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The orientation precision.
 *
 * @author bryanandrews, osephramsey
 */
public class OrientationPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "OP";
    }

    @Override
    public String getDescription() {
        return "Orientation Precision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        OrientationConfusion oriConfusion = new OrientationConfusion(trueGraph, estGraph);
        int oriTp = oriConfusion.getTp();
        int oriFp = oriConfusion.getFp();
        return oriTp / (double) (oriTp + oriFp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
