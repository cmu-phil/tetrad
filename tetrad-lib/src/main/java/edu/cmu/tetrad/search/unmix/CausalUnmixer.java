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
import edu.cmu.tetrad.search.Pc;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

/**
 * The CausalUnmixer class provides functionality for extracting unmixed results from datasets using a graph-less
 * approach. It utilizes clustering and statistical techniques to process input data and produce clustering information,
 * including labels, the number of clusters, and cluster-specific data.
 * <p>
 * The class includes configuration options, allowing customization of clustering parameters, covariance policies,
 * residual options, stability settings, and other features. The results can be tailored or derived based on specific
 * dataset properties and user-defined configurations.
 * <p>
 * This is accomplished through the Expectation-Maximization algorithm and optional model selection processes, with
 * support for Gaussian Mixture Models (full or diagonal covariance types).
 */
public class CausalUnmixer {

    /**
     * Default constructor for the CausalUnmixer class. Initializes the object with default configuration settings.
     */
    public CausalUnmixer() {}

    /**
     * Retrieves the unmixing result by processing the provided dataset using default configuration settings.
     *
     * @param data The input dataset to be unmixed. Represents the collection of data points to be processed for
     *             clustering.
     * @return An {@code UnmixResult} object containing the results of the unmixing process, including cluster
     * assignments, cluster data subsets, and the corresponding Gaussian Mixture Model (GMM).
     */
    public static @NotNull UnmixResult getUnmixedResult(DataSet data) {
        return getUnmixedResult(data, defaults());
    }

    /**
     * Retrieves the unmixing results from the provided dataset and configuration. This method performs data clustering
     * using the Expectation-Maximization (EM) algorithm and optionally selects the optimal number of clusters (K) if
     * not specified in the given configuration.
     *
     * @param data The input dataset to be unmixed. Cannot be null.
     * @param cfg  The configuration settings used for the unmixing process, including parameters such as ridge
     *             regularization and the number of clusters. Cannot be null.
     * @return An {@code UnmixResult} object containing the results of the unmixing process, including cluster
     * assignments, cluster data subsets, and the Gaussian Mixture Model (GMM).
     */
    public static @NotNull UnmixResult getUnmixedResult(DataSet data, @NotNull Config cfg) {
        Objects.requireNonNull(data, "data");

        EmUnmix.Config ec = toEmConfig(cfg, data);
        LinearQRRegressor reg = new LinearQRRegressor().setRidgeLambda(cfg.ridgeLambda);

        if (cfg.K != null) {
            return EmUnmix.run(data, ec, reg);                    // no graphs
        } else {
            return EmUnmix.selectK(data, cfg.Kmin, cfg.Kmax, reg, ec); // no graphs
        }
    }

    /**
     * Converts the provided configuration and dataset into an {@code EmUnmix.Config} object.
     *
     * @param cfg  The configuration settings for the EM algorithm, including parameters like the number of clusters,
     *             covariance policy, and stability options. Cannot be null.
     * @param data The dataset to be processed for clustering, which provides information such as the number of rows and
     *             columns. Cannot be null.
     * @return An {@code EmUnmix.Config} instance populated with the specified configuration and derived settings based
     * on the input dataset.
     */
    private static EmUnmix.Config toEmConfig(Config cfg, DataSet data) {
        EmUnmix.Config ec = new EmUnmix.Config();
        ec.K = (cfg.K != null ? cfg.K : Math.max(cfg.Kmin, 1));

        // Covariance policy
        int n = data.getNumRows();
        int p = data.getNumColumns();
        int K = (cfg.K != null ? cfg.K : Math.max(cfg.Kmin, 2));
        boolean okFull = (n / Math.max(1, K)) >= (p + cfg.fullSigmaSafetyMargin);
        ec.covType = okFull ? GaussianMixtureEM.CovarianceType.FULL : GaussianMixtureEM.CovarianceType.DIAGONAL;

        // Residual options
        ec.useParentSuperset = cfg.useParentSuperset;
        ec.supersetCfg.topM = cfg.supersetTopM;
        ec.supersetCfg.scoreType = cfg.supersetScore;
        ec.robustScaleResiduals = cfg.robustScaleResiduals;

        // EM stability
        ec.kmeansRestarts = cfg.kmeansRestarts;
        ec.emMaxIters = cfg.emMaxIters;
        ec.ridge = cfg.covRidgeRel;          // absolute ridge (kept for compat)
        ec.covRidgeRel = cfg.covRidgeRel;    // relative ridge
        ec.covShrinkage = cfg.covShrinkage;
        ec.annealSteps = cfg.annealSteps;
        ec.annealStartT = cfg.annealStartT;

        return ec;
    }

