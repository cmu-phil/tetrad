package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;

/**
 * The adjacency recall. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PagAdjacencyRecall implements Statistic {
    private static final long serialVersionUID = 23L;

    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "PAR";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Adjacency Recall compared to true PAG";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = GraphTransforms.dagToPag(trueGraph);

        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(pag, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFn = adjConfusion.getFn();
        return adjTp / (double) (adjTp + adjFn);
    }

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
