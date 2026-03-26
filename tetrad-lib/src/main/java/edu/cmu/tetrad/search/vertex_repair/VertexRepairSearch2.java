package edu.cmu.tetrad.search.vertex_repair;

import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import javax.swing.table.AbstractTableModel;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * The VertexRepairSearch class implements graph search and repair algorithms for correcting edges
 * and vertex relationships in a given graph. It supports evaluation of graph consistency,
 * generation of candidate edits for repair, and application of edits to improve the graph's
 * structural and probabilistic properties.
 */
public class VertexRepairSearch2 implements IGraphSearch {

    /**
     * A comparator for strings that compares them in "natural name order." This order
     * is determined by breaking each input string into a prefix (non-numeric portion)
     * and an optional numeric suffix. Comparison is performed first on the prefix
     * lexicographically and then numerically on the suffix, if present.
     * <p>
     * This comparator ensures that strings like "X", "X1", "X2", "Y" are
     * ordered as "X", "X1", "X2", "Y". Strings with no numeric suffix are ordered
     * before those with numeric suffixes of the same prefix.
     */
    public static final Comparator<String> NATURAL_NAME_COMPARATOR =
            Comparator.comparing(
                    NaturalKey::from
            );

    /**
     * A threshold value used during statistical tests to determine
     * the significance level for accepting or rejecting a hypothesis.
     * Typically interpreted as the alpha probability of a Type I error,
     * indicating the likelihood of incorrectly rejecting a true null hypothesis.
     * This value is fixed as 0.01, representing a 1% significance level.
     */
    static final double alpha = 0.01;

    /**
     * Comparator defining the canonical ordering for scored candidates during
     * vertex repair search. This comparator applies a multi-stage sorting process
     * that prioritizes candidates based on a variety of criteria. The order of
     * precedence is as follows:
     * <p>
     * 1. Candidates that pass guard conditions are prioritized over those that don't.
     * 2. Candidates with more negative delta violations are preferred.
     * 3. Fewer edges in the resulting graph are preferred.
     * 4. Smaller edit sizes are favored (e.g., single-edge edits are preferred over multiple-edge edits).
     * 5. Candidates with finite Node-P values are prioritized, followed by higher log-odds for Node-P.
     * 6. Greater improvements in Model-P over the baseline are preferred, with finite improvements
     * taking precedence over "unknown" improvements.
     * 7. Candidates with higher move-bias values are prioritized.
     * 8. Finite absolute Model-P values are preferred, followed by higher log-odds of Model-P.
     * 9. A tie-breaker is applied for stable comparison if all other criteria are equal.
     * <p>
     * This comparator ensures that candidates are selected in an order that maximizes
     * repair effectiveness and adheres to the specified search heuristics.
     */
    public static final Comparator<ScoredCandidate> CANONICAL_TABLE_ORDER = (a, b) -> {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        // 0) Guards first (true before false)
        if (a.passesGuards() != b.passesGuards()) {
            return a.passesGuards() ? -1 : 1;
        }
        if (!a.passesGuards()) {
            return stableTieBreak(a, b);
        }

        // 1) Δ violations (more negative is better)
        int c = Integer.compare(a.delta(), b.delta()); // ASC
        if (c != 0) return c;

        // 2) Fewer edges preferred
        c = Integer.compare(a.edgesAfter(), b.edgesAfter());
        if (c != 0) return c;

        // 3) Smaller edit size preferred (single-edge before multi-edge)
        c = Integer.compare(editSize(a), editSize(b));
        if (c != 0) return c;

        // 4) Node-P: FINITE first, then log-odds DESC
        c = finiteFirst(a.nodePAfter(), b.nodePAfter());
        if (c != 0) return c;

        double npA = nodeLogOdds(a);
        double npB = nodeLogOdds(b);
        c = -Double.compare(npA, npB);
        if (c != 0) return c;

        // 5) Model-P improvement over baseline (dMp DESC)
        // (Optional but recommended: finite improvement beats "unknown improvement")
        c = finiteFirst(modelDeltaValueOrNaN(a), modelDeltaValueOrNaN(b));
        if (c != 0) return c;

        double dMpA = modelDelta(a);
        double dMpB = modelDelta(b);
        c = -Double.compare(dMpA, dMpB);
        if (c != 0) return c;

        // 6) Move-type bias (your existing heuristic)
        c = -Integer.compare(moveBiasScore(a), moveBiasScore(b)); // DESC
        if (c != 0) return c;

        // 7) Absolute Model-P: FINITE first, then log-odds DESC
        c = finiteFirst(a.modelPAfter(), b.modelPAfter());
        if (c != 0) return c;

        double mpA = modelLogOdds(a);
        double mpB = modelLogOdds(b);
        c = -Double.compare(mpA, mpB);
        if (c != 0) return c;

        // 8) Stable tie-break
        return stableTieBreak(a, b);
    };

    /**
     * Defines the default value for the maximum number of candidates (K) to consider
     * in the second pass of candidate evaluation during the repair search process.
     * This value is used to limit the number of top-scoring candidates based on
     * their model-p values, ensuring computational efficiency and consistency
     * across search iterations.
     */
    private static final int DEFAULT_MODELP_TOP_K = 25;

    /**
     * A static constant that provides access to a specific node in the user preferences tree.
     * This node is used to store and retrieve application-specific configuration settings
     * for the VertexRepairPanel in the Tetrad application.
     * <p>
     * The preferences node is located at "edu/cmu/tetradapp/editor/VertexRepairPanel" within
     * the user preferences hierarchy.
     * <p>
     * This constant leverages the Java Preferences API to serialize and persist user-specific
     * configurations.
     */
    private static final Preferences PREFS = Preferences.userRoot().node("edu/cmu/tetradapp/editor/VertexRepairPanel");

    /**
     * A constant key used for storing or accessing the preferred alpha value in
     * the configuration of a search or repair process. This key corresponds to
     * the alpha level used in statistical tests or other operations within the
     * associated graph search algorithms.
     */
    private static final String PREF_ALPHA = "markovAlpha";

    /**
     * CachedIndependenceQueries object used for managing and caching results of independence tests.
     * This variable is utilized during the search to reduce redundant computations by storing and reusing
     * independence test results, thereby improving the efficiency of the search algorithm.
     */
    private final CachedIndependenceQueries Q;

    /**
     * Represents an independence test used within the {@code VertexRepairSearch} process to
     * evaluate statistical independence between variables in a given dataset.
     * This test forms the basis for determining if certain edges in a graph
     * should be added, removed, or retained during the search and repair process.
     */
    private final IndependenceTest test;

    /**
     * The initial graph used as the starting point for the search process in the
     * {@code VertexRepairSearch} class. This graph serves as the baseline structure
     * upon which modifications and evaluations will be performed during the search.
     * It is immutable to ensure the integrity of the starting point throughout the
     * search procedure.
     * <p>
     * This field is set during the construction of a {@code VertexRepairSearch}
     * instance and cannot be modified afterward.
     */
    private final Graph start;

    /**
     * Represents the type of conditioning set to be utilized in the vertex repair search process.
     * The conditioning set type is a core configuration parameter that influences
     * the statistical analysis performed during the repair of the causal structure.
     * <p>
     * This variable is immutable and is initialized through the constructor of the
     * {@code VertexRepairSearch} class.
     */
    private final ConditioningSetType conditioningSetType;

    /**
     * Flag indicating whether the Anderson-Darling statistical test should be used
     * during the vertex repair search process. This test is often employed to
     * evaluate the uniformity of distributions such as p-values.
     * <p>
     * If set to {@code true}, the algorithm will perform the Anderson-Darling test
     * at relevant steps to guide decisions or validate results. If set to
     * {@code false}, the test will not be applied.
     */
    private final boolean useAndersonDarling = false;

    /**
     * Stores knowledge constraints used in the search process.
     * <p>
     * This variable represents prior knowledge regarding the structure of the
     * graph or constraints that must be satisfied during the vertex repair
     * search. It may include rules about which edges are allowed, forbidden, or
     * required, and is used to guide the search algorithm in modifying or
     * evaluating the graph.
     */
    private Knowledge knowledge;

    /**
     * Represents the currently selected node in the algorithm's context, which is
     * dynamically updated through a dropdown or selection menu.
     */
    private Node x; // selected node (changes via dropdown)

    /**
     * Represents the currently selected graph in the algorithm's context, which is
     * dynamically updated based on the search progress and modifications.
     */
    private Graph workingGraph;

    /**
     * If true, repair will not make any move when the graph already passes
     * the global Markov Checker test (Model-P >= alpha). This prevents the
     * tool from chasing finite-sample noise when starting from a near-correct
     * graph.
     */
    private boolean requireMarkovFailure = true;

    /**
     * Constructs an instance of VertexRepairSearch, initializing the class with the provided parameters and
     * sets up the necessary components to perform a vertex repair search.
     *
     * @param test                The independence test to use for evaluating dependencies among variables in the graph.
     * @param start               The initial graph on which the repair search process will begin.
     * @param knowledge           Domain-specific knowledge about allowable and forbidden edges in the graph.
     * @param conditioningSetType The strategy or type of conditioning set to use during independence testing.
     */
    public VertexRepairSearch2(IndependenceTest test, Graph start, Knowledge knowledge,
                               ConditioningSetType conditioningSetType) {
        this.test = test;
        this.start = GraphUtils.replaceNodes(start, test.getVariables());
        this.knowledge = knowledge;
        this.conditioningSetType = conditioningSetType;
        this.Q = new CachedIndependenceQueries(test);
    }

