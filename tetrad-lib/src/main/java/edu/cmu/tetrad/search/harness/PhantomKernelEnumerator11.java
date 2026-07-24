/// ////////////////////////////////////////////////////////////////////////////
// PhantomKernelEnumerator11.java  (parallel, PAG-space, end-to-end FcitSl)    //
//                                                                             //
// PKE11 = PKE8's QUESTION over PKE10's POPULATION.                            //
//                                                                             //
// PKE8 asked the single thing that matters for correctness -- does the actual //
// shipping FcitSl, from its own GRaSP start, driven by the m-separation       //
// oracle, terminate at exactly G*? -- but paid for it with the DAG x latent-  //
// placement sweep (2^21 masks x C(7,2) placements, deduplicated to 2,691      //
// classes at N=7/|L|=2), which does not scale.                                //
//                                                                             //
// PKE10 observed that everything measured depends on the model ONLY through   //
// the true PAG (every oracle query is over observed variables, and the PAG    //
// determines the observed m-separation model), and therefore enumerated       //
// DISTINCT TRUE PAGs directly.  The same observation applies verbatim to      //
// PKE8's question: FcitSl sees only the oracle, and the oracle is a function  //
// of the true PAG's Markov class.  So PKE11 enumerates distinct true PAGs a   //
// la PKE10 and runs PKE8's end-to-end check once per PAG:                     //
//                                                                             //
//     terminal PAG returned by FcitSl.search()  ==  G* (the true PAG)?        //
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
// PAG, not per model, so totals are NOT commensurable with PKE8's per-class   //
// counts unless the populations coincide (dagsweep at PKE8's N/|L| with       //
// MERGE_ISOMORPHS on should reproduce PKE8's class count).  Zero-counts are   //
// unaffected: a violation present under one labelling is present under all,  //
// so a zero here is a zero there.                                             //
//                                                                             //
// ORACLE DRIVE (one uniformity change from PKE8): the true MAG for the        //
// oracle is obtained as zhangMagFromPag(G*) in BOTH modes, rather than        //
// dagToMag(dag) in dagsweep.  Any MAG in G*'s equivalence class induces the   //
// same observed m-separation model, so the oracle is identical; deriving it   //
// from the PAG keeps the analysis a single mode-independent code path (this   //
// is exactly PKE10's analyze() convention).  FcitSl is constructed with the   //
// MsepTest on that MAG as test and a GraphScore on the same MAG as score,     //
// which flips its startWith to GRASP -- the Oracle GRaSP path, as in PKE8.    //
//                                                                             //
// SELECTION BIAS: a single EXCLUDE_SELECTION_BIAS constant (default true,     //
// matching PKE10) is applied consistently to the enumeration projections AND  //
// to FcitSl, since G* and the search must operate under the same assumption.  //
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
//   ERROR              : FcitSl threw or timed out.                           //
// Only EXACT is a pass; every other bucket is a VIOLATION, logged in full.    //
//                                                                             //
// Canonicalization is PKE10's two-stage scheme.  Stage 1 keys on the          //
// positional edge string after relabelling observed nodes to V1..VOBS in      //
// name order.  Stage 2 (MERGE_ISOMORPHS) canonicalizes survivors under all    //
// OBS! permutations and merges isomorphs.  FcitSl's verdict is relabelling-   //
// invariant (the oracle, the start, and the comparison all commute with a     //
// renaming), so stage 2 is sound; disable it for per-labelling counts.        //
//                                                                             //
// CHECKPOINTING: the analysis phase (one FcitSl run per distinct PAG) is cheap //
// and uncheckpointed, as PKE8's per-class work was.  The magspace ENUMERATION  //
// phase is not: at OBS=6 it is 4^15 = 2^30 candidates, 1024x the OBS=5 sweep,  //
// which does not finish in one sitting.  It is therefore block-checkpointed    //
// (see MAGSPACE_BLOCK_SIZE below and args [5]/[6]) so a run can be killed and  //
// resumed.  dagsweep's enumeration (2^p masks, 2^21 at N=7) is left            //
// uncheckpointed, as in PKE10.  The distinct list is sorted by positional key  //
// before analysis so PAG#i ids are stable across runs.  The violation log is   //
// streamed and capped as in PKE8; filling it stops the analysis early.        //
//                                                                             //
// args: [0]=mode (dagsweep|magspace, default magspace)                        //
//       [1]=N (dagsweep only, default 7)  [2]=numLatent (dagsweep, default 2) //
//       [3]=observed count (magspace only, default 5)                         //
//       [4]=violation log path (default pke11_violations.log)                 //
//       [5]=magspace checkpoint path (default pke11_magspace_obs<OBS>_        //
//           checkpoint.txt) -- delete to restart enumeration from scratch     //
//       [6]=magspace distinct-PAG keys path (default pke11_magspace_obs<OBS>_ //
//           keys.txt) -- holds every distinct PAG found so far; do not delete //
//           this without also deleting [5], or resume will silently re-derive //
//           only the blocks it reruns and miss the ones it skips              //
//                                                                             //
// @author josephramsey (harness scaffolding by Claude)                        //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FcitSl;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.MagToPag;
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
 * PKE11: enumerate DISTINCT TRUE PAGs (magspace or dagsweep, as in PKE10) and, once per
 * PAG, run the real FcitSl from a GRaSP start, oracle-driven, reporting every PAG whose
 * terminal graph is not exactly G* (PKE8's end-to-end check over PKE10's population).
 */
