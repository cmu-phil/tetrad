package edu.cmu.tetrad.search.cdnod_pag;

import java.util.*;

/**
 * Iterates all subsets S ⊆ items with |S| ≤ maxSize (returns insertion-ordered sets).
 */
final class SmallSubsetIter<T> implements Iterable<Set<T>> {
    /**
     * A list of items from which subsets will be generated. This is an immutable collection that holds the elements for
     * creating subsets with a specified maximum size.
     */
    private final List<T> items;
    /**
     * The maximum size of subsets to generate. Represents an upper limit on the number of elements in any subset
     * created by the {@code SmallSubsetIter} class. This value must be non-negative and is utilized during the
     * initialization of the subset iterator.
     */
    private final int maxSize;

    /**
     * Constructs an iterator for all subsets S ⊆ items with |S| ≤ maxSize.
     *
     * @param items   the collection of items to generate subsets from
     * @param maxSize the maximum size of subsets to generate; must be non-negative
     */
    SmallSubsetIter(Collection<T> items, int maxSize) {
        this.items = new ArrayList<>(items);
        this.maxSize = Math.max(0, maxSize);
    }

    /**
     * Generates all subsets of a given collection, where the size of each subset is less than or equal to the specified
     * maximum size.
     *
     * @param <T>     the type of elements in the collection
     * @param items   the collection of elements to generate subsets from
     * @param maxSize the maximum size of subsets to generate; must be non-negative
     * @return an iterable over all subsets satisfying the given constraints
     * @throws IllegalArgumentException if maxSize is negative
     */
    static <T> Iterable<Set<T>> subsets(Collection<T> items, int maxSize) {
        return new SmallSubsetIter<>(items, maxSize);
    }

    /**
     * Returns an iterator over all subsets of the collection with a size less than or equal to the maximum specified
     * size. The subsets are generated in ascending order of size, starting with the empty set and progressing through
     * increasing subset sizes.
     *
     * @return an iterator that provides subsets of the collection meeting the specified constraints
     */
    @Override
    public Iterator<Set<T>> iterator() {
        return new Iterator<>() {
            private final int n = items.size();
            private boolean firstReturned = false; // for ∅
            private int k = 0;                     // current subset size
            private int[] comb = (n == 0 ? null : new int[0]);

            @Override
            public boolean hasNext() {
                if (!firstReturned) return true;        // ∅ not returned yet
                if (comb == null) return false;         // exhausted
                if (k == 0) {                           // we just returned ∅
                    if (maxSize < 1 || n == 0) {
                        comb = null;
                        return false;
                    }
                    k = 1;
                    comb = initComb(k);
                    return true;
                }
                if (nextComb()) return true;            // next k-combination
                k++;
                if (k > maxSize || k > n) {
                    comb = null;
                    return false;
                }
                comb = initComb(k);
                return true;
            }

            @Override
            public Set<T> next() {
                // DO NOT call hasNext() here — the for-each loop already did.
                if (!firstReturned) {
                    firstReturned = true;
                    return Collections.emptySet();
                }
                if (comb == null) throw new NoSuchElementException();
                Set<T> s = new LinkedHashSet<>();
                for (int idx : comb) s.add(items.get(idx));
                return s;
            }

            private int[] initComb(int k) {
                int[] c = new int[k];
                for (int i = 0; i < k; i++) c[i] = i;
                return c;
            }

            private boolean nextComb() {
                int i = k - 1;
                while (i >= 0 && comb[i] == n - k + i) i--;
                if (i < 0) return false;
                comb[i]++;
                for (int j = i + 1; j < k; j++) comb[j] = comb[j - 1] + 1;
                return true;
            }
        };
    }
}