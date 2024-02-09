package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>GrowShrinkTree class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
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
     * <p>Constructor for GrowShrinkTree.</p>
     *
     * @param score a {@link edu.cmu.tetrad.search.score.Score} object
     * @param index a {@link java.util.Map} object
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     */
    public GrowShrinkTree(Score score, Map<Node, Integer> index, Node node) {
        this.score = score;
        this.index = index;
        this.node = node;

        this.nodeIndex = index.get(node);
        this.required = new ArrayList<>();
        this.forbidden = new ArrayList<>();
        this.root = new GSTNode(this);
    }

    /**
     * <p>trace.</p>
     *
     * @param prefix a {@link java.util.Set} object
     * @param all a {@link java.util.Set} object
     * @return a double
     */
    public double trace(Set<Node> prefix, Set<Node> all) {
        Set<Node> available = new HashSet<>(all);
        available.remove(this.node);
        this.forbidden.forEach(available::remove);
        Set<Node> parents = new HashSet<>();
        return this.root.trace(prefix, available, parents);
    }

    /**
     * <p>trace.</p>
     *
     * @param prefix a {@link java.util.Set} object
     * @param all a {@link java.util.Set} object
     * @param parents a {@link java.util.Set} object
     * @return a double
     */
    public double trace(Set<Node> prefix, Set<Node> all, Set<Node> parents) {
        Set<Node> available = new HashSet<>(all);
        available.remove(this.node);
        this.forbidden.forEach(available::remove);
        return this.root.trace(prefix, available, parents);
    }

    /**
     * <p>Getter for the field <code>node</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getNode() {
        return this.node;
    }

    /**
     * <p>getFirstLayer.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getFirstLayer() {
        List<Node> firstLayer = new ArrayList<>();
        for (GSTNode branch : this.root.branches) firstLayer.add(branch.getAdd());
        return firstLayer;
    }

    /**
     * <p>Getter for the field <code>index</code>.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.lang.Integer} object
     */
    public Integer getIndex(Node node) {
        return this.index.get(node);
    }

//    public Double localScore() {
//        return this.score.localScore(this.nodeIndex);
//    }
//
//    public Double localScore(int[] X) {
//        return this.score.localScore(this.nodeIndex, X);
//    }

    /**
     * <p>localScore.</p>
     *
     * @return a {@link java.lang.Double} object
     */
    public Double localScore() {
        double score = this.score.localScore(this.nodeIndex);
        return Double.isNaN(score) ? 0 : score;
    }

    /**
     * <p>localScore.</p>
     *
     * @param X an array of {@link int} objects
     * @return a {@link java.lang.Double} object
     */
    public Double localScore(int[] X) {
        double score = this.score.localScore(this.nodeIndex, X);
        return Double.isNaN(score) ? Double.NEGATIVE_INFINITY : score;
    }

    /**
     * <p>isRequired.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isRequired(Node node) {
        return this.required.contains(node);
    }

    /**
     * <p>isForbidden.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isForbidden(Node node) {
        return this.forbidden.contains(node);
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return this.score.getVariables();
    }

    /**
     * <p>Getter for the field <code>required</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getRequired() {
        return this.required;
    }

    /**
     * <p>Getter for the field <code>forbidden</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getForbidden() {
        return this.forbidden;
    }

    /**
     * <p>setKnowledge.</p>
     *
     * @param required a {@link java.util.List} object
     * @param forbidden a {@link java.util.List} object
     */
    public void setKnowledge(List<Node> required, List<Node> forbidden) {
        this.required = required;
        this.forbidden = forbidden;
        this.reset();
    }

    /**
     * <p>reset.</p>
     */
    public void reset() {
        this.root = new GSTNode(this);
    }

    private static class GSTNode implements Comparable<GSTNode> {
        private final GrowShrinkTree tree;
        private final Node add;
        private final double growScore;
        private final AtomicBoolean grow;
        private final AtomicBoolean shrink;
        private double shrinkScore;
        private List<GSTNode> branches;
        private Set<Node> remove;

        private GSTNode(GrowShrinkTree tree) {
            this.tree = tree;
            this.add = null;
            this.grow = new AtomicBoolean(false);
            this.shrink = new AtomicBoolean(false);

            this.growScore = this.tree.localScore();
        }

        private GSTNode(GrowShrinkTree tree, Node add, Set<Node> parents) {
            this.tree = tree;
            this.add = add;
            this.grow = new AtomicBoolean(false);
            this.shrink = new AtomicBoolean(false);

            int i = 0;
            int[] X = new int[parents.size() + 1];
            for (Node parent : parents) X[i++] = this.tree.getIndex(parent);
            X[i] = this.tree.getIndex(add);

            this.growScore = this.tree.localScore(X);
        }

        private synchronized void grow(Set<Node> available, Set<Node> parents) {
            if (this.grow.get()) return;

            this.branches = new ArrayList<>();
            List<GSTNode> required = new ArrayList<>();

            for (Node add : available) {
                GSTNode branch = new GSTNode(this.tree, add, parents);
                if (this.tree.isRequired(add)) required.add(branch);
                else if (branch.getGrowScore() >= this.growScore) this.branches.add(branch);
            }

            this.branches.sort(Collections.reverseOrder());
            this.branches.addAll(0, required);

            this.grow.set(true);
        }

        private synchronized void shrink(Set<Node> parents) {
            if (this.shrink.get()) return;

            this.remove = new HashSet<>();
            this.shrinkScore = this.growScore;
            if (parents.isEmpty()) return;

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

            this.shrink.set(true);
        }

        public double trace(Set<Node> prefix, Set<Node> available, Set<Node> parents) {

            if (!this.grow.get()) grow(available, parents);

            for (GSTNode branch : this.branches) {
                Node add = branch.getAdd();
                available.remove(add);
                if (prefix.contains(add)) {
                    parents.add(add);
                    return branch.trace(prefix, available, parents);
                }
            }

            if (!this.shrink.get()) shrink(parents);

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

