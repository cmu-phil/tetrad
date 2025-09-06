package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The Detect-Mimic-PC (DM-PC) algorithm. This is intended to detect intermediate latent variables for Multiple Input *
 * Multiple IndiCator (MIMIC) models. models. This implements a causal discovery algorithm for detecting and
 * representing intermediate latent variables and their causal relationships in a dataset. The algorithm utilizes
 * constraint-based causal discovery techniques, clustering, and structure refinement to build a causal graph that
 * incorporates latent variables.
 * <p>
 * The class requires an `IndependenceTest` to perform statistical independence checks on variable pairs or groups,
 * which is central to its operation.
 *
 * @author murraywaters
 * @author josephramsey
 */
public class DmPc {

    /**
     * An instance of {@link IndependenceTest} used to perform conditional independence tests. This variable serves as
     * the primary testing mechanism within the constraint-based search algorithms of the containing class.
     * <p>
     * It is initialized through constructor dependency injection and is expected to implement methods for testing
     * independence, retrieving variables, and managing the configuration (such as significance level) necessary for the
     * tests.
     */
    private final IndependenceTest test;
    /**
     * A list of input nodes used within the algorithm. These nodes represent the variables that are considered as
     * potential causes or predictors within the graph structure.
     * <p>
     * This list is immutable after initialization and is populated with instances of {@link Node}, which serve as
     * elements in the causal structure learning process.
     */
    private final List<Node> inputs = new ArrayList<>();
    /**
     * A list of output {@link Node} objects associated with the search or classification process. This collection
     * stores nodes that are identified as outputs within the context of the graph manipulation and structure learning
     * procedures.
     */
    private final List<Node> outputs = new ArrayList<>();
    /**
     * Represents domain-specific knowledge used during the causal discovery process in the DmPc class. This variable is
     * utilized to impose background knowledge and constraints on the structural search process, such as required or
     * forbidden edges in the output graph.
     * <p>
     * The `knowledge` field stores predefined information that is essential for guiding the algorithm to refine its
     * search space according to user or domain constraints.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Represents the index value used to identify or differentiate latent variables during the structure search process
     * within the PC algorithm implementation. The index is initialized to 1 and may be incremented or updated
     * dynamically as latent variables are created or managed throughout the execution of the algorithm.
     */
    private int latentIndex = 1;

    /**
     * Constructs an instance of the DmPc class using the specified independence test.
     *
     * @param test An instance of the {@link IndependenceTest} interface, used to perform conditional independence tests
     *             as part of the algorithm.
     */
    public DmPc(IndependenceTest test) {
        this.test = test;
    }

