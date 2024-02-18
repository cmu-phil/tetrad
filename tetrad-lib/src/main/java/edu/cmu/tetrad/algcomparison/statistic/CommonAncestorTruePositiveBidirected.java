package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.Serial;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CommonAncestorTruePositiveBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Returns true if there is a common ancestor of X and Y in the graph.
     *
     * @param trueGraph the true graph.
     * @param edge      the edge.
     * @return true if there is a common ancestor of X and Y in the graph.
     */
    public static boolean existsCommonAncestor(Graph trueGraph, Edge edge) {

        // edge X*-*Y where there is a common ancestor of X and Y in the graph.
        for (Node c : trueGraph.getNodes()) {
            if (trueGraph.paths().isAncestorOf(c, edge.getNode1())
                    && trueGraph.paths().isAncestorOf(c, edge.getNode2())) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "CATPB";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Common Ancestor True Positive Bidirected";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the value of the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (existsCommonAncestor(trueGraph, edge)) tp++;
            }
        }

        return tp;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the normed value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
