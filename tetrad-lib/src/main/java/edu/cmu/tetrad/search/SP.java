package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.PermutationGenerator;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.*;

import static edu.cmu.tetrad.util.RandomUtil.shuffle;

/**
 * Implements various permutation algorithms, including BOSS and GASP.
 *
 * @author josephramsey
 */
public class SP {
    private final List<Node> variables;
    private long start;
    private Score score;
    private IndependenceTest test;
    private int numStarts = 1;
    private Knowledge knowledge = new Knowledge();
    private TeyssierScorer scorer;

    // flags
    private boolean useScore = true;
    private boolean verbose = false;
    private boolean useDataOrder = false;

    public SP(@NotNull Score score) {
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.useScore = true;
    }

    public SP(@NotNull IndependenceTest test) {
        this.test = test;
        this.variables = new ArrayList<>(test.getVariables());
        this.useScore = false;
    }

    public SP(@NotNull IndependenceTest test, Score score) {
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
            boolean usePearl = false;
            scorer.setUseRaskuttiUhler(usePearl);
            scorer.score(variables);
            scorer.setUseScore(useScore);
        }

        scorer.setKnowledge(knowledge);
        scorer.clearBookmarks();

        List<Node> bestPerm = new ArrayList<>(order);
        double best = Float.NEGATIVE_INFINITY;

        for (int r = 0; r < (useDataOrder ? 1 : numStarts); r++) {
            if (!useDataOrder) {
                shuffle(order);
            }

            this.start = System.currentTimeMillis();

            makeValidKnowledgeOrder(order);

            scorer.score(order);

            List<Node> perm;

            useDataOrder = true;
            perm = sp(scorer);

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

    public List<Node> sp(@NotNull TeyssierScorer scorer) {

        double maxScore = Float.NEGATIVE_INFINITY;
        List<Node> maxP = null;

        List<Node> variables = scorer.getPi();
        PermutationGenerator gen = new PermutationGenerator(variables.size());
        int[] perm;
        Set<Graph> frugalCpdags = new HashSet<>();

        int[] v = new int[scorer.size()];
        for (int i = 0; i < scorer.size(); i++) v[i] = i;

        List<Node> pi0 = GraphUtils.asList(v, variables);
        scorer.score(pi0);
        System.out.println("\t\t# edges for " + pi0 + scorer.getNumEdges());

        while ((perm = gen.next()) != null) {
            List<Node> p = GraphUtils.asList(perm, variables);

            if (violatesKnowledge(p)) continue;

            scorer.score(p);

            if (scorer.score() > maxScore) {
                maxScore = scorer.score();
                maxP = p;
                frugalCpdags.clear();
            }

            if (scorer.score() == maxScore) {
                frugalCpdags.add(scorer.getGraph(true));
            }
        }

        System.out.println("\t\t# frugal cpdags BY SP = " + frugalCpdags.size());
        System.out.println("\t\t# edges for frugal = " + frugalCpdags.iterator().next().getNumEdges());

        if (frugalCpdags.size() == 1) {
            System.out.println("\t!!!! U-FRUGAL BY SP");
        }

        if (verbose) {
            System.out.println("# Edges = " + scorer.getNumEdges()
                    + " Score = " + scorer.score()
                    + " (SP)"
                    + " Elapsed " + ((System.currentTimeMillis() - start) / 1000.0 + " sp"));
        }

        System.out.println("Frugal CPDAGs: ");

        for (Graph g : frugalCpdags) {
            System.out.println(g);
        }

        return maxP;
    }

    @NotNull
    public Graph getGraph(boolean cpDag) {
        if (this.scorer == null) throw new IllegalArgumentException("Please run algorithm first.");
        Graph graph = this.scorer.getGraph(cpDag);

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        graph.addAttribute("score ", nf.format(this.scorer.score()));
        return graph;
    }

    public void orientbk(Knowledge bk, Graph graph, List<Node> variables) {
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
//            graph.setEndpoint(from, to, Endpoint.CIRCLE);
//            this.changeFlag = true;
//            this.logger.forceLogMessage(SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
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
//            graph.setEndpoint(from, to, Endpoint.CIRCLE);
//            this.changeFlag = true;
//            this.logger.forceLogMessage(SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }
    }


    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
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

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
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
}