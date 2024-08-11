    ///////////////////////////////////////////////////////////////////////////////
    // For information as to what this class does, see the Javadoc, below.       //
    // Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
    // 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
    // Scheines, Joseph Ramsey, and Clark Glymour.                               //
    //                                                                           //
    // This program is free software; you can redistribute it and/or modify      //
    // it under the terms of the GNU General Public License as published by      //
    // the Free Software Foundation; either version 2 of the License, or         //
    // (at your option) any later version.                                       //
    //                                                                           //
    // This program is distributed in the hope that it will be useful,           //
    // but WITHOUT ANY WARRANTY; without even the implied warranty of            //
    // MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
    // GNU General Public License for more details.                              //
    //                                                                           //
    // You should have received a copy of the GNU General Public License         //
    // along with this program; if not, write to the Free Software               //
    // Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
    ///////////////////////////////////////////////////////////////////////////////

    package edu.cmu.tetrad.test;

    import edu.cmu.tetrad.data.ContinuousVariable;
    import edu.cmu.tetrad.data.Knowledge;
    import edu.cmu.tetrad.graph.*;
    import edu.cmu.tetrad.search.utils.FciOrient;
    import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
    import edu.cmu.tetrad.util.RandomUtil;
    import org.jetbrains.annotations.Nullable;
    import org.junit.Test;

    import java.util.*;

    import static junit.framework.TestCase.assertEquals;
    import static org.junit.Assert.*;

    /**
     * @author josephramsey
     */
    public final class TestGraphUtils {

        @Test
        public void testCreateRandomDag() {
            List<Node> nodes = new ArrayList<>();

            for (int i = 0; i < 50; i++) {
                nodes.add(new ContinuousVariable("X" + (i + 1)));
            }

            Dag dag = new Dag(RandomGraph.randomGraph(nodes, 0, 50,
                    4, 3, 3, false));

            assertEquals(50, dag.getNumNodes());
            assertEquals(50, dag.getNumEdges());
        }

        @Test
        public void testDirectedPaths() {
            List<Node> nodes = new ArrayList<>();

            for (int i1 = 0; i1 < 6; i1++) {
                nodes.add(new ContinuousVariable("X" + (i1 + 1)));
            }

            Graph graph = new Dag(RandomGraph.randomGraph(nodes, 0, 6,
                    3, 3, 3, false));

            for (int i = 0; i < graph.getNodes().size(); i++) {
                for (int j = 0; j < graph.getNodes().size(); j++) {
                    Node node1 = graph.getNodes().get(i);
                    Node node2 = graph.getNodes().get(j);

                    List<List<Node>> directedPaths = graph.paths().directedPaths(node1, node2, -1);

                    for (List<Node> path : directedPaths) {
                        assertTrue(graph.paths().isAncestorOf(path.get(0), path.get(path.size() - 1)));
                    }
                }
            }
        }

        @Test
        public void testTreks() {
            List<Node> nodes = new ArrayList<>();

            for (int i1 = 0; i1 < 10; i1++) {
                nodes.add(new ContinuousVariable("X" + (i1 + 1)));
            }

            Graph graph = new Dag(RandomGraph.randomGraph(nodes, 0, 15,
                    3, 3, 3, false));

            for (int i = 0; i < graph.getNodes().size(); i++) {
                for (int j = 0; j < graph.getNodes().size(); j++) {
                    Node node1 = graph.getNodes().get(i);
                    Node node2 = graph.getNodes().get(j);

                    List<List<Node>> treks = graph.paths().treks(node1, node2, -1);

                    TREKS:
                    for (List<Node> trek : treks) {
                        Node m0 = trek.get(0);
                        Node m1 = trek.get(trek.size() - 1);

                        for (Node n : trek) {

                            // Not quite it but good enough for a test.
                            if (graph.paths().isAncestorOf(n, m0) && graph.paths().isAncestorOf(n, m1)) {
                                continue TREKS;
                            }
                        }

                        fail("Some trek failed.");
                    }
                }
            }
        }

        @Test
        public void testGraphToDot() {
            final long seed = 28583848283L;
            RandomUtil.getInstance().setSeed(seed);

            List<Node> nodes = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                nodes.add(new ContinuousVariable("X" + (i + 1)));
            }

            Graph g = new Dag(RandomGraph.randomGraph(nodes, 0, 5,
                    30, 15, 15, false));

            String x = GraphSaveLoadUtils.graphToDot(g);
            String[] tokens = x.split("\n");
            int length = tokens.length;
            assertEquals(7, length);

        }

        @Test
        public void testTwoCycleErrors() {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph trueGraph = new EdgeListGraph();
            trueGraph.addNode(x1);
            trueGraph.addNode(x2);
            trueGraph.addNode(x3);
            trueGraph.addNode(x4);

            Graph estGraph = new EdgeListGraph();
            estGraph.addNode(x1);
            estGraph.addNode(x2);
            estGraph.addNode(x3);
            estGraph.addNode(x4);

            trueGraph.addDirectedEdge(x1, x2);
            trueGraph.addDirectedEdge(x2, x1);
            trueGraph.addDirectedEdge(x2, x3);
            trueGraph.addDirectedEdge(x3, x2);

            estGraph.addDirectedEdge(x1, x2);
            estGraph.addDirectedEdge(x2, x1);
            estGraph.addDirectedEdge(x3, x4);
            estGraph.addDirectedEdge(x4, x3);
            estGraph.addDirectedEdge(x4, x1);
            estGraph.addDirectedEdge(x1, x4);

            GraphUtils.TwoCycleErrors errors = GraphUtils.getTwoCycleErrors(trueGraph, estGraph);

            assertEquals(1, errors.twoCycCor);
            assertEquals(2, errors.twoCycFp);
            assertEquals(1, errors.twoCycFn);
        }

        @Test
        public void testMsep() {
            Node a = new ContinuousVariable("A");
            Node b = new ContinuousVariable("B");
            Node x = new ContinuousVariable("X");
            Node y = new ContinuousVariable("Y");

            Graph graph = new EdgeListGraph();

            graph.addNode(a);
            graph.addNode(b);
            graph.addNode(x);
            graph.addNode(y);

            graph.addDirectedEdge(a, x);
            graph.addDirectedEdge(b, y);
            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(y, x);

    //        System.out.println(graph);

            assertTrue(graph.paths().isAncestorOf(a, a));
            assertTrue(graph.paths().isAncestorOf(b, b));
            assertTrue(graph.paths().isAncestorOf(x, x));
            assertTrue(graph.paths().isAncestorOf(y, y));

            assertTrue(graph.paths().isAncestorOf(a, x));
            assertFalse(graph.paths().isAncestorOf(x, a));
            assertTrue(graph.paths().isAncestorOf(a, y));
            assertFalse(graph.paths().isAncestorOf(y, a));

            assertTrue(graph.paths().isAncestorOf(a, y));
            assertTrue(graph.paths().isAncestorOf(b, x));

            assertFalse(graph.paths().isAncestorOf(a, b));
            assertFalse(graph.paths().isAncestorOf(y, a));
            assertFalse(graph.paths().isAncestorOf(x, b));

            assertTrue(graph.paths().isMConnectedTo(a, y, new HashSet<>(), false));
            assertTrue(graph.paths().isMConnectedTo(b, x, new HashSet<>(), false));

            // MSEP problem now with 2-cycles. TODO
            assertTrue(graph.paths().isMConnectedTo(a, y, Collections.singleton(x), false));
            assertTrue(graph.paths().isMConnectedTo(b, x, Collections.singleton(y), false));

            assertTrue(graph.paths().isMConnectedTo(a, y, Collections.singleton(b), false));
            assertTrue(graph.paths().isMConnectedTo(b, x, Collections.singleton(a), false));

            assertTrue(graph.paths().isMConnectedTo(y, a, Collections.singleton(b), false));
            assertTrue(graph.paths().isMConnectedTo(x, b, Collections.singleton(a), false));
        }

        @Test
        public void testMsep2() {
            Node a = new ContinuousVariable("A");
            Node b = new ContinuousVariable("B");
            Node c = new ContinuousVariable("C");

            Graph graph = new EdgeListGraph();

            graph.addNode(a);
            graph.addNode(b);
            graph.addNode(c);

            graph.addDirectedEdge(a, b);
            graph.addDirectedEdge(b, c);
            graph.addDirectedEdge(c, b);

    //        System.out.println(graph);

            assertTrue(graph.paths().isAncestorOf(a, b));
            assertTrue(graph.paths().isAncestorOf(a, c));

            // MSEP problem now with 2-cycles. TODO
            assertTrue(graph.paths().isMConnectedTo(a, b, Collections.EMPTY_SET, false));
            assertTrue(graph.paths().isMConnectedTo(a, c, Collections.EMPTY_SET, false));
    //
            assertTrue(graph.paths().isMConnectedTo(a, c, Collections.singleton(b), false));
            assertTrue(graph.paths().isMConnectedTo(c, a, Collections.singleton(b), false));
        }


        public void test8() {
            final int numNodes = 5;

            for (int i = 0; i < 100; i++) {
                Graph graph = RandomGraph.randomGraphRandomForwardEdges(numNodes, 0, numNodes, 10, 10, 10, true);

                List<Node> nodes = graph.getNodes();
                Node x = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
                Node y = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
                Node z1 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
                Node z2 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));

                if (graph.paths().isMSeparatedFrom(x, y, set(z1), false) && graph.paths().isMSeparatedFrom(x, y, set(z2), false) &&
                    !graph.paths().isMSeparatedFrom(x, y, set(z1, z2), false)) {
                    System.out.println("x = " + x);
                    System.out.println("y = " + y);
                    System.out.println("z1 = " + z1);
                    System.out.println("z2 = " + z2);
                    System.out.println(graph);
                    return;
                }
            }
        }


        @Test
        public void test9() {

            Graph graph = RandomGraph.randomGraphRandomForwardEdges(20, 0, 50,
                    10, 10, 10, false);
            graph = GraphTransforms.dagToCpdag(graph);

            int numSmnallestSizes = 2;

            System.out.println(graph);

            System.out.println("Number of smallest sizes printed = " + numSmnallestSizes);

            List<Node> nodes = graph.getNodes();

            for (Node x : nodes) {
                for (Node y : nodes) {
                    if (x == y) continue;
                    Set<Set<Node>> sets = GraphUtils.visibleEdgeAdjustments3(graph, x, y, numSmnallestSizes, GraphUtils.GraphType.CPDAG);

                    if (sets.isEmpty()) {
                        continue;
                    }

                    System.out.println();

                    for (Set<Node> set : sets) {
                        System.out.println("For " + x + "-->" + y + ", set = " + set);
                    }
                }
            }
        }

        private static @Nullable Graph getGraphWithoutXToYPag(Node x, Node y, Graph graph) {
            if (!graph.isAdjacentTo(x, y)) return null;

            if (Edges.isBidirectedEdge(graph.getEdge(x, y))) {
                return null;
            } else if (Edges.isPartiallyOrientedEdge(graph.getEdge(x, y)) && graph.getEdge(x, y).pointsTowards(x)) {
                return null;
            } else if (Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                return null;
            }

            Graph _graph = new EdgeListGraph(graph);

            _graph.removeEdge(x, y);
            _graph.addDirectedEdge(x, y);

            Knowledge knowledge = new Knowledge();
            knowledge.setRequired(x.getName(), y.getName());

            FciOrient fciOrientation = new FciOrient(R0R4StrategyTestBased.defaultConfiguration(graph, knowledge));
            fciOrientation.orient(_graph);

            _graph.removeEdge(x, y);
            return _graph;
        }

        @Test
        public void test10() {

            Graph graph = RandomGraph.randomGraphRandomForwardEdges(10, 2, 10,
                    10, 10, 10, false);
            graph = GraphTransforms.dagToPag(graph);

            int numSmnallestSizes = 2;

            System.out.println(graph);

            System.out.println("Number of smallest sizes printed = " + numSmnallestSizes);

            List<Node> nodes = graph.getNodes();

            for (Node x : nodes) {
                for (Node y : nodes) {
                    if (x == y) continue;
                    Set<Set<Node>> sets = null;
                    try {
                        sets = GraphUtils.visibleEdgeAdjustments1(graph, x, y, numSmnallestSizes, GraphUtils.GraphType.PAG);
                    } catch (Exception e) {
                        continue;
                    }

                    if (sets.isEmpty()) {
                        continue;
                    }

                    System.out.println();

                    for (Set<Node> set : sets) {
                        System.out.println("For " + x + "-->" + y + ", set = " + set);
                    }
                }
            }
        }

        @Test
        public void test11() {
//            RandomUtil.getInstance().setSeed(1040404L);

            // 10 times over, make a random DAG
            for (int i = 0; i < 1000; i++) {
                Graph graph = RandomGraph.randomGraphRandomForwardEdges(5, 0, 5,
                        100, 100, 100, false);

                // Construct its CPDAG
                Graph cpdag = GraphTransforms.dagToCpdag(graph);
                assertTrue(cpdag.paths().isLegalCpdag());
                assertTrue(cpdag.paths().isLegalMpdag());

//                if (!cpdag.paths().isLegalCpdag()) {
//
//                    System.out.println("Not legal CPDAG:");
//
//                    System.out.println(cpdag);
//
//                    List<Node> pi = new ArrayList<>(cpdag.getNodes());
//                    cpdag.paths().makeValidOrder(pi);
//
//                    System.out.println("Valid order: " + pi);
//
//                    Graph dag = Paths.getDag(pi, cpdag, true);
//
//                    System.out.println("DAG: " + dag);
//
//                    Graph cpdag2 = GraphTransforms.dagToCpdag(dag);
//
//                    System.out.println("CPDAG for DAG: " + cpdag2);
//
//                    break;
//                }
            }

        }

        private Set<Node> set(Node... z) {
            Set<Node> list = new HashSet<>();
            Collections.addAll(list, z);
            return list;
        }

        /**
         * A test of m-connection. We generate 10 random graphs with latents and check that dagToPag
         * produces a legal PAG. We then call dagToPag again on the PAG and check that the result is
         * also a legal PAG.
         */
        @Test
        public void test12() {
            RandomUtil.getInstance().setSeed(1040404L);

            for (int i = 0; i < 10; i++) {
                Graph graph = RandomGraph.randomGraph(10, 3, 10,
                        10, 10, 10, false);
                Graph pag = GraphTransforms.dagToPag(graph);
                assertTrue(pag.paths().isLegalPag());
                Graph pag2 = GraphTransforms.dagToPag(pag);
                assertTrue(pag2.paths().isLegalPag());
            }
        }
    }





