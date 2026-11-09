package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
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

    /**
     * The data set the effects are estimated from.
     */
    private final DataSet dataSet;
    /**
     * The input graph, with its nodes replaced by the data set's variables.
     */
    private final Graph graph;
    /**
     * The parameters for this model.
     */
    private final Parameters parameters;
    /**
     * The SEM IM that simulated the data, used to show true effects, or null if the data were not simulated from a
     * SEM.
     */
    private final SemIm trueSemIm;

    /**
     * Background knowledge to impose on the graph before estimating, or null. When present, the graph is always
     * Meek-closed after the knowledge is applied.
     */
    private final Knowledge knowledge;

    /**
     * The treatments.
     */
    private final LinkedHashSet<Node> X = new LinkedHashSet<>();
    /**
     * The outcomes.
     */
    private final LinkedHashSet<Node> Y = new LinkedHashSet<>();
    /**
     * The result rows from the last computation.
     */
    private final List<ResultRow> results = new ArrayList<>();

    /**
     * The name of this session model.
     */
    private String name = "";
    /**
     * The effect mode.
     */
    private EffectMode effectMode = EffectMode.PAIRWISE;
    /**
     * The text the user entered to specify the treatments, kept for redisplay.
     */
    private String treatmentsText = "*";
    /**
     * The text the user entered to specify the outcomes, kept for redisplay.
     */
    private String outcomesText = "*";
    /**
     * Whether to close the graph under Meek's rules before estimating.
     */
    private boolean meekClose = false;
    /**
     * The number of bootstrap replications; 0 or 1 disables the bootstrap.
     */
    private int numBootstraps = 100;
    /**
     * The bootstrap seed; a negative value means "don't reseed".
     */
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
        this(dataWrapper, graphSource, (Knowledge) null, parameters);
    }

    /**
     * Constructs the model with background knowledge. The knowledge is used only to orient undirected edges of the
     * given graph (required edges, and forbidden edges or tiers that leave one direction), after which the graph is
     * closed under Meek's rules; adjacencies are never changed. This is the MPDAG construction of Meek (1995): the
     * result represents the DAGs of the graph's class that agree with the knowledge. Knowledge that conflicts with
     * a directed edge of the graph is reported as an error rather than ignored.
     *
     * @param dataWrapper  the data
     * @param graphSource  the graph
     * @param knowledgeBox the knowledge
     * @param parameters   the parameters
     */
    public GRegressionModel(DataWrapper dataWrapper, GraphSource graphSource, KnowledgeBoxModel knowledgeBox,
                            Parameters parameters) {
        this(dataWrapper, graphSource, knowledgeBox == null ? null : knowledgeBox.getKnowledge(), parameters);
    }

    private GRegressionModel(DataWrapper dataWrapper, GraphSource graphSource, Knowledge knowledge,
                             Parameters parameters) {
        this.knowledge = (knowledge == null || knowledge.isEmpty()) ? null : new Knowledge(knowledge);
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

        if (Y.isEmpty()) {
            throw new IllegalArgumentException("The outcomes set must not be empty.");
        }

        if (X.isEmpty() && effectMode != EffectMode.JOINT_PARENTS) {
            throw new IllegalArgumentException("The treatments set must not be empty (it is ignored only in "
                                               + "the do(parents(y)) mode).");
        }

        Graph g = prepareGraph();

        // Resolve the selections by name against the graph in use, so they need not be the same Node objects.
        if (effectMode != EffectMode.JOINT_PARENTS) resolve(g, X);
        resolve(g, Y);

        return g;
    }

    /**
     * Checks the data and graph and returns the graph to estimate with (the input graph, with knowledge applied
     * and/or Meek-closed as configured), independent of any X/Y selection. Sets {@link #getEffectiveGraph()} on
     * success and {@link #getGraphProblem()} on failure.
     */
    private Graph prepareGraph() {
        effectiveGraph = null;
        graphProblem = null;

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

        if (knowledge != null) {
            // Knowledge orients undirected edges; conflicts with edges already directed are detected below.
            MeekRules meek = new MeekRules();
            meek.setKnowledge(knowledge);
            meek.setRevertToUnshieldedColliders(false);
            meek.setVerbose(false);
            meek.orientImplied(g);

            for (Edge e : g.getEdges()) {
                String a = e.getNode1().getName(), b = e.getNode2().getName();

                if (Edges.isDirectedEdge(e)) {
                    String tail = Edges.getDirectedEdgeTail(e).getName();
                    String head = Edges.getDirectedEdgeHead(e).getName();
                    if (knowledge.isForbidden(tail, head) || knowledge.isRequired(head, tail)) {
                        graphProblem = "The knowledge conflicts with the graph: it rules out " + tail + " --> "
                                       + head + ", but the graph has that edge (either directly, or as a "
                                       + "consequence of other knowledge under Meek's rules). Knowledge given to "
                                       + "this tool can only orient undirected edges; to change the structure, "
                                       + "give the knowledge to the search instead.";
                        throw new IllegalArgumentException(graphProblem);
                    }
                } else if (Edges.isUndirectedEdge(e) && (knowledge.isRequired(a, b) || knowledge.isRequired(b, a))) {
                    graphProblem = "The knowledge requires an orientation of " + a + " --- " + b + " that could "
                                   + "not be applied without creating a cycle or a new unshielded collider.";
                    throw new IllegalArgumentException(graphProblem);
                }
            }
        } else if (meekClose) {
            MeekRules meek = new MeekRules();
            meek.setRevertToUnshieldedColliders(false);
            meek.setVerbose(false);
            meek.orientImplied(g);
        }

        String problem = GRegression.mpdagProblem(g);

        if (problem != null) {
            graphProblem = problem + ((meekClose || knowledge != null) ? "" : " (If the graph is a PDAG whose "
                                                        + "orientations are correct but incomplete, e.g. a CPDAG "
                                                        + "with edges oriented by hand, check \"Close graph under "
                                                        + "Meek's rules\" and rerun.)");
            throw new IllegalArgumentException(graphProblem);
        }

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
        } else if (effectMode == EffectMode.JOINT) {
            for (Node y : ys) {
                List<Node> a = new ArrayList<>(xs);
                a.remove(y);
                if (a.isEmpty()) continue;
                treatmentLists.add(a);
                outcomes.add(y);
            }
        } else {
            // JOINT_PARENTS: for each outcome, intervene jointly on its directed parents in the graph in use.
            // In a DAG the resulting coefficients are exactly y's structural equation; with undirected edges
            // present, y's undirected neighbors are not included (the graph does not say they are parents), and
            // the identification check decides whether the effect on the directed parents is estimable.
            for (Node y : ys) {
                List<Node> a = new ArrayList<>(g.getParents(y));
                if (a.isEmpty()) continue;
                a.sort(java.util.Comparator.comparing(Node::getName));
                treatmentLists.add(a);
                outcomes.add(y);
            }

            if (outcomes.isEmpty()) {
                throw new IllegalArgumentException("None of the outcomes has directed parents in the graph.");
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
     * The structural equation of the outcome in the bucket-recursive reparameterization: the coefficients of the
     * regression of {@code y} on the external parents of its bucket, i.e. y's column of Lambda. These are the same
     * for every DAG in the MPDAG's class. When y's bucket is a singleton they are exactly the direct effects
     * (structural coefficients) of y's equation; for a larger bucket they are the bucket-recursive coefficients,
     * which fold within-bucket paths and should not be read as direct effects.
     *
     * @param y the outcome, resolved by name against the graph in use.
     * @return an insertion-ordered map from each external parent of y's bucket to its coefficient; empty if the
     * bucket has no external parents.
     * @throws IllegalArgumentException if the graph (after any requested closure/knowledge) is not a valid MPDAG,
     *                                  the data are not continuous, or y is not in the graph.
     */
    public java.util.LinkedHashMap<Node, Double> structuralEquation(Node y) {
        Graph g = effectiveGraph;

        if (g == null) {
            // No successful run yet; check the graph on the fly so the equation can still be shown.
            g = prepareGraph();
        }

        Node yy = g.getNode(y.getName());
        if (yy == null) throw new IllegalArgumentException("Variable " + y.getName() + " is not in the graph.");

        GRegression est = new GRegression(g, new CovarianceMatrix(dataSet));

        Set<Node> bucket = null;
        for (Set<Node> b : GRegression.bucketDecomposition(g)) {
            if (b.contains(yy)) bucket = b;
        }

        java.util.LinkedHashMap<Node, Double> eq = new java.util.LinkedHashMap<>();
        if (bucket == null) return eq;

        // Lambda is indexed by the covariance variable order, not the graph's node order.
        List<Node> order = est.getVariableOrder();
        Matrix lambda = est.getLambda();
        int yIdx = order.indexOf(yy);

        List<Node> parents = new ArrayList<>(GRegression.externalParents(g, bucket));
        parents.sort(java.util.Comparator.comparing(Node::getName));

        for (Node p : parents) {
            eq.put(p, lambda.get(order.indexOf(p), yIdx));
        }

        return eq;
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

    /**
     * Returns the data set.
     *
     * @return the data set
     */
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
     *
     * @return the effective graph, or null
     */
    public Graph getEffectiveGraph() {
        return effectiveGraph;
    }

    /**
     * Why the graph was rejected in the last computation, or null.
     *
     * @return a description of the problem, or null if the graph was accepted
     */
    public String getGraphProblem() {
        return graphProblem;
    }

    /**
     * A one-line description of the graph as it would be used: number of buckets and undirected edges.
     *
     * @return the description, or a note that the graph is not an MPDAG
     */
    public String describeGraph() {
        Graph g = effectiveGraph != null ? effectiveGraph : graph;
        long undirected = g.getEdges().stream().filter(Edges::isUndirectedEdge).count();
        String problem = GRegression.mpdagProblem(g);
        if (problem != null) return "Not an MPDAG: " + problem;
        int buckets = GRegression.bucketDecomposition(g).size();
        String known = (knowledge != null && effectiveGraph != null) ? " (knowledge applied)" : "";
        return g.getNumNodes() + " variables, " + undirected + " undirected edges, " + buckets + " buckets" + known;
    }

    /**
     * Returns the treatments.
     *
     * @return an unmodifiable view of the treatments
     */
    public Set<Node> getX() {
        return Collections.unmodifiableSet(X);
    }

    /**
     * Sets the treatments, replacing any previous ones.
     *
     * @param nodes the treatments; must not be null
     */
    public void setX(Collection<Node> nodes) {
        X.clear();
        X.addAll(Objects.requireNonNull(nodes));
    }

    /**
     * Returns the outcomes.
     *
     * @return an unmodifiable view of the outcomes
     */
    public Set<Node> getY() {
        return Collections.unmodifiableSet(Y);
    }

    /**
     * Sets the outcomes, replacing any previous ones.
     *
     * @param nodes the outcomes; must not be null
     */
    public void setY(Collection<Node> nodes) {
        Y.clear();
        Y.addAll(Objects.requireNonNull(nodes));
    }

    /**
     * Returns the effect mode.
     *
     * @return the effect mode
     */
    public EffectMode getEffectMode() {
        return effectMode;
    }

    /**
     * Sets the effect mode.
     *
     * @param mode the effect mode; must not be null
     */
    public void setEffectMode(EffectMode mode) {
        this.effectMode = Objects.requireNonNull(mode);
    }

    /**
     * True if background knowledge is attached; the graph is then always Meek-closed after applying it.
     *
     * @return true if background knowledge is attached
     */
    public boolean isKnowledgeAttached() {
        return knowledge != null;
    }

    /**
     * Returns whether the graph is Meek-closed before estimating, which is always the case when knowledge is
     * attached.
     *
     * @return true if the graph will be Meek-closed
     */
    public boolean isMeekClose() {
        return meekClose || knowledge != null;
    }

    /**
     * Sets whether to close the graph under Meek's rules before estimating. Ignored when knowledge is attached, in
     * which case the graph is always Meek-closed.
     *
     * @param meekClose true to Meek-close the graph
     */
    public void setMeekClose(boolean meekClose) {
        this.meekClose = meekClose;
    }

    /**
     * Returns the number of bootstrap replications.
     *
     * @return the number of bootstrap replications
     */
    public int getNumBootstraps() {
        return numBootstraps;
    }

    /**
     * Sets the number of bootstrap replications; 0 or 1 disables the bootstrap.
     *
     * @param numBootstraps the number of bootstrap replications
     * @throws IllegalArgumentException if numBootstraps is negative
     */
    public void setNumBootstraps(int numBootstraps) {
        if (numBootstraps < 0) throw new IllegalArgumentException("Number of bootstraps must be nonnegative.");
        this.numBootstraps = numBootstraps;
    }

    /**
     * Returns the bootstrap seed.
     *
     * @return the bootstrap seed; negative means "don't reseed"
     */
    public long getBootstrapSeed() {
        return bootstrapSeed;
    }

    /**
     * Sets the bootstrap seed; a negative value means "don't reseed".
     *
     * @param bootstrapSeed the bootstrap seed
     */
    public void setBootstrapSeed(long bootstrapSeed) {
        this.bootstrapSeed = bootstrapSeed;
    }

    /**
     * Returns the result rows from the last computation.
     *
     * @return an unmodifiable view of the result rows
     */
    public List<ResultRow> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns the result row at the given index.
     *
     * @param rowIndex the row index
     * @return the result row
     */
    public ResultRow getResultRow(int rowIndex) {
        return results.get(rowIndex);
    }

    /**
     * Returns whether the SEM that simulated the data is available, so true effects can be shown.
     *
     * @return true if a simulating SEM IM is available
     */
    public boolean isTrueSemImAvailable() {
        return trueSemIm != null;
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Returns the text the user entered to specify the treatments.
     *
     * @return the treatments text
     */
    public String getTreatmentsText() {
        return treatmentsText;
    }

    /**
     * Sets the text the user entered to specify the treatments.
     *
     * @param treatmentsText the treatments text; must not be null
     */
    public void setTreatmentsText(String treatmentsText) {
        this.treatmentsText = Objects.requireNonNull(treatmentsText);
    }

    /**
     * Returns the text the user entered to specify the outcomes.
     *
     * @return the outcomes text
     */
    public String getOutcomesText() {
        return outcomesText;
    }

    /**
     * Sets the text the user entered to specify the outcomes.
     *
     * @param outcomesText the outcomes text; must not be null
     */
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

    /**
     * Which interventions the effects are computed for.
     */
    public enum EffectMode {
        /**
         * The effect of do(x) on y, for each (x, y) in X &times; Y with x &ne; y.
         */
        PAIRWISE,       // do(x) on y for all (x, y) in X x Y
        /**
         * The effect of the joint intervention do(X \ {y}) on y, for each y in Y.
         */
        JOINT,          // do(X \ {y}) on y for all y in Y
        /**
         * The effect of the joint intervention on the parents of y, do(Pa(y)), for each y in Y; X is ignored.
         */
        JOINT_PARENTS   // do(Pa(y)) on y for all y in Y; X is ignored
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

        /**
         * The treatments of this case.
         */
        public final List<Node> treatments;
        /**
         * The outcome of this case.
         */
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

        /**
         * Constructs a result row.
         *
         * @param treatments the treatments (copied)
         * @param outcome    the outcome
         * @param witness    the witness path if the effect is not identified, or null if identified (copied)
         * @param effect     the estimated effects aligned with the treatments, or null if not identified
         * @param se         the bootstrap standard errors aligned with the treatments, or null
         * @param trueEffect the true effects aligned with the treatments, or null
         */
        public ResultRow(List<Node> treatments, Node outcome, List<Node> witness, double[] effect, double[] se,
                         double[] trueEffect) {
            this.treatments = List.copyOf(treatments);
            this.outcome = outcome;
            this.witness = witness == null ? null : List.copyOf(witness);
            this.effect = effect;
            this.se = se;
            this.trueEffect = trueEffect;
        }

        /**
         * Returns whether the effect is identified from the graph.
         *
         * @return true if identified (no witness path)
         */
        public boolean isIdentified() {
            return witness == null;
        }

        /**
         * Returns the treatment names as a comma-separated string.
         *
         * @return the formatted treatments
         */
        public String formatTreatments() {
            return treatments.stream().map(Node::getName).collect(Collectors.joining(", "));
        }

        /**
         * The witness path rendered with the graph's edge marks, or "Yes" if identified.
         *
         * @param g the graph whose edge marks are used to render the path
         * @return "Yes" if identified, or "No: " followed by the rendered witness path
         */
        public String formatIdentification(Graph g) {
            if (witness == null) return "Yes";
            return "No: " + GRegression.pathString(g, witness);
        }
    }
}
