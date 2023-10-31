package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class NumColoredDD implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#X->Y-Colored-DD";
    }

    @Override
    public String getDescription() {
        return "Number of X-->Y in est where colored DD in Est";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int count = 0;

        GraphUtils.addPagColoring(estGraph);

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                if (edge.getProperties().contains(Edge.Property.dd)) {
                    count++;
                }
            }
        }

        return count;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
