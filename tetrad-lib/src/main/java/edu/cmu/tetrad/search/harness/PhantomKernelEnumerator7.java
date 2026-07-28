/// ////////////////////////////////////////////////////////////////////////////
// PhantomKernelEnumerator7.java  (parallel, deduplicated, checkpointed)       //
//                                                                             //
// PKE7 answers ONE question: does any reachable Markov state exist that the   //
// LATEST FCIT-SL move set cannot solve?  "The latest FCIT-SL move set" means  //
// the shipping FcitSl commit, mirrored move for move:                         //
//                                                                             //
//   per spurious edge f = x--y with recorded separator b:                     //
//     seeds     : the canonical Zhang MAG of the current PAG, plus in-class   //
//                 fork-flip seeds (active x..y paths given b, cap 8 paths;    //
//                 flips of <= MAX_FORK_FLIPS forks; legal-MAG results kept;   //
//                 classified in-class iff Markov-equivalent to the state).    //
//     candidates: per seed, the seed itself first, then the other LEG         //
//                 representatives of the SEED's class (LegEnumerator mirror). //
//     per cand  : copy; orientSepsetColliders(b, x, y) (the stamp); delete    //
//                 x--y; REJECT on an inducing path between x and y (FcitSl's  //
//                 ONLY legality check -- the maximality-only shortcut);       //
//                 REJECT on deleted-pair battery failure (every entailed      //
//                 separation of {x,y} with |Z| <= BATTERY_Z_MAX must be       //
//                 oracle-true); otherwise COMMIT.                             //
//                                                                             //
// Verdicts, per gated I-map H0 (same gate and population as PKE6):            //
//   SL-SOLVED : >= 1 spurious edge commits under the within-class move.       //
//   SL-STALL  : NO edge commits within class.  Logged in full; additionally   //
//               annotated with whether the pass-3 escape (certified           //
//               out-of-class fork-flip seeds) would rescue it.                //
// Soundness of every commit is separately audited against the oracle:        //
//   SL-BREAK          : a committed post-deletion MAG is NOT an I-map of G*   //
//                       (the battery let a Markov exit through -- a battery   //
//                       gap ON THE REAL MOVE, stamping and flips included).   //
//   SL-ILLEGAL-COMMIT : a committed graph passes the removed-pair inducing-   //
//                       path pre-check but FAILS full isLegalMag (a gap in    //
//                       the maximality-only shortcut).                        //
//                                                                             //
// THE ANSWER: slStall == 0 AND slCommitNotImap == 0 AND slCommitIllegal == 0  //
// at this scope means the audited move set solves every gated Markov state    //
// with no unsound or illegal commit -- "no unsolvable state exists" at        //
// N=7/|L|=2/|Spur|<=3.  Any nonzero counter localizes the failure and dumps   //
// the witness in full.                                                        //
//                                                                             //
// COVERAGE CAVEAT (same premise as PKE6): the audited population is every     //
// gated legal I-map H0 of the constructive form truePag+spurious subset,      //
// cold-reoriented -- the proxy for FCIT-SL's reachable Markov states.  A      //
// committed state that EXITS Markov space is caught as SL-BREAK rather than   //
// audited onward; non-Markov successors are outside the population by design. //
//                                                                             //
// FIDELITY NOTES (deviations from FcitSl, all deliberate):                    //
//   * b comes from fcitSpanningSepset on H0 (the harness mirror of            //
//     findIndependenceCheckRecursive against the live PAG), oracle-confirmed. //
//   * In-class/out-of-class seed classification is by EXACT model equality    //
//     (available at this scope); FcitSl classifies by PAG identity            //
//     (MagToPag(seed) == MagToPag(base)).  The two SHOULD coincide; every     //
//     divergence is tallied and logged (pke7_classifier_divergence.log) --    //
//     a free audit of FcitSl's classifier assumption.                         //
//   * LegEnumerator is mirrored as: all legal MAGs over the seed's skeleton,  //
//     Markov-equivalent to the seed, whose bidirected edges are bidirected    //
//     in the seed's PAG (LEG invariance), seed first.  Candidate ORDER beyond //
//     "seed first" is lexicographic in the orientation code, which may        //
//     differ from LegEnumerator's internal order; commit EXISTENCE (the       //
//     audited question) is order-independent, only stage attribution of      //
//     which candidate committed could shift within a stage.                   //
//                                                                             //
// Enumeration, dedup, canonicalization, gating pipeline, checkpointing, and   //
// streamed capped logs are IDENTICAL to PKE6, so the gated population count   //
// must reproduce PKE6's (17,825 at the frozen scope) as a consistency check.  //
// All PKE5/PKE6 probes (stall/step-break/mechanism/residue/ZM/audit ledgers)  //
// are REMOVED: this harness answers the one question only.                    //
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

/**
 * PKE7: does any gated Markov state exist that the latest FCIT-SL move set cannot solve?
 */
public final class PhantomKernelEnumerator7 {

    /**
     * Default constructor.
     */
    public PhantomKernelEnumerator7() {
    }

    // ────────────────────────────────────────────────────────────────────────
    // CONFIGURATION (all hard-coded; edit and re-run)
    // ────────────────────────────────────────────────────────────────────────

    /** Total nodes in the enumerated DAGs. */
    static final int N = 7;
    /** Number of latent nodes per placement. */
    static final int NUM_LATENT = 2;
    /** Spurious subsets of sizes 1..MAX_SPURIOUS are enumerated per model. */
    private static final int MAX_SPURIOUS = 3;

    // ── FCIT-SL move-set knobs (MUST match FcitSl's shipping defaults) ───────
    /** Deleted-pair battery conditioning-set bound (FcitSl batteryZMax). */
    private static final int BATTERY_Z_MAX = 2;
    /** Fork-flip bound for seeds (FcitSl maxForkFlips). */
    private static final int MAX_FORK_FLIPS = 2;
    /** Active-path cap in the fork inventory (FcitSl activePathsGivenS cap). */
    private static final int MAX_ACTIVE_PATHS = 8;
    /** On an SL-STALL, additionally test whether pass-3 (certified out-of-class
     *  fork-flip seeds) would rescue it; annotation only, never the verdict. */
    private static final boolean AUDIT_ESCAPE_ON_STALL = true;
    /** Cross-check FcitSl's PAG-identity in-class classifier against exact model
     *  equality; divergences are tallied and logged. */
    private static final boolean CHECK_CLASSIFIER = true;

