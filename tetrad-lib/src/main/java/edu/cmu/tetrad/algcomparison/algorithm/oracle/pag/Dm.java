/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * Detect-Mimic-PC (DM) algorithm. This is intended to detect intermediate latent variables for Multiple Input
 * Multiple IndiCator (MIMIC) models. models.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "DM",
        command = "dm",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class Dm extends AbstractBootstrapAlgorithm implements Algorithm,
        TakesScoreWrapper, TakesIndependenceWrapper,
        HasKnowledge, ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The score to use.
     */
    private ScoreWrapper score;
    private IndependenceWrapper test;

    /**
     * <p>Constructor for DM-PC.</p>
     */
    public Dm() {
        // Used for reflection; do not delete.
    }

    /**
     * Constructor for the Dm class that initializes it with a given score wrapper.
     *
     * @param score the ScoreWrapper instance to be used by this object
     */
    public Dm(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Runs a search algorithm to find a graph structure based on a given data set and parameters.
     *
     * @param dataModel  the data set to be used for the search algorithm
     * @param parameters the parameters for the search algorithm
     * @return the graph structure found by the search algorithm
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        IndependenceTest test = this.test.getTest(dataModel, parameters);
        Score _score = this.score.getScore(dataModel, parameters);

        edu.cmu.tetrad.search.DmPcRobust search = new edu.cmu.tetrad.search.DmPcRobust(test, knowledge);
//        edu.cmu.tetrad.search.DmBossRobust search = new edu.cmu.tetrad.search.DmBossRobust(_score, knowledge);

        Graph graph;
        double fdrQ = parameters.getDouble(Params.FDR_Q);
//
//        if (fdrQ == 0.0) {
            graph = search.search();
//        } else {
//            boolean negativelyCorrelated = true;
//            boolean verbose = parameters.getBoolean(Params.VERBOSE);
//            double alpha = parameters.getDouble(Params.ALPHA);
//            graph = IndTestFdrWrapper.doFdrLoop(search, negativelyCorrelated, alpha, fdrQ, verbose);
//        }

        return graph;
    }

    /**
     * Retrieves a comparison graph by transforming a true directed graph into a partially directed graph (PAG).
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return graph;
    }

    /**
     * Returns a short, one-line description of this algorithm. The description is generated by concatenating the
     * descriptions of the test and score objects associated with this algorithm.
     *
     * @return The description of this algorithm.
     */
    @Override
    public String getDescription() {
        return "DM using " + this.test.getDescription() + " or " + this.score.getDescription();
    }

    /**
     * Retrieves the data type required by the search algorithm.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * Retrieves the list of parameters used by the algorithm.
     *
     * @return The list of parameters used by the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        params.add(Params.FDR_Q);
        params.add(Params.VERBOSE);

        return params;
    }


    /**
     * Retrieves the knowledge object associated with this method.
     *
     * @return The knowledge object.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object associated with this method.
     *
     * @param knowledge the knowledge object to be set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the current ScoreWrapper instance associated with this object.
     *
     * @return The ScoreWrapper instance used by this object.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the ScoreWrapper instance associated with this object. The ScoreWrapper
     * represents a scoring mechanism that can be used by this algorithm for evaluating
     * data models and obtaining scores based on specific parameters.
     *
     * @param score the ScoreWrapper instance to be set
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

}

