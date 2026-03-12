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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Fcit;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.SublistGenerator;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements the Detect-Mimic-FCIT algorithm.
 *
 * <p>This class is an algcomparison wrapper around an FCIT search followed by a
 * detect-mimic post-processing step. The intended use case is the discovery of
 * intermediate latent variables in settings resembling Multiple Input Multiple
 * Indicator models.</p>
 *
 * <p>The overall procedure is:</p>
 *
 * <ol>
 *   <li>Run FCIT on the measured variables using the supplied score, independence
 *   test, knowledge, and search parameters.</li>
 *   <li>Extract the directed part of the resulting graph as a graph of candidate
 *   parent-to-child relations.</li>
 *   <li>Search for parent sets and child sets that form a complete directed
 *   bipartite pattern in that directed graph.</li>
 *   <li>For each candidate parent set and child set, check whether the candidate
 *   latent remains plausible using independence tests among the children, with
 *   conditioning sets chosen by recursive blocking from the current partially
 *   oriented graph.</li>
 *   <li>If the candidate passes the check, introduce a latent variable between
 *   the measured parents and measured children, remove the corresponding direct
 *   measured edges, and re-orient the graph.</li>
 *   <li>After all such replacements, add latent-to-latent edges using the same
 *   subset-inclusion convention used by the detect-mimic construction.</li>
 * </ol>
 *
 * <p>This class is experimental. It is not presented here as a general latent
 * discovery method, but rather as a specialized detect-mimic variant built on
 * top of FCIT.</p>
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "DM-FCIT",
        command = "dm-fcit",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
