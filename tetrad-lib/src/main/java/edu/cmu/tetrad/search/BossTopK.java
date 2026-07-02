/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.BesPermutation;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * Implements Best Order Score Search (BOSS), extended to return the top <i>k</i> models by score. The reference for
 * BOSS is this:
 * <p>
 * Andrews, B., Ramsey, J., Sanchez Romero, R., Camchong, J., &amp; Kummerfeld, E. (2024). Fast Scalable and Accurate
 * Discovery of DAGs Using the Best Order Score Search and Grow Shrink Trees. Advances in Neural Information Processing
 * Systems, 36.
 * <p>
 * The BOSS algorithm is based on the idea that implied DAGs for permutations are most optimal in their BIC scores when
 * the variables in the permutations are ordered so that that causes in the models come before effects for some DAG in
 * the true Markov equivalence class.
 * <p>
 * This algorithm is implemented as a "plugin-in" algorithm to a PermutationSearch object (see), which deals with
 * certain details of knowledge handling that are common to different permutation searches.
 *
 * <h3>Top-k retention</h3>
 * <p>
 * In addition to the single best ordering, this variant retains the top {@code k} distinct orderings (models) by
 * score. Retention is implemented with a bounded sorted set of {@code (permutation, score)} records, ordered by score
 * ascending with a lexicographic tie-break on the permutation. The tie-break is essential: it keeps distinct
 * permutations that happen to share a score (common near optima) from colliding, and it makes re-discovering the same
 * permutation an idempotent insert (free de-duplication). A record is admitted when the set is under-full, or when its
 * score strictly exceeds the current minimum; the minimum is evicted whenever the set would exceed {@code k}. The
 * models retained are the <i>converged local optima</i> reached by each hill-climb run (one per run), not every
 * transient ordering visited.
 *
 * <h3>Split / defer (the "delta" mechanism)</h3>
 * <p>
 * A {@code delta} &ge; 0 controls a search-splitting rule. Within a {@code betterMutation} step, BOSS already computes
 * the total permutation score for every valid insertion slot of the node being moved and then moves it to the best
 * slot. When another slot's total is strictly below the pursued best by less than {@code delta}, that slot's ordering
 * is "indistinguishable" from the one pursued; the search is split by deferring that alternative ordering (with its
 * score) onto a queue. The current ("stay") slot is deliberately <em>not</em> deferred, because re-running a
 * deterministic hill-climb from the pre-move ordering would simply redo the same move; only genuinely different
 * orderings are deferred. Each hill-climb run explores one ordering to convergence, recording its optimum into the
 * top-k set and enqueuing any near-tied alternatives. When a run finishes, an ordering is popped from the deferred
 * queue and the search continues from it, <em>reusing the same GrowShrinkTree cache</em>. The search as a whole
 * finishes only when the deferred queue is empty (or a safety cap {@code maxRuns} is reached). The exact split
 * predicate is isolated in {@link #maybeDefer} for easy retuning.
 * <p>
 * Notes: with {@code delta == 0} and {@code numStarts == 1} exactly one model is produced (ordinary BOSS); diversity
 * comes from {@code delta > 0} and/or additional restarts. Deferred orderings are explored best-first (by their
 * recorded score). Top-k retention is defined for a single suborder search (the usual no-tier case, where the suborder
 * is the whole variable set); under within-tier-forbidden tiered knowledge the set reflects the last suborder searched.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 * @see PermutationSearchTopK
 * @see SuborderSearchTopK
 * @see Knowledge
 */
public class BossTopK implements SuborderSearchTopK {
    /**
     * The score.
     */
    private final Score score;
    /**
     * The variables.
     */
    private final List<Node> variables;
    /**
     * The parents.
     */
    private final Map<Node, Set<Node>> parents;
    /**
     * A fixed index for each variable, used to encode permutations as int[] arrays for the Python (JPype) client.
     */
    private final Map<Node, Integer> index;
    /**
     * The grow-shrink trees.
     */
    private Map<Node, GrowShrinkTree> gsts;
    /**
     * The set of all variables.
     */
    private Set<Node> all;
    /**
     * The pool for parallelism.
     */
    private ForkJoinPool pool;
    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The BES algorithm.
     */
    private BesPermutation bes = null;
    /**
     * The number of random starts to use.
     */
    private int numStarts = 1;
    /**
     * True if the order of the variables in the data should be used for an initial best-order search, false if a random
     * permutation should be used. (Subsequence automatic best order runs will use random permutations.) This is
     * included so that the algorithm will be capable of outputting the same results with the same data without any
     * randomness.
     */
    private boolean useDataOrder = true;
    /**
     * True if the grow-shrink trees should be reset after each best-mutation step.
     */
    private boolean resetAfterBM = false;
    /**
     * True if the grow-shrink trees should be reset after each restart.
     */
    private boolean resetAfterRS = true;
    /**
     * The number of threads to use.
     */
    private int numThreads = 1;
    /**
     * True if verbose output should be printed.
     */
    private List<Double> bics;
    /**
     * The BIC scores.
     */
    private List<Double> times;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose = false;

    // ---- Top-k / split state -------------------------------------------------------------------------------------

    /**
     * The number of top models to retain. 1 recovers ordinary BOSS.
     */
    private int k = 1;
    /**
     * The split threshold. 0 disables splitting.
     */
    private double delta = 0.0;
    /**
     * A hard cap on the total number of hill-climb runs, to guarantee termination regardless of delta.
     */
    private int maxRuns = 10000;
    /**
     * If true, a model's identity for top-k retention is its canonical CPDAG rather than its permutation, so that
     * distinct permutations in the same Markov equivalence class (same CPDAG, same score) are not double-counted. If
     * false, identity is the permutation itself.
     */
    private boolean dedupByCpdag = false;
    /**
     * If true, every ordering visited across all branches (including orderings that are suboptimal within their own
     * branch, i.e. non-converged intermediates) is offered to the top-k pool, not just each branch's converged
     * optimum. If false (the default), only the converged optimum of each branch is offered.
     */
    private boolean optimalAcrossBranches = false;
    /**
     * The bounded sorted set of top models, ordered by score ascending (so first() is the current worst).
     */
    private TreeSet<ScoredPermutation> topK;
    /**
     * Fast identity membership for the top-k set. Each key is either a canonical permutation string or, when
     * {@link #dedupByCpdag} is set, a canonical CPDAG string.
     */
    private Set<String> topKKeys;
    /**
     * The deferred queue of near-tied alternative orderings, explored best-first (highest score first).
     */
    private PriorityQueue<ScoredPermutation> deferred;
    /**
     * Canonical keys of orderings already run as a hill-climb start (prevents redundant/looping re-runs).
     */
    private Set<String> visited;
    /**
     * Canonical keys of orderings currently sitting in the deferred queue (prevents duplicate enqueues).
     */
    private Set<String> enqueued;
    /**
     * The prefix for the current suborder search (fixed for the duration of a searchSuborder call).
     */
    private List<Node> prefixList = new ArrayList<>();
    /**
     * The ranked (descending by score) snapshot of the top-k models, materialized at the end of a search for the
     * public accessor methods.
     */
    private List<ScoredPermutation> ranked = new ArrayList<>();
    /**
     * The number of hill-climb runs performed in the current search.
     */
    private int runCount = 0;


    /**
     * This algorithm will work with an arbitrary BIC score.
     *
     * @param score The Score to use.
     */
    public BossTopK(Score score) {
        this.score = score;
        this.variables = score.getVariables();
        this.parents = new HashMap<>();
        this.index = new HashMap<>();
        for (int i = 0; i < this.variables.size(); i++) {
            Node x = this.variables.get(i);
            this.parents.put(x, new HashSet<>());
            this.index.put(x, i);
        }
    }

    /**
     * Searches a suborder of the variables. The prefix is the set of variables that must precede the suborder. The
     * suborder is the set of variables to be ordered. The gsts is a map from variables to GrowShrinkTrees, which are
     * used to cache scores for the variables. The searchSuborder method will update the suborder to be the best
     * ordering found, and (as a side effect) populate the top-k structures.
     *
     * @param prefix   The prefix of the suborder.
     * @param suborder The suborder.
     * @param gsts     The GrowShrinkTree being used to do caching of scores.
     * @throws InterruptedException if any
     */
    @Override
    public void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts) throws InterruptedException {
        assert this.numStarts > 0;
        this.gsts = gsts;
        this.all = new HashSet<>(prefix);
        this.all.addAll(suborder);
        this.prefixList = new ArrayList<>(prefix);

        this.bics = new ArrayList<>();
        this.times = new ArrayList<>();

        // Reset top-k / split state for this suborder search.
        this.topK = new TreeSet<>();
        this.topKKeys = new HashSet<>();
        this.deferred = new PriorityQueue<>((a, b) -> Double.compare(b.score, a.score)); // best-first
        this.visited = new HashSet<>();
        this.enqueued = new HashSet<>();
        this.ranked = new ArrayList<>();
        this.runCount = 0;

        List<Node> bestSuborder = null;
        double score, bestScore = Double.NEGATIVE_INFINITY;

        this.pool = new ForkJoinPool(this.numThreads);

        try {
            // ---- Initial restarts (as in ordinary BOSS), each seeding the top-k / deferred machinery. ----
            for (int i = 0; i < this.numStarts; i++) {

                double time = System.currentTimeMillis();

                List<Node> start = new ArrayList<>(suborder);

                if ((i == 0 && !this.useDataOrder) || i > 0) {
                    shuffle(start);
                }

                if (i > 0 && this.resetAfterRS) {
                    for (Node root : start) {
                        this.gsts.get(root).reset();
                    }
                }

                makeValidKnowledgeOrder(start);

                score = runOneHillClimb(prefix, start);
                time = System.currentTimeMillis() - time;

                if (start.size() > 1) {
                    this.bics.add(score);
                    this.times.add(time);
                    if (this.verbose) {
                        TetradLogger.getInstance().log(String.format("Restart: %d\t Score: %.3f\t Time: %.3f", i, score, time / 1e3));
                    }
                }

                if (score > bestScore) {
                    bestSuborder = new ArrayList<>(start);
                    bestScore = score;
                }
            }

            // ---- Drain the deferred queue, reusing the same GrowShrinkTree cache. ----
            while (!this.deferred.isEmpty() && this.runCount < this.maxRuns) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted");
                }

                ScoredPermutation d = this.deferred.poll();
                String key = key(d.perm);
                this.enqueued.remove(key);
                if (this.visited.contains(key)) continue;

                List<Node> start = suborderFromPerm(d.perm);
                makeValidKnowledgeOrder(start);

                score = runOneHillClimb(prefix, start);

                if (score > bestScore) {
                    bestSuborder = new ArrayList<>(start);
                    bestScore = score;
                }
            }
        } finally {
            this.pool.shutdownNow();
        }

        if (this.numThreads > 1) this.pool.shutdown();

        // Set the suborder to the overall best (the top model) so a PermutationSearch on this returns the best graph,
        // and refill this.parents for that model (later runs may have overwritten it).
        suborder.clear();
        if (bestSuborder != null) {
            suborder.addAll(bestSuborder);
        }
        update(prefix, suborder);

        // Materialize the ranked (descending) snapshot for the public accessors.
        this.ranked = new ArrayList<>(this.topK);
        Collections.reverse(this.ranked); // now index 0 == highest score
    }

    /**
     * Runs a single hill-climb from the given starting order to convergence, records the resulting optimum into the
     * top-k set, and (via {@link #maybeDefer}) enqueues any near-tied alternative orderings encountered. The order list
     * is mutated in place to the converged ordering.
     *
     * @param prefix The fixed prefix.
     * @param order  The starting order (mutated to the converged order).
     * @return The total score of the converged order.
     * @throws InterruptedException if any
     */
    private double runOneHillClimb(List<Node> prefix, List<Node> order) throws InterruptedException {
        this.runCount++;
        this.visited.add(key(fullPerm(order)));

        // When collecting optima across branches, the starting ordering is itself a candidate model.
        if (this.optimalAcrossBranches) {
            double s0 = update(prefix, order);
            recordTopK(order, s0);
        }

        int maxIter = order.size() * order.size() + 1;
        int iter = 0;
        boolean improved;

        do {
            improved = false;
            for (Node x : new ArrayList<>(order)) {
                if (this.verbose && (order.size() > 1)) TetradLogger.getInstance().log(x.toString());

                boolean moved;
                if (this.numThreads == 1) moved = betterMutation(prefix, order, x);
                else moved = betterMutationAsync(prefix, order, x);
                improved |= moved;

                // When collecting optima across branches, offer each intermediate (within-branch suboptimal) ordering.
                if (this.optimalAcrossBranches && moved) {
                    double sMid = update(prefix, order);
                    recordTopK(order, sMid);
                }
            }

            if (this.verbose && (order.size() > 1)) {
                TetradLogger.getInstance().log(String.format("Score: %.3f", update(prefix, order)));
            }

            if (++iter >= maxIter) {
                TetradLogger.getInstance().log("Warning: BOSS hit max iterations, terminating early.");
                break;
            }
        } while (improved);

        if (this.bes != null) bes(prefix, order);

        double s = update(prefix, order); // fills this.parents for THIS order
        recordTopK(order, s);             // snapshots this.parents into the retained record (always records the optimum)
        return s;
    }

    /**
     * Sets up BOSS to use the BES algorithm to render BOSS correct under the faithfulness assumption.
     *
     * @param use True if BES should be used.
     */
    public void setUseBes(boolean use) {
        this.bes = null;
        if (use) {
            this.bes = new BesPermutation(this.score);
            this.bes.setVerbose(false);
            this.bes.setKnowledge(knowledge);
        }
    }

    /**
     * Sets the knowledge to be used for the search.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;

        if (this.bes != null) {
            this.bes.setKnowledge(knowledge);
        }
    }

    /**
     * Sets the number of random starts to use. The model with the best score from these restarts will be reported.
     *
     * @param numStarts The number of random starts to use.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets whether the grow-shrink trees should be reset after each best-mutation step.
     *
     * @param reset True if so.
     */
    public void setResetAfterBM(boolean reset) {
        this.resetAfterBM = reset;
    }

    /**
     * Sets whether the grow-shrink trees should be reset after each restart.
     *
     * @param reset True if so.
     */
    public void setResetAfterRS(boolean reset) {
        this.resetAfterRS = reset;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of threads to use.
     *
     * @param numThreads The number of threads to use. Must be at least 1.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) throw new IllegalArgumentException("The number of threads must be at least 1.");
        this.numThreads = numThreads;
    }

    /**
     * Returns the variables.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns the map from nodes to the sets of their parents (for the top model after a search).
     */
    @Override
    public Map<Node, Set<Node>> getParents() {
        return this.parents;
    }

    /**
     * Returns the score being used for the search.
     */
    @Override
    public Score getScore() {
        return this.score;
    }

    /**
     * Returns the BIC scores.
     *
     * @return This list.
     */
    public List<Double> getBics() {
        return this.bics;
    }

    /**
     * Returns the times.
     *
     * @return This list.
     */
    public List<Double> getTimes() {
        return this.times;
    }

    /**
     * True if the order of the variables in the data should be used for an initial best-order search, false if a random
     * permutation should be used. (Subsequence automatic best order runs will use random permutations.) This is
     * included so that the algorithm will be capable of outputting the same results with the same data without any
     * randomness.
     *
     * @param useDataOrder True if so
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    // ---- Top-k accessors / setters ------------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTopK(int k) {
        if (k < 1) throw new IllegalArgumentException("k must be at least 1.");
        this.k = k;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDelta(double delta) {
        if (delta < 0) throw new IllegalArgumentException("delta must be >= 0.");
        this.delta = delta;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxRuns(int maxRuns) {
        if (maxRuns < 1) throw new IllegalArgumentException("maxRuns must be at least 1.");
        this.maxRuns = maxRuns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDedupByCpdag(boolean dedupByCpdag) {
        this.dedupByCpdag = dedupByCpdag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOptimalAcrossBranches(boolean optimalAcrossBranches) {
        this.optimalAcrossBranches = optimalAcrossBranches;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumTopK() {
        return this.ranked.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int[] getTopKPermutation(int i) {
        return this.ranked.get(i).perm.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getTopKScore(int i) {
        return this.ranked.get(i).score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Node, Set<Node>> getTopKParents(int i) {
        Map<Node, Set<Node>> src = this.ranked.get(i).parents;
        Map<Node, Set<Node>> out = new HashMap<>();
        // Provide a complete map over all variables so graph construction never sees a null parent set.
        for (Node x : this.variables) {
            Set<Node> ps = (src == null) ? null : src.get(x);
            out.put(x, ps == null ? new HashSet<>() : new HashSet<>(ps));
        }
        return out;
    }

    // ---- Top-k / split internals --------------------------------------------------------------------------------

    /**
     * Records the given converged order (with score s) into the bounded top-k set. A model's identity is its
     * permutation, or (when {@link #dedupByCpdag} is set) its canonical CPDAG, so that distinct permutations in the
     * same Markov equivalence class collapse to a single entry. Admission: the set is under-full, or s strictly exceeds
     * the current minimum. On admission a snapshot of the current {@code this.parents} (which reflects this order,
     * having just been produced by {@link #update}) is stored, and the set is trimmed to k.
     *
     * @param order The converged suborder ordering.
     * @param s     Its total score.
     */
    private void recordTopK(List<Node> order, double s) {
        int[] perm = fullPerm(order);

        Map<Node, Set<Node>> snap = new HashMap<>();
        for (Node x : this.all) {
            snap.put(x, new HashSet<>(this.parents.get(x)));
        }

        // Identity key: permutation, or canonical CPDAG when deduping by equivalence class.
        String idKey;
        if (this.dedupByCpdag) {
            List<Node> nodes = new ArrayList<>(this.prefixList);
            nodes.addAll(order);
            Graph cpdag = PermutationSearchTopK.getGraph(nodes, this.parents, this.knowledge, true, false);
            idKey = cpdagKey(cpdag);
        } else {
            idKey = key(perm);
        }

        if (this.topKKeys.contains(idKey)) return; // already have this model (same perm, or same CPDAG)

        boolean admit = this.topK.size() < this.k || s > this.topK.first().score;
        if (!admit) return;

        this.topK.add(new ScoredPermutation(perm, s, snap, idKey));
        this.topKKeys.add(idKey);

        while (this.topK.size() > this.k) {
            ScoredPermutation removed = this.topK.pollFirst(); // evict current worst
            if (removed != null) this.topKKeys.remove(removed.idKey);
        }
    }

    /**
     * Builds a canonical string key for a CPDAG, invariant to internal edge ordering, so that the same equivalence
     * class always maps to the same key. Directed edges are recorded as {@code tail>head}; other (e.g. undirected)
     * edges are recorded with endpoint-tagged, name-sorted tokens. Tokens are sorted before joining.
     *
     * @param g The graph (expected to be a CPDAG).
     * @return Its canonical key.
     */
    private static String cpdagKey(Graph g) {
        List<String> toks = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            Node a = e.getNode1();
            Node b = e.getNode2();
            Endpoint ea = e.getEndpoint1();
            Endpoint eb = e.getEndpoint2();

            String tok;
            if (ea == Endpoint.TAIL && eb == Endpoint.ARROW) {
                tok = a.getName() + ">" + b.getName();
            } else if (ea == Endpoint.ARROW && eb == Endpoint.TAIL) {
                tok = b.getName() + ">" + a.getName();
            } else {
                String x = a.getName();
                String y = b.getName();
                if (x.compareTo(y) <= 0) {
                    tok = x + "-" + y + "-" + ea + eb;
                } else {
                    tok = y + "-" + x + "-" + eb + ea;
                }
            }
            toks.add(tok);
        }
        Collections.sort(toks);
        return String.join("|", toks);
    }

    /**
     * The split predicate, isolated so it can be retuned in one place. Given the per-slot total scores computed by a
     * betterMutation step (before the best slot is applied), enqueue every alternative insertion slot whose total is
     * strictly below the pursued best by less than delta. The pursued best slot and the "stay" (original) slot are
     * excluded; the latter because re-running from the pre-move ordering would deterministically redo this move.
     *
     * @param prefix       The fixed prefix.
     * @param suborder     The current order (node to move still in place).
     * @param nodeToMove   The node being moved.
     * @param slotScores   Total permutation scores indexed by insertion slot.
     * @param lastValidSlot The highest valid slot index.
     * @param originalSlot The slot the node currently occupies.
     * @param bestSlot     The pursued best slot (unadjusted).
     */
    private void maybeDefer(List<Node> prefix, List<Node> suborder, Node nodeToMove,
                            double[] slotScores, int lastValidSlot, int originalSlot, int bestSlot) {
        if (this.delta <= 0.0) return;
        if (this.runCount >= this.maxRuns) return;

        double best = slotScores[bestSlot];

        for (int j = 0; j <= lastValidSlot; j++) {
            if (j == bestSlot || j == originalSlot) continue;

            double diff = best - slotScores[j];
            if (diff > 0.0 && diff < this.delta) {
                // Build the alternative ordering with nodeToMove at slot j (mirroring the move's index adjustment).
                List<Node> alt = new ArrayList<>(suborder);
                alt.remove(nodeToMove);
                int pos = (j > originalSlot) ? j - 1 : j;
                if (pos < 0) pos = 0;
                if (pos > alt.size()) pos = alt.size();
                alt.add(pos, nodeToMove);

                enqueueDeferred(prefix, alt, slotScores[j]);
            }
        }
    }

    /**
     * Enqueues a deferred alternative starting order, de-duplicating against orderings already run or already queued.
     *
     * @param prefix The fixed prefix.
     * @param alt    The alternative suborder ordering.
     * @param s      Its score (as computed for the alternative slot).
     */
    private void enqueueDeferred(List<Node> prefix, List<Node> alt, double s) {
        int[] perm = fullPerm(alt);
        String pk = key(perm);
        if (this.visited.contains(pk) || this.enqueued.contains(pk)) return;

        this.deferred.add(new ScoredPermutation(perm, s, null, pk));
        this.enqueued.add(pk);
    }

    /**
     * Encodes a full ordering (prefix followed by the given suborder) as an int[] of variable indices.
     *
     * @param order The suborder ordering.
     * @return The full permutation as variable indices.
     */
    private int[] fullPerm(List<Node> order) {
        int[] perm = new int[this.prefixList.size() + order.size()];
        int i = 0;
        for (Node x : this.prefixList) perm[i++] = this.index.get(x);
        for (Node x : order) perm[i++] = this.index.get(x);
        return perm;
    }

    /**
     * Reconstructs the suborder (the portion after the fixed prefix) from a full permutation.
     *
     * @param perm The full permutation of variable indices.
     * @return The suborder as a list of nodes.
     */
    private List<Node> suborderFromPerm(int[] perm) {
        List<Node> order = new ArrayList<>();
        for (int j = this.prefixList.size(); j < perm.length; j++) {
            order.add(this.variables.get(perm[j]));
        }
        return order;
    }

    /**
     * A canonical string key for a permutation, used for de-duplication.
     *
     * @param perm The permutation.
     * @return Its canonical key.
     */
    private static String key(int[] perm) {
        return Arrays.toString(perm);
    }

    /**
     * This method asynchronously performs a better mutation operation on the given suborder of nodes. It takes a prefix
     * of nodes that must precede the suborder, a suborder of nodes to be ordered, and a node to be moved in the
     * suborder. It returns true if the suborder was modified and false otherwise.
     *
     * @param prefix   The list of nodes that must precede the suborder.
     * @param suborder The list of nodes to be ordered.
     * @param nodeToMove The node to be moved in the suborder.
     * @return true if the suborder was modified, false otherwise.
     * @throws InterruptedException if any
     */
    private boolean betterMutationAsync(List<Node> prefix, List<Node> suborder, Node nodeToMove)
            throws InterruptedException {

        List<Callable<Void>> tasks = new ArrayList<>();

        int numNodes = suborder.size();
        if (numNodes <= 1) return false;

        double[] insertionScores = new double[numNodes + 1];
        double[] scoreWithNode = new double[numNodes];
        double[] scoreWithoutNode = new double[numNodes];

        Set<Node> currentPrefix = new HashSet<>(prefix);

        int index = 0;
        int originalPosition = 0;

        // ---- Forward scan: identify valid insertion points and collect scoring tasks ----
        ListIterator<Node> iterator = suborder.listIterator();

        tasks.add(new Trace(this.gsts.get(nodeToMove), this.all, currentPrefix, insertionScores, index));

        while (iterator.hasNext()) {

            if (Thread.currentThread().isInterrupted()) {
                pool.shutdownNow();
                throw new InterruptedException();
            }

            Node neighborNode = iterator.next();

            // Constraint: nodeToMove must precede neighborNode.
            if (this.knowledge.isRequired(nodeToMove.getName(), neighborNode.getName())) {
                iterator.previous(); // <-- critical rewind to match sequential behavior
                break;
            }

            if (neighborNode == nodeToMove) {
                originalPosition = index;
                continue;
            }

            currentPrefix.add(nodeToMove);
            tasks.add(new Trace(this.gsts.get(neighborNode), this.all, currentPrefix, scoreWithNode, index));
            currentPrefix.remove(nodeToMove);

            tasks.add(new Trace(this.gsts.get(neighborNode), this.all, currentPrefix, scoreWithoutNode, index));

            currentPrefix.add(neighborNode);
            tasks.add(new Trace(this.gsts.get(nodeToMove), this.all, currentPrefix, insertionScores, ++index));
        }

        int lastValidIndex = index;

        // ---- Execute tasks in parallel ----
        try {
            pool.invokeAll(tasks);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        if (this.resetAfterBM) this.gsts.get(nodeToMove).reset();

        // ---- Accumulate relative scores from right (with nodeToMove) ----
        double runningScoreSum = 0.0;
        for (int j = lastValidIndex - 1; j >= 0; j--) {
            runningScoreSum += scoreWithNode[j];
            insertionScores[j] += runningScoreSum;
        }

        // ---- Accumulate relative scores from left (without nodeToMove) ----
        runningScoreSum = 0.0;
        for (int j = 0; j < lastValidIndex; j++) {
            runningScoreSum += scoreWithoutNode[j];
            insertionScores[j + 1] += runningScoreSum;
        }

        // ---- Backward constraint scan to find the best insertion position ----
        int bestPosition = originalPosition;

        for (int j = lastValidIndex; j >= 0; j--) {

            // Constraint: neighborNode must precede nodeToMove.
            if (j < suborder.size()) {
                Node neighborNode = suborder.get(j);
                if (this.knowledge.isRequired(neighborNode.getName(), nodeToMove.getName())) break;
            }

            if (insertionScores[j] + 1e-6 > insertionScores[bestPosition]) bestPosition = j;
        }

        if (insertionScores[originalPosition] + 1e-6 > insertionScores[bestPosition]) return false;

        // Split: defer near-tied alternative orderings (uses unadjusted slot indices).
        maybeDefer(prefix, suborder, nodeToMove, insertionScores, lastValidIndex, originalPosition, bestPosition);

        if (bestPosition > originalPosition) bestPosition--;

        suborder.remove(nodeToMove);
        suborder.add(bestPosition, nodeToMove);

        return true;
    }

    /**
     * Reorders a suborder of nodes in a more optimal way.
     *
     * @param prefix   The list of nodes that must precede the suborder.
     * @param suborder The list of nodes to be ordered.
     * @param nodeToMove The node to be moved in the suborder.
     * @return true if the suborder was modified, false otherwise.
     */
    private boolean betterMutation(List<Node> prefix, List<Node> suborder, Node nodeToMove) throws InterruptedException {
        ListIterator<Node> iterator = suborder.listIterator();
        double[] scores = new double[suborder.size() + 1];
        Set<Node> currentPrefix = new HashSet<>(prefix);

        int index = 0;
        double scoreSum = 0;
        int originalPosition = 0;

        // Forward pass: find valid insertion positions from the left up to any required edge constraint.
        while (iterator.hasNext()) {
            if (Thread.currentThread().isInterrupted()) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                throw new InterruptedException("Interrupted");
            }

            Node neighborNode = iterator.next();

            // Constraint: nodeToMove must precede neighborNode.
            if (this.knowledge.isRequired(nodeToMove.getName(), neighborNode.getName())) {
                iterator.previous();
                break;
            }

            scores[index++] = this.gsts.get(nodeToMove).trace(currentPrefix, this.all) + scoreSum;
            if (neighborNode != nodeToMove) {
                scoreSum += this.gsts.get(neighborNode).trace(currentPrefix, this.all);
                currentPrefix.add(neighborNode);
            } else {
                originalPosition = index - 1;
            }
        }

        // The position after the last valid neighborNode.
        scores[index] = this.gsts.get(nodeToMove).trace(currentPrefix, this.all) + scoreSum;
        int bestPosition = index;
        int lastValidIndex = index; // captured before the backward pass mutates 'index'

        currentPrefix.add(nodeToMove);
        scoreSum = 0;

        // Backward pass: find valid insertion positions from the right up to any required edge constraint.
        while (iterator.hasPrevious()) {
            Node neighborNode = iterator.previous();

            // Constraint: neighborNode must precede nodeToMove.
            if (this.knowledge.isRequired(neighborNode.getName(), nodeToMove.getName())) {
                break;
            }

            if (neighborNode != nodeToMove) {
                currentPrefix.remove(neighborNode);
                scoreSum += gsts.get(neighborNode).trace(currentPrefix, this.all);
            }

            scores[--index] += scoreSum;
            if (scores[index] + 1e-6 > scores[bestPosition]) {
                bestPosition = index;
            }
        }

        if (scores[originalPosition] + 1e-6 > scores[bestPosition]) {
            return false;
        }

        // Split: defer near-tied alternative orderings (uses unadjusted slot indices).
        maybeDefer(prefix, suborder, nodeToMove, scores, lastValidIndex, originalPosition, bestPosition);

        if (bestPosition > originalPosition) {
            bestPosition--;
        }

        suborder.remove(nodeToMove);
        suborder.add(bestPosition, nodeToMove);

        return true;
    }

    /**
     * Runs the Backward Equivalence Search from GES.
     *
     * @param prefix   The list of nodes that must precede the suborder.
     * @param suborder The list of nodes to be ordered.
     * @throws InterruptedException if any
     */
    private void bes(List<Node> prefix, List<Node> suborder) throws InterruptedException {
        List<Node> all = new ArrayList<>(prefix);
        all.addAll(suborder);

        Graph graph = PermutationSearchTopK.getGraph(all, this.parents, this.knowledge, true);
        this.bes.bes(graph, all, suborder);
        graph.paths().makeValidOrder(suborder);
    }

    /**
     * Updates the suborder of variables by adding each variable from the suborder to the prefix and computing the
     * score.
     *
     * @param prefix   The list of variables that must precede the suborder.
     * @param suborder The list of variables to be ordered.
     * @return The score after updating the suborder.
     */
    private double update(List<Node> prefix, List<Node> suborder) {
        double score = 0;

        Set<Node> Z = new HashSet<>(prefix);

        for (Node x : suborder) {
            Set<Node> parents = this.parents.get(x);
            parents.clear();
            score += this.gsts.get(x).trace(Z, this.all, parents);
            Z.add(x);
        }

        return score;
    }

    /**
     * Makes the given knowledge order valid by rearranging the elements in the order list.
     *
     * @param order The list of nodes representing the knowledge order.
     */
    private void makeValidKnowledgeOrder(List<Node> order) {
        if (this.knowledge.isEmpty()) return;

        int index = 0;

        Set<String> tier = new HashSet<>(this.knowledge.getVariablesNotInTiers());
        for (int i = 0; i < order.size(); i++) {
            if (tier.contains(order.get(i).getName())) {
                Node x = order.remove(i);
                order.add(index++, x);
            }
        }

        for (int i = 0; i < this.knowledge.getNumTiers(); i++) {
            tier = new HashSet<>(this.knowledge.getTier(i));
            for (int j = 0; j < order.size(); j++) {
                if (tier.contains(order.get(j).getName())) {
                    Node x = order.remove(j);
                    order.add(index++, x);
                }
            }
        }

        for (int i = 1; i < order.size(); i++) {
            String a = order.get(i).getName();
            for (int j = 0; j < i; j++) {
                String b = order.get(j).getName();
                if (this.knowledge.isRequired(a, b)) {
                    Node x = order.remove(i);
                    order.add(j, x);
                    break;
                }
            }
        }
    }


    // alter this code so that it roughly obeys tiers.

    /**
     * This class represents a callable task for computing the score for a given set of variables.
     */
    private static class Trace implements Callable<Void> {
        private final GrowShrinkTree gst;
        private final Set<Node> all;
        private final Set<Node> prefix;
        private final double[] scores;
        private final int index;

        Trace(GrowShrinkTree gst, Set<Node> all, Set<Node> prefix, double[] scores, int index) {
            this.gst = gst;
            this.all = all;
            this.prefix = new HashSet<>(prefix);
            this.scores = scores;
            this.index = index;
        }

        /**
         * Computes the score for the given set of variables.
         *
         * @return The score.
         */
        @Override
        public Void call() {
            if (!Thread.currentThread().isInterrupted()) {
                double score = gst.trace(this.prefix, this.all);
                this.scores[index] = score;
            }

            return null;
        }
    }

    /**
     * A permutation together with its score and (optionally) the parent map of its implied model. Ordering is by score
     * ascending, with a lexicographic tie-break on the permutation so that distinct permutations sharing a score do not
     * collide in a sorted set (and identical permutations compare equal, giving free de-duplication).
     */
    private static final class ScoredPermutation implements Comparable<ScoredPermutation> {
        private final int[] perm;
        private final double score;
        private final Map<Node, Set<Node>> parents; // null for deferred seeds
        private final String idKey;                 // permutation key, or CPDAG key when deduping by equivalence class

        ScoredPermutation(int[] perm, double score, Map<Node, Set<Node>> parents, String idKey) {
            this.perm = perm;
            this.score = score;
            this.parents = parents;
            this.idKey = idKey;
        }

        @Override
        public int compareTo(ScoredPermutation o) {
            int c = Double.compare(this.score, o.score);
            if (c != 0) return c;
            int n = Math.min(this.perm.length, o.perm.length);
            for (int i = 0; i < n; i++) {
                if (this.perm[i] != o.perm[i]) return Integer.compare(this.perm[i], o.perm[i]);
            }
            return Integer.compare(this.perm.length, o.perm.length);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ScoredPermutation)) return false;
            ScoredPermutation o = (ScoredPermutation) obj;
            return this.score == o.score && Arrays.equals(this.perm, o.perm);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(this.perm) + Double.hashCode(this.score);
        }
    }
}
