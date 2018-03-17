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
        private Node cause;
        private Node effect;
        private double pi;
        private double minBeta;

        private Tuple(Node cause, Node effect, double pi, double minBeta) {
            this.cause = cause;
            this.effect = effect;
            this.pi = pi;
            this.minBeta = minBeta;
        }

        public Node getCauseNode() {
            return cause;
        }

        public Node getEffectNode() {
            return effect;
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
     * @param possibleEffects The effect variables.
     * @param test            This test is only used to make more tests like it for subsamples.
     */
    public LinkedList<LinkedList<Record>> getRecords(DataSet dataSet, List<Node> possibleCauses, List<Node> possibleEffects,
                                                     IndependenceTest test) {
        possibleEffects = GraphUtils.replaceNodes(possibleEffects, dataSet.getVariables());
        possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());
        LinkedList<LinkedList<Record>> allRecords = new LinkedList<>();

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

        // Need final for inner class.
        final List<double[][]> allEffects = new ArrayList<>();

        class Task implements Callable<Boolean> {
            private final List<Node> possibleCauses;
            private final List<Node> possibleEffects;
            private final List<double[][]> allEffects;
            private final int b;

            private Task(int b, List<Node> possibleCauses, List<Node> possibleEffects, List<double[][]> allEffects) {
                this.b = b;
                this.possibleCauses = possibleCauses;
                this.possibleEffects = possibleEffects;
                this.allEffects = allEffects;
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

                    Ida ida = new Ida(sample, pattern, possibleCauses);

                    double[][] effects = new double[possibleCauses.size()][possibleEffects.size()];

                    for (int e = 0; e < possibleEffects.size(); e++) {
                        Map<Node, Double> minEffects = ida.calculateMinimumEffectsOnY(possibleEffects.get(e));

                        for (int c = 0; c < possibleCauses.size(); c++) {
                            effects[c][e] = minEffects.get(possibleCauses.get(c));
                        }
                    }

                    allEffects.add(effects);

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
            tasks.add(new Task(b, possibleCauses, possibleEffects, allEffects));
        }

        ConcurrencyUtils.runCallables(tasks, getParallelism());

        int p = possibleCauses.size() * possibleEffects.size();

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
                    if (verbose) {
                        System.out.println("Examining q = " + q);
                    }

                    double[] cutoffs = new double[getNumSubsamples()];

                    for (int k = 0; k < getNumSubsamples(); k++) {
                        double[][] effects = allEffects.get(k);

                        List<Double> doubles = new ArrayList<>();

                        for (int c = 0; c < possibleCauses.size(); c++) {
                            for (int e = 0; e < possibleEffects.size(); e++) {
                                doubles.add(effects[c][e]);
                            }
                        }

                        doubles.sort((o1, o2) -> Double.compare(o2, o1));
                        cutoffs[k] = doubles.get(Math.min(q - 1, doubles.size() - 1));
                    }

                    List<List<Map<Node, Integer>>> effectNodeHashMap = new ArrayList<>();

                    List<Tuple> tuples = new ArrayList<>();

                    for (int e = 0; e < possibleEffects.size(); e++) {
                        for (int c = 0; c < possibleCauses.size(); c++) {
                            int count = 0;

                            for (int k = 0; k < getNumSubsamples(); k++) {
                                if (allEffects.get(k)[c][e] >= cutoffs[k]) {
                                    count++;
                                }
                            }

                            if (count > 0) {
                                final double pi = count / ((double) getNumSubsamples());
                                final Node cause = possibleCauses.get(c);
                                final Node effect = possibleEffects.get(e);
                                tuples.add(new Tuple(cause, effect, pi,
                                        avgMinEffect(possibleCauses, possibleEffects, allEffects, cause, effect, effectNodeHashMap)));
                            }
                        }

                    }

                    tuples.sort((o1, o2) -> {
                        if (o1.getPi() == o2.getPi()) {
                            return Double.compare(o2.getMinBeta(), o1.getMinBeta());
                        } else {
                            return Double.compare(o2.getPi(), o1.getPi());
                        }
                    });

                    LinkedList<Record> records = new LinkedList<>();

                    for (int i = 0; i < Math.min(q, tuples.size()); i++) {
                        Tuple tuple = tuples.get(i);
                        double avg = tuple.getMinBeta();

                        boolean ancestor = false;

                        final Node causeNode = tuple.getCauseNode();
                        final Node effectNode = tuple.getEffectNode();

                        if (trueDag != null) {
                            ancestor = trueDag.isAncestorOf(trueDag.getNode(causeNode.getName()),
                                    trueDag.getNode(effectNode.getName()));
                        }

                        records.add(new Record(causeNode, effectNode, tuple.getPi(), avg, ancestor, q, p));
                    }

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

    class Pair {
        private final Node cause;
        private final Node effect;

        public Pair(Node cause, Node effect) {
            this.cause = cause;
            this.effect = effect;
        }

        public Node getCause() {
            return cause;
        }

        public Node getEffect() {
            return effect;
        }

        public int hashCode() {
            return 5 * cause.hashCode() + 7 * effect.hashCode();
        }

        public boolean equals(Object o) {
            if (!(o instanceof Pair)) throw new IllegalArgumentException("Not a pair.");
            Pair pair = (Pair) o;
            return pair.cause == cause && pair.effect == effect;
        }
    }

    class Pair2 {
        private final Pair pair;
        private final double beta;

        public Pair2(Pair pair, double beta) {
            if (pair == null) throw new NullPointerException("Null pair");
            this.pair = pair;
            this.beta = beta;
        }

        public int hashCode() {
            return 5 * pair.hashCode();
        }

        public boolean equals(Object o) {
            if (!(o instanceof Pair2)) throw new IllegalArgumentException("Not a pair2.");
            Pair2 pair = (Pair2) o;
            return pair.pair == this.pair;
        }

        public Pair getPair() {
            return pair;
        }

        public double getBeta() {
            return beta;
        }
    }

    private double avgMinEffect(List<Node> possibleCauses, List<Node> possibleEffects, List<double[][]> allEffects,
                                Node causeNode, Node effectNode, List<List<Map<Node, Integer>>> effectNodeHashMap) {
        List<Double> f = new ArrayList<>();

        if (allEffects == null) {
            throw new NullPointerException("effects null");
        }

        for (int k = 0; k < getNumSubsamples(); k++) {
            int c = possibleCauses.indexOf(causeNode);
            int e = possibleEffects.indexOf(effectNode);
            f.add(allEffects.get(k)[c][e]);
        }

        double[] _f = new double[f.size()];
        for (int b = 0; b < f.size(); b++) _f[b] = f.get(b);
        return StatUtils.mean(_f);
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
    public String makeTable(LinkedList<Record> records, boolean printTable) {
        TextTable table = new TextTable(records.size() + 1, 8);
        NumberFormat nf = new DecimalFormat("0.0000");

        table.setToken(0, 0, "Index");
        table.setToken(0, 1, "Cause");
        table.setToken(0, 2, "Effect");
        table.setToken(0, 3, "Type");
        table.setToken(0, 4, "A");
        table.setToken(0, 6, "PI");
        table.setToken(0, 7, "Effect");
//        table.setToken(0, 8, "PCER");
//        table.setToken(0, 9, "E(V)");

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
//            table.setToken(i + 1, 8, nf.format(pcer(records.get(i).getPi(), i + 1, records.get(i).getP())));
//            table.setToken(i + 1, 9, nf.format(er(records.get(i).getPi(), i + 1, records.get(i).getP())));
        }

        int p = records.getLast().getP();
        int q = records.getLast().getQ();

        double sum = 0.0;

        for (Record record : records) {
            final double pi2 = record.getPi() - q / (double) p;
//            if (pi2 < 0) continue;
            sum += pi2;
        }

        sum /= q;

        final double pcer = !records.isEmpty() ? pcer(records.getLast().getPi(), q, p) : Double.NaN;
//        final double jrev = !records.isEmpty() ? q - sum : Double.NaN;
        final double jretp = !records.isEmpty() ? sum : Double.NaN;
        final double mbEv = !records.isEmpty() ? er(records.getLast().getPi(), q, p) : Double.NaN;

        final String fpString = " # TP = " + (q - fp) + " # FP = " + fp + (records.getLast().getQ() != -1 && sampleStyle == SampleStyle.SPLIT ? " PCER = " + nf.format(pcer) + "" : "");

        return printTable ? "\n" + table : "" + "\n"
                + "p = " + p + " q = " + q
                + (fp != records.size() ? fpString : "")
                + (records.getLast().getQ() != -1 ? " SUM(PI - q / p) / q = " + nf.format(jretp) : "")
                + (records.getLast().getQ() != -1 && sampleStyle == SampleStyle.SPLIT ? " MB E(V) bound = " + nf.format(mbEv) : "")
                + "\nA = ancestor of the effect" + "\nType: C = continuous, D = discrete\n";
    }

    /**
     * Makes a graph of the estimated predictors to the effect.
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