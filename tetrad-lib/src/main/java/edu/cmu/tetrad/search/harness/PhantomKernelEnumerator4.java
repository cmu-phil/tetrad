/// ////////////////////////////////////////////////////////////////////////////
// PhantomKernelEnumerator4.java  (parallel, deduplicated, checkpointed)       //
//                                                                             //
// Trimmed successor to PhantomKernelEnumerator2, specialized to the two       //
// verification targets of the RB paper's reachability section, at the larger  //
// scope N=7, |L|=2, |Spur| <= 3:                                              //
//                                                                             //
//   (1) STALL TEST (Proposition prop:stall).  A counterexample is a legal     //
//       I-map (Markov) H0 = cold-reorient(G* + spurious subset) from which    //
//       NO single spurious deletion yields a legal cold reorientation.  On    //
//       each such canonical stall, an optional REPRESENTATIVE SWEEP escalates //
//       the test: does SOME Markov-equivalent MAG representative of [H0]      //
//       (all representatives, or LEGs only) admit a spurious deletion whose   //
//       result is a legal MAG and an I-map of G*?  A stall that SURVIVES the  //
//       sweep is a Step-Lemma counterexample candidate -- the prize.          //
//                                                                             //
//   (2) STEP-BREAK TEST (Proposition prop:committed-markov).  A               //
//       counterexample is an I-map H0 with a LEGAL single-edge cold           //
//       remove+reorient H1 that is NOT an I-map of G* (a legal non-Markov     //
//       committed waypoint).  No guard filtering: legality is the only gate,  //
//       matching the raw process the proposition quantifies over.            //
//                                                                             //
// GATE CHANGE vs PKE2: H0 population is legal AND I-map (the population the   //
// propositions quantify over).  The genuineness (phantom) gate and all        //
// phantom/R0/responsibility/robust-R0/teleport instrumentation are removed.   //
// The tested population is therefore a superset of PKE2's gated population;   //
// counts are not comparable across harness versions.                          //
//                                                                             //
// DEDUPLICATION (the optimization that makes N=7/2-latent feasible):          //
// every downstream quantity is a function of the true MAG over the 5 observed //
// variables alone, once the oracle is switched from MsepTest(dag) to          //
// MsepTest(trueMag) -- exactness-preserving, since d-separation over observed //
// sets in the latent DAG coincides with m-separation in its MAG projection    //
// (no selection).  Per model we compute only GraphTransforms.dagToMag,        //
// canonicalize (relabel observed to V1..V5 + minimize over the 120 node       //
// permutations), and claim the key in a ConcurrentHashMap; the full pipeline  //
// runs once per distinct canonical MAG.  Witnesses record one exemplar        //
// (mask, latent set, relabeling) per key.  Summaries report both raw models   //
// scanned and distinct MAGs processed.                                        //
//                                                                             //
// LOGGING: examples are streamed to their logs AS FOUND, with a flush after   //
// every write, so an early kill loses nothing already logged.  Each capped    //
// log stops accepting entries at its hard-coded max; when ALL capped logs are //
// full, enumeration stops early.  Summaries are appended at the END of each   //
// log on normal or early-stop exit (an external kill leaves no summary, by    //
// design).  The FIRST stall counterexample that survives the strongest        //
// configured test is duplicated into its own special log.                     //
//                                                                             //
// CHECKPOINTING: the 2^21 mask space is processed in blocks; completed block  //
// ids are appended to a checkpoint file.  On restart, completed blocks are    //
// skipped.  Delete the checkpoint file to restart from scratch.  The          //
// checkpoint header records the configuration; a mismatch on load fails       //
// loudly (stale checkpoint from a different configuration).  Notes: (a) the   //
// dedup map is rebuilt per run, so a resumed run may recompute keys already   //
// processed in an earlier run -- harmless but it can re-log the same exemplar //
// across runs (logs are opened in append mode); (b) counters and caps are     //
// per-run.                                                                    //
//                                                                             //
// PARALLELISM: per-block parallel LongStream over DAG masks with thread-      //
// confined Result accumulators (supplier/accumulator/combiner), as in PKE2.   //
// Control thread count with                                                   //
//   -Djava.util.concurrent.ForkJoinPool.common.parallelism=K                  //
// FciOrient.setParallel(false) is kept: no nested parallelism.  Claim-first   //
// dedup means exactly one thread processes each distinct key, so no witness   //
// is double-logged within a run.  Counter totals are deterministic; WHICH     //
// exemplar (mask, latSet) claims a key may vary run to run.                   //
//                                                                             //
// All parameters are hard-coded constants below; no command-line arguments.   //
// Run from IntelliJ; logs and the checkpoint go to the working directory.     //
//                                                                             //
// FLAGGED API ASSUMPTIONS (verify once against your Tetrad):                  //
//   * GraphTransforms.dagToMag(dag) returns the MAG over the OBSERVED         //
//     variables (latents marginalized), mirroring dagToPag.  A defensive      //
//     check throws if the node count or types disagree.                       //
//   * The Zhang MAG of a legal PAG is itself a LEG (circle marks resolve to   //
//     tails/arrows without new bidirected edges), so the representative sweep //
//     must find >= 1 LEG for every legal H0; zero found = machinery drift,    //
//     and the sweep fails loudly rather than miscounting.                     //
//                                                                             //
// @author josephramsey (harness scaffolding by Claude)                        //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
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

public final class PhantomKernelEnumerator4 {

    // ────────────────────────────────────────────────────────────────────────
    // CONFIGURATION (all hard-coded; edit and re-run)
    // ────────────────────────────────────────────────────────────────────────

    /** Total nodes in the enumerated DAGs. */
    private static final int N = 7;
    /** Number of latent nodes per placement. */
    private static final int NUM_LATENT = 2;
    /** Spurious subsets of sizes 1..MAX_SPURIOUS are enumerated per model. */
    private static final int MAX_SPURIOUS = 3;

