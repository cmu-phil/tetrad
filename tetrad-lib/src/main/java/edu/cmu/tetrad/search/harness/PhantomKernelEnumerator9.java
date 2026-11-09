/// ////////////////////////////////////////////////////////////////////////////
// PhantomKernelEnumerator9.java  (parallel)                                   //
//                                                                             //
// EXHAUSTIVE companion to the RB/FCIT reachability appendix. Enumerates EVERY //
// DAG over a fixed topological order x every latent placement x every         //
// spurious-edge subset, running the identical reorient / legality /           //
// zhangMagFromPag machinery. A null result is a PROOF up to (N, latent,       //
// maxSpurious), not a sampling miss.                                          //
//                                                                             //
// CHANGES vs PKE3 (the version behind the old appendix numbers):              //
//                                                                             //
//  FIX 1 (sepset search). rbConfirmedSepset used the RAW RecursiveBlocking    //
//    blocking set, oracle-confirmed. The candidate family therefore never     //
//    ranged over all subsets of common(x,y), so a valid separator CONTAINING  //
//    a common neighbour c could be absent from the table, and                 //
//    adjustForExtraSepsets stamped the fake collider x*->c<-*y. PKE9 now      //
//    CALLS FcitSepsets.spanningSepset -- the identical routine Fcit uses --   //
//    rather than reimplementing it, so harness == algorithm by construction.  //
//    (The first PKE9 draft transcribed the search and differed from FCIT in   //
//    two ways: it ran RB once instead of enumerating not-followed subsets of  //
//    the ambiguous blocking-set nodes, and it passed 3 where FCIT passes 1 in //
//    RecursiveBlocking's post-radius argument. Both differences are gone.)    //
//                                                                             //
//  FIX 2 (sepset coverage). PKE3 recorded separators only for the spurious    //
//    pairs, starving adjustForExtraSepsets of exactly the entries that        //
//    suppress fake colliders — manufacturing step-breaks that real            //
//    FCIT-from-complete avoids. PKE9 records a confirmed separator for EVERY  //
//    true non-adjacency (computed on the dirty H0 skeleton), and audits the   //
//    coverage in the summary so "not starved" is checkable, not assumed.      //
//                                                                             //
//  REMOVED (superseded, kept out for clarity):                                //
//    * WARM/seeded reorient toggle. Warm is rejected: it patched the visible  //
//      case by inheriting marks and manufactured its own phantoms; the H0     //
//      gate was cold regardless, making the mode incoherent. All              //
//      reorientations here are COLD (from-scratch), matching shipped FCIT.    //
//    * ROBUST_R0 and GUARD_RECOMMIT experiments (their conclusions fed the    //
//      FcitSl battery/guard design; they are not part of this audit).         //
//    * The responsibility/neutralization drill-down and the false-CI blocker  //
//      diagnostics — bug-hunt scaffolding for the starved regime. The         //
//      step-break mechanism binning (R0 / R4 / COMPLETION / RESIDUE) is       //
//      retained as the first-line classifier should any step-break appear.    //
//                                                                             //
//  UNIFIED: MAG->PAG is MagToPag.convert everywhere (the map the legality     //
//    check's round-trip prong uses), for both the FcitMag re-commit probe     //
//    and the MAG-sweep escape. PKE3 mixed MagToPag with an FCI-pipeline       //
//    reconstruction; the two probes now measure against the same projection.  //
//                                                                             //
//  ASSUMES: the FciOrient determinism fix (LinkedHashSet) is in the Tetrad    //
//    build, so deadlock counts are stable run-to-run. Serial vs parallel      //
//    totals remain a free correctness check                                   //
//    (-Djava.util.concurrent.ForkJoinPool.common.parallelism=1 vs N).        //
//                                                                             //
//  SCOPE NOTE: at N=6 / 1 latent, each observed pair has only 3 candidate     //
//    conditioners, so the spanning search is exhaustive over conditioning     //
//    sets at this size by construction. Rerunning at N>=7 requires no cap     //
//    change here (the search is bounded by common(x,y) and the RB set), but   //
//    the Bryan check's BRYAN_MAX_SKELETON_EDGES guard will start to bite.     //
//                                                                             //
// Expected result under the corrected regime (the claim the appendix now      //
// makes): zero Conjecture-1 counterexamples, zero I-map step-breaks, zero     //
// non-I-map H0, all logs empty except r0_nongenuine.log, whose entries are    //
// detections under the R0-widened genuineness definition that the algorithm   //
// never commits (alsoStepBreak = 0 for all of them).                          //
//                                                                             //
// args: [0]=N (default 6) [1]=numLatent (default 1) [2]=maxSpurious (def. 2)  //
//       [3]=witness log  [4]=meek log  [5]=imap log  [6]=stepbreak log        //
//       [7]=magrecheck log  [8]=r0ng log  [9]=residue log                     //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.SublistGenerator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

/**
 * Parallel exhaustive enumeration over small latent DAGs, run under the corrected
 * (spanning, fully-covered) sepset regime, that either certifies the reachability
 * appendix's claims at a given size or prints witnesses.
 *
 * @author josephramsey (harness scaffolding by Claude)
 */
public final class PhantomKernelEnumerator9 {

    /**
     * Default constructor.
     */
    public PhantomKernelEnumerator9() {}

    private static int N            = 6;
    private static int NUM_LATENT   = 1;
    private static int MAX_SPURIOUS = 2;

    private static final int     MAX_LEN         = -1;
    private static final int     DEPTH           = -1;
    private static final int     RECURSIVE_DEPTH = -1;
    private static final int     RB_RADIUS       = -1;   // Fcit's default rbRadius
    private static final long    TIMEOUT         = -1L;
    private static final boolean EXCLUDE_SELECTION_BIAS = true;
    private static final boolean PROBE_STEP_BREAKS      = true;   // I-map H0 -> legal non-I-map H1 probe
    private static final boolean PROBE_R0_GENUINE       = true;   // widen genuineness test from R4 to R0

    // Shared read-only config, set in main before the parallel stream.
    private static int OBS, P;
    private static int[][] PAIR;
    private static long TOTAL_DAGS;
    private static final AtomicLong PROGRESS = new AtomicLong();
    private static final int WITNESS_CAP = 5000; // per merged Result, to bound memory

    // Guard on the 3^edges MAG enumeration in the Bryan full-form check.
    private static final int BRYAN_MAX_SKELETON_EDGES = 12;

    /**
     * Main method.
     * @param args args.
     */
    public static void main(String[] args) {
        if (args.length > 0) N            = Integer.parseInt(args[0]);
        if (args.length > 1) NUM_LATENT   = Integer.parseInt(args[1]);
        if (args.length > 2) MAX_SPURIOUS = Integer.parseInt(args[2]);
        String dumpPath       = (args.length > 3) ? args[3] : "phantom_kernel_witnesses.log";
        String meekPath       = (args.length > 4) ? args[4] : "meek_counterexamples.log";
        String imapPath       = (args.length > 5) ? args[5] : "imap_violations.log";
        String stepBreakPath  = (args.length > 6) ? args[6] : "pag_step_imap_break.log";
        String magRecheckPath = (args.length > 7) ? args[7] : "mag_commit_recheck.log";
        String r0NgPath       = (args.length > 8) ? args[8] : "r0_nongenuine.log";
        String residuePath    = (args.length > 9) ? args[9] : "step_break_residue.log";

        OBS = N - NUM_LATENT;
        P   = N * (N - 1) / 2;
        TOTAL_DAGS = 1L << P;
        PAIR = new int[P][2];
        for (int idx = 0, i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++, idx++) { PAIR[idx][0] = i; PAIR[idx][1] = j; }
        }

        int threads = Runtime.getRuntime().availableProcessors();
        System.err.printf("EXHAUSTIVE CONFIG: N=%d latent=%d observed=%d maxSpurious=%d "
                        + "| dags=2^%d=%d latentPlacements=C(%d,%d) | threads~%d%n",
                N, NUM_LATENT, OBS, MAX_SPURIOUS, P, TOTAL_DAGS, N, NUM_LATENT, threads);
        System.err.println("Reorient mode: COLD (from-scratch), matching shipped FCIT. Warm removed.");
        System.err.println("Sepset regime: spanning include-common-first, oracle-confirmed, recorded for");
        System.err.println("EVERY true non-adjacency on the dirty H0 skeleton (PKE9 fixes 1 and 2).");

        // Parallel reduction: each worker accumulates into its own Result, merged at the end.
        Result total = LongStream.range(0, TOTAL_DAGS)
                .parallel()
                .collect(Result::new, PhantomKernelEnumerator9::accumulate, Result::merge);

        // Witnesses / anomalies (rare; collected, written serially at the end).
        PrintWriter dump = openDump(dumpPath);
        try {
            for (String w : total.witnesses) dump.println(w);
            if (total.suppressed > 0) {
                dump.println("==== (" + total.suppressed + " further witnesses suppressed; raise WITNESS_CAP) ====");
            }
        } finally {
            dump.flush();
            dump.close();
        }

        // Generalized-Meek: Bryan-form survivors only.
        PrintWriter meekDump = openDump(meekPath);
        try {
            meekDump.println("# Generalized-Meek (Bryan full form) counterexamples: legal PAGs, I-maps of the");
            meekDump.println("# true PAG, from which the true PAG is NOT reachable by ANY sequence of legal");
            meekDump.println("# single-edge removals and reorientations over I-map PAGs.");
            meekDump.println("# Exhaustive over N=" + N + " latent=" + NUM_LATENT + " maxSpurious=" + MAX_SPURIOUS
                    + " (COLD reorient, spanning sepsets).");
            meekDump.println("# single-edge-form deadlocks: " + total.meekCounterexamples
                    + " | Bryan-form survivors: " + total.bryanCounterexamples
                    + " | unchecked: " + total.bryanUnchecked + "\n");
            for (String w : total.meekWitnesses) meekDump.println(w);
            if (total.meekSuppressed > 0) {
                meekDump.println("==== (" + total.meekSuppressed + " further counterexamples suppressed; raise WITNESS_CAP) ====");
            }
        } finally {
            meekDump.flush();
            meekDump.close();
        }

