package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.util.TMath;

import java.util.*;

/**
 * Iterates all subsets S ⊆ items with |S| ≤ maxSize, in ascending order of size starting with the
 * empty set (returns insertion-ordered sets).
 * <p>
 * The iterator obeys the standard {@link Iterator} contract: {@code hasNext()} is idempotent and
 * side-effect-free from the caller's perspective (it uses a lookahead buffer internally), and
 * {@code next()} throws {@link NoSuchElementException} when exhausted. It is therefore safe to
 * call {@code hasNext()} any number of times, or {@code next()} without a preceding
 * {@code hasNext()}.
 */
final class SmallSubsetIter<T> implements Iterable<Set<T>> {

    /**
     * The items from which subsets are generated.
     */
    private final List<T> items;

    /**
     * The maximum subset size; non-negative.
     */
    private final int maxSize;

    /**
     * Constructs an iterable over all subsets S ⊆ items with |S| ≤ maxSize.
     *
     * @param items   the collection of items to generate subsets from
     * @param maxSize the maximum size of subsets to generate; negative values are clamped to 0
     */
    SmallSubsetIter(Collection<T> items, int maxSize) {
        this.items = new ArrayList<>(items);
        this.maxSize = TMath.max(0, maxSize);
    }

    /**
     * Convenience factory.
     *
     * @param <T>     the element type
     * @param items   the collection of items to generate subsets from
     * @param maxSize the maximum size of subsets to generate; negative values are clamped to 0
     * @return an iterable over all subsets satisfying the constraints
     */
    static <T> Iterable<Set<T>> subsets(Collection<T> items, int maxSize) {
        return new SmallSubsetIter<>(items, maxSize);
    }

    @Override
    public Iterator<Set<T>> iterator() {
        return new Iterator<>() {
            private final int n = items.size();
            private int k = 0;                              // size of the subset last produced
            private int[] comb = null;                      // current k-combination (k >= 1)
            private Set<T> pending = Collections.emptySet(); // lookahead buffer; ∅ is first
            private boolean exhausted = false;

            @Override
            public boolean hasNext() {
                if (pending == null && !exhausted) advance();
                return pending != null;
            }

            @Override
            public Set<T> next() {
                if (!hasNext()) throw new NoSuchElementException();
                Set<T> out = pending;
                pending = null;
                return out;
            }

            /**
             * Computes the next subset into the lookahead buffer, or marks exhaustion.
             */
            private void advance() {
                if (k == 0) {
                    // ∅ has been produced; move to size-1 combinations.
                    k = 1;
                    if (k > maxSize || k > n) {
                        exhausted = true;
                        return;
                    }
                    comb = initComb(k);
                } else if (!nextComb()) {
                    // Current size exhausted; move to the next size.
                    k++;
                    if (k > maxSize || k > n) {
                        exhausted = true;
                        return;
                    }
                    comb = initComb(k);
                }

                Set<T> s = new LinkedHashSet<>();
                for (int idx : comb) s.add(items.get(idx));
                pending = s;
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
