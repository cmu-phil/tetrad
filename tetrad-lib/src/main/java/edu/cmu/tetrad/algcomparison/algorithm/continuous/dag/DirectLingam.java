package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
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
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Direct LiNGAM.
 *
 * @author bryanandrews
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Direct-LiNGAM",
        command = "direct-lingam",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class DirectLingam extends AbstractBootstrapAlgorithm implements Algorithm, UsesScoreWrapper, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The score.
     */
    private ScoreWrapper score;

    /**
     * <p>Constructor for DirectLingam.</p>
     */
    public DirectLingam() {
        // Used in reflection; do not delete.
    }

    /**
     * <p>Constructor for DirectLingam.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public DirectLingam(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     */
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet)) {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        DataSet data = SimpleDataLoader.getContinuousDataSet(dataModel);
        Score score = this.score.getScore(dataModel, parameters);

        edu.cmu.tetrad.search.DirectLingam search = new edu.cmu.tetrad.search.DirectLingam(data, score);
        Graph graph = search.search();
        TetradLogger.getInstance().forceLogMessage(graph.toString());
        LogUtilsSearch.stampWithBic(graph, dataModel);
        return graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * <p>getDescription.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDescription() {
        return "Direct-LiNGAM (Direct Linear Non-Gaussian Acyclic Model";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
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
}
