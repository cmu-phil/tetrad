package edu.cmu.tetrad.util;

import java.util.Comparator;

/**
 * Provides functionality for natural sorting of strings and objects.
 * Natural order refers to sorting where numeric components in strings
 * are considered, for example, sorting "X1", "X2", and "X10" in that order
 * rather than lexicographically as "X1", "X10", "X2".
 *
 * <p>For strings containing a ":" separator (e.g. "X1:2"), the portion after
 * the colon is treated as a lag index. Such strings are sorted first by lag,
 * then by the natural order of the node name before the colon.
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
     * rather than "X1", "X10", "X2"). For strings containing a ":" separator,
     * sorts first by lag (the part after ":"), then by natural order of the node
     * name (the part before ":").
     */
    public static final Comparator<String> NATURAL_NAME_COMPARATOR =
            Comparator.comparing(LaggedNaturalKey::from);

    /**
     * Returns a comparator that sorts objects of any type in natural order,
     * using each object's {@link Object#toString()} representation as the sort key.
     * For strings containing a ":" separator, sorts first by lag, then by natural
     * order of the node name.
     *
     * @param <T> the type of objects to compare
     * @return a natural-order comparator for T
     */
    public static <T> Comparator<T> naturalComparator() {
        return Comparator.comparing(t -> LaggedNaturalKey.from(t.toString()));
    }

    /**
     * Represents a natural key for a possibly-lagged node name of the form
     * {@code "name"} or {@code "name:lag"}. Sorting is performed first by lag
     * (ascending, with no-lag nodes sorted before lagged nodes), then by the
     * natural order of the node name.
     *
     * <p>Instances of this class are immutable.
     */
    public static final class LaggedNaturalKey implements Comparable<LaggedNaturalKey> {
        final NaturalKey name;  // natural key of the part before ":"
        final int lag;          // 0 if no ":" present, otherwise the integer after ":"

        private LaggedNaturalKey(NaturalKey name, int lag) {
            this.name = name;
            this.lag = lag;
        }

        /**
         * Creates a {@code LaggedNaturalKey} from the given string. If the string
         * contains a ":" character, the portion before it is used as the node name
         * and the portion after it is parsed as the lag index. Otherwise, the whole
         * string is the node name and the lag defaults to 0.
         *
         * @param s the input string to be parsed
         * @return a {@code LaggedNaturalKey} constructed from the given string
         * @throws NumberFormatException if the lag portion cannot be parsed as an {@code int}
         */
        public static LaggedNaturalKey from(String s) {
            int colon = s.indexOf(':');
            if (colon >= 0) {
                NaturalKey name = NaturalKey.from(s.substring(0, colon));
                int lag = Integer.parseInt(s.substring(colon + 1));
                return new LaggedNaturalKey(name, lag);
            } else {
                return new LaggedNaturalKey(NaturalKey.from(s), 0);
            }
        }

        @Override
        public int compareTo(LaggedNaturalKey o) {
            int c = Integer.compare(this.lag, o.lag);
            if (c != 0) return c;
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