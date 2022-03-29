package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class AdjacencyPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AP";
    }

    @Override
    public String getDescription() {
        return "Adjacency Precision";
    }

    @Override
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        final AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        final int adjTp = adjConfusion.getAdjTp();
        final int adjFp = adjConfusion.getAdjFp();
//        int adjFn = adjConfusion.getAdjFn();
//        int adjTn = adjConfusion.getAdjTn();
        return adjTp / (double) (adjTp + adjFp);
    }

    @Override
    public double getNormValue(final double value) {
        return value;
    }
}
