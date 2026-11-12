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

package edu.cmu.tetrad.search.lcdnod;

import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Maximum-likelihood fit of a one-factor measurement model to a single covariance matrix: each latent has a disjoint
 * block of pure indicators, the latents are freely correlated, and the indicators have independent residuals. This is
 * the per-domain parameter estimation step of Phase 2 of LCD-NOD (Dong, Dai, Ng, Spirtes, and Zhang, "Identifying
 * Partially Observed Causal Models from Heterogeneous/Nonstationary Data", ICML 2026); it plays the role of the MLE
 * the paper describes in its Section C.3, in place of the closed forms of its Section A.7, which can fail on real data
 * when a square root's argument is negative.
 * <p>
 * The model is x = Lambda eta + e with Var(eta) = Psi (free, positive definite), Var(e) = diag(theta), and Lambda
 * block-structured (one nonzero per row). During optimization the first indicator of each block has its loading fixed
 * at 1 and Psi is parameterized by a Cholesky factor with exponentiated diagonal, so the problem has no flat
 * directions; the residual variances are exponentiated. The objective is the ML discrepancy log|Sigma| +
 * tr(S Sigma^-1), minimized by L-BFGS with an analytic gradient from a closed-form start (ratios of covariances, with
 * medians over the available choices). After the fit the latents are rescaled to unit variance, so the reported
 * loadings are in the "unit latent" convention: for each latent only the scale of its loadings is arbitrary, and the
 * residual variances are identified outright. A block with a single indicator is treated as an observed "latent": its
 * loading is 1 and its residual variance is fixed at a negligible fraction of the indicator's variance.
 * <p>
 * Nothing here depends on sample size; the fit is to whatever covariance matrix is handed in.
 *
 * @author josephramsey
 */
public final class OneFactorFit {

    /**
     * Fraction of an indicator's variance used as the fixed residual variance of a singleton block.
     */
    private static final double SINGLETON_UNIQUE_FRACTION = 1e-6;

    /**
     * Smallest residual variance allowed in the closed-form start, as a fraction of the indicator's variance.
     */
    private static final double MIN_START_UNIQUE_FRACTION = 0.05;

    private final int[][] blocks;
    private final int p;
    private final int k;
    private final int[] blockOf;
    private final boolean[] anchor;
    private final boolean[] freeUnique;
    private final int numParams;

    private double[] loadings;
    private double[] uniques;
    private SimpleMatrix latentCorr;
    private double[] rawParams;
    private double discrepancy = Double.NaN;
    private boolean converged;

    private OneFactorFit(int[][] blocks) {
        this.blocks = blocks;
        this.k = blocks.length;
        int count = 0;
        for (int[] b : blocks) count += b.length;
        this.p = count;
        this.blockOf = new int[p];
        Arrays.fill(blockOf, -1);
        this.anchor = new boolean[p];
        this.freeUnique = new boolean[p];
        for (int i = 0; i < k; i++) {
            if (blocks[i].length == 0) throw new IllegalArgumentException("Block " + i + " is empty.");
            for (int j = 0; j < blocks[i].length; j++) {
                int a = blocks[i][j];
                if (a < 0 || a >= p || blockOf[a] >= 0) {
                    throw new IllegalArgumentException("Blocks must partition 0.." + (p - 1) + " with no repeats; "
                                                       + "offending index " + a + ".");
                }
                blockOf[a] = i;
                anchor[a] = j == 0;
                freeUnique[a] = blocks[i].length > 1;
            }
        }
        this.numParams = (p - k) + k + k * (k - 1) / 2 + p;
    }

    /**
     * Fits the model to the given covariance matrix.
     *
     * @param cov    the covariance (or correlation) matrix over the indicators; its row order is the index space of
     *               the blocks.
     * @param blocks for each latent, the indices of its indicators in the matrix; the blocks must partition the rows
     *               of the matrix, and the first index of each block is the identification anchor during
     *               optimization (which does not affect the reported, unit-latent, estimates).
     * @return the fit.
     */
    public static OneFactorFit fit(SimpleMatrix cov, int[][] blocks) {
        return fit(cov, blocks, null);
    }

    /**
     * Fits the model to the given covariance matrix, starting from the raw parameters of an earlier fit on a similar
     * matrix (a bootstrap resample, say) when given.
     *
     * @param cov       the covariance matrix over the indicators.
     * @param blocks    the blocks; see {@link #fit(SimpleMatrix, int[][])}.
     * @param warmStart raw parameters from {@link #getRawParams()} of a fit with the same blocks, or null for the
     *                  closed-form start.
     * @return the fit.
     */
    public static OneFactorFit fit(SimpleMatrix cov, int[][] blocks, double[] warmStart) {
        OneFactorFit f = new OneFactorFit(blocks);
        if (cov.getNumRows() != f.p || cov.getNumCols() != f.p) {
            throw new IllegalArgumentException("Covariance matrix is " + cov.getNumRows() + "x" + cov.getNumCols()
                                               + " but the blocks cover " + f.p + " indicators.");
        }
        double[][] s = new double[f.p][f.p];
        for (int i = 0; i < f.p; i++) for (int j = 0; j < f.p; j++) s[i][j] = cov.get(i, j);
        f.run(s, warmStart);
        return f;
    }

    /**
     * The loadings in the unit-latent convention, indexed like the rows of the covariance matrix.
     *
     * @return the loadings.
     */
    public double[] getLoadings() {
        return loadings.clone();
    }

    /**
     * The residual (unique) variances, indexed like the rows of the covariance matrix.
     *
     * @return the residual variances.
     */
    public double[] getUniques() {
        return uniques.clone();
    }

    /**
     * The latent covariance matrix in the unit-latent convention, which is a correlation matrix, in block order.
     *
     * @return the latent correlation matrix.
     */
    public SimpleMatrix getLatentCorrelation() {
        return latentCorr.copy();
    }

    /**
     * The ML discrepancy log|Sigma| + tr(S Sigma^-1) - log|S| - p at the solution.
     *
     * @return the discrepancy.
     */
    public double getDiscrepancy() {
        return discrepancy;
    }

    /**
     * Whether the optimizer met its convergence criterion (rather than its iteration cap).
     *
     * @return true if converged.
     */
    public boolean isConverged() {
        return converged;
    }

    /**
     * The raw parameter vector at the solution, for warm-starting another fit with the same blocks.
     *
     * @return the raw parameters.
     */
    public double[] getRawParams() {
        return rawParams.clone();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private void run(double[][] s, double[] warmStart) {
        double[] x = warmStart != null && warmStart.length == numParams ? warmStart.clone() : closedFormStart(s);

        Workspace w = new Workspace();
        double[] g = new double[numParams];
        double f = objective(x, s, w, g);
        if (!Double.isFinite(f)) {
            x = closedFormStart(s);
            f = objective(x, s, w, g);
        }

        f = lbfgs(x, f, g, s, w);

        this.rawParams = x;
        unpack(x, w);
        this.converged = w.converged;

        // Unit-latent normalization.
        double[] d = new double[k];
        for (int i = 0; i < k; i++) d[i] = Math.sqrt(w.psi[i][i]);
        this.loadings = new double[p];
        for (int a = 0; a < p; a++) this.loadings[a] = w.lambda[a] * d[blockOf[a]];
        this.uniques = w.theta.clone();
        this.latentCorr = new SimpleMatrix(k, k);
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                this.latentCorr.set(i, j, w.psi[i][j] / (d[i] * d[j]));
            }
        }

        double logDetS = logDet(s);
        this.discrepancy = Double.isFinite(logDetS) ? f - logDetS - p : f;
    }

    /**
     * Closed-form start: loadings as median ratios of covariances with third variables, latent covariances as median
     * ratios of cross-block covariances to loading products, residuals as the remainder (floored), then Psi shrunk
     * toward its diagonal until positive definite.
     */
    private double[] closedFormStart(double[][] s) {
        double[] lambda = new double[p];
        for (int i = 0; i < k; i++) {
            int a0 = blocks[i][0];
            lambda[a0] = 1.0;
            for (int j = 1; j < blocks[i].length; j++) {
                int b = blocks[i][j];
                List<Double> ratios = new ArrayList<>();
                for (int z = 0; z < p; z++) {
                    if (z == a0 || z == b) continue;
                    double saz = s[a0][z];
                    double scale = Math.sqrt(Math.max(1e-300, s[a0][a0] * s[z][z]));
                    if (Math.abs(saz) < 0.05 * scale) continue;
                    ratios.add(s[b][z] / saz);
                }
                lambda[b] = ratios.isEmpty() ? 1.0 : median(ratios);
                if (!Double.isFinite(lambda[b]) || lambda[b] == 0) lambda[b] = 1.0;
            }
        }

        double[][] psi = new double[k][k];
        for (int i = 0; i < k; i++) {
            if (blocks[i].length == 1) {
                int a = blocks[i][0];
                psi[i][i] = s[a][a] * (1 - SINGLETON_UNIQUE_FRACTION);
            } else {
                List<Double> vals = new ArrayList<>();
                for (int u = 0; u < blocks[i].length; u++) {
                    for (int v = u + 1; v < blocks[i].length; v++) {
                        int a = blocks[i][u], b = blocks[i][v];
                        double r = s[a][b] / (lambda[a] * lambda[b]);
                        if (Double.isFinite(r)) vals.add(r);
                    }
                }
                double est = vals.isEmpty() ? Double.NaN : median(vals);
                int a0 = blocks[i][0];
                if (!Double.isFinite(est) || est <= 0) est = 0.5 * s[a0][a0];
                psi[i][i] = Math.min(est, s[a0][a0] * (1 - MIN_START_UNIQUE_FRACTION));
            }
        }
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                List<Double> vals = new ArrayList<>();
                for (int a : blocks[i]) {
                    for (int b : blocks[j]) {
                        double r = s[a][b] / (lambda[a] * lambda[b]);
                        if (Double.isFinite(r)) vals.add(r);
                    }
                }
                double est = vals.isEmpty() ? 0.0 : median(vals);
                // Keep the implied correlation inside (-1, 1).
                double cap = 0.95 * Math.sqrt(psi[i][i] * psi[j][j]);
                est = Math.max(-cap, Math.min(cap, est));
                psi[i][j] = est;
                psi[j][i] = est;
            }
        }

        double[][] chol = null;
        for (int tries = 0; tries < 60; tries++) {
            chol = cholesky(psi);
            if (chol != null) break;
            for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) if (i != j) psi[i][j] *= 0.9;
        }
        if (chol == null) {
            for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) if (i != j) psi[i][j] = 0;
            chol = cholesky(psi);
        }

        double[] theta = new double[p];
        for (int a = 0; a < p; a++) {
            if (!freeUnique[a]) {
                theta[a] = SINGLETON_UNIQUE_FRACTION * s[a][a];
            } else {
                double rem = s[a][a] - lambda[a] * lambda[a] * psi[blockOf[a]][blockOf[a]];
                theta[a] = Math.max(MIN_START_UNIQUE_FRACTION * s[a][a], rem);
            }
        }

        return pack(lambda, chol, theta);
    }

    private static double median(List<Double> vals) {
        double[] arr = new double[vals.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = vals.get(i);
        Arrays.sort(arr);
        int n = arr.length;
        return n % 2 == 1 ? arr[n / 2] : 0.5 * (arr[n / 2 - 1] + arr[n / 2]);
    }

    // --- Parameter packing: free loadings, log-diagonal of the Cholesky factor, strict lower factor entries
    // (row-major), log residual variances.

    private double[] pack(double[] lambda, double[][] chol, double[] theta) {
        double[] x = new double[numParams];
        int t = 0;
        for (int a = 0; a < p; a++) if (!anchor[a]) x[t++] = lambda[a];
        for (int i = 0; i < k; i++) x[t++] = Math.log(Math.max(1e-12, chol[i][i]));
        for (int i = 0; i < k; i++) for (int j = 0; j < i; j++) x[t++] = chol[i][j];
        for (int a = 0; a < p; a++) x[t++] = Math.log(Math.max(1e-300, theta[a]));
        return x;
    }

    private final class Workspace {
        final double[] lambda = new double[p];
        final double[][] chol = new double[k][k];
        final double[][] psi = new double[k][k];
        final double[] theta = new double[p];
        final double[][] sigma = new double[p][p];
        boolean converged;
    }

    private void unpack(double[] x, Workspace w) {
        int t = 0;
        for (int a = 0; a < p; a++) w.lambda[a] = anchor[a] ? 1.0 : x[t++];
        for (int i = 0; i < k; i++) {
            Arrays.fill(w.chol[i], 0);
            w.chol[i][i] = Math.exp(x[t++]);
        }
        for (int i = 0; i < k; i++) for (int j = 0; j < i; j++) w.chol[i][j] = x[t++];
        for (int a = 0; a < p; a++) w.theta[a] = Math.exp(x[t++]);
        for (int i = 0; i < k; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = 0;
                for (int m = 0; m <= j; m++) sum += w.chol[i][m] * w.chol[j][m];
                w.psi[i][j] = sum;
                w.psi[j][i] = sum;
            }
        }
    }

    /**
     * Objective log|Sigma| + tr(S Sigma^-1) and its gradient with respect to the raw parameters. Returns +infinity
     * (gradient untouched) when Sigma is not positive definite.
     */
    private double objective(double[] x, double[][] s, Workspace w, double[] grad) {
        unpack(x, w);
        for (int a = 0; a < p; a++) {
            int i = blockOf[a];
            for (int b = 0; b < p; b++) {
                int j = blockOf[b];
                w.sigma[a][b] = w.lambda[a] * w.lambda[b] * w.psi[i][j] + (a == b ? w.theta[a] : 0);
            }
        }

        double[][] cholSigma = cholesky(w.sigma);
        if (cholSigma == null) return Double.POSITIVE_INFINITY;

        double logDet = 0;
        for (int a = 0; a < p; a++) logDet += 2 * Math.log(cholSigma[a][a]);

        double[][] inv = inverseFromCholesky(cholSigma);

        double trace = 0;
        for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) trace += s[a][b] * inv[b][a];

        double f = logDet + trace;
        if (grad == null) return f;

        // G = Sigma^-1 - Sigma^-1 S Sigma^-1.
        double[][] invS = new double[p][p];
        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) {
                double sum = 0;
                for (int c = 0; c < p; c++) sum += inv[a][c] * s[c][b];
                invS[a][b] = sum;
            }
        }
        double[][] g = new double[p][p];
        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) {
                double sum = 0;
                for (int c = 0; c < p; c++) sum += invS[a][c] * inv[c][b];
                g[a][b] = inv[a][b] - sum;
            }
        }

        // dF/dLambda_a = 2 (G Lambda Psi)_{a, block(a)}; A = Lambda^T G Lambda.
        double[][] gLambda = new double[p][k];
        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) {
                gLambda[a][blockOf[b]] += g[a][b] * w.lambda[b];
            }
        }
        double[] dLambda = new double[p];
        for (int a = 0; a < p; a++) {
            int i = blockOf[a];
            double sum = 0;
            for (int j = 0; j < k; j++) sum += gLambda[a][j] * w.psi[j][i];
            dLambda[a] = 2 * sum;
        }
        double[][] A = new double[k][k];
        for (int a = 0; a < p; a++) {
            for (int j = 0; j < k; j++) {
                A[blockOf[a]][j] += w.lambda[a] * gLambda[a][j];
            }
        }
        // dF/dL = 2 A L (lower triangle).
        double[][] dChol = new double[k][k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = 0;
                for (int m = 0; m < k; m++) sum += A[i][m] * w.chol[m][j];
                dChol[i][j] = 2 * sum;
            }
        }

        int t = 0;
        for (int a = 0; a < p; a++) if (!anchor[a]) grad[t++] = dLambda[a];
        for (int i = 0; i < k; i++) grad[t++] = dChol[i][i] * w.chol[i][i];
        for (int i = 0; i < k; i++) for (int j = 0; j < i; j++) grad[t++] = dChol[i][j];
        for (int a = 0; a < p; a++) grad[t++] = freeUnique[a] ? g[a][a] * w.theta[a] : 0.0;

        return f;
    }

    /**
     * L-BFGS with Armijo backtracking. Fixed parameters have zero gradient and are never moved.
     */
    private double lbfgs(double[] x, double f, double[] g, double[][] s, Workspace w) {
        final int mem = 10;
        final int maxIter = 1000;
        final double gradTol = 1e-6;
        final double fTol = 1e-12;

        List<double[]> sHist = new ArrayList<>();
        List<double[]> yHist = new ArrayList<>();
        List<Double> rhoHist = new ArrayList<>();

        double[] d = new double[numParams];
        double[] xNew = new double[numParams];
        double[] gNew = new double[numParams];
        double[] alphaBuf = new double[mem];

        w.converged = false;

        for (int iter = 0; iter < maxIter; iter++) {
            double gInf = 0;
            for (double v : g) gInf = Math.max(gInf, Math.abs(v));
            if (gInf < gradTol) {
                w.converged = true;
                break;
            }

            // Two-loop recursion.
            System.arraycopy(g, 0, d, 0, numParams);
            int h = sHist.size();
            for (int i = h - 1; i >= 0; i--) {
                double a = rhoHist.get(i) * dot(sHist.get(i), d);
                alphaBuf[i] = a;
                axpy(-a, yHist.get(i), d);
            }
            if (h > 0) {
                double[] sl = sHist.get(h - 1), yl = yHist.get(h - 1);
                double gamma = dot(sl, yl) / dot(yl, yl);
                for (int i = 0; i < numParams; i++) d[i] *= gamma;
            }
            for (int i = 0; i < h; i++) {
                double b = rhoHist.get(i) * dot(yHist.get(i), d);
                axpy(alphaBuf[i] - b, sHist.get(i), d);
            }
            for (int i = 0; i < numParams; i++) d[i] = -d[i];

            double slope = dot(g, d);
            if (!(slope < 0)) {
                // Not a descent direction; restart with steepest descent.
                sHist.clear();
                yHist.clear();
                rhoHist.clear();
                for (int i = 0; i < numParams; i++) d[i] = -g[i];
                slope = dot(g, d);
                if (!(slope < 0)) break;
            }

            double step = 1.0;
            double fNew = Double.POSITIVE_INFINITY;
            boolean accepted = false;
            for (int ls = 0; ls < 60; ls++) {
                for (int i = 0; i < numParams; i++) xNew[i] = x[i] + step * d[i];
                fNew = objective(xNew, s, w, gNew);
                if (Double.isFinite(fNew) && fNew <= f + 1e-4 * step * slope) {
                    accepted = true;
                    break;
                }
                step *= 0.5;
            }
            if (!accepted) {
                if (sHist.isEmpty()) break;
                sHist.clear();
                yHist.clear();
                rhoHist.clear();
                continue;
            }

            double[] sv = new double[numParams];
            double[] yv = new double[numParams];
            for (int i = 0; i < numParams; i++) {
                sv[i] = xNew[i] - x[i];
                yv[i] = gNew[i] - g[i];
            }
            double sy = dot(sv, yv);
            if (sy > 1e-12) {
                if (sHist.size() == mem) {
                    sHist.remove(0);
                    yHist.remove(0);
                    rhoHist.remove(0);
                }
                sHist.add(sv);
                yHist.add(yv);
                rhoHist.add(1.0 / sy);
            }

            double fOld = f;
            System.arraycopy(xNew, 0, x, 0, numParams);
            System.arraycopy(gNew, 0, g, 0, numParams);
            f = fNew;

            if (Math.abs(fOld - f) <= fTol * Math.max(1.0, Math.abs(f))) {
                w.converged = true;
                break;
            }
        }

        return f;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    private static void axpy(double a, double[] x, double[] y) {
        for (int i = 0; i < x.length; i++) y[i] += a * x[i];
    }

    // --- Small dense linear algebra on double[][] (matrices here are at most a few dozen rows).

    /**
     * Lower Cholesky factor, or null if the matrix is not positive definite.
     */
    static double[][] cholesky(double[][] m) {
        int n = m.length;
        double[][] l = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = m[i][j];
                for (int c = 0; c < j; c++) sum -= l[i][c] * l[j][c];
                if (i == j) {
                    if (!(sum > 0) || !Double.isFinite(sum)) return null;
                    l[i][i] = Math.sqrt(sum);
                } else {
                    l[i][j] = sum / l[j][j];
                }
            }
        }
        return l;
    }

    private static double[][] inverseFromCholesky(double[][] l) {
        int n = l.length;
        // Inverse of L.
        double[][] li = new double[n][n];
        for (int i = 0; i < n; i++) {
            li[i][i] = 1.0 / l[i][i];
            for (int j = 0; j < i; j++) {
                double sum = 0;
                for (int c = j; c < i; c++) sum -= l[i][c] * li[c][j];
                li[i][j] = sum / l[i][i];
            }
        }
        // Sigma^-1 = L^-T L^-1.
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = 0;
                for (int c = i; c < n; c++) sum += li[c][i] * li[c][j];
                inv[i][j] = sum;
                inv[j][i] = sum;
            }
        }
        return inv;
    }

    static double logDet(double[][] m) {
        double[][] l = cholesky(m);
        if (l == null) return Double.NaN;
        double sum = 0;
        for (int i = 0; i < l.length; i++) sum += 2 * Math.log(l[i][i]);
        return sum;
    }

    /**
     * Conditional variance of variable i given the variables in the index set c, from a covariance matrix.
     *
     * @param cov the covariance matrix.
     * @param i   the target index.
     * @param c   the conditioning indices (may be empty); must not contain i.
     * @return the conditional variance, or NaN if the conditioning block is singular.
     */
    public static double conditionalVariance(double[][] cov, int i, int[] c) {
        double[] r = regression(cov, i, c);
        return r == null ? Double.NaN : r[c.length];
    }

    /**
     * The population regression of variable i on the variables in the index set c, from a covariance matrix: the
     * coefficients followed by the residual (conditional) variance.
     *
     * @param cov the covariance matrix.
     * @param i   the target index.
     * @param c   the regressor indices (may be empty); must not contain i.
     * @return an array of length c.length + 1 holding the coefficients in the order of c and then the conditional
     * variance, or null if the regressor block is singular.
     */
    public static double[] regression(double[][] cov, int i, int[] c) {
        double[] out = new double[c.length + 1];
        if (c.length == 0) {
            out[0] = cov[i][i];
            return out;
        }
        double[][] ccc = new double[c.length][c.length];
        for (int u = 0; u < c.length; u++) for (int v = 0; v < c.length; v++) ccc[u][v] = cov[c[u]][c[v]];
        double[][] l = cholesky(ccc);
        if (l == null) return null;
        // Solve L z = cov[c, i], then L^T beta = z; the explained variance is z^T z.
        double[] z = new double[c.length];
        for (int u = 0; u < c.length; u++) {
            double sum = cov[c[u]][i];
            for (int v = 0; v < u; v++) sum -= l[u][v] * z[v];
            z[u] = sum / l[u][u];
        }
        double q = 0;
        for (double v : z) q += v * v;
        for (int u = c.length - 1; u >= 0; u--) {
            double sum = z[u];
            for (int v = u + 1; v < c.length; v++) sum -= l[v][u] * out[v];
            out[u] = sum / l[u][u];
        }
        out[c.length] = cov[i][i] - q;
        return out;
    }
}
