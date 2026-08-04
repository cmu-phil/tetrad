///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.data.missing;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;

/**
 * An immutable specification of how missing data are to be handled: a {@link MissingDataPolicy} together with the
 * parameters that policy needs (EM settings for {@link MissingDataPolicy#EM_COVARIANCE}, the number of imputations
 * for {@link MissingDataPolicy#MULTIPLE_IMPUTATION}, and so on). The whole configuration travels as one object so
 * that constructors, algcomparison wrappers, and py-tetrad can pass it through uniformly.
 * <p>
 * Instances are created from the static factory methods and refined with the {@code with...} methods, e.g.:
 * <pre>
 *     MissingDataSpec spec = MissingDataSpec.emCovariance().withEmRidge(1e-4);
 * </pre>
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see MissingDataPolicy
 */
public final class MissingDataSpec implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The policy.
     */
    private final MissingDataPolicy policy;

    /**
     * Ridge added to the diagonal in the EM M-step, for numerical stability. Used by EM_COVARIANCE.
     */
    private final double emRidge;

    /**
     * Convergence tolerance for the EM observed-data log likelihood. Used by EM_COVARIANCE.
     */
    private final double emTolerance;

    /**
     * Maximum number of EM iterations. Used by EM_COVARIANCE.
     */
    private final int emMaxIterations;

    /**
     * The number of imputed datasets, m. Used by MULTIPLE_IMPUTATION.
     */
    private final int numImputations;

    /**
     * Random seed for stochastic policies (multiple imputation); -1 means no fixed seed.
     */
    private final long seed;

    /**
     * How the effective sample size for penalized scores is determined when the analysis is run on an estimated
     * covariance matrix.
     */
    private final EffectiveSampleSizeMode essMode;

    /**
     * Constructs a spec. Private; use the static factory methods.
     *
     * @param policy          The policy.
     * @param emRidge         The EM ridge.
     * @param emTolerance     The EM tolerance.
     * @param emMaxIterations The maximum number of EM iterations.
     * @param numImputations  The number of imputations.
     * @param seed            The random seed, or -1 for none.
     * @param essMode         The effective sample size mode.
     */
    private MissingDataSpec(MissingDataPolicy policy, double emRidge, double emTolerance, int emMaxIterations,
                            int numImputations, long seed, EffectiveSampleSizeMode essMode) {
        if (policy == null) throw new NullPointerException("Policy is null.");
        if (emRidge < 0) throw new IllegalArgumentException("EM ridge must be >= 0: " + emRidge);
        if (emTolerance <= 0) throw new IllegalArgumentException("EM tolerance must be > 0: " + emTolerance);
        if (emMaxIterations < 1) throw new IllegalArgumentException("EM max iterations must be >= 1: " + emMaxIterations);
        if (numImputations < 2) throw new IllegalArgumentException("Number of imputations must be >= 2: " + numImputations);
        if (essMode == null) throw new NullPointerException("Effective sample size mode is null.");

        this.policy = policy;
        this.emRidge = emRidge;
        this.emTolerance = emTolerance;
        this.emMaxIterations = emMaxIterations;
        this.numImputations = numImputations;
        this.seed = seed;
        this.essMode = essMode;
    }

    /**
     * A spec with the given policy and default parameters.
     *
     * @param policy The policy.
     * @return The spec.
     */
    public static MissingDataSpec of(MissingDataPolicy policy) {
        return new MissingDataSpec(policy, 0.0, 1e-6, 1000, 10, -1L,
                EffectiveSampleSizeMode.FULL_N);
    }

    /**
     * A spec that refuses data containing missing values.
     *
     * @return The spec.
     */
    public static MissingDataSpec fail() {
        return of(MissingDataPolicy.FAIL);
    }

    /**
     * A spec for listwise deletion.
     *
     * @return The spec.
     */
    public static MissingDataSpec listwise() {
        return of(MissingDataPolicy.LISTWISE);
    }

    /**
     * A spec for test-wise deletion. This reproduces the historical implicit behavior of, e.g., SemBicScore on data
     * with missing values, but explicitly.
     *
     * @return The spec.
     */
    public static MissingDataSpec testwise() {
        return of(MissingDataPolicy.TESTWISE);
    }

    /**
     * A spec for EM covariance estimation with default EM settings.
     *
     * @return The spec.
     */
    public static MissingDataSpec emCovariance() {
        return of(MissingDataPolicy.EM_COVARIANCE);
    }

    /**
     * A spec for multiple imputation with the given number of imputations.
     *
     * @param numImputations The number of imputed datasets, m; must be at least 2.
     * @return The spec.
     */
    public static MissingDataSpec multipleImputation(int numImputations) {
        return of(MissingDataPolicy.MULTIPLE_IMPUTATION).withNumImputations(numImputations);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return This exemplar.
     */
    public static MissingDataSpec serializableInstance() {
        return testwise();
    }

    /**
     * Returns a copy of this spec with the given EM ridge.
     *
     * @param emRidge The ridge; must be nonnegative.
     * @return The copy.
     */
    public MissingDataSpec withEmRidge(double emRidge) {
        return new MissingDataSpec(this.policy, emRidge, this.emTolerance, this.emMaxIterations,
                this.numImputations, this.seed, this.essMode);
    }

    /**
     * Returns a copy of this spec with the given EM convergence tolerance.
     *
     * @param emTolerance The tolerance; must be positive.
     * @return The copy.
     */
    public MissingDataSpec withEmTolerance(double emTolerance) {
        return new MissingDataSpec(this.policy, this.emRidge, emTolerance, this.emMaxIterations,
                this.numImputations, this.seed, this.essMode);
    }

    /**
     * Returns a copy of this spec with the given maximum number of EM iterations.
     *
     * @param emMaxIterations The maximum; must be at least 1.
     * @return The copy.
     */
    public MissingDataSpec withEmMaxIterations(int emMaxIterations) {
        return new MissingDataSpec(this.policy, this.emRidge, this.emTolerance, emMaxIterations,
                this.numImputations, this.seed, this.essMode);
    }

    /**
     * Returns a copy of this spec with the given number of imputations.
     *
     * @param numImputations The number of imputed datasets, m; must be at least 2.
     * @return The copy.
     */
    public MissingDataSpec withNumImputations(int numImputations) {
        return new MissingDataSpec(this.policy, this.emRidge, this.emTolerance, this.emMaxIterations,
                numImputations, this.seed, this.essMode);
    }

    /**
     * Returns a copy of this spec with the given random seed.
     *
     * @param seed The seed; -1 means no fixed seed.
     * @return The copy.
     */
    public MissingDataSpec withSeed(long seed) {
        return new MissingDataSpec(this.policy, this.emRidge, this.emTolerance, this.emMaxIterations,
                this.numImputations, seed, this.essMode);
    }

    /**
     * Returns a copy of this spec with the given effective sample size mode.
     *
     * @param essMode The mode.
     * @return The copy.
     */
    public MissingDataSpec withEssMode(EffectiveSampleSizeMode essMode) {
        return new MissingDataSpec(this.policy, this.emRidge, this.emTolerance, this.emMaxIterations,
                this.numImputations, this.seed, essMode);
    }

    /**
     * The policy.
     *
     * @return This policy.
     */
    public MissingDataPolicy getPolicy() {
        return this.policy;
    }

    /**
     * The EM ridge.
     *
     * @return This ridge.
     */
    public double getEmRidge() {
        return this.emRidge;
    }

    /**
     * The EM convergence tolerance.
     *
     * @return This tolerance.
     */
    public double getEmTolerance() {
        return this.emTolerance;
    }

    /**
     * The maximum number of EM iterations.
     *
     * @return This maximum.
     */
    public int getEmMaxIterations() {
        return this.emMaxIterations;
    }

    /**
     * The number of imputations, m.
     *
     * @return This number.
     */
    public int getNumImputations() {
        return this.numImputations;
    }

    /**
     * The random seed, or -1 if none is fixed.
     *
     * @return This seed.
     */
    public long getSeed() {
        return this.seed;
    }

    /**
     * The effective sample size mode.
     *
     * @return This mode.
     */
    public EffectiveSampleSizeMode getEssMode() {
        return this.essMode;
    }

    /**
     * A string representation of the spec.
     *
     * @return This string.
     */
    @Override
    public String toString() {
        return switch (this.policy) {
            case FAIL, LISTWISE, TESTWISE -> "MissingDataSpec(" + this.policy + ")";
            case EM_COVARIANCE -> "MissingDataSpec(EM_COVARIANCE, ridge=" + this.emRidge
                    + ", tol=" + this.emTolerance + ", maxIter=" + this.emMaxIterations
                    + ", essMode=" + this.essMode + ")";
            case MULTIPLE_IMPUTATION -> "MissingDataSpec(MULTIPLE_IMPUTATION, m=" + this.numImputations
                    + ", seed=" + this.seed + ")";
        };
    }

    /**
     * How the effective sample size used by penalized scores (e.g., the n in BIC) is determined when the analysis
     * runs on a covariance matrix estimated from incomplete data. With missing entries, the nominal number of rows
     * overstates the information actually available; these modes offer conservative alternatives.
     */
    public enum EffectiveSampleSizeMode {

        /**
         * Use the full number of rows of the dataset. The default; anti-conservative when much data is missing.
         */
        FULL_N,

        /**
         * Use the minimum, over variable pairs, of the number of rows on which both variables are observed. The most
         * conservative choice.
         */
        MIN_PAIRWISE,

        /**
         * Use the mean, over variable pairs, of the number of rows on which both variables are observed.
         */
        MEAN_PAIRWISE
    }
}
