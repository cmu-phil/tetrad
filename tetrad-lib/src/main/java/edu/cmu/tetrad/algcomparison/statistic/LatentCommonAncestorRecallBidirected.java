package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.Serial;
import java.util.List;

import static edu.cmu.tetrad.algcomparison.statistic.LatentCommonAncestorTruePositiveBidirected.existsLatentCommonAncestor;

/**
 * The bidirected true positives.
 *
 * @author josephramseyHow
 * @version $Id: $Id
 */
public class LatentCommonAncestorRecallBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LatentCommonAncestorRecallBidirected.</p>
     */
    public LatentCommonAncestorRecallBidirected() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#X<~~L~~>Y,adj(X,Y),X<->Y";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "# of X<~~L~~>Y with latent Z for X*-*Y in estimated that are marked as bidirected";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fn = 0;

        List<Node> nodes = estGraph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (existsLatentCommonAncestor(trueGraph, Edges.nondirectedEdge(x, y))) {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
