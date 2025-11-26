package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Utility class containing test helper methods for graph comparison, temporary file writing, and adjacency matrix
 * parsing. These methods are primarily intended for use in unit tests involving graph serialization, deserialization,
 * and format-specific operations.
 */
public class GraphFormatTestUtils {

    /**
     * Asserts that two graphs are equal by comparing their nodes and edges.
     *
     * @param g1 the first graph to compare
     * @param g2 the second graph to compare
     */
    static void assertGraphsEqual(Graph g1, Graph g2) {
        // Compare nodes by name
        assertEquals(
                new HashSet<>(g1.getNodes()),
                new HashSet<>(g2.getNodes())
        );
        // Compare edges including endpoints
        assertEquals(
                new HashSet<>(g1.getEdges()),
                new HashSet<>(g2.getEdges())
        );
    }

    /**
     * Writes the given contents to a temporary file with the specified suffix. The file is created with a default
     * prefix and is automatically set to be deleted on JVM termination.
     *
     * @param contents the string content to be written to the temporary file
     * @param suffix   the suffix to append to the temporary file name (e.g., ".txt")
     * @return the temporary file to which the contents were written
     * @throws Exception if an error occurs during file creation or writing
     */
    static File writeToTempFile(String contents, String suffix) throws Exception {
        File f = File.createTempFile("amat_test_", suffix);
        Files.writeString(f.toPath(), contents, StandardCharsets.UTF_8);
        f.deleteOnExit();
        return f;
    }

    /**
     * Parses an adjacency matrix represented as a string into a 2D integer array. The input string is expected to
     * include a header row and quoted row names followed by the matrix entries. The size of the matrix is determined by
     * the number of variables in the provided list.
     *
     * @param s    the string representation of the adjacency matrix
     * @param vars the list of nodes corresponding to the matrix rows and columns
     * @return a 2D integer array representing the adjacency matrix
     */
    static int[][] parseAmatFromString(String s, List<Node> vars) {
        String[] lines = s.split("\\R");
        int p = vars.size();
        int[][] m = new int[p][p];

        // skip header (line 0)
        for (int i = 0; i < p; i++) {
            String line = lines[i + 1].trim();
            String[] toks = line.split("[ \t]+");
            // toks[0] is the quoted row name; entries start at index 1
            for (int j = 0; j < p; j++) {
                m[i][j] = Integer.parseInt(toks[j + 1]);
            }
        }

        return m;
    }
}