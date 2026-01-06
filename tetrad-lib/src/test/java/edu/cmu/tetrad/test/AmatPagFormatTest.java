package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * The AmatPagFormatTest class contains a set of unit tests for validating the
 * conversion of graph structures to and from the "amat.pag" representation.
 * It tests various scenarios involving directed, undirected, and mixed edges.
 * The class extends GraphFormatTestUtils to leverage utility methods for
 * graph comparison, temporary file creation, and amat.pag parsing.
 *
 * The test cases ensure the correctness of:
 * - Representing directed and undirected edges in amat.pag format.
 * - Round-trip conversion of graphs to and from amat.pag format.
 * - Parsing the adjacency matrix structure of amat.pag representation.
 *
 * Test scenarios include:
 * - Single directed edges (various endpoint styles: tail, arrow, circle).
 * - Bidirected edges with arrowheads at both ends.
 * - Mixed graphs with combinations of edge types and multiple nodes.
 */
public class AmatPagFormatTest extends GraphFormatTestUtils {

    /**
     * Tests the round-trip functionality of saving and loading a graph with an edge
     * having a tail at one node and an arrow at another node in the AMAT PAG format.
     *
     * The method creates a simple graph consisting of two nodes (X1 and X2)
     * connected by an edge with specific endpoints (TAIL at X1, ARROW at X2).
     * It then converts the graph to the AMAT PAG format and writes it to a temporary file.
     * The graph is reloaded from the file, and the original graph is compared
     * to the reloaded graph to ensure they are equal.
     *
     * This test asserts that the serialization and deserialization process
     * preserves the structure and properties of the graph, including its nodes and edges.
     *
     * @throws Exception if an error occurs during the graph save, load, or comparison process.
     */
    @Test
    public void twoNodeArrow_roundTrip() throws Exception {
        // X1 -> X2 (tail at X1, arrow at X2)
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph g = new EdgeListGraph(Arrays.asList(x1, x2));
        g.addEdge(new Edge(x1, x2, Endpoint.TAIL, Endpoint.ARROW));

        String amat = GraphSaveLoadUtils.graphToAmatPag(g);
        File f = writeToTempFile(amat, ".pag");
        Graph g2 = GraphSaveLoadUtils.loadGraphAmatPag(f);

        assertGraphsEqual(g, g2);
    }

    /**
     * Tests the matrix pattern representation of a graph with two nodes connected
     * by an edge having a circle at one endpoint and an arrow at the other in the AMAT PAG format.
     *
     * The method creates a graph with two nodes (X1 and X2) connected by a directed edge
     * with a circle at X1 and an arrow at X2. It then converts the graph to an AMAT PAG
     * string format and parses the corresponding adjacency matrix.
     *
     * The matrix representation follows a specific encoding where:
     * - A circle is denoted by 1.
     * - An arrow is denoted by 2.
     * - A tail (if present) is denoted by 3.
     * - No edge is denoted by 0.
     *
     * Assertions verify the correctness of the encoding by checking:
     * - The entry corresponding to the arrow endpoint at X2 (row X1, column X2).
     * - The entry corresponding to the circle endpoint at X1 (row X2, column X1).
     */
    @Test
    public void twoNodeCircleArrow_matrixPattern() {
        // X1 o-> X2 (circle at X1, arrow at X2)
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph g = new EdgeListGraph(Arrays.asList(x1, x2));
        g.addEdge(new Edge(x1, x2, Endpoint.CIRCLE, Endpoint.ARROW));

        String amat = GraphSaveLoadUtils.graphToAmatPag(g);
        int[][] m = parseAmatFromString(amat, Arrays.asList(x1, x2));

        // pcalg amat.pag: entry [i,j] is mark at column node j
        // codes: circle=1, arrow=2, tail=3, 0=no edge
        // X1 o-> X2 => mark at X2 is ARROW (2), mark at X1 is CIRCLE (1)
        assertEquals(2, m[0][1]); // row X1, col X2 = arrow at X2
        assertEquals(1, m[1][0]); // row X2, col X1 = circle at X1
    }

    /**
     * Tests the round-trip functionality of saving and loading a graph with a bidirected edge
     * in the AMAT PAG format.
     *
     * The method creates a simple graph consisting of two nodes (X1 and X2) connected by a bidirected edge
     * (arrowheads at both ends). It then converts the graph to an AMAT PAG string representation and writes
     * it to a temporary file. The graph is reloaded from this file, and the original graph is compared
     * to the reloaded graph to ensure structural and property equivalence.
     *
     * This test verifies that the serialization and deserialization process preserves the structure and
     * properties of the graph, including the bidirected edge between the two nodes.
     *
     * @throws Exception if an error occurs during the graph save, load, or comparison process.
     */
    @Test
    public void twoNodeBidirected_roundTrip() throws Exception {
        // X1 <-> X2 (arrowheads at both ends)
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph g = new EdgeListGraph(Arrays.asList(x1, x2));
        g.addEdge(new Edge(x1, x2, Endpoint.ARROW, Endpoint.ARROW));

        String amat = GraphSaveLoadUtils.graphToAmatPag(g);
        File f = writeToTempFile(amat, ".pag");
        Graph g2 = GraphSaveLoadUtils.loadGraphAmatPag(f);

        assertGraphsEqual(g, g2);
    }

    /**
     * Tests the round-trip functionality of saving and loading a mixed-type graph
     * with three nodes using the AMAT PAG format.
     *
     * The method constructs a graph with three nodes (X1, X2, X3) and edges:
     * - X1 o-> X2 (circle-to-arrow edge)
     * - X2 <-> X3 (bidirected edge with arrowheads on both ends)
     * - X1 --- X3 (undirected edge with tails at both ends)
     *
     * The graph is serialized to the AMAT PAG format and saved to a temporary file.
     * The serialized graph is then reloaded, and the structure and properties of
     * the original graph are compared to the deserialized graph to ensure they are
     * equivalent.
     *
     * This test confirms that the serialization and deserialization process via
     * the AMAT PAG format correctly preserves the graph's structure, nodes, and
     * mixed edge types.
     *
     * @throws Exception if an error occurs during the graph save, load, or comparison process.
     */
    @Test
    public void threeNodeMixed_pagRoundTrip() throws Exception {
        // Example: X1 o-> X2, X2 <-> X3, X1 --- X3 (tail-tail)
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");

        Graph g = new EdgeListGraph(Arrays.asList(x1, x2, x3));
        g.addEdge(new Edge(x1, x2, Endpoint.CIRCLE, Endpoint.ARROW)); // X1 o-> X2
        g.addEdge(new Edge(x2, x3, Endpoint.ARROW, Endpoint.ARROW));  // X2 <-> X3
        g.addEdge(new Edge(x1, x3, Endpoint.TAIL, Endpoint.TAIL));    // X1 --- X3

        String amat = GraphSaveLoadUtils.graphToAmatPag(g);
        File f = writeToTempFile(amat, ".pag");
        Graph g2 = GraphSaveLoadUtils.loadGraphAmatPag(f);

        assertGraphsEqual(g, g2);
    }
}
