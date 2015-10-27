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
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;


/**
 * Reducing GES to the basics for comparison. Took out (hopefully) all of the optimizations.
 *
 * @author Joseph Ramsey, 8.20.2015
 */
public final class GesBasic implements GraphSearch, GraphScorer {

    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();

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
    private GesScore gesScore;

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

    // True if it is assumed that zero effect adjacencies are not in the graph.
    private boolean faithfulnessAssumed = true;

    // A utility map to help with orientation.
    private WeakHashMap<Node, Set<Node>> neighbors = new WeakHashMap<>();

    //===========================CONSTRUCTORS=============================//

    /**
     * The data set must either be all continuous or all discrete.
     */
    public GesBasic(DataSet dataSet) {
        out.println("GES constructor");

        if (dataSet.isDiscrete()) {
            setGesScore(new BDeuScore(dataSet));
        } else {
            setGesScore(new SemBicScore(new CovarianceMatrixOnTheFly(dataSet)));
        }

        out.println("GES constructor done");
    }

    /**
     * Continuous case--where a covariance matrix is already available.
     */
    public GesBasic(ICovarianceMatrix covMatrix) {
        out.println("GES constructor");

        setGesScore(new SemBicScore(covMatrix));

        out.println("GES constructor done");
    }

    public GesBasic(GesScore gesScore) {
        if (gesScore == null) throw new NullPointerException();
        setGesScore(gesScore);
    }

    //==========================PUBLIC METHODS==========================//

    /**
     * Set to true if it is assumed that all path pairs with one length 1 path do not cancel.
     */
    public void setFaithfulnessAssumed(boolean faithfulness) {
        this.faithfulnessAssumed = faithfulness;
    }

    /**
     * Returns true if it is assumed that all path pairs with one length 1 path do not cancel.
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
        buildIndexing(variables);

        Graph graph;

        if (initialGraph == null) {
            graph = new EdgeListGraphSingleConnections(getVariables());
        } else {
            graph = new EdgeListGraphSingleConnections(initialGraph);
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
     * Returns the background knowledge.
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

    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * For BIC score, a multiplier on the penalty term. For continuous searches.
     */
    public double getPenaltyDiscount() {
        if (gesScore instanceof SemBicScore) {
            return ((SemBicScore) gesScore).getPenaltyDiscount();
        }

        throw new UnsupportedOperationException("Penalty discount supported only for SemBicScore.");
    }

    /**
     * For BIC score, a multiplier on the penalty term. For continuous searches.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount < 0) {
            throw new IllegalArgumentException("Penalty discount must be >= 0: "
                    + penaltyDiscount);
        }

        if (gesScore instanceof SemBicScore) {
            ((SemBicScore) gesScore).setPenaltyDiscount(penaltyDiscount);
        } else {
            throw new UnsupportedOperationException("Penalty discount supported only for SemBicScore.");
        }
    }

    /**
     * Returns the score of the given DAG, up to a constant.
     */
    public double getScore(Graph dag) {
        return scoreDag(dag);
    }

    /**
     * Returns the list of top scoring graphs.
     */
    public SortedSet<ScoredGraph> getTopGraphs() {
        return topGraphs;
    }

    /**
     * Returns the number of patterns to store.
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
     * Returns the initial graph for the search. The search is initialized to this graph and
     * proceeds from there.
     */
    public Graph getInitialGraph() {
        return initialGraph;
    }

