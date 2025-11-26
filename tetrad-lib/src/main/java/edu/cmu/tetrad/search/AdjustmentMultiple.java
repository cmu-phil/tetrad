package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Recursive adjustment for the case of multiple treatments X and multiple outcomes Y.
 *
 * Semantics: X and Y are disjoint non-empty sets of nodes. An output set Z is intended
 * to be a (generalized) adjustment set for estimating the joint intervention effect
 * p(Y | do(X)) in the sense of the generalized adjustment criterion (Perković et al.).
 *
 * This class mirrors the structure of {@link Adjustment}, but is written explicitly
 * for set-valued X and Y:
 *
 *  - forbidden nodes Forb_G(X,Y) are computed for the sets X,Y;
 *  - amenable paths are collected over all x in X, y in Y;
 *  - recursive search blocks noncausal backdoor paths between any x∈X and any y∈Y;
 *  - a final minimality pass tries to remove superfluous nodes from Z.
 *
 * Some of the "engineering" heuristics of Adjustment (shells, nearWhichEndpoint, etc.)
 * are intentionally *not* replicated here. The parameters are kept for API compatibility,
 * but the current implementation:
 *
 *  - ignores maxRadius and nearWhichEndpoint in the pool construction, and
 *  - uses the full candidate pool (minus forbidden / amenable backbone / notFollowed).
 */
public final class AdjustmentMultiple {

    private final Graph graph;

    // Reuse the existing enums from Adjustment for policy configuration.
    private Adjustment.ColliderPolicy colliderPolicy = Adjustment.ColliderPolicy.NONCOLLIDER_FIRST;
    private Adjustment.NoAmenablePolicy noAmenablePolicy = Adjustment.NoAmenablePolicy.SEARCH;

    public AdjustmentMultiple(Graph graph) {
        this.graph = Objects.requireNonNull(graph);
    }

    public AdjustmentMultiple setColliderPolicy(Adjustment.ColliderPolicy p) {
        this.colliderPolicy = Objects.requireNonNull(p);
        return this;
    }

    public AdjustmentMultiple setNoAmenablePolicy(Adjustment.NoAmenablePolicy p) {
        this.noAmenablePolicy = Objects.requireNonNull(p);
        return this;
    }

    /**
     * Entry point analogous to Adjustment.adjustmentSetsRB, but for set-valued X and Y.
     *
     * @param X               set of treatment nodes (non-empty, pairwise distinct from Y)
     * @param Y               set of outcome nodes (non-empty, pairwise distinct from X)
     * @param graphType       "dag", "pdag", "mag", or "pag" (case-insensitive); defaults to "dag" if null.
     * @param maxNumSets      maximum number of adjustment sets to return.
     * @param maxRadius       currently ignored (kept for API compatibility).
     * @param nearWhichEndpoint currently ignored (kept for API compatibility).
     * @param maxPathLength   maximum length of witness paths; if < 0, treated as "unbounded".
     * @param avoidAmenable   if true, never adjust on the amenable backbone (Perković-style GAC).
     * @param notFollowed     nodes never to follow during witness search or to include in Z.
     * @param containing      nodes that must be included in every adjustment set.
     */
    public List<Set<Node>> adjustmentSets(Set<Node> X,
                                          Set<Node> Y,
                                          @Nullable String graphType,
                                          int maxNumSets,
                                          int maxRadius,
                                          int nearWhichEndpoint,
                                          int maxPathLength,
                                          boolean avoidAmenable,
                                          @Nullable Set<Node> notFollowed,
                                          @Nullable Set<Node> containing) {
        return adjustmentSetsInternal(X, Y, graphType,
                maxNumSets, maxRadius, nearWhichEndpoint, maxPathLength,
                colliderPolicy, avoidAmenable, notFollowed, containing);
    }

    // -------------------------------------------------------------------------
    // Master recursive search, adapted to multi X,Y
    // -------------------------------------------------------------------------

