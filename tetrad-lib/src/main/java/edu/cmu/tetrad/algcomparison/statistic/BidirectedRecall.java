package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.BidirectedConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * The bidirected edge precision.
 *
 * @author jdramsey
 */
public class BidirectedRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "PBR";
    }

    @Override
    public String getDescription() {
        return "Recall of bidirected edges compared to the true PAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = SearchGraphUtils.dagToPag(trueGraph);
        BidirectedConfusion confusion = new BidirectedConfusion(pag, estGraph);
        return confusion.getTp() / (double) (confusion.getTp() + confusion.getFn());
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
