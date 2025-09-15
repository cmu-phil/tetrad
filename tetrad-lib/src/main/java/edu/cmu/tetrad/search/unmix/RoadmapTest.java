package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RoadmapTest: EM baseline vs Residual-clustering across scenarios.
 * <p>
 * Phase 1 — Controlled synthetic sweep: (A) params-only mixtures; (B) small topology flips; (C) larger structural
 * shifts. Noise: Gaussian vs non-Gaussian (Laplace). K: known, and unknown via EM-BIC. Metrics: ARI;
 * adjacency/arrowhead F1; SHD (simple implementation); runtime.
 * <p>
 * Phase 2 — Stress & robustness: Class imbalance, smaller n, larger p, weak separation, mis-specified K; seed repeats.
 * <p>
 * Phase 3 — Semi-synthetic realism: Real covariance backbone + injected regime shifts; optional interventions knob.
 * <p>
 * Notes: - This is a practical harness: prints compact tables you can paste in email/docs. - Uses
 * Boss+PermutationSearch for graphs; swap to your preferred search if needed. - Keep DIAGONAL covariance for EM unless
 * d is small.
 */
public class RoadmapTest {

    /**
     * Orientation accuracy only among edges that are directed in BOTH truth and estimate.
     */
    private static double arrowAccBothOriented(Graph[] truth, List<Graph> found) {
        int[][] perms = {{0, 1}, {1, 0}};
        double best = 0.0;
        for (int[] pm : perms) {
            int ok = 0, tot = 0;
            for (int k = 0; k < Math.min(truth.length, found.size()); k++) {
                Graph T = truth[k];
                Graph F = found.get(pm[k]);
                for (Edge e : T.getEdges()) {
                    if (!e.isDirected()) continue;
                    Edge f = F.getEdge(e.getNode1(), e.getNode2());
                    if (f == null || !f.isDirected()) continue;
                    tot++;
                    if (e.getNode1().equals(f.getNode1()) && e.getNode2().equals(f.getNode2())) ok++;
                }
            }
            double acc = tot == 0 ? 0.0 : (double) ok / tot;
            if (acc > best) best = acc;
        }
        return best;
    }

    /**
     * Compare discovered cluster graphs to truths with a robust matching that tolerates size mismatches (e.g., only 1
     * cluster found). Greedy matching by best adj-F1.
     */
    private static GraphMetrics graphMetrics(Graph[] truth, List<Graph> found) {
        if (truth == null || truth.length == 0 || found == null || found.isEmpty()) {
            return new GraphMetrics(0.0, 0.0, Integer.MAX_VALUE / 4);
        }

        int F = found.size();
        boolean[] used = new boolean[F];

        double adjSum = 0.0, arrowSum = 0.0;
        int shdSum = 0, matches = 0;

        for (Graph Gt : truth) {
            double bestAdj = -1.0, bestArrow = 0.0;
            int bestShd = Integer.MAX_VALUE, bestF = -1;

            for (int fi = 0; fi < F; fi++) {
                if (used[fi]) continue;
                Graph Gf = found.get(fi);
                if (Gf == null) continue;

                Metrics m = compareGraphs(Gt, Gf);
                if (m.adjF1 > bestAdj || (m.adjF1 == bestAdj && m.shd < bestShd)) {
                    bestAdj = m.adjF1;
                    bestArrow = m.arrowF1;
                    bestShd = m.shd;
                    bestF = fi;
                }
            }

            if (bestF >= 0) {
                used[bestF] = true;
                adjSum += bestAdj;
                arrowSum += bestArrow;
                shdSum += bestShd;
                matches++;
            } else {
                // Nothing to match: penalize by SHD to an empty graph on same nodes.
                shdSum += structuralHammingDistance(Gt, new EdgeListGraph(Gt.getNodes()));
            }
        }

        if (matches == 0) return new GraphMetrics(0.0, 0.0, shdSum);
        return new GraphMetrics(adjSum / matches, arrowSum / matches, shdSum);
    }

