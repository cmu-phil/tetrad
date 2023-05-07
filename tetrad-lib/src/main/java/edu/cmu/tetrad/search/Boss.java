package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;

import java.util.*;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * <p>Implements an algorithms called BOSS (Bost order Score Search), which
 * intercalalates calls to a permutation discvoery step with called to BES, in order
 * to find an optimal permutation implying a DAG and therefore a CPDAG that is
 * highly accurate. This follows up on work by Raskutti and Uhler on the SP
 * (Sparsest Permutation) algorithm and work by Lam, Andrews and Ramsey on the
 * GRaSP algorithm and is currently under development.</p>
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
 * @see Sp
 * @see Grasp
 * @see PermutationSearch
 */
public class Boss implements SuborderSearch {
    private final BesPermutation bes;
    private final Score score;
    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private final Map<Node, Double> scores;
    private Map<Node, GrowShrinkTree> gsts;
    private int numStarts;
    private Knowledge knowledge = new Knowledge();

    /**
     * This algorithm will work with an arbitrary score.
     *
     * @param score The Score to use.
     */
    public Boss(Score score) {
        this.score = score;
        this.variables = score.getVariables();
        this.parents = new HashMap<>();
        this.scores = new HashMap<>();

        for (Node x : this.variables) {
            this.parents.put(x, new HashSet<>());
        }

        this.bes = new BesPermutation(score);
        this.bes.setVerbose(false);
        this.numStarts = 1;
    }

    /**
     * Searches over the given suborder using the given prefix. Useful if tiered knowledge is available.
     *
     * @param prefix   The prefix--variables alwasys allowed for conditioning.
     * @param suborder The suborder--these are the variables being searched over.
     * @param gsts     The grow-shrink tree used for caching.
     * @see GrowShrinkTree
     */
    @Override
    public void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts) {
        this.gsts = gsts;
        makeValidKnowledgeOrder(suborder);
        List<Node> bestSuborder = new ArrayList<>(suborder);
        double bestScore = update(prefix, suborder);

        Map<Node, Set<Node>> required = new HashMap<>();
        for (Node y : suborder) {
            for (Node z : suborder) {
                if (this.knowledge.isRequired(y.getName(), z.getName())) {
                    if (!required.containsKey(y)) required.put(y, new HashSet<>());
                    required.get(y).add(z);
                }
            }
        }

        for (int i = 0; i < this.numStarts; i++) {
            shuffle(suborder);
            makeValidKnowledgeOrder(suborder);
            double s1, s2, s3;
            s1 = update(prefix, suborder);
            do {
                s2 = s1;
                do {
                    s3 = s1;
                    for (Node x : new ArrayList<>(suborder)) {
                        if (betterMutation(prefix, suborder, required, x)) {
                            s1 = update(prefix, suborder);
                        }
                    }
                } while (s1 > s3);
                do {
                    s3 = s1;
                    List<Node> Z = new ArrayList<>(prefix);
                    Z.addAll(suborder);
                    Graph graph = PermutationSearch.getGraph(Z, parents, this.knowledge, true);
                    this.bes.bes(graph, Z, suborder);
                    graph.paths().makeValidOrder(suborder);
                    s1 = update(prefix, suborder);
                } while (s1 > s3);
            } while (s1 > s2);

            if (s1 > bestScore) {
                bestSuborder = new ArrayList<>(suborder);
                bestScore = s1;
            }
        }

        for (int i = 0; i < suborder.size(); i++) {
            suborder.set(i, bestSuborder.get(i));
        }
        update(prefix, suborder);
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
        this.bes.setKnowledge(knowledge);
    }

    /**
     * Sets the number of times the procedure should be restarted in search of a better
     * score.
     *
     * @param numStarts This number.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Returns the variables.
     *
     * @return A list of these.
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * Returns a map from nodes to their parents.
     *
     * @return This map.
     */
    @Override
    public Map<Node, Set<Node>> getParents() {
        return parents;
    }

    /**
     * Returns the score being used for the search.
     *
     * @return This score.
     * @see Score
     */
    @Override
    public Score getScore() {
        return score;
    }

    private boolean betterMutation(List<Node> prefix, List<Node> suborder, Map<Node, Set<Node>> required, Node x) {
        ListIterator<Node> itr = suborder.listIterator();
        double[] scores = new double[suborder.size() + 1];
        int i = 0;

        Set<Node> Z = new HashSet<>(prefix);
        double score = 0;
        int curr = 0;

        while (itr.hasNext()) {
            Node z = itr.next();
            scores[i++] = gsts.get(x).trace(new HashSet<>(Z), new HashSet<>()) + score;
            if (z != x) {
                score += gsts.get(z).trace(new HashSet<>(Z), new HashSet<>());
                Z.add(z);
            } else curr = i - 1;
        }
        scores[i] = gsts.get(x).trace(new HashSet<>(Z), new HashSet<>()) + score;

        Z.add(x);
        score = 0;
        int best = i;

        while (itr.hasPrevious()) {
            Node z = itr.previous();
            if (z != x) {
                Z.remove(z);
                score += gsts.get(z).trace(new HashSet<>(Z), new HashSet<>());
            }
            scores[--i] += score;
            if (scores[i] > scores[best] && !violatesKnowledge(suborder, required)) best = i;
        }

        if (best == curr) return false;
        else if (best > curr) best--;

        suborder.remove(curr);
        suborder.add(best, x);

        return true;
    }

    private double update(List<Node> prefix, List<Node> suborder) {
        double score = 0;

        Iterator<Node> itr = suborder.iterator();
        Set<Node> Z = new HashSet<>(prefix);
        while (itr.hasNext()) {
            Node x = itr.next();
            parents.get(x).clear();
            scores.put(x, gsts.get(x).trace(new HashSet<>(Z), parents.get(x)));
            score += scores.get(x);
            Z.add(x);
        }

        return score;
    }

    private boolean violatesKnowledge(List<Node> suborder, Map<Node, Set<Node>> required) {
        for (int i = 0; i < suborder.size(); i++) {
            Node y = suborder.get(i);
            if (required.containsKey(y)) {
                for (Node z : required.get(y)) {
                    if (suborder.subList(0, i).contains(z)) {
                        return true;
                    }
                }
            }
        }
        return false;
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



