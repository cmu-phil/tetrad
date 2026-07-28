/// ////////////////////////////////////////////////////////////////////////////
// PhantomKernelEnumerator8.java  (parallel, deduplicated, checkpointed)       //
//                                                                             //
// PKE8 answers the complement of PKE7's question.  PKE7 audited FCIT-SL's     //
// MOVE SET over a PROXY population (cold-reoriented truePag+spurious Markov    //
// starts).  PKE8 runs the ACTUAL shipping FcitSl end to end from a GRaSP DAG  //
// start on the m-separation ORACLE of each true MAG, and checks the single    //
// thing that matters for correctness:                                         //
//                                                                             //
//     terminal PAG returned by FcitSl.search()  ==  G* (the true PAG)?        //
//                                                                             //
// This closes the population-proxy gap from the other end: PKE7 says "the     //
// move set never fails on reachable-shaped Markov states"; PKE8 says "the     //
// algorithm, driven by its own GRaSP start and its own sepset discovery,      //
// actually lands on G* from scratch."  Clean on both is the empirical core.   //
//                                                                             //
// SCOPE: identical enumeration/dedup to PKE6/PKE7 -- all 2^21 DAGs over a     //
// fixed order x all C(7,2) latent placements, deduplicated by canonical true  //
// MAG over the 5 observed variables to the 2,691 distinct classes.  For each  //
// distinct class we run FcitSl ONCE, oracle-driven, and compare.              //
//                                                                             //
// ORACLE DRIVE: FcitSl is constructed with an MsepTest on the true MAG as     //
// BOTH test and score (via a GraphScore on the same MAG), exactly the Oracle  //
// path FcitSl already supports (startWith flips to GRASP when the test is an  //
// MsepTest -- see FcitSl's constructor).  excludeSelectionBias=true.          //
//                                                                             //
// COMPARISON: terminal PAG and G* are compared as GRAPHS, up to node-name     //
// identity (both are over the same 5 canonical observed nodes V1..V5).  The   //
// primary verdict is exact edge+endpoint equality.  For any mismatch we also  //
// record a MODEL-LEVEL check (do the two PAGs' MAGs induce the same           //
// m-separation model over the observed variables?) so a "different PAG, same  //
// Markov class" case -- which would be an orientation/printing discrepancy    //
// rather than a discovery error -- is distinguished from a genuine structural //
// miss in the report.                                                         //
//                                                                             //
// VERDICT BUCKETS per class:                                                  //
//   EXACT              : terminal PAG == G* (edge+endpoint identical).        //
//   EQUIVALENT_NOT_EXACT: terminal != G* but Markov-equivalent (same model)   //
//                         -- flagged, dumped: usually a spurious-edge-free     //
//                         orientation nit, still worth eyeballing.            //
//   SKELETON_MATCH_ORIENT_DIFF : same adjacencies, different marks, NOT       //
//                         Markov-equivalent -- an orientation error.          //
//   SKELETON_DIFF      : adjacencies differ (extra or missing edges) -- the   //
//                         serious miss; extra edges = a spurious edge FcitSl   //
//                         could not delete (the PKE7 "stall" made real), or a //
//                         missing edge = an over-deletion.                    //
//   ERROR              : FcitSl threw or timed out.                           //
// Only EXACT is a pass; every other bucket is a VIOLATION, logged in full.    //
//                                                                             //
// Enumeration, dedup, canonicalization, checkpointing, and streamed capped    //
// logs are IDENTICAL to PKE6/PKE7, so `distinctClasses` must reproduce 2,691. //
//                                                                             //
// @author josephramsey (harness scaffolding by Claude)                        //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.independence.MSeparationTest;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FcitSl;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.SublistGenerator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

/**
 * PKE8: run the real FcitSl from GRaSP starts, oracle-driven, and report every class
 * whose terminal PAG is not exactly G*.
 */
public final class PhantomKernelEnumerator8 {

    /**
     * Default constructor.
     */
    public PhantomKernelEnumerator8() {
    }

    // ────────────────────────────────────────────────────────────────────────
    // CONFIGURATION (all hard-coded; edit and re-run)
    // ────────────────────────────────────────────────────────────────────────

    /** Total nodes in the enumerated DAGs. */
    static final int N = 7;
    /** Number of latent nodes per placement. */
    static final int NUM_LATENT = 2;