        // Non-I-map H0 (should be EMPTY under the corrected regime).
        PrintWriter imapDump = openDump(imapPath);
        try {
            imapDump.println("# Non-I-map H0 examples: legal PAGs whose cold reorientation of G*+spurious");
            imapDump.println("# introduced an independence not entailed by G*. Under the corrected sepset");
            imapDump.println("# regime this log is expected EMPTY; any entry is a defect to investigate.");
            imapDump.println("# Exhaustive over N=" + N + " latent=" + NUM_LATENT + " maxSpurious=" + MAX_SPURIOUS
                    + ".  total non-I-map H0: " + total.bryanNotImap + "\n");
            for (String w : total.imapWitnesses) imapDump.println(w);
            if (total.imapSuppressed > 0) {
                imapDump.println("==== (" + total.imapSuppressed + " further examples suppressed; raise WITNESS_CAP) ====");
            }
        } finally {
            imapDump.flush();
            imapDump.close();
        }

        // PAG->PAG step-breaks (should be EMPTY under the corrected regime).
        PrintWriter stepDump = openDump(stepBreakPath);
        try {
            stepDump.println("# PAG->PAG step-breaks: H0 is an I-map of G*, but a LEGAL single-edge");
            stepDump.println("# remove+reorient yields H1 that is NOT an I-map of G*. Under the corrected");
            stepDump.println("# sepset regime this log is expected EMPTY; any entry is a defect.");
            stepDump.println("# Each carries a witness CI (holds in H1, fails in G*).");
            stepDump.println("# Exhaustive over N=" + N + " latent=" + NUM_LATENT + " maxSpurious=" + MAX_SPURIOUS
                    + ".  total step-breaks: " + total.pagStepBreaks + "\n");
            for (String w : total.stepBreakWitnesses) stepDump.println(w);
            if (total.stepBreakSuppressed > 0) {
                stepDump.println("==== (" + total.stepBreakSuppressed + " further suppressed; raise WITNESS_CAP) ====");
            }
        } finally {
            stepDump.flush();
            stepDump.close();
        }

        // FcitMag re-commit of any step-break (only populated if step-breaks exist).
        PrintWriter magDump = openDump(magRecheckPath);
        try {
            magDump.println("# For each PAG->PAG step-break, the SAME I-map H0 and SAME removal re-committed");
            magDump.println("# through FcitMag's PAG->MAG->PAG path (MagToPag projection). Outcome per case:");
            magDump.println("# ALSO BREAKS / FIXED (stays an I-map) / REVERTED (MAG illegal, step refused).");
            magDump.println("# Tallies -- alsoBreaks: " + total.magCommitAlsoBreaks
                    + " | fixed: " + total.magCommitFixed
                    + " | reverted: " + total.magCommitReverted + "\n");
            for (String w : total.magRecheckWitnesses) magDump.println(w);
            if (total.magRecheckSuppressed > 0) {
                magDump.println("==== (" + total.magRecheckSuppressed + " further suppressed; raise WITNESS_CAP) ====");
            }
        } finally {
            magDump.flush();
            magDump.close();
        }

        // Widened genuineness (R0): detections expected; commits (alsoStepBreak) expected 0.
        PrintWriter r0Dump = openDump(r0NgPath);
        try {
            r0Dump.println("# Genuineness widened from R4 to R0. Each entry is a LEGAL from-scratch");
            r0Dump.println("# reorientation with an unshielded collider on a spurious leg. These are");
            r0Dump.println("# DETECTIONS under the widened definition; the decisive column is whether any");
            r0Dump.println("# is also an I-map step-break (a committed defect). Expected: alsoStepBreak = 0.");
            r0Dump.println("# legal R0-non-genuine: " + total.r0NonGenuineLegal
                    + "  | also I-map step-breaks: " + total.r0NgAndStepBreak
                    + "  | step-breaks NOT explained by R0: " + total.stepBreakNotExplainedByR0
                    + "  (of " + total.pagStepBreaks + " step-breaks)\n");
            for (String w : total.r0NgWitnesses) r0Dump.println(w);
            if (total.r0NgSuppressed > 0) {
                r0Dump.println("==== (" + total.r0NgSuppressed + " further suppressed; raise WITNESS_CAP) ====");
            }
        } finally {
            r0Dump.flush();
            r0Dump.close();
        }

        // Step-break mechanism binning; RESIDUE dumped in full (expected: nothing to bin).
        PrintWriter resDump = openDump(residuePath);
        try {
            resDump.println("# Step-breaks (if any) binned by the mechanism of the false collider:");
            resDump.println("#   R0         : PAG unshielded def-collider on a spurious leg");
            resDump.println("#   R4         : shielded spurious-leg collider in the MAG (discriminating-path type)");
            resDump.println("#   COMPLETION : unshielded spurious-leg collider realized only by MAG completion");
            resDump.println("#   RESIDUE    : NO collider on any spurious leg -- all-real-edge break");
            resDump.println("# tally -- R0: " + total.sbR0 + " | R4(shielded): " + total.sbR4Shielded
                    + " | COMPLETION: " + total.sbCompletion + " | RESIDUE: " + total.sbResidue
                    + "   (of " + total.pagStepBreaks + " step-breaks)\n");
            for (String w : total.residueWitnesses) resDump.println(w);
            if (total.residueSuppressed > 0) {
                resDump.println("==== (" + total.residueSuppressed + " further suppressed; raise WITNESS_CAP) ====");
            }
        } finally {
            resDump.flush();
            resDump.close();
        }

