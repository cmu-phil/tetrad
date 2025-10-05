///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

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
import org.jetbrains.annotations.NotNull;
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
        SemIm im = new SemIm(pm, params);
//        edu.cmu.tetrad.util.RandomUtil.getInstance().setSeed(seed);
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
    /**
     * Score recovery of measurement clusters using one-to-one greedy matching. nodePrec = macro precision over
     * predicted cliques (unmatched predictions = 0) nodeRec  = macro recall over true clusters     (unmatched truths =
     * 0) macroJaccard = average Jaccard over truths (zeros for misses) sumJaccard   = sum of those Jaccards
     */
    private static RecoveryStats scoreRecovery(List<Set<Node>> trueClusters,
                                               List<Set<Node>> recoveredCliques) {
        // Convert clusters to name-sets for set ops
        List<LinkedHashSet<String>> T = trueClusters.stream()
                .map(s -> s.stream().map(Node::getName)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .toList();
        List<LinkedHashSet<String>> R = recoveredCliques.stream()
                .map(s -> s.stream().map(Node::getName)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .toList();

        // --- One-to-one greedy matching by Jaccard ---
        List<Integer> unmatchedT = new ArrayList<>();
        for (int i = 0; i < T.size(); i++) unmatchedT.add(i);
        List<Integer> unmatchedR = new ArrayList<>();
        for (int j = 0; j < R.size(); j++) unmatchedR.add(j);

        List<int[]> matches = new ArrayList<>(); // pairs (ti, rj)

        while (!unmatchedT.isEmpty() && !unmatchedR.isEmpty()) {
            double bestJ = -1.0;
            int bestTi = -1, bestRj = -1;
            for (int ti : unmatchedT) {
                for (int rj : unmatchedR) {
                    double j = jaccard(T.get(ti), R.get(rj));
                    if (j > bestJ) {
                        bestJ = j;
                        bestTi = ti;
                        bestRj = rj;
                    }
                }
            }
            // stop if no overlap at all
            if (bestJ <= 0.0) break;

            matches.add(new int[]{bestTi, bestRj});
            unmatchedT.remove((Integer) bestTi);
            unmatchedR.remove((Integer) bestRj);
        }

        // --- Accumulate per-pair stats (truth-side view) ---
        List<PerCluster> pcs = new ArrayList<>();
        double sumRec_T = 0.0;   // sum recall over matched truths
        double sumJac_T = 0.0;   // sum Jaccard over matched truths
        double sumPrec_R = 0.0;  // sum precision over matched predictions

        for (int[] pr : matches) {
            int ti = pr[0], rj = pr[1];

            Set<Node> tNodes = namesToNodes(T.get(ti), trueClusters.get(ti));
            Set<Node> rNodes = namesToNodes(R.get(rj), recoveredCliques.get(rj));

            PerCluster pc = new PerCluster(tNodes, rNodes);
            pcs.add(pc);

            sumRec_T += pc.recall;  // truth-side recall
            sumJac_T += pc.jaccard; // truth-side Jaccard
            sumPrec_R += pc.prec;    // prediction-side precision
        }

        // --- Macro aggregates with zeros for misses/unmatched ---
        int Kt = Math.max(1, T.size()); // avoid /0 when no truths (shouldn't happen)
        int Kr = Math.max(1, R.size()); // avoid /0 when no predictions

        double nodeRec = sumRec_T / Kt;          // unmatched truths contribute 0
        double nodePrec = sumPrec_R / Kr;        // unmatched predictions contribute 0
        double macroJ = sumJac_T / Kt;           // unmatched truths contribute 0
        double sumJ = sumJac_T;                  // sum over truths (zeros for misses not needed)

        return new RecoveryStats(
                nodePrec,
                nodeRec,
                macroJ,
                sumJ,
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

    private static String trunc(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String fmtMSD(double mean, double sd, int wMean, int wSd) {
        return String.format(Locale.ROOT, "%" + wMean + ".3f±%" + wSd + ".3f", mean, sd);
    }

    private static @NotNull Map<String, Supplier<Graph>> buildLearners(MimData md) {
        Map<String, Supplier<Graph>> learners = new LinkedHashMap<>();

        {
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

            for (double _alpha : new double[]{0.01, 0.05}) {
                learners.put("PC_FisherZ_alpha_" + _alpha, () -> {
                    IndependenceTest test = new IndTestFisherZ(md.data(), _alpha);
                    Pc pc = new Pc(test);
                    pc.setDepth(5);
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
            }
        }
        return learners;
    }

//    @Test
    public void testLearnersCliquesAndClusterRecovery() {
        final int N = 10000;
        final int R = 10; // number of random seeds

        LBMulti multi = new LBMulti();

        for (int r = 0; r < R; r++) {
            long seed = RandomUtil.getInstance().nextLong();

            MimData md = makeMimData(N, seed);
            assertNotNull(md.data());
            assertFalse("No true clusters found", md.trueClusters().isEmpty());

            Map<String, Supplier<Graph>> learners = buildLearners(md);

            SimpleMatrix S = new CorrelationMatrix(md.data()).getMatrix().getSimpleMatrix();

            // --- Per-seed leaderboard ---
            Leaderboard lb = new Leaderboard();

            for (Map.Entry<String, Supplier<Graph>> e : learners.entrySet()) {
                String name = e.getKey();

                // Baseline
                Graph g = e.getValue().get();
                List<Set<Node>> cliquesRaw = maximalCliquesMeasured(g, md.data());

                System.out.println(name + ": raw cliques = " + cliquesRaw);

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
        multi.printBySumJ();
        multi.printByNodes();
        multi.printDeltas();
    }

    private record MimData(DataSet data, Graph trueGraph, List<Set<Node>> trueClusters) {
    }

    private record RecoveryStats(
            double nodePrec, double nodeRec,
            double macroJaccard, double sumJaccard,
            List<PerCluster> perCluster
    ) {
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
            final double sumJaccard, macroJaccard, nodePrec, nodeRec;
            final RecoveryStats stats;

            Row(String label, RecoveryStats s) {
                this.methodLabel = label;
                this.sumJaccard = s.sumJaccard;
                this.macroJaccard = s.macroJaccard;
                this.nodePrec = s.nodePrec;
                this.nodeRec = s.nodeRec;
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
            c.sumJ.add(s.sumJaccard());
            c.MJ.add(s.macroJaccard());
            c.nodeP.add(s.nodePrec());
            c.nodeR.add(s.nodeRec());
        }

        void printBySumJ() {
            List<Map.Entry<String, Cell>> L = new ArrayList<>(map.entrySet());
            L.sort((a, b) -> Double.compare(b.getValue().sumJ.mean(), a.getValue().sumJ.mean()));

            final int rows = L.size() + 1;
            final int cols = 5;
            TextTable tt = new TextTable(rows, cols);
            tt.setDelimiter(TextTable.Delimiter.JUSTIFIED);
            tt.setJustification(TextTable.LEFT_JUSTIFIED);

            tt.setToken(0, 0, "#");
            tt.setToken(0, 1, "Method");
            tt.setToken(0, 2, "sumJ (m±sd)");
            tt.setToken(0, 3, "NodePrec (m±sd)");
            tt.setToken(0, 4, "NodeRec (m±sd)");

            final int W = 6;

            for (int i = 0; i < L.size(); i++) {
                var e = L.get(i);
                var c = e.getValue();
                tt.setToken(i + 1, 0, Integer.toString(i + 1));
                tt.setToken(i + 1, 1, trunc(e.getKey(), 40));
                tt.setToken(i + 1, 2, fmtMSD(c.sumJ.mean(), c.sumJ.sd(), W, W));
                tt.setToken(i + 1, 3, fmtMSD(c.nodeP.mean(), c.nodeP.sd(), W, W));
                tt.setToken(i + 1, 4, fmtMSD(c.nodeR.mean(), c.nodeR.sd(), W, W));
            }

            System.out.println("\n=== Leaderboard (mean±sd over seeds; by SUM Jaccard) ===");
            System.out.print(tt);
        }

        void printByNodes() {
            List<Map.Entry<String, Cell>> L = new ArrayList<>(map.entrySet());
            L.sort((a, b) -> Double.compare(b.getValue().nodeP.mean(), a.getValue().nodeP.mean()));

            final int rows = L.size() + 1;
            final int cols = 5;
            TextTable tt = new TextTable(rows, cols);
            tt.setDelimiter(TextTable.Delimiter.JUSTIFIED);
            tt.setJustification(TextTable.LEFT_JUSTIFIED);

            tt.setToken(0, 0, "#");
            tt.setToken(0, 1, "Method");
            tt.setToken(0, 2, "NodePrec (m±sd)");
            tt.setToken(0, 3, "NodeRec (m±sd)");
            tt.setToken(0, 4, "macroJ (m±sd)");

            final int W = 6;

            for (int i = 0; i < L.size(); i++) {
                var e = L.get(i);
                var c = e.getValue();
                tt.setToken(i + 1, 0, Integer.toString(i + 1));
                tt.setToken(i + 1, 1, trunc(e.getKey(), 40));
                tt.setToken(i + 1, 2, fmtMSD(c.nodeP.mean(), c.nodeP.sd(), W, W));
                tt.setToken(i + 1, 3, fmtMSD(c.nodeR.mean(), c.nodeR.sd(), W, W));
                tt.setToken(i + 1, 4, fmtMSD(c.sumJ.mean(), c.sumJ.sd(), W, W));
            }

            System.out.println("\n=== Leaderboard (mean±sd over seeds; by NODE precision) ===");
            System.out.print(tt);
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
            final int cols = 7;
            TextTable tt = new TextTable(rows, cols);
            tt.setDelimiter(TextTable.Delimiter.JUSTIFIED);
            tt.setJustification(TextTable.LEFT_JUSTIFIED);
            final int W = 5;

            tt.setToken(0, 0, "#");
            tt.setToken(0, 1, "Method");
            tt.setToken(0, 2, "sumJ");
            tt.setToken(0, 3, "NodePrec");
            tt.setToken(0, 4, "NodeRec");

            for (int i = 0; i < keys.size(); i++) {
                String k = keys.get(i);
                Cell a = raw.get(k), b = r1.get(k);

                double dSumJ_m = b.sumJ.mean() - a.sumJ.mean();
                double dNodeP_m = b.nodeP.mean() - a.nodeP.mean();
                double dNodeR_m = b.nodeR.mean() - a.nodeR.mean();

                double dSumJ_sd = Math.sqrt(b.sumJ.var() + a.sumJ.var());
                double dNodeP_sd = Math.sqrt(b.nodeP.var() + a.nodeP.var());
                double dNodeR_sd = Math.sqrt(b.nodeR.var() + a.nodeR.var());

                tt.setToken(i + 1, 0, Integer.toString(i + 1));
                tt.setToken(i + 1, 1, trunc(k, 40));
                tt.setToken(i + 1, 2, fmtMSD(dSumJ_m, dSumJ_sd, W, W));
                tt.setToken(i + 1, 3, fmtMSD(dNodeP_m, dNodeP_sd, W, W));
                tt.setToken(i + 1, 4, fmtMSD(dNodeR_m, dNodeR_sd, W, W));
            }

            System.out.print(tt);
        }

        private static final class Cell {
            Agg sumJ = new Agg(), MJ = new Agg();
            Agg nodeP = new Agg(), nodeR = new Agg();
        }
    }
}