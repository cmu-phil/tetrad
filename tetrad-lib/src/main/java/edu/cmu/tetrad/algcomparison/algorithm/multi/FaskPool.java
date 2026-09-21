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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wraps the pooled-FASK multi-dataset algorithm for continuous variables. A graph-based
 * stage supplies the common adjacency structure&mdash;IMaGES, another pooled adjacency
 * search, or one external graph per dataset, combined&mdash;and each adjacency is
 * oriented by the sign of the FASK left-right statistic pooled across the datasets
 * (sample-size weighted by default), rather than by voting on per-dataset orientations
 * as FASK-Vote does. Where the graph stage compels a direction, that direction is kept
 * as a default orientation that the pooled statistic can overturn only on strong
 * opposing signal.
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
public class FaskPool implements MultiDataSetAlgorithm, AcceptsKnowledge, TakesScoreWrapper,
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
     * Optional algorithm supplying the external graphs (in the GUI, the source graph of
     * a parent graph box, wrapped in a SingleGraphAlg). Run once per dataset, so that
     * each dataset contributes its own graph. Null when no source graph is supplied, in
     * which case the adjacency search selected by FASK_POOL_ADJACENCY runs as before.
     */
    private Algorithm externalGraphAlgorithm = null;

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
            case 1 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.IMAGES);
            case 2 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.POOLED_FAS);
            case 3 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.MG_FAS);
            case 4 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.MG_LING);
            case 5 -> search.setAdjacencyMethod(edu.cmu.tetrad.search.PooledAdjacencySearch.Method.MG_FAS_INTERSECT_LING);
            default -> throw new IllegalStateException("Unconfigured adjacency method (1-5): " + adjacency);
        }

        search.setFasAlpha(parameters.getDouble(Params.ALPHA));
        search.setFasDepth(parameters.getInt(Params.DEPTH));
        search.setLingThreshold(parameters.getDouble(Params.THRESHOLD_B));
        search.setFastIcaMaxIter(parameters.getInt(Params.FAST_ICA_MAX_ITER));
        search.setFastIcaTolerance(parameters.getDouble(Params.FAST_ICA_TOLERANCE));
        search.setFastIcaA(parameters.getDouble(Params.FAST_ICA_A));

        boolean useAdjacencyOrientations = parameters.getBoolean(Params.FASK_POOL_ADJACENCY_ORIENTATIONS);
        search.setUseAdjacencyOrientations(useAdjacencyOrientations);
        search.setExternalAdjacencyFraction(parameters.getDouble(Params.FASK_POOL_EXTERNAL_ADJACENCY_FRACTION));

        List<Graph> externals = externalGraphs(dataSets, parameters);
        boolean evidence;

        if (!externals.isEmpty()) {
            search.setExternalGraphs(externals);
            boolean useOrientations = parameters.getBoolean(Params.FASK_POOL_EXTERNAL_ORIENTATIONS);
            search.setUseExternalOrientations(useOrientations);
            evidence = useOrientations;

            // A fixed source graph is returned as the very same object for every
            // dataset; two graphs that merely agree were still estimated separately.
            boolean fixedGraph = true;
            boolean agree = true;

            for (Graph g : externals) {
                if (g != externals.get(0)) fixedGraph = false;
                if (!sameEdges(g, externals.get(0))) agree = false;
            }

            TetradLogger.getInstance().log("FASK-Pool: " + externals.size() + " external graph(s) received for "
                    + dataSets.size() + " dataset(s); their skeletons will be combined"
                    + (useOrientations ? ", with the majority of their compelled orientations as defaults."
                    : "; their orientations will be ignored."));

            if (fixedGraph && dataSets.size() > 1) {
                TetradLogger.getInstance().log("FASK-Pool: WARNING: the same graph was supplied for every"
                        + " dataset (a fixed source graph), so it was not estimated per dataset and carries no"
                        + " cross-dataset evidence; its orientations nonetheless count as unanimous. Set a"
                        + " positive orientation alpha to gate the override.");
            } else if (externals.size() == 1 && dataSets.size() > 1) {
                TetradLogger.getInstance().log("FASK-Pool: the external-graph algorithm is a multi-dataset"
                        + " algorithm, so it was run once over all " + dataSets.size() + " datasets and pooled"
                        + " them itself; its orientations count as unanimous evidence.");
            } else if (agree && externals.size() > 1) {
                TetradLogger.getInstance().log("FASK-Pool: the per-dataset external graphs agree edge for"
                        + " edge, so their compelled orientations are unanimous evidence.");
            }
        } else {
            evidence = adjacency == 1 && useAdjacencyOrientations;

            TetradLogger.getInstance().log("FASK-Pool: NO external graph supplied; adjacency search "
                    + adjacency + " (1=IMaGES, 2=pooled FAS, 3=MG-FAS, 4=MG-LiNG, 5=intersection of 3 and 4)"
                    + " will estimate the skeleton"
                    + (adjacency == 1 && useAdjacencyOrientations
                    ? ", and the compelled orientations of the IMaGES CPDAG will act as defaults."
                    : ".")
                    + " If a source graph was connected in the interface, it did not reach the algorithm.");
        }

        search.setTwoCycleAlpha(parameters.getDouble(Params.TWO_CYCLE_ALPHA));

        double orientationAlpha = parameters.getDouble(Params.ORIENTATION_ALPHA);
        search.setOrientationAlpha(orientationAlpha);

        if (evidence && orientationAlpha == 0) {
            TetradLogger.getInstance().log("FASK-Pool: orientation alpha is 0, so the only gate on overturning"
                    + " a default orientation from the graph stage is that every dataset's left-right statistic"
                    + " oppose it, however small each one is. Set a positive orientation alpha to require the"
                    + " pooled statistic to be significantly different from zero as well.");
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
     * Runs the external-graph algorithm to obtain the graphs whose skeletons and
     * compelled orientations FASK-Pool combines.
     *
     * <p>A separate graph is estimated for each dataset, so that the orientation
     * evidence is cross-dataset in the same way the pooled left-right statistic is;
     * handing one graph fitted to a single dataset to the whole pool would impose that
     * dataset's structure on the others. The exception is an algorithm that is itself a
     * {@link MultiDataSetAlgorithm}: it sees all the datasets at once and pools them
     * internally, so it is run once and its single graph is used. A fixed source graph
     * from the interface likewise yields the same graph for every dataset; that case is
     * detected and logged, since such a graph carries no cross-dataset evidence.</p>
     *
     * @param dataSets   the datasets, in order
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
     * @return true if the two graphs have the same edges, compared by variable name
     */
    private static boolean sameEdges(Graph graph1, Graph graph2) {
        if (graph1.getNumEdges() != graph2.getNumEdges()) return false;

        Set<String> edges1 = new HashSet<>();
        for (Edge edge : graph1.getEdges()) edges1.add(edge.toString());

        Set<String> edges2 = new HashSet<>();
        for (Edge edge : graph2.getEdges()) edges2.add(edge.toString());

        return edges1.equals(edges2);
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
     * <p>Returns the IMaGES parameters, minus OUTPUT_CPDAG: FASK-Pool takes the
     * compelled orientations of the IMaGES CPDAG as evidence whether or not BOSS is
     * asked to output a CPDAG (a DAG is converted to its CPDAG first), so the setting
     * is irrelevant. FASK_POOL_ADJACENCY chooses the adjacency search (1 = IMaGES,
     * 2 = pooled FAS, 3 = MG-FAS, 4 = MG-LiNG, 5 = intersection of 3 and 4); ALPHA and
     * DEPTH govern the FAS-style tests, and THRESHOLD_B with the FastICA parameters
     * governs the LiNG stage. FASK_POOL_ADJACENCY_ORIENTATIONS (default true) makes the
     * IMaGES CPDAG's compelled orientations act as default orientations, overturnable
     * only on strict cross-dataset sign consensus of the left-right statistic; the
     * other adjacency methods produce undirected graphs, for which it has no effect.
     * FASK_POOL_EXTERNAL_ORIENTATIONS (default true) does the same for supplied
     * external graphs, which are estimated one per dataset and combined, and
     * FASK_POOL_EXTERNAL_ADJACENCY_FRACTION (default 0.5) sets how many of them a pair
     * must be adjacent in to enter the composite skeleton. TWO_CYCLE_ALPHA (default
     * 0 = off) enables per-dataset two-cycle tests requiring unanimity across datasets;
     * ORIENTATION_ALPHA (default 0) sets the bootstrap significance gate on orientation
     * decisions, which with a single dataset is the only meaningful gate on the
     * override. No other FASK single-dataset parameters are listed because there is no
     * skew-adjacency stage in this setting.</p>
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
        parameters.add(Params.FASK_POOL_ADJACENCY_ORIENTATIONS);
        parameters.add(Params.FASK_POOL_EXTERNAL_ORIENTATIONS);
        parameters.add(Params.FASK_POOL_EXTERNAL_ADJACENCY_FRACTION);
        parameters.add(Params.TWO_CYCLE_ALPHA);
        parameters.add(Params.ORIENTATION_ALPHA);

        return parameters;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the algorithm supplying the external graphs. It is run separately on each
     * dataset (unless it is itself a multi-dataset algorithm, which is run once on all
     * of them), and the resulting graphs are combined. Null is accepted: external graphs
     * are optional for FASK-Pool, and without them the adjacency search selected by
     * FASK_POOL_ADJACENCY runs as before. Note that a fixed source graph from the
     * interface returns the same graph for every dataset, which is logged, since such a
     * graph carries no cross-dataset evidence.</p>
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        this.externalGraphAlgorithm = algorithm;
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
