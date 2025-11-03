package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.function.Function;

/**
 * <p>
 * The {@code CdnodPagOrienter} class is responsible for orienting edges in a
 * PAG (Partial Ancestral Graph). It integrates various configuration options
 * to ensure the process adheres to <i>strong legality</i> rules while allowing
 * flexibility through methods for configuring tiers, protected nodes, and
 * additional constraints.
 * </p>
 *
 * <p>
 * The class supports features such as propagating changes, excluding context
 * nodes, enforcing tier constraints, and providing an undo mechanism for
 * reversible operations.
 * </p>
 *
 * <p><b>Core responsibilities of this class include:</b></p>
 *
 * <ul>
 *   <li>Managing the orientation of edges in the PAG based on configurable rules.</li>
 *   <li>Ensuring the legality and integrity of the graph throughout the orientation process.</li>
 *   <li>Allowing extensibility through configurable constraints and flexibility in processing nodes.</li>
 * </ul>
 */
public final class CdnodPagOrienter {

    /**
     * A reference to a {@link ChangeOracle} used by the orienter to evaluate changes and stability in probabilistic
     * dependencies with respect to multi-context scenarios. The ChangeOracle provides methods for determining whether
     * conditional probabilities vary or remain stable across different contexts.
     */
    private final ChangeOracle oracle;
    /**
     * A function representing the strong legality check for PAG (Partial Ancestral Graph) orientation. This function
     * takes a Graph as input and returns a Boolean value indicating whether the fixed-point condition PAG(MAG(G)) == G
     * holds true for the given graph.
     * <p>
     * The strong legality ensures that the orientation process adheres to specific constraints, maintaining the
     * consistency of the PAG with the given graph structure.
     */
    private final Function<Graph, Boolean> strongPagLegality; // fixed-point: PAG(MAG(G)) == G
    /**
     * A function that propagates changes within a graph structure by modifying it in-place. The function takes a
     * {@code Graph} instance as input, applies the required logic to propagate updates or process the graph, and then
     * returns the same modified {@code Graph} instance.
     * <p>
     * This function is typically used to implement in-place graph transformations or orientations, ensuring that the
     * graph structure conforms to certain desired properties or constraints after propagation.
     */
    private final Function<Graph, Graph> propagator;          // in-place; returns same instance
    /**
     * A set of nodes that are protected from certain modifications during the orientation process in the directed
     * graph. This field is used to ensure that specific nodes maintain their structural or functional roles within the
     * graph.
     */
    private final Set<Node> protectedNodes = new LinkedHashSet<>();
    /**
     * A mapping of nodes to their corresponding tier levels, where a smaller integer value indicates an earlier tier.
     * This is used to enforce tier-based constraints within the graph orientation process.
     * <p>
     * The tiers define a hierarchical structure for nodes, and this structure helps guide the orientation logic by
     * ensuring that nodes with earlier tiers have precedence over nodes in later tiers.
     */
    private final Map<Node, Integer> tier = new HashMap<>(); // smaller = earlier
    /**
     * A stack used to record undoable operations in the form of {@link Runnable} commands. Each operation pushed onto
     * this stack represents an action that can be reversed, enabling the ability to backtrack changes made to the graph
     * or other internal states.
     * <p>
     * This stack operates on a last-in-first-out (LIFO) basis, where the most recently added undo operation is the
     * first to be executed when an undo is triggered.
     * <p>
     * It is primarily utilized within the context of the CdnodPagOrienter class to manage reversible modifications
     * during graph orientation.
     */
    private final Deque<Runnable> undoStack = new ArrayDeque<>();
    /**
     * A graphical representation of a partially oriented acyclic graph (PAG). The `pag` variable is a core field of the
     * `CdnodPagOrienter` class and serves as the primary graph being manipulated during orientation operations.
     * <p>
     * The PAG is used to represent both the relationships between nodes and the partial orientation applied based on
     * constraints and rules defined in the context of causal discovery algorithms. This field typically undergoes
     * various modifications during execution of the associated methods in the `CdnodPagOrienter` class.
     */
    private Graph pag;
    /**
     * Represents the maximum subset size used during the operation of the CdnodPagOrienter class. This variable
     * determines the upper limit on the size of subsets that are considered during specific computational or logical
     * operations within the class.
     * <p>
     * The default value is set to 1, but it can be modified through the {@code withMaxSubsetSize(int k)} method to
     * tailor the behavior of the class to specific requirements or datasets.
     */
    private int maxSubsetSize = 1;
    /**
     * Indicates whether the proxy guard mechanism is enabled or not. When enabled, additional constraints or
     * validations may be applied during the processing or orientation of the graph.
     */
    private boolean useProxyGuard = true;
    /**
     * Indicates whether contexts that originate from set S should be excluded during specific operations or processing
     * steps in the CdnodPagOrienter class. This variable primarily influences the behavior of the orientation logic to
     * either include or disregard contextual information provided by set S.
     */
    private boolean excludeContextsFromS = true;

