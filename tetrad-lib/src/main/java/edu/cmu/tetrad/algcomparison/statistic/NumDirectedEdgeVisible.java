package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;

import java.io.Serial;

/**
 * Number of X-->Y for which X-->Y visible in true PAG.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumDirectedEdgeVisible implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NumDirectedEdgeVisible() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#X->Y-NL";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of X-->Y for which X-->Y visible in true PAG";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        Graph pag = GraphTransforms.dagToPag(trueGraph);

        for (Edge edge : pag.getEdges()) {
            if (pag.paths().defVisible(edge)) {
                tp++;
            }
        }

        return tp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
