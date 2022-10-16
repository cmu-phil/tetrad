package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.awt.*;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class NumGreenNonancestors implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#VisNA";
    }

    @Override
    public String getDescription() {
        return "Number green (visible) edges X-->Y in estimates for which X is not an ancestor of Y in true";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
//        if (!estGraph.isPag()) return 0;
        int tp = 0;
        int fp = 0;

        GraphUtils.addPagColoring(estGraph);

        for (Edge edge : estGraph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) continue;

            if (edge.getProperties().contains(Edge.Property.nl)) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (trueGraph.isAncestorOf(x, y)) {
                    System.out.println("Ancestor(x, y): " + Edges.directedEdge(x, y));
                    tp++;
                } else {
                    System.out.println("Not Ancestor(x, y): " + Edges.directedEdge(x, y));

                    trueGraph.isAncestorOf(x, y);

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
