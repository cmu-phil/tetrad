package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.ConcurrencyUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

import static java.lang.Math.abs;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "CStar",
        command = "cstar",
        algoType = AlgType.forbid_latent_common_causes,
        description = "Performs a CStar analysis of the given dataset (Stekhoven, Daniel J., et al. " +
                "Causal stability ranking.\" Bioinformatics 28.21 (2012): 2819-2823) and returns a graph " +
                "in which all selected variables are shown as into the target. The target is the first variables."
)
public class CStar implements Algorithm {
    static final long serialVersionUID = 23L;
    private Algorithm algorithm;

    public CStar() {
        this.algorithm = new Fges();
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        System.out.println("# Available Processors = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Parallelism = " + parameters.getInt("parallelism"));

        DataSet _dataSet = (DataSet) dataSet;

        double percentSubsampleSize = parameters.getDouble("percentSubsampleSize");
        int numSubsamples = parameters.getInt("numSubsamples");
        int q = parameters.getInt("topQ");
        Node y = dataSet.getVariable(parameters.getString("targetName"));

        final List<Node> variables = dataSet.getVariables();
        variables.remove(y);

        Map<String, Integer> counts = new ConcurrentHashMap<>();
        Map<String, Double> medianEffects = new ConcurrentHashMap<>();
        for (Node node : variables) counts.put(node.getName(), 0);

        class Task implements Callable<Boolean> {
            private int i;
            private Map<String, Integer> counts;

            private Task(int i, Map<String, Integer> counts) {
                this.i = i;
                this.counts = counts;
            }

            @Override
            public Boolean call() {
                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    sampler.setWithoutReplacements(true);
                    DataSet sample = sampler.sample(_dataSet, (int) (percentSubsampleSize * _dataSet.getNumRows()));
                    Graph pattern = getPattern(sample, parameters);
                    List<Node> variables = pattern.getNodes();

                    Ida ida = new Ida(sample, pattern, variables);
                    Ida.NodeEffects effects = ida.getSortedMinEffects(y);

                    for (int i = 0; i < q; i++) {
                        final Node key = effects.getNodes().get(i);
                        counts.put(key.getName(), counts.get(key.getName()) + 1);
                    }

                    if (parameters.getBoolean("verbose")) {
                        System.out.println("Bootstrap #" + (i + 1) + " of " + numSubsamples);
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

        List<Node> outNodes = selectedVars(variables, numSubsamples, counts, parameters);

        Graph graph = new EdgeListGraph(outNodes);
        graph.addNode(y);

        for (int i = 0; i < new ArrayList<Node>(outNodes).size(); i++) {
            graph.addDirectedEdge(outNodes.get(i), y);
        }

        return graph;
    }

    public static Graph getPattern(DataSet sample, Parameters parameters) {
        ICovarianceMatrix covariances = new CovarianceMatrixOnTheFly(sample);

        Graph pattern;
        final SemBicScore score = new SemBicScore(covariances);
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));

        if (parameters.getInt("CStarAlg") == 1) {
            edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(score);
            fges.setParallelism(1);
            pattern = fges.search();
        } else if (parameters.getInt("CStarAlg") == 2) {
            PcAll pc = new PcAll(new IndTestScore(score), null);
            pc.setFasRule(PcAll.FasRule.FAS_STABLE);
            pc.setConflictRule(PcAll.ConflictRule.PRIORITY);
            pc.setColliderDiscovery(PcAll.ColliderDiscovery.FAS_SEPSETS);
            pattern = pc.search();
        } else {
            throw new IllegalArgumentException("Not configured for that algorithm: " + parameters.getInt("CStarAlg"));
        }
        return pattern;
    }

    public static List<Node> selectedVars(List<Node> variables, int numSubsamples, Map<String, Integer> counts, Parameters parameters) {
        List<Node> sortedVariables = new ArrayList<>(variables);
        sortedVariables.sort((o1, o2) -> Integer.compare(counts.get(o2.getName()), counts.get(o1.getName())));

        List<Double> pi = new ArrayList<>();

        for (Node sortedVariable : sortedVariables) {
            final Integer count = counts.get(sortedVariable.getName());
            final double _pi = count / (double) numSubsamples;
            pi.add(_pi);
        }

        List<Node> outNodes = new ArrayList<>();
        NumberFormat nf = new DecimalFormat("0.0000");

        for (int i = 0; i < pi.size(); i++) {
            if (pi.get(i) > 0.5) {
                outNodes.add(sortedVariables.get(i));
                final double pcer = pcer(pi.get(i), parameters.getInt("topQ"), variables.size());
            }
        }

        TextTable table = new TextTable(outNodes.size() + 1, 4);

        table.setToken(0, 0, "Index");
        table.setToken(0, 1, "Variable");
        table.setToken(0, 2, "Pi");
        table.setToken(0, 3, "PCER");

        for (int i = 0; i < outNodes.size(); i++) {
            final double pcer = pcer(pi.get(i), parameters.getInt("topQ"), variables.size());

            table.setToken(i + 1, 0, "" + (i + 1));
            table.setToken(i + 1, 1, sortedVariables.get(i).getName());
            table.setToken(i + 1, 2, nf.format(pi.get(i)));
            table.setToken(i + 1, 3, nf.format(pcer));
        }

        System.out.println();
        System.out.println(table);
        System.out.println();

        return outNodes;
    }

    // Per Comparison Error Rate (PCER)
    private static double pcer(double pi, int q, int p) {
        final double pcer = (1.0 / (2 * pi - 1)) * (((q * q) / ((double) (p * p))));
        if (Double.isInfinite(pcer)) return 1.0;
        else return pcer;
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
        parameters.add("topQ");
        parameters.add("piThreshold");
        parameters.add("targetName");
        parameters.add("CStarAlg");
        parameters.add("parallelism");
        return parameters;
    }
}
