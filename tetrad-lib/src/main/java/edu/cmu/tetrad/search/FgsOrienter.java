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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;

import java.io.PrintStream;
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
 * the graph. This is a restricted form of the faithfulness assumption, something GES does not assume. This
 * faithfulness assumption needs to be explicitly turned on using setFaithfulnessAssumed(true).
 * <p>
 * A number of other optimizations were added 5/2015. See code for details.
 *
 * @author Ricardo Silva, Summer 2003
 * @author Joseph Ramsey, Revisions 5/2015
 *         <p>
 *         This Orients a given undirected graph such that the edges in the graph are a superset
 *         of the edges in the oriented graph
 * @author AJ Sedgewick, 5/2015
 */
public final class FgsOrienter implements GraphSearch, GraphScorer, Reorienter {

    /**
     * The covariance matrix for continuous data.
     */
    private ICovarianceMatrix covariances;

    /**
     * Sample size, either from the data set or from the covariances.
     */
    private int sampleSize;

    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * List of variables in the data set, in order.
     */
    private List<Node> variables;

    /**
     * True iff the data set is discrete.
     */
    private boolean discrete;

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
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    /**
     * Penalty penaltyDiscount--the BIC penalty is multiplied by this (for continuous variables).
     */
    private double penaltyDiscount = 1.0;

    /**
     * The depth of search for the forward reevaluation step.
     */
    private int depth = -1;

    /**
     * A bound on cycle length.
     */
    private int cycleBound = -1;

    /**
     * The score for discrete searches.
     */
    private LocalDiscreteScore discreteScore;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * The top n graphs found by the algorithm, where n is numPatternsToStore.
     */
    private SortedSet<ScoredGraph> topGraphs = new TreeSet<>();

    /**
     * The number of top patterns to store.
     */
    private int numPatternsToStore = 0;

    /**
     * True if logs should be output.
     */
    private boolean log = true;

    /**
     * True if verbose output should be printed.
     */
    private boolean verbose = false;

    // Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
    private SortedSet<Arrow> sortedArrows = new ConcurrentSkipListSet<>();

    // Arrows added to sortedArrows for each <i, j>.
    private Map<OrderedPair<Node>, Set<Arrow>> lookupArrows;

    // Map from variables to their column indices in the data set.
    private ConcurrentMap<Node, Integer> hashIndices;

    // The static ForkJoinPool instance.
    private ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

    // A running tally of the total BIC score.
    private double score;

    // A graph where X--Y means that X and Y have non-zero total effect on one another.
    private Graph effectEdgesGraph;

    // The minimum number of operations to do before parallelizing.
    private final int minChunk = 100;

    // Where printed output is sent.
    private PrintStream out = System.out;

    // A initial adjacencies graph.
    private Graph adjacencies = null;

    // True if it is assumed that zero effect adjacencies are not in the graph.
    private boolean faithfulnessAssumed = true;

    // A utility map to help with orientation.
    private WeakHashMap<Node, Set<Node>> neighbors = new WeakHashMap<>();

    // Graph input by user as super-structure to search over
    private Graph graphToOrient = null;

    //===========================CONSTRUCTORS=============================//

    /**
     * The data set must either be all continuous or all discrete.
     */
    public FgsOrienter(DataSet dataSet) {
        out.println("GES constructor");

        if (dataSet.isDiscrete()) {
            DataBox box = new VerticalIntDataBox(dataSet.getNumRows(), dataSet.getNumColumns());

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                for (int j = 0; j < dataSet.getNumColumns(); j++) {
                    box.set(i, j, dataSet.getInt(i, j));
                }
            }

            BoxDataSet dataSet1 = new BoxDataSet(box, dataSet.getVariables());

            setDataSet(dataSet1);
            BDeuScore score = new BDeuScore(dataSet1);
            score.setSamplePrior(10);
            score.setStructurePrior(0.001);
            setDiscreteScore(score);
            setStructurePrior(0.001);
            setSamplePrior(10.);
        } else {
            setCovMatrix(new CovarianceMatrixOnTheFly(dataSet));
        }

