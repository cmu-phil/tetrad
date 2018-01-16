package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.FgesMb;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "FmbStar",
        command = "cstar2",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Uses stability selection (Meinhausen et al.) with FGES-MB (Ramsey et al.) to estimate the " +
                "nodes in the Markov blanket of a target. May be used to estimate nodes with strong influende " +
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
    private Graph initialGraph = null;

    public FmbStar() {
        this.algorithm = new Fges();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        DataSet _dataSet = (DataSet) dataSet;
        List<Node> variables = _dataSet.getVariables();

        double percentageB = parameters.getDouble("percentSubsampleSize");
        int numSubsamples = parameters.getInt("numSubsamples");
        double pithreshold = parameters.getDouble("piThreshold");
        Node y = dataSet.getVariable(parameters.getString("targetName"));

        Map<Node, Integer> counts = new ConcurrentHashMap<>();
        for (Node node : variables) counts.put(node, 0);

        class Task implements Callable<Boolean> {
            private int i;
            private Map<Node, Integer> counts;

            private Task(int i, Map<Node, Integer> counts) {
                this.i = i;
                this.counts = counts;
            }

            @Override
            public Boolean call() {
                if (parameters.getBoolean("verbose")) {
                    System.out.println("Bootstrap #" + (i + 1) + " of " + numSubsamples);
                    System.out.flush();
                }

                BootstrapSampler sampler = new BootstrapSampler();
                sampler.setWithoutReplacements(true);
                DataSet sample = sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows()));

                final ICovarianceMatrix covariances = new CovarianceMatrixOnTheFly(sample);
                final edu.cmu.tetrad.search.SemBicScore score = new edu.cmu.tetrad.search.SemBicScore(covariances);
                score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
                FgesMb fgesMb = new FgesMb(score);
                fgesMb.setParallelism(1);

                Graph g = fgesMb.search(y);

                for (final Node key : variables) {
                    if (g.containsNode(key)) counts.put(key, counts.get(key) + 1);
                }

                return true;
            }
        }

        List<Task> tasks = new ArrayList<>();

        for (int i = 0; i < numSubsamples; i++) {
            tasks.add(new Task(i, counts));
        }

        ForkJoinPoolInstance.getInstance().getPool().invokeAll(tasks);

        List<Node> sortedVariables = new ArrayList<>(variables);

        sortedVariables.sort((o1, o2) -> {
            final int d1 = counts.get(o1);
            final int d2 = counts.get(o2);
            return -Integer.compare(d1, d2);
        });

        double[] sortedFrequencies = new double[counts.keySet().size()];

        for (int i = 0; i < sortedVariables.size(); i++) {
            sortedFrequencies[i] = counts.get(sortedVariables.get(i)) / (double) numSubsamples;
        }

        Graph graph = new EdgeListGraph(dataSet.getVariables());

        for (int i = 0; i < sortedVariables.size(); i++) {
            if (sortedVariables.get(i) == y) continue;
            if (sortedFrequencies[i] >= pithreshold) {
                graph.addUndirectedEdge(sortedVariables.get(i), y);
            }
        }

        return graph;
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
        parameters.add("piThreshold");
        parameters.add("targetName");
        return parameters;
    }
}
