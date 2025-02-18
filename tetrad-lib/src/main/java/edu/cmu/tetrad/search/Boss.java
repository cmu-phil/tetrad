package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.BesPermutation;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * Implements Best Order Score Search (BOSS). The reference is this:
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
 * <p>
 * BOSS, like GRaSP (see), is characterized by high adjacency and orientation precision (especially) and recall for
 * moderate sample sizes.
 * <p>
 * The algorithm works as follows:
 * <ol>
 *     <li>Start with an arbitrary ordering.</li>
 *     <li>Run the permutation search to find a better ordering.</li>
 *     <li>Project this ordering to a CPDAG.</li>
 *     <li>Optionally, Run BES this CPDAG.
 *     <li>Return this CPDAG.</li>
 * </ol>
 * <p>
 * The optional BES step is needed for correctness, though with large
 * models this has very little effect on the output, since nearly all edges
 * are already oriented, so a parameter is included to turn that step off.
 * <p>
 * Knowledge can be used with this search. If tiered knowledge is used,
 * then the procedure is carried out for each tier separately, given the
 * variables preceding that tier, which allows the Boss algorithm to address
 * tiered (e.g., time series) problems with larger numbers of variables.
 * However, knowledge of required and forbidden edges is correctly implemented
 * for arbitrary such knowledge.
 * <p>
 * A parameter is included to restart the search a certain number of times.
 * The idea is that the goal is to optimize a BIC score, so if several runs
 * are done of the algorithm for the same data, the model with the highest
 * BIC score should be returned and the others ignored.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 * @see PermutationSearch
 * @see Grasp
 * @see Knowledge
 */
public class Boss implements SuborderSearch {
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


    /**
     * This algorithm will work with an arbitrary BIC score.
     *
     * @param score The Score to use.
     */
    public Boss(Score score) {
        this.score = score;
        this.variables = score.getVariables();
        this.parents = new HashMap<>();
        for (Node x : this.variables) {
            this.parents.put(x, new HashSet<>());
        }
    }

    /**
     * Searches a suborder of the variables. The prefix is the set of variables that must precede the suborder. The
     * suborder is the set of variables to be ordered. The gsts is a map from variables to GrowShrinkTrees, which are
     * used to cache scores for the variables. The searchSuborder method will update the suborder to be the best
     * ordering found.
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

        this.bics = new ArrayList<>();
        this.times = new ArrayList<>();

        List<Node> bestSuborder = null;
        double score, bestScore = Double.NEGATIVE_INFINITY;
        boolean improved;

        this.pool = new ForkJoinPool(this.numThreads);

        for (int i = 0; i < this.numStarts; i++) {

            double time = System.currentTimeMillis();

            if ((i == 0 && !this.useDataOrder) || i > 0) {
                shuffle(suborder);
            }

            if (i > 0 && this.resetAfterRS) {
                for (Node root : suborder) {
                    this.gsts.get(root).reset();
                }
            }

            makeValidKnowledgeOrder(suborder);

            do {
                improved = false;
                for (Node x : new ArrayList<>(suborder)) {

                    if (this.verbose && (suborder.size() > 1)) System.out.println(x);

                    if (this.numThreads == 1) improved |= betterMutation(prefix, suborder, x);
                    else {
                        try {
                            improved |= betterMutationAsync(prefix, suborder, x);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                if (this.verbose && (suborder.size() > 1)) {
                    System.out.printf("\nScore: %.3f\n\n", update(prefix, suborder));
                }

            } while (improved);

            if (this.bes != null) bes(prefix, suborder);

            score = update(prefix, suborder);
            time = System.currentTimeMillis() - time;

            if (suborder.size() > 1) {
                this.bics.add(score);
                this.times.add(time);
                if (this.verbose) {
                    System.out.printf("\nRestart: %d\t Score: %.3f\t Time: %.3f\n\n", i, score, time / 1e3);
                }
            }

            if (score > bestScore) {
                bestSuborder = new ArrayList<>(suborder);
                bestScore = score;
            }
        }

        if (this.numThreads > 1) this.pool.shutdown();

        suborder.clear();

        if (bestSuborder != null) {
            suborder.addAll(bestSuborder);
        }

        update(prefix, suborder);
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
     * Returns the map from nodes to the sets of their parents.
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

    /**
     * This method asynchronously performs a better mutation operation on the given suborder of nodes. It takes a prefix
     * of nodes that must precede the suborder, a suborder of nodes to be ordered, and a node to be moved in the
     * suborder. It returns true if the suborder was modified and false otherwise.
     *
     * @param prefix   The list of nodes that must precede the suborder.
     * @param suborder The list of nodes to be ordered.
     * @param x        The node to be moved in the suborder.
     * @return true if the suborder was modified, false otherwise.
     * @throws InterruptedException if any
     */
    private boolean betterMutationAsync(List<Node> prefix, List<Node> suborder, Node x) throws InterruptedException {
        List<Callable<Void>> tasks = new ArrayList<>();

        double[] scores = new double[suborder.size()];
        double[] with = new double[suborder.size() - 1];
        double[] without = new double[suborder.size() - 1];

        Set<Node> Z = new HashSet<>(prefix);
        int i = 0, curr = 0;

        tasks.add(new Trace(this.gsts.get(x), this.all, Z, scores, i));

        for (Node z : suborder) {
            if (Thread.currentThread().isInterrupted()) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }
            if (this.knowledge.isRequired(x.getName(), z.getName())) break;
            if (x == z) {
                curr = i;
                continue;
            }

            Z.add(x);
            tasks.add(new Trace(this.gsts.get(z), this.all, Z, with, i));
            Z.remove(x);
            tasks.add(new Trace(this.gsts.get(z), this.all, Z, without, i));
            Z.add(z);
            tasks.add(new Trace(this.gsts.get(x), this.all, Z, scores, ++i));
        }