public final class PhantomKernelEnumerator11 {

    /**
     * Default constructor.
     */
    public PhantomKernelEnumerator11() {
    }

    // ────────────────────────────────────────────────────────────────────────
    // CONFIGURATION (enumeration knobs via args, FcitSl knobs hard-coded)
    // ────────────────────────────────────────────────────────────────────────

    private static String MODE       = "magspace";
    private static int    N          = 7;    // dagsweep only
    private static int    NUM_LATENT = 2;    // dagsweep only
    private static int    OBS        = 5;

    /** Stage 2: merge isomorphic PAGs under all OBS! relabellings.  FcitSl's verdict is
     *  relabelling-invariant, so merging is sound; disable for per-labelling counts.
     *  With dagsweep at PKE8's N/|L| and this ON, distinct count should anchor at 2,691. */
    private static final boolean MERGE_ISOMORPHS = false;

    // ── FcitSl configuration (verbatim PKE8; match the shipping defaults to certify) ──
    /** Step-Lemma-pure mode: no out-of-class escape.  Set false to certify the
     *  full shipping algorithm including the fork-flip fallback. */
    private static final boolean ALLOW_CLASS_ESCAPE = false;
    /** FcitSl commit-gate battery bound. */
    private static final int BATTERY_Z_MAX = 5;
    /** FcitSl fork-flip bound (only relevant when ALLOW_CLASS_ESCAPE). */
    private static final int MAX_FORK_FLIPS = 2;
    /** Per-run FcitSl timeout, ms; -1 = unlimited.  A positive value turns a
     *  hang into an ERROR verdict instead of stalling the whole run. */
    private static final long FCIT_TIMEOUT_MS = -1L;
    /** Depth / recursive-depth passed to FcitSl (-1 = unlimited). */
    private static final int DEPTH = -1;
    private static final int RECURSIVE_DEPTH = -1;
    /** Applied consistently to the enumeration projections AND FcitSl (see header). */
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
    //   keys file       : every distinct PAG found so far, as its positional key,
    //                      one per line, appended as discovered. A positional key
    //                      is fully invertible (decodeGraph), so this file alone
    //                      reconstructs the Graph objects for blocks that get
    //                      SKIPPED on resume -- without it, resuming would only
    //                      recover which blocks are "done", not what they found.
    /** Candidates per checkpoint block. 2^20 -> 1024 blocks at OBS=6 (2^30 total),
     *  1 block at OBS=5 (2^20 total), 4096 blocks at OBS=7 (2^42 total). */
    private static final long MAGSPACE_BLOCK_SIZE = 1L << 20;

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
        String violationLogPath = (args.length > 4) ? args[4] : "pke11_violations.log";

        boolean dagSweep = MODE.startsWith("dag");
        if (dagSweep) OBS = N - NUM_LATENT;

        // Magspace-only; defaults are mode/OBS-scoped so different OBS runs don't collide.
        String checkpointPath = (args.length > 5) ? args[5]
                : "pke11_magspace_obs" + OBS + "_checkpoint.txt";
        String keysPath = (args.length > 6) ? args[6]
                : "pke11_magspace_obs" + OBS + "_keys.txt";

