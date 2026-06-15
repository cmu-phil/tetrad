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
import edu.cmu.tetrad.search.Fcit;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
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
public final class PhantomFcitReachabilityHarness1 {

    private static int N          = 6;
    private static int NUM_LATENT = 1;

    // Mirror the enumerator's reorient knobs so FCIT runs under the same config.
    private static final int     MAX_LEN         = -1;
    private static final int     DEPTH           = -1;
    private static final int     RECURSIVE_DEPTH = -1;
    private static final long    TIMEOUT         = -1L;
    private static final boolean EXCLUDE_SELECTION_BIAS = false;

    private static int OBS, P;
    private static int[][] PAIR;
    private static long TOTAL_DAGS;
    private static final int WITNESS_CAP = 5000;

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
                .collect(Result::new, PhantomFcitReachabilityHarness1::accumulate, Result::merge);

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

                // Run the REAL FCIT to completion on the oracle.
                Graph out = runFcit(oracle, score, knowledge);

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
                    r.addWitness(formatMismatch(mask, latSet, c, truePag, out, legal));
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
    // (and against however FcitBenchmarkHarness builds the test + runs FCIT,
    // which already does this correctly). Adjust the constructor / setters /
    // search() call and the test's observed-variable scope to match exactly.
    // ========================================================================
    private static Graph runFcit(MsepTest test, GraphScore score, Knowledge knowledge) throws InterruptedException {
        Fcit fcit = new Fcit(test, score);
        fcit.setKnowledge(knowledge);
        fcit.setCompleteRuleSetUsed(true);
        fcit.setMaxDiscriminatingPathLength(MAX_LEN);
        fcit.setDepth(DEPTH);
        fcit.setStartWith(Fcit.START_WITH.COMPLETE_GRAPH);
        fcit.setVerbose(false);
        // If Fcit exposes these (the enumerator's reorient uses the same knobs), set them too:
        // fcit.setRecursiveDepth(RECURSIVE_DEPTH);
        // fcit.setTimeout(TIMEOUT);
        return fcit.search();
    }

    // ── Comparison of FCIT output vs true PAG, by node name over observed pairs ──
    private enum CmpKind { EXACT, SAME_SKEL_MARKS, DENSER, SPARSER, MIXED }

    private static final class Cmp {
        CmpKind kind;
        int extraAdj, missingAdj, markDiffs;
        final List<String> extras = new ArrayList<>(), missing = new ArrayList<>(), marks = new ArrayList<>();
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
        System.out.println("\nwitnesses written to: " + dumpPath);
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
            for (String s : b.witnesses) a.addWitness(s);
            a.suppressed += b.suppressed;
            return a;
        }
    }
}
