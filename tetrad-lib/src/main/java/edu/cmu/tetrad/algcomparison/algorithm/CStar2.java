package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FgesMb;
import edu.cmu.tetrad.search.Ida;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.Parameters;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "CStar2",
        command = "cstar2",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Performs a pseudo CStar analysis (using FgesMb) and returns a graph " +
                "in which all selected variables are shown as into the target. The target is the first variables."
)
public class CStar2 implements Algorithm {

    static final long serialVersionUID = 23L;
    private Algorithm algorithm;
    private Graph initialGraph = null;

    public CStar2() {
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

            public Task(int i, Map<Node, Integer> counts) {
                this.i = i;
            }

            @Override
            public Boolean call() {
                System.out.println("\nBootstrap #" + (i + 1) + " of " + numSubsamples);

                BootstrapSampler sampler = new BootstrapSampler();
                sampler.setWithoutReplacements(true);
                DataSet sample = sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows()));

                final CovarianceMatrix covariances = new CovarianceMatrix(sample);
                final edu.cmu.tetrad.search.SemBicScore score = new edu.cmu.tetrad.search.SemBicScore(covariances);
                score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
                FgesMb fgesMb = new FgesMb(score);
//                fgesMb.setParallelism(1);

                Graph g = fgesMb.search(y);

                for (int j = 0; j < variables.size(); j++) {
                    final Node key = variables.get(j);
                    if (g.containsNode(key) && key != y) counts.put(key, counts.get(key) + 1);
                }

                System.out.println("Completed #" + (i + 1));

                return true;
            }
        }

//        List<Task> tasks = new ArrayList<>();

        for (int i = 0; i < numSubsamples; i++) {
            List<Task> tasks = new ArrayList<>();
            tasks.add(new Task(i, counts));
            ForkJoinPoolInstance.getInstance().getPool().invokeAll(tasks);
        }

//        ForkJoinPoolInstance.getInstance().getPool().invokeAll(tasks);

//        for (int i = 0; i < numSubsamples; i++) {
//            System.out.println("\nBootstrap #" + (i + 1) + " of " + numSubsamples);
//
//            BootstrapSampler sampler = new BootstrapSampler();
//            sampler.setWithoutReplacements(true);
//            DataSet sample = sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows()));
//
//            System.out.println("Made sample");
//
//            final CovarianceMatrix covariances = new CovarianceMatrix(sample);
//            final edu.cmu.tetrad.search.SemBicScore score = new edu.cmu.tetrad.search.SemBicScore(covariances);
//            score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
//            FgesMb fgesMb = new FgesMb(score);
//
//            System.out.println("Made FGES-MB");
//
//            Graph g = fgesMb.search(y);
//
//            System.out.println("Search complete");
//
//            for (int j = 0; j < variables.size(); j++) {
//                final Node key = variables.get(j);
//                if (g.containsNode(key) && key != y) counts.put(key, counts.get(key) + 1);
//            }
//        }

        System.out.println(counts);

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

        System.out.println(variables);
        System.out.println(sortedVariables);
        System.out.println(Arrays.toString(sortedFrequencies));

        Graph graph = new EdgeListGraph(dataSet.getVariables());

        for (int i = 0; i < sortedVariables.size(); i++) {
            if (sortedFrequencies[i] > pithreshold) {
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
