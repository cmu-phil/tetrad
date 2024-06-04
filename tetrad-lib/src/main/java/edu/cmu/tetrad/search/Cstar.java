package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.RestrictedBoss;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Implements the CStaR algorithm (Stekhoven et al., 2012), which finds a CPDAG of that data and then tries all
 * orientations of the undirected edges about a variable in the CPDAG to estimate a minimum bound on the effect for a
 * given edge. Some references include the following:
 * <p>
 * Stekhoven, D. J., Moraes, I., Sveinbjörnsson, G., Hennig, L., Maathuis, M. H., and Bühlmann, P. (2012). Causal
 * stability ranking. Bioinformatics, 28(21), 2819-2823.
 * <p>
 * Meinshausen, N., and Bühlmann, P. (2010). Stability selection. Journal of the Royal Statistical Society: Series B
 * (Statistical Methodology), 72(4), 417-473.
 * <p>
 * Colombo, D., and Maathuis, M. H. (2014). Order-independent constraint-based causal structure learning. The Journal of
 * Machine Learning Research, 15(1), 3741-3782.
 * <p>
 * This class is not configured to respect knowledge of forbidden and required edges.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Ida
 */
public class Cstar {

    /**
     * The type of CPDAG algorithm to use.
     */
    private final IndependenceWrapper test;

    /**
     * The type of score to use.
     */
    private final ScoreWrapper score;

    /**
     * The parameters.
     */
    private final Parameters parameters;

    /**
     * Whether the algorithm should be parallelized. Different runs of the algorithms can be run in different threads in
     * parallel.
     */
    private boolean parallelized = false;

    /**
     * The number of subsamples.
     */
    private int numSubsamples = 30;

    /**
     * Represents the top bracket size.
     */
    private int topBracket = 5;

    /**
     * The selection alpha.
     */
    private double selectionAlpha = 0.0;

    /**
     * The CPDAG algorithm.
     */
    private CpdagAlgorithm cpdagAlgorithm = CpdagAlgorithm.PC_STABLE;

    /**
     * The sample style.
     *
     * @see SampleStyle
     */
    private SampleStyle sampleStyle = SampleStyle.SUBSAMPLE;

    /**
     * Represents a boolean variable indicating whether verbose output will be printed.
     */
    private boolean verbose;

    /**
     * Represents a file directory used for storing and accessing files.
     */
    private File newDir = null;

    /**
     * Constructor.
     *
     * @param test       a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     * @param score      a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Cstar(IndependenceWrapper test, ScoreWrapper score, Parameters parameters) {
        this.test = test;
        this.score = score;
        this.parameters = parameters;
    }

    /**
     * Returns a list of records for making a CSTaR table.
     *
     * @param allRecords the list of all records.
     * @return The list for the CSTaR table.
     */
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

            Record record = new Record(edge.getNode1(), edge.getNode2(), medianPis, medianEffects, recordList.get(0).getNumCauses(), recordList.get(0).getNumEffects());
            cstar.add(record);
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
     * Calculates the PCER (Per Comparison Error Rate) based on the given parameters. See Meinhausen and Buhlman.
     *
     * @param pi The pi value.
     * @param q  The q value.
     * @param p  The p value.
     * @return The calculated PCER value.
     */
    private static double pcer(double pi, double q, double p) {
        return (1.0 / ((2. * pi - 1.))) * ((q * q) / (p * p));
    }

