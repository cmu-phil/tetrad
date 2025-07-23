package edu.cmu.tetrad.search.ntad_test;

import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Wishart class is a concrete implementation of the NtadTest abstract class, specifically for statistical tests
 * based on the Wishart distribution. It performs calculations for tetrads and their associated p-values using
 * covariance matrices derived from the input data.
 *
 * @author bryanandrews
 */
public class Wishart extends NtadTest {

    /**
     * Constructs a Wishart test object based on the given data matrix and covariance option. This method initializes
     *
     * @param df          the input data matrix as a SimpleMatrix object, where each row represents an observation and
     *                    each
     * @param covariances a boolean flag indicating whether to compute covariances.
     */
    public Wishart(SimpleMatrix df, boolean covariances) {
        super(df, covariances);
    }

    @Override
    public double tetrad(int[][] tet, boolean resample, double frac) {
        SimpleMatrix S = resample ? computeCorrelations(sampleRows(df, frac)) : this.S;
        int[] a = tet[0];
        int[] b = tet[1];
        int n = resample ? (int) (frac * this.n) : this.n;

        double sigma2 = (double) (n + 1) / (n - 1) * determinant(StatUtils.extractSubMatrix(S, a, a)) * determinant(StatUtils.extractSubMatrix(S, b, b))
                        - determinant(StatUtils.extractSubMatrix(S, concat(a, b), concat(a, b))) / (n - 2);

        double z_score = determinant(StatUtils.extractSubMatrix(S, a, b)) / Math.sqrt(sigma2);
        return 2 * new NormalDistribution().cumulativeProbability(-Math.abs(z_score));
    }

    private int[] concat(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private double determinant(SimpleMatrix matrix) {
        return CommonOps_DDRM.det(matrix.getDDRM());
    }

    @Override
    public double tetrad(int[][] tet) {
        return tetrad(tet, false, 1);
    }

    /**
     * Returns the p-value for the tetrad. This constructor is required by the interface, though in truth it will throw
     * and exception if more than one tetrad is provided.
     *
     * @param tets A single tetrad.
     * @return The p-value for the tetrad.
     */
    @Override
    public double tetrads(int[][]... tets) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, tets);
        return tetrads(tetList);
    }

    /**
     * Returns the p-value for the tetrad. This constructor is required by the interface, though in truth it will throw
     * and exception if more than one tetrad is provided.
     *
     * @param tets A single tetrad.
     * @return The p-value for the tetrad.
     */
    @Override
    public double tetrads(List<int[][]> tets) {
        if (tets.size() != 1) {
            throw new IllegalArgumentException("Only one tetrad is allowed for the Wishart test.");
        }

        return tetrad(tets.getFirst());
    }
}
