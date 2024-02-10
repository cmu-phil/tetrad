package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import org.apache.commons.math3.util.FastMath;

/**
 * Calculates the Matthew's correlation coefficient for adjacencies. See this page in Wikipedia:
 * <p>
 * https://en.wikipedia.org/wiki/Matthews_correlation_coefficient
 * <p>
 * We calculate the correlation directly from the confusion matrix.
 * <p>
 * if the true contains X*-&gt;Y and estimated graph either does not contain an edge from X to Y or else does not
 * contain an arrowhead at X for an edge from X to Y, one false positive is counted. Similarly, for false negatives
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MathewsCorrArrow implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "McArrow";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Matthew's correlation coefficient for arrowheads";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        int arrowsTp = adjConfusion.getTp();
        int arrowsFp = adjConfusion.getFp();
        int arrowsFn = adjConfusion.getFn();
        int arrowsTn = adjConfusion.getTn();
        return mcc(arrowsTp, arrowsFp, arrowsTn, arrowsFn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 0.5 + 0.5 * value;
    }

    private double mcc(double adjTp, double adjFp, double adjTn, double adjFn) {
        double a = adjTp * adjTn - adjFp * adjFn;
        double b = (adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn);

        if (b == 0) b = 1;

        return a / FastMath.sqrt(b);
    }
}
