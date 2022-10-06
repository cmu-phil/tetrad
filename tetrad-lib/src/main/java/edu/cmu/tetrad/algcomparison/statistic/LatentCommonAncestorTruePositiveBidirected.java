package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class LatentCommonAncestorTruePositiveBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "LCATPB";
    }

    @Override
    public String getDescription() {
        return "Latent Common Ancestor True Positive Bidirected";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (existsLatentCommonAncestor(trueGraph, edge)) tp++;
            }
        }

        return tp;
    }

    public static boolean existsLatentCommonAncestor(Graph trueGraph, Edge edge) {
        for (Node c : trueGraph.getNodes()) {
            if (c == edge.getNode1() || c == edge.getNode2()) continue;
            if (c.getNodeType() == NodeType.LATENT) {
                if (trueGraph.isAncestorOf(c, edge.getNode1())
                        && trueGraph.isAncestorOf(c, edge.getNode2())) {
                    return true;
                }
            }
        }

        return false;

        // edge X*-*Y where there is a common ancestor of X and Y in the graph.

//        Set<Node> commonAncestors = new HashSet<>(trueGraph.getAncestors(Collections.singletonList(edge.getNode1())));
//        commonAncestors.retainAll(trueGraph.getAncestors(Collections.singletonList(edge.getNode2())));
//        commonAncestors.remove(edge.getNode1());
//        commonAncestors.remove(edge.getNode2());
//
//        for (Node n : commonAncestors) {
//            if (n.getNodeType() == NodeType.LATENT) {
//                return true;
//            }
//        }
//
//        return false;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
