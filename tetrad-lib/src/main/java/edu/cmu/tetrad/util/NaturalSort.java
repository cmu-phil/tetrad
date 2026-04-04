package edu.cmu.tetrad.util;

import java.util.Comparator;

public class NaturalSort {

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

    public static final class NaturalKey implements Comparable<NaturalKey> {
        final String prefix;
        final Integer suffix;   // null if no numeric suffix

        private NaturalKey(String prefix, Integer suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

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