package edu.cmu.tetrad.search;

import cern.colt.Arrays;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.OrderedPair;
import edu.cmu.tetrad.util.PermutationGenerator;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.shuffle;


/**
 * Implements various permutation algorithms, including BOSS and GASP.
 *
 * @author josephramsey
 */
public class OtherPermAlgs {
    private final List<Node> variables;
    private long start;
    private Score score;
    private IndependenceTest test;
    private int numStarts = 1;
    private Method method = Method.GSP;
    private IKnowledge knowledge = new Knowledge2();
    private int depth = 4;
    private TeyssierScorer scorer;
    private int numRounds = 50;

    // flags
    private boolean useScore = true;
    private boolean usePearl = false;
    private boolean verbose = false;
    private boolean cachingScores = true;
    private boolean useDataOrder = false;
    private int numVars;

    public OtherPermAlgs(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.useScore = true;
    }

    public OtherPermAlgs(@NotNull IndependenceTest test) {
        this.test = test;
        this.variables = new ArrayList<>(test.getVariables());
        this.useScore = false;
    }

    public OtherPermAlgs(@NotNull IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
        this.variables = new ArrayList<>(test.getVariables());
    }

    public List<Node> bestOrder(@NotNull List<Node> _order) {
        List<Node> order = new ArrayList<>(_order);
        long start = System.currentTimeMillis();

        if (useScore && !(score instanceof GraphScore)) {
            scorer = new TeyssierScorer(test, score);
            scorer.setUseScore(true);
        } else {
            scorer = new TeyssierScorer(test, score);
            scorer.setUseVermaPearl(usePearl);
            scorer.score(variables);

            if (usePearl) {
                scorer.setUseScore(false);
            } else {
                scorer.setUseScore(useScore);
            }
        }

        scorer.setKnowledge(knowledge);
        scorer.clearBookmarks();

        scorer.setCachingScores(cachingScores);

        List<Node> bestPerm = new ArrayList<>(order);
        double best = Float.NEGATIVE_INFINITY;

        for (int r = 0; r < (useDataOrder ? 1 : numStarts); r++) {
            if (!useDataOrder) {
                shuffle(order);
            }

            this.start = System.currentTimeMillis();

            makeValidKnowledgeOrder(order);

//            System.out.println("\t\t\t\tIN BESTORDER order = " + order);

            scorer.score(order);

            if (verbose) {
                System.out.println("Using " + method);
            }

            List<Node> perm;

            if (method == Method.RCG) {
                perm = rcg(scorer);
            } else if (method == Method.GSP) {
                perm = gasp(scorer);
            } else if (method == Method.ESP) {
                perm = esp(scorer);
            } else if (method == Method.SP) {
                useDataOrder = true;
                perm = sp(scorer);
            } else {
                throw new IllegalArgumentException("Unrecognized method: " + method);
            }

            scorer.score(perm);

            if (scorer.score() > best) {
                best = scorer.score();
                bestPerm = perm;
            }
        }

        long stop = System.currentTimeMillis();

        if (verbose) {
            System.out.println("Final order = " + scorer.getPi());
            System.out.println("Elapsed time = " + (stop - start) / 1000.0 + " s");
        }

        return bestPerm;
    }

    public int getNumEdges() {
        return scorer.getNumEdges();
    }

    private void makeValidKnowledgeOrder(List<Node> order) {
        if (!knowledge.isEmpty()) {
            order.sort((o1, o2) -> {
                if (o1.getName().equals(o2.getName())) {
                    return 0;
                } else if (knowledge.isRequired(o1.getName(), o2.getName())) {
                    return 1;
                } else if (knowledge.isRequired(o2.getName(), o1.getName())) {
                    return -1;
                } else if (knowledge.isForbidden(o2.getName(), o1.getName())) {
                    return -1;
                } else if (knowledge.isForbidden(o1.getName(), o2.getName())) {
                    return 1;
                } else {
                    return 1;
                }
            });
        }
    }

