package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fcit;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.SublistGenerator;

import java.io.Serial;
import java.util.*;


/**
 * This class represents the Detect-Mimic-FCIT (DM-FCIT) algorithm, a specialized variant of the DM-PC and FCIT
 * algorithms designed to identify intermediate latent variables. DM-FCIT enhances accuracy and computational efficiency
 * by recursively maintaining complete PAG orientations during the search process. At each step, it uses these
 * orientations to substantially reduce the required size of conditioning sets when testing independence. This approach
 * leads to more precise identification of latent variables and better orientation accuracy overall.
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "DM-FCIT",
        command = "dm-FCIT",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
@Experimental
public class DmFcit extends AbstractBootstrapAlgorithm implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper,
        HasKnowledge, ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;
    /**
     * The score to use.
     */
    private ScoreWrapper score;
    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * This class represents a DM-FCIT algorithm.
     *
     * <p>
     * The DM-FCIT algorithm is a bootstrap algorithm that runs a search algorithm to find a graph structure based on a
     * given data set and parameters. It is a subclass of the Abstract BootstrapAlgorithm class and implements the
     * Algorithm interface.
     * </p>
     *
     * @see AbstractBootstrapAlgorithm
     * @see Algorithm
     */
    public DmFcit() {
        // Used for reflection; do not delete.
    }

    /**
     * Represents a DM-FCIT algorithm.
     *
     * <p>
     * The DM-FCIT algorithm is a bootstrap algorithm that runs a search algorithm to find a graph structure based on a
     * given data set and parameters. It is a subclass of the AbstractBootstrapAlgorithm class and implements the
     * Algorithm interface.
     * </p>
     *
     * @param test  The independence test to use.
     * @param score The score to use.
     * @see AbstractBootstrapAlgorithm
     * @see Algorithm
     */
    public DmFcit(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Constructs and modifies a graph to identify latent variables and adjust directed edges based on the provided
     * independence test. Applies orientation rules and models relationships between observed and latent variables.
     *
     * @param graph The input graph that represents the relationships among the observed variables.
     * @param test  An independence test used for determining conditional independencies in the graph.
     * @return A modified graph with latent variables identified and directed edges appropriately adjusted.
     */
    private static Graph getDmGraph(Graph graph, IndependenceTest test) {
        FciOrient fciOrient = new FciOrient(new R0R4StrategyTestBased(test));
        graph = new EdgeListGraph(graph);

        Graph possiblyDirected = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (edge.pointsTowards(edge.getNode2())) {
                possiblyDirected.addDirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        int latentCounter = 1;
        Map<Set<Node>, Node> latentNodes = new HashMap<>();

        for (Node x : possiblyDirected.getNodes()) {
            Set<Node> possibleChildren = new HashSet<>(possiblyDirected.getChildren(x));
            Set<Node> possibleParents = new HashSet<>();

            for (Node p : possibleChildren) {
                possibleParents.addAll(possiblyDirected.getParents(p));
            }

            for (Node c : possibleParents) {
                possibleChildren.addAll(possiblyDirected.getChildren(c));
            }

            List<Node> _possibleParents = new ArrayList<>(possibleParents);
            List<Node> _possibleChildren = new ArrayList<>(possibleChildren);

            SublistGenerator gen1 = new SublistGenerator(_possibleParents.size(), _possibleParents.size());
            int[] choice1;

            W:
            while ((choice1 = gen1.next()) != null) {
                List<Node> a1 = GraphUtils.asList(choice1, _possibleParents);
                Set<Node> parents = new HashSet<>(_possibleParents);
                a1.forEach(parents::remove);
                if (parents.isEmpty()) continue;

                SublistGenerator gen2 = new SublistGenerator(_possibleChildren.size(), _possibleChildren.size());
                int[] choice2;

                C:
                while ((choice2 = gen2.next()) != null) {
                    List<Node> a2 = GraphUtils.asList(choice2, _possibleChildren);
                    Set<Node> children = new HashSet<>(_possibleChildren);
                    a2.forEach(children::remove);
                    if (children.isEmpty()) continue;

                    if (!cartesianProduct(parents, children, possiblyDirected)) continue;

                    // Verify that the candidate latent node meets orientation-based legitimacy criteria.
                    if (confirmLatentUsingOrientation(graph, parents, children, test)) {
                        if (!parents.isEmpty() && !children.isEmpty()) {
                            GraphNode newLatent = new GraphNode("L" + latentCounter++);
                            newLatent.setNodeType(NodeType.LATENT);
                            graph.addNode(newLatent);

                            latentNodes.put(parents, newLatent);

                            for (Node p : parents) {
                                for (Node c : children) {
                                    graph.removeEdge(p, c);
                                    graph.addDirectedEdge(p, newLatent);
                                    graph.addDirectedEdge(newLatent, c);
                                }
                            }

                            fciOrient.finalOrientation(graph);
                        }

                        // Remove edges from parents to children from possiblyDirected
                        for (Node p : parents) {
                            for (Node c : children) {
                                possiblyDirected.removeEdge(p, c);
                            }
                        }

                        break W;
                    }
                }
            }
        }

        // Add latent-to-latent edges based on subset-inclusion logic.
        orientLatentEdges(graph, latentNodes);
        LayoutUtil.repositionLatents(graph);
        return graph;
    }

    /**
     * Checks if there is an edge in the given graph for every pair of nodes from the Cartesian product of the provided
     * parents and children sets.
     *
     * @param parents          The set of parent nodes to be considered.
     * @param children         The set of child nodes to be considered.
     * @param possiblyDirected The graph in which the edges are checked. The graph may or may not be directed.
     * @return True if there is an edge in the graph for every pair of nodes from the Cartesian product of parents and
     * children; false otherwise.
     */
    private static boolean cartesianProduct(Set<Node> parents, Set<Node> children, Graph possiblyDirected) {
        for (Node p : parents) {
            for (Node c : children) {
                Edge e = possiblyDirected.getEdge(p, c);
                if (e == null) return false;
            }
        }
        return true;
    }

    /**
     * Orients the edges between latent nodes in the given graph based on the relationships among sets of observed
     * variables associated with latent nodes. Ensures that directed edges from less inclusive to more inclusive sets
     * are added, maintaining consistency in the graph structure.
     *
     * @param graph       The graph in which the latent node edges are to be oriented. The graph represents
     *                    relationships among observed and latent variables.
     * @param latentNodes A mapping of sets of observed variables to their corresponding latent node in the graph. Keys
     *                    are sets of observed variables, and values are the associated latent nodes.
     */
    private static void orientLatentEdges(Graph graph, Map<Set<Node>, Node> latentNodes) {
        List<Set<Node>> keys = new ArrayList<>(latentNodes.keySet());
        for (Set<Node> setA : keys) {
            for (Set<Node> setB : keys) {
                if (setA.equals(setB)) continue;
                if (setA.containsAll(setB)) {
                    Node latentFrom = latentNodes.get(setB);
                    Node latentTo = latentNodes.get(setA);

                    if (!graph.isAncestorOf(latentTo, latentFrom)) {
                        graph.addDirectedEdge(latentFrom, latentTo);
                    }
                }
            }
        }
    }

    /**
     * Checks if a candidate latent is legitimate, using recursively maintained PAG orientation to dramatically reduce
     * the conditioning set.
     *
     * @param graph    The current oriented PAG structure.
     * @param parents  The candidate parents.
     * @param children The candidate children.
     * @param test     The independence test.
     * @return True if the latent is legitimate, false otherwise.
     */
    private static boolean confirmLatentUsingOrientation(Graph graph, Set<Node> parents, Set<Node> children, IndependenceTest test) {
        try {
            for (Node childA : children) {
                for (Node childB : children) {
                    if (childA.equals(childB)) continue;

                    // Identify minimal conditioning set using the current PAG orientation
                    Set<Node> minimalParents = getMinimalConditioningSet(graph, childA, childB, parents);

                    // Perform independence test using a minimal conditioning set
                    IndependenceResult result = test.checkIndependence(childA, childB, minimalParents);

                    if (result.isIndependent()) {
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // No independence found, latent is legitimate
        return true;
    }

    /**
     * Identifies a minimal conditioning subset from parents sufficient to block all back-door paths between childA and
     * childB based on the current orientation of the PAG.
     *
     * @param graph   The oriented PAG structure.
     * @param childA  First child node.
     * @param childB  Second child node.
     * @param parents Set of potential parent nodes.
     * @return A minimal conditioning subset of parents.
     */
    private static Set<Node> getMinimalConditioningSet(Graph graph, Node childA, Node childB, Set<Node> parents)
            throws InterruptedException {
        return SepsetFinder.blockPathsRecursively(
                graph, childA, childB, new HashSet<>(),  new HashSet<>(), -1
        ).getLeft();
    }

    /**
     * Runs the search algorithm to find a graph structure based on a given data model and parameters.
     *
     * @param dataModel  The data model to use for the search algorithm.
     * @param parameters The parameters to configure the search algorithm.
     * @return The resulting graph structure.
     * @throws IllegalArgumentException if the time lag is greater than 0 and the data model is not an instance of
     *                                  DataSet.
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
            knowledge = timeSeries.getKnowledge();
        }

        IndependenceTest test = this.test.getTest(dataModel, parameters);
        Score score = this.score.getScore(dataModel, parameters);

        if (test instanceof MsepTest) {
            if (parameters.getInt(Params.FCIT_STARTS_WITH) == 1) {
                throw new IllegalArgumentException("For d-separation oracle input, please use the GRaSP option.");
            }
        }

        Fcit search = new Fcit(test, score);

        // BOSS
        search.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        search.setUseBes(parameters.getBoolean(Params.USE_BES));

        // FCI-ORIENT
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));

        // FCIT
        search.setMaxBlockingPathLength(parameters.getInt(Params.MAX_BLOCKING_PATH_LENGTH));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setMaxDdpPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
        search.setEnsureMarkov(parameters.getBoolean(Params.ENSURE_MARKOV));

        if (parameters.getInt(Params.FCIT_STARTS_WITH) == 1) {
            search.setStartWith(Fcit.START_WITH.BOSS);
        } else if (parameters.getInt(Params.FCIT_STARTS_WITH) == 2) {
            search.setStartWith(Fcit.START_WITH.GRASP);
        } else if (parameters.getInt(Params.FCIT_STARTS_WITH) == 3) {
            search.setStartWith(Fcit.START_WITH.SP);
        } else {
            throw new IllegalArgumentException("Unknown start with option: " + parameters.getInt(Params.FCIT_STARTS_WITH));
        }

        // General
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setKnowledge(this.knowledge);

        Graph graph = search.search();

        return getDmGraph(graph, test);
    }

    /**
     * Retrieves a comparison graph by transforming a true directed graph into a partially directed graph (PAG).
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph);
    }

    /**
     * Returns a short, one-line description of this algorithm. The description is generated by concatenating the
     * descriptions of the test and score objects associated with this algorithm.
     *
     * @return The description of this algorithm.
     */
    @Override
    public String getDescription() {
        return "DM-FCIT using " + this.score.getDescription();
    }

    /**
     * Retrieves the data type required by the search algorithm.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * Retrieves the list of parameters used by the algorithm.
     *
     * @return The list of parameters used by the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        // BOSS
        params.add(Params.USE_BES);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.NUM_STARTS);

        // FCI-ORIENT
        params.add(Params.COMPLETE_RULE_SET_USED);

        // FCIT
        params.add(Params.FCIT_STARTS_WITH);
        params.add(Params.GRASP_DEPTH);
        params.add(Params.MAX_BLOCKING_PATH_LENGTH);
        params.add(Params.DEPTH);
        params.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        params.add(Params.GUARANTEE_PAG);
//        params.add(Params.DO_DDP_EDGE_REMOVAL_STEP);
        params.add(Params.ENSURE_MARKOV);

        // General
        params.add(Params.TIME_LAG);
        params.add(Params.VERBOSE);
        params.add(Params.TEST_TIMEOUT);

        return params;
    }

    /**
     * Retrieves the knowledge object associated with this method.
     *
     * @return The knowledge object.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object associated with this method.
     *
     * @param knowledge the knowledge object to be set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the ScoreWrapper object associated with this method.
     *
     * @return The ScoreWrapper object associated with this method.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for the algorithm.
     *
     * @param score the score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Retrieves the IndependenceWrapper object associated with this instance.
     *
     * @return The IndependenceWrapper object that represents the independence test used.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return test;
    }

    /**
     * Sets the IndependenceWrapper object for this algorithm. The IndependenceWrapper represents the independence test
     * to be used in the algorithm's operations.
     *
     * @param independenceWrapper the IndependenceWrapper object to set
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }
}

