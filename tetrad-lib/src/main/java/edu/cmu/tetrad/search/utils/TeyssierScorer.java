package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;
import static org.apache.commons.math3.util.FastMath.floor;


/**
 * Implements and extends a scorer extending Teyssier, M., and Koller, D. (2012). You give it a score function and a
 * variable ordering, and it computes the score. You can move any variable left or right, and it will keep track of the
 * score using the Teyssier and Kohler method. You can move a variable to a new position, and you can bookmark a state
 * and come back to it.
 *
 * <p>Teyssier, M., &amp; Koller, D. (2012). Ordering-based search: A simple and effective algorithm for
 * learning Bayesian networks. arXiv preprint arXiv:1207.1429.</p>
 *
 * @author josephramsey
 * @author bryanandrews
 * @version $Id: $Id
 */
public class TeyssierScorer {
    private final List<Node> variables;
    private final IndependenceTest test;
    private final Score score;
    private final Map<Object, ArrayList<Node>> bookmarkedOrders = new HashMap<>();
    private final Map<Object, ArrayList<Pair>> bookmarkedScores = new HashMap<>();
    private final Map<Object, Map<Node, Integer>> bookmarkedOrderHashes = new HashMap<>();
    private final Map<Object, Double> bookmarkedRunningScores = new HashMap<>();
    private final Map<Node, GrowShrinkTree> trees = new HashMap<>();
    private ArrayList<Node> pi; // The current permutation.
    private Map<Node, Integer> orderHash = new HashMap<>();
    private ArrayList<Set<Node>> prefixes;
    private ArrayList<Pair> scores;
    private Knowledge knowledge = new Knowledge();
    private boolean useScore;
    private boolean useRaskuttiUhler = false;
    private double runningScore = 0f;

    /**
     * Constructor that takes both a test or a score. Only one of these is used, dependeint on how the parameters are
     * set.
     *
     * @param test  The test.
     * @param score The score
     * @see IndependenceTest
     * @see Score
     */
    public TeyssierScorer(IndependenceTest test, Score score) {
        if (test == null && score == null) throw new IllegalArgumentException("Required: test or score");
//        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.NAME);

        this.variables = score.getVariables();
        this.pi = new ArrayList<>(this.variables);
        Map<Node, Integer> variablesHash = new HashMap<>();
        nodesHash(variablesHash, this.variables);
        nodesHash(this.orderHash, this.pi);

        this.test = test;
        this.score = score;

        setUseScore(true);
        if (this.useScore) {
            for (Node node : this.variables) {
                this.trees.put(node, new GrowShrinkTree(score, variablesHash, node));
            }
        }
    }

    /**
     * <p>Setter for the field <code>useScore</code>.</p>
     *
     * @param useScore True if the score should be used; false if the test should be used.
     */
    public void setUseScore(boolean useScore) {
        this.useScore = useScore && !(this.score instanceof GraphScore);
        if (this.useScore) this.useRaskuttiUhler = false;
    }

    /**
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge Knowledge of forbidden edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;

        for (Node node : this.variables) {
            List<Node> required = new ArrayList<>();
            List<Node> forbidden = new ArrayList<>();
            for (Node parent : this.variables) {
                if (knowledge.isRequired(parent.getName(), node.getName())) required.add(parent);
                if (knowledge.isForbidden(parent.getName(), node.getName())) forbidden.add(parent);
            }
            if (required.isEmpty() && forbidden.isEmpty()) continue;
            this.trees.get(node).setKnowledge(required, forbidden);
        }
    }

    /**
     * <p>Setter for the field <code>useRaskuttiUhler</code>.</p>
     *
     * @param useRaskuttiUhler True if Pearl's method for building a DAG should be used.
     */
    public void setUseRaskuttiUhler(boolean useRaskuttiUhler) {
        this.useRaskuttiUhler = useRaskuttiUhler;
        if (useRaskuttiUhler) this.useScore = false;
    }

