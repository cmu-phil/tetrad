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
     * Runs the Direct LiNGAM search algorithm on the given data model with the specified parameters.
     *
     * @param dataModel   the data model to run the search algorithm on
     * @param parameters  the parameters for the search algorithm
     * @return the resulting graph from the search algorithm
     * @throws IllegalArgumentException if the data model is not an instance of DataSet
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
     * Returns a comparison graph based on the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph, which is a new instance of EdgeListGraph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns a short, one-line description of this algorithm. This will be printed in the report.
     *
     * @return The description of the algorithm.
     */
    public String getDescription() {
        return "Direct-LiNGAM (Direct Linear Non-Gaussian Acyclic Model";
    }

    /**
     * Returns the data type of the algorithm, which can be Continuous, Discrete, Mixed, Graph, Covariance, or All.
     *
     * @return The data type of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns a list of parameters for the DirectLingam algorithm.
     *
     * @return the list of parameters
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Retrieves the ScoreWrapper object associated with this DirectLingam instance.
     *
     * @return The ScoreWrapper object.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for this DirectLingam instance.
     *
     * @param score the score wrapper to set.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}
