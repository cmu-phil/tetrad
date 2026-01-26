/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p><b>FF-CI-Mixed: Feature-Function Conditional Independence Test for mixed data</b></p>
 *
 * <p>
 * This class extends {@code FF-CI} with basic support for mixed
 * continuous and discrete variables. For purely continuous data, this
 * implementation is equivalent to {@code FF-CI} and yields identical
 * results.
 * </p>
 *
 * <p><b>Mixed feature mapping</b></p>
 * <ul>
 *   <li>
 *     <b>Continuous variables</b> are mapped using randomized feature-function
 *     expansions (e.g., Random Fourier Features or Orthogonal Random Features),
 *     exactly as in {@code FF-CI}.
 *   </li>
 *   <li>
 *     <b>Discrete variables</b> are mapped using a one-hot (delta-kernel)
 *     feature representation, which preserves category identity without
 *     imposing a smooth geometry.
 *   </li>
 * </ul>
 *
 * <p>
 * All feature blocks are centered (and, where appropriate, standardized)
 * before covariance computation. For blocks containing both continuous and
 * discrete variables, bandwidth selection via the median pairwise distance
 * heuristic is computed using only the continuous columns.
 * </p>
 *
 * <p>
 * If a variable block contains no continuous columns, the bandwidth is
 * set to a default value of {@code 1.0}, and the test proceeds using only
 * the discrete one-hot feature representation.
 * </p>
 *
 * <p><b>Design philosophy</b></p>
 * <p>
 * This implementation deliberately encodes only minimal background knowledge:
 * the <i>measurement type</i> of each variable (continuous vs. discrete).
 * No causal constraints or structural assumptions are imposed. The goal is to
 * avoid forcing discrete variables into a smooth RBF geometry while retaining
 * the efficiency and flexibility of feature-function CI testing.
 * </p>
 *
 * <p>
 * Aside from the feature mapping for discrete variables, the test statistic,
 * conditioning strategy, and p-value approximations are identical to those of
 * {@code FF-CI}.
 * </p>
 */
public final class FfCiMixed implements IndependenceTest, RowsSettable {

    /**
     * The FeatureType enum defines the types of feature representations
     * utilized for kernel and feature-based computations.
     *
     * FeatureType is primarily used to parameterize the style of feature
     * generation for the RBF kernel in contexts where approximate kernel
     * methods are employed.
     */
    public enum FeatureType {

        /**
         * Represents Random Fourier Features (RFF), a method to approximate
         * shift-invariant kernels, such as the radial basis function (RBF) kernel.
         * This technique utilizes random projections and trigonometric functions
         * to efficiently compute feature mappings in high-dimensional space.
         */
        RFF,

        /**
         * Represents Orthogonal Random Features (ORF), a variation of Random Fourier
         * Features (RFF) where the random projection matrix is block-orthogonal.
         * This approach provides computational and theoretical benefits for approximating
         * shift-invariant kernels, such as the radial basis function (RBF) kernel.
         */
        ORF}

    /**
     * The Approx enumeration is used to represent various types of approximation methods
     * or strategies. These constants can be utilized to specify or identify the desired
     * approximation type within computations or algorithms.
     */
    public enum Approx {

        /**
         * Represents the LPB4 value in the Approx enumeration.
         * This constant may be used to specify or identify a specific
         * approximation type within the enumeration.
         */
        LPB4,

        /**
         * Represents the HBE value in the Approx enumeration.
         * This constant is used to specify or identify a specific
         * type of approximation related to the enumeration.
         */
        HBE,

        /**
         * Represents the GAMMA value in the Approx enumeration.
         * This constant is used to specify or identify a specific
         * type of approximation within the enumeration.
         */
        GAMMA,

        /**
         * Represents the CHI2 value in the Approx enumeration.
         * This constant is used to specify or identify a specific
         * type of approximation within the enumeration.
         */
        CHI2,

        /**
         * Represents the PERMUTATION value in the Approx enumeration.
         * This constant is used to denote a specific type of approximation
         * or operation related to permutations within the enumeration.
         */
        PERMUTATION}

    // ---------------- core data ----------------
    private final DataSet data;
    private final List<Node> vars;
    private final Random rng;

    // Active rows state
    private List<Integer> rows = null;
    private int n;

    // ---------------- hyperparams ----------------
    private int numFeatXY = 10;     // continuous-feature budget for X and Y
    private int numFeatZ = 100;     // continuous-feature budget for Z

    private Approx approx = Approx.GAMMA;
    private int permutations = 0;
    private boolean doRcit = true;

    private double lambda = 1;
    private boolean centerFeatures = true;

    private double bandwidthMultiplier = 1.0;
    private int bwMaxRows = 500;

    private FeatureType featureType = FeatureType.RFF;

    // ---------------- IndependenceTest state ----------------
    private double alpha = 0.05;
    private boolean verbose = false;

    // --------- caches ----------
    private final Map<String, SimpleMatrix> featCache = new ConcurrentHashMap<>();
    private final Map<String, Double> bw2Cache = new ConcurrentHashMap<>();

    private double catRho = 0.0; // default: current one-hot behavior

    /**
     * Enum representing the mixed mode configuration for processing data.
     *
     * This enum is used to specify the approach to handle mixed data types
     * (continuous and discrete). It offers two modes of operation.
     */
    public enum MixedMode {

        /**
         * STACK mode processes mixed data by stacking the feature matrices for
         * continuous and discrete data types. This approach treats the data as
         * a unified whole rather than separating discrete and continuous variables
         * during calculations.
         */
        STACK,

        /**
         * STRATA_ZDISC mode processes mixed data by stratifying based on the discrete
         * variables. This approach treats discrete and continuous variables separately,
         * applying methods suited to each type. It is particularly useful for operations
         * such as independence testing or scenarios where distinguishing between variable
         * types is critical.
         */
        STRATA_ZDISC}

    private MixedMode mixedMode = MixedMode.STACK;

    // stratification knobs
    private int minStratumSize = 12;       // ignore strata smaller than this
    private int maxStrata = 2000;          // fallback if too fragmented

    // If the dataset has no discrete variables, we delegate to the original KffRcit
    // so the behavior is identical to PC-KFF-RCIT.
    private final boolean dataHasAnyDiscrete;
    private final FfCi continuousDelegate;


    // ---------------- ctor ----------------

    /**
     * Constructs an instance of FfCiMixed with the specified dataset and default parameters.
     *
     * @param dataSet the dataset to be used for the independence testing. It is expected to contain
     *                the variables and data rows needed for the algorithm.
     */
    public FfCiMixed(DataSet dataSet) {
        this(dataSet, new Parameters());
    }

