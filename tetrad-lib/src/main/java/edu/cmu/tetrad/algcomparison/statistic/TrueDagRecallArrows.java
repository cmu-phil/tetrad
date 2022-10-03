package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class TrueDagRecallArrows implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DRA";
    }

    @Override
    public String getDescription() {
        return "Recall for Tails (DTPA / (DTPA + DFNA) compared to true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double tp = new TrueDagTruePositiveArrow().getValue(trueGraph, estGraph, dataModel);
        double fn = new TrueDagFalseNegativesArrows().getValue(trueGraph, estGraph, dataModel);
        return tp / (tp + fn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