    private static int stableTieBreak(ScoredCandidate a, ScoredCandidate b) {
        String ka = (a.edit() == null || a.edit().key() == null) ? "" : a.edit().key();
        String kb = (b.edit() == null || b.edit().key() == null) ? "" : b.edit().key();
        int c = ka.compareTo(kb);
        if (c != 0) return c;

        String da = (a.edit() == null || a.edit().description() == null) ? "" : a.edit().description();
        String db = (b.edit() == null || b.edit().description() == null) ? "" : b.edit().description();
        return da.compareTo(db);
    }

    private static int editSize(ScoredCandidate s) {
        try {
            if (s.edit() != null && s.edit().getEdges() != null) {
                return TMath.max(1, s.edit().getEdges().size());
            }
        } catch (Throwable ignored) {
        }
        return 1;
    }

    private static int finiteFirst(double a, double b) {
        boolean fa = Double.isFinite(a);
        boolean fb = Double.isFinite(b);
        if (fa == fb) return 0;
        return fa ? -1 : 1; // finite first
    }

    private static double nodeLogOdds(ScoredCandidate s) {
        double p = s.nodePAfter();
        return Double.isFinite(p) ? alphaLogOdds(p, alpha) : 0.0;
    }

    private static double alphaLogOdds(double p, double alpha) {
        if (!Double.isFinite(p)) return -50.0;
        if (!Double.isFinite(alpha) || alpha <= 0.0 || alpha >= 1.0)
            throw new IllegalArgumentException("alpha must be in (0,1)");

        final double eps = 1e-12;

        double q = TMath.min(1.0 - eps, TMath.max(eps, p));
        double a = TMath.min(1.0 - eps, TMath.max(eps, alpha));

        // log(p/(1-p)) - log(alpha/(1-alpha))
        return (TMath.log(q) - TMath.log(1.0 - q))
                - (TMath.log(a) - TMath.log(1.0 - a));
    }

    private static double modelDeltaValueOrNaN(ScoredCandidate s) {
        if (s == null) return Double.NaN;
        double before = s.modelPBefore();
        double after = s.modelPAfter();
        return (Double.isFinite(before) && Double.isFinite(after)) ? (after - before) : Double.NaN;
    }

    private static double modelDelta(ScoredCandidate s) {
        if (s == null) return 0.0;
        double before = s.modelPBefore();
        double after = s.modelPAfter();
        if (Double.isFinite(before) && Double.isFinite(after)) {
            return after - before;
        }
        return 0.0;
    }

    private static int moveBiasScore(ScoredCandidate s) {
        MoveType mt = moveType(s.edit());
        double dMp = modelDelta(s);

        if (Double.isFinite(dMp) && dMp > 0.0) {
            if (mt == MoveType.REORIENT_SIMPLE) return 2;
            if (mt == MoveType.COLLIDER_FIX) return -1;
        } else if (!Double.isFinite(s.modelPAfter())) {
            if (mt == MoveType.REORIENT_SIMPLE) return 1;
            if (mt == MoveType.COLLIDER_FIX) return -1;
        }

        return 0;
    }

    private static MoveType moveType(CandidateEdit e) {
        if (e == null) return MoveType.OTHER;

        String k = safeLower(e.key());
        String d = safeLower(e.description());
        String s = (k + " " + d).trim();

        // Explicit add/remove first (unambiguous)
        if (containsAny(s, "rem:") || containsAny(s, "remove", "delete")) return MoveType.REMOVE_EDGE;
        if (containsAny(s, "add:") || containsAny(s, "add", "insert")) return MoveType.ADD_EDGE;

        // Collider fixes (usually MULTI:... and description starts with "Orient collider" / "Orient away from collider")
        if (containsAny(s, "orient collider", "orient away from collider")) {
            return MoveType.COLLIDER_FIX;
        }

        // Simple reorientation: typically REP:... and/or "replace" with same endpoints (orientation change)
        // We don’t try to prove it’s “orientation-only” here; we just prioritize these moves over collider moves.
        if (containsAny(s, "rep:") || containsAny(s, "replace", "reorient", "orient", "flip", "reverse", "endpoint")) {
            return MoveType.REORIENT_SIMPLE;
        }

        return MoveType.OTHER;
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (n != null && !n.isEmpty() && s.contains(n)) return true;
        return false;
    }

    private static double modelLogOdds(ScoredCandidate s) {
        double p = s.modelPAfter();
        return Double.isFinite(p) ? alphaLogOdds(p, alpha) : 0.0;
    }

    /**
     * Generates a unique key for an {@code IndependenceFact} object based on its components.
     * If the provided fact or its key components are {@code null}, a random UUID is returned.
     * Otherwise, the key is constructed as a string in the format: "a|b|z1,z2,...,zn",
     * where 'a' and 'b' are the names of the fact's variables (sorted lexicographically),
     * and 'z1, z2, ..., zn' is a sorted, comma-separated list of names for the conditional variables.
     *
     * @param f the {@code IndependenceFact} object used to generate the key. It may contain
     *          two variables (X and Y) and a list of conditional variables (Z).
     * @return a string representing the unique key for the given {@code IndependenceFact},
     *         or a random UUID string if the input is partially or entirely {@code null}.
     */
    private static String factKey(IndependenceFact f) {
        if (f == null || f.getX() == null || f.getY() == null) return UUID.randomUUID().toString();

        String a = f.getX().getName();
        String b = f.getY().getName();
        if (a == null) a = "";
        if (b == null) b = "";

        if (a.compareTo(b) > 0) {
            String t = a;
            a = b;
            b = t;
        }

        List<String> z = new ArrayList<>();
        for (Node n : f.getZ()) {
            if (n != null && n.getName() != null) z.add(n.getName());
        }

        Collections.sort(z);

        return a + "|" + b + "|" + String.join(",", z);
    }

    private static boolean edgeStructurallyEqual(Edge a, Edge b, Node x, Node y) {
        if (a == null || b == null) return false;

        Endpoint aX, aY;
        if (a.getNode1().equals(x) && a.getNode2().equals(y)) {
            aX = a.getEndpoint1();
            aY = a.getEndpoint2();
        } else if (a.getNode1().equals(y) && a.getNode2().equals(x)) {
            aX = a.getEndpoint2();
            aY = a.getEndpoint1();
        } else return false;

        Endpoint bX, bY;
        if (b.getNode1().equals(x) && b.getNode2().equals(y)) {
            bX = b.getEndpoint1();
            bY = b.getEndpoint2();
        } else if (b.getNode1().equals(y) && b.getNode2().equals(x)) {
            bX = b.getEndpoint2();
            bY = b.getEndpoint1();
        } else return false;

        return aX == bX && aY == bY;
    }

    private static List<VertexRepairSearch2.CandidateEdit> dedupCandidateEdits(List<VertexRepairSearch2.CandidateEdit> edits) {
        if (edits == null || edits.isEmpty()) return List.of();
        Map<String, VertexRepairSearch2.CandidateEdit> seen = new LinkedHashMap<>();
        for (VertexRepairSearch2.CandidateEdit ce : edits) {
            if (ce == null) continue;
            String key = ce.key();
            if (key == null) key = UUID.randomUUID().toString();
            seen.putIfAbsent(key, ce);
        }
        return new ArrayList<>(seen.values());
    }

