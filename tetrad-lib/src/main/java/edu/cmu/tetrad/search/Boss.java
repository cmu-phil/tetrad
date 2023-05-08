package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * <p>Implements the BOSS (Best Order Permutation Search) algorithm. This procedure uses
 * an optimization of the BOSS algorithm (reference to be included in a future version),
 * looking for a permutation such that when a DAG is built it has the fewest number of
 * edges (i.e., is a most 'frugal' or a 'sparsest' DAG). Returns the CPDAG of this discovered
 * frugal DAG.</p>
 *
 * <p>Knowledge can be used with this search. If tiered knowledge is used, then the procedure
 * is carried out for each tier separately, given the variable preceding that tier, which
 * allows the SP algorithm to address tiered (e.g., time series) problems with larger numbers of
 * variables.</p>
 *
 * <p>This class is meant to be used in the context of the PermutationSearch class (see).
 * the proper use is PermutationSearch search = new PermutationSearch(new Sp(score));</p>
 *
 * @author bryanandrews
 * @author josephramsey
 * @see PermutationSearch
 */
public class Boss implements SuborderSearch {
    private final Score score;
    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private Map<Node, GrowShrinkTree> gsts;
    private Knowledge knowledge = new Knowledge();
    private int numStarts = 1;


    /**
     * This algorithm will work with an arbitrary score.
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

//            bes(prefix, suborder);

            score = update(prefix, suborder);
            if (score > bestScore) {
                bestSuborder = new ArrayList<>(suborder);
                bestScore = score;
            }
        }

        suborder.clear();
        suborder.addAll(bestSuborder);
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

//            if x is a required parent of z
//            we insert x to the right of z
//            this might be wrong...
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

//            if z is a required parent of x
//            we insert x to the left of z
//            this might be wrong...
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

    private void bes(List<Node> prefix, List<Node> suborder) {
        PermutationBes bes = new PermutationBes(this.score);
        bes.setKnowledge(this.knowledge);
        bes.setVerbose(false);

        List<Node> all = new ArrayList<>(prefix);
        all.addAll(suborder);

        Graph graph = PermutationSearch.getGraph(all, this.parents, this.knowledge, true);
        bes.bes(graph, all, suborder);
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