        shuffle(tasks);
        try {
            pool.invokeAll(tasks);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        if (this.resetAfterBM) this.gsts.get(x).reset();
        double runningScore = 0;

        for (i = with.length - 1; i >= 0; i--) {
            runningScore += with[i];
            scores[i] += runningScore;
        }

        runningScore = 0;

        for (i = 0; i < without.length; i++) {
            runningScore += without[i];
            scores[i + 1] += runningScore;
        }

        int best = curr;

        for (i = scores.length - 1; i >= 0; i--) {
            if (this.knowledge.isRequired(suborder.get(i).getName(), x.getName())) break;
            if (scores[i] != Double.POSITIVE_INFINITY && scores[i] + 1e-6 > scores[best]) best = i;
        }

        if (scores[curr] + 1e-6 > scores[best]) return false;
        suborder.remove(x);
        suborder.add(best, x);

        return true;
    }

    /**
     * Reorders a suborder of nodes in a more optimal way.
     *
     * @param prefix   The list of nodes that must precede the suborder.
     * @param suborder The list of nodes to be ordered.
     * @param x        The node to be moved in the suborder.
     * @return true if the suborder was modified, false otherwise.
     */
    private boolean betterMutation(List<Node> prefix, List<Node> suborder, Node x) {
        ListIterator<Node> itr = suborder.listIterator();
        double[] scores = new double[suborder.size() + 1];
        Set<Node> Z = new HashSet<>(prefix);

        int i = 0;
        double score = 0;
        int curr = 0;

        while (itr.hasNext()) {
            if (Thread.currentThread().isInterrupted()) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }

            Node z = itr.next();

            if (this.knowledge.isRequired(x.getName(), z.getName())) {
                itr.previous();
                break;
            }

            scores[i++] = this.gsts.get(x).trace(Z, this.all) + score;
            if (z != x) {
                score += this.gsts.get(z).trace(Z, this.all);
                Z.add(z);
            } else curr = i - 1;
        }

        scores[i] = this.gsts.get(x).trace(Z, this.all) + score;
        int best = i;

        Z.add(x);
        score = 0;

        while (itr.hasPrevious()) {
            Node z = itr.previous();

            if (this.knowledge.isRequired(z.getName(), x.getName())) break;

            if (z != x) {
                Z.remove(z);
                score += gsts.get(z).trace(Z, this.all);
            }

            scores[--i] += score;
            if (scores[i] + 1e-6 > scores[best]) best = i;
        }

        if (scores[curr] + 1e-6 > scores[best]) return false;
        if (best > curr) best--;
        suborder.remove(x);
        suborder.add(best, x);

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

        Graph graph = PermutationSearch.getGraph(all, this.parents, this.knowledge, true);
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
}
