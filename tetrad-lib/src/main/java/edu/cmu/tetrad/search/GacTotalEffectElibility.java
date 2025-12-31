package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * Library-side helper for "Adjustment & Total Effects" style workflows.
 *
 * <p>This class encapsulates the correctness/eligibility logic that should not live in the GUI:
 * <ul>
 *   <li>Graph amenability checks for (X, Y) under a chosen graph type and max path length</li>
 *   <li>Detection of "no potentially directed paths X -> Y", in which case total effect is forced to 0</li>
 * </ul>
 *
 * <p>It deliberately does NOT run regressions. The GUI (or API caller) can use the returned status to decide:
 * <ul>
 *   <li>what to display</li>
 *   <li>whether to compute adjustment sets</li>
 *   <li>whether to run an estimator</li>
 * </ul>
 *
 * <p>Typical usage (pairwise):
 * <pre>
 *   GacTotalEffectElibility gte = new GacTotalEffectElibility(graph, GraphType.PAG, 8);
 *   Eligibility e = gte.checkPairwise(x, y);
 *   if (e.status() == Status.NO_PD_PATHS) { ... show 0.0 ... }
 *   else if (e.status() == Status.NOT_AMENABLE) { ... show Not amenable ... }
 *   else { ... compute adjustment sets + regress ... }
 * </pre>
 */
public final class GacTotalEffectElibility {

    /**
     * High-level eligibility states for total-effect computation.
     */
    public enum Status {
        /** OK to proceed (e.g., compute adjustment sets, then estimate). */
        OK,

        /** The graph is not amenable for (X, Y) under the chosen graph type / constraints. */
        NOT_AMENABLE,

        /** No potentially directed paths from X to Y exist (within maxPathLength); total effect is forced to 0. */
        NO_PD_PATHS,

        /** Invalid or degenerate input (e.g., nulls, x==y, empty sets). */
        INVALID_INPUT
    }

    /**
     * Immutable eligibility result.
     */
    public static final class Eligibility {
        private final Status status;
        private final String reason;
        private final boolean amenable; // redundant but handy for UI callers that already track this notion
        private final Set<List<Node>> potentiallyDirectedPaths; // may be empty or null depending on status

        private Eligibility(Status status, String reason, boolean amenable, Set<List<Node>> pdPaths) {
            this.status = Objects.requireNonNull(status, "status");
            this.reason = reason;
            this.amenable = amenable;
            this.potentiallyDirectedPaths = pdPaths == null ? Collections.emptySet() : Collections.unmodifiableSet(pdPaths);
        }

        public Status status() {
            return status;
        }

        public String reason() {
            return reason;
        }

        public boolean amenable() {
            return amenable;
        }

        /**
         * Potentially directed paths from X to Y, as returned by {@code graph.paths().potentiallyDirectedPaths(...)}.
         * For {@link Status#NO_PD_PATHS} this will be empty.
         */
        public Set<List<Node>> potentiallyDirectedPaths() {
            return potentiallyDirectedPaths;
        }

        /**
         * Convenience: whether the caller should proceed to compute adjustment sets and estimate.
         */
        public boolean shouldEstimate() {
            return status == Status.OK;
        }

        /**
         * Convenience: whether the caller should force a numeric 0 effect (rather than estimating).
         */
        public boolean shouldForceZeroEffect() {
            return status == Status.NO_PD_PATHS;
        }

        @Override
        public String toString() {
            return "Eligibility{" +
                   "status=" + status +
                   ", amenable=" + amenable +
                   ", pdPaths=" + potentiallyDirectedPaths.size() +
                   (reason == null ? "" : ", reason='" + reason + '\'') +
                   '}';
        }
    }

    /**
     * Optional hook for computing adjustment sets. This is intentionally pluggable so the GUI can use
     * RA/OMA/etc without duplicating gating logic. Tetrad-lib callers can also provide their own.
     */
    @FunctionalInterface
    public interface AdjustmentSetProvider {
        /**
         * Returns adjustment sets for estimating the total effect of x on y.
         * The provider may return an empty list to indicate "no sets" (e.g., not amenable under its own contract).
         */
        List<Set<Node>> compute(Graph graph, Node x, Node y, String graphType, int maxPathLength);
    }

    private final Graph graph;
    private final String graphType;
    private final int maxPathLength;

