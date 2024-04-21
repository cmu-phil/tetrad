package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.Serial;
import java.util.ArrayList;

/**
 * Represents the statistic "Exists Almost Cyclic Path in Estimated Graph". An almost cyclic path is a path from node x to
 * node y or from node y to node x in the estimated graph, where x and y are connected by a bidirected edge. PAGs and
 * MAGs should not contain almost cyclic paths.
 */
public class ExistsAlmostCyclicPathEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public ExistsAlmostCyclicPathEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "ACP";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Exists Almost Cyclic Path in Estimated Graph";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {

        // Make a list of the bidirected edges in the graph.
        java.util.List<Edge> bidirectedEdges = new ArrayList<>();

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                // Check if there is a path from x to y or y to x in the estimated graph.
                if (estGraph.paths().isAncestorOf(x, y) || estGraph.paths().isAncestorOf(y, x)) {
                    return 1.0;
                }
            }
        }

        return 0.0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
