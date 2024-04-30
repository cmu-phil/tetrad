package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.io.Serial;
import java.util.List;

/**
 * Represents a statistic that calculates the number of correct visible ancestors in the true graph
 * that are also visible ancestors in the estimated graph.
 */
public class NumCorrectVisibleEdges implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NumCorrectVisibleEdges() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#CorrectVE";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Returns the number of visible edges X->Y in the estimated graph where X and Y have no latent confounder in the true graph.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        GraphUtils.addEdgeSpecializationMarkup(estGraph);
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getProperties().contains(Edge.Property.nl)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                List<List<Node>> treks = estGraph.paths().treks(x, y, -1);

                boolean found = false;

                // If there is a trek, x<~~z~~>y, where z is latent, then the edge is not semantically visible.
                for (List<Node> trek : treks) {
                    if (trek.size() > 2) {
                        Node source = getSource(estGraph, trek);

                        if (source.getNodeType() == NodeType.LATENT) {
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    tp++;
                }
            }
        }

        return  tp;
    }

    private Node getSource(Graph graph, List<Node> trek) {
        Node x = trek.get(0);
        Node y = trek.get(trek.size() - 1);

        Node source = y;

        // Find the first node where the direction is left to right.
        for (int i = 0; i < trek.size() - 1; i++) {
            Node n1 = trek.get(i);
            Node n2 = trek.get(i + 1);

            if (graph.getEdge(n1, n2).pointsTowards(n2)) {
                source = n1;
                break;
            }
        }

        return source;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
