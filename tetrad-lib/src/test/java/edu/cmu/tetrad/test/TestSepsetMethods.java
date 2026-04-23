/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TMath;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.search.SepsetFinder.blockPathsLocalMarkov;
import static org.junit.Assert.*;

/**
 * The TestSepsetMethods class  is responsible for testing various methods for finding a sepset of two nodes in a DAG.
 */
public class TestSepsetMethods {

    private static final Logger log = LoggerFactory.getLogger(TestSepsetMethods.class);

    /**
     * This method is used to test various methods for finding a sepset of two nodes in a directed acyclic graph (DAG).
     * It performs several repetitions of the test and calculates the total time taken for each step.
     */
    @Test
    public void test1() {
        int numNodes = 20;
        int numEdges = 40;
        int numLatentsForPag = 5; // Ignored for the DAG or CPDAG cases.
        int numReps = 100;

        enum GraphType {
            DAG, CPDAG, PAG
        }

        GraphType graphType = GraphType.DAG;

        enum Method {
            BLOCK_PATHS_WITH_MARKOV_BLANKET,
            BLOCK_PATHS_LOCAL_MARKOV,
            BLOCK_PATHS_GREEDY,
            BLOCK_PATHS_MAX_P,
            BLOCK_PATHS_MIN_P,
            BLOCK_PATHS_RECURSIVELY,
        }

        List<Method> methods = List.of(
//                Method.BLOCK_PATHS_WITH_MARKOV_BLANKET,
//                Method.BLOCK_PATHS_LOCAL_MARKOV,
                Method.BLOCK_PATHS_RECURSIVELY
//                Method.BLOCK_PATHS_GREEDY,
//                Method.BLOCK_PATHS_MAX_P,
//                Method.BLOCK_PATHS_MIN_P
        );

        // Make a list of numNodes nodes.
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        Graph graph;

        switch (graphType) {
            case DAG -> graph = RandomGraph.randomDag(nodes, 0, numEdges, 100, 100, 100, false);
            case CPDAG -> {
                graph = RandomGraph.randomDag(nodes, 0, numEdges, 100, 100, 100, false);
                graph = GraphTransforms.dagToCpdag(graph);
            }
            case PAG -> {
                graph = RandomGraph.randomDag(nodes, numLatentsForPag, numEdges, 100, 100, 100, false);
                graph = GraphTransforms.dagToPag(graph, false);
            }
            default -> throw new IllegalArgumentException("Unknown graph type: " + graphType);
        }

        nodes = graph.getNodes();
        numNodes = nodes.size();
        numEdges = graph.getNumEdges();

        long[] timeSums = new long[methods.size()];
        int[] numPass = new int[methods.size()];

        for (int i = 0; i < numReps; i++) {
            Node x, y;

            do {
                x = nodes.get((int) (TMath.random() * numNodes));
                y = nodes.get((int) (TMath.random() * numNodes));
            } while (x.equals(y));

            if (graph.isAdjacentTo(x, y)) {
                i--;
                continue;
            }

            Edge e = graph.getEdge(x, y);
            System.out.println("\n\n###Rep " + (i + 1) + " Checking nodes " + x + " and " + y + ". The edge is " + ((e != null) ? e : "absent"));

            MsepTest msepTest = new MsepTest(graph, graphType == GraphType.PAG);

            for (int k = 0; k < methods.size(); k++) {
                Method method = methods.get(k);
                Set<Node> blockingSet;
                long start = System.currentTimeMillis();

                switch (method) {
                    case BLOCK_PATHS_WITH_MARKOV_BLANKET -> {
                        blockingSet = SepsetFinder.blockPathsWithMarkovBlanket(x, graph);
                    }
                    case BLOCK_PATHS_RECURSIVELY -> {
                        try {
                            blockingSet = RecursiveBlocking.blockPathsRecursively(graph, x, y, new HashSet<Node>(), Set.of(), -1);

                            if (blockingSet == null) {

                                // There are known cases where this cannot succeed--Puzzle #2.
                                continue;
                            }
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    case BLOCK_PATHS_LOCAL_MARKOV -> {
                        blockingSet = blockPathsLocalMarkov(graph, x);
                    }
                    case BLOCK_PATHS_GREEDY -> {
                        blockingSet = SepsetFinder.findSepsetSubsetOfAdjxOrAdjy(graph, x, y, new HashSet<>(), msepTest, -1);
                    }
                    case BLOCK_PATHS_MAX_P -> {
                        try {
                            blockingSet = SepsetFinder.getSepsetContainingMaxPHybrid(graph, x, y, new HashSet<>(), msepTest, -1);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    case BLOCK_PATHS_MIN_P -> {
                        try {
                            blockingSet = SepsetFinder.getSepsetContainingMinPHybrid(graph, x, y, msepTest, -1);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown method: " + graphType);
                }

                long stop = System.currentTimeMillis();
                timeSums[k] += stop - start;

                System.out.println("Sepset " + method + ": " + blockingSet);
                if (blockingSet != null) {
                    System.out.println("M-sep = " + msepTest.checkIndependence(x, y, blockingSet).isIndependent());
                    numPass[k] += msepTest.checkIndependence(x, y, blockingSet).isIndependent() ? 1 : 0;
                }
            }
        }

        System.out.println();
        System.out.println("Graph type = " + graphType);
        System.out.println();
        System.out.println(numReps + " repetitions of the test were performed.");
        System.out.println();

        System.out.println("Num nodes = " + numNodes + " Num edges = " + numEdges + " Num latents (for PAGS only) = " + numLatentsForPag);
        System.out.println();

        for (int i = 0; i < methods.size(); i++) {
            System.out.println("Number of times msep(x, y | set) with " + methods.get(i) + " = " + numPass[i]);
        }

        System.out.println();

        for (int i = 0; i < methods.size(); i++) {
            System.out.println("The total time required for " + methods.get(i) + " = " + timeSums[i]);
        }
    }

    /**
     * This method is used to test the blockPathsRecursively method for finding a set of nodes that blocks all blockable
     * paths between two nodes in a graph.
     */
    @Test
    public void test2() {

        Graph graph = GraphUtils.convert("X-->Y,X-->Z,X-->W,Y-->Z,W-->Z");

        System.out.println(graph);

        Set<Node> blocking = null;
        try {
            Node x = graph.getNode("X");
            Node y = graph.getNode("Z");
            blocking = RecursiveBlocking.blockPathsRecursively(graph, x, y, new HashSet<Node>(), Set.of(), -1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println(blocking);

        assertTrue(blocking.containsAll(Set.of(graph.getNode("Y"), graph.getNode("W"))));

        Graph graph2 = GraphUtils.convert("X-->Y,X-->W,Y-->Z,W-->Z");

        assertTrue(new MsepTest(graph2, false).checkIndependence(graph2.getNode("X"), graph2.getNode("Z"), blocking).isIndependent());

    }

    /**
     * This method is used to test the blockPathsRecursively method for finding a set of nodes that blocks all blockable
     * paths between two nodes in a graph, for local Markov.
     * <p>
     * The blocking set returned by blockPathsRecursively should always be a sepset of x and y given parents(x) for
     * non-descendants x.
     */
    @Test
    public void test3() {

        System.out.println("Checking to make sure blockPathsRecursively works for local Markov for a DAG.");

        Graph graph = RandomGraph.randomDag(20, 0, 40, 100,
                100, 100, false);

        for (Node x : graph.getNodes()) {
            for (Node y : graph.getNodes()) {
                if (x.equals(y)) {
                    continue;
                }

                Set<Node> parents = new HashSet<>(graph.getParents(x));

                if (parents.contains(y)) {
                    continue;
                }

                if (graph.paths().isDescendentOf(y, x)) {
                    continue;
                }

                Set<Node> blocking = null;
                try {
                    blocking = RecursiveBlocking.blockPathsRecursively(graph, x, y, parents, Set.of(), -1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                boolean msep = new MsepTest(graph, false).checkIndependence(x, y, blocking).isIndependent();

                if (!msep) {
                    System.out.println(LogUtilsSearch.independenceFact(x, y, blocking));
                }

                assertTrue(msep);
            }
        }
    }

    // This doesn't work if we return z in instead possibly null from the recursive method.
    @Test
    public void test4() {
        System.out.println("Checking to make sure blockPathsRecursively works for dsep(x, y | mb(x)) for a PAG for y not in mb(x).");

        Graph dag = RandomGraph.randomDag(20, 10, 40, 100,
                100, 100, false);

        Graph pag = new MagToPag(GraphTransforms.dagToMag(dag)).convert(true, false);

        for (Node x : pag.getNodes()) {
            for (Node y : pag.getNodes()) {
                if (x.equals(y)) {
                    continue;
                }

                if (pag.paths().markovBlanket(x).contains(y)) {
                    continue;
                }

                try {
                    Set<Node> blocking = RecursiveBlocking.blockPathsRecursively(dag, x, y, Set.of(),
                            Set.of(), -1);
                    boolean msep = new MsepTest(pag, false).checkIndependence(x, y, blocking).isIndependent();

                    if (!msep) {
                        System.out.println(LogUtilsSearch.independenceFact(x, y, blocking));
                    }

                    assertTrue(msep);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Test
    public void test5() {
        System.out.println("Checking to make sure blockPathsRecursively distinguishes adj vs non-adj for dsep(x, y | \n" +
                "path_blocking(x)) for a PAG for y not in mb(x).");

        boolean allOK = true;

        for (int i = 0; i < 1; i++) {
            Graph dag = RandomGraph.randomDag(15, 5, 40, 100,
                    100, 100, false);

            Graph pag = new MagToPag(GraphTransforms.dagToMag(dag)).convert(true, false);


            for (Node x : pag.getNodes()) {
                for (Node y : pag.getNodes()) {
                    if (x.equals(y)) {
                        continue;
                    }

                    try {
                        Set<Node> blocking = RecursiveBlocking.blockPathsRecursively(pag, x, y, Set.of(),
                                Set.of(), -1);

                        if (blocking == null) {
                            continue;
                        }

                        if (new MsepTest(pag, false).checkIndependence(x, y, blocking).isIndependent()) {
                            // If independent, then ~adj(x, y).
                            if (pag.isAdjacentTo(x, y)) {
                                allOK = false;
                            }
                        } else {
                            // Dependent given blocking set — only assert the removeIfInMb property.
                            System.out.print(pag.isAdjacentTo(x, y) ? " Adjacent" : " Not adjacent");
                            System.out.print(pag.paths().markovBlanket(x).contains(y) ? ", In MB" : ", Not in MB");

                            if (removeIfInMb(pag, x, y)) {
                                if (pag.isAdjacentTo(x, y)) {
                                    allOK = false;
                                }
                            } else {
                                System.out.print(", OK to remove... ");
                            }

                            System.out.println();
                        }
                    } catch (InterruptedException e) {
                        System.out.println("Exception");
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        assertTrue(allOK);
    }

    /**
     * This method is used to test the blockPathsRecursively method for finding a set of nodes that blocks all blockable
     * paths between two nodes in a graph.
     */
    @Test
    public void test6() {

        Graph graph = GraphUtils.convert("x-->a,a-->b,c-->a,w-->c,x-->w,y-->w");

        System.out.println(graph);

        Set<Node> blocking = null;
        try {
            Node x = graph.getNode("x");
            Node y = graph.getNode("y");

            blocking = RecursiveBlockingVerbose.blockPathsRecursivelyVerbose(
                    graph, x, y,
                    new HashSet<>(), Set.of(),
                    /*maxPathLength*/ -1,
                    System.out
            );

            MsepTest test = new MsepTest(graph);
            System.out.println(test.checkIndependence(x, y, blocking).isIndependent());

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println(blocking);
    }

    private boolean removeIfInMb(Graph pag, Node x, Node y) {
        List<Node> common = pag.getAdjacentNodes(x);
        common.retainAll(pag.getAdjacentNodes(y));

        SublistGenerator gen2 = new SublistGenerator(common.size(), common.size());
        int[] choice2;

        while ((choice2 = gen2.next()) != null) {
            Set<Node> c = GraphUtils.asSet(choice2, common);

            try {
                Set<Node> b = RecursiveBlocking.blockPathsRecursively(pag, x, y, Set.of(), Set.of(), -1);

                b.removeAll(c);

                if (new MsepTest(pag, false).checkIndependence(x, y, b).isIndependent()) {
                    return true;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return false;
    }

    @Test
    public void test7() {

        // Bryan's example.
        Graph graph = GraphUtils.convert("a-->b,b-->c,c<--d,d-->y,c-->e,e-->f,f-->y");
        Node a = graph.getNode("a");
        Node y = graph.getNode("y");

        try {
            Set<Node> z = RecursiveBlocking.blockPathsRecursively(graph, a, y, Set.of(), Set.of(), -1);
            System.out.println("z = " + z);

            Node f = graph.getNode("f");
            Node d = graph.getNode("d");
            Set<Node> _z = Set.of(f, d);

            assertEquals(_z, z);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testBryanCounterexample_x2_x5() throws InterruptedException {
        // Graph under test:
        //   1. x0 --- x2      (undirected)
        //   2. x0 --> x8
        //   3. x1 --> x4
        //   4. x1 --> x5
        //   5. x1 --> x9
        //   6. x2 --- x3      (undirected)
        //   7. x2 --> x4
        //   8. x3 --> x8
        //   9. x3 --> x9
        //  10. x4 --> x5
        //  11. x4 --> x6
        //  12. x4 --> x7
        //  13. x4 --> x8
        //  14. x5 --> x9
        //  15. x6 --> x9
        //
        // Query: blockPathsRecursively(graph, x2, x5, {}, {}, -1)
        //
        // Wrong answer (current bug): {x4}
        //   - x4 is a non-collider on x2 -> x4 -> x5 (blocks that path),
        //   - BUT x4 is also a definite collider on x2 -> x4 <- x1,
        //     and conditioning on x4 activates it, opening
        //     x2 -> x4 <- x1 -> x5.
        //
        // Correct answers (any valid graphical sepset works):
        //   - {x4, x1} blocks the collider-activated path through x1.
        //
        // We assert that {x4} alone is NOT returned; any correct answer
        // must at least also contain x1 to block the activated collider.

        Graph graph = GraphUtils.convert(
                "x0---x2,x0-->x8,x1-->x4,x1-->x5,x1-->x9,"
                        + "x2---x3,x2-->x4,x3-->x8,x3-->x9,"
                        + "x4-->x5,x4-->x6,x4-->x7,x4-->x8,"
                        + "x5-->x9,x6-->x9"
        );

        Node x1 = graph.getNode("x1");
        Node x2 = graph.getNode("x2");
        Node x4 = graph.getNode("x4");
        Node x5 = graph.getNode("x5");

        Set<Node> z = RecursiveBlocking.blockPathsRecursively(
                graph, x2, x5, Set.of(), Set.of(), -1);

        System.out.println("Returned Z = " + z);

        // Primary assertion: the buggy answer {x4} must NOT be returned.
        assertNotEquals("Returning {x4} leaves x2 -> x4 <- x1 -> x5 open",
                Set.of(x4), z);

        // Any valid sepset must block x2 -> x4 <- x1 -> x5. The collider
        // at x4 is activated (x4 is in Z in every reasonable answer because
        // it's needed for the direct path x2 -> x4 -> x5), so x1 must also
        // be conditioned on.
        assertNotNull("A valid sepset exists ({x1, x4}); algorithm should find one", z);
        assertTrue("Z must contain x1 to block the collider-activated path through x1",
                z.contains(x1));
        assertTrue("Z must contain x4 to block the direct path x2 -> x4 -> x5",
                z.contains(x4));
    }

    @Test
    public void testCounterexample_x0_x5() throws InterruptedException {
        // Graph under test:
        //   1. x0 --> x4
        //   2. x1 --> x3
        //   3. x1 --> x5
        //   4. x1 --> x6
        //   5. x1 --- x9      (undirected)
        //   6. x2 --> x3
        //   7. x2 --> x5
        //   8. x3 --> x4
        //   9. x3 --> x6
        //  10. x3 --> x8
        //  11. x4 --> x5
        //  12. x4 --> x7
        //  13. x5 --> x6
        //  14. x5 --> x7
        //
        // Query: blockPathsRecursively(graph, x0, x5, {}, {}, -1)
        //
        // Wrong answer (observed bug): {x4}
        //   - x4 blocks the direct path x0 -> x4 -> x5 (non-collider in Z),
        //   - BUT x4 is also a definite collider on x0 -> x4 <- x3,
        //     and conditioning on x4 activates it. The activated paths
        //       x0 -> x4 <- x3 <- x1 -> x5
        //       x0 -> x4 <- x3 <- x2 -> x5
        //     remain open.
        //
        // A valid sepset exists: {x4, x3} blocks the direct path via x4 and
        // blocks the activated-collider paths at x3 (non-collider in Z).

        Graph graph = GraphUtils.convert(
                "x0-->x4,x1-->x3,x1-->x5,x1-->x6,x1---x9,"
                        + "x2-->x3,x2-->x5,x3-->x4,x3-->x6,x3-->x8,"
                        + "x4-->x5,x4-->x7,x5-->x6,x5-->x7"
        );

        Node x0 = graph.getNode("x0");
        Node x3 = graph.getNode("x3");
        Node x4 = graph.getNode("x4");
        Node x5 = graph.getNode("x5");

        Set<Node> z = RecursiveBlocking.blockPathsRecursively(
                graph, x0, x5, Set.of(), Set.of(), -1);

        System.out.println("Returned Z = " + z);

        // The buggy answer {x4} must NOT be returned.
        assertNotEquals("Returning {x4} leaves x0 -> x4 <- x3 <- x1 -> x5 open",
                Set.of(x4), z);

        // A valid sepset exists, so the algorithm must not give up.
        assertNotNull("A valid sepset exists (e.g., {x4, x3}); algorithm should find one", z);

        // Validate the returned Z against the graph via MsepTest, to avoid
        // hard-coding a specific answer (multiple valid sepsets exist).
        MsepTest msep = new MsepTest(graph);
        assertTrue("Returned Z = " + z + " must m-separate x0 and x5 in the graph",
                msep.isMSeparated(x0, x5, z));

        // Weakest necessary conditions for any correct answer on this graph:
        // x4 must be present (to block x0 -> x4 -> x5), and some node must
        // block the collider-activated paths through x3. Either x3 itself
        // (as a non-collider on x4 <- x3 <- {x1,x2}) or both x1 and x2
        // (to cover both activated paths) must be in Z.
        assertTrue("Z must contain x4 to block the direct path x0 -> x4 -> x5",
                z.contains(x4));

        boolean blocksActivatedPaths =
                z.contains(x3) ||
                        (z.contains(graph.getNode("x1")) && z.contains(graph.getNode("x2")));
        assertTrue(
                "Z must block the x4-collider-activated paths through x3 " +
                        "(either by containing x3, or by containing both x1 and x2): Z = " + z,
                blocksActivatedPaths);
    }

    @Test
    public void testParanaoid() {
        long seed = System.nanoTime();
        RandomUtil.getInstance().setSeed(1374415095000375L);

        Graph graph = RandomGraph.randomDag(8, 0, 12, 10, 10, 10, false);

        for (Node x : graph.getNodes()) {
            for (Node w : graph.getNodes()) {
                if (x.equals(w)) continue;
                if (graph.isAdjacentTo(w, x)) continue;

                try {
                    Set<Node> blocking = RecursiveBlocking.blockPathsRecursively(
                            graph, x, w, Set.of(), Set.of(), -1);

                    if (blocking != null && !graph.paths().isMSeparatedFrom(x, w, blocking, false)) {
                        System.out.println("Seed: " + seed);
                        System.out.println("x = " + x + ", w = " + w);
                        System.out.println("Blocking set: " + blocking);
                        System.out.println("Graph:\n" + graph);
                        fail("Blocking set not valid for " + x + " and " + w);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Test
    public void testParanaoid2() {
        long seed = System.nanoTime();
        RandomUtil.getInstance().setSeed(seed);

        Graph graph = RandomGraph.randomDag(10, 0, 15, 10, 10, 10, false);

        for (Node x : graph.getNodes()) {
            for (Node w : graph.getNodes()) {
                if (x.equals(w)) continue;
                if (graph.isAdjacentTo(w, x)) continue;

                try {
                    Set<Node> blocking = RecursiveBlocking.blockPathsRecursively(
                            graph, x, w, Set.of(), Set.of(), -1);

                    if (blocking != null && !graph.paths().isMSeparatedFrom(x, w, blocking, false)) {
                        System.out.println("Seed: " + seed);
                        System.out.println("x = " + x + ", w = " + w);
                        System.out.println("Blocking set: " + blocking);
                        System.out.println("Graph:\n" + graph);
                        fail("Blocking set not valid for " + x + " and " + w);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Test
    public void testParanaoid3() {
        long seed = System.nanoTime();
        RandomUtil.getInstance().setSeed(seed);

        Graph graph = RandomGraph.randomDag(10, 0, 15, 10, 10, 10, false);

        for (Node x : graph.getNodes()) {
            for (Node w : graph.getNodes()) {
                if (x.equals(w)) continue;
                if (graph.isAdjacentTo(w, x)) continue;

                try {
                    Set<Node> blocking = RecursiveBlockingRadiusConstrained.blockPathsRecursively(
                            graph, x, w, Set.of(), Set.of(), -1, -1, -1, 1, null);

                    if (blocking != null && !graph.paths().isMSeparatedFrom(x, w, blocking, false)) {
                        System.out.println("Seed: " + seed);
                        System.out.println("x = " + x + ", w = " + w);
                        System.out.println("Blocking set: " + blocking);
                        System.out.println("Graph:\n" + graph);
                        fail("Blocking set not valid for " + x + " and " + w);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
