package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.NaturalSort;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Pins the ordering contract of {@link NaturalSort} for possibly-lagged variable names:
 * valid lagged names ({@code "name:lag"}, exactly one colon, integer suffix) group first
 * in order of decreasing lag, each group ordered by the natural order of the base name;
 * all other strings (no colon, multiple colons, non-integer suffix) come last, ordered
 * naturally among themselves. The comparator must be total on arbitrary strings and never
 * throw — the original implementation threw NumberFormatException from the AWT event
 * thread when handed a composite string containing colons (e.g. a list rendered to a
 * string), which this test pins as fixed.
 */
public class TestNaturalSort {

    /**
     * The regression that motivated the fix: a composite string whose first colon is
     * followed by a non-integer tail must not throw, and must be treated as an unlagged
     * name.
     */
    @Test
    public void testCompositeStringDoesNotThrow() {
        String composite = "[Fire, RH:1, RH:2, RH:3, RH:4, Rain:1, Rain:2, Rain:3, Rain:4, "
                + "Temperature:1, Temperature:2, Temperature:3, Temperature:4, "
                + "Ws:1, Ws:2, Ws:3, Ws:4, day:1, day:2, day:3, day:4, "
                + "month:1, month:2, month:3, month:4]";
        List<String> names = new ArrayList<>(Arrays.asList("X:2", composite, "X", "X:1"));
        names.sort(NaturalSort.NATURAL_NAME_COMPARATOR);   // must not throw
        // Lagged names first in decreasing lag; the composite is unlagged, so it sorts
        // with the unlagged group ("X" before "[..." since 'X' < '[' in ASCII).
        assertEquals(Arrays.asList("X:2", "X:1", "X", composite), names);
    }

    /**
     * The specified ordering: tiers grouped by decreasing lag, natural name order within a
     * tier, unlagged names last.
     */
    @Test
    public void testDecreasingLagGroupsThenUnlaggedLast() {
        List<String> names = new ArrayList<>(Arrays.asList(
                "Fire", "RH", "RH:1", "RH:2", "Temperature:1", "day:4", "month:2", "X2", "X10"));
        Collections.shuffle(names, new java.util.Random(42));
        names.sort(NaturalSort.NATURAL_NAME_COMPARATOR);
        assertEquals(Arrays.asList(
                "day:4",                    // lag 4
                "RH:2", "month:2",          // lag 2, natural name order
                "RH:1", "Temperature:1",    // lag 1
                "Fire", "RH", "X2", "X10"   // unlagged last, natural order
        ), names);
    }

    /**
     * Natural (not lexicographic) ordering of numeric name components, both within a lag
     * group and among unlagged names.
     */
    @Test
    public void testNaturalNumericOrderWithinGroups() {
        List<String> names = new ArrayList<>(Arrays.asList("X10:1", "X2:1", "X10", "X2"));
        names.sort(NaturalSort.NATURAL_NAME_COMPARATOR);
        assertEquals(Arrays.asList("X2:1", "X10:1", "X2", "X10"), names);
    }

    /**
     * Malformed lagged forms — non-integer suffix, multiple colons, empty suffix — are
     * treated as unlagged names, sorting after all valid lagged names.
     */
    @Test
    public void testMalformedLagFormsTreatedAsUnlagged() {
        List<String> names = new ArrayList<>(Arrays.asList("A:B", "A:1:2", "A:", "B:1"));
        names.sort(NaturalSort.NATURAL_NAME_COMPARATOR);
        assertEquals("B:1", names.get(0));   // the only valid lagged name comes first
        // the rest are unlagged; their relative order is the natural order of the whole
        // strings and just needs to be deterministic and exception-free
        assertEquals(4, names.size());
    }

    /**
     * "X:0" is a valid lagged name with lag 0: it sorts after deeper lags and before the
     * unlagged group.
     */
    @Test
    public void testLagZeroBetweenDeeperLagsAndUnlagged() {
        List<String> names = new ArrayList<>(Arrays.asList("X", "X:0", "X:1"));
        names.sort(NaturalSort.NATURAL_NAME_COMPARATOR);
        assertEquals(Arrays.asList("X:1", "X:0", "X"), names);
    }

    /**
     * Digit runs too long to parse as an int — in a lag suffix or in a plain name — must
     * not throw; such strings are treated as unlagged / suffix-free.
     */
    @Test
    public void testOverflowSuffixesDoNotThrow() {
        List<String> names = new ArrayList<>(Arrays.asList(
                "X:99999999999999999999", "X99999999999999999999", "X:1", "X"));
        names.sort(NaturalSort.NATURAL_NAME_COMPARATOR);   // must not throw
        assertEquals("X:1", names.get(0));                 // the only valid lagged name
        assertEquals(4, names.size());
    }

    /**
     * Compatibility pin: for inputs with no colons at all, the order is plain natural
     * order, unchanged by the lag-handling revision.
     */
    @Test
    public void testPlainNamesUnchanged() {
        List<String> names = new ArrayList<>(Arrays.asList("X10", "Fire", "X2", "RH", "X"));
        names.sort(NaturalSort.NATURAL_NAME_COMPARATOR);
        assertEquals(Arrays.asList("Fire", "RH", "X", "X2", "X10"), names);
    }
}
