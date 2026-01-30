package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Shared cache + utilities for independence testing keyed by (X,Y|Z) using variable NAMES.
 *
 * Goals:
 *  - One canonical keying scheme across UI components.
 *  - Cache slow IndependenceTest.checkIndependence(X, Y, Z) calls.
 *  - Provide helpers to list p-values nodewise or globally with consistent dedup rules.
 *
 * Thread-safety:
 *  - Safe for concurrent reads/writes (ConcurrentHashMap + computeIfAbsent).
 *
 * Notes:
 *  - Keys are based on names: X,Y unordered, Z sorted by name.
 *  - Node identity differences between Graph copies do not matter as long as names match.
 */
public final class CachedIndependenceQueries implements TetradSerializable {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 23L;

    /**
     * Policy for how to treat errors thrown during testing.
     */
    public enum ErrorPolicy {
        /** If a test throws, treat as independent (i.e., not a violation). */
        TREAT_AS_INDEPENDENT,
        /** If a test throws, treat as dependent (conservative). */
        TREAT_AS_DEPENDENT,
        /** If a test throws, rethrow as a RuntimeException. */
        RETHROW
    }

    /**
     * Dedup policy when collecting p-values/results.
     */
    public enum Dedup {
        /** Dedup within the collection you pass in (typical nodewise). */
        WITHIN_INPUT,
        /** Dedup across all calls via the global cache key (typical global/model). */
        BY_CACHE_KEY
    }

