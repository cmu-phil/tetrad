package edu.cmu.tetrad.search.ntad_test;

import edu.cmu.tetrad.util.MathUtils;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Ark class extends the NtadTest class and provides a mechanism to perform statistical operations based on tetrads
 * and their probabilities. It leverages correlation computation, sampling, and matrix manipulation to calculate
 * p-values and z-scores for tetrads. This class is specifically designed to operate on instances of SimpleMatrix for
 * multivariate analysis.
 *
 * @author bryanandrews
 */
public class Ark extends NtadTest {
    private final SimpleMatrix S1;
    private final SimpleMatrix S2;
    private final double sp;

    /**
     * Constructs an Ark object using the provided data matrix, split proportion, and effective sample size.
     * The data matrix is divided into two subsets based on the specified split proportion. Correlation matrices
     * are computed for each subset.
     *
     * @param df  the input data matrix as a SimpleMatrix object, where each row represents an observation
     *            and each column represents a variable
     * @param sp  the split proportion for dividing the data matrix into two subsets; values greater than 0
     *            are used as-is, while negative values are transformed to 1 - sp
     * @param ess the effective sample size, which must be -1 or greater than 1
     */
    public Ark(SimpleMatrix df, double sp, int ess) {
        super(df, false, ess);
        this.sp = sp > 0 ? sp : 1 - sp;
        int splitIndex = (int) (this.sp * df.getNumRows());

        this.S1 = computeCorrelations(df.extractMatrix(0, splitIndex, 0, df.getNumCols()));
        this.S2 = computeCorrelations(df.extractMatrix(splitIndex, df.getNumRows(), 0, df.getNumCols()));
    }

    /**
     * Computes a statistical measure based on the specified indices and correlation matrices.
     * Depending on the resampling flag, the method either operates on precomputed matrices
     * or recalculates them using a sampled subset of the data matrix. This method is designed
     * to compute a p-value reflecting the statistical significance of a correlation pattern.
     *
     * @param ntad      a 2D integer array where ntad[0] contains indices for the first group of variables,
     *                  and ntad[1] contains indices for the second group
     * @param resample  a boolean indicating whether to resample the data matrix for correlation calculation;
     *                  if true, a subset of rows is selected based on the specified fraction
     * @param frac      a double representing the fraction of rows to sample if resampling is enabled,
     *                  where 0.0 &lt;= frac &lt;= 1.0
     * @return a double representing the p-value for the computed correlation statistics
     */
    @Override
    public double ntad(int[][] ntad, boolean resample, double frac) {
        SimpleMatrix S1, S2;
        int n;

        if (resample) {
            SimpleMatrix sampledDf = sampleRows(df, frac);
            n = ess; //sampledDf.getNumRows();
            int splitIndex = (int) (this.sp * n);
            S1 = computeCorrelations(sampledDf.extractMatrix(0, splitIndex, 0, sampledDf.getNumCols()));
            S2 = computeCorrelations(sampledDf.extractMatrix(splitIndex, n, 0, sampledDf.getNumCols()));
        } else {
            n = this.ess;
            S1 = this.S1;
            S2 = this.S2;
        }

        int[] a = ntad[0];
        int[] b = ntad[1];
        int z = a.length;

        SimpleMatrix XY = this.sp < 1 ? StatUtils.extractSubMatrix(S2, a, b) : StatUtils.extractSubMatrix(S1, a, b);
        SimpleSVD<SimpleMatrix> svd = XY.svd();
        SimpleMatrix U = svd.getU();
        SimpleMatrix VT = svd.getV().transpose();

        SimpleMatrix XXi = StatUtils.extractSubMatrix(S1, a, a).invert();
        SimpleMatrix YYi = StatUtils.extractSubMatrix(S1, b, b).invert();

        SimpleMatrix A = U.transpose().mult(XXi).mult(U);
        SimpleMatrix B = VT.mult(YYi).mult(VT.transpose());
        SimpleMatrix C = U.transpose().mult(XXi).mult(XY).mult(YYi).mult(VT.transpose());

        int[] indicesA = new int[z];
        int[] indicesB = new int[z];
        for (int i = 0; i < z; i++) {
            indicesA[i] = i;
            indicesB[i] = i + z;
        }

        SimpleMatrix R = new SimpleMatrix(2 * z, 2 * z);
        R.insertIntoThis(0, 0, A);
        R.insertIntoThis(0, z, C);
        R.insertIntoThis(z, 0, C.transpose());
        R.insertIntoThis(z, z, B);

        SimpleMatrix D = new SimpleMatrix(2 * z, 2 * z);
        for (int i = 0; i < 2 * z; i++) {
            D.set(i, i, Math.sqrt(R.get(i, i)));
        }

        SimpleMatrix Di = D.invert();
        R = Di.mult(R).mult(Di);

        int[] idx = new int[z + 1];
        idx[0] = indicesA[z - 1];
        idx[1] = indicesB[z - 1];
        System.arraycopy(indicesA, 0, idx, 2, z - 1);

        SimpleMatrix subR = StatUtils.extractSubMatrix(R, idx, idx).invert();

        double p_corr = -subR.get(0, 1) / Math.sqrt(subR.get(0, 0) * subR.get(1, 1));
        double z_score = MathUtils.arctanh(p_corr) * Math.sqrt(n - idx.length - 1);

        NormalDistribution normalDist = new NormalDistribution();
        return 2 * normalDist.cumulativeProbability(-Math.abs(z_score));
    }

