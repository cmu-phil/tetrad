package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TaskManager;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;


/**
 * GesSearch is an implementation of the GES algorithm, as specified in Chickering (2002) "Optimal structure
 * identification with greedy search" Journal of Machine Learning Research. It works for both BayesNets and SEMs.
 * <p>
 * Some code optimization could be done for the scoring part of the graph for discrete models (method scoreGraphChange).
 * Some of Andrew Moore's approaches to caching sufficient statistics, for instance.
 * <p>
 * To speed things up, it has been assumed that variables X and Y with zero correlations do not correspond to edges in
 * the graph. This is a restricted form of the heuristicSpeedup assumption, something GES does not assume. This is the
 * graph. This is a restricted form of the heuristicSpeedup assumption, something GES does not assume. This
 * heuristicSpeedup assumption needs to be explicitly turned on using setHeuristicSpeedup(true).
 * <p>
 * A number of other optimizations were added 5/2015. See code for details.
 *
 * @author Ricardo Silva, Summer 2003
 * @author Joseph Ramsey, Revisions 5/2015
 */
public final class ISFges implements IGraphSearch {

    final int[] count = new int[1];
    // Arrows with the same totalScore are stored in this list to distinguish their order in sortedArrows.
    // The ordering doesn't matter; it just has to be transitive.
    int arrowIndex = 0;
    /**
     * Specification of forbidden and required edges.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * List of variables in the data set, in order.
     */
    private List<Node> variables;
    /**
     * The true graph, if known. If this is provided, asterisks will be printed out next to false positive added edges
     * (that is, edges added that aren't adjacencies in the true graph).
     */
    private Graph trueGraph;
    /**
     * An initial graph to start from.
     */
    private Graph initialGraph;
    /**
     * A population graph to start from.
     */
    private Graph populationGraph;
    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;
    /**
     * The totalScore for discrete searches.
     */
    private ISScore score;
    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose = false;
    // Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
    private SortedSet<Arrow> sortedArrows = null;
    // Arrows added to sortedArrows for each <i, j>.
    private Map<OrderedPair<Node>, Set<Arrow>> lookupArrows = null;
    // A utility map to help with orientation.
    private Map<Node, Set<Node>> neighbors = null;
    // Map from variables to their column indices in the data set.
    private ConcurrentMap<Node, Integer> hashIndices;
    // The static ForkJoinPool instance.
    private ForkJoinPool pool = new ForkJoinPool();
    // A graph where X--Y means that X and Y have non-zero total effect on one another.
    private Graph effectEdgesGraph;
    // Where printed output is sent.
    private PrintStream out = System.out;
    // An initial adjacencies graph.
    private Graph adjacencies = null;
    // The graph being constructed.
    private Graph graph;
    // Internal.
    private Mode mode = Mode.heuristicSpeedup;
    /**
     * True if one-edge faithfulness is assumed. Speedse the algorithm up.
     */
    private boolean faithfulnessAssumed = true;
    // Bounds the degree of the graph.
    private int maxDegree = -1;

    //===========================CONSTRUCTORS=============================//
    // True if the first step of adding an edge to an empty graph should be scored in both directions
    // for each edge with the maximum score chosen.
    private boolean symmetricFirstStep = true;//false;

    //==========================PUBLIC METHODS==========================//

    /**
     * Construct a Score and pass it in here. The totalScore should return a positive value in case of conditional
     * dependence and a negative values in case of conditional independence. See Chickering (2002), locally consistent
     * scoring criterion.
     *
     * @param score the ISScore object to be used for scoring
     */
    public ISFges(ISScore score) {
        if (score == null) throw new NullPointerException();
        setScore(score);
        this.graph = new EdgeListGraph(getVariables());
    }

