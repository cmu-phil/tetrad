package edu.cmu.tetrad.study.missingness;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Degenerate-Gaussian likelihood-ratio conditional-independence testing under multiple imputation, pooled by
 * the rules for likelihood-ratio statistics: Meng and Rubin (1992) ("D3", the default, which is what the micd
 * package Witte et al. used implements for its tests), or Li, Meng, Raghunathan and Rubin (1991) ("D2", which
 * pools only the per-imputation chi-square statistics and is included for comparison, being simpler but
 * documented to lose power when the fraction of missing information is large).
 *
 * <p>The per-imputation statistic mirrors {@link edu.cmu.tetrad.search.test.IndTestDegenerateGaussianLrt} on
 * complete data exactly: each variable is embedded (a continuous variable as itself, a discrete variable with
 * C categories as C - 1 indicators), and the statistic for X indep Y given Z is n log(sigma0^2 / sigma1^2),
 * where sigma^2 is the trace of the residual covariance of X's embedded block regressed on the predictors'
 * embedded block, divided by the block size, referred to chi-square with df = the number of Y's embedded
 * columns. D3 additionally needs the statistic evaluated at POOLED parameters: the regression coefficient
 * matrices are averaged across imputations and the residual variance each pooled fit achieves is evaluated on
 * every imputation's covariance by the full quadratic form (the trace shortcut holds only at a dataset's own
 * least-squares solution). Intercepts are profiled out by per-dataset centering rather than pooled, a
 * simplification inside the DG working model.</p>
 *
 * <p>Caveats inherited and introduced: the pooled "likelihood ratio" is the DG working-model ratio on embedded
 * indicators, so Meng-Rubin's guarantees extend only as far as the DG approximation itself; and the base
 * test's asymmetry in X and Y (X's residual block, Y's degrees of freedom) is replicated, not fixed. The m
 * completed datasets must share variables and row count.</p>
 *
 * @author josephramsey
 */
public final class PooledDgLrtTest implements IndependenceTest {

    /** The pooling rule. */
    public enum Pooling {
        /** Meng-Rubin 1992: pools via the statistic at averaged parameters. The default. */
        D3,
        /** Li-Meng-Raghunathan-Rubin 1991: pools the chi-square statistics alone. */
        D2
    }

    private final DataSet first;
    private final List<Node> variables;
    private final Map<Node, Integer> indexOf = new ConcurrentHashMap<>();
    private final Pooling pooling;
    private final int n;
    private final int m;

    /** Embedded column indices per variable. */
    private final int[][] embeddedCols;

    /** Per-imputation covariance of the embedded columns, centered at that imputation's own means. */
    private final List<RealMatrix> covs = new ArrayList<>();

    private volatile double alpha;
    private volatile boolean verbose = false;

    /**
     * Constructs the pooled test over completed datasets.
     *
     * @param imputed The m completed datasets, sharing variables and row count.
     * @param alpha   The significance cutoff.
     * @param pooling The pooling rule; D3 unless there is a reason otherwise.
     */
    public PooledDgLrtTest(List<DataSet> imputed, double alpha, Pooling pooling) {
        if (imputed == null || imputed.isEmpty()) throw new IllegalArgumentException("No imputed datasets.");

        this.first = imputed.get(0);
        this.variables = new ArrayList<>(this.first.getVariables());
        for (int j = 0; j < this.variables.size(); j++) this.indexOf.put(this.variables.get(j), j);
        this.pooling = pooling;
        this.n = this.first.getNumRows();
        this.m = imputed.size();
        this.alpha = alpha;

        // Embedding: continuous -> one column; discrete with C categories -> C - 1 indicators (category 0 the
        // reference). Same layout for every imputation.
        int p = this.variables.size();
        this.embeddedCols = new int[p][];
        int width = 0;

        for (int j = 0; j < p; j++) {
            int cols = this.variables.get(j) instanceof DiscreteVariable dv
                    ? Math.max(1, dv.getNumCategories() - 1) : 1;
            this.embeddedCols[j] = new int[cols];
            for (int c = 0; c < cols; c++) this.embeddedCols[j][c] = width++;
        }

        for (DataSet d : imputed) {
            if (d.getNumRows() != this.n || d.getNumColumns() != p) {
                throw new IllegalArgumentException("Imputed datasets must share variables and row count.");
            }
            this.covs.add(embeddedCovariance(d, width));
        }
    }

