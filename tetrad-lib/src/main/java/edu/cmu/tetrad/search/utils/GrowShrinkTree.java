package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * <p>Provides a tree-based score-caching data structure useful for permutation searches.
 * The use of this caching data structure speeds these algorithms up
 * significantly.</p>
 *
 * @author bryanandrews
 */
public class GrowShrinkTree {
    private final Score score;
    private final Map<Node, Integer> index;
    private final Node node;
    private final int nodeIndex;
    private List<Node> required;
    private List<Node> forbidden;
    private GSTNode root;

    /**
     * Constructor for a grow-shrink tree.
     *
     * @param score A score.
     * @param index A node-index map.
     * @param node The root node.
     */
    public GrowShrinkTree(Score score, Map<Node, Integer> index, Node node) {
        this.score = score;
        this.index = index;
        this.node = node;
        this.nodeIndex = index.get(node);
        this.root = new GSTNode(this);
        this.required = new ArrayList<>();
        this.forbidden = new ArrayList<>();
    }

    /**
     * Trace down the tree and updates parents.
     *
     * @param prefix The prefix (mutated during call).
     * @param parents The parents (modified in place).
     * @return The grow-shrink score.
     */
    public double trace(Set<Node> prefix, Set<Node> parents) {
        return this.root.trace(prefix, parents);
    }

    /**
     * Gets the root node.
     *
     * @return The root node.
     */
    public Node getNode() {
        return this.node;
    }

    /**
     * Gets the index of a node.
     *
     * @param node The node.
     * @return The index.
     */
    public Integer getIndex(Node node) {
        return this.index.get(node);
    }

    /**
     * Wraps the localScore call of the score.
     *
     * @return The value return by the score.
     */
    public Double localScore() {
        return this.score.localScore(this.nodeIndex);
    }

    /**
     * Wraps the localScore call of the score.
     *
     * @param X The parents.
     * @return The value return by the score.
     */
    public Double localScore(int[] X) {
        return this.score.localScore(this.nodeIndex, X);
    }

    /**
     * True if node is a required parent.
     *
     * @param node The node.
     * @return The boolean.
     */
    public boolean isRequired(Node node) {
        return this.required.contains(node);
    }

    /**
     * True if node is a forbidden parent.
     *
     * @param node The node.
     * @return The boolean.
     */
    public boolean isForbidden(Node node) {
        return this.forbidden.contains(node);
    }

    /**
     * Gets the list of variables.
     *
     * @return The list of variables.
     */
    public List<Node> getVariables() {
        return this.score.getVariables();
    }

    /**
     * Gets the list of required parents.
     *
     * @return The list of required parents.
     */
    public List<Node> getRequired() {
        return this.required;
    }

    /**
     * Gets the list of forbidden parents.
     *
     * @return The list of forbidden parents.
     */
    public List<Node> getForbidden() {
        return this.forbidden;
    }

    /**
     * Sets the background knowledge.
     *
     * @param required A list of required parents.
     * @param forbidden A list of forbidden parents.
     */
    public void setKnowledge(List<Node> required, List<Node> forbidden) {
        this.root = new GSTNode(this);
        this.required = required;
        this.forbidden = forbidden;
    }

    private static class GSTNode implements Comparable<GSTNode> {
        private final GrowShrinkTree tree;
        private final Node add;
        private boolean grow;
        private boolean shrink;
        private final double growScore;
        private double shrinkScore;
        private List<GSTNode> branches;
        private Set<Node> remove;

        private GSTNode(GrowShrinkTree tree) {
            this.tree = tree;
            this.add = null;
            this.grow = false;
            this.shrink = false;

            this.growScore = this.tree.localScore();
        }

        private GSTNode(GrowShrinkTree tree, Node add, Set<Node> parents) {
            this.tree = tree;
            this.add = add;
            this.grow = false;
            this.shrink = false;

            int i = 0;
            int[] X = new int[parents.size() + 1];
            for (Node parent : parents) X[i++] = this.tree.getIndex(parent);
            X[i] = this.tree.getIndex(add);

            this.growScore = this.tree.localScore(X);
        }

        public double trace(Set<Node> prefix, Set<Node> parents) {

            if (!this.grow) {
                this.grow = true;
                this.branches = new ArrayList<>();
                List<GSTNode> required = new ArrayList<>();

                for (Node add : this.tree.getVariables()) {
                    if (parents.contains(add) || add == this.tree.getNode()) continue;
                    if (this.tree.isForbidden(add)) continue;
                    GSTNode branch = new GSTNode(this.tree, add, parents);
                    if (this.tree.isRequired(add)) required.add(branch);
                    else if (branch.getGrowScore() >= this.growScore) this.branches.add(branch);
                }

                this.branches.sort(Collections.reverseOrder());
                this.branches.addAll(0, required);
            }

            for (GSTNode branch : this.branches) {
                Node add = branch.getAdd();
                if (prefix.contains(add)) {
                    prefix.remove(add);
                    parents.add(add);
                    return branch.trace(prefix, parents);
                }
            }

            if (!this.shrink) {
                this.shrink = true;
                this.remove = new HashSet<>();
                this.shrinkScore = this.growScore;
                if (parents.isEmpty()) return this.shrinkScore;

                Node best;
                do {
                    best = null;
                    int[] X = new int[parents.size() - 1];

                    for (Node remove : new HashSet<>(parents)) {
                        if (this.tree.isRequired(remove)) continue;
                        int i = 0;
                        parents.remove(remove);
                        for (Node parent : parents) X[i++] = this.tree.getIndex(parent);
                        parents.add(remove);

                        double s = this.tree.localScore(X);
                        if (s > this.shrinkScore) {
                            this.shrinkScore = s;
                            best = remove;
                        }
                    }

                    if (best != null) {
                        parents.remove(best);
                        this.remove.add(best);
                    }
                } while (best != null);

            }
            parents.removeAll(this.remove);
            return this.shrinkScore;
        }

        public Node getAdd() {
            return this.add;
        }

        public double getGrowScore() {
            return this.growScore;
        }

        @Override
        public int compareTo(@NotNull GrowShrinkTree.GSTNode branch) {
            return Double.compare(this.growScore, branch.getGrowScore());
        }
    }
}