    // Helpers used above (if you don't already have them)
    private static String graphSignature(Graph g) {
        if (g == null) return "null";
        List<String> es = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            String an = (a == null || a.getName() == null) ? "?" : a.getName();
            String bn = (b == null || b.getName() == null) ? "?" : b.getName();
            es.add(an + ":" + e.getEndpoint1() + "--" + e.getEndpoint2() + ":" + bn);
        }
        Collections.sort(es);
        return String.join("|", es);
    }

    private static String fmtP(double p) {
        if (Double.isNaN(p)) return "NaN";
        return String.format("%.4g", p);
    }

    private static void vlog(String fmt, Object... args) {
        TetradLogger.getInstance().log("[VertexAutoRepair] " + String.format(fmt, args));
    }

    private static Endpoint endpointAt(Edge e, Node n) {
        if (e == null || n == null) return null;
        return e.getEndpoint(n);
    }

    // True iff graph contains an edge between the same named endpoints with the same endpoint types.
    private static boolean containsStructuralEdge(Graph g, Edge template) {
        if (g == null || template == null) return false;

        // rebind by names so node identity doesn't matter
        Edge reb = CandidateEdit.rebindEdgeToGraph(g, template);
        if (reb == null) return false;

        Edge inG = g.getEdge(reb.getNode1(), reb.getNode2());
        if (inG == null) return false;

        // Compare endpoints at each named node (order-independent)
        Endpoint a1 = inG.getEndpoint(reb.getNode1());
        Endpoint b1 = inG.getEndpoint(reb.getNode2());
        return a1 == reb.getEndpoint(reb.getNode1()) && b1 == reb.getEndpoint(reb.getNode2());
    }

    private static boolean requiresEdgePresenceCheck(VertexRepairSearch2.CandidateEdit cand) {
        if (cand == null) return false;
        if (cand.isNoOp()) return false;

        String k = cand.key();
        if (k != null && k.startsWith("REM:")) return false;

        List<Edge> intended = cand.getEdges();
        return intended != null && !intended.isEmpty();
    }

    private static boolean allIntendedNewEdgesPresent(Graph g, VertexRepairSearch2.CandidateEdit cand) {
        if (g == null || cand == null) return false;
        List<Edge> intended = cand.getEdges();
        if (intended == null || intended.isEmpty()) return true; // nothing to verify
        for (Edge e : intended) {
            if (!containsStructuralEdge(g, e)) return false;
        }
        return true;
    }

    private static Graph seedDagFromAnyGraph(Graph g) {
        if (g == null) return null;

        // 1) Nodes in a stable order (natural sort)
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName,
                Comparator.nullsLast(NATURAL_NAME_COMPARATOR)));

        if (nodes.isEmpty()) return null;

        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            String name = nodes.get(i).getName();
            if (name != null) idx.put(name, i);
        }

        // 2) Build a DAG with the same adjacencies (ignore endpoints), orienting by order
        Graph dag = new EdgeListGraph(nodes);

        Set<String> seenPairs = new HashSet<>();
        for (Edge e : g.getEdges()) {
            Node a0 = e.getNode1();
            Node b0 = e.getNode2();
            if (a0 == null || b0 == null) continue;

            String an0 = a0.getName();
            String bn0 = b0.getName();
            if (an0 == null || bn0 == null) continue;

            Node a = dag.getNode(an0);
            Node b = dag.getNode(bn0);
            if (a == null || b == null || a.equals(b)) continue;

            String key = (an0.compareTo(bn0) <= 0) ? (an0 + "|" + bn0) : (bn0 + "|" + an0);
            if (!seenPairs.add(key)) continue;

            int ia = idx.getOrDefault(a.getName(), 0);
            int ib = idx.getOrDefault(b.getName(), 0);

            if (ia <= ib) dag.addEdge(new Edge(a, b, Endpoint.TAIL, Endpoint.ARROW));
            else dag.addEdge(new Edge(b, a, Endpoint.TAIL, Endpoint.ARROW));
        }

        return dag.paths().isLegalDag() ? dag : null;
    }

    // Accept if:
    //  (A) violations decrease, OR
    //  (B) violations tie and edges decrease, OR
    //  (C) violations tie and edges tie and Model-P increases by at least MIN_MP_GAIN.
    private static boolean isProgress(int baselineViol,
                                      int afterViol,
                                      int currentEdges,
                                      int afterEdges,
                                      double mpBefore,
                                      double mpAfter) {

        if (afterViol < baselineViol) return true;

        if (afterViol == baselineViol) {
            if (afterEdges < currentEdges) return true;

            // NEW: allow pure "quality" improvement when structure doesn't worsen.
            final double MIN_MP_GAIN = 1e-3; // tune; 0.001 is usually safe
            if (afterEdges == currentEdges
                    && Double.isFinite(mpBefore)
                    && Double.isFinite(mpAfter)
                    && (mpAfter - mpBefore) >= MIN_MP_GAIN) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasDirectedPath(Graph g, Node from, Node to) {
        if (g == null || from == null || to == null) return false;
        try {
            return g.paths().existsDirectedPath(from, to);
        } catch (Throwable t) {
            // If API differs, be conservative: don't pre-prune.
            return false;
        }
    }

    private static double getKolomogorovP(List<Double> pvals) {
        double[] x = pvals.stream().mapToDouble(Double::doubleValue).toArray();
        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        return ks.kolmogorovSmirnovTest(new UniformRealDistribution(0.0, 1.0), x);
    }

    /**
     * Tests a list of p-values against the Anderson-Darling Test.
     *
     * @param pValues the list of p-values to be tested
     * @return the p-value obtained from the Anderson-Darling Test
     */
    public static double getAndersonDarlingP(List<Double> pValues) {
        GeneralAndersonDarlingTest generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        return generalAndersonDarlingTest.getP();
    }

    /**
     * Executes a vertex repair search algorithm using the default parameters.
     * <p>
     * The method starts from the initial graph, runs the search process with a
     * fixed configuration, and returns a repaired graph. It internally delegates
     * to the overloaded {@code search(Graph, RepairGraphType, int, int, int)}
     * method with preset values for the repair graph type and other constraints.
     *
     * @return A {@code Graph} object that represents the outcome of the vertex
     * repair search process.
     * @throws InterruptedException If the thread executing the search process
     *                              is interrupted.
     */
    public Graph search() throws InterruptedException {
        return search(this.start, RepairGraphType.CPDAG, 4, 50, 500);
    }

    /**
     * Performs a search and repair operation on the given graph according to the specified parameters.
     * This method iteratively modifies the input graph by applying edits until a stopping condition is met.
     *
     * @param start           The initial graph to start the search and repair process. Must not be null.
     * @param gt              The type of repair graph to be used for computing candidates and edits.
     * @param maxStepsPerNode The maximum number of steps allowed for processing a single node.
     *                        Must be greater than zero.
     * @param maxSweeps       The maximum number of sweeps over the graph to perform during the search.
     *                        Must be greater than zero.
     * @param maxEdits        The maximum number of edits to apply to the graph. Must be greater than zero.
     * @return The modified graph after applying the search and repair process.
     * Returns null if the input graph is null.
     * @throws IllegalArgumentException If any of the following conditions are true:
     *                                  - maxStepsPerNode is less than or equal to zero.
     *                                  - maxSweeps is less than or equal to zero.
     *                                  - maxEdits is less than or equal to zero.
     */
    public Graph search(Graph start,
                        RepairGraphType gt,
                        int maxStepsPerNode,
                        int maxSweeps,
                        int maxEdits) {

        if (start == null) return null;
        if (maxStepsPerNode <= 0) throw new IllegalArgumentException("maxStepsPerNode must be > 0");
        if (maxSweeps <= 0) throw new IllegalArgumentException("maxSweeps must be > 0");
        if (maxEdits <= 0) throw new IllegalArgumentException("maxEdits must be > 0");

        // Panel-style: canonical node identity against test vars
        this.workingGraph = safeCopy(start);
        this.workingGraph = GraphUtils.replaceNodes(this.workingGraph, test.getVariables());

        int editsApplied = 0;

        int sweep = 0;
        while (!stopRequested() && editsApplied < maxEdits) {
            sweep++;
            if (sweep > maxSweeps) break;

            // Guard: if the graph already passes Markov globally, stop repair.
            // This prevents chasing finite-sample noise in near-correct graphs.
            if (requireMarkovFailure) {
                double gmp = computeGlobalModelP(workingGraph);
                if (Double.isFinite(gmp) && gmp >= alpha) {
                    vlog("Graph passes global Markov (Model-P=%.4f >= alpha=%.4f); halting repair.",
                            gmp, alpha);
                    break;
                }
            }

            final String sweepStartSig = graphSignature(workingGraph);
            int editsThisSweep = 0;

            // Recompute node order EACH sweep: worst nodeP first (panel behavior)
            List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
            Map<String, Double> nodePOrder = new HashMap<>();

            for (Node n : nodes) {
                if (n == null || n.getName() == null) continue;
                double p = nodePValue(workingGraph, n);
                nodePOrder.put(n.getName(), p);
            }

            nodes.sort((a, b) -> {
                if (a == null && b == null) return 0;
                if (a == null) return 1;
                if (b == null) return -1;

                String an = a.getName();
                String bn = b.getName();

                double pa = (an == null) ? Double.NaN : nodePOrder.getOrDefault(an, Double.NaN);
                double pb = (bn == null) ? Double.NaN : nodePOrder.getOrDefault(bn, Double.NaN);

                boolean aNaN = Double.isNaN(pa);
                boolean bNaN = Double.isNaN(pb);

                if (aNaN && bNaN) return NATURAL_NAME_COMPARATOR.compare(an, bn);
                if (aNaN) return 1;
                if (bNaN) return -1;

                int c = Double.compare(pa, pb); // ASC (worst first)
                if (c != 0) return c;

                return NATURAL_NAME_COMPARATOR.compare(an, bn);
            });

            // One sweep over nodes
            for (Node v0 : nodes) {
                if (stopRequested() || editsApplied >= maxEdits) break;
                if (v0 == null || v0.getName() == null) continue;

                Node center = workingGraph.getNode(v0.getName());
                if (center == null) continue;

                Set<String> seenSignatures = new HashSet<>();
                int nodeSteps = 0;

                while (!stopRequested() && editsApplied < maxEdits) {
                    nodeSteps++;
                    if (nodeSteps > maxStepsPerNode) break;

                    center = workingGraph.getNode(center.getName());
                    if (center == null) break;

                    String sig = graphSignature(workingGraph);
                    if (!seenSignatures.add(sig)) break;

                    SearchPack pack = computeCandidatesForNode(workingGraph, center, gt);
                    if (pack == null || pack.scored() == null || pack.scored().isEmpty()) break;

                    List<ScoredCandidate> ranked = new ArrayList<>(pack.scored());
                    ranked.sort(CANONICAL_TABLE_ORDER);

                    ScoredCandidate top = ranked.getFirst();
                    if (top == null || top.edit() == null || top.edit().isNoOp()) break;

                    boolean moved = false;

                    for (ScoredCandidate sc : ranked) {
                        if (sc == null || sc.edit() == null) continue;
                        if (sc.edit().isNoOp()) break;

                        if (sc.passesGuards()) {
                            if (applyCandidateInternal(sc.edit(), gt)) {
                                editsApplied++;
                                editsThisSweep++;
                                moved = true;
                                break;
                            }
                        }
                    }

                    if (!moved) break;
                }
            }

            final String sweepEndSig = graphSignature(workingGraph);

            if (editsThisSweep == 0 || sweepEndSig.equals(sweepStartSig)) {
                break; // fixed point
            }
        }

        return workingGraph;
    }

    /**
     * Retrieves the current working graph.
     *
     * @return the Graph object representing the current working graph.
     */
    public Graph getGraph() {
        return workingGraph;
    }

    /**
     * Compute candidates for a *given* node center (like the panel does for x),
     * but returned as a SearchPack for auto-selection.
     * <p>
     * This intentionally mirrors the panel’s 2-pass approach:
     * - pass 1: After + Node-P for all
     * - pass 2: Model-P for top-K only (so NaNs behave the same as the UI)
     */
    private SearchPack computeCandidatesForNode(Graph g, Node center, RepairGraphType gt) {
        if (g == null || center == null) return null;

        Graph base = safeCopy(g);
        if (stopRequested()) return null;

        if (gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) return null;
        } else if (gt == RepairGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
            if (base == null) return null;
        }

        if (knowledge != null && knowledge.isViolatedBy(base)) {
            return null;
        }

        List<VertexRepairSearch2.CandidateEdit> candidates = enumerateCandidates(base, center, gt);
        candidates = new ArrayList<>(candidates);
        if (candidates.stream().noneMatch(VertexRepairSearch2.CandidateEdit::isNoOp)) {
            candidates.addFirst(VertexRepairSearch2.CandidateEdit.noOp());
        }

        GlobalEvalCache baseCache = buildBaselineCache(base);

        // Baseline violations via locality (consistent with your locality merges)
        GraphEval baseEval = evalGraphLocality(baseCache, base, Set.of(), false);
        int baseline = baseEval.violations();

        // Baseline Model-P (mpBefore constant within this pack)
        double mpBefore = evalGraphOnce(base).modelP();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<VertexRepairSearch2.ScoredCandidate> scored = new ArrayList<>();

        // PASS 1: violationsAfter + nodeP + edges (Model-P deferred)
        for (VertexRepairSearch2.CandidateEdit cand : candidates) {
            if (stopRequested()) return null;

            Graph finalBase = base;
            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(), k -> buildCandidateGraph(finalBase, cand, gt));
            if (g2 == null) continue;

            if (knowledge != null && knowledge.isViolatedBy(g2)) continue;

            boolean useLocality = (gt == RepairGraphType.DAG || gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG);
            Set<String> affected = affectedVertices(base, center, g2);

            int after = useLocality
                    ? evalGraphLocality(baseCache, g2, affected, false).violations()
                    : evalViolationsOnly(g2);

            double nodePAfter = nodePValue(g2, center);
            int edgesAfter = g2.getNumEdges();

            // passesGuards patched later
            scored.add(new VertexRepairSearch2.ScoredCandidate(cand, baseline, after, nodePAfter,
                    Double.NaN, Double.NaN, edgesAfter, true));
        }

        if (stopRequested()) return null;

        // PASS 2: compute Model-P for (top-K rows) UNION (all REORIENT_SIMPLE moves)
        List<VertexRepairSearch2.ScoredCandidate> ranked = new ArrayList<>(scored);
        ranked.sort(CANONICAL_TABLE_ORDER);

        final int topK = TMath.min(DEFAULT_MODELP_TOP_K, ranked.size());
        final LinkedHashSet<String> keysToEval = new LinkedHashSet<>();

        // 2a) top-K (table-surfaced set)
        for (int i = 0; i < topK; i++) {
            VertexRepairSearch2.ScoredCandidate sc = ranked.get(i);
            if (sc == null || sc.edit() == null) continue;
            keysToEval.add(sc.edit().key());
        }

        // 2b) all simple reorientation moves
        for (VertexRepairSearch2.ScoredCandidate sc : scored) {
            if (sc == null || sc.edit() == null) continue;
            if (moveType(sc.edit()) == VertexRepairSearch2.MoveType.REORIENT_SIMPLE) {
                keysToEval.add(sc.edit().key());
            }
        }

        Map<String, Double> mpAfterByKey = new HashMap<>(keysToEval.size() * 2);

        for (String key : keysToEval) {
            if (stopRequested()) return null;
            if (key == null) continue;

            Graph g2 = candGraphByKey.get(key);
            if (g2 == null) continue;

            double mpAfter = evalGraphOnce(g2).modelP();
            mpAfterByKey.put(key, mpAfter);
        }

        {
            List<VertexRepairSearch2.ScoredCandidate> patched = new ArrayList<>(scored.size());
            for (VertexRepairSearch2.ScoredCandidate sc : scored) {
                Double mpAfter = (sc.edit() == null) ? null : mpAfterByKey.get(sc.edit().key());

                patched.add(new VertexRepairSearch2.ScoredCandidate(
                        sc.edit(),
                        sc.violationsBaseline(),
                        sc.violationsAfter(),
                        sc.nodePAfter(),
                        mpBefore,
                        (mpAfter == null ? Double.NaN : mpAfter),
                        sc.edgesAfter(),
                        true // patched next
                ));
            }
            scored = patched;
        }

        // PASS 3: compute passesGuards consistently with the UI path
        {
            List<VertexRepairSearch2.ScoredCandidate> patched2 = new ArrayList<>(scored.size());
            for (VertexRepairSearch2.ScoredCandidate sc : scored) {
                boolean ok = wouldPassGuards(base, sc, gt);

                patched2.add(new VertexRepairSearch2.ScoredCandidate(
                        sc.edit(),
                        sc.violationsBaseline(),
                        sc.violationsAfter(),
                        sc.nodePAfter(),
                        sc.modelPBefore(),
                        sc.modelPAfter(),
                        sc.edgesAfter(),
                        ok
                ));
            }
            scored = patched2;
        }

        return new SearchPack(center.getName(), baseline, scored);
    }

    private List<VertexRepairSearch2.CandidateEdit> enumerateCandidates(Graph g, Node x, RepairGraphType gt) {
        if (g == null || x == null) return List.of(VertexRepairSearch2.CandidateEdit.noOp());

        List<VertexRepairSearch2.CandidateEdit> out = new ArrayList<>();
        out.add(VertexRepairSearch2.CandidateEdit.noOp());

        // 0) Build the add-edge pool
        Set<Node> pool = new LinkedHashSet<>(g.getNodes());
        pool.remove(x);

        // 1) Remove any existing edge incident to x
        for (Edge e : new ArrayList<>(g.getEdges(x))) {
            out.add(VertexRepairSearch2.CandidateEdit.removeEdge(e));
        }

        // 2) Replace existing edge x—y with type-specific variants (single-edge moves)
        for (Edge e : new ArrayList<>(g.getEdges(x))) {
            Node y = e.getDistalNode(x);
            if (y == null) continue;

            for (Edge v : edgeMenuForPair(x, y, gt)) {
                if (edgeStructurallyEqual(e, v, x, y)) continue;
                out.add(VertexRepairSearch2.CandidateEdit.replaceEdge(e, v));
            }
        }

        // 3) Add edges x—y for non-adjacent y in pool
        for (Node y : pool) {
            if (y == null) continue;
            if (g.isAdjacentTo(x, y)) continue;

            for (Edge add : addMenuForPair(x, y, gt)) {
                out.add(VertexRepairSearch2.CandidateEdit.addEdge(add));
            }
        }

        // 4) NEW: Enumerate multi-edge "orient incident edges around x" pattern moves.
        // These are the "consider all feasible orientations into the node" moves.
        if (gt == RepairGraphType.DAG || gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG) {
            out.addAll(enumerateIncidentOrientationPatternMoves(g, x, gt));
        }

        // 5) CPDAG-only: 2-edge collider fixes (unshielded triples)
        if (gt == RepairGraphType.CPDAG) {
            out.addAll(enumerateCpdagColliderPairMoves(g, x));
        }

        return dedupCandidateEdits(out);
    }

    private List<VertexRepairSearch2.CandidateEdit> enumerateIncidentOrientationPatternMoves(Graph g, Node x, RepairGraphType gt) {
        if (g == null || x == null) return List.of();

        // Only consider neighbors currently adjacent to x.
        List<Node> adj = new ArrayList<>(g.getAdjacentNodes(x));
        adj.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        // Build the set of "free" incident edges we’re willing to choose orientations for.
        // We keep already-directed edges fixed, and only enumerate over the ambiguous ones.
        List<Edge> freeEdges = new ArrayList<>();
        List<Edge> fixedDirected = new ArrayList<>();

        for (Node y : adj) {
            if (y == null) continue;
            Edge e = g.getEdge(x, y);
            if (e == null) continue;

            Endpoint ex = endpointAt(e, x);
            Endpoint ey = endpointAt(e, y);

            // DAG: any directed edge is fixed; no undirected should exist (but be defensive).
            if (gt == RepairGraphType.DAG) {
                if (ex == Endpoint.TAIL && ey == Endpoint.ARROW) {
                    fixedDirected.add(e);
                    continue;
                } // x->y
                if (ex == Endpoint.ARROW && ey == Endpoint.TAIL) {
                    fixedDirected.add(e);
                    continue;
                } // y->x
                // If something else appears in a "DAG" graph (shouldn’t), treat as free.
                freeEdges.add(e);
                continue;
            }

            // CPDAG/PDAG: treat undirected (TAIL-TAIL) as free; keep compelled directed edges fixed.
            if (ex == Endpoint.TAIL && ey == Endpoint.TAIL) {
                freeEdges.add(e);
            } else if ((ex == Endpoint.TAIL && ey == Endpoint.ARROW) || (ex == Endpoint.ARROW && ey == Endpoint.TAIL)) {
                fixedDirected.add(e);
            } else {
                // Other endpoint types are not expected in CPDAG/PDAG; ignore them here.
                // (You still have single-edge replacement moves for them.)
            }
        }

        // Nothing to enumerate.
        if (freeEdges.isEmpty()) return List.of();

        // --- Safety caps (tune as you like) ---
        final int MAX_FREE = 12;         // avoid 2^deg explosion
        final int MAX_PARENTS = 6;       // optional: bound indegree into x from free edges
        final int MAX_MOVES = 5000;      // hard cap on moves generated

        if (freeEdges.size() > MAX_FREE) {
            // Too many; skip (you can replace this with beam search later).
            return List.of();
        }

        // Enumerate all subsets S of freeEdges to be oriented INTO x (y->x).
        // Others are oriented OUT of x (x->y).
        List<VertexRepairSearch2.CandidateEdit> out = new ArrayList<>();

        int m = freeEdges.size();
        int total = 1 << m;

        // Stable descriptor elements
        String xName = (x.getName() == null) ? "?" : x.getName();

        for (int mask = 0; mask < total; mask++) {
            if (out.size() >= MAX_MOVES) break;

            // Bound number of parents into x (optional but helpful)
            if (Integer.bitCount(mask) > MAX_PARENTS) continue;

            List<Edge> olds = new ArrayList<>(m);
            List<Edge> news = new ArrayList<>(m);

            List<String> parents = new ArrayList<>();
            List<String> children = new ArrayList<>();

            boolean earlyReject = false;

            for (int i = 0; i < m; i++) {
                Edge old = freeEdges.get(i);
                if (old == null) continue;

                Node y = old.getDistalNode(x);
                if (y == null) continue;

                olds.add(old);

                boolean intoX = ((mask & (1 << i)) != 0);

                // Proposed orientation:
                Edge ne = intoX
                        ? new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)   // y -> x
                        : new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW);  // x -> y

                // (Optional) cheap pre-prune for DAG: avoid obvious directed-cycle creation.
                // buildCandidateGraph will also reject illegal DAGs, but this saves work.
                if (gt == RepairGraphType.DAG) {
                    if (intoX) {
                        // y -> x would create a cycle if x reaches y already.
                        if (hasDirectedPath(g, x, y)) {
                            earlyReject = true;
                            break;
                        }
                    } else {
                        // x -> y would create a cycle if y reaches x already.
                        if (hasDirectedPath(g, y, x)) {
                            earlyReject = true;
                            break;
                        }
                    }
                }

                news.add(ne);

                String yn = (y.getName() == null) ? "?" : y.getName();
                if (intoX) parents.add(yn);
                else children.add(yn);
            }

            if (earlyReject) continue;
            if (news.isEmpty()) continue;

            Collections.sort(parents);
            Collections.sort(children);

            // Label is important: your moveType(...) will treat this as a REORIENT_SIMPLE (contains "orient").
            String label =
                    "Orient incident edges at " + xName +
                            " | Pa={" + String.join(",", parents) + "}" +
                            " | Ch={" + String.join(",", children) + "}";

            out.add(VertexRepairSearch2.CandidateEdit.replaceEdges(label, olds, news));
        }

        return out;
    }

    private List<VertexRepairSearch2.CandidateEdit> enumerateCpdagColliderPairMoves(Graph g, Node x) {
        if (g == null || x == null) return List.of();

        List<VertexRepairSearch2.CandidateEdit> out = new ArrayList<>();

        List<Node> adj = new ArrayList<>(g.getAdjacentNodes(x));
        // (optional) stable order for repeatability
        adj.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        for (int i = 0; i < adj.size(); i++) {
            Node y = adj.get(i);
            if (y == null) continue;

            Edge exy = g.getEdge(x, y);
            if (exy == null) continue;

            for (int j = i + 1; j < adj.size(); j++) {
                Node z = adj.get(j);
                if (z == null) continue;

                if (g.isAdjacentTo(y, z)) continue; // only unshielded triples

                Edge exz = g.getEdge(x, z);
                if (exz == null) continue;

                Endpoint endXy = endpointAt(exy, x);
                Endpoint endXz = endpointAt(exz, x);

                // Case A: noncollider y *-* x *-* z. Propose orient into X: Y->X<-Z.
                if (!(endXy == Endpoint.ARROW && endXz == Endpoint.ARROW)) {
                    Edge yToX = new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW);
                    Edge zToX = new Edge(z, x, Endpoint.TAIL, Endpoint.ARROW);

                    String label = "Orient collider " + y.getName() + "->" + x.getName() + "<-" + z.getName();
                    out.add(VertexRepairSearch2.CandidateEdit.replaceEdges(
                            label,
                            List.of(exy, exz),
                            List.of(yToX, zToX)
                    ));
                }

                // Case B: collider already (two arrows at X). Propose orient away: X->Y and X->Z.
                if (endXy == Endpoint.ARROW && endXz == Endpoint.ARROW) {
                    Edge xToY = new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW);
                    Edge xToZ = new Edge(x, z, Endpoint.TAIL, Endpoint.ARROW);

                    String label = "Orient away from collider " + y.getName() + "<-" + x.getName() + "->" + z.getName();
                    out.add(VertexRepairSearch2.CandidateEdit.replaceEdges(
                            label,
                            List.of(exy, exz),
                            List.of(xToY, xToZ)
                    ));
                }
            }
        }

        return out;
    }

    private List<Edge> edgeMenuForPair(Node x, Node y, RepairGraphType gt) {
        List<Edge> variants = new ArrayList<>();

        switch (gt) {
            case DAG -> {
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case CPDAG, PDAG -> {
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case MAG -> {
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));   // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));   // y->x
                variants.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));  // x<->y
            }
            case PAG -> {
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE)); // o-o
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));  // x o-> y
                variants.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));  // y o-> x
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));    // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));    // y->x
                variants.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));   // x<->y
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y
            }
        }

        return variants;
    }

    private List<Edge> addMenuForPair(Node x, Node y, RepairGraphType gt) {
        List<Edge> adds = new ArrayList<>();

        switch (gt) {
            case DAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case CPDAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y (optional)
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x (optional)
            }
            case PDAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case MAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));   // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));   // y->x
                adds.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));  // x<->y
            }
            case PAG -> {
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE)); // o-o
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));  // x o-> y
                adds.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));  // y o-> x
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));    // x->y (optional)
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));    // y->x (optional)
                adds.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));   // x<->y (optional)
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y (optional)
            }
        }

        return adds;
    }

    private Graph canonicalizeToCpdagOrNull(Graph h) {
        if (h == null) return null;

        try {
            Graph h2 = new EdgeListGraph(h);

            // Case 1: already a legal DAG → project to CPDAG
            if (h2.paths().isLegalDag()) {
                return GraphTransforms.dagToCpdag(h2);
            }

            // Case 2: legal CPDAG/PDAG → pick an extension and project back
            if (h2.paths().isLegalCpdag() || h2.paths().isLegalPdag()) {
                Graph dag = GraphTransforms.dagFromCpdag(h2);
                return GraphTransforms.dagToCpdag(dag);
            }

            // Case 3: arbitrary / illegal PDAG → seed a DAG from the adjacency skeleton
            Graph seed = seedDagFromAnyGraph(h2);
            if (seed == null) return null; // only null if nodes empty or something truly broken
            return GraphTransforms.dagToCpdag(seed);

        } catch (Throwable t) {
            return null;
        }
    }

    private Graph canonicalizeToPagOrNull(Graph h) {
        try {
            Graph h2 = new EdgeListGraph(h);
            Graph mag = GraphTransforms.zhangMagFromPag(h2);

            if (!mag.paths().isLegalMag()) {
                return null;
            }

            return GraphTransforms.magToPag(mag, false);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isLegalGraphType(Graph g, RepairGraphType gt) {
        return switch (gt) {
            case DAG -> g.paths().isLegalDag();
            case CPDAG -> g.paths().isLegalCpdag() || g.paths().isLegalPdag();
            case PDAG -> g.paths().isLegalPdag();
            case MAG -> g.paths().isLegalMag();
            case PAG -> g.paths().isLegalPag();
        };
    }

    private Graph safeCopy(Graph g) {
        if (g == null) return null;
        try {
            return g.copy();
        } catch (Throwable t) {
            return new EdgeListGraph(g);
        }
    }

    /**
     * Sets the Knowledge object for the current instance. If the provided Knowledge
     * object is null, a new Knowledge instance will replace it. If the provided
     * Knowledge object is violated by the current working graph, an
     * IllegalArgumentException is thrown.
     *
     * @param knowledge the Knowledge object to set; if null, a default Knowledge
     *                  instance will be created and used
     * @throws IllegalArgumentException if the provided Knowledge object is violated
     *                                  by the current working graph
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;

        if (workingGraph != null && this.knowledge.isViolatedBy(workingGraph)) {
            throw new IllegalArgumentException("The given Knowledge object is violated by the current graph.");
        }
    }

    /**
     * If set to true, repair halts at the start of each sweep when the
     * working graph already passes the global Markov test (Model-P >= alpha).
     * Recommended when using repair as a post-processor for high-quality
     * algorithms such as BOSS.
     *
     * @param requireMarkovFailure true to enable the guard, false to disable.
     */
    public void setRequireMarkovFailure(boolean requireMarkovFailure) {
        this.requireMarkovFailure = requireMarkovFailure;
    }

    private IndependenceResult check(IndependenceFact f) {
        if (stopRequested()) return null;
        if (f == null || Q == null) return null;

        Set<Node> z = new LinkedHashSet<>(f.getZ());
        try {
            return Q.checkIndependence(f.getX(), f.getY(), z);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private double nodePValue(Graph g, Node vertexInOriginalGraph) {
        if (g == null || vertexInOriginalGraph == null) return Double.NaN;

        Node v = g.getNode(vertexInOriginalGraph.getName());
        if (v == null) return Double.NaN;

        List<IndependenceFact> facts = MarkovCheck.computeImpliedFactsForVertex(g, v, this.conditioningSetType);
        if (facts.isEmpty()) return Double.NaN;

        List<Double> pvals = Q.pValuesForFacts(facts, CachedIndependenceQueries.Dedup.WITHIN_INPUT);
        return getUniformityP(pvals);
    }

    /**
     * Computes the p-value representing the uniformity of a given set of p-values.
     * The method determines the uniformity using either the Anderson-Darling test
     * or the Kolmogorov-Smirnov test, based on the configured setting.
     *
     * @param pvals A list of p-values to be tested for uniformity. Must contain at least two elements.
     *              If the input list is null or has fewer than two elements, Double.NaN is returned.
     * @return The computed uniformity p-value. If the input is invalid, returns Double.NaN.
     */
    private double getUniformityP(List<Double> pvals) {
        if (pvals == null || pvals.size() < 2) return Double.NaN;

        if (useAndersonDarling) {
            return getAndersonDarlingP(pvals);
        } else {
            return getKolomogorovP(pvals);
        }
    }

    private GraphEval evalGraphOnce(Graph g) {
        if (g == null) return new GraphEval(0, Double.NaN, 0);

        List<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, this.conditioningSetType);
        if (facts.isEmpty()) return new GraphEval(0, Double.NaN, 0);

        List<CachedIndependenceQueries.Eval> evals =
                Q.evalAll(facts, CachedIndependenceQueries.Dedup.BY_CACHE_KEY);

        int violations = 0;
        List<Double> pvals = new ArrayList<>(evals.size());

        for (CachedIndependenceQueries.Eval e : evals) {
            if (!e.independent()) violations++;
            double p = e.pValue();
            if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) pvals.add(p);
        }

        double p = getUniformityP(pvals);
        return new GraphEval(violations, p, evals.size());
    }

    private int evalViolationsOnly(Graph g) {
        if (g == null) return 0;

        List<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, this.conditioningSetType);
        if (facts.isEmpty()) return 0;

        List<CachedIndependenceQueries.Eval> evals =
                Q.evalAll(facts, CachedIndependenceQueries.Dedup.BY_CACHE_KEY);

        int violations = 0;
        for (CachedIndependenceQueries.Eval e : evals) {
            if (!e.independent()) violations++;
        }
        return violations;
    }

    private GlobalEvalCache buildBaselineCache(Graph g) {
        if (g == null) return new GlobalEvalCache(Map.of());

        Map<String, VertexContribution> out = new HashMap<>();

        for (Node v : g.getNodes()) {
            if (v == null) continue;
            out.put(v.getName(), evalVertexContribution(g, v));
        }

        return new GlobalEvalCache(out);
    }

    /**
     * Computes the global Markov Checker KS p-value for the given graph.
     * Returns NaN if there are fewer than 2 independence facts to test.
     *
     * @param g the graph to evaluate
     * @return the KS uniformity p-value for the implied independence facts
     */
    private double computeGlobalModelP(Graph g) {
        if (g == null) return Double.NaN;
        GlobalEvalCache cache = buildBaselineCache(g);
        GraphEval eval = evalGraphLocality(cache, g, null, true);
        return eval.modelP();
    }

    private VertexContribution evalVertexContribution(Graph g, Node vInGraph) {
        if (g == null || vInGraph == null) return new VertexContribution(Map.of(), Map.of());

        Node v = g.getNode(vInGraph.getName());
        if (v == null) return new VertexContribution(Map.of(), Map.of());

        List<IndependenceFact> facts = MarkovCheck.computeImpliedFactsForVertex(g, v, this.conditioningSetType);
        if (facts.isEmpty()) return new VertexContribution(Map.of(), Map.of());

        Map<String, Boolean> viol = new HashMap<>();
        Map<String, Double> pByKey = new HashMap<>();

        for (IndependenceFact f : facts) {
            if (f == null) continue;

            String key = factKey(f);

            // de-dup within vertex: first wins
            if (viol.containsKey(key)) continue;

            IndependenceResult r = check(f);
            if (r == null) continue;

            boolean isViolation = !r.isIndependent();
            viol.put(key, isViolation);

            double p = r.getPValue();
            if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                pByKey.put(key, p);
            }
        }

        return new VertexContribution(viol, pByKey);
    }

    private GraphEval evalGraphLocality(GlobalEvalCache baseCache,
                                        Graph candidateGraph,
                                        Set<String> affectedVertexNames,
                                        boolean computeModelP) {
        if (candidateGraph == null) return new GraphEval(0, Double.NaN, 0);

        // 1) Start from violationsBaseline vertex contributions (shallow copy map)
        Map<String, VertexContribution> contrib = new HashMap<>();
        if (baseCache != null && baseCache.contribByVertexName() != null) {
            contrib.putAll(baseCache.contribByVertexName());
        }

        // 2) Overwrite affected vertices with freshly evaluated contributions under candidateGraph
        if (affectedVertexNames != null) {
            for (String name : affectedVertexNames) {
                if (name == null) continue;
                Node v = candidateGraph.getNode(name);
                if (v == null) {
                    contrib.remove(name);
                    continue;
                }
                contrib.put(name, evalVertexContribution(candidateGraph, v));
            }
        }

        // 3) Merge to global dedup by factKey (stable traversal for repeatability)
        Map<String, Boolean> globalViolationByKey = new HashMap<>();
        Map<String, Double> globalPByKey = computeModelP ? new HashMap<>() : null;

        List<String> names = new ArrayList<>(contrib.keySet());
        Collections.sort(names);

        for (String name : names) {
            VertexContribution vc = contrib.get(name);
            if (vc == null) continue;

            for (Map.Entry<String, Boolean> e : vc.violationByKey().entrySet()) {
                String key = e.getKey();
                if (key == null) continue;
                globalViolationByKey.putIfAbsent(key, e.getValue());
            }

            if (computeModelP) {
                for (Map.Entry<String, Double> e : vc.pByKey().entrySet()) {
                    String key = e.getKey();
                    if (key == null) continue;
                    globalPByKey.putIfAbsent(key, e.getValue());
                }
            }
        }

        int violations = 0;
        for (boolean isViol : globalViolationByKey.values()) {
            if (isViol) violations++;
        }

        double modelP = Double.NaN;
        if (computeModelP && globalPByKey.size() >= 2) {
            List<Double> pvals = new ArrayList<>(globalPByKey.values());

            pvals.sort(Double::compareTo);
            modelP = getUniformityP(pvals);
        }

        return new GraphEval(violations, modelP, globalViolationByKey.size());
    }

    private Graph buildCandidateGraph(Graph base, CandidateEdit cand, RepairGraphType gt) {
        if (base == null || cand == null) return null;

        Graph g2 = cand.applyTo(safeCopy(base));
        if (g2 == null) return null;

        if (gt == RepairGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) return null;

            if (!cand.isNoOp() && g2.equals(base)) return null;
        } else if (gt == RepairGraphType.PAG) {
            // keep as-is
        } else if (gt == RepairGraphType.PDAG) {
            // keep as-is
        }

        if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) {
            return null;
        }

        try {
            if (gt != null && !isLegalGraphType(g2, gt)) return null;
        } catch (Exception ignored) {
            return null;
        }

        return g2;
    }

    private Set<String> affectedVertices(Graph base, Node x, Graph candidate) {
        Set<String> affected = new LinkedHashSet<>();
        if (x != null) affected.add(x.getName());

        if (base == null || candidate == null || x == null) return affected;

        Node xb = base.getNode(x.getName());
        Node xc = candidate.getNode(x.getName());
        if (xb == null || xc == null) return affected;

        Set<String> nb = new HashSet<>();
        for (Node n : base.getAdjacentNodes(xb)) if (n != null) nb.add(n.getName());

        Set<String> nc = new HashSet<>();
        for (Node n : candidate.getAdjacentNodes(xc)) if (n != null) nc.add(n.getName());

        for (String name : nb) if (!nc.contains(name)) affected.add(name);
        for (String name : nc) if (!nb.contains(name)) affected.add(name);

        affected.addAll(nc);
        return affected;
    }

    private boolean stopRequested() {
        return Thread.currentThread().isInterrupted();
    }

    /**
     * Internal apply that lets auto-repair avoid per-step history pushes.
     */
    private boolean applyCandidateInternal(CandidateEdit cand, RepairGraphType gt) {
        if (cand == null) return false;
        if (cand.isNoOp()) return false;

        Graph base = safeCopy(workingGraph);

        // Canonicalize base if needed (same as panel)
        if (gt == RepairGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) return false;
        } else if (gt == RepairGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
            if (base == null) return false;
        }

        Graph g2 = cand.applyTo(base);
        if (g2 == null) return false;

        // Canonicalize result if needed
        if (gt == RepairGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) return false;
        } else if (gt == RepairGraphType.PAG) {
            // keep as-is (same as panel comment)
        }

        // Critical: treat “no net change” as no move
        if (g2.equals(base)) return false;

        if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) {
            return false;
        }

        // Commit
        workingGraph = g2;

        // (If you maintain a selected node x in this class, resync it by name like panel.)
        if (x != null && x.getName() != null) {
            Node inGraph = workingGraph.getNode(x.getName());
            x = (inGraph != null) ? inGraph : x;
        }

        return true;
    }

    private boolean wouldPassGuards(Graph base, ScoredCandidate sc, RepairGraphType gt) {
        if (sc == null || sc.edit() == null || sc.edit().isNoOp()) return false;

        int currentEdges = base.getNumEdges();

        Graph cand = buildCandidateGraph(base, sc.edit(), gt);
        if (cand == null) return false;

        // Use the SAME table numbers already computed:
        int baselineViol = sc.violationsBaseline();
        int afterViol = sc.violationsAfter();
        int afterEdges = sc.edgesAfter();

        double mpBefore = sc.modelPBefore();
        double mpAfter = sc.modelPAfter();

        return isProgress(baselineViol, afterViol, currentEdges, afterEdges, mpBefore, mpAfter);
    }

    private enum MoveType {
        REORIENT_SIMPLE,   // single-edge replace/orient/flip (low-risk)
        COLLIDER_FIX,      // multi-edge "Orient collider..." / "Orient away..." (higher-risk)
        REMOVE_EDGE,
        ADD_EDGE,
        OTHER
    }

    /**
     * An enumeration that represents various types of graphical structures used
     * in causal inference and other graph-based methodologies.
     * <p>
     * The following types of graph structures are included:
     * - DAG: Directed Acyclic Graph, a graph with directed edges and no cycles.
     * - CPDAG: Completed Partially Directed Acyclic Graph, a representation of a set of DAGs
     * that are Markov equivalent.
     * - PDAG: Partially Directed Acyclic Graph, a graph that combines directed and undirected edges
     * with the restriction of being acyclic.
     * - MAG: Maximal Ancestral Graph, a graph structure used to represent causal relationships
     * with latent variables.
     * - PAG: Partial Ancestral Graph, a generalization of MAGs that maintains ambiguity where
     * causal directions cannot be fully determined.
     */
    public enum RepairGraphType {

        /**
         * Represents a Directed Acyclic Graph (DAG), a type of graph structure where all edges are directed
         * and there are no cycles. Commonly used in causal inference, dependency modeling, and other
         * computational methodologies requiring non-cyclic and directed relationships.
         */
        DAG,

        /**
         * Represents a Completed Partially Directed Acyclic Graph (CPDAG), which is a graphical structure
         * used in causal inference to encode a set of Markov equivalent Directed Acyclic Graphs (DAGs).
         * A CPDAG contains both directed and undirected edges and provides a compact representation
         * of equivalence classes of DAGs that share the same conditional independence relationships.
         * This structure is particularly useful in causal structure learning when the true causal
         * graph cannot be uniquely identified from data.
         */
        CPDAG,

        /**
         * Represents a Partially Directed Acyclic Graph (PDAG), a graph structure that allows a combination
         * of directed and undirected edges, while maintaining the restriction of being acyclic. PDAGs are
         * often used in causal inference and graphical modeling as intermediate structures in the process
         * of learning or representing causal relationships. They accommodate partial information about
         * causal directions that may not be fully resolved.
         */
        PDAG,

        /**
         * Represents a Maximal Ancestral Graph (MAG), a graphical structure used in causal inference
         * to represent causal relationships in the presence of latent variables and selection bias.
         * MAGs encode ancestral relationships and conditional independencies while allowing for
         * the representation of unmeasured confounders and selection effects. They are widely used
         * in scenarios involving incomplete data or latent structures obscuring direct causal paths.
         */
        MAG,

        /**
         * Represents a Partial Ancestral Graph (PAG), a generalized graphical structure used in causal inference
         * to encode ambiguity in causal directions when complete determination is not possible. PAGs extend
         * Maximal Ancestral Graphs (MAGs) by maintaining indeterminacies in edge orientations, allowing them
         * to represent equivalence classes of MAGs that share the same conditional independencies. This graph
         * type is particularly useful in scenarios where the available data or assumptions are insufficient
         * to uniquely infer causal directions, but some causal relationships can still be established.
         */
        PAG
    }

    private interface CandidateEdit {

        static CandidateEdit noOp() {
            return new VertexRepairSearch2.CandidateEdit() {
                @Override
                public String description() {
                    return "No change";
                }

                @Override
                public Graph applyTo(Graph g) {
                    return (g == null) ? null : new EdgeListGraph(g);
                }

                @Override
                public boolean isNoOp() {
                    return true;
                }

                @Override
                public String key() {
                    return "NO_OP";
                }

                @Override
                public Edge getEdge() {
                    return null;
                }
            };
        }

        static CandidateEdit addEdge(Edge edgeToAdd) {
            Objects.requireNonNull(edgeToAdd, "edgeToAdd");
            return new VertexRepairSearch2.CandidateEdit() {
                @Override
                public String description() {
                    return "Add edge " + edgeToAdd;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

                    Edge rebound = rebindEdgeToGraph(g2, edgeToAdd);
                    if (rebound == null) return g2;

                    g2.addEdge(rebound);
                    return g2;
                }

                @Override
                public String key() {
                    return "ADD:" + stableEdgeKey(edgeToAdd);
                }

                @Override
                public Edge getEdge() {
                    return edgeToAdd;
                }
            };
        }

        private static Edge rebindEdgeToGraph(Graph g, Edge e) {
            if (g == null || e == null) return null;

            Node a0 = e.getNode1();
            Node b0 = e.getNode2();
            if (a0 == null || b0 == null) return null;

            String an = a0.getName();
            String bn = b0.getName();
            if (an == null || bn == null) return null;

            Node a = g.getNode(an);
            Node b = g.getNode(bn);
            if (a == null || b == null) return null;

            // Preserve endpoint-at-node semantics, regardless of node order
            Endpoint ea = e.getEndpoint(a0);
            Endpoint eb = e.getEndpoint(b0);
            return new Edge(a, b, ea, eb);
        }

        static CandidateEdit removeEdge(Edge edgeToRemove) {
            Objects.requireNonNull(edgeToRemove, "edgeToRemove");
            return new VertexRepairSearch2.CandidateEdit() {
                @Override
                public String description() {
                    return "Remove edge " + edgeToRemove;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

                    Edge e = getEdgeByNames(g2, edgeToRemove);
                    if (e != null) g2.removeEdge(e);

                    return g2;
                }

                @Override
                public String key() {
                    return "REM:" + stableEdgeKey(edgeToRemove);
                }

                @Override
                public Edge getEdge() {
                    return edgeToRemove;
                }
            };
        }

        private static Edge getEdgeByNames(Graph g, Edge e) {
            if (g == null || e == null) return null;
            String a = e.getNode1() == null ? null : e.getNode1().getName();
            String b = e.getNode2() == null ? null : e.getNode2().getName();
            if (a == null || b == null) return null;
            Node ga = g.getNode(a);
            Node gb = g.getNode(b);
            if (ga == null || gb == null) return null;
            return g.getEdge(ga, gb);
        }

        static CandidateEdit replaceEdge(Edge oldEdge, Edge newEdge) {
            Objects.requireNonNull(oldEdge, "oldEdge");
            Objects.requireNonNull(newEdge, "newEdge");
            return new VertexRepairSearch2.CandidateEdit() {
                @Override
                public String description() {
                    return "Replace " + oldEdge + " with " + newEdge;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

                    Edge eOld = getEdgeByNames(g2, oldEdge);
                    if (eOld != null) g2.removeEdge(eOld);

                    Edge eNew = rebindEdgeToGraph(g2, newEdge);
                    if (eNew != null) g2.addEdge(eNew);

                    return g2;
                }

                @Override
                public String key() {
                    return "REP:" + stableEdgeKey(oldEdge) + "->" + stableEdgeKey(newEdge);
                }

                @Override
                public Edge getEdge() {
                    return newEdge;
                }
            };
        }

        /**
         * Multi-edge replace: removes every old edge’s pair, then adds every new edge.
         */
        private static CandidateEdit replaceEdges(String label, List<Edge> oldEdges, List<Edge> newEdges) {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(oldEdges, "oldEdges");
            Objects.requireNonNull(newEdges, "newEdges");

            // defensively copy for stable key/description
            List<Edge> olds = List.copyOf(oldEdges);
            List<Edge> news = List.copyOf(newEdges);

            return new VertexRepairSearch2.CandidateEdit() {
                @Override
                public String description() {
                    return label;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

                    // remove by *names* (node identity differs across graph copies)
                    for (Edge oe : olds) {
                        if (oe == null) continue;

                        Node a0 = oe.getNode1();
                        Node b0 = oe.getNode2();
                        if (a0 == null || b0 == null) continue;

                        String an = a0.getName();
                        String bn = b0.getName();
                        if (an == null || bn == null) continue;

                        Node a = g2.getNode(an);
                        Node b = g2.getNode(bn);
                        if (a == null || b == null) continue;

                        Edge e = g2.getEdge(a, b);
                        if (e != null) g2.removeEdge(e);
                    }

                    for (Edge ne : news) {
                        if (ne == null) continue;
                        Edge rebound = rebindEdgeToGraph(g2, ne);
                        if (rebound != null) g2.addEdge(rebound);
                    }

                    return g2;
                }

                @Override
                public String key() {
                    List<String> parts = new ArrayList<>();
                    for (Edge oe : olds) parts.add("O:" + stableEdgeKey(oe));
                    for (Edge ne : news) parts.add("N:" + stableEdgeKey(ne));
                    Collections.sort(parts);
                    return "MULTI:" + label + ":" + String.join("|", parts);
                }

                /** For legacy code paths; return first “new” edge if any. */
                @Override
                public Edge getEdge() {
                    return news.isEmpty() ? null : news.getFirst();
                }

                @Override
                public List<Edge> getEdges() {
                    return news;
                }
            };
        }

        private static String stableEdgeKey(Edge e) {
            if (e == null) return "null";
            Node a = e.getNode1();
            Node b = e.getNode2();
            String an = (a == null || a.getName() == null) ? "?" : a.getName();
            String bn = (b == null || b.getName() == null) ? "?" : b.getName();
            Endpoint ea = e.getEndpoint1();
            Endpoint eb = e.getEndpoint2();
            return an + ":" + bn + ":" + ea + ":" + eb;
        }

        String description();

        Graph applyTo(Graph g);

        default boolean isNoOp() {
            return false;
        }

        default String key() {
            return description();
        }

        Edge getEdge();

        default List<Edge> getEdges() {
            Edge e = getEdge();
            return (e == null) ? List.of() : List.of(e);
        }
    }

    private static final class NaturalKey implements Comparable<NaturalKey> {
        final String prefix;
        final Integer suffix;   // null if no numeric suffix

        private NaturalKey(String prefix, Integer suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        static NaturalKey from(String s) {
            int i = s.length();
            while (i > 0 && Character.isDigit(s.charAt(i - 1))) {
                i--;
            }

            String prefix = s.substring(0, i);
            Integer suffix = (i < s.length())
                    ? Integer.parseInt(s.substring(i))
                    : null;

            return new NaturalKey(prefix, suffix);
        }

        @Override
        public int compareTo(NaturalKey o) {
            int c = this.prefix.compareTo(o.prefix);
            if (c != 0) return c;

            if (this.suffix == null && o.suffix == null) return 0;
            if (this.suffix == null) return -1;  // "X" before "X1"
            if (o.suffix == null) return 1;

            return Integer.compare(this.suffix, o.suffix);
        }
    }

    private record ScoredCandidate(
            CandidateEdit edit,
            int violationsBaseline,
            int violationsAfter,
            double nodePAfter,
            double modelPBefore,
            double modelPAfter,
            int edgesAfter,
            boolean passesGuards
    ) {
        public int delta() {
            return violationsAfter - violationsBaseline;
        }
    }

    private static final class CandidateTableModel extends AbstractTableModel {
        private static final int COL_EDIT = 0;
        private static final int COL_BASE = 1;
        private static final int COL_AFTER = 2;
        private static final int COL_DELTA = 3;
        private static final int COL_NODE_P = 4;
        private static final int COL_MODEL_P = 5;
        private static final int COL_EDGES = 6;
        private static final int COL_APPLY = 7;

        private final String[] cols = {
                "Edit", "Baseline", "After", "Δ", "Node-P", "Model-P", "Edges", "Apply"
        };

        //        private List<VertexRepairSearch.ScoredCandidate> rows = List.of();
        private List<VertexRepairSearch2.ScoredCandidate> rows = new ArrayList<>();

        void set(List<VertexRepairSearch2.ScoredCandidate> rows) {
            this.rows = (rows == null) ? new ArrayList<>() : new ArrayList<>(rows);
            sortByCanonicalOrder();
        }

        void sortByCanonicalOrder() {
            this.rows.sort(CANONICAL_TABLE_ORDER);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            VertexRepairSearch2.ScoredCandidate r = rows.get(rowIndex);
            double alpha = PREFS.getDouble(PREF_ALPHA, 0.01);

            return switch (columnIndex) {
                case COL_EDIT -> r.edit().description();
                case COL_BASE -> r.violationsBaseline();
                case COL_AFTER -> r.violationsAfter();
                case COL_DELTA -> r.delta();
                case COL_NODE_P -> r.nodePAfter();// > alpha ? 1.0 : 0.0;
                case COL_MODEL_P -> r.modelPAfter();// > alpha ? 1.0 : 0.0;
                case COL_EDGES -> r.edgesAfter();
                case COL_APPLY -> r.edit().isNoOp() ? "" : "Accept";
                default -> "";
            };
        }

        public Class<?> getColumnClass(int col) {
            return switch (col) {
                case COL_EDIT -> String.class;
                case COL_BASE -> Integer.class;
                case COL_AFTER -> Integer.class;
                case COL_DELTA -> Integer.class;
                case COL_NODE_P -> Double.class;
                case COL_MODEL_P -> Double.class;
                case COL_EDGES -> Integer.class;
                case COL_APPLY -> Object.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == COL_APPLY && !rows.get(rowIndex).edit().isNoOp();
        }
    }

    private record GraphEval(int violations, double modelP, int nFacts) {
    }

    private record VertexContribution(
            Map<String, Boolean> violationByKey,
            Map<String, Double> pByKey
    ) {
    }

    private record GlobalEvalCache(
            Map<String, VertexContribution> contribByVertexName
    ) {
    }

    /**
     * Lightweight container for per-node auto selection.
     */
    private record SearchPack(String centerName, int baseline, List<VertexRepairSearch2.ScoredCandidate> scored) {

        private Graph seedDagFromAnyGraph(Graph g) {
            if (g == null) return null;

            // 1) Nodes in a stable order
            List<Node> nodes = new ArrayList<>(g.getNodes());
            nodes.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(NATURAL_NAME_COMPARATOR)));

            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < nodes.size(); i++) idx.put(nodes.get(i).getName(), i);

            // 2) Build a DAG that has exactly the same adjacencies (ignore endpoints)
            Graph dag = new EdgeListGraph(nodes);

            Set<String> seenPairs = new HashSet<>();
            for (Edge e : g.getEdges()) {
                Node a0 = e.getNode1();
                Node b0 = e.getNode2();
                if (a0 == null || b0 == null) continue;

                Node a = dag.getNode(a0.getName());
                Node b = dag.getNode(b0.getName());
                if (a == null || b == null || a.equals(b)) continue;

                String key = a.getName().compareTo(b.getName()) < 0 ? a.getName() + "|" + b.getName() : b.getName() + "|" + a.getName();
                if (!seenPairs.add(key)) continue;

                int ia = idx.getOrDefault(a.getName(), 0);
                int ib = idx.getOrDefault(b.getName(), 0);

                // orient forward in the order => guarantees DAG
                if (ia <= ib) dag.addEdge(new Edge(a, b, Endpoint.TAIL, Endpoint.ARROW));
                else dag.addEdge(new Edge(b, a, Endpoint.TAIL, Endpoint.ARROW));
            }

            return dag.paths().isLegalDag() ? dag : null;
        }
    }
}