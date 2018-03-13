package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.*;
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
    public enum PatternAlgorithm {FGES, PC_STABLE}

    public enum SampleStyle {BOOTSTRAP, SPLIT}

    private int numSubsamples = 30;
    private int qFrom = 1;
    private int qTo = 1;
    private int qIncrement = 1;

    private int parallelism = Runtime.getRuntime().availableProcessors() * 10;
    private Graph trueDag = null;
    private IndependenceTest test;

    private PatternAlgorithm patternAlgorithm = PatternAlgorithm.FGES;
    private SampleStyle sampleStyle = SampleStyle.BOOTSTRAP;

    private boolean verbose = false;


    private class Tuple {
        private Node predictor;
        private Node target;
        private double pi;
        private double minBeta;

        private Tuple(Node predictor, Node target, double pi, double minBeta) {
            this.predictor = predictor;
            this.target = target;
            this.pi = pi;
            this.minBeta = minBeta;
        }

        public Node getCauseNode() {
            return predictor;
        }

        public Node getEffectNode() {
            return target;
        }

        public double getPi() {
            return pi;
        }

        public double getMinBeta() {
            return minBeta;
        }
    }

    // A single record in the returned table.
    public static class Record implements TetradSerializable {
        static final long serialVersionUID = 23L;

        private Node causeNode;
        private Node target;
        private double pi;
        private double effect;
        private boolean ancestor;
        private int q;
        private int p;

        Record(Node predictor, Node target, double pi, double minEffect, boolean ancestor, int q, int p) {
            this.causeNode = predictor;
            this.target = target;
            this.pi = pi;
            this.effect = minEffect;
            this.ancestor = ancestor;
            this.q = q;
            this.p = p;
        }

        public Node getCauseNode() {
            return causeNode;
        }

        public Node getEffectNode() {
            return target;
        }

        public double getPi() {
            return pi;
        }

        public double getMinBeta() {
            return effect;
        }

        public boolean isAncestor() {
            return ancestor;
        }

        public int getQ() {
            return q;
        }

        public int getP() {
            return p;
        }
    }

    public CStaS() {
    }

    /**
     * Returns records for a set of variables with expected number of false positives bounded by q.
     *
     * @param dataSet         The full datasets to search over.
     * @param possibleCauses  A set of variables in the datasets over which to search.
     * @param possibleEffects The target variables.
     * @param test            This test is only used to make more tests like it for subsamples.
     */
    public LinkedList<LinkedList<Record>> getRecords(DataSet dataSet, List<Node> possibleCauses, List<Node> possibleEffects,
                                                     IndependenceTest test) {
        possibleEffects = GraphUtils.replaceNodes(possibleEffects, dataSet.getVariables());
        possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());
        LinkedList<LinkedList<Record>> allRecords = new LinkedList<>();

        if (new HashSet<>(possibleCauses).removeAll(new HashSet<>(possibleEffects))) {
            throw new IllegalArgumentException("Possible predictors and possibleEffects must be disjoint sets.");
        }

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

        List<Node> augmented = new ArrayList<>(possibleEffects);
        augmented.addAll(possibleCauses);


        final List<Map<Integer, Map<Node, Double>>> minimalEffects = new ArrayList<>();

        for (int t = 0; t < possibleEffects.size(); t++) {
            minimalEffects.add(new ConcurrentHashMap<>());

            for (int b = 0; b < getNumSubsamples(); b++) {
                final Map<Node, Double> map = new ConcurrentHashMap<>();
                for (Node node : possibleCauses) map.put(node, 0.0);
                minimalEffects.get(t).put(b, map);
            }
        }

        final Map<Integer, List<Ida.NodeEffects>> effects = new ConcurrentHashMap<>();

        // Need final for inner class.
        final List<Node> _effects = new ArrayList<>(possibleEffects);

        for (int t = 0; t < possibleEffects.size(); t++) {
            effects.put(t, new ArrayList<>());
        }

        final List<Node> _possiblePredictors = new ArrayList<>(possibleCauses);

        class Task implements Callable<Boolean> {
            int b;

            private Task(int b) {
                this.b = b;
            }

            public Boolean call() {
                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    DataSet sample;

                    if (sampleStyle == SampleStyle.BOOTSTRAP) {
                        sampler.setWithoutReplacements(false);
                        sample = sampler.sample(dataSet, dataSet.getNumRows());
                    } else if (sampleStyle == SampleStyle.SPLIT) {
                        sampler.setWithoutReplacements(true);
                        sample = sampler.sample(dataSet, dataSet.getNumRows() / 2);
                    } else {
                        throw new IllegalArgumentException("That type of sample is not configured: " + sampleStyle);
                    }

                    Graph pattern;

                    if (patternAlgorithm == PatternAlgorithm.FGES) {
                        pattern = getPatternFges(sample);
                    } else if (patternAlgorithm == PatternAlgorithm.PC_STABLE) {
                        pattern = getPatternPcStable(sample);
                    } else {
                        throw new IllegalArgumentException("That type of of pattern algorithm is not configured: "
                                + patternAlgorithm);
                    }

                    Ida ida = new Ida(sample, pattern, _possiblePredictors);

                    for (int t = 0; t < _effects.size(); t++) {
                        effects.get(t).add(ida.getSortedMinEffects(_effects.get(t)));
                    }

                    if (verbose) {
                        System.out.println("Bootstrap " + (b + 1));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int b = 0; b < getNumSubsamples(); b++) {
            tasks.add(new Task(b));
        }

        ConcurrencyUtils.runCallables(tasks, getParallelism());

        int p = dataSet.getNumColumns();

        List<Integer> qs = new ArrayList<>();

        for (int q = qFrom; q <= qTo; q += qIncrement) {
            qs.add(q);
        }

        class Task2 implements Callable<Boolean> {
            private List<Node> possibleCauses;
            private List<Node> possibleEffects;
            private int q;

            private Task2(List<Node> possibleCauses, List<Node> possibleEffects, int q) {
                this.possibleCauses = possibleCauses;
                this.possibleEffects = possibleEffects;
                this.q = q;

            }

            public Boolean call() {
                try {
                    LinkedList<Record> records = getRecords(possibleCauses, possibleEffects, effects, p, q, dataSet);
                    allRecords.add(records);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return null;
            }
        }

        List<Callable<Boolean>> task2s = new ArrayList<>();

        for (int q : qs) {
            task2s.add(new Task2(possibleCauses, possibleEffects, q));
        }

        ConcurrencyUtils.runCallables(task2s, getParallelism());

        allRecords.sort(Comparator.comparingDouble(List::size));

        return allRecords;
    }

    private LinkedList<Record> getRecords(List<Node> possibleCauses, List<Node> possibleEffects, Map<Integer,
            List<Ida.NodeEffects>> effects, int p, int q, DataSet data) {
        if (verbose) {
            System.out.println("Examining q = " + q);
        }

        final List<Map<Node, Integer>> counts = new ArrayList<>();

        for (int t = 0; t < possibleEffects.size(); t++) {
            counts.add(new HashMap<>());
            for (Node node : possibleCauses) counts.get(t).put(node, 0);
        }

        for (int w = 1; w <= q; w++) {
            for (int t = 0; t < possibleEffects.size(); t++) {
                final Map<Node, Integer> _counts = counts.get(t);

                for (Ida.NodeEffects effectSize : effects.get(t)) {
                    final List<Node> nodes = effectSize.getNodes();

                    if (w - 1 < possibleCauses.size()) {
                        final Node key = nodes.get(w - 1);
                        _counts.put(key, _counts.get(key) + 1);
                    }
                }
            }
        }

        List<Tuple> tuples = new ArrayList<>();


        List<List<Map<Node, Integer>>> effectNodeHashMap = new ArrayList<>();

        for (int t = 0; t < possibleEffects.size(); t++) {
            effectNodeHashMap.add(new ArrayList<>());

            for (int b = 0; b < effects.get(t).size(); b++) {
                effectNodeHashMap.get(t).add(new HashMap<>());

                Ida.NodeEffects effectSizes = effects.get(t).get(b);

                for (int i = 0; i < effectSizes.getNodes().size(); i++) {
                    effectNodeHashMap.get(t).get(b).put(effectSizes.getNodes().get(i), i);
                }
            }
        }

        for (Node v : possibleCauses) {
            for (int t = 0; t < possibleEffects.size(); t++) {
                final Integer count = counts.get(t).get(v);
                final double pi = count / ((double) getNumSubsamples());
                tuples.add(new Tuple(v, possibleEffects.get(t), pi,
                        avgMinEffect(possibleEffects, effects, v, possibleEffects.get(t), effectNodeHashMap)));
            }
        }

        tuples.sort((o1, o2) -> {
            if (o1.getPi() == o2.getPi()) {
                return Double.compare(o2.getMinBeta(), o1.getMinBeta());
            } else {
                return Double.compare(o2.getPi(), o1.getPi());
            }
        });

        double pi_thr = Double.NaN;

        for (int g = 0; g < Math.min(q, tuples.size()); g++) {
            if (Double.isNaN(pi_thr) || tuples.get(g).getPi() < pi_thr) pi_thr = tuples.get(g).getPi();
        }

        List<Tuple> _outTuples = new ArrayList<>();

        for (int i = 0; i < Math.min(q, tuples.size()); i++) {
            _outTuples.add(tuples.get(i));
        }

        trueDag = GraphUtils.replaceNodes(trueDag, possibleCauses);
        trueDag = GraphUtils.replaceNodes(trueDag, possibleEffects);

        LinkedList<Record> records = new LinkedList<>();

        for (Tuple tuple : _outTuples) {
            double avg = tuple.getMinBeta();

            boolean ancestor = false;

            if (trueDag != null && tuple.getCauseNode() != null && tuple.getEffectNode() != null) {
                ancestor = trueDag.isAncestorOf(tuple.getCauseNode(), tuple.getEffectNode());
            }

            records.add(new Record(tuple.getCauseNode(), tuple.getEffectNode(), tuple.getPi(), avg, ancestor, q, p));
        }

        return records;
    }

    private double avgMinEffect(List<Node> possibleEffects, Map<Integer, List<Ida.NodeEffects>> effects,
                                Node causeNode, Node effectNode, List<List<Map<Node, Integer>>> effectNodeHashMap) {
        List<Double> e = new ArrayList<>();

        final int t = possibleEffects.indexOf(effectNode);

        for (int b = 0; b < effects.get(t).size(); b++) {
            Ida.NodeEffects effectSizes = effects.get(t).get(b);
            int r = effectNodeHashMap.get(t).get(b).get(causeNode);
            e.add(effectSizes.getEffects().get(r));
        }

        double[] _e = new double[e.size()];
        for (int b = 0; b < e.size(); b++) _e[b] = e.get(b);
        return StatUtils.mean(_e);
    }

    public static LinkedList<Record> cStar(LinkedList<LinkedList<Record>> allRecords) {
        Map<Edge, List<Record>> map = new HashMap<>();

        for (List<Record> records : allRecords) {
            for (Record record : records) {
                Edge edge = Edges.directedEdge(record.getCauseNode(), record.getEffectNode());
                map.computeIfAbsent(edge, k -> new ArrayList<>());
                map.get(edge).add(record);
            }
        }

        LinkedList<Record> cstar = new LinkedList<>();

        for (Edge edge : map.keySet()) {
            List<Record> recordList = map.get(edge);

            double[] pis = new double[recordList.size()];
            double[] effects = new double[recordList.size()];

            for (int i = 0; i < recordList.size(); i++) {
                pis[i] = recordList.get(i).getPi();
                effects[i] = recordList.get(i).getMinBeta();
            }

            double medianPis = StatUtils.median(pis);
            double medianEffects = StatUtils.median(effects);

            Record _record = new Record(edge.getNode1(), edge.getNode2(), medianPis, medianEffects,
                    recordList.get(0).isAncestor(), -1, -1);
            cstar.add(_record);
        }

        cstar.sort((o1, o2) -> {
            if (o1.getPi() == o2.getPi()) {
                return Double.compare(o2.getMinBeta(), o1.getMinBeta());
            } else {
                return Double.compare(o2.getPi(), o1.getPi());
            }
        });

        return cstar;
    }

    /**
     * Returns a text table from the given records
     */
    public String makeTable(LinkedList<Record> records) {
        TextTable table = new TextTable(records.size() + 1, 8);
        NumberFormat nf = new DecimalFormat("0.0000");

        table.setToken(0, 0, "Index");
        table.setToken(0, 1, "Predictor");
        table.setToken(0, 2, "Target");
        table.setToken(0, 3, "Type");
        table.setToken(0, 4, "A");
        table.setToken(0, 6, "PI");
        table.setToken(0, 7, "Average Effect");

        int fp = 0;

        for (int i = 0; i < records.size(); i++) {
            final Node predictor = records.get(i).getCauseNode();
            final Node target = records.get(i).getEffectNode();
            final boolean ancestor = records.get(i).isAncestor();
            if (!(ancestor)) fp++;

            table.setToken(i + 1, 0, "" + (i + 1));
            table.setToken(i + 1, 1, predictor.getName());
            table.setToken(i + 1, 2, target.getName());
            table.setToken(i + 1, 3, predictor instanceof DiscreteVariable ? "D" : "C");
            table.setToken(i + 1, 4, ancestor ? "A" : "");
            table.setToken(i + 1, 6, nf.format(records.get(i).getPi()));
            table.setToken(i + 1, 7, nf.format(records.get(i).getMinBeta()));
        }

        int p = records.getLast().getP();
        int q = records.getLast().getQ();

        double sum = 0.0;

        for (Record record : records) {
            final double pi2 = record.getPi() - q / (double) p;
            if (pi2 < 0) continue;
            sum += pi2;
        }

        final double pcer = !records.isEmpty() ? pcer(records.getLast().getPi(), q, p) : Double.NaN;
        final double jrev = !records.isEmpty() ? q - sum : Double.NaN;
        final double mbEv = !records.isEmpty() ? er(records.getLast().getPi(), q, p) : Double.NaN;

        final String fpString = " # FP = " + fp + (records.getLast().getQ() != -1 && sampleStyle == SampleStyle.SPLIT ? " PCER = " + nf.format(pcer) + "" : "");

        return "\n" + table + "\n"
                + "p = " + p + " q = " + q
                + (fp != records.size() ? fpString : "")
                + (records.getLast().getQ() != -1 ? " JR E(V) = " + nf.format(jrev) : "")
                + (records.getLast().getQ() != -1 && sampleStyle == SampleStyle.SPLIT ? " MB E(V) bound = " + nf.format(mbEv) : "")
                + "\nA = ancestor of the target" + "\nType: C = continuous, D = discrete\n";
    }

    /**
     * Makes a graph of the estimated predictors to the target.
     */
    public Graph makeGraph(List<Record> records) {
        List<Node> outNodes = new ArrayList<>();

        Graph graph = new EdgeListGraph(outNodes);

        for (Record record : records) {
            graph.addNode(record.getCauseNode());
            graph.addNode(record.getEffectNode());
            graph.addDirectedEdge(record.getCauseNode(), record.getEffectNode());
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

    public void setqFrom(int qFrom) {
        this.qFrom = qFrom;
    }

    public void setqTo(int qTo) {
        this.qTo = qTo;
    }

    public void setqIncrement(int qIncrement) {
        this.qIncrement = qIncrement;
    }

    public void setPatternAlgorithm(PatternAlgorithm patternAlgorithm) {
        this.patternAlgorithm = patternAlgorithm;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setSampleStyle(SampleStyle sampleStyle) {
        this.sampleStyle = sampleStyle;
    }

    //=============================PRIVATE==============================//

    private int getNumSubsamples() {
        return numSubsamples;
    }

    private int getParallelism() {
        return parallelism;
    }

    private Graph getPatternPcStable(DataSet sample) {
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
        fges.setParallelism(1);
        return fges.search();
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
}