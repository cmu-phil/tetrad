package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class ActualPrecisionArrow implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "APA";
    }

    @Override
    public String getDescription() {
        return "Actual Precision Arrow (ATPA / (ATPA + AFPA)";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double tp = new ActualTruePositiveArrow().getValue(trueGraph, estGraph, dataModel);
        double fp = new ActualFalsePositiveArrow().getValue(trueGraph, estGraph, dataModel);
        return tp / (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