    /**
     * Sets the initial graph.
     */
    public void setInitialGraph(Graph initialGraph) {

        if (initialGraph != null) {
            initialGraph = GraphUtils.replaceNodes(initialGraph, variables);

            out.println("Initial graph variables: " + initialGraph.getNodes());
            out.println("Data set variables: " + variables);

            if (!new HashSet<>(initialGraph.getNodes()).equals(new HashSet<>(variables))) {
                throw new IllegalArgumentException("Variables aren't the same.");
            }
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
     * Returns the output stream that output (except for log output) should be sent to.
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Returns the depth for the forward reevaluation step.
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

    //===========================PRIVATE METHODS========================//

    //Sets the discrete scoring function to use.
    private void setGesScore(GesScore gesScore) {
        this.gesScore = gesScore;
        this.variables = gesScore.getVariables();
    }

    /**
     * Forward equivalence search.
     *
     * @param graph The graph in the state prior to the forward equivalence search.
     */
    private void fes(Graph graph) {
        TetradLogger.getInstance().log("info", "** FORWARD EQUIVALENCE SEARCH");

        Arrow arrow;

        while ((arrow = maximizeForward(graph)) != null) {
            Node x = arrow.getA();
            Node y = arrow.getB();

            Set<Node> T = arrow.getHOrT();
            double bump = arrow.getBump();

            insert(x, y, T, graph, bump);
            score += bump;

            rebuildPattern(graph);
        }
    }

    /**
     * Backward equivalence search.
     *
     * @param graph The graph in the state after the forward equivalence search.
     */
    private void bes(Graph graph) {
        TetradLogger.getInstance().log("info", "** BACKWARD EQUIVALENCE SEARCH");

        Arrow arrow;

        while ((arrow = maximizeBackward(graph)) != null) {
            Node x = arrow.getA();
            Node y = arrow.getB();

            Set<Node> H = arrow.getHOrT();
            double bump = arrow.getBump();

            delete(x, y, H, graph, bump);
            score += bump;

            rebuildPattern(graph);
        }
    }

    // Returns true if knowledge is not empty.
    private boolean existsKnowledge() {
        return !knowledge.isEmpty();
    }

    // Calcuates new arrows based on changes in the graph for the forward search.
    private Arrow maximizeForward(final Graph graph) {
        Arrow maxArrow = null;
        double maxBump = Double.NEGATIVE_INFINITY;

        for (Node x : variables) {
            for (Node w : variables) {
                if (w == x) continue;

                if (!graph.isAdjacentTo(w, x)) {
                    Arrow arrow = maxArrowForward(w, x, graph);
                    if (arrow != null && arrow.getBump() > maxBump) {
                        maxArrow = arrow;
                        maxBump = arrow.getBump();
                    }
                }
            }
        }

        return maxArrow;
    }

    // Initiaizes the sorted arrows and lookup arrows lists for the backward search.
    private Arrow maximizeBackward(Graph graph) {
        Arrow maxArrow = null;
        double maxBump = Double.NEGATIVE_INFINITY;

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (existsKnowledge()) {
                if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                    continue;
                }
            }

            if (Edges.isDirectedEdge(edge)) {
                Arrow arrow = maxArrowBackward(x, y, graph);
                if (arrow != null && arrow.getBump() > maxBump) {
                    maxArrow = arrow;
                    maxBump = arrow.getBump();
                }
            } else {
                Arrow arrow = maxArrowBackward(x, y, graph);
                if (arrow != null && arrow.getBump() > maxBump) {
                    maxArrow = arrow;
                    maxBump = arrow.getBump();
                }
                arrow = maxArrowBackward(y, x, graph);
                if (arrow != null && arrow.getBump() > maxBump) {
                    maxArrow = arrow;
                    maxBump = arrow.getBump();
                }
            }
        }

        return maxArrow;
    }


