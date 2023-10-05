package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.BidirectedConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

import static edu.cmu.tetrad.search.utils.GraphSearchUtils.dagToPag;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class BidirectedTP implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BTP";
    }

    @Override
    public String getDescription() {
        return "Number of true positive bidirected edges";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = dagToPag(trueGraph);
        BidirectedConfusion confusion = new BidirectedConfusion(pag, estGraph);
        return confusion.getTp();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
