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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the              //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program. If not, see <https://www.gnu.org/licenses/>.     //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Linearizing ("un-warping") transform: estimates, for each variable, a monotone
 * Yeo-Johnson transform chosen to make that variable's relations to the other
 * variables as LINEAR as possible, and returns the dataset with all transforms
 * applied (columns standardized).
 *
 * <p>Contrast with the nonparanormal transform: the nonparanormal chooses each
 * variable's monotone map to make its MARGINAL Gaussian, a per-variable decision that
 * never looks at any other variable, and it destroys the marginal skewness that
 * skew-based orientation methods (FASK) rely on. The linearizer chooses each map by
 * looking at the scatterplots -- it minimizes the summed nonlinearity of the
 * variable's pairwise relations -- and alters the marginal only incidentally.</p>
 *
 * <p>Motivation: under a post-nonlinear model X_i = h_i(linear structural part +
 * error), with h_i a monotone distortion (sensor saturation, for instance), each
 * pairwise scatterplot looks only mildly curved, yet residual-based nonlinearity
 * checks flag most pairs. Estimating the inverse warps and applying them restores the
 * linear world: relationships straighten AND the structural skewness contributed by
 * the errors survives, so both linear adjacency methods and skew-based orientation
 * apply to the transformed data. If the warps are exactly of Yeo-Johnson form this
 * recovers the linear model up to affine maps; general monotone warps are only
 * approximated; and nonlinearity in the LINKS (rather than per-variable distortion)
 * cannot be removed by any per-variable transform -- rerunning a nonlinearity check
 * on the output is the diagnostic for which situation obtains, and the per-variable
 * before/after nonlinearity measures are reported for exactly that purpose.</p>
 *
 * <p>Method: nonlinearity of an ordered pair (u -&gt; v) is measured as the R-squared
 * gain of a cubic polynomial regression of v on u over the linear regression. Each
 * variable's objective is the sum of the two directed nonlinearities over its
 * neighbor set (variables with absolute Spearman correlation at least 0.15, which is
 * invariant under monotone transforms and so computed once). The Yeo-Johnson
 * parameters are fit by coordinate descent over variables, each step minimizing the
 * variable's objective over a grid of lambda values, with the current transforms of
 * the neighbors in place; two to four sweeps suffice. Lambda = 1 is the identity, so
 * the method can only improve on doing nothing, up to grid resolution.</p>
 *
 * @author josephramsey
 * @see DataTransforms#getNonparanormalTransformed(DataSet)
 */
public final class Linearizer {

    /**
     * Grid of candidate Yeo-Johnson lambdas. Includes 1.0 (identity) exactly.
     */
    private static final double[] LAMBDA_GRID;

    static {
        List<Double> grid = new ArrayList<>();
        for (double l = -2.0; l <= 3.0 + 1e-9; l += 0.25) grid.add(l);
        LAMBDA_GRID = new double[grid.size()];
        for (int i = 0; i < grid.size(); i++) LAMBDA_GRID[i] = grid.get(i);
    }

    /**
     * Minimum absolute Spearman correlation for a pair to enter a variable's
     * objective.
     */
    private static final double NEIGHBOR_RHO = 0.15;

    /**
     * Number of coordinate-descent sweeps.
     */
    private static final int SWEEPS = 3;

    private final DataSet dataSet;

    /**
     * Fitted lambda per variable (1.0 = identity), available after linearize().
     */
    private double[] lambdas;

    /**
     * Mean directed nonlinearity per variable before and after, available after
     * linearize().
     */
    private double[] nlBefore;
    private double[] nlAfter;

