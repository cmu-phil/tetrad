package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Sanity test for Adjustment:
 * For random graphs and random (X,Y), each returned adjustment set Z must:
 * - NOT block any amenable (causal) path X => Y (i.e., those must stay m-connecting)
 * - Block every backdoor path between X and Y (i.e., those must be NOT m-connecting)
 * <p>
 * Graph type tested here is "PDAG" to match the helper methods used.
 * Path-length cap (L) is 7 by default; adjust as you see fit.
 */
public class TestAdjustment {

    @Test(timeout = 30_000)
    public void testAdjustmentSetsRespectPaths() {
        RandomUtil.getInstance().setSeed(42L);

        Graph graph = RandomGraph.randomGraph(20, 0, 40, 100, 100, 100, false);
        List<Node> nodes = graph.getNodes();

        final String GRAPH_TYPE = "PDAG";
        final int L = 7;
        final int MAX_SETS = 5;
        final int RADIUS = 3;
        final int TGT_HUG = 1;

        for (int run = 0; run < 20; run++) {
            int i = RandomUtil.getInstance().nextInt(nodes.size());
            int j = RandomUtil.getInstance().nextInt(nodes.size());
            if (i == j) {
                run--;
                continue;
            }

            Node x = nodes.get(i);
            Node y = nodes.get(j);

            List<Set<Node>> sets = graph.paths().adjustmentSets(
                    x, y, GRAPH_TYPE, MAX_SETS, RADIUS, TGT_HUG, L);

            Set<List<Node>> amenablePaths = graph.paths().getAmenablePathsPdagMag(x, y, L);
            if (amenablePaths == null) amenablePaths = java.util.Collections.emptySet();

            Set<List<Node>> backdoorPaths = graph.paths().getBackdoorPaths(x, y, GRAPH_TYPE, L);
            if (backdoorPaths == null) backdoorPaths = java.util.Collections.emptySet();

            if (sets == null) continue; // nothing to check

            for (Set<Node> Z : sets) {
                // Amenable (causal) paths must remain open given Z
                for (List<Node> p : amenablePaths) {
                    boolean open = graph.paths().isMConnectingPath(p, Z, /*disallowSelection*/ false);
                    assertTrue("Amenable path was blocked by Z. X=" + x + ", Y=" + y
                                    + ", |Z|=" + Z.size() + ", Z=" + Z
                                    + ", pathLen=" + p.size() + ", path=" + p,
                            open);
                }
                // Backdoor paths must be blocked given Z
                for (List<Node> p : backdoorPaths) {
                    boolean open = graph.paths().isMConnectingPath(p, Z, /*disallowSelection*/ false);
                    assertFalse("Backdoor path remained open under Z. X=" + x + ", Y=" + y
                                    + ", |Z|=" + Z.size() + ", Z=" + Z
                                    + ", pathLen=" + p.size() + ", path=" + p,
                            open);
                }
            }
        }
    }
}
