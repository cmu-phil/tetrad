package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.LocalGraphConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * LocalGraphRecall implements the Statistic interface and represents the local graph recall statistic.
 * It calculates the recall of the estimated graph with respect to the true graph. The recall is defined as the ratio
 * of true positives (TP) to the sum of true positives and false negatives (TP + FN).
 */
public class LocalGraphRecall implements Statistic {
    @Override
    public String getAbbreviation() {
        return "LGR";
    }

    @Override
    public String getDescription() {
        return "Local Graph Recall";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        LocalGraphConfusion lgConfusion = new LocalGraphConfusion(trueGraph, estGraph);
        int lgTp = lgConfusion.getTp();
        int lgFn = lgConfusion.getFn();
        return lgTp / (double) (lgTp + lgFn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
