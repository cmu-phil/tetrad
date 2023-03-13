package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * Implements the BOSS algorithm.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class Boss {
    private List<Node> order;
    private final List<Node> variables;
    private final Map<Node, Set<Node>> parents;
    private final Map<Node, Double> scores;
    private final GrowShrinkTree gst;
    private final Bes bes;
    private int depth = -1;
    private int numStarts = 1;
    private boolean verbose = true;
    private BossOld.AlgType algType = BossOld.AlgType.BOSS1;
//    private double epsilon = 1e-10;


    public Boss(Score score) {
        this.order = new LinkedList<>(score.getVariables());
        this.variables = score.getVariables();
        this.parents = new HashMap<>();
        this.scores = new HashMap<>();
        this.gst = new GrowShrinkTree(score);
        this.bes = new Bes(score);
        this.bes.setDepth(this.depth);
        this.bes.setVerbose(false);

        for (Node x : this.order) this.parents.put(x, new HashSet<>());
    }

    public Graph search() {
//        shuffle(this.order);

        double s1, s2, s3;
        s1 = this.update();
        do { s2 = s1;

            do { s3 = s1;
                for (Node x : new ArrayList<>(this.order)) {
                    if (betterMutation(x)) s1 = update();
                }
            } while(s1 > s3);

            do { s3 = s1;
                Graph graph = this.getGraph(true);
                this.bes.bes(graph, this.variables);
                this.order = new LinkedList<>(graph.paths().validOrder(this.order, true));
                s1 = update();
            } while(s1 > s3);

        } while(s1 > s2);

        return this.getGraph(true);
    }

    public boolean betterMutation(Node x) {
        ListIterator<Node> itr = this.order.listIterator();
        double[] scores = new double[this.order.size() + 1];
        int i = 0;

        Set<Node> Z = new HashSet<>();
        double score = 0;
        int curr = 0;

        while (itr.hasNext()) {
            Node z = itr.next();
            scores[i++] = this.gst.growShrink(x, new HashSet<>(Z), new HashSet<>()) + score;
            if (z != x) {
                score += this.gst.growShrink(z, new HashSet<>(Z), new HashSet<>());
                Z.add(z);
            } else curr = i - 1;
        }
        scores[i] = this.gst.growShrink(x, new HashSet<>(Z), new HashSet<>()) + score;

        Z.add(x);
        score = 0;
        int best = i;

        while (itr.hasPrevious()) {
            Node z = itr.previous();
            if (z != x) {
                Z.remove(z);
                score += this.gst.growShrink(z, new HashSet<>(Z), new HashSet<>());
            }
            scores[--i] += score;
            if (scores[i] > scores[best]) best = i;
        }

//        if (scores[best] > scores[curr] + this.epsilon) return false;
        if (best == curr) return false;
        else if (best > curr) best--;

        this.order.remove(curr);
        this.order.add(best, x);

        return true;
    }

    private double update() {
        double score = 0;

        Iterator<Node> itr = this.order.iterator();
        Set<Node> Z = new HashSet<>();
        while (itr.hasNext()) {
            Node x = itr.next();
            this.parents.get(x).clear();
            this.scores.put(x, this.gst.growShrink(x, new HashSet<>(Z), this.parents.get(x)));
            score += this.scores.get(x);
            Z.add(x);
        }

        return score;
    }

    private Graph getGraph(boolean cpDag) {
        Graph graph = new EdgeListGraph(this.order);

        for (Node a : this.order) {
            for (Node b : this.parents.get(a)) {
                graph.addDirectedEdge(b, a);
            }
        }

        if (cpDag) {
            MeekRules rules = new MeekRules();
            rules.orientImplied(graph);
        }

        return graph;
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
    }

    public void setAlgType(BossOld.AlgType algType) {
        this.algType = algType;
    }

    public enum AlgType {BOSS1}
}