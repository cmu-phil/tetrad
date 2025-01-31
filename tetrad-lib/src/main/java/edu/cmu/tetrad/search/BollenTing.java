package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.data.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * The BollenTing class implements the NTadTest interface and provides functionality to compute statistical tests based
 * on tetrad configurations. This is commonly used for evaluating structural relationships between variables or
 * assessing model constraints. It supports calculating p-values for specific configurations of variables represented as
 * tetrads, subsets of indices defining relationships among variables.
 * <p>
 * Bollen, K. A., &amp; Ting, K. F. (1993). Confirmatory tetrad analysis. Sociological methodology, 147-175.
 */
public class BollenTing {
    /**
     * Represents the covariance matrix used in the analysis. This matrix is a core component in computations performed
     * within the BollenTing class, including covariance extraction and related statistical tests.
     */
    private final SimpleMatrix S;
    /**
     * Represents the sample size used in the analysis.
     */
    private final int n;

    /**
     * Constructs an instance of the BollenTing class using a DataSet object. The covariance matrix is computed from the
     * given DataSet.
     *
     * @param data the DataSet object from which the covariance matrix is derived
     */
    public BollenTing(DataSet data) {
        this(new CovarianceMatrix(data));
    }

    /**
     * Constructs an instance of the BollenTing class using an ICovarianceMatrix object. The covariance matrix data and
     * sample size are extracted from the provided ICovarianceMatrix instance.
     *
     * @param cov the ICovarianceMatrix object from which the covariance matrix data and sample size are derived
     */
    public BollenTing(ICovarianceMatrix cov) {
        this.S = cov.getMatrix().getDataCopy();
        this.n = cov.getSampleSize();
    }

    /**
     * Computes the tetrad statistic for a single tetrad represented by a 2D array. A tetrad is a mathematical construct
     * used for statistical hypothesis testing.
     *
     * @param tet a 2D integer array representing a single tetrad. The array should comprise two rows, each representing
     *            a set of indices used in the computation.
     * @return the computed tetrad statistic as a double value.
     */
    public double tetrad(int[][] tet) {
        List<int[][]> tetList = new ArrayList<>();
        tetList.add(tet);
        return tetrads(tetList);
    }

    /**
     * Computes the p-value of the tetrads statistic for multiple tetrads represented by an array of 2D arrays. Each 2D
     * array represents a single tetrad, which itself consists of two sets of indices used in the statistical
     * computation.
     *
     * @param tets a variable-length parameter containing one or more 2D integer arrays, where each 2D array represents
     *             a tetrad. Each tetrad consists of two rows, with each row specifying a set of indices to be used in
     *             the tetrad computation.
     * @return the p-value of the computed tetrads statistic as a double value. The p-value provides the probability of
     * observing the given data under a null hypothesis based on the chi-squared distribution.
     */
    public double tetrads(int[][]... tets) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, tets);
        return tetrads(tetList);
    }

    /**
     * ≈ Computes the p-value for the tetrads statistic based on a set of tetrads provided as a list of 2D arrays. Each
     * tetrad is represented by a 2D integer array, where the first row contains one set of indices and the second row
     * contains another set of indices.
     * <p>
     * The computation involves determining the determinant of submatrices derived from the covariance matrix,
     * assembling gradients and sensitivity matrices, and performing statistical testing using the Chi-squared
     * distribution.
     *
     * @param tets a list where each element is a 2D integer array representing a tetrad. Each 2D array must consist of
     *             two rows, with the first row and the second row representing two sets of indices used in the
     *             computation.
     * @return the computed p-value as a double. The p-value quantifies the probability of observing the given data
     * under the null hypothesis, using the Chi-squared distribution.
     */
    public double tetrads(List<int[][]> tets) {
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

            SimpleMatrix AdjT;

            try {
                AdjT = A.invert().transpose().scale(detA);
            } catch (SingularMatrixException e) {
                throw new RuntimeException("AdjT is singular", e);
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
        return 1.0 - chi2.cumulativeProbability(T);
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

