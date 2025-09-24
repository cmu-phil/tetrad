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
 * The EmUnmix class provides functionality for applying the Expectation-Maximization (EM)
 * algorithm on residual signatures derived from a dataset to fit Gaussian mixtures.
 * It supports both single model fitting and model selection for optimal cluster count (K)
 * using criteria such as Bayesian Information Criterion (BIC). Additionally, the class
 * allows optional graph-based operations for pooled and per-cluster analysis.
 *
 * This class is designed to work with complex datasets and employs residual regression
 * for building input data before fitting Gaussian mixtures. It also supports various
 * configurations for the EM algorithm and data preprocessing.
 */
public final class EmUnmix {

    /**
     * Default constructor for EmUnmix.
     */
    public EmUnmix() {}

    /**
     * Runs the unmixing process on the provided dataset using the specified configuration and regressor.
     *
     * @param data The dataset on which the unmixing process is performed. Contains data points to be clustered.
     * @param cfg The configuration object providing parameters and settings for the unmixing algorithm.
     * @param regressor The residual regressor used for determining cluster assignments and handling dependencies in data.
     * @return An instance of UnmixResult containing the results of the unmixing process,
     *         including cluster labels, per-cluster datasets, and optional cluster graphs.
     */
    public static UnmixResult run(DataSet data, Config cfg, ResidualRegressor regressor) {
        return run(data, cfg, regressor, /*pooledSearch*/ null, /*perClusterSearch*/ null);
    }

    /**
     * Selects the optimal number of clusters (K) for unmixing the provided dataset within the specified range.
     * The method uses the specified residual regressor and configuration settings to determine the best cluster count.
     *
     * @param data The dataset to analyze for optimal clustering. Contains data points to be partitioned.
     * @param Kmin The minimum number of clusters to evaluate.
     * @param Kmax The maximum number of clusters to evaluate.
     * @param regressor The residual regressor used to fit the data and evaluate clustering performance.
     * @param base The base configuration used for running the clustering algorithm during evaluation.
     * @return An UnmixResult object containing the best clustering results, cluster labels, per-cluster datasets,
     *         and additional optional cluster information.
     */
    public static UnmixResult selectK(
            DataSet data, int Kmin, int Kmax,
            ResidualRegressor regressor,
            Config base
    ) {
        return selectK(data, Kmin, Kmax, regressor, /*pooledSearch*/ null, /*perClusterSearch*/ null, base);
    }

    /**
     * Executes the unmixing process on the given dataset using the specified configuration, residual regressor,
     * and optional graph search functions. The method applies residual signature extraction, EM clustering,
     * and optionally searches for cluster-specific graphical representations.
     *
     * @param data The dataset to be processed, containing data points to be partitioned into clusters.
     * @param cfg The configuration object that provides parameters and settings for the unmixing algorithm.
     * @param regressor The residual regressor used for generating residual signatures and handling dependencies in the data.
     * @param pooledSearch A function that builds a pooled graphical representation of the dataset, may be null if not needed.
     * @param perClusterSearch A function that builds graphical representations for individual clusters, may be null if not applicable.
     * @return An instance of UnmixResult containing the clustering results, including cluster labels,
     *         per-cluster datasets, and optionally cluster-specific graphs.
     */
    public static UnmixResult run(
            DataSet data,
            Config cfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,   // may be null
            Function<DataSet, Graph> perClusterSearch // may be null
    ) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(regressor);

        // 1) Residual signatures
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
        emc.covShrinkage = cfg.covShrinkage;
        emc.covRidgeRel = cfg.covRidgeRel;
        emc.annealSteps = cfg.annealSteps;
        emc.annealStartT = cfg.annealStartT;

        GaussianMixtureEM.Model model = GaussianMixtureEM.fit(R, emc);
        int[] z = EmUtils.mapLabels(model.responsibilities);

        List<DataSet> parts = splitByLabels(data, z, cfg.K);

