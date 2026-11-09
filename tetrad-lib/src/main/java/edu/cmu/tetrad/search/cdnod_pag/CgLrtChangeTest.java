package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionBlocks;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Residual-vs-environment change test.
 * <p>
 * Tests the hypothesis Y ⊥ env | Z by (i) regressing Y on Z by ordinary least squares (with
 * intercept and a tiny ridge term for numerical stability) and (ii) testing the residuals against
 * the environment column with a basis-function-based marginal independence test. A small p-value
 * indicates that the conditional distribution of Y given Z varies with the environment, i.e., a
 * change.
 * <p>
 * Notes:
 * <ul>
 *   <li>The residualization step is linear; nonlinear dependence of Y on Z that is not captured by
 *       the linear fit can leak into the residuals and be attributed to the environment. For
 *       strongly nonlinear systems, substitute a ChangeTest backed by a genuinely conditional
 *       test of Y ⊥ env | Z.</li>
 *   <li>If env ∈ Z (or env == y), the test is degenerate and this method returns false (no
 *       change), rather than reporting a meaningless p-value.</li>
 *   <li>The underlying basis-function test embeds the dataset once and is cached per DataSet
 *       instance, so repeated calls during a search do not re-embed.</li>
 * </ul>
 */
public final class CgLrtChangeTest implements ChangeTest {

    /**
     * Ridge term added to the diagonal of Z'Z for numerical stability in the OLS step.
     */
    private static final double RIDGE = 1e-8;

    /**
     * Cache of the underlying marginal independence test, keyed by DataSet identity. The
     * basis-function test performs a one-time embedding of the dataset, so reconstructing it per
     * call would be wasteful.
     */
    private transient DataSet cachedData;
    private transient RawMarginalIndependenceTest cachedTest;

    /**
     * Default constructor.
     */
    public CgLrtChangeTest() {
    }

    /**
     * Determines whether the conditional distribution of y given Z changes with the environment
     * variable, by testing the OLS residuals of y on Z against env.
     *
     * @param data  the dataset containing the variables and observations
     * @param y     the target node to be tested for change
     * @param Z     a set of nodes representing the conditioning variables
     * @param env   the environmental (context) variable being tested against
     * @param alpha the significance level for the hypothesis test
     * @return true if the residual-vs-environment p-value is less than alpha, false otherwise
     */
    @Override
    public boolean changes(DataSet data, Node y, Set<Node> Z, Node env, double alpha) {
        ChangeTest.requireNonNulls(data, y, Z);
        Objects.requireNonNull(env, "env");

        // Degenerate configurations: conditioning on the environment itself, or testing it
        // against itself, cannot exhibit change.
        if (y.getName().equals(env.getName())) return false;
        for (Node z : Z) {
            if (z.getName().equals(env.getName())) return false;
        }

        int n = data.getNumRows();

        double[] yCol = column(data, y);
        double[] envCol = column(data, env);

        List<Node> zList = new ArrayList<>(Z);
        double[][] Zcols = new double[n][zList.size()];
        for (int j = 0; j < zList.size(); j++) {
            double[] col = column(data, zList.get(j));
            for (int i = 0; i < n; i++) Zcols[i][j] = col[i];
        }

        double[] resid = residualize(yCol, Zcols);

        double[][] envMat = new double[n][1];
        for (int i = 0; i < n; i++) envMat[i][0] = envCol[i];

        try {
            double p = testFor(data).computePValue(resid, envMat);
            return p < alpha;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    // ---------------- internals ----------------

    private RawMarginalIndependenceTest testFor(DataSet data) {
        if (data != cachedData) {
            cachedTest = new IndTestBasisFunctionBlocks(data, 4, 1);
            cachedData = data;
        }
        return cachedTest;
    }

    private static double[] column(DataSet data, Node v) {
        Node resolved = data.getVariable(v.getName());
        if (resolved == null) {
            throw new IllegalArgumentException("Variable not in dataset: " + v.getName());
        }
        int idx = data.getVariables().indexOf(resolved);
        double[] col = new double[data.getNumRows()];
        for (int i = 0; i < data.getNumRows(); i++) col[i] = data.getDouble(i, idx);
        return col;
    }

    /**
     * OLS residuals of y on Zcols with an intercept, using normal equations with a small ridge
     * term. If Z is empty, returns the mean-centered y.
     */
    private static double[] residualize(double[] y, double[][] Zcols) {
        int n = y.length;
        int p = (n == 0 || Zcols.length == 0) ? 0 : Zcols[0].length;

        if (p == 0) {
            double mean = 0.0;
            for (double v : y) mean += v;
            mean /= n;
            double[] r = new double[n];
            for (int i = 0; i < n; i++) r[i] = y[i] - mean;
            return r;
        }

        int q = p + 1; // intercept + p regressors
        double[][] xtx = new double[q][q];
        double[] xty = new double[q];
        double[] xi = new double[q];

        for (int i = 0; i < n; i++) {
            xi[0] = 1.0;
            for (int j = 0; j < p; j++) xi[j + 1] = Zcols[i][j];
            for (int a = 0; a < q; a++) {
                xty[a] += xi[a] * y[i];
                for (int b = a; b < q; b++) xtx[a][b] += xi[a] * xi[b];
            }
        }
        for (int a = 0; a < q; a++) {
            for (int b = 0; b < a; b++) xtx[a][b] = xtx[b][a];
            xtx[a][a] += RIDGE;
        }

        Matrix A = new Matrix(xtx);
        Vector b = new Vector(xty);
        Vector beta = A.inverse().times(b);

        double[] r = new double[n];
        for (int i = 0; i < n; i++) {
            double fit = beta.get(0);
            for (int j = 0; j < p; j++) fit += beta.get(j + 1) * Zcols[i][j];
            r[i] = y[i] - fit;
        }
        return r;
    }
}