    // Used to find semidirected paths for cycle checking.
    private static Node traverseSemiDirected(Node node, Edge edge) {

        if (node == edge.getNode1()) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                return edge.getNode2();
            }
        } else if (node == edge.getNode2()) {
            if (edge.getEndpoint2() == Endpoint.TAIL) {
                return edge.getNode1();
            }
        }
        return null;
    }

    // Only need the unconditioal d-connection here.
    private static Set<Node> getUnconditionallyDconnectedVars(Node x, Graph graph) {
        Set<Node> Y = new HashSet<>();

        class EdgeNode {

            private final Edge edge;
            private final Node node;

            private EdgeNode(Edge edge, Node node) {
                this.edge = edge;
                this.node = node;
            }

            public int hashCode() {
                return edge.hashCode() + node.hashCode();
            }

            public boolean equals(Object o) {
                if (!(o instanceof EdgeNode _o)) {
                    throw new IllegalArgumentException();
                }
                return _o.edge == edge && _o.node == node;
            }
        }

        Queue<EdgeNode> Q = new ArrayDeque<>();
        Set<EdgeNode> V = new HashSet<>();

        for (Edge edge : graph.getEdges(x)) {
            EdgeNode edgeNode = new EdgeNode(edge, x);
            Q.offer(edgeNode);
            V.add(edgeNode);
            Y.add(edge.getDistalNode(x));
        }

        while (!Q.isEmpty()) {
            EdgeNode t = Q.poll();

            Edge edge1 = t.edge;
            Node a = t.node;
            Node b = edge1.getDistalNode(a);

            for (Edge edge2 : graph.getEdges(b)) {
                Node c = edge2.getDistalNode(b);
                if (c == a) {
                    continue;
                }

                if (reachable(edge1, edge2, a)) {
                    EdgeNode u = new EdgeNode(edge2, b);

                    if (!V.contains(u)) {
                        V.add(u);
                        Q.offer(u);
                        Y.add(c);
                    }
                }
            }
        }

        return Y;
    }

    private static boolean reachable(Edge e1, Edge e2, Node a) {
        Node b = e1.getDistalNode(a);

        boolean collider = e1.getProximalEndpoint(b) == Endpoint.ARROW
                           && e2.getProximalEndpoint(b) == Endpoint.ARROW;

        return !collider;
    }

    /**
     * Returns the faithfulness assumption status for the current instance.
     *
     * @return true if it is assumed that all path pairs with one length 1 path do not cancel.
     */
    public boolean isFaithfulnessAssumed() {
        return faithfulnessAssumed;
    }

    /**
     * Sets the faithfulness assumption status for the current instance.
     *
     * @param faithfulnessAssumed a boolean indicating whether faithfulness is assumed
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    /**
     * Greedy equivalence search: Start from the empty graph, add edges till the model is significant. Then start deleting
     * edges till a minimum is achieved.
     *
     * @return the resulting Pattern.
     */
    public Graph search() {

        lookupArrows = new ConcurrentHashMap<>();
        final List<Node> nodes = new ArrayList<>(variables);
        graph = new EdgeListGraph(nodes);

        if (adjacencies != null) {
            adjacencies = GraphUtils.replaceNodes(adjacencies, nodes);
        }

        if (initialGraph != null) {
            graph = new EdgeListGraph(initialGraph);
            graph = GraphUtils.replaceNodes(graph, nodes);
        }

        addRequiredEdges(graph);

        if (faithfulnessAssumed) {
            initializeForwardEdgesFromEmptyGraph(getVariables());

            // Do forward search.
            this.mode = Mode.heuristicSpeedup;
            fes();
            bes();

            this.mode = Mode.coverNoncolliders;
            initializeTwoStepEdges(getVariables());
            fes();
            bes();
        } else {
            initializeForwardEdgesFromEmptyGraph(getVariables());

            // Do forward search.
            this.mode = Mode.heuristicSpeedup;
            fes();
            bes();

            this.mode = Mode.allowUnfaithfulness;
            initializeForwardEdgesFromExistingGraph(getVariables());
            fes();
            bes();
        }

        long start = System.currentTimeMillis();
        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - start;
        this.logger.log("\nReturning this graph: " + graph);

        this.logger.log("Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.flush();

        return graph;
    }

    /**
     * Retrieves the Knowledge instance associated with this object.
     *
     * @return the Knowledge instance associated with this object
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    /**
     * Returns the elapsed time.
     *
     * @return the elapsed time in milliseconds.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Sets the true graph.
     *
     * @param trueGraph the Graph object to be set as the true graph
     */
    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    /**
     * Calculates and returns the score of the provided Directed Acyclic Graph (DAG).
     *
     * @param dag the DAG for which the score needs to be calculated
     * @return the score of the given DAG
     */
    public double getScore(Graph dag) {
        return scoreDag(dag);
    }

    /**
     * Sets the initial graph, ensuring the graph's nodes match the expected variables.
     *
     * @param initialGraph the initial graph to set. If null, does nothing. The method
     *                     replaces the nodes of the graph with the expected variables
     *                     and checks if they match.
     * @throws IllegalArgumentException if the nodes of the initialGraph do not
     *                                  match the expected variables.
     */
    public void setInitialGraph(Graph initialGraph) {
        if (initialGraph != null) {
            initialGraph = GraphUtils.replaceNodes(initialGraph, variables);

            if (verbose) {
                out.println("Initial graph variables: " + initialGraph.getNodes());
                out.println("Data set variables: " + variables);
            }

            if (!new HashSet<>(initialGraph.getNodes()).equals(new HashSet<>(variables))) {
                throw new IllegalArgumentException("Variables aren't the same.");
            }
        }

        this.initialGraph = initialGraph;
    }

    /**
     * Sets the population graph for the current instance. The provided graph will replace the
     * current population graph if it is not null. The method ensures that the nodes of the
     * provided graph match the expected variables.
     *
     * @param populationGraph the graph to set as the population graph
     * @throws IllegalArgumentException if the variables of the provided graph do not match the
     *                                  expected variables
     */
    public void setPopulationGraph(Graph populationGraph) {
        if (populationGraph != null) {
            populationGraph = GraphUtils.replaceNodes(populationGraph, variables);

//            if (verbose) {
//                out.println("Population graph variables: " + populationGraph.getNodes());
//                out.println("Data set variables: " + variables);
//            }

            if (!new HashSet<>(populationGraph.getNodes()).equals(new HashSet<>(variables))) {
                throw new IllegalArgumentException("Variables aren't the same.");
            }
        }

        this.populationGraph = populationGraph;
    }

    /**
     * Sets the verbosity level for the application.
     *
     * @param verbose a boolean indicating whether to enable verbose mode (true) or not (false)
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Retrieves the current PrintStream used for standard output.
     *
     * @return the PrintStream object representing the standard output stream.
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Sets the output stream for this instance.
     *
     * @param out the PrintStream to be used for output
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Retrieves the preset adjacencies graph.
     *
     * @return the Graph of adjacencies.
     */
    public Graph getAdjacencies() {
        return adjacencies;
    }

    /**
     * Sets the preset adjacency information for the graph.
     *
     * @param adjacencies the graph representing adjacency relationships to be set
     */
    public void setAdjacencies(Graph adjacencies) {
        this.adjacencies = adjacencies;
    }

    /**
     * Sets the level of parallelism for the ForkJoinPool by specifying the number
     * of processors to be used.
     *
     * @param numProcessors the number of processors to be used for parallel computations
     */
    public void setParallelism(int numProcessors) {
        this.pool = new ForkJoinPool(numProcessors);
    }

    //===========================PRIVATE METHODS========================//

    /**
     * The maximum of parents any nodes can have in an output pattern.
     *
     * @return -1 for unlimited.
     */
    public int getMaxDegree() {
        return maxDegree;
    }

    /**
     * The maximum of parents any nodes can have in an output pattern.
     *
     * @param maxDegree -1 for unlimited.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) throw new IllegalArgumentException();
        this.maxDegree = maxDegree;
    }

    /**
     * Checks if the first step of the algorithm is symmetric.
     *
     * @return true if the first step of the algorithm is symmetric, false otherwise.
     */
    public boolean isSymmetricFirstStep() {
        return symmetricFirstStep;
    }

    /**
     * Sets whether the first step of the algorithm should be symmetric.
     *
     * @param symmetricFirstStep true to make the first step symmetric, false otherwise.
     */
    public void setSymmetricFirstStep(boolean symmetricFirstStep) {
        this.symmetricFirstStep = symmetricFirstStep;
    }

    /**
     * Sets the total score, initializes the list of measured variables, builds indexing,
     * and sets the maximum degree.
     *
     * @param totalScore The ISScore object containing the total score and variables.
     */
    private void setScore(ISScore totalScore) {
        this.score = totalScore;

        this.variables = new ArrayList<>();

        for (Node node : totalScore.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }

        buildIndexing(totalScore.getVariables());

        this.maxDegree = score.getMaxDegree();
    }

    /**
     * Calculates the minimum chunk size for parallel processing.
     *
     * @param n the total number of tasks to be distributed
     * @return the minimum chunk size which is either the result of dividing
     *         the total number of tasks by the parallelism of the pool or 100,
     *         whichever is larger
     */
    public int getMinChunk(int n) {
        return Math.max(n / pool.getParallelism(), 100);
    }

    /**
     * Initializes the forward edges for the graph starting from an empty state.
     * This method constructs an edge list graph based on the provided list of nodes
     * and distributes the initialization tasks over multiple parallel tasks for efficiency.
     *
     * @param nodes The list of nodes from which the graph will be initialized.
     *              Each node will be part of the resulting edge list graph.
     */
    private void initializeForwardEdgesFromEmptyGraph(final List<Node> nodes) {
//        if (verbose) {
//            System.out.println("heuristicSpeedup = true");
//        }

        sortedArrows = new ConcurrentSkipListSet<>();
        lookupArrows = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();
        final Set<Node> emptySet = new HashSet<>();

        long start = System.currentTimeMillis();
        this.effectEdgesGraph = new EdgeListGraph(nodes);

        class InitializeFromEmptyGraphTask extends RecursiveTask<Boolean> {

            public InitializeFromEmptyGraphTask() {
            }

            @Override
            protected Boolean compute() {
                Queue<NodeTaskEmptyGraph> tasks = new ArrayDeque<>();

                int numNodesPerTask = Math.max(100, nodes.size() / pool.getParallelism());

                for (int i = 0; i < nodes.size(); i += numNodesPerTask) {
                    NodeTaskEmptyGraph task = new NodeTaskEmptyGraph(i, Math.min(nodes.size(), i + numNodesPerTask),
                            nodes, emptySet);
                    tasks.add(task);
                    task.fork();

                    for (NodeTaskEmptyGraph _task : new ArrayList<>(tasks)) {
                        if (_task.isDone()) {
                            _task.join();
                            tasks.remove(_task);
                        }
                    }

                    while (tasks.size() > pool.getParallelism()) {
                        NodeTaskEmptyGraph _task = tasks.poll();
                        assert _task != null;
                        _task.join();
                    }
                }

                for (NodeTaskEmptyGraph task : tasks) {
                    task.join();
                }

                return true;
            }
        }

        pool.invoke(new InitializeFromEmptyGraphTask());

        long stop = System.currentTimeMillis();

        if (verbose) {
            out.println("Elapsed initializeForwardEdgesFromEmptyGraph = " + (stop - start) + " ms");
        }
    }

    private void initializeTwoStepEdges(final List<Node> nodes) {
//        if (verbose) {
//            System.out.println("heuristicSpeedup = false");
//        }

        count[0] = 0;

        sortedArrows = new ConcurrentSkipListSet<>();
        lookupArrows = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();

        if (this.effectEdgesGraph == null) {
            this.effectEdgesGraph = new EdgeListGraph(nodes);
        }

        if (initialGraph != null) {
            for (Edge edge : initialGraph.getEdges()) {
                if (!effectEdgesGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    effectEdgesGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
                }
            }
        }

        final Set<Node> emptySet = new HashSet<>(0);

        class InitializeFromExistingGraphTask extends RecursiveTask<Boolean> {
            private final int chunk;
            private final int from;
            private final int to;

            public InitializeFromExistingGraphTask(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (TaskManager.getInstance().isCanceled()) return false;

                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        if ((i + 1) % 1000 == 0) {
                            count[0] += 1000;
                            out.println("Initializing effect edges: " + (count[0]));
                        }

                        // We want to recapture the variables that would have been effect edges if paths hadn't
                        // exactly canceled. These are variables X which are d-connected to the target Y where
                        // X--Y was not identified as an effect edge earlier.
                        Node y = nodes.get(i);
                        Set<Node> D = new HashSet<>(getUnconditionallyDconnectedVars(y, graph));
                        D.remove(y);
                        effectEdgesGraph.getAdjacentNodes(y).forEach(D::remove);

                        for (Node x : D) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            if (existsKnowledge()) {
                                if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                                    continue;
                                }

                                if (invalidSetByKnowledge(y, emptySet)) {
                                    continue;
                                }
                            }

                            if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            calculateArrowsForward(x, y);
                        }
                    }
//                        Set<Node> g = new HashSet<>();
//
//                        for (Node n : graph.getAdjacentNodes(y)) {
//                            for (Node m : graph.getAdjacentNodes(n)) {
//                                if (m == y) continue;
//
//                                if (graph.isAdjacentTo(y, m)) {
//                                    continue;
//                                }
//
//                                if (graph.isDefCollider(m, n, y)) {
//                                    continue;
//                                }
//
//                                g.add(m);
//                            }
//                        }
//
//                        for (Node x : g) {
//                            if (x == y) throw new IllegalArgumentException();
//
//                            if (existsKnowledge()) {
//                                if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
//                                    continue;
//                                }
//
//                                if (invalidSetByKnowledge(y, emptySet)) {
//                                    continue;
//                                }
//                            }
//
//                            if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
//                                continue;
//                            }
//
//                            if (removedEdges.contains(Edges.undirectedEdge(x, y))) {
//                                continue;
//                            }
//
//                            calculateArrowsForward(x, y);
//                        }
//                    }
//
                    return true;
                } else {
                    int mid = (to + from) / 2;

                    InitializeFromExistingGraphTask left = new InitializeFromExistingGraphTask(chunk, from, mid);
                    InitializeFromExistingGraphTask right = new InitializeFromExistingGraphTask(chunk, mid, to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        pool.invoke(new InitializeFromExistingGraphTask(getMinChunk(nodes.size()), 0, nodes.size()));
    }

    private void initializeForwardEdgesFromExistingGraph(final List<Node> nodes) {
//        if (verbose) {
//            System.out.println("heuristicSpeedup = false");
//        }

        count[0] = 0;

        sortedArrows = new ConcurrentSkipListSet<>();
        lookupArrows = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();

        if (this.effectEdgesGraph == null) {
            this.effectEdgesGraph = new EdgeListGraph(nodes);
        }

        if (initialGraph != null) {
            for (Edge edge : initialGraph.getEdges()) {
                if (!effectEdgesGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    effectEdgesGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
                }
            }
        }

        final Set<Node> emptySet = new HashSet<>(0);

        class InitializeFromExistingGraphTask extends RecursiveTask<Boolean> {
            private final int chunk;
            private final int from;
            private final int to;

            public InitializeFromExistingGraphTask(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (TaskManager.getInstance().isCanceled()) return false;

                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        if ((i + 1) % 1000 == 0) {
                            count[0] += 1000;
                            out.println("Initializing effect edges: " + (count[0]));
                        }
                        // We want to recapture the variables that would have been effect edges if paths hadn't
                        // exactly canceled. These are variables X which are d-connected to the target Y where
                        // X--Y was not identified as an effect edge earlier.
                        Node y = nodes.get(i);
                        Set<Node> D = new HashSet<>(getUnconditionallyDconnectedVars(y, graph));
                        D.remove(y);
                        effectEdgesGraph.getAdjacentNodes(y).forEach(D::remove);

                        for (Node x : D) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            if (existsKnowledge()) {
                                if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                                    continue;
                                }

                                if (invalidSetByKnowledge(y, emptySet)) {
                                    continue;
                                }
                            }

                            if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            calculateArrowsForward(x, y);
                        }
                    }

                    return true;
//                        Node y = nodes.get(i);
//                        Set<Node> D = new HashSet<>();
//                        List<Node> cond = new ArrayList<>();
//                        D.addAll(GraphUtils.getDconnectedVars(y, cond, graph));
//                        D.remove(y);
//                        D.removeAll(effectEdgesGraph.getAdjacentNodes(y));
//
//                        for (Node x : D) {
//                            if (existsKnowledge()) {
//                                if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
//                                    continue;
//                                }
//
//                                if (invalidSetByKnowledge(y, emptySet)) {
//                                    continue;
//                                }
//                            }
//
//                            if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
//                                continue;
//                            }
//
//                            calculateArrowsForward(x, y);
//                        }
//                    }
//
//                    return true;
                } else {
                    int mid = (to + from) / 2;

                    InitializeFromExistingGraphTask left = new InitializeFromExistingGraphTask(chunk, from, mid);
                    InitializeFromExistingGraphTask right = new InitializeFromExistingGraphTask(chunk, mid, to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        pool.invoke(new InitializeFromExistingGraphTask(getMinChunk(nodes.size()), 0, nodes.size()));
    }

    private void fes() {

        if (verbose) {
            TetradLogger.getInstance().log("** FORWARD EQUIVALENCE SEARCH");
            out.println("** FORWARD EQUIVALENCE SEARCH");
        }

        int maxDegree = this.maxDegree == -1 ? 1000 : this.maxDegree;

        while (!sortedArrows.isEmpty()) {
            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (graph.isAdjacentTo(x, y)) {
                continue;
            }

            if (graph.getDegree(x) > maxDegree - 1) {
                continue;
            }

            if (graph.getDegree(y) > maxDegree - 1) {
                continue;
            }

            if (!arrow.getNaYX().equals(getNaYX(x, y))) {
                continue;
            }

            if (!new HashSet<>(getTNeighbors(x, y)).equals(arrow.getTNeighbors())) {
                continue;
            }

            if (!validInsert(x, y, arrow.getHOrT(), getNaYX(x, y))) {
                continue;
            }

            boolean inserted = insert(x, y, arrow.getHOrT(), arrow.getBump());

            if (!inserted) {
                continue;
            }

            reapplyOrientation();
            Set<Node> toProcess = new HashSet<>();

            for (Node node : variables) {
                final Set<Node> neighbors1 = getNeighbors(node);
                final Set<Node> storedNeighbors = this.neighbors.get(node);

                if (!(neighbors1.equals(storedNeighbors))) {
                    toProcess.add(node);
                }
            }

            toProcess.add(x);
            toProcess.add(y);

            reevaluateForward(toProcess);
        }
    }

    private void bes() {
        if (verbose) {
            TetradLogger.getInstance().log("** BACKWARD EQUIVALENCE SEARCH");
            out.println("** BACKWARD EQUIVALENCE SEARCH");
        }

        sortedArrows = new ConcurrentSkipListSet<>();
        lookupArrows = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();

        initializeArrowsBackward();

        while (!sortedArrows.isEmpty()) {
            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (!graph.isAdjacentTo(x, y)) {
                continue;
            }

            Edge edge = graph.getEdge(x, y);

            if (edge.pointsTowards(x)) {
                continue;
            }

            if (!getNaYX(x, y).equals(arrow.getNaYX())) {
                continue;
            }

            if (!validDelete(x, y, arrow.getHOrT(), arrow.getNaYX())) {
                continue;
            }

            boolean deleted = delete(x, y, arrow.getHOrT(), arrow.getBump(), arrow.getNaYX());

            if (!deleted) {
                continue;
            }

            reapplyOrientation();

            Set<Node> toProcess = new HashSet<>();

            for (Node node : getVariables()) {
                final Set<Node> neighbors1 = getNeighbors(node);
                final Set<Node> storedNeighbors = this.neighbors.get(node);

                if (!(neighbors1.equals(storedNeighbors))) {
                    toProcess.add(node);
                }
            }

            toProcess.add(x);
            toProcess.add(y);
            toProcess.addAll(getCommonAdjacents(x, y));

            reevaluateBackward(toProcess);
        }
    }

    private Set<Node> getCommonAdjacents(Node x, Node y) {
        Set<Node> commonChildren = new HashSet<>(graph.getAdjacentNodes(x));
        commonChildren.retainAll(graph.getAdjacentNodes(y));
        return commonChildren;
    }

    private void reapplyOrientation() {
        new MeekRules().orientImplied(graph);
    }

    // Returns true if knowledge is not empty.
    private boolean existsKnowledge() {
        return !knowledge.isEmpty();
    }

    // Initiaizes the sorted arrows lists for the backward search.
    private void initializeArrowsBackward() {
        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (existsKnowledge()) {
                if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                    continue;
                }
            }

            clearArrow(x, y);
            clearArrow(y, x);

            if (edge.pointsTowards(y)) {
                calculateArrowsBackward(x, y);
            } else if (edge.pointsTowards(x)) {
                calculateArrowsBackward(y, x);
            } else {
                calculateArrowsBackward(x, y);
                calculateArrowsBackward(y, x);
            }

            this.neighbors.put(x, getNeighbors(x));
            this.neighbors.put(y, getNeighbors(y));
        }
    }

    // Calcuates new arrows based on changes in the graph for the forward search.
    private void reevaluateForward(final Set<Node> nodes) {
        class AdjTask extends RecursiveTask<Boolean> {
            private final List<Node> nodes;
            private final int from;
            private final int to;
            private final int chunk;

            public AdjTask(int chunk, List<Node> nodes, int from, int to) {
                this.nodes = nodes;
                this.from = from;
                this.to = to;
                this.chunk = chunk;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int _w = from; _w < to; _w++) {
                        Node x = nodes.get(_w);

                        List<Node> adj;

                        if (mode == Mode.heuristicSpeedup) {
                            adj = effectEdgesGraph.getAdjacentNodes(x);
                        } else if (mode == Mode.coverNoncolliders) {
                            Set<Node> g = new HashSet<>();

                            for (Node n : graph.getAdjacentNodes(x)) {
                                for (Node m : graph.getAdjacentNodes(n)) {
                                    if (graph.isAdjacentTo(x, m)) {
                                        continue;
                                    }

                                    if (graph.isDefCollider(m, n, x)) {
                                        continue;
                                    }

                                    g.add(m);
                                }
                            }

                            adj = new ArrayList<>(g);
                        } else if (mode == Mode.allowUnfaithfulness) {
                            HashSet<Node> D = new HashSet<>(getUnconditionallyDconnectedVars(x, graph));
                            D.remove(x);
                            adj = new ArrayList<>(D);
                        } else {
                            throw new IllegalStateException();
                        }

                        for (Node w : adj) {
                            if (adjacencies != null && !(adjacencies.isAdjacentTo(w, x))) {
                                continue;
                            }

                            if (w == x) {
                                continue;
                            }

                            if (!graph.isAdjacentTo(w, x)) {
                                calculateArrowsForward(w, x);
                            }
                        }
                    }

                    return true;
                } else {
                    int mid = (to - from) / 2;

                    List<AdjTask> tasks = new ArrayList<>();

                    tasks.add(new AdjTask(chunk, nodes, from, from + mid));
                    tasks.add(new AdjTask(chunk, nodes, from + mid, to));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        final AdjTask task = new AdjTask(getMinChunk(nodes.size()), new ArrayList<>(nodes), 0, nodes.size());
        pool.invoke(task);
    }

    // Calculates the new arrows for an a->b edge.
    private void calculateArrowsForward(Node a, Node b) {
        if (mode == Mode.heuristicSpeedup && !effectEdgesGraph.isAdjacentTo(a, b)) {
            return;
        }
        if (adjacencies != null && !adjacencies.isAdjacentTo(a, b)) {
            return;
        }
        this.neighbors.put(b, getNeighbors(b));

        clearArrow(a, b);

        if (a == b) {
            throw new IllegalArgumentException();
        }

        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = getNaYX(a, b);
        if (!isClique(naYX)) {
            return;
        }

        List<Node> TNeighbors = getTNeighbors(a, b);

        Set<Set<Node>> previousCliques = new HashSet<>();
        previousCliques.add(new HashSet<>());
        Set<Set<Node>> newCliques = new HashSet<>();

        Set<Node> _T = null;
        double _bump = Double.NEGATIVE_INFINITY;

        FOR:
        for (int i = 0; i <= TNeighbors.size(); i++) {
            final SublistGenerator gen = new SublistGenerator(TNeighbors.size(), i);
            int[] choice;

            while ((choice = gen.next()) != null) {
                Set<Node> T = GraphUtils.asSet(choice, TNeighbors);

                Set<Node> union = new HashSet<>(naYX);
                union.addAll(T);

                boolean foundAPreviousClique = false;

                for (Set<Node> clique : previousCliques) {
                    if (union.containsAll(clique)) {
                        foundAPreviousClique = true;
                        break;
                    }
                }

                if (!foundAPreviousClique) {
                    break FOR;
                }

                if (!isClique(union)) {
                    continue;
                }
                newCliques.add(union);

                double bump = insertEval(a, b, T, naYX, hashIndices);

                if (bump > 0) {
                    _T = T;
                    _bump = bump;
//                    addArrow(a, b, TNeighbors, naYX, bump);
                }
            }

            if (_bump > Double.NEGATIVE_INFINITY) {
                addArrow(a, b, _T, new HashSet<>(TNeighbors), naYX, _bump);
            }

            previousCliques = newCliques;
            newCliques = new HashSet<>();
        }
    }

    private void addArrow(Node a, Node b, Set<Node> hOrT, Set<Node> TNeighbors, Set<Node> naYX, double bump) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, TNeighbors, naYX, arrowIndex++);
        sortedArrows.add(arrow);
        addLookupArrow(a, b, arrow);
    }

    // Reevaluates arrows after removing an edge from the graph.
    private void reevaluateBackward(Set<Node> toProcess) {
        class BackwardTask extends RecursiveTask<Boolean> {
            private final Node r;
            private final List<Node> adj;
            private final Map<Node, Integer> hashIndices;
            private final int chunk;
            private final int from;
            private final int to;

            public BackwardTask(Node r, List<Node> adj, int chunk, int from, int to,
                                Map<Node, Integer> hashIndices) {
                this.adj = adj;
                this.hashIndices = hashIndices;
                this.chunk = chunk;
                this.from = from;
                this.to = to;
                this.r = r;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int _w = from; _w < to; _w++) {
                        final Node w = adj.get(_w);
                        Edge e = graph.getEdge(w, r);

                        if (e != null) {
                            if (e.pointsTowards(r)) {
                                calculateArrowsBackward(w, r);
                            } else if (e.pointsTowards(w)) {
                                calculateArrowsBackward(r, w);
                            } else if (Edges.isUndirectedEdge(graph.getEdge(w, r))) {
                                calculateArrowsBackward(w, r);
                                calculateArrowsBackward(r, w);
                            }
                        }
                    }

                    return true;
                } else {
                    int mid = (to - from) / 2;

                    List<BackwardTask> tasks = new ArrayList<>();

                    tasks.add(new BackwardTask(r, adj, chunk, from, from + mid, hashIndices));
                    tasks.add(new BackwardTask(r, adj, chunk, from + mid, to, hashIndices));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        for (Node r : toProcess) {
            this.neighbors.put(r, getNeighbors(r));
            List<Node> adjacentNodes = graph.getAdjacentNodes(r);
            pool.invoke(new BackwardTask(r, adjacentNodes, getMinChunk(adjacentNodes.size()), 0,
                    adjacentNodes.size(), hashIndices));
        }
    }

    // Calculates the arrows for the removal in the backward direction.
    private void calculateArrowsBackward(Node a, Node b) {
        if (existsKnowledge()) {
            if (!getKnowledge().noEdgeRequired(a.getName(), b.getName())) {
                return;
            }
        }

        clearArrow(a, b);

        Set<Node> naYX = getNaYX(a, b);

        List<Node> _naYX = new ArrayList<>(naYX);

        final int _depth = _naYX.size();

        Set<Node> _h = null;
        double _bump = Double.NEGATIVE_INFINITY;

        final SublistGenerator gen = new SublistGenerator(_naYX.size(), _depth);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Set<Node> h = GraphUtils.asSet(choice, _naYX);

            if (existsKnowledge()) {
                if (invalidSetByKnowledge(b, h)) {
                    continue;
                }
            }

            double bump = deleteEval(a, b, h, hashIndices);

            if (bump >= 0.0) {
                _h = h;
                _bump = bump;
            }
        }

        if (_bump > Double.NEGATIVE_INFINITY) {
            addArrow(a, b, _h, null, naYX, _bump);
        }
    }

    // Get all adj that are connected to Y by an undirected edge and not adjacent to X.
    private List<Node> getTNeighbors(Node x, Node y) {
        Set<Edge> yEdges = graph.getEdges(y);
        List<Node> tNeighbors = new ArrayList<>();

        for (Edge edge : yEdges) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            Node z = edge.getDistalNode(y);

            if (graph.isAdjacentTo(z, x)) {
                continue;
            }

            tNeighbors.add(z);
        }

        return tNeighbors;
    }

    // Get all adj that are connected to Y.
    private Set<Node> getNeighbors(Node y) {
        Set<Edge> yEdges = graph.getEdges(y);
        Set<Node> neighbors = new HashSet<>();

        for (Edge edge : yEdges) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            Node z = edge.getDistalNode(y);

            neighbors.add(z);
        }

        return neighbors;
    }

    // Evaluate the Insert(X, Y, T) operator (Definition 12 from Chickering, 2002).
    private double insertEval(Node x, Node y, Set<Node> t, Set<Node> naYX,
                              Map<Node, Integer> hashIndices) {
        if (x == y) throw new IllegalArgumentException();
        Set<Node> set = new HashSet<>(naYX);
        set.addAll(t);
        set.addAll(graph.getParents(y));
        return scoreGraphChange(y, set, x, hashIndices);
    }

    // Evaluate the Delete(X, Y, T) operator (Definition 12 from Chickering, 2002).
    private double deleteEval(Node x, Node y, Set<Node> diff,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set = new HashSet<>(diff);
        set.addAll(graph.getParents(y));
        set.remove(x);
        return -scoreGraphChange(y, set, x, hashIndices);
    }

    // Do an actual insertion. (Definition 12 from Chickering, 2002).
    private boolean insert(Node x, Node y, Set<Node> T, double bump) {
        if (graph.isAdjacentTo(x, y)) {
            return false; // The initial graph may already have put this edge in the graph.
        }

        Edge trueEdge = null;

        if (trueGraph != null) {
            Node _x = trueGraph.getNode(x.getName());
            Node _y = trueGraph.getNode(y.getName());
            trueEdge = trueGraph.getEdge(_x, _y);
        }

        graph.addDirectedEdge(x, y);

        if (verbose) {
            String label = trueGraph != null && trueEdge != null ? "*" : "";
            TetradLogger.getInstance().log(graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y) +
                                           " " + T + " " + bump + " " + label);
        }

        int numEdges = graph.getNumEdges();