    // Calculates the new arrows for an a->b edge.
    private Arrow maxArrowForward(final Node x, final Node y, final Graph graph) {
        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(x.getName(), y.getName())) {
                return null;
            }
        }

        final Set<Node> naYX = getNaYX(x, y, graph);
        final List<Node> t = getTNeighbors(x, y, graph);

        final int _depth = Math.min(t.size(), depth == -1 ? 1000 : depth);

        final DepthChoiceGenerator gen = new DepthChoiceGenerator(t.size(), _depth);

        int[] choice;

        Arrow maxArrow = null;
        double maxBump = Double.NEGATIVE_INFINITY;

        while ((choice = gen.next()) != null) {
            Set<Node> s = GraphUtils.asSet(choice, t);

            Set<Node> union = new HashSet<>(s);
            union.addAll(naYX);

            if (graph.isAdjacentTo(x, y)) {
                continue;
            }

            if (!validInsert(x, y, s, naYX, graph)) {
                continue;
            }

            if (existsKnowledge()) {
                if (!validSetByKnowledge(y, s)) {
                    continue;
                }
            }

            double bump = insertEval(x, y, s, naYX, graph, hashIndices);

            if (bump > maxBump && bump > 0) {
                maxArrow = new Arrow(bump, x, y, s, naYX);
                maxBump = bump;
            }
        }

        return maxArrow;
    }

    // Calculates the arrows for the removal in the backward direction.
    private Arrow maxArrowBackward(Node x, Node y, Graph graph) {
        if (x == y) {
            return null;
        }

        if (!graph.isAdjacentTo(x, y)) {
            return null;
        }

        if (existsKnowledge()) {
            if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                return null;
            }
        }

        Set<Node> naYX = getNaYX(x, y, graph);

        List<Node> _naYX = new ArrayList<>(naYX);

        DepthChoiceGenerator gen = new DepthChoiceGenerator(_naYX.size(), _naYX.size());
        int[] choice;
        Arrow maxArrow = null;
        double maxBump = -1;

        while ((choice = gen.next()) != null) {
            Set<Node> H = GraphUtils.asSet(choice, _naYX);

            if (!validDelete(H, naYX, graph)) {
                continue;
            }

            if (existsKnowledge()) {
                if (!validSetByKnowledge(y, H)) {
                    continue;
                }
            }

            double bump = deleteEval(x, y, H, naYX, graph, hashIndices);

            if (bump > 0.0 && bump > maxBump) {
                maxArrow = new Arrow(bump, x, y, H, naYX);
                maxBump = bump;
            }
        }

        return maxArrow;
    }

    public void setSamplePrior(double samplePrior) {
        if (gesScore instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) gesScore).setSamplePrior(samplePrior);
        }
    }

    public void setStructurePrior(double structurePrior) {
        if (gesScore instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) gesScore).setStructurePrior(structurePrior);
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

        // Sorting by bump, high to low. The problem is the SortedSet contains won't add a new element if it compares
        // to zero with an existing element, so for the cases where the comparison is to zero (i.e. have the same
        // bump, we need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables.
        // The fastest way to do this is using a hash code, though it's still possible for two Arrows to have the
        // same hash code but not be equal. If we're paranoid, in this case we calculate a determinate comparison
        // not equal to zero by keeping a list. This last part is commened out by default.
        public int compareTo(Arrow arrow) {
            final int compare = Double.compare(arrow.getBump(), getBump());

            if (compare == 0) {
                int hashcode1 = hashCode();
                int hashcode2 = arrow.hashCode();
                return Integer.compare(hashcode1, hashcode2);
            }

            return compare;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Arrow)) {
                return false;
            }

            Arrow a = (Arrow) o;

            return a.a.equals(this.a) && a.b.equals(this.b) && a.hOrT.equals(this.hOrT) && a.naYX.equals(this.naYX);
        }

        public int hashCode() {
            return 11 * a.hashCode() + 13 * b.hashCode() + 17 * hOrT.hashCode() + 19 * naYX.hashCode();
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

    // Evaluate the Insert(X, Y, T) operator (Definition 12 from Chickering, 2002).
    private double insertEval(Node x, Node y, Set<Node> t, Set<Node> naYX, Graph graph,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set1 = new HashSet<>(naYX);
        set1.addAll(t);
        set1.addAll(graph.getParents(y));
        set1.add(x);

        Set<Node> set2 = new HashSet<>(set1);
        set2.remove(x);

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

        // Insert x->y
        graph.addDirectedEdge(x, y);

        if (log) {
            TetradLogger.getInstance().log("insertedEdges", graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y) +
                    " " + t + " " + bump);
        }

        int numEdges = graph.getNumEdges();
        if (numEdges % 1000 == 0) out.println("Num edges added: " + numEdges);

        if (verbose) {
            out.println(graph.getNumEdges() + ". INSERT " + graph.getEdge(x, y) +
                    " " + t + " " + bump);
        }

        // For each t in T, if t--x, reorient as t-->x.
        for (Node _t : t) {
            Edge oldTy = graph.getEdge(_t, y);

            if (oldTy == null) throw new IllegalArgumentException("Not adjacent: " + _t + ", " + y);

            if (Edges.isUndirectedEdge(oldTy)) {
                graph.removeEdge(oldTy);
                graph.addDirectedEdge(_t, y);

                if (log && verbose) {
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldTy + " to " +
                            graph.getEdge(_t, y));
                    out.println("--- Directing " + oldTy + " to " +
                            graph.getEdge(_t, y));
                }
            }
        }
    }

    // Do an actual deletion (Definition 13 from Chickering, 2002).
    private void delete(Node x, Node y, Set<Node> H, Graph graph, double bump) {
        Edge oldXy = graph.getEdge(x, y);

        if (Edges.isUndirectedEdge(oldXy) || oldXy.pointsTowards(y)) {
            graph.removeEdge(x, y);

            if (verbose) {
                int numEdges = graph.getNumEdges();
                if (numEdges % 1000 == 0) out.println("Num edges (backwards) = " + numEdges);
            }

            if (log) {
                TetradLogger.getInstance().log("deletedEdges", (graph.getNumEdges() - 1) + ". DELETE " + oldXy +
                        " " + H + " (" + bump + ")");
                out.println((graph.getNumEdges()) + ". DELETE " + oldXy +
                        " " + H + " (" + bump + ")");
            }

            for (Node h : H) {
                Edge oldYh = graph.getEdge(y, h);

                if (Edges.isUndirectedEdge(oldYh)) {
                    graph.removeEdge(y, h);
                    graph.addDirectedEdge(y, h);

                    if (log && verbose) {
                        TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldYh + " to " +
                                oldYh);
                        out.println("--- Directing " + oldYh + " to " +
                                oldYh);
                    }
                }

                Edge oldXh = graph.getEdge(x, h);

                if (oldXh != null) {
                    if (Edges.isUndirectedEdge(oldXh)) {
                        graph.removeEdge(x, h);
                        graph.addDirectedEdge(x, h);

                        if (log && verbose) {
                            TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldYh + " to " +
                                    oldXh);
                            out.println("--- Directing " + oldYh + " to " +
                                    oldXh);
                        }
                    }
                }
            }
        }
    }

    // Test if the candidate insertion is a valid operation
    // (Theorem 15 from Chickering, 2002).
    private boolean validInsert(Node x, Node y, Set<Node> s, Set<Node> naYX, Graph graph) {
        Set<Node> union = new HashSet<>(s);
        union.addAll(naYX);

        return isClique(union, graph) && !existsUnblockedSemiDirectedPath(y, x, union, graph, cycleBound);
    }

    // Test if the candidate deletion is a valid operation (Theorem 17 from Chickering, 2002).
    private boolean validDelete(Set<Node> h, Set<Node> naXY, Graph graph) {
        Set<Node> set = new HashSet<>(naXY);
        set.removeAll(h);
        return isClique(set, graph);
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

    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
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

    // This is Ricardo's method.
    private void rebuildPattern(Graph graph) {

        // Removes all orientation except those involved in unshielded colliders.
        basicPattern(graph, false);

        // Orient using Meek rules.
        new MeekRules().orientImplied(graph);
    }

    /**
     * Get a graph and direct only the unshielded colliders.
     *
     * @return the child nodes of unshielded colliders.
     */
    public void basicPattern(Graph graph, boolean orientInPlace) {
        Set<Edge> undirectedEdges = new HashSet<Edge>();

        NEXT_EDGE:
        for (Edge edge : graph.getEdges()) {
            if (!edge.isDirected()) {
                continue;
            }

            Node x = Edges.getDirectedEdgeTail(edge);
            Node y = Edges.getDirectedEdgeHead(edge);

            for (Node parent : graph.getParents(y)) {
                if (parent != x) {
                    if (graph.isAdjacentTo(parent, x)) {
                        continue;
                    }

                    continue NEXT_EDGE;
                }
            }

            undirectedEdges.add(edge);
        }

        for (Edge nextUndirected : undirectedEdges) {
            if (orientInPlace) {
                nextUndirected.setEndpoint1(Endpoint.TAIL);
                nextUndirected.setEndpoint2(Endpoint.TAIL);
            } else {
                Node node1 = nextUndirected.getNode1();
                Node node2 = nextUndirected.getNode2();

                graph.removeEdge(nextUndirected);
                graph.addUndirectedEdge(node1, node2);
            }
        }
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

    //===========================SCORING METHODS===================//

    /**
     * Scores the given DAG, up to a constant.
     */
    public double scoreDag(Graph dag) {

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

            score += gesScore.localScore(nextIndex, parentIndices);
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

        score1 = gesScore.localScore(yIndex, parentIndices1);

        int[] parentIndices2 = new int[parents2.size()];

        int count2 = -1;
        for (Node parent : parents2) {
            parentIndices2[++count2] = hashIndices.get(parent);
        }

        score2 = gesScore.localScore(yIndex, parentIndices2);

        return score1 - score2;
    }

    private List<Node> getVariables() {
        return variables;
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






