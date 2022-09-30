package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class ActualTruePositiveBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "ATPB";
    }

    @Override
    public String getDescription() {
        return "Actual True Positive Bidirected";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                int count = 0;

                if (!trueGraph.isAncestorOf(edge.getNode1(), edge.getNode2())) {
                    count++;
                }

                if (!trueGraph.isAncestorOf(edge.getNode2(), edge.getNode1())) {
                    count++;
                }

                if (count == 2) tp++;
            }

        }

//        for (Edge edge : estGraph.getEdges()) {
//            if (Edges.isBidirectedEdge(edge)) {
//                Set<Node> commonAncestors = new HashSet<>(trueGraph.getAncestors(Collections.singletonList(edge.getNode1())));
//                commonAncestors.retainAll(trueGraph.getAncestors(Collections.singletonList(edge.getNode2())));
//                commonAncestors.remove(edge.getNode1());
//                commonAncestors.remove(edge.getNode2());
//
//                for (Node c : commonAncestors) {
//                    if (c.getNodeType() == NodeType.LATENT) {
//                        tp++;
//                        break;
//                    }
//                }
//            }
//        }

//        if (tp == 0) return Double.NaN;

        return tp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
