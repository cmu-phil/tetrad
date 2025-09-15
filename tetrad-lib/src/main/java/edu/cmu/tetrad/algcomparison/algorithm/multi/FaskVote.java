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

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.continuous.dag.Fask;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the MultiFask algorithm for continuous variables.
 * <p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many datasets should be taken at a time
 * (randomly). This cannot given multiple values.
 *
 * @author mglymour
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK-Vote",
        command = "fask-vote",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
@Experimental
public class FaskVote implements MultiDataSetAlgorithm, HasKnowledge, TakesScoreWrapper, TakesIndependenceWrapper {

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

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * <p>Constructor for FaskVote.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public FaskVote(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * <p>Constructor for FaskVote.</p>
     */
    public FaskVote() {

    }

    /**
     * <p>Constructor for FaskVote.</p>
     *
     * @param test  a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public FaskVote(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) throws InterruptedException {
        for (DataModel d : dataSets) {
            if (((DataSet) d).existsMissingValue()) {
                throw new IllegalArgumentException("Please remove or impute missing values.");
            }
        }

        List<DataSet> _dataSets = new ArrayList<>();
        for (DataModel d : dataSets) {
            _dataSets.add((DataSet) d);
        }

        edu.cmu.tetrad.search.work_in_progress.FaskVote search = new edu.cmu.tetrad.search.work_in_progress.FaskVote(_dataSets, this.score, this.test);

        search.setKnowledge(this.knowledge);
        return search.search(parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) throws InterruptedException {
        return search(Collections.singletonList(SimpleDataLoader.getContinuousDataSet(dataSet)), parameters);
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
        return "FASK-Vote";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new Images().getParameters();
        parameters.addAll(new Fask().getParameters());

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
    public void setIndTestWrapper(IndependenceWrapper test) {
        this.test = test;
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

