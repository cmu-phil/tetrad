/// ////////////////////////////////////////////////////////////////////////////
// PhantomCounterexampleHarness.java                                          //
//                                                                            //
// Generate-and-test search for a counterexample to the phantom / genuineness //
// conjecture (RB paper, Reachability section, Conjecture 1).                  //
//                                                                            //
// Target object: a *legal* PAG that is *non-genuine* -- a discriminating path //
// with a leg/chord absent from the true PAG that nonetheless passes           //
// PagLegalityCheck. Reached by deleting one spurious edge from a genuine legal //
// Markov state and reorienting from scratch.                                   //
//                                                                            //
// This variant adds:                                                          //
//   (1) a discriminating-path CENSUS, so a null result can be judged;         //
//   (2) a NEAR-MISS DUMP of every legal-revert case with the legality reason; //
//   (3) an optional I-MAP check (CHECK_IMAP) using zhangMagFromPag;           //
//   (4) a PHANTOM-SPINE CENSUS over ALL phantom DDPs in each non-genuine H1   //
//       (not just the first one found), reporting the max collider-path       //
//       length over phantom DDPs vs. over all DDPs, plus a length histogram.  //
//       This resolves the first-hit ambiguity (firstPhantom returned at the   //
//       first match, which could mask a longer phantom) and guards vacuity:   //
//       phantom-max==1 is only meaningful if all-DDP-max >= 2.                //
//                                                                            //
// The classification (genuine / non-genuine / legal) is UNCHANGED: H1 is      //
// non-genuine iff >=1 phantom DDP exists, exactly as before. Only the         //
// reporting and census are enriched.                                          //
//                                                                            //
// REACHABILITY CAVEAT unchanged: H0 is constructed, not proven FCIT-reachable. //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.SublistGenerator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Searches for a legal-but-non-genuine PAG reachable by a single spurious-edge
 * deletion from a genuine legal Markov PAG; reports a discriminating-path census
 * (now over all phantom DDPs) and dumps near-misses for inspection.
 *
 * @author josephramsey (harness scaffolding by Claude)
 */
public final class PhantomCounterexampleHarness {

    /**
     * Constructor.
     */
    public PhantomCounterexampleHarness() {}

    // ── Configuration (args[0] = numModels, args[1] = dump file path) ────────
    private static int    NUM_MODELS         = 2000;
    private static final int    NUM_NODES    = 12;
    private static final int    NUM_LATENT   = 4;
    private static final int    NUM_EDGES    = 18;
    private static final int    SPURIOUS_PER_STATE = 4;
    private static final int    STATES_PER_MODEL   = 5;
    private static final int    MAX_COND     = 3;
    private static final int    MAX_LEN      = -1;
    private static final int    DEPTH        = -1;
    private static final int    RECURSIVE_DEPTH = -1;
    private static final long   TIMEOUT      = -1L;
    private static final boolean EXCLUDE_SELECTION_BIAS = false;

    /** Off by default: the I-map probe brute-forces m-sep comparison and is costly. */
    private static final boolean CHECK_IMAP  = false;

    /** Only DUMP near-misses whose deepest phantom has collider-length >= this.
     *  Counterexamples (legal non-genuine) are ALWAYS dumped regardless of length.
     *  Set 1 to dump everything; 2+ focuses the dump on deep-spine phantoms, which
     *  are where a legal-non-genuine counterexample would most plausibly hide.
     *  Counting/census is unaffected -- this only gates what gets written out. */
    private static final int MIN_REPORT_COLLIDER_LEN = 2;


