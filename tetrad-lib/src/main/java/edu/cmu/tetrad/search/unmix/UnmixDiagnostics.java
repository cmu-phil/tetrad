/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.*;
import java.util.function.Function;

/**
 * The {@code UnmixDiagnostics} class provides utilities for evaluating and diagnosing results from unsupervised mixture
 * models, such as Gaussian Mixture Models and related clustering techniques. It includes methods for assessing model
 * fit, stability across different runs, and cluster agreement. The class also provides metrics such as the Bayesian
 * Information Criterion (BIC) difference, entropy statistics, and log-likelihood. Comparisons between cluster graphs
 * are supported as well.
 * <p>
 * This class only offers static utility methods and does not allow direct instantiation.
 */
public final class UnmixDiagnostics {

    private UnmixDiagnostics() {
    }

    // ---------- A. BIC(K=1) vs BIC(K=2) ----------

    /**
     * Computes the Bayesian Information Criterion (BIC) for mixture models with K=1 and K=2 clusters, and calculates
     * the BIC difference (delta) between them.
     *
     * @param data       the dataset to evaluate using the EM algorithm.
     * @param baseCfg    the configuration template for the EM algorithm, which will be cloned and modified for K=1 and
     *                   K=2.
     * @param regressor  a residual regressor used during model evaluation.
     * @param pooled     a function mapping the dataset to a pooled graph.
     * @param perCluster a function mapping the dataset to a graph per cluster.
     * @return a BicDelta object containing the BIC values for K=1, K=2, and their difference (delta).
     */
    public static BicDelta computeBicDeltaK1K2(DataSet data, EmUnmix.Config baseCfg, ResidualRegressor regressor, Function<DataSet, Graph> pooled, Function<DataSet, Graph> perCluster) {

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

    /**
     * Computes entropy statistics for a given responsibility matrix in the context of mixture models. The method
     * calculates the mean normalized entropy, the fraction of data points with a maximum responsibility value of at
     * least 0.90, and the fraction of data points with a maximum responsibility value of at least 0.80.
     *
     * @param responsibilities a 2D array where each row represents the responsibilities of a single data point across
     *                         all clusters and each column corresponds to a specific cluster.
     * @return an {@code EntropyStats} object that contains the mean entropy, the fraction of data points with high
     * confidence (>= 90%), and the fraction with moderately high confidence (>= 80%) for the maximum responsibility
     * across clusters.
     */
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

        return new EntropyStats(sumH / n, confident90 / (double) n, confident80 / (double) n);
    }

    // ---------- B. Soft-assignment entropy & confident fraction ----------

