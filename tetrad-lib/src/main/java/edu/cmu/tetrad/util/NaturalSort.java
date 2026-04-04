package edu.cmu.tetrad.util;

import java.util.Comparator;

/**
 * Provides functionality for natural sorting of strings and objects.
 * Natural order refers to sorting where numeric components in strings
 * are considered, for example, sorting "X1", "X2", and "X10" in that order
 * rather than lexicographically as "X1", "X10", "X2".
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
     * rather than "X1", "X10", "X2").
     */
    public static final Comparator<String> NATURAL_NAME_COMPARATOR =
            Comparator.comparing(NaturalKey::from);

    /**
     * Returns a comparator that sorts objects of any type in natural order,
     * using each object's {@link Object#toString()} representation as the sort key.
     *
     * @param <T> the type of objects to compare
     * @return a natural-order comparator for T
     */
    public static <T> Comparator<T> naturalComparator() {
        return Comparator.comparing(t -> NaturalKey.from(t.toString()));
    }

    /**
     * Represents a natural key, which consists of a string prefix and an optional numeric suffix.
     * This class facilitates natural ordering of strings, combining lexicographical sorting
     * of the prefix with numerical comparison of the suffix when present.
     *
     * Instances of this class are immutable.
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
         * @return a {@code NaturalKey} constructed from the given string's prefix and suffix
         * @throws NumberFormatException if the numeric suffix cannot be parsed as an {@code Integer}
         */
        public static NaturalKey from(String s) {
            int i = s.length();
            while (i > 0 && Character.isDigit(s.charAt(i - 1))) {
                i--;
            }

            String prefix = s.substring(0, i);
            Integer suffix = (i < s.length())
                    ? Integer.parseInt(s.substring(i))
                    : null;

            return new NaturalKey(prefix, suffix);
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