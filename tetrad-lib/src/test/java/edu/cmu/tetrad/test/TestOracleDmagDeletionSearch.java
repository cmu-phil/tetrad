package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.OracleDmagDeletionSearch;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Focused oracle regression tests for the bounded DMAG deletion prototype. */
public class TestOracleDmagDeletionSearch {

    @Test
    public void testDirectDeletionAndInputUnchanged() throws InterruptedException {
        Graph truth = GraphUtils.convert("A-->B,B-->C");
        Graph dense = GraphUtils.convert("A-->B,B-->C,A-->C");
        Graph saved = dense.copy();
        var result = new OracleDmagDeletionSearch().findDeletion(dense, new MsepTest(truth));

        assertEquals(OracleDmagDeletionSearch.Status.FOUND, result.status());
        assertEquals(saved, dense);
        Graph reduced = result.step().orElseThrow().after();
        assertEquals(2, reduced.getNumEdges());
        assertEquals(3, reduced.getNumNodes());
        assertModelsNested(dense, reduced, truth);
    }

    @Test
    public void testComponentGuidedCrossingDeletion() throws InterruptedException {
        Graph truth = GraphUtils.convert("A-->B,C-->D");
        Graph dense = GraphUtils.convert("A-->B,B-->C,C-->D");
        var result = new OracleDmagDeletionSearch().findDeletion(dense, new MsepTest(truth));

        assertEquals(OracleDmagDeletionSearch.Status.FOUND, result.status());
        Edge removed = result.step().orElseThrow().removed();
        Set<String> ends = Set.of(removed.getNode1().getName(), removed.getNode2().getName());
        assertEquals(Set.of("B", "C"), ends);
        assertModelsNested(dense, result.step().orElseThrow().after(), truth);
    }

    @Test
    public void testMixedRepresentativeCanRepairDeletion() throws InterruptedException {
        Graph dense = GraphUtils.convert("X2-->X1,X2-->X0,X1-->X0,X3-->X0");
        Graph truth = GraphUtils.convert("X1-->X2,X2<->X0,X3-->X0");
        var result = new OracleDmagDeletionSearch(8, 2_000).findDeletion(dense, new MsepTest(truth));

        assertEquals(OracleDmagDeletionSearch.Status.FOUND, result.status());
        assertFalse("The deletion needs a different marked representative",
                dense.equals(result.step().orElseThrow().representative()));
        assertEquals(dense.getNumEdges() - 1, result.step().orElseThrow().after().getNumEdges());
        assertModelsNested(dense, result.step().orElseThrow().after(), truth);
    }

    @Test
    public void testNonImapStartRejected() throws InterruptedException {
        Graph dense = GraphUtils.convert("A<--B,B-->C");
        Graph truth = GraphUtils.convert("A-->B,C-->B");
        var result = new OracleDmagDeletionSearch().findDeletion(dense, new MsepTest(truth));
        assertEquals(OracleDmagDeletionSearch.Status.INVALID_START, result.status());
        assertTrue(result.step().isEmpty());
    }

    @Test
    public void testSearchLimitIsNotMistakenForProofOfNonexistence() throws InterruptedException {
        Graph truth = GraphUtils.convert("A-->B");
        var capped = new OracleDmagDeletionSearch(8, 1).findDeletion(truth, new MsepTest(truth));
        assertEquals(OracleDmagDeletionSearch.Status.LIMIT_REACHED, capped.status());
        assertTrue(capped.step().isEmpty());

        var full = new OracleDmagDeletionSearch(8, 10).findDeletion(truth, new MsepTest(truth));
        assertEquals(OracleDmagDeletionSearch.Status.NO_DELETION, full.status());
    }

    /** Independent full-signature check, not the deletion helper's certificate. */
    private static void assertModelsNested(Graph dense, Graph reduced, Graph truth) {
        List<Node> vertices = new ArrayList<>(dense.getNodes());
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                Node a = vertices.get(i);
                Node b = vertices.get(j);
                List<Node> others = new ArrayList<>(vertices);
                others.remove(b);
                others.remove(a);
                int subsets = 1 << others.size();
                for (int mask = 0; mask < subsets; mask++) {
                    Set<Node> z = new HashSet<>();
                    for (int k = 0; k < others.size(); k++) {
                        if ((mask & (1 << k)) != 0) z.add(others.get(k));
                    }
                    boolean gd = dense.paths().isMSeparatedFrom(a, b, z, false);
                    boolean kr = reduced.paths().isMSeparatedFrom(a, b, z, false);
                    Set<Node> zTruth = new HashSet<>();
                    for (Node member : z) zTruth.add(truth.getNode(member.getName()));
                    boolean ht = truth.paths().isMSeparatedFrom(
                            truth.getNode(a.getName()), truth.getNode(b.getName()), zTruth, false);
                    assertFalse("Lost an original independence", gd && !kr);
                    assertFalse("Added an independence absent from H", kr && !ht);
                }
            }
        }
    }
}
