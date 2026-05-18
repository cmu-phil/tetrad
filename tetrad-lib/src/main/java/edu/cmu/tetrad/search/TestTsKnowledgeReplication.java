package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests the behavior of TsUtils.createLagData(DataSet, int, Knowledge) and its
 * interaction with BOSS via a ReplicatingGraph.
 */
public class TestTsKnowledgeReplication {

    /**
     * Default constructor.
     */
    public TestTsKnowledgeReplication() {}

    /**
     * Constructs a small 3-variable time series dataset, defines within-lag knowledge
     * over two tiers, expands it across lags, verifies the expansion, then runs BOSS
     * with the expanded knowledge and a replicating graph.
     */
    @Test
    public void testKnowledgeReplicationWithBoss() {

        // ── 1. Simulate a small 3-variable time series ──────────────────────────

        List<Node> nodes = new ArrayList<>();
        nodes.add(new ContinuousVariable("X"));
        nodes.add(new ContinuousVariable("Y"));
        nodes.add(new ContinuousVariable("Z"));

        // Build a simple chain DAG X -> Y -> Z as the base graph
        Graph baseGraph = new EdgeListGraph(nodes);
        baseGraph.addDirectedEdge(nodes.get(0), nodes.get(1)); // X -> Y
        baseGraph.addDirectedEdge(nodes.get(1), nodes.get(2)); // Y -> Z

        // Parameterize and simulate
        SemPm pm = new SemPm(baseGraph);
        SemIm im = new SemIm(pm);
        DataSet timeSeries = null;
        try {
            timeSeries = im.simulateData(200, false);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        // ── 2. Define within-lag knowledge over base variable names ─────────────
        //
        // Two tiers: tier 0 = {X}, tier 1 = {Y, Z}
        // This means X can cause Y and Z within a lag, but Y and Z cannot cause X.
        // Tier 1 is also forbidden within (Y and Z cannot cause each other).

        Knowledge withinLagKnowledge = new Knowledge();
        withinLagKnowledge.addToTier(0, "X");
        withinLagKnowledge.addToTier(1, "Y");
        withinLagKnowledge.addToTier(1, "Z");
        withinLagKnowledge.setTierForbiddenWithin(1, true);

        // ── 3. Create lagged data with knowledge expansion ───────────────────────

        int numLags = 2;
        DataSet laggedData = TsUtils.createLagData(timeSeries, numLags, withinLagKnowledge);

        // ── 4. Retrieve and verify the expanded knowledge ────────────────────────

        Knowledge expandedKnowledge = laggedData.getKnowledge();

        // With 2 non-empty input tiers and (numLags + 1) = 3 time slices,
        // we expect 2 * 3 = 6 expanded tiers.
        int expectedTiers = 2 * (numLags + 1);
        assertEquals("Expected " + expectedTiers + " expanded tiers",
                expectedTiers, expandedKnowledge.getNumTiers());

        // Check that variables land in the right expanded tiers.
        // Expanded tier = (numLags - lag) * k + t, where k = 2.
        //
        // lag=2 (earliest), t=0 -> expanded tier 0: X:2
        // lag=2 (earliest), t=1 -> expanded tier 1: Y:2, Z:2
        // lag=1,            t=0 -> expanded tier 2: X:1
        // lag=1,            t=1 -> expanded tier 3: Y:1, Z:1
        // lag=0 (latest),   t=0 -> expanded tier 4: X
        // lag=0 (latest),   t=1 -> expanded tier 5: Y, Z

        assertTrue("X:2 should be in expanded tier 0",
                expandedKnowledge.getTier(0).contains("X:2"));
        assertTrue("Y:2 should be in expanded tier 1",
                expandedKnowledge.getTier(1).contains("Y:2"));
        assertTrue("Z:2 should be in expanded tier 1",
                expandedKnowledge.getTier(1).contains("Z:2"));
        assertTrue("X:1 should be in expanded tier 2",
                expandedKnowledge.getTier(2).contains("X:1"));
        assertTrue("Y:1 should be in expanded tier 3",
                expandedKnowledge.getTier(3).contains("Y:1"));
        assertTrue("Z:1 should be in expanded tier 3",
                expandedKnowledge.getTier(3).contains("Z:1"));
        assertTrue("X should be in expanded tier 4",
                expandedKnowledge.getTier(4).contains("X"));
        assertTrue("Y should be in expanded tier 5",
                expandedKnowledge.getTier(5).contains("Y"));
        assertTrue("Z should be in expanded tier 5",
                expandedKnowledge.getTier(5).contains("Z"));

        // Check that tierForbiddenWithin is replicated for tier 1 (t=1) at each lag.
        assertTrue("Expanded tier 1 (Y:2, Z:2) should be forbidden within",
                expandedKnowledge.isTierForbiddenWithin(1));
        assertTrue("Expanded tier 3 (Y:1, Z:1) should be forbidden within",
                expandedKnowledge.isTierForbiddenWithin(3));
        assertTrue("Expanded tier 5 (Y, Z) should be forbidden within",
                expandedKnowledge.isTierForbiddenWithin(5));

        // Check that the tier ordering implies cross-lag forbidding —
        // a later-time variable should not be allowed to cause an earlier-time variable.
        assertTrue("Y -> X:2 should be forbidden (later time cannot cause earlier)",
                expandedKnowledge.isForbidden("Y", "X:2"));
        assertTrue("X -> Y:1 should be forbidden (lag-0 cannot cause lag-1 slice)",
                expandedKnowledge.isForbidden("X", "Y:1"));

        // Check within-lag tier ordering is respected —
        // Y and Z (tier 1 within lag) cannot cause X (tier 0 within lag).
        assertTrue("Y -> X should be forbidden within lag 0",
                expandedKnowledge.isForbidden("Y", "X"));
        assertTrue("Z -> X should be forbidden within lag 0",
                expandedKnowledge.isForbidden("Z", "X"));
        assertTrue("Y:1 -> X:1 should be forbidden within lag 1",
                expandedKnowledge.isForbidden("Y:1", "X:1"));
        assertTrue("Z:2 -> X:2 should be forbidden within lag 2",
                expandedKnowledge.isForbidden("Z:2", "X:2"));

        // ── 5. Run BOSS with the expanded knowledge and a replicating graph ──────

        SemBicScore score = new SemBicScore(new CovarianceMatrix(laggedData));
        PermutationSearch boss = new PermutationSearch(new Boss(score));
        boss.setReplicatingGraph(true);
        boss.setKnowledge(expandedKnowledge);

        Graph result = null;
        try {
            result = boss.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Basic sanity checks on the result
        assertNotNull("BOSS should return a non-null graph", result);
        assertFalse("Result graph should have nodes", result.getNodes().isEmpty());
        assertFalse("Result graph should have edges", result.getEdges().isEmpty());

        // The result should not violate the expanded knowledge
        assertFalse("Result graph should not violate expanded knowledge",
                expandedKnowledge.isViolatedBy(result));

        System.out.println("BOSS result graph:");
        System.out.println(result);
    }

    /**
     * Verifies that createLagData(DataSet, int, Knowledge) throws an
     * IllegalArgumentException when the supplied knowledge contains lagged
     * variable names (i.e. names containing ":").
     */
    @Test(expected = IllegalArgumentException.class)
    public void testLaggedVariableInKnowledgeThrows() {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new ContinuousVariable("X"));
        nodes.add(new ContinuousVariable("Y"));

        Graph baseGraph = new EdgeListGraph(nodes);
        baseGraph.addDirectedEdge(nodes.get(0), nodes.get(1));

        SemPm pm = new SemPm(baseGraph);
        SemIm im = new SemIm(pm);
        DataSet timeSeries = null;
        try {
            timeSeries = im.simulateData(100, false);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        // Supplying knowledge with a lagged variable name should throw
        Knowledge badKnowledge = new Knowledge();
        badKnowledge.addToTier(0, "X:1"); // illegal — lagged variable in input knowledge

        TsUtils.createLagData(timeSeries, 2, badKnowledge);
    }
}