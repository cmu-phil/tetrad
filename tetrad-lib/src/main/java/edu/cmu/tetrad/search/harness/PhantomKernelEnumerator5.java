/// ////////////////////////////////////////////////////////////////////////////
// PhantomKernelEnumerator5.java  (parallel, deduplicated, checkpointed)       //
//                                                                             //
// DIAGNOSTIC successor to PhantomKernelEnumerator4.  Same enumeration, dedup, //
// pipeline, and two populations (canonical stalls + step-breaks), re-derived  //
// by re-running rather than by parsing PKE4's logs, so the stall/step-break   //
// counts MUST match PKE4 (8 and 58 at N=7/|L|=2/|Spur|<=3, scope=LEG_ONLY) as //
// a built-in consistency check.  On top of that, PKE5 answers the two follow- //
// up questions PKE4 left open:                                                //
//                                                                             //
//   (A) STEP-BREAK MECHANISM CLASSIFIER (decides conj:legal-genuine at this   //
//       scope).  Each legal non-I-map H1 is re-binned by the mechanism of its //
//       false collider, using the SAME leg-test machinery as PKE2:            //
//         R0         : PAG unshielded def-collider on a spurious leg;         //
//         R4         : shielded spurious-leg collider in H1's MAG;            //
//         COMPLETION : unshielded spurious-leg collider realized only by MAG  //
//                      completion (circles in the PAG);                       //
//         RESIDUE    : NO collider on any spurious leg -- an all-real-edge    //
//                      break.  This is the alarming bucket: a RESIDUE break   //
//                      is a legal non-Markov waypoint that collider-          //
//                      genuineness does NOT flag, i.e. a REFUTATION of        //
//                      conj:legal-genuine at N=7/|L|=2.  RESIDUE=0 confirms    //
//                      the certificate's detector-completeness at this scope  //
//                      (the N=6 expectation).  RESIDUE cases are dumped in    //
//                      full; the other buckets are tallied.                   //
//                                                                             //
//   (B) FCIT-ZM SINGLE-ZHANG-MAG RESCUE (substantiates or refutes "FCIT-ZM    //
//       stalls less readily than FCIT" as DISTINCT from conj:mag-meek).  For  //
//       each canonical stall, in addition to PKE4's LEG sweep (free choice of //
//       realizing MAG over the class, restricted to LEGs), PKE5 runs the      //
//       actual FCIT-ZM move: form the ONE canonical Zhang MAG of H0, delete   //
//       each spurious edge THERE, stamp recorded-separator colliders, require //
//       a legal MAG, and test I-map of G*.  A stall RESCUED_BY_ZM is cleared  //
//       by FCIT-ZM itself (single fixed MAG); a stall the LEG sweep rescues   //
//       but ZM does NOT needs the richer free-MAG-choice move set that        //
//       neither FCIT nor FCIT-ZM implements -- an FCIT-ZM stall, cleared only //
//       by conj:mag-meek's search or the saturating step.  The two verdicts   //
//       are logged side by side per stall.                                    //
//                                                                             //
// Everything else (constants, dedup, cold reorient, legality/I-map gates,     //
// LEG sweep, checkpointing, streamed capped logs) is IDENTICAL to PKE4.  Two  //
// extra logs are written: pke5_stepbreak_mechanism.log and                    //
// pke5_stall_zm_rescue.log.  The stall/step-break logs and their caps are as  //
// in PKE4.                                                                    //
//                                                                             //
// ---- original PKE4 banner follows ----                                      //
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
// NOTE: FCIT-ZM's full step closes with a MAG->PAG projection
// (`new MagToPag(mag).convert(false, EXCLUDE_SELECTION_BIAS)` in PKE2).  The
// RESCUE VERDICT here is decided at the MAG level -- legal MAG + I-map of G* --
// which is exactly what determines whether FcitMag commits the step, so the
// PAG projection is not needed and is deliberately omitted.  If you want the
// projected PAG in the log, import edu.cmu.tetrad.search.utils.MagToPag and add
// it to formatZm; verify the signature against your Tetrad first.

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

public final class PhantomKernelEnumerator5 {

    // ────────────────────────────────────────────────────────────────────────
    // CONFIGURATION (all hard-coded; edit and re-run)
    // ────────────────────────────────────────────────────────────────────────

    /** Total nodes in the enumerated DAGs. */
    static final int N = 7;                                  // package-private: read by Pke5DeterminismCheck
    /** Number of latent nodes per placement. */
    static final int NUM_LATENT = 2;                          // package-private: read by Pke5DeterminismCheck
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

    // ── RESIDUE fault-trace ──────────────────────────────────────────────────
    // Set true to print sepset-map record / pre-reorient / post-reorient state for
    // the two RESIDUE exemplar masks (314672, 400048), localizing where the V2-V3
    // sepset entry is lost between fcitSpanningSepset recording it and the reorient
    // consuming it.  Set false for production runs.
    private static final boolean RESIDUE_TRACE = true;

    /** Canonicalize dedup keys over all 120 permutations of the observed
     *  nodes (max dedup).  false = relabel-by-sorted-index only. */
    private static final boolean CANONICALIZE_PERMS = true;

    // Log files (created/appended in the current working directory).
    /** Canonical stalls (legal I-map H0, no legal single deletion), each
     *  annotated with its representative-sweep verdict. */
    private static final String STALL_LOG_PATH = "pke5_stall_counterexamples.log";
    /** The first stall that SURVIVES the strongest configured test. */
    private static final String FIRST_STALL_LOG_PATH = "pke5_first_stall_counterexample.log";
    /** Legal non-I-map single-edge waypoints from I-map H0s. */
    private static final String STEP_BREAK_LOG_PATH = "pke5_step_imap_breaks.log";
    /** (A) Per-step-break mechanism classification; RESIDUE cases dumped in full. */
    private static final String MECH_LOG_PATH = "pke5_stepbreak_mechanism.log";
    /** (A') RESIDUE drill-down only: DISPLACED / CLASSIFIER-GAP / HARD-RESIDUE per case. */
    private static final String RESIDUE_LOG_PATH = "pke5_residue_drilldown.log";
    /** (B) Per-stall FCIT-ZM single-Zhang-MAG rescue verdict vs the LEG sweep. */
    private static final String ZM_RESCUE_LOG_PATH = "pke5_stall_zm_rescue.log";
    /** Completed block ids for resume. Delete to restart from scratch. */
    private static final String CHECKPOINT_PATH = "pke5_checkpoint.txt";

    // Per-log caps, counted in EXAMPLES (not bytes), per run.  A full log goes
    // silent; when ALL capped logs are full, enumeration stops early.
    private static final int STALL_LOG_MAX = 200;
    private static final int STEP_BREAK_LOG_MAX = 500;

    /** Masks per checkpoint block: 2^12 = 4096 -> 512 blocks over 2^21 masks. */
    static final long BLOCK_SIZE = 1L << 12;                  // package-private: read by Pke5DeterminismCheck

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
    static final long TOTAL_DAGS = 1L << P;                   // package-private: read by Pke5DeterminismCheck
    private static final int[][] PAIR = buildPairs();
    private static final List<int[]> PERMS = buildPerms();

    static final String CONFIG_LINE = String.format(          // package-private: read by Pke5DeterminismCheck
            "# PKE5 config: N=%d latent=%d maxSpurious=%d scope=%s canonPerms=%b blockSize=%d",
            N, NUM_LATENT, MAX_SPURIOUS, REPRESENTATIVE_SCOPE, CANONICALIZE_PERMS, BLOCK_SIZE);

    /** Claimed canonical MAG keys.  Claim-first: exactly one thread runs the
     *  pipeline for each distinct key within a run. */
    private static final ConcurrentHashMap<String, Boolean> SEEN = new ConcurrentHashMap<>();

    private static final AtomicBoolean STOP = new AtomicBoolean(false);
    private static final AtomicBoolean FIRST_STALL_WRITTEN = new AtomicBoolean(false);
    private static final AtomicLong ERR_PRINTS = new AtomicLong();

