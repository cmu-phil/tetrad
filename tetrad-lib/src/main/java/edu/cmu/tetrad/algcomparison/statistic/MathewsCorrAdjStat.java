package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.interfaces.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.utilities.AdjacencyConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 7/10/16.
 */
public class MathewsCorrAdjStat implements Statistic {
    @Override
    public String getAbbreviation() {
        return "McAdj";
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
        return mcc(adjTp, adjFp, adjTn, adjFn);
    }

    @Override
    public double getUtility(double value) {
        return value;
    }

    private double mcc(double adjTp, double adjFp, double adjTn, double adjFn) {
        double a = adjTp * adjTn - adjFp * adjFn;
        double b = (adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn);

        if (b == 0) b = 1;

        return a / Math.sqrt(b);
    }
}
