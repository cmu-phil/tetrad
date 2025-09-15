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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * BOSS (Best Order Score Search)
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "BOSS",
        command = "boss",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Boss extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper, HasKnowledge,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {
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
     * Constructs a new BOSS algorithm.
     */
    public Boss() {
        // Used in reflection; do not delete.
    }

    /**
     * Constructs a new BOSS algorithm with the given score.
     *
     * @param score the score to use
     */
    public Boss(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Runs the BOSS algorithm.
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        long seed = parameters.getLong(Params.SEED);
        parameters.set(Params.NUM_THREADS, 4);

        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataModel.getName() != null) {
                timeSeries.setName(dataModel.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        Score myScore = this.score.getScore(dataModel, parameters);

        edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(myScore);

        boss.setUseBes(parameters.getBoolean(Params.USE_BES));
        boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        boss.setNumThreads(parameters.getInt(Params.NUM_THREADS));
        boss.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        boss.setVerbose(parameters.getBoolean(Params.VERBOSE));
        PermutationSearch permutationSearch = new PermutationSearch(boss);
        permutationSearch.setKnowledge(this.knowledge);
        permutationSearch.setSeed(seed);
        try {
            Graph graph = permutationSearch.search(parameters.getBoolean(Params.OUTPUT_CPDAG));
            LogUtilsSearch.stampWithScore(graph, boss.getScore());
            LogUtilsSearch.stampWithBic(graph, dataModel);
            return graph;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the true graph if there is one.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "BOSS (Best Order Score Search) using " + this.score.getDescription();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters for the algorithm.
     */
    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Parameters
        params.add(Params.USE_BES);
        params.add(Params.NUM_STARTS);
        params.add(Params.TIME_LAG);
        params.add(Params.NUM_THREADS);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.OUTPUT_CPDAG);
        params.add(Params.SEED);
        params.add(Params.VERBOSE);
//        params.add(Params.MC_ALPHA);

        return params;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the score wrapper.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the knowledge.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the knowledge.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

}

