package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.BidirectedConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class BidirectedTP implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BTP";
    }

    @Override
    public String getDescription() {
        return "Bidirected True Positives";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        BidirectedConfusion adjConfusion = new BidirectedConfusion(trueGraph, estGraph);
        return adjConfusion.getTp();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
