package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Ark implements NTadTest {

    private final int n;
    private final SimpleMatrix S;

    public Ark(DataSet dataSet) {
        this.S = new CovarianceMatrix(dataSet).getMatrix().getDataCopy();
        this.n = dataSet.getNumRows();
    }

    public Ark(ICovarianceMatrix cov) {
        if (cov instanceof CorrelationMatrix) {
            throw new IllegalArgumentException("Covariance matrix must not be a correlation matrix.");
        }

        this.S = cov.getMatrix().getDataCopy();
        this.n = cov.getSampleSize();
    }

    public double tetrads(int[][]...tets) {
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

        for (double p : p_values){
            sum += Math.log(p);
        }

        sum *= -2;

//        p_values = [self.tetrad(tet, resample, frac) for tet in tets]
//        p_values = [max(p_value, 1e-16) for p_value in p_values]
//        double combined = -2 * np.sum(np.log(p_values))
//
//        print(S, v)
//        print(p_values)

        return 1.0 - new ChiSquaredDistribution(2 * p_values.size()).cumulativeProbability(sum);

//        return 1 - chi2.cdf(combined, 2 * len(p_values))

    }



    public double tetrad(int[][] tet) {//}, boolean resample, double frac) {
//        SimpleMatrix S1, S2;
//        int n;

//        SimpleMatrix S = computeCovariance(df);

//        if (resample) {
//            // Pseudocode for sampling, replace with actual implementation
//            SimpleMatrix dfSample = df.sample(frac);
//            n = dfSample.getNumRows();
//
//            int splitIndex = (int) (this.sp * n);
//            SimpleMatrix part1 = dfSample.extractMatrix(0, splitIndex, 0, dfSample.numCols());
//            SimpleMatrix part2 = dfSample.extractMatrix(splitIndex, dfSample.numRows(), 0, dfSample.numCols());
//
//            S1 = computeCovariance(part1);
//            S2 = computeCovariance(part2);
//        } else {
//            n = this.n;
//            S1 = this.S1;
//            S2 = this.S2;
//        }

        int[] a = tet[0];
        int[] b = tet[1];
        int z = a.length;

        SimpleMatrix XY = extractSubMatrix(this.S, a, b);
        SimpleSVD<SimpleMatrix> svd = XY.svd();
        SimpleMatrix U = svd.getU();
        SimpleMatrix VT = svd.getV().transpose();

//        if (sp < 1) {
//            XY = extractSubMatrix(this.S, a, b);
//        }
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
        double z_score = arctanh(p_corr) * Math.sqrt(this.n - idx.length - 1);

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

    public static double arctanh(double x) {
        if (x <= -1 || x >= 1) {
            throw new IllegalArgumentException("Input x must be between -1 and 1 (exclusive).");
        }
        return 0.5 * Math.log((1 + x) / (1 - x));
    }
}
