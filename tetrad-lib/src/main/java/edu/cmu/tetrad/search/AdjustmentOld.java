package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * The Adjustment class provides methods and utilities for computing adjustment sets in causal inference
 * using graph-based methods. It supports tasks such as identifying minimal adjustment sets,
 * determining forbidden nodes for adjustment, and navigating paths and reachability in various graph types.
 * These computations are based on the properties of possibly-directed graphs, backdoor criteria, and
 * other rules from causal inference literature.
 */
public final class AdjustmentOld {

    // ---- New collider-bias policy ---------------------------------------------------------------

    /**
     * The {@code graph} field represents the primary graph structure associated
     * with the {@code Adjustment} instance. It is used in various calculations and
     * operations throughout the class methods to determine adjustment sets, causal
     * relationships, and graph-based policies.
     * <p>
     * This field is final and immutable after the initialization of the {@code Adjustment}
     * instance, ensuring consistency for all operations that rely on the graph.
     */
    private final Graph graph;
    /**
     * Specifies the collider preference policy used for selecting blockers on a backdoor witness.
     * Determines whether noncolliders, colliders, or a mix of both are preferred during adjustment
     * calculations. Defaults to {@code NONCOLLIDER_FIRST}, meaning noncolliders and ambiguous
     * nodes are prioritized initially, and colliders are only utilized if necessary.
     */
    private ColliderPolicy colliderPolicy = ColliderPolicy.NONCOLLIDER_FIRST;
    /**
     * <p>Defines the policy for handling cases where no amenable adjustment paths are
     * found during adjustment-set computation. Defaults to {@link NoAmenablePolicy#SEARCH}.</p>
     *
     * <p>The policy determines how the search proceeds when amenable paths are not identified:</p>
     * <ul>
     *   <li>{@link NoAmenablePolicy#SEARCH}: Continue searching for adjustment possibilities.</li>
     *   <li>{@link NoAmenablePolicy#RETURN_EMPTY_SET}: Return the empty set when no amenable paths are found.</li>
     *   <li>{@link NoAmenablePolicy#SUPPRESS}: Suppress any result for the pair (i.e., return no sets).</li>
     * </ul>
     *
     * <p>Configure this via {@code setNoAmenablePolicy}.</p>
     */
    private NoAmenablePolicy noAmenablePolicy = NoAmenablePolicy.SEARCH;

    /**
     * Constructor for the Adjustment class, initializing it with the given graph.
     *
     * @param graph the graph instance to be used for adjustment calculations
     */
    public AdjustmentOld(Graph graph) {
        this.graph = graph;
    }

    private static Set<Node> amenableBackbone(Set<List<Node>> amenable, Node X, Node Y) {
        if (amenable == null || amenable.isEmpty()) return Collections.emptySet();
        LinkedHashSet<Node> s = new LinkedHashSet<>();
        for (List<Node> p : amenable) {
            for (int i = 1; i < p.size() - 1; i++) {
                Node v = p.get(i);
                if (v != X && v != Y) s.add(v);
            }
        }
        return s;
    }

    /**
     * Compute Forb_G(X,Y) (Perković et al., 2018).
     */
    private static Set<Node> getForbiddenForAdjustment(Graph G, String graphType, Node X, Node Y) {
        Objects.requireNonNull(G);
        Objects.requireNonNull(X);
        Objects.requireNonNull(Y);
        if (X == Y) return Collections.emptySet();

        final String gt = graphType == null ? "DAG" : graphType.toUpperCase(Locale.ROOT);

        Set<Node> fwdFromX = forwardReach(G, gt, Set.of(X));
        Set<Node> canReachY = backwardFilterByForwardRule(G, gt, Y);

        Set<Node> onSomePDPath = new LinkedHashSet<>(fwdFromX);
        onSomePDPath.retainAll(canReachY);
        onSomePDPath.remove(X);

        Set<Node> seeds = new LinkedHashSet<>();
        seeds.add(X);
        seeds.addAll(onSomePDPath);

        Set<Node> forb = forwardReach(G, gt, seeds);
        forb.remove(X);
        forb.remove(Y);
        return forb;
    }

    // ---------------------------------------------------------------------------------------------

