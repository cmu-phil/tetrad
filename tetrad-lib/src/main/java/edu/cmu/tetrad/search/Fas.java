package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.io.PrintStream;
import java.util.*;

/**
 * Implements the Fast Adjacency Search (FAS), which is the adjacency search of the PC algorithm (see). This is a useful
 * algorithm in many contexts, including as the first step of FCI (see).
 * <p>
 * The idea of FAS is that at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S,
 * where S is a subset of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency
 * search performs this procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1.
 * Here, d1 is either the maximum depth or else the first such depth at which no edges can be removed. The
 * interpretation of this adjacency search is different for different algorithms, depending on the assumptions of the
 * algorithm. A mapping from {x, y} to S({x, y}) is returned for edges x *-* y that have been removed.
 * <p>
 * FAS may optionally use a heuristic from Causation, Prediction, and Search, which (like PC-Stable) renders the output
 * invariant to the order of the input variables.
 * <p>
 * This algorithm was described in the earlier edition of this book:
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author peterspirtes
 * @author clarkglymour
 * @author josephramsey.
 * @version $Id: $Id
 * @see Pc
 * @see Fci
 * @see Knowledge
 */
public class Fas {
    /**
     * Represents the core independence test utilized in the FAS algorithm to determine conditional independence
     * relationships between variables. This test serves as the foundation for building and refining graphs during the
     * search process.
     */
    private final IndependenceTest test;
    /**
     * A separation set map used to store the results of conditional independence tests performed during the graph
     * search process. It holds information about the sets of variables that separate pairs of nodes in the graph.
     */
    private final SepsetMap sepset = new SepsetMap();
    /**
     * A Knowledge object used to define constraints, such as forbidden or required edges, and other background
     * information necessary for the conditional independence search process.
     * <p>
     * This variable is integral to guiding various search methods and algorithms in the parent class, ensuring that the
     * results adhere to predetermined conditions or limitations imposed by the user or the context of the analysis.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Specifies the maximum depth to be considered during the conditional independence search process. The depth
     * determines the maximum number of conditioning variables used in independence tests, where a value of -1 indicates
     * that no limit is imposed.
     */
    private int depth = -1;
    /**
     * A flag indicating whether the search process will use a stable strategy. When set to {@code true}, the search
     * follows a stable approach. This flag may influence the behavior or results of certain algorithms in the
     * conditional independence graph search process.
     */
    private boolean stable = true;
    /**
     * A flag indicating whether verbose output is enabled during the search process. If true, additional logging or
     * debugging information is printed to the configured output stream. If false, only essential information is
     * displayed.
     */
    private boolean verbose = false;
    /**
     * The output stream used for logging or displaying messages during the search process. By default, it is set to
     * {@code System.out}. This stream can be replaced or customized by the user to redirect output messages to
     * alternative destinations.
     */
    private PrintStream out = System.out;

    /**
     * Constructs a new instance of the Fas algorithm using the specified independence test.
     *
     * @param test The independence test to be used by the Fas algorithm for conditional independence tests
     *             during the search process.
     */
    public Fas(IndependenceTest test) {
        this.test = test;
    }

    /**
     * Returns a list of nodes that are possible parents of a given node, based on the adjacency list of the given node,
     * the knowledge object, and another node.
     *
     * @param x         The node for which to find possible parents.
     * @param adjx      The adjacency list of the node x.
     * @param knowledge The knowledge object that provides information about conditional independencies.
     * @param y         Another node in the graph.
     * @return A list of nodes that are possible parents of the node x.
     * @throws InterruptedException if any
     */
    private static List<Node> possibleParents(Node x, List<Node> adjx, Knowledge knowledge, Node y) throws InterruptedException {
        List<Node> possibleParents = new LinkedList<>();
        String _x = x.getName();

        for (Node z : adjx) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (z == null) continue;
            if (z == x) continue;
            if (z == y) continue;
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    /**
     * Determines if a given node is a possible parent of another node based on the provided knowledge.
     *
     * @param z         The first node.
     * @param x         The second node.
     * @param knowledge The knowledge object that provides information about conditional independencies.
     * @return True if the node z is a possible parent of node x, false otherwise.
     */
    private static boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    /**
     * Performs a conditional independence graph search using the default set of variables from the initialized
     * independence test. The method delegates the search process to another method that takes a list of variables as
     * input.
     *
     * @return A graph where edges are updated based on the results of conditional independence tests and other provided
     * constraints.
     * @throws InterruptedException If the search process is interrupted.
     */
    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    /**
     * Searches for conditional independence relationships in a graph constructed from the given list of nodes. Edges
     * are removed based on the provided set of constraints and the maximum depth for the search.
     *
     * @param nodes The list of nodes to construct the graph and perform the search on.
     * @return A graph with edges updated based on the search process and conditional independence tests.
     * @throws InterruptedException If variables provided are not a subset of the ones in the test or if the search
     *                              process is interrupted.
     */
    public Graph search(List<Node> nodes) throws InterruptedException {
        if (!new HashSet<>(test.getVariables()).containsAll(nodes)) {
            throw new InterruptedException("Variables should be a subset of the ones in the test.");
        }

        Graph graph = new EdgeListGraph(nodes);
        graph = GraphUtils.completeGraph(graph);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (knowledge.isForbidden(x.getName(), y.getName()) && knowledge.isForbidden(y.getName(), x.getName())) {
                    graph.removeEdge(x, y);
                }
            }
        }

