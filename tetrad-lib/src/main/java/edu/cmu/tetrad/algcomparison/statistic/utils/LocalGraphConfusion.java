package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A confusion matrix for local graph accuracy check --i.e. TP, FP, TN, FN for counts of a combination of
 * arrowhead and precision.
 */
public class LocalGraphConfusion {
    /**
     * The true positive count.
     */
    private int tp;

    /**
     * The true negative count.
     */
    private int tn;

    /**
     * The false positive count.
     */
    private int fp;

    /**
     * The false positive count.
     */
    private int fn;

    /**
     * Constructs a new LocalGraphConfusion object from the given graphs.
     * @param trueGraph The true graph
     *
     * @param estGraph The estimated graph
     */
    public LocalGraphConfusion(Graph trueGraph, Graph estGraph) {
        this.tp = 0;
        this.tn = 0;
        this.fp = 0;
        this.fn = 0;

        // STEP0: Create lookups for both true graph and estimated graph.
        // trueGraphLookup is the same structure as trueGraph's structure but node objects replaced by estimated graph nodes.
        Graph trueGraphLookup = GraphUtils.replaceNodes(trueGraph, estGraph.getNodes());
        // estGraphLookup is the same structure as estGraph's structure but node objects replaced by true graph nodes.
        Graph estGraphLookup = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        // STEP1: Check for Adjacency.
        /**
         *                     True
         *               Y             N
         *            ---------------------
         *         Y |    TP            FP
         *    Est    | --------------------
         *         N |    FN            TN
         *           -----------------------
         */
        // STEP 1.1: Create allUnoriented base on trueGraphLookup and estimatedGraph
        Set<Edge> allUnoriented = new HashSet<>();
        for (Edge edge: trueGraphLookup.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }
        for (Edge edge: estGraph.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }
        // STEP 1.2: Iterate through allUnoriented to record confusion metrix
        for (Edge u: allUnoriented) {
            Node node1 = u.getNode1();
            Node node2 = u.getNode2();
            if (estGraph.isAdjacentTo(node1, node2)) { // Est: Y
                if (trueGraphLookup.isAdjacentTo(node1, node2)) { // True: Y
                    this.tp++;
                } else { // True: N
                    this.fp++;
                }
            } else { // Est: N
                if (trueGraphLookup.isAdjacentTo(node1, node2)) { // True: Y
                    this.fn++;
                } else { // True: N
                    this.tn++;
                }
            }
        }

        // STEP2: Check for Orientation(i.e. Arrowhead), so we need to check both endpoints of an edge.
        /**
         *                     True
         *                ->          <-        ...(None)
         *            ---------------------------
         *         -> |   TP        FP,FN       / (Do not repeat count, as we checked for it in Adj step)
         *    Est     | --------------------------
         *         <- |   FP, FN     TP         /
         *            | --------------------------
         *         -- |   0         0           /       (0 means unknown, do nothing)
         *            | --------------------------
         *         ...|    /         /          /
         *           -----------------------------
         *
         */
        // STEP2.1: Check through the true graph
        for (Edge tle: trueGraphLookup.getEdges()) {
            // STEP2.1.1: Get corresponding endpoint in Est graph lookup
            List<Edge> estGraphLookupEdges = estGraphLookup.getEdges(tle.getNode1(), tle.getNode2());
            Edge ele; // estimated lookup graph edge
            if (estGraphLookupEdges.size() == 1) {
                ele = estGraphLookupEdges.iterator().next();
            } else {
                ele = estGraphLookup.getDirectedEdge(tle.getNode1(), tle.getNode2());
            }
            Endpoint ep1Est = null;
            Endpoint ep2Est = null;
            if (ele != null) {
                ep1Est = ele.getProximalEndpoint(tle.getNode1());
                ep2Est = ele.getProximalEndpoint(tle.getNode2());
            }

            // STEP2.1.2: Get corresponding endpoint in true graph lookup
            List<Edge> trueGraphLookupEdges = trueGraphLookup.getEdges(tle.getNode1(), tle.getNode1());
            Edge tle2;
            if (trueGraphLookupEdges.size() == 1) {
                tle2 = trueGraphLookupEdges.iterator().next();
            } else {
                tle2 = trueGraphLookup.getDirectedEdge(tle.getNode1(), tle.getNode2());
            }
            Endpoint ep1True = null;
            Endpoint ep2True = null;
            if (tle2 != null) {
                ep1True = tle2.getProximalEndpoint(tle.getNode1());
                ep2True = tle2.getProximalEndpoint(tle.getNode2());
            }

            // STEP2.1.3: Compare the endpoints
            // we only care the case when the edge exist.
            boolean connected = trueGraph.isAdjacentTo(tle.getNode1(), tle.getNode2())
                    && estGraph.isAdjacentTo(tle.getNode1(), tle.getNode2());
            if (connected) {
                if (ep1True == Endpoint.TAIL && ep2True == Endpoint.ARROW) { // True: ->
                    if (ep1Est == Endpoint.TAIL && ep2Est == Endpoint.ARROW) { // Est: ->
                        this.tp++;
                    } else if (ep1Est == Endpoint.ARROW && ep2Est == Endpoint.TAIL) { // Est: <-
                        // this.fp++;
                        this.fn++;
                    } else if (ep1Est == Endpoint.TAIL && ep2Est == Endpoint.TAIL) { // Est: --
                        // -- means Unknown, do nothing
                    }
                } else if (ep1True == Endpoint.ARROW && ep2True == Endpoint.TAIL) { // True: <-
                    if (ep1Est == Endpoint.TAIL && ep2Est == Endpoint.ARROW) { // Est: ->
                        // this.fp++;
                        this.fn++;
                    } else if (ep1Est == Endpoint.ARROW && ep2Est == Endpoint.TAIL) { // Est: <-
                        this.tp++;
                    } else if (ep1Est == Endpoint.TAIL && ep2Est == Endpoint.TAIL) { // Est: --
                        // -- means Unknown, do nothing
                    }
                }
            }
        }
        // STEP2: Check through the est graph
        // because est graph can have extra arrowhead that was not in true graph, which should be count as fp.
        for (Edge ele: estGraphLookup.getEdges()) {
            List<Edge> estGraphLookupEdges = estGraphLookup.getEdges(ele.getNode1(), ele.getNode2());
            Edge ele2;
            if (estGraphLookupEdges.size() == 1) {
                ele2 = estGraphLookupEdges.iterator().next();
            } else {
                ele2 = estGraphLookup.getDirectedEdge(ele.getNode1(), ele.getNode2());
            }
            Endpoint ep1Est = null;
            Endpoint ep2Est = null;
            if (ele2 != null) {
                ep1Est = ele2.getProximalEndpoint(ele.getNode1());
                ep2Est = ele2.getProximalEndpoint(ele.getNode2());
            }

            List<Edge> trueGraphLookupEdges = trueGraphLookup.getEdges(ele.getNode1(), ele.getNode1());
            Edge tle;
            if (trueGraphLookupEdges.size() == 1) {
                tle = trueGraphLookupEdges.iterator().next();
            } else {
                tle = trueGraphLookup.getDirectedEdge(ele.getNode1(), ele.getNode2());
            }
            Endpoint ep1True = null;
            Endpoint ep2True = null;
            if (tle != null) {
                ep1True = tle.getProximalEndpoint(ele.getNode1());
                ep2True = tle.getProximalEndpoint(ele.getNode2());
            }

            boolean connected = trueGraph.isAdjacentTo(ele.getNode1(), ele.getNode2());
            if (connected) {
                if (ep1True == Endpoint.TAIL && ep2True == Endpoint.ARROW) { // True: ->
                    if (ep1Est == Endpoint.ARROW && ep2Est == Endpoint.TAIL) { // Est: <-
                        this.fp++;
                    }
                    // TODO VBC: Question: seems we wont encounter <-> case, is it?
                } else if (ep1True == Endpoint.ARROW && ep2True == Endpoint.TAIL) { // True: <-
                    if (ep1Est == Endpoint.TAIL && ep2Est == Endpoint.ARROW) { // Est: ->
                        this.fp++;
                    }
                }
            }
        }
    }

    /**
     * Returns the true positives (TP) value of the LocalGraphConfusion object.
     *
     * @return The true positives (TP) value.
     */
    public int getTp() {
        return tp;
    }

    /**
     * Retrieves the value of true negatives (TN) from the LocalGraphConfusion object.
     *
     * @return The true negatives (TN) value.
     */
    public int getTn() {
        return tn;
    }

    /**
     * Retrieves the value of false positives (FP) from the LocalGraphConfusion object.
     *
     * @return The false positives (FP) value.
     */
    public int getFp() {
        return fp;
    }

    /**
     * Returns the false negatives (FN) value of the LocalGraphConfusion object.
     *
     * @return The false negatives (FN) value.
     */
    public int getFn() {
        return fn;
    }
}