    private RealMatrix embeddedCovariance(DataSet d, int width) {
        double[][] emb = new double[this.n][width];

        for (int j = 0; j < this.variables.size(); j++) {
            int[] cols = this.embeddedCols[j];
            if (this.variables.get(j) instanceof DiscreteVariable) {
                for (int i = 0; i < this.n; i++) {
                    int v = d.getInt(i, j);
                    for (int c = 0; c < cols.length; c++) emb[i][cols[c]] = v == c + 1 ? 1.0 : 0.0;
                }
            } else {
                for (int i = 0; i < this.n; i++) emb[i][cols[0]] = d.getDouble(i, j);
            }
        }

        double[] mean = new double[width];
        for (int c = 0; c < width; c++) {
            for (int i = 0; i < this.n; i++) mean[c] += emb[i][c];
            mean[c] /= this.n;
        }

        double[][] cov = new double[width][width];
        for (int i = 0; i < this.n; i++) {
            for (int a = 0; a < width; a++) {
                double da = emb[i][a] - mean[a];
                for (int b = a; b < width; b++) cov[a][b] += da * (emb[i][b] - mean[b]);
            }
        }
        for (int a = 0; a < width; a++) {
            for (int b = a; b < width; b++) {
                cov[a][b] /= (this.n - 1);
                cov[b][a] = cov[a][b];
            }
        }

        return new Array2DRowRealMatrix(cov, false);
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        int xi = this.indexOf.get(x);
        int yi = this.indexOf.get(y);

        int[] xCols = this.embeddedCols[xi];
        int[] yCols = this.embeddedCols[yi];

        List<Integer> zColsList = new ArrayList<>();
        for (Node node : z) {
            for (int c : this.embeddedCols[this.indexOf.get(node)]) zColsList.add(c);
        }
        int[] zCols = zColsList.stream().mapToInt(Integer::intValue).toArray();
        int[] yzCols = concat(yCols, zCols);

        int h = yCols.length;
        double p = pooledPValue(xCols, zCols, yzCols, h);

        boolean independent = p > this.alpha;
        return new IndependenceResult(new IndependenceFact(x, y, z), independent, p, this.alpha - p);
    }

    private double pooledPValue(int[] xCols, int[] zCols, int[] yzCols, int h) {
        double[] d = new double[this.m];
        RealMatrix[] betaNull = new RealMatrix[this.m];
        RealMatrix[] betaAlt = new RealMatrix[this.m];

        for (int k = 0; k < this.m; k++) {
            RealMatrix cov = this.covs.get(k);

            RealMatrix b0 = olsBeta(cov, xCols, zCols);
            RealMatrix b1 = olsBeta(cov, xCols, yzCols);
            if (b1 == null) return 1.0; // singular alternative: be conservative, as the base test is

            double s0 = residualVariance(cov, xCols, zCols, b0);
            double s1 = residualVariance(cov, xCols, yzCols, b1);

            d[k] = Math.max(0.0, this.n * Math.log(Math.max(1e-10, s0) / Math.max(1e-10, s1)));
            betaNull[k] = b0;
            betaAlt[k] = b1;
        }

        double dBar = 0;
        for (double v : d) dBar += v;
        dBar /= this.m;

        if (this.m == 1) {
            return 1.0 - new ChiSquaredDistribution(h).cumulativeProbability(dBar);
        }

        if (this.pooling == Pooling.D2) {
            return d2PValue(d, dBar, h);
        }

        // D3: the statistic at the POOLED coefficients, averaged over imputations.
        RealMatrix b0Bar = average(betaNull);
        RealMatrix b1Bar = average(betaAlt);

        double dTilde = 0;
        for (int k = 0; k < this.m; k++) {
            RealMatrix cov = this.covs.get(k);
            double s0 = residualVariance(cov, xCols, zCols, b0Bar);
            double s1 = residualVariance(cov, xCols, yzCols, b1Bar);
            dTilde += Math.max(0.0, this.n * Math.log(Math.max(1e-10, s0) / Math.max(1e-10, s1)));
        }
        dTilde /= this.m;

        double r = Math.max(0.0, (this.m + 1.0) / (h * (this.m - 1.0)) * (dBar - dTilde));

        if (r < 1e-12) {
            return 1.0 - new ChiSquaredDistribution(h).cumulativeProbability(dTilde);
        }

        double stat = dTilde / (h * (1.0 + r));
        double t = h * (this.m - 1.0);
        double w = t > 4.0
                ? 4.0 + (t - 4.0) * Math.pow(1.0 + (1.0 - 2.0 / t) / r, 2)
                : 0.5 * t * (1.0 + 1.0 / h) * Math.pow(1.0 + 1.0 / r, 2);

        return 1.0 - new FDistribution(h, Math.max(1.0, w)).cumulativeProbability(Math.max(0.0, stat));
    }

