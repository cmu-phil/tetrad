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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.RandomUtil;
import nu.xom.Element;
import nu.xom.ParsingException;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the Graph interface.
 *
 * @author josephramsey
 */
public final class TestGraph {

    @Test
    public void testSearchGraph() {
        checkGraph(new EdgeListGraph());
    }

    private void checkGraph(Graph graph) {
        checkAddRemoveNodes(graph);
        checkCopy(graph);
    }

    @Test
    public void testXml() {
        List<Node> nodes1 = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes1.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(RandomGraph.randomGraph(nodes1, 0, 10,
                30, 15, 15, false));

        Set<Triple> ambiguousTriples = new HashSet<>();
        ambiguousTriples.add(pickRandomTriple(graph));
        ambiguousTriples.add(pickRandomTriple(graph));
        graph.setAmbiguousTriples(ambiguousTriples);

        Set<Triple> underlineTriples = new HashSet<>();
        underlineTriples.add(pickRandomTriple(graph));
        underlineTriples.add(pickRandomTriple(graph));
        graph.setUnderLineTriples(underlineTriples);

        Set<Triple> dottedUnderlineTriples = new HashSet<>();
        dottedUnderlineTriples.add(pickRandomTriple(graph));
        dottedUnderlineTriples.add(pickRandomTriple(graph));
        graph.setDottedUnderLineTriples(dottedUnderlineTriples);

        Map<String, Node> nodes = new HashMap<>();

        for (Node node : graph.getNodes()) {
            nodes.put(node.getName(), node);
        }

        Element element = GraphSaveLoadUtils.convertToXml(graph);

        try {
            Graph _graph = GraphSaveLoadUtils.parseGraphXml(element, nodes);

            assertEquals(graph, new Dag(_graph));
        } catch (ParsingException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTripleCode() {
        Graph graph = new EdgeListGraph();
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");
        Node w = new GraphNode("W");

        graph.addNode(x);
        graph.addNode(y);
        graph.addNode(z);
        graph.addNode(w);

        graph.addDirectedEdge(x, z);
        graph.addDirectedEdge(y, z);
        graph.addDirectedEdge(z, w);

        graph.addAmbiguousTriple(x, z, w);
        graph.addUnderlineTriple(x, z, w);
        graph.addDottedUnderlineTriple(x, z, w);

        graph.addUnderlineTriple(y, z, w);
        graph.addUnderlineTriple(y, z, x);

        assertEquals(1, graph.getAmbiguousTriples().size());
        assertEquals(3, graph.getUnderLines().size());
        assertEquals(1, graph.getDottedUnderlines().size());

        assertTrue(graph.isAmbiguousTriple(x, z, w));
        assertFalse(graph.isAmbiguousTriple(y, z, w));

        graph.removeAmbiguousTriple(x, z, w);
        graph.removeUnderlineTriple(x, z, w);
        graph.removeDottedUnderlineTriple(x, z, w);

        assertEquals(0, graph.getAmbiguousTriples().size());
        assertEquals(2, graph.getUnderLines().size());
        assertEquals(0, graph.getDottedUnderlines().size());

        graph.addAmbiguousTriple(x, z, w);
        graph.addUnderlineTriple(x, z, w);
        graph.addDottedUnderlineTriple(x, z, w);

        graph.removeNode(z);

        graph.removeTriplesNotInGraph();

        assertEquals(0, graph.getAmbiguousTriples().size());
        assertEquals(0, graph.getUnderLines().size());
        assertEquals(0, graph.getDottedUnderlines().size());

        graph.addNode(z);

        graph.addDirectedEdge(x, z);
        graph.addDirectedEdge(y, z);
        graph.addDirectedEdge(z, w);

        graph.addAmbiguousTriple(x, z, w);
        graph.addUnderlineTriple(x, z, w);
        graph.addDottedUnderlineTriple(x, z, w);

        graph.addUnderlineTriple(y, z, w);
        graph.addUnderlineTriple(y, z, x);

        graph.removeEdge(z, w);

        graph.removeTriplesNotInGraph();

        assertEquals(0, graph.getAmbiguousTriples().size());
        assertEquals(1, graph.getUnderLines().size());
        assertEquals(0, graph.getDottedUnderlines().size());

        graph.addDirectedEdge(z, w);

        Set<Triple> triples = new HashSet<>();
        triples.add(new Triple(x, z, w));
        triples.add(new Triple(x, y, z));

        graph.setAmbiguousTriples(triples);

        triples.remove(new Triple(x, y, z));

        graph.setAmbiguousTriples(triples);
        graph.setUnderLineTriples(triples);
        graph.setDottedUnderLineTriples(triples);
    }


    @Test
    public void testHighlighted() {
        Graph graph = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        Edge e1 = Edges.directedEdge(x1, x2);
        Edge e2 = Edges.directedEdge(x2, x3);

        graph.addEdge(e1);
        graph.addEdge(e2);

        graph.getEdge(x1, x2).setHighlighted(true);
        assertTrue(graph.getEdge(x1, x2).isHighlighted());
        assertFalse(graph.getEdge(x2, x3).isHighlighted());

        graph.removeEdge(e1);

        assertFalse(Edges.directedEdge(x1, x2).isHighlighted());
    }

    @Test
    public void testLegalCpdag() {
        Graph g1 = GraphUtils.convert("X1---X2,X2---X3,X3---X4,X4---X1");
        assertFalse(g1.paths().isLegalCpdag());

        Graph g2 = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        g2 = GraphTransforms.dagToCpdag(g2);

        assertTrue(g2.paths().isLegalCpdag());

        Graph g3 = GraphUtils.convert("X1-->X2,X2-->X3,X3-->X4,X4-->X5,X5-->X6,X2---X7,X7-->X8");
        assertFalse(g3.paths().isLegalCpdag());

        Graph g4 = GraphUtils.convert("X1-->X2,X2---X3,X3<--X4");
        assertFalse(g4.paths().isLegalCpdag());

        Graph g5 = GraphUtils.convert("X1---X2,X2---X3,X3---X4,X1---X4,X1--X3");
        assertFalse(g5.paths().isLegalCpdag());

        Graph g6 = GraphUtils.convert("X1-->X2,X2<--X3");
        assertTrue(g6.paths().isLegalCpdag());
    }


    private Triple pickRandomTriple(Graph graph) {
        List<Node> nodes = graph.getNodes();

        int trial = 0;

        while (++trial < 1000) {
            int i = RandomUtil.getInstance().nextInt(nodes.size());
            Node y = nodes.get(i);

            List<Node> adjCenter = new ArrayList<>(graph.getAdjacentNodes(y));

            if (adjCenter.isEmpty()) {
                continue;
            }

            int j1 = RandomUtil.getInstance().nextInt(adjCenter.size());
            int j2 = RandomUtil.getInstance().nextInt(adjCenter.size());

            if (j1 == j2) {
                continue;
            }

            Node x = adjCenter.get(j1);
            Node z = adjCenter.get(j2);

            return new Triple(x, y, z);
        }

        throw new IllegalArgumentException("Couldn't find a random triple.");
    }

    private void checkAddRemoveNodes(Graph graph) {
        Node x1 = new GraphNode("x1");
        Node x2 = new GraphNode("x2");
        Node x3 = new GraphNode("x3");
        Node x4 = new GraphNode("x4");
        Node x5 = new GraphNode("x5");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);

        List<Node> children = graph.getChildren(x1);
        List<Node> parents = graph.getParents(x4);

        assertTrue(graph.paths().isMConnectedTo(x1, x3, new HashSet<>(), false));


        graph.removeNode(x2);

    }

    /**
     * Tests the adjustment set method.
     */
    @Test
    public void testAdjustmentSet1() {
        Graph graph = new EdgeListGraph();
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x1);
        graph.addDirectedEdge(x4, x2);
        graph.addDirectedEdge(x4, x3);

        try {
            List<Set<Node>> adjustmentSets = graph.paths().adjustmentSets(x1, x3, 4, 2, 1, 6);
            System.out.println(adjustmentSets);
        } catch (Exception e) {
            System.out.println("No adjustment set: " + e.getMessage());
        }
    }


