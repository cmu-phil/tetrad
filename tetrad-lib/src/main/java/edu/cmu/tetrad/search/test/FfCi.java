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
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Double.NaN;

/**
 * FF-CI-Mixed: Feature-Function CI test for mixed continuous/discrete data.
 *
 * Guarantee:
 *   If the dataset contains NO discrete variables, this class delegates all
 *   CI queries to IndTestFfCi, and (when configured through the same setters)
 *   behaves identically to FF-CI on continuous data.
 *
 * Mixed handling:
 *   - Continuous vars: Random Fourier Features (RFF) or Orthogonal Random Features (ORF)
 *   - Discrete vars: categorical features (one-hot if catRho==0; otherwise exact PSD feature map
 *     for kernel with diag=1, offdiag=catRho)
 *
 * Conditioning uses ridge residualization in feature space (same pattern as FF-CI).
 */
public final class FfCi implements IndependenceTest, RowsSettable {

    // ---------------- feature representation ----------------

//    public enum FeatureType { RFF, ORF }

    // ---------------- core data ----------------
    private final DataSet data;
    private final List<Node> vars;

    // Active rows state
    private List<Integer> rows = null;
    private int n;

    // ---------------- hyperparams (kept aligned with IndTestFfCi for continuous-only) ----------------
    private int numFeatXY = 10;
    private int numFeatZ  = 100;

    private int permutations = 0;
    private double lambda = 1.0;
    private boolean centerFeatures = true;

    private FfCiContinuous.Approx pValueMethod = FfCiContinuous.Approx.GAMMA; // same enum as IndTestFfCi

    // Mixed-only knobs
    private double bandwidthMultiplier = 1.0;
    private int bwMaxRows = 500;
    private FfCiContinuous.FeatureType featureType = FfCiContinuous.FeatureType.RFF;
    private double catRho = 0.0; // 0 => one-hot

    // ---------------- IndependenceTest state ----------------
    private double alpha = 0.05;
    private boolean verbose = false;

    // ---------------- RNG ----------------
    private final Random rng;

    // ---------------- caches ----------------
    private transient Map<String, SimpleMatrix> featCache = new ConcurrentHashMap<>();
    private transient Map<String, Double> bw2Cache = new ConcurrentHashMap<>();

    // ---------------- Continuous delegate ----------------
    private final boolean dataHasAnyDiscrete;
    private final FfCiContinuous continuousDelegate;

    // Bump this whenever the *values* in `data` change (e.g., resimulate in-place).
    // Included in cache keys so cached features/bandwidths cannot leak across datasets.
    private volatile long dataVersion = 0L;

    // ---------------- ctor ----------------

    /**
     * Constructs an instance of FfCiMixed with the specified dataset and default parameters.
     *
     * @param dataSet the dataset to be used for initializing the instance
     */
    public FfCi(DataSet dataSet) {
        this(dataSet, new Parameters());
        this.dataVersion = System.identityHashCode(dataSet);
    }

