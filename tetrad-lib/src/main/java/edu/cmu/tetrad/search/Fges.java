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
import edu.cmu.tetrad.search.utils.DagScorer;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.Bes;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

import static edu.cmu.tetrad.graph.Edges.directedEdge;
import static org.apache.commons.math3.util.FastMath.max;
import static org.apache.commons.math3.util.FastMath.min;

/**
 * <p>Imlements the Greedy Equivalence Search (GES) algorithm, originally due to
 * Chris Meek but developed significantly by Max Chickering, with some
 * optimizations that allow it to scale accurately to thousands of variables
 * for the sparse case.</p>
 *
 * <p>Specificlly, FGES is an implementation of the GES algorithm as specified
 * in this paper:</p>

 * <p>Chickering (2002) "Optimal structure identification with greedy search"
 * Journal of Machine Learning Research.</p>
 *
 * <p>It works for both BayesNets and SEMs.</p>
 *
 * <p>To speed things up, it has been assumed that variables X and Y with zero
 * correlation do not correspond to edges in the graph. This is a restricted
 * form of the heuristicSpeedup assumption, something GES does not assume. This
 * the graph. This is a restricted form of the heuristicSpeedup assumption,
 * something GES does not assume. This heuristicSpeedup assumption needs to be
 * explicitly turned on using setHeuristicSpeedup(true).</p>
 *
 * <p>A number of other optimizations were also. See code for details.</p>
 *
 * <p>This class is configured to respect knowledge of forbidden and required
 * edges, including knowledge of temporal tiers.</p>
 *
 * @author Ricardo Silva
 * @author josephramsey
 * @see Grasp
 * @see Boss
 * @see Sp
 * @see Knowledge
 */
public final class Fges implements IGraphSearch, DagScorer {
    private final Set<Node> emptySet = new HashSet<>();
    private final int[] count = new int[1];
    private final int depth = 10000;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The top n graphs found by the algorithm, where n is numPatternsToStore.
     */
    private final LinkedList<ScoredGraph> topGraphs = new LinkedList<>();
    // Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
    private final SortedSet<Arrow> sortedArrows = new ConcurrentSkipListSet<>();
    //    private final SortedSet<Arrow> sortedArrowsBack = new ConcurrentSkipListSet<>();
    private final Map<Edge, ArrowConfig> arrowsMap = new ConcurrentHashMap<>();
    //    private final Map<Edge, ArrowConfigBackward> arrowsMapBackward = new ConcurrentHashMap<>();
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
    private boolean meekVerbose = false;
    // Map from variables to their column indices in the data set.
    private ConcurrentMap<Node, Integer> hashIndices;
    // A graph where X--Y means that X and Y have non-zero total effect on one another.
    private Graph effectEdgesGraph;

    // Where printed output is sent.
    private PrintStream out = System.out;

    // The graph being constructed.
    private Graph graph;

    // Arrows with the same totalScore are stored in this list to distinguish their order in sortedArrows.
    // The ordering doesn't matter; it just has to be transitive.
    private int arrowIndex = 0;

    // The score of the model.
    private double modelScore;

    // Internal.
    private Mode mode = Mode.heuristicSpeedup;

    // Bounds the degree of the graph.
    private int maxDegree = -1;

    // True if the first step of adding an edge to an empty graph should be scored in both directions
    // for each edge with the maximum score chosen.
    private boolean symmetricFirstStep = false;

    // True if FGES should run in a single thread, no if parallelized.
    private boolean parallelized = false;

    /**
     * Construct a Score and pass it in here. The totalScore should return a
     * positive value in case of conditional dependence and a negative values in
     * case of conditional independence. See Chickering (2002), locally
     * consistent scoring criterion. This by default uses all the processors on
     * the machine.
     *
     * @param score The score to use. The score should yield better scores for
     *              more correct local models. The algorithm as given by
     *              Chickering assumes the score will be a BIC score of some
     *              sort.
     */
    public Fges(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        setScore(score);
        this.graph = new EdgeListGraph(getVariables());
    }

    //==========================PUBLIC METHODS==========================//

    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    /**
     * Greedy equivalence search: Start from the empty graph, add edges till
     * model is significant. Then start deleting edges till a minimum is
     * achieved.
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

        this.modelScore = scoreDag(GraphSearchUtils.dagFromCPDAG(graph), true);

        return graph;
    }

    /**
     * @return the background knowledge.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required
     *                  edges.
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
     * @return the score of the given DAG, up to a constant.
     */
    public double scoreDag(Graph dag) {
        return scoreDag(dag, false);
    }

