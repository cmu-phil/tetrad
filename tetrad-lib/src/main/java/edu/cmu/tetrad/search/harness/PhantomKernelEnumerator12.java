/// ////////////////////////////////////////////////////////////////////////////
// PhantomKernelEnumerator12.java  (parallel, PAG-space, end-to-end Fcit)    //
//                                                                             //
// pke12 = PKE8's QUESTION over PKE10's POPULATION.                            //
//                                                                             //
// PKE8 asked the single thing that matters for correctness -- does the actual //
// shipping Fcit, from its own GRaSP start, driven by the m-separation       //
// oracle, terminate at exactly G*? -- but paid for it with the DAG x latent-  //
// placement sweep (2^21 masks x C(7,2) placements, deduplicated to 2,691      //
// classes at N=7/|L|=2), which does not scale.                                //
//                                                                             //
// PKE10 observed that everything measured depends on the model ONLY through   //
// the true PAG (every oracle query is over observed variables, and the PAG    //
// determines the observed m-separation model), and therefore enumerated       //
// DISTINCT TRUE PAGs directly.  The same observation applies verbatim to      //
// PKE8's question: Fcit sees only the oracle, and the oracle is a function  //
// of the true PAG's Markov class.  So pke12 enumerates distinct true PAGs a   //
// la PKE10 and runs PKE8's end-to-end check once per PAG:                     //
//                                                                             //
//     terminal PAG returned by Fcit.search()  ==  G* (the true PAG)?        //
//                                                                             //
// TWO ENUMERATION MODES (arg [0]), identical to PKE10:                        //
//                                                                             //
//   dagsweep : build every DAG on N vertices in a fixed order x every         //
//     placement of numLatent latents, take dagToPag, and deduplicate.  This   //
//     reproduces EXACTLY a DAG-sweep population (e.g. PKE8's N=7/|L|=2), at   //
//     the cost of the dagToPag calls dominating the enumeration phase.        //
//                                                                             //
//   magspace : enumerate every labelled graph on OBS observed vertices with   //
//     each pair absent / --> / <-- / <->, keep the legal MAGs, project to a   //
//     PAG, and deduplicate.  ~4^C(OBS,2) legality checks (about 1M at OBS=5), //
//     yielding ALL PAGs over OBS observed variables -- a SUPERSET of those    //
//     realizable with any bounded latent count.  The resulting claim is       //
//     therefore about all PAGs at that observed size, the stronger and        //
//     simpler scope statement.                                                //
//                                                                             //
// COUNTS CHANGE UNIT (as in PKE10).  Every count here is per DISTINCT TRUE    //
// PAG LABELLING, not per model, so totals are NOT commensurable with PKE8's   //
// per-class counts.  Zero-counts are unaffected: a violation present under    //
// one labelling is present under all, so a zero here is a zero there.         //
//                                                                             //
// ORACLE DRIVE (one uniformity change from PKE8): the true MAG for the        //
// oracle is obtained as zhangMagFromPag(G*) in BOTH modes, rather than        //
// dagToMag(dag) in dagsweep.  Any MAG in G*'s equivalence class induces the   //
// same observed m-separation model, so the oracle is identical; deriving it   //
// from the PAG keeps the analysis a single mode-independent code path (this   //
// is exactly PKE10's analyze() convention).  Fcit is constructed with the   //
// MsepTest on that MAG as test and a GraphScore on the same MAG as score,     //
// which flips its startWith to GRASP -- the Oracle GRaSP path, as in PKE8.    //
//                                                                             //
// SELECTION BIAS: a single EXCLUDE_SELECTION_BIAS constant (default true,     //
// matching PKE10) is applied consistently to the enumeration projections AND  //
// to Fcit, since G* and the search must operate under the same assumption.  //
// (PKE8's code had false here while its header said true; magspace enumerates //
// no undirected edges, so true is the coherent default for this population.)  //
//                                                                             //
// VERDICT BUCKETS per distinct PAG (verbatim PKE8):                           //
//   EXACT              : terminal PAG == G* (edge+endpoint identical).        //
//   EQUIVALENT_NOT_EXACT: terminal != G* but Markov-equivalent (same model)   //
//                         -- flagged, dumped: usually an orientation nit.     //
//   SKELETON_MATCH_ORIENT_DIFF : same adjacencies, different marks, NOT       //
//                         Markov-equivalent -- an orientation error.          //
//   SKELETON_DIFF      : adjacencies differ -- the serious miss; extra edges  //
//                         = an undeletable spurious edge, missing = an        //
//                         over-deletion.                                      //
//   ERROR              : Fcit threw or timed out.                           //
// Only EXACT is a pass; every other bucket is a VIOLATION, logged in full.    //
//                                                                             //
// DEDUP KEY: models are deduplicated on their CANONICAL key -- the least edge   //
// string over all OBS! relabellings -- so Fcit runs once per isomorphism      //
// class (PAG up to relabelling), not once per labelling.  This is the lever     //
// that cuts the Fcit call count (up to OBS!-fold in the limit, less in        //
// practice), which matters because per-model analysis is the cost that grows.   //
// The positional key (identity order) survives only as the LOG id, naming the   //
// specific representative that was analyzed.  Fcit's verdict is relabelling-   //
// invariant (oracle, start, comparison all commute with a renaming), so         //
// collapsing isomorphs to one run misses nothing: a zero over classes is a zero //
// over labellings.                                                             //
//                                                                             //
// INLINE STREAMING ANALYSIS: rather than enumerate all distinct PAGs and then //
// analyze, pke12 runs Fcit on each distinct PAG the instant it is first      //
// discovered, and writes+flushes any violation to the log immediately.  So a   //
// long magspace run surfaces a counterexample the moment it hits one -- tail   //
// the violation log to watch live -- instead of only at the end.               //
//                                                                             //
// CHECKPOINTING: the magspace ENUMERATION is block-checkpointed (see           //
// MAGSPACE_BLOCK_SIZE and args [5]/[6]) so a run can be killed and resumed.     //
// Because analysis is inline, a resumed run does NOT re-analyze models from     //
// already-completed blocks (their verdicts are already in the log), so the      //
// violation log is opened in APPEND mode and earlier runs' entries are kept.    //
// dagsweep's enumeration (2^p masks, 2^21 at N=7) is left uncheckpointed, as    //
// in PKE10.  The violation log is streamed and capped; filling it stops the     //
// run early.  Model ids in the log are the PAG's positional key (stable, and    //
// it identifies the model), rather than a PAG#i index into a sorted list.       //
//                                                                             //
// args: [0]=mode (dagsweep|magspace, default magspace)                        //
//       [1]=N (dagsweep only, default 7)  [2]=numLatent (dagsweep, default 2) //
//       [3]=observed count (magspace only, default 5)                         //
//       [4]=violation log path (default pke12_violations.log)                 //
//       [5]=magspace checkpoint path (default pke12_magspace_obs<OBS>_        //
//           checkpoint.txt) -- delete to restart enumeration from scratch     //
//       [6]=magspace distinct-class keys path (default pke12_magspace_obs<OBS>_//
//           keys.txt) -- holds the CANONICAL key of every class found so far;   //
//           do not delete without also deleting [5], or resume will re-analyze  //
//           only the blocks it reruns and miss the classes it skips             //
//                                                                             //
// Blocks are visited in a fixed-seed shuffled order (SHUFFLE_BLOCKS), so a      //
// prefix -- or a live tail of the log -- samples across mask space rather than  //
// a low-index corner.  Shuffling does not affect resume (the checkpoint stores  //
// real block ids).  Changing MAGSPACE_BLOCK_SIZE changes the enum config header //
// and so invalidates an old checkpoint/keys pair, forcing a clean restart.      //
//                                                                             //
// @author josephramsey (harness scaffolding by Claude)                        //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fcit;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.util.SublistGenerator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.LongStream;