    /**
     * Compare two graphs on (i) adjacency F1; (ii) orientation F1 over the shared skeleton; (iii) SHD.
     */
    private static Metrics compareGraphs(Graph Gt, Graph Gh) {
        if (Gt == null || Gh == null) return new Metrics(0, 0, Integer.MAX_VALUE / 4);

        // Evaluate in equivalence-class space: CPDAGs capture only compelled orientations.
        // Assumes DAG inputs; if an estimator may output non-DAGs, add a DAG check/repair.
        Gt = GraphTransforms.dagToCpdag(Gt);
        Gh = GraphTransforms.dagToCpdag(Gh);

        // --- Adjacency (skeleton) F1
        Set<String> skelT = undirectedEdgeSet(Gt);
        Set<String> skelH = undirectedEdgeSet(Gh);

        Set<String> inter = new HashSet<>(skelT);
        inter.retainAll(skelH);

        int tp = inter.size();
        int fp = Math.max(skelH.size() - tp, 0);
        int fn = Math.max(skelT.size() - tp, 0);

        double precA = tp == 0 ? 0 : (double) tp / (tp + fp);
        double recA = tp == 0 ? 0 : (double) tp / (tp + fn);
        double adjF1 = (precA + recA == 0) ? 0 : 2 * precA * recA / (precA + recA);

        // --- Orientation F1 over shared skeleton
        // Treat a directed edge as an ordered pair "A>B".
        Set<String> dirT = directedEdgeSet(Gt);
        Set<String> dirH = directedEdgeSet(Gh);

        int tpO = 0, fpO = 0, fnO = 0;
        for (String e : inter) {
            String[] ab = e.split("--");
            String a = ab[0], b = ab[1];
            String abDir = a + ">" + b, baDir = b + ">" + a;

            boolean t_ab = dirT.contains(abDir), t_ba = dirT.contains(baDir);
            boolean h_ab = dirH.contains(abDir), h_ba = dirH.contains(baDir);

            // Count TP when both are directed the same way.
            if (t_ab && h_ab) tpO++;
            if (t_ba && h_ba) tpO++;

            // FN: true is directed but hypothesized is either undirected or opposite.
            if (t_ab && !h_ab) fnO++;
            if (t_ba && !h_ba) fnO++;

            // FP: hypothesized is directed but true is either undirected or opposite.
            if (h_ab && !t_ab) fpO++;
            if (h_ba && !t_ba) fpO++;
        }

        double precO = (tpO + fpO) == 0 ? 0 : (double) tpO / (tpO + fpO);
        double recO = (tpO + fnO) == 0 ? 0 : (double) tpO / (tpO + fnO);
        double arrowF1 = (precO + recO == 0) ? 0 : 2 * precO * recO / (precO + recO);

        int shd = structuralHammingDistance(Gt, Gh);
        return new Metrics(adjF1, arrowF1, shd);
    }

    // =========================
    // Common plumbing & metrics
    // =========================

    private static int structuralHammingDistance(Graph A, Graph B) {
        Set<String> EA = directedEdgeSet(A), EB = directedEdgeSet(B);
        Set<String> UA = undirectedEdgeSet(A), UB = undirectedEdgeSet(B);
        // skeleton difference
        Set<String> SA = new HashSet<>(UA);
        SA.addAll(stripDirections(EA));
        Set<String> SB = new HashSet<>(UB);
        SB.addAll(stripDirections(EB));
        Set<String> sym = new HashSet<>(SA);
        sym.removeAll(SB);
        Set<String> sym2 = new HashSet<>(SB);
        sym2.removeAll(SA);
        int skelDiff = sym.size() + sym2.size();
        // orientation differences on common skeleton
        Set<String> inter = new HashSet<>(SA);
        inter.retainAll(SB);
        int orientDiff = 0;
        for (String s : inter) {
            String[] ab = s.split("--");
            String a = ab[0], b = ab[1];
            Edge ea = A.getEdge(A.getNode(a), A.getNode(b));
            Edge eb = B.getEdge(B.getNode(a), B.getNode(b));
            boolean da = ea != null && ea.isDirected();
            boolean db = eb != null && eb.isDirected();
            if (da != db) orientDiff++;
            else if (da) {
                if (!(ea.getNode1().getName().equals(eb.getNode1().getName()) &&
                      ea.getNode2().getName().equals(eb.getNode2().getName()))) {
                    orientDiff++;
                }
            }
        }
        return skelDiff + orientDiff;
    }

