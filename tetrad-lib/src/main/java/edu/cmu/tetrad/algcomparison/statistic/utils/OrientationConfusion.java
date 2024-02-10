package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * A confusion matrix for orientations:
 *
 * @author bryanandrews, josephramsey
 * @version $Id: $Id
 */
public class OrientationConfusion {
    private int tp;
    private int fp;
    private int fn;
    private int tn;

    /**
     * <p>Constructor for OrientationConfusion.</p>
     *
     * @param truth a {@link edu.cmu.tetrad.graph.Graph} object
     * @param est   a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public OrientationConfusion(Graph truth, Graph est) {
        this.tp = 0;
        this.fp = 0;
        this.fn = 0;
        this.tn = 0;

        for (Edge edge : truth.getEdges()) {
            if (!edge.isDirected()) continue;
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            if (!est.isAdjacentTo(a, b)) {
                this.fn++;
                continue;
            }

            Edge other = est.getEdge(a, b);
            boolean m1 = edge.getEndpoint1() == other.getProximalEndpoint(a);
            boolean m2 = edge.getEndpoint2() == other.getProximalEndpoint(b);

            if (m1 && m2) {
                this.tp++;
                this.tn++;
                continue;
            }

            if (!m1 && !m2) {
                this.fp++;
                this.fn++;
                continue;
            }

            if (other.getEndpoint1() != Endpoint.TAIL) continue;
            if (other.getEndpoint2() != Endpoint.TAIL) continue;
            this.fn++;
        }

        for (Edge edge : est.getEdges()) {
            if (!edge.isDirected()) continue;
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            if (!truth.isAdjacentTo(a, b)) this.fp++;
        }
    }

    /**
     * <p>Getter for the field <code>tp</code>.</p>
     *
     * @return a int
     */
    public int getTp() {
        return this.tp;
    }

    /**
     * <p>Getter for the field <code>fp</code>.</p>
     *
     * @return a int
     */
    public int getFp() {
        return this.fp;
    }

    /**
     * <p>Getter for the field <code>fn</code>.</p>
     *
     * @return a int
     */
    public int getFn() {
        return this.fn;
    }

    /**
     * <p>Getter for the field <code>tn</code>.</p>
     *
     * @return a int
     */
    public int getTn() {
        return this.tn;
    }

}
