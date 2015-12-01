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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;


/**
 * GesSearch is an implentation of the GES algorithm, as specified in Chickering (2002) "Optimal structure
 * identification with greedy search" Journal of Machine Learning Research. It works for both BayesNets and SEMs.
 * <p>
 * Some code optimization could be done for the scoring part of the graph for discrete models (method scoreGraphChange).
 * Some of Andrew Moore's approaches for caching sufficient statistics, for instance.
 *
 * @author Ricardo Silva, Summer 2003
 * @author Joseph Ramsey, Revisions 10/2005
 */

public final class GesOrienter implements GraphSearch, GraphScorer, Reorienter {

    /**
     * The data set, various variable subsets of which are to be scored.
     */
    private DataSet dataSet;

    /**
     * The covariance matrix for the data set.
     */
    private ICovarianceMatrix covariances;

    /**
     * Sample size, either from the data set or from the variances.
     */
    private int sampleSize;

    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * Map from variables to their column indices in the data set.
     */
    private HashMap<Node, Integer> hashIndices;

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
     * Caches scores for discrete search.
     */
    private final LocalScoreCache localScoreCache = new LocalScoreCache();

    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles = false;

    /**
     * Listeners for graph change events.
     */
    private transient List<PropertyChangeListener> listeners;

    /**
     * Penalty discount--the BIC penalty is multiplied by this (for continuous variables).
     */
    private double penaltyDiscount = 1.0;

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
    private SortedSet<ScoredGraph> topGraphs = new TreeSet<ScoredGraph>();

    /**
     * The number of top patterns to store.
     */
    private int numPatternsToStore = 0;

    // Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
    private SortedSet<Arrow> sortedArrows = new ConcurrentSkipListSet<Arrow>();

    // Arrows added to sortedArrows for each <i, j>.
    private Map<OrderedPair<Node>, Set<Arrow>> lookupArrows;

    /**
     * True if graphs should be stored.
     */
    private boolean log = true;
    private boolean verbose = false;

    private int NTHREADS = Runtime.getRuntime().availableProcessors() * 5;
    //    private boolean checkedKnowledgeEmpty = false;
//    private boolean knowledgeEmpty = false;
    private ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();
    private double score;

    private Graph graphToOrient;


    //===========================CONSTRUCTORS=============================//

    public GesOrienter(DataSet dataSet) {
        setDataSet(dataSet);
        if (dataSet.isDiscrete()) {
            BDeuScore score = new BDeuScore(dataSet);
            score.setSamplePrior(10);
            score.setStructurePrior(0.001);
        }
        setStructurePrior(0.001);
        setSamplePrior(10.);
    }

    public GesOrienter(ICovarianceMatrix covMatrix) {
        setCovMatrix(covMatrix);
        setStructurePrior(0.001);
        setSamplePrior(10.);
    }

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


    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * Greedy equivalence search: Start from the empty graph, add edges till model is significant. Then start deleting
     * edges till a minimum is achieved.
     *
     * @return the resulting Pattern.
     */
    public Graph search() {

        Graph graph;

        if (initialGraph == null) {
            graph = new EdgeListGraphSingleConnections(getVariables());
        } else {
            graph = new EdgeListGraphSingleConnections(initialGraph);
        }

        fireGraphChange(graph);
        buildIndexing(graph);
        addRequiredEdges(graph);

        topGraphs.clear();

        storeGraph(graph);

        List<Node> nodes = graph.getNodes();

        long start = System.currentTimeMillis();
        score = 0.0;

        // Do forward search.
        fes(graph, nodes);

        // Do backward search.
        bes(graph);

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - start;
        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.flush();

        return graph;
    }

    public Graph search(List<Node> nodes) {
        long startTime = System.currentTimeMillis();
        localScoreCache.clear();

        if (!dataSet().getVariables().containsAll(nodes)) {
            throw new IllegalArgumentException(
                    "All of the nodes must be in " + "the supplied data set.");
        }

        Graph graph;

        if (initialGraph == null) {
            graph = new EdgeListGraphSingleConnections(nodes);
        } else {
            initialGraph = GraphUtils.replaceNodes(initialGraph, variables);
            graph = new EdgeListGraphSingleConnections(initialGraph);
        }

        topGraphs.clear();

        buildIndexing(graph);
        addRequiredEdges(graph);
        score = 0.0;

        // Do forward search.
        fes(graph, nodes);

        // Do backward search.
        bes(graph);

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;
        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.flush();

        return graph;
    }

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

