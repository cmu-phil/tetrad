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

    private List<Node> targets;

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
     * If non-null, edges not adjacent in this graph will not be added.
     */
    private Graph boundGraph;

    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    /**
     * The depth of search for the forward reevaluation step.
     */
    private final int depth = -1;

    /**
     * A bound on cycle length.
     */
    private int cycleBound = -1;

    /**
     * The totalScore for discrete searches.
     */
    private Score fgesScore;

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

    // The minimum number of operations to do before parallelizing.
    private final int minChunk = 100;

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

    // Bounds the degree of the graph.
    private int maxDegree = -1;

    /**
     * True if one-edge faithfulness is assumed. Speeds the algorithm up.
     */
    private boolean faithfulnessAssumed = true;

    final int maxThreads = ForkJoinPoolInstance.getInstance().getPool().getParallelism();

    //===========================CONSTRUCTORS=============================//

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
        this.setFgesScore(score);
        graph = new EdgeListGraph(this.getVariables());
    }

    //==========================PUBLIC METHODS==========================//

    /**
     * Set to true if it is assumed that all path pairs with one length 1 path
     * do not cancel.
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = true;
    }

    /**
     * @return true if it is assumed that all path pairs with one length 1 path
     * do not cancel.
     */
    public boolean isFaithfulnessAssumed() {
        return faithfulnessAssumed;
    }

    /**
     * Greedy equivalence search: Start from the empty graph, add edges till
     * model is significant. Then start deleting edges till a minimum is
     * achieved.
     *
     * @return the resulting CPDAG.
     */
