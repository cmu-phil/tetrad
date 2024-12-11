package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * <p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many datasets should be taken at a time
 * (randomly). This cannot given multiple values.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "IMaGES",
        command = "images",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.All
)
@Bootstrapping
public class Images implements MultiDataSetAlgorithm, HasKnowledge, UsesScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The score to use.
     */
    private ScoreWrapper score = new SemBicScore();

    /**
     * <p>Constructor for Images.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public Images(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * <p>Constructor for Images.</p>
     */
    public Images() {
    }

    /**
     * Searches for a graph using the given data sets and parameters.
     *
     * @param dataSets   The data sets to search on.
     * @param parameters The parameters for the search.
     * @return The resulting graph.
     * @throws IllegalArgumentException If the meta option is unrecognized.
     */
    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) throws InterruptedException {
        int meta = parameters.getInt(Params.IMAGES_META_ALG);

        List<DataModel> _dataSets = new ArrayList<>();

        if (parameters.getInt(Params.TIME_LAG) > 0) {
            for (DataModel dataSet : dataSets) {
                DataSet timeSeries = TsUtils.createLagData((DataSet) dataSet, parameters.getInt(Params.TIME_LAG));
                if (dataSet.getName() != null) {
                    timeSeries.setName(dataSet.getName());
                }
                _dataSets.add(timeSeries);
            }

            dataSets = _dataSets;
        }

        List<Score> scores = new ArrayList<>();

        for (DataModel dataModel : dataSets) {
            Score s = score.getScore(dataModel, parameters);
            scores.add(s);
        }

        ImagesScore score = new ImagesScore(scores);

        if (meta == 1) {
            edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(score);
            search.setKnowledge(this.knowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        } else if (meta == 2) {
            PermutationSearch search = new PermutationSearch(new Boss(score));
            search.setKnowledge(this.knowledge);
            return search.search();
        } else {
            throw new IllegalArgumentException("Unrecognized meta option: " + meta);
        }
    }

    /**
     * Searches for a graph using the given data set and parameters.
     *
     * @param dataSet    The data set to run the search on.
     * @param parameters The parameters of the search.
     * @return The resulting graph.
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) throws InterruptedException {
        return search(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
    }

    /**
     * Returns a comparison graph based on the true directed graph, if there is one.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns the description of this method.
     *
     * @return the description of this method.
     */
    @Override
    public String getDescription() {
        return "IMaGES";
    }

    /**
     * Returns the type of the data set.
     *
     * @return the type of the data set
     */
    @Override
    public DataType getDataType() {
        return DataType.All;
    }

    /**
     * Retrieves the list of parameters required for the algorithm.
     *
     * @return The list of parameters required for the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();
        parameters.addAll(new SemBicScore().getParameters());

        parameters.addAll((new Fges()).getParameters());
        parameters.add(Params.RANDOM_SELECTION_SIZE);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.IMAGES_META_ALG);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Retrieves the knowledge of the current instance.
     *
     * @return The knowledge of the current instance.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object for this instance.
     *
     * @param knowledge The knowledge object to be set. Cannot be null.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the score wrapper object.
     *
     * @return The score wrapper object.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for the algorithm.
     *
     * @param score The score wrapper to be set.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Sets the IndependenceWrapper for this algorithm.
     *
     * @param test The IndependenceWrapper object to be set. Cannot be null.
     */
    @Override
    public void setIndTestWrapper(IndependenceWrapper test) {
        // Not used.
    }
}
