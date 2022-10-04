package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class TrueDagPrecisionTails implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DTP";
    }

    @Override
    public String getDescription() {
        return "Precision for Tails (DTPT / (DTPT + DFPT) compared to true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double tp = new TrueDagTruePositiveTails().getValue(trueGraph, estGraph, dataModel);
        double fp = new TrueDagFalsePositiveTails().getValue(trueGraph, estGraph, dataModel);
        return tp / (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
