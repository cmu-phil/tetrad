package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * Implements the BOSS algorithm.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class GraspNew implements SuborderSearch {
    private final Score score;
    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private final Map<Node, Double> scores;
    private Map<Node, GrowShrinkTree> gsts;
    private int numStarts = 1;
    private int depth = 4;
    private Knowledge knowledge;


    public GraspNew(Score score) {
        this.score = score;
        this.variables = score.getVariables();
        this.parents = new HashMap<>();
        this.scores = new HashMap<>();

        for (Node node : this.variables) {
            this.parents.put(node, new HashSet<>());
        }
    }

    @Override
    public void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts) {
        this.gsts = gsts;
        makeValidKnowledgeOrder(suborder);
        List<Node> bestSuborder = new ArrayList<>(suborder);
        double bestScore = update(prefix, suborder);

        for (int i = 0; i < this.numStarts; i++) {
            shuffle(suborder);
            makeValidKnowledgeOrder(suborder);

            double s1, s2;
            s1 = update(prefix, suborder);
            do {
                s2 = s1;
                if (graspDfs(prefix, suborder)) s1 = update(prefix, suborder);
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



    private boolean graspDfs(List<Node> prefix, List<Node> suborder) {


        //        THIS NEEDS TO NOT VIOLATE KNOWLEDGE!!!

        List<Node> tuck = new ArrayList<>(suborder);

        for (int i = suborder.size(); i > 0; i--) {

            Set<Node> ancestors = new HashSet<>();
            collectAncestorsVisit(suborder.get(i), ancestors);

            for (int j = i; j > 0; j--) {

                if (this.parents.get(suborder.get(i)).contains(suborder.get(j))) {

                }
                if (ancestors.contains(suborder.get(j))) {



                }


            boolean covered;
            boolean singular;


        }


        }
        return false;

    }


    private boolean covered(Node x, Node y) {
        for (Node parent : this.parents.get(x)) {
            if (parent == y) continue;
            if (!this.parents.get(y).contains(parent)) return false;
        }

        return true;
    }


    private void collectAncestorsVisit(Node node, Set<Node> ancestors) {
        if (!ancestors.contains(node)) {
            for (Node parent : this.parents.get(node)) {
                collectAncestorsVisit(parent, ancestors);
            }
        }
    }



    private void graspDfs(@NotNull TeyssierScorer scorer, double sOld, int[] depth, int currentDepth) {
        for (Node y : scorer.getShuffledVariables()) {
            Set<Node> ancestors = scorer.getAncestors(y);
            List<Node> parents = new ArrayList<>(scorer.getParents(y));
            Collections.shuffle(parents);
            for (Node x : parents) {

                boolean covered = scorer.coveredEdge(x, y);
                boolean singular = true;

                if (currentDepth > depth[1] && !covered) continue;

                int i = scorer.index(x);
                int j = scorer.index(y);
                scorer.bookmark(currentDepth);


//                THIS IS THE TUCK!

                boolean first = true;
                List<Node> Z = new ArrayList<>(scorer.getOrderShallow().subList(i + 1, j));
                Iterator<Node> zItr = Z.iterator();
                do {
                    if (first) {
                        scorer.moveTo(y, i);
                        first = false;
                    } else {
                        Node z = zItr.next();
                        if (ancestors.contains(z)) {
                            if (scorer.getParents(z).contains(x)) {
                                singular = false;
                            }
                            scorer.moveTo(z, i++);
                        }
                    }
                } while (zItr.hasNext());


                if (currentDepth > depth[2] && !singular) {
                    scorer.goToBookmark(currentDepth);
                    continue;
                }

                double sNew = scorer.score();
                if (sNew > sOld) {
                    return;
                }

                if (sNew == sOld && currentDepth < depth[0]) {
                    if (currentDepth > depth[1]) {
                        graspDfs(scorer, sOld, depth, currentDepth + 1);
                    } else {
                        graspDfs(scorer, sOld, depth, currentDepth + 1);
                    }
                }

                if (scorer.score() > sOld) return;

                scorer.goToBookmark(currentDepth);
            }
        }
    }


























    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
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

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public Map<Node, Set<Node>> getParents() {
        return parents;
    }

    @Override
    public Score getScore() {
        return score;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
