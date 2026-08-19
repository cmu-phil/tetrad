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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * LV-BOSS: a latent-variable search that uses no independence tests. A BOSS CPDAG is projected to a PAG and then
 * to its Zhang MAG, and that MAG is improved by steepest-ascent greedy search under the Gaussian MAG BIC, moving
 * only among legal MAGs, with deletion, reversal, type change (directed to bidirected and back), and addition as
 * moves. The final MAG is projected back to a PAG.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.LvBoss
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "LV-BOSS",
        command = "lv-boss",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
@Experimental
public class LvBoss extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper, AcceptsKnowledge,
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
     * Used for reflection; do not delete.
     */
    public LvBoss() {
    }

    /**
     * Constructs an LV-BOSS algorithm with the given score.
     *
     * @param score The score to use.
     */
    public LvBoss(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Runs the search.
     *
     * @param dataModel  The data model.
     * @param parameters The parameters.
     * @return The estimated PAG.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG), knowledge);

            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }

            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        Score score = this.score.getScore(dataModel, parameters);

        edu.cmu.tetrad.search.LvBoss search = new edu.cmu.tetrad.search.LvBoss(score);

        // BOSS seed.
        search.setUseBes(parameters.getBoolean(Params.USE_BES));
        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        search.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));

        // MAG-space search. The MAG score's penalty discount is its own parameter, independent of the
        // penaltyDiscount the BOSS score wrapper reads.
        search.setMagPenaltyDiscount(parameters.getDouble(Params.SCORE_CHECK_PENALTY_DISCOUNT));
        search.setAllowAdditions(parameters.getBoolean(Params.LV_BOSS_ALLOW_ADDITIONS));
        search.setLookaheadDepth(parameters.getInt(Params.LV_BOSS_LOOKAHEAD_DEPTH));
//        search.setMaxLookaheadFirstMoves(150);
        search.setExcludeSelectionBias(parameters.getBoolean(Params.EXCLUDE_SELECTION_BIAS));

        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return search.search();
    }

    /**
     * Returns the comparison graph, the PAG of the true DAG.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph, false);
    }

    /**
     * Returns a short description of this algorithm.
     *
     * @return The description.
     */
    @Override
    public String getDescription() {
        return "LV-BOSS (BOSS seed, greedy MAG-BIC search in MAG space, no tests) using "
               + this.score.getDescription();
    }

    /**
     * Returns the data type required.
     *
     * @return The data type.
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * Returns the parameters used.
     *
     * @return The parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        // BOSS
        params.add(Params.USE_BES);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.NUM_STARTS);

        // LV-BOSS
        params.add(Params.SCORE_CHECK_PENALTY_DISCOUNT);
        params.add(Params.LV_BOSS_ALLOW_ADDITIONS);
        params.add(Params.LV_BOSS_LOOKAHEAD_DEPTH);
        params.add(Params.EXCLUDE_SELECTION_BIAS);

        // General
        params.add(Params.TIME_LAG);
        params.add(Params.VERBOSE);

        return params;
    }

    /**
     * Returns the knowledge.
     *
     * @return The knowledge.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge.
     *
     * @param knowledge The knowledge.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the score wrapper.
     *
     * @return The score wrapper.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper.
     *
     * @param score The score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}
