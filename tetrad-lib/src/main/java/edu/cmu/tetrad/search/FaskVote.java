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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the              //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program. If not, see <https://www.gnu.org/licenses/>.     //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements a simple voting scheme for orienting a common adjacency graph using FASK.
 *
 * <p>The procedure has two stages:</p>
 *
 * <ol>
 *   <li>Run IMaGES across the supplied datasets to estimate a common adjacency graph.</li>
 *   <li>For each dataset, run FASK using the undirected version of that IMaGES graph as an
 *   external adjacency constraint, then orient each IMaGES adjacency by majority vote across
 *   the per-dataset FASK results.</li>
 * </ol>
 *
 * <p>In this construction, IMaGES determines which adjacencies are considered, and FASK is
 * used only to vote on their orientations. A directed edge is added when more than half of
 * the counted per-dataset FASK graphs support that direction. If the two directions are tied
 * exactly at one half each, an undirected edge is added. If no per-dataset FASK graph supports
 * either direction for a given IMaGES adjacency, the adjacency is left undirected.</p>
 *
 * <p>The datasets are standardized for the IMaGES stage, but the original datasets are passed
 * to FASK. This follows the behavior of the original implementation.</p>
 *
 * @author Madelyn Glymour
 * @author josephramsey
 */
public class FaskVote {

    /**
     * The score wrapper used to construct the IMaGES score and the per-dataset FASK scores.
     */
    private final ScoreWrapper score;

    /**
     * The datasets over which the composite graph is constructed.
     */
    private final List<DataSet> dataSets;

    /**
     * Background knowledge containing forbidden and required edges.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs a FASK-voting search from the supplied datasets, score wrapper, and
     * independence wrapper.
     *
     * @param dataSets the datasets to search over
     * @param score    the score wrapper to use
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the dataset list is empty
     */
    public FaskVote(List<DataSet> dataSets, ScoreWrapper score) {
        if (dataSets == null) {
            throw new NullPointerException("Dataset list must not be null.");
        }

        if (score == null) {
            throw new NullPointerException("Score wrapper must not be null.");
        }

        if (dataSets.isEmpty()) {
            throw new IllegalArgumentException("At least one dataset is required.");
        }

        this.dataSets = dataSets;
        this.score = score;
    }

    /**
     * Runs the search and returns the composite graph.
     *
     * <p>The search first standardizes each dataset and runs IMaGES to obtain a common
     * adjacency graph. It then runs FASK separately on each original dataset, constraining
     * FASK to the undirected version of the IMaGES graph. Each IMaGES adjacency is then
     * oriented by majority vote across the per-dataset FASK graphs.</p>
     *
     * <p>The voting denominator is the number of per-dataset FASK graphs that contain
     * at least one of the two candidate directions for the edge. If no such graph exists
     * for a given adjacency, the adjacency is retained as undirected.</p>
     *
     * @param parameters the search parameters
     * @return the composite graph
     * @throws InterruptedException if one of the underlying searches is interrupted
     */
    public Graph search(Parameters parameters) throws InterruptedException {
        List<DataModel> standardizedDataModels = standardizeDataSets(this.dataSets);

        Images images = new Images(this.score);
        images.setKnowledge(this.knowledge);

        Graph imagesGraph = images.search(standardizedDataModels, parameters);
        List<Node> imagesNodes = imagesGraph.getNodes();

        Graph result = new EdgeListGraph(this.dataSets.get(0).getVariables());
        List<Graph> perDatasetFaskGraphs = runFaskOnAllDataSets(imagesGraph, imagesNodes, parameters);

        for (Edge edge : imagesGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            OrientationVote vote = voteOnOrientation(x, y, perDatasetFaskGraphs);
            addVotedEdge(result, x, y, vote);
        }

        return result;
    }

    /**
     * Sets background knowledge for the search.
     *
     * <p>A defensive copy is stored so later external changes to the supplied knowledge
     * object do not unexpectedly affect this search.</p>
     *
     * @param knowledge knowledge containing forbidden and required edges
     * @throws NullPointerException if the supplied knowledge is null
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Standardizes the supplied datasets for the IMaGES stage.
     *
     * @param dataSets the datasets to standardize
     * @return the standardized datasets as data models
     */
    private List<DataModel> standardizeDataSets(List<DataSet> dataSets) {
        List<DataModel> standardized = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            standardized.add(DataTransforms.standardizeData(dataSet));
        }

