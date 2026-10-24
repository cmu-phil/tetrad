///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * BFCI-Bryan-Score: BFCI-Bryan (sepset re-selection under the legality gate) with a scoring step: a legal
 * candidate is committed only if the RICF Gaussian BIC of its MAG strictly improves on the committed model's,
 * following Bryan Andrews's "BFCI-Bryan with scoring" pseudocode. Continuous data only; the score must be
 * SEM BIC (the MAG score takes its penalty discount and covariance from it, so the two are on one scale). See
 * {@link edu.cmu.tetrad.search.BfciBryanScore} for the algorithm and what the score step does and does not buy.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "BFCI-Bryan-Score",
        command = "bfci-bryan-score",
        algoType = AlgType.allow_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
@Experimental
public class BfciBryanScore extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper,
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
     * Whether to exclude selection bias in the comparison graph.
     */
    private boolean excludeSelectionBias = false;

    /**
     * No-arg constructor. Used for reflection; do not delete.
     */
    public BfciBryanScore() {
        // Used for reflection; do not delete.
    }

    /**
     * Constructs a new BFCI-Bryan algorithm using the given test and score.
     *
     * @param test  the independence test to use
     * @param score the score to use
     */
    public BfciBryanScore(IndependenceWrapper test, ScoreWrapper score) {
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
        edu.cmu.tetrad.search.BfciBryanScore search = new edu.cmu.tetrad.search.BfciBryanScore(
                this.test.getTest(dataModel, parameters), this.score.getScore(dataModel, parameters));

        search.setBossUseBes(parameters.getBoolean(Params.USE_BES_DEFAULT_TRUE));
        search.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
        search.setMaxPossibleDsepPathLength(parameters.getInt(Params.MAX_POSSIBLE_SEP_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setNumThreads(Runtime.getRuntime().availableProcessors());
        search.setExcludeSelectionBias(parameters.getBoolean(Params.EXCLUDE_SELECTION_BIAS));
        search.setUsePossibleDsep(parameters.getBoolean(Params.DO_POSSIBLE_DSEP));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setLogFinalOrientations(parameters.getBoolean(Params.LOG_FINAL_ORIENTATIONS));

        search.setKnowledge(knowledge);

        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        search.setUseSaturatingStep(parameters.getBoolean(Params.USE_SATURATING_STEP));

        return search.search();
    }

    /**
     * Retrieves the comparison graph generated by applying the DAG-to-PAG transformation to the given true
     * directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
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
        return "BFCI-Bryan-Score (sepset re-selection, RICF-BIC score step) using " + this.test.getDescription()
                + " and " + this.score.getDescription();
    }

    /**
     * Retrieves the data type that the search requires.
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
     * @return the list of parameters
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        params.add(Params.USE_BES_DEFAULT_TRUE);
        params.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        params.add(Params.MAX_POSSIBLE_SEP_PATH_LENGTH);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DEPTH);
        params.add(Params.EXCLUDE_SELECTION_BIAS);
        params.add(Params.DO_POSSIBLE_DSEP);
        params.add(Params.VERBOSE);
        params.add(Params.LOG_FINAL_ORIENTATIONS);

        // Parameters
        params.add(Params.NUM_STARTS);
        params.add(Params.USE_SATURATING_STEP);

        return params;
    }

    /**
     * Retrieves the knowledge associated with the algorithm.
     *
     * @return the knowledge
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
     * Sets whether selection bias should be excluded in the comparison graph.
     *
     * @param excludeSelectionBias True to exclude selection bias, false otherwise.
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }
}