    /**
     * Creates and returns a default {@code Config} object with predefined settings. These default configurations
     * include parameters such as the number of clusters, regularization values, optimization settings, and other
     * clustering-related options.
     *
     * @return A non-null {@code Config} object containing the default configuration settings for the unmixing or
     * clustering process.
     */
    public static @NotNull Config defaults() { /* â¦ your existing defaults â¦ */
        return new Config();
    }

    /**
     * The {@code Config} class encapsulates the configuration settings used for the unmixing or clustering process
     * within the {@code CausalUnmixer} framework. It provides parameters to control the behavior of the
     * Expectation-Maximization (EM) algorithm, Gaussian Mixture Models (GMM), and additional related processes.
     * <p>
     * This class includes various tunable parameters, such as the number of clusters (K), parent superset
     * configurations, regularization factors, covariance handling, annealing steps, and graph-related settings.
     */
    public static class Config {

        /**
         * Specifies the number of clusters (K) used in the clustering process within the {@code CausalUnmixer}
         * framework. This variable represents the target number of clusters that will be formed during the modeling
         * process. It is a critical parameter for Gaussian Mixture Models (GMM) or other clustering algorithms applied.
         * The default value is set to 2.
         */
        public Integer K = 2;
        /**
         * Specifies the minimum number of clusters (Kmin) that can be used in the clustering process within the
         * {@code CausalUnmixer} framework. This variable defines a lower bound for cluster configurations during the
         * modeling process, ensuring that at least this number of clusters is considered when applying algorithms such
         * as Gaussian Mixture Models (GMM) or k-means.
         * <p>
         * The default value is set to 1, indicating that the system will always consider at least one cluster.
         */
        public int Kmin = 1, Kmax = 4;
        /**
         * Indicates whether to use the parent superset configuration during the clustering or unmixing process within
         * the {@code CausalUnmixer} framework. When set to {@code true}, the framework incorporates parent supersets
         * into the modeling process, potentially influencing cluster assignments, causal decoding, or related
         * processes. This setting impacts the generation or utilization of grouped parent structures in the modeling
         * pipeline.
         * <p>
         * The default value is {@code true}.
         */
        public boolean useParentSuperset = true;
        /**
         * The maximum size of the superset to be considered during processing or configuration. Represents a threshold
         * or limit to control the scope of operations in the superset.
         */
        public int supersetTopM = 12;
        /**
         * Specifies the scoring type to be used when working with the parent superset configuration. This variable
         * determines the method of evaluation or comparison within the parent superset context. The value is
         * initialized to {@code ParentSupersetBuilder.ScoreType.KENDALL}.
         */
        public ParentSupersetBuilder.ScoreType supersetScore = ParentSupersetBuilder.ScoreType.KENDALL;
        /**
         * Determines whether to use robust scaling for residuals in computations. When set to true, scaling methods
         * that are less sensitive to outliers are applied, improving stability and accuracy in the presence of
         * anomalous data.
         */
        public boolean robustScaleResiduals = true;
        /**
         * The number of restarts to be performed by the k-means clustering algorithm. A higher value increases the
         * chances of finding a better clustering solution by running the algorithm multiple times with different
         * initializations.
         */
        public int kmeansRestarts = 20;
        /**
         * Maximum number of iterations allowed for the Expectation-Maximization (EM) algorithm. This value determines
         * the upper limit of iterations the EM algorithm can perform in its optimization process.
         */
        public int emMaxIters = 300;
        /**
         * Regularization parameter for covariance estimation. Controls the amount of shrinkage applied to the
         * covariance matrix during estimation.
         */
        public double covRidgeRel = 1e-3;
        /**
         * Regularization parameter for covariance estimation. Controls the amount of shrinkage applied to the
         * covariance matrix during estimation.
         */
        public double covShrinkage = 0.10;
        /**
         * Number of steps in the simulated annealing process. Determines the number of iterations in the simulated
         * annealing algorithm.
         */
        public int annealSteps = 15;
        /**
         * Starting temperature for the simulated annealing process. Controls the initial temperature level for the
         * simulated annealing algorithm.
         */
        public double annealStartT = 0.8;
        /**
         * Safety margin for full sigma estimation. Ensures that the estimated covariance matrix is positive definite.
         */
        public int fullSigmaSafetyMargin = 10;
        /**
         * A regularization parameter used in ridge regression to prevent overfitting by adding a penalty proportional
         * to the square of the coefficients' magnitude. Typically, higher values increase regularization, while lower
         * values reduce it. This parameter helps stabilize solutions when dealing with multicollinearity or poorly
         * conditioned problems.
         */
        public double ridgeLambda = 1e-3;
        /**
         * A function that generates a pooled graph model based on a given configuration and dataset. This variable
         * represents a high-level mapping from a configuration object to a secondary function, which further maps a
         * dataset to a graph representation.
         * <p>
         * The function chain allows flexible composition of graph creation pipelines tailored to different
         * configurations and input datasets. It potentially leverages model pooling or other aggregation mechanisms.
         * <p>
         * The purpose of this variable is to encapsulate the logic needed to derive a pooled graph representation,
         * enabling modularity and reusability.
         * <p>
         * Expected to be used in scenarios where graph structure modeling from datasets is required under varying
         * parameterized configurations.
         * <p>
         * The resulting graph may involve structures influenced by clustering effects, covariance matrix adjustments,
         * or statistical aggregation across dataset features, based on the particular configuration provided.
         */
        public java.util.function.Function<Config, Function<DataSet, Graph>> pooledGraphFn;
        /**
         * A function that generates a cluster-specific graph creation method.
         * <p>
         * This variable is a higher-order function that takes a {@code Config} object as input and produces a function.
         * The resulting function, in turn, takes a {@code DataSet} as input and outputs a {@code Graph} object. The
         * purpose of {@code perClusterGraphFn} is to allow for the creation of graphs tailored to the specific
         * characteristics of different clusters, enabling more customized and effective processing or representation of
         * data.
         * <p>
         * The exact behavior of the function is determined by the configuration provided in the {@code Config} object.
         * This allows for flexibility in defining how cluster-specific graphs should be generated based on varying use
         * cases or requirements.
         */
        public java.util.function.Function<Config, Function<DataSet, Graph>> perClusterGraphFn;
        /**
         * Represents the alpha parameter for controlling the significance level in hypothesis testing or statistical
         * calculations.
         * <p>
         * Typically used to determine the threshold below which a null hypothesis can be rejected. A smaller value
         * indicates a more stringent significance level.
         */
        public double pcAlpha = 0.01;
        /**
         * Defines the orientation style for the collider in the configuration. Specifically, this variable determines
         * how the collider's orientation is calculated or interpreted. The value is set to
         * Pc.ColliderOrientationStyle.MAX_P by default, indicating the use of the MAX_P style.
         */
        public Pc.ColliderOrientationStyle pcColliderStyle = Pc.ColliderOrientationStyle.MAX_P;

        /**
         * Default constructor for the Config class. Initializes an instance of the Config class with default values set
         * for its fields. This constructor does not take any parameters and is primarily used for creating a Config
         * object with default settings.
         */
        public Config() {
        }
    }
}
