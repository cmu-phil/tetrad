package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import java.util.HashSet;
import java.util.Set;

/**
 * A confusion matrix for bidireced edges--i.e. TP, FP, TN, FN for counts of bidirected edges.
 *
 * @author josephramsey
 */
public class BidirectedConfusion {
    private final int tn;
    private int tp;
    private int fp;
    private int fn;

    /**
     * Constructs a new confusion matrix for bidirected edges.
     *
     * @param truth The true graph.
     * @param est   The estimated graph.
     */
    public BidirectedConfusion(Graph truth, Graph est) {
        this.tp = 0;
        this.fp = 0;
        this.fn = 0;

        Set<Edge> allBidirected = new HashSet<>();

        for (Edge edge : truth.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                allBidirected.add(edge);
            }
        }

        for (Edge edge : est.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                allBidirected.add(edge);
            }
        }

        for (Edge edge : allBidirected) {
            if (est.containsEdge(edge) && !truth.containsEdge(edge)) {
                this.fp++;
            }

            if (truth.containsEdge(edge) && !est.containsEdge(edge)) {
                this.fn++;
            }

            if (truth.containsEdge(edge) && est.containsEdge(edge)) {
                this.tp++;
            }
        }

        int all = truth.getNumNodes() * (truth.getNumNodes() - 1) / 2;

        this.tn = all - this.fn - this.fp - this.fn;
    }

    /**
     * Returns the number of true positives.
     *
     * @return The number of true positives.
     */
    public int getTp() {
        return this.tp;
    }

    /**
     * Returns the number of false positives.
     *
     * @return The number of false positives.
     */
    public int getFp() {
        return this.fp;
    }

    /**
     * Returns the number of false negatives.
     *
     * @return The number of false negatives.
     */
    public int getFn() {
        return this.fn;
    }

    /**
     * Returns the number of true negatives.
     *
     * @return The number of true negatives.
     */
    public int getTn() {
        return this.tn;
    }

}
