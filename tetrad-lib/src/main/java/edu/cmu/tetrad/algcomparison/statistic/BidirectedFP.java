package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.BidirectedConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;

/**
 * The bidirected false negatives.
 *
 * @author jdramsey
 */
public class BidirectedFP implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BFP";
    }

    @Override
    public String getDescription() {
        return "Bidirected False Positives";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = new DagToPag(trueGraph).convert();
        BidirectedConfusion confusion = new BidirectedConfusion(pag, estGraph);
        return confusion.getFp();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
