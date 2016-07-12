package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.interfaces.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.utilities.AdjacencyConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 7/10/16.
 */
public class F1AdjStat implements Statistic {
    @Override
    public String getAbbreviation() {
        return "F1Adj";
    }

    @Override
    public String getDescription() {
        return "Matthew's correlation coeffficient for adjacencies";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getAdjTp();
        int adjFp = adjConfusion.getAdjFp();
        int adjFn = adjConfusion.getAdjFn();
        int adjTn = adjConfusion.getAdjTn();
        double adjPrecision = adjTp / (double) (adjTp + adjFp);
        double adjRecall = adjTp / (double) (adjTp + adjFn);
        return 2 * (adjPrecision * adjRecall) / (adjPrecision + adjRecall);
    }

    @Override
    public double getUtility(double value) {
        return value;
    }
}
