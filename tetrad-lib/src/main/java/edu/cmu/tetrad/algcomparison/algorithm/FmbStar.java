package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.FgesMb;
import edu.cmu.tetrad.search.Ida;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.ConcurrencyUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "FmbStar",
        command = "fmbstar",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Uses stability selection (Meinhausen et al.) with FGES-MB (Ramsey et al.) to estimate the " +
                "nodes in the Markov blanket of a target. May be used to estimate nodes with influence " +
                "on a target, in the style of CStar (Stekhoven et al.)" +
                "\nMeinshausen, Nicolai, and Peter Bühlmann. \"Stability selection.\" Journal of the Royal Statistical " +
                "Society: Series B (Statistical Methodology) 72.4 (2010): 417-473." +
                "\nStekhoven, Daniel J., et al. \"Causal stability ranking.\" Bioinformatics 28.21 (2012): 2819-2823." +
                "\nRamsey, Joseph, et al. \"A million variables and more: the Fast Greedy Equivalence Search algorithm for " +
                "learning high-dimensional graphical causal models, with an application to functional magnetic " +
                "resonance images.\" International journal of data science and analytics 3.2 (2017): 121-129."
)
public class FmbStar implements Algorithm {
    static final long serialVersionUID = 23L;
    private Algorithm algorithm;

    public FmbStar() {
        this.algorithm = new Fges();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        System.out.println("# Available Processors = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Parallelism = " + parameters.getInt("parallelism"));

        DataSet _dataSet = (DataSet) dataSet;
        List<Node> variables = _dataSet.getVariables();

        double percentageB = parameters.getDouble("percentSubsampleSize");
        int numSubsamples = parameters.getInt("numSubsamples");
        int q = parameters.getInt("maxQ");
        Node y = dataSet.getVariable(parameters.getString("targetName"));
        double penaltyDiscount = parameters.getDouble("penaltyDiscount");
        variables.remove(y);

        List<Node> nodes = getNodes(parameters, _dataSet, variables, percentageB, numSubsamples,
                y, penaltyDiscount);
        Set<Node> allNodes = new HashSet<>(nodes);

        Graph graph = new EdgeListGraph(new ArrayList<>(allNodes));
        graph.addNode(y);

        for (Node w : allNodes) {
            graph.addDirectedEdge(w, y);
        }

        return graph;
    }

    private List<Node> getNodes(Parameters parameters, DataSet _dataSet, List<Node> variables, double percentageB,
                                int numSubsamples, Node y, double penaltyDiscount) {
        Map<Integer, Map<String, Integer>> counts = new ConcurrentHashMap<>();

        for (int q = 1; q < parameters.getInt("maxQ"); q++) {
            counts.put(q, new HashMap<>());
            for (Node node : variables) counts.get(q).put(node.getName(), 0);
        }

        class Task implements Callable<Boolean> {
            private int i;
            private Map<Integer, Map<String, Integer>> counts;

            private Task(int i, Map<Integer, Map<String, Integer>> counts) {
                this.i = i;
                this.counts = counts;
            }

            @Override
            public Boolean call() {
                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    sampler.setWithoutReplacements(true);
                    DataSet sample = sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows()));

                    ICovarianceMatrix covariances = new CovarianceMatrixOnTheFly(sample);
                    final SemBicScore score = new SemBicScore(covariances);
                    score.setPenaltyDiscount(penaltyDiscount);

                    FgesMb fgesMb = new FgesMb(score);
                    fgesMb.setParallelism(1);
                    Graph mb = fgesMb.search(y);

                     Ida ida = new Ida(sample, mb);
                    Ida.NodeEffects effects = ida.getSortedMinEffects(y);

                    if (effects.getEffects().isEmpty() || effects.getEffects().getFirst() == 0.0) {
                        return true;
                    }

                    for (int q = 1; q < parameters.getInt("maxQ"); q++) {
                        for (int i = 0; i < q; i++) {
                            if (i >= effects.getNodes().size()) continue;
                            final String name = effects.getNodes().get(i).getName();
                            counts.get(q).put(name, counts.get(q).get(name) + 1);
                        }
                    }

                    if (parameters.getBoolean("verbose")) {
                        System.out.println("Bootstrap #" + (i + 1) + " of " + numSubsamples);
                        System.out.flush();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        }


        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < numSubsamples; i++) {
            tasks.add(new Task(i, counts));
        }

        ConcurrencyUtils.runCallables(tasks, parameters.getInt("parallelism"));

        return CStar.selectedVars(variables, numSubsamples, counts, parameters);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return algorithm.getComparisonGraph(graph);
    }

    @Override
    public String getDescription() {
        return "CStar";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("numSubsamples");
        parameters.add("percentSubsampleSize");
        parameters.add("targetName");
        parameters.add("maxEv");
        parameters.add("parallelism");
        return parameters;
    }
}
