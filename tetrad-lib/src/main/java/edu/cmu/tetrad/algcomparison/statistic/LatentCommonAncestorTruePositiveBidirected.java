package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LatentCommonAncestorTruePositiveBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LatentCommonAncestorTruePositiveBidirected.</p>
     */
    public LatentCommonAncestorTruePositiveBidirected() {

    }

    /**
     * <p>existsLatentCommonAncestor.</p>
     *
     * @param trueGraph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param edge      a {@link edu.cmu.tetrad.graph.Edge} object
     * @return a boolean
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#X<->Y,X<~~L~~>Y";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Latent Common Ancestor True Positive Bidirected";
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
