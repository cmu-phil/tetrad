package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The EmUnmix class provides utility methods to perform Gaussian Mixture Models (GMM)-based clustering and approximate
 * inference on residuals. The methods within this class support various configurations and allow for flexible modeling
 * via Expectation-Maximization (EM) and Bayesian Information Criterion (BIC)-based model selection. This class is
 * particularly designed for clustering datasets into subpopulations based on residual signatures, and subsequently
 * applying per-cluster search logic.
 * <p>
 * This class includes: - A nested static `Config` class for configuring various model parameters. - Methods to
 * automatically determine the number of clusters (via BIC). - Core functionality for applying EM on residuals and
 * dividing data into sub-clusters.
 * <p>
 * All methods in the EmUnmix class treat the input configuration and data carefully, ensuring appropriate preprocessing
 * of residuals and enabling splitting into clusters with per-cluster analysis.
 * <p>
 * The class is final and cannot be subclassed.
 */
public final class EmUnmix {

    /**
     * Executes the process of unmixing a dataset into clusters, building residual signatures,
     * performing expectation-maximization (EM) on the residuals, and conducting per-cluster
     * graph search. Returns an {@code UnmixResult} containing the outcomes of this process.
     *
     * @param data             the input dataset to be unmixed
     * @param cfg              the configuration settings for the unmixing process
     * @param regressor        the residual regressor to generate residual signatures
     * @param pooledSearch     an optional function for pooled graph search across the dataset,
     *                         can be null if {@code useParentSuperset} is enabled
     * @param perClusterSearch the function to perform graph search on each individual cluster's dataset
     * @return an {@code UnmixResult} containing the cluster assignments, cluster-specific datasets, and graphs
     * @throws NullPointerException if {@code data}, {@code regressor}, or {@code perClusterSearch} is null
     */
    public static UnmixResult run(
            DataSet data,
            Config cfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,   // may be null if useParentSuperset=true
            Function<DataSet, Graph> perClusterSearch
    ) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(regressor);
        Objects.requireNonNull(perClusterSearch);

        // 1) Build residual signatures (same initializer options as your pipeline)
        double[][] R = buildResiduals(data, cfg, regressor, pooledSearch);
        if (cfg.robustScaleResiduals) ResidualUtils.robustStandardizeInPlace(R);

        // 2) EM on residuals
        GaussianMixtureEM.Config emc = new GaussianMixtureEM.Config();
        emc.K = cfg.K;
        emc.covType = cfg.covType;
        emc.maxIters = cfg.emMaxIters;
        emc.tol = cfg.emTol;
        emc.seed = cfg.seed;
        emc.ridge = cfg.ridge;
        emc.kmeansRestarts = cfg.kmeansRestarts;

        GaussianMixtureEM.Model model = GaussianMixtureEM.fit(R, emc);
        int[] z = EmUtils.mapLabels(model.responsibilities);

        // 3) Split & per-cluster search
        List<DataSet> parts = splitByLabels(data, z, cfg.K);
        List<Graph> graphs = searchPerCluster(parts, perClusterSearch);

