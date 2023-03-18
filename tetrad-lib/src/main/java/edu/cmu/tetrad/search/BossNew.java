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
public class BossNew extends PermutationSearch {
    private final Bes bes;


    public BossNew(Score score) {
        super(score);

        this.bes = new Bes(score);
        this.bes.setVerbose(false);

        for (Node x : this.variables) this.parents.put(x, new HashSet<>());
    }


    @Override
    public void subroutine(List<Node> prefix, List<Node> suborder) {

        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < this.numStarts; i++) {
            shuffle(suborder);

            double s1, s2, s3;
            s1 = this.update(prefix, suborder);
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
                    Graph graph = this.getGraph(Z, true);
                    this.bes.bes(graph, Z);
                    validOrder(graph, suborder);
                    s1 = update(prefix, suborder);
                } while (s1 > s3);

            } while (s1 > s2);

            if (s1 > bestScore) {
                bestScore = s1;
            }
        }
    }


    public boolean betterMutation(List<Node> prefix, List<Node> suborder, Node x) {
        ListIterator<Node> itr = suborder.listIterator();
        double[] scores = new double[suborder.size() + 1];
        int i = 0;

        Set<Node> Z = new HashSet<>(prefix);
        double score = 0;
        int curr = 0;

        while (itr.hasNext()) {
            Node z = itr.next();
            scores[i++] = this.gsts.get(x).trace(new HashSet<>(Z), new HashSet<>()) + score;
            if (z != x) {
                score += this.gsts.get(z).trace(new HashSet<>(Z), new HashSet<>());
                Z.add(z);
            } else curr = i - 1;
        }
        scores[i] = this.gsts.get(x).trace(new HashSet<>(Z), new HashSet<>()) + score;

        Z.add(x);
        score = 0;
        int best = i;

        while (itr.hasPrevious()) {
            Node z = itr.previous();
            if (z != x) {
                Z.remove(z);
                score += this.gsts.get(z).trace(new HashSet<>(Z), new HashSet<>());
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

    private double update(List<Node> prefix, List<Node> suborder) {
        double score = 0;

        Iterator<Node> itr = suborder.iterator();
        Set<Node> Z = new HashSet<>(prefix);
        while (itr.hasNext()) {
            Node x = itr.next();
            this.parents.get(x).clear();
            this.scores.put(x, this.gsts.get(x).trace(new HashSet<>(Z), this.parents.get(x)));
            score += this.scores.get(x);
            Z.add(x);
        }

        return score;
    }

    public void validOrder(Graph graph, List<Node> order) {
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
        super.setKnowledge(knowledge);
        this.bes.setKnowledge(knowledge);
    }
}



