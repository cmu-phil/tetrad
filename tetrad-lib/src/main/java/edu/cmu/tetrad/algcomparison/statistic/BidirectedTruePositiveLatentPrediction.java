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
public class BidirectedTruePositiveLatentPrediction implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BTPL";
    }

    @Override
    public String getDescription() {
        return "Bidirected True Positive Latent Prediction";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int count = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Set<Node> commonAncestors = new HashSet<>(trueGraph.getAncestors(Collections.singletonList(edge.getNode1())));
                commonAncestors.retainAll(trueGraph.getAncestors(Collections.singletonList(edge.getNode2())));
                commonAncestors.remove(edge.getNode1());
                commonAncestors.remove(edge.getNode2());

                for (Node c : commonAncestors) {
                    if (c.getNodeType() == NodeType.LATENT) {
                        count++;
                        break;
                    }
                }
            }
        }

        return count;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
