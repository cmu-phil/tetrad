package edu.cmu.tetrad.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.stream.IntStream;

/**
 * Conditional Kernel Density Estimation with multivariate conditioning.
 * <p>
 * Estimates conditional distributions using a product Gaussian kernel over
 * one or more conditioning variables. Supports:
 * <p>
 * 1. Conditional univariate:  p(Y | X = x)       — 1D density over a grid
 * 2. Conditional bivariate:   p(Y1, Y2 | X = x)  — 2D density over a grid
 * 3. Correlation profile:     rho(Y1,Y2 | X1=x1, X2 swept, ...)
 * 4. Batch bivariate:         all pairs at once, weights precomputed and shared
 * <p>
 * Performance design
 * ------------------
 * - Call computeWeights(targets) once per slider position and pass the
 * resulting double[] into any of the precomputed-weight overloads. This
 * avoids recomputing O(n * nCond) kernel products for every pair.
 * - batchBivariate() evaluates all requested pairs in parallel using the
 * common ForkJoinPool, then parallelises the inner grid loop of each pair
 * with a RecursiveAction. Adjust PARALLEL_THRESHOLD to tune granularity.
 * - Single-pair methods expose a parallel flag; pass true when calling
 * outside a batch (e.g. on a single highlighted cell).
 * <p>
 * Curse-of-dimensionality note
 * ----------------------------
 * With k conditioning variables effective sample size falls roughly as
 * n^(k/(k+4)). Beyond 4-5 conditioning variables you need a large dataset
 * or explicitly widened bandwidths.
 */
public class ConditionalKDE {

    // Usage example.
    //    ConditionalKDE kde = new ConditionalKDE(new double[][]{ X10, X11 });
    //
    //    // On every slider move:
    //    double[] w = kde.computeWeights(new double[]{ slider1.getValue(), slider2.getValue() });
    //    ConditionalKDE.BatchResult batch = kde.batchBivariateAllPairs(data, w, 64);
    //
    //    // In your cell renderer:
    //    ConditionalResult2D r = batch.get(rowVar, colVar);
    //    // → r.grid1, r.grid2, r.density ready to render

    // -----------------------------------------------------------------------
    // Tuning constant
    // -----------------------------------------------------------------------

    /**
     * Minimum number of grid rows assigned to one ForkJoin leaf task.
     * Increase if thread-overhead dominates (small n); decrease for large grids.
     */
    private static final int PARALLEL_THRESHOLD = 8;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final double[][] condVars;       // [nCondVars][n]
    private final double[] condBandwidths; // one per conditioning variable
    private final int n;
    private final int nCond;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * Constructs a conditional kernel density estimator (KDE) with specified
     * conditioning variables and bandwidths.
     *
     * @param condVars       A 2D array of conditioning variables where each
     *                       row represents a conditioning variable and each
     *                       column corresponds to a data sample. All rows must
     *                       have equal lengths.
     * @param condBandwidths An array of bandwidths for each conditioning
     *                       variable. If null, empty, or containing non-positive
     *                       values for any variable, the bandwidth for that
     *                       variable will be automatically determined using
     *                       Silverman's rule of thumb.
     * @throws IllegalArgumentException if the length of any row in condVars
     *                                  is not equal to the length of the first row.
     */
    public ConditionalKDE(double[][] condVars, double[] condBandwidths) {
        this.nCond = condVars.length;
        this.n = condVars[0].length;
        this.condVars = new double[nCond][];
        for (int k = 0; k < nCond; k++) {
            if (condVars[k].length != n)
                throw new IllegalArgumentException(
                        "Conditioning variable " + k + " length mismatch: "
                                + condVars[k].length + " vs " + n);
            this.condVars[k] = Arrays.copyOf(condVars[k], n);
        }
        this.condBandwidths = new double[nCond];
        for (int k = 0; k < nCond; k++) {
            boolean auto = condBandwidths == null
                    || k >= condBandwidths.length
                    || condBandwidths[k] <= 0;
            this.condBandwidths[k] = auto ? silverman1D(condVars[k])
                    : condBandwidths[k];
        }
    }

