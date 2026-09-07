package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheckNullReference;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.text.ParseException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the parametric-bootstrap null reference for the Markov check. Uses a linear-Gaussian
 * chain X1 -&gt; X2 -&gt; ... -&gt; X6 so that (a) checking the true graph should be null-typical
 * and (b) checking the empty graph (which implies all pairs marginally independent) should be
 * decisively rejected relative to a null fitted to the empty graph, whose draws have independent
 * columns by construction.
 */
public class TestMarkovCheckNullReference {

    private static DataSet chainData() throws ParseException {
        RandomUtil.getInstance().setSeed(42);
        Graph chain = new EdgeListGraph();
        Node prev = null;
        for (int i = 1; i <= 6; i++) {
            Node node = new edu.cmu.tetrad.data.ContinuousVariable("X" + i);
            chain.addNode(node);
            if (prev != null) chain.addDirectedEdge(prev, node);
            prev = node;
        }
        SemPm pm = new SemPm(chain);
        SemIm im = new SemIm(pm);
        return im.simulateData(500, false);
    }

    private static Graph trueGraph(DataSet data) {
        List<Node> vars = data.getVariables();
        Graph g = new EdgeListGraph(vars);
        for (int i = 0; i < vars.size() - 1; i++) {
            g.addDirectedEdge(vars.get(i), vars.get(i + 1));
        }
        return g;
    }

    @Test
    public void testTrueGraphIsNullTypical() throws ParseException {
        DataSet data = chainData();
        Graph g = trueGraph(data);

        MarkovCheckNullReference.Result result = MarkovCheckNullReference.compute(
                data, g, d -> new IndTestFisherZ(d, 0.01),
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY,
                MarkovCheckNullReference.SimulatorType.TRAINED_DAG_GNM,
                10, -1, 1000L, null);

        assertEquals(10, result.getNullAdInd().length);
        for (double v : result.getNullAdInd()) {
            assertTrue("null ad_ind out of [0,1]: " + v, v >= 0.0 && v <= 1.0);
        }

        // The real data was generated from the checked graph (linear-Gaussian, which the GNM
        // can represent), so the real statistic should not be below every null draw.
        assertTrue("true graph rejected against its own null: " + result,
                result.getEmpiricalP() > 0.0);
    }

    @Test
    public void testEmptyGraphIsDecisivelyRejected() throws ParseException {
        DataSet data = chainData();
        Graph empty = new EdgeListGraph(data.getVariables());

        MarkovCheckNullReference.Result result = MarkovCheckNullReference.compute(
                data, empty, d -> new IndTestFisherZ(d, 0.01),
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY,
                MarkovCheckNullReference.SimulatorType.TRAINED_DAG_GNM,
                10, -1, 1000L, null);

        // The empty graph implies all pairs marginally independent; the chain data violates
        // this decisively, while draws simulated under the empty graph have independent columns.
        assertTrue("real ad_ind should be tiny: " + result, result.getRealAdInd() < 0.01);
        assertEquals("real should fall below all null draws: " + result,
                0.0, result.getEmpiricalP(), 0.0);
    }
}
