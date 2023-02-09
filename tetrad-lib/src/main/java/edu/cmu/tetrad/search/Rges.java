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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;

import static edu.cmu.tetrad.graph.Edges.directedEdge;
import static edu.cmu.tetrad.util.RandomUtil.shuffle;
import static org.apache.commons.math3.util.FastMath.min;

/**
 * GesSearch is an implementation of the GES algorithm, as specified in
 * Chickering (2002) "Optimal structure identification with greedy search"
 * Journal of Machine Learning Research. It works for both BayesNets and SEMs.
 * <p>
 * Some code optimization could be done for the scoring part of the graph for
 * discrete models (method scoreGraphChange). Some of Andrew Moore's approaches
 * for caching sufficient statistics, for instance.
 * <p>
 * To speed things up, it has been assumed that variables X and Y with zero
 * correlation do not correspond to edges in the graph. This is a restricted
 * form of the heuristicSpeedup assumption, something GES does not assume. This
 * the graph. This is a restricted form of the heuristicSpeedup assumption,
 * something GES does not assume. This heuristicSpeedup assumption needs to be
 * explicitly turned on using setHeuristicSpeedup(true).
 * <p>
 * A number of other optimizations were added 5/2015. See code for details.
 *
 * @author Ricardo Silva, Summer 2003
 * @author Joseph Ramsey, Revisions 5/2015
 */
public final class Rges implements GraphSearch, GraphScorer {

    final Set<Node> emptySet = new HashSet<>();
    final int[] count = new int[1];
    private final int depth = 10000;
    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    // Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
    private SortedSet<Arrow> sortedArrows = new ConcurrentSkipListSet<>();
    private final Map<Edge, ArrowConfig> arrowsMap = new ConcurrentHashMap<>();
    /**
     * List of variables in the data set, in order.
     */
    private List<Node> variables;
    /**
     * An initial graph to start from.
     */
    private Graph initialGraph;
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
    private final MeekRules rules;
    private boolean verbose = false;
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

    // Internal.
    private Mode mode = Mode.heuristicSpeedup;

    // Bounds the degree of the graph.
    private int maxDegree = -1;

    // True if FGES should run in a single thread, no if parallelized.
    private boolean parallelized = false;

