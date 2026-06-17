package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Joint, dependence-aware Markov check for a DAG or CPDAG.
 *
 * <p>For each target X we test the local constraints X _||_ Y | MB(X) for every Y outside
 * MB(X) U {X}, using a GCM-style residual-product statistic
 *
 * <pre>   T_j = sqrt(n) * mean_i( rX_i * rY_i ) / sd_i( rX_i * rY_i ),</pre>
 *
 * where rX, rY are residuals of X and Y regressed on MB(X). Under the null each T_j is
 * approximately N(0,1), but the T_j are <em>correlated</em> (they share rX, and the non-blanket
 * variables are correlated through the rest of the graph). We therefore do NOT convert to
 * p-values and assume independence, and we do NOT flip the signs of the averaged T_j
 * (a per-coordinate flip silently assumes the T_j are uncorrelated and badly over-rejects).
 *
 * <p>Instead we calibrate the whole vector jointly with a per-observation
 * <b>wild / multiplier bootstrap</b>: a single sign (or Gaussian) multiplier vector e_1..e_n is
 * drawn per replicate and applied to the <em>centered per-observation products</em>, shared
 * across all constraints. This reproduces the joint covariance of the T_j, so the omnibus
 * statistic is calibrated under dependence.
 *
 * <p>Two omnibus statistics are reported:
 * <ul>
 *   <li>max |T_j|  &mdash; power against a few badly-violated constraints (sparse alternatives);</li>
 *   <li>sum T_j^2  &mdash; power against many small violations (diffuse alternatives).</li>
 * </ul>
 *
 * <p>The residual-product statistic is a conditional-<em>covariance</em> test, so like GCM it has
 * no power against dependence that lives only in the conditional variance / higher moments. The
 * bootstrap fixes the calibration under dependence, not the power direction. Swap in a richer
 * {@link Residualizer} (e.g. RFF / feature regression) to relax the functional form of the
 * conditional mean.
 *
 * <p>Typical use:
 * <pre>
 *   DataSet data = ...;           // continuous (or use a mixed-data Residualizer)
 *   Graph cpdag  = ...;           // candidate model to check
 *   WildBootstrapMarkovCheck mc = new WildBootstrapMarkovCheck(data, cpdag)
 *           .setNumBootstraps(1000)
 *           .setSeed(13L)
 *           .setMultiplier(Multiplier.RADEMACHER);
 *   WildBootstrapMarkovCheck.Result r = mc.check();
 *   System.out.println(r);                       // p-values + worst constraints
 *   boolean passes = r.pMax > 0.05;              // thumbs-up / thumbs-down
 * </pre>
 *
 * <p>Run {@code main} (within the Tetrad classpath) for a self-contained calibration demo that
 * contrasts the per-observation bootstrap against the broken per-coordinate flip.
 */
public final class WildBootstrapMarkovCheck {

    /** Multiplier distribution for the wild bootstrap. */
    public enum Multiplier { RADEMACHER, GAUSSIAN }

    /** Pluggable residualization. Default is OLS; supply RFF/NN/mixed-data variants here. */
    public interface Residualizer {
        /**
         * @param target     length-n response.
         * @param predictors d predictor columns, each length n (may be empty: then residual = target - mean).
         * @return length-n residual vector.
         */
        double[] residuals(double[] target, double[][] predictors);
    }

    // ----------------------------------------------------------------------------------------
    // Result
    // ----------------------------------------------------------------------------------------

    /** Outcome of a check: omnibus p-values plus per-constraint diagnostics. */
    public static final class Result {
        public final double pMax;          // wild-bootstrap p-value for max_j |T_j|
        public final double pSumSquares;   // wild-bootstrap p-value for sum_j T_j^2
        public final double mObs;          // observed max_j |T_j|
        public final double qObs;          // observed sum_j T_j^2
        public final int n;                // sample size
        public final int numConstraints;   // K
        public final int numBootstraps;    // B
        public final String[] labels;      // length-K constraint labels, "X _||_ Y"
        public final double[] t;           // length-K observed T_j

        Result(double pMax, double pSumSquares, double mObs, double qObs,
               int n, int numConstraints, int numBootstraps, String[] labels, double[] t) {
            this.pMax = pMax;
            this.pSumSquares = pSumSquares;
            this.mObs = mObs;
            this.qObs = qObs;
            this.n = n;
            this.numConstraints = numConstraints;
            this.numBootstraps = numBootstraps;
            this.labels = labels;
            this.t = t;
        }

