package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.data.reader.Delimiter;

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
public class Cstar {
    public enum PatternAlgorithm {FGES, PC_STABLE}

    public enum SampleStyle {BOOTSTRAP, SPLIT}

    private int numSubsamples = 30;
    private int qFrom = 1;
    private int qTo = 1;
    private int qIncrement = 1;

    private int parallelism = Runtime.getRuntime().availableProcessors() * 10;
    private IndependenceTest test;

    private PatternAlgorithm patternAlgorithm = PatternAlgorithm.PC_STABLE;
    private SampleStyle sampleStyle = SampleStyle.BOOTSTRAP;

    private boolean verbose = false;

    private Graph trueDag;

    private class Tuple {
        private final Node cause;
        private final Node effect;
        private final double pi;
        private final double minBeta;

        private Tuple(final Node cause, final Node effect, final double pi, final double minBeta) {
            this.cause = cause;
            this.effect = effect;
            this.pi = pi;
            this.minBeta = minBeta;
        }

        public Node getCauseNode() {
            return this.cause;
        }

        public Node getEffectNode() {
            return this.effect;
        }

        public double getPi() {
            return this.pi;
        }

        public double getMinBeta() {
            return this.minBeta;
        }
    }

    // A single record in the returned table.
    public static class Record implements TetradSerializable {
        static final long serialVersionUID = 23L;

        private final Node causeNode;
        private final Node target;
        private final double pi;
        private final double effect;
        private final int q;
        private final int p;

        Record(final Node predictor, final Node target, final double pi, final double minEffect, final int q, final int p) {
            this.causeNode = predictor;
            this.target = target;
            this.pi = pi;
            this.effect = minEffect;
            this.q = q;
            this.p = p;
        }

        public Node getCauseNode() {
            return this.causeNode;
        }

        public Node getEffectNode() {
            return this.target;
        }

        public double getPi() {
            return this.pi;
        }

        double getMinBeta() {
            return this.effect;
        }

        public int getQ() {
            return this.q;
        }

        public int getP() {
            return this.p;
        }
    }

    public Cstar() {
    }

    /**
     * Returns records for a set of variables with expected number of false positives bounded by q.
     *
     * @param dataSet         The full datasets to search over.
     * @param possibleCauses  A set of variables in the datasets over which to search.
     * @param possibleEffects The effect variables.
     * @param test            This test is only used to make more tests like it for subsamples.
     */
    public LinkedList<LinkedList<Record>> getRecords(final DataSet dataSet, final List<Node> possibleCauses, final List<Node> possibleEffects,
                                                     final IndependenceTest test) {
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
                                                     final IndependenceTest test, final String path) {
        possibleEffects = GraphUtils.replaceNodes(possibleEffects, dataSet.getVariables());
        possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());

        final int p = possibleCauses.size() * possibleEffects.size();

        final List<Integer> qs = new ArrayList<>();

        for (int q = this.qFrom; q <= this.qTo; q += this.qIncrement) {
            if (q <= p) {
                qs.add(q);
            } else {
                TetradLogger.getInstance().forceLogMessage("q = " + q + " > p = " + p + "; skipping");
            }
        }

        if (qs.isEmpty()) return new LinkedList<>();

