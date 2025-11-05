package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Represents an adjustment mechanism for defining adjustment sets in causal inference
 * across different types of graphical models.
 * <p>
 * The {@code RecursiveAdjustment} class provides methods to define valid adjustment sets based on
 * the structure of the graph, specific node relationships, and user-defined policies
 * about allowed paths and collateral factors.
 * </p>
 *
 * <ul>
 *   <li><code>graph</code>: The causal graph being analyzed.</li>
 *   <li><code>colliderPolicy</code>: A policy defining how colliders are handled during
 *       adjustment set definition.</li>
 *   <li><code>noAmenablePolicy</code>: A policy specifying behavior when the graph is
 *       not adjustment-amenable relative to (X, Y) in the sense of Perković et al.</li>
 * </ul>
 */
public final class RecursiveAdjustment {

    /**
     * Represents the type of graph being analyzed for adjustment set definition.
     */
    public enum GraphType{

        /**
         * Represents a Partially Directed Acyclic Graph (PDAG) within the context of the graph analysis.
         * A PDAG is a graph type used for describing the structure of causal relationships
         * while allowing some edges to remain undirected.
         */
        PDAG,

        /**
         * Represents a Maximal Ancestral Graph (MAG) within the context of the graph analysis.
         * A MAG is a graph type used to encode causal relationships, typically in the presence
         * of latent variables and selection bias. It allows the representation of both directed
         * and bidirectional edges while maintaining specific ancestral properties.
         */
        MAG,

        /**
         * Represents a Partial Ancestral Graph (PAG) within the context of the graph analysis.
         * A PAG is used to depict possible causal structures, accounting for latent variables
         * and selection bias. It is a graphical representation that includes directed, undirected,
         * and bidirectional edges, encapsulating causal and non-causal relationships in a compact form.
         */
        PAG}

    /**
     * Represents the causal graph being analyzed for adjustment set definition.
     */
    private final Graph graph;

    /**
     * Represents the policy for handling colliders during adjustment set definition.
     */
    private ColliderPolicy colliderPolicy = ColliderPolicy.NONCOLLIDER_FIRST;

    /**
     * Represents the policy for handling non-amenability (in Perković's sense) relative to (X, Y).
     */
    private NoAmenablePolicy noAmenablePolicy = NoAmenablePolicy.SEARCH;

    /**
     * Constructs a RecursiveAdjustment instance with the specified causal graph.
     *
     * @param graph The causal graph to be analyzed for adjustment set definition.
     */
    public RecursiveAdjustment(Graph graph) { this.graph = graph; }

    /**
     * Sets the collider policy for handling colliders during adjustment set definition.
     *
     * @param p The collider policy to be applied.
     * @return This RecursiveAdjustment instance for method chaining.
     */
    public RecursiveAdjustment setColliderPolicy(ColliderPolicy p) {
        this.colliderPolicy = Objects.requireNonNull(p);
        return this;
    }

    /**
     * Sets the policy for handling cases where the graph is not amenable relative to (X, Y).
     *
     * @param p The no-amenable policy to be applied.
     * @return This RecursiveAdjustment instance for method chaining.
     */
    public RecursiveAdjustment setNoAmenablePolicy(NoAmenablePolicy p) {
        this.noAmenablePolicy = Objects.requireNonNull(p);
        return this;
    }

    /**
     * Checks whether the graph is adjustment-amenable relative to (X, Y)
     * in the sense of Perković et al.: every proper possibly-directed path
     * from X to Y starts with a visible / directed edge out of X.
     *
     * This uses the same notion of "amenable path" as the main algorithm:
     * - For PAGs: first edge must be visible and point away from X.
     * - For DAG/PDAG/MAG: first edge must be directed out of X.
     *
     * @param X             source node
     * @param Y             target node
     * @param graphType     graph type ("DAG", "PDAG", "MAG", "PAG", etc.)
     * @param maxPathLength maximum path length for possibly-directed paths; -1 means unlimited
     * @return true if the graph is amenable w.r.t. (X, Y), false otherwise
     */
    public boolean isGraphAmenable(Node X, Node Y, String graphType, int maxPathLength) {
        if (X == null || Y == null || X == Y) {
            throw new IllegalArgumentException("X and Y must be distinct non-null nodes.");
        }
        return isGraphAmenableInternal(X, Y, graphType, maxPathLength);
    }