    public List<Node> esp(@NotNull TeyssierScorer scorer) {
        if (depth <= 0) throw new IllegalArgumentException("Form ESP, max depth should be > 0");

        double sOld;
        double sNew = scorer.score();

        do {
            sOld = sNew;
            espDfs(scorer, sOld, (depth < 0 ? 100 : depth), 1);
            sNew = scorer.score();
        } while (sNew > sOld);

        if (verbose) {
            System.out.println("# Edges = " + scorer.getNumEdges()
                    + " Score = " + scorer.score()
                    + " (ESP)"
                    + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
        }

        return scorer.getPi();
    }

    public List<Node> gasp(@NotNull TeyssierScorer scorer) {
        if (depth < 0) throw new IllegalArgumentException("Form GRASP, max depth should be >= 0");
        scorer.clearBookmarks();

        double sOld;
        double sNew = scorer.score();

        do {
            sOld = sNew;
            graspDfs(scorer, sOld, (depth < 0 ? Integer.MAX_VALUE : depth), 0, true);
            sNew = scorer.score();
        } while (sNew > sOld);

        if (verbose) {
            System.out.println("# Edges = " + scorer.getNumEdges()
                    + " Score = " + scorer.score()
                    + " (GASP))"
                    + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
        }

        return scorer.getPi();

    }

    public List<Node> rcg(@NotNull TeyssierScorer scorer) {
        if (numRounds <= 0) throw new IllegalArgumentException("For RCG, #rounds should be > 0");
        scorer.clearBookmarks();
        NumberFormat nf = new DecimalFormat("0.0");

        if (verbose) {
            System.out.println("\nInitial # edges = " + scorer.getNumEdges());
        }

        scorer.bookmark(1);

        int maxRounds = numRounds < 0 ? Integer.MAX_VALUE : numRounds;
        int unimproved = 0;
//
        for (int r = 1; r <= maxRounds; r++) {
            if (verbose) {
                System.out.println("### Round " + (r));
            }

            List<OrderedPair<Node>> pairs = new ArrayList<>();

            for (Node y : scorer.getPi()) {
                for (Node x : scorer.getParents(y)) {
                    pairs.add(new OrderedPair<>(x, y));
                }
            }

            shuffle(pairs);

            int numImprovements = 0;
            int numEquals = 0;

            int visited = 0;
            int numPairs = pairs.size();

            for (OrderedPair<Node> pair : pairs) {
                scorer.resetCacheIfTooBig(100000);
                visited++;

                Node x = pair.getFirst();
                Node y = pair.getSecond();
                if (!scorer.adjacent(x, y)) continue;

                double s0 = scorer.score();
                scorer.bookmark(0);

                // 'tuck' operation.
//                scorer.tuck(x, y);
                scorer.moveTo(y, scorer.index(x));

                if (violatesKnowledge(scorer.getOrderShallow())) {
                    scorer.goToBookmark(0);
                    continue;
                }

                double sNew = scorer.score();

                if (sNew < s0) {
                    scorer.goToBookmark(0);
                    continue;
                }

                scorer.bookmark(1);

                if (sNew > s0) {
                    numImprovements++;
                }

                if (verbose) {
                    if (sNew == s0) {
                        numEquals++;
                    }

                    if (sNew > s0) {
                        System.out.println("Round " + (r) + " # improvements = " + numImprovements
                                + " # unimproved = " + numEquals
                                + " # edges = " + scorer.getNumEdges() + " progress this round = "
                                + nf.format(100 * visited / (double) numPairs) + "%");
                    }
                }
            }

            for (OrderedPair<Node> pair : pairs) {
                scorer.resetCacheIfTooBig(100000);
                visited++;

                Node x = pair.getFirst();
                Node y = pair.getSecond();
                if (!scorer.adjacent(x, y)) continue;

                double s0 = scorer.score();
                scorer.bookmark(0);

                // 'tuck' operation.
//                scorer.tuck(x, y);
                scorer.moveTo(x, scorer.index(y));

                if (violatesKnowledge(scorer.getOrderShallow())) {
                    scorer.goToBookmark(0);
                    continue;
                }

                double sNew = scorer.score();

                if (sNew < s0) {
                    scorer.goToBookmark(0);
                    continue;
                }

                scorer.bookmark(1);

                if (sNew > s0) {
                    numImprovements++;
                }

                if (verbose) {
                    if (sNew == s0) {
                        numEquals++;
                    }

                    if (sNew > s0) {
                        System.out.println("Round " + (r) + " # improvements = " + numImprovements
                                + " # unimproved = " + numEquals
                                + " # edges = " + scorer.getNumEdges() + " progress this round = "
                                + nf.format(100 * visited / (double) numPairs) + "%");
                    }
                }
            }

            if (numImprovements == 0) {
                unimproved++;
            }

            if (unimproved >= depth) {
                break;
            }
        }

        if (verbose) {
            System.out.println("# Edges = " + scorer.getNumEdges()
                    + " Score = " + scorer.score()
                    + " #round = " + numRounds
                    + " (RCG)"
                    + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " s"));
        }

        scorer.goToBookmark(1);
        return scorer.getPi();
    }

    public List<Node> sp(@NotNull TeyssierScorer scorer) {
//        System.out.println("\t\t\t\tIN SP PI = " + scorer.getPi());

        double maxScore = Float.NEGATIVE_INFINITY;
        List<Node> maxP = null;

        List<Node> variables = scorer.getPi();
        PermutationGenerator gen = new PermutationGenerator(variables.size());
        int[] perm;
        Set<Graph> frugalCpdags = new HashSet<>();

        int[] v = new int[numVars];
        for (int i = 0; i < numVars; i++) v[i] = i;

        List<Node> pi0 = GraphUtils.asList(v, variables);
        scorer.score(pi0);
        System.out.println("\t\t# edges for " + pi0 + scorer.getNumEdges());

        while ((perm = gen.next()) != null) {
            List<Node> p = GraphUtils.asList(perm, variables);
            scorer.score(p);

            if (scorer.score() > maxScore) {
                maxScore = scorer.score();
                maxP = p;
                frugalCpdags.clear();
            }

            if (scorer.score() == maxScore) {
                frugalCpdags.add(scorer.getGraph(true));
            }
//            if (scorer.score() == maxScore) {
//                Graph g = scorer.getGraph(true);
//                boolean containsCpdag = false;
//                for (Graph h : frugalCpdags) {
//                    if (g.equals(h)) {
//                        containsCpdag = true;
//                        break;
//                    }
//                }
//                if (!containsCpdag) {
//                    frugalCpdags.add(g);
//                }
//            }
        }

        if (true) {
            System.out.println("\t\t# frugal cpdags BY SP = " + frugalCpdags.size());
            System.out.println("\t\t# edges for frugal = " + frugalCpdags.iterator().next().getNumEdges());

            if (frugalCpdags.size() == 1) {
                System.out.println("\t!!!! U-FRUGAL BY SP");
            }
        }

        if (verbose) {
            System.out.println("# Edges = " + scorer.getNumEdges()
                    + " Score = " + scorer.score()
                    + " (SP)"
//                    + " # frugal CPDAGs = " + frugalCpdags.size()
                    + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " sp"));
        }

        return maxP;
    }

