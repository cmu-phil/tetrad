///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TaskManager;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;


/**
 * GesSearch is an implementation of the GES algorithm, as specified in Chickering (2002) "Optimal structure
 * identification with greedy search" Journal of Machine Learning Research. It works for both BayesNets and SEMs.
 * <p>
 * Some code optimization could be done for the scoring part of the graph for discrete models (method scoreGraphChange).
 * Some of Andrew Moore's approaches for caching sufficient statistics, for instance.
 * <p>
 * To speed things up, it has been assumed that variables X and Y with zero correlation do not correspond to edges in
 * the graph. This is a restricted form of the heuristicSpeedup assumption, something GES does not assume. This
 * the graph. This is a restricted form of the heuristicSpeedup assumption, something GES does not assume. This
 * heuristicSpeedup assumption needs to be explicitly turned on using setHeuristicSpeedup(true).
 * <p>
 * A number of other optimizations were added 5/2015. See code for details.
 *
 * @author Ricardo Silva, Summer 2003
 * @author Joseph Ramsey, Revisions 5/2015
 * @author Daniel Malinsky
 */
public final class TsFges2 implements GraphSearch, GraphScorer {


    /**
     * Internal.
     */
    private enum Mode {
        allowUnfaithfulness, heuristicSpeedup, coverNoncolliders
    }

    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();

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
    private Graph externalGraph;

    /**
     * If non-null, edges not adjacent in this graph will not be added.
     */
    private Graph boundGraph;

    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    /**
     * A bound on cycle length.
     */
    private int cycleBound = -1;

    /**
     * The totalScore for discrete searches.
     */
    private Score score;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * The top n graphs found by the algorithm, where n is numCPDAGsToStore.
     */
    private final LinkedList<ScoredGraph> topGraphs = new LinkedList<>();

    /**
     * The number of top CPDAGs to store.
     */
    private int numCPDAGsToStore;

    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;

    // Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
    private SortedSet<Arrow> sortedArrows;

    // Arrows added to sortedArrows for each <i, j>.
    private Map<OrderedPair<Node>, Set<Arrow>> lookupArrows;

    // A utility map to help with orientation.
    private Map<Node, Set<Node>> neighbors;

    // Map from variables to their column indices in the data set.
    private ConcurrentMap<Node, Integer> hashIndices;

    // The static ForkJoinPool instance.
    private ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

    // A running tally of the total BIC totalScore.
    private double totalScore;

    // A graph where X--Y means that X and Y have non-zero total effect on one another.
    private Graph effectEdgesGraph;

    // Where printed output is sent.
    private PrintStream out = System.out;

    // A initial adjacencies graph.
    private Graph adjacencies;

    // The graph being constructed.
    private Graph graph;

    // Arrows with the same totalScore are stored in this list to distinguish their order in sortedArrows.
    // The ordering doesn't matter; it just have to be transitive.
    int arrowIndex;

    // The final totalScore after search.
    private double modelScore;

    // Internal.
    private Mode mode = Mode.heuristicSpeedup;

    /**
     * True if one-edge faithfulness is assumed. Speeds the algorithm up.
     */
    private boolean faithfulnessAssumed = true;

    // Bounds the indegree of the graph.
    private int maxIndegree = -1;

    final int maxThreads = ForkJoinPoolInstance.getInstance().getPool().getParallelism();

    //===========================CONSTRUCTORS=============================//

    /**
     * Construct a Score and pass it in here. The totalScore should return a
     * positive value in case of conditional dependence and a negative
     * values in case of conditional independence. See Chickering (2002),
     * locally consistent scoring criterion.
     */
    public TsFges2(Score score) {
        if (score == null) throw new NullPointerException();
        setScore(score);
        this.graph = new EdgeListGraph(getVariables());
    }

    //==========================PUBLIC METHODS==========================//

    /**
     * Set to true if it is assumed that all path pairs with one length 1 path do not cancel.
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = true;
    }

    /**
     * @return true if it is assumed that all path pairs with one length 1 path do not cancel.
     */
    public boolean isFaithfulnessAssumed() {
        return this.faithfulnessAssumed;
    }

    /**
     * Greedy equivalence search: Start from the empty graph, add edges till model is significant. Then start deleting
     * edges till a minimum is achieved.
     *
     * @return the resulting CPDAG.
     */
    public Graph search() {
        this.topGraphs.clear();

        this.lookupArrows = new ConcurrentHashMap<>();
        List<Node> nodes = new ArrayList<>(this.variables);
        this.graph = new EdgeListGraph(nodes);

        if (this.adjacencies != null) {
            this.adjacencies = GraphUtils.replaceNodes(this.adjacencies, nodes);
        }

        if (this.externalGraph != null) {
            this.graph = new EdgeListGraph(this.externalGraph);
            this.graph = GraphUtils.replaceNodes(this.graph, nodes);
        }

        addRequiredEdges(this.graph);

        if (this.faithfulnessAssumed) {
            initializeForwardEdgesFromEmptyGraph(getVariables());

            // Do forward search.
            this.mode = Mode.heuristicSpeedup;
            fes();
            bes();

            this.mode = Mode.coverNoncolliders;
            initializeTwoStepEdges(getVariables());
        } else {
            initializeForwardEdgesFromEmptyGraph(getVariables());

            // Do forward search.
            this.mode = Mode.heuristicSpeedup;
            fes();
            bes();

            this.mode = Mode.allowUnfaithfulness;
            initializeForwardEdgesFromExistingGraph(getVariables());
        }
        fes();
        bes();

        long start = System.currentTimeMillis();
        this.totalScore = 0.0;

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - start;
        this.logger.log("graph", "\nReturning this graph: " + this.graph);

        this.logger.log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        this.logger.flush();

        this.modelScore = this.totalScore;

        return this.graph;
    }

    /**
     * @return the background knowledge.
     */

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * If the true graph is set, askterisks will be printed in log output for the true edges.
     */
    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    /**
     * @return the totalScore of the given DAG, up to a constant.
     */
    public double getScore(Graph dag) {
        return scoreDag(dag);
    }

