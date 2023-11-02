package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

import static edu.cmu.tetrad.graph.GraphTransforms.dagFromCPDAG;
import static edu.cmu.tetrad.graph.GraphUtils.asSet;


public class BssPc {
    private final DataSet data;
    private final int n;
    private int reps;
    private double doubleThreshold;
    private double tripleThreshold;
    private final Map<Set, Integer> sets;
    private final Graph graph;
    private double lambda;
    private boolean bes;
    private int restarts;
    private int threads;

    public BssPc(DataSet data) {
        this.data = data;
        this.n = data.getNumRows();
        this.reps = 100;
        this.doubleThreshold= 0.5;
        this.tripleThreshold= 0.5;
        this.sets = new HashMap<>();
        this.graph = new EdgeListGraph(data.getVariables());
        this.lambda = 2.0;
        this.bes = false;
        this.restarts = 10;
        this.threads = -1;
    }

    public Graph search() {
        for (int i = 0; i < reps; i++) {
            System.out.println(i);

            DataSet resampled = DataTransforms.getBootstrapSample(this.data, this.n);
            SemBicScore score = new SemBicScore(resampled, true);
            score.setPenaltyDiscount(this.lambda);
            score.setStructurePrior(0);

            Boss boss = new Boss(score);
            boss.setUseBes(this.bes);
            boss.setNumStarts(this.restarts);
            boss.setNumThreads(this.threads);
            boss.setUseDataOrder(false);
            boss.setResetAfterBM(false);
            boss.setResetAfterRS(false);
            boss.setVerbose(false);

            PermutationSearch search = new PermutationSearch(boss);
            Graph graph = dagFromCPDAG(search.search());

            for (Node node : graph.getNodes()) {
                List<Node> parents = graph.getParents(node);
                ChoiceGenerator cg = new ChoiceGenerator(parents.size(), 2);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    Set<Node> S = asSet(choice, parents);
                    if (S.size() != 2) continue;
                    S.add(node);
                    if (!this.sets.containsKey(S)) this.sets.put(S, 0);
                    this.sets.put(S, this.sets.get(S) + 1);
                }

                for (Node parent : parents) {
                    Set<Node> S = new HashSet<>();
                    S.add(parent);
                    S.add(node);
                    if (!this.sets.containsKey(S)) this.sets.put(S, 0);
                    this.sets.put(S, this.sets.get(S) + 1);
                }
            }
        }

        for (Set<Node> key : this.sets.keySet()) {
            if ((key.size() == 2) && (sets.get(key) > (this.doubleThreshold * this.reps))) {
                Iterator<Node> itr = key.iterator();
                this.graph.addUndirectedEdge(itr.next(), itr.next());
            }
        }

        for (Set<Node> key : this.sets.keySet()) {
            if ((key.size() == 3) && (sets.get(key) > (this.tripleThreshold * this.reps))) {
                Iterator<Node> itr = key.iterator();
                Node a = itr.next();
                Node b = itr.next();
                Node c = itr.next();
                triple(a, b, c);
                triple(b, a, c);
                triple(c, a, b);
            }
        }

        MeekRules meekRules = new MeekRules();
        meekRules.setRevertToUnshieldedColliders(false);
        meekRules.orientImplied(this.graph);

        return this.graph;
    }

    private void triple(Node a, Node b, Node c) {
        if (!this.graph.isAdjacentTo(a, b)) return;
        if (!this.graph.isAdjacentTo(a, c)) return;
        if (this.graph.isAdjacentTo(b, c)) return;
        Edge edge;

        edge = this.graph.getEdge(a, b);
        if (edge.getNode1() == a) edge.setEndpoint1(Endpoint.ARROW);
        if (edge.getNode2() == a) edge.setEndpoint2(Endpoint.ARROW);

        edge = this.graph.getEdge(a, c);
        if (edge.getNode1() == a) edge.setEndpoint1(Endpoint.ARROW);
        if (edge.getNode2() == a) edge.setEndpoint2(Endpoint.ARROW);
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public double getDoubleThreshold() {
        return doubleThreshold;
    }

    public void setDoubleThreshold(double doubleThreshold) {
        this.doubleThreshold = doubleThreshold;
    }

    public double getTripleThreshold() {
        return tripleThreshold;
    }

    public void setTripleThreshold(double tripleThreshold) {
        this.tripleThreshold = tripleThreshold;
    }

    public double getLambda() {
        return lambda;
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    public boolean isBes() {
        return bes;
    }

    public void setBes(boolean bes) {
        this.bes = bes;
    }

    public int getRestarts() {
        return restarts;
    }

    public void setRestarts(int restarts) {
        this.restarts = restarts;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }
}