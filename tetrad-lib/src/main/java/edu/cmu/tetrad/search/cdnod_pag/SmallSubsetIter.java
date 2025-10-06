package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.graph.Node;

import java.util.*;

/** Iterates all subsets S ⊆ neigh with |S| ≤ maxSize. */
final class SmallSubsetIter implements Iterable<Set<Node>> {
    private final List<Node> items;
    private final int maxSize;

    SmallSubsetIter(Collection<Node> items, int maxSize) {
        this.items = new ArrayList<>(items);
        this.maxSize = Math.max(0, maxSize);
    }

    @Override
    public Iterator<Set<Node>> iterator() {
        return new Iterator<>() {
            private final int n = items.size();
            private int k = 0;
            private int[] comb = (n == 0 ? null : new int[0]);
            private boolean firstReturned = false;

            @Override public boolean hasNext() {
                if (!firstReturned) return true;
                if (comb == null) return false;
                if (k == 0) { // just returned empty; move to size 1 or stop if maxSize < 1
                    if (maxSize < 1 || n == 0) { comb = null; return false; }
                    k = 1; comb = initComb(k); return true;
                }
                if (nextComb()) return true;
                k++;
                if (k > maxSize || k > n) { comb = null; return false; }
                comb = initComb(k);
                return true;
            }

            @Override public Set<Node> next() {
                if (!firstReturned) { firstReturned = true; return Collections.emptySet(); }
                if (comb == null) throw new NoSuchElementException();
                Set<Node> s = new LinkedHashSet<>();
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
                while (i >= 0 && comb[i] == items.size() - k + i) i--;
                if (i < 0) return false;
                comb[i]++;
                for (int j = i + 1; j < k; j++) comb[j] = comb[j - 1] + 1;
                return true;
            }
        };
    }

    static Iterable<Set<Node>> subsets(Collection<Node> items, int maxSize) {
        return new SmallSubsetIter(items, maxSize);
    }
}