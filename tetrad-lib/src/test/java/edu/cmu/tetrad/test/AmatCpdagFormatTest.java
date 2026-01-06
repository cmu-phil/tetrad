package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * A test class for evaluating the "Amat-Cpdag" graph format functionality.
 * This class extends {@code GraphFormatTestUtils} and contains various test cases
 * to validate graph serialization and deserialization, as well as adherence
 * to conventions such as matrix format for CPDAG (Completed Partially Directed
 * Acyclic Graph).
 *
 * The tests involve:
 * - Verifying correct round-trip conversion (serialization and deserialization)
 *   of graphs with directed, undirected, and mixed edges in the Amat-Cpdag format.
 * - Ensuring the correctness of CPDAG matrix representations and their compliance
 *   with conventions like PCALG row-to-head-right-to-tail.
 * - Analyzing small example graphs to verify expected behaviors for edge types
 *   and directionality.
 *
 * The primary methods tested in this class work with the Amat-Cpdag format,
 * which represents adjacency matrices associated with CPDAGs.
 */
public class AmatCpdagFormatTest extends GraphFormatTestUtils {

    @Test
    public void twoNodeDirected_roundTrip() throws Exception {
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph g = new EdgeListGraph(Arrays.asList(x1, x2));
        g.addDirectedEdge(x1, x2);   // X1 -> X2

        String amat = GraphSaveLoadUtils.graphToAmatCpdag(g);
        File f = writeToTempFile(amat, ".cpdag");
        Graph g2 = GraphSaveLoadUtils.loadGraphAmatCpdag(f);

        assertGraphsEqual(g, g2);
    }

    /**c
     * Tests the round-trip conversion process for a simple undirected graph
     * consisting of two nodes, using the adjacency matrix (AMAT) representation
     * for CPDAG format.
     *
     * This method performs the following steps:
     * 1. Creates a graph with two nodes ("X1" and "X2") and an undirected edge
     *    between them.
     * 2. Converts the graph to an AMAT CPDAG text representation.
     * 3. Writes the AMAT CPDAG representation to a temporary file.
     * 4. Loads the graph back from the AMAT CPDAG file.
     * 5. Asserts that the original graph and the graph loaded from the file are
     *    equal, verifying the integrity of the round-trip process.
     *
     * @throws Exception if there is an error during the conversion process,
     *                   file I/O operations, or graph comparison.
     */
    @Test
    public void twoNodeUndirected_roundTrip() throws Exception {
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph g = new EdgeListGraph(Arrays.asList(x1, x2));
        g.addUndirectedEdge(x1, x2); // X1 -- X2

        String amat = GraphSaveLoadUtils.graphToAmatCpdag(g);
        File f = writeToTempFile(amat, ".cpdag");
        Graph g2 = GraphSaveLoadUtils.loadGraphAmatCpdag(f);

        assertGraphsEqual(g, g2);
    }

    /**
     * Tests whether the adjacency matrix (AMAT) representation of a two-node directed graph
     * matches the CPDAG (Completed Partially Directed Acyclic Graph) convention used by the pcalg library.
     *
     * This method verifies the following:
     * 1. Constructs a directed graph with two nodes ("X1" and "X2") where X1 -> X2.
     * 2. Converts the graph into a CPDAG adjacency matrix format (amat) string.
     * 3. Parses the adjacency matrix string back into a 2D integer array using the given node list.
     * 4. Checks the CPDAG convention, which specifies that the row index carries the arrowhead.
     *    - For edge X1 -> X2, m[0][1] (directed from X1 to X2) is expected to be 0.
     *    - Conversely, m[1][0] (reflecting the reverse relationship) is expected to be 1.
     *
     * Asserts:
     * - The adjacency matrix representation follows the pcalg convention for directed edges.
     */
    @Test
    public void cpdagMatrixPattern_matchesPcalgConvention() {
        // X1 -> X2
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph g = new EdgeListGraph(Arrays.asList(x1, x2));
        g.addDirectedEdge(x1, x2);

        String amat = GraphSaveLoadUtils.graphToAmatCpdag(g);
        int[][] m = parseAmatFromString(amat, Arrays.asList(x1, x2));

        // pcalg: row index carries the arrowhead
        // X1 -> X2   => m[0][1] = 0, m[1][0] = 1
        assertEquals(0, m[0][1]);
        assertEquals(1, m[1][0]);
    }

    /**
     * Tests the round-trip conversion process of a three-node mixed graph
     * (containing both directed and undirected edges) using the adjacency
     * matrix (AMAT) representation in CPDAG format.
     *
     * This method validates the integrity of the graph representation by performing
     * the following steps:
     * 1. Creates a graph with three nodes ("X1", "X2", "X3") with the following edges:
     *    - A directed edge from X1 to X2.
     *    - A directed edge from X2 to X3.
     *    - An undirected edge between X1 and X3.
     * 2. Converts the graph into an AMAT CPDAG string representation.
     * 3. Writes the string representation to a temporary file in CPDAG format.
     * 4. Loads a graph object by reading the file and re-parsing the AMAT representation.
     * 5. Asserts that the original graph and the loaded graph are equivalent to ensure
     *    that the round-trip conversion preserves the structure and properties of the graph.
     *
     * @throws Exception if there are issues during graph creation, file I/O operations,
     *                   adjacency matrix parsing, or graph equivalence validation.
     */
    @Test
    public void threeNodeMixed_roundTrip() throws Exception {
        // X1 -> X2 -> X3, and X1 -- X3
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");

        Graph g = new EdgeListGraph(Arrays.asList(x1, x2, x3));
        g.addDirectedEdge(x1, x2);
        g.addDirectedEdge(x2, x3);
        g.addUndirectedEdge(x1, x3);

        String amat = GraphSaveLoadUtils.graphToAmatCpdag(g);
        File f = writeToTempFile(amat, ".cpdag");
        Graph g2 = GraphSaveLoadUtils.loadGraphAmatCpdag(f);

        assertGraphsEqual(g, g2);
    }
}
