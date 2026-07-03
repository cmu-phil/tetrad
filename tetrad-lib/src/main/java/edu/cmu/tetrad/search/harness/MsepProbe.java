/// ////////////////////////////////////////////////////////////////////////////
// MsepProbe.java                                                              //
//                                                                             //
// Settles ONE question in isolation, with no harness machinery:               //
//   In the true MAG G* of the RESIDUE case (mask 314672), is V2 _||_ V3 |     //
//   {V1,V5} ?                                                                 //
//                                                                             //
// Hand analysis says NO (m-CONNECTED): the path V2 -> V4 <-> V3 has a         //
// collider at V4; V5 is a descendant of V4 (V4 -> V5) and V5 is in the        //
// conditioning set, so the collider is ACTIVE and the path is open.           //
//                                                                             //
// The harness's oracle (new MsepTest(canonMag)) reported INDEPENDENT for this //
// set, which is what drove the spurious "RESIDUE counterexample."  This probe //
// asks MsepTest the same question on a freshly, explicitly built MAG, plus a  //
// few controls, so we can tell whether the fault is:                          //
//   * MsepTest itself (probe disagrees with hand analysis on the clean MAG),  //
//   * or something in how the harness constructs/relabels canonMag (probe     //
//     AGREES with hand analysis, so the harness's canonMag differs from this  //
//     explicit one).                                                          //
//                                                                             //
// Run main() in IntelliJ against the same Tetrad build.  No file output; all  //
// results print to stdout.                                                    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;

import java.util.*;

public final class MsepProbe {

