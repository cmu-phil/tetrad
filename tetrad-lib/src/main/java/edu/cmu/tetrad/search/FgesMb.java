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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TaskManager;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;

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
public final class FgesMb {

    final int[] count = new int[1];

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The top n graphs found by the algorithm, where n is numCPDAGsToStore.
     */
    private final LinkedList<ScoredGraph> topGraphs = new LinkedList<>();
    // The static ForkJoinPool instance.
    private final ForkJoinPool pool = ForkJoinPool.commonPool();
    // Arrows with the same totalScore are stored in this list to distinguish their order in sortedArrows.
    // The ordering doesn't matter; it just have to be transitive.
    int arrowIndex;
    Set<Edge> removedEdges = new HashSet<>();
    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();
    /**
     * List of variables in the data set, in order.
     */
    private List<Node> variables;
    /**
     * The true graph, if known. If this is provided, asterisks will be printed
     * out next to false positive added edges (that is, edges added that aren't
     * adjacencies in the true graph).
     */
    private Graph trueGraph;
    /**
     * An initial graph to start from.
     */
    private Graph externalGraph;
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
    private Score fgesScore;
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
    // Internal.
    private Mode mode = Mode.heuristicSpeedup;
    // Bounds the degree of the graph.
    private int maxDegree = -1;
    /**
     * True if one-edge faithfulness is assumed. Speeds the algorithm up.
     */
    private boolean faithfulnessAssumed = true;

    //===========================CONSTRUCTORS=============================//
    private boolean parallelized = false;

    //==========================PUBLIC METHODS==========================//

