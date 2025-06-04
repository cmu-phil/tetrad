///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.ScoredGraph;
import edu.cmu.tetrad.search.utils.Bes;
import edu.cmu.tetrad.search.utils.DagScorer;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;

import static edu.cmu.tetrad.graph.Edges.directedEdge;
import static org.apache.commons.math3.util.FastMath.max;
import static org.apache.commons.math3.util.FastMath.min;

/**
 * Implements the Fast Greedy Equivalence Search (FGES) algorithm. This is an implementation of the Greedy Equivalence
 * Search algorithm, originally due to Chris Meek but developed significantly by Max Chickering. FGES uses with some
 * optimizations that allow it to scale accurately to thousands of variables accurately for the sparse case. The
 * reference for FGES is this:
 * <p>
 * Ramsey, J., Glymour, M., Sanchez-Romero, R., &amp; Glymour, C. (2017). A million variables and more: the fast greedy
 * equivalence search algorithm for learning high-dimensional graphical causal models, with an application to functional
 * magnetic resonance images. International journal of data science and analytics, 3, 121-129.
 * <p>
 * The reference for Chickering's GES is this:
 * <p>
 * Chickering (2002) "Optimal structure identification with greedy search" Journal of Machine Learning Research.
 * <p>
 * FGES works for the continuous case, the discrete case, and the mixed continuous/discrete case, so long as a BIC score
 * is available for the type of data in question.
 * <p>
 * To speed things up, it has been assumed that variables X and Y with zero correlation do not correspond to edges in
 * the graph. This is a restricted form of the heuristic speedup assumption, something GES does not assume. This
 * heuristic speedup assumption needs to be explicitly turned on using setHeuristicSpeedup(true).
 * <p>
 * Also, edges to be added or remove from the graph in the forward or backward phase, respectively are cached, together
 * with the ancillary information needed to do the additions or removals, to reduce rescoring.
 * <p>
 * A number of other optimizations were also. See code for details.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author Ricardo Silva
 * @author josephramsey
 * @version $Id: $Id
 * @see Grasp
 * @see Boss
 * @see Sp
 * @see Knowledge
 */
public final class FgesMb implements DagScorer {
    /**
     * Represents an empty set of nodes. This set is a final variable and cannot be modified.
     */
    private final Set<Node> emptySet = new HashSet<>();
    /**
     * This variable represents an array of integers called count. It is a private final field, meaning it cannot be
     * changed after initialization.
     * <p>
     * The array count has a length of 1, indicating that it can hold one integer value. It is initialized with a
     * default value of 0. The index of the array is 0 and can be accessed using count[0].
     */
    private final int[] count = new int[1];
    /**
     * Represents the depth of a variable. The depth determines how deep the variable is nested within the code
     * structure.
     */
    private final int depth = 10000;
    /**
     * The top n graphs found by the algorithm, where n is numPatternsToStore.
     */
    private final LinkedList<ScoredGraph> topGraphs = new LinkedList<>();
    /**
     * Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
     */
    private final SortedSet<Arrow> sortedArrows = new ConcurrentSkipListSet<>();
    /**
     * The logger variable is an instance of the TetradLogger class, which is used for logging purposes in the
     * software.
     * <p>
     * TetradLogger is a utility class for logging various messages during the execution of the software. It provides
     * methods for logging different levels of messages, including debug, info, warning, and error messages.
     * <p>
     * By using the logger variable, you can log messages to the console or any other configured output destination.
     * <p>
     * Example usage: logger.debug("This is a debug message"); logger.info("This is an info message");
     * logger.warning("This is a warning message"); logger.error("This is an error message");
     * <p>
     * Note: This documentation assumes that the TetradLogger.getInstance() method returns a valid instance of the
     * TetradLogger class.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * A private final Map representing the arrow configurations for each Edge. The map is implemented using
     * ConcurrentHashMap to provide thread-safe access to the map. The map contains Edge objects as keys and ArrowConfig
     * objects as values.
     */
    private final Map<Edge, ArrowConfig> arrowsMap = new ConcurrentHashMap<>();
    List<Node> targets = new ArrayList<>();
    /**
     * The number of times the forward phase is iterated to expand to new adjacencies.
     */
    private int numExpansions = 2;
    /**
     * The style of trimming to use.
     */
    private int trimmingStyle = 3; // default MB trimming.
    /**
     * Bounds the degree of the graph.
     */
    private int maxDegree = -1;
    /**
     * Whether one-edge faithfulness is assumed (less general but faster).
     */
    private boolean faithfulnessAssumed = false;
    /**
     * The knowledge to use in the search.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * True, if FGES should run in a single thread, no if parallelized.
     */
    private boolean parallelized = false;
    /**
     * The variables to use in the search.
     */
    private List<Node> variables;
    /**
     * The initial graph.
     */
    private Graph initialGraph;
    /**
     * The graph to which the search is bound.
     */
    private Graph boundGraph = null;
    /**
     * The elapsed time of the search.
     */
    private long elapsedTime;
    /**
     * The score of the graph.
     */
    private Score score;
    /**
     * Whether verbose output should be produced.
     */
    private boolean verbose = false;
    /**
     * Map from variables to their column indices in the data set.
     */
    private ConcurrentMap<Node, Integer> hashIndices;
    /**
     * A graph where X--Y means that X and Y have non-zero total effect on one another.
     */
    private Graph effectEdgesGraph;
    /**
     * Where printed output is sent.
     */
    private transient PrintStream out = System.out;
    /**
     * The graph being constructed.
     */
    private Graph graph;
    /**
     * Arrows with the same totalScore are stored in this list to distinguish their order in sortedArrows. The ordering
     * doesn't matter; it just has to be transitive.
     */
    private int arrowIndex = 0;
    /**
     * The score of the model.
     */
    private double modelScore;
    /**
     * The mode determines the behavior of the software in certain situations. It is an internal parameter and should
     * not be modified directly.
     * <p>
     * Possible values are: - allowUnfaithfulness: This mode allows the software to assume one-edge faithfulness,
     * meaning that if two variables are unconditionally dependent, there is an edge between them in the graph.
     * <p>
     * - heuristicSpeedup: This mode enables a heuristic speedup during the search. It may sacrifice some accuracy for
     * faster execution.
     * <p>
     * - coverNoncolliders: This mode ensures that the software includes all noncolliders, which are nonadjacent
     * variables that are indirectly connected through a collider (a node with two incoming edges).
     */
    private Mode mode = Mode.heuristicSpeedup;
    /**
     * True if the first step of adding an edge to an empty graph should be scored in both directions for each edge with
     * the maximum score chosen.
     */
    private boolean symmetricFirstStep = false;
    /**
     * The list of all targets.
     */
    private ArrayList<Node> allTargets;

