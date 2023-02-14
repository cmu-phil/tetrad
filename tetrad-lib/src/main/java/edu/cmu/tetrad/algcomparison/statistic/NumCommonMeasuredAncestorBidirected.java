package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.Collections;
import java.util.List;

import static edu.cmu.tetrad.algcomparison.statistic.LatentCommonAncestorTruePositiveBidirected.existsLatentCommonAncestor;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class NumCommonMeasuredAncestorBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#X<->Y,X<~~MnotL~~>Y";
    }

    @Override
    public String getDescription() {
        return "# X<->Y where X<~~M~~>Y in true but not X<~~L~~>Y";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (existsCommonAncestor(trueGraph, edge) && !existsLatentCommonAncestor(trueGraph, edge)) {
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

    public static boolean existsCommonAncestor(Graph trueGraph, Edge edge) {
        List<Node> nodes = trueGraph.paths().getAncestors(Collections.singletonList(edge.getNode1()));
        nodes.retainAll(trueGraph.paths().getAncestors(Collections.singletonList(edge.getNode2())));
        return !nodes.isEmpty();
    }
}
