/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements the PC (Peter–Clark) causal discovery algorithm for learning a
 * causal graph from conditional independence information.
 * <p>
 * The PC algorithm is a constraint-based method that estimates the Markov
 * equivalence class of a causal directed acyclic graph (DAG) under the
 * assumptions of causal sufficiency (no latent confounders), acyclicity,
 * and faithfulness. The output is typically a partially directed acyclic
 * graph (CPDAG) representing the equivalence class of DAGs consistent with
 * the observed conditional independence relations.
 * <p>
 * This implementation follows the standard three-phase structure described
 * in Causation, Prediction, and Search:
 * <ol>
 *   <li><b>Skeleton discovery</b> using Fast Adjacency Search (FAS), with an
 *       optional PC-Stable variant to ensure order independence.</li>
 *   <li><b>Collider orientation</b> on unshielded triples, using one of several
 *       supported strategies:
 *       <ul>
 *         <li>Sepset-based orientation (original PC)</li>
 *         <li>Conservative PC (CPC)</li>
 *         <li>MAX-P orientation, including optional global and depth-stratified
 *             variants</li>
 *       </ul>
 *   </li>
 *   <li><b>Orientation propagation</b> via Meek’s orientation rules, applied
 *       to closure while respecting background knowledge constraints.</li>
 * </ol>
 * <p>
 * The algorithm relies on an external {@link IndependenceTest} to evaluate
 * conditional independence relations and supports the use of background
 * knowledge to forbid or require specific edge orientations. Optional guards
 * are included to prevent the introduction of directed cycles during collider
 * orientation.
 * <p>
 * This class is deterministic given a fixed independence test, variable
 * ordering, and configuration.
 * <p>
 * <b>References:</b>
 * <ul>
 *   <li>Spirtes, P., Glymour, C. N., &amp; Scheines, R. (2000).
 *       <i>Causation, Prediction, and Search</i>. MIT Press.</li>
 *   <li>Colombo, D., &amp; Maathuis, M. H. (2014).
 *       Order-independent constraint-based causal structure learning.
 *       <i>Journal of Machine Learning Research</i>, 15, 3741–3782.</li>
 *   <li>Ramsey, J. D., Spirtes, P., &amp; Zhang, J. (2012).
 *       Adjacency-faithfulness and conservative causal inference.
 *       <i>Proceedings of UAI</i>.</li>
 * </ul>
 */
public class Pc implements IGraphSearch {

    /**
     * The independence test used to evaluate the statistical independence of variables during the structure learning
     * process in the PC algorithm. This test is a critical component of the algorithm as it dictates the conditional
     * independence relationships to be used for constructing the causal graph.
     */
    private IndependenceTest test;
    /**
     * Represents a {@link Knowledge} object that contains constraints and domain-specific information for use in search
     * algorithms within the Pc class. This variable is used to impose restrictions, such as which edges are allowed or
     * disallowed, and to guide the orientation of structures in the graph.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Represents the depth configuration for a search algorithm.
     * <p>
     * The variable controls the maximum depth to be considered in certain algorithmic operations. A value of -1
     * indicates that there is no depth limit.
     */
    private int depth = -1;                  // -1 => no cap
    /**
     * Indicates whether the PC-Stable variant of the PC algorithm is enabled.
     * <p>
     * When `fasStable` is set to `true`, the skeleton learning phase of the PC algorithm adheres to the PC-Stable
     * rules, which guarantee order independence by fixing the separation sets (sepsets) before running the collider
     * orientation phase.
     * <p>
     * This option is typically used when order independence in constraint-based structure learning is desirable. If set
     * to `false`, the standard PC algorithm is used, which does not enforce such order independence.
     */
    private boolean fasStable = true;        // PC-Stable skeleton
    /**
     * Represents the strategy or style used for orienting colliders within the causal discovery process. Determines how
     * unshielded triples are analyzed and how causal edges are oriented based on available statistical or structural
     * information.
     * <p>
     * The possible values of {@code ColliderOrientationStyle} are: - {@code SEPSETS}: Uses separation sets to orient
     * colliders. - {@code CONSERVATIVE}: Employs a conservative approach to avoid premature orientations. -
     * {@code MAX_P}: Uses statistical measures with potentially global or depth-stratified considerations.
     * <p>
     * This variable is initialized to {@code ColliderOrientationStyle.SEPSETS} by default.
     */
    private ColliderOrientationStyle colldierOrientationStyle = ColliderOrientationStyle.SEPSETS;
    /**
     * Determines whether bidirected edges are allowed in the graph. By default, bidirected edges are disallowed, which
     * means the algorithm will not consider such edges during its operations.
     * <p>
     * This variable can be configured using the {@code setAllowBidirected} method, enabling the user to allow or
     * disallow bidirected edges as needed.
     */
    private AllowBidirected allowBidirected = AllowBidirected.DISALLOW;
    /**
     * Indicates whether verbose logging is enabled for this instance. When set to true, additional detailed information
     * about the internal operations and processes is logged, aiding in debugging and analysis. When false, minimal or
     * no logging is performed.
     */
    private boolean verbose = false;
    /**
     * The timeout in milliseconds for search operations. This value determines the maximum duration allowed for an
     * operation to complete before a timeout is triggered. A value less than 0 indicates that no timeout is applied.
     * <p>
     * This configuration can be used to control the runtime of lengthy computations, ensuring that the process does not
     * exceed a specified limit. If no timeout is desired, set the value to a negative number (e.g., -1).
     */
    private long timeoutMs = -1;             // <0 => no timeout
    /**
     * The start time in milliseconds, used to measure or reference elapsed time for specific operations or processes
     * within the class.
     */
    private long startTimeMs = 0;

