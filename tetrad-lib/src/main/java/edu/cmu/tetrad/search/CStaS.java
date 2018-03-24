package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;

import java.io.*;
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
        private double avgAvgDegree;

        Record(Node predictor, Node target, double pi, double minEffect, boolean ancestor, int q, int p, double avgAvgDegree) {
            this.causeNode = predictor;
            this.target = target;
            this.pi = pi;
            this.effect = minEffect;
            this.ancestor = ancestor;
            this.q = q;
            this.p = p;
            this.avgAvgDegree = avgAvgDegree;
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

        double getMinBeta() {
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

        public double getAvgAvgDegree() {
            return avgAvgDegree;
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
        return getRecords(dataSet, possibleCauses, possibleEffects, test, null);
    }


    /**
     * Returns records for a set of variables with expected number of false positives bounded by q.
     *
     * @param dataSet         The full datasets to search over.
     * @param possibleCauses  A set of variables in the datasets over which to search.
     * @param possibleEffects The effect variables.
     * @param test            This test is only used to make more tests like it for subsamples.
     * @param path            A path where interim results are to be stored. If null, interim results will not be stored.
     *                        If the path is specified, then if the process is stopped and restarted, previously
     *                        computed interim results will be loaded.
     */
    public LinkedList<LinkedList<Record>> getRecords(DataSet dataSet, List<Node> possibleCauses, List<Node> possibleEffects,
                                                     IndependenceTest test, String path) {
        possibleEffects = GraphUtils.replaceNodes(possibleEffects, dataSet.getVariables());
        possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());

        int p = possibleCauses.size() * possibleEffects.size();

        List<Integer> qs = new ArrayList<>();

        for (int q = qFrom; q <= qTo; q += qIncrement) {
            if (q <= p) {
                qs.add(q);
            } else {
                System.out.println("q = " + q + " > p = " + p + "; skipping");
            }
        }

        if (qs.isEmpty()) return new LinkedList<>();

        LinkedList<LinkedList<Record>> allRecords = new LinkedList<>();

        File _dir = null;

        if (path != null) {
            _dir = new File(path);
            _dir.mkdirs();
        }

        final File dir = _dir;

        if (dir != null) {

            if (new File(dir, "possible.causes.txt").exists() && new File(dir, "possible.causes.txt").exists()) {
                possibleCauses = readVars(dataSet, dir, "possible.causes.txt");
                possibleEffects = readVars(dataSet, dir, "possible.effects.txt");
                dataSet = readData(dir, "data.txt");

                if (new File(dir, "trueDag.txt").exists()) {
                    trueDag = GraphUtils.loadGraphTxt(new File(dir, "trueDag.txt"));
                }
            } else {
                writeVars(possibleCauses, dir, "possible.causes.txt");
                writeVars(possibleEffects, dir, "possible.effects.txt");
                writeData(dataSet, dir, "data.txt");

                if (trueDag != null) {
                    GraphUtils.saveGraph(trueDag, new File(dir, "trueDag.txt"), false);
                }
            }
        }

        final DataSet _dataSet = dataSet;

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
        final double[] totalAvgDegree = new double[1];

        class Task implements Callable<Boolean> {
            private final List<Node> possibleCauses;
            private final List<Node> possibleEffects;
            private final List<double[][]> allEffects;
            private final int k;

            private Task(int k, List<Node> possibleCauses, List<Node> possibleEffects, List<double[][]> allEffects) {
                this.k = k;
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
                        sample = sampler.sample(_dataSet, _dataSet.getNumRows());
                    } else if (sampleStyle == SampleStyle.SPLIT) {
                        sampler.setWithoutReplacements(true);
                        sample = sampler.sample(_dataSet, _dataSet.getNumRows() / 2);
                    } else {
                        throw new IllegalArgumentException("That type of sample is not configured: " + sampleStyle);
                    }

                    Graph pattern = null;
                    double[][] effects = null;

                    if (dir != null && new File(dir, "pattern." + (k + 1) + ".txt").exists()
                            && new File(dir, "effects." + (k + 1) + ".txt").exists()) {
                        try {
                            pattern = GraphUtils.loadGraphTxt(new File(dir, "pattern." + (k + 1) + ".txt"));
                            effects = loadMatrix(new File(dir, "effects." + (k + 1) + ".txt"));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (pattern == null || effects == null) {
                        if (patternAlgorithm == PatternAlgorithm.FGES) {
                            pattern = getPatternFges(sample);
                        } else if (patternAlgorithm == PatternAlgorithm.PC_STABLE) {
                            pattern = getPatternPcStable(sample);
                        } else {
                            throw new IllegalArgumentException("That type of of pattern algorithm is not configured: "
                                    + patternAlgorithm);
                        }

                        double avgDegree = 2 * pattern.getNumEdges() / (double) pattern.getNumNodes();
                        totalAvgDegree[0] += avgDegree;

                        if (dir != null) {
                            GraphUtils.saveGraph(pattern, new File(dir, "pattern." + (k + 1) + ".txt"), false);
                        }

                        Ida ida = new Ida(sample, pattern, possibleCauses);

                        effects = new double[possibleCauses.size()][possibleEffects.size()];

                        for (int e = 0; e < possibleEffects.size(); e++) {
                            Map<Node, Double> minEffects = ida.calculateMinimumEffectsOnY(possibleEffects.get(e));

                            for (int c = 0; c < possibleCauses.size(); c++) {
                                effects[c][e] = minEffects.get(possibleCauses.get(c));
                            }
                        }

                        if (dir != null) {
                            saveMatrix(effects, new File(dir, "effects." + (k + 1) + ".txt"));
                        }
                    }

                    allEffects.add(effects);

                    if (verbose) {
                        System.out.println("Bootstrap " + (k + 1));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int k = 0; k < getNumSubsamples(); k++) {
            tasks.add(new Task(k, possibleCauses, possibleEffects, allEffects));
        }

        ConcurrencyUtils.runCallables(tasks, getParallelism());

        final double avgAvgDegree = totalAvgDegree[0] / getNumSubsamples();

        List<List<Double>> doubles = new ArrayList<>();

        for (int k = 0; k < allEffects.size(); k++) {
            double[][] effects = allEffects.get(k);

            if (effects.length != possibleCauses.size() || effects[0].length != possibleEffects.size()) {
                throw new IllegalStateException("Bootstrap " + (k + 1)
                        + " is damaged; delete the pattern and effect files for that bootstrap and rerun");
            }

            List<Double> _doubles = new ArrayList<>();

            for (int c = 0; c < possibleCauses.size(); c++) {
                for (int e = 0; e < possibleEffects.size(); e++) {
                    _doubles.add(effects[c][e]);
                }
            }

            _doubles.sort((o1, o2) -> Double.compare(o2, o1));
            doubles.add(_doubles);
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

                    List<Tuple> tuples = new ArrayList<>();

                    for (int e = 0; e < possibleEffects.size(); e++) {
                        for (int c = 0; c < possibleCauses.size(); c++) {
                            int count = 0;

                            for (int k = 0; k < getNumSubsamples(); k++) {
                                if (q > doubles.get(k).size()) {
                                    continue;
                                }

                                double cutoff = doubles.get(k).get(q - 1);

                                if (allEffects.get(k)[c][e] >= cutoff) {
                                    count++;
                                }
                            }

                            if (count > 0) {
                                final double pi = count / ((double) getNumSubsamples());
                                final Node cause = possibleCauses.get(c);
                                final Node effect = possibleEffects.get(e);
                                tuples.add(new Tuple(cause, effect, pi,
                                        avgMinEffect(possibleCauses, possibleEffects, allEffects, cause, effect)));
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

                        records.add(new Record(causeNode, effectNode, tuple.getPi(), avg, ancestor, q, p, avgAvgDegree));
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

    private DataSet readData(File dir, String s) {
        try {
            Dataset dataset = new ContinuousTabularDataFileReader(new File(dir, s), Delimiter.TAB).readInData();
            final DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataset);

            System.out.println("Loaded data " + dataSet.getNumRows() + " x " + dataSet.getNumColumns());

            return dataSet;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeData(DataSet dataSet, File dir, String s) {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(new File(dir, s)));
            out.println(dataSet.toString());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Node> readVars(DataSet dataSet, File dir, String s) {
        try {
            List<Node> vars = new ArrayList<>();

            File file = new File(dir, s);

            BufferedReader in = new BufferedReader(new FileReader(file));
            String line;

            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                vars.add(dataSet.getVariable(line.trim()));
            }

            return vars;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeVars(List<Node> vars, File dir, String s) {
        try {
            File file = new File(dir, s);

            PrintStream out = new PrintStream(new FileOutputStream(file));

            for (Node node : vars) {
                out.println(node.getName());
            }

            out.flush();
            ;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private double avgMinEffect(List<Node> possibleCauses, List<Node> possibleEffects, List<double[][]> allEffects,
                                Node causeNode, Node effectNode) {
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
                    recordList.get(0).isAncestor(), -1, -1, Double.NaN);
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
//        LinkedList<Record> _records = new LinkedList<>();
//
//        for (int i = 0; i < records.size(); i++) {
//            if (records.get(i).getPi() > 0.8) _records.add(records.get(i));
//        }
//
//        records = _records;

        TextTable table = new TextTable(records.size() + 1, 17);
        NumberFormat nf = new DecimalFormat("0.0000");
        int column = 0;

        table.setToken(0, column++, "Index");
        table.setToken(0, column++, "Cause");
        table.setToken(0, column++, "Effect");
        table.setToken(0, column++, "Type");
        table.setToken(0, column++, "A");
        table.setToken(0, column++, "PI");
        table.setToken(0, column++, "Effect");
        table.setToken(0, column++, "FP");
        table.setToken(0, column++, "TP");
        table.setToken(0, column++, "SUM(Pi)");
        table.setToken(0, column++, "S");
        table.setToken(0, column++, "E(V)");

        table.setToken(0, column++, "pi1");
        table.setToken(0, column++, "pi2");

        table.setToken(0, column++, "TotalCounts");
        table.setToken(0, column++, "AvgDegree");


        int tp = 0;
        int fp = 0;
        double tpSumPi = 0;
        double fpSumPi = 0;
        int totalCounts = 0;
        double sumPi = 0.0;
        int p = records.getLast().getP();
        int q = records.getLast().getQ();

        for (int i = 0; i < records.size(); i++) {
            final Node predictor = records.get(i).getCauseNode();
            final Node target = records.get(i).getEffectNode();

            if (records.get(i).isAncestor()) {
                tp++;
                tpSumPi += records.get(i).getPi();
            } else {
                fp++;
                fpSumPi += records.get(i).getPi();
            }

            totalCounts += getNumSubsamples() * records.get(i).getPi();

            sumPi += records.get(i).getPi();

            // average pi for the false positives
            double pi1 = fpSumPi / (fp);
            double pi2 = tpSumPi / (tp) ;
            int R = (i + 1);

            double S = R * (pi2 / (1 + pi2 - pi1));


            double factor = 1 / ((double) getNumSubsamples());

            final double avgAvgDegree = records.get(i).getAvgAvgDegree();

            column = 0;

            table.setToken(i + 1, column++, "" + (i + 1));
            table.setToken(i + 1, column++, predictor.getName());
            table.setToken(i + 1, column++, target.getName());
            table.setToken(i + 1, column++, predictor instanceof DiscreteVariable ? "D" : "C");
            table.setToken(i + 1, column++, records.get(i).isAncestor() ? "A" : "");
            table.setToken(i + 1, column++, nf.format(records.get(i).getPi()));
            table.setToken(i + 1, column++, nf.format(records.get(i).getMinBeta()));
            table.setToken(i + 1, column++, nf.format(fp));
            table.setToken(i + 1, column++, nf.format(tp));
            table.setToken(i + 1, column++, nf.format(sumPi));
            table.setToken(i + 1, column++, nf.format(S));
            table.setToken(i + 1, column++, nf.format(er(records.get(i).getPi(), q, p)));

            table.setToken(i + 1, column++, nf.format(pi1));
            table.setToken(i + 1, column++, nf.format(pi2));

            table.setToken(i + 1, column++, nf.format(totalCounts * factor));
            table.setToken(i + 1, column++, nf.format(avgAvgDegree));
        }

        final double pi_thr = records.getLast().getPi();
        final double pcer = !records.isEmpty() ? pcer(pi_thr, q, p) : Double.NaN;
        final double mbEv = !records.isEmpty() ? er(pi_thr, q, p) : Double.NaN;

        final String fpString =
                " TP = " + nf.format(tp / (double) 1) + " FP = " + nf.format((fp) / (double) 1);


        return (printTable ? "\n" + table : "" + "")
                + "p = " + p + " q = " + q
                + (fp != records.size() ? fpString : "")
                + (q != -1 ? " SUM(PI)  = " + nf.format(sumPi) : "")
                + (q != -1 ? " PCER = " + nf.format(pcer) + "" : "")
                + (q != -1 ? " MB E(V) = " + nf.format(mbEv) : "")
                + (printTable ? "\nA = ancestor of the effect" + "\nType: C = continuous, D = discrete\n" : "");
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
        return p * pcer(pi, q, p);
    }

    // Per comparison error rate.
    private static double pcer(double pi, double q, double p) {
        return (q * q) / (p * p * (2 * pi - 1));
    }

    private void saveMatrix(double[][] effects, File file) {
        try {
            List<Node> vars = new ArrayList<>();
            for (int i = 0; i < effects[0].length; i++) vars.add(new ContinuousVariable("V" + (i + 1)));
            BoxDataSet data = new BoxDataSet(new DoubleDataBox(effects), vars);
            PrintStream out = new PrintStream(new FileOutputStream(file));
            out.println(data.toString());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private double[][] loadMatrix(File file) {
        try {
            Dataset dataset = new ContinuousTabularDataFileReader(file, Delimiter.TAB).readInData();
            final DataSet dataSet = (DataSet) DataConvertUtils.toDataModel(dataset);

            System.out.println("Loaded data " + dataSet.getNumRows() + " x " + dataSet.getNumColumns());

            return dataSet.getDoubleData().toArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}