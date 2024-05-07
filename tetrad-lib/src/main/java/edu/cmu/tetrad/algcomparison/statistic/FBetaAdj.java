package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

import java.io.Serial;

/**
 * Calculates the F1 statistic for adjacencies. See
 * <p>
 * <a href="https://en.wikipedia.org/wiki/F1_score">...</a>
 * <p>
 * We use what's on this page called the "traditional" F1 statistic.
 *
 * @author Joseh Ramsey
 * @version $Id: $Id
 */
public class FBetaAdj implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The beta parameter.
     */
    private double beta = 1;

    /**
     * Constructs a new instance of the algorithm.
     */
    public FBetaAdj() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "FBetaAdj";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "FBeta statistic for adjacencies";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFp = adjConfusion.getFp();
        int adjFn = adjConfusion.getFn();
        int adjTn = adjConfusion.getTn();
        double adjPrecision = adjTp / (double) (adjTp + adjFp);
        double adjRecall = adjTp / (double) (adjTp + adjFn);
        return (1 + beta * beta) * (adjPrecision * adjRecall)
               / (beta * beta * adjPrecision + adjRecall);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }

    /**
     * <p>Getter for the field <code>beta</code>.</p>
     *
     * @return a double
     */
    public double getBeta() {
        return beta;
    }

    /**
     * <p>Setter for the field <code>beta</code>.</p>
     *
     * @param beta a double
     */
    public void setBeta(double beta) {
        this.beta = beta;
    }
}
