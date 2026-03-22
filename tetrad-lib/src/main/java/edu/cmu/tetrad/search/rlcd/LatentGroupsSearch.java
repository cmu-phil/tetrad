package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Tsc;

import java.util.*;

/**
 * Phase 2 of RLCD: latent cluster discovery using TSC (Trek Separation Clustering).
 *
 * <p>The original RLCD Algorithms 3-5 test rank(Σ_{C∪X, N∪X}) where X appears on
 * both sides of the cross-covariance matrix — a setup that breaks standard canonical
 * correlation rank tests which require disjoint variable sets. TSC avoids this by
 * always testing rank(cluster, complement), which is always disjoint.</p>
 *
 * <p>This implementation:
 * <ol>
 *   <li>Runs TSC on the combined group + neighbour variables to find clusters.</li>
 *   <li>Creates a latent node for each cluster found.</li>
 *   <li>Wires latent → indicator edges in the adjacency matrix.</li>
 *   <li>Attempts to recover measured parents of the latents from the neighbour pool.</li>
 * </ol>
 */
final class LatentGroupsSearch {

    private LatentGroupsSearch() {
    }

    /**
     * Runs local latent discovery for one clique group.
     *
     * @param dataSet      full dataset (observed variables only)
     * @param group        observed variables in the clique group (XQ)
     * @param neighbourSet neighbours of the group in the CI skeleton (NQ)
     * @param localAdj     adjacency sub-matrix over {@code group} variables
     * @param params       RLCD parameters
     * @return adjacency over [groupVars | new latents]
     */
    static LatentGroupsResult runLocalSearch(DataSet dataSet,
                                             List<Node> group,
                                             Set<Node> neighbourSet,
                                             int[][] localAdj,
                                             RLCDParams params) {
        if (params.getRankTestFactory() == null) {
            return new LatentGroupsResult(localAdj);
        }

        double alpha = params.getAlphaByK().length > 0 ? params.getAlphaByK()[0] : 0.01;
        int groupSize = group.size();

        // ---- Step 1: run TSC on the group variables to find clusters ----
        // TSC expects all variables to come from the dataset's variable list,
        // so we restrict to variables that appear in the dataset.
        List<Node> dataVars = dataSet.getVariables();
        Map<Node, Integer> nodeToDataCol = new HashMap<>();
        for (int i = 0; i < dataVars.size(); i++) nodeToDataCol.put(dataVars.get(i), i);

        // Only pass variables that exist in the dataset to TSC.
        List<Node> tscInput = new ArrayList<>();
        for (Node n : group) {
            if (nodeToDataCol.containsKey(n)) tscInput.add(n);
        }
        // Also include neighbours so TSC can see cross-cluster structure.
        for (Node n : neighbourSet) {
            if (nodeToDataCol.containsKey(n) && !tscInput.contains(n)) tscInput.add(n);
        }

        if (tscInput.size() < 3) {
            // Too few variables to find any cluster.
            return new LatentGroupsResult(localAdj);
        }

        Tsc tsc = new Tsc(tscInput, new CovarianceMatrix(dataSet));
        tsc.setAlpha(alpha);
        tsc.setRmax(params.getMaxK());
        tsc.setMinRedundancy(0);
        tsc.setEffectiveSampleSize(dataSet.getNumRows());

        Map<Set<Integer>, Integer> clusters = tsc.findClusters();

        System.out.println("TSC found " + clusters.size() + " clusters.");
        for (Map.Entry<Set<Integer>, Integer> entry : clusters.entrySet()) {
            System.out.println("  Cluster indices " + entry.getKey()
                    + " -> nodes: " + entry.getKey().stream()
                    .map(i -> tscInput.get(i).getName())
                    .reduce((a, b) -> a + ", " + b).orElse("")
                    + " rank=" + entry.getValue());
        }

        if (clusters.isEmpty()) {
            return new LatentGroupsResult(localAdj);
        }

        // ---- Step 2: build result adjacency [groupVars | newLatents] ----
        // We only add latents for clusters whose members overlap with group
        // (not pure-neighbour clusters).
        List<Set<Node>> groupClusters = new ArrayList<>();
        for (Set<Integer> clusterIdx : clusters.keySet()) {
            Set<Node> clusterNodes = new LinkedHashSet<>();
            for (int i : clusterIdx) clusterNodes.add(tscInput.get(i));

            // Only include clusters that contain at least one group variable.
            boolean hasGroupVar = false;
            for (Node n : clusterNodes) {
                if (group.contains(n)) { hasGroupVar = true; break; }
            }
            if (hasGroupVar) groupClusters.add(clusterNodes);
        }

        int numNewLatents = groupClusters.size();
        if (numNewLatents == 0) {
            return new LatentGroupsResult(localAdj);
        }

        // Result adjacency: rows/cols = [group variables | new latents]
        int sz = groupSize + numNewLatents;
        int[][] result = new int[sz][sz];

        // Copy existing measured-measured adjacency.
        for (int i = 0; i < groupSize; i++) {
            System.arraycopy(localAdj[i], 0, result[i], 0, Math.min(groupSize, localAdj[i].length));
        }

        // Wire latent → indicator edges.
        for (int li = 0; li < groupClusters.size(); li++) {
            int latentRow = groupSize + li;
            Set<Node> clusterNodes = groupClusters.get(li);

            for (Node indicator : clusterNodes) {
                int indicatorCol = group.indexOf(indicator);
                if (indicatorCol >= 0) {
                    result[latentRow][indicatorCol] = 1; // latent → indicator
                }
            }
        }

        // ---- Step 3: attempt to recover measured parents of each latent ----
        // A neighbour node is a measured parent of latent L if it has rank-1
        // cross-covariance with the indicators of L (i.e., it is correlated
        // with all indicators through the latent).
        if (!neighbourSet.isEmpty()) {
            List<Node> neighbours = new ArrayList<>(neighbourSet);
            for (int li = 0; li < groupClusters.size(); li++) {
                int latentRow = groupSize + li;
                Set<Node> indicators = groupClusters.get(li);

                // Collect indicator data-set column indices.
                int[] indCols = indicators.stream()
                        .mapToInt(n -> nodeToDataCol.getOrDefault(n, -1))
                        .filter(i -> i >= 0)
                        .toArray();

                if (indCols.length == 0) continue;

                RankTest rankTest = params.getRankTestFactory().create(dataSet);

                for (Node nb : neighbours) {
                    Integer nbCol = nodeToDataCol.get(nb);
                    if (nbCol == null) continue;

                    int[] parentCols = new int[]{nbCol};

                    // rank(parentCols, indCols) == 1 means nb correlates with
                    // the indicators through a single channel (the latent).
                    boolean isParent = rankTest.test(parentCols, indCols, 1, alpha)
                            && !rankTest.test(parentCols, indCols, 0, alpha);

                    if (isParent) {
                        int nbGroupIdx = group.indexOf(nb);
                        if (nbGroupIdx >= 0) {
                            result[nbGroupIdx][latentRow] = 1; // nb → latent
                        }
                    }
                }
            }
        }

        System.out.println("runLocalSearch: group=" + groupSize
                + " neighbours=" + neighbourSet.size()
                + " newLatents=" + numNewLatents
                + " result adj size=" + result.length);

        return new LatentGroupsResult(result);
    }
}