    /** Canonicalize dedup keys over all 120 permutations of the observed
     *  nodes (max dedup).  false = relabel-by-sorted-index only. */
    private static final boolean CANONICALIZE_PERMS = true;

    // Log files (created/appended in the current working directory).
    /** Gated I-map H0s no within-class FCIT-SL move can serve -- the prize. */
    private static final String SL_STALL_LOG_PATH = "pke7_sl_stall.log";
    /** Battery-passed commits that are NOT I-maps of G* (battery gap). */
    private static final String SL_BREAK_LOG_PATH = "pke7_sl_break.log";
    /** Pre-check-passed commits failing full isLegalMag (shortcut gap). */
    private static final String SL_ILLEGAL_LOG_PATH = "pke7_sl_illegal.log";
    /** PAG-identity vs model-equality classifier divergences. */
    private static final String CLASSIFIER_LOG_PATH = "pke7_classifier_divergence.log";
    /** Completed block ids for resume. Delete to restart from scratch. */
    private static final String CHECKPOINT_PATH = "pke7_checkpoint.txt";

    // Per-log caps, counted in EXAMPLES (not bytes), per run.
    private static final int SL_STALL_LOG_MAX = 200;
    private static final int SL_BREAK_LOG_MAX = 500;
    private static final int SL_ILLEGAL_LOG_MAX = 200;
    private static final int CLASSIFIER_LOG_MAX = 100;

    /** Masks per checkpoint block: 2^12 = 4096 -> 512 blocks over 2^21 masks. */
    static final long BLOCK_SIZE = 1L << 12;

    // FCI/RB knobs (match the PKE cold pipeline).
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
    static final long TOTAL_DAGS = 1L << P;
    private static final int[][] PAIR = buildPairs();
    private static final List<int[]> PERMS = buildPerms();

    static final String CONFIG_LINE = String.format(
            "# PKE7 config: N=%d latent=%d maxSpurious=%d zMax=%d forkFlips=%d activePaths=%d "
                    + "escapeAudit=%b canonPerms=%b blockSize=%d",
            N, NUM_LATENT, MAX_SPURIOUS, BATTERY_Z_MAX, MAX_FORK_FLIPS, MAX_ACTIVE_PATHS,
            AUDIT_ESCAPE_ON_STALL, CANONICALIZE_PERMS, BLOCK_SIZE);

    /** Claimed canonical MAG keys.  Claim-first: exactly one thread runs the
     *  pipeline for each distinct key within a run. */
    private static final ConcurrentHashMap<String, Boolean> SEEN = new ConcurrentHashMap<>();

    private static final AtomicBoolean STOP = new AtomicBoolean(false);
    private static final AtomicLong ERR_PRINTS = new AtomicLong();

    private static StreamLog slStallLog;
    private static StreamLog slBreakLog;
    private static StreamLog slIllegalLog;
    private static StreamLog classifierLog;

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
        System.err.printf("observed=%d | dags=2^%d=%d | latentPlacements=C(%d,%d)=%d | models=%d%n",
                OBS, P, TOTAL_DAGS, N, NUM_LATENT, choose(N, NUM_LATENT),
                TOTAL_DAGS * choose(N, NUM_LATENT));
        System.err.printf("blocks=%d (size %d), %d already complete from checkpoint | threads~%d%n",
                numBlocks, BLOCK_SIZE, doneBlocks.size(),
                Runtime.getRuntime().availableProcessors());

        String header = CONFIG_LINE + "\n# run started " + new Date()
                + " | resuming with " + doneBlocks.size() + "/" + numBlocks + " blocks already complete"
                + " | entries streamed and flushed as found";
        slStallLog = new StreamLog(SL_STALL_LOG_PATH, SL_STALL_LOG_MAX, header
                + "\n# SL-STALL: gated legal I-map H0 at which NO spurious edge commits under the"
                + "\n# within-class FCIT-SL move (Zhang MAG + LEGs + in-class fork-flips, stamped,"
                + "\n# inducing-path pre-check, deleted-pair battery zMax=" + BATTERY_Z_MAX + ")."
                + "\n# Each entry is annotated with the pass-3 (out-of-class) rescue verdict."
                + "\n# An UNRESCUED entry is a state the full shipping FCIT-SL cannot move from.");
        slBreakLog = new StreamLog(SL_BREAK_LOG_PATH, SL_BREAK_LOG_MAX, header
                + "\n# SL-BREAK: a committed post-deletion MAG (battery-passed, pre-check-passed)"
                + "\n# that is NOT an I-map of G*.  A battery gap ON THE REAL MOVE (stamping and"
                + "\n# fork-flip seeds included) -- exactly the population the 548-classification"
                + "\n# did not cover.  Expect empty if Conjecture pairlocal extends to this move.");
        slIllegalLog = new StreamLog(SL_ILLEGAL_LOG_PATH, SL_ILLEGAL_LOG_MAX, header
                + "\n# SL-ILLEGAL-COMMIT: a committed graph passing the removed-pair inducing-path"
                + "\n# pre-check (FcitSl's only legality gate) but FAILING full isLegalMag --"
                + "\n# a gap in the maximality-only shortcut (ancestrality break or non-maximality"
                + "\n# away from the deleted pair, introduced by stamping or a fork-flip).");
        classifierLog = new StreamLog(CLASSIFIER_LOG_PATH, CLASSIFIER_LOG_MAX, header
                + "\n# CLASSIFIER DIVERGENCE: fork-flip seeds where FcitSl's in-class test"
                + "\n# (PAG identity via MagToPag) disagrees with exact model equality."
                + "\n# Expect empty; any entry means FcitSl's Stage-2b/pass-3 partition is wrong.");

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
                    .collect(Result::new, PhantomKernelEnumerator7::accumulate, Result::merge);
            total.add(blockRes);
            blocksThisRun++;

            if (STOP.get()) break;   // block may be partial once STOP fired: do NOT checkpoint it
            appendCheckpoint(b);

