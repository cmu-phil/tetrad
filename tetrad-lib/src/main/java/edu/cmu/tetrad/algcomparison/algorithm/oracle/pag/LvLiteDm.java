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
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.SublistGenerator;

import java.io.Serial;
import java.util.*;


/**
 * This class represents the LV-Lite algorithm, which is variant of the *-FCI algorithm for learning causal structures
 * from observational data using the BOSS algorithm as an initial CPDAG and using all score-based steps afterward.
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Lv-Lite-DM",
        command = "lv-lite-dm",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
@Experimental
public class LvLiteDm extends AbstractBootstrapAlgorithm implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper,
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
     * This class represents a LV-Lite algorithm.
     *
     * <p>
     * The LV-Lite algorithm is a bootstrap algorithm that runs a search algorithm to find a graph structure based on a
     * given data set and parameters. It is a subclass of the Abstract BootstrapAlgorithm class and implements the
     * Algorithm interface.
     * </p>
     *
     * @see AbstractBootstrapAlgorithm
     * @see Algorithm
     */
    public LvLiteDm() {
        // Used for reflection; do not delete.
    }

    /**
     * LV-Lite is a class that represents a LV-Lite algorithm.
     *
     * <p>
     * The LV-Lite algorithm is a bootstrap algorithm that runs a search algorithm to find a graph structure based on a
     * given data set and parameters. It is a subclass of the AbstractBootstrapAlgorithm class and implements the
     * Algorithm interface.
     * </p>
     *
     * @param test  The independence test to use.
     * @param score The score to use.
     * @see AbstractBootstrapAlgorithm
     * @see Algorithm
     */
    public LvLiteDm(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
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
            if (parameters.getInt(Params.LV_LITE_STARTS_WITH) == 1) {
                throw new IllegalArgumentException("For d-separation oracle input, please use the GRaSP option.");
            }
        }

        edu.cmu.tetrad.search.LvLite search = new edu.cmu.tetrad.search.LvLite(test, score);

        // BOSS
        search.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        search.setUseBes(parameters.getBoolean(Params.USE_BES));

        // FCI-ORIENT
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));

        // LV-Lite
        search.setRecursionDepth(parameters.getInt(Params.GRASP_DEPTH));
        search.setMaxBlockingPathLength(parameters.getInt(Params.MAX_BLOCKING_PATH_LENGTH));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setMaxDdpPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
        search.setTestTimeout(parameters.getLong(Params.TEST_TIMEOUT));
        search.setGuaranteePag(parameters.getBoolean(Params.GUARANTEE_PAG));
        search.setDoDdpEdgeRemovalStep(parameters.getBoolean(Params.DO_DDP_EDGE_REMOVAL_STEP));
        search.setEnsureMarkov(parameters.getBoolean(Params.ENSURE_MARKOV));

        if (parameters.getInt(Params.LV_LITE_STARTS_WITH) == 1) {
            search.setStartWith(edu.cmu.tetrad.search.LvLite.START_WITH.BOSS);
        } else if (parameters.getInt(Params.LV_LITE_STARTS_WITH) == 2) {
            search.setStartWith(edu.cmu.tetrad.search.LvLite.START_WITH.GRASP);
        } else if (parameters.getInt(Params.LV_LITE_STARTS_WITH) == 3) {
            search.setStartWith(edu.cmu.tetrad.search.LvLite.START_WITH.SP);
        } else {
            throw new IllegalArgumentException("Unknown start with option: " + parameters.getInt(Params.LV_LITE_STARTS_WITH));
        }

        // General
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setKnowledge(this.knowledge);

        Graph graph = search.search();

        return getGraph(graph, test);
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
        return "Lv-Lite-DM using " + this.score.getDescription();
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

        // LV-Lite
        params.add(Params.LV_LITE_STARTS_WITH);
        params.add(Params.GRASP_DEPTH);
        params.add(Params.MAX_BLOCKING_PATH_LENGTH);
        params.add(Params.DEPTH);
        params.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        params.add(Params.GUARANTEE_PAG);
        params.add(Params.DO_DDP_EDGE_REMOVAL_STEP);
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

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    private static Graph getGraph(Graph graph,  IndependenceTest test) {
        graph = new EdgeListGraph(graph);

        Map<Set<Node>, Set<Node>> cartesianProducts = new HashMap<>();

        Graph possiblyDirected = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (edge.pointsTowards(edge.getNode2())) {
                possiblyDirected.addDirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

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
                Set<Node> comp1 = new HashSet<>(_possibleParents);
                a1.forEach(comp1::remove);
                if (comp1.isEmpty()) continue;

                SublistGenerator gen2 = new SublistGenerator(_possibleChildren.size(), _possibleChildren.size());
                int[] choice2;

                C:
                while ((choice2 = gen2.next()) != null) {
                    List<Node> a2 = GraphUtils.asList(choice2, _possibleChildren);
                    Set<Node> comp2 = new HashSet<>(_possibleChildren);
                    a2.forEach(comp2::remove);
                    if (comp2.isEmpty()) continue;

                    for (Node p : comp1) {
                        for (Node c : comp2) {
                            Edge e = possiblyDirected.getEdge(p, c);
                            if (e == null) continue C;
                        }
                    }

                    // DM CHECK: Confirm legitimate latent
                    if (confirmLatent(comp1, comp2, test)) {
                        cartesianProducts.put(new HashSet<>(comp1), new HashSet<>(comp2));

                        // Remove edges from comp1 to comp2 from possiblyDirected
                        for (Node p : comp1) {
                            for (Node c : comp2) {
                                possiblyDirected.removeEdge(p, c);
                            }
                        }

                        break W;
                    }
                }
            }
        }

        Map<Set<Node>, Node> latentNodes = new HashMap<>();
        int latentCounter = 1;

        for (Set<Node> parents : cartesianProducts.keySet()) {
            Set<Node> children = cartesianProducts.get(parents);

            if (!parents.isEmpty() && !children.isEmpty()) {
                GraphNode newNode = new GraphNode("L" + latentCounter++);
                newNode.setNodeType(NodeType.LATENT);
                graph.addNode(newNode);
                latentNodes.put(parents, newNode);

                for (Node p : parents) {
                    for (Node c : children) {
                        graph.removeEdge(p, c);
                        graph.addDirectedEdge(p, newNode);
                        graph.addDirectedEdge(newNode, c);
                    }
                }
            }
        }

        // DM CHECK: Orient latent-to-latent edges using subset-inclusion logic
        orientLatentEdges(graph, latentNodes);
        LayoutUtil.repositionLatents(graph);
        return graph;
    }

    private static boolean confirmLatent(Set<Node> parents, Set<Node> children, IndependenceTest test) {
        try {
            for (Node childA : children) {
                for (Node childB : children) {
                    if (childA.equals(childB)) continue;
                    IndependenceResult result = test.checkIndependence(childA, childB, parents);
                    if (result.isIndependent()) {
                        return false; // Independence found, latent is NOT legitimate
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return true; // No independence found, latent is legitimate
    }

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

}