    /**
     * Computes a statistical measure using the specified indices and correlation matrices.
     * This method internally delegates to another overloaded version of the function
     * with additional parameters set to default values.
     *
     * @param ntad a 2D integer array where ntad[0] contains indices for the first group of variables,
     *             and ntad[1] contains indices for the second group
     * @return a double representing the p-value for the computed correlation statistics
     */
    @Override
    public double ntad(int[][] ntad) {
        return ntad(ntad, false, 1);
    }

    /**
     * Computes a statistical measure based on a variable number of 2D integer arrays, where each array
     * contains tetrad configurations. This method internally delegates to another overloaded version
     * of the function which accepts a list of tetrad configurations.
     *
     * @param ntads a variable-length array of 2D integer arrays, where each element represents a tetrad
     *              configuration. Each 2D array is expected to contain two subarrays, each defining a group
     *              of node indices.
     * @return a double value representing the computed statistical measure for the provided tetrads.
     */
    @Override
    public double ntads(int[][]... ntads) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, ntads);
        return ntads(tetList);
    }

    /**
     * Computes a statistical measure based on a list of tetrad configurations.
     * Each tetrad configuration is represented as a 2D integer array with two subarrays, where
     * each subarray defines a group of node indices. The method calculates a combined
     * p-value through logarithmic transformations and chi-squared distribution.
     *
     * @param ntads a list of 2D integer arrays, where each array represents a tetrad configuration.
     *              Each 2D array must contain exactly two subarrays with matching lengths,
     *              representing two groups of node indices.
     * @return a double value representing the computed statistical measure for the provided tetrads.
     *         The result is a p-value reflecting the statistical significance of the configurations.
     * @throws IllegalArgumentException if any tetrad does not contain exactly two subarrays
     *                                  or if the subarrays do not have the same length.
     */
    @Override
    public double ntads(List<int[][]> ntads) {
        double sum = 0.0;
        int count = 0;

        for (int[][] tet : ntads) {
            if (tet.length != 2) {
                throw new IllegalArgumentException("Each tetrad must contain two pairs of nodes.");
            }
            if (tet[0].length != tet[1].length) {
                throw new IllegalArgumentException("Each pair of nodes must have the same length.");
            }

            double pValue = this.ntad(tet);
            if (pValue == 0) {
                sum = Double.NEGATIVE_INFINITY;
            } else {
                sum += Math.log(pValue);
            }

            count++;
        }

        sum *= -2;
        return 1.0 - new ChiSquaredDistribution(2 * count).cumulativeProbability(sum);
    }
}

