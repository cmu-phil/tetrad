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

package edu.cmu.tetrad.data;

import java.io.Serializable;

/**
 * Minimal, UI-friendly spec for the unmixer. No search/PC/BOSS classes referenced here; we keep it decoupled and
 * translate elsewhere.
 *
 * @author josephramsey
 */
public final class UnmixSpec implements Serializable {
    /**
     * A flag that determines whether the value of 'k' in an operation or calculation should be automatically selected
     * by the system. When set to true, the system determines the appropriate value of 'k' without user intervention. If
     * false, the value of 'k' must be provided explicitly by the user or another part of the program.
     */
    private boolean autoSelectK = true;
    /**
     * Represents an integer constant used within the class. The variable is initialized with a default value of 2. This
     * value may signify a specific configuration or a predefined parameter relevant to the class operations.
     */
    private int K = 2;
    /**
     * Represents the minimum value of K being used in the context of the program. This variable is initialized to 1 and
     * is typically utilized to define a lower boundary or threshold for calculations or iterations involving K.
     */
    private int Kmin = 1;
    /**
     * The maximum allowable value for a certain parameter or configuration in the application. This variable is used to
     * define a constant limit for operations or calculations. It is initialized to a default value of 4.
     */
    private int Kmax = 4;
    /**
     * A private instance variable representing the graph learning strategy. `graphLearner` is set to the
     * `GraphLearner.BOSS` as the default learning method. The value determines the algorithm or approach used in the
     * learning process within the graph-based operations.
     */
    private GraphLearner graphLearner = GraphLearner.BOSS;
    /**
     * Represents the alpha value for a specific probability or calculation context. This variable is often used to
     * adjust sensitivity or weighting in algorithms. In this case, it is initialized with a default value of 0.01.
     */
    private double pcAlpha = 0.01;
    /**
     * Represents the collision style for player-controlled objects in the application. The variable is initialized to
     * the maximum precision collision style, defined as ColliderStyle.MAX_P. Determines how collisions are handled or
     * processed for player-controlled entities. Can be modified to adjust the collision handling strategy during
     * runtime.
     */
    private ColliderStyle pcColliderStyle = ColliderStyle.MAX_P;
    /**
     * Represents the discount rate applied as a penalty imposed by the "boss". This value is used to calculate or
     * adjust certain conditions or fees associated with tasks or operations where the penalty is relevant. The default
     * value is 2.0.
     */
    private double bossPenaltyDiscount = 2.0;
    /**
     * A flag indicating whether to use the parent superset in some operable context. When set to {@code true}, the
     * logic will incorporate or rely on the parent superset instead of solely focusing on the current or individual
     * scope. Otherwise, it will bypass or exclude the parent superset in the operation.
     */
    private boolean useParentSuperset = true;
    /**
     * The maximum number of elements to be included in the superset. This defines the top limit for the collection or
     * processing within a superset-related operation.
     */
    private int supersetTopM = 12;
    /**
     * Represents the score type used for evaluating the similarity or ranking between elements in a superset. Defaults
     * to {@code SupersetScore.KENDALL}. The value of this variable determines the method of computation for scoring or
     * comparison within superset-related contexts.
     */
    private SupersetScore supersetScore = SupersetScore.KENDALL;
    /**
     * A flag that determines whether residuals should be scaled robustly. When set to true, residual scaling is
     * performed in a manner that is less sensitive to outliers, improving the robustness of the scaling process. If set
     * to false, residuals are not scaled robustly.
     */
    private boolean robustScaleResiduals = true;
    /**
     * The covariance mode setting used to determine how covariance is calculated in specific operations or algorithms.
     * This variable is an instance of the {@link CovarianceMode} enumeration and dictates the strategy for covariance
     * estimation.
     * <p>
     * The available modes in {@link CovarianceMode} may include: - AUTO: Automatically selects the appropriate
     * covariance handling method based on context. - Other specific modes depending on the {@link CovarianceMode}
     * implementation.
     * <p>
     * This variable plays an essential role in configuring processes or computations that rely on covariance-related
     * functionality or optimization.
     */
    private CovarianceMode covarianceMode = CovarianceMode.AUTO;
    /**
     * Used only when AUTO: require n/K >= p + safetyMargin to use FULL
     */
    private int fullSigmaSafetyMargin = 10;
    /**
     * Represents the number of times the k-means clustering algorithm will be restarted with different initial
     * centroids to improve the chances of finding a global optimum.
     * <p>
     * This value determines the number of random initializations performed during the k-means clustering process. A
     * higher value increases the likelihood of achieving better clustering results at the expense of additional
     * computation time.
     */
    private int kmeansRestarts = 20;
    /**
     * Defines the maximum number of iterations allowed for the Expectation-Maximization (EM) algorithm to converge.
     * This value is used to limit the computational effort when training or optimizing models using the EM approach.
     * <p>
     * The default value is set to 300 iterations.
     */
    private int emMaxIters = 300;
    /**
     * Represents the ridge regularization parameter used in machine learning algorithms such as Ridge Regression. This
     * parameter helps prevent overfitting by penalizing large coefficients. A smaller value indicates less
     * regularization, whereas a larger value increases the regularization strength.
     */
    private double ridge = 1e-3;
    /**
     * Represents the shrinkage regularization parameter used in machine learning algorithms such as Elastic Net. This
     * parameter helps prevent overfitting by penalizing large coefficients. A smaller value indicates less
     * regularization, whereas a larger value increases the regularization strength.
     */
    private double shrinkage = 0.10;  // optional; use if you wire it
    /**
     * Represents the number of steps for annealing in a process. This parameter controls the number of iterations for
     * annealing. The default value is set to 15 steps.
     */
    private int annealSteps = 15;     // optional
    /**
     * Represents the starting temperature for annealing in a process. This parameter controls the initial temperature
     * for annealing. The default value is set to 0.8.
     */
    private double annealStartT = 0.8;// optional
    /**
     * Represents the random seed for generating random numbers. This parameter controls the initial seed for random
     * number generation. The default value is set to 13.
     */
    private long randomSeed = 13L;
    /**
     * Determines if diagnostic information should be saved during the process.
     */
    private boolean saveDiagnostics = false;
    /**
     * Determines if intermediate logging is enabled.
     */
    private boolean logIntermediate = false;

