///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.rlcd;

import java.util.*;

/**
 * Static set operations over {@link Cover}s, translating the module-level helpers in <code>Cover.py</code> and
 * <code>misc.py</code> of the causal-learn RLCD implementation. Where the Python relies on set iteration order
 * (which is hash-randomized per process), these methods impose a deterministic order by sorting on
 * {@link Cover#key()}.
 *
 * @author josephramsey (translation)
 */
public final class CoverSets {

    /**
     * Orders covers by their sorted variable names.
     */
    public static final Comparator<Cover> BY_KEY = Comparator.comparing(Cover::key);

    private CoverSets() {
    }

    /**
     * All variable names appearing in any of the covers (Python <code>getVars</code>).
     *
     * @param covers the covers.
     * @return the union of their variable sets, sorted.
     */
    public static SortedSet<String> vars(Collection<Cover> covers) {
        SortedSet<String> out = new TreeSet<>();
        for (Cover c : covers) out.addAll(c.getVars());
        return out;
    }

    /**
     * The number of distinct variables covered (Python <code>setLength</code>, written ||Vs||).
     *
     * @param covers the covers.
     * @return the count.
     */
    public static int setLength(Collection<Cover> covers) {
        return vars(covers).size();
    }

    /**
     * Variable-level intersection of two sets of covers (Python <code>setIntersection</code>).
     *
     * @param as first set of covers.
     * @param bs second set of covers.
     * @return the variable names that appear in both.
     */
    public static SortedSet<String> setIntersection(Collection<Cover> as, Collection<Cover> bs) {
        SortedSet<String> out = vars(as);
        out.retainAll(vars(bs));
        return out;
    }

