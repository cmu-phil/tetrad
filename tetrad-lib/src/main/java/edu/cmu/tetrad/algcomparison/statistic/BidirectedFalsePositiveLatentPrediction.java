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
public class BidirectedFalsePositiveLatentPrediction implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BFPLP";
    }

    @Override
    public String getDescription() {
        return "Bidirected False Positive Latent Predictions";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int count = 0;
        int all = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                all++;

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

        return all - count;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