    /**
     * Constructor. Construct a Score and pass it in here. The totalScore should return a positive value in case of
     * conditional dependence and a negative values in case of conditional independence. See Chickering (2002), locally
     * consistent scoring criterion. This by default uses all the processors on the machine.
     *
     * @param score The score to use. The score should yield better scores for more correct local models. The algorithm
     *              as given by Chickering assumes the score will be a BIC score of some sort.
     */
    public FgesMb(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        setScore(score);
        this.graph = new EdgeListGraph(getVariables());
    }

    /**
     * Traverses a semi-directed graph and returns the connected node based on the given edge.
     *
     * @param node The starting node of the traversal.
     * @param edge The edge connecting the nodes.
     * @return The connected node if the traversal is possible, otherwise null.
     */
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

    /**
     * Sets the trimming style for the algorithm.
     *
     * @param trimmingStyle The trimming style to be set. It represents how edges are trimmed during the search. The
     *                      valid values are: - 0: No trimming. All edges are considered during the search. - 1: Forward
     *                      trimming. Edges are trimmed only during the forward search phase. - 2: Backward trimming.
     *                      Edges are trimmed only during the backward search phase.
     */
    public void setTrimmingStyle(int trimmingStyle) {
        this.trimmingStyle = trimmingStyle;
    }

    /**
     * Greedy equivalence search: Start from the empty graph, add edges till the model is significant. Then start
     * deleting edges till a minimum is achieved.
     *
     * @param targets a {@link java.util.List} object
     * @return the resulting Pattern.
     * @throws InterruptedException if any
     */
    public Graph search(List<Node> targets) throws InterruptedException {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("Target(s) weren't specified");
        }

        for (Node target : targets) {
            if (target == null) {
                throw new IllegalArgumentException("Target(s) weren't specified");
            }
        }

        List<Node> _targets = new ArrayList<>();

        for (Node target : targets) {
            _targets.add(graph.getNode(target.getName()));
        }

        this.targets = _targets;
        allTargets = new ArrayList<>(this.targets);

        long start = MillisecondTimes.timeMillis();
        topGraphs.clear();

        graph = new EdgeListGraph(getVariables());

        if (boundGraph != null) {
            boundGraph = GraphUtils.replaceNodes(boundGraph, getVariables());
        }

        if (initialGraph != null) {
            graph = new EdgeListGraph(initialGraph);
            graph = GraphUtils.replaceNodes(graph, getVariables());
        }

        this.allTargets = new ArrayList<>(targets);

        for (int i = 0; i < numExpansions; i++) {
            for (Node n : new ArrayList<>(allTargets)) {
                for (Node a : graph.getAdjacentNodes(n)) {
                    if (!allTargets.contains(a)) {
                        allTargets.add(a);
                    }
                }
            }

            graph = new EdgeListGraph(getVariables());

            doLoop();

        }

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - start;

        if (verbose) {
            this.logger.log("Elapsed time = " + (elapsedTime) / 1000. + " s");
        }

