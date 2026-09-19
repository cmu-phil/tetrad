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
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the pooled-FASK multi-dataset algorithm for continuous variables. IMaGES supplies
 * the common adjacency structure; each adjacency is oriented by the sign of the FASK
 * left-right statistic pooled across the datasets (sample-size weighted by default),
 * rather than by voting on per-dataset orientations as FASK-Vote does.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK-Pool",
        command = "fask-pool",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Experimental
public class FaskPool implements MultiDataSetAlgorithm, AcceptsKnowledge, TakesScoreWrapper {

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
     * <p>Constructor for FaskPool.</p>
     */
    public FaskPool() {

    }

    /**
     * <p>Constructor for FaskPool.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public FaskPool(ScoreWrapper score) {
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

        edu.cmu.tetrad.search.FaskPool search = new edu.cmu.tetrad.search.FaskPool(_dataSets, this.score);

        int adjacency = parameters.getInt(Params.FASK_POOL_ADJACENCY);

        switch (adjacency) {
            case 1 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.FaskPool.AdjacencyMethod.IMAGES);
            case 2 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.FaskPool.AdjacencyMethod.POOLED_FAS);
            case 3 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.FaskPool.AdjacencyMethod.MG_FAS);
            case 4 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.FaskPool.AdjacencyMethod.MG_LING);
            case 5 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.FaskPool.AdjacencyMethod.MG_FAS_INTERSECT_LING);
            default -> throw new IllegalStateException("Unconfigured adjacency method (1-5): " + adjacency);
        }

        search.setFasAlpha(parameters.getDouble(Params.ALPHA));
        search.setFasDepth(parameters.getInt(Params.DEPTH));
        search.setLingThreshold(parameters.getDouble(Params.THRESHOLD_B));
        search.setFastIcaMaxIter(parameters.getInt(Params.FAST_ICA_MAX_ITER));
        search.setFastIcaTolerance(parameters.getDouble(Params.FAST_ICA_TOLERANCE));
        search.setFastIcaA(parameters.getDouble(Params.FAST_ICA_A));

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
        return "FASK-Pool";
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
     *
     * <p>Returns the IMaGES parameters, minus OUTPUT_CPDAG: FASK-Pool uses only the
     * adjacencies of the adjacency-stage graph and re-orients every edge from the
     * pooled left-right statistic, so whether BOSS outputs a CPDAG or a DAG is
     * irrelevant. FASK_POOL_ADJACENCY chooses the adjacency search (1 = IMaGES,
     * 2 = pooled FAS, 3 = MG-FAS, 4 = MG-LiNG, 5 = intersection of 3 and 4); ALPHA and
     * DEPTH govern the FAS-style tests, and THRESHOLD_B with the FastICA parameters
     * governs the LiNG stage. No other FASK single-dataset parameters are listed
     * because there is no skew-adjacency stage and no two-cycle detection in this
     * setting.</p>
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new Images().getParameters();
        parameters.remove(Params.OUTPUT_CPDAG);

        parameters.add(Params.FASK_POOL_ADJACENCY);
        parameters.add(Params.ALPHA);
        parameters.add(Params.DEPTH);
        parameters.add(Params.THRESHOLD_B);
        parameters.add(Params.FAST_ICA_MAX_ITER);
        parameters.add(Params.FAST_ICA_TOLERANCE);
        parameters.add(Params.FAST_ICA_A);

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
