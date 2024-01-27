package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * Calculates the F1 statistic for adjacencies. See
 * <p>
 * https://en.wikipedia.org/wiki/F1_score
 * <p>
 * We use what's on this page called the "traditional" F1 statistic.
 *
 * @author Joseh Ramsey
 */
public class FBetaAdj implements Statistic {
    private static final long serialVersionUID = 23L;

    private double beta = 1;

    @Override
    public String getAbbreviation() {
        return "FBetaAdj";
    }

    @Override
    public String getDescription() {
        return "FBeta statistic for adjacencies";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFp = adjConfusion.getFp();
        int adjFn = adjConfusion.getFn();
        int adjTn = adjConfusion.getTn();
        double adjPrecision = adjTp / (double) (adjTp + adjFp);
        double adjRecall = adjTp / (double) (adjTp + adjFn);
        return (1 + beta * beta) * (adjPrecision * adjRecall)
                / (beta * beta * adjPrecision + adjRecall);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }

    public double getBeta() {
        return beta;
    }

    public void setBeta(double beta) {
        this.beta = beta;
    }
}
