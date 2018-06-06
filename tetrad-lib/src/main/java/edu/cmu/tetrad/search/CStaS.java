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
import java.util.concurrent.*;

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
    private IndependenceTest test;

    private PatternAlgorithm patternAlgorithm = PatternAlgorithm.FGES;
    private SampleStyle sampleStyle = SampleStyle.BOOTSTRAP;

    private boolean verbose = false;

    private Graph trueDag;

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
        private int q;
        private int p;

        Record(Node predictor, Node target, double pi, double minEffect, int q, int p) {
            this.causeNode = predictor;
            this.target = target;
            this.pi = pi;
            this.effect = minEffect;
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

        double getMinBeta() {
            return effect;
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
            } else {
                writeVars(possibleCauses, dir, "possible.causes.txt");
                writeVars(possibleEffects, dir, "possible.effects.txt");
                writeData(dataSet, dir, "data.txt");
            }
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

        final int[] edgesTotal = new int[1];
        final int[] edgesCount = new int[1];

        class Task implements Callable<double[][]> {
            private final List<Node> possibleCauses;
            private final List<Node> possibleEffects;
            private final int k;
            private DataSet _dataSet;

            private Task(int k, List<Node> possibleCauses, List<Node> possibleEffects, DataSet _dataSet) {
                this.k = k;
                this.possibleCauses = possibleCauses;
                this.possibleEffects = possibleEffects;
                this._dataSet = _dataSet;
            }

            public double[][] call() {
                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    DataSet sample;

                    if (sampleStyle == SampleStyle.BOOTSTRAP) {
                        sampler.setWithoutReplacements(false);
                        sample = sampler.sample(_dataSet, _dataSet.getNumRows());
                    } else if (sampleStyle == SampleStyle.SPLIT) {
                        sampler.setWithoutReplacements(true);
                        sample = sampler.sample(this._dataSet, this._dataSet.getNumRows() / 2);
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

                        System.out.println("# edges = " + pattern.getNumEdges());

                        edgesTotal[0] += pattern.getNumEdges();
                        edgesCount[0]++;

                        if (dir != null) {
                            GraphUtils.saveGraph(pattern, new File(dir, "pattern." + (k + 1) + ".txt"), false);
                        }

                        Ida ida = new Ida(sample, pattern, this.possibleCauses);

                        effects = new double[this.possibleCauses.size()][this.possibleEffects.size()];

                        for (int e = 0; e < this.possibleEffects.size(); e++) {
                            Map<Node, Double> minEffects = ida.calculateMinimumEffectsOnY(this.possibleEffects.get(e));

                            for (int c = 0; c < this.possibleCauses.size(); c++) {
                                effects[c][e] = minEffects.get(this.possibleCauses.get(c));
                            }
                        }

                        if (dir != null) {
                            saveMatrix(effects, new File(dir, "effects." + (k + 1) + ".txt"));
                        }
                    }


                    if (verbose) {
                        System.out.println("Bootstrap " + (k + 1));
                    }

                    return effects;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        List<Callable<double[][]>> tasks = new ArrayList<>();

        for (int k = 0; k < getNumSubsamples(); k++) {
            tasks.add(new Task(k, possibleCauses, possibleEffects, dataSet));
        }

        final List<double[][]> allEffects = runCallablesDoubleArray(tasks, getParallelism());

        int avgEdges = (int) (edgesTotal[0] / (double) edgesCount[0]);
//        avgEdges /= 2.0;

        qs.clear();
        qs.add(avgEdges);


        List<List<Double>> doubles = new ArrayList<>();
//        List<Double> allDoubles = new ArrayList<>();

        for (int k = 0; k < getNumSubsamples(); k++) {
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

//                            if (count > 0) {
                            final double pi = count / ((double) getNumSubsamples());
                            if (pi < (q / (double) p)) continue;
                            final Node cause = possibleCauses.get(c);
                            final Node effect = possibleEffects.get(e);
                            tuples.add(new Tuple(cause, effect, pi,
                                    avgMinEffect(possibleCauses, possibleEffects, allEffects, cause, effect)));
//                            }
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
                    double sum = 0.0;

                    for (int i = 0; i < tuples.size() /*Math.min(q, tuples.size())*/; i++) {
                        Tuple tuple = tuples.get(i);

                        sum += tuple.getPi();
                        double avg = tuple.getMinBeta();

                        final Node causeNode = tuple.getCauseNode();
                        final Node effectNode = tuple.getEffectNode();

                        records.add(new Record(causeNode, effectNode, tuple.getPi(), avg, q, p));
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
                    -1, -1);
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
        int numColumns = 8;

        if (trueDag != null) {
            numColumns++;
        }

        TextTable table = new TextTable(records.size() + 1, numColumns);
//        table.setLatex(true);
        NumberFormat nf = new DecimalFormat("0.0000");
        int column = 0;

        table.setToken(0, column++, "Index");
        table.setToken(0, column++, "Cause");
        table.setToken(0, column++, "Effect");
//        table.setToken(0, column++, "Type");
        table.setToken(0, column++, "PI");
        table.setToken(0, column++, "Effect");
//        table.setToken(0, column++, "SUM(Pi)");
        table.setToken(0, column++, "R-SUM(Pi)");
        table.setToken(0, column++, "E(V)");
        table.setToken(0, column++, "PCER");

        if (trueDag != null) {
            table.setToken(0, column++, "SUM(FP)");
        }

        double sumPi = 0.0;

        if (records.isEmpty()) {
            return "\nThere are no records above chance.\n";
        }

        int p = records.getLast().getP();
        int q = records.getLast().getQ();

        int ancestorCount = 0;

        for (int i = 0; i < records.size(); i++) {
            final Node cause = records.get(i).getCauseNode();
            final Node effect = records.get(i).getEffectNode();

            int R = (i + 1);
            sumPi += records.get(i).getPi();
            column = 0;


            table.setToken(i + 1, column++, "" + (i + 1));
            table.setToken(i + 1, column++, cause.getName());
            table.setToken(i + 1,    column++, effect.getName());
//            table.setToken(i + 1, column++, cause instanceof DiscreteVariable ? "D" : "C");
            table.setToken(i + 1, column++, nf.format(records.get(i).getPi()));
            table.setToken(i + 1, column++, nf.format(records.get(i).getMinBeta()));
//            table.setToken(i + 1, column++, nf.format(sumPi));
            table.setToken(i + 1, column++, nf.format(R - sumPi));
            final double er = er(records.get(i).getPi(), (i + 1), p);
            table.setToken(i + 1, column++, records.get(i).getPi() <= 0.5 ? "*" : nf.format(er));
            final double pcer = pcer(records.get(i).getPi(), (i + 1), p);
            table.setToken(i + 1, column++, records.get(i).getPi() <= 0.5 ? "*" : nf.format(pcer));

            if (trueDag != null) {
                boolean ancestor = trueDag.isAncestorOf(trueDag.getNode(cause.getName()), trueDag.getNode(effect.getName()));
                if (ancestor) ancestorCount++;
                table.setToken(i + 1, column++, nf.format((R - ancestorCount)));
            }
        }

        return (printTable ? "\n" + table : "" + "")
                + "p = " + p + " q = " + q
                + (printTable ? " Type: C = continuous, D = discrete\n" : "");
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

    // Meinhausen and Buhlmann E(|V|) bound
    private static double er(double pi, double q, double p) {
        return p * pcer(pi, q, p);
    }

    // Meinhausen and Buhlmann per comparison error rate (PCER)
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

    private List<double[][]> runCallablesDoubleArray(List<Callable<double[][]>> tasks, int parallelism) {
        if (tasks.isEmpty()) return new ArrayList<>();

        List<double[][]> results = new ArrayList<>();

        if (parallelism == 1) {
            for (Callable<double[][]> task : tasks) {
                try {
                    results.add(task.call());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            ExecutorService executorService = Executors.newWorkStealingPool();

            try {
                List<Future<double[][]>> futures = executorService.invokeAll(tasks);

                for (Future<double[][]> future : futures) {
                    results.add(future.get());
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            executorService.shutdown();

            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }

        return results;
    }
}