        int depth_ = depth >= 0 ? depth : Math.max(depth, test.getVariables().size() - 1);

        for (int d = 0; d <= depth_; d++) {
            if (verbose) {
                System.out.println("Depth: " + d);
            }

            Graph graph_ = graph;

            if (this.stable) {
                graph = new EdgeListGraph(graph_);

                if (searchAtDepth(graph, graph_, d, this.stable)) {
                    graph = graph_;
                    break;
                }

                graph = graph_;
            } else {
                if (searchAtDepth(graph, graph_, d, stable)) {
                    break;
                }
            }
        }

        return graph;
    }

    /**
     * Searches for conditional independence relationships in a graph up to a given depth d. If the graph's nodes meet
     * the conditions specified, the edges may be removed based on the provided knowledge.
     *
     * @param graph  The graph on which the search is performed.
     * @param graph_ A copy of the graph where changes are applied during the search process.
     * @param d      The maximum depth to consider for conditional independence tests.
     * @param stable If true, performs the search using a parallel strategy; otherwise, uses a sequential strategy.
     * @return True if the maximum free degree of the resulting graph is less than or equal to d, false otherwise.
     * @throws InterruptedException if the search is interrupted during independence testing or parent determination.
     */
    private boolean searchAtDepth(Graph graph, Graph graph_, int d, boolean stable) throws InterruptedException {
        List<Node> nodes = graph.getNodes();

        if (stable) {
            nodes.parallelStream().forEach(x -> {
                for (Node y : nodes) {
                    if (x == y) continue;

                    if (knowledge.isForbidden(x.getName(), y.getName()) && knowledge.isForbidden(y.getName(), x.getName())) {
                        synchronized (graph_) {
                            graph_.removeEdge(x, y);
                        }
                    }

                    List<Node> adjX = graph.getAdjacentNodes(x);
                    adjX.remove(y);

                    List<Node> ppx = null;
                    try {
                        ppx = possibleParents(x, adjX, knowledge, y);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    if (ppx.size() >= d) {
                        ChoiceGenerator generator = new ChoiceGenerator(ppx.size(), d);
                        int[] choice;

                        while ((choice = generator.next()) != null) {
                            Set<Node> S = GraphUtils.asSet(choice, ppx);

                            IndependenceResult result = null;
                            try {
                                result = test.checkIndependence(x, y, S);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            if (result.isIndependent() && knowledge.noEdgeRequired(x.getName(), y.getName())) {
                                synchronized (graph_) {
                                    graph_.removeEdge(x, y);
                                    sepset.set(x, y, S);
                                }
                                break;
                            }
                        }
                    }
                }
            });
        } else {
            System.out.println("Depth: " + d);

            for (Node x : nodes) {
                for (Node y : nodes) {
                    if (x == y) continue;

                    if (knowledge.isForbidden(x.getName(), y.getName()) && knowledge.isForbidden(y.getName(), x.getName())) {
                        graph_.removeEdge(x, y);
                    }

                    List<Node> adjX = graph.getAdjacentNodes(x);
                    adjX.remove(y);

                    List<Node> ppx = possibleParents(x, adjX, knowledge, y);

                    if (adjX.size() >= d) {
                        ChoiceGenerator generator = new ChoiceGenerator(ppx.size(), d);
                        int[] choice;

                        while ((choice = generator.next()) != null) {
                            Set<Node> S = GraphUtils.asSet(choice, ppx);

                            if (graph.isAdjacentTo(x, y)) {
                                IndependenceResult result = test.checkIndependence(x, y, S);
                                if (result.isIndependent() && knowledge.noEdgeRequired(x.getName(), y.getName())) {
                                    graph_.removeEdge(x, y);
                                    sepset.set(x, y, S);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        return freeDegree(graph_) <= d;
    }

    /**
     * Calculates the maximum free degree among the nodes in the given adjacency map.
     *
     * @param graph The graph.
     * @return The maximum free degree among the nodes.
     */
    private int freeDegree(Graph graph) {
        int max = 0;

        for (Node n : graph.getNodes()) {
            List<Node> opposites = graph.getAdjacentNodes(n);

            for (Node y : opposites) {
                Set<Node> adjx = new LinkedHashSet<>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

    /**
     * Sets the depth for the search process.
     *
     * @param depth The maximum depth to be considered during the search.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets the knowledge object used in the conditional independence search process.
     *
     * @param knowledge The knowledge object that provides information about constraints or background knowledge, such
     *                  as forbidden edges, required edges, or other conditional independencies.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the separation set map that is used during the search process to store information about conditional
     * independence tests.
     *
     * @return The separation set map represented by an instance of {@code SepsetMap}, containing conditional
     * independence information.
     */
    public SepsetMap getSepsets() {
        return this.sepset;
    }

    /**
     * Sets the verbosity level for the logging or debugging output of the search process.
     *
     * @param verbose If true, enables verbose output; if false, disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the stability flag for the search process, which may determine the search strategy or algorithm's behavior.
     *
     * @param stable If true, enables a stable search strategy; otherwise, an unstable or alternative strategy is used.
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Retrieves a list of nodes associated with the initialized independence test.
     *
     * @return A list of nodes derived from the variables in the associated independence test.
     */
    public List<Node> getNodes() {
        return test.getVariables();
    }

    /**
     * Retrieves the current output stream used for logging or display purposes.
     *
     * @return The output stream instance associated with the logging or output operations.
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Sets the output stream to be used for logging or other display purposes.
     *
     * @param out The output stream to which messages or data will be printed.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }
}