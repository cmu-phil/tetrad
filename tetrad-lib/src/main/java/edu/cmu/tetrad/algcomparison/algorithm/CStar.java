package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Ida;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.PcAll;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.ConcurrencyUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Integer.min;

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

        double percentSubsampleSize = 0.5;
        int numSubsamples = parameters.getInt("numSubsamples");
        Node y = dataSet.getVariable(parameters.getString("targetName"));

        final List<Node> variables = dataSet.getVariables();
        variables.remove(y);

        final Map<Integer, Map<String, Integer>> counts = new ConcurrentHashMap<>();
        final Map<Integer, Map<String, Double>> minimalEffects = new ConcurrentHashMap<>();

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
                    DataSet sample = sampler.sample(_dataSet, (int) (percentSubsampleSize * _dataSet.getNumRows()));
                    sample = DataUtils.standardizeData(sample);
                    Graph pattern = getPattern(sample, parameters);

                    Ida ida = new Ida(sample, pattern);
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

        List<Node> outNodes = selectedVars(variables, numSubsamples, counts, minimalEffects, parameters);

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

    public static List<Node> selectedVars(List<Node> variables, int numSubsamples, Map<Integer,
            Map<String, Integer>> counts, Map<Integer, Map<String, Double>> minimalEffects, Parameters parameters) {

        List<Node> bestNodes = new ArrayList<>();
        List<Double> bestPi = new ArrayList<>();
        int bestQ = 1;

        double maxRatio = 0.0;

        for (int q = 1; q < min(parameters.getInt("maxQ"), variables.size()); q++) {
            List<Node> sortedVariables = new ArrayList<>(variables);
            final int q1 = q;
            sortedVariables.sort((o1, o2) -> Integer.compare(counts.get(q1).get(o2.getName()), counts.get(q1).get(o1.getName())));

            List<Double> pi = new ArrayList<>();

            for (Node sortedVariable : sortedVariables) {
                final Integer count = counts.get(q).get(sortedVariable.getName());
                pi.add(count / ((double) numSubsamples));
            }

            List<Node> outNodes = new ArrayList<>();
            List<Double> outPis = new ArrayList<>();

            for (int i = 0; i < pi.size(); i++) {
                final double ev = ev(pi.get(i), q, pi.size(), parameters.getDouble("PIThreshold"));

                if (ev <= parameters.getDouble("maxEv")) {
                    if (!outNodes.contains(sortedVariables.get(i))) {
                        outNodes.add(sortedVariables.get(i));
                        outPis.add(pi.get(i));
                    }
                }

                if ((outNodes.size() / (double) pi.size()) >= maxRatio) {
                    maxRatio = outNodes.size() / (double) pi.size();
                    bestNodes = new ArrayList<>(outNodes);
                    bestPi = new ArrayList<>(outPis);
                    bestQ = q;
                }
            }
        }

        TextTable table = new TextTable(bestNodes.size() + 1, 5);
        NumberFormat nf = new DecimalFormat("0.0000");

        table.setToken(0, 0, "Index");
        table.setToken(0, 1, "Variable");
        table.setToken(0, 2, "PI");
        table.setToken(0, 3, "Average Effect");
        table.setToken(0, 4, "E[V]");

        for (int i = 0; i < bestNodes.size(); i++) {
            final double ev = ev(bestPi.get(i), bestQ, variables.size(), parameters.getDouble("PIThreshold"));

            List<Double> e = new ArrayList<>();

            for (int b = 0; b < numSubsamples; b++) {
                final String name = bestNodes.get(i).getName();
                final double m = minimalEffects.get(b).get(name);

                if (m != 0 && !Double.isNaN(m)) {
                    e.add(m);
                }
            }

            double[] _e = new double[e.size()];
            for (int t = 0; t < e.size(); t++) _e[t] = e.get(t);
            double avg = StatUtils.mean(_e);

            table.setToken(i + 1, 0, "" + (i + 1));
            table.setToken(i + 1, 1, bestNodes.get(i).getName());
            table.setToken(i + 1, 2, nf.format(bestPi.get(i)));
            table.setToken(i + 1, 3, nf.format(avg));
            table.setToken(i + 1, 4, nf.format(ev));
        }

        System.out.println();
        System.out.println(table);
        System.out.println();

        return bestNodes;
    }

    // Per Comparison Error Rate (PCER)
    private static double pcer(double pi, int q, int p, double piThreshold) {
        if (pi <= piThreshold) return Double.POSITIVE_INFINITY;
        return ((q * q) / ((double) (p * p))) / (2 * (piThreshold - .5));
    }

    // E[V]
    private static double ev(double pi, int q, int p, double piThreshold) {
        double ev = (q * q) / (p * (2 * (pi - piThreshold)));
        if (ev < 0) ev = Double.POSITIVE_INFINITY;
        return ev;
    }

    private static double e1(double pi, int q, int p, double piThreshold) {
        if (pi <= piThreshold) return Double.POSITIVE_INFINITY;
        return ((q * q) / ((double) p)) / (2 * piThreshold - 1);
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
        parameters.add("maxQ");
        parameters.add("targetName");
        parameters.add("CStarAlg");
        parameters.add("maxEv");
        parameters.add("parallelism");
        return parameters;
    }
}
