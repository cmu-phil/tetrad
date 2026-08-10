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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A wrapper independence test that consults a list of "pinned" conditional independence facts before delegating to an
 * inner test. If the queried fact (X, Y | Z) is pinned <i>dependent</i>, the wrapper returns a dependent judgment
 * without consulting data; if pinned <i>independent</i>, it returns an independent judgment; otherwise it delegates to
 * the inner test unchanged.
 * <p>
 * The intended use is Markov-check feedback: when a Markov check of an estimated graph rejects a list of implied
 * conditional independencies (ideally gated by effect size, not p-value alone, to avoid feeding back test
 * misspecification), those facts may be pinned dependent for a subsequent run of a constraint-based search. This
 * encodes exactly the disjunctive constraint the rejection licenses — "the next graph should not imply this
 * separation" — while leaving the algorithm free to decide <i>how</i> to accommodate it (edge addition,
 * reorientation, etc.), in contrast to translating rejections into required edges in a {@code Knowledge} object,
 * which resolves the disjunction by fiat and always in the densifying direction. The symmetric direction (implied
 * dependencies judged clearly independent, pinned independent) licenses removals and prevents the iteration from
 * having a one-way ratchet toward density.
 * <p>
 * <b>Design decisions.</b> (1) Pins are matched by <i>variable name</i>, not node identity, so facts collected from a
 * Markov check run on a held-out fold (with distinct Node objects) match queries in the search fold. The unordered
 * pair {X, Y} and the set Z are canonicalized, consistent with {@link IndependenceFact} symmetry. (2) A pin fires
 * only on an exact match of the conditioning set. For PC-style searches this has traction because implied local
 * Markov conditioning sets are parent sets, which are subsets of the adjacency sets FAS enumerates; the
 * {@link #getDependentPinHits()} / {@link #getIndependentPinHits()} counters report whether pins were actually
 * consulted, which callers should check. (3) Pinned-dependent results report p = 0 and pinned-independent results
 * report p = 1; both are marked valid. These p-values are conventional, not evidential — they exist to steer the
 * search, and should not be pooled into downstream p-value summaries.
 * <p>
 * If a fact is pinned both dependent and independent, the dependent pin wins and the conflict is recorded; see
 * {@link #getConflicts()}.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.MarkovCheck
 */
public class PinnedIndependenceTest implements IndependenceTest {

    /**
     * The inner test to which unpinned queries are delegated.
     */
    private final IndependenceTest base;

    /**
     * Canonical name-keys of facts pinned dependent.
     */
    private final Set<String> pinnedDependent = ConcurrentHashMap.newKeySet();

    /**
     * Canonical name-keys of facts pinned independent.
     */
    private final Set<String> pinnedIndependent = ConcurrentHashMap.newKeySet();

    /**
     * Keys pinned in both directions; dependent wins at query time.
     */
    private final Set<String> conflicts = ConcurrentHashMap.newKeySet();

    /**
     * Number of queries answered from a dependent pin.
     */
    private final AtomicInteger dependentPinHits = new AtomicInteger();

    /**
     * Number of queries answered from an independent pin.
     */
    private final AtomicInteger independentPinHits = new AtomicInteger();

    /**
     * If true, every query and its judgment is recorded; see {@link #getQueryLog()}.
     */
    private volatile boolean recordQueries = false;

    /**
     * The query log, in query order. Only appended to when {@link #setRecordQueries(boolean)} is true.
     */
    private final List<QueryRecord> queryLog = Collections.synchronizedList(new ArrayList<>());

    /**
     * Constructs the wrapper around the given inner test.
     *
     * @param base the test to delegate unpinned queries to. Not null.
     */
    public PinnedIndependenceTest(IndependenceTest base) {
        this.base = Objects.requireNonNull(base, "base test");
    }

    /**
     * Turns query-trace recording on or off. When on, every query is logged with its judgment and whether it was
     * answered from a pin. The trace is the set of CI decisions the calling algorithm actually relied on, which is
     * the right object to validate out-of-sample: for a constraint-based search, pinning an invalidated trace fact
     * is guaranteed to have traction on the next run (the algorithm re-issues the query), whereas pinning a
     * graph-implied fact the algorithm never queries has none.
     *
     * @param recordQueries true to record queries.
     */
    public void setRecordQueries(boolean recordQueries) {
        this.recordQueries = recordQueries;
    }

    /**
     * Returns a copy of the query log in query order.
     *
     * @return the query log.
     */
    public List<QueryRecord> getQueryLog() {
        synchronized (queryLog) {
            return new ArrayList<>(queryLog);
        }
    }

    /**
     * Clears the query log.
     */
    public void clearQueryLog() {
        queryLog.clear();
    }

    /**
     * One recorded query: the fact, the p-value and judgment returned, and whether a pin answered it.
     *
     * @param fact        the queried fact.
     * @param pValue      the p-value returned.
     * @param independent the judgment returned.
     * @param fromPin     true if a pin answered the query (so the judgment is not the inner test's).
     */
    public record QueryRecord(IndependenceFact fact, double pValue, boolean independent, boolean fromPin) {
    }

    /**
     * Pins the given fact dependent: subsequent queries of exactly (X, Y | Z), matched by name with {X, Y} unordered,
     * return a dependent judgment without consulting data.
     *
     * @param fact the fact to pin dependent.
     */
    public void pinDependent(IndependenceFact fact) {
        String key = key(fact.getX(), fact.getY(), fact.getZ());
        if (pinnedIndependent.contains(key)) conflicts.add(key);
        pinnedDependent.add(key);
    }

    /**
     * Pins the given fact independent: subsequent queries of exactly (X, Y | Z), matched by name with {X, Y}
     * unordered, return an independent judgment without consulting data. If the same fact is also pinned dependent,
     * the dependent pin wins.
     *
     * @param fact the fact to pin independent.
     */
    public void pinIndependent(IndependenceFact fact) {
        String key = key(fact.getX(), fact.getY(), fact.getZ());
        if (pinnedDependent.contains(key)) conflicts.add(key);
        pinnedIndependent.add(key);
    }

    /**
     * Removes all pins and resets hit counters.
     */
    public void clearPins() {
        pinnedDependent.clear();
        pinnedIndependent.clear();
        conflicts.clear();
        resetHitCounts();
    }

    /**
     * Resets the pin hit counters (typically between searches, so per-search traction can be reported).
     */
    public void resetHitCounts() {
        dependentPinHits.set(0);
        independentPinHits.set(0);
    }

    /**
     * Returns the number of queries answered from a dependent pin since the last reset. If this is 0 after a search,
     * the pins had no traction on that search and the resulting graph owes nothing to them.
     *
     * @return the dependent pin hit count.
     */
    public int getDependentPinHits() {
        return dependentPinHits.get();
    }

    /**
     * Returns the number of queries answered from an independent pin since the last reset.
     *
     * @return the independent pin hit count.
     */
    public int getIndependentPinHits() {
        return independentPinHits.get();
    }

    /**
     * Returns the number of dependent pins currently registered.
     *
     * @return the dependent pin count.
     */
    public int getNumDependentPins() {
        return pinnedDependent.size();
    }

    /**
     * Returns the number of independent pins currently registered.
     *
     * @return the independent pin count.
     */
    public int getNumIndependentPins() {
        return pinnedIndependent.size();
    }

    /**
     * Returns the canonical keys of facts pinned in both directions. Dependent pins win at query time; a nonempty
     * conflict set usually indicates the caller's gating thresholds overlap and should be tightened.
     *
     * @return an unmodifiable view of the conflicting keys.
     */
    public Set<String> getConflicts() {
        return Collections.unmodifiableSet(conflicts);
    }

    /**
     * Checks the pinned facts first; delegates to the inner test if the queried fact is not pinned.
     *
     * @param x a {@link Node} object.
     * @param y a {@link Node} object.
     * @param z a {@link Set} of {@link Node} objects.
     * @return the pinned or delegated result.
     * @throws InterruptedException if the delegated test is interrupted.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        String key = key(x, y, z);

        if (pinnedDependent.contains(key)) {
            dependentPinHits.incrementAndGet();
            IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, z), false, 0.0,
                    getAlpha() - 0.0, true);
            if (recordQueries) queryLog.add(new QueryRecord(result.getFact(), 0.0, false, true));
            return result;
        }

        if (pinnedIndependent.contains(key)) {
            independentPinHits.incrementAndGet();
            IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, z), true, 1.0,
                    getAlpha() - 1.0, true);
            if (recordQueries) queryLog.add(new QueryRecord(result.getFact(), 1.0, true, true));
            return result;
        }

        IndependenceResult result = base.checkIndependence(x, y, z);
        if (recordQueries) {
            queryLog.add(new QueryRecord(result.getFact(), result.getPValue(), result.isIndependent(), false));
        }
        return result;
    }

    /**
     * Returns the variables of the inner test.
     *
     * @return the inner test's variables.
     */
    @Override
    public List<Node> getVariables() {
        return base.getVariables();
    }

    /**
     * Returns the sample size of the inner test.
     *
     * @return the inner test's sample size.
     */
    @Override
    public int getSampleSize() {
        return base.getSampleSize();
    }

    /**
     * Returns the alpha of the inner test.
     *
     * @return the inner test's alpha.
     */
    @Override
    public double getAlpha() {
        return base.getAlpha();
    }

    /**
     * Sets the alpha of the inner test.
     *
     * @param alpha the new alpha.
     */
    @Override
    public void setAlpha(double alpha) {
        base.setAlpha(alpha);
    }

    /**
     * Returns the covariance matrix of the inner test.
     *
     * @return the inner test's covariance matrix.
     */
    @Override
    public ICovarianceMatrix getCov() {
        return base.getCov();
    }

    /**
     * Returns the datasets of the inner test.
     *
     * @return the inner test's datasets.
     */
    @Override
    public List<DataSet> getDataSets() {
        return base.getDataSets();
    }

    /**
     * Returns the data model of the inner test.
     *
     * @return the inner test's data model.
     */
    @Override
    public edu.cmu.tetrad.data.DataModel getData() {
        return base.getData();
    }

    /**
     * Returns whether the inner test is verbose.
     *
     * @return the inner test's verbosity.
     */
    @Override
    public boolean isVerbose() {
        return base.isVerbose();
    }

    /**
     * Sets the verbosity of the inner test.
     *
     * @param verbose the new verbosity.
     */
    @Override
    public void setVerbose(boolean verbose) {
        base.setVerbose(verbose);
    }

    /**
     * Returns a description of this wrapper and its inner test.
     *
     * @return the description.
     */
    @Override
    public String toString() {
        return "Pinned(" + base + "; " + pinnedDependent.size() + " dep pins, "
               + pinnedIndependent.size() + " ind pins)";
    }

    /**
     * Canonical name-based key: unordered {x, y} pair plus sorted conditioning names.
     */
    private static String key(Node x, Node y, Set<Node> z) {
        String a = x.getName();
        String b = y.getName();
        if (a.compareTo(b) > 0) {
            String t = a;
            a = b;
            b = t;
        }
        List<String> zs = new ArrayList<>();
        for (Node n : z) zs.add(n.getName());
        Collections.sort(zs);
        return a + "\u0001" + b + "\u0001" + String.join("\u0002", zs);
    }
}
