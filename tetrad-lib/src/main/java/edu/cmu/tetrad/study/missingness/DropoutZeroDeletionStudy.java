package edu.cmu.tetrad.study.missingness;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.missing.MiceLiteImputer;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replication harness for the zero-deletion ("testwise deletion of zeros") proposal of Dai, Ng, Luo, Spirtes,
 * Stojanov and Zhang, "Gene Regulatory Network Inference in the Presence of Dropouts: A Causal View" (ICLR 2024).
 *
 * <p>The paper's setting differs from Tetrad's NaN-marked missing-data machinery in two ways. First, missingness is
 * not located: zeros ARE the data, and a zero may be a true (biological) zero or a dropout; nothing is marked NaN.
 * Second, the deletion rule of their Theorem 1 removes, for each conditional-independence estimate of X _||_ Y | S,
 * only the rows in which some CONDITIONING variable in S is zero -- rows in which X or Y themselves are zero are
 * KEPT. Their Definition 2 variant deletes rows with zeros in all involved variables (X, Y, and S), which under
 * their self-masking assumption (A3) is asymptotically equivalent but finite-sample hungrier.</p>
 *
 * <p>For greedy score-based search the paper does NOT sum a zero-deleted objective. It reframes each GES
 * insert/delete decision as a CI test: compute the partial correlation on the zero-deleted rows, rescale it so the
 * Fisher-Z statistic is preserved at the full sample size, and take the BIC change from that. That is a
 * per-decision use of deletion, the same boundary our October NaN studies found: per-decision deletion is sound
 * (PC tests, FGES diffs), per-objective summed deletion is incoherent (BOSS, GRaSP).</p>
 *
 * <p>Arms, per dropout mechanism and rate:</p>
 * <ul>
 *   <li>ORACLE PC / FGES / BOSS on the clean data Z (upper bound);</li>
 *   <li>FULL PC / FGES / BOSS on the zero-contaminated data X, dropouts ignored -- the paper's Proposition 1
 *       predicts these are biased toward DENSER graphs;</li>
 *   <li>PC 0DEL-COND: PC with the Theorem 1 test (delete rows with zeros in the conditioning set only);</li>
 *   <li>PC 0DEL-ALL: PC with the Definition 2 test (delete rows with zeros in any involved variable);</li>
 *   <li>FGES 0DEL: FGES with the per-decision zero-deleted, rescaled BIC diff (the paper's GES integration);</li>
 *   <li>BOSS 0DEL: BOSS with a SUMMED zero-deleted BIC -- each family scored on its own all-nonzero rows. The
 *       paper never proposes this; it is included to test the prediction that summed-objective deletion fails in
 *       the zero regime as it did in the NaN regime.</li>
 *   <li>MICE-PMM + IMaGES BOSS: all zeros recoded as missing, multiple imputation by predictive mean matching,
 *       and one BOSS run on the IMaGES score over the completed datasets -- the arm that won our NaN-marked
 *       studies, now tested on the paper's own mechanisms, where self-masking should bias it.</li>
 * </ul>
 *
 * <p>Dropout mechanisms follow the paper's Examples 1-3: fixed-rate Bernoulli dropout; truncation of the lowest
 * values to zero; and logistic self-masking dropout, where higher expression means less dropout. Data are linear
 * Gaussian SEMs, with an optional lognormal phase (exp transform) matching the paper's second case. Note a
 * limitation inherited from the paper's own linear-SEM design: continuous data has no true zeros (exact zeros have
 * probability zero), so every observed zero is technical, and the biological-versus-technical ambiguity that
 * motivates the model is not actually exercised here. That ambiguity needs a count or zero-inflated regime and is
 * left for a later phase.</p>
 *
 * <p>Run from IntelliJ; it streams a line per condition. Nothing outside this file is touched.</p>
 *
 * @author josephramsey
 */
public final class DropoutZeroDeletionStudy {

    private DropoutZeroDeletionStudy() {
    }

    // ---------------------------------------------------------------- configuration

    /** Variables in the simulated graph. */
    private static final int NUM_VARS = 15;

    /** Edges in the simulated graph (NUM_VARS * avg degree / 2). */
    private static final int NUM_EDGES = 30;

    /** Rows simulated before dropout. The paper uses 10000. */
    private static final int SAMPLE_SIZE = 5000;

    /** Repetitions per condition; each uses a fresh graph, SEM, and dataset. The paper uses 5. */
    private static final int NUM_REPS = 5;

    /** Target dropout rates to sweep. The paper draws per-gene rates in 0.3 to 0.7. */
    private static final double[] DROPOUT_RATES = {0.3, 0.5, 0.7};