    private static Set<Node> forwardReach(Graph G, String graphType, Set<Node> starts) {
        Deque<Node> q = new ArrayDeque<>(starts);
        Set<Node> seen = new LinkedHashSet<>(starts);
        while (!q.isEmpty()) {
            Node a = q.removeFirst();
            for (Node b : G.getAdjacentNodes(a)) {
                Edge e = G.getEdge(a, b);
                if (e == null) continue;
                if (!isPossiblyOutEdge(graphType, e, a, b)) continue;
                if (seen.add(b)) q.addLast(b);
            }
        }
        return seen;
    }

    private static boolean isPossiblyOutEdge(String graphType, Edge e, Node a, Node b) {
        if (e.pointsTowards(a)) return false;
        if (Edges.isBidirectedEdge(e)) return false;
        if (Edges.isUndirectedEdge(e)) return true;
        return true;
    }

    // ---- Public API ---------------------------------------------------------------------------

    private static Set<Node> backwardFilterByForwardRule(Graph G, String graphType, Node Y) {
        Set<Node> canReach = new LinkedHashSet<>();
        Deque<Node> q = new ArrayDeque<>();
        canReach.add(Y);
        q.add(Y);

        while (!q.isEmpty()) {
            Node b = q.removeFirst();
            for (Node a : G.getAdjacentNodes(b)) {
                Edge e = G.getEdge(a, b);
                if (e == null) continue;
                if (!isPossiblyOutEdge(graphType, e, a, b)) continue;
                if (canReach.add(a)) q.addLast(a);
            }
        }
        return canReach;
    }

    /**
     * Sets the collider policy for the Adjustment instance to guide backdoor adjustment
     * calculations. The policy determines how colliders are handled during the selection
     * of blockers on backdoor paths. Default NONCOLLIDER_FIRST.
     *
     * @param p the collider policy to be applied, must not be null
     * @return the Adjustment instance with the updated collider policy
     */
    public AdjustmentOld setColliderPolicy(ColliderPolicy p) {
        this.colliderPolicy = Objects.requireNonNull(p);
        return this;
    }

    /**
     * Sets the no-amenable policy for the Adjustment instance. This policy determines
     * how situations with no amenable adjustment sets should be handled.
     * Default SEARCH.
     *
     * @param p the no-amenable policy to apply, must not be null
     * @return the Adjustment instance with the updated no-amenable policy
     */
    public AdjustmentOld setNoAmenablePolicy(NoAmenablePolicy p) {
        this.noAmenablePolicy = Objects.requireNonNull(p);
        return this;
    }

    /**
     * Computes a list of potential adjustment sets that, if conditioned upon, are capable
     * of blocking backdoor paths between the given nodes X and Y in a graph. The adjustment
     * sets are computed based on the provided graph type, path constraints, and specified
     * policies for handling colliders.
     *
     * @param X                 The source node from which the paths originate.
     * @param Y                 The target node to which the paths lead.
     * @param graphType         The type of graph (e.g., directed acyclic graph) used for the adjustment calculation.
     * @param maxNumSets        The maximum number of adjustment sets to return.
     * @param maxRadius         The maximum radius considered in the search for adjustment sets.
     * @param nearWhichEndpoint Specifies which endpoint (X or Y) to prioritize in the search.
     * @param maxPathLength     The maximum length of paths to consider in the calculation.
     * @return A list of sets of nodes, each representing a valid adjustment set that satisfies the backdoor
     * adjustment criteria, or an empty list if no such set exists.
     */
    public List<Set<Node>> adjustmentSets(Node X, Node Y, String graphType,
                                          int maxNumSets, int maxRadius,
                                          int nearWhichEndpoint, int maxPathLength) {
        return adjustmentSets(X, Y, graphType, maxNumSets, maxRadius, nearWhichEndpoint, maxPathLength, this.colliderPolicy);
    }

