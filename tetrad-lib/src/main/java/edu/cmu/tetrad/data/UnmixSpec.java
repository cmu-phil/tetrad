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

    // ---- K handling ----
    // If autoSelectK == true, use Kmin..Kmax; otherwise use K (>=1).
    private boolean autoSelectK = true;
    private int K = 2;
    private int Kmin = 1;
    private int Kmax = 4;
    private GraphLearner graphLearner = GraphLearner.BOSS;
    // PC-only knobs
    private double pcAlpha = 0.01;
    private ColliderStyle pcColliderStyle = ColliderStyle.MAX_P;
    // BOSS-only knobs (use what you actually wire; this is a common one)
    private double bossPenaltyDiscount = 2.0;
    // ---- Parent-superset residualization ----
    private boolean useParentSuperset = true;
    private int supersetTopM = 12;
    private SupersetScore supersetScore = SupersetScore.KENDALL;
    // ---- Residual scaling ----
    private boolean robustScaleResiduals = true;
    private CovarianceMode covarianceMode = CovarianceMode.AUTO;
    // Used only when AUTO: require n/K >= p + safetyMargin to use FULL
    private int fullSigmaSafetyMargin = 10;
    // ---- EM stability ----
    private int kmeansRestarts = 20;
    private int emMaxIters = 300;
    private double ridge = 1e-3;
    private double shrinkage = 0.10;  // optional; use if you wire it
    private int annealSteps = 15;     // optional
    private double annealStartT = 0.8;// optional
    private long randomSeed = 13L;
    // ---- Diagnostics ----
    private boolean saveDiagnostics = false;
    private boolean logIntermediate = false;

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
     * @param v a boolean value indicating whether to enable (true) or disable (false)
     *          the automatic selection of K.
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
     * Sets the value of K for the current instance of UnmixSpec.
     * K must be greater than or equal to 1.
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
     * Sets the minimum allowed value of K. Ensures that the value of Kmin
     * is at least 1.
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
     * Sets the maximum allowed value of K (Kmax) for the current instance of UnmixSpec. Ensures
     * that the value of Kmax is at least 1.
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
     * @return the currently configured GraphLearner, which determines the method
     *         used for graph learning (e.g., BOSS or PC_MAX).
     */
    public GraphLearner getGraphLearner() {
        return graphLearner;
    }

    /**
     * Sets the graph learning method for this instance of UnmixSpec.
     * The provided GraphLearner determines the algorithm to be used
     * for graph learning (e.g., BOSS or PC_MAX).
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
     * @return the alpha value for the PC algorithm, which is typically used as a threshold
     *         for significance in tests.
     */
    public double getPcAlpha() {
        return pcAlpha;
    }

    /**
     * Sets the alpha value for the PC algorithm. This value is typically used
     * as a significance threshold in statistical testing within the PC algorithm.
     *
     * @param a the alpha value to set for the PC algorithm; must be a non-negative
     *          double representing the significance level.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setPcAlpha(double a) {
        this.pcAlpha = a;
        return this;
    }

    /**
     * Retrieves the collider style used in the PC algorithm for handling colliders
     * during causal discovery. The collider style determines the specific method
     * or approach used to identify colliders in the causal graph.
     *
     * @return the configured {@link ColliderStyle} for the PC algorithm, which
     *         may be one of SEPSETS, CONSERVATIVE, or MAX_P.
     */
    public ColliderStyle getPcColliderStyle() {
        return pcColliderStyle;
    }

    /**
     * Sets the collider style to be used in the PC algorithm for identifying colliders
     * during causal graph discovery. The collider style determines the approach applied
     * for handling colliders (e.g., SEPSETS, CONSERVATIVE, or MAX_P).
     *
     * @param s the {@link ColliderStyle} to set; must be a valid enumeration value.
     * @return the current instance of UnmixSpec for method chaining.
     */
    public UnmixSpec setPcColliderStyle(ColliderStyle s) {
        this.pcColliderStyle = s;
        return this;
    }

    /**
     * Retrieves the BOSS penalty discount value. The BOSS penalty discount is
     * used as a parameter in graph learning algorithms to adjust or scale
     * penalties during the learning process.
     *
     * @return the current BOSS penalty discount value as a double.
     */
    public double getBossPenaltyDiscount() {
        return bossPenaltyDiscount;
    }

    /**
     * Sets the BOSS penalty discount value for this instance of UnmixSpec.
     * The BOSS penalty discount is a parameter used in graph learning algorithms
     * to adjust or scale penalties during the learning process.
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
     * The GraphLearner enum represents different algorithms or strategies
     * used for graph learning processes within a system.
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
     * The ColliderStyle enum represents different styles for handling colliders
     * during graph learning processes.
     */
    public enum ColliderStyle {

        /**
         * Represents the Sepsets collider style in the graph learning process.
         * This style prioritizes the use of separating sets (sepsets) information
         * to determine the orientation of colliders during inference.
         */
        SEPSETS,

        /**
         * Represents the Conservative collider style in the graph learning process.
         * This style is more cautious and may result in fewer edges being inferred.
         */
        CONSERVATIVE,

        /**
         * Represents the Max_P collider style in the graph learning process.
         * This style prioritizes maximizing the number of edges inferred.
         */
        MAX_P
    }

    public enum SupersetScore {KENDALL, SPEARMAN}

    // ---- Covariance policy ---

    /**
     * The CovarianceMode enum represents different modes for handling covariance
     * calculations during graph learning processes.
     */
    public enum CovarianceMode {

        /**
         * Represents the automatic mode for covariance calculations.
         * This mode automatically determines the appropriate covariance calculation
         * based on the data characteristics.
         */
        AUTO,

        /**
         * Represents the full covariance mode.
         * This mode calculates the full covariance matrix, including all pairwise
         * covariances between variables.
         */
        FULL,

        /**
         * Represents the diagonal covariance mode.
         * This mode calculates only the diagonal elements of the covariance matrix,
         * ignoring off-diagonal covariances.
         */
        DIAGONAL
    }
}
