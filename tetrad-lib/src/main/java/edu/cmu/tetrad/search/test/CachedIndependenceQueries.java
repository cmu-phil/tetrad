package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
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
 * <p>
 * Key properties:
 * - X,Y treated as unordered for caching (minId,maxId)
 * - Z represented as sorted int[] of variable IDs
 * - No String key construction or String.join in the hot path
 * <p>
 * Thread-safety:
 * - Safe for concurrent reads/writes (ConcurrentHashMap + putIfAbsent pattern).
 * <p>
 * NOTE:
 * We intentionally do NOT use ConcurrentHashMap.computeIfAbsent here because it can throw
 * IllegalStateException("Recursive update") if the mapping function re-enters the map for the
 * same key (which can happen if the wrapped test delegates back into this cache layer).
 */
public final class CachedIndependenceQueries implements IndependenceTest, RowsSettable, TetradSerializable {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 23L;
    /**
     * Cache keyed by structured QueryKey.
     */
    private final ConcurrentMap<QueryKey, Eval> evalCache = new ConcurrentHashMap<>();
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
     * Represents the error handling strategy employed when evaluating an independence query in the context
     * of cached independence tests. The variable determines how the system should react to errors or exceptions
     * encountered during the evaluation process.
     * <p>
     * Possible values:
     * - {@code ErrorPolicy.TREAT_AS_INDEPENDENT}: Treats an error as if the variables are independent.
     * - {@code ErrorPolicy.TREAT_AS_DEPENDENT}: Treats an error as if the variables are dependent.
     * - {@code ErrorPolicy.RETHROW}: Propagates the error by rethrowing the encountered exception.
     * <p>
     * This variable is declared as {@code volatile} to ensure thread-safe visibility of its updates across
     * multiple threads.
     */
    private volatile ErrorPolicy errorPolicy = ErrorPolicy.TREAT_AS_INDEPENDENT;

    /**
     * Default constructor for the CachedIndependenceQueries class.
     * <p>
     * Initializes a new instance of the CachedIndependenceQueries without any pre-defined
     * configurations or parameters. By default, this constructor sets up the necessary
     * data structures for caching but requires further customization before performing
     * independence queries or tests.
     */
    public CachedIndependenceQueries() {
    }

    // ------------------------ policies ------------------------

    /**
     * Constructs a new instance of CachedIndependenceQueries with the specified IndependenceTest.
     * Initializes the internal caching mechanism and uses the provided IndependenceTest
     * as the underlying dependency-testing framework. If the provided test is different
     * from the current one, it replaces the current test and clears all existing caches.
     *
     * @param test The IndependenceTest instance to be used for dependency checks
     *             within this CachedIndependenceQueries instance. This parameter
     *             cannot be null and must be set to enable proper functioning of
     *             the independence query operations.
     */
    public CachedIndependenceQueries(IndependenceTest test) {
        if (this.test != test) {
            setTest(test);
        }
    }

    /**
     * Constructs a new instance of CachedIndependenceQueries with the specified error-handling policy.
     * This constructor initializes the error policy to determine how the system should handle any exceptions
     * or anomalies encountered during the evaluation of independence tests.
     *
     * @param errorPolicy The error-handling behavior to be used within this instance.
     *                    Must not be null; possible values are defined in the {@code ErrorPolicy} enum,
     *                    such as {@code TREAT_AS_INDEPENDENT}, {@code TREAT_AS_DEPENDENT}, or {@code RETHROW}.
     */
    public CachedIndependenceQueries(ErrorPolicy errorPolicy) {
        this.errorPolicy = Objects.requireNonNull(errorPolicy, "errorPolicy");
    }

    /**
     * Retrieves a list of variables associated with the current object.
     * If the test object is null, an empty list is returned.
     *
     * @return a list of nodes representing the variables, or an empty list if no variables are available.
     */
    public List<Node> getVariables() {
        return test == null ? List.of() : test.getVariables();
    }

    /**
     * Retrieves the data model from the test object.
     * If the test object is null, this method returns null.
     *
     * @return the data model retrieved from the test object,
     * or null if the test object is null.
     */
    @Override
    public DataModel getData() {
        return test == null ? null : test.getData();
    }

    // ------------------------ state ------------------------

    /**
     * Determines whether verbose mode is enabled.
     *
     * @return true if verbose mode is enabled, false otherwise
     */
    @Override
    public boolean isVerbose() {
        return false;
    }

    /**
     * Sets the verbose mode for the wrapped test instance if it implements the IndependenceTest interface.
     *
     * @param verbose true to enable verbose mode, false to disable
     */
    public void setVerbose(boolean verbose) {
        if (this.test != null) this.test.setVerbose(verbose);
    }

