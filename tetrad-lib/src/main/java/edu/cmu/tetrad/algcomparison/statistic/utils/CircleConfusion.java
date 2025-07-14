package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.List;

/**
 * A confusion matrix for circles--i.e. TP, FP, TN, FN for counts of circle endpoints. A true positive circle is counted
 * for X*-o;Y in the estimated graph if X is not adjacent to Y or X--Y or Xo;--Y. // TODO VBC: is this correct?
 *
 * @author Verity Bing Chu (July, 2025)
 * @version $Id: $Id
 */
public class CircleConfusion {

    /**
     * For circle FP's, don't count an error unless the variables are adj in the true graph.
     */
    private final boolean truthAdj;

    /**
     * The true positive count.
     */
    private int tp;

    /**
     * The true positive count for common edges.
     */
    private int tpc;

    /**
     * The false positive count.
     */
    private int fp;

    /**
     * The false positive count for common edges.
     */
    private int fpc;

    /**
     * The false negative count.
     */
    private int fn;

    /**
     * The false negative count for common edges.
     */
    private int fnc;

    /**
     * The true negative count.
     */
    private int tn;

    /**
     * The true negative count for common edges.
     */
    private int tnc;

    /**
     * Constructs a new CircleConfusion object.
     *
     * @param truth the true graph
     * @param est   the estimated graph
     */
    public CircleConfusion(Graph truth, Graph est) {
        this(truth, est, false);
    }

