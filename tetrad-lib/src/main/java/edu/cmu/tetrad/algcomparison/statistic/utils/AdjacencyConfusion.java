package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import java.util.HashSet;
import java.util.Set;

/**
 * A confusion matrix for adjacencies--i.e. TP, FP, TN, FN for counts of adjacencies.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AdjacencyConfusion {

    /**
     * The true negative count.
     */
    private final int tn;

    /**
     * The true positive count.
     */
    private int tp;

    /**
     * The false positive count.
     */
    private int fp;


    private int fn;

    /**
     * Constructs a new AdjacencyConfusion object from the given graphs.
     *
     * @param truth The true graph.
     * @param est   The estimated graph.
     */
    public AdjacencyConfusion(Graph truth, Graph est) {
        this.tp = 0;
        this.fp = 0;
        this.fn = 0;

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
                this.fp++;
            }

            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                !est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.fn++;
            }

            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tp++;
            }
        }

        int allEdges = truth.getNumNodes() * (truth.getNumNodes() - 1) / 2;

        this.tn = allEdges - this.fn - this.fp - this.fn;
    }

    /**
     * Returns the true positive count.
     *
     * @return the true positive count.
     */
    public int getTp() {
        return this.tp;
    }

    /**
     * Returns the false positive count.
     *
     * @return the false positive count.
     */
    public int getFp() {
        return this.fp;
    }

    /**
     * Returns the false negative count.
     *
     * @return the false negative count.
     */
    public int getFn() {
        return this.fn;
    }

    /**
     * Returns the true negative count.
     *
     * @return the true negative count.
     */
    public int getTn() {
        return this.tn;
    }

}
