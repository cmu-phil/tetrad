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
        return "#X<-M->Y,adj(X,Y)=>X<->Y";
    }

    @Override
    public String getDescription() {
        return "# of X<-...<-Z->...->Y with latent Z for X*-*Y in estimated that are marked as bidirected";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fn = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (existsLatentCommonAncestor(trueGraph, Edges.nondirectedEdge(x, y))
                        && !existsLatentCommonAncestor(trueGraph, Edges.nondirectedEdge(x, y))) {
                    Edge edge2 = estGraph.getEdge(x, y);

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

        return tp;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
