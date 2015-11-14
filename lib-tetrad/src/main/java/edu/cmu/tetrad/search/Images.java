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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * GesSearch is an implementation of the GES algorithm, as specified in Chickering (2002) "Optimal structure
 * identification with greedy search" Journal of Machine Learning Research. It works for both BayesNets and SEMs.
 * <p/>
 * Some code optimization could be done for the scoring part of the graph for discrete models (method scoreGraphChange).
 * Some of Andrew Moore's approaches for caching sufficient statistics, for instance.
 *
 * @author Ricardo Silva, Summer 2003
 * @author Joseph Ramsey, Revisions 10/2005
 */

public final class Images implements GraphSearch, IImages {

    /**
     * The data set, various variable subsets of which are to be scored.
     */
    private List<DataSet> dataSets;

    /**
     * The covariance matrix for the data set.
     */
    private List<TetradMatrix> covariances;

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
     * Array of variable names from the data set, in order.
     */
    private String varNames[];

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
     * For formatting printed numbers.
     */
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

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
     * The top n graphs found by the algorithm, where n is <code>numPatternsToStore</code>.
     */
    private SortedSet<ScoredGraph> topGraphs = new TreeSet<ScoredGraph>();

    /**
     * The number of top patterns to store.
     */
    private int numPatternsToStore = 10;

    private SortedSet<Arrow> sortedArrows = new TreeSet<Arrow>();
    private Set<Arrow>[][] lookupArrows;
    private Map<Node, Map<Set<Node>, Double>> scoreHash;
    private Map<Node, Integer> nodesHash;

    /**
     * True if graphs should be stored.
     */
    private boolean storeGraphs = true;

    private double bic;
    private Map<DataSet, Set<Node>> missingVariables;
    private Graph returnGraph;
    private int maxNumEdges = -1;
    private int subsetBound = Integer.MAX_VALUE;
    private ArrayList<CovarianceMatrix> covarianceMatrices = new ArrayList<CovarianceMatrix>();
    private boolean log = true;
    private double trimAlpha = 0.0;
    private boolean useKnowledgeBackwards;
    private Graph adjacencyGraph;
    private boolean verbose = true;
    private boolean containsMissingVariables;


    //===========================CONSTRUCTORS=============================//

    private Images() {
        // private
    }

    public Images(List<DataSet> dataSets) {
        setDataSets(dataSets);
    }

    public static Images covarianceInstance(List<CovarianceMatrix> covarianceMatrixes) {
        Images images = new Images();
        images.setCovariances(covarianceMatrixes);
        return images;
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
        long startTime = System.currentTimeMillis();

        topGraphs = new TreeSet<ScoredGraph>();

        Graph graph = new EdgeListGraph(new LinkedList<Node>(getVariables()));

        scoreHash = new WeakHashMap<Node, Map<Set<Node>, Double>>();

        for (Node node : graph.getNodes()) {
            scoreHash.put(node, new HashMap<Set<Node>, Double>());
        }


        fireGraphChange(graph);
        buildIndexing(graph);
        addRequiredEdges(graph);

        double score = 0; //scoreGraph(SearchGraphUtils.dagFromPattern(graph));

        storeGraph(new EdgeListGraph(graph), score);

        List<Node> nodes = graph.getNodes();

        nodesHash = new HashMap<Node, Integer>();
        int index = -1;

        for (Node node : nodes) {
            nodesHash.put(node, ++index);
        }

        // Do forward search.
        score = fes(graph, nodes, score);

        // Do backward search.
        score = bes(graph, nodes, score);

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;
        this.logger.log("graph", "\nReturning this graph: " + graph);
        TetradLogger.getInstance().log("info", "Final Model BIC = " + nf.format(score));

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.flush();

        bic = score;
        return graph;
    }

