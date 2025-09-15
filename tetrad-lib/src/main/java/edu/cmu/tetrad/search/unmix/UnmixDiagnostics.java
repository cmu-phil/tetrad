package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.*;
import java.util.function.Function;

public final class UnmixDiagnostics {

    private UnmixDiagnostics() {
    }

    // ---------- A. BIC(K=1) vs BIC(K=2) ----------

    public static BicDelta computeBicDeltaK1K2(
            DataSet data,
            EmUnmix.Config baseCfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooled,
            Function<DataSet, Graph> perCluster) {

        // Clone config to keep everything identical except K
        EmUnmix.Config c1 = baseCfg.copy();
        c1.K = 1;
        EmUnmix.Config c2 = baseCfg.copy();
        c2.K = 2;

        UnmixResult r1 = EmUnmix.run(data, c1, regressor, pooled, perCluster);
        UnmixResult r2 = EmUnmix.run(data, c2, regressor, pooled, perCluster);

        int n = data.getNumRows();
        double bic1 = r1.gmmModel.bic(n);
        double bic2 = r2.gmmModel.bic(n);
        return new BicDelta(bic1, bic2, bic1 - bic2);
    }

    public static EntropyStats computeEntropyStats(double[][] responsibilities) {
        int n = responsibilities.length;
        int K = responsibilities[0].length;

        double logK = Math.log(K);
        double sumH = 0.0;
        int confident90 = 0, confident80 = 0;

        for (int i = 0; i < n; i++) {
            double max = 0.0, Hi = 0.0;
            for (int k = 0; k < K; k++) {
                double r = Math.max(responsibilities[i][k], 1e-15);
                Hi -= r * Math.log(r);
                if (r > max) max = r;
            }
            sumH += Hi / logK; // normalized to [0,1]
            if (max >= 0.90) confident90++;
            if (max >= 0.80) confident80++;
        }

        return new EntropyStats(
                sumH / n,
                confident90 / (double) n,
                confident80 / (double) n
        );
    }

    // ---------- B. Soft-assignment entropy & confident fraction ----------

