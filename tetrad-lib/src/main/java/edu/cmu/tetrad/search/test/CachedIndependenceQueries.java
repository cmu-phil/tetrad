package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Shared cache + utilities for independence testing keyed by (X,Y|Z) using variable NAMES
 * for mapping into the test, but using a structured int-ID cache key for speed/GC reduction.
 *
 * Key properties:
 *  - X,Y treated as unordered for caching (minId,maxId)
 *  - Z represented as sorted int[] of variable IDs
 *  - No String key construction or String.join in the hot path
 *
 * Thread-safety:
 *  - Safe for concurrent reads/writes (ConcurrentHashMap + computeIfAbsent).
 */
public final class CachedIndependenceQueries implements TetradSerializable {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 23L;

    // ------------------------ policies ------------------------

    public enum ErrorPolicy {
        TREAT_AS_INDEPENDENT,
        TREAT_AS_DEPENDENT,
        RETHROW
    }

    public enum Dedup {
        WITHIN_INPUT,
        BY_CACHE_KEY
    }

    public record Eval(boolean independent, double pValue) implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;
    }

    @FunctionalInterface
    public interface ImpliedFactProvider {
        List<IndependenceFact> impliedFactsForVertex(Node vertex);
    }

    // ------------------------ state ------------------------

    private transient volatile IndependenceTest test;

    /**
     * Map name -> Node instance used by the test (rebuilt on setTest()).
     * Used to rebind facts that come from graph copies.
     */
    private transient volatile Map<String, Node> testVarByName = Map.of();

    /**
     * Map name -> small int id (rebuilt on setTest()).
     * Used only for fast cache keys.
     */
    private transient volatile Map<String, Integer> idByName = Map.of();

    /**
     * Cache keyed by structured QueryKey.
     */
    private final ConcurrentMap<QueryKey, Eval> evalCache = new ConcurrentHashMap<>();

    private volatile ErrorPolicy errorPolicy = ErrorPolicy.TREAT_AS_INDEPENDENT;

    public CachedIndependenceQueries() { }

    public CachedIndependenceQueries(ErrorPolicy errorPolicy) {
        this.errorPolicy = Objects.requireNonNull(errorPolicy, "errorPolicy");
    }

    // ------------------------ lifecycle ------------------------

    /**
     * Set/replace the underlying IndependenceTest. Clears all cached evaluations.
     * Call this whenever the user changes the test or its parameters.
     */
    public synchronized void setTest(IndependenceTest test) {
        this.test = test;
        rebuildMaps(test);
        clearCaches();
    }

    public void clearCaches() {
        evalCache.clear();
    }

    public synchronized void clearTest() {
        this.test = null;
        this.testVarByName = Map.of();
        this.idByName = Map.of();
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

    // ------------------------ core API ------------------------

    /**
     * Evaluate a single IndependenceFact, using the cache.
     * Facts may contain Nodes from graph copies; we key by name->id and rebind to test variables.
     */
    public Eval eval(IndependenceFact fact) {
        IndependenceTest local = this.test;
        if (local == null || fact == null) {
            return new Eval(true, Double.NaN);
        }

        // Build a fast structured key using ids.
        QueryKey key = keyOf(fact);
        if (key == null) {
            // Missing vars => treat as independent; don't cache under a null key.
            return new Eval(true, Double.NaN);
        }

        return evalCache.computeIfAbsent(key, k -> computeEval(local, fact));
    }

    /**
     * Compatibility method used by UI code: returns an IndependenceResult.
     * NOTE: This method still does the "rebind to test variables by name" to avoid identity issues.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        if (test == null) throw new IllegalStateException("Independence test not set.");

        Node X = mapToTestNode(x);
        Node Y = mapToTestNode(y);

        Set<Node> Z = new LinkedHashSet<>();
        if (z != null) {
            for (Node zi : z) {
                Node ZZ = mapToTestNode(zi);
                if (ZZ != null) Z.add(ZZ);
            }
        }

        if (X == null || Y == null) {
            IndependenceFact fact = new IndependenceFact(x, y, z == null ? Set.of() : z);
            double alpha = test.getAlpha();
            double score = -alpha;
            return new IndependenceResult(fact, true, Double.NaN, score);
        }

        IndependenceFact fact = new IndependenceFact(X, Y, Z);
        Eval eval = eval(fact);

        double p = eval.pValue();
        double alpha = test.getAlpha();
        double score = Double.isNaN(p) ? -alpha : (alpha - p);

        return new IndependenceResult(fact, eval.independent(), p, score);
    }

    public boolean isIndependent(IndependenceFact fact) {
        return eval(fact).independent();
    }

    public double pValue(IndependenceFact fact) {
        return eval(fact).pValue();
    }

    public List<Eval> evalAll(Collection<IndependenceFact> facts, Dedup dedup) {
        if (facts == null || facts.isEmpty()) return List.of();

        // Dedup in this list by cache key.
        Set<QueryKey> seen = new HashSet<>();
        List<Eval> out = new ArrayList<>(facts.size());

        for (IndependenceFact f : facts) {
            if (f == null) continue;

            QueryKey k = keyOf(f);
            if (k == null) continue;

            if (dedup == Dedup.WITHIN_INPUT || dedup == Dedup.BY_CACHE_KEY) {
                if (!seen.add(k)) continue;
            }

            out.add(eval(f));
        }

        return out;
    }

    public List<Double> pValuesForFacts(Collection<IndependenceFact> facts, Dedup dedup) {
        List<Eval> evals = evalAll(facts, dedup);
        List<Double> p = new ArrayList<>(evals.size());

        for (Eval e : evals) {
            double v = e.pValue();
            if (!Double.isNaN(v) && v >= 0.0 && v <= 1.0) p.add(v);
        }

        return p;
    }

    public List<Double> pValuesForVertex(ImpliedFactProvider provider, Node vertex) {
        if (provider == null || vertex == null) return List.of();
        List<IndependenceFact> facts = provider.impliedFactsForVertex(vertex);
        return pValuesForFacts(facts, Dedup.WITHIN_INPUT);
    }

    public List<Double> pValuesForAllVertices(Collection<Node> vertices, ImpliedFactProvider provider) {
        if (vertices == null || vertices.isEmpty() || provider == null) return List.of();

        Set<QueryKey> seen = new HashSet<>();
        List<Double> pvals = new ArrayList<>();

        for (Node v : vertices) {
            if (v == null) continue;
            List<IndependenceFact> facts = provider.impliedFactsForVertex(v);
            if (facts == null) continue;

            for (IndependenceFact f : facts) {
                if (f == null) continue;

                QueryKey k = keyOf(f);
                if (k == null) continue;
                if (!seen.add(k)) continue;

                double p = pValue(f);
                if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                    pvals.add(p);
                }
            }
        }

        return pvals;
    }

    // ------------------------ keying ------------------------

    /**
     * Structured key for (X,Y|Z) with X,Y unordered and Z sorted.
     * Immutable and safe for use as a CHM key. Hash is precomputed.
     */
    private static final class QueryKey implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 23L;

        final int a;        // min(xId,yId)
        final int b;        // max(xId,yId)
        final int[] z;      // sorted
        final int hash;     // precomputed

        QueryKey(int a, int b, int[] z) {
            this.a = a;
            this.b = b;
            this.z = (z == null || z.length == 0) ? new int[0] : z;
            this.hash = computeHash(a, b, this.z);
        }

        private static int computeHash(int a, int b, int[] z) {
            int h = 31 * a + b;
            h = 31 * h + z.length;
            for (int v : z) {
                h = 31 * h + v;
            }
            return h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QueryKey other)) return false;
            if (a != other.a || b != other.b) return false;
            return Arrays.equals(z, other.z);
        }
    }

    /**
     * Build a cache key from a fact.
     * Returns null if any variable name is missing from the current id map.
     */
    private QueryKey keyOf(IndependenceFact f) {
        if (f == null) return null;

        Integer ix = idOfName(f.getX());
        Integer iy = idOfName(f.getY());
        if (ix == null || iy == null) return null;

        int a = Math.min(ix, iy);
        int b = Math.max(ix, iy);

        Set<Node> zset = f.getZ();
        if (zset == null || zset.isEmpty()) {
            return new QueryKey(a, b, new int[0]);
        }

        // Collect Z ids; drop any unknowns.
        int[] tmp = new int[zset.size()];
        int k = 0;
        for (Node zn : zset) {
            Integer id = idOfName(zn);
            if (id != null) tmp[k++] = id;
        }

        if (k == 0) {
            return new QueryKey(a, b, new int[0]);
        }

        int[] z = (k == tmp.length) ? tmp : Arrays.copyOf(tmp, k);
        Arrays.sort(z);

        // Optional: remove duplicates in Z (rare but safe).
        int uniq = 1;
        for (int i = 1; i < z.length; i++) {
            if (z[i] != z[i - 1]) uniq++;
        }
        if (uniq != z.length) {
            int[] zd = new int[uniq];
            zd[0] = z[0];
            int j = 1;
            for (int i = 1; i < z.length; i++) {
                if (z[i] != z[i - 1]) zd[j++] = z[i];
            }
            z = zd;
        }

        return new QueryKey(a, b, z);
    }

    private Integer idOfName(Node n) {
        if (n == null) return null;
        String name = n.getName();
        if (name == null) return null;
        return idByName.get(name);
    }

    // ------------------------ evaluation ------------------------

    private Eval computeEval(IndependenceTest local, IndependenceFact fact) {
        try {
            Node X = mapToTestNode(fact.getX());
            Node Y = mapToTestNode(fact.getY());

            if (X == null || Y == null) {
                return new Eval(true, Double.NaN);
            }

            Set<Node> Z = new LinkedHashSet<>();
            for (Node z : fact.getZ()) {
                Node zz = mapToTestNode(z);
                if (zz != null) Z.add(zz);
            }

            IndependenceResult r = local.checkIndependence(X, Y, Z);

            if (r == null) return new Eval(true, Double.NaN);
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

    /**
     * Map an arbitrary Node (often from a graph copy) to the IndependenceTest's Node instance.
     * If the Node is already the test's instance, returns it immediately.
     */
    private Node mapToTestNode(Node n) {
        if (n == null) return null;
        String name = n.getName();
        if (name == null) return null;

        // Fast path: if the test can resolve by name, that's our canonical instance.
        Node mapped = testVarByName.get(name);
        return mapped;
    }

    // ------------------------ map rebuild ------------------------

    private void rebuildMaps(IndependenceTest test) {
        if (test == null) {
            this.testVarByName = Map.of();
            this.idByName = Map.of();
            return;
        }

        Map<String, Node> nameToNode = new HashMap<>();
        Map<String, Integer> nameToId = new HashMap<>();

        int i = 0;
        try {
            for (Node n : test.getVariables()) {
                if (n == null) continue;
                String nm = n.getName();
                if (nm == null) continue;
                nameToNode.put(nm, n);
                nameToId.put(nm, i++);
            }
        } catch (Throwable t) {
            nameToNode.clear();
            nameToId.clear();
        }

        this.testVarByName = Collections.unmodifiableMap(nameToNode);
        this.idByName = Collections.unmodifiableMap(nameToId);
    }
}