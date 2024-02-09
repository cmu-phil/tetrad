package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.List;

/**
 * A confusion matrix for arrows--i.e. TP, FP, TN, FN for counts of arrow endpoints. A true positive arrow is counted
 * for X*-&gt;Y in the estimated graph if X is not adjacent to Y or X--Y or X&lt;--Y.
 *
 * @author josephramsey, rubens (November, 2016)
 * @version $Id: $Id
 */
public class ArrowConfusion {

    // For arrowhead FP's, don't count an error unless the variables are adj in the true graph.
    private final boolean truthAdj;

    private int tp;
    private int tpc;
    private int fp;
    private int fpc;
    private int fn;
    private int fnc;
    private int tn;
    private int tnc;
    private int TCtp;
    private int TCfn;
    private int TCfp;

    /**
     * Constructs a new ArrowConfusion object.
     *
     * @param truth the true graph
     * @param est the estimated graph
     */
    public ArrowConfusion(Graph truth, Graph est) {
        this(truth, est, false);
    }

    /**
     * Constructs a new ArrowConfusion object.
     *
     * @param truth the true graph
     * @param est the estimated graph
     * @param truthAdj if true, use the true graph to determine adjacency for arrowhead FP's
     */
    public ArrowConfusion(Graph truth, Graph est, boolean truthAdj) {
        Graph truth1 = truth;
        Graph est1 = est;
        this.tp = 0;
        this.tpc = 0;
        this.fp = 0;
        this.fpc = 0;
        this.fn = 0;
        this.fnc = 0;
        this.TCtp = 0; //for the two-cycle accuracy
        this.TCfn = 0;
        this.TCfp = 0;
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


            if (e1True == Endpoint.ARROW && e1Est != Endpoint.ARROW) {
                this.fn++;
            }

            if (e2True == Endpoint.ARROW && e2Est != Endpoint.ARROW) {
                this.fn++;
            }

            if (e1True == Endpoint.ARROW && e1Est != Endpoint.ARROW && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.fnc = getFnc() + 1;
            }

            if (e2True == Endpoint.ARROW && e2Est != Endpoint.ARROW && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.fnc = getFnc() + 1;
            }


            if (e1True == Endpoint.ARROW && e1Est == Endpoint.ARROW) {
                this.tp++;
            }

            if (e2True == Endpoint.ARROW && e2Est == Endpoint.ARROW) {
                this.tp++;
            }

            if (e1True == Endpoint.ARROW && e1Est == Endpoint.ARROW && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tpc = getTpc() + 1;
            }

            if (e2True == Endpoint.ARROW && e2Est == Endpoint.ARROW && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tpc = getTpc() + 1;
            }

            if (e1True != Endpoint.ARROW && e1Est != Endpoint.ARROW) {
                this.tn++;
            }

            if (e2True != Endpoint.ARROW && e2Est != Endpoint.ARROW) {
                this.tn++;
            }

            if (e1True != Endpoint.ARROW && e1Est != Endpoint.ARROW && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tnc = getTnc() + 1;
            }

            if (e2True != Endpoint.ARROW && e2Est != Endpoint.ARROW && truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
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
                    if (e1Est == Endpoint.ARROW && e1True != Endpoint.ARROW) {
                        this.fp++;
                    }

                    if (e2Est == Endpoint.ARROW && e2True != Endpoint.ARROW) {
                        this.fp++;
                    }
                }
            } else {
                if (e1Est == Endpoint.ARROW && e1True != Endpoint.ARROW) {
                    this.fp++;
                }

                if (e2Est == Endpoint.ARROW && e2True != Endpoint.ARROW) {
                    this.fp++;
                }
            }

            if (e1Est == Endpoint.ARROW && e1True != Endpoint.ARROW && edge1 != null && edge2 != null) {
                this.fpc = getFpc() + 1;
            }

            if (e2Est == Endpoint.ARROW && e2True != Endpoint.ARROW && edge1 != null && edge2 != null) {
                this.fpc = getFpc() + 1;
            }

        }


        // test for 2-cycle

        for (Edge edge : truth1.getEdges()) {


            List<Edge> TwoCycle1 = truth1.getEdges(edge.getNode1(), edge.getNode2());
            List<Edge> TwoCycle2 = est1.getEdges(edge.getNode1(), edge.getNode2());

            if (TwoCycle1.size() == 2 && TwoCycle2.size() == 2) {
                //              System.out.println("2-cycle correctly inferred " + TwoCycle1);
                this.TCtp++;
            }

            if (TwoCycle1.size() == 2 && TwoCycle2.size() != 2) {
                //             System.out.println("2-cycle not inferred " + TwoCycle1);
                this.TCfn++;
            }
        }

        for (Edge edge : est1.getEdges()) {

            List<Edge> TwoCycle1 = truth1.getEdges(edge.getNode1(), edge.getNode2());
            List<Edge> TwoCycle2 = est1.getEdges(edge.getNode1(), edge.getNode2());

            if (TwoCycle1.size() != 2 && TwoCycle2.size() == 2) {
                //              System.out.println("2-cycle falsely inferred" + TwoCycle2);
                this.TCfp++;
            }
        }

        //divide by 2, the 2cycle accuracy is duplicated due to how getEdges is used
        this.TCtp = this.TCtp / 2;
        this.TCfn = this.TCfn / 2;
        this.TCfp = this.TCfp / 2;

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
     * Two positives for two-cycles.
     *
     * @return the number of true positives for two-cycles.
     */
    public int getTwoCycleTp() {
        return this.TCtp;
    }

    /**
     * False positives for two-cycles.
     *
     * @return the number of false positives for two-cycles.
     */
    public int getTwoCycleFp() {
        return this.TCfp;
    }

    /**
     * False negatives for two-cycles.
     *
     * @return the number of false negatives for two-cycles.
     */
    public int getTwoCycleFn() {
        return this.TCfn;
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
     * Returns true if the truth graph is used to determine adjacency for arrowhead FP's.
     *
     * @return true if the truth graph is used to determine adjacency for arrowhead FP's
     */
    public boolean isTruthAdj() {
        return this.truthAdj;
    }
}
