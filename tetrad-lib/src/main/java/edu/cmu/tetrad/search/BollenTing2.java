package edu.cmu.tetrad.search;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

class BollenTing2 extends NTadTest2 {
    public BollenTing2(SimpleMatrix df) {
        super(df);
    }

    @Override
    public double tetrad(int[][] tet, boolean resample, double frac) {
        return tetrads(Collections.singletonList(tet), resample, frac);
    }

    @Override
    public double tetrads(List<int[][]> tets, boolean resample, double frac) {
        SimpleMatrix S = resample ? computeCovariance(sampleRows(df, frac)) : this.S;
        int n = resample ? (int) (frac * this.n) : this.n;

        Set<Integer> V = new HashSet<>();
        for (int[][] tet : tets) {
            for (int[] pair : tet) {
                for (int v : pair) {
                    V.add(v);
                }
            }
        }

        List<int[]> pairs = generateCombinations(new ArrayList<>(V), 2);
        SimpleMatrix ss = new SimpleMatrix(pairs.size(), pairs.size());

        for (int i = 0; i < pairs.size(); i++) {
            for (int j = 0; j < pairs.size(); j++) {
                int[] x = pairs.get(i);
                int[] y = pairs.get(j);
                ss.set(i, j, S.get(x[0], y[0]) * S.get(x[1], y[1]) + S.get(x[0], y[1]) * S.get(x[1], y[0]));
            }
        }

        SimpleMatrix dt_ds = new SimpleMatrix(pairs.size(), tets.size());
        SimpleMatrix t = new SimpleMatrix(tets.size(), 1);

        for (int i = 0; i < tets.size(); i++) {
            int[][] tet = tets.get(i);
            int[] a = tet[0];
            int[] b = tet[1];
            SimpleMatrix A = extractSubMatrix(S, a, b);
            double detA = CommonOps_DDRM.det(A.getDDRM());
            t.set(i, 0, detA);

            SimpleMatrix AdjT = A.invert().transpose().scale(detA);
            for (int j = 0; j < pairs.size(); j++) {
                int[] pair = pairs.get(j);
                for (int k = 0; k < a.length; k++) {
                    for (int l = 0; l < b.length; l++) {
                        if (contains(pair, a[k]) && contains(pair, b[l])) {
                            dt_ds.set(j, i, AdjT.get(k, l));
                        }
                    }
                }
            }
        }

        SimpleMatrix tt = dt_ds.transpose().mult(ss).mult(dt_ds);
        SimpleMatrix ttInv = tt.invert();
        double T = n * t.transpose().mult(ttInv).mult(t).get(0, 0);

        return 1 - new ChiSquaredDistribution(tets.size()).cumulativeProbability(T);
    }

    /**
     * Computes the tetrad statistic for a single tetrad represented by a 2D array. A tetrad is a mathematical construct
     * used for statistical hypothesis testing.
     *
     * @param tet a 2D integer array representing a single tetrad. The array should comprise two rows, each representing
     *            a set of indices used in the computation.
     * @return the computed tetrad statistic as a double value.
     */
    @Override
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
    @Override
    public double tetrads(int[][]... tets) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, tets);
        return tetrads(tetList);
    }

    @Override
    public double tetrads(List<int[][]> tets) {
        return tetrads(tets, false, 1);
    }
}
