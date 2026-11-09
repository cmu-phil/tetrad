package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.multi.ImagesFges;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ImagesFges counterpart of TestImagesTimeLag, pinning the same two fixes applied to ImagesFges.runSearch:
 * (1) this.knowledge must not be overwritten with lagged knowledge inside the per-data-set loop (the second
 * data set's createLagData call threw IllegalArgumentException), and (2) with the BOSS meta-algorithm
 * (IMAGES_META_ALG = 2), Params.TIME_LAG_REPLICATING_GRAPH must reach PermutationSearch.setReplicatingGraph
 * so that the output is closed under lag translation. Also pins that the wrapper's knowledge is still base
 * knowledge after a two-data-set lagged run.
 */
public class TestImagesFgesTimeLag {

    /**
     * Constructs a small stationary two-variable VAR(1) data set deterministically:
     * x_t = 0.7 x_{t-1} + e1, y_t = 0.5 y_{t-1} + 0.4 x_t + e2.
     */
    private static DataSet var1Data(long seed, int n) {
        Random rng = new Random(seed);
        double[][] d = new double[n][2];
        double x = 0.0, y = 0.0;
        for (int t = 0; t < n; t++) {
            x = 0.7 * x + rng.nextGaussian();
            y = 0.5 * y + 0.4 * x + rng.nextGaussian();
            d[t][0] = x;
            d[t][1] = y;
        }
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static Graph search(List<DataModel> dataSets, int timeLag, boolean replicating) throws InterruptedException {
        Parameters parameters = new Parameters();
        parameters.set(Params.TIME_LAG, timeLag);
        parameters.set(Params.TIME_LAG_REPLICATING_GRAPH, replicating);
        parameters.set(Params.PENALTY_DISCOUNT, 2.0);
        parameters.set(Params.SEED, 42L);
        parameters.set(Params.NUMBER_RESAMPLING, 0);
        parameters.set(Params.IMAGES_META_ALG, 2);
        ImagesFges images = new ImagesFges();
        images.setScoreWrapper(new SemBicScore());
        return images.search(dataSets, parameters);
    }

    private static String base(Node n) {
        String name = n.getName();
        int i = name.indexOf(':');
        return i < 0 ? name : name.substring(0, i);
    }

    private static int lag(Node n) {
        String name = n.getName();
        int i = name.indexOf(':');
        return i < 0 ? 0 : Integer.parseInt(name.substring(i + 1));
    }

    private static String name(String baseName, int lagIndex) {
        return lagIndex == 0 ? baseName : baseName + ":" + lagIndex;
    }

    /**
     * Returns null if the graph is closed under lag translation (adjacency and endpoints),
     * otherwise a description of a violating edge.
     */
    private static String translationViolation(Graph graph, int maxLag) {
        Set<String> names = new HashSet<>();
        for (Node node : graph.getNodes()) names.add(node.getName());

        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();
            for (int shift = -maxLag; shift <= maxLag; shift++) {
                if (shift == 0) continue;
                String t1 = name(base(n1), lag(n1) + shift);
                String t2 = name(base(n2), lag(n2) + shift);
                if (!names.contains(t1) || !names.contains(t2)) continue;

                Node m1 = graph.getNode(t1);
                Node m2 = graph.getNode(t2);
                Edge translate = graph.getEdge(m1, m2);
                if (translate == null) {
                    return edge + " has no translate " + t1 + " ~ " + t2;
                }
                Endpoint p1 = translate.getProximalEndpoint(m1);
                Endpoint p2 = translate.getProximalEndpoint(m2);
                if (p1 != edge.getProximalEndpoint(n1) || p2 != edge.getProximalEndpoint(n2)) {
                    return edge + " and its translate " + translate + " disagree on endpoints";
                }
            }
        }
        return null;
    }

    /**
     * Bug 1: multiple data sets plus a time lag must not throw. Before the fix, the second
     * data set's createLagData call received lagged knowledge and threw
     * IllegalArgumentException.
     */
    @Test
    public void testMultipleDataSetsWithTimeLagDoesNotThrow() throws InterruptedException {
        List<DataModel> dataSets = new ArrayList<>();
        dataSets.add(var1Data(11L, 300));
        dataSets.add(var1Data(22L, 300));
        Graph graph = search(dataSets, 2, false);
        assertTrue("Expected a non-empty lagged search result", graph.getNumNodes() == 6);
    }

    /**
     * The wrapper's knowledge must still be base (unlagged) knowledge after a lagged multi-data-set run, and
     * the FGES meta-algorithm path must also survive two lagged data sets.
     */
    @Test
    public void testKnowledgeNotOverwrittenAndFgesMetaRuns() throws InterruptedException {
        List<DataModel> dataSets = new ArrayList<>();
        dataSets.add(var1Data(3L, 200));
        dataSets.add(var1Data(4L, 200));
        Parameters parameters = new Parameters();
        parameters.set(Params.TIME_LAG, 1);
        parameters.set(Params.PENALTY_DISCOUNT, 2.0);
        parameters.set(Params.NUMBER_RESAMPLING, 0);
        parameters.set(Params.IMAGES_META_ALG, 1);
        ImagesFges images = new ImagesFges();
        images.setScoreWrapper(new SemBicScore());
        Graph graph = images.search(dataSets, parameters);
        assertTrue(graph.getNumNodes() == 4);
        for (String name : images.getKnowledge().getVariables()) {
            assertFalse("Wrapper knowledge was overwritten with lagged knowledge: " + name, name.contains(":"));
        }
    }

    /**
     * Bug 2: with the replicating-graph option set, the output must be closed under lag
     * translation, for adjacencies and endpoints alike.
     */
    @Test
    public void testReplicatingGraphIsTranslationClosed() throws InterruptedException {
        List<DataModel> dataSets = new ArrayList<>();
        dataSets.add(var1Data(11L, 300));
        dataSets.add(var1Data(22L, 300));
        Graph graph = search(dataSets, 2, true);
        String violation = translationViolation(graph, 2);
        assertTrue("Replicating IMaGES output is not translation-closed: " + violation,
                violation == null);
        // The dynamics should be detected at all: at least one cross-lag edge into the
        // current slice.
        boolean anyCrossLag = false;
        for (Edge edge : graph.getEdges()) {
            if (lag(edge.getNode1()) != lag(edge.getNode2())) anyCrossLag = true;
        }
        assertTrue("Expected at least one cross-lag edge", anyCrossLag);
        assertFalse("Expected a nonempty graph", graph.getEdges().isEmpty());
    }
}
