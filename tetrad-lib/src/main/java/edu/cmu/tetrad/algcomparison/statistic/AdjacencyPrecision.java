package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AdjacencyPrecision implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AdjacencyPrecision() {

    }


    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "AP";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Adjacency Precision";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFp = adjConfusion.getFp();
        return adjTp / (double) (adjTp + adjFp);
    }

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
