package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

/**
 * The {@code RecursiveAdjustment} algorithm searches recursively for a
 * (not necessarily minimal) adjustment set between variables X and Y
 * in a causal graph under PAG semantics.
 *
 * <p>This algorithm extends recursive blocking to the adjustment-set problem:
 * it forbids nodes on amenable (causal) paths and recursively adds nodes
 * that block all remaining backdoor paths, returning a graphical candidate
 * adjustment set suitable for causal effect estimation.</p>
 */
public final class Adjustment {

    private static final boolean DEBUG = false; // set false to silence logging
    private final Graph graph;

    public enum GraphType{PDAG, MAG, PAG}

    public Adjustment(Graph graph) {
        this.graph = graph;
    }

    public List<Set<Node>> adjustmentSets(Node X, Node Y, String graphType,
                                          int maxNumSets, int maxRadius,
                                          int nearWhichEndpoint, int maxPathLength) {
        List<Set<Node>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // precompute shells, pool, amenable, forbidden as you already do…
        var ctx = precomputeContext(X, Y, graphType, maxRadius, nearWhichEndpoint, maxPathLength);

        // Worklist of bans; start with “no bans”
        Deque<Set<Node>> bans = new ArrayDeque<>();
        bans.add(Collections.emptySet());

        while (!bans.isEmpty() && out.size() < maxNumSets) {
            Set<Node> ban = bans.removeFirst();

            // find one solution under this ban
            LinkedHashSet<Node> Z = solveOnce(ctx, ban);
            if (Z == null) continue;

            // minimality already enforced inside solveOnce(...)
            String key = keyOf(Z);
            if (seen.add(key)) {
                out.add(Z);

                // diversify: for every v in Z, create a new run that bans v
                for (Node v : Z) {
                    Set<Node> ban2 = new LinkedHashSet<>(ban);
                    ban2.add(v);
                    bans.addLast(ban2);
                }
            }
        }
        return out;
    }

    public PrecomputeContext precomputeContext(Node X, Node Y, String graphType,
                                                     int maxRadius, int nearWhichEndpoint,
                                                     int maxPathLength) {
        if (X == null || Y == null || X == Y) throw new IllegalArgumentException("X and Y must differ.");

        // Normalize “no limit”
        if (maxRadius < 0) maxRadius = graph.getNodes().size();    // full reach
        final boolean pag = "PAG".equalsIgnoreCase(graphType);

        // 1) Amenable (PD-out-of-X) paths you must preserve
        List<List<Node>> amenable = getAmenablePaths(X, Y, graphType, maxPathLength);
        if (amenable.isEmpty()) {
            // No PD-out-of-X route => {} is valid later; still return a context
            amenable = Collections.emptyList();
        }

        // 2) Forbidden (Perković GAC)
        Set<Node> forbidden = getForbiddenForAdjustment(graph, graphType, X, Y);

        // 3) Endpoint-local shells (no full backdoor enumeration)
        List<Node> starts = firstBackdoorNeighbors(X, Y, graphType);
        Shells shellsFromX = starts.isEmpty()
                ? new Shells(emptyLayers(maxRadius), Set.of())
                : backdoorShellsFromX(X, starts, maxRadius);
        Shells shellsFromY = undirectedShells(Y, maxRadius);

        // Reachability filter: keep nodes lying on some X–Y path
        Set<Node> reachX = shellsFromX.reach;
        Set<Node> reachY = shellsFromY.reach;

        // 4) Candidate pool (local to endpoints, reachable, not forbidden, not endpoints)
        LinkedHashSet<Node> poolSet = new LinkedHashSet<>();
        for (int r = 1; r <= maxRadius; r++) {
            if (nearWhichEndpoint == 1 || nearWhichEndpoint == 3) {
                poolSet.addAll(shellsFromX.layers[r]);
            }
            if (nearWhichEndpoint == 2 || nearWhichEndpoint == 3) {
                poolSet.addAll(shellsFromY.layers[r]);
            }
        }
        // Intersect with reach both sides
        poolSet.retainAll(reachX);
        poolSet.retainAll(reachY);
        poolSet.remove(X);
        poolSet.remove(Y);
        poolSet.removeAll(forbidden);

        // Optional: prune obvious amenable blockers up front (safe and fast)
        // Set<Node> A = amenableBlockers(amenable); poolSet.removeAll(A);

        // 5) Stable order: nearer to endpoints → lower degree → name
        List<Node> pool = new ArrayList<>(poolSet);
        pool.sort(Comparator
                .comparingInt((Node v) -> approxEndpointDistance(v, X, Y, shellsFromX, shellsFromY))
                .thenComparingInt(v -> graph.getAdjacentNodes(v).size())
                .thenComparing(Node::getName));

        // 6) Index maps
        Map<Node, Integer> idx = new HashMap<>(pool.size() * 2);
        Map<Node, Integer> order = new HashMap<>(pool.size() * 2);
        for (int i = 0; i < pool.size(); i++) {
            idx.put(pool.get(i), i);
            order.put(pool.get(i), i);
        }

        return new PrecomputeContext(X, Y, graphType, maxRadius, nearWhichEndpoint, maxPathLength,
                amenable, forbidden, shellsFromX, shellsFromY, pool, idx, order);
    }