    /**
     * Evaluates the stability of clustering results across multiple independent runs of the EM algorithm by calculating
     * the average Adjusted Rand Index (ARI) and its standard deviation between all pairs of runs. This method repeats
     * the clustering process multiple times with differing random seeds and compares the resulting cluster labelings to
     * assess reproducibility.
     *
     * @param data       the dataset to be clustered using the EM algorithm.
     * @param cfg        the configuration for the EM algorithm. A copy of this configuration is used for each run.
     * @param regressor  a residual regressor used during the clustering process.
     * @param pooled     a function mapping the dataset to a pooled graph representation.
     * @param perCluster a function mapping the dataset to a graph representation for each cluster.
     * @param repeats    the number of independent clustering runs to perform.
     * @param seedBase   the base value for generating random seeds used across the runs.
     * @return a StabilityResult object containing the mean ARI, the standard deviation of the ARI values, and the total
     * number of pairwise comparisons.
     */
    public static StabilityResult stabilityAcrossRestarts(DataSet data, EmUnmix.Config cfg, ResidualRegressor regressor, Function<DataSet, Graph> pooled, Function<DataSet, Graph> perCluster, int repeats, long seedBase) {

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

    /**
     * Computes the difference in per-sample log-likelihoods on a held-out test set between Gaussian Mixture Models
     * (GMMs) trained with K=2 and K=1 clusters. This method evaluates the generalization performance of GMMs with
     * differing numbers of clusters on unseen data.
     *
     * @param trainFeatures a 2D array of training data features used to fit the GMMs.
     * @param testFeatures  a 2D array of test data features used to evaluate the GMMs.
     * @param baseGmmCfg    the base configuration for fitting the GMMs. This configuration will be cloned and modified
     *                      for models with K=1 and K=2 clusters.
     * @return the difference in average per-sample log-likelihoods (test set) between the K=2 and K=1 models. A
     * positive value indicates that the model with K=2 clusters generalizes better.
     */
    public static double heldoutPerSampleLoglikGain(double[][] trainFeatures, double[][] testFeatures, GaussianMixtureEM.Config baseGmmCfg) {

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

    /**
     * Compares two clustering graphs by transforming them into CPDAGs (Completed Partially Directed Acyclic Graphs),
     * and calculates the adjacency F1 score, arrow F1 score, and Structural Hamming Distance (SHD) between the graphs.
     *
     * @param g1 the first graph to compare, typically representing a clustering structure.
     * @param g2 the second graph to compare, typically representing a clustering structure.
     * @return a GraphDiff object containing the adjacency F1 score, arrow F1 score, and the SHD value between the
     * transformed CPDAGs of the two input graphs.
     */
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

    /**
     * Computes the average log-likelihood of the data under a given Gaussian Mixture Model (GMM).
     *
     * @param X a 2D array where each row represents a data point and each column represents a feature.
     * @param m the Gaussian Mixture Model containing the weights, means, covariance matrices, and covariance type.
     * @return the average log-likelihood of the dataset under the specified GMM.
     */
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

    /**
     * Computes the Adjusted Rand Index (ARI) to measure the similarity between two cluster labelings. The ARI adjusts
     * the Rand Index by taking into account the probability of a chance agreement between the two clusterings. It
     * ranges from -1 (completely dissimilar) to 1 (perfectly identical). A value of 0 indicates randomness.
     *
     * @param a an array of integers where each element represents the cluster label for a data point as assigned by the
     *          first clustering.
     * @param b an array of integers where each element represents the cluster label for the same data points as
     *          assigned by the second clustering. The arrays must have the same length as the first clustering.
     * @return the Adjusted Rand Index (ARI) as a double, representing the similarity between the two cluster labelings.
     */
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
            if (e.isDirected()) s.add(e.getNode1().getName() + ">" + e.getNode2().getName());
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
                if (!(ea.getNode1().getName().equals(eb.getNode1().getName()) && ea.getNode2().getName().equals(eb.getNode2().getName()))) {
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

    /**
     * Represents the Bayesian Information Criterion (BIC) values for mixture models with K=1 and K=2 clusters and the
     * difference (delta) between these values.
     * <p>
     * The BIC value is a statistical measure used in model selection to quantify the goodness of fit of a model while
     * penalizing the complexity of the model. This class stores the individual BIC values for K=1 (bicK1) and K=2
     * (bicK2) clusters, as well as their difference (delta).
     */
    public static final class BicDelta {
        /**
         * Stores the Bayesian Information Criterion (BIC) value for a mixture model with a single cluster (K=1). The
         * BIC value is used to measure the goodness of fit of a statistical model while penalizing its complexity.
         */
        public final double bicK1;
        /**
         * Represents the Bayesian Information Criterion (BIC) value for a mixture model with two clusters (K=2). The
         * BIC value is a statistical measure used to evaluate and compare models by balancing model fit with
         * complexity.
         */
        public final double bicK2;
        /**
         * Represents the difference between the Bayesian Information Criterion (BIC) values for mixture models with K=1
         * and K=2 clusters.
         * <p>
         * This value quantifies how much better (or worse) a two-cluster model (K=2) fits the data compared to a
         * single-cluster model (K=1), while factoring in model complexity. A positive delta indicates that the
         * two-cluster model has a better fit relative to the single-cluster model after accounting for complexity.
         */
        public final double delta;

        /**
         * Constructs a BicDelta instance that represents the Bayesian Information Criterion (BIC) values
         * for mixture models with K=1 and K=2 clusters, along with the difference (delta) between these values.
         *
         * @param bicK1 the BIC value for a mixture model with a single cluster (K=1)
         * @param bicK2 the BIC value for a mixture model with two clusters (K=2)
         * @param delta the difference between the BIC values for K=1 and K=2 clusters
         */
        public BicDelta(double bicK1, double bicK2, double delta) {
            this.bicK1 = bicK1;
            this.bicK2 = bicK2;
            this.delta = delta;
        }
    }

    /**
     * Represents the entropy statistics of clustering results in the context of mixture models. This class provides
     * metrics to evaluate the uncertainty and confidence in clustering assignments based on the responsibility matrix.
     */
    public static final class EntropyStats {
        /**
         * Represents the mean entropy of clustering results, providing a measure of the uncertainty in cluster
         * assignments. The value ranges between 0 and 1: - A value of 0 indicates a "crisp" clustering where each data
         * point is assigned to a single cluster with high certainty. - A value of 1 indicates a high level of
         * uncertainty, with a "uniform" distribution of responsibility values across clusters.
         */
        public final double meanEntropy;   // 0 = crisp, 1 = uniform
        /**
         * Represents the fraction of data points in the clustering result where the maximum responsibility value
         * assigned to a cluster for a data point (r_ik) is greater than or equal to 0.90. This metric provides an
         * indication of the proportion of data points that are assigned to a cluster with high confidence.
         */
        public final double fracConf90;    // fraction with max r_ik >= 0.90
        /**
         * Represents the fraction of data points in the clustering result where the maximum responsibility value
         * assigned to a cluster for a data point (r_ik) is greater than or equal to 0.80. This metric provides an
         * indication of the proportion of data points that are assigned to a cluster with moderate confidence.
         */
        public final double fracConf80;    // fraction with max r_ik >= 0.80

        /**
         * Constructs an instance of EntropyStats with the specified mean entropy and confidence fractions.
         *
         * @param meanEntropy the mean entropy of clustering results, representing the average uncertainty in cluster
         *                    assignments. Values range from 0 (crisp clustering) to 1 (maximum uncertainty).
         * @param fracConf90  the fraction of data points where the maximum responsibility value for a cluster is
         *                    greater than or equal to 0.90, indicating the proportion of highly confident cluster
         *                    assignments.
         * @param fracConf80  the fraction of data points where the maximum responsibility value for a cluster is
         *                    greater than or equal to 0.80, indicating the proportion of moderately confident cluster
         *                    assignments.
         */
        public EntropyStats(double meanEntropy, double fracConf90, double fracConf80) {
            this.meanEntropy = meanEntropy;
            this.fracConf90 = fracConf90;
            this.fracConf80 = fracConf80;
        }
    }

    /**
     * Represents the result of a stability analysis of clustering, typically performed by evaluating the Adjusted Rand
     * Index (ARI) across multiple independent runs.
     * <p>
     * The stability analysis provides:
     * <ul>
     * <li> The mean Adjusted Rand Index (meanARI) across all pairwise comparisons of cluster labelings.
     * <li>< The standard deviation of the Adjusted Rand Index (sdARI) across these comparisons.
     * <li> The total number of pairwise comparisons (numPairs) considered.
     * </ul>
     * <p>
     * This data structure is used to summarize clustering stability, often in the context
     * of algorithms like Expectation-Maximization or similar clustering methods.
     */
    public static final class StabilityResult {
        /**
         * Represents the mean Adjusted Rand Index (ARI) across all pairwise comparisons of cluster labelings in a
         * clustering stability analysis.
         * <p>
         * The ARI is a measure of similarity between two cluster labelings, adjusted for chance. `meanARI` provides an
         * overall metric summarizing the stability of a clustering algorithm by averaging the ARI values across
         * multiple independent runs.
         */
        public final double meanARI;
        /**
         * Represents the standard deviation of the Adjusted Rand Index (ARI) across multiple pairwise comparisons of
         * cluster labelings in a clustering stability analysis.
         * <p>
         * This metric quantifies the variability of ARI values, providing insight into the consistency of clustering
         * results across independent runs. A lower value of `sdARI` indicates more stability, while a higher value
         * suggests greater variability in clustering outcomes.
         */
        public final double sdARI;
        /**
         * Represents the total number of pairwise comparisons considered in the context of a clustering stability
         * analysis. This value is used to quantify the number of evaluations performed when computing metrics such as
         * the Adjusted Rand Index (ARI) across multiple independent runs of a clustering algorithm.
         */
        public final int numPairs;

        /**
         * Constructs an object to represent the result of a stability analysis of clustering.
         *
         * @param meanARI  The mean Adjusted Rand Index (ARI) across all pairwise comparisons of cluster labelings.
         * @param sdARI    The standard deviation of the Adjusted Rand Index (ARI) across pairwise comparisons.
         * @param numPairs The total number of pairwise comparisons considered in the analysis.
         */
        public StabilityResult(double meanARI, double sdARI, int numPairs) {
            this.meanARI = meanARI;
            this.sdARI = sdARI;
            this.numPairs = numPairs;
        }
    }

    /**
     * Represents the difference between two clustering graphs in terms of structural and adjacency metrics. The metrics
     * include adjacency F1 score, arrow F1 score, and Structural Hamming Distance (SHD).
     */
    public static final class GraphDiff {
        /**
         * Represents the adjacency F1 score, which measures the similarity between the adjacency structures of two
         * graphs in terms of precision and recall. The adjacency F1 score is a commonly used metric in graph comparison
         * to evaluate how well nodes and edges align between two graphs.
         */
        public final double adjacencyF1;
        /**
         * Represents the arrow F1 score, which measures the similarity of directed edges (arrows) between two graphs.
         * The metric evaluates precision and recall for correctly inferred arrow directions in a structural comparison
         * of graphs.
         */
        public final double arrowF1;
        /**
         * Represents the Structural Hamming Distance (SHD) between two graphs. SHD quantifies the structural difference
         * between two graphs by counting the number of edits (addition, deletion, or change of edges or directions)
         * required to transform one graph into another.
         */
        public final int shd;

        /**
         * Constructs a {@code GraphDiff} instance representing the difference between two graphs based on adjacency F1
         * score, arrow F1 score, and Structural Hamming Distance (SHD).
         *
         * @param adjacencyF1 The adjacency F1 score, which measures the similarity of the adjacency structures of two
         *                    graphs.
         * @param arrowF1     The arrow F1 score, which evaluates the similarity of the directed edges in two graphs.
         * @param shd         The Structural Hamming Distance (SHD), representing the number of edits required to make
         *                    the two graphs identical.
         */
        public GraphDiff(double adjacencyF1, double arrowF1, int shd) {
            this.adjacencyF1 = adjacencyF1;
            this.arrowF1 = arrowF1;
            this.shd = shd;
        }
    }
}
