package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The 2-cycle recall. This counts 2-cycles manually, wherever they occur in the graphs.
 * The true positives are the number of 2-cycles in both the true and estimated graphs.
 * Thus, if the true contains X->Y,Y->X and estimated graph does not contain it, one false negative
 * is counted.
 *
 * @author jdramsey, rubens (November 2016)
 */
public class TwoCycleRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "2CR";
    }

    @Override
    public String getDescription() {
        return "2-cycle recall";
    }

    @Override
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        final ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        final double TwoCycleTp = adjConfusion.getTwoCycleTp();
        final double TwoCycleFn = adjConfusion.getTwoCycleFn();
        final double recall = TwoCycleTp / (TwoCycleTp + TwoCycleFn);
//        if (recall == 0) recall = Double.NaN;
        return recall;

    }

    @Override
    public double getNormValue(final double value) {
        return value;
    }
}
