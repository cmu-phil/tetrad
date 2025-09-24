///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.List;

/**
 * A confusion matrix for tails--i.e. TP, FP, TN, FN for counts of arrow endpoints. A true positive arrow is counted for
 * X*--Y in the estimated graph if X--*Y.
 *
 * @author josephramsey, rubens (November, 2016)
 * @version $Id: $Id
 */
public class TailConfusion {

    /**
     * The true positive count for tails.
     */
    private int tailsTp;

    /**
     * The false positive count for tails.
     */
    private int tailsFp;

    /**
     * The false negative count for tails.
     */
    private int tailsFn;

    /**
     * The true negative count for tails.
     */
    private int tailsTn;

    /**
     * The true positive count for 2-cycles.
     */
    private int TCtp;

    /**
     * The false negative count for 2-cycles.
     */
    private int TCfn;

    /**
     * The false positive count for 2-cycles.
     */
    private int TCfp;

    /**
     * <p>Constructor for TailConfusion.</p>
     *
     * @param truth a {@link edu.cmu.tetrad.graph.Graph} object
     * @param est   a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public TailConfusion(Graph truth, Graph est) {
        Graph est1 = est;
        this.tailsTp = 0;
        this.tailsFp = 0;
        this.tailsFn = 0;
        this.TCtp = 0; //for the two-cycle accuracy
        this.TCfn = 0;
        this.TCfp = 0;


        est1 = GraphUtils.replaceNodes(est, truth.getNodes());


        // Get edges from the true Graph to compute TruePositives, TrueNegatives and FalseNeagtives
        //    System.out.println(this.truth.getEdges());

        for (Edge edge : truth.getEdges()) {

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

            List<Edge> edges2 = truth.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge2;

            if (edges2.size() == 1) {
                edge2 = edges2.iterator().next();
            } else {
                edge2 = truth.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            //       System.out.println(edge2 + "(truth)");

            Endpoint e1True = null;
            Endpoint e2True = null;

            if (edge2 != null) {
                e1True = edge2.getProximalEndpoint(edge.getNode1());
                e2True = edge2.getProximalEndpoint(edge.getNode2());
            }


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

            List<Edge> edges2 = truth.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge2;

            if (edges2.size() == 1) {
                edge2 = edges2.iterator().next();
            } else {
                edge2 = truth.getDirectedEdge(edge.getNode1(), edge.getNode2());
            }

            //          System.out.println(edge2 + "(truth)");

            Endpoint e1True = null;
            Endpoint e2True = null;

            if (edge2 != null) {
                e1True = edge2.getProximalEndpoint(edge.getNode1());
                e2True = edge2.getProximalEndpoint(edge.getNode2());
            }


            if (e1Est == Endpoint.TAIL && e1True != Endpoint.TAIL) {
                this.tailsFp++;
            }

            if (e2Est == Endpoint.TAIL && e2True != Endpoint.TAIL) {
                this.tailsFp++;
            }


        }


        // test for 2-cycle

        for (Edge edge : truth.getEdges()) {


            List<Edge> TwoCycle1 = truth.getEdges(edge.getNode1(), edge.getNode2());
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

            List<Edge> TwoCycle1 = truth.getEdges(edge.getNode1(), edge.getNode2());
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
     * <p>getArrowsTp.</p>
     *
     * @return a int
     */
    public int getArrowsTp() {
        return this.tailsTp;
    }

    /**
     * <p>getArrowsFp.</p>
     *
     * @return a int
     */
    public int getArrowsFp() {
        return this.tailsFp;
    }

    /**
     * <p>getArrowsFn.</p>
     *
     * @return a int
     */
    public int getArrowsFn() {
        return this.tailsFn;
    }

    /**
     * <p>getArrowsTn.</p>
     *
     * @return a int
     */
    public int getArrowsTn() {
        return this.tailsTn;
    }

    /**
     * <p>getTwoCycleTp.</p>
     *
     * @return a int
     */
    public int getTwoCycleTp() {
        return this.TCtp;
    }

    /**
     * <p>getTwoCycleFp.</p>
     *
     * @return a int
     */
    public int getTwoCycleFp() {
        return this.TCfp;
    }

    /**
     * <p>getTwoCycleFn.</p>
     *
     * @return a int
     */
    public int getTwoCycleFn() {
        return this.TCfn;
    }


}

