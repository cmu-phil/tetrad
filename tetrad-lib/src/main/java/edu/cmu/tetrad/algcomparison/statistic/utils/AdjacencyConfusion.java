package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import java.util.HashSet;
import java.util.Set;

/**
 * A confusion matrix for adjacencies--i.e. TP, FP, TN, FN for counts of adjacencies.
 *
 * @author jdramsey
 */
public class AdjacencyConfusion {
    private int adjTp;
    private int adjFp;
    private int adjFn;
    private final int adjTn;

    public AdjacencyConfusion(Graph truth, Graph est) {
        this.adjTp = 0;
        this.adjFp = 0;
        this.adjFn = 0;

        Set<Edge> allUnoriented = new HashSet<>();
        for (Edge edge : truth.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (Edge edge : est.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (Edge edge : allUnoriented) {
            if (est.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.adjFp++;
            }

            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.adjFn++;
            }

            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.adjTp++;
            }
        }

        int allEdges = truth.getNumNodes() * (truth.getNumNodes() - 1) / 2;

        this.adjTn = allEdges - this.adjFn;
    }

    public int getAdjTp() {
        return this.adjTp;
    }

    public int getAdjFp() {
        return this.adjFp;
    }

    public int getAdjFn() {
        return this.adjFn;
    }

    public int getAdjTn() {
        return this.adjTn;
    }

}