        out.println("GES constructor done");
    }

    /**
     * Continuous case--where a covariance matrix is already available.
     */
    public FgsOrienter(ICovarianceMatrix covMatrix) {
        out.println("GES(orient) constructor");

        setCovMatrix(covMatrix);

        out.println("GES(orient) constructor done");
    }

    // This will "orient" graph
    @Override
    public void orient(Graph graph) {
        this.graphToOrient = new EdgeListGraph(graph);
        this.graphToOrient = GraphUtils.undirectedGraph(this.graphToOrient);

        // Had to fix this reference to the original graph was modified.
        Graph _graph = search();

        graph.removeEdges(graph.getEdges());

        for (Edge edge : _graph.getEdges()) {
            graph.addEdge(edge);
        }
    }

    //==========================PUBLIC METHODS==========================//

    /**
     * Returns superstructure graph
     */
    public Graph getGraphToOrient() {
        return graphToOrient;
    }

    /**
     * Set to true if it is assumed that all path pairs with one length 1 path do not cancel.
     */
    public void setFaithfulnessAssumed(boolean faithfulness) {
        this.faithfulnessAssumed = faithfulness;
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
     * @return the resulting Pattern.
     */
    public Graph search() {
        lookupArrows = new ConcurrentHashMap<>();
        final List<Node> nodes = new ArrayList<>(variables);
        this.effectEdgesGraph = getEffectEdges(nodes);

        if (adjacencies != null) {
            adjacencies = GraphUtils.replaceNodes(adjacencies, nodes);
        }

        Graph graph;

        if (initialGraph == null) {
            graph = new EdgeListGraphSingleConnections(getVariables());
        } else {
            graph = new EdgeListGraphSingleConnections(initialGraph);

            for (Edge edge : initialGraph.getEdges()) {
                if (!effectEdgesGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    effectEdgesGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
                }
            }
        }

        addRequiredEdges(graph);

        topGraphs.clear();

        storeGraph(graph);

        long start = System.currentTimeMillis();
        score = 0.0;

        // Do forward search.
        fes(graph);

        // Do backward search.
        bes(graph);

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - start;
        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.flush();

        return graph;

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
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    /**
     * For BDeu score for discrete search; see Chickering (2002).
     */
    public void setStructurePrior(double structurePrior) {
        if (getDiscreteScore() != null) {
            getDiscreteScore().setStructurePrior(structurePrior);
        }
    }

    /**
     * For BDeu score for discrete search; see Chickering (2002).
     */
    public void setSamplePrior(double samplePrior) {
        if (getDiscreteScore() != null) {
            getDiscreteScore().setSamplePrior(samplePrior);
        }
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * For BIC score, a multiplier on the penalty term. For continuous searches.
     */
    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    /**
     * For BIC score, a multiplier on the penalty term. For continuous searches.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount < 0) {
            throw new IllegalArgumentException("Penalty penaltyDiscount must be >= 0: "
                    + penaltyDiscount);
        }

        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * If the true graph is set, askterisks will be printed in log output for the true edges.
     */
    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    /**
     * @return the score of the given DAG, up to a constant.
     */
    public double getScore(Graph dag) {
        return scoreDag(dag);
    }

    /**
     * @return the list of top scoring graphs.
     */
    public SortedSet<ScoredGraph> getTopGraphs() {
        return topGraphs;
    }

    /**
     * @return the number of patterns to store.
     */
    public int getNumPatternsToStore() {
        return numPatternsToStore;
    }

    /**
     * Sets the number of patterns to store. This should be set to zero for fast search.
     */
    public void setNumPatternsToStore(int numPatternsToStore) {
        if (numPatternsToStore < 0) {
            throw new IllegalArgumentException("# graphs to store must at least 0: " + numPatternsToStore);
        }

        this.numPatternsToStore = numPatternsToStore;
    }

    /**
     * @return the discrete scoring function being used. By default, BDeu.
     */
    public LocalDiscreteScore getDiscreteScore() {
        return discreteScore;
    }

    /**
     * Sets the discrete scoring function to use.
     */
    public void setDiscreteScore(LocalDiscreteScore discreteScore) {
        this.discreteScore = discreteScore;
    }

    /**
     * True iff log output should be produced.
     */
    public boolean isLog() {
        return log;
    }

    /**
     * Sets whether log output should be produced. Set to false a faster search.
     */
    public void setLog(boolean log) {
        this.log = log;
    }

    /**
     * @return the initial graph for the search. The search is initialized to this graph and
     * proceeds from there.
     */
    public Graph getInitialGraph() {
        return initialGraph;
    }

    /**
     * Sets the initial graph.
     */
    public void setInitialGraph(Graph initialGraph) {
        initialGraph = GraphUtils.replaceNodes(initialGraph, variables);

        out.println("Initial graph variables: " + initialGraph.getNodes());
        out.println("Data set variables: " + variables);

        if (!new HashSet<>(initialGraph.getNodes()).equals(new HashSet<>(variables))) {
            throw new IllegalArgumentException("Variables aren't the same.");
        }

        this.initialGraph = initialGraph;
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
        return out;
    }

    /**
     * @return the set of preset adjacenies for the algorithm; edges not in this adjacencies graph
     * will not be added.
     */
    public Graph getAdjacencies() {
        return adjacencies;
    }

    /**
     * Sets the set of preset adjacenies for the algorithm; edges not in this adjacencies graph
     * will not be added.
     */
    public void setAdjacencies(Graph adjacencies) {
        this.adjacencies = adjacencies;
    }

    /**
     * @return the depth for the forward reevaluation step.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * -1 for unlimited depth, otherwise a number >= 0. In the forward reevaluation step, subsets of neighbors up to
     * depth in size are considered. Limiting depth can speed up the algorithm.
     */
    public void setDepth(int depth) {
        this.depth = depth;
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
        if (!(cycleBound == -1 || cycleBound >= 1))
            throw new IllegalArgumentException("Cycle bound needs to be -1 or >= 1: " + cycleBound);
        this.cycleBound = cycleBound;
    }

    //===========================PRIVATE METHODS========================//

    // Simultaneously finds the first edge to add to an empty graph and finds all length 1 undirectedPaths that are
    // not canceled by other undirectedPaths (the "effect edges")
    private Graph getEffectEdges(final List<Node> nodes) {
        long start = System.currentTimeMillis();
        final Graph effectEdgesGraph = new EdgeListGraphSingleConnections(nodes);
        final Set<Node> emptySet = new HashSet<>(0);

        final int[] count = new int[1];

        class EffectTask extends RecursiveTask<Boolean> {
            private int chunk;
            private int from;
            private int to;

            public EffectTask(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        synchronized (count) {
                            if (((count[0]++) + 1) % 1000 == 0) out.println("Initializing effect edges: " + count[0]);
                        }

                        Node y = nodes.get(i);

                        for (int j = i + 1; j < nodes.size(); j++) {
                            Node x = nodes.get(j);
//
                            if (!graphToOrient.isAdjacentTo(x, y)) {
                                continue;
                            }

                            if (existsKnowledge()) {
                                if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                                    continue;
                                }

                                if (!validSetByKnowledge(y, emptySet)) {
                                    continue;
                                }
                            }

                            if (adjacencies != null && !adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            double bump;

                            if (covariances != null) {
                                double s1 = localSemScoreSingleParent(hashIndices.get(y), hashIndices.get(x));
                                double s2 = localSemScoreSingleParent(hashIndices.get(y));
                                bump = s1 - s2;
                            } else {
                                bump = scoreGraphChange(y, Collections.singleton(x), emptySet, hashIndices);
                            }
//
//                            if (bump > 0.0) {
                            if (bump > -getPenaltyDiscount() * Math.log(sampleSize())) {
//                                if (bump > -getPenaltyDiscount() * Math.log(sampleSize())) {
                                final Edge edge = Edges.undirectedEdge(x, y);
                                effectEdgesGraph.addEdge(edge);
                            }

                            if (bump > 0.0) {
                                Arrow arrow1 = new Arrow(bump, x, y, emptySet, emptySet);
                                Arrow arrow2 = new Arrow(bump, y, x, emptySet, emptySet);

                                sortedArrows.add(arrow1);
                                addLookupArrow(x, y, arrow1);

                                sortedArrows.add(arrow2);
                                addLookupArrow(y, x, arrow2);
                            }
                        }
                    }

                    return true;
                } else {
                    int mid = (to - from) / 2;

                    List<EffectTask> tasks = new ArrayList<>();

                    tasks.add(new EffectTask(chunk, from, from + mid));
                    tasks.add(new EffectTask(chunk, from + mid, to));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        buildIndexing(nodes);
        pool.invoke(new EffectTask(minChunk, 0, nodes.size()));

        long stop = System.currentTimeMillis();

        if (verbose) {
            out.println("Elapsed getEffectEdges = " + (stop - start) + " ms");
        }

        return effectEdgesGraph;
    }

    /**
     * Forward equivalence search.
     *
     * @param graph The graph in the state prior to the forward equivalence search.
     */
    private void fes(Graph graph) {
        TetradLogger.getInstance().log("info", "** FORWARD EQUIVALENCE SEARCH");

        while (!sortedArrows.isEmpty()) {
            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            clearArrow(x, y);

            if (graph.isAdjacentTo(x, y)) {
                continue;
            }

            if (!validInsert(x, y, arrow.getHOrT(), arrow.getNaYX(), graph)) {
                continue;
            }

            Set<Node> t = arrow.getHOrT();
            double bump = arrow.getBump();

            insert(x, y, t, graph, bump);
            score += bump;

            Set<Node> visited = rebuildPatternRestricted(graph, x, y);
            Set<Node> toProcess = new HashSet<>();

            for (Node node : visited) {
                final Set<Node> neighbors = getNeighbors(node, graph);
                final Set<Node> storedNeighbors = this.neighbors.get(node);

                if (!neighbors.equals(storedNeighbors)) {
                    toProcess.add(node);
                    this.neighbors.put(node, neighbors);
                }
            }

            Edge xy = graph.getEdge(x, y);

            if (xy.pointsTowards(x)) {
                toProcess.add(x);
            } else if (xy.pointsTowards(y)) {
                toProcess.add(y);
            }

            reevaluateForward(graph, toProcess);

            storeGraph(graph);
        }
    }

    // Returns the set of nodes {x} U {y} U adj(x) U adj(y).
    private Set<Node> adjNodes(Graph graph, Node x, Node y) {
        Set<Node> adj = new HashSet<>();
        adj.addAll(graph.getAdjacentNodes(x));
        adj.addAll(graph.getAdjacentNodes(y));
        adj.add(x);
        adj.add(y);
        return adj;
    }

    /**
     * Backward equivalence search.
     *
     * @param graph The graph in the state after the forward equivalence search.
     */
    private void bes(Graph graph) {
        TetradLogger.getInstance().log("info", "** BACKWARD EQUIVALENCE SEARCH");

        initializeArrowsBackward(graph);

        while (!sortedArrows.isEmpty()) {
            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (!graph.isAdjacentTo(x, y)) continue;

            if (!validDelete(y, arrow.getHOrT(), arrow.getNaYX(), graph)) {
                continue;
            }

            Set<Node> h = arrow.getHOrT();
            double bump = arrow.getBump();

            delete(x, y, h, graph, bump);
            score += bump;

            rebuildPatternRestricted(graph, x, y);

            storeGraph(graph);

            reevaluateBackward(graph, x, y);
        }
    }

    // Returns true if knowledge is not empty.
    private boolean existsKnowledge() {
        return !knowledge.isEmpty();
    }

    // Initiaizes the sorted arrows and lookup arrows lists for the backward search.
    private void initializeArrowsBackward(Graph graph) {
        sortedArrows.clear();
        lookupArrows.clear();

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (existsKnowledge()) {
                if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                    continue;
                }
            }

            if (Edges.isDirectedEdge(edge)) {
                calculateArrowsBackward(x, y, graph);
            } else {
                calculateArrowsBackward(x, y, graph);
                calculateArrowsBackward(y, x, graph);
            }
        }
    }

    // Calcuates new arrows based on changes in the graph for the forward search.
    private void reevaluateForward(final Graph graph, final Set<Node> nodes) {
        List<Node> _nodes = new ArrayList<>(nodes);

        List<OrderedPair<Node>> pairs = new ArrayList<>();

        for (final Node x : _nodes) {
            List<Node> adj;

            if (isFaithfulnessAssumed()) {
                adj = effectEdgesGraph.getAdjacentNodes(x);
            } else {
                adj = variables;
            }

            for (Node w : adj) {
                pairs.add(new OrderedPair<>(w, x));
            }
        }

        class AdjTask extends RecursiveTask<Boolean> {
            private final List<OrderedPair<Node>> pairs;
            private int from;
            private int to;

            public AdjTask(List<OrderedPair<Node>> pairs, int from, int to) {
                this.pairs = pairs;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= 25) {
                    for (int _w = from; _w < to; _w++) {
                        final OrderedPair<Node> p = pairs.get(_w);
                        Node w = p.getFirst();
                        Node x = p.getSecond();

                        if (w == x) continue;

                        if (adjacencies != null && !(adjacencies.isAdjacentTo(w, x))) {
                            continue;
                        }

                        if (!graph.isAdjacentTo(w, x)) {
                            calculateArrowsForward(w, x, graph);
                        }
                    }

                    return true;
                } else {
                    int mid = (to - from) / 2;

                    List<AdjTask> tasks = new ArrayList<>();

                    tasks.add(new AdjTask(pairs, from, from + mid));
                    tasks.add(new AdjTask(pairs, from + mid, to));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        final AdjTask task = new AdjTask(pairs, 0, pairs.size());

        pool.invoke(task);

    }

    // Calculates the new arrows for an a->b edge.
    private void calculateArrowsForward(final Node a, final Node b, final Graph graph) {
        if (isFaithfulnessAssumed() && !effectEdgesGraph.isAdjacentTo(a, b)) return;
        if (adjacencies != null && !adjacencies.isAdjacentTo(a, b)) return;

        if (!graphToOrient.isAdjacentTo(a, b)) {
            return;
        }

        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(a.getName(), b.getName())) {
                return;
            }
        }

        final Set<Node> naYX = getNaYX(a, b, graph);
        final List<Node> t = getTNeighbors(a, b, graph);

        final int _depth = Math.min(t.size(), depth == -1 ? 1000 : depth);

        clearArrow(a, b);

        final DepthChoiceGenerator gen = new DepthChoiceGenerator(t.size(), _depth);

        int[] choice;

        while ((choice = gen.next()) != null) {
            Set<Node> s = GraphUtils.asSet(choice, t);

            Set<Node> union = new HashSet<>(s);
            union.addAll(naYX);

            // Necessary condition for it to be a clique later (after possible edge removals) is that it be a clique
            // now.
            if (!isClique(union, graph)) continue;

            if (existsKnowledge()) {
                if (!validSetByKnowledge(b, s)) {
                    continue;
                }
            }


            double bump = insertEval(a, b, s, naYX, graph, hashIndices);

            if (bump > 0.0) {
                Arrow arrow = new Arrow(bump, a, b, s, naYX);
                sortedArrows.add(arrow);
                addLookupArrow(a, b, arrow);
            }
        }
    }

    // Reevaluates arrows after removing an edge from the graph.
    private void reevaluateBackward(final Graph graph, final Node x, final Node y) {
        class BackwardTask extends RecursiveTask<Boolean> {
            private List<Node> nodes;
            private Map<Node, Integer> hashIndices;
            private int chunk;
            private int from;
            private int to;

            public BackwardTask(List<Node> nodes, int chunk, int from, int to,
                                Map<Node, Integer> hashIndices) {
                this.nodes = new ArrayList<>(nodes);
                this.hashIndices = new HashMap<>(hashIndices);
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int _w = from; _w < to; _w++) {
                        final Node w = nodes.get(_w);

                        if (w == x) continue;
                        if (w == y) continue;

                        if (!graph.isAdjacentTo(w, x)) {
                            calculateArrowsBackward(w, x, graph);
                        }

                        if (!graph.isAdjacentTo(w, y)) {
                            calculateArrowsBackward(w, y, graph);
                        }
                    }

                    return true;
                } else {
                    int mid = (to - from) / 2;

                    List<BackwardTask> tasks = new ArrayList<>();

                    tasks.add(new BackwardTask(nodes, chunk, from, from + mid, hashIndices));
                    tasks.add(new BackwardTask(nodes, chunk, from + mid, to, hashIndices));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        Set<Node> _adj = adjNodes(graph, x, y);
        final List<Node> adj = new ArrayList<>(_adj);

        pool.invoke(new BackwardTask(adj, minChunk, 0, adj.size(), hashIndices));
    }

    // Calculates the arrows for the removal in the backward direction.
    private void calculateArrowsBackward(Node a, Node b, Graph graph) {
        if (a == b) {
            return;
        }

        if (!graph.isAdjacentTo(a, b)) {
            return;
        }

        if (existsKnowledge()) {
            if (!getKnowledge().noEdgeRequired(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = getNaYX(a, b, graph);

        clearArrow(a, b);

        List<Node> _naYX = new ArrayList<>(naYX);
//        final int _depth = Math.min(_naYX.size(), depth == -1 ? 1000 : depth);

        DepthChoiceGenerator gen = new DepthChoiceGenerator(_naYX.size(), _naYX.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            Set<Node> h = GraphUtils.asSet(choice, _naYX);

            Set<Node> diff = new HashSet<>(naYX);
            diff.removeAll(h);

            if (!isClique(diff, graph)) continue;

            if (existsKnowledge()) {
                if (!validSetByKnowledge(b, h)) {
                    continue;
                }
            }

            double bump = deleteEval(a, b, h, naYX, graph, hashIndices);

            if (bump > 0.0) {
                Arrow arrow = new Arrow(bump, a, b, h, naYX);
                sortedArrows.add(arrow);
                addLookupArrow(a, b, arrow);
            }
        }
    }

    // Basic data structure for an arrow a->b considered for additiom or removal from the graph, together with
    // associated sets needed to make this determination. For both forward and backward direction, NaYX is needed.
    // For the forward direction, T neighbors are needed; for the backward direction, H neighbors are needed.
    // See Chickering (2002). The score difference resulting from added in the edge (hypothetically) is recorded
    // as the "bump".
    private static class Arrow implements Comparable<Arrow> {
        private double bump;
        private Node a;
        private Node b;
        private Set<Node> hOrT;
        private Set<Node> naYX;

        public Arrow(double bump, Node a, Node b, Set<Node> hOrT, Set<Node> naYX) {
            this.bump = bump;
            this.a = a;
            this.b = b;
            this.hOrT = hOrT;
            this.naYX = naYX;
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

        // Sorting by bump, high to low.
        public int compareTo(Arrow arrow) {
            return Double.compare(arrow.getBump(), getBump());
        }

        public String toString() {
            return "Arrow<" + a + "->" + b + " bump = " + bump + " t/h = " + hOrT + " naYX = " + naYX + ">";
        }
    }

    // Get all nodes that are connected to Y by an undirected edge and not adjacent to X.
    private static List<Node> getTNeighbors(Node x, Node y, Graph graph) {
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

    // Get all nodes that are connected to Y by an undirected edge.
    private static Set<Node> getNeighbors(Node y, Graph graph) {
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
    private double insertEval(Node x, Node y, Set<Node> t, Set<Node> naYX, Graph graph,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set1 = new HashSet<>(naYX);
        set1.addAll(t);
        set1.addAll(graph.getParents(y));
        set1.remove(x);

        Set<Node> set2 = new HashSet<>(set1);
        set1.add(x);

        return scoreGraphChange(y, set1, set2, hashIndices);
    }

    // Evaluate the Delete(X, Y, T) operator (Definition 12 from Chickering, 2002).
    private double deleteEval(Node x, Node y, Set<Node> h, Set<Node> naYX, Graph graph,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set1 = new HashSet<>(naYX);
        set1.removeAll(h);
        set1.addAll(graph.getParents(y));
        set1.remove(x);

        Set<Node> set2 = new HashSet<>(set1);
        set2.add(x);

        return scoreGraphChange(y, set1, set2, hashIndices);
    }

    // Do an actual insertion. (Definition 12 from Chickering, 2002).
    private void insert(Node x, Node y, Set<Node> t, Graph graph, double bump) {
        if (graph.isAdjacentTo(x, y)) {
            return; // The initial graph may already have put this edge in the graph.
//            throw new IllegalArgumentException(x + " and " + y + " are already adjacent in the graph.");
        }

        Edge trueEdge = null;

        if (trueGraph != null) {
            Node _x = trueGraph.getNode(x.getName());
            Node _y = trueGraph.getNode(y.getName());
            trueEdge = trueGraph.getEdge(_x, _y);
        }

        graph.addDirectedEdge(x, y);

        if (log) {
            String label = trueGraph != null && trueEdge != null ? "*" : "";
            TetradLogger.getInstance().log("insertedEdges", graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y) +
                    " " + t + " " + bump + " " + label);
        }

        int numEdges = graph.getNumEdges();
        if (numEdges % 1000 == 0) out.println("Num edges added: " + numEdges);

        if (verbose) {
            String label = trueGraph != null && trueEdge != null ? "*" : "";
            out.println(graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y) +
                    " " + t + " " + bump + " " + label);
        }

        for (Node _t : t) {
            Edge oldEdge = graph.getEdge(_t, y);

            if (oldEdge == null) throw new IllegalArgumentException("Not adjacent: " + _t + ", " + y);

            graph.removeEdge(_t, y);
            graph.addDirectedEdge(_t, y);

            if (log && verbose) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(_t, y));
                out.println("--- Directing " + oldEdge + " to " +
                        graph.getEdge(_t, y));
            }
        }
    }

    // Do an actual deletion (Definition 13 from Chickering, 2002).
    private void delete(Node x, Node y, Set<Node> subset, Graph graph, double bump) {

        Edge trueEdge = null;

        if (trueGraph != null) {
            Node _x = trueGraph.getNode(x.getName());
            Node _y = trueGraph.getNode(y.getName());
            trueEdge = trueGraph.getEdge(_x, _y);
        }

        graph.removeEdge(x, y);

        if (verbose) {
            int numEdges = graph.getNumEdges();
            if (numEdges % 1000 == 0) out.println("Num edges (backwards) = " + numEdges);
        }

        if (log) {
            Edge oldEdge = graph.getEdge(x, y);
            String label = trueGraph != null && trueEdge != null ? "*" : "";
            TetradLogger.getInstance().log("deletedEdges", (graph.getNumEdges() - 1) + ". DELETE " + oldEdge +
                    " " + subset + " (" + bump + ") " + label);
            out.println((graph.getNumEdges()) + ". DELETE " + oldEdge +
                    " " + subset + " (" + bump + ") " + label);
        }

        for (Node h : subset) {
            Edge oldEdge = graph.getEdge(y, h);

            graph.removeEdge(y, h);
            graph.addDirectedEdge(y, h);

            if (log) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(y, h));
            }

            if (verbose) {
                out.println("--- Directing " + oldEdge + " to " +
                        graph.getEdge(y, h));
            }

            final Edge edge = graph.getEdge(x, h);

            if (edge == null) {
                continue;
            }

            if (Edges.isUndirectedEdge(edge)) {
                if (!graph.isAdjacentTo(x, h)) throw new IllegalArgumentException("Not adjacent: " + x + ", " + h);
                oldEdge = edge;

                graph.removeEdge(x, h);
                graph.addDirectedEdge(x, h);

                if (log) {
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                            edge);
                }

                if (verbose) {
                    out.println("--- Directing " + oldEdge + " to " +
                            edge);
                }
            }
        }
    }

    // Test if the candidate insertion is a valid operation
    // (Theorem 15 from Chickering, 2002).
    private boolean validInsert(Node x, Node y, Set<Node> s, Set<Node> naYX, Graph graph) {
        Set<Node> union = new HashSet<>(s);
        union.addAll(naYX);

        // Note s U NaYX must be a clique, but this has already been checked. Nevertheless, at this
        // point it must be verified that all nodes in s U NaYX are neighbors of Y, since some of
        // the edges g---Y may have been oriented in the interim.
        return allNeighbors(y, union, graph) && !existsUnblockedSemiDirectedPath(y, x, union, graph, cycleBound);

    }

    // Returns true if all of the members of 'union' are neighbors of y.
    private boolean allNeighbors(Node y, Set<Node> union, Graph graph) {
        for (Node n : union) {
            Edge e = graph.getEdge(y, n);
            if (e == null) {
                return false;
            }
            if (!Edges.isUndirectedEdge(e)) {
                return false;
            }
        }

        return true;
    }

    // Test if the candidate deletion is a valid operation (Theorem 17 from Chickering, 2002).
    private boolean validDelete(Node y, Set<Node> h, Set<Node> naXY, Graph graph) {
        Set<Node> set = new HashSet<>(naXY);
        set.removeAll(h);
        return isClique(set, graph) && allNeighbors(y, set, graph);
    }

    // Adds edges required by knowledge.
    private void addRequiredEdges(Graph graph) {
        if (!existsKnowledge()) return;

        for (Iterator<KnowledgeEdge> it = getKnowledge().requiredEdgesIterator(); it.hasNext(); ) {
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
            final String A = edge.getNode1().getName();
            final String B = edge.getNode2().getName();

            if (knowledge.isForbidden(A, B)) {
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
            } else if (knowledge.isForbidden(B, A)) {
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

    // Find all nodes that are connected to Y by an undirected edge that are adjacent to X (that is, by undirected or
    // directed edge).
    private static Set<Node> getNaYX(Node x, Node y, Graph graph) {
        List<Edge> yEdges = graph.getEdges(y);
        Set<Node> nayx = new HashSet<>();

        for (Edge edge : yEdges) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            Node z = edge.getDistalNode(y);

            if (!graph.isAdjacentTo(z, x)) {
                continue;
            }

            nayx.add(z);
        }

        return nayx;
    }

    // Returns true iif the given set forms a clique in the given graph.
    private static boolean isClique(Set<Node> nodes, Graph graph) {
        List<Node> _nodes = new ArrayList<>(nodes);
        for (int i = 0; i < _nodes.size() - 1; i++) {
            for (int j = i + 1; j < _nodes.size(); j++) {
                if (!graph.isAdjacentTo(_nodes.get(i), _nodes.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

    // Returns true is a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound'. Cycle checker in other words.
    private boolean existsUnblockedSemiDirectedPath(Node from, Node to, Set<Node> cond, Graph G, int bound) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();
        Q.offer(from);
        V.add(from);
        Node e = null;
        int distance = 0;

        while (!Q.isEmpty()) {
            Node t = Q.remove();
            if (t == to) return true;

            if (e == t) {
                e = null;
                distance++;
                if (distance > (bound == -1 ? 1000 : bound)) return true;
            }

            for (Node u : G.getAdjacentNodes(t)) {
                Edge edge = G.getEdge(t, u);
                Node c = traverseSemiDirected(t, edge);
                if (c == null) continue;
                if (cond.contains(c)) continue;
                if (c == to) return true;

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

    // Used to find semidirected undirectedPaths for cycle checking.
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

    // Runs the Meek rules on just the changed nodes.
    private Set<Node> rebuildPatternRestricted(Graph graph, Node x, Node y) {
        Set<Node> visited = new HashSet<>();

        visited.addAll(reorientNode(graph, x));
        visited.addAll(reorientNode(graph, y));

        if (TetradLogger.getInstance().isEventActive("rebuiltPatterns")) {
            TetradLogger.getInstance().log("rebuiltPatterns", "Rebuilt pattern = " + graph);
        }

        return visited;
    }

    // Runs Meek rules on jsut the changed nodes.
    private Set<Node> reorientNode(Graph graph, Node a) {
        List<Node> nodes = graph.getAdjacentNodes(a);
        nodes.add(a);

        List<Edge> edges = graph.getEdges(a);
        SearchGraphUtils.basicPatternRestricted2(graph, a);
        addRequiredEdges(graph);
        Set<Node> visited = meekOrientRestricted(graph, nodes, getKnowledge());

        List<Edge> newEdges = graph.getEdges(a);
        newEdges.removeAll(edges); // The newly oriented edges.

        for (Edge edge : newEdges) {
            if (Edges.isUndirectedEdge(edge)) {
                Node _node = edge.getDistalNode(a);
                visited.addAll(reorientNode(graph, _node));
            }
        }

        return visited;
    }

    // Runs Meek rules on just the changed nodes.
    private Set<Node> meekOrientRestricted(Graph graph, List<Node> nodes, IKnowledge knowledge) {
        MeekRulesRestricted rules = new MeekRulesRestricted();
        rules.setOrientInPlace(false);
        rules.setKnowledge(knowledge);
        rules.orientImplied(graph, new HashSet<>(nodes));
        return rules.getVisitedNodes();
    }

    // Sets the data set, possibly calculating a covariane matrix.
    private void setDataSet(DataSet dataSet) {
        this.variables = dataSet.getVariables();
        this.discrete = dataSet.isDiscrete();

        if (!isDiscrete()) {
            CovarianceMatrix covariances = new CovarianceMatrix(dataSet);
            setCovMatrix(covariances);
        }

        this.sampleSize = dataSet.getNumRows();
    }

    // Sets the covariance matrix.
    private void setCovMatrix(final ICovarianceMatrix covarianceMatrix) {
        this.covariances = covarianceMatrix;
        this.variables = covarianceMatrix.getVariables();
        this.sampleSize = covarianceMatrix.getSampleSize();

        out.println("Calculating variances");
    }

    // Maps nodes to their indices for quick lookup.
    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();
        for (Node node : nodes) {
            this.hashIndices.put(node, variables.indexOf(node));
        }
    }

    // Removes informatilon associated with an edge x->y.
    private void clearArrow(Node x, Node y) {
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
        buildIndexing(dag.getNodes());

        double score = 0.0;

        for (Node y : dag.getNodes()) {
            Set<Node> parents = new HashSet<>(dag.getParents(y));
            int nextIndex = -1;
            for (int i = 0; i < getVariables().size(); i++) {
                nextIndex = hashIndices.get(variables.get(i));
            }
            int parentIndices[] = new int[parents.size()];
            Iterator<Node> pi = parents.iterator();
            int count = 0;
            while (pi.hasNext()) {
                Node nextParent = pi.next();
                parentIndices[count++] = hashIndices.get(nextParent);
            }

            if (this.isDiscrete()) {
                score += localDiscreteScore(nextIndex, parentIndices);
            } else {
                score += localSemScore(nextIndex, parentIndices);
            }
        }
        return score;
    }

    // Scores the difference between y with 'parents1' as parents an y with 'parents2' as parents.
    private double scoreGraphChange(Node y, Set<Node> parents1,
                                    Set<Node> parents2, Map<Node, Integer> hashIndices) {
        int yIndex = hashIndices.get(y);

        double score1, score2;

        int[] parentIndices1 = new int[parents1.size()];

        int count = -1;
        for (Node parent : parents1) {
            parentIndices1[++count] = hashIndices.get(parent);
        }

        if (isDiscrete()) {
            score1 = localDiscreteScore(yIndex, parentIndices1);
        } else {
            score1 = localSemScore(yIndex, parentIndices1);
        }

        int[] parentIndices2 = new int[parents2.size()];

        int count2 = -1;
        for (Node parent : parents2) {
            parentIndices2[++count2] = hashIndices.get(parent);
        }

        if (isDiscrete()) {
            score2 = localDiscreteScore(yIndex, parentIndices2);
        } else {
            score2 = localSemScore(yIndex, parentIndices2);
        }

        return score1 - score2;
    }

    // Compute the local BDeu score of (i, parents(i)). See (Chickering, 2002).
    private double localDiscreteScore(int i, int parents[]) {
        return getDiscreteScore().localScore(i, parents);
    }

    // Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model.
    private double localSemScore(int i, int[] parents) {
        ICovarianceMatrix cov = getCovMatrix();
        double residualVariance = cov.getValue(i, i);
        int n = sampleSize();
        int p = parents.length;
        TetradMatrix covxx = getSelection1(cov, parents);
        TetradMatrix covxxInv;
        try {
            covxxInv = covxx.inverse();
        } catch (Exception e) {
            printMinimalLinearlyDependentSet(parents, cov);
            out.println("Using generalized inverse.");
            covxxInv = covxx.ginverse();
        }
        TetradVector covxy = getSelection2(cov, parents, i);
        TetradVector b = covxxInv.times(covxy);
        residualVariance -= covxy.dotProduct(b);

        if (residualVariance <= 0 && verbose) {
            out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / cov.getValue(i, i)));
            return Double.NaN;
        }

        double c = getPenaltyDiscount();
        return score(residualVariance, n, p, c);
    }

    // Specialized scoring method for a single parent. Used to speed up the effect edges search.
    private double localSemScoreSingleParent(int i, int parent) {
        double residualVariance = covariances.getValue(i, i);
        int n = sampleSize();
        int p = 1;
        final double covXX = covariances.getValue(parent, parent);
        double covxxInv = 1.0 / covXX;
        double covxy = getCovMatrix().getValue(i, parent);
        double b = covxxInv * covxy;
        residualVariance -= covxy * b;

        if (residualVariance <= 0 && verbose) {
            out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / getCovMatrix().getValue(i, i)));
            return Double.NaN;
        }

        double c = getPenaltyDiscount();
        return score(residualVariance, n, p, c);
    }

    // Calculates the BIC score.
    private double score(double residualVariance, int n, int p, double c) {
        return -n * Math.log(residualVariance) - c * getK(p) * Math.log(n);
    }

    // Degrees of freedom--a stringent choice.
    private int getK(int p) {
        return (p + 1) * (p + 2) / 2;
//        return p + 1;
    }

    // Specialized scoring method for a no parents. Used to speed up the effect edges search.
    private double localSemScoreSingleParent(int i) {
        ICovarianceMatrix cov = getCovMatrix();

        double residualVariance = cov.getValue(i, i);
        int n = sampleSize();
        int p = 0;

        if (residualVariance <= 0 && verbose) {
            out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / cov.getValue(i, i)));
            return Double.NaN;
        }

        double c = getPenaltyDiscount();
        return score(residualVariance, n, p, c);
    }

    // Just some fields to help avoid duplication in the effect edges search.
    int[] lastSquareIndices = new int[0];
    TetradMatrix lastSquareCovs = new TetradMatrix(0, 0);
    int[] lastColumnIndices = new int[0];
    TetradVector lastColumnCov = new TetradVector(0);
    int lastK = -1;

    private TetradMatrix getSelection1(ICovarianceMatrix cov, int[] rows) {
        TetradMatrix m = new TetradMatrix(rows.length, rows.length);

        for (int i = 0; i < rows.length; i++) {
            for (int j = i; j < rows.length; j++) {
                final double value = cov.getValue(rows[i], rows[j]);
                m.set(i, j, value);
                m.set(j, i, value);
            }
        }

        lastSquareIndices = rows;
        lastSquareCovs = m;

        return m;
    }

    private TetradVector getSelection2(ICovarianceMatrix cov, int[] rows, int k) {
        TetradVector m = new TetradVector(rows.length);

        for (int i = 0; i < rows.length; i++) {
            final double value = cov.getValue(rows[i], k);
            m.set(i, value);
        }

        lastColumnCov = m;
        lastColumnIndices = rows;
        lastK = k;

        return m;
    }

    // This is supposed to print a smallest subset of parents that causes a singular matrix exception.
    // Doesn't quite work but should.
    private void printMinimalLinearlyDependentSet(int[] parents, ICovarianceMatrix cov) {
        List<Node> _parents = new ArrayList<>();
        for (int p : parents) _parents.add(variables.get(p));

        DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            int[] sel = new int[choice.length];
            List<Node> _sel = new ArrayList<>();
            for (int m = 0; m < choice.length; m++) {
                sel[m] = parents[m];
                _sel.add(variables.get(sel[m]));
            }

            TetradMatrix m = cov.getSelection(sel, sel);

            try {
                m.inverse();
            } catch (Exception e2) {
//                e2.printStackTrace();
                out.println("### Linear dependence among variables: " + _sel);
            }
        }
    }

    private int sampleSize() {
        return this.sampleSize;
    }

    private List<Node> getVariables() {
        return variables;
    }

    private ICovarianceMatrix getCovMatrix() {
        return covariances;
    }

    private boolean isDiscrete() {
        return discrete;
    }

    // Stores the graph, if its score knocks out one of the top ones.
    private void storeGraph(Graph graph) {
        if (numPatternsToStore < 1) return;

        if (topGraphs.isEmpty() || score > topGraphs.first().getScore()) {
            Graph graphCopy = new EdgeListGraphSingleConnections(graph);

            topGraphs.add(new ScoredGraph(graphCopy, score));

            if (topGraphs.size() > getNumPatternsToStore()) {
                topGraphs.remove(topGraphs.first());
            }
        }
    }
}