    private List<List<Node>> getAmenablePaths(Node source, Node target, String graphType, int maxLength) {
        RecursiveAdjustment.GraphType _graphType = RecursiveAdjustment.GraphType.valueOf(graphType);

        if (source == null || target == null || source == target) return Collections.emptyList();
        if (_graphType == RecursiveAdjustment.GraphType.PAG) {
            return graph.paths().amenablePathsPag(source, target, maxLength);
        } else {
            // DAG/CPDAG/PDAG/MAG handled here
            return graph.paths().amenablePathsPdagMag(source, target, maxLength);
        }
    }

    /**
     * Compute Forb_G(X,Y): possible descendants of X and of any node that lies on
     * a proper possibly-directed path from X to Y (Perković et al., 2018).
     * Returns a set of observed + latent nodes (caller may subtract latents if desired).
     * <p>
     * NOTE:
     * - "Possibly-directed" step a -> b is allowed iff the edge has NO arrowhead into 'a'.
     * (i.e., not e.pointsTowards(a) and not bidirected into 'a').
     * - We exclude {X, Y} from the returned set (RA already forbids adjusting on them).
     */
    public static Set<Node> getForbiddenForAdjustment(Graph G, String graphType, Node X, Node Y) {
        Objects.requireNonNull(G);
        Objects.requireNonNull(X);
        Objects.requireNonNull(Y);
        if (X == Y) return Collections.emptySet();

        // Normalize graphType to an enum-ish tag
        final String gt = graphType == null ? "DAG" : graphType.toUpperCase(Locale.ROOT);

        // 1) Nodes reachable from X along possibly-directed forward steps
        Set<Node> fwdFromX = forwardReach(G, gt, Set.of(X));

        // 2) Nodes that can reach Y along possibly-directed forward steps
        //    (equivalently, forwardReach on the reversed sense: a node v is "backward-reachable"
        //     if there exists a neighbor u such that the step v -> u is possibly-directed AND u is known to reach Y)
        Set<Node> canReachY = backwardFilterByForwardRule(G, gt, Y);

        // 3) Nodes that lie on at least one proper possibly-directed path X ~> Y
        //    Intersect (forward from X) with (can reach Y), and remove X itself.
        Set<Node> onSomePDPath = new LinkedHashSet<>(fwdFromX);
        onSomePDPath.retainAll(canReachY);
        onSomePDPath.remove(X);

        // 4) Forbidden = possible descendants of X and of every node on such paths
        Set<Node> seeds = new LinkedHashSet<>();
        seeds.add(X);
        seeds.addAll(onSomePDPath);

        Set<Node> forb = forwardReach(G, gt, seeds);

        // 5) Do not return X or Y as "forbidden adjusters" (they're banned elsewhere anyway)
        forb.remove(X);
        forb.remove(Y);

        return forb;
    }