    /**
     * @return the list of top scoring graphs.
     */
    public LinkedList<ScoredGraph> getTopGraphs() {
        return this.topGraphs;
    }

    /**
     * @return the number of patterns to store.
     */
    public int getnumCPDAGsToStore() {
        return this.numCPDAGsToStore;
    }

    /**
     * Sets the number of patterns to store. This should be set to zero for fast search.
     */
    public void setNumCPDAGsToStore(int numCPDAGsToStore) {
        if (numCPDAGsToStore < 0) {
            throw new IllegalArgumentException("# graphs to store must at least 0: " + numCPDAGsToStore);
        }

        this.numCPDAGsToStore = numCPDAGsToStore;
    }

    /**
     * @return the initial graph for the search. The search is initialized to this graph and
     * proceeds from there.
     */
    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    /**
     * Sets the initial graph.
     */
    public void setExternalGraph(Graph externalGraph) {
        if (externalGraph != null) {
            externalGraph = GraphUtils.replaceNodes(externalGraph, this.variables);

            if (this.verbose) {
                this.out.println("Initial graph variables: " + externalGraph.getNodes());
                this.out.println("Data set variables: " + this.variables);
            }

            if (!new HashSet<>(externalGraph.getNodes()).equals(new HashSet<>(this.variables))) {
                throw new IllegalArgumentException("Variables aren't the same.");
            }
        }

        this.externalGraph = externalGraph;
    }

    /**
     * Sets whether verbose output should be produced.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent to.
     * By detault System.out.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * @return the output stream that output (except for log output) should be sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * @return the set of preset adjacenies for the algorithm; edges not in this adjacencies graph
     * will not be added.
     */
    public Graph getAdjacencies() {
        return this.adjacencies;
    }

    /**
     * Sets the set of preset adjacenies for the algorithm; edges not in this adjacencies graph
     * will not be added.
     */
    public void setAdjacencies(Graph adjacencies) {
        this.adjacencies = adjacencies;
    }

    /**
     * A bound on cycle length.
     */
    public int getCycleBound() {
        return this.cycleBound;
    }

    /**
     * A bound on cycle length.
     *
     * @param cycleBound The bound, >= 1, or -1 for unlimited.
     */
    public void setCycleBound(int cycleBound) {
        if (!(cycleBound == -1 || cycleBound >= 1))
            throw new IllegalArgumentException("Cycle bound needs to be -1 or >= 1: " + cycleBound);
        this.cycleBound = cycleBound;
    }

    /**
     * Creates a new processors pool with the specified number of threads.
     */
    public void setParallelism(int numProcessors) {
        this.pool = new ForkJoinPool(numProcessors);
    }