    /**
     * Constructs a CdnodPagOrienter instance with the specified parameters.
     *
     * @param pag               the graph representation of the PAG (Possible Ancestral Graph)
     * @param oracle            the ChangeOracle which provides context nodes and handles constraints
     * @param strongPagLegality a function to verify the strong legality of the PAG
     * @param propagator        a function to propagate changes in the PAG
     */
    public CdnodPagOrienter(Graph pag,
                            ChangeOracle oracle,
                            Function<Graph, Boolean> strongPagLegality,
                            Function<Graph, Graph> propagator) {
        this.pag = Objects.requireNonNull(pag, "pag");
        this.oracle = Objects.requireNonNull(oracle, "oracle");
        this.strongPagLegality = Objects.requireNonNull(strongPagLegality, "legality");
        this.propagator = Objects.requireNonNull(propagator, "propagator");
        // contexts never receive arrowheads
        this.protectedNodes.addAll(oracle.contexts());
    }

    private static <T> Set<T> plus(Set<T> s, T x) {
        Set<T> u = new LinkedHashSet<>(s);
        u.add(x);
        return u;
    }

    /**
     * Sets the maximum subset size to the given value. The size is restricted to a non-negative value. This method
     * updates the state of the current object and allows for method chaining.
     *
     * @param k the desired maximum subset size; if negative, it will be set to 0.
     * @return the current instance of {@code CdnodPagOrienter} for method chaining.
     */
    // ---- Fluent setters ----
    public CdnodPagOrienter withMaxSubsetSize(int k) {
        this.maxSubsetSize = Math.max(0, k);
        return this;
    }

    /**
     * Configures the use of a proxy guard for the current CdnodPagOrienter instance. The proxy guard is a mechanism
     * that provides additional constraints or checks when processing the PAG.
     *
     * @param on specifies whether to enable (true) or disable (false) the proxy guard.
     * @return the current instance of {@code CdnodPagOrienter} for method chaining.
     */
    public CdnodPagOrienter withProxyGuard(boolean on) {
        this.useProxyGuard = on;
        return this;
    }

    /**
     * Configures whether to exclude contexts from S during the processing of the PAG. This modifies the internal state
     * of the current object and supports method chaining.
     *
     * @param on specifies whether to enable (true) or disable (false) the exclusion of contexts from S.
     * @return the current instance of {@code CdnodPagOrienter} for method chaining.
     */
    public CdnodPagOrienter withExcludeContextsFromS(boolean on) {
        this.excludeContextsFromS = on;
        return this;
    }

    /**
     * Prohibits the addition of arrowheads into the specified nodes in the PAG (Possible Ancestral Graph). This ensures
     * that the given nodes are protected from certain types of modifications. The method updates the internal state of
     * the object and allows for method chaining.
     *
     * @param nodes a collection of {@code Node} instances to be protected against arrowhead additions
     * @return the current instance of {@code CdnodPagOrienter} for method chaining
     */
    public CdnodPagOrienter forbidArrowheadsInto(Collection<Node> nodes) {
        this.protectedNodes.addAll(nodes);
        return this;
    }

    /**
     * Prohibits the addition of arrowheads into the specified node in the PAG (Possible Ancestral Graph). This ensures
     * that the given node is protected from certain types of modifications. The method updates the internal state of
     * the object and supports method chaining.
     *
     * @param node the {@code Node} instance to be protected against arrowhead additions
     * @return the current instance of {@code CdnodPagOrienter} for method chaining
     */
    public CdnodPagOrienter forbidArrowheadsInto(Node node) {
        this.protectedNodes.add(node);
        return this;
    }

    /**
     * Configures the tiers for the current instance of {@code CdnodPagOrienter}. Tiers are used to group nodes,
     * assigning each node to a specific level or layer. This method merges the provided tiers map into the existing
     * tier configuration. If the provided map is not null, its contents will replace or add to the current tiers. This
     * method supports method chaining by returning the current instance.
     *
     * @param tiers a map where the keys are {@code Node} instances and the values are their respective tier
     *              assignments
     * @return the current instance of {@code CdnodPagOrienter} for method chaining
     */
    public CdnodPagOrienter withTiers(Map<Node, Integer> tiers) {
        if (tiers != null) this.tier.putAll(tiers);
        return this;
    }

