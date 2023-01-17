package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;


/**
 * Implements a scorer extending Teyssier, M., and Koller, D. (2012). Ordering-based search: A simple and effective
 * algorithm for learning Bayesian networks. arXiv preprint arXiv:1207.1429. You give it a score function
 * and a variable ordering, and it computes the score. You can move any variable left or right, and it will
 * keep track of the score using the Teyssier and Kohler method. You can move a variable to a new position,
 * and you can bookmark a state and come back to it.
 *
 * @author josephramsey
 * @author bryanandrews
 */
public class TeyssierScorerOpt {
    private final List<Node> variables;
    private final Map<Node, Integer> variablesHash;
    private final Score score;

    private final Map<Node, Map<Set<Node>, Float>> cache = new HashMap<>();
    private final Map<Node, Integer> orderHash;
    private ArrayList<Node> pi; // The current permutation.
    private ArrayList<Pair> scores = new ArrayList<>();
    private Knowledge knowledge = new Knowledge();
    private ArrayList<Set<Node>> prefixes;

    public TeyssierScorerOpt(@NotNull Score score) {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        this.score = score;

        this.variables = score.getVariables();
        this.pi = new ArrayList<>(this.variables);

        this.orderHash = new HashMap<>();
        nodesHash(this.orderHash, this.pi);

        this.variablesHash = new HashMap<>();
        nodesHash(this.variablesHash, this.variables);
    }

    /**
     * @param knowledge Knowledge of forbidden edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    /**
     * Scores the given permutation. This needs to be done initially before any move or tuck
     * operations are performed.
     *
     * @param order The permutation to score.
     * @return The score of it.
     */
    public float score(List<Node> order) {
        this.pi = new ArrayList<>(order);
        this.scores = new ArrayList<>();

        for (int i1 = 0; i1 < order.size(); i1++) {
            this.scores.add(null);
        }

        this.prefixes = new ArrayList<>();
        for (int i1 = 0; i1 < order.size(); i1++) this.prefixes.add(new HashSet<>());
        initializeScores();
        return score();
    }

    /**
     * @return The score of the current permutation.
     */
    public float score() {
        return sum();
    }

    private float sum() {
        float score = 0;

        for (int i = 0; i < this.pi.size(); i++) {
            float score1 = this.scores.get(i).getScore();
            score += score1;
        }

        return score;
    }

    /**
     * Moves v to a new index.
     *
     * @param v       The variable to move.
     * @param toIndex The index to move v to.
     */
    public void moveTo(Node v, int toIndex) {
//        if (!this.pi.contains(v)) return;

        int vIndex = index(v);

        if (vIndex == toIndex) return;

//        if (lastMoveSame(vIndex, toIndex)) return;

        this.pi.remove(v);
        this.pi.add(toIndex, v);

        if (toIndex < vIndex) {
            updateScores(toIndex, vIndex);
        } else {
            updateScores(vIndex, toIndex);
        }
    }