        return new UnmixResult(z, cfg.K, parts, graphs);
    }

    /**
     * Identifies the optimal number of clusters (K) within a specified range by maximizing
     * the Bayesian Information Criterion (BIC). This method performs an iterative process
     * to evaluate different values of K, and for each value, it builds residuals, fits a
     * Gaussian mixture model, evaluates the BIC, and performs per-cluster graph searches.
     * Returns an {@code UnmixResult} containing the best clustering result and associated graphs.
     *
     * @param data             the input dataset to be analyzed
     * @param Kmin             the minimum number of clusters to consider
     * @param Kmax             the maximum number of clusters to consider
     * @param regressor        the residual regressor for building residual signatures
     * @param pooledSearch     a function to perform an optional pooled graph search across the dataset
     * @param perClusterSearch a function to perform graph search individually for each cluster's dataset
     * @param base             the base configuration used for the clustering and model fitting
     * @return an {@code UnmixResult} containing the optimal cluster assignments, per-cluster datasets, and graphs
     */
    public static UnmixResult selectK(
            DataSet data, int Kmin, int Kmax,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,
            Function<DataSet, Graph> perClusterSearch,
            Config base
    ) {
        double bestBIC = Double.NEGATIVE_INFINITY;
        UnmixResult best = null;
        for (int K = Kmin; K <= Kmax; K++) {
            Config cfg = copy(base);
            cfg.K = K;
            // Build residuals once (so BIC compares apples-to-apples)
            double[][] R = buildResiduals(data, cfg, regressor, pooledSearch);
            if (cfg.robustScaleResiduals) ResidualUtils.robustStandardizeInPlace(R);

            GaussianMixtureEM.Config emc = new GaussianMixtureEM.Config();
            emc.K = K;
            emc.covType = cfg.covType;
            emc.maxIters = cfg.emMaxIters;
            emc.tol = cfg.emTol;
            emc.seed = cfg.seed;
            emc.ridge = cfg.ridge;
            emc.kmeansRestarts = cfg.kmeansRestarts;

            GaussianMixtureEM.Model m = GaussianMixtureEM.fit(R, emc);
            double bic = EmUtils.bic(m, R.length);
            if (bic > bestBIC) {
                bestBIC = bic;
                int[] z = EmUtils.mapLabels(m.responsibilities);
                List<DataSet> parts = splitByLabels(data, z, K);
                List<Graph> graphs = searchPerCluster(parts, perClusterSearch);
                best = new UnmixResult(z, K, parts, graphs);
            }
        }
        return best;
    }

    private static double[][] buildResiduals(
            DataSet data, Config cfg, ResidualRegressor regressor, Function<DataSet, Graph> pooledSearch) {
        if (cfg.useParentSuperset) {
            var pa = ParentSupersetBuilder.build(data, cfg.supersetCfg);
            return ResidualUtils.residualMatrix(data, pa, regressor);
        } else {
            Objects.requireNonNull(pooledSearch, "pooledSearch must be provided when useParentSuperset=false");
            Graph gPool = pooledSearch.apply(data);
            return ResidualUtils.residualMatrix(data, gPool, regressor);
        }
    }

    // ----- helpers (mirror your existing ones) -----

    private static List<Graph> searchPerCluster(List<DataSet> parts, Function<DataSet, Graph> perClusterSearch) {
        List<Graph> graphs = new ArrayList<>(parts.size());
        for (DataSet dk : parts) {
            if (dk == null || dk.getNumRows() == 0) graphs.add(null);
            else graphs.add(perClusterSearch.apply(dk));
        }
        return graphs;
    }

    private static List<DataSet> splitByLabels(DataSet data, int[] z, int K) {
        int n = data.getNumRows();
        List<List<Integer>> idx = new ArrayList<>(K);
        for (int k = 0; k < K; k++) idx.add(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            int lab = z[i];
            if (lab >= 0 && lab < K) idx.get(lab).add(i);
        }
        List<DataSet> out = new ArrayList<>(K);
        for (int k = 0; k < K; k++) {
            List<Integer> rows = idx.get(k);
            if (rows.isEmpty()) {
                DoubleDataBox box = new DoubleDataBox(0, data.getVariables().size());
                out.add(new BoxDataSet(box, data.getVariables()));
            } else {
                int[] rowsArray = rows.stream().mapToInt(Integer::intValue).toArray();
                out.add(data.subsetRows(rowsArray));
            }
        }
        return out;
    }

    private static Config copy(Config c) {
        Config d = new Config();
        d.K = c.K;
        d.useParentSuperset = c.useParentSuperset;
        d.supersetCfg = ParentSupersetBuilderCopy.copy(c.supersetCfg);
        d.robustScaleResiduals = c.robustScaleResiduals;
        d.covType = c.covType;
        d.emMaxIters = c.emMaxIters;
        d.emTol = c.emTol;
        d.seed = c.seed;
        d.ridge = c.ridge;
        d.kmeansRestarts = c.kmeansRestarts;
        d.useMAP = c.useMAP;
        return d;
    }

    /**
     * Configuration class for the EM unmixing process. This class contains a set of
     * parameters used to control the behavior of Gaussian Mixture Models (GMM),
     * clustering, and residual processing during the unmixing process.
     *
     * Fields in this class allow tuning of aspects such as the number of clusters (K),
     * EM algorithm settings, covariance type, regularization, and annealing parameters.
     * It also supports the use of a parent superset for pooled searches, along with
     * robust scaling of residuals.
     */
    public static final class Config {
        public int K;
        public boolean useParentSuperset = false;
        public ParentSupersetBuilder.Config supersetCfg = new ParentSupersetBuilder.Config();
        public boolean robustScaleResiduals = true;

        public GaussianMixtureEM.CovarianceType covType = GaussianMixtureEM.CovarianceType.DIAGONAL;
        public int emMaxIters = 200;
        public double emTol = 1e-5;
        public long seed = 13L;
        public double ridge = 1e-6;
        public int kmeansRestarts = 5;
        public boolean useMAP = true; // hard-assign by MAP; set false if you plan weighted searches
        public double covRidgeRel;
        public double covShrinkage;
        public int annealSteps;
        public double annealStartT;
    }

    /**
     * small internal copier to avoid sharing superset config across trials
     */
    private static final class ParentSupersetBuilderCopy {
        static ParentSupersetBuilder.Config copy(ParentSupersetBuilder.Config c) {
            ParentSupersetBuilder.Config d = new ParentSupersetBuilder.Config();
            d.topM = c.topM;
            d.scoreType = c.scoreType;
            d.useBagging = c.useBagging;
            d.bags = c.bags;
            d.bagFraction = c.bagFraction;
            d.seed = c.seed;
            d.shallowSearch = c.shallowSearch;
            return d;
        }
    }
}