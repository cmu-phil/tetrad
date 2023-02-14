package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class TrueDagTruePositiveDirectedPathNonancestor implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DTPA";
    }

    @Override
    public String getDescription() {
        return "True Positives for Arrows compared to true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (estGraph.paths().isAncestorOf(x, y)) {
                    if (!trueGraph.paths().isAncestorOf(y, x)) {
                        tp++;
                    }
                }
            }
        }

//        for (Edge edge : estGraph.getEdges()) {
//            if (edge.getEndpoint1() == Endpoint.ARROW) {
//                if (!trueGraph.isAncestorOf(edge.getNode1(), edge.getNode2())) {
//                    tp++;
//                }
//            }
//
//            if (edge.getEndpoint2() == Endpoint.ARROW) {
//                if (!trueGraph.isAncestorOf(edge.getNode2(), edge.getNode1())) {
//                    tp++;
//                }
//            }
//        }

        return tp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