    // MAX-P options
    /**
     * Indicates whether to apply global, order-independent MAX-P collider orientation.
     * <p>
     * When set to true, the procedure for orienting colliders within the graph operates independently of the order in
     * which nodes are considered. This can provide more robust results in certain scenarios but may alter the
     * algorithm's behavior based on priorities within the graph search process.
     * <p>
     * Default value is false.
     */
    private boolean maxPGlobalOrder = false;     // if true, apply global order
    /**
     * A boolean flag that, when enabled, configures the MAX-P collider orientation process to operate in a
     * depth-stratified manner, meaning it processes by incrementally increasing the size of separating sets (|S|). This
     * setting is applied only when global order-independent MAX-P processing is active.
     */
    private boolean maxPDepthStratified = true;  // when global is on, process by increasing |S|
    /**
     * The `maxPMargin` variable serves as a threshold or margin guard that determines whether the difference in
     * p-values between potential separation sets on opposite sides of a causal structure is significant enough to
     * resolve as definite instead of ambiguous. Specifically, during detailed MAX-P collider orientation, if both
     * candidate sides share similar best p-values within the range defined by `maxPMargin`, the relationship is marked
     * as ambiguous, avoiding a decisive orientation.
     * <p>
     * By default, `maxPMargin` is set to 0.0, meaning this margin guard is turned off and decisions are made solely
     * based on p-value rankings without further constraints.
     */
    private double maxPMargin = 0.0;            // margin guard; 0 => off

    // Optional tie logging for MAX_P
    /**
     * A flag indicating whether to log details about ties in p-values during the MAX-P collider orientation process.
     * Ties occur when multiple separation sets result in the same best p-value for a collider determination.
     * <p>
     * When set to {@code true}, additional information about these ties will be logged, helping users to debug or
     * analyze scenarios where MAX-P decisions are influenced by such ties.
     * <p>
     * This flag is primarily relevant for debugging or detailed analysis of the MAX-P orientation process and has no
     * effect if such information is not required.
     */
    private boolean logMaxPTies = false;
    /**
     * The output stream used for logging operations within the class. By default, this is set to the standard output
     * stream (System.out). This stream can be redirected to a different output stream as needed.
     */
    private java.io.PrintStream logStream = System.out;
    /**
     * The `fas` variable holds an instance of the Fast Adjacency Search (FAS) algorithm used to construct the skeleton
     * of a graphical model.
     * <p>
     * This field is initialized to `null` and can be accessed via the `getFas()` method. It is typically configured and
     * utilized within the context of the causal discovery process.
     */
    private Fas fas = null; // expose via getFas()

    /**
     * Indicates whether the graph replication process is currently active.
     * This variable is used to track the state of graph replication,
     * which may be required in scenarios involving data duplication,
     * synchronization, or fault tolerance mechanisms.
     */
    private boolean replicatingGraph = false;

    // ---------------- NEW: cycle-safety knobs ----------------
    /** If true, do not allow any orientation that creates a directed cycle. */
    private boolean forbidDirectedCycles = true;

    /**
     * If true, Meek closure is applied in a cycle-safe incremental fashion:
     * attempt one implied orientation at a time; rollback if it creates a cycle.
     */
    private boolean meekCycleSafe = true;

    /**
     * Constructs a new instance of the Pc algorithm with the specified independence test.
     *
     * @param test the independence test to be used by the Pc algorithm
     */
    public Pc(IndependenceTest test) {
        this.test = test;
    }

    private static boolean isArrowheadAllowed(Node from, Node to, Knowledge knowledge) {
        if (knowledge.isEmpty()) return true;
        String f = from.getName();
        String t = to.getName();
        return !knowledge.isRequired(t, f)   // disallow f->t if t->f is required
                && !knowledge.isForbidden(f, t); // disallow f->t if f->t is forbidden
    }

    private static void orientEdgesFromKnowledge(Graph g, Knowledge knowledge) {
        if (knowledge == null || knowledge.isEmpty()) return;

        List<Edge> edges = new ArrayList<>(g.getEdges());
        for (Edge e : edges) {
            Node a = e.getNode1();
            Node b = e.getNode2();
            String A = a.getName();
            String B = b.getName();

            // If A->B required OR B->A forbidden, force A->B (if possible)
            boolean forceAToB = knowledge.isRequired(A, B) || knowledge.isForbidden(B, A);
            boolean forceBToA = knowledge.isRequired(B, A) || knowledge.isForbidden(A, B);

            // If both force in opposite directions, it's inconsistent knowledge; skip or log.
            if (forceAToB && forceBToA) {
                // optional: log inconsistency
                continue;
            }

            if (forceAToB) {
                // only if edge exists and is not already oriented the other way
                if (g.isAdjacentTo(a, b) && !g.isParentOf(b, a)) {
                    g.removeEdge(g.getEdge(a, b));
                    g.addDirectedEdge(a, b);
                }
            } else if (forceBToA) {
                if (g.isAdjacentTo(a, b) && !g.isParentOf(a, b)) {
                    g.removeEdge(g.getEdge(a, b));
                    g.addDirectedEdge(b, a);
                }
            }
        }
    }

