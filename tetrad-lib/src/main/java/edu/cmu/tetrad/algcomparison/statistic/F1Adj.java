package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * Calculates the F1 statistic for adjacencies. See
 * </p>
 * https://en.wikipedia.org/wiki/F1_score
 * </p>
 * We use what's on this page called the "traditional" F1 statistic.
 *
 * @author Joseh Ramsey
 */
public class F1Adj implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "F1Adj";
    }

    @Override
    public String getDescription() {
        return "F1 statistic for adjacencies";
    }

    @Override
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        final AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        final int adjTp = adjConfusion.getAdjTp();
        final int adjFp = adjConfusion.getAdjFp();
        final int adjFn = adjConfusion.getAdjFn();
        final int adjTn = adjConfusion.getAdjTn();
        final double adjPrecision = adjTp / (double) (adjTp + adjFp);
        final double adjRecall = adjTp / (double) (adjTp + adjFn);
        return 2 * (adjPrecision * adjRecall) / (adjPrecision + adjRecall);
    }

    @Override
    public double getNormValue(final double value) {
        return value;
    }
}
