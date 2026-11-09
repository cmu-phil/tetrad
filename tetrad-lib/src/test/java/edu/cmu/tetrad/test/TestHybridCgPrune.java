package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeSignificance;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.hybridcg.HybridCgPruneReport;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link HybridCgEdgeSignificance#backwardPrune}: null edges are removed and real edges kept at high
 * seeded frequency, a correlated null parent does not drag the real parent out with it, the report is consistent
 * with the proposed graph and does not modify the input PM, and never-testable edges are kept and listed rather
 * than removed.
 */
public final class TestHybridCgPrune {

    /**
     * X -> Y (coef 1), Z -> Y (coef 0), D -> Y (distinct means). Optionally X -> Z (coef 1, small noise), making Z a
     * correlated null parent of Y.
     */
    private record Fixture(HybridCgPm pm, HybridCgIm im, Node x, Node z, Node d, Node y) {
    }

    private static Fixture fixture(boolean zCorrelatedWithX) {
        Node x = new ContinuousVariable("X");
        Node z = new ContinuousVariable("Z");
        Node y = new ContinuousVariable("Y");
        Node d = new DiscreteVariable("D", List.of("a", "b", "c"));
        Graph g = new EdgeListGraph(List.of(x, z, d, y));
        g.addDirectedEdge(x, y);
        g.addDirectedEdge(z, y);
        g.addDirectedEdge(d, y);
        if (zCorrelatedWithX) g.addDirectedEdge(x, z);

        Map<Node, Boolean> flags = new LinkedHashMap<>();
        flags.put(x, false);
        flags.put(z, false);
        flags.put(y, false);
        flags.put(d, true);
        Map<Node, List<String>> cats = new LinkedHashMap<>();
        cats.put(d, List.of("a", "b", "c"));

        HybridCgPm pm = new HybridCgPm(g, List.of(x, z, d, y), flags, cats);
        HybridCgIm im = new HybridCgIm(pm);

        int xi = pm.indexOf(x);
        im.setMean(xi, 0, 0.0);
        im.setVariance(xi, 0, 1.0);

        int zi = pm.indexOf(z);
        im.setMean(zi, 0, 0.0);
        if (zCorrelatedWithX) {
            im.setCoefficient(zi, 0, 0, 1.0);
            im.setVariance(zi, 0, 0.25);
        } else {
            im.setVariance(zi, 0, 1.0);
        }

        int di = pm.indexOf(d);
        for (int k = 0; k < 3; k++) im.setProbability(di, 0, k, 1.0 / 3.0);

        int yi = pm.indexOf(y);
        int[] cps = pm.getContinuousParents(yi);
        int tx = -1, tz = -1;
        for (int t = 0; t < cps.length; t++) {
            if (pm.getNodes()[cps[t]].equals(x)) tx = t;
            if (pm.getNodes()[cps[t]].equals(z)) tz = t;
        }
        double[] means = {0.0, 1.0, 2.0};
        for (int row = 0; row < pm.getNumRows(yi); row++) {
            im.setMean(yi, row, means[row]);
            im.setCoefficient(yi, row, tx, 1.0);
            im.setCoefficient(yi, row, tz, 0.0);
            im.setVariance(yi, row, 1.0);
        }
        return new Fixture(pm, im, x, z, d, y);
    }

    private static DataSet sample(HybridCgIm im, int n) {
        return im.toDataSet(im.sample(n));
    }

    /**
     * Over seeded replications, the null edge Z -> Y is removed at roughly its null rate of exceeding alpha, and the
     * real edges X -> Y and D -> Y are never removed. The report's graph is the input graph minus exactly the
     * deletions, every recorded p exceeds alpha, and the input PM's graph is untouched.
     */
    @Test
    public void testNullEdgeRemovedRealEdgesKept() {
        RandomUtil.getInstance().setSeed(771001L);
        int reps = 30, zRemoved = 0;
        for (int rep = 0; rep < reps; rep++) {
            Fixture f = fixture(false);
            DataSet ds = sample(f.im, 500);
            int inputEdges = f.pm.getGraph().getNumEdges();

            HybridCgPruneReport report = HybridCgEdgeSignificance.backwardPrune(f.pm, ds, 0.05, false);
            Graph pruned = report.getPrunedGraph();

            assertEquals(inputEdges, f.pm.getGraph().getNumEdges());          // input PM unmodified
            assertNotNull(pruned.getEdge(pruned.getNode("X"), pruned.getNode("Y")));
            assertNotNull(pruned.getEdge(pruned.getNode("D"), pruned.getNode("Y")));
            assertEquals(inputEdges - report.getDeletions().size(), pruned.getNumEdges());
            for (HybridCgPruneReport.Deletion del : report.getDeletions()) {
                assertTrue(del.getPValue() > 0.05);
            }
            if (pruned.getEdge(pruned.getNode("Z"), pruned.getNode("Y")) == null) zRemoved++;
        }
        // The null edge's LRT p exceeds .05 about 95% of the time; allow generous Monte Carlo slack.
        assertTrue("Z->Y removed in " + zRemoved + "/" + reps, zRemoved >= 24);
    }