    public Graph search(List<Node> nodes) {
        long startTime = System.currentTimeMillis();
        localScoreCache.clear();

        if (!variables.containsAll(nodes)) {
            throw new IllegalArgumentException(
                    "All of the nodes must be in the supplied data set.");
        }

        Graph graph = new EdgeListGraph(nodes);
        buildIndexing(graph);
        addRequiredEdges(graph);
        double score = 0; //scoreGraph(graph);

        // Do forward search.
        score = fes(graph, nodes, score);

        // Do backward search.
        score = bes(graph, nodes, score);

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;

        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.flush();
        bic = score;
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
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }
        this.knowledge = knowledge;
    }

    public void setElapsedTime(long elapsedTime) {
        this.elapsedTime = elapsedTime;
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
            throw new IllegalArgumentException("Number of patterns to store must be >= 0: " + numPatternsToStore);
        }

        storeGraphs = numPatternsToStore != 0;

        this.numPatternsToStore = numPatternsToStore;
    }

    //===========================PRIVATE METHODS========================//

    /**
     * Forward equivalence search.
     *
     * @param graph The graph in the state prior to the forward equivalence search.
     * @param score The score in the state prior to the forward equivalence search
     * @return the score in the state after the forward equivelance search. Note that the graph is changed as a
     * side-effect to its state after the forward equivelance search.
     */
    private double fes(Graph graph, List<Node> nodes, double score) {
        TetradLogger.getInstance().log("info", "** FORWARD EQUIVALENCE SEARCH");
        TetradLogger.getInstance().log("info", "Initial Model BIC = " + nf.format(score));

        initializeArrowsForward(nodes);

        while (!sortedArrows.isEmpty()) {
            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = nodes.get(arrow.getX());
            Node y = nodes.get(arrow.getY());

            if (!validInsert(x, y, arrow.getHOrT(), arrow.getNaYX(), graph)) {
                continue;
            }

            List<Node> t = arrow.getHOrT();
            double bump = arrow.getBump();

            score = score + bump;

            Set<Edge> edges = new HashSet<Edge>(graph.getEdges());

            insert(x, y, t, graph, score, bump);

            rebuildPattern(graph);

            // Try to avoid duplicating scoring calls. First clear out all of the edges that need to be changed,
            // then change them, checking to see if they're already been changed. I know, roundabout, but there's
            // a performance boost.
            for (Edge edge : graph.getEdges()) {
                Node _x = edge.getNode1();
                Node _y = edge.getNode2();

                if (!edges.contains(edge)) {
                    clearForward(graph, nodes, nodesHash.get(_x), nodesHash.get(_y));
                }
            }

            for (Edge edge : graph.getEdges()) {
                Node _x = edge.getNode1();
                Node _y = edge.getNode2();

                if (!edges.contains(edge)) {
                    reevaluateForward(graph, nodes, nodesHash.get(_x), nodesHash.get(_y));
                }
            }

            storeGraph(graph, score);
        }

        return score;
    }

    private double bes(Graph graph, List<Node> nodes, double score) {
        TetradLogger.getInstance().log("info", "** BACKWARD EQUIVALENCE SEARCH");
        TetradLogger.getInstance().log("info", "Initial Model BIC = " + nf.format(score));

        if (!useKnowledgeBackwards) {
            knowledge = new Knowledge2();
        }

        initializeArrowsBackward(graph);

        while (!sortedArrows.isEmpty()) {
            Arrow arrow = sortedArrows.first();
            sortedArrows.remove(arrow);

            Node x = nodes.get(arrow.getX());
            Node y = nodes.get(arrow.getY());

            if (!validDelete(arrow.getHOrT(), arrow.getNaYX(), graph)) {
                continue;
            }

            List<Node> h = arrow.getHOrT();
            double bump = arrow.getBump();

            delete(x, y, h, graph, score, bump);
            score = score + bump;

            rebuildPattern(graph);

            storeGraph(graph, score);

            initializeArrowsBackward(graph);  // Rebuilds Arrows from scratch each time. Fast enough for backwards.
        }

        return score;
    }

    // Expensive.
    private void initializeArrowsForward(List<Node> nodes) {
        sortedArrows.clear();
        lookupArrows = new HashSet[nodes.size()][nodes.size()];
        List<Node> emptyList = Collections.EMPTY_LIST;
        Set<Node> emptySet = Collections.EMPTY_SET;

        for (int j = 0; j < nodes.size(); j++) {
            if (verbose) {
                if ((j + 1) % 10 == 0) System.out.println("Initializing arrows forward: " + (j + 1));
            }

            for (int i = 0; i < nodes.size(); i++) {
                if (j == i) continue;

                Node _x = nodes.get(i);
                Node _y = nodes.get(j);

                if (getAdjacencyGraph() != null && !getAdjacencyGraph().isAdjacentTo(_x, _y)) {
                    continue;
                }

                if (getKnowledge().isForbidden(_x.getName(), _y.getName())) {
                    continue;
                }

                if (!validSetByKnowledge(_y, emptyList)) {
                    continue;
                }

                double bump = scoreGraphChange(_y, Collections.singleton(_x), emptySet);

                if (bump > 0.0) {
                    Arrow arrow = new Arrow(bump, i, j, emptyList, emptyList, nodes);
                    lookupArrows[i][j] = new HashSet<Arrow>();
                    sortedArrows.add(arrow);
                    lookupArrows[i][j].add(arrow);
                }
            }
        }
    }

    private void initializeArrowsBackward(Graph graph) {
        List<Node> nodes = graph.getNodes();
        sortedArrows.clear();
        lookupArrows = new HashSet[nodes.size()][nodes.size()];

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            int i = nodesHash.get(edge.getNode1());
            int j = nodesHash.get(edge.getNode2());

            if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                continue;
            }

            if (Edges.isDirectedEdge(edge)) {
                calculateArrowsBackward(i, j, nodes, graph);
            } else {
                calculateArrowsBackward(i, j, nodes, graph);
                calculateArrowsBackward(j, i, nodes, graph);
            }
        }
    }

    private void clearForward(Graph graph, List<Node> nodes, int i, int j) {
        Node x = nodes.get(i);
        Node y = nodes.get(j);

        if (!graph.isAdjacentTo(x, y)) throw new IllegalArgumentException();

        clearArrow(i, j);
        clearArrow(j, i);

        for (int _w = 0; _w < nodes.size(); _w++) {
            Node w = nodes.get(_w);
            if (w == x) continue;
            if (w == y) continue;

            if (!graph.isAdjacentTo(w, x)) {
                clearArrow(_w, i);

                if (graph.isAdjacentTo(w, y)) {
                    clearArrow(i, _w);
                }
            }

            if (!graph.isAdjacentTo(w, y)) {
                clearArrow(_w, j);

                if (graph.isAdjacentTo(w, x)) {
                    clearArrow(j, _w);
                }
            }
        }
    }

    private void reevaluateForward(Graph graph, List<Node> nodes, int i, int j) {
        Node x = nodes.get(i);
        Node y = nodes.get(j);

        if (!graph.isAdjacentTo(x, y)) throw new IllegalArgumentException();

        for (int _w = 0; _w < nodes.size(); _w++) {
            Node w = nodes.get(_w);
            if (w == x) continue;
            if (w == y) continue;

            if (!graph.isAdjacentTo(w, x)) {
                if (lookupArrows[_w][i] == null) {
                    calculateArrowsForward(_w, i, nodes, graph);
                }

                if (graph.isAdjacentTo(w, y)) {
                    if (lookupArrows[i][_w] == null) {
                        calculateArrowsForward(i, _w, nodes, graph);
                    }
                }
            }

            if (!graph.isAdjacentTo(w, y)) {
                if (lookupArrows[_w][j] == null) {
                    calculateArrowsForward(_w, j, nodes, graph);
                }

                if (graph.isAdjacentTo(w, x)) {
                    if (lookupArrows[j][_w] == null) {
                        calculateArrowsForward(j, _w, nodes, graph);
                    }
                }
            }
        }
    }

    private void clearArrow(int i, int j) {
        if (lookupArrows[i][j] != null) {

            // removeall is slower
            for (Arrow arrow : lookupArrows[i][j]) {
                sortedArrows.remove(arrow);
            }

            lookupArrows[i][j] = null;
        }
    }

    private void calculateArrowsForward(int i, int j, List<Node> nodes, Graph graph) {
        if (i == j) {
            return;
        }

        Node x = nodes.get(i);
        Node y = nodes.get(j);

//        if (graph.isAdjacentTo(x, y)) {
//            return;
//        }

        if (getKnowledge().isForbidden(x.getName(), y.getName())) {
            return;
        }

//        clearArrow(i, j);

        List<Node> naYX = getNaYX(x, y, graph);
        List<Node> tNeighbors = getTNeighbors(x, y, graph);

        lookupArrows[i][j] = new HashSet<Arrow>();

        DepthChoiceGenerator gen = new DepthChoiceGenerator(tNeighbors.size(), 1);
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> t = GraphUtils.asList(choice, tNeighbors);

            if (!validSetByKnowledge(y, t)) {
                continue;
            }

            double bump = insertEval(x, y, t, naYX, graph);

            if (bump > 0.0) {
                Arrow arrow = new Arrow(bump, i, j, t, naYX, nodes);
                sortedArrows.add(arrow);
                lookupArrows[i][j].add(arrow);
            }
        }
    }

    private void calculateArrowsBackward(int i, int j, List<Node> nodes, Graph graph) {
        if (i == j) {
            return;
        }

        Node x = nodes.get(i);
        Node y = nodes.get(j);

        if (!graph.isAdjacentTo(x, y)) {
            return;
        }

        if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
            return;
        }

        List<Node> naYX = getNaYX(x, y, graph);

        clearArrow(i, j);

        List<Node> _naYX = new ArrayList<Node>(naYX);
        DepthChoiceGenerator gen = new DepthChoiceGenerator(_naYX.size(), _naYX.size());
        int[] choice;
        lookupArrows[i][j] = new HashSet<Arrow>();

        while ((choice = gen.next()) != null) {
            List<Node> H = GraphUtils.asList(choice, _naYX);

            if (!validSetByKnowledge(y, H)) {
                continue;
            }

            double bump = deleteEval(x, y, H, naYX, graph);

            if (bump > 0.0) {
                Arrow arrow = new Arrow(bump, i, j, H, naYX, nodes);
                sortedArrows.add(arrow);
                lookupArrows[i][j].add(arrow);
            }
        }
    }

    /**
     * True iff log output should be produced.
     */
    public ArrayList<CovarianceMatrix> getCovarianceMatrices() {
        return covarianceMatrices;
    }

    public boolean isLog() {
        return log;
    }

    public void setLog(boolean log) {
        this.log = log;
    }

    public double getTrimAlpha() {
        return trimAlpha;
    }

    public void setTrimAlpha(double trimAlpha) {
        if (trimAlpha < 0.0) {// || trimAlpha > 0.5) {
            throw new IllegalArgumentException("Clip must be in [0, 1]");
        }

        this.trimAlpha = trimAlpha;
    }

    public void setAdjacencyGraph(Graph adjacencyGraph) {
        Graph graph = GraphUtils.undirectedGraph(adjacencyGraph);
        graph = GraphUtils.replaceNodes(graph, getVariables());
        this.adjacencyGraph = graph;
    }

    public Graph getAdjacencyGraph() {
        return adjacencyGraph;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isUseKnowledgeBackwards() {
        return useKnowledgeBackwards;
    }

    public void setUseKnowledgeBackwards(boolean useKnowledgeBackwards) {
        this.useKnowledgeBackwards = useKnowledgeBackwards;
    }

    private static class Arrow implements Comparable {
        private double bump;
        private int x;
        private int y;
        private List<Node> hOrT;
        private List<Node> naYX;
        private List<Node> nodes;

        public Arrow(double bump, int x, int y, List<Node> hOrT, List<Node> naYX, List<Node> nodes) {
            this.bump = bump;
            this.x = x;
            this.y = y;
            this.hOrT = hOrT;
            this.naYX = naYX;
            this.nodes = nodes;
        }

        public double getBump() {
            return bump;
        }

        public int getX() {
            return x;
        }

        public int getY() {
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
            return "Arrow<" + nodes.get(x) + "->" + nodes.get(y) + " bump = " + bump + " t/h = " + hOrT + " naYX = " + naYX + ">";
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
        List<Node> paY = graph.getParents(y);
        Set<Node> paYPlusX = new HashSet<Node>(paY);
        paYPlusX.add(x);

        Set<Node> set1 = new HashSet<Node>(naYX);
        set1.addAll(t);
        set1.addAll(paYPlusX);

        Set<Node> set2 = new HashSet<Node>(naYX);
        set2.addAll(t);
        set2.addAll(paY);

        return scoreGraphChange(y, set1, set2);
    }

    /**
     * Evaluate the Delete(X, Y, T) operator (Definition 12 from Chickering, 2002).
     */
    private double deleteEval(Node x, Node y, List<Node> h, List<Node> naYX, Graph graph) {
        List<Node> paY = graph.getParents(y);
        paY.add(x);
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

    private void insert(Node x, Node y, List<Node> t, Graph graph, double score, double bump) {
        if (graph.isAdjacentTo(x, y)) {
            Edge edge = graph.getEdge(x, y);

            if (Edges.isUndirectedEdge(edge)) {
                graph.removeEdge(x, y);
            } else {
                return;
            }
        }

        if (graph.isAdjacentTo(x, y)) {
            return; // knowledge required
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
                    " " + t +
                    " (" + nf.format(score) + ") " + label);
            if (verbose) {
                System.out.println(graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y) +
                        " " + t +
                        "\t" + nf.format(score) + "\t" + bump + "\t" + label);
            }
        } else {
            int numEdges = graph.getNumEdges() - 1;
            if (numEdges % 50 == 0) System.out.println(numEdges);
        }

        for (Node _t : t) {
            Edge oldEdge = graph.getEdge(_t, y);

            if (oldEdge == null) throw new IllegalArgumentException("Not adjacent: " + _t + ", " + y);

            if (!Edges.isUndirectedEdge(oldEdge)) {
                throw new IllegalArgumentException("Should be undirected: " + oldEdge);
            }

            graph.removeEdge(_t, y);
            graph.addDirectedEdge(_t, y);

            if (log) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(_t, y));
                if (verbose) {
//                    System.out.println("--- Directing " + oldEdge + " to " +
//                            graph.getEdge(_t, y));
                }
            }
        }
    }

    /**
     * Do an actual deletion (Definition 13 from Chickering, 2002).
     */
    private void delete(Node x, Node y, List<Node> subset, Graph graph, double score, double bump) {

        Edge trueEdge = null;

        if (trueGraph != null) {
            Node _x = trueGraph.getNode(x.getName());
            Node _y = trueGraph.getNode(y.getName());
            trueEdge = trueGraph.getEdge(_x, _y);
        }

        if (log) {
            Edge oldEdge = graph.getEdge(x, y);

            String label = trueGraph != null && trueEdge != null ? "*" : "";
            TetradLogger.getInstance().log("deletedEdges", (graph.getNumEdges() - 1) + ". DELETE " + oldEdge +
                    " " + subset +
                    " (" + nf.format(score) + ") " + label);
            if (verbose) {
                System.out.println((graph.getNumEdges() - 1) + ". DELETE " + oldEdge +
                        " " + subset +
                        "\t" + nf.format(score) + "\t" + bump + "\t" + label);
            }
        } else {
            int numEdges = graph.getNumEdges() - 1;

            if (verbose) {
                if (numEdges % 50 == 0) System.out.println(numEdges);
            }
        }

        graph.removeEdge(x, y);

        for (Node h : subset) {
            Edge oldEdge = graph.getEdge(y, h);

            graph.removeEdge(y, h);
            graph.addDirectedEdge(y, h);

            if (log) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                        graph.getEdge(y, h));

                if (verbose) {
//                    System.out.println("--- Directing " + oldEdge + " to " +
//                            graph.getEdge(y, h));
                }
            }

            if (Edges.isUndirectedEdge(graph.getEdge(x, h))) {
                if (!graph.isAdjacentTo(x, h)) throw new IllegalArgumentException("Not adjacent: " + x + ", " + h);
                oldEdge = graph.getEdge(x, h);

                graph.removeEdge(x, h);
                graph.addDirectedEdge(x, h);

                if (log) {
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldEdge + " to " +
                            graph.getEdge(x, h));

                    if (verbose) {
                        System.out.println("--- Directing " + oldEdge + " to " +
                                graph.getEdge(x, h));
                    }
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

        if (!isClique(union, graph)) {
            return false;
        }

        return !existsUnblockedSemiDirectedPath(y, x, union, graph);
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
        for (Iterator<KnowledgeEdge> it = getKnowledge().requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge next = it.next();
            String a = next.getFrom();
            String b = next.getTo();
            Node nodeA = null, nodeB = null;
            Iterator<Node> itn = graph.getNodes().iterator();
            while (itn.hasNext() && (nodeA == null || nodeB == null)) {
                Node nextNode = itn.next();
                if (nextNode.getName().equals(a)) {
                    nodeA = nextNode;
                }
                if (nextNode.getName().equals(b)) {
                    nodeB = nextNode;
                }
            }

            if (graph.containsEdge(Edges.directedEdge(nodeB, nodeA))) {
                graph.removeEdge(nodeB, nodeA);
                graph.addEdge(Edges.undirectedEdge(nodeA, nodeB));
            } else if (!graph.isAncestorOf(nodeB, nodeA)) {
                graph.removeEdges(nodeA, nodeB);
                graph.addDirectedEdge(nodeA, nodeB);
                TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB));
            }
        }
        for (Iterator<KnowledgeEdge> it = getKnowledge().forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge next = it.next();
            String a = next.getFrom();
            String b = next.getTo();
            Node nodeA = null, nodeB = null;
            Iterator<Node> itn = graph.getNodes().iterator();
            while (itn.hasNext() && (nodeA == null || nodeB == null)) {
                Node nextNode = itn.next();
                if (nextNode.getName().equals(a)) {
                    nodeA = nextNode;
                }
                if (nextNode.getName().equals(b)) {
                    nodeB = nextNode;
                }
            }
            if (nodeA != null && nodeB != null && graph.isAdjacentTo(nodeA, nodeB) &&
                    !graph.isChildOf(nodeA, nodeB)) {
                if (!graph.isAncestorOf(nodeA, nodeB)) {
                    graph.removeEdges(nodeA, nodeB);
                    graph.addDirectedEdge(nodeB, nodeA);
                    TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                }
            }
        }
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
            for (int j = i; j < nodes.size(); j++) {
                if (i == j && graph.isAdjacentTo(nodes.get(i), nodes.get(j))) {
                    throw new IllegalArgumentException();
                }

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

        TetradLogger.getInstance().log("rebuiltPatterns", "Rebuilt pattern = " + graph);
    }

    /**
     * Fully direct a graph with background knowledge. I am not sure how to adapt Chickering's suggested algorithm above
     * (dagToPdag) to incorporate background knowledge, so I am also implementing this algorithm based on Meek's 1995
     * UAI paper. Notice it is the same implemented in PcSearch. </p> *IMPORTANT!* *It assumes all colliders are
     * oriented, as well as arrows dictated by time order.*
     */
    private void meekOrient(Graph graph, IKnowledge knowledge) {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        rules.orientImplied(graph);
    }

    private void setDataSets(List<DataSet> dataSets) {
        List<String> varNames = dataSets.get(0).getVariableNames();

        for (int i = 2; i < dataSets.size(); i++) {
            List<String> _varNames = dataSets.get(i).getVariableNames();

            if (!varNames.equals(_varNames)) {
                throw new IllegalArgumentException("Variable names not consistent.");
            }
        }

        this.varNames = varNames.toArray(new String[varNames.size()]);
        this.sampleSize = dataSets.get(0).getNumRows();

        this.variables = dataSets.get(0).getVariables();
        this.dataSets = dataSets;
        this.discrete = dataSets.get(0).isDiscrete();

        if (!isDiscrete()) {
            this.covariances = new ArrayList<TetradMatrix>();

            for (DataSet dataSet : dataSets) {
                CovarianceMatrix cov = new CovarianceMatrix(dataSet);
                this.covarianceMatrices.add(cov);
                this.covariances.add(cov.getMatrix());
            }
        }

        missingVariables = new HashMap<DataSet, Set<Node>>();
        containsMissingVariables = false;

        for (DataSet dataSet : dataSets) {
            missingVariables.put(dataSet, new HashSet<Node>());
        }

        for (DataSet dataSet : dataSets) {
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

        setKnowledge(dataSets.get(0).getKnowledge());
    }

    private void setCovariances(List<CovarianceMatrix> dataSets) {
        List<String> varNames = dataSets.get(0).getVariableNames();

        for (int i = 2; i < dataSets.size(); i++) {
            List<String> _varNames = dataSets.get(i).getVariableNames();

            if (!varNames.equals(_varNames)) {
                throw new IllegalArgumentException("Variable names not consistent.");
            }
        }

        this.varNames = varNames.toArray(new String[varNames.size()]);
        this.sampleSize = dataSets.get(0).getSampleSize();

        this.variables = dataSets.get(0).getVariables();

        this.covariances = new ArrayList<TetradMatrix>();

        for (CovarianceMatrix cov : dataSets) {
            this.covarianceMatrices.add(cov);
            this.covariances.add(cov.getMatrix());
        }

        setKnowledge(dataSets.get(0).getKnowledge());
    }

    private void buildIndexing(Graph graph) {
        this.hashIndices = new HashMap<Node, Integer>();
        for (Node next : graph.getNodes()) {
            for (int i = 0; i < this.varNames.length; i++) {
                if (this.varNames[i].equals(next.getName())) {
                    this.hashIndices.put(next, i);
                    break;
                }
            }
        }
    }

    //===========================SCORING METHODS
    public double scoreDag(Graph graph) {
        Dag dag = new Dag(graph);

        double score = 0.0;

        for (Node y : dag.getNodes()) {
            Set<Node> parents = new HashSet<Node>(dag.getParents(y));
            int nextIndex = -1;
            for (int i = 0; i < getVariables().size(); i++) {
                if (this.varNames[i].equals(y.getName())) {
                    nextIndex = i;
                    break;
                }
            }
            int parentIndices[] = new int[parents.size()];
            Iterator<Node> pi = parents.iterator();
            int count = 0;
            while (pi.hasNext()) {
                Node nextParent = pi.next();
                for (int i = 0; i < getVariables().size(); i++) {
                    if (this.varNames[i].equals(nextParent.getName())) {
                        parentIndices[count++] = i;
                        break;
                    }
                }
            }

            score += localSemScore(nextIndex, parentIndices);
        }

        return score;
    }

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

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model.
     */
    private double localSemScore(int i, int[] parents) {
        double sum = 0.0;

        for (int d = 0; d < numDataSets(); d++) {
            sum += localSemScoreOneDataSet(d, i, parents);
        }

        return sum;
    }

    private int numDataSets() {
        return getCovMatrices().size();
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
                System.out.println("Negative residual variance: " + residualVariance);
            }
            return Double.NaN;
        }

        double c = getPenaltyDiscount();
        return score(residualVariance, n, p, c);
    }

    // Calculates the BIC score.
    private double score(double residualVariance, int n, int p, double c) {
        return -n * Math.log(residualVariance) - n * Math.log(2 * Math.PI) - n - dof(p) * c * (Math.log(n));
//        return -n * Math.log(residualVariance) - c * dof(p) * Math.log(n);
    }

    private int dof(int p) {
        return (p + 1) * (p + 2) / 2;
//        return p + 1;
    }


    private void throwMinimalLinearDependentSet(int[] parents, TetradMatrix cov) {
        List<Node> _parents = new ArrayList<Node>();
        for (int p : parents) _parents.add(variables.get(p));

        DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            int[] sel = new int[choice.length];
            List<Node> _sel = new ArrayList<Node>();

            for (int m = 0; m < choice.length; m++) {
                sel[m] = parents[m];
                _sel.add(variables.get(sel[m]));
            }

            TetradMatrix m = cov.getSelection(sel, sel);

            try {
                m.inverse();
            } catch (Exception e2) {
                throw new RuntimeException("Linear dependence among variables: " + _sel);
            }
        }
    }

    private int sampleSize() {
        return this.sampleSize;
    }

    private List<Node> getVariables() {
        return variables;
    }

    private List<TetradMatrix> getCovMatrices() {
        return covariances;
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

    private void storeGraph(Graph graph, double score) {
        if (!storeGraphs) return;

        if (topGraphs.isEmpty() || score > topGraphs.first().getScore()) {
            Graph graphCopy = new EdgeListGraph(graph);

            topGraphs.add(new ScoredGraph(graphCopy, score));

            if (topGraphs.size() > getNumPatternsToStore()) {
                topGraphs.remove(topGraphs.first());
            }
        }
    }

    public Map<Edge, Integer> getBoostrapCounts(int numBootstraps) {
        if (returnGraph == null) {
            returnGraph = search();
        }
        return bootstrapImagesCounts(dataSets, returnGraph.getNodes(), getKnowledge(), numBootstraps, getPenaltyDiscount());

    }

    public String bootstrapPercentagesString(int numBootstraps) {
        if (returnGraph == null) {
            returnGraph = search();
        }

        StringBuilder builder = new StringBuilder(
                "For " + numBootstraps + " repetitions, the percentage of repetitions in which each " +
                        "edge occurs in the IMaGES pattern for that repetition. In each repetition, for each " +
                        "input data set, a sample the size of that data set chosen randomly and with replacement. " +
                        "Images is run on the collection of these data sets. 100% for an edge means that that " +
                        "edge occurs in all such randomly chosen samples, over " + numBootstraps + " repetitions; " +
                        "0% means it never occurs. Edges not mentioned occur in 0% of the random samples.\n\n"
        );

        Map<Edge, Integer> counts = getBoostrapCounts(numBootstraps);
        builder.append(edgePercentagesString(counts, new ArrayList<Edge>(returnGraph.getEdges()), null, numBootstraps));

        return builder.toString();
    }

    public String gesCountsString() {
        if (returnGraph == null) {
            returnGraph = search();
        }
        Map<Edge, Integer> counts = getGesCounts(dataSets, returnGraph.getNodes(), getKnowledge(), getPenaltyDiscount());
        return gesEdgesString(counts, dataSets);
    }

    private Map<Edge, Integer> getGesCounts(List<DataSet> dataSets, List<Node> nodes, IKnowledge knowledge, double penalty) {
        if (returnGraph == null) {
            returnGraph = search();
        }

        Map<Edge, Integer> counts = new HashMap<Edge, Integer>();

        for (DataSet dataSet : dataSets) {
            Ges ges = new Ges(dataSet);
            ges.setKnowledge(knowledge);
            ges.setPenaltyDiscount(penalty);
            Graph pattern = ges.search();

            incrementCounts(counts, pattern, nodes);
        }

        return counts;
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


    public double getModelScore() {
        return bic;
    }

    public int getMaxNumEdges() {
        return maxNumEdges;
    }

    public void setMaxNumEdges(int maxNumEdges) {
        if (maxNumEdges < -1) throw new IllegalArgumentException();

        this.maxNumEdges = maxNumEdges;
    }

}