//    public Graph search() {
//        topGraphs.clear();
//
//        lookupArrows = new ConcurrentHashMap<>();
//        final List<Node> nodes = new ArrayList<>(variables);
//        graph = new EdgeListGraph(nodes);
//
//        if (adjacencies != null) {
//            adjacencies = GraphUtils.replaceNodes(adjacencies, nodes);
//        }
//
//        if (externalGraph != null) {
//            graph = new EdgeListGraph(externalGraph);
//            graph = GraphUtils.replaceNodes(graph, nodes);
//        }
//
//        addRequiredEdges(graph);
//
//        if (faithfulnessAssumed) {
//            initializeForwardEdgesFromEmptyGraph(getVariable());
//
//            // Do forward search.
//            this.mode = Mode.heuristicSpeedup;
//            fes();
//            bes();
//
//            this.mode = Mode.coverNoncolliders;
//            initializeTwoStepEdges(getVariable());
//            fes();
//            bes();
//        } else {
//            initializeForwardEdgesFromEmptyGraph(getVariable());
//
//            // Do forward search.
//            this.mode = Mode.heuristicSpeedup;
//            fes();
//            bes();
//
//            this.mode = Mode.allowUnfaithfulness;
//            initializeForwardEdgesFromExistingGraph(getVariable());
//            fes();
//            bes();
//        }
//
//        long start = System.currentTimeMillis();
//        totalScore = 0.0;
//
//        long endTime = System.currentTimeMillis();
//        this.elapsedTime = endTime - start;
//        this.logger.log("graph", "\nReturning this graph: " + graph);
//
//        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
//        this.logger.flush();
//
//        this.modelScore = totalScore;
//
//        return graph;
//    }
    public Graph search(Node target) {
        return this.search(Collections.singletonList(target));
    }

    public Graph search(List<Node> targets) {

        // Assumes one-edge faithfulness.
        long start = System.currentTimeMillis();
        modelScore = 0.0;

        if (targets == null) {
            throw new NullPointerException();
        }

        for (Node target : targets) {
            if (!fgesScore.getVariables().contains(target)) {
                throw new IllegalArgumentException(
                        "Target is not specified."
                );
            }
        }

        this.targets = targets;

        topGraphs.clear();

        lookupArrows = new ConcurrentHashMap<>();
        List<Node> nodes = new ArrayList<>(fgesScore.getVariables());

        if (adjacencies != null) {
            adjacencies = GraphUtils.replaceNodes(adjacencies, nodes);
        }

        graph = new EdgeListGraph(this.getVariables());

        mode = Mode.heuristicSpeedup;

        this.calcDConnections(targets);

        // Do forward search.
        this.fes();
        this.bes();

        mode = Mode.coverNoncolliders;
        this.initializeTwoStepEdges(this.getVariables());
        this.fes();
        this.bes();

        long endTime = System.currentTimeMillis();
        elapsedTime = endTime - start;
        logger.log("graph", "\nReturning this graph: " + graph);

        logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        logger.flush();

        Set<Node> mb = new HashSet<>();
        mb.addAll(targets);

        for (Node target : targets) {
            mb.addAll(graph.getAdjacentNodes(target));

            for (Node child : graph.getChildren(target)) {
                mb.addAll(graph.getParents(child));
            }
        }

        Graph mbgraph = graph.subgraph(new ArrayList<>(mb));

        this.storeGraph(mbgraph);

        return mbgraph;
    }

    private void calcDConnections(List<Node> targets) {
        sortedArrows = new ConcurrentSkipListSet<>();
        lookupArrows = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();
        List<Node> nodes = fgesScore.getVariables();

        effectEdgesGraph = new EdgeListGraph();

        for (Node target : targets) {
            effectEdgesGraph.addNode(target);
        }

        Set emptySet = new HashSet();

        for (Node target : targets) {
            for (Node x : fgesScore.getVariables()) {
                if (targets.contains(x)) {
                    continue;
                }

                int child = hashIndices.get(target);
                int parent = hashIndices.get(x);
                double bump = fgesScore.localScoreDiff(parent, child);

                if (bump > 0) {
                    synchronized (effectEdgesGraph) {
                        effectEdgesGraph.addNode(x);
                    }

                    this.addUnconditionalArrows(x, target, emptySet);

                    class MbAboutNodeTask extends RecursiveTask<Boolean> {

                        public MbAboutNodeTask() {
                        }

                        @Override
                        protected Boolean compute() {
                            Queue<NodeTaskEmptyGraph> tasks = new ArrayDeque<>();

                            for (Node y : fgesScore.getVariables()) {
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

                                while (tasks.size() > maxThreads) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        break;
                                    }

                                    NodeTaskEmptyGraph _task = tasks.poll();
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

                    pool.invoke(new MbAboutNodeTask());
                }
            }
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
            if (!effectEdgesGraph.isAdjacentTo(x, y) && !effectEdgesGraph.isAdjacentTo(y, target)) {
                int child2 = hashIndices.get(x);
                int parent2 = hashIndices.get(y);

                double bump2 = fgesScore.localScoreDiff(parent2, child2);

                if (bump2 > 0) {
                    synchronized (effectEdgesGraph) {
                        effectEdgesGraph.addNode(y);
                    }

                    FgesMb.this.addUnconditionalArrows(x, y, emptySet);
                }
            }

            return true;
        }
    }

    private void addUnconditionalArrows(Node x, Node y, Set emptySet) {
        if (this.existsKnowledge()) {
            if (this.getKnowledge().isForbidden(x.getName(), y.getName()) && this.getKnowledge().isForbidden(y.getName(), x.getName())) {
                return;
            }

            if (!this.validSetByKnowledge(y, emptySet)) {
                return;
            }
        }

        if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
            return;
        }

        int child = hashIndices.get(y);
        int parent = hashIndices.get(x);
        double bump = fgesScore.localScoreDiff(parent, child);

        if (boundGraph != null && !boundGraph.isAdjacentTo(x, y)) {
            return;
        }

        Edge edge = Edges.undirectedEdge(x, y);
        effectEdgesGraph.addEdge(edge);

        if (bump > 0.0) {
            this.addArrow(x, y, emptySet, emptySet, bump);
            this.addArrow(y, x, emptySet, emptySet, bump);
        }
    }

    /**
     * @return the background knowledge.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
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
        return elapsedTime;
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
        return this.scoreDag(dag);
    }

    /**
     * @return the list of top scoring graphs.
     */
    public LinkedList<ScoredGraph> getTopGraphs() {
        return topGraphs;
    }

    /**
     * @return the number of CPDAGs to store.
     */
    public int getnumCPDAGsToStore() {
        return numCPDAGsToStore;
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
        return externalGraph;
    }

    /**
     * Sets the initial graph.
     */
    public void setExternalGraph(Graph externalGraph) {
        if (externalGraph != null) {
            externalGraph = GraphUtils.replaceNodes(externalGraph, variables);

            if (verbose) {
                out.println("Initial graph variables: " + externalGraph.getNodes());
                out.println("Data set variables: " + variables);
            }

            if (!new HashSet<>(externalGraph.getNodes()).equals(new HashSet<>(variables))) {
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
     * Sets the output stream that output (except for log output) should be sent
     * to. By detault System.out.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * @return the set of preset adjacenies for the algorithm; edges not in this
     * adjacencies graph will not be added.
     */
    public Graph getAdjacencies() {
        return adjacencies;
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
     */
    public int getCycleBound() {
        return cycleBound;
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
    public void setParallelism(int numProcessors) {
        pool = new ForkJoinPool(numProcessors);
    }

    /**
     * If non-null, edges not adjacent in this graph will not be added.
     */
    public void setBoundGraph(Graph boundGraph) {
        this.boundGraph = GraphUtils.replaceNodes(boundGraph, this.getVariables());
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
     *
     * @deprecated Use the getters on the individual scores instead.
     */
    public double getPenaltyDiscount() {
        if (fgesScore instanceof ISemBicScore) {
            return ((ISemBicScore) fgesScore).getPenaltyDiscount();
        } else {
            return 2.0;
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setSamplePrior(double samplePrior) {
        if (fgesScore instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) fgesScore).setSamplePrior(samplePrior);
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setStructurePrior(double expectedNumParents) {
        if (fgesScore instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) fgesScore).setStructurePrior(expectedNumParents);
        }
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
     *
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (fgesScore instanceof ISemBicScore) {
            ((ISemBicScore) fgesScore).setPenaltyDiscount(penaltyDiscount);
        }
    }

    /**
     * The maximum of parents any nodes can have in output CPDAG.
     *
     * @return -1 for unlimited.
     */
    public int getMaxDegree() {
        return maxDegree;
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
        fgesScore = totalScore;

        variables = new ArrayList<>();

        for (Node node : totalScore.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                variables.add(node);
            }
        }

        this.buildIndexing(totalScore.getVariables());
    }

    final int[] count = new int[1];

    public int getMinChunk(int n) {
        return Math.max(n / maxThreads, minChunk);
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
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    Node x = nodes.get(j);

                    if (FgesMb.this.existsKnowledge()) {
                        if (FgesMb.this.getKnowledge().isForbidden(x.getName(), y.getName()) && FgesMb.this.getKnowledge().isForbidden(y.getName(), x.getName())) {
                            continue;
                        }

                        if (!FgesMb.this.validSetByKnowledge(y, emptySet)) {
                            continue;
                        }
                    }

                    if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
                        continue;
                    }

                    int child = hashIndices.get(y);
                    int parent = hashIndices.get(x);
                    double bump = fgesScore.localScoreDiff(parent, child);

                    if (boundGraph != null && !boundGraph.isAdjacentTo(x, y)) {
                        continue;
                    }

                    if (bump > 0) {
                        Edge edge = Edges.undirectedEdge(x, y);
                        effectEdgesGraph.addEdge(edge);
                    }

                    if (bump > 0.0) {
                        FgesMb.this.addArrow(x, y, emptySet, emptySet, bump);
                        FgesMb.this.addArrow(y, x, emptySet, emptySet, bump);
                    }
                }
            }

            return true;
        }
    }

    private void initializeForwardEdgesFromEmptyGraph(List<Node> nodes) {
        if (verbose) {
            System.out.println("heuristicSpeedup = true");
        }

        sortedArrows = new ConcurrentSkipListSet<>();
        lookupArrows = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();
        Set<Node> emptySet = new HashSet<>();

        long start = System.currentTimeMillis();
        effectEdgesGraph = new EdgeListGraph(nodes);

        class InitializeFromEmptyGraphTask extends RecursiveTask<Boolean> {

            public InitializeFromEmptyGraphTask() {
            }

            @Override
            protected Boolean compute() {
                Queue<NodeTaskEmptyGraph> tasks = new ArrayDeque<>();

                int numNodesPerTask = Math.max(100, nodes.size() / maxThreads);

                for (int i = 0; i < nodes.size(); i += numNodesPerTask) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    NodeTaskEmptyGraph task = new NodeTaskEmptyGraph(i, Math.min(nodes.size(), i + numNodesPerTask),
                            nodes, emptySet);
                    tasks.add(task);
                    task.fork();

                    for (NodeTaskEmptyGraph _task : new ArrayList<>(tasks)) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if (_task.isDone()) {
                            _task.join();
                            tasks.remove(_task);
                        }
                    }

                    while (tasks.size() > maxThreads) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        NodeTaskEmptyGraph _task = tasks.poll();
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

        pool.invoke(new InitializeFromEmptyGraphTask());

        long stop = System.currentTimeMillis();

        if (verbose) {
            out.println("Elapsed initializeForwardEdgesFromEmptyGraph = " + (stop - start) + " ms");
        }
    }

    private void initializeTwoStepEdges(List<Node> nodes) {
        if (verbose) {
            System.out.println("heuristicSpeedup = false");
        }

        count[0] = 0;

        sortedArrows = new ConcurrentSkipListSet<>();
        lookupArrows = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();

        if (effectEdgesGraph == null) {
            effectEdgesGraph = new EdgeListGraph(nodes);
        }

        if (externalGraph != null) {
            for (Edge edge : externalGraph.getEdges()) {
                if (!effectEdgesGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    effectEdgesGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
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

                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if ((i + 1) % 1000 == 0) {
                            count[0] += 1000;
                            out.println("Initializing effect edges: " + (count[0]));
                        }

                        Node y = nodes.get(i);

                        Set<Node> g = new HashSet<>();

                        for (Node n : graph.getAdjacentNodes(y)) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            for (Node m : graph.getAdjacentNodes(n)) {
                                if (Thread.currentThread().isInterrupted()) {
                                    break;
                                }

                                if (graph.isAdjacentTo(y, m)) {
                                    continue;
                                }

                                if (graph.isDefCollider(m, n, y)) {
                                    continue;
                                }

                                g.add(m);
                            }
                        }

                        for (Node x : g) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            if (FgesMb.this.existsKnowledge()) {
                                if (FgesMb.this.getKnowledge().isForbidden(x.getName(), y.getName()) && FgesMb.this.getKnowledge().isForbidden(y.getName(), x.getName())) {
                                    continue;
                                }

                                if (!FgesMb.this.validSetByKnowledge(y, emptySet)) {
                                    continue;
                                }
                            }

                            if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            if (removedEdges.contains(Edges.undirectedEdge(x, y))) {
                                continue;
                            }

                            FgesMb.this.calculateArrowsForward(x, y);
                        }
                    }

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

        pool.invoke(new InitializeFromExistingGraphTask(this.getMinChunk(nodes.size()), 0, nodes.size()));
    }

    private void initializeForwardEdgesFromExistingGraph(List<Node> nodes) {
        if (verbose) {
            System.out.println("heuristicSpeedup = false");
        }

        count[0] = 0;

        sortedArrows = new ConcurrentSkipListSet<>();
        lookupArrows = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();

        if (effectEdgesGraph == null) {
            effectEdgesGraph = new EdgeListGraph(nodes);
        }

        if (externalGraph != null) {
            for (Edge edge : externalGraph.getEdges()) {
                if (!effectEdgesGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    effectEdgesGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
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

                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if ((i + 1) % 1000 == 0) {
                            count[0] += 1000;
                            out.println("Initializing effect edges: " + (count[0]));
                        }

                        Node y = nodes.get(i);
                        Set<Node> D = new HashSet<>();
                        List<Node> cond = new ArrayList<>();
                        D.addAll(GraphUtils.getDconnectedVars(y, cond, graph));
                        D.remove(y);
                        D.removeAll(effectEdgesGraph.getAdjacentNodes(y));

                        for (Node x : D) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            if (FgesMb.this.existsKnowledge()) {
                                if (FgesMb.this.getKnowledge().isForbidden(x.getName(), y.getName()) && FgesMb.this.getKnowledge().isForbidden(y.getName(), x.getName())) {
                                    continue;
                                }

                                if (!FgesMb.this.validSetByKnowledge(y, emptySet)) {
                                    continue;
                                }
                            }

                            if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            FgesMb.this.calculateArrowsForward(x, y);
                        }
                    }

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

        pool.invoke(new InitializeFromExistingGraphTask(this.getMinChunk(nodes.size()), 0, nodes.size()));
    }

    private void fes() {
        TetradLogger.getInstance().log("info", "** FORWARD EQUIVALENCE SEARCH");

        int maxDeg = maxDegree == -1 ? 1000 : maxDegree;

        while (!sortedArrows.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (graph.isAdjacentTo(x, y)) {
                continue;
            }

            if (graph.getDegree(x) > maxDeg - 1) {
                continue;
            }
            if (graph.getDegree(y) > maxDeg - 1) {
                continue;
            }

            if (!arrow.getNaYX().equals(this.getNaYX(x, y))) {
                continue;
            }

            if (!this.getTNeighbors(x, y).containsAll(arrow.getHOrT())) {
                continue;
            }

            if (!this.validInsert(x, y, arrow.getHOrT(), this.getNaYX(x, y))) {
                continue;
            }

            Set<Node> T = arrow.getHOrT();
            double bump = arrow.getBump();

            boolean inserted = this.insert(x, y, T, bump);
            if (!inserted) {
                continue;
            }

            totalScore += bump;

            Set<Node> visited = this.reapplyOrientation(x, y, null);
            Set<Node> toProcess = new HashSet<>();

            for (Node node : visited) {
                Set<Node> neighbors1 = this.getNeighbors(node);
                Set<Node> storedNeighbors = neighbors.get(node);

                if (!(neighbors1.equals(storedNeighbors))) {
                    toProcess.add(node);
                }
            }

            toProcess.add(x);
            toProcess.add(y);

            this.storeGraph(graph);
            this.reevaluateForward(toProcess, arrow);
        }
    }

    private void bes() {
        TetradLogger.getInstance().log("info", "** BACKWARD EQUIVALENCE SEARCH");

        sortedArrows = new ConcurrentSkipListSet<>();
        lookupArrows = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();

        this.initializeArrowsBackward();

        while (!sortedArrows.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (!arrow.getNaYX().equals(this.getNaYX(x, y))) {
                continue;
            }

            if (!graph.isAdjacentTo(x, y)) {
                continue;
            }

            Edge edge = graph.getEdge(x, y);
            if (edge.pointsTowards(x)) {
                continue;
            }

            HashSet<Node> diff = new HashSet<>(arrow.getNaYX());
            diff.removeAll(arrow.getHOrT());

            if (!this.validDelete(x, y, arrow.getHOrT(), arrow.getNaYX())) {
                continue;
            }

            Set<Node> H = arrow.getHOrT();
            double bump = arrow.getBump();

            boolean deleted = this.delete(x, y, H, bump, arrow.getNaYX());
            if (!deleted) {
                continue;
            }

            totalScore += bump;

            this.clearArrow(x, y);

            Set<Node> visited = this.reapplyOrientation(x, y, H);

            Set<Node> toProcess = new HashSet<>();

            for (Node node : visited) {
                Set<Node> neighbors1 = this.getNeighbors(node);
                Set<Node> storedNeighbors = neighbors.get(node);

                if (!(neighbors1.equals(storedNeighbors))) {
                    toProcess.add(node);
                }
            }

            toProcess.add(x);
            toProcess.add(y);
            toProcess.addAll(this.getCommonAdjacents(x, y));

            this.storeGraph(graph);
            this.reevaluateBackward(toProcess);
        }

        this.meekOrientRestricted(this.getVariables(), this.getKnowledge());
    }

    private Set<Node> getCommonAdjacents(Node x, Node y) {
        Set<Node> commonChildren = new HashSet<>(graph.getAdjacentNodes(x));
        commonChildren.retainAll(graph.getAdjacentNodes(y));
        return commonChildren;
    }

    private Set<Node> reapplyOrientation(Node x, Node y, Set<Node> newArrows) {
        Set<Node> toProcess = new HashSet<>();
        toProcess.add(x);
        toProcess.add(y);

        if (newArrows != null) {
            toProcess.addAll(newArrows);
        }

        return this.meekOrientRestricted(new ArrayList<>(toProcess), this.getKnowledge());
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

            if (this.existsKnowledge()) {
                if (!this.getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                    continue;
                }
            }

            this.clearArrow(x, y);
            this.clearArrow(y, x);

            if (edge.pointsTowards(y)) {
                this.calculateArrowsBackward(x, y);
            } else if (edge.pointsTowards(x)) {
                this.calculateArrowsBackward(y, x);
            } else {
                this.calculateArrowsBackward(x, y);
                this.calculateArrowsBackward(y, x);
            }

            neighbors.put(x, this.getNeighbors(x));
            neighbors.put(y, this.getNeighbors(y));
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
                            HashSet<Node> D = new HashSet<>();
                            D.addAll(GraphUtils.getDconnectedVars(x, new ArrayList<Node>(), graph));
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
                                FgesMb.this.clearArrow(w, x);
                                FgesMb.this.calculateArrowsForward(w, x);
                            }
                        }
                    }

                    return true;
                } else {
                    int mid = (to - from) / 2;

                    List<AdjTask> tasks = new ArrayList<>();

                    tasks.add(new AdjTask(chunk, nodes, from, from + mid));
                    tasks.add(new AdjTask(chunk, nodes, from + mid, to));

                    ForkJoinTask.invokeAll(tasks);

                    return true;
                }
            }
        }

        AdjTask task = new AdjTask(this.getMinChunk(nodes.size()), new ArrayList<>(nodes), 0, nodes.size());
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
        neighbors.put(b, this.getNeighbors(b));

        if (this.existsKnowledge()) {
            if (this.getKnowledge().isForbidden(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = this.getNaYX(a, b);
        if (!GraphUtils.isClique(naYX, graph)) {
            return;
        }

        List<Node> TNeighbors = this.getTNeighbors(a, b);
        int _maxDegree = maxDegree == -1 ? 1000 : maxDegree;

        int _max = Math.min(TNeighbors.size(), _maxDegree - graph.getIndegree(b));

        Set<Set<Node>> previousCliques = new HashSet<>();
        previousCliques.add(new HashSet<Node>());
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

                if (!GraphUtils.isClique(union, graph)) {
                    continue;
                }
                newCliques.add(union);

                double bump = this.insertEval(a, b, T, naYX, hashIndices);

                if (bump > 0.0) {
                    this.addArrow(a, b, naYX, T, bump);
                }

//                if (mode == Mode.heuristicSpeedup && union.isEmpty() && score.isEffectEdge(bump) &&
//                        !effectEdgesGraph.isAdjacentTo(a, b) && graph.getParents(b).isEmpty()) {
//                    effectEdgesGraph.addUndirectedEdge(a, b);
//                }
            }

            previousCliques = newCliques;
            newCliques = new HashSet<>();
        }
    }

    private void addArrow(Node a, Node b, Set<Node> naYX, Set<Node> hOrT, double bump) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, naYX, arrowIndex++);
        sortedArrows.add(arrow);
        this.addLookupArrow(a, b, arrow);
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
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        Node w = adj.get(_w);
                        Edge e = graph.getEdge(w, r);

                        if (e != null) {
                            if (e.pointsTowards(r)) {
                                FgesMb.this.clearArrow(w, r);
                                FgesMb.this.clearArrow(r, w);

                                FgesMb.this.calculateArrowsBackward(w, r);
                            } else if (Edges.isUndirectedEdge(graph.getEdge(w, r))) {
                                FgesMb.this.clearArrow(w, r);
                                FgesMb.this.clearArrow(r, w);

                                FgesMb.this.calculateArrowsBackward(w, r);
                                FgesMb.this.calculateArrowsBackward(r, w);
                            }
                        }
                    }

                    return true;
                } else {
                    int mid = (to - from) / 2;

                    List<BackwardTask> tasks = new ArrayList<>();

                    tasks.add(new BackwardTask(r, adj, chunk, from, from + mid, hashIndices));
                    tasks.add(new BackwardTask(r, adj, chunk, from + mid, to, hashIndices));

                    ForkJoinTask.invokeAll(tasks);

                    return true;
                }
            }
        }

        for (Node r : toProcess) {
            neighbors.put(r, this.getNeighbors(r));
            List<Node> adjacentNodes = graph.getAdjacentNodes(r);
            pool.invoke(new BackwardTask(r, adjacentNodes, this.getMinChunk(adjacentNodes.size()), 0,
                    adjacentNodes.size(), hashIndices));
        }
    }

    // Calculates the arrows for the removal in the backward direction.
    private void calculateArrowsBackward(Node a, Node b) {
        if (this.existsKnowledge()) {
            if (!this.getKnowledge().noEdgeRequired(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = this.getNaYX(a, b);

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

                if (this.existsKnowledge()) {
                    if (!this.validSetByKnowledge(b, h)) {
                        continue;
                    }
                }

                double bump = this.deleteEval(a, b, diff, naYX, hashIndices);

                if (bump > 0.0) {
                    this.addArrow(a, b, naYX, h, bump);
                }
            }
        }
    }

    public double getModelScore() {
        return modelScore;
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
        private int index;

        public Arrow(double bump, Node a, Node b, Set<Node> hOrT, Set<Node> naYX, int index) {
            this.bump = bump;
            this.a = a;
            this.b = b;
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

        public Set<Node> getHOrT() {
            return hOrT;
        }

        public Set<Node> getNaYX() {
            return naYX;
        }

        // Sorting by bump, high to low. The problem is the SortedSet contains won't add a new element if it compares
        // to zero with an existing element, so for the cases where the comparison is to zero (i.e. have the same
        // bump, we need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables.
        // The fastest way to do this is using a hash code, though it's still possible for two Arrows to have the
        // same hash code but not be equal. If we're paranoid, in this case we calculate a determinate comparison
        // not equal to zero by keeping a list. This last part is commened out by default.
        public int compareTo(Arrow arrow) {
            if (arrow == null) {
                throw new NullPointerException();
            }

            int compare = Double.compare(arrow.getBump(), this.getBump());

            if (compare == 0) {
                return Integer.compare(this.getIndex(), arrow.getIndex());
            }

            return compare;
        }

        public String toString() {
            return "Arrow<" + a + "->" + b + " bump = " + bump + " t/h = " + hOrT + " naYX = " + naYX + ">";
        }

        public int getIndex() {
            return index;
        }
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

    // Get all adj that are connected to Y.
    private Set<Node> getNeighbors(Node y) {
        List<Edge> yEdges = graph.getEdges(y);
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
        set.addAll(graph.getParents(y));
        return this.scoreGraphChange(y, set, x, hashIndices);
    }

    // Evaluate the Delete(X, Y, T) operator (Definition 12 from Chickering, 2002).
    private double deleteEval(Node x, Node y, Set<Node> diff, Set<Node> naYX,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set = new HashSet<>(diff);
        set.addAll(graph.getParents(y));
        set.remove(x);
        return -this.scoreGraphChange(y, set, x, hashIndices);
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

        if (boundGraph != null && !boundGraph.isAdjacentTo(x, y)) {
            return false;
        }

        graph.addDirectedEdge(x, y);

        if (verbose) {
            String label = trueGraph != null && trueEdge != null ? "*" : "";
            TetradLogger.getInstance().log("insertedEdges", graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y)
                    + " " + T + " " + bump + " " + label);
        }

        int numEdges = graph.getNumEdges();

        if (verbose) {
            if (numEdges % 1000 == 0) {
                out.println("Num edges added: " + numEdges);
            }
        }

        if (verbose) {
            String label = trueGraph != null && trueEdge != null ? "*" : "";
            out.println(graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y)
                    + " " + T + " " + bump + " " + label
                    + " degree = " + GraphUtils.getDegree(graph)
                    + " indegree = " + GraphUtils.getIndegree(graph));
        }

        for (Node _t : T) {
            graph.removeEdge(_t, y);
            if (boundGraph != null && !boundGraph.isAdjacentTo(_t, y)) {
                continue;
            }

            graph.addDirectedEdge(_t, y);

            if (verbose) {
                String message = "--- Directing " + graph.getEdge(_t, y);
                TetradLogger.getInstance().log("directedEdges", message);
                out.println(message);
            }
        }

        return true;
    }

    Set<Edge> removedEdges = new HashSet<>();

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
        removedEdges.add(Edges.undirectedEdge(x, y));

        if (verbose) {
            int numEdges = graph.getNumEdges();
            if (numEdges % 1000 == 0) {
                out.println("Num edges (backwards) = " + numEdges);
            }

            if (verbose) {
                String label = trueGraph != null && trueEdge != null ? "*" : "";
                String message = (graph.getNumEdges()) + ". DELETE " + x + "-->" + y
                        + " H = " + H + " NaYX = " + naYX + " diff = " + diff + " (" + bump + ") " + label;
                TetradLogger.getInstance().log("deletedEdges", message);
                out.println(message);
            }
        }

        for (Node h : H) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (graph.isParentOf(h, y) || graph.isParentOf(h, x)) {
                continue;
            }

            Edge oldyh = graph.getEdge(y, h);

            graph.removeEdge(oldyh);

            graph.addEdge(Edges.directedEdge(y, h));

            if (verbose) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldyh + " to "
                        + graph.getEdge(y, h));
                out.println("--- Directing " + oldyh + " to " + graph.getEdge(y, h));
            }

            Edge oldxh = graph.getEdge(x, h);

            if (Edges.isUndirectedEdge(oldxh)) {
                graph.removeEdge(oldxh);

                graph.addEdge(Edges.directedEdge(x, h));

                if (verbose) {
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldxh + " to "
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

        if (this.existsKnowledge()) {
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
        boolean clique = GraphUtils.isClique(union, graph);
        boolean noCycle = !this.existsUnblockedSemiDirectedPath(y, x, union, cycleBound);
        return clique && noCycle && !violatesKnowledge;
    }

    private boolean validDelete(Node x, Node y, Set<Node> H, Set<Node> naYX) {
        boolean violatesKnowledge = false;

        if (this.existsKnowledge()) {
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
        return GraphUtils.isClique(diff, graph) && !violatesKnowledge;
    }

    // Adds edges required by knowledge.
    private void addRequiredEdges(Graph graph) {
        if (!this.existsKnowledge()) {
            return;
        }

        for (Iterator<KnowledgeEdge> it = this.getKnowledge().requiredEdgesIterator(); it.hasNext(); ) {
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

            if (knowledge.isForbidden(A, B)) {
                Node nodeA = edge.getNode1();
                Node nodeB = edge.getNode2();
                if (nodeA == null || nodeB == null) {
                    throw new NullPointerException();
                }

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }

                if (!graph.isChildOf(nodeA, nodeB) && this.getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }
            } else if (knowledge.isForbidden(B, A)) {
                Node nodeA = edge.getNode2();
                Node nodeB = edge.getNode1();
                if (nodeA == null || nodeB == null) {
                    throw new NullPointerException();
                }

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }
                if (!graph.isChildOf(nodeA, nodeB) && this.getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
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
            if (this.getKnowledge().isForbidden(node.getName(), y.getName())) {
                return false;
            }
        }
        return true;
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
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

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
        this.addRequiredEdges(graph);
        return this.meekOrientRestricted(nodes, this.getKnowledge());
    }

    // Runs Meek rules on just the changed adj.
    private Set<Node> meekOrientRestricted(List<Node> nodes, IKnowledge knowledge) {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        return rules.orientImplied(graph);
    }

    // Maps adj to their indices for quick lookup.
    private void buildIndexing(List<Node> nodes) {
        hashIndices = new ConcurrentHashMap<>();

        int i = -1;

        for (Node n : nodes) {
            hashIndices.put(n, ++i);
        }
    }

    // Removes information associated with an edge x->y.
    private synchronized void clearArrow(Node x, Node y) {
        OrderedPair<Node> pair = new OrderedPair<>(x, y);
        Set<Arrow> lookupArrows = this.lookupArrows.get(pair);

        if (lookupArrows != null) {
            sortedArrows.removeAll(lookupArrows);
        }

        this.lookupArrows.remove(pair);
    }

    // Adds the given arrow for the adjacency i->j. These all are for i->j but may have
    // different T or H or NaYX sets, and so different bumps.
    private void addLookupArrow(Node i, Node j, Arrow arrow) {
        OrderedPair<Node> pair = new OrderedPair<>(i, j);
        Set<Arrow> arrows = lookupArrows.get(pair);

        if (arrows == null) {
            arrows = new ConcurrentSkipListSet<>();
            lookupArrows.put(pair, arrows);
        }

        arrows.add(arrow);
    }

    //===========================SCORING METHODS===================//

    /**
     * Scores the given DAG, up to a constant.
     */
    public double scoreDag(Graph dag) {
        this.buildIndexing(dag.getNodes());

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
                parentIndices[count++] = hashIndices.get(nextParent);
            }

            int yIndex = hashIndices.get(y);
            _score += fgesScore.localScore(yIndex, parentIndices);
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

        return fgesScore.localScoreDiff(hashIndices.get(x), yIndex, parentIndices);
    }

    private List<Node> getVariables() {
        return variables;
    }

    // Stores the graph, if its totalScore knocks out one of the top ones.
    private void storeGraph(Graph graph) {
        if (this.getnumCPDAGsToStore() > 0) {
            Graph graphCopy = new EdgeListGraph(graph);
            topGraphs.addLast(new ScoredGraph(graphCopy, totalScore));
        }

        if (topGraphs.size() == this.getnumCPDAGsToStore() + 1) {
            topGraphs.removeFirst();
        }
    }

    public String logEdgeBayesFactorsString(Graph dag) {
        Map<Edge, Double> factors = this.logEdgeBayesFactors(dag);
        return this.logBayesPosteriorFactorsString(factors, this.scoreDag(dag));
    }

    public Map<Edge, Double> logEdgeBayesFactors(Graph dag) {
        Map<Edge, Double> logBayesFactors = new HashMap<>();
        double withEdge = this.scoreDag(dag);

        for (Edge edge : dag.getEdges()) {
            dag.removeEdge(edge);
            double withoutEdge = this.scoreDag(dag);
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

        builder.append("For a DAG in the IMaGES CPDAG with model totalScore m, for each edge e in the "
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
}