    /**
     * Computes the valid adjustment sets for estimating the causal effect of X on Y
     * in a graph. The adjustment sets are subsets of variables that satisfy the backdoor
     * criterion, ensuring the causal effect can be estimated without confounding biases.
     *
     * @param X The node representing the cause variable.
     * @param Y The node representing the effect variable.
     * @param graphType A string specifying the type of the graph (e.g., DAG, PAG).
     * @param maxNumSets The maximum number of adjustment sets to be returned.
     * @param maxRadius The maximum radius around X and Y to search for adjustment sets.
     * @param nearWhichEndpoint Specifies which endpoint of the edges to consider when searching
     *                          for near variables, typically represented as an integer flag.
     * @param maxPathLength The maximum length of paths to consider when searching for adjustment
     *                      sets.
     * @param colliderPolicy The policy for handling colliders in the graph.
     * @return A list of sets of variables, where each set represents a valid adjustment set
     *         satisfying the backdoor criterion.
     */
    public List<Set<Node>> adjustmentSets(Node X, Node Y, String graphType,
                                          int maxNumSets, int maxRadius,
                                          int nearWhichEndpoint, int maxPathLength,
                                          ColliderPolicy colliderPolicy) {
        return adjustmentSets(X, Y, graphType, maxNumSets, maxRadius, nearWhichEndpoint, maxPathLength,
                colliderPolicy, true);
    }

    /**
     * Computes a list of adjustment sets for a given pair of nodes in a graph, subject to various criteria and constraints.
     * Adjustment sets are used in causal inference to control for confounding variables between a source node and a target node.
     *
     * @param X the source node for which the adjustment sets are to be computed
     * @param Y the target node for which the adjustment sets are to be computed
     * @param graphType the type of the graph on which the adjustment sets computation is based
     * @param maxNumSets the maximum number of adjustment sets to compute
     * @param maxRadius the maximum search radius around the nodes for consideration in the adjustment set
     * @param nearWhichEndpoint specifies which endpoint of the connection to prioritize during adjustment set creation
     * @param maxPathLength the maximum length of paths to consider in the graph while finding adjustment sets
     * @param colliderPolicy the policy that determines how colliders should be handled during adjustment set computation
     * @param avoidAmenable a boolean flag indicating whether adjustment sets involving certain amenable nodes should be avoided
     * @return a list of sets of nodes, where each set represents an adjustment set that satisfies the given constraints
     */
    public List<Set<Node>> adjustmentSets(Node X, Node Y, String graphType,
                                          int maxNumSets, int maxRadius,
                                          int nearWhichEndpoint, int maxPathLength,
                                          ColliderPolicy colliderPolicy, boolean avoidAmenable) {
        List<Set<Node>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        var ctx = precomputeContext(X, Y, graphType, maxRadius, nearWhichEndpoint, maxPathLength, avoidAmenable);

        Deque<Set<Node>> bans = new ArrayDeque<>();
        bans.add(Collections.emptySet());

        while (!bans.isEmpty() && out.size() < maxNumSets) {
            Set<Node> ban = bans.removeFirst();

            LinkedHashSet<Node> Z = solveOnce(ctx, ban, colliderPolicy);
            if (Z == null) continue;

            String key = keyOf(Z);
            if (seen.add(key)) {
                out.add(Z);
                for (Node v : Z) {
                    Set<Node> ban2 = new LinkedHashSet<>(ban);
                    ban2.add(v);
                    bans.addLast(ban2);
                }
            }
        }
        return out;
    }