    /**
     * Constructs a new CircleConfusion object.
     *
     * @param truth    the true graph
     * @param est      the estimated graph
     * @param truthAdj if true, use the true graph to determine adjacency for circle FP's
     */
    public CircleConfusion(Graph truth, Graph est, boolean truthAdj) {
        Graph truth1 = truth;
        Graph est1 = est;
        this.tp = 0;
        this.tpc = 0;
        this.fp = 0;
        this.fpc = 0;
        this.fn = 0;
        this.fnc = 0;
        this.truthAdj = truthAdj;


        est1 = GraphUtils.replaceNodes(est, truth.getNodes());
        truth1 = GraphUtils.replaceNodes(truth, est.getNodes());


        // Get edges from the true Graph to compute TruePositives, TrueNegatives and FalseNeagtives
        //    System.out.println(this.truth.getEdges());

        for (Edge edge : truth1.getEdges()) {

            List<Edge> edges1 = est1.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge1;

            if (edges1.size() == 1) {
                edge1 = edges1.iterator().next();
            } else {
                edge1 = est1.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            //      System.out.println(edge1 + "(est)");

            Endpoint e1Est = null;
            Endpoint e2Est = null;

            if (edge1 != null) {
                e1Est = edge1.getProximalEndpoint(edge.getNode1());
                e2Est = edge1.getProximalEndpoint(edge.getNode2());
            }

            List<Edge> edges2 = truth1.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge2;

            if (edges2.size() == 1) {
                edge2 = edges2.iterator().next();
//                if (Edges.isUndirectedEdge(edge2)) continue;
            } else {
                edge2 = truth1.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            //       System.out.println(edge2 + "(truth)");

            Endpoint e1True = null;
            Endpoint e2True = null;

            if (edge2 != null) {
                e1True = edge2.getProximalEndpoint(edge.getNode1());
                e2True = edge2.getProximalEndpoint(edge.getNode2());
            }


            if (e1True == Endpoint.CIRCLE && e1Est != Endpoint.CIRCLE) {
                this.fn++;
            }

            if (e2True == Endpoint.CIRCLE && e2Est != Endpoint.CIRCLE) {
                this.fn++;
            }

            if (e1True == Endpoint.CIRCLE && e1Est != Endpoint.CIRCLE && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.fnc = getFnc() + 1;
            }

            if (e2True == Endpoint.CIRCLE && e2Est != Endpoint.CIRCLE && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.fnc = getFnc() + 1;
            }


            if (e1True == Endpoint.CIRCLE && e1Est == Endpoint.CIRCLE) {
                this.tp++;
            }

            if (e2True == Endpoint.CIRCLE && e2Est == Endpoint.CIRCLE) {
                this.tp++;
            }

            if (e1True == Endpoint.CIRCLE && e1Est == Endpoint.CIRCLE && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tpc = getTpc() + 1;
            }

            if (e2True == Endpoint.CIRCLE && e2Est == Endpoint.CIRCLE && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tpc = getTpc() + 1;
            }

            if (e1True != Endpoint.CIRCLE && e1Est != Endpoint.CIRCLE) {
                this.tn++;
            }

            if (e2True != Endpoint.CIRCLE && e2Est != Endpoint.CIRCLE) {
                this.tn++;
            }

            if (e1True != Endpoint.CIRCLE && e1Est != Endpoint.CIRCLE && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tnc = getTnc() + 1;
            }

            if (e2True != Endpoint.CIRCLE && e2Est != Endpoint.CIRCLE && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tnc = getTnc() + 1;
            }
        }
// Get edges from the estimated graph to compute only FalsePositives
        // System.out.println(this.est.getEdges());

        for (Edge edge : est1.getEdges()) {

            List<Edge> edges1 = est1.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge1;

            if (edges1.size() == 1) {
                edge1 = edges1.iterator().next();
            } else {
                edge1 = est1.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }
            //      System.out.println(edge1 + "(est)");

            Endpoint e1Est = null;
            Endpoint e2Est = null;

            if (edge1 != null) {
                e1Est = edge1.getProximalEndpoint(edge.getNode1());
                e2Est = edge1.getProximalEndpoint(edge.getNode2());
            }


            List<Edge> edges2 = truth1.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge2;

            if (edges2.size() == 1) {
                edge2 = edges2.iterator().next();
//                if (Edges.isUndirectedEdge(edge2)) continue;
            } else {
                edge2 = truth1.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            //          System.out.println(edge2 + "(truth)");

            Endpoint e1True = null;
            Endpoint e2True = null;

            if (edge2 != null) {
                e1True = edge2.getProximalEndpoint(edge.getNode1());
                e2True = edge2.getProximalEndpoint(edge.getNode2());
            }

            if (isTruthAdj()) {
                if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    if (e1Est == Endpoint.CIRCLE && e1True != Endpoint.CIRCLE) {
                        this.fp++;
                    }

                    if (e2Est == Endpoint.CIRCLE && e2True != Endpoint.CIRCLE) {
                        this.fp++;
                    }
                }
            } else {
                if (e1Est == Endpoint.CIRCLE && e1True != Endpoint.CIRCLE) {
                    this.fp++;
                }

                if (e2Est == Endpoint.CIRCLE && e2True != Endpoint.CIRCLE) {
                    this.fp++;
                }
            }

            if (e1Est == Endpoint.CIRCLE && e1True != Endpoint.CIRCLE && edge1 != null && edge2 != null) {
                this.fpc = getFpc() + 1;
            }

            if (e2Est == Endpoint.CIRCLE && e2True != Endpoint.CIRCLE && edge1 != null && edge2 != null) {
                this.fpc = getFpc() + 1;
            }

        }
    }

    /**
     * True positives.
     *
     * @return the number of true positives
     */
    public int getTp() {
        return this.tp;
    }

    /**
     * False positives.
     *
     * @return the number of false positives
     */
    public int getFp() {
        return this.fp;
    }

    /**
     * False negatives.
     *
     * @return the number of false negatives
     */
    public int getFn() {
        return this.fn;
    }

    /**
     * True negatives.
     *
     * @return the number of true negatives
     */
    public int getTn() {
        return this.tn;
    }

    /**
     * True positives for common edges.
     *
     * @return the number of true positives for common edges
     */
    public int getTpc() {
        return this.tpc;
    }

    /**
     * False positives for common edges.
     *
     * @return the number of false positives for common edges
     */
    public int getFpc() {
        return this.fpc;
    }

    /**
     * False negatives for common edges.
     *
     * @return the number of false negatives for common edges
     */
    public int getFnc() {
        return this.fnc;
    }

    /**
     * True Negatives for common edges.
     *
     * @return the number of true negatives for common edges
     */
    public int getTnc() {
        return this.tnc;
    }

    /**
     * Returns true if the truth graph is used to determine adjacency for circle FP's.
     *
     * @return true if the truth graph is used to determine adjacency for circle FP's
     */
    public boolean isTruthAdj() {
        return this.truthAdj;
    }
}
