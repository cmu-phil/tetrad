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
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * EXPERIMENTAL: BFCI with a per-removal RICF-BIC score check ("tests propose, scores dispose"). Identical to
 * {@link Bfci} except that on the gated path every candidate edge removal (single or joint) must, in addition to
 * passing the PAG legality certificate, not decrease the BIC of the RICF-fitted Gaussian MAG implied by the
 * candidate graph. See {@link edu.cmu.tetrad.search.BfciScoreCheck} for the semantics, the fail-soft contract, and
 * the invariance argument for scoring the Zhang MAG.
 * <p>
 * The score check's penalty discount is its own parameter, {@link Params#SCORE_CHECK_PENALTY_DISCOUNT} (default 1,
 * the classical BIC test), independent of {@link Params#PENALTY_DISCOUNT}, which the initializer score wrapper
 * (e.g., SEM BIC for BOSS) reads. The two can therefore be set separately from the interface and from
 * algcomparison.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "BFCI-Score-Check",
//        command = "bfci-score-check",
//        algoType = AlgType.allow_latent_common_causes
//)
//@Bootstrapping
//@Experimental
public class BfciScoreCheck extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper,
        TakesIndependenceWrapper, AcceptsKnowledge, ReturnsBootstrapGraphs,
        TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Whether to exclude selection bias.
     */
    private boolean excludeSelectionBias = false;

    /**
     * No-arg constructor. Used for reflection; do not delete.
     */
    public BfciScoreCheck() {
        // Used for reflection; do not delete.
    }

    /**
     * Constructs a new BFCI-Score-Check algorithm using the given test and score.
     *
     * @param test  the independence test to use
     * @param score the score to use
     */
    public BfciScoreCheck(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Runs the search algorithm using the given dataset and parameters and returns the resulting graph.
     *
     * @param dataModel  the data model to run the search on
     * @param parameters the parameters used for the search algorithm
     * @return the graph resulting from the search algorithm
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG), knowledge);
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        edu.cmu.tetrad.search.BfciScoreCheck search = new edu.cmu.tetrad.search.BfciScoreCheck(
                this.test.getTest(dataModel, parameters), this.score.getScore(dataModel, parameters));

        boolean parallelized = parameters.getBoolean(Params.PARALLELIZED);

        search.setBossUseBes(parameters.getBoolean(Params.USE_BES));
        search.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
        search.setMaxPossibleDsepPathLength(parameters.getInt(Params.MAX_POSSIBLE_SEP_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setParallelized(parallelized);
        search.setNumThreads(parallelized ? 1 : Runtime.getRuntime().availableProcessors());
        search.setDoLegalityGating(parameters.getBoolean(Params.DO_LEGALITY_GATING));
        search.setUseMaxP(parameters.getBoolean(Params.USE_MAX_P_HEURISTIC));
        search.setExcludeSelectionBias(parameters.getBoolean(Params.EXCLUDE_SELECTION_BIAS));
        search.setLvHeuristicOnly(parameters.getBoolean(Params.LV_HEURISTIC_ONLY));
        search.setUsePossibleDsep(parameters.getBoolean(Params.DO_POSSIBLE_DSEP));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        // Score check configuration. The gate's penalty discount is its own parameter (default 1, the
        // classical BIC test), decoupled from the initializer score's penaltyDiscount.
        search.setScoreCheckPenaltyDiscount(parameters.getDouble(Params.SCORE_CHECK_PENALTY_DISCOUNT));

        // Hand the score check its covariance directly when the data model provides one; otherwise the search
        // class resolves it lazily from the test or its data.
        if (dataModel instanceof ICovarianceMatrix icm) {
            search.setCovarianceMatrix(icm);
        }

        search.setKnowledge(knowledge);

        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));

        return search.search();
    }

    /**
     * Retrieves the comparison graph generated by applying the DAG-to-PAG transformation to the given true directed
     * graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph generated by applying the DAG-to-PAG transformation.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph, excludeSelectionBias);
    }

    /**
     * Returns a description of the algorithm using the description of its independence test and score.
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "BFCI-Score-Check (BFCI with per-removal RICF-BIC score check) using " + this.test.getDescription()
                + " and " + this.score.getDescription();
    }

    /**
     * Retrieves the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return the data type required by the search algorithm
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves the list of parameters used for the algorithm.
     *
     * @return the list of parameters used for the algorithm
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        params.add(Params.USE_BES);
        params.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        params.add(Params.MAX_POSSIBLE_SEP_PATH_LENGTH);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DEPTH);
        params.add(Params.TIME_LAG);
        params.add(Params.DO_LEGALITY_GATING);
        params.add(Params.USE_MAX_P_HEURISTIC);
        params.add(Params.EXCLUDE_SELECTION_BIAS);
        params.add(Params.LV_HEURISTIC_ONLY);
        params.add(Params.DO_POSSIBLE_DSEP);
        params.add(Params.SCORE_CHECK_PENALTY_DISCOUNT);
        params.add(Params.PARALLELIZED);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);

        return params;
    }


    /**
     * Retrieves the knowledge associated with the algorithm.
     *
     * @return the knowledge associated with the algorithm
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with the algorithm.
     *
     * @param knowledge a knowledge object
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the IndependenceWrapper associated with this algorithm.
     *
     * @return the IndependenceWrapper object
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the IndependenceWrapper object for this algorithm.
     *
     * @param test the IndependenceWrapper object to set
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Retrieves the ScoreWrapper associated with this algorithm.
     *
     * @return The ScoreWrapper object.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for this algorithm.
     *
     * @param score the score wrapper to set
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Sets whether selection bias should be excluded during the search process.
     *
     * @param excludeSelectionBias True to exclude selection bias, false otherwise.
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }
}
