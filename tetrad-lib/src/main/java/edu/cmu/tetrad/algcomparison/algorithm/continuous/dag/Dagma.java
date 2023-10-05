package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * DAGMA.
 *
 * @author bryanandrews
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "DAGMA",
        command = "dagma",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class Dagma implements Algorithm, ReturnsBootstrapGraphs {

    private static final long serialVersionUID = 23L;
    private List<Graph> bootstrapGraphs = new ArrayList<>();

    public Dagma() {}

    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            DataSet data = SimpleDataLoader.getContinuousDataSet(dataSet);

            edu.cmu.tetrad.search.Dagma search = new edu.cmu.tetrad.search.Dagma(data);
            search.setLambda1(parameters.getDouble(Params.LAMBDA1));
            search.setWThreshold(parameters.getDouble(Params.W_THRESHOLD));
            search.setCpdag(parameters.getBoolean(Params.CPDAG));
            Graph graph = search.search();
            TetradLogger.getInstance().forceLogMessage(graph.toString());

            return graph;
        } else {
            Dagma algorithm = new Dagma();

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm,
                    parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE),
                    parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    public String getDescription() {
        return "DAGMA (DAGs via M-matrices for Acyclicity)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.VERBOSE);
        parameters.add(Params.LAMBDA1);
        parameters.add(Params.W_THRESHOLD);
        parameters.add(Params.CPDAG);
        return parameters;
    }

    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }

}