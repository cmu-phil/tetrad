/// ////////////////////////////////////////////////////////////////////////////
// PhantomKernelEnumerator10.java  (parallel, PAG-space)                       //
//                                                                             //
// PKE9 enumerated DAG x latent placement. Everything it measures, however,    //
// depends on the model ONLY through the true PAG: every oracle query is over  //
// observed variables, and m-separation among the observed variables of a DAG  //
// equals m-separation in its MAG, which the true PAG determines up to         //
// equivalence. PKE10 therefore enumerates DISTINCT TRUE PAGs and runs the     //
// identical analysis once per PAG.                                            //
//                                                                             //
// Why: at N=7 / 2 latents the model sweep is 2^21 x C(7,2) = 44M models,      //
// roughly 224x the N=6 sweep before the maxSpurious 2->3 increase (another    //
// ~3x), which is not a runnable experiment. The distinct true PAGs over five  //
// observed variables number in the thousands.                                 //
//                                                                             //
// TWO ENUMERATION MODES (arg [0]):                                            //
//                                                                             //
//   dagsweep : build every DAG on N vertices in a fixed order x every         //
//     placement of numLatent latents, take dagToPag, and deduplicate. This    //
//     reproduces EXACTLY the N=7 / |L|=2 population, at the cost of 44M       //
//     dagToPag calls in the enumeration phase (parallel, and by far the       //
//     dominant cost). Use this to match a previous DAG-sweep run.             //
//                                                                             //
//   magspace : enumerate every labelled graph on OBS observed vertices with   //
//     each pair absent / --> / <-- / <->, keep the legal MAGs, project to a   //
//     PAG, and deduplicate. This is ~4^C(OBS,2) legality checks (about 1M at  //
//     OBS=5) and yields ALL PAGs over OBS observed variables -- a SUPERSET of //
//     those realizable with a bounded number of latents, since a MAG may need //
//     more latents than the cap allows. The resulting claim is therefore      //
//     about all PAGs at that observed size rather than about a latent count,  //
//     which is the stronger and simpler scope statement if it is compatible   //
//     with the run being reproduced.                                          //
//                                                                             //
// COUNTS CHANGE UNIT. Deduplication makes every nonzero count per DISTINCT    //
// TRUE PAG, not per model, so the totals here are NOT commensurable with a    //
// per-model sweep. Zero-counts are unaffected: a counterexample present under //
// one labelling is present under all, so a zero here is a zero there. Report  //
// the two kinds of result accordingly.                                        //
//                                                                             //
// Canonicalization is two-stage. Stage 1 keys on the positional edge string   //
// after relabelling the observed nodes to V1..VOBS in name order -- cheap,    //
// applied to every model. Stage 2 canonicalizes the survivors under all OBS!  //
// permutations and merges isomorphs -- expensive per graph, but applied only  //
// to the (few thousand) stage-1 survivors. Every quantity the analysis        //
// measures is relabelling-invariant, so stage 2 is sound; it can be disabled  //
// to obtain per-labelling counts instead.                                     //
//                                                                             //
// Everything downstream of the enumeration -- the separator regime (the       //
// shared FcitSepsets search, recorded for every true non-adjacency), the cold //
// reorientation, the legality prongs, the probes -- is PKE9 unchanged.        //
//                                                                             //
// args: [0]=mode (dagsweep|magspace, default magspace)                        //
//       [1]=N (dagsweep only, default 7)  [2]=numLatent (dagsweep, default 2) //
//       [3]=observed count (magspace only, default 5)                         //
//       [4]=maxSpurious (default 3)                                           //
//       [5]=bryanMaxSkeletonEdges (default 12)                                //
//       [6..]=log paths, as PKE9                                              //
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

/**
 * Exhaustive enumeration over DISTINCT TRUE PAGs, running PKE9's analysis once per
 * PAG. Feasible at observed sizes where the DAG x latent-placement sweep is not.
 *
 * @author josephramsey (harness scaffolding by Claude)
 */
public final class PhantomKernelEnumerator10 {

    /**
     * Default constructor.
     */
    public PhantomKernelEnumerator10() {}

    private static String MODE         = "magspace";
    private static int    N            = 6;    // dagsweep only
    private static int    NUM_LATENT   = 1;    // dagsweep only
    private static int    OBS          = 5;
    private static int    MAX_SPURIOUS = 3;
    private static boolean MERGE_ISOMORPHS = false;

//    private static String MODE         = "dagsweep";
//    private static int    N            = 6;    // dagsweep only
//    private static int    NUM_LATENT   = 1;    // dagsweep only
//    private static int    OBS          = 5;
//    private static int    MAX_SPURIOUS = 2;
//    private static boolean MERGE_ISOMORPHS = true;

    private static final int     MAX_LEN         = -1;
    private static final int     DEPTH           = -1;
    private static final int     RECURSIVE_DEPTH = -1;
    private static final int     RB_RADIUS       = -1;   // Fcit's default rbRadius
    private static final long    TIMEOUT         = -1L;
    private static final boolean EXCLUDE_SELECTION_BIAS = true;
    private static final boolean PROBE_STEP_BREAKS      = true;
    private static final boolean PROBE_R0_GENUINE       = true;

    private static int P;
    private static int[][] PAIR;
    private static long TOTAL_UNITS;
    private static final AtomicLong PROGRESS = new AtomicLong();
    private static final int WITNESS_CAP = 5000;

    private static int BRYAN_MAX_SKELETON_EDGES = 12;