        CANON = new ArrayList<>();
        for (int i = 0; i < OBS; i++) CANON.add(new GraphNode("V" + (i + 1)));

        CONFIG_LINE = String.format(
                "# PKE11 config: mode=%s N=%d latent=%d observed=%d mergeIsomorphs=%b "
                        + "escape=%b zMax=%d forkFlips=%d timeoutMs=%d depth=%d recDepth=%d "
                        + "exclSel=%b",
                dagSweep ? "dagsweep" : "magspace", N, NUM_LATENT, OBS, MERGE_ISOMORPHS,
                ALLOW_CLASS_ESCAPE, BATTERY_Z_MAX, MAX_FORK_FLIPS, FCIT_TIMEOUT_MS,
                DEPTH, RECURSIVE_DEPTH, EXCLUDE_SELECTION_BIAS);

        System.err.println(CONFIG_LINE);
        System.err.printf("threads~%d%n", Runtime.getRuntime().availableProcessors());

        // ── Enumeration phase ──
        long tEnum = System.currentTimeMillis();
        List<Graph> distinct = dagSweep ? enumerateByDagSweep()
                : enumerateByMagSpace(checkpointPath, keysPath);
        // Stable PAG#i ids across runs: sort by the positional key.
        distinct.sort(Comparator.comparing(PhantomKernelEnumerator11::positionalKey));
        System.err.printf("enumeration: %d distinct true PAGs in %.1f s%n",
                distinct.size(), (System.currentTimeMillis() - tEnum) / 1000.0);

        // ── Analysis phase ──
        String header = CONFIG_LINE + "\n# run started " + new Date()
                + "\n# VIOLATIONS: every distinct true PAG whose terminal FcitSl PAG (GRaSP start,"
                + "\n# oracle-driven) is NOT exactly G*.  Bucket legend:"
                + "\n#   EQUIVALENT_NOT_EXACT       -- Markov-equivalent to G* but not edge-identical"
                + "\n#   SKELETON_MATCH_ORIENT_DIFF -- same skeleton, non-equivalent orientation error"
                + "\n#   SKELETON_DIFF              -- adjacencies differ (extra = undeletable spurious,"
                + "\n#                                 missing = over-deletion)"
                + "\n#   ERROR                      -- FcitSl threw or timed out";
        violationLog = new StreamLog(violationLogPath, VIOLATION_LOG_MAX, header);

        ANALYSIS_T0 = System.currentTimeMillis();
        Result total = LongStream.range(0, distinct.size())
                .parallel()
                .collect(Result::new,
                        (r, i) -> analyze(r, distinct.get((int) i), "PAG#" + i),
                        Result::merge);
        total.distinctPags = distinct.size();

