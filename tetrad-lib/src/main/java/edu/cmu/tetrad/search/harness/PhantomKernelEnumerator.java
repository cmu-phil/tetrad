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

        printSummary(total, dumpPath);
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

                int cap = Math.min(MAX_SPURIOUS, nonAdj.size());
                for (int k = 1; k <= cap; k++) {
                    SublistGenerator spGen = new SublistGenerator(nonAdj.size(), k);
                    int[] spChoice;
                    while ((spChoice = spGen.next()) != null) {
                        if (spChoice.length != k) continue;

                        Graph h0 = new EdgeListGraph(truePag);
                        SepsetMap sepsets = new SepsetMap();
                        List<Edge> spurious = new ArrayList<>();
                        boolean ok = true;
                        for (int si : spChoice) {
                            Node a = obs.get(nonAdj.get(si)[0]);
                            Node b2 = obs.get(nonAdj.get(si)[1]);
                            Set<Node> sep = oracleSepset(oracle, a, b2, obs, MAX_COND);
                            if (sep == null) { ok = false; break; }
                            h0.addEdge(new Edge(a, b2, Endpoint.CIRCLE, Endpoint.CIRCLE));
                            sepsets.set(a, b2, sep);
                            spurious.add(new Edge(a, b2, Endpoint.CIRCLE, Endpoint.CIRCLE));
                        }
                        if (!ok) continue;

                        int abst0 = reorient(h0, oracle, sepsets, knowledge, initialColliders, EXCLUDE_SELECTION_BIAS);
                        if (!PagLegalityCheck.isLegalPag(h0, new HashSet<>()).isLegalPag()) continue;
                        Set<DiscriminatingPath> ddp0 = FciOrient.listDiscriminatingPaths(h0, MAX_LEN, true);
                        if (firstPhantom(ddp0, truePag) != null) continue;
                        r.gated++;
                        if (abst0 > 0) r.gatedWithAbstention++;

                        for (Edge e : spurious) {
                            Graph h1 = new EdgeListGraph(h0);
                            Edge present = h1.getEdge(e.getNode1(), e.getNode2());
                            if (present == null) continue;
                            h1.removeEdge(present);

                            int abst1 = reorient(h1, oracle, sepsets, knowledge, initialColliders, EXCLUDE_SELECTION_BIAS);
                            r.h1States++;
                            r.totalAbstentions += abst1;
                            boolean abstained = abst1 > 0;
                            if (abstained) r.h1WithAbstention++;

                            Set<DiscriminatingPath> ddp1 = FciOrient.listDiscriminatingPaths(h1, MAX_LEN, true);
                            List<DiscriminatingPath> phantoms = allPhantoms(ddp1, truePag);

                            PagLegalityCheck.LegalPagRet ret = PagLegalityCheck.isLegalPag(h1, new HashSet<>());
                            boolean legal = ret.isLegalPag();

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

                            // R4 firing-gap probe: is the phantom's spine fully made of definite
                            // colliders? If not, that's why R4 never fired (it requires definite
                            // colliders); if it IS, the gap is elsewhere and we dump it.
                            boolean spineDefinite = allSpineCollidersDefinite(h1, worst);
                            if (spineDefinite) {
                                r.phantomSpineDefinite++;
                                r.addWitness(formatCase("PHANTOM with FULLY DEFINITE spine but R4 did not fire "
                                                + "(firing-gap NOT explained by non-definite colliders)",
                                        mask, latSet, spurious, e, worst, "(non-genuine)", committed, sxy, h1));
                            } else {
                                r.phantomSpineNonDefinite++;
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

    private static void printSummary(Result t, String dumpPath) {
        System.out.println("\n==== EXHAUSTIVE SUMMARY ====");
        System.out.printf("N=%d latent=%d observed=%d maxSpurious=%d%n", N, NUM_LATENT, OBS, MAX_SPURIOUS);
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
        System.out.printf("phantoms with >=1 NON-definite spine collider : %d / %d%n", t.phantomSpineNonDefinite, pg);
        System.out.printf("phantoms with FULLY definite spine            : %d / %d%n", t.phantomSpineDefinite, pg);
        System.out.println("  R4 requires definite colliders to fire; listDiscriminatingPaths(...,true) admits");
        System.out.println("  paths with circles on the spine. If NON-definite dominates, that is the gap:");
        System.out.println("  R4 never recognizes the path, the v-end stays a circle, and Lemma A forces it.");
        System.out.println("  Fully-definite-spine phantoms are NOT explained by this -- they are dumped for");
        System.out.println("  inspection, since for those the gap (and Lemma B) lies elsewhere.");

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
        } else {
            System.out.println("COUNTEREXAMPLE(S) FOUND -- Conjecture 1 is FALSE at this size. See dump for witnesses.");
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
        long[] phantomLenHist = new long[Math.max(2, OBS + 2)];
        List<String> witnesses = new ArrayList<>();
        long suppressed;

        void addWitness(String s) {
            if (witnesses.size() < WITNESS_CAP) witnesses.add(s); else suppressed++;
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

    /** True iff every colliderPath vertex of the DDP is a DEFINITE collider in h1
     *  w.r.t. its path neighbours. R4 requires definite colliders to fire; the
     *  detector's listDiscriminatingPaths(...,true) admits looser paths, so a
     *  non-definite spine is the candidate reason R4 never orients the phantom. */
    private static boolean allSpineCollidersDefinite(Graph h1, DiscriminatingPath dd) {
        List<Node> path = new ArrayList<>();
        path.add(dd.getX());
        path.addAll(dd.getColliderPath());
        path.add(dd.getV());
        for (int j = 1; j <= dd.getColliderPath().size(); j++) {
            if (!h1.isDefCollider(path.get(j - 1), path.get(j), path.get(j + 1))) return false;
        }
        return true;
    }

    private static String prong(String reason) {
        if (reason == null) return "other";
        String r = reason.toLowerCase();
        if (r.contains("cannot recover") || r.contains("between a mag and a pag")) return "roundtrip";
        if (r.contains("not maximal") || r.contains("inducing path")) return "maximality";
        if (r.contains("acyclic") || r.contains("cyclic")) return "acyclic";
        return "other";
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
