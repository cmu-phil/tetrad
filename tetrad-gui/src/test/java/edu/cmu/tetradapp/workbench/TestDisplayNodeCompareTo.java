package edu.cmu.tetradapp.workbench;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests that DisplayNode.compareTo orders possibly-lagged names sensibly: unlagged names first,
 * then increasing lag compared numerically, with natural ordering of base names within a lag
 * group. The former implementation compared lag suffixes and base names as raw strings, which
 * put "X:10" before "X:2" and "X10" before "X2"; this test fails on that implementation and
 * passes on the shared NaturalSort.lagAscendingComparator delegation.
 *
 * @author josephramsey
 */
public class TestDisplayNodeCompareTo {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static DisplayNode node(String name) {
        DisplayNode node = new DisplayNode();
        node.setName(name);
        return node;
    }

    private static List<String> sortedNames(String... names) {
        List<DisplayNode> nodes = new ArrayList<>();

        for (String name : names) {
            nodes.add(node(name));
        }

        Collections.sort(nodes);

        List<String> result = new ArrayList<>();

        for (DisplayNode node : nodes) {
            result.add(node.getName());
        }

        return result;
    }

    /**
     * Lag suffixes compare numerically, so lag 2 precedes lag 10.
     */
    @Test
    public void testLagsCompareNumerically() {
        assertEquals(Arrays.asList("X:1", "X:2", "X:10"),
                sortedNames("X:10", "X:2", "X:1"));
    }

    /**
     * Unlagged names precede lagged names, and base names order naturally within a lag group.
     */
    @Test
    public void testUnlaggedFirstAndNaturalBaseOrder() {
        assertEquals(Arrays.asList("X2", "X10", "Y", "X2:1", "X10:1"),
                sortedNames("X10:1", "X2:1", "X10", "X2", "Y"));
    }

    /**
     * A colon name whose suffix is not an integer sorts among the unlagged names, and comparison
     * never misreads its suffix as a lag.
     */
    @Test
    public void testNonLagColonNamesSortAsUnlagged() {
        assertEquals(Arrays.asList("apple", "price:usd", "X:1"),
                sortedNames("X:1", "price:usd", "apple"));
    }
}
