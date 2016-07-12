package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.interfaces.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.utilities.ArrowConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 7/10/16.
 */
public class F1ArrowStat implements Statistic {
    @Override
    public String getAbbreviation() {
        return "F1Arrow";
    }

    @Override
    public String getDescription() {
        return "Matthew's correlation coeffficient for arrowacencies";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        ArrowConfusion arrowConfusion = new ArrowConfusion(trueGraph, estGraph);
        int arrowTp = arrowConfusion.getArrowsTp();
        int arrowFp = arrowConfusion.getArrowsFp();
        int arrowFn = arrowConfusion.getArrowsFn();
        int arrowTn = arrowConfusion.getArrowsTn();
        double arrowPrecision = arrowTp / (double) (arrowTp + arrowFp);
        double arrowRecall = arrowTp / (double) (arrowTp + arrowFn);
        return 2 * (arrowPrecision * arrowRecall) / (arrowPrecision + arrowRecall);
    }

    @Override
    public double getUtility(double value) {
        return value;
    }
}
