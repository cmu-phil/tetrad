package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.BesPermutation;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;

import java.util.*;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * <p>Implements an algorithm called BOSS (Best Order Score Search). This
 * follows up on work by Raskutti and Uhler on the SP (Sparsest Permutation)
 * algorithm and work by Lam, Andrews, and Ramsey on the GRaSP algorithm is
 * currently under development.</p>
 *
 * <p>This algorithm is based on the intuition that the permutation search is
 * more effective when the variables are ordered in a way that is close to the
 * true causal ordering. The permutation search is a greedy algorithm that
 * starts with an arbitrary ordering and then tries to improve the ordering
 * by swapping adjacent variables. The permutation search is not guaranteed
 * to find the optimal ordering, but it is guaranteed to find an ordering that
 * implies a DAG. The permutation search is also very fast, so it can be
 * called many times in order to find the best ordering. This algorithm
 * intercalates calls to the permutation search with calls to BES, which
 * finds a DAG for a given ordering. The algorithm then keeps track of the
 * best DAG found so far and the ordering that implies that DAG.</p>
 *
 * <p>The algorithm works as follows:</p>
 *
 * <ol>
 *     <li>Start with an arbitrary ordering.</li>
 *     <li>Run the permutation search to find a better ordering.</li>
 *     <li>Run BES on the new ordering to find a DAG.</li>
 *     <li>Repeat steps 2 and 3 until the model score can no longer be improved..</li>
 *     <li>Return the CPDAG of the best DAG found.</li>
 * </ol>
 *
 * <o>The BES step is needed for correctness, though with large models is has very
 * little effect on the output, since nearly all edges are alreayy oriented, so
 * a parameter is included to turn that step off.</o>
 *
 * <p>The permutation search step uses an repeated insert operation.</p>
 *
 * <p>Knowledge can be used with this search. If tiered knowledge is used, then the procedure
 * is carried out for each tier separately, given the variable preceding that tier, which
 * allows the Boss algorithm to address tiered (e.g., time series) problems with larger numbers of
 * variables.</p>
 *
 * <p>This class is meant to be used in the context of the PermutationSearch class (see).
 *
 * @author bryanandrews
 * @author josephramsey
 * @see Sp
 * @see Grasp
 * @see PermutationSearch
 * @see Knowledge
 */
public class Boss implements SuborderSearch {
    private final Score score;
    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private Map<Node, GrowShrinkTree> gsts;
    private Knowledge knowledge = new Knowledge();
    private BesPermutation bes = null;
    private int numStarts = 1;


    /**
     * This algorithm will work with an arbitrary score.
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

        for (int i = 0; i < this.numStarts; i++) {
            shuffle(suborder);
            makeValidKnowledgeOrder(suborder);

            do {
                improved = false;
                for (Node x : new ArrayList<>(suborder)) {
                    if (betterMutation(prefix, suborder, x)) improved = true;
                }
            } while (improved);

            if (this.bes != null) bes(prefix, suborder);

            score = update(prefix, suborder);
            if (score > bestScore) {
                bestSuborder = new ArrayList<>(suborder);
                bestScore = score;
            }
        }

        suborder.clear();

        if (bestSuborder != null) {
            suborder.addAll(bestSuborder);
        }

        update(prefix, suborder);
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
            if(this.knowledge.isRequired(z.getName(), x.getName())) break;

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

    public void useBes(boolean use) {
        this.bes = null;
        if (use) {
            this.bes = new BesPermutation(this.score);
            this.bes.setVerbose(false);
            this.bes.setKnowledge(knowledge);
        }
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

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;

        if (this.bes != null) {
            this.bes.setKnowledge(knowledge);
        }
    }

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
}