    private static Set<String> directedEdgeSet(Graph G) {
        Set<String> s = new HashSet<>();
        for (Edge e : G.getEdges()) if (e.isDirected()) s.add(e.getNode1().getName() + ">" + e.getNode2().getName());
        return s;
    }

    private static Set<String> undirectedEdgeSet(Graph G) {
        Set<String> s = new HashSet<>();
        for (Edge e : G.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            String key = a.compareTo(b) < 0 ? a + "--" + b : b + "--" + a;
            s.add(key);
        }
        return s;
    }

    private static Set<String> stripDirections(Set<String> dir) {
        Set<String> s = new HashSet<>();
        for (String e : dir) {
            String[] ab = e.split(">");
            String key = ab[0].compareTo(ab[1]) < 0 ? ab[0] + "--" + ab[1] : ab[1] + "--" + ab[0];
            s.add(key);
        }
        return s;
    }

    private static List<Graph> searchPerCluster(List<DataSet> parts, java.util.function.Function<DataSet, Graph> perClusterSearch) {
        List<Graph> graphs = new ArrayList<>(parts.size());
        for (DataSet dk : parts) {
            if (dk == null || dk.getNumRows() == 0) graphs.add(null);
            else graphs.add(perClusterSearch.apply(dk));
        }
        return graphs;
    }

