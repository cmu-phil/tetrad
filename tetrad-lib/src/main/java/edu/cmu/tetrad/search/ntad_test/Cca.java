package edu.cmu.tetrad.search.ntad_test;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Cca class extends the NtadTest class and provides a mechanism to perform Canonical Correlation Analysis (CCA) as
 * a rank-based way of getting a p-value for a tetrad.
 * <p>
 * Anderson, T. W., Anderson, T. W., Anderson, T. W., & Anderson, T. W. (1958). An introduction to multivariate
 * statistical analysis (Vol. 2, pp. 3-5). New York: Wiley.
 * <p>
 * Huang, B., Low, C. J. H., Xie, F., Glymour, C., & Zhang, K. (2022). Latent hierarchical causal structure discovery
 * with rank constraints. Advances in neural information processing systems, 35, 5549-5561.
 *
 * @author bryanandrews
 */
public class Cca extends NtadTest {

    /**
     * Constructs a new Cca object based on the provided data matrix and covariance option.
     *
     * @param df          the input data matrix as a SimpleMatrix object, where each row represents an observation
     *                    and each column represents a variable.
     * @param covariances a boolean indicating whether the provided data matrix represents covariances (true)
     *                    or raw data (false). If false, the covariance matrix will be computed from the raw data.
     */
    public Cca(SimpleMatrix df, boolean covariances) {
        super(df, covariances);
    }

    @Override
    public double tetrad(int[][] tet, boolean resample, double frac) {
        int d = 1;

        SimpleMatrix S = resample ? computeCovariance(sampleRows(df, frac)) : this.S;
        int[] a = tet[0];
        int[] b = tet[1];
        int n = resample ? (int) (frac * this.n) : this.n;
        int k = a.length;

        // Extract submatrices
        SimpleMatrix XX = extractSubMatrix(S, a, a);
        SimpleMatrix YY = extractSubMatrix(S, b, b);
        SimpleMatrix XY = extractSubMatrix(S, a, b);

        // Perform Cholesky decompositions and their inverses
        SimpleMatrix XXir = chol(XX).invert();
        SimpleMatrix YYir = chol(YY).invert();

        // Compute singular values of the product XXir * XY * YYir
        SimpleMatrix product = XXir.mult(XY).mult(YYir);
        double[] singularValues = product.svd().getSingularValues();

        // Select the last `d` singular values and apply the statistic
        double stat = 0.0;
        for (int i = singularValues.length - d; i < singularValues.length; i++) {
            double adjustedValue = 1 - Math.pow(singularValues[i], 2);
            adjustedValue = Math.max(adjustedValue, 1e-6);  // Clip to avoid log(0)
            stat += Math.log(adjustedValue);
        }

        // Final calculation using the given formula
        stat *= (k + 3.0 / 2.0 - n);
        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(d * d);
        return 1 - chi2.cumulativeProbability(stat);
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
            throw new IllegalArgumentException("Only one tetrad is allowed for the CCA test.");
        }

        return tetrad(tets.getFirst());
    }

    private SimpleMatrix chol(SimpleMatrix A) {
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(A.getNumRows(), true);

        if (!chol.decompose(A.getMatrix()))
            throw new RuntimeException("Cholesky failed!");

        return SimpleMatrix.wrap(chol.getT(null));
    }
}
