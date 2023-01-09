package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * Calculates the F1 statistic for adjacencies. See
 *
 * https://en.wikipedia.org/wiki/F1_score
 *
 * We use what's on this page called the "traditional" F1 statistic.
 *
 * @author Joseh Ramsey
 */
public class NonancestorF1 implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "Nonancestor-F1";
    }

    @Override
    public String getDescription() {
        return "F1 statistic for nonancestry comparing the estimated graph to the true graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double precision = new NonancestorPrecision().getValue(trueGraph, estGraph, dataModel);
        double recall = new NonancestorRecall().getValue(trueGraph, estGraph, dataModel);
        return 2 * (precision * recall) / (precision + recall);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
