package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Cstar.CpdagAlgorithm;
import edu.cmu.tetrad.search.Cstar.SampleStyle;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>Cstar class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CStaR",
        command = "cstar",
        algoType = AlgType.forbid_latent_common_causes
)
public class Cstar implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The records.
     */
    private LinkedList<edu.cmu.tetrad.search.Cstar.Record> records;

    // Don't delete.

    /**
     * <p>Constructor for Cstar.</p>
     */
    public Cstar() {
    }

    /**
     * <p>Constructor for Cstar.</p>
     *
     * @param test  a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public Cstar(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        System.out.println("# Available Processors = " + Runtime.getRuntime().availableProcessors());
        System.out.println("Parallelized = " + parameters.getBoolean("parallelized"));

        edu.cmu.tetrad.search.Cstar cStaR = new edu.cmu.tetrad.search.Cstar(test, score, parameters);

        CpdagAlgorithm algorithm = switch (parameters.getInt(Params.CSTAR_CPDAG_ALGORITHM)) {
            case 1 -> CpdagAlgorithm.PC_STABLE;
            case 2 -> CpdagAlgorithm.FGES;
            case 3 -> CpdagAlgorithm.BOSS;
            case 4 -> CpdagAlgorithm.RESTRICTED_BOSS;
            default ->
                    throw new IllegalArgumentException("Unknown CPDAG algorithm: " + parameters.getInt(Params.CSTAR_CPDAG_ALGORITHM));
        };

        SampleStyle sampleStyle = switch (parameters.getInt(Params.SAMPLE_STYLE)) {
            case 1 -> SampleStyle.SUBSAMPLE;
            case 2 -> SampleStyle.BOOTSTRAP;
            default ->
                    throw new IllegalArgumentException("Unknown sample style: " + parameters.getInt(Params.SAMPLE_STYLE));
        };

        int topBracket = parameters.getInt(Params.TOP_BRACKET);

        cStaR.setParallelized(parameters.getBoolean(Params.PARALLELIZED));
        cStaR.setNumSubsamples(parameters.getInt(Params.NUM_SUBSAMPLES));
        cStaR.setSelectionAlpha(parameters.getDouble(Params.SELECTION_MIN_EFFECT));
        cStaR.setCpdagAlgorithm(algorithm);
        cStaR.setSampleStyle(sampleStyle);
        cStaR.setVerbose(parameters.getBoolean(Params.VERBOSE));

        List<Node> possibleEffects = new ArrayList<>();

        String targetNames = parameters.getString(Params.TARGETS);

        if (targetNames.trim().equalsIgnoreCase("")) {
            throw new IllegalStateException("Please specify target name(s).");
        }

        if (targetNames.trim().equalsIgnoreCase("all")) {
            for (String name : dataSet.getVariableNames()) {
                possibleEffects.add(dataSet.getVariable(name));
            }
        } else {
            String string = parameters.getString(Params.TARGETS);
            String[] _targets;

            if (string.contains(",")) {
                _targets = string.split(",");
            } else {
                _targets = string.split(" ");
            }

            for (String _target : _targets) {
                possibleEffects.add(dataSet.getVariable(_target));
            }
        }

        List<Node> possibleCauses = new ArrayList<>(dataSet.getVariables());

        if (parameters.getBoolean(Params.REMOVE_EFFECT_NODES)) {
            possibleCauses.removeAll(possibleEffects);
        }

        if (!(dataSet instanceof DataSet)) {
            throw new IllegalArgumentException("Expecting tabular data for CStaR.");
        }

        String path = parameters.getString(Params.FILE_OUT_PATH);

        LinkedList<LinkedList<edu.cmu.tetrad.search.Cstar.Record>> allRecords
                = cStaR.getRecords((DataSet) dataSet, possibleCauses, possibleEffects, topBracket, path);

        if (allRecords.isEmpty()) {
            throw new IllegalStateException("There were no records.");
        }

        records = allRecords.getLast();

        TetradLogger.getInstance().log("CStaR Table");
        String table1 = cStaR.makeTable(edu.cmu.tetrad.search.Cstar.cStar(allRecords));
        TetradLogger.getInstance().log(table1);

        // Print table1 to file.
        File _file = new File(cStaR.getDir(), "/cstar_table.txt");
        try {
            PrintWriter writer = new PrintWriter(_file);
            writer.println(table1);
            writer.close();
        } catch (IOException e) {
            System.out.println("Error writing to file: " + _file.getAbsolutePath());
        }

        TetradLogger.getInstance().log("Files stored in : " + cStaR.getDir().getAbsolutePath());

        // This stops the program from running in R.
//        JOptionPane.showMessageDialog(null, "Files stored in : " + cStaR.getDir().getAbsolutePath());

        return cStaR.makeGraph(this.getRecords());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "CStaR";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.SELECTION_MIN_EFFECT);
        parameters.add(Params.NUM_SUBSAMPLES);
        parameters.add(Params.TARGETS);
        parameters.add(Params.TOP_BRACKET);
        parameters.add(Params.PARALLELIZED);
        parameters.add(Params.CSTAR_CPDAG_ALGORITHM);
        parameters.add(Params.FILE_OUT_PATH);
        parameters.add(Params.REMOVE_EFFECT_NODES);
        parameters.add(Params.SAMPLE_STYLE);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * <p>Getter for the field <code>records</code>.</p>
     *
     * @return a {@link java.util.LinkedList} object
     */
    public LinkedList<edu.cmu.tetrad.search.Cstar.Record> getRecords() {
        return this.records;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }
}