    private static java.util.function.Function<DataSet, Graph> makePcMax(double alpha, Pc.ColliderOrientationStyle style) {
        return ds -> {
            try {
                IndTestFisherZ test = new IndTestFisherZ(new CovarianceMatrix(ds), alpha);
                Pc pc = new Pc(test);
                pc.setColliderOrientationStyle(style);
                return pc.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static java.util.function.Function<DataSet, Graph> makeBoss(double penaltyDiscount) {
        return ds -> {
            try {
                SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
                score.setPenaltyDiscount(penaltyDiscount);
                return new PermutationSearch(new Boss(score)).search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    // ---------- Metrics ----------

    private static MixOut shuffleWithLabels(DataSet concat, int[] labels, long seed) {
        int n = concat.getNumRows();
        List<Integer> perm = new ArrayList<>(n);
        for (int i = 0; i < n; i++) perm.add(i);
        Collections.shuffle(perm, new Random(seed));
        DataSet shuffled = concat.subsetRows(perm);
        int[] y = new int[n];
        for (int i = 0; i < n; i++) y[i] = labels[perm.get(i)];
        MixOut out = new MixOut();
        out.data = shuffled;
        out.labels = y;
        return out;
    }

    private static @NotNull Graph copyWithFlippedDirections(Graph g, int flips, Random rnd) {
        Graph h = new EdgeListGraph(g);
        List<Edge> dir = h.getEdges().stream().filter(Edge::isDirected).collect(Collectors.toList());
        if (dir.isEmpty()) return h;
        Collections.shuffle(dir, rnd);
        int done = 0;
        for (Edge e : dir) {
            if (done >= flips) break;
            Node a = e.getNode1(), b = e.getNode2();
            if (!h.isAdjacentTo(a, b)) continue;
            h.removeEdge(e);
            Edge rev = Edges.directedEdge(b, a);
            if (!h.isAdjacentTo(b, a)) {
                h.addEdge(rev);
                done++;
            } else {
                h.addEdge(e);
            }
        }
        return h;
    }

    private static double adjustedRandIndex(int[] a, int[] b) {
        int n = a.length;
        int maxA = Arrays.stream(a).max().orElse(0);
        int maxB = Arrays.stream(b).max().orElse(0);
        int[][] M = new int[maxA + 1][maxB + 1];
        int[] row = new int[maxA + 1], col = new int[maxB + 1];
        for (int i = 0; i < n; i++) {
            M[a[i]][b[i]]++;
            row[a[i]]++;
            col[b[i]]++;
        }
        double sumComb = 0, rowComb = 0, colComb = 0;
        for (int i = 0; i <= maxA; i++) for (int j = 0; j <= maxB; j++) sumComb += comb2(M[i][j]);
        for (int i = 0; i <= maxA; i++) rowComb += comb2(row[i]);
        for (int j = 0; j <= maxB; j++) colComb += comb2(col[j]);
        double totalComb = comb2(n);
        double exp = rowComb * colComb / totalComb;
        double max = 0.5 * (rowComb + colComb);
        return (sumComb - exp) / (max - exp + 1e-12);
    }

    private static double comb2(int m) {
        return m < 2 ? 0 : m * (m - 1) / 2.0;
    }

    private static double median(List<Double> xs) {
        double[] v = xs.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int n = v.length;
        return (n % 2 == 1) ? v[n / 2] : 0.5 * (v[n / 2 - 1] + v[n / 2]);
    }

    private static double iqr(List<Double> xs) {
        double[] v = xs.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int n = v.length;
        double q1 = v[(int) Math.floor(0.25 * (n - 1))];
        double q3 = v[(int) Math.floor(0.75 * (n - 1))];
        return q3 - q1;
    }

    @Test
    public void phase1_controlledSweep() {
        int p = 12;
        int nPer = 1000;
        long seed = 11;

        // Scenarios: params-only, small flips, large flips
        Scenario[] scenarios = new Scenario[]{
                Scenario.paramsOnly(p, nPer, nPer, /*coefScale*/2.0, /*noiseScale*/2.5, NoiseFamily.LAPLACE, seed),
                Scenario.smallTopoFlip(p, nPer, nPer, /*flips*/4, NoiseFamily.LAPLACE, seed),
                Scenario.largeTopoFlip(p, nPer, nPer, /*flips*/10, NoiseFamily.LAPLACE, seed)
        };

        for (Scenario sc : scenarios) {
            System.out.println("\n=== Phase1: " + sc.name + " | noise=" + sc.noise + " ===");

            // --- EmUnmix config (no graphers inside EmUnmix) ---
            EmUnmix.Config ec = new EmUnmix.Config();
            ec.K = 2;
            ec.useParentSuperset = true;
            ec.supersetCfg.topM = 10;
            ec.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL;
            ec.robustScaleResiduals = true;
            ec.covType = (sc.mixed.getNumColumns() <= 20)
                    ? GaussianMixtureEM.CovarianceType.FULL
                    : GaussianMixtureEM.CovarianceType.DIAGONAL;
            ec.emMaxIters = 200;
            ec.kmeansRestarts = 10;

            // --- Unmix (K fixed = 2) ---
            long s0 = System.currentTimeMillis();
            UnmixResult rEM = EmUnmix.run(sc.mixed, ec, new LinearQRRegressor());
            long s1 = System.currentTimeMillis();

            // --- Unmix with selectK outside (unknown K, 1..4) ---
            UnmixResult rEMbest = EmUnmix.selectK(sc.mixed, 1, 4, new LinearQRRegressor(), ec);

            // === External per-cluster graph learning ===
            // Default: BOSS (+ PermutationSearch) per component
            java.util.function.Function<DataSet, Graph> perCluster = ds -> {
                try {
                    SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
                    score.setPenaltyDiscount(2.0); // tweak if desired
                    return new PermutationSearch(new Boss(score)).search();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };
            // If you prefer PC-Max instead, swap the lambda above for this:
            // java.util.function.Function<DataSet, Graph> perCluster = ds -> {
            //     try {
            //         IndTestFisherZ test = new IndTestFisherZ(new CovarianceMatrix(ds), 0.01);
            //         Pc pc = new Pc(test);
            //         pc.setColliderOrientationStyle(Pc.ColliderOrientationStyle.MAX_P);
            //         return pc.search();
            //     } catch (InterruptedException e) {
            //         throw new RuntimeException(e);
            //     }
            // };

            // Build graphs for each cluster externally
            List<Graph> gEM = new ArrayList<>();
            for (DataSet dk : rEM.clusterData) {
                if (dk == null || dk.getNumRows() == 0) gEM.add(null);
                else gEM.add(perCluster.apply(dk));
            }
            List<Graph> gEMbest = new ArrayList<>();
            for (DataSet dk : rEMbest.clusterData) {
                if (dk == null || dk.getNumRows() == 0) gEMbest.add(null);
                else gEMbest.add(perCluster.apply(dk));
            }

            // --- Metrics (clustering) ---
            double ariEM = adjustedRandIndex(sc.labels, rEM.labels);
            double ariEMbest = adjustedRandIndex(sc.labels, rEMbest.labels);

            // --- Graph metrics per regime (vs. truth) ---
            GraphMetrics gmEM = graphMetrics(sc.truthGraphs, gEM);
            GraphMetrics gmEMbest = graphMetrics(sc.truthGraphs, gEMbest);

            System.out.printf("EM (K=2):             ARI=%.3f  AdjF1=%.3f  ArrowF1=%.3f  SHD=%d  time=%dms%n",
                    ariEM, gmEM.adjF1, gmEM.arrowF1, gmEM.shd, (s1 - s0));
            System.out.printf("EM (selectK):         ARI=%.3f  AdjF1=%.3f  ArrowF1=%.3f  SHD=%d%n",
                    ariEMbest, gmEMbest.adjF1, gmEMbest.arrowF1, gmEMbest.shd);

            // --- Raw-X EM baseline (z-score columns), unchanged ---
            double[][] X = new double[sc.mixed.getNumRows()][sc.mixed.getNumColumns()];
            for (int i = 0; i < sc.mixed.getNumRows(); i++) {
                for (int j = 0; j < sc.mixed.getNumColumns(); j++) {
                    X[i][j] = sc.mixed.getDouble(i, j);
                }
            }
            // z-score columns
            for (int j = 0; j < X[0].length; j++) {
                double mean = 0, m2 = 0;
                for (double[] x : X) mean += x[j];
                mean /= X.length;
                for (double[] x : X) {
                    double d = x[j] - mean;
                    m2 += d * d;
                }
                double sd = Math.sqrt(m2 / Math.max(1, X.length - 1));
                if (sd < 1e-12) sd = 1.0;
                for (int i = 0; i < X.length; i++) X[i][j] = (X[i][j] - mean) / sd;
            }

            GaussianMixtureEM.Config gx = new GaussianMixtureEM.Config();
            gx.K = 2;
            gx.covType = GaussianMixtureEM.CovarianceType.FULL;
            gx.maxIters = 200;
            gx.kmeansRestarts = 10;

            long rx0 = System.currentTimeMillis();
            GaussianMixtureEM.Model mx = GaussianMixtureEM.fit(X, gx);
            int[] zRaw = EmUtils.mapLabels(mx.responsibilities);
            long rx1 = System.currentTimeMillis();

            double ariRaw = adjustedRandIndex(sc.labels, zRaw);
            System.out.printf("EM (raw X, K=2):      ARI=%.3f  time=%dms%n", ariRaw, (rx1 - rx0));

            double arrBothEM = arrowAccBothOriented(sc.truthGraphs, gEM);
            double arrBothEMbest = arrowAccBothOriented(sc.truthGraphs, gEMbest);
            System.out.printf("ArrowOK(both oriented): EM=%.3f  EM(bestK)=%.3f%n", arrBothEM, arrBothEMbest);
        }
    }

    @Test
    public void phase2_robustness() {
        int p = 12;
        long seed = 2949392L;
        int[] nTotals = {600, 800, 1200};
        double[] imbalances = {0.5, 0.3, 0.2}; // fraction in regime A
        double[] signalScales = {1.0, 1.3, 1.6, 2.0}; // 1.0 = weak separation
        int flips = 8;
        int repeats = 10;

        System.out.println("\n=== Phase2: robustness curves (mean±IQR over seeds) ===");
        for (int nTot : nTotals) {
            for (double fracA : imbalances) {
                int n1 = (int) Math.round(nTot * fracA);
                int n2 = nTot - n1;
                for (double sig : signalScales) {
                    List<Double> arisEM = new ArrayList<>();
                    for (int r = 0; r < repeats; r++) {
                        long s = seed + 1000L * r;
                        Scenario sc = Scenario.smallTopoFlipParamScaled(p, n1, n2, flips, sig, NoiseFamily.LAPLACE, s);

                        EmUnmix.Config ec = new EmUnmix.Config();
                        ec.K = 2;
                        ec.useParentSuperset = true;
                        ec.supersetCfg.topM = 10;
                        ec.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.SPEARMAN;
                        ec.robustScaleResiduals = false;
                        ec.covType = GaussianMixtureEM.CovarianceType.FULL;
                        ec.emMaxIters = 400;
                        ec.kmeansRestarts = 10;

                        UnmixResult rEM = EmUnmix.run(sc.mixed, ec, new LinearQRRegressor());
                        arisEM.add(adjustedRandIndex(sc.labels, rEM.labels));
                    }
                    String tag = String.format("n=%d  fracA=%.2f  signal=%.1f", nTot, fracA, sig);
                    System.out.printf("%s | ARI-EM median=%.3f IQR=%.3f%n",
                            tag,
                            median(arisEM), iqr(arisEM));
                }
            }
        }
    }

    @Test
    public void phase3_semisynthetic() {
        // Use a backbone covariance from a single SEM sample, then inject shifts.
        int p = 15, n1 = 900, n2 = 900, flips = 5;
        long seed = 33;

        // Backbone DAG & sample (Laplace errors for heavier tails)
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("X" + i));
        Graph gBackbone = RandomGraph.randomGraph(vars, 0, 20, 100, 100, 100, false);

        Parameters params = new Parameters();
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm imBack = new SemIm(new SemPm(gBackbone), params);
        DataSet Dreal = imBack.simulateData(n1 + n2, false); // “realistic” marginal structure

        // Now create two regimes by injecting controlled shifts on top of Dreal’s covariance:
        // (A) keep backbone; (B) flip edges & scale some parameters — simulate from shifted SEMs.
        Graph gA = gBackbone.copy();
        Graph gB = copyWithFlippedDirections(gBackbone, flips, new Random(seed));
        SemIm imA = new SemIm(new SemPm(gA), params);
        SemIm imB = new SemIm(new SemPm(gB), params);
        // Scale B
        double coefScale = 1.6, noiseScale = 1.8;
        for (Edge e : gB.getEdges()) {
            try {
                double b = imB.getEdgeCoef(e);
                imB.setEdgeCoef(e.getNode1(), e.getNode2(), coefScale * b);
            } catch (Exception ignore) {
            }
        }
        for (Node v : vars) imB.setErrVar(v, noiseScale * imB.getErrVar(v));

        DataSet dA = imA.simulateData(n1, false);
        DataSet dB = imB.simulateData(n2, false);
        DataSet concat = DataTransforms.concatenate(dA, dB);
        int[] lab = new int[n1 + n2];
        Arrays.fill(lab, 0, n1, 0);
        Arrays.fill(lab, n1, n1 + n2, 1);
        MixOut mix = shuffleWithLabels(concat, lab, seed);

        EmUnmix.Config ec = new EmUnmix.Config();
        ec.K = 2;
        ec.useParentSuperset = true;
        ec.supersetCfg.topM = 12;
        ec.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL;
        ec.robustScaleResiduals = true;
        ec.covType = GaussianMixtureEM.CovarianceType.FULL;
        ec.emMaxIters = 200;
        ec.kmeansRestarts = 10;

        UnmixResult rEM = EmUnmix.run(mix.data, ec, new LinearQRRegressor());

// --- external per-cluster graph learning (same pattern as Phase 1) ---
        java.util.function.Function<DataSet, Graph> perCluster = ds -> {
            try {
                SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
                score.setPenaltyDiscount(2.0); // tweak if desired
                return new PermutationSearch(new Boss(score)).search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        // If you prefer PC-Max instead, swap for:
        // java.util.function.Function<DataSet, Graph> perCluster = ds -> {
        //     try {
        //         IndTestFisherZ test = new IndTestFisherZ(new CovarianceMatrix(ds), 0.01);
        //         Pc pc = new Pc(test);
        //         pc.setColliderOrientationStyle(Pc.ColliderOrientationStyle.MAX_P);
        //         return pc.search();
        //     } catch (InterruptedException e) { throw new RuntimeException(e); }
        // };

        // Build graphs for each cluster externally
        List<Graph> gEM = new ArrayList<>();
        for (DataSet dk : rEM.clusterData) {
            if (dk == null || dk.getNumRows() == 0) gEM.add(null);
            else gEM.add(perCluster.apply(dk));
        }

        Graph[] truth = new Graph[]{gA, gB};
        GraphMetrics gmEM = graphMetrics(truth, gEM);

        System.out.printf("\n=== Phase3 (semi-synth) ===%n");
        System.out.printf("EM:          ARI=%.3f  AdjF1=%.3f  ArrowF1=%.3f  SHD=%d%n",
                adjustedRandIndex(mix.labels, rEM.labels), gmEM.adjF1, gmEM.arrowF1, gmEM.shd);
    }

    //    @Test
    public EmUnmix.Config makeDefaultEc(DataSet data, int K) {
        // For residual-EM (your EmUnmix pipeline)
        EmUnmix.Config ec = new EmUnmix.Config();
        ec.K = 2;                               // or leave unset and use selectK (below)
        ec.useParentSuperset = true;            // if you want the superset trick
        ec.supersetCfg.topM = 10;
        ec.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL;
        ec.robustScaleResiduals = true;

        // Covariance: FULL if safe, else DIAGONAL
        int p = data.getNumColumns();
        int n = data.getNumRows();
        boolean okFull = (n / Math.max(1, K)) >= (p + 10);
        ec.covType = okFull ? GaussianMixtureEM.CovarianceType.FULL
                : GaussianMixtureEM.CovarianceType.DIAGONAL;

        // Stable EM
        ec.kmeansRestarts = 20;                 // robust init
        ec.emMaxIters = 300;
        ec.covRidgeRel = 1e-3;                  // well-conditioned Σ
        ec.covShrinkage = 0.10;                 // mild shrinkage
        ec.annealSteps = 15;                   // tempered EM helps in tougher cases
        ec.annealStartT = 0.8;

        return ec;
    }

    // ---------- Basic helpers reused from prior tests ----------

    public void defaultsRawXEM(DataSet data) {
        GaussianMixtureEM.Config gx = new GaussianMixtureEM.Config();
        gx.K = 2;
        int p = data.getNumColumns();
        int n = data.getNumRows();
        boolean okFull = (n / Math.max(1, gx.K)) >= (p + 10);
        gx.covType = okFull ? GaussianMixtureEM.CovarianceType.FULL
                : GaussianMixtureEM.CovarianceType.DIAGONAL;
        gx.kmeansRestarts = 20;
        gx.maxIters = 300;
        gx.covRidgeRel = 1e-3;
        gx.covShrinkage = 0.10;
        gx.annealSteps = 15;
        gx.annealStartT = 0.8;
        // remember to z-score columns before fit(X, gx)
    }

    private enum NoiseFamily {GAUSSIAN, LAPLACE}

    private static class Scenario {
        final String name;
        final Kind kind;
        final NoiseFamily noise;
        final DataSet mixed;
        final int[] labels;
        final Graph[] truthGraphs;

        private Scenario(String name, Kind kind, NoiseFamily noise, DataSet mixed, int[] labels, Graph[] truthGraphs) {
            this.name = name;
            this.kind = kind;
            this.noise = noise;
            this.mixed = mixed;
            this.labels = labels;
            this.truthGraphs = truthGraphs;
        }

        static Scenario paramsOnly(int p, int n1, int n2, double coefScale, double noiseScale, NoiseFamily nf, long seed) {
            List<Node> vars = new ArrayList<>();
            for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("X" + i));
            Graph g = RandomGraph.randomGraph(vars, 0, 12, 100, 100, 100, false);
            Parameters par = new Parameters();
            setNoise(par, nf);

            SemIm imA = new SemIm(new SemPm(g), par);
            SemIm imB = new SemIm(new SemPm(g), par);
            for (Edge e : g.getEdges()) {
                try {
                    double b = imB.getEdgeCoef(e);
                    imB.setEdgeCoef(e.getNode1(), e.getNode2(), coefScale * b);
                } catch (Exception ignore) {
                }
            }
            for (Node v : vars) imB.setErrVar(v, noiseScale * imB.getErrVar(v));

            DataSet d1 = imA.simulateData(n1, false);
            DataSet d2 = imB.simulateData(n2, false);
            int[] lab = labelVec(n1, n2);
            return new Scenario("ParamsOnly", Kind.PARAMS_ONLY, nf, shuffleWithLabels(DataTransforms.concatenate(d1, d2), lab, seed).data,
                    lab, new Graph[]{g.copy(), g.copy()});
        }

        static Scenario smallTopoFlip(int p, int n1, int n2, int flips, NoiseFamily nf, long seed) {
            return topoFlip("SmallFlip", Kind.SMALL_FLIP, p, n1, n2, flips, nf, seed, 1.0);
        }

        static Scenario largeTopoFlip(int p, int n1, int n2, int flips, NoiseFamily nf, long seed) {
            return topoFlip("LargeFlip", Kind.LARGE_FLIP, p, n1, n2, flips, nf, seed, 1.0);
        }

        static Scenario smallTopoFlipParamScaled(int p, int n1, int n2, int flips, double scale, NoiseFamily nf, long seed) {
            return topoFlip("SmallFlipParamScaled(" + scale + ")", Kind.SMALL_FLIP, p, n1, n2, flips, nf, seed, scale);
        }

        private static Scenario topoFlip(String name, Kind kind, int p, int n1, int n2, int flips,
                                         NoiseFamily nf, long seed, double paramScaleB) {
            List<Node> vars = new ArrayList<>();
            for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("X" + i));
            Graph gA = RandomGraph.randomGraph(vars, 0, 14, 100, 100, 100, false);
            Graph gB = copyWithFlippedDirections(gA, flips, new Random(seed));
            Parameters par = new Parameters();
            setNoise(par, nf);
            SemIm imA = new SemIm(new SemPm(gA), par);
            SemIm imB = new SemIm(new SemPm(gB), par);
            if (paramScaleB != 1.0) {
                for (Edge e : gB.getEdges()) {
                    try {
                        double b = imB.getEdgeCoef(e);
                        imB.setEdgeCoef(e.getNode1(), e.getNode2(), paramScaleB * b);
                    } catch (Exception ignore) {
                    }
                }
                for (Node v : vars) imB.setErrVar(v, paramScaleB * imB.getErrVar(v));
            }
            DataSet d1 = imA.simulateData(n1, false);
            DataSet d2 = imB.simulateData(n2, false);
            int[] lab = labelVec(n1, n2);
            MixOut mix = shuffleWithLabels(DataTransforms.concatenate(d1, d2), lab, seed);
            return new Scenario(name, kind, nf, mix.data, mix.labels, new Graph[]{gA, gB});
        }

        private static void setNoise(Parameters par, NoiseFamily nf) {
            if (nf == NoiseFamily.GAUSSIAN) {
                par.set(Params.SIMULATION_ERROR_TYPE, 0); // Gaussian
            } else {
                par.set(Params.SIMULATION_ERROR_TYPE, 3); // Laplace-like (as used before)
                par.set(Params.SIMULATION_PARAM1, 1);
            }
        }

        private static int[] labelVec(int n1, int n2) {
            int[] lab = new int[n1 + n2];
            Arrays.fill(lab, 0, n1, 0);
            Arrays.fill(lab, n1, n1 + n2, 1);
            return lab;
        }

        enum Kind {PARAMS_ONLY, SMALL_FLIP, LARGE_FLIP}
    }

    private static class GraphMetrics {
        final double adjF1, arrowF1;
        final int shd;

        GraphMetrics(double adjF1, double arrowF1, int shd) {
            this.adjF1 = adjF1;
            this.arrowF1 = arrowF1;
            this.shd = shd;
        }
    }

    private static class Metrics {
        final double adjF1, arrowF1;
        final int shd;

        Metrics(double a, double b, int s) {
            adjF1 = a;
            arrowF1 = b;
            shd = s;
        }
    }

    private static class MixOut {
        DataSet data;
        int[] labels;
    }
}