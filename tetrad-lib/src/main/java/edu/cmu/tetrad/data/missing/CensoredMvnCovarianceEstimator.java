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

package edu.cmu.tetrad.data.missing;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.Arrays;
import java.util.Random;

/**
 * Estimates the covariance matrix of data in which some values are <i>censored</i> rather than missing: a value at a
 * ceiling (a grade of 100, a detector at its maximum, a score at the top of its scale) is known only to be at least
 * that bound, and a value at a floor to be at most it. Treating such values as observed flattens the top of the
 * distribution and attenuates every correlation the variable takes part in; treating them as missing throws away the
 * information that they were large, and is missing-not-at-random besides, so an EM estimate that assumes MAR is
 * biased. This estimator keeps the information: each censored value is modelled as a normal variable truncated to the
 * side of the bound it is known to be on, conditional on the other values in its row.
 * <p>
 * <b>Method.</b> Stochastic EM (Monte Carlo EM by data augmentation) under a multivariate normal model. The parameters
 * are initialised from {@link EmCovarianceEstimator} with censored cells set missing. Each iteration then (1) sweeps
 * the censored cells, drawing each from its conditional normal given the current values of the other variables in
 * its row, truncated to the censored side of its bound; and (2) re-estimates the mean and covariance from the completed
 * data. The reported covariance is the average of the per-iteration estimates after a burn-in, which damps the Monte
 * Carlo noise of any single completion. Ordinary missing values -- NaN in a column with no bound -- are drawn the same
 * way without truncation, so the estimator handles mixed censored-and-missing data in one pass.
 * <p>
 * <b>Which cells count as censored.</b> In a column with an upper bound, a cell is censored above if its value is at
 * least the bound; with a lower bound, censored below if at most the bound. A column may have both. What a NaN means
 * in a bounded column is a choice, {@link #setMissingAsCensored(boolean)}: by default (false) NaN is an ordinary
 * missing value everywhere, which is right for recorded data such as grades, where a ceiling value is written down as
 * 100 and a missing grade is simply missing. With the flag true, a NaN in a column with an upper bound is read as
 * censored above (or below, with only a lower bound), which is right when the censored values were removed rather
 * than recorded, as in a simulation that deletes values above a threshold. For a column with no bound, NaN is an
 * ordinary missing value either way.
 * <p>
 * <b>What it assumes, and when it is wrong.</b> Multivariate normality of the uncensored latent values. Under Gaussian
 * errors the estimate is essentially exact (correlation errors below 0.01 in simulation at 30 percent censoring);
 * under skewed errors the truncated-normal model is misspecified and the correction is partial, though still a large
 * improvement over MAR-EM. It assumes the bound is known: for a ceiling-truncated grade it is the maximum of the
 * scale, and for a value removed because it exceeded a threshold, the largest value observed is a serviceable proxy.
 * It does <i>not</i> apply to values missing for reasons unrelated to their size, which are MAR and belong to
 * {@link EmCovarianceEstimator}.
 * <p>
 * The pairwise counts reported are of rows on which both variables are observed <i>and uncensored</i>, the
 * conservative choice for an effective sample size; a censored cell carries less information than an observed one and
 * this counts it as none.
 *
 * @author josephramsey
 * @see EmCovarianceEstimator
 */
public final class CensoredMvnCovarianceEstimator {

    private final DataSet dataSet;
    private final double[] upper;
    private final double[] lower;
    private int iterations = 60;
    private int burnIn = 20;
    private long seed = 0L;
    private double ridge = 1e-6;
    private boolean missingAsCensored = false;

    private ICovarianceMatrix result;
    private double[] means;
    private int[][] pairwiseCounts;
    private int censoredCells;
    private int missingCells;
    private int[] censoredByColumn;

    /**
     * Constructs the estimator.
     *
     * @param dataSet the data; continuous; NaN for missing or censored.
     * @param upper   per column, the upper bound at and above which a value is censored, or NaN (or +infinity) for
     *                none.
     * @param lower   per column, the lower bound at and below which a value is censored, or NaN (or -infinity) for
     *                none; may be null for no lower bounds.
     */
    public CensoredMvnCovarianceEstimator(DataSet dataSet, double[] upper, double[] lower) {
        if (dataSet == null) throw new NullPointerException("Data set is null.");
        if (!dataSet.isContinuous()) throw new IllegalArgumentException("Continuous data required.");
        int p = dataSet.getNumColumns();
        if (upper == null) {
            upper = new double[p];
            Arrays.fill(upper, Double.POSITIVE_INFINITY);
        }
        if (lower == null) {
            lower = new double[p];
            Arrays.fill(lower, Double.NEGATIVE_INFINITY);
        }
        if (upper.length != p || lower.length != p) {
            throw new IllegalArgumentException("Bounds must have one entry per column.");
        }
        this.dataSet = dataSet;
        this.upper = upper.clone();
        this.lower = lower.clone();
        for (int j = 0; j < p; j++) {
            if (Double.isNaN(this.upper[j])) this.upper[j] = Double.POSITIVE_INFINITY;
            if (Double.isNaN(this.lower[j])) this.lower[j] = Double.NEGATIVE_INFINITY;
        }
    }