    /**
     * Constructs a new FfCiMixed instance for conducting conditional independence tests
     * on a dataset that may contain both continuous and discrete variables. This class
     * initializes the necessary delegates and configuration parameters for the mixed data setting.
     *
     * @param dataSet the dataset containing the variables and data to be analyzed;
     *                must not be null
     * @param params  configuration parameters for the conditional independence tests;
     *                should contain relevant key-value pairs, including optional "rcit.seed"
     *                for random seed initialization
     */
    public FfCi(DataSet dataSet, Parameters params) {
        this.data = Objects.requireNonNull(dataSet, "data");
        this.vars = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));
        this.n = getActiveRowCount();

        boolean anyDisc = false;
        for (Node v : this.vars) {
            if (v instanceof DiscreteVariable) { anyDisc = true; break; }
        }
        this.dataHasAnyDiscrete = anyDisc;

        // Delegate (FF-CI continuous implementation)
        this.continuousDelegate = new FfCiContinuous(this.data);

        // Seed: respect rcit.seed if present; otherwise stable default.
        long seed = params.getLong("rcit.seed", 1729L);
        this.rng = new Random(seed);

        // Initialize delegate from current knobs
        syncDelegateToThis();
    }

    // ---------------- public setters (wrapper-friendly) ----------------

    /**
     * Sets the seed for the random number generator and updates any associated components.
     * This method ensures that the internal random number generator and any dependent
     * delegates are seeded with the same value, potentially impacting reproducibility of
     * generated outputs.
     *
     * @param seed the initial seed value to set for the random number generator.
     */
    public void setSeed(long seed) {
        this.rng.setSeed(seed);
        invalidateCaches();
        this.continuousDelegate.setSeed(seed);
    }

    /**
     * Sets the alpha value to control transparency or blending levels.
     * The value must be in the range (0, 1), exclusive.
     *
     * @param alpha the alpha value to set; must be greater than 0 and less than 1
     * @throws IllegalArgumentException if the alpha value is not in the range (0, 1)
     */
    @Override
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
        invalidateCaches();
        this.continuousDelegate.setAlpha(alpha);
    }

    /**
     * Retrieves the alpha value.
     *
     * @return The alpha value as a double.
     */
    @Override
    public double getAlpha() { return alpha; }

    /**
     * Determines whether the verbose mode is enabled or not.
     *
     * @return true if verbose mode is enabled, false otherwise.
     */
    @Override
    public boolean isVerbose() { return verbose; }

    /**
     * Sets the verbosity level for the current instance and its delegate.
     *
     * @param verbose a boolean value indicating whether verbose mode
     *                should be enabled (true) or disabled (false)
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        this.continuousDelegate.setVerbose(verbose);
    }

    /**
     * Sets the lambda value for the instance. The value is constrained to a minimum
     * threshold to ensure numerical stability. This method also invalidates any
     * cached computations and updates the lambda value in the continuous delegate.
     *
     * @param lambda the new lambda value to be set; constrained to a minimum of 1e-12
     */
    public void setLambda(double lambda) {
        this.lambda = Math.max(1e-12, lambda);
        invalidateCaches();
        this.continuousDelegate.setLambda(this.lambda);
    }

