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
 * A number of other optimizations were also. See code for de tails.
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
public final class Fges implements IGraphSearch, DagScorer {
    /**
     * Used to find semidirected paths for cycle checking.
     */
    private final Set<Node> emptySet = new HashSet<>();
    /**
     * Used to find semidirected paths for cycle checking.
     */
    private final int[] count = new int[1];
    /**
     * Used to find semidirected paths for cycle checking.
     */
    private final int depth = 10000;
    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The top n graphs found by the algorithm, where n is numPatternsToStore.
     */
    private final LinkedList<ScoredGraph> topGraphs = new LinkedList<>();
    /**
     * Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
     */
    private final SortedSet<Arrow> sortedArrows = new ConcurrentSkipListSet<>();
    /**
     * Map from edges to arrows.
     */
    private final Map<Edge, ArrowConfig> arrowsMap = new ConcurrentHashMap<>();
    /**
     * The fork join pool.
     */
    private ForkJoinPool pool;
    /**
     * Whether one-edge faithfulness should be assumed.
     */
    private boolean faithfulnessAssumed = false;
    /**
     * Specification of forbidden and required edges.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * List of variables in the data set, in order.
     */
    private List<Node> variables;
    /**
     * An initial graph to start from.
     */
    private Graph initialGraph;
    /**
     * If non-null, edges not adjacent in this graph will not be added.
     */
    private Graph boundGraph = null;
    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;
    /**
     * The totalScore for discrete searches.
     */
    private Score score;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose = false;
    /**
     * Whether Meek rules should be verbose.
     */
    private boolean meekVerbose = false;
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
     * Internal.
     */
    private Mode mode = Mode.heuristicSpeedup;
    /**
     * Bounds the degree of the graph.
     */
    private int maxDegree = -1;
    /**
     * True if the first step of adding an edge to an empty graph should be scored in both directions for each edge with
     * the maximum score chosen.
     */
    private boolean symmetricFirstStep = false;
    /**
     * The number of threads to use to run the algorithm.
     */
    private int numThreads = 1;

    /**
     * Constructor. Construct a Score and pass it in here. The totalScore should return a positive value in case of
     * conditional dependence and a negative values in case of conditional independence. See Chickering (2002), locally
     * consistent scoring criterion. This by default uses all the processors on the machine.
     *
     * @param score The score to use. The score should yield better scores for more correct local models. The algorithm
     *              as given by Chickering assumes the score will be a BIC score of some sort.
     */
    public Fges(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        setScore(score);
        this.graph = new EdgeListGraph(getVariables());
        this.pool = new ForkJoinPool(numThreads);
    }

