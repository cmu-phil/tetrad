package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GrowShrinkTree {
    private static Score score;
    private static List<Node> variables;
    private final Map<Node, GSTNode> roots;

    public GrowShrinkTree(Score score) {
        GrowShrinkTree.score = score;
        GrowShrinkTree.variables = score.getVariables();
        this.roots = new HashMap<>();
        for (Node node : GrowShrinkTree.variables) {
            this.roots.put(node, new GSTNode(node));
        }
    }

    public double GrowShrink(Node node, Set<Node> prefix, LinkedHashSet<Node> parents) {
        return this.roots.get(node).GrowShrink(node, prefix, parents);
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

            int y = GrowShrinkTree.variables.indexOf(node);
            this.growScore = GrowShrinkTree.score.localScore(y);
        }

        private GSTNode(Node node, Node add, Set<Node> parents) {
            this.add = add;
            this.grow = false;
            this.shrink = false;

            int y = GrowShrinkTree.variables.indexOf(node);
            int[] X = new int[parents.size() + 1];

            int i = 0;
            for (Node parent : parents) X[i++] = GrowShrinkTree.variables.indexOf(parent);
            X[i] = GrowShrinkTree.variables.indexOf(add);

            this.growScore = GrowShrinkTree.score.localScore(y, X);
        }

        public double GrowShrink(Node node, Set<Node> prefix, LinkedHashSet<Node> parents) {

            if (!this.grow) {
                this.grow = true;
                this.branches = new ArrayList<>();

                for (Node add : GrowShrinkTree.variables) {
                    if (parents.contains(add) || add == node) continue;
                    GSTNode branch = new GSTNode(node, add, parents);
                    if (this.compareTo(branch) < 0) this.branches.add(branch);
                }
                this.branches.sort(Collections.reverseOrder());
            }

            for (GSTNode branch : this.branches) {
                Node add = branch.getAdd();
                if (prefix.contains(add)) {
                    prefix.remove(add);
                    parents.add(add);
                    return branch.GrowShrink(node, prefix, parents);
                }
            }

            if (!this.shrink) {
                this.shrink = true;
                this.remove = new HashSet<>();
                this.shrinkScore = this.growScore;

                if (parents.isEmpty()) return this.shrinkScore;

                int y = GrowShrinkTree.variables.indexOf(node);
                int[] X = new int[parents.size() - 1];

                int i = 0;
                Iterator<Node> itr = parents.iterator();
                itr.next();
                while (itr.hasNext()) X[i++] = GrowShrinkTree.variables.indexOf(itr.next());
                Node best;

                do {
                    i = 0;
                    itr = parents.iterator();
                    Node remove = itr.next();
                    best = null;

                    do {
                        double s = GrowShrinkTree.score.localScore(y, X);
                        if (s > this.shrinkScore) {
                            this.shrinkScore = s;
                            best = remove;
                        }

                        if (i < parents.size() - 1) {
                            remove = itr.next();
                            X[i++] = GrowShrinkTree.variables.indexOf(remove);
                        }
                    } while (i < parents.size() - 1);

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