//        if (verbose) {
        if (numEdges % 1000 == 0) out.println("Num edges added: " + numEdges);
//        }

        if (verbose) {
            String label = trueGraph != null && trueEdge != null ? "*" : "";
            out.println(graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y) +
                        " " + T + " " + bump + " " + label
                        + " degree = " + GraphUtils.getDegree(graph)
                        + " indegree = " + GraphUtils.getIndegree(graph));
        }

        for (Node _t : T) {
            graph.removeEdge(_t, y);
            graph.addDirectedEdge(_t, y);

            if (verbose) {
                String message = "--- Directing " + graph.getEdge(_t, y);
                TetradLogger.getInstance().log(message);
                out.println(message);
            }
        }
//        out.println(this.graph);
        return true;
    }

    // Do an actual deletion (Definition 13 from Chickering, 2002).
    private boolean delete(Node x, Node y, Set<Node> H, double bump, Set<Node> naYX) {
        Edge trueEdge = null;

        if (trueGraph != null) {
            Node _x = trueGraph.getNode(x.getName());
            Node _y = trueGraph.getNode(y.getName());
            trueEdge = trueGraph.getEdge(_x, _y);
        }

        Edge oldxy = graph.getEdge(x, y);

        Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);

        graph.removeEdge(oldxy);

        int numEdges = graph.getNumEdges();
        if (numEdges % 1000 == 0) {
            out.println("Num edges (backwards) = " + numEdges);
        }

        if (verbose) {
            String label = trueGraph != null && trueEdge != null ? "*" : "";
            String message = (graph.getNumEdges()) + ". DELETE " + x + "-->" + y
                             + " H = " + H + " NaYX = " + naYX + " diff = " + diff + " (" + bump + ") " + label;
            TetradLogger.getInstance().log(message);
            out.println(message);
        }

        for (Node h : H) {
            if (graph.isParentOf(h, y) || graph.isParentOf(h, x)) {
                continue;
            }

            Edge oldyh = graph.getEdge(y, h);

            graph.removeEdge(oldyh);

            graph.addEdge(Edges.directedEdge(y, h));

            if (verbose) {
                TetradLogger.getInstance().log("--- Directing " + oldyh + " to "
                                               + graph.getEdge(y, h));
                out.println("--- Directing " + oldyh + " to " + graph.getEdge(y, h));
            }

            Edge oldxh = graph.getEdge(x, h);

            if (Edges.isUndirectedEdge(oldxh)) {
                graph.removeEdge(oldxh);

                graph.addEdge(Edges.directedEdge(x, h));

                if (verbose) {
                    TetradLogger.getInstance().log("--- Directing " + oldxh + " to "
                                                   + graph.getEdge(x, h));
                    out.println("--- Directing " + oldxh + " to " + graph.getEdge(x, h));
                }
            }
        }

        return true;
    }

    // Test if the candidate insertion is a valid operation
    // (Theorem 15 from Chickering, 2002).
    private boolean validInsert(Node x, Node y, Set<Node> T, Set<Node> naYX) {

        boolean violatesKnowledge = false;

        if (existsKnowledge()) {
            if (knowledge.isForbidden(x.getName(), y.getName())) {
                violatesKnowledge = true;
            }

            for (Node t : T) {
                if (knowledge.isForbidden(t.getName(), y.getName())) {
                    violatesKnowledge = true;
                }
            }
        }

        Set<Node> union = new HashSet<>(T);
        union.addAll(naYX);
        boolean clique = isClique(union);
        int cycleBound = -1;
        boolean noCycle = !existsUnblockedSemiDirectedPath(y, x, union, cycleBound);
        return clique && noCycle && !violatesKnowledge;

    }

    private boolean validDelete(Node x, Node y, Set<Node> H, Set<Node> naYX) {
        boolean violatesKnowledge = false;

        if (existsKnowledge()) {
            for (Node h : H) {
                if (knowledge.isForbidden(x.getName(), h.getName())) {
                    violatesKnowledge = true;
                }

                if (knowledge.isForbidden(y.getName(), h.getName())) {
                    violatesKnowledge = true;
                }
            }
        }

        Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);
        return isClique(diff) && !violatesKnowledge;
    }

    // Adds edges required by knowledge.
    private void addRequiredEdges(Graph graph) {

        if (!existsKnowledge()) {
            return;
        }

        for (Iterator<KnowledgeEdge> it = getKnowledge().requiredEdgesIterator(); it.hasNext() && !Thread.currentThread().isInterrupted(); ) {
            KnowledgeEdge next = it.next();

            Node nodeA = graph.getNode(next.getFrom());
            Node nodeB = graph.getNode(next.getTo());

            if (!graph.paths().isAncestorOf(nodeB, nodeA)) {
                graph.removeEdges(nodeA, nodeB);
                graph.addDirectedEdge(nodeA, nodeB);

                if (verbose) {
                    TetradLogger.getInstance().log("Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB));
                    out.println("Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB));
                }
            }
        }
        for (Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final String A = edge.getNode1().getName();
            final String B = edge.getNode2().getName();

            if (knowledge.isForbidden(A, B)) {
                Node nodeA = edge.getNode1();
                Node nodeB = edge.getNode2();

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.paths().isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (verbose) {
                            TetradLogger.getInstance().log("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                            out.println("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }

                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.paths().isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (verbose) {
                            TetradLogger.getInstance().log("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                            out.println("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }
            } else if (knowledge.isForbidden(B, A)) {
                Node nodeA = edge.getNode2();
                Node nodeB = edge.getNode1();

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.paths().isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (verbose) {
                            TetradLogger.getInstance().log("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                            out.println("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }
                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.paths().isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (verbose) {
                            TetradLogger.getInstance().log("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                            out.println("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }
            }
        }

    }

    // Use background knowledge to decide if an insert or delete operation does not orient edges in a forbidden
    // direction according to prior knowledge. If some orientation is forbidden in the subset, the whole subset is
    // forbidden.
    private boolean invalidSetByKnowledge(Node y, Set<Node> subset) {
        for (Node node : subset) {
            if (getKnowledge().isForbidden(node.getName(), y.getName())) {
                return true;
            }
        }
        return false;
    }

    // Find all adj that are connected to Y by an undirected edge that are adjacent to X (that is, by undirected or
    // directed edge).
    private Set<Node> getNaYX(Node x, Node y) {
        List<Node> adj = graph.getAdjacentNodes(y);
        Set<Node> nayx = new HashSet<>();

        for (Node z : adj) {
            if (z == x) {
                continue;
            }
            Edge yz = graph.getEdge(y, z);
            if (!Edges.isUndirectedEdge(yz)) {
                continue;
            }
            if (!graph.isAdjacentTo(z, x)) {
                continue;
            }
            nayx.add(z);
        }

        return nayx;
    }

//    Set<Edge> cliqueEdges = new HashSet<>();

    // Returns true iif the given set forms a clique in the given graph.
    private boolean isClique(Set<Node> nodes) {
        List<Node> _nodes = new ArrayList<>(nodes);
        for (int i = 0; i < _nodes.size(); i++) {
            for (int j = i + 1; j < _nodes.size(); j++) {
                if (!graph.isAdjacentTo(_nodes.get(i), _nodes.get(j))) {
                    return false;
                }
            }
        }

        return true;

    }

    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound'. Cycle checker in other words.
    private boolean existsUnblockedSemiDirectedPath(Node from, Node to, Set<Node> cond, int bound) {

        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();
        Q.offer(from);
        V.add(from);
        Node e = null;
        int distance = 0;

        while (!Q.isEmpty()) {
            Node t = Q.remove();
            if (t == to) {
                return true;
            }

            if (e == t) {
                e = null;
                distance++;
                if (distance > (bound == -1 ? 1000 : bound)) {
                    return false;
                }
            }

            for (Node u : graph.getAdjacentNodes(t)) {
                Edge edge = graph.getEdge(t, u);
                Node c = traverseSemiDirected(t, edge);
                if (c == null) {
                    continue;
                }
                if (cond.contains(c)) {
                    continue;
                }

                if (c == to) {
                    return true;
                }

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        return false;
    }

    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();

        int i = -1;

        for (Node n : nodes) {
            this.hashIndices.put(n, ++i);
        }
    }

    // Removes information associated with an edge x->y.
    private synchronized void clearArrow(Node x, Node y) {
        final OrderedPair<Node> pair = new OrderedPair<>(x, y);
        final Set<Arrow> lookupArrows = this.lookupArrows.get(pair);

        if (lookupArrows != null) {
            sortedArrows.removeAll(lookupArrows);
        }

        this.lookupArrows.remove(pair);
    }

    // Adds the given arrow for the adjacency i->j. These all are for i->j but may have
    // different T or H or NaYX sets, and so different bumps.
    private void addLookupArrow(Node i, Node j, Arrow arrow) {
        OrderedPair<Node> pair = new OrderedPair<>(i, j);
        Set<Arrow> arrows = lookupArrows.computeIfAbsent(pair, k -> new ConcurrentSkipListSet<>());
        arrows.add(arrow);
    }

    /**
     * Computes the score of a given directed acyclic graph (DAG).
     *
     * @param dag the directed acyclic graph to be scored
     * @return the computed score of the DAG as a double
     */
    public double scoreDag(Graph dag) {
        buildIndexing(dag.getNodes());

        double _score = 0.0;

        for (Node y : dag.getNodes()) {
            Set<Node> parents = new HashSet<>(dag.getParents(y));
            int[] parentIndices = new int[parents.size()];
            Iterator<Node> pi = parents.iterator();
            int count = 0;

            while (pi.hasNext()) {
                Node nextParent = pi.next();
                parentIndices[count++] = hashIndices.get(nextParent);
            }

            int yIndex = hashIndices.get(y);
            Set<Node> populationParents = new HashSet<>(this.populationGraph.getParents(y));
            int[] populationParentIndices = new int[populationParents.size()];
            count = 0;
            for (Node parent : populationParents) {
                populationParentIndices[count++] = hashIndices.get(parent);
            }

            Set<Node> populationChildren = new HashSet<>(this.populationGraph.getChildren(y));
            int[] populationChildrenIndices = new int[populationChildren.size()];
            count = 0;
            for (Node child : populationChildren) {
                populationChildrenIndices[count++] = hashIndices.get(child);
            }
//			System.out.println(y);
//			System.out.println(dag.getParents(y));
//			System.out.println(this.populationGraph.getParents(y));
//			System.out.println(Arrays.toString(parentIndices));
//			System.out.println(Arrays.toString(populationParentIndices));
//			System.out.println("---------------");
            double ls = score.localScore1(yIndex, parentIndices, populationParentIndices, populationChildrenIndices);
//          double ls = score.localScore(yIndex, parentIndices);
//			System.out.println( "node " + y +", pa_is = " + Arrays.toString(parentIndices) + ", pa_pop = " + Arrays.toString(populationParentIndices) +" :" + ls);
            _score += ls;
        }

        return _score;
    }

    //===========================SCORING METHODS===================//

    /**
     * Computes the score of a Directed Acyclic Graph (DAG) based on a given population graph.
     * This method evaluates each node in the DAG, considering its parents and the corresponding nodes
     * and structure in the population graph.
     *
     * @param dag The directed acyclic graph whose score is to be calculated.
     * @param pop The population graph used as a reference for scoring the DAG.
     * @return The cumulative score of the DAG based on the population graph.
     */
    public double scoreDag(Graph dag, Graph pop) {
        buildIndexing(pop.getNodes());

        double _score = 0.0;

        for (Node y : dag.getNodes()) {
            Set<Node> parents = new HashSet<>(dag.getParents(y));
            int[] parentIndices = new int[parents.size()];
            Iterator<Node> pi = parents.iterator();
            int count = 0;

            while (pi.hasNext()) {
                Node nextParent = pi.next();
                parentIndices[count++] = hashIndices.get(nextParent);
            }

            int yIndex = hashIndices.get(y);
            Set<Node> populationParents = new HashSet<>(this.populationGraph.getParents(y));
            int[] populationParentIndices = new int[populationParents.size()];
            count = 0;
            for (Node parent : populationParents) {
                populationParentIndices[count++] = hashIndices.get(parent);
            }

            Set<Node> populationChildren = new HashSet<>(this.populationGraph.getChildren(y));
            int[] populationChildrenIndices = new int[populationChildren.size()];
            count = 0;
            for (Node child : populationChildren) {
                populationChildrenIndices[count++] = hashIndices.get(child);
            }
//			System.out.println(y);
//			System.out.println(dag.getParents(y));
//			System.out.println(this.populationGraph.getParents(y));
//			System.out.println(Arrays.toString(parentIndices));
//			System.out.println(Arrays.toString(populationParentIndices));
//			System.out.println("---------------");
            double ls = score.localScore1(yIndex, parentIndices, populationParentIndices, populationChildrenIndices);
//          double ls = score.localScore(yIndex, parentIndices);
//			System.out.println( "node " + y +", pa_is = " + Arrays.toString(parentIndices) + ", pa_pop = " + Arrays.toString(populationParentIndices) +" :" + ls);
            _score += ls;
        }

        return _score;
    }

    private double scoreGraphChange(Node y, Set<Node> parents,
                                    Node x, Map<Node, Integer> hashIndices) {
        int yIndex = hashIndices.get(y);

        if (x == y) {
            throw new IllegalArgumentException();
        }

        if (parents.contains(y)) {
            throw new IllegalArgumentException();
        }

        int[] parentIndices = new int[parents.size()];

        int count = 0;
        for (Node parent : parents) {
            parentIndices[count++] = hashIndices.get(parent);
        }

        Set<Node> populationParents = new HashSet<>(this.populationGraph.getParents(y));

        int[] populationParentIndices = new int[populationParents.size()];
        count = 0;
        for (Node parent : populationParents) {
            populationParentIndices[count++] = hashIndices.get(parent);
        }

        Set<Node> populationChildren = new HashSet<>(this.populationGraph.getChildren(y));
        int[] populationChildrenIndices = new int[populationChildren.size()];
        count = 0;
        for (Node child : populationChildren) {
            populationChildrenIndices[count++] = hashIndices.get(child);
        }
        return score.localScoreDiff(hashIndices.get(x), yIndex, parentIndices, populationParentIndices, populationChildrenIndices);

//        return score.localScoreDiff(hashIndices.get(x), yIndex, parentIndices);
    }

    private List<Node> getVariables() {
        return variables;
    }

    /**
     * Internal.
     */
    private enum Mode {
        allowUnfaithfulness, heuristicSpeedup, coverNoncolliders
    }

    // Basic data structure for an arrow a->b considered for addition or removal from the graph, together with
// associated sets needed to make this determination. For both forward and backward direction, NaYX is needed.
// For the forward direction, TNeighbors neighbors are needed; for the backward direction, H neighbors are needed.
// See Chickering (2002). The totalScore difference resulting from added in the edge (hypothetically) is recorded
// as the "bump".
    private static class Arrow implements Comparable<Arrow> {

        private final double bump;
        private final Node a;
        private final Node b;
        private final Set<Node> hOrT;
        private Set<Node> TNeighbors;
        private final Set<Node> naYX;
        private final int index;

        Arrow(double bump, Node a, Node b, Set<Node> hOrT, Set<Node> capTorH, Set<Node> naYX, int index) {
            this.bump = bump;
            this.a = a;
            this.b = b;
            this.setTNeighbors(capTorH);
            this.hOrT = hOrT;
            this.naYX = naYX;
            this.index = index;
        }

        public double getBump() {
            return bump;
        }

        public Node getA() {
            return a;
        }

        public Node getB() {
            return b;
        }

        Set<Node> getHOrT() {
            return hOrT;
        }

        Set<Node> getNaYX() {
            return naYX;
        }

        // Sorting by bump, high to low. The problem is the SortedSet contains won't add a new element if it compares
        // to zero with an existing element, so for the cases where the comparison is to zero (i.e., have the same
        // bump, we need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables.
        // The fastest way to do this is using a hash code, though it's still possible for two Arrows to have the
        // same hash code but not be equal. If we're paranoid, in this case, we calculate a determinate comparison
        // not equal to zero by keeping a list. This last part is commened out by default.
        public int compareTo(@NotNull Arrow arrow) {

            final int compare = Double.compare(arrow.getBump(), getBump());

            if (compare == 0) {
                return Integer.compare(getIndex(), arrow.getIndex());
            }

            return compare;
        }

        public String toString() {
            return "Arrow<" + a + "->" + b + " bump = " + bump + " t/h = " + hOrT + " naYX = " + naYX + ">";
        }

        public int getIndex() {
            return index;
        }

        Set<Node> getTNeighbors() {
            return TNeighbors;
        }

        void setTNeighbors(Set<Node> TNeighbors) {
            this.TNeighbors = TNeighbors;
        }

    }

    class NodeTaskEmptyGraph extends RecursiveTask<Boolean> {
        private final int from;
        private final int to;
        private final List<Node> nodes;
        private final Set<Node> emptySet;

        public NodeTaskEmptyGraph(int from, int to, List<Node> nodes, Set<Node> emptySet) {
            this.from = from;
            this.to = to;
            this.nodes = nodes;
            this.emptySet = emptySet;
        }

        @Override
        protected Boolean compute() {
            for (int i = from; i < to; i++) {
                if ((i + 1) % 1000 == 0) {
                    count[0] += 1000;
                    out.println("Initializing effect edges: " + (count[0]));
                }

                Node y = nodes.get(i);
                neighbors.put(y, emptySet);

                for (int j = i + 1; j < nodes.size(); j++) {
                    Node x = nodes.get(j);

                    if (existsKnowledge()) {
                        if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                            continue;
                        }

                        if (invalidSetByKnowledge(y, emptySet)) {
                            continue;
                        }
                    }

                    if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
                        continue;
                    }

                    // start: changed by Fattaneh
                    int child = hashIndices.get(y);
                    int parent = hashIndices.get(x);
                    double bump, bump2 = 0.0;

                    Set<Node> populationParents = new HashSet<>(populationGraph.getParents(y));
                    int[] populationParentIndices = new int[populationParents.size()];
                    int c = 0;
                    for (Node p : populationParents) {
                        populationParentIndices[c++] = hashIndices.get(p);
                    }

                    Set<Node> populationChildren = new HashSet<>(populationGraph.getChildren(y));
                    int[] populationChildrenIndices = new int[populationChildren.size()];
                    c = 0;
                    for (Node ch : populationChildren) {
                        populationChildrenIndices[c++] = hashIndices.get(ch);
                    }

                    // if the initial graph is empty, proceed as usual
                    if (initialGraph == null) {
                        bump = score.localScoreDiff(parent, child, new int[0], populationParentIndices, populationChildrenIndices);
                    } else {
                        // if x or y has no adjacency in the initial graph, then proceed as if the initial graph is empty
                        if (initialGraph.getAdjacentNodes(x).isEmpty() && initialGraph.getAdjacentNodes(y).isEmpty()) {
                            bump = score.localScoreDiff(parent, child, new int[0], populationParentIndices, populationChildrenIndices);

                        }
                        // if x or y has adjacencies in the initial graph, then that should be considered in scoring
                        else {
                            int[] parentIndicesY;
                            Set<Node> parentsY = new HashSet<>(initialGraph.getParents(y));
                            parentIndicesY = new int[parentsY.size()];
                            c = 0;
                            for (Node p : parentsY) {
                                parentIndicesY[c++] = hashIndices.get(p);
                            }

                            bump = score.localScoreDiff(parent, child, parentIndicesY, populationParentIndices, populationChildrenIndices);
                        }

                    }

                    // computing the bump of an edge from y (child) --> x (parent)
                    if (symmetricFirstStep) {
                        Set<Node> populationParentsX = new HashSet<>(populationGraph.getParents(x));
                        int[] populationParentIndicesX = new int[populationParentsX.size()];
                        c = 0;
                        for (Node p : populationParentsX) {
                            populationParentIndicesX[c++] = hashIndices.get(p);
                        }

                        Set<Node> populationChildrenX = new HashSet<>(populationGraph.getChildren(x));
                        int[] populationChildrenIndicesX = new int[populationChildrenX.size()];
                        c = 0;
                        for (Node ch : populationChildrenX) {
                            populationChildrenIndicesX[c++] = hashIndices.get(ch);
                        }

                        if (initialGraph == null) {
                            bump2 = score.localScoreDiff(child, parent, new int[0], populationParentIndicesX, populationChildrenIndicesX);
                        } else {
                            // if x or y has no adjacency, then proceed as an empty initial graph
                            if (initialGraph.getAdjacentNodes(x).isEmpty() && initialGraph.getAdjacentNodes(y).isEmpty()) {
                                bump2 = score.localScoreDiff(child, parent, new int[0], populationParentIndicesX, populationChildrenIndicesX);

                            } else {
                                int[] parentIndicesX;
                                Set<Node> parentsX = new HashSet<>(initialGraph.getParents(x));
                                parentIndicesX = new int[parentsX.size()];
                                c = 0;
                                for (Node p : parentsX) {
                                    parentIndicesX[c++] = hashIndices.get(p);
                                }

//								bump2  = score.localScoreDiff(child, parent, parentIndicesX);
                                bump = score.localScoreDiff(child, parent, parentIndicesX, populationParentIndicesX, populationChildrenIndicesX);

                            }

                        }

                        bump = Math.max(bump, bump2);
                    }

//                    if (symmetricFirstStep) {
//                        double bump2 = score.localScoreDiff(child, parent);
//                        bump = bump > bump2 ? bump : bump2;
//                    }

                    if (bump > 0) {
                        final Edge edge = Edges.undirectedEdge(x, y);
                        effectEdgesGraph.addEdge(edge);
                    }

                    if (bump > 0) {
                        if (initialGraph == null) {
                            addArrow(x, y, emptySet, emptySet, emptySet, bump);

                            if (!symmetricFirstStep) {
                                addArrow(y, x, emptySet, emptySet, emptySet, bump2);
                            }

                        } else {
                            if (initialGraph.getAdjacentNodes(x).isEmpty() && initialGraph.getAdjacentNodes(y).isEmpty()) {
                                addArrow(x, y, emptySet, emptySet, emptySet, bump);

                                if (!symmetricFirstStep) {
                                    addArrow(y, x, emptySet, emptySet, emptySet, bump2);
                                }
                            } else {
//								System.out.println("x: " +  x+ ", y: " + y);
//								System.out.println("sortedArrows before calculateArrowsForward: " +  sortedArrows);
                                calculateArrowsForward(x, y);
//								System.out.println("sortedArrows after calculateArrowsForward: " +  sortedArrows);
                                calculateArrowsForward(y, x);
//								System.out.println("sortedArrows after calculateArrowsForward IN REVERSE: " + sortedArrows);
                            }
                        }
                    }
                    if (symmetricFirstStep) {
                        if (bump2 > 0) {
                            if (initialGraph == null) {
                                addArrow(y, x, emptySet, emptySet, emptySet, bump2);

                            } else {
                                if (initialGraph.getAdjacentNodes(x).isEmpty() && initialGraph.getAdjacentNodes(y).isEmpty()) {
                                    addArrow(y, x, emptySet, emptySet, emptySet, bump2);

                                }
                            }
                        }
                    }
                }
            }

            return true;
        }
    }

}