    /**
     * Constructs a conditional kernel density estimator (KDE) with a single
     * conditioning variable. This is a convenience constructor that wraps
     * the single variable into a 2D array for compatibility with the main logic.
     *
     * @param condVar A 1D array representing the conditioning variable. Each
     *                element corresponds to a data sample.
     */
    public ConditionalKDE(double[] condVar) {
        this(new double[][]{condVar}, null);
    }

    /**
     * Constructor for the ConditionalKDE class.
     *
     * @param condVars A 2D array where each row represents a data point
     *                 and each column corresponds to a conditional variable.
     */
    public ConditionalKDE(double[][] condVars) {
        this(condVars, null);
    }

    // -----------------------------------------------------------------------
    // Weight precomputation  ← share across all pairs at a fixed slider position
    // -----------------------------------------------------------------------

    private static double gaussianKernel(double u) {
        return Math.exp(-0.5 * u * u) / Math.sqrt(2.0 * Math.PI);
    }

    private static double silverman1D(double[] x) {
        return 0.9 * stdDev(x) * Math.pow(x.length, -0.2);
    }

    // -----------------------------------------------------------------------
    // Univariate conditional  p(Y | X = x)
    // -----------------------------------------------------------------------

    private static double silverman1DWeighted(double[] x, double[] w) {
        double mean = 0.0;
        for (int i = 0; i < x.length; i++) mean += w[i] * x[i];
        double var = 0.0, sumW2 = 0.0;
        for (int i = 0; i < x.length; i++) {
            var += w[i] * (x[i] - mean) * (x[i] - mean);
            sumW2 += w[i] * w[i];
        }
        return 0.9 * Math.sqrt(var) * Math.pow(1.0 / sumW2, 0.2);
    }

    private static double weightedCorrelation(double[] x, double[] y, double[] w) {
        double mx = 0, my = 0;
        for (int i = 0; i < x.length; i++) {
            mx += w[i] * x[i];
            my += w[i] * y[i];
        }
        double vx = 0, vy = 0, cov = 0;
        for (int i = 0; i < x.length; i++) {
            vx += w[i] * (x[i] - mx) * (x[i] - mx);
            vy += w[i] * (y[i] - my) * (y[i] - my);
            cov += w[i] * (x[i] - mx) * (y[i] - my);
        }
        double denom = Math.sqrt(vx * vy);
        return denom < 1e-12 ? 0.0 : cov / denom;
    }

    private static double stdDev(double[] x) {
        double mean = Arrays.stream(x).average().orElse(0.0);
        double var = Arrays.stream(x).map(v -> (v - mean) * (v - mean)).average().orElse(0.0);
        return Math.sqrt(var);
    }

    private static double min(double[] x) {
        return Arrays.stream(x).min().orElse(0.0);
    }

    // -----------------------------------------------------------------------
    // Bivariate conditional  p(Y1, Y2 | X = x)
    // -----------------------------------------------------------------------

    private static double max(double[] x) {
        return Arrays.stream(x).max().orElse(1.0);
    }

