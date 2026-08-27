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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.special.Gamma;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.log;

/**
 * Implements the BGe (Bayesian Gaussian equivalent) score of Geiger and Heckerman (1994, 2002), in the corrected form
 * given by Kuipers, Moffa, and Heckerman (2014). This is the Gaussian analog of the BDeu score for discrete data: the
 * local score of a variable given its parents is the log marginal likelihood of the data for that family under a
 * Normal-Wishart prior on the mean and precision of the Gaussian model, so, unlike BIC, no asymptotic penalty is
 * involved. The score is score-equivalent (all DAGs in a Markov equivalence class receive the same total score) and
 * decomposable, and "higher is better," so it can be used directly with FGES, BOSS, GRaSP, and the other score-based
 * searches in Tetrad.
 * <p>
 * For a family Y | Pa with |Pa| = l, N the (effective) sample size, p the number of variables in the data, alpha_mu the
 * prior precision on the mean, and alpha_w the Wishart degrees of freedom, the local score is
 * <pre>
 *   -(N/2) log pi + (1/2) log(alpha_mu / (N + alpha_mu))
 *   + log Gamma((N + alpha_w - p + l + 1)/2) - log Gamma((alpha_w - p + l + 1)/2)
 *   + ((alpha_w - p + l + 1)/2) log|T_{Pa,Y}| - ((alpha_w - p + l)/2) log|T_{Pa}|
 *   - ((N + alpha_w - p + l + 1)/2) log|R_{Pa,Y}| + ((N + alpha_w - p + l)/2) log|R_{Pa}|,
 * </pre>
 * where T_S is the prior scale matrix restricted to S and R_S = T_S + (N - 1) S_S with S the sample covariance matrix.
 * This is p(D_{Pa,Y}) / p(D_{Pa}) with the multivariate gamma ratios telescoped; see Kuipers et al. (2014), eq. (10).
 * <p>
 * The prior mean is taken to be the sample mean, so the mean-correction term in R vanishes; this is the convention
 * used by bnlearn and BiDAG and is the only choice available when the score is built from a covariance matrix. The
 * prior scale matrix is T = t diag(s_1^2, ..., s_p^2), with t = alpha_mu (alpha_w - p - 1) / (alpha_mu + 1) and s_j^2
 * the sample variance of variable j. With unit-variance data this is exactly the bnlearn/Kuipers default T = t I;
 * scaling T to the sample variances makes the DAG ranking invariant to the units in which the variables are
 * measured, which the T = t I convention is not.
 * <p>
 * The Wishart degrees of freedom are given as an offset above p, alpha_w = p + alphaWOffset, since the prior is
 * proper only for alpha_w &gt; p - 1 and t is positive only for alpha_w &gt; p + 1. The defaults alpha_mu = 1 and
 * alphaWOffset = 2 give alpha_w = p + alpha_mu + 1, the setting recommended by Kuipers et al. and the bnlearn default.
 * <p>
 * References:
 * <ul>
 * <li>Geiger, D., and Heckerman, D. (1994). Learning Gaussian networks. UAI 1994.</li>
 * <li>Geiger, D., and Heckerman, D. (2002). Parameter priors for directed acyclic graphical models and the
 * characterization of several probability distributions. Annals of Statistics 30(5), 1412-1440.</li>
 * <li>Kuipers, J., Moffa, G., and Heckerman, D. (2014). Addendum on the scoring of Gaussian directed acyclic graphical
 * models. Annals of Statistics 42(4), 1689-1691.</li>
 * </ul>
 *
 * @author josephramsey
 * @see SemBicScore
 * @see BdeuScore
 */
public class BgeScore implements Score {

    /**
     * The covariance matrix of the data.
     */
    private final ICovarianceMatrix covariances;

    /**
     * The variables of the data, in covariance-matrix order.
     */
    private final List<Node> variables;

    /**
     * The sample variances of the variables, used to scale the prior.
     */
    private final double[] sampleVariances;

    /**
     * The sample size of the data.
     */
    private final int sampleSize;

    /**
     * The effective sample size, N in the formulas; defaults to the sample size.
     */
    private int nEff;

    /**
     * The prior precision on the mean (alpha_mu; bnlearn's iss.mu).
     */
    private double alphaMu = 1.0;

    /**
     * The Wishart degrees of freedom above the number of variables: alpha_w = p + alphaWOffset.
     */
    private double alphaWOffset = 2.0;

    /**
     * Constructs a BGe score from a covariance matrix.
     *
     * @param covariances the covariance matrix.
     */
    public BgeScore(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException("Covariance matrix is null.");
        }

        this.covariances = covariances;
        this.variables = new ArrayList<>(covariances.getVariables());
        this.sampleSize = covariances.getSampleSize();
        this.nEff = this.sampleSize;

