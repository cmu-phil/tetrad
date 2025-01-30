package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArkSplit implements NTadTest {

    private final SimpleMatrix S1;
    private final SimpleMatrix S2;
    private final int n2;

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

    public static double arctanh(double x) {
        return Ark.arctanh(x);
    }

    public double tetrads(int[][]... tets) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, tets);
        return tetrads(tetList);
    }

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
        double z_score = arctanh(p_corr) * Math.sqrt(this.n2 - idx.length - 1);

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