/**
 * pke12: enumerate DISTINCT TRUE PAGs (magspace or dagsweep, as in PKE10) and, once per
 * PAG, run the real Fcit from a GRaSP start, oracle-driven, reporting every PAG whose
 * terminal graph is not exactly G* (PKE8's end-to-end check over PKE10's population).
 */
public final class PhantomKernelEnumerator12 {

    /**
     * Default constructor.
     */
    public PhantomKernelEnumerator12() {
    }

    // ────────────────────────────────────────────────────────────────────────
    // CONFIGURATION (enumeration knobs via args, Fcit knobs hard-coded)
    // ────────────────────────────────────────────────────────────────────────

    private static String MODE       = "magspace";
    private static int    N          = 7;    // dagsweep only
    private static int    NUM_LATENT = 2;    // dagsweep only
    private static int    OBS        = 6;

    // ── Fcit configuration (verbatim PKE8; match the shipping defaults to certify) ──
    /** Per-run Fcit timeout, ms; -1 = unlimited.  A positive value turns a
     *  hang into an ERROR verdict instead of stalling the whole run. */
    private static final long FCIT_TIMEOUT_MS = -1L;
    /** Depth / recursive-depth passed to Fcit (-1 = unlimited). */
    private static final int DEPTH = -1;
    private static final int RECURSIVE_DEPTH = -1;
    /** Applied consistently to the enumeration projections AND Fcit (see header). */
    private static final boolean EXCLUDE_SELECTION_BIAS = true;

    /** Violations are the point of the run; cap generously. */
    private static final int VIOLATION_LOG_MAX = 3000;

    /** Print an analysis progress line every this many PAGs. */
    private static final int PROGRESS_EVERY = 250;

    // ── Magspace enumeration checkpointing ──────────────────────────────────
    // Only magspace needs this: its 4^C(OBS,2) sweep hits 2^30 candidates already
    // at OBS=6 (1024x the OBS=5 sweep), which does not finish in one sitting on a
    // workstation. dagsweep's 2^p mask sweep is the cheap PKE8-scope population
    // (2^21 at N=7) and is left unchecked, as in PKE10.
    //
    // Two files, both gated on a config header so a parameter change is caught
    // rather than silently corrupting a resume:
    //   checkpoint file : completed block ids, one per line, for resume.
    //   keys file       : the CANONICAL key of every distinct-up-to-relabelling PAG
    //                      found so far, one per line, appended as discovered. Used
    //                      purely as a dedup set on resume so already-analyzed classes
    //                      are recognized and skipped (their verdicts are already in
    //                      the violation log); it is not reconstructed into Graphs.
    /** Candidates per checkpoint block. 2^16 = 65536 -> 16 blocks at OBS=5 (2^20
     *  total), 16384 blocks at OBS=6 (2^30 total), 2^26 blocks at OBS=7. Small enough
     *  that a shuffled prefix is a representative sample and checkpoints are frequent;
     *  big enough that per-block overhead stays negligible. */
    private static final long MAGSPACE_BLOCK_SIZE = 1L << 16;