    /** Representative-sweep scope applied to canonical stalls.
     *  NONE     : no sweep; every canonical stall counts as surviving.
     *  LEG_ONLY : sweep over the LEGs of [H0] (bidirected edges = H0's).
     *  ALL      : sweep over every Markov-equivalent MAG representative of [H0].
     *  (The still-richer bryan-style move set -- any legal I-map MAG on the
     *  skeleton, equivalence not required -- is deliberately out of scope.) */
    private enum RepScope {NONE, LEG_ONLY, ALL}

    private static final RepScope REPRESENTATIVE_SCOPE = RepScope.LEG_ONLY;

    /** Canonicalize dedup keys over all 120 permutations of the observed
     *  nodes (max dedup).  false = relabel-by-sorted-index only. */
    private static final boolean CANONICALIZE_PERMS = true;

    // Log files (created/appended in the current working directory).
    /** Canonical stalls (legal I-map H0, no legal single deletion), each
     *  annotated with its representative-sweep verdict. */
    private static final String STALL_LOG_PATH = "pke4_stall_counterexamples.log";
    /** The first stall that SURVIVES the strongest configured test. */
    private static final String FIRST_STALL_LOG_PATH = "pke4_first_stall_counterexample.log";
    /** Legal non-I-map single-edge waypoints from I-map H0s. */
    private static final String STEP_BREAK_LOG_PATH = "pke4_step_imap_breaks.log";
    /** Completed block ids for resume. Delete to restart from scratch. */
    private static final String CHECKPOINT_PATH = "pke4_checkpoint.txt";

    // Per-log caps, counted in EXAMPLES (not bytes), per run.  A full log goes
    // silent; when ALL capped logs are full, enumeration stops early.
    private static final int STALL_LOG_MAX = 200;
    private static final int STEP_BREAK_LOG_MAX = 500;

    /** Masks per checkpoint block: 2^12 = 4096 -> 512 blocks over 2^21 masks. */
    private static final long BLOCK_SIZE = 1L << 12;

    // FCI/RB knobs (match PKE2's cold pipeline).
    private static final int MAX_LEN = -1;
    private static final int DEPTH = -1;
    private static final int RECURSIVE_DEPTH = -1;
    private static final long TIMEOUT = -1L;
    private static final boolean EXCLUDE_SELECTION_BIAS = true;

    // ────────────────────────────────────────────────────────────────────────
    // DERIVED CONSTANTS AND SHARED STATE
    // ────────────────────────────────────────────────────────────────────────

    private static final int OBS = N - NUM_LATENT;
    private static final int P = N * (N - 1) / 2;
    private static final long TOTAL_DAGS = 1L << P;
    private static final int[][] PAIR = buildPairs();
    private static final List<int[]> PERMS = buildPerms();

    private static final String CONFIG_LINE = String.format(
            "# PKE4 config: N=%d latent=%d maxSpurious=%d scope=%s canonPerms=%b blockSize=%d",
            N, NUM_LATENT, MAX_SPURIOUS, REPRESENTATIVE_SCOPE, CANONICALIZE_PERMS, BLOCK_SIZE);

    /** Claimed canonical MAG keys.  Claim-first: exactly one thread runs the
     *  pipeline for each distinct key within a run. */
    private static final ConcurrentHashMap<String, Boolean> SEEN = new ConcurrentHashMap<>();

    private static final AtomicBoolean STOP = new AtomicBoolean(false);
    private static final AtomicBoolean FIRST_STALL_WRITTEN = new AtomicBoolean(false);
    private static final AtomicLong ERR_PRINTS = new AtomicLong();

    private static StreamLog stallLog;
    private static StreamLog stepLog;

    // ────────────────────────────────────────────────────────────────────────
    // MAIN
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        long numBlocks = (TOTAL_DAGS + BLOCK_SIZE - 1) / BLOCK_SIZE;
        Set<Long> doneBlocks = loadCheckpoint();

        System.err.println(CONFIG_LINE);
        System.err.printf("observed=%d | dags=2^%d=%d | latentPlacements=C(%d,%d)=%d | models=%d%n",
                OBS, P, TOTAL_DAGS, N, NUM_LATENT, choose(N, NUM_LATENT),
                TOTAL_DAGS * choose(N, NUM_LATENT));
        System.err.printf("blocks=%d (size %d), %d already complete from checkpoint | threads~%d%n",
                numBlocks, BLOCK_SIZE, doneBlocks.size(),
                Runtime.getRuntime().availableProcessors());

        String header = CONFIG_LINE + "\n# run started " + new Date()
                + " | resuming with " + doneBlocks.size() + "/" + numBlocks + " blocks already complete"
                + " | caps: stall=" + STALL_LOG_MAX + " stepBreak=" + STEP_BREAK_LOG_MAX
                + " (per run; entries streamed and flushed as found)";
        stallLog = new StreamLog(STALL_LOG_PATH, STALL_LOG_MAX, header
                + "\n# CANONICAL STALLS: legal I-map H0 with no legal single-edge cold deletion,"
                + "\n# each annotated with the representative-sweep verdict (scope=" + REPRESENTATIVE_SCOPE + ").");
        stepLog = new StreamLog(STEP_BREAK_LOG_PATH, STEP_BREAK_LOG_MAX, header
                + "\n# STEP BREAKS: I-map H0, LEGAL single-edge cold remove+reorient H1, H1 NOT an"
                + "\n# I-map of G* (legal non-Markov committed waypoint; legality is the only gate).");

        Result total = new Result();
        long t0 = System.currentTimeMillis();
        long blocksThisRun = 0;