    /**
     * Used to find semidirected paths for cycle checking.
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
     * Greedy equivalence search: Start from the empty graph, add edges till the model is significant. Then start
     * deleting edges till a minimum is achieved.
     *
     * @return the resulting Pattern.
     */
    public Graph search() {
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

        addRequiredEdges(graph);

        initializeEffectEdges(getVariables());

        this.mode = Mode.heuristicSpeedup;
        fes();
        bes();

        this.mode = Mode.coverNoncolliders;
        fes();
        bes();

        if (!faithfulnessAssumed) {
            this.mode = Mode.allowUnfaithfulness;
            fes();
            bes();
        }

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - start;

        if (verbose) {
            this.logger.forceLogMessage("Elapsed time = " + (elapsedTime) / 1000. + " s");
        }

        this.modelScore = scoreDag(GraphTransforms.dagFromCpdag(graph, null), true);

        return graph;
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
     * Scores a Directed Acyclic Graph (DAG) based on its structure.
     *
     * @param dag The input DAG to be scored.
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
     * @see #setMeekVerbose(boolean)
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets whether verbose output should be produced for the Meek rules.
     *
     * @param meekVerbose True iff the case.
     */
    public void setMeekVerbose(boolean meekVerbose) {
        this.meekVerbose = meekVerbose;
    }

    /**
     * Returns the output stream used for printing.
     *
     * @return the output stream used for printing.
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
     * Sets the discrete scoring function to use.
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

    private int getChunkSize(int n) {
        int chunk = n / Runtime.getRuntime().availableProcessors();
        if (chunk < 100) chunk = 100;
        return chunk;
    }

    /**
     * Initializes the effectEdgesGraph using the given list of nodes.
     *
     * @param nodes The list of nodes.
     */
    private void initializeEffectEdges(final List<Node> nodes) {
        long start = MillisecondTimes.timeMillis();
        this.effectEdgesGraph = new EdgeListGraph(nodes);

        List<Callable<Boolean>> tasks = new ArrayList<>();

        int chunkSize = getChunkSize(nodes.size());

        for (int i = 0; i < nodes.size(); i += chunkSize) {
            if (Thread.currentThread().isInterrupted()) {
                this.pool.shutdownNow();
                throw new RuntimeException("Interrupted");
//                break;
            }

            NodeTaskEmptyGraph task = new NodeTaskEmptyGraph(i, min(nodes.size(), i + chunkSize), nodes, emptySet);
            tasks.add(task);
        }

        try {
            pool.invokeAll(tasks);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        long stop = MillisecondTimes.timeMillis();

        if (verbose) {
            out.println("Elapsed initializeForwardEdgesFromEmptyGraph = " + (stop - start) + " ms");
        }
    }

    /**
     * Performs the Forward Equivalent Search (FES) algorithm.
     * <p>
     * This algorithm searches for causal relationships in a directed acyclic graph (DAG) by iteratively examining each
     * arrow and determining if it satisfies the FES conditions.
     * <p>
     * The FES algorithm follows these steps: - Re-evaluate the forward relationships of all variables in the graph -
     * While there are still arrows to examine: - Get the first arrow from the sorted arrows list - Remove the arrow
     * from the sorted arrows list - Retrieve the source (A) and target (B) nodes of the arrow - If A and B are already
     * adjacent, skip this arrow and move to the next one - If the degree of A or B exceeds the maximum degree allowed,
     * skip this arrow - If the N_A_Y_X value of A and B differs from the arrow's N_A_Y_X value, skip this arrow - If
     * the set of T-neighbors of A and B differs from the arrow's T-neighbors value, skip this arrow - If the set of
     * parents of B differs from the arrow's parents value, skip this arrow - If the insertion of the arrow creates a
     * cycle or violates the FES conditions, skip this arrow - Insert the arrow into the graph with the appropriate bump
     * value - Revert the graph to complete partial DAG (CPDAG) - Recalculate the forward relationships of the nodes
     * involved in the insertion
     */
    private void fes() {
        int maxDegree = this.maxDegree == -1 ? 1000 : this.maxDegree;

        reevaluateForward(new HashSet<>(variables));

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

            Set<Node> process = revertToCpdag();

            process.add(x);
            process.add(y);
            process.addAll(getCommonAdjacents(x, y));

            reevaluateForward(new HashSet<>(process));
        }
    }

    /**
     * Runs the Bes algorithm.
     *
     * @see Bes
     */
    private void bes() {
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
     * Reevaluates the forward direction of arrows for a set of nodes.
     *
     * @param nodes the set of nodes for which to reevaluate arrows
     */
    private void reevaluateForward(final Set<Node> nodes) {
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
            public Boolean call() {
                for (int _y = from; _y < to; _y++) {
                    if (Thread.interrupted()) break;

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

        for (int i = 0; i < nodes.size(); i += chunkSize) {
            if (Thread.currentThread().isInterrupted()) {
                pool.shutdownNow();
                throw new RuntimeException("Interrupted");
            }

            AdjTask task = new AdjTask(new ArrayList<>(nodes), i, min(nodes.size(), i + chunkSize));
            tasks.add(task);
        }

        try {
            pool.invokeAll(tasks);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /**
     * Calculates and adds forward arrows from node a to node b based on the given conditions.
     *
     * @param a the starting node
     * @param b the ending node
     */
    private void calculateArrowsForward(Node a, Node b) {
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
            public EvalPair call() {
                for (int k = from; k < to; k++) {
                    if (Thread.interrupted()) break;
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

        for (int i = 0; i < TT.size(); i += chunkSize) {
            if (Thread.currentThread().isInterrupted()) {
                pool.shutdownNow();
//                break;
                throw new RuntimeException("Interrupted");
            }

            EvalTask task = new EvalTask(TT, i, min(TT.size(), i + chunkSize), hashIndices);
            tasks.add(task);
        }

        List<Future<EvalPair>> futures = pool.invokeAll(tasks);

        for (Future<EvalPair> future : futures) {
            try {
                EvalPair pair = future.get();
                if (pair.bump > maxBump) {
                    maxT = pair.T;
                    maxBump = pair.bump;
                }
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                TetradLogger.getInstance().forceLogMessage(e.getMessage());
                return;
            }
        }

        if (maxBump > 0) {
            addArrowForward(a, b, maxT, TNeighborsSet, naYX, parents, maxBump);
        }
    }

    /**
     * Adds an arrow connecting two nodes in the specified direction.
     *
     * @param a          the starting node of the arrow
     * @param b          the ending node of the arrow
     * @param hOrT       the set of nodes representing heads or tails
     * @param TNeighbors the set of nodes representing tail neighbors
     * @param naYX       the set of nodes representing NA and YX
     * @param parents    the set of parent nodes
     * @param bump       the bump value of the arrow
     */
    private void addArrowForward(Node a, Node b, Set<Node> hOrT, Set<Node> TNeighbors, Set<Node> naYX, Set<Node> parents, double bump) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, TNeighbors, naYX, parents, arrowIndex++);
        sortedArrows.add(arrow);
    }

    /**
     * Returns a set of common adjacent nodes between two given nodes.
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
     * @param x the first node
     * @param y the second node
     * @return a list of T-neighbors of the two nodes
     */
    private List<Node> getTNeighbors(Node x, Node y) {
        List<Edge> yEdges = graph.getEdges(y);
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
     * @param x           The starting node of the edge.
     * @param y           The ending node of the edge.
     * @param T           The set of nodes representing the graph.
     * @param naYX        The set of nodes not adjacent to 'y' but adjacent to 'x'.
     * @param parents     The set of parent nodes of 'x'.
     * @param hashIndices The map of nodes to their corresponding indices.
     * @return The evaluation score after inserting the edge between 'x' and 'y'.
     */
    private double insertEval(Node x, Node y, Set<Node> T, Set<Node> naYX, Set<Node> parents, Map<Node, Integer> hashIndices) {
        Set<Node> set = new HashSet<>(naYX);
        set.addAll(T);
        set.addAll(parents);

        return scoreGraphChange(x, y, set, hashIndices);
    }

    /**
     * Do an actual insertion. (Definition 12 from Chickering, 2002).
     *
     * @param x    the source node
     * @param y    the target node
     * @param T    a set of nodes to be updated
     * @param bump a value used for updating the graph
     */
    private void insert(Node x, Node y, Set<Node> T, double bump) {
        graph.addDirectedEdge(x, y);

        int numEdges = graph.getNumEdges();

        if (numEdges % 1000 == 0) {
            out.println("Num edges added: " + numEdges);
        }

        if (verbose) {
            int cond = T.size() + getNaYX(x, y).size() + graph.getParents(y).size();

            if (verbose) {
                final String message = graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y) + " " + T + " " + bump + " degree = " + GraphUtils.getDegree(graph) + " indegree = " + GraphUtils.getIndegree(graph) + " cond = " + cond;
                TetradLogger.getInstance().forceLogMessage(message);
            }
        }

        for (Node _t : T) {
            graph.removeEdge(_t, y);
            graph.addDirectedEdge(_t, y);

            if (verbose) {
                String message = "--- Directing " + graph.getEdge(_t, y);
                TetradLogger.getInstance().forceLogMessage(message);
            }
        }
    }

    /**
     * Checks if inserting nodes into a graph conforms to certain conditions. (Theorem 15 from Chickering, 2002).
     *
     * @param x    the first node to be inserted
     * @param y    the second node to be inserted
     * @param T    the set of existing nodes
     * @param naYX the set of nodes not adjacent to x or y
     * @return true if inserting the nodes satisfies the conditions, false otherwise
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

        return isClique(union) && semidirectedPathCondition(y, x, union) && !violatesKnowledge;
    }

    /**
     * Adds the required edges to the provided graph based on the existing knowledge.
     *
     * @param graph the graph to which the required edges will be added
     */
    private void addRequiredEdges(Graph graph) {
        if (!existsKnowledge()) {
            return;
        }

        for (Iterator<KnowledgeEdge> it = getKnowledge().requiredEdgesIterator(); it.hasNext(); ) {
            if (Thread.currentThread().isInterrupted()) {
                pool.shutdownNow();
//                break;
                throw new RuntimeException("Interrupted");
            }

            KnowledgeEdge next = it.next();

            Node nodeA = graph.getNode(next.getFrom());
            Node nodeB = graph.getNode(next.getTo());

            if (!graph.paths().isAncestorOf(nodeB, nodeA)) {
                graph.removeEdges(nodeA, nodeB);
                graph.addDirectedEdge(nodeA, nodeB);

                if (verbose) {
                    TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB));
                }
            }
        }
        for (Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                pool.shutdownNow();
                throw new RuntimeException("Interrupted");
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
                            TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }

                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.paths().isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (verbose) {
                            TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
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
                            TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }
                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.paths().isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (verbose) {
                            TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if at least one node in the given subset is forbidden by knowledge.
     *
     * @param y      The node for which to check if it is forbidden by knowledge.
     * @param subset The set of nodes to check against knowledge.
     * @return True if at least one node in the subset is forbidden by knowledge, otherwise false.
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
     * Retrieves the set of nodes that are adjacent to node y and are also adjacent to node x.
     *
     * @param x the first node
     * @param y the second node
     * @return a set of nodes that are neighbors of both x and y
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
     * Checks if the given set of nodes forms a clique.
     *
     * @param nodes the set of nodes to check
     * @return true if the given set of nodes forms a clique, false otherwise
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
     * Determines whether there exists a semi-directed path from a given source node to a target node that satisfies a
     * set of conditions.
     *
     * @param from the source node
     * @param to   the target node
     * @param cond the set of conditions
     * @return {@code true} if a semi-directed path from the source node to the target node exists that satisfies the
     * conditions; {@code false} otherwise
     * @throws IllegalArgumentException if the source node and the target node are the same
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
     * Reverts the graph to a completed partially directed acyclic graph (CPDAG) using Meek rules.
     *
     * @return the set of nodes in the CPDAG
     */
    private Set<Node> revertToCpdag() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getKnowledge());
        rules.setMeekPreventCycles(true);
        rules.setVerbose(meekVerbose);
        return rules.orientImplied(graph);
    }

    /**
     * Builds indexing for the given list of nodes.
     *
     * @param nodes the list of nodes to build indexing for
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
     * @param dag          The DAG to be scored.
     * @param recordScores Indicates whether or not to record the scores for each node in the graph.
     * @return The total score of the DAG.
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
     * Calculates the score graph change between two nodes.
     *
     * @param x           The first node.
     * @param y           The second node.
     * @param parents     The set of parent nodes.
     * @param hashIndices A mapping of nodes to their corresponding indices.
     * @return The score graph change between the two nodes.
     * @throws IllegalArgumentException If x is the same as y or y is one of x's parents.
     */
    private double scoreGraphChange(Node x, Node y, Set<Node> parents, Map<Node, Integer> hashIndices) {
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
     * Retrieves a list of variables.
     *
     * @return the list of variables
     */
    private List<Node> getVariables() {
        return variables;
    }

    /**
     * Sets the number of threads to use. By default, the number of threads is 1.
     *
     * @param numThreads The number of threads to use. Must be at least 1.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) throw new IllegalArgumentException("numThreads must be at least 1.");
        this.numThreads = numThreads;
        this.pool = new ForkJoinPool(numThreads);
    }

    /**
     * Sets the initial graph for the application.
     *
     * @param initialGraph the initial graph to be set
     */
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    /**
     * Enumeration representing the different modes for the Mode class.
     * <p>
     * This enumeration defines the different modes that can be used in the Mode class. The available modes are: -
     * allowUnfaithfulness: This mode allows unfaithfulness in the results. - heuristicSpeedup: This mode applies a
     * heuristic speedup strategy. - coverNoncolliders: This mode includes noncolliders in the coverage.
     */
    private enum Mode {
        allowUnfaithfulness, heuristicSpeedup, coverNoncolliders
    }

    /**
     * This class represents the configuration of arrows.
     */
    private static class ArrowConfig {

        /**
         * Sets the T set.
         *
         * @param t the set of Node objects for the T set.
         */
        private Set<Node> T;

        /**
         * This variable represents a set of nodes. It is used in the ArrowConfig class to store the nayx set.
         *
         * @see ArrowConfig
         */
        private Set<Node> nayx;

        /**
         * This variable represents a set of parent nodes.
         */
        private Set<Node> parents;

        /**
         * Constructs a new ArrowConfig with the specified sets of nodes.
         *
         * @param T       The set of T nodes.
         * @param nayx    The set of nayx nodes.
         * @param parents The set of parent nodes.
         */
        public ArrowConfig(Set<Node> T, Set<Node> nayx, Set<Node> parents) {
            this.setT(T);
            this.setNayx(nayx);
            this.setParents(parents);
        }

        /**
         * Sets the T set.
         *
         * @param t the set of Node objects for the T set.
         */
        public void setT(Set<Node> t) {
            T = t;
        }

        /**
         * Sets the nayx set.
         *
         * @param nayx the set of Node objects for the nayx set
         */
        public void setNayx(Set<Node> nayx) {
            this.nayx = nayx;
        }

        /**
         * Sets the parents of this node.
         *
         * @param parents the set of Node objects to set as parents
         */
        public void setParents(Set<Node> parents) {
            this.parents = parents;
        }

        /**
         * Compares this ArrowConfig object with the specified object for equality.
         *
         * @param o the object to be compared for equality with this ArrowConfig
         * @return true if the specified object is equal to this ArrowConfig, false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrowConfig that = (ArrowConfig) o;
            return T.equals(that.T) && nayx.equals(that.nayx) && parents.equals(that.parents);
        }

        /**
         * Returns the hash code value for this ArrowConfig object. The hashCode is calculated by computing a hash code
         * for each of the T, nayx, and parents sets using the Objects .hash() method.
         *
         * @return the computed hash code value for this ArrowConfig object
         * @see Objects#hash(Object...)
         */
        @Override
        public int hashCode() {
            return Objects.hash(T, nayx, parents);
        }
    }

    /**
     * Basic data structure for an arrow a->b considered for addition or removal from the graph, together with
     * associated sets needed to make this determination. For both forward and backward direction, NaYX is needed. For
     * the forward direction, TNeighbors neighbors are needed; for the backward direction, H neighbors are needed. See
     * Chickering (2002). The totalScore difference resulting from added in the edge (hypothetically) is recorded as the
     * "bump."
     */
    private static class Arrow implements Comparable<Arrow> {

        /**
         * Represents the bump value for an Arrow object. The bump value determines the ordering of Arrow objects in a
         * SortedSet. A higher bump value indicates a higher priority.
         *
         * @see Arrow
         */
        private final double bump;

        /**
         * Represents a for a-$gt;b.
         *
         * @see Arrow
         * @see Node
         */
        private final Node a;

        /**
         * Represents b for a-&gt;b.
         */
        private final Node b;

        /**
         * Represents a T set or an H set, depending on whehter we are running the forward or the backward search.
         */
        private final Set<Node> hOrT;

        /**
         * Represents the NaYX set.
         */
        private final Set<Node> naYX;

        /**
         * Represents the parents.
         */
        private final Set<Node> parents;

        /**
         * A unique index.
         */
        private final int index;

        /**
         * Represents the T-neighbors
         */
        private Set<Node> TNeighbors;

        /**
         * Constructs a new instance of the Arrow class with the given parameters.
         *
         * @param bump    The bump value of the arrow.
         * @param a       The first node of the arrow.
         * @param b       The second node of the arrow.
         * @param hOrT    The set of nodes representing H or T.
         * @param capTorH The set of nodes representing Cap or H.
         * @param naYX    The set of nodes representing Na or YX.
         * @param parents The set of parent nodes.
         * @param index   The index of the arrow.
         */
        Arrow(double bump, Node a, Node b, Set<Node> hOrT, Set<Node> capTorH, Set<Node> naYX, Set<Node> parents, int index) {
            this.bump = bump;
            this.a = a;
            this.b = b;
            this.setTNeighbors(capTorH);
            this.hOrT = hOrT;
            this.naYX = naYX;
            this.index = index;
            this.parents = parents;
        }

        /**
         * Retrieves the bump value of the Arrow.
         *
         * @return The bump value of the Arrow.
         */
        public double getBump() {
            return bump;
        }

        /**
         * Retrieves the first node of the arrow.
         *
         * @return the first node of the arrow.
         */
        public Node getA() {
            return a;
        }

        /**
         * Retrieves the second node of the arrow.
         *
         * @return the second node of the arrow.
         */
        public Node getB() {
            return b;
        }

        /**
         * Retrieves the set of nodes representing H or T.
         *
         * @return The set of nodes representing H or T.
         */
        Set<Node> getHOrT() {
            return hOrT;
        }

        /**
         * Retrieves the neighbors of b that are adjacent to a.
         *
         * @return The set of nodes representing NaYX.
         */
        Set<Node> getNaYX() {
            return naYX;
        }

        /**
         * Sorting by bump, high to low. The problem is the SortedSet contains won't add a new element if it compares to
         * zero with an existing element, so for the cases where the comparison is to zero (i.e. have the same bump), we
         * need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables. The
         * fastest way to do this is using a hash code, though it's still possible for two Arrows to have the same hash
         * code but not be equal. If we're paranoid, in this case, we calculate a determinate comparison not equal to
         * zero by keeping a list. This last part is commented out by default.
         */
        public int compareTo(@NotNull Arrow arrow) {

            final int compare = Double.compare(arrow.getBump(), getBump());

            if (compare == 0) {
                return Integer.compare(getIndex(), arrow.getIndex());
            }

            return compare;
        }

        /**
         * Returns a string representation of this Arrow object.
         *
         * @return a string representation of this Arrow object
         */
        public String toString() {
            return "Arrow<" + a + "->" + b + " bump = " + bump + " t/h = " + hOrT + " TNeighbors = " + getTNeighbors() + " parents = " + parents + " naYX = " + naYX + ">";
        }

        /**
         * Retrieves the index of the Arrow.
         *
         * @return The index of the Arrow.
         */
        public int getIndex() {
            return index;
        }

        /**
         * Retrieves the set of neighboring nodes of the Arrow that are represented as T.
         *
         * @return The set of neighboring nodes represented as T.
         */
        public Set<Node> getTNeighbors() {
            return TNeighbors;
        }

        /**
         * Sets the neighbors of b that are not adjacent to a.
         *
         * @param TNeighbors The set of neighboring nodes represented as T.
         */
        public void setTNeighbors(Set<Node> TNeighbors) {
            this.TNeighbors = TNeighbors;
        }

        /**
         * Retrieves the set of parent nodes.
         *
         * @return The set of parent nodes.
         */
        public Set<Node> getParents() {
            return parents;
        }
    }

    /**
     * Represents a pair of evaluation values.
     * <p>
     * This class is used to store information about a set of nodes (T) and a bump value (bump). It is a private static
     * class, meaning it is accessible only within the enclosing class.
     */
    private static class EvalPair {
        Set<Node> T;
        double bump;
    }

    /**
     * A class representing a task for finding empty graphs in a given node list.
     */
    class NodeTaskEmptyGraph implements Callable<Boolean> {

        private final int from;
        private final int to;
        private final List<Node> nodes;
        private final Set<Node> emptySet;

        /**
         * A class representing a task for finding empty graphs in a given node list.
         */
        NodeTaskEmptyGraph(int from, int to, List<Node> nodes, Set<Node> emptySet) {
            this.from = from;
            this.to = to;
            this.nodes = nodes;
            this.emptySet = emptySet;
        }

        /**
         * Executes the task for finding empty graphs in a given node list.
         *
         * @return true if the task completes successfully, false otherwise.
         */
        @Override
        public Boolean call() {
            for (int i = from; i < to; i++) {
                if (Thread.interrupted()) break;
                if ((i + 1) % 1000 == 0) {
                    count[0] += 1000;
                    out.println("Initializing effect edges: " + (count[0]));
                }

                Node y = nodes.get(i);

                for (int j = i + 1; j < nodes.size(); j++) {
                    if (Thread.interrupted()) {
                        pool.shutdownNow();
                        throw new RuntimeException("Interrupted");
                    }

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
