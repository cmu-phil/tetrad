package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.BidirectedConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;

/**
 * The bidirected edge precision.
 *
 * @author jdramsey
 */
public class BidirectedPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BP";
    }

    @Override
    public String getDescription() {
        return "Precision of bidirected edges compared to true PAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = new DagToPag(trueGraph).convert();
        BidirectedConfusion confusion = new BidirectedConfusion(pag, estGraph);
        return confusion.getTp() / (double) (confusion.getTp() + confusion.getFp());
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