    /**
     * Set difference that also drops any cover in <code>as</code> that shares a variable with some cover in
     * <code>bs</code> (Python <code>setDifference</code>).
     *
     * @param as the covers to keep from.
     * @param bs the covers to remove.
     * @return a new set, in the iteration order of <code>as</code>.
     */
    public static Set<Cover> setDifference(Collection<Cover> as, Collection<Cover> bs) {
        Set<Cover> out = new LinkedHashSet<>();
        for (Cover a : as) {
            if (bs.contains(a)) continue;
            boolean overlaps = false;
            for (Cover b : bs) {
                if (a.intersects(b)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) out.add(a);
        }
        return out;
    }

    /**
     * Removes any cover whose variables are a strict subset of another cover's variables (Python
     * <code>deduplicate</code>): {L1, {L1, L3}} becomes {{L1, L3}}.
     *
     * @param covers the covers.
     * @return a new set, in the iteration order of the input.
     */
    public static Set<Cover> deduplicate(Collection<Cover> covers) {
        Set<Cover> out = new LinkedHashSet<>();
        for (Cover vi : covers) {
            boolean dup = false;
            for (Cover vj : covers) {
                if (vi.isStrictSubsetOf(vj)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(vi);
        }
        return out;
    }

    /**
     * The variable names of the covers, sorted and space-joined (Python <code>getOrderedVarsString</code>).
     *
     * @param covers the covers.
     * @return the string.
     */
    public static String orderedVarsString(Collection<Cover> covers) {
        return String.join(" ", vars(covers));
    }

    /**
     * Sorts a set of covers deterministically.
     *
     * @param covers the covers.
     * @return a new list sorted by {@link #BY_KEY}.
     */
    public static List<Cover> sorted(Collection<Cover> covers) {
        List<Cover> out = new ArrayList<>(covers);
        out.sort(BY_KEY);
        return out;
    }

    /**
     * Generates all minimal subsets of the given covers whose variable cardinality exceeds <code>k</code>, where a
     * subset is minimal if dropping its smallest-cardinality member brings the cardinality to at most <code>k</code>.
     * Covers sharing a variable are never placed in the same subset. This is <code>generateSubsetMinimal</code> from
     * <code>misc.py</code>: covers are processed in descending order of cardinality, and at each cover the search
     * branches on excluding or including it, emitting the current subset as soon as its cardinality exceeds
     * <code>k</code>.
     * <p>
     * The Python generator can emit duplicate (equal) subsets when covers overlap; those are removed here. The result
     * is returned in a deterministic order.
     *
     * @param covers the covers to draw from.
     * @param k      the cardinality threshold; subsets must have cardinality greater than k.
     * @return the list of subsets.
     */
    public static List<Set<Cover>> generateSubsetMinimal(Collection<Cover> covers, int k) {
        List<Cover> items = new ArrayList<>(covers);
        // Descending cardinality first (the Python pops the largest dimension first); ties broken by key so that
        // the enumeration is reproducible.
        items.sort(Comparator.comparingInt(Cover::size).reversed().thenComparing(BY_KEY));
        LinkedHashSet<Set<Cover>> out = new LinkedHashSet<>();
        if (!items.isEmpty()) {
            recursiveSearch(items, 0, k, new LinkedHashSet<>(), out);
        }
        List<Set<Cover>> result = new ArrayList<>(out);
        result.sort(Comparator.comparing(CoverSets::orderedVarsString));
        return result;
    }

    private static void recursiveSearch(List<Cover> items, int idx, int gap, LinkedHashSet<Cover> current,
                                        LinkedHashSet<Set<Cover>> out) {
        if (idx >= items.size()) return;
        Cover v = items.get(idx);
        boolean moreAfter = idx + 1 < items.size();

        // Branch 1: continue without this cover.
        if (moreAfter) recursiveSearch(items, idx + 1, gap, current, out);

        // Branch 2: add this cover (unless it overlaps the current subset) and continue while the gap is not met.
        LinkedHashSet<Cover> withV = new LinkedHashSet<>(current);
        int gap2 = gap;
        if (!groupInLatentSet(v, current)) {
            withV.add(v);
            gap2 -= v.size();
        }
        if (gap2 >= 0 && moreAfter) recursiveSearch(items, idx + 1, gap2, withV, out);
        if (gap2 < 0) out.add(Collections.unmodifiableSet(new LinkedHashSet<>(withV)));
    }

    /**
     * Whether the cover shares a variable with any cover in the set (Python <code>groupInLatentSet</code>).
     *
     * @param v      the cover.
     * @param covers the set.
     * @return the answer.
     */
    public static boolean groupInLatentSet(Cover v, Collection<Cover> covers) {
        for (Cover c : covers) {
            if (v.intersects(c)) return true;
        }
        return false;
    }

    /**
     * All <code>r</code>-combinations of the list, in lexicographic index order (as <code>itertools.combinations</code>).
     *
     * @param items the items.
     * @param r     the combination size.
     * @param <T>   the item type.
     * @return the combinations; a single empty combination when r = 0.
     */
    public static <T> List<List<T>> combinations(List<T> items, int r) {
        List<List<T>> out = new ArrayList<>();
        int n = items.size();
        if (r < 0 || r > n) return out;
        int[] idx = new int[r];
        for (int i = 0; i < r; i++) idx[i] = i;
        while (true) {
            List<T> combo = new ArrayList<>(r);
            for (int i : idx) combo.add(items.get(i));
            out.add(combo);
            int i = r - 1;
            while (i >= 0 && idx[i] == n - r + i) i--;
            if (i < 0) break;
            idx[i]++;
            for (int j = i + 1; j < r; j++) idx[j] = idx[j - 1] + 1;
        }
        return out;
    }

    /**
     * The powerset of the list in the order produced by the Python recipe: by subset size, empty set first, then
     * all singletons, and so on up to the full set (Python <code>powerset</code> in <code>misc.py</code>).
     *
     * @param items the items.
     * @param <T>   the item type.
     * @return the subsets.
     */
    public static <T> List<List<T>> powerset(List<T> items) {
        List<List<T>> out = new ArrayList<>();
        for (int r = 0; r <= items.size(); r++) out.addAll(combinations(items, r));
        return out;
    }
}
