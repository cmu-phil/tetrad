package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.MathUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ArkSplit class implements the NTadTest interface and provides functionality for conducting statistical tests
 * based on tetrad configurations. It primarily operates on covariance matrices derived from split data sets.
 * <p>
 * This class takes an input dataset, splits it into two subsets based on a specified fraction, computes the covariance
 * matrices for each subset, and then uses these matrices for subsequent calculations.
 */
public class ArkSplit implements NTadTest {
    /**
     * Covariance matrix of the first subset of the data.
     */
    private final SimpleMatrix S1;
    /**
     * Covariance matrix of the second subset of the data.
     */
    private final SimpleMatrix S2;
    /**
     * Number of rows in the second subset of the data.
     */
    private final int n2;

    /**
     * Constructs an ArkSplit object that splits a given dataset into two parts based on the specified fraction. It
     * computes the covariance matrices for each part of the split dataset.
     *
     * @param dataSet The dataset to be split and analyzed.
     * @param frac    The fraction of the dataset to be allocated to the first partition. The remaining portion is
     *                assigned to the second partition.
     */
    public ArkSplit(DataSet dataSet, double frac) {
        SimpleMatrix D = dataSet.getDoubleData().getDataCopy();

        // Let D1 be the first fraction of the D and D2 be the remaining fraction.
        int splitIndex = (int) (frac * dataSet.getNumRows());
        SimpleMatrix D1 = D.extractMatrix(0, splitIndex, 0, D.getNumCols());
        SimpleMatrix D2 = D.extractMatrix(splitIndex, D.getNumRows(), 0, D.getNumCols());

        // Let S1 be the covariance matrix of D1 and S2 be the covariance matrix of D2
        this.S1 = computeCovariance(D1);
        this.S2 = computeCovariance(D2);

        this.n2 = D2.getNumRows();
    }

    /**
     * Computes a combined p-value for a collection of tetrads represented as variable index pairs. The method first
     * converts the variable arguments into a list of tetrads and then delegates the computation to another method that
     * accepts a list as input.
     *
     * @param tets A variable-length argument array where each element is a 2D array representing a tetrad. Each 2D
     *             array must contain two rows of equal length, where each row represents a pair of variable indices.
     * @return The combined p-value computed using the chi-squared distribution. The result is in the range [0, 1],
     * where values close to 0 indicate stronger evidence against the null hypothesis.
     */
    public double tetrads(int[][]... tets) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, tets);
        return tetrads(tetList);
    }

    /**
     * Computes a combined p-value for a list of tetrads, where each tetrad is represented as a 2D array. The method
     * calculates the p-value for each tetrad individually and then combines them using a chi-squared distribution.
     * <p>
     * Each tetrad must consist of two pairs of nodes, represented as two rows of equal length. The combined p-value
     * provides a measure of statistical significance for the collection of tetrads.
     *
     * @param tets A list of 2D arrays, where each array represents a tetrad. Each 2D array must contain two rows of
     *             equal length, with each row representing a pair of variable indices.
     * @return The combined p-value for the input tetrads. The result is in the range [0, 1], where values closer to 0
     * indicate stronger evidence against the null hypothesis.
     * @throws IllegalArgumentException If a tetrad does not contain exactly two pairs of nodes or if the lengths of the
     *                                  pairs within a tetrad are not equal.
     */
    public double tetrads(List<int[][]> tets) {
        List<Double> p_values = new ArrayList<>();

        for (int[][] tet : tets) {
            if (tet.length != 2) {
                throw new IllegalArgumentException("Each tetrad must contain two pairs of nodes.");
            }
            if (tet[0].length != tet[1].length) {
                throw new IllegalArgumentException("Each pair of nodes must have the same length.");
            }

            double pValue = this.tetrad(tet);

            pValue = Math.max(pValue, 1e-16);
            p_values.add(pValue);
        }

        double sum = 0.0;

        for (double p : p_values) {
            sum += Math.log(p);
        }

        sum *= -2;

        return 1.0 - new ChiSquaredDistribution(2 * p_values.size()).cumulativeProbability(sum);
    }

    /**
     * Computes the p-value for a single tetrad, where the tetrad is represented as a 2D array with two rows. Each row
     * contains indices of variables in the covariance matrices.
     * <p>
     * This method performs calculations using submatrices of pre-computed covariance matrices and implements various
     * matrix operations to compute the p-value, measuring the statistical significance of the tetrad's independence
     * structure.
     *
     * @param tet A 2D array representing the tetrad, consisting of two rows of equal length. Each row specifies indices
     *            of variables involved in the tetrad computation.
     * @return The computed p-value, which is a double in the range [0, 1]. Values closer to 0 indicate stronger
     * evidence against the null hypothesis of independence.
     * @throws IllegalArgumentException If the input array does not consist of exactly two rows or the rows have unequal
     *                                  lengths.
     */
    public double tetrad(int[][] tet) {
        int[] a = tet[0];
        int[] b = tet[1];
        int z = a.length;

        SimpleMatrix XY = extractSubMatrix(this.S2, a, b);
        SimpleSVD<SimpleMatrix> svd = XY.svd();
        SimpleMatrix U = svd.getU();
        SimpleMatrix VT = svd.getV().transpose();

        SimpleMatrix XXi = extractSubMatrix(this.S1, a, a).invert();
        SimpleMatrix YYi = extractSubMatrix(this.S1, b, b).invert();

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

        SimpleMatrix subR = extractSubMatrix(R, idx, idx).invert();

        double p_corr = -subR.get(0, 1) / Math.sqrt(subR.get(0, 0) * subR.get(1, 1));
        double z_score = MathUtils.arctanh(p_corr) * Math.sqrt(this.n2 - idx.length - 1);

        NormalDistribution normalDist = new NormalDistribution();
        return 2 * normalDist.cumulativeProbability(-Math.abs(z_score));
    }

    private SimpleMatrix computeCovariance(SimpleMatrix data) {
        int n = data.getNumRows();
        int m = data.getNumCols();

        // Compute mean of each column
        SimpleMatrix mean = new SimpleMatrix(1, m);
        for (int i = 0; i < m; i++) {
            mean.set(0, i, data.extractVector(false, i).elementSum() / n);
        }

        // Center the data
        SimpleMatrix centeredData = new SimpleMatrix(n, m);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                centeredData.set(i, j, data.get(i, j) - mean.get(0, j));
            }
        }

        // Covariance matrix: (X^T * X) / (n - 1)
        return centeredData.transpose().mult(centeredData).scale(1.0 / (n - 1));
    }

    private SimpleMatrix extractSubMatrix(SimpleMatrix matrix, int[] rows, int[] cols) {
        SimpleMatrix subMatrix = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                subMatrix.set(i, j, matrix.get(rows[i], cols[j]));
            }
        }
        return subMatrix;
    }
}