    /**
     * Constructs a linearizer for the given continuous dataset.
     *
     * @param dataSet the dataset to linearize
     */
    public Linearizer(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("Data set must not be null.");
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("The linearizing transform requires a continuous data set.");
        }
        this.dataSet = dataSet;
    }

    /**
     * Runs the transform and returns the linearized dataset (same variables, columns
     * standardized). Fitted lambdas and before/after nonlinearity per variable are
     * available from the getters afterwards, and a summary is written to the log.
     *
     * @return the linearized dataset
     */
    public DataSet linearize() {
        int p = this.dataSet.getNumColumns();
        int n = this.dataSet.getNumRows();
        List<Node> vars = this.dataSet.getVariables();

        // Original standardized columns (x) and current transformed columns (t).
        double[][] x = this.dataSet.getDoubleData().transpose().toArray();
        for (int j = 0; j < p; j++) standardize(x[j]);

        double[][] t = new double[p][];
        this.lambdas = new double[p];
        for (int j = 0; j < p; j++) {
            t[j] = x[j].clone();
            this.lambdas[j] = 1.0;
        }

        // Neighbor sets from Spearman correlations (monotone-invariant; computed once).
        int[][] neighbors = neighborSets(x, p, n);

        this.nlBefore = meanNonlinearity(t, neighbors, p);

        for (int sweep = 0; sweep < SWEEPS; sweep++) {
            boolean changed = false;

            for (int i = 0; i < p; i++) {
                if (neighbors[i].length == 0) continue;

                double bestLambda = this.lambdas[i];
                double bestObj = objective(t, x, i, this.lambdas[i], neighbors[i]);

                for (double lambda : LAMBDA_GRID) {
                    if (lambda == bestLambda) continue;
                    double obj = objective(t, x, i, lambda, neighbors[i]);
                    if (obj < bestObj - 1e-9) {
                        bestObj = obj;
                        bestLambda = lambda;
                    }
                }

                if (bestLambda != this.lambdas[i]) {
                    this.lambdas[i] = bestLambda;
                    t[i] = yeoJohnsonStandardized(x[i], bestLambda);
                    changed = true;
                }
            }

            if (!changed) break;
        }

        this.nlAfter = meanNonlinearity(t, neighbors, p);

        // Log a report.
        DecimalFormat df = new DecimalFormat("0.000");
        TetradLogger log = TetradLogger.getInstance();
        log.log("Linearize (un-warp) transform: per-variable Yeo-Johnson lambdas "
                + "(1.0 = identity) with mean directed nonlinearity (cubic R^2 gain) before -> after.");
        for (int j = 0; j < p; j++) {
            log.log(vars.get(j).getName() + ": lambda = " + df.format(this.lambdas[j])
                    + ", nonlinearity " + df.format(this.nlBefore[j]) + " -> " + df.format(this.nlAfter[j]));
        }

        return new BoxDataSet(new VerticalDoubleDataBox(t), vars);
    }

    /**
     * @return the fitted Yeo-Johnson lambda per variable (1.0 = identity).
     */
    public double[] getLambdas() {
        return this.lambdas == null ? null : this.lambdas.clone();
    }

    /**
     * @return the mean directed nonlinearity per variable before the transform.
     */
    public double[] getNonlinearityBefore() {
        return this.nlBefore == null ? null : this.nlBefore.clone();
    }

    /**
     * @return the mean directed nonlinearity per variable after the transform.
     */
    public double[] getNonlinearityAfter() {
        return this.nlAfter == null ? null : this.nlAfter.clone();
    }

    // ------------ Internals ------------

    /**
     * The objective for variable i at the given lambda: summed directed nonlinearity
     * between candidate t_i and the current transforms of its neighbors.
     */
    private double objective(double[][] t, double[][] x, int i, double lambda, int[] nbrs) {
        double[] ti = lambda == 1.0 ? x[i] : yeoJohnsonStandardized(x[i], lambda);
        double sum = 0.0;
        for (int j : nbrs) {
            sum += nonlinearity(t[j], ti);
            sum += nonlinearity(ti, t[j]);
        }
        return sum;
    }

    private static double[] meanNonlinearity(double[][] t, int[][] neighbors, int p) {
        double[] out = new double[p];
        for (int i = 0; i < p; i++) {
            if (neighbors[i].length == 0) continue;
            double sum = 0.0;
            for (int j : neighbors[i]) {
                sum += nonlinearity(t[j], t[i]);
                sum += nonlinearity(t[i], t[j]);
            }
            out[i] = sum / (2.0 * neighbors[i].length);
        }
        return out;
    }

    private static int[][] neighborSets(double[][] x, int p, int n) {
        double[][] ranks = new double[p][];
        for (int j = 0; j < p; j++) {
            ranks[j] = ranksOf(x[j]);
            standardize(ranks[j]);
        }

        int[][] neighbors = new int[p][];
        for (int i = 0; i < p; i++) {
            List<Integer> nbrs = new ArrayList<>();
            int best = -1;
            double bestRho = -1.0;
            for (int j = 0; j < p; j++) {
                if (j == i) continue;
                double rho = Math.abs(correlation(ranks[i], ranks[j]));
                if (rho >= NEIGHBOR_RHO) nbrs.add(j);
                if (rho > bestRho) {
                    bestRho = rho;
                    best = j;
                }
            }
            if (nbrs.isEmpty() && best >= 0) nbrs.add(best);
            neighbors[i] = new int[nbrs.size()];
            for (int k = 0; k < nbrs.size(); k++) neighbors[i][k] = nbrs.get(k);
        }
        return neighbors;
    }

    /**
     * Directed nonlinearity of regressing v on u: R^2 of the cubic polynomial fit
     * minus R^2 of the linear fit. Both series are assumed standardized.
     */
    private static double nonlinearity(double[] u, double[] v) {
        int n = u.length;

        // Linear R^2 = corr^2.
        double r = correlation(u, v);
        double r2lin = r * r;

        // Cubic fit via normal equations on [1, u, u^2, u^3].
        double[][] xtx = new double[4][4];
        double[] xty = new double[4];
        double[] pow = new double[7]; // sums of u^0..u^6

        for (int i = 0; i < n; i++) {
            double u1 = u[i], u2 = u1 * u1, u3 = u2 * u1;
            pow[0] += 1;
            pow[1] += u1;
            pow[2] += u2;
            pow[3] += u3;
            pow[4] += u2 * u2;
            pow[5] += u2 * u3;
            pow[6] += u3 * u3;
            xty[0] += v[i];
            xty[1] += u1 * v[i];
            xty[2] += u2 * v[i];
            xty[3] += u3 * v[i];
        }

        for (int a = 0; a < 4; a++)
            for (int b = 0; b < 4; b++) xtx[a][b] = pow[a + b];

        double[] betaArr;
        try {
            Matrix inv = new Matrix(xtx).inverse();
            betaArr = new double[4];
            for (int a = 0; a < 4; a++) {
                double s = 0.0;
                for (int b = 0; b < 4; b++) s += inv.get(a, b) * xty[b];
                betaArr[a] = s;
            }
        } catch (Exception e) {
            return 0.0;
        }

        double ssRes = 0.0, ssTot = 0.0;
        double meanV = 0.0;
        for (double vv : v) meanV += vv;
        meanV /= n;

        for (int i = 0; i < n; i++) {
            double u1 = u[i], u2 = u1 * u1, u3 = u2 * u1;
            double fit = betaArr[0] + betaArr[1] * u1 + betaArr[2] * u2 + betaArr[3] * u3;
            double e1 = v[i] - fit;
            ssRes += e1 * e1;
            double e0 = v[i] - meanV;
            ssTot += e0 * e0;
        }

        if (ssTot <= 0) return 0.0;
        double r2cubic = 1.0 - ssRes / ssTot;
        return Math.max(0.0, r2cubic - r2lin);
    }

    /**
     * Yeo-Johnson transform of the (standardized) series at the given lambda,
     * re-standardized. Lambda 1 returns a standardized copy (the YJ identity).
     */
    private static double[] yeoJohnsonStandardized(double[] x, double lambda) {
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            double v = x[i];
            if (v >= 0) {
                if (Math.abs(lambda) < 1e-12) {
                    out[i] = Math.log1p(v);
                } else {
                    out[i] = (Math.pow(v + 1.0, lambda) - 1.0) / lambda;
                }
            } else {
                double tl = 2.0 - lambda;
                if (Math.abs(tl) < 1e-12) {
                    out[i] = -Math.log1p(-v);
                } else {
                    out[i] = -(Math.pow(1.0 - v, tl) - 1.0) / tl;
                }
            }
        }
        standardize(out);
        return out;
    }

    private static double[] ranksOf(double[] x) {
        int n = x.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(x[a], x[b]));

        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && x[idx[j + 1]] == x[idx[i]]) j++;
            double avg = (i + j) / 2.0 + 1.0; // average rank for ties
            for (int k = i; k <= j; k++) ranks[idx[k]] = avg;
            i = j + 1;
        }
        return ranks;
    }

    private static void standardize(double[] x) {
        int n = x.length;
        double mean = 0.0;
        for (double v : x) mean += v;
        mean /= n;
        double var = 0.0;
        for (double v : x) {
            double d = v - mean;
            var += d * d;
        }
        var /= (n - 1);
        double sd = Math.sqrt(var);
        if (sd == 0 || Double.isNaN(sd)) sd = 1.0;
        for (int i = 0; i < n; i++) x[i] = (x[i] - mean) / sd;
    }

    private static double correlation(double[] u, double[] v) {
        int n = u.length;
        double mu = 0, mv = 0;
        for (int i = 0; i < n; i++) {
            mu += u[i];
            mv += v[i];
        }
        mu /= n;
        mv /= n;
        double suv = 0, suu = 0, svv = 0;
        for (int i = 0; i < n; i++) {
            double du = u[i] - mu, dv = v[i] - mv;
            suv += du * dv;
            suu += du * du;
            svv += dv * dv;
        }
        if (suu <= 0 || svv <= 0) return 0.0;
        return suv / Math.sqrt(suu * svv);
    }
}