    public static StabilityResult stabilityAcrossRestarts(
            DataSet data,
            EmUnmix.Config cfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooled,
            Function<DataSet, Graph> perCluster,
            int repeats,
            long seedBase) {

        List<int[]> labelings = new ArrayList<>(repeats);
        Random rnd = new Random(seedBase);

        for (int r = 0; r < repeats; r++) {
            EmUnmix.Config c = cfg.copy();
            c.randomSeed = rnd.nextLong();    // if you support seeds
            UnmixResult res = EmUnmix.run(data, c, regressor, pooled, perCluster);
            labelings.add(res.labels);
        }

        // pairwise ARI
        int m = labelings.size();
        List<Double> aris = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                aris.add(adjustedRandIndex(labelings.get(i), labelings.get(j)));
            }
        }
        double mean = aris.stream().mapToDouble(x -> x).average().orElse(Double.NaN);
        double sd = Math.sqrt(aris.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / Math.max(1, aris.size() - 1));
        return new StabilityResult(mean, sd, aris.size());
    }

    public static double heldoutPerSampleLoglikGain(
            double[][] trainFeatures,
            double[][] testFeatures,
            GaussianMixtureEM.Config baseGmmCfg) {

        // Fit K=1 and K=2 on TRAIN
        GaussianMixtureEM.Config c1 = baseGmmCfg.copy();
        c1.K = 1;
        GaussianMixtureEM.Config c2 = baseGmmCfg.copy();
        c2.K = 2;

        GaussianMixtureEM.Model m1 = GaussianMixtureEM.fit(trainFeatures, c1);
        GaussianMixtureEM.Model m2 = GaussianMixtureEM.fit(trainFeatures, c2);

        double ll1 = averageLogLikelihood(testFeatures, m1);
        double ll2 = averageLogLikelihood(testFeatures, m2);
        return ll2 - ll1; // >0 means K=2 generalizes better
    }

    // ---------- C. Stability across restarts (mean ARI) ----------

    public static GraphDiff compareClusterGraphsCpdag(Graph g1, Graph g2) {
        if (g1 == null || g2 == null) {
            return new GraphDiff(0.0, 0.0, Integer.MAX_VALUE / 4);
        }
        Graph Gt = edu.cmu.tetrad.graph.GraphTransforms.dagToCpdag(g1);
        Graph Gh = edu.cmu.tetrad.graph.GraphTransforms.dagToCpdag(g2);

        Set<String> skelT = undirectedEdgeSet(Gt);
        Set<String> skelH = undirectedEdgeSet(Gh);

        Set<String> inter = new HashSet<>(skelT);
        inter.retainAll(skelH);
        int tp = inter.size(), fp = Math.max(skelH.size() - tp, 0), fn = Math.max(skelT.size() - tp, 0);
        double precA = tp == 0 ? 0 : tp / (double) (tp + fp);
        double recA = tp == 0 ? 0 : tp / (double) (tp + fn);
        double adjF1 = (precA + recA == 0) ? 0.0 : 2 * precA * recA / (precA + recA);

        // Orientation F1 over shared skeleton (directed arcs in CPDAGs)
        Set<String> dirT = directedEdgeSet(Gt);
        Set<String> dirH = directedEdgeSet(Gh);
        int tpO = 0, fpO = 0, fnO = 0;
        for (String e : inter) {
            String[] ab = e.split("--");
            String a = ab[0], b = ab[1];
            String abDir = a + ">" + b, baDir = b + ">" + a;

            boolean t_ab = dirT.contains(abDir), t_ba = dirT.contains(baDir);
            boolean h_ab = dirH.contains(abDir), h_ba = dirH.contains(baDir);

            if (t_ab && h_ab) tpO++;
            if (t_ba && h_ba) tpO++;
            if (t_ab && !h_ab) fnO++;
            if (t_ba && !h_ba) fnO++;
            if (h_ab && !t_ab) fpO++;
            if (h_ba && !t_ba) fpO++;
        }
        double precO = (tpO + fpO) == 0 ? 0 : tpO / (double) (tpO + fpO);
        double recO = (tpO + fnO) == 0 ? 0 : tpO / (double) (tpO + fnO);
        double arrowF1 = (precO + recO == 0) ? 0.0 : 2 * precO * recO / (precO + recO);

        int shd = structuralHammingDistance(Gt, Gh);
        return new GraphDiff(adjF1, arrowF1, shd);
    }

    public static double averageLogLikelihood(double[][] X, GaussianMixtureEM.Model m) {
        int n = X.length, d = X[0].length, K = m.weights.length;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double lik = 0.0;
            for (int k = 0; k < K; k++) {
                lik += m.weights[k] * GaussianMixtureEM.gaussianPdf(X[i], m.means[k], m.covs[k], m.covType);
            }
            sum += Math.log(Math.max(lik, 1e-300));
        }
        return sum / n;
    }

    // ---------- D. Held-out log-likelihood gain (K=2 vs K=1) ----------
    // This version expects you to expose the EM feature matrix builder used inside EmUnmix.
    // If you can supply trainFeatures/testFeatures, this will score the GMMs on held-out.

    public static double adjustedRandIndex(int[] a, int[] b) {
        int n = a.length;
        int maxA = 0, maxB = 0;
        for (int v : a) if (v > maxA) maxA = v;
        for (int v : b) if (v > maxB) maxB = v;
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

    // ---------- E. Graph divergence between clusters (CPDAG-aware) ----------

    private static double comb2(int m) {
        return m < 2 ? 0 : m * (m - 1) / 2.0;
    }

    private static Set<String> directedEdgeSet(Graph G) {
        Set<String> s = new HashSet<>();
        for (edu.cmu.tetrad.graph.Edge e : G.getEdges())
            if (e.isDirected())
                s.add(e.getNode1().getName() + ">" + e.getNode2().getName());
        return s;
    }

    // ---------- F. Helper: average log-likelihood of a GMM on features ----------

    private static Set<String> undirectedEdgeSet(Graph G) {
        Set<String> s = new HashSet<>();
        for (edu.cmu.tetrad.graph.Edge e : G.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            String key = a.compareTo(b) < 0 ? a + "--" + b : b + "--" + a;
            s.add(key);
        }
        return s;
    }

    // ---------- G. Helper: ARI (copy local to avoid deps) ----------

    private static int structuralHammingDistance(Graph A, Graph B) {
        Set<String> EA = directedEdgeSet(A), EB = directedEdgeSet(B);
        Set<String> UA = undirectedEdgeSet(A), UB = undirectedEdgeSet(B);
        Set<String> SA = new HashSet<>(UA);
        SA.addAll(stripDirections(EA));
        Set<String> SB = new HashSet<>(UB);
        SB.addAll(stripDirections(EB));
        Set<String> sym = new HashSet<>(SA);
        sym.removeAll(SB);
        Set<String> sym2 = new HashSet<>(SB);
        sym2.removeAll(SA);
        int skelDiff = sym.size() + sym2.size();
        Set<String> inter = new HashSet<>(SA);
        inter.retainAll(SB);
        int orientDiff = 0;
        for (String s : inter) {
            String[] ab = s.split("--");
            String a = ab[0], b = ab[1];
            edu.cmu.tetrad.graph.Edge ea = A.getEdge(A.getNode(a), A.getNode(b));
            edu.cmu.tetrad.graph.Edge eb = B.getEdge(B.getNode(a), B.getNode(b));
            boolean da = ea != null && ea.isDirected();
            boolean db = eb != null && eb.isDirected();
            if (da != db) orientDiff++;
            else if (da && db) {
                if (!(ea.getNode1().getName().equals(eb.getNode1().getName())
                      && ea.getNode2().getName().equals(eb.getNode2().getName()))) {
                    orientDiff++;
                }
            }
        }
        return skelDiff + orientDiff;
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

    // ---------- H. Tiny graph set helpers (CPDAG comparison uses these) ----------

    public static final class BicDelta {
        public final double bicK1, bicK2, delta; // delta = BIC(1) - BIC(2)

        public BicDelta(double bicK1, double bicK2, double delta) {
            this.bicK1 = bicK1;
            this.bicK2 = bicK2;
            this.delta = delta;
        }
    }

    public static final class EntropyStats {
        public final double meanEntropy;   // 0 = crisp, 1 = uniform
        public final double fracConf90;    // fraction with max r_ik >= 0.90
        public final double fracConf80;    // fraction with max r_ik >= 0.80

        public EntropyStats(double meanEntropy, double fracConf90, double fracConf80) {
            this.meanEntropy = meanEntropy;
            this.fracConf90 = fracConf90;
            this.fracConf80 = fracConf80;
        }
    }

    public static final class StabilityResult {
        public final double meanARI, sdARI;
        public final int numPairs;

        public StabilityResult(double meanARI, double sdARI, int numPairs) {
            this.meanARI = meanARI;
            this.sdARI = sdARI;
            this.numPairs = numPairs;
        }
    }

    public static final class GraphDiff {
        public final double adjacencyF1, arrowF1;
        public final int shd;

        public GraphDiff(double adjacencyF1, double arrowF1, int shd) {
            this.adjacencyF1 = adjacencyF1;
            this.arrowF1 = arrowF1;
            this.shd = shd;
        }
    }
}