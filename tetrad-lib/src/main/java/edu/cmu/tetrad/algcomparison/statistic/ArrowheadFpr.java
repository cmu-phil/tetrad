package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency true positive rate. The true positives are the number of adjacencies in both the true and estimated
 * graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ArrowheadFpr implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public ArrowheadFpr() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "AHFPR";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Arrowhead False Positive Rate";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        int adjFp = adjConfusion.getFp();
        int adjTn = adjConfusion.getTn();
        return adjFp / (double) (adjFp + adjTn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
