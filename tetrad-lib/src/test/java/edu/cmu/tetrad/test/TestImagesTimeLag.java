package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
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
 * Pins two fixes to Images (IMaGES) when Params.TIME_LAG &gt; 0:
 *
 * <p>(1) With more than one data set, runSearch previously overwrote this.knowledge with the
 * lagged knowledge inside the per-data-set loop, so the second call to
 * TsUtils.createLagData(dataSet, lag, knowledge) received knowledge containing lag-suffixed
 * variable names and threw IllegalArgumentException. The field must remain the user's base
 * (unlagged) knowledge across data sets (and across bootstrap re-entries).
 *
 * <p>(2) Params.TIME_LAG_REPLICATING_GRAPH (the SVAR "repeating structure" option, displayed for
 * IMaGES because its parameter list inherits from the Boss wrapper) was never wired through to
 * PermutationSearch.setReplicatingGraph, so setting it had no effect and edges did not repeat
 * from one lag to the next. When the flag is set, the output must be closed under lag
 * translation, at the level of both adjacency and endpoints.
 */
public class TestImagesTimeLag {

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
        Images images = new Images();
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
