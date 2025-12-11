package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Phases 2 & 3 of RLCD: latent cluster discovery & refinement.
 *
 * This is currently a scaffold that mirrors the Python control-flow and the
 * high-level algorithm structure in the ICLR paper, but the actual rank-based
 * cluster search is left as a TODO.
 */
final class LatentDiscovery {

    private LatentDiscovery() {
    }

    static LatentDiscoveryResult runPhase2And3(DataSet dataSet,
                                               Phase1Result phase1,
                                               RLCDParams params) {

        List<Node> measuredVars = dataSet.getVariables();
        Map<Node, Integer> idx = phase1.getNodeIndex();
        int n = measuredVars.size();

        // Start from stage-1 adjacency over measured variables.
        int[][] adj = adjacencyFromGraph(phase1.getSkeleton(), idx, n);

        List<List<Node>> partitions = phase1.getPartitions();

        // This will be extended with latent nodes as we go.
        List<Node> allNodes = new ArrayList<>(measuredVars);

        if (params.getStages() >= 2) {
            for (List<Node> group : partitions) {
                // Extract local indices.
                int[] groupIdx = group.stream().mapToInt(idx::get).toArray();
                Set<Node> neighbourSet = neighbourSet(adj, measuredVars, groupIdx);

                // Build local adjacency submatrix.
                int[][] localAdj = submatrix(adj, groupIdx);

                // Hook: call Java version of LatentGroups + rlcd_find_latent.
                LatentGroupsResult localResult =
                        LatentGroupsSearch.runLocalSearch(
                                dataSet,
                                group,
                                neighbourSet,
                                localAdj,
                                params
                        );

                // Merge localResult into global adjacency and node list.
                adj = mergeLocalResult(adj, allNodes, group, groupIdx, localResult);
            }
        }

        // TODO: Phase 3 "refinement" can be another pass over adj / LatentGroups structures
        // once the full latent representation is implemented.

        // Build a Tetrad Graph with measured + latent nodes.
        Graph g = adjacencyToMixedGraph(adj, allNodes);

        return new LatentDiscoveryResult(g, allNodes, adj);
    }

    private static int[][] adjacencyFromGraph(Graph g,
                                              Map<Node, Integer> idx,
                                              int nMeasured) {
        int n = nMeasured;
        int[][] adj = new int[n][n];
        for (Edge e : g.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();
            Integer ix = idx.get(x);
            Integer iy = idx.get(y);
            if (ix == null || iy == null) continue;
            if (ix.equals(iy)) continue;
            adj[ix][iy] = 1;
            adj[iy][ix] = 1;
        }
        return adj;
    }

    private static Set<Node> neighbourSet(int[][] adj,
                                          List<Node> measured,
                                          int[] groupIdx) {
        Set<Integer> groupSet = new HashSet<>();
        for (int i : groupIdx) groupSet.add(i);

        Set<Node> nb = new LinkedHashSet<>();
        for (int i : groupIdx) {
            for (int j = 0; j < measured.size(); j++) {
                if (groupSet.contains(j)) continue;
                if (adj[i][j] != 0) {
                    nb.add(measured.get(j));
                }
            }
        }
        return nb;
    }

    private static int[][] submatrix(int[][] adj, int[] indices) {
        int m = indices.length;
        int[][] sub = new int[m][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                sub[i][j] = adj[indices[i]][indices[j]];
            }
        }
        return sub;
    }

    /**
     * Merge local latent result back into global adjacency, padding with new
     * latent nodes as needed. This is the Java analog of:
     *
     * - computing num_new_latent
     * - padding Adj with extra rows/cols
     * - copy_by_idx(...) for x-x, x-l, l-x, l-l blocks
     * - naming latents L1, L2, ... [oai_citation:9‡GitHub](https://github.com/dongxinshuai/scm-identify/raw/main/StructureLearning/RLCD/RLCD_alg.py)
     */
    private static int[][] mergeLocalResult(int[][] adj,
                                            List<Node> allNodes,
                                            List<Node> group,
                                            int[] groupIdx,
                                            LatentGroupsResult localResult) {

        int[][] localAdjOut = localResult.getAdjacency();
        int numGroup = groupIdx.length;
        int numNewLatent =
                localAdjOut.length - numGroup;  // same logic as Python

        if (numNewLatent <= 0) {
            // Nothing to merge.
            return adj;
        }

        // 1) Pad global adjacency.
        int oldN = adj.length;
        int newN = oldN + numNewLatent;
        int[][] newAdj = new int[newN][newN];
        for (int i = 0; i < oldN; i++) {
            System.arraycopy(adj[i], 0, newAdj[i], 0, oldN);
        }

        // 2) Create new latent nodes.
        int latentStartIndex = allNodes.size();
        for (int i = 0; i < numNewLatent; i++) {
            String name = "L" + (latentStartIndex + i + 1);
            Node latent = new GraphNode(name);
            latent.setNodeType(NodeType.LATENT);
            allNodes.add(latent);
        }

        // Map: positions in localAdjOut -> positions in global newAdj.
        int[] localToGlobalRow = new int[localAdjOut.length];
        int[] localToGlobalCol = new int[localAdjOut.length];

        // first the measured nodes in the group
        for (int i = 0; i < numGroup; i++) {
            localToGlobalRow[i] = groupIdx[i];
            localToGlobalCol[i] = groupIdx[i];
        }
        // then the new latent nodes
        for (int i = numGroup; i < localAdjOut.length; i++) {
            int globalIdx = oldN + (i - numGroup);
            localToGlobalRow[i] = globalIdx;
            localToGlobalCol[i] = globalIdx;
        }

        // 3) Copy localAdjOut into newAdj using the index mapping.
        for (int i = 0; i < localAdjOut.length; i++) {
            for (int j = 0; j < localAdjOut[i].length; j++) {
                int gi = localToGlobalRow[i];
                int gj = localToGlobalCol[j];
                newAdj[gi][gj] = localAdjOut[i][j];
            }
        }

        return newAdj;
    }

    private static Graph adjacencyToMixedGraph(int[][] adj, List<Node> nodes) {
        Graph g = new EdgeListGraph(nodes);
        int n = nodes.size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                if (adj[i][j] == 0) continue;

                // Placeholder: treat non-zero entries as directed i→j for now.
                // Once you replicate the Python semantics (1 vs -1 vs -2) you
                // can map them to correct endpoint types.
                Node from = nodes.get(i);
                Node to = nodes.get(j);
                if (!g.isAdjacentTo(from, to)) {
                    g.addDirectedEdge(from, to);
                }
            }
        }
        return g;
    }
}