    public void setStructurePrior(double structurePrior) {
        if (getDiscreteScore() != null) {
            getDiscreteScore().setStructurePrior(structurePrior);
        }
    }

    public void setSamplePrior(double samplePrior) {
        if (getDiscreteScore() != null) {
            getDiscreteScore().setSamplePrior(samplePrior);
        }
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        getListeners().add(l);
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount < 0) {
            throw new IllegalArgumentException("Penalty discount must be >= 0: "
                    + penaltyDiscount);
        }

        this.penaltyDiscount = penaltyDiscount;
    }

    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    public double getScore(Graph dag) {
        return scoreDag(dag);
    }

    public SortedSet<ScoredGraph> getTopGraphs() {
        return topGraphs;
    }

    public int getNumPatternsToStore() {
        return numPatternsToStore;
    }

    public void setNumPatternsToStore(int numPatternsToStore) {
        if (numPatternsToStore < 0) {
            throw new IllegalArgumentException("# graphs to store must at least 0: " + numPatternsToStore);
        }

        this.numPatternsToStore = numPatternsToStore;
    }

    public LocalDiscreteScore getDiscreteScore() {
        return discreteScore;
    }

    public void setDiscreteScore(LocalDiscreteScore discreteScore) {
        if (discreteScore.getDataSet() != dataSet) {
            throw new IllegalArgumentException("Must use the same data set.");
        }
        this.discreteScore = discreteScore;
    }


    //===========================PRIVATE METHODS========================//

    /**
     * Forward equivalence search.
     *
     * @param graph The graph in the state prior to the forward equivalence search.
     */
    private void fes(Graph graph, List<Node> nodes) {
        TetradLogger.getInstance().log("info", "** FORWARD EQUIVALENCE SEARCH");

        lookupArrows = new HashMap<OrderedPair<Node>, Set<Arrow>>();

        initializeArrowsForward(nodes);

        while (!sortedArrows.isEmpty()) {
            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = arrow.getX();
            Node y = arrow.getY();

            clearArrow(x, y);

            if (graph.isAdjacentTo(x, y)) {
                continue;
            }

            if (!validInsert(x, y, arrow.getHOrT(), arrow.getNaYX(), graph)) {
                continue;
            }

            List<Node> t = arrow.getHOrT();
            double bump = arrow.getBump();

            Set<Edge> edges = graph.getEdges();

            insert(x, y, t, graph, bump);
            score += bump;
            rebuildPattern(graph);

            // Try to avoid duplicating scoring calls. First clear out all of the edges that need to be changed,
            // then change them, checking to see if they're already been changed. I know, roundabout, but there's
            // a performance boost.
            for (Edge edge : graph.getEdges()) {
                if (!edges.contains(edge)) {
                    reevaluateForward(graph, nodes, edge.getNode1(), edge.getNode2());
                }
            }

            storeGraph(graph);
        }
    }

    private void bes(Graph graph) {
        TetradLogger.getInstance().log("info", "** BACKWARD EQUIVALENCE SEARCH");

        initializeArrowsBackward(graph);

        while (!sortedArrows.isEmpty()) {
            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = arrow.getX();
            Node y = arrow.getY();

            clearArrow(x, y);

            if (!validDelete(arrow.getHOrT(), arrow.getNaYX(), graph)) {
                continue;
            }

            List<Node> h = arrow.getHOrT();
            double bump = arrow.getBump();

            delete(x, y, h, graph, bump);
            score += bump;
            rebuildPattern(graph);

            storeGraph(graph);

            initializeArrowsBackward(graph);  // Rebuilds Arrows from scratch each time. Fast enough for backwards.
        }
    }

    // Expensive
    // Concurrent.
    private void initializeArrowsForward(final List<Node> nodes) {
        final List<Node> emptyList = new ArrayList<Node>(0);
        final Set<Node> emptySet = new HashSet<Node>(0);
        List<Callable<Boolean>> callables = new ArrayList<Callable<Boolean>>();

        final List<Edge> edges = new ArrayList<Edge>(graphToOrient.getEdges());

        for (int t = 0; t < NTHREADS; t++) {
            final int _t = t;

            Callable<Boolean> worker = new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    int chunk = edges.size() / NTHREADS + 1;

                    for (int j = _t * chunk; j < Math.min((_t + 1) * chunk, nodes.size()); j++) {
                        if (log && verbose) {
                            if ((j + 1) % 10 == 0) System.out.println("Initializing arrows forward: " + (j + 1));
                        }

                        Node x = edges.get(j).getNode1();
                        Node y = edges.get(j).getNode2();

                        if (!graphToOrient.isAdjacentTo(x, y)) {
                            continue;
                        }

                        if (!knowledgeEmpty()) {
                            if (getKnowledge().isForbidden(x.getName(), y.getName())) {
                                continue;
                            }

                            if (!validSetByKnowledge(y, emptyList)) {
                                continue;
                            }
                        }

                        double bump = scoreGraphChange(y, Collections.singleton(x), emptySet);

                        if (bump > 0.0) {
                            Arrow arrow = new Arrow(bump, x, y, emptyList, emptyList);
                            sortedArrows.add(arrow);
                            addLookupArrow(x, y, arrow);

                            Arrow arrow2 = new Arrow(bump, y, x, emptyList, emptyList);
                            sortedArrows.add(arrow2);
                            addLookupArrow(y, x, arrow);
                        }
                    }

                    return true;
                }
            };

            callables.add(worker);
        }

        pool.invokeAll(callables);
    }

    private boolean knowledgeEmpty() {
//        if (!checkedKnowledgeEmpty) {
//            knowledgeEmpty = knowledge.isEmpty();
//            checkedKnowledgeEmpty = true;
//        }
//
//        return knowledgeEmpty;

//        return knowledge.isEmpty();
        return false;
    }

    private void initializeArrowsBackward(Graph graph) {
        sortedArrows.clear();
        lookupArrows.clear();

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (!knowledgeEmpty()) {
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

    private void reevaluateForward(final Graph graph, final List<Node> nodes, final Node x, final Node y) {
        List<Callable<Boolean>> callables = new ArrayList<Callable<Boolean>>();

        Set<Node> _W = new HashSet<Node>();

        for (Node r : graphToOrient.getAdjacentNodes(x)) {
            _W.add(r);
        }

        for (Node r : graphToOrient.getAdjacentNodes(y)) {
            _W.add(r);
        }

        _W.remove(x);
        _W.remove(y);

        final List<Node> W = new ArrayList<Node>(_W);

        for (int t = 0; t < NTHREADS; t++) {
            final int _t = t;

            Callable<Boolean> worker = new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    int chunk = W.size() / NTHREADS + 1;
                    for (int _w = _t * chunk; _w < Math.min((_t + 1) * chunk, W.size()); _w++) {
                        final Node w = W.get(_w);

//                        if (w == x) continue;
//                        if (w == y) continue;

                        if (!graph.isAdjacentTo(w, x)) {
                            calculateArrowsForward(w, x, graph);

                            if (graph.isAdjacentTo(w, y)) {
                                calculateArrowsForward(x, w, graph);
                            }
                        }

                        if (!graph.isAdjacentTo(w, y)) {
                            calculateArrowsForward(w, y, graph);

                            if (graph.isAdjacentTo(w, x)) {
                                calculateArrowsForward(y, w, graph);
                            }
                        }
                    }

                    return true;
                }
            };

            callables.add(worker);
        }

        pool.invokeAll(callables);
    }

    private void calculateArrowsForward(Node x, Node y, Graph graph) {
        clearArrow(x, y);

        if (!graphToOrient.isAdjacentTo(x, y)) return;

        if (!knowledgeEmpty()) {
            if (getKnowledge().isForbidden(x.getName(), y.getName())) {
                return;
            }
        }

        List<Node> naYX = getNaYX(x, y, graph);
        List<Node> t = getTNeighbors(x, y, graph);

        DepthChoiceGenerator gen = new DepthChoiceGenerator(t.size(), t.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> s = GraphUtils.asList(choice, t);

            if (!knowledgeEmpty()) {
                if (!validSetByKnowledge(y, s)) {
                    continue;
                }
            }

            double bump = insertEval(x, y, s, naYX, graph);

            if (bump > 0.0) {
                Arrow arrow = new Arrow(bump, x, y, s, naYX);
                sortedArrows.add(arrow);
                addLookupArrow(x, y, arrow);
            }
        }
    }

    // Invalid if then nodes or graph changes.
    private void calculateArrowsBackward(Node x, Node y, Graph graph) {
        if (x == y) {
            return;
        }

        if (!graph.isAdjacentTo(x, y)) {
            return;
        }

        if (!graphToOrient.isAdjacentTo(x, y)) {
            return;
        }

        if (!knowledgeEmpty()) {
            if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                return;
            }
        }

        List<Node> naYX = getNaYX(x, y, graph);

        clearArrow(x, y);

        List<Node> _naYX = new ArrayList<Node>(naYX);
        DepthChoiceGenerator gen = new DepthChoiceGenerator(_naYX.size(), _naYX.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> H = GraphUtils.asList(choice, _naYX);

            if (!knowledgeEmpty()) {
                if (!validSetByKnowledge(y, H)) {
                    continue;
                }
            }

            double bump = deleteEval(x, y, H, naYX, graph);

            if (bump > 0.0) {
                Arrow arrow = new Arrow(bump, x, y, H, naYX);
                sortedArrows.add(arrow);
                addLookupArrow(x, y, arrow);
            }
        }
    }

    /**
     * True iff log output should be produced.
     */
    public boolean isLog() {
        return log;
    }

    public void setLog(boolean log) {
        this.log = log;
    }

    public Graph getInitialGraph() {
        return initialGraph;
    }

    // Cannot be done if the graph changes.
    public void setInitialGraph(Graph initialGraph) {
        initialGraph = GraphUtils.replaceNodes(initialGraph, variables);

        System.out.println("Initial graph variables: " + initialGraph.getNodes());
        System.out.println("Data set variables: " + variables);

        if (!new HashSet<Node>(initialGraph.getNodes()).equals(new HashSet<Node>(variables))) {
            throw new IllegalArgumentException("Variables aren't the same.");
        }

        this.initialGraph = initialGraph;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // Concurrent OK.
    private static class Arrow implements Comparable {
        private double bump;
        private Node x;
        private Node y;
        private List<Node> hOrT;
        private List<Node> naYX;

        public Arrow(double bump, Node x, Node y, List<Node> hOrT, List<Node> naYX) {
            this.bump = bump;
            this.x = x;
            this.y = y;
            this.hOrT = hOrT;
            this.naYX = naYX;
        }

        public double getBump() {
            return bump;
        }

        public Node getX() {
            return x;
        }

        public Node getY() {
            return y;
        }

        public List<Node> getHOrT() {
            return hOrT;
        }

        public List<Node> getNaYX() {
            return naYX;
        }

        // Sorting is by bump, high to low.
        public int compareTo(Object o) {
            Arrow arrow = (Arrow) o;
            return Double.compare(arrow.getBump(), getBump());
        }

        public String toString() {
            return "Arrow<" + x + "->" + y + " bump = " + bump + " t/h = " + hOrT + " naYX = " + naYX + ">";
        }
    }

    /**
     * Get all nodes that are connected to Y by an undirected edge and not adjacent to X.
     */
    private static List<Node> getTNeighbors(Node x, Node y, Graph graph) {
        List<Edge> yEdges = graph.getEdges(y);
        List<Node> tNeighbors = new ArrayList<Node>();

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
     * Evaluate the Insert(X, Y, T) operator (Definition 12 from Chickering, 2002).
     */
    private double insertEval(Node x, Node y, List<Node> t, List<Node> naYX, Graph graph) {
        Set<Node> set1 = new HashSet<Node>(naYX);
        set1.addAll(t);
        List<Node> paY = graph.getParents(y);
        set1.addAll(paY);
        Set<Node> set2 = new HashSet<Node>(set1);
        set1.add(x);

        return scoreGraphChange(y, set1, set2);
    }

    /**
     * Evaluate the Delete(X, Y, T) operator (Definition 12 from Chickering, 2002).
     */
    // Can be done concurrently.
    private double deleteEval(Node x, Node y, List<Node> h, List<Node> naYX, Graph graph) {
        List<Node> paY = graph.getParents(y);
        Set<Node> paYMinuxX = new HashSet<Node>(paY);
        paYMinuxX.remove(x);

        Set<Node> set1 = new HashSet<Node>(naYX);
        set1.removeAll(h);
        set1.addAll(paYMinuxX);

        Set<Node> set2 = new HashSet<Node>(naYX);
        set2.removeAll(h);
        set2.addAll(paY);

        return scoreGraphChange(y, set1, set2);
    }

    /*
    * Do an actual insertion
    * (Definition 12 from Chickering, 2002).
    **/
    // serial.
    private void insert(Node x, Node y, List<Node> t, Graph graph, double bump) {
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
        } else {
            int numEdges = graph.getNumEdges() - 1;
            if (verbose) {
                if (numEdges % 50 == 0) System.out.println(numEdges);
            }
        }

        if (verbose) {
            String label = trueGraph != null && trueEdge != null ? "*" : "";
            System.out.println(graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y) +
                    " " + t + " " + bump + " " + label);
        } else {
            int numEdges = graph.getNumEdges() - 1;
            if (verbose) {
                if (numEdges % 50 == 0) System.out.println(numEdges);
            }
        }

        for (Node _t : t) {
            Edge oldEdge = graph.getEdge(_t, y);

            if (oldEdge == null) throw new IllegalArgumentException("Not adjacent: " + _t + ", " + y);

            graph.removeEdge(_t, y);
            graph.addDirectedEdge(_t, y);

            if (log && verbose) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(_t, y));
                System.out.println("--- Directing " + oldEdge + " to " +
                        graph.getEdge(_t, y));
            }
        }
    }

    /**
     * Do an actual deletion (Definition 13 from Chickering, 2002).
     */
    private void delete(Node x, Node y, List<Node> subset, Graph graph, double bump) {

        Edge trueEdge = null;

        if (trueGraph != null) {
            Node _x = trueGraph.getNode(x.getName());
            Node _y = trueGraph.getNode(y.getName());
            trueEdge = trueGraph.getEdge(_x, _y);
        }

        if (log && verbose) {
            Edge oldEdge = graph.getEdge(x, y);

            String label = trueGraph != null && trueEdge != null ? "*" : "";
            TetradLogger.getInstance().log("deletedEdges", (graph.getNumEdges() - 1) + ". DELETE " + oldEdge +
                    " " + subset + " (" + bump + ") " + label);
            System.out.println((graph.getNumEdges() - 1) + ". DELETE " + oldEdge +
                    " " + subset + " (" + bump + ") " + label);
        } else {
            int numEdges = graph.getNumEdges() - 1;
            if (numEdges % 50 == 0) System.out.println(numEdges);
        }

        graph.removeEdge(x, y);

        for (Node h : subset) {
            Edge oldEdge = graph.getEdge(y, h);

            graph.removeEdge(y, h);
            graph.addDirectedEdge(y, h);

            if (log) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(y, h));
            }

            if (verbose) {
                System.out.println("--- Directing " + oldEdge + " to " +
                        graph.getEdge(y, h));
            }

            if (Edges.isUndirectedEdge(graph.getEdge(x, h))) {
                if (!graph.isAdjacentTo(x, h)) throw new IllegalArgumentException("Not adjacent: " + x + ", " + h);
                oldEdge = graph.getEdge(x, h);

                graph.removeEdge(x, h);
                graph.addDirectedEdge(x, h);

                if (log) {
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                            graph.getEdge(x, h));
                }

                if (verbose) {
                    System.out.println("--- Directing " + oldEdge + " to " +
                            graph.getEdge(x, h));
                }
            }
        }
    }

    /*
     * Test if the candidate insertion is a valid operation
     * (Theorem 15 from Chickering, 2002).
     **/

    private boolean validInsert(Node x, Node y, List<Node> t, List<Node> naYX, Graph graph) {
        List<Node> union = new ArrayList<Node>(t); // t and nayx are disjoint
        union.addAll(naYX);

        return isClique(union, graph) && !existsUnblockedSemiDirectedPath(y, x, union, graph);
    }

    /**
     * Test if the candidate deletion is a valid operation (Theorem 17 from Chickering, 2002).
     */
    private static boolean validDelete(List<Node> h, List<Node> naXY, Graph graph) {
        List<Node> list = new ArrayList<Node>(naXY);
        list.removeAll(h);
        return isClique(list, graph);
    }

    //---Background knowledge methods.

    private void addRequiredEdges(Graph graph) {
        if (true) return;
        if (knowledgeEmpty()) return;

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

                if (nodeA != null && nodeB != null && graph.isAdjacentTo(nodeA, nodeB) &&
                        !graph.isChildOf(nodeA, nodeB)) {
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

                if (nodeA != null && nodeB != null && graph.isAdjacentTo(nodeA, nodeB) &&
                        !graph.isChildOf(nodeA, nodeB)) {
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

    private String getString(KnowledgeEdge next) {
        return next.getTo();
    }

    /**
     * Use background knowledge to decide if an insert or delete operation does not orient edges in a forbidden
     * direction according to prior knowledge. If some orientation is forbidden in the subset, the whole subset is
     * forbidden.
     */

    private boolean validSetByKnowledge(Node y, List<Node> subset) {
        for (Node node : subset) {
            if (getKnowledge().isForbidden(node.getName(), y.getName())) {
                return false;
            }
        }
        return true;
    }

    //--Auxiliary methods.

    /**
     * Find all nodes that are connected to Y by an undirected edge that are adjacent to X (that is, by undirected or
     * directed edge).
     */
    private static List<Node> getNaYX(Node x, Node y, Graph graph) {
        List<Edge> yEdges = graph.getEdges(y);
        List<Node> nayx = new ArrayList<Node>();

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

    /**
     * @return true iif the given set forms a clique in the given graph.
     */
    private static boolean isClique(List<Node> nodes, Graph graph) {
        for (int i = 0; i < nodes.size() - 1; i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (!graph.isAdjacentTo(nodes.get(i), nodes.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean existsUnblockedSemiDirectedPath(Node from, Node to, List<Node> cond, Graph G) {
        Queue<Node> Q = new LinkedList<Node>();
        Set<Node> V = new HashSet<Node>();
        Q.offer(from);
        V.add(from);

        while (!Q.isEmpty()) {
            Node t = Q.remove();
            if (t == to) return true;

            for (Node u : G.getAdjacentNodes(t)) {
                Edge edge = G.getEdge(t, u);
                Node c = Edges.traverseSemiDirected(t, edge);
                if (c == null) continue;
                if (cond.contains(c)) continue;
                if (c == to) return true;

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }
        }

        return false;
    }

    /**
     * Completes a pattern that was modified by an insertion/deletion operator Based on the algorithm described on
     * Appendix C of (Chickering, 2002).
     */
    private void rebuildPattern(Graph graph) {
        SearchGraphUtils.basicPattern(graph, false);
        addRequiredEdges(graph);
        meekOrient(graph, getKnowledge());

        if (TetradLogger.getInstance().isEventActive("rebuiltPatterns")) {
            TetradLogger.getInstance().log("rebuiltPatterns", "Rebuilt pattern = " + graph);
        }
    }

    private Graph pickDag(Graph graph) {
        SearchGraphUtils.basicPattern(graph, false);
        addRequiredEdges(graph);
        boolean containsUndirected;

        do {
            containsUndirected = false;

            for (Edge edge : graph.getEdges()) {
                if (Edges.isUndirectedEdge(edge)) {
                    containsUndirected = true;
                    graph.removeEdge(edge);
                    Edge _edge = Edges.directedEdge(edge.getNode1(), edge.getNode2());
                    graph.addEdge(_edge);
                }
            }

            meekOrient(graph, getKnowledge());
        } while (containsUndirected);

        return graph;
    }

    /**
     * Fully direct a graph with background knowledge. I am not sure how to adapt Chickering's suggested algorithm above
     * (dagToPdag) to incorporate background knowledge, so I am also implementing this algorithm based on Meek's 1995
     * UAI paper. Notice it is the same implemented in PcSearch. </p> *IMPORTANT!* *It assumes all colliders are
     * oriented, as well as arrows dictated by time order.*
     */
    private void meekOrient(Graph graph, IKnowledge knowledge) {
        MeekRules rules = new MeekRules();
        rules.setOrientInPlace(false);
        rules.setKnowledge(knowledge);
        rules.orientImplied(graph);
    }

    private void setDataSet(DataSet dataSet) {
        List<String> _varNames = dataSet.getVariableNames();

        this.variables = dataSet.getVariables();
        this.dataSet = dataSet;
        this.discrete = dataSet.isDiscrete();

        if (!isDiscrete()) {
            this.covariances = new CovarianceMatrix(dataSet);
        }

        this.sampleSize = dataSet.getNumRows();
    }

    private void setCovMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covariances = covarianceMatrix;
        this.variables = covarianceMatrix.getVariables();
        this.sampleSize = covarianceMatrix.getSampleSize();
    }

    private void buildIndexing(Graph graph) {
        this.hashIndices = new HashMap<Node, Integer>();
        for (Node node : graph.getNodes()) {
            this.hashIndices.put(node, variables.indexOf(node));
        }
    }

    private void clearArrow(Node x, Node y) {
        final OrderedPair<Node> pair = new OrderedPair<Node>(x, y);
        final Set<Arrow> lookupArrows = this.lookupArrows.get(pair);

        if (lookupArrows != null) {
            sortedArrows.removeAll(lookupArrows);
        }

        this.lookupArrows.remove(pair);
    }

    private void addLookupArrow(Node i, Node j, Arrow arrow) {
        OrderedPair<Node> pair = new OrderedPair<Node>(i, j);
        Set<Arrow> arrows = lookupArrows.get(pair);

        if (arrows == null) {
            arrows = new HashSet<Arrow>();
            lookupArrows.put(pair, arrows);
        }

        arrows.add(arrow);
    }

    //===========================SCORING METHODS===================//
    public double scoreDag(Graph graph) {
        Graph dag = new EdgeListGraphSingleConnections(graph);
        buildIndexing(graph);

        double score = 0.0;

        for (Node y : dag.getNodes()) {
            Set<Node> parents = new HashSet<Node>(dag.getParents(y));
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

    private double scoreGraphChange(Node y, Set<Node> parents1,
                                    Set<Node> parents2) {
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

    /**
     * Compute the local BDeu score of (i, parents(i)). See (Chickering, 2002).
     */
    private double localDiscreteScore(int i, int parents[]) {
        return getDiscreteScore().localScore(i, parents);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model.
     */
    private double localSemScore(int i, int[] parents) {
        try {
            ICovarianceMatrix cov = getCovMatrix();
            double varianceY = cov.getValue(i, i);
            double residualVariance = varianceY;
            int n = sampleSize();
            int p = parents.length;
            int k = (p * (p + 1)) / 2 + p;
//            int k = (p + 1) * (p + 1);
//            int k = p + 1;
            TetradMatrix covxx = cov.getSelection(parents, parents);
            TetradMatrix covxxInv = covxx.inverse();
            TetradVector covxy = cov.getSelection(parents, new int[]{i}).getColumn(0);
            TetradVector b = covxxInv.times(covxy);
            residualVariance -= covxy.dotProduct(b);

            if (residualVariance <= 0 && verbose) {
                System.out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / varianceY));
                return Double.NaN;
            }

            double c = getPenaltyDiscount();

//            return -n * log(residualVariance) - 2 * k; //AIC
            return -n * Math.log(residualVariance) - c * k * Math.log(n);
//            return -n * log(residualVariance) - c * k * (log(n) - log(2 * PI));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
//            throwMinimalLinearDependentSet(parents, cov);
        }

    }

    //    private void throwMinimalLinearDependentSet(int[] parents, TetradMatrix cov) {
//        List<Node> _parents = new ArrayList<Node>();
//        for (int p : parents) _parents.add(variables.get(p));
//
//        DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
//        int[] choice;
//
//        while ((choice = gen.next()) != null) {
//            int[] sel = new int[choice.length];
//            List<Node> _sel = new ArrayList<Node>();
//            for (int m = 0; m < choice.length; m++) {
//                sel[m] = parents[m];
//                _sel.add(variables.get(sel[m]));
//            }
//
//            TetradMatrix m = cov.getSelection(sel, sel);
//
//            try {
//                m.inverse();
//            } catch (Exception e2) {
//                throw new RuntimeException("Linear dependence among variables: " + _sel);
//            }
//        }
//    }

    private int sampleSize() {
        return this.sampleSize;
    }

    private List<Node> getVariables() {
        return variables;
    }

    private ICovarianceMatrix getCovMatrix() {
        return covariances;
    }

    private DataSet dataSet() {
        return dataSet;
    }

    private boolean isDiscrete() {
        return discrete;
    }

    private void fireGraphChange(Graph graph) {
        for (PropertyChangeListener l : getListeners()) {
            l.propertyChange(new PropertyChangeEvent(this, "graph", null, graph));
        }
    }

    private List<PropertyChangeListener> getListeners() {
        if (listeners == null) {
            listeners = new ArrayList<PropertyChangeListener>();
        }
        return listeners;
    }

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






