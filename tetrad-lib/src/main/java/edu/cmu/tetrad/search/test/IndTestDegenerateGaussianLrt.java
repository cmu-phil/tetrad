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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Double.NaN;

/**
 * Implements degenerate Gaussian test as a likelihood ratio test. The reference is here:
 * <p>
 * Andrews, B., Ramsey, J., &amp; Cooper, G. F. (2019, July). Learning high-dimensional directed acyclic graphs with
 * mixed data-types. In The 2019 ACM SIGKDD Workshop on Causal Discovery (pp. 4-21). PMLR.
 *
 * @author Bryan Andrews
 * @author Joseph Ramsey refactoring 2024-12-26
 * @version $Id: $Id
 */
public class IndTestDegenerateGaussianLrt implements IndependenceTest, EffectiveSampleSizeSettable {
    /**
     * A hash of nodes to indices.
     */
    private final Map<Node, Integer> nodeHash;
    /**
     * The data set.
     */
    private final DataSet dataSet;
    /**
     * The mixed variables of the original dataset.
     */
    private final List<Node> variables;
    /**
     * The embedding map.
     */
    private final Map<Integer, List<Integer>> embedding;
    /**
     * Covariance matrix over the embedde data.
     */
    private final SimpleMatrix covarianceMatrix;
    /**
     * Represents the sample size of the dataset being analyzed. This variable is used in statistical computations, such
     * as variance and covariance calculations, to determine the scale and reliability of the analysis.
     */
    private final int sampleSize;
    /**
     * Singularity lambda.
     */
    private double lambda = 0.0;
    /**
     * The alpha level.
     */
    private double alpha = 0.01;
    /**
     * The p value.
     */
    private double pValue = NaN;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;
    private int nEff;

    /**
     * Constructs the test using the given (mixed) data set.
     *
     * @param dataSet The data being analyzed.
     */
    public IndTestDegenerateGaussianLrt(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int j = 0; j < this.variables.size(); j++) {
            nodesHash.put(this.variables.get(j), j);
        }

        this.nodeHash = nodesHash;

        // Expand the discrete columns to give indicators for each category. For the continuous variables, we
        // wet the truncation limit to 1, on the contract that the first polynomial for any basis will be just
        // x itself. These are asssumed to be Gaussian for this test. Basis scale -1 will do no scaling.
        Embedding.EmbeddedData embeddedData = Embedding.getEmbeddedData(
                dataSet, 1, 1, -1);
        this.embedding = embeddedData.embedding();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);
        this.covarianceMatrix = DataUtils.cov(embeddedData.embeddedData().getDoubleData().getSimpleMatrix());
        this.setLambda(lambda);
    }

    /**
     * Subsets the variables used in the independence test.
     *
     * @param vars The sublist of variables.
     * @return The IndependenceTest object with subset of variables.
     * @throws UnsupportedOperationException if the method is not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Tests for the conditional independence of two nodes given a set of conditioning nodes.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double pValue = getPValue(x, y, z);
        boolean independent = pValue > alpha;
        return new IndependenceResult(new IndependenceFact(x, y, z), independent, pValue, alpha - pValue);
    }

    /**
     * Computes the p-value for the null hypothesis that two variables, represented as nodes, are conditionally
     * independent given a set of conditioning variables.
     * <p>
     * The method transforms the input nodes and conditioning set into their respective embedded representations and
     * computes the likelihood ratio statistic based on residual variances. It then calculates the corresponding p-value
     * using a Chi-squared distribution.
     *
     * @param x the first node representing one of the variables to be tested.
     * @param y the second node representing the other variable to be tested.
     * @param z the set of nodes representing the conditioning variables.
     * @return the computed p-value for the hypothesis test of conditional independence.
     */
    private double getPValue(Node x, Node y, Set<Node> z) {
        List<Node> zList = new ArrayList<>(z);
        Collections.sort(zList);

        int _x = this.nodeHash.get(x);
        int _y = this.nodeHash.get(y);
        int[] _z = new int[zList.size()];
        for (int i = 0; i < zList.size(); i++) {
            _z[i] = this.nodeHash.get(zList.get(i));
        }

        // Grab the embedded data for _x, _y, and _z.
        List<Integer> embedded_x = embedding.get(_x);
        List<Integer> embedded_y = embedding.get(_y);
        List<Integer> embedded_z = new ArrayList<>();

        for (int value : _z) {
            List<Integer> embeddedValues = embedding.get(value);
            if (embeddedValues != null) {
                embedded_z.addAll(embeddedValues);
            }
        }

        // Convert to index arrays
        int[] xIndices = embedded_x.stream().mapToInt(Integer::intValue).toArray();
        int[] yIndices = embedded_y.stream().mapToInt(Integer::intValue).toArray();
        int[] zIndices = embedded_z.stream().mapToInt(Integer::intValue).toArray();

        // Compute variance estimates
        double eps = 1e-10;
        double sigma0_sq = Math.max(eps, computeResidualVariance(xIndices, zIndices));
        double sigma1_sq = Math.max(eps, computeResidualVariance(xIndices, concatArrays(yIndices, zIndices)));

        // Log-likelihood ratio statistic
        double LR_stat = nEff * Math.log(sigma0_sq / sigma1_sq);

        // Degrees of freedom is the number of additional basis columns in Y
        int df = yIndices.length;
        if (df == 0) return 1.0;

        // Compute p-value
        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        double p_value = 1.0 - chi2.cumulativeProbability(LR_stat);
        this.pValue = p_value;

//        if (verbose) {
//            System.out.printf("LR Stat: %.4f | df: %d | p: %.4f%n", LR_stat, df, p_value);
//        }

        return p_value;
    }

    /**
     * Computes the variance of residuals given the indices of predictors.
     */
    private double computeResidualVariance(int[] xIndices, int[] predictorIndices) {
        if (predictorIndices.length == 0) {
            return StatUtils.extractSubMatrix(covarianceMatrix, xIndices, xIndices).trace() / xIndices.length;
        }

        SimpleMatrix Sigma_XX = StatUtils.extractSubMatrix(covarianceMatrix, xIndices, xIndices);
        SimpleMatrix Sigma_XP = StatUtils.extractSubMatrix(covarianceMatrix, xIndices, predictorIndices);
        SimpleMatrix Sigma_PP = StatUtils.extractSubMatrix(covarianceMatrix, predictorIndices, predictorIndices);
//        Sigma_PP = StatUtils.chooseMatrix(Sigma_PP, lambda);

        // Compute OLS estimate of X given predictors P
        SimpleMatrix beta = (new Matrix(Sigma_PP).chooseInverse(lambda)).getData().mult(Sigma_XP.transpose());

        // Compute residual variance
        return Sigma_XX.minus(Sigma_XP.mult(beta)).trace() / xIndices.length;
    }

    /**
     * Concatenates two integer arrays.
     */
    private int[] concatArrays(int[] first, int[] second) {
        int[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * Returns the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for this test.
     *
     * @return This p-value.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinining independence
     * relations.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return this level, default 0.01.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException("Alpha must be between 0 and 1.");
        }
        this.alpha = alpha;
    }

    /**
     * Returns a copy of the dataset being analyzed.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.dataSet.copy();
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Degenerate Gaussian, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns true iff verbose output should be printed.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the lambda value for the test.
     *
     * @param lambda The singularity lambda parameter to be used in the independence test.
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    @Override
    public int getEffectiveSampleSize() {
        return this.nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? sampleSize : nEff;
    }
}

