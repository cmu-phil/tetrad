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
 * @version $Id: $Id
 */
public class NoSemidirectedF1 implements Statistic {
    private static final long serialVersionUID = 23L;

    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "NoSemidirected-F1";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "F1 statistic for nonancestry comparing the estimated graph to the true graph";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double precision = new NoSemidirectedPrecision().getValue(trueGraph, estGraph, dataModel);
        double recall = new NoSemidirectedRecall().getValue(trueGraph, estGraph, dataModel);
        return 2 * (precision * recall) / (precision + recall);
    }

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
