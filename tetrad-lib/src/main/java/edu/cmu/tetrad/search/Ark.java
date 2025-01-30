package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.MathUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Experimental Ark test of a tetrad vanishing.
 */
public class Ark implements NTadTest {
    /**
     * The covariance matrix used for statistical computations within the Ark class. This matrix is typically derived
     * from the input data or covariance matrix provided during the initialization of the Ark instance.
     * <p>
     * S is a final SimpleMatrix object, ensuring its reference cannot be reassigned after initialization. However, its
     * elements may still be modified if necessary by operations defined on the SimpleMatrix class.
     */
    private final SimpleMatrix S;
    /**
     * Represents the number of variables to be analyzed or operated on within the context of the containing class. This
     * value is typically used to configure or determine operations involving variable dimensions or counts in matrices
     * or tetrad-related calculations.
     */
    private final int n;

    /**
     * Constructs an Ark object by initializing the covariance matrix and the number of rows based on the given
     * DataSet.
     *
     * @param dataSet the data set used to populate the covariance matrix and determine the number of rows
     */
    public Ark(DataSet dataSet) {
        this.S = new CovarianceMatrix(dataSet).getMatrix().getDataCopy();
        this.n = dataSet.getNumRows();
    }

    /**
     * Constructs an Ark object by initializing the covariance matrix and the sample size.
     *
     * @param cov the covariance matrix to initialize the Ark object with. Must not be an instance of
     *            CorrelationMatrix.
     * @throws IllegalArgumentException if the provided covariance matrix is a CorrelationMatrix.
     */
    public Ark(ICovarianceMatrix cov) {
        if (cov instanceof CorrelationMatrix) {
            throw new IllegalArgumentException("Covariance matrix must not be a correlation matrix.");
        }

        this.S = cov.getMatrix().getDataCopy();
        this.n = cov.getSampleSize();
    }

    /**
     * Calculates a combined p-value for multiple tetrad tests. Each tetrad is represented by two pairs of nodes,
     * provided as a variable number of two-dimensional arrays.
     *
     * @param tets a variable number of two-dimensional integer arrays, where each array represents a tetrad as two
     *             pairs of nodes. Each array must contain exactly two rows, and both rows must have the same length.
     * @return the combined p-value for the input tetrads, computed using a Chi-squared distribution.
     * @throws IllegalArgumentException if any of the input tetrads do not meet the required format (two pairs of nodes
     *                                  with equal lengths).
     */
    public double tetrads(int[][]... tets) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, tets);
        return tetrads(tetList);
    }

    /**
     * Computes a combined p-value for multiple tetrad tests. Each tetrad is represented by a pair of node arrays,
     * processed to determine their statistical association.
     *
     * @param tets a list of two-dimensional integer arrays, where each array represents a tetrad with two pairs of
     *             nodes. Each array must contain exactly two rows, and both rows must have the same length.
     * @return the combined p-value for the input tetrads, computed using a Chi-squared distribution and the sum of the
     * logarithms of individual p-values.
     * @throws IllegalArgumentException if any of the input tetrads do not conform to the required format (two pairs of
     *                                  nodes with equal lengths).
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
     * Computes the p-value for a tetrad test given a 2D integer array representing two pairs of nodes. The method
     * performs computations involving submatrices, singular value decomposition, and statistical tests to determine the
     * association between the nodes in the tetrad.
     *
     * @param tet a two-dimensional integer array where the first row represents the indices of the first pair of nodes
     *            and the second row represents the indices of the second pair of nodes. Both rows must have the same
     *            length.
     * @return the p-value resulting from the tetrad test, computed using a statistical z-score and its corresponding
     * cumulative probability value.
     */
    public double tetrad(int[][] tet) {//}, boolean resample, double frac) {
        int[] a = tet[0];
        int[] b = tet[1];
        int z = a.length;

        SimpleMatrix XY = extractSubMatrix(this.S, a, b);
        SimpleSVD<SimpleMatrix> svd = XY.svd();
        SimpleMatrix U = svd.getU();
        SimpleMatrix VT = svd.getV().transpose();

        SimpleMatrix XXi = extractSubMatrix(this.S, a, a).invert();
        SimpleMatrix YYi = extractSubMatrix(this.S, b, b).invert();

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
        double z_score = MathUtils.arctanh(p_corr) * Math.sqrt(this.n - idx.length - 1);

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
