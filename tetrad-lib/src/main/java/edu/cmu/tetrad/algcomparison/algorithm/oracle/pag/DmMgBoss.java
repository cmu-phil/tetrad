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
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Algorithm-comparison wrapper for the Murray-Watters/Glymour style Detect-Mimic
 * search, DM-MG-BOSS.
 *
 * <p>This wrapper builds the requested independence test from the supplied
 * {@link IndependenceWrapper}, runs DM-MD-BOSS, and returns the resulting graph.</p>
 *
 * <p>The underlying DM-MG search internally performs both a depth-0 PC run and a
 * full-depth PC run as part of the Murray-Watters/Glymour procedure. Accordingly,
 * no external PC-depth parameter is exposed here.</p>
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "DM-MG-BOSS",
        command = "dm-mg-boss",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class DmMgBoss extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        ReturnsBootstrapGraphs, TakesIndependenceWrapper, TakesScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence-wrapper used to construct the test.
     */
    private IndependenceWrapper test;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * Background knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs the algorithm with a default Fisher Z test.
     */
    public DmMgBoss() {
        this(new FisherZ());
    }

    /**
     * Constructs the algorithm with the supplied independence wrapper.
     *
     * @param test the independence wrapper
     */
    public DmMgBoss(IndependenceWrapper test) {
        if (test == null) {
            throw new NullPointerException("Independence wrapper must not be null.");
        }

        this.test = test;
    }

    /**
     * Runs DM-MG on the supplied data model.
     *
     * @param dataModel the data model
     * @param parameters the runtime parameters
     * @return the resulting graph
     * @throws InterruptedException never thrown directly by this implementation,
     * but retained for interface compatibility
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        IndependenceTest test = this.test.getTest(dataModel, parameters);
        test.setAlpha(parameters.getDouble(Params.ALPHA));

        Score score = this.score.getScore(dataModel, parameters);

        edu.cmu.tetrad.search.DmMgBoss search = new edu.cmu.tetrad.search.DmMgBoss(test, score);
//        search.setKnowledge(this.knowledge);

        if (knowledge != null && !knowledge.getTier(0).isEmpty() && !knowledge.getTier(1).isEmpty()) {
            List<String> _inputs = knowledge.getTier(0);
            List<String> _outputs = knowledge.getTier(1);

            List<Node> inputs = new ArrayList<>();
            List<Node> outputs = new ArrayList<>();

            for (String input : _inputs) {
                inputs.add(dataModel.getVariable(input));
            }

            for (String output : _outputs) {
                outputs.add(dataModel.getVariable(output));
            }

            search.setMeasuredInputs(inputs);
            search.setMeasuredOutputs(outputs);
        }

        return search.search();
    }

    /**
     * Returns the comparison graph for the supplied graph.
     *
     * <p>As with other CPDAG-style wrappers, the comparison graph is the CPDAG of the DAG
     * obtained from the input graph.</p>
     *
     * @param graph the graph
     * @return the comparison graph
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToCpdag(graph);
    }

    /**
     * Returns a description of this algorithm.
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "DM-MG-BOSS using " + this.test.getDescription()
                + " and " + this.score.getDescription();
    }

    /**
     * Returns the data type supported by the underlying test.
     *
     * @return the data type
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Returns the list of parameters used by this algorithm.
     *
     * @return the parameter names
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Returns the background knowledge.
     *
     * @return the background knowledge
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the background knowledge
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}