    /**
     * @param graph the causal graph (PAG/CPDAG/DAG/MAG, etc.)
     * @param graphType used for amenability and potentially-directed path logic
     * @param maxPathLength maximum path length to consider (must be >= 1)
     */
    public GacTotalEffectElibility(Graph graph, String graphType, int maxPathLength) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.graphType = Objects.requireNonNull(graphType, "graphType");
        if (maxPathLength < 1) {
            throw new IllegalArgumentException("maxPathLength must be >= 1, but was " + maxPathLength);
        }
        this.maxPathLength = maxPathLength;
    }

    public Graph getGraph() {
        return graph;
    }

    public String getGraphType() {
        return graphType;
    }

    public int getMaxPathLength() {
        return maxPathLength;
    }

    /**
     * Pairwise check for a single (x, y).
     *
     * <p>Logic:
     * <ol>
     *   <li>validate inputs</li>
     *   <li>amenability check</li>
     *   <li>potentially-directed-path existence check</li>
     * </ol>
     *
     * <p>If there are no potentially directed paths from x to y, returns {@link Status#NO_PD_PATHS}
     * and callers should force total effect = 0 (and should not run regression).
     */
    public Eligibility checkPairwise(Node x, Node y) {
        if (x == null || y == null) {
            return new Eligibility(Status.INVALID_INPUT, "x or y is null", false, Collections.emptySet());
        }
        if (x.equals(y)) {
            return new Eligibility(Status.INVALID_INPUT, "x equals y", false, Collections.emptySet());
        }
        // Defensive: ensure nodes belong to the graph.
        // (Some code paths construct Nodes elsewhere; this avoids silent surprises.)
        if (graph.getNode(x.getName()) == null || graph.getNode(y.getName()) == null) {
            return new Eligibility(Status.INVALID_INPUT,
                    "x or y is not in graph (by name lookup): x=" + x.getName() + ", y=" + y.getName(),
                    false, Collections.emptySet());
        }

        boolean amenable = graph.paths().isGraphAmenable(x, y, graphType, maxPathLength, Set.of());
        if (!amenable) {
            return new Eligibility(Status.NOT_AMENABLE, "Graph is not amenable for (x,y)", false, Collections.emptySet());
        }

        Set<List<Node>> pdPaths = graph.paths().potentiallyDirectedPaths(x, y, maxPathLength);
        if (pdPaths == null || pdPaths.isEmpty()) {
            return new Eligibility(Status.NO_PD_PATHS,
                    "No potentially directed paths from x to y (within maxPathLength)",
                    true, Collections.emptySet());
        }

        return new Eligibility(Status.OK, null, true, pdPaths);
    }

    /**
     * Joint check for a set X (treat as "treatments") and a single outcome y.
     *
     * <p>Current policy: returns {@link Status#NO_PD_PATHS} iff there are no potentially directed paths
     * from any x in X to y (within maxPathLength). If at least one exists and amenability holds for all,
     * returns {@link Status#OK}.
     *
     * <p>If you want a different policy (e.g., require PD paths from every x), adjust the logic here.
     */
    public Eligibility checkJoint(Set<Node> X, Node y) {
        if (X == null || X.isEmpty() || y == null) {
            return new Eligibility(Status.INVALID_INPUT, "X is null/empty or y is null", false, Collections.emptySet());
        }
        if (X.contains(y)) {
            return new Eligibility(Status.INVALID_INPUT, "X contains y", false, Collections.emptySet());
        }

        // Validate membership
        for (Node x : X) {
            if (x == null) {
                return new Eligibility(Status.INVALID_INPUT, "X contains null", false, Collections.emptySet());
            }
            if (graph.getNode(x.getName()) == null) {
                return new Eligibility(Status.INVALID_INPUT,
                        "Node in X not in graph (by name lookup): " + x.getName(),
                        false, Collections.emptySet());
            }
        }
        if (graph.getNode(y.getName()) == null) {
            return new Eligibility(Status.INVALID_INPUT,
                    "y not in graph (by name lookup): " + y.getName(),
                    false, Collections.emptySet());
        }

        // Amenability policy for joint:
        // Require amenability for each x in X to y (simple/defensible default).
        for (Node x : X) {
            boolean amenable = graph.paths().isGraphAmenable(x, y, graphType, maxPathLength, Set.of());
            if (!amenable) {
                return new Eligibility(Status.NOT_AMENABLE,
                        "Graph is not amenable for at least one (x,y) pair in joint query",
                        false, Collections.emptySet());
            }
        }

        // PD-path existence policy for joint:
        // Collect PD paths across all x in X. If none exist, force zero effect.
        Set<List<Node>> allPdPaths = new LinkedHashSet<>();
        for (Node x : X) {
            Set<List<Node>> pd = graph.paths().potentiallyDirectedPaths(x, y, maxPathLength);
            if (pd != null) allPdPaths.addAll(pd);
        }

        if (allPdPaths.isEmpty()) {
            return new Eligibility(Status.NO_PD_PATHS,
                    "No potentially directed paths from any x in X to y (within maxPathLength)",
                    true, Collections.emptySet());
        }

        return new Eligibility(Status.OK, null, true, allPdPaths);
    }

    /**
     * Optional convenience: compute adjustment sets using a provided provider, but only if eligibility is OK.
     * This helps callers avoid duplicating "don’t compute Z sets when NO_PD_PATHS / NOT_AMENABLE".
     */
    public List<Set<Node>> computeAdjustmentSetsIfEligible(Node x, Node y, AdjustmentSetProvider provider) {
        Objects.requireNonNull(provider, "provider");
        Eligibility e = checkPairwise(x, y);
        if (e.status() != Status.OK) {
            return Collections.emptyList();
        }
        return provider.compute(graph, x, y, graphType, maxPathLength);
    }

    /**
     * Same idea as {@link #computeAdjustmentSetsIfEligible(Node, Node, AdjustmentSetProvider)} but for joint.
     *
     * <p>Note: most adjustment-set algorithms for joint effects are not just a loop over x in X.
     * So this method is intentionally not implemented here; it’s provided as a hook point.
     */
    public List<Set<Node>> computeJointAdjustmentSetsIfEligible(Set<Node> X, Node y, JointAdjustmentSetProvider provider) {
        Objects.requireNonNull(provider, "provider");
        Eligibility e = checkJoint(X, y);
        if (e.status() != Status.OK) {
            return Collections.emptyList();
        }
        return provider.compute(graph, X, y, graphType, maxPathLength);
    }

    /**
     * Optional hook for joint adjustment sets.
     */
    @FunctionalInterface
    public interface JointAdjustmentSetProvider {
        List<Set<Node>> compute(Graph graph, Set<Node> X, Node y, String graphType, int maxPathLength);
    }
}