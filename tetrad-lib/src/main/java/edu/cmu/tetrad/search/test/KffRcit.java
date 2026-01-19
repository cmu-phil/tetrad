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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
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
 * <p>RCIT-KFF: Randomized Conditional Independence Test with KFF-ML-consistent Fourier features</p>
 *
 * <p>
 * This class implements a fast, kernel-based conditional independence test intended as a practical
 * alternative to {@code IndTestKci} when runtime is the dominant constraint. Empirically, KCI often
 * provides excellent accuracy but can be too slow for routine use inside constraint-based searches
 * (e.g., PC/PC-Max), especially at moderate-to-large sample sizes and repeated CI queries.
 * </p>
 *
 * <p>
 * <b>Core idea.</b> RCIT-KFF follows the randomized-approximation strategy of RCIT/RCoT
 * (Strobl, Zhang, Visweswaran, 2019) but replaces the standard random Fourier feature construction
 * with the same Fourier-feature kernel approximation used by the KFF-ML score
 * ({@code KffMarginalLikelihoodScore}). The goal is to make the CI test and the score a coherent
 * pair: they rely on the same kernel approximation machinery, reducing “test/score mismatch” in
 * hybrid workflows.
 * </p>
 *
 * <p>Hypotheses</p>
 * <ul>
 *   <li>{@code H0}: {@code X ⟂ Y | Z}</li>
 *   <li>{@code H1}: {@code X ⟂̸ Y | Z}</li>
 * </ul>
 *
 * <p>Feature maps (KFF-style)</p>
 * <p>
 * Each variable block is mapped to a fixed-dimensional feature representation using randomized
 * Fourier features that approximate an RBF (Gaussian) kernel. Two feature types are supported:
 * </p>
 * <ul>
 *   <li><b>RFF</b>: standard Random Fourier Features.</li>
 *   <li><b>ORF</b>: Orthogonal Random Features (block-orthogonal directions), often improving stability.</li>
 * </ul>
 *
 * <p>
 * Bandwidths are chosen by a median pairwise distance heuristic (on a subsample for speed),
 * and random-feature generation is seeded deterministically from the variable set (and the active
 * row subset) so repeated calls are reproducible and cacheable.
 * </p>
 *
 * <p>Test statistic (RCIT / RCoT style)</p>
 * <p>
 * Let {@code fX}, {@code fY}, {@code fZ} denote feature matrices for {@code X}, {@code Y} (or an
 * augmented {@code Y} block), and {@code Z}. The statistic is based on the squared Frobenius norm of the
 * (conditional) cross-covariance in feature space:
 * </p>
 * <ul>
 *   <li><b>RIT</b> (no conditioning): {@code T = n ||Cov(fX, fY)||_F^2}</li>
 *   <li><b>RCIT/RCoT</b> (with conditioning): {@code T = n || Cov(fX,fY) - Cov(fX,fZ)(Cov(fZ,fZ)+λI)^{-1}Cov(fZ,fY) ||_F^2}</li>
 * </ul>
 *
 * <p>
 * The ridge parameter {@code λ} stabilizes the inversion of {@code Cov(fZ,fZ)}.
 * </p>
 *
 * <p>P-value approximations</p>
 * <p>
 * Under the null, the statistic is approximated as a weighted sum of chi-square variables whose
 * weights are obtained from the eigenvalues of a covariance matrix formed from elementwise products
 * of residual feature matrices. This implementation supports the same family of approximations commonly
 * used in RCIT:
 * </p>
 * <ul>
 *   <li><b>GAMMA</b>: Satterthwaite–Welch moment-matched Gamma approximation.</li>
 *   <li><b>HBE</b>: Cornish–Fisher/Edgeworth correction using skewness.</li>
 *   <li><b>LPB4</b>: Edgeworth correction using skewness and kurtosis.</li>
 *   <li><b>CHI2</b>: chi-square approximation based on pseudo-inverse weighting.</li>
 *   <li><b>PERMUTATION</b>: optional permutation p-values (slow, but robust).</li>
 * </ul>
 *
 * <p>Practical notes</p>
 * <ul>
 *   <li>This test is intended for continuous variables; input columns are standardized internally.</li>
 *   <li>Caching is used aggressively (features, bandwidths, and Cholesky factors) to reduce repeated work
 *       in search procedures.</li>
 *   <li>As with other randomized-feature CI tests, p-values are approximate and depend on the
 *       feature dimension and bandwidth heuristic; they are best interpreted as a fast screening tool.</li>
 * </ul>
 *
 * <p>References</p>
 * <ul>
 *   <li>Strobl, E. V., Zhang, K., &amp; Visweswaran, S. (2019). Approximate kernel-based conditional independence tests
 *       for fast non-parametric causal discovery. <i>Journal of Causal Inference</i>, 7(1).</li>
 * </ul>
 */
