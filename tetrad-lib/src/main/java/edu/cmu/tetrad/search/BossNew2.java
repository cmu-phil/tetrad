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


    public BossNew2(Score score) {
        this.bes = new Bes(score);
        this.bes.setVerbose(false);
        this.score = score;

        this.variables = score.getVariables();
        this.parents = new HashMap<>();

        for (Node x : this.variables) this.parents.put(x, new HashSet<>());

        this.scores = new HashMap<>();

    }

    @Override
    public void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts, int numStarts) {

        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < numStarts; i++) {
            shuffle(suborder);

            double s1, s2, s3;
            s1 = update(prefix, suborder, parents, scores, gsts);
            do {
                s2 = s1;

                do {
                    s3 = s1;
                    for (Node x : new ArrayList<>(suborder)) {
                        if (betterMutation(prefix, suborder, x, gsts)) s1 = update(prefix, suborder, parents,
                            scores, gsts);
                    }
                } while (s1 > s3);

                do {
                    s3 = s1;
                    List<Node> Z = new ArrayList<>(prefix);
                    Z.addAll(suborder);
                    Graph graph = PermutationSearch2.getGraph(Z, parents, true);
                    this.bes.bes(graph, Z);
                    validOrder(graph, suborder);
                    s1 = update(prefix, suborder, parents, scores, gsts);
                } while (s1 > s3);

            } while (s1 > s2);

            if (s1 > bestScore) {
                bestScore = s1;
            }
        }
    }

    private boolean betterMutation(List<Node> prefix, List<Node> suborder, Node x, Map<Node, GrowShrinkTree> gsts) {
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
            if (scores[i] > scores[best]) best = i;
        }

        if (best == curr) return false;
        else if (best > curr) best--;

        suborder.remove(curr);
        suborder.add(best, x);

        return true;
    }

    private double update(List<Node> prefix, List<Node> suborder, Map<Node, Set<Node>> parents
            , Map<Node, Double> scores, Map<Node, GrowShrinkTree> gsts) {
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

    private void validOrder(Graph graph, List<Node> order) {
        List<Node> initialOrder = new ArrayList<>(order);
        Graph _graph = new EdgeListGraph(graph);

        Collections.reverse(initialOrder);
        order.clear();

        while (!initialOrder.isEmpty()) {
            Iterator<Node> itr = initialOrder.iterator();
            Node x;
            do {
                if (itr.hasNext()) x = itr.next();
                else throw new IllegalArgumentException("This graph has a cycle.");
            } while (graph.paths().invalidSink(x, _graph));
            order.add(x);
            _graph.removeNode(x);
            itr.remove();
        }

        Collections.reverse(order);
    }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.bes.setDepth(depth);
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.bes.setKnowledge(knowledge);
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



