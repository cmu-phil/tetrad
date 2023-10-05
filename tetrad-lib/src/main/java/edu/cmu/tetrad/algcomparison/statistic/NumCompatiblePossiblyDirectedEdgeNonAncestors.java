package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;

import static edu.cmu.tetrad.graph.GraphUtils.compatible;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class NumCompatiblePossiblyDirectedEdgeNonAncestors implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#CPDENA";
    }

    @Override
    public String getDescription() {
        return "Number compatible PD X-->Y for which X is not an ancestor of Y in true";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        GraphUtils.addPagColoring(estGraph);

        Graph pag = GraphSearchUtils.dagToPag(trueGraph);

        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            Edge trueEdge = pag.getEdge(edge.getNode1(), edge.getNode2());
            if (!compatible(edge, trueEdge)) continue;

            if (edge.getProperties().contains(Edge.Property.pd)) {
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
