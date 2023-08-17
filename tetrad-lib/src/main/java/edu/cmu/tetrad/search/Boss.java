package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.BesPermutation;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * <p>Implements Best Order Score Search (BOSS). The following references are relevant:</p>
 *
 * <p>Lam, W. Y., Andrews, B., & Ramsey, J. (2022, August). Greedy relaxations of the sparsest permutation algorithm.
 * In Uncertainty in Artificial Intelligence (pp. 1052-1062). PMLR.</p>
 *
 * <p>Teyssier, M., & Koller, D. (2012). Ordering-based search: A simple and effective algorithm for learning Bayesian
 * networks. arXiv preprint arXiv:1207.1429.</p>
 *
 * <p>Solus, L., Wang, Y., & Uhler, C. (2021). Consistency guarantees for greedy permutation-based causal inference
 * algorithms. Biometrika, 108(4), 795-814.</p>
 *
 * <p>The BOSS algorithm is based on the idea that implied DAGs for permutations are most optimal in their BIC scores
 * when the variables in the permutations are ordered causally--that is, so that that causes in the models come before
 * effects in a topological order.</p>
 *
 * <p>This algorithm is implemented as a "plugin-in" algorithm to a PermutationSearch object (see), which deals with
 * certain details of knowledge handling that are common to different permutation searches.</p>
 *
 * <p>BOSS, like GRaSP (see), is characterized by high adjacency and orientation precision (especially) and recall for
 * moderate sample sizes. BOSS scales up currently further than GRaSP to larger variable sets and denser graphs and so
 * is currently preferable from a practical standpoint, though performance is essentially identical.</p>
 *
 * <p>The algorithm works as follows:</p>
 *
 * <ol>
 *     <li>Start with an arbitrary ordering.</li>
 *     <li>Run the permutation search to find a better ordering.</li>
 *     <li>Project this ordering to a CPDAG.</li>
 *     <li>Optionally, Run BES this CPDAG.
 *     <li>Return this CPDAG.</li>
 * </ol>
 *
 * <o>The optional BES step is needed for correctness, though with large
 * models is has very little effect on the output, since nearly all edges
 * are already oriented, so a parameter is included to turn that step off.</o>
 *
 * <p>Knowledge can be used with this search. If tiered knowledge is used,
 * then the procedure is carried out for each tier separately, given the
 * variables preceding that tier, which allows the Boss algorithm to address
 * tiered (e.g., time series) problems with larger numbers of variables.
 * However, knowledge of required and forbidden edges is correctly implemented
 * for arbitrary such knowledge.</p>
 *
 * <p>A parameter is included to restart the search a certain number of time.
 * The idea is that the goal is to optimize a BIC score, so if several runs
 * are done of the algorithm for the same data, the model with the highest
 * BIC score should be returned and the others ignored.</p>
 *
 * <p>This class is meant to be used in the context of the PermutationSearch
 * class (see).
 *
 * @author bryanandrews
 * @author josephramsey
 * @see PermutationSearch
 * @see Grasp
 * @see Knowledge
 */
public class Boss implements SuborderSearch {
    private final Score score;
    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private Map<Node, GrowShrinkTree> gsts;
    private ForkJoinPool pool;
    private Knowledge knowledge = new Knowledge();
    private BesPermutation bes = null;
    private int numStarts = 1;

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

