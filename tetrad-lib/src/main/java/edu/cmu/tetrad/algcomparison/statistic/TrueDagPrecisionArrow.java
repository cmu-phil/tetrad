package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class TrueDagPrecisionArrow implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DAP";
    }

    @Override
    public String getDescription() {
        return "Precision for Arrows (DTPA / (DTPA + DFPA) compared to true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double tp = new TrueDagTruePositiveArrow().getValue(trueGraph, estGraph, dataModel);
        double fp = new TrueDagFalsePositiveArrow().getValue(trueGraph, estGraph, dataModel);
        return tp / (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
