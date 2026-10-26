package edu.cmu.tetrad.study.missingness;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.missing.MiceLiteImputer;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Replication harness for Strobl, Visweswaran and Spirtes, "Fast Causal Inference with Non-Random Missingness
 * by Test-Wise Deletion" (Int J Data Sci Anal, 2018).
 *
 * <p>Their claims under test:</p>
 * <ul>
 *   <li><b>T1</b>: FCI (and RFCI) with test-wise deletion via their Algorithm 1 beats list-wise deletion under
 *       their latent-driven MNAR at every n, with the largest gains at small n;</li>
 *   <li><b>T2</b>: under that MNAR, imputation (hot deck, kNN, regression, PMM, random forests) performs much
 *       worse than either deletion flavor;</li>
 *   <li><b>T3</b>: under MAR, correctly specified imputation modestly beats deletion, and deletion remains
 *       mid-pack;</li>
 *   <li><b>T4</b> (argued, not simulated by them): when the non-ancestral missingness assumption fails --
 *       a missingness mechanism causally affecting another -- test-wise deletion loses its justification.
 *       The VIOLATION mechanism below realizes this as hierarchical nonresponse (one target set's
 *       missingness indicator causes the other's, the survey skip-pattern case).</li>
 * </ul>
 *
 * <p>Design follows their Section 9.2: random Gaussian DAGs with average degree 2 over {@link #TOTAL_VARS}
 * variables, coefficients Uniform(+/-[0.1, 1]) (Tetrad's SemIm defaults are close), two latent common causes,
 * and for MNAR two latent driver variables L-tilde, each deleting the bottom r-percentile (r ~ U[0.1, 0.5]) of
 * 3-6 observed target variables by its own value; latent columns are then dropped. MAR uses observed drivers
 * instead. Alpha 0.01 with Fisher-Z, as in the paper.</p>
 *
 * <p><b>Metric deviation, on purpose:</b> their MNAR oracle is the SELECTION-BIASED PAG (FCI with the
 * list-wise selection set S_l), which Tetrad does not construct cheaply; estimates here are scored against the
 * unselected true PAG (dagToPag of the latent-marked DAG). That choice tilts the comparison AGAINST the
 * deletion arms (which converge to the selected PAG) and toward imputation (which targets the unselected
 * law), so a test-wise win under this metric strengthens rather than weakens their conclusion; imputation
 * should look relatively better here than in their tables.</p>
 *
 * <p>Arms: ORACLE FCI on complete data; FCI TW-ALG1 (their Algorithm 1: test-wise test, with independence
 * confirmed list-wise); FCI TW-HEURISTIC (test-wise only, their line-1-only variant); FCI LISTWISE; FCI
 * MEAN-IMPUTE; FCI PMM m=1 (their best-performing imputation class); FCI MICE m=5 POOLED-Z (our pooled test,
 * which their paper predates); FCI EM-COV. Set {@link #USE_RFCI} to run the same grid with RFCI.</p>
 *
 * <p>Repetitions run concurrently (RandomUtil is thread-local; each rep seeds its own thread), the MICE
 * chains run concurrently, and each arm is abandoned after {@link #ARM_TIMEOUT_SECONDS}. Run from IntelliJ;
 * nothing outside this file is touched.</p>
 *
 * @author josephramsey
 */
public final class StroblTestwiseFciStudy {

    private StroblTestwiseFciStudy() {
    }

    // ---------------------------------------------------------------- configuration

    /** Total simulated variables, including the ones later marked latent. Their p = 20 observed. */
    private static final int TOTAL_VARS = 24;

    /** Edges (average degree 2, their E(N) = 2). */
    private static final int NUM_EDGES = 24;

    /** Latent common causes marked in every mechanism, their 0-4 range. */
    private static final int NUM_COMMON_LATENTS = 2;

    /** Driver variables (latent for MNAR and VIOLATION, observed for MAR). */
    private static final int NUM_DRIVERS = 2;

    /** Observed targets per driver, their 3-6 range. */
    private static final int MIN_TARGETS = 3;
    private static final int MAX_TARGETS = 6;

    /** Deletion percentile range, their r ~ Uniform[0.1, 0.5]. */
    private static final double MIN_RATE = 0.1;
    private static final double MAX_RATE = 0.5;

    /** Sample sizes; theirs were 100-5000 with the gains largest at small n. */
    private static final int[] SAMPLE_SIZES = {250, 1000, 5000};

    /** Repetitions per condition (theirs: 400; kept within reason). */
    private static final int NUM_REPS = 10;

    /** Fisher-Z alpha, their 0.01. */
    private static final double ALPHA = 0.01;

    /** Imputations for the pooled-z MICE arm. */
    private static final int NUM_IMPUTATIONS = 5;

    /** Run RFCI instead of FCI for every arm. */
    private static final boolean USE_RFCI = false;

    /** Seconds any single arm may take before it is abandoned; 0 disables. */
    private static final int ARM_TIMEOUT_SECONDS = 120;

    /** Base seed for the imputation chains. */
    private static final long MICE_SEED = 8675309L;

    /** Stream one line per arm per rep. */
    private static final boolean STREAM_PROGRESS = true;

    /** Threads for concurrent repetitions. */
    private static final int REP_THREADS =
            Math.max(1, Math.min(NUM_REPS, Runtime.getRuntime().availableProcessors() / 2));

    // ---------------------------------------------------------------- mechanisms

    private enum Mechanism {MNAR_LATENT, MAR_OBSERVED, VIOLATION_HIERARCHICAL}

    /**
     * One generated scenario: the latent-marked true DAG, the observed-columns complete dataset, and the
     * observed-columns dataset with NaN missingness injected.
     */
    private record Scenario(Graph trueDag, DataSet completeObserved, DataSet missingObserved) {
    }

    /**
     * Generates a scenario per their Section 9.2 on the calling thread's seeded RandomUtil. Drivers delete the
     * bottom r-percentile of each of their targets according to the driver's own column; under
     * VIOLATION_HIERARCHICAL, any row missing a first-driver target also loses the second driver's targets
     * (the first mechanism's indicator is a parent of the second's, violating non-ancestral missingness).
     */
    private static Scenario generate(Mechanism mechanism, int n) {
        RandomUtil rand = RandomUtil.getInstance();

        Graph dag = RandomGraph.randomGraph(TOTAL_VARS, 0, NUM_EDGES, 100, 100, 100, false);
        List<Node> nodes = dag.getNodes();

        List<Integer> order = new ArrayList<>();
        for (int j = 0; j < TOTAL_VARS; j++) order.add(j);
        for (int i = order.size() - 1; i > 0; i--) Collections.swap(order, i, rand.nextInt(i + 1));

        // Common-cause latents: prefer variables with at least two children.
        List<Integer> commonLatents = new ArrayList<>();
        for (int j : order) {
            if (commonLatents.size() == NUM_COMMON_LATENTS) break;
            if (dag.getChildren(nodes.get(j)).size() >= 2) commonLatents.add(j);
        }

        // Drivers: distinct from the common latents, preferring connected variables.
        List<Integer> drivers = new ArrayList<>();
        for (int j : order) {
            if (drivers.size() == NUM_DRIVERS) break;
            if (!commonLatents.contains(j) && !dag.getAdjacentNodes(nodes.get(j)).isEmpty()) drivers.add(j);
        }
        for (int j : order) { // fallback if the graph was too sparse
            if (drivers.size() == NUM_DRIVERS) break;
            if (!commonLatents.contains(j) && !drivers.contains(j)) drivers.add(j);
        }

        boolean driversLatent = mechanism != Mechanism.MAR_OBSERVED;

        for (int j : commonLatents) nodes.get(j).setNodeType(NodeType.LATENT);
        if (driversLatent) for (int j : drivers) nodes.get(j).setNodeType(NodeType.LATENT);

        SemIm im = new SemIm(new SemPm(dag));
        DataSet all;
        try {
            all = im.simulateData(n, true); // keep latent columns for the thresholds
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Column index of each node in the simulated dataset.
        Map<String, Integer> col = new LinkedHashMap<>();
        for (int j = 0; j < all.getNumColumns(); j++) col.put(all.getVariable(j).getName(), j);

        // Observed variables: everything not marked latent.
        List<Integer> observedCols = new ArrayList<>();
        for (int j = 0; j < TOTAL_VARS; j++) {
            if (nodes.get(j).getNodeType() != NodeType.LATENT) observedCols.add(col.get(nodes.get(j).getName()));
        }

        // Targets per driver: 3-6 observed non-driver variables, disjoint across drivers.
        List<List<Integer>> targetSets = new ArrayList<>();
        List<Integer> pool = new ArrayList<>(observedCols);
        if (!driversLatent) for (int j : drivers) pool.remove((Integer) col.get(nodes.get(j).getName()));
        for (int i = pool.size() - 1; i > 0; i--) Collections.swap(pool, i, rand.nextInt(i + 1));

        int cursor = 0;
        for (int d = 0; d < NUM_DRIVERS; d++) {
            int k = MIN_TARGETS + rand.nextInt(MAX_TARGETS - MIN_TARGETS + 1);
            List<Integer> targets = new ArrayList<>();
            for (int t = 0; t < k && cursor < pool.size(); t++) targets.add(pool.get(cursor++));
            targetSets.add(targets);
        }

        // Inject missingness on a copy carrying all columns, then drop the latent columns.
        DataSet withMissing = all.copy();
        boolean[][] setMissing = new boolean[NUM_DRIVERS][n];

        for (int d = 0; d < NUM_DRIVERS; d++) {
            int driverCol = col.get(nodes.get(drivers.get(d)).getName());
            double r = MIN_RATE + rand.nextDouble() * (MAX_RATE - MIN_RATE);

            double[] vals = new double[n];
            for (int i = 0; i < n; i++) vals[i] = all.getDouble(i, driverCol);
            double[] sorted = vals.clone();
            java.util.Arrays.sort(sorted);
            double threshold = sorted[Math.max(0, (int) Math.floor(r * n) - 1)];

            for (int i = 0; i < n; i++) {
                if (vals[i] <= threshold) {
                    setMissing[d][i] = true;
                    for (int t : targetSets.get(d)) withMissing.setDouble(i, t, Double.NaN);
                }
            }
        }

        if (mechanism == Mechanism.VIOLATION_HIERARCHICAL) {
            // The first mechanism's indicator is a parent of the second's: rows that lost the first driver's
            // targets also lose the second driver's targets, whatever the second driver's value.
            for (int i = 0; i < n; i++) {
                if (setMissing[0][i]) {
                    for (int t : targetSets.get(1)) withMissing.setDouble(i, t, Double.NaN);
                }
            }
        }

        int[] obs = observedCols.stream().mapToInt(Integer::intValue).toArray();
        int[] allRows = new int[n];
        for (int i = 0; i < n; i++) allRows[i] = i;

        return new Scenario(dag, all.subsetRowsColumns(allRows, obs), withMissing.subsetRowsColumns(allRows, obs));
    }

    // ---------------------------------------------------------------- Algorithm 1 wrapper test

    /**
     * Their Algorithm 1: query the test-wise-deleted Fisher-Z first; on an independence verdict, confirm with
     * the list-wise-deleted Fisher-Z and report the smaller p-value. A dependence verdict from the test-wise
     * test stands un-confirmed (their Lemma 1).
     */
    static final class Algorithm1Test implements IndependenceTest {

        private final IndTestFisherZ testwise;
        private final IndTestFisherZ listwise;
        private volatile boolean verbose = false;

        Algorithm1Test(DataSet missing, double alpha) {
            this.testwise = new IndTestFisherZ(missing, alpha, MissingDataSpec.testwise());

            List<Integer> complete = new ArrayList<>();
            outer:
            for (int i = 0; i < missing.getNumRows(); i++) {
                for (int j = 0; j < missing.getNumColumns(); j++) {
                    if (Double.isNaN(missing.getDouble(i, j))) continue outer;
                }
                complete.add(i);
            }
            if (complete.size() < 20) {
                throw new IllegalStateException("list-wise confirmation impossible: only "
                                                + complete.size() + " complete rows");
            }
            this.listwise = new IndTestFisherZ(missing.subsetRows(complete), alpha);
        }

        @Override
        public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
            IndependenceResult first = this.testwise.checkIndependence(x, y, z);
            if (!first.isIndependent()) return first;

            IndependenceResult second = this.listwise.checkIndependence(
                    this.listwise.getVariable(x.getName()), this.listwise.getVariable(y.getName()),
                    rename(z));

            double p = Math.min(first.getPValue(), second.getPValue());
            boolean independent = p > getAlpha();
            return new IndependenceResult(new IndependenceFact(x, y, z), independent, p, getAlpha() - p);
        }

        private Set<Node> rename(Set<Node> z) {
            Set<Node> out = new java.util.HashSet<>();
            for (Node node : z) out.add(this.listwise.getVariable(node.getName()));
            return out;
        }

        @Override
        public List<Node> getVariables() {
            return this.testwise.getVariables();
        }

        @Override
        public DataSet getData() {
            return (DataSet) this.testwise.getData();
        }

        @Override
        public double getAlpha() {
            return this.testwise.getAlpha();
        }

        @Override
        public void setAlpha(double alpha) {
            this.testwise.setAlpha(alpha);
            this.listwise.setAlpha(alpha);
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
            return "Strobl Algorithm 1 (test-wise with list-wise confirmation), alpha = " + getAlpha();
        }
    }

    // ---------------------------------------------------------------- pooled-z Fisher test over imputations

    /** Rubin-pooled Fisher-Z over m completed datasets; same construction as in WitteReplicationStudy. */
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
            if (df < 1) return new IndependenceResult(new IndependenceFact(x, y, z), false, 0.0, this.alpha);

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
        Graph search(Scenario scenario) throws Exception;
    }

    private static Map<String, Arm> arms() {
        Map<String, Arm> arms = new LinkedHashMap<>();

        arms.put("ORACLE", s -> fci(new IndTestFisherZ(s.completeObserved(), ALPHA)));

        arms.put("TW-ALG1", s -> fci(new Algorithm1Test(s.missingObserved(), ALPHA)));

        arms.put("TW-HEURISTIC", s ->
                fci(new IndTestFisherZ(s.missingObserved(), ALPHA, MissingDataSpec.testwise())));

        arms.put("LISTWISE", s -> {
            DataSet missing = s.missingObserved();
            List<Integer> complete = new ArrayList<>();
            outer:
            for (int i = 0; i < missing.getNumRows(); i++) {
                for (int j = 0; j < missing.getNumColumns(); j++) {
                    if (Double.isNaN(missing.getDouble(i, j))) continue outer;
                }
                complete.add(i);
            }
            if (complete.size() < 20) {
                throw new IllegalStateException("only " + complete.size() + " complete rows");
            }
            return fci(new IndTestFisherZ(missing.subsetRows(complete), ALPHA));
        });

        arms.put("MEAN IMPUTE", s -> fci(new IndTestFisherZ(meanImpute(s.missingObserved()), ALPHA)));

        arms.put("PMM m=1", s -> {
            DataSet imputed = new MiceLiteImputer().impute(s.missingObserved(), 1, MICE_SEED).get(0);
            return fci(new IndTestFisherZ(imputed, ALPHA));
        });

        arms.put("MICE m=5 POOLED-Z", s -> fci(new PooledZFisherTest(miceImpute(s.missingObserved()), ALPHA)));

        arms.put("EM-COV", s ->
                fci(new IndTestFisherZ(s.missingObserved(), ALPHA, MissingDataSpec.emCovariance())));

        return arms;
    }

    // ---------------------------------------------------------------- main

    /**
     * Entry point: one table per (mechanism, n), scored against the unselected true PAG.
     *
     * @param args Unused.
     * @throws Exception If a condition fails irrecoverably.
     */
    public static void main(String[] args) throws Exception {
        System.out.printf("Strobl et al. (2018) replication: %s, p=%d total (%d common latents, %d drivers), "
                          + "edges=%d, n in %s, reps=%d, alpha=%.2f, MICE m=%d, rep threads=%d%n",
                USE_RFCI ? "RFCI" : "FCI", TOTAL_VARS, NUM_COMMON_LATENTS, NUM_DRIVERS, NUM_EDGES,
                java.util.Arrays.toString(SAMPLE_SIZES), NUM_REPS, ALPHA, NUM_IMPUTATIONS, REP_THREADS);
        System.out.println("Mechanisms: MNAR_LATENT (their latent-driven percentile deletion), MAR_OBSERVED");
        System.out.println("(observed drivers), VIOLATION_HIERARCHICAL (one mechanism's indicator causes the");
        System.out.println("other's -- breaks their non-ancestral missingness assumption, their Fig 2 case).");
        System.out.println("Scoring is against the UNSELECTED true PAG, not their selection-biased oracle;");
        System.out.println("this tilts against the deletion arms, so a test-wise win here is conservative.");
        System.out.println();

        for (Mechanism mechanism : Mechanism.values()) {
            for (int n : SAMPLE_SIZES) {
                runCondition(mechanism, n);
            }
        }
    }

    private static void runCondition(Mechanism mechanism, int n) throws Exception {
        System.out.printf("=== mechanism = %s, n = %d ===%n", mechanism, n);

        List<String> armNames = new ArrayList<>(arms().keySet());
        Map<String, double[]> totals = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Integer> failures = new LinkedHashMap<>();
        for (String name : armNames) {
            totals.put(name, new double[6]);
            counts.put(name, new int[6]);
            failures.put(name, 0);
        }

        ExecutorService repPool = Executors.newFixedThreadPool(REP_THREADS);

        try {
            List<Future<Map<String, double[]>>> futures = new ArrayList<>();
            for (int rep = 0; rep < NUM_REPS; rep++) {
                final int theRep = rep;
                futures.add(repPool.submit((Callable<Map<String, double[]>>) () ->
                        runRep(mechanism, n, theRep)));
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
                    for (int s = 0; s < 6; s++) {
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

        System.out.printf("%-20s %8s %8s %8s %8s %6s %8s%n",
                "method", "adjPrec", "adjRec", "arrPrec", "arrRec", "#E", "seconds");

        for (String name : armNames) {
            double[] t = totals.get(name);
            int[] c = counts.get(name);
            int fail = failures.get(name);
            System.out.printf("%-20s %8s %8s %8s %8s %6s %8s%s%n",
                    name, mean(t, c, 0, "%.3f"), mean(t, c, 1, "%.3f"), mean(t, c, 2, "%.3f"),
                    mean(t, c, 3, "%.3f"), mean(t, c, 4, "%.1f"), mean(t, c, 5, "%.1f"),
                    fail > 0 ? "   (" + fail + " rep(s) failed/timed out)" : "");
        }

        System.out.println();
    }

    private static Map<String, double[]> runRep(Mechanism mechanism, int n, int rep) {
        RandomUtil.getInstance().setSeed(70_000L * (mechanism.ordinal() + 1) + 10L * n + rep);

        Scenario scenario = generate(mechanism, n);
        Graph truePag = GraphTransforms.dagToPag(scenario.trueDag(), false);

        Map<String, double[]> results = new LinkedHashMap<>();

        for (Map.Entry<String, Arm> entry : arms().entrySet()) {
            long t0 = System.currentTimeMillis();
            Graph est;

            try {
                est = runWithTimeout(entry.getValue(), scenario);
            } catch (TimeoutException e) {
                if (STREAM_PROGRESS) {
                    System.out.printf("  rep %d  %-20s timed out after %ds%n", rep, entry.getKey(),
                            ARM_TIMEOUT_SECONDS);
                }
                continue;
            } catch (Exception e) {
                if (STREAM_PROGRESS) {
                    System.out.printf("  rep %d  %-20s FAILED: %s%n", rep, entry.getKey(), e.getMessage());
                }
                continue;
            }

            double seconds = (System.currentTimeMillis() - t0) / 1000.0;
            if (STREAM_PROGRESS) {
                System.out.printf("  rep %d  %-20s %6.1fs%n", rep, entry.getKey(), seconds);
            }

            est = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(est, truePag.getNodes());

            double[] stats = new double[6];
            stats[0] = new AdjacencyPrecision().getValue(truePag, est, scenario.missingObserved());
            stats[1] = new AdjacencyRecall().getValue(truePag, est, scenario.missingObserved());
            stats[2] = new ArrowheadPrecision().getValue(truePag, est, scenario.missingObserved());
            stats[3] = new ArrowheadRecall().getValue(truePag, est, scenario.missingObserved());
            stats[4] = est.getNumEdges();
            stats[5] = seconds;

            results.put(entry.getKey(), stats);
        }

        return results;
    }

    private static Graph runWithTimeout(Arm arm, Scenario scenario) throws Exception {
        if (ARM_TIMEOUT_SECONDS <= 0) return arm.search(scenario);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "strobl-arm");
            t.setDaemon(true);
            return t;
        });

        try {
            Future<Graph> future = executor.submit(() -> arm.search(scenario));
            try {
                return future.get(ARM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                throw cause instanceof Exception ex ? ex : new RuntimeException(cause);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String mean(double[] sums, int[] counts, int slot, String format) {
        if (counts[slot] == 0) return "n/a";
        return String.format(format, sums[slot] / counts[slot]);
    }

    private static Graph fci(IndependenceTest test) throws InterruptedException {
        if (USE_RFCI) {
            Rfci rfci = new Rfci(test);
            rfci.setVerbose(false);
            return rfci.search();
        }
        Fci fci = new Fci(test);
        fci.setVerbose(false);
        return fci.search();
    }

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

    private static DataSet meanImpute(DataSet data) {
        DataSet out = data.copy();
        for (int j = 0; j < out.getNumColumns(); j++) {
            double sum = 0;
            int count = 0;
            for (int i = 0; i < out.getNumRows(); i++) {
                double v = out.getDouble(i, j);
                if (!Double.isNaN(v)) {
                    sum += v;
                    count++;
                }
            }
            double mean = count > 0 ? sum / count : 0.0;
            for (int i = 0; i < out.getNumRows(); i++) {
                if (Double.isNaN(out.getDouble(i, j))) out.setDouble(i, j, mean);
            }
        }
        return out;
    }
}