    @Override
    public void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts) {
        assert this.numStarts > 0;
        this.gsts = gsts;

        List<Node> bestSuborder = null;
        double score, bestScore = Double.NEGATIVE_INFINITY;
        boolean improved;

        this.pool = new ForkJoinPool(10 * Runtime.getRuntime().availableProcessors());

        for (int i = 0; i < this.numStarts; i++) {
            shuffle(suborder);

            makeValidKnowledgeOrder(suborder);

            do {
                improved = false;
                for (Node x : new ArrayList<>(suborder)) {
//                     if (betterMutation(prefix, suborder, x)) improved = true;
                    if (betterMutationAsync(prefix, suborder, x)) improved = true;
                }
            } while (improved);

            if (this.bes != null) bes(prefix, suborder);

            score = update(prefix, suborder);
            if (score > bestScore) {
                bestSuborder = new ArrayList<>(suborder);
                bestScore = score;
            }
        }

        this.pool.shutdown();

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

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public Map<Node, Set<Node>> getParents() {
        return this.parents;
    }

    @Override
    public Score getScore() {
        return this.score;
    }

    private boolean betterMutationAsync(List<Node> prefix, List<Node> suborder, Node x) {
        Set<Node> all = new HashSet<>(suborder);
        all.addAll(prefix);

        List<Future<Double>> futures = new ArrayList<>();
        List<Future<Double>> with = new ArrayList<>();
        List<Future<Double>> without = new ArrayList<>();

        Set<Node> Z = new HashSet<>(prefix);

        int i = 0;
        int curr = 0;

        futures.add(this.pool.submit(new Trace(this.gsts.get(x), Z, all)));
        for (Node z : suborder) {
            if (x != z){
                Z.add(x);
                with.add(0, this.pool.submit(new Trace(this.gsts.get(z), Z, all)));
                Z.remove(x);
                without.add(this.pool.submit(new Trace(this.gsts.get(z), Z, all)));
                Z.add(z);
                futures.add(this.pool.submit(new Trace(this.gsts.get(x), Z, all)));
            } else curr = i;
            i++;
        }

        double[] scores = new double[suborder.size()];
        double score;

        try {
            i = 0;
            for (Future<Double> future : new ArrayList<>(futures)) {
                scores[i++] = future.get();
            }

            score = 0;
            i = with.size();
            for (Future<Double> future : new ArrayList<>(with)) {
                score += future.get();
                scores[--i] += score;
            }

            score = 0;
            i = 0;
            for (Future<Double> future : new ArrayList<>(without)) {
                score += future.get();
                scores[++i] += score;
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        int best = curr;

        for (i = scores.length - 1; i >= 0; i--) {
            if (scores[i] + 1e-6 > scores[best]) best = i;
        }

        if (scores[curr] + 1e-6 > scores[best]) return false;
        suborder.remove(x);
        suborder.add(best, x);

        return true;
    }

    private static class Trace implements Callable<Double> {

        private final GrowShrinkTree gst;
        private final Set<Node> prefix;
        private final Set<Node> available;

        Trace(GrowShrinkTree gst, Set<Node> prefix, Set<Node> available) {
            this.gst = gst;
            this.prefix = new HashSet<>(prefix);
            this.available = new HashSet<>(available);
        }

        @Override
        public Double call() {
            return gst.traceUnsafe(this.prefix, this.available);
        }
    }

    private boolean betterMutation(List<Node> prefix, List<Node> suborder, Node x) {
        Set<Node> all = new HashSet<>(suborder);
        all.addAll(prefix);

        ListIterator<Node> itr = suborder.listIterator();
        double[] scores = new double[suborder.size() + 1];
        Set<Node> Z = new HashSet<>(prefix);

        int i = 0;
        double score = 0;
        int curr = 0;

        while (itr.hasNext()) {
            Node z = itr.next();

            // THE CORRECTNESS OF THIS NEEDS TO BE VERIFIED
            if (this.knowledge.isRequired(x.getName(), z.getName())) break;

            scores[i++] = this.gsts.get(x).trace(Z, all) + score;
            if (z != x) {
                score += this.gsts.get(z).trace(Z, all);
                Z.add(z);
            } else curr = i - 1;
        }

        scores[i] = this.gsts.get(x).trace(Z, all) + score;
        int best = i;

        Z.add(x);
        score = 0;

        while (itr.hasPrevious()) {
            Node z = itr.previous();

            // THE CORRECTNESS OF THIS NEEDS TO BE VERIFIED
            if (this.knowledge.isRequired(z.getName(), x.getName())) break;

            if (z != x) {
                Z.remove(z);
                score += gsts.get(z).trace(Z, all);
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

    private void bes(List<Node> prefix, List<Node> suborder) {
        List<Node> all = new ArrayList<>(prefix);
        all.addAll(suborder);

        Graph graph = PermutationSearch.getGraph(all, this.parents, this.knowledge, true);
        this.bes.bes(graph, all, suborder);
        graph.paths().makeValidOrder(suborder);
    }

    private double update(List<Node> prefix, List<Node> suborder) {
        double score = 0;
        Set<Node> all = new HashSet<>(suborder);
        all.addAll(prefix);

        Set<Node> Z = new HashSet<>(prefix);

        for (Node x : suborder) {
            Set<Node> parents = this.parents.get(x);
            parents.clear();
            score += this.gsts.get(x).trace(Z, all, parents);
            Z.add(x);
        }

        return score;
    }

    private void makeValidKnowledgeOrder(List<Node> order) {
        if (!this.knowledge.isEmpty()) {
            order.sort((a, b) -> {
                if (a.getName().equals(b.getName())) return 0;
                else if (this.knowledge.isRequired(a.getName(), b.getName())) return -1;
                else if (this.knowledge.isRequired(b.getName(), a.getName())) return 1;
                else return 0;
            });
        }
    }
}