    /**
     * Sets whether the algorithm should be parallelized. Different runs of the algorithms can be run in different
     * threads in parallel.
     *
     * @param parallelized True, if so.
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * Returns records for a set of variables with expected number of false positives bounded by q.
     *
     * @param dataSet         The full datasets to search over.
     * @param possibleCauses  A set of variables in the datasets over which to search.
     * @param possibleEffects The effect variables.
     * @param path            A path where interim results are to be stored. If null, interim results will not be
     *                        stored. If the path is specified, then if the process is stopped and restarted, previously
     *                        computed interim results will be loaded.
     * @param topBracket      a int
     * @return a {@link java.util.LinkedList} object
     * @see Record
     */
    public LinkedList<LinkedList<Record>> getRecords(DataSet dataSet, List<Node> possibleCauses, List<Node> possibleEffects, int topBracket, String path) {
        if (topBracket < 1) {
            throw new IllegalArgumentException("Top bracket must be at least 1.");
        }

        if (topBracket > possibleCauses.size()) {
            throw new IllegalArgumentException("Top bracket (q) is too large; it is " + topBracket + " but the number of possible causes is " + possibleCauses.size());
        }

        this.topBracket = topBracket;

        if (path == null || path.isEmpty()) {
            path = "cstar-out";
            TetradLogger.getInstance().log("Using path = 'cstar-out'.");
        }

        File origDir = null;

        if (new File(path).exists()) {
            origDir = new File(path);
        }

        File newDir;

        int i;

        for (i = 1; ; i++) {
            if (!new File(path + "." + i).exists()) break;
        }

        path = path + "." + i;

        newDir = new File(path);

        if (origDir == null) {
            origDir = newDir;
        }

        boolean made = newDir.mkdirs();

        if (!made) {
            throw new IllegalStateException("Could not make a new directory; perhaps file permissions need to be adjusted.");
        }

        TetradLogger.getInstance().log("Creating directories for " + newDir.getAbsolutePath());

        newDir = new File(path);
        TetradLogger.getInstance().log("Using files in directory " + origDir.getAbsolutePath());

        this.newDir = newDir;

        possibleEffects = GraphUtils.replaceNodes(possibleEffects, dataSet.getVariables());
        possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());

        LinkedList<LinkedList<Record>> allRecords = new LinkedList<>();

        TetradLogger.getInstance().log("Results directory = " + newDir.getAbsolutePath());

        if (new File(origDir, "possible.causes.txt").exists() && new File(newDir, "possible.causes.txt").exists()) {
            TetradLogger.getInstance().log("Loading data, possible causes, and possible effects from " + origDir.getAbsolutePath());
            possibleCauses = readVars(dataSet, origDir, "possible.causes.txt");
            possibleEffects = readVars(dataSet, origDir, "possible.effects.txt");
        }

        writeVars(possibleCauses, newDir, "possible.causes.txt");
        writeVars(possibleEffects, newDir, "possible.effects.txt");

        if (new File(origDir, "data.txt").exists()) {
            try {
                dataSet = SimpleDataLoader.loadContinuousData(new File(origDir, "data.txt"), "//",
                        '\"', "*", true, Delimiter.TAB, false);
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not load data from " + new File(origDir, "data.txt").getAbsolutePath());
            }
        }

        writeData(dataSet, newDir);

        List<Map<Integer, Map<Node, Double>>> minimalEffects = new ArrayList<>();

        for (int e = 0; e < possibleEffects.size(); e++) {
            minimalEffects.add(new ConcurrentHashMap<>());

            for (int s = 0; s < this.numSubsamples; s++) {
                Map<Node, Double> map = new ConcurrentHashMap<>();
                for (Node node : possibleCauses) map.put(node, 0.0);
                minimalEffects.get(e).put(s, map);
            }
        }

        class Task implements Callable<double[][]> {
            private final List<Node> possibleCauses;
            private final List<Node> possibleEffects;
            private final int subsample;
            private final DataSet _dataSet;
            private final File origDir;
            private final File newDir;

            private Task(int subsample, List<Node> possibleCauses, List<Node> possibleEffects, DataSet dataSet, File origDir, File newDir) {
                this.subsample = subsample;
                this.possibleCauses = possibleCauses;
                this.possibleEffects = possibleEffects;
                this._dataSet = dataSet;
                this.origDir = origDir;
                this.newDir = newDir;
            }

