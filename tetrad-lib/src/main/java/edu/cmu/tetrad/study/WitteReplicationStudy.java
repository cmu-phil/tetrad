package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.missing.MiceLiteImputer;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.DegenerateGaussianScore;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestDegenerateGaussianLrt;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Replication harness for Witte, Foraita and Didelez, "Multiple imputation and test-wise deletion for causal
 * discovery with incomplete cohort data" (arXiv:2108.13331, 2021).
 *
 * <p>Their claims under test, labeled as in our review:</p>
 * <ul>
 *   <li><b>W1</b>: test-wise deletion and multiple imputation both clearly beat list-wise deletion and single
 *       mean/mode imputation (which produces too-dense graphs);</li>
 *   <li><b>W2</b>: for Gaussian data under MCAR and MAR, MI beats test-wise deletion, with the gap invisible at
 *       n = 100 and largest at n = 5000;</li>
 *   <li><b>W3</b>: under their MNAR design (key-variable deletion, built to satisfy the admissible separator
 *       condition), test-wise deletion stays valid while MI does not;</li>
 *   <li><b>W4</b>: for mixed data, test-wise deletion beats MI in all their scenarios;</li>
 *   <li><b>W5</b>: their warning against predictive mean matching (increased type I error, results not shown).
 *       Our MICE arms use PMM, so depressed MICE precision under Gaussian MCAR at large n would support them,
 *       and clean precision there would not.</li>
 * </ul>
 *
 * <p>Design: two phases (Gaussian p = 12, 17 edges, their ECOLI shape; mixed p = 8, 9 edges, half discrete with
 * 3 categories, their HEALTHCARE shape); three mechanisms at their 18% overall rate (MCAR; their ampute-style
 * MAR with exactly one group member missing per row, chosen by the other members' values, topped up by one MCAR
 * column; their MNAR with the key variable's largest rows deleting the key and four subordinates); n in
 * {100, 1000, 5000}.</p>
 *
 * <p>Arms per phase: ORACLE BOSS on the complete data; LISTWISE PC (complete rows only); MEAN/MODE-IMPUTE PC;
 * TESTWISE PC (Fisher-Z or DG-LRT with the testwise spec); MICE-PMM m = 5 with the test-level pooled-z PC that
 * is their actual procedure (Gaussian phase only -- pooling the DG likelihood-ratio statistic needs Meng-Rubin
 * rules we have not implemented); MICE-PMM m = 5 + IMaGES BOSS (SEM-BIC or DG components); and, Gaussian only,
 * MVN-EM covariance BOSS. Including both MI arms separates "MI versus deletion" from "PC versus BOSS", which
 * their design confounds.</p>
 *
 * <p>Parallelism: repetitions run concurrently (RandomUtil is thread-local, and each rep seeds its own thread's
 * instance from the condition and rep index, so results are reproducible regardless of scheduling), and the
 * m MICE chains within an arm run concurrently (the imputer takes an explicit seed and uses no shared state
 * across instances). Arms within a rep run sequentially on the rep's thread.</p>
 *
 * <p>Run from IntelliJ; one line per arm per rep streams as it finishes, and a table prints per condition.
 * Nothing outside this file is touched.</p>
 *
 * @author josephramsey
 */
public final class WitteReplicationStudy {

    private WitteReplicationStudy() {
    }

    // ---------------------------------------------------------------- configuration

    /** Variables and edges in the Gaussian phase (their ECOLI shape). */
    private static final int GAUSS_VARS = 12;
    private static final int GAUSS_EDGES = 17;

    /** Variables and edges in the mixed phase (their HEALTHCARE shape). */
    private static final int MIXED_VARS = 8;
    private static final int MIXED_EDGES = 9;
    private static final double PERCENT_DISCRETE = 50.0;
    private static final int NUM_CATEGORIES = 3;

    /** Sample sizes to sweep; W2 is explicitly a trend across these. */
    private static final int[] SAMPLE_SIZES = {100, 1000, 5000};

    /** Repetitions per condition; each uses a fresh graph and dataset. */
    private static final int NUM_REPS = 5;

    /** Their overall missingness rate. */
    private static final double MISSING_RATE = 0.18;

    /** Imputations for the MICE arms. */
    private static final int NUM_IMPUTATIONS = 5;

    /** Fisher-Z / DG-LRT alpha, matching their 0.05. */
    private static final double ALPHA = 0.05;

    /** BIC penalty discount for every score arm. */
    private static final double PENALTY_DISCOUNT = 2.0;

    /** Slope of the MAR selection weights on the standardized scale. */
    private static final double MAR_SLOPE = 1.5;

    /** Subordinate variables deleted along with the key variable under MNAR. */
    private static final int MNAR_SUBORDINATES = 4;

    /** Base seed for the MICE chains; chain k of a rep uses MICE_SEED + k. */
    private static final long MICE_SEED = 8675309L;

    /** Stream one line per arm per rep as it finishes. */
    private static final boolean STREAM_PROGRESS = true;

    /** Threads for concurrent repetitions. */
    private static final int REP_THREADS =
            Math.max(1, Math.min(NUM_REPS, Runtime.getRuntime().availableProcessors() / 2));

    // ---------------------------------------------------------------- phases and mechanisms

    private enum Phase {GAUSSIAN, MIXED}

    private enum Mechanism {MCAR, MAR_GROUP, MNAR_KEY}

    // ---------------------------------------------------------------- missingness injection

    /**
     * Marks a cell missing: NaN for continuous, {@link DiscreteVariable#MISSING_VALUE} for discrete.
     */
    private static void markMissing(DataSet data, int row, int col) {
        if (data.getVariable(col) instanceof DiscreteVariable) {
            data.setInt(row, col, DiscreteVariable.MISSING_VALUE);
        } else {
            data.setDouble(row, col, Double.NaN);
        }
    }

    /** The cell value as a double for weighting purposes: the category index for discrete variables. */
    private static double raw(DataSet data, int row, int col) {
        return data.getVariable(col) instanceof DiscreteVariable
                ? data.getInt(row, col) : data.getDouble(row, col);
    }

    /**
     * Injects missingness into a copy of the data under the given mechanism, targeting {@link #MISSING_RATE}
     * overall. Uses the calling thread's RandomUtil, which the rep has seeded.
     */
    private static DataSet injectMissing(DataSet complete, Mechanism mechanism) {
        DataSet out = complete.copy();
        int n = out.getNumRows();
        int p = out.getNumColumns();
        RandomUtil rand = RandomUtil.getInstance();

        switch (mechanism) {
            case MCAR -> {
                for (int j = 0; j < p; j++) {
                    for (int i = 0; i < n; i++) {
                        if (rand.nextDouble() < MISSING_RATE) markMissing(out, i, j);
                    }
                }
            }

            case MAR_GROUP -> {
                // Their ampute-style design: disjoint groups of 3; in each row, exactly one member of each
                // group is made missing, chosen with probability depending on the OTHER members' values (which
                // stay observed, making this MAR). One further non-group column takes MCAR deletion to top the
                // overall rate up to MISSING_RATE.
                List<Integer> order = new ArrayList<>();
                for (int j = 0; j < p; j++) order.add(j);
                shuffle(order, rand);

                int numGroups = Math.max(1, (int) Math.floor(MISSING_RATE * p)); // 2 for p=12, 1 for p=8
                int groupSize = 3;
                int[][] groups = new int[numGroups][groupSize];
                int t = 0;
                for (int g = 0; g < numGroups; g++) {
                    for (int k = 0; k < groupSize; k++) groups[g][k] = order.get(t++);
                }
                int topUpCol = order.get(t); // outside every group

                // Standardize each column of the complete data for the weights.
                double[] mean = new double[p];
                double[] sd = new double[p];
                for (int j = 0; j < p; j++) {
                    double s = 0;
                    for (int i = 0; i < n; i++) s += raw(complete, i, j);
                    mean[j] = s / n;
                    double v = 0;
                    for (int i = 0; i < n; i++) {
                        double d = raw(complete, i, j) - mean[j];
                        v += d * d;
                    }
                    sd[j] = Math.sqrt(v / (n - 1));
                    if (sd[j] == 0) sd[j] = 1;
                }

                for (int i = 0; i < n; i++) {
                    for (int[] group : groups) {
                        double[] w = new double[groupSize];
                        double sum = 0;
                        for (int k = 0; k < groupSize; k++) {
                            double others = 0;
                            for (int k2 = 0; k2 < groupSize; k2++) {
                                if (k2 == k) continue;
                                int j = group[k2];
                                others += (raw(complete, i, j) - mean[j]) / sd[j];
                            }
                            w[k] = Math.exp(MAR_SLOPE * others / (groupSize - 1));
                            sum += w[k];
                        }
                        double u = rand.nextDouble() * sum, acc = 0;
                        int chosen = groupSize - 1;
                        for (int k = 0; k < groupSize; k++) {
                            acc += w[k];
                            if (u <= acc) {
                                chosen = k;
                                break;
                            }
                        }
                        markMissing(out, i, group[chosen]);
                    }
                }

                double topUpRate = Math.max(0, MISSING_RATE * p - numGroups); // cells per row still needed
                for (int i = 0; i < n; i++) {
                    if (rand.nextDouble() < topUpRate) markMissing(out, i, topUpCol);
                }
            }

            case MNAR_KEY -> {
                // Their design: a continuous key variable; in the rows with the q% largest key values, the key
                // and MNAR_SUBORDINATES subordinate variables are all deleted, q chosen to hit the overall rate.
                List<Integer> order = new ArrayList<>();
                for (int j = 0; j < p; j++) order.add(j);
                shuffle(order, rand);

                int key = -1;
                for (int j : order) {
                    if (complete.getVariable(j) instanceof ContinuousVariable) {
                        key = j;
                        break;
                    }
                }
                if (key < 0) throw new IllegalStateException("No continuous variable available as MNAR key.");

                int[] subs = new int[MNAR_SUBORDINATES];
                int t = 0;
                for (int j : order) {
                    if (j == key) continue;
                    subs[t++] = j;
                    if (t == MNAR_SUBORDINATES) break;
                }

                double q = MISSING_RATE * p / (1 + MNAR_SUBORDINATES);

                double[] keyVals = new double[n];
                for (int i = 0; i < n; i++) keyVals[i] = complete.getDouble(i, key);
                double[] sorted = keyVals.clone();
                Arrays.sort(sorted);
                double threshold = sorted[Math.max(0, n - 1 - (int) Math.floor(q * n))];

                for (int i = 0; i < n; i++) {
                    if (keyVals[i] >= threshold) {
                        markMissing(out, i, key);
                        for (int s : subs) markMissing(out, i, s);
                    }
                }
            }
        }

        return out;
    }

    /** Fisher-Yates shuffle using RandomUtil, so rep seeding controls it. */
    private static void shuffle(List<Integer> list, RandomUtil rand) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }

    // ---------------------------------------------------------------- simple handlers

    private static boolean isMissing(DataSet data, int row, int col) {
        if (data.getVariable(col) instanceof DiscreteVariable) {
            return data.getInt(row, col) == DiscreteVariable.MISSING_VALUE;
        }
        return Double.isNaN(data.getDouble(row, col));
    }

    /** The complete rows of a dataset, as a dataset. Throws if fewer than 20 survive. */
    private static DataSet completeRows(DataSet data) {
        List<Integer> rows = new ArrayList<>();
        outer:
        for (int i = 0; i < data.getNumRows(); i++) {
            for (int j = 0; j < data.getNumColumns(); j++) {
                if (isMissing(data, i, j)) continue outer;
            }
            rows.add(i);
        }
        if (rows.size() < 20) {
            throw new IllegalStateException("listwise deletion leaves only " + rows.size() + " complete rows");
        }
        return data.subsetRows(rows);
    }

    /** Mean (continuous) / mode (discrete) single imputation. */
    private static DataSet meanModeImpute(DataSet data) {
        DataSet out = data.copy();
        int n = out.getNumRows();

        for (int j = 0; j < out.getNumColumns(); j++) {
            if (out.getVariable(j) instanceof DiscreteVariable dv) {
                int[] counts = new int[dv.getNumCategories()];
                for (int i = 0; i < n; i++) {
                    int v = out.getInt(i, j);
                    if (v != DiscreteVariable.MISSING_VALUE) counts[v]++;
                }
                int mode = 0;
                for (int c = 1; c < counts.length; c++) if (counts[c] > counts[mode]) mode = c;
                for (int i = 0; i < n; i++) {
                    if (out.getInt(i, j) == DiscreteVariable.MISSING_VALUE) out.setInt(i, j, mode);
                }
            } else {
                double sum = 0;
                int count = 0;
                for (int i = 0; i < n; i++) {
                    double v = out.getDouble(i, j);
                    if (!Double.isNaN(v)) {
                        sum += v;
                        count++;
                    }
                }
                double mean = count > 0 ? sum / count : 0.0;
                for (int i = 0; i < n; i++) {
                    if (Double.isNaN(out.getDouble(i, j))) out.setDouble(i, j, mean);
                }
            }
        }

        return out;
    }

    /** The m MICE-PMM imputations, chains run concurrently with deterministic per-chain seeds. */
    private static List<DataSet> miceImpute(DataSet data) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(NUM_IMPUTATIONS);
        try {
            List<Future<DataSet>> futures = new ArrayList<>();
            for (int k = 0; k < NUM_IMPUTATIONS; k++) {
                final long seed = MICE_SEED + k;
                futures.add(pool.submit(() -> new MiceLiteImputer().impute(data, 1, seed).get(0)));
            }
            List<DataSet> out = new ArrayList<>();
            for (Future<DataSet> f : futures) out.add(f.get());
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- pooled-z Fisher test (their MI procedure)

    /**
     * Fisher-Z conditional-independence testing under multiple imputation with test-level pooling, which is the
     * procedure Witte et al. actually evaluate (following Foraita et al. 2020): the z-statistic is computed on
     * each completed dataset and the m statistics are pooled by Rubin's rules (within-variance 1 for the
     * Fisher z, between-variance from the m statistics, reference t distribution with Barnard-Rubin-style
     * degrees of freedom). Continuous data only.
     */
    static final class PooledZFisherTest implements IndependenceTest {

        private static final NormalDistribution NORMAL = new NormalDistribution(0, 1);

        private final DataSet first;
        private final List<Node> variables;
        private final Map<Node, Integer> indexOf = new ConcurrentHashMap<>();
        private final List<RealMatrix> covs = new ArrayList<>();
        private final int n;
        private volatile double alpha;
        private volatile boolean verbose = false;

        PooledZFisherTest(List<DataSet> imputed, double alpha) {
            this.first = imputed.get(0);
            this.variables = new ArrayList<>(this.first.getVariables());
            for (int j = 0; j < this.variables.size(); j++) this.indexOf.put(this.variables.get(j), j);
            this.n = this.first.getNumRows();
            this.alpha = alpha;

            for (DataSet d : imputed) {
                int p = d.getNumColumns();
                double[] mean = new double[p];
                for (int j = 0; j < p; j++) {
                    double s = 0;
                    for (int i = 0; i < this.n; i++) s += d.getDouble(i, j);
                    mean[j] = s / this.n;
                }
                double[][] cov = new double[p][p];
                for (int a = 0; a < p; a++) {
                    for (int b = a; b < p; b++) {
                        double s = 0;
                        for (int i = 0; i < this.n; i++) {
                            s += (d.getDouble(i, a) - mean[a]) * (d.getDouble(i, b) - mean[b]);
                        }
                        cov[a][b] = s / (this.n - 1);
                        cov[b][a] = cov[a][b];
                    }
                }
                this.covs.add(new Array2DRowRealMatrix(cov, false));
            }
        }

        @Override
        public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
            int xi = this.indexOf.get(x);
            int yi = this.indexOf.get(y);
            int[] zi = new int[z.size()];
            int t = 0;
            for (Node node : z) zi[t++] = this.indexOf.get(node);

            double df = this.n - zi.length - 3.0;
            if (df < 1) {
                return new IndependenceResult(new IndependenceFact(x, y, z), false, 0.0, this.alpha);
            }

            int m = this.covs.size();
            double[] zs = new double[m];

            for (int k = 0; k < m; k++) {
                double r;
                try {
                    r = partialCorrFromCov(this.covs.get(k), xi, yi, zi);
                } catch (SingularMatrixException e) {
                    return new IndependenceResult(new IndependenceFact(x, y, z), false, 0.0, this.alpha);
                }
                r = Math.max(-0.999999, Math.min(0.999999, r));
                zs[k] = Math.sqrt(df) * 0.5 * Math.log((1.0 + r) / (1.0 - r));
            }

            double zbar = 0;
            for (double v : zs) zbar += v;
            zbar /= m;

            double b = 0;
            for (double v : zs) b += (v - zbar) * (v - zbar);
            b = m > 1 ? b / (m - 1) : 0.0;

            double p;
            if (b < 1e-12) {
                p = 2 * (1.0 - NORMAL.cumulativeProbability(Math.abs(zbar)));
            } else {
                double totalVar = 1.0 + (1.0 + 1.0 / m) * b;
                double stat = zbar / Math.sqrt(totalVar);
                double nu = (m - 1) * Math.pow(1.0 + 1.0 / ((1.0 + 1.0 / m) * b), 2);
                p = 2 * (1.0 - new TDistribution(nu).cumulativeProbability(Math.abs(stat)));
            }

            boolean independent = p > this.alpha;
            return new IndependenceResult(new IndependenceFact(x, y, z), independent, p, this.alpha - p);
        }

        private static double partialCorrFromCov(RealMatrix cov, int x, int y, int[] z) {
            int d = 2 + z.length;
            int[] idx = new int[d];
            idx[0] = x;
            idx[1] = y;
            System.arraycopy(z, 0, idx, 2, z.length);

            double[][] sub = new double[d][d];
            for (int a = 0; a < d; a++) {
                for (int b = 0; b < d; b++) sub[a][b] = cov.getEntry(idx[a], idx[b]);
            }

            RealMatrix inv = new LUDecomposition(new Array2DRowRealMatrix(sub, false)).getSolver().getInverse();
            return -inv.getEntry(0, 1) / Math.sqrt(inv.getEntry(0, 0) * inv.getEntry(1, 1));
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
            return "Pooled-z Fisher test over " + this.covs.size() + " imputations, alpha = " + this.alpha;
        }
    }

    // ---------------------------------------------------------------- arms

    private interface Arm {
        Graph search(DataSet complete, DataSet missing) throws Exception;
    }

    private static Map<String, Arm> arms(Phase phase) {
        Map<String, Arm> arms = new LinkedHashMap<>();
        boolean gaussian = phase == Phase.GAUSSIAN;

        arms.put("ORACLE BOSS", (complete, missing) ->
                boss(gaussian ? semBic(complete) : dgScore(complete)));

        arms.put("LISTWISE PC", (complete, missing) -> {
            DataSet cc = completeRows(missing);
            return pc(gaussian ? new IndTestFisherZ(cc, ALPHA) : dgTest(cc));
        });

        arms.put("MEAN/MODE PC", (complete, missing) -> {
            DataSet filled = meanModeImpute(missing);
            return pc(gaussian ? new IndTestFisherZ(filled, ALPHA) : dgTest(filled));
        });

        arms.put("TESTWISE PC", (complete, missing) -> {
            if (gaussian) {
                return pc(new IndTestFisherZ(missing, ALPHA, MissingDataSpec.testwise()));
            }
            IndTestDegenerateGaussianLrt test = new IndTestDegenerateGaussianLrt(missing, MissingDataSpec.testwise());
            test.setAlpha(ALPHA);
            return pc(test);
        });

        if (gaussian) {
            // Their actual MI procedure: test-level Rubin pooling inside PC. Continuous only -- pooling the DG
            // likelihood-ratio statistic needs the Meng-Rubin rules, which we have not implemented.
            arms.put("MICE POOLED-Z PC", (complete, missing) ->
                    pc(new PooledZFisherTest(miceImpute(missing), ALPHA)));
        }

        arms.put("MICE + IMaGES BOSS", (complete, missing) -> {
            List<DataSet> imputed = miceImpute(missing);
            List<Score> scores = new ArrayList<>();
            for (DataSet d : imputed) scores.add(gaussian ? semBic(d) : dgScore(d));
            return boss(new ImagesScore(scores));
        });

        if (gaussian) {
            arms.put("EM-COV BOSS", (complete, missing) -> {
                SemBicScore score = new SemBicScore(missing, true, MissingDataSpec.emCovariance());
                score.setPenaltyDiscount(PENALTY_DISCOUNT);
                return boss(score);
            });
        }

        return arms;
    }

    // ---------------------------------------------------------------- main

    /**
     * Entry point. Repetitions run concurrently; a table prints per (phase, mechanism, n) condition.
     *
     * @param args Unused.
     * @throws Exception If a condition fails irrecoverably.
     */
    public static void main(String[] args) throws Exception {
        System.out.printf("Witte et al. (2021) replication: Gaussian p=%d/%d edges, mixed p=%d/%d edges "
                          + "(%d%% discrete, %d cats), rate=%.2f, n in %s, reps=%d, alpha=%.2f, penalty=%.1f, "
                          + "MICE m=%d, rep threads=%d%n",
                GAUSS_VARS, GAUSS_EDGES, MIXED_VARS, MIXED_EDGES, (int) PERCENT_DISCRETE, NUM_CATEGORIES,
                MISSING_RATE, Arrays.toString(SAMPLE_SIZES), NUM_REPS, ALPHA, PENALTY_DISCOUNT,
                NUM_IMPUTATIONS, REP_THREADS);
        System.out.println("Mechanisms: MCAR; MAR-group (one member per group missing per row, chosen by the");
        System.out.println("other members' values, MCAR top-up column); MNAR-key (largest key rows delete the");
        System.out.println("key and " + MNAR_SUBORDINATES + " subordinates -- their design, which by construction"
                           + " favors test-wise deletion).");
        System.out.println("MICE arms use predictive mean matching, which their W5 warns against; Gaussian MCAR");
        System.out.println("at n=5000 is the clean test of that warning.");
        System.out.println();

        for (Phase phase : Phase.values()) {
            System.out.println("################ " + phase + " ################");
            System.out.println();

            for (Mechanism mechanism : Mechanism.values()) {
                for (int n : SAMPLE_SIZES) {
                    runCondition(phase, mechanism, n);
                }
            }
        }
    }

    private static void runCondition(Phase phase, Mechanism mechanism, int n) throws Exception {
        System.out.printf("=== %s, mechanism = %s, n = %d ===%n", phase, mechanism, n);

        Map<String, Arm> armTemplate = arms(phase);
        List<String> armNames = new ArrayList<>(armTemplate.keySet());

        Map<String, double[]> totals = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Integer> failures = new LinkedHashMap<>();
        for (String name : armNames) {
            totals.put(name, new double[7]);
            counts.put(name, new int[7]);
            failures.put(name, 0);
        }

        ExecutorService repPool = Executors.newFixedThreadPool(REP_THREADS);

        try {
            List<Future<Map<String, double[]>>> futures = new ArrayList<>();

            for (int rep = 0; rep < NUM_REPS; rep++) {
                final int theRep = rep;
                futures.add(repPool.submit((Callable<Map<String, double[]>>) () ->
                        runRep(phase, mechanism, n, theRep)));
            }

            for (Future<Map<String, double[]>> future : futures) {
                Map<String, double[]> repResult = future.get();

                for (String name : armNames) {
                    double[] stats = repResult.get(name);
                    if (stats == null) {
                        failures.merge(name, 1, Integer::sum);
                        continue;
                    }
                    double[] t = totals.get(name);
                    int[] c = counts.get(name);
                    for (int s = 0; s < 7; s++) {
                        if (Double.isFinite(stats[s])) {
                            t[s] += stats[s];
                            c[s]++;
                        }
                    }
                }
            }
        } finally {
            repPool.shutdownNow();
        }

        System.out.printf("%-22s %8s %8s %8s %8s %8s %6s %8s%n",
                "method", "adjPrec", "adjRec", "arrPrec", "arrRec", "SHD", "#E", "seconds");

        for (String name : armNames) {
            double[] t = totals.get(name);
            int[] c = counts.get(name);
            int fail = failures.get(name);

            System.out.printf("%-22s %8s %8s %8s %8s %8s %6s %8s%s%n",
                    name, mean(t, c, 0, "%.3f"), mean(t, c, 1, "%.3f"), mean(t, c, 2, "%.3f"),
                    mean(t, c, 3, "%.3f"), mean(t, c, 4, "%.1f"), mean(t, c, 5, "%.1f"),
                    mean(t, c, 6, "%.1f"),
                    fail > 0 ? "   (" + fail + " rep(s) failed)" : "");
        }

        System.out.println();
    }

    /**
     * One repetition: seeds this thread's RandomUtil from the condition, generates the graph and data, injects
     * missingness, and runs every arm sequentially. Returns per-arm statistics; an arm that throws is reported
     * as absent and counted as a failure (expected for LISTWISE PC at small n, which is W1's point).
     */
    private static Map<String, double[]> runRep(Phase phase, Mechanism mechanism, int n, int rep) {
        RandomUtil.getInstance().setSeed(100_000L * (phase.ordinal() + 1)
                                        + 10_000L * (mechanism.ordinal() + 1) + 100L * n + rep);

        Graph dag;
        DataSet complete;

        if (phase == Phase.GAUSSIAN) {
            dag = RandomGraph.randomGraph(GAUSS_VARS, 0, GAUSS_EDGES, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            try {
                complete = im.simulateData(n, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            Parameters simParams = new Parameters();
            simParams.set(Params.NUM_MEASURES, MIXED_VARS);
            simParams.set(Params.AVG_DEGREE, 2.0 * MIXED_EDGES / (double) MIXED_VARS);
            simParams.set(Params.SAMPLE_SIZE, n);
            simParams.set(Params.NUM_RUNS, 1);
            simParams.set(Params.PERCENT_DISCRETE, PERCENT_DISCRETE);
            simParams.set(Params.NUM_CATEGORIES, NUM_CATEGORIES);
            simParams.set(Params.DIFFERENT_GRAPHS, false);

            LeeHastieSimulation sim = new LeeHastieSimulation(new RandomForward());
            sim.createData(simParams, true);
            dag = sim.getTrueGraph(0);
            complete = (DataSet) sim.getDataModel(0);
        }

        Graph trueCpdag = GraphTransforms.dagToCpdag(dag);
        DataSet missing = injectMissing(complete, mechanism);

        Map<String, double[]> results = new LinkedHashMap<>();

        for (Map.Entry<String, Arm> entry : arms(phase).entrySet()) {
            long t0 = System.currentTimeMillis();
            Graph est;

            try {
                est = entry.getValue().search(complete, missing);
            } catch (Exception e) {
                if (STREAM_PROGRESS) {
                    System.out.printf("  rep %d  %-22s FAILED: %s%n", rep, entry.getKey(), e.getMessage());
                }
                continue;
            }

            double seconds = (System.currentTimeMillis() - t0) / 1000.0;

            if (STREAM_PROGRESS) {
                System.out.printf("  rep %d  %-22s %6.1fs%n", rep, entry.getKey(), seconds);
            }

            est = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(est, trueCpdag.getNodes());

            double[] stats = new double[7];
            stats[0] = new AdjacencyPrecision().getValue(trueCpdag, est, missing);
            stats[1] = new AdjacencyRecall().getValue(trueCpdag, est, missing);
            stats[2] = new ArrowheadPrecision().getValue(trueCpdag, est, missing);
            stats[3] = new ArrowheadRecall().getValue(trueCpdag, est, missing);
            double shd = new StructuralHammingDistance().getValue(trueCpdag, est, missing);
            stats[4] = shd < 0 ? Double.NaN : shd;
            stats[5] = est.getNumEdges();
            stats[6] = seconds;

            results.put(entry.getKey(), stats);
        }

        return results;
    }

    // ---------------------------------------------------------------- helpers

    private static String mean(double[] sums, int[] counts, int slot, String format) {
        if (counts[slot] == 0) return "n/a";
        return String.format(format, sums[slot] / counts[slot]);
    }

    private static SemBicScore semBic(DataSet data) {
        SemBicScore score = new SemBicScore(data, true);
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    private static DegenerateGaussianScore dgScore(DataSet data) {
        DegenerateGaussianScore score = new DegenerateGaussianScore(data, true, 0.0);
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    private static IndTestDegenerateGaussianLrt dgTest(DataSet data) {
        IndTestDegenerateGaussianLrt test = new IndTestDegenerateGaussianLrt(data);
        test.setAlpha(ALPHA);
        return test;
    }

    private static Graph pc(IndependenceTest test) throws InterruptedException {
        Pc pc = new Pc(test);
        pc.setVerbose(false);
        return pc.search();
    }

    private static Graph boss(Score score) throws InterruptedException {
        edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(score);
        boss.setUseBes(false);
        boss.setNumStarts(1);
        return new PermutationSearch(boss).search();
    }
}