    /**
     * Constructor for the UnmixSpec class. This constructor initializes an instance of the UnmixSpec class.
     */
    public UnmixSpec() {

    }

    /**
     * Determines if intermediate logging is enabled.
     *
     * @return true if intermediate logging is enabled, false otherwise
     */
    public boolean isLogIntermediate() {
        return logIntermediate;
    }

    /**
     * Sets the logging state for intermediate steps in a process.
     *
     * @param logIntermediate a boolean indicating whether intermediate steps should be logged (true) or not (false)
     */
    public void setLogIntermediate(boolean logIntermediate) {
        this.logIntermediate = logIntermediate;
    }

    /**
     * Retrieves the current value of K.
     *
     * @return the value of K.
     */
    public boolean isAutoSelectK() {
        return autoSelectK;
    }

    /**
     * Sets whether to enable or disable the automatic selection of the best value of K.
     *
     * @param v a boolean value indicating whether to enable (true) or disable (false) the automatic selection of K.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setAutoSelectK(boolean v) {
        this.autoSelectK = v;
        return this;
    }

    /**
     * Retrieves the current value of K.
     *
     * @return the value of K.
     */
    public int getK() {
        return K;
    }

    /**
     * Sets the value of K for the current instance of UnmixSpec. K must be greater than or equal to 1.
     *
     * @param k the value to set for K; must be a positive integer >= 1.
     * @return the current instance of UnmixSpec for method chaining.
     * @throws IllegalArgumentException if the provided k is less than 1.
     */
    public UnmixSpec setK(int k) {
        if (k < 1) throw new IllegalArgumentException("K must be >= 1");
        this.K = k;
        return this;
    }

    // ===== Getters / Setters =====

