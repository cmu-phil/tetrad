package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

/**
 * Return a 1 if the graph is exactly right, 0 otherwise.
 *
 * @author jdramsey
 */
public class GraphExactlyRight implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "ExactlyRight";
    }

    @Override
    public String getDescription() {
        return "Graph exactly right";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());
        return trueGraph.equals(estGraph) ? 1 : 0;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
