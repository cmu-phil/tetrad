package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GrowShrinkTree {
    private static Score score;
    private static HashMap<Node, Integer> index;
    private final Map<Node, GSTNode> roots;

    public GrowShrinkTree(Score score) {
        GrowShrinkTree.score = score;
        GrowShrinkTree.index = new HashMap<>();

        int i = 0;
        for (Node node : score.getVariables()) GrowShrinkTree.index.put(node, i++);

        this.roots = new HashMap<>();
        for (Node node : GrowShrinkTree.score.getVariables()) {
            this.roots.put(node, new GSTNode(node));
        }
    }

    public double growShrink(Node node, Set<Node> prefix, Set<Node> parents) {
        return this.roots.get(node).growShrink(node, prefix, parents);
    }

    private static class GSTNode implements Comparable<GSTNode> {
        private final Node add;
        private boolean grow;
        private boolean shrink;
        private final double growScore;
        private double shrinkScore;
        private List<GSTNode> branches;
        private Set<Node> remove;

        private GSTNode(Node node) {
            this.add = null;
            this.grow = false;
            this.shrink = false;

            int y = GrowShrinkTree.index.get(node);
            this.growScore = GrowShrinkTree.score.localScore(y);
        }

        private GSTNode(Node node, Node add, Set<Node> parents) {
            this.add = add;
            this.grow = false;
            this.shrink = false;

            int y = GrowShrinkTree.index.get(node);
            int[] X = new int[parents.size() + 1];

            int i = 0;
            for (Node parent : parents) X[i++] = GrowShrinkTree.index.get(parent);
            X[i] = GrowShrinkTree.index.get(add);

            this.growScore = GrowShrinkTree.score.localScore(y, X);
        }

        public double growShrink(Node node, Set<Node> prefix, Set<Node> parents) {

            if (!this.grow) {
                this.grow = true;
                this.branches = new ArrayList<>();

                for (Node add : GrowShrinkTree.score.getVariables()) {
                    if (parents.contains(add) || add == node) continue;
                    GSTNode branch = new GSTNode(node, add, parents);
                    if (branch.getGrowScore() >= this.growScore) this.branches.add(branch);
                }
                this.branches.sort(Collections.reverseOrder());
            }

            for (GSTNode branch : this.branches) {
                Node add = branch.getAdd();
                if (prefix.contains(add)) {
                    prefix.remove(add);
                    parents.add(add);
                    return branch.growShrink(node, prefix, parents);
                }
            }

            if (!this.shrink) {
                this.shrink = true;
                this.remove = new HashSet<>();
                this.shrinkScore = this.growScore;

                if (parents.isEmpty()) return this.shrinkScore;

                Node best;
                int y = GrowShrinkTree.index.get(node);

                do {

                    best = null;
                    int[] X = new int[parents.size() - 1];

                    for (Node remove : new HashSet<>(parents)) {

                        int i = 0;
                        parents.remove(remove);
                        for (Node parent : parents) X[i++] = GrowShrinkTree.index.get(parent);
                        parents.add(remove);

                        double s = score.localScore(y, X);

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