    // ── FcitSl configuration (match the shipping defaults you want to certify) ──
    /** Step-Lemma-pure mode: no out-of-class escape.  Set false to certify the
     *  full shipping algorithm including the fork-flip fallback. */
    private static final boolean ALLOW_CLASS_ESCAPE = false;
    /** FcitSl commit-gate battery bound. */
    private static final int BATTERY_Z_MAX = 5;
    /** FcitSl fork-flip bound (only relevant when ALLOW_CLASS_ESCAPE). */
    private static final int MAX_FORK_FLIPS = 2;
    /** Per-edge FcitSl timeout, ms; -1 = unlimited.  A positive value turns a
     *  hang into an ERROR verdict instead of stalling the whole run. */
    private static final long FCIT_TIMEOUT_MS = -1L;
    /** Depth / recursive-depth passed to FcitSl (-1 = unlimited). */
    private static final int DEPTH = -1;
    private static final int RECURSIVE_DEPTH = -1;
    private static final boolean EXCLUDE_SELECTION_BIAS = false;

    /** Canonicalize dedup keys over all 120 permutations of the observed nodes. */
    private static final boolean CANONICALIZE_PERMS = true;

    /** Visit blocks in a shuffled order rather than 0..numBlocks-1, so that stopping
     *  early yields a representative sample instead of a corner of mask space. */
    private static final boolean SHUFFLE_BLOCKS = true;
    /** Seed for the visitation order.  Fixed so a run is reproducible. */
    private static final long BLOCK_ORDER_SEED = 20260723L;

    // Log files.
    /** Every class whose terminal PAG != G* (any non-EXACT bucket). */
    private static final String VIOLATION_LOG_PATH = "pke8_violations.log";
    /** Completed block ids for resume. Delete to restart from scratch. */
    private static final String CHECKPOINT_PATH = "pke8_checkpoint.txt";

    /** Violations are the point of the run; cap generously. */
    private static final int VIOLATION_LOG_MAX = 3000;

    /** Masks per checkpoint block: 2^12 = 4096 -> 512 blocks over 2^21 masks. */
    static final long BLOCK_SIZE = 1L << 12;

    // ────────────────────────────────────────────────────────────────────────
    // DERIVED CONSTANTS AND SHARED STATE
    // ────────────────────────────────────────────────────────────────────────

    private static final int OBS = N - NUM_LATENT;
    private static final int P = N * (N - 1) / 2;
    static final long TOTAL_DAGS = 1L << P;
    private static final int[][] PAIR = buildPairs();
    private static final List<int[]> PERMS = buildPerms();

    static final String CONFIG_LINE = String.format(
            "# PKE8 config: N=%d latent=%d escape=%b zMax=%d forkFlips=%d timeoutMs=%d "
                    + "canonPerms=%b blockSize=%d",
            N, NUM_LATENT, ALLOW_CLASS_ESCAPE, BATTERY_Z_MAX, MAX_FORK_FLIPS, FCIT_TIMEOUT_MS,
            CANONICALIZE_PERMS, BLOCK_SIZE);

    private static final ConcurrentHashMap<String, Boolean> SEEN = new ConcurrentHashMap<>();
    private static final AtomicBoolean STOP = new AtomicBoolean(false);
    private static final AtomicLong ERR_PRINTS = new AtomicLong();

    private static StreamLog violationLog;

