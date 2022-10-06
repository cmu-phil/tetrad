package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

import static edu.cmu.tetrad.algcomparison.statistic.LatentCommonAncestorTruePositiveBidirected.existsLatentCommonAncestor;

/**
 * The bidirected true positives.
 *
 * @author jdramseyHow
 */
public class LatentCommonAncestorRecallBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "LCABR";
    }

    @Override
    public String getDescription() {
        return "Proportion of X<-...<-Z->...->Y with latent Z for X*-*Y in estimated graph that are marked as bidirected edge";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fn = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (existsLatentCommonAncestor(trueGraph, Edges.nondirectedEdge(x, y))) {
                    Edge edge2 = estGraph.getEdge(x, y);

//                    if (edge2 != null && Edges.isBidirectedEdge(edge2)) {
//                        tp++;
//                    } else {
//                        fn++;
//                    }

                    if (edge2 != null) {
                        if (Edges.isBidirectedEdge(edge2)) {
                            tp++;
                        } else {
                            fn++;
                        }
                    }
                }
            }
        }

        return tp / (double) (tp + fn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