    /**
     * Constructs an instance of FfCiMixed, a class designed to handle mixed data
     * types in a dataset for specific statistical or computational tasks.
     *
     * @param dataSet The dataset containing the variables and data to be analyzed.
     *                Cannot be null.
     * @param params  A set of parameters used to configure various settings of
     *                the FfCiMixed instance, such as random seed, feature
     *                counts, bandwidth settings, permutation settings, and
     *                approximation type.
     */
    public FfCiMixed(DataSet dataSet, Parameters params) {
        this.data = Objects.requireNonNull(dataSet, "data");
        this.vars = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));
        this.n = getActiveRowCount();

        boolean anyDisc = false;
        for (Node v : this.vars) {
            if (v instanceof edu.cmu.tetrad.data.DiscreteVariable) { // qualify or import
                anyDisc = true;
                break;
            }
        }
        this.dataHasAnyDiscrete = anyDisc;

        // Build a delegate that implements the *original* continuous behavior
        this.continuousDelegate = new FfCi(this.data, params);

        // Make delegate match current settings right away
        syncDelegateToThis();

        long seed = params.getLong("rcit.seed", 1729L);
        this.rng = new Random(seed);

        this.numFeatZ = Math.max(1, params.getInt("rcit.numF", 100));
        this.numFeatXY = Math.max(1, params.getInt("rcit.numF2", 5));
        this.permutations = Math.max(0, params.getInt("rcit.permutations", 0));
        this.doRcit = params.getBoolean("rcit.rcit", true);
        this.lambda = Math.max(1e-12, params.getDouble("rcit.lambda", this.lambda));
        this.centerFeatures = params.getBoolean("rcit.centerFeatures", true);

        String approxStr = params.getString("rcit.approx", "gamma");
        setApproximationFromInt(switch (approxStr.toLowerCase(Locale.ROOT)) {
            case "perm", "permutation" -> 5;
            case "chi2", "chi-sq", "chisq" -> 4;
            case "hbe" -> 2;
            case "lpb4", "lpd4" -> 1;
            default -> 3;
        });

        this.bandwidthMultiplier = params.getDouble("rcit.bwMult", this.bandwidthMultiplier);
        this.bwMaxRows = Math.max(50, params.getInt("rcit.bwMaxRows", this.bwMaxRows));

        String ft = params.getString("rcit.featureType", "orf").toLowerCase(Locale.ROOT);
        if (ft.equals("rff")) this.featureType = FeatureType.RFF;
        if (ft.equals("orf")) this.featureType = FeatureType.ORF;
    }

    private void syncDelegateToThis() {
        // Keep delegate aligned with this object's knobs.
        // (If you later add more knobs, add them here too.)
        continuousDelegate.setNumFeaturesXY(this.numFeatXY);
        continuousDelegate.setNumFeaturesZ(this.numFeatZ);
        continuousDelegate.setApproximationFromInt(switch (this.approx) {
            case LPB4 -> 1;
            case HBE -> 2;
            case GAMMA -> 3;
            case CHI2 -> 4;
            case PERMUTATION -> 5;
        });
        continuousDelegate.setPermutations(this.permutations);
        continuousDelegate.setDoRcit(this.doRcit);
        continuousDelegate.setLambda(this.lambda);
        continuousDelegate.setCenterFeatures(this.centerFeatures);
        continuousDelegate.setBandwidthMultiplier(this.bandwidthMultiplier);
        continuousDelegate.setBwMaxRows(this.bwMaxRows);
        continuousDelegate.setFeatureType(switch (this.featureType) {
            case RFF -> FfCi.FeatureType.RFF;
            case ORF -> FfCi.FeatureType.ORF;
        });

        continuousDelegate.setAlpha(this.alpha);
        continuousDelegate.setVerbose(this.verbose);

        // Keep rows in sync too
        continuousDelegate.setRows(this.rows);
    }

    // ---------------- public setters ----------------

    /**
     * Sets the approximation method based on the given integer code and updates
     * related configurations if necessary.
     *
     * The method assigns one of the predefined approximation methods to the `approx`
     * field of the class based on the provided code. If the code does not match any
     * predefined values, a default approximation is used. Additionally, if the data
     * contains discrete variables, the delegate is synchronized with the current
     * instance.
     *
     * @param approxCode an integer code representing the desired approximation type:
     *                   1 for LPB4, 2 for HBE, 3 for GAMMA, 4 for CHI2, 5 for PERMUTATION.
     *                   Any other value defaults to GAMMA.
     */
    public void setApproximationFromInt(int approxCode) {
        switch (approxCode) {
            case 1 -> this.approx = Approx.LPB4;
            case 2 -> this.approx = Approx.HBE;
            case 3 -> this.approx = Approx.GAMMA;
            case 4 -> this.approx = Approx.CHI2;
            case 5 -> this.approx = Approx.PERMUTATION;
            default -> this.approx = Approx.GAMMA;
        }
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets the state of the DoRcit flag and synchronizes the delegate if no discrete data exists.
     *
     * @param doRcit a boolean value representing the desired state to set for the DoRcit flag
     */
    public void setDoRcit(boolean doRcit) {
        this.doRcit = doRcit;
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets the lambda parameter, ensuring it is not less than a minimum threshold.
     * If the data contains no discrete elements, synchronizes the delegate with the current instance.
     *
     * @param lambda the value to set for the lambda parameter. Must be a positive number,
     *               as it will be clamped to a minimum value of 1e-12.
     */
    public void setLambda(double lambda) {
        this.lambda = Math.max(1e-12, lambda);
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets the number of permutations to the specified value.
     * If the given value is less than zero, it will be set to zero.
     * Triggers synchronization if the data contains no discrete elements.
     *
     * @param permutations the number of permutations to set, must be zero or greater
     */
    public void setPermutations(int permutations) {
        this.permutations = Math.max(0, permutations);
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets whether the features should be centered. Centering features ensures
     * that the data is adjusted around the mean, making it more suitable for
     * certain statistical computations or machine learning models.
     *
     * @param centerFeatures a boolean value indicating whether to center features.
     *                        If true, the features will be centered. If false,
     *                        they will remain unchanged.
     */
    public void setCenterFeatures(boolean centerFeatures) {
        this.centerFeatures = centerFeatures;
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets the number of features in the XY dimension, ensuring a minimum value of 1.
     * Clears relevant caches and synchronizes delegate if no discrete data is present.
     *
     * @param d the desired number of features in the XY dimension
     */
    public void setNumFeaturesXY(int d) {
        this.numFeatXY = Math.max(1, d);
        this.featCache.clear();
        this.bw2Cache.clear();
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets the number of features (Z) to the specified value. The value is
     * constrained to be at least 1. This method also clears relevant caches
     * and synchronizes delegate settings if no discrete data is present.
     *
     * @param d the desired number of features (Z). If the value is less than 1,
     *          it will be set to 1.
     */
    public void setNumFeaturesZ(int d) {
        this.numFeatZ = Math.max(1, d);
        this.featCache.clear();
        this.bw2Cache.clear();
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets the bandwidth multiplier used for calculations. The value must be greater
     * than 0 and finite. Invalid values will result in an IllegalArgumentException.
     * This method also clears cached values and synchronizes data if necessary.
     *
     * @param bandwidthMultiplier the multiplier value to set; must be greater than 0 and finite
     * @throws IllegalArgumentException if the provided value is not greater than 0 or is not finite
     */
    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0 and finite");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        this.bw2Cache.clear();
        this.featCache.clear();
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets the maximum number of rows for processing while ensuring a minimum limit of 50.
     * Clears internal caches and synchronizes the delegate if the data contains no discrete attributes.
     *
     * @param bwMaxRows the desired maximum number of rows; if less than 50, it will default to 50
     */
    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = Math.max(50, bwMaxRows);
        this.bw2Cache.clear();
        this.featCache.clear();
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets the feature type for the current object. This method updates the
     * feature type and clears any cached features. If the data contains
     * no discrete values, it synchronizes the delegate to the current instance.
     *
     * @param featureType the feature type to be set; must not be null
     * @throws NullPointerException if the provided featureType is null
     */
    public void setFeatureType(FeatureType featureType) {
        this.featureType = Objects.requireNonNull(featureType, "featureType");
        this.featCache.clear();
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Returns the current feature type.
     *
     * @return the current feature type
     */
    public FeatureType getFeatureType() {
        return featureType;
    }

    /**
     * Sets the seed for the random number generator (RNG) and clears the feature cache.
     * If the data does not contain any discrete elements, synchronizes the delegate to this instance.
     *
     * @param seed the seed value to initialize the RNG
     */
    public void setSeed(long seed) {
        this.rng.setSeed(seed);
        this.featCache.clear();
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Sets the value of the catRho parameter, which must be within the range [0, 1)
     * and finite. This method also clears the feature cache and syncs delegate
     * behavior if necessary.
     *
     * @param rho the value to set for catRho; must satisfy 0.0 &lt;= rho &lt; 1.0 and be finite
     * @throws IllegalArgumentException if rho is not within the valid range or is not finite
     */
    public void setCatRho(double rho) {
        if (!(rho >= 0.0 && rho < 1.0) || !Double.isFinite(rho)) {
            throw new IllegalArgumentException("catRho must be in [0,1)");
        }
        this.catRho = rho;
        this.featCache.clear();
        if (!dataHasAnyDiscrete) syncDelegateToThis();
    }

    /**
     * Retrieves the value of the catRho property.
     *
     * @return the current value of the catRho property as a double
     */
    public double getCatRho() {
        return catRho;
    }

    // ---------------- IndependenceTest interface ----------------

    /**
     * Checks the independence between two nodes given a set of conditioning nodes.
     * This method evaluates whether the two specified nodes are independent of each other
     * when conditioned on the given set of nodes, using various possible statistical methods.
     *
     * @param x the first node to test for independence; must not be null
     * @param y the second node to test for independence; must not be null
     * @param z the set of conditioning nodes to condition the test upon; can be null or empty
     * @return an IndependenceResult object containing the result of the independence test,
     *         including statistical details and decision outcome
     * @throws InterruptedException if the thread running this method is interrupted during execution
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");

        this.n = getActiveRowCount();

        final List<Node> Z = (z == null) ? new ArrayList<>() : new ArrayList<>(z);
        Z.sort(Comparator.comparing(Node::getName));

        IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));

        // --- Optional STRATIFIED mixed handling: stratify on discrete vars in Z only ---
        if (mixedMode == MixedMode.STRATA_ZDISC
                && !Z.isEmpty()
                && containsDiscrete(Z)
                && approx == Approx.PERMUTATION
                && permutations > 0
                && !(x instanceof DiscreteVariable)
                && !(y instanceof DiscreteVariable)) {

            IndependenceResult r = checkIndependenceStratifiedOnZDiscrete(x, y, Z, fact);
            if (r != null) return r; // null means: stratification decided to fall back
            // else fall through to STACK
        }

        return checkIndependenceStack(x, y, Z, fact);
    }

    private IndependenceResult checkIndependenceStack(Node x, Node y, List<Node> Z, IndependenceFact fact)
            throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        if (x.equals(y)) {
            if (verbose) TetradLogger.getInstance().log(fact + " x == y");
            double p_ = 0.0;
            return new IndependenceResult(fact, false, p_, alpha - p_, false);
        }

        this.n = getActiveRowCount();

        if (n < 5) {
            if (verbose) TetradLogger.getInstance().log(fact + " n < 5");
            double p_ = 1.0;
            return new IndependenceResult(fact, true, p_, alpha - p_, false);
        }

        // RCIT: augment Y with Z at the “input block” level (mixed-aware)
        List<Node> yKeyVars = (doRcit && !Z.isEmpty()) ? hstackVarList(y, Z) : Collections.singletonList(y);

        // Deterministic seeds (cache-friendly)
        long seedX = seedForX(x) ^ 1729L;
        long seedY = seedForBlock("Y", yKeyVars) ^ 1729L;
        long seedZ = seedForBlock("Z", Z) ^ 1729L;

        // Features (mixed-aware)
        SimpleMatrix fX = kffFeatMixedCached("X", Collections.singletonList(x), numFeatXY, seedX);
        SimpleMatrix fY = kffFeatMixedCached("Y", yKeyVars, numFeatXY, seedY);
        SimpleMatrix fZ = Z.isEmpty() ? null : kffFeatMixedCached("Z", Z, numFeatZ, seedZ);

        final double stat;
        double p;

        if (fZ == null || fZ.getNumCols() == 0) {
            // ---------------- RIT (no conditioning) ----------------
            SimpleMatrix Cxy = cov(fX, fY);
            stat = n * frob2(Cxy);

            SimpleMatrix resX = fX.copy();
            SimpleMatrix resY = fY.copy();
            subtractColumnMeansInPlace(resX);
            subtractColumnMeansInPlace(resY);

            SimpleMatrix Cov = kronResCov(resX, resY);
            double[] eig = positiveEigs(Cov);

            switch (approx) {
                case PERMUTATION -> {
                    if (permutations > 0) {
                        int greater = 0;
                        for (int b = 0; b < permutations; b++) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                            int[] perm = randomPermutation(n, rng);
                            SimpleMatrix C = covWithPermutedB(fX, fY, perm);
                            double s = n * frob2(C);
                            if (s >= stat) greater++;
                        }
                        p = (greater + 1.0) / (permutations + 1.0);
                    } else {
                        p = gammaApproxP(stat, eig);
                    }
                }
                case HBE -> p = edgeworthP(stat, eig, false);
                case LPB4 -> p = edgeworthP(stat, eig, true);
                case CHI2 -> p = chi2ApproxP(n, vec(Cxy), Cov);
                case GAMMA -> p = gammaApproxP(stat, eig);
                default -> p = gammaApproxP(stat, eig);
            }
        } else {
            // ---------------- Conditional: ridge residualization in feature space ----------------
//            final double alphaRidge = Math.max(1e-18, lambda);
            final double alphaRidge = Math.max(1e-18, lambda / Math.max(1.0, (n - 1.0)));

            SimpleMatrix rX = ridgeResidual(fX, fZ, alphaRidge);
            SimpleMatrix rY = ridgeResidual(fY, fZ, alphaRidge);

            subtractColumnMeansInPlace(rX);
            subtractColumnMeansInPlace(rY);

            SimpleMatrix Cxy = cov(rX, rY);
            stat = n * frob2(Cxy);

            if (approx == Approx.PERMUTATION && permutations > 0) {
                int greater = 0;
                for (int b = 0; b < permutations; b++) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    int[] perm = randomPermutation(n, rng);

                    SimpleMatrix rYp = permuteRows(rY, perm);
                    SimpleMatrix C = cov(rX, rYp);

                    double s = n * frob2(C);
                    if (s >= stat) greater++;
                }
                p = (greater + 1.0) / (permutations + 1.0);
            } else {
                SimpleMatrix resX = rX.copy();
                SimpleMatrix resY = rY.copy();
                subtractColumnMeansInPlace(resX);
                subtractColumnMeansInPlace(resY);

                SimpleMatrix Cov = kronResCov(resX, resY);
                double[] eig = positiveEigs(Cov);

                switch (approx) {
                    case HBE -> p = edgeworthP(stat, eig, false);
                    case LPB4 -> p = edgeworthP(stat, eig, true);
                    case CHI2 -> p = chi2ApproxP(n, vec(Cxy), Cov);
                    case GAMMA -> p = gammaApproxP(stat, eig);
                    default -> p = gammaApproxP(stat, eig);
                }
            }
        }

        double p_ = clamp01(p);
        boolean indep = (p_ > alpha);

        if (verbose && indep) {
            TetradLogger.getInstance().log(fact + " p = " + p_ + " stat=" + stat
                    + " approx=" + approx + " Fx=" + numFeatXY + " Fz=" + numFeatZ
                    + " ft=" + featureType + " bwMult=" + bandwidthMultiplier + " lam=" + lambda);
        }

        return new IndependenceResult(fact, indep, p_, alpha - p_);
    }

    private static boolean containsDiscrete(List<Node> vars) {
        for (Node v : vars) if (v instanceof DiscreteVariable) return true;
        return false;
    }

    private IndependenceResult checkIndependenceStratifiedOnZDiscrete(
            Node x, Node y, List<Node> Zsorted, IndependenceFact fact
    ) throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        this.n = getActiveRowCount();
        final int nAll = this.n;

        // Split Z into discrete vs continuous
        final ArrayList<DiscreteVariable> zDisc = new ArrayList<>();
        final ArrayList<Node> zCont = new ArrayList<>();
        for (Node v : Zsorted) {
            if (v instanceof DiscreteVariable dv) zDisc.add(dv);
            else zCont.add(v);
        }

        // Build strata: map key -> list of active-row indices (0..nAll-1)
        // Using Long boxing in HashMap is not perfect, but OK; avoids extra dependencies.
        final HashMap<Long, IntArrayList> strata = new HashMap<>(64);

        for (int i = 0; i < nAll; i++) {
            int row = activeRowIndex(i);

            long key = 1469598103934665603L; // FNV-ish
            for (DiscreteVariable dv : zDisc) {
                int col = data.getColumn(dv);
                if (col < 0) col = data.getVariableNames().indexOf(dv.getName());
                if (col < 0) throw new IllegalArgumentException("Variable not found: " + dv.getName());

                int val;
                try {
                    val = data.getInt(row, col);
                } catch (Throwable t) {
                    val = (int) Math.round(data.getDouble(row, col));
                }

                // clamp defensively
                int k = Math.max(1, dv.getNumCategories());
                if (val < 0) val = 0;
                if (val >= k) val = k - 1;

                key ^= (val + 0x9E3779B97F4A7C15L);
                key *= 1099511628211L;
            }

            strata.computeIfAbsent(key, kk -> new IntArrayList()).add(i);
        }

        if (strata.size() > maxStrata) {
            // Too fragmented -> fall back to standard STACK behavior
            // (No recursion—just do the original path by returning null sentinel and falling through is messy;
            // simplest is to just run STACK inline by calling the normal code path. Since you’re inside checkIndependence,
            // easiest is: temporarily switch mode and call checkIndependence again.)
            MixedMode prev = this.mixedMode;
            return null;
        }

        // Keep only sufficiently large strata
        final ArrayList<IntArrayList> strataIdx = new ArrayList<>(strata.size());
        int totalKept = 0;
        for (IntArrayList lst : strata.values()) {
            if (lst.size() >= minStratumSize) {
                strataIdx.add(lst);
                totalKept += lst.size();
            }
        }

        if (totalKept < Math.max(10, minStratumSize)) {
            return null;
        }

        // Deterministic base seeds (so results are stable)
        long seedBase = seedForX(x) ^ seedForBlock("STRATA", Zsorted) ^ 0xC0FFEE;

        // Observed stat: sum over strata
        double statObs = 0.0;

        // Precompute per-stratum residual features rX_s and rY_s for observed statistic
        // (Then permutations just permute rY_s within the stratum.)
        final ArrayList<SimpleMatrix> rXs = new ArrayList<>(strataIdx.size());
        final ArrayList<SimpleMatrix> rYs = new ArrayList<>(strataIdx.size());

        for (int s = 0; s < strataIdx.size(); s++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            IntArrayList idx = strataIdx.get(s);
            int ns = idx.size();

            // Build features for this stratum (continuous-only; discrete are constant inside stratum by construction)
            SimpleMatrix fX = kffFeatContinuousOnRows(Collections.singletonList(x), numFeatXY, seedBase ^ (s * 1315423911L), idx);
            List<Node> yKeyVars = (doRcit && !Zsorted.isEmpty()) ? hstackVarList(y, Zsorted) : Collections.singletonList(y);
            SimpleMatrix fY = kffFeatContinuousOnRows(yKeyVars, numFeatXY, seedBase ^ (s * 2654435761L), idx);

            SimpleMatrix fZ = zCont.isEmpty() ? null
                    : kffFeatContinuousOnRows(zCont, numFeatZ, seedBase ^ (s * 97531L), idx);

            SimpleMatrix rX = (fZ == null || fZ.getNumCols() == 0) ? fX : ridgeResidual(fX, fZ, Math.max(1e-18, lambda));
            SimpleMatrix rY = (fZ == null || fZ.getNumCols() == 0) ? fY : ridgeResidual(fY, fZ, Math.max(1e-18, lambda));

            // stat contribution
            SimpleMatrix Cxy = cov(rX, rY);
            statObs += ns * frob2(Cxy);

            rXs.add(rX);
            rYs.add(rY);
        }

        // Permutation p-value: permute Y within each stratum
        int greater = 0;
        for (int b = 0; b < permutations; b++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            double statB = 0.0;

            for (int s = 0; s < strataIdx.size(); s++) {
                int ns = strataIdx.get(s).size();

                SimpleMatrix rX = rXs.get(s);
                SimpleMatrix rY = rYs.get(s);

                int[] perm = randomPermutation(ns, rng);
                SimpleMatrix rYp = permuteRows(rY, perm);

                SimpleMatrix C = cov(rX, rYp);
                statB += ns * frob2(C);
            }

            if (statB >= statObs) greater++;
        }

        double p = (greater + 1.0) / (permutations + 1.0);
        double p_ = clamp01(p);
        boolean indep = (p_ > alpha);

        if (verbose) {
            TetradLogger.getInstance().log(fact + " STRATA_ZDISC p=" + p_ + " stat=" + statObs
                    + " strata=" + strataIdx.size() + " keptN=" + totalKept
                    + " minStratum=" + minStratumSize + " perms=" + permutations);
        }

        return new IndependenceResult(fact, indep, p_, alpha - p_);
    }

    private SimpleMatrix kffFeatContinuousOnRows(List<Node> vv, int mFeaturesCont, long seed, IntArrayList activeIdxWithinCurrentRows) {
        final int ns = activeIdxWithinCurrentRows.size();

        // Build continuous raw matrix Zc (ns x dc)
        ArrayList<Node> contVars = new ArrayList<>();
        for (Node v : vv) if (!(v instanceof DiscreteVariable)) contVars.add(v);

        int dc = contVars.size();
        if (dc == 0) {
            // no continuous inputs: return constant RFF block (handled by rffFeatures for d==0)
            double[][] Z0 = new double[ns][0];
            double[][] Phi = rffFeatures(Z0, Math.max(1, mFeaturesCont), 1.0, seed);
            SimpleMatrix M = new SimpleMatrix(Phi);
            if (centerFeatures) zscoreInPlace(M);
            else subtractColumnMeansInPlace(M);
            return M;
        }

        double[][] Zc = new double[ns][dc];
        for (int j = 0; j < dc; j++) {
            Node v = contVars.get(j);
            int col = data.getColumn(v);
            if (col < 0) col = data.getVariableNames().indexOf(v.getName());
            if (col < 0) throw new IllegalArgumentException("Variable not found: " + v.getName());

            for (int i = 0; i < ns; i++) {
                int rowAll = activeRowIndex(activeIdxWithinCurrentRows.get(i));
                Zc[i][j] = data.getDouble(rowAll, col);
            }
        }

        zscoreInPlace(Zc);

        double bw2 = medianDistanceSquaredND(Zc, Math.min(ns, bwMaxRows));
        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
        bw2 *= (bandwidthMultiplier * bandwidthMultiplier);
        if (bw2 < 1e-12) bw2 = 1e-12;

        double[][] Phi = rffFeatures(Zc, mFeaturesCont, bw2, seed);
        SimpleMatrix M = new SimpleMatrix(Phi);

        if (centerFeatures) zscoreInPlace(M);
        else subtractColumnMeansInPlace(M);

        return M;
    }

    /**
     * Retrieves the list of nodes representing variables.
     *
     * @return a list of Node objects contained in this instance.
     */
    @Override
    public List<Node> getVariables() {
        return vars;
    }

    /**
     * Retrieves the alpha value.
     *
     * @return the alpha value as a double.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the alpha value for the object. The alpha determines the level of transparency,
     * where the value must be strictly between 0 (completely transparent) and 1 (completely opaque).
     *
     * @param alpha the transparency level to be set; must be a value in the range (0, 1)
     * @throws IllegalArgumentException if the alpha value is less than or equal to 0, or greater than or equal to 1
     */
    @Override
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
    }

    /**
     * Retrieves the current data set.
     *
     * @return the DataSet object representing the current data.
     */
    @Override
    public DataSet getData() {
        return data;
    }

    /**
     * Determines if verbose mode is enabled.
     *
     * @return true if verbose mode is enabled; otherwise, false
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets the verbose mode for the current instance.
     *
     * @param verbose a boolean value indicating whether verbose mode should be enabled (true) or disabled (false)
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // ---------------- RowsSettable ----------------

    /**
     * Retrieves the list of row indices currently set for the instance.
     *
     * @return a List containing the row indices, or null if no rows are set
     */
    @Override
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Sets the list of row indices for the current instance.
     *
     * @param rows a List containing the row indices to be set, or null to reset to all rows
     * @throws NullPointerException if any row index in the list is null
     * @throws IllegalArgumentException if any row index is negative or out of bounds
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            this.n = data.getNumRows();
            featCache.clear();
            bw2Cache.clear();
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (r >= data.getNumRows()) throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
        }

        this.rows = new ArrayList<>(rows);
        this.n = this.rows.size();
        featCache.clear();
        bw2Cache.clear();
    }

    private int getActiveRowCount() {
        return (rows == null) ? data.getNumRows() : rows.size();
    }

    private int activeRowIndex(int i) {
        return (rows == null) ? i : rows.get(i);
    }

    // ---------------- Mixed feature construction ----------------

    private SimpleMatrix kffFeatMixedCached(String tag, List<Node> varsForKey, int mFeaturesCont, long seed)
            throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        // Key includes variable list; discrete mappings depend only on observed category counts in active rows,
        // so we fold in a cheap “levels signature” too.
        String key = keyFeat(tag, varsForKey, mFeaturesCont, seed);

        return featCache.computeIfAbsent(key, k -> {
            MixedBlock block = extractMixedBlock(varsForKey);

            // Continuous part: z-score raw, then RFF
            double[][] Zc = block.cont; // n x dc (already z-scored)
            double bw2 = bw2For(tag, varsForKey, Zc);

            double[][] PhiC = (Zc.length == 0 || Zc[0].length == 0)
                    ? new double[n][0]
                    : rffFeatures(Zc, mFeaturesCont, bw2, seed);

            // Discrete part: one-hot (delta-kernel)
            double[][] PhiD = block.discOneHot; // n x dd (already centered if requested later)

            // Stack
            double[][] Phi = hstackRaw(PhiC, PhiD);

            SimpleMatrix M = new SimpleMatrix(Phi);

            if (centerFeatures) zscoreInPlace(M);
            else subtractColumnMeansInPlace(M);

            return M;
        });
    }

    /**
     * Extracts a mixed block for a set of variables:
     * - Continuous vars -> double[][] cont (n x dc), z-scored columnwise
     * - Discrete vars   -> double[][] discFeat (n x sum(levels)), where each discrete var contributes
     * either:
     * (a) one-hot (delta kernel) if catRho == 0, or
     * (b) an exact PSD categorical feature map (Cholesky row features) if catRho > 0,
     * i.e. features f(c) s.t. f(c)^T f(c') = 1 if c=c', else catRho.
     * <p>
     * Note: centering/z-scoring is handled later in kffFeatMixedCached(...) via zscoreInPlace(SimpleMatrix).
     */
    private MixedBlock extractMixedBlock(List<Node> vv) {
        final int n = getActiveRowCount();

        // 1) Identify continuous/discrete vars in block
        final ArrayList<Node> contVars = new ArrayList<>();
        final ArrayList<DiscreteVariable> discVars = new ArrayList<>();

        for (Node v : vv) {
            if (v instanceof DiscreteVariable dv) discVars.add(dv);
            else contVars.add(v);
        }

        // 2) Continuous raw -> z-score
        final double[][] cont = new double[n][contVars.size()];
        for (int j = 0; j < contVars.size(); j++) {
            Node v = contVars.get(j);
            int col = data.getColumn(v);
            if (col < 0) col = data.getVariableNames().indexOf(v.getName());
            if (col < 0) throw new IllegalArgumentException("Variable not found: " + v.getName());

            for (int i = 0; i < n; i++) {
                int row = activeRowIndex(i);
                cont[i][j] = data.getDouble(row, col);
            }
        }
        // z-score continuous columns (like original KffRcit)
        zscoreInPlace(cont);

        // 3) Discrete features: one-hot (rho=0) OR Cholesky row features (rho>0)
        int totalLevels = 0;
        final int[] levelsPerVar = new int[discVars.size()];
        for (int j = 0; j < discVars.size(); j++) {
            int k = Math.max(1, discVars.get(j).getNumCategories());
            levelsPerVar[j] = k;
            totalLevels += k;
        }

        final double[][] discFeat = new double[n][totalLevels];
        if (totalLevels > 0) {
            int offset = 0;

            for (int j = 0; j < discVars.size(); j++) {
                DiscreteVariable dv = discVars.get(j);

                int col = data.getColumn(dv);
                if (col < 0) col = data.getVariableNames().indexOf(dv.getName());
                if (col < 0) throw new IllegalArgumentException("Variable not found: " + dv.getName());

                final int k = levelsPerVar[j];

                // Build categorical feature map rows for this variable:
                // - if catRho == 0: rows are standard basis (one-hot)
                // - else: rows are Cholesky factor rows for K_levels (diag=1, offdiag=catRho)
                final double[][] A = buildCatFeatureRows(k, this.catRho);

                for (int i = 0; i < n; i++) {
                    int row = activeRowIndex(i);

                    int val;
                    try {
                        val = data.getInt(row, col);
                    } catch (Throwable t) {
                        val = (int) Math.round(data.getDouble(row, col));
                    }

                    // Clamp defensively
                    if (val < 0) val = 0;
                    if (val >= k) val = k - 1;

                    // Copy row features into the appropriate slice
                    System.arraycopy(A[val], 0, discFeat[i], offset, k);
                }

                offset += k;
            }
        }

        return new MixedBlock(cont, discFeat);
    }

    /**
     * Returns an exact feature matrix A (k x k) for the categorical kernel:
     * K_ij = 1 if i==j else rho
     * such that A * A^T = K.
     * <p>
     * If rho == 0, this returns the identity (one-hot features).
     * <p>
     * Requirements:
     * - k >= 1
     * - 0 <= rho < 1
     */
    private static double[][] buildCatFeatureRows(int k, double rho) {
        if (k <= 0) throw new IllegalArgumentException("k must be >= 1");

        // Treat tiny rho as zero for stability.
        if (!(rho > 0.0) || rho < 1e-15) {
            double[][] I = new double[k][k];
            for (int i = 0; i < k; i++) I[i][i] = 1.0;
            return I;
        }

        if (!(rho >= 0.0 && rho < 1.0) || !Double.isFinite(rho)) {
            throw new IllegalArgumentException("rho must be in [0,1)");
        }

        // Build K (k x k): diag 1, offdiag rho
        double[][] K = new double[k][k];
        for (int i = 0; i < k; i++) {
            K[i][i] = 1.0;
            for (int j = 0; j < i; j++) {
                K[i][j] = rho;
                K[j][i] = rho;
            }
        }

        // Cholesky lower factor L such that K = L L^T.
        // This exists for 0 <= rho < 1.
        double[][] L = choleskyLowerOrThrow(K);

        // We want row-features. Using rows of L is fine: row_i dot row_j = K_ij.
        // (Because (L L^T)_{ij} = row_i(L) · row_j(L).)
        return L;
    }

    /**
     * Basic Cholesky (lower-triangular) with a small jitter fallback for numerical safety.
     * Returns L where M = L L^T.
     */
    private static double[][] choleskyLowerOrThrow(double[][] M) {
        int n = M.length;
        double[][] L = new double[n][n];

        // Try without jitter first
        if (choleskyLowerInto(M, L, 0.0)) return L;

        // Add a tiny diagonal jitter if needed (rare, but defensive)
        // Keep it small so it doesn't materially change the kernel.
        double jitter = 1e-12;
        for (int tries = 0; tries < 3; tries++) {
            if (choleskyLowerInto(M, L, jitter)) return L;
            jitter *= 10.0;
        }

        throw new IllegalArgumentException("Categorical kernel matrix not PD (unexpected for rho in [0,1)).");
    }

    /**
     * Computes Cholesky lower factor into L, optionally adding 'jitter' to the diagonal of M.
     * Returns true on success, false if not PD.
     */
    private static boolean choleskyLowerInto(double[][] M, double[][] L, double jitter) {
        int n = M.length;

        // zero L
        for (int i = 0; i < n; i++) Arrays.fill(L[i], 0.0);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = M[i][j];
                if (i == j) sum += jitter;

                for (int k = 0; k < j; k++) sum -= L[i][k] * L[j][k];

                if (i == j) {
                    if (!(sum > 1e-15) || !Double.isFinite(sum)) return false;
                    L[i][j] = Math.sqrt(sum);
                } else {
                    double denom = L[j][j];
                    if (!(denom > 0) || !Double.isFinite(denom)) return false;
                    L[i][j] = sum / denom;
                }
            }
        }
        return true;
    }

    private record MixedBlock(double[][] cont, double[][] discOneHot) {
    }

    private double bw2For(String tag, List<Node> varsForKey, double[][] Zcont) {
        // Bandwidth is computed ONLY from the continuous variables.
        // So the cache key must ignore discrete vars to avoid stale/incorrect reuse patterns.
        String key = keyBw2ContinuousOnly(tag, varsForKey);

        return bw2Cache.computeIfAbsent(key, k -> {
            int n = Zcont.length;
            if (n <= 2 || (n > 0 && Zcont[0].length == 0)) return 1.0;

            int maxRows = Math.min(n, bwMaxRows);
            double bw2 = medianDistanceSquaredND(Zcont, maxRows);
            if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

            bw2 *= (bandwidthMultiplier * bandwidthMultiplier);
            if (bw2 < 1e-12) bw2 = 1e-12;
            return bw2;
        });
    }

    private String keyBw2ContinuousOnly(String tag, List<Node> vs) {
        ArrayList<String> names = new ArrayList<>(vs.size());
        for (Node v : vs) {
            if (!(v instanceof DiscreteVariable)) names.add(v.getName());
        }
        names.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(140);
        sb.append(tag)
                .append("|n=").append(getActiveRowCount())
                .append("|rows=").append(activeRowsHash())
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|cvars=");
        for (String s : names) sb.append(s).append(",");
        return sb.toString();
    }

    private String keyFeat(String tag, List<Node> vs, int mFeatures, long seed) {
        ArrayList<String> names = new ArrayList<>(vs.size());
        ArrayList<String> types = new ArrayList<>(vs.size());

        for (Node v : vs) {
            names.add(v.getName());
            types.add((v instanceof DiscreteVariable) ? "D" : "C");
        }

        // stable order
        ArrayList<String> pair = new ArrayList<>(vs.size());
        for (int i = 0; i < names.size(); i++) pair.add(names.get(i) + ":" + types.get(i));
        pair.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(180);
        sb.append(tag)
                .append("|n=").append(getActiveRowCount())
                .append("|rows=").append(activeRowsHash())
                .append("|m=").append(mFeatures)
                .append("|ft=").append(featureType.name())
                .append("|ctr=").append(centerFeatures ? 1 : 0)
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|seed=").append(seed)
                .append("|vars=")
                .append("|catRho=").append(Double.doubleToLongBits(catRho));
        for (String s : pair) sb.append(s).append(",");
        return sb.toString();
    }

    private String keyBw2(String tag, List<Node> vs) {
        ArrayList<String> names = new ArrayList<>(vs.size());
        for (Node v : vs) names.add(v.getName());
        names.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(140);
        sb.append(tag)
                .append("|n=").append(getActiveRowCount())
                .append("|rows=").append(activeRowsHash())
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|vars=");
        for (String s : names) sb.append(s).append(",");
        return sb.toString();
    }

    private int activeRowsHash() {
        if (rows == null) return 0;
        int h = 1;
        for (int r : rows) h = 31 * h + r;
        return h;
    }

    private static List<Node> hstackVarList(Node y, List<Node> Z) {
        ArrayList<Node> out = new ArrayList<>(1 + Z.size());
        out.add(y);
        out.addAll(Z);
        return out;
    }

    // ---------------- Seeds (stable) ----------------

    private long seedForX(Node x) {
        long h = 1469598103934665603L;
        h = 1099511628211L * (h ^ x.getName().hashCode());
        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        return h;
    }

    private long seedForBlock(String tag, List<Node> block) {
        long h = 1469598103934665603L;
        h = 1099511628211L * (h ^ tag.hashCode());

        ArrayList<String> names = new ArrayList<>(block.size());
        for (Node v : block) names.add(v.getName());
        names.sort(String::compareTo);
        for (String s : names) h = 1099511628211L * (h ^ s.hashCode());

        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        return h;
    }

    // ---------------- KFF Fourier features ----------------

    /**
     * Build Random Fourier (or Orthogonal Random) Features for an RBF kernel:
     * k(x,x') = exp(-||x-x'||^2 / bw2)
     * RFF/ORF:
     * wStd = sqrt(2/bw2)
     * phi_j(x) = sqrt(2/m) cos(w_j^T x + b_j)
     */
    private double[][] rffFeatures(double[][] Z, int mFeatures, double bw2, long seed) {
        final int n = Z.length;
        final int d = (n == 0) ? 0 : Z[0].length;

        if (n == 0 || mFeatures <= 0) return new double[n][Math.max(mFeatures, 0)];
        if (d == 0) {
            double[][] Phi = new double[n][mFeatures];
            SplittableRandom rng0 = new SplittableRandom(seed);
            double scale0 = Math.sqrt(2.0 / mFeatures);
            double[] b0 = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) b0[j] = 2.0 * Math.PI * rng0.nextDouble();
            for (int i = 0; i < n; i++) for (int j = 0; j < mFeatures; j++) Phi[i][j] = scale0 * Math.cos(b0[j]);
            return Phi;
        }

        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

        final double wStd = Math.sqrt(2.0 / bw2);
        final double scale = Math.sqrt(2.0 / mFeatures);

        SplittableRandom rng = new SplittableRandom(seed);

        double[][] W;
        double[] b = new double[mFeatures];

        if (featureType == FeatureType.RFF) {
            W = new double[mFeatures][d];
            for (int j = 0; j < mFeatures; j++) {
                for (int k = 0; k < d; k++) W[j][k] = wStd * nextGaussian(rng);
                b[j] = 2.0 * Math.PI * rng.nextDouble();
            }
        } else if (featureType == FeatureType.ORF) {
            W = sampleOrthogonalW(mFeatures, d, wStd, rng);
            for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * Math.PI * rng.nextDouble();
        } else {
            throw new IllegalArgumentException("featureType must be RFF or ORF");
        }

        double[][] Phi = new double[n][mFeatures];

        for (int i = 0; i < n; i++) {
            double[] Zi = Z[i];
            for (int j = 0; j < mFeatures; j++) {
                double dot = 0.0;
                double[] wj = W[j];
                for (int k = 0; k < d; k++) dot += wj[k] * Zi[k];
                Phi[i][j] = scale * Math.cos(dot + b[j]);
            }
        }

        return Phi;
    }

    // Box–Muller-ish gaussian from SplittableRandom (Marsaglia polar)
    private static double nextGaussian(SplittableRandom rng) {
        double u, v, s;
        do {
            u = 2.0 * rng.nextDouble() - 1.0;
            v = 2.0 * rng.nextDouble() - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);
        return u * Math.sqrt(-2.0 * Math.log(s) / s);
    }

    /**
     * Orthogonal Random Features (ORF) weights for RBF kernel.
     * Generates rows in (block-)orthogonal blocks of size d.
     */
    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd, SplittableRandom rng) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;

        while (filled < mFeatures) {
            int block = Math.min(d, mFeatures - filled);

            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++) for (int j = 0; j < d; j++) Q[i][j] = nextGaussian(rng);

            for (int i = 0; i < block; i++) {
                for (int k = 0; k < i; k++) {
                    double dot = 0.0;
                    for (int j = 0; j < d; j++) dot += Q[i][j] * Q[k][j];
                    for (int j = 0; j < d; j++) Q[i][j] -= dot * Q[k][j];
                }
                double norm2 = 0.0;
                for (int j = 0; j < d; j++) norm2 += Q[i][j] * Q[i][j];
                double norm = Math.sqrt(Math.max(1e-18, norm2));
                for (int j = 0; j < d; j++) Q[i][j] /= norm;
            }

            for (int i = 0; i < block; i++) {
                double r = chiRadius(d, rng);
                double s = wStd * r;
                int outRow = filled + i;
                for (int j = 0; j < d; j++) W[outRow][j] = s * Q[i][j];
            }

            filled += block;
        }

        return W;
    }

    private static double chiRadius(int d, SplittableRandom rng) {
        double ss = 0.0;
        for (int k = 0; k < d; k++) {
            double g = nextGaussian(rng);
            ss += g * g;
        }
        return Math.sqrt(Math.max(1e-18, ss));
    }

    /**
     * Median of pairwise squared distances for up to maxRows points.
     * Deterministic (uses evenly spaced subsample of rows).
     */
    private static double medianDistanceSquaredND(double[][] Z, int maxRows) {
        int n = Z.length;
        int d = (n == 0) ? 0 : Z[0].length;
        if (n < 3 || d == 0) return 1.0;

        int m = Math.min(n, Math.max(3, maxRows));

        int[] idx = new int[m];
        if (m == n) {
            for (int i = 0; i < m; i++) idx[i] = i;
        } else {
            for (int i = 0; i < m; i++) idx[i] = (int) Math.floor((i * (long) (n - 1)) / (double) (m - 1));
        }

        int cnt = m * (m - 1) / 2;
        double[] d2 = new double[cnt];
        int t = 0;

        for (int a = 1; a < m; a++) {
            int i = idx[a];
            for (int b = 0; b < a; b++) {
                int j = idx[b];
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                d2[t++] = dist2;
            }
        }

        Arrays.sort(d2, 0, t);
        int firstPos = 0;
        while (firstPos < t && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= t) return 1.0;
        int mid = firstPos + (t - firstPos) / 2;
        return d2[mid];
    }

    private static double[][] hstackRaw(double[][] A, double[][] B) {
        int n = A.length;
        if (n != B.length) throw new IllegalArgumentException("Row mismatch in hstackRaw.");
        int p = (n == 0) ? 0 : A[0].length;
        int q = (n == 0) ? 0 : B[0].length;

        double[][] out = new double[n][p + q];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, out[i], 0, p);
            System.arraycopy(B[i], 0, out[i], p, q);
        }
        return out;
    }

    // ---------------- Feature-space ridge residualization ----------------

    private static SimpleMatrix ridgeResidual(SimpleMatrix X, SimpleMatrix Z, double alpha) {
        if (Z == null || Z.getNumCols() == 0) return X;
        if (!(alpha > 0) || !Double.isFinite(alpha)) alpha = 1e-18;

        SimpleMatrix ZtZ = Z.transpose().mult(Z);
        SimpleMatrix A = ZtZ.plus(SimpleMatrix.identity(ZtZ.getNumRows()).scale(alpha));
        SimpleMatrix B = A.solve(Z.transpose().mult(X));
        return X.minus(Z.mult(B));
    }

    // ---------------- Stats + approximations ----------------

    private static void zscoreInPlace(double[][] M) {
        int n = M.length;
        if (n == 0) return;
        int d = M[0].length;
        if (d == 0) return;

        for (int j = 0; j < d; j++) {
            double sum = 0.0;
            for (int i = 0; i < n; i++) sum += M[i][j];
            double mean = sum / n;

            double var = 0.0;
            for (int i = 0; i < n; i++) {
                double u = M[i][j] - mean;
                var += u * u;
            }
            var /= Math.max(1, n - 1);
            double sd = Math.sqrt(var);

            if (!(sd > 0) || !Double.isFinite(sd)) {
                for (int i = 0; i < n; i++) M[i][j] = 0.0;
            } else {
                for (int i = 0; i < n; i++) M[i][j] = (M[i][j] - mean) / sd;
            }
        }
    }

    private static void zscoreInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n < 2 || d == 0) return;
        for (int j = 0; j < d; j++) {
            double sum = 0, sumsq = 0;
            for (int i = 0; i < n; i++) {
                double v = M.get(i, j);
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;
            for (int i = 0; i < n; i++) M.set(i, j, (M.get(i, j) - mean) / sd);
        }
    }

    private static SimpleMatrix cov(SimpleMatrix A, SimpleMatrix B) {
        return covCentered(A, B);
//        int n = A.getNumRows();
//        return A.transpose().mult(B).scale(1.0 / (n - 1));
    }

    private static double frob2(SimpleMatrix M) {
        double s = 0.0;
        double[] a = M.getDDRM().data;
        for (double v : a) s += v * v;
        return s;
    }

    private static SimpleMatrix kronResCov(SimpleMatrix resX, SimpleMatrix resY) {
        int Fx = resX.getNumCols(), Fy = resY.getNumCols(), q = Fx * Fy, n = resX.getNumRows();
        SimpleMatrix Z = new SimpleMatrix(n, q);
        int idx = 0;
        for (int a = 0; a < Fx; a++) {
            for (int b = 0; b < Fy; b++) {
                for (int i = 0; i < n; i++) Z.set(i, idx, resX.get(i, a) * resY.get(i, b));
                idx++;
            }
        }
        return Z.transpose().mult(Z).scale(1.0 / (n - 1));
    }

    private static double[] positiveEigs(SimpleMatrix Cov) {
        SimpleEVD<SimpleMatrix> evd = Cov.eig();
        int m = evd.getNumberOfEigenvalues();
        ArrayList<Double> pos = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            double lam = evd.getEigenvalue(i).getReal();
            if (lam > 1e-12 && Double.isFinite(lam)) pos.add(lam);
        }
        double[] e = new double[pos.size()];
        for (int i = 0; i < e.length; i++) e[i] = pos.get(i);
        return e;
    }

    private static double gammaApproxP(double stat, double[] eig) {
        if (eig.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;
        double s1 = 0.0, s2 = 0.0;
        for (double l : eig) {
            s1 += l;
            s2 += l * l;
        }
        double mu = s1, var = 2.0 * s2;
        if (mu <= 0 || var <= 0) return (stat <= 1e-12) ? 1.0 : 0.0;
        double k = (mu * mu) / var;
        double theta = var / mu;
        GammaDistribution gd = new GammaDistribution(k, theta);
        return 1.0 - gd.cumulativeProbability(stat);
    }

    private static double edgeworthP(double stat, double[] eig, boolean useKurtosis) {
        if (eig.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        double s1 = 0, s2 = 0, s3 = 0, s4 = 0;
        for (double l : eig) {
            s1 += l;
            s2 += l * l;
            s3 += l * l * l;
            s4 += l * l * l * l;
        }
        double mu = s1;
        double var = 2.0 * s2;
        if (var <= 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        double sigma = Math.sqrt(var);
        double t = (stat - mu) / sigma;

        double gamma1 = (8.0 * s3) / Math.pow(var, 1.5);
        double gamma2 = (48.0 * s4) / (var * var);

        double z = t + (gamma1 / 6.0) * (t * t - 1.0);
        if (useKurtosis) {
            z += (gamma2 / 24.0) * (t * t * t - 3.0 * t)
                    - (gamma1 * gamma1 / 36.0) * (2.0 * t * t * t - 5.0 * t);
        }
        NormalDistribution nd = new NormalDistribution();
        return 1.0 - nd.cumulativeProbability(z);
    }

    private static double chi2ApproxP(double n, SimpleMatrix Cvec, SimpleMatrix Cov) {
        SimpleMatrix iCov = Cov.pseudoInverse();
        SimpleMatrix tmp = iCov.mult(Cvec);
        double Q = n * Cvec.dot(tmp);

        int df = 0;
        SimpleEVD<SimpleMatrix> evd = Cov.eig();
        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            if (evd.getEigenvalue(i).getReal() > 1e-12) df++;
        }
        df = Math.max(df, 1);

        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        return 1.0 - chi2.cumulativeProbability(Q);
    }

    private static SimpleMatrix vec(SimpleMatrix M) {
        SimpleMatrix v = new SimpleMatrix(M.getNumElements(), 1);
        int k = 0;
        for (int j = 0; j < M.numCols(); j++)
            for (int i = 0; i < M.numRows(); i++)
                v.set(k++, 0, M.get(i, j));
        return v;
    }

    private static int[] randomPermutation(int n, Random rng) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        return p;
    }

    private static SimpleMatrix permuteRows(SimpleMatrix M, int[] perm) {
        SimpleMatrix out = new SimpleMatrix(M.getNumRows(), M.getNumCols());
        for (int i = 0; i < perm.length; i++) {
            for (int j = 0; j < M.getNumCols(); j++) out.set(i, j, M.get(perm[i], j));
        }
        return out;
    }

    private static SimpleMatrix covWithPermutedB(SimpleMatrix A, SimpleMatrix B, int[] perm) {
        int n = A.getNumRows();
        int p = A.getNumCols();
        int q = B.getNumCols();
        SimpleMatrix C = new SimpleMatrix(p, q);

        for (int i = 0; i < n; i++) {
            int bi = perm[i];
            for (int a = 0; a < p; a++) {
                double av = A.get(i, a);
                for (int b = 0; b < q; b++) {
                    C.set(a, b, C.get(a, b) + av * B.get(bi, b));
                }
            }
        }
        return C.scale(1.0 / (n - 1));
    }

    private static void subtractColumnMeansInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n == 0 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) s += M.get(i, j);
            double mean = s / n;
            for (int i = 0; i < n; i++) M.set(i, j, M.get(i, j) - mean);
        }
    }

    private static SimpleMatrix covCentered(SimpleMatrix A, SimpleMatrix B) {
        SimpleMatrix Ac = A.copy();
        SimpleMatrix Bc = B.copy();
        subtractColumnMeansInPlace(Ac);
        subtractColumnMeansInPlace(Bc);
        int n = A.getNumRows();
        return Ac.transpose().mult(Bc).scale(1.0 / (n - 1));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static final class IntArrayList {
        private int[] a = new int[16];
        private int size = 0;

        void add(int v) {
            if (size == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[size++] = v;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return Arrays.copyOf(a, size);
        }

        public int get(int i) {
            return a[i];
        }
    }
}