    /**
     * Tests the adjustment set method.
     */
    @Test
    public void testAdjustmentSet2() {
        RandomUtil.getInstance().setSeed(3848234422L);

        Graph graph = RandomGraph.randomGraph(20, 0, 80, 30, 15, 15, false);

        System.out.println(graph);

        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                Node x = graph.getNodes().get(i);
                Node y = graph.getNodes().get(j);

                try {
                    List<Set<Node>> adjustmentSetsNearSource = graph.paths().adjustmentSets(x, y, 8, 2, 1, 6);
                    List<Set<Node>> adjustmentSetsNearTarget = graph.paths().adjustmentSets(x, y, 8, 2, 2, 6);

                    System.out.println("x " + x + " y " + y);
                    System.out.println("    AdjustmentSets near source: " + adjustmentSetsNearSource);
                    System.out.println("    AdjustmentSets near target: " + adjustmentSetsNearTarget);
                } catch (Exception e) {
                    System.out.println("No adjustment set: " + e.getMessage());
                }
            }
        }
    }

    @Test
    public void testAdjustmentSet3() {
        Graph graph = GraphSaveLoadUtils.loadGraphTxt(new File("/Users/josephramsey/Downloads/graph6 (1).txt"));
        File _file = new File("/Users/josephramsey/Downloads/adjustment_mike_out.txt");

        try (PrintWriter out = new PrintWriter(_file)) {
            long start = System.currentTimeMillis();

            out.println(new Date());
            out.println();
            out.println(graph);

            List<Node> graphNodes = graph.getNodes();

            for (int i = 0; i < graphNodes.size(); i++) {
                for (int j = 0; j < graphNodes.size(); j++) {
                    Node x = graph.getNodes().get(i);
                    Node y = graph.getNodes().get(j);

                    List<Set<Node>> adjustmentSetsNearSource = new ArrayList<>();
                    try {
                        adjustmentSetsNearSource = graph.paths().adjustmentSets(x, y, 4, 4, 1, 8);
                    } catch (Exception e) {
                        System.out.println("No adjustment set new source: " + e.getMessage());
                    }
                    List<Set<Node>> adjustmentSetsNearTarget = new ArrayList<>();
                    try {
                        adjustmentSetsNearTarget = graph.paths().adjustmentSets(x, y, 4, 4, 2, 8);
                    } catch (Exception e) {
                        System.out.println("No adjustment set new target: " + e.getMessage());
                    }

                    out.println("source = " + x + " target = " + y);
                    out.println("    AdjustmentSets near source: " + adjustmentSetsNearSource);
                    out.println("    AdjustmentSets near target: " + adjustmentSetsNearTarget);
                }
            }

            long stop = System.currentTimeMillis();
            out.println("Time: " + (stop - start) / 1000.0 + " seconds");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


    private void checkCopy(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph);
        assertEquals(graph, graph2);
    }
}





