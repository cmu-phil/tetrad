package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomMim;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.CliqueCompletion;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TestCliqueCompletionIntegration {

    @Test
    public void testLearnersCliquesAndClusterRecovery() {
        double penalty = 1.0;
        double alpha = 0.01;
        int N = 2000;

        MimData md = makeMimData(N, 5, 5, RandomUtil.getInstance().nextLong());
        assertNotNull(md.data());
        assertFalse("No true clusters found", md.trueClusters().isEmpty());

        // --- 1) Learners (added FGES) ---
        Map<String, Supplier<Graph>> learners = new LinkedHashMap<>();

        learners.put("BOSS_SEMBIC_penalty_" + penalty, () -> {
            SemBicScore score = new SemBicScore(new CorrelationMatrix(md.data()));
            score.setPenaltyDiscount(penalty);
            try {
                return new PermutationSearch(new Boss(score)).search();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        learners.put("PC_FisherZ_alpha_" + alpha, () -> {
            IndependenceTest test = new IndTestFisherZ(md.data(), alpha);
            Pc pc = new Pc(test);
            try {
                return pc.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        // NEW: FGES (SEM-BIC, penalty 2.0)
        learners.put("FGES_SEMBIC_penalty_" + penalty, () -> {
            SemBicScore score = new SemBicScore(new CorrelationMatrix(md.data()));
            score.setPenaltyDiscount(penalty);
            try {
                Fges ges = new Fges(score);
                return ges.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        for (Map.Entry<String, Supplier<Graph>> e : learners.entrySet()) {
            String name = e.getKey();

            // --- Baseline ---
            Graph g = e.getValue().get();
            System.out.println("\n=== " + name + " : raw ===");
            printComponents(g);
            List<Set<Node>> cliquesRaw = maximalCliquesMeasured(g, md.data());
            printCliques(cliquesRaw);

            RecoveryStats base = scoreRecovery(md.trueClusters(), cliquesRaw);
            System.out.printf(Locale.ROOT,
                    "[%s] RAW  : macro-Prec=%.3f macro-Rec=%.3f macro-F1=%.3f%n",
                    name, base.macroPrec, base.macroRec, base.macroF1);

            // --- Clique completion (precision-friendly) ---
//            CliqueCompletion cc = CliqueCompletion.newBuilder(new IndTestFisherZ(md.data, 0.01))
//                    .maxCompletionOrder(0)     // unconditional only; fast & precise for large n, positive coeffs
//                    .intraAlpha(0.015)         // modest; BH within pocket stays conservative
//                    .kCore(4)                  // only work in dense pockets
//                    .minCommonNeighbors(2)     // the new precision gate
//                    .enableTriangleCompletion(true)
//                    .enableDenseCoreRetest(true)
//                    .log(true)
//                    .build();

            CliqueCompletion cc = CliqueCompletion.newBuilder(new IndTestFisherZ(md.data(), 0.01))
                    .maxCompletionOrder(9)    // add size-1 sets {pivot} or one shared neighbor
                    .intraAlpha(0.03)         // still BH within pocket
                    .kCore(2)                 // include more nodes in pockets
                    .minCommonNeighbors(1)    // allow pairs with at least 1 shared neighbor
                    .enableTriangleCompletion(true)
                    .enableDenseCoreRetest(true)
                    .log(true)
                    .build();

//            CliqueCompletion cc = CliqueCompletion.newBuilder(new IndTestFisherZ(md.data(), 0.01))
//                    .maxCompletionOrder(2)    // tries a few size-2 sets (capped)
//                    .intraAlpha(0.05)         // liberal, but BH keeps it local
//                    .kCore(2)
//                    .minCommonNeighbors(1)
//                    .enableTriangleCompletion(true)
//                    .enableDenseCoreRetest(true)
//                    .log(true)
//                    .build();

            // Track edges before/after to compute clutter diagnostics
            Set<String> edgesBefore = undirectedEdgeSet(g);
            cc.apply(g);
            Set<String> edgesAfter  = undirectedEdgeSet(g);
            Set<String> added = new LinkedHashSet<>(edgesAfter);
            added.removeAll(edgesBefore);

            System.out.println("\n=== " + name + " + CliqueCompletion ===");
            printComponents(g);
            List<Set<Node>> cliquesCC = maximalCliquesMeasured(g, md.data());
            printCliques(cliquesCC);

            RecoveryStats after = scoreRecovery(md.trueClusters(), cliquesCC);
            System.out.printf(Locale.ROOT,
                    "[%s] CC   : macro-Prec=%.3f macro-Rec=%.3f macro-F1=%.3f%n",
                    name, after.macroPrec, after.macroRec, after.macroF1);

            // --- Clutter diagnostics (edges added across true clusters) ---
            ClutterStats clutter = clutterStats(added, md.trueClusters());
            System.out.printf(Locale.ROOT,
                    "[%s] CC additions: total=%d  intraCluster=%d  interCluster=%d  interRate=%.2f%n",
                    name, clutter.total, clutter.intra, clutter.inter, clutter.interRate());

            // Inspect near-equal cliques overlap patterns
            {
                int cand = 0, tested = 0;
                for (int i = 0; i < cliquesRaw.size(); i++) {
                    Set<Node> A = cliquesRaw.get(i);
                    for (int j = i + 1; j < cliquesRaw.size(); j++) {
                        Set<Node> B = cliquesRaw.get(j);
                        if (A.size() != B.size()) break;
                        Set<Node> inter = new LinkedHashSet<>(A); inter.retainAll(B);
                        if (inter.size() == A.size() - 1) {
                            cand++;
                            // fringe pair a,b:
                            Set<Node> diffA = new LinkedHashSet<>(A); diffA.removeAll(B);
                            Set<Node> diffB = new LinkedHashSet<>(B); diffB.removeAll(A);
                            Node a = diffA.iterator().next(), b = diffB.iterator().next();
                            tested++;
                            System.out.printf("NearClique candidate: |A|=|B|=%d, |∩|=%d, fringe=(%s,%s)%n",
                                    A.size(), inter.size(), a.getName(), b.getName());
                        }
                    }
                }
                System.out.printf("NearClique summary: candidates=%d, tested=%d%n", cand, tested);
            }

            debugClusterSeeds(g, md.data(), md.trueClusters());

//            // --- Assertions ---
//            assertTrue(name + " macro-F1 should not decrease after CliqueCompletion",
//                    after.macroF1 + 1e-9 >= base.macroF1 - 1e-9);
//
//            // Soft guard on clutter: allow some inter-cluster, but not dominance
//            assertTrue(name + " inter-cluster additions should not dominate",
//                    clutter.interRate() <= 0.60 + 1e-9);
        }
    }

    // =================== Data & Truth ===================

    private record MimData(DataSet data, Graph trueGraph, List<Set<Node>> trueClusters) {}

    private static MimData makeMimData(int nRows, int latentGroups, int childrenPerGroup, long seed) {
        RandomMim.LatentGroupSpec spec = new RandomMim.LatentGroupSpec(latentGroups, 1, childrenPerGroup);
        Random rng = new Random(seed);

        Graph gTrue = RandomMim.constructRandomMim(
                List.of(spec), 0, 0, 0, 0,
                RandomMim.LatentLinkMode.CARTESIAN_PRODUCT, rng);

        Parameters params = new Parameters();
        params.set("seed", seed);
        params.set("coefLow", 0.3);
        params.set("coefHigh", 1.1);
        params.set("coefSymmetric", false);

        SemPm pm = new SemPm(gTrue);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(nRows, false);

        List<Set<Node>> clusters = extractTrueMimClusters(gTrue, data);
        System.out.println("True clusters (children of each latent): " +
                           clusters.stream().map(TestCliqueCompletionIntegration::names).collect(Collectors.toList()));

        return new MimData(data, gTrue, clusters);
    }

    private static List<Set<Node>> extractTrueMimClusters(Graph gTrue, DataSet measuredData) {
        Set<String> measuredNames = measuredData.getVariables().stream().map(Node::getName).collect(Collectors.toSet());
        List<Set<Node>> out = new ArrayList<>();
        for (Node v : gTrue.getNodes()) {
            if (!gTrue.getParents(v).isEmpty()) continue;
            List<Node> kids = gTrue.getChildren(v);
            if (kids.size() < 3) continue;
            Set<Node> cluster = kids.stream()
                    .filter(u -> measuredNames.contains(u.getName()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (cluster.size() >= 3) out.add(cluster);
        }
        return dedupSetsByNames(out);
    }

    // =================== Clique extraction (measured only) ===================

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

    // =================== Scoring / Matching ===================

    private static class RecoveryStats {
        final double macroPrec, macroRec, macroF1;
        final List<PerCluster> perCluster;
        RecoveryStats(double P, double R, double F1, List<PerCluster> pcs) {
            macroPrec = P; macroRec = R; macroF1 = F1; perCluster = pcs;
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

    private static RecoveryStats scoreRecovery(List<Set<Node>> trueClusters,
                                               List<Set<Node>> recoveredCliques) {
        final double JACCARD_MIN = 0.0; // change to 0.1 or 0.25 if you want stricter matches

        List<LinkedHashSet<String>> T = trueClusters.stream()
                .map(s -> s.stream().map(Node::getName)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .toList();

        List<LinkedHashSet<String>> R = recoveredCliques.stream()
                .map(s -> s.stream().map(Node::getName)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .toList();

        List<Integer> unmatchedR = new ArrayList<>();
        for (int i = 0; i < R.size(); i++) unmatchedR.add(i);

        List<PerCluster> pcs = new ArrayList<>();
        double sumP = 0, sumR = 0, sumF1 = 0;
        int matchedTrue = 0;

        for (int ti = 0; ti < T.size(); ti++) {
            Set<String> t = T.get(ti);
            int bestR = -1; double bestJ = -1.0;

            for (int rIdx : unmatchedR) {
                Set<String> r = R.get(rIdx);
                double j = jaccard(t, r);
                if (j > bestJ) { bestJ = j; bestR = rIdx; }
            }

            Set<Node> tNodes = namesToNodes(t, trueClusters.get(ti));
            Set<Node> rNodes = null;

            if (bestR >= 0 && bestJ > JACCARD_MIN) {
                rNodes = namesToNodes(R.get(bestR), recoveredCliques.get(bestR));
                unmatchedR.remove((Integer) bestR);
                matchedTrue++;
            }

            PerCluster pc = new PerCluster(tNodes, rNodes);
            pcs.add(pc);
            sumP += pc.prec; sumR += pc.recall; sumF1 += pc.f1;

            System.out.printf(Locale.ROOT,
                    "  Truth {%s}  ↔  Match {%s}  |  P=%.2f R=%.2f F1=%.2f  (J=%.2f)%n",
                    pc.truth, pc.match, pc.prec, pc.recall, pc.f1, pc.jaccard);
        }

        double mP = sumP / Math.max(1, pcs.size());
        double mR = sumR / Math.max(1, pcs.size());
        double mF1 = sumF1 / Math.max(1, pcs.size());

        // Optional diagnostics: coverage and how many recovered cliques went unused
        int unmatchedRecovered = unmatchedR.size();
        double coverage = matchedTrue / (double) Math.max(1, T.size());
        System.out.printf(Locale.ROOT,
                "Coverage: matched %d/%d true clusters (%.2f), unused recovered cliques: %d%n",
                matchedTrue, T.size(), coverage, unmatchedRecovered);

        return new RecoveryStats(mP, mR, mF1, pcs);
    }

    // =================== Clutter diagnostics ===================

    private static class ClutterStats {
        final int total, intra, inter;
        ClutterStats(int total, int intra, int inter) { this.total = total; this.intra = intra; this.inter = inter; }
        double interRate() { return total == 0 ? 0.0 : inter / (double) total; }
    }

    /** Count how many added edges lie within the same true cluster vs across clusters. */
    private static ClutterStats clutterStats(Set<String> addedEdges, List<Set<Node>> trueClusters) {
        // Build a map from var -> cluster id (first match wins; clusters are disjoint in RandomMim)
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

    // =================== Plumbing / Utilities ===================

    /** Print connected components (by undirected adjacency). */
    private static void printComponents(Graph g) {
        List<Set<Node>> comps = connectedComponents(g);
        System.out.println("Connected components (" + comps.size() + "): "
                           + comps.stream().map(TestCliqueCompletionIntegration::names).collect(Collectors.toList()));
    }

    private static void printCliques(List<Set<Node>> cliques) {
        // sort by size desc then lexicographically
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
            dq.add(s); un.remove(s);
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
        // Build name->node map for stable Set<Node> creation
        Map<String, Node> byName = new HashMap<>();
        for (Node v : g.getNodes()) byName.put(v.getName(), v);

        // Sort cliques by size desc so we prefer larger ones first
        cliques.sort((a,b) -> Integer.compare(b.size(), a.size()));

        int added = 0;
        for (int i = 0; i < cliques.size(); i++) {
            Set<Node> A = cliques.get(i);
            for (int j = i + 1; j < cliques.size(); j++) {
                Set<Node> B = cliques.get(j);
                if (A.size() != B.size()) break; // only same-size pairs (fast exit)

                // compute intersection and symmetric diff
                Set<Node> inter = new LinkedHashSet<>(A); inter.retainAll(B);
                if (inter.size() < A.size() - 1) continue; // need overlap n-1

                Set<Node> diffA = new LinkedHashSet<>(A); diffA.removeAll(B);
                Set<Node> diffB = new LinkedHashSet<>(B); diffB.removeAll(A);
                if (diffA.size() != 1 || diffB.size() != 1) continue;

                Node a = diffA.iterator().next();
                Node b = diffB.iterator().next();
                if (g.isAdjacentTo(a, b)) continue;

                // Build small conditioning family from the intersection
                List<Set<Node>> fam = new ArrayList<>();
                fam.add(Collections.emptySet());
                if (maxOrder >= 1) {
                    // a few size-1 sets from intersection
                    int c1 = 0;
                    for (Node c : inter) { fam.add(Set.of(c)); if (++c1 >= 3) break; }
                }
                if (maxOrder >= 2) {
                    // a few size-2 sets from intersection
                    List<Node> L = new ArrayList<>(inter);
                    int c2 = 0;
                    for (int u=0; u<L.size() && c2<3; u++)
                        for (int v=u+1; v<L.size() && c2<3; v++, c2++)
                            fam.add(Set.of(L.get(u), L.get(v)));
                }

                // p-min across family
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

    private static void debugClusterSeeds(Graph g, DataSet data, List<Set<Node>> trueClusters) {
        Map<String, Node> byName = g.getNodes().stream().collect(Collectors.toMap(Node::getName, n -> n));
        double[][] corr = new CorrelationMatrix(data).getMatrix().toArray();

        System.out.println("---- Seed diagnostics (per true cluster) ----");
        for (Set<Node> T : trueClusters) {
            List<Node> L = new ArrayList<>(T);
            // count triangles among L
            int tri = 0;
            for (int i=0;i<L.size();i++) for (int j=i+1;j<L.size();j++) for (int k=j+1;k<L.size();k++) {
                Node a=L.get(i), b=L.get(j), c=L.get(k);
                if (g.isAdjacentTo(a,b) && g.isAdjacentTo(a,c) && g.isAdjacentTo(b,c)) tri++;
            }
            // avg |r| within L
            double sumAbsR=0; int cnt=0;
            for (int i=0;i<L.size();i++) for (int j=i+1;j<L.size();j++) {
                int ia = data.getColumnIndex(data.getVariable(L.get(i).getName()));
                int ib = data.getColumnIndex(data.getVariable(L.get(j).getName()));
                sumAbsR += Math.abs(corr[ia][ib]); cnt++;
            }
            System.out.printf(Locale.ROOT,
                    "Cluster {%s}: triangles=%d, avg|r|=%.3f%n",
                    names(T), tri, (cnt==0?0.0:sumAbsR/cnt));
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
            Set<Node> Rv = new LinkedHashSet<>(R); Rv.add(v);
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
}