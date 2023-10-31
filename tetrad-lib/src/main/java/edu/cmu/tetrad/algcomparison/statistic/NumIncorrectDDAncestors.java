package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class NumIncorrectDDAncestors implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#IDDA";
    }

    @Override
    public String getDescription() {
        return "Number definitely direct X-->Y where not X~~>Y in true";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        GraphUtils.addPagColoring(estGraph);

        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getProperties().contains(Edge.Property.dd)) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (trueGraph.paths().isAncestorOf(x, y)) {
                    tp++;
                } else {
                    fp++;
                }
            }
        }

        return fp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
