package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;

import java.util.*;

/**
 * Phase 1 of RLCD: find skeleton and partition into overlapping clique groups.
 */
final class Phase1 {

    private Phase1() {
    }

    static Phase1Result runPhase1(DataSet dataSet, RLCDParams params) {
        List<Node> variables = dataSet.getVariables();
        Map<Node, Integer> nodeIndex = new LinkedHashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            nodeIndex.put(variables.get(i), i);
        }

        int n = variables.size();
        int[][] adj;

        switch (params.getStage1Method()) {
            case ALL -> {
                // Fully connected undirected skeleton (no self edges).
                adj = new int[n][n];
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        adj[i][j] = 1;
                        adj[j][i] = 1;
                    }
                }
            }
            case FGES -> {
                adj = runFgesSkeleton(dataSet, variables, nodeIndex, params);
            }
            // You can add PC_RANK, GES, FCI here later.
            default -> throw new UnsupportedOperationException(
                    "Stage1Method " + params.getStage1Method() +
                    " not yet implemented in Java RLCD.");
        }

        Graph skeleton = adjacencyToUndirectedGraph(adj, variables);

        List<List<Node>> partitions =
                partitionCliques(variables, adj, params.getStage1PartitionThreshold());

        return new Phase1Result(skeleton, partitions, nodeIndex);
    }

    /**
     * Stage-1 using FGES with SEM-BIC, mirroring the Python code that uses
     * py-tetrad's FGES with a sparsity/penalty parameter.
     */
    private static int[][] runFgesSkeleton(DataSet dataSet,
                                           List<Node> vars,
                                           Map<Node, Integer> nodeIndex,
                                           RLCDParams params) {

        // Import names may need to be adjusted depending on your Tetrad version.
        edu.cmu.tetrad.search.Fges fges;
        {
            SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
            score.setPenaltyDiscount(params.getStage1GesSparsity());
            fges = new edu.cmu.tetrad.search.Fges(score);
        }

        Graph g = null;
        try {
            g = fges.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int n = vars.size();
        int[][] adj = new int[n][n];

        for (Edge e : g.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();
            Integer ix = nodeIndex.get(x);
            Integer iy = nodeIndex.get(y);
            if (ix == null || iy == null) continue;

            // For Phase 1 we only care about the undirected skeleton.
            if (ix.equals(iy)) continue;
            adj[ix][iy] = 1;
            adj[iy][ix] = 1;
        }

        return adj;
    }

    private static Graph adjacencyToUndirectedGraph(int[][] adj, List<Node> vars) {
        Graph g = new EdgeListGraph(vars);
        int n = vars.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adj[i][j] != 0) {
                    g.addUndirectedEdge(vars.get(i), vars.get(j));
                }
            }
        }
        return g;
    }

    /**
     * Partition maximal cliques into groups of overlapping cliques, where two
     * cliques are grouped if they share at least 2 vertices. This mirrors
     * getPartition(...) in the Python code and Algorithm 1 (lines 3–7). [oai_citation:6‡OpenReview](https://openreview.net/pdf/8f4691fbb70a9d84f611815c4c5f2456d66f1a30.pdf)
     */
    private static List<List<Node>> partitionCliques(List<Node> vars,
                                                     int[][] adj,
                                                     int cliqueSizeThresh) {
        int n = vars.size();
        // Step 1: find all maximal cliques.
        List<BitSet> cliques = bronKerboschMaximalCliques(adj);

        // Filter cliques by size >= cliqueSizeThresh.
        List<BitSet> filtered = new ArrayList<>();
        for (BitSet c : cliques) {
            if (c.cardinality() >= cliqueSizeThresh) {
                filtered.add((BitSet) c.clone());
            }
        }

        // Step 2: DSU merge cliques if |Q1 ∩ Q2| >= 2.
        int m = filtered.size();
        DSU dsu = new DSU(m);

        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                BitSet ci = filtered.get(i);
                BitSet cj = filtered.get(j);
                BitSet common = (BitSet) ci.clone();
                common.and(cj);
                if (common.cardinality() >= 2) {
                    dsu.union(i, j);
                }
            }
        }

        // Collect groups by DSU parent.
        Map<Integer, BitSet> groupToBits = new LinkedHashMap<>();
        for (int i = 0; i < m; i++) {
            int root = dsu.find(i);
            BitSet acc = groupToBits.computeIfAbsent(root, k -> new BitSet(n));
            acc.or(filtered.get(i));
        }

        // Convert back to node lists, and require group size >= 4 as in Python.
        List<List<Node>> partitions = new ArrayList<>();
        for (BitSet bs : groupToBits.values()) {
            if (bs.cardinality() < 4) continue;
            List<Node> group = new ArrayList<>(bs.cardinality());
            for (int idx = bs.nextSetBit(0); idx >= 0; idx = bs.nextSetBit(idx + 1)) {
                group.add(vars.get(idx));
            }
            partitions.add(group);
        }

        return partitions;
    }

    /**
     * Standard Bron–Kerbosch with pivot for maximal cliques in an undirected graph.
     */
    private static List<BitSet> bronKerboschMaximalCliques(int[][] adj) {
        int n = adj.length;
        List<BitSet> cliques = new ArrayList<>();

        BitSet R = new BitSet(n); // current clique
        BitSet P = new BitSet(n); // prospective vertices
        BitSet X = new BitSet(n); // excluded vertices

        P.set(0, n);

        bronKerboschPivot(adj, R, P, X, cliques);
        return cliques;
    }

    private static void bronKerboschPivot(int[][] adj,
                                          BitSet R,
                                          BitSet P,
                                          BitSet X,
                                          List<BitSet> cliques) {
        if (P.isEmpty() && X.isEmpty()) {
            cliques.add((BitSet) R.clone());
            return;
        }

        BitSet unionPX = (BitSet) P.clone();
        unionPX.or(X);
        int u = unionPX.nextSetBit(0);
        BitSet neighborsOfU = (u >= 0) ? neighbors(adj, u) : new BitSet(adj.length);

        BitSet candidates = (BitSet) P.clone();
        candidates.andNot(neighborsOfU);

        for (int v = candidates.nextSetBit(0); v >= 0; v = candidates.nextSetBit(v + 1)) {
            BitSet newR = (BitSet) R.clone();
            newR.set(v);

            BitSet neighborsOfV = neighbors(adj, v);

            BitSet newP = (BitSet) P.clone();
            newP.and(neighborsOfV);

            BitSet newX = (BitSet) X.clone();
            newX.and(neighborsOfV);

            bronKerboschPivot(adj, newR, newP, newX, cliques);

            P.clear(v);
            X.set(v);
        }
    }

    private static BitSet neighbors(int[][] adj, int v) {
        int n = adj.length;
        BitSet bs = new BitSet(n);
        for (int j = 0; j < n; j++) {
            if (adj[v][j] != 0) {
                bs.set(j);
            }
        }
        return bs;
    }

    /**
     * Simple Disjoint Set Union (DSU / Union-Find) for clique grouping.
     */
    private static final class DSU {
        private final int[] parent;
        private final int[] rank;

        DSU(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) return;
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
            }
        }
    }

    // In Phase1 (or a small debug utility)
    static void logPartitions(Phase1Result res) {
        System.out.println("RLCD Phase 1 partitions:");
        int i = 1;
        for (List<Node> group : res.getPartitions()) {
            System.out.print("  Group " + (i++) + " (" + group.size() + " vars): ");
            System.out.println(
                    group.stream().map(Node::getName).reduce((a, b) -> a + ", " + b).orElse("")
            );
        }
    }
}