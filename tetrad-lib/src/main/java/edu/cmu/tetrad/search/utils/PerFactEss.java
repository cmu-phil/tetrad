package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h2>Per-fact effective sample sizes from block structure</h2>
 *
 * <p>Serially dependent data with block (grouped) structure carries less information than n
 * independent rows, so i.i.d.-based independence tests are anti-conservative and Markov checks
 * yield too many dependence judgments for implied independence constraints. Crucially, the right
 * correction is <em>fact-dependent</em>: for a constraint X _||_ Y | Z in which the participating
 * variables are constant within blocks, the data contains only about as many independent
 * observations as there are blocks, while a constraint involving variables that vary within
 * blocks retains most of the nominal sample size.</p>
 *
 * <p>This class computes, for each independence fact, an effective sample size</p>
 *
 * <pre>    n_eff = n / (1 + (m0 - 1) * icc(X|Z) * icc(Y|Z))</pre>
 *
 * <p>where m0 is the ANOVA average block size for unbalanced designs, m0 = (n - sum_k m_k^2 / n)
 * / (K - 1), and icc(V|Z) is the one-way ANOVA intraclass correlation of V linearly residualized
 * on Z, clamped to [0, 1]. The product-of-ICCs design effect is the standard first-order
 * correction for covariance-type statistics between clustered variables (Kish); it has the right
 * limits: if X and Y are both block-constant (both ICCs 1), n_eff is approximately the number of
 * blocks, and if either varies freely within blocks (ICC near 0), n_eff is approximately n.</p>
 *
 * <p>Limitations, stated plainly: the residualization is linear, so nonlinear conditioning is
 * corrected only to first order; discrete variables are handled through their numeric category
 * codes, which is crude; and the design-effect rescaling assumes the block dependence changes the
 * information count rather than the shape of the test statistic's null distribution. Where these
 * approximations are in doubt, a block bootstrap of the check is the nonparametric validator.</p>
 */
public final class PerFactEss {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private PerFactEss() {
    }

    /**
     * Computes block ids (0..K-1, one per row) from the distinct joint values of the given
     * columns.
     *
     * @param data         The dataset.
     * @param blockColumns Names of the columns whose distinct joint values define the blocks
     *                     (e.g., design or grouping variables identified by the data audit).
     * @return An array of length data.getNumRows() assigning each row its block id.
     */
    public static int[] blockIds(DataSet data, List<String> blockColumns) {
        int n = data.getNumRows();
        int[] cols = new int[blockColumns.size()];
        for (int j = 0; j < blockColumns.size(); j++) {
            Node v = data.getVariable(blockColumns.get(j));
            if (v == null) throw new IllegalArgumentException(
                    "Block column not in data: " + blockColumns.get(j));
            cols[j] = data.getColumnIndex(v);
        }

        Map<String, Integer> ids = new HashMap<>();
        int[] out = new int[n];
        StringBuilder key = new StringBuilder();

        for (int i = 0; i < n; i++) {
            key.setLength(0);
            for (int c : cols) key.append(data.getDouble(i, c)).append('|');
            out[i] = ids.computeIfAbsent(key.toString(), k -> ids.size());
        }

        return out;
    }

    /**
     * Computes the per-fact effective sample size for X _||_ Y | Z under the given block
     * structure.
     *
     * @param data     The dataset (variables matched to the fact's nodes by name).
     * @param blockIds Row-to-block assignment, as from {@link #blockIds(DataSet, List)}. If null,
     *                 or if there are fewer than 2 blocks, the nominal sample size is returned.
     * @param fact     The independence fact.
     * @return The effective sample size, clamped to [max(8, |Z| + 5), n].
     */
    public static int effectiveSampleSize(DataSet data, int[] blockIds, IndependenceFact fact) {
        return effectiveSampleSize(data, blockIds, fact.getX(), fact.getY(), fact.getZ());
    }

    /**
     * Computes the per-fact effective sample size for x _||_ y | z under the given block
     * structure.
     *
     * @param data     The dataset (variables matched by name).
     * @param blockIds Row-to-block assignment; null means no block structure.
     * @param x        The first variable.
     * @param y        The second variable.
     * @param z        The conditioning variables.
     * @return The effective sample size, clamped to [max(8, |z| + 5), n].
     */
    public static int effectiveSampleSize(DataSet data, int[] blockIds, Node x, Node y,
                                          Set<Node> z) {
        int n = data.getNumRows();
        if (blockIds == null || blockIds.length != n) return n;

        int numBlocks = 0;
        for (int id : blockIds) numBlocks = Math.max(numBlocks, id + 1);
        if (numBlocks < 2) return n;

        int[] zCols = new int[z.size()];
        int j = 0;
        for (Node v : z) zCols[j++] = column(data, v);

        double[] rx = residualize(data, column(data, x), zCols);
        double[] ry = residualize(data, column(data, y), zCols);

        double m0 = anovaAverageBlockSize(blockIds, numBlocks, n);
        double deff = 1.0 + (m0 - 1.0) * icc(rx, blockIds, numBlocks) * icc(ry, blockIds, numBlocks);
        int nEff = (int) Math.round(n / deff);

        int floor = Math.max(8, z.size() + 5);
        return Math.max(floor, Math.min(n, nEff));
    }

