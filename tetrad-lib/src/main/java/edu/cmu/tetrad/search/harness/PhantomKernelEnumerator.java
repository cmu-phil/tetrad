/// ////////////////////////////////////////////////////////////////////////////
// PhantomKernelEnumerator.java  (parallel)                                    //
//                                                                            //
// EXHAUSTIVE companion to PhantomCounterexampleHarness. Enumerates EVERY DAG   //
// over a fixed topological order (every structure up to relabeling) x every    //
// latent placement x every spurious-edge subset, running the identical        //
// reorient / legality / zhangMagFromPag machinery. A null result is a PROOF    //
// up to (N, latent, maxSpurious), not a sampling miss.                        //
//                                                                            //
// Parallelism: the outer DAG-mask loop is embarrassingly parallel. We use a    //
// parallel LongStream with a THREAD-CONFINED Result accumulator (collect with  //
// supplier/accumulator/combiner), so there is no shared mutable state, no      //
// lock contention on the hot path, and the merged totals are identical to a    //
// serial run regardless of thread count -- a free correctness check (run with  //
// -Djava.util.concurrent.ForkJoinPool.common.parallelism=1 vs N and compare).  //
// FciOrient.setParallel(false) is kept: no nested parallelism inside a worker. //
// Each task builds its own DAG/PAG/oracle/FciOrient, so nothing Tetrad-stateful //
// is shared; the enumerator uses no RNG. If a Tetrad static cache ever bites,  //
// the serial-vs-parallel count check will expose it.                          //
//                                                                            //
// Kernel target (unchanged): the "consistent lie" -- a non-genuine H1 whose    //
// phantom v-y leg is FULLY COMMITTED yet legal. Cross-tab {v-end committed?} x  //
// {legal?}; (committed,legal) must be empty for Conjecture 1.                  //
//                                                                            //
// New instrumentation:                                                         //
//   * phantom collider-length census (max + histogram) so deep-spine cases     //
//     are visibly in scope, not just geometrically possible.                   //
//   * Lemma-B probe: for each phantom, does the DDP endpoint pair (x,y) have a  //
//     recorded sepset, and is v in it? Tests "R4 is blind on an unrecorded     //
//     pair, hence cannot commit the v-end" as the mechanism behind under-       //
//     commitment.                                                              //
//                                                                            //
// args: [0]=N (default 6) [1]=numLatent (default 1) [2]=maxSpurious (default 2)//
//       [3]=dump path (default phantom_kernel_witnesses.log)                   //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
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
 * Parallel exhaustive enumeration over small latent DAGs that either proves no
 * legal-non-genuine PAG exists up to a given size or prints a witness, while
 * censusing phantom spine length and probing the under-commitment mechanism.
 *
 * @author josephramsey (harness scaffolding by Claude)
 */
public final class PhantomKernelEnumerator {

    private static int N            = 6;
    private static int NUM_LATENT   = 1;
    private static int MAX_SPURIOUS = 2;

    // H1 delete-and-reorient mode. COLD (default): wipe to circles and re-close from scratch.
    // WARM (seeded): inherit H0's marks (minus the deleted edge) and run only the closure.
    // H0's own gating reorient is always COLD, so the gated population is identical in both
    // modes and any deadlock-count difference is attributable to the H1 step alone.
    private static boolean SEEDED_REORIENT = false;

    private static final int     MAX_COND        = 3;
    private static final int     MAX_LEN         = -1;
    private static final int     DEPTH           = -1;
    private static final int     RECURSIVE_DEPTH = -1;
    private static final long    TIMEOUT         = -1L;
    private static final boolean EXCLUDE_SELECTION_BIAS = false;

    // Shared read-only config, set in main before the parallel stream.
    private static int OBS, P;
    private static int[][] PAIR;
    private static long TOTAL_DAGS;
    private static final AtomicLong PROGRESS = new AtomicLong();
    private static final int WITNESS_CAP = 5000; // per merged Result, to bound memory

