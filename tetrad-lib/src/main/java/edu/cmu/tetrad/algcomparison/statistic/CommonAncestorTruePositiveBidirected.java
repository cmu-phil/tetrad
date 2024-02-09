package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class CommonAncestorTruePositiveBidirected implements Statistic {
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
     * Returns the name of the statistic.
     *
     * @return the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "CATPB";
    }

    /**
     * Returns the description of the statistic.
     *
     * @return the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Common Ancestor True Positive Bidirected";
    }

    /**
     * Returns the value of the statistic.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return the value of the statistic.
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
     * Returns the normed value of the statistic.
     * @param value The value of the statistic.
     * @return the normed value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