    private PrecomputeContext precomputeContext(Node X, Node Y, String graphType,
                                                int maxRadius, int nearWhichEndpoint,
                                                int maxPathLength, boolean avoidAmenable) {
        if (X == null || Y == null || X == Y) throw new IllegalArgumentException("X and Y must differ.");
        if (maxRadius < 0) maxRadius = graph.getNodes().size();    // full reach

        // 1) Amenable (PD-out-of-X) routes we don't want to accidentally block
        Set<List<Node>> amenable = avoidAmenable ? getAmenablePaths(X, Y, graphType, maxPathLength) : new HashSet<>();

        // Backbone = interior nodes on amenable paths (excluding X,Y)
        Set<Node> amenableBackbone = amenableBackbone(amenable, X, Y);

        // 2) Forbidden (Perković GAC)
        Set<Node> forbidden = getForbiddenForAdjustment(graph, graphType, X, Y);

        // 3) Endpoint-local shells
        List<Node> starts = firstBackdoorNeighbors(X, Y, graphType);
        Shells shellsFromX = starts.isEmpty()
                ? new Shells(emptyLayers(maxRadius), Set.of())
                : backdoorShellsFromX(X, starts, maxRadius);
        Shells shellsFromY = undirectedShells(Y, maxRadius);

        // Reachability filter
        Set<Node> reachX = shellsFromX.reach;
        Set<Node> reachY = shellsFromY.reach;

        // 4) Candidate pool (local, reachable, not forbidden, not endpoints)
        LinkedHashSet<Node> poolSet = new LinkedHashSet<>();
        for (int r = 1; r <= maxRadius; r++) {
            if (nearWhichEndpoint == 1 || nearWhichEndpoint == 3) {
                poolSet.addAll(shellsFromX.layers[r]);
            }
            if (nearWhichEndpoint == 2 || nearWhichEndpoint == 3) {
                poolSet.addAll(shellsFromY.layers[r]);
            }
        }
//        poolSet.retainAll(reachX);
//        poolSet.retainAll(reachY);

        // Replace the two retains with:
        if (nearWhichEndpoint == 1) {
            poolSet.retainAll(reachX);
        } else if (nearWhichEndpoint == 2) {
            poolSet.retainAll(reachY);
        } else { // 3 = both
            poolSet.retainAll(reachX);
            poolSet.retainAll(reachY);
        }

        poolSet.remove(X);
        poolSet.remove(Y);
        poolSet.removeAll(forbidden);

        // SAFETY: never adjust on interior of amenable PD-out-of-X routes.
        poolSet.removeAll(amenableBackbone);

        // 5) Stable order
        List<Node> pool = new ArrayList<>(poolSet);
        pool.sort(Comparator
                .comparingInt((Node v) -> approxEndpointDistance(v, X, Y, shellsFromX, shellsFromY))
                .thenComparingInt(v -> graph.getAdjacentNodes(v).size())
                .thenComparing(Node::getName));

        pool.sort(Comparator
                .comparingInt((Node v) -> {
                    if (nearWhichEndpoint == 1) return endpointDistance(v, shellsFromX);
                    if (nearWhichEndpoint == 2) return endpointDistance(v, shellsFromY);
                    return Math.min(endpointDistance(v, shellsFromX), endpointDistance(v, shellsFromY));
                })
                .thenComparingInt((Node v) -> graph.getAdjacentNodes(v).size())
                .thenComparing(Node::getName));

        // 6) Index maps (kept for future BitSet paths / caching)
        Map<Node, Integer> idx = new HashMap<>(pool.size() * 2);
        Map<Node, Integer> order = new HashMap<>(pool.size() * 2);
        for (int i = 0; i < pool.size(); i++) {
            idx.put(pool.get(i), i);
            order.put(pool.get(i), i);
        }

        return new PrecomputeContext(X, Y, graphType, maxRadius, nearWhichEndpoint, maxPathLength,
                amenable, amenableBackbone, forbidden, shellsFromX, shellsFromY, pool, idx, order);
    }

    private int endpointDistance(Node v, Shells s) {
        for (int r = 1; r < s.layers.length; r++) if (s.layers[r].contains(v)) return r;
        return Integer.MAX_VALUE / 2;
    }

    private Set<List<Node>> getAmenablePaths(Node source, Node target, String graphType, int maxLength) {
        Adjustment.GraphType _graphType = Adjustment.GraphType.valueOf(graphType);
        if (source == null || target == null || source == target) return Collections.emptySet();
        if (_graphType == Adjustment.GraphType.PAG) {
            return graph.paths().getAmenablePathsPag(source, target, maxLength);
        } else {
            return graph.paths().getAmenablePathsPdagMag(source, target, maxLength);
        }
    }

    private @NotNull List<Node> firstBackdoorNeighbors(Node X, Node Y, String graphType) {
        boolean isPAG = "PAG".equalsIgnoreCase(graphType);
        List<Node> starts = new ArrayList<>();
        for (Node W : graph.getAdjacentNodes(X)) {
            Edge e = graph.getEdge(X, W);
            if (e == null) continue;
            if (!isPAG) {
                if (e.pointsTowards(X)) starts.add(W);                 // DAG/PDAG/MAG: X <- W
            } else {
                boolean intoX = e.pointsTowards(X);
                boolean undOrBi = Edges.isUndirectedEdge(e) || Edges.isBidirectedEdge(e);
                boolean wToX = graph.paths().existsDirectedPath(W, X);
                boolean wToY = graph.paths().existsDirectedPath(W, Y);
                if (intoX || (undOrBi && (wToX || wToY))) starts.add(W);
            }
        }
        return starts;
    }

