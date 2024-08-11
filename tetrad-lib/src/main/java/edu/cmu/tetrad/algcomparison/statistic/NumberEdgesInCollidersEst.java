package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents the NumberEdgesEst statistic, which calculates the number of edges in colliders in the estimated graph.
 */
public class NumberEdgesInCollidersEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public NumberEdgesInCollidersEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#EdgesInCEst";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Edges in Colliders in the Estimated Graph";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        List<Node> nodes = estGraph.getNodes();
        Set<Edge> edges = new HashSet<>();

        for (int i = 0; i < nodes.size(); i++) {
            Node x = nodes.get(i);
            List<Node> adj = estGraph.getAdjacentNodes(x);

            for (int j = 0; j < adj.size(); j++) {
                for (int k = j + 1; k < adj.size(); k++) {
                    Node y = adj.get(j);
                    Node z = adj.get(k);

                    if (estGraph.isDefCollider(y, x, z)) {
                        edges.add(estGraph.getEdge(y, x));
                        edges.add(estGraph.getEdge(z, x));
                    }
                }
            }
        }

        return edges.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(value / 1000.);
    }
}
