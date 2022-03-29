package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency true positive rate. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class ArrowheadFPR implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AHFPR";
    }

    @Override
    public String getDescription() {
        return "Arrowhead False Positive Rate";
    }

    @Override
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        final ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        final int adjTp = adjConfusion.getArrowsTp();
        final int adjFp = adjConfusion.getArrowsFp();
        final int adjFn = adjConfusion.getArrowsFn();
        final int adjTn = adjConfusion.getArrowsTn();
        return adjFp / (double) (adjFp + adjTn);
    }

    @Override
    public double getNormValue(final double value) {
        return value;
    }
}
