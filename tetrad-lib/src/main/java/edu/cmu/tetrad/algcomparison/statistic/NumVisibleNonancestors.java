package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class NumVisibleNonancestors implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#X->Y-Nonanc-VisibleEst";
    }

    @Override
    public String getDescription() {
        return "Number X-->Y for which X->Y is visible in est not X->...->Y in true";
    }

    @Override
//    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
//        int tp = 0;
//
//        for (Edge edge : estGraph.getEdges()) {
//            if (Edges.isDirectedEdge(edge)) {
//                Node x = Edges.getDirectedEdgeTail(edge);
//                Node y = Edges.getDirectedEdgeHead(edge);
//
//                if (!trueGraph.isAncestorOf(x, y) && !existsLatentCommonAncestor(trueGraph, edge)) {
//                    tp++;
//                }
//            }
//        }
//
//        return tp;
//    }

    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (estGraph.defVisible(edge)) {
                    if (!trueGraph.isAncestorOf(x, y)) {
                        tp++;
                    }
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
