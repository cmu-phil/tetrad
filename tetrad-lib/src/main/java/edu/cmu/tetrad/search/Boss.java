package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
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
    private final Score score;
    private IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private final TeyssierScorer scorer;
    private long start;
    private boolean useScore = true;
    private boolean useRaskuttiUhler;
    private boolean useDataOrder = true;
    private boolean verbose = true;
    private int depth = -1;
    private int numStarts = 1;
    private AlgType algType = AlgType.BOSS1;
    private boolean caching = true;

    public Boss(@NotNull IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.useScore = true;
        this.scorer = new TeyssierScorer(this.test, this.score);
    }

    public Boss(Score score) {
        this.test = null;
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.useScore = true;
        this.scorer = new TeyssierScorer(null, this.score);
    }

    public Boss(TeyssierScorer scorer) {
        this.scorer = scorer;
        this.score = scorer.getScoreObject();
        this.variables = new ArrayList<>(scorer.getPi());
    }

    public List<Node> bestOrder(@NotNull List<Node> order) {


//        boolean caching = true;
        scorer.setCachingScores(caching);
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

            List<Node> pi;
            double s1, s2;

//            if (algType == AlgType.BOSS1) {
//                betterMutation1(scorer);
//            } else if (algType == AlgType.BOSS2) {
//                betterMutation2(scorer);
//            }

//            do {
//                pi = scorer.getPi();
//                s1 = scorer.score();
//
//                if (algType == AlgType.BOSS1) {
//                    betterMutation1(scorer);
//                } else if (algType == AlgType.BOSS2) {
//                    betterMutation2(scorer);
//                }
//
//                besMutation(scorer);
//
//                s2 = scorer.score();
//            } while (s2 > s1);

            int count = 0;

            do {
                s1 = scorer.score();

                if (algType == AlgType.BOSS1) {
                    betterMutation1(scorer);
                    besMutation(scorer);
                } else if (algType == AlgType.BOSS2) {
//                    tubes(scorer);
                    betterMutation2(scorer);
                    besMutation(scorer);

                } else if (algType == AlgType.BOSS3) {
                    betterMutationBryan(scorer);
                    besMutation(scorer);

                }

//                besMutation(scorer);
                s2 = scorer.score();
            } while (s2 > s1 || ++count < 8);

//            scorer.score(pi);

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

    public void tubes(@NotNull TeyssierScorer scorer) {
        double s;

        do {
            s = scorer.score();

            Graph g = scorer.findCompelled();

            scorer.bookmark();

            for (Edge edge : g.getEdges()) {
                if (!edge.isDirected()) continue;

                Node x = edge.getNode1();
                Node y = edge.getNode2();

                tuck(y, scorer.index(x), scorer, new int[2]);

                if (scorer.score() >= s) {
                    scorer.bookmark();
                    break;
                }

                scorer.goToBookmark();
            }

            scorer.goToBookmark();

            besMutation(scorer);

            scorer.bookmark();
        } while (scorer.score() > s);
    }

    public List<Node> bestOrder2(@NotNull List<Node> order) {
//        boolean caching = true;
        scorer.setCachingScores(caching);
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

            List<Node> pi;
            double s1, s2;

            do {
                s1 = scorer.score();


                betterMutationBryan(scorer);
                besMutation(scorer);
                s2 = scorer.score();
            } while (s2 > s1);

//            scorer.score(pi);

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

    public void betterMutation1(@NotNull TeyssierScorer scorer) {
        double bestScore = scorer.score();
        scorer.bookmark();
        double s1, s2;

        Set<Node> introns1;
        Set<Node> introns2;

        introns2 = new HashSet<>(scorer.getPi());

        int[] range = new int[2];

        do {
            s1 = scorer.score();

            introns1 = introns2;
            introns2 = new HashSet<>();

            for (int i = 1; i < scorer.size(); i++) {
                Node x = scorer.get(i);
                if (!introns1.contains(x)) continue;

                scorer.bookmark(1);

                for (int j = i - 1; j >= 0; j--) {
                    if (!scorer.adjacent(scorer.get(j), x)) continue;

                    tuck(x, j, scorer, range);

                    if (scorer.score() < bestScore || violatesKnowledge(scorer.getPi())) {
                        scorer.goToBookmark();
                    } else {
                        bestScore = scorer.score();

                        for (int l = range[0]; l <= range[1]; l++) {
                            introns2.add(scorer.get(l));
                        }
                    }

                    scorer.bookmark();

                    if (verbose) {
                        System.out.print("\rIndex = " + (i + 1) + " Score = " + scorer.score() + " (betterMutation1)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
//                        System.out.print("\r# Edges = " + scorer.getNumEdges() + " Index = " + (i + 1) + " Score = " + scorer.score() + " (betterMutationTuck)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
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

    public void betterMutationBryan(@NotNull TeyssierScorer scorer) {
        double bestScore = scorer.score();
        scorer.bookmark();
        double s1, s2;

        Set<Node> introns1;
        Set<Node> introns2;

        introns2 = new HashSet<>(scorer.getPi());

        int[] range = new int[2];

        do {
            s1 = scorer.score();

            introns1 = introns2;
            introns2 = new HashSet<>();

            List<OrderedPair<Node>> edges = scorer.getEdges();
            int m = 0;
            int all = edges.size();

            for (OrderedPair<Node> edge : edges) {
                m++;
                Node x = edge.getFirst();
                Node y = edge.getSecond();
                if (scorer.index(x) > scorer.index(y)) continue;
                if (!scorer.adjacent(y, x)) continue;
                if (!introns1.contains(y) && !introns1.contains(x)) continue;

//                scorer.bookmark(1);

                tuck(y, scorer.index(x), scorer, range);

                if (scorer.score() < bestScore || violatesKnowledge(scorer.getPi())) {
                    scorer.goToBookmark();
                } else {
                    bestScore = scorer.score();

                    for (int l = range[0]; l <= range[1]; l++) {
                        introns2.add(scorer.get(l));
                    }

                    if (verbose) {
                        System.out.print("\r Score " + m + " / " + all + " = " + scorer.score() + " (boss)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
                    }
                }

                scorer.bookmark();

                if (verbose) {
                    System.out.print("\r Score " + m + " / " + all + " = " + scorer.score() + " (boss)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
                }
            }

            if (verbose) {
                System.out.println();
            }

            s2 = scorer.score();
        } while (s2 > s1);

//        scorer.goToBookmark(1);
    }

    public void betterMutation2(@NotNull TeyssierScorer scorer) {
        scorer.bookmark();
        double s1, s2;

        Set<Node> introns1;
        Set<Node> introns2;

        introns2 = new HashSet<>(scorer.getPi());

        do {
            s1 = scorer.score();
            scorer.bookmark(1);

            introns1 = introns2;
            introns2 = new HashSet<>();

            for (Node k : scorer.getPi()) {
                double _sp = NEGATIVE_INFINITY;
                scorer.bookmark();

                if (!introns1.contains(k)) continue;

                for (int j = 0; j < scorer.size(); j++) {
                    scorer.moveTo(k, j);

                    if (scorer.score() >= _sp) {
                        if (!violatesKnowledge(scorer.getPi())) {
                            _sp = scorer.score();
                            scorer.bookmark();

                            if (scorer.index(k) <= j) {
                                for (int m = scorer.index(k); m <= j; m++) {
                                    introns2.add(scorer.get(m));
                                }
                            } else if (scorer.index(k) > j) {
                                for (int m = j; m <= scorer.index(k); m++) {
                                    introns2.add(scorer.get(m));
                                }
                            }
                        }
                    }

                    if (verbose) {
                        System.out.print("\rIndex = " + (j + 1) + " Score = " + scorer.score() + " (betterMutation2)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
//                        System.out.print("\r# Edges = " + scorer.getNumEdges() + " Index = " + (i + 1) + " Score = " + scorer.score() + " (betterMutationTuck)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
                    }
                }

//                if (verbose) {
//                    System.out.print("\r# Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutation)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
//                }

                scorer.goToBookmark();
            }

            s2 = scorer.score();
        } while (s2 > s1);

        scorer.goToBookmark(1);
    }


    public void betterMutationBryan2(@NotNull TeyssierScorer scorer) {
        scorer.bookmark();
        double s1, s2;

        Set<Node> introns1;
        Set<Node> introns2;

        introns2 = new HashSet<>(scorer.getPi());

        do {
            s1 = scorer.score();
            scorer.bookmark(1);

            introns1 = introns2;
            introns2 = new HashSet<>();

            Graph g = scorer.findCompelled();

            for (Node k : scorer.getPi()) {
                double _sp = NEGATIVE_INFINITY;
                scorer.bookmark();

                if (!introns1.contains(k)) continue;

                for (int j = 0; j < scorer.size(); j++) {
                    if (!g.containsEdge(Edges.directedEdge(k, scorer.get(j)))) continue;

                    scorer.moveTo(k, j);

                    if (scorer.score() >= _sp) {
                        if (!violatesKnowledge(scorer.getPi())) {
                            _sp = scorer.score();
                            scorer.bookmark();
//                            g = scorer.findCompelled();

                            if (scorer.index(k) <= j) {
                                for (int m = scorer.index(k); m <= j; m++) {
                                    introns2.add(scorer.get(m));
                                }
                            } else if (scorer.index(k) > j) {
                                for (int m = j; m <= scorer.index(k); m++) {
                                    introns2.add(scorer.get(m));
                                }
                            }
                        }
                    }

                    if (verbose) {
                        System.out.print("\rIndex = " + (j + 1) + " Score = " + scorer.score() + " (betterMutation2)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
//                        System.out.print("\r# Edges = " + scorer.getNumEdges() + " Index = " + (i + 1) + " Score = " + scorer.score() + " (betterMutationTuck)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
                    }
                }

//                if (verbose) {
//                    System.out.print("\r# Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutation)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
//                }

                scorer.goToBookmark();
            }

            s2 = scorer.score();
        } while (s2 > s1);

        scorer.goToBookmark(1);
    }

//    public void betterMutation2(@NotNull TeyssierScorer scorer) {
//        scorer.bookmark();
//        double s1, s2;
//
//        Set<Node> introns1;
//        Set<Node> introns2;
//
//        introns2 = new HashSet<>(scorer.getPi());
//
//        do {
//            s1 = scorer.score();
//            scorer.bookmark(1);
//
//            introns1 = introns2;
//            introns2 = new HashSet<>();
//
//            for (Node k : scorer.getPi()) {
//                double _sp = NEGATIVE_INFINITY;
//                scorer.bookmark();
//
//                if (!introns1.contains(k)) continue;
//
//                for (int j = 0; j < scorer.size(); j++) {
//                    scorer.moveTo(k, j);
//
//                    if (scorer.score() >= _sp) {
//                        if (!violatesKnowledge(scorer.getPi())) {
//                            _sp = scorer.score();
//                            scorer.bookmark();
//
//                            if (scorer.index(k) <= j) {
//                                for (int m = scorer.index(k); m <= j; m++) {
//                                    introns2.add(scorer.get(m));
//                                }
//                            } else if (scorer.index(k) > j) {
//                                for (int m = j; m <= scorer.index(k); m++) {
//                                    introns2.add(scorer.get(m));
//                                }
//                            }
//                        }
//                    }
//
//                    if (verbose) {
//                        System.out.print("\rIndex = " + (j + 1) + " Score = " + scorer.score() + " (betterMutation2)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
////                        System.out.print("\r# Edges = " + scorer.getNumEdges() + " Index = " + (i + 1) + " Score = " + scorer.score() + " (betterMutationTuck)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
//                    }
//                }
//
////                if (verbose) {
////                    System.out.print("\r# Edges = " + scorer.getNumEdges() + " Score = " + scorer.score() + " (betterMutation)" + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
////                }
//
//                scorer.goToBookmark();
//            }
//
//            s2 = scorer.score();
//        } while (s2 > s1);
//
//        scorer.goToBookmark(1);
//    }

    private void tuck(Node k, int j, TeyssierScorer scorer, int[] range) {
        if (scorer.index(k) < j) return;
        Set<Node> ancestors = scorer.getAncestors(k);

        int minIndex = j;

        for (int i = j + 1; i <= scorer.index(k); i++) {
            if (ancestors.contains(scorer.get(i))) {
                scorer.moveTo(scorer.get(i), j++);
            }
        }

        range[0] = minIndex;
        range[1] = scorer.index(k);
    }

    public void besMutation(TeyssierScorer scorer) {
        Graph graph = scorer.getGraph(true);
        Bes bes = new Bes(score);
        bes.setDepth(depth);
        bes.setVerbose(false);
        bes.setKnowledge(knowledge);
        bes.bes(graph, scorer.getPi());
        List<Node> pi = causalOrder(scorer.getPi(), graph);
        scorer.score(pi);
    }

    private List<Node> causalOrder(List<Node> initialOrder, Graph graph) {
        List<Node> found = new ArrayList<>();
        HashSet<Node> __found = new HashSet<>();
        boolean _found = true;

//        while (_found) {
//            _found = false;
//
//            for (Node node : initialOrder) {
//                if (!__found.contains(node) && __found.containsAll(graph.getParents(node))) {
//                    found.add(node);
//                    __found.add(node);
//                    _found = true;
//                }
//            }
//        }

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

    public Graph getGraph(boolean cpDag) {
        if (this.scorer == null) throw new IllegalArgumentException("Please run algorithm first.");
        Graph graph = this.scorer.getGraph(cpDag);

//        if (cpDag) {
//            orientbk(knowledge, graph, variables);
//            MeekRules meekRules = new MeekRules();
//            meekRules.setRevertToUnshieldedColliders(false);
//            meekRules.orientImplied(graph);
//        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        graph.addAttribute("score ", nf.format(this.scorer.score()));
        return graph;
    }

    public void orientbk(Knowledge bk, Graph graph, List<Node> variables) {
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
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
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

    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    public enum AlgType {BOSS1, BOSS2, BOSS3}
}