    @SuppressWarnings("unchecked")
    private List<Node>[] emptyLayers(int maxRadius) {
        if (maxRadius < 0) maxRadius = 0;
        List<Node>[] layers = (List<Node>[]) new List[maxRadius + 1];
        for (int i = 0; i <= maxRadius; i++) {
            layers[i] = new ArrayList<>();
        }
        return layers;
    }

    /**
     * Find ONE minimal adjustment set under the given ban set.
     * Returns null if no solution exists with these bans.
     */
    private @Nullable LinkedHashSet<Node> solveOnce(PrecomputeContext ctx, Set<Node> ban,
                                                    ColliderPolicy colliderPolicy) {
        final Node X = ctx.X, Y = ctx.Y;
        final String graphType = ctx.graphType;

        final List<Node> pool = ctx.pool;
        final Set<Node> poolSet = new HashSet<>(pool);
        final Set<Node> forbidden = ctx.forbidden;

        if (ctx.amenable.isEmpty()) {
            switch (noAmenablePolicy) {
                case RETURN_EMPTY_SET:
                    return new LinkedHashSet<>(Collections.emptySet());
                case SUPPRESS:
                    return new LinkedHashSet<>();
                case SEARCH:
                default:
                    // keep going; solver may return {}
            }
        }

        LinkedHashSet<Node> Z = new LinkedHashSet<>();

        // 1) Witness-guided growth
        while (true) {
            Optional<List<Node>> wit = findBackdoorWitness(X, Y, Z, graphType, ctx.maxPathLength);
            if (wit.isEmpty()) break; // all backdoors blocked

            Node pick = chooseBlockerOnWitness(
                    wit.get(), pool, poolSet, forbidden, ban, Z, ctx.amenableBackbone, graphType, colliderPolicy
            );

            if (pick == null) return null; // no allowable blocker under this ban
            Z.add(pick);
        }

        // 2) Minimality trim (try-delete)
//        if (!Z.isEmpty()) {
//            LinkedHashSet<Node> Zmin = new LinkedHashSet<>(Z);
//            for (Node v : new ArrayList<>(Zmin)) {
//                Zmin.remove(v);
//                if (findBackdoorWitness(X, Y, Zmin, graphType, ctx.maxPathLength).isPresent()) {
//                    Zmin.add(v);
//                }
//            }
//            Z = Zmin;
//        }

        boolean changed;
        do {
            changed = false;
            for (Node v : new ArrayList<>(Z)) {
                Z.remove(v);
                if (findBackdoorWitness(X, Y, Z, graphType, ctx.maxPathLength).isPresent()) {
                    Z.add(v);
                } else {
                    changed = true;
                }
            }
        } while (changed);

        return Z;
    }

    // ---- NEW: role-aware candidate ranking on a witness ---------------------------------------

    private Optional<List<Node>> findBackdoorWitness(Node X, Node Y,
                                                     Set<Node> Z,
                                                     String graphType,
                                                     int maxPathLength) {
        List<Node> starts = firstBackdoorNeighbors(X, Y, graphType);
        if (starts.isEmpty()) return Optional.empty();

        final int edgeLimit = (maxPathLength < 0) ? Integer.MAX_VALUE : maxPathLength;

        for (Node w : starts) {
            LinkedList<Node> path = new LinkedList<>(List.of(X, w));
            HashSet<Node> inPath = new HashSet<>(List.of(X, w));
            if (dfsWitness(path, inPath, Y, Z, graphType, edgeLimit)) {
                return Optional.of(new ArrayList<>(path));
            }
        }
        return Optional.empty();
    }

