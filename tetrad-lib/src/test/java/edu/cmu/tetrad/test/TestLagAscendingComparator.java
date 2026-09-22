package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.NaturalSort;
import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the shared display comparator for possibly-lagged names: unlagged names first, then
 * increasing lag, with natural ordering of base names within a lag group, and a raw-string
 * tiebreaker so distinct names never compare equal. This is the order used by GUI variable
 * lists; it replaces ad hoc comparators that compared lag suffixes and base names as raw
 * strings.
 *
 * <p>This test does not compile against jars predating lagAscendingComparator.
 *
 * @author josephramsey
 */
public class TestLagAscendingComparator {

    /**
     * Lags compare numerically, so lag 2 precedes lag 10. The comparators this replaces compared
     * the suffixes as strings, putting "X:10" before "X:2".
     */
    @Test
    public void testLagsCompareNumerically() {
        List<String> names = Arrays.asList("X:10", "X:2", "X:1");
        names.sort(NaturalSort.lagAscendingComparator());
        assertEquals(Arrays.asList("X:1", "X:2", "X:10"), names);
    }

    /**
     * Unlagged names precede all lagged names, and base names order naturally within a lag group,
     * so X2 precedes X10.
     */
    @Test
    public void testUnlaggedFirstAndNaturalBaseOrder() {
        List<String> names = Arrays.asList("X10:1", "X2:1", "X10", "X2", "Y", "X2:2");
        names.sort(NaturalSort.lagAscendingComparator());
        assertEquals(Arrays.asList("X2", "X10", "Y", "X2:1", "X10:1", "X2:2"), names);
    }

    /**
     * A colon name whose suffix is not an integer is not a lagged name; it sorts among the
     * unlagged names, and comparison never throws.
     */
    @Test
    public void testNonLagColonNamesSortAsUnlagged() {
        List<String> names = Arrays.asList("X:1", "price:usd", "apple");
        names.sort(NaturalSort.lagAscendingComparator());
        assertEquals(Arrays.asList("apple", "price:usd", "X:1"), names);
    }

    /**
     * Distinct names never compare equal, and equal names compare equal, so the comparator is
     * safe for ordered sets and stable displays.
     */
    @Test
    public void testDistinctNamesNeverTie() {
        Comparator<String> comparator = NaturalSort.lagAscendingComparator();
        List<String> names = Arrays.asList("X", "X:1", "X:01", "X2", "X02", "price:usd");

        for (String a : names) {
            for (String b : names) {
                if (a.equals(b)) {
                    assertEquals(0, comparator.compare(a, b));
                } else {
                    assertTrue("Distinct names should not tie: " + a + " vs " + b,
                            comparator.compare(a, b) != 0);
                    assertTrue("Comparator should be antisymmetric: " + a + " vs " + b,
                            Integer.signum(comparator.compare(a, b))
                                    == -Integer.signum(comparator.compare(b, a)));
                }
            }
        }
    }
}
