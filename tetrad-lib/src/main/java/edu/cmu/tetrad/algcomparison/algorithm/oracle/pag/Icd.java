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

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * The Iterative Causal Discovery (ICD) algorithm.
 *
 * <p>ICD is an anytime algorithm that learns a Partial Ancestral Graph (PAG) iteratively,
 * increasing the conditioning-set size r by 1 each round. At each round it produces an
 * r-representing PAG, so the caller may interrupt the search early and still obtain a
 * meaningful (if less refined) result.
 *
 * <p>Reference: Rohekar et al., "Iterative Causal Discovery in the Possible Presence of
 * Latent Confounders and Selection Bias", NeurIPS 2021.
 *
 * @author josephramsey (wrapper)
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "ICD",
//        command = "icd",
//        algoType = AlgType.allow_latent_common_causes,
//        dataType = DataType.All
//)
//@Bootstrapping
public class Icd extends AbstractBootstrapAlgorithm implements Algorithm, AcceptsKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix,
        LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * No-arg constructor required by the framework.
     */
    public Icd() {
    }

    /**
     * Constructor.
     *
     * @param test the independence wrapper to use.
     */
    public Icd(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Runs the ICD search and returns the resulting PAG.
     *
     * @param dataModel  the data model containing the dataset.
     * @param parameters the parameters for the search algorithm.
     * @return the resulting PAG.
     * @throws InterruptedException if the search thread is interrupted.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        IndependenceTest independenceTest = getIndependenceWrapper().getTest(dataModel, parameters);
        independenceTest = new CachingIndependenceTest(independenceTest);

        edu.cmu.tetrad.search.Icd search = new edu.cmu.tetrad.search.Icd(independenceTest);
        search.setKnowledge(this.knowledge);
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setSelectionBias(!parameters.getBoolean(Params.EXCLUDE_SELECTION_BIAS));
//        search.setTailCompleteness(parameters.getBoolean(Params.TAIL_COMPLETENESS));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return search.search();
    }

    /**
     * Returns the comparison graph based on the true directed graph.
     *
     * @param graph the true directed graph.
     * @return the PAG corresponding to the true graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph, false);
    }

    /**
     * Returns a short description of this algorithm.
     *
     * @return the description string.
     */
    @Override
    public String getDescription() {
        return "ICD (Iterative Causal Discovery) using " + this.test.getDescription();
    }

    /**
     * Returns the data type that this algorithm requires.
     *
     * @return the data type.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Returns the list of parameter keys used by this algorithm.
     *
     * @return the parameter list.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.EXCLUDE_SELECTION_BIAS);
//        parameters.add(Params.TAIL_COMPLETENESS);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Returns the knowledge object.
     *
     * @return the knowledge.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object.
     *
     * @param knowledge the knowledge to set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the independence wrapper.
     *
     * @return the independence wrapper.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the independence wrapper.
     *
     * @param test the independence wrapper to set.
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}