    /**
     * Executes the Directed Maximal PC (DmPc) algorithm to identify a causal graph structure that represents the
     * relationships between observed and latent variables. The method performs several steps including initialization,
     * clustering, introducing latent nodes, refining edges, and final adjustments to produce the resultant graph.
     *
     * @return A causal {@link Graph} that represents the inferred structure of relationships among variables,
     * incorporating both observed and latent variables.
     */
    public Graph search() {

        // Step 1: Identify inputs and outputs.
        Graph result;

        try {
            Pc pc = new Pc(test);
            pc.setDepth(0);
            pc.setKnowledge(knowledge);
            result = pc.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Graph initialGraph = result;

        classifyVariables(initialGraph);

        // Step 2: Build sets of outputs for each input.
        Map<Set<Node>, Set<Node>> latentOutputClusters = clusterOutputs(initialGraph);

        // Step 3: Introduce latent nodes and build graph.
        Graph latentGraph = buildLatentStructure(latentOutputClusters);

        // Step 4: Refine latent-latent edges using conditional xindependence.
        refineLatentEdges(latentGraph);

        // Step 5: Final refinement using PC algorithm (full depth).
        finalRefinement(latentGraph);

        return latentGraph;
    }

    /**
     * Classifies the variables in the given graph into input or output nodes based on their indegree and outdegree.
     * Nodes with zero indegree and non-zero outdegree are classified as input nodes, while nodes with a non-zero
     * indegree are classified as output nodes.
     *
     * @param pattern The graph whose nodes are to be classified into input and output variables.
     */
    private void classifyVariables(Graph pattern) {
        for (Node node : pattern.getNodes()) {
            int indegree = pattern.getIndegree(node);
            int outdegree = pattern.getOutdegree(node);

            if (indegree == 0 && outdegree > 0) {
                inputs.add(node);
            } else if (indegree > 0) {
                outputs.add(node);
            }
            // Variables with no edges can be handled separately if needed.
        }
    }

    /**
     * Groups output nodes into clusters based on their associated input nodes. Each cluster is represented as an entry
     * in the resulting map, where the key is a set of associated input nodes, and the value is a list of output nodes
     * that share those inputs.
     *
     * @return A map where each key is a set of input nodes and the corresponding value is a list of output nodes that
     * are associated with those input nodes.
     */
    private Map<Set<Node>, Set<Node>> clusterOutputs(Graph initialGraph) {
        Map<Set<Node>, Set<Node>> clusters = new HashMap<>();

        for (Node output : outputs) {
            if (output.getNodeType() != NodeType.LATENT) {
                Set<Node> associatedInputs = getAssociatedInputs(output, initialGraph);
                clusters.computeIfAbsent(associatedInputs, k -> new HashSet<>()).add(output);
            }
        }

        return clusters;
    }

    private Set<Node> getAssociatedInputs(Node output, Graph pcDepth0) {
        Set<Node> associatedInputs = new HashSet<>();

        for (Node input : inputs) {
            if (pcDepth0.isAdjacentTo(input, output)) {
                associatedInputs.add(input);
            }
        }

        return associatedInputs;
    }

    /**
     * Constructs a graph that incorporates latent variables, connecting them to input and output nodes based on the
     * provided clusters of variables. Latent variables are created based on sets of input nodes, and directed edges are
     * established between related nodes. Additionally, hierarchical relationships between latent variables are added
     * based on subset relations within the clusters.
     *
     * @param clusters A map where each key is a set of input {@link Node} objects and the corresponding value is a list
     *                 of output {@link Node} objects associated with those inputs.
     * @return A {@link Graph} containing the latent structure, including the input and output nodes, latent variables,
     * and directed edges connecting them based on their relationships.
     */
    private Graph buildLatentStructure(Map<Set<Node>, Set<Node>> clusters) {
        Graph graph = new EdgeListGraph();

        // Add input and output nodes.
        for (Node input : inputs) graph.addNode(input);
        for (Node output : outputs) graph.addNode(output);

        Map<Set<Node>, Node> latentNodes = new HashMap<>();

        // Create latents and connect them.
        for (Set<Node> inputSet : clusters.keySet()) {
            Set<Node> outputSet = clusters.get(inputSet);

            if (!inputSet.isEmpty() && !outputSet.isEmpty()) {
                Node latent = createLatentNode();
                latentNodes.put(inputSet, latent);
                graph.addNode(latent);

                for (Node input : inputSet) graph.addDirectedEdge(input, latent);
                for (Node output : outputSet) graph.addDirectedEdge(latent, output);
            }
        }

        // Connect latent-latent based on subset relations.
        for (Set<Node> setA : clusters.keySet()) {
            for (Set<Node> setB : clusters.keySet()) {
                if (!setA.equals(setB) && setA.containsAll(setB)) {
                    graph.addDirectedEdge(latentNodes.get(setB), latentNodes.get(setA));
                }
            }
        }

        return graph;
    }

    /**
     * Creates a latent node with a unique name and sets its type to latent. This method is typically used to introduce
     * a latent variable into the causal graph being constructed.
     *
     * @return A newly created {@link Node} object representing the latent variable.
     */
    private Node createLatentNode() {
        String latentName = "L" + latentIndex++;
        Node latent = new GraphNode(latentName);
        latent.setNodeType(NodeType.LATENT);
        return latent;
    }

    /**
     * Determines whether a latent edge can be removed between two latent nodes in a graph. The method evaluates if the
     * output nodes of the two latent nodes are conditionally independent given their combined input nodes. If
     * independence holds, the latent edge can be removed.
     *
     * @param latentA The first latent node being evaluated.
     * @param latentB The second latent node being evaluated.
     * @param graph   The graph structure containing the latent nodes and their relationships.
     * @return {@code true} if the edge between the two latent nodes can be removed based on conditional independence;
     * {@code false} otherwise.
     */
    private boolean canRemoveLatentEdge(Node latentA, Node latentB, Graph graph) {
        Set<Node> inputsToA = getInputNodes(latentA, graph);
        Set<Node> inputsToB = getInputNodes(latentB, graph);

        Set<Node> combinedInputs = new HashSet<>();
        combinedInputs.addAll(inputsToA);
        combinedInputs.addAll(inputsToB);

        Set<Node> outputsA = getOutputNodes(latentA, graph);
        Set<Node> outputsB = getOutputNodes(latentB, graph);

        return conditionalIndependent(outputsA, outputsB, combinedInputs);
    }

    /**
     * Removes nodes of type {@link NodeType#LATENT} from the provided set of nodes.
     *
     * @param set A set of {@link Node} objects to be filtered, potentially containing latent nodes.
     * @return A set of {@link Node} objects excluding any nodes of type {@link NodeType#LATENT}.
     */
    private Set<Node> removeLatents(Set<Node> set) {
        return set.stream()
                .filter(n -> n.getNodeType() != NodeType.LATENT)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves the input nodes of a specified node in a given graph. An input node is defined as a parent of the
     * specified node in the graph.
     *
     * @param node  The node for which input nodes are to be retrieved.
     * @param graph The graph structure containing the relationships between nodes.
     * @return A set of nodes representing the input nodes (parents) of the specified node in the graph.
     */
    private Set<Node> getInputNodes(Node node, Graph graph) {
        Set<Node> nodes = new HashSet<>(graph.getParents(node));
        nodes = removeLatents(nodes);
        return nodes;
    }

    /**
     * Retrieves the output nodes of a specified node in a given graph. An output node is defined as a child of the
     * specified node node in the graph.
     *
     * @param node  The node {@link Node} for which output nodes are to be retrieved.
     * @param graph The {@link Graph} structure containing the relationships between nodes.
     * @return A set of {@link Node} objects representing the output nodes (children) of the specified node in the
     * graph.
     */
    private Set<Node> getOutputNodes(Node node, Graph graph) {
        Set<Node> nodes = new HashSet<>(graph.getChildren(node));
        nodes = removeLatents(nodes);
        return nodes;
    }

    /**
     * Determines whether all pairs of nodes between two sets are conditionally independent given a third "conditioning"
     * set. Latent variables are removed from all input sets before performing the conditional independence check.
     *
     * @param setA     The first set of {@link Node} objects to be tested for independence against the second set.
     * @param setB     The second set of {@link Node} objects to be tested for independence against the first set.
     * @param givenSet The set of {@link Node} objects that serves as the conditioning set for the independence test.
     * @return {@code true} if all pairs of nodes between the first and second sets are found to be conditionally
     * independent given the conditioning set; {@code false} otherwise.
     */
    private boolean conditionalIndependent(Set<Node> setA, Set<Node> setB, Set<Node> givenSet) {
        Set<Node> cleanSetA = removeLatents(setA);
        Set<Node> cleanSetB = removeLatents(setB);
        Set<Node> cleanGivenSet = removeLatents(givenSet);

        try {
            for (Node a : cleanSetA) {
                for (Node b : cleanSetB) {
                    if (!test.checkIndependence(a, b, cleanGivenSet).isIndependent()) {
                        return false; // dependent pair found
                    }
                }
            }
            return true; // all pairs independent
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Refines the latent edges in the given graph by iterating through all edges and removing edges between latent
     * nodes if they meet certain conditions for removal.
     *
     * @param graph The graph in which latent edges are to be refined. The graph contains nodes and edges representing
     *              relationships between variables, including latent variables.
     */
    private void refineLatentEdges(Graph graph) {
        List<Edge> latentEdges = new ArrayList<>(graph.getEdges());

        for (Edge edge : latentEdges) {
            Node latentA = edge.getNode1();
            Node latentB = edge.getNode2();

            if (latentA.getNodeType() == NodeType.LATENT && latentB.getNodeType() == NodeType.LATENT) {
                if (canRemoveLatentEdge(latentA, latentB, graph)) {
                    graph.removeEdge(edge);
                }
            }
        }
    }

    /**
     * Performs the final refinement step in the Directed Maximal PC (DmPc) algorithm, refining the graph structure by
     * updating connections based on outputs from the PC algorithm. This method adjusts edges between latent and output
     * nodes and modifies edges among output nodes to align with the output of the PC algorithm.
     *
     * @param graph The {@link Graph} being refined. This graph contains nodes and edges representing the relationships
     *              between variables, including both observed and latent variables.
     */
    private void finalRefinement(Graph graph) {
        try {
            Pc pc = new Pc(test);
            pc.setDepth(-1);
            pc.setKnowledge(knowledge);
            Graph fullPattern = pc.search();

            for (Node output : outputs) {
                if (fullPattern.getAdjacentNodes(output).stream().noneMatch(inputs::contains)) {
                    // Remove latent-output edge and instead use PC output edges among outputs.
                    for (Node parent : new ArrayList<>(graph.getParents(output))) {
                        if (parent.getNodeType() == NodeType.LATENT) {
                            graph.removeEdge(parent, output);
                        }
                    }

                    // Add edges among outputs according to fullPattern.
                    for (Node neighbor : fullPattern.getAdjacentNodes(output)) {
                        if (outputs.contains(neighbor)) {
                            if (!graph.isAdjacentTo(output, neighbor)) {
                                graph.addUndirectedEdge(output, neighbor);
                            }
                        }
                    }
                }
            }

            for (Node node : graph.getNodes()) {
                if (node.getNodeType() == NodeType.LATENT) {
                    Set<Node> measuredParents = removeLatents(new HashSet<>(graph.getParents(node)));
                    Set<Node> measuredChildren = removeLatents(new HashSet<>(graph.getChildren(node)));

                    if (measuredParents.isEmpty() || measuredChildren.isEmpty()) {
                        graph.removeNode(node);
                    }
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the knowledge for the current instance. The provided knowledge is used in the context of the DmPc algorithm
     * to guide the structure learning process by incorporating prior information or constraints.
     *
     * @param knowledge An instance of the {@link Knowledge} class that encapsulates domain-specific rules, constraints,
     *                  or prior knowledge to be applied.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}