    /** Visit blocks in a fixed-seed shuffled order rather than 0..numBlocks-1, so that
     *  stopping early (or watching the log) samples across mask space instead of a
     *  low-index corner. The checkpoint stores actual block ids, so shuffling does not
     *  affect resume and needs no config-header change. */
    private static final boolean SHUFFLE_BLOCKS = true;
    /** Seed for the block visitation order. Fixed so a run is reproducible. */
    private static final long BLOCK_ORDER_SEED = 20260725L;

    // ────────────────────────────────────────────────────────────────────────
    // SHARED STATE
    // ────────────────────────────────────────────────────────────────────────

    /** Canonical observed-node names, shared by every enumerated PAG. */
    private static List<Node> CANON;

    private static final AtomicBoolean STOP = new AtomicBoolean(false);
    private static final AtomicLong ERR_PRINTS = new AtomicLong();
    private static final AtomicLong ANALYZED = new AtomicLong();
    private static long ANALYSIS_T0;

    private static String CONFIG_LINE;
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
        if (args.length > 0) MODE       = args[0].toLowerCase();
        if (args.length > 1) N          = Integer.parseInt(args[1]);
        if (args.length > 2) NUM_LATENT = Integer.parseInt(args[2]);
        if (args.length > 3) OBS        = Integer.parseInt(args[3]);
        String violationLogPath = (args.length > 4) ? args[4] : "pke12_violations.log";

        boolean dagSweep = MODE.startsWith("dag");
        if (dagSweep) OBS = N - NUM_LATENT;

        // Magspace-only; defaults are mode/OBS-scoped so different OBS runs don't collide.
        String checkpointPath = (args.length > 5) ? args[5]
                : "pke12_magspace_obs" + OBS + "_checkpoint.txt";
        String keysPath = (args.length > 6) ? args[6]
                : "pke12_magspace_obs" + OBS + "_keys.txt";

        CANON = new ArrayList<>();
        for (int i = 0; i < OBS; i++) CANON.add(new GraphNode("V" + (i + 1)));

        CONFIG_LINE = String.format(
                "# pke12 config: mode=%s N=%d latent=%d observed=%d "
                        + "timeoutMs=%d depth=%d recDepth=%d "
                        + "exclSel=%b",
                dagSweep ? "dagsweep" : "magspace", N, NUM_LATENT, OBS,
                FCIT_TIMEOUT_MS,
                DEPTH, RECURSIVE_DEPTH, EXCLUDE_SELECTION_BIAS);

        System.err.println(CONFIG_LINE);
        System.err.printf("threads~%d%n", Runtime.getRuntime().availableProcessors());

        // ── Violation log (opened BEFORE enumeration, since analysis is now inline) ──
        // Append mode: on a resumed run, blocks already completed are skipped and their
        // models are NOT re-analyzed, so violations from earlier runs must survive in the
        // log rather than being truncated away.  The header is written only when the file
        // is new.
        String header = CONFIG_LINE + "\n# run started " + new Date()
                + "\n# VIOLATIONS: every distinct true PAG whose terminal Fcit PAG (GRaSP start,"
                + "\n# oracle-driven) is NOT exactly G*.  Analysis is INLINE: each new distinct"
                + "\n# PAG is run through Fcit the moment it is found, and any violation below"
                + "\n# is written and flushed immediately -- tail this file to watch live.  Bucket"
                + "\n# legend:"
                + "\n#   EQUIVALENT_NOT_EXACT       -- Markov-equivalent to G* but not edge-identical"
                + "\n#   SKELETON_MATCH_ORIENT_DIFF -- same skeleton, non-equivalent orientation error"
                + "\n#   SKELETON_DIFF              -- adjacencies differ (extra = undeletable spurious,"
                + "\n#                                 missing = over-deletion)"
                + "\n#   ERROR                      -- Fcit threw or timed out";
        violationLog = new StreamLog(violationLogPath, VIOLATION_LOG_MAX, header);

        // ── Enumeration + inline analysis (single phase) ──
        // Fcit runs on each distinct PAG as it is discovered; `total` accumulates the
        // verdicts across the parallel enumeration streams.
        Result total = new Result();
        ANALYSIS_T0 = System.currentTimeMillis();
        long tEnum = System.currentTimeMillis();
        long distinctCount = dagSweep
                ? enumerateAndAnalyzeDagSweep(total)
                : enumerateAndAnalyzeMagSpace(total, checkpointPath, keysPath);
        total.distinctPags = distinctCount;
        System.err.printf("enumeration+analysis: %d distinct true PAGs analyzed in %.1f s%n",
                distinctCount, (System.currentTimeMillis() - tEnum) / 1000.0);

