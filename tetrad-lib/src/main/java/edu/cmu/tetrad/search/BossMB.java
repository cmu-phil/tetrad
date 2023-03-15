package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the GRASP algorithms, with various execution flags.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class BossMB {
    private final List<Node> variables;
    private final Score score;
    private Knowledge knowledge = new Knowledge();
    private TeyssierScorer2 scorer;
    private long start;
    private boolean useDataOrder = true;
    private boolean verbose = true;
    private int depth = 4;
    private int numStarts = 1;
    private boolean findMb = false;
    private Graph graph;

    public BossMB(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
    }

    public List<Node> bestOrder(@NotNull List<Node> order, List<Node> targets) {
        long start = MillisecondTimes.timeMillis();
        order = new ArrayList<>(order);

        this.scorer = new TeyssierScorer2(this.score);

        this.scorer.setKnowledge(this.knowledge);
        this.scorer.clearBookmarks();

        List<Node> bestPerm = null;
        int bestSize = scorer.size();

        this.scorer.score(order);

        System.out.println("Initial score = " + scorer.score());

        for (int r = 0; r < this.numStarts; r++) {
            if ((r == 0 && !this.useDataOrder) || r > 0) {
                RandomUtil.shuffle(order);
            }

            this.start = MillisecondTimes.timeMillis();

            makeValidKnowledgeOrder(order);

            List<Node> pi2;
            List<Node> pi1;

            do {
                pi1 = scorer.getPi();
                betterMutationBoss(scorer, targets);
                pi2 = besOrder(scorer);
            } while (!pi2.equals(pi1));

            if (this.scorer.size() <= bestSize) {
                bestSize = this.scorer.size();
                bestPerm = scorer.getPi();
            }
        }

        this.scorer.score(bestPerm);
        this.graph = scorer.getGraph(false);

        if (findMb) {
            Set<Node> mb = new HashSet<>();

            for (Node n : graph.getNodes()) {
                for (Node t : targets) {
                    if (graph.isAdjacentTo(t, n)) {
                        mb.add(n);
                    } else {
                        for (Node m : graph.getChildren(t)) {
                            if (graph.isParentOf(n, m)) {
                                mb.add(n);
                            }
                        }
                    }
                }
            }

            N:
            for (Node n : graph.getNodes()) {
                for (Node t : targets) {
                    if (t == n) continue N;
                }

                if (!mb.contains(n)) graph.removeNode(n);
            }
        } else {
            for (Edge e : graph.getEdges()) {
                if (!(targets.contains(e.getNode1()) || targets.contains(e.getNode2()))) {
                    graph.removeEdge(e);
                }
            }
        }

        this.graph = SearchGraphUtils.cpdagForDag(this.graph);

        long stop = MillisecondTimes.timeMillis();

        if (this.verbose) {
            TetradLogger.getInstance().forceLogMessage("Final order = " + this.scorer.getPi());
            TetradLogger.getInstance().forceLogMessage("Elapsed time = " + (stop - start) / 1000.0 + " s");
        }

        return bestPerm;
    }

    public void setFindMb(boolean findMb) {
        this.findMb = findMb;
    }

    public void betterMutationBoss(@NotNull TeyssierScorer2 scorer, List<Node> targets) {
        double sp;

        List<Node> p1, p2;

        do {
            p1 = scorer.getPi();

            Graph g = scorer.getGraph(false);
            Set<Node> keep = new HashSet<>(targets);
            for (Node n : targets) {
                keep.addAll(g.getAdjacentNodes(n));
            }

            if (findMb) {
                for (Node k : new HashSet<>(keep)) {
                    keep.addAll(g.getAdjacentNodes(k));
                }
            }

            List<Node> _pi = new ArrayList<>();

            for (Node n : scorer.getPi()) {
                if (keep.contains(n)) _pi.add(n);
            }

            sp = scorer.score(_pi);

            scorer.bookmark();

            System.out.println("After snips: # vars = " + scorer.getPi().size() + " # Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutation)" + " Elapsed " + ((MillisecondTimes.timeMillis() - start) / 1000.0 + " s") + " order = " + scorer.getPi());


            for (Node x : scorer.getPi()) {
                int i = scorer.index(x);

                for (int j = i - 1; j >= 0; j--) {
                    if (scorer.tuck(x, j)) {
                        if (scorer.score() > sp && !violatesKnowledge(scorer.getPi())) {
                            sp = scorer.score();
                            scorer.bookmark();

                            if (verbose) {
                                System.out.println("# vars = " + scorer.getPi().size() + " # Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutation)" + " Elapsed " + ((MillisecondTimes.timeMillis() - start) / 1000.0 + " s"));
                            }
                        } else {
                            scorer.goToBookmark();
                        }
                    }
                }
            }

            p2 = scorer.getPi();
        } while (!p1.equals(p2));
    }

    public List<Node> besOrder(TeyssierScorer2 scorer) {
        Graph graph = scorer.getGraph(true);
        Bes bes = new Bes(score);
        bes.setDepth(depth);
        bes.setVerbose(verbose);
        bes.setKnowledge(knowledge);
        bes.bes(graph, scorer.getPi());
        return graph.paths().validOrder(scorer.getPi(), true);
    }

    public int getNumEdges() {
        return this.scorer.getNumEdges();
    }

    private void makeValidKnowledgeOrder(List<Node> order) {
        if (!this.knowledge.isEmpty()) {
            order.sort((o1, o2) -> {
                if (o1.getName().equals(o2.getName())) {
                    return 0;
                } else if (this.knowledge.isRequired(o1.getName(), o2.getName())) {
                    return 1;
                } else if (this.knowledge.isRequired(o2.getName(), o1.getName())) {
                    return -1;
                } else if (this.knowledge.isForbidden(o2.getName(), o1.getName())) {
                    return -1;
                } else if (this.knowledge.isForbidden(o1.getName(), o2.getName())) {
                    return 1;
                } else {
                    return 1;
                }
            });
        }
    }

    @NotNull
    public Graph getGraph() {
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

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
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

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }


    public Knowledge getKnowledge() {
        return knowledge;
    }

    public enum AlgType {BOSS_OLD, BOSS}
}