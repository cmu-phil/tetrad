package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import java.util.*;

import static java.lang.Double.NEGATIVE_INFINITY;

/**
 * Implements the BOSS DC Experimental algorithm.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class Bdce {
    private final List<Node> variables;
    private final TeyssierScorerExperimental scorer;
    private final Bes bes;
    private boolean verbose = true;
    private int depth = -1;
    private int numStarts = 1;


    public Bdce(Score score) {
        this.variables = new ArrayList<>(score.getVariables());
        this.scorer = new TeyssierScorerExperimental(score);
        this.bes = new Bes(score);
        this.bes.setDepth(this.depth);
        this.bes.setVerbose(false);
    }


    public Graph search() {
        Graph bestGraph = null;
        double best = NEGATIVE_INFINITY;

        for (int r = 0; r < this.numStarts; r++) {
            this.scorer.shuffleOrder();
            Graph graph;

            double s1, s2;

            s2 = this.scorer.getScore();
            do {
//                System.out.println("dividing...");
//                do {
                    s1 = s2;
                    divide(0, this.scorer.size() / 2, this.scorer.size());
//                    s2 = this.scorer.getScore();
//                } while (s2 > s1);
//                System.out.println("bes...");
                graph = this.scorer.getGraph(true);
                this.bes.bes(graph, this.variables);
                this.scorer.setOrder(graph);
                s2 = scorer.getScore();
            } while (s2 > s1);

            if (s2 < best) continue;
            bestGraph = this.scorer.getGraph(true);
            best = s2;
        }

        return bestGraph;
    }


    public void divide(int a, int b, int c) {
        if (a < (b - 1)) {
            divide(a, (a + b) / 2, b);
        }
        if (b < (c - 1)) {
            divide(b, (b + c) / 2, c);
        }
        conquer(a, b, c);
    }


    public void conquer(int a, int b, int c) {
        for (int i = b; i < c; i++) {
            for (int j = a; j < b; j++) {
                if (!scorer.hasParent(i, j)) continue;
                if (this.scorer.tuck(i, j)) break;
            }
        }
    }


    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }


    public List<Node> getVariables() {
        return this.variables;
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
}