    /**
     * Small immutable view of a cached test evaluation.
     * (We store only what we need for Markov-checking displays/KS tests.)
     */
    public record Eval(boolean independent, double pValue) implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;
    }

    /**
     * Provides implied facts for a vertex, without binding this class to app-layer models.
     * VertexCheckIndTestModel already knows how to do this; you pass a lambda.
     */
    @FunctionalInterface
    public interface ImpliedFactProvider {
        List<IndependenceFact> impliedFactsForVertex(Node vertex);
    }

    private transient volatile IndependenceTest test;
    private transient volatile Map<String, Node> testVarByName = Map.of(); // rebuilt on setTest()

    // Keyed by canonical queryKey(X,Y,Z) -> cached Eval
    private final ConcurrentMap<String, Eval> evalCache = new ConcurrentHashMap<>();

    // Optional: cache canonical keys for facts to avoid recomputing them when listing
    // (not strictly necessary; cheap)
    // private final ConcurrentMap<IndependenceFact, String> factKeyCache = new ConcurrentHashMap<>();

    private volatile ErrorPolicy errorPolicy = ErrorPolicy.TREAT_AS_INDEPENDENT;

    public CachedIndependenceQueries() { }

    public CachedIndependenceQueries(ErrorPolicy errorPolicy) {
        this.errorPolicy = Objects.requireNonNull(errorPolicy, "errorPolicy");
    }

    /**
     * Set/replace the underlying IndependenceTest. Clears all cached evaluations.
     * Call this whenever the user changes the test or its parameters.
     */
    public synchronized void setTest(IndependenceTest test) {
        this.test = test;
        rebuildNameMap(test);
        clearCaches();
    }

    /**
     * Clears caches (but does not unset the test).
     */
    public void clearCaches() {
        evalCache.clear();
    }

    /**
     * Unset the test (clears caches too).
     */
    public synchronized void clearTest() {
        this.test = null;
        this.testVarByName = Map.of();
        clearCaches();
    }

    public IndependenceTest getTest() {
        return test;
    }

    public void setErrorPolicy(ErrorPolicy policy) {
        this.errorPolicy = Objects.requireNonNull(policy, "policy");
    }

    public ErrorPolicy getErrorPolicy() {
        return errorPolicy;
    }

    /**
     * Canonical key for caching (X,Y unordered; Z sorted).
     */
    public static String queryKey(IndependenceFact f) {
        String a = f.getX().getName();
        String b = f.getY().getName();

        if (a.compareTo(b) > 0) {
            String t = a;
            a = b;
            b = t;
        }

        List<String> z = new ArrayList<>();
        for (Node n : f.getZ()) z.add(n.getName());
        Collections.sort(z);

        return a + "|" + b + "|" + String.join(",", z);
    }

    /**
     * Evaluate a single IndependenceFact, using the cache.
     * Result keying is by names; we map to the test's Node objects by name.
     */
    public Eval eval(IndependenceFact fact) {
        IndependenceTest local = this.test;
        if (local == null || fact == null) {
            // No test => treat as independent with NaN p-value.
            return new Eval(true, Double.NaN);
        }

        final String key = queryKey(fact);

        return evalCache.computeIfAbsent(key, k -> computeEval(local, fact));
    }


    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        if (test == null) throw new IllegalStateException("Independence test not set.");

        // Rebind to the test's variable instances (prevents identity mismatches).
        Node X = test.getVariable(x.getName());
        Node Y = test.getVariable(y.getName());

        Set<Node> Z = new LinkedHashSet<>();
        if (z != null) {
            for (Node zi : z) {
                if (zi == null) continue;
                Node ZZ = test.getVariable(zi.getName());
                if (ZZ != null) Z.add(ZZ);
            }
        }

        // If variables aren't found, treat as independent with a negative score (no evidence of dependence).
        if (X == null || Y == null) {
            IndependenceFact fact = new IndependenceFact(x, y, z == null ? Set.of() : z);
            double alpha = test.getAlpha();
            double score = -alpha; // negative => "independent-ish"
            return new IndependenceResult(fact, true, Double.NaN, score);
        }

        IndependenceFact fact = new IndependenceFact(X, Y, Z);
        Eval eval = eval(fact);

        double p = eval.pValue();
        double alpha = test.getAlpha();

        // Robust scoring: score = alpha - p, but if p is NaN treat as "no evidence of dependence".
        double score = Double.isNaN(p) ? -alpha : (alpha - p);

        return new IndependenceResult(fact, eval.independent(), p, score);
    }

    /**
     * Convenience: isIndependent for a fact.
     */
    public boolean isIndependent(IndependenceFact fact) {
        return eval(fact).independent();
    }

    /**
     * Convenience: pValue for a fact.
     */
    public double pValue(IndependenceFact fact) {
        return eval(fact).pValue();
    }

    /**
     * Collect evaluations for a given list of facts with dedup policy.
     *
     * Dedup.WITHIN_INPUT: de-dups repeated facts within this input list only (typical nodewise).
     * Dedup.BY_CACHE_KEY: uses cache key dedup; in practice identical, but makes intent explicit.
     */
    public List<Eval> evalAll(Collection<IndependenceFact> facts, Dedup dedup) {
        if (facts == null || facts.isEmpty()) return List.of();

        Set<String> seen = new HashSet<>();
        List<Eval> out = new ArrayList<>(facts.size());

        for (IndependenceFact f : facts) {
            if (f == null) continue;

            String k = queryKey(f);
            if (dedup == Dedup.WITHIN_INPUT) {
                if (!seen.add(k)) continue;
            } else {
                // BY_CACHE_KEY: still skip duplicates in this list, but key is same anyway.
                if (!seen.add(k)) continue;
            }

            out.add(eval(f));
        }

        return out;
    }

    /**
     * Collect p-values for a list of facts, with a dedup policy and a validity filter.
     * By default, filters to [0,1] and excludes NaN.
     */
    public List<Double> pValuesForFacts(Collection<IndependenceFact> facts, Dedup dedup) {
        List<Eval> evals = evalAll(facts, dedup);
        List<Double> p = new ArrayList<>(evals.size());

        for (Eval e : evals) {
            double v = e.pValue();
            if (!Double.isNaN(v) && v >= 0.0 && v <= 1.0) p.add(v);
        }

        return p;
    }

    /**
     * Nodewise: obtain implied facts for a vertex via provider, and return p-values (dedup within vertex).
     */
    public List<Double> pValuesForVertex(ImpliedFactProvider provider, Node vertex) {
        if (provider == null || vertex == null) return List.of();
        List<IndependenceFact> facts = provider.impliedFactsForVertex(vertex);
        return pValuesForFacts(facts, Dedup.WITHIN_INPUT);
    }

    /**
     * Model/global: collect p-values across many vertices, with global dedup by key.
     * This is ideal for "M-KS".
     */
    public List<Double> pValuesForAllVertices(Collection<Node> vertices, ImpliedFactProvider provider) {
        if (vertices == null || vertices.isEmpty() || provider == null) return List.of();

        // Global dedup across vertices by cache key.
        Set<String> seen = new HashSet<>();
        List<Double> pvals = new ArrayList<>();

        for (Node v : vertices) {
            if (v == null) continue;
            List<IndependenceFact> facts = provider.impliedFactsForVertex(v);
            if (facts == null) continue;

            for (IndependenceFact f : facts) {
                if (f == null) continue;
                String k = queryKey(f);
                if (!seen.add(k)) continue;

                double p = pValue(f);
                if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                    pvals.add(p);
                }
            }
        }

        return pvals;
    }

    // ------------------------------------------------------------------

    private void rebuildNameMap(IndependenceTest test) {
        if (test == null) {
            this.testVarByName = Map.of();
            return;
        }

        Map<String, Node> map = new HashMap<>();
        try {
            for (Node n : test.getVariables()) {
                if (n != null && n.getName() != null) {
                    map.put(n.getName(), n);
                }
            }
        } catch (Throwable t) {
            // If getVariables() misbehaves, fall back to empty mapping.
            map.clear();
        }

        this.testVarByName = Collections.unmodifiableMap(map);
    }

    private Eval computeEval(IndependenceTest local, IndependenceFact fact) {
        try {
            Node X = mapToTestNode(fact.getX());
            Node Y = mapToTestNode(fact.getY());

            if (X == null || Y == null) {
                // Missing vars: treat as independent, p unknown.
                return new Eval(true, Double.NaN);
            }

            Set<Node> Z = new LinkedHashSet<>();
            for (Node z : fact.getZ()) {
                Node zz = mapToTestNode(z);
                if (zz != null) Z.add(zz);
            }

            IndependenceResult r = local.checkIndependence(X, Y, Z);

            if (r == null) return new Eval(true, Double.NaN);

            // Cache only "what we need":
            return new Eval(r.isIndependent(), r.getPValue());

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return switch (errorPolicy) {
                case TREAT_AS_INDEPENDENT -> new Eval(true, Double.NaN);
                case TREAT_AS_DEPENDENT -> new Eval(false, Double.NaN);
                case RETHROW -> throw new RuntimeException(ie);
            };
        } catch (Throwable t) {
            return switch (errorPolicy) {
                case TREAT_AS_INDEPENDENT -> new Eval(true, Double.NaN);
                case TREAT_AS_DEPENDENT -> new Eval(false, Double.NaN);
                case RETHROW -> throw new RuntimeException(t);
            };
        }
    }

    private Node mapToTestNode(Node n) {
        if (n == null) return null;
        String name = n.getName();
        if (name == null) return null;
        return testVarByName.get(name);
    }
}