    /**
     * Main
     * @param args args
     * @throws InterruptedException If any
     */
    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0) NUM_MODELS = Integer.parseInt(args[0]);
        String dumpPath = (args.length > 1) ? args[1] : "phantom_near_misses.log";

        long gated = 0, positives = 0, illegalNonGenuine = 0, counterexamples = 0, imapViolations = 0;

        // Census accumulators (collider-path length = number of colliders; minimal DDP == 1).
        long gatedWithDdp = 0, gatedDdpTotal = 0, gatedMaxColliderAll = 0;
        long h1States = 0, h1WithDdp = 0, h1DdpTotal = 0, h1MaxColliderAll = 0;
        long h1MaxColliderPhantom = 0, phantomDdpTotal = 0;
        TreeMap<Integer, Long> phantomLenHist = new TreeMap<>();

        // Legal-vs-illegal split of non-genuine H1, keyed by its deepest phantom's
        // collider-length. The headline question: does legal% rise with length?
        TreeMap<Integer, Long> nonGenIllegalByLen = new TreeMap<>();
        TreeMap<Integer, Long> nonGenLegalByLen   = new TreeMap<>();

        // Echo config so a stale build / measured-vs-total confusion is obvious.
        System.err.printf("CONFIG: nodes=%d latent=%d measured=%d edges=%d spurious=%d states=%d "
                        + "models=%d minReportColliderLen=%d%n",
                NUM_NODES, NUM_LATENT, NUM_NODES - NUM_LATENT, NUM_EDGES,
                SPURIOUS_PER_STATE, STATES_PER_MODEL, NUM_MODELS, MIN_REPORT_COLLIDER_LEN);

        PrintWriter dump = openDump(dumpPath);

        int OFFSET = 100000;

        try {
            for (int seed = OFFSET; seed <= NUM_MODELS + OFFSET; seed++) {
                try {
                    RandomUtil.getInstance().setSeed(seed);

                    Graph dag = RandomGraph.randomGraph(
                            NUM_NODES, 0, NUM_EDGES, 100, 100, 100, false);
                    List<Node> allNodes = dag.getNodes();
                    for (int i = NUM_NODES - NUM_LATENT; i < NUM_NODES; i++) {
                        allNodes.get(i).setNodeType(NodeType.LATENT);
                    }

                    Knowledge knowledge = new Knowledge();
                    Graph truePag = GraphTransforms.dagToPag(
                            dag, knowledge, EXCLUDE_SELECTION_BIAS, RECURSIVE_DEPTH);
                    IndependenceTest oracle = new MsepTest(dag);

                    List<Node> obs = truePag.getNodes();
                    Set<Triple> initialColliders = noteInitialColliders(obs, truePag);

                    List<int[]> nonAdj = nonAdjacentPairs(truePag, obs);
                    if (nonAdj.size() < SPURIOUS_PER_STATE) continue;

                    for (int attempt = 0; attempt < STATES_PER_MODEL; attempt++) {
                        Collections.shuffle(nonAdj, new Random(seed * 1000L + attempt));

                        Graph h0 = new EdgeListGraph(truePag);
                        SepsetMap sepsets = new SepsetMap();
                        List<Edge> spurious = new ArrayList<>();

                        for (int[] p : nonAdj) {
                            if (spurious.size() >= SPURIOUS_PER_STATE) break;
                            Node a = obs.get(p[0]);
                            Node b = obs.get(p[1]);
                            Set<Node> sep = oracleSepset(oracle, a, b, obs, MAX_COND);
                            if (sep == null) continue;
                            h0.addEdge(new Edge(a, b, Endpoint.CIRCLE, Endpoint.CIRCLE));
                            sepsets.set(a, b, sep);
                            spurious.add(new Edge(a, b, Endpoint.CIRCLE, Endpoint.CIRCLE));
                        }
                        if (spurious.size() < SPURIOUS_PER_STATE) continue;

                        reorient(h0, oracle, sepsets, knowledge, initialColliders, EXCLUDE_SELECTION_BIAS);

                        // GATE: genuine legal H0 only.
                        if (!PagLegalityCheck.isLegalPag(h0, new HashSet<>()).isLegalPag()) continue;
                        Set<DiscriminatingPath> ddp0 = FciOrient.listDiscriminatingPaths(h0, MAX_LEN, true);
                        if (firstPhantom(ddp0, truePag) != null) continue;

                        gated++;
                        if (!ddp0.isEmpty()) {
                            gatedWithDdp++;
                            gatedDdpTotal += ddp0.size();
                            gatedMaxColliderAll = Math.max(gatedMaxColliderAll, maxColliderLen(ddp0));
                        }

                        // Delete one spurious edge, reorient, classify H1.
                        for (Edge e : spurious) {
                            Node x = e.getNode1();
                            Node y = e.getNode2();

                            Graph h1 = new EdgeListGraph(h0);
                            Edge present = h1.getEdge(x, y);
                            if (present == null) continue;
                            h1.removeEdge(present);

                            reorient(h1, oracle, sepsets, knowledge, initialColliders, EXCLUDE_SELECTION_BIAS);

                            h1States++;
                            Set<DiscriminatingPath> ddp1 = FciOrient.listDiscriminatingPaths(h1, MAX_LEN, true);
                            if (!ddp1.isEmpty()) {
                                h1WithDdp++;
                                h1DdpTotal += ddp1.size();
                                h1MaxColliderAll = Math.max(h1MaxColliderAll, maxColliderLen(ddp1));
                            }

                            // ALL phantom DDPs, not just the first.
                            List<DiscriminatingPath> phantoms = allPhantoms(ddp1, truePag);
                            PagLegalityCheck.LegalPagRet ret =
                                    PagLegalityCheck.isLegalPag(h1, new HashSet<>());
                            boolean legal = ret.isLegalPag();

                            if (phantoms.isEmpty()) {
                                positives++;
                                if (CHECK_IMAP && legal && isImapViolation(h1, oracle, obs, MAX_COND)) {
                                    imapViolations++;
                                    dumpCase(dump, "LEGAL GENUINE but NON-I-MAP (condition iii violated)",
                                            seed, attempt, spurious, e, null, "(i-map probe)", h1, 0, 0);
                                }
                                continue;
                            }

                            // Phantom-spine bookkeeping over EVERY phantom DDP.
                            phantomDdpTotal += phantoms.size();
                            for (DiscriminatingPath dd : phantoms) {
                                phantomLenHist.merge(dd.getColliderPath().size(), 1L, Long::sum);
                            }
                            int maxLen = maxColliderLen(phantoms);
                            h1MaxColliderPhantom = Math.max(h1MaxColliderPhantom, maxLen);

                            // Report the LONGEST phantom, not an arbitrary first one.
                            DiscriminatingPath worst = phantoms.get(0);
                            for (DiscriminatingPath dd : phantoms) {
                                if (dd.getColliderPath().size() > worst.getColliderPath().size()) worst = dd;
                            }

                            if (!legal) {
                                illegalNonGenuine++;
                                nonGenIllegalByLen.merge(maxLen, 1L, Long::sum);
                                if (maxLen >= MIN_REPORT_COLLIDER_LEN) {
                                    dumpCase(dump, "NEAR-MISS: non-genuine, illegal (caught by revert)",
                                            seed, attempt, spurious, e, worst, ret.getReason(), h1,
                                            phantoms.size(), maxLen);
                                }
                            } else {
                                counterexamples++;
                                nonGenLegalByLen.merge(maxLen, 1L, Long::sum);
                                // Counterexamples are always dumped, whatever the length.
                                dumpCase(dump, "***** COUNTEREXAMPLE: legal AND non-genuine *****",
                                        seed, attempt, spurious, e, worst, "(legal)", h1,
                                        phantoms.size(), maxLen);
                                System.out.println("COUNTEREXAMPLE at seed " + seed + " attempt " + attempt
                                        + " deleting " + e + " -> " + worst
                                        + " (phantomDDPs=" + phantoms.size() + ", maxColliderLen=" + maxLen + ")");
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("seed " + seed + " skipped: " + ex.getMessage());
                }

                if (seed % 200 == 0) {
                    System.err.printf("…%d models | gated=%d (w/DDP=%d) pos=%d illegalNG=%d COUNTEREX=%d "
                                    + "phantomMaxCollider=%d imapV=%d%n",
                            seed, gated, gatedWithDdp, positives, illegalNonGenuine, counterexamples,
                            h1MaxColliderPhantom, imapViolations);
                    dump.flush();
                }
            }
        } finally {
            dump.flush();
            dump.close();
        }

        System.out.println("\n==== SUMMARY ====");
        System.out.printf("measured / latent / total   : %d / %d / %d%n",
                NUM_NODES - NUM_LATENT, NUM_LATENT, NUM_NODES);
        System.out.printf("models scanned             : %d%n", NUM_MODELS);
        System.out.printf("genuine legal H0 gated      : %d%n", gated);
        System.out.printf("H1 genuine (conj. holds)    : %d%n", positives);
        System.out.printf("H1 non-genuine, illegal     : %d  (caught by legality revert)%n", illegalNonGenuine);
        System.out.printf("H1 non-genuine, LEGAL       : %d  <-- CONJECTURE 1 COUNTEREXAMPLES%n", counterexamples);
        if (CHECK_IMAP) {
            System.out.printf("H1 genuine, LEGAL, non-I-map: %d  <-- intermediate-I-map violations%n", imapViolations);
        }

        System.out.println("\n==== DISCRIMINATING-PATH CENSUS ====");
        System.out.println("(a null counterexample count is only meaningful if these are non-trivial)");
        System.out.printf("gated H0 with >=1 DDP        : %d / %d  (%.1f%%)%n",
                gatedWithDdp, gated, pct(gatedWithDdp, gated));
        System.out.printf("  mean DDPs per such H0      : %.2f%n", ratio(gatedDdpTotal, gatedWithDdp));
        System.out.printf("H1 states with >=1 DDP       : %d / %d  (%.1f%%)%n",
                h1WithDdp, h1States, pct(h1WithDdp, h1States));
        System.out.printf("  mean DDPs per such H1      : %.2f%n", ratio(h1DdpTotal, h1WithDdp));

        System.out.println("\n==== PHANTOM-SPINE CENSUS (resolves the first-hit ambiguity) ====");
        System.out.printf("max collider-path length, ALL DDPs in gated H0 : %d%n", gatedMaxColliderAll);
        System.out.printf("max collider-path length, ALL DDPs in H1       : %d%n", h1MaxColliderAll);
        System.out.printf("max collider-path length, PHANTOM DDPs in H1   : %d%n", h1MaxColliderPhantom);
        System.out.printf("total phantom DDPs counted                      : %d%n", phantomDdpTotal);
        System.out.println("phantom collider-path length histogram:");
        if (phantomLenHist.isEmpty()) {
            System.out.println("  (no phantom DDPs observed)");
        } else {
            for (Map.Entry<Integer, Long> en : phantomLenHist.entrySet()) {
                System.out.printf("  length %d : %d%n", en.getKey(), en.getValue());
            }
        }
        System.out.println();
        System.out.println("Reading the phantom census:");
        System.out.println("  ALL-DDP max >= 2 AND PHANTOM max == 1  -> phantoms are genuinely minimal (lemma).");
        System.out.println("  ALL-DDP max == 1 too                   -> graphs too small/sparse to host long");
        System.out.println("                                            DDPs at all; phantom result is vacuous,");
        System.out.println("                                            raise NUM_NODES/NUM_EDGES before concluding.");
        System.out.println("  PHANTOM max >= 2                       -> deep-spine phantom exists; inspect the dump.");

        System.out.println("\n==== LEGAL vs ILLEGAL BY PHANTOM SPINE LENGTH ====");
        System.out.println("(non-genuine H1 bucketed by its deepest phantom's collider-length;");
        System.out.println(" the question is whether legal%% -- the conjecture-counterexample rate -- rises with length)");
        System.out.printf("  %-16s %12s %12s %10s%n", "collider-length", "illegal", "LEGAL(cex)", "legal%");
        TreeSet<Integer> lens = new TreeSet<>();
        lens.addAll(nonGenIllegalByLen.keySet());
        lens.addAll(nonGenLegalByLen.keySet());
        if (lens.isEmpty()) {
            System.out.println("  (no non-genuine H1 observed)");
        } else {
            for (int L : lens) {
                long ill = nonGenIllegalByLen.getOrDefault(L, 0L);
                long leg = nonGenLegalByLen.getOrDefault(L, 0L);
                long tot = ill + leg;
                System.out.printf("  %-16d %12d %12d %9.2f%%%n",
                        L, ill, leg, tot == 0 ? 0.0 : 100.0 * leg / tot);
            }
        }
        System.out.println("  (all-zero legal column at every length = conjecture holds across observed spines;");
        System.out.println("   any nonzero legal entry IS a Conjecture 1 counterexample -- see the dump.)");
        System.out.println("\nnear-misses / counterexamples written to: " + dumpPath);
    }

    // ── Dump helpers ─────────────────────────────────────────────────────────
    private static PrintWriter openDump(String path) {
        try {
            return new PrintWriter(new BufferedWriter(new FileWriter(path, false)));
        } catch (IOException io) {
            System.err.println("Could not open dump file " + path + "; falling back to stderr.");
            return new PrintWriter(System.err, true);
        }
    }

    private static void dumpCase(PrintWriter w, String tag, int seed, int attempt, List<Edge> spurious,
                                 Edge deleted, DiscriminatingPath phantom, String reason, Graph h1,
                                 int phantomCount, int maxColliderLen) {
        w.println("==== " + tag + " ====");
        w.println("  seed/attempt   : " + seed + " / " + attempt);
        w.println("  spurious added : " + spurious);
        w.println("  deleted edge   : " + deleted);
        if (phantom != null) {
            w.println("  phantom DDPs   : " + phantomCount
                    + "  (max collider-path length " + maxColliderLen + ")");
            w.println("  worst phantom  : " + phantom);
        }
        w.println("  legality reason: " + reason);
        w.println("  H1:");
        w.println(h1);
        w.println();
        w.flush();
    }

    // ── Genuineness predicate on a single DDP: true iff some spine leg or chord
    //    is absent from truePag. ───────────────────────────────────────────────
    private static boolean isPhantom(DiscriminatingPath dd, Graph truePag) {
        List<Node> spine = new ArrayList<>(dd.getColliderPath());
        spine.addFirst(dd.getX());
        spine.addLast(dd.getY());
        for (int i = 0; i < spine.size() - 1; i++) {
            if (!truePag.isAdjacentTo(spine.get(i), spine.get(i + 1))) return true;
        }
        Node y = dd.getY();
        for (Node v : dd.getColliderPath()) {
            if (!truePag.isAdjacentTo(v, y)) return true;
        }
        return false;
    }

    /** First phantom DDP (cheap existence check for the H0 gate). */
    private static DiscriminatingPath firstPhantom(Set<DiscriminatingPath> ddps, Graph truePag) {
        for (DiscriminatingPath dd : ddps) {
            if (isPhantom(dd, truePag)) return dd;
        }
        return null;
    }

    /** ALL phantom DDPs in a graph (for the phantom-spine census on H1). */
    private static List<DiscriminatingPath> allPhantoms(Set<DiscriminatingPath> ddps, Graph truePag) {
        List<DiscriminatingPath> out = new ArrayList<>();
        for (DiscriminatingPath dd : ddps) {
            if (isPhantom(dd, truePag)) out.add(dd);
        }
        return out;
    }

    /** Max collider-path length (number of colliders) over a set of DDPs. */
    private static int maxColliderLen(Collection<DiscriminatingPath> ddps) {
        int m = 0;
        for (DiscriminatingPath dd : ddps) m = Math.max(m, dd.getColliderPath().size());
        return m;
    }

    // ── Optional I-map probe: does the legal genuine H1 entail an independence
    //    false in M*? Uses a representative MAG of H1's class. ─────────────────
    private static boolean isImapViolation(Graph h1Legal, IndependenceTest oracle, List<Node> obs, int maxCond)
            throws InterruptedException {
        Graph mag = GraphTransforms.zhangMagFromPag(h1Legal);
        IndependenceTest hTest = new MsepTest(mag);

        for (int i = 0; i < obs.size(); i++) {
            for (int j = i + 1; j < obs.size(); j++) {
                Node a = obs.get(i);
                Node b = obs.get(j);
                List<Node> pool = new ArrayList<>(obs);
                pool.remove(a);
                pool.remove(b);
                int cap = Math.min(maxCond, pool.size());
                for (int k = 0; k <= cap; k++) {
                    SublistGenerator gen = new SublistGenerator(pool.size(), k);
                    int[] c;
                    while ((c = gen.next()) != null) {
                        if (c.length != k) continue;
                        Set<Node> S = GraphUtils.asSet(c, pool);
                        if (hTest.checkIndependence(a, b, S).isIndependent()
                                && !oracle.checkIndependence(a, b, S).isIndependent()) {
                            return true; // H entails an independence false in M*
                        }
                    }
                }
            }
        }
        return false;
    }

    // ── Reorientation: redoGfciOrientation recipe, fresh FciOrient on current sepsets. ─
    private static void reorient(Graph h, IndependenceTest oracle, SepsetMap sepsets, Knowledge knowledge,
                                 Set<Triple> initialColliders, boolean excludeSelectionBias)
            throws InterruptedException {
        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(oracle, TIMEOUT);
        strategy.setSepsetMap(sepsets);
        strategy.setVerbose(false);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);
        strategy.setDepth(DEPTH);

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setVerbose(false);
        fciOrient.setParallel(false);
        fciOrient.setCompleteRuleSetUsed(true);
        fciOrient.setRecursiveDepth(RECURSIVE_DEPTH);
        fciOrient.setMaxDiscriminatingPathLength(MAX_LEN);
        fciOrient.setKnowledge(knowledge);

        GraphUtils.reorientWithCircles(h, false);
        GraphUtils.recallInitialColliders(h, initialColliders, knowledge);
        adjustForExtraSepsets(sepsets, h);
        fciOrient.finalOrientation(h, excludeSelectionBias);
    }

    private static void adjustForExtraSepsets(SepsetMap sepsets, Graph pag) {
        for (Set<Node> edge : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(edge);
            Node x = arr.get(0);
            Node y = arr.get(1);
            if (pag.isAdjacentTo(x, y)) continue;

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));

            for (Node node : common) {
                if (!sepsets.get(x, y).contains(node)) {
                    if (!pag.isDefCollider(x, node, y)) {
                        pag.setEndpoint(x, node, Endpoint.ARROW);
                        pag.setEndpoint(y, node, Endpoint.ARROW);
                    }
                }
            }
        }
    }

    private static Set<Triple> noteInitialColliders(List<Node> best, Graph graph) {
        Set<Triple> initialColliders = new HashSet<>();
        for (Node b : best) {
            var adj = graph.getAdjacentNodes(b);
            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node x = adj.get(i);
                    Node y = adj.get(j);
                    if (graph.isDefCollider(x, b, y) && !graph.isAdjacentTo(x, y)) {
                        initialColliders.add(new Triple(x, b, y));
                    }
                }
            }
        }
        return initialColliders;
    }

    private static List<int[]> nonAdjacentPairs(Graph truePag, List<Node> obs) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < obs.size(); i++) {
            for (int j = i + 1; j < obs.size(); j++) {
                if (!truePag.isAdjacentTo(obs.get(i), obs.get(j))) out.add(new int[]{i, j});
            }
        }
        return out;
    }

    private static Set<Node> oracleSepset(IndependenceTest oracle, Node a, Node b,
                                          List<Node> obs, int maxCond) throws InterruptedException {
        List<Node> pool = new ArrayList<>(obs);
        pool.remove(a);
        pool.remove(b);
        int cap = Math.min(maxCond, pool.size());
        for (int k = 0; k <= cap; k++) {
            SublistGenerator gen = new SublistGenerator(pool.size(), k);
            int[] choice;
            while ((choice = gen.next()) != null) {
                if (choice.length != k) continue;
                Set<Node> S = GraphUtils.asSet(choice, pool);
                if (oracle.checkIndependence(a, b, S).isIndependent()) return S;
            }
        }
        return null;
    }

    private static double pct(long n, long d) { return d == 0 ? 0.0 : 100.0 * n / d; }
    private static double ratio(long n, long d) { return d == 0 ? 0.0 : (double) n / d; }
}