    // ────────────────────────────────────────────────────────────────────────
    // MAIN
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Main method.
     * @param args args
     * @throws IOException if any.
     */
    public static void main(String[] args) throws IOException {
        long numBlocks = (TOTAL_DAGS + BLOCK_SIZE - 1) / BLOCK_SIZE;
        Set<Long> doneBlocks = loadCheckpoint();

        System.err.println(CONFIG_LINE);
        System.err.printf("observed=%d | dags=2^%d=%d | latentPlacements=C(%d,%d)=%d%n",
                OBS, P, TOTAL_DAGS, N, NUM_LATENT, choose(N, NUM_LATENT));
        System.err.printf("blocks=%d (size %d), %d already complete | threads~%d%n",
                numBlocks, BLOCK_SIZE, doneBlocks.size(), Runtime.getRuntime().availableProcessors());

        String header = CONFIG_LINE + "\n# run started " + new Date()
                + " | resuming with " + doneBlocks.size() + "/" + numBlocks + " blocks complete"
                + "\n# VIOLATIONS: every distinct class whose terminal FcitSl PAG (GRaSP start,"
                + "\n# oracle-driven) is NOT exactly G*.  Bucket legend:"
                + "\n#   EQUIVALENT_NOT_EXACT       -- Markov-equivalent to G* but not edge-identical"
                + "\n#   SKELETON_MATCH_ORIENT_DIFF -- same skeleton, non-equivalent orientation error"
                + "\n#   SKELETON_DIFF              -- adjacencies differ (extra = undeletable spurious,"
                + "\n#                                 missing = over-deletion)"
                + "\n#   ERROR                      -- FcitSl threw or timed out";
        violationLog = new StreamLog(VIOLATION_LOG_PATH, VIOLATION_LOG_MAX, header);

        Result total = new Result();
        long t0 = System.currentTimeMillis();
        long blocksThisRun = 0;

        long[] order = blockOrder(numBlocks);

        for (long idx = 0; idx < numBlocks; idx++) {
            long b = order[(int) idx];
            if (STOP.get()) break;
            if (doneBlocks.contains(b)) continue;

            long lo = b * BLOCK_SIZE;
            long hi = Math.min(TOTAL_DAGS, lo + BLOCK_SIZE);

            Result blockRes = LongStream.range(lo, hi)
                    .parallel()
                    .collect(Result::new, PhantomKernelEnumerator8::accumulate, Result::merge);
            total.add(blockRes);
            blocksThisRun++;

            if (STOP.get()) break;
            appendCheckpoint(b);

//            System.err.printf("block %d done (%d this run) | models=%d distinct=%d "
//                            + "| EXACT=%d EQUIV=%d ORIENT=%d SKEL=%d ERR=%d | keys=%d | %.1f min%n",
//                    b, blocksThisRun, total.modelsScanned, total.distinctClasses,
//                    total.exact, total.equivNotExact, total.skeletonMatchOrientDiff,
//                    total.skeletonDiff, total.error, SEEN.size(),
//                    (System.currentTimeMillis() - t0) / 60000.0);

            System.err.printf("block %d (%d/%d visited) | new=%d | models=%d distinct=%d "
                            + "| EXACT=%d EQUIV=%d ORIENT=%d SKEL=%d ERR=%d | keys=%d | %.1f min%n",
                    b, blocksThisRun, numBlocks, blockRes.distinctClasses, total.modelsScanned,
                    total.distinctClasses, total.exact, total.equivNotExact,
                    total.skeletonMatchOrientDiff, total.skeletonDiff, total.error, SEEN.size(),
                    (System.currentTimeMillis() - t0) / 60000.0);
        }

        String summary = summarize(total, blocksThisRun, numBlocks, doneBlocks.size(),
                System.currentTimeMillis() - t0);
        System.out.println(summary);
        violationLog.summary("\n" + summary);
        violationLog.close();
    }