    /**
     * Retrieves the minimum allowed value of K.
     *
     * @return the minimum value of K (Kmin).
     */
    public int getKmin() {
        return Kmin;
    }

    /**
     * Sets the minimum allowed value of K. Ensures that the value of Kmin is at least 1.
     *
     * @param kmin the proposed minimum value for K; must be an integer.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setKmin(int kmin) {
        this.Kmin = Math.max(1, kmin);
        return this;
    }

    /**
     * Retrieves the maximum allowed value of K (Kmax).
     *
     * @return the maximum value of K (Kmax).
     */
    public int getKmax() {
        return Kmax;
    }

    /**
     * Sets the maximum allowed value of K (Kmax) for the current instance of UnmixSpec. Ensures that the value of Kmax
     * is at least 1.
     *
     * @param kmax the proposed maximum value for K; must be an integer.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setKmax(int kmax) {
        this.Kmax = Math.max(1, kmax);
        return this;
    }

    /**
     * Retrieves the graph learning method currently set for this instance.
     *
     * @return the currently configured GraphLearner, which determines the method used for graph learning (e.g., BOSS or
     * PC_MAX).
     */
    public GraphLearner getGraphLearner() {
        return graphLearner;
    }

    /**
     * Sets the graph learning method for this instance of UnmixSpec. The provided GraphLearner determines the algorithm
     * to be used for graph learning (e.g., BOSS or PC_MAX).
     *
     * @param g the GraphLearner to set; must be a valid enumeration value.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setGraphLearner(GraphLearner g) {
        this.graphLearner = g;
        return this;
    }

    /**
     * Retrieves the alpha value used in the PC algorithm for statistical testing.
     *
     * @return the alpha value for the PC algorithm, which is typically used as a threshold for significance in tests.
     */
    public double getPcAlpha() {
        return pcAlpha;
    }

    /**
     * Sets the alpha value for the PC algorithm. This value is typically used as a significance threshold in
     * statistical testing within the PC algorithm.
     *
     * @param a the alpha value to set for the PC algorithm; must be a non-negative double representing the significance
     *          level.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setPcAlpha(double a) {
        this.pcAlpha = a;
        return this;
    }

    /**
     * Retrieves the collider style used in the PC algorithm for handling colliders during causal discovery. The
     * collider style determines the specific method or approach used to identify colliders in the causal graph.
     *
     * @return the configured {@link ColliderStyle} for the PC algorithm, which may be one of SEPSETS, CONSERVATIVE, or
     * MAX_P.
     */
    public ColliderStyle getPcColliderStyle() {
        return pcColliderStyle;
    }

    /**
     * Sets the collider style to be used in the PC algorithm for identifying colliders during causal graph discovery.
     * The collider style determines the approach applied for handling colliders (e.g., SEPSETS, CONSERVATIVE, or
     * MAX_P).
     *
     * @param s the {@link ColliderStyle} to set; must be a valid enumeration value.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setPcColliderStyle(ColliderStyle s) {
        this.pcColliderStyle = s;
        return this;
    }

    /**
     * Retrieves the BOSS penalty discount value. The BOSS penalty discount is used as a parameter in graph learning
     * algorithms to adjust or scale penalties during the learning process.
     *
     * @return the current BOSS penalty discount value as a double.
     */
    public double getBossPenaltyDiscount() {
        return bossPenaltyDiscount;
    }

    /**
     * Sets the BOSS penalty discount value for this instance of UnmixSpec. The BOSS penalty discount is a parameter
     * used in graph learning algorithms to adjust or scale penalties during the learning process.
     *
     * @param d the BOSS penalty discount value to set; must be a double.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setBossPenaltyDiscount(double d) {
        this.bossPenaltyDiscount = d;
        return this;
    }

    /**
     * Indicates whether the parent superset feature is enabled or disabled.
     *
     * @return {@code true} if the parent superset feature is enabled; {@code false} otherwise.
     */
    public boolean isUseParentSuperset() {
        return useParentSuperset;
    }

