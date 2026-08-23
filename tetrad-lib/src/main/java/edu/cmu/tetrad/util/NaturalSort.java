package edu.cmu.tetrad.util;

import java.util.Comparator;

/**
 * Provides functionality for natural sorting of strings and objects.
 * Natural order refers to sorting where numeric components in strings
 * are considered, for example, sorting "X1", "X2", and "X10" in that order
 * rather than lexicographically as "X1", "X10", "X2".
 *
 * <p>Lagged names. A string of the form {@code "name:lag"} — exactly one colon,
 * with the portion after the colon parseable as an integer — is treated as a
 * lagged node name. Lagged names are grouped in order of <em>decreasing</em> lag
 * (deepest lag first), and within a lag group are ordered by the natural order
 * of the name before the colon. All strings that are not valid lagged names
 * (no colon, more than one colon, or a non-integer suffix) come <em>last</em>,
 * ordered naturally among themselves. This matches the layout convention for
 * time-lagged data, where the deepest lag slice is displayed first and the
 * contemporaneous (unlagged) variables are displayed last.
 *
 * <p>The comparators here are total on arbitrary strings and never throw:
 * malformed inputs are simply treated as unlagged names.
 */
public class NaturalSort {

    /**
     * Private constructor to prevent instantiation of the NaturalSort class.
     * This class is intended to be used as a utility class containing static methods
     * and constants for natural sorting.
     */
    private NaturalSort() {}

    /**
     * A comparator that sorts strings in natural order (e.g. "X1", "X2", "X10"
     * rather than "X1", "X10", "X2"). Valid lagged names ({@code "name:lag"})
     * are grouped first, in order of decreasing lag, each group ordered by the
     * natural order of the base name; all other strings come last, in natural
     * order. Never throws on malformed input.
     */
    public static final Comparator<String> NATURAL_NAME_COMPARATOR =
            Comparator.comparing(LaggedNaturalKey::from);

    /**
     * Returns a comparator that sorts objects of any type in natural order,
     * using each object's {@link Object#toString()} representation as the sort key.
     * Valid lagged names ({@code "name:lag"}) group first in order of decreasing
     * lag, then natural order of the base name; everything else comes last in
     * natural order. Never throws on malformed input.
     *
     * @param <T> the type of objects to compare
     * @return a natural-order comparator for T
     */
    public static <T> Comparator<T> naturalComparator() {
        return Comparator.comparing(t -> LaggedNaturalKey.from(t.toString()));
    }

    /**
     * Represents a natural key for a possibly-lagged node name of the form
     * {@code "name"} or {@code "name:lag"}. Valid lagged names sort before all
     * unlagged names, grouped in order of decreasing lag; within a lag group,
     * and among the unlagged names, ordering is by the natural order of the
     * name. A string is a valid lagged name only if it contains exactly one
     * colon and the portion after the colon parses as an integer; anything
     * else — including strings with multiple colons or non-integer suffixes —
     * is treated as an unlagged name, so construction never throws.
     *
     * <p>Instances of this class are immutable.
     */
    public static final class LaggedNaturalKey implements Comparable<LaggedNaturalKey> {
        final NaturalKey name;   // natural key of the part before ":" (or the whole string)
        final boolean lagged;    // true iff the string is a valid "name:lag" form
        final int lag;           // the integer after ":" when lagged; unused otherwise

        private LaggedNaturalKey(NaturalKey name, boolean lagged, int lag) {
            this.name = name;
            this.lagged = lagged;
            this.lag = lag;
        }

        /**
         * Creates a {@code LaggedNaturalKey} from the given string. If the string
         * contains exactly one ":" whose suffix parses as an integer, the portion
         * before the colon is the node name and the suffix is the lag index.
         * Otherwise the whole string is treated as an unlagged node name. Note
         * that a suffix containing a further colon (e.g. {@code "A:1:2"}) fails
         * the integer parse, so multi-colon strings are treated as unlagged.
         *
         * @param s the input string to be parsed
         * @return a {@code LaggedNaturalKey} constructed from the given string
         */
        public static LaggedNaturalKey from(String s) {
            int colon = s.indexOf(':');
            if (colon >= 0) {
                try {
                    int lag = Integer.parseInt(s.substring(colon + 1));
                    return new LaggedNaturalKey(NaturalKey.from(s.substring(0, colon)), true, lag);
                } catch (NumberFormatException e) {
                    // fall through: not a valid lagged name
                }
            }
            return new LaggedNaturalKey(NaturalKey.from(s), false, 0);
        }

        @Override
        public int compareTo(LaggedNaturalKey o) {
            if (this.lagged != o.lagged) {
                return this.lagged ? -1 : 1;   // lagged names first; unlagged last
            }
            if (this.lagged) {
                int c = Integer.compare(o.lag, this.lag);   // decreasing lag
                if (c != 0) return c;
            }
            return this.name.compareTo(o.name);
        }
    }

    /**
     * Represents a natural key, which consists of a string prefix and an optional numeric suffix.
     * This class facilitates natural ordering of strings, combining lexicographical sorting
     * of the prefix with numerical comparison of the suffix when present.
     *
     * <p>Instances of this class are immutable.
     */
    public static final class NaturalKey implements Comparable<NaturalKey> {
        final String prefix;
        final Integer suffix;   // null if no numeric suffix

        private NaturalKey(String prefix, Integer suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        /**
         * Creates a {@code NaturalKey} from the given string. The input string is divided into a
         * prefix consisting of non-numeric characters and an optional numeric suffix.
         * If the string ends with numeric characters, they are extracted as the suffix;
         * otherwise, the suffix is {@code null}.
         *
         * @param s the input string to be parsed into a {@code NaturalKey}
         * @return a {@code NaturalKey} constructed from the given string's prefix and suffix;
         *         if the trailing digit run is too long to parse as an {@code int}, the whole
         *         string is used as the prefix (never throws)
         */
        public static NaturalKey from(String s) {
            int i = s.length();
            while (i > 0 && Character.isDigit(s.charAt(i - 1))) {
                i--;
            }

            if (i < s.length()) {
                try {
                    return new NaturalKey(s.substring(0, i), Integer.parseInt(s.substring(i)));
                } catch (NumberFormatException e) {
                    // Digit run too long for an int; treat the whole string as the prefix.
                }
            }

            return new NaturalKey(s, null);
        }

        @Override
        public int compareTo(NaturalKey o) {
            int c = this.prefix.compareTo(o.prefix);
            if (c != 0) return c;

            if (this.suffix == null && o.suffix == null) return 0;
            if (this.suffix == null) return -1;  // "X" before "X1"
            if (o.suffix == null) return 1;

            return Integer.compare(this.suffix, o.suffix);
        }
    }
}