package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A confusion matrix for arrows--i.e. TP, FP, TN, FN for counts of arrow endpoints.
 * A true positive arrow is counted for X*->Y in the estimated graph if X is not adjacent
 * to Y or X--Y or X<--Y.
 *
 * @author jdramsey, rubens (November, 2016)
 */
public class ArrowConfusion {

    private Graph truth;
    private Graph est;
    private int arrowsTp;
    private int arrowsFp;
    private int arrowsFn;
    private int arrowsTn;
    private int TCtp;
    private int TCfn;
    private int TCfp;

    public ArrowConfusion(Graph truth, Graph est) {
//        System.out.println("truth = " + truth);
//        System.out.println("est = " + est);


        this.truth = truth;
        this.est = est;
        arrowsTp = 0;
        arrowsFp = 0;
        arrowsFn = 0;
        TCtp = 0; //for the two-cycle accuracy
        TCfn = 0;
        TCfp = 0;


        // Get edges from the true Graph to compute TruePositives, TrueNegatives and FalseNeagtives
        //    System.out.println(this.truth.getEdges());

        for (Edge edge : this.truth.getEdges()) {
            Edge edgeT = getDirectedEdge(edge.getNode1(), edge.getNode2(), truth);
            Edge edgeE = getDirectedEdge(edge.getNode1(), edge.getNode2(), est);

            if (edgeT != null) {
                if (edgeE != null) {
                    arrowsTp++;
                } else {
                    arrowsFn++;
                }
            }
        }

        for (Edge edge : this.est.getEdges()) {
            Edge edgeE = getDirectedEdge(edge.getNode1(), edge.getNode2(), est);
            Edge edgeT = getDirectedEdge(edge.getNode1(), edge.getNode2(), truth);

            if (edgeE != null && edgeT == null) {
                arrowsFp++;
            }
        }

        int n = this.truth.getNumNodes();
        int m = n * (n - 1) / 2;
        arrowsTn = m - this.truth.getNumEdges();

        // test for 2-cycle
        //Set<Edge> allOriented = new HashSet<>();
        //allOriented.addAll(this.truth.getEdges());
        //allOriented.addAll(this.est.getEdges());

        List<Node> nodes = this.truth.getNodes();

        Graph adjacencies = new EdgeListGraph(nodes);

        for (Edge edge : truth.getEdges()) {
            adjacencies.addUndirectedEdge(edge.getNode1(), edge.getNode2());
        }

        for (Edge edge : est.getEdges()) {
            adjacencies.addUndirectedEdge(edge.getNode1(), edge.getNode2());
        }

        for (Edge edge : adjacencies.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();

            Edge edgeT1 = getDirectedEdge(n1, n2, truth);
            Edge edgeT2 = getDirectedEdge(n2, n1, truth);
            Edge edgeE1 = getDirectedEdge(n1, n2, est);
            Edge edgeE2 = getDirectedEdge(n2, n1, est);

            boolean twocycleT = edgeT1 != null && edgeT2 != null;
            boolean twocycleE = edgeE1 != null && edgeE2 != null;

            if (twocycleT && twocycleE) {
                TCtp++;
            }

            if (twocycleT && !twocycleE) {
                TCfn++;
            }

            if (twocycleE && !twocycleT) {
                TCfp++;
            }


        }
// Get edges from the estimated graph to compute only FalsePositives
        // System.out.println(this.est.getEdges());

        for (Edge edge : this.est.getEdges()) {

            List<Edge> edges1 = this.est.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge1;

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

            List<Edge> edges2 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
            Edge edge2;

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


            if (e1Est == Endpoint.ARROW && e1True != Endpoint.ARROW) {
                arrowsFp++;
            }

            if (e2Est == Endpoint.ARROW && e2True != Endpoint.ARROW) {
                arrowsFp++;
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

  /*      System.out.println(arrowsTp);
        System.out.println(arrowsTn);
        System.out.println(arrowsFn);
        System.out.println(arrowsFp);
*/
        //divide by 2, the 2cycle accuracy is duplicated due to how getEdges is used
        TCtp = TCtp / 2;
        TCfn = TCfn / 2;
        TCfp = TCfp / 2;
 //       System.out.println(TCtp);
 //       System.out.println(TCfn);
 //       System.out.println(TCfp);

//        for (Edge edge : this.truth.getEdges()) {
//            List<Edge> TwoCycle1 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
//            List<Edge> TwoCycle2 = this.est.getEdges(edge.getNode1(), edge.getNode2());
//
//            if (TwoCycle1.size() == 2 && TwoCycle2.size() == 2) {
//                //              System.out.println("2-cycle correctly inferred " + TwoCycle1);
//                TCtp++;
//            }
//
//            if (TwoCycle1.size() == 2 && TwoCycle2.size() != 2) {
//                //             System.out.println("2-cycle not inferred " + TwoCycle1);
//                TCfn++;
//            }
//        }

//        for (Edge edge : this.est.getEdges()) {
//
//            List<Edge> TwoCycle1 = this.truth.getEdges(edge.getNode1(), edge.getNode2());
//            List<Edge> TwoCycle2 = this.est.getEdges(edge.getNode1(), edge.getNode2());
//
//            if (TwoCycle1.size() != 2 && TwoCycle2.size() == 2) {
//                //              System.out.println("2-cycle falsely inferred" + TwoCycle2);
//                TCfp++;
//            }
//        }

//        System.out.println("Arrow TP = " + arrowsTp);
//        System.out.println("Arrow TN = " + arrowsTn);
//        System.out.println("Arrow FN = " + arrowsFn);
//        System.out.println("Arrow FP = " + arrowsFp);

        //divide by 2, the 2cycle accuracy is duplicated due to how getEdges is used
//        TCtp = TCtp / 2;
//        TCfn = TCfn / 2;
//        TCfp = TCfp / 2;
//        System.out.println("TCTP = " + TCtp);
//        System.out.println("TCFN = " + TCfn);
//        System.out.println("TCFP = " + TCfp);

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

    private Edge getDirectedEdge(Node node1, Node node2, Graph graph) {
        List<Edge> edges = graph.getEdges(node1, node2);

        if (edges == null) return null;

        if (edges.size() == 0) {
            return null;
        }

        for (Edge edge : edges) {
            if (Edges.isDirectedEdge(edge) && edge.pointsTowards(node2)) {
                return edge;
            }
        }

        return null;
    }
}
