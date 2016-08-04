package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * Calculates the Matthew's correlation coefficient for adjacencies. See this page in
 * Wikipedia:
 * </p>
 * https://en.wikipedia.org/wiki/Matthews_correlation_coefficient
 * </p>
 * We calculate the correlation directly from the confusion matrix.
 * </p>
 * if the true contains X*->Y and estimated graph either does not contain an edge from
 * X to Y or else does not contain an arrowhead at X for an edge from X to Y, one false
 * positive is counted. Similarly for false negatives
 *
 * @author jdramsey
 */
public class MathewsCorrArrow implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "McArrow";
    }

    @Override
    public String getDescription() {
        return "Matthew's correlation coefficient for arrowheads";
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
    public double getNormValue(double value) {
        return 0.5 + 0.5 * value;
    }

    private double mcc(double adjTp, double adjFp, double adjTn, double adjFn) {
        double a = adjTp * adjTn - adjFp * adjFn;
        double b = (adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn);

        if (b == 0) b = 1;

        return a / Math.sqrt(b);
    }
}