    /** Canonical observed-node names, shared by every enumerated PAG. */
    private static List<Node> CANON;

    /**
     * Main method.
     * @param args args.
     */
    public static void main(String[] args) {
        if (args.length > 0) MODE         = args[0].toLowerCase();
        if (args.length > 1) N            = Integer.parseInt(args[1]);
        if (args.length > 2) NUM_LATENT   = Integer.parseInt(args[2]);
        if (args.length > 3) OBS          = Integer.parseInt(args[3]);
        if (args.length > 4) MAX_SPURIOUS = Integer.parseInt(args[4]);
        if (args.length > 5) BRYAN_MAX_SKELETON_EDGES = Integer.parseInt(args[5]);
        String dumpPath       = (args.length > 6)  ? args[6]  : "pke10_witnesses.log";
        String meekPath       = (args.length > 7)  ? args[7]  : "pke10_meek.log";
        String imapPath       = (args.length > 8)  ? args[8]  : "pke10_imap_violations.log";
        String stepBreakPath  = (args.length > 9)  ? args[9]  : "pke10_step_breaks.log";
        String magRecheckPath = (args.length > 10) ? args[10] : "pke10_mag_recheck.log";
        String r0NgPath       = (args.length > 11) ? args[11] : "pke10_r0_nongenuine.log";
        String residuePath    = (args.length > 12) ? args[12] : "pke10_residue.log";

        boolean dagSweep = MODE.startsWith("dag");
        if (dagSweep) OBS = N - NUM_LATENT;

        CANON = new ArrayList<>();
        for (int i = 0; i < OBS; i++) CANON.add(new GraphNode("V" + (i + 1)));

        System.err.printf("PKE10 mode=%s observed=%d maxSpurious=%d bryanCap=%d threads~%d%n",
                dagSweep ? "dagsweep" : "magspace", OBS, MAX_SPURIOUS,
                BRYAN_MAX_SKELETON_EDGES, Runtime.getRuntime().availableProcessors());

        long tEnum = System.currentTimeMillis();
        List<Graph> distinct = dagSweep ? enumerateByDagSweep() : enumerateByMagSpace();
        System.err.printf("enumeration: %d distinct true PAGs in %d ms%n",
                distinct.size(), System.currentTimeMillis() - tEnum);

        TOTAL_UNITS = distinct.size();
        PROGRESS.set(0);

        Result total = LongStream.range(0, distinct.size())
                .parallel()
                .collect(Result::new,
                        (r, i) -> analyze(r, distinct.get((int) i), "PAG#" + i),
                        Result::merge);
        total.distinctPags = distinct.size();

        writeLogs(total, dumpPath, meekPath, imapPath, stepBreakPath,
                magRecheckPath, r0NgPath, residuePath);
        printSummary(total, dumpPath, meekPath);
    }

    // ── Enumeration phase: distinct true PAGs ─────────────────────────────────

