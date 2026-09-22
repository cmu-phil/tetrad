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

import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements a simple voting scheme for orienting a common adjacency graph using FASK.
 *
 * <p>The procedure has two stages:</p>
 *
 * <ol>
 *   <li>Estimate a common adjacency graph across the supplied datasets with a
 *   {@link PooledAdjacencySearch} (IMaGES by default; pooled FAS and the
 *   moral-graph-based methods, which are designed to remain correct when the true
 *   graph is cyclic, are also available; see {@link PooledAdjacencySearch.Method}).</li>
 *   <li>For each dataset, run FASK using that adjacency graph as an external adjacency
 *   constraint, then orient each adjacency by majority vote across the per-dataset
 *   FASK results.</li>
 * </ol>
 *
 * <p>In this construction, the adjacency search determines which adjacencies are
 * considered, and FASK is used only to vote on their orientations. A directed edge is added when more than half of
 * the counted per-dataset FASK graphs support that direction. If the two directions are tied
 * exactly at one half each, an undirected edge is added. If no per-dataset FASK graph supports
 * either direction for a given IMaGES adjacency, the adjacency is left undirected.</p>
 *
 * <p>When external graphs are supplied (e.g., BOSS run on each dataset) the adjacency search is skipped: a pair
 * enters the composite skeleton when it is adjacent in more than
 * {@link #setExternalAdjacencyFraction(double)} of the graphs. With
 * {@link #setUseExternalOrientations(boolean)} at its default of true, the graphs' compelled orientations also
 * act as orientation evidence. Only compelled orientations count: a graph that is a legal DAG is converted to its
 * CPDAG first, so reversible edges do not masquerade as compelled. Per pair, the compelled directions are tallied
 * across the graphs and the majority direction, if there is one, becomes the default orientation; a tie leaves the
 * pair to the ordinary vote. The vote overturns a default only when it opposes it, and the counter-signal required
 * scales with the strength of the evidence, as in {@link FaskPool}: unanimous evidence (no graph compels the other
 * direction) yields only to a unanimous vote -- every dataset voted, and every one against -- while evidence that
 * is itself divided yields to the ordinary majority. This is a lexicographic override rather than additive
 * pooling. The per-dataset FASK runs never see the external orientations, so the votes are cast independently of
 * the defaults they may overturn.</p>
 *
 * <p>The datasets are standardized for the adjacency stage, but the original datasets are
 * passed to FASK. This follows the behavior of the original implementation.</p>
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
     * The adjacency-stage configuration; see {@link PooledAdjacencySearch}.
     */
    private final PooledAdjacencySearch adjacencySearch;

    /**
     * Optional external graphs, one per dataset (or a single graph, when the external-graph algorithm is itself a
     * multi-dataset algorithm). When nonempty, the adjacency search is skipped and these graphs' combined skeleton
     * is oriented instead.
     */
    private List<Graph> externalGraphs = new ArrayList<>();

    /**
     * Whether the external graphs' compelled orientations act as default orientations that the vote can overturn.
     * Only consulted when external graphs have been set. Default: true.
     */
    private boolean useExternalOrientations = true;

    /**
     * Fraction of the external graphs in which a pair must be adjacent to enter the composite skeleton. Zero takes
     * the union, 0.5 (the default) a strict majority, and 1.0 the intersection.
     */
    private double externalAdjacencyFraction = 0.5;

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
        this.adjacencySearch = new PooledAdjacencySearch(score);
    }

    /**
     * Runs the search and returns the composite graph.
     *
     * <p>The search first standardizes each dataset and runs the configured adjacency
     * search to obtain a common adjacency graph. It then runs FASK separately on each
     * original dataset, constraining FASK to that graph's adjacencies. Each adjacency is
     * then oriented by majority vote across the per-dataset FASK graphs.</p>
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
        List<DataSet> standardized = standardizeDataSets(this.dataSets);

        Graph imagesGraph;

        // Orientation evidence: per pair, how many external graphs compel each direction.
        Map<NamePair, int[]> evidence = new HashMap<>();
        int numEvidenceGraphs = 0;

        if (!this.externalGraphs.isEmpty()) {
            // Each graph is reduced to its compelled orientations (a legal DAG is converted
            // to its CPDAG, so reversible edges do not masquerade as compelled), the
            // skeletons are combined, and the compelled orientations are tallied.
            List<Graph> compelledGraphs = new ArrayList<>();
            for (Graph g : this.externalGraphs) compelledGraphs.add(compelledGraph(g));

            imagesGraph = combineAdjacencies(compelledGraphs);

            if (this.useExternalOrientations) {
                numEvidenceGraphs = compelledGraphs.size();
                tallyOrientations(compelledGraphs, evidence);
            }
        } else {
            this.adjacencySearch.setKnowledge(this.knowledge);
            imagesGraph = this.adjacencySearch.search(standardized, parameters);
        }

        List<Node> imagesNodes = imagesGraph.getNodes();

        Graph result = new EdgeListGraph(this.dataSets.get(0).getVariables());
        List<Graph> perDatasetFaskGraphs = runFaskOnAllDataSets(imagesGraph, imagesNodes, parameters);

        for (Edge edge : imagesGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            OrientationVote vote = voteOnOrientation(x, y, perDatasetFaskGraphs);

            // Orientation evidence for this pair: how many external graphs compel x->y, and
            // how many compel y->x. The majority direction, if there is one, is the default
            // orientation; a tie (including none at all, or a pair reversible in every
            // CPDAG) leaves the pair to the ordinary vote.
            int forDefault = 0;
            int againstDefault = 0;
            int compelled = 0;

            if (numEvidenceGraphs > 0) {
                int[] tally = evidence.get(NamePair.of(x.getName(), y.getName()));

                if (tally != null) {
                    boolean xFirst = x.getName().compareTo(y.getName()) < 0;
                    int forward = xFirst ? tally[0] : tally[1];
                    int backward = xFirst ? tally[1] : tally[0];

                    if (forward > backward) {
                        compelled = +1;
                        forDefault = forward;
                        againstDefault = backward;
                    } else if (backward > forward) {
                        compelled = -1;
                        forDefault = backward;
                        againstDefault = forward;
                    }
                }
            }

            if (compelled != 0) {
                double proportionAgainst = compelled > 0 ? vote.proportionYToX : vote.proportionXToY;
                boolean opposes = proportionAgainst > 0.5;

                // Unanimous opposition in the vote: every dataset voted, and every one of
                // them against the default. This is the voting analogue of FASK-Pool's
                // strict cross-dataset sign consensus.
                boolean consensus = vote.countedGraphs == this.dataSets.size()
                                    && proportionAgainst == 1.0;

                // The counter-signal required scales with the strength of the evidence, as
                // in FASK-Pool: unanimous evidence (no external graph compels the other
                // direction) yields only to a unanimous vote; evidence that is itself
                // divided yields to the ordinary majority.
                boolean unanimousEvidence = againstDefault == 0;
                boolean overturned = opposes && (!unanimousEvidence || consensus);

                if (overturned) {
                    if (compelled > 0) result.addDirectedEdge(y, x);
                    else result.addDirectedEdge(x, y);
                } else {
                    if (compelled > 0) result.addDirectedEdge(x, y);
                    else result.addDirectedEdge(y, x);
                }
            } else {
                addVotedEdge(result, x, y, vote);
            }
        }

        return result;
    }

    /**
     * Sets how the common adjacency structure is obtained. Default: IMAGES.
     *
     * @param method the adjacency method
     */
    public void setAdjacencyMethod(PooledAdjacencySearch.Method method) {
        this.adjacencySearch.setMethod(method);
    }

    /**
     * Sets the external graphs, one per dataset (or a single graph, when the external-graph algorithm pools the
     * datasets internally). When nonempty, the adjacency search is skipped and the combined skeleton of these
     * graphs is oriented instead.
     *
     * @param externalGraphs the external graphs, or null or empty to use the adjacency search
     */
    public void setExternalGraphs(List<Graph> externalGraphs) {
        this.externalGraphs = externalGraphs == null ? new ArrayList<>() : new ArrayList<>(externalGraphs);
    }

    /**
     * Sets whether the external graphs' compelled orientations act as default orientations for the vote. When
     * false, the external graphs contribute adjacencies only. Only consulted when external graphs have been set.
     * Default: true.
     *
     * @param useExternalOrientations true to use the external orientations as defaults
     */
    public void setUseExternalOrientations(boolean useExternalOrientations) {
        this.useExternalOrientations = useExternalOrientations;
    }

    /**
     * Sets the fraction of the external graphs in which a pair must be adjacent to enter the composite skeleton.
     * Zero takes the union of the skeletons, 0.5 (the default) a strict majority, and 1.0 the intersection.
     *
     * @param externalAdjacencyFraction the fraction, in [0, 1]
     */
    public void setExternalAdjacencyFraction(double externalAdjacencyFraction) {
        if (externalAdjacencyFraction < 0.0 || externalAdjacencyFraction > 1.0) {
            throw new IllegalArgumentException("Fraction out of range: " + externalAdjacencyFraction);
        }
        this.externalAdjacencyFraction = externalAdjacencyFraction;
    }

    /**
     * @return the graph reduced to its compelled orientations: a legal DAG is converted to its CPDAG, so that
     * reversible edges do not masquerade as compelled.
     */
    private Graph compelledGraph(Graph graph) {
        return graph.paths().isLegalDag() ? GraphTransforms.dagToCpdag(graph) : graph;
    }

    /**
     * Combines the skeletons of the per-dataset graphs into one composite skeleton: a pair is an adjacency when the
     * number of graphs in which it is adjacent exceeds {@link #setExternalAdjacencyFraction(double)} times the
     * number of graphs (or, at a fraction of 1.0, when it is adjacent in all of them). Pairs involving variables
     * absent from the datasets are dropped.
     *
     * @param graphs the per-dataset graphs
     * @return an undirected graph over the dataset variables
     */
    private Graph combineAdjacencies(List<Graph> graphs) {
        List<Node> vars = this.dataSets.get(0).getVariables();
        Graph out = new EdgeListGraph(vars);

        Map<NamePair, Integer> counts = new HashMap<>();

        for (Graph graph : graphs) {
            Set<NamePair> seen = new HashSet<>();

            for (Edge edge : graph.getEdges()) {
                // A two-cycle contributes two edges between the same pair; count the pair
                // once per graph.
                NamePair pair = NamePair.of(edge.getNode1().getName(), edge.getNode2().getName());
                if (seen.add(pair)) counts.merge(pair, 1, Integer::sum);
            }
        }

        int m = graphs.size();

        for (Map.Entry<NamePair, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();

            boolean keep = this.externalAdjacencyFraction >= 1.0
                    ? count == m : count > this.externalAdjacencyFraction * m;

            if (!keep) continue;

            Node a = out.getNode(entry.getKey().first());
            Node b = out.getNode(entry.getKey().second());

            if (a != null && b != null) out.addUndirectedEdge(a, b);
        }

        return out;
    }

    /**
     * Tallies the compelled orientations of the given graphs into the evidence map: for each pair, the number of
     * graphs compelling each direction, indexed by the pair's name order.
     *
     * @param graphs   the graphs to tally
     * @param evidence the map to tally into
     */
    private void tallyOrientations(List<Graph> graphs, Map<NamePair, int[]> evidence) {
        for (Graph graph : graphs) {
            for (Edge edge : graph.getEdges()) {
                if (!Edges.isDirectedEdge(edge)) continue;

                Node node1 = edge.getNode1();
                Node node2 = edge.getNode2();
                Node from = edge.pointsTowards(node2) ? node1 : node2;

                NamePair pair = NamePair.of(node1.getName(), node2.getName());
                int[] tally = evidence.computeIfAbsent(pair, k -> new int[2]);

                if (from.getName().equals(pair.first())) tally[0]++;
                else tally[1]++;
            }
        }
    }

    /**
     * An unordered pair of variable names, in a canonical order, for use as a map key.
     */
    private record NamePair(String first, String second) {
        private static NamePair of(String name1, String name2) {
            return name1.compareTo(name2) < 0 ? new NamePair(name1, name2) : new NamePair(name2, name1);
        }
    }

    /**
     * Sets the alpha level for the FAS-style adjacency tests. Default: 0.05.
     *
     * @param fasAlpha the alpha level, in (0, 1)
     */
    public void setFasAlpha(double fasAlpha) {
        this.adjacencySearch.setFasAlpha(fasAlpha);
    }

    /**
     * Sets the depth of the FAS-style adjacency searches (-1 for unlimited; capped at
     * 4 in MG_FAS). Default: -1.
     *
     * @param fasDepth the depth
     */
    public void setFasDepth(int fasDepth) {
        this.adjacencySearch.setFasDepth(fasDepth);
    }

    /**
     * Sets the threshold on the pooled absolute LiNG B-hat entries above which a moral
     * pair is kept as an adjacency. Default: 0.1.
     *
     * @param lingThreshold the threshold, nonnegative
     */
    public void setLingThreshold(double lingThreshold) {
        this.adjacencySearch.setLingThreshold(lingThreshold);
    }

    /**
     * Sets the FastICA maximum iterations for the LiNG adjacency stage. Default: 2000.
     *
     * @param fastIcaMaxIter maximum iterations, positive
     */
    public void setFastIcaMaxIter(int fastIcaMaxIter) {
        this.adjacencySearch.setFastIcaMaxIter(fastIcaMaxIter);
    }

    /**
     * Sets the FastICA convergence tolerance for the LiNG adjacency stage. Default:
     * 1e-6.
     *
     * @param fastIcaTolerance the tolerance, positive
     */
    public void setFastIcaTolerance(double fastIcaTolerance) {
        this.adjacencySearch.setFastIcaTolerance(fastIcaTolerance);
    }

    /**
     * Sets the FastICA tanh nonlinearity parameter for the LiNG adjacency stage.
     * Default: 1.1.
     *
     * @param fastIcaA the parameter
     */
    public void setFastIcaA(double fastIcaA) {
        this.adjacencySearch.setFastIcaA(fastIcaA);
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
     * Standardizes the supplied datasets for the adjacency stage.
     *
     * @param dataSets the datasets to standardize
     * @return the standardized datasets
     */
    private List<DataSet> standardizeDataSets(List<DataSet> dataSets) {
        List<DataSet> standardized = new ArrayList<>();

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

            // The per-dataset votes must be cast independently of any external orientations:
            // if each dataset's FASK started from the compelled orientations as defaults, every
            // vote would simply reproduce them and the unanimity gate above could never fire.
            // The graph handed over is undirected in any case; this makes that a stated
            // requirement rather than an accident of the call above.
            fask.setUseExternalOrientations(false);
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