    /**
     * With Z a noisy copy of X and a zero coefficient on Y, backward elimination should remove Z -> Y (no unique
     * contribution given X) while keeping X -> Y — the redundant pair loses the right member, not both.
     */
    @Test
    public void testCorrelatedNullParentDoesNotRemoveRealParent() {
        RandomUtil.getInstance().setSeed(771002L);
        int reps = 30, zRemovedXKept = 0;
        for (int rep = 0; rep < reps; rep++) {
            Fixture f = fixture(true);
            DataSet ds = sample(f.im, 500);
            HybridCgPruneReport report = HybridCgEdgeSignificance.backwardPrune(f.pm, ds, 0.05, false);
            Graph pruned = report.getPrunedGraph();
            boolean xKept = pruned.getEdge(pruned.getNode("X"), pruned.getNode("Y")) != null;
            boolean zGone = pruned.getEdge(pruned.getNode("Z"), pruned.getNode("Y")) == null;
            assertTrue("X->Y must survive", xKept);
            assertNotNull("X->Z is real and must survive", pruned.getEdge(pruned.getNode("X"), pruned.getNode("Z")));
            if (zGone) zRemovedXKept++;
        }
        assertTrue("Z->Y removed with X kept in " + zRemovedXKept + "/" + reps, zRemovedXKept >= 24);
    }

    /**
     * A discrete parent that is constant in the sampled data leaves 0 identifiable df for its edge at every round:
     * the edge must be kept, not removed, and listed as untested in the report.
     */
    @Test
    public void testUntestableEdgeKeptAndListed() {
        RandomUtil.getInstance().setSeed(771003L);
        Node d2 = new DiscreteVariable("D2", List.of("a", "b"));
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Graph g = new EdgeListGraph(List.of(d2, x, y));
        g.addDirectedEdge(d2, y);
        g.addDirectedEdge(x, y);

        Map<Node, Boolean> flags = new LinkedHashMap<>();
        flags.put(d2, true);
        flags.put(x, false);
        flags.put(y, false);
        Map<Node, List<String>> cats = new LinkedHashMap<>();
        cats.put(d2, List.of("a", "b"));

        HybridCgPm pm = new HybridCgPm(g, List.of(d2, x, y), flags, cats);
        HybridCgIm im = new HybridCgIm(pm);
        int d2i = pm.indexOf(d2);
        im.setProbability(d2i, 0, 0, 1.0);      // constant: category b never sampled
        im.setProbability(d2i, 0, 1, 0.0);
        int xi = pm.indexOf(x);
        im.setMean(xi, 0, 0.0);
        im.setVariance(xi, 0, 1.0);
        int yi = pm.indexOf(y);
        for (int row = 0; row < pm.getNumRows(yi); row++) {
            im.setMean(yi, row, 0.0);
            im.setCoefficient(yi, row, 0, 1.0);
            im.setVariance(yi, row, 1.0);
        }

        DataSet ds = sample(im, 300);
        HybridCgPruneReport report = HybridCgEdgeSignificance.backwardPrune(pm, ds, 0.05, false);
        Graph pruned = report.getPrunedGraph();

        assertNotNull(pruned.getEdge(pruned.getNode("D2"), pruned.getNode("Y")));
        assertNotNull(pruned.getEdge(pruned.getNode("X"), pruned.getNode("Y")));
        assertFalse(report.getUntested().isEmpty());
        for (HybridCgPruneReport.Deletion del : report.getDeletions()) {
            assertFalse("untestable edge must not be deleted", del.getParentName().equals("D2"));
        }
    }

    /**
     * Sanity on report bookkeeping when nothing should be pruned: strong edges everywhere, no deletions, pruned
     * graph equals the input graph edge-for-edge, and no untested entries on well-populated data.
     */
    @Test
    public void testNothingToPrune() {
        RandomUtil.getInstance().setSeed(771004L);
        Fixture f = fixture(false);
        // Make Z real too.
        int yi = f.pm.indexOf(f.y);
        int[] cps = f.pm.getContinuousParents(yi);
        for (int t = 0; t < cps.length; t++) {
            if (f.pm.getNodes()[cps[t]].equals(f.z)) {
                for (int row = 0; row < f.pm.getNumRows(yi); row++) f.im.setCoefficient(yi, row, t, 1.0);
            }
        }
        DataSet ds = sample(f.im, 1000);
        HybridCgPruneReport report = HybridCgEdgeSignificance.backwardPrune(f.pm, ds, 0.05, false);
        assertTrue(report.getDeletions().isEmpty());
        assertTrue(report.getUntested().isEmpty());
        assertEquals(f.pm.getGraph().getNumEdges(), report.getPrunedGraph().getNumEdges());
        for (edu.cmu.tetrad.graph.Edge e : f.pm.getGraph().getEdges()) {   // same edges, same orientations
            assertTrue(report.getPrunedGraph().getEdges().contains(e));
        }
    }
}
