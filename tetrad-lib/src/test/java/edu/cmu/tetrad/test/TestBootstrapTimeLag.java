package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.EdgeTypeProbability;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the fix for bootstrapping time-lag searches: rows must be resampled AFTER lagging, not before.
 * <p>
 * Before the fix, each bootstrap replicate row-resampled the raw series and then lagged the scrambled rows, so
 * X:1 in a replicate was an unrelated row of the original series. On the VAR(1) below (X_{t-1} -> Y_t, no
 * contemporaneous X-Y edge) the unpatched bootstrap reported X:1 -> Y at frequency 0.00 and a contemporaneous
 * X -- Y edge at 1.00: not merely noisy, but confidently wrong, because scrambling the rows keeps the
 * contemporaneous correlation induced by the shared lag while destroying the transition that explains it.
 * (Additionally, all but one replicate in the single-dataset path threw from createLagData because the wrapper's
 * knowledge field had been overwritten with lagged knowledge by the first replicate.)
 */
public class TestBootstrapTimeLag {

    /**
     * X_t = 0.5 X_{t-1} + e; Y_t = 0.5 Y_{t-1} + 0.8 X_{t-1} + e; Z_t = 0.5 Z_{t-1} + 0.8 Y_t + e.
     */
    private static DataSet series(Random rnd, int n) {
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"), new ContinuousVariable("Z"));
        DataSet d = new BoxDataSet(new DoubleDataBox(n, 3), vars);
        double x = 0, y = 0, z = 0;
        for (int t = 0; t < n; t++) {
            double nx = 0.5 * x + rnd.nextGaussian();
            double ny = 0.5 * y + 0.8 * x + rnd.nextGaussian();
            double nz = 0.5 * z + 0.8 * ny + rnd.nextGaussian();
            x = nx;
            y = ny;
            z = nz;
            d.setDouble(t, 0, x);
            d.setDouble(t, 1, y);
            d.setDouble(t, 2, z);
        }
        return d;
    }

    private static double adjacencyFrequency(Graph sampling, String a, String b) {
        Edge e = sampling.getEdge(sampling.getNode(a), sampling.getNode(b));
        if (e == null) return 0;
        double p = 0;
        for (EdgeTypeProbability etp : e.getEdgeTypeProbabilities()) {
            if (etp.getEdgeType() != EdgeTypeProbability.EdgeType.nil) p += etp.getProbability();
        }
        return p;
    }

    private static Parameters params() {
        Parameters p = new Parameters();
        p.set(Params.TIME_LAG, 1);
        p.set(Params.NUMBER_RESAMPLING, 30);
        p.set(Params.PERCENT_RESAMPLE_SIZE, 100);
        p.set(Params.RESAMPLING_WITH_REPLACEMENT, true);
        p.set(Params.ADD_ORIGINAL_DATASET, false);
        p.set(Params.SEED, 3);
        p.set(Params.PENALTY_DISCOUNT, 2);
        p.set(Params.VERBOSE, false);
        return p;
    }

    private static void checkFrequencies(Graph result, String label) {
        Graph sampling = ((EdgeListGraph) result).getAncillaryGraph("samplingGraph");
        double lagEdge = adjacencyFrequency(sampling, "X:1", "Y");
        double contemp = adjacencyFrequency(sampling, "X", "Y");
        double autoX = adjacencyFrequency(sampling, "X:1", "X");
        assertTrue(label + ": expected X:1 -> Y to be recovered in most replicates, got " + lagEdge, lagEdge >= 0.9);
        assertTrue(label + ": expected the autoregressive X:1 -> X edge, got " + autoX, autoX >= 0.9);
        assertTrue(label + ": expected no contemporaneous X -- Y edge, got " + contemp, contemp <= 0.2);
    }

    @Test
    public void testMultiDatasetBootstrapLagsBeforeResampling() throws Exception {
        Random rnd = new Random(7);
        DataSet a = series(rnd, 150);
        DataSet b = series(rnd, 150);
        Parameters p = params();

        Images images = new Images(new SemBicScore());
        Graph g = images.search(new ArrayList<DataModel>(List.of(a, b)), p);

        checkFrequencies(g, "IMaGES");

        // The wrapper's knowledge is only temporarily the lagged knowledge; it must be restored afterwards, and
        // the caller's parameters must not have been modified.
        Knowledge after = images.getKnowledge();
        assertTrue("Wrapper knowledge should be restored to (empty) base knowledge", after == null || after.isEmpty());
        assertEquals(1, p.getInt(Params.TIME_LAG));
    }

    @Test
    public void testSingleDatasetBootstrapLagsBeforeResampling() throws Exception {
        Random rnd = new Random(11);
        DataSet a = series(rnd, 200);
        Parameters p = params();

        Boss boss = new Boss(new SemBicScore());
        Graph g = boss.search(a, p);

        checkFrequencies(g, "BOSS");
        Knowledge after = boss.getKnowledge();
        assertTrue("Wrapper knowledge should be restored to (empty) base knowledge", after == null || after.isEmpty());
        assertEquals(1, p.getInt(Params.TIME_LAG));
    }

    @Test
    public void testUserBaseKnowledgeIsRestored() throws Exception {
        Random rnd = new Random(5);
        DataSet a = series(rnd, 150);
        Parameters p = params();

        Knowledge base = new Knowledge();
        base.setForbidden("Z", "X");

        Boss boss = new Boss(new SemBicScore());
        boss.setKnowledge(base);
        boss.search(a, p);

        Knowledge after = boss.getKnowledge();
        assertTrue("User's base knowledge should be restored", after.isForbidden("Z", "X"));
        for (String name : after.getVariables()) {
            assertTrue("Restored knowledge should carry no lag suffixes: " + name, !name.contains(":"));
        }
    }
}
