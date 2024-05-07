package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.io.Serial;

/**
 * Return a 1 if the graph is exactly right, 0 otherwise.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphExactlyRight implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for GraphExactlyRight.</p>
     */
    public GraphExactlyRight() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "ExactlyRight";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Graph exactly right";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());
        return trueGraph.equals(estGraph) ? 1 : 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
