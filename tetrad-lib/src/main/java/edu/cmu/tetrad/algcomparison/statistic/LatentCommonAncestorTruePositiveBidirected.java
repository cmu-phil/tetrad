package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

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
                if (!trueGraph.isAncestorOf(edge.getNode1(), edge.getNode2())
                        && !trueGraph.isAncestorOf(edge.getNode2(), edge.getNode1())) {
                    tp++;
                }
            }
        }

//        for (Edge edge : estGraph.getEdges()) {
//            if (Edges.isBidirectedEdge(edge)) {
//
//                Set<Node> commonAncestors = new HashSet<>(trueGraph.getAncestors(Collections.singletonList(edge.getNode1())));
//                commonAncestors.retainAll(trueGraph.getAncestors(Collections.singletonList(edge.getNode2())));
//                commonAncestors.remove(edge.getNode1());
//                commonAncestors.remove(edge.getNode2());
//
//                if (!commonAncestors.isEmpty()) tp++;
//
////                for (Node c : commonAncestors) {
//////                    if (c.getNodeType() == NodeType.LATENT) {
////                        tp++;
////                        break;
//////                    }
////                }
//            }
//        }

        return tp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
