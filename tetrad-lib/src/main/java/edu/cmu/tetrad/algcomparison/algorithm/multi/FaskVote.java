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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

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
@Experimental
public class FaskVote implements MultiDataSetAlgorithm, AcceptsKnowledge, TakesScoreWrapper,
        TakesExternalGraph {

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
     * Optional algorithm supplying the external graph (in the GUI, the source graph of a parent graph box, wrapped
     * in a SingleGraphAlg). Null when no source graph is supplied, in which case the adjacency search selected by
     * FASK_POOL_ADJACENCY runs as before.
     */
    private Algorithm externalGraphAlgorithm = null;

    /**
     * <p>Constructor for FaskVote.</p>
     */
    public FaskVote() {

    }

    /**
     * <p>Constructor for FaskVote.</p>
     *
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public FaskVote(ScoreWrapper score) {
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

        edu.cmu.tetrad.search.FaskVote search = new edu.cmu.tetrad.search.FaskVote(_dataSets, this.score);

        int adjacency = parameters.getInt(Params.FASK_POOL_ADJACENCY);

        switch (adjacency) {
            case 1 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.IMAGES);
            case 2 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.POOLED_FAS);
            case 3 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.MG_FAS);
            case 4 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.MG_LING);
            case 5 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.MG_FAS_INTERSECT_LING);
            case 6 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.IMAGES_RESTRICT_MG_FAS);
            default -> throw new IllegalStateException("Unconfigured adjacency method (1-6): " + adjacency);
        }

        search.setFasAlpha(parameters.getDouble(Params.ALPHA));
        search.setFasDepth(parameters.getInt(Params.DEPTH));
        search.setLingThreshold(parameters.getDouble(Params.THRESHOLD_B));
        search.setFastIcaMaxIter(parameters.getInt(Params.FAST_ICA_MAX_ITER));
        search.setFastIcaTolerance(parameters.getDouble(Params.FAST_ICA_TOLERANCE));
        search.setFastIcaA(parameters.getDouble(Params.FAST_ICA_A));

        search.setExternalAdjacencyFraction(parameters.getDouble(Params.FASK_POOL_EXTERNAL_ADJACENCY_FRACTION));

        List<Graph> externals = externalGraphs(dataSets, parameters);

        if (!externals.isEmpty()) {
            search.setExternalGraphs(externals);
            boolean useOrientations = parameters.getBoolean(Params.FASK_POOL_EXTERNAL_ORIENTATIONS);
            search.setUseExternalOrientations(useOrientations);

            TetradLogger.getInstance().log("FASK-Vote: " + externals.size() + " external graph(s) received for "
                    + dataSets.size() + " dataset(s); their combined skeleton will be used"
                    + (useOrientations ? ", with their compelled orientations as defaults."
                    : "; their orientations will be ignored."));
        } else {
            TetradLogger.getInstance().log("FASK-Vote: NO external graph supplied; adjacency search "
                    + adjacency + " (1=IMaGES, 2=pooled FAS, 3=MG-FAS, 4=MG-LiNG, 5=intersection of 3 and 4,"
                    + " 6=IMaGES restricted to MG-FAS) will estimate the skeleton. If a source graph was"
                    + " connected in the interface, it did not reach the algorithm.");
        }

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
     *
     * <p>Returns the IMaGES parameters, minus OUTPUT_CPDAG (only adjacencies are used
     * from the adjacency-stage graph), plus the adjacency-method parameters shared with
     * FASK-Pool (FASK_POOL_ADJACENCY selects the adjacency search; ALPHA and DEPTH
     * govern the FAS-style tests; THRESHOLD_B and the FastICA parameters govern the
     * LiNG stage) and SKEW_EDGE_THRESHOLD, which the per-dataset FASK runs use.</p>
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
        parameters.add(Params.SKEW_EDGE_THRESHOLD);
        parameters.add(Params.FASK_POOL_EXTERNAL_ORIENTATIONS);
        parameters.add(Params.FASK_POOL_EXTERNAL_ADJACENCY_FRACTION);

        return parameters;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the algorithm supplying the external graph (in the GUI, the source graph of a parent graph box).
     * Null is accepted: the external graph is optional for FASK-Vote, and without one the adjacency search selected
     * by FASK_POOL_ADJACENCY runs as before.</p>
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        this.externalGraphAlgorithm = algorithm;
    }

    /**
     * Runs the external-graph algorithm to obtain the graphs whose skeletons and compelled orientations FASK-Vote
     * will use. A multi-dataset external algorithm pools the datasets itself and is run once; any other algorithm
     * is run separately on each dataset, so that the orientation evidence is genuinely per dataset.
     *
     * @param dataSets   the datasets
     * @param parameters the search parameters
     * @return the external graphs, empty if no external-graph algorithm was set
     * @throws InterruptedException if one of the searches is interrupted
     */
    private List<Graph> externalGraphs(List<DataModel> dataSets, Parameters parameters)
            throws InterruptedException {
        List<Graph> externals = new ArrayList<>();

        if (this.externalGraphAlgorithm == null) {
            return externals;
        }

        if (this.externalGraphAlgorithm instanceof MultiDataSetAlgorithm multi) {
            Graph graph = multi.search(dataSets, parameters);
            if (graph != null) externals.add(graph);
            return externals;
        }

        for (DataModel dataSet : dataSets) {
            Graph graph = this.externalGraphAlgorithm.search(dataSet, parameters);

            if (graph == null) {
                throw new IllegalStateException("The external-graph algorithm returned no graph for one of "
                        + "the datasets.");
            }

            externals.add(graph);
        }

        return externals;
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

