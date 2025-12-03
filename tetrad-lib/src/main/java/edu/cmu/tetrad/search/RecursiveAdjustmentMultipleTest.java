package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Sanity checks for AdjustmentMultiple:
 */
public class RecursiveAdjustmentMultipleTest {

    public RecursiveAdjustmentMultipleTest() {

    }

    // --- Helpers ----------------------------------------------------------

    private Node n(String name) {
        return new GraphNode(name);
    }

    private Set<Node> set(Node... nodes) {
        return new LinkedHashSet<>(Arrays.asList(nodes));
    }

    private String keyOf(Set<Node> z) {
        List<String> names = new ArrayList<>();
        for (Node v : z) names.add(v.getName());
        Collections.sort(names);
        return names.toString();
    }

    // Compare families of sets ignoring:
    //  - order of sets
    //  - order of elements inside each set
    private void assertSameSolutionFamilies(List<Set<Node>> expected,
                                            List<Set<Node>> actual) {
        Map<String, Set<Node>> eMap = new LinkedHashMap<>();
        for (Set<Node> z : expected) {
            eMap.put(keyOf(z), new LinkedHashSet<>(z));
        }

        Map<String, Set<Node>> aMap = new LinkedHashMap<>();
        for (Set<Node> z : actual) {
            aMap.put(keyOf(z), new LinkedHashSet<>(z));
        }

        assertEquals("Solution families differ", eMap, aMap);
    }

    // --- Tests ------------------------------------------------------------

    /**
     * Tests the adjustment set computation for a simple Directed Acyclic Graph (DAG)
     * with a single treatment-outcome pair.
     */
    @Test
    public void testSinglePairSimpleDAG() {
        // Graph:
        // W -> X -> Z -> Y
        // W -> Y
        Node X = n("X");
        Node Y = n("Y");
        Node Z = n("Z");
        Node W = n("W");

        Graph g = new Dag();
        g.addNode(X);
        g.addNode(Y);
        g.addNode(Z);
        g.addNode(W);

        g.addEdge(Edges.directedEdge(W, X));
        g.addEdge(Edges.directedEdge(X, Z));
        g.addEdge(Edges.directedEdge(Z, Y));
        g.addEdge(Edges.directedEdge(W, Y));

        String graphType = "DAG";
        int maxNumSets = 100;
        int maxRadius = 10;
        int nearWhichEndpoint = -1;   // "near either"
        int maxPathLength = 10;
        boolean avoidAmenable = false;  // RB mode keeps things simple here
        Set<Node> notFollowed = null;
        Set<Node> containing = null;

        RecursiveAdjustmentMultiple multi = new RecursiveAdjustmentMultiple(g);

        List<Set<Node>> sets =
                multi.adjustmentSets(
                        set(X),
                        set(Y),
                        graphType,
                        maxNumSets,
                        maxRadius,
                        nearWhichEndpoint,
                        maxPathLength,
                        avoidAmenable,
                        notFollowed,
                        containing
                );

        // For this graph, {W} is the natural minimal backdoor adjustment set.
        List<Set<Node>> expected = new ArrayList<>();
        expected.add(set(W));

        assertSameSolutionFamilies(expected, sets);
    }

    /**
     * Tests the adjustment set computation for a Directed Acyclic Graph (DAG) with multiple treatments
     * sharing a common backdoor path to the outcome.
     */
    @Test
    public void testMultiTreatmentSharedBackdoor() {
        // Graph:
        // W -> X1 -> Y
        // W -> X2 -> Y
        // W -> Y
        Node X1 = n("X1");
        Node X2 = n("X2");
        Node Y = n("Y");
        Node W = n("W");

        Graph g = new Dag();
        g.addNode(X1);
        g.addNode(X2);
        g.addNode(Y);
        g.addNode(W);

        g.addEdge(Edges.directedEdge(W, X1));
        g.addEdge(Edges.directedEdge(W, X2));
        g.addEdge(Edges.directedEdge(W, Y));
        g.addEdge(Edges.directedEdge(X1, Y));
        g.addEdge(Edges.directedEdge(X2, Y));

        String graphType = "DAG";
        int maxNumSets = 100;
        int maxRadius = 10;
        int nearWhichEndpoint = -1;
        int maxPathLength = 10;
        boolean avoidAmenable = false;   // RB mode
        Set<Node> notFollowed = null;
        Set<Node> containing = null;

        RecursiveAdjustmentMultiple multi = new RecursiveAdjustmentMultiple(g);

        List<Set<Node>> sets =
                multi.adjustmentSets(
                        set(X1, X2),
                        set(Y),
                        graphType,
                        maxNumSets,
                        maxRadius,
                        nearWhichEndpoint,
                        maxPathLength,
                        avoidAmenable,
                        notFollowed,
                        containing
                );

        // For this graph, {W} should again be a valid minimal adjustment set.
        boolean foundW = false;
        for (Set<Node> Z : sets) {
            if (Z.size() == 1 && Z.contains(W)) {
                foundW = true;
                break;
            }
        }

        assertTrue("Expected {W} as a multi-treatment adjustment set", foundW);
    }

    /**
     * Tests whether specifying different possible endpoint options in the adjustment set computation
     * produces solutions from the same only slightly reordered but identical solution families.
     */
    @Test
    public void testNearWhichEndpointDoesNotChangeSolutions() {
        // Slightly more complex DAG where ordering might matter:
        //
        // X -> M1 -> Y
        // X -> M2 -> Y
        // W -> X, W -> Y  (backdoor)
        //
        Node X = n("X");
        Node Y = n("Y");
        Node M1 = n("M1");
        Node M2 = n("M2");
        Node W = n("W");

        Graph g = new Dag();
        g.addNode(X);
        g.addNode(Y);
        g.addNode(M1);
        g.addNode(M2);
        g.addNode(W);

        g.addEdge(Edges.directedEdge(W, X));
        g.addEdge(Edges.directedEdge(W, Y));
        g.addEdge(Edges.directedEdge(X, M1));
        g.addEdge(Edges.directedEdge(M1, Y));
        g.addEdge(Edges.directedEdge(X, M2));
        g.addEdge(Edges.directedEdge(M2, Y));

        RecursiveAdjustmentMultiple multi = new RecursiveAdjustmentMultiple(g);

        String graphType = "DAG";
        int maxNumSets = 100;
        int maxRadius = 10;
        int maxPathLength = 10;
        boolean avoidAmenable = false;  // RB mode again
        Set<Node> notFollowed = null;
        Set<Node> containing = null;

        List<Set<Node>> setsNearX =
                multi.adjustmentSets(
                        set(X),
                        set(Y),
                        graphType,
                        maxNumSets,
                        maxRadius,
                        /*nearWhichEndpoint=*/0,   // near X
                        maxPathLength,
                        avoidAmenable,
                        notFollowed,
                        containing
                );

        List<Set<Node>> setsNearY =
                multi.adjustmentSets(
                        set(X),
                        set(Y),
                        graphType,
                        maxNumSets,
                        maxRadius,
                        /*nearWhichEndpoint=*/1,   // near Y
                        maxPathLength,
                        avoidAmenable,
                        notFollowed,
                        containing
                );

        // Same family of sets, possibly different order.
        assertSameSolutionFamilies(setsNearX, setsNearY);
    }
}