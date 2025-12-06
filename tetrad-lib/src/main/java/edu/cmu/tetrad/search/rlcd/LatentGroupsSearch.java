package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.List;
import java.util.Set;

/**
 * Placeholder for Java port of LatentGroups + rlcd_find_latent(...) +
 * getLfromLatentGroups(...). For now this simply returns the measured-only
 * localAdj unchanged.
 */
final class LatentGroupsSearch {

    private LatentGroupsSearch() {}

    static LatentGroupsResult runLocalSearch(DataSet dataSet,
                                             List<Node> group,
                                             Set<Node> neighbourSet,
                                             int[][] localAdj,
                                             RLCDParams params) {
        // TODO: port LatentGroups and rlcd_find_latent from Python here.
        // Use params.getRankTestFactory() to obtain a RankTest that wraps
        // the rank-test logic (e.g., based on covariance submatrices).
//        return new LatentGroupsResult(localAdj);

        int m = localAdj.length;
        int[][] out = new int[m + 1][m + 1];

        // copy measured-measured block
        for (int i = 0; i < m; i++) {
            System.arraycopy(localAdj[i], 0, out[i], 0, m);
        }

        int latentIdx = m;
        // make latent a parent of all measured in this group (latent -> X)
        for (int i = 0; i < m; i++) {
            out[latentIdx][i] = 1;    // latent -> Xi
            // optional: we could leave out[ i ][latentIdx] = 0
        }

        return new LatentGroupsResult(out);
    }
}