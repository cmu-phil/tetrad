/// ////////////////////////////////////////////////////////////////////////////
// PhantomFcitReachabilityHarness.java  (parallel)                             //
//                                                                            //
// Reachability test for the NOSTALL deadlocks found by PhantomKernelEnumerator.//
// The enumerator shows that CONSTRUCTED delete-and-reorient states can be      //
// cold-deadlocks (genuine+legal, dense, with no legal single-edge escape).     //
// This harness asks the only remaining question: does the REAL FCIT algorithm  //
// ever WALK INTO one? It enumerates the identical population (every DAG over a  //
// fixed topo order x every latent placement), runs actual oracle-FCIT to        //
// completion, and compares its output to the true PAG.                         //
//                                                                            //
//   * output == true PAG                  -> FCIT recovered the truth          //
//   * DENSER (extra adjacencies only)     -> REACHABLE STALL: FCIT could not   //
//       peel an edge -> output is valid (legal+genuine by Conjecture 1) but    //
//       non-minimal. This is the completeness defect the deadlocks predict.    //
//   * SPARSER (missing true adjacencies)  -> different failure (should be 0     //
//       under an oracle; a true edge was dropped).                             //
//   * same skeleton, different marks      -> an orientation discrepancy.       //
//                                                                            //
// A zero DENSER/MIXED count is the decisive negative result: the cold          //
// deadlocks are OFF FCIT's actual trajectory and completeness holds in         //
// practice at this size. A nonzero count gives concrete reachable witnesses.   //
//                                                                            //
// args: [0]=N (default 6) [1]=numLatent (default 1)                            //
//       [2]=dump path (default fcit_reachability_witnesses.log)                //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.SublistGenerator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.LongStream;

/**
 * Runs the real FCIT on every small latent DAG (oracle independence) and compares
 * the result to the true PAG, to test whether the constructed cold deadlocks are
 * actually reachable by FCIT's own edge-removal trajectory.
 *
 * @author josephramsey (harness scaffolding by Claude)
 */
public final class PhantomFcitReachabilityHarness {

    /**
     * Constructor.
     */
    public PhantomFcitReachabilityHarness() {}

    private static int N          = 6;
    private static int NUM_LATENT = 1;

    // Mirror the enumerator's reorient knobs so FCIT runs under the same config.
    private static final int     MAX_LEN         = -1;
    private static final int     DEPTH           = -1;
    private static final int     RECURSIVE_DEPTH = -1;
    private static final long    TIMEOUT         = -1L;
    private static final int     MAX_COND        = 3;   // sepset search cap for the deadlock check
    private static final boolean EXCLUDE_SELECTION_BIAS = false;

    private static int OBS, P;
    private static int[][] PAIR;
    private static long TOTAL_DAGS;
    private static final int WITNESS_CAP = 5000;

    /**
     * Main.
     * @param args args.
     */
    public static void main(String[] args) {
        if (args.length > 0) N          = Integer.parseInt(args[0]);
        if (args.length > 1) NUM_LATENT = Integer.parseInt(args[1]);
        String dumpPath = (args.length > 2) ? args[2] : "fcit_reachability_witnesses.log";

        OBS = N - NUM_LATENT;
        P   = N * (N - 1) / 2;
        TOTAL_DAGS = 1L << P;
        PAIR = new int[P][2];
        for (int idx = 0, i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++, idx++) { PAIR[idx][0] = i; PAIR[idx][1] = j; }
        }

        int threads = Runtime.getRuntime().availableProcessors();
        System.err.printf("FCIT REACHABILITY CONFIG: N=%d latent=%d observed=%d | dags=2^%d=%d | threads~%d%n",
                N, NUM_LATENT, OBS, P, TOTAL_DAGS, threads);

        Result total = LongStream.range(0, TOTAL_DAGS)
                .parallel()
                .collect(Result::new, PhantomFcitReachabilityHarness::accumulate, Result::merge);

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
                MsepTest oracle = new MsepTest(dag);
                GraphScore score = new GraphScore(dag);

                // Run the REAL FCIT to completion (oracle test + oracle score).
                FcitResult fr = runFcit(oracle, score, knowledge);
                Graph out = fr.out;

                // Defensive: if FCIT searched a different node set (e.g. the oracle pulled in
                // latents), the comparison below is meaningless -- flag and skip classification.
                if (out.getNumNodes() != truePag.getNumNodes()) {
                    r.nodeSetMismatch++;
                    r.addWitness(formatNodeMismatch(mask, latSet, truePag, out));
                    continue;
                }