        /** Constraints with the largest |T_j|, most-violated first. */
        public List<String> topConstraints(int k) {
            Integer[] idx = new Integer[numConstraints];
            for (int j = 0; j < numConstraints; j++) idx[j] = j;
            Arrays.sort(idx, (a, b) -> Double.compare(Math.abs(t[b]), Math.abs(t[a])));
            List<String> out = new ArrayList<>();
            for (int r = 0; r < Math.min(k, numConstraints); r++) {
                int j = idx[r];
                out.add(String.format("%-28s T = %+.3f", labels[j], t[j]));
            }
            return out;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("WildBootstrapMarkovCheck\n");
            sb.append(String.format("  n = %d, constraints = %d, bootstraps = %d%n",
                    n, numConstraints, numBootstraps));
            sb.append(String.format("  max|T|      = %.4f   p_max        = %.4f%n", mObs, pMax));
            sb.append(String.format("  sum T^2     = %.4f   p_sumSquares = %.4f%n", qObs, pSumSquares));
            sb.append("  most-violated constraints:\n");
            for (String s : topConstraints(8)) sb.append("    ").append(s).append('\n');
            return sb.toString();
        }
    }

    // ----------------------------------------------------------------------------------------
    // Core engine (Tetrad-independent): given the n x K product matrix, do the bootstrap.
    // ----------------------------------------------------------------------------------------

    /**
     * Run the joint check directly on a precomputed product matrix.
     *
     * @param product       n x K matrix; column j holds the per-observation products rX_i*rY_i
     *                      for one constraint. Pass uncentered products; centering is handled here.
     * @param labels        length-K constraint labels.
     * @param numBootstraps number of bootstrap replicates B.
     * @param seed          RNG seed (reproducibility).
     * @param mult          multiplier distribution.
     */
    public static Result runEngine(double[][] product, String[] labels,
                                   int numBootstraps, long seed, Multiplier mult) {
        final int n = product.length;
        final int K = (n == 0) ? 0 : product[0].length;
        if (K == 0) {
            return new Result(1.0, 1.0, 0.0, 0.0, n, 0, numBootstraps, new String[0], new double[0]);
        }

        // Column means, population sds, validity mask, observed standardized statistics.
        final double sqrtN = Math.sqrt(n);
        double[] mean = new double[K];
        double[] sd = new double[K];
        boolean[] valid = new boolean[K];
        double[] t = new double[K];

        for (int j = 0; j < K; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) s += product[i][j];
            double m = s / n;
            double ss = 0.0;
            for (int i = 0; i < n; i++) {
                double d = product[i][j] - m;
                ss += d * d;
            }
            double sdj = Math.sqrt(ss / n);          // population sd of the products
            mean[j] = m;
            sd[j] = sdj;
            valid[j] = sdj > 1e-12;
            // Observed: numerator is the UNcentered mean (the signal); denominator is sd.
            t[j] = valid[j] ? (sqrtN * m / sdj) : 0.0;
        }

        double mObs = 0.0, qObs = 0.0;
        for (int j = 0; j < K; j++) {
            mObs = Math.max(mObs, Math.abs(t[j]));
            qObs += t[j] * t[j];
        }

        // Wild bootstrap: one shared multiplier vector per replicate, applied per row to the
        // CENTERED products, shared across all K constraints. This preserves Cov(T_j, T_l).
        Random rng = new Random(seed);
        int geMax = 0, geQ = 0;
        double[] eps = new double[n];

        for (int b = 0; b < numBootstraps; b++) {
            if (mult == Multiplier.RADEMACHER) {
                for (int i = 0; i < n; i++) eps[i] = rng.nextBoolean() ? 1.0 : -1.0;
            } else {
                for (int i = 0; i < n; i++) eps[i] = rng.nextGaussian();
            }

            double mB = 0.0, qB = 0.0;
            for (int j = 0; j < K; j++) {
                if (!valid[j]) continue;
                double num = 0.0;
                double mj = mean[j];
                for (int i = 0; i < n; i++) num += eps[i] * (product[i][j] - mj);
                double tb = num / (sqrtN * sd[j]);
                double abs = Math.abs(tb);
                if (abs > mB) mB = abs;
                qB += tb * tb;
            }
            if (mB >= mObs) geMax++;
            if (qB >= qObs) geQ++;
        }