    /**
     * Legal first hops for a backdoor in the given graphType (your tested rule).
     */
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
    public @Nullable LinkedHashSet<Node> solveOnce(PrecomputeContext ctx, Set<Node> ban) {
        final Node X = ctx.X, Y = ctx.Y;
        final String graphType = ctx.graphType;
        final boolean pag = "PAG".equalsIgnoreCase(graphType);

        // Candidate universe and quick lookups
        final List<Node> pool = ctx.pool;                 // ordered endpoint-near first
        final Set<Node> poolSet = new HashSet<>(pool);
        final Set<Node> forbidden = ctx.forbidden;
        final List<List<Node>> amenable = ctx.amenable;

        // Start empty; we'll add only blockers that (1) lie on a witness and (2) don't close amenable paths
        LinkedHashSet<Node> Z = new LinkedHashSet<>();

        // If there are no PD-out-of-X paths, {} is valid
//        if (amenable.isEmpty()) return Z;

        Set<Node> _Z = new HashSet<>(Z);

        // Quick helper
        java.util.function.Supplier<Boolean> amenableOpen =
                () -> {
                    for (List<Node> p : amenable) if (!graph.paths().isMConnectingPath(p, _Z, pag)) return false;
                    return true;
                };

        // 1) Witness-guided growth
        while (true) {
            if (!amenableOpen.get()) {
                // Shouldn't happen if chooseBlocker preserves amenable, but guard anyway: fail under this ban
                return null;
            }

            Optional<List<Node>> wit = findBackdoorWitness(X, Y, Z, graphType, ctx.maxPathLength);
            if (wit.isEmpty()) break; // all backdoors are blocked → candidate found

            Node pick = chooseBlockerOnWitness(wit.get(), pool, poolSet, forbidden, ban, Z, amenable, graphType);
            if (pick == null) {
                // No allowable blocker on this witness that preserves amenable (under current bans)
                return null;
            }
            Z.add(pick);
            // loop to see if any backdoor remains
        }

        // 2) Minimality trim (try-delete)
        if (!Z.isEmpty()) {
            LinkedHashSet<Node> Zmin = new LinkedHashSet<>(Z);
            for (Node v : new ArrayList<>(Zmin)) {
                Zmin.remove(v);
                // must keep amenable open
                boolean ok = true;
                for (List<Node> semi : amenable) {
                    if (!graph.paths().isMConnectingPath(semi, Zmin, pag)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    // must still block all backdoors (no witness)
                    if (findBackdoorWitness(X, Y, Zmin, graphType, ctx.maxPathLength).isPresent()) ok = false;
                }
                if (!ok) Zmin.add(v);
            }
            Z = Zmin;
        }

        return Z;
    }

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
     * Pick a blocker on the current witness path.
     * Constraints:
     * - must lie on the witness AND be in the candidate pool (pool/poolSet)
     * - not in forbidden, not in current Z, not in ban
     * - adding it must NOT close any amenable path
     * Tie-break by your pool order (endpoint-near first), then degree, then name.
     *
     * @return the chosen Node, or null if no allowable blocker exists under these constraints.
     */
    private @Nullable Node chooseBlockerOnWitness(
            List<Node> witness,
            List<Node> pool,
            Set<Node> poolSet,
            Set<Node> forbidden,
            Set<Node> ban,
            Set<Node> Z,
            List<List<Node>> amenable,
            String graphType
    ) {
        final boolean pag = "PAG".equalsIgnoreCase(graphType);

        // Fast membership for witness nodes
        Set<Node> inWitness = new HashSet<>(witness);

        // Candidates = (pool ∩ witness) \ (forbidden ∪ Z ∪ ban)
        List<Node> candidates = new ArrayList<>();
        for (Node v : pool) {
            if (inWitness.contains(v) && !forbidden.contains(v) && !Z.contains(v) && !ban.contains(v)) {
                candidates.add(v);
            }
        }

        // Optional fallback: allow any witness node that’s also in poolSet (keeps locality bias)
        if (candidates.isEmpty()) {
            for (Node v : witness) {
                if (poolSet.contains(v) && !forbidden.contains(v) && !Z.contains(v) && !ban.contains(v)) {
                    candidates.add(v);
                }
            }
        }

        // Stable, locality-biased order: pool rank → degree → name
        candidates.sort(
                Comparator.<Node>comparingInt(v -> pool.indexOf(v))
                        .thenComparingInt(v -> graph.getAdjacentNodes(v).size())
                        .thenComparing(Node::getName)
        );

        // Pick the first candidate that does NOT close any amenable path when added
        for (Node v : candidates) {
            boolean preservesAmenable = true;
            for (List<Node> semi : amenable) {
                if (!graph.paths().isMConnectingPath(semi, add1(Z, v), pag)) { // test with Z ∪ {v}
                    preservesAmenable = false;
                    break;
                }
            }
            if (preservesAmenable) return v;
        }

        return null;
    }


    private Set<Node> add1(Set<Node> Z, Node v) {
        LinkedHashSet<Node> s = new LinkedHashSet<>(Z);
        s.add(v);
        return s;
    }

    // Keep uniques by a canonical key
    private String keyOf(Set<Node> Z) {
        return Z.stream().map(Node::getName).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    boolean dfsWitness(LinkedList<Node> path, Set<Node> inPath, Node Y,
                       Set<Node> Z, String graphType, int edgeLimit) {

        if (path.size() - 1 > edgeLimit) return false;

        Node tail = path.getLast();
        if (tail.equals(Y)) return true;

        // Iterate neighbors of tail
        for (Edge e : graph.getEdges(tail)) {
            Node nxt = Edges.traverse(tail, e);
            if (nxt == null || inPath.contains(nxt)) continue;

            // Enforce m-connection triple rule on the new triple (prev, tail, nxt)
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

    // Decide if <a,b,c> allows an m-connecting continuation given Z
    boolean tripleKeepsOpen(Node a, Node b, Node c, Set<Node> Z) {

        boolean collider = graph.isDefCollider(a, b, c); // use your PAG/MAG logic
        if (collider) {
            // collider must be conditioned on (or have a descendant in Z) to keep the path open
            return Z.contains(b) || graph.paths().isAncestorOfAnyZ(b, Z);
        } else {
            // noncollider must NOT be conditioned on
            return !Z.contains(b);
        }
    }


    /**
     * BFS shells from X, seeded only by backdoor starts.
     */
    private Shells backdoorShellsFromX(Node X, List<Node> starts, int maxRadius) {
        @SuppressWarnings("unchecked")
        List<Node>[] layers = new ArrayList[maxRadius + 1];
        for (int i = 0; i <= maxRadius; i++) layers[i] = new ArrayList<>();

        Set<Node> visited = new HashSet<>();
        Deque<Node> q = new ArrayDeque<>();
        Map<Node, Integer> dist = new HashMap<>();

        for (Node s : starts) {
            visited.add(X);
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

    /**
     * Undirected BFS shells from seed (used for Y side).
     */
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

    /**
     * Small heuristic used only for sorting candidates (endpoint-nearness).
     */
    private int approxEndpointDistance(Node v, Node X, Node Y, Shells sx, Shells sy) {
        int best = Integer.MAX_VALUE;
        for (int r = 1; r < sx.layers.length; r++) if (sx.layers[r].contains(v)) best = Math.min(best, r);
        for (int r = 1; r < sy.layers.length; r++) if (sy.layers[r].contains(v)) best = Math.min(best, r);
        return best == Integer.MAX_VALUE ? 1_000_000_000 : best;
    }

    /**
     * BFS forward reach under "possibly-directed out of 'a'": edge must NOT have an arrowhead into 'a'.
     */
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
        // include the start nodes’ possible descendants, but the caller will tweak X/Y removal
        return seen;
    }

    /**
     * True iff the edge (a,b) can be used as a possibly-directed step "a -> b",
     * i.e., the endpoint at 'a' does NOT have an arrowhead.
     * <p>
     * Concretely:
     * - If e.pointsTowards(a) => there's an arrowhead into 'a' ⇒ NOT allowed.
     * - Bidirected (↔) has arrowheads at both ends ⇒ NOT allowed.
     * - Undirected (—) / tail at 'a' (a—>b) / circle at 'a' (PAG) ⇒ allowed.
     */
    private static boolean isPossiblyOutEdge(String graphType, Edge e, Node a, Node b) {
        // Arrowhead into 'a' disqualifies for possibly-directed step out of 'a'
        if (e.pointsTowards(a)) return false;
        // Bidirected disqualifies (arrowheads on both ends)
        if (Edges.isBidirectedEdge(e)) return false;

        // Undirected is OK for PD paths in (M)PDAG/PAG semantics
        if (Edges.isUndirectedEdge(e)) return true;

        // Otherwise, tail or circle at 'a' is fine:
        // - In DAG/PDAG "a -> b": tail at 'a' ⇒ allowed
        // - In PAG/MAG "a o-> b" (circle at 'a') also allowed as "possibly out"
        return true;
    }

    /**
     * Compute nodes that can reach Y via a possibly-directed path.
     * We do this by a reverse-style dynamic programming:
     * canReach[Y] = true; iteratively mark a as true if exists b with canReach[b] and
     * step a -> b is possibly-directed (i.e., no arrowhead into a on edge (a,b)).
     */
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
                // For a to step toward b on a possibly-directed path, the edge must NOT have an arrowhead into 'a'
                if (!isPossiblyOutEdge(graphType, e, a, b)) continue;
                if (canReach.add(a)) q.addLast(a);
            }
        }
        return canReach;
    }


    // -------- Context holder --------
    public static final class PrecomputeContext {
        public final Node X, Y;
        public final String graphType;
        public final int maxRadius, nearWhichEndpoint, maxPathLength;

        public final List<List<Node>> amenable;     // PD-out-of-X paths you must keep open
        public final Set<Node> forbidden;           // GAC forbidden nodes
        public final Shells shellsFromX;            // BFS shells seeded by backdoor starts
        public final Shells shellsFromY;            // Undirected BFS shells from Y
        public final List<Node> pool;               // candidate variables (ordered)
        public final Map<Node, Integer> idx;         // candidate -> stable index (for bitmasks)
        public final Map<Node, Integer> order;       // candidate -> rank in 'pool' (for sorting)

        PrecomputeContext(Node X, Node Y, String graphType,
                          int maxRadius, int nearWhichEndpoint, int maxPathLength,
                          List<List<Node>> amenable,
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
            this.forbidden = forbidden;
            this.shellsFromX = shellsFromX;
            this.shellsFromY = shellsFromY;
            this.pool = pool;
            this.idx = idx;
            this.order = order;
        }
    }

    /**
     * @param layers 1..maxRadius
     * @param reach  union of all layers
     */
    private record Shells(List<Node>[] layers, Set<Node> reach) {
    }

}