        this.modelScore = scoreDag(GraphTransforms.dagFromCpdag(graph, null), true);
        graph = GraphUtils.trimGraph(targets, graph, trimmingStyle);
        return graph;
    }

    /**
     * Performs a loop of operations for the FgesMb algorithm. The loop consists of the following steps: 1. Adds
     * required edges to the graph. 2. Initializes effect edges based on the variables. 3. Sets the mode to
     * heuristicSpeedup. 4. Performs forward equivalence search (fes). 5. Performs backward equivalence search (bes). 6.
     * Sets the mode to coverNoncolliders. 7. Performs fes again. 8. Performs bes again. 9. If faithfulnessAssumed is
     * false, sets the mode to allowUnfaithfulness and performs fes and bes again.
     * @throws InterruptedException if any
     */
    private void doLoop() throws InterruptedException {
        addRequiredEdges(graph);

        initializeEffectEdges(getVariables());

        this.mode = Mode.heuristicSpeedup;

        try {
            fes();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        bes();

        this.mode = Mode.coverNoncolliders;

        try {
            fes();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        bes();

        if (!faithfulnessAssumed) {
            this.mode = Mode.allowUnfaithfulness;

            try {
                fes();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            bes();
        }
    }

    /**
     * Sets whether one-edge faithfulness should be assumed. This assumption is that if X and Y are unconditionally
     * dependent, then there is an edge between X and Y in the graph. This could in principle be false, as for a path
     * cancellation whether one path is A->B->C->D and the other path is A->D.
     *
     * @param faithfulnessAssumed True, if so.
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    /**
     * Returns the background knowledge.
     *
     * @return This knowledge
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
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
    }

    /**
     * Returns the elapsed time of the search.
     *
     * @return This elapsed time.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Scores the given directed acyclic graph (DAG).
     *
     * @param dag The directed acyclic graph to be scored. Must be of type {@link Graph}.
     * @return The score of the DAG.
     */
    public double scoreDag(Graph dag) {
        return scoreDag(dag, false);
    }

    /**
     * Sets whether verbose output should be produced. Verbose output generated by the Meek rules is treated
     * separately.
     *
     * @param verbose True iff the case.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the output stream associated with this object.
     *
     * @return the output stream
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent to. By default System.out.
     *
     * @param out This print stream.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * If non-null, edges not adjacent in this graph will not be added.
     *
     * @param boundGraph This bound graph.
     */
    public void setBoundGraph(Graph boundGraph) {
        if (boundGraph == null) {
            this.boundGraph = null;
        } else {
            this.boundGraph = GraphUtils.replaceNodes(boundGraph, getVariables());
        }
    }

    /**
     * The maximum of parents any nodes can have in the output pattern.
     *
     * @return -1 for unlimited.
     */
    public int getMaxDegree() {
        return maxDegree;
    }

    /**
     * The maximum of parents any nodes can have in the output pattern.
     *
     * @param maxDegree -1 for unlimited.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException();
        }
        this.maxDegree = maxDegree;
    }

    /**
     * Sets whether the first step of the procedure will score both X->Y and Y->X and prefer the higher score (for
     * adding X--Y to the graph).
     *
     * @param symmetricFirstStep True iff the case.
     */
    public void setSymmetricFirstStep(boolean symmetricFirstStep) {
        this.symmetricFirstStep = symmetricFirstStep;
    }

    /**
     * Returns the score of the final search model.
     *
     * @return This score.
     */
    public double getModelScore() {
        return modelScore;
    }

    /**
     * Sets the score and initializes the variables and indexing. The score represents a set of musical notes. The
     * variables are the measured nodes in the score. The indexing is used for efficient retrieval of nodes based on
     * their properties. The maxDegree represents the maximum number of simultaneous notes in the score.
     *
     * @param score the score to set
     */
    private void setScore(Score score) {
        this.score = score;

        this.variables = new ArrayList<>();

        for (Node node : score.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }

        buildIndexing(score.getVariables());

        this.maxDegree = this.score.getMaxDegree();
    }

    /**
     * Calculates the size of each chunk for parallel processing.
     *
     * @param n The total number of elements to be processed.
     * @return The size of each chunk for parallel processing.
     */
    private int getChunkSize(int n) {
        int chunk = n / Runtime.getRuntime().availableProcessors();
        if (chunk < 100) chunk = 100;
        return chunk;
    }

    /**
     * Initializes the effect edges graph with the given list of nodes.
     *
     * @param nodes The list of nodes to initialize the effect edges graph with
     */
    private void initializeEffectEdges(final List<Node> nodes) {
        long start = MillisecondTimes.timeMillis();
        this.effectEdgesGraph = new EdgeListGraph(nodes);

        List<Callable<Boolean>> tasks = new ArrayList<>();

        int chunkSize = getChunkSize(nodes.size());

        for (int i = 0; i < nodes.size() /*&& !Thread.currentThread().isInterrupted()*/; i += chunkSize) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            NodeTaskEmptyGraph task = new NodeTaskEmptyGraph(i, min(nodes.size(), i + chunkSize),
                    nodes, emptySet);

            if (!parallelized) {
                task.call();
            } else {
                tasks.add(task);
            }
        }

        if (parallelized) {
            int parallelism = Runtime.getRuntime().availableProcessors();
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            try {
                pool.invokeAll(tasks);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }

        long stop = MillisecondTimes.timeMillis();

        if (verbose) {
            out.println("Elapsed initializeForwardEdgesFromEmptyGraph = " + (stop - start) + " ms");
        }
    }

    /**
     * Executes the forward elimination step in the FES algorithm.
     * <p>
     * This method performs the forward elimination step in the FES algorithm, which eliminates arrows from the graph
     * according to specific criteria. The method iterates through the sorted arrows and checks if each arrow meets the
     * elimination criteria. If an arrow does not meet the criteria, it is skipped. If an arrow meets all the criteria,
     * it is used to insert a new arrow into the graph and update the set of arrows to be processed. The method
     * continues this process until there are no more sorted arrows to be processed.
     * <p>
     * The criteria for arrow elimination are as follows: - Both nodes of the arrow must be part of the set of target
     * variables. - The nodes must not be adjacent in the graph. - The degree of both nodes must be within the maximum
     * degree limit. - The NaYX (neighbors of either X or Y) of the arrow must match the current NaYX of X and Y. - The
     * set of t-neighbors (common neighbors) of X and Y must match the current set of t-neighbors of the arrow. - The
     * set of parent nodes of Y must match the parent nodes of the arrow. - The insertion of the new arrow must be valid
     * according to certain criteria.
     * <p>
     * This method does not return any values. It updates the graph by inserting new arrows and updating the set of
     * arrows to be processed.
     * @throws InterruptedException if any
     */
    private void fes() throws InterruptedException {
        int maxDegree = this.maxDegree == -1 ? 1000 : this.maxDegree;

        reevaluateForward(new HashSet<>(variables));

        while (!sortedArrows.isEmpty()) {
            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (!(allTargets.contains(x) || allTargets.contains(y))) {
                continue;
            }

            if (graph.isAdjacentTo(x, y)) {
                continue;
            }

            if (graph.getDegree(x) > maxDegree - 1) {
                continue;
            }

            if (graph.getDegree(y) > maxDegree - 1) {
                continue;
            }

            if (!getNaYX(x, y).equals(arrow.getNaYX())) {
                continue;
            }

            if (!new HashSet<>(getTNeighbors(x, y)).equals(arrow.getTNeighbors())) {
                continue;
            }

            if (!new HashSet<>(graph.getParents(y)).equals(new HashSet<>(arrow.getParents()))) {
                continue;
            }

            if (!validInsert(x, y, arrow.getHOrT(), getNaYX(x, y))) {
                continue;
            }

            insert(x, y, arrow.getHOrT(), arrow.getBump());

            Set<Node> process = revertToCPDAG();

            process.add(x);
            process.add(y);
            process.addAll(getCommonAdjacents(x, y));

            reevaluateForward(new HashSet<>(process));
        }
    }

    /**
     * Executes the BES algorithm to calculate the optimal score for a given graph and set of variables.
     * <p>
     * This method creates a new instance of the Bes class, sets the depth, verbose, and knowledge parameters, and then
     * invokes the bes() method of the Bes instance to calculate the optimal score.
     *
     * @see Bes
     * @throws InterruptedException if any
     */
    private void bes() throws InterruptedException {
        Bes bes = new Bes(score);
        bes.setDepth(depth);
        bes.setVerbose(verbose);
        bes.setKnowledge(knowledge);
        bes.bes(graph, variables);
    }

    /**
     * Checks if knowledge exists.
     *
     * @return true if knowledge exists, false otherwise.
     */
    private boolean existsKnowledge() {
        return !knowledge.isEmpty();
    }

    /**
     * Reevaluates the forward arrows for a set of nodes.
     *
     * @param nodes the set of nodes for which to reevaluate the forward arrows
     * @throws InterruptedException if any
     */
    private void reevaluateForward(final Set<Node> nodes) throws InterruptedException {
        class AdjTask implements Callable<Boolean> {

            private final List<Node> nodes;
            private final int from;
            private final int to;

            private AdjTask(List<Node> nodes, int from, int to) {
                this.nodes = nodes;
                this.from = from;
                this.to = to;
            }


            @Override
            public Boolean call() throws InterruptedException {
                for (int _y = from; _y < to; _y++) {
                    if (Thread.currentThread().isInterrupted()) break;

                    Node y = nodes.get(_y);

                    List<Node> adj;

                    if (mode == Mode.heuristicSpeedup) {
                        adj = effectEdgesGraph.getAdjacentNodes(y);
                    } else if (mode == Mode.coverNoncolliders) {
                        Set<Node> g = new HashSet<>();

                        for (Node n : graph.getAdjacentNodes(y)) {
                            for (Node m : graph.getAdjacentNodes(n)) {
                                if (graph.isAdjacentTo(y, m)) {
                                    continue;
                                }

                                if (graph.isDefCollider(m, n, y)) {
                                    continue;
                                }

                                g.add(m);
                            }
                        }

                        adj = new ArrayList<>(g);
                    } else if (mode == Mode.allowUnfaithfulness) {
                        adj = new ArrayList<>(variables);
                    } else {
                        throw new IllegalStateException();
                    }

                    for (Node x : adj) {
                        if (boundGraph != null && !(boundGraph.isAdjacentTo(x, y))) {
                            continue;
                        }

                        calculateArrowsForward(x, y);
                    }
                }

                return true;
            }
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();

        int chunkSize = getChunkSize(nodes.size());

        for (int i = 0; i < nodes.size() /*&& !Thread.currentThread().isInterrupted()*/; i += chunkSize) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            AdjTask task = new AdjTask(new ArrayList<>(nodes), i, min(nodes.size(), i + chunkSize));

            if (!this.parallelized) {
                task.call();
            } else {
                tasks.add(task);
            }
        }

        if (this.parallelized) {
            int parallelism = Runtime.getRuntime().availableProcessors();
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            try {
                pool.invokeAll(tasks);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Calculates the forward arrows between two nodes.
     *
     * @param a The source node.
     * @param b The target node.
     */
    private void calculateArrowsForward(Node a, Node b) throws InterruptedException {
        if (boundGraph != null && !boundGraph.isAdjacentTo(a, b)) {
            return;
        }

        if (a == b) return;

        if (graph.isAdjacentTo(a, b)) return;

        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = getNaYX(a, b);
        List<Node> TNeighbors = getTNeighbors(a, b);
        Set<Node> parents = new HashSet<>(graph.getParents(b));

        HashSet<Node> TNeighborsSet = new HashSet<>(TNeighbors);
        ArrowConfig config = new ArrowConfig(TNeighborsSet, naYX, parents);
        ArrowConfig storedConfig = arrowsMap.get(directedEdge(a, b));
        if (storedConfig != null && storedConfig.equals(config)) return;
        arrowsMap.put(directedEdge(a, b), new ArrowConfig(TNeighborsSet, naYX, parents));

        int _depth = min(depth, TNeighbors.size());

        final SublistGenerator gen = new SublistGenerator(TNeighbors.size(), _depth);// TNeighbors.size());
        int[] choice;

        Set<Node> maxT = null;
        double maxBump = Double.NEGATIVE_INFINITY;
        List<Set<Node>> TT = new ArrayList<>();

        while ((choice = gen.next()) != null) {
            Set<Node> _T = GraphUtils.asSet(choice, TNeighbors);
            TT.add(_T);
        }

        class EvalTask implements Callable<EvalPair> {
            private final List<Set<Node>> Ts;
            private final ConcurrentMap<Node, Integer> hashIndices;
            private final int from;
            private final int to;
            private Set<Node> maxT = null;
            private double maxBump = Double.NEGATIVE_INFINITY;

            public EvalTask(List<Set<Node>> Ts, int from, int to, ConcurrentMap<Node, Integer> hashIndices) {
                this.Ts = Ts;
                this.hashIndices = hashIndices;
                this.from = from;
                this.to = to;
            }

            @Override
            public EvalPair call() throws InterruptedException {
                for (int k = from; k < to; k++) {
                    if (Thread.currentThread().isInterrupted()) break;
                    double _bump = insertEval(a, b, Ts.get(k), naYX, parents, this.hashIndices);

                    if (_bump > maxBump) {
                        maxT = Ts.get(k);
                        maxBump = _bump;
                    }
                }

                EvalPair pair = new EvalPair();
                pair.T = maxT;
                pair.bump = maxBump;

                return pair;
            }
        }

        int chunkSize = getChunkSize(TT.size());
        List<EvalTask> tasks = new ArrayList<>();

        for (int i = 0; i < TT.size() && !Thread.currentThread().isInterrupted(); i += chunkSize) {
            EvalTask task = new EvalTask(TT, i, min(TT.size(), i + chunkSize), hashIndices);

            if (!this.parallelized) {
                EvalPair pair = task.call();

                if (pair.bump > maxBump) {
                    maxT = pair.T;
                    maxBump = pair.bump;
                }
            } else {
                tasks.add(task);
            }
        }

        if (this.parallelized) {
            int parallelism = Runtime.getRuntime().availableProcessors();
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            List<Future<EvalPair>> futures = null;
            futures = pool.invokeAll(tasks);

            for (Future<EvalPair> future : futures) {
                try {
                    EvalPair pair = future.get();
                    if (pair.bump > maxBump) {
                        maxT = pair.T;
                        maxBump = pair.bump;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    Thread.currentThread().interrupt();
                }

                pool.shutdown();
            }
        }

        if (maxBump > 0) {
            addArrowForward(a, b, maxT, TNeighborsSet, naYX, parents, maxBump);
        }
    }

    /**
     * Adds an arrow from node 'a' to node 'b' in a directed graph.
     *
     * @param a          The starting node of the arrow.
     * @param b          The ending node of the arrow.
     * @param hOrT       The set of nodes representing either head nodes or tail nodes.
     * @param TNeighbors The set of nodes representing neighbors of tail nodes.
     * @param naYX       The set of nodes representing all nodes with incoming or outgoing edges.
     * @param parents    The set of nodes representing the parents of a node.
     * @param bump       The value to adjust the curvature of the arrow.
     */
    private void addArrowForward(Node a, Node b, Set<Node> hOrT, Set<Node> TNeighbors, Set<Node> naYX,
                                 Set<Node> parents, double bump) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, TNeighbors, naYX, parents, arrowIndex++);
        sortedArrows.add(arrow);
    }

    /**
     * Returns a set of nodes that are adjacent to both x and y.
     *
     * @param x the first node
     * @param y the second node
     * @return a set of common adjacent nodes
     */
    private Set<Node> getCommonAdjacents(Node x, Node y) {
        Set<Node> adj = new HashSet<>(graph.getAdjacentNodes(x));
        adj.retainAll(graph.getAdjacentNodes(y));
        return adj;
    }

    /**
     * Get all adj that are connected to Y by an undirected edge and not adjacent to X.
     *
     * @param x The first node
     * @param y The second node
     */
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

    /**
     * Evaluate the Insert(X, Y, TNeighbors) operator (Definition 12 from Chickering, 2002).
     *
     * @param x           The source node to be inserted.
     * @param y           The target node to be inserted.
     * @param T           The set of nodes to be inserted.
     * @param naYX        The set of nodes excluding y and x.
     * @param parents     The set of parent nodes of x and y.
     * @param hashIndices The map of node and its index.
     * @return The evaluation score after inserting nodes into the graph.
     * @throws InterruptedException if any
     */
    private double insertEval(Node x, Node y, Set<Node> T, Set<Node> naYX, Set<Node> parents,
                              Map<Node, Integer> hashIndices) throws InterruptedException {
        Set<Node> set = new HashSet<>(naYX);
        set.addAll(T);
        set.addAll(parents);

        return scoreGraphChange(x, y, set, hashIndices);
    }

    /**
     * Inserts an edge between two nodes in the graph and updates the set T, the number of edges and optionally logs the
     * operation. (Definition 12 from Chickering, 2002).
     *
     * @param x    the starting node of the edge
     * @param y    the ending node of the edge
     * @param T    the set of nodes to update
     * @param bump the value to bump
     */
    private void insert(Node x, Node y, Set<Node> T, double bump) {
        graph.addDirectedEdge(x, y);

        int numEdges = graph.getNumEdges();

        if (numEdges % 1000 == 0) {
            out.println("Num edges added: " + numEdges);
        }

        if (verbose) {
            int cond = T.size() + getNaYX(x, y).size() + graph.getParents(y).size();

            final String message = graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y)
                                   + " " + T + " " + bump
                                   + " degree = " + GraphUtils.getDegree(graph)
                                   + " indegree = " + GraphUtils.getIndegree(graph) + " cond = " + cond;
            TetradLogger.getInstance().log(message);
        }

        for (Node _t : T) {
            graph.removeEdge(_t, y);
            graph.addDirectedEdge(_t, y);

            if (verbose) {
                String message = "--- Directing " + graph.getEdge(_t, y);
                TetradLogger.getInstance().log(message);
            }
        }
    }

    /**
     * Checks if inserting node x into node y is valid based on the given sets of nodes. (Theorem 15 from Chickering,
     * 2002).
     *
     * @param x    the node being inserted
     * @param y    the node in which x is being inserted
     * @param T    a set of nodes
     * @param naYX a set of nodes
     * @return true if the insert is valid, false otherwise
     */
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

        return isClique(union) && semidirectedPathCondition(y, x, union)
               && !violatesKnowledge;
    }

    /**
     * Adds required edges to the given graph based on knowledge information.
     *
     * @param graph The graph to add required edges to
     */
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
                        }
                    }
                }

                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.paths().isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (verbose) {
                            TetradLogger.getInstance().log("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
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
                        }
                    }
                }
                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.paths().isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (verbose) {
                            TetradLogger.getInstance().log("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if any node in the given subset is forbidden by knowledge.
     *
     * @param y      the node for which the knowledge is being checked
     * @param subset the subset of nodes to check against the knowledge
     * @return true if any node in the subset is forbidden by the knowledge, false otherwise
     */
    private boolean invalidSetByKnowledge(Node y, Set<Node> subset) {
        for (Node node : subset) {
            if (getKnowledge().isForbidden(node.getName(), y.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the nodes adjacent to node y and also adjacent to node x.
     *
     * @param x the first node
     * @param y the second node
     * @return the set of nodes adjacent to both node x and node y
     */
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

    /**
     * Determines whether the given set of nodes forms a clique in a graph.
     *
     * @param nodes the set of nodes to be checked for clique
     * @return true if the nodes form a clique, false otherwise
     */
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

    /**
     * Checks if there is a semidirected path from the 'from' Node to the 'to' Node satisfying the given condition.
     *
     * @param from The starting Node.
     * @param to   The ending Node.
     * @param cond The Set of Nodes representing the condition.
     * @return True if there is a semidirected path from 'from' to 'to' satisfying the condition, false otherwise.
     * @throws IllegalArgumentException If 'from' and 'to' Nodes are the same.
     */
    private boolean semidirectedPathCondition(Node from, Node to, Set<Node> cond) {
        if (from == to) throw new IllegalArgumentException();

        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();

        Q.add(from);
        V.add(from);

        while (!Q.isEmpty()) {
            Node t = Q.remove();

            if (cond.contains(t)) {
                continue;
            }

            if (t == to) {
                return false;
            }

            for (Node u : graph.getAdjacentNodes(t)) {
                Edge edge = graph.getEdge(t, u);
                Node c = traverseSemiDirected(t, edge);

                if (c == null) {
                    continue;
                }

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }
        }

        return true;
    }

    /**
     * Reverts the current graph to a special version called CPDAG (completed partially directed acyclic graph).
     *
     * @return a set of nodes that represents the CPDAG version of the graph
     */
    private Set<Node> revertToCPDAG() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getKnowledge());
        rules.setMeekPreventCycles(true);
        rules.setVerbose(verbose);
        return rules.orientImplied(graph);
    }

    /**
     * Builds an indexing for the given list of nodes.
     *
     * @param nodes the list of nodes to be indexed
     */
    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();

        int i = -1;

        for (Node n : nodes) {
            this.hashIndices.put(n, ++i);
        }
    }

    /**
     * Calculates the score of a directed acyclic graph (DAG).
     *
     * @param dag          the DAG to calculate the score for
     * @param recordScores a flag indicating whether to record the scores in the nodes and graph attributes
     * @return the score of the DAG
     */
    private double scoreDag(Graph dag, boolean recordScores) {
        if (score instanceof GraphScore) return 0.0;
        dag = GraphUtils.replaceNodes(dag, getVariables());

        double _score = 0;

        for (Node node : getVariables()) {
            List<Node> x = dag.getParents(node);

            int[] parentIndices = new int[x.size()];

            int count = 0;
            for (Node parent : x) {
                parentIndices[count++] = hashIndices.get(parent);
            }

            final double nodeScore = score.localScore(hashIndices.get(node), parentIndices);

            if (recordScores) {
                node.addAttribute("Score", nodeScore);
            }

            _score += nodeScore;
        }

        if (recordScores) {
            graph.addAttribute("Score", _score);
        }

        return _score;
    }

    /**
     * Calculates the change in score for the graph when a new edge is added between two nodes.
     *
     * @param x           the first node
     * @param y           the second node
     * @param parents     the set of parent nodes for the second node
     * @param hashIndices a mapping of nodes to their corresponding indices
     * @return the change in score for the graph
     * @throws IllegalArgumentException if x and y are the same node, or if y is already a parent of x
     */
    private double scoreGraphChange(Node x, Node y, Set<Node> parents,
                                    Map<Node, Integer> hashIndices) throws InterruptedException {
        int xIndex = hashIndices.get(x);
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

        return score.localScoreDiff(xIndex, yIndex, parentIndices);
    }

    /**
     * Retrieves the list of variables.
     *
     * @return The list of variables.
     */
    private List<Node> getVariables() {
        return variables;
    }

    /**
     * Sets the parallelized flag of the object.
     *
     * @param parallelized the value indicating whether the object should be parallelized
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * Sets the initial graph for the software.
     *
     * @param initialGraph the initial graph to be set
     */
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    /**
     * Sets the number of expansions for a given object.
     *
     * @param numExpansions the number of expansions to set. Must be at least 1.
     * @throws IllegalArgumentException if the number of expansions is less than 1.
     */
    public void setNumExpansions(int numExpansions) {
        if (numExpansions < 1) throw new IllegalArgumentException("Number of expansions must be at least 1.");
        this.numExpansions = numExpansions;
    }

    /**
     * This is an enumeration class that represents different modes. The modes are used for certain functionalities
     * within the application. Each mode has a unique behavior and purpose.
     *
     * <p>The available modes are:</p>
     * <ul>
     *   <li>allowUnfaithfulness - Represents the mode that allows unfaithfulness in calculations.</li>
     *   <li>heuristicSpeedup - Represents the mode that enables a heuristic speedup algorithm.</li>
     *   <li>coverNoncolliders - Represents the mode that covers noncolliders in the calculations.</li>
     * </ul>
     *
     * <p>Usage:</p>
     * <p>
     * Each mode can be used individually or in combination with other modes.
     * To specify a mode, use the syntax {@code Mode.<MODE_NAME>}.
     * The modes can be passed as arguments or used within conditional statements to control the flow of the application.
     * </p>
     *
     * <p>Example Usage:</p>
     * <pre>{@code
     * Mode mode = Mode.allowUnfaithfulness;
     *
     * if (mode == Mode.heuristicSpeedup) {
     *     // Perform heuristic speedup algorithm
     * } else if (mode == Mode.coverNoncolliders) {
     *     // Cover noncolliders in calculations
     * } else {
     *     // Perform default behavior
     * }
     * }</pre>
     */
    private enum Mode {
        allowUnfaithfulness, heuristicSpeedup, coverNoncolliders
    }

    /**
     * This private static class represents the configuration of arrows in a graph. Each object of this class contains
     * the following fields: - T: a set of nodes representing the target nodes of the arrows. - nayx: a set of nodes
     * representing the source nodes of the arrows. - parents: a set of nodes representing the parent nodes of the
     * arrows.
     */
    private static class ArrowConfig {

        private Set<Node> T;
        private Set<Node> nayx;
        private Set<Node> parents;

        public ArrowConfig(Set<Node> T, Set<Node> nayx, Set<Node> parents) {
            this.setT(T);
            this.setNayx(nayx);
            this.setParents(parents);
        }

        public void setT(Set<Node> t) {
            T = t;
        }

        public void setNayx(Set<Node> nayx) {
            this.nayx = nayx;
        }

        public void setParents(Set<Node> parents) {
            this.parents = parents;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrowConfig that = (ArrowConfig) o;
            return T.equals(that.T) && nayx.equals(that.nayx) && parents.equals(that.parents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(T, nayx, parents);
        }
    }

    // Basic data structure for an arrow a->b considered for addition or removal from the graph, together with
    // associated sets needed to make this determination. For both forward and backward direction, NaYX is needed.
    // For the forward direction, TNeighbors neighbors are needed; for the backward direction, H neighbors are needed.
    // See Chickering (2002). The totalScore difference resulting from added in the edge (hypothetically) is recorded
    // as the "bump."
    private static class Arrow implements Comparable<Arrow> {

        private final double bump;
        private final Node a;
        private final Node b;
        private final Set<Node> hOrT;
        private final Set<Node> naYX;
        private final Set<Node> parents;
        private final int index;
        private Set<Node> TNeighbors;

        Arrow(double bump, Node a, Node b, Set<Node> hOrT, Set<Node> capTorH, Set<Node> naYX,
              Set<Node> parents, int index) {
            this.bump = bump;
            this.a = a;
            this.b = b;
            this.setTNeighbors(capTorH);
            this.hOrT = hOrT;
            this.naYX = naYX;
            this.index = index;
            this.parents = parents;
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
        // to zero with an existing element, so for the cases where the comparison is to zero (i.e. have the same
        // bump), we need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables.
        // The fastest way to do this is using a hash code, though it's still possible for two Arrows to have the
        // same hash code but not be equal. If we're paranoid, in this case, we calculate a determinate comparison
        // not equal to zero by keeping a list. This last part is commented out by default.
        public int compareTo(@NotNull Arrow arrow) {

            final int compare = Double.compare(arrow.getBump(), getBump());

            if (compare == 0) {
                return Integer.compare(getIndex(), arrow.getIndex());
            }

            return compare;
        }

        public String toString() {
            return "Arrow<" + a + "->" + b + " bump = " + bump
                   + " t/h = " + hOrT
                   + " TNeighbors = " + getTNeighbors()
                   + " parents = " + parents
                   + " naYX = " + naYX + ">";
        }

        public int getIndex() {
            return index;
        }

        public Set<Node> getTNeighbors() {
            return TNeighbors;
        }

        public void setTNeighbors(Set<Node> TNeighbors) {
            this.TNeighbors = TNeighbors;
        }

        public Set<Node> getParents() {
            return parents;
        }
    }

    private static class EvalPair {
        Set<Node> T;
        double bump;
    }

    class NodeTaskEmptyGraph implements Callable<Boolean> {

        private final int from;
        private final int to;
        private final List<Node> nodes;
        private final Set<Node> emptySet;

        NodeTaskEmptyGraph(int from, int to, List<Node> nodes, Set<Node> emptySet) {
            this.from = from;
            this.to = to;
            this.nodes = nodes;
            this.emptySet = emptySet;
        }

        @Override
        public Boolean call() {
            for (int i = from; i < to; i++) {
                if (Thread.currentThread().isInterrupted()) break;
                if ((i + 1) % 1000 == 0) {
                    count[0] += 1000;
                    out.println("Initializing effect edges: " + (count[0]));
                }

                Node y = nodes.get(i);

                for (int j = i + 1; j < nodes.size() && !Thread.currentThread().isInterrupted(); j++) {
                    Node x = nodes.get(j);

                    if (existsKnowledge()) {
                        if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                            continue;
                        }

                        if (invalidSetByKnowledge(y, emptySet)) {
                            continue;
                        }
                    }

                    if (boundGraph != null && !boundGraph.isAdjacentTo(x, y)) {
                        continue;
                    }

                    int child = hashIndices.get(y);
                    int parent = hashIndices.get(x);
                    double bump = score.localScoreDiff(parent, child);

                    if (symmetricFirstStep) {
                        double bump2 = score.localScoreDiff(child, parent);
                        bump = max(bump, bump2);
                    }

                    if (boundGraph != null && !boundGraph.isAdjacentTo(x, y)) {
                        continue;
                    }

                    if (bump > 0) {
                        effectEdgesGraph.addEdge(Edges.undirectedEdge(x, y));
                        addArrowForward(x, y, emptySet, emptySet, emptySet, emptySet, bump);
                        addArrowForward(y, x, emptySet, emptySet, emptySet, emptySet, bump);
                    }
                }
            }

            return true;
        }
    }
}
