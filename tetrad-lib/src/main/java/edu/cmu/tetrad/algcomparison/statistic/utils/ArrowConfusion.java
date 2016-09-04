package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;

import java.util.HashSet;
import java.util.Set;

/**
 * A confusion matrix for arrows--i.e. TP, FP, TN, FN for counts of arrow endpoints.
 * A true positive arrow is counted for X*->Y in the estimated graph if X is not adjacent
 * to Y or X--Y or X<--Y.
 *
 * @author jdramsey
 */
public class ArrowConfusion {
    private Graph truth;
    private Graph est;
    private int arrowsTp;
    private int arrowsFp;
    private int arrowsFn;
    private int arrowsTn;

    public ArrowConfusion(Graph truth, Graph est) {
        this.truth = truth;
        this.est = est;
        arrowsTp = 0;
        arrowsFp = 0;
        arrowsFn = 0;

        Set<Edge> allOriented = new HashSet<>();
        allOriented.addAll(this.truth.getEdges());
        allOriented.addAll(this.est.getEdges());

        for (Edge edge : allOriented) {
            Edge edge1 = this.est.getEdge(edge.getNode1(), edge.getNode2());

            Endpoint e1Est = null;
            Endpoint e2Est = null;

            if (edge1 != null) {
                e1Est = edge.getProximalEndpoint(edge.getNode1());
                e2Est = edge.getProximalEndpoint(edge.getNode2());
            }

            Edge edge2 = this.truth.getEdge(edge.getNode1(), edge.getNode2());

            Endpoint e1True = null;
            Endpoint e2True = null;

            if (edge2 != null) {
                e1True = edge2.getProximalEndpoint(edge.getNode1());
                e2True = edge2.getProximalEndpoint(edge.getNode2());
            }

            edge = this.est.getEdge(edge.getNode1(), edge.getNode2());

            if (edge != null) {
                e1Est = edge.getProximalEndpoint(edge.getNode1());
                e2Est = edge.getProximalEndpoint(edge.getNode2());
            }

            if (e1Est == Endpoint.ARROW && e1True != Endpoint.ARROW) {
                arrowsFp++;
            }

            if (e2Est == Endpoint.ARROW && e2True != Endpoint.ARROW) {
                arrowsFp++;
            }

            if (e1True == Endpoint.ARROW && e1Est != Endpoint.ARROW) {
                arrowsFn++;
            }

            if (e2True == Endpoint.ARROW && e2Est != Endpoint.ARROW) {
                arrowsFn++;
            }

            if (e1True == Endpoint.ARROW && e1Est == Endpoint.ARROW) {
                arrowsTp++;
            }

            if (e2True == Endpoint.ARROW && e2Est == Endpoint.ARROW) {
                arrowsTp++;
            }

            if (e1True != Endpoint.ARROW && e1Est != Endpoint.ARROW) {
                arrowsTn++;
            }

            if (e2True != Endpoint.ARROW && e2Est != Endpoint.ARROW) {
                arrowsTn++;
            }
        }

//        int allEdges = this.truth.getNumNodes() * (this.truth.getNumNodes() - 1) / 2;
//        arrowsTn = allEdges - arrowsFn;
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

}