    public void demote(Node v) {
        int vIndex = index(v);
        int toIndex = vIndex + 1;

        if (toIndex > size() - 1) return;

        this.pi.remove(v);
        this.pi.add(toIndex, v);

        updateScores(vIndex, toIndex);
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
     * @return A copy of the current permutation.
     */
    public List<Node> getPi() {
        return new ArrayList<>(this.pi);
    }

    /**
     * Return the index of v in the current permutation.
     *
     * @param v The variable.
     * @return Its index.
     */
    public int index(Node v) {
        if (!this.orderHash.containsKey(v)) {
            System.out.println();
        }

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
        return new HashSet<>(this.scores.get(p).getParents());
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
        List<Node> order = getPi();
        Graph G1 = new EdgeListGraph(this.variables);

        for (int p = 0; p < order.size(); p++) {
            for (Node z : getParents(p)) {
                G1.addDirectedEdge(z, order.get(p));
            }
        }

        GraphUtils.replaceNodes(G1, this.variables);

        if (cpDag) {
            return SearchGraphUtils.cpdagForDag(G1);
        } else {
            return G1;
        }
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

    public Set<Node> getAncestors(Node node) {
        Set<Node> ancestors = new HashSet<>();
        collectAncestorsVisit(node, ancestors);

        return ancestors;
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
     * @return The size of pi, the current permutation.
     */
    public int size() {
        return this.pi.size();
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

    private boolean violatesKnowledge(List<Node> order) {
        if (!knowledge.isEmpty()) {
            for (int i = 0; i < order.size(); i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    if (this.knowledge.isForbidden(order.get(i).getName(), order.get(j).getName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void initializeScores() {
        for (int i1 = 0; i1 < this.pi.size(); i1++) this.prefixes.set(i1, null);

        for (int i = 0; i < this.pi.size(); i++) {
            recalculate(i);
            this.orderHash.put(this.pi.get(i), i);
        }
    }

    public void updateScores(int i1, int i2) {
        for (int i = i1; i <= i2; i++) {
            recalculate(i);
            this.orderHash.put(this.pi.get(i), i);
        }
    }

    private float score(Node n, Set<Node> pi) {
        pi = new HashSet<>(pi);

        this.cache.computeIfAbsent(n, w -> new HashMap<>());
        Float score = this.cache.get(n).get(pi);

        if (score != null) {
            return score;
        }

        int[] parentIndices = new int[pi.size()];

        int k = 0;

        for (Node p : pi) {
            parentIndices[k++] = this.variablesHash.get(p);
        }

        float v = (float) this.score.localScore(this.variablesHash.get(n), parentIndices);

        this.cache.computeIfAbsent(n, w -> new HashMap<>());
        this.cache.get(n).put(pi, v);

        return v;
    }

    private Set<Node> getPrefix(int i) {
        Set<Node> prefix = new HashSet<>();

        for (int j = 0; j < i; j++) {
            prefix.add(this.pi.get(j));
        }

        return prefix;
    }

    private void recalculate(int p) {
        Set<Node> prefix = getPrefix(p);
        Pair pair = getGrowShrinkScore(p, prefix);
        this.scores.set(p, pair);
    }

    private void nodesHash(Map<Node, Integer> nodesHash, List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            nodesHash.put(variables.get(i), i);
        }
    }

    private boolean lastMoveSame(int i1, int i2) {
        if (i1 <= i2) {
            for (int i = i1; i <= i2; i++) {
                if (!getPrefix(i).equals(this.prefixes.get(i))) return false;
            }
        } else {
            for (int i = i2; i <= i1; i++) {
                if (!getPrefix(i).equals(this.prefixes.get(i))) return false;
            }
        }

        return true;
    }

    @NotNull
    private Pair getGrowShrinkScore(int p, Set<Node> prefix) {
        Node n = this.pi.get(p);

        Set<Node> parents = new HashSet<>();
        float sMax = score(n, new HashSet<>());

        boolean changed = true;

        // Grow-shrink
        while (changed) {
            changed = false;

            // Let z be the node that maximizes the score...
            Node z = null;

            for (Node z0 : prefix) {
                if (parents.contains(z0)) continue;

                if (!knowledge.isEmpty() && this.knowledge.isForbidden(z0.getName(), n.getName())) continue;
                parents.add(z0);

                float s2 = score(n, parents);

                if (s2 >= sMax) {
                    sMax = s2;
                    z = z0;
                }

                parents.remove(z0);
            }

            if (z != null) {
                parents.add(z);
                changed = true;
            }

        }

        boolean changed2 = true;

        while (changed2) {
            changed2 = false;

            Node w = null;

            for (Node z0 : new HashSet<>(parents)) {
                parents.remove(z0);

                float s2 = score(n, parents);

                if (s2 >= sMax) {
                    sMax = s2;
                    w = z0;
                }

                parents.add(z0);
            }

            if (w != null) {
                parents.remove(w);
                changed2 = true;
            }
        }

        return new Pair(parents, Float.isNaN(sMax) ? Float.NEGATIVE_INFINITY : sMax);
    }

    private static class Pair {
        private final Set<Node> parents;
        private final float score;


        private Pair(Set<Node> parents, float score) {
            this.parents = parents;
            this.score = score;
        }

        public Set<Node> getParents() {
            return this.parents;
        }

        public float getScore() {
            return this.score;
        }

        public int hashCode() {
            return this.parents.hashCode();// + (int) floor(10000 * this.score);
        }

        public boolean equals(Object o) {
            if (o == null) return false;
            if (!(o instanceof Pair)) return false;
            Pair thatPair = (Pair) o;
            return this.parents.equals(thatPair.parents);// && this.score == thatPair.score;
        }
    }
}