    public static void main(String[] args) {
        // Build the true MAG G* exactly as printed in the log (canonical labels):
        //   1. V1 --> V2
        //   2. V1 --> V3
        //   3. V2 --> V4
        //   4. V3 <-> V4
        //   5. V3 <-> V5
        //   6. V4 --> V5
        Node v1 = new GraphNode("V1");
        Node v2 = new GraphNode("V2");
        Node v3 = new GraphNode("V3");
        Node v4 = new GraphNode("V4");
        Node v5 = new GraphNode("V5");
        List<Node> nodes = Arrays.asList(v1, v2, v3, v4, v5);

        Graph mag = new EdgeListGraph(new ArrayList<>(nodes));
        mag.addDirectedEdge(v1, v2);
        mag.addDirectedEdge(v1, v3);
        mag.addDirectedEdge(v2, v4);
        mag.addBidirectedEdge(v3, v4);
        mag.addBidirectedEdge(v3, v5);
        mag.addDirectedEdge(v4, v5);

        System.out.println("MAG G* as built:");
        System.out.println(mag);
        System.out.println();

        // Sanity: descendant relation the collider-activation rule depends on.
        System.out.println("--- structural sanity ---");
        System.out.println("V4 is collider on <V2,V4,V3>? "
                + mag.isDefCollider(v2, v4, v3)
                + "   (endpoints: V2->V4 arrow=" + (mag.getEndpoint(v2, v4) == Endpoint.ARROW)
                + ", V3<->V4 arrow at V4=" + (mag.getEndpoint(v3, v4) == Endpoint.ARROW) + ")");
        System.out.println("V5 a descendant of V4? " + isDesc(mag, v4, v5)
                + "   (V4->V5 present: " + (mag.getEdge(v4, v5) != null) + ")");
        System.out.println();

        MsepTest msep = new MsepTest(mag);

        // THE question that decides the RESIDUE finding.
        report(msep, v2, v3, set(v1, v5), "DECISIVE: V2 _||_ V3 | {V1,V5}",
                "EXPECT m-CONNECTED (dependent): collider V4 activated by descendant V5");

        // Controls.
        report(msep, v2, v3, set(v1), "V2 _||_ V3 | {V1}",
                "EXPECT m-SEPARATED (independent): collider V4 NOT activated, V1 blocks the direct forks");
        report(msep, v2, v3, set(v1, v4), "V2 _||_ V3 | {V1,V4}",
                "EXPECT m-CONNECTED: conditioning on collider V4 directly opens it");
        report(msep, v2, v3, set(v1, v4, v5), "V2 _||_ V3 | {V1,V4,V5}",
                "EXPECT m-CONNECTED: collider open");
        report(msep, v2, v3, set(), "V2 _||_ V3 | {}",
                "EXPECT m-CONNECTED: V1 fork open (V2<-V1->V3)");

        // Isolate the mechanism: does conditioning on the descendant alone open it?
        report(msep, v2, v3, set(v5), "V2 _||_ V3 | {V5}",
                "diagnostic: descendant-only activation (V1 fork also open here)");

        System.out.println("\n--- verdict ---");
        boolean dep = !msep.checkIndependence(v2, v3, set(v1, v5)).isIndependent();
        if (dep) {
            System.out.println("MsepTest reports V2,V3 DEPENDENT given {V1,V5} -- AGREES with hand analysis.");
            System.out.println("=> MsepTest is correct here; the harness's oracle must have been called on a");
            System.out.println("   DIFFERENT graph than this explicit MAG.  Compare canonMag construction/relabel.");
        } else {
            System.out.println("MsepTest reports V2,V3 INDEPENDENT given {V1,V5} -- DISAGREES with hand analysis.");
            System.out.println("=> MsepTest is not activating the V4 collider via its descendant V5 on this MAG.");
            System.out.println("   Minimal reproducible m-separation discrepancy; escalate with THIS graph.");
        }

        // ── RELABEL ROUND-TRIP TEST ──────────────────────────────────────────
        // The harness never tests the MAG above directly; it tests relabel(trueMag,...).
        // Reproduce the two relabel strategies on a permutation that FLIPS the sort order
        // of the V4->V5 pair, and check whether V4->V5 survives and the DECISIVE CI holds.
        System.out.println("\n=== RELABEL ROUND-TRIP (does relabeling preserve V4->V5 and the CI?) ===");
        // A permutation of names that reverses order, so any pair whose mapped names sort
        // opposite to the originals will exercise Edge's internal reordering.
        Map<String, String> rename = new HashMap<>();
        rename.put("V1", "V5"); rename.put("V2", "V4"); rename.put("V3", "V3");
        rename.put("V4", "V2"); rename.put("V5", "V1");

        System.out.println("\n[OLD relabel: positional endpoints]");
        Graph oldR = relabelPositional(mag, rename);
        checkRoundTrip(oldR, rename);

        System.out.println("\n[NEW relabel: endpoints by node identity]");
        Graph newR = relabelByIdentity(mag, rename);
        checkRoundTrip(newR, rename);

        // ── THE CASE THAT ACTUALLY TRIGGERS THE BUG ──────────────────────────
        // The round-trips above used a MAG built with addDirectedEdge (tail-first
        // storage), so Edge's pointingLeft flip NEVER fires and every relabel looks
        // clean.  The bug only bites edges stored ARROW-FIRST (endpoint1==ARROW), which
        // is how some dagToMag/dagToPag edges come out.  Build one explicitly and show
        // the 4-arg (flipping) vs 5-arg-false (non-flipping) constructors diverge.
        System.out.println("\n=== ARROW-FIRST EDGE (the real trigger) ===");
        Node p = new GraphNode("P"), q = new GraphNode("Q");
        // Intend P <-- Q  i.e. a directed edge Q --> P, but STORED arrow-first:
        // endpoint1 (at P) = ARROW, endpoint2 (at Q) = TAIL.
        Edge arrowFirst = new Edge(p, q, Endpoint.ARROW, Endpoint.TAIL, false); // no flip: store as given
        System.out.println("built arrow-first edge, endpoint1@P=" + arrowFirst.getEndpoint1()
                + " endpoint2@Q=" + arrowFirst.getEndpoint2()
                + "  (semantically Q --> P)");
        // Now relabel P->P, Q->Q (identity names) via the OLD 4-arg constructor path:
        Edge viaFlipping = new Edge(p, q, arrowFirst.getEndpoint1(), arrowFirst.getEndpoint2()); // 4-arg, flip=true
        System.out.println("4-arg (flipping) reconstruction: node1=" + viaFlipping.getNode1().getName()
                + " endpoint1=" + viaFlipping.getEndpoint1()
                + ", node2=" + viaFlipping.getNode2().getName()
                + " endpoint2=" + viaFlipping.getEndpoint2());
        System.out.println("  -> arrowhead now at " + (viaFlipping.getProximalEndpoint(p) == Endpoint.ARROW ? "P" : "Q")
                + "  (want: P).  " + (viaFlipping.getProximalEndpoint(p) == Endpoint.ARROW
                ? "PRESERVED" : "*** FLIPPED: arrowhead moved to the wrong node ***"));
        Edge viaNonFlip = new Edge(p, q, arrowFirst.getEndpoint1(), arrowFirst.getEndpoint2(), false); // 5-arg false
        System.out.println("5-arg-false reconstruction: arrowhead at "
                + (viaNonFlip.getProximalEndpoint(p) == Endpoint.ARROW ? "P" : "Q")
                + "  (want: P).  " + (viaNonFlip.getProximalEndpoint(p) == Endpoint.ARROW
                ? "PRESERVED (this is the fix)" : "*** still wrong ***"));

        Graph cm = mag;//relabel(mag);//GraphTransforms.dagToMag(dag314672), /* obsSorted, perm, canonNodes */);
        System.out.println("getChildren(V4) = " + cm.getChildren(cm.getNode("V4")));
        System.out.println("descMap.get(V4) = " + cm.paths().getDescendantsMap().get(cm.getNode("V4")));
        System.out.println("isAncestorOf(V4,V5) = " + cm.paths().isAncestorOf(cm.getNode("V4"), cm.getNode("V5")));
        System.out.println("edge V4-V5 = " + cm.getEdge(cm.getNode("V4"), cm.getNode("V5")));
    }