    private static double[] linspace(double lo, double hi, int n) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = lo + i * (hi - lo) / (n - 1);
        return out;
    }

    /**
     * Computes and normalises the product-kernel conditioning weights for the
     * given target values. Call this ONCE per slider position, then pass the
     * returned array to any precomputed-weight overload or to batchBivariate().
     *
     * @param targets one target value per conditioning variable (length nCond)
     * @return normalised weight array of length n
     */
    public double[] computeWeights(double[] targets) {
        checkTargets(targets);
        return conditioningWeights(targets);
    }

    /**
     * Computes and returns an array of weights based on the given target value.
     *
     * @param target the target value to compute weights for
     * @return an array of computed weights derived from the target value
     */
    public double[] computeWeights(double target) {
        return computeWeights(new double[]{target});
    }

    /**
     * Full control overload — accepts precomputed weights.
     *
     * @param Y          response variable (length n)
     * @param weights    precomputed from computeWeights(); null → recompute from targets
     * @param targets    used only when weights == null
     * @param gridPoints grid resolution
     * @param yBandwidth &lt;= 0 → Silverman auto
     * @param parallel   true → parallelise over grid points
     * @return a ConditionalResult1D object containing the computed univariate result.
     */
    public ConditionalResult1D conditionalUnivariate(
            double[] Y, double[] weights, double[] targets,
            int gridPoints, double yBandwidth, boolean parallel) {

        checkResponseLength(Y);
        double[] w = weights != null ? weights : conditioningWeights(targets);
        double hy = yBandwidth > 0 ? yBandwidth : silverman1DWeighted(Y, w);

        double[] grid = linspace(min(Y), max(Y), gridPoints);
        double[] density = new double[gridPoints];

        if (parallel) {
            IntStream.range(0, gridPoints).parallel().forEach(g -> {
                double sum = 0.0;
                for (int i = 0; i < n; i++)
                    sum += w[i] * gaussianKernel((grid[g] - Y[i]) / hy);
                density[g] = sum / hy;
            });
        } else {
            for (int g = 0; g < gridPoints; g++) {
                double sum = 0.0;
                for (int i = 0; i < n; i++)
                    sum += w[i] * gaussianKernel((grid[g] - Y[i]) / hy);
                density[g] = sum / hy;
            }
        }
        return new ConditionalResult1D(grid, density, w,
                targets != null ? targets : new double[0], hy);
    }

    // -----------------------------------------------------------------------
    // Batch bivariate — all pairs, shared weights, parallel pairs
    // -----------------------------------------------------------------------

    /**
     * Computes the conditional univariate result based on the provided data and weights.
     *
     * @param Y       the array representing the target variable for the univariate computation.
     * @param weights the array of weights associated with the target variable values.
     * @return the result of the conditional univariate computation encapsulated in a ConditionalResult1D object.
     */
    public ConditionalResult1D conditionalUnivariate(double[] Y, double[] weights) {
        return conditionalUnivariate(Y, weights, null, 256, -1, false);
    }

    /**
     * Computes a conditional univariate result based on the provided parameters.
     *
     * @param Y      The array of dependent variable values.
     * @param target The target value used to condition the univariate computation.
     * @return A {@code ConditionalResult1D} object containing the result of the conditional univariate analysis.
     */
    public ConditionalResult1D conditionalUnivariate(double[] Y, double target) {
        return conditionalUnivariate(Y, null, new double[]{target}, 256, -1, false);
    }

    // -----------------------------------------------------------------------
    // Correlation profile (sweep one variable)
    // -----------------------------------------------------------------------

    /**
     * Computes the conditional univariate result based on the provided data and parameters.
     *
     * @param Y          the array of observed data points for the dependent variable.
     * @param targets    the array of target values for which the conditional univariate
     *                   computation will be performed.
     * @param gridPoints the number of grid points to use in the computation.
     * @param yBandwidth the bandwidth parameter for the kernel density estimation.
     * @return a ConditionalResult1D object representing the computed conditional univariate result.
     */
    public ConditionalResult1D conditionalUnivariate(double[] Y, double[] targets,
                                                     int gridPoints, double yBandwidth) {
        return conditionalUnivariate(Y, null, targets, gridPoints, yBandwidth, false);
    }

    /**
     * Computes the conditional bivariate density estimate for the given data points.
     * This method uses kernel density estimation to generate a grid-based density representation.
     *
     * @param Y1 An array of double values representing the first dimension of the data points.
     * @param Y2 An array of double values representing the second dimension of the data points.
     * @param weights An optional array of weights for the data points. If null, weights will be computed internally.
     * @param targets An optional array of target values for conditioning. If null, no conditioning will be applied.
     * @param gridSize The number of grid points to compute along each dimension of the density grid.
     * @param h1 The bandwidth for the kernel in the first dimension. If less than or equal to 0, it will be calculated automatically.
     * @param h2 The bandwidth for the kernel in the second dimension. If less than or equal to 0, it will be calculated automatically.
     * @param parallel A boolean flag indicating whether to compute the density using parallel processing.
     * @return A {@code ConditionalResult2D} object containing the computed density grid, grids along each dimension,
     *         weights, targets, and applied bandwidths.
     * @throws IllegalArgumentException If the lengths of {@code Y1} and {@code Y2} do not match.
     */
    public ConditionalResult2D conditionalBivariate(
            double[] Y1, double[] Y2,
            double[] weights, double[] targets,
            int gridSize, double h1, double h2,
            boolean parallel) {

        checkResponseLength(Y1);
        if (Y2.length != n) throw new IllegalArgumentException("Y1/Y2 length mismatch");

        double[] w = weights != null ? weights : conditioningWeights(targets);
        double bh1 = h1 > 0 ? h1 : silverman1DWeighted(Y1, w);
        double bh2 = h2 > 0 ? h2 : silverman1DWeighted(Y2, w);

        double[] grid1 = linspace(min(Y1), max(Y1), gridSize);
        double[] grid2 = linspace(min(Y2), max(Y2), gridSize);
        double[][] density = new double[gridSize][gridSize];

        if (parallel) {
            ForkJoinPool.commonPool().invoke(
                    new GridTask(Y1, Y2, w, grid1, grid2, density, bh1, bh2,
                            0, gridSize));
        } else {
            fillGrid(Y1, Y2, w, grid1, grid2, density, bh1, bh2, 0, gridSize);
        }

        return new ConditionalResult2D(grid1, grid2, density, w,
                targets != null ? targets : new double[0], bh1, bh2);
    }

    /**
     * Computes a conditional bivariate result using the provided data arrays and weights.
     *
     * @param Y1      the first array of data points
     * @param Y2      the second array of data points
     * @param weights the weights corresponding to each data point
     * @return a ConditionalResult2D representing the computed bivariate result
     */
    public ConditionalResult2D conditionalBivariate(
            double[] Y1, double[] Y2, double[] weights) {
        return conditionalBivariate(Y1, Y2, weights, null, 64, -1, -1, false);
    }

    // -----------------------------------------------------------------------
    // ForkJoin grid task
    // -----------------------------------------------------------------------

    /**
     * Computes a conditional bivariate result in parallel given input data arrays and weights.
     *
     * @param Y1      the first array of input values
     * @param Y2      the second array of input values
     * @param weights the weights associated with the input values
     * @return the conditional bivariate result as an instance of ConditionalResult2D
     */
    public ConditionalResult2D conditionalBivariateParallel(
            double[] Y1, double[] Y2, double[] weights) {
        return conditionalBivariate(Y1, Y2, weights, null, 64, -1, -1, true);
    }

    /**
     * Computes a conditional bivariate result based on the provided input arrays and target value.
     *
     * @param Y1     the first array of data points.
     * @param Y2     the second array of data points.
     * @param target the target value used for conditioning the computation.
     * @return an instance of ConditionalResult2D representing the computed conditional bivariate result.
     */
    public ConditionalResult2D conditionalBivariate(
            double[] Y1, double[] Y2, double target) {
        return conditionalBivariate(Y1, Y2, null, new double[]{target},
                64, -1, -1, false);
    }

    // -----------------------------------------------------------------------
    // Internal kernel machinery
    // -----------------------------------------------------------------------

    /**
     * Computes the conditional bivariate distribution for the given data and targets.
     *
     * @param Y1       the first array of random variable observations
     * @param Y2       the second array of random variable observations
     * @param targets  the target values for conditioning
     * @param gridSize the size of the grid used in the computation
     * @param h1       the bandwidth parameter for the first variable
     * @param h2       the bandwidth parameter for the second variable
     * @return a ConditionalResult2D object containing the computed conditional bivariate distribution
     */
    public ConditionalResult2D conditionalBivariate(
            double[] Y1, double[] Y2, double[] targets,
            int gridSize, double h1, double h2) {
        return conditionalBivariate(Y1, Y2, null, targets, gridSize, h1, h2, false);
    }

    /**
     * Evaluates p(Yi, Yj | X = targets) for every (i,j) pair in the supplied
     * list, using a single shared weight vector. Pairs are evaluated in parallel
     * via the common ForkJoinPool; the grid inner loop of each pair is also
     * parallelised when gridSize is large enough to justify it.
     * <p>
     * Typical plot-matrix usage:
     * <pre>
     *   double[] w = kde.computeWeights(sliderTargets);
     *
     *   List&lt;int[]&gt; pairs = new ArrayList&lt;&gt;();
     *   for (int i = 0; i &lt; p; i++)
     *       for (int j = i+1; j &lt; p; j++)
     *           pairs.add(new int[]{ i, j });
     *
     *   ConditionalResult2D[] results = kde.batchBivariate(data, pairs, w, 64);
     * </pre>
     *
     * @param data     [nVars][n] — all response variables
     * @param pairs    list of {i, j} index pairs into data
     * @param weights  precomputed from computeWeights()
     * @param gridSize grid resolution per axis
     * @return results in the same order as pairs
     */
    public ConditionalResult2D[] batchBivariate(
            double[][] data, List<int[]> pairs, double[] weights, int gridSize) {

        ConditionalResult2D[] results = new ConditionalResult2D[pairs.size()];

        // Parallel stream over pairs; grid inner loop also parallelised per pair
        // via ForkJoin (nested parallelism uses the same pool but different tasks).
        IntStream.range(0, pairs.size()).parallel().forEach(p -> {
            int[] pair = pairs.get(p);
            results[p] = conditionalBivariate(
                    data[pair[0]], data[pair[1]],
                    weights, null, gridSize, -1, -1, true);
        });

        return results;
    }

    // -----------------------------------------------------------------------
    // Bandwidth selection
    // -----------------------------------------------------------------------

    /**
     * Convenience form: builds all upper-triangle pairs from data automatically.
     *
     * @param data     [nVars][n]
     * @param weights  precomputed from computeWeights()
     * @param gridSize grid resolution
     * @return BatchResult containing the pair index list and density results
     */
    public BatchResult batchBivariateAllPairs(
            double[][] data, double[] weights, int gridSize) {

        int nVars = data.length;
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < nVars; i++)
            for (int j = i + 1; j < nVars; j++)
                pairs.add(new int[]{i, j});

        ConditionalResult2D[] results = batchBivariate(data, pairs, weights, gridSize);
        return new BatchResult(pairs, results, nVars);
    }

    /**
     * Computes a conditional correlation profile between two response variables (Y1 and Y2)
     * while sweeping through a range of values for a specified conditional variable.
     * The correlation is calculated for each step in the sweep.
     *
     * @param Y1 the first response variable array; must have the same length as Y2.
     * @param Y2 the second response variable array; must have the same length as Y1.
     * @param fixedTargets an array of fixed values for the conditional variables, except for the swept variable.
     * @param sweepIndex the index of the conditional variable to be swept over.
     * @param nSteps the number of steps or points in the sweep range.
     * @return a 2D array where each row contains the swept value and the corresponding weighted correlation value.
     *         The first column contains the swept variable values, and the second column contains the correlation values.
     * @throws IllegalArgumentException if the lengths of Y1 and Y2 do not match, or if fixedTargets contains invalid inputs.
     */
    public double[][] conditionalCorrelationProfile(
            double[] Y1, double[] Y2,
            double[] fixedTargets, int sweepIndex, int nSteps) {

        checkResponseLength(Y1);
        if (Y2.length != n) throw new IllegalArgumentException("Y1/Y2 length mismatch");
        checkTargets(fixedTargets);

        double[] swept = condVars[sweepIndex];
        double[] targets = Arrays.copyOf(fixedTargets, fixedTargets.length);
        double[] steps = linspace(min(swept), max(swept), nSteps);
        double[][] profile = new double[nSteps][2];

        for (int s = 0; s < nSteps; s++) {
            targets[sweepIndex] = steps[s];
            double[] w = conditioningWeights(targets);
            profile[s][0] = steps[s];
            profile[s][1] = weightedCorrelation(Y1, Y2, w);
        }
        return profile;
    }

    // -----------------------------------------------------------------------
    // Statistics helpers
    // -----------------------------------------------------------------------

    /**
     * Computes the conditional correlation profile of two given time series over a specified
     * number of steps. This method assumes there is only one conditioning variable.
     *
     * @param Y1     the first time series array
     * @param Y2     the second time series array
     * @param nSteps the number of steps for the correlation profile
     * @return a 2D array representing the computed conditional correlation profile
     * @throws IllegalArgumentException if more than one conditioning variable is used
     */
    public double[][] conditionalCorrelationProfile(
            double[] Y1, double[] Y2, int nSteps) {
        if (nCond != 1)
            throw new IllegalArgumentException(
                    "Use the sweepIndex overload for more than one conditioning variable.");
        return conditionalCorrelationProfile(Y1, Y2, new double[]{0}, 0, nSteps);
    }

    /**
     * Computes the conditional correlation profile between two input time series arrays.
     *
     * @param Y1 the first time series array, representing a sequence of numerical values
     * @param Y2 the second time series array, representing a sequence of numerical values
     * @return a 2D array where each row contains the computed conditional correlation values
     * for the corresponding lag or condition
     */
    public double[][] conditionalCorrelationProfile(double[] Y1, double[] Y2) {
        return conditionalCorrelationProfile(Y1, Y2, 100);
    }

    /**
     * Sequential grid fill for rows [rowLo, rowHi).
     */
    private void fillGrid(double[] Y1, double[] Y2, double[] w,
                          double[] grid1, double[] grid2, double[][] density,
                          double bh1, double bh2, int rowLo, int rowHi) {
        int gs = grid2.length;
        for (int i1 = rowLo; i1 < rowHi; i1++) {
            for (int i2 = 0; i2 < gs; i2++) {
                double sum = 0.0;
                for (int i = 0; i < n; i++) {
                    sum += w[i]
                            * gaussianKernel((grid1[i1] - Y1[i]) / bh1)
                            * gaussianKernel((grid2[i2] - Y2[i]) / bh2);
                }
                density[i1][i2] = sum / (bh1 * bh2);
            }
        }
    }

    private double[] conditioningWeights(double[] targets) {
        double[] w = new double[n];
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            double prod = 1.0;
            for (int k = 0; k < nCond; k++)
                prod *= gaussianKernel(
                        (condVars[k][i] - targets[k]) / condBandwidths[k]);
            w[i] = prod;
            total += prod;
        }
        if (total < 1e-300)
            throw new IllegalStateException(
                    "All conditioning weights are effectively zero. " +
                            "Target may be outside the data range, or bandwidths are too narrow.");
        for (int i = 0; i < n; i++) w[i] /= total;
        return w;
    }

    private void checkResponseLength(double[] Y) {
        if (Y.length != n)
            throw new IllegalArgumentException(
                    "Response length " + Y.length + " != data length " + n);
    }

    private void checkTargets(double[] targets) {
        if (targets.length != nCond)
            throw new IllegalArgumentException(
                    "targets.length=" + targets.length + " but nCond=" + nCond);
    }

    /**
     * Represents the result of a conditional density estimation in a one-dimensional space.
     * This class encapsulates the computed grid points, density values, weights, conditional targets,
     * and the bandwidth used in the estimation process. It provides methods for analyzing the resulting
     * density, such as finding the mode or integrating over the density function.
     */
    public static class ConditionalResult1D {
        /**
         * The grid points used in the density estimation.
         */
        public final double[] grid;
        /**
         * The density values at the grid points.
         */
        public final double[] density;
        /**
         * The weights associated with each data point.
         */
        public final double[] weights;
        /**
         * The conditional targets used in the density estimation.
         */
        public final double[] condTargets;
        /**
         * The bandwidth used in the density estimation.
         */
        public final double bandwidth;

        /**
         * Constructs a ConditionalResult1D instance representing the result of
         * a conditional density estimation in a one-dimensional space.
         *
         * @param grid        The grid points used in the density estimation.
         * @param density     The density values at the specified grid points.
         * @param weights     The weights associated with each data point.
         * @param condTargets The conditional targets used in the density estimation.
         * @param bandwidth   The bandwidth utilized during the density estimation process.
         */
        ConditionalResult1D(double[] grid, double[] density, double[] weights,
                            double[] condTargets, double bandwidth) {
            this.grid = grid;
            this.density = density;
            this.weights = weights;
            this.condTargets = condTargets;
            this.bandwidth = bandwidth;
        }

        /**
         * Computes the mode of the density function. The mode is the grid point corresponding
         * to the highest density value.
         *
         * @return The grid point where the density reaches its maximum value.
         */
        public double mode() {
            int best = 0;
            for (int i = 1; i < density.length; i++)
                if (density[i] > density[best]) best = i;
            return grid[best];
        }

        /**
         * Integrates the density function over the grid using the trapezoidal rule.
         *
         * @return The integral of the density function over the grid.
         */
        public double integrate() {
            double sum = 0.0, dx = grid[1] - grid[0];
            for (int i = 0; i < density.length - 1; i++)
                sum += 0.5 * (density[i] + density[i + 1]) * dx;
            return sum;
        }
    }

    // -----------------------------------------------------------------------
    // Result types
    // -----------------------------------------------------------------------

    /**
     * Represents the result of a 2D conditional density estimation.
     * This class encapsulates the data required for evaluating and analyzing
     * the conditional density within a defined grid of two variables.
     */
    public static class ConditionalResult2D {
        /**
         * The grid points used in the density estimation in the first dimension.
         */
        public final double[] grid1;
        /**
         * The grid points used in the density estimation in the second dimension.
         */
        public final double[] grid2;
        /**
         * The density values at the specified grid points.
         */
        public final double[][] density;   // [i1][i2]
        /**
         * The weights associated with each data point.
         */
        public final double[] weights;
        /**
         * The conditional targets used in the density estimation.
         */
        public final double[] condTargets;
        /**
         * The bandwidths used in the density estimation in the first and second dimensions.
         */
        public final double bandwidth1;
        /**
         * The bandwidths used in the density estimation in the first and second dimensions.
         */
        public final double bandwidth2;

        /**
         * Constructs a ConditionalResult2D instance with the specified parameters.
         *
         * @param grid1       The grid points used in the density estimation for the first dimension.
         * @param grid2       The grid points used in the density estimation for the second dimension.
         * @param density     The density values at the specified grid points.
         * @param weights     The weights associated with each data point.
         * @param condTargets The conditional targets used in the density estimation process.
         * @param bandwidth1  The bandwidth used in the density estimation for the first dimension.
         * @param bandwidth2  The bandwidth used in the density estimation for the second dimension.
         */
        ConditionalResult2D(double[] grid1, double[] grid2, double[][] density,
                            double[] weights, double[] condTargets,
                            double bandwidth1, double bandwidth2) {
            this.grid1 = grid1;
            this.grid2 = grid2;
            this.density = density;
            this.weights = weights;
            this.condTargets = condTargets;
            this.bandwidth1 = bandwidth1;
            this.bandwidth2 = bandwidth2;
        }

        /**
         * Computes the marginal probability density for the first dimension (Y1)
         * by integrating the joint density along the second dimension (Y2).
         *
         * @return An array representing the marginal density for the first dimension.
         */
        public double[] marginalY1() {
            double dg2 = grid2[1] - grid2[0];
            double[] marg = new double[grid1.length];
            for (int i1 = 0; i1 < grid1.length; i1++) {
                double sum = 0.0;
                for (int i2 = 0; i2 < grid2.length - 1; i2++)
                    sum += 0.5 * (density[i1][i2] + density[i1][i2 + 1]) * dg2;
                marg[i1] = sum;
            }
            return marg;
        }

        /**
         * Computes the marginal probability density for the second dimension (Y2)
         * by integrating the joint density along the first dimension (Y1).
         *
         * @return An array representing the marginal density for the second dimension.
         */
        public double[] marginalY2() {
            double dg1 = grid1[1] - grid1[0];
            double[] marg = new double[grid2.length];
            for (int i2 = 0; i2 < grid2.length; i2++) {
                double sum = 0.0;
                for (int i1 = 0; i1 < grid1.length - 1; i1++)
                    sum += 0.5 * (density[i1][i2] + density[i1 + 1][i2]) * dg1;
                marg[i2] = sum;
            }
            return marg;
        }
    }

    /**
     * Result of batchBivariateAllPairs(): provides indexed lookup so callers
     * don't have to track which result corresponds to which pair.
     */
    public static class BatchResult {
        private final List<int[]> pairs;
        private final ConditionalResult2D[] results;
        private final int nVars;

        /**
         * Constructs a BatchResult instance that encapsulates the results of a batch operation
         * performed on pairs of variable indices, along with metadata about the number of variables.
         *
         * @param pairs   A list of integer arrays, where each array represents a pair of variable indices.
         * @param results An array of ConditionalResult2D objects that correspond to the results for each pair.
         * @param nVars   The total number of variables involved in the batch operation.
         */
        BatchResult(List<int[]> pairs, ConditionalResult2D[] results, int nVars) {
            this.pairs = pairs;
            this.results = results;
            this.nVars = nVars;
        }

        /**
         * Retrieves a {@code ConditionalResult2D} object associated with the specified pair of indices.
         * If the input indices are not ordered (i.e., i > j), the method swaps the values
         * to ensure proper lookup. Throws an exception if the pair is not found.
         *
         * @param i The first index of the pair.
         * @param j The second index of the pair.
         * @return The {@code ConditionalResult2D} object corresponding to the specified pair of indices.
         * @throws IllegalArgumentException if the pair (i, j) cannot be found in the list of pairs.
         */
        public ConditionalResult2D get(int i, int j) {
            if (i > j) {
                int tmp = i;
                i = j;
                j = tmp;
            }
            for (int p = 0; p < pairs.size(); p++) {
                int[] pair = pairs.get(p);
                if (pair[0] == i && pair[1] == j) return results[p];
            }
            throw new IllegalArgumentException("Pair (" + i + "," + j + ") not found");
        }

        /**
         * Returns the list of variable index pairs involved in the batch operation.
         *
         * @return A list of integer arrays, where each array represents a pair of variable indices.
         */
        public List<int[]> pairs() {
            return pairs;
        }

        /**
         * Returns the array of {@code ConditionalResult2D} objects that represent the results
         * of the 2D conditional density estimations for all variable pairs in the batch operation.
         *
         * @return An array of {@code ConditionalResult2D} objects corresponding to the computations
         * performed for each pair of variables.
         */
        public ConditionalResult2D[] results() {
            return results;
        }

        /**
         * Returns the total number of variables involved in the batch operation.
         *
         * @return The number of variables.
         */
        public int nVars() {
            return nVars;
        }
    }

    /**
     * Fills rows [rowLo, rowHi) of the density grid, splitting recursively.
     */
    private class GridTask extends RecursiveAction {
        private final double[] Y1, Y2, w, grid1, grid2;
        private final double[][] density;
        private final double bh1, bh2;
        private final int rowLo, rowHi;

        GridTask(double[] Y1, double[] Y2, double[] w,
                 double[] grid1, double[] grid2, double[][] density,
                 double bh1, double bh2, int rowLo, int rowHi) {
            this.Y1 = Y1;
            this.Y2 = Y2;
            this.w = w;
            this.grid1 = grid1;
            this.grid2 = grid2;
            this.density = density;
            this.bh1 = bh1;
            this.bh2 = bh2;
            this.rowLo = rowLo;
            this.rowHi = rowHi;
        }

        @Override
        protected void compute() {
            if (rowHi - rowLo <= PARALLEL_THRESHOLD) {
                fillGrid(Y1, Y2, w, grid1, grid2, density, bh1, bh2, rowLo, rowHi);
            } else {
                int mid = (rowLo + rowHi) >>> 1;
                invokeAll(
                        new GridTask(Y1, Y2, w, grid1, grid2, density, bh1, bh2, rowLo, mid),
                        new GridTask(Y1, Y2, w, grid1, grid2, density, bh1, bh2, mid, rowHi));
            }
        }
    }
}
