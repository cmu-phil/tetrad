package edu.cmu.tetrad.search.ntad_test;

import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Arrays;
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
     * Constructs a new Cca object based on the provided data matrix and correlation option.
     *
     * @param df           the input data matrix as a SimpleMatrix object, where each row represents an observation and
     *                     each column represents a variable.
     * @param correlations a boolean indicating whether the provided data matrix represents correlations (true) or raw
     *                     data (false). If false, the correlation matrix will be computed from the raw data.
     */
    public Cca(SimpleMatrix df, boolean correlations, int ess) {
        super(df, correlations, ess);
    }

    /**
     * Computes the aggregate statistical measure based on a list of tetrad configurations. Each configuration specifies
     * sets of indices representing structural relationships among variables. This method evaluates and combines results
     * for all provided configurations, with optional resampling.
     *
     * @param ntad     a list of 2D integer arrays where each array contains multiple tetrad configurations. Each
     *                 configuration defines sets of indices representing structural relationships among variables.
     * @param resample a boolean indicating whether resampling should be applied to the data matrix for the
     *                 computation.
     * @param frac     a double value representing the fraction of data to use during resampling, ignored if resample is
     *                 false.
     * @return a double value representing the sum of the statistical measures for all provided tetrad configurations.
     */
    @Override
    public double ntad(int[][] ntad, boolean resample, double frac) {
        // Determine S (either resample or use the default correlation matrix)
        SimpleMatrix S = resample ? computeCorrelations(sampleRows(df, frac)) : this.S;
        int[] a = ntad[0];
        int[] b = ntad[1];
        int n = resample ? (int) (frac * this.ess) : this.ess;

        // Use the getCcaPValueRankD method for rank r = 1 (or make r configurable if needed)
        int r = Math.min(a.length, b.length) - 1;
        return RankTests.rankLeByWilks(S, a, b, n, r);
    }

    @Override
    public double ntad(int[][] ntad) {
        return ntad(ntad, false, 1);
    }

    /**
     * Returns the p-value for the tetrad. This constructor is required by the interface, though in truth it will throw
     * and exception if more than one tetrad is provided.
     *
     * @param ntads A single tetrad.
     * @return The p-value for the tetrad.
     */
    @Override
    public double ntads(int[][]... ntads) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, ntads);
        return ntads(tetList);
    }

    /**
     * Returns the p-value for the tetrad. This constructor is required by the interface, though in truth it will throw
     * and exception if more than one tetrad is provided.
     *
     * @param ntads A single tetrad.
     * @return The p-value for the tetrad.
     */
    @Override
    public double ntads(List<int[][]> ntads) {
        if (ntads.size() != 1) {
            throw new IllegalArgumentException("Only one tetrad is allowed for the CCA test.");
        }

        return ntad(ntads.getFirst());
    }
}
