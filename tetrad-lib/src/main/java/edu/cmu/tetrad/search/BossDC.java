package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.NumberFormatUtil;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.*;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.util.Collections.shuffle;

/**
 * Implements the BOSS DC algorithm.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class BossDC {
    private final List<Node> variables;
    private final Score score;
    private final TeyssierScorer scorer;
    private boolean useDataOrder = true;
    private boolean verbose = true;
    private int depth = -1;
    private int numStarts = 1;
    private Boss.AlgType algType = Boss.AlgType.BOSS1;
    private boolean caching = true;


    public BossDC(Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.scorer = new TeyssierScorer(null, this.score);
    }

    public BossDC(TeyssierScorer scorer) {
        this.scorer = scorer;
        this.score = scorer.getScoreObject();
        this.variables = new ArrayList<>(scorer.getPi());
    }

    public List<Node> bestOrder(@NotNull List<Node> order) {

        this.scorer.setCachingScores(caching);
        this.scorer.setUseRaskuttiUhler(false);
        this.scorer.setUseScore(true);
        this.scorer.clearBookmarks();

        List<Node> bestPerm = null;
        double best = NEGATIVE_INFINITY;
        order = new ArrayList<>(order);
        this.scorer.score(order);

        for (int r = 0; r < this.numStarts; r++) {
            if ((r == 0 && !this.useDataOrder) || r > 0) {
                shuffle(order);
            }

            double s1, s2;

            s2 = scorer.score();
            do {
                do {
                    s1 = s2;
                    divide(scorer, 0, scorer.size() / 2, scorer.size());
                    s2 = scorer.score();
                } while (s2 > s1);
                besMutation(scorer);
                s2 = scorer.score();
            } while (s2 > s1);


            if (this.scorer.score() > best) {
                best = this.scorer.score();
                bestPerm = scorer.getPi();
            }
        }

        this.scorer.score(bestPerm);

        return bestPerm;
    }

    public void divide(@NotNull TeyssierScorer scorer, int a, int b, int c) {
        if (a < (b - 1)) {
            divide(scorer, a, (a + b) / 2, b);
        }
        if (b < (c - 1)) {
            divide(scorer, b, (b + c) / 2, c);
        }
        if (algType == Boss.AlgType.BOSS1) {
            conquerRTL(scorer, a, b, c);
        } else if (algType == Boss.AlgType.BOSS2){
            conquerLTR(scorer, a, b, c);
        }
    }

    public void conquerRTL(@NotNull TeyssierScorer scorer, int a, int b, int c) {
        double currentScore = scorer.score();;
        double bestScore = currentScore;
        scorer.bookmark();

        for (int i = b; i < c; i++) {
            Node x = scorer.get(i);

            for (int j = (b-1); j >= a; j--) {
                if (!scorer.adjacent(scorer.get(j), x)) continue;

                tuck(x, j, scorer);
                currentScore = scorer.score();

                if (currentScore > bestScore) {
                    bestScore = currentScore;
                    scorer.bookmark();
                }
            }

            if (currentScore < bestScore) {
                scorer.goToBookmark();
            }
        }
    }

    public void conquerLTR(@NotNull TeyssierScorer scorer, int a, int b, int c) {
        double currentScore = scorer.score();;
        double bestScore = currentScore;
        scorer.bookmark();

        for (int i = b; i < c; i++) {
            Node x = scorer.get(i);

            for (int j = a; j < b; j++) {
                if (!scorer.adjacent(scorer.get(j), x)) continue;

                tuck(x, j, scorer);
                currentScore = scorer.score();

                if (currentScore > bestScore){
                    bestScore = currentScore;
                    scorer.bookmark();
                    break;
                } else {
                    scorer.goToBookmark();
                }
            }
        }
    }

    private void tuck(Node k, int j, TeyssierScorer scorer) {
        if (scorer.index(k) < j) return;
        Set<Node> ancestors = scorer.getAncestors(k);

        for (int i = j + 1; i <= scorer.index(k); i++) {
            if (ancestors.contains(scorer.get(i))) {
                scorer.moveTo(scorer.get(i), j++);
            }
        }
    }

    public void besMutation(TeyssierScorer scorer) {
        Graph graph = scorer.getGraph(true);
        Bes bes = new Bes(score);
        bes.setDepth(depth);
        bes.setVerbose(false);
        bes.bes(graph, scorer.getPi());
        List<Node> pi = causalOrder(scorer.getPi(), graph);
        scorer.score(pi);
    }

    private List<Node> causalOrder(List<Node> initialOrder, Graph graph) {
        List<Node> found = new ArrayList<>();
        HashSet<Node> __found = new HashSet<>();
        boolean _found = true;

        T:
        while (_found) {
            _found = false;

            for (Node node : initialOrder) {
                if (!__found.contains(node) && __found.containsAll(graph.getParents(node))) {
                    found.add(node);
                    __found.add(node);
                    _found = true;
                    continue T;
                }
            }
        }

        return found;
    }

    public int getNumEdges() {
        return this.scorer.getNumEdges();
    }

    public Graph getGraph(boolean cpDag) {
        if (this.scorer == null) throw new IllegalArgumentException("Please run algorithm first.");
        Graph graph = this.scorer.getGraph(cpDag);

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        graph.addAttribute("score ", nf.format(this.scorer.score()));
        return graph;
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

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    public void setAlgType(Boss.AlgType algType) {
        this.algType = algType;
    }

    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    public enum AlgType {BOSS1, BOSS2}
}