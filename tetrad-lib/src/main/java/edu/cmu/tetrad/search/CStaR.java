package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ConcurrencyUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Integer.min;
import static java.lang.Math.pow;

public class CStaR {

    private int numSubsamples;
    private double penaltyDiscount;
    private double maxEr;
    private int parallelism;
    private Graph trueDag = null;

    public CStaR() {
    }

    public List<Record> getRecords(DataSet dataSet, Node target) {
        final DataSet selection = selectVariables(dataSet, target, penaltyDiscount, parallelism);

        final List<Node> variables = selection.getVariables();
        variables.remove(target);

        final Map<Integer, Map<String, Double>> minimalEffects = new ConcurrentHashMap<>();

        for (int b = 0; b < getNumSubsamples(); b++) {
            final Map<String, Double> map = new ConcurrentHashMap<>();
            for (Node node : variables) map.put(node.getName(), 0.0);
            minimalEffects.put(b, map);
        }

        final List<Ida.NodeEffects> effects = new ArrayList<>();

        class Task implements Callable<Boolean> {
            private Task() {
            }

            public Boolean call() {
                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    sampler.setWithoutReplacements(true);
                    DataSet sample = sampler.sample(selection, (int) (selection.getNumRows() / 2));
                    SemBicScore score = new SemBicScore(new CorrelationMatrixOnTheFly(sample));
                    score.setPenaltyDiscount(getPenaltyDiscount());
                    IndependenceTest test = new IndTestScore(score);
                    Graph pattern = getPattern(test);
                    Ida ida = new Ida(sample, pattern);
                    effects.add(ida.getSortedMinEffects(target));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int b = 0; b < getNumSubsamples(); b++) {
            tasks.add(new Task());
        }

        ConcurrencyUtils.runCallables(tasks, getParallelism());

        List<Node> bestNodes = new ArrayList<>();
        List<Double> bestPi = new ArrayList<>();
        List<Integer> bestQs = new ArrayList<>();

        int q = 2;
        int maxOut = 0;

        while (q++ < variables.size()) {
            final Map<String, Integer> map = new ConcurrentHashMap<>();
            for (Node node : variables) map.put(node.getName(), 0);

            for (Ida.NodeEffects _effects : effects) {
                for (int i = 0; i <= q; i++) {
                    if (i < _effects.getNodes().size()) {
                        if (_effects.getEffects().get(i) > 0) {
                            map.put(_effects.getNodes().get(i).getName(), map.get(_effects.getNodes().get(i).getName()) + 1);
                        }
                    }
                }

            }

            for (int b = 0; b < effects.size(); b++) {
                Ida.NodeEffects _effects = effects.get(b);

                for (int r = 0; r < _effects.getNodes().size(); r++) {
                    Node n = _effects.getNodes().get(r);
                    Double e = _effects.getEffects().get(r);
                    minimalEffects.get(b).put(n.getName(), e);
                }
            }

            List<Node> sortedVariables = new ArrayList<>(variables);
            sortedVariables.sort((o1, o2) -> Integer.compare(map.get(o2.getName()), map.get(o1.getName())));

            List<Double> pi = new ArrayList<>();

            for (Node v : sortedVariables) {
                final Integer count = map.get(v.getName());
                pi.add(count / ((double) getNumSubsamples()));
            }

            List<Node> outNodes1 = new ArrayList<>();
            List<Double> outPis = new ArrayList<>();
            List<Integer> outQs = new ArrayList<>();

            int numOut = 0;

            for (int i = 0; i < pi.size(); i++) {
                final double ev = er(pi.get(i), q, variables.size());

                if (ev <= getMaxEr()) {
                    numOut++;
                    outNodes1.add(sortedVariables.get(i));
                    outPis.add(pi.get(i));
                    outQs.add(q);
                }
            }

            if (numOut < maxOut) {
                break;
            } else {
                maxOut = numOut;
            }

            bestNodes = new ArrayList<>(outNodes1);
            bestPi = new ArrayList<>(outPis);
            bestQs = new ArrayList<>(outQs);
        }

        List<Node> ancestors = new ArrayList<>();

        if (trueDag != null) {
            Graph truePattern = SearchGraphUtils.patternForDag(trueDag);
            truePattern = GraphUtils.replaceNodes(truePattern, bestNodes);
            truePattern = GraphUtils.replaceNodes(truePattern, Collections.singletonList(target));

            if (truePattern != null) {
                for (Node node : truePattern.getNodes()) {
                    if (truePattern.existsSemiDirectedPathFromTo(node, Collections.singleton(target))) {
                        ancestors.add(node);
                    }
                }
            }
        }

        List<Record> records = new ArrayList<>();

        for (int i = 0; i < bestNodes.size(); i++) {

            final double er = er(bestPi.get(i), bestQs.get(i), variables.size());
            final double pcer = pcer(bestPi.get(i), bestQs.get(i), variables.size());

            List<Double> e = new ArrayList<>();

            for (int b = 0; b < getNumSubsamples(); b++) {
                final String name = bestNodes.get(i).getName();
                final double m = minimalEffects.get(b).get(name);
                e.add(m);
            }

            double[] _e = new double[e.size()];
            for (int t = 0; t < e.size(); t++) _e[t] = e.get(t);
            double avg = StatUtils.mean(_e);

            records.add(new Record(bestNodes.get(i), bestPi.get(i), avg, pcer, er, ancestors.contains(bestNodes.get(i))));
        }

        return records;
    }

    public static Graph getPattern(IndependenceTest test) {
        PcAll pc = new PcAll(test, null);
        pc.setFasRule(PcAll.FasRule.FAS_STABLE);
        pc.setConflictRule(PcAll.ConflictRule.OVERWRITE);
        pc.setColliderDiscovery(PcAll.ColliderDiscovery.MAX_P);
        return pc.search();
    }

    public int getNumSubsamples() {
        return numSubsamples;
    }

    public void setNumSubsamples(int numSubsamples) {
        this.numSubsamples = numSubsamples;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public double getMaxEr() {
        return maxEr;
    }

    public void setMaxEr(double maxEr) {
        this.maxEr = maxEr;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public void setTrueDag(Graph trueDag) {
        this.trueDag = trueDag;
    }

    public static class Record {
        private Node variable;
        private double pi;
        private double effect;
        private double pcer;
        private double er;
        private boolean trueInfluence;

        Record(Node variable, double pi, double minEffect, double pcer, double er, boolean trueInfluence) {
            this.variable = variable;
            this.pi = pi;
            this.effect = minEffect;
            this.pcer = pcer;
            this.er = er;
            this.trueInfluence = trueInfluence;
        }

        public Node getVariable() {
            return variable;
        }

        public double getPi() {
            return pi;
        }

        public double getEffect() {
            return effect;
        }

        public double getPcer() {
            return pcer;
        }

        public double getEr() {
            return er;
        }

        public boolean isTrueInfluence() {
            return trueInfluence;
        }
    }

    // E(V) bound
    private static double er(double pi, double q, double p) {
        double v = pcer(pi, q, p) * p;
        if (v < 0 || v > q) return Double.POSITIVE_INFINITY;
        return v;
    }

    private static double erq(double pi, double q, double p) {
        double v = er(pi, q, p) / q;
        if (v < 0 || v > 1) return Double.POSITIVE_INFINITY;
        return v;
    }

    // Per comparison error rate.
    private static double pcer(double pi, double q, double p) {
        double v = pow(q / (2 * p), 2) * (1.0 / (2 * pi - 1));
        if (v < 0 || v > 1) return Double.POSITIVE_INFINITY;
        return v;
    }

    public String makeTable(List<Record> records) {
        TextTable table = new TextTable(records.size() + 1, 6);
        NumberFormat nf = new DecimalFormat("0.0000");

        table.setToken(0, 0, "Index");
        table.setToken(0, 1, "Variable");
        table.setToken(0, 2, "PI");
        table.setToken(0, 3, "Average Effect");
        table.setToken(0, 4, "PCER");
        table.setToken(0, 5, "ER");

        int numStarred = 0;

        for (int i = 0; i < records.size(); i++) {
            table.setToken(i + 1, 0, "" + (i + 1));
            final boolean contains = records.get(i).isTrueInfluence();
            if (contains) numStarred++;
            table.setToken(i + 1, 1, (contains ? "* " : "") + records.get(i).getVariable().getName());
            table.setToken(i + 1, 2, nf.format(records.get(i).getPi()));
            table.setToken(i + 1, 3, nf.format(records.get(i).getEffect()));
            table.setToken(i + 1, 4, nf.format(records.get(i).getPcer()));
            table.setToken(i + 1, 5, nf.format(records.get(i).getEr()));
        }

        return "\n" + table + "\n" + "# FP = " +
                (records.size() - numStarred) + "\n";
    }

    public Graph makeGraph(Node y, List<Record> records) {
        List<Node> outNodes = new ArrayList<>();
        for (Record record : records) outNodes.add(record.getVariable());

        Graph graph = new EdgeListGraph(outNodes);
        graph.addNode(y);

        for (int i = 0; i < new ArrayList<Node>(outNodes).size(); i++) {
            graph.addDirectedEdge(outNodes.get(i), y);
        }

        return graph;
    }

    private static void printRanks(List<Node> variables, double numSubsamples, Map<Integer, Map<String, Integer>> counts, Parameters parameters) {
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
            System.out.println((j + 1) + ". " + sortedVariables.get(j) + " "
                    + rankMedians[variables.indexOf(sortedVariables.get(j))]);
        }
    }

    private DataSet selectVariables(DataSet fullData, Node y, double penaltyDiscount, int parallelism) {
        final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(fullData));
        score.setPenaltyDiscount(penaltyDiscount);
        IndependenceTest test = new IndTestScore(score);

        List<Node> selection = new ArrayList<>();

        final List<Node> variables = fullData.getVariables();

        {
            class Task implements Callable<Boolean> {
                private int from;
                private int to;
                private Node y;

                private Task(int from, int to, Node y) {
                    this.from = from;
                    this.to = to;
                    this.y = y;
                }

                @Override
                public Boolean call() {
                    for (int n = from; n < to; n++) {
                        final Node node = variables.get(n);
                        if (node != y) {
                            if (!test.isIndependent(node, y)) {
                                if (!selection.contains(node)) {
                                    selection.add(node);
                                }
                            }
                        }
                    }

                    return true;
                }
            }

            final int chunk = 50;
            List<Callable<Boolean>> tasks;

            {
                tasks = new ArrayList<>();

                for (int from = 0; from < variables.size(); from += chunk) {
                    final int to = Math.min(variables.size(), from + chunk);
                    tasks.add(new Task(from, to, y));
                }

                ConcurrencyUtils.runCallables(tasks, parallelism);
            }

            {
                tasks = new ArrayList<>();

                for (Node s : new ArrayList<>(selection)) {
                    for (int from = 0; from < variables.size(); from += chunk) {
                        final int to = Math.min(variables.size(), from + chunk);
                        tasks.add(new Task(from, to, s));
                    }
                }
            }

            ConcurrencyUtils.runCallables(tasks, parallelism);
        }

        final DataSet dataSet = fullData.subsetColumns(selection);

        System.out.println("# selected variables = " + dataSet.getVariables().size());

        return dataSet;

    }
}
