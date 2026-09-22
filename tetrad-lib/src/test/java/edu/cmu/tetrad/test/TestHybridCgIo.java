package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgIo;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Round-trip tests for {@link HybridCgIo}: save/load must preserve every parameter, the JSON must be a fixed point
 * under save/load/save, node display positions must survive, and malformed files must be rejected with informative
 * messages.
 */
public class TestHybridCgIo {

    /**
     * Save-then-load reproduces every probability, mean, coefficient, variance, category list, cutpoint list, and
     * node position; saving the reloaded model reproduces the identical JSON text.
     */
    @Test
    public void testRoundTrip() {
        HybridCgIm im = buildModel();

        String json = HybridCgIo.toJson(im);
        HybridCgIm back = HybridCgIo.fromJson(json);
        compare(im, back);

        assertEquals("save/load/save should be a fixed point", json, HybridCgIo.toJson(back));

        Node[] a = im.getPm().getNodes();
        for (Node node : a) {
            Node bn = find(back.getPm().getNodes(), node.getName());
            assertEquals(node.getCenterX(), bn.getCenterX());
            assertEquals(node.getCenterY(), bn.getCenterY());
        }
    }

    /**
     * Malformed files are rejected: a wrong format tag, an unknown parent name, and a parent list implying a cycle.
     */
    @Test
    public void testRejection() {
        expectFail("{\"format\":\"nope\"}");

        String cyclic = "{\"format\":\"hybridcg-im\",\"version\":1,\"nodes\":["
                        + "{\"name\":\"A\",\"discrete\":false,\"discreteParents\":[],\"continuousParents\":[\"B\"],"
                        + "\"params\":[[0,1,1]]},"
                        + "{\"name\":\"B\",\"discrete\":false,\"discreteParents\":[],\"continuousParents\":[\"A\"],"
                        + "\"params\":[[0,1,1]]}]}";
        expectFail(cyclic);

        String unknownParent = "{\"format\":\"hybridcg-im\",\"version\":1,\"nodes\":["
                               + "{\"name\":\"A\",\"discrete\":false,\"discreteParents\":[],\"continuousParents\":[\"Q\"],"
                               + "\"params\":[[0,1,1]]}]}";
        expectFail(unknownParent);
    }

