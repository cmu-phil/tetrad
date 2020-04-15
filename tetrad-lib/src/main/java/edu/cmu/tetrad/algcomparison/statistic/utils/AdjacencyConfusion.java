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
        adjTn = 0;

        for (Edge edge : truth.getEdges()) {
            if (this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjTp++;
            }

            if (this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjTn++;
            }

            if (!this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjFp++;
            }

            if (!this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjFn++;
            }
        }

        for (Edge edge : est.getEdges()) {
            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) continue;

            if (this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjTp++;
            }

            if (this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjTn++;
            }

            if (!this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjFp++;
            }

            if (!this.truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !this.est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                adjFn++;
            }
        }
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
