package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ConcurrencyUtils;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An adaptation of the CStaR algorithm (Steckoven et al., 2012).
 * <p>
 * Stekhoven, D. J., Moraes, I., Sveinbjörnsson, G., Hennig, L., Maathuis, M. H., & Bühlmann, P. (2012). Causal stability ranking. Bioinformatics, 28(21), 2819-2823.
 * <p>
 * Meinshausen, N., & Bühlmann, P. (2010). Stability selection. Journal of the Royal Statistical Society: Series B (Statistical Methodology), 72(4), 417-473.
 * <p>
 * Colombo, D., & Maathuis, M. H. (2014). Order-independent constraint-based causal structure learning. The Journal of Machine Learning Research, 15(1), 3741-3782.
 *
 * @author jdramsey@andrew.cmu.edu
 */
public class CStaS {

    private int maxTrekLength = 20;

    // A single record in the returned table.
    public static class Record implements TetradSerializable {
        private Node variable;
        private double pi;
        private double effect;
        private double pcer;
        private double er;
        private boolean ancestor;
        private boolean existsTrekToTarget;

        Record(Node variable, double pi, double minEffect, double pcer, double er, boolean ancestor, boolean existsTrekToTarget) {
            this.variable = variable;
            this.pi = pi;
            this.effect = minEffect;
            this.pcer = pcer;
            this.er = er;
            this.ancestor = ancestor;
            this.existsTrekToTarget = existsTrekToTarget;
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

        public boolean isAncestor() {
            return ancestor;
        }

        public boolean isExistsTrekToTarget() {
            return existsTrekToTarget;
        }
    }


    private IndependenceTest test;

    private int numSubsamples = 30;
    private double maxEr = 5;
    private int parallelism = Runtime.getRuntime().availableProcessors() * 10;
    private Graph trueDag = null;

    public CStaS() {
    }

    public List<Record> getRecords(DataSet dataSet, Node target, IndependenceTest test) {
        return getRecords(dataSet, dataSet.getVariables(), target, test);
    }

    /**
     * Returns records for a set of variables with expected number of false positives bounded by getMaxEr.
     *
     * @param dataSet            The full datasets to search over.
     * @param possiblePredictors A set of variables in the datasets over which to search.
     * @param target             The target variables.
     * @param test               This test is only used to make more tests like it for subsamples.
     */
    public List<Record> getRecords(DataSet dataSet, List<Node> possiblePredictors, Node target, IndependenceTest test) {
        if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof SemBicScore) {
            this.test = test;
        } else if (test instanceof IndTestFisherZ) {
            this.test = test;
        } else if (test instanceof ChiSquare) {
            this.test = test;
        } else if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof ConditionalGaussianScore) {
            this.test = test;
        } else {
            throw new IllegalArgumentException("That test is not configured.");
        }

        possiblePredictors = GraphUtils.replaceNodes(possiblePredictors, dataSet.getVariables());
        Node _target = dataSet.getVariable(target.getName());
        final DataSet selection = selectVariables(dataSet, possiblePredictors, _target, parallelism);

        final List<Node> variables = selection.getVariables();
        variables.remove(_target);

        final Map<Integer, Map<Node, Double>> minimalEffects = new ConcurrentHashMap<>();

