package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

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
    private TeyssierScorer scorer;
    private long start;
    // flags
    private boolean useScore = true;
    private boolean usePearl;
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
    }

    public Boss(@NotNull IndependenceTest test) {
        this.test = test;
        this.variables = new ArrayList<>(test.getVariables());
        this.useScore = false;
    }

    public Boss(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
        this.variables = new ArrayList<>(test.getVariables());
    }

    public List<Node> bestOrder(@NotNull List<Node> order) {
        List<Node> bestPerm;
        try {
            long start = System.currentTimeMillis();
            order = new ArrayList<>(order);

            this.scorer = new TeyssierScorer(this.test, this.score);
            this.scorer.setUseRaskuttiUhler(this.usePearl);

            if (this.usePearl) {
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
                if (Thread.interrupted()) break;

                if ((r == 0 && !this.useDataOrder) || r > 0) {
                    shuffle(order);
                }

                this.start = System.currentTimeMillis();

                makeValidKnowledgeOrder(order);

                List<Node> pi2 = order;// causalOrder(scorer.getPi(), graph);
                List<Node> pi1;

                do {
                    scorer.score(pi2);

                    if (algType == AlgType.BOSS) {
                        betterMutation(scorer);
                    } else {
                        betterMutationTuck(scorer);
                    }

                    pi1 = scorer.getPi();

                    if (algType == AlgType.KING_OF_BRIDGES) {
                        pi2 = fgesOrder(scorer);
                    } else {
                        pi2 = besOrder(scorer);
                    }

                } while (!pi1.equals(pi2));

                if (this.scorer.score() > best) {
                    best = this.scorer.score();
                    bestPerm = scorer.getPi();
                }
            }

            this.scorer.score(bestPerm);
            this.graph = scorer.getGraph(true);

            long stop = System.currentTimeMillis();

            if (this.verbose) {
                TetradLogger.getInstance().forceLogMessage("Final order = " + this.scorer.getPi());
                TetradLogger.getInstance().forceLogMessage("Elapsed time = " + (stop - start) / 1000.0 + " s");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return bestPerm;
    }

    public void betterMutation(@NotNull TeyssierScorer scorer) {
        scorer.bookmark();
        List<Node> pi1, pi2;

        do {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            pi1 = scorer.getPi();
            scorer.bookmark(1);

            for (Node k : scorer.getPi()) {
                relocate(k, scorer);
            }

            pi2 = scorer.getPi();
        } while (!pi1.equals(pi2));

        scorer.goToBookmark(1);

        System.out.println();

        scorer.score();
    }

    private void relocate(Node k, @NotNull TeyssierScorer scorer) {
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

    public void betterMutationTuck(@NotNull TeyssierScorer scorer) throws InterruptedException {
        double sp = scorer.score();
        scorer.bookmark();
        List<Node> pi1, pi2;

        do {
            pi1 = scorer.getPi();

            for (int i = 1; i < scorer.size(); i++) {
                scorer.bookmark(1);

                Node x = scorer.get(i);
                for (int j = i - 1; j >= 0; j--) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                    if (tuck(x, j, scorer)) {
                        if (scorer.score() < sp || violatesKnowledge(scorer.getPi())) {
                            scorer.goToBookmark();
                        } else {
                            sp = scorer.score();

                            if (verbose) {
                                System.out.println("# Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutationTuck)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
                            }
                        }

                        scorer.bookmark();
                    }
                }
            }

            pi2 = scorer.getPi();
        } while (!pi1.equals(pi2));
//
        scorer.goToBookmark(1);
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

    public List<Node> besOrder(TeyssierScorer scorer) {
        Graph graph = scorer.getGraph(true);
        Bes bes = new Bes(score);
        bes.setDepth(depth);
        bes.setVerbose(verbose);
        bes.setKnowledge(knowledge);
        bes.bes(graph, scorer.getPi());
        return causalOrder(scorer.getPi(), graph);
    }

    public List<Node> fgesOrder(TeyssierScorer scorer) {
        Fges fges = new Fges(score);
        fges.setKnowledge(knowledge);
        Graph graph = scorer.getGraph(true);
        fges.setExternalGraph(graph);
        fges.setVerbose(false);
        graph = fges.search();
        return causalOrder(scorer.getPi(), graph);
    }

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
        orientbk(knowledge, graph, variables);
        MeekRules meekRules = new MeekRules();
        meekRules.setRevertToUnshieldedColliders(false);
        meekRules.orientImplied(graph);

        return this.graph;
    }

    public void orientbk(IKnowledge bk, Graph graph, List<Node> variables) {
        for (Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

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
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

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
                }
            }
        }

        return false;
    }

    public void setUseRaskuttiUhler(boolean usePearl) {
        this.usePearl = usePearl;
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    private Graph graph;

    public void setAlgType(AlgType algType) {
        this.algType = algType;
    }

    public enum AlgType {BOSS, BOSS_TUCK, KING_OF_BRIDGES}
}