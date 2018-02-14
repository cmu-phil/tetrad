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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static edu.cmu.tetrad.graph.GraphUtils.replaceNodes;
import static java.lang.Integer.min;
import static java.lang.Math.pow;

//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "CStar",
//        command = "cstar",
//        algoType = AlgType.forbid_latent_common_causes,
//        description = "Performs a CStar analysis of the given dataset (Stekhoven, Daniel J., et al. " +
//                "Causal stability ranking.\" Bioinformatics 28.21 (2012): 2819-2823) and returns a graph " +
//                "in which all selected variables are shown as into the target. The target is the first variables."
//)

@edu.cmu.tetrad.annotation.Algorithm(
        name = "CStar",
        command = "cstar",
        algoType = AlgType.forbid_latent_common_causes,
        description = ""
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
        double numBootstraps = 1.0 / parameters.getDouble("percentSubsampleSize");

        DataSet _dataSet = (DataSet) dataSet;

        int numSubsamples = parameters.getInt("numSubsamples");

        List<Node> y = new ArrayList<>();

        if ("all".equalsIgnoreCase(parameters.getString("targetName"))) {
            y.addAll(dataSet.getVariables());
        } else if ("connected".equalsIgnoreCase(parameters.getString("targetName"))) {
            Graph pattern = getPattern(_dataSet, parameters);

            for (Node n : pattern.getNodes()) {
                if (pattern.getAdjacentNodes(n).size() >= 3
                        ) {
                    y.add(n);
                }
            }

            System.out.println("# targets = " + y.size());
        } else {
            y.add(dataSet.getVariable(parameters.getString("targetName")));
        }

        final List<Node> variables = dataSet.getVariables();
        variables.remove(y);

        final Map<Integer, Map<Pair, Integer>> counts = new ConcurrentHashMap<>();
        final Map<Integer, Map<Pair, Double>> minimalEffects = new ConcurrentHashMap<>();

        for (int q = 1; q < min(parameters.getInt("maxQ"), variables.size()); q++) {
            final HashMap<Pair, Integer> map = new HashMap<>();

            for (Node x : variables) {
                for (Node _y : y) {
                    if (x == _y) continue;
                    map.put(new Pair(x.getName(), _y.getName()), 0);
                }
            }

            counts.put(q, map);
        }

        for (int b = 0; b < numSubsamples; b++) {
            final HashMap<Pair, Double> map = new HashMap<>();

            for (Node x : variables) {
                for (Node _y : y) {
                    if (x == _y) continue;
                    map.put(new Pair(x.getName(), _y.getName()), 0.0);
                }
            }

            minimalEffects.put(b, map);
        }

        class Task implements Callable<Boolean> {
            private int b;
            private Map<Integer, Map<Pair, Integer>> counts;
            private Map<Integer, Map<Pair, Double>> minimalEffects;

            private Task(int b, Map<Integer, Map<Pair, Integer>> counts, Map<Integer, Map<Pair, Double>> minimalEffects) {
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

                    for (Node _y : y) {
                        Ida.NodeEffects effects = ida.getSortedMinEffects(_y);

                        for (int q = 1; q < min(parameters.getInt("maxQ"), variables.size()); q++) {
                            for (int i = 0; i < q; i++) {
                                if (i < effects.getNodes().size()) {
                                    if (effects.getEffects().get(i) > 0) {
                                        final String name = effects.getNodes().get(i).getName();
                                        final Pair key = new Pair(name, _y.getName());
                                        counts.get(q).put(key, counts.get(q).get(key) + 1);
                                    }
                                }
                            }
                        }

                        for (int r = 0; r < effects.getNodes().size(); r++) {
                            Node n = effects.getNodes().get(r);
                            Double e = effects.getEffects().get(r);
                            final Pair key = new Pair(n.getName(), _y.getName());
                            minimalEffects.get(b).put(key, e);
                        }
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

        List<Pair> bestPairs = bestPairs(variables, numSubsamples, counts, minimalEffects, parameters, numBootstraps, y);

        System.out.println("best pairs = " + bestPairs);

        Graph graph = new EdgeListGraph();

        for (Pair pair : bestPairs) {
            Node _x = dataSet.getVariable(pair.x);
            Node _y = dataSet.getVariable(pair.y);

            if (!graph.containsNode(_x)) graph.addNode(_x);
            if (!graph.containsNode(_y)) graph.addNode(_y);

            graph.addDirectedEdge(_x, _y);
        }

        return graph;
    }

    public static Graph getPattern(DataSet sample, Parameters parameters) {
        ICovarianceMatrix covariances = new CovarianceMatrixOnTheFly(sample);

        final SemBicScore score = new SemBicScore(covariances);
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));

        PcAll pc = new PcAll(new IndTestScore(score), null);
        pc.setFasRule(PcAll.FasRule.FAS_STABLE);
        pc.setConflictRule(PcAll.ConflictRule.OVERWRITE);
        pc.setColliderDiscovery(PcAll.ColliderDiscovery.MAX_P);
        return pc.search();
    }

    public static List<Pair> bestPairs(List<Node> variables, int numSubsamples, Map<Integer,
            Map<Pair, Integer>> counts, Map<Integer, Map<Pair, Double>> minimalEffects, Parameters parameters,
                                       double numBootstraps, List<Node> y) {
        List<Pair> bestPairs = new ArrayList<>();
        List<Double> bestPi = new ArrayList<>();
        List<Integer> bestQs = new ArrayList<>();

        List<Pair> bestPairs2 = new ArrayList<>();
        List<Double> bestPi2 = new ArrayList<>();
        List<Integer> bestQ2 = new ArrayList<>();

        int maxOut = 0;

        for (int q = 1; q < min(parameters.getInt("maxQ"), variables.size()); q++) {
            final int q1 = q;
            List<Pair> sortedPairs = new ArrayList<>(counts.get(q).keySet());
            sortedPairs.sort((o1, o2) -> Integer.compare(counts.get(q1).get(o2),
                    counts.get(q1).get(o1)));

            List<Double> pi = new ArrayList<>();

            for (Pair v : sortedPairs) {
                final Integer count = counts.get(q).get(v);
                pi.add(count / ((double) numSubsamples));
            }

            System.out.println(pi);

            List<Pair> outPairs = new ArrayList<>();
            List<Double> outPis = new ArrayList<>();
            List<Integer> outQs = new ArrayList<>();

            for (int i = 0; i < pi.size(); i++) {
                final double ev = er(pi.get(i), q, variables.size(), numBootstraps);

                if (ev == Double.POSITIVE_INFINITY) continue;

                if (ev <= parameters.getDouble("maxEr")) {
                    if (!outPairs.contains(sortedPairs.get(i))) {
                        outPairs.add(sortedPairs.get(i));
                        outPis.add(pi.get(i));
                        outQs.add(q);
                    }

                    if (!bestPairs2.contains(sortedPairs.get(i))) {
                        bestPairs2.add(sortedPairs.get(i));
                        bestPi2.add(pi.get(i));
                        bestQ2.add(q);
                    }
                }
            }

            if ((outPairs.size()) >= maxOut) {
                maxOut = outPairs.size();
                bestPairs = new ArrayList<>(outPairs);
                bestPi = new ArrayList<>(outPis);
                bestQs = new ArrayList<>(outQs);
            }

        }

//        printRanks(variables, numSubsamples, counts, parameters, ancestors, y);

        TextTable table = new TextTable(bestPairs.size() + 1, 6);
        NumberFormat nf = new DecimalFormat("0.0000");

        table.setToken(0, 0, "Index");
        table.setToken(0, 1, "Pair");
        table.setToken(0, 2, "PI");
        table.setToken(0, 3, "Average Effect");
        table.setToken(0, 4, "PCER");
        table.setToken(0, 5, "ER");

        for (int i = 0; i < bestPairs.size(); i++) {
            final double er = er(bestPi.get(i), bestQs.get(i), variables.size(), numBootstraps);
            final double pcer = pcer(bestPi.get(i), bestQs.get(i), variables.size(), numBootstraps);

            List<Double> e = new ArrayList<>();

            for (int b = 0; b < numSubsamples; b++) {
                final double m = minimalEffects.get(b).get(bestPairs.get(i));
                e.add(m);
            }

            double[] _e = new double[e.size()];
            for (int t = 0; t < e.size(); t++) _e[t] = e.get(t);
            double avg = StatUtils.mean(_e);

            table.setToken(i + 1, 0, "" + (i + 1));

//            Pair pair = bestPairs.get(i);
//            Node _x = trueGraph.getNode(pair.x);
//            Node _y = trueGraph.getNode(pair.y);

            final boolean contains = false;//trueGraph.existsSemiDirectedPathFromTo(_x, Collections.singleton(_y));

            table.setToken(i + 1, 1, (contains ? "* " : "") + bestPairs.get(i));
            table.setToken(i + 1, 2, nf.format(bestPi.get(i)));
            table.setToken(i + 1, 3, nf.format(avg));
            table.setToken(i + 1, 4, nf.format(pcer));
            table.setToken(i + 1, 5, nf.format(er));
        }

        System.out.println();
        System.out.println(table);
        System.out.println();

        return bestPairs;
    }

    private static void printRanks(List<Node> variables, double numSubsamples, Map<Integer, Map<Pair,
            Integer>> counts, Parameters parameters, List<Node> ancestors, List<Node> y) {
        System.out.println("\n# ancestors = " + ancestors.size() + "\n");

        final int maxQ = min(parameters.getInt("maxQ"), variables.size());

        List<Integer> qs = new ArrayList<>();

        for (int q = maxQ / 10; q < maxQ; q += maxQ / 10) qs.add(q);

        double[][] ranks = new double[variables.size()][qs.size()];

        for (int s = 0; s < qs.size(); s++) {
            int q = qs.get(s);
            List<Double> pi = new ArrayList<>();

            for (Node _y : y) {
                for (Node v : variables) {
                    final Integer count = counts.get(q).get(new Pair(v.getName(), _y.getName()));

                    if (count != null) {
                        pi.add(count / numSubsamples);
                    }
                }
            }
            double[] _pi = new double[pi.size()];

            for (int i = 0; i < pi.size(); i++) _pi[i] = pi.get(i);

            double[] _ranks = StatUtils.getRanks(_pi);

            for (int i = 0; i < _ranks.length; i++) {
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
        parameters.add("percentSubsampleSize");
        parameters.add("maxQ");
        parameters.add("targetName");
        parameters.add("maxEr");
        parameters.add("parallelism");
        return parameters;
    }

    public static class Pair {
        private String x;
        private String y;

        public Pair(String x, String y) {
            this.x = x;
            this.y = y;
        }

        public String getX() {
            return x;
        }

        public String getY() {
            return y;
        }

        public int hashCode() {
            return x.hashCode() + 17 * y.hashCode();
        }

        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Pair)) return false;
            Pair pair = (Pair) o;
            return x.equals(pair.getX()) && y.equals(pair.getY());
        }

        public String toString() {
            return "(" + x + ", " + y + ")";
        }

    }
}
