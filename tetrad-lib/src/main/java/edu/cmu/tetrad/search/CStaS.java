package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
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
import java.util.concurrent.CopyOnWriteArrayList;

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

    private int maxTrekLength = 15;
    private int numSubsamples = 30;
    private int parallelism = Runtime.getRuntime().availableProcessors() * 10;
    private Graph trueDag = null;
    private IndependenceTest test;

    // A single record in the returned table.
    public static class Record implements TetradSerializable {
        static final long serialVersionUID = 23L;

        private Node predictor;
        private double pi;
        private double effect;
        private double pcer;
        private double er;
        private boolean ancestor;
        private boolean existsTrekToTarget;
        private double MBEv;

        Record(Node predictor, double pi, double minEffect, double pcer, double ev, double MBEv, boolean ancestor, boolean existsTrekToTarget) {
            this.predictor = predictor;
            this.pi = pi;
            this.effect = minEffect;
            this.pcer = pcer;
            this.er = ev;
            this.MBEv = MBEv;
            this.ancestor = ancestor;
            this.existsTrekToTarget = existsTrekToTarget;
        }

        public Node getPredictor() {
            return predictor;
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

        public double getEv() {
            return er;
        }

        public boolean isAncestor() {
            return ancestor;
        }

        public boolean isExistsTrekToTarget() {
            return existsTrekToTarget;
        }

        public double getMBEv() {
            return MBEv;
        }
    }

    public CStaS() {
    }

    public List<Record> getRecords(DataSet dataSet, Node target, IndependenceTest test, double selectionAlpha) {
        this.test = test;

        List<Node> selectionVars = selectVariables(dataSet, target, selectionAlpha, 40);
        selectionVars = GraphUtils.replaceNodes(selectionVars, dataSet.getVariables());
        List<Node> augmented = new ArrayList<>(selectionVars);

        if (!augmented.contains(target)) augmented.add(target);

        augmented = GraphUtils.replaceNodes(augmented, dataSet.getVariables());
        DataSet selection = dataSet.subsetColumns(augmented);

        return getRecords(selection, selectionVars, target, test);
    }

    /**
     * Returns records for a set of variables with expected number of false positives bounded by q.
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

        Node _target = dataSet.getVariable(target.getName());
        possiblePredictors = GraphUtils.replaceNodes(possiblePredictors, dataSet.getVariables());

        List<Node> augmented = new ArrayList<>();
        augmented.add(_target);
        augmented.addAll(possiblePredictors);

        DataSet selection = dataSet.subsetColumns(augmented);

        final List<Node> variables = selection.getVariables();
        variables.remove(_target);

        final Map<Integer, Map<Node, Double>> minimalEffects = new ConcurrentHashMap<>();

        for (int b = 0; b < getNumSubsamples(); b++) {
            final Map<Node, Double> map = new ConcurrentHashMap<>();
            for (Node node : variables) map.put(node, 0.0);
            minimalEffects.put(b, map);
        }

        final List<Ida.NodeEffects> effects = new ArrayList<>();
        final List<Node> _possiblePredictors = new ArrayList<>(possiblePredictors);
        final List<Integer> edgeCounts = new ArrayList<>();

        class Task implements Callable<Boolean> {
            private Task() {
            }

            public Boolean call() {
                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    sampler.setWithoutReplacements(false);
                    DataSet sample = sampler.sample(dataSet, dataSet.getNumRows());
                    Graph pattern = getPatternFges(sample);
                    Ida ida = new Ida(sample, pattern, _possiblePredictors);
                    effects.add(ida.getSortedMinEffects(_target));
                    edgeCounts.add(pattern.getNumEdges());
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

        int totalEdges = 0;

        for (int count : edgeCounts) {
            totalEdges += count;
        }

        double avgEdges = totalEdges / getNumSubsamples();

        System.out.println("Avg edges = " + avgEdges);

        List<Node> outNodes = new ArrayList<>();
        List<Double> outPis = new ArrayList<>();
        int bestQ = -1;

        final Map<Node, Integer> counts = new HashMap<>();
        for (Node node : variables) counts.put(node, 0);

        final int p = dataSet.getNumColumns();

        final double avgDegree = 2.0 * avgEdges / p;
        System.out.println("avg degree = " + avgDegree);

        double bestEv = 0.0;
        double bestMBEv = 0.0;
        double max = 0;

        for (int q = 1; q <= possiblePredictors.size() / 2; q++) {
            for (Ida.NodeEffects _effects : effects) {
                if (q - 1 < possiblePredictors.size()) {
                    final Node key = _effects.getNodes().get(q - 1);
                    counts.put(key, counts.get(key) + 1);
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

            double sum = 0.0;

            for (int g = 0; g < q; g++) {
                sum += pi.get(g);
            }

            if (sum / q >= avgDegree * q / p) {
                if (sum / q > max) {
                    max = sum / q;
                } else {
                    continue;
                }

                List<Node> _outNodes = new ArrayList<>();
                List<Double> _outPis = new ArrayList<>();

                for (int i = 0; i < q; i++) {
                    _outNodes.add(sortedVariables.get(i));
                    _outPis.add(pi.get(i));
                }

                double pi_thr = Double.NaN;

                for (int i = 0; i < q; i++) {
                    if (Double.isNaN(pi_thr) || pi.get(i) < pi_thr) pi_thr = pi.get(i);
                }

                outNodes = _outNodes;
                outPis = _outPis;
                bestQ = q;
                bestEv = q - sum;
                bestMBEv = er(pi_thr, q, p);
            }
        }

        System.out.println("q = " + bestQ);

        trueDag = GraphUtils.replaceNodes(trueDag, outNodes);
        trueDag = GraphUtils.replaceNodes(trueDag, Collections.singletonList(target));

        List<Record> records = new ArrayList<>();

        for (int i = 0; i < outNodes.size(); i++) {
            final double pcer = pcer(outPis.get(i), bestQ, p);

            List<Double> e = new ArrayList<>();

            for (int b = 0; b < getNumSubsamples(); b++) {
                final Node node = outNodes.get(i);
                final double m = minimalEffects.get(b).get(node);
                e.add(m);
            }

            double[] _e = new double[e.size()];
            for (int t = 0; t < e.size(); t++) _e[t] = e.get(t);
            double avg = StatUtils.mean(_e);
            boolean ancestor = false;

            if (trueDag != null) {
                ancestor = trueDag.isAncestorOf(outNodes.get(i), target);
            }

            boolean trekToTarget = false;

            if (trueDag != null) {
//                List<List<Node>> treks = GraphUtils.treks(trueDag, outNodes.get(i), target, maxTrekLength);
//                trekToTarget = !treks.isEmpty();
            }

            records.add(new Record(outNodes.get(i), outPis.get(i), avg, pcer, bestEv, bestMBEv, ancestor, trekToTarget));
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
        TextTable table = new TextTable(records.size() + 1, 8);
        NumberFormat nf = new DecimalFormat("0.0000");

        table.setToken(0, 0, "Index");
        table.setToken(0, 1, "Variable");
        table.setToken(0, 2, "Type");
        table.setToken(0, 3, "A");
        table.setToken(0, 4, "T");
        table.setToken(0, 5, "PI");
        table.setToken(0, 6, "Average Effect");
        table.setToken(0, 7, "PCER");
//        table.setToken(0, 8, "ER");

        int fp = 0;

        for (int i = 0; i < records.size(); i++) {
            final Node node = records.get(i).getPredictor();
            final boolean ancestor = records.get(i).isAncestor();
            final boolean existsTrekToTarget = records.get(i).isExistsTrekToTarget();
            if (!(ancestor)) fp++;

            table.setToken(i + 1, 0, "" + (i + 1));
            table.setToken(i + 1, 1, node.getName());
            table.setToken(i + 1, 2, node instanceof DiscreteVariable ? "D" : "C");
            table.setToken(i + 1, 3, ancestor ? "A" : "");
            table.setToken(i + 1, 4, existsTrekToTarget ? "T" : "");
            table.setToken(i + 1, 5, nf.format(records.get(i).getPi()));
            table.setToken(i + 1, 6, nf.format(records.get(i).getEffect()));
            table.setToken(i + 1, 7, nf.format(records.get(i).getPcer()));
//            table.setToken(i + 1, 8, nf.format(records.get(i).getEv()));
        }
        final double er = !records.isEmpty() ? records.get(0).getEv() : Double.NaN;
        final double mbEv = !records.isEmpty() ? records.get(0).getMBEv() : Double.NaN;

        return "\n" + table + "\n" + "# FP = " + fp + " E(V) = " + nf.format(er) + " MB-E(V) = " + nf.format(mbEv) +
                "\n\nT = exists a trek of length no more than " + maxTrekLength + " to the target" +
                "\nA = ancestor of the target" +
                "\nType: C = continuous, D = discrete\n";
    }

    /**
     * Makes a graph of the estimated predictors to the target.
     */
    public Graph makeGraph(Node y, List<Record> records) {
        List<Node> outNodes = new ArrayList<>();
        for (Record record : records) outNodes.add(record.getPredictor());

        Graph graph = new EdgeListGraph(outNodes);
        graph.addNode(y);

        for (int i = 0; i < new ArrayList<>(outNodes).size(); i++) {
            graph.addDirectedEdge(outNodes.get(i), y);
        }

        return graph;
    }

    public void setNumSubsamples(int numSubsamples) {
        this.numSubsamples = numSubsamples;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public void setTrueDag(Graph trueDag) {
        this.trueDag = trueDag;
    }

    //=============================PRIVATE==============================//

    private int getNumSubsamples() {
        return numSubsamples;
    }

    private int getParallelism() {
        return parallelism;
    }

    private Graph getPattern(DataSet sample) {
        IndependenceTest test = getIndependenceTest(sample, this.test);
        PcAll pc = new PcAll(test, null);
        pc.setFasRule(PcAll.FasRule.FAS_STABLE);
        pc.setConflictRule(PcAll.ConflictRule.OVERWRITE);
        pc.setColliderDiscovery(PcAll.ColliderDiscovery.FAS_SEPSETS);
        return pc.search();
    }

    private Graph getPatternFges(DataSet sample) {
        Score score = new ScoredIndTest(getIndependenceTest(sample, this.test));
        Fges fges = new Fges(score);
        return fges.search();
    }

    private Graph getPatternFgesMb(DataSet sample, Node target) {
        Score score = new ScoredIndTest(getIndependenceTest(sample, this.test));
        FgesMb fges = new FgesMb(score);
        return fges.search(target);
    }

    private IndependenceTest getIndependenceTest(DataSet sample, IndependenceTest test) {
        if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof SemBicScore) {
            SemBicScore score = new SemBicScore(new CorrelationMatrixOnTheFly(sample));
            score.setPenaltyDiscount(((SemBicScore) ((IndTestScore) test).getWrappedScore()).getPenaltyDiscount());
            return new IndTestScore(score);
        } else if (test instanceof IndTestFisherZ) {
            double alpha = test.getAlpha();
            return new IndTestFisherZ(new CorrelationMatrixOnTheFly(sample), alpha);
        } else if (test instanceof ChiSquare) {
            double alpha = test.getAlpha();
            return new IndTestFisherZ(sample, alpha);
        } else if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof ConditionalGaussianScore) {
            ConditionalGaussianScore score = (ConditionalGaussianScore) ((IndTestScore) test).getWrappedScore();
            double penaltyDiscount = score.getPenaltyDiscount();
            ConditionalGaussianScore _score = new ConditionalGaussianScore(sample, 1, false);
            _score.setPenaltyDiscount(penaltyDiscount);
            return new IndTestScore(_score);
        } else {
            throw new IllegalArgumentException("That test is not configured: " + test);
        }
    }

    // E(|V|) bound
    private static double er(double pi, double q, double p) {
        double v = q * q / (p * (2 * pi - 1));
        if (v < 0 || v > q) v = q;
        return v;
    }

    // Per comparison error rate.
    private static double pcer(double pi, double q, double p) {
        double v = (q * q) / (p * p * (2 * pi - 1));
        if (v < 0 || v > 1) v = 1;
        return v;
    }

    public static List<Node> selectVariables(DataSet dataSet, Node y, double alpha, int parallelism) {
        IndependenceTest test = new IndTestFisherZ(dataSet, alpha);
        List<Node> selection = new CopyOnWriteArrayList<>();

        final List<Node> variables = test.getVariables();

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
        }

        System.out.println("# selected variables = " + selection.size());

        return selection;
    }
}