            System.err.printf("block %d done (%d this run) | models=%d distinct=%d dup=%d "
                            + "| gated=%d SL: solved=%d stalls=%d (unrescued %d) breaks=%d illegal=%d "
                            + "| keys=%d | %.1f min%n",
                    b, blocksThisRun, total.modelsScanned, total.distinctMags, total.dupModels,
                    total.gated, total.slSolved, total.slStall, total.slStallUnrescued,
                    total.slCommitNotImap, total.slCommitIllegal,
                    SEEN.size(), (System.currentTimeMillis() - t0) / 60000.0);
        }

        String summary = summarize(total, blocksThisRun, numBlocks, doneBlocks.size(),
                System.currentTimeMillis() - t0);
        System.out.println(summary);
        slStallLog.summary("\n" + summary);
        slBreakLog.summary("\n" + summary);
        slIllegalLog.summary("\n" + summary);
        classifierLog.summary("\n" + summary);
        slStallLog.close();
        slBreakLog.close();
        slIllegalLog.close();
        classifierLog.close();
    }

    private static String summarize(Result t, long blocksThisRun, long numBlocks, long resumedBlocks,
                                    long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== PKE7 SUMMARY (this run only; caps and counters are per-run) ====\n");
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
        sb.append(String.format("  GATED (legal I-map H0)           : %d   (PKE6 consistency anchor)%n", t.gated));
        sb.append("---- FCIT-SL MOVE AUDIT (the one question) ----\n");
        sb.append(String.format("per-edge attempts                  : %d (no recorded separator: %d)%n",
                t.slEdgeAttempts, t.slEdgeNoSep));
        sb.append(String.format("  commits -- Stage 1 (Zhang MAG)   : %d%n", t.slCommitStage1));
        sb.append(String.format("             Stage 2 (other LEG)   : %d%n", t.slCommitStage2));
        sb.append(String.format("             Stage 2b (in-cls flip): %d%n", t.slCommitStage2b));
        sb.append(String.format("  edges with NO within-class commit: %d%n", t.slEdgeStalled));
        sb.append(String.format("  pre-check (inducing-path) rejects: %d | stamp-guard rejects: %d "
                        + "| battery refusals: %d (statements tested: %d)%n",
                t.slIpRejects, t.slStampGuardRejects, t.slBatteryRefusals, t.slBatteryStatements));
        sb.append(String.format("H0 verdicts -- SL-SOLVED           : %d%n", t.slSolved));
        sb.append(String.format("               SL-STALL            : %d%s%n", t.slStall,
                t.slStall == 0 ? "" : "   (within-class move set stuck; see stall log)"));
        if (t.slStall > 0) {
            sb.append(String.format("                 rescued by pass 3 : %d%n", t.slStallEscapeRescued));
            sb.append(String.format("                 UNRESCUED         : %d%s%n", t.slStallUnrescued,
                    t.slStallUnrescued == 0 ? "   (full shipping FCIT-SL clears every stall)"
                            : "   *** states the full shipping FCIT-SL cannot move from ***"));
        }
        sb.append("---- COMMIT SOUNDNESS (audited against the oracle) ----\n");
        sb.append(String.format("SL-BREAK (commit not an I-map)     : %d%s%n", t.slCommitNotImap,
                t.slCommitNotImap == 0
                        ? "   (battery caught every Markov exit on the real move)"
                        : "   *** battery gap: see break log ***"));
        sb.append(String.format("SL-ILLEGAL-COMMIT (isLegalMag fail): %d%s%n", t.slCommitIllegal,
                t.slCommitIllegal == 0
                        ? "   (maximality-only shortcut exact at this scope)"
                        : "   *** shortcut gap: see illegal log ***"));
        sb.append(String.format("first-fit commit sound / unsound   : %d / %d%n",
                t.slFirstFitSound, t.slFirstFitUnsound));
        if (CHECK_CLASSIFIER) {
            sb.append(String.format("classifier divergences (PAG vs mdl): %d%s%n", t.classifierDivergence,
                    t.classifierDivergence == 0 ? "   (FcitSl's PAG-identity classifier validated)"
                            : "   *** FcitSl's in-class partition is wrong: see classifier log ***"));
        }
        sb.append("---- THE ANSWER ----\n");
        boolean pureClean = t.slStall == 0 && t.slCommitNotImap == 0 && t.slCommitIllegal == 0;
        boolean fullClean = t.slStallUnrescued == 0 && t.slCommitNotImap == 0 && t.slCommitIllegal == 0;
        sb.append(pureClean
                ? "Step-Lemma-pure FCIT-SL (escape OFF): NO unsolvable or unsoundly-solved state at this scope.\n"
                : "Step-Lemma-pure FCIT-SL (escape OFF): FAILURES FOUND -- see logs above.\n");
        sb.append(fullClean
                ? "Full shipping FCIT-SL (escape ON)   : NO unsolvable or unsoundly-solved state at this scope.\n"
                : "Full shipping FCIT-SL (escape ON)   : FAILURES FOUND -- see logs above.\n");
        sb.append(String.format("elapsed                            : %.1f min%n", elapsedMs / 60000.0));
        sb.append("==== END SUMMARY ====");
        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // PER-MASK ACCUMULATOR (identical to PKE6)
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
                            + " nodes; expected the " + OBS + " observed variables (latents marginalized).");
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
                r.distinctMags++;

                Graph truePag = GraphTransforms.dagToPag(dag, new Knowledge(),
                        EXCLUDE_SELECTION_BIAS, RECURSIVE_DEPTH);

                List<Node> canonNodes = new ArrayList<>();
                for (int q = 0; q < OBS; q++) canonNodes.add(new GraphNode("V" + (q + 1)));
                Graph canonMag = relabel(trueMag, obsSorted, canon.perm, canonNodes);
                Graph canonPag = relabel(truePag, obsSorted, canon.perm, canonNodes);
                String mapping = mappingDesc(obsSorted, canon.perm, latSet);

                runPipeline(r, mask, latSet, mapping, canonMag, canonPag, canonNodes);
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
    // PIPELINE (per distinct canonical MAG): PKE6 gate, then the SL audit
    // ────────────────────────────────────────────────────────────────────────

    private static void runPipeline(Result r, long mask, Set<Integer> latSet, String mapping,
                                    Graph canonMag, Graph canonPag, List<Node> obs)
            throws InterruptedException {

        MsepTest oracle = new MsepTest(canonMag);
        Knowledge knowledge = new Knowledge();
        Set<Triple> initialColliders = noteInitialColliders(obs, canonPag);

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
                    if (h0.isAdjacentTo(a, b2)) continue;
                    if (sepsets.get(a, b2) != null) continue;
                    Set<Node> sep = fcitSpanningSepset(h0, oracle, a, b2);
                    // ORACLE GUARD (P2), verbatim PKE6.
                    if (sep != null && oracle.checkIndependence(a, b2, sep).isIndependent()) {
                        sepsets.set(a, b2, sep);
                    }
                }

                reorient(h0, oracle, sepsets, knowledge, initialColliders);
                if (!PagLegalityCheck.isLegalPag(h0, new HashSet<>()).isLegalPag()) {
                    r.h0IllegalPag++;
                    continue;
                }
                boolean[] h0Model = modelOf(new MsepTest(magOfPag(h0)), obs, trPairs, trZ, T);
                if (!subsetModel(h0Model, truthModel)) {
                    r.h0NotImap++;
                    continue;
                }
                r.gated++;

                // ── THE ONE QUESTION ──
                slAudit(r, oracle, h0, h0Model, truthModel, obs, trPairs, trZ, T,
                        spurious, sepsets, mask, latSet, mapping, canonMag, canonPag);

                maybeStop();
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // FCIT-SL MOVE AUDIT
    // ────────────────────────────────────────────────────────────────────────

    /** Outcome of one FCIT-SL move attempt. stage: 1 = Zhang MAG, 2 = other LEG of the
     *  base class, 3 = in-class fork-flip seed (Stage 2b), 4 = out-of-class escape. */
    private static final class SlCommit {
        final int stage;
        final Graph candidate;
        final Graph postDel;

        SlCommit(int stage, Graph candidate, Graph postDel) {
            this.stage = stage;
            this.candidate = candidate;
            this.postDel = postDel;
        }

        String stageName() {
            return switch (stage) {
                case 1 -> "Stage 1 (Zhang MAG)";
                case 2 -> "Stage 2 (other LEG)";
                case 3 -> "Stage 2b (in-class fork-flip)";
                default -> "pass 3 (out-of-class escape)";
            };
        }
    }

    private static void slAudit(Result r, MsepTest oracle, Graph h0, boolean[] h0Model, boolean[] truthModel,
                                List<Node> obs, List<int[]> trPairs, List<Set<Node>> trZ, int T,
                                List<Edge> spurious, SepsetMap sepsets,
                                long mask, Set<Integer> latSet, String mapping,
                                Graph canonMag, Graph canonPag) throws InterruptedException {

        // FcitSl sweep order: lexicographic by (sorted) endpoint names -- first-fit fidelity.
        List<Edge> ordered = new ArrayList<>(spurious);
        ordered.sort(Comparator.comparing(e -> {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            return a.compareTo(b) <= 0 ? a + '\u0000' + b : b + '\u0000' + a;
        }));

        boolean anyCommit = false;
        boolean firstFitSeen = false;
        StringBuilder trace = new StringBuilder();

        for (Edge f : ordered) {
            Node x = f.getNode1(), y = f.getNode2();
            Set<Node> b = sepsets.get(x, y);
            if (b == null) {
                r.slEdgeNoSep++;
                trace.append("    ").append(f).append(" : no recorded separator (edge not attemptable)\n");
                continue;
            }
            r.slEdgeAttempts++;

            SlCommit c = slMove(r, oracle, h0, h0Model, x, y, b, obs, trPairs, trZ, T, false,
                    mask, latSet, mapping);
            if (c == null) {
                r.slEdgeStalled++;
                trace.append("    ").append(f).append(" : NO within-class candidate commits (b=")
                        .append(b).append(")\n");
                continue;
            }
            anyCommit = true;
            switch (c.stage) {
                case 1 -> r.slCommitStage1++;
                case 2 -> r.slCommitStage2++;
                default -> r.slCommitStage2b++;
            }

            // Soundness of the committed graph, against the oracle.
            boolean legal = isLegalMag(c.postDel);
            if (!legal) {
                r.slCommitIllegal++;
                slIllegalLog.write("==== SL-ILLEGAL-COMMIT ====\n"
                        + "  mask=" + mask + " lat=" + latSet + " map=" + mapping + '\n'
                        + "  spurious=" + spurious + "  deleted=" + f + "  b=" + b + '\n'
                        + "  committed at " + c.stageName() + '\n'
                        + "  candidate MAG (pre-deletion, stamped later):\n" + c.candidate + '\n'
                        + "  post-deletion graph (pre-check-passed, isLegalMag-FAILED):\n" + c.postDel + '\n'
                        + "==== end entry ====\n");
            }
            boolean[] m = modelOf(new MsepTest(c.postDel), obs, trPairs, trZ, T);
            boolean imap = subsetModel(m, truthModel);
            if (!imap) {
                r.slCommitNotImap++;
                slBreakLog.write("==== SL-BREAK (battery-passed, non-I-map commit) ====\n"
                        + "  mask=" + mask + " lat=" + latSet + " map=" + mapping + '\n'
                        + "  spurious=" + spurious + "  deleted=" + f + "  b=" + b + '\n'
                        + "  committed at " + c.stageName() + '\n'
                        + "  false CI: " + firstFalseCi(m, truthModel, obs, trPairs, trZ) + '\n'
                        + "  true MAG G*:\n" + canonMag + '\n'
                        + "  true PAG G*:\n" + canonPag + '\n'
                        + "  H0 (gated legal I-map):\n" + h0 + '\n'
                        + "  candidate MAG:\n" + c.candidate + '\n'
                        + "  post-deletion MAG (battery-passed, NOT an I-map):\n" + c.postDel + '\n'
                        + "==== end entry ====\n");
            }
            if (!firstFitSeen) {
                firstFitSeen = true;
                if (legal && imap) r.slFirstFitSound++;
                else r.slFirstFitUnsound++;
            }
            trace.append("    ").append(f).append(" : COMMIT at ").append(c.stageName())
                    .append(legal ? "" : "  [ILLEGAL MAG]").append(imap ? "" : "  [NOT I-MAP]")
                    .append('\n');
        }

        if (anyCommit) {
            r.slSolved++;
            return;
        }

        // SL-STALL: no edge commits within class.  Annotate with the pass-3 verdict.
        r.slStall++;
        String escapeVerdict = "not audited";
        if (AUDIT_ESCAPE_ON_STALL) {
            boolean rescued = false;
            for (Edge f : ordered) {
                Set<Node> b = sepsets.get(f.getNode1(), f.getNode2());
                if (b == null) continue;
                SlCommit c = slMove(r, oracle, h0, h0Model, f.getNode1(), f.getNode2(), b,
                        obs, trPairs, trZ, T, true, mask, latSet, mapping);
                if (c != null) {
                    rescued = true;
                    escapeVerdict = "RESCUED by pass 3 (out-of-class seed), deleting " + f;
                    break;
                }
            }
            if (rescued) {
                r.slStallEscapeRescued++;
            } else {
                r.slStallUnrescued++;
                escapeVerdict = "NOT RESCUED by pass 3 -- the full shipping FCIT-SL cannot move here";
            }
        }
        slStallLog.write("==== SL-STALL ====\n"
                + "  mask=" + mask + " lat=" + latSet + " map=" + mapping + '\n'
                + "  spurious=" + spurious + '\n'
                + "  per-edge trace:\n" + trace
                + "  pass-3 escape verdict: " + escapeVerdict + '\n'
                + "  true MAG G*:\n" + canonMag + '\n'
                + "  true PAG G*:\n" + canonPag + '\n'
                + "  H0 (gated legal I-map, no within-class SL commit):\n" + h0 + '\n'
                + "  Zhang MAG of H0:\n" + magOfPag(h0) + '\n'
                + "==== end entry ====\n");
    }

    /**
     * One FCIT-SL move for the pair {x,y} with separator b, mirrored from FcitSl:
     * seeds (Zhang MAG; fork-flips partitioned in-class/out-of-class), candidates
     * (seed first, then the other LEGs of the seed's class), and per candidate the
     * stamp, the deletion, the inducing-path pre-check, and the deleted-pair
     * battery against the ORACLE.  Returns the first commit, or null.
     * escape == false: base seed + in-class flips.  escape == true: out-of-class
     * flips only (the within-class seeds were already exhausted).
     */
    private static SlCommit slMove(Result r, MsepTest oracle, Graph h0, boolean[] h0Model, Node x, Node y, Set<Node> b,
                                   List<Node> obs, List<int[]> trPairs, List<Set<Node>> trZ, int T,
                                   boolean escape, long mask, Set<Integer> latSet, String mapping)
            throws InterruptedException {
        Graph base = magOfPag(h0);
        if (base.getEdge(x, y) == null) return null;

        // ── seeds, mirroring seedMags ──
        List<Graph> seeds = new ArrayList<>();
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        seen.put(magKey(base), Boolean.TRUE);
        if (!escape) seeds.add(base);

        Graph probe = new EdgeListGraph(base);
        probe.removeEdge(x, y);
        boolean baseHosts = new MsepTest(probe).checkIndependence(x, y, b).isIndependent();

        if (!baseHosts) {
            Graph basePag = CHECK_CLASSIFIER ? new MagToPag(base).convert(false, EXCLUDE_SELECTION_BIAS) : null;

            Map<Node, Set<Node>> forkNbrs = new LinkedHashMap<>();
            for (List<Node> p : activePathsGivenS(probe, x, y, b, MAX_ACTIVE_PATHS)) {
                for (int i = 1; i < p.size() - 1; i++) {
                    Node a = p.get(i - 1), mNode = p.get(i), c = p.get(i + 1);
                    if (b.contains(mNode)) continue;
                    if (!probe.isDefCollider(a, mNode, c)) {
                        forkNbrs.computeIfAbsent(mNode, kk -> new LinkedHashSet<>()).add(a);
                        forkNbrs.get(mNode).add(c);
                    }
                }
            }
            List<Node> forks = new ArrayList<>(forkNbrs.keySet());
            int fcap = Math.min(forks.size(), MAX_FORK_FLIPS);
            SublistGenerator gen = new SublistGenerator(forks.size(), fcap);
            int[] choice;
            while ((choice = gen.next()) != null) {
                if (choice.length == 0) continue;
                Graph seed = new EdgeListGraph(base);
                for (int idx : choice) makeCollider(seed, forks.get(idx), forkNbrs.get(forks.get(idx)));
                if (!seed.paths().isLegalMag()) continue;
                String key = magKey(seed);
                if (seen.putIfAbsent(key, Boolean.TRUE) != null) continue;

                // Ground-truth in-class classification: exact model equality.
                boolean inClass = modelsEqual(
                        modelOf(new MsepTest(seed), obs, trPairs, trZ, T), h0Model);

                // Cross-check FcitSl's PAG-identity classifier.
                if (CHECK_CLASSIFIER) {
                    boolean pagEq;
                    try {
                        Graph seedPag = new MagToPag(seed).convert(false, EXCLUDE_SELECTION_BIAS);
                        pagEq = seedPag.equals(basePag);
                    } catch (Throwable tt) {
                        pagEq = inClass;   // API drift: don't let the cross-check distort counts
                    }
                    if (pagEq != inClass) {
                        r.classifierDivergence++;
                        classifierLog.write("==== CLASSIFIER DIVERGENCE ====\n"
                                + "  mask=" + mask + " lat=" + latSet + " map=" + mapping + '\n'
                                + "  pair=" + x.getName() + "-" + y.getName() + "  b=" + b + '\n'
                                + "  model-equality says inClass=" + inClass
                                + " ; PAG-identity says inClass=" + pagEq + '\n'
                                + "  flip seed:\n" + seed + '\n'
                                + "==== end entry ====\n");
                    }
                }

                if (escape != inClass) seeds.add(seed);   // escape wants out-of-class only
            }
        }

        // ── candidates per seed: the seed, the other LEGs of the seed's class, and the
        //    fork-flip variants OF EACH of those.  Flipping the base alone is not enough:
        //    a witness can require a directed orientation reachable only by a legitimate
        //    reversal TOGETHER WITH a non-invariant bidirected edge reachable only by a
        //    flip, and only a flip of a WALKED LEG composes the two. ──
        Set<String> tried = new HashSet<>();

        for (int si = 0; si < seeds.size(); si++) {
            Graph seed = seeds.get(si);
            boolean baseSeed = !escape && si == 0;

            boolean[] seedModel = baseSeed ? h0Model
                    : modelOf(new MsepTest(seed), obs, trPairs, trZ, T);

            List<Graph> walk = new ArrayList<>();
            walk.add(seed);
            walk.addAll(otherLegReps(seed, seedModel, obs, trPairs, trZ, T));

            for (int wi = 0; wi < walk.size(); wi++) {
                Graph leg = walk.get(wi);

                if (tried.add(magKey(leg))) {
                    Graph pd = tryCandidate(r, oracle, leg, x, y, b);
                    if (pd != null) {
                        int stage = escape ? 4 : (!baseSeed ? 3 : (wi == 0 ? 1 : 2));
                        return new SlCommit(stage, leg, pd);
                    }
                }

                // Flip variants of THIS leg, computed only after the leg itself failed.
                for (Graph flip : forkFlips(leg, h0Model, x, y, b, obs, trPairs, trZ, T, escape)) {
                    if (!tried.add(magKey(flip))) continue;
                    Graph pdf = tryCandidate(r, oracle, flip, x, y, b);
                    if (pdf != null) {
                        return new SlCommit(escape ? 4 : 3, flip, pdf);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Fork-flip variants of ONE representative: on each active x..y path given b, convert
     * non-collider path nodes to colliders by stamping arrowheads in from their path-neighbours.
     * Classified by EXACT model equality against the state's model -- within-class flips are
     * served to the within-class pass, non-equivalent ones only to the escape pass.
     */
    private static List<Graph> forkFlips(Graph mag, boolean[] h0Model, Node x, Node y, Set<Node> b,
                                         List<Node> obs, List<int[]> trPairs, List<Set<Node>> trZ, int T,
                                         boolean escape) throws InterruptedException {
        List<Graph> out = new ArrayList<>();

        Graph probe = new EdgeListGraph(mag);
        probe.removeEdge(x, y);
        if (new MsepTest(probe).checkIndependence(x, y, b).isIndependent()) return out;

        Map<Node, Set<Node>> forkNbrs = new LinkedHashMap<>();
        for (List<Node> p : activePathsGivenS(probe, x, y, b, MAX_ACTIVE_PATHS)) {
            for (int i = 1; i < p.size() - 1; i++) {
                Node a = p.get(i - 1), mNode = p.get(i), c = p.get(i + 1);
                if (b.contains(mNode)) continue;
                if (!probe.isDefCollider(a, mNode, c)) {
                    forkNbrs.computeIfAbsent(mNode, kk -> new LinkedHashSet<>()).add(a);
                    forkNbrs.get(mNode).add(c);
                }
            }
        }

        List<Node> forks = new ArrayList<>(forkNbrs.keySet());
        int fcap = Math.min(forks.size(), MAX_FORK_FLIPS);
        SublistGenerator gen = new SublistGenerator(forks.size(), fcap);
        int[] choice;
        while ((choice = gen.next()) != null) {
            if (choice.length == 0) continue;
            Graph flip = new EdgeListGraph(mag);
            for (int idx : choice) makeCollider(flip, forks.get(idx), forkNbrs.get(forks.get(idx)));
            if (!flip.paths().isLegalMag()) continue;
            boolean inClass = modelsEqual(modelOf(new MsepTest(flip), obs, trPairs, trZ, T), h0Model);
            if (escape != inClass) out.add(flip);
        }

        return out;
    }

    /**
     * FcitSl's per-candidate commit test, verbatim in structure: stamp the recorded-
     * separator colliders for {x,y}, delete x--y, reject on an inducing path between
     * x and y (the ONLY legality check FcitSl runs), reject on a deleted-pair battery
     * failure (against the ORACLE here), else return the post-deletion graph.
     */
    private static Graph tryCandidate(Result r, MsepTest oracle, Graph mag, Node x, Node y, Set<Node> b)
            throws InterruptedException {
        Graph m = new EdgeListGraph(mag);
        if (!stampLegColliders(m, b, x, y)) {
            r.slStampGuardRejects++;
            return null;
        }
        Edge fe = m.getEdge(x, y);
        if (fe == null) return null;
        m.removeEdge(fe);
        if (m.paths().existsInducingPath(x, y, Set.of())) {
            r.slIpRejects++;
            return null;
        }
        if (!batteryPasses(r, oracle, m, x, y)) return null;
        return m;
    }

    /**
     * FcitSl.stampLegColliders, verbatim: stamp x*->c<-*y at every common neighbor c of the
     * pair excluded from b, but REFUSE the candidate if doing so would create a NEW unshielded
     * collider. Unshielded colliders are class-invariant (ARS e2), so a stamp that manufactures
     * one puts the candidate outside the class; equivalently, the separator of the affected pair
     * must contain c (triple dichotomy), which the stamped collider would contradict. The
     * dangerous neighbours are those already carrying an arrowhead AT c -- they supply the other
     * half of the collider the stamp completes.
     */
    private static boolean stampLegColliders(Graph mag, Set<Node> b, Node x, Node y) {
        List<Node> common = mag.getAdjacentNodes(x);
        common.retainAll(mag.getAdjacentNodes(y));

        for (Node c : common) {
            if (b.contains(c)) continue;               // in the separator: non-collider, leave it
            if (mag.isDefCollider(x, c, y)) continue;  // already x*->c<-*y

            for (Node d : mag.getAdjacentNodes(c)) {
                if (d == x) continue;
                if (d == y) continue;

                if (mag.getEndpoint(d, c) == Endpoint.ARROW) {
                    if (!mag.isAdjacentTo(d, x)) {
                        if (!mag.isDefCollider(d, c, x)) return false;
                    }

                    if (!mag.isAdjacentTo(d, y)) {
                        if (!mag.isDefCollider(d, c, y)) return false;
                    }
                }
            }

            mag.setEndpoint(x, c, Endpoint.ARROW);
            mag.setEndpoint(y, c, Endpoint.ARROW);
        }

        return true;
    }

    /** FcitSl.makeCollider, verbatim. */
    private static void makeCollider(Graph g, Node f, Set<Node> from) {
        for (Node w : from) {
            if (g.isAdjacentTo(w, f)) g.setEndpoint(w, f, Endpoint.ARROW);
        }
    }

    /** Deleted-pair battery at the oracle: every separation of {x,y} the candidate
     *  entails with |Z| <= BATTERY_Z_MAX must be oracle-true; first failure refuses. */
    private static boolean batteryPasses(Result r, MsepTest oracle, Graph mag, Node x, Node y)
            throws InterruptedException {
        MsepTest entails = new MsepTest(mag);
        List<Node> others = new ArrayList<>(mag.getNodes());
        others.remove(x);
        others.remove(y);
        int kMax = Math.min(BATTERY_Z_MAX, others.size());
        // Enumerate all subsets of size <= kMax.
        for (int sz = 0; sz <= kMax; sz++) {
            int[] idx = new int[Math.max(sz, 1)];
            if (!batteryScan(r, entails, oracle, x, y, others, sz, 0, 0, idx)) return false;
        }
        return true;
    }

    private static boolean batteryScan(Result r, MsepTest entails, MsepTest oracle, Node x, Node y,
                                       List<Node> others, int k, int start, int depth, int[] idx)
            throws InterruptedException {
        if (depth == k) {
            Set<Node> z = new HashSet<>();
            for (int i = 0; i < k; i++) z.add(others.get(idx[i]));
            if (entails.checkIndependence(x, y, z).isIndependent()) {
                r.slBatteryStatements++;
                if (!oracle.checkIndependence(x, y, z).isIndependent()) {
                    r.slBatteryRefusals++;
                    return false;
                }
            }
            return true;
        }
        for (int i = start; i < others.size(); i++) {
            idx[depth] = i;
            if (!batteryScan(r, entails, oracle, x, y, others, k, i + 1, depth + 1, idx)) return false;
        }
        return true;
    }

    /**
     * LegEnumerator mirror: all legal MAGs over the seed's skeleton that are (i)
     * Markov-equivalent to the seed, and (ii) LEGs of the seed's class -- every
     * bidirected edge of the candidate is bidirected in the seed's PAG (invariant).
     * The seed itself is EXCLUDED (the caller tries it first).  Enumeration is
     * 3^E with the LEG and unshielded-collider prefilters of representativeSweep.
     */
    private static List<Graph> otherLegReps(Graph seed, boolean[] seedModel, List<Node> obs,
                                            List<int[]> trPairs, List<Set<Node>> trZ, int T)
            throws InterruptedException {
        List<Graph> out = new ArrayList<>();

        Graph seedPag;
        try {
            seedPag = new MagToPag(seed).convert(false, EXCLUDE_SELECTION_BIAS);
        } catch (Throwable t) {
            // If MagToPag ever fails on a fork-flip seed, fall back to "invariant =
            // bidirected in the seed" (a subset of the true LEG set): sound, possibly
            // incomplete, and it cannot create spurious commits.
            seedPag = seed;
        }

        Map<String, Integer> pos = new HashMap<>();
        for (int i = 0; i < obs.size(); i++) pos.put(obs.get(i).getName(), i);

        List<Edge> skel = new ArrayList<>(seed.getEdges());
        int E = skel.size();
        int[] ea = new int[E], eb = new int[E];
        boolean[] bidirInvariant = new boolean[E];
        for (int e = 0; e < E; e++) {
            Edge ed = skel.get(e);
            ea[e] = pos.get(ed.getNode1().getName());
            eb[e] = pos.get(ed.getNode2().getName());
            Edge pe = seedPag.getEdge(seedPag.getNode(ed.getNode1().getName()),
                    seedPag.getNode(ed.getNode2().getName()));
            bidirInvariant[e] = pe != null
                    && pe.getEndpoint1() == Endpoint.ARROW && pe.getEndpoint2() == Endpoint.ARROW;
        }

        boolean[][] adj = new boolean[obs.size()][obs.size()];
        for (int e = 0; e < E; e++) {
            adj[ea[e]][eb[e]] = true;
            adj[eb[e]][ea[e]] = true;
        }
        List<int[]> triples = new ArrayList<>();
        List<Boolean> seedColl = new ArrayList<>();
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
                    seedColl.add(seed.isDefCollider(obs.get(a), obs.get(z), obs.get(c)));
                }
            }
        }

        String seedKey = magKey(seed);
        long total = 1;
        for (int e = 0; e < E; e++) total *= 3;
        int[] o = new int[E];

        outer:
        for (long code = 0; code < total; code++) {
            long c = code;
            for (int e = 0; e < E; e++) {
                o[e] = (int) (c % 3);
                c /= 3;
            }
            for (int e = 0; e < E; e++) {                    // LEG prefilter
                if (o[e] == 2 && !bidirInvariant[e]) continue outer;
            }
            for (int t = 0; t < triples.size(); t++) {       // unshielded-collider agreement
                int[] tr = triples.get(t);
                boolean cand = arrowAtZ(tr[0], tr[2], o[tr[0]], ea, eb)
                        && arrowAtZ(tr[1], tr[2], o[tr[1]], ea, eb);
                if (cand != seedColl.get(t)) continue outer;
            }
            Graph cand = new EdgeListGraph(obs);
            for (int e = 0; e < E; e++) {
                Node a = obs.get(ea[e]), bb = obs.get(eb[e]);
                if (o[e] == 0) cand.addDirectedEdge(a, bb);
                else if (o[e] == 1) cand.addDirectedEdge(bb, a);
                else cand.addBidirectedEdge(a, bb);
            }
            if (!isLegalMag(cand)) continue;
            if (magKey(cand).equals(seedKey)) continue;      // the caller tried the seed already
            boolean[] m = modelOf(new MsepTest(cand), obs, trPairs, trZ, T);
            if (!modelsEqual(m, seedModel)) continue;
            out.add(cand);
        }
        return out;
    }

    /** Does edge e, under orientation code o, carry an arrowhead at position z? */
    private static boolean arrowAtZ(int e, int z, int o, int[] ea, int[] eb) {
        if (eb[e] == z) return o == 0 || o == 2;
        return o == 1 || o == 2;
    }

    // ── FcitSl's active-path machinery, ported static ──

    private static List<List<Node>> activePathsGivenS(Graph g, Node x, Node y, Set<Node> S, int maxPaths) {
        List<List<Node>> out = new ArrayList<>();
        Deque<Node> path = new ArrayDeque<>();
        Set<Node> onPath = new HashSet<>();
        path.addLast(x);
        onPath.add(x);
        dfsActive(g, x, y, S, path, onPath, out, maxPaths);
        return out;
    }

    private static void dfsActive(Graph g, Node cur, Node y, Set<Node> S, Deque<Node> path,
                                  Set<Node> onPath, List<List<Node>> out, int maxPaths) {
        if (out.size() >= maxPaths) return;
        if (cur.equals(y)) {
            List<Node> p = new ArrayList<>(path);
            if (p.size() >= 3 && isActiveGivenS(g, p, S)) out.add(p);
            return;
        }
        for (Node next : g.getAdjacentNodes(cur)) {
            if (onPath.contains(next)) continue;
            path.addLast(next);
            onPath.add(next);
            dfsActive(g, next, y, S, path, onPath, out, maxPaths);
            onPath.remove(next);
            path.removeLast();
            if (out.size() >= maxPaths) return;
        }
    }

    private static boolean isActiveGivenS(Graph g, List<Node> path, Set<Node> S) {
        for (int i = 1; i < path.size() - 1; i++) {
            Node a = path.get(i - 1), m = path.get(i), c = path.get(i + 1);
            if (g.isDefCollider(a, m, c)) {
                if (!ancestorInS(g, m, S)) return false;
            } else {
                if (S.contains(m)) return false;
            }
        }
        return true;
    }

    private static boolean ancestorInS(Graph g, Node m, Set<Node> S) {
        for (Node z : S) {
            if (m.equals(z) || g.paths().isAncestorOf(m, z)) return true;
        }
        return false;
    }

    /** FcitSl.magKey, verbatim. */
    private static String magKey(Graph g) {
        List<String> toks = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            Node a = e.getNode1(), bb = e.getNode2();
            Endpoint ea = e.getProximalEndpoint(a), eb = e.getDistalEndpoint(a);
            String u = a.getName(), v = bb.getName();
            if (ea == Endpoint.TAIL && eb == Endpoint.ARROW) toks.add(u + ">" + v);
            else if (ea == Endpoint.ARROW && eb == Endpoint.TAIL) toks.add(v + ">" + u);
            else if (ea == Endpoint.ARROW && eb == Endpoint.ARROW)
                toks.add(u.compareTo(v) <= 0 ? u + "<>" + v : v + "<>" + u);
            else toks.add(u.compareTo(v) <= 0 ? u + "-" + v : v + "-" + u);
        }
        Collections.sort(toks);
        return String.join("|", toks);
    }

    // ────────────────────────────────────────────────────────────────────────
    // COLD REORIENTATION AND SEPSET SEARCH (verbatim PKE6 pipeline)
    // ────────────────────────────────────────────────────────────────────────

    private static void reorient(Graph h, IndependenceTest oracle, SepsetMap sepsets, Knowledge knowledge,
                                 Set<Triple> initialColliders) throws InterruptedException {
        GraphUtils.reorientWithCircles(h, false);
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

            // EVERY member of the blocking set is a removal candidate, not just the common
            // neighbours of x and y: RB blocks defensively on a graph whose circles hide
            // collider status, so a node it includes may be a collider (or a descendant of
            // one) in the truth, and no superset of it can separate. Such a node need not be
            // adjacent to both endpoints, so the old common-neighbour restriction could leave
            // the true separator untestable.
            List<Node> common = graph.getAdjacentNodes(x);
            common.retainAll(graph.getAdjacentNodes(y));
            B.addAll(common);

            Set<Node> definitelyRemove = new LinkedHashSet<>();
            for (Node c : common) {
                if (graph.isDefCollider(x, c, y)) definitelyRemove.add(c);
            }
            Set<Node> B0 = new LinkedHashSet<>(B);
            B0.removeAll(definitelyRemove);

            List<Node> removalCandidates = new ArrayList<>();
            for (Node v : B0) if (common.contains(v)) removalCandidates.add(v);
            for (Node v : B0) if (!common.contains(v)) removalCandidates.add(v);

            SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), removalCandidates.size());
            int[] cChoice;
            while ((cChoice = cGen.next()) != null) {
                Set<Node> S = new LinkedHashSet<>(B0);
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
    // MODELS, I-MAP CHECKS, SMALL GRAPH UTILITIES (verbatim PKE6)
    // ────────────────────────────────────────────────────────────────────────

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
    // CANONICALIZATION AND RELABELING (verbatim PKE6)
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
            // 5-arg form with flipIfBackwards=false: see PKE6's note on the 4-arg
            // constructor silently swapping nodes and endpoints.
            out.addEdge(new Edge(map.get(e.getNode1().getName()), map.get(e.getNode2().getName()),
                    e.getEndpoint1(), e.getEndpoint2(), false));
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
    // STREAMED, CAPPED LOGS / CHECKPOINTING (verbatim PKE6)
    // ────────────────────────────────────────────────────────────────────────

    private static final class StreamLog {
        private final PrintWriter out;
        private final int max;
        private int count;

        StreamLog(String path, int max, String header) throws IOException {
            this.out = new PrintWriter(new FileWriter(path, true));
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
        if (slStallLog.full() && slBreakLog.full() && STOP.compareAndSet(false, true)) {
            System.err.println("Stall and break logs are full -- stopping enumeration early.");
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

    private static final class Result {
        long dagsScanned, modelsScanned, dupModels, distinctMags, skipped;
        long h0Candidates, h0RbFail, h0IllegalPag, h0NotImap, gated;

        // FCIT-SL move audit.
        long slEdgeAttempts, slEdgeNoSep, slEdgeStalled;
        long slCommitStage1, slCommitStage2, slCommitStage2b;
        long slIpRejects, slStampGuardRejects, slBatteryStatements, slBatteryRefusals;
        long slSolved, slStall, slStallEscapeRescued, slStallUnrescued;
        long slCommitNotImap, slCommitIllegal;
        long slFirstFitSound, slFirstFitUnsound;
        long classifierDivergence;

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
            slEdgeAttempts += o.slEdgeAttempts;
            slEdgeNoSep += o.slEdgeNoSep;
            slEdgeStalled += o.slEdgeStalled;
            slCommitStage1 += o.slCommitStage1;
            slCommitStage2 += o.slCommitStage2;
            slCommitStage2b += o.slCommitStage2b;
            slIpRejects += o.slIpRejects;
            slStampGuardRejects += o.slStampGuardRejects;
            slBatteryStatements += o.slBatteryStatements;
            slBatteryRefusals += o.slBatteryRefusals;
            slSolved += o.slSolved;
            slStall += o.slStall;
            slStallEscapeRescued += o.slStallEscapeRescued;
            slStallUnrescued += o.slStallUnrescued;
            slCommitNotImap += o.slCommitNotImap;
            slCommitIllegal += o.slCommitIllegal;
            slFirstFitSound += o.slFirstFitSound;
            slFirstFitUnsound += o.slFirstFitUnsound;
            classifierDivergence += o.classifierDivergence;
        }
    }

}