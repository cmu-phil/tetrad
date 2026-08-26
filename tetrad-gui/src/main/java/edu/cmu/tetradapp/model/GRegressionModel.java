package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.GRegression;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.Tetrad;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.util.WatchedProcess;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Model for the "G-Regression Effects" regression tool: total causal effects estimated by the G-regression
 * estimator of Guo and Perković (2022) from a data set and an MPDAG (a DAG, a CPDAG, or a CPDAG with background
 * knowledge added and Meek-closed), assuming a linear SEM with independent errors and no latent confounding.
 * <p>
 * The model takes treatments X and outcomes Y as sets of nodes and has two modes, mirroring
 * {@link LinearAdjustmentTotalEffectsModel}:
 * <ul>
 *   <li><b>PAIRWISE</b>: for every (x, y) in X &times; Y with x &ne; y, the effect of the point intervention
 *   do(x) on y.</li>
 *   <li><b>JOINT</b>: for every y in Y, the effect of the joint intervention do(X \ {y}) on y, one coefficient per
 *   treatment.</li>
 * </ul>
 * For each case the model records whether the effect is identified from the graph (Theorem 2 of the paper) and, if
 * not, a witness path whose first (undirected) edge is the orientation the graph is missing. Identified effects get
 * the G-regression point estimate and, optionally, bootstrap standard errors. When the data come from a SEM
 * simulation, the true effects are shown for comparison.
 * <p>
 * Unidentified effects are not estimated here; the Linear IDA Check tool gives bounds for those.
 */