    /**
     * Retrieves a list of row indices from the wrapped test instance if it implements the RowsSettable interface.
     *
     * @return a list of integers representing the row indices provided by the wrapped test instance.
     * @throws UnsupportedOperationException if the wrapped test instance does not support retrieving rows.
     */
    @Override
    public List<Integer> getRows() {
        if (test instanceof RowsSettable rs) return rs.getRows();
        else throw new UnsupportedOperationException("Wrapped test does not support getRows()");
    }

    /**
     * Sets the rows for the wrapped test, if it supports row setting.
     * If the wrapped test does not support setting rows, an
     * UnsupportedOperationException is thrown.
     *
     * @param rows a list of integers representing the rows to be set
     * @throws UnsupportedOperationException if the wrapped test does not support setting rows
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (test instanceof RowsSettable rs) rs.setRows(rows);
        else throw new UnsupportedOperationException("Wrapped test does not support setRows()");
    }

    /**
     * Determines whether the current independence test supports subsampling.
     * <p>
     * This method delegates the check to the underlying test instance, verifying
     * if the test implementation allows for subsampling operations.
     *
     * @return {@code true} if the underlying test supports subsampling; {@code false} otherwise.
     */
    @Override
    public boolean canBeSubsampled() {
        return test.canBeSubsampled();
    }

    /**
     * Clears all cached evaluations maintained by the object.
     * <p>
     * This method is responsible for resetting the internal caching
     * mechanism used for independence evaluations. Primarily, it clears
     * the `evalCache` field, which stores previously computed results
     * of independence tests to optimize performance.
     * <p>
     * Use this method when the caching mechanism needs to be reset, such
     * as when test parameters change or to free up memory by removing
     * stored results that are no longer needed.
     */
    public void clearCaches() {
        evalCache.clear();
    }

    /**
     * Resets the internal state of the object by clearing key data structures and references.
     * <p>
     * This method performs the following operations:
     * - Sets the `test` field to null, detaching any existing IndependenceTest instance.
     * - Resets `testVarByName` and `idByName` maps to empty, immutable maps.
     * - Invokes the `clearCaches` method to clear all cached evaluations.
     * <p>
     * Use this method to fully reset the internal state when the current test configuration
     * becomes invalid or when a fresh setup is required for subsequent operations.
     * <p>
     * This method is thread-safe as it synchronizes access to ensure consistency during the reset process.
     */
    public synchronized void clearTest() {
        this.test = null;
        this.testVarByName = Map.of();
        this.idByName = Map.of();
        clearCaches();
    }

    /**
     * Retrieves the current instance of the IndependenceTest being used.
     * <p>
     * The returned IndependenceTest object is the one currently configured
     * within the CachedIndependenceQueries instance. This test is used to
     * evaluate independence hypotheses and must be set before performing
     * any independence evaluations. If no test has been set, this method
     * may return null.
     *
     * @return The current IndependenceTest instance being used, or null
     * if no test has been assigned.
     */
    public IndependenceTest getTest() {
        return test;
    }

    /**
     * Sets the independence test instance and rebuilds internal structures.
     * Ensures no self-referencing or cyclic dependencies are present.
     *
     * @param test The independence test to set. If null, the current test is cleared.
     *             If the given test is an instance of {@code CachedIndependenceQueries},
     *             the inner test is checked for validity before being set.
     * @throws IllegalArgumentException If the provided test is the current instance,
     *                                  is a {@code CachedIndependenceQueries} with a null
     *                                  inner test, or if it creates a cycle in the
     *                                  wrapper chain.
     */
    public synchronized void setTest(IndependenceTest test) {
        if (test == null) {
            clearTest();
            return;
        }

        // Defend against accidental self-wrapping / wrapping-a-wrapper.
        // (This is a common way to create re-entrancy that trips CHM.computeIfAbsent.)
        if (test == this) {
            throw new IllegalArgumentException("Cannot setTest(this) for CachedIndependenceQueries.");
        }
        if (test instanceof CachedIndependenceQueries other) {
            IndependenceTest inner = other.getTest();
            if (inner == null) {
                throw new IllegalArgumentException("Cannot setTest(CachedIndependenceQueries) with null inner test.");
            }
            if (inner == this) {
                throw new IllegalArgumentException("Cannot setTest: would create a cycle in wrapper chain.");
            }
            test = inner;
        }

        this.test = test;
        rebuildMaps(test);
        clearCaches();
    }

    // ------------------------ lifecycle ------------------------

    /**
     * Retrieves the error-handling policy currently used by the instance.
     * <p>
     * The error policy determines how errors or anomalies encountered during
     * independence evaluations are handled. Possible values are defined in
     * the {@code ErrorPolicy} enumeration and include:
     * {@code TREAT_AS_INDEPENDENT}, {@code TREAT_AS_DEPENDENT}, and {@code RETHROW}.
     *
     * @return The current {@code ErrorPolicy} being used by this instance.
     */
    public ErrorPolicy getErrorPolicy() {
        return errorPolicy;
    }