    /** Relabel g's observed nodes: obsSorted.get(i) -> canonNodes.get(perm[i]). */
    private static Graph relabel(Graph g, List<Node> obsSorted, int[] perm, List<Node> canonNodes) {
        Map<String, Node> map = new HashMap<>();
        for (int i = 0; i < obsSorted.size(); i++) {
            map.put(obsSorted.get(i).getName(), canonNodes.get(perm[i]));
        }
        Graph out = new EdgeListGraph(canonNodes);
        for (Edge e : g.getEdges()) {
            // flipIfBackwards=false: the 4-arg Edge constructor silently swaps nodes AND
            // endpoints when the edge is "pointing left" (endpoint1==ARROW).  Passing mapped
            // nodes with original endpoints then reattaches endpoints to the swapped nodes,
            // reversing directed edges stored arrow-first by dagToMag/dagToPag.  The 5-arg
            // form with false stores nodes and endpoints exactly as given.
            out.addEdge(new Edge(map.get(e.getNode1().getName()), map.get(e.getNode2().getName()),
                    e.getEndpoint1(), e.getEndpoint2(), false));
        }
        return out;
    }

    // Old harness logic: new Edge(a,b,ep1,ep2) positionally.
    private static Graph relabelPositional(Graph g, Map<String, String> rename) {
        Map<String, Node> nn = new HashMap<>();
        for (Node v : g.getNodes()) nn.put(v.getName(), new GraphNode(rename.get(v.getName())));
        Graph out = new EdgeListGraph(new ArrayList<>(nn.values()));
        for (Edge e : g.getEdges()) {
            out.addEdge(new Edge(nn.get(e.getNode1().getName()), nn.get(e.getNode2().getName()),
                    e.getEndpoint1(), e.getEndpoint2()));
        }
        return out;
    }

    // New logic: endpoints set by node identity.
    private static Graph relabelByIdentity(Graph g, Map<String, String> rename) {
        Map<String, Node> nn = new HashMap<>();
        for (Node v : g.getNodes()) nn.put(v.getName(), new GraphNode(rename.get(v.getName())));
        Graph out = new EdgeListGraph(new ArrayList<>(nn.values()));
        for (Edge e : g.getEdges()) {
            Node n1 = e.getNode1(), n2 = e.getNode2();
            Node a = nn.get(n1.getName()), b = nn.get(n2.getName());
            Edge ne = new Edge(a, b, Endpoint.NULL, Endpoint.NULL);
            ne.setProximalEndpoint(a, e.getProximalEndpoint(n1));
            ne.setProximalEndpoint(b, e.getProximalEndpoint(n2));
            out.addEdge(ne);
        }
        return out;
    }

    // After renaming, original V4->V5 becomes (rename V4=V2)->(rename V5=V1), i.e. V2->V1.
    // Check that directed edge survives with the correct orientation, and re-ask the CI
    // (original V2,V3,{V1,V5} -> renamed V4,V3,{V5,V1}).
    private static void checkRoundTrip(Graph r, Map<String, String> rename) {
        System.out.println("  relabeled graph:");
        System.out.println(indent(r.toString()));
        Node rv4 = r.getNode(rename.get("V4"));  // = V2
        Node rv5 = r.getNode(rename.get("V5"));  // = V1
        Edge e45 = r.getEdge(rv4, rv5);
        System.out.println("  original V4->V5 is now edge " + rename.get("V4") + "-" + rename.get("V5")
                + ": " + e45
                + "   directed " + rename.get("V4") + "->" + rename.get("V5") + "? "
                + (e45 != null && e45.getProximalEndpoint(rv4) == Endpoint.TAIL
                && e45.getProximalEndpoint(rv5) == Endpoint.ARROW));
        Node rv2 = r.getNode(rename.get("V2"));  // = V4
        Node rv3 = r.getNode(rename.get("V3"));  // = V3
        Node rv1 = r.getNode(rename.get("V1"));  // = V5
        boolean indep = new MsepTest(r).checkIndependence(rv2, rv3, set(rv1, rv5)).isIndependent();
        System.out.println("  DECISIVE CI (renamed): " + rename.get("V2") + " _||_ " + rename.get("V3")
                + " | {" + rename.get("V1") + "," + rename.get("V5") + "} -> "
                + (indep ? "INDEPENDENT (WRONG -- relabel corrupted the graph)"
                : "DEPENDENT (correct -- relabel preserved it)"));
    }

    private static String indent(String s) {
        StringBuilder b = new StringBuilder();
        for (String line : s.split("\n")) b.append("    ").append(line).append('\n');
        return b.toString();
    }

    private static void report(MsepTest t, Node x, Node y, Set<Node> z, String label, String expect) {
        boolean indep = t.checkIndependence(x, y, z).isIndependent();
        System.out.printf("%-34s -> %s   [%s]%n",
                label, indep ? "INDEPENDENT (m-sep)" : "DEPENDENT (m-conn)", expect);
    }

    private static boolean isDesc(Graph g, Node a, Node of) {
        // a's descendants include of?
        return g.paths().isAncestorOf(a, of);
    }

    private static Set<Node> set(Node... ns) {
        return new HashSet<>(Arrays.asList(ns));
    }
}
