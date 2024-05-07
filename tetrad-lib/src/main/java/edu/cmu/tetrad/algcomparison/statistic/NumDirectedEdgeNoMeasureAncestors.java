package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.io.Serial;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumDirectedEdgeNoMeasureAncestors implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NumDirectedEdgeNoMeasureAncestors() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#X->Y-Anc-Direct";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number X-->Y for which X~~>Y in true with no measures on path";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isDirectedEdge(edge)) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (existsDirectedPathFromTo(trueGraph, x, y)) {
                    tp++;
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

    /**
     * <p>existsDirectedPathFromTo.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean existsDirectedPathFromTo(Graph graph, Node node1, Node node2) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();

        Q.add(node1);
        V.add(node1);

//        for (Node c : getChildren(node1)) {
//            if (c == node2) return true;
//
//            Q.add(c);
//            V.add(c);
//        }

        while (!Q.isEmpty()) {
            Node t = Q.remove();

            for (Node c : graph.getChildren(t)) {
                if (c == node2) return true;
                if (c.getNodeType() == NodeType.MEASURED) continue;

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }
        }

        return false;
    }

}
