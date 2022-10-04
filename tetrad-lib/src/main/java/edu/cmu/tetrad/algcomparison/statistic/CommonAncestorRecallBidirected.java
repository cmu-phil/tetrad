package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class CommonAncestorRecallBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "CABR";
    }

    @Override
    public String getDescription() {
        return "Proportion of X<-...<-Z->...>Y for X*-*Y in estimated graph that are marked as bidirected edges";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double tp = new CommonAncestorTruePositiveBidirected().getValue(trueGraph, estGraph, dataModel);
        double fn = new CommonAncestorFalseNegativeBidirected().getValue(trueGraph, estGraph, dataModel);
        return tp / (tp + fn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