    /**
     * Construct a Score and pass it in here. The totalScore should return a
     * positive value in case of conditional dependence and a negative values in
     * case of conditional independence. See Chickering (2002), locally
     * consistent scoring criterion.
     */
    public FgesMb(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        setFgesScore(score);
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

    /**
     * @return true if it is assumed that all path pairs with one length 1 path
     * do not cancel.
     */
    public boolean isFaithfulnessAssumed() {
        return this.faithfulnessAssumed;
    }

    /**
     * Set to true if it is assumed that all path pairs with one length 1 path
     * do not cancel.
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    /**
     * Greedy equivalence search: Start from the empty graph, add edges till
     * model is significant. Then start deleting edges till a minimum is
     * achieved.
     *
     * @return the resulting CPDAG.
     */
    public Graph search(Node target) {
        return search(Collections.singletonList(target));
    }

    public Graph search(List<Node> targets) {

        // Assumes one-edge faithfulness.
        long start = System.currentTimeMillis();

        if (targets == null) {
            throw new NullPointerException();
        }

        for (Node target : targets) {
            if (!this.fgesScore.getVariables().contains(target)) {
                throw new IllegalArgumentException(
                        "Target is not specified."
                );
            }
        }

        this.topGraphs.clear();

        this.lookupArrows = new ConcurrentHashMap<>();
        List<Node> nodes = new ArrayList<>(this.fgesScore.getVariables());

        if (this.adjacencies != null) {
            this.adjacencies = GraphUtils.replaceNodes(this.adjacencies, nodes);
        }

        this.graph = new EdgeListGraph(getVariables());

        this.mode = Mode.heuristicSpeedup;

        calcDConnections(targets);

        // Do forward search.
        fes();
        bes();

        this.mode = Mode.coverNoncolliders;
        initializeTwoStepEdges(getVariables());
        fes();
        bes();

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - start;
        this.logger.log("graph", "\nReturning this graph: " + this.graph);

        this.logger.log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        this.logger.flush();

        Set<Node> mb = new HashSet<>(targets);

        for (Node target : targets) {
            mb.addAll(this.graph.getAdjacentNodes(target));

            for (Node child : this.graph.getChildren(target)) {
                mb.addAll(this.graph.getParents(child));
            }
        }

        Graph mbgraph = this.graph.subgraph(new ArrayList<>(mb));

        storeGraph(mbgraph);

        return mbgraph;
    }

    private void calcDConnections(List<Node> targets) {
        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();

        this.effectEdgesGraph = new EdgeListGraph();

        for (Node target : targets) {
            this.effectEdgesGraph.addNode(target);
        }

        Set<Node> emptySet = new HashSet<>();

        for (Node target : targets) {
            for (Node x : this.fgesScore.getVariables()) {
                if (targets.contains(x)) {
                    continue;
                }

                int child = this.hashIndices.get(target);
                int parent = this.hashIndices.get(x);
                double bump = this.fgesScore.localScoreDiff(parent, child);

                if (bump > 0) {
                    this.effectEdgesGraph.addNode(x);

                    addUnconditionalArrows(x, target, emptySet);

                    class MbAboutNodeTask extends RecursiveTask<Boolean> {

                        public MbAboutNodeTask() {
                        }

                        @Override
                        protected Boolean compute() {
                            Queue<NodeTaskEmptyGraph> tasks = new ArrayDeque<>();

                            for (Node y : FgesMb.this.fgesScore.getVariables()) {
                                if (Thread.currentThread().isInterrupted()) {
                                    break;
                                }

                                if (x == y) {
                                    continue;
                                }

                                MbTask mbTask = new MbTask(x, y, target);
                                mbTask.fork();

                                for (NodeTaskEmptyGraph _task : new ArrayList<>(tasks)) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        break;
                                    }

                                    if (_task.isDone()) {
                                        _task.join();
                                        tasks.remove(_task);
                                    }
                                }

                                while (tasks.size() > Runtime.getRuntime().availableProcessors()) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        break;
                                    }

                                    NodeTaskEmptyGraph _task = tasks.poll();
                                    assert _task != null;
                                    _task.join();
                                }
                            }

                            for (NodeTaskEmptyGraph task : tasks) {
                                if (Thread.currentThread().isInterrupted()) {
                                    break;
                                }

                                task.join();
                            }

                            return true;
                        }
                    }

                    this.pool.invoke(new MbAboutNodeTask());
                }
            }
        }
    }

    private void addUnconditionalArrows(Node x, Node y, Set<Node> emptySet) {
        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                return;
            }

            if (invalidSetByKnowledge(y, emptySet)) {
                return;
            }
        }

        if (this.adjacencies != null && !this.adjacencies.isAdjacentTo(x, y)) {
            return;
        }

        int child = this.hashIndices.get(y);
        int parent = this.hashIndices.get(x);
        double bump = this.fgesScore.localScoreDiff(parent, child);

        Edge edge = Edges.undirectedEdge(x, y);
        this.effectEdgesGraph.addEdge(edge);

        if (bump > 0.0) {
            addArrow(x, y, emptySet, emptySet, bump);
            addArrow(y, x, emptySet, emptySet, bump);
        }
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
     * @param knowledge the knowledge object, specifying forbidden and required
     *                  edges.
     */
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * If the true graph is set, askterisks will be printed in log output for
     * the true edges.
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
     * @return the number of CPDAGs to store.
     */
    public int getnumCPDAGsToStore() {
        return this.numCPDAGsToStore;
    }

    /**
     * Sets the number of CPDAGs to store. This should be set to zero for fast
     * search.
     */
    public void setNumCPDAGsToStore(int numCPDAGsToStore) {
        if (numCPDAGsToStore < 0) {
            throw new IllegalArgumentException("# graphs to store must at least 0: " + numCPDAGsToStore);
        }

        this.numCPDAGsToStore = numCPDAGsToStore;
    }

    /**
     * @return the initial graph for the search. The search is initialized to
     * this graph and proceeds from there.
     */
    public Graph getexternalGraph() {
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
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent
     * to. By detault System.out.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * @return the set of preset adjacenies for the algorithm; edges not in this
     * adjacencies graph will not be added.
     */
    public Graph getAdjacencies() {
        return this.adjacencies;
    }

    /**
     * Sets the set of preset adjacenies for the algorithm; edges not in this
     * adjacencies graph will not be added.
     */
    public void setAdjacencies(Graph adjacencies) {
        this.adjacencies = adjacencies;
    }

    /**
     * A bound on cycle length.
     *
     * @param cycleBound The bound, >= 1, or -1 for unlimited.
     */
    public void setCycleBound(int cycleBound) {
        if (!(cycleBound == -1 || cycleBound >= 1)) {
            throw new IllegalArgumentException("Cycle bound needs to be -1 or >= 1: " + cycleBound);
        }
        this.cycleBound = cycleBound;
    }

    /**
     * Creates a new processors pool with the specified number of threads.
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
     *
     * @deprecated Use the getters on the individual scores instead.
     */
    public double getPenaltyDiscount() {
        if (this.fgesScore instanceof ISemBicScore) {
            return ((ISemBicScore) this.fgesScore).getPenaltyDiscount();
        } else {
            return 2.0;
        }
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
     *
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (this.fgesScore instanceof ISemBicScore) {
            ((ISemBicScore) this.fgesScore).setPenaltyDiscount(penaltyDiscount);
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setSamplePrior(double samplePrior) {
        if (this.fgesScore instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) this.fgesScore).setSamplePrior(samplePrior);
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setStructurePrior(double expectedNumParents) {
        if (this.fgesScore instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) this.fgesScore).setStructurePrior(expectedNumParents);
        }
    }

    /**
     * The maximum of parents any nodes can have in output CPDAG.
     *
     * @return -1 for unlimited.
     */
    public int getMaxDegree() {
        return this.maxDegree;
    }

    /**
     * The maximum of parents any nodes can have in output CPDAG.
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
    private void setFgesScore(Score totalScore) {
        this.fgesScore = totalScore;

        this.variables = new ArrayList<>();

        for (Node node : totalScore.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }

        buildIndexing(totalScore.getVariables());
    }

    public int getMinChunk(int n) {
        // The minimum number of operations to do before parallelizing.
        int minChunk = 100;
        return Math.max(n / Runtime.getRuntime().availableProcessors(), minChunk);
    }

    private void initializeTwoStepEdges(List<Node> nodes) {
        if (this.verbose) {
            System.out.println("heuristicSpeedup = false");
        }

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
                if (TaskManager.getInstance().isCanceled()) {
                    return false;
                }

                if (this.to - this.from <= this.chunk) {
                    for (int i = this.from; i < this.to; i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if ((i + 1) % 1000 == 0) {
                            FgesMb.this.count[0] += 1000;
                            FgesMb.this.out.println("Initializing effect edges: " + (FgesMb.this.count[0]));
                        }

                        Node y = nodes.get(i);

                        Set<Node> g = new HashSet<>();

                        for (Node n : FgesMb.this.graph.getAdjacentNodes(y)) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            for (Node m : FgesMb.this.graph.getAdjacentNodes(n)) {
                                if (Thread.currentThread().isInterrupted()) {
                                    break;
                                }

                                if (FgesMb.this.graph.isAdjacentTo(y, m)) {
                                    continue;
                                }

                                if (FgesMb.this.graph.isDefCollider(m, n, y)) {
                                    continue;
                                }

                                g.add(m);
                            }
                        }

                        for (Node x : g) {
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

                            if (FgesMb.this.adjacencies != null && !FgesMb.this.adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            if (FgesMb.this.removedEdges.contains(Edges.undirectedEdge(x, y))) {
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

        int maxDeg = this.maxDegree == -1 ? 1000 : this.maxDegree;

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

            if (this.graph.getDegree(x) > maxDeg - 1) {
                continue;
            }
            if (this.graph.getDegree(y) > maxDeg - 1) {
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
            if (!inserted) {
                continue;
            }

            this.totalScore += bump;

            Set<Node> visited = reapplyOrientation();
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

            storeGraph(this.graph);
            reevaluateForward(toProcess);
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

            if (!this.graph.isAdjacentTo(x, y)) {
                continue;
            }

            Edge edge = this.graph.getEdge(x, y);
            if (edge.pointsTowards(x)) {
                continue;
            }

            if (!validDelete(x, y, arrow.getHOrT(), arrow.getNaYX())) {
                continue;
            }

            Set<Node> H = arrow.getHOrT();
            double bump = arrow.getBump();

            boolean deleted = delete(x, y, H, bump, arrow.getNaYX());
            if (!deleted) {
                continue;
            }

            this.totalScore += bump;

            clearArrow(x, y);

            Set<Node> visited = reapplyOrientation();

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

            storeGraph(this.graph);
            reevaluateBackward(toProcess);
        }

        meekOrientRestricted(getKnowledge());
    }

    private Set<Node> getCommonAdjacents(Node x, Node y) {
        Set<Node> commonChildren = new HashSet<>(this.graph.getAdjacentNodes(x));
        commonChildren.retainAll(this.graph.getAdjacentNodes(y));
        return commonChildren;
    }

    private Set<Node> reapplyOrientation() {
        return meekOrientRestricted(getKnowledge());
    }

    // Returns true if knowledge is not empty.
    private boolean existsKnowledge() {
        return !this.knowledge.isEmpty();
    }

    // Initiaizes the sorted arrows lists for the backward search.
    private void initializeArrowsBackward() {
        for (Edge edge : this.graph.getEdges()) {
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
    private void reevaluateForward(Set<Node> nodes) {
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
                        Node x = this.nodes.get(_w);

                        List<Node> adj;

                        if (FgesMb.this.mode == Mode.heuristicSpeedup) {
                            adj = FgesMb.this.effectEdgesGraph.getAdjacentNodes(x);
                        } else if (FgesMb.this.mode == Mode.coverNoncolliders) {
                            Set<Node> g = new HashSet<>();

                            for (Node n : FgesMb.this.graph.getAdjacentNodes(x)) {
                                for (Node m : FgesMb.this.graph.getAdjacentNodes(n)) {
                                    if (FgesMb.this.graph.isAdjacentTo(x, m)) {
                                        continue;
                                    }

                                    if (FgesMb.this.graph.isDefCollider(m, n, x)) {
                                        continue;
                                    }

                                    g.add(m);
                                }
                            }

                            adj = new ArrayList<>(g);
                        } else if (FgesMb.this.mode == Mode.allowUnfaithfulness) {
                            HashSet<Node> D = new HashSet<>(
                                    GraphUtils.getDconnectedVars(x, new ArrayList<>(), FgesMb.this.graph));
                            D.remove(x);
                            adj = new ArrayList<>(D);
                        } else {
                            throw new IllegalStateException();
                        }

                        for (Node w : adj) {
                            if (FgesMb.this.adjacencies != null && !(FgesMb.this.adjacencies.isAdjacentTo(w, x))) {
                                continue;
                            }

                            if (w == x) {
                                continue;
                            }

                            if (!FgesMb.this.graph.isAdjacentTo(w, x)) {
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
        if (this.mode == Mode.heuristicSpeedup && !this.effectEdgesGraph.isAdjacentTo(a, b)) {
            return;
        }
        if (this.adjacencies != null && !this.adjacencies.isAdjacentTo(a, b)) {
            return;
        }
        this.neighbors.put(b, getNeighbors(b));

        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = getNaYX(a, b);
        if (!GraphUtils.isClique(naYX, this.graph)) {
            return;
        }

        List<Node> TNeighbors = getTNeighbors(a, b);
        int _maxDegree = this.maxDegree == -1 ? 1000 : this.maxDegree;

        int _max = Math.min(TNeighbors.size(), _maxDegree - this.graph.getIndegree(b));

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

                if (!GraphUtils.isClique(union, this.graph)) {
                    continue;
                }
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
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        Node w = this.adj.get(_w);
                        Edge e = FgesMb.this.graph.getEdge(w, this.r);

                        if (e != null) {
                            if (e.pointsTowards(this.r)) {
                                clearArrow(w, this.r);
                                clearArrow(this.r, w);

                                calculateArrowsBackward(w, this.r);
                            } else if (Edges.isUndirectedEdge(FgesMb.this.graph.getEdge(w, this.r))) {
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
                    if (invalidSetByKnowledge(b, h)) {
                        continue;
                    }
                }

                double bump = deleteEval(a, b, diff, this.hashIndices);

                if (bump > 0.0) {
                    addArrow(a, b, naYX, h, bump);
                }
            }
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
    private double deleteEval(Node x, Node y, Set<Node> diff,
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

        this.graph.addDirectedEdge(x, y);

        if (this.verbose) {
            String label = this.trueGraph != null && trueEdge != null ? "*" : "";
            TetradLogger.getInstance().log("insertedEdges", this.graph.getNumEdges() + ". INSERT " + this.graph.getEdge(x, y)
                    + " " + T + " " + bump + " " + label);
        }

        int numEdges = this.graph.getNumEdges();

        if (this.verbose) {
            if (numEdges % 1000 == 0) {
                this.out.println("Num edges added: " + numEdges);
            }
        }

        if (this.verbose) {
            String label = this.trueGraph != null && trueEdge != null ? "*" : "";
            this.out.println(this.graph.getNumEdges() + ". INSERT " + this.graph.getEdge(x, y)
                    + " " + T + " " + bump + " " + label
                    + " degree = " + GraphUtils.getDegree(this.graph)
                    + " indegree = " + GraphUtils.getIndegree(this.graph));
        }

        for (Node _t : T) {
            this.graph.removeEdge(_t, y);

            this.graph.addDirectedEdge(_t, y);

            if (this.verbose) {
                String message = "--- Directing " + this.graph.getEdge(_t, y);
                TetradLogger.getInstance().log("directedEdges", message);
                this.out.println(message);
            }
        }

        return true;
    }

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

        if (this.verbose) {
            int numEdges = this.graph.getNumEdges();
            if (numEdges % 1000 == 0) {
                this.out.println("Num edges (backwards) = " + numEdges);
            }

            if (this.verbose) {
                String label = this.trueGraph != null && trueEdge != null ? "*" : "";
                String message = (this.graph.getNumEdges()) + ". DELETE " + x + "-->" + y
                        + " H = " + H + " NaYX = " + naYX + " diff = " + diff + " (" + bump + ") " + label;
                TetradLogger.getInstance().log("deletedEdges", message);
                this.out.println(message);
            }
        }

        for (Node h : H) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (this.graph.isParentOf(h, y) || this.graph.isParentOf(h, x)) {
                continue;
            }

            Edge oldyh = this.graph.getEdge(y, h);

            this.graph.removeEdge(oldyh);

            this.graph.addEdge(Edges.directedEdge(y, h));

            if (this.verbose) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldyh + " to "
                        + this.graph.getEdge(y, h));
                this.out.println("--- Directing " + oldyh + " to " + this.graph.getEdge(y, h));
            }

            Edge oldxh = this.graph.getEdge(x, h);

            if (Edges.isUndirectedEdge(oldxh)) {
                this.graph.removeEdge(oldxh);

                this.graph.addEdge(Edges.directedEdge(x, h));

                if (this.verbose) {
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldxh + " to "
                            + this.graph.getEdge(x, h));
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
        List<Node> adj = this.graph.getAdjacentNodes(y);
        Set<Node> nayx = new HashSet<>();

        for (Node z : adj) {
            if (z == x) {
                continue;
            }
            Edge yz = this.graph.getEdge(y, z);
            if (!Edges.isUndirectedEdge(yz)) {
                continue;
            }
            if (!this.graph.isAdjacentTo(z, x)) {
                continue;
            }
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
                if (distance > (bound == -1 ? 1000 : bound)) {
                    return false;
                }
            }

            for (Node u : this.graph.getAdjacentNodes(t)) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Edge edge = this.graph.getEdge(t, u);
                Node c = FgesMb.traverseSemiDirected(t, edge);
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

    // Runs Meek rules on just the changed adj.
    private Set<Node> meekOrientRestricted(IKnowledge knowledge) {
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

    /**
     * Scores the given DAG, up to a constant.
     */
    public double scoreDag(Graph dag) {
        buildIndexing(dag.getNodes());

        double _score = 0.0;

        for (Node y : dag.getNodes()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Set<Node> parents = new HashSet<>(dag.getParents(y));
            int[] parentIndices = new int[parents.size()];
            Iterator<Node> pi = parents.iterator();
            int count = 0;

            while (pi.hasNext()) {
                Node nextParent = pi.next();
                parentIndices[count++] = this.hashIndices.get(nextParent);
            }

            int yIndex = this.hashIndices.get(y);
            _score += this.fgesScore.localScore(yIndex, parentIndices);
        }

        return _score;
    }

    private double scoreGraphChange(Node y, Set<Node> parents,
                                    Node x, Map<Node, Integer> hashIndices) {
        int yIndex = hashIndices.get(y);

        if (parents.contains(x)) {
            return Double.NaN;//throw new IllegalArgumentException();
        }
        int[] parentIndices = new int[parents.size()];

        int count = 0;
        for (Node parent : parents) {
            parentIndices[count++] = hashIndices.get(parent);
        }

        return this.fgesScore.localScoreDiff(hashIndices.get(x), yIndex, parentIndices);
    }

    private List<Node> getVariables() {
        return this.variables;
    }

    // Stores the graph, if its totalScore knocks out one of the top ones.
    private void storeGraph(Graph graph) {
        if (getnumCPDAGsToStore() > 0) {
            Graph graphCopy = new EdgeListGraph(graph);
            this.topGraphs.addLast(new ScoredGraph(graphCopy, this.totalScore));
        }

        if (this.topGraphs.size() == getnumCPDAGsToStore() + 1) {
            this.topGraphs.removeFirst();
        }
    }

    //===========================SCORING METHODS===================//

    /**
     * Internal.
     */
    private enum Mode {
        allowUnfaithfulness, heuristicSpeedup, coverNoncolliders
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
        public int compareTo(@NotNull Arrow arrow) {
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

    class MbTask extends RecursiveTask<Boolean> {

        Node x;
        Node y;
        Node target;
        Set<Node> emptySet = new HashSet<>();

        public MbTask(Node x, Node y, Node target) {
            this.x = x;
            this.y = y;
            this.target = target;
        }

        @Override
        protected Boolean compute() {
            if (!FgesMb.this.effectEdgesGraph.isAdjacentTo(this.x, this.y) && !FgesMb.this.effectEdgesGraph.isAdjacentTo(this.y, this.target)) {
                int child2 = FgesMb.this.hashIndices.get(this.x);
                int parent2 = FgesMb.this.hashIndices.get(this.y);

                double bump2 = FgesMb.this.fgesScore.localScoreDiff(parent2, child2);

                if (bump2 > 0) {
                    FgesMb.this.effectEdgesGraph.addNode(this.y);
                    addUnconditionalArrows(this.x, this.y, this.emptySet);
                }
            }

            return true;
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
            for (int i = this.from; i < this.to; i++) {
                if ((i + 1) % 1000 == 0) {
                    FgesMb.this.count[0] += 1000;
                    FgesMb.this.out.println("Initializing effect edges: " + (FgesMb.this.count[0]));
                }

                Node y = this.nodes.get(i);
                FgesMb.this.neighbors.put(y, this.emptySet);

                for (int j = i + 1; j < this.nodes.size(); j++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    Node x = this.nodes.get(j);

                    if (existsKnowledge()) {
                        if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                            continue;
                        }

                        if (invalidSetByKnowledge(y, this.emptySet)) {
                            continue;
                        }
                    }

                    if (FgesMb.this.adjacencies != null && !FgesMb.this.adjacencies.isAdjacentTo(x, y)) {
                        continue;
                    }

                    int child = FgesMb.this.hashIndices.get(y);
                    int parent = FgesMb.this.hashIndices.get(x);
                    double bump = FgesMb.this.fgesScore.localScoreDiff(parent, child);

                    if (bump > 0) {
                        Edge edge = Edges.undirectedEdge(x, y);
                        FgesMb.this.effectEdgesGraph.addEdge(edge);
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

}