    /**
     * Construct a Score and pass it in here. The totalScore should return a
     * positive value in case of conditional dependence and a negative values in
     * case of conditional independence. See Chickering (2002), locally
     * consistent scoring criterion. This by default uses all the processors on
     * the machine.
     */
    public Rges(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        setScore(score);
        this.graph = new EdgeListGraph(getVariables());

        this.rules = new MeekRules();
        rules.setAggressivelyPreventCycles(true);
        rules.setVerbose(false);

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

    //==========================PUBLIC METHODS==========================//

    /**
     * Greedy equivalence search: Start from the empty graph, add edges till
     * model is significant. Then start deleting edges till a minimum is
     * achieved.
     *
     * @return the resulting Pattern.
     */
    public Graph search() {
        long start =  MillisecondTimes.timeMillis();

        graph = new EdgeListGraph(getVariables());

        if (initialGraph != null) {
            graph = new EdgeListGraph(initialGraph);
            graph = GraphUtils.replaceNodes(graph, getVariables());
        }

        initializeEffectEdges(getVariables());

        this.mode = Mode.heuristicSpeedup;
        fes(true);
        bes();

        this.mode = Mode.coverNoncolliders;
        fes(true);
        bes();

        this.mode = Mode.allowUnfaithfulness;
        fes(true);
        bes();

        Graph g0 = new EdgeListGraph(graph);
        double s0 = scoreDag(SearchGraphUtils.dagFromCPDAG(graph), false);

        boolean flag = true;

        while (flag) {
            if (Thread.interrupted()) break;

            reevaluateForward(new HashSet<>(variables));
            SortedSet<Arrow> initialArrows = new ConcurrentSkipListSet<>(this.sortedArrows);

            flag = false;
            List<Edge> edges = new ArrayList<>(g0.getEdges());
            shuffle(edges);
            Iterator<Edge> edgeItr = edges.iterator();

            int count = 1;

            while (!flag && edgeItr.hasNext()) {

                getOut().println(count++ + " / " + g0.getNumEdges());

                Edge edge = edgeItr.next();
                if (!edge.isDirected()) continue;

                graph = new EdgeListGraph(g0);
                sortedArrows = new ConcurrentSkipListSet<>(initialArrows);
                Node a = Edges.getDirectedEdgeHead(edge);
                Node b = Edges.getDirectedEdgeTail(edge);

                for (Node c : graph.getAdjacentNodes(b)) {
                    if (c == a || graph.paths().existsSemidirectedPath(c, a)) {
                        graph.removeEdge(graph.getEdge(b, c));
                        graph.addDirectedEdge(c, b);
                    }
                }

                Set<Node> process = revertToCPDAG();

                process.add(b);
                process.addAll(graph.getAdjacentNodes(b));
                reevaluateForward(new HashSet<>(process));

                this.mode = Mode.heuristicSpeedup;
                fes(false);
                bes();

                this.mode = Mode.coverNoncolliders;
                fes(true);
                bes();

                this.mode = Mode.allowUnfaithfulness;
                fes(true);
                bes();

                double s1 = scoreDag(SearchGraphUtils.dagFromCPDAG(graph), false);
                if (s1 <= s0) continue;

                flag = true;
                g0 = new EdgeListGraph(graph);
                s0 = s1;
//                getOut().println(g0.getNumEdges());
            }
        }

        this.graph = g0;

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - start;

        if (verbose) {
            this.logger.forceLogMessage("Elapsed time = " + (elapsedTime) / 1000. + " s");
        }

        scoreDag(SearchGraphUtils.dagFromCPDAG(graph), true);

        return graph;
    }


    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * @return the totalScore of the given DAG, up to a constant.
     */
    public double scoreDag(Graph dag) {
        return scoreDag(dag, false);
    }

    /**
     * Sets the initial graph.
     */
    public void setExternalGraph(Graph externalGraph) {
        externalGraph = GraphUtils.replaceNodes(externalGraph, variables);

        if (verbose) {
            out.println("External graph variables: " + externalGraph.getNodes());
            out.println("Data set variables: " + variables);
        }

        if (!new HashSet<>(externalGraph.getNodes()).equals(new HashSet<>(variables))) {
            throw new IllegalArgumentException("Variables aren't the same.");
        }

        this.initialGraph = externalGraph;
    }

    /**
     * Sets whether verbose output should be produced.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
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
     */
    public void setOut(PrintStream out) {
        this.out = out;
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


    //===========================PRIVATE METHODS========================//

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
        long start =  MillisecondTimes.timeMillis();
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

        long stop =  MillisecondTimes.timeMillis();

        if (verbose) {
            out.println("Elapsed initializeForwardEdgesFromEmptyGraph = " + (stop - start) + " ms");
        }
    }

    private void fes(boolean reevaluate) {
        int maxDegree = this.maxDegree == -1 ? 1000 : this.maxDegree;

        if (reevaluate) reevaluateForward(new HashSet<>(variables));

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
        bes.bes(graph, variables);
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

        if (a == b) return;

        if (graph.isAdjacentTo(a, b)) return;

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

        Set<Node> union = new HashSet<>(T);
        union.addAll(naYX);

        return isClique(union) && semidirectedPathCondition(y, x, union)
                && !violatesKnowledge;
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
        return this.rules.orientImplied(graph);
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

        Score score = this.score.defaultScore();

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
            graph.addAttribute("BIC", _score);
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

                    int child = hashIndices.get(y);
                    int parent = hashIndices.get(x);
                    double bump = score.localScoreDiff(parent, child);

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