        String summary = summarize(total, System.currentTimeMillis() - ANALYSIS_T0,
                violationLogPath);
        System.out.println(summary);
        violationLog.summary("\n" + summary);
        violationLog.close();
    }

    // ────────────────────────────────────────────────────────────────────────
    // ENUMERATION PHASE: distinct true PAGs (verbatim PKE10 machinery)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Mode dagsweep: every DAG on N vertices in a fixed order x every placement of
     * NUM_LATENT latents, deduplicated by true PAG.  Reproduces a DAG-sweep
     * population exactly; the dagToPag calls dominate the run.
     */
    private static List<Graph> enumerateByDagSweep() {
        int p = N * (N - 1) / 2;
        int[][] pair = new int[p][2];
        for (int idx = 0, i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++, idx++) { pair[idx][0] = i; pair[idx][1] = j; }
        }
        long totalMasks = 1L << p;
        System.err.printf("  dagsweep: 2^%d = %d masks x C(%d,%d) latent placements%n",
                p, totalMasks, N, NUM_LATENT);

        Map<String, Graph> seen = new ConcurrentHashMap<>();
        AtomicLong done = new AtomicLong();

        LongStream.range(0, totalMasks).parallel().forEach(mask -> {
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
                    seen.putIfAbsent(positionalKey(canon), canon);
                } catch (Exception ignore) {
                    // A model that will not project is not a true PAG; skip it.
                }
            }
            long d = done.incrementAndGet();
            if ((d & 0x3FFF) == 0) {
                System.err.printf("  …mask %d/%d, %d distinct so far%n", d, totalMasks, seen.size());
            }
        });

        return mergeIsomorphs(new ArrayList<>(seen.values()));
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
     * positional key. On resume, keysPath is decoded back into Graph objects BEFORE
     * any block runs, so skipped (already-done) blocks still contribute to the
     * returned list -- the checkpoint alone would only tell you a block is done, not
     * what it found.
     */
    private static List<Graph> enumerateByMagSpace(String checkpointPath, String keysPath)
            throws IOException {
        int m = OBS * (OBS - 1) / 2;
        int[][] pr = new int[m][2];
        for (int idx = 0, i = 0; i < OBS; i++) {
            for (int j = i + 1; j < OBS; j++, idx++) { pr[idx][0] = i; pr[idx][1] = j; }
        }
        long total = 1L << (2 * m);   // 4^m
        long numBlocks = (total + MAGSPACE_BLOCK_SIZE - 1) / MAGSPACE_BLOCK_SIZE;
        String enumConfig = String.format(
                "# PKE11 magspace-enum config: OBS=%d blockSize=%d", OBS, MAGSPACE_BLOCK_SIZE);

        Set<Long> doneBlocks = loadCheckpoint(checkpointPath, enumConfig);
        Map<String, Graph> seen = new ConcurrentHashMap<>();
        loadPersistedKeys(keysPath, enumConfig, seen);
        KeyAppender keyOut = new KeyAppender(keysPath, enumConfig);

        System.err.printf("  magspace: 4^%d = %d candidate graphs on %d observed vertices | "
                        + "%d blocks of %d, %d already complete | %d distinct loaded from disk%n",
                m, total, OBS, numBlocks, MAGSPACE_BLOCK_SIZE, doneBlocks.size(), seen.size());

        for (long b = 0; b < numBlocks; b++) {
            if (doneBlocks.contains(b)) continue;
            long lo = b * MAGSPACE_BLOCK_SIZE;
            long hi = Math.min(total, lo + MAGSPACE_BLOCK_SIZE);

            Map<String, Graph> blockNew = new ConcurrentHashMap<>();
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
                    String key = positionalKey(canon);
                    if (!seen.containsKey(key)) {
                        blockNew.putIfAbsent(key, canon);
                    }
                } catch (Exception ignore) {
                    // Not a legal MAG / will not project; skip.
                }
            });

            // Merge into the global map and persist newly-added keys ONLY (dedup against
            // `seen`, which already reflects everything on disk plus every prior block
            // this run). Flush + checkpoint the block together so a crash between the two
            // just makes the next resume recompute this block -- idempotent, since
            // `seen.containsKey` above skips anything already found.
            int newCount = 0;
            for (Map.Entry<String, Graph> e : blockNew.entrySet()) {
                if (seen.putIfAbsent(e.getKey(), e.getValue()) == null) {
                    keyOut.write(e.getKey());
                    newCount++;
                }
            }
            keyOut.flush();
            appendCheckpoint(checkpointPath, b);

            System.err.printf("  block %d/%d done | +%d new this block | %d distinct so far%n",
                    b + 1, numBlocks, newCount, seen.size());
        }
        keyOut.close();

        return mergeIsomorphs(new ArrayList<>(seen.values()));
    }

    /** Stage 2: merge isomorphs by minimizing the edge string over all OBS! permutations. */
    private static List<Graph> mergeIsomorphs(List<Graph> stage1) {
        if (!MERGE_ISOMORPHS) return stage1;
        System.err.printf("  stage 1: %d distinct labellings; merging isomorphs…%n", stage1.size());
        Map<String, Graph> byCanon = new LinkedHashMap<>();
        for (Graph g : stage1) byCanon.putIfAbsent(canonicalKey(g), g);
        System.err.printf("  stage 2: %d distinct up to relabelling%n", byCanon.size());
        return new ArrayList<>(byCanon.values());
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

    /** Edge string under the identity order: cheap, labelling-sensitive. */
    private static String positionalKey(Graph g) {
        return encode(g, CANON);
    }

    /** Lexicographically least edge string over all permutations of the vertices. */
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
    // MAGSPACE ENUMERATION CHECKPOINTING (block ids + invertible distinct-PAG keys)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Invert {@link #encode}: a positional key determines its graph completely (fixed
     * pair order over CANON, one '.' per absent pair, two endpoint-mark chars per present
     * pair), so this exactly reconstructs the Graph a key was produced from. This is what
     * lets a resumed run recover the contribution of a block it chooses to SKIP.
     */
    private static Graph decodeGraph(String key) {
        Graph g = new EdgeListGraph(CANON);
        int pos = 0;
        for (int i = 0; i < OBS; i++) {
            for (int j = i + 1; j < OBS; j++) {
                char c1 = key.charAt(pos);
                if (c1 == '.') { pos += 1; continue; }
                char c2 = key.charAt(pos + 1);
                pos += 2;
                Node a = CANON.get(i), b = CANON.get(j);
                g.addEdge(new Edge(a, b, decodeMark(c1), decodeMark(c2)));
            }
        }
        return g;
    }

    private static Endpoint decodeMark(char c) {
        switch (c) {
            case 'a': return Endpoint.ARROW;
            case 't': return Endpoint.TAIL;
            case 'c': return Endpoint.CIRCLE;
            default: throw new IllegalArgumentException("bad endpoint mark in key: " + c);
        }
    }

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

    /** Load every previously-discovered distinct PAG from the keys file into `into`,
     *  decoding each key back into a Graph. No-op if the file doesn't exist yet. */
    private static void loadPersistedKeys(String path, String expectedConfig,
                                          Map<String, Graph> into) throws IOException {
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
                into.put(line, decodeGraph(line));
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

    // ────────────────────────────────────────────────────────────────────────
    // ANALYSIS PHASE: PKE8's end-to-end FcitSl check, once per distinct true PAG
    // ────────────────────────────────────────────────────────────────────────

    private static void analyze(Result r, Graph truePag, String modelId) {
        if (STOP.get()) return;
        r.pagsScanned++;
        try {
            // The oracle is m-separation in a MAG of G*'s equivalence class.  Any MAG
            // in the class induces the same observed m-separation model, so deriving
            // it from the PAG (Zhang completion) makes the analysis mode-independent:
            // FcitSl cannot tell two models sharing a true PAG apart.  This replaces
            // PKE8's dagToMag(dag) with the same oracle.
            Graph trueMag = GraphTransforms.zhangMagFromPag(truePag);
            runFcit(r, modelId, trueMag, truePag, CANON);
        } catch (Exception ex) {
            r.skipped++;
            if (ERR_PRINTS.incrementAndGet() <= 5) {
                System.err.println(modelId + " skipped: " + ex);
                ex.printStackTrace();
            }
        } finally {
            long d = ANALYZED.incrementAndGet();
            if (d % PROGRESS_EVERY == 0) {
                System.err.printf("  analyzed %d PAGs | EXACT=%d EQUIV=%d ORIENT=%d SKEL=%d ERR=%d "
                                + "| %.1f min%n",
                        d, r.exact, r.equivNotExact, r.skeletonMatchOrientDiff, r.skeletonDiff,
                        r.error, (System.currentTimeMillis() - ANALYSIS_T0) / 60000.0);
            }
        }
    }

    private static void runFcit(Result r, String modelId, Graph trueMag, Graph truePag,
                                List<Node> obs) {
        // Oracle drive: MsepTest on the true MAG as both test and score basis.  FcitSl's
        // constructor flips startWith to GRASP when the test is an MsepTest, so this is the
        // Oracle GRaSP path.  A fresh graph per FcitSl (it mutates node types internally).
        Graph magForTest = new EdgeListGraph(trueMag);
        Graph magForScore = new EdgeListGraph(trueMag);
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
            violationLog.write(violationEntry("ERROR", modelId, trueMag, truePag, null,
                    "FcitSl threw: " + ex));
            if (ERR_PRINTS.incrementAndGet() <= 10) {
                System.err.println("FcitSl error " + modelId + ": " + ex);
            }
            return;
        }

        // Normalize terminal to the canonical observed nodes (FcitSl returns a PAG over
        // the test's variables, which are the trueMag nodes -- same names V1..VOBS).
        Graph term = GraphUtils.replaceNodes(terminal, obs);

        // Primary verdict: exact edge+endpoint identity with G*.
        if (graphsIdentical(term, truePag)) {
            r.exact++;
            return;
        }

        // Non-exact: classify.  Skeleton first.
        boolean sameSkeleton = sameSkeleton(term, truePag);
        String bucket;
        if (!sameSkeleton) {
            r.skeletonDiff++;
            int[] xd = skeletonDelta(term, truePag);
            r.skeletonExtra += (xd[0] > 0 ? 1 : 0);
            r.skeletonMissing += (xd[1] > 0 ? 1 : 0);
            bucket = "SKELETON_DIFF";
        } else if (markovEquivalent(term, truePag, obs)) {
            r.equivNotExact++;
            bucket = "EQUIVALENT_NOT_EXACT";
        } else {
            r.skeletonMatchOrientDiff++;
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
                .append(" escape=").append(ALLOW_CLASS_ESCAPE)
                .append(" zMax=").append(BATTERY_Z_MAX)
                .append(" forkFlips=").append(MAX_FORK_FLIPS)
                .append(" depth=").append(DEPTH)
                .append(" recursiveDepth=").append(RECURSIVE_DEPTH).append('\n');
        sb.append("  model             : ").append(modelId)
                .append("   positionalKey=").append(positionalKey(truePag)).append('\n');
        sb.append("  oracle MAG (Zhang MAG of G*):\n").append(trueMag).append('\n');
        sb.append("  true PAG G*:\n").append(truePag).append('\n');
        if (terminal != null) {
            sb.append("  terminal PAG from FcitSl:\n").append(terminal).append('\n');
        }
        sb.append(detail);
        sb.append("==== end entry ====\n");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // SUMMARY
    // ────────────────────────────────────────────────────────────────────────

    private static String summarize(Result t, long elapsedMs, String violationLogPath) {
        long violations = t.equivNotExact + t.skeletonMatchOrientDiff + t.skeletonDiff + t.error;
        StringBuilder sb = new StringBuilder();
        sb.append("==== PKE11 SUMMARY ====\n");
        sb.append(CONFIG_LINE).append('\n');
        sb.append(String.format("distinct true PAGs enumerated      : %d%s%n", t.distinctPags,
                MODE.startsWith("dag") && MERGE_ISOMORPHS
                        ? "   (dagsweep+merge: comparable to PKE8's class count)" : ""));
        sb.append(String.format("PAGs analyzed                      : %d%n", t.pagsScanned));
        sb.append(String.format("  skipped on exception             : %d%n", t.skipped));
        sb.append(String.format("stopped early (log full)           : %b%n", STOP.get()));
        sb.append("---- TERMINAL-PAG IDENTITY (FcitSl from GRaSP start, oracle) ----\n");
        sb.append(String.format("EXACT (terminal PAG == G*)         : %d%n", t.exact));
        sb.append(String.format("VIOLATIONS (terminal PAG != G*)    : %d%s%n", violations,
                violations == 0 ? "   *** clean: FcitSl recovers G* on every distinct PAG ***" : ""));
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
                + ") recovers G* EXACTLY on all " + t.distinctPags + " distinct true PAGs at this scope ("
                + (MODE.startsWith("dag")
                ? "dagsweep N=" + N + " |L|=" + NUM_LATENT
                : "ALL PAGs on " + OBS + " observed variables") + ").\n"
                : "FcitSl did NOT recover G* on " + violations + " distinct PAG(s) -- see "
                + violationLogPath + ".\n");
        sb.append(String.format("elapsed (analysis phase)           : %.1f min%n", elapsedMs / 60000.0));
        sb.append("==== END SUMMARY ====");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // STREAMED, CAPPED LOG (verbatim PKE8)
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
    // RESULT ACCUMULATOR
    // ────────────────────────────────────────────────────────────────────────

    private static final class Result {
        long distinctPags, pagsScanned, skipped;
        long exact, equivNotExact, skeletonMatchOrientDiff, skeletonDiff, error;
        long skeletonExtra, skeletonMissing;

        static void merge(Result a, Result b) {
            a.add(b);
        }

        void add(Result o) {
            distinctPags += o.distinctPags;
            pagsScanned += o.pagsScanned;
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
