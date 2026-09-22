package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the default Node.compareTo, which now delegates to the shared display order for
 * possibly-lagged names: unlagged names first, then increasing lag, natural base-name order
 * within a lag group, and the raw name as a final tiebreaker. The former bucketed comparison
 * ordered names by concatenating all their digits, under which distinct lagged names could
 * compare equal, and crashed outright on digit runs too long for an int.
 *
 * <p>Each failure mode tested here is exhibited by the former implementation, so this test
 * fails on the unpatched build and passes on the patched one.
 *
 * @author josephramsey
 */
public class TestNodeCompareTo {

    private static List<String> sortedNames(String... names) {
        List<Node> nodes = new ArrayList<>();

        for (String name : names) {
            nodes.add(new GraphNode(name));
        }

        Collections.sort(nodes);

        List<String> result = new ArrayList<>();

        for (Node node : nodes) {
            result.add(node.getName());
        }

        return result;
    }

    /**
     * Distinct lagged names never compare equal. Formerly "X12:3" and "X1:23" both reduced to
     * the digit string "123" and compared equal, which corrupts ordered collections.
     */
    @Test
    public void testDistinctLagNamesNeverTie() {
        Node a = new GraphNode("X12:3");
        Node b = new GraphNode("X1:23");

        assertTrue("Distinct names X12:3 and X1:23 must not compare equal.",
                a.compareTo(b) != 0);
        assertEquals("Comparison must be antisymmetric.",
                -Integer.signum(a.compareTo(b)), Integer.signum(b.compareTo(a)));
    }

    /**
     * Names with digit runs too long for an int sort without throwing. Formerly
     * Integer.valueOf on the concatenated digits threw NumberFormatException.
     */
    @Test
    public void testLongDigitRunsDoNotThrow() {
        List<String> sorted = sortedNames("N12345678901", "N2", "N1");

        assertEquals(3, sorted.size());
        assertTrue(sorted.contains("N12345678901"));
    }

    /**
     * Lagged names group by lag, ascending, with natural base-name order within a group, after
     * all unlagged names. Formerly lagged names ordered by their concatenated digits, so "X1:2"
     * (digits 12) preceded "X2:1" (digits 21) despite having the deeper lag.
     */
    @Test
    public void testLagGroupsOrderByLagThenBase() {
        assertEquals(Arrays.asList("X", "X2:1", "Y:1", "X1:2"),
                sortedNames("X1:2", "X2:1", "X", "Y:1"));
    }

    /**
     * The common cases keep their familiar order: pure-alpha and indexed names sort naturally
     * ("V2" before "V10") and precede all lagged names.
     */
    @Test
    public void testCommonCasesKeepNaturalOrder() {
        assertEquals(Arrays.asList("V2", "V10", "W", "V:1"),
                sortedNames("V10", "V2", "V:1", "W"));
    }
}
