    /// ////////////////////////////////////////////////////////////////////////////
    // PhantomKernelSampler.java                                                   //
    //                                                                            //
    // Random-sampling variant of PhantomKernelEnumerator. It keeps ALL of the     //
    // enumerator's instrumentation (R4-abstention counts, kernel cross-tab,       //
    // Lemma-B sepset probe, firing-gap probe, witness dumps) but iterates over    //
    // graphs EXACTLY the way PhantomCounterexampleHarness does: seeded            //
    // RandomGraph.randomGraph models, the last NUM_LATENT nodes marked latent,    //
    // STATES_PER_MODEL shuffled spurious-edge selections of SPURIOUS_PER_STATE     //
    // edges each. The point is to surface DIVERSE, deeper-spine examples to        //
    // analyse, instead of the exhaustive enumerator re-hitting the same handful    //
    // of length-1 shapes through millions of equivalent masks.                    //
    //                                                                            //
    // Serial by construction: the seeded RNG (RandomUtil singleton) is global and //
    // the seeds are the reproducibility contract, so there is no safe parallelism  //
    // here -- unlike the exhaustive enumerator, whose mask loop was thread-safe.   //
    //                                                                            //
    // NOT a proof. A null counterexample count here is sampling evidence, not the  //
    // "exhaustive up to size" guarantee the enumerator gives. Use this to find and //
    // inspect witnesses (especially length>=2 phantoms); use the enumerator to     //
    // certify a size.                                                              //
    //                                                                            //
    // Firing-gap update: the enumerator established that phantom spines are FULLY  //
    // DEFINITE colliders yet R4 still does not fire, refuting the definite-collider //
    // hypothesis. The witness graphs showed why: the (last-collider)->v edge       //
    // carries a CIRCLE at v (w <-o v), not the arrowhead R4 needs to pose the       //
    // discriminated-collider question. This sampler probes that endpoint directly  //
    // (wvEndpointAtV) and reports its census as the candidate gap, keeping the      //
    // now-refuted definite-collider count alongside as a control.                  //
    //                                                                            //
    // args: [0]=numModels (default 2000) [1]=dump path [2]=minReportColliderLen    //
    //       (default 2: dump only deep-spine phantoms + all counterexamples/anoms) //
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
     * Seeded random-sampling search that runs the PhantomKernelEnumerator probes on
     * PhantomCounterexampleHarness-style models, to surface diverse witnesses (in
     * particular deeper-spine phantoms) for analysis.
     *
     * @author josephramsey (harness scaffolding by Claude)
     */
    public final class PhantomKernelSampler {

        /**
         * Default constructor.
         */
        public PhantomKernelSampler() {

        }

        // ── Configuration (args[0]=numModels, args[1]=dump, args[2]=minReportColliderLen) ──
        private static int NUM_MODELS = 2000;
        private static final int     NUM_NODES          = 12;
        private static final int     NUM_LATENT         = 4;
        private static final int     NUM_EDGES          = 18;
        private static final int     SPURIOUS_PER_STATE = 4;
        private static final int     STATES_PER_MODEL   = 5;
        private static final int     MAX_COND           = 3;
        private static final int     MAX_LEN            = -1;
        private static final int     DEPTH              = -1;
        private static final int     RECURSIVE_DEPTH    = -1;
        private static final long    TIMEOUT            = -1L;
        private static final boolean EXCLUDE_SELECTION_BIAS = false;
        private static final int     OFFSET             = 100000;
        private static final int     OBS                = NUM_NODES - NUM_LATENT;
        private static final int     WITNESS_CAP        = 5000;

        /** Dump only non-genuine-illegal cases whose deepest phantom reaches this
         *  collider-length. Counterexamples (legal non-genuine) and committed-v-end
         *  round-trip anomalies are ALWAYS dumped regardless. Set 1 to dump every
         *  phantom; 2 (default) focuses the dump on the deep-spine cases we have not
         *  already seen a thousand times. Counters/census are unaffected. */
        private static int MIN_REPORT_COLLIDER_LEN = 2;

        /**
         * Runs the sampler.
         * @param args arguments.
         */
        public static void main(String[] args) {
            if (args.length > 0) NUM_MODELS = Integer.parseInt(args[0]);
            String dumpPath = (args.length > 1) ? args[1] : "phantom_kernel_sampled.log";
            if (args.length > 2) MIN_REPORT_COLLIDER_LEN = Integer.parseInt(args[2]);

            System.err.printf("SAMPLER CONFIG: nodes=%d latent=%d observed=%d edges=%d spurious=%d "
                            + "states=%d models=%d offset=%d minReportColliderLen=%d%n",
                    NUM_NODES, NUM_LATENT, OBS, NUM_EDGES, SPURIOUS_PER_STATE,
                    STATES_PER_MODEL, NUM_MODELS, OFFSET, MIN_REPORT_COLLIDER_LEN);

            Result r = new Result();
            PrintWriter dump = openDump(dumpPath);

            try {
                for (int seed = OFFSET; seed <= NUM_MODELS + OFFSET; seed++) {
                    try {
                        sampleModel(r, seed, dump);
                    } catch (Exception ex) {
                        r.skipped++;
                        System.err.println("seed " + seed + " skipped: " + ex.getMessage());
                    }
                    if (seed % 200 == 0) {
                        System.err.printf("…%d models | gated=%d h1=%d pos=%d illegalNG=%d COUNTEREX=%d "
                                        + "phantomMax=%d abst=%d%n",
                                seed - OFFSET, r.gated, r.h1States, r.positives, r.illegalNG,
                                r.counterexamples, r.maxPhantomColliderLen, r.totalAbstentions);
                        dump.flush();
                    }
                }
            } finally {
                for (String w : r.witnesses) dump.println(w);
                if (r.suppressed > 0) {
                    dump.println("==== (" + r.suppressed + " further witnesses suppressed; raise WITNESS_CAP) ====");
                }
                dump.flush();
                dump.close();
            }

            printSummary(r, dumpPath);
        }

        // ── One seeded model: build truePag, gate genuine-legal H0 states, classify H1. ──
        private static void sampleModel(Result r, int seed, PrintWriter dump) throws InterruptedException {
            RandomUtil.getInstance().setSeed(seed);

            Graph dag = RandomGraph.randomGraph(NUM_NODES, 0, NUM_EDGES, 100, 100, 100, false);
            List<Node> allNodes = dag.getNodes();
            for (int i = NUM_NODES - NUM_LATENT; i < NUM_NODES; i++) {
                allNodes.get(i).setNodeType(NodeType.LATENT);
            }

            Knowledge knowledge = new Knowledge();
            Graph truePag = GraphTransforms.dagToPag(dag, knowledge, EXCLUDE_SELECTION_BIAS, RECURSIVE_DEPTH);
            IndependenceTest oracle = new MsepTest(dag);

            List<Node> obs = truePag.getNodes();
            Set<Triple> initialColliders = noteInitialColliders(obs, truePag);

            List<int[]> nonAdj = nonAdjacentPairs(truePag, obs);
            if (nonAdj.size() < SPURIOUS_PER_STATE) return;

            for (int attempt = 0; attempt < STATES_PER_MODEL; attempt++) {
                Collections.shuffle(nonAdj, new Random(seed * 1000L + attempt));

                Graph h0 = new EdgeListGraph(truePag);
                SepsetMap sepsets = new SepsetMap();
                List<Edge> spurious = new ArrayList<>();
                for (int[] p : nonAdj) {
                    if (spurious.size() >= SPURIOUS_PER_STATE) break;
                    Node a = obs.get(p[0]), b = obs.get(p[1]);
                    Set<Node> sep = oracleSepset(oracle, a, b, obs, MAX_COND);
                    if (sep == null) continue;
                    h0.addEdge(new Edge(a, b, Endpoint.CIRCLE, Endpoint.CIRCLE));
                    sepsets.set(a, b, sep);
                    spurious.add(new Edge(a, b, Endpoint.CIRCLE, Endpoint.CIRCLE));
                }
                if (spurious.size() < SPURIOUS_PER_STATE) continue;

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
                    r.h1MaxColliderAll = Math.max(r.h1MaxColliderAll, maxColliderLen(ddp1));
                    List<DiscriminatingPath> phantoms = allPhantoms(ddp1, truePag);

                    PagLegalityCheck.LegalPagRet ret = PagLegalityCheck.isLegalPag(h1, new HashSet<>());
                    boolean legal = ret.isLegalPag();

                    if (phantoms.isEmpty()) { r.positives++; continue; }

                    // phantom-length census over EVERY phantom DDP
                    for (DiscriminatingPath dd : phantoms) {
                        int L = dd.getColliderPath().size();
                        if (L >= 0 && L < r.phantomLenHist.length) r.phantomLenHist[L]++;
                        if (L > r.maxPhantomColliderLen) r.maxPhantomColliderLen = L;
                    }

                    // report the LONGEST phantom, not an arbitrary first one
                    DiscriminatingPath worst = phantoms.get(0);
                    for (DiscriminatingPath dd : phantoms) {
                        if (dd.getColliderPath().size() > worst.getColliderPath().size()) worst = dd;
                    }
                    int maxLen = worst.getColliderPath().size();

                    // Lemma-B probe: recorded sepset coverage of the DDP endpoints
                    Node px = worst.getX(), py = worst.getY(), pv = worst.getV();
                    Set<Node> sxy = null;
                    try { sxy = sepsets.get(px, py); } catch (Exception ignore) { }
                    if (sxy == null) r.phantomXYNoSepset++;
                    else { r.phantomXYHasSepset++; if (sxy.contains(pv)) r.phantomVInSepset++; }

                    boolean committed = vEndCommitted(h1, worst);
                    boolean spineDefinite = allSpineCollidersDefinite(h1, worst);
                    if (spineDefinite) r.phantomSpineDefinite++; else r.phantomSpineNonDefinite++;

                    // The actual firing-gap probe: endpoint AT v on the (last-collider)->v edge.
                    Endpoint wvAtV = wvEndpointAtV(h1, worst);
                    if (wvAtV == Endpoint.ARROW)      r.wvArrowAtV++;
                    else if (wvAtV == Endpoint.CIRCLE) r.wvCircleAtV++;
                    else if (wvAtV == Endpoint.TAIL)  r.wvTailAtV++;
                    else                               r.wvOtherAtV++;

                    // decisive cross-tab: non-genuine H1 by {R4 abstained} x {legal}
                    if (legal) { if (abstained) r.nonGenAbstainLegal++; else r.nonGenNoAbstainLegal++; }
                    else       { if (abstained) r.nonGenAbstainIllegal++; else r.nonGenNoAbstainIllegal++; }

                    if (legal) {
                        r.counterexamples++;
                        if (committed) r.committedLegal++; else r.circleLegal++;
                        r.addWitness(formatCase(
                                (committed ? "***** CONSISTENT LIE: legal, non-genuine, COMMITTED v-end *****"
                                        : "***** COUNTEREXAMPLE: legal, non-genuine, circle v-end *****")
                                        + (abstained ? "  [R4-ABSTAINED]" : ""),
                                seed, attempt, spurious, e, worst, "(legal)", committed, wvAtV, spineDefinite, sxy, h1));
                        System.out.println("COUNTEREXAMPLE seed=" + seed + " attempt=" + attempt
                                + " del=" + e + " committedVEnd=" + committed + " abstained=" + abstained
                                + " wvAtV=" + wvAtV + " len=" + maxLen + " -> " + worst);
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
                                        seed, attempt, spurious, e, worst, reason, true, wvAtV, spineDefinite, sxy, h1));
                            }
                        } else {
                            r.circleIllegal++;
                            boolean offHypothesis = (wvAtV != Endpoint.CIRCLE); // arrow/tail/null at v
                            if (maxLen >= MIN_REPORT_COLLIDER_LEN || offHypothesis) {
                                String tag = offHypothesis
                                        ? "FALSIFIER?: non-genuine, circle v-end, but w-v endpoint at v = " + wvAtV
                                          + " (R4 had its precondition yet did not fire) -- len " + maxLen
                                        : "NON-GENUINE, illegal, circle v-end (deep-spine phantom, len " + maxLen + ")";
                                r.addWitness(formatCase(tag, seed, attempt, spurious, e, worst,
                                        reason, false, wvAtV, spineDefinite, sxy, h1));
                            }
                        }
                    }
                }
            }
        }

        private static void printSummary(Result t, String dumpPath) {
            System.out.println("\n==== SAMPLER SUMMARY ====");
            System.out.printf("measured / latent / total        : %d / %d / %d%n", OBS, NUM_LATENT, NUM_NODES);
            System.out.printf("models scanned                   : %d%n", NUM_MODELS);
            System.out.printf("genuine legal H0 gated           : %d%n", t.gated);
            System.out.printf("H1 states classified             : %d%n", t.h1States);
            System.out.printf("H1 genuine (conj. holds)         : %d%n", t.positives);
            System.out.printf("H1 non-genuine, illegal          : %d%n", t.illegalNG);
            System.out.printf("H1 non-genuine, LEGAL            : %d  <-- CONJECTURE 1 COUNTEREXAMPLES%n", t.counterexamples);
            System.out.printf("models skipped (exceptions)      : %d%n", t.skipped);

            System.out.println("\n==== R4 ABSTENTIONS ====");
            System.out.printf("total R4 abstentions             : %d%n", t.totalAbstentions);
            System.out.printf("gated H0 needing >=1 abstention  : %d / %d%n", t.gatedWithAbstention, t.gated);
            System.out.printf("H1 with >=1 abstention           : %d / %d%n", t.h1WithAbstention, t.h1States);
            System.out.println("non-genuine H1 by {R4 abstained} x {legal}:");
            System.out.printf("  %-18s %12s %12s%n", "", "illegal", "LEGAL");
            System.out.printf("  %-18s %12d %12d%n", "abstained",     t.nonGenAbstainIllegal,   t.nonGenAbstainLegal);
            System.out.printf("  %-18s %12d %12d%n", "no abstention",  t.nonGenNoAbstainIllegal, t.nonGenNoAbstainLegal);

            System.out.println("\n==== KERNEL CROSS-TAB (non-genuine H1: v-end committed? x legal?) ====");
            System.out.printf("  %-22s %12s %12s%n", "", "illegal", "LEGAL");
            System.out.printf("  %-22s %12d %12d%n", "v-end = circle",    t.circleIllegal,    t.circleLegal);
            System.out.printf("  %-22s %12d %12d%n", "v-end = committed", t.committedIllegal, t.committedLegal);
            System.out.printf("  committed-illegal by prong -> roundtrip=%d maximality=%d acyclic=%d other=%d%n",
                    t.committedIllegalRoundtrip, t.committedIllegalMaximality,
                    t.committedIllegalAcyclic, t.committedIllegalOther);

            System.out.println("\n==== PHANTOM SPINE CENSUS ====");
            System.out.printf("max collider-path length, ALL DDPs in H1 : %d   (vacuity guard)%n", t.h1MaxColliderAll);
            System.out.printf("max collider-path length, PHANTOM DDPs   : %d%n", t.maxPhantomColliderLen);
            System.out.println("phantom collider-length histogram:");
            boolean any = false;
            for (int L = 0; L < t.phantomLenHist.length; L++) {
                if (t.phantomLenHist[L] > 0) { System.out.printf("  length %d : %d%n", L, t.phantomLenHist[L]); any = true; }
            }
            if (!any) System.out.println("  (no phantom DDPs observed)");
            System.out.println("  If ALL-DDP max >= 2 but PHANTOM max == 1, phantoms are genuinely minimal here.");
            System.out.println("  If ALL-DDP max == 1 too, the sample is too sparse to host long DDPs (raise edges).");

            System.out.println("\n==== LEMMA-B PROBE (recorded-sepset coverage) ====");
            long ng = t.phantomXYHasSepset + t.phantomXYNoSepset;
            System.out.printf("non-genuine H1 with recorded sepset for (x,y) : %d / %d%n", t.phantomXYHasSepset, ng);
            System.out.printf("  ...of those, v in the recorded sepset       : %d%n", t.phantomVInSepset);
            System.out.printf("non-genuine H1 with NO recorded sepset (x,y)  : %d / %d%n", t.phantomXYNoSepset, ng);

            System.out.println("\n==== R4 FIRING-GAP PROBE ====");
            long pg = t.phantomSpineDefinite + t.phantomSpineNonDefinite;
            System.out.printf("spine colliders all definite : %d / %d   (control: definite yet R4 silent)%n",
                    t.phantomSpineDefinite, pg);
            System.out.println("(last-collider)->v edge, endpoint AT v -- the candidate gap:");
            System.out.printf("  circle : %d / %d   <- R4's arrowhead precondition unmet; this is the gap%n", t.wvCircleAtV, pg);
            System.out.printf("  arrow  : %d / %d   <- R4 SHOULD have fired here; inspect any such case%n", t.wvArrowAtV, pg);
            System.out.printf("  tail   : %d / %d%n", t.wvTailAtV, pg);
            System.out.printf("  other  : %d / %d   (no w-v edge / null)%n", t.wvOtherAtV, pg);
            System.out.println("  Hypothesis: phantom ==> circle at v on the w-v edge ==> R4 never poses the");
            System.out.println("  discriminated-collider question ==> v-y circle survives ==> Lemma A forces it.");
            System.out.println("  A nonzero 'arrow' count is the falsifier: a phantom R4 had the precondition to");
            System.out.println("  orient yet did not -- that case would break the under-commit story, so it is dumped.");

            System.out.println();
            if (t.counterexamples == 0) {
                System.out.println("No legal non-genuine PAG found in this sample. Conjecture 1 survives (sampling "
                        + "evidence, NOT an exhaustive proof -- use PhantomKernelEnumerator to certify a size).");
            } else {
                System.out.println("COUNTEREXAMPLE(S) FOUND -- see dump for legal non-genuine witnesses.");
            }
            System.out.println("\nwitnesses / anomalies written to: " + dumpPath);
        }

        // ── Accumulator (single-threaded; no merge needed) ─────────────────────────
        static final class Result {
            long gated, h1States, positives, illegalNG, counterexamples, skipped;
            long circleIllegal, circleLegal, committedIllegal, committedLegal;
            long committedIllegalRoundtrip, committedIllegalMaximality, committedIllegalAcyclic, committedIllegalOther;
            long phantomXYHasSepset, phantomXYNoSepset, phantomVInSepset;
            long maxPhantomColliderLen, h1MaxColliderAll;
            long totalAbstentions, gatedWithAbstention, h1WithAbstention;
            long nonGenAbstainIllegal, nonGenAbstainLegal, nonGenNoAbstainIllegal, nonGenNoAbstainLegal;
            long phantomSpineDefinite, phantomSpineNonDefinite;
            long wvArrowAtV, wvCircleAtV, wvTailAtV, wvOtherAtV;
            long[] phantomLenHist = new long[Math.max(2, OBS + 2)];
            List<String> witnesses = new ArrayList<>();
            long suppressed;

            void addWitness(String s) {
                if (witnesses.size() < WITNESS_CAP) witnesses.add(s); else suppressed++;
            }
        }

        // ── Probes ─────────────────────────────────────────────────────────────────
        private static boolean vEndCommitted(Graph h1, DiscriminatingPath dd) {
            Node v = dd.getV(), y = dd.getY();
            if (h1.getEdge(v, y) == null) return false;
            Endpoint atV = h1.getEndpoint(y, v);
            return atV == Endpoint.TAIL || atV == Endpoint.ARROW;
        }

        /** Endpoint AT v on the (last collider in the spine)->v edge. R4's
         *  discriminating-path precondition needs an arrowhead into v here (w *-> v);
         *  the witness graphs show a circle instead (w <-o v), which is why R4 never
         *  fires. w is the last colliderPath vertex (falls back to x if empty). */
        private static Endpoint wvEndpointAtV(Graph h1, DiscriminatingPath dd) {
            List<Node> cp = dd.getColliderPath();
            // colliderPath is ordered from the v-side: cp[0] is the collider adjacent
            // to v (equals dd.getW()), cp[last] is adjacent to x. The w-v edge is the
            // one whose endpoint at v R4 needs to be an arrowhead to fire.
            Node w = cp.isEmpty() ? dd.getX() : cp.get(0);
            Node v = dd.getV();
            if (h1.getEdge(w, v) == null) return null;
            return h1.getEndpoint(w, v); // endpoint at v on the w-v edge
        }

        /** True iff every colliderPath vertex is a DEFINITE collider in h1 w.r.t. its
         *  path neighbours. Kept as a control: established to be uniformly true on
         *  phantoms, so definiteness is NOT the firing gap. */
        private static boolean allSpineCollidersDefinite(Graph h1, DiscriminatingPath dd) {
            // colliderPath is ordered v->x, so the discriminating path in v-first order
            // is v, cp[0], cp[1], ..., x. Check each colliderPath vertex is a definite
            // collider w.r.t. its two path neighbours in that order.
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

        private static int maxColliderLen(Collection<DiscriminatingPath> ddps) {
            int m = 0;
            for (DiscriminatingPath dd : ddps) m = Math.max(m, dd.getColliderPath().size());
            return m;
        }

        private static String prong(String reason) {
            if (reason == null) return "other";
            String r = reason.toLowerCase();
            if (r.contains("cannot recover") || r.contains("between a mag and a pag")) return "roundtrip";
            if (r.contains("not maximal") || r.contains("inducing path")) return "maximality";
            if (r.contains("acyclic") || r.contains("cyclic")) return "acyclic";
            return "other";
        }

        // ── Dump ─────────────────────────────────────────────────────────────────
        private static PrintWriter openDump(String path) {
            try {
                return new PrintWriter(new BufferedWriter(new FileWriter(path, false)));
            } catch (IOException io) {
                System.err.println("Could not open dump file " + path + "; falling back to stderr.");
                return new PrintWriter(System.err, true);
            }
        }

        private static String formatCase(String tag, int seed, int attempt, List<Edge> spurious,
                                         Edge deleted, DiscriminatingPath phantom, String reason,
                                         boolean committedVEnd, Endpoint wvAtV, boolean spineDefinite,
                                         Set<Node> sepsetXY, Graph h1) {
            StringBuilder sb = new StringBuilder();
            sb.append("==== ").append(tag).append(" ====\n");
            sb.append("  seed/attempt    : ").append(seed).append(" / ").append(attempt).append('\n');
            sb.append("  spurious added  : ").append(spurious).append('\n');
            sb.append("  deleted edge    : ").append(deleted).append('\n');
            sb.append("  phantom DDP     : ").append(phantom).append('\n');
            sb.append("  collider length : ").append(phantom.getColliderPath().size()).append('\n');
            sb.append("  v-end committed : ").append(committedVEnd).append('\n');
            sb.append("  w-v endpt at v  : ").append(wvAtV).append("   (arrow => R4 should have fired)\n");
            sb.append("  spine definite  : ").append(spineDefinite).append('\n');
            sb.append("  sepset(x,y)     : ").append(sepsetXY == null ? "(none recorded)" : sepsetXY).append('\n');
            sb.append("  legality reason : ").append(reason).append('\n');
            sb.append("  H1:\n").append(h1).append('\n');
            return sb.toString();
        }

        // ── Helpers reused verbatim from the enumerator/harness ────────────────────
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
                Node x = arr.get(0), y = arr.get(1);
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
                        Node x = adj.get(i), y = adj.get(j);
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
