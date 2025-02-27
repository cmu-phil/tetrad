package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * The 2-cycle precision. This counts 2-cycles manually, wherever they occur in the graphs. The true positives are the
 * number of 2-cycles in both the true and estimated graphs. Thus, if the true does not contains X-&gt;Y,Y-&gt;X and
 * estimated graph does contain it, one false positive is counted.
 *
 * @author josephramsey, rubens (November 2016)
 * @version $Id: $Id
 */
public class TwoCycleFalsePositive implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * This class represents a statistic that calculates the 2-cycle precision. It counts 2-cycles manually, wherever
     * they occur in the graphs. The true positives are the number of 2-cycles in both the true and estimated graphs.
     * Thus, if the true does not contains X-&gt;Y,Y-&gt;X and estimated graph does contain it, one false positive is
     * counted.
     */
    public TwoCycleFalsePositive() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "2CFP";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "2-cycle false positive";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        return adjConfusion.getTwoCycleFp();

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(value);
    }
}
