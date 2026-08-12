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

import edu.cmu.tetrad.algcomparison.algorithm.AbstractMultiBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
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
 * Wraps the IMaGES algorithm for continuous variables. This version uses the BOSS algorithm in place of FGES.
 * <p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many datasets should be taken at a time
 * (randomly). This cannot be given multiple values.
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
public class Images extends AbstractMultiBootstrapAlgorithm implements MultiDataSetAlgorithm, AcceptsKnowledge, TakesScoreWrapper {

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
     * <p>Constructor for ImagesBoss.</p>
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
     * The non-resampling core of the IMaGES search. Bootstrapping, when requested via
     * {@code numberResampling}, is handled by the base class, which resamples rows within each
     * data set separately and calls this method on each resampled list.
     */
    @Override
    protected Graph runSearch(List<DataModel> dataSets, Parameters parameters) {
        List<DataModel> _dataSets = new ArrayList<>();

        if (parameters.getInt(Params.TIME_LAG) > 0) {
            for (DataModel dataSet : dataSets) {
                DataSet timeSeries = TsUtils.createLagData((DataSet) dataSet, parameters.getInt(Params.TIME_LAG), knowledge);
                if (dataSet.getName() != null) {
                    timeSeries.setName(dataSet.getName());
                }
                _dataSets.add(timeSeries);
                this.knowledge = timeSeries.getKnowledge();
            }

            dataSets = _dataSets;
        }

        List<Score> scores = new ArrayList<>();

        for (DataModel dataModel : dataSets) {
            Score s = score.getScore(dataModel, parameters);
            scores.add(s);
        }

        ImagesScore score = new ImagesScore(scores);

        PermutationSearch search = new PermutationSearch(new Boss(score));
        search.setSeed(parameters.getLong(Params.SEED));
        search.setKnowledge(this.knowledge);
        try {
            return search.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Graph runSearch(DataModel dataSet, Parameters parameters) throws InterruptedException {
        return runSearch(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "IMaGES";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.All;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();
        parameters.addAll(new SemBicScore().getParameters());

        parameters.addAll((new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss()).getParameters());
        parameters.add(Params.RANDOM_SELECTION_SIZE);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.SEED);
        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
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

