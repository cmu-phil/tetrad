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
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.special.Gamma;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.math3.util.FastMath.log;

/**
 * BF-BGe: the BGe score applied to the basis-function embedding used by {@link BasisFunctionBicScore}. Each continuous
 * variable is expanded into its Legendre basis columns up to a truncation limit and each discrete variable into one
 * indicator column per category but the last, exactly as in BF-BIC; but where BF-BIC scores the expanded family with
 * a BIC-penalized Gaussian likelihood, BF-BGe scores it with the exact Normal-Wishart marginal likelihood of
 * {@link BgeScore}. This replaces the BIC penalty on the whole expanded block (the source of BF-BIC's conservatism on
 * weak linear edges) with the Bayesian Occam factor, in the same way that BDeu replaces the discrete BIC penalty.
 * <p>
 * For a family Y | Pa with embedded column sets A (for Y) and B (for Pa), the local score is
 * <pre>
 *   log p(D_{A u B}) - log p(D_B),
 * </pre>
 * where p(D_S) is the BGe marginal likelihood of the embedded columns S under the Normal-Wishart prior on the full
 * embedded system of dimension p (the total number of embedded columns), from Kuipers, Moffa, and Heckerman (2014),
 * eq. (10). This is the block analog of BGe's family score, equivalently the chain-rule sum of single-column BGe
 * scores over the columns of A given B and the earlier columns of A, and it telescopes the same way, so the score is
 * decomposable and score-equivalent over the embedded block DAG. Because the prior scale matrix is added to the
 * scatter, the family scatter matrix is always positive definite, and no singularity lambda is needed.
 * <p>
 * Hyperparameters and prior conventions are those of {@link BgeScore}: prior mean equal to the sample mean, prior
 * scale T = t diag(s_j^2) over the embedded columns with t = alpha_mu (alpha_w - p - 1) / (alpha_mu + 1), and
 * alpha_w = p + alphaWOffset. As with BGe, alpha_mu acts on the score only through t, so smaller alpha_mu makes
 * edges more expensive; alpha_mu = 0.1 tracks SEM-BIC at penalty discount 2 in null false-positive rate, and
 * alpha_mu = 1 sits between penalty discounts 1 and 2.
 * <p>
 * Embedded columns with zero sample variance (e.g., indicators of categories that do not occur) carry no information
 * and are dropped from the embedding at construction.
 *
 * @author josephramsey
 * @see BgeScore
 * @see BasisFunctionBicScore
 * @see DegenerateGaussianBgeScore
 */
public class BasisFunctionBgeScore implements Score {

    /**
     * The original variables.
     */
    private final List<Node> variables;

    /**
     * Variable index to embedded column indices.
     */
    private final Map<Integer, List<Integer>> embedding;

    /**
     * Covariance matrix of the embedded data.
     */
    private final ICovarianceMatrix embeddedCov;

    /**
     * Sample variances of the embedded columns.
     */
    private final double[] embeddedVariances;

    /**
     * Total number of embedded columns; the dimension p of the Normal-Wishart prior.
     */
    private final int embeddedDim;

    /**
     * The sample size.
     */
    private final int sampleSize;

    /**
     * The truncation limit used for the basis expansion (1 for the degenerate Gaussian embedding).
     */
    private final int truncationLimit;

    /**
     * The effective sample size, N in the formulas.
     */
    private int nEff;

    /**
     * Prior precision on the mean.
     */
    private double alphaMu = 1.0;

    /**
     * Wishart degrees of freedom above the embedded dimension: alpha_w = p + alphaWOffset.
     */
    private double alphaWOffset = 2.0;

    /**
     * Constructs a BF-BGe score with the Legendre embedding of {@link BasisFunctionBicScore}.
     *
     * @param dataSet         the (mixed) data set.
     * @param truncationLimit the truncation limit of the basis (1 gives the linear/degenerate-Gaussian embedding).
     */
    public BasisFunctionBgeScore(DataSet dataSet, int truncationLimit) {
        this(dataSet, truncationLimit, false);
    }