    /**
     * Determine node’s role (collider / noncollider / ambiguous) on the given witness path.
     */
    private RoleOnWitness roleOnWitness(Node v, List<Node> witness) {
        int k = witness.indexOf(v);
        if (k < 0) return RoleOnWitness.OFFPATH;
        if (k == 0 || k == witness.size() - 1) return RoleOnWitness.ENDPOINT; // X or Y
        Node a = witness.get(k - 1), b = v, c = witness.get(k + 1);

        boolean collider = graph.isDefCollider(a, b, c);
        boolean defNon = graph.isDefNoncollider(a, b, c);

        if (collider) return RoleOnWitness.COLLIDER;
        if (defNon) return RoleOnWitness.NONCOLLIDER;
        return RoleOnWitness.AMBIGUOUS;
    }

    /**
     * Pick a blocker on the current witness path, honoring colliderPolicy.
     * Guard: never pick forbidden nodes, amenable backbone, or banned nodes.
     */
    private @Nullable Node chooseBlockerOnWitness(
            List<Node> witness,
            List<Node> pool,
            Set<Node> poolSet,
            Set<Node> forbidden,
            Set<Node> ban,
            Set<Node> Z,
            Set<Node> amenableBackbone,
            String graphType,
            ColliderPolicy colliderPolicy
    ) {
        Set<Node> inWitness = new HashSet<>(witness);

        // Build candidate list from pool ∩ witness (fallback to witness order if pool empty for those)
        List<Node> candidates = new ArrayList<>();
        for (Node v : pool) {
            if (inWitness.contains(v)
                    && !forbidden.contains(v)
                    && !amenableBackbone.contains(v)
                    && !Z.contains(v)
                    && !ban.contains(v)) {
                candidates.add(v);
            }
        }
        if (candidates.isEmpty()) {
            for (Node v : witness) {
                if (poolSet.contains(v)
                        && !forbidden.contains(v)
                        && !amenableBackbone.contains(v)
                        && !Z.contains(v)
                        && !ban.contains(v)) {
                    candidates.add(v);
                }
            }
        }
        if (candidates.isEmpty()) return null;

        // If NONCOLLIDER_FIRST: filter to NONCOLLIDER or AMBIGUOUS; if none, then allow colliders.
        if (colliderPolicy == ColliderPolicy.NONCOLLIDER_FIRST) {
            List<Node> noncolOnly = new ArrayList<>();
            for (Node v : candidates) {
                RoleOnWitness r = roleOnWitness(v, witness);
                if (r == RoleOnWitness.NONCOLLIDER || r == RoleOnWitness.AMBIGUOUS) {
                    noncolOnly.add(v);
                }
            }
            if (!noncolOnly.isEmpty()) candidates = noncolOnly;
        }

        // Rank: primary = role score (noncollider > ambiguous > collider), then stable pool order,
        // then degree (lower first), then name.
        candidates.sort((a, b) -> {
            int ra = roleScore(roleOnWitness(a, witness), colliderPolicy);
            int rb = roleScore(roleOnWitness(b, witness), colliderPolicy);
            if (ra != rb) return Integer.compare(rb, ra); // higher score first
            int ia = pool.indexOf(a), ib = pool.indexOf(b);
            if (ia != ib) return Integer.compare(ia, ib);
            int da = graph.getAdjacentNodes(a).size(), db = graph.getAdjacentNodes(b).size();
            if (da != db) return Integer.compare(da, db);
            return a.getName().compareTo(b.getName());
        });

        return candidates.get(0);
    }

    /**
     * Map role → score given the policy (higher = better).
     */
    private int roleScore(RoleOnWitness r, ColliderPolicy policy) {
        // Base scores encode the intuition:
        // NONCOLLIDER: strongly preferred; AMBIGUOUS: mild positive; COLLIDER: negative.
        int base = switch (r) {
            case NONCOLLIDER -> 100;
            case AMBIGUOUS -> 30;
            case COLLIDER -> -80;
            case ENDPOINT -> -1000; // endpoints are never adjusted; should be filtered
            case OFFPATH -> -200;
        };
        // Policy modulation: OFF → dampen; PREFER → keep; NONCOLLIDER_FIRST handled via filtering already.
        return switch (policy) {
            case OFF ->
                // Soften the effect but keep the shape
                    (int) Math.round(base * 0.3);
            case PREFER_NONCOLLIDERS -> base;
            default -> base; // filtering already applied
        };
    }

    // ---- Path openness check used by DFS ------------------------------------------------------

