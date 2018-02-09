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

import static java.lang.Integer.min;

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

        double percentageB = 0.5;
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
        final Map<Integer, Map<String, Double>> minimalEffects = new ConcurrentHashMap<>();

        for (int q = 1; q < parameters.getInt("maxQ"); q++) {
            counts.put(q, new HashMap<>());
            for (Node node : variables) counts.get(q).put(node.getName(), 0);
        }

        for (int q = 1; q < min(parameters.getInt("maxQ"), variables.size()); q++) {
            final HashMap<String, Integer> map = new HashMap<>();
            for (Node node : variables) map.put(node.getName(), 0);
            counts.put(q, map);
        }

        for (int b = 0; b < numSubsamples; b++) {
            final HashMap<String, Double> map = new HashMap<>();
            for (Node node : variables) map.put(node.getName(), 0.0);
            minimalEffects.put(b, map);
        }

        class Task implements Callable<Boolean> {
            private int b;
            private Map<Integer, Map<String, Integer>> counts;
            private Map<Integer, Map<String, Double>> minimalEffects;

            private Task(int b, Map<Integer, Map<String, Integer>> counts, Map<Integer, Map<String, Double>> minimalEffects) {
                this.b = b;
                this.counts = counts;
                this.minimalEffects = minimalEffects;
            }

            @Override
            public Boolean call() {
                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    sampler.setWithoutReplacements(true);
                    DataSet sample = sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows()));
//                    sample = DataUtils.standardizeData(sample);

                    ICovarianceMatrix covariances = new CovarianceMatrixOnTheFly(sample);
                    final SemBicScore score = new SemBicScore(covariances);
                    score.setPenaltyDiscount(penaltyDiscount);

                    FgesMb fgesMb = new FgesMb(score);
                    fgesMb.setParallelism(1);
                    Graph mb = fgesMb.search(y);

//                    {
//                        List<Node> targets2 = new ArrayList<>();
//
//                        for (Node n : mb.getNodes()) {
//                            if (mb.existsSemiDirectedPathFromTo(n, Collections.singleton(y))) {
//                                targets2.add(n);
//                            }
//                        }
//
//                        targets2.add(y);
//
//                        FgesMb fgesMb2 = new FgesMb(score);
//                        fgesMb2.setParallelism(1);
//                        mb = fgesMb2.search(targets2);
//                    }

//                    {
//                        List<Node> targets2 = new ArrayList<>();
//
//                        for (Node n : mb.getAdjacentNodes(y)) {
//                            if (mb.existsSemiDirectedPathFromTo(n, Collections.singleton(y))) {
//                                targets2.add(n);
//                            }
//                        }
//
//                        targets2.add(y);
//
//                        FgesMb fgesMb2 = new FgesMb(score);
//                        fgesMb2.setParallelism(1);
//                        mb = fgesMb2.search(targets2);
//                    }

                    Ida ida = new Ida(sample, mb);
                    Ida.NodeEffects effects = ida.getSortedMinEffects(y);

                    for (int q = 1; q < min(parameters.getInt("maxQ"), variables.size()); q++) {
                        for (int i = 0; i < q; i++) {
                            if (i < effects.getNodes().size()) {
                                if (effects.getEffects().get(i) > 0) {
                                    final String name = effects.getNodes().get(i).getName();
                                    counts.get(q).put(name, counts.get(q).get(name) + 1);
                                }
                            }
                        }
                    }

                    for (int r = 0; r < effects.getNodes().size(); r++) {
                        Node n = effects.getNodes().get(r);
                        Double e = effects.getEffects().get(r);
                        minimalEffects.get(b).put(n.getName(), e);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        }


        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int b = 0; b < numSubsamples; b++) {
            tasks.add(new Task(b, counts, minimalEffects));
        }

        ConcurrencyUtils.runCallables(tasks, parameters.getInt("parallelism"));

        return CStar.selectedVars(variables, numSubsamples, counts, minimalEffects, parameters);
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
        parameters.add("targetName");
        parameters.add("maxEr");
        parameters.add("parallelism");
        return parameters;
    }
}
