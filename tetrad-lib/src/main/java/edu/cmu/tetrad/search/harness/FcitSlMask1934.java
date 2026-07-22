/// ////////////////////////////////////////////////////////////////////////////
// FcitSlMask1934.java                                                        //
//                                                                            //
// Standalone reproducer for the first PKE8 violation:                        //
//                                                                            //
//   ==== VIOLATION [SKELETON_DIFF] ====                                      //
//     config : escape=false zMax=5 forkFlips=2 depth=-1 recursiveDepth=-1     //
//     mask=1934  latents={X1,X2}  X3->V5, X4->V2, X5->V1, X6->V4, X7->V3     //
//     terminal PAG carries an extra V4 o-o V5 that G* does not.               //
//                                                                            //
// The true MAG is rebuilt DIRECTLY from the canonical-label edge list in the  //
// violation entry, which is exactly the graph PKE8 wraps in MsepTest and      //
// GraphScore -- so this runs the same search on the same oracle, with no      //
// dependence on the mask/latent/relabel reconstruction.                       //
//                                                                            //
// It answers four questions, in order:                                       //
//   (0) TARGET      -- does MagToPag(trueMag) equal the printed G*?  If not,  //
//                      PKE8's comparison target disagrees with the algorithm's//
//                      own projection and some "violations" are harness bugs. //
//   (1) ORACLE      -- what does the oracle actually say about {V4,V5} and    //
//                      the other non-adjacency, at every |Z|?  This fixes the //
//                      separator the commit is entitled to use.               //
//   (2) PURE vs ESCAPE -- run with allowClassEscape false, then true.  The    //
//                      interface hardcodes true; PKE8 ran false.  If pure     //
//                      stalls and escape clears it, the discrepancy is        //
//                      configuration, not a harness fault, and the within-    //
//                      class search is the thing that is short.               //
//   (3) DETERMINISM -- run the pure configuration REPEATS times and count     //
//                      distinct terminal PAGs.  More than one means the       //
//                      result is scheduling-dependent (the speculative        //
//                      foundSepsets writes in the parallel lookahead are the  //
//                      prime suspect), and any single run -- in the interface //
//                      or in the harness -- is only a sample.                 //
//                                                                            //
// Run from IntelliJ.  For the determinism question also try:                  //
//   -Djava.util.concurrent.ForkJoinPool.common.parallelism=1                  //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FcitSl;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Reproducer for the mask-1934 PKE8 violation.
 */
public final class FcitSlMask1934 {

    /** Config mirrored from the violation entry. */
    private static final boolean VERBOSE_RUN = true;
    private static final int BATTERY_Z_MAX = 5;
    private static final int MAX_FORK_FLIPS = 2;
    private static final int DEPTH = -1;
    private static final int RECURSIVE_DEPTH = -1;
    private static final boolean EXCLUDE_SELECTION_BIAS = true;
    private static final int REPEATS = 20;

    private FcitSlMask1934() {
    }

    /**
     * Main.
     *
     * @param args unused
     * @throws Exception if the search throws
     */
    public static void main(String[] args) throws Exception {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 5; i++) nodes.add(new GraphNode("V" + i));

        Graph trueMag = trueMag(nodes);
        Graph expectedPag = expectedPag(nodes);

        System.out.println("=== true MAG G* (from the violation entry) ===");
        System.out.println(trueMag);
        System.out.println("=== expected PAG G* (from the violation entry) ===");
        System.out.println(expectedPag);

        // ---- (0) TARGET SELF-CHECK ----------------------------------------------------------
        // PKE8's target comes from dagToPag over the 7-node latent DAG; FcitSl's terminal PAG
        // comes from MagToPag over a MAG.  If those two projections disagree, a perfect run is
        // logged as a violation.  (Here the skeletons already differ, so this is not the whole
        // story -- but it is the cheapest thing to rule out, and it is worth knowing whether the
        // two conventions agree on this class at all.)
        Graph projected = new MagToPag(new EdgeListGraph(trueMag)).convert(false, EXCLUDE_SELECTION_BIAS);
        System.out.println("=== (0) MagToPag(trueMag) ===");
        System.out.println(projected);
        System.out.println("MagToPag(trueMag) == printed G* ?  " + identical(projected, expectedPag));
        System.out.println();

        // ---- (1) WHAT THE ORACLE SAYS -------------------------------------------------------
        // The two non-adjacencies of G* are {V3,V5} and {V4,V5}.  V4--V5 is the edge that
        // survives.  Print every separator the oracle grants each pair, so the separator the
        // commit is entitled to use is not in doubt.
        MsepTest oracle = new MsepTest(new EdgeListGraph(trueMag));
        System.out.println("=== (1) oracle separators ===");
        reportSeparators(oracle, nodes, "V3", "V5");
        reportSeparators(oracle, nodes, "V4", "V5");
        System.out.println();