        String summary = summarize(total, System.currentTimeMillis() - ANALYSIS_T0,
                violationLogPath);
        System.out.println(summary);
        violationLog.summary("\n" + summary);
        violationLog.close();
    }

    // ────────────────────────────────────────────────────────────────────────
    // ENUMERATION + INLINE ANALYSIS: Fcit runs on each distinct PAG as found
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Mode dagsweep: every DAG on N vertices in a fixed order x every placement of
     * NUM_LATENT latents, deduplicated by true PAG.  Reproduces a DAG-sweep
     * population exactly; the dagToPag calls dominate the run.  Each PAG the first
     * time it is seen is immediately run through Fcit (inline analysis).
     *
     * @return number of distinct true PAGs discovered (= analyzed).
     */
    private static long enumerateAndAnalyzeDagSweep(Result total) {
        int p = N * (N - 1) / 2;
        int[][] pair = new int[p][2];
        for (int idx = 0, i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++, idx++) { pair[idx][0] = i; pair[idx][1] = j; }
        }
        long totalMasks = 1L << p;
        System.err.printf("  dagsweep: 2^%d = %d masks x C(%d,%d) latent placements%n",
                p, totalMasks, N, NUM_LATENT);

        Map<String, Boolean> seen = new ConcurrentHashMap<>();
        AtomicLong done = new AtomicLong();

        LongStream.range(0, totalMasks).parallel().forEach(mask -> {
            if (STOP.get()) return;
            SublistGenerator latGen = new SublistGenerator(N, NUM_LATENT);
            int[] latChoice;
            while ((latChoice = latGen.next()) != null) {
                if (latChoice.length != NUM_LATENT) continue;
                try {
                    List<Node> nodes = new ArrayList<>();
                    for (int i = 0; i < N; i++) nodes.add(new GraphNode("X" + (i + 1)));
                    Graph dag = new EdgeListGraph(nodes);
                    for (int b = 0; b < p; b++) {
                        if ((mask & (1L << b)) != 0) {
                            dag.addDirectedEdge(nodes.get(pair[b][0]), nodes.get(pair[b][1]));
                        }
                    }
                    for (int li : latChoice) nodes.get(li).setNodeType(NodeType.LATENT);

                    Graph pag = GraphTransforms.dagToPag(dag, new Knowledge(),
                            EXCLUDE_SELECTION_BIAS, RECURSIVE_DEPTH);
                    Graph relabelled = relabelToCanon(pag);
                    Graph canon = canonicalEdgeOrder(relabelled);
                    // Dedup on the canonical (isomorph-invariant) key: one Fcit run per
                    // class up to relabelling, matching magspace.
                    if (seen.putIfAbsent(canonicalKey(canon), Boolean.TRUE) == null) {
                        analyzeInline(total, canon);   // new class -> run Fcit now
                    }
                } catch (Exception ignore) {
                    // A model that will not project is not a true PAG; skip it.
                }
            }
            long d = done.incrementAndGet();
            if ((d & 0x3FFF) == 0) {
                System.err.printf("  …mask %d/%d, %d distinct so far%n", d, totalMasks, seen.size());
            }
        });

        return seen.size();
    }

    /**
     * Mode magspace: every labelled graph on OBS vertices with each pair absent /
     * --&gt; / &lt;-- / &lt;-&gt;, keeping legal MAGs, projected to PAGs and deduplicated.
     * Yields ALL PAGs over OBS observed variables -- a superset of those a bounded
     * latent count can realize.
     * <p>
     * Checkpointed in blocks of {@link #MAGSPACE_BLOCK_SIZE} candidates so a run can
     * be killed and resumed: {@code checkpointPath} records completed block ids,
     * {@code keysPath} records every distinct PAG found so far as an invertible
     * positional key. On resume, keysPath is loaded into {@code seen} BEFORE any block
     * runs, so already-analyzed models are recognized and skipped rather than
     * re-analyzed.
     * <p>
     * Analysis is INLINE: the moment a candidate is confirmed a genuinely-new distinct
     * PAG (won the {@code seen.putIfAbsent} race), Fcit runs on it and any violation
     * is flushed to the log immediately. Ordering within a block is: analyze -> persist
     * key -> (after the block) flush keys -> checkpoint block. A hard crash mid-block
     * reruns the whole block on resume; models whose keys were already flushed are in
     * {@code seen} and skipped, so at worst a crash between analyzing a model and
     * flushing its key causes that one model to be re-analyzed (a duplicate log entry,
     * never a miss).
     *
     * @return number of distinct true PAGs discovered (= analyzed) across all runs.
     */
    private static long enumerateAndAnalyzeMagSpace(Result total, String checkpointPath,
                                                    String keysPath) throws IOException {
        int m = OBS * (OBS - 1) / 2;
        int[][] pr = new int[m][2];
        for (int idx = 0, i = 0; i < OBS; i++) {
            for (int j = i + 1; j < OBS; j++, idx++) { pr[idx][0] = i; pr[idx][1] = j; }
        }
        long total4 = 1L << (2 * m);   // 4^m
        long numBlocks = (total4 + MAGSPACE_BLOCK_SIZE - 1) / MAGSPACE_BLOCK_SIZE;
        String enumConfig = String.format(
                "# pke12 magspace-enum config: OBS=%d blockSize=%d", OBS, MAGSPACE_BLOCK_SIZE);

        Set<Long> doneBlocks = loadCheckpoint(checkpointPath, enumConfig);
        // Dedup set keyed by CANONICAL (isomorph-invariant) key: Fcit runs once per
        // class up to relabelling, not once per labelling. Value unused; Boolean.TRUE
        // keeps it a simple set-map.
        Map<String, Boolean> seen = new ConcurrentHashMap<>();
        loadPersistedKeys(keysPath, enumConfig, seen);
        KeyAppender keyOut = new KeyAppender(keysPath, enumConfig);

        System.err.printf("  magspace: 4^%d = %d candidate graphs on %d observed vertices | "
                        + "%d blocks of %d, %d already complete | %d distinct classes loaded from disk%n",
                m, total4, OBS, numBlocks, MAGSPACE_BLOCK_SIZE, doneBlocks.size(), seen.size());

        long[] order = blockOrder(numBlocks);

        for (long oi = 0; oi < numBlocks; oi++) {
            if (STOP.get()) break;
            long b = order[(int) oi];
            if (doneBlocks.contains(b)) continue;
            long lo = b * MAGSPACE_BLOCK_SIZE;
            long hi = Math.min(total4, lo + MAGSPACE_BLOCK_SIZE);

            // Keys genuinely new in this block, to be persisted after analysis. Analysis
            // happens inline (below) the instant a key wins the putIfAbsent race, so
            // violations surface mid-block; key persistence is deferred to block end so it
            // pairs atomically with the checkpoint.
            Set<String> blockNewKeys = ConcurrentHashMap.newKeySet();

            LongStream.range(lo, hi).parallel().forEach(code -> {
                try {
                    Graph mag = new EdgeListGraph(CANON);
                    for (int idx = 0; idx < m; idx++) {
                        int st = (int) ((code >> (2 * idx)) & 3L);
                        if (st == 0) continue;
                        Node a = CANON.get(pr[idx][0]), b2 = CANON.get(pr[idx][1]);
                        switch (st) {
                            case 1:  mag.addDirectedEdge(a, b2); break;
                            case 2:  mag.addDirectedEdge(b2, a); break;
                            default: mag.addBidirectedEdge(a, b2); break;
                        }
                    }
                    if (!isLegalMag(mag)) return;
                    Graph pag = pagOfMag(mag);
                    Graph canon = canonicalEdgeOrder(pag);
                    // Dedup on the CANONICAL key (least edge string over all OBS!
                    // relabellings), so isomorphic PAGs collapse to one Fcit run.
                    String key = canonicalKey(canon);
                    if (seen.putIfAbsent(key, Boolean.TRUE) == null) {
                        analyzeInline(total, canon);   // new class -> run Fcit now
                        blockNewKeys.add(key);
                    }
                } catch (Exception ignore) {
                    // Not a legal MAG / will not project; skip.
                }
            });

            // Persist this block's new keys, flush, then checkpoint the block. The
            // flush-before-checkpoint order means a block is marked done only after its
            // keys are on disk, so a resume never skips a block whose keys it hasn't got.
            for (String key : blockNewKeys) keyOut.write(key);
            keyOut.flush();
            appendCheckpoint(checkpointPath, b);

            System.err.printf("  block %d (%d/%d visited) | +%d new this block | %d classes so far | "
                            + "EXACT=%d viol=%d | %.1f min%n",
                    b, oi + 1, numBlocks, blockNewKeys.size(), seen.size(),
                    total.exact.sum(), violationCount(total),
                    (System.currentTimeMillis() - ANALYSIS_T0) / 60000.0);
        }
        keyOut.close();

        return seen.size();
    }

    private static long violationCount(Result t) {
        return t.equivNotExact.sum() + t.skeletonMatchOrientDiff.sum()
                + t.skeletonDiff.sum() + t.error.sum();
    }

    /** Relabel a PAG's observed nodes to V1..VOBS in name order. */
    private static Graph relabelToCanon(Graph pag) {
        List<Node> src = new ArrayList<>(pag.getNodes());
        src.sort(Comparator.comparing(Node::getName));
        if (src.size() != OBS) {
            throw new IllegalStateException("expected " + OBS + " observed nodes, got " + src.size());
        }
        Map<Node, Node> map = new HashMap<>();
        for (int i = 0; i < OBS; i++) map.put(src.get(i), CANON.get(i));

        Graph out = new EdgeListGraph(CANON);
        for (Edge e : pag.getEdges()) {
            Node a = map.get(e.getNode1()), b = map.get(e.getNode2());
            out.addEdge(new Edge(a, b, e.getEndpoint1(), e.getEndpoint2()));
        }
        return out;
    }

    /** Rebuild a graph with nodes in CANON order and edges inserted in sorted
     *  order, so its internal adjacency ordering is a function of the graph's
     *  content alone.  Without this, the representative kept by putIfAbsent under
     *  a parallel stream is whichever object won the race, and two objects equal
     *  as labelled graphs can still differ in getAdjacentNodes order -- which the
     *  search is sensitive to, since it walks adjacency in order. */
    private static Graph canonicalEdgeOrder(Graph g) {
        List<Edge> edges = new ArrayList<>(g.getEdges());
        edges.sort(Comparator
                .comparing((Edge e) -> e.getNode1().getName())
                .thenComparing(e -> e.getNode2().getName()));
        Graph out = new EdgeListGraph(CANON);
        for (Edge e : edges) {
            out.addEdge(new Edge(e.getNode1(), e.getNode2(), e.getEndpoint1(), e.getEndpoint2()));
        }
        return out;
    }

    /** Edge string under the identity order: cheap, labelling-sensitive. Used as the
     *  log id of a model (names the specific representative that was analyzed). */
    private static String positionalKey(Graph g) {
        return encode(g, CANON);
    }

    /** Lexicographically least edge string over all OBS! permutations of the vertices:
     *  an isomorph-invariant key, so two PAGs equal up to relabelling share it. This is
     *  the dedup key, so Fcit runs once per class up to relabelling. Cost is one OBS!
     *  minimization per surviving candidate -- cheaper than the Fcit run it saves. */
    private static String canonicalKey(Graph g) {
        List<Node> order = new ArrayList<>(CANON);
        String[] best = {null};
        permute(g, order, 0, best);
        return best[0];
    }

    private static void permute(Graph g, List<Node> order, int k, String[] best) {
        if (k == order.size()) {
            String s = encode(g, order);
            if (best[0] == null || s.compareTo(best[0]) < 0) best[0] = s;
            return;
        }
        for (int i = k; i < order.size(); i++) {
            Collections.swap(order, k, i);
            permute(g, order, k + 1, best);
            Collections.swap(order, k, i);
        }
    }

    private static String encode(Graph g, List<Node> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            for (int j = i + 1; j < order.size(); j++) {
                Node a = order.get(i), b = order.get(j);
                Edge e = g.getEdge(a, b);
                if (e == null) { sb.append('.'); continue; }
                sb.append(mark(g.getEndpoint(b, a))).append(mark(g.getEndpoint(a, b)));
            }
        }
        return sb.toString();
    }

    private static char mark(Endpoint ep) {
        if (ep == Endpoint.ARROW)  return 'a';
        if (ep == Endpoint.TAIL)   return 't';
        if (ep == Endpoint.CIRCLE) return 'c';
        return '?';
    }

    private static boolean isLegalMag(Graph g) {
        return g.paths().isLegalMag();
    }

    // MAG -> PAG: unified on MagToPag.convert, the projection used by the FcitMag
    // commit path and the legality check's round-trip prong (PKE9/PKE10 convention).
    private static Graph pagOfMag(Graph mag) throws InterruptedException {
        return new MagToPag(mag).convert(false, EXCLUDE_SELECTION_BIAS);
    }

    // ────────────────────────────────────────────────────────────────────────
    // MAGSPACE ENUMERATION CHECKPOINTING (block ids + distinct-PAG dedup keys)
    // ────────────────────────────────────────────────────────────────────────

    /** Append-only, flush-on-demand writer for the distinct-PAG keys file. First line is
     *  the config header (written fresh, or verified to match on an existing file). */
    private static final class KeyAppender {
        private final PrintWriter out;

        KeyAppender(String path, String expectedConfig) throws IOException {
            boolean existed = Files.exists(Paths.get(path));
            if (existed) {
                try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                    String first = br.readLine();
                    if (first == null || !first.equals(expectedConfig)) {
                        throw new IllegalStateException("Keys file " + path
                                + " was written under a different configuration:\n  found   : " + first
                                + "\n  expected: " + expectedConfig
                                + "\nDelete both the keys file and its checkpoint file to restart.");
                    }
                }
            }
            this.out = new PrintWriter(new FileWriter(path, existed));
            if (!existed) {
                out.println(expectedConfig);
                out.flush();
            }
        }

        synchronized void write(String key) {
            out.println(key);
        }

        synchronized void flush() {
            out.flush();
        }

        synchronized void close() {
            out.close();
        }
    }

    /** Load the canonical key of every previously-analyzed class into `into` (as a dedup
     *  set; values are Boolean.TRUE). Classes on disk were already analyzed in the run that
     *  discovered them -- their verdicts are already in the violation log -- so on resume
     *  they only need to be recognized and skipped, not re-run. No-op if the file doesn't
     *  exist yet. */
    private static void loadPersistedKeys(String path, String expectedConfig,
                                          Map<String, Boolean> into) throws IOException {
        if (!Files.exists(Paths.get(path))) return;
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String first = br.readLine();
            if (first == null || !first.equals(expectedConfig)) {
                throw new IllegalStateException("Keys file " + path
                        + " was written under a different configuration:\n  found   : " + first
                        + "\n  expected: " + expectedConfig
                        + "\nDelete both the keys file and its checkpoint file to restart.");
            }
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                into.put(line, Boolean.TRUE);
            }
        }
    }

    private static Set<Long> loadCheckpoint(String path, String expectedConfig) throws IOException {
        Set<Long> done = new HashSet<>();
        if (!Files.exists(Paths.get(path))) {
            try (PrintWriter w = new PrintWriter(new FileWriter(path, false))) {
                w.println(expectedConfig);
                w.flush();
            }
            return done;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String first = br.readLine();
            if (first == null || !first.equals(expectedConfig)) {
                throw new IllegalStateException("Checkpoint file " + path
                        + " was written under a different configuration:\n  found   : " + first
                        + "\n  expected: " + expectedConfig
                        + "\nDelete the checkpoint file (and its keys file) to restart from scratch.");
            }
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) done.add(Long.parseLong(line));
            }
        }
        return done;
    }

    private static synchronized void appendCheckpoint(String path, long block) {
        try (PrintWriter w = new PrintWriter(new FileWriter(path, true))) {
            w.println(block);
            w.flush();
        } catch (IOException e) {
            System.err.println("Failed to append checkpoint for block " + block + ": " + e);
        }
    }

    /** Block visitation order. Sequential order is systematically biased: block b fixes the
     *  high bits of the candidate code, so a low-index prefix leaves the high-index pairs
     *  unset and samples only a corner of mask space. A fixed-seed Fisher-Yates shuffle makes
     *  any prefix a representative sample instead. The checkpoint stores actual block ids, so
     *  order does not affect resume and the config header need not change. */
    private static long[] blockOrder(long numBlocks) {
        if (numBlocks > Integer.MAX_VALUE) {
            throw new IllegalStateException("numBlocks " + numBlocks + " exceeds array addressing; "
                    + "raise MAGSPACE_BLOCK_SIZE or switch to an index-mapping shuffle.");
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

    // ────────────────────────────────────────────────────────────────────────
    // INLINE ANALYSIS: PKE8's end-to-end Fcit check, run the instant a PAG is found
    // ────────────────────────────────────────────────────────────────────────

    /** Run Fcit on one just-discovered distinct true PAG and record the verdict.
     *  Called from inside the enumeration streams, so it must be thread-safe: the Result
     *  counters are LongAdders and the violation log is synchronized. The model id is the
     *  PAG's positional key -- stable, and it identifies the model in the log. */
    private static void analyzeInline(Result r, Graph truePag) {
        if (STOP.get()) return;
        String modelId = positionalKey(truePag);
        r.pagsScanned.increment();
        try {
            // The oracle is m-separation in a MAG of G*'s equivalence class.  Any MAG
            // in the class induces the same observed m-separation model, so deriving
            // it from the PAG (Zhang completion) makes the analysis mode-independent:
            // Fcit cannot tell two models sharing a true PAG apart.  This replaces
            // PKE8's dagToMag(dag) with the same oracle.
            Graph trueMag = GraphTransforms.zhangMagFromPag(truePag);
            runFcit(r, modelId, trueMag, truePag, CANON);
        } catch (Exception ex) {
            r.skipped.increment();
            if (ERR_PRINTS.incrementAndGet() <= 5) {
                System.err.println(modelId + " skipped: " + ex);
                ex.printStackTrace();
            }
        } finally {
            long d = ANALYZED.incrementAndGet();
            if (d % PROGRESS_EVERY == 0) {
                System.err.printf("  analyzed %d PAGs | EXACT=%d EQUIV=%d ORIENT=%d SKEL=%d ERR=%d "
                                + "| %.1f min%n",
                        d, r.exact.sum(), r.equivNotExact.sum(), r.skeletonMatchOrientDiff.sum(),
                        r.skeletonDiff.sum(), r.error.sum(),
                        (System.currentTimeMillis() - ANALYSIS_T0) / 60000.0);
            }
        }
    }

    private static void runFcit(Result r, String modelId, Graph trueMag, Graph truePag,
                                List<Node> obs) {
        // Oracle drive: MsepTest on the true MAG as both test and score basis.  Fcit's
        // constructor flips startWith to GRASP when the test is an MsepTest, so this is the
        // Oracle GRaSP path.  A fresh graph per Fcit (it mutates node types internally).
        Graph magForTest = new EdgeListGraph(trueMag);
        Graph magForScore = new EdgeListGraph(trueMag);
        MsepTest test = new MsepTest(magForTest);
        GraphScore scoreObj = new GraphScore(magForScore);

        Graph terminal;
        try {
            Fcit fcit = new Fcit(test, scoreObj);
            fcit.setKnowledge(new Knowledge());
            fcit.setExcludeSelectionBias(EXCLUDE_SELECTION_BIAS);
            fcit.setCompleteRuleSetUsed(true);
            fcit.setDepth(DEPTH);
            fcit.setRecursiveDepth(RECURSIVE_DEPTH);
            fcit.setTimeout(FCIT_TIMEOUT_MS);
            fcit.setVerbose(false);
            terminal = fcit.search();
        } catch (Throwable ex) {
            r.error.increment();
            violationLog.write(violationEntry("ERROR", modelId, trueMag, truePag, null,
                    "Fcit threw: " + ex));
            if (ERR_PRINTS.incrementAndGet() <= 10) {
                System.err.println("Fcit error " + modelId + ": " + ex);
            }
            return;
        }

        // Normalize terminal to the canonical observed nodes (Fcit returns a PAG over
        // the test's variables, which are the trueMag nodes -- same names V1..VOBS).
        Graph term = GraphUtils.replaceNodes(terminal, obs);

        // Primary verdict: exact edge+endpoint identity with G*.
        if (graphsIdentical(term, truePag)) {
            r.exact.increment();
            return;
        }

        // Non-exact: classify.  Skeleton first.
        boolean sameSkeleton = sameSkeleton(term, truePag);
        String bucket;
        if (!sameSkeleton) {
            r.skeletonDiff.increment();
            int[] xd = skeletonDelta(term, truePag);
            if (xd[0] > 0) r.skeletonExtra.increment();
            if (xd[1] > 0) r.skeletonMissing.increment();
            bucket = "SKELETON_DIFF";
        } else if (markovEquivalent(term, truePag, obs)) {
            r.equivNotExact.increment();
            bucket = "EQUIVALENT_NOT_EXACT";
        } else {
            r.skeletonMatchOrientDiff.increment();
            bucket = "SKELETON_MATCH_ORIENT_DIFF";
        }

        violationLog.write(violationEntry(bucket, modelId, trueMag, truePag, term,
                diffDetail(term, truePag)));
    }

    // ── comparison helpers (verbatim PKE8) ──

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

    private static String violationEntry(String bucket, String modelId, Graph trueMag,
                                         Graph truePag, Graph terminal, String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== VIOLATION [").append(bucket).append("] ====\n");
        sb.append("  config            : mode=").append(MODE)
                .append(" depth=").append(DEPTH)
                .append(" recursiveDepth=").append(RECURSIVE_DEPTH).append('\n');
        sb.append("  model             : ").append(modelId)
                .append("   positionalKey=").append(positionalKey(truePag)).append('\n');
        sb.append("  oracle MAG (Zhang MAG of G*):\n").append(trueMag).append('\n');
        sb.append("  true PAG G*:\n").append(truePag).append('\n');
        if (terminal != null) {
            sb.append("  terminal PAG from Fcit:\n").append(terminal).append('\n');
        }
        sb.append(detail);
        sb.append("==== end entry ====\n");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // SUMMARY
    // ────────────────────────────────────────────────────────────────────────

    private static String summarize(Result t, long elapsedMs, String violationLogPath) {
        long exact = t.exact.sum();
        long equivNotExact = t.equivNotExact.sum();
        long orientDiff = t.skeletonMatchOrientDiff.sum();
        long skelDiff = t.skeletonDiff.sum();
        long error = t.error.sum();
        long violations = equivNotExact + orientDiff + skelDiff + error;
        StringBuilder sb = new StringBuilder();
        sb.append("==== pke12 SUMMARY ====\n");
        sb.append(CONFIG_LINE).append('\n');
        sb.append(String.format("distinct true PAGs (this run's view): %d%n", t.distinctPags));
        sb.append(String.format("PAGs analyzed (Fcit runs)        : %d%n", t.pagsScanned.sum()));
        sb.append(String.format("  skipped on exception             : %d%n", t.skipped.sum()));
        sb.append(String.format("stopped early (log full)           : %b%n", STOP.get()));
        sb.append("---- TERMINAL-PAG IDENTITY (Fcit from GRaSP start, oracle) ----\n");
        sb.append(String.format("EXACT (terminal PAG == G*)         : %d%n", exact));
        sb.append(String.format("VIOLATIONS (terminal PAG != G*)    : %d%s%n", violations,
                violations == 0 ? "   *** clean: Fcit recovers G* on every distinct PAG ***" : ""));
        sb.append(String.format("  EQUIVALENT_NOT_EXACT             : %d%s%n", equivNotExact,
                equivNotExact > 0 ? "   (Markov-equivalent to G*; orientation/printing nit)" : ""));
        sb.append(String.format("  SKELETON_MATCH_ORIENT_DIFF       : %d%s%n", orientDiff,
                orientDiff > 0 ? "   *** non-equivalent orientation error ***" : ""));
        sb.append(String.format("  SKELETON_DIFF                    : %d%s%n", skelDiff,
                skelDiff > 0 ? "   *** wrong adjacencies (undeletable spurious / over-deletion) ***" : ""));
        sb.append(String.format("    of which extra edges present   : %d%n", t.skeletonExtra.sum()));
        sb.append(String.format("    of which edges missing         : %d%n", t.skeletonMissing.sum()));
        sb.append(String.format("  ERROR (threw / timed out)        : %d%s%n", error,
                error > 0 ? "   *** see violation log ***" : ""));
        sb.append("---- THE ANSWER ----\n");
        sb.append(violations == 0
                ? "Fcit recovers G* EXACTLY on all " + t.pagsScanned.sum()
                + " distinct true PAGs analyzed this run at this scope ("
                + (MODE.startsWith("dag")
                ? "dagsweep N=" + N + " |L|=" + NUM_LATENT
                : "ALL PAGs on " + OBS + " observed variables") + ").\n"
                + (STOP.get() ? "" : "")
                : "Fcit did NOT recover G* on " + violations + " distinct PAG(s) -- see "
                + violationLogPath + ".\n");
        sb.append(String.format("elapsed (enumeration+analysis)     : %.1f min%n", elapsedMs / 60000.0));
        sb.append("==== END SUMMARY ====");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // STREAMED, CAPPED LOG (append across resumes; header written once)
    // ────────────────────────────────────────────────────────────────────────

    private static final class StreamLog {
        private final PrintWriter out;
        private final int max;
        private int count;

        StreamLog(String path, int max, String header) throws IOException {
            // Append: on a resumed run, already-completed blocks are skipped and their
            // models are not re-analyzed, so violations logged in earlier runs must not be
            // truncated away. The header (with a fresh run-started line) is appended each
            // run as a separator; the cap counts THIS run's entries.
            boolean existed = Files.exists(Paths.get(path));
            this.out = new PrintWriter(new FileWriter(path, true));
            this.max = max;
            if (existed) out.println();   // blank line between runs
            out.println(header);
            out.flush();
        }

        synchronized boolean write(String entry) {
            if (count >= max) return false;
            out.println(entry);
            out.flush();
            count++;
            if (count == max) {
                out.println("==== cap of " + max + " entries reached (this run); log closed ====");
                out.flush();
                if (STOP.compareAndSet(false, true)) {
                    System.err.println("Violation log full -- stopping analysis early.");
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

    // ────────────────────────────────────────────────────────────────────────
    // RESULT ACCUMULATOR (thread-safe: a single shared instance across the
    // parallel enumeration streams, so counters are LongAdders)
    // ────────────────────────────────────────────────────────────────────────

    private static final class Result {
        // Set once, single-threaded, after enumeration; a plain field is fine.
        long distinctPags;
        final LongAdder pagsScanned = new LongAdder();
        final LongAdder skipped = new LongAdder();
        final LongAdder exact = new LongAdder();
        final LongAdder equivNotExact = new LongAdder();
        final LongAdder skeletonMatchOrientDiff = new LongAdder();
        final LongAdder skeletonDiff = new LongAdder();
        final LongAdder error = new LongAdder();
        final LongAdder skeletonExtra = new LongAdder();
        final LongAdder skeletonMissing = new LongAdder();
    }
}
