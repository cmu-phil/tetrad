package edu.cmu.tetrad.search.ntad_test;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * The BollenTing class extends the NtadTest class and provides statistical methods for computing tetrad and tetrads
 * statistics. These computations are used in hypothesis testing and involve tetrad determinants and p-values derived
 * from chi-squared distributions. The class supports handling single and multiple tetrads, as well as flexible options
 * such as resampling and fractional sampling rates.
 */
public class BollenTing extends NtadTest {
    public BollenTing(SimpleMatrix df) {
        this(df, false);
    }

    public BollenTing(SimpleMatrix df, boolean covariances) {
        super(df, covariances);
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

    @Override
    public double tetrad(int[][] tet) {
        List<int[][]> tetList = new ArrayList<>();
        tetList.add(tet);
        return tetrads(tetList);
    }

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
