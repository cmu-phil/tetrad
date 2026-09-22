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
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Bfci;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Runs the ION (Integration of Overlapping Networks) algorithm on multiple datasets whose variable sets may overlap
 * only partially. BFCI is run separately on each dataset using the chosen independence test and score, and the
 * resulting PAGs are integrated by ION into a set of PAGs over the union of the variables that are consistent with
 * every input PAG.
 * <p>
 * BFCI is used for the per-dataset searches, rather than FCI, because ION's correctness argument requires that its
 * inputs be legal PAGs, and FCI is not guaranteed to produce a legal PAG, whereas BFCI is designed to. For oracle
 * experiments the analogous choice is GRaSP-FCI with the m-separation test and score.
 * <p>
 * Reference: Danks, D., Glymour, C., &amp; Tillman, R. E. (2008). Integrating locally learned causal structures with
 * overlapping variables. In Advances in Neural Information Processing Systems 21 (NIPS 2008), pp. 1665-1672.
 * <p>
 * ION returns an equivalence class of PAGs, which may contain more than one graph; all of them are printed to the log.
 * Since this interface must return a single graph, the graph with the fewest edges is returned, with ties broken by a
 * lexicographic comparison of the printed edge lists, so that the choice is deterministic. If the input PAGs are
 * jointly inconsistent (which can happen when they are estimated from finite samples of different datasets), ION may
 * return no graphs at all; in that case an edgeless graph over the union of the variables is returned and a warning is
 * logged.
 * <p>
 * Note that ION is exponential in the number of variables and is intended for problems with a small number of
 * variables.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see edu.cmu.tetrad.search.Ion
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "ION",
        command = "ion",
        algoType = AlgType.allow_latent_common_causes,
        dataType = DataType.All
)
public class Ion implements MultiDataSetAlgorithm, TakesIndependenceWrapper, TakesScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use for the per-dataset BFCI searches.
     */
    private IndependenceWrapper test;

    /**
     * The score to use for the per-dataset BFCI searches.
     */
    private ScoreWrapper score;

    /**
     * <p>Constructor for Ion.</p>
     *
     * @param test  a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public Ion(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * <p>Constructor for Ion.</p>
     */
    public Ion() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) throws InterruptedException {
        List<Graph> pags = new ArrayList<>();

        for (DataModel dataModel : dataSets) {
            IndependenceTest s = this.test.getTest(dataModel, parameters);
            Score sc = this.score.getScore(dataModel, parameters);

            Bfci bfci = new Bfci(s, sc);

            boolean parallelized = parameters.getBoolean(Params.PARALLELIZED);

            bfci.setBossUseBes(parameters.getBoolean(Params.USE_BES));
            bfci.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
            bfci.setMaxPossibleDsepPathLength(parameters.getInt(Params.MAX_POSSIBLE_SEP_PATH_LENGTH));
            bfci.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
            bfci.setDepth(parameters.getInt(Params.DEPTH));
            bfci.setParallelized(parallelized);
            bfci.setNumThreads(parallelized ? 1 : Runtime.getRuntime().availableProcessors());
            bfci.setDoLegalityGating(parameters.getBoolean(Params.DO_LEGALITY_GATING));
            bfci.setUseMaxP(parameters.getBoolean(Params.USE_MAX_P_HEURISTIC));
            bfci.setExcludeSelectionBias(parameters.getBoolean(Params.EXCLUDE_SELECTION_BIAS));
            bfci.setLvHeuristicOnly(parameters.getBoolean(Params.LV_HEURISTIC_ONLY));
            bfci.setUsePossibleDsep(parameters.getBoolean(Params.DO_POSSIBLE_DSEP));
            bfci.setVerbose(parameters.getBoolean(Params.VERBOSE));
            bfci.setLogFinalOrientations(parameters.getBoolean(Params.LOG_FINAL_ORIENTATIONS));
            bfci.setNumStarts(parameters.getInt(Params.NUM_STARTS));

            Graph pag = bfci.search();
            TetradLogger.getInstance().log("ION input PAG for " + dataModel.getName() + ": " + pag);
            pags.add(pag);
        }

        edu.cmu.tetrad.search.Ion ion = new edu.cmu.tetrad.search.Ion(pags);
        ion.setDoPathLengthSearch(parameters.getBoolean(Params.ION_PATH_LENGTH_SEARCH));
        ion.setDoAdjacencySearch(parameters.getBoolean(Params.ION_ADJACENCY_SEARCH));

        List<Graph> output = ion.search();

        TetradLogger.getInstance().log("ION returned " + output.size() + " graph(s).");

        for (Graph graph : output) {
            TetradLogger.getInstance().log(graph.toString());
        }

        if (output.isEmpty()) {
            TetradLogger.getInstance().log("ION returned no graphs; the input PAGs appear to be jointly "
                                           + "inconsistent. Returning an edgeless graph over the union of the "
                                           + "variables.");

            Set<String> names = new LinkedHashSet<>();
            for (Graph pag : pags) {
                for (Node node : pag.getNodes()) {
                    names.add(node.getName());
                }
            }

            List<Node> nodes = new ArrayList<>();
            for (String name : names) {
                nodes.add(new GraphNode(name));
            }

            return new EdgeListGraph(nodes);
        }

        // The interface requires a single graph, so return a deterministic representative of the
        // equivalence class: the graph with the fewest edges, ties broken lexicographically.
        Graph best = null;
        String bestKey = null;

        for (Graph graph : output) {
            List<String> edgeStrings = new ArrayList<>();
            for (edu.cmu.tetrad.graph.Edge edge : graph.getEdges()) {
                edgeStrings.add(edge.toString());
            }
            Collections.sort(edgeStrings);
            String key = graph.getNumEdges() + ":" + edgeStrings;

            if (best == null || graph.getNumEdges() < best.getNumEdges()
                || (graph.getNumEdges() == best.getNumEdges() && key.compareTo(bestKey) < 0)) {
                best = graph;
                bestKey = key;
            }
        }

        return best;
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
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) throws InterruptedException {
        return search(Collections.singletonList(SimpleDataLoader.getMixedDataSet(dataSet)), parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "ION (Integration of Overlapping Networks) using BFCI on each dataset with "
               + (this.test != null ? this.test.getDescription() : "the chosen test") + " and "
               + (this.score != null ? this.score.getDescription() : "the chosen score");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.All;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();
        parameters.add(Params.USE_BES);
        parameters.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        parameters.add(Params.MAX_POSSIBLE_SEP_PATH_LENGTH);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.DEPTH);
        parameters.add(Params.DO_LEGALITY_GATING);
        parameters.add(Params.USE_MAX_P_HEURISTIC);
        parameters.add(Params.EXCLUDE_SELECTION_BIAS);
        parameters.add(Params.LV_HEURISTIC_ONLY);
        parameters.add(Params.DO_POSSIBLE_DSEP);
        parameters.add(Params.PARALLELIZED);
        parameters.add(Params.NUM_STARTS);
        parameters.add(Params.ION_PATH_LENGTH_SEARCH);
        parameters.add(Params.ION_ADJACENCY_SEARCH);
        parameters.add(Params.VERBOSE);
        parameters.add(Params.LOG_FINAL_ORIENTATIONS);

        return parameters;
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
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}
