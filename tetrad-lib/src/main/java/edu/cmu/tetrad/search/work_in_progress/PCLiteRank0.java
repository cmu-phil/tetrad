package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RankTests;
import org.apache.commons.math3.util.Pair;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PC-Lite (PC-Stable) skeleton learner using RCCA rank-0 as the CI oracle.
 *
 * Assumptions:
 * - You have a Graph with Node objects (undirected edges used for skeleton).
 * - 'nodes' is the fixed, index-consistent list of Node's.
 * - Independence oracle: estimateRccaRankConditioned(S, {u}, {v}, Z, n, alpha) == 0.
 */
public class PCLiteRank0 {

    public static class Result {
        public final Graph skeleton;
        public final Map<Pair<Node, Node>, List<Node>> sepset;              // one minimal sepset
        public final Map<Pair<Node, Node>, List<List<Node>>> allSepsets;    // optional: all found (may be null)

        public Result(Graph skeleton,
                      Map<Pair<Node, Node>, List<Node>> sepset,
                      Map<Pair<Node, Node>, List<List<Node>>> allSepsets) {
            this.skeleton = skeleton;
            this.sepset = sepset;
            this.allSepsets = allSepsets;
        }
    }

    public static Result run(SimpleMatrix S,
                             List<Node> nodes,
                             int n,
                             double alpha,
                             int dmax,
                             boolean storeAllSepsets) {

        // Start from a complete undirected graph
        Graph G = new EdgeListGraph(nodes); // or however you construct an empty undirected Graph then addAllEdges

        // Add all undirected edges
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                G.addUndirectedEdge(nodes.get(i), nodes.get(j));
            }
        }

        // Sepset stores one minimal separating set per non-adjacent pair
        Map<Pair<Node, Node>, List<Node>> sepset = new HashMap<>();
        Map<Pair<Node, Node>, List<List<Node>>> allSeps = storeAllSepsets ? new HashMap<>() : null;

        // Cache for CI queries (saves a lot of time)
        Map<String, Boolean> ciCache = new HashMap<>();

        // Depth loop: PC-Stable (order-independent)
        for (int k = 0; k <= dmax; k++) {

            // Collect all currently adjacent pairs (frozen for this depth)
            List<Pair<Node, Node>> adjPairs = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i++) {
                Node ui = nodes.get(i);
                List<Node> adj = G.getAdjacentNodes(ui);
                for (Node v : adj) {
                    int j = nodes.indexOf(v);
                    if (i < j) adjPairs.add(new Pair<>(ui, v));
                }
            }

            // Edges to remove after finishing this depth
            Set<Pair<Node, Node>> removeAtEnd = new HashSet<>();

            // Test each pair with all k-subsets of its current neighbors (excluding each other)
            for (Pair<Node, Node> uv : adjPairs) {
                Node u = uv.getFirst();
                Node v = uv.getSecond();

                // Neighbor set for PC-Stable: union of current adjacencies minus {u,v}
                Set<Node> Nu = new HashSet<>(G.getAdjacentNodes(u));
                Set<Node> Nv = new HashSet<>(G.getAdjacentNodes(v));
                Nu.remove(v);
                Nv.remove(u);
                // Common convention: use neighbors of u or union; union is fine for rank tests
                List<Node> N = new ArrayList<>(Nu);
                // You can also do: new ArrayList<>(intersection(Nu, Nv)) if you prefer

                if (N.size() < k) continue;

                boolean separated = false;

                for (int[] combIdx : combinations(N.size(), k)) {
                    int[] Zidx = new int[k];
                    for (int t = 0; t < k; t++) Zidx[t] = nodes.indexOf(N.get(combIdx[t]));

                    int iu = nodes.indexOf(u);
                    int iv = nodes.indexOf(v);

                    boolean indep = cachedIndep(ciCache, S, iu, iv, Zidx, n, alpha);

                    if (indep) {
                        // Mark edge for removal, store sepset(s)
                        removeAtEnd.add(orderedPair(u, v));

                        // store one minimal sepset
                        sepset.put(orderedPair(u, v), Arrays.stream(Zidx).boxed()
                                .map(nodes::get).collect(Collectors.toList()));

                        if (storeAllSepsets) {
                            allSeps.computeIfAbsent(orderedPair(u, v), key -> new ArrayList<>());
                            allSeps.get(orderedPair(u, v)).add(Arrays.stream(Zidx).boxed()
                                    .map(nodes::get).collect(Collectors.toList()));
                        }
                        separated = true;
                        break; // stop at first witnessing Z of size k
                    }
                }
                // next uv
            }

            // Remove edges found independent at this depth (after finishing all tests)
            for (Pair<Node, Node> e : removeAtEnd) {
                G.removeEdge(e.getFirst(), e.getSecond());
            }
        }

        return new Result(G, sepset, allSeps);
    }

    // ===== Helper: cached CI oracle using RCCA rank-0 =====
    private static boolean cachedIndep(Map<String, Boolean> cache,
                                       SimpleMatrix S,
                                       int iu, int iv,
                                       int[] Z,
                                       int n, double alpha) {
        String key = ciKey(iu, iv, Z);
        Boolean v = cache.get(key);
        if (v != null) return v;

        int[] X = new int[]{iu};
        int[] Y = new int[]{iv};
        boolean indep = (RankTests.estimateRccaRankConditioned(S, X, Y, Z, n, alpha) == 0);

        cache.put(key, indep);
        return indep;
    }

    private static String ciKey(int iu, int iv, int[] Z) {
        int a = Math.min(iu, iv), b = Math.max(iu, iv);
        int[] Zs = Z.clone();
        Arrays.sort(Zs);
        StringBuilder sb = new StringBuilder();
        sb.append(a).append('-').append(b).append('|');
        for (int z : Zs) sb.append(z).append(',');
        return sb.toString();
    }

    // ===== Combinations iterator: choose(k out of n) as index arrays =====
    private static List<int[]> combinations(int n, int k) {
        List<int[]> out = new ArrayList<>();
        if (k == 0) { out.add(new int[0]); return out; }
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) idx[i] = i;
        while (true) {
            out.add(idx.clone());
            int p = k - 1;
            while (p >= 0 && idx[p] == p + n - k) p--;
            if (p < 0) break;
            idx[p]++;
            for (int i = p + 1; i < k; i++) idx[i] = idx[i - 1] + 1;
        }
        return out;
    }

    // ===== Ordered pair helper to key sepsets symmetrically =====
    private static Pair<Node, Node> orderedPair(Node a, Node b) {
        return (nodeId(a) <= nodeId(b)) ? new Pair<>(a, b) : new Pair<>(b, a);
    }

    // Define nodeId as a stable integer (e.g., nodes.indexOf(node) or node hash)
    private static int nodeId(Node x) {
        // Replace with your stable ID logic; nodes.indexOf(x) is fine if list is fixed
        return x.hashCode();
    }
}