@Experimental
public class DmFcit extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper,
        TakesIndependenceWrapper, HasKnowledge, ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test wrapper used to construct the working independence test.
     */
    private IndependenceWrapper test;

    /**
     * The score wrapper used to construct the working score.
     */
    private ScoreWrapper score;

    /**
     * Background knowledge used by the FCIT search.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs a DM-FCIT algorithm instance for reflective creation.
     */
    public DmFcit() {
        // Used for reflection; do not delete.
    }

    /**
     * Constructs a DM-FCIT algorithm instance with the given independence-test and score wrappers.
     *
     * @param test the independence-test wrapper
     * @param score the score wrapper
     */
    public DmFcit(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Runs the DM-FCIT search on the supplied data model using the supplied parameters.
     *
     * <p>If time lagging is requested, the data are first expanded into lagged form and the
     * resulting knowledge is taken from that lagged dataset. FCIT is then run on the measured
     * variables. The resulting graph is passed through the detect-mimic post-processing step
     * that introduces latent variables.</p>
     *
     * @param dataModel the data model to analyze
     * @param parameters the search parameters
     * @return the resulting graph after FCIT followed by detect-mimic processing
     * @throws InterruptedException if the search is interrupted
     * @throws IllegalArgumentException if time lagging is requested for a non-dataset input,
     * or if an invalid FCIT start option is supplied
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));

            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }

            dataModel = timeSeries;
            this.knowledge = new Knowledge(timeSeries.getKnowledge());
        }

        IndependenceTest independenceTest = this.test.getTest(dataModel, parameters);
        independenceTest = new CachedIndependenceQueries(independenceTest);

        Score searchScore = this.score.getScore(dataModel, parameters);

        if (independenceTest instanceof MsepTest && parameters.getInt(Params.FCIT_STARTS_WITH) == 1) {
            throw new IllegalArgumentException(
                    "For d-separation oracle input, please use the GRaSP option."
            );
        }

        Fcit search = new Fcit(independenceTest, searchScore);

        // BOSS-related settings used by FCIT.
        search.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        search.setUseBes(parameters.getBoolean(Params.USE_BES));

        int startOption = parameters.getInt(Params.FCIT_STARTS_WITH);

        if (startOption == 1) {
            search.setStartWith(Fcit.START_WITH.BOSS);
        } else if (startOption == 2) {
            search.setStartWith(Fcit.START_WITH.GRASP);
        } else if (startOption == 3) {
            search.setStartWith(Fcit.START_WITH.SP);
        } else {
            throw new IllegalArgumentException("Unknown start with option: " + startOption);
        }

        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setKnowledge(this.knowledge);

        Graph graph = search.search();

        return getDmGraph(graph, independenceTest);
    }

    /**
     * Returns the comparison graph used for algorithm-comparison evaluation.
     *
     * <p>The supplied true DAG is converted to a PAG.</p>
     *
     * @param graph the true directed graph, if there is one
     * @return the comparison PAG
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph, false);
    }

    /**
     * Returns a short one-line description of this algorithm.
     *
     * @return a one-line description of the algorithm
     */
    @Override
    public String getDescription() {
        String scoreDescription = this.score == null ? "no score specified" : this.score.getDescription();
        return "DM-FCIT using " + scoreDescription;
    }

    /**
     * Returns the data type expected by this algorithm.
     *
     * @return the required data type
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * Returns the list of parameters used by this algorithm.
     *
     * @return the list of parameter names
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        // BOSS-related settings used by FCIT.
        params.add(Params.USE_BES);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.NUM_STARTS);

        // FCI orientation settings.
        params.add(Params.COMPLETE_RULE_SET_USED);

        // FCIT settings.
        params.add(Params.FCIT_STARTS_WITH);
        params.add(Params.GRASP_DEPTH);
        params.add(Params.GUARANTEE_PAG);
        params.add(Params.PRESERVE_MARKOV);

        // General settings.
        params.add(Params.TIME_LAG);
        params.add(Params.VERBOSE);
        params.add(Params.TEST_TIMEOUT);

        return params;
    }

    /**
     * Returns the current background knowledge.
     *
     * @return the current background knowledge
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the background knowledge for this algorithm.
     *
     * <p>A defensive copy is stored so later changes to the supplied knowledge object
     * do not unexpectedly affect this algorithm.</p>
     *
     * @param knowledge the background knowledge to use
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the score wrapper currently used by this algorithm.
     *
     * @return the score wrapper
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for this algorithm.
     *
     * @param score the score wrapper
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Returns the independence-test wrapper currently used by this algorithm.
     *
     * @return the independence-test wrapper
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the independence-test wrapper for this algorithm.
     *
     * @param independenceWrapper the independence-test wrapper
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    /**
     * Applies the detect-mimic post-processing step to an already computed graph.
     *
     * <p>The directed part of the graph is treated as a source of candidate parent-to-child
     * relations. The method searches for complete directed bipartite patterns between a
     * candidate measured-parent set and a candidate measured-child set. When such a candidate
     * also passes an orientation-based legitimacy test, a new latent variable is inserted
     * between the parent set and child set, the direct measured edges are removed, and FCI
     * orientation is re-applied. Finally, latent-to-latent edges are added using subset
     * inclusion of the parent sets.</p>
     *
     * @param graph the graph produced by FCIT
     * @param test the independence test used to assess latent plausibility
     * @return the graph after detect-mimic latent insertion
     */
    private static Graph getDmGraph(Graph graph, IndependenceTest test) {
        FciOrient fciOrient = new FciOrient(new R0R4StrategyTestBased(test));
        Graph dmGraph = new EdgeListGraph(graph);

        Graph potentiallyDirected = new EdgeListGraph(dmGraph.getNodes());

        for (Edge edge : dmGraph.getEdges()) {
            if (edge.pointsTowards(edge.getNode2())) {
                potentiallyDirected.addDirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        int latentCounter = 1;
        Map<Set<Node>, Node> latentNodes = new HashMap<>();

        for (Node node : potentiallyDirected.getNodes()) {
            Set<Node> possibleChildren = new HashSet<>(potentiallyDirected.getChildren(node));
            Set<Node> possibleParents = collectPossibleParents(possibleChildren, potentiallyDirected);

            expandPossibleChildrenFromParents(possibleChildren, possibleParents, potentiallyDirected);

            List<Node> parentList = new ArrayList<>(possibleParents);
            List<Node> childList = new ArrayList<>(possibleChildren);

            SublistGenerator parentGenerator = new SublistGenerator(parentList.size(), parentList.size());
            int[] parentChoice;

            searchForLatent:
            while ((parentChoice = parentGenerator.next()) != null) {
                Set<Node> parents = complement(parentChoice, parentList);

                if (parents.isEmpty()) {
                    continue;
                }

                SublistGenerator childGenerator = new SublistGenerator(childList.size(), childList.size());
                int[] childChoice;

                while ((childChoice = childGenerator.next()) != null) {
                    Set<Node> children = complement(childChoice, childList);

                    if (children.isEmpty()) {
                        continue;
                    }

                    if (!formsCompleteParentChildPattern(parents, children, potentiallyDirected)) {
                        continue;
                    }

                    if (!confirmLatentUsingOrientation(dmGraph, parents, children, test)) {
                        continue;
                    }

                    GraphNode latent = new GraphNode("L" + latentCounter++);
                    latent.setNodeType(NodeType.LATENT);
                    dmGraph.addNode(latent);

                    latentNodes.put(new HashSet<>(parents), latent);

                    insertLatentBetweenParentsAndChildren(dmGraph, latent, parents, children);

                    for (Node parent : parents) {
                        for (Node child : children) {
                            potentiallyDirected.removeEdge(parent, child);
                        }
                    }

                    fciOrient.finalOrientation(dmGraph);
                    break searchForLatent;
                }
            }
        }

        orientLatentEdges(dmGraph, latentNodes);
        LayoutUtil.repositionLatents(dmGraph);

        return dmGraph;
    }

    /**
     * Collects candidate parents by taking the union of the parents of the supplied
     * candidate children in the potentially directed graph.
     *
     * @param possibleChildren the current candidate children
     * @param potentiallyDirected the graph containing only directed candidate edges
     * @return the union of parents of the candidate children
     */
    private static Set<Node> collectPossibleParents(Set<Node> possibleChildren, Graph potentiallyDirected) {
        Set<Node> possibleParents = new HashSet<>();

        for (Node child : possibleChildren) {
            possibleParents.addAll(potentiallyDirected.getParents(child));
        }

        return possibleParents;
    }

    /**
     * Expands the candidate-child set by taking the union of the children of the supplied
     * candidate parents in the potentially directed graph.
     *
     * @param possibleChildren the current candidate-child set, updated in place
     * @param possibleParents the current candidate-parent set
     * @param potentiallyDirected the graph containing only directed candidate edges
     */
    private static void expandPossibleChildrenFromParents(Set<Node> possibleChildren, Set<Node> possibleParents,
                                                          Graph potentiallyDirected) {
        for (Node parent : possibleParents) {
            possibleChildren.addAll(potentiallyDirected.getChildren(parent));
        }
    }

    /**
     * Returns the complement subset determined by removing the chosen indices from the supplied list.
     *
     * <p>The original code enumerates complements in this way by starting from the full set and
     * removing the chosen elements.</p>
     *
     * @param choice the indices to remove
     * @param nodes the base list of nodes
     * @return the complement subset
     */
    private static Set<Node> complement(int[] choice, List<Node> nodes) {
        List<Node> removed = GraphUtils.asList(choice, nodes);
        Set<Node> kept = new HashSet<>(nodes);
        removed.forEach(kept::remove);
        return kept;
    }

    /**
     * Inserts a latent variable between each parent in the supplied parent set and each child
     * in the supplied child set.
     *
     * <p>For each parent-child pair, any direct edge is removed. Then directed edges are added
     * from the parent to the latent and from the latent to the child.</p>
     *
     * @param graph the graph to modify
     * @param latent the new latent variable
     * @param parents the measured parents
     * @param children the measured children
     */
    private static void insertLatentBetweenParentsAndChildren(Graph graph, Node latent, Set<Node> parents,
                                                              Set<Node> children) {
        for (Node parent : parents) {
            for (Node child : children) {
                graph.removeEdge(parent, child);

                if (!graph.isAdjacentTo(parent, latent)) {
                    graph.addDirectedEdge(parent, latent);
                }

                if (!graph.isAdjacentTo(latent, child)) {
                    graph.addDirectedEdge(latent, child);
                }
            }
        }
    }

    /**
     * Returns true if every parent-child pair in the Cartesian product of the supplied
     * parent and child sets appears as an edge in the potentially directed graph.
     *
     * @param parents the candidate parents
     * @param children the candidate children
     * @param potentiallyDirected the graph containing only directed candidate edges
     * @return true if the parent-child pattern is complete, false otherwise
     */
    private static boolean formsCompleteParentChildPattern(Set<Node> parents, Set<Node> children,
                                                           Graph potentiallyDirected) {
        for (Node parent : parents) {
            for (Node child : children) {
                Edge edge = potentiallyDirected.getEdge(parent, child);

                if (edge == null) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Orients edges among latent nodes using subset inclusion of the measured parent sets
     * associated with those latents.
     *
     * <p>If one measured-parent set is a strict subset of another, the latent corresponding
     * to the smaller set is directed into the latent corresponding to the larger set, unless
     * doing so would create an ancestor conflict.</p>
     *
     * @param graph the graph to modify
     * @param latentNodes a map from measured-parent sets to their latent nodes
     */
    private static void orientLatentEdges(Graph graph, Map<Set<Node>, Node> latentNodes) {
        List<Set<Node>> parentSets = new ArrayList<>(latentNodes.keySet());

        for (Set<Node> setA : parentSets) {
            for (Set<Node> setB : parentSets) {
                if (setA.equals(setB)) {
                    continue;
                }

                if (setA.containsAll(setB)) {
                    Node latentFrom = latentNodes.get(setB);
                    Node latentTo = latentNodes.get(setA);

                    if (latentFrom != null && latentTo != null && !graph.isAncestorOf(latentTo, latentFrom)) {
                        graph.addDirectedEdge(latentFrom, latentTo);
                    }
                }
            }
        }
    }

    /**
     * Returns true if the candidate latent remains plausible according to orientation-guided
     * child-child dependence checks.
     *
     * <p>For each ordered pair of distinct candidate children, a conditioning set is chosen
     * using recursive blocking from the current graph. The two children are then tested for
     * independence given that conditioning set. If any such child pair is found independent,
     * the candidate latent is rejected. Otherwise the candidate latent is accepted.</p>
     *
     * @param graph the current graph
     * @param parents the candidate measured parents
     * @param children the candidate measured children
     * @param test the independence test
     * @return true if the candidate latent passes the check, false otherwise
     */
    private static boolean confirmLatentUsingOrientation(Graph graph, Set<Node> parents, Set<Node> children,
                                                         IndependenceTest test) {
        try {
            for (Node childA : children) {
                for (Node childB : children) {
                    if (childA.equals(childB)) {
                        continue;
                    }

                    Set<Node> conditioningSet = getMinimalConditioningSet(graph, childA, childB, parents);
                    IndependenceResult result = test.checkIndependence(childA, childB, conditioningSet);

                    if (result.isIndependent()) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed while confirming a latent candidate.", e);
        }
    }

    /**
     * Computes a conditioning set used to test a pair of candidate children.
     *
     * <p>The current implementation delegates to recursive blocking on the current graph.
     * The supplied parent set is not directly used here, but it is retained in the method
     * signature because it is conceptually part of the candidate latent specification and
     * may be useful for later refinements.</p>
     *
     * @param graph the current graph
     * @param childA the first child
     * @param childB the second child
     * @param parents the candidate measured parents
     * @return a conditioning set returned by recursive blocking
     * @throws InterruptedException if recursive blocking is interrupted
     */
    private static Set<Node> getMinimalConditioningSet(Graph graph, Node childA, Node childB, Set<Node> parents)
            throws InterruptedException {
        return RecursiveBlocking.blockPathsRecursively(graph, childA, childB, new HashSet<>(), new HashSet<>(), -1);
    }
}