        double pMax = (1.0 + geMax) / (numBootstraps + 1.0);
        double pSumSq = (1.0 + geQ) / (numBootstraps + 1.0);
        return new Result(pMax, pSumSq, mObs, qObs, n, K, numBootstraps, labels, t);
    }

    // ----------------------------------------------------------------------------------------
    // Tetrad adapter
    // ----------------------------------------------------------------------------------------

    private final DataSet data;
    private final Graph graph;
    private Residualizer residualizer = new OlsResidualizer();
    private int numBootstraps = 1000;
    private long seed = 0L;
    private Multiplier multiplier = Multiplier.RADEMACHER;

    public WildBootstrapMarkovCheck(DataSet data, Graph graph) {
        this.data = data;
        this.graph = graph;
    }

    public WildBootstrapMarkovCheck setResidualizer(Residualizer r) { this.residualizer = r; return this; }
    public WildBootstrapMarkovCheck setNumBootstraps(int b)         { this.numBootstraps = b; return this; }
    public WildBootstrapMarkovCheck setSeed(long s)                 { this.seed = s; return this; }
    public WildBootstrapMarkovCheck setMultiplier(Multiplier m)     { this.multiplier = m; return this; }

    /** Build the stacked product matrix from the data and graph, then run the joint check. */
    public Result check() {
        final int n = data.getNumRows();
        final List<Node> vars = data.getVariables();
        final int p = vars.size();

        // Cache each data column once.
        double[][] col = new double[p][n];
        for (int c = 0; c < p; c++) {
            for (int i = 0; i < n; i++) col[c][i] = data.getDouble(i, c);
        }

        List<double[]> columns = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (Node x : graph.getNodes()) {
            int xi = indexByName(vars, x.getName());
            if (xi < 0) continue;                       // graph node not in data

            // Markov blanket of x, restricted to variables present in the data.
            List<Node> mb = markovBlanket(x);
            List<Integer> mbIdx = new ArrayList<>();
            for (Node z : mb) {
                int zi = indexByName(vars, z.getName());
                if (zi >= 0) mbIdx.add(zi);
            }
            double[][] preds = new double[mbIdx.size()][];
            for (int k = 0; k < mbIdx.size(); k++) preds[k] = col[mbIdx.get(k)];

            double[] rX = residualizer.residuals(col[xi], preds);

            // Constraints: x _||_ y | MB(x) for every y outside MB(x) U {x}.
            Set<Integer> blanketSet = new LinkedHashSet<>(mbIdx);
            for (int yi = 0; yi < p; yi++) {
                if (yi == xi || blanketSet.contains(yi)) continue;
                double[] rY = residualizer.residuals(col[yi], preds);
                double[] prod = new double[n];
                for (int i = 0; i < n; i++) prod[i] = rX[i] * rY[i];
                columns.add(prod);
                labels.add(x.getName() + " _||_ " + vars.get(yi).getName());
            }
        }

        // Transpose List<column> -> n x K matrix.
        int K = columns.size();
        double[][] product = new double[n][K];
        for (int j = 0; j < K; j++) {
            double[] cj = columns.get(j);
            for (int i = 0; i < n; i++) product[i][j] = cj[i];
        }

        return runEngine(product, labels.toArray(new String[0]), numBootstraps, seed, multiplier);
    }

    /** MB(x) = adjacents(x) U {parents of children of x}, minus x. Valid for DAGs and CPDAGs. */
    private List<Node> markovBlanket(Node x) {
        Set<Node> mb = new LinkedHashSet<>(graph.getAdjacentNodes(x));
        for (Node child : graph.getChildren(x)) {
            mb.addAll(graph.getParents(child));
        }
        mb.remove(x);
        return new ArrayList<>(mb);
    }

    private static int indexByName(List<Node> vars, String name) {
        for (int i = 0; i < vars.size(); i++) {
            if (vars.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    // ----------------------------------------------------------------------------------------
    // Default residualizer: OLS with intercept and a tiny ridge for stability.
    // ----------------------------------------------------------------------------------------

    /** Ordinary least squares residualization. Treats all predictors as continuous. */
    public static final class OlsResidualizer implements Residualizer {
        private final double ridge;
        public OlsResidualizer()            { this(1e-10); }
        public OlsResidualizer(double ridge) { this.ridge = ridge; }

        @Override
        public double[] residuals(double[] y, double[][] preds) {
            final int n = y.length;
            final int d = preds.length;
            final int m = d + 1;                         // + intercept

            double[][] A = new double[m][m];             // X'X
            double[] g = new double[m];                  // X'y
            double[] xi = new double[m];

            for (int i = 0; i < n; i++) {
                xi[0] = 1.0;
                for (int k = 0; k < d; k++) xi[k + 1] = preds[k][i];
                double yi = y[i];
                for (int a = 0; a < m; a++) {
                    g[a] += xi[a] * yi;
                    for (int bb = 0; bb < m; bb++) A[a][bb] += xi[a] * xi[bb];
                }
            }
            for (int a = 0; a < m; a++) A[a][a] += ridge;

            double[] beta = solve(A, g);

            double[] r = new double[n];
            for (int i = 0; i < n; i++) {
                double fit = beta[0];
                for (int k = 0; k < d; k++) fit += beta[k + 1] * preds[k][i];
                r[i] = y[i] - fit;
            }
            return r;
        }

        /** Solve A x = b for small symmetric A via Gaussian elimination with partial pivoting. */
        private static double[] solve(double[][] A, double[] b) {
            int m = b.length;
            double[][] M = new double[m][m + 1];
            for (int i = 0; i < m; i++) {
                System.arraycopy(A[i], 0, M[i], 0, m);
                M[i][m] = b[i];
            }
            for (int c = 0; c < m; c++) {
                int piv = c;
                for (int r = c + 1; r < m; r++) if (Math.abs(M[r][c]) > Math.abs(M[piv][c])) piv = r;
                double[] tmp = M[c]; M[c] = M[piv]; M[piv] = tmp;
                double diag = M[c][c];
                if (Math.abs(diag) < 1e-300) continue;   // singular: leave coefficient ~0
                for (int r = 0; r < m; r++) {
                    if (r == c) continue;
                    double f = M[r][c] / diag;
                    if (f == 0.0) continue;
                    for (int k = c; k <= m; k++) M[r][k] -= f * M[c][k];
                }
            }
            double[] x = new double[m];
            for (int i = 0; i < m; i++) {
                double diag = M[i][i];
                x[i] = (Math.abs(diag) < 1e-300) ? 0.0 : M[i][m] / diag;
            }
            return x;
        }
    }

    // ----------------------------------------------------------------------------------------
    // Self-contained calibration demo (no Tetrad data needed). Run within the Tetrad classpath:
    //   java edu.cmu.tetrad.search.WildBootstrapMarkovCheck
    // Shows the per-observation bootstrap holding level under correlated constraints while the
    // per-coordinate sign flip over-rejects.
    // ----------------------------------------------------------------------------------------

    public static void main(String[] args) {
        int n = 200, K = 12, B = 300, reps = 300;
        double alpha = 0.05;
        Random rng = new Random(7L);

        System.out.println("Null-model rejection rate at alpha = " + alpha
                + "  (n=" + n + ", constraints=" + K + ", bootstraps=" + B + ", reps=" + reps + ")");
        System.out.println("rho  | per-row max | per-row sumSq | per-COORD sum (broken)");

        for (double rho : new double[]{0.0, 0.5, 0.8}) {
            int rejMax = 0, rejSq = 0, rejCoord = 0;
            for (int rep = 0; rep < reps; rep++) {
                double[][] product = nullProducts(n, K, rho, rng);  // all constraints truly hold
                String[] labels = new String[K];
                for (int j = 0; j < K; j++) labels[j] = "c" + j;

                Result r = runEngine(product, labels, B, rng.nextLong(), Multiplier.RADEMACHER);
                if (r.pMax < alpha) rejMax++;
                if (r.pSumSquares < alpha) rejSq++;

                if (perCoordinateLinearSumP(product, B, rng) < alpha) rejCoord++;
            }
            System.out.printf("%.1f  |    %.3f    |     %.3f     |     %.3f%n",
                    rho, rejMax / (double) reps, rejSq / (double) reps, rejCoord / (double) reps);
        }
        System.out.println("(per-row columns should sit near " + alpha
                + " at every rho; per-COORD inflates as rho grows)");
    }

    /** Null products: shared X-residual 'a'; Y-residuals share a factor so columns correlate (Cov ~ rho). */
    private static double[][] nullProducts(int n, int K, double rho, Random rng) {
        double[] a = new double[n];
        double[] c = new double[n];
        for (int i = 0; i < n; i++) { a[i] = rng.nextGaussian(); c[i] = rng.nextGaussian(); }
        double[][] product = new double[n][K];
        double sr = Math.sqrt(rho), so = Math.sqrt(1 - rho);
        for (int j = 0; j < K; j++) {
            for (int i = 0; i < n; i++) {
                double b = sr * c[i] + so * rng.nextGaussian();
                product[i][j] = a[i] * b;                // mean-zero under the null
            }
        }
        return product;
    }

    /** The BROKEN calibration: flip signs of the averaged T_j independently (linear-sum statistic). */
    private static double perCoordinateLinearSumP(double[][] product, int B, Random rng) {
        int n = product.length, K = product[0].length;
        double sqrtN = Math.sqrt(n);
        double[] t = new double[K];
        for (int j = 0; j < K; j++) {
            double s = 0; for (int i = 0; i < n; i++) s += product[i][j];
            double m = s / n, ss = 0;
            for (int i = 0; i < n; i++) { double d = product[i][j] - m; ss += d * d; }
            double sd = Math.sqrt(ss / n);
            t[j] = sd > 1e-12 ? sqrtN * m / sd : 0.0;
        }
        double lObs = 0; for (double v : t) lObs += v;
        int ge = 0;
        for (int b = 0; b < B; b++) {
            double l = 0;
            for (int j = 0; j < K; j++) l += (rng.nextBoolean() ? 1.0 : -1.0) * t[j];
            if (Math.abs(l) >= Math.abs(lObs)) ge++;
        }
        return (1.0 + ge) / (B + 1.0);
    }
}
