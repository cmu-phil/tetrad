    package edu.cmu.tetrad.search;

    import edu.cmu.tetrad.data.DataSet;
    import edu.cmu.tetrad.graph.Graph;
    import edu.cmu.tetrad.graph.IndependenceFact;
    import edu.cmu.tetrad.graph.Node;

    import java.util.*;

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
     * (a per-coordinate flip silently assumes the T_j are uncorrelated and badly over-rejexiects).
     *
     * <p>Instead we calibrate the whole vector jointly with a per-observation
     * <b>wild / multiplier bootstrap</b>: a single sign (or Gaussian) multiplier vector e_1..e_n is
     * drawn per replicate and applied to the <em>centered per-observation products</em>, shared
     * across all constraints. This reproduces the joint covariance of the T_j, so the omnibus
     * statistic is calibrated under dependence.
     *
     * <p><b>Row dependence.</b> The dependence handled above is among the CONSTRAINTS; the rows are
     * assumed exchangeable. Under serial or spatial dependence among observations the i.i.d.
     * multiplier bootstrap understates the null variance of mean_i(rX_i*rY_i) and the check is
     * anticonservative. Two dependence-aware variants are provided, both <em>bootstrap-t</em>:
     * the observed and replicate statistics are studentized by a dependence-consistent variance
     * (re-studentization is essential; with the plain statistic, global centering removes exactly
     * the low-frequency variance that dependent multipliers are supposed to carry, and the level
     * error does not vanish at any block size or bandwidth):
     * <ul>
     *   <li>{@link #setBlocks(int[])} &mdash; wild CLUSTER bootstrap-t (Cameron, Gelbach &amp;
     *       Miller 2008): one multiplier per block per replicate, cluster-robust variance in the
     *       denominator. Preferred whenever a grouping exists or can be constructed: towns for
     *       tract data, subjects for repeated measures, or spatial grid cells via
     *       {@link #gridBlocks(double[][], double)}. Choose blocks at least as large as the
     *       dependence range; too-small blocks leave an anticonservative residue. In simulation
     *       (GP rows, range ~9 in row units, n=120), i.i.d. multipliers rejected a true null 98%
     *       of the time at alpha = .05; block bootstrap-t with adequate blocks rejected ~6%.</li>
     *   <li>{@link #setKernel(double[][], double)} &mdash; dependent wild bootstrap-t (Shao 2010;
     *       self-normalized): Gaussian multipliers with covariance exp(-d^2/(2 h^2)) over row
     *       coordinates, HAC-type kernel variance in the denominator. For dependence without a
     *       clean block structure. More bandwidth-sensitive than blocks (same simulation: ~11%
     *       at the best h); run a small sensitivity grid over h.
     *       {@link #medianNearestNeighborDistance(double[][])} times 2&ndash;4 is a starting
     *       scale.</li>
     * </ul>
     * Defaults are unchanged: with neither option set, multipliers are i.i.d. per row and the
     * statistic is the original one.
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
        public enum Multiplier {
            /** Signs drawn uniformly from {-1, +1}. The default; cheap and exact in the first two moments. */
            RADEMACHER,
            /** Standard normal multipliers. */
            GAUSSIAN
        }

        /** Pluggable residualization. Default is OLS; supply RFF/NN/mixed-data variants here. */
        public interface Residualizer {
            /**
             * Residualizes a target column on a set of predictor columns.
             *
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
            /** Wild-bootstrap p-value for the omnibus statistic max_j |T_j|. */
            public final double pMax;
            /** Wild-bootstrap p-value for the omnibus statistic sum_j T_j^2. */
            public final double pSumSquares;
            /** The observed value of max_j |T_j|. */
            public final double mObs;
            /** The observed value of sum_j T_j^2. */
            public final double qObs;
            /** Sample size. */
            public final int n;
            /** Number of constraints tested, K. */
            public final int numConstraints;
            /** Number of bootstrap replicates used, B. */
            public final int numBootstraps;
            /** Length-K constraint labels, of the form "X _||_ Y" or "X _||_ Y | Z1,Z2". */
            public final String[] labels;
            /** Length-K observed standardized statistics T_j, aligned with {@link #labels}. */
            public final double[] t;

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

            /**
             * Constraints with the largest |T_j|, most-violated first.
             *
             * @param k the maximum number of constraints to return; fewer are returned if K &lt; k.
             * @return formatted "label T = value" lines, sorted by decreasing |T_j|.
             */
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

            /**
             * @return a multi-line report giving the omnibus statistics, their p-values, and the eight
             * most-violated constraints.
             */
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
         * @return the omnibus p-values and per-constraint statistics. If K == 0 both p-values are 1.0.
         * @throws InterruptedException if the calling thread is interrupted while the bootstrap runs.
         */
        public static Result runEngine(double[][] product, String[] labels,
                                       int numBootstraps, long seed, Multiplier mult) throws InterruptedException {
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
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

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
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }

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

        /**
         * Wild CLUSTER bootstrap-t engine for row dependence with a block (cluster) structure. The
         * observed statistic per constraint is the cluster-studentized mean,
         *
         * <pre>   T_j = sqrt(n) * mean_i(c_ij) / s_j,   s_j^2 = (1/n) sum_b ( sum_{i in b} (c_ij - mean_j) )^2,</pre>
         *
         * and each replicate multiplies the centered products by one Rademacher/Gaussian draw per
         * BLOCK (shared across constraints, preserving cross-constraint dependence) and is
         * re-studentized by the same cluster-variance formula. Blocks should be at least as large
         * as the row-dependence range.
         *
         * @param product       n x K matrix of per-observation products (uncentered).
         * @param labels        length-K constraint labels.
         * @param numBootstraps number of bootstrap replicates B.
         * @param seed          RNG seed.
         * @param mult          multiplier distribution (per block).
         * @param blocks        length-n block assignment; rows with equal values share a multiplier.
         * @return the omnibus p-values and per-constraint statistics.
         * @throws InterruptedException if the calling thread is interrupted while the bootstrap runs.
         */
        public static Result runEngineBlocks(double[][] product, String[] labels, int numBootstraps,
                                             long seed, Multiplier mult, int[] blocks) throws InterruptedException {
            final int n = product.length;
            final int K = (n == 0) ? 0 : product[0].length;
            if (K == 0) {
                return new Result(1.0, 1.0, 0.0, 0.0, n, 0, numBootstraps, new String[0], new double[0]);
            }
            if (blocks.length != n) {
                throw new IllegalArgumentException("blocks length " + blocks.length + " != rows " + n);
            }

            // Remap arbitrary block ids to slots 0..G-1 in order of first appearance, so the draw
            // order (hence the seeded RNG stream) is well defined.
            Map<Integer, Integer> slotOf = new LinkedHashMap<>();
            for (int b : blocks) slotOf.putIfAbsent(b, slotOf.size());
            final int G = slotOf.size();
            final int[] slot = new int[n];
            for (int i = 0; i < n; i++) slot[i] = slotOf.get(blocks[i]);

            final double sqrtN = Math.sqrt(n);
            double[] mean = new double[K];
            boolean[] valid = new boolean[K];
            double[] t = new double[K];

            for (int j = 0; j < K; j++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                double s = 0.0;
                for (int i = 0; i < n; i++) s += product[i][j];
                double m = s / n;
                mean[j] = m;
                double q = clusterQuad(product, j, m, slot, G);
                double sj = Math.sqrt(q / n);
                valid[j] = sj > 1e-12;
                t[j] = valid[j] ? (sqrtN * m / sj) : 0.0;
            }

            double mObs = 0.0, qObs = 0.0;
            for (int j = 0; j < K; j++) {
                mObs = Math.max(mObs, Math.abs(t[j]));
                qObs += t[j] * t[j];
            }

            Random rng = new Random(seed);
            int geMax = 0, geQ = 0;
            double[] draw = new double[G];
            double[] cs = new double[n];

            for (int b = 0; b < numBootstraps; b++) {
                if (mult == Multiplier.RADEMACHER) {
                    for (int g = 0; g < G; g++) draw[g] = rng.nextBoolean() ? 1.0 : -1.0;
                } else {
                    for (int g = 0; g < G; g++) draw[g] = rng.nextGaussian();
                }
                double mB = 0.0, qB = 0.0;
                for (int j = 0; j < K; j++) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (!valid[j]) continue;
                    double mj = mean[j];
                    double mStar = 0.0;
                    for (int i = 0; i < n; i++) {
                        cs[i] = draw[slot[i]] * (product[i][j] - mj);
                        mStar += cs[i];
                    }
                    mStar /= n;
                    double q = clusterQuadArr(cs, mStar, slot, G);
                    if (q < 1e-12) continue;               // degenerate replicate: conservative skip
                    double tb = sqrtN * mStar / Math.sqrt(q / n);
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

        /**
         * Dependent wild bootstrap-t engine (Shao 2010, self-normalized) for row dependence indexed
         * by coordinates (spatial locations; a single column of time indices for serial data). The
         * observed statistic per constraint is the HAC-studentized mean,
         *
         * <pre>   T_j = sqrt(n) * mean_i(c_ij) / s_j,   s_j^2 = (1/n) sum_ik W_ik (c_ij - mean_j)(c_kj - mean_j),</pre>
         *
         * with W_ik = exp(-d_ik^2 / (2 h^2)), and each replicate multiplies the centered products by
         * a Gaussian vector with covariance W (shared across constraints) and is re-studentized by
         * the same kernel variance. O(n^2) work per constraint per replicate. Level is more
         * bandwidth-sensitive than the block engine; prefer blocks when a grouping exists, and run a
         * small sensitivity grid over h otherwise.
         *
         * @param product       n x K matrix of per-observation products (uncentered).
         * @param labels        length-K constraint labels.
         * @param numBootstraps number of bootstrap replicates B.
         * @param seed          RNG seed.
         * @param coords        n rows of coordinates aligned with the data rows.
         * @param bandwidth     the kernel bandwidth h in coordinate units; must be positive.
         * @return the omnibus p-values and per-constraint statistics.
         * @throws InterruptedException if the calling thread is interrupted while the bootstrap runs.
         */
        public static Result runEngineKernel(double[][] product, String[] labels, int numBootstraps,
                                             long seed, double[][] coords, double bandwidth) throws InterruptedException {
            final int n = product.length;
            final int K = (n == 0) ? 0 : product[0].length;
            if (K == 0) {
                return new Result(1.0, 1.0, 0.0, 0.0, n, 0, numBootstraps, new String[0], new double[0]);
            }
            if (coords.length != n) {
                throw new IllegalArgumentException("coords rows " + coords.length + " != rows " + n);
            }
            if (bandwidth <= 0.0) {
                throw new IllegalArgumentException("bandwidth must be positive: " + bandwidth);
            }

            final double[][] W = gaussianKernelMatrix(coords, bandwidth);
            final double[][] L = choleskyWithJitter(W);

            final double sqrtN = Math.sqrt(n);
            double[] mean = new double[K];
            boolean[] valid = new boolean[K];
            double[] t = new double[K];
            double[] buf = new double[n];

            for (int j = 0; j < K; j++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                double s = 0.0;
                for (int i = 0; i < n; i++) s += product[i][j];
                double m = s / n;
                mean[j] = m;
                for (int i = 0; i < n; i++) buf[i] = product[i][j] - m;
                double q = kernelQuad(W, buf);
                double sj = Math.sqrt(Math.max(q, 0.0) / n);
                valid[j] = sj > 1e-12;
                t[j] = valid[j] ? (sqrtN * m / sj) : 0.0;
            }

            double mObs = 0.0, qObs = 0.0;
            for (int j = 0; j < K; j++) {
                mObs = Math.max(mObs, Math.abs(t[j]));
                qObs += t[j] * t[j];
            }

            Random rng = new Random(seed);
            int geMax = 0, geQ = 0;
            double[] z = new double[n];
            double[] eps = new double[n];
            double[] cs = new double[n];

            for (int b = 0; b < numBootstraps; b++) {
                for (int i = 0; i < n; i++) z[i] = rng.nextGaussian();
                for (int i = 0; i < n; i++) {
                    double s = 0.0;
                    double[] Li = L[i];
                    for (int k = 0; k <= i; k++) s += Li[k] * z[k];
                    eps[i] = s;
                }
                double mB = 0.0, qB = 0.0;
                for (int j = 0; j < K; j++) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (!valid[j]) continue;
                    double mj = mean[j];
                    double mStar = 0.0;
                    for (int i = 0; i < n; i++) {
                        cs[i] = eps[i] * (product[i][j] - mj);
                        mStar += cs[i];
                    }
                    mStar /= n;
                    for (int i = 0; i < n; i++) cs[i] -= mStar;
                    double q = kernelQuad(W, cs);
                    if (q < 1e-12) continue;               // degenerate replicate: conservative skip
                    double tb = sqrtN * mStar / Math.sqrt(q / n);
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

        /** Cluster variance numerator: sum over blocks of squared block sums of column j centered at m. */
        private static double clusterQuad(double[][] product, int j, double m, int[] slot, int G) {
            double[] bs = new double[G];
            for (int i = 0; i < product.length; i++) bs[slot[i]] += product[i][j] - m;
            double q = 0.0;
            for (int g = 0; g < G; g++) q += bs[g] * bs[g];
            return q;
        }

        /** Cluster variance numerator over a precomputed vector centered at m. */
        private static double clusterQuadArr(double[] c, double m, int[] slot, int G) {
            double[] bs = new double[G];
            for (int i = 0; i < c.length; i++) bs[slot[i]] += c[i] - m;
            double q = 0.0;
            for (int g = 0; g < G; g++) q += bs[g] * bs[g];
            return q;
        }

        /** Quadratic form c' W c for a centered vector c. */
        private static double kernelQuad(double[][] W, double[] c) {
            final int n = c.length;
            double q = 0.0;
            for (int i = 0; i < n; i++) {
                double w = 0.0;
                double[] Wi = W[i];
                for (int k = 0; k < n; k++) w += Wi[k] * c[k];
                q += c[i] * w;
            }
            return q;
        }

        /** Gaussian kernel matrix over coords: W_ik = exp(-||coord_i - coord_k||^2 / (2 h^2)). */
        private static double[][] gaussianKernelMatrix(double[][] coords, double h) {
            final int n = coords.length;
            final double twoH2 = 2.0 * h * h;
            double[][] W = new double[n][n];
            for (int i = 0; i < n; i++) {
                W[i][i] = 1.0;
                for (int k = i + 1; k < n; k++) {
                    double d2 = 0.0;
                    for (int c = 0; c < coords[i].length; c++) {
                        double d = coords[i][c] - coords[k][c];
                        d2 += d * d;
                    }
                    double v = Math.exp(-d2 / twoH2);
                    W[i][k] = v;
                    W[k][i] = v;
                }
            }
            return W;
        }

        /**
         * Lower Cholesky factor with escalating diagonal jitter. The Gaussian kernel is PD for
         * distinct points, but duplicated coordinates or tiny bandwidths can make it numerically
         * semidefinite.
         */
        private static double[][] choleskyWithJitter(double[][] A) {
            for (double jitter = 1e-10; jitter <= 1e-2; jitter *= 10.0) {
                double[][] L = tryCholesky(A, jitter);
                if (L != null) return L;
            }
            throw new IllegalStateException("Kernel matrix is not positive definite even with jitter;"
                    + " check for many duplicated coordinates or use setBlocks instead.");
        }

        /** Cholesky attempt with the given jitter added to the diagonal; returns null on failure. */
        private static double[][] tryCholesky(double[][] A, double jitter) {
            final int n = A.length;
            double[][] L = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int k = 0; k <= i; k++) {
                    double s = A[i][k];
                    for (int m = 0; m < k; m++) s -= L[i][m] * L[k][m];
                    if (i == k) {
                        s += jitter;
                        if (s <= 0.0) return null;
                        L[i][i] = Math.sqrt(s);
                    } else {
                        L[i][k] = s / L[k][k];
                    }
                }
            }
            return L;
        }

        /**
         * Bins coordinates into a square grid and returns one block id per row: rows falling in the
         * same grid cell share a block. A convenient way to build spatial clusters for
         * {@link #setBlocks(int[])} from locations (e.g. LON/LAT): choose the cell size at least as
         * large as the suspected spatial dependence range.
         *
         * @param coords   n rows of coordinates (any dimension).
         * @param cellSize the grid cell edge length, in coordinate units; must be positive.
         * @return length-n block ids (arbitrary ints; equal id = same cell).
         */
        public static int[] gridBlocks(double[][] coords, double cellSize) {
            if (cellSize <= 0.0) throw new IllegalArgumentException("cellSize must be positive: " + cellSize);
            final int n = coords.length;
            Map<List<Long>, Integer> cellId = new LinkedHashMap<>();
            int[] blocks = new int[n];
            for (int i = 0; i < n; i++) {
                List<Long> cell = new ArrayList<>(coords[i].length);
                for (double v : coords[i]) cell.add((long) Math.floor(v / cellSize));
                blocks[i] = cellId.computeIfAbsent(cell, c -> cellId.size());
            }
            return blocks;
        }

        /**
         * Median Euclidean distance from each row to its nearest neighbor; a scale reference for
         * {@link #setKernel(double[][], double)} bandwidths and {@link #gridBlocks(double[][],
         * double)} cell sizes. Multiply by 2&ndash;4 to cover a dependence range extending a few
         * neighbor-spacings.
         *
         * @param coords n rows of coordinates.
         * @return the median nearest-neighbor distance (0.0 if fewer than 2 rows or if duplicated
         * coordinates dominate; callers should then choose a scale by hand).
         */
        public static double medianNearestNeighborDistance(double[][] coords) {
            int n = coords.length;
            if (n < 2) return 0.0;
            double[] nn = new double[n];
            for (int i = 0; i < n; i++) {
                double best = Double.POSITIVE_INFINITY;
                for (int k = 0; k < n; k++) {
                    if (k == i) continue;
                    double d2 = 0.0;
                    for (int c = 0; c < coords[i].length; c++) {
                        double d = coords[i][c] - coords[k][c];
                        d2 += d * d;
                    }
                    if (d2 < best) best = d2;
                }
                nn[i] = Math.sqrt(best);
            }
            Arrays.sort(nn);
            return (n % 2 == 1) ? nn[n / 2] : 0.5 * (nn[n / 2 - 1] + nn[n / 2]);
        }

        // ----------------------------------------------------------------------------------------
        // Tetrad adapter
        // ----------------------------------------------------------------------------------------

        private final DataSet data;
        private Residualizer residualizer = new OlsResidualizer();
        private int numBootstraps = 1000;
        private long seed = 0L;
        private Multiplier multiplier = Multiplier.RADEMACHER;
        private int[] blocks = null;                 // dependent WB: wild cluster bootstrap-t
        private double[][] kernelCoords = null;      // dependent WB: kernel bootstrap-t
        private double kernelBandwidth = 0.0;

        /**
         * Constructs a check over the given data. Configure it with the setters, then call
         * {@link #checkMarkovBlanket(Graph)} or {@link #checkFacts(List)}.
         *
         * @param data the dataset to check; columns are treated as continuous by the default residualizer.
         */
        public WildBootstrapMarkovCheck(DataSet data) {
            this.data = data;
        }

        /**
         * Sets the residualization used to regress X and Y on the conditioning set. Default is
         * {@link OlsResidualizer}.
         *
         * @param r the residualizer.
         * @return this, for chaining.
         */
        public WildBootstrapMarkovCheck setResidualizer(Residualizer r) { this.residualizer = r; return this; }

        /**
         * Sets the number of bootstrap replicates B. Default is 1000.
         *
         * @param b the number of replicates; the smallest attainable p-value is 1 / (b + 1).
         * @return this, for chaining.
         */
        public WildBootstrapMarkovCheck setNumBootstraps(int b)         { this.numBootstraps = b; return this; }

        /**
         * Sets the RNG seed for the bootstrap, for reproducibility. Default is 0.
         *
         * @param s the seed.
         * @return this, for chaining.
         */
        public WildBootstrapMarkovCheck setSeed(long s)                 { this.seed = s; return this; }

        /**
         * Sets the multiplier distribution for the wild bootstrap. Default is
         * {@link Multiplier#RADEMACHER}.
         *
         * @param m the multiplier distribution.
         * @return this, for chaining.
         */
        public WildBootstrapMarkovCheck setMultiplier(Multiplier m)     { this.multiplier = m; return this; }

        /**
         * Dependent wild bootstrap, cluster form: switches the check to the wild cluster
         * bootstrap-t of {@link #runEngineBlocks}. Use when rows are grouped (tracts within towns,
         * repeated measures within subjects) or a spatial grouping can be constructed via
         * {@link #gridBlocks(double[][], double)}, and within-group row dependence is the concern.
         * Blocks should be at least as large as the dependence range. Clears any kernel setting;
         * pass null to return to i.i.d. multipliers.
         *
         * @param blocks length-n block assignment aligned with the data rows; rows with equal
         *               values share a multiplier.
         * @return this, for chaining.
         */
        public WildBootstrapMarkovCheck setBlocks(int[] blocks) {
            this.blocks = (blocks == null) ? null : blocks.clone();
            this.kernelCoords = null;
            return this;
        }

        /**
         * Dependent wild bootstrap, kernel form: switches the check to the dependent wild
         * bootstrap-t of {@link #runEngineKernel} (Shao 2010, self-normalized), with multiplier
         * covariance exp(-d^2/(2 h^2)) over the given row coordinates. Use for spatial or serial
         * dependence without a clean block structure; multipliers are necessarily Gaussian and the
         * {@link Multiplier} setting is ignored while a kernel is set. Level is more
         * bandwidth-sensitive than the block form; run a small sensitivity grid over h. Clears any
         * block setting; pass null coords to return to i.i.d. multipliers.
         *
         * @param coords    n rows of coordinates aligned with the data rows (1 column for time
         *                  order, 2 for LON/LAT, ...).
         * @param bandwidth the kernel bandwidth h in coordinate units; see
         *                  {@link #medianNearestNeighborDistance(double[][])} for a scale reference.
         * @return this, for chaining.
         */
        public WildBootstrapMarkovCheck setKernel(double[][] coords, double bandwidth) {
            if (coords == null) {
                this.kernelCoords = null;
                return this;
            }
            if (bandwidth <= 0.0) throw new IllegalArgumentException("bandwidth must be positive: " + bandwidth);
            double[][] copy = new double[coords.length][];
            for (int i = 0; i < coords.length; i++) copy[i] = coords[i].clone();
            this.kernelCoords = copy;
            this.kernelBandwidth = bandwidth;
            this.blocks = null;
            return this;
        }

        /** Dispatches to the engine matching the configured dependence handling. */
        private Result run(double[][] product, String[] labels) throws InterruptedException {
            final int n = product.length;
            if (blocks != null) {
                if (blocks.length != n) {
                    throw new IllegalArgumentException("blocks length " + blocks.length
                            + " != number of data rows " + n);
                }
                return runEngineBlocks(product, labels, numBootstraps, seed, multiplier, blocks);
            }
            if (kernelCoords != null) {
                if (kernelCoords.length != n) {
                    throw new IllegalArgumentException("kernel coords rows " + kernelCoords.length
                            + " != number of data rows " + n);
                }
                return runEngineKernel(product, labels, numBootstraps, seed, kernelCoords, kernelBandwidth);
            }
            return runEngine(product, labels, numBootstraps, seed, multiplier);
        }

        /**
         * Build the stacked product matrix from the data and graph, then run the joint check. One
         * constraint X _||_ Y | MB(X) is contributed for each target X and each Y outside
         * MB(X) U {X}. Graph nodes absent from the data are skipped.
         *
         * @param graph the DAG or CPDAG to check.
         * @return the omnibus p-values and per-constraint statistics.
         * @throws InterruptedException if the calling thread is interrupted while the bootstrap runs.
         */
        public Result checkMarkovBlanket(Graph graph) throws InterruptedException {
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
                Set<Node> mb = graph.paths().markovBlanket(x);
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

            return run(product, labels.toArray(new String[0]));
        }

        /**
         * Run the joint wild-bootstrap check against an explicit list of independence facts.
         *
         * <p>Each fact X _||_ Y | Z contributes one product column: residualize X and Y on Z
         * (via the configured {@link Residualizer}) and take the per-observation product rX_i*rY_i.
         * The facts need not come from a Markov blanket; any constraint set works, e.g. the
         * m-separation implications of a DAG / MAG / PAG. Calibration is the same joint wild
         * bootstrap, so dependence among the T_j (including redundancy among implied facts) is handled.
         *
         * @param facts the independence facts to test; one product column each. Members of Z equal to
         *              X or Y are ignored.
         * @return the omnibus p-values and per-constraint statistics.
         * @throws IllegalArgumentException if a fact references an unknown variable or has X == Y.
         * @throws InterruptedException     if the calling thread is interrupted while the bootstrap runs.
         */
        public Result checkFacts(List<IndependenceFact> facts) throws InterruptedException {
            final int n = data.getNumRows();
            final List<Node> vars = data.getVariables();
            final int p = vars.size();

            double[][] col = new double[p][n];
            for (int c = 0; c < p; c++)
                for (int i = 0; i < n; i++) col[c][i] = data.getDouble(i, c);

            Map<String, double[]> residualCache = new HashMap<>();   // key: varName + "|" + sorted-Z
            List<double[]> columns = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (IndependenceFact fact : facts) {
                Node x = fact.getX(), y = fact.getY();
                final int xi = indexByName(vars, x.getName());
                final int yi = indexByName(vars, y.getName());
                if (xi < 0) throw new IllegalArgumentException("X not in data: " + x.getName());
                if (yi < 0) throw new IllegalArgumentException("Y not in data: " + y.getName());
                if (xi == yi) throw new IllegalArgumentException("Degenerate fact X == Y: " + x.getName());

                List<Integer> zIdx = new ArrayList<>();
                List<String> zNames = new ArrayList<>();
                for (Node z : fact.getZ()) {
                    int zi = indexByName(vars, z.getName());
                    if (zi < 0) throw new IllegalArgumentException("Z member not in data: " + z.getName());
                    if (zi == xi || zi == yi) continue;              // ill-formed Z: X/Y can't be in Z
                    zIdx.add(zi);
                    zNames.add(z.getName());
                }
                Collections.sort(zNames);
                final String zKey = String.join(",", zNames);

                final double[][] preds = new double[zIdx.size()][];
                for (int k = 0; k < zIdx.size(); k++) preds[k] = col[zIdx.get(k)];

                double[] rX = residualCache.computeIfAbsent(
                        x.getName() + "|" + zKey, k -> residualizer.residuals(col[xi], preds));
                double[] rY = residualCache.computeIfAbsent(
                        y.getName() + "|" + zKey, k -> residualizer.residuals(col[yi], preds));

                double[] prod = new double[n];
                for (int i = 0; i < n; i++) prod[i] = rX[i] * rY[i];

                columns.add(prod);
                labels.add(x.getName() + " _||_ " + y.getName()
                        + (zNames.isEmpty() ? "" : " | " + String.join(",", zNames)));
            }

            int K = columns.size();
            double[][] product = new double[n][K];
            for (int j = 0; j < K; j++) {
                double[] cj = columns.get(j);
                for (int i = 0; i < n; i++) product[i][j] = cj[i];
            }
            return run(product, labels.toArray(new String[0]));
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

            /** Constructs an OLS residualizer with the default ridge of 1e-10. */
            public OlsResidualizer()            { this(1e-10); }

            /**
             * Constructs an OLS residualizer with a given ridge.
             *
             * @param ridge added to the diagonal of X'X for numerical stability; a singular system
             *              yields zero coefficients rather than an exception.
             */
            public OlsResidualizer(double ridge) { this.ridge = ridge; }

            /**
             * Regresses y on the predictors (with an intercept) and returns the residuals. With no
             * predictors this returns y minus its mean.
             *
             * @param y     length-n response.
             * @param preds d predictor columns, each length n.
             * @return length-n residual vector.
             */
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

        /**
         * Self-contained calibration demo. Simulates null data in which every constraint holds, at
         * several levels of cross-constraint correlation rho, and prints the rejection rate of the
         * per-observation bootstrap against that of the broken per-coordinate sign flip. The former
         * should sit near alpha at every rho; the latter inflates as rho grows.
         *
         * @param args ignored.
         */
        public static void main(String[] args) {
            try {
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
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
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
