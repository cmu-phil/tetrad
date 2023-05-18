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
public class NumColoredPD implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#X->Y-Colored-PD";
    }

    @Override
    public String getDescription() {
        return "Number of X-->Y in est where colored PD in Est";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int count = 0;

        GraphUtils.addPagColoring(estGraph);

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                if (edge.getProperties().contains(Edge.Property.pd)) {
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
