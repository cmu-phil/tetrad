package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.Params.*;

/**
 * FASK algorithm.
 */
@Bootstrapping
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK",
        command = "fask",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
public class Fask extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, TakesScoreWrapper,
        TakesExternalGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The algorithm.
     */
    private Algorithm algorithm;

    /**
     * <p>Constructor for Fask.</p>
     */
    public Fask() {
        // Don't delete.
    }

    /**
     * Constructs a new Fask object with the given ScoreWrapper.
     *
     * @param score the ScoreWrapper object to use
     */
    public Fask(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Runs the Fask search algorithm on the given data model with the specified parameters.
     *
     * @param dataModel  the data model to run the search on
     * @param parameters the parameters for the search
     * @return the resulting graph from the search
     * @throws IllegalStateException    if the data model is not a DataSet or if there are missing values
     * @throws IllegalArgumentException if there are missing values in the data set
     * @throws InterruptedException if any
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalStateException("Expecting a dataset.");
        }

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (Double.isNaN(dataSet.getDouble(i, j))) {
                    throw new IllegalArgumentException("Please remove or impute missing values.");
                }
            }
        }

        edu.cmu.tetrad.search.Fask search = new edu.cmu.tetrad.search.Fask(dataSet, this.score.getScore(dataSet, parameters));

        int lrRule = parameters.getInt(FASK_LEFT_RIGHT_RULE);

        if (lrRule == 1) {
            search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.FASK1);
        } else if (lrRule == 2) {
            search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.FASK2);
        } else if (lrRule == 3) {
            search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.RSKEW);
        } else if (lrRule == 4) {
            search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.SKEW);
        } else if (lrRule == 5) {
            search.setLeftRight(edu.cmu.tetrad.search.Fask.LeftRight.TANH);
        } else {
            throw new IllegalStateException("Unconfigured left right rule index: " + lrRule);
        }

        search.setDepth(parameters.getInt(DEPTH));
        search.setAlpha(parameters.getDouble(ALPHA));
        search.setExtraEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
        search.setDelta(parameters.getDouble(FASK_DELTA));
        search.setUseFasAdjacencies(true);
        search.setUseSkewAdjacencies(true);

        if (algorithm != null) {
            search.setExternalGraph(algorithm.search(dataSet, parameters));
        }

        search.setKnowledge(this.knowledge);
        return search.search();
    }

    /**
     * Returns a comparison graph based on the true directed graph, if there is one.
     *
     * @param graph The true directed graph, if there is one.
     * @return A comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns a short, one-line description of the FASK algorithm. This description will be printed in the report.
     *
     * @return A short description of the FASK algorithm.
     * @throws IllegalStateException if the FASK algorithm is not initialized with either a test or an algorithm.
     */
    @Override
    public String getDescription() {
        return "FASK using " + this.score.getDescription();
    }

    /**
     * Retrieves the data type of the dataset.
     *
     * @return The data type of the dataset.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the list of parameter names that are used by the algorithm. These parameters are looked up in the
     * ParamMap, so if they are not already defined, they will need to be defined there.
     *
     * @return The list of parameter names used by the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (this.algorithm != null) {
            parameters.addAll(this.algorithm.getParameters());
        }

        parameters.add(DEPTH);
        parameters.add(SKEW_EDGE_THRESHOLD);
        parameters.add(ALPHA);
        parameters.add(FASK_DELTA);
        parameters.add(FASK_LEFT_RIGHT_RULE);

        return parameters;
    }

    /**
     * Retrieves the knowledge associated with this object.
     *
     * @return The knowledge.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with this object.
     *
     * @param knowledge The knowledge object to be set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the external graph to be used by the algorithm.
     *
     * @param algorithm The algorithm object.
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Retrieves the ScoreWrapper object associated with this class.
     *
     * @return The ScoreWrapper object.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for the object.
     *
     * @param score the score wrapper to be set.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}
