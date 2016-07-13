package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utilities.ArrowConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * The arrow recall. This counts arrowheads maniacally, wherever they occur in the graphs.
 * The true positives are the number of arrowheads in both the true and estimated graphs.
 * Thus, if the true contains X*->Y and estimated graph either does not contain an edge from
 * X to Y or else does not contain an arrowhead at X for an edge from X to Y, one false
 * positive is counted. Similarly for false negatives.
 * @author jdramsey
 */
public class ArrowRecallStat implements Statistic {
    @Override
    public String getAbbreviation() {
        return "OR";
    }

    @Override
    public String getDescription() {
        return "Orientation (Arrow) recall";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        int arrowsTp = adjConfusion.getArrowsTp();
        int arrowsFp = adjConfusion.getArrowsFp();
        int arrowsFn = adjConfusion.getArrowsFn();
        int arrowsTn = adjConfusion.getArrowsTn();
        return arrowsTp / (double) (arrowsTp + arrowsFn);
    }

    @Override
    public double getUtility(double value) {
        return value;
    }
}
