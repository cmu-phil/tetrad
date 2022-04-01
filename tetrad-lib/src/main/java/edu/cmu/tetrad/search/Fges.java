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
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

import static edu.cmu.tetrad.graph.Edges.directedEdge;
import static java.lang.Math.max;
import static java.lang.Math.min;

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
public final class Fges implements GraphSearch, GraphScorer {

    final Set<Node> emptySet = new HashSet<>();
    final int[] count = new int[1];
    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The top n graphs found by the algorithm, where n is numCPDAGsToStore.
     */
    private final LinkedList<ScoredGraph> topGraphs = new LinkedList<>();
    // Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
    private final SortedSet<Arrow> sortedArrows = new ConcurrentSkipListSet<>();
    private final SortedSet<Arrow> sortedArrowsBack = new ConcurrentSkipListSet<>();
    private final Map<Edge, ArrowConfig> arrowsMap = new ConcurrentHashMap<>();
    private final Map<Edge, ArrowConfigBackward> arrowsMapBackward = new ConcurrentHashMap<>();
    // The static ForkJoinPool instance.
    private final ForkJoinPool pool;
    // The maximum number of threads to use.
    private final int maxThreads;
    private boolean faithfulnessAssumed = true;
    private final int depth = 10000;
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
     * The totalScore for discrete searches.
     */
    private Score score;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;
    private boolean meekVerbose;
    // Map from variables to their column indices in the data set.
    private ConcurrentMap<Node, Integer> hashIndices;
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
    private int arrowIndex;
    // The BIC score of the model.
    private double modelScore;
    // Internal.
    private Mode mode = Mode.heuristicSpeedup;
    // Bounds the degree of the graph.
    private int maxDegree = -1;
    // True if the first step of adding an edge to an empty graph should be scored in both directions
    // for each edge with the maximum score chosen.
    private boolean symmetricFirstStep;

    /**
     * Construct a Score and pass it in here. The totalScore should return a
     * positive value in case of conditional dependence and a negative values in
     * case of conditional independence. See Chickering (2002), locally
     * consistent scoring criterion. This by default uses all of the processors on
     * the machine.
     */
    public Fges(Score score) {
        this(score, Runtime.getRuntime().availableProcessors() * 10);
    }