        // ---- (2) PURE vs ESCAPE -------------------------------------------------------------
        TetradLogger.getInstance().setLogging(true);

        System.out.println("=== (2a) allowClassEscape = FALSE (Step-Lemma-pure; PKE8's config) ===");
        Graph pure = runFcit(trueMag, nodes, false, VERBOSE_RUN);
        report("pure", pure, expectedPag);

        System.out.println("=== (2b) allowClassEscape = TRUE (the algcomparison wrapper's config) ===");
        Graph esc = runFcit(trueMag, nodes, true, VERBOSE_RUN);
        report("escape", esc, expectedPag);

        System.out.println("VERDICT (2): "
                + (identical(pure, expectedPag)
                ? "pure reaches G* -- the PKE8 violation is NOT reproduced here; suspect the harness "
                + "or run-to-run variation (see (3))."
                : identical(esc, expectedPag)
                ? "pure STALLS and escape CLEARS it -- the interface/PKE8 split is configuration, and "
                + "the WITHIN-CLASS search is what falls short on this class."
                : "BOTH stall -- the failure is independent of the escape hatch."));
        System.out.println();

        // ---- (3) DETERMINISM ----------------------------------------------------------------
        // FcitSl's per-sweep lookahead is a parallel stream whose speculative branches write into
        // foundSepsets even when findFirst short-circuits, so WHICH cached separators survive can
        // depend on thread timing -- and those caches steer later rounds.  If the terminal PAG
        // varies across identical runs, no single run (interface or harness) settles anything.
        System.out.println("=== (3) determinism: " + REPEATS + " identical pure runs ===");
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (int i = 0; i < REPEATS; i++) {
            Graph g = runFcit(trueMag, nodes, false, false);
            tally.merge(key(g), 1, Integer::sum);
        }
        System.out.println("distinct terminal PAGs: " + tally.size());
        for (Map.Entry<String, Integer> e : tally.entrySet()) {
            System.out.println("  x" + e.getValue() + "  " + e.getKey()
                    + (e.getKey().equals(key(expectedPag)) ? "   <-- G*" : ""));
        }
        System.out.println("VERDICT (3): " + (tally.size() == 1
                ? "deterministic across " + REPEATS + " runs at this parallelism."
                : "NON-DETERMINISTIC -- scheduling-dependent; re-run with "
                + "-Djava.util.concurrent.ForkJoinPool.common.parallelism=1 to confirm."));
    }

    // ────────────────────────────────────────────────────────────────────────
    // The model, verbatim from the violation entry
    // ────────────────────────────────────────────────────────────────────────

    private static Graph trueMag(List<Node> nodes) {
        Graph g = new EdgeListGraph(nodes);
        bi(g, nodes, "V1", "V2");
        bi(g, nodes, "V1", "V5");
        bi(g, nodes, "V2", "V5");
        bi(g, nodes, "V3", "V1");
        bi(g, nodes, "V3", "V2");
        bi(g, nodes, "V3", "V4");
        bi(g, nodes, "V4", "V1");
        bi(g, nodes, "V4", "V2");
        if (!g.paths().isLegalMag()) {
            throw new IllegalStateException("rebuilt trueMag is not a legal MAG -- check the edge list");
        }
        return g;
    }

    private static Graph expectedPag(List<Node> nodes) {
        Graph g = new EdgeListGraph(nodes);
        pag(g, nodes, "V1", "V2", Endpoint.CIRCLE, Endpoint.CIRCLE);
        pag(g, nodes, "V3", "V1", Endpoint.CIRCLE, Endpoint.ARROW);
        pag(g, nodes, "V3", "V2", Endpoint.CIRCLE, Endpoint.ARROW);
        pag(g, nodes, "V3", "V4", Endpoint.CIRCLE, Endpoint.CIRCLE);
        pag(g, nodes, "V4", "V1", Endpoint.CIRCLE, Endpoint.ARROW);
        pag(g, nodes, "V4", "V2", Endpoint.CIRCLE, Endpoint.ARROW);
        pag(g, nodes, "V5", "V1", Endpoint.CIRCLE, Endpoint.ARROW);
        pag(g, nodes, "V5", "V2", Endpoint.CIRCLE, Endpoint.ARROW);
        return g;
    }

    private static void bi(Graph g, List<Node> nodes, String a, String b) {
        g.addBidirectedEdge(node(nodes, a), node(nodes, b));
    }

    /** 5-arg Edge form with flipIfBackwards=false: endpoints stay attached to the given nodes. */
    private static void pag(Graph g, List<Node> nodes, String a, String b, Endpoint ea, Endpoint eb) {
        g.addEdge(new Edge(node(nodes, a), node(nodes, b), ea, eb, false));
    }

    private static Node node(List<Node> nodes, String name) {
        for (Node n : nodes) if (n.getName().equals(name)) return n;
        throw new IllegalArgumentException("no node " + name);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Running the search
    // ────────────────────────────────────────────────────────────────────────

    /**
     * One FcitSl run, configured exactly as PKE8 configures it.  Fresh graph copies per run:
     * FcitSl flips node types internally, so the same Graph object must not be reused.
     */
    private static Graph runFcit(Graph trueMag, List<Node> nodes, boolean escape, boolean verbose)
            throws InterruptedException {
        MsepTest test = new MsepTest(new EdgeListGraph(trueMag));
        GraphScore score = new GraphScore(new EdgeListGraph(trueMag));

        FcitSl fcit = new FcitSl(test, score);          // MsepTest => startWith flips to GRASP
        fcit.setKnowledge(new Knowledge());
        fcit.setExcludeSelectionBias(EXCLUDE_SELECTION_BIAS);
        fcit.setCompleteRuleSetUsed(true);
        fcit.setDepth(DEPTH);
        fcit.setRecursiveDepth(RECURSIVE_DEPTH);
        fcit.setBatteryZMax(BATTERY_Z_MAX);
        fcit.setMaxForkFlips(MAX_FORK_FLIPS);
        fcit.setAllowClassEscape(escape);
        fcit.setTimeout(-1L);
        fcit.setVerbose(verbose);

        return GraphUtils.replaceNodes(fcit.search(), nodes);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Reporting
    // ────────────────────────────────────────────────────────────────────────

    private static void report(String label, Graph terminal, Graph expected) {
        System.out.println("--- " + label + " terminal PAG ---");
        System.out.println(terminal);
        boolean same = identical(terminal, expected);
        System.out.println(label + " == G* ?  " + same);
        if (!same) {
            for (Edge e : terminal.getEdges()) {
                Node a = expected.getNode(e.getNode1().getName());
                Node b = expected.getNode(e.getNode2().getName());
                if (a == null || b == null || !expected.isAdjacentTo(a, b)) {
                    System.out.println("  EXTRA in terminal : " + e);
                }
            }
            for (Edge e : expected.getEdges()) {
                Node a = terminal.getNode(e.getNode1().getName());
                Node b = terminal.getNode(e.getNode2().getName());
                if (a == null || b == null || !terminal.isAdjacentTo(a, b)) {
                    System.out.println("  MISSING from terminal : " + e);
                } else {
                    Edge te = terminal.getEdge(a, b);
                    if (!key(e).equals(key(te))) {
                        System.out.println("  MARKS differ : G* " + e + "  vs terminal " + te);
                    }
                }
            }
        }
        System.out.println();
    }

    /** Every conditioning set the oracle accepts for a pair, smallest first. */
    private static void reportSeparators(MsepTest oracle, List<Node> nodes, String a, String b)
            throws InterruptedException {
        Node x = node(nodes, a), y = node(nodes, b);
        List<Node> others = new ArrayList<>(nodes);
        others.remove(x);
        others.remove(y);
        List<String> seps = new ArrayList<>();
        int n = others.size();
        for (int mask = 0; mask < (1 << n); mask++) {
            Set<Node> Z = new LinkedHashSet<>();
            for (int i = 0; i < n; i++) if ((mask & (1 << i)) != 0) Z.add(others.get(i));
            if (oracle.checkIndependence(x, y, Z).isIndependent()) seps.add(names(Z));
        }
        seps.sort(Comparator.comparingInt(String::length));
        System.out.println("  " + a + " _||_ " + b + " given: " + (seps.isEmpty() ? "(never)" : seps));
    }

    private static String names(Collection<Node> ns) {
        List<String> s = new ArrayList<>();
        for (Node n : ns) s.add(n.getName());
        Collections.sort(s);
        return s.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Canonical keys (order-independent graph comparison)
    // ────────────────────────────────────────────────────────────────────────

    private static boolean identical(Graph a, Graph b) {
        return key(a).equals(key(b));
    }

    private static String key(Graph g) {
        List<String> toks = new ArrayList<>();
        for (Edge e : g.getEdges()) toks.add(key(e));
        Collections.sort(toks);
        return String.join("|", toks);
    }

    /** Endpoint-aware, orientation-normalized token for one edge. */
    private static String key(Edge e) {
        String u = e.getNode1().getName(), v = e.getNode2().getName();
        Endpoint eu = e.getEndpoint1(), ev = e.getEndpoint2();
        if (u.compareTo(v) <= 0) return u + mark(eu) + "-" + mark(ev) + v;
        return v + mark(ev) + "-" + mark(eu) + u;
    }

    private static String mark(Endpoint e) {
        if (e == Endpoint.TAIL) return "-";
        if (e == Endpoint.ARROW) return ">";
        if (e == Endpoint.CIRCLE) return "o";
        return "?";
    }
}