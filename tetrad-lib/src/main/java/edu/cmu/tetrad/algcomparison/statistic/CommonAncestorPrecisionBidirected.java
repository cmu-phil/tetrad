package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class CommonAncestorPrecisionBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "CABP";
    }

    @Override
    public String getDescription() {
        return "Proportion of X<->Y in estimated graph where  X<-...<-Z->...->Y for X*-*Y in true DAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (existsCommonAncestor(trueGraph, edge)) {
                    tp++;
                } else {
                    fp++;
                }
            }
        }

        return tp / (double) (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }

    public static boolean existsCommonAncestor(Graph trueGraph, Edge edge) {

        // edge X*-*Y where there is a common ancestor of X and Y in the graph.

        for (Node c : trueGraph.getNodes()) {
//            if (c == edge.getNode1() && c == edge.getNode2()) continue;
            if (trueGraph.isAncestorOf(c, edge.getNode1())
                    && trueGraph.isAncestorOf(c, edge.getNode2())) {
                return true;
            }
        }

        return false;


//        Set<Node> commonAncestors = new HashSet<>(trueGraph.getAncestors(Collections.singletonList(edge.getNode1())));
//        commonAncestors.retainAll(trueGraph.getAncestors(Collections.singletonList(edge.getNode2())));
//        commonAncestors.remove(edge.getNode1());
//        commonAncestors.remove(edge.getNode2());
//        return !commonAncestors.isEmpty();
    }
}