    // ----- Configuration setters -----

    /**
     * Sets the knowledge object for this instance by creating a new instance based on the provided knowledge.
     *
     * @param knowledge the Knowledge object to be set, representing constraints or prior information about the graph
     *                  structure
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the depth parameter for the instance. The depth controls the maximum number of conditioning variables used
     * in conditional independence tests.
     *
     * @param depth the maximum number of conditioning variables; a value of -1 typically indicates no limit.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets whether the Fast Adjacency Search (FAS) algorithm will use the "stable" modification. The stable version
     * ensures that edge removals during execution do not affect the search process.
     *
     * @param fasStable true to enable the stable FAS modification, false to disable it
     */
    public void setFasStable(boolean fasStable) {
        this.fasStable = fasStable;
    }

    /**
     * Sets the orientation style for handling colliders in the graph. The orientation style determines the method used
     * to decide whether a triple forms a collider. Valid styles are defined in the {@link ColliderOrientationStyle}
     * enum, such as SEPSETS, CONSERVATIVE, or MAX_P.
     *
     * @param rule the {@link ColliderOrientationStyle} that specifies the method used for collider orientation
     */
    public void setColliderOrientationStyle(ColliderOrientationStyle rule) {
        this.colldierOrientationStyle = rule;
    }

    /**
     * Sets the allowance for bidirected edges in the structure being analyzed. This method determines whether
     * bidirected edges are permitted based on the specified option.
     *
     * @param allow an instance of {@link AllowBidirected} that specifies whether bidirected edges are allowed (e.g.,
     *              ALLOW or DISALLOW)
     */
    public void setAllowBidirected(AllowBidirected allow) {
        this.allowBidirected = allow;
    }

    /**
     * Sets whether this instance and its associated test will print verbose output.
     *
     * @param verbose true to enable verbose output; false to disable it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        test.setVerbose(verbose);
    }

    /**
     * Sets the timeout duration for this instance, specifying the maximum time (in milliseconds) the algorithm or
     * operation is permitted to run.
     *
     * @param timeoutMs the timeout duration in milliseconds. A value of 0 or a negative number may indicate no timeout
     *                  (depending on implementation).
     */
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Sets whether ties in the MAX-P conditional independence tests are logged during execution. If enabled, details
     * about ties that impact decision-making in the algorithm are recorded in the logs.
     *
     * @param enabled true to enable logging for MAX-P ties; false to disable it
     */
    public void setLogMaxPTies(boolean enabled) {
        this.logMaxPTies = enabled;
    }

    /**
     * Sets the output stream for logging messages. The specified PrintStream will be used to capture log outputs
     * generated during the execution of this instance.
     *
     * @param out the {@link java.io.PrintStream} object where the log messages will be directed. Passing {@code null}
     *            disables logging.
     */
    public void setLogStream(java.io.PrintStream out) {
        this.logStream = out;
    }

    /**
     * Sets the global order-independent MAX-P collider orientation option. This determines whether the MAX-P algorithm
     * will apply a global ordering that is independent of the sequence of operations.
     *
     * @param enabled true to enable global order-independent orientation, false to disable it
     */
    public void setMaxPGlobalOrder(boolean enabled) {
        this.maxPGlobalOrder = enabled;
    }

    /**
     * Sets whether the MAX-P depth stratification procedure is enabled or disabled. Depth stratification can be used to
     * adjust the way depth constraints are applied during the MAX-P algorithm, based on specific requirements.
     *
     * @param enabled true to enable depth stratification in MAX-P, false to disable it
     */
    public void setMaxPDepthStratified(boolean enabled) {
        this.maxPDepthStratified = enabled;
    }

    /**
     * Configures whether directed cycles are forbidden in the graph.
     *
     * @param enabled a boolean indicating if directed cycles should be forbidden.
     *                If true, directed cycles are not allowed. If false, directed
     *                cycles are permitted.
     */
    // NEW knobs (optional)
    public void setForbidDirectedCycles(boolean enabled) { this.forbidDirectedCycles = enabled; }

    /**
     * Sets the Meek Cycle safety flag.
     *
     * @param enabled a boolean indicating whether the Meek Cycle safety feature
     *                should be enabled (true) or disabled (false)
     */
    public void setMeekCycleSafe(boolean enabled) { this.meekCycleSafe = enabled; }

    // ----- Entry points -----

