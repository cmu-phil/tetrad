package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static edu.cmu.tetrad.graph.GraphUtils.existsSemidirectedPath;


/**
 * Implements the GRASP algorithms, with various execution flags.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class Bridges3 {
    private final List<Node> variables;
    private final Score score;
    private final IndependenceTest test;
    private IKnowledge knowledge = new Knowledge2();
    private TeyssierScorer scorer;
    private long start;
    // flags
    private boolean useScore = true;
    private boolean useRaskuttiUhler;
    private boolean cachingScores = true;

    private boolean verbose = true;

    public Bridges3(@NotNull IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
        this.variables = new ArrayList<>(test.getVariables());
    }

    public List<Node> bestOrder(@NotNull List<Node> pi) {
        long start = System.currentTimeMillis();

        this.scorer = new TeyssierScorer(this.test, this.score);
        this.scorer.setUseRaskuttiUhler(this.useRaskuttiUhler);

        if (this.useRaskuttiUhler) {
            this.scorer.setUseScore(false);
        } else {
            this.scorer.setUseScore(this.useScore && !(this.score instanceof GraphScore));
        }

        this.scorer.setKnowledge(this.knowledge);
        this.scorer.clearBookmarks();
        this.scorer.setCachingScores(this.cachingScores);
        this.start = System.currentTimeMillis();

        scorer.score(pi);

        List<Node> pi1;
        List<Node> pi2 = fgesOrder(scorer);

        do {
            scorer.score(pi2);
            oneMove(scorer);
            betterMutation(scorer);
            pi1 = scorer.getPi();
//            pi2 = besOrder(scorer);
            pi2 = fgesOrder(scorer);
        } while (!pi1.equals(pi2));

        long stop = System.currentTimeMillis();

        if (this.verbose) {
            TetradLogger.getInstance().forceLogMessage("Final order = " + this.scorer.getPi());
            TetradLogger.getInstance().forceLogMessage("Elapsed time = " + (stop - start) / 1000.0 + " s");
        }

        return pi;
    }

    public List<Node> fgesOrder(TeyssierScorer scorer) {
        Fges fges = new Fges(score);
        fges.setKnowledge(knowledge);
        Graph graph = scorer.getGraph(true);
        fges.setExternalGraph(graph);
        graph = fges.search();
        List<Node> pi2 = GraphUtils.getCausalOrdering(graph, scorer.getPi());
        return causalOrder(pi2, graph);
    }

    @NotNull
    private List<Node> causalOrder(List<Node> initialOrder, Graph graph) {
        List<Node> found = new ArrayList<>();
        boolean _found = true;

        while (_found) {
            _found = false;

            for (Node node : initialOrder) {
                HashSet<Node> __found = new HashSet<>(found);
                if (!__found.contains(node) && __found.containsAll(graph.getParents(node))) {
                    found.add(node);
                    _found = true;
                }
            }
        }
        return found;
    }

    public void betterMutation(@NotNull TeyssierScorer scorer) {
        double sp = scorer.score();
        scorer.bookmark();

        List<Node> pi, pi2;

        do {
            pi = scorer.getPi();

            for (int i = 0; i < scorer.size(); i++) {
                scorer.bookmark(1);
                Node x = scorer.get(i);

                for (int j = i - 1; j >= 0; j--) {
                    if (tuck(x, j, scorer)) {
                        if (scorer.score() <= sp || violatesKnowledge(scorer.getPi())) {
                            scorer.goToBookmark();
                        } else {
                            sp = scorer.score();
                            scorer.bookmark();

                            System.out.print("\r# Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutation)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
                        }

                    }
                }
            }

            scorer.goToBookmark(1);

            System.out.println("*");

            pi2 = scorer.getPi();
        } while (!pi.equals(pi2));


        System.out.println();
    }

    private boolean tuck(Node k, int j, TeyssierScorer scorer) {
        if (!scorer.adjacent(k, scorer.get(j))) return false;
        if (scorer.coveredEdge(k, scorer.get(j))) return false;
        if (j >= scorer.index(k)) return false;

        Set<Node> ancestors = scorer.getAncestors(k);
        for (int i = j + 1; i <= scorer.index(k); i++) {
            if (ancestors.contains(scorer.get(i))) {
                scorer.moveTo(scorer.get(i), j++);
            }
        }

        return true;
    }

    public List<Node> oneMove(TeyssierScorer scorer) {
        double s0 = scorer.score();
        List<Node> pi = scorer.getPi();
        Graph g0 = scorer.getGraph(true);

        for (Edge edge : new EdgeListGraph((EdgeListGraph) g0).getEdges()) {
            if (edge.isDirected()) {
                Graph g = new EdgeListGraph((EdgeListGraph) g0);
                Node a = Edges.getDirectedEdgeHead(edge);
                Node b = Edges.getDirectedEdgeTail(edge);

                // This code performs "pre-tuck" operation
                // that makes anterior nodes of the distal
                // node into parents of the proximal node

                for (Node c : g0.getAdjacentNodes(b)) {
                    if (existsSemidirectedPath(c, a, g0)) {
                        g.removeEdge(g0.getEdge(b, c));
                        g.addDirectedEdge(c, b);
                    }
                }

                List<Node> co = causalOrder(pi, g);

                double s1 = scorer.score(co);

                if (s1 > s0) {
                    g0 = g;
                    pi = co;
                }
            }
        }

        return pi;
    }

    public int getNumEdges() {
        return this.scorer.getNumEdges();
    }

    @NotNull
    public Graph getGraph(boolean cpdag) {
        return scorer.getGraph(cpdag);
    }

    public void setCacheScores(boolean cachingScores) {
        this.cachingScores = cachingScores;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        if (this.test != null) {
            this.test.setVerbose(verbose);
        }
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        // other params
    }

    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    private boolean violatesKnowledge(List<Node> order) {
        if (!this.knowledge.isEmpty()) {
            for (int i = 0; i < order.size(); i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    if (this.knowledge.isForbidden(order.get(i).getName(), order.get(j).getName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void setUseRaskuttiUhler(boolean usePearl) {
        this.useRaskuttiUhler = usePearl;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }
}