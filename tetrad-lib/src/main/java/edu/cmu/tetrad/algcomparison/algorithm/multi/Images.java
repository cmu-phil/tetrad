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
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

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
     * Searches for a graph based on the given data sets and parameters.
     *
     * @param dataSets   The data sets to search on.
     * @param parameters The parameters to use for the search.
     * @return A Graph object representing the search result.
     */
    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
        int meta = parameters.getInt(Params.IMAGES_META_ALG);

        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
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
        } else {
            Images imagesSemBic = new Images();

            List<DataSet> dataSets2 = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                dataSets2.add((DataSet) dataModel);
            }

            List<DataSet> _dataSets = new ArrayList<>();

            if (parameters.getInt(Params.TIME_LAG) > 0) {
                for (DataSet dataSet : dataSets2) {
                    DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
                    if (dataSet.getName() != null) {
                        timeSeries.setName(dataSet.getName());
                    }
                    _dataSets.add(timeSeries);
                }

                dataSets2 = _dataSets;
            }

            GeneralResamplingTest search = new GeneralResamplingTest(
                    dataSets2,
                    imagesSemBic,
                    knowledge, parameters
            );
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setScoreWrapper(score);
            return search.search();
        }
    }

    /**
     * Searches for a graph based on the given data set and parameters.
     *
     * @param dataSet    The data set to run the search on.
     * @param parameters The parameters of the search.
     * @return A Graph object representing the search result.
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            return search(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
        } else {
            Images images = new Images();

            List<DataSet> dataSets = Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet));
            GeneralResamplingTest search = new GeneralResamplingTest(dataSets,
                    images,
                    knowledge, parameters);

            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setScoreWrapper(score);
            return search.search();
        }
    }

    /**
     * Retrieves the comparison graph based on the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Retrieves the description of the algorithm.
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "IMaGES";
    }

    /**
     * Retrieves the data type of the algorithm.
     *
     * @return The data type of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return DataType.All;
    }

    /**
     * Retrieves the parameters required by the Images algorithm.
     *
     * @return A list of strings representing the parameters required by the Images algorithm.
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
     * Retrieves the knowledge associated with this instance.
     *
     * @return The Knowledge object representing the knowledge associated with this instance.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with this instance.
     *
     * @param knowledge The knowledge object to be set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the score wrapper associated with this instance.
     *
     * @return The ScoreWrapper object representing the score wrapper associated with this instance.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper associated with this instance.
     *
     * @param score The ScoreWrapper object representing the score wrapper to be set.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Sets the IndependenceWrapper for the algorithm.
     *
     * @param test The IndependenceWrapper to be set.
     */
    @Override
    public void setIndTestWrapper(IndependenceWrapper test) {
        // Not used.
    }
}
