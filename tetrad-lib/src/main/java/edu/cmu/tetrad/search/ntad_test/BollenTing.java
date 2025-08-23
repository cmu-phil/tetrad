package edu.cmu.tetrad.search.ntad_test;

import edu.cmu.tetrad.util.StatUtils;
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

    /**
     * Constructs a BollenTing object for performing statistical operations. This constructor initializes the instance
     * using a data matrix and assumes the input matrix is treated as raw data from which correlations should be
     * calculated.
     *
     * @param df  the input data matrix as a SimpleMatrix object, where each row represents an observation and each
     *            column represents a variable.
     * @param ess an integer value representing the effective sample size used in statistical computations.
     */
    public BollenTing(SimpleMatrix df, int ess) {
        this(df, false, ess);
    }

    /**
     * Constructs a BollenTing object for performing statistical operations. This constructor initializes the instance
     * using a data matrix and a boolean flag indicating whether corelation matrices should be used directly or computed
     * from the input data.
     *
     * @param df           the input data matrix as a SimpleMatrix object, where each row represents an observation and
     *                     each column represents a variable.
     * @param correlations a boolean flag that determines whether the input matrix is treated as a correlation matrix
     *                     (true) or as raw data from which correlations should be calculated (false).
     * @param ess          the effective sample size used in the computation, or -1 if the actual sample size is to be
     *                     used.
     */
    public BollenTing(SimpleMatrix df, boolean correlations, int ess) {
        super(df, correlations, ess);
    }

    /**
     * Computes the NTAD statistic for a given 2D array of integer pairs. The method determines the fit of specified
     * pairs in the provided NTAD based on correlation computations and statistical distribution measures. An optional
     * flag allows for resampling based on input data with a specified fraction, altering the results dynamically.
     *
     * @param ntad     a 2D array where each sub-array contains pairs of integers indicating relationships to evaluate.
     * @param resample a boolean flag that specifies whether the computations should involve resampling the data.
     * @param frac     a double representing the fraction of the data to use if resampling is enabled.
     * @return a double value representing the NTAD significance statistic, indicating fit or anomalies.
     */
    @Override
    public double ntad(int[][] ntad, boolean resample, double frac) {
        return ntads(Collections.singletonList(ntad), resample, frac);
    }

    /**
     * Computes the NTADS for a given list of NTAD (Non-Testable Assumption Detection) structures. Each NTAD structure
     * is represented as a 2D array of integer pairs, where each pair denotes a set of variables to evaluate. This
     * method calculates the NTADS significance statistic using correlation matrices and chi-squared distribution for
     * hypothesis testing. It optionally supports resampling to dynamically alter the effective sample size.
     *
     * @param ntads    a list of 2D arrays, where each 2D array contains integer pairs representing the NTAD structures
     *                 to evaluate
     * @param resample a boolean flag indicating whether resampling of the data rows should be performed for the
     *                 calculations
     * @param frac     a double representing the fraction of the data to sample when resampling is enabled
     * @return a double value representing the NTADS significance statistic, with a higher value indicating a better fit
     * or reduced anomalies
     */
    @Override
    public double ntads(List<int[][]> ntads, boolean resample, double frac) {
        SimpleMatrix S = resample ? computeCorrelations(sampleRows(df, frac)) : this.S;
        int n = resample ? (int) (frac * this.ess) : this.ess;

        Set<Integer> V = new HashSet<>();
        for (int[][] tet : ntads) {
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

        SimpleMatrix dt_ds = new SimpleMatrix(pairs.size(), ntads.size());
        SimpleMatrix t = new SimpleMatrix(ntads.size(), 1);

        for (int i = 0; i < ntads.size(); i++) {
            int[][] tet = ntads.get(i);
            int[] a = tet[0];
            int[] b = tet[1];
            SimpleMatrix A = StatUtils.extractSubMatrix(S, a, b);
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

        return 1 - new ChiSquaredDistribution(ntads.size()).cumulativeProbability(T);
    }

    /**
     * Computes the NTAD statistic for a given 2D array of integer pairs. This method packages the single NTAD structure
     * into a list and delegates the computation to the NTADS method.
     *
     * @param ntad a 2D array where each sub-array contains pairs of integers representing relationships to evaluate.
     * @return a double value representing the NTAD significance statistic, based on the evaluation of the provided
     * structure.
     */
    @Override
    public double ntad(int[][] ntad) {
        List<int[][]> tetList = new ArrayList<>();
        tetList.add(ntad);
        return ntads(tetList);
    }

    /**
     * Computes the NTADS (Non-Testable Assumption Detection Score) for a given set of NTAD structures, each passed as a
     * 2D array. This method converts variable-length NTAD arrays into a list and delegates the computation to the
     * overloaded ntads method.
     *
     * @param ntads a variable-length array where each element is a 2D array containing integer pairs that define the
     *              NTAD structures to evaluate.
     * @return a double value representing the NTADS significance statistic, with a higher value indicating a better fit
     * or reduced anomalies.
     */
    @Override
    public double ntads(int[][]... ntads) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, ntads);
        return ntads(tetList);
    }

    @Override
    public double ntads(List<int[][]> ntads) {
        return ntads(ntads, false, 1);
    }
}
