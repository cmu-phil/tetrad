package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * Implements the BOSS algorithm.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class BossNew2 implements SuborderSearch {
    private final Bes bes;
    private final Score score;
    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private final Map<Node, Double> scores;
    private Map<Node, GrowShrinkTree> gsts;
    private int numStarts;
    private Knowledge knowledge;


    public BossNew2(Score score) {
        this.score = score;
        this.variables = score.getVariables();
        this.parents = new HashMap<>();
        this.scores = new HashMap<>();

        for (Node x : this.variables) {
            this.parents.put(x, new HashSet<>());
        }

        this.bes = new Bes(score);
        this.bes.setVerbose(false);
        this.numStarts = 1;
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
            double s1, s2, s3;
            s1 = update(prefix, suborder);
            do {
                s2 = s1;
                do {
                    s3 = s1;
                    for (Node x : new ArrayList<>(suborder)) {
                        if (betterMutation(prefix, suborder, x)) s1 = update(prefix, suborder);
                    }
                } while (s1 > s3);
                do {
                    s3 = s1;
                    List<Node> Z = new ArrayList<>(prefix);
                    Z.addAll(suborder);
                    Graph graph = PermutationSearch2.getGraph(Z, parents, true);
                    this.bes.bes(graph, Z);
                    graph.paths().makeValidOrder(suborder);
                    s1 = update(prefix, suborder);
                } while (s1 > s3);
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

    private boolean betterMutation(List<Node> prefix, List<Node> suborder, Node x) {
        ListIterator<Node> itr = suborder.listIterator();
        double[] scores = new double[suborder.size() + 1];
        int i = 0;

        Set<Node> Z = new HashSet<>(prefix);
        double score = 0;
        int curr = 0;

        while (itr.hasNext()) {
            Node z = itr.next();
            scores[i++] = gsts.get(x).trace(new HashSet<>(Z), new HashSet<>()) + score;
            if (z != x) {
                score += gsts.get(z).trace(new HashSet<>(Z), new HashSet<>());
                Z.add(z);
            } else curr = i - 1;
        }
        scores[i] = gsts.get(x).trace(new HashSet<>(Z), new HashSet<>()) + score;

        Z.add(x);
        score = 0;
        int best = i;

        while (itr.hasPrevious()) {
            Node z = itr.previous();
            if (z != x) {
                Z.remove(z);
                score += gsts.get(z).trace(new HashSet<>(Z), new HashSet<>());
            }
            scores[--i] += score;
            if (scores[i] > scores[best] && does_not_violate_knowlegde) best = i;
        }

        if (best == curr) return false;
        else if (best > curr) best--;

        suborder.remove(curr);
        suborder.add(best, x);

        return true;
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

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.bes.setDepth(depth);
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
        this.bes.setKnowledge(knowledge);
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
}