    /**
     * Scores the given permutation. This needs to be done initially before any move or tuck operations are performed.
     *
     * @param order The permutation to score.
     * @return The score of it.
     */
    public double score(List<Node> order) {
        this.pi = new ArrayList<>(order);
        this.scores = new ArrayList<>();

        for (int i1 = 0; i1 < order.size(); i1++) {
            this.scores.add(null);
        }

        this.prefixes = new ArrayList<>();
        for (int i1 = 0; i1 < order.size(); i1++) this.prefixes.add(null);
        initializeScores();
        return score();
    }

    /**
     * <p>score.</p>
     *
     * @return The score of the current permutation.
     */
    public double score() {
        return sum();
    }

    /**
     * Performs a tuck operation.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     */
    public void swaptuck(Node x, Node y) {
        if (index(y) < index(x)) {
            moveTo(x, index(y));
        }
    }

    /**
     * Moves j to before k and moves all the ancestors of j betwween k and j to before k.
     *
     * @param k The node to tuck j before.
     * @param j The node to tuck.
     * @return true if the tuck made a change.
     */
    public boolean tuck(Node k, Node j) {
        int jIndex = index(j);
        int kIndex = index(k);

        if (jIndex < kIndex) {
            return false;
        }

        Set<Node> ancestors = getAncestors(j);
        int _kIndex = kIndex;

        boolean changed = false;

        for (int i = jIndex; i > kIndex; i--) {
            if (ancestors.contains(get(i))) {
                moveTo(get(i), _kIndex++);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Moves all j's to before k and moves all the ancestors of all ji's betwween k and ji to before k.
     * @param k The node to tuck j before.
     * @param j The nodes to tuck.
     * @return true if the tuck made a change.
     */
    public boolean tuck(Node k, Node...j) {
        List<Integer> jIndices = new ArrayList<>();
        int maxj = Integer.MIN_VALUE;
        int minj = Integer.MAX_VALUE;
        for (Node node : j) {
            jIndices.add(index(node));
            maxj = Math.max(maxj, index(node));
            minj = Math.min(minj, index(node));
        }

        int kIndex = index(k);

        if (maxj < kIndex) {
            return false;
        }

        boolean changed = false;

        for (int _j : jIndices) {
            Set<Node> ancestors = getAncestors(get(_j));
            int _kIndex = kIndex;

            for (int i = minj; i > kIndex; i--) {
                if (ancestors.contains(get(i))) {
                    moveTo(get(i), _kIndex++);
                    changed = true;
                }
            }
        }

        return changed;
    }

    /**
     * Moves v to a new index.
     *
     * @param v       The variable to move.
     * @param toIndex The index to move v to.
     */
    public void moveTo(Node v, int toIndex) {
        int vIndex = index(v);
        if (vIndex == toIndex) return;
        if (lastMoveSame(vIndex, toIndex)) return;

        int size = pi.size();
        this.pi.remove(v);

        if (toIndex == this.pi.size() - 1) {
            this.pi.add(v);
        } else {
            this.pi.add(toIndex, v);
        }

//        this.pi.add(toIndex, v);

        if (toIndex < size) {
            updateScores(toIndex, vIndex);
        } else {
            updateScores(vIndex, toIndex);
        }
    }

    /**
     * Swaps m and n in the permutation.
     *
     * @param m The first variable.
     * @param n The second variable.
     * @return True iff the swap was done.
     */
    public boolean swap(Node m, Node n) {
        int i = this.orderHash.get(m);
        int j = this.orderHash.get(n);

        this.pi.set(i, n);
        this.pi.set(j, m);

        if (violatesKnowledge(this.pi)) {
            this.pi.set(i, m);
            this.pi.set(j, n);
            return false;
        }

        if (i < j) {
            updateScores(i, j);
        } else {
            updateScores(j, i);
        }

        return true;
    }

    /**
     * Returns true iff x-&gt;y or y-&gt;x is a covered edge. x-&gt;y is a covered edge if parents(x) = parents(y) \
     * {x}
     *
     * @param x The first variable.
     * @param y The second variable.
     * @return True iff x-&gt;y or y-&gt;x is a covered edge.
     */
    public boolean coveredEdge(Node x, Node y) {
        if (!adjacent(x, y)) return false;
        Set<Node> px = getParents(x);
        Set<Node> py = getParents(y);
        px.remove(y);
        py.remove(x);
        return px.equals(py);
    }

    /**
     * <p>Getter for the field <code>pi</code>.</p>
     *
     * @return A copy of the current permutation.
     */
    public List<Node> getPi() {
        return new ArrayList<>(this.pi);
    }

    /**
     * Returns the current permutation without making a copy. Could be dangerous!
     *
     * @return the current permutation.
     */
    public List<Node> getOrderShallow() {
        return this.pi;
    }

    /**
     * Return the index of v in the current permutation.
     *
     * @param v The variable.
     * @return Its index.
     */
    public int index(Node v) {
        Integer integer = this.orderHash.get(v);

        if (integer == null)
            throw new IllegalArgumentException("First 'evaluate' a permutation containing variable "
                                               + v + ".");

        return integer;
    }

    /**
     * Returns the parents of the node at index p.
     *
     * @param p The index of the node.
     * @return Its parents.
     */
    public Set<Node> getParents(int p) {
        try {
            if (this.scores.get(p) == null) recalculate(p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new HashSet<>(this.scores.get(p).getParents());
    }

    /**
     * Returns the children of a node v.
     *
     * @param p The index of the variable.
     * @return Its parents.
     */
    public Set<Node> getChildren(int p) {
        Set<Node> adj = getAdjacentNodes(get(p));
        adj.removeAll(getParents(p));
        return adj;
    }

    /**
     * Returns the parents of a node v.
     *
     * @param v The variable.
     * @return Its parents.
     */
    public Set<Node> getParents(Node v) {
        return getParents(index(v));
    }

    /**
     * Returns the children of a node v.
     *
     * @param v The variable.
     * @return Its parents.
     */
    public Set<Node> getChildren(Node v) {
        return getChildren(index(v));
    }

    /**
     * Returns the nodes adjacent to v.
     *
     * @param v The variable.
     * @return Its adjacent nodes.
     */
    public Set<Node> getAdjacentNodes(Node v) {
        Set<Node> adj = new HashSet<>();

        for (Node w : this.pi) {
            if (getParents(v).contains(w) || getParents(w).contains(v)) {
                adj.add(w);
            }
        }

        return adj;
    }

    /**
     * Returns the DAG build for the current permutation, or its CPDAG.
     *
     * @param cpDag True iff the CPDAG should be returned, False if the DAG.
     * @return This graph.
     */
    public Graph getGraph(boolean cpDag) {
        Graph graph = new EdgeListGraph(this.variables);
        for (Node a : this.variables) {
            for (Node b : getParents(a)) {
                graph.addDirectedEdge(b, a);
            }
        }

        if (cpDag) {
            MeekRules rules = new MeekRules();
            rules.setKnowledge(this.knowledge);
            rules.orientImplied(graph);
        }

        return graph;
    }

    /**
     * Returns a list of adjacent node pairs in the current graph.
     *
     * @return This list.
     */
    public List<NodePair> getAdjacencies() {
        List<Node> order = getPi();
        Set<NodePair> pairs = new HashSet<>();

        for (int i = 0; i < order.size(); i++) {
            for (int j = 0; j < i; j++) {
                Node x = order.get(i);
                Node y = order.get(j);

                if (adjacent(x, y)) {
                    pairs.add(new NodePair(x, y));
                }
            }
        }

        return new ArrayList<>(pairs);
    }

    /**
     * <p>getAncestors.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.Set} object
     */
    public Set<Node> getAncestors(Node node) {
        Set<Node> ancestors = new HashSet<>();
        collectAncestorsVisit(node, ancestors);

        return ancestors;
    }

    /**
     * Returns a list of edges for the current graph as a list of ordered pairs.
     *
     * @return This list.
     */
    public List<OrderedPair<Node>> getEdges() {
        List<Node> order = getPi();
        List<OrderedPair<Node>> edges = new ArrayList<>();

        for (Node y : order) {
            for (Node x : getParents(y)) {
                edges.add(new OrderedPair<>(x, y));
            }
        }

        return edges;
    }

    /**
     * <p>getNumEdges.</p>
     *
     * @return The number of edges in the current graph.
     */
    public int getNumEdges() {
        int numEdges = 0;

        for (int p = 0; p < this.pi.size(); p++) {
            numEdges += getParents(p).size();
        }

        return numEdges;
    }

    /**
     * Returns the node at index j in pi.
     *
     * @param j The index.
     * @return The node at that index.
     */
    public Node get(int j) {
        return this.pi.get(j);
    }

    /**
     * Bookmarks the current pi as index key.
     *
     * @param key This bookmark may be retrieved using the index 'key', an integer. This bookmark will be stored until
     *            it is retrieved and then removed.
     */
    public void bookmark(int key) {
        try {
            this.bookmarkedOrders.put(key, new ArrayList<>(this.pi));
            this.bookmarkedScores.put(key, new ArrayList<>(this.scores));
            this.bookmarkedOrderHashes.put(key, new HashMap<>(this.orderHash));
            this.bookmarkedRunningScores.put(key, runningScore);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Bookmarks the current pi with index Integer.MIN_VALUE.
     */
    public void bookmark() {
        bookmark(Integer.MIN_VALUE);
    }

    /**
     * Retrieves the bookmarked state for index 'key' and removes that bookmark.
     *
     * @param key The integer key for this bookmark.
     */
    public void goToBookmark(int key) {
        if (!this.bookmarkedOrders.containsKey(key)) {
            throw new IllegalArgumentException("That key was not bookmarked: " + key);
        }

        this.pi = new ArrayList<>(this.bookmarkedOrders.get(key));
        this.scores = new ArrayList<>(this.bookmarkedScores.get(key));
        this.orderHash = new HashMap<>(this.bookmarkedOrderHashes.get(key));
        this.runningScore = this.bookmarkedRunningScores.get(key);
    }

    /**
     * Retries the bookmark with key = Integer.MIN_VALUE and removes the bookmark.
     */
    public void goToBookmark() {
        goToBookmark(Integer.MIN_VALUE);
    }

    /**
     * Clears all bookmarks.
     */
    public void clearBookmarks() {
        this.bookmarkedOrders.clear();
        this.bookmarkedScores.clear();
        this.bookmarkedOrderHashes.clear();
        this.bookmarkedRunningScores.clear();
    }

    /**
     * <p>size.</p>
     *
     * @return The size of pi, the current permutation.
     */
    public int size() {
        return this.pi.size();
    }

    /**
     * <p>getShuffledVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getShuffledVariables() {
        List<Node> variables = getPi();
        shuffle(variables);
        return variables;
    }

    /**
     * Returns True iff a is adjacent to b in the current graph.
     *
     * @param a The first node.
     * @param b The second node.
     * @return True iff adj(a, b).
     */
    public boolean adjacent(Node a, Node b) {
        if (a == b) return false;
        return getParents(a).contains(b) || getParents(b).contains(a);
    }

    /**
     * Returns true iff [a, b, c] is a collider.
     *
     * @param a The first node.
     * @param b The second node.
     * @param c The third node.
     * @return True iff a-&gt;b&lt;-c in the current DAG.
     */
    public boolean collider(Node a, Node b, Node c) {
        return getParents(b).contains(a) && getParents(b).contains(c);
    }

    /**
     * Returns true iff [a, b, c] is an unshielded collider.
     *
     * @param a The first node.
     * @param b The second node.
     * @param c The third node.
     * @return True iff a-&gt;b&lt;-c in the current DAG.
     */
    public boolean unshieldedCollider(Node a, Node b, Node c) {
        return getParents(b).contains(a) && getParents(b).contains(c) && !adjacent(a, c);
    }

    /**
     * Returns true iff [a, b, c] is an unshielded collider.
     *
     * @param a The first node.
     * @param b The second node.
     * @param c The third node.
     * @return True iff a-&gt;b&lt;-c in the current DAG.
     */
    public boolean unshieldedTriple(Node a, Node b, Node c) {
        return adjacent(a, b) && adjacent(b, c) && !adjacent(a, c);
    }

    /**
     * Returns true iff [a, b, c] is a triangle.
     *
     * @param a The first node.
     * @param b The second node.
     * @param c The third node.
     * @return True iff adj(a, b) and adj(b, c) and adj(a, c).
     */
    public boolean triangle(Node a, Node b, Node c) {
        return adjacent(a, b) && adjacent(b, c) && adjacent(a, c);
    }

    /**
     * True iff the nodes in W form a clique in the current DAG.
     *
     * @param W The nodes.
     * @return True iff these nodes form a clique.
     */
    public boolean clique(List<Node> W) {
        for (int i = 0; i < W.size(); i++) {
            for (int j = i + 1; j < W.size(); j++) {
                if (!adjacent(W.get(i), W.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * <p>getPrefix.</p>
     *
     * @param i a int
     * @return a {@link java.util.Set} object
     */
    public Set<Node> getPrefix(int i) {
        Set<Node> prefix = new HashSet<>();

        for (int j = 0; j < i; j++) {
            prefix.add(this.pi.get(j));
        }

        return prefix;
    }

    /**
     * <p>getSkeleton.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Set<Node>> getSkeleton() {
        List<Node> order = getPi();
        Set<Set<Node>> skeleton = new HashSet<>();

        for (Node y : order) {
            for (Node x : getParents(y)) {
                Set<Node> adj = new HashSet<>();
                adj.add(x);
                adj.add(y);
                skeleton.add(adj);
            }
        }

        return skeleton;
    }

    /**
     * <p>parent.</p>
     *
     * @param k a {@link edu.cmu.tetrad.graph.Node} object
     * @param j a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean parent(Node k, Node j) {
        return getParents(j).contains(k);
    }

    private void collectAncestorsVisit(Node node, Set<Node> ancestors) {
        if (ancestors.contains(node)) {
            return;
        }

        ancestors.add(node);
        Set<Node> parents = getParents(node);

        if (!parents.isEmpty()) {
            for (Node parent : parents) {
                collectAncestorsVisit(parent, ancestors);
            }
        }
    }

    private boolean violatesKnowledge(List<Node> order) {
        if (this.knowledge.isEmpty()) return false;

        for (int i = 0; i < order.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (this.knowledge.isRequired(order.get(i).getName(), order.get(j).getName())) {
                    return true;
                }
            }
        }

        return false;
    }

    private void initializeScores() {
        for (int i1 = 0; i1 < this.pi.size(); i1++) this.prefixes.set(i1, null);
        updateScores(0, this.pi.size() - 1);
    }

    private void updateScores(int i1, int i2) {
        for (int i = i1; i <= i2; i++) {
            this.orderHash.put(this.pi.get(i), i);
            this.scores.set(i, null);
        }
    }

    private void recalculate(int p) {
        if (this.prefixes.get(p) == null || !this.prefixes.get(p).containsAll(getPrefix(p))) {
            Pair p2 = getParentsInternal(p);
            if (scores.get(p) == null) {
                this.runningScore += p2.score;
            } else {
                this.runningScore += p2.score - scores.get(p).score;
            }
            this.scores.set(p, p2);
        }
    }

    private double sum() {
        double score = 0;

        for (int i = 0; i < this.pi.size(); i++) {
            if (this.scores.get(i) == null) {
                recalculate(i);
            }
            score += this.scores.get(i).getScore();
        }

        return score;
    }

    private void nodesHash(Map<Node, Integer> nodesHash, List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            nodesHash.put(variables.get(i), i);
        }
    }

    private boolean lastMoveSame(int i1, int i2) {
        if (i1 <= i2) {
            Set<Node> prefix0 = getPrefix(i1);

            for (int i = i1; i <= i2; i++) {
                prefix0.add(get(i));
                if (!prefix0.equals(this.prefixes.get(i))) return false;
            }
        } else {
            Set<Node> prefix0 = getPrefix(i1);

            for (int i = i2; i <= i1; i++) {
                prefix0.add(get(i));
                if (!prefix0.equals(this.prefixes.get(i))) return false;
            }
        }

        return true;
    }

    @NotNull
    private Pair getGrowShrinkScore(int p) {
        Node n = this.pi.get(p);
        Set<Node> prefix = new HashSet<>(getPrefix(p));
        Set<Node> all = new HashSet<>(this.variables);
        HashSet<Node> parents = new LinkedHashSet<>();
        double sMax = this.trees.get(n).trace(prefix, all, parents);

        return new Pair(parents, Double.isNaN(sMax) ? Double.NEGATIVE_INFINITY : sMax);
    }

    private Pair getGrowShrinkIndependent(int p) {
        Node n = this.pi.get(p);
        Set<Node> parents = new HashSet<>();
        Set<Node> prefix = getPrefix(p);

        boolean changed1 = true;
        while (changed1) {
            changed1 = false;

            for (Node z0 : prefix) {
                if (parents.contains(z0)) continue;
                if (!knowledge.isEmpty() && this.knowledge.isForbidden(z0.getName(), n.getName())) continue;
                if (!knowledge.isEmpty() && this.knowledge.isRequired(z0.getName(), n.getName())) {
                    parents.add(z0);
                    continue;
                }
                if (this.test.checkIndependence(n, z0, new HashSet<>(parents)).isDependent()) {
                    parents.add(z0);
                    changed1 = true;
                }
            }

            for (Node z1 : new HashSet<>(parents)) {
                if (!knowledge.isEmpty() && this.knowledge.isRequired(z1.getName(), n.getName())) {
                    continue;
                }
                parents.remove(z1);
                if (this.test.checkIndependence(n, z1, new HashSet<>(parents)).isDependent()) {
                    parents.add(z1);
                } else {
                    changed1 = true;
                }
            }
        }

        return new Pair(parents, -parents.size());
    }

    private Pair getParentsInternal(int p) {
        if (this.useRaskuttiUhler) {
            return getRaskuttiUhlerParents(p);
        } else {
            if (this.useScore) {
                return getGrowShrinkScore(p);
            } else {
                return getGrowShrinkIndependent(p);
            }
        }
    }

    /**
     * Returns the parents of the node at index p, calculated using Pearl's method.
     *
     * @param p The index.
     * @return The parents, as a Pair object (parents + score).
     */
    private Pair getRaskuttiUhlerParents(int p) {
        Node x = this.pi.get(p);
        Set<Node> parents = new HashSet<>();
        Set<Node> prefix = getPrefix(p);

        for (Node y : prefix) {
            Set<Node> minus = new HashSet<>(prefix);
            minus.remove(y);
            Set<Node> z = new HashSet<>(minus);

            if (this.test.checkIndependence(x, y, z).isDependent()) {
                parents.add(y);
            }
        }

        return new Pair(parents, -parents.size());
    }

    private static class Pair {
        private final Set<Node> parents;
        private final double score;

        private Pair(Set<Node> parents, double score) {
            this.parents = parents;
            this.score = score;
        }

        public Set<Node> getParents() {
            return this.parents;
        }

        public double getScore() {
            return this.score;
        }

        public int hashCode() {
            return this.parents.hashCode() + (int) floor(10000D * this.score);
        }

        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof Pair thatPair)) return false;
            return this.parents.equals(thatPair.parents) && this.score == thatPair.score;
        }
    }
}