    /**
     * Performs a search operation based on the test variables associated with the instance. Delegates the search to the
     * method that accepts a list of nodes.
     *
     * @return the resulting graph structure after the search operation is completed
     * @throws InterruptedException if the thread executing the search is interrupted
     */
    @Override
    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    /**
     * Performs a search to generate a graph structure based on the provided list of nodes. Executes a three-step
     * process: skeleton construction using the Fast Adjacency Search (FAS), orientation of unshielded triples as
     * colliders, and application of Meek rules to ensure proper edge orientations.
     *
     * @param nodes the list of nodes to be used as input for the search algorithm
     * @return the resulting graph structure after the search process is completed
     * @throws InterruptedException if the thread executing the search is interrupted
     */
    public Graph search(List<Node> nodes) throws InterruptedException {
        checkVars(nodes);
        this.startTimeMs = System.currentTimeMillis();

        // 1) Skeleton via FAS
        this.fas = new Fas(test);
        fas.setReplicatingGraph(replicatingGraph);
        fas.setKnowledge(knowledge);
        fas.setDepth(depth);
        fas.setStable(fasStable);
        fas.setVerbose(verbose);

        Graph g = fas.search(nodes);

        SepsetMap sepsets = fas.getSepsets();

        // 2) Orient colliders
        orientUnshieldedTriples(g, sepsets);

        // 3) Meek rules to closure (cycle-safe)
        applyMeekRules(g);

        return g;
    }

    public IndependenceTest getTest() { return test; }


    // ------------------------------------------------------------------------------------
    // Triple classification APIs (unshielded only), deterministic order
    // ------------------------------------------------------------------------------------

    /**
     * Sets the independence test for this instance. The provided test must have the same list of variables as the
     * current test to ensure consistency. Otherwise, an exception will be thrown.
     *
     * @param test the new {@link IndependenceTest} to be set. This test must have the same list of variables as the
     *             current test.
     * @throws IllegalArgumentException if the node lists of the current test and the provided test are not equal.
     */
    public void setTest(IndependenceTest test) {
        List<Node> nodes = this.test.getVariables();
        List<Node> _nodes = test.getVariables();
        if (!nodes.equals(_nodes)) {
            throw new IllegalArgumentException(
                    "The nodes of the proposed new test are not equal list-wise to the nodes of the existing test."
            );
        }
        this.test = test;
    }

    /**
     * Retrieves the Fas object.
     *
     * @return the Fas object associated with this instance.
     */
    public Fas getFas() { return fas; }