    /** Slope of the logistic self-masking mechanism on the standardized scale (paper's Example 6 uses 2). */
    private static final double LOGISTIC_SLOPE = 2.0;

    /** Fisher-Z alpha for the PC arms. */
    private static final double ALPHA = 0.05;

    /** BIC penalty discount for every score arm. */
    private static final double PENALTY_DISCOUNT = 2.0;

    /**
     * Whether the per-decision FGES diff rescales the zero-deleted partial correlation to the full sample size,
     * preserving the Fisher-Z statistic, as the paper's GES integration does. Set false for the "honest" variant
     * that uses the deleted-row count directly in both the likelihood term and the penalty.
     */
    private static final boolean RESCALE_DIFF_TO_FULL_N = true;

    /** Run the lognormal phase (exp transform of the Gaussian data) in addition to the Gaussian phase. */
    private static final boolean RUN_LOGNORMAL = true;

    /** Imputations for the MICE arm. */
    private static final int NUM_IMPUTATIONS = 5;

    /** Seed for the MICE imputation chains, fixed so reruns are reproducible given the per-rep data seed. */
    private static final long MICE_SEED = 8675309L;

    /** Seconds any single arm may take before it is abandoned and reported as timed out; 0 disables. */
    private static final int ARM_TIMEOUT_SECONDS = 300;

    /**
     * Stream one line per arm per rep as it finishes. Without this, nothing prints until a whole condition (all
     * reps of all arms) completes, and the first condition front-loads the two slow arms -- MICE at tens of
     * seconds per rep, and BOSS 0DEL at its pathological densest at the lowest rate -- so the console sits silent
     * for minutes and the run looks stalled when it is not.
     */
    private static final boolean STREAM_PROGRESS = true;

    /** Score returned for a family or decision whose zero-deleted row set is too small to estimate. */
    private static final double STARVED_SCORE = -1e9;

    // ---------------------------------------------------------------- dropout mechanisms

    private enum Mechanism {
        /** Example 1: each cell is zeroed independently with the target probability, regardless of value. */
        FIXED_RATE,
        /** Example 2: per column, the lowest rate-quantile of values is set to zero. */
        TRUNCATED,
        /** Example 3: P(drop) = logistic(a - slope * standardized value); a is solved to hit the target rate. */
        LOGISTIC
    }

    /**
     * Applies a dropout mechanism to a copy of the data, writing literal zeros. Nothing is marked missing; the
     * output is exactly what a zero-contaminated dataset looks like.
     */
    private static DataSet applyDropout(DataSet complete, Mechanism mechanism, double rate) {
        DataSet out = complete.copy();
        int n = out.getNumRows();
        int p = out.getNumColumns();
        RandomUtil rand = RandomUtil.getInstance();

        for (int j = 0; j < p; j++) {
            switch (mechanism) {
                case FIXED_RATE -> {
                    for (int i = 0; i < n; i++) {
                        if (rand.nextDouble() < rate) out.setDouble(i, j, 0.0);
                    }
                }
                case TRUNCATED -> {
                    double[] col = new double[n];
                    for (int i = 0; i < n; i++) col[i] = out.getDouble(i, j);
                    double[] sorted = col.clone();
                    Arrays.sort(sorted);
                    int k = (int) Math.floor(rate * n);
                    if (k <= 0) break;
                    double threshold = sorted[Math.min(k, n - 1)];
                    for (int i = 0; i < n; i++) {
                        if (out.getDouble(i, j) < threshold) out.setDouble(i, j, 0.0);
                    }
                }
                case LOGISTIC -> {
                    double mean = 0, var = 0;
                    for (int i = 0; i < n; i++) mean += out.getDouble(i, j);
                    mean /= n;
                    for (int i = 0; i < n; i++) {
                        double d = out.getDouble(i, j) - mean;
                        var += d * d;
                    }
                    double sd = Math.sqrt(var / (n - 1));
                    if (sd == 0) sd = 1;

                    double[] std = new double[n];
                    for (int i = 0; i < n; i++) std[i] = (out.getDouble(i, j) - mean) / sd;

                    // Bisection on the intercept a so that the mean dropout probability hits the target rate.
                    double lo = -20, hi = 20;
                    for (int iter = 0; iter < 60; iter++) {
                        double a = 0.5 * (lo + hi);
                        double meanP = 0;
                        for (double s : std) meanP += 1.0 / (1.0 + Math.exp(-(a - LOGISTIC_SLOPE * s)));
                        meanP /= n;
                        if (meanP < rate) lo = a; else hi = a;
                    }
                    double a = 0.5 * (lo + hi);

                    for (int i = 0; i < n; i++) {
                        double pDrop = 1.0 / (1.0 + Math.exp(-(a - LOGISTIC_SLOPE * std[i])));
                        if (rand.nextDouble() < pDrop) out.setDouble(i, j, 0.0);
                    }
                }
            }
        }

        return out;
    }

