package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

import java.io.Serial;

/**
 * The 2-cycle precision. This counts 2-cycles manually, wherever they occur in the graphs. The true positives are the
 * number of 2-cycles in both the true and estimated graphs. Thus, if the true does not contains X-&gt;Y,Y-&gt;X and
 * estimated graph does contain it, one false positive is counted.
 *
 * @author josephramsey, rubens (November 2016)
 * @version $Id: $Id
 */
public class TwoCyclePrecision implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "2CP";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "2-cycle precision";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        double TwoCycleTp = adjConfusion.getTwoCycleTp();
        double TwoCycleFp = adjConfusion.getTwoCycleFp();
        //        if (precision == 0) precision = Double.NaN;
        return TwoCycleTp / (TwoCycleTp + TwoCycleFp);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