    /**
     * Upper bounds taken as each column's largest observed value, for data whose censored values were removed rather
     * than recorded at the bound; columns with no missing values get no bound.
     *
     * @param dataSet the data.
     * @return the bounds.
     */
    public static double[] upperBoundsFromObservedMaxima(DataSet dataSet) {
        int n = dataSet.getNumRows(), p = dataSet.getNumColumns();
        double[] upper = new double[p];
        for (int j = 0; j < p; j++) {
            double max = Double.NEGATIVE_INFINITY;
            boolean anyMissing = false;
            for (int i = 0; i < n; i++) {
                double v = dataSet.getDouble(i, j);
                if (Double.isNaN(v)) anyMissing = true;
                else max = Math.max(max, v);
            }
            upper[j] = anyMissing ? max : Double.POSITIVE_INFINITY;
        }
        return upper;
    }

    /**
     * Upper bounds set to a single ceiling for every column, for data recorded at the top of a common scale.
     *
     * @param p       the number of columns.
     * @param ceiling the ceiling.
     * @return the bounds.
     */
    public static double[] commonCeiling(int p, double ceiling) {
        double[] upper = new double[p];
        Arrays.fill(upper, ceiling);
        return upper;
    }

    /**
     * Sets the number of stochastic EM iterations (default 60) and the burn-in before averaging (default 20).
     *
     * @param iterations the iterations; at least burnIn + 5.
     * @param burnIn     the burn-in.
     */
    public void setIterations(int iterations, int burnIn) {
        if (iterations < burnIn + 5) throw new IllegalArgumentException("Need at least five iterations past burn-in.");
        this.iterations = iterations;
        this.burnIn = burnIn;
    }

    /**
     * Sets the seed of the draws.
     *
     * @param seed the seed.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Sets how a NaN in a bounded column is read: as censored at that column's bound (true), or as an ordinary missing
     * value (false, the default). See the class comment.
     *
     * @param missingAsCensored the choice.
     */
    public void setMissingAsCensored(boolean missingAsCensored) {
        this.missingAsCensored = missingAsCensored;
    }

    /**
     * Sets the ridge added to the diagonal of each iteration's covariance before inversion (default 1e-6).
     *
     * @param ridge the ridge.
     */
    public void setRidge(double ridge) {
        this.ridge = ridge;
    }

    /**
     * The estimated covariance matrix, computed on first call.
     *
     * @return the matrix, with the data's row count as its sample size.
     */
    public ICovarianceMatrix estimate() {
        if (result == null) compute();
        return result;
    }

    /**
     * The estimated means.
     *
     * @return the means.
     */
    public double[] getMeans() {
        if (result == null) compute();
        return means.clone();
    }

    /**
     * For each pair of columns, the number of rows on which both are observed and uncensored; the diagonal holds the
     * per-column count.
     *
     * @return the counts.
     */
    public int[][] getPairwiseCounts() {
        if (result == null) compute();
        int[][] copy = new int[pairwiseCounts.length][];
        for (int j = 0; j < copy.length; j++) copy[j] = pairwiseCounts[j].clone();
        return copy;
    }

    /**
     * The number of cells treated as censored.
     *
     * @return the count.
     */
    public int getCensoredCells() {
        if (result == null) compute();
        return censoredCells;
    }

    /**
     * Per column, the number of cells treated as censored (above or below).
     *
     * @return the counts.
     */
    public int[] getCensoredCellsByColumn() {
        if (result == null) compute();
        return censoredByColumn.clone();
    }

    /**
     * The bounds in force, after NaN has been read as "no bound".
     *
     * @return upper bounds.
     */
    public double[] getUpperBounds() {
        return upper.clone();
    }

    /**
     * The number of cells treated as ordinary missing values (NaN in an unbounded column).
     *
     * @return the count.
     */
    public int getMissingCells() {
        if (result == null) compute();
        return missingCells;
    }

    // ------------------------------------------------------------------ internals

    private static final int FREE = 0, ABOVE = 1, BELOW = 2, MISSING = 3;