        for (int b = 0; b < getNumSubsamples(); b++) {
            final Map<Node, Double> map = new ConcurrentHashMap<>();
            for (Node node : variables) map.put(node, 0.0);
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
                    Graph pattern = getPattern(sample);
                    Ida ida = new Ida(sample, pattern);
                    effects.add(ida.getSortedMinEffects(_target));
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

        List<Node> outNodes = new ArrayList<>();
        List<Double> outPis = new ArrayList<>();
        int bestQ = -1;

        for (int q = 1; q <= variables.size(); q++) {
            if (q / (double) variables.size() > 0.2) continue;

            final Map<Node, Integer> counts = new HashMap<>();
            for (Node node : variables) counts.put(node, 0);

            for (Ida.NodeEffects _effects : effects) {
                for (int i = 0; i < q; i++) {
                    if (i < _effects.getNodes().size()) {
                        if (_effects.getEffects().get(i) > 0) {
                            final Node key = _effects.getNodes().get(i);
                            counts.put(key, counts.get(key) + 1);
                        }
                    }
                }
            }

            for (int b = 0; b < effects.size(); b++) {
                Ida.NodeEffects _effects = effects.get(b);

                for (int r = 0; r < _effects.getNodes().size(); r++) {
                    Node n = _effects.getNodes().get(r);
                    Double e = _effects.getEffects().get(r);
                    minimalEffects.get(b).put(n, e);
                }
            }

            List<Node> sortedVariables = new ArrayList<>(variables);
            sortedVariables.sort((o1, o2) -> Integer.compare(counts.get(o2), counts.get(o1)));

            List<Double> pi = new ArrayList<>();

            for (Node v : sortedVariables) {
                final Integer count = counts.get(v);
                pi.add(count / ((double) getNumSubsamples()));
            }

            List<Node> _outNodes = new ArrayList<>();
            List<Double> _outPis = new ArrayList<>();

            for (int i = 0; i < q; i++) {
                final double er = er(pi.get(i), q, variables.size());

                if (er <= getMaxEr()) {
                    _outNodes.add(sortedVariables.get(i));
                    _outPis.add(pi.get(i));
                }
            }

            if (_outNodes.size() > outNodes.size()) {
                outNodes = _outNodes;
                outPis = _outPis;
                bestQ = q;
            }
        }

        System.out.println("q = " + bestQ);

        List<Node> ancestors = new ArrayList<>();

        trueDag = GraphUtils.replaceNodes(trueDag, outNodes);
        trueDag = GraphUtils.replaceNodes(trueDag, Collections.singletonList(target));

        if (trueDag != null) {
            for (Node node : trueDag.getNodes()) {
                if (trueDag.isAncestorOf(node, target)) {
                    ancestors.add(node);
                }
            }
        }

        List<Record> records = new ArrayList<>();

        for (int i = 0; i < outNodes.size(); i++) {


            final double er = er(outPis.get(i), bestQ, variables.size());
            final double pcer = pcer(outPis.get(i), bestQ, variables.size());

            List<Double> e = new ArrayList<>();

            for (int b = 0; b < getNumSubsamples(); b++) {
                final Node node = outNodes.get(i);
                final double m = minimalEffects.get(b).get(node);
                e.add(m);
            }

            double[] _e = new double[e.size()];
            for (int t = 0; t < e.size(); t++) _e[t] = e.get(t);
            double avg = StatUtils.mean(_e);
            final boolean ancestor = ancestors.contains(outNodes.get(i));
            boolean trekToTarget = false;

            if (trueDag != null) {
                List<List<Node>> treks = GraphUtils.treks(trueDag, outNodes.get(i), target, maxTrekLength);
                trekToTarget = !treks.isEmpty();
            }

            records.add(new Record(outNodes.get(i), outPis.get(i), avg, pcer, er, ancestor, trekToTarget));
        }

        records.sort((o1, o2) -> {
            if (o1.getPi() == o2.getPi()) {
                return Double.compare(o2.effect, o1.effect);
            } else {
                return 0;
            }
        });

        return records;
    }

    /**
     * Returns a text table from the given records
     */
    public String makeTable(List<Record> records) {
        TextTable table = new TextTable(records.size() + 1, 6);
        NumberFormat nf = new DecimalFormat("0.0000");

        table.setToken(0, 0, "Index");
        table.setToken(0, 1, "Variable");
        table.setToken(0, 2, "PI");
        table.setToken(0, 3, "Average Effect");
        table.setToken(0, 4, "PCER");
        table.setToken(0, 5, "ER");

        int fp = 0;

        for (int i = 0; i < records.size(); i++) {
            table.setToken(i + 1, 0, "" + (i + 1));
            final boolean possibleAncestor = records.get(i).isAncestor();
            final boolean existsTrekToTarget = records.get(i).isExistsTrekToTarget();
            if (!(existsTrekToTarget)) fp++;
            table.setToken(i + 1, 1, (existsTrekToTarget ? "T " : "") + (possibleAncestor ? "A " : "")
                    + records.get(i).getVariable().getName());
            table.setToken(i + 1, 2, nf.format(records.get(i).getPi()));
            table.setToken(i + 1, 3, nf.format(records.get(i).getEffect()));
            table.setToken(i + 1, 4, nf.format(records.get(i).getPcer()));
            table.setToken(i + 1, 5, nf.format(records.get(i).getEr()));
        }

        return "\n" + table + "\n" + "# FP = " + fp +
                "\n\nT = exists a trak of length no more than " + maxTrekLength + " to the target" +
                "\nA = ancestor of the target\n";
    }

