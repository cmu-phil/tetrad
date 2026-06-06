package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * Differential / equivalence harness for the memoized continuation cache added
 * to {@link RecursiveBlocking#findPathToTargetVisit}.
 *
 * <p><b>What it checks.</b> The memoization is an internal optimization that
 * must not change any observable verdict. This harness generates random graphs
 * with deliberately embedded cliques — the exact structure where the cache
 * fires hardest and therefore where any path-taint bug would surface — and, for
 * every ordered pair (x, y), compares the public {@link
 * RecursiveBlocking.BlockingResult} of the current (memoized) build against a
 * trusted reference verdict computed independently.</p>
 *
 * <p><b>The reference oracle.</b> We do not diff against a saved copy of the
 * old code (a regression snapshot), per the project's preference for
 * theorem-grounded checks. Instead we verify the property the algorithm is
 * supposed to deliver: a returned set Z must actually m-separate x and y in the
 * graph (a graphical fact, checkable directly via {@code graph.paths()
 * .isMSeparatedFrom}); and an UNBLOCKABLE verdict must be corroborated by an
 * independent exhaustive-subset search over the same node pool failing to find
 * any m-separating set. INDETERMINATE verdicts are exempt from the UNBLOCKABLE
 * cross-check (they assert nothing about existence) but a returned set, if any,
 * is still verified to separate.</p>
 *
 * <p>Because exhaustive verification is exponential in the pool size, the
 * exhaustive cross-check is gated behind a pool-size cap; above it, only the
 * "returned set genuinely separates" invariant is enforced (which alone catches
 * the path-taint class of bug, since a wrong cached BLOCKED yields a Z that
 * fails to separate).</p>
 *
 * <p>Run with no arguments for defaults, or pass:
 * {@code numGraphs numNodes avgDegree latentFraction cliqueSize seed}.</p>
 */
public final class RecursiveBlockingMemoEquivalenceHarness {

    // ---- Tunables (overridable via args) ------------------------------------
    private int numGraphs = 200;
    private int numNodes = 14;
    private double avgDegree = 4.0;
    private double latentFraction = 0.15;
    private int cliqueSize = 5;     // size of the clique we inject into each graph
    private long seed = 13L;

    // Search parameters held fixed across both sides of the comparison.
    private final int recursiveDepth = -1;   // unlimited (ceiling = numNodes)
    private final int depth = -1;            // unlimited Z size
    private final int maxRadius = -1;        // unrestricted pool
    private final int nearWhichEndpoint = 3; // both endpoints
    private final boolean ignoreDirectEdge = true;
    private final long deadlineMs = Long.MAX_VALUE;

    // Exhaustive UNBLOCKABLE cross-check only when pool is small enough.
    private static final int EXHAUSTIVE_POOL_CAP = 16;

    public static void main(String[] args) {
        RecursiveBlockingMemoEquivalenceHarness h = new RecursiveBlockingMemoEquivalenceHarness();
        if (args.length >= 1) h.numGraphs = Integer.parseInt(args[0]);
        if (args.length >= 2) h.numNodes = Integer.parseInt(args[1]);
        if (args.length >= 3) h.avgDegree = Double.parseDouble(args[2]);
        if (args.length >= 4) h.latentFraction = Double.parseDouble(args[3]);
        if (args.length >= 5) h.cliqueSize = Integer.parseInt(args[4]);
        if (args.length >= 6) h.seed = Long.parseLong(args[5]);
        h.run();
    }

    public void run() {
        RandomUtil.getInstance().setSeed(seed);
        Random rng = new Random(seed);

        long startMs = System.currentTimeMillis();

        int pairsChecked = 0;
        int separatesVerified = 0;
        int unblockableCrossChecked = 0;
        int indeterminate = 0;

        List<String> failures = new ArrayList<>();

        for (int g = 0; g < numGraphs; g++) {
            Graph pag = randomPagWithClique(rng);
            List<Node> nodes = pag.getNodes();

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = 0; j < nodes.size(); j++) {
                    if (i == j) continue;
                    Node x = nodes.get(i);
                    Node y = nodes.get(j);

                    // Skip latent endpoints — RecursiveBlocking is used for
                    // measured-pair separation; latent x/y are not meaningful
                    // queries in the calling contexts.
                    if (x.getNodeType() == NodeType.LATENT) continue;
                    if (y.getNodeType() == NodeType.LATENT) continue;

                    RecursiveBlocking.BlockingResult result;
                    try {
                        result = RecursiveBlocking.blockPathsRecursively(
                                pag, x, y,
                                new HashSet<>(),   // containing
                                new HashSet<>(),   // notFollowed
                                recursiveDepth, depth, maxRadius,
                                nearWhichEndpoint, ignoreDirectEdge, deadlineMs);
                    } catch (Exception e) {
                        failures.add(String.format(
                                "graph %d  (%s -> %s): threw %s",
                                g, x.getName(), y.getName(), e));
                        continue;
                    }

                    pairsChecked++;

                    if (result.found()) {
                        // INVARIANT 1: a returned set must actually m-separate.
                        // This is the invariant that a bad cached BLOCKED breaks.
                        Set<Node> z = result.blockingSet();
                        boolean sep = pag.paths().isMSeparatedFrom(x, y, z, false);
                        if (!sep) {
                            failures.add(String.format(
                                    "graph %d  (%s -> %s): returned Z=%s that does NOT m-separate",
                                    g, x.getName(), y.getName(), names(z)));
                        } else {
                            separatesVerified++;
                        }
                    } else if (result.indeterminate()) {
                        indeterminate++;
                        // No existence claim; nothing to cross-check.
                    } else {
                        // UNBLOCKABLE: claims NO separator exists in the pool.
                        // INVARIANT 2: an independent exhaustive search over the
                        // same pool must also fail to find one. Only run when
                        // the pool is small enough to enumerate.
                        Set<Node> pool = poolFor(pag, x, y);
                        if (pool.size() <= EXHAUSTIVE_POOL_CAP) {
                            Set<Node> witness = exhaustiveSeparator(pag, x, y, pool);
                            if (witness != null) {
                                failures.add(String.format(
                                        "graph %d  (%s -> %s): claimed UNBLOCKABLE but Z=%s separates",
                                        g, x.getName(), y.getName(), names(witness)));
                            } else {
                                unblockableCrossChecked++;
                            }
                        }
                    }
                }
            }
        }

        long elapsedMs = System.currentTimeMillis() - startMs;

        System.out.println("=== RecursiveBlocking memo equivalence harness ===");
        System.out.printf("graphs=%d  nodes=%d  avgDeg=%.1f  latentFrac=%.2f  clique=%d  seed=%d%n",
                numGraphs, numNodes, avgDegree, latentFraction, cliqueSize, seed);
        System.out.printf("pairs checked:            %d%n", pairsChecked);
        System.out.printf("returned-set separations: %d (all verified to m-separate)%n", separatesVerified);
        System.out.printf("UNBLOCKABLE cross-checked: %d (pool <= %d)%n", unblockableCrossChecked, EXHAUSTIVE_POOL_CAP);
        System.out.printf("indeterminate:            %d%n", indeterminate);
        System.out.printf("wall-clock:               %d ms%n", elapsedMs);

        if (failures.isEmpty()) {
            System.out.println("RESULT: PASS — no invariant violations.");
        } else {
            System.out.printf("RESULT: FAIL — %d violation(s):%n", failures.size());
            int shown = 0;
            for (String f : failures) {
                System.out.println("  " + f);
                if (++shown >= 50) {
                    System.out.printf("  ... and %d more%n", failures.size() - shown);
                    break;
                }
            }
            // Non-zero exit so CI catches it.
            throw new AssertionError(failures.size() + " equivalence violation(s)");
        }
    }

    // ---- Graph generation ---------------------------------------------------

    /**
     * Builds a random DAG over {@code numNodes} variables (a fraction marked
     * latent), force-injects a fully connected clique among the first
     * {@code cliqueSize} measured nodes to stress the continuation cache, then
     * converts to a PAG via the DAG-to-PAG transform so the verdicts exercise
     * genuine PAG semantics (circles, bidirected edges, underline triples).
     */
    private Graph randomPagWithClique(Random rng) {
        List<Node> nodes = new ArrayList<>();
        int numLatent = (int) Math.round(numNodes * latentFraction);

        for (int i = 0; i < numNodes; i++) {
            GraphNode n = new GraphNode("X" + (i + 1));
            n.setNodeType(i < numLatent ? NodeType.LATENT : NodeType.MEASURED);
            nodes.add(n);
        }

        // Random DAG by sampling edges respecting the index order (acyclicity).
        Graph dag = new EdgeListGraph(nodes);
        double pEdge = avgDegree / (numNodes - 1);
        for (int i = 0; i < numNodes; i++) {
            for (int j = i + 1; j < numNodes; j++) {
                if (rng.nextDouble() < pEdge) {
                    dag.addDirectedEdge(nodes.get(i), nodes.get(j));
                }
            }
        }

        // Force a clique among the first cliqueSize MEASURED nodes (oriented by
        // index order to keep the DAG acyclic). This is the structure where
        // many path prefixes converge on the same node under the same Z.
        List<Node> measured = new ArrayList<>();
        for (Node n : nodes) if (n.getNodeType() == NodeType.MEASURED) measured.add(n);
        int k = Math.min(cliqueSize, measured.size());
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                Node a = measured.get(i), b = measured.get(j);
                if (!dag.isAdjacentTo(a, b)) {
                    dag.addDirectedEdge(a, b); // a before b in measured order
                }
            }
        }

        // Convert DAG -> PAG (true PAG with latents marginalized out).     