    /**
     * Sets the initial graph.
     *
     * @param initialGraph This graph.
     */
    public void setInitialGraph(Graph initialGraph) {
        if (initialGraph == null) {
            this.initialGraph = null;
            return;
        }

        initialGraph = GraphUtils.replaceNodes(initialGraph, variables);

        if (verbose) {
            out.println("External graph variables: " + initialGraph.getNodes());
            out.println("Data set variables: " + variables);
        }

        if (!new HashSet<>(initialGraph.getNodes()).equals(new HashSet<>(variables))) {
            throw new IllegalArgumentException("Variables aren't the same.");
        }

        this.initialGraph = initialGraph;
    }

    /**
     * Sets whether verbose output should be produced. Verbose output generated
     * by the Meek rules is treated separately.
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
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent
     * to. By detault System.out.
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
     * The maximum of parents any nodes can have in output pattern.
     *
     * @return -1 for unlimited.
     */
    public int getMaxDegree() {
        return maxDegree;
    }

    /**
     * The maximum of parents any nodes can have in output pattern.
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
     * Sets whether the first step of the procedure will score both X->Y and Y->X and prefer the
     * higher score (for adding X--Y to the graph).
     *
     * @param symmetricFirstStep True iff the case.
     */
    public void setSymmetricFirstStep(boolean symmetricFirstStep) {
        this.symmetricFirstStep = symmetricFirstStep;
    }

    /**
     * Makes a string for the edge Bayes factors to log.
     *
     * @param dag The DAG to logs the factors for.
     * @return The string to log.
     */
    public String logEdgeBayesFactorsString(Graph dag) {
        Map<Edge, Double> factors = logEdgeBayesFactors(dag);
        return logBayesPosteriorFactorsString(factors);
    }

    /**
     * Returns the score of the final search model.
     *
     * @return This score.
     */
    public double getModelScore() {
        return modelScore;
    }

    //===========================PRIVATE METHODS========================//


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

    //Sets the discrete scoring function to use.
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

    private void initializeEffectEdges(final List<Node> nodes) {
        long start = MillisecondTimes.timeMillis();
        this.effectEdgesGraph = new EdgeListGraph(nodes);

        List<Callable<Boolean>> tasks = new ArrayList<>();

        int chunkSize = getChunkSize(nodes.size());

        for (int i = 0; i < nodes.size() && !Thread.currentThread().isInterrupted(); i += chunkSize) {
            NodeTaskEmptyGraph task = new NodeTaskEmptyGraph(i, min(nodes.size(), i + chunkSize),
                    nodes, emptySet);

            if (!parallelized) {
                task.call();
            } else {
                tasks.add(task);
            }
        }

        if (parallelized) {
            ForkJoinPool.commonPool().invokeAll(tasks);
        }

        long stop = MillisecondTimes.timeMillis();

        if (verbose) {
            out.println("Elapsed initializeForwardEdgesFromEmptyGraph = " + (stop - start) + " ms");
        }
    }

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

            Set<Node> process = revertToCPDAG();

            process.add(x);
            process.add(y);
            process.addAll(getCommonAdjacents(x, y));

            reevaluateForward(new HashSet<>(process));
        }
    }

    private void bes() {
        Bes bes = new Bes(score);
        bes.setDepth(depth);
        bes.setVerbose(verbose);
        bes.setKnowledge(knowledge);
        bes.bes(graph, variables);
    }

    // Returns true if knowledge is not empty.
    private boolean existsKnowledge() {
        return !knowledge.isEmpty();
    }

    // Calcuates new arrows based on changes in the graph for the forward search.
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

        for (int i = 0; i < nodes.size() && !Thread.currentThread().isInterrupted(); i += chunkSize) {
            AdjTask task = new AdjTask(new ArrayList<>(nodes), i, min(nodes.size(), i + chunkSize));

            if (!this.parallelized) {
                task.call();
            } else {
                tasks.add(task);
            }
        }

        if (this.parallelized) {
            ForkJoinPool.commonPool().invokeAll(tasks);
        }
    }

    // Calculates the new arrows for an a->b edge.
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
            List<Future<EvalPair>> futures = ForkJoinPool.commonPool().invokeAll(tasks);

            for (Future<EvalPair> future : futures) {
                try {
                    EvalPair pair = future.get();
                    if (pair.bump > maxBump) {
                        maxT = pair.T;
                        maxBump = pair.bump;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }

        if (maxBump > 0) {
            addArrowForward(a, b, maxT, TNeighborsSet, naYX, parents, maxBump);
        }
    }

    private void addArrowForward(Node a, Node b, Set<Node> hOrT, Set<Node> TNeighbors, Set<Node> naYX,
                                 Set<Node> parents, double bump) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, TNeighbors, naYX, parents, arrowIndex++);
        sortedArrows.add(arrow);