    private void compute() {
        int n = dataSet.getNumRows(), p = dataSet.getNumColumns();
        double[][] x = new double[n][p];
        int[][] kind = new int[n][p];
        censoredCells = 0;
        missingCells = 0;
        censoredByColumn = new int[p];

        // Classify cells and build the missing-marked copy for the MAR-EM start.
        DataSet start = dataSet.copy();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                double v = dataSet.getDouble(i, j);
                boolean hasUpper = upper[j] != Double.POSITIVE_INFINITY;
                boolean hasLower = lower[j] != Double.NEGATIVE_INFINITY;
                int k;
                if (Double.isNaN(v)) {
                    k = !missingAsCensored ? MISSING : hasUpper ? ABOVE : hasLower ? BELOW : MISSING;
                } else if (hasUpper && v >= upper[j]) {
                    k = ABOVE;
                } else if (hasLower && v <= lower[j]) {
                    k = BELOW;
                } else {
                    k = FREE;
                }
                kind[i][j] = k;
                x[i][j] = v;
                if (k != FREE) {
                    start.setDouble(i, j, Double.NaN);
                    if (k == MISSING) missingCells++;
                    else {
                        censoredCells++;
                        censoredByColumn[j]++;
                    }
                }
            }
        }

        // Pairwise uncensored counts.
        pairwiseCounts = new int[p][p];
        for (int i = 0; i < n; i++) {
            for (int a = 0; a < p; a++) {
                if (kind[i][a] != FREE) continue;
                for (int b = a; b < p; b++) {
                    if (kind[i][b] != FREE) continue;
                    pairwiseCounts[a][b]++;
                    if (a != b) pairwiseCounts[b][a]++;
                }
            }
        }

        // Start: MAR-EM with every non-free cell missing.
        EmCovarianceEstimator em = new EmCovarianceEstimator(start);
        em.setRidge(ridge);
        ICovarianceMatrix emCov = em.estimate();
        double[] mu = em.getMeans().clone();
        double[][] sigma = emCov.getMatrix().toArray();

        // Initial completion: censored cells at the bound (or the mean, whichever is on the censored side).
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                switch (kind[i][j]) {
                    case ABOVE -> x[i][j] = Math.max(upper[j], mu[j]);
                    case BELOW -> x[i][j] = Math.min(lower[j], mu[j]);
                    case MISSING -> x[i][j] = mu[j];
                    default -> {
                    }
                }
            }
        }

        Random random = new Random(seed);
        NormalDistribution normal = new NormalDistribution();
        double[][] accS = new double[p][p];
        double[] accMu = new double[p];
        int kept = 0;

        for (int it = 0; it < iterations; it++) {
            double[][] prec = precision(sigma, p);

            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    int k = kind[i][j];
                    if (k == FREE) continue;
                    // Conditional of x_j given the others, from the precision matrix.
                    double cv = 1.0 / prec[j][j];
                    double s = 0.0;
                    for (int c = 0; c < p; c++) if (c != j) s += prec[j][c] * (x[i][c] - mu[c]);
                    double cm = mu[j] - cv * s;
                    double sd = Math.sqrt(Math.max(cv, 1e-12));
                    x[i][j] = switch (k) {
                        case ABOVE -> truncatedAbove(cm, sd, upper[j], random, normal);
                        case BELOW -> -truncatedAbove(-cm, sd, -lower[j], random, normal);
                        default -> cm + sd * random.nextGaussian();
                    };
                }
            }

            // M-step: mean and covariance of the completed data.
            double[] m = new double[p];
            for (double[] row : x) for (int j = 0; j < p; j++) m[j] += row[j];
            for (int j = 0; j < p; j++) m[j] /= n;
            double[][] S = new double[p][p];
            for (double[] row : x) {
                for (int a = 0; a < p; a++) {
                    double da = row[a] - m[a];
                    for (int b = a; b < p; b++) S[a][b] += da * (row[b] - m[b]);
                }
            }
            for (int a = 0; a < p; a++) {
                for (int b = a; b < p; b++) {
                    S[a][b] /= n;
                    S[b][a] = S[a][b];
                }
            }
            mu = m;
            sigma = S;

            if (it >= burnIn) {
                for (int a = 0; a < p; a++) {
                    accMu[a] += mu[a];
                    for (int b = 0; b < p; b++) accS[a][b] += sigma[a][b];
                }
                kept++;
            }
        }

        means = new double[p];
        Matrix out = new Matrix(p, p);
        for (int a = 0; a < p; a++) {
            means[a] = accMu[a] / kept;
            for (int b = 0; b < p; b++) out.set(a, b, accS[a][b] / kept);
        }
        result = new CovarianceMatrix(dataSet.getVariables(), out, n);
    }

    private double[][] precision(double[][] sigma, int p) {
        Matrix m = new Matrix(p, p);
        for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) m.set(a, b, sigma[a][b] + (a == b ? ridge : 0));
        return m.inverse().toArray();
    }

    /**
     * A draw from N(mean, sd^2) truncated to [bound, infinity): inverse CDF in the body of the distribution, Robert's
     * (1995) exponential rejection sampler in the far tail where the inverse CDF loses precision.
     */
    private static double truncatedAbove(double mean, double sd, double bound, Random random,
                                         NormalDistribution normal) {
        double a = (bound - mean) / sd;
        if (a < 3.0) {
            double lo = normal.cumulativeProbability(a);
            double u = lo + (1.0 - lo) * random.nextDouble();
            u = Math.min(u, 1.0 - 1e-15);
            return mean + sd * normal.inverseCumulativeProbability(u);
        }
        double lambda = (a + Math.sqrt(a * a + 4.0)) / 2.0;
        for (int tries = 0; tries < 1000; tries++) {
            double z = a - Math.log(1.0 - random.nextDouble()) / lambda;
            double rho = Math.exp(-(z - lambda) * (z - lambda) / 2.0);
            if (random.nextDouble() <= rho) return mean + sd * z;
        }
        return mean + sd * a;
    }
}
