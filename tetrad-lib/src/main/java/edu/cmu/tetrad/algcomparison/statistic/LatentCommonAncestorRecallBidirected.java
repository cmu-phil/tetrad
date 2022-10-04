package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class LatentCommonAncestorRecallBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "LCABR";
    }

    @Override
    public String getDescription() {
        return "Proportion X<-Z->Y with latent Z for X*-*Y in estimated graph that are marked as bidirected edge";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double tp = new LatentCommonAncestorTruePositiveBidirected().getValue(trueGraph, estGraph, dataModel);
        double fn = new LatentCommonAncestorFalseNegativeBidirected().getValue(trueGraph, estGraph, dataModel);
        return tp / (tp + fn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