public final class KffRcit implements IndependenceTest, RowsSettable {

    /**
     * An enumeration representing the types of feature generation methods
     * used in machine learning for kernel approximation tasks.
     */
    public enum FeatureType {

        /**
         * Represents the Random Fourier Features (RFF) method used for kernel
         * approximation in machine learning tasks. This method is commonly employed
         * to approximate shift-invariant kernels, such as the Gaussian kernel, by
         * mapping input features into a lower-dimensional randomized feature space.
         */
        RFF,

        /**
         * Represents the Orthogonal Random Features (ORF) method used for kernel
         * approximation in machine learning tasks. This method builds on Random Fourier
         * Features (RFF) by enforcing orthogonality in the randomized feature space,
         * potentially leading to improved approximation quality and numerical stability
         * compared to standard RFF.
         */
        ORF
    }

    // ---------------- core data ----------------
    private final DataSet data;
    private final List<Node> vars;
    private final Random rng;

    // Active rows state
    private List<Integer> rows = null;
    private int n;

    // ---------------- hyperparams ----------------
    private int numFeatXY = 10;       // features for X and Y (default aligns with causal-learn-ish defaults)
    private int numFeatZ = 100;     // features for Z

    private Approx approx = Approx.GAMMA;
    private int permutations = 0;    // only if approx == PERMUTATION
    private boolean doRcit = true;   // true => RCIT (augment Y with Z), false => RCoT

    /**
     * Ridge strength for feature-space residualization.
     * Internally used as alpha = lambda * (n-1) unless you change the mapping.
     */
    private double lambda = 1e-10;

    /**
     * If true: z-score the features columnwise (ddof=1) after Fourier mapping.
     * If false: only subtract feature column means.
     */
    private boolean centerFeatures = true;

    /** Bandwidth multiplier applied to bw2 (squared dist median). */
    private double bandwidthMultiplier = 1.0;

    /** Maximum rows used to compute median squared distance for bandwidth (deterministic subsample). */
    private int bwMaxRows = 500;

    /** Feature type. */
    private FeatureType featureType = FeatureType.RFF;

    // ---------------- IndependenceTest state ----------------
    private double alpha = 0.05;
    private boolean verbose = false;

    // --------- caches ----------
    private final Map<String, SimpleMatrix> featCache = new ConcurrentHashMap<>();
    private final Map<String, Double> bw2Cache = new ConcurrentHashMap<>();

    // ---------------- ctor ----------------

    /**
     * Constructs a new instance of the KffRcit class using the specified DataSet and default parameters.
     *
     * @param dataSet The DataSet to be used for the KffRcit instance. This dataset typically contains the
     *                data needed for independence testing and feature construction.
     */
    public KffRcit(DataSet dataSet) {
        this(dataSet, new Parameters());
    }

