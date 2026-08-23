package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * Pins the dataset-knowledge fallback in Images (IMaGES): when no knowledge has been set on
 * the algorithm but the data sets carry base-variable knowledge, that knowledge must be used
 * to seed the time-lag expansion (and the search generally). Previously the algorithm read
 * only its own knowledge field, so knowledge attached to the data - which is how the GUI's
 * multi-data-set path historically delivered it - was silently ignored, and time-lag runs
 * fell back to pure time tiers: time order was enforced while the user's tier order was
 * violated both within and across lags.
 */
public class TestImagesKnowledgeFallback {

    /**
     * Two variables where the data pressure runs AGAINST the knowledge: X is driven by Y
     * (contemporaneously and at lag 1), but the knowledge puts X in tier 0 and Y in tier 1,
     * so no edge may point into X. Without the knowledge the search will happily orient
     * into X; with it, edges into X are forbidden at every lag.
     */
    private static DataSet data(long seed, int n) {
        Random rng = new Random(seed);
        double[][] d = new double[n][2];
        double y = 0.0;
        for (int t = 0; t < n; t++) {
            y = 0.7 * y + rng.nextGaussian();
            double x = 0.8 * y + 0.3 * rng.nextGaussian();
            d[t][0] = x;
            d[t][1] = y;
        }
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static Knowledge tiers() {
        Knowledge k = new Knowledge();
        k.addToTier(0, "X");
        k.addToTier(1, "Y");
        return k;
    }

    private static String base(String name) {
        int i = name.indexOf(':');
        return i < 0 ? name : name.substring(0, i);
    }

    private static List<String> edgesIntoBase(Graph graph, String baseName) {
        List<String> out = new ArrayList<>();
        for (Edge edge : graph.getEdges()) {
            if (!edge.isDirected()) continue;
            // Identify the arrow end via the endpoints so canonicalization doesn't matter.
            Node tail = edge.getNode1();
            Node arrow = edge.getNode2();
            if (edge.getEndpoint1() == edu.cmu.tetrad.graph.Endpoint.ARROW) {
                tail = edge.getNode2();
                arrow = edge.getNode1();
            }
            if (base(arrow.getName()).equals(baseName) && !base(tail.getName()).equals(baseName)) {
                out.add(tail.getName() + " --> " + arrow.getName());
            }
        }
        return out;
    }

    /**
     * With knowledge attached only to the data sets (algorithm knowledge never set), the
     * time-lag search must still honor the base tiers: no edge from any lag of Y into any
     * lag of X.
     */
    @Test
    public void testDatasetKnowledgeReachesTimeLagSearch() throws InterruptedException {
        List<DataModel> dataSets = new ArrayList<>();
        for (long seed : new long[]{11L, 22L}) {
            DataSet ds = data(seed, 300);
            ds.setKnowledge(tiers());   // knowledge on the DATA, not the algorithm
            dataSets.add(ds);
        }

        Parameters parameters = new Parameters();
        parameters.set(Params.TIME_LAG, 2);
        parameters.set(Params.TIME_LAG_REPLICATING_GRAPH, true);
        parameters.set(Params.PENALTY_DISCOUNT, 2.0);
        parameters.set(Params.SEED, 42L);
        parameters.set(Params.NUMBER_RESAMPLING, 0);

        Images images = new Images();
        images.setScoreWrapper(new SemBicScore());
        Graph graph = images.search(dataSets, parameters);

        List<String> intoX = edgesIntoBase(graph, "X");
        assertTrue("Tier knowledge on the data sets must forbid edges into X at every lag, "
                + "but found: " + intoX, intoX.isEmpty());

        // Sanity: the search did find the Y-side structure, so the test is not passing
        // vacuously on an empty graph.
        assertTrue("Expected a nonempty result", !graph.getEdges().isEmpty());
    }

    /**
     * Knowledge set on the algorithm must still take precedence and behave as before.
     */
    @Test
    public void testAlgorithmKnowledgeStillHonored() throws InterruptedException {
        List<DataModel> dataSets = new ArrayList<>();
        for (long seed : new long[]{11L, 22L}) {
            dataSets.add(data(seed, 300));   // no knowledge on the data
        }

        Parameters parameters = new Parameters();
        parameters.set(Params.TIME_LAG, 2);
        parameters.set(Params.TIME_LAG_REPLICATING_GRAPH, true);
        parameters.set(Params.PENALTY_DISCOUNT, 2.0);
        parameters.set(Params.SEED, 42L);
        parameters.set(Params.NUMBER_RESAMPLING, 0);

        Images images = new Images();
        images.setScoreWrapper(new SemBicScore());
        images.setKnowledge(tiers());
        Graph graph = images.search(dataSets, parameters);

        List<String> intoX = edgesIntoBase(graph, "X");
        assertTrue("Tier knowledge on the algorithm must forbid edges into X at every lag, "
                + "but found: " + intoX, intoX.isEmpty());
    }

    /**
     * Data sets carrying LAGGED knowledge (names with ":") must not be used to seed the
     * lag expansion - the run should complete without the lag-suffix validation throwing.
     */
    @Test
    public void testLaggedDatasetKnowledgeIgnoredGracefully() throws InterruptedException {
        List<DataModel> dataSets = new ArrayList<>();
        for (long seed : new long[]{11L, 22L}) {
            DataSet ds = data(seed, 300);
            Knowledge lagged = new Knowledge();
            lagged.addToTier(0, "X:1");
            lagged.addToTier(1, "X");
            ds.setKnowledge(lagged);
            dataSets.add(ds);
        }

        Parameters parameters = new Parameters();
        parameters.set(Params.TIME_LAG, 2);
        parameters.set(Params.PENALTY_DISCOUNT, 2.0);
        parameters.set(Params.SEED, 42L);
        parameters.set(Params.NUMBER_RESAMPLING, 0);

        Images images = new Images();
        images.setScoreWrapper(new SemBicScore());
        Graph graph = images.search(dataSets, parameters);   // must not throw
        assertTrue(graph.getNumNodes() > 0);
    }
}