                Cmp c = compare(truePag, out);
                switch (c.kind) {
                    case EXACT:           r.exact++;             break;
                    case SAME_SKEL_MARKS: r.sameSkelDiffMarks++; break;
                    case DENSER:          r.denser++;            break;
                    case SPARSER:         r.sparser++;           break;
                    default:              r.mixed++;             break;
                }

                // The reachable-stall signature is a denser-than-truth skeleton.
                if (c.kind == CmpKind.DENSER || c.kind == CmpKind.MIXED) {
                    boolean legal = PagLegalityCheck.isLegalPag(out, new HashSet<>()).isLegalPag();
                    if (c.kind == CmpKind.DENSER) {
                        if (legal) r.denserLegal++; else r.denserIllegal++;
                    }
                    // Loop-closing check: rebuild the stuck skeleton as the enumerator's
                    // constructed H0 (truePag + extra edges + their oracle sepsets) and run
                    // the enumerator's exact cold-reorient deadlock test on it.
                    DeadlockInfo di = confirmDeadlock(truePag, oracle, knowledge, c.extraPairs, fr.sepsets);
                    if (di.confirmed) r.confirmedDeadlock++; else r.notConfirmedDeadlock++;
                    if (!di.gated) r.h0NotGated++;
                    if (di.k >= 0 && di.k < r.kHist.length) r.kHist[di.k]++;
                    for (int i = 0; i < 4; i++) { r.dlGenProng[i] += di.genProng[i]; r.dlNgProng[i] += di.ngProng[i]; }
                    r.addWitness(formatMismatch(mask, latSet, c, truePag, out, legal) + formatDeadlockInfo(di));
                } else if (c.kind == CmpKind.SPARSER || c.kind == CmpKind.SAME_SKEL_MARKS) {
                    r.addWitness(formatMismatch(mask, latSet, c, truePag, out, false));
                }
            } catch (Exception ex) {
                r.skipped++;
                System.err.println("model mask=" + mask + " lat=" + Arrays.toString(latChoice)
                        + " skipped: " + ex.getMessage());
            }
        }
    }

    // ========================================================================
    // THE ONE PLACE THAT ENCODES THE Fcit API -- verify against Fcit.java
    // (and against however FcitBenchmarkHarness builds the oracle + runs FCIT,
    // which already does this correctly). Adjust the constructor / setters /
    // search() call and the oracle's observed-variable scope to match exactly.
    // ========================================================================
    private static final class FcitResult {
        final Graph out;
        final SepsetMap sepsets;
        FcitResult(Graph out, SepsetMap sepsets) { this.out = out; this.sepsets = sepsets; }
    }

    private static FcitResult runFcit(MsepTest test, GraphScore score, Knowledge knowledge) throws InterruptedException {
        Fcit fcit = new Fcit(test, score);
        fcit.setKnowledge(knowledge);
        fcit.setCompleteRuleSetUsed(true);
        fcit.setMaxDiscriminatingPathLength(MAX_LEN);
        fcit.setDepth(DEPTH);
        fcit.setStartWith(Fcit.START_WITH.COMPLETE_GRAPH);
        fcit.setVerbose(false);
        // >>> KEEP your start-from-complete-PAG setter here (the option you ran with) <<<
        //     e.g. fcit.setStartWith(Fcit.START_WITH.COMPLETE_GRAPH);
        // If Fcit exposes these, set them too:
        // fcit.setRecursiveDepth(RECURSIVE_DEPTH);
        // fcit.setTimeout(TIMEOUT);
        Graph out = fcit.search();
        return new FcitResult(out, fcit.getSepsetMap());   // getSepsetMap(): the method you're adding
    }

    // ── Comparison of FCIT output vs true PAG, by node name over observed pairs ──
    private enum CmpKind { EXACT, SAME_SKEL_MARKS, DENSER, SPARSER, MIXED }

    private static final class Cmp {
        CmpKind kind;
        int extraAdj, missingAdj, markDiffs;
        final List<String> extras = new ArrayList<>(), missing = new ArrayList<>(), marks = new ArrayList<>();
        final List<Node[]> extraPairs = new ArrayList<>();   // truth-space node pairs absent from truth
    }

    private static Cmp compare(Graph truth, Graph out) {
        Cmp c = new Cmp();
        Map<String, Node> outByName = new HashMap<>();
        for (Node n : out.getNodes()) outByName.put(n.getName(), n);

        List<Node> tn = truth.getNodes();
        for (int i = 0; i < tn.size(); i++) {
            for (int j = i + 1; j < tn.size(); j++) {
                Node a = tn.get(i), b = tn.get(j);
                Node oa = outByName.get(a.getName()), ob = outByName.get(b.getName());
                boolean tAdj = truth.isAdjacentTo(a, b);
                boolean oAdj = oa != null && ob != null && out.isAdjacentTo(oa, ob);

                if (tAdj && !oAdj) {
                    c.missingAdj++; c.missing.add(a.getName() + "---" + b.getName());
                } else if (!tAdj && oAdj) {
                    c.extraAdj++; c.extras.add(a.getName() + "---" + b.getName());
                    c.extraPairs.add(new Node[]{a, b});   // truth-space nodes, oracle-compatible
                } else if (tAdj && oAdj) {
                    Endpoint tAB = truth.getEndpoint(a, b), tBA = truth.getEndpoint(b, a);
                    Endpoint oAB = out.getEndpoint(oa, ob), oBA = out.getEndpoint(ob, oa);
                    if (tAB != oAB || tBA != oBA) {
                        c.markDiffs++;
                        c.marks.add(a.getName() + "(" + tAB + "/" + oAB + ")"
                                + b.getName() + "(" + tBA + "/" + oBA + ")");
                    }
                }
            }
        }

        if (c.extraAdj == 0 && c.missingAdj == 0) {
            c.kind = (c.markDiffs == 0) ? CmpKind.EXACT : CmpKind.SAME_SKEL_MARKS;
        } else if (c.extraAdj > 0 && c.missingAdj == 0) {
            c.kind = CmpKind.DENSER;
        } else if (c.extraAdj == 0 && c.missingAdj > 0) {
            c.kind = CmpKind.SPARSER;
        } else {
            c.kind = CmpKind.MIXED;
        }
        return c;
    }

    private static String formatMismatch(long mask, Set<Integer> latSet, Cmp c,
                                         Graph truth, Graph out, boolean legal) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== FCIT OUTPUT != TRUE PAG  (kind=").append(c.kind).append(") ====\n");
        sb.append("  dag mask     : ").append(mask).append('\n');
        sb.append("  latent set   : ").append(latSet).append('\n');
        sb.append("  extra adj    : ").append(c.extraAdj).append("  ").append(c.extras).append('\n');
        sb.append("  missing adj  : ").append(c.missingAdj).append("  ").append(c.missing).append('\n');
        sb.append("  mark diffs   : ").append(c.markDiffs).append("  ").append(c.marks).append('\n');
        if (c.kind == CmpKind.DENSER) {
            sb.append("  output legal : ").append(legal)
                    .append(legal ? "  (denser+legal => reachable stall: valid but non-minimal)" : "  (denser+ILLEGAL => unexpected; FCIT emitted an illegal PAG)")
                    .append('\n');
        }
        sb.append("  TRUE PAG:\n").append(truth).append('\n');
        sb.append("  FCIT OUT:\n").append(out).append('\n');
        return sb.toString();
    }

    private static String formatNodeMismatch(long mask, Set<Integer> latSet, Graph truth, Graph out) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== NODE-SET MISMATCH (oracle scope? FCIT searched a different variable set) ====\n");
        sb.append("  dag mask        : ").append(mask).append('\n');
        sb.append("  latent set      : ").append(latSet).append('\n');
        sb.append("  true PAG nodes  : ").append(truth.getNumNodes()).append("  ").append(truth.getNodes()).append('\n');
        sb.append("  FCIT out nodes  : ").append(out.getNumNodes()).append("  ").append(out.getNodes()).append('\n');
        return sb.toString();
    }

    private static void printSummary(Result t, String dumpPath) {
        System.out.println("\n==== FCIT REACHABILITY SUMMARY ====");
        System.out.printf("N=%d latent=%d observed=%d%n", N, NUM_LATENT, OBS);
        System.out.printf("DAGs enumerated (fixed order)   : %d%n", t.dagsScanned);
        System.out.printf("models (DAG x latent placement) : %d%n", t.modelsScanned);
        System.out.printf("models skipped (throws)         : %d%n", t.skipped);
        System.out.printf("node-set mismatches (see note)  : %d%n", t.nodeSetMismatch);
        System.out.println();
        System.out.printf("FCIT output == true PAG (exact)         : %d%n", t.exact);
        System.out.printf("same skeleton, different marks          : %d%n", t.sameSkelDiffMarks);
        System.out.printf("DENSER than truth (extra edges only)    : %d   <-- REACHABLE STALL signature%n", t.denser);
        System.out.printf("   ...of those, output a legal PAG      : %d%n", t.denserLegal);
        System.out.printf("   ...of those, output ILLEGAL          : %d  (should be 0; FCIT reverts illegal)%n", t.denserIllegal);
        System.out.printf("SPARSER than truth (missing edges)      : %d  (should be 0 under an oracle)%n", t.sparser);
        System.out.printf("MIXED (both extra and missing edges)    : %d%n", t.mixed);
        System.out.println();

        if (t.nodeSetMismatch > 0) {
            System.out.println("WARNING: node-set mismatches occurred -- the oracle handed FCIT a different");
            System.out.println("variable set than the true PAG's observed nodes (likely latents included).");
            System.out.println("Fix the oracle scope in runFcit before trusting the counts above.");
            System.out.println();
        }

        long denserAny = t.denser + t.mixed;
        if (denserAny == 0 && t.nodeSetMismatch == 0) {
            System.out.println("NO reachable stall: oracle-FCIT never returns a graph denser than the true PAG.");
            System.out.println("The cold deadlocks found by the enumerator are therefore OFF FCIT's actual");
            System.out.println("trajectory -- completeness holds in practice at this size despite NOSTALL");
            System.out.println("failing for the constructed delete-and-reorient states.");
        } else if (denserAny > 0) {
            System.out.printf("REACHABLE STALLS: %d models where oracle-FCIT returned extra edges.%n", denserAny);
            System.out.println("FCIT's removal order DOES land on cycle-locked states. By Conjecture 1 these");
            System.out.println("outputs are still legal+genuine (valid but non-minimal) -- a real completeness");
            System.out.println("defect, not a soundness one. Witnesses dumped.");
        }
        if (denserAny > 0) {
            System.out.println();
            System.out.println("==== DEADLOCK CONFIRMATION (are the denser outputs enumerator-style deadlocks?) ====");
            System.out.printf("denser outputs examined            : %d%n", denserAny);
            System.out.printf("  confirmed deadlocks              : %d   (no legal single extra-edge escape)%n", t.confirmedDeadlock);
            System.out.printf("  NOT confirmed (had a legal escape): %d%n", t.notConfirmedDeadlock);
            System.out.printf("  rebuilt H0' not a legal PAG       : %d  (should be 0; output was legal)%n", t.h0NotGated);
            System.out.println("  extra-edge count (k) histogram over denser outputs:");
            for (int k = 0; k < t.kHist.length; k++) {
                if (t.kHist[k] > 0) System.out.printf("    k=%d : %d%n", k, t.kHist[k]);
            }
            System.out.println("  illegal-deletion prong tally (the trap's mechanism):");
            System.out.printf("    %-12s %12s %12s%n", "", "genuine", "non-genuine");
            for (int i = 0; i < 4; i++) {
                System.out.printf("    %-12s %12d %12d%n", PRONG_NAME[i], t.dlGenProng[i], t.dlNgProng[i]);
            }
            System.out.println("  Seeded with FCIT's OWN accumulated sepset map (not minimal sepsets):");
            System.out.println("  confirmed should approach the denser count if accumulated-sepset collider");
            System.out.println("  over-stamping is the unifying trap mechanism. A residual (not-confirmed)");
            System.out.println("  is then escapes RB never attempted -- a third, distinct failure mode.");
        }

        System.out.println("\nwitnesses written to: " + dumpPath);
    }

    // ====================================================================
    // Deadlock confirmation: is a denser FCIT output an enumerator-style
    // deadlock? Rebuild the stuck skeleton as the enumerator's constructed
    // H0 (truePag + extra edges + their oracle sepsets), then run the
    // enumerator's EXACT cold-reorient deadlock test on it. The reorient /
    // collider / sepset / phantom helpers below are copied verbatim from
    // PhantomKernelEnumerator so the verdict is identical to that probe's.
    // ====================================================================
    private static final class DeadlockInfo {
        boolean gated;          // H0' (truePag + extras) cold-reorients to a legal PAG
        boolean confirmed;      // every single extra-edge deletion cold-reorients illegal
        int k;                  // number of extra edges
        final int[] genProng = new int[4];
        final int[] ngProng  = new int[4];
        String note = "";
        String log  = "";
    }

    private static DeadlockInfo confirmDeadlock(Graph truePag, MsepTest oracle, Knowledge knowledge,
                                                List<Node[]> extraPairs, SepsetMap fcitSepsets) throws InterruptedException {
        DeadlockInfo di = new DeadlockInfo();
        di.k = extraPairs.size();
        List<Node> obs = truePag.getNodes();

        // FCIT's accumulated sepset map, translated into truth node space (by name) so it can
        // drive adjustForExtraSepsets over the truePag-built construction. This reproduces the
        // collider over-stamping that traps FCIT, which the minimal-sepset version did not.
        SepsetMap fSep = translateSepsets(fcitSepsets, obs);

        // Rebuild H0' = truePag + extra edges (o-o), in truth node space.
        Graph h0 = new EdgeListGraph(truePag);
        List<Edge> spurious = new ArrayList<>();
        for (Node[] p : extraPairs) {
            h0.addEdge(new Edge(p[0], p[1], Endpoint.CIRCLE, Endpoint.CIRCLE));
            spurious.add(new Edge(p[0], p[1], Endpoint.CIRCLE, Endpoint.CIRCLE));
        }
        Set<Triple> initialColliders = noteInitialColliders(obs, truePag);

        // H0' must reorient (under FCIT's map) to a legal PAG to count as a deadlock.
        Graph g0 = new EdgeListGraph(h0);
        reorient(g0, oracle, fSep, knowledge, initialColliders, EXCLUDE_SELECTION_BIAS);
        di.gated = PagLegalityCheck.isLegalPag(g0, new HashSet<>()).isLegalPag();

        // Deadlock test: delete each extra edge the way FCIT would (its accumulated map PLUS the
        // removed edge's own oracle sepset), cold-reorient, require all illegal.
        int legalEscapes = 0;
        StringBuilder log = new StringBuilder();
        for (Edge e : spurious) {
            Graph h1 = new EdgeListGraph(h0);
            Edge present = h1.getEdge(e.getNode1(), e.getNode2());
            if (present == null) continue;
            h1.removeEdge(present);

            SepsetMap delMap = copySepsets(fSep);
            Set<Node> se = oracleSepset(oracle, e.getNode1(), e.getNode2(), obs, MAX_COND);
            if (se != null) delMap.set(e.getNode1(), e.getNode2(), se);

            reorient(h1, oracle, delMap, knowledge, initialColliders, EXCLUDE_SELECTION_BIAS);
            PagLegalityCheck.LegalPagRet ret = PagLegalityCheck.isLegalPag(h1, new HashSet<>());
            if (ret.isLegalPag()) { legalEscapes++; continue; }
            Set<DiscriminatingPath> ddp = FciOrient.listDiscriminatingPaths(h1, MAX_LEN, true);
            boolean genuine = allPhantoms(ddp, truePag).isEmpty();
            int idx = prongIdx(ret.getReason());
            if (genuine) di.genProng[idx]++; else di.ngProng[idx]++;
            log.append("      ").append(e).append(" : ").append(genuine ? "genuine/" : "non-genuine/")
                    .append(PRONG_NAME[idx]).append(" -- ").append(ret.getReason()).append('\n');
        }
        di.confirmed = (legalEscapes == 0);
        di.log = log.toString();
        return di;
    }

    // FCIT's sepset map lives in the output's node space; re-key it into truth space by name so it
    // can drive the truePag-built reorientation. Drops any pair/separator node not present in truth.
    private static SepsetMap translateSepsets(SepsetMap src, List<Node> truthNodes) {
        Map<String, Node> byName = new HashMap<>();
        for (Node n : truthNodes) byName.put(n.getName(), n);
        SepsetMap dst = new SepsetMap();
        if (src == null) return dst;
        for (Set<Node> key : src.keySet()) {
            List<Node> pr = new ArrayList<>(key);
            if (pr.size() != 2) continue;
            Node a = byName.get(pr.get(0).getName());
            Node b = byName.get(pr.get(1).getName());
            if (a == null || b == null) continue;
            Set<Node> s = src.get(pr.get(0), pr.get(1));
            Set<Node> st = new HashSet<>();
            if (s != null) for (Node z : s) { Node tz = byName.get(z.getName()); if (tz != null) st.add(tz); }
            dst.set(a, b, st);
        }
        return dst;
    }

    private static SepsetMap copySepsets(SepsetMap src) {
        SepsetMap dst = new SepsetMap();
        if (src == null) return dst;
        for (Set<Node> key : src.keySet()) {
            List<Node> pr = new ArrayList<>(key);
            if (pr.size() != 2) continue;
            Set<Node> s = src.get(pr.get(0), pr.get(1));
            dst.set(pr.get(0), pr.get(1), s == null ? new HashSet<>() : new HashSet<>(s));
        }
        return dst;
    }

    private static String formatDeadlockInfo(DeadlockInfo di) {
        StringBuilder sb = new StringBuilder();
        sb.append("  -- deadlock confirmation --\n");
        sb.append("    extra edges (k)        : ").append(di.k).append('\n');
        sb.append("    H0' is a legal PAG     : ").append(di.gated).append('\n');
        sb.append("    confirmed deadlock     : ").append(di.confirmed)
                .append(di.confirmed ? "  (every single extra-edge removal cold-reorients illegal)" : "  (a legal single-edge escape exists)").append('\n');
        if (!di.note.isEmpty()) sb.append("    note                   : ").append(di.note).append('\n');
        if (!di.log.isEmpty())  sb.append("    illegal-deletion prongs:\n").append(di.log);
        return sb.toString();
    }

    // ── Helpers copied verbatim from PhantomKernelEnumerator (identical verdict) ──
    static final String[] PRONG_NAME = {"roundtrip", "maximality", "acyclic", "other"};

    private static String prong(String reason) {
        if (reason == null) return "other";
        String r = reason.toLowerCase();
        if (r.contains("cannot recover") || r.contains("between a mag and a pag")) return "roundtrip";
        if (r.contains("not maximal") || r.contains("inducing path")) return "maximality";
        if (r.contains("acyclic") || r.contains("cyclic")) return "acyclic";
        return "other";
    }

    private static int prongIdx(String reason) {
        switch (prong(reason)) {
            case "roundtrip":  return 0;
            case "maximality": return 1;
            case "acyclic":    return 2;
            default:           return 3;
        }
    }

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

    private static PrintWriter openDump(String path) {
        try {
            return new PrintWriter(new BufferedWriter(new FileWriter(path, false)));
        } catch (IOException io) {
            System.err.println("Could not open dump file " + path + "; falling back to stderr.");
            return new PrintWriter(System.err, true);
        }
    }

    static final class Result {
        long dagsScanned, modelsScanned, skipped, nodeSetMismatch;
        long exact, sameSkelDiffMarks, denser, sparser, mixed;
        long denserLegal, denserIllegal;
        // Deadlock confirmation on denser outputs (loop-closing check vs the enumerator).
        long confirmedDeadlock, notConfirmedDeadlock, h0NotGated;
        long[] dlGenProng = new long[4], dlNgProng = new long[4];
        long[] kHist = new long[Math.max(2, OBS * (OBS - 1) / 2 + 1)];
        List<String> witnesses = new ArrayList<>();
        long suppressed;

        void addWitness(String s) {
            if (witnesses.size() < WITNESS_CAP) witnesses.add(s); else suppressed++;
        }

        static Result merge(Result a, Result b) {
            a.dagsScanned += b.dagsScanned;   a.modelsScanned += b.modelsScanned;
            a.skipped += b.skipped;           a.nodeSetMismatch += b.nodeSetMismatch;
            a.exact += b.exact;               a.sameSkelDiffMarks += b.sameSkelDiffMarks;
            a.denser += b.denser;             a.sparser += b.sparser;   a.mixed += b.mixed;
            a.denserLegal += b.denserLegal;   a.denserIllegal += b.denserIllegal;
            a.confirmedDeadlock += b.confirmedDeadlock;
            a.notConfirmedDeadlock += b.notConfirmedDeadlock;
            a.h0NotGated += b.h0NotGated;
            for (int i = 0; i < 4; i++) { a.dlGenProng[i] += b.dlGenProng[i]; a.dlNgProng[i] += b.dlNgProng[i]; }
            int kn = Math.min(a.kHist.length, b.kHist.length);
            for (int i = 0; i < kn; i++) a.kHist[i] += b.kHist[i];
            for (String s : b.witnesses) a.addWitness(s);
            a.suppressed += b.suppressed;
            return a;
        }
    }
}