    /**
     * Makes a graph of the estimated predictors to the target.
     */
    public Graph makeGraph(Node y, List<Record> records) {
        List<Node> outNodes = new ArrayList<>();
        for (Record record : records) outNodes.add(record.getVariable());

        Graph graph = new EdgeListGraph(outNodes);
        graph.addNode(y);

        for (int i = 0; i < new ArrayList<>(outNodes).size(); i++) {
            graph.addDirectedEdge(outNodes.get(i), y);
        }

        return graph;
    }

    public int getNumSubsamples() {
        return numSubsamples;
    }

    public void setNumSubsamples(int numSubsamples) {
        this.numSubsamples = numSubsamples;
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

    private Graph getPattern(DataSet sample) {
        IndependenceTest test = getIndependenceTest(sample);

        PcAll pc = new PcAll(test, null);
        pc.setFasRule(PcAll.FasRule.FAS_STABLE);
        pc.setConflictRule(PcAll.ConflictRule.OVERWRITE);
        pc.setColliderDiscovery(PcAll.ColliderDiscovery.FAS_SEPSETS);
        return pc.search();
    }

    private IndependenceTest getIndependenceTest(DataSet sample) {
        if (this.test instanceof IndTestScore && ((IndTestScore) this.test).getWrappedScore() instanceof SemBicScore) {
            SemBicScore score = new SemBicScore(new CorrelationMatrixOnTheFly(sample));
            score.setPenaltyDiscount(((SemBicScore) ((IndTestScore) this.test).getWrappedScore()).getPenaltyDiscount());
            return new IndTestScore(score);
        } else if (this.test instanceof IndTestFisherZ) {
            double alpha = this.test.getAlpha();
            return new IndTestFisherZ(new CorrelationMatrixOnTheFly(sample), alpha);
        } else if (this.test instanceof ChiSquare) {
            double alpha = this.test.getAlpha();
            return new IndTestFisherZ(sample, alpha);
        } else if (this.test instanceof IndTestScore && ((IndTestScore) this.test).getWrappedScore() instanceof ConditionalGaussianScore) {
            ConditionalGaussianScore score = (ConditionalGaussianScore) ((IndTestScore) this.test).getWrappedScore();
            double penaltyDiscount = score.getPenaltyDiscount();
            ConditionalGaussianScore _score = new ConditionalGaussianScore(sample, 1, false);
            _score.setPenaltyDiscount(penaltyDiscount);
            return new IndTestScore(_score);
        } else {
            throw new IllegalArgumentException("That test is not configured.");
        }
    }

    // E(V) bound
    private static double er(double pi, double q, double p) {
        double v = ((q * q) / (4 * p)) * (1.0 / (2 * pi - 1));
        if (v < 0) return Double.POSITIVE_INFINITY;
        return v;
    }

    // Per comparison error rate.
    private static double pcer(double pi, double q, double p) {
        final double v = ((q * q) / (4 * p * p)) * (1.0 / (2 * pi - 1));
        if (v < 0) return Double.POSITIVE_INFINITY;
        return v;
    }


    private DataSet selectVariables(DataSet fullData, List<Node> possiblePredictors, Node y, int parallelism) {
        IndependenceTest test = getIndependenceTest(fullData);

        List<Node> selection = new ArrayList<>();

        final List<Node> variables = possiblePredictors;

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

                ConcurrencyUtils.runCallables(tasks, parallelism);
            }
        }

        if (!selection.contains(y)) selection.add(y);

        final DataSet dataSet = fullData.subsetColumns(selection);

        System.out.println("# selected variables = " + dataSet.getVariables().size());

        return dataSet;
    }
}