    @NotNull
    public Graph getGraph(boolean cpDag) {
        if (scorer == null) throw new IllegalArgumentException("Please run algorithm first.");
        Graph graph = scorer.getGraph(cpDag);
        graph.addAttribute("# edges", graph.getNumEdges());
        return graph;
    }

    public void setCacheScores(boolean cachingScores) {
        this.cachingScores = cachingScores;
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth should be >= -1.");
        this.depth = depth;
    }

    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    private boolean violatesKnowledge(List<Node> order) {
        if (!knowledge.isEmpty()) {
            for (int i = 0; i < order.size(); i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    if (knowledge.isForbidden(order.get(i).getName(), order.get(j).getName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void espDfs(@NotNull TeyssierScorer scorer, double sOld, int depth, int currentDepth) {
        for (int i = 0; i < scorer.size() - 1; i++) {
            List<Node> pi = scorer.getPi();
            scorer.swap(scorer.get(i), scorer.get(i + 1));

            if (violatesKnowledge(scorer.getPi())) {
                scorer.score(pi);
                continue;
            }

            double sNew = scorer.score();

            if (sNew == sOld && currentDepth < depth) {
                espDfs(scorer, sNew, depth, currentDepth + 1);
                sNew = scorer.score();
            }

            if (sNew <= sOld) {
                scorer.score(pi);
            } else {
                break;
            }
        }
    }

    private void graspDfs(@NotNull TeyssierScorer scorer, double sOld, int depth, int currentDepth,
                          boolean checkCovering) {
        for (OrderedPair<Node> adj : scorer.getEdges()) {
            Node x = adj.getFirst();
            Node y = adj.getSecond();
            if (checkCovering && !scorer.coveredEdge(x, y)) continue;
            scorer.bookmark(currentDepth);
            scorer.tuck(x, y);
//            System.out.println(scorer.getOrder() + " score = " + scorer.getNumEdges());

            if (violatesKnowledge(scorer.getPi())) {
                scorer.goToBookmark(currentDepth);
                continue;
            }

            double sNew = scorer.score();

            if (sNew == sOld && currentDepth < depth) {
//                System.out.println("equals " + scorer.getOrder() + " sNew = " + sNew);
                graspDfs(scorer, sNew, depth, currentDepth + 1, checkCovering);
                sNew = scorer.score();
            }

            if (sNew <= sOld) {
                scorer.goToBookmark(currentDepth);
            } else {
                break;
            }
        }
    }

    public void setNumRounds(int numRounds) {
        this.numRounds = numRounds;
    }

    public void setUsePearl(boolean usePearl) {
        this.usePearl = usePearl;
    }

    public void setNumVariables(int numVars) {
        this.numVars = numVars;
    }

    public enum Method {RCG, GSP, ESP, SP}
}