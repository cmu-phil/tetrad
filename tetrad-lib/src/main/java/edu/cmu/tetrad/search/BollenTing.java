package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.data.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BollenTing {

    private final int n;
    private final SimpleMatrix df;
    private SimpleMatrix S;

    public BollenTing(SimpleMatrix df) {
        this.df = df;
        this.S = computeCovariance(df);
        this.n = df.numRows();
    }

    public BollenTing(ICovarianceMatrix cov) {
        this.df = null;
        this.S = cov.getMatrix().getDataCopy();
        this.n = cov.getSampleSize();
    }

    public double tetrad(int[][] tet) {//}, boolean resample, double frac) {
        List<int[][]> tetList = new ArrayList<>();
        tetList.add(tet);
        return tetrads(tetList); ///, resample, frac);
    }

    public double tetrads(List<int[][]> tets) {//}, boolean resample, double frac) {
//        SimpleMatrix S;
//        int n;
//
//        if (resample) {
//            // Assume df.sample(frac) is handled elsewhere
//            SimpleMatrix dfSample = df.sample(frac);  // Pseudocode: Replace with actual sampling logic
//            n = dfSample.numRows();
//            S = computeCovariance(dfSample);
//        } else {
//            n = this.n;
//            S = this.S;
//        }

        Set<Integer> V = new HashSet<>();
        for (int[][] tet : tets) {
            for (int i = 0; i < 2; i++) {
                for (int x : tet[i]) {
                    V.add(x);
                }
            }
        }

        List<int[]> s = generateSortedPairs(V);
        int lenS = s.size();
        SimpleMatrix ss = new SimpleMatrix(lenS, lenS);

        for (int i = 0; i < lenS; i++) {
            for (int j = 0; j < lenS; j++) {
                int[] x = s.get(i);
                int[] y = s.get(j);
                ss.set(i, j, S.get(x[0], y[0]) * S.get(x[1], y[1]) +
                             S.get(x[0], y[1]) * S.get(x[1], y[0]));
            }
        }

        SimpleMatrix dt_ds = new SimpleMatrix(lenS, tets.size());
        SimpleMatrix t = new SimpleMatrix(tets.size(), 1);

        for (int i = 0; i < tets.size(); i++) {
            int[][] tet = tets.get(i);
            int[] a = tet[0];
            int[] b = tet[1];

            SimpleMatrix A = extractSubMatrix(S, a, b);
            double detA = A.determinant();
            t.set(i, 0, detA);

//            if (MatrixFeatures_DDRM.isSingular(A.getDDRM())) {
//                throw new RuntimeException("Matrix is singular, cannot compute inverse.");
//            }

            SimpleMatrix AdjT;

            try {
                AdjT = A.invert().transpose().scale(detA);
            } catch (SingularMatrixException e) {
                throw new RuntimeException("Singular matrix", e);
            }

            for (int j = 0; j < lenS; j++) {
                int[] x = s.get(j);
                for (int k = 0; k < a.length; k++) {
                    for (int l = 0; l < b.length; l++) {
                        if (contains(x, a[k]) && contains(x, b[l])) {
                            dt_ds.set(j, i, AdjT.get(k, l));
                        }
                    }
                }
            }
        }

        SimpleMatrix tt = dt_ds.transpose().mult(ss).mult(dt_ds);
        SimpleMatrix ttInv = tt.invert();
        double T = n * t.transpose().mult(ttInv).mult(t).get(0, 0);

        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(tets.size());
        return 1 - chi2.cumulativeProbability(T);
    }

    private boolean contains(int[] array, int value) {
        for (int v : array) {
            if (v == value) return true;
        }
        return false;
    }

    private List<int[]> generateSortedPairs(Set<Integer> V) {
        List<int[]> pairs = new ArrayList<>();
        List<Integer> sortedList = new ArrayList<>(V);
        sortedList.sort(Integer::compare);
        for (int i = 0; i < sortedList.size(); i++) {
            for (int j = i + 1; j < sortedList.size(); j++) {
                pairs.add(new int[]{sortedList.get(i), sortedList.get(j)});
            }
        }
        return pairs;
    }

    private SimpleMatrix computeCovariance(SimpleMatrix data) {
        int n = data.getNumRows();
        int m = data.getNumCols();

        // Compute the mean of each column
        SimpleMatrix mean = new SimpleMatrix(1, m);
        for (int i = 0; i < m; i++) {
            mean.set(0, i, data.extractVector(false, i).elementSum() / n);
        }

        // Center the data (subtract mean from each column)
        SimpleMatrix centeredData = new SimpleMatrix(n, m);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                centeredData.set(i, j, data.get(i, j) - mean.get(0, j));
            }
        }

        // Compute covariance matrix: S = (X^T * X) / (n - 1)
        return centeredData.transpose().mult(centeredData).scale(1.0 / (n - 1));
    }

    private SimpleMatrix extractSubMatrix(SimpleMatrix S, int[] rows, int[] cols) {
        SimpleMatrix subMatrix = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                subMatrix.set(i, j, S.get(rows[i], cols[j]));
            }
        }
        return subMatrix;
    }

}