    /**
     * Sets the error-handling policy for this instance.
     * <p>
     * This method allows specifying how errors or anomalies encountered during
     * independence evaluations should be handled. The error policy must be one
     * of the predefined values in the {@code ErrorPolicy} enumeration.
     *
     * @param policy The error-handling policy to set. Must not be null.
     *               Valid values are {@code TREAT_AS_INDEPENDENT},
     *               {@code TREAT_AS_DEPENDENT}, and {@code RETHROW}.
     * @throws NullPointerException if the provided policy is null.
     */
    public void setErrorPolicy(ErrorPolicy policy) {
        this.errorPolicy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Retrieves the significance level (alpha) used by the underlying IndependenceTest.
     * <p>
     * This method delegates to the current IndependenceTest instance to obtain
     * the significance level. If no test has been set, it returns {@code Double.NaN}.
     *
     * @return The significance level (alpha) configured for the current independence
     * test, or {@code Double.NaN} if no test is assigned.
     */
    public double getAlpha() {
        return test == null ? Double.NaN : test.getAlpha();
    }

    /**
     * Evaluates the independence fact using a cached or computed result.
     * If the test or the provided fact is null, it returns a default evaluation
     * indicating independence with a NaN p-value.
     * If the fact cannot be associated with a valid key, it is treated as independent
     * and not cached.
     *
     * @param fact the independence fact to be evaluated; must not be null for meaningful computation.
     * @return an Eval object containing the results of the evaluation, either from the cache
     * or newly computed. If the input is invalid, an Eval object indicating independence
     * with a NaN p-value is returned.
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

        // IMPORTANT: avoid computeIfAbsent (can throw "Recursive update" under re-entrancy).
        Eval cached = evalCache.get(key);
        if (cached != null) return cached;

        Eval computed = computeEval(local, fact);

        Eval raced = evalCache.putIfAbsent(key, computed);
        return raced != null ? raced : computed;
    }

    /**
     * IndependenceTest API: returns an IndependenceResult.
     * NOTE: This method still does the "rebind to test variables by name" to avoid identity issues.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
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

        // If we can't map nodes, treat as "can't test"; choose a conservative default.
        if (X == null || Y == null) {
            IndependenceFact fact0 = new IndependenceFact(x, y, z == null ? Set.of() : z);
            double alpha0 = test.getAlpha();
            double p0 = Double.NaN;
            boolean independent0 = true; // keep your previous behavior
            double score0 = -alpha0;
            return new IndependenceResult(fact0, independent0, p0, score0);
        }

        IndependenceFact fact = new IndependenceFact(X, Y, Z);
        Eval eval = eval(fact);

        double p = eval.pValue();
        double alpha = test.getAlpha();

        // Decision must be computed now, from the current alpha.
        boolean independent = !Double.isNaN(p) && p > alpha;

        double score = Double.isNaN(p) ? -alpha : (alpha - p);
        return new IndependenceResult(fact, independent, p, score);
    }

    /**
     * Determines whether the given IndependenceFact represents an independent state.
     *
     * @param fact the IndependenceFact to evaluate
     * @return true if the provided fact indicates independence, otherwise false
     */
    public boolean isIndependent(IndependenceFact fact) {
        return eval(fact).independent();
    }

    /**
     * Computes the p-value associated with a given independence fact by evaluating
     * its statistical significance.
     *
     * @param fact an instance of IndependenceFact representing the independence
     *             assertion to be evaluated
     * @return the p-value as a double, indicating the significance level of the
     * independence fact
     */
    public double pValue(IndependenceFact fact) {
        return eval(fact).pValue();
    }

    // ------------------------ core API ------------------------

    /**
     * Evaluates a collection of independence facts and returns a list of evaluation results.
     * The method allows optional deduplication based on the provided deduplication strategy.
     *
     * @param facts the collection of {@link IndependenceFact} objects to be evaluated; can be null or empty
     * @param dedup the deduplication strategy to be applied, either {@link Dedup#WITHIN_INPUT}
     *              or {@link Dedup#BY_CACHE_KEY}
     * @return a list of {@link Eval} results based on the provided facts; an empty list is returned if the input
     * collection is null, empty, or all items are filtered through deduplication
     */
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

    /**
     * Computes the p-values for a collection of independence facts after evaluating them.
     * Applies filtering to include only valid p-values within the range [0.0, 1.0].
     *
     * @param facts a collection of {@code IndependenceFact} instances to be evaluated.
     * @param dedup a {@code Dedup} instance to handle deduplication during the evaluation process.
     * @return a list of p-values (as {@code Double}) for the given independence facts that are valid.
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
     * Computes and returns a list of p-values associated with the given vertex
     * based on implied independence facts provided by the given provider.
     *
     * @param provider the provider of implied independence facts; must not be null.
     * @param vertex   the vertex for which to compute p-values; must not be null.
     * @return a list of p-values corresponding to the independence facts for the vertex;
     * returns an empty list if either the provider or the vertex is null.
     */
    public List<Double> pValuesForVertex(ImpliedFactProvider provider, Node vertex) {
        if (provider == null || vertex == null) return List.of();
        List<IndependenceFact> facts = provider.impliedFactsForVertex(vertex);
        return pValuesForFacts(facts, Dedup.WITHIN_INPUT);
    }

    /**
     * Computes and returns a list of p-values for all given vertices based on the independence
     * facts provided by the specified {@code ImpliedFactProvider}. The method ensures that duplicate
     * facts are skipped and only valid p-values within the range [0.0, 1.0] are included in the result.
     *
     * @param vertices a collection of {@code Node} objects representing the vertices for which p-values
     *                 need to be computed. If the collection is null or empty, an empty list is returned.
     * @param provider an {@code ImpliedFactProvider} that supplies the independence facts for the provided
     *                 vertices. If null, an empty list is returned.
     * @return a list of p-values as {@code Double} objects, corresponding to the valid independence facts
     * for the given vertices. If no valid p-values are found, an empty list is returned.
     */
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
     */
    private Node mapToTestNode(Node n) {
        if (n == null) return null;
        String name = n.getName();
        if (name == null) return null;
        return testVarByName.get(name);
    }

    // ------------------------ keying ------------------------

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

    /**
     * ErrorPolicy represents the policies that can be applied to handle errors
     * encountered during the execution of a process or task. This enum defines
     * various strategies, allowing for flexible management of errors based on
     * specific requirements.
     * <p>
     * - TREAT_AS_INDEPENDENT: Indicates that the error is considered independent
     * and does not affect other operations or processes.
     * - TREAT_AS_DEPENDENT: Indicates that the error has dependencies and may
     * affect other operations or processes.
     * - RETHROW: Indicates that the error should be rethrown to the calling
     * context for further handling or propagation.
     */
    public enum ErrorPolicy {

        /**
         * Indicates that the error is considered independent and does not affect
         * other operations or processes. This policy can be used to treat errors
         * in isolation, ensuring that they do not interfere with the execution
         * of unrelated tasks.
         */
        TREAT_AS_INDEPENDENT,

        /**
         * Indicates that the error has dependencies and may affect other operations
         * or processes. This policy is applied in scenarios where an error is not
         * isolated and could influence the execution of related tasks or workflows.
         */
        TREAT_AS_DEPENDENT,

        /**
         * Indicates that the error should be rethrown to the calling context for further
         * handling or propagation. This policy is applied when the error cannot be
         * handled within the current context and needs to be escalated.
         */
        RETHROW
    }

    /**
     * Enum representing deduplication strategies.
     * <p>
     * Deduplication refers to the process of eliminating duplicate data.
     * This enum provides strategies to handle duplicates in different contexts.
     */
    public enum Dedup {

        /**
         * Strategy indicating deduplication will occur within the current input data.
         * <p>
         * This option ensures duplication within the provided input set is removed,
         * typically without considering external contexts or caches.
         */
        WITHIN_INPUT,

        /**
         * Strategy indicating deduplication will occur based on a cache key.
         * <p>
         * This option ensures duplication is handled by consulting a cache,
         * where previously processed keys are stored. It is typically used
         * to avoid reprocessing data that has already been handled before.
         */
        BY_CACHE_KEY
    }

    // ------------------------ evaluation ------------------------

    /**
     * Functional interface representing a provider responsible for generating implied facts
     * associated with a given vertex in a graph-like data structure.
     * <p>
     * The interface defines a single abstract method which, given a vertex, returns a list
     * of {@link IndependenceFact} objects representing the implied facts for that vertex.
     * This can be used in contexts where relationships or constraints between vertices need
     * to be deduced.
     */
    @FunctionalInterface
    public interface ImpliedFactProvider {

        /**
         * Retrieves the list of implied {@link IndependenceFact} objects for the given vertex.
         *
         * @param vertex The vertex for which implied facts are to be retrieved.
         * @return A list of {@link IndependenceFact} objects representing the implied facts for the vertex.
         */
        List<IndependenceFact> impliedFactsForVertex(Node vertex);
    }

    /**
     * This record represents an evaluation with a binary indicator of independence
     * and an associated p-value. It implements the TetradSerializable interface to
     * ensure compatibility with Tetrad's serialization system.
     *
     * @param independent a boolean flag indicating whether the given relationship is independent
     * @param pValue      a double representing the p-value associated with the independence test
     */
    public record Eval(boolean independent, double pValue) implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;
    }

    // ------------------------ map rebuild ------------------------

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
}