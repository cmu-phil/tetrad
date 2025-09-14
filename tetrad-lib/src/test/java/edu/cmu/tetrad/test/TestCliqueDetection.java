package edu.cmu.tetrad.test;

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
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TextTable;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class TestCliqueDetection {

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
                           clusters.stream().map(TestCliqueDetection::names).toList());

        return new MimData(data, gTrue, clusters);
    }

    // =================== Data & Truth ===================

    private static List<Set<Node>> extractTrueMimClusters(Graph gTrue, DataSet measuredData) {
        return extractTrueMimClusters(gTrue, measuredData, 3);
    }

    /**
     * Extract measurement clusters as measured children of latent nodes.
     */
    private static List<Set<Node>> extractTrueMimClusters(Graph gTrue,
                                                          DataSet measuredData,
                                                          int minSize) {
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

        Map<String, Set<Node>> uniq = new LinkedHashMap<>();
        for (Set<Node> c : raw) {
            String key = c.stream().map(Node::getName).sorted().collect(Collectors.joining(","));
            uniq.putIfAbsent(key, c);
        }
        return new ArrayList<>(uniq.values());
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

        // ----- existing macro/micro over matched clusters -----
        int K = Math.max(1, pcs.size());
        double mP = sumP / K, mR = sumR / K, mF1 = sumF1 / K;
        double mJ = sumJ / K;

        double miP = (TP + FP) == 0 ? 0.0 : TP / (double) (TP + FP);
        double miR = (TP + FN) == 0 ? 0.0 : TP / (double) (TP + FN);
        double miF1 = (miP + miR == 0.0) ? 0.0 : 2 * miP * miR / (miP + miR);

        // ----- NEW: edge-level PRF over all edges implied by cliques vs truth -----
        Set<String> truthE = truthEdgesFromClusters(trueClusters);
        Set<String> predE = edgesFromCliques(recoveredCliques);

        long eTP = 0, eFP = 0;
        for (String e : predE) {
            if (truthE.contains(e)) eTP++;
            else eFP++;
        }
        long eFN = 0;
        for (String e : truthE) {
            if (!predE.contains(e)) eFN++;
        }
        double ePrec = (eTP + eFP) == 0 ? 0.0 : eTP / (double) (eTP + eFP);
        double eRec = (eTP + eFN) == 0 ? 0.0 : eTP / (double) (eTP + eFN);
        double eF1 = (ePrec + eRec) == 0 ? 0.0 : 2 * ePrec * eRec / (ePrec + eRec);

        return new RecoveryStats(
                mP, mR, mF1,
                mJ, sumJ,
                miP, miR, miF1,
                ePrec, eRec, eF1,
                eTP, eFP, eFN,
                pcs
        );
    }

    // =================== Leaderboard ===================

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

    /**
     * Make canonical undirected key "A--B" with A<B.
     */
    private static String undKey(String a, String b) {
        return (a.compareTo(b) < 0) ? (a + "--" + b) : (b + "--" + a);
    }

    /**
     * All undirected edges implied by a list of cliques (pairwise within each).
     */
    private static Set<String> edgesFromCliques(List<Set<Node>> cliques) {
        Set<String> E = new LinkedHashSet<>();
        for (Set<Node> c : cliques) {
            List<Node> L = new ArrayList<>(c);
            for (int i = 0; i < L.size(); i++) {
                for (int j = i + 1; j < L.size(); j++) {
                    E.add(undKey(L.get(i).getName(), L.get(j).getName()));
                }
            }
        }
        return E;
    }

    /**
     * All undirected edges within each true cluster (pairwise within each).
     */
    private static Set<String> truthEdgesFromClusters(List<Set<Node>> trueClusters) {
        Set<String> T = new LinkedHashSet<>();
        for (Set<Node> c : trueClusters) {
            List<Node> L = new ArrayList<>(c);
            for (int i = 0; i < L.size(); i++) {
                for (int j = i + 1; j < L.size(); j++) {
                    T.add(undKey(L.get(i).getName(), L.get(j).getName()));
                }
            }
        }
        return T;
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String fmtMSD(double mean, double sd, int wMean, int wSd) {
        // Example: " 0.812±0.047" with mean width 6 and sd width 6
        return String.format(Locale.ROOT, "%" + wMean + ".3f±%" + wSd + ".3f", mean, sd);
    }

    private static String fmt(double x, int w) {
        return String.format(Locale.ROOT, "%" + w + ".3f", x);
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

            for (double _penalty : new double[]{1.0, 2.0, 4.0}) {
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

//            for (double _alpha : new double[]{0.01, 0.05, 0.1, 0.2}) {
//                learners.put("PC_Rank_Wilkes_alpha_" + _alpha, () -> {
//                    Parameters params = new Parameters();
//                    params.set(Params.ALPHA, _alpha);
//
//                    IndependenceTest test = new RankIndependenceTestWilkesSingletons().getTest(md.data(), params);
//                    Pc pc = new Pc(test);
//                    try {
//                        return pc.search();
//                    } catch (InterruptedException e1) {
//                        throw new RuntimeException(e1);
//                    }
//                });
//            }

//            for (double _alpha : new double[]{0.01, 0.05, 0.1, 0.2}) {
//                learners.put("PC_Rank_Lemma10_alpha_" + _alpha, () -> {
//                    Parameters params = new Parameters();
//                    params.set(Params.ALPHA, _alpha);
//
//                    IndependenceTest test = new RankIndependenceTestLemma10Singletons().getTest(md.data(), params);
//                    Pc pc = new Pc(test);
//                    try {
//                        return pc.search();
//                    } catch (InterruptedException e1) {
//                        throw new RuntimeException(e1);
//                    }
//                });
//            }

            for (double _penalty : new double[]{1.0, 2.0, 4.0}) {
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
                    BlockSpec spec = BlockDiscoverers.tsc(
                            md.data(), 0.05, -1, 1e-8, 2, SingleClusterPolicy.EXCLUDE, 0, false
                    ).discover();

                    List<Set<Node>> cliquesRaw = new ArrayList<>();
                    List<Node> variables = spec.dataSet().getVariables();

                    for (List<Integer> cluster : spec.blocks()) {
                        Set<Node> nodes = new LinkedHashSet<>();
                        for (int i : cluster) nodes.add(variables.get(i));
                        cliquesRaw.add(nodes);
                    }

                    System.out.println("TSC cliques = " + cliquesRaw);

                    Graph out = new EdgeListGraph(variables);
                    for (Set<Node> clique : cliquesRaw) {
                        List<Node> L = new ArrayList<>(clique);
                        for (int i = 0; i < L.size(); i++)
                            for (int j = i + 1; j < L.size(); j++)
                                out.addUndirectedEdge(L.get(i), L.get(j));
                    }
                    return out;
                });
                ;
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

                List<Integer> all = new ArrayList<>();
                for (int i = 0; i < md.data().getVariables().size(); i++) {
                    all.add(i);
                }

                List<Set<Node>> filteredCliques = new ArrayList<>();

                for (Set<Node> clique : cliquesRaw) {
                    List<Integer> indices = new ArrayList<>();
                    for (Node node : clique) {
                        indices.add(md.data().getColumn(node));
                    }

                    List<Integer> remaining = new ArrayList<>(all);
                    remaining.removeAll(indices);

                    int[] _indices = indices.stream().mapToInt(Integer::intValue).toArray();
                    int[] _remaining = remaining.stream().mapToInt(Integer::intValue).toArray();

                    double rank = RankTests.estimateWilksRank(S, _indices, _remaining, N, 0.001);

                    if (rank == 1) {
                        filteredCliques.add(new HashSet<>(clique));
                    }
                }

                System.out.println("Rank 1 cliques:");

                for (int i = 0; i < filteredCliques.size(); i++) {
                    System.out.println("Clique " + (i + 1) + ": " + filteredCliques.get(i));
                }

                RecoveryStats base = scoreRecovery(md.trueClusters(), cliquesRaw);
                lb.add(name + " (raw)", base);

                RecoveryStats after = scoreRecovery(md.trueClusters(), filteredCliques);
                lb.add(name + " (rank 1)", after);
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

    private static class RecoveryStats {
        final double macroPrec, macroRec, macroF1;
        final double macroJaccard, sumJaccard;
        final double microPrec, microRec, microF1;

        // NEW: edge-level metrics
        final double edgePrec, edgeRec, edgeF1;
        final long edgeTP, edgeFP, edgeFN;

        final List<PerCluster> perCluster;

        RecoveryStats(double mP, double mR, double mF1,
                      double mJ, double sJ,
                      double miP, double miR, double miF1,
                      double eP, double eR, double eF1,
                      long eTP, long eFP, long eFN,
                      List<PerCluster> pcs) {
            macroPrec = mP;
            macroRec = mR;
            macroF1 = mF1;
            macroJaccard = mJ;
            sumJaccard = sJ;
            microPrec = miP;
            microRec = miR;
            microF1 = miF1;
            edgePrec = eP;
            edgeRec = eR;
            edgeF1 = eF1;
            edgeTP = eTP;
            edgeFP = eFP;
            edgeFN = eFN;
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

        static final class Row {
            final String methodLabel;
            final double sumJaccard, macroJaccard, macroF1, microF1;
            final double edgeF1; // NEW
            final RecoveryStats stats;

            Row(String label, RecoveryStats s) {
                this.methodLabel = label;
                this.sumJaccard = s.sumJaccard;
                this.macroJaccard = s.macroJaccard;
                this.macroF1 = s.macroF1;
                this.microF1 = s.microF1;
                this.edgeF1 = s.edgeF1; // NEW
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
            c.eF1.add(s.edgeF1);     // NEW
            c.eP.add(s.edgePrec);    // NEW
            c.eR.add(s.edgeRec);     // NEW
        }

        void printBySumJ() {
            List<Map.Entry<String, Cell>> L = new ArrayList<>(map.entrySet());
            L.sort((a, b) -> Double.compare(b.getValue().sumJ.mean(), a.getValue().sumJ.mean()));

            // Columns: Rank, Method, sumJ, macroF1, microF1, edgeF1
            final int rows = L.size() + 1;
            final int cols = 7;
            TextTable tt = new TextTable(rows, cols);
            tt.setDelimiter(TextTable.Delimiter.JUSTIFIED);
            tt.setJustification(TextTable.LEFT_JUSTIFIED); // we will left-justify; numbers are fixed-width

            // Header
            tt.setToken(0, 0, "#");
            tt.setToken(0, 1, "Method");
            tt.setToken(0, 2, "sumJ (m±sd)");
            tt.setToken(0, 3, "macroF1 (m±sd)");
            tt.setToken(0, 4, "microF1 (m±sd)");
            tt.setToken(0, 5, "edgeP (m±sd)");
            tt.setToken(0, 6, "edgeR (m±sd)");

            // pick widths for numeric cells so they line up
            final int W = 6; // width for mean and sd components

            for (int i = 0; i < L.size(); i++) {
                var e = L.get(i);
                var c = e.getValue();
                tt.setToken(i + 1, 0, Integer.toString(i + 1));
                tt.setToken(i + 1, 1, trunc(e.getKey(), 40)); // keep label compact
                tt.setToken(i + 1, 2, fmtMSD(c.sumJ.mean(), c.sumJ.sd(), W, W));
                tt.setToken(i + 1, 3, fmtMSD(c.mF1.mean(), c.mF1.sd(), W, W));
                tt.setToken(i + 1, 4, fmtMSD(c.micF1.mean(), c.micF1.sd(), W, W));
                tt.setToken(i + 1, 5, fmtMSD(c.eP.mean(), c.eP.sd(), W, W));
                tt.setToken(i + 1, 6, fmtMSD(c.eR.mean(), c.eR.sd(), W, W));
            }

            System.out.println("\n=== Leaderboard (mean±sd over seeds; by SUM Jaccard) ===");
            System.out.print(tt.toString());
        }

        void printByMacroF1() {
            List<Map.Entry<String, Cell>> L = new ArrayList<>(map.entrySet());
            L.sort((a, b) -> Double.compare(b.getValue().mF1.mean(), a.getValue().mF1.mean()));

            final int rows = L.size() + 1;
            final int cols = 7;
            TextTable tt = new TextTable(rows, cols);
            tt.setDelimiter(TextTable.Delimiter.JUSTIFIED);
            tt.setJustification(TextTable.LEFT_JUSTIFIED);

            tt.setToken(0, 0, "#");
            tt.setToken(0, 1, "Method");
            tt.setToken(0, 2, "macroF1 (m±sd)");
            tt.setToken(0, 3, "sumJ (m±sd)");
            tt.setToken(0, 4, "microF1 (m±sd)");
            tt.setToken(0, 5, "edgeP (m±sd)");
            tt.setToken(0, 6, "edgeR (m±sd)");

            final int W = 6;

            for (int i = 0; i < L.size(); i++) {
                var e = L.get(i);
                var c = e.getValue();
                tt.setToken(i + 1, 0, Integer.toString(i + 1));
                tt.setToken(i + 1, 1, trunc(e.getKey(), 40));
                tt.setToken(i + 1, 2, fmtMSD(c.mF1.mean(), c.mF1.sd(), W, W));
                tt.setToken(i + 1, 3, fmtMSD(c.sumJ.mean(), c.sumJ.sd(), W, W));
                tt.setToken(i + 1, 4, fmtMSD(c.micF1.mean(), c.micF1.sd(), W, W));
                tt.setToken(i + 1, 5, fmtMSD(c.eP.mean(), c.eP.sd(), W, W));
                tt.setToken(i + 1, 6, fmtMSD(c.eR.mean(), c.eR.sd(), W, W));
            }

            System.out.println("\n=== Leaderboard (mean±sd over seeds; by MACRO F1) ===");
            System.out.print(tt.toString());
        }

        void printDeltas() {
            System.out.println("\n=== Δ from Rank-1 filtering (mean±sd; (rank 1) – (raw)) ===");

            Map<String, Cell> raw = new LinkedHashMap<>();
            Map<String, Cell> r1 = new LinkedHashMap<>();
            for (String label : map.keySet()) {
                if (label.endsWith("(raw)")) {
                    raw.put(label.substring(0, label.length() - "(raw)".length()).trim(), map.get(label));
                } else if (label.endsWith("(rank 1)")) {
                    r1.put(label.substring(0, label.length() - "(rank 1)".length()).trim(), map.get(label));
                }
            }

            List<String> keys = new ArrayList<>(raw.keySet());
            keys.retainAll(r1.keySet());
            Collections.sort(keys);

            final int rows = keys.size() + 1;
            final int cols = 7; // fewer columns to keep width down
            TextTable tt = new TextTable(rows, cols);
            tt.setDelimiter(TextTable.Delimiter.JUSTIFIED);
            tt.setJustification(TextTable.LEFT_JUSTIFIED);
            final int W = 6;

            tt.setToken(0, 0, "#");
            tt.setToken(0, 1, "Method");
            tt.setToken(0, 2, "ΔsumJ");
            tt.setToken(0, 3, "ΔmacroF1");
            tt.setToken(0, 4, "ΔmicroF1");
            tt.setToken(0, 5, "ΔedgeP");
            tt.setToken(0, 6, "ΔedgeR");

            for (int i = 0; i < keys.size(); i++) {
                String k = keys.get(i);
                Cell a = raw.get(k), b = r1.get(k);

                double dSumJ_m = b.sumJ.mean() - a.sumJ.mean();
                double dMacroF1_m = b.mF1.mean() - a.mF1.mean();
                double dMicroF1_m = b.micF1.mean() - a.micF1.mean();
                double dEdgeF1_m = b.eF1.mean() - a.eF1.mean();
                double dEdgeP_m = b.eP.mean() - a.eP.mean();
                double dEdgeR_m = b.eR.mean() - a.eR.mean();

                double dSumJ_sd = Math.sqrt(b.sumJ.var() + a.sumJ.var());
                double dMacroF1_sd = Math.sqrt(b.mF1.var() + a.mF1.var());
                double dMicroF1_sd = Math.sqrt(b.micF1.var() + a.micF1.var());
                double dEdgeF1_sd = Math.sqrt(b.eF1.var() + a.eF1.var());
                double dEdgeP_sd = Math.sqrt(b.eP.var() + a.eP.var());
                double dEdgeR_sd = Math.sqrt(b.eR.var() + a.eR.var());

                tt.setToken(i + 1, 0, Integer.toString(i + 1));
                tt.setToken(i + 1, 1, trunc(k, 40));
                tt.setToken(i + 1, 2, fmtMSD(dSumJ_m, dSumJ_sd, W, W));
                tt.setToken(i + 1, 3, fmtMSD(dMacroF1_m, dMacroF1_sd, W, W));
                tt.setToken(i + 1, 4, fmtMSD(dMicroF1_m, dMicroF1_sd, W, W));
                tt.setToken(i + 1, 5, fmtMSD(dEdgeP_m, dEdgeP_sd, W, W));
                tt.setToken(i + 1, 6, fmtMSD(dEdgeR_m, dEdgeR_sd, W, W));
            }

            System.out.print(tt.toString());
        }

        private static final class Cell {
            Agg sumJ = new Agg(), mF1 = new Agg(), MJ = new Agg(), micF1 = new Agg();
            Agg eF1 = new Agg(), eP = new Agg(), eR = new Agg();
        }
    }
}