    private boolean dfsWitness(LinkedList<Node> path, Set<Node> inPath, Node Y,
                               Set<Node> Z, String graphType, int edgeLimit) {
        if (path.size() - 1 > edgeLimit) return false;

        Node tail = path.getLast();
        if (tail.equals(Y)) return true;

        for (Edge e : graph.getEdges(tail)) {
            Node nxt = Edges.traverse(tail, e);
            if (nxt == null || inPath.contains(nxt)) continue;

            if (path.size() >= 2) {
                Node prev = path.get(path.size() - 2);
                if (!tripleKeepsOpen(prev, tail, nxt, Z)) continue;
            }

            path.addLast(nxt);
            inPath.add(nxt);
            if (dfsWitness(path, inPath, Y, Z, graphType, edgeLimit)) return true;
            inPath.remove(nxt);
            path.removeLast();
        }
        return false;
    }

    // Same stance you had: avoid blocking through noncolliders in Z; colliders open only if in Z or ancestor-of-Z.
    private boolean tripleKeepsOpen(Node a, Node b, Node c, Set<Node> Z) {
        boolean collider = graph.isDefCollider(a, b, c);
        boolean defNoncollider = graph.isDefNoncollider(a, b, c);

        if (collider) {
            return Z.contains(b) || graph.paths().isAncestorOfAnyZ(b, Z);
        } else if (defNoncollider) {
            return !Z.contains(b);
        } else {
            return !Z.contains(b);
        }
    }

    // ---- Neighborhood shell builders ----------------------------------------------------------

    private Shells backdoorShellsFromX(Node X, List<Node> starts, int maxRadius) {
        @SuppressWarnings("unchecked")
        List<Node>[] layers = new ArrayList[maxRadius + 1];
        for (int i = 0; i <= maxRadius; i++) layers[i] = new ArrayList<>();

        Set<Node> visited = new HashSet<>();
        Deque<Node> q = new ArrayDeque<>();
        Map<Node, Integer> dist = new HashMap<>();

        visited.add(X);
        for (Node s : starts) {
            if (visited.add(s)) {
                q.add(s);
                dist.put(s, 1);
                layers[1].add(s);
            }
        }

        while (!q.isEmpty()) {
            Node u = q.remove();
            int du = dist.get(u);
            if (du >= maxRadius) continue;
            for (Node v : graph.getAdjacentNodes(u)) {
                if (visited.add(v)) {
                    dist.put(v, du + 1);
                    if (du + 1 <= maxRadius) layers[du + 1].add(v);
                    q.add(v);
                }
            }
        }
        Set<Node> reach = dist.keySet();
        return new Shells(layers, reach);
    }

    private Shells undirectedShells(Node seed, int maxRadius) {
        @SuppressWarnings("unchecked")
        List<Node>[] layers = new ArrayList[maxRadius + 1];
        for (int i = 0; i <= maxRadius; i++) layers[i] = new ArrayList<>();

        Set<Node> visited = new HashSet<>();
        Deque<Node> q = new ArrayDeque<>();
        Map<Node, Integer> dist = new HashMap<>();

        visited.add(seed);
        q.add(seed);
        dist.put(seed, 0);

        while (!q.isEmpty()) {
            Node u = q.remove();
            int du = dist.get(u);
            if (du < maxRadius) {
                for (Node v : graph.getAdjacentNodes(u)) {
                    if (visited.add(v)) {
                        dist.put(v, du + 1);
                        layers[du + 1].add(v);
                        q.add(v);
                    }
                }
            }
        }
        return new Shells(layers, visited);
    }

    private int approxEndpointDistance(Node v, Node X, Node Y, Shells sx, Shells sy) {
        int best = Integer.MAX_VALUE;
        for (int r = 1; r < sx.layers.length; r++) if (sx.layers[r].contains(v)) best = Math.min(best, r);
        for (int r = 1; r < sy.layers.length; r++) if (sy.layers[r].contains(v)) best = Math.min(best, r);
        return best == Integer.MAX_VALUE ? 1_000_000_000 : best;
    }

    // ---- Utilities ---------------------------------------------------------------------------

