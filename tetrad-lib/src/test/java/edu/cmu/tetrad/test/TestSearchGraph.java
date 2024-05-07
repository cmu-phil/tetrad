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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fas;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.SublistGenerator;
import org.junit.Test;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.log;
import static org.apache.commons.math3.util.FastMath.pow;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author josephramsey
 */
public final class TestSearchGraph {

    public static Graph erdosRenyiGraph(int n, int e) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) nodes.add(new GraphNode("X" + i));

        Graph graph = new EdgeListGraph(nodes);

        for (int e0 = 0; e0 < e; e0++) {
            int i1 = RandomUtil.getInstance().nextInt(n);
            int i2 = RandomUtil.getInstance().nextInt(n);

            if (i1 == i2) {
                e0--;
                continue;
            }

            Edge edge = Edges.undirectedEdge(nodes.get(i1), nodes.get(i2));

            if (graph.containsEdge(edge)) {
                e0--;
                continue;
            }

            graph.addEdge(edge);
        }

        return graph;
    }

    public static Graph weightedRandomGraph(int n, int e) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) nodes.add(new GraphNode("X" + i));

        Graph graph = new EdgeListGraph(nodes);

        for (int e0 = 0; e0 < e; e0++) {
            int i1 = TestSearchGraph.weightedRandom(nodes, graph);
//            int i2 = RandomUtil.getInstance().nextInt(n);
            int i2 = TestSearchGraph.weightedRandom(nodes, graph);

            if (!(TestSearchGraph.shortestPath(nodes.get(i1), nodes.get(i2), graph) < 9)) {
                e0--;
                continue;
            }

            if (i1 == i2) {
                e0--;
                continue;
            }

            Edge edge = Edges.undirectedEdge(nodes.get(i1), nodes.get(i2));

            if (graph.containsEdge(edge)) {
                e0--;
                continue;
            }

            graph.addEdge(edge);
        }

        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();

            if (!graph.paths().isAncestorOf(n2, n1)) {
                graph.removeEdge(edge);
                graph.addDirectedEdge(n1, n2);
            } else {
                graph.removeEdge(edge);
                graph.addDirectedEdge(n2, n1);
            }
        }

        return graph;
    }

    private static int shortestPath(Node n1, Node n2, Graph g) {
        Queue<Node> Q = new ArrayDeque<>();
        Map<Node, Node> V = new HashMap<>();

        Q.offer(n1);
        V.put(n1, null);

        while (!Q.isEmpty()) {
            Node m = Q.poll();

            if (V.containsKey(n2)) break;

            for (Node p : g.getAdjacentNodes(m)) {
                if (V.containsKey(p)) continue;

                Q.offer(p);
                V.put(p, m);
            }
        }

        int s = 0;

        do {
            s++;
            n2 = V.get(n2);
        } while (n2 != null);

        return s;
    }

    private static int weightedRandom(List<Node> nodes, Graph graph) {
        int total = 0;
        int n = nodes.size();

        for (int b = 0; b < n; b++) {
            total = TestSearchGraph.weight(nodes, graph, total, b);
        }

        int r = RandomUtil.getInstance().nextInt(total);

        int count = 0;
        int index = 0;

        for (int b = 0; b < n; b++) {
            count = TestSearchGraph.weight(nodes, graph, count, b);
            if (r <= count) {
                index = b;
                break;
            }
        }

        return index;
    }

    private static int weight(List<Node> nodes, Graph graph, int total, int b) {
        final double p = 1;
        int degree = graph.getNumEdges(nodes.get(b));
        int t = degree + 1;
        total += pow(t, p);
        return total;
    }

    private static double characteristicPathLength(Graph g) {
        List<Node> nodes = g.getNodes();
        int total = 0;
        int count = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i; j < nodes.size(); j++) {
                int shortest = TestSearchGraph.shortestPath(nodes.get(i), nodes.get(j), g);
                total += shortest;
                count++;
            }
        }

        return total / (double) count;
    }

    /**
     * Tests to see if d separation facts are symmetric.
     */
    @Test
    public void testMSeparation() {
        List<Node> nodes1 = new ArrayList<>();

        for (int i1 = 0; i1 < 7; i1++) {
            nodes1.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        EdgeListGraph graph = new EdgeListGraph(new Dag(RandomGraph.randomGraph(nodes1, 0, 7,
                30, 15, 15, true)));

        List<Node> nodes = graph.getNodes();

        final int depth = -1;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                List<Node> theRest = new ArrayList<>(nodes);
                theRest.remove(x);
                theRest.remove(y);

                SublistGenerator gen = new SublistGenerator(theRest.size(), depth);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Set<Node> z = new HashSet<>();

                    for (int value : choice) {
                        z.add(theRest.get(value));
                    }

                    if (graph.isMSeparatedFrom(x, y, z) != graph.isMSeparatedFrom(y, x, z)) {
                        System.out.println(graph);

                        fail(LogUtilsSearch.independenceFact(x, y, z) + " should have same m-sep result as " +
                             LogUtilsSearch.independenceFact(y, x, z));
                    }
                }

            }
        }
    }

    /**
     * Tests to see if d separation facts are symmetric.
     */
    @Test
    public void testMSeparation2() {
        List<Node> nodes1 = new ArrayList<>();

        for (int i1 = 0; i1 < 7; i1++) {
            nodes1.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        EdgeListGraph graph = new EdgeListGraph(new Dag(RandomGraph.randomGraph(nodes1, 0, 14,
                30, 15, 15, true)));

        List<Node> nodes = graph.getNodes();

        final int depth = -1;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                List<Node> theRest = new ArrayList<>(nodes);

                SublistGenerator gen = new SublistGenerator(theRest.size(), depth);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Set<Node> z = new HashSet<>();

                    for (int value : choice) {
                        z.add(theRest.get(value));
                    }

                    boolean mConnectedTo = graph.paths().isMConnectedTo(x, y, z, false);
                    boolean mConnectedTo1 = graph.paths().isMConnectedTo(y, x, z, false);

                    if (mConnectedTo != mConnectedTo1) {
                        System.out.println(x + " d connected to " + y + " given " + z);
                        System.out.println(graph);
                        System.out.println("mconnectedto = " + mConnectedTo);
                        System.out.println("mconnecteto1 = " + mConnectedTo1);
                        fail();
                    }
                }

            }
        }
    }

    // Trying to trip up the breadth first algorithm.
    public void testMSeparation3() {
        Graph graph = GraphUtils.convert("x-->s1,x-->s2,s1-->s3,s3-->s2,s3&lt;--y");
        assertTrue(graph.paths().isMSeparatedFrom(graph.getNode("x"), graph.getNode("y"), new HashSet<>(), false));

        graph = GraphUtils.convert("1-->2,2&lt;--4,2-->7,2-->3");
        assertTrue(graph.paths().isMSeparatedFrom(graph.getNode("4"), graph.getNode("1"), new HashSet<>(), false));

        graph = GraphUtils.convert("X1-->X5,X1-->X6,X2-->X3,X4-->X6,X5-->X3,X6-->X5,X7-->X3");
        assertTrue(mConnected(graph, "X2", "X4", "X3", "X6"));

        graph = GraphUtils.convert("X1&lt;--X2,X1&lt;--X3,X2-->X3,X3&lt;--X4");
        assertTrue(mConnected(graph, "X1", "X4", "X3"));

        graph = GraphUtils.convert("X2-->X7,X3-->X2,X5-->X1,X5-->X2,X6-->X1,X7-->X6,X2->X4");
        assertTrue(mConnected(graph, "X1", "X3"));

        graph = GraphUtils.convert("1-->3,1-->4,2-->5,4-->5,4-->7,6-->5,7-->3");
        assertTrue(mConnected(graph, "1", "4"));
    }

    public void rtestMSeparation4() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(RandomGraph.randomGraph(nodes, 20, 100,
                5, 5, 5, false));

        long start, stop;
        final int depth = -1;

        IndependenceTest test = new MsepTest(graph);

        Rfci fci = new Rfci(test);
        Fas fas = new Fas(test);
        start = MillisecondTimes.timeMillis();
        fci.setDepth(depth);
        fci.setVerbose(false);
        fci.search(fas, fas.getNodes());
        stop = MillisecondTimes.timeMillis();

        System.out.println("MSEP RFCI");
        System.out.println("# msep checks = " + fas.getNumIndependenceTests());
        System.out.println("Elapsed " + (stop - start));
        System.out.println("Per " + fas.getNumIndependenceTests() / (double) (stop - start));

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);
        IndependenceTest test2 = new IndTestFisherZ(data, 0.001);

        Rfci fci3 = new Rfci(test2);
        Fas fas2 = new Fas(test2);
        start = MillisecondTimes.timeMillis();
        fci3.setDepth(depth);
        fci3.search(fas2, fas2.getNodes());
        stop = MillisecondTimes.timeMillis();

        System.out.println("FISHER Z RFCI");
        System.out.println("# indep checks = " + fas.getNumIndependenceTests());
        System.out.println("Elapsed " + (stop - start));
        System.out.println("Per " + fas.getNumIndependenceTests() / (double) (stop - start));
    }

    private boolean mConnected(Graph graph, String x, String y, String... z) {
        Node _x = graph.getNode(x);
        Node _y = graph.getNode(y);

        Set<Node> _z = new HashSet<>();

        for (String name : z) {
            _z.add(graph.getNode(name));
        }

        return graph.paths().isMConnectedTo(_x, _y, _z, false);
    }

    public void testAlternativeGraphs() {

        Graph graph = TestSearchGraph.weightedRandomGraph(250, 400);

        List<Integer> degreeCounts = new ArrayList<>();
        Map<Integer, Integer> degreeCount = new HashMap<>();

        for (Node node : graph.getNodes()) {
            int degree = graph.getNumEdges(node);
            degreeCounts.add(degree);


            if (degreeCount.get(degree) == null) {
                degreeCount.put(degree, 0);
            }

            degreeCount.put(degree, degreeCount.get(degree) + 1);
        }

        Collections.sort(degreeCounts);
        System.out.println(degreeCounts);
        List<Integer> _degrees = new ArrayList<>(degreeCount.keySet());
        Collections.sort(_degrees);

        for (int i : _degrees) {
            int j = degreeCount.get(i);
//            System.out.println(i + " " + j);
            System.out.println(log(i + 1) + " " + log(j));
        }

        System.out.println("\nCPL = " + TestSearchGraph.characteristicPathLength(graph));

        Graph erGraph = TestSearchGraph.erdosRenyiGraph(200, 200);
        System.out.println("\n ER CPL = " + TestSearchGraph.characteristicPathLength(erGraph));
    }

    public void test11() {
        Node x = new GraphNode("X");
        List<Node> nodes = Collections.singletonList(x);

        Graph g = new EdgeListGraph(nodes);

        Graph g2 = new EdgeListGraph(g);

        System.out.println();
    }
}


