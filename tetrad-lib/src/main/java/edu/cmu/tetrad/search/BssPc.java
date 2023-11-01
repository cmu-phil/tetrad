package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

import static edu.cmu.tetrad.graph.GraphTransforms.dagFromCPDAG;
import static edu.cmu.tetrad.graph.GraphUtils.asSet;


public class BssPc {
    private final DataSet data;
    private final int n;
    private int reps;
    private double threshold;
    private final Map<Set, Integer> sets;
    private final Graph graph;
    private double lambda;
    private boolean bes;
    private int restarts;
    private int threads;

    public BssPc(DataSet data) {
        this.data = data;
        this.n = data.getNumRows();
        this.reps = 10;
        this.threshold = 0.1;
        this.sets = new HashMap<>();
        this.graph = new EdgeListGraph(data.getVariables());
        this.lambda = 2.0;
        this.bes = false;
        this.restarts = 1;
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
            if ((key.size() == 2) && (sets.get(key) > (this.threshold * this.reps))) {
                Iterator<Node> itr = key.iterator();
                this.graph.addUndirectedEdge(itr.next(), itr.next());
            }
        }

        return this.graph;
    }
}