    /**
     * Sets the parent superset feature to enabled or disabled.
     *
     * @param v {@code true} to enable the parent superset feature; {@code false} to disable.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setUseParentSuperset(boolean v) {
        this.useParentSuperset = v;
        return this;
    }

    /**
     * Retrieves the top M value for the parent superset feature.
     *
     * @return the top M value for the parent superset feature.
     */
    public int getSupersetTopM() {
        return supersetTopM;
    }

    /**
     * Sets the top M value for the parent superset feature.
     *
     * @param m the top M value to set; must be greater than or equal to 1.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setSupersetTopM(int m) {
        this.supersetTopM = Math.max(1, m);
        return this;
    }

    /**
     * Retrieves the score used for parent superset feature.
     *
     * @return the score used for parent superset feature.
     */
    public SupersetScore getSupersetScore() {
        return supersetScore;
    }

    /**
     * Sets the score used for parent superset feature.
     *
     * @param s the score to set; must not be null.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setSupersetScore(SupersetScore s) {
        this.supersetScore = s;
        return this;
    }

    /**
     * Indicates whether robust scaling of residuals is enabled.
     *
     * @return {@code true} if robust scaling of residuals is enabled; {@code false} otherwise.
     */
    public boolean isRobustScaleResiduals() {
        return robustScaleResiduals;
    }

    /**
     * Sets whether robust scaling of residuals is enabled.
     *
     * @param v {@code true} to enable robust scaling of residuals; {@code false} to disable.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setRobustScaleResiduals(boolean v) {
        this.robustScaleResiduals = v;
        return this;
    }

    /**
     * Retrieves the covariance mode.
     *
     * @return the covariance mode.
     */
    public CovarianceMode getCovarianceMode() {
        return covarianceMode;
    }

    /**
     * Sets the covariance mode.
     *
     * @param m the covariance mode to set; must not be null.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setCovarianceMode(CovarianceMode m) {
        this.covarianceMode = m;
        return this;
    }

    /**
     * Retrieves the full sigma safety margin.
     *
     * @return the full sigma safety margin.
     */
    public int getFullSigmaSafetyMargin() {
        return fullSigmaSafetyMargin;
    }

    /**
     * Sets the full sigma safety margin.
     *
     * @param m the full sigma safety margin to set; must be non-negative.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setFullSigmaSafetyMargin(int m) {
        this.fullSigmaSafetyMargin = Math.max(0, m);
        return this;
    }

    /**
     * Retrieves the number of k-means restarts.
     *
     * @return the number of k-means restarts.
     */
    public int getKmeansRestarts() {
        return kmeansRestarts;
    }

    /**
     * Sets the number of k-means restarts.
     *
     * @param r the number of k-means restarts to set; must be positive.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setKmeansRestarts(int r) {
        this.kmeansRestarts = Math.max(1, r);
        return this;
    }

    /**
     * Retrieves the maximum number of iterations for the EM algorithm.
     *
     * @return the maximum number of iterations for the EM algorithm.
     */
    public int getEmMaxIters() {
        return emMaxIters;
    }

    /**
     * Sets the maximum number of iterations for the EM algorithm.
     *
     * @param it the maximum number of iterations to set; must be positive.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setEmMaxIters(int it) {
        this.emMaxIters = Math.max(1, it);
        return this;
    }

    /**
     * Retrieves the ridge parameter.
     *
     * @return the ridge parameter.
     */
    public double getRidge() {
        return ridge;
    }

    /**
     * Sets the ridge parameter.
     *
     * @param r the ridge parameter to set; must be non-negative.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setRidge(double r) {
        this.ridge = Math.max(0.0, r);
        return this;
    }

    /**
     * Retrieves the shrinkage parameter.
     *
     * @return the shrinkage parameter.
     */
    public double getShrinkage() {
        return shrinkage;
    }

    /**
     * Sets the shrinkage parameter.
     *
     * @param s the shrinkage parameter to set; must be between 0 and 1 inclusive.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setShrinkage(double s) {
        this.shrinkage = Math.max(0.0, Math.min(1.0, s));
        return this;
    }

    /**
     * Retrieves the number of annealing steps.
     *
     * @return the number of annealing steps.
     */
    public int getAnnealSteps() {
        return annealSteps;
    }