        // 3) Optional per-cluster graphs
        if (perClusterSearch == null) {
            return new UnmixResult(z, cfg.K, parts, model);
        } else {
            List<Graph> graphs = searchPerCluster(parts, perClusterSearch);
            return new UnmixResult(z, cfg.K, parts, graphs, model);
        }
    }

    /**
     * Selects the optimal number of clusters (K) for unmixing a dataset within the specified range [Kmin, Kmax].
     * The method evaluates clustering performance using a residual regressor and determines the best clustering
     * configuration based on the Bayesian Information Criterion (BIC). The solution may optionally incorporate
     * graphical searches for pooled or per-cluster representations.
     *
     * @param data The dataset to be analyzed for clustering. Contains data points to be partitioned.
     * @param Kmin The minimum number of clusters to evaluate. Must be at least 1.
     * @param Kmax The maximum number of clusters to evaluate. Cannot exceed the number of rows in the dataset.
     * @param regressor The residual regressor used for fitting the data and evaluating clustering performance.
     * @param pooledSearch A function for building a pooled graphical representation of the dataset. Can be null if not needed.
     * @param perClusterSearch A function for building graphical representations for individual clusters. Can be null if not applicable.
     * @param base The base configuration object containing parameters for clustering and residual calculation.
     * @return An UnmixResult object containing the clustering results, including the best number of clusters (K),
     *         cluster labels, per-cluster datasets, and optionally cluster-specific graphs.
     * @throws IllegalArgumentException If Kmin is less than 1 or if Kmax exceeds the number of rows in the dataset.
     */
    public static UnmixResult selectK(
            DataSet data, int Kmin, int Kmax,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,
            Function<DataSet, Graph> perClusterSearch,
            Config base
    ) {
        double bestBIC = Double.POSITIVE_INFINITY;
        UnmixResult best = null;

        for (int K = Kmin; K <= Kmax; K++) {
            if (K < 1) throw new IllegalArgumentException("K must be >= 1");
            if (K > data.getNumRows()) throw new IllegalArgumentException("K cannot exceed number of rows");

            Config cfg = copy(base);
            cfg.K = K;

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
            emc.covShrinkage = cfg.covShrinkage;
            emc.covRidgeRel = cfg.covRidgeRel;
            emc.annealSteps = cfg.annealSteps;
            emc.annealStartT = cfg.annealStartT;

            GaussianMixtureEM.Model m = GaussianMixtureEM.fit(R, emc);
            double bic = m.bic(R.length);

            if (bic < bestBIC) {
                bestBIC = bic;
                int[] z = EmUtils.mapLabels(m.responsibilities);
                List<DataSet> parts = splitByLabels(data, z, K);

                if (perClusterSearch == null) {
                    best = new UnmixResult(z, K, parts, m);
                } else {
                    best = new UnmixResult(z, K, parts, searchPerCluster(parts, perClusterSearch), m);
                }
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
            // If graphs are removed, pooledSearch is likely nullâforce parent superset mode instead.
            if (pooledSearch == null) {
                // Fallback: use parent-superset automatically
                var pa = ParentSupersetBuilder.build(data, cfg.supersetCfg);
                return ResidualUtils.residualMatrix(data, pa, regressor);
            } else {
                Graph gPool = pooledSearch.apply(data);
                return ResidualUtils.residualMatrix(data, gPool, regressor);
            }
        }
    }

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
        d.covRidgeRel = c.covRidgeRel;
        d.covShrinkage = c.covShrinkage;
        d.annealSteps = c.annealSteps;
        d.annealStartT = c.annealStartT;
        d.randomSeed = c.randomSeed;
        return d;
    }

    /**
     * Configuration class for the EmUnmix algorithm, providing parameters and settings to control
     * the behavior of the unmixing process. This class encapsulates various options for clustering,
     * initialization, and residual scaling.
     *
     * The properties allow users to configure aspects such as the number of clusters,
     * EM algorithm behavior, covariance type, regularization, randomization, annealing,
     * and other advanced settings.
     */
    public static final class Config {

        /**
         * Default constructor for Config.
         */
        public Config() {}

        /**
         * The number of clusters to be used in the EmUnmix algorithm. This defines the number
         * of distinct components in the Gaussian Mixture Model, influencing the clustering
         * and unmixing process. A higher value of K allows for more clusters, which may better
         * capture the data structure but could also lead to overfitting.
         */
        public int K;
        /**
         * Indicates whether to use a parent superset for subset initialization in the EmUnmix algorithm.
         * This flag controls whether the algorithm should leverage a pre-defined superset configuration
         * as a starting point for cluster construction. When set to true, it enables the algorithm
         * to utilize a higher-level grouping structure to aid in the initialization process, potentially
         * improving clustering results in scenarios with hierarchical data organization.
         */
        public boolean useParentSuperset = true;
        /**
         * Configuration object for the parent superset used in the EmUnmix algorithm. This variable encapsulates
         * settings specific to the initialization and construction of cluster supersets. It provides configurable
         * parameters for guiding the unmixing process by utilizing a parent superset structure.
         */
        public ParentSupersetBuilder.Config supersetCfg = new ParentSupersetBuilder.Config();
        /**
         * Determines whether robust scaling should be applied to the residuals during the EmUnmix algorithm process.
         * When set to true, the algorithm applies robust techniques to normalize the residuals, reducing the influence
         * of outliers and improving the stability and reliability of the unmixing process.
         */
        public boolean robustScaleResiduals = true;
        /**
         * Specifies the covariance type to be used in the EmUnmix algorithm's Gaussian Mixture Model.
         * This variable determines how the covariance matrices are modeled for the different Gaussian components
         * in the mixture. The choice of covariance type can influence the flexibility and complexity of
         * the model when fitting the data.
         *
         * <p>Possible values include:</p>
         * <ul>
         *   <li><b>FULL:</b> Allows for a full covariance matrix, capturing correlations between all variables.</li>
         *   <li><b>DIAGONAL:</b> Restricts the covariance matrix to be diagonal, assuming no correlations between variables.</li>
         *   <li><b>SPHERICAL:</b> Assumes equal variance (spherical covariance) for all dimensions.</li>
         * </ul>
         */
        public GaussianMixtureEM.CovarianceType covType = GaussianMixtureEM.CovarianceType.DIAGONAL;
        /**
         * Maximum number of iterations allowed for the Expectation-Maximization (EM) algorithm
         * during the computation process. This value places an upper limit on the iterations
         * to ensure termination and prevent excessive computation time.
         */
        public int emMaxIters = 200;
        /**
         * Convergence tolerance for the Expectation-Maximization (EM) algorithm.
         * This parameter specifies the threshold for detecting convergence
         * in the iterative optimization process of the EM algorithm.
         * Smaller values indicate stricter convergence criteria, while larger
         * values may result in faster but less precise convergence.
         */
        public double emTol = 1e-5;
        /**
         * Represents the seed value used for initializing random number generation
         * or other operations that require a deterministic starting point.
         *
         * This value ensures reproducibility of results when running stochastic
         * processes or algorithms that involve randomness.
         */
        public long seed = 13L;
        /**
         * Represents the ridge parameter for regularization in the covariance matrix estimation.
         * A positive value adds a penalty term to the covariance matrix to prevent overfitting.
         */
        public double ridge = 1e-6;
        /**
         * Represents the number of restarts for the K-means clustering algorithm.
         * Increasing this value can improve the quality of the clustering but may
         * also increase computation time.
         */
        public int kmeansRestarts = 5;
        /**
         * Indicates whether to use Maximum A Posteriori (MAP) estimation for parameter initialization.
         * MAP estimation can provide more stable and accurate parameter estimates.
         */
        public boolean useMAP = true;
        /**
         * Represents the annealing steps for the simulated annealing algorithm.
         * Increasing this value can improve the quality of the solution but may
         * also increase computation time.
         */
        public double covRidgeRel = 0.0;
        /**
         * Represents the starting temperature for the simulated annealing algorithm.
         * A higher value can lead to more exploration of the solution space.
         */
        public double covShrinkage = 0.0;
        /**
         * Represents the random seed for initializing the random number generator.
         * This ensures reproducibility of results when running stochastic algorithms.
         */
        public int annealSteps = 0;
        /**
         * Represents the starting temperature for the simulated annealing algorithm.
         * A higher value can lead to more exploration of the solution space.
         */
        public double annealStartT = 1.0;
        /**
         * Represents the seed value used to initialize a random number generator.
         * This value ensures that results are reproducible by producing a
         * consistent sequence of random numbers when the same seed is used.
         */
        public long randomSeed = 35L;

        /**
         * Creates a copy of this configuration object.
         * @return a new Config object with the same settings as this one.
         */
        public Config copy() {
            return EmUnmix.copy(this);
        }
    }

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