    public static void main(String[] args) {
        if (args.length > 0) N            = Integer.parseInt(args[0]);
        if (args.length > 1) NUM_LATENT   = Integer.parseInt(args[1]);
        if (args.length > 2) MAX_SPURIOUS = Integer.parseInt(args[2]);
        String dumpPath = (args.length > 3) ? args[3] : "phantom_kernel_witnesses.log";
        if (args.length > 4) {
            String s = args[4];
            SEEDED_REORIENT = s.equalsIgnoreCase("true") || s.equalsIgnoreCase("seeded")
                    || s.equalsIgnoreCase("warm") || s.equals("1");
        }
        String meekPath = (args.length > 5) ? args[5] : "meek_counterexamples.log";

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
        System.err.printf("H1 reorient mode: %s%n",
                SEEDED_REORIENT ? "WARM (seeded from H0's marks)" : "COLD (from-scratch wipe)");

        // Parallel reduction: each worker accumulates into its own Result, merged at the end.
        Result total = LongStream.range(0, TOTAL_DAGS)
                .parallel()
                .collect(Result::new, PhantomKernelEnumerator::accumulate, Result::merge);

        // Write witnesses (rare; collected, written serially at the end).
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

        // Write generalized-Meek single-edge counterexamples to their own log.
        PrintWriter meekDump = openDump(meekPath);
        try {
            meekDump.println("# Generalized-Meek (Bryan full form) counterexamples: legal PAGs, I-maps of the");
            meekDump.println("# true PAG, from which the true PAG is NOT reachable by ANY sequence of legal");
            meekDump.println("# single-edge removals and reorientations over I-map PAGs (removals + reorientations).");
            meekDump.println("# Exhaustive over N=" + N + " latent=" + NUM_LATENT + " maxSpurious=" + MAX_SPURIOUS
                    + " (reorient mode " + (SEEDED_REORIENT ? "WARM" : "COLD") + ").");
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

        printSummary(total, dumpPath, meekPath);
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
                List<int[]> nonAdj = nonAdjacentPairs(truePag, obs);
                if (nonAdj.isEmpty()) continue;

                // Cache each non-adjacent pair's oracle sepset ONCE per model, and keep
                // only separable pairs. Avoids recomputing the same m-sep search inside
                // every spurious subset, and shrinks the subset space. Exhaustiveness is
                // preserved: a pair with no sepset within MAX_COND can't be a spurious edge.
                List<Node[]> sepPairs = new ArrayList<>();
                List<Set<Node>> sepSets = new ArrayList<>();
                for (int[] p : nonAdj) {
                    Node a = obs.get(p[0]), b2 = obs.get(p[1]);
                    Set<Node> sep = oracleSepset(oracle, a, b2, obs, MAX_COND);
                    if (sep != null) { sepPairs.add(new Node[]{a, b2}); sepSets.add(sep); }
                }
                if (sepPairs.isEmpty()) continue;

                int cap = Math.min(MAX_SPURIOUS, sepPairs.size());
                for (int k = 1; k <= cap; k++) {
                    SublistGenerator spGen = new SublistGenerator(sepPairs.size(), k);
                    int[] spChoice;
                    while ((spChoice = spGen.next()) != null) {
                        if (spChoice.length != k) continue;

                        Graph h0 = new EdgeListGraph(truePag);
                        SepsetMap sepsets = new SepsetMap();
                        List<Edge> spurious = new ArrayList<>();
                        for (int si : spChoice) {
                            Node a = sepPairs.get(si)[0];
                            Node b2 = sepPairs.get(si)[1];
                            h0.addEdge(new Edge(a, b2, Endpoint.CIRCLE, Endpoint.CIRCLE));
                            sepsets.set(a, b2, sepSets.get(si));
                            spurious.add(new Edge(a, b2, Endpoint.CIRCLE, Endpoint.CIRCLE));
                        }

                        int abst0 = reorient(h0, oracle, sepsets, knowledge, initialColliders, EXCLUDE_SELECTION_BIAS);
                        if (!PagLegalityCheck.isLegalPag(h0, new HashSet<>()).isLegalPag()) continue;
                        Set<DiscriminatingPath> ddp0 = FciOrient.listDiscriminatingPaths(h0, MAX_LEN, true);
                        if (firstPhantom(ddp0, truePag) != null) continue;
                        r.gated++;
                        if (abst0 > 0) r.gatedWithAbstention++;

                        // NOSTALL probe: count how many single-edge deletions of this gated
                        // genuine-legal H0 land legal. Zero => deadlock (no legal escape).
                        int legalEscapes = 0;
                        // Per-H0 anatomy of illegal deletions, captured on the FIRST pass so it
                        // is exactly consistent with legalEscapes (no divergent recompute).
                        int dlGen = 0, dlNg = 0;
                        int[] dlGenProng = new int[4], dlNgProng = new int[4];
                        StringBuilder dlLog = new StringBuilder();

                        for (Edge e : spurious) {
                            Graph h1 = new EdgeListGraph(h0);
                            Edge present = h1.getEdge(e.getNode1(), e.getNode2());
                            if (present == null) continue;
                            h1.removeEdge(present);

                            int abst1 = reorientStep(h1, oracle, sepsets, knowledge, initialColliders, EXCLUDE_SELECTION_BIAS);
                            r.h1States++;
                            r.totalAbstentions += abst1;
                            boolean abstained = abst1 > 0;
                            if (abstained) r.h1WithAbstention++;

                            Set<DiscriminatingPath> ddp1 = FciOrient.listDiscriminatingPaths(h1, MAX_LEN, true);
                            List<DiscriminatingPath> phantoms = allPhantoms(ddp1, truePag);

                            PagLegalityCheck.LegalPagRet ret = PagLegalityCheck.isLegalPag(h1, new HashSet<>());
                            boolean legal = ret.isLegalPag();
                            if (legal) legalEscapes++;   // a legal single-edge escape from H0

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

                            // R4 firing-gap probe. CONTROL: spine collider-definiteness
                            // (refuted -- uniformly true yet R4 silent). REAL gap: endpoint
                            // at v on the (last-collider)->v edge. R4 needs an arrowhead there
                            // (w *-> v); a circle (w <-o v) means R4 never poses the question.
                            boolean spineDefinite = allSpineCollidersDefinite(h1, worst);
                            if (spineDefinite) r.phantomSpineDefinite++; else r.phantomSpineNonDefinite++;

                            Endpoint wvAtV = wvEndpointAtV(h1, worst);
                            if (wvAtV == Endpoint.ARROW)       r.wvArrowAtV++;
                            else if (wvAtV == Endpoint.CIRCLE) r.wvCircleAtV++;
                            else if (wvAtV == Endpoint.TAIL)   r.wvTailAtV++;
                            else                                r.wvOtherAtV++;

                            // Off-hypothesis cases (w-v endpoint at v NOT a circle) are the
                            // would-be falsifiers: R4 had its precondition yet did not fire.
                            // Dump those for inspection regardless of length.
                            if (wvAtV != Endpoint.CIRCLE) {
                                r.addWitness(formatCase("FALSIFIER?: non-genuine, w-v endpoint at v = " + wvAtV
                                                + " (R4 had its precondition yet did not fire)",
                                        mask, latSet, spurious, e, worst, "(non-genuine)", committed, sxy, h1));
                            }

                            // Decisive cross-tab for the formerly-thrown population:
                            // among non-genuine H1, split by {R4 abstained} x {legal}.
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
                                                + (abstained ? "  [R4-ABSTAINED -- formerly-thrown population]" : ""),
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

                        // NOSTALL: a gated genuine-legal H0 with no legal single-edge
                        // deletion is a deadlock -- a NOSTALL_legal_progress counterexample.
                        if (legalEscapes == 0) {
                            r.h0Deadlock++;
                            for (int i = 0; i < 4; i++) {
                                r.dlGenProng[i] += dlGenProng[i];
                                r.dlNgProng[i]  += dlNgProng[i];
                            }
                            if (dlNg == 0 && dlGen > 0)      r.dlAllGenuine++;
                            else if (dlGen == 0 && dlNg > 0) r.dlAllNonGenuine++;
                            else if (dlGen > 0 && dlNg > 0)  r.dlMixed++;
                            r.addWitness(formatDeadlock(mask, latSet, spurious, dlLog.toString(), dag, truePag, h0));

                            // Generalized-Meek (single-edge form) reachability: a deadlock is a
                            // legal PAG denser than truth with no legal single-edge move toward it,
                            // hence truePag is unreachable via legal single-edge moves -- a
                            // counterexample to the single-edge reachability claim. Record the
                            // minimum escape arity (edges removable in one legal move).
                            MeekInfo mi = subsetReachability(truePag, spurious, oracle, sepsets,
                                    knowledge, initialColliders);
                            if (!mi.singleEdgeReachesTrue) {
                                r.meekCounterexamples++;
                                if (mi.minEscapeArity >= 0 && mi.minEscapeArity < r.meekArityHist.length) {
                                    r.meekArityHist[mi.minEscapeArity]++;
                                }
                            }

                            // Bryan full form: re-test this deadlock against the richer move set
                            // (any legal I-map PAG on a reduced/reoriented skeleton, not just the
                            // canonical cold reorient). Only survivors here are genuine
                            // generalized-Meek counterexamples; they alone go to the meek log.
                            BryanInfo bi = bryanReachable(truePag, h0, spurious);
                            if (!bi.checked) {
                                r.bryanUnchecked++;
                            } else if (!bi.reachable) {
                                r.bryanCounterexamples++;
                                r.addMeekWitness(formatBryan(mask, latSet, spurious, dag, truePag, h0, mi, bi));
                            }

                            // MAG-sweep escape: the concrete closed fallback. For each spurious
                            // edge in turn, take the Zhang MAG of the current PAG, delete that
                            // adjacency, require a legal MAG, project MAG->PAG, re-canonicalize.
                            // Reaches G* iff the sequence lands exactly on truePag.
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

    private static void printSummary(Result t, String dumpPath, String meekPath) {
        System.out.println("\n==== EXHAUSTIVE SUMMARY ====");
        System.out.printf("N=%d latent=%d observed=%d maxSpurious=%d%n", N, NUM_LATENT, OBS, MAX_SPURIOUS);
        System.out.printf("H1 reorient mode: %s%n",
                SEEDED_REORIENT ? "WARM (seeded from H0's marks)" : "COLD (from-scratch wipe)");
        System.out.printf("DAGs enumerated (fixed order)   : %d%n", t.dagsScanned);
        System.out.printf("models (DAG x latent placement) : %d%n", t.modelsScanned);
        System.out.printf("genuine legal H0 gated           : %d%n", t.gated);
        System.out.printf("H1 states classified             : %d%n", t.h1States);
        System.out.printf("H1 genuine (conj. holds)         : %d%n", t.positives);
        System.out.printf("H1 non-genuine, illegal          : %d%n", t.illegalNG);
        System.out.printf("H1 non-genuine, LEGAL            : %d  <-- CONJECTURE 1 COUNTEREXAMPLES%n", t.counterexamples);
        System.out.printf("models skipped (residual throws) : %d  (should be 0 now R4 abstains)%n", t.skipped);

        System.out.println("\n==== R4 ABSTENTIONS (the formerly thrown-and-skipped population) ====");
        System.out.printf("total R4 abstentions             : %d%n", t.totalAbstentions);
        System.out.printf("gated H0 needing >=1 abstention  : %d / %d%n", t.gatedWithAbstention, t.gated);
        System.out.printf("H1 with >=1 abstention           : %d / %d%n", t.h1WithAbstention, t.h1States);
        System.out.println("non-genuine H1 by {R4 abstained} x {legal} -- the decisive test:");
        System.out.printf("  %-18s %12s %12s%n", "", "illegal", "LEGAL");
        System.out.printf("  %-18s %12d %12d%n", "abstained",     t.nonGenAbstainIllegal,   t.nonGenAbstainLegal);
        System.out.printf("  %-18s %12d %12d%n", "no abstention",  t.nonGenNoAbstainIllegal, t.nonGenNoAbstainLegal);
        System.out.println("  (abstained, LEGAL) is a counterexample the throw-and-abort used to hide.");
        System.out.println("  It must be 0; if it is, the formerly-thrown population is also uniformly illegal");
        System.out.println("  and the exhaustive proof at this size is finally without coverage holes.");

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
        System.out.println("  NOTE: no-sepset alone does NOT explain the circle -- R4 would fall back to RB.");
        System.out.println("  With total abstentions AND stored sepsets both 0, R4 never reached the sepset");
        System.out.println("  lookup at all -- it never fired on the phantom path. See the firing-gap probe.");

        System.out.println("\n==== R4 FIRING-GAP PROBE (why doesn't R4 orient the phantom path?) ====");
        long pg = t.phantomSpineDefinite + t.phantomSpineNonDefinite;
        System.out.printf("spine colliders all definite : %d / %d   (control: definite yet R4 silent)%n",
                t.phantomSpineDefinite, pg);
        System.out.println("(last-collider)->v edge, endpoint AT v -- the candidate gap:");
        System.out.printf("  circle : %d / %d   <- R4's arrowhead precondition unmet; this is the gap%n", t.wvCircleAtV, pg);
        System.out.printf("  arrow  : %d / %d   <- R4 SHOULD have fired; any such case is a falsifier (dumped)%n", t.wvArrowAtV, pg);
        System.out.printf("  tail   : %d / %d%n", t.wvTailAtV, pg);
        System.out.printf("  other  : %d / %d   (no w-v edge / null)%n", t.wvOtherAtV, pg);
        System.out.println("  Mechanism: phantom ==> circle at v on the w-v edge ==> R4 never poses the");
        System.out.println("  discriminated-collider question ==> v-y circle survives ==> Lemma A forces it.");
        System.out.println("  Collider-definiteness is a refuted control: uniformly true, yet R4 stays silent,");
        System.out.println("  so the gap is the missing arrowhead at v, not the spine.");

        System.out.println("\n==== NOSTALL PROBE (does every gated H0 have a legal single-edge escape?) ====");
        System.out.printf("gated H0 with >=1 legal escape    : %d / %d%n", t.h0WithEscape, t.gated);
        System.out.printf("gated H0 with NO legal escape      : %d / %d   <-- NOSTALL COUNTEREXAMPLES (deadlocks)%n",
                t.h0Deadlock, t.gated);
        System.out.println("  A deadlock is a genuine, legal, dense H0 from which EVERY single spurious");
        System.out.println("  deletion lands illegal. It must be 0 for NOSTALL_legal_progress to hold at this");
        System.out.println("  size. k=1 H0 can never deadlock (deleting the lone spurious edge yields the");
        System.out.println("  true PAG); a deadlock therefore requires k>=2 mutually-masking spurious edges.");
        System.out.println("  Deadlocks (if any) are dumped with the per-edge illegality reason.");
        if (t.h0Deadlock > 0) {
            System.out.println("  -- deadlock composition (what kind of trap?) --");
            System.out.printf("    all deletions genuine-but-illegal (legality non-monotonicity) : %d%n", t.dlAllGenuine);
            System.out.printf("    all deletions non-genuine (mutual phantom masking)           : %d%n", t.dlAllNonGenuine);
            System.out.printf("    mixed (both mechanisms in one trap)                          : %d%n", t.dlMixed);
            System.out.println("  -- illegal-deletion prong tally (which legality test fails) --");
            System.out.printf("    %-12s %12s %12s%n", "", "genuine", "non-genuine");
            for (int i = 0; i < 4; i++) {
                System.out.printf("    %-12s %12d %12d%n", PRONG_NAME[i], t.dlGenProng[i], t.dlNgProng[i]);
            }
            System.out.println("  Non-genuine deletions should land in 'roundtrip' (the phantom forces");
            System.out.println("  PAG(M)!=H1); genuine-but-illegal deletions in 'maximality' are the");
            System.out.println("  non-monotonic traps -- and, being boundary cases, the likely source of the");
            System.out.println("  run-to-run jitter in the deadlock count.");
        }

        System.out.println();
        if (t.counterexamples == 0) {
            System.out.printf("PROVED (exhaustive up to size): no legal non-genuine PAG arises for "
                    + "N=%d, latent=%d, spurious<=%d, over ALL DAGs in fixed order x ALL latent "
                    + "placements. Conjecture 1 holds at this size.%n", N, NUM_LATENT, MAX_SPURIOUS);
            if (t.committedIllegalRoundtrip == 0) {
                System.out.println("Moreover every round-trip catch had a CIRCLE v-end: the forcing/under-commit "
                        + "argument is the complete mechanism for the round-trip prong at this size.");
            } else {
                System.out.println("NOTE: some round-trip catches had a COMMITTED v-end -- the forcing argument "
                        + "does not cover those; inspect the dump to extend it.");
            }
            if (t.wvArrowAtV == 0 && t.wvTailAtV == 0 && t.wvOtherAtV == 0) {
                System.out.println("Mechanism certified: every phantom had a CIRCLE at v on the w-v edge, so R4's "
                        + "firing precondition was unmet in all cases -- Lemma B's under-commit holds at this size.");
            } else {
                System.out.println("NOTE: some phantoms had a non-circle w-v endpoint at v (see FALSIFIER? dumps) -- "
                        + "R4 had its precondition yet did not fire; Lemma B's mechanism needs those cases explained.");
            }
        } else {
            System.out.println("COUNTEREXAMPLE(S) FOUND -- Conjecture 1 is FALSE at this size. See dump for witnesses.");
        }
        if (t.h0Deadlock == 0) {
            System.out.println("NOSTALL holds at this size: every gated genuine-legal H0 has a legal single-edge "
                    + "escape, so the delete-and-reorient trajectory never stalls.");
        } else {
            System.out.printf("NOSTALL FAILS at this size: %d gated H0 are deadlocks (every single deletion "
                    + "illegal). These are genuine+legal but denser than truth -- a completeness gap, not a "
                    + "soundness one. See the NOSTALL DEADLOCK dumps.%n", t.h0Deadlock);
        }
        System.out.println("\n==== GENERALIZED-MEEK (single-edge form) COUNTEREXAMPLES ====");
        System.out.printf("legal PAGs (denser than truth) with NO legal single-edge move to truth : %d%n",
                t.meekCounterexamples);
        System.out.println("  (each is a deadlock; truePag is unreachable from it by legal single-edge moves)");
        System.out.println("  min escape arity histogram (edges removable in one legal move):");
        boolean anyArity = false;
        for (int a = 0; a < t.meekArityHist.length; a++) {
            if (t.meekArityHist[a] > 0) { System.out.printf("    arity %d : %d%n", a, t.meekArityHist[a]); anyArity = true; }
        }
        if (!anyArity) System.out.println("    (none)");
        System.out.println("  arity>=2 means the true PAG can only be reached by removing >=2 edges at once,");
        System.out.println("  so the single-edge reachability claim fails on these legal PAGs (this is the");
        System.out.println("  operational/NOSTALL form: it tests only the canonical cold reorient of each");
        System.out.println("  reduced skeleton).");

        System.out.println("\n==== GENERALIZED-MEEK (Bryan full form: removals + reorientations) ====");
        System.out.printf("deadlocks re-tested against the richer move set (any legal I-map PAG on a%n");
        System.out.printf("reduced/reoriented skeleton, not just the canonical cold reorient):%n");
        System.out.printf("  GENUINE counterexamples (truePag still unreachable) : %d%n", t.bryanCounterexamples);
        System.out.printf("  rescued (a legal I-map removal/reorientation exists) : %d%n",
                Math.max(0, t.meekCounterexamples - t.bryanCounterexamples - t.bryanUnchecked));
        System.out.printf("  unchecked (skeleton over %d edges, enumeration skipped) : %d%n",
                BRYAN_MAX_SKELETON_EDGES, t.bryanUnchecked);
        if (t.bryanCounterexamples == 0 && t.bryanUnchecked == 0) {
            System.out.println("  => every single-edge counterexample is rescued by the richer move set;");
            System.out.println("     Bryan's conjecture is NOT refuted at this size (the refutation is only of");
            System.out.println("     the operational single-edge/canonical form, i.e. why FCIT needs the seed).");
        } else if (t.bryanCounterexamples > 0) {
            System.out.println("  => genuine counterexamples to Bryan's full conjecture exist; see the meek log.");
        }
        System.out.println("  Bryan survivors (only) written to: " + meekPath);

        System.out.println("\n==== MAG-SWEEP ESCAPE (closed per-edge fallback FCIT could run) ====");
        System.out.println("  At each deadlock, for each spurious edge in turn: Zhang MAG of the current");
        System.out.println("  PAG, delete that adjacency, require a legal MAG, project MAG->PAG. List order.");
        long magTotal = t.magSweepReachesTrue + t.magSweepStuck + t.magSweepWrongPag;
        System.out.printf("    deadlocks swept                          : %d%n", magTotal);
        System.out.printf("    sweep reconstructs G* exactly            : %d / %d%n", t.magSweepReachesTrue, magTotal);
        System.out.printf("    sweep stuck (a deletion -> illegal MAG)  : %d%n", t.magSweepStuck);
        System.out.printf("    sweep legal but != G*                    : %d%n", t.magSweepWrongPag);
        if (magTotal > 0 && t.magSweepStuck == 0 && t.magSweepWrongPag == 0) {
            System.out.println("  => the concrete per-edge MAG sweep clears every deadlock to G* in list order:");
            System.out.println("     a deterministic closed operator with no stall at this size (stronger than the");
            System.out.println("     existence-only Bryan result).");
        } else if (t.magSweepStuck > 0 || t.magSweepWrongPag > 0) {
            System.out.println("  => some deadlocks are NOT cleared by the canonical/greedy single-edge sweep in");
            System.out.println("     spurious-list order; those need a reorder or a non-canonical MAG (the search).");
        }

        System.out.println("\nwitnesses / anomalies written to: " + dumpPath);
    }

    // ── Thread-confined accumulator ────────────────────────────────────────────
    static final class Result {
        long dagsScanned, modelsScanned, gated, h1States, positives, illegalNG, counterexamples;
        long circleIllegal, circleLegal, committedIllegal, committedLegal;
        long committedIllegalRoundtrip, committedIllegalMaximality, committedIllegalAcyclic, committedIllegalOther;
        long phantomXYHasSepset, phantomXYNoSepset, phantomVInSepset;
        long maxPhantomColliderLen;
        // R4 abstention instrumentation (formerly the throw-and-abort population).
        long totalAbstentions, gatedWithAbstention, h1WithAbstention, skipped;
        long nonGenAbstainIllegal, nonGenAbstainLegal, nonGenNoAbstainIllegal, nonGenNoAbstainLegal;
        long phantomSpineDefinite, phantomSpineNonDefinite;
        long wvArrowAtV, wvCircleAtV, wvTailAtV, wvOtherAtV;
        long[] phantomLenHist = new long[Math.max(2, OBS + 2)];
        // NOSTALL probe: gated H0 with at least one legal single-edge escape vs none (deadlock).
        long h0WithEscape, h0Deadlock;
        // Deadlock anatomy: per-edge illegal-deletion prong tallies, split genuine vs non-genuine,
        // and per-deadlock composition (all-genuine = legality non-monotonicity; all-non-genuine =
        // mutual phantom masking; mixed = both).
        long[] dlGenProng = new long[4], dlNgProng = new long[4];
        long dlAllGenuine, dlAllNonGenuine, dlMixed;
        // Generalized-Meek single-edge counterexamples (each deadlock is one) + escape-arity census.
        long meekCounterexamples;
        long[] meekArityHist = new long[Math.max(3, OBS + 1)];
        long bryanCounterexamples;
        long bryanUnchecked;
        // MAG-sweep escape: the closed per-edge fallback (Zhang MAG, delete the adjacency,
        // MAG->PAG, re-canonicalize each step) run over a stall's spurious set, in list order.
        long magSweepReachesTrue;   // sweep lands exactly on truePag
        long magSweepStuck;         // a deletion left an illegal MAG (no clean projection)
        long magSweepWrongPag;      // sweep finished legal but not on truePag
        List<String> meekWitnesses = new ArrayList<>();
        long meekSuppressed;
        List<String> witnesses = new ArrayList<>();
        long suppressed;

        void addWitness(String s) {
            if (witnesses.size() < WITNESS_CAP) witnesses.add(s); else suppressed++;
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
            a.h0WithEscape += b.h0WithEscape; a.h0Deadlock += b.h0Deadlock;
            for (int i = 0; i < 4; i++) { a.dlGenProng[i] += b.dlGenProng[i]; a.dlNgProng[i] += b.dlNgProng[i]; }
            a.dlAllGenuine += b.dlAllGenuine; a.dlAllNonGenuine += b.dlAllNonGenuine; a.dlMixed += b.dlMixed;
            a.meekCounterexamples += b.meekCounterexamples;
            a.bryanCounterexamples += b.bryanCounterexamples;
            a.bryanUnchecked += b.bryanUnchecked;
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
     *  definiteness is NOT the firing gap. colliderPath is ordered v->x, so the path
     *  in v-first order is v, cp[0], cp[1], ..., x. */
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

    /** Endpoint AT v on the (last collider)->v edge. w = colliderPath[0] (the
     *  collider adjacent to v, equals dd.getW()). R4's discriminating-path
     *  precondition needs an arrowhead into v here (w *-> v); the witness graphs
     *  show a circle instead (w <-o v), which is why R4 never fires. */
    private static Endpoint wvEndpointAtV(Graph h1, DiscriminatingPath dd) {
        List<Node> cp = dd.getColliderPath();
        Node w = cp.isEmpty() ? dd.getX() : cp.get(0);
        Node v = dd.getV();
        if (h1.getEdge(w, v) == null) return null;
        return h1.getEndpoint(w, v);
    }

    private static String prong(String reason) {
        if (reason == null) return "other";
        String r = reason.toLowerCase();
        if (r.contains("cannot recover") || r.contains("between a mag and a pag")) return "roundtrip";
        if (r.contains("not maximal") || r.contains("inducing path")) return "maximality";
        if (r.contains("acyclic") || r.contains("cyclic")) return "acyclic";
        return "other";
    }

    // Indexed form of prong() for fast per-deadlock tallies. Indices match PRONG_NAME.
    static final String[] PRONG_NAME = {"roundtrip", "maximality", "acyclic", "other"};
    private static int prongIdx(String reason) {
        switch (prong(reason)) {
            case "roundtrip":  return 0;
            case "maximality": return 1;
            case "acyclic":    return 2;
            default:           return 3;
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

    // NOSTALL witness: a gated genuine-legal H0 from which EVERY single spurious
    // deletion lands illegal. The per-edge log is captured on the first pass so it
    // is exactly consistent with the escape count (no order-divergent recompute).
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
        sb.append("  H0 (the counterexample PAG = G* + spurious, cold-reoriented):\n").append(h0).append('\n');
        return sb.toString();
    }

    // Latent node names, for readability alongside the (index-based) latent set.
    private static String latentNames(Graph dag) {
        List<String> names = new ArrayList<>();
        List<Node> nodes = dag.getNodes();
        for (Node nd : nodes) if (nd.getNodeType() == NodeType.LATENT) names.add(nd.getName());
        return names.toString();
    }

    // ── Generalized-Meek single-edge reachability over the spurious-subset lattice ──
    private static final class MeekInfo {
        boolean singleEdgeReachesTrue;   // is truePag reachable from H0 by legal single-edge moves?
        int minEscapeArity = -1;         // fewest edges removable in one legal move from H0
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
            reorient(g, oracle, sepsets, knowledge, initialColliders, EXCLUDE_SELECTION_BIAS);
            legal[m] = PagLegalityCheck.isLegalPag(g, new HashSet<>()).isLegalPag();
        }

        int full = n - 1;   // all spurious present = H0;  0 = truePag
        // BFS over legal single-edge removals from H0; can we reach truePag (empty)?
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
        // Smallest one-move edge-removal from H0 that lands legal (>=2 for a deadlock).
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

    private static String formatMeekCounterexample(long mask, Set<Integer> latSet, List<Edge> spurious,
                                                   Graph h0, MeekInfo mi) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== GENERALIZED-MEEK COUNTEREXAMPLE (single-edge form) ====\n");
        sb.append("  A legal PAG, denser than the true PAG, with NO legal single-edge move toward it.\n");
        sb.append("  dag mask              : ").append(mask).append('\n');
        sb.append("  latent set            : ").append(latSet).append('\n');
        sb.append("  extra (spurious) edges: ").append(spurious).append("  (k=").append(mi.k).append(")\n");
        sb.append("  truePag reachable by legal single-edge moves : ").append(mi.singleEdgeReachesTrue).append('\n');
        sb.append("  min escape arity (edges per legal move)      : ").append(mi.minEscapeArity).append('\n');
        sb.append("  subset lattice (truePag + subset, cold-reoriented):\n").append(mi.lattice);
        sb.append("  H0 (the counterexample PAG):\n").append(h0).append('\n');
        return sb.toString();
    }

    // ── Bryan's generalized-Meek check ────────────────────────────────────────────
    // Among legal PAGs that are I-maps of truePag, is truePag reachable from H0 by single-edge
    // REMOVALS and REORIENTATIONS (the |Hi|-|Hi+1| in {0,1} move set), under the I(Hi) subset
    // I(Hi+1) order? Only deadlocks unreachable here are genuine counterexamples to Bryan's
    // conjecture; the single-edge canonical form (subsetReachability) is strictly weaker.
    //
    // A "state" is (skeleton = truePag.skel + subset of spurious, independence model). The model is
    // the set of m-separations realised by SOME legal MAG on that skeleton; the state is admissible
    // iff its model is a subset of truePag's model (I-map). Two flagged Tetrad calls below
    // (isLegalMag, magOfPag) must be checked against your API.
    private static final int BRYAN_MAX_SKELETON_EDGES = 12;   // guard on 3^edges MAG enumeration

    private static final class BryanInfo {
        boolean reachable;     // truePag reachable from H0 via Bryan moves
        boolean checked;       // false if skipped (skeleton too large)
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

        // Canonical (pair, conditioning-set) enumeration -> independence-model bitvectors.
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
        // truth model from the true MAG, so it is commensurable with every enumerated state
        // (all models computed via MsepTest on a MAG; do NOT mix in the DAG oracle here).
        boolean[] truth = modelOf(new MsepTest(magOfPag(truePag)), obs, trPairs, trZ, T);
        String targetKey = key(truth, T);

        // truePag.skel as node-pair list (orientation ignored; we re-orient freely).
        List<Node[]> baseSkel = new ArrayList<>();
        for (Edge e : truePag.getEdges()) baseSkel.add(new Node[]{e.getNode1(), e.getNode2()});

        // For each spurious subset: distinct legal I-map MAG models on that skeleton.
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

        // Start = H0's model on the full subset; target = truth on the empty subset.
        boolean[] h0model = modelOf(new MsepTest(magOfPag(h0)), obs, trPairs, trZ, T);
        String h0key = key(h0model, T);
        modelByKey.put(h0key, h0model);
        int full = (1 << k) - 1;

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
            // reorientation: same subset, to any enumerated I-map state with curModel subset of it
            for (String bk : statesBySubset.getOrDefault(sub, Collections.emptySet())) {
                if (subsetModel(curModel, modelByKey.get(bk))) {
                    String s = sub + "|" + bk;
                    if (visited.add(s)) q.add(s);
                }
            }
            // removal: clear one spurious bit, to any enumerated I-map state on the reduced subset
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

    // FLAGGED: verify against your API. A legal MAG is ancestral + maximal.
    private static boolean isLegalMag(Graph g) {
        return g.paths().isLegalMag();
    }

    // FLAGGED: verify against your API (the PAG->MAG used by the round-trip / legality check).
    private static Graph magOfPag(Graph pag) {
        return GraphTransforms.zhangMagFromPag(pag);
    }

    // FLAGGED: MAG -> PAG. This SHOULD be the same map PagLegalityCheck uses for its
    // round-trip prong (the "PAG of an implied MAG" in its failure message). If that is
    // exposed, call it here so this check is consistent with the legality test that
    // defines the deadlocks. The fallback below builds it via the FCI pipeline: circles
    // + the MAG's own unshielded colliders + final rules, m-separation read from the MAG.
    private static Graph pagOfMag(Graph mag) throws InterruptedException {
        Graph pag = new EdgeListGraph(mag);
        Set<Triple> magColliders = noteInitialColliders(mag.getNodes(), mag);
        GraphUtils.reorientWithCircles(pag, false);
        GraphUtils.recallInitialColliders(pag, magColliders, new Knowledge());
        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(new MsepTest(mag), TIMEOUT);
        strategy.setSepsetMap(new SepsetMap());
        strategy.setVerbose(false);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);
        strategy.setDepth(DEPTH);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setVerbose(false);
        fciOrient.setParallel(false);
        fciOrient.setCompleteRuleSetUsed(true);
        fciOrient.setRecursiveDepth(RECURSIVE_DEPTH);
        fciOrient.setMaxDiscriminatingPathLength(MAX_LEN);
        fciOrient.setKnowledge(new Knowledge());
        fciOrient.finalOrientation(pag, EXCLUDE_SELECTION_BIAS);
        return pag;
    }

    private static final class MagSweepInfo {
        boolean reachesTrue;   // sweep lands exactly on truePag
        boolean stuck;         // a deletion left an illegal/non-maximal MAG (no clean projection)
    }

    // Per-edge MAG sweep over the spurious list, re-canonicalizing (fresh Zhang MAG) each
    // step. Order is spurious-list order; a different order could clear a case this one
    // gets stuck on -- magSweepStuck surfaces exactly those.
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

    // Exact oriented-graph equality: same edge set with identical endpoints both ways.
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

    // ── Helpers reused verbatim from PhantomCounterexampleHarness ──────────────
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

    private static int reorient(Graph h, IndependenceTest oracle, SepsetMap sepsets, Knowledge knowledge,
                                Set<Triple> initialColliders, boolean excludeSelectionBias)
            throws InterruptedException {
        // H0 gating reorient: always COLD (from-scratch), regardless of the H1 toggle.
        return reorientCore(h, oracle, sepsets, knowledge, initialColliders, excludeSelectionBias, true);
    }

    private static int reorientStep(Graph h, IndependenceTest oracle, SepsetMap sepsets, Knowledge knowledge,
                                    Set<Triple> initialColliders, boolean excludeSelectionBias)
            throws InterruptedException {
        // H1 delete-and-reorient step: COLD wipe by default, or WARM (seeded from H0's inherited
        // marks) when SEEDED_REORIENT is set. WARM omits the reorientWithCircles wipe so the
        // closure propagates from H0's orientation instead of re-deriving every mark from circles.
        return reorientCore(h, oracle, sepsets, knowledge, initialColliders, excludeSelectionBias, !SEEDED_REORIENT);
    }

    private static int reorientCore(Graph h, IndependenceTest oracle, SepsetMap sepsets, Knowledge knowledge,
                                    Set<Triple> initialColliders, boolean excludeSelectionBias, boolean wipe)
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

        if (wipe) GraphUtils.reorientWithCircles(h, false);
        GraphUtils.recallInitialColliders(h, initialColliders, knowledge);
        adjustForExtraSepsets(sepsets, h);
        fciOrient.finalOrientation(h, excludeSelectionBias);
        return fciOrient.getR4AbstentionCount();
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
}