            public double[][] call() {
                TetradLogger.getInstance().log("\nRunning subsample " + (this.subsample + 1) + " of " + Cstar.this.numSubsamples + ".");

                try {
                    BootstrapSampler sampler = new BootstrapSampler();
                    DataSet sample;
                    Graph cpdag;
                    double[][] effects;

                    if (new File(origDir, "cpdag." + (this.subsample + 1) + ".txt").exists() && new File(origDir, "effects." + (this.subsample + 1) + ".txt").exists()) {
                        TetradLogger.getInstance().log("Loading CPDAG and effects from " + origDir.getAbsolutePath() + " for index " + (this.subsample + 1));
                        cpdag = GraphSaveLoadUtils.loadGraphTxt(new File(origDir, "cpdag." + (this.subsample + 1) + ".txt"));
                        effects = loadMatrix(new File(origDir, "effects." + (this.subsample + 1) + ".txt"));
                    } else {
                        TetradLogger.getInstance().log("Sampling data for index " + (this.subsample + 1));

                        if (Cstar.this.sampleStyle == SampleStyle.BOOTSTRAP) {
                            sampler.setWithoutReplacements(false);
                            sample = sampler.sample(this._dataSet, this._dataSet.getNumRows() / 2);
                        } else if (Cstar.this.sampleStyle == SampleStyle.SUBSAMPLE) {
                            sampler.setWithoutReplacements(true);
                            sample = sampler.sample(this._dataSet, this._dataSet.getNumRows() / 2);
                        } else {
                            throw new IllegalArgumentException("That type of sample is not configured: " + Cstar.this.sampleStyle);
                        }

                        if (Cstar.this.cpdagAlgorithm == CpdagAlgorithm.PC_STABLE) {
                            TetradLogger.getInstance().log("Running PC-Stable for index " + (this.subsample + 1));
                            cpdag = getPatternPcStable(sample);
                        } else if (Cstar.this.cpdagAlgorithm == CpdagAlgorithm.FGES) {
                            TetradLogger.getInstance().log("Running FGES for index " + (this.subsample + 1));
                            cpdag = getPatternFges(sample);
                        } else if (Cstar.this.cpdagAlgorithm == CpdagAlgorithm.BOSS) {
                            TetradLogger.getInstance().log("Running BOSS for index " + (this.subsample + 1));
                            cpdag = getPatternBoss(sample);
                        } else if (Cstar.this.cpdagAlgorithm == CpdagAlgorithm.RESTRICTED_BOSS) {
                            TetradLogger.getInstance().log("Running Restricted BOSS for index " + (this.subsample + 1));
                            cpdag = getPatternRestrictedBoss(sample, this._dataSet);
                        } else {
                            throw new IllegalArgumentException("That type of of cpdag algorithm is not configured: " + Cstar.this.cpdagAlgorithm);
                        }

                        Ida ida = new Ida(sample, cpdag, this.possibleCauses);

                        effects = new double[this.possibleCauses.size()][this.possibleEffects.size()];

                        TetradLogger.getInstance().log("Running IDA for index " + (this.subsample + 1));
                        for (int e = 0; e < this.possibleEffects.size(); e++) {
                            Map<Node, Double> minEffects = ida.calculateMinimumTotalEffectsOnY(this.possibleEffects.get(e));

                            for (int c = 0; c < this.possibleCauses.size(); c++) {
                                final Double _e = minEffects.get(this.possibleCauses.get(c));
                                effects[c][e] = _e != null ? _e : 0.0;
                            }
                        }
                    }

                    TetradLogger.getInstance().log("Saving CPDAG and effects for index " + (this.subsample + 1));
                    saveMatrix(effects, new File(newDir, "effects." + (this.subsample + 1) + ".txt"));

                    try {
                        GraphSaveLoadUtils.saveGraph(cpdag, new File(newDir, "cpdag." + (this.subsample + 1) + ".txt"), false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    return effects;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        List<Callable<double[][]>> tasks = new ArrayList<>();

        for (int subsample = 0; subsample < this.numSubsamples; subsample++) {
            tasks.add(new Task(subsample, possibleCauses, possibleEffects, dataSet, origDir, newDir));
        }

        List<double[][]> allEffects = runCallablesDoubleArray(tasks, parallelized);

        List<List<Double>> doubles = new ArrayList<>();

        for (int subsample = 0; subsample < this.numSubsamples; subsample++) {
            double[][] effects = allEffects.get(subsample);

            if (effects.length != possibleCauses.size() || effects[0].length != possibleEffects.size()) {
                throw new IllegalStateException("Length of subsample " + (subsample + 1) + "does not match the number of possible causes.");
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

        try {
            if (Cstar.this.verbose) {
                TetradLogger.getInstance().log("Examining top bracket = " + this.topBracket + ".");
            }

            List<Tuple> tuples = new ArrayList<>();

            for (int e = 0; e < possibleEffects.size(); e++) {
                for (int c = 0; c < possibleCauses.size(); c++) {
                    int count = 0;

                    for (int subsample = 0; subsample < Cstar.this.numSubsamples; subsample++) {
                        double cutoff = doubles.get(subsample).get(this.topBracket * possibleEffects.size() - 1);

                        if (allEffects.get(subsample)[c][e] >= cutoff) {
                            count++;
                        }
                    }

                    double pi = count / ((double) Cstar.this.numSubsamples);
                    if (pi <= 0) continue;
                    Node cause = possibleCauses.get(c);
                    Node effect = possibleEffects.get(e);
                    tuples.add(new Tuple(cause, effect, pi, avgMinEffect(possibleCauses, possibleEffects,
                            allEffects, cause, effect)));
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

            for (Tuple tuple : tuples) {
                double avg = tuple.getMinBeta();

                Node causeNode = tuple.getCauseNode();
                Node effectNode = tuple.getEffectNode();

                if (tuple.getMinBeta() > selectionAlpha) {
                    records.add(new Record(causeNode, effectNode, tuple.getPi(), avg, possibleCauses.size(), possibleEffects.size()));
                }
            }

            allRecords.add(records);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        allRecords.sort(Comparator.comparingDouble(List::size));

        return allRecords;
    }

    /**
     * Makes a graph of the estimated predictors to the effect.
     *
     * @param records The list of records obtained from a method above.
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph makeGraph(List<Record> records) {
        List<Node> outNodes = new ArrayList<>();

        Graph graph = new EdgeListGraph(outNodes);

        for (Record record : records) {
            if (record.getPi() > 0.5) {
                graph.addNode(record.getCauseNode());
                graph.addNode(record.getEffectNode());
                graph.addDirectedEdge(record.getCauseNode(), record.getEffectNode());
            }
        }

        return graph;
    }

    /**
     * The CSTaR algorithm can use any CPDAG algorithm; here you can set it.
     *
     * @param cpdagAlgorithm The CPDAG algorithm.
     * @see CpdagAlgorithm
     */
    public void setCpdagAlgorithm(CpdagAlgorithm cpdagAlgorithm) {
        this.cpdagAlgorithm = cpdagAlgorithm;
    }

    /**
     * Sets whether verbose output will be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the selection alpha.
     *
     * @param selectionAlpha This alpha.
     */
    public void setSelectionAlpha(double selectionAlpha) {
        this.selectionAlpha = selectionAlpha;
    }

    /**
     * Sets the sample style.
     *
     * @param sampleStyle This style.
     * @see SampleStyle
     */
    public void setSampleStyle(SampleStyle sampleStyle) {
        this.sampleStyle = sampleStyle;
    }

    /**
     * Sets the number of subsamples.
     *
     * @param numSubsamples This number.
     */
    public void setNumSubsamples(int numSubsamples) {
        this.numSubsamples = numSubsamples;
    }

    /**
     * <p>getDir.</p>
     *
     * @return a {@link java.io.File} object
     */
    public File getDir() {
        return newDir;
    }

    /**
     * Writes the given DataSet to a file in the specified directory.
     *
     * @param dataSet The DataSet to write.
     * @param dir     The directory where the file will be written.
     */
    private void writeData(DataSet dataSet, File dir) {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(new File(dir, "data.txt")));
            out.println(dataSet.toString());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads variables from a file and returns a list of corresponding nodes.
     *
     * @param dataSet The data set from which to retrieve the variables.
     * @param dir     The directory containing the file.
     * @param s       The name of the file.
     * @return A list of nodes representing the variables read from the file.
     * @throws RuntimeException If an error occurs during the file reading process.
     */
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

    /**
     * Writes the given variables to a file in the specified directory.
     *
     * @param vars - List of Node objects representing the variables to be written.
     * @param dir  - The directory where the file will be written.
     * @param s    - The name of the file.
     */
    private void writeVars(List<Node> vars, File dir, String s) {
        try {
            File file = new File(dir, s);

            PrintStream out = new PrintStream(new FileOutputStream(file));

            for (Node node : vars) {
                out.println(node.getName());
            }

            out.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculates the average minimum effect for a given cause and effect node.
     *
     * @param possibleCauses  the list of possible cause nodes
     * @param possibleEffects the list of possible effect nodes
     * @param allEffects      the list of effect matrices
     * @param causeNode       the cause node
     * @param effectNode      the effect node
     * @return the average minimum effect
     * @throws NullPointerException if allEffects is null
     */
    private double avgMinEffect(List<Node> possibleCauses, List<Node> possibleEffects, List<double[][]> allEffects, Node causeNode, Node effectNode) {
        List<Double> f = new ArrayList<>();

        if (allEffects == null) {
            throw new NullPointerException("effects null");
        }

        for (int k = 0; k < this.numSubsamples; k++) {
            int c = possibleCauses.indexOf(causeNode);
            int e = possibleEffects.indexOf(effectNode);
            f.add(allEffects.get(k)[c][e]);
        }

        double[] _f = new double[f.size()];
        for (int b = 0; b < f.size(); b++) _f[b] = f.get(b);
        return StatUtils.mean(_f);
    }

    /**
     * Returns a text table from the given records
     *
     * @param records a {@link java.util.LinkedList} object
     * @return a {@link java.lang.String} object
     */
    public String makeTable(LinkedList<Record> records) {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("There were no CSTaR records generated. Perhaps the parameters are wrong.");
        }

        String header = "# Potential Causes = " + records.get(0).getNumCauses() + "\n"
                        + "# Potential Effects = " + records.get(0).getNumEffects() + "\n" +
                        "Top Bracket (‘q’) = " + this.topBracket +
                        "\n\n";

        int numColumns = 6;

        TextTable table = new TextTable(records.size() + 1, numColumns);
        NumberFormat nf = new DecimalFormat("0.0000");
        int column = 0;

        table.setToken(0, column++, "Index");
        table.setToken(0, column++, "Cause");
        table.setToken(0, column++, "Effect");
        table.setToken(0, column++, "PI");
        table.setToken(0, column++, "Effect");

        table.setToken(0, column, "PCER");

        if (records.isEmpty()) {
            return "\nThere are no records above chance.\n";
        }

        int p = records.getLast().getNumCauses();

        for (int i = 0; i < records.size(); i++) {
            Node cause = records.get(i).getCauseNode();
            Node effect = records.get(i).getEffectNode();
            column = 0;

            table.setToken(i + 1, column++, String.valueOf(i + 1));
            table.setToken(i + 1, column++, cause.getName());
            table.setToken(i + 1, column++, effect.getName());
            table.setToken(i + 1, column++, nf.format(records.get(i).getPi()));
            table.setToken(i + 1, column++, nf.format(records.get(i).getMinBeta()));

            double pcer = Cstar.pcer(records.get(i).getPi(), (i + 1), p);
            table.setToken(i + 1, column, records.get(i).getPi() <= 0.5 ? "*" : nf.format(pcer));
        }

        return header + table;
    }

    /**
     * Retrieves a stable pattern graph using the PC algorithm.
     *
     * @param sample the dataset to use for the PC algorithm
     * @return the graph representing the stable pattern
     */
    private Graph getPatternPcStable(DataSet sample) {
        IndependenceTest test = this.test.getTest(sample, parameters);
        test.setVerbose(false);
        Pc pc = new Pc(test);
        pc.setStable(true);
        pc.setVerbose(false);
        return pc.search();
    }

    /**
     * Retrieves a pattern graph using the FGES (Fast Greedy Equivalence Search) algorithm.
     *
     * @param sample the dataset to use for the FGES algorithm
     * @return the graph representing the pattern
     */
    private Graph getPatternFges(DataSet sample) {
        Score score = this.score.getScore(sample, parameters);
        Fges fges = new Fges(score);
        fges.setVerbose(false);
        return fges.search();
    }

    /**
     * Retrieves a pattern graph using the BOSS (Bayesian Optimal Structure Search) algorithm.
     *
     * @param sample the dataset to use for the BOSS algorithm
     * @return the graph representing the pattern
     */
    private Graph getPatternBoss(DataSet sample) {
        Score score = this.score.getScore(sample, parameters);
        PermutationSearch boss = new PermutationSearch(new Boss(score));
        boss.setSeed(parameters.getLong(Params.SEED));
        return boss.search();
    }

    /**
     * Retrieves a pattern graph using the Restricted BOSS (Bayesian Optimal Structure Search) algorithm.
     *
     * @param sample the dataset to use for the Restricted BOSS algorithm
     * @param data   the dataset containing the variables for replacing the nodes in the resulting graph
     * @return the graph representing the pattern
     */
    private Graph getPatternRestrictedBoss(DataSet sample, DataSet data) {
        RestrictedBoss restrictedBoss = new RestrictedBoss(score);
        parameters.set(Params.TRIMMING_STYLE, 1);
        Graph g = restrictedBoss.search(sample, parameters);
        g = GraphUtils.replaceNodes(g, data.getVariables());
        return g;
    }

    /**
     * Saves a matrix of effects to a file.
     *
     * @param effects the matrix of effects to be saved
     * @param file    the file to save the matrix to
     */
    private void saveMatrix(double[][] effects, File file) {
        try {
            List<Node> vars = new ArrayList<>();
            for (int i = 0; i < effects[0].length; i++) vars.add(new ContinuousVariable("V" + (i + 1)));
            BoxDataSet data = new BoxDataSet(new DoubleDataBox(effects), vars);
            if (file != null) {
                PrintStream out = new PrintStream(new FileOutputStream(file));
                out.println(data);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a matrix from the specified file.
     *
     * @param file The file to load the matrix from.
     * @return The loaded matrix as a two-dimensional array of doubles.
     * @throws RuntimeException If an error occurs during the matrix loading process.
     */
    private double[][] loadMatrix(File file) {
        try {
            DataSet dataSet = SimpleDataLoader.loadContinuousData(file, "//",
                    '\"', "*", true, Delimiter.TAB, false);
            return dataSet.getDoubleData().toArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes a list of Callables that return double[][] arrays and returns a List of the results.
     *
     * @param tasks        a List of Callables that return double[][] arrays.
     * @param parallelized a boolean indicating whether to execute the tasks in parallel.
     * @return a List of double[][] arrays representing the results of the executed tasks.
     */
    private List<double[][]> runCallablesDoubleArray(List<Callable<double[][]>> tasks, boolean parallelized) {
        if (tasks.isEmpty()) return new ArrayList<>();

        List<double[][]> results = new ArrayList<>();

        int parallelism = Runtime.getRuntime().availableProcessors();
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        List<Future<double[][]>> futures = pool.invokeAll(tasks);

        for (Future<double[][]> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                TetradLogger.getInstance().log(e.getMessage());
            }
        }

        return results;
    }

    /**
     * An enumeration of the options available for determining the CPDAG used for the algorithm.
     */
    public enum CpdagAlgorithm {

        /**
         * The PC_STABLE algorithm.
         */
        PC_STABLE,

        /**
         * The FGES algorithm.
         */
        FGES,

        /**
         * The BOSS algorihtm.
         */
        BOSS,

        /**
         * The RESTRICTED_BOSS algorithm.
         */
        RESTRICTED_BOSS
    }

    /**
     * An enumeration of the methods for selecting samples from the full dataset.
     */
    public enum SampleStyle {

        /**
         * Use bootstrap.
         */
        BOOTSTRAP,

        /**
         * Use subsample.
         */
        SUBSAMPLE
    }

    /**
     * Represents a single record in the returned table for CSTaR.
     */
    public static class Record implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The cause node.
         */
        private final Node causeNode;

        /**
         * The effect node.
         */
        private final Node target;

        /**
         * The percentage of the time the predictor is a cause of the target across subsamples.
         */
        private final double pi;

        /**
         * The minimum effect size of the predictor on the target across subsamples calculated by IDA.
         */
        private final double effect;

        /**
         * The number of possible causes of the target.
         */
        private final int numCauses;

        /**
         * The number of possible effects of the target.
         */
        private final int numEffects;

        /**
         * For X->Y.
         *
         * @param predictor  X (the cause)
         * @param target     Y (the effect)
         * @param pi         The percentage of the time the predictor is a cause of the target across subsamples.
         * @param minEffect  The minimum effect size of the predictor on the target across subsamples calculated by IDA
         * @param numCauses  The number of possible causes of the target.
         * @param numEffects The nuber of possible effects of the target.
         */
        Record(Node predictor, Node target, double pi, double minEffect, int numCauses, int numEffects) {
            this.causeNode = predictor;
            this.target = target;
            this.pi = pi;
            this.effect = minEffect;
            this.numCauses = numCauses;
            this.numEffects = numEffects;
        }

        /**
         * Returns the cause node associated with this record.
         *
         * @return the cause node
         */
        public Node getCauseNode() {
            return this.causeNode;
        }

        /**
         * Retrieves the effect node of the record.
         *
         * @return The effect node.
         */
        public Node getEffectNode() {
            return this.target;
        }

        /**
         * Retrieves the value of pi.
         *
         * @return The value of pi.
         */
        public double getPi() {
            return this.pi;
        }

        /**
         * Retrieves the minimum effect size (beta) of the target in a record.
         *
         * @return The minimum effect size (beta) of the target.
         */
        double getMinBeta() {
            return this.effect;
        }

        /**
         * Retrieves the number of possible causes of the target in a record.
         *
         * @return The number of possible causes of the target.
         */
        public int getNumCauses() {
            return this.numCauses;
        }

        /**
         * Retrieves the number of possible effects of the target in a record.
         *
         * @return The number of possible effects of the target.
         */
        public int getNumEffects() {
            return this.numEffects;
        }
    }

    /**
     * A tuple.
     */
    private static class Tuple {

        /**
         * The cause node.
         */
        private final Node cause;

        /**
         * The effect node.
         */
        private final Node effect;

        /**
         * The percentage of the time the predictor is a cause of the target across subsamples.
         */
        private final double pi;

        /**
         * The minimum effect size of the predictor on the target across subsamples calculated by IDA.
         */
        private final double minBeta;

        /**
         * For X->Y.
         *
         * @param cause   X (the cause)
         * @param effect  Y (the effect)
         * @param pi      The percentage of the time the predictor is a cause of the target across subsamples.
         * @param minBeta The minimum effect size of the predictor on the target across subsamples calculated by IDA
         */
        private Tuple(Node cause, Node effect, double pi, double minBeta) {
            this.cause = cause;
            this.effect = effect;
            this.pi = pi;
            this.minBeta = minBeta;
        }

        /**
         * Retrieves the cause node of a tuple.
         *
         * @return the cause node
         */
        public Node getCauseNode() {
            return this.cause;
        }

        /**
         * Retrieves the effect node of a tuple.
         *
         * @return the effect node
         */
        public Node getEffectNode() {
            return this.effect;
        }

        /**
         * Returns the value of pi.
         *
         * @return the value of pi
         */
        public double getPi() {
            return this.pi;
        }

        /**
         * Returns the minimum effect size of the predictor on the target across subsamples calculated by IDA.
         *
         * @return the minimum effect size of the predictor on the target across subsamples
         */
        public double getMinBeta() {
            return this.minBeta;
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}