    private static String summarize(Result t, long blocksThisRun, long numBlocks, long resumedBlocks,
                                    long elapsedMs) {
        long violations = t.equivNotExact + t.skeletonMatchOrientDiff + t.skeletonDiff + t.error;
        StringBuilder sb = new StringBuilder();
        sb.append("==== PKE8 SUMMARY (this run only; counters are per-run) ====\n");
        sb.append(CONFIG_LINE).append('\n');
        sb.append(String.format("blocks processed this run          : %d (resumed past %d; total %d)%n",
                blocksThisRun, resumedBlocks, numBlocks));
        sb.append(String.format("stopped early (log full)           : %b%n", STOP.get()));
        sb.append(String.format("dag masks scanned                  : %d%n", t.dagsScanned));
        sb.append(String.format("models scanned (mask x latent)     : %d%n", t.modelsScanned));
        sb.append(String.format("  duplicate canonical MAGs skipped : %d%n", t.dupModels));
        sb.append(String.format("  distinct classes processed       : %d (global key map: %d)   "
                + "(anchor: 2691)%n", t.distinctClasses, SEEN.size()));
        sb.append(String.format("  models skipped on exception      : %d%n", t.skipped));
        sb.append("---- TERMINAL-PAG IDENTITY (FcitSl from GRaSP start, oracle) ----\n");
        sb.append(String.format("EXACT (terminal PAG == G*)         : %d%n", t.exact));
        sb.append(String.format("VIOLATIONS (terminal PAG != G*)    : %d%s%n", violations,
                violations == 0 ? "   *** clean: FcitSl recovers G* on every class ***" : ""));
        sb.append(String.format("  EQUIVALENT_NOT_EXACT             : %d%s%n", t.equivNotExact,
                t.equivNotExact > 0 ? "   (Markov-equivalent to G*; orientation/printing nit)" : ""));
        sb.append(String.format("  SKELETON_MATCH_ORIENT_DIFF       : %d%s%n", t.skeletonMatchOrientDiff,
                t.skeletonMatchOrientDiff > 0 ? "   *** non-equivalent orientation error ***" : ""));
        sb.append(String.format("  SKELETON_DIFF                    : %d%s%n", t.skeletonDiff,
                t.skeletonDiff > 0 ? "   *** wrong adjacencies (undeletable spurious / over-deletion) ***" : ""));
        sb.append(String.format("    of which extra edges present   : %d%n", t.skeletonExtra));
        sb.append(String.format("    of which edges missing         : %d%n", t.skeletonMissing));
        sb.append(String.format("  ERROR (threw / timed out)        : %d%s%n", t.error,
                t.error > 0 ? "   *** see violation log ***" : ""));
        sb.append("---- THE ANSWER ----\n");
        sb.append(violations == 0
                ? "FcitSl (" + (ALLOW_CLASS_ESCAPE ? "escape ON" : "Step-Lemma-pure")
                + ") recovers G* EXACTLY on all " + t.distinctClasses
                + " classes from GRaSP starts at this scope.\n"
                : "FcitSl did NOT recover G* on " + violations + " class(es) -- see " + VIOLATION_LOG_PATH + ".\n");
        sb.append(String.format("elapsed                            : %.1f min%n", elapsedMs / 60000.0));
        sb.append("==== END SUMMARY ====");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // PER-MASK ACCUMULATOR (dedup identical to PKE6/PKE7)
    // ────────────────────────────────────────────────────────────────────────

    private static void accumulate(Result r, long mask) {
        if (STOP.get()) return;
        r.dagsScanned++;

        SublistGenerator latGen = new SublistGenerator(N, NUM_LATENT);
        int[] latChoice;
        while ((latChoice = latGen.next()) != null) {
            if (latChoice.length != NUM_LATENT) continue;
            if (STOP.get()) return;
            r.modelsScanned++;
            try {
                List<Node> nodes = new ArrayList<>();
                for (int i = 0; i < N; i++) nodes.add(new GraphNode("X" + (i + 1)));
                Graph dag = new EdgeListGraph(nodes);
                for (int b = 0; b < P; b++) {
                    if ((mask & (1L << b)) != 0) {
                        dag.addDirectedEdge(nodes.get(PAIR[b][0]), nodes.get(PAIR[b][1]));
                    }
                }
                Set<Integer> latSet = new TreeSet<>();
                for (int li : latChoice) {
                    latSet.add(li);
                    nodes.get(li).setNodeType(NodeType.LATENT);
                }

                Graph trueMag = GraphTransforms.dagToMag(dag);
                if (trueMag.getNumNodes() != OBS) {
                    throw new IllegalStateException("dagToMag returned " + trueMag.getNumNodes()
                            + " nodes; expected " + OBS + " observed.");
                }
                for (Node v : trueMag.getNodes()) {
                    if (v.getNodeType() == NodeType.LATENT) {
                        throw new IllegalStateException("dagToMag output contains a latent node: " + v);
                    }
                }

                List<Node> obsSorted = new ArrayList<>(trueMag.getNodes());
                obsSorted.sort(Comparator.comparingInt(nd -> Integer.parseInt(nd.getName().substring(1))));
                Canon canon = canonicalKey(trueMag, obsSorted);
                if (SEEN.putIfAbsent(canon.key, Boolean.TRUE) != null) {
                    r.dupModels++;
                    continue;
                }
                r.distinctClasses++;

                // Fresh copy: dagToMag ran on `dag` above, and the comparison target must not
                // depend on whether that call mutated its input.
                Graph truePag = GraphTransforms.dagToPag(new EdgeListGraph(dag), new Knowledge(),
                        EXCLUDE_SELECTION_BIAS, RECURSIVE_DEPTH);

                List<Node> canonNodes = new ArrayList<>();
                for (int q = 0; q < OBS; q++) canonNodes.add(new GraphNode("V" + (q + 1)));
                Graph canonDag = relabelWithLatents(dag, obsSorted, canon.perm, canonNodes, latSet);
                Graph canonMag = relabel(trueMag, obsSorted, canon.perm, canonNodes);
                Graph canonPag = relabel(truePag, obsSorted, canon.perm, canonNodes);
                String mapping = mappingDesc(obsSorted, canon.perm, latSet);

                runFcit(r, mask, latSet, mapping, canonDag, canonMag, canonPag, canonNodes);
            } catch (Exception ex) {
                r.skipped++;
                if (ERR_PRINTS.incrementAndGet() <= 5) {
                    System.err.println("model mask=" + mask + " lat=" + Arrays.toString(latChoice)
                            + " skipped: " + ex);
                    ex.printStackTrace();
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // RUN FcitSl AND COMPARE
    // ────────────────────────────────────────────────────────────────────────

    private static void runFcit(Result r, long mask, Set<Integer> latSet, String mapping,
                                Graph canonDag, Graph canonMag, Graph canonPag, List<Node> obs) {
        // Oracle drive: MsepTest on the true MAG as both test and score basis.  FcitSl's
        // constructor flips startWith to GRASP when the test is an MsepTest, so this is the
        // Oracle GRaSP path.  A fresh graph per FcitSl (it mutates node types internally).
        Graph magForTest = new EdgeListGraph(canonMag);
        Graph magForScore = new EdgeListGraph(canonMag);
        MsepTest test = new MsepTest(magForTest);
        GraphScore scoreObj = new GraphScore(magForScore);

        Graph terminal;
        try {
            FcitSl fcit = new FcitSl(test, scoreObj);
            fcit.setKnowledge(new Knowledge());
            fcit.setExcludeSelectionBias(EXCLUDE_SELECTION_BIAS);
            fcit.setCompleteRuleSetUsed(true);
            fcit.setDepth(DEPTH);
            fcit.setRecursiveDepth(RECURSIVE_DEPTH);
            fcit.setBatteryZMax(BATTERY_Z_MAX);
            fcit.setMaxForkFlips(MAX_FORK_FLIPS);
            fcit.setAllowClassEscape(ALLOW_CLASS_ESCAPE);
            fcit.setTimeout(FCIT_TIMEOUT_MS);
            fcit.setUseClosureCoverSearch(true);
            fcit.setVerbose(false);
            terminal = fcit.search();
        } catch (Throwable ex) {
            r.error++;
            violationLog.write(violationEntry("ERROR", mask, latSet, mapping, canonDag, canonMag, canonPag,
                    null, "FcitSl threw: " + ex));
            if (ERR_PRINTS.incrementAndGet() <= 10) {
                System.err.println("FcitSl error mask=" + mask + " lat=" + latSet + ": " + ex);
            }
            return;
        }

        // Normalize terminal to the canonical observed nodes (FcitSl returns a PAG over
        // the test's variables, which are the canonMag nodes -- same names V1..V5).
        Graph term = GraphUtils.replaceNodes(terminal, obs);

        // Primary verdict: exact edge+endpoint identity with G*.
        if (graphsIdentical(term, canonPag)) {
            r.exact++;
            return;
        }

        // Non-exact: classify.  Skeleton first.
        boolean sameSkeleton = sameSkeleton(term, canonPag);
        String bucket;
        if (!sameSkeleton) {
            r.skeletonDiff++;
            int[] xd = skeletonDelta(term, canonPag);
            r.skeletonExtra += (xd[0] > 0 ? 1 : 0);
            r.skeletonMissing += (xd[1] > 0 ? 1 : 0);
            bucket = "SKELETON_DIFF";
        } else if (markovEquivalent(term, canonPag, obs)) {
            r.equivNotExact++;
            bucket = "EQUIVALENT_NOT_EXACT";
        } else {
            r.skeletonMatchOrientDiff++;
            bucket = "SKELETON_MATCH_ORIENT_DIFF";
        }

        violationLog.write(violationEntry(bucket, mask, latSet, mapping, canonDag, canonMag, canonPag,
                term, diffDetail(term, canonPag)));
    }

    // ── comparison helpers ──

    /** Exact: same edge set with identical endpoints on each (order-insensitive). */
    private static boolean graphsIdentical(Graph a, Graph b) {
        if (a.getNumNodes() != b.getNumNodes()) return false;
        if (a.getNumEdges() != b.getNumEdges()) return false;
        for (Edge e : a.getEdges()) {
            Node u = e.getNode1(), v = e.getNode2();
            Edge f = b.getEdge(bNode(b, u), bNode(b, v));
            if (f == null) return false;
            if (endpointAt(e, u) != endpointOf(f, b, u, v, true)) return false;
            if (endpointAt(e, v) != endpointOf(f, b, u, v, false)) return false;
        }
        return true;
    }

    private static Node bNode(Graph g, Node byName) {
        return g.getNode(byName.getName());
    }

    private static Endpoint endpointAt(Edge e, Node n) {
        return e.getNode1().getName().equals(n.getName()) ? e.getEndpoint1() : e.getEndpoint2();
    }

    /** Endpoint of edge f (in graph b) at the node whose NAME matches u (uSide=true) or v. */
    private static Endpoint endpointOf(Edge f, Graph b, Node u, Node v, boolean uSide) {
        Node target = uSide ? u : v;
        return f.getNode1().getName().equals(target.getName()) ? f.getEndpoint1() : f.getEndpoint2();
    }

    private static boolean sameSkeleton(Graph a, Graph b) {
        if (a.getNumEdges() != b.getNumEdges()) return false;
        for (Edge e : a.getEdges()) {
            if (!b.isAdjacentTo(bNode(b, e.getNode1()), bNode(b, e.getNode2()))) return false;
        }
        return true;
    }

    /** {extraInTerm, missingFromTerm} adjacency counts (term vs G*). */
    private static int[] skeletonDelta(Graph term, Graph star) {
        int extra = 0, missing = 0;
        for (Edge e : term.getEdges()) {
            if (!star.isAdjacentTo(star.getNode(e.getNode1().getName()),
                    star.getNode(e.getNode2().getName()))) extra++;
        }
        for (Edge e : star.getEdges()) {
            if (!term.isAdjacentTo(term.getNode(e.getNode1().getName()),
                    term.getNode(e.getNode2().getName()))) missing++;
        }
        return new int[]{extra, missing};
    }

    /** Same observed m-separation model?  Compared via each PAG's Zhang MAG. */
    private static boolean markovEquivalent(Graph a, Graph b, List<Node> obs) {
        try {
            Graph ma = GraphTransforms.zhangMagFromPag(a);
            Graph mb = GraphTransforms.zhangMagFromPag(b);
            MsepTest ta = new MsepTest(ma);
            MsepTest tb = new MsepTest(mb);
            int n = obs.size();
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    Node x = obs.get(i), y = obs.get(j);
                    List<Integer> others = new ArrayList<>();
                    for (int t = 0; t < n; t++) if (t != i && t != j) others.add(t);
                    int mm = others.size();
                    for (int z = 0; z < (1 << mm); z++) {
                        Set<Node> Z = new HashSet<>();
                        for (int q = 0; q < mm; q++) if ((z & (1 << q)) != 0) Z.add(obs.get(others.get(q)));
                        boolean ia = ta.checkIndependence(x, y, Z).isIndependent();
                        boolean ib = tb.checkIndependence(x, y, Z).isIndependent();
                        if (ia != ib) return false;
                    }
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String diffDetail(Graph term, Graph star) {
        StringBuilder sb = new StringBuilder();
        int[] d = skeletonDelta(term, star);
        sb.append("    skeleton delta: extra-in-terminal=").append(d[0])
                .append("  missing-from-terminal=").append(d[1]).append('\n');
        // Per-edge mark differences on shared adjacencies.
        for (Edge e : star.getEdges()) {
            Node u = star.getNode(e.getNode1().getName()), v = star.getNode(e.getNode2().getName());
            Node tu = term.getNode(u.getName()), tv = term.getNode(v.getName());
            if (tu == null || tv == null || !term.isAdjacentTo(tu, tv)) {
                sb.append("    G* edge ").append(e).append("  -> ABSENT in terminal\n");
                continue;
            }
            Edge te = term.getEdge(tu, tv);
            if (!edgeMarksMatch(e, te)) {
                sb.append("    G* edge ").append(e).append("  -> terminal ").append(te).append('\n');
            }
        }
        for (Edge e : term.getEdges()) {
            Node u = e.getNode1(), v = e.getNode2();
            Node su = star.getNode(u.getName()), sv = star.getNode(v.getName());
            if (su == null || sv == null || !star.isAdjacentTo(su, sv)) {
                sb.append("    terminal edge ").append(e).append("  -> EXTRA (not in G*)\n");
            }
        }
        return sb.toString();
    }

    private static boolean edgeMarksMatch(Edge a, Edge b) {
        // a from G*, b from terminal; compare endpoints by node name.
        Endpoint a1 = a.getEndpoint1(), a2 = a.getEndpoint2();
        String n1 = a.getNode1().getName(), n2 = a.getNode2().getName();
        Endpoint b1 = b.getNode1().getName().equals(n1) ? b.getEndpoint1() : b.getEndpoint2();
        Endpoint b2 = b.getNode1().getName().equals(n2) ? b.getEndpoint1() : b.getEndpoint2();
        return a1 == b1 && a2 == b2;
    }

    private static String violationEntry(String bucket, long mask, Set<Integer> latSet, String mapping,
                                         Graph dag, Graph canonMag, Graph canonPag, Graph terminal, String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== VIOLATION [").append(bucket).append("] ====\n");
        sb.append("  config            : escape=").append(ALLOW_CLASS_ESCAPE)
                .append(" zMax=").append(BATTERY_Z_MAX)
                .append(" forkFlips=").append(MAX_FORK_FLIPS)
                .append(" depth=").append(DEPTH)
                .append(" recursiveDepth=").append(RECURSIVE_DEPTH).append('\n');
        sb.append("  exemplar dag mask : ").append(mask).append('\n');
        sb.append("  latent set        : ").append(latSet).append('\n');
        sb.append("  relabeling        : ").append(mapping).append('\n');
        sb.append("  true DAG (canonical labels):\n").append(dag).append('\n');
        sb.append("  true MAG G* (canonical labels):\n").append(canonMag).append('\n');
        sb.append("  true PAG G* (canonical labels):\n").append(canonPag).append('\n');
        if (terminal != null) {
            sb.append("  terminal PAG from FcitSl:\n").append(terminal).append('\n');
        }
        sb.append(detail);
        sb.append("==== end entry ====\n");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // CANONICALIZATION AND RELABELING (verbatim PKE6/PKE7)
    // ────────────────────────────────────────────────────────────────────────

    private static final class Canon {
        final String key;
        final int[] perm;

        Canon(String key, int[] perm) {
            this.key = key;
            this.perm = perm;
        }
    }

    private static Canon canonicalKey(Graph mag, List<Node> obsSorted) {
        int n = obsSorted.size();
        Map<String, Integer> pos = new HashMap<>();
        for (int i = 0; i < n; i++) pos.put(obsSorted.get(i).getName(), i);

        List<Edge> edges = new ArrayList<>(mag.getEdges());
        int m = edges.size();
        int[] ia = new int[m], ib = new int[m], ca = new int[m], cb = new int[m];
        for (int k = 0; k < m; k++) {
            Edge e = edges.get(k);
            ia[k] = pos.get(e.getNode1().getName());
            ib[k] = pos.get(e.getNode2().getName());
            ca[k] = endpointCode(e.getEndpoint1());
            cb[k] = endpointCode(e.getEndpoint2());
        }

        int[] best = null;
        int[] bestPerm = null;
        for (int[] perm : PERMS) {
            int[] codes = new int[m];
            for (int k = 0; k < m; k++) {
                int pi = perm[ia[k]], pj = perm[ib[k]], ci = ca[k], cj = cb[k];
                if (pi > pj) {
                    int t = pi;
                    pi = pj;
                    pj = t;
                    t = ci;
                    ci = cj;
                    cj = t;
                }
                codes[k] = ((pi * n + pj) * 4 + ci) * 4 + cj;
            }
            Arrays.sort(codes);
            if (best == null || lexLess(codes, best)) {
                best = codes;
                bestPerm = perm;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(m).append(':');
        for (int c : best) sb.append(c).append(',');
        return new Canon(sb.toString(), bestPerm);
    }

    private static int endpointCode(Endpoint e) {
        if (e == Endpoint.TAIL) return 0;
        if (e == Endpoint.ARROW) return 1;
        if (e == Endpoint.CIRCLE) return 2;
        return 3;
    }

    private static boolean lexLess(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return a[i] < b[i];
        }
        return false;
    }

    private static Graph relabel(Graph g, List<Node> obsSorted, int[] perm, List<Node> canonNodes) {
        Map<String, Node> map = new HashMap<>();
        for (int i = 0; i < obsSorted.size(); i++) {
            map.put(obsSorted.get(i).getName(), canonNodes.get(perm[i]));
        }
        Graph out = new EdgeListGraph(canonNodes);
        for (Edge e : g.getEdges()) {
            out.addEdge(new Edge(map.get(e.getNode1().getName()), map.get(e.getNode2().getName()),
                    e.getEndpoint1(), e.getEndpoint2(), false));
        }
        return out;
    }

    private static Graph relabelWithLatents(Graph g, List<Node> obsSorted, int[] perm,
                                            List<Node> canonNodes, Set<Integer> latSet) {
        Map<String, Node> map = new HashMap<>();
        for (int i = 0; i < obsSorted.size(); i++) {
            map.put(obsSorted.get(i).getName(), canonNodes.get(perm[i]));
        }

        List<Node> all = new ArrayList<>(canonNodes);
        int k = 1;
        for (int li : new TreeSet<>(latSet)) {
            Node latent = new GraphNode("L" + k++);
            latent.setNodeType(NodeType.LATENT);
            map.put("X" + (li + 1), latent);
            all.add(latent);
        }

        Graph out = new EdgeListGraph(all);
        for (Edge e : g.getEdges()) {
            Node u = map.get(e.getNode1().getName());
            Node v = map.get(e.getNode2().getName());
            if (u == null || v == null) {
                throw new IllegalStateException("Unmapped node on edge " + e + "; map covers " + map.keySet());
            }
            out.addEdge(new Edge(u, v, e.getEndpoint1(), e.getEndpoint2(), false));
        }
        return out;
    }

    private static String mappingDesc(List<Node> obsSorted, int[] perm, Set<Integer> latSet) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < obsSorted.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(obsSorted.get(i).getName()).append("->V").append(perm[i] + 1);
        }
        sb.append(" ; latents(original): {");
        boolean first = true;
        for (int li : latSet) {
            if (!first) sb.append(",");
            sb.append("X").append(li + 1);
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // STREAMED, CAPPED LOG / CHECKPOINTING (verbatim PKE6/PKE7)
    // ────────────────────────────────────────────────────────────────────────

    private static final class StreamLog {
        private final PrintWriter out;
        private final int max;
        private int count;

        StreamLog(String path, int max, String header) throws IOException {
            this.out = new PrintWriter(new FileWriter(path, false));
            this.max = max;
            out.println(header);
            out.flush();
        }

        synchronized boolean write(String entry) {
            if (count >= max) return false;
            out.println(entry);
            out.flush();
            count++;
            if (count == max) {
                out.println("==== cap of " + max + " entries reached; log closed ====");
                out.flush();
                if (STOP.compareAndSet(false, true)) {
                    System.err.println("Violation log full -- stopping enumeration early.");
                }
            }
            return true;
        }

        synchronized void summary(String s) {
            out.println(s);
            out.flush();
        }

        synchronized void close() {
            out.close();
        }
    }

    private static Set<Long> loadCheckpoint() throws IOException {
        Set<Long> done = new HashSet<>();
        if (!Files.exists(Paths.get(CHECKPOINT_PATH))) {
            try (PrintWriter w = new PrintWriter(new FileWriter(CHECKPOINT_PATH, false))) {
                w.println(CONFIG_LINE);
                w.flush();
            }
            return done;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(CHECKPOINT_PATH))) {
            String first = br.readLine();
            if (first == null || !first.equals(CONFIG_LINE)) {
                throw new IllegalStateException("Checkpoint file " + CHECKPOINT_PATH
                        + " was written under a different configuration:\n  found   : " + first
                        + "\n  expected: " + CONFIG_LINE
                        + "\nDelete the checkpoint file to restart from scratch.");
            }
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) done.add(Long.parseLong(line));
            }
        }
        return done;
    }

    private static synchronized void appendCheckpoint(long block) {
        try (PrintWriter w = new PrintWriter(new FileWriter(CHECKPOINT_PATH, true))) {
            w.println(block);
            w.flush();
        } catch (IOException e) {
            System.err.println("Failed to append checkpoint for block " + block + ": " + e);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // STATIC TABLES AND THE RESULT ACCUMULATOR
    // ────────────────────────────────────────────────────────────────────────

    private static int[][] buildPairs() {
        int[][] pair = new int[N * (N - 1) / 2][2];
        for (int idx = 0, i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++, idx++) {
                pair[idx][0] = i;
                pair[idx][1] = j;
            }
        }
        return pair;
    }

    private static List<int[]> buildPerms() {
        List<int[]> perms = new ArrayList<>();
        int n = N - NUM_LATENT;
        int[] identity = new int[n];
        for (int i = 0; i < n; i++) identity[i] = i;
        if (!CANONICALIZE_PERMS) {
            perms.add(identity);
            return perms;
        }
        permute(identity, 0, perms);
        return perms;
    }

    private static void permute(int[] arr, int k, List<int[]> out) {
        if (k == arr.length) {
            out.add(arr.clone());
            return;
        }
        for (int i = k; i < arr.length; i++) {
            int t = arr[k];
            arr[k] = arr[i];
            arr[i] = t;
            permute(arr, k + 1, out);
            t = arr[k];
            arr[k] = arr[i];
            arr[i] = t;
        }
    }

    private static long choose(int n, int k) {
        long c = 1;
        for (int i = 0; i < k; i++) c = c * (n - i) / (i + 1);
        return c;
    }

    /**
     * Block visitation order.  Sequential order is systematically biased: block b fixes mask
     * bits 12..27, and the low blocks leave the high-index pairs entirely unset, so an early
     * stop covers only models whose high-index nodes are an independent set.  A fixed-seed
     * Fisher-Yates shuffle makes any prefix a uniform sample of blocks instead.  The checkpoint
     * stores actual block ids, so order does not affect resume and CONFIG_LINE need not change.
     */
    private static long[] blockOrder(long numBlocks) {
        if (numBlocks > Integer.MAX_VALUE) {
            throw new IllegalStateException("numBlocks " + numBlocks + " exceeds array addressing; "
                    + "raise BLOCK_SIZE or switch to an index-mapping shuffle.");
        }
        long[] order = new long[(int) numBlocks];
        for (int i = 0; i < order.length; i++) order[i] = i;
        if (!SHUFFLE_BLOCKS) return order;
        Random rnd = new Random(BLOCK_ORDER_SEED);
        for (int i = order.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            long t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        return order;
    }

    private static final class Result {
        long dagsScanned, modelsScanned, dupModels, distinctClasses, skipped;
        long exact, equivNotExact, skeletonMatchOrientDiff, skeletonDiff, error;
        long skeletonExtra, skeletonMissing;

        static void merge(Result a, Result b) {
            a.add(b);
        }

        void add(Result o) {
            dagsScanned += o.dagsScanned;
            modelsScanned += o.modelsScanned;
            dupModels += o.dupModels;
            distinctClasses += o.distinctClasses;
            skipped += o.skipped;
            exact += o.exact;
            equivNotExact += o.equivNotExact;
            skeletonMatchOrientDiff += o.skeletonMatchOrientDiff;
            skeletonDiff += o.skeletonDiff;
            error += o.error;
            skeletonExtra += o.skeletonExtra;
            skeletonMissing += o.skeletonMissing;
        }
    }
}