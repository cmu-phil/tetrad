package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;

import java.util.*;

/**
 * Implements the SP (Sparsest Permutation) algorithm. This procedure goes through every permutation of the variables
 * (so can be slow for more than 11 variables with no knowledge) looking for a permutation such that when a DAG is built
 * it has the fewest number of edges (i.e., is a most 'frugal' or a 'sparsest' DAG). The procedure can in principle
 * return all such sparsest permutations and their corresponding DAGs, but in this version it return one of them, and
 * converts the result into a CPDAG.
 * <p>
 * Note that SP considers all permutations of the algorithm, which is exponential in the number of variables. So SP
 * without knowledge is limited to about 10 variables per knowledge tier.
 * <p>
 * However, notably, tiered Knowledge can be used with this search. If tiered knowledge is used, then the procedure is
 * carried out for each tier separately, given the variable preceding that tier, which allows the SP algorithm to
 * address tiered (e.g., time series) problems with more than 11 variables.
 * <p>
 * This class is meant to be used in the context of the PermutationSearch class (see). the proper use is
 * PermutationSearch search = new PermutationSearch(new Sp(score));
 * <p>
 * Raskutti, G., &amp; Uhler, C. (2018). Learning directed acyclic graph models based on sparsest permutations. Stat,
 * 7(1), e183.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 * @see PermutationSearch
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see SpFci
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 * @see Knowledge
 */
public class Sp implements SuborderSearch {
    /**
     * The score to use.
     */
    private final Score score;
    /**
     * The variables to search over.
     */
    private final List<Node> variables;
    /**
     * The parents of each variable.
     */
    private final Map<Node, Set<Node>> parents;
    /**
     * The GrowShrinkTree for each variable.
     */
    private Map<Node, GrowShrinkTree> gsts;
    /**
     * The knowledge.
     */
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

        for (Node node : this.variables) {
            this.parents.put(node, new HashSet<>());
        }
    }

    /**
     * Searches for the best suborder of nodes given a prefix and a suborder.
     *
     * @param prefix   The prefix of the suborder.
     * @param suborder The suborder.
     * @param gsts     The GrowShrinkTree being used to do caching of scores.
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
     * Returns the list of variables associated with this object.
     *
     * @return the list of variables.
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * Retrieves a mapping of nodes to their parent nodes.
     *
     * @return the mapping of nodes to their parent nodes.
     */
    @Override
    public Map<Node, Set<Node>> getParents() {
        return parents;
    }

    /**
     * Retrieves the score associated with this object.
     *
     * @return the score
     */
    @Override
    public Score getScore() {
        return score;
    }

    /**
     * Sets the knowledge associated with this object.
     *
     * @param knowledge The knowledge to set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * Makes the order of nodes valid according to the knowledge rules. The order list will be sorted based on the
     * knowledge rules if the knowledge is not empty.
     *
     * @param order the list of nodes to be sorted
     */
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

    /**
     * Checks if the given suborder violates the knowledge rules.
     *
     * @param suborder The suborder to check.
     * @param required The mapping of nodes to their required parent nodes.
     * @return True if the suborder violates the knowledge rules, false otherwise.
     */
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

    /**
     * Updates the score of the suborder by adding nodes from the suborder to the prefix one by one and calculating the
     * score.
     *
     * @param prefix   The prefix of the suborder.
     * @param suborder The suborder.
     * @return The updated score of the suborder.
     */
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

    /**
     * SwapIterator is an Iterator implementation that generates all possible swaps between two elements in an array. It
     * can be used to generate all possible permutations of a given array.
     */
    private static class SwapIterator implements Iterator<int[]> {
        private final int n;
        private final int[] perm;
        private final int[] dirs;
        private int[] next;

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

        private static void swap(int i, int j, int[] arr) {
            int x = arr[i];
            arr[i] = arr[j];
            arr[j] = x;
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
    }
}