    /**
     * Lets one construct with a score and a parallelism, that is, the number of threads to effectively use.
     */
    public Fges(Score score, int parallelism) {
        if (score == null) {
            throw new NullPointerException();
        }

        if (parallelism < 1) {
            throw new IllegalArgumentException("Parallelism must be >= 1.");
        }

        setScore(score);
        this.maxThreads = parallelism;
        this.pool = new ForkJoinPool(parallelism);
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

    //===========================CONSTRUCTORS=============================//

    //==========================PUBLIC METHODS==========================//

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
    public Graph search() {
        long start = System.currentTimeMillis();
        this.topGraphs.clear();

        this.graph = new EdgeListGraph(getVariables());

        if (this.adjacencies != null) {
            this.adjacencies = GraphUtils.replaceNodes(this.adjacencies, getVariables());
        }

        if (this.externalGraph != null) {
            this.graph = new EdgeListGraph(this.externalGraph);
            this.graph = GraphUtils.replaceNodes(this.graph, getVariables());
        }

        addRequiredEdges(this.graph);

        initializeEffectEdges(getVariables());

        this.mode = Mode.heuristicSpeedup;
        fes();
        bes();

        this.mode = Mode.coverNoncolliders;
        fes();
        bes();

        if (!this.faithfulnessAssumed) {
            this.mode = Mode.allowUnfaithfulness;
            fes();
            bes();
        }

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - start;

        if (this.verbose) {
            this.logger.forceLogMessage("Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        }

        this.modelScore = scoreDag(SearchGraphUtils.dagFromCPDAG(this.graph), true);

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
     * @return the totalScore of the given DAG, up to a constant.
     */
    public double scoreDag(Graph dag) {
        return scoreDag(dag, false);
    }

    /**
     * @return the list of top scoring graphs.
     */
    public LinkedList<ScoredGraph> getTopGraphs() {
        return this.topGraphs;
    }

    /**
     * @return the initial graph for the search. The search is initialized to
     * this graph and proceeds from there.
     */
    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    /**
     * Sets the initial graph.
     */
    public void setExternalGraph(Graph externalGraph) {
        externalGraph = GraphUtils.replaceNodes(externalGraph, this.variables);

        if (this.verbose) {
            this.out.println("Initial graph variables: " + externalGraph.getNodes());
            this.out.println("Data set variables: " + this.variables);
        }

        if (!new HashSet<>(externalGraph.getNodes()).equals(new HashSet<>(this.variables))) {
            throw new IllegalArgumentException("Variables aren't the same.");
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
     * Sets whether verbose output should be produced.
     */
    public void setMeekVerbose(boolean meekVerbose) {
        this.meekVerbose = meekVerbose;
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
     * If non-null, edges not adjacent in this graph will not be added.
     */
    public void setBoundGraph(Graph boundGraph) {
        this.boundGraph = GraphUtils.replaceNodes(boundGraph, getVariables());
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
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
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
     *
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (this.score instanceof ISemBicScore) {
            ((ISemBicScore) this.score).setPenaltyDiscount(penaltyDiscount);
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

    public void setSymmetricFirstStep(boolean symmetricFirstStep) {
        this.symmetricFirstStep = symmetricFirstStep;
    }

    public String logEdgeBayesFactorsString(Graph dag) {
        Map<Edge, Double> factors = logEdgeBayesFactors(dag);
        return logBayesPosteriorFactorsString(factors);
    }

    //===========================PRIVATE METHODS========================//

    double getModelScore() {
        return this.modelScore;
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
        int chunk = n / this.maxThreads;
        if (chunk < 10) chunk = 10;
        return chunk;
    }

    private void initializeEffectEdges(List<Node> nodes) {
        long start = System.currentTimeMillis();
        this.effectEdgesGraph = new EdgeListGraph(nodes);

        List<Callable<Boolean>> tasks = new ArrayList<>();

        int chunkSize = getChunkSize(nodes.size());

        for (int i = 0; i < nodes.size() && !Thread.currentThread().isInterrupted(); i += chunkSize) {
            NodeTaskEmptyGraph task = new NodeTaskEmptyGraph(i, min(nodes.size(), i + chunkSize),
                    nodes, this.emptySet);

            if (this.maxThreads == 1) {
                task.call();
            } else {
                tasks.add(task);
            }
        }

        if (this.maxThreads > 1) {
            this.pool.invokeAll(tasks);
        }

        long stop = System.currentTimeMillis();

        if (this.verbose) {
            this.out.println("Elapsed initializeForwardEdgesFromEmptyGraph = " + (stop - start) + " ms");
        }
    }

    private void fes() {
        int maxDegree = this.maxDegree == -1 ? 1000 : this.maxDegree;

        reevaluateForward(new HashSet<>(this.variables));

        while (!this.sortedArrows.isEmpty()) {
            Arrow arrow = this.sortedArrows.first();
            this.sortedArrows.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (this.graph.isAdjacentTo(x, y)) {
                continue;
            }

            if (this.graph.getDegree(x) > maxDegree - 1) {
                continue;
            }

            if (this.graph.getDegree(y) > maxDegree - 1) {
                continue;
            }

            if (!getNaYX(x, y).equals(arrow.getNaYX())) {
                continue;
            }

            if (!new HashSet<>(getTNeighbors(x, y)).equals(arrow.getTNeighbors())) {
                continue;
            }

            if (!new HashSet<>(this.graph.getParents(y)).equals(new HashSet<>(arrow.getParents()))) {
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
        reevaluateBackward(new HashSet<>(this.variables));

        while (!this.sortedArrowsBack.isEmpty()) {
            Arrow arrow = this.sortedArrowsBack.first();
            this.sortedArrowsBack.remove(arrow);

            Node x = arrow.getA();
            Node y = arrow.getB();

            if (!this.graph.isAdjacentTo(x, y)) {
                continue;
            }

            Edge edge = this.graph.getEdge(x, y);

            if (edge.pointsTowards(x)) {
                continue;
            }

            if (!getNaYX(x, y).equals(arrow.getNaYX())) {
                continue;
            }

            if (!new HashSet<>(this.graph.getParents(y)).equals(new HashSet<>(arrow.getParents()))) {
                continue;
            }

            if (!validDelete(x, y, arrow.getHOrT(), arrow.getNaYX())) {
                continue;
            }

            Set<Node> complement = new HashSet<>(arrow.getNaYX());
            complement.removeAll(arrow.getHOrT());

            double _bump = deleteEval(x, y, complement,
                    arrow.parents, this.hashIndices);

            delete(x, y, arrow.getHOrT(), _bump, arrow.getNaYX());

            Set<Node> process = revertToCPDAG();
            process.add(x);
            process.add(y);
            process.addAll(this.graph.getAdjacentNodes(x));
            process.addAll(this.graph.getAdjacentNodes(y));

            reevaluateBackward(new HashSet<>(process));
        }
    }

    // Returns true if knowledge is not empty.
    private boolean existsKnowledge() {
        return !this.knowledge.isEmpty();
    }

    // Calcuates new arrows based on changes in the graph for the forward search.
    private void reevaluateForward(Set<Node> nodes) {
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
                for (int _y = this.from; _y < this.to; _y++) {
                    Node y = this.nodes.get(_y);

                    List<Node> adj;

                    if (Fges.this.mode == Mode.heuristicSpeedup) {
                        adj = Fges.this.effectEdgesGraph.getAdjacentNodes(y);
                    } else if (Fges.this.mode == Mode.coverNoncolliders) {
                        Set<Node> g = new HashSet<>();

                        for (Node n : Fges.this.graph.getAdjacentNodes(y)) {
                            for (Node m : Fges.this.graph.getAdjacentNodes(n)) {
                                if (Fges.this.graph.isAdjacentTo(y, m)) {
                                    continue;
                                }

                                if (Fges.this.graph.isDefCollider(m, n, y)) {
                                    continue;
                                }

                                g.add(m);
                            }
                        }

                        adj = new ArrayList<>(g);
                    } else if (Fges.this.mode == Mode.allowUnfaithfulness) {
//                        adj = new ArrayList<>(variables);
                        adj = new ArrayList<>(GraphUtils.getDconnectedVars(y, new ArrayList<>(), Fges.this.graph));
                        adj.remove(y);
                    } else {
                        throw new IllegalStateException();
                    }

                    for (Node x : adj) {
                        if (Fges.this.adjacencies != null && !(Fges.this.adjacencies.isAdjacentTo(x, y))) {
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

            if (this.maxThreads == 1) {
                task.call();
            } else {
                tasks.add(task);
            }
        }

        if (this.maxThreads > 1) {
            this.pool.invokeAll(tasks);
        }
    }

    // Calculates the new arrows for an a->b edge.
    private void calculateArrowsForward(Node a, Node b) {

        if (this.adjacencies != null && !this.adjacencies.isAdjacentTo(a, b)) {
            return;
        }

        if (a == b) return;

        if (this.graph.isAdjacentTo(a, b)) return;

        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(a.getName(), b.getName())) {
                return;
            }
        }

        Set<Node> naYX = getNaYX(a, b);
        List<Node> TNeighbors = getTNeighbors(a, b);
        Set<Node> parents = new HashSet<>(this.graph.getParents(b));

        HashSet<Node> TNeighborsSet = new HashSet<>(TNeighbors);
        ArrowConfig config = new ArrowConfig(TNeighborsSet, naYX, parents);
        ArrowConfig storedConfig = this.arrowsMap.get(directedEdge(a, b));
        if (config.equals(storedConfig)) return;
        this.arrowsMap.put(directedEdge(a, b), new ArrowConfig(TNeighborsSet, naYX, parents));

        int _depth = min(this.depth, TNeighbors.size());

        DepthChoiceGenerator gen = new DepthChoiceGenerator(TNeighbors.size(), _depth);// TNeighbors.size());
        int[] choice;

        Set<Node> maxT = null;
        double maxBump = Double.NEGATIVE_INFINITY;

        while ((choice = gen.next()) != null) {
            Set<Node> _T = GraphUtils.asSet(choice, TNeighbors);

            double _bump = insertEval(a, b, _T, naYX, parents, this.hashIndices);

            if (_bump > maxBump) {
                maxT = _T;
                maxBump = _bump;
            }
        }

        if (maxBump > 0) {
            addArrowForward(a, b, maxT, TNeighborsSet, naYX, parents, maxBump);
        }
    }

    private void addArrowForward(Node a, Node b, Set<Node> hOrT, Set<Node> TNeighbors, Set<Node> naYX,
                                 Set<Node> parents, double bump) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, TNeighbors, naYX, parents, this.arrowIndex++);
        this.sortedArrows.add(arrow);
    }

    private void addArrowBackward(Node a, Node b, Set<Node> hOrT, Set<Node> naYX,
                                  Set<Node> parents, double bump) {
        Arrow arrow = new Arrow(bump, a, b, hOrT, null, naYX, parents, this.arrowIndex++);
        this.sortedArrowsBack.add(arrow);
    }

    // Reevaluates arrows after removing an edge from the graph.
    private void reevaluateBackward(Set<Node> toProcess) {
        class BackwardTask implements Callable<Boolean> {
            private final Node r;
            private final List<Node> adj;
            private final int chunk;
            private final int from;
            private final int to;

            private BackwardTask(Node r, List<Node> adj, int chunk, int to) {
                this.adj = adj;
                this.chunk = chunk;
                this.from = 0;
                this.to = to;
                this.r = r;
            }

            @Override
            public Boolean call() {
                if (this.to - this.from <= this.chunk) {
                    for (int _w = this.from; _w < this.to; _w++) {
                        Node w = this.adj.get(_w);
                        Edge e = Fges.this.graph.getEdge(w, this.r);

                        if (e != null) {
                            if (e.pointsTowards(this.r)) {
                                calculateArrowsBackward(w, this.r);
                            } else if (e.pointsTowards(w)) {
                                calculateArrowsBackward(this.r, w);
                            } else {
                                calculateArrowsBackward(w, this.r);
                                calculateArrowsBackward(this.r, w);
                            }
                        }
                    }
                }

                return true;
            }
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (Node r : toProcess) {
            List<Node> adjacentNodes = this.graph.getAdjacentNodes(r);
            adjacentNodes.retainAll(toProcess);
            BackwardTask task = new BackwardTask(r, adjacentNodes, getChunkSize(adjacentNodes.size()),
                    adjacentNodes.size());

            if (this.maxThreads == 1) {
                task.call();
            } else {
                tasks.add(task);
            }

            if (this.maxThreads > 1) {
                this.pool.invokeAll(tasks);
            }
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
        Set<Node> parents = new HashSet<>(this.graph.getParents(b));

        List<Node> _naYX = new ArrayList<>(naYX);

        ArrowConfigBackward config = new ArrowConfigBackward(naYX, parents);
        ArrowConfigBackward storedConfig = this.arrowsMapBackward.get(directedEdge(a, b));
        if (config.equals(storedConfig)) return;
        this.arrowsMapBackward.put(directedEdge(a, b), new ArrowConfigBackward(naYX, parents));

        int _depth = min(this.depth, _naYX.size());

        DepthChoiceGenerator gen = new DepthChoiceGenerator(_naYX.size(), _depth);//_naYX.size());
        int[] choice;
        Set<Node> maxComplement = null;
        double maxBump = Double.NEGATIVE_INFINITY;

        while ((choice = gen.next()) != null) {
            Set<Node> complement = GraphUtils.asSet(choice, _naYX);

            double _bump = deleteEval(a, b, complement, parents, this.hashIndices);

            if (_bump > maxBump) {
                maxBump = _bump;
                maxComplement = complement;
            }
        }

        if (maxBump > 0) {
            Set<Node> _H = new HashSet<>(naYX);
            _H.removeAll(maxComplement);
            addArrowBackward(a, b, _H, naYX, parents, maxBump);
        }
    }

    private Set<Node> getCommonAdjacents(Node x, Node y) {
        Set<Node> adj = new HashSet<>(this.graph.getAdjacentNodes(x));
        adj.retainAll(this.graph.getAdjacentNodes(y));
        return adj;
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

    // Evaluate the Insert(X, Y, TNeighbors) operator (Definition 12 from Chickering, 2002).
    private double insertEval(Node x, Node y, Set<Node> T, Set<Node> naYX, Set<Node> parents,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set = new HashSet<>(naYX);
        set.addAll(T);
        set.addAll(parents);

        return scoreGraphChange(x, y, set, hashIndices);
    }

    // Evaluate the Delete(X, Y, TNeighbors) operator (Definition 12 from Chickering, 2002).
    private double deleteEval(Node x, Node y, Set<Node> complement, Set<Node> parents,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set = new HashSet<>(complement);
        set.addAll(parents);
        set.remove(x);

        return -scoreGraphChange(x, y, set, hashIndices);
    }

    // Do an actual insertion. (Definition 12 from Chickering, 2002).
    private void insert(Node x, Node y, Set<Node> T, double bump) {
        this.graph.addDirectedEdge(x, y);

        int numEdges = this.graph.getNumEdges();

        if (numEdges % 1000 == 0) {
            this.out.println("Num edges added: " + numEdges);
        }

        if (this.verbose) {
            int cond = T.size() + getNaYX(x, y).size() + this.graph.getParents(y).size();

            String message = this.graph.getNumEdges() + ". INSERT " + this.graph.getEdge(x, y)
                    + " " + T + " " + bump
                    + " degree = " + GraphUtils.getDegree(this.graph)
                    + " indegree = " + GraphUtils.getIndegree(this.graph) + " cond = " + cond;
            TetradLogger.getInstance().forceLogMessage(message);
        }

        for (Node _t : T) {
            this.graph.removeEdge(_t, y);
            this.graph.addDirectedEdge(_t, y);

            if (this.verbose) {
                String message = "--- Directing " + this.graph.getEdge(_t, y);
                TetradLogger.getInstance().forceLogMessage(message);
            }
        }
    }

    // Do an actual deletion (Definition 13 from Chickering, 2002).
    private void delete(Node x, Node y, Set<Node> H, double bump, Set<Node> naYX) {
        Edge oldxy = this.graph.getEdge(x, y);

        Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);

        this.graph.removeEdge(oldxy);

        int numEdges = this.graph.getNumEdges();
        if (numEdges % 1000 == 0) {
            this.out.println("Num edges (backwards) = " + numEdges);
        }

        if (this.verbose) {
            int cond = diff.size() + this.graph.getParents(y).size();

            String message = (this.graph.getNumEdges()) + ". DELETE " + x + " --> " + y
                    + " H = " + H + " NaYX = " + naYX
                    + " degree = " + GraphUtils.getDegree(this.graph)
                    + " indegree = " + GraphUtils.getIndegree(this.graph)
                    + " diff = " + diff + " (" + bump + ") "
                    + " cond = " + cond;
            TetradLogger.getInstance().forceLogMessage(message);
        }

        for (Node h : H) {
            if (this.graph.isParentOf(h, y) || this.graph.isParentOf(h, x)) {
                continue;
            }

            Edge oldyh = this.graph.getEdge(y, h);

            this.graph.removeEdge(oldyh);

            this.graph.addDirectedEdge(y, h);

            if (this.verbose) {
                TetradLogger.getInstance().forceLogMessage("--- Directing " + oldyh + " to "
                        + this.graph.getEdge(y, h));
            }

            Edge oldxh = this.graph.getEdge(x, h);

            if (Edges.isUndirectedEdge(oldxh)) {
                this.graph.removeEdge(oldxh);

                this.graph.addEdge(directedEdge(x, h));

                if (this.verbose) {
                    TetradLogger.getInstance().forceLogMessage("--- Directing " + oldxh + " to "
                            + this.graph.getEdge(x, h));
                }
            }
        }
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

        return isClique(union) && semidirectedPathCondition(y, x, union)
                && !violatesKnowledge;
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
        return isClique(diff) && !violatesKnowledge;
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

            if (!graph.isAncestorOf(nodeB, nodeA)) {
                graph.removeEdges(nodeA, nodeB);
                graph.addDirectedEdge(nodeA, nodeB);

                if (this.verbose) {
                    TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB));
                }
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

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (this.verbose) {
                            TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }

                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (this.verbose) {
                            TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }
            } else if (this.knowledge.isForbidden(B, A)) {
                Node nodeA = edge.getNode2();
                Node nodeB = edge.getNode1();

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (this.verbose) {
                            TetradLogger.getInstance().forceLogMessage("Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                        }
                    }
                }
                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);

                        if (this.verbose) {
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

    // Returns true iif the given set forms a clique in the given graph.
    private boolean isClique(Set<Node> nodes) {
        List<Node> _nodes = new ArrayList<>(nodes);
        for (int i = 0; i < _nodes.size(); i++) {
            for (int j = i + 1; j < _nodes.size(); j++) {
                if (!this.graph.isAdjacentTo(_nodes.get(i), _nodes.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

    // Returns true iff every semidirected path from from to to contains an element of cond.
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

            for (Node u : this.graph.getAdjacentNodes(t)) {
                Edge edge = this.graph.getEdge(t, u);
                Node c = Fges.traverseSemiDirected(t, edge);

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
        rules.setVerbose(this.meekVerbose);
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

    private double scoreDag(Graph dag, boolean recordScores) {
        if (this.score instanceof GraphScore) return 0.0;
        dag = GraphUtils.replaceNodes(dag, getVariables());

        Score score = this.score.defaultScore();

        double _score = 0;

        for (Node node : getVariables()) {

            if (score instanceof SemBicScore) {
                List<Node> x = dag.getParents(node);

                int[] parentIndices = new int[x.size()];

                int count = 0;
                for (Node parent : x) {
                    parentIndices[count++] = this.hashIndices.get(parent);
                }

                double bic = score.localScore(this.hashIndices.get(node), parentIndices);

                if (recordScores) {
                    NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                    node.addAttribute("BIC", nf.format(bic));
                }

                _score += bic;
            }
        }

        if (recordScores) {
            NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
            this.graph.addAttribute("BIC", nf.format(_score));
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

        return this.score.localScoreDiff(xIndex, yIndex, parentIndices);
    }

    private List<Node> getVariables() {
        return this.variables;
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

    private String logBayesPosteriorFactorsString(Map<Edge, Double> factors) {
        NumberFormat nf = new DecimalFormat("0.00");
        StringBuilder builder = new StringBuilder();

        List<Edge> edges = new ArrayList<>(factors.keySet());

        edges.sort((o1, o2) -> -Double.compare(factors.get(o1), factors.get(o2)));

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

    /**
     * Internal.
     */

    private enum Mode {
        allowUnfaithfulness, heuristicSpeedup, coverNoncolliders
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
            return this.bump;
        }

        public Node getA() {
            return this.a;
        }

        public Node getB() {
            return this.b;
        }

        Set<Node> getHOrT() {
            return this.hOrT;
        }

        Set<Node> getNaYX() {
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
            return "Arrow<" + this.a + "->" + this.b + " bump = " + this.bump
                    + " t/h = " + this.hOrT
                    + " TNeighbors = " + getTNeighbors()
                    + " parents = " + this.parents
                    + " naYX = " + this.naYX + ">";
        }

        public int getIndex() {
            return this.index;
        }

        public Set<Node> getTNeighbors() {
            return this.TNeighbors;
        }

        public void setTNeighbors(Set<Node> TNeighbors) {
            this.TNeighbors = TNeighbors;
        }

        public Set<Node> getParents() {
            return this.parents;
        }
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
            for (int i = this.from; i < this.to; i++) {
                if ((i + 1) % 1000 == 0) {
                    Fges.this.count[0] += 1000;
                    Fges.this.out.println("Initializing effect edges: " + (Fges.this.count[0]));
                }

                Node y = this.nodes.get(i);

                for (int j = i + 1; j < this.nodes.size() && !Thread.currentThread().isInterrupted(); j++) {
                    Node x = this.nodes.get(j);

                    if (existsKnowledge()) {
                        if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                            continue;
                        }

                        if (invalidSetByKnowledge(y, this.emptySet)) {
                            continue;
                        }
                    }

                    if (Fges.this.adjacencies != null && !Fges.this.adjacencies.isAdjacentTo(x, y)) {
                        continue;
                    }

                    int child = Fges.this.hashIndices.get(y);
                    int parent = Fges.this.hashIndices.get(x);
                    double bump = Fges.this.score.localScoreDiff(parent, child);

                    if (Fges.this.symmetricFirstStep) {
                        double bump2 = Fges.this.score.localScoreDiff(child, parent);
                        bump = max(bump, bump2);
                    }

                    if (Fges.this.boundGraph != null && !Fges.this.boundGraph.isAdjacentTo(x, y)) {
                        continue;
                    }

                    if (bump > 0) {
                        Fges.this.effectEdgesGraph.addEdge(Edges.undirectedEdge(x, y));
                    }
                }
            }

            return true;
        }
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
            return this.T;
        }

        public void setT(Set<Node> t) {
            this.T = t;
        }

        public void setNayx(Set<Node> nayx) {
            this.nayx = nayx;
        }

        public Set<Node> getParents() {
            return this.parents;
        }

        public void setParents(Set<Node> parents) {
            this.parents = parents;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrowConfig that = (ArrowConfig) o;
            return this.T.equals(that.T) && this.nayx.equals(that.nayx) && this.parents.equals(that.parents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.T, this.nayx, this.parents);
        }
    }

    private static class ArrowConfigBackward {
        private Set<Node> nayx;
        private Set<Node> parents;

        public ArrowConfigBackward(Set<Node> nayx, Set<Node> parents) {
            this.setNayx(nayx);
            this.setParents(parents);
        }

        public void setNayx(Set<Node> nayx) {
            this.nayx = nayx;
        }

        public Set<Node> getParents() {
            return this.parents;
        }

        public void setParents(Set<Node> parents) {
            this.parents = parents;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrowConfigBackward that = (ArrowConfigBackward) o;
            return this.nayx.equals(that.nayx) && this.parents.equals(that.parents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.nayx, this.parents);
        }
    }
}