    /**
     * Mode dagsweep: every DAG on N vertices in a fixed order x every placement of
     * NUM_LATENT latents, deduplicated by true PAG. Reproduces a DAG-sweep
     * population exactly; the dagToPag calls dominate the run.
     */
    private static List<Graph> enumerateByDagSweep() {
        P = N * (N - 1) / 2;
        PAIR = new int[P][2];
        for (int idx = 0, i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++, idx++) { PAIR[idx][0] = i; PAIR[idx][1] = j; }
        }
        long totalMasks = 1L << P;
        TOTAL_UNITS = totalMasks;
        System.err.printf("  dagsweep: 2^%d = %d masks x C(%d,%d) latent placements%n",
                P, totalMasks, N, NUM_LATENT);

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
                    for (int b = 0; b < P; b++) {
                        if ((mask & (1L << b)) != 0) {
                            dag.addDirectedEdge(nodes.get(PAIR[b][0]), nodes.get(PAIR[b][1]));
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
     */
    private static List<Graph> enumerateByMagSpace() {
        int m = OBS * (OBS - 1) / 2;
        int[][] pr = new int[m][2];
        for (int idx = 0, i = 0; i < OBS; i++) {
            for (int j = i + 1; j < OBS; j++, idx++) { pr[idx][0] = i; pr[idx][1] = j; }
        }
        long total = 1L << (2 * m);   // 4^m
        TOTAL_UNITS = total;
        System.err.printf("  magspace: 4^%d = %d candidate graphs on %d observed vertices%n",
                m, total, OBS);

        Map<String, Graph> seen = new ConcurrentHashMap<>();
        AtomicLong done = new AtomicLong();

        LongStream.range(0, total).parallel().forEach(code -> {
            try {
                Graph mag = new EdgeListGraph(CANON);
                for (int idx = 0; idx < m; idx++) {
                    int st = (int) ((code >> (2 * idx)) & 3L);
                    if (st == 0) continue;
                    Node a = CANON.get(pr[idx][0]), b = CANON.get(pr[idx][1]);
                    switch (st) {
                        case 1:  mag.addDirectedEdge(a, b); break;
                        case 2:  mag.addDirectedEdge(b, a); break;
                        default: mag.addBidirectedEdge(a, b); break;
                    }
                }
                if (!isLegalMag(mag)) return;
                Graph pag = pagOfMag(mag);
                Graph canon = canonicalEdgeOrder(pag);
                seen.putIfAbsent(positionalKey(canon), canon);
            } catch (Exception ignore) {
                // Not a legal MAG / will not project; skip.
            } finally {
                long d = done.incrementAndGet();
                if ((d & 0x3FFFF) == 0) {
                    System.err.printf("  …graph %d/%d, %d distinct so far%n", d, total, seen.size());
                }
            }
        });

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
     *  content alone. Without this, the representative kept by putIfAbsent under
     *  a parallel stream is whichever object won the race, and two objects equal
     *  as labelled graphs can still differ in getAdjacentNodes order -- which RB
     *  is sensitive to, since it walks adjacency to build firstHops. */
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

    // ── Analysis phase: PKE9's per-model work, once per distinct true PAG ──────
    private static void analyze(Result r, Graph truePag, String modelId) {
        r.modelsScanned++;
        try {
            Knowledge knowledge = new Knowledge();
            List<Node> obs = truePag.getNodes();
            Set<Triple> initialColliders = noteInitialColliders(obs, truePag);

            // The oracle is m-separation in the TRUE MAG, not in a generating DAG.
            // Over the observed variables these coincide (marginalizing a DAG to a
            // MAG preserves m-separation among the retained vertices), and every
            // oracle query the harness makes is over observed variables only. This
            // is what lets the enumeration range over distinct true PAGs instead of
            // over DAG x latent-placement models: nothing downstream can tell two
            // models sharing a true PAG apart.
            Graph trueMag = magOfPag(truePag);
            MsepTest trueMsep = new MsepTest(trueMag);
            IndependenceTest oracle = trueMsep;
            List<int[]> nonAdj = nonAdjacentPairs(truePag, obs);
            if (nonAdj.isEmpty()) return;

            // Candidate spurious edges are true non-adjacencies. Separators are NOT
            // precomputed from a generating DAG: for each H0 below, EVERY true non-adjacency
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
//                            r.sepPairsQueried++;
//                            Set<Node> sep = spanningConfirmedSepset(h0, oracle, a, b2);
//                            if (sep != null) { sepsets.set(a, b2, sep); r.sepPairsConfirmed++; }
//                            else r.sepPairsUnconfirmed++;

                            r.sepPairsQueried++;
                            Set<Node> sep = spanningConfirmedSepset(h0, oracle, a, b2);
                            if (exhaustiveSepset(oracle, a, b2, obs) != null) r.sepPairsSeparable++;
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
                                r.addR0NgWitness(formatR0NonGenuine(modelId, spurious, e,
                                        truePag, h0, h1, r0ng, h0imap, alsoStepBreak));
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
                                            mech = "  mechanism COMPLETION (MAG-only unshielded): " + un;} else {
                                            r.sbResidue++;
                                            mech = "  mechanism RESIDUE: no collider on any spurious leg";

                                            // Residue anatomy. (a) Is a REAL adjacency oriented
                                            // differently in H1's MAG than in G*'s MAG -- the
                                            // reversal hypothesis? (b) Is there an all-real-leg
                                            // triple whose collider status the two MAGs disagree
                                            // on -- the unsound mark DISPLACED onto real edges?
                                            // (c) A relabelling-invariant signature of the pair
                                            // (G*, H1), so the residue can be counted by distinct
                                            // structure rather than by instance.
                                            String rev  = reversedRealEdge(mag1, trueMag, truePag);
                                            if (rev != null) r.sbResidueReversal++; else r.sbResidueNoReversal++;
                                            String disp = allRealLegUnsoundTriple(mag1, truePag, trueMag);
                                            if (disp != null) r.sbResidueDisplaced++; else r.sbResidueNoDisplaced++;
                                            String sig  = residueSignature(truePag, h1);
                                            r.residueSignatures.add(sig);

                                            r.addResidueWitness(formatResidue(modelId, spurious, e,
                                                    truePag, h0, h1, mag1, h1break)
                                                    + "  reversed real edge  : "
                                                    + (rev  == null ? "(NONE -- hypothesis fails here)" : rev)  + '\n'
                                                    + "  displaced mark      : "
                                                    + (disp == null ? "(NONE -- investigate)" : disp) + '\n'
                                                    + "  structure signature : " + sig + '\n');
                                        }
                                    }
                                }

                                r.addStepBreakWitness(formatStepBreak(modelId, spurious, e,
                                        truePag, h0, h1, h1break) + mech + "\n");

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
                                r.addMagRecheckWitness(formatMagRecheck(modelId, spurious, e,
                                        truePag, h0, h1, h1break, h1mag, magBreak, outcome));
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
                                    modelId, spurious, e, worst, "(non-genuine)", committed, sxy, h1));
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
                                    modelId, spurious, e, worst, "(legal)", committed, sxy, h1));
                            System.out.println("COUNTEREXAMPLE model=" + modelId
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
                                            modelId, spurious, e, worst, reason, true, sxy, h1));
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
                        r.addWitness(formatDeadlock(modelId, spurious, dlLog.toString(), truePag, h0));

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
                            r.addImapWitness(formatImapViolation(modelId, spurious,
                                    truePag, h0, bi));
                        } else if (!bi.checked) {
                            r.bryanUnchecked++;
                        } else if (!bi.reachable) {
                            r.bryanCounterexamples++;
                            r.addMeekWitness(formatBryan(modelId, spurious, truePag, h0, mi, bi));
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
            System.err.println("model " + modelId + " skipped: " + ex.getMessage());
        }

        long d = PROGRESS.incrementAndGet();
        if ((d & 0x3F) == 0) {
            System.err.printf("…PAG %d/%d analyzed%n", d, TOTAL_UNITS);
        }
    }

    /** Deterministic separability baseline. Every true non-adjacency IS m-separated
     *  by some subset of the remaining observed variables (non-adjacent in G* implies
     *  non-adjacent in the true MAG), so this returns non-null for every queried pair.
     *  Smallest-first over a name-sorted pool, so the returned set is a function of
     *  the graph alone -- unlike FCIT's search, whose candidate family is seeded by
     *  RB's blocking set and therefore inherits hash-iteration order. */
    private static Set<Node> exhaustiveSepset(IndependenceTest oracle, Node a, Node b, List<Node> obs)
            throws InterruptedException {
        List<Node> pool = new ArrayList<>(obs);
        pool.remove(a); pool.remove(b);
        pool.sort(Comparator.comparing(Node::getName));
        for (int k = 0; k <= pool.size(); k++) {
            SublistGenerator gen = new SublistGenerator(pool.size(), k);
            int[] choice;
            while ((choice = gen.next()) != null) {
                if (choice.length != k) continue;
                Set<Node> S = new LinkedHashSet<>(GraphUtils.asSet(choice, pool));
                if (oracle.checkIndependence(a, b, S).isIndependent()) return S;
            }
        }
        return null;
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

    // ── Logs ──────────────────────────────────────────────────────────────────
    private static void writeLogs(Result total, String dumpPath, String meekPath, String imapPath,
                                  String stepBreakPath, String magRecheckPath, String r0NgPath,
                                  String residuePath) {
        String scope = "mode=" + MODE + " observed=" + OBS + " maxSpurious=" + MAX_SPURIOUS
                + " distinctTruePags=" + total.distinctPags
                + (MERGE_ISOMORPHS ? " (up to relabelling)" : " (per labelling)");

        PrintWriter dump = openDump(dumpPath);
        try {
            dump.println("# PKE10 witnesses / anomalies.  " + scope);
            dump.println("# Counts are per DISTINCT TRUE PAG, not per model.\n");
            for (String w : total.witnesses) dump.println(w);
            if (total.suppressed > 0) {
                dump.println("==== (" + total.suppressed + " further suppressed; raise WITNESS_CAP) ====");
            }
        } finally { dump.flush(); dump.close(); }

        PrintWriter meekDump = openDump(meekPath);
        try {
            meekDump.println("# Generalized-Meek (Bryan full form) counterexamples.  " + scope);
            meekDump.println("# single-edge-form deadlocks: " + total.meekCounterexamples
                    + " | Bryan-form survivors: " + total.bryanCounterexamples
                    + " | unchecked: " + total.bryanUnchecked + "\n");
            for (String w : total.meekWitnesses) meekDump.println(w);
            if (total.meekSuppressed > 0) {
                meekDump.println("==== (" + total.meekSuppressed + " further suppressed) ====");
            }
        } finally { meekDump.flush(); meekDump.close(); }

        PrintWriter imapDump = openDump(imapPath);
        try {
            imapDump.println("# Non-I-map H0: cold reorientation of G*+spurious is not an I-map of G*.  " + scope);
            imapDump.println("# total: " + total.bryanNotImap + "\n");
            for (String w : total.imapWitnesses) imapDump.println(w);
            if (total.imapSuppressed > 0) {
                imapDump.println("==== (" + total.imapSuppressed + " further suppressed) ====");
            }
        } finally { imapDump.flush(); imapDump.close(); }

        PrintWriter stepDump = openDump(stepBreakPath);
        try {
            stepDump.println("# PAG->PAG step-breaks: I-map H0, legal single-edge move, non-I-map H1.  " + scope);
            stepDump.println("# total: " + total.pagStepBreaks + "\n");
            for (String w : total.stepBreakWitnesses) stepDump.println(w);
            if (total.stepBreakSuppressed > 0) {
                stepDump.println("==== (" + total.stepBreakSuppressed + " further suppressed) ====");
            }
        } finally { stepDump.flush(); stepDump.close(); }

        PrintWriter magDump = openDump(magRecheckPath);
        try {
            magDump.println("# Each step-break re-committed through FcitMag (PAG->MAG->PAG).  " + scope);
            magDump.println("# alsoBreaks: " + total.magCommitAlsoBreaks
                    + " | fixed: " + total.magCommitFixed
                    + " | reverted: " + total.magCommitReverted + "\n");
            for (String w : total.magRecheckWitnesses) magDump.println(w);
            if (total.magRecheckSuppressed > 0) {
                magDump.println("==== (" + total.magRecheckSuppressed + " further suppressed) ====");
            }
        } finally { magDump.flush(); magDump.close(); }

        PrintWriter r0Dump = openDump(r0NgPath);
        try {
            r0Dump.println("# Genuineness widened from R4 to R0: legal waypoints with an unshielded");
            r0Dump.println("# collider on a spurious leg. DETECTIONS; the decisive column is whether any");
            r0Dump.println("# is also an I-map step-break.  " + scope);
            r0Dump.println("# legal R0-non-genuine: " + total.r0NonGenuineLegal
                    + " | also step-breaks: " + total.r0NgAndStepBreak
                    + " | step-breaks not explained by R0: " + total.stepBreakNotExplainedByR0 + "\n");
            for (String w : total.r0NgWitnesses) r0Dump.println(w);
            if (total.r0NgSuppressed > 0) {
                r0Dump.println("==== (" + total.r0NgSuppressed + " further suppressed) ====");
            }
        } finally { r0Dump.flush(); r0Dump.close(); }

        PrintWriter resDump = openDump(residuePath);
        try {
            resDump.println("# Step-breaks binned by false-collider mechanism.  " + scope);
            resDump.println("# R0: " + total.sbR0 + " | R4 shielded: " + total.sbR4Shielded
                    + " | COMPLETION: " + total.sbCompletion + " | RESIDUE: " + total.sbResidue);
            resDump.println("# RESIDUE anatomy -- real edge reversed: " + total.sbResidueReversal
                    + "/" + total.sbResidue + " | displaced mark: " + total.sbResidueDisplaced
                    + "/" + total.sbResidue + " | distinct (G*,H1) structures: "
                    + total.residueSignatures.size() + "\n");
            for (String w : total.residueWitnesses) resDump.println(w);
            if (total.residueSuppressed > 0) {
                resDump.println("==== (" + total.residueSuppressed + " further suppressed) ====");
            }
        } finally { resDump.flush(); resDump.close(); }
    }

    private static void printSummary(Result t, String dumpPath, String meekPath) {
        System.out.println("\n==== PKE10 EXHAUSTIVE SUMMARY (PAG-space enumeration) ====");
        System.out.printf("mode=%s observed=%d maxSpurious=%d%n", MODE, OBS, MAX_SPURIOUS);
        if (MODE.startsWith("dag")) {
            System.out.printf("  source population: all DAGs on %d vertices x C(%d,%d) latent placements%n",
                    N, N, NUM_LATENT);
        } else {
            System.out.printf("  source population: ALL legal MAGs on %d observed vertices%n", OBS);
            System.out.println("  (a superset of those realizable with any bounded latent count)");
        }
        System.out.printf("distinct true PAGs analyzed      : %d   %s%n", t.distinctPags,
                MERGE_ISOMORPHS ? "(up to relabelling)" : "(per labelling)");
        System.out.println("NOTE: nonzero counts below are PER DISTINCT TRUE PAG and are NOT");
        System.out.println("      commensurable with a per-model sweep. Zero-counts are invariant.");
        System.out.printf("legal, phantom-free seeds H0     : %d%n", t.gated);
        System.out.printf("single-edge waypoints classified : %d%n", t.h1States);
        System.out.printf("waypoints genuine                : %d%n", t.positives);
        System.out.printf("non-genuine, illegal             : %d%n", t.illegalNG);
        System.out.printf("non-genuine, LEGAL               : %d  <-- CONJECTURE 1 COUNTEREXAMPLES%n",
                t.counterexamples);
        System.out.printf("PAGs skipped (residual throws)   : %d%n", t.skipped);

        System.out.println("\n==== SEPSET COVERAGE AUDIT ====");
        System.out.printf("non-spurious non-adjacent pairs queried  : %d%n", t.sepPairsQueried);
        System.out.printf("  ...confirmed separator recorded        : %d%n", t.sepPairsConfirmed);
        System.out.printf("  ...no confirmed separator              : %d%n", t.sepPairsUnconfirmed);
        System.out.printf("spurious subsets skipped (no sepset)     : %d%n", t.spuriousSepMissing);
        System.out.printf("  ...separable by exhaustive search      : %d   %s%n", t.sepPairsSeparable,
                t.sepPairsSeparable == t.sepPairsQueried
                        ? "(= queried: every pair IS separable; the unconfirmed are search misses)"
                        : "(< queried: SOME PAIR IS NOT SEPARABLE -- investigate)");

        System.out.println("\n==== R4 ABSTENTIONS ====");
        System.out.printf("total abstentions                : %d%n", t.totalAbstentions);
        System.out.printf("  %-18s %12s %12s%n", "", "illegal", "LEGAL");
        System.out.printf("  %-18s %12d %12d%n", "abstained",    t.nonGenAbstainIllegal,   t.nonGenAbstainLegal);
        System.out.printf("  %-18s %12d %12d%n", "no abstention", t.nonGenNoAbstainIllegal, t.nonGenNoAbstainLegal);

        System.out.println("\n==== KERNEL CROSS-TAB (non-genuine H1) ====");
        System.out.printf("  %-22s %12s %12s%n", "", "illegal", "LEGAL");
        System.out.printf("  %-22s %12d %12d%n", "v-end = circle",    t.circleIllegal,    t.circleLegal);
        System.out.printf("  %-22s %12d %12d%n", "v-end = committed", t.committedIllegal, t.committedLegal);

        System.out.println("\n==== PHANTOM SPINE CENSUS ====");
        System.out.printf("max phantom collider-path length : %d%n", t.maxPhantomColliderLen);
        boolean any = false;
        for (int L = 0; L < t.phantomLenHist.length; L++) {
            if (t.phantomLenHist[L] > 0) { System.out.printf("  length %d : %d%n", L, t.phantomLenHist[L]); any = true; }
        }
        if (!any) System.out.println("  (no phantom DDPs observed)");

        System.out.println("\n==== R4 FIRING-GAP PROBE ====");
        long pg = t.phantomSpineDefinite + t.phantomSpineNonDefinite;
        System.out.printf("spine colliders all definite : %d / %d   (control)%n", t.phantomSpineDefinite, pg);
        System.out.printf("  circle at v : %d / %d   <- the under-commit gap%n", t.wvCircleAtV, pg);
        System.out.printf("  arrow at v  : %d / %d   <- falsifier if nonzero (dumped)%n", t.wvArrowAtV, pg);
        System.out.printf("  tail  at v  : %d / %d%n", t.wvTailAtV, pg);
        System.out.printf("  other       : %d / %d%n", t.wvOtherAtV, pg);

        System.out.println("\n==== NOSTALL PROBE ====");
        System.out.printf("seeds with >=1 legal escape      : %d / %d%n", t.h0WithEscape, t.gated);
        System.out.printf("seeds with NO legal escape       : %d / %d   <-- deadlocks%n", t.h0Deadlock, t.gated);
        if (t.h0Deadlock > 0) {
            System.out.printf("    all-genuine / all-non-genuine / mixed : %d / %d / %d%n",
                    t.dlAllGenuine, t.dlAllNonGenuine, t.dlMixed);
            System.out.printf("    %-13s %12s %12s%n", "", "genuine", "non-genuine");
            for (int i = 0; i < 5; i++) {
                System.out.printf("    %-13s %12d %12d%n", PRONG_NAME[i], t.dlGenProng[i], t.dlNgProng[i]);
            }
        }

        System.out.println("\n==== PAG->PAG MARKOV AUDIT ====");
        System.out.printf("non-I-map H0                                  : %d%n", t.bryanNotImap);
        System.out.printf("I-map step-breaks (legal H1, non-I-map)       : %d%n", t.pagStepBreaks);
        System.out.printf("  mechanism R0 / R4 / COMPLETION / RESIDUE    : %d / %d / %d / %d%n",
                t.sbR0, t.sbR4Shielded, t.sbCompletion, t.sbResidue);
        if (t.sbResidue > 0) {
            System.out.println("  -- RESIDUE anatomy (collider-genuine yet non-Markov) --");
            System.out.printf("    real edge reversed in H1's MAG   : %d / %d%n",
                    t.sbResidueReversal, t.sbResidue);
            System.out.printf("    no real edge reversed            : %d   <- hypothesis fails here%n",
                    t.sbResidueNoReversal);
            System.out.printf("    all-real-leg displaced mark      : %d / %d%n",
                    t.sbResidueDisplaced, t.sbResidue);
            System.out.printf("    no displaced mark                : %d   <- investigate%n",
                    t.sbResidueNoDisplaced);
            System.out.printf("    distinct (G*,H1) structures      : %d   (of %d instances)%n",
                    t.residueSignatures.size(), t.sbResidue);
        }
        System.out.printf("  FcitMag re-commit breaks / fixes / reverts  : %d / %d / %d%n",
                t.magCommitAlsoBreaks, t.magCommitFixed, t.magCommitReverted);
        System.out.printf("R0-non-genuine detections (alsoStepBreak=%d)   : %d%n",
                t.r0NgAndStepBreak, t.r0NonGenuineLegal);

        System.out.println("\n==== GENERALIZED-MEEK ====");
        System.out.printf("single-edge counterexamples (deadlocks)       : %d%n", t.meekCounterexamples);
        for (int a = 0; a < t.meekArityHist.length; a++) {
            if (t.meekArityHist[a] > 0) System.out.printf("    min escape arity %d : %d%n", a, t.meekArityHist[a]);
        }
        System.out.printf("Bryan-form genuine counterexamples           : %d%n", t.bryanCounterexamples);
        System.out.printf("  rescued by the richer move set             : %d%n",
                Math.max(0, t.meekCounterexamples - t.bryanCounterexamples - t.bryanUnchecked - t.bryanNotImap));
        System.out.printf("  excluded (H0 not an I-map)                 : %d%n", t.bryanNotImap);
        System.out.printf("  unchecked (skeleton over %d edges)          : %d%n",
                BRYAN_MAX_SKELETON_EDGES, t.bryanUnchecked);
        if (t.bryanUnchecked > 0) {
            System.out.println("  WARNING: unchecked > 0 -- the Bryan verdict is not exhaustive at this");
            System.out.println("  scope. Raise the cap (arg[5]) or report the gap explicitly.");
        }

        System.out.println("\n==== MAG-SWEEP ESCAPE ====");
        long magTotal = t.magSweepReachesTrue + t.magSweepStuck + t.magSweepWrongPag;
        System.out.printf("    deadlocks swept                         : %d%n", magTotal);
        System.out.printf("    reconstructs G* exactly                 : %d%n", t.magSweepReachesTrue);
        System.out.printf("    stuck (illegal MAG)                     : %d%n", t.magSweepStuck);
        System.out.printf("    legal but != G*                         : %d%n", t.magSweepWrongPag);

        System.out.println();
        if (t.counterexamples == 0) {
            System.out.printf("No legal non-genuine PAG arises over %d distinct true PAGs at observed size "
                            + "%d with spurious<=%d. Conjecture 1 holds at this scope.%n",
                    t.distinctPags, OBS, MAX_SPURIOUS);
        } else {
            System.out.println("COUNTEREXAMPLE(S) FOUND -- see " + dumpPath);
        }
        System.out.println("Bryan survivors (only) written to: " + meekPath);
    }

    // ── Thread-confined accumulator ────────────────────────────────────────────
    static final class Result {
        long distinctPags;
        long modelsScanned, gated, h1States, positives, illegalNG, counterexamples;
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
        long sepPairsQueried, sepPairsConfirmed, sepPairsUnconfirmed, spuriousSepMissing, sepPairsSeparable;
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
        // Residue anatomy: reversal hypothesis, displaced mark, distinct structures.
        long sbResidueReversal, sbResidueNoReversal, sbResidueDisplaced, sbResidueNoDisplaced;
        Set<String> residueSignatures = new LinkedHashSet<>();
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
            a.modelsScanned += b.modelsScanned;
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
            a.sepPairsSeparable += b.sepPairsSeparable;
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
            a.sbResidueReversal += b.sbResidueReversal;
            a.sbResidueNoReversal += b.sbResidueNoReversal;
            a.sbResidueDisplaced += b.sbResidueDisplaced;
            a.sbResidueNoDisplaced += b.sbResidueNoDisplaced;
            a.residueSignatures.addAll(b.residueSignatures);
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

    private static String formatCase(String tag, String modelId, List<Edge> spurious,
                                     Edge deleted, DiscriminatingPath phantom, String reason,
                                     boolean committedVEnd, Set<Node> sepsetXY, Graph h1) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== ").append(tag).append(" ====\n");
        sb.append("  model           : ").append(modelId).append('\n');
        sb.append("  spurious added  : ").append(spurious).append('\n');
        sb.append("  deleted edge    : ").append(deleted).append('\n');
        sb.append("  phantom DDP     : ").append(phantom).append('\n');
        sb.append("  v-end committed : ").append(committedVEnd).append('\n');
        sb.append("  sepset(x,y)     : ").append(sepsetXY == null ? "(none recorded)" : sepsetXY).append('\n');
        sb.append("  legality reason : ").append(reason).append('\n');
        sb.append("  H1:\n").append(h1).append('\n');
        return sb.toString();
    }

    private static String formatDeadlock(String modelId, List<Edge> spurious,
                                         String perEdgeLog, Graph truePag, Graph h0) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== NOSTALL DEADLOCK: gated genuine-legal H0, NO legal single-edge deletion ====\n");
        sb.append("  model           : ").append(modelId).append('\n');
        sb.append("  spurious edges  : ").append(spurious).append("  (k=").append(spurious.size()).append(")\n");
        sb.append("  outcome of deleting each spurious edge from H0:\n");
        sb.append(perEdgeLog);
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0 (G* + spurious, cold-reoriented):\n").append(h0).append('\n');
        return sb.toString();
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

    private static String formatImapViolation(String modelId, List<Edge> spurious,
                                              Graph truePag, Graph h0, BryanInfo bi) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== NON-I-MAP H0 (cold reorient of G*+spurious is NOT an I-map of G*) ====\n");
        sb.append("  Under the corrected sepset regime this population is expected EMPTY; an entry\n");
        sb.append("  here is a defect. The witness CI below is the proof of the violation.\n");
        sb.append("  model                 : ").append(modelId).append('\n');
        sb.append("  extra (spurious) edges: ").append(spurious).append("  (k=").append(bi.k).append(")\n");
        sb.append("  WITNESS (holds in H0, fails in G*): ").append(bi.imapWitness).append('\n');
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

    private static String formatMagRecheck(String modelId, List<Edge> spurious, Edge removed,
                                           Graph truePag, Graph h0, Graph h1, String pagWitness,
                                           Graph h1mag, String magWitness, String outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== STEP-BREAK RE-COMMITTED THROUGH FcitMag (PAG->MAG->PAG) ====\n");
        sb.append("  Same I-map H0, same removal. Does FcitMag's commit also leave the I-map class?\n");
        sb.append("  FcitMag outcome       : ").append(outcome).append('\n');
        sb.append("  model                 : ").append(modelId).append('\n');
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

    // Reversal hypothesis. A real adjacency of G* (present in both MAGs) carrying
    // different endpoints in H1's MAG than in G*'s MAG. Reorienting a real edge can
    // create or dissolve an all-real-leg collider, which is how a residue break
    // arises with no arrowhead sitting on a spurious leg. Returns the first such
    // edge, or null. Real adjacencies absent from H1's MAG are reported too, since
    // that is the other way the skeleton can differ.
    private static String reversedRealEdge(Graph mag1, Graph trueMag, Graph truePag) {
        for (Edge e : truePag.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            if (!trueMag.isAdjacentTo(a, b)) continue;
            if (!mag1.isAdjacentTo(a, b)) {
                return a.getName() + "-" + b.getName() + " real in G* but ABSENT from H1's MAG";
            }
            Endpoint ma = mag1.getEndpoint(b, a),    mb = mag1.getEndpoint(a, b);
            Endpoint ta = trueMag.getEndpoint(b, a), tb = trueMag.getEndpoint(a, b);
            if (ma == ta && mb == tb) continue;
            return a.getName() + edgeStr(ta, tb) + b.getName() + " in G*'s MAG, but "
                    + a.getName() + edgeStr(ma, mb) + b.getName() + " in H1's MAG";
        }
        return null;
    }

    private static String edgeStr(Endpoint atA, Endpoint atB) {
        return " " + (atA == Endpoint.ARROW ? "<" : atA == Endpoint.TAIL ? "-" : "o")
                + "-" + (atB == Endpoint.ARROW ? ">" : atB == Endpoint.TAIL ? "-" : "o") + " ";
    }

    // Displaced unsound mark (restored from PKE3). A triple BOTH of whose legs are
    // real in G*, but whose collider status H1's MAG and G*'s MAG disagree on.
    // ARROWHEAD-displaced: a collider here, a non-collider in truth -- an unsound
    // arrowhead carried onto real edges by propagation. TAIL-displaced: the reverse,
    // a real collider dropped. Either way the spurious edge's damage has moved off
    // the spurious legs, which is why the leg test misses it.
    private static String allRealLegUnsoundTriple(Graph mag1, Graph truePag, Graph trueMag) {
        for (Node w : mag1.getNodes()) {
            List<Node> adj = mag1.getAdjacentNodes(w);
            int m = adj.size();
            for (int p = 0; p < m; p++) {
                for (int q = p + 1; q < m; q++) {
                    Node a = adj.get(p), b = adj.get(q);
                    if (!truePag.isAdjacentTo(a, w) || !truePag.isAdjacentTo(w, b)) continue;
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

    // Relabelling-invariant signature of the PAIR (G*, H1): the lexicographically
    // least concatenation over a COMMON permutation of the vertices, so two residue
    // instances that differ only by relabelling collapse to one structure.
    private static String residueSignature(Graph truePag, Graph h1) {
        List<Node> order = new ArrayList<>(CANON);
        String[] best = {null};
        permutePair(truePag, h1, order, 0, best);
        return best[0];
    }

    private static void permutePair(Graph g1, Graph g2, List<Node> order, int k, String[] best) {
        if (k == order.size()) {
            String s = encode(g1, order) + "|" + encode(g2, order);
            if (best[0] == null || s.compareTo(best[0]) < 0) best[0] = s;
            return;
        }
        for (int i = k; i < order.size(); i++) {
            Collections.swap(order, k, i);
            permutePair(g1, g2, order, k + 1, best);
            Collections.swap(order, k, i);
        }
    }

    private static String formatResidue(String modelId, List<Edge> spurious, Edge removed,
                                        Graph truePag, Graph h0, Graph h1, Graph mag1, String witness) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== STEP-BREAK WITH NO SPURIOUS-LEG COLLIDER (RESIDUE) ====\n");
        sb.append("  A LEGAL non-Markov reorientation in which NO collider, in the PAG or in its MAG\n");
        sb.append("  completion, sits on a spurious leg -- the unsoundness is not traceable to a\n");
        sb.append("  spurious adjacency by the leg test. Investigate before anything else.\n");
        sb.append("  WITNESS (in H1, not in G*): ").append(witness).append('\n');
        sb.append("  model                 : ").append(modelId).append('\n');
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

    private static String formatR0NonGenuine(String modelId, List<Edge> spurious, Edge removed,
                                             Graph truePag, Graph h0, Graph h1,
                                             String firing, boolean h0imap, boolean alsoStepBreak) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== LEGAL BUT R0-NON-GENUINE (genuineness widened from R4 to R0) ====\n");
        sb.append("  A LEGAL from-scratch reorientation carrying an unshielded collider with a\n");
        sb.append("  spurious leg -- a DETECTION under the widened definition. The decisive column\n");
        sb.append("  is alsoStepBreak; expected false for every entry.\n");
        sb.append("  R0 non-genuine firing : ").append(firing).append('\n');
        sb.append("  H0 was an I-map of G* : ").append(h0imap).append('\n');
        sb.append("  also an I-map step-break (non-I-map H1): ").append(alsoStepBreak).append('\n');
        sb.append("  model                 : ").append(modelId).append('\n');
        sb.append("  extra (spurious) edges: ").append(spurious).append('\n');
        sb.append("  edge removed from H0  : ").append(removed).append('\n');
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0:\n").append(h0).append('\n');
        sb.append("  H1 (legal, R0-non-genuine):\n").append(h1).append('\n');
        return sb.toString();
    }

    private static String formatStepBreak(String modelId, List<Edge> spurious, Edge removed,
                                          Graph truePag, Graph h0, Graph h1, String witness) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== PAG->PAG STEP LEAVES I-MAP CLASS (defect: expected EMPTY under PKE9 regime) ====\n");
        sb.append("  H0 is an I-map of G*, but the LEGAL single-edge remove+reorient below yields an\n");
        sb.append("  H1 that is NOT an I-map of G*.\n");
        sb.append("  model                 : ").append(modelId).append('\n');
        sb.append("  extra (spurious) edges: ").append(spurious).append('\n');
        sb.append("  edge removed from H0  : ").append(removed).append('\n');
        sb.append("  WITNESS (in H1, not in G*): ").append(witness).append('\n');
        sb.append("  true PAG G* (over observed):\n").append(truePag).append('\n');
        sb.append("  H0 (I-map of G*, legal):\n").append(h0).append('\n');
        sb.append("  H1 (legal, NOT an I-map of G*):\n").append(h1).append('\n');
        return sb.toString();
    }

    private static String formatBryan(String modelId, List<Edge> spurious,
                                      Graph truePag, Graph h0, MeekInfo mi, BryanInfo bi) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== GENERALIZED-MEEK COUNTEREXAMPLE (Bryan full form: removals + reorientations) ====\n");
        sb.append("  A legal PAG, I-map of the true PAG, from which the true PAG is NOT reachable by any\n");
        sb.append("  sequence of legal single-edge removals and reorientations over I-map PAGs.\n");
        sb.append("  model                 : ").append(modelId).append('\n');
        sb.append("  extra (spurious) edges: ").append(spurious).append("  (k=").append(bi.k).append(")\n");
        sb.append("  single-edge min escape arity : ").append(mi.minEscapeArity).append('\n');
        sb.append("  I-map states explored        : ").append(bi.statesExplored).append('\n');
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