    private static StreamLog stallLog;
    private static StreamLog stepLog;
    private static StreamLog mechLog;    // (A) step-break mechanism classification
    private static StreamLog residueLog; // (A') RESIDUE drill-down
    private static StreamLog zmLog;      // (B) FCIT-ZM single-Zhang-MAG stall rescue

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
        // (A) and (B) logs get generous caps: at this scope the populations are tiny
        // (58 step-breaks, 8 stalls in PKE4), so nothing is realistically suppressed,
        // and neither participates in the all-logs-full early-stop (maybeStop tracks
        // only stall/step, exactly as PKE4).  RESIDUE breaks are the alarming bucket
        // and are dumped in full within STEP_BREAK_LOG_MAX-independent budget.
        mechLog = new StreamLog(MECH_LOG_PATH, Math.max(STEP_BREAK_LOG_MAX, 2000), header
                + "\n# (A) STEP-BREAK MECHANISM: each legal non-I-map H1 re-binned by the mechanism of"
                + "\n# its false collider -- R0 / R4(MAG shielded) / COMPLETION(MAG-only unshielded) /"
                + "\n# RESIDUE(no collider on any spurious leg).  RESIDUE dumped in full: a RESIDUE break"
                + "\n# is a legal non-Markov waypoint collider-genuineness does NOT flag -- a refutation"
                + "\n# of conj:legal-genuine at this scope.  RESIDUE=0 confirms the N=6 expectation.");
        residueLog = new StreamLog(RESIDUE_LOG_PATH, Math.max(STEP_BREAK_LOG_MAX, 2000), header
                + "\n# (A') RESIDUE DRILL-DOWN.  Each RESIDUE step-break separated into:"
                + "\n#   DISPLACED      -- all-real-leg unsound triple exists (propagated onto real"
                + "\n#                     edges; refutes conj:legal-genuine as LOCALIZATION, consistent"
                + "\n#                     with spur-local(2)).  Expected bucket."
                + "\n#   CLASSIFIER-GAP -- break survives spurious-leg neutralization; a mark the binner"
                + "\n#                     did not classify carried it.  A PKE5 BUG, not a refutation."
                + "\n#   HARD-RESIDUE   -- neither: contradicts Lemma spur-local head-on.  Alarming;"
                + "\n#                     expect empty.  Verify any such case by hand.");
        zmLog = new StreamLog(ZM_RESCUE_LOG_PATH, Math.max(STALL_LOG_MAX, 2000), header
                + "\n# (B) FCIT-ZM RESCUE: per canonical stall, the actual FCIT-ZM move -- form the ONE"
                + "\n# canonical Zhang MAG of H0, delete each spurious edge THERE, stamp recorded-separator"
                + "\n# colliders, require a legal MAG, test I-map of G*.  RESCUED_BY_ZM => FCIT-ZM clears"
                + "\n# it with a single fixed MAG; NOT_RESCUED_BY_ZM (while the LEG sweep rescues) => an"
                + "\n# FCIT-ZM stall needing free-MAG choice (conj:mag-meek) or the saturating step.");

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
                    .collect(Result::new, PhantomKernelEnumerator5::accumulate, Result::merge);
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
        mechLog.summary("\n" + summary);
        residueLog.summary("\n" + summary);
        zmLog.summary("\n" + summary);
        stallLog.close();
        stepLog.close();
        mechLog.close();
        residueLog.close();
        zmLog.close();
    }

    private static String summarize(Result t, long blocksThisRun, long numBlocks, long resumedBlocks,
                                    long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== PKE5 SUMMARY (this run only; caps and counters are per-run) ====\n");
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
        long mechTotal = t.mechR0 + t.mechR4Shielded + t.mechCompletion + t.mechResidue;
        sb.append(String.format("  (A) mechanism -- R0              : %d%n", t.mechR0));
        sb.append(String.format("                   R4 (shielded)   : %d%n", t.mechR4Shielded));
        sb.append(String.format("                   COMPLETION      : %d%n", t.mechCompletion));
        sb.append(String.format("                   RESIDUE         : %d%s%n", t.mechResidue,
                t.mechResidue == 0
                        ? "   (conj:legal-genuine detector-complete at this scope)"
                        : "   (see drill-down below -- do NOT read as refuted until classified)"));
        if (t.mechResidue > 0) {
            sb.append(String.format("      RESIDUE drill-down -- DISPLACED     : %d%s%n",
                    t.residueDisplaced,
                    t.residueDisplaced > 0
                            ? "  (collider-genuine yet non-Markov -- refutes conj:legal-genuine)"
                            : ""));
            if (t.residueDisplaced > 0) {
                sb.append(String.format("        all-reps: some rep exposes sep-leg : %d%s%n",
                        t.displacedSomeRepExposes,
                        t.displacedSomeRepExposes > 0
                                ? "  (certificate SURVIVES the \"exists a MAG\" reading; canonical reading refuted)"
                                : ""));
                sb.append(String.format("        all-reps: NO rep exposes sep-leg   : %d%s%n",
                        t.displacedNoRepExposes,
                        t.displacedNoRepExposes > 0
                                ? "  *** refuted under BOTH readings: collider-genuine in EVERY representative ***"
                                : ""));
            }
            sb.append(String.format("                            CLASSIFIER-GAP: %d%s%n",
                    t.residueClassifierGap,
                    t.residueClassifierGap > 0
                            ? "  *** PKE5 binner gap, NOT a refutation -- fix the classifier ***"
                            : ""));
            sb.append(String.format("                            HARD-RESIDUE  : %d%s%n",
                    t.residueHard,
                    t.residueHard > 0
                            ? "  *** contradicts Lemma spur-local -- verify by hand before believing ***"
                            : "  (none: no spur-local contradiction)"));
            if (t.residueOther > 0) {
                sb.append(String.format("                            (unclassified): %d%n", t.residueOther));
            }
            long rsum = t.residueDisplaced + t.residueClassifierGap + t.residueHard + t.residueOther;
            sb.append(String.format("      RESIDUE readings total (== RESIDUE) : %d%s%n", rsum,
                    rsum == t.mechResidue ? "  (partitions RESIDUE -- OK)"
                            : "  *** != RESIDUE: drill-down gap, INVESTIGATE ***"));
        }
        sb.append(String.format("      mechanism total (== breaks)  : %d%s%n", mechTotal,
                mechTotal == t.stepBreaks ? "  (partitions the step-breaks -- OK)"
                        : "  *** != step-breaks: classifier gap, INVESTIGATE ***"));
        sb.append(String.format("CANONICAL STALLS (I-map H0)        : %d (suppressed over cap: %d)%n",
                t.canonicalStalls, t.stallSuppressed));
        sb.append(String.format("  rescued by representative sweep  : %d%n", t.stallRescued));
        sb.append(String.format("  SURVIVING (counterexample cand.) : %d%n", t.stallSurvives));
        sb.append(String.format("  (B) FCIT-ZM rescued (single MAG) : %d%n", t.zmRescued));
        sb.append(String.format("      FCIT-ZM NOT rescued          : %d%s%n", t.zmNotRescued,
                (t.zmNotRescued > 0 && t.stallRescued == t.canonicalStalls)
                        ? "   (FCIT-ZM stalls: LEG sweep clears them, single Zhang MAG does not)"
                        : ""));
        sb.append(String.format("        of which MAG illegal on del: %d%n", t.zmMagIllegalOnDelete));
        sb.append(String.format("        of which no spurious in MAG: %d%n", t.zmNoLegalDeletion));
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

    // ────────────────────────────────────────────────────────────────────────
    // DETERMINISM FINGERPRINT  (used by Pke5DeterminismCheck; never by the run)
    // ────────────────────────────────────────────────────────────────────────
    //
    // fingerprintModel mirrors accumulate + runPipeline DECISION FOR DECISION:
    // same model construction, same helper calls in the same order, same gates.
    // It is pure -- touches no logs, no Result, no SEEN, no STOP -- and returns
    // the decisions as text.  IF YOU EDIT runPipeline, EDIT THIS IN LOCKSTEP;
    // the determinism check is meaningful only while the two are
    // decision-identical.
    //
    // Two fingerprints per call:
    //   code   : verdict codes only.  Instability here is a VERDICT flip --
    //            the maximality-prong nondeterminism, if live, shows up here.
    //   strict : code plus reason strings, false-CI witnesses, mechanism detail,
    //            and sweep/ZM counters.  A strict-only flip (code stable) means
    //            wording or detail varies while verdicts hold -- worth knowing,
    //            not alarming on its own.

    static final class Fp {
        String key;
        String code;
        String strict;
    }

    /** Canonical key only (cheap: dagToMag + canonicalization, no pipeline).
     *  Same defensive checks as accumulate; throws on API drift. */
    static String canonicalKeyForModel(long mask, int[] latChoice) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < N; i++) nodes.add(new GraphNode("X" + (i + 1)));
        Graph dag = new EdgeListGraph(nodes);
        for (int b = 0; b < P; b++) {
            if ((mask & (1L << b)) != 0) {
                dag.addDirectedEdge(nodes.get(PAIR[b][0]), nodes.get(PAIR[b][1]));
            }
        }
        for (int li : latChoice) nodes.get(li).setNodeType(NodeType.LATENT);
        Graph trueMag = GraphTransforms.dagToMag(dag);
        if (trueMag.getNumNodes() != OBS) {
            throw new IllegalStateException("dagToMag returned " + trueMag.getNumNodes()
                    + " nodes; expected " + OBS + ".");
        }
        for (Node v : trueMag.getNodes()) {
            if (v.getNodeType() == NodeType.LATENT) {
                throw new IllegalStateException("dagToMag output contains a latent node: " + v);
            }
        }
        List<Node> obsSorted = new ArrayList<>(trueMag.getNodes());
        obsSorted.sort(Comparator.comparingInt(nd -> Integer.parseInt(nd.getName().substring(1))));
        return canonicalKey(trueMag, obsSorted).key;
    }

    /** Full pure fingerprint of one (mask, latent-choice) model. */
    static Fp fingerprintModel(long mask, int[] latChoice) throws Exception {
        // ---- construction: mirrors accumulate ----
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
                    + " nodes; expected " + OBS + ".");
        }
        for (Node v : trueMag.getNodes()) {
            if (v.getNodeType() == NodeType.LATENT) {
                throw new IllegalStateException("dagToMag output contains a latent node: " + v);
            }
        }
        List<Node> obsSorted = new ArrayList<>(trueMag.getNodes());
        obsSorted.sort(Comparator.comparingInt(nd -> Integer.parseInt(nd.getName().substring(1))));
        Canon canon = canonicalKey(trueMag, obsSorted);

        Graph truePag = GraphTransforms.dagToPag(dag, new Knowledge(),
                EXCLUDE_SELECTION_BIAS, RECURSIVE_DEPTH);
        List<Node> canonNodes = new ArrayList<>();
        for (int q = 0; q < OBS; q++) canonNodes.add(new GraphNode("V" + (q + 1)));
        Graph canonMag = relabel(trueMag, obsSorted, canon.perm, canonNodes);
        Graph canonPag = relabel(truePag, obsSorted, canon.perm, canonNodes);
        List<Node> obs = canonNodes;

        // ---- pipeline: mirrors runPipeline, writing codes instead of logs ----
        StringBuilder code = new StringBuilder();
        StringBuilder strict = new StringBuilder();

        MsepTest oracle = new MsepTest(canonMag);
        Knowledge knowledge = new Knowledge();
        Set<Triple> initialColliders = noteInitialColliders(obs, canonPag);

        List<int[]> trPairs = new ArrayList<>();
        List<Set<Node>> trZ = new ArrayList<>();
        buildStatements(obs, trPairs, trZ);
        int T = trPairs.size();
        boolean[] truthModel = modelOf(oracle, obs, trPairs, trZ, T);

        List<int[]> nonAdj = nonAdjacentPairs(canonPag, obs);
        if (nonAdj.isEmpty()) {
            code.append("NO-NONADJ\n");
            strict.append("NO-NONADJ\n");
            return fp(canon.key, code, strict);
        }
        List<Node[]> sepPairs = new ArrayList<>();
        for (int[] p : nonAdj) sepPairs.add(new Node[]{obs.get(p[0]), obs.get(p[1])});

        int subsetIdx = 0;
        int cap = Math.min(MAX_SPURIOUS, sepPairs.size());
        for (int k = 1; k <= cap; k++) {
            SublistGenerator spGen = new SublistGenerator(sepPairs.size(), k);
            int[] spChoice;
            while ((spChoice = spGen.next()) != null) {
                if (spChoice.length != k) continue;
                subsetIdx++;

                Graph h0 = new EdgeListGraph(canonPag);
                SepsetMap sepsets = new SepsetMap();
                List<Edge> spurious = new ArrayList<>();
                StringBuilder desc = new StringBuilder("{");
                for (int si : spChoice) {
                    Node a = sepPairs.get(si)[0];
                    Node b2 = sepPairs.get(si)[1];
                    Edge edge = new Edge(a, b2, Endpoint.CIRCLE, Endpoint.CIRCLE);
                    h0.addEdge(edge);
                    spurious.add(edge);
                    if (desc.length() > 1) desc.append(",");
                    desc.append(a.getName()).append("-").append(b2.getName());
                }
                desc.append("}");
                String sPfx = "S" + subsetIdx + desc + ": ";

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
                    code.append(sPfx).append("RBFAIL\n");
                    strict.append(sPfx).append("RBFAIL\n");
                    continue;
                }
                for (int[] p : nonAdj) {
                    Node a = obs.get(p[0]), b2 = obs.get(p[1]);
                    if (h0.isAdjacentTo(a, b2)) continue;
                    if (sepsets.get(a, b2) != null) continue;
                    Set<Node> sep = fcitSpanningSepset(h0, oracle, a, b2);
                    // ORACLE GUARD (P2): record only separators the oracle confirms against M*,
                    // exactly as the removed-edge path does.  RB's spanning separator is valid
                    // for the dirty intermediate H0, but may be oracle-FALSE against M* (it can
                    // include a node that is a true collider in G*); recording it unguarded is
                    // what let an unconfirmed CI drive the reorientation.
                    if (sep != null && oracle.checkIndependence(a, b2, sep).isIndependent()) {
                        sepsets.set(a, b2, sep);
                    }
                }

                reorient(h0, oracle, sepsets, knowledge, initialColliders);
                PagLegalityCheck.LegalPagRet h0ret = PagLegalityCheck.isLegalPag(h0, new HashSet<>());
                if (!h0ret.isLegalPag()) {
                    code.append(sPfx).append("ILLEGAL\n");
                    strict.append(sPfx).append("ILLEGAL :: ").append(h0ret.getReason()).append('\n');
                    continue;
                }
                boolean[] h0Model = modelOf(new MsepTest(magOfPag(h0)), obs, trPairs, trZ, T);
                if (!subsetModel(h0Model, truthModel)) {
                    code.append(sPfx).append("NOTIMAP\n");
                    strict.append(sPfx).append("NOTIMAP :: ")
                            .append(firstFalseCi(h0Model, truthModel, obs, trPairs, trZ)).append('\n');
                    continue;
                }
                code.append(sPfx).append("GATED\n");
                strict.append(sPfx).append("GATED\n");

                int legalEscapes = 0;
                for (Edge e : spurious) {
                    String dPfx = "S" + subsetIdx + " del "
                            + e.getNode1().getName() + "-" + e.getNode2().getName() + ": ";
                    Graph h1 = new EdgeListGraph(h0);
                    Edge present = h1.getEdge(e.getNode1(), e.getNode2());
                    if (present == null) {
                        code.append(dPfx).append("ABSENT\n");
                        strict.append(dPfx).append("ABSENT\n");
                        continue;
                    }
                    h1.removeEdge(present);
                    Set<Node> opSep = fcitSpanningSepset(h1, oracle, e.getNode1(), e.getNode2());
                    if (opSep == null
                            || !oracle.checkIndependence(e.getNode1(), e.getNode2(), opSep).isIndependent()) {
                        code.append(dPfx).append("NOSEP\n");
                        strict.append(dPfx).append("NOSEP\n");
                        continue;
                    }
                    SepsetMap sepsetsH1 = copySepsets(sepsets);
                    sepsetsH1.set(e.getNode1(), e.getNode2(), opSep);

                    reorient(h1, oracle, sepsetsH1, knowledge, initialColliders);
                    PagLegalityCheck.LegalPagRet ret = PagLegalityCheck.isLegalPag(h1, new HashSet<>());
                    if (ret.isLegalPag()) {
                        legalEscapes++;
                        boolean[] h1Model = modelOf(new MsepTest(magOfPag(h1)), obs, trPairs, trZ, T);
                        String falseCi = firstFalseCi(h1Model, truthModel, obs, trPairs, trZ);
                        if (falseCi != null) {
                            // Mechanism classification, exactly as runPipeline.
                            Graph mag1 = magOfPag(h1);
                            String bucket, mech;
                            String r0 = r0NonGenuineFiring(h1, canonPag);
                            if (r0 != null) {
                                bucket = "R0";
                                mech = r0;
                            } else {
                                String sh = magColliderOnSpuriousLeg(mag1, canonPag, h1, true);
                                String un = (sh == null)
                                        ? magColliderOnSpuriousLeg(mag1, canonPag, h1, false) : null;
                                if (sh != null) {
                                    bucket = "R4";
                                    mech = sh;
                                } else if (un != null) {
                                    bucket = "COMPLETION";
                                    mech = un;
                                } else {
                                    bucket = "RESIDUE";
                                    mech = "(no collider on any spurious leg)";
                                }
                            }
                            // Keep decision-identical with runPipeline: run the drill-down
                            // for RESIDUE and fold its reading into the fingerprint, so a
                            // reading flip would show up as instability too.
                            String residueReading = null;
                            if ("RESIDUE".equals(bucket)) {
                                ResidueVerdict frv = classifyResidue(h1, magOfPag(h1), canonPag, canonMag,
                                        obs, falseCi);
                                residueReading = frv.reading;
                                bucket = "RESIDUE-" + residueReading;
                                if (frv.someRepExposesSepLeg != null) {
                                    bucket += (frv.someRepExposesSepLeg ? "-SOMEREP" : "-NOREP");
                                }
                            }
                            code.append(dPfx).append("BREAK-").append(bucket).append('\n');
                            strict.append(dPfx).append("BREAK-").append(bucket)
                                    .append(" :: ").append(falseCi)
                                    .append(" :: ").append(mech).append('\n');
                        } else {
                            code.append(dPfx).append("LEGAL\n");
                            strict.append(dPfx).append("LEGAL\n");
                        }
                    } else {
                        code.append(dPfx).append("ILLEGAL\n");
                        strict.append(dPfx).append("ILLEGAL :: ").append(ret.getReason()).append('\n');
                    }
                }

                if (legalEscapes > 0) continue;

                // Canonical stall: sweep + ZM, exactly as runPipeline.
                SweepOutcome sw = null;
                if (REPRESENTATIVE_SCOPE != RepScope.NONE) {
                    sw = representativeSweep(h0, h0Model, truthModel, obs, trPairs, trZ, T,
                            spurious, REPRESENTATIVE_SCOPE == RepScope.LEG_ONLY);
                }
                ZmOutcome zm = fcitZmRescue(h0, sepsets, truthModel, obs, trPairs, trZ, T, spurious);
                String sweepCode = (sw == null) ? "OFF" : (sw.rescued ? "RESCUED" : "SURVIVES");
                String zmCode = zm.rescued ? "RESCUED" : "NOT";
                String stPfx = "S" + subsetIdx + " STALL: ";
                code.append(stPfx).append("SWEEP=").append(sweepCode)
                        .append(" ZM=").append(zmCode).append('\n');
                strict.append(stPfx).append("SWEEP=").append(sweepCode)
                        .append(sw == null ? "" : " (enum=" + sw.enumerated
                                                  + " prefilter=" + sw.passedPrefilter
                                                  + " equiv=" + sw.equivalents + " legs=" + sw.legs
                                                  + " delChecks=" + sw.deletionChecks + ")")
                        .append(" ZM=").append(zmCode)
                        .append(" (reached=").append(zm.everReachedDeletion)
                        .append(" magIllegal=").append(zm.everMagIllegalOnDelete).append(")")
                        .append('\n');
            }
        }
        return fp(canon.key, code, strict);
    }

    private static Fp fp(String key, StringBuilder code, StringBuilder strict) {
        Fp f = new Fp();
        f.key = key;
        f.code = code.toString();
        f.strict = strict.toString();
        return f;
    }

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
                    // ORACLE GUARD (P2): record only separators the oracle confirms against M*,
                    // exactly as the removed-edge path (line ~697) does.  RB's spanning separator
                    // is valid on the dirty intermediate H0 but may be oracle-FALSE against M*
                    // (it can include a node that is a true collider of G*); recording it unguarded
                    // let an unconfirmed CI drive the reorientation and suppress the true collider.
                    boolean oracleOk = sep != null && oracle.checkIndependence(a, b2, sep).isIndependent();
                    if (oracleOk) sepsets.set(a, b2, sep);
                    if (RESIDUE_TRACE && (mask == 314672L || mask == 400048L) && latSet.size() == 2 && latSet.contains(0) && latSet.contains(1)) {
                        System.err.println("[fill] mask=" + mask + " RB Sep(" + a.getName() + ","
                                + b2.getName() + ")=" + sep + "  oracle-ok=" + oracleOk
                                + (oracleOk ? "  RECORDED" : "  REJECTED (unconfirmed CI, not recorded)"));
                    }
                }

                if (RESIDUE_TRACE && (mask == 314672L || mask == 400048L) && latSet.size() == 2 && latSet.contains(0) && latSet.contains(1)) {
                    // Dump the ENTIRE recorded map right before the H0 reorient, so we see whether
                    // the break pair's entry is present here and lost later, or absent already.
                    StringBuilder ks = new StringBuilder("[pre-H0-reorient] mask=" + mask + " map keys: ");
                    for (Set<Node> k2 : sepsets.keySet()) {
                        List<Node> kk = new ArrayList<>(k2);
                        if (kk.size() == 2) {
                            ks.append(kk.get(0).getName()).append("-").append(kk.get(1).getName())
                                    .append("=").append(sepsets.get(kk.get(0), kk.get(1))).append("  ");
                        }
                    }
                    System.err.println(ks);
                }

                // GATE: legal AND I-map (the population prop:stall / prop:committed-markov
                // quantify over).  No genuineness gate.
                // Snapshot the PRE-reorient H0 (circle-marked skeleton) before reorient
                // mutates it in place: the RESIDUE dump queries RB on this snapshot to test
                // the masking prediction (no oracle-true separator of the break pair is
                // certifiable on the dirty pre-reorient graph, because the spurious circle
                // edge forces the true-collider's descendant into every candidate set).
                Graph h0PreReorient = new EdgeListGraph(h0);
                reorient(h0, oracle, sepsets, knowledge, initialColliders);
                if (RESIDUE_TRACE && (mask == 314672L || mask == 400048L) && latSet.size() == 2 && latSet.contains(0) && latSet.contains(1)) {
                    StringBuilder ks = new StringBuilder("[post-H0-reorient] mask=" + mask + " map keys: ");
                    for (Set<Node> k2 : sepsets.keySet()) {
                        List<Node> kk = new ArrayList<>(k2);
                        if (kk.size() == 2) {
                            ks.append(kk.get(0).getName()).append("-").append(kk.get(1).getName())
                                    .append("=").append(sepsets.get(kk.get(0), kk.get(1))).append("  ");
                        }
                    }
                    System.err.println(ks);
                }
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

                    // PRE-REORIENT SNAPSHOT of the whole map fed into the H1 reorient, so the
                    // RESIDUE provenance can compare INPUT sepsets against post-reorient state
                    // (FciOrient/R4 may mutate the map it is given).  Cheap: a shallow key->set copy.
                    SepsetMap sepsetsH1Input = copySepsets(sepsetsH1);

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

                            // (A) MECHANISM CLASSIFIER.  canonPag plays truePag; h1 is the
                            // legal non-I-map PAG; mag1 = magOfPag(h1).  Buckets partition the
                            // step-breaks: R0 first (PAG unshielded def-collider on a spurious
                            // leg), else R4 (MAG shielded spurious-leg collider), else COMPLETION
                            // (MAG-only unshielded spurious-leg collider), else RESIDUE (no
                            // collider on any spurious leg -- the alarming all-real-edge break).
                            Graph mag1 = magOfPag(h1);
                            String bucket;   // local, thread-confined bucket label
                            String mech;
                            String r0 = r0NonGenuineFiring(h1, canonPag);
                            if (r0 != null) {
                                r.mechR0++;
                                bucket = "R0";
                                mech = "R0 (PAG unshielded): " + r0;
                            } else {
                                String sh = magColliderOnSpuriousLeg(mag1, canonPag, h1, true);
                                String un = (sh == null)
                                        ? magColliderOnSpuriousLeg(mag1, canonPag, h1, false) : null;
                                if (sh != null) {
                                    r.mechR4Shielded++;
                                    bucket = "R4";
                                    mech = "R4 (MAG shielded): " + sh;
                                } else if (un != null) {
                                    r.mechCompletion++;
                                    bucket = "COMPLETION";
                                    mech = "COMPLETION (MAG-only unshielded): " + un;
                                } else {
                                    r.mechResidue++;
                                    bucket = "RESIDUE";
                                    mech = "RESIDUE: no collider on any spurious leg"
                                            + "  *** legal non-Markov waypoint NOT flagged by"
                                            + " collider-genuineness -- see drill-down ***";
                                }
                            }
                            // RESIDUE drill-down: separate DISPLACED / CLASSIFIER-GAP /
                            // HARD-RESIDUE before anyone calls conj:legal-genuine refuted.
                            ResidueVerdict rv = null;
                            if ("RESIDUE".equals(bucket)) {
                                rv = classifyResidue(h1, mag1, canonPag, canonMag, obs, falseCi);
                                switch (rv.reading) {
                                    case "DISPLACED":
                                        r.residueDisplaced++;
                                        if (Boolean.TRUE.equals(rv.someRepExposesSepLeg)) r.displacedSomeRepExposes++;
                                        else if (Boolean.FALSE.equals(rv.someRepExposesSepLeg)) r.displacedNoRepExposes++;
                                        break;
                                    case "CLASSIFIER-GAP": r.residueClassifierGap++; break;
                                    case "HARD-RESIDUE":   r.residueHard++;        break;
                                    default:               r.residueOther++;       break;
                                }
                            }
                            // Tally line for every break; RESIDUE additionally dumped in full.
                            StringBuilder mb = new StringBuilder();
                            mb.append("---- step-break mechanism: ").append(bucket).append(" ----\n");
                            mb.append("  mask=").append(mask).append(" lat=").append(latSet)
                                    .append(" map=").append(mapping).append('\n');
                            mb.append("  spurious=").append(spurious).append("  removed=").append(e).append('\n');
                            mb.append("  false CI (in H1, not G*): ").append(falseCi).append('\n');
                            mb.append("  mechanism: ").append(mech).append('\n');
                            if ("RESIDUE".equals(bucket)) {
                                mb.append("  RESIDUE reading: ").append(rv.reading).append('\n');
                                mb.append(rv.detail);
                                // Fault-localizing sepset provenance for the break pair.
                                Node[] bp = firstFalseCiPair(
                                        modelOf(new MsepTest(mag1), obs, trPairs, trZ, T),
                                        truthModel, obs, trPairs);
                                if (bp != null) {
                                    mb.append(sepsetProvenanceDump(bp[0], bp[1], sepsetsH1, h0, oracle, sepsetsH1Input, h0PreReorient));
                                    mb.append(oracleVsStructureDump(bp[0], bp[1], obs, oracle, canonMag));
                                }
                                mb.append("  true PAG G* (canonical labels):\n").append(canonPag).append('\n');
                                mb.append("  true MAG G* (canonical labels):\n").append(canonMag).append('\n');
                                mb.append("  H0 (legal I-map):\n").append(h0).append('\n');
                                mb.append("  H1 (legal, non-I-map):\n").append(h1).append('\n');
                                mb.append("  MAG of H1 (magOfPag):\n").append(mag1).append('\n');
                            }
                            mechLog.write(mb.toString());
                            if ("RESIDUE".equals(bucket)) residueLog.write(mb.toString());
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

                // (B) FCIT-ZM SINGLE-ZHANG-MAG RESCUE.  The actual FCIT-ZM move uses ONE
                // realizing MAG per step -- the canonical Zhang MAG of the current PAG --
                // not free choice over the class.  For each spurious edge: delete it in
                // magOfPag(h0), stamp recorded-separator colliders (orientSepsetCollidersInMag),
                // require a legal MAG, and test I-map of G* (model subset of truth).  This is
                // strictly narrower than the LEG sweep, so ZM-rescue => LEG-rescue but not
                // conversely; a stall the LEG sweep rescues but ZM does not is an FCIT-ZM stall.
                ZmOutcome zm = fcitZmRescue(h0, sepsets, truthModel, obs, trPairs, trZ, T, spurious);
                if (zm.rescued) r.zmRescued++;
                else {
                    r.zmNotRescued++;
                    if (zm.everMagIllegalOnDelete) r.zmMagIllegalOnDelete++;
                    if (!zm.everReachedDeletion) r.zmNoLegalDeletion++;
                }
                // Cross-check (soft): the Zhang MAG of H0 is a representative the LEG sweep
                // enumerates, but the FCIT-ZM move deletes AND re-stamps recorded-separator
                // colliders, whereas the sweep deletes with no re-stamping -- so the two
                // post-deletion MAGs need not coincide, and ZM rescuing where the sweep does
                // not is possible in principle rather than a guaranteed bug.  It IS worth
                // inspecting (the stamping changed the outcome), so flag it in the entry and
                // to stderr rather than throwing.  (A true machinery drift would instead show
                // up as the sweep's own zero-equivalents assertion.)
                boolean zmExceedsSweep = zm.rescued && sw != null && !sw.rescued;
                if (zmExceedsSweep && ERR_PRINTS.incrementAndGet() < 50) {
                    System.err.println("NOTE: FCIT-ZM rescued a stall the LEG sweep did not "
                            + "(mask=" + mask + " lat=" + latSet + "): recorded-separator stamping "
                            + "changed the post-deletion MAG -- inspect pke5_stall_zm_rescue.log.");
                }
                zmLog.write((zmExceedsSweep
                        ? "# NOTE: ZM rescued where the LEG sweep did not -- stamping-sensitive; inspect.\n"
                        : "") + formatZm(mask, latSet, mapping, spurious, canonPag, h0, sw, zm));

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
    // RESIDUE DRILL-DOWN  (ported from PKE2; runs only on RESIDUE step-breaks)
    // ────────────────────────────────────────────────────────────────────────
    //
    // A RESIDUE step-break is legal, non-I-map, and carries NO collider on any
    // spurious leg in H1's MAG.  That would refute conj:legal-genuine -- BUT it
    // also strains Lemma spur-local, so before believing it we separate three
    // readings, per RESIDUE case:
    //   (1) DISPLACED : an all-real-leg unsound triple exists -- the unsound
    //       mark propagated onto real edges (Remark detect-not-prevent).  This
    //       refutes conj:legal-genuine as a LOCALIZATION claim yet is consistent
    //       with spur-local(2).  Expected bucket.
    //   (2) CLASSIFIER-GAP : the false CI SURVIVES neutralizing every spurious-
    //       leg apex's unsound arrowheads -- i.e. it was carried by a mark the
    //       collider-presence test did not classify as a spurious-leg collider.
    //       A PKE5 bug in the mechanism binner, NOT a refutation.
    //   (3) HARD RESIDUE : neither -- no all-real unsound triple, and the CI
    //       does NOT survive spurious-leg neutralization (so a spurious-leg
    //       mark WAS carrying it after all, but not one shaped as a def-collider
    //       the presence test recognizes).  The genuinely alarming case; expect
    //       empty.  If non-empty this contradicts spur-local head-on.

    private static final class ResidueVerdict {
        String reading;        // DISPLACED | CLASSIFIER-GAP | HARD-RESIDUE | CI-VANISHED
        String displacedTriple;   // nullable
        String survivingCi;       // nullable (the CI that survived neutralization)
        // Quantifier settler for DISPLACED cases: does SOME Markov-equivalent
        // representative of H1 expose a separable-leg collider the leg test would
        // flag?  YES => certificate survives under the "exists a MAG" (all-reps)
        // reading; only the canonical-Zhang-MAG reading is refuted.  NO => refuted
        // under both readings.  null when not computed (non-DISPLACED).
        Boolean someRepExposesSepLeg;
        long repsEnumerated, repsEquivalent;
        String detail;
    }

    /**
     * @param h1        the legal non-I-map PAG
     * @param mag1      magOfPag(h1)
     * @param canonPag  G* as a PAG (== truePag here)
     * @param canonMag  G* as a MAG (the true MAG; trueMsep basis)
     * @param falseCi   the false CI the mechanism binner already found
     */
    private static ResidueVerdict classifyResidue(Graph h1, Graph mag1, Graph canonPag, Graph canonMag,
                                                  List<Node> obs, String falseCi) throws InterruptedException {
        ResidueVerdict v = new ResidueVerdict();
        MsepTest trueMsep = new MsepTest(canonMag);

        // (1) Displaced unsound mark: a triple both of whose legs are real in G*,
        // whose collider status mag1 and G* disagree on.
        v.displacedTriple = allRealLegUnsoundTriple(mag1, canonPag, canonMag);

        // (2)/(3) Responsibility: does any false CI survive neutralizing the unsound
        // arrowheads at every spurious-leg apex?  For a RESIDUE case there is no
        // spurious-leg *def-collider*, but isSpuriousLegApex may still find an apex
        // whose leg is spurious; if it finds none, no neutralization happens and a
        // surviving CI simply confirms the break is carried by non-spurious-apex marks.
        v.survivingCi = stepBreakResidualBlock(mag1, canonPag, canonMag, obs, trueMsep);

        StringBuilder d = new StringBuilder();
        if (v.displacedTriple != null) {
            v.reading = "DISPLACED";
            d.append("  displaced unsound mark (all-real legs): ").append(v.displacedTriple).append('\n');
            d.append("  => H1's Zhang MAG has NO separable-leg collider yet H1 is legal & non-Markov:\n");
            d.append("     collider-genuine yet non-Markov -- conj:legal-genuine REFUTED under the\n");
            d.append("     canonical-Zhang-MAG (\"its MAG\") reading.\n");
            // Quantifier settler: does ANY Markov-equivalent representative of H1
            // expose a separable-leg collider?  Decides the "exists a MAG" reading.
            boolean[] found = someRepresentativeExposesSeparableLeg(h1, canonPag, obs, v);
            v.someRepExposesSepLeg = found[0];
            if (found[0]) {
                d.append("  ALL-REPS reading: SOME equivalent representative exposes a separable-leg\n");
                d.append("     collider (of ").append(v.repsEquivalent).append(" equivalents, enum ")
                        .append(v.repsEnumerated).append(") -- certificate SURVIVES under the\n");
                d.append("     \"exists a realizing MAG\" reading; only the canonical reading is refuted.\n");
            } else {
                d.append("  ALL-REPS reading: NO equivalent representative exposes a separable-leg\n");
                d.append("     collider (of ").append(v.repsEquivalent).append(" equivalents, enum ")
                        .append(v.repsEnumerated).append(") -- collider-genuine under EVERY\n");
                d.append("     representative yet non-Markov: conj:legal-genuine REFUTED under BOTH the\n");
                d.append("     canonical and the \"exists a MAG\" readings.\n");
            }
        } else if (v.survivingCi != null) {
            v.reading = "CLASSIFIER-GAP";
            d.append("  surviving false CI after spurious-leg neutralization: ")
                    .append(v.survivingCi).append('\n');
            d.append("  no all-real unsound triple, but the break survives -- a mark the collider-\n");
            d.append("  presence binner did not classify carried it.  Likely a PKE5 CLASSIFIER GAP,\n");
            d.append("  NOT a refutation.  INVESTIGATE the binner, not the conjecture.\n");
        } else {
            v.reading = "HARD-RESIDUE";
            d.append("  *** HARD RESIDUE: no spurious-leg collider, no all-real unsound triple, and\n");
            d.append("  *** the break does NOT survive spurious-leg neutralization -- contradicts\n");
            d.append("  *** Lemma spur-local directly.  This is the alarming case; verify by hand.\n");
        }
        v.detail = d.toString();
        return v;
    }

    /**
     * Quantifier settler for the certificate.  collider-genuineness is defined
     * over "a MAG realizing" the PAG; the RESIDUE test uses the canonical Zhang
     * MAG.  This asks the "exists a realizing MAG" reading: enumerate the
     * Markov-equivalent representatives of h1 (its class) and return true iff SOME
     * representative carries a collider whose leg is separable in G* (absent in
     * canonPag) -- i.e. some realizing MAG that collider-genuineness WOULD flag.
     *
     * Mirrors representativeSweep's enumeration (3^E over h1's skeleton, LEG-free:
     * ALL equivalents, since the certificate quantifies over realizing MAGs, not
     * just LEGs), but tests separable-leg-collider presence instead of deletion.
     * Returns {found}; fills v.repsEnumerated / v.repsEquivalent.  Fails loudly on
     * zero equivalents (h1's own Zhang MAG must appear), as representativeSweep does.
     */
    private static boolean[] someRepresentativeExposesSeparableLeg(
            Graph h1, Graph canonPag, List<Node> obs, ResidueVerdict v) throws InterruptedException {

        // truth model of h1's class: the model of its Zhang MAG (invariant across
        // the class), used to keep only Markov-equivalent orientations.
        List<int[]> trPairs = new ArrayList<>();
        List<Set<Node>> trZ = new ArrayList<>();
        buildStatements(obs, trPairs, trZ);
        int T = trPairs.size();
        boolean[] h1Model = modelOf(new MsepTest(magOfPag(h1)), obs, trPairs, trZ, T);

        Map<String, Integer> pos = new HashMap<>();
        for (int i = 0; i < obs.size(); i++) pos.put(obs.get(i).getName(), i);

        List<Edge> skel = new ArrayList<>(h1.getEdges());
        int E = skel.size();
        int[] ea = new int[E], eb = new int[E];
        for (int e = 0; e < E; e++) {
            Edge ed = skel.get(e);
            ea[e] = pos.get(ed.getNode1().getName());
            eb[e] = pos.get(ed.getNode2().getName());
        }

        // Unshielded-collider agreement prefilter (necessary for equivalence), keyed
        // to h1's own def-collider verdict per unshielded triple.
        boolean[][] adj = new boolean[obs.size()][obs.size()];
        for (int e = 0; e < E; e++) {
            adj[ea[e]][eb[e]] = true;
            adj[eb[e]][ea[e]] = true;
        }
        List<int[]> triples = new ArrayList<>();
        List<Boolean> h1Coll = new ArrayList<>();
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
                    h1Coll.add(h1.isDefCollider(obs.get(a), obs.get(z), obs.get(c)));
                }
            }
        }

        long total = 1;
        for (int e = 0; e < E; e++) total *= 3;
        int[] o = new int[E];
        long enumerated = 0, equivalents = 0;
        boolean found = false;

        outer:
        for (long code = 0; code < total; code++) {
            enumerated++;
            long c = code;
            for (int e = 0; e < E; e++) {
                o[e] = (int) (c % 3);
                c /= 3;
            }
            for (int t = 0; t < triples.size(); t++) {
                int[] tr = triples.get(t);
                boolean cand = arrowAtZ(tr[0], tr[2], o[tr[0]], ea, eb)
                        && arrowAtZ(tr[1], tr[2], o[tr[1]], ea, eb);
                if (cand != h1Coll.get(t)) continue outer;
            }
            Graph rep = new EdgeListGraph(obs);
            for (int e = 0; e < E; e++) {
                Node a = obs.get(ea[e]), b = obs.get(eb[e]);
                if (o[e] == 0) rep.addDirectedEdge(a, b);
                else if (o[e] == 1) rep.addDirectedEdge(b, a);
                else rep.addBidirectedEdge(a, b);
            }
            if (!isLegalMag(rep)) continue;
            boolean[] m = modelOf(new MsepTest(rep), obs, trPairs, trZ, T);
            if (!modelsEqual(m, h1Model)) continue;    // Markov-equivalent to h1
            equivalents++;
            // Separable-leg collider present in THIS representative?  (shielded or unshielded)
            if (magColliderOnSpuriousLeg(rep, canonPag, h1, true) != null
                    || magColliderOnSpuriousLeg(rep, canonPag, h1, false) != null) {
                found = true;
                break;
            }
        }
        v.repsEnumerated = enumerated;
        v.repsEquivalent = equivalents;
        if (equivalents == 0) {
            throw new IllegalStateException("someRepresentativeExposesSeparableLeg: zero equivalents "
                    + "for a legal H1 -- isLegalMag/model drift; quantifier verdict untrustworthy.");
        }
        return new boolean[]{found};
    }

    // ---- ported verbatim from PKE2 (trueMag == canonMag in PKE5) ----

    private static String allRealLegUnsoundTriple(Graph mag1, Graph truePag, Graph trueMag) {
        for (Node w : mag1.getNodes()) {
            List<Node> adj = mag1.getAdjacentNodes(w);
            int m = adj.size();
            for (int p = 0; p < m; p++) {
                for (int q = p + 1; q < m; q++) {
                    Node a = adj.get(p), b = adj.get(q);
                    if (!truePag.isAdjacentTo(a, w) || !truePag.isAdjacentTo(w, b)) continue;  // both legs real
                    boolean magColl = mag1.getEndpoint(a, w) == Endpoint.ARROW
                            && mag1.getEndpoint(b, w) == Endpoint.ARROW;
                    boolean trueColl = trueMag.isAdjacentTo(a, w) && trueMag.isAdjacentTo(w, b)
                            && trueMag.getEndpoint(a, w) == Endpoint.ARROW
                            && trueMag.getEndpoint(b, w) == Endpoint.ARROW;
                    if (magColl && !trueColl) {
                        return a.getName() + " *-> " + w.getName() + " <-* " + b.getName()
                                + "  [ARROWHEAD-displaced: collider in H1's MAG, non-collider in G*]";
                    }
                    if (!magColl && trueColl) {
                        return a.getName() + " -- " + w.getName() + " -- " + b.getName()
                                + "  [TAIL-displaced: non-collider in H1's MAG, collider in G*]";
                    }
                }
            }
        }
        return null;
    }

    private static String stepBreakResidualBlock(Graph mag1, Graph truePag, Graph trueMag,
                                                 List<Node> obs, MsepTest trueMsep) throws InterruptedException {
        MsepTest t1 = new MsepTest(mag1);
        int n = obs.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Node x = obs.get(i), y = obs.get(j);
                List<Integer> others = new ArrayList<>();
                for (int t = 0; t < n; t++) if (t != i && t != j) others.add(t);
                int mm = others.size();
                for (int zk = 0; zk < (1 << mm); zk++) {
                    Set<Node> Z = new HashSet<>();
                    for (int b = 0; b < mm; b++) if ((zk & (1 << b)) != 0) Z.add(obs.get(others.get(b)));
                    boolean falseInMag1 = t1.checkIndependence(x, y, Z).isIndependent()
                            && !trueMsep.checkIndependence(x, y, Z).isIndependent();
                    if (!falseInMag1) continue;

                    Graph mag2 = new EdgeListGraph(mag1);
                    for (Node c : mag1.getNodes()) {
                        if (Z.contains(c)) continue;                        // skip apices in the conditioning set
                        if (!isSpuriousLegApex(mag1, truePag, c)) continue; // only at spurious-leg collider apices
                        for (Node a : mag1.getAdjacentNodes(c)) {
                            if (mag1.getEndpoint(a, c) == Endpoint.ARROW && !soundArrowInTruth(trueMag, a, c)) {
                                mag2.setEndpoint(a, c, Endpoint.TAIL);      // tail every UNSOUND arrowhead at apex
                            }
                        }
                    }
                    if (new MsepTest(mag2).checkIndependence(x, y, Z).isIndependent()) {
                        return x.getName() + " _||_ " + y.getName() + " | " + Z
                                + "  (survives: blocked by an unsound mark not at a spurious-leg apex)";
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSpuriousLegApex(Graph mag, Graph truePag, Node c) {
        List<Node> adj = mag.getAdjacentNodes(c);
        int m = adj.size();
        for (int p = 0; p < m; p++) {
            for (int q = p + 1; q < m; q++) {
                Node a = adj.get(p), b = adj.get(q);
                if (mag.getEndpoint(a, c) != Endpoint.ARROW) continue;
                if (mag.getEndpoint(b, c) != Endpoint.ARROW) continue;
                if (!truePag.isAdjacentTo(a, c) || !truePag.isAdjacentTo(c, b)) return true;
            }
        }
        return false;
    }

    private static boolean soundArrowInTruth(Graph trueMag, Node a, Node c) {
        return trueMag.isAdjacentTo(a, c) && trueMag.getEndpoint(a, c) == Endpoint.ARROW;
    }

    // ────────────────────────────────────────────────────────────────────────
    // (B) FCIT-ZM SINGLE-ZHANG-MAG RESCUE  (the actual FCIT-ZM commit move)
    // ────────────────────────────────────────────────────────────────────────

    private static final class ZmOutcome {
        boolean rescued;
        boolean everReachedDeletion;      // some spurious edge was present in the Zhang MAG to delete
        boolean everMagIllegalOnDelete;   // some deletion produced an illegal MAG (FcitMag would revert)
        String detail;
    }

    /**
     * FCIT-ZM's move, exactly: form the ONE canonical Zhang MAG of h0, and for each
     * spurious edge f delete f in that MAG, stamp the recorded-separator colliders
     * (orientSepsetCollidersInMag -- FcitMag's adjustForExtraSepsets analogue),
     * require the result to be a legal MAG, and test whether its independence model
     * is a subset of the truth model (I-map of G*).  Rescued iff some f yields a
     * legal MAG that is an I-map.  Uses a SINGLE fixed MAG -- no free choice over
     * the equivalence class -- so this is strictly narrower than the LEG sweep.
     */
    private static ZmOutcome fcitZmRescue(Graph h0, SepsetMap sepsets, boolean[] truthModel,
                                          List<Node> obs, List<int[]> trPairs, List<Set<Node>> trZ,
                                          int T, List<Edge> spurious) throws InterruptedException {
        ZmOutcome out = new ZmOutcome();
        Graph baseMag = magOfPag(h0);                       // the ONE canonical Zhang MAG
        StringBuilder sb = new StringBuilder();
        for (Edge f : spurious) {
            Graph mag = new EdgeListGraph(baseMag);
            Edge fe = mag.getEdge(f.getNode1(), f.getNode2());
            if (fe == null) {
                sb.append("    ").append(f).append(" : absent in the Zhang MAG (nothing to delete)\n");
                continue;
            }
            out.everReachedDeletion = true;
            mag.removeEdge(fe);
            orientSepsetCollidersInMag(mag, sepsets);       // FcitMag's recorded-separator stamping
            if (!isLegalMag(mag)) {
                out.everMagIllegalOnDelete = true;
                sb.append("    ").append(f).append(" : deletion -> ILLEGAL MAG (FcitMag reverts this step)\n");
                continue;
            }
            boolean[] m = modelOf(new MsepTest(mag), obs, trPairs, trZ, T);
            if (subsetModel(m, truthModel)) {
                out.rescued = true;
                sb.append("    ").append(f).append(" : deletion -> LEGAL MAG, I-map of G*  *** RESCUES ***\n");
                sb.append("    post-deletion Zhang MAG (legal, I-map):\n").append(mag).append('\n');
                out.detail = sb.toString();
                return out;
            } else {
                sb.append("    ").append(f).append(" : deletion -> legal MAG but NOT an I-map of G*\n");
            }
        }
        out.detail = sb.toString();
        return out;
    }

    // FcitMag's adjustForExtraSepsets analogue (keyset form), as patched into FcitMag.
    // Ported verbatim from PKE2: stamp x*->c<-*y for each recorded Sep(x,y) that
    // excludes a common neighbour c not already a def-collider.
    private static void orientSepsetCollidersInMag(Graph mag, SepsetMap sepsets) {
        for (Set<Node> pair : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(pair);
            if (arr.size() != 2) continue;
            Node x = arr.get(0), y = arr.get(1);
            Set<Node> s = sepsets.get(x, y);
            if (s == null) continue;
            if (mag.isAdjacentTo(x, y)) continue;
            List<Node> common = mag.getAdjacentNodes(x);
            common.retainAll(mag.getAdjacentNodes(y));
            for (Node c : common) {
                if (s.contains(c)) continue;
                if (mag.isDefCollider(x, c, y)) continue;
                mag.setEndpoint(x, c, Endpoint.ARROW);
                mag.setEndpoint(y, c, Endpoint.ARROW);
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // (A) STEP-BREAK MECHANISM HELPERS  (ported verbatim from PKE2)
    // ────────────────────────────────────────────────────────────────────────

    // Widened genuineness test (R0). Enumerate the unshielded colliders of `cand`
    // -- the R0 firing sites -- and test their legs against G* (truePag). A leg
    // absent in G* is spurious, so the apex was stamped a collider on a triple G*
    // does not contain: a NON-genuine R0 firing. Returns the first, or null.
    private static String r0NonGenuineFiring(Graph cand, Graph truePag) {
        for (Node z : cand.getNodes()) {
            List<Node> adj = cand.getAdjacentNodes(z);
            int m = adj.size();
            for (int i = 0; i < m; i++) {
                for (int j = i + 1; j < m; j++) {
                    Node x = adj.get(i), y = adj.get(j);
                    if (cand.isAdjacentTo(x, y)) continue;        // unshielded only -- the R0 site
                    if (!cand.isDefCollider(x, z, y)) continue;   // R0 actually oriented x*->z<-*y
                    boolean legXZ = truePag.isAdjacentTo(x, z);
                    boolean legZY = truePag.isAdjacentTo(z, y);
                    if (!legXZ || !legZY) {
                        return x.getName() + " *-> " + z.getName() + " <-* " + y.getName()
                                + "   (" + x.getName() + "," + y.getName() + " nonadjacent)   spurious leg: "
                                + (legXZ ? "" : x.getName() + "-" + z.getName() + " ")
                                + (legZY ? "" : z.getName() + "-" + y.getName());
                    }
                }
            }
        }
        return null;
    }

    // Shielded (wantShielded=true) or unshielded (false) collider a*->c<-*b in `mag`
    // with a spurious leg (a-c or c-b absent in truePag).  `pag` classifies the source
    // (committed in the PAG vs realized by MAG completion).  Returns the first, or null.
    private static String magColliderOnSpuriousLeg(Graph mag, Graph truePag, Graph pag, boolean wantShielded) {
        for (Node c : mag.getNodes()) {
            List<Node> adj = mag.getAdjacentNodes(c);
            int m = adj.size();
            for (int i = 0; i < m; i++) {
                for (int j = i + 1; j < m; j++) {
                    Node a = adj.get(i), b = adj.get(j);
                    if (mag.getEndpoint(a, c) != Endpoint.ARROW) continue;
                    if (mag.getEndpoint(b, c) != Endpoint.ARROW) continue;   // collider a*->c<-*b in the MAG
                    if (mag.isAdjacentTo(a, b) != wantShielded) continue;
                    boolean legAC = truePag.isAdjacentTo(a, c);
                    boolean legCB = truePag.isAdjacentTo(c, b);
                    if (!legAC || !legCB) {
                        String src = pag.isDefCollider(a, c, b) ? "committed in the PAG"
                                : "circle(s) in the PAG -> realized by MAG completion";
                        return a.getName() + " *-> " + c.getName() + " <-* " + b.getName()
                                + "  [" + src + "]  spurious leg: "
                                + (legAC ? "" : a.getName() + "-" + c.getName() + " ")
                                + (legCB ? "" : c.getName() + "-" + b.getName());
                    }
                }
            }
        }
        return null;
    }

    private static String formatZm(long mask, Set<Integer> latSet, String mapping, List<Edge> spurious,
                                   Graph canonPag, Graph h0, SweepOutcome sw, ZmOutcome zm) {
        String legVerdict = (sw == null) ? "n/a (sweep disabled)"
                : (sw.rescued ? "RESCUED_BY_LEG_SWEEP" : "SURVIVES_LEG_SWEEP");
        String zmVerdict = zm.rescued ? "RESCUED_BY_ZM" : "NOT_RESCUED_BY_ZM";
        StringBuilder sb = new StringBuilder();
        sb.append("==== CANONICAL STALL: FCIT-ZM rescue vs LEG sweep ====\n");
        sb.append("  LEG-sweep verdict : ").append(legVerdict).append('\n');
        sb.append("  FCIT-ZM verdict   : ").append(zmVerdict);
        if (!zm.rescued && sw != null && sw.rescued) {
            sb.append("   <== FCIT-ZM STALL: LEG sweep rescues but the single Zhang MAG does not")
                    .append(" (needs free-MAG choice / conj:mag-meek, or the saturating step)");
        }
        sb.append('\n');
        sb.append("  exemplar dag mask : ").append(mask).append('\n');
        sb.append("  latent set        : ").append(latSet).append('\n');
        sb.append("  relabeling        : ").append(mapping).append('\n');
        sb.append("  spurious edges    : ").append(spurious).append('\n');
        sb.append("  true PAG G*:\n").append(canonPag).append('\n');
        sb.append("  H0 (legal I-map stall):\n").append(h0).append('\n');
        sb.append("  --- FCIT-ZM per-edge trace (single canonical Zhang MAG of H0) ---\n");
        sb.append(zm.detail == null ? "    (no spurious edge present in the Zhang MAG)\n" : zm.detail);
        if (sw != null && sw.rescueDetail != null) {
            sb.append("  --- LEG-sweep rescue (for comparison) ---\n").append(sw.rescueDetail);
        }
        sb.append("==== end entry ====\n");
        return sb.toString();
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

    /** The offending pair {x,y} of the first false CI (m-sep in cand, m-conn in truth),
     *  as node objects, for sepset-provenance instrumentation.  Null if none. */
    private static Node[] firstFalseCiPair(boolean[] cand, boolean[] truth, List<Node> obs,
                                           List<int[]> trPairs) {
        for (int t = 0; t < cand.length; t++) {
            if (cand[t] && !truth[t]) {
                return new Node[]{obs.get(trPairs.get(t)[0]), obs.get(trPairs.get(t)[1])};
            }
        }
        return null;
    }

    /**
     * FAULT-LOCALIZING DUMP for a RESIDUE break.  The break pair (x,y) is m-separated
     * in H1 by some conditioning set the harness's reorientation encoded.  This prints
     * the recorded Sep(x,y) actually carried into the reorientation, whether the oracle
     * accepts it, and (by re-running RB on H0) what fcitSpanningSepset originally
     * returned for (x,y) -- distinguishing:
     *   (i)  BULK-FILL RECORDED AN ORACLE-FALSE SET  -- Sep(x,y) is recorded but the
     *        oracle rejects it: the bulk sepset fill (unguarded, unlike the removed-edge
     *        path) trusted a non-separator.  Fix: guard the fill with an oracle check.
     *   (ii) RB RETURNED NON-MINIMAL  -- RB returns a valid-but-non-minimal set that
     *        includes a true collider node, so stampExtraSepsetColliders reads that node
     *        as a non-collider and suppresses the true collider.  Fix: minimize, or fix RB.
     *   (iii) NEITHER -- recorded set is minimal and oracle-valid; the wrong non-collider
     *        came from elsewhere (FciOrient closure).  Then it is not a sepset artifact.
     */
    private static String sepsetProvenanceDump(Node x, Node y, SepsetMap sepsetsH1, Graph h0,
                                               IndependenceTest oracle, SepsetMap sepsetsH1Input,
                                               Graph h0PreReorient) throws InterruptedException {
        StringBuilder sb = new StringBuilder();
        sb.append("  --- SEPSET PROVENANCE for break pair ")
                .append(x.getName()).append(",").append(y.getName()).append(" ---\n");
        Set<Node> input = sepsetsH1Input.get(x, y);
        Set<Node> post = sepsetsH1.get(x, y);
        sb.append("    Sep(").append(x.getName()).append(",").append(y.getName())
                .append(") FED INTO reorient: ").append(input).append('\n');
        sb.append("    Sep(").append(x.getName()).append(",").append(y.getName())
                .append(") AFTER reorient    : ").append(post).append('\n');
        if (input != null) {
            boolean ok = oracle.checkIndependence(x, y, input).isIndependent();
            sb.append("    oracle accepts the fed-in set? ").append(ok)
                    .append(ok ? "" : "   *** ORACLE-FALSE fed in ***").append('\n');
        }
        // MASKING TEST.  Prediction (rem:r0-dirty composition): on the PRE-reorient dirty
        // H0 -- where the spurious circle edge forces the true collider's descendant into
        // every graphical candidate -- RB can certify NO oracle-true separator (returns
        // null, since fcitSpanningSepset oracle-confirms internally).  On the POST-reorient
        // H0 -- where the completion happened to realize the collider -- the pair separates
        // without the descendant, so RB certifies the clean set.  PRE=null with POST=[..]
        // CONFIRMS the masking mechanism (the break is a genuine product of the modeled
        // process, not a dropped entry).  PRE returning a confirmed set REFUTES it (a real
        // recording gap exists after all).
        Set<Node> pre = fcitSpanningSepset(h0PreReorient, oracle, x, y);
        sb.append("    RB on PRE-reorient H0 (dirty skeleton) : ").append(pre);
        if (pre != null) {
            sb.append("   oracle-valid? ").append(oracle.checkIndependence(x, y, pre).isIndependent())
                    .append("   *** REFUTES masking prediction: certifiable set existed pre-reorient ***");
        } else {
            sb.append("   (no oracle-true certifiable separator -- masking CONFIRMED for this pair)");
        }
        sb.append('\n');
        Set<Node> fresh = fcitSpanningSepset(h0, oracle, x, y);
        sb.append("    RB on POST-reorient H0 (collider fixed): ").append(fresh);
        if (fresh != null) {
            sb.append("   oracle-valid? ").append(oracle.checkIndependence(x, y, fresh).isIndependent());
        }
        sb.append('\n');
        sb.append("    => PRE=null + POST=set is the masking geometry: the dirty graph demands the\n");
        sb.append("       descendant in every candidate, the oracle forbids it, so the table is\n");
        sb.append("       necessarily silent on exactly the pair whose collider needed stamping.\n");
        return sb.toString();
    }

    /**
     * Decisive cross-check for a RESIDUE break: ask the RUN'S OWN live oracle whether x,y
     * are independent given all other observed nodes, and print canonMag's structural facts
     * (def-colliders among common neighbors, and their descendants) that should decide it.
     * A def-collider with a descendant in the conditioning set MUST force DEP; if the live
     * oracle says INDEP anyway, the oracle's graph is not the printed canonMag.
     */
    private static String oracleVsStructureDump(Node x, Node y, List<Node> obs,
                                                IndependenceTest oracle, Graph canonMag) throws InterruptedException {
        StringBuilder sb = new StringBuilder();
        sb.append("  --- LIVE-ORACLE vs canonMag STRUCTURE for ")
                .append(x.getName()).append(",").append(y.getName()).append(" ---\n");
        List<Node> common = new ArrayList<>(canonMag.getAdjacentNodes(x));
        common.retainAll(canonMag.getAdjacentNodes(y));
        sb.append("    common neighbors in canonMag: ").append(nameList(common)).append('\n');
        for (Node c : common) {
            boolean coll = canonMag.isDefCollider(x, c, y);
            sb.append("      ").append(c.getName()).append(": isDefCollider=").append(coll);
            if (coll) {
                List<Node> desc = new ArrayList<>();
                for (Node d : obs) if (d != c && canonMag.paths().isAncestorOf(c, d)) desc.add(d);
                sb.append("  descendants=").append(nameList(desc));
            }
            sb.append('\n');
        }
        Set<Node> others = new HashSet<>();
        for (Node o : obs) if (o != x && o != y) others.add(o);
        boolean indepAll = oracle.checkIndependence(x, y, others).isIndependent();
        sb.append("    LIVE ORACLE ").append(x.getName()).append(" _||_ ").append(y.getName())
                .append(" | ").append(nameList(new ArrayList<>(others)))
                .append(" -> ").append(indepAll ? "INDEP" : "DEP").append('\n');
        // DECISIVE: for each def-collider c among common neighbors, ask the oracle the set
        // that conditions on a DESCENDANT of c but NOT c itself -- the exact descendant-only
        // activation {...,descendant} that the recorded separator used.  This is the query
        // MsepProbe found DEPENDENT on the hand-built MAG; if the live oracle says INDEP here,
        // MsepTest is not activating the collider via its descendant on THIS graph object,
        // isolating a graph-construction difference from the hand-built MAG.
        for (Node c : common) {
            if (!canonMag.isDefCollider(x, c, y)) continue;
            for (Node d : obs) {
                if (d == c || d == x || d == y) continue;
                if (!canonMag.paths().isAncestorOf(c, d)) continue;   // d is a descendant of collider c
                Set<Node> descSet = new HashSet<>();
                descSet.add(d);
                // include the "fork blockers" (non-collider common neighbors) so only the
                // collider path is at issue -- mirrors the recorded {V1,V5} shape.
                for (Node o : common) if (!canonMag.isDefCollider(x, o, y)) descSet.add(o);
                boolean indep = oracle.checkIndependence(x, y, descSet).isIndependent();
                sb.append("    DECISIVE oracle ").append(x.getName()).append(" _||_ ").append(y.getName())
                        .append(" | ").append(nameList(new ArrayList<>(descSet)))
                        .append("  (conditions on descendant ").append(d.getName())
                        .append(" of collider ").append(c.getName()).append(", not ").append(c.getName())
                        .append(") -> ").append(indep ? "INDEP" : "DEP")
                        .append(indep ? "   *** WRONG: descendant must activate collider -> should be DEP;"
                                        + " MsepTest not seeing " + d.getName() + " as descendant of " + c.getName()
                                        + " on this graph object ***"
                                : "   (correct)")
                        .append('\n');
            }
        }
        sb.append("    (a def-collider with a descendant in that set MUST force DEP; if the oracle\n");
        sb.append("     says INDEP anyway, its graph != the printed canonMag.)\n");
        return sb.toString();
    }

    private static String nameList(List<Node> ns) {
        List<String> s = new ArrayList<>();
        for (Node n : ns) s.add(n.getName());
        return s.toString();
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
        // (A) step-break mechanism histogram (buckets partition the step-breaks).
        long mechR0, mechR4Shielded, mechCompletion, mechResidue;
        // (A') RESIDUE drill-down readings (partition the RESIDUE bucket).
        long residueDisplaced, residueClassifierGap, residueHard, residueOther;
        // Quantifier settler over DISPLACED cases: did some equivalent representative
        // expose a separable-leg collider (certificate survives all-reps reading)?
        long displacedSomeRepExposes, displacedNoRepExposes;
        // (B) FCIT-ZM single-Zhang-MAG rescue of canonical stalls.
        long zmRescued, zmNotRescued, zmMagIllegalOnDelete, zmNoLegalDeletion;

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
            mechR0 += o.mechR0;
            mechR4Shielded += o.mechR4Shielded;
            mechCompletion += o.mechCompletion;
            mechResidue += o.mechResidue;
            residueDisplaced += o.residueDisplaced;
            residueClassifierGap += o.residueClassifierGap;
            residueHard += o.residueHard;
            residueOther += o.residueOther;
            displacedSomeRepExposes += o.displacedSomeRepExposes;
            displacedNoRepExposes += o.displacedNoRepExposes;
            zmRescued += o.zmRescued;
            zmNotRescued += o.zmNotRescued;
            zmMagIllegalOnDelete += o.zmMagIllegalOnDelete;
            zmNoLegalDeletion += o.zmNoLegalDeletion;
        }
    }
}
