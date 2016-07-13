package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utilities.ArrowConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 7/10/16.
 */
public class MathewsCorrArrowStat implements Statistic {
    @Override
    public String getAbbreviation() {
        return "McArrow";
    }

    @Override
    public String getDescription() {
        return "Matthew's correlation coefficient for adjacencies";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        int arrowsTp = adjConfusion.getArrowsTp();
        int arrowsFp = adjConfusion.getArrowsFp();
        int arrowsFn = adjConfusion.getArrowsFn();
        int arrowsTn = adjConfusion.getArrowsTn();
        return mcc(arrowsTp, arrowsFp, arrowsTn, arrowsFn);
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
