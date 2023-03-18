package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GrowShrinkTree {
    private final Score score;
    private final Map<Node, Integer> index;
    private final Node node;
    private final int nodeIndex;
    private final List<Node> required;
    private final List<Node> forbidden;
    private final GSTNode root;

    public GrowShrinkTree(Score score,  Map<Node, Integer> index, Node node, List<Node> required, List<Node> forbidden) {
        this.score = score;
        this.index = index;
        this.node = node;
        this.nodeIndex = index.get(node);
        this.root = new GSTNode(this);
        this.required = required;
        this.forbidden = forbidden;
    }

    public GrowShrinkTree(Score score, Map<Node, Integer> index, Node node) {
        this(score, index, node, new ArrayList<>(), new ArrayList<>());
    }

    public double trace(Set<Node> prefix, Set<Node> parents) {
        return this.root.trace(prefix, parents);
    }

    public Node getNode() {
        return this.node;
    }

    public Integer getIndex(Node node) {
        return this.index.get(node);
    }

    public Double localScore() {
        return this.score.localScore(this.nodeIndex);
    }

    public Double localScore(int[] X) {
        return this.score.localScore(this.nodeIndex, X);
    }

    public boolean isRequired(Node node) {
        return this.required.contains(node);
    }

    public boolean isForbidden(Node node) {
        return this.forbidden.contains(node);
    }

    public List<Node> getVariables() {
        return this.score.getVariables();
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
                } while(best != null);

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