    /** Li et al. 1991 as presented in Schafer (1997): pooled from the statistics alone. */
    private double d2PValue(double[] d, double dBar, int h) {
        double meanSqrt = 0;
        for (double v : d) meanSqrt += Math.sqrt(v);
        meanSqrt /= this.m;

        double varSqrt = 0;
        for (double v : d) varSqrt += Math.pow(Math.sqrt(v) - meanSqrt, 2);
        varSqrt /= (this.m - 1);

        double r2 = (1.0 + 1.0 / this.m) * varSqrt;

        if (r2 < 1e-12) {
            return 1.0 - new ChiSquaredDistribution(h).cumulativeProbability(dBar);
        }

        double stat = Math.max(0.0, dBar / h - (this.m + 1.0) / (this.m - 1.0) * r2) / (1.0 + r2);
        double w2 = Math.pow(h, -3.0 / this.m) * (this.m - 1.0) * Math.pow(1.0 + 1.0 / r2, 2);

        return 1.0 - new FDistribution(h, Math.max(1.0, w2)).cumulativeProbability(stat);
    }

    /** OLS coefficients (predictors x xBlock) from a covariance matrix, or null if singular. */
    private static RealMatrix olsBeta(RealMatrix cov, int[] xCols, int[] predCols) {
        if (predCols.length == 0) return new Array2DRowRealMatrix(1, xCols.length); // unused zero row

        RealMatrix sPP = sub(cov, predCols, predCols);
        RealMatrix sPX = sub(cov, predCols, xCols);

        try {
            return new LUDecomposition(ridged(sPP)).getSolver().solve(sPX);
        } catch (SingularMatrixException e) {
            return null;
        }
    }

    /**
     * The average per-column residual variance of the x block under the GIVEN coefficients: the full quadratic
     * form trace(Sxx - Sxp B - B' Spx + B' Spp B) / |x|, valid for any B, not only the OLS solution.
     */
    private static double residualVariance(RealMatrix cov, int[] xCols, int[] predCols, RealMatrix beta) {
        RealMatrix sXX = sub(cov, xCols, xCols);
        if (predCols.length == 0) return sXX.getTrace() / xCols.length;

        RealMatrix sXP = sub(cov, xCols, predCols);
        RealMatrix sPP = sub(cov, predCols, predCols);

        RealMatrix xpB = sXP.multiply(beta);
        RealMatrix quad = beta.transpose().multiply(sPP).multiply(beta);

        double trace = sXX.getTrace() - xpB.getTrace() - xpB.getTrace() + quad.getTrace();
        return trace / xCols.length;
    }

    private static RealMatrix sub(RealMatrix cov, int[] rows, int[] cols) {
        double[][] out = new double[rows.length][cols.length];
        for (int a = 0; a < rows.length; a++) {
            for (int b = 0; b < cols.length; b++) out[a][b] = cov.getEntry(rows[a], cols[b]);
        }
        return new Array2DRowRealMatrix(out, false);
    }

    private static RealMatrix ridged(RealMatrix sPP) {
        RealMatrix out = sPP.copy();
        for (int i = 0; i < out.getRowDimension(); i++) {
            out.addToEntry(i, i, 1e-10);
        }
        return out;
    }

    private static RealMatrix average(RealMatrix[] mats) {
        RealMatrix out = mats[0].copy();
        for (int k = 1; k < mats.length; k++) out = out.add(mats[k]);
        return out.scalarMultiply(1.0 / mats.length);
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public DataSet getData() {
        return this.first;
    }

    @Override
    public double getAlpha() {
        return this.alpha;
    }

    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public String toString() {
        return "Pooled DG LRT over " + this.m + " imputations (" + this.pooling + "), alpha = " + this.alpha;
    }
}
