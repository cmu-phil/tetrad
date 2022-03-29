package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.List;

/**
 * A confusion matrix for tails--i.e. TP, FP, TN, FN for counts of arrow endpoints.
 * A true positive arrow is counted for X*->Y in the estimated graph if X is not adjacent
 * to Y or X--Y or X<--Y.
 *
 * @author jdramsey, rubens (November, 2016)
 */
public class TailConfusion {

    private final Graph truth;
    private Graph est;
    private int tailsTp;
    private int tailsFp;
    private int tailsFn;
    private int tailsTn;
    private int TCtp;
    private int TCfn;
    private int TCfp;

    public TailConfusion(final Graph truth, final Graph est) {
        this.truth = truth;
        this.est = est;
        this.tailsTp = 0;
        this.tailsFp = 0;
        this.tailsFn = 0;
        this.TCtp = 0; //for the two-cycle accuracy
        this.TCfn = 0;
        this.TCfp = 0;


        this.est = GraphUtils.replaceNodes(est, truth.getNodes());


        // Get edges from the true Graph to compute TruePositives, TrueNegatives and FalseNeagtives
        //    System.out.println(this.truth.getEdges());

        for (final Edge edge : this.truth.getEdges()) {

            final List<Edge> edges1 = this.est.getEdges(edge.getNode1(), edge.getNode2());
            final Edge edge1;

            if (edges1.size() == 1) {
                edge1 = edges1.get(0);
            } else {
                edge1 = this.est.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            //      System.out.println(edge1 + "(est)");

            Endpoint e1Est = null;
            Endpoint e2Est = null;

            if (edge1 != null) {
                e1Est = edge1.getProximalEndpoint(edge.getNode1());
                e2Est = edge1.getProximalEndpoint(edge.getNode2());
            }
            //      System.out.println(e1Est);
            //      System.out.println(e2Est);

            final List<Edge> edges2 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
            final Edge edge2;

            if (edges2.size() == 1) {
                edge2 = edges2.get(0);
            } else {
                edge2 = this.truth.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            //       System.out.println(edge2 + "(truth)");

            Endpoint e1True = null;
            Endpoint e2True = null;

            if (edge2 != null) {
                e1True = edge2.getProximalEndpoint(edge.getNode1());
                e2True = edge2.getProximalEndpoint(edge.getNode2());
            }
            //       System.out.println(e1True);
            //       System.out.println(e2True);


            if (e1True == Endpoint.TAIL && e1Est != Endpoint.TAIL) {
                this.tailsFn++;
            }

            if (e2True == Endpoint.TAIL && e2Est != Endpoint.TAIL) {
                this.tailsFn++;
            }

            if (e1True == Endpoint.TAIL && e1Est == Endpoint.TAIL) {
                this.tailsTp++;
            }

            if (e2True == Endpoint.TAIL && e2Est == Endpoint.TAIL) {
                this.tailsTp++;
            }

            if (e1True != Endpoint.TAIL && e1Est != Endpoint.TAIL) {
                this.tailsTn++;
            }

            if (e2True != Endpoint.TAIL && e2Est != Endpoint.TAIL) {
                this.tailsTn++;
            }


        }
// Get edges from the estimated graph to compute only FalsePositives
        // System.out.println(this.est.getEdges());

        for (final Edge edge : this.est.getEdges()) {

            final List<Edge> edges1 = this.est.getEdges(edge.getNode1(), edge.getNode2());
            final Edge edge1;

            if (edges1.size() == 1) {
                edge1 = edges1.get(0);
            } else {
                edge1 = this.est.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }
            //      System.out.println(edge1 + "(est)");

            Endpoint e1Est = null;
            Endpoint e2Est = null;

            if (edge1 != null) {
                e1Est = edge1.getProximalEndpoint(edge.getNode1());
                e2Est = edge1.getProximalEndpoint(edge.getNode2());
            }
            //       System.out.println(e1Est);
            //       System.out.println(e2Est);

            final List<Edge> edges2 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
            final Edge edge2;

            if (edges2.size() == 1) {
                edge2 = edges2.get(0);
            } else {
                edge2 = this.truth.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            //          System.out.println(edge2 + "(truth)");

            Endpoint e1True = null;
            Endpoint e2True = null;

            if (edge2 != null) {
                e1True = edge2.getProximalEndpoint(edge.getNode1());
                e2True = edge2.getProximalEndpoint(edge.getNode2());
            }
            //          System.out.println(e1True);
            //          System.out.println(e2True);


            if (e1Est == Endpoint.TAIL && e1True != Endpoint.TAIL) {
                this.tailsFp++;
            }

            if (e2Est == Endpoint.TAIL && e2True != Endpoint.TAIL) {
                this.tailsFp++;
            }


        }


        // test for 2-cycle
        //Set<Edge> allOriented = new HashSet<>();
        //allOriented.addAll(this.truth.getEdges());
        //allOriented.addAll(this.est.getEdges());

        for (final Edge edge : this.truth.getEdges()) {


            final List<Edge> TwoCycle1 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
            final List<Edge> TwoCycle2 = this.est.getEdges(edge.getNode1(), edge.getNode2());

            if (TwoCycle1.size() == 2 && TwoCycle2.size() == 2) {
                //              System.out.println("2-cycle correctly inferred " + TwoCycle1);
                this.TCtp++;
            }

            if (TwoCycle1.size() == 2 && TwoCycle2.size() != 2) {
                //             System.out.println("2-cycle not inferred " + TwoCycle1);
                this.TCfn++;
            }
        }

        for (final Edge edge : this.est.getEdges()) {

            final List<Edge> TwoCycle1 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
            final List<Edge> TwoCycle2 = this.est.getEdges(edge.getNode1(), edge.getNode2());

            if (TwoCycle1.size() != 2 && TwoCycle2.size() == 2) {
                //              System.out.println("2-cycle falsely inferred" + TwoCycle2);
                this.TCfp++;
            }
        }

  /*      System.out.println(tailsTp);
        System.out.println(tailsTn);
        System.out.println(tailsFn);
        System.out.println(tailsFp);
*/
        //divide by 2, the 2cycle accuracy is duplicated due to how getEdges is used
        this.TCtp = this.TCtp / 2;
        this.TCfn = this.TCfn / 2;
        this.TCfp = this.TCfp / 2;
        //       System.out.println(TCtp);
        //       System.out.println(TCfn);
        //       System.out.println(TCfp);

    }


    public int getArrowsTp() {
        return this.tailsTp;
    }

    public int getArrowsFp() {
        return this.tailsFp;
    }

    public int getArrowsFn() {
        return this.tailsFn;
    }

    public int getArrowsTn() {
        return this.tailsTn;
    }

    public int getTwoCycleTp() {
        return this.TCtp;
    }

    public int getTwoCycleFp() {
        return this.TCfp;
    }

    public int getTwoCycleFn() {
        return this.TCfn;
    }


}
