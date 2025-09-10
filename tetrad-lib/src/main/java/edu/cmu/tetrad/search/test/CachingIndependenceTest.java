package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * A caching wrapper for IndependenceTest.
 * <p>
 * Ensures that each unique conditional independence test (X ⟂ Y | Z) is evaluated at most once. Results are cached and
 * re-used. Both (X,Y|Z) and (Y,X|Z) map to the same cache entry.
 * <p>
 * Useful for expensive tests such as KCI.
 */
public class CachingIndependenceTest implements IndependenceTest {

    private final IndependenceTest base;

    // Canonical key -> cached result
    private final Map<CacheKey, IndependenceResult> cache = new HashMap<>();

    public CachingIndependenceTest(IndependenceTest base) {
        this.base = Objects.requireNonNull(base);
    }

    @Override
    public List<Node> getVariables() {
        return base.getVariables();
    }

    @Override
    public DataModel getData() {
        return this.base.getData();
    }

    @Override
    public boolean isVerbose() {
        return this.base.isVerbose();
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.base.setVerbose(verbose);
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        CacheKey key = new CacheKey(x, y, z);
        IndependenceResult cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        IndependenceResult result = base.checkIndependence(x, y, z);
        cache.put(key, result);
        return result;
    }

    public IndependenceTest getBaseTest() {
        return base;
    }

    // ---------------------------------------------------------------------
    // Internal cache key
    // ---------------------------------------------------------------------
    private static final class CacheKey {
        private final String x;
        private final String y;
        private final List<String> z;

        CacheKey(Node a, Node b, Set<Node> condSet) {
            // canonicalize: order x,y alphabetically
            if (a.getName().compareTo(b.getName()) <= 0) {
                this.x = a.getName();
                this.y = b.getName();
            } else {
                this.x = b.getName();
                this.y = a.getName();
            }
            // canonicalize: sorted conditioning set
            this.z = new ArrayList<>();
            for (Node n : condSet) this.z.add(n.getName());
            Collections.sort(this.z);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey other)) return false;
            return x.equals(other.x) && y.equals(other.y) && z.equals(other.z);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}