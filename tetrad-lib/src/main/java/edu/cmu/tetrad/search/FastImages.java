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
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.*;

import java.beans.PropertyChangeListener;
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
 * the graph. This is a restricted form of the faithfulness assumption, something GES does not assume. This
 * faithfulness assumption needs to be explicitly turned on using setFaithfulnessAssumed(true).
 * <p>
 * A number of other optimizations were added 5/2015. See code for details.
 *
 * @author Ricardo Silva, Summer 2003
 * @author Joseph Ramsey, Revisions 5/2015
 */
public final class FastImages implements GraphSearch, GraphScorer, IImages {

    /**
     * The covariance matrix for the data set.
     */
    private List<TetradMatrix> covariances;

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
     * If non-null, edges not adjacent in this graph will not be added.
     */
    private Graph boundGraph = null;

    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    /**
     * Penalty discount--the BIC penalty is multiplied by this (for continuous variables).
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
    private boolean otherDof = false;

    //===========================CONSTRUCTORS=============================//

    /**
     * The data set must either be all continuous or coariance matrices, or all discrete.
     */
    public FastImages(List<DataSet> dataSets, boolean allContinuous) {
        List<DataModel> dataModels = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            dataModels.add(dataSet);
        }

        setDataModels(dataModels);
    }

    public FastImages(List<DataModel> dataModels) {
        setDataModels(dataModels);
    }

    /**
     * Array of variable names from the data set, in order.
     */
    private String varNames[];

    /**
     * The data set, various variable subsets of which are to be scored.
     */
    private List<DataSet> dataSets;

    private ArrayList<ICovarianceMatrix> covarianceMatrices = new ArrayList<>();
    private Map<DataModel, Set<Node>> missingVariables;
    private boolean containsMissingVariables;

    private void setDataModels(List<DataModel> dataModels) {
        List<String> varNames = dataModels.get(0).getVariableNames();

        for (int i = 0; i < dataModels.size(); i++) {
            List<String> _varNames = dataModels.get(i).getVariableNames();

            if (!varNames.equals(_varNames)) {
                throw new IllegalArgumentException("Variable names not consistent.");
            }
        }

        this.varNames = varNames.toArray(new String[varNames.size()]);

        DataModel model0 = dataModels.get(0);

        if (model0 instanceof DataSet) {
            this.sampleSize = ((DataSet) model0).getNumRows();
            this.variables = dataModels.get(0).getVariables();

            if (((DataSet) model0).isDiscrete()) {
                this.dataSets = new ArrayList<>();
                this.discrete = true;

                for (DataModel dataModel : dataModels) {
                    if (((DataSet) dataModel).isDiscrete()) {
                        this.dataSets.add((DataSet) dataModel);
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
            }
        } else if (model0 instanceof ICovarianceMatrix) {
            this.sampleSize = ((ICovarianceMatrix) model0).getSampleSize();
            this.variables = dataModels.get(0).getVariables();
        }

        if (!isDiscrete()) {
            this.covariances = new ArrayList<TetradMatrix>();

            for (DataModel dataModel : dataModels) {
                if (dataModel instanceof ICovarianceMatrix) {
                    covarianceMatrices.add((ICovarianceMatrix) dataModel);
                } else {
                    ICovarianceMatrix cov = new CovarianceMatrix((DataSet) dataModel);
                    covarianceMatrices.add(cov);
                    this.covariances.add(cov.getMatrix());
                }
            }
        }

        missingVariables = new HashMap<DataModel, Set<Node>>();
        containsMissingVariables = false;

        for (DataModel dataModel : dataModels) {
            missingVariables.put(dataModel, new HashSet<Node>());
        }

        for (DataModel dataModel : dataModels) {
            if (dataModel instanceof DataSet) {
                DataSet dataSet = (DataSet) dataModel;

                for (Node node : dataSet.getVariables()) {
                    int index = dataSet.getVariables().indexOf(node);
                    boolean missing = true;

                    for (int i = 0; i < dataSet.getNumRows(); i++) {
                        if (!Double.isNaN(dataSet.getDouble(i, index))) {
                            missing = false;
                            break;
                        }
                    }

                    if (missing) {
                        missingVariables.get(dataSet).add(node);
                        containsMissingVariables = true;
                    }
                }
            }
        }

        setKnowledge(dataModels.get(0).getKnowledge());
    }


    //==========================PUBLIC METHODS==========================//

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

    @Override
    public boolean isAggressivelyPreventCycles() {
        return false;
    }

    @Override
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {

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

    @Override
    public Graph search(List<Node> nodes) {
        return null;
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

    @Override
    public void setElapsedTime(long elapsedTime) {

    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {

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
            throw new IllegalArgumentException("Penalty discount must be >= 0: "
                    + penaltyDiscount);
        }

        this.penaltyDiscount = penaltyDiscount;
    }

    @Override
    public int getMaxNumEdges() {
        return 0;
    }

    @Override
    public void setMaxNumEdges(int maxNumEdges) {

    }

    @Override
    public double getModelScore() {
        return 0;
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

    @Override
    public Map<Edge, Integer> getBoostrapCounts(int numBootstraps) {
        return null;
    }

    @Override
    public String bootstrapPercentagesString(int numBootstraps) {
        return null;
    }

    @Override
    public String gesCountsString() {
        return null;
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
     * If non-null, edges not adjacent in this graph will not be added.
     */
    public void setBoundGraph(Graph boundGraph) {
        this.boundGraph = GraphUtils.replaceNodes(boundGraph, getVariables());
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
                                double s1 = localSemScore(hashIndices.get(y), new int[]{hashIndices.get(x)});
                                double s2 = localSemScore(hashIndices.get(y), new int[]{});
                                bump = s1 - s2;
                            } else {
                                bump = scoreGraphChange(y, Collections.singleton(x), emptySet);
                            }
//
                            if (bump > -getPenaltyDiscount() * Math.log(sampleSize())) {
                                final Edge edge = Edges.undirectedEdge(x, y);
                                if (boundGraph != null && !boundGraph.isAdjacentTo(edge.getNode1(), edge.getNode2()))
                                    continue;
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

        out.println("Elapsed getEffectEdges = " + (stop - start) + " ms");

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


            double bump = insertEval(a, b, s, naYX, graph);

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

                        calculateArrowsBackward(w, x, graph);
                        calculateArrowsBackward(w, y, graph);
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

    public void setOtherDof(boolean otherDof) {
        this.otherDof = otherDof;
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
    private double insertEval(Node x, Node y, Set<Node> t, Set<Node> naYX, Graph graph) {
        Set<Node> set1 = new HashSet<>(naYX);
        set1.addAll(t);
        set1.addAll(graph.getParents(y));
        set1.remove(x);

        Set<Node> set2 = new HashSet<>(set1);
        set1.add(x);

        return scoreGraphChange(y, set1, set2);
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

        return scoreGraphChange(y, set1, set2);
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

        if (boundGraph != null && !boundGraph.isAdjacentTo(x, y)) return;
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
            if (boundGraph != null && !boundGraph.isAdjacentTo(_t, y)) return;
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
        for (Node y : dag.getNodes()) {
            Set<Node> parents = new HashSet<>(dag.getParents(y));
            int index = hashIndices.get(y);
            int parentIndices[] = new int[parents.size()];
            Iterator<Node> pi = parents.iterator();
            int count = 0;
            while (pi.hasNext()) {
                Node nextParent = pi.next();
                parentIndices[count++] = hashIndices.get(nextParent);
            }

            double _score;

            if (this.isDiscrete()) {
                _score = localDiscreteScore(index, parentIndices);
            } else {
                _score = localSemScore(index, parentIndices);
            }

            if (!Double.isNaN(_score))
                score += _score;
        }
        return score;
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model.
     */
    private double localSemScore(int i, int[] parents) {
        double sum = 0.0;

        for (int d = 0; d < numDataSets(); d++) {
            double score = localSemScoreOneDataSet(d, i, parents);

            if (!Double.isNaN(score)) {
                sum += score;
            }
        }

        return sum;
    }

    private int numDataSets() {
        return getCovMatrices().size();
    }

    private double trimAlpha = 0.0;

    public double getTrimAlpha() {
        return trimAlpha;
    }

    public void setTrimAlpha(double trimAlpha) {
        if (trimAlpha < 0.0) {// || trimAlpha > 0.5) {
            throw new IllegalArgumentException("Clip must be in [0, 1]");
        }

        this.trimAlpha = trimAlpha;
    }

    // Scores the difference between y with 'parents1' as parents an y with 'parents2' as parents.
    private double scoreGraphChange(Node y, Set<Node> parents1,
                                    Set<Node> parents2) {
        int yIndex = hashIndices.get(y);
        int parentIndices1[] = new int[parents1.size()];

        int count = 0;
        for (Node aParents1 : parents1) {
            parentIndices1[count++] = (hashIndices.get(aParents1));
        }

        int parentIndices2[] = new int[parents2.size()];

        int count2 = 0;
        for (Node aParents2 : parents2) {
            parentIndices2[count2++] = (hashIndices.get(aParents2));
        }

        List<Double> diffs = new ArrayList<Double>();

        int numDataSets = numDataSets();

        for (int d = 0; d < numDataSets; d++) {
            double score1 = localSemScoreOneDataSet(d, yIndex, parentIndices1);
            double score2 = localSemScoreOneDataSet(d, yIndex, parentIndices2);
            double diff = score1 - score2;
            diffs.add(diff);
        }

        Collections.sort(diffs);

        double sum = 0.0;
        int _count = 0;

        int from = (int) Math.floor(((double) (numDataSets - 1)) * (trimAlpha));
//        int to = (int) Math.ceil(((double) (numDataSets - 1)) * (1.0 - trimAlpha));
        int to = numDataSets - 1; //(int) Math.ceil(((double) (numDataSets - 1)) * (1.0 - trimAlpha));

        for (int m = from; m <= to; m++) {
            double diff = diffs.get(m);

            if (diff != 0) {
                sum += diff;
                _count++;
            }
        }

        return sum / _count;
    }

    private double localSemScoreOneDataSet(int dataIndex, int i, int[] parents) {
        TetradMatrix cov = getCovMatrices().get(dataIndex);
        double residualVariance = cov.get(i, i);
        int n = sampleSize();
        int p = parents.length;

        if (containsMissingVariables) {
            DataSet data = dataSets.get(dataIndex);

            if (missingVariables.get(data).contains(data.getVariable(i))) {
                return 0;
            }
        }

        try {
            TetradMatrix covxx = cov.getSelection(parents, parents);
            TetradMatrix covxxInv = covxx.inverse();
            TetradVector covxy = cov.getSelection(parents, new int[]{i}).getColumn(0);
            TetradVector b = covxxInv.times(covxy);
            residualVariance -= covxy.dotProduct(b);
        } catch (Exception e) {
            e.printStackTrace();
            throwMinimalLinearDependentSet(parents, cov);
        }

        if (residualVariance <= 0) {
            if (verbose) {
                List<Node> _parents = new ArrayList<Node>();

                for (int k : parents) {
                    _parents.add(variables.get(k));
                }

                System.out.println("Negative residual variance: " + residualVariance +
                        " for " + variables.get(i) + " given " + _parents);
            }
            return Double.NaN;
        }

        double c = getPenaltyDiscount();
        return score(residualVariance, n, p, c);
    }

    // Calculates the BIC score.
    private double score(double residualVariance, int n, int p, double c) {
//        return -n * Math.log(residualVariance) - n * Math.log(2 * Math.PI) - n - dof(n, p) * c * (Math.log(n));
        return -n * Math.log(residualVariance) - c * dof(n, p) * Math.log(n);
    }

    private int dof(int n, int p) {
        if (otherDof) {
            return (p + 1) * (p + 2) / 2;
        } else {
            return 1 + 2 * p;
        }
    }

    private void throwMinimalLinearDependentSet(int[] parents, TetradMatrix cov) {
        List<Node> _parents = new ArrayList<Node>();
        for (int p : parents) _parents.add(variables.get(p));

        DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            int[] sel = new int[choice.length];
            List<Node> _sel = getSel(parents, choice, sel);

            TetradMatrix m = cov.getSelection(sel, sel);

            try {
                m.inverse();
            } catch (Exception e2) {
                throw new RuntimeException("Linear dependence among variables: " + _sel);
            }
        }
    }

    private List<Node> getSel(int[] parents, int[] choice, int[] sel) {
        List<Node> _sel = new ArrayList<Node>();

        for (int m = 0; m < choice.length; m++) {
            sel[m] = parents[m];
            _sel.add(variables.get(sel[m]));
        }
        return _sel;
    }

    private List<TetradMatrix> getCovMatrices() {
        return covariances;
    }

    // Compute the local BDeu score of (i, parents(i)). See (Chickering, 2002).
    private double localDiscreteScore(int i, int parents[]) {
        return getDiscreteScore().localScore(i, parents);
    }

    private TetradMatrix getSelection1(ICovarianceMatrix cov, int[] rows) {
//        return cov.getSelection(rows, rows);
//
        TetradMatrix m = new TetradMatrix(rows.length, rows.length);

        for (int i = 0; i < rows.length; i++) {
            for (int j = i; j < rows.length; j++) {
                final double value = cov.getValue(rows[i], rows[j]);
                m.set(i, j, value);
                m.set(j, i, value);
            }
        }

        return m;
    }

    private TetradVector getSelection2(ICovarianceMatrix cov, int[] rows, int k) {
//        return cov.getSelection(rows, new int[]{k}).getColumn(0);
//
        TetradVector m = new TetradVector(rows.length);

        for (int i = 0; i < rows.length; i++) {
            final double value = cov.getValue(rows[i], k);
            m.set(i, value);
        }

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
            List<Node> _sel = getSel(parents, choice, sel);

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


    public Map<Edge, Double> averageStandardizedCoefficients() {
        if (returnGraph == null) {
            returnGraph = search();
        }

        return averageStandardizedCoefficients(returnGraph);
    }

    public Map<Edge, Double> averageStandardizedCoefficients(Graph graph) {

        Graph dag = SearchGraphUtils.dagFromPattern(graph);
        Map<Edge, Double> coefs = new HashMap<Edge, Double>();

        for (DataSet dataSet : dataSets) {
            SemPm pm = new SemPm(dag);
            Graph _graph = pm.getGraph();
            SemEstimator estimator = new SemEstimator(dataSet, pm);
            SemIm im = estimator.estimate();
            StandardizedSemIm im2 = new StandardizedSemIm(im);

            for (Edge edge : _graph.getEdges()) {
                edge = translateEdge(edge, dag);

                if (coefs.get(edge) == null) {
                    coefs.put(edge, 0.0);
                }

                coefs.put(edge, coefs.get(edge) + im2.getParameterValue(edge));
            }
        }

        for (Edge edge : coefs.keySet()) {
            coefs.put(edge, coefs.get(edge) / (double) numDataSets());
        }

        return coefs;
    }

    private Graph returnGraph;

    public String averageStandardizedCoefficientsString() {
        if (returnGraph == null) {
            returnGraph = search();
        }

        Graph graph = new Dag(GraphUtils.randomDag(returnGraph.getNodes(), 0, 12, 30, 15, 15, true));
        return averageStandardizedCoefficientsString(graph);
    }

    public String averageStandardizedCoefficientsString(Graph graph) {
        Map<Edge, Double> coefs = averageStandardizedCoefficients(graph);
        return edgeCoefsString(coefs, new ArrayList<Edge>(graph.getEdges()), "Estimated adjacencyGraph",
                "Average standardized coefficient");
    }

    public String logEdgeBayesFactorsString(Graph dag) {
        Map<Edge, Double> coefs = logEdgeBayesFactors(dag);
        return logBayesPosteriorFactorsString(coefs, scoreDag(dag));
    }

    public Map<Edge, Double> logEdgeBayesFactors(Graph dag) {
        Map<Edge, Double> logBayesFactors = new HashMap<Edge, Double>();
        double withEdge = scoreDag(dag);

        for (Edge edge : dag.getEdges()) {
            dag.removeEdge(edge);
            double withoutEdge = scoreDag(dag);
            double difference = withoutEdge - withEdge;
            logBayesFactors.put(edge, difference);
            dag.addEdge(edge);
        }

        return logBayesFactors;
    }


    private Edge translateEdge(Edge edge, Graph graph) {
        Node node1 = graph.getNode(edge.getNode1().getName());
        Node node2 = graph.getNode(edge.getNode2().getName());
        return new Edge(node1, node2, edge.getEndpoint1(), edge.getEndpoint2());
    }

    private String gesEdgesString(Map<Edge, Integer> counts, List<DataSet> dataSets) {
        if (returnGraph == null) {
            returnGraph = search();
        }

        return edgePercentagesString(counts, new ArrayList<Edge>(returnGraph.getEdges()),
                "Percentage of GES results each edge participates in", dataSets.size());
    }


    /**
     * Bootstraps images coefs at a particular penalty level.
     *
     * @param dataSets      The data sets from which bootstraps are drawn. These must share the same variable set, be
     *                      continuous, but may have different sample sizes.
     * @param nodes         The nodes over which edge coefs are to be done. Why not specify this in advance?
     * @param knowledge     Knowledge under which IMaGES should operate.
     * @param numBootstraps The number of bootstrap samples to be drawn.
     * @param penalty       The penalty discount at which the bootstrap analysis is to be done.
     * @return A map from edges to coefs, where the edges are over the nodes of the datasets.
     */
    private Map<Edge, Integer> bootstrapImagesCounts(List<DataSet> dataSets, List<Node> nodes, IKnowledge knowledge,
                                                     int numBootstraps, double penalty) {
        List<Node> dataVars = dataSets.get(0).getVariables();

        for (DataSet dataSet : dataSets) {
            if (!dataSet.getVariables().equals(dataVars)) {
                throw new IllegalArgumentException("Data sets must share the same variable set.");
            }
        }

        Map<Edge, Integer> counts = new HashMap<Edge, Integer>();

        for (int i = 0; i < numBootstraps; i++) {
            List<DataSet> bootstraps = new ArrayList<DataSet>();

            for (DataSet dataSet : dataSets) {
                bootstraps.add(DataUtils.getBootstrapSample(dataSet, dataSet.getNumRows()));
//                bootstraps.add(dataSet);
            }

            Images images = new Images(bootstraps);
            images.setPenaltyDiscount(penalty);

//            ImagesFirstNontriangular images = new ImagesFirstNontriangular(bootstraps);

            images.setKnowledge(knowledge);
            Graph pattern = images.search();
            incrementCounts(counts, pattern, nodes);
        }

        return counts;
    }

    private void incrementCounts(Map<Edge, Integer> counts, Graph pattern, List<Node> nodes) {
        Graph _pattern = GraphUtils.replaceNodes(pattern, nodes);

        for (Edge e : _pattern.getEdges()) {
            if (counts.get(e) == null) {
                counts.put(e, 0);
            }

            counts.put(e, counts.get(e) + 1);
        }
    }

    /**
     * Prints edge coefs, with edges in the order of the adjacencies in <code>edgeList</code>.
     *
     * @param counts   A map from edges to coefs.
     * @param edgeList A list of edges, the true edges or estimated edges.
     */
    private String edgePercentagesString(Map<Edge, Integer> counts, List<Edge> edgeList,
                                         String percentagesLabel, int numBootstraps) {
        NumberFormat nf = new DecimalFormat("0");
        StringBuilder builder = new StringBuilder();

        if (percentagesLabel != null) {
            builder.append("\n").append(percentagesLabel).append(":\n\n");
        }

        for (int i = 0; i < edgeList.size(); i++) {
            Edge edge = edgeList.get(i);
            int total = 0;

            for (Edge _edge : new HashMap<Edge, Integer>(counts).keySet()) {
                if (_edge.getNode1() == edge.getNode1() && _edge.getNode2() == edge.getNode2()
                        || _edge.getNode1() == edge.getNode2() && _edge.getNode2() == edge.getNode1()) {
                    total += counts.get(_edge);
                    double percentage = counts.get(_edge) / (double) numBootstraps * 100.;
                    builder.append(i + 1).append(". ").append(_edge).append(" ").append(nf.format(percentage)).append("%\n");
                    counts.remove(_edge);
                }
            }

            double percentage = total / (double) numBootstraps * 100.;
            builder.append("   (Sum = ").append(nf.format(percentage)).append("%)\n\n");
        }

        // The left over edges.
        builder.append("Edges not adjacent in the estimated pattern:\n\n");

//        for (Edge edge : coefs.keySet()) {
//            double percentage = coefs.get(edge) / (double) numBootstraps * 100.;
//            builder.append(edge + " " + nf.format(percentage) + "%\n");
//        }

        for (Edge edge : new ArrayList<Edge>(counts.keySet())) {
            if (!counts.keySet().contains(edge)) continue;

            int total = 0;

            for (Edge _edge : new HashMap<Edge, Integer>(counts).keySet()) {
                if (_edge.getNode1() == edge.getNode1() && _edge.getNode2() == edge.getNode2()
                        || _edge.getNode1() == edge.getNode2() && _edge.getNode2() == edge.getNode1()) {
                    total += counts.get(_edge);
                    double percentage = counts.get(_edge) / (double) numBootstraps * 100.;
                    builder.append(_edge).append(" ").append(nf.format(percentage)).append("%\n");
                    counts.remove(_edge);
                }
            }

            double percentage = total / (double) numBootstraps * 100.;
            builder.append("   (Sum = ").append(nf.format(percentage)).append("%)\n\n");
        }

        builder.append("\nThe estimated pattern, for reference:\n\n");

        for (int i = 0; i < edgeList.size(); i++) {
            Edge edge = edgeList.get(i);
            builder.append(i + 1).append(". ").append(edge).append("\n");
        }

        return builder.toString();
    }

    private String edgeCoefsString(Map<Edge, Double> coefs, List<Edge> edgeList, String edgeListLabel,
                                   String percentagesLabel) {
        NumberFormat nf = new DecimalFormat("0.00");
        StringBuilder builder = new StringBuilder();

        builder.append("\n").append(edgeListLabel).append(":\n\n");

        for (int i = 0; i < edgeList.size(); i++) {
            Edge edge = edgeList.get(i);
            builder.append(i + 1).append(". ").append(edge).append("\n");
        }

        builder.append("\n").append(percentagesLabel).append(":\n\n");

        for (int i = 0; i < edgeList.size(); i++) {
            Edge edge = edgeList.get(i);

            for (Edge _edge : new HashMap<Edge, Double>(coefs).keySet()) {
                if (_edge.getNode1() == edge.getNode1() && _edge.getNode2() == edge.getNode2()
                        || _edge.getNode1() == edge.getNode2() && _edge.getNode2() == edge.getNode1()) {
                    double coef = coefs.get(_edge);
                    builder.append(i + 1).append(". ").append(_edge).append(" ").append(nf.format(coef)).append("\n");
                    coefs.remove(_edge);
                }
            }
        }


        return builder.toString();
    }

    private String logBayesPosteriorFactorsString(final Map<Edge, Double> coefs, double modelScore) {
        NumberFormat nf = new DecimalFormat("0.00");
        StringBuilder builder = new StringBuilder();

        SortedMap<Edge, Double> sortedCoefs = new TreeMap<Edge, Double>(new Comparator<Edge>() {
            public int compare(Edge edge1, Edge edge2) {
                return coefs.get(edge1).compareTo(coefs.get(edge2));
            }
        });

        sortedCoefs.putAll(coefs);

        builder.append("Model score: ").append(nf.format(modelScore)).append("\n\n");

        builder.append("Edge Posterior Log Bayes Factors:\n\n");

        builder.append("For a DAG in the IMaGES pattern with model score m, for each edge e in the " +
                "DAG, the model score that would result from removing each edge, calculating " +
                "the resulting model score m(e), and then reporting m(e) - m. The score used is " +
                "the IMScore, L - SUM_i{kc ln n(i)}, L is the maximum likelihood of the model, " +
                "k isthe number of parameters of the model, n(i) is the sample size of the ith " +
                "data set, and c is the penalty discount. Note that the more negative the score, " +
                "the more important the edge is to the posterior probability of the IMaGES model. " +
                "Edges are given in order of their importance so measured.\n\n");

        int i = 0;

        for (Edge edge : sortedCoefs.keySet()) {
            builder.append(++i).append(". ").append(edge).append(" ").append(nf.format(sortedCoefs.get(edge))).append("\n");
        }

        return builder.toString();
    }

}