    /**
     * If non-null, edges not adjacent in this graph will not be added.
     */
    public void setBoundGraph(Graph boundGraph) {
        this.boundGraph = GraphUtils.replaceNodes(boundGraph, getVariables());
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous searches.
     *
     * @deprecated Use the getters on the individual scores instead.
     */
    public double getPenaltyDiscount() {
        if (this.score instanceof ISemBicScore) {
            return ((ISemBicScore) this.score).getPenaltyDiscount();
        } else {
            return 2.0;
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setSamplePrior(double samplePrior) {
        if (this.score instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) this.score).setSamplePrior(samplePrior);
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setStructurePrior(double expectedNumParents) {
        if (this.score instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) this.score).setStructurePrior(expectedNumParents);
        }
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous searches.
     *
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (this.score instanceof ISemBicScore) {
            ((ISemBicScore) this.score).setPenaltyDiscount(penaltyDiscount);
        }
    }

    /**
     * The maximum of parents any nodes can have in output pattern.
     *
     * @return -1 for unlimited.
     */
    public int getMaxIndegree() {
        return this.maxIndegree;
    }

    /**
     * The maximum of parents any nodes can have in output pattern.
     *
     * @param maxIndegree -1 for unlimited.
     */
    public void setMaxIndegree(int maxIndegree) {
        if (maxIndegree < -1) throw new IllegalArgumentException();
        this.maxIndegree = maxIndegree;
    }

    //===========================PRIVATE METHODS========================//

    //Sets the discrete scoring function to use.
    private void setScore(Score totalScore) {
        this.score = totalScore;

        this.variables = new ArrayList<>();

        for (Node node : totalScore.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }

        buildIndexing(totalScore.getVariables());

        this.maxIndegree = this.score.getMaxDegree();
    }

    final int[] count = new int[1];

    public int getMinChunk(int n) {
        // The minimum number of operations to do before parallelizing.
        int minChunk = 100;
        return Math.max(n / this.maxThreads, minChunk);
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
            for (int i = this.from; i < this.to; i++) {
                if ((i + 1) % 1000 == 0) {
                    TsFges2.this.count[0] += 1000;
                    TsFges2.this.out.println("Initializing effect edges: " + (TsFges2.this.count[0]));
                }

                Node y = this.nodes.get(i);
                TsFges2.this.neighbors.put(y, this.emptySet);

                for (int j = i + 1; j < this.nodes.size(); j++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    Node x = this.nodes.get(j);

                    if (existsKnowledge()) {
                        if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                            continue;
                        }

                        if (!validSetByKnowledge(y, this.emptySet)) {
                            continue;
                        }
                    }

                    if (TsFges2.this.adjacencies != null && !TsFges2.this.adjacencies.isAdjacentTo(x, y)) {
                        continue;
                    }

                    int child = TsFges2.this.hashIndices.get(y);
                    int parent = TsFges2.this.hashIndices.get(x);
                    double bump = TsFges2.this.score.localScoreDiff(parent, child);

                    if (TsFges2.this.boundGraph != null && !TsFges2.this.boundGraph.isAdjacentTo(x, y)) continue;

                    if (bump > 0) {
                        Edge edge = Edges.undirectedEdge(x, y);
                        TsFges2.this.effectEdgesGraph.addEdge(edge);
                    }

                    if (bump > 0.0) {
                        addArrow(x, y, this.emptySet, this.emptySet, bump);
                        addArrow(y, x, this.emptySet, this.emptySet, bump);
                    }
                }
            }

            return true;
        }
    }

    private void initializeForwardEdgesFromEmptyGraph(List<Node> nodes) {

        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();
        Set<Node> emptySet = new HashSet<>();

        long start = System.currentTimeMillis();
        this.effectEdgesGraph = new EdgeListGraph(nodes);

        class InitializeFromEmptyGraphTask extends RecursiveTask<Boolean> {

            public InitializeFromEmptyGraphTask() {
            }

            @Override
            protected Boolean compute() {
                Queue<NodeTaskEmptyGraph> tasks = new ArrayDeque<>();

                int numNodesPerTask = Math.max(100, nodes.size() / TsFges2.this.maxThreads);

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

                    while (tasks.size() > TsFges2.this.maxThreads) {
                        NodeTaskEmptyGraph _task = tasks.poll();
                        _task.join();
                    }
                }

                for (NodeTaskEmptyGraph task : tasks) {
                    task.join();
                }

                return true;
            }
        }

        this.pool.invoke(new InitializeFromEmptyGraphTask());

        long stop = System.currentTimeMillis();

        if (this.verbose) {
            this.out.println("Elapsed initializeForwardEdgesFromEmptyGraph = " + (stop - start) + " ms");
        }
    }

    private void initializeTwoStepEdges(List<Node> nodes) {

        this.count[0] = 0;

        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();

        if (this.effectEdgesGraph == null) {
            this.effectEdgesGraph = new EdgeListGraph(nodes);
        }

        if (this.externalGraph != null) {
            for (Edge edge : this.externalGraph.getEdges()) {
                if (!this.effectEdgesGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    this.effectEdgesGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
                }
            }
        }

        Set<Node> emptySet = new HashSet<>(0);

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

                if (this.to - this.from <= this.chunk) {
                    for (int i = this.from; i < this.to; i++) {
                        if ((i + 1) % 1000 == 0) {
                            TsFges2.this.count[0] += 1000;
                            TsFges2.this.out.println("Initializing effect edges: " + (TsFges2.this.count[0]));
                        }

                        Node y = nodes.get(i);

                        Set<Node> g = new HashSet<>();

                        for (Node n : TsFges2.this.graph.getAdjacentNodes(y)) {
                            for (Node m : TsFges2.this.graph.getAdjacentNodes(n)) {
                                if (TsFges2.this.graph.isAdjacentTo(y, m)) {
                                    continue;
                                }

                                if (TsFges2.this.graph.isDefCollider(m, n, y)) {
                                    continue;
                                }

                                g.add(m);
                            }
                        }

                        for (Node x : g) {
                            if (existsKnowledge()) {
                                if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                                    continue;
                                }

                                if (!validSetByKnowledge(y, emptySet)) {
                                    continue;
                                }
                            }

                            if (TsFges2.this.adjacencies != null && !TsFges2.this.adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            if (TsFges2.this.removedEdges.contains(Edges.undirectedEdge(x, y))) {
                                continue;
                            }

                            calculateArrowsForward(x, y);
                        }
                    }

                } else {
                    int mid = (this.to + this.from) / 2;

                    InitializeFromExistingGraphTask left = new InitializeFromExistingGraphTask(this.chunk, this.from, mid);
                    InitializeFromExistingGraphTask right = new InitializeFromExistingGraphTask(this.chunk, mid, this.to);

                    left.fork();
                    right.compute();
                    left.join();

                }
                return true;
            }
        }

        this.pool.invoke(new InitializeFromExistingGraphTask(getMinChunk(nodes.size()), 0, nodes.size()));
    }

    private void initializeForwardEdgesFromExistingGraph(List<Node> nodes) {

        this.count[0] = 0;

        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();

        if (this.effectEdgesGraph == null) {
            this.effectEdgesGraph = new EdgeListGraph(nodes);
        }

        if (this.externalGraph != null) {
            for (Edge edge : this.externalGraph.getEdges()) {
                if (!this.effectEdgesGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    this.effectEdgesGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
                }
            }
        }

        Set<Node> emptySet = new HashSet<>(0);

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

                if (this.to - this.from <= this.chunk) {
                    for (int i = this.from; i < this.to; i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if ((i + 1) % 1000 == 0) {
                            TsFges2.this.count[0] += 1000;
                            TsFges2.this.out.println("Initializing effect edges: " + (TsFges2.this.count[0]));
                        }

                        Node y = nodes.get(i);
                        List<Node> cond = new ArrayList<>();
                        Set<Node> D = new HashSet<>(GraphUtils.getDconnectedVars(y, cond, TsFges2.this.graph));
                        D.remove(y);
                        TsFges2.this.effectEdgesGraph.getAdjacentNodes(y).forEach(D::remove);

                        for (Node x : D) {
                            if (existsKnowledge()) {
                                if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                                    continue;
                                }

                                if (!validSetByKnowledge(y, emptySet)) {
                                    continue;
                                }
                            }

                            if (TsFges2.this.adjacencies != null && !TsFges2.this.adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            calculateArrowsForward(x, y);
                        }
                    }

                } else {
                    int mid = (this.to + this.from) / 2;

                    InitializeFromExistingGraphTask left = new InitializeFromExistingGraphTask(this.chunk, this.from, mid);
                    InitializeFromExistingGraphTask right = new InitializeFromExistingGraphTask(this.chunk, mid, this.to);

                    left.fork();
                    right.compute();
                    left.join();

                }
                return true;
            }
        }

        this.pool.invoke(new InitializeFromExistingGraphTask(getMinChunk(nodes.size()), 0, nodes.size()));
    }

    private void fes() {
        TetradLogger.getInstance().log("info", "** FORWARD EQUIVALENCE SEARCH");

        while (!this.sortedArrows.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Arrow arrow = this.sortedArrows.first();
            this.sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (this.graph.isAdjacentTo(x, y)) {
                continue;
            }

            if (!arrow.getNaYX().equals(getNaYX(x, y))) {
                continue;
            }

            if (!getTNeighbors(x, y).containsAll(arrow.getHOrT())) {
                continue;
            }

            if (!validInsert(x, y, arrow.getHOrT(), getNaYX(x, y))) {
                continue;
            }

            Set<Node> T = arrow.getHOrT();
            double bump = arrow.getBump();

            boolean inserted = insert(x, y, T, bump);
            if (!inserted) continue;

            this.totalScore += bump;

            Set<Node> visited = reapplyOrientation(x, y, null);
            Set<Node> toProcess = new HashSet<>();

            for (Node node : visited) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Set<Node> neighbors1 = getNeighbors(node);
                Set<Node> storedNeighbors = this.neighbors.get(node);

                if (!(neighbors1.equals(storedNeighbors))) {
                    toProcess.add(node);
                }
            }

            toProcess.add(x);
            toProcess.add(y);

            storeGraph();
            reevaluateForward(toProcess, arrow);
        }
    }

    private void bes() {
        TetradLogger.getInstance().log("info", "** BACKWARD EQUIVALENCE SEARCH");

        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();

        initializeArrowsBackward();

        while (!this.sortedArrows.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Arrow arrow = this.sortedArrows.first();
            this.sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (!arrow.getNaYX().equals(getNaYX(x, y))) {
                continue;
            }

            if (!this.graph.isAdjacentTo(x, y)) continue;

            Edge edge = this.graph.getEdge(x, y);
            if (edge.pointsTowards(x)) continue;

            HashSet<Node> diff = new HashSet<>(arrow.getNaYX());
            diff.removeAll(arrow.getHOrT());

            if (!validDelete(x, y, arrow.getHOrT(), arrow.getNaYX())) continue;

            Set<Node> H = arrow.getHOrT();
            double bump = arrow.getBump();

            boolean deleted = delete(x, y, H, bump, arrow.getNaYX());
            if (!deleted) continue;

            this.totalScore += bump;

            clearArrow(x, y);

            Set<Node> visited = reapplyOrientation(x, y, H);

            Set<Node> toProcess = new HashSet<>();

            for (Node node : visited) {
                Set<Node> neighbors1 = getNeighbors(node);
                Set<Node> storedNeighbors = this.neighbors.get(node);

                if (!(neighbors1.equals(storedNeighbors))) {
                    toProcess.add(node);
                }
            }

            toProcess.add(x);
            toProcess.add(y);
            toProcess.addAll(getCommonAdjacents(x, y));

            storeGraph();
            reevaluateBackward(toProcess);
        }

        meekOrientRestricted(getVariables(), getKnowledge());
    }

    private Set<Node> getCommonAdjacents(Node x, Node y) {
        Set<Node> commonChildren = new HashSet<>(this.graph.getAdjacentNodes(x));
        commonChildren.retainAll(this.graph.getAdjacentNodes(y));
        return commonChildren;
    }

    private Set<Node> reapplyOrientation(Node x, Node y, Set<Node> newArrows) {
        Set<Node> toProcess = new HashSet<>();
        toProcess.add(x);
        toProcess.add(y);

        if (newArrows != null) {
            toProcess.addAll(newArrows);
        }

        return meekOrientRestricted(new ArrayList<>(toProcess), getKnowledge());
    }

    // Returns true if knowledge is not empty.
    private boolean existsKnowledge() {
        return !this.knowledge.isEmpty();
    }


    // Initiaizes the sorted arrows lists for the backward search.
    private void initializeArrowsBackward() {
        for (Edge edge : this.graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

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
    private void reevaluateForward(Set<Node> nodes, Arrow arrow) {
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
                if (this.to - this.from <= this.chunk) {
                    for (int _w = this.from; _w < this.to; _w++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        Node x = this.nodes.get(_w);

                        List<Node> adj;

                        if (TsFges2.this.mode == Mode.heuristicSpeedup) {
                            adj = TsFges2.this.effectEdgesGraph.getAdjacentNodes(x);
                        } else if (TsFges2.this.mode == Mode.coverNoncolliders) {
                            Set<Node> g = new HashSet<>();

                            for (Node n : TsFges2.this.graph.getAdjacentNodes(x)) {
                                for (Node m : TsFges2.this.graph.getAdjacentNodes(n)) {
                                    if (TsFges2.this.graph.isAdjacentTo(x, m)) {
                                        continue;
                                    }

                                    if (TsFges2.this.graph.isDefCollider(m, n, x)) {
                                        continue;
                                    }

                                    g.add(m);
                                }
                            }

                            adj = new ArrayList<>(g);
                        } else if (TsFges2.this.mode == Mode.allowUnfaithfulness) {
                            HashSet<Node> D = new HashSet<>(GraphUtils.getDconnectedVars(x, new ArrayList<>(), TsFges2.this.graph));
                            D.remove(x);
                            adj = new ArrayList<>(D);
                        } else {
                            throw new IllegalStateException();
                        }

                        for (Node w : adj) {
                            if (TsFges2.this.adjacencies != null && !(TsFges2.this.adjacencies.isAdjacentTo(w, x))) {
                                continue;
                            }

                            if (w == x) continue;

                            if (!TsFges2.this.graph.isAdjacentTo(w, x)) {
                                clearArrow(w, x);
                                calculateArrowsForward(w, x);
                            }
                        }
                    }

                } else {
                    int mid = (this.to - this.from) / 2;

                    List<AdjTask> tasks = new ArrayList<>();

                    tasks.add(new AdjTask(this.chunk, this.nodes, this.from, this.from + mid));
                    tasks.add(new AdjTask(this.chunk, this.nodes, this.from + mid, this.to));

                    ForkJoinTask.invokeAll(tasks);

                }
                return true;
            }
        }

        AdjTask task = new AdjTask(getMinChunk(nodes.size()), new ArrayList<>(nodes), 0, nodes.size());
        this.pool.invoke(task);
    }

    // Calculates the new arrows for an a->b edge.
    private void calculateArrowsForward(Node a, Node b) {
        if (this.mode == Mode.heuristicSpeedup && !this.effectEdgesGraph.isAdjacentTo(a, b)) return;
        if (this.adjacencies != null && !this.adjacencies.isAdjacentTo(a, b)) return;
        this.neighbors.put(b, getNeighbors(b));

        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = getNaYX(a, b);
        if (!GraphUtils.isClique(naYX, this.graph)) return;

        List<Node> TNeighbors = getTNeighbors(a, b);
        int _maxIndegree = this.maxIndegree == -1 ? 1000 : this.maxIndegree;

        int _max = Math.min(TNeighbors.size(), _maxIndegree - this.graph.getIndegree(b));

        Set<Set<Node>> previousCliques = new HashSet<>();
        previousCliques.add(new HashSet<>());
        Set<Set<Node>> newCliques = new HashSet<>();

        FOR:
        for (int i = 0; i <= _max; i++) {
            ChoiceGenerator gen = new ChoiceGenerator(TNeighbors.size(), i);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

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

                if (!GraphUtils.isClique(union, this.graph)) continue;
                newCliques.add(union);

                double bump = insertEval(a, b, T, naYX, this.hashIndices);

                if (bump > 0.0) {
                    addArrow(a, b, naYX, T, bump);
                }

            }

            previousCliques = newCliques;
            newCliques = new HashSet<>();
        }
    }

    private void addArrow(Node a, Node b, Set<Node> naYX, Set<Node> hOrT, double bump) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, naYX, this.arrowIndex++);
        this.sortedArrows.add(arrow);
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
                if (this.to - this.from <= this.chunk) {
                    for (int _w = this.from; _w < this.to; _w++) {
                        Node w = this.adj.get(_w);
                        Edge e = TsFges2.this.graph.getEdge(w, this.r);

                        if (e != null) {
                            if (e.pointsTowards(this.r)) {
                                clearArrow(w, this.r);
                                clearArrow(this.r, w);

                                calculateArrowsBackward(w, this.r);
                            } else if (Edges.isUndirectedEdge(TsFges2.this.graph.getEdge(w, this.r))) {
                                clearArrow(w, this.r);
                                clearArrow(this.r, w);

                                calculateArrowsBackward(w, this.r);
                                calculateArrowsBackward(this.r, w);
                            }
                        }
                    }

                } else {
                    int mid = (this.to - this.from) / 2;

                    List<BackwardTask> tasks = new ArrayList<>();

                    tasks.add(new BackwardTask(this.r, this.adj, this.chunk, this.from, this.from + mid, this.hashIndices));
                    tasks.add(new BackwardTask(this.r, this.adj, this.chunk, this.from + mid, this.to, this.hashIndices));

                    ForkJoinTask.invokeAll(tasks);

                }
                return true;
            }
        }

        for (Node r : toProcess) {
            this.neighbors.put(r, getNeighbors(r));
            List<Node> adjacentNodes = this.graph.getAdjacentNodes(r);
            this.pool.invoke(new BackwardTask(r, adjacentNodes, getMinChunk(adjacentNodes.size()), 0,
                    adjacentNodes.size(), this.hashIndices));
        }
    }

    // Calculates the arrows for the removal in the backward direction.
    private void calculateArrowsBackward(Node a, Node b) {
        if (existsKnowledge()) {
            if (!getKnowledge().noEdgeRequired(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = getNaYX(a, b);

        List<Node> _naYX = new ArrayList<>(naYX);

        int _depth = _naYX.size();

        for (int i = 0; i <= _depth; i++) {
            ChoiceGenerator gen = new ChoiceGenerator(_naYX.size(), i);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Set<Node> diff = GraphUtils.asSet(choice, _naYX);

                Set<Node> h = new HashSet<>(_naYX);
                h.removeAll(diff);

                if (existsKnowledge()) {
                    if (!validSetByKnowledge(b, h)) {
                        continue;
                    }
                }

                double bump = deleteEval(a, b, diff, naYX, this.hashIndices);

                if (bump > 0.0) {
                    addArrow(a, b, naYX, h, bump);
                }
            }
        }
    }

    public double getModelScore() {
        return this.modelScore;
    }

    // Basic data structure for an arrow a->b considered for additiom or removal from the graph, together with
    // associated sets needed to make this determination. For both forward and backward direction, NaYX is needed.
    // For the forward direction, T neighbors are needed; for the backward direction, H neighbors are needed.
    // See Chickering (2002). The totalScore difference resulting from added in the edge (hypothetically) is recorded
    // as the "bump".
    private static class Arrow implements Comparable<Arrow> {
        private final double bump;
        private final Node a;
        private final Node b;
        private final Set<Node> hOrT;
        private final Set<Node> naYX;
        private final int index;

        public Arrow(double bump, Node a, Node b, Set<Node> hOrT, Set<Node> naYX, int index) {
            this.bump = bump;
            this.a = a;
            this.b = b;
            this.hOrT = hOrT;
            this.naYX = naYX;
            this.index = index;
        }

        public double getBump() {
            return this.bump;
        }

        public Node getA() {
            return this.a;
        }

        public Node getB() {
            return this.b;
        }

        public Set<Node> getHOrT() {
            return this.hOrT;
        }

        public Set<Node> getNaYX() {
            return this.naYX;
        }

        // Sorting by bump, high to low. The problem is the SortedSet contains won't add a new element if it compares
        // to zero with an existing element, so for the cases where the comparison is to zero (i.e. have the same
        // bump, we need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables.
        // The fastest way to do this is using a hash code, though it's still possible for two Arrows to have the
        // same hash code but not be equal. If we're paranoid, in this case we calculate a determinate comparison
        // not equal to zero by keeping a list. This last part is commened out by default.
        public int compareTo(Arrow arrow) {
            if (arrow == null) throw new NullPointerException();

            int compare = Double.compare(arrow.getBump(), getBump());

            if (compare == 0) {
                return Integer.compare(getIndex(), arrow.getIndex());
            }

            return compare;
        }

        public String toString() {
            return "Arrow<" + this.a + "->" + this.b + " bump = " + this.bump + " t/h = " + this.hOrT + " naYX = " + this.naYX + ">";
        }

        public int getIndex() {
            return this.index;
        }
    }

    // Get all adj that are connected to Y by an undirected edge and not adjacent to X.
    private List<Node> getTNeighbors(Node x, Node y) {
        List<Edge> yEdges = this.graph.getEdges(y);
        List<Node> tNeighbors = new ArrayList<>();

        for (Edge edge : yEdges) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            Node z = edge.getDistalNode(y);

            if (this.graph.isAdjacentTo(z, x)) {
                continue;
            }

            tNeighbors.add(z);
        }

        return tNeighbors;
    }

    // Get all adj that are connected to Y.
    private Set<Node> getNeighbors(Node y) {
        List<Edge> yEdges = this.graph.getEdges(y);
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
        Set<Node> set = new HashSet<>(naYX);
        set.addAll(t);
        set.addAll(this.graph.getParents(y));
        return scoreGraphChange(y, set, x, hashIndices);
    }

    // Evaluate the Delete(X, Y, T) operator (Definition 12 from Chickering, 2002).
    private double deleteEval(Node x, Node y, Set<Node> diff, Set<Node> naYX,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set = new HashSet<>(diff);
        set.addAll(this.graph.getParents(y));
        set.remove(x);
        return -scoreGraphChange(y, set, x, hashIndices);
    }

    // Do an actual insertion. (Definition 12 from Chickering, 2002).
    private boolean insert(Node x, Node y, Set<Node> T, double bump) {
        if (this.graph.isAdjacentTo(x, y)) {
            return false; // The initial graph may already have put this edge in the graph.
        }

        Edge trueEdge = null;

        if (this.trueGraph != null) {
            Node _x = this.trueGraph.getNode(x.getName());
            Node _y = this.trueGraph.getNode(y.getName());
            trueEdge = this.trueGraph.getEdge(_x, _y);
        }

        if (this.boundGraph != null && !this.boundGraph.isAdjacentTo(x, y)) return false;

        this.graph.addDirectedEdge(x, y);
        //  Adding similar edges to enforce repeating structure **/
        addSimilarEdges(x, y);
        //  **/

        if (this.verbose) {
            String label = this.trueGraph != null && trueEdge != null ? "*" : "";
            TetradLogger.getInstance().log("insertedEdges", this.graph.getNumEdges() + ". INSERT " + this.graph.getEdge(x, y) +
                    " " + T + " " + bump + " " + label);
        }

        int numEdges = this.graph.getNumEdges();

//        if (verbose) {
        if (numEdges % 1000 == 0) this.out.println("Num edges added: " + numEdges);
//        }

        if (this.verbose) {
            String label = this.trueGraph != null && trueEdge != null ? "*" : "";
            this.out.println(this.graph.getNumEdges() + ". INSERT " + this.graph.getEdge(x, y) +
                    " " + T + " " + bump + " " + label
                    + " degree = " + GraphUtils.getDegree(this.graph)
                    + " indegree = " + GraphUtils.getIndegree(this.graph));
        }

        for (Node _t : T) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            this.graph.removeEdge(_t, y);
            //  removing similar edges to enforce repeating structure **/
            removeSimilarEdges(_t, y);
            //  **/
            if (this.boundGraph != null && !this.boundGraph.isAdjacentTo(_t, y)) continue;

            this.graph.addDirectedEdge(_t, y);
            //  Adding similar edges to enforce repeating structure **/
            addSimilarEdges(_t, y);
            //  **/

            if (this.verbose) {
                String message = "--- Directing " + this.graph.getEdge(_t, y);
                TetradLogger.getInstance().log("directedEdges", message);
                this.out.println(message);
            }
        }

        return true;
    }

    Set<Edge> removedEdges = new HashSet<>();

    // Do an actual deletion (Definition 13 from Chickering, 2002).
    private boolean delete(Node x, Node y, Set<Node> H, double bump, Set<Node> naYX) {
        Edge trueEdge = null;

        if (this.trueGraph != null) {
            Node _x = this.trueGraph.getNode(x.getName());
            Node _y = this.trueGraph.getNode(y.getName());
            trueEdge = this.trueGraph.getEdge(_x, _y);
        }

        Edge oldxy = this.graph.getEdge(x, y);

        Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);

        this.graph.removeEdge(oldxy);
        this.removedEdges.add(Edges.undirectedEdge(x, y));
        //  removing similar edges to enforce repeating structure **/
        removeSimilarEdges(x, y);
        //  **/

