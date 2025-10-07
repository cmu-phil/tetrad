package edu.cmu.tetrad.search.cdnod_pag;

import java.util.*;

/** Iterates all subsets S ⊆ items with |S| ≤ maxSize (returns insertion-ordered sets). */
final class SmallSubsetIter<T> implements Iterable<Set<T>> {
    private final List<T> items;
    private final int maxSize;

    SmallSubsetIter(Collection<T> items, int maxSize) {
        this.items = new ArrayList<>(items);
        this.maxSize = Math.max(0, maxSize);
    }

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
                    if (maxSize < 1 || n == 0) { comb = null; return false; }
                    k = 1; comb = initComb(k); return true;
                }
                if (nextComb()) return true;            // next k-combination
                k++;
                if (k > maxSize || k > n) { comb = null; return false; }
                comb = initComb(k);
                return true;
            }

            @Override
            public Set<T> next() {
                // DO NOT call hasNext() here — the for-each loop already did.
                if (!firstReturned) { firstReturned = true; return Collections.emptySet(); }
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

    static <T> Iterable<Set<T>> subsets(Collection<T> items, int maxSize) {
        return new SmallSubsetIter<>(items, maxSize);
    }
}