    /**
     * Computes a list of adjustment sets for estimating the causal effect of X on Y
     * within a given graph structure using the Rubin-Bai / RB-style approach.
     *
     * @param X The node representing the cause in the causal relationship.
     * @param Y The node representing the effect in the causal relationship.
     * @param graphType The type of graph (e.g., "dag", "pdag", "mag") used for the adjustment computation.
     * @param maxNumSets The maximum number of adjustment sets to compute.
     * @param maxRadius The maximum radius for creating shells during adjustment set determination.
     * @param nearWhichEndpoint Specifies which endpoint (source or target) should dominate the adjustment determination.
     * @param maxPathLength The maximum allowed path length between X and Y for eligibility in adjustment sets.
     * @param avoidAmenable Whether to avoid adjustment sets involving amenable paths (RA-mode vs RB-mode).
     * @param notFollowed A set of nodes that should not be followed during path exploration in the graph. Optional.
     * @param containing A set of nodes that must be included in the adjustment sets. Optional.
     * @return A list of sets of nodes, where each set represents a possible valid adjustment set for the causal effect.
     */
    public List<Set<Node>> adjustmentSetsRB(Node X, Node Y, String graphType,
                                            int maxNumSets, int maxRadius,
                                            int nearWhichEndpoint, int maxPathLength,
                                            boolean avoidAmenable,
                                            @Nullable Set<Node> notFollowed,
                                            @Nullable Set<Node> containing) {
        return adjustmentSets(X, Y, graphType, maxNumSets, maxRadius, nearWhichEndpoint,
                maxPathLength, colliderPolicy, avoidAmenable, notFollowed, containing);
    }

    // --- Master entry point -----------------------------------------------------------------