    /**
     * Sets the number of annealing steps.
     *
     * @param s the number of annealing steps to set; must be non-negative.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setAnnealSteps(int s) {
        this.annealSteps = Math.max(0, s);
        return this;
    }

    /**
     * Retrieves the starting temperature for annealing.
     *
     * @return the starting temperature for annealing.
     */
    public double getAnnealStartT() {
        return annealStartT;
    }

    /**
     * Sets the starting temperature for annealing.
     *
     * @param t the starting temperature to set; must be non-negative.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setAnnealStartT(double t) {
        this.annealStartT = Math.max(0.0, t);
        return this;
    }

    /**
     * Retrieves the random seed.
     *
     * @return the random seed.
     */
    public long getRandomSeed() {
        return randomSeed;
    }

    /**
     * Sets the random seed.
     *
     * @param s the random seed to set; must be non-negative.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setRandomSeed(long s) {
        this.randomSeed = s;
        return this;
    }

    /**
     * Retrieves whether to save diagnostics.
     *
     * @return true if diagnostics are saved, false otherwise.
     */
    public boolean isSaveDiagnostics() {
        return saveDiagnostics;
    }

    /**
     * Sets whether to save diagnostics.
     *
     * @param v true to save diagnostics, false otherwise.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setSaveDiagnostics(boolean v) {
        this.saveDiagnostics = v;
        return this;
    }

    /**
     * The GraphLearner enum represents different algorithms or strategies used for graph learning processes within a
     * system.
     */
    public enum GraphLearner {

        /**
         * Represents the BOSS graph learning algorithm.
         */
        BOSS,

        /**
         * Represents the PC-Max graph learning algorithm.
         */
        PC_MAX
    }

    /**
     * The ColliderStyle enum represents different styles for handling colliders during graph learning processes.
     */
    public enum ColliderStyle {

        /**
         * Represents the Sepsets collider style in the graph learning process. This style prioritizes the use of
         * separating sets (sepsets) information to determine the orientation of colliders during inference.
         */
        SEPSETS,

        /**
         * Represents the Conservative collider style in the graph learning process. This style is more cautious and may
         * result in fewer edges being inferred.
         */
        CONSERVATIVE,

        /**
         * Represents the Max_P collider style in the graph learning process. This style prioritizes maximizing the
         * number of edges inferred.
         */
        MAX_P
    }

    /**
     * The SupersetScore enum represents statistical ranking correlation coefficients that can be used to assess the
     * relationship between two rankings.
     * <p>
     * This enum can be utilized in various data analysis contexts where correlation between ranked data is required.
     */
    public enum SupersetScore {

        /**
         * Represents Kendall's tau coefficient, a measure of ordinal association between two ranked variables. It
         * evaluates the strength and direction of association, focusing on concordant and discordant pairs of
         * rankings.
         */
        KENDALL,

        /**
         * Represents Spearman's rank-order correlation coefficient, a non-parametric measure of the monotonic
         * relationship between two ranked variables. Spearman's coefficient assesses how well the relationship between
         * two variables can be described using a monotonic function, making it suitable for ordinal data or data that
         * do not meet the assumptions of parametric correlation methods.
         */
        SPEARMAN
    }

    // ---- Covariance policy ---

    /**
     * The CovarianceMode enum represents different modes for handling covariance calculations during graph learning
     * processes.
     */
    public enum CovarianceMode {

        /**
         * Represents the automatic mode for covariance calculations. This mode automatically determines the appropriate
         * covariance calculation based on the data characteristics.
         */
        AUTO,

        /**
         * Represents the full covariance mode. This mode calculates the full covariance matrix, including all pairwise
         * covariances between variables.
         */
        FULL,

        /**
         * Represents the diagonal covariance mode. This mode calculates only the diagonal elements of the covariance
         * matrix, ignoring off-diagonal covariances.
         */
        DIAGONAL
    }
}
