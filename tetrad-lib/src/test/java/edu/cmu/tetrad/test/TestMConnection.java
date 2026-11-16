package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link Paths#isMConnectedTo}. They check that (1) m-connection read off a CPDAG or PAG agrees
 * with d-connection in the DAG it came from, for every entry point and either value of the legacy flag, and (2) the
 * linear-time search agrees with the (edge, node) search it replaced. Small graphs, fixed seeds; runs in about a
 * second.
 */
public class TestMConnection {

    /**
     * X2 -> X5 --- X6 <- X4 must not be read as open at X6. The entry point used by MsepTest used to get this wrong.
     */
    @Test
    public void testUndirectedEdgeAfterArrowheadInCpdag() {
        Graph cpdag = GraphUtils.convert("X1---X2,X1---X3,X1---X4,X3---X4,X5---X6,"
                                         + "X2-->X5,X2-->X6,X3-->X5,X3-->X6,X4-->X5,X4-->X6");
        Node x2 = cpdag.getNode("X2");
        Node x4 = cpdag.getNode("X4");
        Set<Node> z = Set.of(cpdag.getNode("X1"));

        assertFalse(cpdag.paths().isMConnectedTo(x2, x4, z, false));
        assertFalse(cpdag.paths().isMConnectedTo(x2, x4, z, true));
        assertFalse(cpdag.paths().isMConnectedTo(x2, x4, z, cpdag.paths().getDescendantsMap(), false));
        assertTrue(cpdag.paths().isMSeparatedFrom(x2, x4, z, false));
        assertTrue(new MsepTest(cpdag).checkIndependence(x2, x4, z).isIndependent());

        // Sanity: not separated without conditioning on X1.
        assertTrue(cpdag.paths().isMConnectedTo(x2, x4, Set.of(), false));
    }

    @Test
    public void testCpdagAgreesWithDag() {
        RandomUtil.getInstance().setSeed(4821L);
        Random r = new Random(4821L);

        for (int trial = 0; trial < 150; trial++) {
            Graph dag = randomDag(r, 0);
            checkAgainstDag(dag, new EdgeListGraph(GraphTransforms.dagToCpdag(dag)), r, "CPDAG");
        }
    }

    @Test
    public void testPagAgreesWithDag() {
        RandomUtil.getInstance().setSeed(9173L);
        Random r = new Random(9173L);

        for (int trial = 0; trial < 60; trial++) {
            Graph dag = randomDag(r, 2);
            checkAgainstDag(dag, new EdgeListGraph(GraphTransforms.dagToPag(dag, false)), r, "PAG");
        }
    }

    /**
     * An underline triple on a separate component sends a query down the old (edge, node) search without changing
     * its answer, so the two searches can be compared on the same graph, including graphs with circle endpoints,
     * undirected edges, parallel edges and directed cycles.
     */
    @Test
    public void testFastSearchAgreesWithEdgeNodeSearch() {
        Random r = new Random(2206L);
        Endpoint[] marks = {Endpoint.TAIL, Endpoint.ARROW, Endpoint.CIRCLE};

        for (int trial = 0; trial < 300; trial++) {
            int n = 4 + r.nextInt(8);
            double density = 0.15 + 0.4 * r.nextDouble();
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < n; i++) nodes.add(new GraphNode("X" + i));
            Graph g = new EdgeListGraph(nodes);

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (r.nextDouble() > density) continue;
                    Endpoint e1 = marks[r.nextInt(3)];
                    Endpoint e2 = marks[r.nextInt(3)];
                    g.addEdge(new Edge(nodes.get(i), nodes.get(j), e1, e2));

                    if (r.nextDouble() < 0.05 && !(e1 == Endpoint.ARROW && e2 == Endpoint.ARROW)) {
                        g.addEdge(new Edge(nodes.get(i), nodes.get(j), Endpoint.ARROW, Endpoint.ARROW));
                    }
                }
            }

            Graph old = new EdgeListGraph(g);
            Node u1 = new GraphNode("U1");
            Node u2 = new GraphNode("U2");
            Node u3 = new GraphNode("U3");
            old.addNode(u1);
            old.addNode(u2);
            old.addNode(u3);
            old.addDirectedEdge(u1, u2);
            old.addDirectedEdge(u2, u3);
            old.addUnderlineTriple(u1, u2, u3);
            assertFalse(old.getUnderLines().isEmpty());

            Map<Node, Set<Node>> descG = g.paths().getDescendantsMap();
            Map<Node, Set<Node>> descOld = old.paths().getDescendantsMap();

            for (int q = 0; q < 40; q++) {
                Node x = nodes.get(r.nextInt(n));
                Node y = nodes.get(r.nextInt(n));
                Set<Node> z = randomZ(r, nodes, x, y);
                String msg = x + " , " + y + " | " + z + " in\n" + g;

                assertEquals(msg, old.paths().isMConnectedTo(x, y, z, false), g.paths().isMConnectedTo(x, y, z, false));
                assertEquals(msg, old.paths().isMConnectedTo(x, y, z, descOld, false),
                        g.paths().isMConnectedTo(x, y, z, descG, false));
            }
        }
    }

    private static Graph randomDag(Random r, int numLatents) {
        int n = 5 + r.nextInt(6);
        int numEdges = Math.min(n * (n - 1) / 2, n + r.nextInt(n));
        return RandomGraph.randomDag(n, numLatents, numEdges, 100, 100, 100, false);
    }

    private static Set<Node> randomZ(Random r, List<Node> nodes, Node x, Node y) {
        Set<Node> z = new HashSet<>();
        int k = r.nextInt(Math.min(5, nodes.size() - 1));

        while (z.size() < k) {
            Node c = nodes.get(r.nextInt(nodes.size()));
            if (c != x && c != y) z.add(c);
        }

        return z;
    }

    // Every query over the nodes of g (the measured nodes, for a PAG) must agree with d-connection in the DAG.
    private static void checkAgainstDag(Graph dag, Graph g, Random r, String kind) {
        List<Node> nodes = g.getNodes();
        Map<Node, Set<Node>> desc = g.paths().getDescendantsMap();

        for (int q = 0; q < 40; q++) {
            Node x = nodes.get(r.nextInt(nodes.size()));
            Node y = nodes.get(r.nextInt(nodes.size()));
            if (x == y) continue;
            Set<Node> z = randomZ(r, nodes, x, y);

            Set<Node> dagZ = new HashSet<>();
            for (Node c : z) dagZ.add(dag.getNode(c.getName()));
            boolean truth = dag.paths().isMConnectedTo(dag.getNode(x.getName()), dag.getNode(y.getName()), dagZ, false);

            String msg = kind + ": " + x + " , " + y + " | " + z + " in\n" + g;
            assertEquals(msg, truth, g.paths().isMConnectedTo(x, y, z, false));
            assertEquals(msg, truth, g.paths().isMConnectedTo(x, y, z, true));
            assertEquals(msg, truth, g.paths().isMConnectedTo(x, y, z, desc, false));
        }
    }
}