    /**
     * <p>
     * Executes the process of orienting edges in a PAG (Partial Ancestral Graph) based on specific rules and
     * constraints while maintaining <i>strong legality</i>. The method iterates through nodes and applies C1-like
     * orientation attempts, ensures protected nodes are not modified, respects tier constraints, and optionally
     * excludes certain nodes from being considered during the orientation steps.
     * </p>
     *
     * <p><b>Core responsibilities of the method:</b></p>
     *
     * <ul>
     *   <li>Iterates through the nodes in the PAG and their adjacent nodes.</li>
     *   <li>Skips nodes marked as protected or nodes that do not meet specific orientation guards,
     *       such as directed edges or tier constraints.</li>
     *   <li>Determines the neighborhood set for each node, considering configuration options
     *       such as excluding context nodes.</li>
     *   <li>Attempts to orient edges using a per-edge strong legality gating mechanism.</li>
     *   <li>Applies a safety net using a propagator to ensure the strong legality of the resulting PAG.</li>
     *   <li>Supports an undo mechanism to revert changes if the PAG violates strong legality rules after propagation.</li>
     * </ul>
     */
    public void run() {
        final List<Node> ctx = oracle.contexts();

        for (Node y : pag.getNodes()) {
            if (protectedNodes.contains(y)) continue; // never add heads into protected

            final List<Node> adjs = pag.getAdjacentNodes(y);
            for (Node x : adjs) {
                if (protectedNodes.contains(x)) continue;
                if (pag.isDirectedFromTo(x, y) || pag.isDirectedFromTo(y, x)) continue;

                // Tier guard
                Integer tx = tier.get(x), ty = tier.get(y);
                if (tx != null && ty != null && tx >= ty) continue;

                // Neighborhood for S = Adj(Y)\{X}, optionally minus contexts
                List<Node> neigh = new ArrayList<>(adjs);
                neigh.remove(x);
                if (excludeContextsFromS) neigh.removeAll(ctx);

                tryOrientC1PerEdgeStrong(x, y, neigh, ctx);
            }
        }

        // Final safety net: propagate & ensure strong legality
        pag = propagator.apply(pag);
        if (!strongPagLegality.apply(pag)) {
            while (!undoStack.isEmpty() && !strongPagLegality.apply(pag)) {
                undoStack.pop().run();
                pag = propagator.apply(pag);
            }
        }
    }

    // ---- Internals ----

    // Attempt C1-like orientation for (x,y) using per-edge strong legality gating.
    private void tryOrientC1PerEdgeStrong(Node x, Node y, List<Node> neigh, List<Node> contexts) {
        for (Set<Node> S0 : SmallSubsetIter.subsets(neigh, maxSubsetSize)) {
            // Work on a copy; never mutate iterator's set
            Set<Node> S = new LinkedHashSet<>(S0);

            // Require: Y shows change under S
            if (!oracle.changes(y, S)) continue;

            // If adding X stabilizes across all contexts, propose X o-> Y
            Set<Node> SplusX = plus(S, x);
            if (!oracle.stable(y, SplusX)) continue;

            // Optional proxy guard: at least one context alone stabilizes Y
            if (useProxyGuard && !contexts.isEmpty()) {
                boolean someContextStabilizes = false;
                for (Node c : contexts) {
                    if (oracle.stable(y, plus(S, c))) {
                        someContextStabilizes = true;
                        break;
                    }
                }
                if (!someContextStabilizes) continue;
            }

            // Tentatively orient arrowhead at child Y (X o-> Y)
            addArrowheadAt(pag, x, y);

            // Propagate and enforce strong fixed-point legality immediately
            pag = propagator.apply(pag);
            if (!strongPagLegality.apply(pag)) {
                // Roll back just this arrowhead and continue trying other S
                undoLast();
                pag = propagator.apply(pag);
                continue;
            }

            // Keep this orientation; move on to next (x,y)
            return;
        }
    }

    /**
     * Add an arrowhead at y on edge (x,y); record undo. (Arrowheads-only; preserves the other endpoint.)
     */
    private void addArrowheadAt(Graph g, Node x, Node y) {
        Endpoint oldXY = g.getEndpoint(x, y);
        Endpoint oldYX = g.getEndpoint(y, x);
        if (oldXY == Endpoint.ARROW) return; // already has head at y

        g.setEndpoint(x, y, Endpoint.ARROW); // X *-> Y
        undoStack.push(() -> {
            g.setEndpoint(x, y, oldXY);
            g.setEndpoint(y, x, oldYX);
        });
    }

    private void undoLast() {
        if (!undoStack.isEmpty()) undoStack.pop().run();
    }
}