package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;

import java.util.*;

/**
 * <p>Implements the SP (Sparsest Permutation) algorithm. This procedure goes through every
 * permutation of the variables (so can be slow for more than 11 variables with no knowledge)
 * looking for a permutation such that when a DAG is built it has the fewest number of
 * edges (i.e., is a most 'frugal' or a 'sparsest' DAG). The procedure can in principle
 * return all such sparsest permutations and their corresponding DAGs, but in this version
 * it return one of them, and converts the result into a CPDAG.</p>
 *
 * <p>Knowledge can be used with this search. If tiered knowledge is used, then the procedure
 * is carried out for each tier separately, given the variable preceding that tier, which
 * allows the SP algorithm to address tiered (e.g., time series) problems with more than 11
 * variables.</p>
 *
 * <p>This class is meant to be used in the context of the PermutationSearch class (see).
 * the proper use is PermutationSearch search = new PermutationSearch(new Sp(score));</p>
 *
 * <p>Raskutti, G., & Uhler, C. (2018). Learning directed acyclic graph models based on
 * sparsest permutations. Stat, 7(1), e183.</p>
 *
 * @author bryanandrews
 * @author josephramsey
 * @see PermutationSearch
 */
public class Sp implements SuborderSearch {
    private final Score score;
    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private final Map<Node, Double> scores;
    private Map<Node, GrowShrinkTree> gsts;
    private Knowledge knowledge = new Knowledge();

    /**
     * This algorithm will work with an arbitrary score.
     *
     * @param score The Score to use.
     */
    public Sp(Score score) {
        this.score = score;
        this.variables = score.getVariables();
        this.parents = new HashMap<>();
        this.scores = new HashMap<>();

        for (Node node : this.variables) {
            this.parents.put(node, new HashSet<>());
        }
    }

    /**
     * This is the method called by PermutationSearch per tier.
     *
     * @param prefix   The variable preceding the suborder variables in the permutation, including
     *                 all variables from previous tiers.
     * @param suborder The suborder of the variables list beign searched over. Only the order of the
     *                 variables in this suborder will be modified.
     * @param gsts     The GrowShrinkTree used for the search. This is an optimized score-caching class.
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

        int[] swap;
        double s;
        SwapIterator itr = new SwapIterator(suborder.size());
        while (itr.hasNext()) {
            swap = itr.next();
            Node x = suborder.get(swap[0]);
            suborder.set(swap[0], suborder.get(swap[1]));
            suborder.set(swap[1], x);
            s = update(prefix, suborder);
            if (s > bestScore && !violatesKnowledge(suborder, required)) {
                bestSuborder = new ArrayList<>(suborder);
                bestScore = s;
            }
        }

        for (int i = 0; i < suborder.size(); i++) {
            suborder.set(i, bestSuborder.get(i));
        }

        update(prefix, suborder);
    }

    /**
     * Sets the knowledge to be used in the search.
     *
     * @param knowledge This knowledge.
     * @see Knowledge
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * Returns the variables in the data.
     *
     * @return This list.
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * Returns the map from nodes to their parents.
     *
     * @return This map.
     */
    @Override
    public Map<Node, Set<Node>> getParents() {
        return parents;
    }

    /**
     * Returns the score being used for this search.
     *
     * @return This score.
     * @see Score
     */
    @Override
    public Score getScore() {
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

    private static class SwapIterator implements Iterator<int[]> {
        private int[] next;
        private final int n;
        private final int[] perm;
        private final int[] dirs;

        public SwapIterator(int size) {
            this.n = size;
            if (this.n <= 0) {
                this.perm = null;
                this.dirs = null;
            } else {
                this.perm = new int[n];
                this.dirs = new int[n];
                for (int i = 0; i < n; i++) {
                    this.perm[i] = i;
                    this.dirs[i] = -1;
                }
                this.dirs[0] = 0;
            }
        }

        @Override
        public int[] next() {
            int[] next = makeNext();
            this.next = null;
            return next;
        }

        @Override
        public boolean hasNext() {
            return makeNext() != null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private int[] makeNext() {
            if (this.next != null) return this.next;
            if ((this.dirs == null) || (this.perm == null)) return null;

            int i = -1;
            int x = -1;
            for (int j = 0; j < n; j++)
                if ((this.dirs[j] != 0) && (this.perm[j] > x)) {
                    x = this.perm[j];
                    i = j;
                }

            if (i == -1) return null;

            int k = i + this.dirs[i];
            this.next = new int[]{i, k};

            swap(i, k, this.dirs);
            swap(i, k, this.perm);

            if ((k == 0) || (k == n - 1) || (this.perm[k + this.dirs[k]] > x))
                this.dirs[k] = 0;

            for (int j = 0; j < n; j++)
                if (this.perm[j] > x)
                    this.dirs[j] = (j < k) ? +1 : -1;

            return this.next;
        }

        private static void swap(int i, int j, int[] arr) {
            int x = arr[i];
            arr[i] = arr[j];
            arr[j] = x;
        }
    }
}




