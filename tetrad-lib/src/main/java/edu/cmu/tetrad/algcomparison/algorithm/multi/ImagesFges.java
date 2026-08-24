///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.annotation.Bootstrapping;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractMultiBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.MultiDataSetScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
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
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "IMaGES-FGES",
//        command = "images-fges",
//        algoType = AlgType.forbid_latent_common_causes,
//        dataType = DataType.All
//)
@Bootstrapping
public class ImagesFges extends AbstractMultiBootstrapAlgorithm implements MultiDataSetAlgorithm, AcceptsKnowledge, TakesScoreWrapper {

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
    public ImagesFges(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * <p>Constructor for Images.</p>
     */
    public ImagesFges() {
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
    protected Graph runSearch(List<DataModel> dataSets, Parameters parameters) throws InterruptedException {
        int meta = parameters.getInt(Params.IMAGES_META_ALG);

        List<DataModel> _dataSets = new ArrayList<>();

        // Same discipline as Images: the base (unlagged) knowledge is the algorithm's own knowledge if the
        // user supplied any, otherwise base-variable (no lag suffix) knowledge carried by the data sets. The
        // field this.knowledge is never overwritten here - createLagData rejects previously lagged knowledge,
        // so overwriting it broke the second data set (and every bootstrap replicate after the first).
        Knowledge baseKnowledge = this.knowledge;

        if (baseKnowledge == null || baseKnowledge.isEmpty()) {
            for (DataModel dataSet : dataSets) {
                Knowledge fromData = dataSet.getKnowledge();
                if (fromData == null || fromData.isEmpty()) continue;
                boolean baseOnly = true;
                for (String name : fromData.getVariables()) {
                    if (name.contains(":")) {
                        baseOnly = false;
                        break;
                    }
                }
                if (baseOnly) {
                    baseKnowledge = fromData;
                    break;
                }
            }
        }

        if (baseKnowledge == null) {
            baseKnowledge = new Knowledge();
        }

        Knowledge searchKnowledge = baseKnowledge;

        if (parameters.getInt(Params.TIME_LAG) > 0) {
            for (DataModel dataSet : dataSets) {
                DataSet timeSeries = TsUtils.createLagData((DataSet) dataSet, parameters.getInt(Params.TIME_LAG), baseKnowledge);
                if (dataSet.getName() != null) {
                    timeSeries.setName(dataSet.getName());
                }
                _dataSets.add(timeSeries);
                searchKnowledge = timeSeries.getKnowledge();
            }

            dataSets = _dataSets;
        }

        List<Score> scores;

        // As in Images: a MultiDataSetScoreWrapper coordinates data-dependent representation choices across
        // the data sets so that every data set scores the identical parameterization, which the summed
        // IMaGES score requires.
        if (score instanceof MultiDataSetScoreWrapper multiWrapper) {
            scores = multiWrapper.getScores(dataSets, parameters);
        } else {
            scores = new ArrayList<>();
            for (DataModel dataModel : dataSets) {
                Score s = score.getScore(dataModel, parameters);
                scores.add(s);
            }
        }

        ImagesScore score = new ImagesScore(scores);

        if (meta == 1) {
            edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(score);
            search.setKnowledge(searchKnowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        } else if (meta == 2) {
            PermutationSearch search = new PermutationSearch(new Boss(score));
            search.setSeed(parameters.getLong(Params.SEED));
            search.setKnowledge(searchKnowledge);
            search.setReplicatingGraph(parameters.getBoolean(Params.TIME_LAG_REPLICATING_GRAPH));
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
    protected Graph runSearch(DataModel dataSet, Parameters parameters) throws InterruptedException {
        return runSearch(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
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
        return "IMaGES-FGES";
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
        parameters.addAll((new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss()).getParameters());
        parameters.add(Params.RANDOM_SELECTION_SIZE);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.TIME_LAG_REPLICATING_GRAPH);
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
}

