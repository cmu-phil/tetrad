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
    private Graph truth;
    private Graph est;
    private int adjTp;
    private int adjFp;
    private int adjFn;
    private int adjTn;

    public AdjacencyConfusion(Graph truth, Graph est) {
        this.truth = truth;
        this.est = est;
        adjTp = 0;
        adjFp = 0;
        adjFn = 0;

        Set<Edge> allUnoriented = new HashSet<>();
        for (Edge edge : this.truth.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (Edge edge : this.est.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (Edge edge : allUnoriented) {
            if (this.est.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjFp++;
            }

            if (this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjFn++;
            }

            if (this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjTp++;
            }
        }

        int allEdges = this.truth.getNumNodes() * (this.truth.getNumNodes() - 1) / 2;

        adjTn = allEdges - adjFn;
    }

    public int getAdjTp() {
        return adjTp;
    }

    public int getAdjFp() {
        return adjFp;
    }

    public int getAdjFn() {
        return adjFn;
    }

    public int getAdjTn() {
        return adjTn;
    }

}
