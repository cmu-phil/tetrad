package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Cpw;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * CPW with the two-cycle step (two-cycle alpha positive, the new default) on cyclic Exp(1)
 * data: a true 2-cycle is output as both directed edges, and an acyclic chain yields no
 * 2-cycles. Seeds are pinned; the data path is the reduced-form SemIm simulation.
 */
public class TestCpwTwoCycles {

    @Test
    public void trueTwoCycleIsOutputAsBothDirections() throws Exception {
        RandomUtil.getInstance().setSeed(4411L);

        Graph g = graph(5);
        SemIm im = im(g, new String[][]{{"X1", "X2"}, {"X2", "X1"}, {"X3", "X4"}, {"X4", "X5"}},
                new double[]{0.6, 0.4, 0.8, 0.8});
        DataSet data = im.simulateData(2000, false);

        Graph est = run(data);

        assertTrue("True 2-cycle pair should come out with both directions",
                directed(est, "X1", "X2") && directed(est, "X2", "X1"));
        assertFalse("The acyclic X3 -> X4 edge should not be a 2-cycle",
                directed(est, "X3", "X4") && directed(est, "X4", "X3"));
    }

    @Test
    public void acyclicChainYieldsNoTwoCycles() throws Exception {
        RandomUtil.getInstance().setSeed(4412L);

        Graph g = graph(4);
        SemIm im = im(g, new String[][]{{"X1", "X2"}, {"X2", "X3"}, {"X3", "X4"}},
                new double[]{0.8, 0.8, 0.8});
        DataSet data = im.simulateData(2000, false);

        Graph est = run(data);

        for (Node a : est.getNodes()) {
            for (Node b : est.getNodes()) {
                if (a.getName().compareTo(b.getName()) < 0) {
                    assertFalse("No 2-cycles expected on the acyclic chain: " + a + ", " + b,
                            directed(est, a.getName(), b.getName()) && directed(est, b.getName(), a.getName()));
                }
            }
        }
    }

    private static Graph run(DataSet data) throws InterruptedException {
        Parameters p = new Parameters();
        p.set(Params.ALPHA, 0.05);
        p.set(Params.DEPTH, -1);
        p.set(Params.STABLE_FAS, true);
        p.set(Params.COLLIDER_ORIENTATION_STYLE, 1);
        p.set(Params.MAX_DISCRIMINATING_PATH_LENGTH, -1);
        p.set(Params.DO_POSSIBLE_DSEP, true);
        p.set(Params.COMPLETE_RULE_SET_USED, true);
        p.set(Params.FASK_LEFT_RIGHT_RULE, 2);
        p.set(Params.TWO_CYCLE_ALPHA, 0.01);
        p.set(Params.FDR_Q, 0.0);
        p.set(Params.TIME_LAG, 0);
        p.set(Params.VERBOSE, false);
        p.set(Params.NUMBER_RESAMPLING, 0);
        return new Cpw(new FisherZ()).search(data, p);
    }

    private static Graph graph(int p) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= p; i++) nodes.add(new GraphNode("X" + i));
        return new EdgeListGraph(nodes);
    }

    private static SemIm im(Graph g, String[][] edges, double[] coefs) {
        for (String[] e : edges) g.addDirectedEdge(g.getNode(e[0]), g.getNode(e[1]));
        Parameters params = new Parameters();
        params.set(Params.CUSTOM_NOISE_OPTION, 2);
        params.set(Params.CUSTOM_NOISE_EXPRESSION, "Exp(1)");
        SemIm im = new SemIm(new SemPm(g), params);
        for (int k = 0; k < edges.length; k++) {
            Node a = im.getSemPm().getGraph().getNode(edges[k][0]);
            Node b = im.getSemPm().getGraph().getNode(edges[k][1]);
            im.setEdgeCoef(a, b, coefs[k]);
        }
        return im;
    }

    private static boolean directed(Graph g, String from, String to) {
        Node a = g.getNode(from), b = g.getNode(to);
        for (Edge e : g.getEdges(a, b)) {
            if (e.getProximalEndpoint(b) == Endpoint.ARROW && e.getProximalEndpoint(a) == Endpoint.TAIL) return true;
        }
        return false;
    }
}
