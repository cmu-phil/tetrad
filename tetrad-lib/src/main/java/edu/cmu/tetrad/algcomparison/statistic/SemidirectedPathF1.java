package edu.cmu.tetrad.algcomparison.statistic;

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
public class SemidirectedPathF1 implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "Semidirected-F1";
    }

    @Override
    public String getDescription() {
        return "F1 statistic for semidirected paths comparing the estimated graph to the true graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double precision = new SemidirectedPrecision().getValue(trueGraph, estGraph, dataModel);
        double recall = new SemidirectedRecall().getValue(trueGraph, estGraph, dataModel);
        return 2 * (precision * recall) / (precision + recall);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