        final LinkedList<LinkedList<Record>> allRecords = new LinkedList<>();

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
            throw new IllegalArgumentException("Expecting Fisher Z, Chi Square, Sem BIC, or Conditional Gaussian Score.");
        }

        final List<Node> augmented = new ArrayList<>(possibleEffects);
        augmented.addAll(possibleCauses);

        final List<Map<Integer, Map<Node, Double>>> minimalEffects = new ArrayList<>();

        for (int t = 0; t < possibleEffects.size(); t++) {
            minimalEffects.add(new ConcurrentHashMap<>());

            for (int b = 0; b < getNumSubsamples(); b++) {
                final Map<Node, Double> map = new ConcurrentHashMap<>();
                for (final Node node : possibleCauses) map.put(node, 0.0);
                minimalEffects.get(t).put(b, map);
            }
        }

        final int[] edgesTotal = new int[1];
        final int[] edgesCount = new int[1];

        class Task implements Callable<double[][]> {
            private final List<Node> possibleCauses;
            private final List<Node> possibleEffects;
            private final int k;
            private final DataSet _dataSet;

            private Task(final int k, final List<Node> possibleCauses, final List<Node> possibleEffects, final DataSet _dataSet) {
                this.k = k;
                this.possibleCauses = possibleCauses;
                this.possibleEffects = possibleEffects;
                this._dataSet = _dataSet;
            }

            public double[][] call() {
                try {
                    final BootstrapSampler sampler = new BootstrapSampler();
                    final DataSet sample;

                    if (Cstar.this.sampleStyle == SampleStyle.BOOTSTRAP) {
                        sampler.setWithoutReplacements(false);
                        sample = sampler.sample(this._dataSet, this._dataSet.getNumRows());
                    } else if (Cstar.this.sampleStyle == SampleStyle.SPLIT) {
                        sampler.setWithoutReplacements(true);
                        sample = sampler.sample(this._dataSet, this._dataSet.getNumRows() / 2);
                    } else {
                        throw new IllegalArgumentException("That type of sample is not configured: " + Cstar.this.sampleStyle);
                    }

                    Graph pattern = null;
                    double[][] effects = null;

                    if (dir != null && new File(dir, "pattern." + (this.k + 1) + ".txt").exists()
                            && new File(dir, "effects." + (this.k + 1) + ".txt").exists()) {
                        try {
                            pattern = GraphUtils.loadGraphTxt(new File(dir, "pattern." + (this.k + 1) + ".txt"));
                            effects = loadMatrix(new File(dir, "effects." + (this.k + 1) + ".txt"));
                        } catch (final Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (pattern == null || effects == null) {
                        if (Cstar.this.patternAlgorithm == PatternAlgorithm.FGES) {
                            pattern = getPatternFges(sample);
                        } else if (Cstar.this.patternAlgorithm == PatternAlgorithm.PC_STABLE) {
                            pattern = getPatternPcStable(sample);
                        } else {
                            throw new IllegalArgumentException("That type of of pattern algorithm is not configured: "
                                    + Cstar.this.patternAlgorithm);
                        }

                        TetradLogger.getInstance().forceLogMessage("# edges = " + pattern.getNumEdges());

                        edgesTotal[0] += pattern.getNumEdges();
                        edgesCount[0]++;

                        if (dir != null) {
                            GraphUtils.saveGraph(pattern, new File(dir, "pattern." + (this.k + 1) + ".txt"), false);
                        }

                        final Ida ida = new Ida(sample, pattern, this.possibleCauses);

                        effects = new double[this.possibleCauses.size()][this.possibleEffects.size()];

                        for (int e = 0; e < this.possibleEffects.size(); e++) {
                            final Map<Node, Double> minEffects = ida.calculateMinimumEffectsOnY(this.possibleEffects.get(e));

                            for (int c = 0; c < this.possibleCauses.size(); c++) {
                                effects[c][e] = minEffects.get(this.possibleCauses.get(c));
                            }
                        }

                        if (dir != null) {
                            saveMatrix(effects, new File(dir, "effects." + (this.k + 1) + ".txt"));
                        } else {
                            saveMatrix(effects, null);
                        }
                    }


                    if (Cstar.this.verbose) {
                        TetradLogger.getInstance().forceLogMessage("Bootstrap " + (this.k + 1));
                    }

                    return effects;
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        final List<Callable<double[][]>> tasks = new ArrayList<>();

        for (int k = 0; k < getNumSubsamples(); k++) {
            tasks.add(new Task(k, possibleCauses, possibleEffects, dataSet));
        }

        final List<double[][]> allEffects = runCallablesDoubleArray(tasks, getParallelism());

        final int avgEdges = (int) (edgesTotal[0] / (double) edgesCount[0]);
//        avgEdges /= 2.0;

        qs.clear();
        qs.add(avgEdges);


        final List<List<Double>> doubles = new ArrayList<>();
//        List<Double> allDoubles = new ArrayList<>();

        for (int k = 0; k < getNumSubsamples(); k++) {
            final double[][] effects = allEffects.get(k);

            if (effects.length != possibleCauses.size() || effects[0].length != possibleEffects.size()) {
                throw new IllegalStateException("Bootstrap " + (k + 1)
                        + " is damaged; delete the pattern and effect files for that bootstrap and rerun");
            }

            final List<Double> _doubles = new ArrayList<>();

            for (int c = 0; c < possibleCauses.size(); c++) {
                for (int e = 0; e < possibleEffects.size(); e++) {
                    _doubles.add(effects[c][e]);
                }
            }

            _doubles.sort((o1, o2) -> Double.compare(o2, o1));
            doubles.add(_doubles);
        }

        class Task2 implements Callable<Boolean> {
            private final List<Node> possibleCauses;
            private final List<Node> possibleEffects;
            private final int q;

            private Task2(final List<Node> possibleCauses, final List<Node> possibleEffects, final int q) {
                this.possibleCauses = possibleCauses;
                this.possibleEffects = possibleEffects;
                this.q = q;
            }

            public Boolean call() {
                try {
                    if (Cstar.this.verbose) {
                        TetradLogger.getInstance().forceLogMessage("Examining q = " + this.q);
                    }

                    final List<Tuple> tuples = new ArrayList<>();

                    for (int e = 0; e < this.possibleEffects.size(); e++) {
                        for (int c = 0; c < this.possibleCauses.size(); c++) {
                            int count = 0;

                            for (int k = 0; k < getNumSubsamples(); k++) {
                                if (this.q > doubles.get(k).size()) {
                                    continue;
                                }

                                final double cutoff = doubles.get(k).get(this.q - 1);

                                if (allEffects.get(k)[c][e] >= cutoff) {
                                    count++;
                                }
                            }

//                            if (count > 0) {
                            final double pi = count / ((double) getNumSubsamples());
                            if (pi < (this.q / (double) p)) continue;
                            final Node cause = this.possibleCauses.get(c);
                            final Node effect = this.possibleEffects.get(e);
                            tuples.add(new Tuple(cause, effect, pi,
                                    avgMinEffect(this.possibleCauses, this.possibleEffects, allEffects, cause, effect)));
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

                    final LinkedList<Record> records = new LinkedList<>();
                    double sum = 0.0;

                    for (int i = 0; i < tuples.size() /*Math.min(q, tuples.size())*/; i++) {
                        final Tuple tuple = tuples.get(i);

                        sum += tuple.getPi();
                        final double avg = tuple.getMinBeta();

                        final Node causeNode = tuple.getCauseNode();
                        final Node effectNode = tuple.getEffectNode();

                        records.add(new Record(causeNode, effectNode, tuple.getPi(), avg, this.q, p));
                    }

                    allRecords.add(records);
                    return true;
                } catch (final Exception e) {
                    e.printStackTrace();
                }

                return null;
            }
        }

        final List<Callable<Boolean>> task2s = new ArrayList<>();

        for (final int q : qs) {
            task2s.add(new Task2(possibleCauses, possibleEffects, q));
        }

        ConcurrencyUtils.runCallables(task2s, getParallelism());

        allRecords.sort(Comparator.comparingDouble(List::size));

        return allRecords;
    }

    private DataSet readData(final File dir, final String s) {
        try {
            final DataSet dataSet = DataUtils.loadContinuousData(new File(dir, s),"//", '*', "*", true, Delimiter.TAB);

            TetradLogger.getInstance().forceLogMessage(
                    "Loaded data " + dataSet.getNumRows() + " x " + dataSet.getNumColumns());

            return dataSet;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeData(final DataSet dataSet, final File dir, final String s) {
        try {
            final PrintStream out = new PrintStream(new FileOutputStream(new File(dir, s)));
            out.println(dataSet.toString());
        } catch (final FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Node> readVars(final DataSet dataSet, final File dir, final String s) {
        try {
            final List<Node> vars = new ArrayList<>();

            final File file = new File(dir, s);

            final BufferedReader in = new BufferedReader(new FileReader(file));
            String line;

            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                vars.add(dataSet.getVariable(line.trim()));
            }

            return vars;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeVars(final List<Node> vars, final File dir, final String s) {
        try {
            final File file = new File(dir, s);

            final PrintStream out = new PrintStream(new FileOutputStream(file));

            for (final Node node : vars) {
                out.println(node.getName());
            }

            out.flush();
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private double avgMinEffect(final List<Node> possibleCauses, final List<Node> possibleEffects, final List<double[][]> allEffects,
                                final Node causeNode, final Node effectNode) {
        final List<Double> f = new ArrayList<>();

        if (allEffects == null) {
            throw new NullPointerException("effects null");
        }

        for (int k = 0; k < getNumSubsamples(); k++) {
            final int c = possibleCauses.indexOf(causeNode);
            final int e = possibleEffects.indexOf(effectNode);
            f.add(allEffects.get(k)[c][e]);
        }

        final double[] _f = new double[f.size()];
        for (int b = 0; b < f.size(); b++) _f[b] = f.get(b);
        return StatUtils.mean(_f);
    }

    public static LinkedList<Record> cStar(final LinkedList<LinkedList<Record>> allRecords) {
        final Map<Edge, List<Record>> map = new HashMap<>();

        for (final List<Record> records : allRecords) {
            for (final Record record : records) {
                final Edge edge = Edges.directedEdge(record.getCauseNode(), record.getEffectNode());
                map.computeIfAbsent(edge, k -> new ArrayList<>());
                map.get(edge).add(record);
            }
        }

        final LinkedList<Record> cstar = new LinkedList<>();

        for (final Edge edge : map.keySet()) {
            final List<Record> recordList = map.get(edge);

            final double[] pis = new double[recordList.size()];
            final double[] effects = new double[recordList.size()];

            for (int i = 0; i < recordList.size(); i++) {
                pis[i] = recordList.get(i).getPi();
                effects[i] = recordList.get(i).getMinBeta();
            }

            final double medianPis = StatUtils.median(pis);
            final double medianEffects = StatUtils.median(effects);

            final Record _record = new Record(edge.getNode1(), edge.getNode2(), medianPis, medianEffects,
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
    public String makeTable(final LinkedList<Record> records, final boolean printTable) {
        int numColumns = 8;

        if (this.trueDag != null) {
            numColumns++;
        }

        final TextTable table = new TextTable(records.size() + 1, numColumns);
//        table.setLatex(true);
        final NumberFormat nf = new DecimalFormat("0.0000");
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

        if (this.trueDag != null) {
            table.setToken(0, column++, "SUM(FP)");
        }

        double sumPi = 0.0;

        if (records.isEmpty()) {
            return "\nThere are no records above chance.\n";
        }

        final int p = records.getLast().getP();
        final int q = records.getLast().getQ();

        int ancestorCount = 0;

        for (int i = 0; i < records.size(); i++) {
            final Node cause = records.get(i).getCauseNode();
            final Node effect = records.get(i).getEffectNode();

            final int R = (i + 1);
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

            if (this.trueDag != null) {
                final boolean ancestor = this.trueDag.isAncestorOf(this.trueDag.getNode(cause.getName()), this.trueDag.getNode(effect.getName()));
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
    public Graph makeGraph(final List<Record> records) {
        final List<Node> outNodes = new ArrayList<>();

        final Graph graph = new EdgeListGraph(outNodes);

        for (final Record record : records) {
            graph.addNode(record.getCauseNode());
            graph.addNode(record.getEffectNode());
            graph.addDirectedEdge(record.getCauseNode(), record.getEffectNode());
        }

        return graph;
    }

    public void setNumSubsamples(final int numSubsamples) {
        this.numSubsamples = numSubsamples;
    }

    public void setParallelism(final int parallelism) {
        this.parallelism = parallelism;
    }

    public void setqFrom(final int qFrom) {
        this.qFrom = qFrom;
    }

    public void setqTo(final int qTo) {
        this.qTo = qTo;
    }

    public void setqIncrement(final int qIncrement) {
        this.qIncrement = qIncrement;
    }

    public void setPatternAlgorithm(final PatternAlgorithm patternAlgorithm) {
        this.patternAlgorithm = patternAlgorithm;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    public void setSampleStyle(final SampleStyle sampleStyle) {
        this.sampleStyle = sampleStyle;
    }

    public void setTrueDag(final Graph trueDag) {
        this.trueDag = trueDag;
    }

    //=============================PRIVATE==============================//

    private int getNumSubsamples() {
        return this.numSubsamples;
    }

    private int getParallelism() {
        return this.parallelism;
    }

    private Graph getPatternPcStable(final DataSet sample) {
        final IndependenceTest test = getIndependenceTest(sample, this.test);
        final PcStable pc = new PcStable(test);
        return pc.search();
    }

    private Graph getPatternFges(final DataSet sample) {
        final Score score = new ScoredIndTest(getIndependenceTest(sample, this.test));
        final Fges fges = new Fges(score, 1);
        return fges.search();
    }

    private IndependenceTest getIndependenceTest(final DataSet sample, final IndependenceTest test) {
        if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof SemBicScore) {
            final SemBicScore score = new SemBicScore(new CorrelationMatrix(sample));
            score.setPenaltyDiscount(((SemBicScore) ((IndTestScore) test).getWrappedScore()).getPenaltyDiscount());
            return new IndTestScore(score);
        } else if (test instanceof IndTestFisherZ) {
            final double alpha = test.getAlpha();
            return new IndTestFisherZ(new CorrelationMatrix(sample), alpha);
        } else if (test instanceof ChiSquare) {
            final double alpha = test.getAlpha();
            return new IndTestFisherZ(sample, alpha);
        } else if (test instanceof IndTestScore && ((IndTestScore) test).getWrappedScore() instanceof ConditionalGaussianScore) {
            final ConditionalGaussianScore score = (ConditionalGaussianScore) ((IndTestScore) test).getWrappedScore();
            final double penaltyDiscount = score.getPenaltyDiscount();
            final ConditionalGaussianScore _score = new ConditionalGaussianScore(sample, 1, 0, false);
            _score.setPenaltyDiscount(penaltyDiscount);
            return new IndTestScore(_score);
        } else {
            throw new IllegalArgumentException("That test is not configured: " + test);
        }
    }

    // Meinhausen and Buhlmann E(|V|) bound
    private static double er(final double pi, final double q, final double p) {
        return p * pcer(pi, q, p);
    }

    // Meinhausen and Buhlmann per comparison error rate (PCER)
    private static double pcer(final double pi, final double q, final double p) {
        return (q * q) / (p * p * (2 * pi - 1));
    }

    private void saveMatrix(final double[][] effects, final File file) {
        try {
            final List<Node> vars = new ArrayList<>();
            for (int i = 0; i < effects[0].length; i++) vars.add(new ContinuousVariable("V" + (i + 1)));
            final BoxDataSet data = new BoxDataSet(new DoubleDataBox(effects), vars);
            if (file != null) {
                final PrintStream out = new PrintStream(new FileOutputStream(file));
                out.println(data);
            } else {
                TetradLogger.getInstance().forceLogMessage(data.toString());
            }
        } catch (final FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private double[][] loadMatrix(final File file) {
        try {
            final DataSet dataSet = DataUtils.loadContinuousData(file, "//", '\"', "*", true, Delimiter.TAB);

            TetradLogger.getInstance().forceLogMessage("Loaded data " + dataSet.getNumRows() + " x " + dataSet.getNumColumns());

            return dataSet.getDoubleData().toArray();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<double[][]> runCallablesDoubleArray(final List<Callable<double[][]>> tasks, final int parallelism) {
        if (tasks.isEmpty()) return new ArrayList<>();

        final List<double[][]> results = new ArrayList<>();

        if (parallelism == 1) {
            for (final Callable<double[][]> task : tasks) {
                try {
                    results.add(task.call());
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            final ExecutorService executorService = Executors.newWorkStealingPool();

            try {
                final List<Future<double[][]>> futures = executorService.invokeAll(tasks);

                for (final Future<double[][]> future : futures) {
                    results.add(future.get());
                }
            } catch (final InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            executorService.shutdown();

            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (final InterruptedException e) {
                executorService.shutdownNow();
            }
        }

        return results;
    }
}