//        System.out.println(arrow);
    }

    private Set<Node> getCommonAdjacents(Node x, Node y) {
        Set<Node> adj = new HashSet<>(graph.getAdjacentNodes(x));
        adj.retainAll(graph.getAdjacentNodes(y));
        return adj;
    }

    // Get all adj that are connected to Y by an undirected edge and not adjacent to X.
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

    // Evaluate the Insert(X, Y, TNeighbors) operator (Definition 12 from Chickering, 2002).
    private double insertEval(Node x, Node y, Set<Node> T, Set<Node> naYX, Set<Node> parents,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set = new HashSet<>(naYX);
        set.addAll(T);
        set.addAll(parents);

        return scoreGraphChange(x, y, set, hashIndices);
    }

    // Do an actual insertion. (Definition 12 from Chickering, 2002).
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
            TetradLogger.getInstance().forceLogMessage(message);
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

        return isClique(union) && semidirectedPathCondition(y, x, union)
                && !violatesKnowledge;
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
                    TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB));
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

    // Returns true iff every semidirected path contains an element of cond.
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

    // Runs Meek rules on just the changed adj.
    private Set<Node> revertToCPDAG() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(getKnowledge());
        rules.setAggressivelyPreventCycles(true);
        rules.setVerbose(meekVerbose);
        return rules.orientImplied(graph);
    }

    // Maps adj to their indices for quick lookup.
    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();

        int i = -1;

        for (Node n : nodes) {
            this.hashIndices.put(n, ++i);
        }
    }

    private double scoreDag(Graph dag, boolean recordScores) {
        if (score instanceof GraphScore) return 0.0;
        dag = GraphUtils.replaceNodes(dag, getVariables());

        // We will switch to using the score of the FGES search.
//        Score score = this.score.defaultScore();

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

    private double scoreGraphChange(Node x, Node y, Set<Node> parents,
                                    Map<Node, Integer> hashIndices) {
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

    private List<Node> getVariables() {
        return variables;
    }

    private Map<Edge, Double> logEdgeBayesFactors(Graph dag) {
        Map<Edge, Double> logBayesFactors = new HashMap<>();
        double withEdge = scoreDag(dag);

        for (Edge edge : dag.getEdges()) {
            dag.removeEdge(edge);
            double withoutEdge = scoreDag(dag);
            double difference = withEdge - withoutEdge;
            logBayesFactors.put(edge, difference);
            dag.addEdge(edge);
        }

        return logBayesFactors;
    }

    private String logBayesPosteriorFactorsString(final Map<Edge, Double> factors) {
        NumberFormat nf = new DecimalFormat("0.00");
        StringBuilder builder = new StringBuilder();

        List<Edge> edges = new ArrayList<>(factors.keySet());

        edges.sort((o1, o2) -> -Double.compare(factors.get(o1), factors.get(o2)));

        builder.append("Edge Posterior Log Bayes Factors:\n\n");

        builder.append("For a DAG in the IMaGES pattern with model totalScore m, for each edge e in the "
                + "DAG, the model totalScore that would result from removing each edge, calculating "
                + "the resulting model totalScore m(e), and then reporting m - m(e). The totalScore used is "
                + "the IMScore, L - SUM_i{kc ln n(i)}, L is the maximum likelihood of the model, "
                + "k isthe number of parameters of the model, n(i) is the sample size of the ith "
                + "data set, and c is the penalty penaltyDiscount. Note that the more negative the totalScore, "
                + "the more important the edge is to the posterior probability of the IMaGES model. "
                + "Edges are given in order of their importance so measured.\n\n");

        int i = 0;

        for (Edge edge : edges) {
            builder.append(++i).append(". ").append(edge).append(" ").append(nf.format(factors.get(edge))).append("\n");
        }

        return builder.toString();
    }

    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    //===========================SCORING METHODS===================//

    /**
     * Internal.
     */
    private enum Mode {
        allowUnfaithfulness, heuristicSpeedup, coverNoncolliders
    }

    private static class ArrowConfig {
        private Set<Node> T;
        private Set<Node> nayx;
        private Set<Node> parents;

        public ArrowConfig(Set<Node> T, Set<Node> nayx, Set<Node> parents) {
            this.setT(T);
            this.setNayx(nayx);
            this.setParents(parents);
        }

        public Set<Node> getT() {
            return T;
        }

        public void setT(Set<Node> t) {
            T = t;
        }

        public void setNayx(Set<Node> nayx) {
            this.nayx = nayx;
        }

        public Set<Node> getParents() {
            return parents;
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
    // as the "bump".
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
        // bump, we need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables.
        // The fastest way to do this is using a hash code, though it's still possible for two Arrows to have the
        // same hash code but not be equal. If we're paranoid, in this case we calculate a determinate comparison
        // not equal to zero by keeping a list. This last part is commened out by default.
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
                if (Thread.interrupted()) break;
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