    /**
     * One-way ANOVA intraclass correlation of v across blocks, clamped to [0, 1]. Block-constant
     * variables get 1; variables with no between-block variance get 0.
     */
    private static double icc(double[] v, int[] blockIds, int numBlocks) {
        int n = v.length;
        double[] sum = new double[numBlocks];
        int[] count = new int[numBlocks];
        double grand = 0.0;

        for (int i = 0; i < n; i++) {
            sum[blockIds[i]] += v[i];
            count[blockIds[i]]++;
            grand += v[i];
        }
        grand /= n;

        double ssb = 0.0;
        int usedBlocks = 0;
        for (int k = 0; k < numBlocks; k++) {
            if (count[k] == 0) continue;
            usedBlocks++;
            double mean = sum[k] / count[k];
            ssb += count[k] * (mean - grand) * (mean - grand);
        }

        double ssw = 0.0;
        for (int i = 0; i < n; i++) {
            double mean = sum[blockIds[i]] / count[blockIds[i]];
            ssw += (v[i] - mean) * (v[i] - mean);
        }

        if (usedBlocks < 2 || ssb + ssw <= 0.0) return 0.0;

        double msb = ssb / (usedBlocks - 1);
        double msw = n - usedBlocks > 0 ? ssw / (n - usedBlocks) : 0.0;
        double m0 = anovaAverageBlockSize(blockIds, numBlocks, n);

        if (msw <= 0.0) return msb > 0.0 ? 1.0 : 0.0;

        double icc = (msb - msw) / (msb + (m0 - 1.0) * msw);
        return Math.max(0.0, Math.min(1.0, icc));
    }

    /**
     * The ANOVA average block size for unbalanced designs, m0 = (n - sum m_k^2 / n) / (K - 1).
     */
    private static double anovaAverageBlockSize(int[] blockIds, int numBlocks, int n) {
        int[] count = new int[numBlocks];
        for (int id : blockIds) count[id]++;

        double sumSq = 0.0;
        int usedBlocks = 0;
        for (int c : count) {
            if (c > 0) {
                usedBlocks++;
                sumSq += (double) c * c;
            }
        }

        if (usedBlocks < 2) return n;
        return (n - sumSq / n) / (usedBlocks - 1);
    }

    /**
     * Linearly residualizes column yCol on the columns in zCols (with intercept); with no
     * conditioning columns, returns the centered column. A small ridge guards singular designs.
     */
    private static double[] residualize(DataSet data, int yCol, int[] zCols) {
        int n = data.getNumRows();
        double[] y = new double[n];
        for (int i = 0; i < n; i++) y[i] = data.getDouble(i, yCol);

        double mean = 0.0;
        for (double v : y) mean += v;
        mean /= n;
        for (int i = 0; i < n; i++) y[i] -= mean;

        if (zCols.length == 0) return y;

        int p = zCols.length;
        double[][] zc = new double[n][p];
        for (int j = 0; j < p; j++) {
            double m = 0.0;
            for (int i = 0; i < n; i++) m += data.getDouble(i, zCols[j]);
            m /= n;
            for (int i = 0; i < n; i++) zc[i][j] = data.getDouble(i, zCols[j]) - m;
        }

        double[][] a = new double[p][p];
        double[] b = new double[p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                b[j] += zc[i][j] * y[i];
                for (int k = j; k < p; k++) a[j][k] += zc[i][j] * zc[i][k];
            }
        }
        for (int j = 0; j < p; j++) {
            for (int k = 0; k < j; k++) a[j][k] = a[k][j];
            a[j][j] += 1e-8 * (a[j][j] + 1.0);   // ridge for singular designs
        }

        double[] beta = solve(a, b);

        double[] resid = new double[n];
        for (int i = 0; i < n; i++) {
            double fit = 0.0;
            for (int j = 0; j < p; j++) fit += zc[i][j] * beta[j];
            resid[i] = y[i] - fit;
        }
        return resid;
    }

    /**
     * Solves a x = b by Gauss-Jordan elimination with partial pivoting (a is small and made
     * nonsingular by the ridge above).
     */
    private static double[] solve(double[][] a, double[] b) {
        int p = b.length;
        double[][] m = new double[p][p + 1];
        for (int i = 0; i < p; i++) {
            System.arraycopy(a[i], 0, m[i], 0, p);
            m[i][p] = b[i];
        }

        for (int col = 0; col < p; col++) {
            int pivot = col;
            for (int r = col + 1; r < p; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) pivot = r;
            }
            double[] t = m[col];
            m[col] = m[pivot];
            m[pivot] = t;

            double d = m[col][col];
            if (d == 0.0) continue;
            for (int c = col; c <= p; c++) m[col][c] /= d;
            for (int r = 0; r < p; r++) {
                if (r == col) continue;
                double f = m[r][col];
                if (f == 0.0) continue;
                for (int c = col; c <= p; c++) m[r][c] -= f * m[col][c];
            }
        }

        double[] x = new double[p];
        for (int i = 0; i < p; i++) x[i] = m[i][p];
        return x;
    }

    /**
     * Resolves a fact node to a data column by name.
     */
    private static int column(DataSet data, Node node) {
        Node v = data.getVariable(node.getName());
        if (v == null) throw new IllegalArgumentException(
                "Fact variable not in data: " + node.getName());
        return data.getColumnIndex(v);
    }

    /**
     * Convenience: the number of distinct blocks in a block-id assignment.
     *
     * @param blockIds Row-to-block assignment.
     * @return The number of distinct blocks.
     */
    public static int numBlocks(int[] blockIds) {
        int max = -1;
        for (int id : blockIds) max = Math.max(max, id);
        return max + 1;
    }
}
