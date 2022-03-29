package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
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
public class F1All implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "F1All";
    }

    @Override
    public String getDescription() {
        return "F1 statistic for adjacencies and orientations combined";
    }

    @Override
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        final AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        final ArrowConfusion arrowConfusion = new ArrowConfusion(trueGraph, estGraph);
        final int adjTp = adjConfusion.getAdjTp();
        final int adjFp = adjConfusion.getAdjFp();
        final int adjFn = adjConfusion.getAdjFn();
        final int adjTn = adjConfusion.getAdjTn();
        final int arrowTp = arrowConfusion.getArrowsTp();
        final int arrowFp = arrowConfusion.getArrowsFp();
        final int arrowFn = arrowConfusion.getArrowsFn();
        final int arrowTn = arrowConfusion.getArrowsTn();
        final double adjPrecision = adjTp / (double) (adjTp + adjFp);
        final double adjRecall = adjTp / (double) (adjTp + adjFn);
        final double arrowPrecision = arrowTp / (double) (arrowTp + arrowFp);
        final double arrowRecall = arrowTp / (double) (arrowTp + arrowFn);
        return 4 * (adjPrecision * adjRecall * arrowPrecision * arrowRecall)
                / (adjPrecision + adjRecall + arrowPrecision + arrowRecall);
    }

    @Override
    public double getNormValue(final double value) {
        return value;
    }
}
