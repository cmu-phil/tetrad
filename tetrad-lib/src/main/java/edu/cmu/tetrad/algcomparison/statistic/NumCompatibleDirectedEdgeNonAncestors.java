package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import static edu.cmu.tetrad.algcomparison.statistic.LatentCommonAncestorTruePositiveBidirected.existsLatentCommonAncestor;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class NumCompatibleDirectedEdgeNonAncestors implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#CompDNA";
    }

    @Override
    public String getDescription() {
        return "Number X-->Y for which not X<~~L->..->Y in true";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        GraphUtils.addPagColoring(estGraph);
//        Graph pag = SearchGraphUtils.dagToPag(trueGraph);

        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
//            Edge trueEdge = pag.getEdge(edge.getNode1(), edge.getNode2());
//            if (!compatible(edge, trueEdge)) continue;

            if (edge.getProperties().contains(Edge.Property.dd) || edge.getProperties().contains(Edge.Property.pd)) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (!trueGraph.paths().isAncestorOf(x, y) && !existsLatentCommonAncestor(trueGraph, edge)) {
                    tp++;
                } else {
                    fp++;
                }
            }
        }

        return tp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
