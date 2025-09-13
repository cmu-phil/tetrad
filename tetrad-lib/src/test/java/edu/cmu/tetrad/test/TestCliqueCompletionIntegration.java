package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.independence.RankIndependenceTest;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomMim;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.blocks.BlockDiscoverers;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.SingleClusterPolicy;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.CliqueCompletion;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class TestCliqueCompletionIntegration {

    private static MimData makeMimData(int nRows, long seed) {
        RandomMim.LatentGroupSpec spec1 = new RandomMim.LatentGroupSpec(5, 1, 6);
        Random rng = new Random(seed);

        Graph gTrue = RandomMim.constructRandomMim(
                List.of(spec1),
                5, 0,
                0, 0,
                RandomMim.LatentLinkMode.CARTESIAN_PRODUCT,
                rng
        );

        Parameters params = new Parameters();
        params.set("seed", seed);
        params.set("coefLow", 0.2);
        params.set("coefHigh", 1.2);
        params.set("coefSymmetric", true);

        SemPm pm = new SemPm(gTrue);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(nRows, false);

        List<Set<Node>> clusters = extractTrueMimClusters(gTrue, data);
        System.out.println("True clusters (children of each latent): " +
                           clusters.stream().map(TestCliqueCompletionIntegration::names).toList());

        return new MimData(data, gTrue, clusters);
    }

    // =================== Data & Truth ===================

    private static List<Set<Node>> extractTrueMimClusters(Graph gTrue, DataSet measuredData) {
        return extractTrueMimClusters(gTrue, measuredData, 3, /*coalesceIdenticalChildSets=*/true);
    }

    /**
     * Extract measurement clusters as measured children of latent nodes.
     */
    private static List<Set<Node>> extractTrueMimClusters(Graph gTrue,
                                                          DataSet measuredData,
                                                          int minSize,
                                                          boolean coalesceIdenticalChildSets) {
        Set<String> measuredNames = measuredData.getVariables().stream()
                .map(Node::getName).collect(Collectors.toSet());

        // Identify latents (type if available; fallback to name)
        List<Node> latents = new ArrayList<>();
        for (Node v : gTrue.getNodes()) {
            boolean isLatentType = false;
            try {
                isLatentType = v.getNodeType() != null &&
                               v.getNodeType().name().equalsIgnoreCase("LATENT");
            } catch (Throwable ignore) {
            }
            boolean nameLooksLatent = v.getName().startsWith("L") ||
                                      v.getName().toLowerCase().contains("latent");
            if (isLatentType || nameLooksLatent) latents.add(v);
        }

        List<Set<Node>> raw = new ArrayList<>();
        for (Node L : latents) {
            List<Node> kids = gTrue.getChildren(L);
            Set<Node> cluster = kids.stream()
                    .filter(ch -> measuredNames.contains(ch.getName()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (cluster.size() >= minSize) raw.add(cluster);
        }

        if (coalesceIdenticalChildSets) {
            Map<String, Set<Node>> uniq = new LinkedHashMap<>();
            for (Set<Node> c : raw) {
                String key = c.stream().map(Node::getName).sorted().collect(Collectors.joining(","));
                uniq.putIfAbsent(key, c);
            }
            return new ArrayList<>(uniq.values());
        } else {
            return raw;
        }
    }

    private static List<Set<Node>> maximalCliquesMeasured(Graph g, DataSet measured) {
        Set<String> meas = measured.getVariables().stream().map(Node::getName).collect(Collectors.toSet());
        Map<Node, Set<Node>> und = undirectedAdjFiltered(g, meas);
        List<Set<Node>> cliques = bronKerbosch(und);
        cliques.removeIf(c -> c.size() < 3);
        return dedupSetsByNames(cliques);
    }

    private static Map<Node, Set<Node>> undirectedAdjFiltered(Graph g, Set<String> allow) {
        Map<Node, Set<Node>> adj = new HashMap<>();
        for (Node v : g.getNodes()) if (allow.contains(v.getName())) adj.put(v, new LinkedHashSet<>());
        for (Node a : adj.keySet()) {
            for (Node b : g.getAdjacentNodes(a)) {
                if (adj.containsKey(b)) {
                    adj.get(a).add(b);
                    adj.get(b).add(a);
                }
            }
        }
        return adj;
    }

    // =================== Clique extraction (measured only) ===================

    /**
     * Batch-guarded CC: propose → filter → vet → commit.
     */
    static CCGuardResult applyCliqueCompletionWithGuards(
            Graph g,
            DataSet data,
            List<Set<Node>> trueClusters,
            CliqueCompletion cc,
            double neighborJacThr,
            int kCoreForPocket,
            double maxInterRate
    ) {
        // 1) Snapshot edges on original
        Set<String> before = undirectedEdgeSet(g);

        // 2) Run CC on a COPY (so we can diff without committing yet)
        edu.cmu.tetrad.graph.EdgeListGraph gCopy = new edu.cmu.tetrad.graph.EdgeListGraph(g);
        cc.apply(gCopy);

        // 3) Proposed = (afterCopy − beforeOriginal)
        Set<String> afterCopy = undirectedEdgeSet(gCopy);
        Set<String> proposed = new LinkedHashSet<>(afterCopy);
        proposed.removeAll(before);

        // 4) Build node map for parsing edge strings
        Map<String, Node> byName = new HashMap<>();
        for (Node v : g.getNodes()) byName.put(v.getName(), v);

        // Optional: pocket restriction via k-core on ORIGINAL graph
        Set<Node> pocketRestrict = (kCoreForPocket > 0)
                ? kCoreNodesForGraph(g, kCoreForPocket)
                : null;

        // 5) Filter by neighbor-Jaccard on ORIGINAL graph
        Set<String> kept = new LinkedHashSet<>();
        Set<String> rejected = new LinkedHashSet<>();
        for (String e : proposed) {
            String[] ab = e.split("--", -1);
            Node a = byName.get(ab[0]);
            Node b = byName.get(ab[1]);
            if (a == null || b == null) {
                rejected.add(e);
                continue;
            }

            double nj = (pocketRestrict == null)
                    ? neighborJaccardFull(g, a, b)
                    : neighborJaccardPocket(g, a, b, pocketRestrict);

            if (nj >= neighborJacThr) kept.add(e);
            else rejected.add(e);
        }

        // 6) If we have truth, estimate clutter on KEPT; otherwise skip gate
        ClutterStats clutter = (trueClusters != null) ? clutterStats(kept, trueClusters) : new ClutterStats(kept.size(), 0, 0);
        boolean pass = (trueClusters == null) || clutter.interRate() <= maxInterRate;

        // 7) Commit kept edges if pass
        if (pass) {
            for (String e : kept) {
                String[] ab = e.split("--", -1);
                Node a = byName.get(ab[0]), b = byName.get(ab[1]);
                if (!g.isAdjacentTo(a, b)) g.addUndirectedEdge(a, b);
            }
        }

        // 8) Summary
        System.out.printf(Locale.ROOT,
                "CC-Guard: proposed=%d, kept=%d, rejected=%d, interRate=%.3f, %s%n",
                proposed.size(), kept.size(), rejected.size(),
                clutter.interRate(),
                pass ? "COMMITTED" : "SKIPPED");

        return new CCGuardResult(proposed, kept, rejected, clutter);
    }

    /**
     * Count how many proposed/added edges are within the same true cluster vs across clusters.
     */
    private static ClutterStats clutterStats(Set<String> addedEdges, List<Set<Node>> trueClusters) {
        // Build var -> clusterId map (RandomMim clusters are disjoint)
        Map<String, Integer> cid = new HashMap<>();
        for (int i = 0; i < trueClusters.size(); i++) {
            for (Node n : trueClusters.get(i)) cid.put(n.getName(), i);
        }

        int intra = 0, inter = 0;
        for (String e : addedEdges) {
            String[] ab = e.split("--", -1);
            Integer ca = cid.get(ab[0]), cb = cid.get(ab[1]);
            if (ca != null && cb != null && Objects.equals(ca, cb)) intra++;
            else inter++;
        }
        return new ClutterStats(addedEdges.size(), intra, inter);
    }

    // =================== Scoring / Matching ===================

    // ---- Neighbor Jaccard helpers ----
    static double neighborJaccardFull(Graph g, Node a, Node b) {
        Set<Node> Na = new LinkedHashSet<>(g.getAdjacentNodes(a));
        Set<Node> Nb = new LinkedHashSet<>(g.getAdjacentNodes(b));
        return jaccardSets(Na, Nb);
    }

    static double neighborJaccardPocket(Graph g, Node a, Node b, Set<Node> pocket) {
        Set<Node> Na = g.getAdjacentNodes(a).stream().filter(pocket::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Node> Nb = g.getAdjacentNodes(b).stream().filter(pocket::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        return jaccardSets(Na, Nb);
    }

    // =================== Clutter diagnostics ===================

    static double jaccardSets(Set<Node> A, Set<Node> B) {
        int inter = 0;
        for (Node x : A) if (B.contains(x)) inter++;
        int union = A.size() + B.size() - inter;
        return union == 0 ? 0.0 : inter / (double) union;
    }

    // ---- Simple k-core over undirected view (on the ORIGINAL graph) ----
    static Set<Node> kCoreNodesForGraph(Graph g, int k) {
        Map<Node, Set<Node>> adj = new HashMap<>();
        for (Node v : g.getNodes()) adj.put(v, new LinkedHashSet<>());
        for (Node u : g.getNodes())
            for (Node v : g.getAdjacentNodes(u)) {
                adj.get(u).add(v);
                adj.get(v).add(u);
            }
        if (k <= 0) return adj.keySet();

        Map<Node, Integer> deg = new HashMap<>();
        for (var e : adj.entrySet()) deg.put(e.getKey(), e.getValue().size());

        Deque<Node> q = new ArrayDeque<>();
        Set<Node> keep = new LinkedHashSet<>(adj.keySet());
        for (Node v : keep) if (deg.get(v) < k) q.add(v);

        while (!q.isEmpty()) {
            Node v = q.removeFirst();
            if (!keep.remove(v)) continue;
            for (Node u : adj.getOrDefault(v, Set.of()))
                if (keep.contains(u)) {
                    deg.put(u, deg.get(u) - 1);
                    if (deg.get(u) == k - 1) q.add(u);
                }
        }
        return keep;
    }

    // Compute neighbor Jaccard on either full graph or restricted to a pocket
    private static double neighborJaccard(Graph g, Node a, Node b, Set<Node> pocketOrNull) {
        Set<Node> Na = new LinkedHashSet<>(g.getAdjacentNodes(a));
        Set<Node> Nb = new LinkedHashSet<>(g.getAdjacentNodes(b));
        if (pocketOrNull != null) {
            Na.retainAll(pocketOrNull);
            Nb.retainAll(pocketOrNull);
        }
        return jaccardSets(Na, Nb);
    }

    // Count common neighbors; optionally inside pocket
    private static int commonNeighborCount(Graph g, Node a, Node b, Set<Node> pocketOrNull) {
        Set<Node> Na = new LinkedHashSet<>(g.getAdjacentNodes(a));
        Set<Node> Nb = new LinkedHashSet<>(g.getAdjacentNodes(b));
        if (pocketOrNull != null) {
            Na.retainAll(pocketOrNull);
            Nb.retainAll(pocketOrNull);
        }
        Na.retainAll(Nb);
        return Na.size();
    }

    // Triangle support = number of c where a--c and b--c already exist
    private static int triangleSupport(Graph g, Node a, Node b, Set<Node> pocketOrNull) {
        int t = 0;
        for (Node c : g.getAdjacentNodes(a)) {
            if (pocketOrNull != null && !pocketOrNull.contains(c)) continue;
            if (g.isAdjacentTo(b, c)) t++;
        }
        return t;
    }

    // Simple k-core nodes on undirected view
    private static Set<Node> kCoreNodes(Graph g, int k) {
        Map<Node, Set<Node>> adj = new LinkedHashMap<>();
        for (Node v : g.getNodes()) adj.put(v, new LinkedHashSet<>());
        for (Node u : g.getNodes())
            for (Node v : g.getAdjacentNodes(u)) {
                adj.get(u).add(v);
                adj.get(v).add(u);
            }
        if (k <= 0) return adj.keySet();
        Map<Node, Integer> deg = new HashMap<>();
        for (var e : adj.entrySet()) deg.put(e.getKey(), e.getValue().size());
        Deque<Node> q = new ArrayDeque<>();
        Set<Node> keep = new LinkedHashSet<>(adj.keySet());
        for (Node v : keep) if (deg.get(v) < k) q.add(v);
        while (!q.isEmpty()) {
            Node v = q.removeFirst();
            if (!keep.remove(v)) continue;
            for (Node u : adj.getOrDefault(v, Set.of()))
                if (keep.contains(u)) {
                    deg.put(u, deg.get(u) - 1);
                    if (deg.get(u) == k - 1) q.add(u);
                }
        }
        return keep;
    }

    /**
     * Absolute (marginal) correlation as a cheap proxy for strength.
     */
    private static double absCorr(DataSet data, Node a, Node b) {
        CorrelationMatrix cm = new CorrelationMatrix(data);
        int ia = data.getColumn(data.getVariable(a.getName()));
        int ib = data.getColumn(data.getVariable(b.getName()));
        return Math.abs(cm.getValue(ia, ib));
    }

    private static double fisherZP(DataSet data, Node a, Node b) {
        // alpha unused here; we only read a single p-value
        IndTestFisherZ test = new IndTestFisherZ(data, 0.5);
        return test.getPValue(a, b, Collections.emptySet());
    }

    /**
     * Truth-free guard: keep only edges that look intra-cluster by local structure/strength.
     */
    static void applyCliqueCompletionWithHeuristics(
            Graph g,
            DataSet data,
            CliqueCompletion cc,
            HeurParams hp
    ) {
        // Snapshot before
        Set<String> before = undirectedEdgeSet(g);

        // Run CC on a copy
        edu.cmu.tetrad.graph.EdgeListGraph gCopy = new edu.cmu.tetrad.graph.EdgeListGraph(g);
        cc.apply(gCopy);

        // Proposed additions = afterCopy − before
        Set<String> afterCopy = undirectedEdgeSet(gCopy);
        List<String> proposed = new ArrayList<>(afterCopy);
        proposed.removeAll(before);

        // Build name→node map
        Map<String, Node> byName = new HashMap<>();
        for (Node v : g.getNodes()) byName.put(v.getName(), v);

        // Pocket restriction (optional)
        Set<Node> pocket = (hp.kCorePocket > 0) ? kCoreNodes(g, hp.kCorePocket) : null;

        // Per-node budget
        Map<String, Integer> addedByNode = new HashMap<>();

        // Score and filter
        final class ScoredEdge {
            final String key;
            final Node a, b;
            final double score;

            ScoredEdge(String k, Node a, Node b, double s) {
                this.key = k;
                this.a = a;
                this.b = b;
                this.score = s;
            }
        }
        List<ScoredEdge> kept = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (String e : proposed) {
            String[] ab = e.split("--", -1);
            Node a = byName.get(ab[0]), b = byName.get(ab[1]);
            if (a == null || b == null) {
                rejected.add(e);
                continue;
            }

            // Structural gates
            double nj = neighborJaccard(g, a, b, pocket);
            if (nj < hp.minNeighborJaccard) {
                rejected.add(e);
                continue;
            }

            int cn = commonNeighborCount(g, a, b, pocket);
            if (cn < hp.minCommonNeighbors) {
                rejected.add(e);
                continue;
            }

            int tri = triangleSupport(g, a, b, pocket);
            if (tri < hp.minTriangleSupport) {
                rejected.add(e);
                continue;
            }

            // Strength gate (choose the one you configured)
            double strength01 = 0.0;
            boolean strengthOK = true;
            if (hp.minAbsPartialCorr != null) {
                double rAbs = absCorr(data, a, b); // fast proxy
                strength01 = Math.min(1.0, Math.max(0.0, rAbs)); // already in [0,1]
                if (rAbs < hp.minAbsPartialCorr) {
                    rejected.add(e);
                    continue;
                }
            } else if (hp.maxFisherZP != null) {
                double p = fisherZP(data, a, b);
                // map p to [0,1] “strength”: smaller p ⇒ larger score
                strength01 = Math.min(1.0, Math.max(0.0, 1.0 - Math.min(1.0, p / hp.maxFisherZP)));
                if (p > hp.maxFisherZP) {
                    rejected.add(e);
                    continue;
                }
            }

            // Composite score in [0,1]
            // Normalize triangle support by (cn) to avoid bias: tri<=cn
            double tri01 = (cn == 0) ? 0.0 : (tri / (double) cn);
            double score = hp.wJ * nj + hp.wTri * tri01 + hp.wStr * strength01;
            if (score < hp.minCompositeScore) {
                rejected.add(e);
                continue;
            }

            kept.add(new ScoredEdge(e, a, b, score));
        }

        // Prefer stronger edges first, throttle per node to avoid over-densifying
        kept.sort((u, v) -> Double.compare(v.score, u.score));
        int committed = 0;
        for (ScoredEdge se : kept) {
            int ba = addedByNode.getOrDefault(se.a.getName(), 0);
            int bb = addedByNode.getOrDefault(se.b.getName(), 0);
            if (ba >= hp.maxAddsPerNode || bb >= hp.maxAddsPerNode) continue;
            if (!g.isAdjacentTo(se.a, se.b)) {
                g.addUndirectedEdge(se.a, se.b);
                addedByNode.put(se.a.getName(), ba + 1);
                addedByNode.put(se.b.getName(), bb + 1);
                committed++;
            }
        }

        System.out.printf(Locale.ROOT,
                "CC-Heur: proposed=%d, passedFilters=%d, committed=%d (per-node cap=%d)%n",
                proposed.size(), kept.size(), committed, hp.maxAddsPerNode);
    }

    /**
     * Greedy Jaccard matching of true clusters to recovered cliques.
     */
    private static RecoveryStats scoreRecovery(List<Set<Node>> trueClusters, List<Set<Node>> recoveredCliques) {
        final double JACCARD_MIN = 0.0;

        List<LinkedHashSet<String>> T = trueClusters.stream()
                .map(s -> s.stream().map(Node::getName).collect(Collectors.toCollection(LinkedHashSet::new)))
                .toList();
        List<LinkedHashSet<String>> R = recoveredCliques.stream()
                .map(s -> s.stream().map(Node::getName).collect(Collectors.toCollection(LinkedHashSet::new)))
                .toList();

        List<Integer> unmatchedR = new ArrayList<>();
        for (int i = 0; i < R.size(); i++) unmatchedR.add(i);

        List<PerCluster> pcs = new ArrayList<>();
        double sumP = 0, sumR = 0, sumF1 = 0, sumJ = 0;

        long TP = 0, FP = 0, FN = 0;

        for (int ti = 0; ti < T.size(); ti++) {
            Set<String> t = T.get(ti);
            int bestR = -1;
            double bestJ = -1.0;
            for (int rIdx : unmatchedR) {
                double j = jaccard(t, R.get(rIdx));
                if (j > bestJ) {
                    bestJ = j;
                    bestR = rIdx;
                }
            }

            Set<Node> tNodes = namesToNodes(t, trueClusters.get(ti));
            Set<Node> rNodes = null;
            if (bestR >= 0 && bestJ > JACCARD_MIN) {
                rNodes = namesToNodes(R.get(bestR), recoveredCliques.get(bestR));
                unmatchedR.remove((Integer) bestR);
            }

            PerCluster pc = new PerCluster(tNodes, rNodes);
            pcs.add(pc);

            sumP += pc.prec;
            sumR += pc.recall;
            sumF1 += pc.f1;
            sumJ += pc.jaccard;

            TP += pc.inter;
            FP += Math.max(0, pc.rSize - pc.inter);
            FN += Math.max(0, pc.tSize - pc.inter);
        }

        int K = Math.max(1, pcs.size());
        double mP = sumP / K, mR = sumR / K, mF1 = sumF1 / K;
        double mJ = sumJ / K;

        double miP = (TP + FP) == 0 ? 0.0 : TP / (double) (TP + FP);
        double miR = (TP + FN) == 0 ? 0.0 : TP / (double) (TP + FN);
        double miF1 = (miP + miR == 0.0) ? 0.0 : 2 * miP * miR / (miP + miR);

        return new RecoveryStats(mP, mR, mF1, mJ, sumJ, miP, miR, miF1, pcs);
    }

//    // Jaccard over node sets
//    private static double jaccardSets(Set<Node> A, Set<Node> B) {
//        int inter = 0; for (Node x : A) if (B.contains(x)) inter++;
//        int union = A.size() + B.size() - inter;
//        return union == 0 ? 0.0 : inter / (double) union;
//    }

    // Quick partial-corr via 3×3..5×5 inversion for {a,b|S}, but we’ll use S=∅ for the guard.

    private static void printDeltaTable(Leaderboard lb) {
        Map<String, RecoveryStats> raw = new LinkedHashMap<>();
        Map<String, RecoveryStats> cc = new LinkedHashMap<>();

        for (var row : lb.rows) {
            String label = row.methodLabel;
            if (label.endsWith(" (raw)")) {
                raw.put(label.substring(0, label.length() - " (raw)".length()), row.stats);
            } else if (label.endsWith(" (+CC)")) {
                cc.put(label.substring(0, label.length() - " (+CC)".length()), row.stats);
            }
        }

        System.out.println("\n=== Δ from CliqueCompletion (matched methods) ===");
        List<String> keys = new ArrayList<>(raw.keySet());
        keys.retainAll(cc.keySet());
        keys.sort(String::compareTo);

        for (String k : keys) {
            RecoveryStats r = raw.get(k), c = cc.get(k);
            double dSumJ = c.sumJaccard - r.sumJaccard;
            double dMacF1 = c.macroF1 - r.macroF1;
            double dMicF1 = c.microF1 - r.microF1;
            System.out.printf(Locale.ROOT,
                    "%-26s  ΔsumJ=%.3f  ΔmacroF1=%.3f  ΔmicroF1=%.3f%n",
                    k, dSumJ, dMacF1, dMicF1);
        }
    }

    private static void printComponents(Graph g) {
        List<Set<Node>> comps = connectedComponents(g);
        System.out.println("Connected components (" + comps.size() + "): "
                           + comps.stream().map(TestCliqueCompletionIntegration::names).collect(Collectors.toList()));
    }

    /** Truth-free guard: keep only edges that look intra-cluster by local structure/strength. */
//    static void applyCliqueCompletionWithHeuristics(
//            Graph g,
//            DataSet data,
//            CliqueCompletion cc,
//            HeurParams hp
//    ) {
//        // Snapshot before
//        Set<String> before = undirectedEdgeSet(g);
//
//        // Run CC on a copy
//        edu.cmu.tetrad.graph.EdgeListGraph gCopy = new edu.cmu.tetrad.graph.EdgeListGraph(g);
//        cc.apply(gCopy);
//
//        // Proposed additions = afterCopy − before
//        Set<String> afterCopy = undirectedEdgeSet(gCopy);
//        List<String> proposed = new ArrayList<>(afterCopy);
//        proposed.removeAll(before);
//
//        // Build name→node map
//        Map<String, Node> byName = new HashMap<>();
//        for (Node v : g.getNodes()) byName.put(v.getName(), v);
//
//        // Pocket restriction (optional)
//        Set<Node> pocket = (hp.kCorePocket > 0) ? kCoreNodes(g, hp.kCorePocket) : null;
//
//        // Per-node budget
//        Map<String,Integer> addedByNode = new HashMap<>();
//
//        // Score and filter
//        final class ScoredEdge {
//            final String key; final Node a,b; final double score;
//            ScoredEdge(String k, Node a, Node b, double s){ this.key=k; this.a=a; this.b=b; this.score=s; }
//        }
//        List<ScoredEdge> kept = new ArrayList<>();
//        List<String> rejected = new ArrayList<>();
//
//        for (String e : proposed) {
//            String[] ab = e.split("--", -1);
//            Node a = byName.get(ab[0]), b = byName.get(ab[1]);
//            if (a == null || b == null) { rejected.add(e); continue; }
//
//            // Structural gates
//            double nj = neighborJaccard(g, a, b, pocket);
//            if (nj < hp.minNeighborJaccard) { rejected.add(e); continue; }
//
//            int cn = commonNeighborCount(g, a, b, pocket);
//            if (cn < hp.minCommonNeighbors) { rejected.add(e); continue; }
//
//            int tri = triangleSupport(g, a, b, pocket);
//            if (tri < hp.minTriangleSupport) { rejected.add(e); continue; }
//
//            // Strength gate (choose the one you configured)
//            double strength01 = 0.0;
//            boolean strengthOK = true;
//            if (hp.minAbsPartialCorr != null) {
//                double rAbs = absCorr(data, a, b); // fast proxy
//                strength01 = Math.min(1.0, Math.max(0.0, rAbs)); // already in [0,1]
//                if (rAbs < hp.minAbsPartialCorr) { rejected.add(e); continue; }
//            } else if (hp.maxFisherZP != null) {
//                double p = fisherZP(data, a, b);
//                // map p to [0,1] “strength”: smaller p ⇒ larger score
//                strength01 = Math.min(1.0, Math.max(0.0, 1.0 - Math.min(1.0, p / hp.maxFisherZP)));
//                if (p > hp.maxFisherZP) { rejected.add(e); continue; }
//            }
//
//            // Composite score in [0,1]
//            // Normalize triangle support by (cn) to avoid bias: tri<=cn
//            double tri01 = (cn == 0) ? 0.0 : (tri / (double) cn);
//            double score = hp.wJ * nj + hp.wTri * tri01 + hp.wStr * strength01;
//            if (score < hp.minCompositeScore) { rejected.add(e); continue; }
//
//            kept.add(new ScoredEdge(e, a, b, score));
//        }
//
//        // Prefer stronger edges first, throttle per node to avoid over-densifying
//        kept.sort((u,v) -> Double.compare(v.score, u.score));
//        int committed = 0;
//        for (ScoredEdge se : kept) {
//            int ba = addedByNode.getOrDefault(se.a.getName(), 0);
//            int bb = addedByNode.getOrDefault(se.b.getName(), 0);
//            if (ba >= hp.maxAddsPerNode || bb >= hp.maxAddsPerNode) continue;
//            if (!g.isAdjacentTo(se.a, se.b)) {
//                g.addUndirectedEdge(se.a, se.b);
//                addedByNode.put(se.a.getName(), ba+1);
//                addedByNode.put(se.b.getName(), bb+1);
//                committed++;
//            }
//        }
//
//        System.out.printf(Locale.ROOT,
//                "CC-Heur: proposed=%d, passedFilters=%d, committed=%d (per-node cap=%d)%n",
//                proposed.size(), kept.size(), committed, hp.maxAddsPerNode);
//    }

    private static void printCliques(List<Set<Node>> cliques) {
        cliques.sort((a, b) -> {
            int cmp = Integer.compare(b.size(), a.size());
            if (cmp != 0) return cmp;
            return names(a).compareTo(names(b));
        });
        System.out.println("Maximal cliques (" + cliques.size() + "): " +
                           cliques.stream().map(TestCliqueCompletionIntegration::namesWithSize).collect(Collectors.toList()));
    }

    private static Set<String> undirectedEdgeSet(Graph g) {
        Set<String> s = new LinkedHashSet<>();
        for (Node a : g.getNodes()) {
            for (Node b : g.getAdjacentNodes(a)) {
                if (a.getName().compareTo(b.getName()) < 0) {
                    s.add(a.getName() + "--" + b.getName());
                }
            }
        }
        return s;
    }

    // -------- metrics aggregation

    private static Map<Node, Set<Node>> undirectedAdj(Graph g) {
        Map<Node, Set<Node>> adj = new HashMap<>();
        for (Node v : g.getNodes()) adj.put(v, new LinkedHashSet<>());
        for (Node a : g.getNodes()) {
            for (Node b : g.getAdjacentNodes(a)) {
                adj.get(a).add(b);
                adj.get(b).add(a);
            }
        }
        return adj;
    }

    private static List<Set<Node>> connectedComponents(Graph g) {
        Map<Node, Set<Node>> adj = undirectedAdj(g);
        Set<Node> un = new LinkedHashSet<>(adj.keySet());
        List<Set<Node>> comps = new ArrayList<>();
        while (!un.isEmpty()) {
            Node s = un.iterator().next();
            Set<Node> comp = new LinkedHashSet<>();
            Deque<Node> dq = new ArrayDeque<>();
            dq.add(s);
            un.remove(s);
            while (!dq.isEmpty()) {
                Node v = dq.removeFirst();
                comp.add(v);
                for (Node w : adj.get(v)) if (un.remove(w)) dq.add(w);
            }
            comps.add(comp);
        }
        return comps;
    }

    // Merge two near-duplicate cliques if they overlap in n-1 nodes by testing the lone fringe pair.
    static int mergeNearCliques(Graph g,
                                List<Set<Node>> cliques,
                                CliqueCompletion.EdgeTester tester,
                                double alpha, int maxOrder) {
        Map<String, Node> byName = new HashMap<>();
        for (Node v : g.getNodes()) byName.put(v.getName(), v);

        cliques.sort((a, b) -> Integer.compare(b.size(), a.size()));

        int added = 0;
        for (int i = 0; i < cliques.size(); i++) {
            Set<Node> A = cliques.get(i);
            for (int j = i + 1; j < cliques.size(); j++) {
                Set<Node> B = cliques.get(j);
                if (A.size() != B.size()) break;

                Set<Node> inter = new LinkedHashSet<>(A);
                inter.retainAll(B);
                if (inter.size() < A.size() - 1) continue;

                Set<Node> diffA = new LinkedHashSet<>(A);
                diffA.removeAll(B);
                Set<Node> diffB = new LinkedHashSet<>(B);
                diffB.removeAll(A);
                if (diffA.size() != 1 || diffB.size() != 1) continue;

                Node a = diffA.iterator().next();
                Node b = diffB.iterator().next();
                if (g.isAdjacentTo(a, b)) continue;

                List<Set<Node>> fam = new ArrayList<>();
                fam.add(Collections.emptySet());
                if (maxOrder >= 1) {
                    int c1 = 0;
                    for (Node c : inter) {
                        fam.add(Set.of(c));
                        if (++c1 >= 3) break;
                    }
                }
                if (maxOrder >= 2) {
                    List<Node> L = new ArrayList<>(inter);
                    int c2 = 0;
                    for (int u = 0; u < L.size() && c2 < 3; u++)
                        for (int v = u + 1; v < L.size() && c2 < 3; v++, c2++)
                            fam.add(Set.of(L.get(u), L.get(v)));
                }

                double pmin = 1.0;
                for (Set<Node> S : fam) {
                    double p = tester.pValue(a, b, S);
                    if (p < pmin) pmin = p;
                }

                if (pmin <= alpha) {
                    g.addUndirectedEdge(a, b);
                    added++;
                    System.out.printf(Locale.ROOT,
                            "NearCliqueMerge: add %s--%s (|∩|=%d, p=%.3g)%n",
                            a.getName(), b.getName(), inter.size(), pmin);
                }
            }
        }
        return added;
    }

    // =================== Leaderboard ===================

    private static void debugClusterSeeds(Graph g, DataSet data, List<Set<Node>> trueClusters) {
        Map<String, Node> byName = g.getNodes().stream().collect(Collectors.toMap(Node::getName, n -> n));
        double[][] corr = new CorrelationMatrix(data).getMatrix().toArray();

        System.out.println("---- Seed diagnostics (per true cluster) ----");
        for (Set<Node> T : trueClusters) {
            List<Node> L = new ArrayList<>(T);
            int tri = 0;
            for (int i = 0; i < L.size(); i++)
                for (int j = i + 1; j < L.size(); j++)
                    for (int k = j + 1; k < L.size(); k++) {
                        Node a = L.get(i), b = L.get(j), c = L.get(k);
                        if (g.isAdjacentTo(a, b) && g.isAdjacentTo(a, c) && g.isAdjacentTo(b, c)) tri++;
                    }
            double sumAbsR = 0;
            int cnt = 0;
            for (int i = 0; i < L.size(); i++)
                for (int j = i + 1; j < L.size(); j++) {
                    int ia = data.getColumn(data.getVariable(L.get(i).getName()));
                    int ib = data.getColumn(data.getVariable(L.get(j).getName()));
                    sumAbsR += Math.abs(corr[ia][ib]);
                    cnt++;
                }
            System.out.printf(Locale.ROOT,
                    "Cluster {%s}: triangles=%d, avg|r|=%.3f%n",
                    names(T), tri, (cnt == 0 ? 0.0 : sumAbsR / cnt));
        }
    }

    private static List<Set<Node>> bronKerbosch(Map<Node, Set<Node>> adj) {
        List<Set<Node>> out = new ArrayList<>();
        bronRec(new LinkedHashSet<>(), new LinkedHashSet<>(adj.keySet()), new LinkedHashSet<>(), adj, out);
        return out;
    }

    private static void bronRec(Set<Node> R, Set<Node> P, Set<Node> X,
                                Map<Node, Set<Node>> adj, List<Set<Node>> out) {
        if (P.isEmpty() && X.isEmpty()) {
            if (!R.isEmpty()) out.add(new LinkedHashSet<>(R));
            return;
        }
        List<Node> iter = new ArrayList<>(P);
        for (Node v : iter) {
            Set<Node> Nv = adj.getOrDefault(v, Collections.emptySet());
            Set<Node> Rv = new LinkedHashSet<>(R);
            Rv.add(v);
            Set<Node> Pv = P.stream().filter(Nv::contains).collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Node> Xv = X.stream().filter(Nv::contains).collect(Collectors.toCollection(LinkedHashSet::new));
            bronRec(Rv, Pv, Xv, adj, out);
            P.remove(v);
            X.add(v);
        }
    }

    private static List<Set<Node>> dedupSetsByNames(List<Set<Node>> sets) {
        Map<String, Set<Node>> uniq = new LinkedHashMap<>();
        for (Set<Node> s : sets) {
            String key = names(s);
            uniq.put(key, s);
        }
        return new ArrayList<>(uniq.values());
    }

    // =================== Plumbing / Utilities ===================

    private static int intersectSize(Set<Node> a, Set<Node> b) {
        int k = 0;
        for (Node x : a) if (b.contains(x)) k++;
        return k;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int inter = 0;
        for (String s : a) if (b.contains(s)) inter++;
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0.0 : inter / (double) union;
    }

    private static Set<Node> namesToNodes(Set<String> names, Set<Node> exemplarPool) {
        Map<String, Node> map = exemplarPool.stream().collect(Collectors.toMap(Node::getName, n -> n));
        Set<Node> out = new LinkedHashSet<>();
        for (String s : names) {
            Node n = map.get(s);
            out.add(n != null ? n : new edu.cmu.tetrad.graph.GraphNode(s));
        }
        return out;
    }

    private static String names(Collection<Node> nodes) {
        return nodes.stream().map(Node::getName).sorted().collect(Collectors.joining(","));
    }

    private static String namesWithSize(Collection<Node> nodes) {
        return names(nodes) + " [k=" + nodes.size() + "]";
    }

    @Test
    public void testLearnersCliquesAndClusterRecovery() {
        final int N = 10000;
        final int R = 1; // number of random seeds

        LBMulti multi = new LBMulti();

        for (int r = 0; r < R; r++) {
            long seed = RandomUtil.getInstance().nextLong();

            MimData md = makeMimData(N, seed);
            assertNotNull(md.data());
            assertFalse("No true clusters found", md.trueClusters().isEmpty());

            // --- Build learners for THIS seed's data ---
            Map<String, Supplier<Graph>> learners = new LinkedHashMap<>();

            for (double _penalty : new double[]{0.8, 1.0, 1.5, 2.0}) {
                learners.put("BOSS_SEMBIC_penalty_" + _penalty, () -> {
                    SemBicScore score = new SemBicScore(new CorrelationMatrix(md.data()));
                    score.setPenaltyDiscount(_penalty);
                    try {
                        return new PermutationSearch(new Boss(score)).search();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            for (double _alpha : new double[]{0.01, 0.05, 0.1, 0.2}) {
                learners.put("PC_FisherZ_alpha_" + _alpha, () -> {
                    IndependenceTest test = new IndTestFisherZ(md.data(), _alpha);
                    Pc pc = new Pc(test);
                    try {
                        return pc.search();
                    } catch (InterruptedException e1) {
                        throw new RuntimeException(e1);
                    }
                });
            }

            for (double _alpha : new double[]{0.01, 0.05, 0.1, 0.2}) {
                learners.put("PC_Rank_Wilkes_alpha_" + _alpha, () -> {
                    Parameters params = new Parameters();
                    params.set(Params.ALPHA, _alpha);

                    IndependenceTest test = new RankIndependenceTest().getTest(md.data, new Parameters());
                    Pc pc = new Pc(test);
                    try {
                        return pc.search();
                    } catch (InterruptedException e1) {
                        throw new RuntimeException(e1);
                    }
                });
            }

            for (double _penalty : new double[]{0.8, 1.0, 1.5, 2.0}) {
                learners.put("FGES_SEMBIC_penalty_" + _penalty, () -> {
                    SemBicScore score = new SemBicScore(new CorrelationMatrix(md.data()));
                    score.setPenaltyDiscount(_penalty);
                    try {
                        Fges ges = new Fges(score);
                        return ges.search();
                    } catch (InterruptedException e2) {
                        throw new RuntimeException(e2);
                    }
                });
            }

            {
                learners.put("TSC", () -> {
                    BlockSpec spec = BlockDiscoverers.tsc(md.data, 0.05, -1, 1e-8, 2, SingleClusterPolicy.EXCLUDE,
                            0, false).discover();

                    List<Set<Node>> cliquesRaw = new ArrayList<>();
                    List<Node> variables = spec.dataSet().getVariables();

                    for (List<Integer> cluster : spec.blocks()) {
                        Set<Node> nodes = new HashSet<>();
                        for (int i : cluster) {
                            nodes.add(variables.get(i));
                        }
                        cliquesRaw.add(nodes);
                    }

                    System.out.println("TSC cliques = " + cliquesRaw);

                    Graph out = new EdgeListGraph(variables);

                    for (Set<Node> clique : cliquesRaw) {
                        List<Node> _clique = new ArrayList<>(clique);

                        for (int i = 0; i < _clique.size(); i++) {
                            for (int j = i + 1; j < _clique.size(); j++) {
                                out.addUndirectedEdge(_clique.get(i), _clique.get(j));
                            }
                        }
                    }

                    return out;
                });
            }

            // --- Per-seed leaderboard ---
            Leaderboard lb = new Leaderboard();

            for (Map.Entry<String, Supplier<Graph>> e : learners.entrySet()) {
                String name = e.getKey();

                // Baseline
                Graph g = e.getValue().get();
                List<Set<Node>> cliquesRaw = maximalCliquesMeasured(g, md.data());

                System.out.println(name + ": raw cliques = " + cliquesRaw);

                SimpleMatrix S = new CorrelationMatrix(md.data()).getMatrix().getSimpleMatrix();

                System.out.println("Rank 1 cliques = ");

                List<Integer> all = new  ArrayList<>();
                for (int i = 0; i < md.data.getVariables().size(); i++) {
                    all.add(i);
                }

                List<Set<Node>> filteredCliques = new ArrayList<>();
                
                for (Set<Node> clique : cliquesRaw) {
                    List<Integer> indices = new ArrayList<>();
                    for (Node node : clique) {
                        indices.add(md.data().getColumn(node));
                    }

                    List<Integer> remaining =  new  ArrayList<>(all);
                    remaining.removeAll(indices);

                    int[] _indices = indices.stream().mapToInt(Integer::intValue).toArray();
                    int[] _remaining = remaining.stream().mapToInt(Integer::intValue).toArray();

                    double rank = RankTests.estimateWilksRank(S, _indices, _remaining, N, 0.001);

                    if (rank == 1) {
                        System.out.println(clique);
                        filteredCliques.add(new HashSet<>(clique));
                    }
                }

                RecoveryStats base = scoreRecovery(md.trueClusters(), filteredCliques);
                lb.add(name + " (raw)", base);

                // Completion (guarded)
                CliqueCompletion cc = CliqueCompletion.newBuilder(new IndTestFisherZ(md.data(), 0.01))
                        .maxCompletionOrder(2)
                        .intraAlpha(0.025)
                        .kCore(3)
                        .minCommonNeighbors(1)
                        .enableTriangleCompletion(true)
                        .enableDenseCoreRetest(true)
                        .log(false)
                        .build();

//                CCGuardResult res = applyCliqueCompletionWithGuards(
//                        g, md.data(), md.trueClusters(),
//                        cc,
//                        0.60, // neighbor Jaccard threshold
//                        4,    // k-core pocket
//                        0.05  // max inter rate allowed
//                );

                HeurParams hp = new HeurParams();
                // tweak if needed:
                // hp.kCorePocket = 4;
                // hp.minNeighborJaccard = 0.55;
                // hp.minCommonNeighbors = 2;
                // hp.minTriangleSupport = 2;
                // hp.minAbsPartialCorr = 0.12;  // or set to null and use hp.maxFisherZP = 0.05;
                // hp.minCompositeScore = 0.58;
                // hp.maxAddsPerNode = 2;

                applyCliqueCompletionWithHeuristics(g, md.data(), cc, hp);

                List<Set<Node>> cliquesCC = maximalCliquesMeasured(g, md.data());
                RecoveryStats after = scoreRecovery(md.trueClusters(), cliquesCC);
                lb.add(name + " (+CC)", after);
            }

            // Fold this seed's results into the multi-seed aggregator
            for (Leaderboard.Row row : lb.rows) {
                multi.add(row.methodLabel, row.stats);
            }
        }

        // --- Final multi-seed tables ---
        multi.printBySumJ();     // mean±sd by SUM Jaccard
        multi.printByMacroF1();  // mean±sd by Macro F1
        multi.printDeltas();     // Δ (CC – raw) per method family
    }

    private record MimData(DataSet data, Graph trueGraph, List<Set<Node>> trueClusters) {
    }

    static final class CCGuardResult {
        final Set<String> proposed, kept, rejected;
        final ClutterStats clutterKept;
        final double interRateKept;

        CCGuardResult(Set<String> proposed, Set<String> kept, Set<String> rejected, ClutterStats clutterKept) {
            this.proposed = proposed;
            this.kept = kept;
            this.rejected = rejected;
            this.clutterKept = clutterKept;
            this.interRateKept = clutterKept.interRate();
        }
    }

    private static final class ClutterStats {
        final int total, intra, inter;

        ClutterStats(int total, int intra, int inter) {
            this.total = total;
            this.intra = intra;
            this.inter = inter;
        }

        double interRate() {
            return total == 0 ? 0.0 : inter / (double) total;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "total=%d intra=%d inter=%d (interRate=%.3f)",
                    total, intra, inter, interRate());
        }
    }

    // ---- Truth-free heuristic guard (config) ----
    private static final class HeurParams {
        // structural
        int kCorePocket = 3;          // compute features inside k-core (0 = whole graph)
        double minNeighborJaccard = 0.50;
        int minCommonNeighbors = 2;   // raw |N(a) ∩ N(b)| threshold
        int minTriangleSupport = 2;   // how many triangles a--c--b are closed

        // strength: choose ONE of these two gates (set the other to "disabled")
        Double minAbsPartialCorr = 0.10; // e.g., 0.10..0.20; set null to disable
        Double maxFisherZP = null;       // e.g., 0.05; set null to disable

        // composite-score acceptance (optional)
        double wJ = 0.5, wTri = 0.3, wStr = 0.2;  // weights for score ∈ [0,1]
        double minCompositeScore = 0.55;

        // per-node throttling to avoid over-densifying hubs
        int maxAddsPerNode = 2;
    }

    private static class RecoveryStats {
        final double macroPrec, macroRec, macroF1;
        final double macroJaccard, sumJaccard;
        final double microPrec, microRec, microF1;
        final List<PerCluster> perCluster;

        RecoveryStats(double mP, double mR, double mF1,
                      double mJ, double sJ,
                      double miP, double miR, double miF1,
                      List<PerCluster> pcs) {
            macroPrec = mP;
            macroRec = mR;
            macroF1 = mF1;
            macroJaccard = mJ;
            sumJaccard = sJ;
            microPrec = miP;
            microRec = miR;
            microF1 = miF1;
            perCluster = pcs;
        }
    }

    private static class PerCluster {
        final String truth, match;
        final int tSize, rSize, inter;
        final double prec, recall, f1, jaccard;

        PerCluster(Set<Node> T, Set<Node> R) {
            this.truth = names(T);
            this.match = (R == null ? "∅" : names(R));
            this.tSize = T.size();
            this.rSize = (R == null ? 0 : R.size());
            this.inter = (R == null ? 0 : intersectSize(T, R));
            this.prec = (R == null || rSize == 0) ? 0.0 : inter / (double) rSize;
            this.recall = tSize == 0 ? 0.0 : inter / (double) tSize;
            this.f1 = (prec + recall == 0.0) ? 0.0 : 2 * prec * recall / (prec + recall);
            int union = tSize + rSize - inter;
            this.jaccard = union == 0 ? 0.0 : inter / (double) union;
        }
    }

    private static final class Leaderboard {
        final List<Row> rows = new ArrayList<>();

        void add(String label, RecoveryStats stats) {
            rows.add(new Row(label, stats));
        }

        void printBySumJaccard() {
            rows.sort((a, b) -> Double.compare(b.sumJaccard, a.sumJaccard));
            System.out.println("\n=== Leaderboard (by SUM Jaccard; higher is better) ===");
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                System.out.printf(Locale.ROOT, "%2d) %-28s  sumJ=%.3f  macroJ=%.3f  macroF1=%.3f  microF1=%.3f%n",
                        i + 1, r.methodLabel, r.sumJaccard, r.macroJaccard, r.macroF1, r.microF1);
            }
        }

        void printByMacroF1() {
            rows.sort((a, b) -> Double.compare(b.macroF1, a.macroF1));
            System.out.println("\n=== Leaderboard (by MACRO F1; higher is better) ===");
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                System.out.printf(Locale.ROOT, "%2d) %-28s  macroF1=%.3f  sumJ=%.3f  macroJ=%.3f  microF1=%.3f%n",
                        i + 1, r.methodLabel, r.macroF1, r.sumJaccard, r.macroJaccard, r.microF1);
            }
        }

        static final class Row {
            final String methodLabel;
            final double sumJaccard, macroJaccard, macroF1, microF1;
            final RecoveryStats stats;

            Row(String label, RecoveryStats s) {
                this.methodLabel = label;
                this.sumJaccard = s.sumJaccard;
                this.macroJaccard = s.macroJaccard;
                this.macroF1 = s.macroF1;
                this.microF1 = s.microF1;
                this.stats = s;
            }
        }
    }

    // Tiny accumulator
    private static final class Agg {
        double n = 0, sum = 0, sum2 = 0;

        void add(double x) {
            n++;
            sum += x;
            sum2 += x * x;
        }

        double mean() {
            return n == 0 ? 0.0 : sum / n;
        }

        double var() {
            if (n <= 1) return 0.0;
            double m = mean();
            double v = (sum2 / n) - m * m;
            return Math.max(0.0, v);
        }

        double sd() {
            return Math.sqrt(var());
        }

        int count() {
            return (int) Math.round(n);
        }
    }

    // Aggregate leaderboard across seeds
    private static final class LBMulti {
        private final Map<String, Cell> map = new LinkedHashMap<>();

        void add(String label, RecoveryStats s) {
            Cell c = map.computeIfAbsent(label, k -> new Cell());
            c.sumJ.add(s.sumJaccard);
            c.MJ.add(s.macroJaccard);
            c.mF1.add(s.macroF1);
            c.micF1.add(s.microF1);
        }

        void printBySumJ() {
            List<Map.Entry<String, Cell>> L = new ArrayList<>(map.entrySet());
            L.sort((a, b) -> Double.compare(b.getValue().sumJ.mean(), a.getValue().sumJ.mean()));
            System.out.println("\n=== Leaderboard (mean±sd over seeds; by SUM Jaccard) ===");
            for (int i = 0; i < L.size(); i++) {
                var e = L.get(i);
                var c = e.getValue();
                System.out.printf(Locale.ROOT,
                        "%2d) %-28s  sumJ=%.3f±%.3f  macroF1=%.3f±%.3f  microF1=%.3f±%.3f%n",
                        i + 1, e.getKey(), c.sumJ.mean(), c.sumJ.sd(), c.mF1.mean(), c.mF1.sd(),
                        c.micF1.mean(), c.micF1.sd());
            }
        }

        void printByMacroF1() {
            List<Map.Entry<String, Cell>> L = new ArrayList<>(map.entrySet());
            L.sort((a, b) -> Double.compare(b.getValue().mF1.mean(), a.getValue().mF1.mean()));
            System.out.println("\n=== Leaderboard (mean±sd over seeds; by MACRO F1) ===");
            for (int i = 0; i < L.size(); i++) {
                var e = L.get(i);
                var c = e.getValue();
                System.out.printf(Locale.ROOT,
                        "%2d) %-28s  macroF1=%.3f±%.3f  sumJ=%.3f±%.3f  microF1=%.3f±%.3f%n",
                        i + 1, e.getKey(), c.mF1.mean(), c.mF1.sd(), c.sumJ.mean(), c.sumJ.sd(),
                        c.micF1.mean(), c.micF1.sd());
            }
        }

        /**
         * Print mean±sd deltas (CC – raw) for each method prefix.
         */
        void printDeltas() {
            System.out.println("\n=== Δ from CliqueCompletion (mean±sd; CC – raw) ===");

            // Group labels by family prefix: "<name> (raw)" paired with "<name> (+CC)"
            Map<String, Cell> raw = new LinkedHashMap<>();
            Map<String, Cell> cc = new LinkedHashMap<>();
            for (String label : map.keySet()) {
                if (label.endsWith("(raw)")) {
                    raw.put(label.substring(0, label.length() - "(raw)".length()).trim(), map.get(label));
                } else if (label.endsWith("(+CC)")) {
                    cc.put(label.substring(0, label.length() - "(+CC)".length()).trim(), map.get(label));
                }
            }

            List<String> keys = new ArrayList<>(raw.keySet());
            keys.retainAll(cc.keySet());
            Collections.sort(keys);

            for (String k : keys) {
                Cell r = raw.get(k), c = cc.get(k);

                double dSumJ_m = c.sumJ.mean() - r.sumJ.mean();
                double dMacroF1_m = c.mF1.mean() - r.mF1.mean();
                double dMicroF1_m = c.micF1.mean() - r.micF1.mean();

                // conservative sd (assume independent across seeds for simplicity)
                double dSumJ_sd = Math.sqrt(c.sumJ.var() + r.sumJ.var());
                double dMacroF1_sd = Math.sqrt(c.mF1.var() + r.mF1.var());
                double dMicroF1_sd = Math.sqrt(c.micF1.var() + r.micF1.var());

                System.out.printf(Locale.ROOT,
                        "%-28s  ΔsumJ=%.3f±%.3f  ΔmacroF1=%.3f±%.3f  ΔmicroF1=%.3f±%.3f%n",
                        k, dSumJ_m, dSumJ_sd, dMacroF1_m, dMacroF1_sd, dMicroF1_m, dMicroF1_sd);
            }
        }

        private static final class Cell {
            Agg sumJ = new Agg(), mF1 = new Agg(), MJ = new Agg(), micF1 = new Agg();
        }
    }
}