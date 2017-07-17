package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
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
    public double getValue(Graph trueGraph, Graph estGraph) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        ArrowConfusion arrowConfusion = new ArrowConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getAdjTp();
        int adjFp = adjConfusion.getAdjFp();
        int adjFn = adjConfusion.getAdjFn();
        int adjTn = adjConfusion.getAdjTn();
        int arrowTp = arrowConfusion.getArrowsTp();
        int arrowFp = arrowConfusion.getArrowsFp();
        int arrowFn = arrowConfusion.getArrowsFn();
        int arrowTn = arrowConfusion.getArrowsTn();
        double adjPrecision = adjTp / (double) (adjTp + adjFp);
        double adjRecall = adjTp / (double) (adjTp + adjFn);
        double arrowPrecision = arrowTp / (double) (arrowTp + arrowFp);
        double arrowRecall = arrowTp / (double) (arrowTp + arrowFn);
        return 4 * (adjPrecision * adjRecall * arrowPrecision * arrowRecall)
                / (adjPrecision + adjRecall + arrowPrecision + arrowRecall);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
