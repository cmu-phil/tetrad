package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.BidirectedConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;

import static edu.cmu.tetrad.search.SearchGraphUtils.dagToPag;

/**
 * The bidirected edge precision.
 *
 * @author jdramsey
 */
public class BidirectedPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "PBP";
    }

    @Override
    public String getDescription() {
        return "Precision of bidirected edges compared to true PAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = dagToPag(trueGraph);
        BidirectedConfusion confusion = new BidirectedConfusion(pag, estGraph);
        return confusion.getTp() / (double) (confusion.getTp() + confusion.getFp());
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