    private String keyOf(Set<Node> Z) {
        return Z.stream().map(Node::getName).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    /**
     * Collider preference policy for selecting blockers on a backdoor witness.
     */
    public enum ColliderPolicy {
        /**
         * No bias; original behavior.
         */
        OFF,
        /**
         * Prefer noncolliders; colliders allowed but penalized.
         */
        PREFER_NONCOLLIDERS,
        /**
         * First try using noncolliders (and ambiguous) only; if impossible, allow colliders.
         */
        NONCOLLIDER_FIRST
    }

    /**
     * Policy for handling non-amenable paths during adjustment.
     */
    public enum NoAmenablePolicy {
        /**
         * Represents a policy option where non-amenable paths are further analyzed
         * or searched for potential adjustment solutions. This policy attempts to
         * find compatible adjustments instead of immediately discarding or suppressing them.
         */
        SEARCH,

        /**
         * Represents a policy option where non-amenable paths are immediately discarded,
         * returning an empty set as the result. This policy does not attempt further analysis
         * or adjustments for incompatible paths.
         */
        RETURN_EMPTY_SET,

        /**
         * Represents a policy option where non-amenable paths are suppressed,
         * preventing them from being processed or impacting the overall adjustment logic.
         * This policy effectively ignores such paths without returning explicit results.
         */
        SUPPRESS}

    /**
     * Represents the role of a node along a witness path in a graph.
     * The role is used to categorize the node's behavior concerning
     * backdoor adjustment calculations and path evaluation.
     */
    private enum RoleOnWitness {

        /**
         * Represents the role of a node along a witness path in a graph.
         * The role is used to categorize the node's behavior concerning
         * backdoor adjustment calculations and path evaluation.
         */
        ENDPOINT,

        /**
         * Represents a node in a graph that acts as a collider along a witness path.
         * A collider is a node where two directed edges meet head-to-head. It is
         * significant in path analysis and backdoor adjustment calculations,
         * particularly for determining path activation and d-separation.
         */
        COLLIDER,

        /**
         * Represents a node in a graph that does not act as a collider along
         * a witness path. A non-collider is a node where directed edges do
         * not meet head-to-head. It is used for path analysis and backdoor
         * adjustment calculations, particularly for assessing path behavior
         * and determining d-separation.
         */
        NONCOLLIDER,

        /**
         * Represents a node in a graph where its role along a witness path cannot be
         * clearly categorized as either a collider or non-collider. The ambiguity arises
         * due to the specific structure or context of the graph, making its exact role
         * indeterminate in the context of backdoor adjustment calculations and path analysis.
         */
        AMBIGUOUS,

        /**
         * Indicates that a node is not part of the current witness path in the graph.
         * Nodes with this role are excluded from consideration in backdoor adjustment
         * calculations and path evaluations as they do not contribute to the analysis
         * of the specified path.
         */
        OFFPATH}

    private static final class PrecomputeContext {
        public final Node X, Y;
        public final String graphType;
        public final int maxRadius, nearWhichEndpoint, maxPathLength;

        public final Set<List<Node>> amenable;       // PD-out-of-X paths
        public final Set<Node> amenableBackbone;      // interior nodes to avoid adjusting on
        public final Set<Node> forbidden;             // GAC forbidden
        public final Shells shellsFromX;
        public final Shells shellsFromY;
        public final List<Node> pool;
        public final Map<Node, Integer> idx;
        public final Map<Node, Integer> order;

        public PrecomputeContext(Node X, Node Y, String graphType,
                                 int maxRadius, int nearWhichEndpoint, int maxPathLength,
                                 Set<List<Node>> amenable,
                                 Set<Node> amenableBackbone,
                                 Set<Node> forbidden,
                                 Shells shellsFromX, Shells shellsFromY,
                                 List<Node> pool,
                                 Map<Node, Integer> idx, Map<Node, Integer> order) {
            this.X = X;
            this.Y = Y;
            this.graphType = graphType;
            this.maxRadius = maxRadius;
            this.nearWhichEndpoint = nearWhichEndpoint;
            this.maxPathLength = maxPathLength;
            this.amenable = amenable;
            this.amenableBackbone = amenableBackbone;
            this.forbidden = forbidden;
            this.shellsFromX = shellsFromX;
            this.shellsFromY = shellsFromY;
            this.pool = pool;
            this.idx = idx;
            this.order = order;
        }
    }

    private record Shells(List<Node>[] layers, Set<Node> reach) {
    }
}