package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import org.ejml.simple.SimpleMatrix;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Wishart implements NTadTest {

    private final SimpleMatrix S;
    private final int n;

    public Wishart(DataSet dataSet) {
        this.S = computeCovariance(dataSet.getDoubleData().getDataCopy());
        this.n = dataSet.getNumRows();
    }

    @Override
    public double tetrads(int[][]... tets) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, tets);
        return tetrads(tetList);
    }

    @Override
    public double tetrads(List<int[][]> tets) {
        if (tets.size() != 1) {
            throw new IllegalArgumentException("Only one tetrad is allowed for the Wishart test.");
        }

        return tetrad(tets.getFirst());
    }

    public double tetrad(int[][] tet) {
        int[] a = tet[0];
        int[] b = tet[1];

        // Compute sigma^2
        double sigma2 = ((double) (n + 1)) / (n - 1);
        sigma2 *= extractSubMatrix(S, a, a).determinant();
        sigma2 *= extractSubMatrix(S, b, b).determinant();
        sigma2 -= extractSubMatrix(S, concatenate(a, b), concatenate(a, b)).determinant();
        sigma2 /= (n - 2);

        // Compute z-score
        double z_score = extractSubMatrix(S, a, b).determinant() / Math.sqrt(sigma2);

        // Compute the p-value using the cumulative distribution function
        NormalDistribution normalDist = new NormalDistribution();
        return 2 * normalDist.cumulativeProbability(-Math.abs(z_score));
    }

    // Helper method to compute covariance matrix
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

    // Helper method to extract submatrix
    private SimpleMatrix extractSubMatrix(SimpleMatrix matrix, int[] rows, int[] cols) {
        SimpleMatrix subMatrix = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                subMatrix.set(i, j, matrix.get(rows[i], cols[j]));
            }
        }
        return subMatrix;
    }

    // Helper method to concatenate two arrays
    private int[] concatenate(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}


