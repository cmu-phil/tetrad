package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.TailConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Calculates the F1 statistic for tails.
 * <p>
 * <a href="https://en.wikipedia.org/wiki/F1_score">...</a>
 * <p>
 * We use what's on this page called the "traditional" F1 statistic. A true positive arrow is counted for X*-&gt;Y in
 * the estimated graph if X is not adjacent to Y or X--Y or X&lt;--Y. Similarly for false negatives. *
 */
public class F1Tail implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public F1Tail() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "F1Tail";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "F1 statistic for tails";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        TailConfusion tailConfusion = new TailConfusion(trueGraph, estGraph);
        int arrowTp = tailConfusion.getArrowsTp();
        int arrowFp = tailConfusion.getArrowsFp();
        int arrowFn = tailConfusion.getArrowsFn();
        int twoCycleTp = tailConfusion.getTwoCycleTp();
        int twoCycleFp = tailConfusion.getTwoCycleFp();
        int twoCycleFn = tailConfusion.getTwoCycleFn();

        double arrowsCyclesPrecision = (arrowTp + twoCycleTp) / (double) ((arrowTp + twoCycleTp) + (arrowFp + twoCycleFp));
        double arrowsCyclesRecall = (arrowTp + twoCycleTp) / (double) ((arrowTp + twoCycleTp) + (arrowFn + twoCycleFn));
        return 2 * (arrowsCyclesPrecision * arrowsCyclesRecall) / (arrowsCyclesPrecision + arrowsCyclesRecall);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