//        return GraphTransforms.dagToPag(dag);

        MagToPag magToPag = new MagToPag(GraphTransforms.dagToMag(dag));
        magToPag.setRecursiveDepth(recursiveDepth);

        return magToPag.convert(false, false);
    }

    // ---- Reference oracle helpers ------------------------------------------

    /**
     * Reconstructs the same node pool RecursiveBlocking would use for (x, y)
     * under the harness's fixed parameters, so the exhaustive cross-check
     * searches over an identical candidate set. With maxRadius = -1 the pool is
     * all nodes except x and y; latent nodes are excluded from conditioning.
     */
    private Set<Node> poolFor(Graph graph, Node x, Node y) {
        Set<Node> pool = new LinkedHashSet<>(graph.getNodes());
        pool.remove(x);
        pool.remove(y);
        pool.removeIf(n -> n.getNodeType() == NodeType.LATENT);
        return pool;
    }

    /**
     * Exhaustively searches subsets of {@code pool} (smallest first) for any set
     * that m-separates x and y. Returns the first such set, or null if none
     * exists. Exponential — caller must gate on pool size.
     */
    private Set<Node> exhaustiveSeparator(Graph graph, Node x, Node y, Set<Node> pool) {
        List<Node> poolList = new ArrayList<>(pool);
        int n = poolList.size();

        // Smallest-first by iterating subset cardinality.
        for (int size = 0; size <= n; size++) {
            int[] idx = firstCombination(size);
            while (idx != null) {
                Set<Node> z = new HashSet<>();
                for (int t : idx) z.add(poolList.get(t));
                if (graph.paths().isMSeparatedFrom(x, y, z, false)) {
                    return z;
                }
                idx = nextCombination(idx, n);
            }
        }
        return null;
    }

    private static int[] firstCombination(int size) {
        int[] c = new int[size];
        for (int i = 0; i < size; i++) c[i] = i;
        return c;
    }

    /** Standard lexicographic next-combination of {@code size} indices from [0,n). */
    private static int[] nextCombination(int[] c, int n) {
        int size = c.length;
        if (size == 0) return null; // only the empty set exists at size 0
        int i = size - 1;
        while (i >= 0 && c[i] == n - size + i) i--;
        if (i < 0) return null;
        c[i]++;
        for (int j = i + 1; j < size; j++) c[j] = c[j - 1] + 1;
        return c;
    }

    private static List<String> names(Set<Node> z) {
        List<String> out = new ArrayList<>();
        for (Node n : z) out.add(n.getName());
        Collections.sort(out);
        return out;
    }
}
