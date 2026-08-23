package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.TsUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the knowledge semantics of {@code TsUtils.createLagData(data, numLags, knowledge)}
 * when the input knowledge places base variables in tiers: the input tier order must apply
 * to the base variables at EVERY pair of lags, not just within a lag.
 *
 * <p>Background: the expanded knowledge interleaves time blocks with the input tiers,
 * assigning tier index (numLags - lag) * k + t. That total order forbids future-to-past
 * edges and within-lag tier violations, but it cannot forbid a deeper-lag variable in a
 * later input tier from pointing into a more recent variable in an earlier input tier
 * (e.g. Rain:4 --&gt; day with day in tier 0 and weather in tier 1) - no total tier order
 * can express the intended product order (time x input tier). Those cases must be covered
 * by explicit forbidden edges, which this test pins.
 */
public class TestTsKnowledgeCrossLagTiers {

    private static DataSet data(String... names) {
        int n = 30;
        Random rng = new Random(1L);
        List<Node> vars = new ArrayList<>();
        for (String name : names) vars.add(new ContinuousVariable(name));
        double[][] d = new double[n][names.length];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < names.length; j++)
                d[i][j] = rng.nextGaussian();
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static Knowledge laggedKnowledge(int numLags) {
        DataSet ds = data("day", "month", "Temperature", "Rain", "Fire");
        Knowledge base = new Knowledge();
        base.addToTier(0, "day");
        base.addToTier(0, "month");
        base.addToTier(1, "Temperature");
        base.addToTier(1, "Rain");
        base.addToTier(2, "Fire");
        return TsUtils.createLagData(ds, numLags, base).getKnowledge();
    }

    /**
     * The motivating violations: weather (tier 1) at a deeper lag must not point into the
     * calendar variables (tier 0) at any more recent lag - including lag 0.
     */
    @Test
    public void testDeeperLagLaterTierIntoEarlierTierForbidden() {
        Knowledge k = laggedKnowledge(4);

        // The three edge families observed in the motivating output:
        assertTrue(k.isForbidden("Rain:4", "day"));
        assertTrue(k.isForbidden("Temperature:3", "month"));
        assertTrue(k.isForbidden("Temperature:4", "month:1"));

        // The rule is general over lags and tier pairs:
        assertTrue(k.isForbidden("Rain:1", "day"));
        assertTrue(k.isForbidden("Rain:2", "month:1"));
        assertTrue(k.isForbidden("Fire:2", "Rain:1"));      // tier 2 into tier 1
        assertTrue(k.isForbidden("Fire:1", "Temperature")); // tier 2 into tier 1, lag 0
        assertTrue(k.isForbidden("Fire:4", "day:3"));       // tier 2 into tier 0
    }

    /**
     * The fix must not forbid anything the intended semantics allow.
     */
    @Test
    public void testAllowedCrossLagEdgesRemainAllowed() {
        Knowledge k = laggedKnowledge(4);

        // Earlier-or-equal input tier at a deeper lag into a later input tier: allowed.
        assertFalse(k.isForbidden("Rain:2", "Fire:1"));       // weather -> later fire
        assertFalse(k.isForbidden("Temperature:1", "Fire"));  // weather -> current fire
        assertFalse(k.isForbidden("day:2", "Rain:1"));        // tier 0 -> tier 1
        assertFalse(k.isForbidden("month:1", "Fire"));        // tier 0 -> tier 2

        // Same input tier across lags: allowed (autoregression and cross-effects).
        assertFalse(k.isForbidden("Rain:1", "Rain"));
        assertFalse(k.isForbidden("Rain:2", "Temperature:1"));
        assertFalse(k.isForbidden("day:1", "month"));

        // Within a lag, input tier order: earlier tier into later tier allowed.
        assertFalse(k.isForbidden("Temperature", "Fire"));
        assertFalse(k.isForbidden("day", "Rain"));
    }

    /**
     * The pre-existing coverage must be intact: future-to-past edges and within-lag tier
     * violations remain forbidden.
     */
    @Test
    public void testExistingCoverageIntact() {
        Knowledge k = laggedKnowledge(4);

        // Future to past (any tiers):
        assertTrue(k.isForbidden("Rain", "Rain:1"));
        assertTrue(k.isForbidden("day", "Fire:2"));
        assertTrue(k.isForbidden("Fire:1", "Fire:3"));

        // Within-lag tier violations:
        assertTrue(k.isForbidden("Fire", "Temperature"));
        assertTrue(k.isForbidden("Rain:2", "day:2"));
        assertTrue(k.isForbidden("Fire:3", "month:3"));
    }

    /**
     * Sanity at a different lag count, including numLags = 1.
     */
    @Test
    public void testLagOne() {
        Knowledge k = laggedKnowledge(1);
        assertTrue(k.isForbidden("Rain:1", "day"));
        assertTrue(k.isForbidden("Fire:1", "Temperature"));
        assertFalse(k.isForbidden("Rain:1", "Fire"));
        assertFalse(k.isForbidden("Rain:1", "Rain"));
    }
}