//    public void setCenterFeatures(boolean centerFeatures) {
//        this.centerFeatures = centerFeatures;
//        this.featCache.clear();
////        this.continuousDelegate.setCenterFeatures(centerFeatures);
//    }

    /**
     * Sets the number of features in the XY dimension. Ensures the value is at least 1.
     * Invalidates any cached data and updates the continuous delegate with the new value.
     *
     * @param d the desired number of features in the XY dimension
     */
    public void setNumFeaturesXY(int d) {
        this.numFeatXY = Math.max(1, d);
        invalidateCaches();
        this.continuousDelegate.setNumFeaturesXY(this.numFeatXY);
    }

    /**
     * Sets the number of features for the Z component. This value is constrained
     * to be at least 1. Updates dependent caches and delegates with the new value.
     *
     * @param d the desired number of features for the Z component
     */
    public void setNumFeaturesZ(int d) {
        this.numFeatZ = Math.max(1, d);
        invalidateCaches();
        this.continuousDelegate.setNumFeaturesZ(this.numFeatZ);
    }

    /**
     * Invalidates and clears all cached data to ensure that outdated or stale
     * entries are removed. This method clears the contents of the `featCache`
     * and `bw2Cache` collections, effectively resetting their state.
     *
     * Intended to be used when the cached data becomes unreliable or requires
     * a refresh to ensure correctness.
     */
    private void invalidateCaches() {
        getFeatureCache().clear();
        getBw2Cache().clear();
    }

    private Map<String, Double> getBw2Cache() {
        if (bw2Cache == null) bw2Cache = new ConcurrentHashMap<>();
        return bw2Cache;
    }

    private Map<String, SimpleMatrix> getFeatureCache() {
        if (featCache == null) featCache = new ConcurrentHashMap<>();
        return featCache;
    }

    /**
     * Sets the number of permutations to be used in the mixed independence test.
     * The value is clamped to zero or above to ensure non-negative input.
     * Also invalidates any relevant caches and synchronizes the continuous delegate.
     *
     * @param permutations the number of permutations to set; must be non-negative
     */
    public void setPermutations(int permutations) {
        this.permutations = Math.max(0, permutations);
        invalidateCaches();
        this.continuousDelegate.setPermutations(this.permutations);
    }

    /**
     * Sets the approximation method to be used in statistical computations within the mixed
     * independence test. This method updates the internal approximation strategy and ensures
     * that any necessary caches are invalidated or synchronized accordingly.
     *
     * @param method the approximation method to set; must not be null
     */
    public void setApproximation(FfCiContinuous.Approx method) {
        this.pValueMethod = Objects.requireNonNull(method, "method");
        invalidateCaches();
        this.continuousDelegate.setApproximation(method);
    }

    // Mixed-only knobs (safe no-ops for continuous delegate; they just affect mixed path)

    /**
     * Updates the bandwidth multiplier used for computations. The value must be greater than 0
     * and finite to ensure valid operation. This method also invalidates cached computations
     * and updates the corresponding settings in the continuous delegate.
     *
     * @param bandwidthMultiplier the new bandwidth multiplier value; must be greater than 0 and finite
     * @throws IllegalArgumentException if the provided bandwidthMultiplier is less than or equal to 0 or not finite
     */
    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0 and finite");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        invalidateCaches();
        this.continuousDelegate.setBandwidthMultiplier(bandwidthMultiplier);
    }

    /**
     * Sets the maximum number of rows to be considered during bandwidth computations.
     * The value provided is clamped to a minimum of 50 to ensure a valid threshold.
     * This method also updates the continuous delegate and invalidates any related caches.
     *
     * @param bwMaxRows the maximum number of rows to set; must be a non-negative integer.
     */
    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = Math.max(50, bwMaxRows);
        this.continuousDelegate.setBwMaxRows(bwMaxRows);
        invalidateCaches();
    }

    /**
     * Sets the feature type to be used in the mixed independence test.
     * This method updates the feature type for the continuous delegate, clears the feature cache,
     * and validates that the provided feature type is non-null.
     *
     * @param featureType the feature type to set; must not be null
     * @throws NullPointerException if the provided featureType is null
     */
    public void setFeatureType(FfCiContinuous.FeatureType featureType) {
        this.featureType = Objects.requireNonNull(featureType, "featureType");
        this.continuousDelegate.setFeatureType(featureType);
        this.featCache.clear();
    }

    /**
     * Retrieves the current feature type used in the mixed independence test.
     *
     * @return the feature type currently set for the mixed independence test
     */
    public FfCiContinuous.FeatureType getFeatureType() { return featureType; }

    /**
     * Sets the categorical feature correlation coefficient (catRho) for the mixed independence test.
     * The value of rho must be within the range [0, 1) and must be finite.
     * This method also invalidates any relevant caches to ensure consistency.
     *
     * @param rho the new value for the categorical correlation coefficient; must be in [0, 1) and finite
     * @throws IllegalArgumentException if rho is not within the range [0, 1) or is not finite
     */
    public void setCatRho(double rho) {
        if (!(rho >= 0.0 && rho < 1.0) || !Double.isFinite(rho)) {
            throw new IllegalArgumentException("catRho must be in [0,1)");
        }
        this.catRho = rho;
        invalidateCaches();
    }

    /**
     * Retrieves the current categorical feature correlation coefficient (catRho)
     * used in the mixed independence test. This coefficient reflects the assumed
     * correlation structure for categorical variables and impacts statistical computations
     * within the model.
     *
     * @return the categorical correlation coefficient (catRho), a value in the range [0, 1).
     */
    public double getCatRho() { return catRho; }

    // ---------------- RowsSettable ----------------

    /**
     * Retrieves the list of integers representing the rows used in the mixed independence test.
     *
     * @return a list of integers corresponding to the rows currently considered in the test
     */
    @Override
    public List<Integer> getRows() { return rows; }

    /**
     * Sets the rows to be used in computations. Validates that the provided list of rows
     * is non-null and contains only valid, non-negative indices that are within
     * the bounds of the data. Clears relevant caches and synchronizes the state
     * of the continuous delegate to maintain consistency.
     *
     * @param rows a list of integers representing the row indices to set; must be non-null,
     *             must not contain any null elements, and each element must be a
     *             non-negative index within the bounds of the number of rows in the data.
     * @throws NullPointerException if any element in the list is null.
     * @throws IllegalArgumentException if any element is negative or outside the valid range.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            this.n = data.getNumRows();
            featCache.clear();
            bw2Cache.clear();
            // critical for parity
            this.continuousDelegate.setRows(null);
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
        // critical for parity
        this.continuousDelegate.setRows(this.rows);
    }

    private int getActiveRowCount() { return (rows == null) ? data.getNumRows() : rows.size(); }
    private int activeRowIndex(int i) { return (rows == null) ? i : rows.get(i); }

    // ---------------- IndependenceTest interface ----------------

    /**
     * Retrieves the current data set associated with this instance.
     *
     * @return the current DataSet object
     */
    @Override
    public DataSet getData() { return data; }

    /**
     * Retrieves a list of variable nodes.
     *
     * @return a list of Node objects representing the variables.
     */
    @Override
    public List<Node> getVariables() { return vars; }

    /**
     * Checks the independence between two nodes, x and y, given a set of conditioning nodes, z.
     * The method performs different operations depending on the properties of the dataset (e.g.,
     * whether the dataset includes discrete variables). If the dataset does not contain discrete
     * variables, it delegates the computation to a method for continuous data. Otherwise, it follows
     * a mixed-discrete approach.
     *
     * @param x The first node whose independence is to be tested. Must not be null.
     * @param y The second node whose independence is to be tested. Must not be null.
     * @param z The set of conditioning nodes for the independence test. May be null, in which case it
     *          will be treated as an empty set.
     * @return An {@code IndependenceResult} object indicating whether x and y are independent given z,
     *         as well as other details related to the independence test.
     * @throws InterruptedException If the thread executing this method is interrupted before or during
     *                              the computation.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");

        // Hard guarantee: if dataset has no discrete vars, behave exactly like FF-CI (IndTestFfCi).
        if (!dataHasAnyDiscrete) {
            // Keep delegate aligned with current knobs (mostly redundant since setters forward,
            // but safe if something was set via constructor/params)
            syncDelegateToThis();
            return continuousDelegate.checkIndependence(x, y, z);
        }

        this.n = getActiveRowCount();

        final List<Node> Z = (z == null) ? new ArrayList<>() : new ArrayList<>(z);
        Z.sort(Comparator.comparing(Node::getName));
        IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));

        // --- mixed stack path ---
        return checkIndependenceMixedStack(x, y, Z, fact);
    }

    private IndependenceResult checkIndependenceMixedStack(Node x, Node y, List<Node> Z, IndependenceFact fact)
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

        // RCIT-style augmentation (same pattern as your mixed code)
        final List<Node> yKeyVars = (!Z.isEmpty()) ? hstackVarList(y, Z) : Collections.singletonList(y);

        long seedX = seedForX(x) ^ 1729L;
        long seedY = seedForBlock("Y", yKeyVars) ^ 1729L;
        long seedZ = seedForBlock("Z", Z) ^ 1729L;

        SimpleMatrix fX = kffFeatMixedCached("X", Collections.singletonList(x), numFeatXY, seedX);
        SimpleMatrix fY = kffFeatMixedCached("Y", yKeyVars, numFeatXY, seedY);
        SimpleMatrix fZ = Z.isEmpty() ? null : kffFeatMixedCached("Z", Z, numFeatZ, seedZ);

        final double stat;
        double p = NaN;

        if (fZ == null || fZ.getNumCols() == 0) {
            // -------- RIT --------
            SimpleMatrix Cxy = covCentered(fX, fY);
            stat = n * frob2(Cxy);

            SimpleMatrix resX = fX.copy();
            SimpleMatrix resY = fY.copy();
            subtractColumnMeansInPlace(resX);
            subtractColumnMeansInPlace(resY);

            SimpleMatrix Cov = kronResCov(resX, resY);
            double[] eig = positiveEigs(Cov);

//            p = pValueFromMethod(stat, eig, fX, fY, null);
            p = pValueFromMethod(fact, stat, eig, fX, fY, null);
        } else {
            // -------- Conditional: ridge residualization --------
            final double alphaRidge = Math.max(1e-18, lambda);// / Math.max(1.0, (n - 1.0)));
            SimpleMatrix rX = ridgeResidual(fX, fZ, alphaRidge);
            SimpleMatrix rY = ridgeResidual(fY, fZ, alphaRidge);

            subtractColumnMeansInPlace(rX);
            subtractColumnMeansInPlace(rY);

            SimpleMatrix Cxy = covCentered(rX, rY);
            stat = n * frob2(Cxy);

            SimpleMatrix resX = rX.copy();
            SimpleMatrix resY = rY.copy();
            subtractColumnMeansInPlace(resX);
            subtractColumnMeansInPlace(resY);

            SimpleMatrix Cov = kronResCov(resX, resY);
            double[] eig = positiveEigs(Cov);

//            p = pValueFromMethod(stat, eig, rX, rY, null);
            p = pValueFromMethod(fact, stat, eig, rX, rY, null);
        }

        double p_ = clamp01(p);
        boolean indep = (p_ > alpha);

        if (verbose) {
            TetradLogger.getInstance().log(fact + " p=" + p_ + " stat=" + stat
                    + " method=" + pValueMethod
                    + " Fx=" + numFeatXY + " Fz=" + numFeatZ
                    + " ft=" + featureType + " bwMult=" + bandwidthMultiplier + " lam=" + lambda
                    + " catRho=" + catRho + " perms=" + permutations);
        }

        return new IndependenceResult(fact, indep, p_, alpha - p_);
    }

    // ---------------- continuous delegate sync ----------------

    private void syncDelegateToThis() {
        // Keep delegate aligned (setters already forward, but this covers constructor-set defaults too)
        continuousDelegate.setAlpha(alpha);
        continuousDelegate.setVerbose(verbose);
        continuousDelegate.setLambda(lambda);
//        continuousDelegate.setCenterFeatures(centerFeatures);
//        continuousDelegate.setNumFeaturesXY(numFeatXY);
//        continuousDelegate.setNumFeaturesZ(numFeatZ);
        continuousDelegate.setPermutations(permutations);
        continuousDelegate.setApproximation(pValueMethod);
        continuousDelegate.setRows(rows);
        // NOTE: bandwidthMultiplier/bwMaxRows/featureType/catRho are mixed-only here.
    }

    // ---------------- Mixed feature construction ----------------

    private SimpleMatrix kffFeatMixedCached(String tag, List<Node> varsForKey, int mFeaturesCont, long seed)
            throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        String key = keyFeat(tag, varsForKey, mFeaturesCont, seed);

        return featCache.computeIfAbsent(key, k -> {
            MixedBlock block = extractMixedBlock(varsForKey);

            double[][] Zc = block.cont; // n x dc (z-scored)
            double bw2 = bw2For(tag, varsForKey, Zc);

            double[][] PhiC = (Zc.length == 0 || Zc[0].length == 0)
                    ? new double[n][0]
                    : rffFeatures(Zc, mFeaturesCont, bw2, seed);

            double[][] PhiD = block.discFeat;

            double[][] Phi = hstackRaw(PhiC, PhiD);
            SimpleMatrix M = new SimpleMatrix(Phi);

            if (centerFeatures) zscoreInPlace(M);
            else subtractColumnMeansInPlace(M);

            return M;
        });
    }

    private MixedBlock extractMixedBlock(List<Node> vv) {
        final int n = getActiveRowCount();

        final ArrayList<Node> contVars = new ArrayList<>();
        final ArrayList<DiscreteVariable> discVars = new ArrayList<>();

        for (Node v : vv) {
            if (v instanceof DiscreteVariable dv) discVars.add(dv);
            else contVars.add(v);
        }

        // continuous raw -> z-score
        final double[][] cont = new double[n][contVars.size()];
        for (int j = 0; j < contVars.size(); j++) {
            Node v = contVars.get(j);
            int col = data.getColumn(v);
            if (col < 0) col = data.getVariableNames().indexOf(v.getName());
            if (col < 0) throw new IllegalArgumentException("Variable not found: " + v.getName());
            for (int i = 0; i < n; i++) cont[i][j] = data.getDouble(activeRowIndex(i), col);
        }
        zscoreInPlace(cont);

        // discrete categorical features
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
                final double[][] A = buildCatFeatureRows(k, this.catRho);

                for (int i = 0; i < n; i++) {
                    int row = activeRowIndex(i);
                    int val;
                    try {
                        val = data.getInt(row, col);
                    } catch (Throwable t) {
                        val = (int) Math.round(data.getDouble(row, col));
                    }
                    if (val < 0) val = 0;
                    if (val >= k) val = k - 1;
                    System.arraycopy(A[val], 0, discFeat[i], offset, k);
                }
                offset += k;
            }
        }

        return new MixedBlock(cont, discFeat);
    }

    private record MixedBlock(double[][] cont, double[][] discFeat) { }

    private double bw2For(String tag, List<Node> varsForKey, double[][] Zcont) {
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
        for (Node v : vs) if (!(v instanceof DiscreteVariable)) names.add(v.getName());
        names.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(140);
        sb.append(tag)
                .append("|dv=").append(dataVersion)
                .append("|n=").append(getActiveRowCount())
                .append("|rows=").append(activeRowsHash())
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|cvars=");
        for (String s : names) sb.append(s).append(",");
        return sb.toString();
    }

    private String keyFeat(String tag, List<Node> vs, int mFeatures, long seed) {
        ArrayList<String> pair = new ArrayList<>(vs.size());
        for (Node v : vs) pair.add(v.getName() + ":" + ((v instanceof DiscreteVariable) ? "D" : "C"));
        pair.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(220);
        sb.append(tag)
                .append("|dv=").append(dataVersion)
                .append("|n=").append(getActiveRowCount())
                .append("|rows=").append(activeRowsHash())
                .append("|m=").append(mFeatures)
                .append("|ft=").append(featureType.name())
                .append("|ctr=").append(centerFeatures ? 1 : 0)
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|seed=").append(seed)
                .append("|catRho=").append(Double.doubleToLongBits(catRho))
                .append("|vars=");
        for (String s : pair) sb.append(s).append(",");
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

    // ---------------- stable seeds ----------------

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

    // ---------------- RFF / ORF features ----------------

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
            for (int i = 0; i < n; i++)
                for (int j = 0; j < mFeatures; j++)
                    Phi[i][j] = scale0 * Math.cos(b0[j]);
            return Phi;
        }

        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

        final double wStd = Math.sqrt(2.0 / bw2);
        final double scale = Math.sqrt(2.0 / mFeatures);

        SplittableRandom rng = new SplittableRandom(seed);

        double[][] W;
        double[] b = new double[mFeatures];

        if (featureType == FfCiContinuous.FeatureType.RFF) {
            W = new double[mFeatures][d];
            for (int j = 0; j < mFeatures; j++) {
                for (int k = 0; k < d; k++) W[j][k] = wStd * nextGaussian(rng);
                b[j] = 2.0 * Math.PI * rng.nextDouble();
            }
        } else {
            W = sampleOrthogonalW(mFeatures, d, wStd, rng);
            for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * Math.PI * rng.nextDouble();
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

    private static double nextGaussian(SplittableRandom rng) {
        double u, v, s;
        do {
            u = 2.0 * rng.nextDouble() - 1.0;
            v = 2.0 * rng.nextDouble() - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);
        return u * Math.sqrt(-2.0 * Math.log(s) / s);
    }

    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd, SplittableRandom rng) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;
        while (filled < mFeatures) {
            int block = Math.min(d, mFeatures - filled);

            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++)
                for (int j = 0; j < d; j++)
                    Q[i][j] = nextGaussian(rng);

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

    // ---------------- categorical feature map ----------------

    private static double[][] buildCatFeatureRows(int k, double rho) {
        if (k <= 0) throw new IllegalArgumentException("k must be >= 1");

        if (!(rho > 0.0) || rho < 1e-15) {
            double[][] I = new double[k][k];
            for (int i = 0; i < k; i++) I[i][i] = 1.0;
            return I;
        }
        if (!(rho >= 0.0 && rho < 1.0) || !Double.isFinite(rho)) {
            throw new IllegalArgumentException("rho must be in [0,1)");
        }

        double[][] K = new double[k][k];
        for (int i = 0; i < k; i++) {
            K[i][i] = 1.0;
            for (int j = 0; j < i; j++) {
                K[i][j] = rho;
                K[j][i] = rho;
            }
        }

        double[][] L = choleskyLowerOrThrow(K);
        return L;
    }

    private static double[][] choleskyLowerOrThrow(double[][] M) {
        int n = M.length;
        double[][] L = new double[n][n];

        if (choleskyLowerInto(M, L, 0.0)) return L;

        double jitter = 1e-12;
        for (int tries = 0; tries < 3; tries++) {
            if (choleskyLowerInto(M, L, jitter)) return L;
            jitter *= 10.0;
        }

        throw new IllegalArgumentException("Categorical kernel matrix not PD (unexpected for rho in [0,1)).");
    }

    private static boolean choleskyLowerInto(double[][] M, double[][] L, double jitter) {
        int n = M.length;
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

    // ---------------- ridge residualization ----------------

    private static SimpleMatrix ridgeResidual(SimpleMatrix X, SimpleMatrix Z, double alpha) {
        if (Z == null || Z.getNumCols() == 0) return X;
        if (!(alpha > 0) || !Double.isFinite(alpha)) alpha = 1e-18;

        SimpleMatrix ZtZ = Z.transpose().mult(Z);
        SimpleMatrix A = ZtZ.plus(SimpleMatrix.identity(ZtZ.getNumRows()).scale(alpha));
        SimpleMatrix B = A.solve(Z.transpose().mult(X));
        return X.minus(Z.mult(B));
    }

    // ---------------- p-values ----------------

    private double pValueFromMethod(IndependenceFact fact, double stat, double[] eig,
                                    SimpleMatrix rX, SimpleMatrix rY, SimpleMatrix ignored) {       // Permutation (if requested and available)
        if (pValueMethod == FfCiContinuous.Approx.PERMUTATION && permutations > 0) {
//            int greater = 0;
//            for (int b = 0; b < permutations; b++) {
//                int[] perm = randomPermutation(rY.getNumRows(), rng);
//                SimpleMatrix rYp = permuteRows(rY, perm);
//                SimpleMatrix C = covCentered(rX, rYp);
//                double s = rY.getNumRows() * frob2(C);
//                if (s >= stat) greater++;
//            }
//            return (greater + 1.0) / (permutations + 1.0);

            // IMPORTANT: per-query RNG, NOT shared rng.
            SplittableRandom prng = new SplittableRandom(seedForPermutation(fact));

            int greater = 0;
            for (int b = 0; b < permutations; b++) {
                int[] perm = randomPermutation(rY.getNumRows(), prng);
                SimpleMatrix rYp = permuteRows(rY, perm);
                SimpleMatrix C = covCentered(rX, rYp);
                double s = rY.getNumRows() * frob2(C);
                if (s >= stat) greater++;
            }
            return (greater + 1.0) / (permutations + 1.0);
        }

        // Otherwise use quadratic-form approximations (same as your mixed code calls)
        return switch (pValueMethod) {
            case GAMMA -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
            case SADDLEPOINT -> QuadraticFormPValues.saddlepointLugannaniRiceP(stat, eig);
            case DAVIES_IMHOF -> QuadraticFormPValues.daviesP(stat, eig);
            // If PERMUTATION requested but permutations==0, fall back to GAMMA for safety
            case PERMUTATION -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
        };
    }

    // ---------------- linear algebra helpers ----------------

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

    private static int[] randomPermutation(int n, SplittableRandom rng) {
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

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }


    /** Call this after any operation that changes the contents of `data` (resimulate, edit, reload, etc.). */
    public void bumpDataVersion() {
        dataVersion++;
        invalidateCaches();
        // If your continuous delegate also caches anything internally, keep it in sync as well.
        // (If it doesn't have such a method, just omit this line.)
//        if (continuousDelegate instanceof DataVersioned dv) {
//            dv.setDataVersion(dataVersion);
//        }
    }

    /** Use this if the caller maintains a run-id or dataset-id; any change forces cache miss. */
    public void setDataVersion(long newVersion) {
        this.dataVersion = newVersion;
        invalidateCaches();
//        if (continuousDelegate instanceof DataVersioned dv) {
//            dv.setDataVersion(dataVersion);
//        }
    }

    public long getDataVersion() {
        return dataVersion;
    }


//    /**
//     * Optional tiny interface you can also implement in the delegate later if you want.
//     * Safe to leave unused.
//     */
//    private interface DataVersioned {
//        void setDataVersion(long v);
//    }

    private long seedForPermutation(IndependenceFact fact) {
        long h = 1469598103934665603L;          // FNV offset
        h = 1099511628211L * (h ^ "PERM".hashCode());

        // include dataset identity/version + active rows
        h = 1099511628211L * (h ^ Long.hashCode(dataVersion));
        h = 1099511628211L * (h ^ Integer.hashCode(getActiveRowCount()));
        h = 1099511628211L * (h ^ Integer.hashCode(activeRowsHash()));

        // include the actual CI query: X, Y, and conditioning set Z (sorted)
        h = 1099511628211L * (h ^ fact.getX().getName().hashCode());
        h = 1099511628211L * (h ^ fact.getY().getName().hashCode());

        ArrayList<String> zNames = new ArrayList<>();
        for (Node z : fact.getZ()) zNames.add(z.getName());
        Collections.sort(zNames);
        for (String s : zNames) h = 1099511628211L * (h ^ s.hashCode());

        return h;
    }
}