    /**
     * Constructs a new instance of the KffRcit class using the specified DataSet and Parameters.
     *
     * @param dataSet The DataSet to be used for the KffRcit instance. This DataSet typically contains
     *                the data required for kernel-based independence testing and feature construction.
     *                It must not be null.
     * @param params  The Parameters object that provides configuration options for the instance,
     *                such as the number of features, random seed, and specific algorithm settings.
     *                It must not be null.
     */
    public KffRcit(DataSet dataSet, Parameters params) {
        this.data = Objects.requireNonNull(dataSet, "data");
        this.vars = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));
        this.n = getActiveRowCount();

        long seed = params.getLong("rcit.seed", 1729L);
        this.rng = new Random(seed);

        // legacy-ish keys (do not override later explicit setter calls)
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
            default -> 3; // gamma
        });

        // Optional: allow these if passed
        this.bandwidthMultiplier = params.getDouble("rcit.bwMult", this.bandwidthMultiplier);
        this.bwMaxRows = Math.max(50, params.getInt("rcit.bwMaxRows", this.bwMaxRows));

        String ft = params.getString("rcit.featureType", "orf").toLowerCase(Locale.ROOT);
        if (ft.equals("rff")) this.featureType = FeatureType.RFF;
        if (ft.equals("orf")) this.featureType = FeatureType.ORF;
    }

    // ---------------- public setters ----------------

    /**
     * Sets the approximation method used for statistical calculations based on an integer code.
     * This method maps the provided integer code to a specific {@code Approx} enumeration value.
     * If the code does not match any defined approximation type, the default value {@code Approx.GAMMA} is used.
     *
     * @param approxCode An integer code representing the desired approximation method:
     *                   1 -> {@code Approx.LPB4},
     *                   2 -> {@code Approx.HBE},
     *                   3 -> {@code Approx.GAMMA},
     *                   4 -> {@code Approx.CHI2},
     *                   5 -> {@code Approx.PERMUTATION}.
     *                   Any other value defaults to {@code Approx.GAMMA}.
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
    }

    /**
     * Sets whether to perform Randomized Conditional Independence Testing (RCIT).
     *
     * @param doRcit A boolean value indicating whether RCIT should be enabled.
     *               If true, RCIT is performed; otherwise, RCoT is used.
     */
    public void setDoRcit(boolean doRcit) {
        this.doRcit = doRcit;
    }

    /**
     * Sets the value of the lambda parameter for the KffRcit instance.
     * The lambda parameter is a regularization term used to ensure numerical
     * stability. If the provided value of lambda is less than 1e-12, it will
     * be clamped to 1e-12. Default is 1.
     *
     * @param lambda The value to set for the lambda parameter. Must be a positive
     *               double value to ensure proper regularization. Values lower
     *               than 1e-12 will be automatically adjusted to 1e-12.
     */
    public void setLambda(double lambda) {
        this.lambda = Math.max(1e-12, lambda);
    }

    /**
     * Sets the number of permutations to be used in statistical testing or feature generation.
     * The value is clamped to ensure it is non-negative.
     *
     * @param permutations The desired number of permutations.
     *                      If the input value is negative, it will automatically be set to 0.
     */
    public void setPermutations(int permutations) {
        this.permutations = Math.max(0, permutations);
    }

    /**
     * Enables or disables the centering of features for the KffRcit instance.
     * Centering is typically used to preprocess feature data by subtracting the
     * mean value from each feature dimension, ensuring zero mean for the features.
     * Default is true.
     *
     * @param centerFeatures A boolean value indicating whether feature centering
     *                       should be applied. If true, features will be centered;
     *                       if false, centering will be skipped.
     */
    public void setCenterFeatures(boolean centerFeatures) {
        this.centerFeatures = centerFeatures;
    }

    /**
     * Sets the number of features to be used for the X and Y variables.
     * The value of the number of features is clamped to ensure it is at least 1.
     * This parameter determines the dimensionality of random features generated
     * for testing or feature representation tasks within the KffRcit framework.
     * Default is 10.
     *
     * @param d The desired number of features for the X and Y variables.
     *          If the input value is less than 1, it will be automatically
     *          adjusted to 1 to ensure validity.
     */
    public void setNumFeaturesXY(int d) {
        this.numFeatXY = Math.max(1, d);
    }

    /**
     * Sets the number of features for the Z dimension. Ensures that the value
     * is at least 1 by applying a minimum bound of 1.
     * Default is 100.
     *
     * @param d the desired number of features for the Z dimension
     */
    public void setNumFeaturesZ(int d) {
        this.numFeatZ = Math.max(1, d);
    }

    /**
     * Sets the bandwidth multiplier for the current instance. This value must be greater than 0 and finite.
     * The method also clears the cached data related to bandwidth and feature computations to ensure consistency.
     * Default is 1.0.
     *
     * @param bandwidthMultiplier the multiplier to adjust the bandwidth, must be a positive and finite value
     * @throws IllegalArgumentException if the provided bandwidthMultiplier is not greater than 0 or is not finite
     */
    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0 and finite");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        // invalidate bw2 cache (conservative)
        this.bw2Cache.clear();
        this.featCache.clear();
    }

    /**
     * Sets the maximum number of rows for the bw (bandwidth) configuration. The provided
     * value will be clamped to a minimum of 50. This method also clears the associated
     * caches to ensure consistency with the updated maximum rows setting.
     * Default is 500.
     *
     * @param bwMaxRows The desired maximum number of rows. Values less than 50 will be
     *                  automatically adjusted to 50.
     */
    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = Math.max(50, bwMaxRows);
        this.bw2Cache.clear();
        this.featCache.clear();
    }

    /**
     * Sets the feature type for this instance and clears the feature cache.
     * Default is RFF.
     *
     * @param featureType the feature type to be set; must not be null
     * @throws NullPointerException if the provided featureType is null
     */
    public void setFeatureType(FeatureType featureType) {
        this.featureType = Objects.requireNonNull(featureType, "featureType");
        this.featCache.clear();
    }

    /**
     * Retrieves the feature type associated with this instance.
     *
     * @return the feature type of the current instance
     */
    public FeatureType getFeatureType() {
        return featureType;
    }

    /**
     * Sets the seed for the random number generator and clears the feature cache.
     *
     * @param seed the seed value to initialize the random number generator
     */
    public void setSeed(long seed) {
        this.rng.setSeed(seed);
        this.featCache.clear();
    }

    // ---------------- IndependenceTest interface ----------------

    /**
     * Determines whether two nodes, {@code x} and {@code y}, are independent
     * given a set of conditioning nodes {@code z}, using statistical tests
     * and feature space projections. The method can operate under different
     * approximation schemes, handles permutations if specified, and uses
     * feature-based approaches for dimensionality reduction and residualization.
     *
     * @param x The first node whose independence with respect to {@code y} is being tested.
     *          Must not be null.
     * @param y The second node whose independence with respect to {@code x} is being tested.
     *          Must not be null.
     * @param z The set of nodes (conditioning set) to be conditioned on during the independence test.
     *          Can be empty or null if no conditioning is needed.
     * @return An {@code IndependenceResult} object containing the details of the independence test, including
     *         whether the nodes are deemed independent, the computed p-value, and statistical measures.
     * @throws InterruptedException If the thread executing the method is interrupted during computation.
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

        if (x.equals(y)) {
            if (verbose) TetradLogger.getInstance().log(fact + " x == y");
            double p_ = 0.0;
            return new IndependenceResult(fact, false, p_, alpha - p_, false);
        }
        if (n < 5) {
            if (verbose) TetradLogger.getInstance().log(fact + " n < 5");
            double p_ = 1.0;
            return new IndependenceResult(fact, true, p_, alpha - p_, false);
        }

        // --- raw data matrices as double[][] (active rows only), then z-score columns ---
        double[][] Xraw = rawMatrix(Collections.singletonList(x));
        double[][] Yraw = rawMatrix(Collections.singletonList(y));
        double[][] Zraw = Z.isEmpty() ? new double[n][0] : rawMatrix(Z);

        zscoreInPlace(Xraw);
        zscoreInPlace(Yraw);
        if (!Z.isEmpty()) zscoreInPlace(Zraw);

        // RCIT: augment Y with Z in the *input space* before features
        double[][] YaugRaw = (doRcit && !Z.isEmpty()) ? hstackRaw(Yraw, Zraw) : Yraw;

        // Deterministic seeds (cache-friendly)
//        long seedX = seedForX(x) ^ 1729L;
//        long seedY = seedForY(y, Z, doRcit) ^ 1729L;
//        long seedZ = seedForZ(Z) ^ 1729L;

        long seedX = seedForX(x) ^ 1729L;

        List<Node> yKeyVars = (doRcit && !Z.isEmpty()) ? hstackVarList(y, Z) : Collections.singletonList(y);

        long seedY = seedForBlock("Y", yKeyVars) ^ 1729L;
        long seedZ = seedForBlock("Z", Z) ^ 1729L;

        // Features
        SimpleMatrix fX = kffFeatCached("X", Collections.singletonList(x), Xraw, numFeatXY, seedX);
//        List<Node> yKeyVars = (doRcit && !Z.isEmpty()) ? hstackVarList(y, Z) : Collections.singletonList(y);
        SimpleMatrix fY = kffFeatCached("Y", yKeyVars, YaugRaw, numFeatXY, seedY);

        SimpleMatrix fZ = Z.isEmpty() ? null : kffFeatCached("Z", Z, Zraw, numFeatZ, seedZ);

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
            // ---------------- Conditional: KFF-ML style ridge residualization in feature space ----------------

            // alphaRidge controls how hard we project out Z-features.
            // Mapping: alphaRidge = lambda*(n-1) is a sane default given cov normalization / ridge semantics.
            final double alphaRidge = Math.max(1e-18, lambda);// * ((n - 1) / (double) n));

            // Residualize
            SimpleMatrix rX = ridgeResidual(fX, fZ, alphaRidge);
            SimpleMatrix rY = ridgeResidual(fY, fZ, alphaRidge);

            SimpleMatrix Cxy = cov(rX, rY);
            stat = n * frob2(Cxy);

            if (approx == Approx.PERMUTATION && permutations > 0) {
                int greater = 0;
                for (int b = 0; b < permutations; b++) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    int[] perm = randomPermutation(n, rng);

                    // Permute rY rows (keep rX fixed)
                    SimpleMatrix rYp = permuteRows(rY, perm);
                    SimpleMatrix C = cov(rX, rYp);

                    double s = n * frob2(C);
                    if (s >= stat) greater++;
                }
                p = (greater + 1.0) / (permutations + 1.0);
            } else {
                // Null approx based on residual products
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

        if (verbose) {
            TetradLogger.getInstance().log(fact + " p = " + p_ + " stat=" + stat
                    + " approx=" + approx + " Fx=" + numFeatXY + " Fz=" + numFeatZ
                    + " ft=" + featureType + " bwMult=" + bandwidthMultiplier + " lam=" + lambda);
        }

        return new IndependenceResult(fact, indep, p_, alpha - p_);
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

    /**
     * Retrieves the list of variable nodes.
     *
     * @return a list of Node objects representing the variables
     */
    @Override
    public List<Node> getVariables() {
        return vars;
    }

    /**
     * Retrieves the alpha value.
     *
     * @return the alpha value as a double
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the value of alpha for the current instance.
     * The alpha value must be a double within the range (0, 1), exclusive.
     * An IllegalArgumentException will be thrown if the specified value
     * is not within the valid range.
     *
     * @param alpha the new alpha value, must be greater than 0 and less than 1
     * @throws IllegalArgumentException if alpha is not in the range (0, 1)
     */
    @Override
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
    }

    /**
     * Retrieves the data encapsulated in this object.
     *
     * @return the DataSet instance contained within this object
     */
    @Override
    public DataSet getData() {
        return data;
    }

    /**
     * Determines whether verbose mode is enabled.
     *
     * @return true if verbose mode is enabled, false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets the verbosity mode for the system.
     *
     * @param verbose a boolean indicating whether verbose mode should be enabled (true) or disabled (false)
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // ---------------- RowsSettable ----------------

    @Override
    public List<Integer> getRows() {
        return rows;
    }

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

    // ---------------- Feature construction (KFF-ML style) ----------------

    private SimpleMatrix kffFeatCached(String tag, List<Node> varsForKey, double[][] Z, int mFeatures, long seed)
            throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        String key = keyFeat(tag, varsForKey, mFeatures, seed);

        return featCache.computeIfAbsent(key, k -> {
            // Compute bw2 (cached separately but keyed compatibly)
            double bw2 = bw2For(tag, varsForKey, Z);

            double[][] Phi = rffFeatures(Z, mFeatures, bw2, seed);
            SimpleMatrix M = new SimpleMatrix(Phi);

            if (centerFeatures) zscoreInPlace(M);
            else subtractColumnMeansInPlace(M);

            return M;
        });
    }

    private double bw2For(String tag, List<Node> varsForKey, double[][] Z) {
        String key = keyBw2(tag, varsForKey);

        return bw2Cache.computeIfAbsent(key, k -> {
            int n = Z.length;
            if (n <= 2 || Z[0].length == 0) return 1.0;

            int maxRows = Math.min(n, bwMaxRows);
            double bw2 = medianDistanceSquaredND(Z, maxRows);
            if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

            double mult2 = bandwidthMultiplier * bandwidthMultiplier;
            bw2 *= mult2;

            // Keep it from being insanely tiny
            if (bw2 < 1e-12) bw2 = 1e-12;

            return bw2;
        });
    }

    private String keyFeat(String tag, List<Node> vs, int mFeatures, long seed) {
        ArrayList<String> names = new ArrayList<>(vs.size());
        for (Node v : vs) names.add(v.getName());
        names.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(160);
        sb.append(tag)
                .append("|n=").append(getActiveRowCount())
                .append("|rows=").append(activeRowsHash())
                .append("|m=").append(mFeatures)
                .append("|ft=").append(featureType.name())
                .append("|ctr=").append(centerFeatures ? 1 : 0)
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|seed=").append(seed)
                .append("|vars=");
        for (String s : names) sb.append(s).append(",");
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

    // ---------------- Seeds (stable, order-invariant over Z) ----------------

    private long seedForX(Node x) {
        long h = 1469598103934665603L;
        h = 1099511628211L * (h ^ x.getName().hashCode());
        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        return h;
    }

    private long seedForY(Node y, List<Node> Z, boolean doRcit) {
        long h = 1469598103934665603L;
        h = 1099511628211L * (h ^ y.getName().hashCode());

        if (doRcit) {
            ArrayList<String> names = new ArrayList<>(Z.size());
            for (Node z : Z) names.add(z.getName());
            names.sort(String::compareTo);
            for (String s : names) h = 1099511628211L * (h ^ s.hashCode());
        }

        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        return h;
    }

    private long seedForZ(List<Node> Z) {
        long h = 1469598103934665603L;
        ArrayList<String> names = new ArrayList<>(Z.size());
        for (Node z : Z) names.add(z.getName());
        names.sort(String::compareTo);
        for (String s : names) h = 1099511628211L * (h ^ s.hashCode());
        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        return h;
    }

    // ---------------- Raw extraction + standardization ----------------

    private double[][] rawMatrix(List<Node> vv) {
        int n = getActiveRowCount();
        int d = vv.size();
        double[][] M = new double[n][d];

        for (int j = 0; j < d; j++) {
            int col = data.getColumn(vv.get(j));
            if (col < 0) {
                // fallback: name lookup
                col = data.getVariableNames().indexOf(vv.get(j).getName());
                if (col < 0) throw new IllegalArgumentException("Variable not found: " + vv.get(j).getName());
            }
            for (int i = 0; i < n; i++) {
                int row = activeRowIndex(i);
                M[i][j] = data.getDouble(row, col);
            }
        }
        return M;
    }

    // z-score each column of a raw n×d double matrix (ddof=1)
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

    // ---------------- KFF Fourier features ----------------

    /**
     * Build Random Fourier (or Orthogonal Random) Features for an RBF kernel:
     *   k(x,x') = exp(-||x-x'||^2 / bw2)
     * RFF/ORF:
     *   wStd = sqrt(2/bw2)
     *   phi_j(x) = sqrt(2/m) cos(w_j^T x + b_j)
     */
    private double[][] rffFeatures(double[][] Z, int mFeatures, double bw2, long seed) {
        final int n = Z.length;
        final int d = (n == 0) ? 0 : Z[0].length;

        if (n == 0 || mFeatures <= 0) return new double[n][Math.max(mFeatures, 0)];
        if (d == 0) {
            // No inputs: features are just cos(b) constants.
            double[][] Phi = new double[n][mFeatures];
            SplittableRandom rng0 = new SplittableRandom(seed);
            double scale0 = Math.sqrt(2.0 / mFeatures);
            double[] b0 = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) b0[j] = 2.0 * Math.PI * rng0.nextDouble();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < mFeatures; j++) Phi[i][j] = scale0 * Math.cos(b0[j]);
            }
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

            // Gaussian block
            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++) {
                for (int j = 0; j < d; j++) Q[i][j] = nextGaussian(rng);
            }

            // Orthonormalize rows (Gram–Schmidt)
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

            // Scale by chi(d) radii
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

    // ---------------- Feature-space ridge residualization ----------------

    private static SimpleMatrix ridgeResidual(SimpleMatrix X, SimpleMatrix Z, double alpha) {
        if (Z == null || Z.getNumCols() == 0) return X;
        if (!(alpha > 0) || !Double.isFinite(alpha)) alpha = 1e-18;

        // B = (Z^T Z + alpha I)^{-1} Z^T X
        SimpleMatrix ZtZ = Z.transpose().mult(Z);
        SimpleMatrix A = ZtZ.plus(SimpleMatrix.identity(ZtZ.getNumRows()).scale(alpha));
        SimpleMatrix B = A.solve(Z.transpose().mult(X));
        return X.minus(Z.mult(B));
    }

    // ---------------- Stats + approximations (from RCIT-style code) ----------------

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
        int n = A.getNumRows();
        return A.transpose().mult(B).scale(1.0 / (n - 1));
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
        double k = (mu * mu) / var;     // shape
        double theta = var / mu;        // scale
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

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    // ---------------- Internal enum ----------------

    /**
     * Represents a set of approximation methods used for probabilistic or statistical computations.
     * These methods may be used in various domains such as machine learning, data analysis,
     * or other applications involving approximate calculations.
     */
    public enum Approx {

        /**
         * An approximation method based on the LPB4 algorithm.
         */
        LPB4,

        /**
         * Represents an approximation method based on the Hilbert-based Estimation (HBE) technique.
         * This technique is used for probabilistic or statistical computations, often applied in domains
         * such as data analysis, machine learning, and scenarios requiring approximate results.
         */
        HBE,

        /**
         * Utilizes the Gamma distribution for approximation purposes.
         * This method is commonly applied in statistical computations,
         * particularly in scenarios requiring probabilistic modeling
         * or handling continuous data distributions.
         */
        GAMMA,

        /**
         * An approximation method derived from the Chi-squared distribution.
         * This method is commonly used in statistical computations and hypothesis testing.
         * It provides a means to approximate probabilities or test the goodness-of-fit
         * for observed data against an expected distribution.
         */
        CHI2,

        /**
         * An approximation method based on permutation algorithms.
         * This approach is commonly used in probabilistic or statistical computations
         * where randomization or reordering of elements is employed.
         * Permutation-based methods are often applied in hypothesis testing,
         * resampling techniques, or simulations to assess statistical significance
         * or estimate probabilities.
         */
        PERMUTATION
    }
}