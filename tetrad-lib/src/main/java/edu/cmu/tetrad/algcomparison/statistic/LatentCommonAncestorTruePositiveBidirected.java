package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.Collections;
import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class LatentCommonAncestorTruePositiveBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "#X<->Y,X<~~L~~>Y";
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
        List<Node> nodes = trueGraph.paths().getAncestors(Collections.singletonList(edge.getNode1()));
        nodes.retainAll(trueGraph.paths().getAncestors(Collections.singletonList(edge.getNode2())));

        for (Node c : nodes) {
            if (c.getNodeType() == NodeType.LATENT) {
                return true;
            }
        }

        return false;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