    private List<Set<Node>> adjustmentSetsInternal(Set<Node> X,
                                                   Set<Node> Y,
                                                   @Nullable String graphType,
                                                   int maxNumSets,
                                                   int maxRadius,
                                                   int nearWhichEndpoint,
                                                   int maxPathLength,
                                                   Adjustment.ColliderPolicy colliderPolicy,
                                                   boolean avoidAmenable,
                                                   @Nullable Set<Node> notFollowed,
                                                   @Nullable Set<Node> containing) {

        Objects.requireNonNull(X, "X must not be null");
        Objects.requireNonNull(Y, "Y must not be null");
        if (X.isEmpty() || Y.isEmpty())
            throw new IllegalArgumentException("X and Y must be non-empty sets.");
        Set<Node> inter = new HashSet<>(X);
        inter.retainAll(Y);
        if (!inter.isEmpty())
            throw new IllegalArgumentException("X and Y must be disjoint.");

        final boolean rbMode = !avoidAmenable;
        PrecomputeContextMulti ctx = precomputeContextMulti(X, Y, graphType,
                maxRadius, nearWhichEndpoint, maxPathLength,
                avoidAmenable, notFollowed, containing);

        List<Set<Node>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Deque<Set<Node>> bans = new ArrayDeque<>();
        bans.add(Collections.emptySet());

        while (!bans.isEmpty() && out.size() < maxNumSets) {
            Set<Node> ban = bans.removeFirst();
            LinkedHashSet<Node> Z = solveOnceMulti(ctx, ban, colliderPolicy, rbMode);
            if (Z == null) continue;

            String key = keyOf(Z);
            if (seen.add(key)) {
                out.add(Z);
                // Standard RA "ban each node in Z" enumeration trick:
                for (Node v : Z) {
                    Set<Node> ban2 = new LinkedHashSet<>(ban);
                    ban2.add(v);
                    bans.addLast(ban2);
                }
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Precomputation for multi X,Y
    // -------------------------------------------------------------------------

    private PrecomputeContextMulti precomputeContextMulti(Set<Node> X,
                                                          Set<Node> Y,
                                                          @Nullable String graphType,
                                                          int maxRadius,
                                                          int nearWhichEndpoint,
                                                          int maxPathLength,
                                                          boolean avoidAmenable,
                                                          @Nullable Set<Node> notFollowed,
                                                          @Nullable Set<Node> containing) {
        final boolean rbMode = !avoidAmenable;

        // Normalize graph type string
        final String gt = (graphType == null)
                ? "DAG"
                : graphType.toUpperCase(Locale.ROOT);

        // Amenable paths and backbone (Perković et al.)
        Set<List<Node>> amenable = avoidAmenable
                ? getAmenablePathsMulti(X, Y, gt, maxPathLength)
                : Collections.emptySet();
        Set<Node> amenableBackbone = amenableBackboneMulti(amenable, X, Y);

        // Forbidden nodes Forb_G(X,Y) (generalized adjustment criterion).
        Set<Node> forbidden = getForbiddenForAdjustmentMulti(graph, gt, X, Y);

        // Candidate pool: all nodes except X∪Y, and (if not in RB mode) except forbidden & amenable backbone.
        LinkedHashSet<Node> poolSet = new LinkedHashSet<>(graph.getNodes());
        poolSet.removeAll(X);
        poolSet.removeAll(Y);

        if (!rbMode) {
            poolSet.removeAll(forbidden);
            poolSet.removeAll(amenableBackbone);
        }

        if (notFollowed != null) {
            poolSet.removeAll(notFollowed);
        }

        // Distances from X and Y for radius and ordering heuristics.
        Map<Node, Integer> distFromX = bfsDistances(graph, X);
        Map<Node, Integer> distFromY = bfsDistances(graph, Y);

        // Choose center side for shells / ordering: 0 = treatment side (X),
        // 1 = effect side (Y), anything else = min(distance to X, distance to Y).
        Map<Node, Integer> distFromCenter = new HashMap<>();
        if (nearWhichEndpoint == 0) {
            distFromCenter.putAll(distFromX);
        } else if (nearWhichEndpoint == 1) {
            distFromCenter.putAll(distFromY);
        } else {
            for (Node v : graph.getNodes()) {
                Integer dx = distFromX.get(v);
                Integer dy = distFromY.get(v);
                if (dx == null && dy == null) continue;
                int d = (dx == null) ? dy : (dy == null ? dx : Math.min(dx, dy));
                distFromCenter.put(v, d);
            }
        }

        // If a maxRadius is specified, keep only nodes within that radius of the chosen center side.
        if (maxRadius >= 0) {
            Iterator<Node> it = poolSet.iterator();
            while (it.hasNext()) {
                Node v = it.next();
                Integer d = distFromCenter.get(v);
                if (d == null || d > maxRadius) {
                    it.remove();
                }
            }
        }

        // SeedZ = "must contain" nodes, intersected with pool.
        LinkedHashSet<Node> seedZ = new LinkedHashSet<>();
        if (containing != null) {
            for (Node v : containing) {
                if (poolSet.contains(v)) seedZ.add(v);
            }
        }

        // Deterministic ordering of pool for reproducibility: by center distance, degree, then name.
        List<Node> pool = new ArrayList<>(poolSet);
        // Sort by distance from the chosen endpoint side first (effect vs treatment),
        // then by degree, then by name. This lets nearWhichEndpoint control whether
        // we search "from the side of the effect" or "from the side of the treatment".
        pool.sort(Comparator
                .comparingInt((Node v) -> distFromCenter.getOrDefault(v, Integer.MAX_VALUE))
                .thenComparingInt(v -> graph.getAdjacentNodes(v).size())
                .thenComparing(Node::getName));

        Set<Node> nf = (notFollowed == null) ? Set.of() : new HashSet<>(notFollowed);

        return new PrecomputeContextMulti(
                new LinkedHashSet<>(X),
                new LinkedHashSet<>(Y),
                gt, maxPathLength,
                amenable, amenableBackbone, forbidden,
                pool, nf, seedZ, rbMode,
                distFromCenter);
    }

    // Helper: BFS distances from a set of sources over the undirected skeleton.
    private static Map<Node, Integer> bfsDistances(Graph G, Set<Node> sources) {
        Map<Node, Integer> dist = new HashMap<>();
        Deque<Node> q = new ArrayDeque<>();
        for (Node s : sources) {
            dist.put(s, 0);
            q.addLast(s);
        }
        while (!q.isEmpty()) {
            Node u = q.removeFirst();
            int du = dist.get(u);
            for (Node v : G.getAdjacentNodes(u)) {
                if (!dist.containsKey(v)) {
                    dist.put(v, du + 1);
                    q.addLast(v);
                }
            }
        }
        return dist;
    }

    /**
     * Collect amenable paths between any x∈X and any y∈Y.
     */
    private Set<List<Node>> getAmenablePathsMulti(Set<Node> X,
                                                  Set<Node> Y,
                                                  String graphType,
                                                  int maxLength) {
        if (X.isEmpty() || Y.isEmpty()) return Collections.emptySet();

        Set<List<Node>> out = new LinkedHashSet<>();
        for (Node x : X) {
            for (Node y : Y) {
                if (x.equals(y)) continue;
                if ("PAG".equalsIgnoreCase(graphType)) {
                    out.addAll(graph.paths().getAmenablePathsPag(x, y, maxLength));
                } else {
                    out.addAll(graph.paths().getAmenablePathsPdagMag(x, y, maxLength));
                }
            }
        }
        return out;
    }

    /**
     * Forb_G(X,Y) for sets X,Y (Perković et al. 2018).
     */
    private static Set<Node> getForbiddenForAdjustmentMulti(Graph G,
                                                            String graphType,
                                                            Set<Node> X,
                                                            Set<Node> Y) {
        Objects.requireNonNull(G);
        if (X.isEmpty() || Y.isEmpty()) return Collections.emptySet();

        // Forward reach from all X along possibly-out edges.
        Set<Node> fwdFromX = forwardReach(G, graphType, X);

        // Nodes that can (in forward sense) reach any y∈Y.
        Set<Node> canReachY = backwardFilterByForwardRuleMulti(G, graphType, Y);

        // Nodes on some possibly directed path from X to Y.
        Set<Node> onSomePDPath = new LinkedHashSet<>(fwdFromX);
        onSomePDPath.retainAll(canReachY);
        onSomePDPath.removeAll(X);

        // Seeds = X ∪ onSomePDPath
        Set<Node> seeds = new LinkedHashSet<>(X);
        seeds.addAll(onSomePDPath);

        // Forb = forward reach from seeds, minus X and Y.
        Set<Node> forb = forwardReach(G, graphType, seeds);
        forb.removeAll(X);
        forb.removeAll(Y);
        return forb;
    }

    private static Set<Node> forwardReach(Graph G,
                                          String graphType,
                                          Set<Node> starts) {
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

    private static Set<Node> backwardFilterByForwardRuleMulti(Graph G,
                                                              String graphType,
                                                              Set<Node> Y) {
        Set<Node> canReach = new LinkedHashSet<>();
        Deque<Node> q = new ArrayDeque<>();

        canReach.addAll(Y);
        q.addAll(Y);

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

    private static boolean isPossiblyOutEdge(String graphType,
                                             Edge e,
                                             Node a,
                                             Node b) {
        if (e.pointsTowards(a)) return false;          // arrowhead into a: no
        if (Edges.isBidirectedEdge(e)) return false;   // ↔ edges never "out"
        if (Edges.isUndirectedEdge(e)) return true;    // – edges can be "out"
        return true;                                   // tail at a is fine
    }

    // -------------------------------------------------------------------------
    // Single solve (multi X,Y)
    // -------------------------------------------------------------------------

    private @Nullable LinkedHashSet<Node> solveOnceMulti(PrecomputeContextMulti ctx,
                                                         Set<Node> ban,
                                                         Adjustment.ColliderPolicy colliderPolicy,
                                                         boolean rbMode) {
        if (ctx.amenable.isEmpty()) {
            switch (noAmenablePolicy) {
                case RETURN_EMPTY_SET:
                    return new LinkedHashSet<>(Collections.emptySet());
                case SUPPRESS:
                    return new LinkedHashSet<>();
                case SEARCH:
                default:
                    // fall through
            }
        }

        LinkedHashSet<Node> Z = new LinkedHashSet<>(ctx.seedZ);

        // 1) Grow Z until all backdoor (noncausal) paths from any x∈X to any y∈Y are blocked.
        while (true) {
            Optional<List<Node>> wit = findBackdoorWitnessMulti(
                    ctx.X, ctx.Y, Z,
                    ctx.graphType, ctx.maxPathLength,
                    ctx.notFollowed, rbMode);

            if (wit.isEmpty()) break;

            Node pick = chooseBlockerOnWitness(
                    wit.get(),
                    ctx.pool,
                    new HashSet<>(ctx.pool),
                    ctx.forbidden,
                    ban,
                    Z,
                    ctx.amenableBackbone,
                    ctx.graphType,
                    colliderPolicy,
                    ctx.notFollowed,
                    rbMode);

            if (pick == null) return null;
            Z.add(pick);
        }

        // 2) Minimality pass: try to remove each non-seed node from Z.
        boolean changed;
        do {
            changed = false;
            for (Node v : new ArrayList<>(Z)) {
                if (ctx.seedZ.contains(v)) continue; // must-include
                Z.remove(v);
                if (findBackdoorWitnessMulti(
                        ctx.X, ctx.Y, Z,
                        ctx.graphType, ctx.maxPathLength,
                        ctx.notFollowed, rbMode).isPresent()) {
                    // Removing v re-opened a path; restore it.
                    Z.add(v);
                } else {
                    changed = true;
                }
            }
        } while (changed);

        return Z;
    }

    // -------------------------------------------------------------------------
    // Witness search: any x∈X to any y∈Y
    // -------------------------------------------------------------------------

    private Optional<List<Node>> findBackdoorWitnessMulti(Set<Node> X,
                                                          Set<Node> Y,
                                                          Set<Node> Z,
                                                          String graphType,
                                                          int maxPathLength,
                                                          Set<Node> notFollowed,
                                                          boolean rbMode) {
        final int edgeLimit = (maxPathLength < 0)
                ? Integer.MAX_VALUE
                : maxPathLength;

        for (Node x : X) {
            // Backdoor starts for this x.
            List<Node> starts = firstBackdoorNeighbors(x, Y, graphType);

            // RB-mode fallback: allow causal neighbors when no backdoor starts exist.
            if (starts.isEmpty() && rbMode) {
                starts = new ArrayList<>();
                for (Node w : graph.getAdjacentNodes(x)) {
                    if (!w.equals(x)) starts.add(w);
                }
            }

            if (starts.isEmpty()) continue;

            for (Node w : starts) {
                if (notFollowed.contains(w) && !Y.contains(w)) continue;

                LinkedList<Node> path = new LinkedList<>();
                path.add(x);
                path.add(w);
                HashSet<Node> inPath = new HashSet<>(List.of(x, w));

                if (dfsWitnessMulti(path, inPath, Y, Z,
                        graphType, edgeLimit, notFollowed, rbMode)) {
                    return Optional.of(new ArrayList<>(path));
                }
            }
        }
        return Optional.empty();
    }

    private boolean dfsWitnessMulti(LinkedList<Node> path,
                                    Set<Node> inPath,
                                    Set<Node> Y,
                                    Set<Node> Z,
                                    String graphType,
                                    int edgeLimit,
                                    Set<Node> notFollowed,
                                    boolean rbMode) {
        if (path.size() - 1 > edgeLimit) return false;

        Node tail = path.getLast();

        if (Y.contains(tail)) {
            // In RB-mode, do NOT accept a direct 1-edge path [x, y].
            if (rbMode && path.size() == 2) return false;
            return true;
        }

        for (Edge e : graph.getEdges(tail)) {
            Node nxt = Edges.traverse(tail, e);
            if (nxt == null || inPath.contains(nxt)) continue;
            if (notFollowed.contains(nxt) && !Y.contains(nxt)) continue;

            if (path.size() >= 2) {
                Node prev = path.get(path.size() - 2);
                if (notFollowed.contains(tail) && !Y.contains(tail)) continue;
                if (!tripleKeepsOpen(prev, tail, nxt, Z)) continue;
            }

            path.addLast(nxt);
            inPath.add(nxt);

            if (dfsWitnessMulti(path, inPath, Y, Z,
                    graphType, edgeLimit, notFollowed, rbMode)) {
                return true;
            }

            inPath.remove(nxt);
            path.removeLast();
        }
        return false;
    }

    /**
     * First backdoor neighbors of x relative to the outcome set Y.
     */
    private List<Node> firstBackdoorNeighbors(Node X,
                                              Set<Node> Y,
                                              String graphType) {
        final String gt = (graphType == null)
                ? "DAG"
                : graphType.toUpperCase(Locale.ROOT);

        boolean isPAG = "PAG".equalsIgnoreCase(gt);
        List<Node> starts = new ArrayList<>();

        for (Node W : graph.getAdjacentNodes(X)) {
            Edge e = graph.getEdge(X, W);
            if (e == null) continue;

            if (!isPAG) {
                // DAG/PDAG/MAG: classic backdoor neighbors: X <- W.
                if (e.pointsTowards(X)) starts.add(W);
            } else {
                boolean intoX = e.pointsTowards(X);
                boolean undOrBi = Edges.isUndirectedEdge(e) || Edges.isBidirectedEdge(e);

                boolean wToX = graph.paths().existsDirectedPath(W, X);
                boolean wToAnyY = false;
                for (Node y : Y) {
                    if (graph.paths().existsDirectedPath(W, y)) {
                        wToAnyY = true;
                        break;
                    }
                }

                if (intoX || (undOrBi && (wToX || wToAnyY))) {
                    starts.add(W);
                }
            }
        }
        return starts;
    }

    // -------------------------------------------------------------------------
    // Blocker choice (unchanged except for parameter types)
    // -------------------------------------------------------------------------

    private @Nullable Node chooseBlockerOnWitness(List<Node> witness,
                                                  List<Node> pool,
                                                  Set<Node> poolSet,
                                                  Set<Node> forbidden,
                                                  Set<Node> ban,
                                                  Set<Node> Z,
                                                  Set<Node> amenableBackbone,
                                                  String graphType,
                                                  Adjustment.ColliderPolicy colliderPolicy,
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
            // Fallback: allow any node on the witness, including those not in poolSet (in RB mode).
            for (Node v : witness) {
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

        if (colliderPolicy == Adjustment.ColliderPolicy.NONCOLLIDER_FIRST) {
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

            int da = graph.getAdjacentNodes(a).size();
            int db = graph.getAdjacentNodes(b).size();
            if (da != db) return Integer.compare(da, db);

            return a.getName().compareTo(b.getName());
        });

        return candidates.get(0);
    }

    // -------------------------------------------------------------------------
    // Local helper methods (triple openness, roles, keys, backbone)
    // -------------------------------------------------------------------------

    private boolean tripleKeepsOpen(Node a, Node b, Node c, Set<Node> Z) {
        boolean collider = graph.isDefCollider(a, b, c);
        boolean defNon = graph.isDefNoncollider(a, b, c);
        if (collider) {
            // Collider is open if conditioned on or if it has a conditioned descendant.
            return Z.contains(b) || graph.paths().isAncestorOfAnyZ(b, Z);
        }
        // Noncollider or unknown: path closed if we condition on it.
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

    private int roleScore(RoleOnWitness r, Adjustment.ColliderPolicy p) {
        int base = switch (r) {
            case NONCOLLIDER -> 100;
            case AMBIGUOUS -> 30;
            case COLLIDER -> -80;
            case ENDPOINT -> -200;
        };
        // For now, we ignore p beyond the NONCOLLIDER_FIRST filter above.
        return base;
    }

    private static String keyOf(Set<Node> Z) {
        return Z.stream()
                .map(Node::getName)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private static Set<Node> amenableBackboneMulti(Set<List<Node>> amenable,
                                                   Set<Node> X,
                                                   Set<Node> Y) {
        LinkedHashSet<Node> s = new LinkedHashSet<>();
        for (List<Node> p : amenable) {
            for (int i = 1; i < p.size() - 1; i++) {
                Node v = p.get(i);
                if (!X.contains(v) && !Y.contains(v)) {
                    s.add(v);
                }
            }
        }
        return s;
    }

    // -------------------------------------------------------------------------
    // Internal context and enums
    // -------------------------------------------------------------------------

    private enum RoleOnWitness {
        ENDPOINT,
        COLLIDER,
        NONCOLLIDER,
        AMBIGUOUS
    }

    private static final class PrecomputeContextMulti {
        final Set<Node> X, Y;
        final String graphType;
        final int maxPathLength;
        final Set<List<Node>> amenable;
        final Set<Node> amenableBackbone, forbidden;
        final List<Node> pool;
        final Set<Node> notFollowed;
        final LinkedHashSet<Node> seedZ;
        final boolean rbMode;
        final Map<Node, Integer> distFromCenter;

        PrecomputeContextMulti(Set<Node> X,
                               Set<Node> Y,
                               String graphType,
                               int maxPathLength,
                               Set<List<Node>> amenable,
                               Set<Node> amenableBackbone,
                               Set<Node> forbidden,
                               List<Node> pool,
                               Set<Node> notFollowed,
                               LinkedHashSet<Node> seedZ,
                               boolean rbMode,
                               Map<Node, Integer> distFromCenter) {
            this.X = X;
            this.Y = Y;
            this.graphType = graphType;
            this.maxPathLength = maxPathLength;
            this.amenable = amenable;
            this.amenableBackbone = amenableBackbone;
            this.forbidden = forbidden;
            this.pool = pool;
            this.notFollowed = notFollowed;
            this.seedZ = seedZ;
            this.rbMode = rbMode;
            this.distFromCenter = distFromCenter;
        }
    }
}