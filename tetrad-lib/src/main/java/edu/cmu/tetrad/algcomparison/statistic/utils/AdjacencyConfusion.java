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
    private final Graph truth;
    private final Graph est;
    private int adjTp;
    private int adjFp;
    private int adjFn;
    private final int adjTn;

    public AdjacencyConfusion(final Graph truth, final Graph est) {
        this.truth = truth;
        this.est = est;
        this.adjTp = 0;
        this.adjFp = 0;
        this.adjFn = 0;

        final Set<Edge> allUnoriented = new HashSet<>();
        for (final Edge edge : this.truth.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (final Edge edge : this.est.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (final Edge edge : allUnoriented) {
            if (this.est.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.adjFp++;
            }

            if (this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.adjFn++;
            }

            if (this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.adjTp++;
            }
        }

        final int allEdges = this.truth.getNumNodes() * (this.truth.getNumNodes() - 1) / 2;

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
