package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.*;

import java.util.List;

/**
 * A confusion matrix for arrows--i.e. TP, FP, TN, FN for counts of arrow endpoints.
 * A true positive arrow is counted for X*->Y in the estimated graph if X is not adjacent
 * to Y or X--Y or X<--Y.
 *
 * @author jdramsey, rubens (November, 2016)
 */
public class ArrowConfusion {

    // For arrowhead FP's, don't count an error unless the variables are adj in the true graph.
    private boolean truthAdj = false;

    private Graph truth;
    private Graph est;
    private int arrowsTp;
    private int arrowsTpc;
    private int arrowsFp;
    private int arrowsFpc;
    private int arrowsTn;
    private int arrowsTnc;
    private int arrowsFn;
    private int arrowsFnc;
    private int TCtp;
    private int TCfn;
    private int TCfp;

    public ArrowConfusion(Graph truth, Graph est) {
        this(truth, est, false);
    }

    public ArrowConfusion(Graph truth, Graph est, boolean truthAdj) {
        this.truth = truth;
        this.est = est;
        arrowsTp = 0;
        arrowsTpc = 0;
        arrowsFp = 0;
        arrowsFpc = 0;
        arrowsTn = 0;
        arrowsTnc = 0;
        arrowsFn = 0;
        arrowsFnc = 0;
        TCtp = 0; //for the two-cycle accuracy
        TCfn = 0;
        TCfp = 0;
        this.truthAdj = truthAdj;

        this.est = GraphUtils.replaceNodes(est, truth.getNodes());
        this.truth = GraphUtils.replaceNodes(truth, est.getNodes());


        // Get edges from the true Graph to compute TruePositives, TrueNegatives and FalseNeagtives
        //    System.out.println(this.truth.getEdges());

        for (Edge edge : this.truth.getEdges()) {
            List<Edge> edges1 = this.est.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge1;

            if (edges1.size() == 1) {
                edge1 = edges1.get(0);
            } else {
                edge1 = this.est.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            Endpoint e1Est = null;
            Endpoint e2Est = null;

            if (edge1 != null) {
                e1Est = edge1.getProximalEndpoint(edge.getNode1());
                e2Est = edge1.getProximalEndpoint(edge.getNode2());
            }

            List<Edge> edges2 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge2;

            if (edges2.size() == 1) {
                edge2 = edges2.get(0);
            } else {
                edge2 = this.truth.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            Endpoint e1True = null;
            Endpoint e2True = null;

            if (edge2 != null) {
                e1True = edge2.getProximalEndpoint(edge.getNode1());
                e2True = edge2.getProximalEndpoint(edge.getNode2());
            }

            count(truth, est, edge, edge1, e1Est, e2Est, edge2, e1True, e2True);
        }

        // Get edges from the estimated graph to compute only FalsePositives for adjacencies not in truth.
        // System.out.println(this.est.getEdges());

        for (Edge edge : this.est.getEdges()) {
            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) continue;

            List<Edge> edges1 = this.est.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge1;

            if (edges1.size() == 1) {
                edge1 = edges1.get(0);
            } else {
                edge1 = this.est.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            Endpoint e1Est = null;
            Endpoint e2Est = null;

            if (edge1 != null) {
                e1Est = edge1.getProximalEndpoint(edge.getNode1());
                e2Est = edge1.getProximalEndpoint(edge.getNode2());
            }

            List<Edge> edges2 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge2;

            if (edges2.size() == 1) {
                edge2 = edges2.get(0);
            } else {
                edge2 = this.truth.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            Endpoint e1True = null;
            Endpoint e2True = null;

            if (edge2 != null) {
                e1True = edge2.getProximalEndpoint(edge.getNode1());
                e2True = edge2.getProximalEndpoint(edge.getNode2());
            }

            if (isTruthAdj()) {
                if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    count(truth, est, edge, edge1, e1Est, e2Est, edge2, e1True, e2True);
                }
            } else {
                count(truth, est, edge, edge1, e1Est, e2Est, edge2, e1True, e2True);
            }
        }

        // test for 2-cycle
        //Set<Edge> allOriented = new HashSet<>();
        //allOriented.addAll(this.truth.getEdges());
        //allOriented.addAll(this.est.getEdges());

        for (Edge edge : this.truth.getEdges()) {
            List<Edge> TwoCycle1 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
            List<Edge> TwoCycle2 = this.est.getEdges(edge.getNode1(), edge.getNode2());

            if (TwoCycle1.size() == 2 && TwoCycle2.size() == 2) {
                //              System.out.println("2-cycle correctly inferred " + TwoCycle1);
                TCtp++;
            }

            if (TwoCycle1.size() == 2 && TwoCycle2.size() != 2) {
                //             System.out.println("2-cycle not inferred " + TwoCycle1);
                TCfn++;
            }
        }

        for (Edge edge : this.est.getEdges()) {
            List<Edge> TwoCycle1 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
            List<Edge> TwoCycle2 = this.est.getEdges(edge.getNode1(), edge.getNode2());

            if (TwoCycle1.size() != 2 && TwoCycle2.size() == 2) {
                //              System.out.println("2-cycle falsely inferred" + TwoCycle2);
                TCfp++;
            }
        }

        //divide by 2, the 2cycle accuracy is duplicated due to how getEdges is used
        TCtp = TCtp / 2;
        TCfn = TCfn / 2;
        TCfp = TCfp / 2;

    }

    private void count(Graph truth, Graph est, Edge edge, Edge edge1, Endpoint e1Est, Endpoint e2Est, Edge edge2, Endpoint e1True, Endpoint e2True) {
        if (e1True == Endpoint.ARROW && e1Est == Endpoint.ARROW) {
            arrowsTp++;
        }

        if (e2True == Endpoint.ARROW && e2Est == Endpoint.ARROW) {
            arrowsTp++;
        }

        if (e1True == Endpoint.ARROW && e1Est != Endpoint.ARROW) {
            arrowsTn++;
        }

        if (e2True == Endpoint.ARROW && e2Est != Endpoint.ARROW) {
            arrowsTn++;
        }

        if (e1True != Endpoint.ARROW && e1Est == Endpoint.ARROW) {
            arrowsFp++;
        }

        if (e2True != Endpoint.ARROW && e2Est == Endpoint.ARROW) {
            arrowsFp++;
        }

        if (e1True != Endpoint.ARROW && e1Est != Endpoint.ARROW) {
            arrowsFn++;
        }

        if (e2True != Endpoint.ARROW && e2Est != Endpoint.ARROW) {
            arrowsFn++;
        }

        boolean adj = truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) && est.isAdjacentTo(edge.getNode1(), edge.getNode2());

        if (e1True == Endpoint.ARROW && e1Est == Endpoint.ARROW && adj) {
            arrowsTpc++;
        }

        if (e2True == Endpoint.ARROW && e2Est == Endpoint.ARROW && adj) {
            arrowsTpc++;
        }

        if (e1True == Endpoint.ARROW && e1Est != Endpoint.ARROW && adj) {
            arrowsTnc++;
        }

        if (e2True == Endpoint.ARROW && e2Est != Endpoint.ARROW && adj) {
            arrowsTnc++;
        }

        if (e1True != Endpoint.ARROW && e1Est == Endpoint.ARROW && adj) {
            arrowsFpc++;
        }

        if (e2True != Endpoint.ARROW && e2Est == Endpoint.ARROW && adj) {
            arrowsFpc++;
        }

        if (e1True != Endpoint.ARROW && e1Est != Endpoint.ARROW && adj) {
            arrowsFnc++;
        }

        if (e2True != Endpoint.ARROW && e2Est != Endpoint.ARROW && adj) {
            arrowsFnc++;
        }
    }


    public int getArrowsTp() {
        return arrowsTp;
    }

    public int getArrowsFp() {
        return arrowsFp;
    }

    public int getArrowsFn() {
        return arrowsFn;
    }

    public int getArrowsTn() {
        return arrowsTn;
    }

    public int getTwoCycleTp() {
        return TCtp;
    }

    public int getTwoCycleFp() {
        return TCfp;
    }

    public int getTwoCycleFn() {
        return TCfn;
    }

    /**
     * Two positives for common edges.
     */
    public int getArrowsTpc() {
        return arrowsTpc;
    }

    /**
     * False positives for common edges.
     */
    public int getArrowsFpc() {
        return arrowsFpc;
    }

    /**
     * False negatives for common edges.
     */
    public int getArrowsFnc() {
        return arrowsFnc;
    }

    /**
     * True Negatives for common edges.
     */
    public int getArrowsTnc() {
        return arrowsTnc;
    }

    public boolean isTruthAdj() {
        return truthAdj;
    }
}
