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
import edu.cmu.tetrad.util.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static edu.cmu.tetrad.graph.GraphUtils.replaceNodes;
import static java.lang.Integer.min;
import static java.lang.Math.pow;

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
    private List<Node> ancestors;

    public CStar(List<Node> ancestors) {
        this.algorithm = new Fges();
        this.ancestors = ancestors;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        System.out.println("# Available Processors = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Parallelism = " + parameters.getInt("parallelism"));
        double numBootstraps = 1.0 / parameters.getDouble("bootstrapSelectionSize");

        DataSet _dataSet = (DataSet) dataSet;

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

            public Boolean call() {
                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    sampler.setWithoutReplacements(true);
                    DataSet sample = sampler.sample(_dataSet, (int) (_dataSet.getNumRows() / numBootstraps));
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

        List<Node> outNodes = selectedVars(variables, numSubsamples, counts, minimalEffects, parameters, ancestors, numBootstraps);

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
            pc.setConflictRule(PcAll.ConflictRule.OVERWRITE);
            pc.setColliderDiscovery(PcAll.ColliderDiscovery.MAX_P);
            pattern = pc.search();
        } else {
            throw new IllegalArgumentException("Not configured for that algorithm: " + parameters.getInt("CStarAlg"));
        }
        return pattern;
    }

    public static List<Node> selectedVars(List<Node> variables, int numSubsamples, Map<Integer,
            Map<String, Integer>> counts, Map<Integer, Map<String, Double>> minimalEffects, Parameters parameters,
                                          List<Node> ancestors, double numBootstraps) {
        ancestors = replaceNodes(ancestors, variables);

        List<Node> bestNodes = new ArrayList<>();
        List<Double> bestPi = new ArrayList<>();
        List<Integer> bestQs = new ArrayList<>();

        List<Node> bestNodes2 = new ArrayList<>();
        List<Double> bestPi2 = new ArrayList<>();
        List<Integer> bestQ2 = new ArrayList<>();

        int maxOut = 0;

        for (int q = 1; q < min(parameters.getInt("maxQ"), variables.size()); q++) {
            final int q1 = q;
            List<Node> sortedVariables = new ArrayList<>(variables);
            sortedVariables.sort((o1, o2) -> Integer.compare(counts.get(q1).get(o2.getName()), counts.get(q1).get(o1.getName())));

            List<Double> pi = new ArrayList<>();

            for (Node v : sortedVariables) {
                final Integer count = counts.get(q).get(v.getName());
                pi.add(count / ((double) numSubsamples));
            }

            List<Node> outNodes = new ArrayList<>();
            List<Double> outPis = new ArrayList<>();
            List<Integer> outQs = new ArrayList<>();

            for (int i = 0; i < pi.size(); i++) {
                final double ev = er(pi.get(i), q, variables.size(), numBootstraps);

                if (ev == Double.POSITIVE_INFINITY) continue;

                if (ev <= parameters.getDouble("maxEr")) {
                    if (!outNodes.contains(sortedVariables.get(i))) {
                        outNodes.add(sortedVariables.get(i));
                        outPis.add(pi.get(i));
                        outQs.add(q);
                    }

                    if (!bestNodes2.contains(sortedVariables.get(i))) {
                        bestNodes2.add(sortedVariables.get(i));
                        bestPi2.add(pi.get(i));
                        bestQ2.add(q);
                    }
                }
            }

            if ((outNodes.size()) >= maxOut) {
                maxOut = outNodes.size();
                bestNodes = new ArrayList<>(outNodes);
                bestPi = new ArrayList<>(outPis);
                bestQs = new ArrayList<>(outQs);
            }

        }

        printRanks(variables, numSubsamples, counts, parameters, ancestors);

        TextTable table = new TextTable(bestNodes.size() + 1, 6);
        NumberFormat nf = new DecimalFormat("0.0000");

        table.setToken(0, 0, "Index");
        table.setToken(0, 1, "Variable");
        table.setToken(0, 2, "PI");
        table.setToken(0, 3, "Average Effect");
        table.setToken(0, 4, "PCER");
        table.setToken(0, 5, "ER");

        ancestors = replaceNodes(ancestors, variables);

        for (int i = 0; i < bestNodes.size(); i++) {
            final double er = er(bestPi.get(i), bestQs.get(i), variables.size(), numBootstraps);
            final double pcer = pcer(bestPi.get(i), bestQs.get(i), variables.size(), numBootstraps);

            List<Double> e = new ArrayList<>();

            for (int b = 0; b < numSubsamples; b++) {
                final String name = bestNodes.get(i).getName();
                final double m = minimalEffects.get(b).get(name);
                e.add(m);
            }

            double[] _e = new double[e.size()];
            for (int t = 0; t < e.size(); t++) _e[t] = e.get(t);
            double avg = StatUtils.mean(_e);

            table.setToken(i + 1, 0, "" + (i + 1));
            final boolean contains = ancestors.contains(bestNodes.get(i));
            table.setToken(i + 1, 1, (contains ? "* " : "") + bestNodes.get(i).getName());
            table.setToken(i + 1, 2, nf.format(bestPi.get(i)));
            table.setToken(i + 1, 3, nf.format(avg));
            table.setToken(i + 1, 4, nf.format(pcer));
            table.setToken(i + 1, 5, nf.format(er));
        }

        System.out.println();
        System.out.println(table);
        System.out.println();

        return bestNodes;
    }

    private static void printRanks(List<Node> variables, double numSubsamples, Map<Integer, Map<String, Integer>> counts, Parameters parameters, List<Node> ancestors) {
        System.out.println("\n# ancestors = " + ancestors.size() + "\n");

        final int maxQ = min(parameters.getInt("maxQ"), variables.size());

        List<Integer> qs = new ArrayList<>();

        for (int q = maxQ / 10; q < maxQ; q += maxQ / 10) qs.add(q);

        double[][] ranks = new double[variables.size()][qs.size()];

        for (int s = 0; s < qs.size(); s++) {
            int q = qs.get(s);
            List<Double> pi = new ArrayList<>();

            for (Node v : variables) {
                final Integer count = counts.get(q).get(v.getName());
                pi.add(count / numSubsamples);
            }
            double[] _pi = new double[pi.size()];

            for (int i = 0; i < variables.size(); i++) {
                final Integer count = counts.get(q).get(variables.get(i).getName());
                _pi[i] = count / numSubsamples;
            }

            double[] _ranks = StatUtils.getRanks(_pi);

            for (int i = 0; i < variables.size(); i++) {
                ranks[i][s] = _ranks[i];
            }
        }

        double[] rankMedians = new double[ranks.length];

        for (int j = 0; j < ranks.length; j++) {
            rankMedians[j] = StatUtils.median(ranks[j]);
        }

        System.out.println("Rank medians");

        List<Node> sortedVariables = new ArrayList<>(variables);
        sortedVariables.sort((o1, o2) -> Double.compare(rankMedians[variables.indexOf(o2)], rankMedians[variables.indexOf(o1)]));

        for (int j = 0; j < sortedVariables.size(); j++) {
            final boolean contains = ancestors.contains(sortedVariables.get(j));
            if (contains) {
                System.out.println((j + 1) + ". " + (contains ? "* " : "") + sortedVariables.get(j) + " "
                        + rankMedians[variables.indexOf(sortedVariables.get(j))]);
            }
        }
    }

    // E(V) bound
    private static double er(double pi, double q, double p, double numBootstraps) {
        double v = pcer(pi, q, p, numBootstraps) * p;
        if (v < 0 || v > q) return Double.POSITIVE_INFINITY;
        return v;
    }

    private static double erq(double pi, double q, double p, double numBootstraps) {
        double v = er(pi, q, p, numBootstraps) / q;
        if (v < 0 || v > 1) return Double.POSITIVE_INFINITY;
        return v;
    }

    // Per comparison error rate.
    private static double pcer(double pi, double q, double p, double numBootstraps) {
        final double numCombinations = numBootstraps * (numBootstraps - 1) / 2;
        double v = pow(q / p, numBootstraps) * (numCombinations / (2 * pi - 1));
        if (v < 0 || v > 1) return Double.POSITIVE_INFINITY;
        return v;
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
        parameters.add("bootstrapSelectionSize");
        parameters.add("maxQ");
        parameters.add("targetName");
        parameters.add("CStarAlg");
        parameters.add("maxEr");
        parameters.add("parallelism");
        return parameters;
    }
}
