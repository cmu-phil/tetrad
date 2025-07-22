package edu.cmu.tetrad.search.ntad_test;

import edu.cmu.tetrad.util.StatUtils;
import org.ejml.data.DMatrixRMaj;
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
 * Anderson, T. W., Anderson, T. W., Anderson, T. W., &amp; Anderson, T. W. (1958). An introduction to multivariate
 * statistical analysis (Vol. 2, pp. 3-5). New York: Wiley.
 * <p>
 * Huang, B., Low, C. J. H., Xie, F., Glymour, C., &amp; Zhang, K. (2022). Latent hierarchical causal structure
 * discovery with rank constraints. Advances in neural information processing systems, 35, 5549-5561.
 *
 * @author bryanandrews
 */
public class Cca extends NtadTest {

    /**
     * Constructs a new Cca object based on the provided data matrix and covariance option.
     *
     * @param df          the input data matrix as a SimpleMatrix object, where each row represents an observation and
     *                    each column represents a variable.
     * @param covariances a boolean indicating whether the provided data matrix represents covariances (true) or raw
     *                    data (false). If false, the covariance matrix will be computed from the raw data.
     */
    public Cca(SimpleMatrix df, boolean covariances) {
        super(df, covariances);
    }

    /**
     * Computes the aggregate statistical measure based on a list of tetrad configurations. Each configuration specifies
     * sets of indices representing structural relationships among variables. This method evaluates and combines results
     * for all provided configurations, with optional resampling.
     *
     * @param tet     a list of 2D integer arrays where each array contains multiple tetrad configurations. Each
     *                 configuration defines sets of indices representing structural relationships among variables.
     * @param resample a boolean indicating whether resampling should be applied to the data matrix for the
     *                 computation.
     * @param frac     a double value representing the fraction of data to use during resampling, ignored if resample is
     *                 false.
     * @return a double value representing the sum of the statistical measures for all provided tetrad configurations.
     */
    @Override
    public double tetrad(int[][] tet, boolean resample, double frac) {
        // Determine S (either resample or use the default covariance matrix)
        SimpleMatrix S = resample ? computeCovariance(sampleRows(df, frac)) : this.S;
        int[] a = tet[0];
        int[] b = tet[1];
        int n = resample ? (int) (frac * this.n) : this.n;

        // Use the getCcaPValueRankD method for rank d = 1 (or make d configurable if needed)
        int d = 1;  // You can adjust this if you want to explore larger rank tests
        return StatUtils.getCcaPValueRankD(S, a, b, n, d);
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
}