        printSummary(total, dumpPath, meekPath);
        System.out.println("Non-I-map H0 examples (" + total.bryanNotImap + ") written to: " + imapPath);
        System.out.println("PAG->PAG step-breaks (" + total.pagStepBreaks + ") written to: " + stepBreakPath);
        System.out.println("FcitMag re-commit (alsoBreaks=" + total.magCommitAlsoBreaks
                + " fixed=" + total.magCommitFixed + " reverted=" + total.magCommitReverted
                + ") written to: " + magRecheckPath);
        System.out.println("R0-non-genuine legal (" + total.r0NonGenuineLegal
                + ", alsoStepBreak=" + total.r0NgAndStepBreak
                + ", stepBreaksNotR0=" + total.stepBreakNotExplainedByR0
                + ") written to: " + r0NgPath);
        System.out.println("Step-break mechanism -- R0=" + total.sbR0 + " R4=" + total.sbR4Shielded
                + " COMPLETION=" + total.sbCompletion + " RESIDUE=" + total.sbResidue
                + " (residue dumped to: " + residuePath + ")");
    }

    // ── Per-DAG-mask work; thread-confined writes into r. Catches per-model. ───
    private static void accumulate(Result r, long mask) {
        r.dagsScanned++;

        SublistGenerator latGen = new SublistGenerator(N, NUM_LATENT);
        int[] latChoice;
        while ((latChoice = latGen.next()) != null) {
            if (latChoice.length != NUM_LATENT) continue;
            try {
                List<Node> nodes = new ArrayList<>();
                for (int i = 0; i < N; i++) nodes.add(new GraphNode("X" + (i + 1)));
                Graph dag = new EdgeListGraph(nodes);
                for (int b = 0; b < P; b++) {
                    if ((mask & (1L << b)) != 0) {
                        dag.addDirectedEdge(nodes.get(PAIR[b][0]), nodes.get(PAIR[b][1]));
                    }
                }
                Set<Integer> latSet = new HashSet<>();
                for (int li : latChoice) { latSet.add(li); nodes.get(li).setNodeType(NodeType.LATENT); }

                r.modelsScanned++;

                Knowledge knowledge = new Knowledge();
                Graph truePag = GraphTransforms.dagToPag(dag, knowledge, EXCLUDE_SELECTION_BIAS, RECURSIVE_DEPTH);
                IndependenceTest oracle = new MsepTest(dag);

                List<Node> obs = truePag.getNodes();
                Set<Triple> initialColliders = noteInitialColliders(obs, truePag);
                Graph trueMag = PROBE_STEP_BREAKS ? magOfPag(truePag) : null;        // PAG->PAG probe (true MAG)
                MsepTest trueMsep = PROBE_STEP_BREAKS ? new MsepTest(trueMag) : null;
                List<int[]> nonAdj = nonAdjacentPairs(truePag, obs);
                if (nonAdj.isEmpty()) continue;

                // Candidate spurious edges are true non-adjacencies. Separators are NOT
                // precomputed from the true DAG: for each H0 below, EVERY true non-adjacency
                // gets a separator computed operationally on the dirty H0 skeleton by the
                // spanning include-common-first search, oracle-confirmed (PKE9 fixes 1+2).
                List<Node[]> sepPairs = new ArrayList<>();
                for (int[] p : nonAdj) {
                    Node a = obs.get(p[0]), b2 = obs.get(p[1]);
                    sepPairs.add(new Node[]{a, b2});
                }

                int cap = Math.min(MAX_SPURIOUS, sepPairs.size());
                for (int k = 1; k <= cap; k++) {
                    SublistGenerator spGen = new SublistGenerator(sepPairs.size(), k);
                    int[] spChoice;
                    while ((spChoice = spGen.next()) != null) {
                        if (spChoice.length != k) continue;

                        Graph h0 = new EdgeListGraph(truePag);
                        List<Edge> spurious = new ArrayList<>();
                        Set<Integer> spIdx = new HashSet<>();
                        for (int si : spChoice) {
                            Node a = sepPairs.get(si)[0];
                            Node b2 = sepPairs.get(si)[1];
                            Edge edge = new Edge(a, b2, Endpoint.CIRCLE, Endpoint.CIRCLE);
                            h0.addEdge(edge);
                            spurious.add(edge);
                            spIdx.add(si);
                        }

                        // FIX 2: record a confirmed separator for EVERY true non-adjacency on
                        // this dirty skeleton, not only the spurious pairs. Spurious pairs must
                        // succeed (a spurious edge with no confirmable separator cannot be
                        // deleted, and the subset is out of scope); other pairs are recorded
                        // when found and counted either way for the coverage audit.
                        SepsetMap sepsets = new SepsetMap();
                        boolean rbOk = true;
                        for (int si = 0; si < sepPairs.size(); si++) {
                            Node a = sepPairs.get(si)[0];
                            Node b2 = sepPairs.get(si)[1];
                            boolean isSpurious = spIdx.contains(si);
                            if (isSpurious) {
                                // The pair is adjacent in h0 (we just added the edge);
                                // the search ignores the direct edge (ignoreDirect=true).
                                Set<Node> sep = spanningConfirmedSepset(h0, oracle, a, b2);
                                if (sep == null) { rbOk = false; break; }
                                sepsets.set(a, b2, sep);
                            } else {
                                r.sepPairsQueried++;
                                Set<Node> sep = spanningConfirmedSepset(h0, oracle, a, b2);
                                if (sep != null) { sepsets.set(a, b2, sep); r.sepPairsConfirmed++; }
                                else r.sepPairsUnconfirmed++;
                            }
                        }
                        if (!rbOk) { r.spuriousSepMissing++; continue; }

                        int abst0 = reorient(h0, oracle, sepsets, knowledge, initialColliders);
                        if (!PagLegalityCheck.isLegalPag(h0, new HashSet<>()).isLegalPag()) continue;
                        Set<DiscriminatingPath> ddp0 = FciOrient.listDiscriminatingPaths(h0, MAX_LEN, true);
                        if (firstPhantom(ddp0, truePag) != null) continue;
                        r.gated++;
                        if (abst0 > 0) r.gatedWithAbstention++;

                        // NOSTALL probe: count legal single-edge escapes from this gated H0.
                        int legalEscapes = 0;
                        int dlGen = 0, dlNg = 0;
                        int[] dlGenProng = new int[5], dlNgProng = new int[5];
                        StringBuilder dlLog = new StringBuilder();

                        // Is H0 itself an I-map of G*? Only then is "I-map H0 -> non-I-map H1"
                        // the real PAG->PAG soundness defect.
                        boolean h0imap = PROBE_STEP_BREAKS && (imapWitnessAgainst(h0, obs, trueMsep) == null);

                        for (Edge e : spurious) {
                            Graph h1 = new EdgeListGraph(h0);
                            Edge present = h1.getEdge(e.getNode1(), e.getNode2());
                            if (present == null) continue;
                            h1.removeEdge(present);

                            int abst1 = reorient(h1, oracle, sepsets, knowledge, initialColliders);
                            r.h1States++;
                            r.totalAbstentions += abst1;
                            boolean abstained = abst1 > 0;
                            if (abstained) r.h1WithAbstention++;

                            Set<DiscriminatingPath> ddp1 = FciOrient.listDiscriminatingPaths(h1, MAX_LEN, true);
                            List<DiscriminatingPath> phantoms = allPhantoms(ddp1, truePag);

                            PagLegalityCheck.LegalPagRet ret = PagLegalityCheck.isLegalPag(h1, new HashSet<>());
                            boolean legal = ret.isLegalPag();
                            if (legal) legalEscapes++;

                            // Widened genuineness (R0): a LEGAL reorientation carrying an
                            // unshielded collider with a spurious leg. Detection, not commit;
                            // the decisive column is alsoStepBreak.
                            if (PROBE_R0_GENUINE && legal) {
                                String r0ng = r0NonGenuineFiring(h1, truePag);
                                if (r0ng != null) {
                                    r.r0NonGenuineLegal++;
                                    boolean alsoStepBreak = (trueMsep != null) && h0imap
                                            && (imapWitnessAgainst(h1, obs, trueMsep) != null);
                                    if (alsoStepBreak) r.r0NgAndStepBreak++;
                                    r.addR0NgWitness(formatR0NonGenuine(mask, latSet, spurious, e,
                                            dag, truePag, h0, h1, r0ng, h0imap, alsoStepBreak));
                                }
                            }

                            // PAG->PAG soundness probe: expected EMPTY under the corrected regime.
                            if (h0imap && legal) {
                                String h1break = imapWitnessAgainst(h1, obs, trueMsep);
                                if (h1break != null) {
                                    r.pagStepBreaks++;

                                    String mech = "";
                                    if (PROBE_R0_GENUINE) {
                                        Graph mag1 = magOfPag(h1);
                                        String r0 = r0NonGenuineFiring(h1, truePag);
                                        if (r0 != null) {
                                            r.sbR0++;
                                            mech = "  mechanism R0 (PAG unshielded): " + r0;
                                        } else {
                                            r.stepBreakNotExplainedByR0++;
                                            String sh = magColliderOnSpuriousLeg(mag1, truePag, h1, true);
                                            String un = (sh == null) ? magColliderOnSpuriousLeg(mag1, truePag, h1, false) : null;
                                            if (sh != null) {
                                                r.sbR4Shielded++;
                                                mech = "  mechanism R4 (MAG shielded): " + sh;
                                            } else if (un != null) {
                                                r.sbCompletion++;
                                                mech = "  mechanism COMPLETION (MAG-only unshielded): " + un;
                                            } else {
                                                r.sbResidue++;
                                                mech = "  mechanism RESIDUE: no collider on any spurious leg";
                                                r.addResidueWitness(formatResidue(mask, latSet, spurious, e,
                                                        dag, truePag, h0, h1, mag1, h1break));
                                            }
                                        }
                                    }

                                    r.addStepBreakWitness(formatStepBreak(mask, latSet, spurious, e,
                                            dag, truePag, h0, h1, h1break) + mech + "\n");

                                    // Re-commit the SAME removal through FcitMag's PAG->MAG->PAG path.
                                    Graph h1mag = magCommit(h0, e.getNode1(), e.getNode2(), sepsets);
                                    String magBreak = null, outcome;
                                    if (h1mag == null) {
                                        r.magCommitReverted++;
                                        outcome = "REVERTED (MAG illegal -- FcitMag refuses this step)";
                                    } else {
                                        magBreak = imapWitnessAgainst(h1mag, obs, trueMsep);
                                        if (magBreak != null) {
                                            r.magCommitAlsoBreaks++;
                                            outcome = "ALSO BREAKS (FcitMag commit is also non-I-map)";
                                        } else {
                                            r.magCommitFixed++;
                                            outcome = "FIXED (FcitMag commit stays an I-map of G*)";
                                        }
                                    }
                                    r.addMagRecheckWitness(formatMagRecheck(mask, latSet, spurious, e,
                                            dag, truePag, h0, h1, h1break, h1mag, magBreak, outcome));
                                }
                            }

                            // Classify every illegal deletion (deadlock anatomy), first-pass consistent.
                            if (!legal) {
                                int idx = prongIdx(ret.getReason());
                                boolean gen = phantoms.isEmpty();
                                if (gen) { dlGen++; dlGenProng[idx]++; } else { dlNg++; dlNgProng[idx]++; }
                                dlLog.append("    ").append(e).append(" : illegal -- ")
                                        .append(gen ? "genuine/" : "non-genuine/").append(PRONG_NAME[idx])
                                        .append(" -- ").append(ret.getReason()).append('\n');
                            }

                            if (phantoms.isEmpty()) { r.positives++; continue; }

                            // phantom-length census
                            for (DiscriminatingPath dd : phantoms) {
                                int L = dd.getColliderPath().size();
                                if (L >= 0 && L < r.phantomLenHist.length) r.phantomLenHist[L]++;
                                if (L > r.maxPhantomColliderLen) r.maxPhantomColliderLen = L;
                            }

                            DiscriminatingPath worst = phantoms.get(0);
                            for (DiscriminatingPath dd : phantoms) {
                                if (dd.getColliderPath().size() > worst.getColliderPath().size()) worst = dd;
                            }

                            // Lemma-B probe: recorded sepset coverage of the DDP endpoints.
                            Node px = worst.getX(), py = worst.getY(), pv = worst.getV();
                            Set<Node> sxy = null;
                            try { sxy = sepsets.get(px, py); } catch (Exception ignore) { }
                            if (sxy == null) {
                                r.phantomXYNoSepset++;
                            } else {
                                r.phantomXYHasSepset++;
                                if (sxy.contains(pv)) r.phantomVInSepset++;
                            }

                            boolean committed = vEndCommitted(h1, worst);

                            // R4 firing-gap probe. CONTROL: spine collider-definiteness. REAL gap:
                            // endpoint at v on the (last-collider)->v edge.
                            boolean spineDefinite = allSpineCollidersDefinite(h1, worst);
                            if (spineDefinite) r.phantomSpineDefinite++; else r.phantomSpineNonDefinite++;

                            Endpoint wvAtV = wvEndpointAtV(h1, worst);
                            if (wvAtV == Endpoint.ARROW)       r.wvArrowAtV++;
                            else if (wvAtV == Endpoint.CIRCLE) r.wvCircleAtV++;
                            else if (wvAtV == Endpoint.TAIL)   r.wvTailAtV++;
                            else                                r.wvOtherAtV++;

                            if (wvAtV != Endpoint.CIRCLE) {
                                r.addWitness(formatCase("FALSIFIER?: non-genuine, w-v endpoint at v = " + wvAtV
                                                + " (R4 had its precondition yet did not fire)",
                                        mask, latSet, spurious, e, worst, "(non-genuine)", committed, sxy, h1));
                            }

                            // Decisive cross-tab: among non-genuine H1, {R4 abstained} x {legal}.
                            if (legal) {
                                if (abstained) r.nonGenAbstainLegal++; else r.nonGenNoAbstainLegal++;
                            } else {
                                if (abstained) r.nonGenAbstainIllegal++; else r.nonGenNoAbstainIllegal++;
                            }

                            if (legal) {
                                r.counterexamples++;
                                if (committed) r.committedLegal++; else r.circleLegal++;
                                r.addWitness(formatCase(
                                        (committed ? "***** CONSISTENT LIE: legal, non-genuine, COMMITTED v-end *****"
                                                : "***** COUNTEREXAMPLE: legal, non-genuine, circle v-end *****")
                                                + (abstained ? "  [R4-ABSTAINED]" : ""),
                                        mask, latSet, spurious, e, worst, "(legal)", committed, sxy, h1));
                                System.out.println("COUNTEREXAMPLE mask=" + mask + " lat=" + latSet
                                        + " del=" + e + " committedVEnd=" + committed + " abstained=" + abstained
                                        + " -> " + worst);
                            } else {
                                r.illegalNG++;
                                String reason = ret.getReason();
                                if (committed) {
                                    r.committedIllegal++;
                                    switch (prong(reason)) {
                                        case "roundtrip":  r.committedIllegalRoundtrip++;  break;
                                        case "maximality": r.committedIllegalMaximality++; break;
                                        case "acyclic":    r.committedIllegalAcyclic++;    break;
                                        default:           r.committedIllegalOther++;      break;
                                    }
                                    if ("roundtrip".equals(prong(reason))) {
                                        r.addWitness(formatCase("COMMITTED v-end, illegal by ROUND-TRIP "
                                                        + "(outside the under-commit explanation)",
                                                mask, latSet, spurious, e, worst, reason, true, sxy, h1));
                                    }
                                } else {
                                    r.circleIllegal++;
                                }
                            }
                        }

                        // NOSTALL: a gated H0 with no legal single-edge deletion is a deadlock.
                        if (legalEscapes == 0) {
                            r.h0Deadlock++;
                            for (int i = 0; i < 5; i++) {
                                r.dlGenProng[i] += dlGenProng[i];
                                r.dlNgProng[i]  += dlNgProng[i];
                            }
                            if (dlNg == 0 && dlGen > 0)      r.dlAllGenuine++;
                            else if (dlGen == 0 && dlNg > 0) r.dlAllNonGenuine++;
                            else if (dlGen > 0 && dlNg > 0)  r.dlMixed++;
                            r.addWitness(formatDeadlock(mask, latSet, spurious, dlLog.toString(), dag, truePag, h0));

                            // Generalized-Meek (single-edge form) reachability over the subset lattice.
                            MeekInfo mi = subsetReachability(truePag, spurious, oracle, sepsets,
                                    knowledge, initialColliders);
                            if (!mi.singleEdgeReachesTrue) {
                                r.meekCounterexamples++;
                                if (mi.minEscapeArity >= 0 && mi.minEscapeArity < r.meekArityHist.length) {
                                    r.meekArityHist[mi.minEscapeArity]++;
                                }
                            }

                            // Bryan full form: removals + reorientations over I-map PAGs.
                            BryanInfo bi = bryanReachable(truePag, h0, spurious);
                            if (bi.imapFail) {
                                r.bryanNotImap++;     // H0 not an I-map of G*: excluded (and a defect now)
                                r.addImapWitness(formatImapViolation(mask, latSet, spurious,
                                        dag, truePag, h0, bi));
                            } else if (!bi.checked) {
                                r.bryanUnchecked++;
                            } else if (!bi.reachable) {
                                r.bryanCounterexamples++;
                                r.addMeekWitness(formatBryan(mask, latSet, spurious, dag, truePag, h0, mi, bi));
                            }

                            // MAG-sweep escape: the concrete closed fallback, in list order.
                            MagSweepInfo ms = magSweep(truePag, h0, spurious);
                            if (ms.stuck)            r.magSweepStuck++;
                            else if (ms.reachesTrue) r.magSweepReachesTrue++;
                            else                     r.magSweepWrongPag++;
                        } else {
                            r.h0WithEscape++;
                        }
                    }
                }
            } catch (Exception ex) {
                r.skipped++;
                System.err.println("model mask=" + mask + " lat=" + Arrays.toString(latChoice)
                        + " skipped: " + ex.getMessage());
            }
        }

        long d = PROGRESS.incrementAndGet();
        if ((d & 0xFFF) == 0) {
            System.err.printf("…dag %d/%d done%n", d, TOTAL_DAGS);
        }
    }

    // ── FIX 1: THE search, not a transcription of it ───────────────────────────
    // Delegates to FcitSepsets.spanningSepset -- the identical routine Fcit calls
    // from findIndependenceCheckRecursive. Nothing about the candidate family is
    // reimplemented here, so a change to FCIT's search changes the harness too,
    // and no harness result is conditional on a transcription staying faithful.
    //
    // The returned set is oracle-confirmed by construction: the `test` passed in
    // IS the m-separation oracle, and spanningSepset returns a set only after
    // checkIndependence confirms it. No separate confirmation step is needed.
    //
    // What Fcit does around this call and the harness does NOT: consult the
    // committed `sepsets` map and the sticky `foundSepsets` cache first. Those
    // make FCIT's recorded separator path-dependent (it is the set that worked at
    // the graph where the edge was actually removed, frozen thereafter), whereas
    // the harness computes every separator fresh against H0. That difference is a
    // property of the enumerate-H0-directly design, not of the search, and it is
    // the scoping caveat the appendix has to state.
    private static Set<Node> spanningConfirmedSepset(Graph graph, IndependenceTest oracle,
                                                     Node a, Node b) throws InterruptedException {
        FcitSepsets.SepsetResult found = FcitSepsets.spanningSepset(
                graph, oracle, a, b,
                RECURSIVE_DEPTH, DEPTH, RB_RADIUS,
                TIMEOUT < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + TIMEOUT,
                null);
        return (found == null) ? null : found.sepset();
    }

    // ── Cold from-scratch reorientation (the only mode; warm removed) ──────────
    private static int reorient(Graph h, IndependenceTest oracle, SepsetMap sepsets, Knowledge knowledge,
                                Set<Triple> initialColliders) throws InterruptedException {
        GraphUtils.reorientWithCircles(h, false);
        GraphUtils.recallInitialColliders(h, initialColliders, knowledge);
        adjustForExtraSepsets(sepsets, h);

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
        return fciOrient.getR4AbstentionCount();
    }

    // Plain R0 stamping from recorded separators (the shipped-FCIT behaviour).
    // With the spanning search, a recorded Sep(x,y) CONTAINING a common neighbour
    // c exists whenever a valid one does, so this loop never stamps a collider the
    // oracle contradicts -- that is the content of the fix, not extra machinery.
    private static void adjustForExtraSepsets(SepsetMap sepsets, Graph pag) {
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

    private static void printSummary(Result t, String dumpPath, String meekPath) {
        System.out.println("\n==== EXHAUSTIVE SUMMARY (PKE9: spanning sepsets, full coverage, cold) ====");
        System.out.printf("N=%d latent=%d observed=%d maxSpurious=%d%n", N, NUM_LATENT, OBS, MAX_SPURIOUS);
        System.out.printf("DAGs enumerated (fixed order)   : %d%n", t.dagsScanned);
        System.out.printf("models (DAG x latent placement) : %d%n", t.modelsScanned);
        System.out.printf("genuine legal H0 gated           : %d%n", t.gated);
        System.out.printf("H1 states classified             : %d%n", t.h1States);
        System.out.printf("H1 genuine (conj. holds)         : %d%n", t.positives);
        System.out.printf("H1 non-genuine, illegal          : %d%n", t.illegalNG);
        System.out.printf("H1 non-genuine, LEGAL            : %d  <-- CONJECTURE 1 COUNTEREXAMPLES%n", t.counterexamples);
        System.out.printf("models skipped (residual throws) : %d  (should be 0)%n", t.skipped);

        System.out.println("\n==== SEPSET COVERAGE AUDIT (is the table starved?) ====");
        long q = t.sepPairsQueried;
        System.out.printf("non-spurious non-adjacent pairs queried    : %d%n", q);
        System.out.printf("  ...confirmed separator recorded          : %d%n", t.sepPairsConfirmed);
        System.out.printf("  ...no confirmed separator (unrecorded)   : %d%n", t.sepPairsUnconfirmed);
        System.out.printf("spurious subsets skipped (no sepset for a spurious pair) : %d%n", t.spuriousSepMissing);
        System.out.println("  A large unrecorded count would mean adjustForExtraSepsets is again running");
        System.out.println("  on a partial table; near-zero certifies the corrected regime is in force.");

        System.out.println("\n==== R4 ABSTENTIONS ====");
        System.out.printf("total R4 abstentions             : %d%n", t.totalAbstentions);
        System.out.printf("gated H0 needing >=1 abstention  : %d / %d%n", t.gatedWithAbstention, t.gated);
        System.out.printf("H1 with >=1 abstention           : %d / %d%n", t.h1WithAbstention, t.h1States);
        System.out.println("non-genuine H1 by {R4 abstained} x {legal} -- the decisive test:");
        System.out.printf("  %-18s %12s %12s%n", "", "illegal", "LEGAL");
        System.out.printf("  %-18s %12d %12d%n", "abstained",     t.nonGenAbstainIllegal,   t.nonGenAbstainLegal);
        System.out.printf("  %-18s %12d %12d%n", "no abstention",  t.nonGenNoAbstainIllegal, t.nonGenNoAbstainLegal);
        System.out.println("  (abstained, LEGAL) must be 0 for the proof to be without coverage holes.");

        System.out.println("\n==== KERNEL CROSS-TAB (non-genuine H1: v-end committed? x legal?) ====");
        System.out.printf("  %-22s %12s %12s%n", "", "illegal", "LEGAL");
        System.out.printf("  %-22s %12d %12d%n", "v-end = circle",    t.circleIllegal,    t.circleLegal);
        System.out.printf("  %-22s %12d %12d%n", "v-end = committed", t.committedIllegal, t.committedLegal);
        System.out.printf("  committed-illegal by prong -> roundtrip=%d maximality=%d acyclic=%d other=%d%n",
                t.committedIllegalRoundtrip, t.committedIllegalMaximality,
                t.committedIllegalAcyclic, t.committedIllegalOther);

        System.out.println("\n==== PHANTOM SPINE CENSUS ====");
        System.out.printf("max phantom collider-path length : %d%n", t.maxPhantomColliderLen);
        System.out.println("phantom collider-length histogram (over all phantom DDPs):");
        boolean any = false;
        for (int L = 0; L < t.phantomLenHist.length; L++) {
            if (t.phantomLenHist[L] > 0) { System.out.printf("  length %d : %d%n", L, t.phantomLenHist[L]); any = true; }
        }
        if (!any) System.out.println("  (no phantom DDPs observed)");

        System.out.println("\n==== LEMMA-B PROBE (why is the v-end uncommitted?) ====");
        long ng = t.phantomXYHasSepset + t.phantomXYNoSepset;
        System.out.printf("non-genuine H1 with recorded sepset for DDP (x,y) : %d / %d%n", t.phantomXYHasSepset, ng);
        System.out.printf("  ...of those, v in the recorded sepset            : %d%n", t.phantomVInSepset);
        System.out.printf("non-genuine H1 with NO recorded sepset for (x,y)   : %d / %d%n", t.phantomXYNoSepset, ng);

        System.out.println("\n==== R4 FIRING-GAP PROBE ====");
        long pg = t.phantomSpineDefinite + t.phantomSpineNonDefinite;
        System.out.printf("spine colliders all definite : %d / %d   (control)%n",
                t.phantomSpineDefinite, pg);
        System.out.println("(last-collider)->v edge, endpoint AT v:");
        System.out.printf("  circle : %d / %d   <- R4's arrowhead precondition unmet; the under-commit gap%n", t.wvCircleAtV, pg);
        System.out.printf("  arrow  : %d / %d   <- R4 SHOULD have fired; any such case is a falsifier (dumped)%n", t.wvArrowAtV, pg);
        System.out.printf("  tail   : %d / %d%n", t.wvTailAtV, pg);
        System.out.printf("  other  : %d / %d   (no w-v edge / null)%n", t.wvOtherAtV, pg);

        System.out.println("\n==== NOSTALL PROBE ====");
        System.out.printf("gated H0 with >=1 legal escape    : %d / %d%n", t.h0WithEscape, t.gated);
        System.out.printf("gated H0 with NO legal escape      : %d / %d   <-- deadlocks%n",
                t.h0Deadlock, t.gated);
        if (t.h0Deadlock > 0) {
            System.out.println("  -- deadlock composition --");
            System.out.printf("    all deletions genuine-but-illegal (legality non-monotonicity) : %d%n", t.dlAllGenuine);
            System.out.printf("    all deletions non-genuine (mutual phantom masking)           : %d%n", t.dlAllNonGenuine);
            System.out.printf("    mixed                                                        : %d%n", t.dlMixed);
            System.out.println("  -- illegal-deletion prong tally --");
            System.out.printf("    %-13s %12s %12s%n", "", "genuine", "non-genuine");
            for (int i = 0; i < 5; i++) {
                System.out.printf("    %-13s %12d %12d%n", PRONG_NAME[i], t.dlGenProng[i], t.dlNgProng[i]);
            }
        }

        System.out.println("\n==== PAG->PAG MARKOV AUDIT (expected EMPTY under the corrected regime) ====");
        System.out.printf("non-I-map H0 (cold reorient broke I-map-ness)     : %d%n", t.bryanNotImap);
        System.out.printf("I-map step-breaks (legal H1, non-I-map)           : %d%n", t.pagStepBreaks);
        System.out.printf("R0-non-genuine detections (legal, never committed): %d  (alsoStepBreak=%d)%n",
                t.r0NonGenuineLegal, t.r0NgAndStepBreak);

        System.out.println();
        if (t.counterexamples == 0) {
            System.out.printf("PROVED (exhaustive up to size): no legal non-genuine PAG arises for "
                    + "N=%d, latent=%d, spurious<=%d, over ALL DAGs in fixed order x ALL latent "
                    + "placements, under the operational spanning sepset regime. Conjecture 1 holds "
                    + "at this size.%n", N, NUM_LATENT, MAX_SPURIOUS);
            if (t.wvArrowAtV == 0 && t.wvTailAtV == 0 && t.wvOtherAtV == 0) {
                System.out.println("Mechanism certified: every phantom had a CIRCLE at v on the w-v edge, so R4's "
                        + "firing precondition was unmet in all cases -- the under-commit mechanism holds at this size.");
            }
        } else {
            System.out.println("COUNTEREXAMPLE(S) FOUND -- Conjecture 1 is FALSE at this size. See dump for witnesses.");
        }
        if (t.h0Deadlock == 0) {
            System.out.println("NOSTALL holds at this size: every gated H0 has a legal single-edge escape.");
        } else {
            System.out.printf("NOSTALL fails at this size: %d gated H0 are deadlocks (a completeness gap, "
                            + "not a soundness one). See the deadlock dumps and the Meek/Bryan/MAG-sweep sections.%n",
                    t.h0Deadlock);
        }

        System.out.println("\n==== GENERALIZED-MEEK (single-edge form) ====");
        System.out.printf("legal PAGs (denser than truth) with NO legal single-edge move to truth : %d%n",
                t.meekCounterexamples);
        System.out.println("  min escape arity histogram:");
        boolean anyArity = false;
        for (int a = 0; a < t.meekArityHist.length; a++) {
            if (t.meekArityHist[a] > 0) { System.out.printf("    arity %d : %d%n", a, t.meekArityHist[a]); anyArity = true; }
        }
        if (!anyArity) System.out.println("    (none)");

        System.out.println("\n==== GENERALIZED-MEEK (Bryan full form: removals + reorientations) ====");
        System.out.printf("  GENUINE counterexamples (truePag still unreachable) : %d%n", t.bryanCounterexamples);
        System.out.printf("  rescued (a legal I-map removal/reorientation exists) : %d%n",
                Math.max(0, t.meekCounterexamples - t.bryanCounterexamples - t.bryanUnchecked - t.bryanNotImap));
        System.out.printf("  excluded (H0 not an I-map of G*)                     : %d%n", t.bryanNotImap);
        System.out.printf("  unchecked (skeleton over %d edges)                   : %d%n",
                BRYAN_MAX_SKELETON_EDGES, t.bryanUnchecked);
        System.out.println("  Bryan survivors (only) written to: " + meekPath);

        System.out.println("\n==== MAG-SWEEP ESCAPE (closed per-edge fallback, MagToPag projection) ====");
        long magTotal = t.magSweepReachesTrue + t.magSweepStuck + t.magSweepWrongPag;
        System.out.printf("    deadlocks swept                          : %d%n", magTotal);
        System.out.printf("    sweep reconstructs G* exactly            : %d / %d%n", t.magSweepReachesTrue, magTotal);
        System.out.printf("    sweep stuck (a deletion -> illegal MAG)  : %d%n", t.magSweepStuck);
        System.out.printf("    sweep legal but != G*                    : %d%n", t.magSweepWrongPag);

        System.out.println("\nwitnesses / anomalies written to: " + dumpPath);
    }

    // ── Thread-confined accumulator ────────────────────────────────────────────
    static final class Result {
        long dagsScanned, modelsScanned, gated, h1States, positives, illegalNG, counterexamples;
        long circleIllegal, circleLegal, committedIllegal, committedLegal;
        long committedIllegalRoundtrip, committedIllegalMaximality, committedIllegalAcyclic, committedIllegalOther;
        long phantomXYHasSepset, phantomXYNoSepset, phantomVInSepset;
        long maxPhantomColliderLen;
        long totalAbstentions, gatedWithAbstention, h1WithAbstention, skipped;
        long nonGenAbstainIllegal, nonGenAbstainLegal, nonGenNoAbstainIllegal, nonGenNoAbstainLegal;
        long phantomSpineDefinite, phantomSpineNonDefinite;
        long wvArrowAtV, wvCircleAtV, wvTailAtV, wvOtherAtV;
        long[] phantomLenHist = new long[Math.max(2, OBS + 2)];
        // Sepset coverage audit (FIX 2 verification).
        long sepPairsQueried, sepPairsConfirmed, sepPairsUnconfirmed, spuriousSepMissing;
        // NOSTALL probe.
        long h0WithEscape, h0Deadlock;
        long[] dlGenProng = new long[5], dlNgProng = new long[5];
        long dlAllGenuine, dlAllNonGenuine, dlMixed;
        // Generalized-Meek.
        long meekCounterexamples;
        long[] meekArityHist = new long[Math.max(3, OBS + 1)];
        long bryanCounterexamples;
        long bryanUnchecked;
        long bryanNotImap;
        // MAG-sweep escape.
        long magSweepReachesTrue, magSweepStuck, magSweepWrongPag;
        List<String> meekWitnesses = new ArrayList<>();
        long meekSuppressed;
        List<String> witnesses = new ArrayList<>();
        long suppressed;
        List<String> imapWitnesses = new ArrayList<>();
        long imapSuppressed;
        long pagStepBreaks;
        List<String> stepBreakWitnesses = new ArrayList<>();
        long stepBreakSuppressed;
        long magCommitAlsoBreaks, magCommitFixed, magCommitReverted;
        List<String> magRecheckWitnesses = new ArrayList<>();
        long magRecheckSuppressed;
        long r0NonGenuineLegal;
        long r0NgAndStepBreak;
        long stepBreakNotExplainedByR0;
        List<String> r0NgWitnesses = new ArrayList<>();
        long r0NgSuppressed;
        long sbR0, sbR4Shielded, sbCompletion, sbResidue;
        List<String> residueWitnesses = new ArrayList<>();
        long residueSuppressed;

        void addWitness(String s) {
            if (witnesses.size() < WITNESS_CAP) witnesses.add(s); else suppressed++;
        }

        void addImapWitness(String s) {
            if (imapWitnesses.size() < WITNESS_CAP) imapWitnesses.add(s); else imapSuppressed++;
        }

        void addStepBreakWitness(String s) {
            if (stepBreakWitnesses.size() < WITNESS_CAP) stepBreakWitnesses.add(s); else stepBreakSuppressed++;
        }

        void addMagRecheckWitness(String s) {
            if (magRecheckWitnesses.size() < WITNESS_CAP) magRecheckWitnesses.add(s); else magRecheckSuppressed++;
        }

        void addR0NgWitness(String s) {
            if (r0NgWitnesses.size() < WITNESS_CAP) r0NgWitnesses.add(s); else r0NgSuppressed++;
        }

        void addResidueWitness(String s) {
            if (residueWitnesses.size() < WITNESS_CAP) residueWitnesses.add(s); else residueSuppressed++;
        }

        void addMeekWitness(String s) {
            if (meekWitnesses.size() < WITNESS_CAP) meekWitnesses.add(s); else meekSuppressed++;
        }

        static Result merge(Result a, Result b) {
            a.dagsScanned += b.dagsScanned;       a.modelsScanned += b.modelsScanned;
            a.gated += b.gated;                   a.h1States += b.h1States;
            a.positives += b.positives;           a.illegalNG += b.illegalNG;
            a.counterexamples += b.counterexamples;
            a.circleIllegal += b.circleIllegal;   a.circleLegal += b.circleLegal;
            a.committedIllegal += b.committedIllegal; a.committedLegal += b.committedLegal;
            a.committedIllegalRoundtrip += b.committedIllegalRoundtrip;
            a.committedIllegalMaximality += b.committedIllegalMaximality;
            a.committedIllegalAcyclic += b.committedIllegalAcyclic;
            a.committedIllegalOther += b.committedIllegalOther;
            a.phantomXYHasSepset += b.phantomXYHasSepset;
            a.phantomXYNoSepset += b.phantomXYNoSepset;
            a.phantomVInSepset += b.phantomVInSepset;
            a.totalAbstentions += b.totalAbstentions;
            a.gatedWithAbstention += b.gatedWithAbstention;
            a.h1WithAbstention += b.h1WithAbstention;
            a.skipped += b.skipped;
            a.nonGenAbstainIllegal += b.nonGenAbstainIllegal;
            a.nonGenAbstainLegal += b.nonGenAbstainLegal;
            a.nonGenNoAbstainIllegal += b.nonGenNoAbstainIllegal;
            a.nonGenNoAbstainLegal += b.nonGenNoAbstainLegal;
            a.phantomSpineDefinite += b.phantomSpineDefinite;
            a.phantomSpineNonDefinite += b.phantomSpineNonDefinite;
            a.wvArrowAtV += b.wvArrowAtV;   a.wvCircleAtV += b.wvCircleAtV;
            a.wvTailAtV += b.wvTailAtV;     a.wvOtherAtV += b.wvOtherAtV;
            a.sepPairsQueried += b.sepPairsQueried;
            a.sepPairsConfirmed += b.sepPairsConfirmed;
            a.sepPairsUnconfirmed += b.sepPairsUnconfirmed;
            a.spuriousSepMissing += b.spuriousSepMissing;
            a.h0WithEscape += b.h0WithEscape; a.h0Deadlock += b.h0Deadlock;
            for (int i = 0; i < 5; i++) { a.dlGenProng[i] += b.dlGenProng[i]; a.dlNgProng[i] += b.dlNgProng[i]; }
            a.dlAllGenuine += b.dlAllGenuine; a.dlAllNonGenuine += b.dlAllNonGenuine; a.dlMixed += b.dlMixed;
            a.meekCounterexamples += b.meekCounterexamples;
            a.bryanCounterexamples += b.bryanCounterexamples;
            a.bryanUnchecked += b.bryanUnchecked;
            a.bryanNotImap += b.bryanNotImap;
            a.magSweepReachesTrue += b.magSweepReachesTrue;
            a.magSweepStuck += b.magSweepStuck;
            a.magSweepWrongPag += b.magSweepWrongPag;
            int km = Math.min(a.meekArityHist.length, b.meekArityHist.length);
            for (int i = 0; i < km; i++) a.meekArityHist[i] += b.meekArityHist[i];
            for (String s : b.meekWitnesses) a.addMeekWitness(s);
            a.meekSuppressed += b.meekSuppressed;
            a.maxPhantomColliderLen = Math.max(a.maxPhantomColliderLen, b.maxPhantomColliderLen);
            int n = Math.min(a.phantomLenHist.length, b.phantomLenHist.length);
            for (int i = 0; i < n; i++) a.phantomLenHist[i] += b.phantomLenHist[i];
            for (String s : b.witnesses) a.addWitness(s);
            a.suppressed += b.suppressed;
            for (String s : b.imapWitnesses) a.addImapWitness(s);
            a.imapSuppressed += b.imapSuppressed;
            a.pagStepBreaks += b.pagStepBreaks;
            for (String s : b.stepBreakWitnesses) a.addStepBreakWitness(s);
            a.stepBreakSuppressed += b.stepBreakSuppressed;
            a.magCommitAlsoBreaks += b.magCommitAlsoBreaks;
            a.magCommitFixed += b.magCommitFixed;
            a.magCommitReverted += b.magCommitReverted;
            for (String s : b.magRecheckWitnesses) a.addMagRecheckWitness(s);
            a.magRecheckSuppressed += b.magRecheckSuppressed;
            a.r0NonGenuineLegal += b.r0NonGenuineLegal;
            a.r0NgAndStepBreak += b.r0NgAndStepBreak;
            a.stepBreakNotExplainedByR0 += b.stepBreakNotExplainedByR0;
            for (String s : b.r0NgWitnesses) a.addR0NgWitness(s);
            a.r0NgSuppressed += b.r0NgSuppressed;
            a.sbR0 += b.sbR0;
            a.sbR4Shielded += b.sbR4Shielded;
            a.sbCompletion += b.sbCompletion;
            a.sbResidue += b.sbResidue;
            for (String s : b.residueWitnesses) a.addResidueWitness(s);
            a.residueSuppressed += b.residueSuppressed;
            return a;
        }
    }

    private static boolean vEndCommitted(Graph h1, DiscriminatingPath dd) {
        Node v = dd.getV(), y = dd.getY();
        Edge e = h1.getEdge(v, y);
        if (e == null) return false;
        Endpoint atV = h1.getEndpoint(y, v);
        return atV == Endpoint.TAIL || atV == Endpoint.ARROW;
    }

    /** True iff every colliderPath vertex of the DDP is a DEFINITE collider in h1.
     *  Kept as a CONTROL: established to be uniformly true on phantoms, so collider
     *  definiteness is NOT the firing gap. colliderPath is ordered v->x. */
    private static boolean allSpineCollidersDefinite(Graph h1, DiscriminatingPath dd) {
        List<Node> cp = dd.getColliderPath();
        List<Node> path = new ArrayList<>();
        path.add(dd.getV());
        path.addAll(cp);
        path.add(dd.getX());
        for (int j = 1; j <= cp.size(); j++) {
            if (!h1.isDefCollider(path.get(j - 1), path.get(j), path.get(j + 1))) return false;
        }
        return true;
    }

    /** Endpoint AT v on the (last collider)->v edge. R4's discriminating-path
     *  precondition needs an arrowhead into v here (w *-> v); a circle (w <-o v)
     *  means R4 never poses the question. */
    private static Endpoint wvEndpointAtV(Graph h1, DiscriminatingPath dd) {
        List<Node> cp = dd.getColliderPath();
        Node w = cp.isEmpty() ? dd.getX() : cp.get(0);
        Node v = dd.getV();
        if (h1.getEdge(w, v) == null) return null;
        return h1.getEndpoint(w, v);
    }

    // Legality-failure taxonomy. "magconstruct" is tested FIRST so a message that
    // mentions both the failed construction and a cycle is binned by the primary
    // cause. Semantically it is the strongest failure: roundtrip means a MAG exists
    // but PAG(MAG) != H, whereas magconstruct means zhangMagFromPag could not build
    // a MAG at all -- no member of the class exists.
    private static String prong(String reason) {
        if (reason == null) return "other";
        String r = reason.toLowerCase();
        if (r.contains("pag to mag")) return "magconstruct";
        if (r.contains("cannot recover") || r.contains("between a mag and a pag")) return "roundtrip";
        if (r.contains("not maximal") || r.contains("inducing path")) return "maximality";
        if (r.contains("acyclic") || r.contains("cyclic")) return "acyclic";
        return "other";
    }

    static final String[] PRONG_NAME = {"roundtrip", "maximality", "acyclic", "magconstruct", "other"};
    private static int prongIdx(String reason) {
        switch (prong(reason)) {
            case "roundtrip":    return 0;
            case "maximality":   return 1;
            case "acyclic":      return 2;
            case "magconstruct": return 3;
            default:             return 4;
        }
    }

    private static PrintWriter openDump(String path) {
        try {
            return new PrintWriter(new BufferedWriter(new FileWriter(path, false)));
        } catch (IOException io) {
            System.err.println("Could not open dump file " + path + "; falling back to stderr.");
            return new PrintWriter(System.err, true);
        }
    }

    private static String formatCase(String tag, long mask, Set<Integer> latSet, List<Edge> spurious,
                                     Edge deleted, DiscriminatingPath phantom, String reason,
                                     boolean committedVEnd, Set<Node> sepsetXY, Graph h1) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== ").append(tag).append(" ====\n");
        sb.append("  dag mask        : ").append(mask).append('\n');
        sb.append("  latent set      : ").append(latSet).append('\n');
        sb.append("  spurious added  : ").append(spurious).append('\n');
        sb.append("  deleted edge    : ").append(deleted).append('\n');
        sb.append("  phantom DDP     : ").append(phantom).append('\n');
        sb.append("  v-end committed : ").append(committedVEnd).append('\n');
        sb.append("  sepset(x,y)     : ").append(sepsetXY == null ? "(none recorded)" : sepsetXY).append('\n');
        sb.append("  legality reason : ").append(reason).append('\n');
        sb.append("  H1:\n").append(h1).append('\n');
        return sb.toString();
    }

    private static String formatDeadlock(long mask, Set<Integer> latSet, List<Edge> spurious,
                                         String perEdgeLog, Graph dag, Graph truePag, Graph h0) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== NOSTALL DEADLOCK: gated genuine-legal H0, NO legal single-edge deletion ====\n");
        sb.append("  dag mask        : ").append(mask).append('\n');
        sb.append("  latent set      : ").append(latSet).append("  (latent nodes: ").append(latentNames(dag)).append(")\n");
        sb.append("  spurious edges  : ").append(spurious).append("  (k=").append(spurious.size()).append(")\n");
        sb.append("  outcome of deleting each spurious edge from H0:\n");
        sb.append(perEdgeLog);
        sb.append("  true DAG (all variables, latents included):\n").append(dag).append('\n');
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0 (G* + spurious, cold-reoriented):\n").append(h0).append('\n');
        return sb.toString();
    }

    private static String latentNames(Graph dag) {
        List<String> names = new ArrayList<>();
        List<Node> nodes = dag.getNodes();
        for (Node nd : nodes) if (nd.getNodeType() == NodeType.LATENT) names.add(nd.getName());
        return names.toString();
    }

    // ── Generalized-Meek single-edge reachability over the spurious-subset lattice ──
    private static final class MeekInfo {
        boolean singleEdgeReachesTrue;
        int minEscapeArity = -1;
        int k;
        String lattice = "";
    }

    private static MeekInfo subsetReachability(Graph truePag, List<Edge> spurious, IndependenceTest oracle,
                                               SepsetMap sepsets, Knowledge knowledge,
                                               Set<Triple> initialColliders) throws InterruptedException {
        int k = spurious.size();
        int n = 1 << k;
        boolean[] legal = new boolean[n];
        for (int m = 0; m < n; m++) {
            Graph g = new EdgeListGraph(truePag);
            for (int i = 0; i < k; i++) {
                if ((m & (1 << i)) != 0) {
                    Edge e = spurious.get(i);
                    g.addEdge(new Edge(e.getNode1(), e.getNode2(), Endpoint.CIRCLE, Endpoint.CIRCLE));
                }
            }
            reorient(g, oracle, sepsets, knowledge, initialColliders);
            legal[m] = PagLegalityCheck.isLegalPag(g, new HashSet<>()).isLegalPag();
        }

        int full = n - 1;   // all spurious present = H0;  0 = truePag
        boolean[] seen = new boolean[n];
        Deque<Integer> queue = new ArrayDeque<>();
        if (legal[full]) { seen[full] = true; queue.add(full); }
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            for (int i = 0; i < k; i++) {
                if ((cur & (1 << i)) != 0) {
                    int nxt = cur & ~(1 << i);
                    if (!seen[nxt] && legal[nxt]) { seen[nxt] = true; queue.add(nxt); }
                }
            }
        }

        MeekInfo mi = new MeekInfo();
        mi.k = k;
        mi.singleEdgeReachesTrue = seen[0];
        int best = Integer.MAX_VALUE;
        for (int m = 0; m < full; m++) {
            if (legal[m]) best = Math.min(best, Integer.bitCount(full) - Integer.bitCount(m));
        }
        mi.minEscapeArity = (best == Integer.MAX_VALUE) ? -1 : best;

        StringBuilder sb = new StringBuilder();
        for (int m = 0; m < n; m++) {
            sb.append("      {");
            boolean first = true;
            for (int i = 0; i < k; i++) {
                if ((m & (1 << i)) != 0) { if (!first) sb.append(","); sb.append(spurious.get(i)); first = false; }
            }
            sb.append("} : ").append(legal[m] ? "legal" : "illegal").append('\n');
        }
        mi.lattice = sb.toString();
        return mi;
    }

    // ── Bryan's generalized-Meek check ─────────────────────────────────────────
    private static final class BryanInfo {
        boolean reachable;
        boolean checked;
        boolean imapFail;
        String imapWitness;
        int k;
        int statesExplored;
    }

    private static BryanInfo bryanReachable(Graph truePag, Graph h0, List<Edge> spurious) throws InterruptedException {
        BryanInfo bi = new BryanInfo();
        int k = spurious.size();
        bi.k = k;
        if (truePag.getNumEdges() + k > BRYAN_MAX_SKELETON_EDGES) { bi.checked = false; return bi; }
        bi.checked = true;

        List<Node> obs = truePag.getNodes();
        int n = obs.size();

        List<int[]> trPairs = new ArrayList<>();
        List<Set<Node>> trZ = new ArrayList<>();
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
        int T = trPairs.size();
        boolean[] truth = modelOf(new MsepTest(magOfPag(truePag)), obs, trPairs, trZ, T);
        String targetKey = key(truth, T);

        boolean[] h0model = modelOf(new MsepTest(magOfPag(h0)), obs, trPairs, trZ, T);
        String h0key = key(h0model, T);
        if (!subsetModel(h0model, truth)) {
            bi.imapFail = true;
            for (int t = 0; t < T; t++) {
                if (h0model[t] && !truth[t]) {
                    Node wx = obs.get(trPairs.get(t)[0]);
                    Node wy = obs.get(trPairs.get(t)[1]);
                    bi.imapWitness = wx.getName() + " _||_ " + wy.getName() + " | " + trZ.get(t)
                            + "   (m-separated in H0's MAG, m-connected in G*)";
                    break;
                }
            }
            return bi;
        }

        List<Node[]> baseSkel = new ArrayList<>();
        for (Edge e : truePag.getEdges()) baseSkel.add(new Node[]{e.getNode1(), e.getNode2()});

        Map<Integer, Set<String>> statesBySubset = new HashMap<>();
        Map<String, boolean[]> modelByKey = new HashMap<>();
        for (int sub = 0; sub < (1 << k); sub++) {
            List<Node[]> skel = new ArrayList<>(baseSkel);
            for (int i = 0; i < k; i++) {
                if ((sub & (1 << i)) != 0) {
                    Edge e = spurious.get(i);
                    skel.add(new Node[]{e.getNode1(), e.getNode2()});
                }
            }
            Set<String> models = new HashSet<>();
            enumRec(0, new int[skel.size()], skel, obs, trPairs, trZ, T, truth, models, modelByKey);
            statesBySubset.put(sub, models);
        }

        modelByKey.put(h0key, h0model);
        int full = (1 << k) - 1;

        // API-drift guard: a confirmed I-map H0 MUST appear among the enumerated
        // legal I-map MAGs on the full skeleton, and truePag's model on the base
        // skeleton. If either is missing, isLegalMag/magOfPag have drifted from the
        // legality predicate that defines the deadlocks -- fail loudly.
        if (!statesBySubset.getOrDefault(full, Collections.emptySet()).contains(h0key)) {
            throw new IllegalStateException("bryanReachable: I-map H0 absent from enumerated states "
                    + "on the full skeleton -- isLegalMag/magOfPag mismatch, verdict untrustworthy.");
        }
        if (!statesBySubset.getOrDefault(0, Collections.emptySet()).contains(targetKey)) {
            throw new IllegalStateException("bryanReachable: truePag model absent from enumerated states "
                    + "on the base skeleton -- isLegalMag/magOfPag mismatch, verdict untrustworthy.");
        }

        Set<String> visited = new HashSet<>();
        Deque<String> q = new ArrayDeque<>();
        String start = full + "|" + h0key;
        visited.add(start); q.add(start);
        boolean reached = false;
        int explored = 0;
        while (!q.isEmpty()) {
            String cur = q.poll(); explored++;
            int bar = cur.indexOf('|');
            int sub = Integer.parseInt(cur.substring(0, bar));
            boolean[] curModel = modelByKey.get(cur.substring(bar + 1));
            if (sub == 0 && cur.substring(bar + 1).equals(targetKey)) { reached = true; break; }
            for (String bk : statesBySubset.getOrDefault(sub, Collections.emptySet())) {
                if (subsetModel(curModel, modelByKey.get(bk))) {
                    String s = sub + "|" + bk;
                    if (visited.add(s)) q.add(s);
                }
            }
            for (int i = 0; i < k; i++) {
                if ((sub & (1 << i)) != 0) {
                    int nsub = sub & ~(1 << i);
                    for (String bk : statesBySubset.getOrDefault(nsub, Collections.emptySet())) {
                        if (subsetModel(curModel, modelByKey.get(bk))) {
                            String s = nsub + "|" + bk;
                            if (visited.add(s)) q.add(s);
                        }
                    }
                }
            }
        }
        bi.reachable = reached;
        bi.statesExplored = explored;
        return bi;
    }

    private static void enumRec(int idx, int[] orient, List<Node[]> skel, List<Node> obs,
                                List<int[]> trPairs, List<Set<Node>> trZ, int T, boolean[] truth,
                                Set<String> out, Map<String, boolean[]> modelByKey) throws InterruptedException {
        if (idx == skel.size()) {
            Graph mag = new EdgeListGraph(obs);
            for (int e = 0; e < skel.size(); e++) {
                Node a = skel.get(e)[0], b = skel.get(e)[1];
                switch (orient[e]) {
                    case 0:  mag.addDirectedEdge(a, b); break;
                    case 1:  mag.addDirectedEdge(b, a); break;
                    default: mag.addBidirectedEdge(a, b); break;
                }
            }
            if (!isLegalMag(mag)) return;
            boolean[] model = modelOf(new MsepTest(mag), obs, trPairs, trZ, T);
            if (!subsetModel(model, truth)) return;          // must be an I-map of truePag
            String mk = key(model, T);
            if (out.add(mk)) modelByKey.put(mk, model);
            return;
        }
        for (int o = 0; o < 3; o++) {
            orient[idx] = o;
            enumRec(idx + 1, orient, skel, obs, trPairs, trZ, T, truth, out, modelByKey);
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

    private static String key(boolean[] m, int T) {
        StringBuilder sb = new StringBuilder(T);
        for (int i = 0; i < T; i++) sb.append(m[i] ? '1' : '0');
        return sb.toString();
    }

    // A legal MAG is ancestral + maximal.
    private static boolean isLegalMag(Graph g) {
        return g.paths().isLegalMag();
    }

    // PAG -> MAG: the Zhang completion (the map the legality check's round-trip uses).
    private static Graph magOfPag(Graph pag) {
        return GraphTransforms.zhangMagFromPag(pag);
    }

    // MAG -> PAG: UNIFIED on MagToPag.convert, the same projection used by the
    // FcitMag commit path and (by intent) the legality check's round-trip prong.
    // PKE3 mixed this with an FCI-pipeline reconstruction; PKE9 does not.
    private static Graph pagOfMag(Graph mag) throws InterruptedException {
        return new MagToPag(mag).convert(false, EXCLUDE_SELECTION_BIAS);
    }

    private static final class MagSweepInfo {
        boolean reachesTrue;
        boolean stuck;
    }

    // Per-edge MAG sweep over the spurious list, re-canonicalizing (fresh Zhang MAG)
    // each step. Order is spurious-list order.
    private static MagSweepInfo magSweep(Graph truePag, Graph h0, List<Edge> spurious)
            throws InterruptedException {
        MagSweepInfo info = new MagSweepInfo();
        Graph pag = new EdgeListGraph(h0);
        for (Edge e : spurious) {
            Graph mag = magOfPag(pag);
            Edge inMag = mag.getEdge(e.getNode1(), e.getNode2());
            if (inMag == null) { info.stuck = true; return info; }
            mag.removeEdge(inMag);
            if (!isLegalMag(mag)) { info.stuck = true; return info; }
            pag = pagOfMag(mag);
            if (!PagLegalityCheck.isLegalPag(pag, new HashSet<>()).isLegalPag()) {
                info.stuck = true; return info;
            }
        }
        info.reachesTrue = sameOrientedGraph(pag, truePag);
        return info;
    }

    private static boolean sameOrientedGraph(Graph a, Graph b) {
        if (a.getNumEdges() != b.getNumEdges()) return false;
        for (Edge e : a.getEdges()) {
            Node x = e.getNode1(), y = e.getNode2();
            if (b.getEdge(x, y) == null) return false;
            if (a.getEndpoint(x, y) != b.getEndpoint(x, y)) return false;
            if (a.getEndpoint(y, x) != b.getEndpoint(y, x)) return false;
        }
        return true;
    }

    private static String formatImapViolation(long mask, Set<Integer> latSet, List<Edge> spurious,
                                              Graph dag, Graph truePag, Graph h0, BryanInfo bi) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== NON-I-MAP H0 (cold reorient of G*+spurious is NOT an I-map of G*) ====\n");
        sb.append("  Under the corrected sepset regime this population is expected EMPTY; an entry\n");
        sb.append("  here is a defect. The witness CI below is the proof of the violation.\n");
        sb.append("  dag mask              : ").append(mask).append('\n');
        sb.append("  latent set            : ").append(latSet).append("  (latent nodes: ").append(latentNames(dag)).append(")\n");
        sb.append("  extra (spurious) edges: ").append(spurious).append("  (k=").append(bi.k).append(")\n");
        sb.append("  WITNESS (holds in H0, fails in G*): ").append(bi.imapWitness).append('\n');
        sb.append("  true DAG (all variables, latents included):\n").append(dag).append('\n');
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0 (legal PAG = G* + spurious, cold-reoriented):\n").append(h0).append('\n');
        return sb.toString();
    }

    // FcitMag's single-edge PAG->MAG->PAG commit, replicated so any step-break can
    // be re-run through it. Returns null iff the MAG was illegal (FcitMag reverts).
    private static Graph magCommit(Graph h0, Node x, Node y, SepsetMap sepsets)
            throws InterruptedException {
        Graph mag = magOfPag(h0);                 // zhangMagFromPag(h0)
        mag.removeEdge(x, y);
        orientSepsetCollidersInMag(mag, sepsets);
        if (!isLegalMag(mag)) return null;        // illegal MAG: FcitMag reverts
        return pagOfMag(mag);
    }

    // FcitMag's adjustForExtraSepsets analogue (keyset form). Reads the SAME fully
    // covered sepset map as the PAG-level stamp, so the two paths are commensurable.
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

    private static String formatMagRecheck(long mask, Set<Integer> latSet, List<Edge> spurious, Edge removed,
                                           Graph dag, Graph truePag, Graph h0, Graph h1, String pagWitness,
                                           Graph h1mag, String magWitness, String outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== STEP-BREAK RE-COMMITTED THROUGH FcitMag (PAG->MAG->PAG) ====\n");
        sb.append("  Same I-map H0, same removal. Does FcitMag's commit also leave the I-map class?\n");
        sb.append("  FcitMag outcome       : ").append(outcome).append('\n');
        sb.append("  dag mask              : ").append(mask).append('\n');
        sb.append("  latent set            : ").append(latSet).append("  (latent nodes: ").append(latentNames(dag)).append(")\n");
        sb.append("  extra (spurious) edges: ").append(spurious).append('\n');
        sb.append("  edge removed from H0  : ").append(removed).append('\n');
        sb.append("  PAG->PAG witness (in H1, not G*)  : ").append(pagWitness).append('\n');
        if (magWitness != null) {
            sb.append("  FcitMag witness  (in H1mag, not G*): ").append(magWitness).append('\n');
        }
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0 (I-map of G*, legal):\n").append(h0).append('\n');
        sb.append("  H1 via PAG->PAG (legal, NOT I-map):\n").append(h1).append('\n');
        if (h1mag != null) {
            sb.append("  H1 via FcitMag (PAG->MAG->PAG):\n").append(h1mag).append('\n');
        } else {
            sb.append("  H1 via FcitMag: (none -- MAG illegal, step refused)\n");
        }
        return sb.toString();
    }

    // Null iff `cand` is an I-map of the true PAG whose MAG-model is `trueMsep`;
    // otherwise the first CI that `cand` entails but G* does not.
    private static String imapWitnessAgainst(Graph cand, List<Node> obs, MsepTest trueMsep)
            throws InterruptedException {
        MsepTest candT = new MsepTest(magOfPag(cand));
        int n = obs.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                List<Integer> others = new ArrayList<>();
                for (int t = 0; t < n; t++) if (t != i && t != j) others.add(t);
                int mm = others.size();
                for (int z = 0; z < (1 << mm); z++) {
                    Set<Node> Z = new HashSet<>();
                    for (int b = 0; b < mm; b++) if ((z & (1 << b)) != 0) Z.add(obs.get(others.get(b)));
                    if (candT.checkIndependence(obs.get(i), obs.get(j), Z).isIndependent()
                            && !trueMsep.checkIndependence(obs.get(i), obs.get(j), Z).isIndependent()) {
                        return obs.get(i).getName() + " _||_ " + obs.get(j).getName() + " | " + Z
                                + "   (m-separated here, m-connected in G*)";
                    }
                }
            }
        }
        return null;
    }

    // Any collider a*->c<-*b in `mag` whose leg a-c or c-b is absent in G*.
    // wantShielded selects shielded (R4/discriminating-path type) vs unshielded
    // (R0/seed, or realized only by MAG completion).
    private static String magColliderOnSpuriousLeg(Graph mag, Graph truePag, Graph pag, boolean wantShielded) {
        for (Node c : mag.getNodes()) {
            List<Node> adj = mag.getAdjacentNodes(c);
            int m = adj.size();
            for (int i = 0; i < m; i++) {
                for (int j = i + 1; j < m; j++) {
                    Node a = adj.get(i), b = adj.get(j);
                    if (mag.getEndpoint(a, c) != Endpoint.ARROW) continue;
                    if (mag.getEndpoint(b, c) != Endpoint.ARROW) continue;
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

    private static String formatResidue(long mask, Set<Integer> latSet, List<Edge> spurious, Edge removed,
                                        Graph dag, Graph truePag, Graph h0, Graph h1, Graph mag1, String witness) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== STEP-BREAK WITH NO SPURIOUS-LEG COLLIDER (RESIDUE) ====\n");
        sb.append("  A LEGAL non-Markov reorientation in which NO collider, in the PAG or in its MAG\n");
        sb.append("  completion, sits on a spurious leg -- the unsoundness is not traceable to a\n");
        sb.append("  spurious adjacency by the leg test. Investigate before anything else.\n");
        sb.append("  WITNESS (in H1, not in G*): ").append(witness).append('\n');
        sb.append("  dag mask              : ").append(mask).append('\n');
        sb.append("  latent set            : ").append(latSet).append("  (latent nodes: ").append(latentNames(dag)).append(")\n");
        sb.append("  extra (spurious) edges: ").append(spurious).append('\n');
        sb.append("  edge removed from H0  : ").append(removed).append('\n');
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0:\n").append(h0).append('\n');
        sb.append("  H1 (PAG, legal, non-I-map):\n").append(h1).append('\n');
        sb.append("  MAG of H1 (magOfPag):\n").append(mag1).append('\n');
        return sb.toString();
    }

    // Widened genuineness test (R0, not just R4): the first unshielded collider of
    // `cand` with a leg absent in G*, or null if every unshielded collider has both
    // legs in G* (R0-genuine).
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

    private static String formatR0NonGenuine(long mask, Set<Integer> latSet, List<Edge> spurious, Edge removed,
                                             Graph dag, Graph truePag, Graph h0, Graph h1,
                                             String firing, boolean h0imap, boolean alsoStepBreak) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== LEGAL BUT R0-NON-GENUINE (genuineness widened from R4 to R0) ====\n");
        sb.append("  A LEGAL from-scratch reorientation carrying an unshielded collider with a\n");
        sb.append("  spurious leg -- a DETECTION under the widened definition. The decisive column\n");
        sb.append("  is alsoStepBreak; expected false for every entry.\n");
        sb.append("  R0 non-genuine firing : ").append(firing).append('\n');
        sb.append("  H0 was an I-map of G* : ").append(h0imap).append('\n');
        sb.append("  also an I-map step-break (non-I-map H1): ").append(alsoStepBreak).append('\n');
        sb.append("  dag mask              : ").append(mask).append('\n');
        sb.append("  latent set            : ").append(latSet).append("  (latent nodes: ").append(latentNames(dag)).append(")\n");
        sb.append("  extra (spurious) edges: ").append(spurious).append('\n');
        sb.append("  edge removed from H0  : ").append(removed).append('\n');
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0:\n").append(h0).append('\n');
        sb.append("  H1 (legal, R0-non-genuine):\n").append(h1).append('\n');
        return sb.toString();
    }

    private static String formatStepBreak(long mask, Set<Integer> latSet, List<Edge> spurious, Edge removed,
                                          Graph dag, Graph truePag, Graph h0, Graph h1, String witness) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== PAG->PAG STEP LEAVES I-MAP CLASS (defect: expected EMPTY under PKE9 regime) ====\n");
        sb.append("  H0 is an I-map of G*, but the LEGAL single-edge remove+reorient below yields an\n");
        sb.append("  H1 that is NOT an I-map of G*.\n");
        sb.append("  dag mask              : ").append(mask).append('\n');
        sb.append("  latent set            : ").append(latSet).append("  (latent nodes: ").append(latentNames(dag)).append(")\n");
        sb.append("  extra (spurious) edges: ").append(spurious).append('\n');
        sb.append("  edge removed from H0  : ").append(removed).append('\n');
        sb.append("  WITNESS (in H1, not in G*): ").append(witness).append('\n');
        sb.append("  true DAG (all variables, latents included):\n").append(dag).append('\n');
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0 (I-map of G*, legal):\n").append(h0).append('\n');
        sb.append("  H1 (legal, NOT an I-map of G*):\n").append(h1).append('\n');
        return sb.toString();
    }

    private static String formatBryan(long mask, Set<Integer> latSet, List<Edge> spurious,
                                      Graph dag, Graph truePag, Graph h0, MeekInfo mi, BryanInfo bi) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== GENERALIZED-MEEK COUNTEREXAMPLE (Bryan full form: removals + reorientations) ====\n");
        sb.append("  A legal PAG, I-map of the true PAG, from which the true PAG is NOT reachable by any\n");
        sb.append("  sequence of legal single-edge removals and reorientations over I-map PAGs.\n");
        sb.append("  dag mask              : ").append(mask).append('\n');
        sb.append("  latent set            : ").append(latSet).append("  (latent nodes: ").append(latentNames(dag)).append(")\n");
        sb.append("  extra (spurious) edges: ").append(spurious).append("  (k=").append(bi.k).append(")\n");
        sb.append("  single-edge min escape arity : ").append(mi.minEscapeArity).append('\n');
        sb.append("  I-map states explored        : ").append(bi.statesExplored).append('\n');
        sb.append("  true DAG (all variables, latents included):\n").append(dag).append('\n');
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0 (the counterexample PAG = G* + spurious, cold-reoriented):\n").append(h0).append('\n');
        return sb.toString();
    }

    // ── Helpers reused verbatim from the harness line ──────────────────────────
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

    private static DiscriminatingPath firstPhantom(Set<DiscriminatingPath> ddps, Graph truePag) {
        for (DiscriminatingPath dd : ddps) if (isPhantom(dd, truePag)) return dd;
        return null;
    }

    private static List<DiscriminatingPath> allPhantoms(Set<DiscriminatingPath> ddps, Graph truePag) {
        List<DiscriminatingPath> out = new ArrayList<>();
        for (DiscriminatingPath dd : ddps) if (isPhantom(dd, truePag)) out.add(dd);
        return out;
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
}