        return standardized;
    }

    /**
     * Runs FASK once per dataset, using the undirected version of the IMaGES graph as
     * an external adjacency graph.
     *
     * @param imagesGraph the IMaGES graph providing the common adjacency structure
     * @param targetNodes the node identities to which per-dataset FASK graphs should be aligned
     * @param parameters the search parameters
     * @return the list of per-dataset FASK graphs
     * @throws InterruptedException if one of the underlying FASK searches is interrupted
     */
    private List<Graph> runFaskOnAllDataSets(Graph imagesGraph, List<Node> targetNodes, Parameters parameters)
            throws InterruptedException {
        List<Graph> faskGraphs = new ArrayList<>();

        for (DataSet dataSet : this.dataSets) {
            Fask fask = new Fask(dataSet, this.score.getScore(dataSet, parameters));
            fask.setExternalGraph(GraphUtils.undirectedGraph(imagesGraph));
            fask.setLeftRight(Fask.LeftRight.FASK2);
            fask.setExtraEdgeThreshold(parameters.getDouble(Params.SKEW_EDGE_THRESHOLD));
            fask.setDepth(parameters.getInt(Params.DEPTH));

            // Two-cycle detection is intentionally disabled here because it did not perform well in this voting setup.
            fask.setTwoCycleAlpha(0.0);

            fask.setKnowledge(this.knowledge);

            Graph graph = fask.search();

            // Align node identities with the IMaGES graph so edge comparisons are consistent.
            graph = GraphUtils.replaceNodes(graph, targetNodes);
            faskGraphs.add(graph);
        }

        return faskGraphs;
    }

    /**
     * Computes the orientation vote for a single adjacency.
     *
     * @param x one endpoint of the adjacency
     * @param y the other endpoint of the adjacency
     * @param faskGraphs the per-dataset FASK graphs
     * @return the resulting orientation vote summary
     */
    private OrientationVote voteOnOrientation(Node x, Node y, List<Graph> faskGraphs) {
        Edge xToY = Edges.directedEdge(x, y);
        Edge yToX = Edges.directedEdge(y, x);

        int countXToY = 0;
        int countYToX = 0;
        int countedGraphs = 0;

        for (Graph graph : faskGraphs) {
            boolean hasXToY = graph.containsEdge(xToY);
            boolean hasYToX = graph.containsEdge(yToX);

            if (hasXToY) {
                countXToY++;
            }

            if (hasYToX) {
                countYToX++;
            }

            if (hasXToY || hasYToX) {
                countedGraphs++;
            }
        }

        if (countedGraphs == 0) {
            return new OrientationVote(0.0, 0.0, 0);
        }

        double proportionXToY = countXToY / (double) countedGraphs;
        double proportionYToX = countYToX / (double) countedGraphs;

        return new OrientationVote(proportionXToY, proportionYToX, countedGraphs);
    }

    /**
     * Adds the voted edge for the given endpoints to the result graph.
     *
     * <p>If no per-dataset FASK graph supported either direction, the edge is added as
     * undirected. If the two directions tie exactly at one half each, the edge is also
     * added as undirected. Otherwise, any direction receiving more than one half of the
     * counted votes is added.</p>
     *
     * @param result the graph being constructed
     * @param x one endpoint
     * @param y the other endpoint
     * @param vote the vote summary
     */
    private void addVotedEdge(Graph result, Node x, Node y, OrientationVote vote) {
        if (vote.countedGraphs == 0) {
            result.addUndirectedEdge(x, y);
            return;
        }

        if (vote.proportionXToY == 0.5 && vote.proportionYToX == 0.5) {
            result.addUndirectedEdge(x, y);
            return;
        }

        if (vote.proportionXToY > 0.5) {
            result.addDirectedEdge(x, y);
        }

        if (vote.proportionYToX > 0.5) {
            result.addDirectedEdge(y, x);
        }
    }

    /**
     * Stores the vote proportions for a single adjacency.
     */
    private static final class OrientationVote {

        /**
         * The proportion of counted graphs containing the direction x to y.
         */
        private final double proportionXToY;

        /**
         * The proportion of counted graphs containing the direction y to x.
         */
        private final double proportionYToX;

        /**
         * The number of per-dataset FASK graphs that contributed to the denominator.
         */
        private final int countedGraphs;

        /**
         * Constructs a vote summary.
         *
         * @param proportionXToY the proportion supporting x to y
         * @param proportionYToX the proportion supporting y to x
         * @param countedGraphs the number of graphs counted in the denominator
         */
        private OrientationVote(double proportionXToY, double proportionYToX, int countedGraphs) {
            this.proportionXToY = proportionXToY;
            this.proportionYToX = proportionYToX;
            this.countedGraphs = countedGraphs;
        }
    }
}