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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Pc (Unified "Classic PC")
 * <p>
 * Skeleton via FAS (stable toggle), orient unshielded triples using VANILLA/CPC/MAX_P, then Meek rules to closure.
 * Deterministic: sorted names, canonical (x,y) endpoint ordering. No internal CI cache (wrap your test if desired).
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
    private boolean replicatingGraph = false;

    /**
     * Constructs a new instance of the Pc class with a given independence test.
     *
     * @param test an IndependenceTest object that defines the test for conditional independence
     */
    public Pc(IndependenceTest test) {
        this.test = test;
    }

    private static boolean isArrowheadAllowed(Node from, Node to, Knowledge knowledge) {
        if (knowledge.isEmpty()) return true;
        return !knowledge.isRequired(to.toString(), from.toString()) && !knowledge.isForbidden(from.toString(), to.toString());
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
     * Sets the maximum p-value margin for decision-making in the MAX-P algorithm. The margin is constrained to be
     * non-negative, ensuring that negative values are reset to 0.0.
     *
     * @param margin the maximum p-value margin; if provided value is less than 0.0, it will default to 0.0.
     */
    public void setMaxPMargin(double margin) {
        this.maxPMargin = Math.max(0.0, margin);
    }

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

        // 3) Meek rules to closure
        applyMeekRules(g);

        return g;
    }

    /**
     * Returns the independence test associated with this instance.
     *
     * @return the {@link IndependenceTest} object representing the test for conditional independence.
     */
    public IndependenceTest getTest() {
        return test;
    }

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
            throw new IllegalArgumentException(String.format("The nodes of the proposed new test are not equal list-wise\n" + "to the nodes of the existing test."));
        }

        this.test = test;
    }

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
     * Identifies and retrieves all noncollider triples from the given graph. A noncollider triple is a triple where the
     * middle node is not a collider, meaning there are no arrowheads pointing to it from either of the other two
     * nodes.
     *
     * @param g the graph from which noncollider triples are to be extracted
     * @return a list of noncollider triples found in the graph
     */
    public List<Triple> getNoncolliderTriples(Graph g) {
        List<Triple> result = new ArrayList<>();
        for (Triple t : collectUnshieldedTriples(g)) {
            boolean intoZFromX = g.isParentOf(t.x, t.z);
            boolean intoZFromY = g.isParentOf(t.y, t.z);
            boolean outOfZToX = g.isParentOf(t.z, t.x);
            boolean outOfZToY = g.isParentOf(t.z, t.y);
            boolean undZX = isUndirected(g, t.z, t.x);
            boolean undZY = isUndirected(g, t.z, t.y);
            // Definite noncollider only when BOTH incident edges have tails at z
            // i.e., z->x or undirected (tail at z), and z->y or undirected,
            // and there are NO arrowheads pointing into z.
            if (!intoZFromX && !intoZFromY && (outOfZToX || undZX) && (outOfZToY || undZY)) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Identifies and returns a list of ambiguous triples from the given graph. Ambiguous triples are determined based
     * on the structure of the graph and the relationships between nodes in each triple.
     *
     * @param g the graph from which ambiguous triples are to be identified
     * @return a list of triples from the graph that are categorized as ambiguous
     */
    public List<Triple> getAmbiguousTriples(Graph g) {
        List<Triple> result = new ArrayList<>();
        for (Triple t : collectUnshieldedTriples(g)) {
            boolean collider = g.isParentOf(t.x, t.z) && g.isParentOf(t.y, t.z);
            boolean intoZFromX = g.isParentOf(t.x, t.z);
            boolean intoZFromY = g.isParentOf(t.y, t.z);
            boolean outOfZToX = g.isParentOf(t.z, t.x);
            boolean outOfZToY = g.isParentOf(t.z, t.y);
            boolean undZX = isUndirected(g, t.z, t.x);
            boolean undZY = isUndirected(g, t.z, t.y);
            boolean noncollider = !intoZFromX && !intoZFromY && (outOfZToX || undZX) && (outOfZToY || undZY);
            if (!collider && !noncollider) result.add(t);
        }
        return result;
    }

    // Helper: collect all unshielded triples, with (x,y) endpoints canonicalized by name.
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
                            Node tmp = x;
                            x = y;
                            y = tmp;
                        }
                        triples.add(new Triple(x, z, y));
                    }
                }
            }
        }
        triples.sort(Comparator.comparing((Triple t) -> t.x.getName()).thenComparing(t -> t.z.getName()).thenComparing(t -> t.y.getName()));
        return triples;
    }

    private boolean isUndirected(Graph g, Node a, Node b) {
        // Adjacent and neither is a parent of the other â undirected edge
        return g.isAdjacentTo(a, b) && !g.isParentOf(a, b) && !g.isParentOf(b, a);
    }

    // ------------------------------------------------------------------------------------
    // Collider orientation
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
                        GraphUtils.orientCollider(g, t.x, t.z, t.y);
                        if (verbose)
                            TetradLogger.getInstance().log("Collider oriented: " + t.x.getName() + " -> " + t.z.getName() + " <- " + t.y.getName());
                    }
                }
                case DEPENDENT, NO_SEPSET -> { /* leave unoriented */ }
                case AMBIGUOUS -> {
                    if (allowBidirected == AllowBidirected.ALLOW) {
                        ambiguousTriples.add(t);
                        // Optionally mark ambiguity if your Graph supports bidirected marks.
                    }
                    if (verbose)
                        TetradLogger.getInstance().log("Ambiguous triple: " + t.x.getName() + " - " + t.z.getName() + " - " + t.y.getName());
                }
            }
        }

        Set<edu.cmu.tetrad.graph.Triple> _ambiguousTriples = new HashSet<>();
        for (Triple t : ambiguousTriples) {
            _ambiguousTriples.add(new edu.cmu.tetrad.graph.Triple(t.x, t.z, t.y));
        }

        g.setAmbiguousTriples(_ambiguousTriples);
    }

    // ----- CPC/MAX-P decisions -----------------------------------------------------------

    /**
     * Global, order-independent MAX-P collider orientation (optionally depth-stratified; avoids â).
     */
    private void orientMaxPGlobal(Graph g, List<Triple> triples) throws InterruptedException {
        List<MaxPDecision> winners = new ArrayList<>();
        for (Triple t : triples) {
            checkTimeout();
            MaxPDecision d = decideMaxPDetail(t, g);
            if (d.outcome == ColliderOutcome.AMBIGUOUS && logMaxPTies) {
                // (details already printed inside decideMaxPDetail if enabled)
            }
            if (d.outcome == ColliderOutcome.INDEPENDENT) {
                winners.add(d);
            }
        }

        if (maxPDepthStratified) {
            Map<Integer, List<MaxPDecision>> buckets = new TreeMap<>();
            for (MaxPDecision d : winners) {
                buckets.computeIfAbsent(d.bestS.size(), k -> new ArrayList<>()).add(d);
            }
            for (Map.Entry<Integer, List<MaxPDecision>> e : buckets.entrySet()) {
                List<MaxPDecision> level = e.getValue();
                level.sort(Comparator.comparingDouble((MaxPDecision m) -> m.bestP).reversed().thenComparing(m -> m.t.x.getName()).thenComparing(m -> m.t.z.getName()).thenComparing(m -> m.t.y.getName()).thenComparing(m -> stringifySet(m.bestS)));
                for (MaxPDecision d : level) {
                    if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                        GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                        if (verbose)
                            TetradLogger.getInstance().log("[MAX-P global(d=" + d.bestS.size() + ")] " + d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() + " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                    }
                }
            }
        } else {
            winners.sort(Comparator.comparingDouble((MaxPDecision d) -> d.bestP).reversed().thenComparing(d -> d.t.x.getName()).thenComparing(d -> d.t.z.getName()).thenComparing(d -> d.t.y.getName()).thenComparing(d -> stringifySet(d.bestS)));

            for (MaxPDecision d : winners) {
                if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                    GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                    if (verbose)
                        TetradLogger.getInstance().log("[MAX-P global] Collider oriented: " + d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() + " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                }
            }
        }
    }

    private ColliderOutcome judgeConservative(Triple t, Graph g) throws InterruptedException {
        // Ensure canonical (x,y) in case a caller ever constructs Triple differently
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) {
            Node tmp = x;
            x = y;
            y = tmp;
        }

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

    /**
     * Detailed MAX-P: returns outcome + bestP + bestS. Applies the margin guard: if both sides have candidates and
     * their best p's differ by < maxPMargin, returns AMBIGUOUS.
     */
    private MaxPDecision decideMaxPDetail(Triple t, Graph g) throws InterruptedException {
        // Canonicalize (x,y) defensively
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) {
            Node tmp = x;
            x = y;
            y = tmp;
        }

        List<SepCandidate> indep = new ArrayList<>();
        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (cand.independent) indep.add(cand);
        }
        if (indep.isEmpty()) return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());

        double bestP = indep.stream().mapToDouble(c -> c.p).max().orElse(Double.NEGATIVE_INFINITY);
        List<SepCandidate> ties = new ArrayList<>();
        for (SepCandidate c : indep) if (c.p == bestP) ties.add(c);
        ties.sort(Comparator.comparing((SepCandidate c) -> c.S.contains(t.z))   // prefer excludes-Z first in ordering
                .thenComparing(c -> stringifySet(c.S)));

        double bestExcl = Double.NEGATIVE_INFINITY, bestIncl = Double.NEGATIVE_INFINITY;
        for (SepCandidate c : indep) {
            if (c.S.contains(t.z)) bestIncl = Math.max(bestIncl, c.p);
            else bestExcl = Math.max(bestExcl, c.p);
        }
        boolean hasExcl = bestExcl > Double.NEGATIVE_INFINITY;
        boolean hasIncl = bestIncl > Double.NEGATIVE_INFINITY;

        if (hasExcl && hasIncl) {
            if (bestExcl >= bestIncl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, false);
                return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
            }
            if (bestIncl >= bestExcl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, true);
                return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
            }
            if (logMaxPTies && ties.size() > 1) debugPrintMaxPTies(t, bestP, ties);
            return new MaxPDecision(t, ColliderOutcome.AMBIGUOUS, Math.max(bestExcl, bestIncl), ties.isEmpty() ? Collections.emptySet() : ties.get(0).S);
        } else if (hasExcl) {
            Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, false);
            return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
        } else if (hasIncl) {
            Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, true);
            return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
        } else {
            return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());
        }
    }

    // ----- enumeration (unique S across both sides), rely on external cache --------------

    private Set<Node> firstTieMatchingContainsZ(List<SepCandidate> ties, Node z, boolean containsZ) {
        for (SepCandidate c : ties) {
            if (c.S.contains(z) == containsZ) return c.S;
        }
        return ties.isEmpty() ? Collections.emptySet() : ties.get(0).S;
    }

    private Iterable<SepCandidate> enumerateSepsetsWithPvals(Node x, Node y, Graph g) throws InterruptedException {
        // Enforce canonical (x,y)
        if (x.getName().compareTo(y.getName()) > 0) {
            Node tmp = x;
            x = y;
            y = tmp;
        }

        Map<String, SepCandidate> uniq = new LinkedHashMap<>(); // deterministic order

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
                    if (uniq.containsKey(sKey)) continue; // de-dup across sides

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

    private void debugPrintMaxPTies(Triple t, double bestP, List<SepCandidate> ties) {
        if (logStream == null) return;
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) {
            Node tmp = x;
            x = y;
            y = tmp;
        }

        String header = "[MAX-P tie] pair=(" + x.getName() + "," + y.getName() + "), z=" + t.z.getName() + ", bestP=" + bestP + ", #ties=" + ties.size();
        logStream.println(header);
        for (SepCandidate c : ties) {
            boolean containsZ = c.S.contains(t.z);
            String line = "  S=" + stringifySet(c.S) + " | contains(z)=" + containsZ + " | p=" + c.p;
            logStream.println(line);
        }
    }

    // ----- checks / utils ---------------------------------------------------------------

    private String stringifySet(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return "{" + String.join(",", names) + "}";
    }

    private boolean canOrientCollider(Graph g, Node x, Node z, Node y) {
        if (!g.isAdjacentTo(x, z) || !g.isAdjacentTo(z, y)) return false;
        if (!isArrowheadAllowed(x, z, knowledge) || !isArrowheadAllowed(y, z, knowledge)) return false;
        if (allowBidirected != AllowBidirected.ALLOW && (g.isParentOf(z, x) || g.isParentOf(z, y))) return false;
        return true;
    }

    private void applyMeekRules(Graph g) {
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(g);
    }

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

    /**
     * Retrieves the Fas object associated with this instance.
     *
     * @return the Fas object
     */
    public Fas getFas() {
        return fas;
    }

    /**
     * Sets the flag indicating whether the graph is in a replicating state.
     *
     * @param replicatingGraph a boolean value where {@code true} denotes that the graph is replicating and
     *                         {@code false} denotes that it is not.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
        this.replicatingGraph = replicatingGraph;
    }

    // ------------------------------------------------------------
    // Enums & small records
    // ------------------------------------------------------------

    /**
     * Enum representing the orientation style used for colliders in a computational context.
     */
    public enum ColliderOrientationStyle {
        /**
         * Represents an orientation style based on separate sets.
         */
        SEPSETS,
        /**
         * Represents a conservative approach to orientation.
         */
        CONSERVATIVE,
        /**
         * Represents an orientation style that maximizes some criterion or probability.
         */
        MAX_P
    }

    /**
     * Enum representing the permission to allow or disallow bidirected edges.
     * <p>
     * This enumeration can be used to specify whether bidirectional relationships are permitted in a specific context.
     * It provides two possible values:
     */
    public enum AllowBidirected {
        /**
         * Indicates that bidirected edges are allowed.
         */
        ALLOW,
        /**
         * Indicates that bidirected edges are not allowed.
         */
        DISALLOW
    }

    /**
     * Represents the possible outcomes of a collider relationship in a probabilistic or causal graph context.
     * <p>
     * This enumeration defines the different states that can result from evaluating the collider's relationships within
     * a graphical model. The outcomes provide insights into the structural dependencies and separation properties
     * between nodes.
     */
    private enum ColliderOutcome {
        /**
         * Indicates that the nodes involved are independent.
         */
        INDEPENDENT,
        /**
         * Indicates that the nodes involved are dependent on each other.
         */
        DEPENDENT,
        /**
         * Indicates that the dependency relationship cannot be clearly determined or is uncertain.
         */
        AMBIGUOUS,
        /**
         * Indicates that no separating set exists between the nodes in the graph.
         */
        NO_SEPSET
    }

    /**
     * Public so callers can use it in results. Endpoints (x,y) are canonicalized by name when created here.
     */
    public static final class Triple {
        /**
         * The first endpoint in a {@link Triple} that represents a relationship or connection between three
         * {@link Node} objects. This endpoint is treated as canonicalized by its name.
         * <p>
         * Instances of this field are immutable once set within a {@link Triple}.
         *
         * @see Node
         * @see Triple
         */
        public final Node x;
        /**
         * Represents the second endpoint in a {@link Triple} that models a relationship or connection between three
         * {@link Node} objects. This node is immutable once set during the construction of a {@link Triple}.
         * <p>
         * The specific semantics of this node depend on the context in which it is used.
         *
         * @see Node
         * @see Triple
         */
        public final Node z;
        /**
         * Represents the second endpoint in a {@link Triple} structure that establishes a relationship or connection
         * between three {@link Node} instances. This field is immutable once assigned during the instantiation of a
         * {@link Triple}.
         * <p>
         * The meaning or role of this {@link Node} in the {@link Triple} depends on the specific context in which the
         * {@link Triple} is used. Typically, it serves as a conceptual or functional link between the other nodes
         * within the same {@link Triple}.
         *
         * @see Node
         * @see Triple
         */
        public final Node y;

        /**
         * Constructs a Triple instance representing a relationship or connection between three Node objects.
         *
         * @param x the first endpoint in this Triple
         * @param z the second endpoint in this Triple
         * @param y the third endpoint in this Triple
         */
        public Triple(Node x, Node z, Node y) {
            this.x = x;
            this.z = z;
            this.y = y;
        }
    }

    private static final class SepCandidate {
        final Set<Node> S;         // deterministic storage
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