    /** Replaces each value with its exponential, giving lognormal margins as in the paper's second case. */
    private static DataSet exponentiate(DataSet data) {
        DataSet out = data.copy();
        for (int j = 0; j < out.getNumColumns(); j++) {
            for (int i = 0; i < out.getNumRows(); i++) {
                out.setDouble(i, j, Math.exp(out.getDouble(i, j)));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- zero-deletion row machinery

    /**
     * Computes, and caches per column set, the rows of a dataset in which every listed column is nonzero. Shared by
     * the test and the score; the cache is concurrent because FGES and BOSS evaluate in parallel.
     */
    private static final class ZeroRows {
        private final double[][] cols; // column-major
        private final Map<ColKey, int[]> cache = new ConcurrentHashMap<>();

        ZeroRows(DataSet data) {
            int n = data.getNumRows();
            int p = data.getNumColumns();
            this.cols = new double[p][n];
            for (int j = 0; j < p; j++) {
                for (int i = 0; i < n; i++) {
                    this.cols[j][i] = data.getDouble(i, j);
                }
            }
        }

        double[][] columns() {
            return this.cols;
        }

        int numRows() {
            return this.cols.length == 0 ? 0 : this.cols[0].length;
        }

        /** Rows on which every column in {@code columns} is nonzero. The empty set returns all rows. */
        int[] nonzeroRows(int[] columns) {
            int[] sorted = columns.clone();
            Arrays.sort(sorted);
            return this.cache.computeIfAbsent(new ColKey(sorted), key -> {
                int n = numRows();
                int[] buf = new int[n];
                int m = 0;
                outer:
                for (int i = 0; i < n; i++) {
                    for (int c : key.cols) {
                        if (this.cols[c][i] == 0.0) continue outer;
                    }
                    buf[m++] = i;
                }
                return Arrays.copyOf(buf, m);
            });
        }

        private record ColKey(int[] cols) {
            @Override
            public boolean equals(Object o) {
                return o instanceof ColKey other && Arrays.equals(this.cols, other.cols);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(this.cols);
            }
        }
    }

    /**
     * The partial correlation of columns x and y given columns z, computed over the given rows, by inverting the
     * covariance matrix of (x, y, z) on those rows. Throws SingularMatrixException when the covariance is singular.
     */
    private static double partialCorrelationOnRows(double[][] cols, int x, int y, int[] z, int[] rows) {
        int d = 2 + z.length;
        int[] idx = new int[d];
        idx[0] = x;
        idx[1] = y;
        System.arraycopy(z, 0, idx, 2, z.length);

        int m = rows.length;
        double[] means = new double[d];
        for (int a = 0; a < d; a++) {
            double s = 0;
            for (int r : rows) s += cols[idx[a]][r];
            means[a] = s / m;
        }

        double[][] cov = new double[d][d];
        for (int a = 0; a < d; a++) {
            for (int b = a; b < d; b++) {
                double s = 0;
                for (int r : rows) {
                    s += (cols[idx[a]][r] - means[a]) * (cols[idx[b]][r] - means[b]);
                }
                cov[a][b] = s / (m - 1);
                cov[b][a] = cov[a][b];
            }
        }

        RealMatrix inv = new LUDecomposition(new Array2DRowRealMatrix(cov, false)).getSolver().getInverse();
        return -inv.getEntry(0, 1) / Math.sqrt(inv.getEntry(0, 0) * inv.getEntry(1, 1));
    }

    // ---------------------------------------------------------------- the zero-deletion Fisher-Z test

    /**
     * Fisher-Z conditional-independence test with zero-deletion. In CONDITIONING_ONLY mode it implements the
     * paper's Theorem 1 estimator: for X _||_ Y | S it uses the rows on which every member of S is nonzero, and
     * KEEPS the rows in which X or Y are zero. In ALL_INVOLVED mode it implements their Definition 2 variant,
     * using only the rows on which X, Y, and every member of S are all nonzero.
     *
     * <p>The statistic, degrees of freedom, p-value, and singularity handling mirror
     * {@link edu.cmu.tetrad.search.test.IndTestFisherZ}: df = m - |S| - 3 where m is the row count actually used;
     * a singular covariance is recorded as "dependent" (keeping the adjacency); a test with df below 1 is likewise
     * recorded as dependent and counted, so starvation shows up as density rather than a crash.</p>
     */
    static final class ZeroDeletionFisherZ implements IndependenceTest {

        enum DeletionMode {CONDITIONING_ONLY, ALL_INVOLVED}

        private static final NormalDistribution NORMAL = new NormalDistribution(0, 1);

        private final DataSet dataSet;
        private final List<Node> variables;
        private final Map<Node, Integer> indexOf = new ConcurrentHashMap<>();
        private final ZeroRows zeroRows;
        private final DeletionMode mode;
        private volatile double alpha;
        private volatile boolean verbose = false;
        private final java.util.concurrent.atomic.LongAdder starvedTests = new java.util.concurrent.atomic.LongAdder();

        ZeroDeletionFisherZ(DataSet dataSet, double alpha, DeletionMode mode) {
            this.dataSet = dataSet;
            this.variables = new ArrayList<>(dataSet.getVariables());
            for (int j = 0; j < this.variables.size(); j++) this.indexOf.put(this.variables.get(j), j);
            this.zeroRows = new ZeroRows(dataSet);
            this.alpha = alpha;
            this.mode = mode;
        }

        @Override
        public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
            int xi = this.indexOf.get(x);
            int yi = this.indexOf.get(y);
            int[] zi = new int[z.size()];
            int t = 0;
            for (Node node : z) zi[t++] = this.indexOf.get(node);

            int[] deletionCols;
            if (this.mode == DeletionMode.CONDITIONING_ONLY) {
                deletionCols = zi;
            } else {
                deletionCols = new int[zi.length + 2];
                deletionCols[0] = xi;
                deletionCols[1] = yi;
                System.arraycopy(zi, 0, deletionCols, 2, zi.length);
            }

            int[] rows = this.zeroRows.nonzeroRows(deletionCols);
            double df = rows.length - zi.length - 3.0;

            if (df < 1) {
                // Too few nonzero rows to test: record as dependent, keeping the adjacency, and count it.
                this.starvedTests.increment();
                return new IndependenceResult(new IndependenceFact(x, y, z), false, 0.0, this.alpha);
            }

            double r;
            try {
                r = partialCorrelationOnRows(this.zeroRows.columns(), xi, yi, zi, rows);
            } catch (SingularMatrixException e) {
                return new IndependenceResult(new IndependenceFact(x, y, z), false, 0.0, this.alpha);
            }

            double p;
            if (Math.abs(r) >= 1.0 || Double.isNaN(r)) {
                p = 0.0;
            } else {
                double q = 0.5 * (Math.log(1.0 + Math.abs(r)) - Math.log(1.0 - Math.abs(r)));
                double fisherZ = Math.sqrt(df) * q;
                p = 2 * (1.0 - NORMAL.cumulativeProbability(fisherZ));
            }

            boolean independent = p > this.alpha;
            return new IndependenceResult(new IndependenceFact(x, y, z), independent, p, this.alpha - p);
        }

        long starvedTestCount() {
            return this.starvedTests.sum();
        }

        @Override
        public List<Node> getVariables() {
            return this.variables;
        }

        @Override
        public DataSet getData() {
            return this.dataSet;
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
            return "Zero-deletion Fisher Z (" + this.mode + "), alpha = " + this.alpha;
        }
    }

    // ---------------------------------------------------------------- the zero-deletion BIC score

    /**
     * A SEM-BIC-shaped score with zero-deletion, in two distinct roles.
     *
     * <p>When {@code perDecisionDiff} is true, {@code localScoreDiff(x, y, z)} implements the paper's GES
     * integration: the partial correlation of x and y given z is computed on the rows where every member of z is
     * nonzero (zeros in x and y kept), optionally rescaled so the Fisher-Z statistic is preserved at the full
     * sample size, and converted to a BIC change of the same shape as SemBicScore's Nandy rule:
     * -N log(1 - r^2) - c log N. FGES consumes exactly this diff for its insert and delete decisions, so this arm
     * is a per-decision use of deletion with a single comparable penalty scale.</p>
     *
     * <p>{@code localScore(i, parents)} scores the family on the rows where i and all parents are nonzero, with
     * the likelihood and penalty taken at that family's own row count. Summing these across families -- which is
     * what BOSS does -- compares objectives computed on DIFFERENT row sets, the same incoherence our NaN studies
     * exposed. The BOSS arm uses this deliberately, as the thing under test, not as a recommendation.</p>
     *
     * <p>A family or decision whose surviving row count is too small returns {@link #STARVED_SCORE} (scaled by the
     * family size for local scores), so starvation reads as a strong penalty rather than a crash.</p>
     */
    static final class ZeroDeletionBicScore implements Score {

        private final List<Node> variables;
        private final ZeroRows zeroRows;
        private final double penaltyDiscount;
        private final boolean perDecisionDiff;
        private final boolean rescaleToFullN;

        ZeroDeletionBicScore(DataSet dataSet, double penaltyDiscount, boolean perDecisionDiff,
                             boolean rescaleToFullN) {
            this.variables = new ArrayList<>(dataSet.getVariables());
            this.zeroRows = new ZeroRows(dataSet);
            this.penaltyDiscount = penaltyDiscount;
            this.perDecisionDiff = perDecisionDiff;
            this.rescaleToFullN = rescaleToFullN;
        }

        @Override
        public double localScore(int i, int... parents) {
            int[] family = new int[parents.length + 1];
            family[0] = i;
            System.arraycopy(parents, 0, family, 1, parents.length);

            int[] rows = this.zeroRows.nonzeroRows(family);
            int m = rows.length;
            int k = parents.length;

            if (m < k + 3) {
                return STARVED_SCORE * (k + 1);
            }

            double sigma2;
            try {
                sigma2 = residualVariance(i, parents, rows);
            } catch (SingularMatrixException e) {
                return STARVED_SCORE * (k + 1);
            }

            if (sigma2 <= 0 || Double.isNaN(sigma2)) sigma2 = 1e-10;

            // Same shape as SemBicScore: 2 * lik - c * k * log(n), lik = -(n/2)(log(2 pi sigma^2) + 1),
            // with n the family's own surviving row count. Summing these across families mixes row sets.
            double lik = -0.5 * m * (Math.log(2 * Math.PI * sigma2) + 1);
            return 2 * lik - this.penaltyDiscount * k * Math.log(m);
        }

        /**
         * FGES's effect-edge initialization calls the TWO-argument diff, whose interface default would bypass the
         * per-decision override below; routing it here keeps every decision on the same rule. With an empty
         * conditioning set, conditioning-set deletion deletes nothing, matching the paper (marginal dependence is
         * preserved under dropout; only conditional estimates need deletion).
         */
        @Override
        public double localScoreDiff(int x, int y) {
            return localScoreDiff(x, y, new int[0]);
        }

        @Override
        public double localScoreDiff(int x, int y, int[] z) {
            if (!this.perDecisionDiff) {
                return localScore(y, append(z, x)) - localScore(y, z);
            }

            int[] rows = this.zeroRows.nonzeroRows(z);
            int m = rows.length;
            int nFull = this.zeroRows.numRows();

            double dfDel = m - z.length - 3.0;
            double dfFull = nFull - z.length - 3.0;

            if (dfDel < 1 || dfFull < 1) {
                return STARVED_SCORE;
            }

            double r;
            try {
                r = partialCorrelationOnRows(this.zeroRows.columns(), x, y, z, rows);
            } catch (SingularMatrixException e) {
                return STARVED_SCORE;
            }

            if (Double.isNaN(r)) return STARVED_SCORE;
            r = Math.max(-0.999999, Math.min(0.999999, r));

            double rUsed;
            double nUsed;

            if (this.rescaleToFullN) {
                // Preserve the Fisher-Z statistic at the full sample size, per the paper's GES integration:
                // atanh(r') sqrt(dfFull) = atanh(r) sqrt(dfDel).
                double q = 0.5 * Math.log((1.0 + r) / (1.0 - r));
                double qPrime = q * Math.sqrt(dfDel / dfFull);
                rUsed = Math.tanh(qPrime);
                nUsed = nFull;
            } else {
                rUsed = r;
                nUsed = m;
            }

            // The Nandy-rule BIC change for adding x to y's parents given z; positive favors the edge.
            return -nUsed * Math.log(1.0 - rUsed * rUsed) - this.penaltyDiscount * Math.log(nUsed);
        }

        /** OLS residual variance of column i regressed on the parent columns, over the given rows. */
        private double residualVariance(int i, int[] parents, int[] rows) {
            double[][] cols = this.zeroRows.columns();
            int m = rows.length;
            int k = parents.length;

            double meanI = 0;
            for (int r : rows) meanI += cols[i][r];
            meanI /= m;

            if (k == 0) {
                double s = 0;
                for (int r : rows) {
                    double d = cols[i][r] - meanI;
                    s += d * d;
                }
                return s / (m - 1);
            }

            double[] meansP = new double[k];
            for (int a = 0; a < k; a++) {
                double s = 0;
                for (int r : rows) s += cols[parents[a]][r];
                meansP[a] = s / m;
            }

            double[][] cpp = new double[k][k];
            double[] cpi = new double[k];
            double varI = 0;

            for (int r : rows) {
                double di = cols[i][r] - meanI;
                varI += di * di;
                for (int a = 0; a < k; a++) {
                    double da = cols[parents[a]][r] - meansP[a];
                    cpi[a] += da * di;
                    for (int b = a; b < k; b++) {
                        cpp[a][b] += da * (cols[parents[b]][r] - meansP[b]);
                    }
                }
            }

            for (int a = 0; a < k; a++) {
                for (int b = a; b < k; b++) {
                    cpp[a][b] /= (m - 1);
                    cpp[b][a] = cpp[a][b];
                }
                cpi[a] /= (m - 1);
            }
            varI /= (m - 1);

            RealMatrix inv = new LUDecomposition(new Array2DRowRealMatrix(cpp, false)).getSolver().getInverse();
            double explained = 0;
            for (int a = 0; a < k; a++) {
                for (int b = 0; b < k; b++) {
                    explained += cpi[a] * inv.getEntry(a, b) * cpi[b];
                }
            }

            return varI - explained;
        }

        @Override
        public List<Node> getVariables() {
            return this.variables;
        }

        @Override
        public int getSampleSize() {
            return this.zeroRows.numRows();
        }

        @Override
        public String toString() {
            return "Zero-deletion BIC score (perDecisionDiff = " + this.perDecisionDiff
                   + ", rescaleToFullN = " + this.rescaleToFullN + ", c = " + this.penaltyDiscount + ")";
        }
    }

    // ---------------------------------------------------------------- arms

    private interface Arm {
        Graph search(DataSet complete, DataSet dropped) throws Exception;
    }

    private static Map<String, Arm> arms() {
        Map<String, Arm> arms = new LinkedHashMap<>();

        arms.put("ORACLE PC", (complete, dropped) -> pc(new IndTestFisherZ(complete, ALPHA)));
        arms.put("ORACLE FGES", (complete, dropped) -> fges(semBic(complete)));
        arms.put("ORACLE BOSS", (complete, dropped) -> boss(semBic(complete)));

        arms.put("FULL PC", (complete, dropped) -> pc(new IndTestFisherZ(dropped, ALPHA)));
        arms.put("FULL FGES", (complete, dropped) -> fges(semBic(dropped)));
        arms.put("FULL BOSS", (complete, dropped) -> boss(semBic(dropped)));

        arms.put("PC 0DEL-COND", (complete, dropped) ->
                pc(new ZeroDeletionFisherZ(dropped, ALPHA, ZeroDeletionFisherZ.DeletionMode.CONDITIONING_ONLY)));
        arms.put("PC 0DEL-ALL", (complete, dropped) ->
                pc(new ZeroDeletionFisherZ(dropped, ALPHA, ZeroDeletionFisherZ.DeletionMode.ALL_INVOLVED)));

        arms.put("FGES 0DEL (per-decision)", (complete, dropped) ->
                fges(new ZeroDeletionBicScore(dropped, PENALTY_DISCOUNT, true, RESCALE_DIFF_TO_FULL_N)));

        arms.put("BOSS 0DEL (summed)", (complete, dropped) ->
                boss(new ZeroDeletionBicScore(dropped, PENALTY_DISCOUNT, false, RESCALE_DIFF_TO_FULL_N)));

        // Multiple imputation by predictive mean matching, all zeros treated as missing, followed by a single
        // BOSS run on the IMaGES score over the m completed datasets. In this continuous simulation every zero IS
        // technical, so the zeros-as-missing coding is exact; in a count regime it would also blank biological
        // zeros, which is the additional bias the paper warns about and which this phase cannot exercise.
        // Prediction: under FIXED_RATE the missingness is MCAR and this arm should approach oracle; under
        // TRUNCATED and LOGISTIC it is self-masking MNAR, and PMM can only donate observed (selection-biased)
        // values -- under truncation, none below the threshold -- so the paper's imputation-bias argument gets a
        // fair test on its own mechanisms.
        arms.put("MICE-PMM + IMaGES BOSS", (complete, dropped) -> {
            DataSet asMissing = zerosToMissing(dropped);
            List<DataSet> imputed = new MiceLiteImputer().impute(asMissing, NUM_IMPUTATIONS, MICE_SEED);

            List<Score> scores = new ArrayList<>();
            for (DataSet d : imputed) {
                scores.add(semBic(d));
            }

            return boss(new ImagesScore(scores));
        });

        return arms;
    }

    // ---------------------------------------------------------------- main

    /**
     * Entry point. Streams one block per (phase, mechanism, rate) condition with mean adjacency and arrowhead
     * precision and recall, SHD, and seconds per arm.
     *
     * @param args Unused.
     * @throws Exception If a phase fails irrecoverably.
     */
    public static void main(String[] args) throws Exception {
        System.out.printf("Dropout zero-deletion study (Dai et al., ICLR 2024 replication): "
                          + "p=%d, edges=%d, n=%d, reps=%d, alpha=%.2f, penalty=%.1f%n",
                NUM_VARS, NUM_EDGES, SAMPLE_SIZE, NUM_REPS, ALPHA, PENALTY_DISCOUNT);
        System.out.println("Zeros are written into the data; nothing is marked missing. 0DEL-COND deletes rows");
        System.out.println("with zeros in the conditioning set only (their Theorem 1); 0DEL-ALL deletes rows with");
        System.out.println("zeros in any involved variable (their Definition 2). The FGES 0DEL arm uses the");
        System.out.println("per-decision rescaled diff (their GES integration); the BOSS 0DEL arm sums per-family");
        System.out.println("zero-deleted scores, which their theory does NOT license, as a negative control.");
        System.out.println();

        runPhase("GAUSSIAN", false);

        if (RUN_LOGNORMAL) {
            runPhase("LOGNORMAL", true);
        }
    }

    private static void runPhase(String label, boolean lognormal) throws Exception {
        System.out.println("################ " + label + " ################");
        System.out.println();

        for (Mechanism mechanism : Mechanism.values()) {
            for (double rate : DROPOUT_RATES) {
                System.out.printf("=== %s, mechanism = %s, target rate = %.1f ===%n", label, mechanism, rate);
                System.out.printf("%-28s %8s %8s %8s %8s %8s %9s%n",
                        "method", "adjPrec", "adjRec", "arrPrec", "arrRec", "SHD", "seconds");

                Map<String, Arm> arms = arms();
                // Per arm: sums of the six statistics and, in parallel, counts of the FINITE values summed, so
                // that one undefined statistic (e.g., arrowhead precision when an arm orients no edges) reads as
                // a smaller denominator rather than poisoning the mean with NaN.
                Map<String, double[]> totals = new LinkedHashMap<>();
                Map<String, int[]> counts = new LinkedHashMap<>();
                for (String name : arms.keySet()) {
                    totals.put(name, new double[6]);
                    counts.put(name, new int[6]);
                }
                Set<String> shdUndefined = new LinkedHashSet<>();

                double achievedZeroFraction = 0;

                for (int rep = 0; rep < NUM_REPS; rep++) {
                    RandomUtil.getInstance().setSeed(1000L * (mechanism.ordinal() + 1) + 100L * (long) (rate * 10) + rep);

                    Graph dag = RandomGraph.randomGraph(NUM_VARS, 0, NUM_EDGES, 100, 100, 100, false);
                    SemIm im = new SemIm(new SemPm(dag));
                    DataSet complete = im.simulateData(SAMPLE_SIZE, false);

                    if (lognormal) {
                        complete = exponentiate(complete);
                    }

                    Graph trueCpdag = GraphTransforms.dagToCpdag(dag);
                    DataSet dropped = applyDropout(complete, mechanism, rate);
                    achievedZeroFraction += zeroFraction(dropped);

                    for (Map.Entry<String, Arm> entry : arms.entrySet()) {
                        long t0 = System.currentTimeMillis();
                        Graph est;

                        try {
                            est = runWithTimeout(entry.getValue(), complete, dropped);
                        } catch (java.util.concurrent.TimeoutException e) {
                            System.out.printf("  [%s] rep %d timed out after %ds; arm abandoned for this rep%n",
                                    entry.getKey(), rep, ARM_TIMEOUT_SECONDS);
                            continue;
                        } catch (Exception e) {
                            System.out.printf("  [%s] rep %d failed: %s%n", entry.getKey(), rep, e.getMessage());
                            continue;
                        }

                        double seconds = (System.currentTimeMillis() - t0) / 1000.0;

                        if (STREAM_PROGRESS) {
                            System.out.printf("  rep %d  %-28s %6.1fs%n", rep, entry.getKey(), seconds);
                            System.out.flush();
                        }

                        est = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(est, trueCpdag.getNodes());

                        double[] t = totals.get(entry.getKey());
                        int[] c = counts.get(entry.getKey());
                        accumulate(t, c, 0, new AdjacencyPrecision().getValue(trueCpdag, est, dropped));
                        accumulate(t, c, 1, new AdjacencyRecall().getValue(trueCpdag, est, dropped));
                        accumulate(t, c, 2, new ArrowheadPrecision().getValue(trueCpdag, est, dropped));
                        accumulate(t, c, 3, new ArrowheadRecall().getValue(trueCpdag, est, dropped));
                        accumulate(t, c, 4, shd(trueCpdag, est, dropped, entry.getKey(), shdUndefined));
                        accumulate(t, c, 5, seconds);
                    }

                    System.out.flush();
                }

                for (Map.Entry<String, double[]> entry : totals.entrySet()) {
                    double[] t = entry.getValue();
                    int[] c = counts.get(entry.getKey());
                    boolean undefined = shdUndefined.contains(entry.getKey());

                    System.out.printf("%-28s %8s %8s %8s %8s %8s %9s%n",
                            entry.getKey(), mean(t, c, 0, "%.3f"), mean(t, c, 1, "%.3f"),
                            mean(t, c, 2, "%.3f"), mean(t, c, 3, "%.3f"),
                            undefined ? "n/a" : mean(t, c, 4, "%.1f"), mean(t, c, 5, "%.1f"));
                }

                if (!shdUndefined.isEmpty()) {
                    System.out.println("(SHD is n/a for " + String.join(", ", shdUndefined)
                                       + ": the graph is not a legal PDAG, so the measure is undefined for it.)");
                }

                System.out.printf("(mean achieved zero fraction: %.3f)%n%n", achievedZeroFraction / NUM_REPS);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** A copy of the data with every zero replaced by NaN, coding all zeros as missing for the MICE arm. */
    private static DataSet zerosToMissing(DataSet data) {
        DataSet out = data.copy();
        for (int j = 0; j < out.getNumColumns(); j++) {
            for (int i = 0; i < out.getNumRows(); i++) {
                if (out.getDouble(i, j) == 0.0) out.setDouble(i, j, Double.NaN);
            }
        }
        return out;
    }

    /** Adds a statistic value into the running sums, skipping NaN and infinity but counting what was added. */
    private static void accumulate(double[] sums, int[] counts, int slot, double value) {
        if (Double.isFinite(value)) {
            sums[slot] += value;
            counts[slot]++;
        }
    }

    /** The mean of the finite values accumulated in a slot, formatted, or "n/a" when none were. */
    private static String mean(double[] sums, int[] counts, int slot, String format) {
        if (counts[slot] == 0) return "n/a";
        return String.format(format, sums[slot] / counts[slot]);
    }

    private static double zeroFraction(DataSet data) {
        long zeros = 0;
        long total = (long) data.getNumRows() * data.getNumColumns();
        for (int j = 0; j < data.getNumColumns(); j++) {
            for (int i = 0; i < data.getNumRows(); i++) {
                if (data.getDouble(i, j) == 0.0) zeros++;
            }
        }
        return zeros / (double) total;
    }

    private static double shd(Graph trueCpdag, Graph est, DataSet data, String armName, Set<String> undefined) {
        double value = new StructuralHammingDistance().getValue(trueCpdag, est, data);

        if (value < 0) {
            undefined.add(armName);
            return 0.0;
        }

        return value;
    }

    private static Graph runWithTimeout(Arm arm, DataSet complete, DataSet dropped) throws Exception {
        if (ARM_TIMEOUT_SECONDS <= 0) return arm.search(complete, dropped);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "study-arm");
            t.setDaemon(true);
            return t;
        });

        try {
            java.util.concurrent.Future<Graph> future = executor.submit(() -> arm.search(complete, dropped));

            try {
                return future.get(ARM_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                throw cause instanceof Exception ex ? ex : new RuntimeException(cause);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static edu.cmu.tetrad.search.score.SemBicScore semBic(DataSet data) {
        edu.cmu.tetrad.search.score.SemBicScore score =
                new edu.cmu.tetrad.search.score.SemBicScore(data, true);
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    private static Graph pc(IndependenceTest test) throws InterruptedException {
        Pc pc = new Pc(test);
        pc.setVerbose(false);
        Graph graph = pc.search();

        if (test instanceof ZeroDeletionFisherZ zd && zd.starvedTestCount() > 0) {
            System.out.printf("  (note: %d CI tests had too few nonzero rows and were recorded as dependent)%n",
                    zd.starvedTestCount());
        }

        return graph;
    }

    private static Graph fges(Score score) throws InterruptedException {
        Fges fges = new Fges(score);
        fges.setVerbose(false);
        return fges.search();
    }

    private static Graph boss(Score score) throws InterruptedException {
        edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(score);
        boss.setUseBes(false);
        boss.setNumStarts(1);
        return new PermutationSearch(boss).search();
    }
}
