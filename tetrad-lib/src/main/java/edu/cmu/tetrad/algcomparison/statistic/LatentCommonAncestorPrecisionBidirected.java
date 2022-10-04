package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class LatentCommonAncestorPrecisionBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "LCABP";
    }

    @Override
    public String getDescription() {
        return "Proportion of X<->Y in estimaged graph where  X<-Z->Y with latent Z for X*-*Y in true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double tp = new LatentCommonAncestorTruePositiveBidirected().getValue(trueGraph, estGraph, dataModel);
        double fp = new LatentCommonAncestorFalsePositiveBidirected().getValue(trueGraph, estGraph, dataModel);
        return tp / (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