        this.sampleVariances = new double[this.variables.size()];
        for (int j = 0; j < this.variables.size(); j++) {
            this.sampleVariances[j] = covariances.getValue(j, j);
            if (!(this.sampleVariances[j] > 0)) {
                throw new IllegalArgumentException("Variable " + this.variables.get(j)
                        + " has non-positive sample variance; BGe requires every variable to vary.");
            }
        }
    }

    /**
     * Constructs a BGe score from a continuous data set.
     *
     * @param dataSet the data set.
     */
    public BgeScore(DataSet dataSet) {
        this(covarianceOf(dataSet));
    }

    private static ICovarianceMatrix covarianceOf(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Data set is null.");
        }
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("BGe requires a continuous data set.");
        }
        return new CovarianceMatrix(dataSet);
    }

    /**
     * Returns the log marginal likelihood of the family node | parents; see the class Javadoc for the formula.
     *
     * @param node    the index of the child.
     * @param parents the indices of the parents.
     * @return the local score ("higher is better").
     */
    @Override
    public double localScore(int node, int... parents) {
        int[] pa = parents.clone();
        Arrays.sort(pa);

        int[] paY = new int[pa.length + 1];
        System.arraycopy(pa, 0, paY, 0, pa.length);
        paY[pa.length] = node;

        double n = this.nEff;
        double p = this.variables.size();
        double l = pa.length;
        double a = alphaW() - p;
        double t = priorScale();

        // Prior scale is diagonal, so log|T_S| = sum_{j in S} log(t s_j^2).
        double logDetTPaY = logDetPrior(paY, t);
        double logDetTPa = logDetPrior(pa, t);

        double logDetRPaY = logDetPosterior(paY, t, n);
        double logDetRPa = pa.length == 0 ? 0.0 : logDetPosterior(pa, t, n);

        double score = -(n / 2.0) * log(Math.PI)
                + 0.5 * log(this.alphaMu / (n + this.alphaMu))
                + Gamma.logGamma((n + a + l + 1) / 2.0) - Gamma.logGamma((a + l + 1) / 2.0)
                + ((a + l + 1) / 2.0) * logDetTPaY - ((a + l) / 2.0) * logDetTPa
                - ((n + a + l + 1) / 2.0) * logDetRPaY + ((n + a + l) / 2.0) * logDetRPa;

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        }

        return score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Returns the effective sample size used in the score.
     *
     * @return the effective sample size.
     */
    public int getEffectiveSampleSize() {
        return this.nEff;
    }

    /**
     * Sets the effective sample size used in the score; a value less than 1 restores the actual sample size.
     *
     * @param nEff the effective sample size.
     */
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 1 ? this.sampleSize : nEff;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(log(this.nEff));
    }

    /**
     * Returns the prior precision on the mean.
     *
     * @return alpha_mu.
     */
    public double getAlphaMu() {
        return this.alphaMu;
    }

    /**
     * Sets the prior precision on the mean; must be positive.
     *
     * @param alphaMu alpha_mu.
     */
    public void setAlphaMu(double alphaMu) {
        if (!(alphaMu > 0)) {
            throw new IllegalArgumentException("alphaMu must be positive: " + alphaMu);
        }
        this.alphaMu = alphaMu;
    }

    /**
     * Returns the Wishart degrees of freedom offset above the number of variables.
     *
     * @return alphaWOffset, where alpha_w = p + alphaWOffset.
     */
    public double getAlphaWOffset() {
        return this.alphaWOffset;
    }

    /**
     * Sets the Wishart degrees of freedom offset above the number of variables; must exceed 1 so that the prior scale
     * t = alpha_mu (alpha_w - p - 1) / (alpha_mu + 1) is positive.
     *
     * @param alphaWOffset alphaWOffset, where alpha_w = p + alphaWOffset.
     */
    public void setAlphaWOffset(double alphaWOffset) {
        if (!(alphaWOffset > 1)) {
            throw new IllegalArgumentException("alphaWOffset must exceed 1: " + alphaWOffset);
        }
        this.alphaWOffset = alphaWOffset;
    }

    /**
     * Returns the Wishart degrees of freedom, alpha_w = p + alphaWOffset.
     *
     * @return alpha_w.
     */
    public double getAlphaW() {
        return alphaW();
    }

    /**
     * Returns the covariance matrix the score is computed from.
     *
     * @return the covariance matrix.
     */
    public ICovarianceMatrix getCovariances() {
        return this.covariances;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "BGe Score alphaMu = " + nf.format(this.alphaMu) + " alphaW = p + " + nf.format(this.alphaWOffset);
    }

    private double alphaW() {
        return this.variables.size() + this.alphaWOffset;
    }

    /**
     * The scalar t in T = t diag(s^2): t = alpha_mu (alpha_w - p - 1) / (alpha_mu + 1).
     */
    private double priorScale() {
        return this.alphaMu * (this.alphaWOffset - 1.0) / (this.alphaMu + 1.0);
    }

    private double logDetPrior(int[] s, double t) {
        double sum = 0.0;
        for (int j : s) {
            sum += log(t * this.sampleVariances[j]);
        }
        return sum;
    }

    /**
     * log|R_S| for R_S = T_S + (N - 1) S_S, computed by Cholesky.
     */
    private double logDetPosterior(int[] s, double t, double n) {
        int k = s.length;
        Matrix sub = this.covariances.getSelection(s, s);
        double[][] r = new double[k][k];

        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                r[i][j] = (n - 1.0) * sub.get(i, j);
            }
            r[i][i] += t * this.sampleVariances[s[i]];
        }

        return logDetSpd(r);
    }

    /**
     * Log-determinant of a symmetric positive-definite matrix via an in-place Cholesky factorization.
     */
    private static double logDetSpd(double[][] a) {
        int k = a.length;
        double logDet = 0.0;

        for (int j = 0; j < k; j++) {
            double d = a[j][j];
            for (int m = 0; m < j; m++) {
                d -= a[j][m] * a[j][m];
            }
            if (!(d > 0)) {
                throw new IllegalStateException("Posterior scatter matrix is not positive definite (pivot " + d
                        + " at index " + j + ").");
            }
            double ljj = Math.sqrt(d);
            a[j][j] = ljj;
            logDet += 2.0 * log(ljj);

            for (int i = j + 1; i < k; i++) {
                double v = a[i][j];
                for (int m = 0; m < j; m++) {
                    v -= a[i][m] * a[j][m];
                }
                a[i][j] = v / ljj;
            }
        }

        return logDet;
    }
}
