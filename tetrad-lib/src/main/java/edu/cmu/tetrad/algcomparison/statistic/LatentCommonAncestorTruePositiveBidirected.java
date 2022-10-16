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
            if (c.getNodeType() == NodeType.LATENT) {
                if (trueGraph.isAncestorOf(c, edge.getNode1())
                        && trueGraph.isAncestorOf(c, edge.getNode2())) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