    /**
     * Computes a list of adjustment sets for estimating the causal effect of X on Y
     * within a given graph structure. This method uses a breadth-first search strategy
     * to identify valid adjustment sets based on the provided parameters and collider policy.
     *
     * @param X The node representing the cause in the causal relationship.
     * @param Y The node representing the effect in the causal relationship.
     * @param graphType The type of graph (e.g., "dag", "pdag", "mag") used for adjustment computation.
     * @param maxNumSets The maximum number of adjustment sets to compute.
     * @param maxRadius The maximum radius for creating shells during adjustment set determination.
     * @param nearWhichEndpoint Specifies which endpoint (source or target) should dominate the adjustment determination.
     * @param maxPathLength The maximum allowed path length between X and Y for eligibility in adjustment sets.
     * @param colliderPolicy The policy for handling colliders during adjustment set computation.
     * @param avoidAmenable Whether to avoid adjustment sets involving amenable paths.
     * @param notFollowed A set of nodes that should not be followed during path exploration in the graph. Optional.
     * @param containing A set of nodes that must be included in the adjustment sets. Optional.
     * @return A list of sets of nodes, where each set represents a possible valid adjustment set for the causal effect.
     */
    public List<Set<Node>> adjustmentSets(Node X, Node Y, String graphType,
                                          int maxNumSets, int maxRadius,
                                          int nearWhichEndpoint, int maxPathLength,
                                          ColliderPolicy colliderPolicy, boolean avoidAmenable,
                                          @Nullable Set<Node> notFollowed,
                                          @Nullable Set<Node> containing) {
        List<Set<Node>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean rbMode = !avoidAmenable;

        var ctx = precomputeContext(X, Y, graphType, maxRadius, nearWhichEndpoint,
                maxPathLength, avoidAmenable, notFollowed, containing);

        Deque<Set<Node>> bans = new ArrayDeque<>();
        bans.add(Collections.emptySet());

        while (!bans.isEmpty() && out.size() < maxNumSets) {
            Set<Node> ban = bans.removeFirst();
            LinkedHashSet<Node> Z = solveOnce(ctx, ban, colliderPolicy, rbMode);
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

    // --- Precompute context -----------------------------------------------------------------

    private PrecomputeContext precomputeContext(Node X, Node Y, String graphType,
                                                int maxRadius, int nearWhichEndpoint,
                                                int maxPathLength, boolean avoidAmenable,
                                                @Nullable Set<Node> notFollowed,
                                                @Nullable Set<Node> containing) {
        boolean rbMode = !avoidAmenable;

        if (X == null || Y == null || X == Y)
            throw new IllegalArgumentException("X and Y must differ.");
        if (maxRadius < 0) maxRadius = graph.getNodes().size();

//        // 1. All possibly-directed (potentially-directed) paths from X to Y
//        Set<List<Node>> pdPaths = graph.paths().potentiallyDirectedPaths(X, Y, maxPathLength);
//
//        // 2. Subset of those that are "amenable paths" in your sense
//        Set<List<Node>> amenablePaths = getAmenablePaths(X, Y, graphType, maxPathLength);
//
//        // 3. Graph-level amenability in the sense of Perković et al.:
//        //    G is amenable w.r.t. (X, Y) iff every proper possibly-directed X→Y path
//        //    starts with a visible/directed edge out of X.
//        boolean graphAmenable = pdPaths.isEmpty() || pdPaths.equals(amenablePaths);

        // Path-level "amenable" set is still used for amenableBackbone,
        // but graph-level amenability is computed via a shared helper.
        Set<List<Node>> amenablePaths = getAmenablePaths(X, Y, graphType, maxPathLength);
        boolean graphAmenable = isGraphAmenableInternal(X, Y, graphType, maxPathLength);

        Set<Node> amenableBackbone = amenableBackbone(amenablePaths, X, Y);
        Set<Node> forbidden = getForbiddenForAdjustment(graph, graphType, X, Y);

        List<Node> starts = firstBackdoorNeighbors(X, Y, graphType);
        Shells shellsFromX = starts.isEmpty()
                ? new Shells(emptyLayers(maxRadius), Set.of())
                : backdoorShellsFromX(X, starts, maxRadius);
        Shells shellsFromY = undirectedShells(Y, maxRadius);

        LinkedHashSet<Node> poolSet = new LinkedHashSet<>();
        for (int r = 1; r <= maxRadius; r++) {
            if (nearWhichEndpoint == 1 || nearWhichEndpoint == 3)
                poolSet.addAll(shellsFromX.layers[r]);
            if (nearWhichEndpoint == 2 || nearWhichEndpoint == 3)
                poolSet.addAll(shellsFromY.layers[r]);
        }
        if (nearWhichEndpoint == 1) poolSet.retainAll(shellsFromX.reach);
        else if (nearWhichEndpoint == 2) poolSet.retainAll(shellsFromY.reach);
        else { poolSet.retainAll(shellsFromX.reach); poolSet.retainAll(shellsFromY.reach); }

        // In RB (sepset) mode, DO NOT prune candidates by Forb_G or amenable backbone.
        if (!rbMode) {
            poolSet.removeAll(forbidden);
            poolSet.removeAll(amenableBackbone);
        }

        if (notFollowed != null) poolSet.removeAll(notFollowed);

        if (rbMode) poolSet.add(Y);

        List<Node> pool = new ArrayList<>(poolSet);
        pool.sort(Comparator
                .comparingInt((Node v) -> {
                    int dx = endpointDistance(v, shellsFromX);
                    int dy = endpointDistance(v, shellsFromY);
                    return (nearWhichEndpoint == 1) ? dx :
                            (nearWhichEndpoint == 2 ? dy : Math.min(dx, dy));
                })
                .thenComparingInt(v -> graph.getAdjacentNodes(v).size())
                .thenComparing(Node::getName));

        Map<Node, Integer> idx = new HashMap<>();
        Map<Node, Integer> order = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            idx.put(pool.get(i), i);
            order.put(pool.get(i), i);
        }

        LinkedHashSet<Node> seedZ = new LinkedHashSet<>();
        if (containing != null)
            for (Node v : containing)
                if (poolSet.contains(v)) seedZ.add(v);

        return new PrecomputeContext(X, Y, graphType, maxRadius, nearWhichEndpoint,
                maxPathLength, amenablePaths, amenableBackbone, forbidden,
                shellsFromX, shellsFromY, pool, idx, order,
                notFollowed == null ? Set.of() : new HashSet<>(notFollowed),
                seedZ, rbMode, graphAmenable);
    }

    /**
     * Internal helper: compute graph-level amenability relative to (X, Y)
     * using potentiallyDirectedPaths and the path-level amenable test.
     */
    private boolean isGraphAmenableInternal(Node X, Node Y, String graphType, int maxPathLength) {
        // All possibly-directed (potentially-directed) paths from X to Y
        Set<List<Node>> pdPaths = graph.paths().potentiallyDirectedPaths(X, Y, maxPathLength);

        // Subset of those that are "amenable paths" in your sense
        Set<List<Node>> amenablePaths = getAmenablePaths(X, Y, graphType, maxPathLength);

        // G is amenable w.r.t. (X, Y) iff every possibly-directed path is amenable.
        // If there are no possibly-directed paths, amenability holds vacuously.
        return pdPaths.isEmpty() || pdPaths.equals(amenablePaths);
    }

    // Uses your existing graph.paths() helpers:
    private Set<List<Node>> getAmenablePaths(Node source, Node target, String graphType, int maxLength) {
        if (source == null || target == null || source == target) return Collections.emptySet();
        return ("PAG".equalsIgnoreCase(graphType))
                ? graph.paths().getAmenablePathsPag(source, target, maxLength)
                : graph.paths().getAmenablePathsPdagMag(source, target, maxLength);
    }

    /**
     * Compute Forb_G(X,Y) (Perković et al.).
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

    private @NotNull List<Node> firstBackdoorNeighbors(Node X, Node Y, String graphType) {
        final String gt = (graphType == null) ? "DAG" : graphType.toUpperCase(Locale.ROOT);
        boolean isPAG = "PAG".equalsIgnoreCase(gt);
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

    private RecursiveAdjustment.Shells backdoorShellsFromX(Node X, List<Node> starts, int maxRadius) {
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
        return new RecursiveAdjustment.Shells(layers, reach);
    }

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

    private static boolean isPossiblyOutEdge(String graphType, Edge e, Node a, Node b) {
        if (e.pointsTowards(a)) return false;
        if (Edges.isBidirectedEdge(e)) return false;
        if (Edges.isUndirectedEdge(e)) return true;
        return true;
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

    // --- Solve once -------------------------------------------------------------------------

    private @Nullable LinkedHashSet<Node> solveOnce(PrecomputeContext ctx, Set<Node> ban,
                                                    ColliderPolicy colliderPolicy, boolean rbMode) {
        final Node X = ctx.X, Y = ctx.Y;

        // NEW: graph-level amenability check (Perković) only in RA mode.
        if (!rbMode && !ctx.graphAmenable) {
            switch (noAmenablePolicy) {
                case RETURN_EMPTY_SET:
                    // Return {} as the (only) adjustment set
                    return new LinkedHashSet<>(Collections.emptySet());
                case SUPPRESS:
                    // Suppress any output for this (X,Y) pair
                    return new LinkedHashSet<>();
                default: /* SEARCH */
                    // Fall through: allow heuristic search even if not GAC-sound
                    break;
            }
        }

        LinkedHashSet<Node> Z = new LinkedHashSet<>(ctx.seedZ);
        while (true) {
            Optional<List<Node>> wit = findBackdoorWitness(X, Y, Z,
                    ctx.graphType, ctx.maxPathLength, ctx.notFollowed, rbMode);
            if (wit.isEmpty()) break;

            Node pick = chooseBlockerOnWitness(wit.get(), ctx.pool,
                    new HashSet<>(ctx.pool), ctx.forbidden, ban, Z,
                    ctx.amenableBackbone, ctx.graphType, colliderPolicy,
                    ctx.notFollowed, rbMode);
            if (pick == null) return null;
            Z.add(pick);
        }

        boolean changed;
        do {
            changed = false;
            for (Node v : new ArrayList<>(Z)) {
                if (ctx.seedZ.contains(v)) continue;
                Z.remove(v);
                if (findBackdoorWitness(X, Y, Z, ctx.graphType,
                        ctx.maxPathLength, ctx.notFollowed, rbMode).isPresent())
                    Z.add(v);
                else changed = true;
            }
        } while (changed);
        return Z;
    }

    // --- Witness search and DFS --------------------------------------------------------------

    private Optional<List<Node>> findBackdoorWitness(Node X, Node Y,
                                                     Set<Node> Z,
                                                     String graphType,
                                                     int maxPathLength,
                                                     Set<Node> notFollowed,
                                                     boolean rbMode) {
        List<Node> starts = firstBackdoorNeighbors(X, Y, graphType);

        // RB-mode fallback: include causal neighbors when no backdoor starts exist
        if (starts.isEmpty() && rbMode) {
            starts = new ArrayList<>();
            for (Node w : graph.getAdjacentNodes(X)) {
                if (!w.equals(X)) starts.add(w);
            }
        }

        if (starts.isEmpty()) return Optional.empty();

        final int edgeLimit = (maxPathLength < 0) ? Integer.MAX_VALUE : maxPathLength;

        for (Node w : starts) {
            if (notFollowed.contains(w) && !w.equals(Y)) continue;
            LinkedList<Node> path = new LinkedList<>(List.of(X, w));
            HashSet<Node> inPath = new HashSet<>(List.of(X, w));
            if (dfsWitness(path, inPath, Y, Z, graphType, edgeLimit, notFollowed, rbMode))
                return Optional.of(new ArrayList<>(path));
        }
        return Optional.empty();
    }

    private boolean dfsWitness(LinkedList<Node> path, Set<Node> inPath, Node Y,
                               Set<Node> Z, String graphType, int edgeLimit,
                               Set<Node> notFollowed, boolean rbMode){
        if (path.size() - 1 > edgeLimit) return false;
        Node tail = path.getLast();

        if (tail.equals(Y)) {
            // If we’re emulating RB, do NOT accept the direct 1-edge path [X,Y].
            // Only accept witnesses of length >= 2 edges (i.e., path.size() >= 3).
            if (rbMode && path.size() == 2) return false;
            return true;
        }

        for (Edge e : graph.getEdges(tail)) {
            Node nxt = Edges.traverse(tail, e);
            if (nxt == null || inPath.contains(nxt)) continue;
            if (notFollowed.contains(nxt) && !nxt.equals(Y)) continue;
            if (path.size() >= 2) {
                Node prev = path.get(path.size() - 2);
                if (notFollowed.contains(tail) && !tail.equals(Y)) continue;
                if (!tripleKeepsOpen(prev, tail, nxt, Z)) continue;
            }
            path.addLast(nxt);
            inPath.add(nxt);
            if (dfsWitness(path, inPath, Y, Z, graphType, edgeLimit, notFollowed, rbMode))
                return true;
            inPath.remove(nxt);
            path.removeLast();
        }
        return false;
    }

    // --- Blocker choice ----------------------------------------------------------------------

    private @Nullable Node chooseBlockerOnWitness(List<Node> witness,
                                                  List<Node> pool,
                                                  Set<Node> poolSet,
                                                  Set<Node> forbidden,
                                                  Set<Node> ban,
                                                  Set<Node> Z,
                                                  Set<Node> amenableBackbone,
                                                  String graphType,
                                                  ColliderPolicy colliderPolicy,
                                                  Set<Node> notFollowed,
                                                  boolean rbMode) {
        Set<Node> inWitness = new HashSet<>(witness);
        List<Node> candidates = new ArrayList<>();

        for (Node v : pool) {
            if (inWitness.contains(v)
                && (rbMode || !forbidden.contains(v))
                && (rbMode || !amenableBackbone.contains(v))
                && !Z.contains(v)
                && !ban.contains(v)
                && !notFollowed.contains(v)) {
                candidates.add(v);
            }
        }

        if (candidates.isEmpty()) {
            for (Node v : witness) {
                // In RB-mode, allow witness nodes even if they’re not in poolSet.
                boolean allow = rbMode || poolSet.contains(v);
                if (allow
                    && (rbMode || !forbidden.contains(v))
                    && (rbMode || !amenableBackbone.contains(v))
                    && !Z.contains(v)
                    && !ban.contains(v)
                    && !notFollowed.contains(v)) {
                    candidates.add(v);
                }
            }
        }
        if (candidates.isEmpty()) return null;

        if (colliderPolicy == ColliderPolicy.NONCOLLIDER_FIRST) {
            List<Node> noncol = new ArrayList<>();
            for (Node v : candidates) {
                RoleOnWitness r = roleOnWitness(v, witness);
                if (r == RoleOnWitness.NONCOLLIDER || r == RoleOnWitness.AMBIGUOUS)
                    noncol.add(v);
            }
            if (!noncol.isEmpty()) candidates = noncol;
        }

        candidates.sort((a, b) -> {
            int ra = roleScore(roleOnWitness(a, witness), colliderPolicy);
            int rb = roleScore(roleOnWitness(b, witness), colliderPolicy);
            if (ra != rb) return Integer.compare(rb, ra);
            int ia = pool.indexOf(a), ib = pool.indexOf(b);
            if (ia != ib) return Integer.compare(ia, ib);
            int da = graph.getAdjacentNodes(a).size(), db = graph.getAdjacentNodes(b).size();
            if (da != db) return Integer.compare(da, db);
            return a.getName().compareTo(b.getName());
        });
        return candidates.get(0);
    }

    // --- Helper methods ----------------------------------------------------------------------

    private boolean tripleKeepsOpen(Node a, Node b, Node c, Set<Node> Z) {
        boolean collider = graph.isDefCollider(a, b, c);
        boolean defNon = graph.isDefNoncollider(a, b, c);
        if (collider) return Z.contains(b) || graph.paths().isAncestorOfAnyZ(b, Z);
        return !Z.contains(b);
    }

    private RoleOnWitness roleOnWitness(Node v, List<Node> witness) {
        int k = witness.indexOf(v);
        if (k <= 0 || k >= witness.size() - 1) return RoleOnWitness.ENDPOINT;
        Node a = witness.get(k - 1), b = v, c = witness.get(k + 1);
        boolean collider = graph.isDefCollider(a, b, c);
        boolean defNon = graph.isDefNoncollider(a, b, c);
        if (collider) return RoleOnWitness.COLLIDER;
        if (defNon) return RoleOnWitness.NONCOLLIDER;
        return RoleOnWitness.AMBIGUOUS;
    }

    private int roleScore(RoleOnWitness r, ColliderPolicy p) {
        int base = switch (r) {
            case NONCOLLIDER -> 100;
            case AMBIGUOUS -> 30;
            case COLLIDER -> -80;
            default -> -200;
        };
        return base;
    }

    private static String keyOf(Set<Node> Z) {
        return Z.stream().map(Node::getName).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    private int endpointDistance(Node v, Shells s) {
        for (int r = 1; r < s.layers.length; r++)
            if (s.layers[r].contains(v)) return r;
        return Integer.MAX_VALUE / 2;
    }

    private static Set<Node> amenableBackbone(Set<List<Node>> amenable, Node X, Node Y) {
        LinkedHashSet<Node> s = new LinkedHashSet<>();
        for (List<Node> p : amenable)
            for (int i = 1; i < p.size() - 1; i++)
                if (p.get(i) != X && p.get(i) != Y) s.add(p.get(i));
        return s;
    }

    // --- Enums & records ---------------------------------------------------------------------

    public enum ColliderPolicy {
        OFF,
        PREFER_NONCOLLIDERS,
        NONCOLLIDER_FIRST
    }

    public enum NoAmenablePolicy {
        SEARCH,
        RETURN_EMPTY_SET,
        SUPPRESS
    }

    private enum RoleOnWitness {
        ENDPOINT,
        COLLIDER,
        NONCOLLIDER,
        AMBIGUOUS
    }

    private static final class PrecomputeContext {
        final Node X, Y;
        final String graphType;
        final int maxRadius, nearWhichEndpoint, maxPathLength;
        final Set<List<Node>> amenable;
        final Set<Node> amenableBackbone, forbidden;
        final Shells shellsFromX, shellsFromY;
        final List<Node> pool;
        final Map<Node,Integer> idx, order;
        final Set<Node> notFollowed;
        final LinkedHashSet<Node> seedZ;
        final boolean rbMode;
        final boolean graphAmenable;

        PrecomputeContext(Node X, Node Y, String graphType, int maxRadius, int nearWhichEndpoint,
                          int maxPathLength, Set<List<Node>> amenable, Set<Node> amenableBackbone,
                          Set<Node> forbidden, Shells sx, Shells sy, List<Node> pool,
                          Map<Node,Integer> idx, Map<Node,Integer> order,
                          Set<Node> notFollowed, LinkedHashSet<Node> seedZ,
                          boolean rbMode, boolean graphAmenable) {
            this.X = X; this.Y = Y; this.graphType = graphType;
            this.maxRadius = maxRadius; this.nearWhichEndpoint = nearWhichEndpoint;
            this.maxPathLength = maxPathLength;
            this.amenable = amenable; this.amenableBackbone = amenableBackbone;
            this.forbidden = forbidden; this.shellsFromX = sx; this.shellsFromY = sy;
            this.pool = pool; this.idx = idx; this.order = order;
            this.notFollowed = notFollowed; this.seedZ = seedZ;
            this.rbMode = rbMode; this.graphAmenable = graphAmenable;
        }
    }

    private record Shells(List<Node>[] layers, Set<Node> reach) { }
}