        for (long b = 0; b < numBlocks; b++) {
            if (STOP.get()) break;
            if (doneBlocks.contains(b)) continue;

            long lo = b * BLOCK_SIZE;
            long hi = Math.min(TOTAL_DAGS, lo + BLOCK_SIZE);

            Result blockRes = LongStream.range(lo, hi)
                    .parallel()
                    .collect(Result::new, PhantomKernelEnumerator4::accumulate, Result::merge);
            total.add(blockRes);
            blocksThisRun++;

            if (STOP.get()) break;   // block may be partial once STOP fired: do NOT checkpoint it
            appendCheckpoint(b);

            System.err.printf("block %d done (%d this run; %d/%d overall) | models=%d distinct=%d dup=%d "
                            + "| gated=%d stalls=%d (surviving %d) stepBreaks=%d | keys=%d | %.1f min%n",
                    b, blocksThisRun, doneBlocks.size() + blocksThisRun, numBlocks,
                    total.modelsScanned, total.distinctMags, total.dupModels,
                    total.gated, total.canonicalStalls, total.stallSurvives, total.stepBreaks,
                    SEEN.size(), (System.currentTimeMillis() - t0) / 60000.0);
        }

        String summary = summarize(total, blocksThisRun, numBlocks, doneBlocks.size(),
                System.currentTimeMillis() - t0);
        System.out.println(summary);
        stallLog.summary("\n" + summary);
        stepLog.summary("\n" + summary);
        stallLog.close();
        stepLog.close();
    }

    private static String summarize(Result t, long blocksThisRun, long numBlocks, long resumedBlocks,
                                    long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== PKE4 SUMMARY (this run only; caps and counters are per-run) ====\n");
        sb.append(CONFIG_LINE).append('\n');
        sb.append(String.format("blocks processed this run          : %d (resumed past %d; total blocks %d)%n",
                blocksThisRun, resumedBlocks, numBlocks));
        sb.append(String.format("stopped early (all logs full)      : %b%n", STOP.get()));
        sb.append(String.format("dag masks scanned                  : %d%n", t.dagsScanned));
        sb.append(String.format("models scanned (mask x latent)     : %d%n", t.modelsScanned));
        sb.append(String.format("  duplicate canonical MAGs skipped : %d%n", t.dupModels));
        sb.append(String.format("  distinct canonical MAGs processed: %d (global key map: %d)%n",
                t.distinctMags, SEEN.size()));
        sb.append(String.format("  models skipped on exception      : %d%n", t.skipped));
        sb.append(String.format("H0 candidates (spurious subsets)   : %d%n", t.h0Candidates));
        sb.append(String.format("  refused (RB sepset gate)         : %d%n", t.h0RbFail));
        sb.append(String.format("  illegal after cold reorient      : %d%n", t.h0IllegalPag));
        sb.append(String.format("  legal but NOT I-map (excluded)   : %d%n", t.h0NotImap));
        sb.append(String.format("  GATED (legal I-map H0)           : %d%n", t.gated));
        sb.append(String.format("deletions attempted                : %d%n", t.delAttempts));
        sb.append(String.format("  refused (no confirmed separator) : %d%n", t.delNoSepset));
        sb.append(String.format("  legal                            : %d%n", t.delLegal));
        sb.append(String.format("  illegal                          : %d%n", t.delIllegal));
        sb.append(String.format("STEP BREAKS (legal non-I-map H1)   : %d (suppressed over cap: %d)%n",
                t.stepBreaks, t.stepBreakSuppressed));
        sb.append(String.format("CANONICAL STALLS (I-map H0)        : %d (suppressed over cap: %d)%n",
                t.canonicalStalls, t.stallSuppressed));
        sb.append(String.format("  rescued by representative sweep  : %d%n", t.stallRescued));
        sb.append(String.format("  SURVIVING (counterexample cand.) : %d%n", t.stallSurvives));
        sb.append(String.format("H0s with a legal escape            : %d%n", t.h0WithEscape));
        sb.append(String.format("elapsed                            : %.1f min%n", elapsedMs / 60000.0));
        sb.append("==== END SUMMARY ====");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // PER-MASK ACCUMULATOR
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
                // Build the latent DAG for this (mask, placement).
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

                // Cheap projection for the dedup key: DAG -> MAG over observed.
                Graph trueMag = GraphTransforms.dagToMag(dag);
                if (trueMag.getNumNodes() != OBS) {
                    throw new IllegalStateException("dagToMag returned " + trueMag.getNumNodes()
                            + " nodes; expected the " + OBS + " observed variables (latents marginalized).");
                }
                for (Node v : trueMag.getNodes()) {
                    if (v.getNodeType() == NodeType.LATENT) {
                        throw new IllegalStateException("dagToMag output contains a latent node: " + v);
                    }
                }

                // Canonicalize and claim.  Everything downstream is a function of
                // this MAG once the oracle is MsepTest(trueMag).
                List<Node> obsSorted = new ArrayList<>(trueMag.getNodes());
                obsSorted.sort(Comparator.comparingInt(nd -> Integer.parseInt(nd.getName().substring(1))));
                Canon canon = canonicalKey(trueMag, obsSorted);
                if (SEEN.putIfAbsent(canon.key, Boolean.TRUE) != null) {
                    r.dupModels++;
                    continue;
                }
                r.distinctMags++;

                // Full work, once per distinct key.
                Graph truePag = GraphTransforms.dagToPag(dag, new Knowledge(),
                        EXCLUDE_SELECTION_BIAS, RECURSIVE_DEPTH);

                List<Node> canonNodes = new ArrayList<>();
                for (int q = 0; q < OBS; q++) canonNodes.add(new GraphNode("V" + (q + 1)));
                Graph canonMag = relabel(trueMag, obsSorted, canon.perm, canonNodes);
                Graph canonPag = relabel(truePag, obsSorted, canon.perm, canonNodes);
                String mapping = mappingDesc(obsSorted, canon.perm, latSet);

                runPipeline(r, mask, latSet, mapping, dag, canonMag, canonPag, canonNodes);
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
    // PIPELINE (per distinct canonical MAG)
    // ────────────────────────────────────────────────────────────────────────

    private static void runPipeline(Result r, long mask, Set<Integer> latSet, String mapping,
                                    Graph dag, Graph canonMag, Graph canonPag, List<Node> obs)
            throws InterruptedException {

        MsepTest oracle = new MsepTest(canonMag);   // exactness-preserving oracle swap (see header)
        Knowledge knowledge = new Knowledge();
        Set<Triple> initialColliders = noteInitialColliders(obs, canonPag);

        // Canonical CI-statement enumeration (pairs x conditioning subsets) and
        // the truth model bitvector, shared by every I-map check below.
        List<int[]> trPairs = new ArrayList<>();
        List<Set<Node>> trZ = new ArrayList<>();
        buildStatements(obs, trPairs, trZ);
        int T = trPairs.size();
        boolean[] truthModel = modelOf(oracle, obs, trPairs, trZ, T);

        List<int[]> nonAdj = nonAdjacentPairs(canonPag, obs);
        if (nonAdj.isEmpty()) return;

        List<Node[]> sepPairs = new ArrayList<>();
        for (int[] p : nonAdj) sepPairs.add(new Node[]{obs.get(p[0]), obs.get(p[1])});

        int cap = Math.min(MAX_SPURIOUS, sepPairs.size());
        for (int k = 1; k <= cap; k++) {
            SublistGenerator spGen = new SublistGenerator(sepPairs.size(), k);
            int[] spChoice;
            while ((spChoice = spGen.next()) != null) {
                if (spChoice.length != k) continue;
                if (STOP.get()) return;
                r.h0Candidates++;

                // H0 = truePag + circles at the chosen spurious pairs.
                Graph h0 = new EdgeListGraph(canonPag);
                SepsetMap sepsets = new SepsetMap();
                List<Edge> spurious = new ArrayList<>();
                for (int si : spChoice) {
                    Node a = sepPairs.get(si)[0];
                    Node b2 = sepPairs.get(si)[1];
                    Edge edge = new Edge(a, b2, Endpoint.CIRCLE, Endpoint.CIRCLE);
                    h0.addEdge(edge);
                    spurious.add(edge);
                }

                // FAITHFUL-FIX sepset population (COLD), as in PKE2:
                // (1) spanning include-common-first separator per spurious pair (rbOk gate);
                // (2) spanning separator for every remaining true non-adjacency of G*.
                boolean rbOk = true;
                for (Edge edge : spurious) {
                    Set<Node> sep = fcitSpanningSepset(h0, oracle, edge.getNode1(), edge.getNode2());
                    if (sep == null) {
                        rbOk = false;
                        break;
                    }
                    sepsets.set(edge.getNode1(), edge.getNode2(), sep);
                }
                if (!rbOk) {
                    r.h0RbFail++;
                    continue;
                }
                for (int[] p : nonAdj) {
                    Node a = obs.get(p[0]), b2 = obs.get(p[1]);
                    if (h0.isAdjacentTo(a, b2)) continue;         // spurious pair, present in H0
                    if (sepsets.get(a, b2) != null) continue;
                    Set<Node> sep = fcitSpanningSepset(h0, oracle, a, b2);
                    if (sep != null) sepsets.set(a, b2, sep);
                }

                // GATE: legal AND I-map (the population prop:stall / prop:committed-markov
                // quantify over).  No genuineness gate.
                reorient(h0, oracle, sepsets, knowledge, initialColliders);
                if (!PagLegalityCheck.isLegalPag(h0, new HashSet<>()).isLegalPag()) {
                    r.h0IllegalPag++;
                    continue;
                }
                boolean[] h0Model = modelOf(new MsepTest(magOfPag(h0)), obs, trPairs, trZ, T);
                if (!subsetModel(h0Model, truthModel)) {
                    r.h0NotImap++;                                 // tally only, per spec
                    continue;
                }
                r.gated++;

                // Single-edge deletions from H0.
                int legalEscapes = 0;
                StringBuilder delLog = new StringBuilder();
                List<Edge> illegalEdges = new ArrayList<>();
                List<Graph> illegalH1s = new ArrayList<>();
                List<String> illegalReasons = new ArrayList<>();

                for (Edge e : spurious) {
                    if (STOP.get()) return;
                    r.delAttempts++;
                    Graph h1 = new EdgeListGraph(h0);
                    Edge present = h1.getEdge(e.getNode1(), e.getNode2());
                    if (present == null) {
                        delLog.append("    ").append(e).append(" : absent in H0 (unexpected)\n");
                        continue;
                    }
                    h1.removeEdge(present);

                    // Deletion sepset found in the removal context and recorded for e's pair.
                    Set<Node> opSep = fcitSpanningSepset(h1, oracle, e.getNode1(), e.getNode2());
                    if (opSep == null
                            || !oracle.checkIndependence(e.getNode1(), e.getNode2(), opSep).isIndependent()) {
                        r.delNoSepset++;
                        delLog.append("    ").append(e)
                                .append(" : refused -- no oracle-confirmed separator in removal context\n");
                        continue;
                    }
                    SepsetMap sepsetsH1 = copySepsets(sepsets);
                    sepsetsH1.set(e.getNode1(), e.getNode2(), opSep);

                    reorient(h1, oracle, sepsetsH1, knowledge, initialColliders);   // COLD, always
                    PagLegalityCheck.LegalPagRet ret = PagLegalityCheck.isLegalPag(h1, new HashSet<>());

                    if (ret.isLegalPag()) {
                        legalEscapes++;
                        r.delLegal++;
                        delLog.append("    ").append(e).append(" : legal\n");

                        // STEP-BREAK probe: legal H1 from an I-map H0 that is not an I-map.
                        boolean[] h1Model = modelOf(new MsepTest(magOfPag(h1)), obs, trPairs, trZ, T);
                        String falseCi = firstFalseCi(h1Model, truthModel, obs, trPairs, trZ);
                        if (falseCi != null) {
                            r.stepBreaks++;
                            String entry = formatStepBreak(mask, latSet, mapping, spurious, e,
                                    dag, canonMag, canonPag, h0, h1, falseCi);
                            if (!stepLog.write(entry)) r.stepBreakSuppressed++;
                            maybeStop();
                        }
                    } else {
                        r.delIllegal++;
                        delLog.append("    ").append(e).append(" : illegal -- ")
                                .append(ret.getReason()).append('\n');
                        illegalEdges.add(e);
                        illegalH1s.add(h1);
                        illegalReasons.add(ret.getReason());
                    }
                }

                if (legalEscapes > 0) {
                    r.h0WithEscape++;
                    continue;
                }

                // CANONICAL STALL: legal I-map H0 with no legal single-edge cold deletion.
                r.canonicalStalls++;
                SweepOutcome sw = null;
                if (REPRESENTATIVE_SCOPE != RepScope.NONE) {
                    sw = representativeSweep(h0, h0Model, truthModel, obs, trPairs, trZ, T,
                            spurious, REPRESENTATIVE_SCOPE == RepScope.LEG_ONLY);
                }
                boolean survives = (sw == null) || !sw.rescued;
                if (sw != null && sw.rescued) r.stallRescued++;
                if (survives) r.stallSurvives++;

                String entry = formatStall(mask, latSet, mapping, spurious, dag, canonMag, canonPag,
                        h0, delLog.toString(), survives ? illegalEdges : null,
                        survives ? illegalH1s : null, survives ? illegalReasons : null, sw, survives);
                if (!stallLog.write(entry)) r.stallSuppressed++;
                if (survives && FIRST_STALL_WRITTEN.compareAndSet(false, true)) {
                    writeFirstStall(entry);
                }
                maybeStop();
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // REPRESENTATIVE SWEEP (Step-Lemma escalation on canonical stalls)
    // ────────────────────────────────────────────────────────────────────────

    private static final class SweepOutcome {
        boolean legOnly;
        boolean rescued;
        long enumerated;         // orientation assignments considered
        long passedPrefilter;    // survived LEG + unshielded-collider prefilters
        int equivalents;         // Markov-equivalent legal MAGs found
        int legs;                // of those, LEGs
        int deletionChecks;
        String rescueDetail;
    }

    /**
     * On the skeleton of h0, enumerate every orientation assignment (3^E), keep
     * legal MAGs Markov-equivalent to h0 (exact model equality; unshielded-collider
     * agreement as a fast necessary prefilter), restricted to LEGs when legOnly
     * (every bidirected edge of the candidate is bidirected in the PAG h0 --
     * i.e., invariant).  For each representative and each spurious edge f, test
     * whether representative - f is a legal MAG whose model is a subset of the
     * truth model (I-map).  Monotonicity is automatic: deletion only adds
     * m-separations.  Short-circuits on the first rescue.
     */
    private static SweepOutcome representativeSweep(Graph h0, boolean[] h0Model, boolean[] truthModel,
                                                    List<Node> obs, List<int[]> trPairs,
                                                    List<Set<Node>> trZ, int T,
                                                    List<Edge> spurious, boolean legOnly)
            throws InterruptedException {
        SweepOutcome out = new SweepOutcome();
        out.legOnly = legOnly;

        Map<String, Integer> pos = new HashMap<>();
        for (int i = 0; i < obs.size(); i++) pos.put(obs.get(i).getName(), i);

        List<Edge> skel = new ArrayList<>(h0.getEdges());
        int E = skel.size();
        int[] ea = new int[E], eb = new int[E];
        boolean[] bidirInH0 = new boolean[E];
        for (int e = 0; e < E; e++) {
            Edge ed = skel.get(e);
            ea[e] = pos.get(ed.getNode1().getName());
            eb[e] = pos.get(ed.getNode2().getName());
            bidirInH0[e] = ed.getEndpoint1() == Endpoint.ARROW && ed.getEndpoint2() == Endpoint.ARROW;
        }

        // Skeleton adjacency and unshielded triples, with h0's class-invariant
        // collider verdict per triple (in a legal PAG, an unshielded triple is a
        // collider in every representative iff it is a def-collider in the PAG).
        boolean[][] adj = new boolean[obs.size()][obs.size()];
        for (int e = 0; e < E; e++) {
            adj[ea[e]][eb[e]] = true;
            adj[eb[e]][ea[e]] = true;
        }
        List<int[]> triples = new ArrayList<>();   // {edgeA, edgeC, zPos}
        List<Boolean> h0Coll = new ArrayList<>();
        for (int z = 0; z < obs.size(); z++) {
            List<Integer> inc = new ArrayList<>();
            for (int e = 0; e < E; e++) if (ea[e] == z || eb[e] == z) inc.add(e);
            for (int i = 0; i < inc.size(); i++) {
                for (int j = i + 1; j < inc.size(); j++) {
                    int e1 = inc.get(i), e2 = inc.get(j);
                    int a = (ea[e1] == z) ? eb[e1] : ea[e1];
                    int c = (ea[e2] == z) ? eb[e2] : ea[e2];
                    if (adj[a][c]) continue;
                    triples.add(new int[]{e1, e2, z});
                    h0Coll.add(h0.isDefCollider(obs.get(a), obs.get(z), obs.get(c)));
                }
            }
        }

        long total = 1;
        for (int e = 0; e < E; e++) total *= 3;
        int[] o = new int[E];

        outer:
        for (long code = 0; code < total; code++) {
            out.enumerated++;
            long c = code;
            for (int e = 0; e < E; e++) {
                o[e] = (int) (c % 3);
                c /= 3;
            }

            // LEG prefilter: candidate bidirected edges must be invariant (bidirected in h0).
            if (legOnly) {
                for (int e = 0; e < E; e++) {
                    if (o[e] == 2 && !bidirInH0[e]) continue outer;
                }
            }
            // Unshielded-collider agreement (necessary for Markov equivalence).
            for (int t = 0; t < triples.size(); t++) {
                int[] tr = triples.get(t);
                boolean cand = arrowAtZ(tr[0], tr[2], o[tr[0]], ea, eb)
                        && arrowAtZ(tr[1], tr[2], o[tr[1]], ea, eb);
                if (cand != h0Coll.get(t)) continue outer;
            }
            out.passedPrefilter++;

            Graph cand = new EdgeListGraph(obs);
            for (int e = 0; e < E; e++) {
                Node a = obs.get(ea[e]), b = obs.get(eb[e]);
                if (o[e] == 0) cand.addDirectedEdge(a, b);
                else if (o[e] == 1) cand.addDirectedEdge(b, a);
                else cand.addBidirectedEdge(a, b);
            }
            if (!isLegalMag(cand)) continue;

            boolean[] m = modelOf(new MsepTest(cand), obs, trPairs, trZ, T);
            if (!modelsEqual(m, h0Model)) continue;             // exact: equivalent to [H0]
            out.equivalents++;

            boolean isLeg = true;
            for (int e = 0; e < E; e++) {
                if (o[e] == 2 && !bidirInH0[e]) {
                    isLeg = false;
                    break;
                }
            }
            if (isLeg) out.legs++;

            for (Edge f : spurious) {
                out.deletionChecks++;
                Graph del = new EdgeListGraph(cand);
                Edge fe = del.getEdge(f.getNode1(), f.getNode2());
                if (fe == null) continue;
                del.removeEdge(fe);
                if (!isLegalMag(del)) continue;                 // only possible break: inducing path x..y
                boolean[] dm = modelOf(new MsepTest(del), obs, trPairs, trZ, T);
                if (subsetModel(dm, truthModel)) {
                    out.rescued = true;
                    out.rescueDetail = "    rescuing representative ("
                            + (isLeg ? "LEG" : "non-LEG equivalent") + "):\n" + cand
                            + "\n    deletion: " + f
                            + "\n    post-deletion MAG (legal, I-map of G*):\n" + del + "\n";
                    break outer;
                }
            }
        }

        // Sanity: the Zhang MAG of a legal PAG is an equivalent representative
        // (and a LEG), so zero equivalents = machinery drift.  Fail loudly.
        if (out.equivalents == 0) {
            throw new IllegalStateException("representativeSweep: zero Markov-equivalent "
                    + (legOnly ? "LEGs" : "MAGs") + " found for a legal H0 -- "
                    + "isLegalMag/model machinery drift; sweep verdicts untrustworthy.");
        }
        return out;
    }

    /** Does edge e, under orientation code o, carry an arrowhead at position z? */
    private static boolean arrowAtZ(int e, int z, int o, int[] ea, int[] eb) {
        if (eb[e] == z) return o == 0 || o == 2;   // a->b or a<->b: arrow at b
        return o == 1 || o == 2;                    // b->a or a<->b: arrow at a
    }

    // ────────────────────────────────────────────────────────────────────────
    // COLD REORIENTATION (verbatim PKE2 pipeline, robust-R0 stripped)
    // ────────────────────────────────────────────────────────────────────────

    private static void reorient(Graph h, IndependenceTest oracle, SepsetMap sepsets, Knowledge knowledge,
                                 Set<Triple> initialColliders) throws InterruptedException {
        GraphUtils.reorientWithCircles(h, false);                     // COLD wipe, always
        GraphUtils.recallInitialColliders(h, initialColliders, knowledge);
        stampExtraSepsetColliders(sepsets, h);

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
        fciOrient.finalOrientation(h, EXCLUDE_SELECTION_BIAS);
    }

    /** Ordinary (non-robust) R0 stamping from recorded separators: for each
     *  recorded non-adjacent pair, orient x*->c<-*y at every common neighbor c
     *  excluded from Sep(x,y) that is not already a def-collider. */
    private static void stampExtraSepsetColliders(SepsetMap sepsets, Graph pag) {
        for (Set<Node> edge : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(edge);
            if (arr.size() != 2) continue;
            Node x = arr.get(0);
            Node y = arr.get(1);
            Set<Node> s = sepsets.get(x, y);
            if (s == null) continue;
            if (pag.isAdjacentTo(x, y)) continue;

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));
            for (Node node : common) {
                if (s.contains(node)) continue;
                if (pag.isDefCollider(x, node, y)) continue;
                pag.setEndpoint(x, node, Endpoint.ARROW);
                pag.setEndpoint(y, node, Endpoint.ARROW);
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // SEPSET SEARCH (verbatim PKE2: FCIT-faithful spanning include-common-first)
    // ────────────────────────────────────────────────────────────────────────

    private static Set<Node> fcitSpanningSepset(Graph graph, IndependenceTest oracle,
                                                Node x, Node y) throws InterruptedException {
        final long deadline = TIMEOUT < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + TIMEOUT;

        RecursiveBlocking.BlockingResult b0 = RecursiveBlocking.blockPathsRecursively(
                graph, x, y, Collections.emptySet(), Collections.emptySet(),
                RECURSIVE_DEPTH, DEPTH, -1, 1, true, deadline);

        Set<Node> nfCandSet = new LinkedHashSet<>();
        if (b0 != null && !b0.indeterminate() && b0.blockingSet() != null) {
            for (Node v : b0.blockingSet()) {
                if (graph.getAdjacentNodes(v).stream().anyMatch(
                        w -> graph.getEndpoint(v, w) == Endpoint.CIRCLE
                                || graph.getEndpoint(w, v) == Endpoint.CIRCLE)) {
                    nfCandSet.add(v);
                }
            }
        }
        List<Node> nfCand = new ArrayList<>(nfCandSet);

        SublistGenerator nfGen = new SublistGenerator(nfCand.size(), nfCand.size());
        int[] nfChoice;
        while ((nfChoice = nfGen.next()) != null) {
            Set<Node> notFollowed = GraphUtils.asSet(nfChoice, nfCand);

            RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursively(
                    graph, x, y, Collections.emptySet(), notFollowed,
                    RECURSIVE_DEPTH, DEPTH, -1, 1, true, deadline);

            if (result == null || result.indeterminate() || result.blockingSet() == null) continue;

            Set<Node> B = result.blockingSet();

            List<Node> common = graph.getAdjacentNodes(x);
            common.retainAll(graph.getAdjacentNodes(y));
            B.addAll(common);
            List<Node> removalCandidates = new ArrayList<>(common);

            SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), removalCandidates.size());
            int[] cChoice;
            while ((cChoice = cGen.next()) != null) {
                Set<Node> S = new LinkedHashSet<>(B);
                S.removeAll(GraphUtils.asSet(cChoice, removalCandidates));
                if (DEPTH != -1 && S.size() > DEPTH) continue;
                if (oracle.checkIndependence(x, y, S).isIndependent()) {
                    return S;
                }
            }
        }
        return null;
    }

    // ────────────────────────────────────────────────────────────────────────
    // MODELS, I-MAP CHECKS, SMALL GRAPH UTILITIES
    // ────────────────────────────────────────────────────────────────────────

    /** Canonical enumeration of all CI statements over obs: every pair (i<j) x
     *  every conditioning subset of the remaining nodes. */
    private static void buildStatements(List<Node> obs, List<int[]> trPairs, List<Set<Node>> trZ) {
        int n = obs.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                List<Integer> others = new ArrayList<>();
                for (int t = 0; t < n; t++) if (t != i && t != j) others.add(t);
                int mm = others.size();
                for (int z = 0; z < (1 << mm); z++) {
                    Set<Node> Z = new HashSet<>();
                    for (int b = 0; b < mm; b++) if ((z & (1 << b)) != 0) Z.add(obs.get(others.get(b)));
                    trPairs.add(new int[]{i, j});
                    trZ.add(Z);
                }
            }
        }
    }

    private static boolean[] modelOf(IndependenceTest test, List<Node> obs, List<int[]> trPairs,
                                     List<Set<Node>> trZ, int T) throws InterruptedException {
        boolean[] m = new boolean[T];
        for (int t = 0; t < T; t++) {
            Node x = obs.get(trPairs.get(t)[0]), y = obs.get(trPairs.get(t)[1]);
            m[t] = test.checkIndependence(x, y, trZ.get(t)).isIndependent();
        }
        return m;
    }

    private static boolean subsetModel(boolean[] a, boolean[] b) {
        for (int i = 0; i < a.length; i++) if (a[i] && !b[i]) return false;
        return true;
    }

    private static boolean modelsEqual(boolean[] a, boolean[] b) {
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    /** First CI statement holding in cand but not in truth (an I-map violation), or null. */
    private static String firstFalseCi(boolean[] cand, boolean[] truth, List<Node> obs,
                                       List<int[]> trPairs, List<Set<Node>> trZ) {
        for (int t = 0; t < cand.length; t++) {
            if (cand[t] && !truth[t]) {
                return obs.get(trPairs.get(t)[0]).getName() + " _||_ "
                        + obs.get(trPairs.get(t)[1]).getName() + " | " + trZ.get(t)
                        + "   (m-separated here, m-connected in G*)";
            }
        }
        return null;
    }

    private static boolean isLegalMag(Graph g) {
        return g.paths().isLegalMag();
    }

    private static Graph magOfPag(Graph pag) {
        return GraphTransforms.zhangMagFromPag(pag);
    }

    private static SepsetMap copySepsets(SepsetMap sepsets) {
        SepsetMap copy = new SepsetMap();
        for (Set<Node> edge : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(edge);
            if (arr.size() != 2) continue;
            Node x = arr.get(0);
            Node y = arr.get(1);
            Set<Node> s = sepsets.get(x, y);
            if (s != null) copy.set(x, y, new HashSet<>(s));
        }
        return copy;
    }

    private static Set<Triple> noteInitialColliders(List<Node> best, Graph graph) {
        Set<Triple> initialColliders = new HashSet<>();
        for (Node b : best) {
            List<Node> adj = graph.getAdjacentNodes(b);
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

    // ────────────────────────────────────────────────────────────────────────
    // CANONICALIZATION AND RELABELING
    // ────────────────────────────────────────────────────────────────────────

    private static final class Canon {
        final String key;
        final int[] perm;

        Canon(String key, int[] perm) {
            this.key = key;
            this.perm = perm;
        }
    }

    /** Key = sorted edge codes of the MAG, minimized over PERMS.  perm maps
     *  sorted-observed index i to canonical position perm[i]. */
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

    /** Relabel g's observed nodes: obsSorted.get(i) -> canonNodes.get(perm[i]). */
    private static Graph relabel(Graph g, List<Node> obsSorted, int[] perm, List<Node> canonNodes) {
        Map<String, Node> map = new HashMap<>();
        for (int i = 0; i < obsSorted.size(); i++) {
            map.put(obsSorted.get(i).getName(), canonNodes.get(perm[i]));
        }
        Graph out = new EdgeListGraph(canonNodes);
        for (Edge e : g.getEdges()) {
            out.addEdge(new Edge(map.get(e.getNode1().getName()), map.get(e.getNode2().getName()),
                    e.getEndpoint1(), e.getEndpoint2()));
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
    // WITNESS FORMATTING
    // ────────────────────────────────────────────────────────────────────────

    private static String formatStall(long mask, Set<Integer> latSet, String mapping,
                                      List<Edge> spurious, Graph dag, Graph canonMag, Graph canonPag,
                                      Graph h0, String delLog, List<Edge> illegalEdges,
                                      List<Graph> illegalH1s, List<String> illegalReasons,
                                      SweepOutcome sw, boolean survives) {
        StringBuilder sb = new StringBuilder();
        if (survives) {
            sb.append("==== SURVIVING STALL -- STEP-LEMMA COUNTEREXAMPLE CANDIDATE ====\n");
        } else {
            sb.append("==== CANONICAL STALL (rescued by representative sweep) ====\n");
        }
        sb.append("  exemplar dag mask     : ").append(mask).append('\n');
        sb.append("  latent set (indices)  : ").append(latSet).append('\n');
        sb.append("  relabeling            : ").append(mapping).append('\n');
        sb.append("  spurious edges (canon): ").append(spurious).append('\n');
        sb.append("  exemplar DAG (original labels):\n").append(dag).append('\n');
        sb.append("  true MAG (canonical labels):\n").append(canonMag).append('\n');
        sb.append("  true PAG (canonical labels):\n").append(canonPag).append('\n');
        sb.append("  H0 (legal I-map, cold-reoriented):\n").append(h0).append('\n');
        sb.append("  per-deletion outcomes:\n").append(delLog);
        if (sw == null) {
            sb.append("  representative sweep  : NONE (scope=NONE) -- canonical stall counts as surviving\n");
        } else {
            sb.append(String.format("  representative sweep  : scope=%s | enumerated=%d prefiltered=%d "
                            + "equivalents=%d legs=%d deletionChecks=%d%n",
                    sw.legOnly ? "LEG_ONLY" : "ALL", sw.enumerated, sw.passedPrefilter,
                    sw.equivalents, sw.legs, sw.deletionChecks));
            if (sw.rescued) {
                sb.append("  verdict               : RESCUED_BY_").append(sw.legOnly ? "LEG" : "ALL").append('\n');
                sb.append(sw.rescueDetail);
            } else {
                sb.append("  verdict               : SURVIVES (no representative admits a legal I-map deletion)\n");
            }
        }
        if (survives && illegalEdges != null) {
            sb.append("  --- ILLEGAL SINGLE-EDGE INTERMEDIATES (cold-reoriented PAG + implied MAG) ---\n");
            for (int i = 0; i < illegalEdges.size(); i++) {
                sb.append("  ---- failed deletion ").append(illegalEdges.get(i)).append(" ----\n");
                sb.append("    reason: ").append(illegalReasons.get(i)).append('\n');
                sb.append("    H1 (the PAG isLegalPag rejected):\n").append(illegalH1s.get(i)).append('\n');
                sb.append("    implied MAG of H1 (zhangMagFromPag):\n")
                        .append(magOfPag(illegalH1s.get(i))).append('\n');
            }
        }
        sb.append("==== end entry ====\n");
        return sb.toString();
    }

    private static String formatStepBreak(long mask, Set<Integer> latSet, String mapping,
                                          List<Edge> spurious, Edge removed, Graph dag,
                                          Graph canonMag, Graph canonPag, Graph h0, Graph h1,
                                          String falseCi) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== STEP BREAK: I-map H0 -> LEGAL non-I-map H1 ====\n");
        sb.append("  exemplar dag mask     : ").append(mask).append('\n');
        sb.append("  latent set (indices)  : ").append(latSet).append('\n');
        sb.append("  relabeling            : ").append(mapping).append('\n');
        sb.append("  spurious edges (canon): ").append(spurious).append('\n');
        sb.append("  removed edge          : ").append(removed).append('\n');
        sb.append("  false CI witness      : ").append(falseCi).append('\n');
        sb.append("  exemplar DAG (original labels):\n").append(dag).append('\n');
        sb.append("  true MAG (canonical labels):\n").append(canonMag).append('\n');
        sb.append("  true PAG (canonical labels):\n").append(canonPag).append('\n');
        sb.append("  H0 (legal I-map):\n").append(h0).append('\n');
        sb.append("  H1 (LEGAL, non-I-map):\n").append(h1).append('\n');
        sb.append("==== end entry ====\n");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // STREAMED, CAPPED LOGS
    // ────────────────────────────────────────────────────────────────────────

    private static final class StreamLog {
        private final PrintWriter out;
        private final int max;
        private int count;

        StreamLog(String path, int max, String header) throws IOException {
            this.out = new PrintWriter(new FileWriter(path, true));   // append: resumes accumulate
            this.max = max;
            out.println(header);
            out.flush();
        }

        synchronized boolean write(String entry) {
            if (count >= max) return false;
            out.println(entry);
            out.flush();                                              // flush per entry, per spec
            count++;
            if (count == max) {
                out.println("==== cap of " + max + " entries reached; log closed to further examples ====");
                out.flush();
            }
            return true;
        }

        synchronized boolean full() {
            return count >= max;
        }

        synchronized void summary(String s) {
            out.println(s);
            out.flush();
        }

        synchronized void close() {
            out.close();
        }
    }

    private static void maybeStop() {
        if (stallLog.full() && stepLog.full() && STOP.compareAndSet(false, true)) {
            System.err.println("All capped logs are full -- stopping enumeration early.");
        }
    }

    private static synchronized void writeFirstStall(String entry) {
        try (PrintWriter w = new PrintWriter(new FileWriter(FIRST_STALL_LOG_PATH, true))) {
            w.println("# First surviving stall counterexample "
                    + "(strongest configured test: scope=" + REPRESENTATIVE_SCOPE + ")");
            w.println(CONFIG_LINE);
            w.println(entry);
            w.flush();
        } catch (IOException e) {
            System.err.println("Failed to write first-stall log: " + e);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // CHECKPOINTING
    // ────────────────────────────────────────────────────────────────────────

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

    private static final class Result {
        long dagsScanned, modelsScanned, dupModels, distinctMags, skipped;
        long h0Candidates, h0RbFail, h0IllegalPag, h0NotImap, gated, h0WithEscape;
        long delAttempts, delNoSepset, delLegal, delIllegal;
        long stepBreaks, stepBreakSuppressed;
        long canonicalStalls, stallRescued, stallSurvives, stallSuppressed;

        static void merge(Result a, Result b) {
            a.add(b);
        }

        void add(Result o) {
            dagsScanned += o.dagsScanned;
            modelsScanned += o.modelsScanned;
            dupModels += o.dupModels;
            distinctMags += o.distinctMags;
            skipped += o.skipped;
            h0Candidates += o.h0Candidates;
            h0RbFail += o.h0RbFail;
            h0IllegalPag += o.h0IllegalPag;
            h0NotImap += o.h0NotImap;
            gated += o.gated;
            h0WithEscape += o.h0WithEscape;
            delAttempts += o.delAttempts;
            delNoSepset += o.delNoSepset;
            delLegal += o.delLegal;
            delIllegal += o.delIllegal;
            stepBreaks += o.stepBreaks;
            stepBreakSuppressed += o.stepBreakSuppressed;
            canonicalStalls += o.canonicalStalls;
            stallRescued += o.stallRescued;
            stallSurvives += o.stallSurvives;
            stallSuppressed += o.stallSuppressed;
        }
    }
}
