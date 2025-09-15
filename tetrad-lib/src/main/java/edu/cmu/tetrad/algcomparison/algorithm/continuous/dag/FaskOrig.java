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

package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
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
 * The FaskOrig class is an implementation of the FASK-Orig algorithm for causal discovery. It searches for causal
 * relationships among variables in a dataset using the given independence test and score functions.
 * <p>
 * This is the code before cleaning it up on 2024-5-16.
 */
@Bootstrapping
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "FASK-Orig",
//        command = "fask-orig",
//        algoType = AlgType.forbid_latent_common_causes,
//        dataType = DataType.Continuous
//)
//@Experimental
public class FaskOrig extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, TakesScoreWrapper, TakesIndependenceWrapper, TakesExternalGraph {
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
     * The external graph.
     */
    private Graph externalGraph;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The algorithm.
     */
    private Algorithm algorithm;

    // Don't delete.

    /**
     * <p>Constructor for Fask.</p>
     */
    public FaskOrig() {

    }

    /**
     * <p>Constructor for Fask.</p>
     *
     * @param test  a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public FaskOrig(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    private Graph getGraph(edu.cmu.tetrad.search.FaskOrig search) throws InterruptedException {
        return search.search();
    }

    /**
     * Runs the Fask search algorithm on the given data model with the specified parameters.
     *
     * @param dataModel  the data model to run the search on
     * @param parameters the parameters for the search
     * @return the resulting graph from the search
     * @throws IllegalStateException    if the data model is not a DataSet or if there are missing values
     * @throws IllegalArgumentException if there are missing values in the data set
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

        edu.cmu.tetrad.search.FaskOrig search;

        search = new edu.cmu.tetrad.search.FaskOrig(dataSet, this.score.getScore(dataSet, parameters),
                this.test.getTest(dataSet, parameters));

        search.setDepth(parameters.getInt(DEPTH));
        search.setSkewEdgeThreshold(parameters.getDouble(SKEW_EDGE_THRESHOLD));
        search.setOrientationAlpha(parameters.getDouble(ORIENTATION_ALPHA));
        search.setTwoCycleScreeningCutoff(parameters.getDouble(TWO_CYCLE_SCREENING_THRESHOLD));
        search.setDelta(parameters.getDouble(FASK_DELTA));
        search.setEmpirical(!parameters.getBoolean(FASK_NONEMPIRICAL));

        if (this.externalGraph != null) {
            this.externalGraph = algorithm.search(dataSet, parameters);
        }

        if (this.externalGraph != null) {
            search.setExternalGraph(this.externalGraph);
        }

        int lrRule = parameters.getInt(FASK_LEFT_RIGHT_RULE);

        if (lrRule == 1) {
            search.setLeftRight(edu.cmu.tetrad.search.FaskOrig.LeftRight.FASK1);
        } else if (lrRule == 2) {
            search.setLeftRight(edu.cmu.tetrad.search.FaskOrig.LeftRight.FASK2);
        } else if (lrRule == 3) {
            search.setLeftRight(edu.cmu.tetrad.search.FaskOrig.LeftRight.RSKEW);
        } else if (lrRule == 4) {
            search.setLeftRight(edu.cmu.tetrad.search.FaskOrig.LeftRight.SKEW);
        } else if (lrRule == 5) {
            search.setLeftRight(edu.cmu.tetrad.search.FaskOrig.LeftRight.TANH);
        } else {
            throw new IllegalStateException("Unconfigured left right rule index: " + lrRule);
        }

        int adjacencyMethod = parameters.getInt(FASK_ADJACENCY_METHOD);

        if (adjacencyMethod == 1) {
            search.setAdjacencyMethod(edu.cmu.tetrad.search.FaskOrig.AdjacencyMethod.FAS_STABLE);
        } else if (adjacencyMethod == 2) {
            search.setAdjacencyMethod(edu.cmu.tetrad.search.FaskOrig.AdjacencyMethod.FGES);
        } else if (adjacencyMethod == 3) {
            search.setAdjacencyMethod(edu.cmu.tetrad.search.FaskOrig.AdjacencyMethod.EXTERNAL_GRAPH);
        } else if (adjacencyMethod == 4) {
            search.setAdjacencyMethod(edu.cmu.tetrad.search.FaskOrig.AdjacencyMethod.NONE);
        } else {
            throw new IllegalStateException("Unconfigured left right rule index: " + lrRule);
        }

        search.setKnowledge(this.knowledge);
        return getGraph(search);
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
        if (this.test != null) {
            return "FASK-Orig using " + this.test.getDescription();
        } else if (this.algorithm != null) {
            return "FASK-Orig using " + this.algorithm.getDescription();
        } else {
            throw new IllegalStateException("Need to initialize with either a test or an algorithm.");
        }
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
        parameters.add(TWO_CYCLE_SCREENING_THRESHOLD);
        parameters.add(ORIENTATION_ALPHA);
        parameters.add(FASK_DELTA);
        parameters.add(FASK_LEFT_RIGHT_RULE);
        parameters.add(FASK_ADJACENCY_METHOD);
        parameters.add(FASK_NONEMPIRICAL);
        parameters.add(VERBOSE);
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
     * Retrieves the IndependenceWrapper associated with this object.
     *
     * @return The IndependenceWrapper object.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the independence wrapper for the object.
     *
     * @param independenceWrapper the independence wrapper to be set. Must implement the {@link IndependenceWrapper}
     *                            interface.
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
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