    /**
     * Retrieves all colliders from the provided graph based on specific criteria. A collider is an unshielded triple
     * where both parent nodes x and y point to a common child z (i.e., x -&gt; z &lt;- y). The method identifies all
     * such triples in the graph and returns them.
     *
     * @param g the graph from which colliders are identified
     * @return a list of triples representing the colliders found in the graph
     */
    public List<Triple> getColliderTriples(Graph g) {
        List<Triple> result = new ArrayList<>();
        for (Triple t : collectUnshieldedTriples(g)) {
            if (g.isParentOf(t.x, t.z) && g.isParentOf(t.y, t.z)) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Sets the value that determines whether the graph is in a replicating state.
     *
     * @param replicatingGraph a boolean indicating whether the graph is replicating.
     */
    public void setReplicatingGraph(boolean replicatingGraph) { this.replicatingGraph = replicatingGraph; }

    // ------------------------------------------------------------------------------------
    // Triple helpers
    // ------------------------------------------------------------------------------------

    private List<Triple> collectUnshieldedTriples(Graph g) {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        List<Triple> triples = new ArrayList<>();
        for (Node z : nodes) {
            List<Node> adj = new ArrayList<>(g.getAdjacentNodes(z));
            adj.sort(Comparator.comparing(Node::getName));
            int m = adj.size();
            for (int i = 0; i < m; i++) {
                Node xi = adj.get(i);
                for (int j = i + 1; j < m; j++) {
                    Node yj = adj.get(j);
                    if (!g.isAdjacentTo(xi, yj)) {
                        Node x = xi, y = yj;
                        if (x.getName().compareTo(y.getName()) > 0) {
                            Node tmp = x; x = y; y = tmp;
                        }
                        triples.add(new Triple(x, z, y));
                    }
                }
            }
        }
        triples.sort(Comparator.comparing((Triple t) -> t.x.getName())
                .thenComparing(t -> t.z.getName())
                .thenComparing(t -> t.y.getName()));
        return triples;
    }

    private static String stringifySet(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return "{" + String.join(",", names) + "}";
    }

    // ------------------------------------------------------------------------------------
    // Collider orientation (cycle-safe)
    // ------------------------------------------------------------------------------------

    private void orientUnshieldedTriples(Graph g, SepsetMap fasSepsets) throws InterruptedException {
        List<Triple> triples = collectUnshieldedTriples(g);

        if (colldierOrientationStyle == ColliderOrientationStyle.MAX_P && maxPGlobalOrder) {
            orientMaxPGlobal(g, triples);
            return;
        }

        List<Triple> ambiguousTriples = new ArrayList<>();

        for (Triple t : triples) {
            checkTimeout();

            // Already collider? skip
            if (g.isParentOf(t.x, t.z) && g.isParentOf(t.y, t.z)) continue;

            ColliderOutcome outcome = switch (colldierOrientationStyle) {
                case SEPSETS -> {
                    Set<Node> s = fasSepsets.get(t.x, t.y);
                    if (s == null) yield ColliderOutcome.NO_SEPSET;
                    yield s.contains(t.z) ? ColliderOutcome.DEPENDENT : ColliderOutcome.INDEPENDENT;
                }
                case CONSERVATIVE -> judgeConservative(t, g);
                case MAX_P -> judgeMaxP(t, g);
            };

            switch (outcome) {
                case INDEPENDENT -> {
                    if (canOrientCollider(g, t.x, t.z, t.y)) {
                        // orientCollider does both x->z and y->z
                        GraphUtils.orientCollider(g, t.x, t.z, t.y);
                        if (verbose) {
                            TetradLogger.getInstance().log(
                                    "Collider oriented: " + t.x.getName() + " -> " + t.z.getName() +
                                            " <- " + t.y.getName());
                        }
                    } else if (verbose) {
                        TetradLogger.getInstance().log(
                                "Skipped collider (cycle/knowledge/bidir guard): " +
                                        t.x.getName() + " - " + t.z.getName() + " - " + t.y.getName());
                    }
                }
                case DEPENDENT, NO_SEPSET -> { /* leave unoriented */ }
                case AMBIGUOUS -> {
                    if (allowBidirected == AllowBidirected.ALLOW) ambiguousTriples.add(t);
                    if (verbose) {
                        TetradLogger.getInstance().log(
                                "Ambiguous triple: " + t.x.getName() + " - " + t.z.getName() + " - " + t.y.getName());
                    }
                }
            }
        }

        // store ambiguous triple marks
        Set<edu.cmu.tetrad.graph.Triple> _ambiguousTriples = new HashSet<>();
        for (Triple t : ambiguousTriples) _ambiguousTriples.add(new edu.cmu.tetrad.graph.Triple(t.x, t.z, t.y));
        g.setAmbiguousTriples(_ambiguousTriples);
    }

    /**
     * Cycle/knowledge/bidirected-safe collider orientation gate.
     *
     * Forbids:
     *  - violating knowledge arrowhead constraints
     *  - creating bidirected (unless allowed)
     *  - creating a directed cycle (if enabled)
     */
    private boolean canOrientCollider(Graph g, Node x, Node z, Node y) {
        if (!g.isAdjacentTo(x, z) || !g.isAdjacentTo(z, y)) return false;

        // knowledge: require arrowheads into z allowed
        if (!isArrowheadAllowed(x, z, knowledge) || !isArrowheadAllowed(y, z, knowledge)) return false;

        // if bidirected not allowed, disallow if z already points to x or y (would create <->)
        if (allowBidirected != AllowBidirected.ALLOW && (g.isParentOf(z, x) || g.isParentOf(z, y))) return false;

        if (forbidDirectedCycles) {
            // If there is already a directed path z -> ... -> x, then x -> z would create a cycle.
            if (g.paths().existsDirectedPath(z, x)) return false;
            if (g.paths().existsDirectedPath(z, y)) return false;
        }

        return true;
    }

    // ------------------------------------------------------------------------------------
    // MAX-P / Conservative logic (same as your code, but left intact)
    // ------------------------------------------------------------------------------------

    private void orientMaxPGlobal(Graph g, List<Triple> triples) throws InterruptedException {
        List<MaxPDecision> winners = new ArrayList<>();
        for (Triple t : triples) {
            checkTimeout();
            MaxPDecision d = decideMaxPDetail(t, g);
            if (d.outcome == ColliderOutcome.INDEPENDENT) winners.add(d);
        }

        if (maxPDepthStratified) {
            Map<Integer, List<MaxPDecision>> buckets = new TreeMap<>();
            for (MaxPDecision d : winners) buckets.computeIfAbsent(d.bestS.size(), k -> new ArrayList<>()).add(d);

            for (Map.Entry<Integer, List<MaxPDecision>> e : buckets.entrySet()) {
                List<MaxPDecision> level = e.getValue();
                level.sort(Comparator.comparingDouble((MaxPDecision m) -> m.bestP).reversed()
                        .thenComparing(m -> m.t.x.getName())
                        .thenComparing(m -> m.t.z.getName())
                        .thenComparing(m -> m.t.y.getName())
                        .thenComparing(m -> stringifySet(m.bestS)));

                for (MaxPDecision d : level) {
                    if (d.outcome != ColliderOutcome.INDEPENDENT) continue;
                    if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                        GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                        if (verbose) {
                            TetradLogger.getInstance().log(
                                    "[MAX-P global(d=" + d.bestS.size() + ")] " +
                                            d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() +
                                            " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                        }
                    }
                }
            }
        } else {
            winners.sort(Comparator.comparingDouble((MaxPDecision d) -> d.bestP).reversed()
                    .thenComparing(d -> d.t.x.getName())
                    .thenComparing(d -> d.t.z.getName())
                    .thenComparing(d -> d.t.y.getName())
                    .thenComparing(d -> stringifySet(d.bestS)));

            for (MaxPDecision d : winners) {
                if (d.outcome != ColliderOutcome.INDEPENDENT) continue;
                if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                    GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                    if (verbose) {
                        TetradLogger.getInstance().log(
                                "[MAX-P global] " + d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() +
                                        " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                    }
                }
            }
        }
    }

    private ColliderOutcome judgeConservative(Triple t, Graph g) throws InterruptedException {
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        boolean sawIncludesZ = false, sawExcludesZ = false, sawAny = false;

        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (!cand.independent) continue;
            sawAny = true;
            if (cand.S.contains(t.z)) sawIncludesZ = true;
            else sawExcludesZ = true;
            if (sawIncludesZ && sawExcludesZ) return ColliderOutcome.AMBIGUOUS;
        }

        if (!sawAny) return ColliderOutcome.NO_SEPSET;
        if (sawExcludesZ && !sawIncludesZ) return ColliderOutcome.INDEPENDENT;
        if (sawIncludesZ && !sawExcludesZ) return ColliderOutcome.DEPENDENT;
        return ColliderOutcome.AMBIGUOUS;
    }

    private ColliderOutcome judgeMaxP(Triple t, Graph g) throws InterruptedException {
        return decideMaxPDetail(t, g).outcome;
    }

    private MaxPDecision decideMaxPDetail(Triple t, Graph g) throws InterruptedException {
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        List<SepCandidate> indep = new ArrayList<>();
        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (cand.independent) indep.add(cand);
        }
        if (indep.isEmpty()) return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());

        double bestP = indep.stream().mapToDouble(c -> c.p).max().orElse(Double.NEGATIVE_INFINITY);

        double bestExcl = Double.NEGATIVE_INFINITY, bestIncl = Double.NEGATIVE_INFINITY;
        for (SepCandidate c : indep) {
            if (c.S.contains(t.z)) bestIncl = Math.max(bestIncl, c.p);
            else bestExcl = Math.max(bestExcl, c.p);
        }
        boolean hasExcl = bestExcl > Double.NEGATIVE_INFINITY;
        boolean hasIncl = bestIncl > Double.NEGATIVE_INFINITY;

        if (hasExcl && hasIncl) {
            if (bestExcl >= bestIncl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, false, bestExcl);
                return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
            }
            if (bestIncl >= bestExcl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, true, bestIncl);
                return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
            }
            if (logMaxPTies && logStream != null) {
                logStream.println("[MAX-P ambiguous] pair=(" + x.getName() + "," + y.getName() + "), z=" + t.z.getName()
                        + " bestExcl=" + bestExcl + " bestIncl=" + bestIncl + " margin=" + maxPMargin);
            }
            return new MaxPDecision(t, ColliderOutcome.AMBIGUOUS, Math.max(bestExcl, bestIncl), Collections.emptySet());
        } else if (hasExcl) {
            Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, false, bestExcl);
            return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
        } else if (hasIncl) {
            Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, true, bestIncl);
            return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
        } else {
            return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());
        }
    }

    private Set<Node> firstTieMatchingContainsZ(List<SepCandidate> indep, Node z, boolean containsZ, double targetP) {
        List<SepCandidate> ties = new ArrayList<>();
        for (SepCandidate c : indep) if (c.p == targetP && c.S.contains(z) == containsZ) ties.add(c);
        ties.sort(Comparator.comparing(c -> stringifySet(c.S)));
        return ties.isEmpty() ? Collections.emptySet() : ties.get(0).S;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Iterable<SepCandidate> enumerateSepsetsWithPvals(Node x, Node y, Graph g) throws InterruptedException {
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        Map<String, SepCandidate> uniq = new LinkedHashMap<>();

        List<Node> adjx = new ArrayList<>(g.getAdjacentNodes(x));
        List<Node> adjy = new ArrayList<>(g.getAdjacentNodes(y));
        adjx.remove(y);
        adjy.remove(x);

        adjx.sort(Comparator.comparing(Node::getName));
        adjy.sort(Comparator.comparing(Node::getName));

        final int depthCap = (depth < 0) ? Integer.MAX_VALUE : depth;
        int maxAdj = Math.max(adjx.size(), adjy.size());

        for (int d = 0; d <= Math.min(depthCap, maxAdj); d++) {
            for (List<Node> adj : new List[]{adjx, adjy}) {
                if (d > adj.size()) continue;

                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;
                while ((choice = gen.next()) != null) {
                    checkTimeout();
                    Set<Node> S = GraphUtils.asSet(choice, adj);
                    String sKey = setKey(S);
                    if (uniq.containsKey(sKey)) continue;

                    IndependenceResult r = test.checkIndependence(x, y, S);
                    uniq.put(sKey, new SepCandidate(S, r.isIndependent(), r.getPValue()));
                }
            }
        }
        return uniq.values();
    }

    private String setKey(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return String.join("\u0001", names);
    }

    // ------------------------------------------------------------------------------------
    // Meek closure (cycle-safe)
    // ------------------------------------------------------------------------------------

//    private void applyMeekRules(Graph g) {
//        if (!meekCycleSafe || !forbidDirectedCycles) {
//            MeekRules meekRules = new MeekRules();
//            meekRules.setKnowledge(knowledge);
//            meekRules.orientImplied(g);
//            return;
//        }
//
//        // Cycle-safe Meek: repeatedly apply Meek, but only keep implied orientations that do not introduce cycles.
//        boolean changed;
//        int guard = 0;
//
//        do {
//            changed = false;
//            guard++;
//            if (guard > 10_000) break; // safety
//
//            // snapshot edges before
//            List<Edge> before = new ArrayList<>(g.getEdges());
//
//            MeekRules meekRules = new MeekRules();
//            meekRules.setKnowledge(knowledge);
//            meekRules.orientImplied(g);
//
//            // detect new directed edges introduced (or newly oriented)
//            List<Edge> after = new ArrayList<>(g.getEdges());
//
//            // Build a map key -> edge for comparisons
//            Map<String, Edge> beforeMap = new HashMap<>();
//            for (Edge e : before) beforeMap.put(edgeKey(e), e);
//
//            for (Edge eAfter : after) {
//                Edge eBefore = beforeMap.get(edgeKey(eAfter));
//                if (eBefore == null) continue;
//
//                // If orientation changed to introduce arrowhead(s), validate it
//                if (!sameOrientation(eBefore, eAfter)) {
//                    // If this particular change creates a cycle, rollback it.
//                    // Rollback by restoring the old edge.
//                    if (wouldCreateCycleIfSetEdge(g, eAfter)) {
//                        // revert
//                        g.removeEdge(eAfter);
//                        g.addEdge(eBefore);
//                        changed = true;
//                    }
//                }
//            }
//
//            // If we reverted anything, we should rerun meek to closure again, hence loop.
//        } while (changed);
//    }

    private void applyMeekRules(Graph g) {
        if (!meekCycleSafe || !forbidDirectedCycles) {
            MeekRules meekRules = new MeekRules();
            meekRules.setKnowledge(knowledge);
            meekRules.orientImplied(g);
            return;
        }

        int guard = 0;

        while (true) {
            guard++;
            if (guard > 10_000) break;

            // Snapshot current graph edges (this is our "before" for this round)
            List<Edge> before = new ArrayList<>(g.getEdges());

            // Run full Meek to closure on the current graph to discover what it *wants* to change
            MeekRules meekRules = new MeekRules();
            meekRules.setKnowledge(knowledge);
            meekRules.orientImplied(g);

            List<Edge> after = new ArrayList<>(g.getEdges());

            // Build maps keyed by unordered node-pair
            Map<String, Edge> beforeMap = new HashMap<>();
            for (Edge e : before) beforeMap.put(edgeKey(e), e);

            Map<String, Edge> afterMap = new HashMap<>();
            for (Edge e : after) afterMap.put(edgeKey(e), e);

            // Collect candidate changes: those pairs that existed before and after but orientation differs
            List<EdgeChange> changes = new ArrayList<>();
            for (Map.Entry<String, Edge> ent : afterMap.entrySet()) {
                String k = ent.getKey();
                Edge eAfter = ent.getValue();
                Edge eBefore = beforeMap.get(k);
                if (eBefore == null) continue; // ignore truly-new edges (PC/Meek shouldn't add adjacencies anyway)
                if (!sameOrientation(eBefore, eAfter)) {
                    changes.add(new EdgeChange(eBefore, eAfter));
                }
            }

            // If Meek produced no orientation changes, we're done.
            if (changes.isEmpty()) {
                // IMPORTANT: we are currently sitting at the "after" graph from the meek call.
                // That's fine since no changes were proposed; it equals the prior state.
                return;
            }

            // Revert graph *completely* back to "before"
            // (We do this by restoring each edge pair to its before version.)
            for (EdgeChange ch : changes) {
                // Remove current edge for that pair and restore before
                Edge cur = afterMap.get(edgeKey(ch.after));
                if (cur != null) g.removeEdge(cur);
                // Ensure before edge is present
                g.addEdge(ch.before);
            }

            // Now replay proposed changes one by one, keeping only those that don't create cycles
            boolean acceptedAny = false;

            for (EdgeChange ch : changes) {
                // Apply the proposed oriented edge
                g.removeEdge(ch.before);
                g.addEdge(ch.after);

                if (hasDirectedCycle(g)) {
                    // Roll back THIS change
                    g.removeEdge(ch.after);
                    g.addEdge(ch.before);
                } else {
                    acceptedAny = true;
                }
            }

            // If we couldn't accept anything without a cycle, stop.
            if (!acceptedAny) return;

            // Otherwise, loop: run Meek again from this new (partially oriented) state.
        }
    }

    private static final class EdgeChange {
        final Edge before;
        final Edge after;

        EdgeChange(Edge before, Edge after) {
            this.before = before;
            this.after = after;
        }
    }

    /** Return true if the current graph contains any directed cycle. */
    private static boolean hasDirectedCycle(Graph g) {
        // Cheap test: for each node, check if there is a directed path from it to itself.
        // If your GraphPaths has a cycle check, use that instead.
        for (Node v : g.getNodes()) {
            if (g.paths().existsDirectedPath(v, v)) return true;
        }
        return false;
    }

    /**
     * Conservative check: temporarily set the edge orientation (already in graph),
     * then check for directed cycles; revert handled by caller.
     */
    private boolean wouldCreateCycleIfSetEdge(Graph g, Edge orientedEdgeNowInGraph) {
        // Quick local sufficient check is hard for arbitrary Meek changes; just test cycle presence.
        // (Graph sizes here are usually small/moderate in PC.)
        return hasDirectedCycle(g);
    }

    private static boolean sameOrientation(Edge a, Edge b) {
        return a.getEndpoint1() == b.getEndpoint1() && a.getEndpoint2() == b.getEndpoint2();
    }

    private static String edgeKey(Edge e) {
        Node n1 = e.getNode1();
        Node n2 = e.getNode2();
        String a = n1.getName();
        String b = n2.getName();
        if (a.compareTo(b) <= 0) return a + "||" + b;
        return b + "||" + a;
    }

    // ------------------------------------------------------------------------------------
    // Checks / timeouts
    // ------------------------------------------------------------------------------------

    private void checkVars(List<Node> nodes) {
        if (!new HashSet<>(test.getVariables()).containsAll(nodes)) {
            throw new IllegalArgumentException("All nodes must be contained in the test's variables.");
        }
    }

    private void checkTimeout() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
        if (timeoutMs >= 0) {
            long now = System.currentTimeMillis();
            if (now - startTimeMs > timeoutMs)
                throw new InterruptedException("Timed out after " + (now - startTimeMs) + " ms");
        }
    }

    // ------------------------------------------------------------
    // Enums & small records
    // ------------------------------------------------------------

    /**
     * An enumeration that defines the styles of collider orientation in a graph or network.
     * This enumeration can be used in algorithms or systems that deal with graph structures,
     * providing different approaches to orienting colliders.
     */
    public enum ColliderOrientationStyle {

        /**
         * Represents the collider orientation style that utilizes separating sets (sepsets).
         * This style is typically employed in graph or network algorithms where colliders
         * are oriented based on separation properties derived from conditional independencies.
         */
        SEPSETS,

        /**
         * Represents the collider orientation style for the Conservative PC algorithm.
         */
        CONSERVATIVE,

        /**
         * Represents the collider orientation style for the PC-Max algorithm.
         */
        MAX_P }

    /**
     * Enum representing whether bidirected edges are allowed in the graph.
     */
    public enum AllowBidirected {

        /**
         * Represents the option to allow bidirected edges in a graph.
         * Used as part of the AllowBidirected enumeration to specify
         * whether such edges are permissible in the graph structure.
         */
        ALLOW,

        /**
         * Represents the option to disallow bidirected edges in a graph.
         * Used as part of the AllowBidirected enumeration to specify that
         * bidirected edges are not permissible in the graph structure.
         */
        DISALLOW }

    /**
     * Represents the possible outcomes for a collider relationship in a causal
     * inference or graph-based algorithm. This enumeration is used to classify
     * the nature of the relationship between variables in terms of their
     * dependency or causal structure.
     */
    private enum ColliderOutcome {

        /**
         * Represents a state where two variables are determined to be independent
         * in the context of collider relationship analysis within a causal inference
         * or graph-based algorithm.
         */
        INDEPENDENT,

        /**
         * Represents a state where two variables are determined to be dependent
         * in the context of collider relationship analysis within a causal inference
         * or graph-based algorithm. This outcome indicates that the variables
         * exhibit a dependency in their relationship in the examined context.
         */
        DEPENDENT,

        /**
         * Represents a state where the relationship between two variables cannot be
         * conclusively determined as independent or dependent in the context of collider
         * relationship analysis within a causal inference or graph-based algorithm.
         * This outcome indicates uncertainty or lack of sufficient information to classify
         * the nature of the relationship.
         */
        AMBIGUOUS,

        /**
         * Represents a state where there is no separating set (sepset) between two variables
         * in the context of collider relationship analysis within a causal inference or
         * graph-based algorithm. This outcome indicates the absence of a conditioning set
         * that can render the variables independent.
         */
        NO_SEPSET }

    /**
     * A utility class that encapsulates a triplet of nodes.
     * This class is immutable and thread-safe as its fields are final.
     */
    public static final class Triple {

        /**
         * The first node in the triplet represented by this Triple object.
         * This field is immutable and represents one component of the triplet structure.
         */
        public final Node x;

        /**
         * The second node in the triplet represented by this Triple object.
         * This field is immutable and represents one component of the triplet structure.
         */
        public final Node z;

        /**
         * The third node in the triplet represented by this Triple object.
         * This field is immutable and represents one component of the triplet structure.
         */
        public final Node y;

        /**
         * Constructs a Triple object with the specified nodes.
         *
         * @param x the first node in the triplet
         * @param z the second node in the triplet
         * @param y the third node in the triplet
         */
        public Triple(Node x, Node z, Node y) { this.x = x; this.z = z; this.y = y; }
    }

    private static final class SepCandidate {
        final Set<Node> S;
        final boolean independent;
        final double p;

        SepCandidate(Set<Node> S, boolean independent, double p) {
            List<Node> sorted = new ArrayList<>(S);
            sorted.sort(Comparator.comparing(Node::getName));
            this.S = new LinkedHashSet<>(sorted);
            this.independent = independent;
            this.p = p;
        }
    }

    private static final class MaxPDecision {
        final Triple t;
        final ColliderOutcome outcome;
        final double bestP;
        final Set<Node> bestS;

        MaxPDecision(Triple t, ColliderOutcome outcome, double bestP, Set<Node> bestS) {
            this.t = t;
            this.outcome = outcome;
            this.bestP = bestP;
            this.bestS = bestS;
        }
    }
}