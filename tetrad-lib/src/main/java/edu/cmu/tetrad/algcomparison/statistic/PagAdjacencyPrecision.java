package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

import static edu.cmu.tetrad.search.SearchGraphUtils.dagToPag;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class PagAdjacencyPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "PAP";
    }

    @Override
    public String getDescription() {
        return "Adjacency Precision compared to true PAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = dagToPag(trueGraph);

        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(pag, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFp = adjConfusion.getFp();
        return adjTp / (double) (adjTp + adjFp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
