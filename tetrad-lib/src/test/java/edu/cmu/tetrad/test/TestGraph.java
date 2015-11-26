///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import nu.xom.Element;
import nu.xom.ParsingException;

import java.util.*;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the
 * Graph interface.
 *
 * @author Joseph Ramsey
 */
public final class TestGraph extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestGraph(String name) {
        super(name);
    }

    public void testSearchGraph() {
        checkGraph(new EdgeListGraph());
    }

    public void testEdgeListGraph() {
        checkGraph(new EdgeListGraph());
    }

    private void checkGraph(Graph graph) {
        checkAddRemoveNodes(graph);
        checkCopy(graph);
    }

    public void testXml() {
        Graph graph = new Dag(GraphUtils.randomGraph(10, 0, 10, 30, 15, 15, false));

        Set<Triple> ambiguousTriples = new HashSet<Triple>();
        ambiguousTriples.add(pickRandomTriple(graph));
        ambiguousTriples.add(pickRandomTriple(graph));
        graph.setAmbiguousTriples(ambiguousTriples);

        Set<Triple> underlineTriples = new HashSet<Triple>();
        underlineTriples.add(pickRandomTriple(graph));
        underlineTriples.add(pickRandomTriple(graph));
        graph.setUnderLineTriples(underlineTriples);

        Set<Triple> dottedUnderlineTriples = new HashSet<Triple>();
        dottedUnderlineTriples.add(pickRandomTriple(graph));
        dottedUnderlineTriples.add(pickRandomTriple(graph));
        graph.setDottedUnderLineTriples(dottedUnderlineTriples);

        System.out.println(graph);
       
        Map<String, Node> nodes = new HashMap<String, Node>();

        for (Node node : graph.getNodes()) {
            nodes.put(node.getName(), node);
        }

        System.out.println(GraphUtils.graphToXml(graph));

        Element element = GraphUtils.convertToXml(graph);

        try {
            Graph _graph = GraphUtils.parseGraphXml(element, nodes);

            assertEquals(graph, _graph);
        } catch (ParsingException e) {
            e.printStackTrace();
        }
    }

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

        System.out.println("Ambiguous " + graph.getAmbiguousTriples());
        System.out.println("Underline " + graph.getUnderLines());
        System.out.println("Dotted underline " + graph.getDottedUnderlines());

        assertTrue(graph.getAmbiguousTriples().size() == 1);
        assertTrue(graph.getUnderLines().size() == 3);
        assertTrue(graph.getDottedUnderlines().size() == 1);

        assertTrue(graph.isAmbiguousTriple(x, z, w));
        assertTrue(!graph.isAmbiguousTriple(y, z, w));

        // Waiting for Rob to check out some issues in the ION code.
//        try {
//            graph.isUnderlineTriple(z, x, y);
//            fail();
//        }
//        catch (Exception e) {
//        }

        graph.removeAmbiguousTriple(x, z, w);
        graph.removeUnderlineTriple(x, z, w);
        graph.removeDottedUnderlineTriple(x, z, w);

        assertTrue(graph.getAmbiguousTriples().size() == 0);
        assertTrue(graph.getUnderLines().size() == 2);
        assertTrue(graph.getDottedUnderlines().size() == 0);

        graph.addAmbiguousTriple(x, z, w);
        graph.addUnderlineTriple(x, z, w);
        graph.addDottedUnderlineTriple(x, z, w);

        graph.removeNode(z);

        ((EdgeListGraph) graph).removeTriplesNotInGraph();

        assertTrue(graph.getAmbiguousTriples().size() == 0);
        assertTrue(graph.getUnderLines().size() == 0);
        assertTrue(graph.getDottedUnderlines().size() == 0);

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

        ((EdgeListGraph) graph).removeTriplesNotInGraph();

        assertTrue(graph.getAmbiguousTriples().size() == 0);
        assertTrue(graph.getUnderLines().size() == 1);
        assertTrue(graph.getDottedUnderlines().size() == 0);

        graph.addDirectedEdge(z, w);

        Set<Triple> triples = new HashSet<Triple>();
        triples.add(new Triple(x, z, w));
        triples.add(new Triple(x, y, z));

        try {
            graph.setAmbiguousTriples(triples);
            fail();
        } catch (Exception e) {
            // pass
        }

        triples.remove(new Triple(x, y, z));

        graph.setAmbiguousTriples(triples);
        graph.setUnderLineTriples(triples);
        graph.setDottedUnderLineTriples(triples);
    }

    public void testSimplifiedGraph() {
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");
        Node w = new GraphNode("W");

        List<Node> nodes = new ArrayList<Node>();

        nodes.add(x);
        nodes.add(y);
        nodes.add(z);
        nodes.add(w);

        Graph graph = new MatrixGraphSimplified(nodes);
        graph.addDirectedEdge(x, y);
        graph.addDirectedEdge(y, z);
        System.out.println(graph.getEdges());

        graph.removeEdge(x, y);

        System.out.println(graph);
    }

    private Triple pickRandomTriple(Graph graph) {
        List<Node> nodes = graph.getNodes();

        int trial = 0;

        while (++trial < 1000) {
            int i = RandomUtil.getInstance().nextInt(nodes.size());
            Node y = nodes.get(i);

            List<Node> adjCenter = graph.getAdjacentNodes(y);

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

        System.out.println(graph);

        List<Node> children = graph.getChildren(x1);
        List<Node> parents = graph.getParents(x4);

        System.out.println("Children of x1 = " + children);
        System.out.println("Parents of x4 = " + parents);

        assertTrue(graph.isDConnectedTo(x1, x3, new LinkedList<Node>()));


        graph.removeNode(x2);

        System.out.println("Without x2: " + graph);
    }

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

        graph.setHighlighted(graph.getEdge(x1, x2), true);
        assertTrue(graph.isHighlighted(graph.getEdge(x1, x2)));
        assertTrue(!graph.isHighlighted(graph.getEdge(x2, x3)));

        graph.removeEdge(e1);

        try {
            assertTrue(graph.isHighlighted(Edges.directedEdge(x1, x2)));
            fail("Should have thrown an exception--edge not in graph.");
        } catch (AssertionFailedError e) {
            // Should fail.
        }

    }

    public void testRenameFirst() {
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");

        Graph graph = new EdgeListGraph();
        graph.addNode(x);
        graph.addNode(y);

        x.setName("X1");
        y.setName("Y1");

        graph.addDirectedEdge(x, y);
    }

    private void checkCopy(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph);
        assertEquals(graph, graph2);
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestGraph.class);
    }
}