    /**
     * Constructs a BF-BGe score with the Legendre embedding of {@link BasisFunctionBicScore}, optionally applying the
     * same adaptive basis-column screen that BF-BIC uses.
     *
     * @param dataSet                the (mixed) data set.
     * @param truncationLimit        the truncation limit of the basis.
     * @param adaptiveBasisSelection if true, higher-order basis columns that fail BF-BIC's BIC-crossing screen are
     *                               dropped from the embedding before scoring.
     * @see Embedding#pruneUninformativeBasisColumns(DataSet, Map, ICovarianceMatrix)
     */
    public BasisFunctionBgeScore(DataSet dataSet, int truncationLimit, boolean adaptiveBasisSelection) {
        this(dataSet, truncationLimit, embed(dataSet, truncationLimit, 1), adaptiveBasisSelection);
    }

    /**
     * Shared constructor over a prepared embedding.
     *
     * @param dataSet                the original data set.
     * @param truncationLimit        the truncation limit recorded for {@link #getTruncationLimit()}.
     * @param embedded               the embedded data and column map.
     * @param adaptiveBasisSelection whether to apply the BIC-crossing screen to the embedding.
     */
    protected BasisFunctionBgeScore(DataSet dataSet, int truncationLimit, Embedding.EmbeddedData embedded,
                                    boolean adaptiveBasisSelection) {
        if (dataSet == null) throw new NullPointerException("Data set is null.");

        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        this.nEff = this.sampleSize;
        this.truncationLimit = truncationLimit;

        DataSet embeddedData = embedded.embeddedData();
        this.embeddedCov = new CovarianceMatrix(embeddedData);
        this.embeddedDim = embeddedData.getNumColumns();

        Map<Integer, List<Integer>> map = embedded.embedding();
        if (adaptiveBasisSelection) {
            map = Embedding.pruneUninformativeBasisColumns(dataSet, map,
                    new edu.cmu.tetrad.data.CorrelationMatrix(embeddedData));
        }

        this.embeddedVariances = new double[this.embeddedDim];
        for (int j = 0; j < this.embeddedDim; j++) {
            this.embeddedVariances[j] = this.embeddedCov.getValue(j, j);
        }

        // Drop zero-variance embedded columns (e.g., unobserved categories); they carry no information and would
        // make the scatter matrix singular.
        this.embedding = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : map.entrySet()) {
            List<Integer> kept = new ArrayList<>();
            for (int col : e.getValue()) {
                if (this.embeddedVariances[col] > 1e-12) kept.add(col);
            }
            this.embedding.put(e.getKey(), kept);
        }
    }

    /**
     * Builds the embedding with the conventions of the corresponding BIC score: Legendre basis, scaled to [-1, 1] for
     * the basis-function embedding (basisScale 1), unscaled for the degenerate-Gaussian embedding (basisScale -1).
     */
    protected static Embedding.EmbeddedData embed(DataSet dataSet, int truncationLimit, double basisScale) {
        if (dataSet == null) throw new NullPointerException("Data set is null.");
        return Embedding.getEmbeddedData(dataSet, truncationLimit, 1, basisScale);
    }

    /**
     * Returns log p(D_{A u B}) - log p(D_B) for the embedded columns A of the node and B of its parents.
     *
     * @param node    the index of the child.
     * @param parents the indices of the parents.
     * @return the local score ("higher is better").
     */
    @Override
    public double localScore(int node, int... parents) {
        List<Integer> a = this.embedding.get(node);
        List<Integer> b = new ArrayList<>();
        for (int p : parents) b.addAll(this.embedding.get(p));

        int[] bCols = toArray(b);
        int[] abCols = new int[b.size() + a.size()];
        System.arraycopy(bCols, 0, abCols, 0, bCols.length);
        for (int j = 0; j < a.size(); j++) abCols[bCols.length + j] = a.get(j);

        double score = logMarginal(abCols) - logMarginal(bCols);

        if (Double.isNaN(score) || Double.isInfinite(score)) return Double.NaN;
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
     * Returns the effective sample size.
     *
     * @return N used in the score.
     */
    public int getEffectiveSampleSize() {
        return this.nEff;
    }

    /**
     * Sets the effective sample size; a value less than 1 restores the actual sample size.
     *
     * @param nEff the effective sample size.
     */
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 1 ? this.sampleSize : nEff;
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
        if (!(alphaMu > 0)) throw new IllegalArgumentException("alphaMu must be positive: " + alphaMu);
        this.alphaMu = alphaMu;
    }

    /**
     * Returns the Wishart degrees-of-freedom offset above the embedded dimension.
     *
     * @return alphaWOffset, where alpha_w = p + alphaWOffset.
     */
    public double getAlphaWOffset() {
        return this.alphaWOffset;
    }

    /**
     * Sets the Wishart degrees-of-freedom offset; must exceed 1.
     *
     * @param alphaWOffset alphaWOffset, where alpha_w = p + alphaWOffset.
     */
    public void setAlphaWOffset(double alphaWOffset) {
        if (!(alphaWOffset > 1)) throw new IllegalArgumentException("alphaWOffset must exceed 1: " + alphaWOffset);
        this.alphaWOffset = alphaWOffset;
    }

    /**
     * Returns the truncation limit of the basis expansion.
     *
     * @return the truncation limit.
     */
    public int getTruncationLimit() {
        return this.truncationLimit;
    }

    /**
     * Returns the total number of embedded columns (the dimension of the Normal-Wishart prior).
     *
     * @return the embedded dimension.
     */
    public int getEmbeddedDimension() {
        return this.embeddedDim;
    }

    /**
     * Returns a copy of the embedding (variable index to embedded column indices) the score is using.
     *
     * @return the embedding.
     */
    public Map<Integer, List<Integer>> getEmbedding() {
        Map<Integer, List<Integer>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : this.embedding.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "BF-BGe Score truncation = " + this.truncationLimit + " alphaMu = " + nf.format(this.alphaMu)
                + " alphaW = p + " + nf.format(this.alphaWOffset);
    }

    // ---- BGe marginal likelihood of a set of embedded columns ----

    /**
     * log p(D_S) for embedded columns S, Kuipers et al. (2014) eq. (10), with prior mean = sample mean and
     * T_S = t diag(s_j^2). Returns 0 for the empty set.
     */
    protected double logMarginal(int[] s) {
        int k = s.length;
        if (k == 0) return 0.0;

        double n = this.nEff;
        double a = this.alphaWOffset; // alpha_w - p
        double t = this.alphaMu * (this.alphaWOffset - 1.0) / (this.alphaMu + 1.0);
        double aK = (a + k) / 2.0;

        double logDetT = 0.0;
        for (int j : s) logDetT += log(t * this.embeddedVariances[j]);

        Matrix sub = this.embeddedCov.getSelection(s, s);
        double[][] r = new double[k][k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) r[i][j] = (n - 1.0) * sub.get(i, j);
            r[i][i] += t * this.embeddedVariances[s[i]];
        }
        double logDetR = logDetSpd(r);

        return -(k * n / 2.0) * log(Math.PI)
                + (k / 2.0) * log(this.alphaMu / (n + this.alphaMu))
                + logMultiGamma(k, aK + n / 2.0) - logMultiGamma(k, aK)
                + aK * logDetT
                - (aK + n / 2.0) * logDetR;
    }

    private static double logMultiGamma(int k, double x) {
        double v = (k * (k - 1) / 4.0) * log(Math.PI);
        for (int j = 1; j <= k; j++) v += Gamma.logGamma(x + (1 - j) / 2.0);
        return v;
    }

    private static int[] toArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        Arrays.sort(arr);
        return arr;
    }

    /**
     * Log-determinant of a symmetric positive-definite matrix via in-place Cholesky.
     */
    private static double logDetSpd(double[][] a) {
        int k = a.length;
        double logDet = 0.0;

        for (int j = 0; j < k; j++) {
            double d = a[j][j];
            for (int m = 0; m < j; m++) d -= a[j][m] * a[j][m];
            if (!(d > 0)) {
                throw new IllegalStateException("Embedded scatter matrix is not positive definite (pivot " + d
                        + " at index " + j + ").");
            }
            double ljj = Math.sqrt(d);
            a[j][j] = ljj;
            logDet += 2.0 * log(ljj);
            for (int i = j + 1; i < k; i++) {
                double v = a[i][j];
                for (int m = 0; m < j; m++) v -= a[i][m] * a[j][m];
                a[i][j] = v / ljj;
            }
        }

        return logDet;
    }
}
