package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * The adjacency true positive rate. The true positives are the number of adjacencies in both the true and estimated
 * graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AdjacencyFpr implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AdjacencyFpr() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "AFPR";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Adjacency False Positive Rate";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the value of the statistic, given the true graph and the estimated graph.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        int adjFp = adjConfusion.getFp();
        int adjTn = adjConfusion.getTn();
        return adjFp / (double) (adjFp + adjTn);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a mapping of the statistic to the interval [0, 1], with higher being better. This is used for a
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
