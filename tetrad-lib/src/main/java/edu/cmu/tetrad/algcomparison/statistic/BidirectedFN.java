package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.BidirectedConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected false negatives.
 *
 * @author jdramsey
 */
public class BidirectedFN implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BFP";
    }

    @Override
    public String getDescription() {
        return "Bidirected False Negatives";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        BidirectedConfusion adjConfusion = new BidirectedConfusion(trueGraph, estGraph);
        return adjConfusion.getFn();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