    private static void expectFail(String json) {
        try {
            HybridCgIo.fromJson(json);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage() != null && !e.getMessage().isBlank());
        }
    }

    private static HybridCgIm buildModel() {
        Node d1 = new DiscreteVariable("D1", Arrays.asList("a", "b", "c"));
        Node d3 = new DiscreteVariable("D3", Arrays.asList("u", "v"));
        Node c1 = new ContinuousVariable("C1");
        Node d2 = new DiscreteVariable("D2", Arrays.asList("lo", "hi"));
        Node c2 = new ContinuousVariable("C2");

        Graph g = new EdgeListGraph();
        for (Node n : Arrays.asList(d1, d3, c1, d2, c2)) g.addNode(n);
        g.addDirectedEdge(d1, d2);
        g.addDirectedEdge(d3, d2);
        g.addDirectedEdge(c1, d2);   // discrete child with a continuous parent, so cutpoints are exercised
        g.addDirectedEdge(d1, c2);
        g.addDirectedEdge(c1, c2);

        d1.setCenter(50, 60);
        d3.setCenter(90, 40);
        c1.setCenter(150, 60);
        d2.setCenter(100, 160);
        c2.setCenter(200, 160);

        Map<Node, Boolean> flags = new HashMap<>();
        flags.put(d1, true);
        flags.put(d3, true);
        flags.put(c1, false);
        flags.put(d2, true);
        flags.put(c2, false);

        Map<Node, List<String>> cats = new HashMap<>();
        cats.put(d1, Arrays.asList("a", "b", "c"));
        cats.put(d3, Arrays.asList("u", "v"));
        cats.put(d2, Arrays.asList("lo", "hi"));

        HybridCgPm pm = new HybridCgPm(g, Arrays.asList(d1, d3, c1, d2, c2), flags, cats);
        pm.setContParentCutpointsForDiscreteChild(d2, Map.of(c1, new double[]{-0.4, 0.7}));

        HybridCgIm im = new HybridCgIm(pm);
        Random r = new Random(31);

        for (int y = 0; y < pm.getNodes().length; y++) {
            int rows = pm.getNumRows(y);
            if (pm.isDiscrete(y)) {
                int card = pm.getCardinality(y);
                for (int q = 0; q < rows; q++) {
                    double s = 0;
                    double[] t = new double[card];
                    for (int k = 0; k < card; k++) {
                        t[k] = 0.05 + r.nextDouble();
                        s += t[k];
                    }
                    for (int k = 0; k < card; k++) im.setProbability(y, q, k, t[k] / s);
                }
            } else {
                int m = pm.getContinuousParents(y).length;
                for (int q = 0; q < rows; q++) {
                    im.setMean(y, q, r.nextGaussian());
                    for (int j = 0; j < m; j++) im.setCoefficient(y, q, j, r.nextGaussian());
                    im.setVariance(y, q, 0.1 + r.nextDouble());
                }
            }
        }

        return im;
    }

    /**
     * Compares two models stratum by stratum via parent-value assignments, so a difference in internal parent order
     * between the two PMs cannot cause a spurious pass or failure.
     */
    private static void compare(HybridCgIm a, HybridCgIm b) {
        HybridCgPm pa = a.getPm();
        HybridCgPm pb = b.getPm();
        Node[] na = pa.getNodes();

        for (int ya = 0; ya < na.length; ya++) {
            int yb = pb.indexOf(find(pb.getNodes(), na[ya].getName()));
            assertEquals(pa.isDiscrete(ya), pb.isDiscrete(yb));
            if (pa.isDiscrete(ya)) assertEquals(pa.getCategories(ya), pb.getCategories(yb));

            int rows = pa.getNumRows(ya);
            assertEquals(rows, pb.getNumRows(yb));

            int[] dimsA = pa.getRowDims(ya);
            List<String> seqA = seq(pa, ya);
            int[] dimsB = pb.getRowDims(yb);
            List<String> seqB = seq(pb, yb);

            for (int ra = 0; ra < rows; ra++) {
                Map<String, Integer> assign = new HashMap<>();
                int rem = ra;
                for (int k = dimsA.length - 1; k >= 0; k--) {
                    assign.put(seqA.get(k), rem % dimsA[k]);
                    rem /= dimsA[k];
                }
                int rb = 0;
                for (int k = 0; k < dimsB.length; k++) rb = rb * dimsB[k] + assign.get(seqB.get(k));

                if (pa.isDiscrete(ya)) {
                    for (int c = 0; c < pa.getCardinality(ya); c++) {
                        assertEquals(a.getProbability(ya, ra, c), b.getProbability(yb, rb, c), 1e-12);
                    }
                } else {
                    assertEquals(a.getMean(ya, ra), b.getMean(yb, rb), 1e-12);
                    assertEquals(a.getVariance(ya, ra), b.getVariance(yb, rb), 1e-12);
                    int[] cpA = pa.getContinuousParents(ya);
                    int[] cpB = pb.getContinuousParents(yb);
                    for (int j = 0; j < cpA.length; j++) {
                        String pname = pa.getNodes()[cpA[j]].getName();
                        int jb = -1;
                        for (int t = 0; t < cpB.length; t++) {
                            if (pb.getNodes()[cpB[t]].getName().equals(pname)) jb = t;
                        }
                        assertTrue(jb >= 0);
                        assertEquals(a.getCoefficient(ya, ra, j), b.getCoefficient(yb, rb, jb), 1e-12);
                    }
                }
            }
        }
    }

    private static List<String> seq(HybridCgPm pm, int y) {
        List<String> s = new ArrayList<>();
        for (int p : pm.getDiscreteParents(y)) s.add(pm.getNodes()[p].getName());
        if (pm.isDiscrete(y)) for (int p : pm.getContinuousParents(y)) s.add(pm.getNodes()[p].getName());
        return s;
    }

    private static Node find(Node[] nodes, String name) {
        for (Node n : nodes) if (n.getName().equals(name)) return n;
        fail("missing node " + name);
        return null; // unreachable
    }
}