public final class GRegressionModel implements SessionModel, GraphSource, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final DataSet dataSet;
    private final Graph graph;
    private final Parameters parameters;
    private final SemIm trueSemIm;

    private final LinkedHashSet<Node> X = new LinkedHashSet<>();
    private final LinkedHashSet<Node> Y = new LinkedHashSet<>();
    private final List<ResultRow> results = new ArrayList<>();

    private String name = "";
    private EffectMode effectMode = EffectMode.PAIRWISE;
    private String treatmentsText = "*";
    private String outcomesText = "*";
    private boolean meekClose = false;
    private int numBootstraps = 100;
    private long bootstrapSeed = -1;

    /**
     * The graph actually used in the last computation (the input graph, Meek-closed if requested), or null if it
     * was rejected.
     */
    private Graph effectiveGraph;

    /**
     * Why the graph was rejected in the last computation, or null if it was accepted.
     */
    private String graphProblem;

    /**
     * Constructs the model.
     *
     * @param dataWrapper the data (the first data set is used; must be continuous)
     * @param graphSource the graph, which should be an MPDAG
     * @param parameters  the parameters
     */
    public GRegressionModel(DataWrapper dataWrapper, GraphSource graphSource, Parameters parameters) {
        this.dataSet = (DataSet) Objects.requireNonNull(dataWrapper).getDataModelList().getFirst();
        this.graph = GraphUtils.replaceNodes(Objects.requireNonNull(graphSource).getGraph(), dataSet.getVariables());
        this.parameters = Objects.requireNonNull(parameters);

        if (dataWrapper instanceof Simulation simulation
            && simulation.getSimulation() instanceof SemSimulation semSimulation
            && !semSimulation.getIms().isEmpty()) {
            this.trueSemIm = semSimulation.getIms().getFirst();
        } else {
            this.trueSemIm = null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Computation.
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Recomputes all rows for the current X, Y, mode, and settings, synchronously. Equivalent to
     * {@code recompute(null)}.
     */
    public void recompute() {
        recompute(null);
    }

    /**
     * Recomputes all rows for the current X, Y, mode, and settings. Input validation (nonempty X and Y,
     * continuous data, the graph being an MPDAG after optional Meek closure) is done synchronously and throws
     * IllegalArgumentException, so the caller can report it. The estimation and bootstrap then run in a watched
     * process when the Tetrad frame is up (directly otherwise), and {@code onDone}, if given, is run on the event
     * thread when they finish, whether or not they succeeded. Note that a watched process does not block, so
     * callers must not read the results before {@code onDone} fires.
     *
     * @param onDone A callback to run on completion, or null.
     */
    public void recompute(Runnable onDone) {
        Graph g = validate();

        if (Tetrad.frame == null) {
            try {
                estimate(g);
            } finally {
                if (onDone != null) onDone.run();
            }
            return;
        }

        new WatchedProcess() {
            @Override
            public void watch() {
                try {
                    estimate(g);
                } finally {
                    if (onDone != null) javax.swing.SwingUtilities.invokeLater(onDone);
                }
            }
        };
    }

    /**
     * Checks the inputs and returns the graph to estimate with (the input graph, Meek-closed if requested).
     * Clears any previous results.
     */
    private Graph validate() {
        results.clear();
        effectiveGraph = null;
        graphProblem = null;

        if (X.isEmpty() || Y.isEmpty()) {
            throw new IllegalArgumentException("Treatments and outcomes sets must not be empty.");
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("G-regression requires continuous data (it is a linear-SEM estimator).");
        }

        Graph g = new EdgeListGraph(graph);

        for (Edge e : g.getEdges()) {
            if (Edges.isBidirectedEdge(e)) {
                graphProblem = "The graph contains a bidirected edge (" + e + "). G-regression assumes causal "
                               + "sufficiency (no latent confounding), so a MAG or PAG is not a valid input.";
                throw new IllegalArgumentException(graphProblem);
            }
        }

        if (meekClose) {
            MeekRules meek = new MeekRules();
            meek.setRevertToUnshieldedColliders(false);
            meek.setVerbose(false);
            meek.orientImplied(g);
        }

        String problem = GRegression.mpdagProblem(g);

        if (problem != null) {
            graphProblem = problem + (meekClose ? "" : " (If the graph is a PDAG whose orientations are correct "
                                                        + "but incomplete, e.g. a CPDAG with edges oriented by hand, "
                                                        + "check \"Close graph under Meek's rules\" and rerun.)");
            throw new IllegalArgumentException(graphProblem);
        }

        // Resolve the selections by name against the graph in use, so they need not be the same Node objects.
        resolve(g, X);
        resolve(g, Y);

        effectiveGraph = g;
        return g;
    }

    /**
     * Identification, point estimates, and bootstrap for every case, on an already validated graph.
     */
    private void estimate(Graph g) {
        // 1. Build the list of cases.
        List<Node> xs = resolve(g, X);
        List<Node> ys = resolve(g, Y);
        List<List<Node>> treatmentLists = new ArrayList<>();
        List<Node> outcomes = new ArrayList<>();

        if (effectMode == EffectMode.PAIRWISE) {
            for (Node x : xs) {
                for (Node y : ys) {
                    if (x.equals(y)) continue;
                    treatmentLists.add(List.of(x));
                    outcomes.add(y);
                }
            }
        } else {
            for (Node y : ys) {
                List<Node> a = new ArrayList<>(xs);
                a.remove(y);
                if (a.isEmpty()) continue;
                treatmentLists.add(a);
                outcomes.add(y);
            }
        }

        // 2. Identification and point estimates from the full sample.
        GRegression full = new GRegression(g, new CovarianceMatrix(dataSet));
        List<double[]> estimates = new ArrayList<>();
        List<List<Node>> witnesses = new ArrayList<>();

        for (int i = 0; i < outcomes.size(); i++) {
            List<Node> a = treatmentLists.get(i);
            Node y = outcomes.get(i);
            List<Node> witness = GRegression.nonIdentificationWitness(g, new LinkedHashSet<>(a), y);
            witnesses.add(witness);
            estimates.add(witness == null ? full.totalEffect(a, y) : null);
        }

        // 3. Bootstrap: resample once, re-estimate every identified case per resample. This is much cheaper than
        // bootstrapping each row separately, since the covariance matrix and Lambda are shared across rows.
        List<double[]> ses = new ArrayList<>(Collections.nCopies(outcomes.size(), null));

        if (numBootstraps > 1) {
            RandomUtil random = RandomUtil.getInstance();
            if (bootstrapSeed >= 0) random.setSeed(bootstrapSeed);

            int n = dataSet.getNumRows();
            double[][] sum = new double[outcomes.size()][];
            double[][] sumSq = new double[outcomes.size()][];

            for (int i = 0; i < outcomes.size(); i++) {
                if (estimates.get(i) != null) {
                    sum[i] = new double[estimates.get(i).length];
                    sumSq[i] = new double[estimates.get(i).length];
                }
            }

            for (int b = 0; b < numBootstraps; b++) {
                if (Thread.currentThread().isInterrupted()) return;

                int[] rows = new int[n];
                for (int r = 0; r < n; r++) rows[r] = random.nextInt(n);
                GRegression boot = new GRegression(g, new CovarianceMatrix(dataSet.subsetRows(rows)));

                for (int i = 0; i < outcomes.size(); i++) {
                    if (estimates.get(i) == null) continue;
                    double[] tau = boot.totalEffect(treatmentLists.get(i), outcomes.get(i));
                    for (int k = 0; k < tau.length; k++) {
                        sum[i][k] += tau[k];
                        sumSq[i][k] += tau[k] * tau[k];
                    }
                }
            }

            for (int i = 0; i < outcomes.size(); i++) {
                if (estimates.get(i) == null) continue;
                double[] se = new double[sum[i].length];
                for (int k = 0; k < se.length; k++) {
                    double mean = sum[i][k] / numBootstraps;
                    double var = (sumSq[i][k] - numBootstraps * mean * mean) / (numBootstraps - 1);
                    se[k] = Math.sqrt(Math.max(var, 0.0));
                }
                ses.set(i, se);
            }
        }

        // 4. Assemble rows.
        for (int i = 0; i < outcomes.size(); i++) {
            List<Node> a = treatmentLists.get(i);
            Node y = outcomes.get(i);
            double[] truth = trueSemIm == null ? null : trueTotalEffect(a, y);
            results.add(new ResultRow(a, y, witnesses.get(i), estimates.get(i), ses.get(i), truth));
        }
    }

    /**
     * The true total effect of the joint intervention do(A) on y in the simulating SEM: with Gamma the true
     * coefficient matrix (Gamma[i][j] the coefficient on i -&gt; j), cut the edges into A by zeroing their columns,
     * then read entries (a, y) of (I - Gamma_cut)^{-1}.
     */
    private double[] trueTotalEffect(List<Node> a, Node y) {
        List<Node> vars = trueSemIm.getVariableNodes();
        int p = vars.size();
        Matrix gamma = trueSemIm.getEdgeCoef().copy();

        int yIdx = indexByName(vars, y);
        int[] aIdx = new int[a.size()];
        for (int k = 0; k < a.size(); k++) aIdx[k] = indexByName(vars, a.get(k));
        if (yIdx < 0) return null;
        for (int idx : aIdx) if (idx < 0) return null;

        for (int idx : aIdx) {
            for (int i = 0; i < p; i++) gamma.set(i, idx, 0.0);
        }

        Matrix total = Matrix.identity(p).minus(gamma).inverse();
        double[] out = new double[a.size()];
        for (int k = 0; k < a.size(); k++) out[k] = total.get(aIdx[k], yIdx);
        return out;
    }

    private static List<Node> resolve(Graph g, Collection<Node> nodes) {
        List<Node> out = new ArrayList<>();
        for (Node node : nodes) {
            Node in = g.getNode(node.getName());
            if (in == null) throw new IllegalArgumentException("Variable " + node.getName() + " is not in the graph.");
            if (!out.contains(in)) out.add(in);
        }
        return out;
    }

    private static int indexByName(List<Node> vars, Node node) {
        for (int i = 0; i < vars.size(); i++) {
            if (vars.get(i).getName().equals(node.getName())) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Accessors.
    // ---------------------------------------------------------------------------------------------------------

    public DataSet getDataSet() {
        return dataSet;
    }

    @Override
    public Graph getGraph() {
        return graph;
    }

    /**
     * The graph used in the last computation (Meek-closed if that was requested), or null if none has been
     * accepted yet.
     */
    public Graph getEffectiveGraph() {
        return effectiveGraph;
    }

    /**
     * Why the graph was rejected in the last computation, or null.
     */
    public String getGraphProblem() {
        return graphProblem;
    }

    /**
     * A one-line description of the graph as it would be used: number of buckets and undirected edges.
     */
    public String describeGraph() {
        Graph g = effectiveGraph != null ? effectiveGraph : graph;
        long undirected = g.getEdges().stream().filter(Edges::isUndirectedEdge).count();
        String problem = GRegression.mpdagProblem(g);
        if (problem != null) return "Not an MPDAG: " + problem;
        int buckets = GRegression.bucketDecomposition(g).size();
        return g.getNumNodes() + " variables, " + undirected + " undirected edges, " + buckets + " buckets";
    }

    public Set<Node> getX() {
        return Collections.unmodifiableSet(X);
    }

    public void setX(Collection<Node> nodes) {
        X.clear();
        X.addAll(Objects.requireNonNull(nodes));
    }

    public Set<Node> getY() {
        return Collections.unmodifiableSet(Y);
    }

    public void setY(Collection<Node> nodes) {
        Y.clear();
        Y.addAll(Objects.requireNonNull(nodes));
    }

    public EffectMode getEffectMode() {
        return effectMode;
    }

    public void setEffectMode(EffectMode mode) {
        this.effectMode = Objects.requireNonNull(mode);
    }

    public boolean isMeekClose() {
        return meekClose;
    }

    public void setMeekClose(boolean meekClose) {
        this.meekClose = meekClose;
    }

    public int getNumBootstraps() {
        return numBootstraps;
    }

    /**
     * Sets the number of bootstrap replications; 0 or 1 disables the bootstrap.
     */
    public void setNumBootstraps(int numBootstraps) {
        if (numBootstraps < 0) throw new IllegalArgumentException("Number of bootstraps must be nonnegative.");
        this.numBootstraps = numBootstraps;
    }

    public long getBootstrapSeed() {
        return bootstrapSeed;
    }

    /**
     * Sets the bootstrap seed; a negative value means "don't reseed".
     */
    public void setBootstrapSeed(long bootstrapSeed) {
        this.bootstrapSeed = bootstrapSeed;
    }

    public List<ResultRow> getResults() {
        return Collections.unmodifiableList(results);
    }

    public ResultRow getResultRow(int rowIndex) {
        return results.get(rowIndex);
    }

    public boolean isTrueSemImAvailable() {
        return trueSemIm != null;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public String getTreatmentsText() {
        return treatmentsText;
    }

    public void setTreatmentsText(String treatmentsText) {
        this.treatmentsText = Objects.requireNonNull(treatmentsText);
    }

    public String getOutcomesText() {
        return outcomesText;
    }

    public void setOutcomesText(String outcomesText) {
        this.outcomesText = Objects.requireNonNull(outcomesText);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public enum EffectMode {
        PAIRWISE,  // do(x) on y for all (x, y) in X x Y
        JOINT      // do(X \ {y}) on y for all y in Y
    }

    // ---------------------------------------------------------------------------------------------------------
    // Result row.
    // ---------------------------------------------------------------------------------------------------------

    /**
     * One (A, Y) case: the treatment list, the outcome, and either a witness path showing why the effect is not
     * identified, or the estimated effect vector (aligned with the treatment list) with optional bootstrap
     * standard errors, plus the true effect if a simulating SEM is available.
     */
    public static final class ResultRow implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final List<Node> treatments;
        public final Node outcome;

        /**
         * Null if identified; otherwise a proper possibly causal path from a treatment to the outcome starting
         * with an undirected edge.
         */
        public final List<Node> witness;

        /**
         * Estimated effects aligned with {@code treatments}; null if not identified.
         */
        public final double[] effect;

        /**
         * Bootstrap standard errors aligned with {@code treatments}; null if not identified or not bootstrapped.
         */
        public final double[] se;

        /**
         * True effects aligned with {@code treatments}; null if no simulating SEM is available.
         */
        public final double[] trueEffect;

        public ResultRow(List<Node> treatments, Node outcome, List<Node> witness, double[] effect, double[] se,
                         double[] trueEffect) {
            this.treatments = List.copyOf(treatments);
            this.outcome = outcome;
            this.witness = witness == null ? null : List.copyOf(witness);
            this.effect = effect;
            this.se = se;
            this.trueEffect = trueEffect;
        }

        public boolean isIdentified() {
            return witness == null;
        }

        public String formatTreatments() {
            return treatments.stream().map(Node::getName).collect(Collectors.joining(", "));
        }

        /**
         * The witness path rendered with the graph's edge marks, or "Yes" if identified.
         */
        public String formatIdentification(Graph g) {
            if (witness == null) return "Yes";
            return "No: " + GRegression.pathString(g, witness);
        }
    }
}
