package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.*;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.util.Collections.shuffle;


/**
 * Implements the GRASP algorithms, with various execution flags.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public class Boss {
    private final List<Node> variables;
    private Score score;
    private IndependenceTest test;
    private IKnowledge knowledge = new Knowledge2();
    private final TeyssierScorer scorer;
    private long start;
    // flags
    private boolean useScore = true;
    private boolean useRaskuttiUhler;
    private boolean useDataOrder = true;

    private boolean verbose = true;

    // other params
    private int depth = 4;
    private int numStarts = 1;

    private AlgType algType = AlgType.BOSS;

    public Boss(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.useScore = true;
        this.scorer = new TeyssierScorer(this.test, this.score);
    }

    public Boss(@NotNull IndependenceTest test) {
        this.test = test;
        this.variables = new ArrayList<>(test.getVariables());
        this.useScore = false;
        this.scorer = new TeyssierScorer(this.test, this.score);
    }

    public Boss(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
        this.variables = new ArrayList<>(test.getVariables());
        this.scorer = new TeyssierScorer(this.test, this.score);
    }

    public Boss(TeyssierScorer scorer) {
        this.scorer = scorer;
        this.test = scorer.getTestObject();
        this.score = scorer.getScoreObject();
        this.variables = new ArrayList<>(scorer.getPi());
    }

    public List<Node> bestOrder(@NotNull List<Node> order) {
        scorer.setKnowledge(knowledge);

        List<Node> bestPerm;
        long start = System.currentTimeMillis();
        order = new ArrayList<>(order);

        this.scorer.setUseRaskuttiUhler(this.useRaskuttiUhler);

        if (this.useRaskuttiUhler) {
            this.scorer.setUseScore(false);
        } else {
            this.scorer.setUseScore(this.useScore && !(this.score instanceof GraphScore));
        }

        this.scorer.setKnowledge(this.knowledge);
        this.scorer.clearBookmarks();

        boolean cachingScores = true;
        this.scorer.setCachingScores(cachingScores);

        bestPerm = null;
        double best = NEGATIVE_INFINITY;

        this.scorer.score(order);

        for (int r = 0; r < this.numStarts; r++) {

            if ((r == 0 && !this.useDataOrder) || r > 0) {
                shuffle(order);
                System.out.println("order = " + order);
            }

            this.start = System.currentTimeMillis();

            makeValidKnowledgeOrder(order);

            List<Node> pi, pi2;
            int e1, e2 = scorer.getNumEdges();

            do {
                pi = scorer.getPi();

                if (algType == AlgType.BOSS_OLD) {
                    betterMutation(scorer);
                } else {
                    do {
                        pi = scorer.getPi();
                        e1 = e2;

                        betterMutationTuck(scorer, true);
                        betterMutationTuck(scorer, false);

                        e2 = scorer.getNumEdges();
                    } while (e2 < e1);
                }

                List<Node> pi3 = scorer.getPi();

                pi2 = besOrder(scorer);
                if (pi2.equals(pi3)) break;

                e2 = scorer.getNumEdges();
            } while (true);

            scorer.score(pi);

            if (this.scorer.score() > best) {
                best = this.scorer.score();
                bestPerm = scorer.getPi();
            }
        }

        this.scorer.score(bestPerm);

        long stop = System.currentTimeMillis();

        if (this.verbose) {
            TetradLogger.getInstance().forceLogMessage("Final order = " + this.scorer.getPi());
            TetradLogger.getInstance().forceLogMessage("Elapsed time = " + (stop - start) / 1000.0 + " s");
        }

        return bestPerm;
    }

    public void betterMutation(@NotNull TeyssierScorer scorer) {
        scorer.bookmark();
        float s1, s2;

        do {
            s1 = scorer.score();
            scorer.bookmark(1);

            for (Node k : scorer.getPi()) {
                double _sp = NEGATIVE_INFINITY;
                scorer.bookmark();

                for (int j = 0; j < scorer.size(); j++) {
                    scorer.moveTo(k, j);

                    if (scorer.score() >= _sp) {
                        if (!violatesKnowledge(scorer.getPi())) {
                            _sp = scorer.score();
                            scorer.bookmark();
                        }
                    }
                }

                if (verbose) {
                    System.out.print("\r# Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutation)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
                }

                scorer.goToBookmark();
            }

            s2 = scorer.score();
        } while (s2 > s1);

        scorer.goToBookmark(1);

        if (verbose) {
            System.out.println();
        }

        scorer.score();
    }

    public void betterMutationTuck(@NotNull TeyssierScorer scorer, boolean skipUncovered) {
        double sp = scorer.score();
        scorer.bookmark();
        float s1, s2;

        if (verbose) {
            System.out.print("\r# Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutationTuck)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
        }

        int max = scorer.size() - 1;

        do {
            s1 = scorer.score();

            for (int i = max; i > 0; i--) {
//                for (int i = scorer.size() - 1; i > 0; i--) {
//                for (int i = 1; i < scorer.size(); i++) {
                scorer.bookmark(1);
                Node x = scorer.get(i);

                for (int j = i - 1; j >= 0; j--) {
                    if (tuck(x, j, scorer, skipUncovered)) {
                        if (scorer.score() < sp || violatesKnowledge(scorer.getPi())) {
                            scorer.goToBookmark();
                        } else {
                            sp = scorer.score();

                            max = i;

                            if (verbose) {
                                System.out.print("\r# Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutationTuck)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
                            }
                        }

                        scorer.bookmark();
                    }
                }
            }

            if (verbose) {
                System.out.println();
            }

            s2 = scorer.score();
        } while (s2 > s1);

        scorer.goToBookmark(1);
    }

    private boolean tuck(Node k, int j, TeyssierScorer scorer, boolean skipUncovered) {
        if (j >= scorer.index(k)) return false;
        if (!scorer.adjacent(k, scorer.get(j))) return false;

        if (skipUncovered) {
            if (!scorer.coveredEdge(k, scorer.get(j))) return false;
        }

        Set<Node> ancestors = scorer.getAncestors(k);

        int minIndex = j;

        for (int i = j + 1; i <= scorer.index(k); i++) {
            if (ancestors.contains(scorer.get(i))) {
                scorer.moveToNoUpdate(scorer.get(i), j++);
            }
        }

        scorer.updateScores(minIndex, scorer.index(k));

        return true;
    }

    public List<Node> besOrder(TeyssierScorer scorer) {
        Graph graph = scorer.getGraph(true);
        Bes bes = new Bes(score);
        bes.setDepth(depth);
        bes.setVerbose(false);
        bes.setKnowledge(knowledge);
        bes.bes(graph, scorer.getPi());
        return causalOrder(scorer.getPi(), graph);
    }

    private List<Node> causalOrder(List<Node> initialOrder, Graph graph) {
        List<Node> found = new ArrayList<>();
        HashSet<Node> __found = new HashSet<>();
        boolean _found = true;

        while (_found) {
            _found = false;

            for (Node node : initialOrder) {
                if (!__found.contains(node) && __found.containsAll(graph.getParents(node))) {
                    found.add(node);
                    __found.add(node);
                    _found = true;
                }
            }
        }

        return found;
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
                } else if (this.knowledge.isForbidden(o1.getName(), o2.getName())) {
                    return -1;
                } else if (this.knowledge.isForbidden(o2.getName(), o1.getName())) {
                    return 1;
                } else {
                    return 0;
                }
            });
        }
    }

    @NotNull
//    public Graph getGraph(boolean cpdag) {
//
//
//        return scorer.getGraph(cpdag);
//    }

    public Graph getGraph(boolean cpDag) {
        if (this.scorer == null) throw new IllegalArgumentException("Please run algorithm first.");
        Graph graph = this.scorer.getGraph(cpDag);

        orientbk(knowledge, graph, variables);
        MeekRules meekRules = new MeekRules();
        meekRules.setRevertToUnshieldedColliders(false);
        meekRules.orientImplied(graph);

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        graph.addAttribute("score ", nf.format(this.scorer.score()));
        return graph;
    }

    public void orientbk(IKnowledge bk, Graph graph, List<Node> variables) {
        for (Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*-&gt;from
            graph.setEndpoint(to, from, Endpoint.ARROW);
        }

        for (Iterator<KnowledgeEdge> it = bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*-&gt;from
            graph.setEndpoint(from, to, Endpoint.ARROW);
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
        if (this.test != null) {
            this.test.setVerbose(verbose);
        }
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
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

                    if (this.knowledge.isRequired(order.get(j).getName(), order.get(i).getName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void setUseRaskuttiUhler(boolean useRaskuttiUhler) {
        this.useRaskuttiUhler = useRaskuttiUhler;
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    public void setAlgType(AlgType algType) {
        this.algType = algType;
    }

    public enum AlgType {BOSS_OLD, BOSS}
}