//        if (verbose) {
        int numEdges = this.graph.getNumEdges();
        if (numEdges % 1000 == 0) this.out.println("Num edges (backwards) = " + numEdges);
//        }

        if (this.verbose) {
            String label = this.trueGraph != null && trueEdge != null ? "*" : "";
            String message = (this.graph.getNumEdges()) + ". DELETE " + x + "-->" + y +
                    " H = " + H + " NaYX = " + naYX + " diff = " + diff + " (" + bump + ") " + label;
            TetradLogger.getInstance().log("deletedEdges", message);
            this.out.println(message);
        }

        for (Node h : H) {
            if (this.graph.isParentOf(h, y) || this.graph.isParentOf(h, x)) continue;

            Edge oldyh = this.graph.getEdge(y, h);

            this.graph.removeEdge(oldyh);

            this.graph.addEdge(Edges.directedEdge(y, h));
            //  removing similar edges (which should be undirected) and adding similar directed edges **/
            removeSimilarEdges(y, h);
            addSimilarEdges(y, h);
            //  **/

            if (this.verbose) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldyh + " to " +
                        this.graph.getEdge(y, h));
                this.out.println("--- Directing " + oldyh + " to " + this.graph.getEdge(y, h));
            }

            Edge oldxh = this.graph.getEdge(x, h);

            if (Edges.isUndirectedEdge(oldxh)) {
                this.graph.removeEdge(oldxh);

                this.graph.addEdge(Edges.directedEdge(x, h));

                //  removing similar edges (which should be undirected) and adding similar directed edges **/
                removeSimilarEdges(x, h);
                addSimilarEdges(x, h);
                //  **/

                if (this.verbose) {
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldxh + " to " +
                            this.graph.getEdge(x, h));
                    this.out.println("--- Directing " + oldxh + " to " + this.graph.getEdge(x, h));
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
            if (this.knowledge.isForbidden(x.getName(), y.getName())) {
                violatesKnowledge = true;
            }

            for (Node t : T) {
                if (this.knowledge.isForbidden(t.getName(), y.getName())) {
                    violatesKnowledge = true;
                }
            }
        }

        Set<Node> union = new HashSet<>(T);
        union.addAll(naYX);
        boolean clique = GraphUtils.isClique(union, this.graph);
        boolean noCycle = !existsUnblockedSemiDirectedPath(y, x, union, this.cycleBound);
        return clique && noCycle && !violatesKnowledge;
    }

    private boolean validDelete(Node x, Node y, Set<Node> H, Set<Node> naYX) {
        boolean violatesKnowledge = false;

        if (existsKnowledge()) {
            for (Node h : H) {
                if (this.knowledge.isForbidden(x.getName(), h.getName())) {
                    violatesKnowledge = true;
                }

                if (this.knowledge.isForbidden(y.getName(), h.getName())) {
                    violatesKnowledge = true;
                }
            }
        }

        Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);
        return GraphUtils.isClique(diff, this.graph) && !violatesKnowledge;
    }

    // Adds edges required by knowledge.
    private void addRequiredEdges(Graph graph) {
        if (!existsKnowledge()) return;

        for (Iterator<KnowledgeEdge> it = getKnowledge().requiredEdgesIterator(); it.hasNext(); ) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            KnowledgeEdge next = it.next();

            Node nodeA = graph.getNode(next.getFrom());
            Node nodeB = graph.getNode(next.getTo());

            if (!graph.isAncestorOf(nodeB, nodeA)) {
                graph.removeEdges(nodeA, nodeB);
                graph.addDirectedEdge(nodeA, nodeB);
                TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB));
            }
        }
        for (Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            String A = edge.getNode1().getName();
            String B = edge.getNode2().getName();

            if (this.knowledge.isForbidden(A, B)) {
                Node nodeA = edge.getNode1();
                Node nodeB = edge.getNode2();
                if (nodeA == null || nodeB == null) throw new NullPointerException();

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }

                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }
            } else if (this.knowledge.isForbidden(B, A)) {
                Node nodeA = edge.getNode2();
                Node nodeB = edge.getNode1();
                if (nodeA == null || nodeB == null) throw new NullPointerException();

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }
                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }
            }
        }
    }

    // Use background knowledge to decide if an insert or delete operation does not orient edges in a forbidden
    // direction according to prior knowledge. If some orientation is forbidden in the subset, the whole subset is
    // forbidden.
    private boolean validSetByKnowledge(Node y, Set<Node> subset) {
        for (Node node : subset) {
            if (getKnowledge().isForbidden(node.getName(), y.getName())) {
                return false;
            }
        }
        return true;
    }

    // Find all adj that are connected to Y by an undirected edge that are adjacent to X (that is, by undirected or
    // directed edge).
    private Set<Node> getNaYX(Node x, Node y) {
        List<Node> adj = this.graph.getAdjacentNodes(y);
        Set<Node> nayx = new HashSet<>();

        for (Node z : adj) {
            if (z == x) continue;
            Edge yz = this.graph.getEdge(y, z);
            if (!Edges.isUndirectedEdge(yz)) continue;
            if (!this.graph.isAdjacentTo(z, x)) continue;
            nayx.add(z);
        }

        return nayx;
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
                if (distance > (bound == -1 ? 1000 : bound)) return false;
            }

            for (Node u : this.graph.getAdjacentNodes(t)) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Edge edge = this.graph.getEdge(t, u);
                Node c = TsFges2.traverseSemiDirected(t, edge);
                if (c == null) continue;
                if (cond.contains(c)) continue;

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

    // Runs Meek rules on just the changed adj.
    private Set<Node> reorientNode(List<Node> nodes) {
        addRequiredEdges(this.graph);
        return meekOrientRestricted(nodes, getKnowledge());
    }

    // Runs Meek rules on just the changed adj.
    private Set<Node> meekOrientRestricted(List<Node> nodes, IKnowledge knowledge) {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        return rules.orientImplied(this.graph);
    }

    // Maps adj to their indices for quick lookup.
    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();

        int i = -1;

        for (Node n : nodes) {
            this.hashIndices.put(n, ++i);
        }
    }

    // Removes information associated with an edge x->y.
    private synchronized void clearArrow(Node x, Node y) {
        OrderedPair<Node> pair = new OrderedPair<>(x, y);
        Set<Arrow> lookupArrows = this.lookupArrows.get(pair);

        if (lookupArrows != null) {
            this.sortedArrows.removeAll(lookupArrows);
        }

        this.lookupArrows.remove(pair);
    }

    // Adds the given arrow for the adjacency i->j. These all are for i->j but may have
    // different T or H or NaYX sets, and so different bumps.
    private void addLookupArrow(Node i, Node j, Arrow arrow) {
        OrderedPair<Node> pair = new OrderedPair<>(i, j);
        Set<Arrow> arrows = this.lookupArrows.get(pair);

        if (arrows == null) {
            arrows = new ConcurrentSkipListSet<>();
            this.lookupArrows.put(pair, arrows);
        }

        arrows.add(arrow);
    }

    //===========================SCORING METHODS===================//

    /**
     * Scores the given DAG, up to a constant.
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
                parentIndices[count++] = this.hashIndices.get(nextParent);
            }

            int yIndex = this.hashIndices.get(y);
            _score += this.score.localScore(yIndex, parentIndices);
        }

        return _score;
    }

    private double scoreGraphChange(Node y, Set<Node> parents,
                                    Node x, Map<Node, Integer> hashIndices) {
        int yIndex = hashIndices.get(y);

        if (parents.contains(x)) return Double.NaN;//throw new IllegalArgumentException();

        int[] parentIndices = new int[parents.size()];

        int count = 0;
        for (Node parent : parents) {
            parentIndices[count++] = hashIndices.get(parent);
        }

        return this.score.localScoreDiff(hashIndices.get(x), yIndex, parentIndices);
    }

    private List<Node> getVariables() {
        return this.variables;
    }

    // Stores the graph, if its totalScore knocks out one of the top ones.
    private void storeGraph() {
        if (getnumCPDAGsToStore() > 0) {
            Graph graphCopy = new EdgeListGraph(this.graph);
            this.topGraphs.addLast(new ScoredGraph(graphCopy, this.totalScore));
        }

        if (this.topGraphs.size() == getnumCPDAGsToStore() + 1) {
            this.topGraphs.removeFirst();
        }
    }

    public String logEdgeBayesFactorsString(Graph dag) {
        Map<Edge, Double> factors = logEdgeBayesFactors(dag);
        return logBayesPosteriorFactorsString(factors, scoreDag(dag));
    }

    public Map<Edge, Double> logEdgeBayesFactors(Graph dag) {
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

    private String logBayesPosteriorFactorsString(Map<Edge, Double> factors, double modelScore) {
        NumberFormat nf = new DecimalFormat("0.00");
        StringBuilder builder = new StringBuilder();

        List<Edge> edges = new ArrayList<>(factors.keySet());

        Collections.sort(edges, new Comparator<Edge>() {
            @Override
            public int compare(Edge o1, Edge o2) {
                return -Double.compare(factors.get(o1), factors.get(o2));
            }
        });

        builder.append("Edge Posterior Log Bayes Factors:\n\n");

        builder.append("For a DAG in the IMaGES pattern with model totalScore m, for each edge e in the " +
                "DAG, the model totalScore that would result from removing each edge, calculating " +
                "the resulting model totalScore m(e), and then reporting m - m(e). The totalScore used is " +
                "the IMScore, L - SUM_i{kc ln n(i)}, L is the maximum likelihood of the model, " +
                "k isthe number of parameters of the model, n(i) is the sample size of the ith " +
                "data set, and c is the penalty penaltyDiscount. Note that the more negative the totalScore, " +
                "the more important the edge is to the posterior probability of the IMaGES model. " +
                "Edges are given in order of their importance so measured.\n\n");

        int i = 0;

        for (Edge edge : edges) {
            builder.append(++i).append(". ").append(edge).append(" ").append(nf.format(factors.get(edge))).append("\n");
        }

        return builder.toString();
    }

    // returnSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
    private List<List<Node>> returnSimilarPairs(Node x, Node y) {
        System.out.println("$$$$$ Entering returnSimilarPairs method with x,y = " + x + ", " + y);
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return new ArrayList<>();
        }
//        System.out.println("Knowledge within returnSimilar : " + knowledge);
        int ntiers = this.knowledge.getNumTiers();
        int indx_tier = this.knowledge.isInWhichTier(x);
        int indy_tier = this.knowledge.isInWhichTier(y);
        int tier_diff = Math.max(indx_tier, indy_tier) - Math.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List tier_x = this.knowledge.getTier(indx_tier);
//        Collections.sort(tier_x);
        List tier_y = this.knowledge.getTier(indy_tier);
//        Collections.sort(tier_y);

        int i;
        for (i = 0; i < tier_x.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for (i = 0; i < tier_y.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        System.out.println("original independence: " + x + " and " + y);

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");


        List<Node> simListX = new ArrayList<>();
        List<Node> simListY = new ArrayList<>();

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (this.knowledge.getTier(i).size() == 1) continue;
            String A;
            Node x1;
            String B;
            Node y1;
            if (indx_tier >= indy_tier) {
                List tmp_tier1 = this.knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier1);
                List tmp_tier2 = this.knowledge.getTier(i);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
            } else {
                List tmp_tier1 = this.knowledge.getTier(i);
//                Collections.sort(tmp_tier1);
                List tmp_tier2 = this.knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
            }
            if (A.equals(B)) continue;
            if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
            if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
            x1 = this.graph.getNode(A);
            y1 = this.graph.getNode(B);
            System.out.println("Adding pair to simList = " + x1 + " and " + y1);
            simListX.add(x1);
            simListY.add(y1);
        }

        List<List<Node>> pairList = new ArrayList<>();
        pairList.add(simListX);
        pairList.add(simListY);
        return (pairList);
    }

    public String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }

    public void addSimilarEdges(Node x, Node y) {
        List<List<Node>> simList = returnSimilarPairs(x, y);
        if (simList.isEmpty()) return;
        List<Node> x1List = simList.get(0);
        List<Node> y1List = simList.get(1);
        Iterator itx = x1List.iterator();
        Iterator ity = y1List.iterator();
        while (itx.hasNext() && ity.hasNext()) {
            Node x1 = (Node) itx.next();
            Node y1 = (Node) ity.next();
            System.out.println("$$$$$$$$$$$ similar pair x,y = " + x1 + ", " + y1);
            System.out.println("adding edge between x = " + x1 + " and y = " + y1);
            this.graph.addDirectedEdge(x1, y1);
        }
    }

    public void removeSimilarEdges(Node x, Node y) {
        List<List<Node>> simList = returnSimilarPairs(x, y);
        if (simList.isEmpty()) return;
        List<Node> x1List = simList.get(0);
        List<Node> y1List = simList.get(1);
        Iterator itx = x1List.iterator();
        Iterator ity = y1List.iterator();
        while (itx.hasNext() && ity.hasNext()) {
            Node x1 = (Node) itx.next();
            Node y1 = (Node) ity.next();
            System.out.println("$$$$$$$$$$$ similar pair x,y = " + x1 + ", " + y1);
            System.out.println("removing edge between x = " + x1 + " and y = " + y1);
            Edge oldxy = this.graph.getEdge(x1, y1);
            this.graph.removeEdge(oldxy);
            this.removedEdges.add(Edges.undirectedEdge(x1, y1));
        }
    }

}






