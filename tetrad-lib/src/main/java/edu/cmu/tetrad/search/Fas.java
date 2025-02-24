/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014 by Peter Spirtes, Richard Scheines, Joseph   //
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import javax.sound.midi.SysexMessage;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * Implements the Fast Adjacency Search (FAS), which is the adjacency search of the PC algorithm (see). This is a useful
 * algorithm in many contexts, including as the first step of FCI (see).
 * <p>
 * The idea of FAS is that at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S,
 * where S is a subset of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency
 * search performs this procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1.
 * Here, d1 is either the maximum depth or else the first such depth at which no edges can be removed. The
 * interpretation of this adjacency search is different for different algorithms, depending on the assumptions of the
 * algorithm. A mapping from {x, y} to S({x, y}) is returned for edges x *-* y that have been removed.
 * <p>
 * FAS may optionally use a heuristic from Causation, Prediction, and Search, which (like PC-Stable) renders the output
 * invariant to the order of the input variables.
 * <p>
 * This algorithm was described in the earlier edition of this book:
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author peterspirtes
 * @author clarkglymour
 * @author josephramsey.
 * @version $Id: $Id
 * @see Pc
 * @see Fci
 * @see Knowledge
 */
public class Fas implements IFas {
    /**
     * The test to be used for conditional independence tests.
     */
    private final IndependenceTest test;
    /**
     * The logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The sepsets that were discovered in the search.
     */
    private SepsetMap sepset = new SepsetMap();
    /**
     * The heuristic to use.
     */
    private PcCommon.PcHeuristicType heuristic = PcCommon.PcHeuristicType.NONE;
    /**
     * The depth of the search.
     */
    private int depth = 1000;
    /**
     * Whether the stable adjacency search should be used.
     */
    private boolean stable = true;
    /**
     * The elapsed time of the search.
     */
    private long elapsedTime = 0L;
    /**
     * Whether verbose output should be printed.
     */
    private transient PrintStream out = System.out;
    /**
     * Whether verbose output should be printed.
     */
    private boolean verbose = false;
    /**
     * Represents the start time of a specific operation or process within the algorithm's execution. This variable is
     * used to measure elapsed time or to enforce time constraints during the execution.
     */
    private long startTime;
    /**
     * Specifies the timeout value for the search operation or algorithm execution. The timeout value is used to
     * determine the maximum allowed duration for the execution, preventing any operation from exceeding this limit.
     */
    private long timeout;

    /**
     * Constructor.
     *
     * @param test The test to use for oracle conditional independence test results.
     */
    public Fas(IndependenceTest test) {
        this.test = test;
    }

    /**
     * Checks if there is an adjacency between two nodes on a side.
     *
     * @param scores      The map of scores for each edge.
     * @param test        The independence test to use.
     * @param adjacencies The map of nodes and their adjacent nodes.
     * @param depth       The maximum depth to search for adjacencies.
     * @param x           The first node.
     * @param y           The second node.
     * @param mainThread  The main thread. Needed to try to interrupt the search if it takes too long.
     * @return True if there is an adjacency between x and y at the given depth, false otherwise.
     * @throws InterruptedException if any
     */
    private static boolean checkSide(Map<Edge, Double> scores, IndependenceTest test, Map<Node, Set<Node>> adjacencies,
                                     int depth, Node x, Node y,
                                     PcCommon.PcHeuristicType heuristic,
                                     Knowledge knowledge,
                                     SepsetMap sepsets, Thread mainThread) throws InterruptedException {
        if (!adjacencies.get(x).contains(y)) return false;

        List<Node> _adjx = new ArrayList<>(adjacencies.get(x));
        _adjx.remove(y);

        if (heuristic == PcCommon.PcHeuristicType.HEURISTIC_1 || heuristic == PcCommon.PcHeuristicType.HEURISTIC_2) {
            Collections.sort(_adjx);
        }

        List<Node> ppx = possibleParents(x, _adjx, knowledge, y);

        Map<Node, Double> scores2 = new HashMap<>();

        for (Node node : ppx) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            Double _score = scores.get(Edges.undirectedEdge(node, x));
            scores2.put(node, _score);
        }

        if (heuristic == PcCommon.PcHeuristicType.HEURISTIC_3) {
            ppx.sort(Comparator.comparing(scores2::get));
            Collections.reverse(ppx);
        }

        if (ppx.size() >= depth) {
            ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
            int[] choice;

            while ((choice = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Set<Node> Z = GraphUtils.asSet(choice, ppx);

                // Check for main thread interruption
                if (mainThread.isInterrupted()) {
                    Thread.currentThread().interrupt(); // Propagate to current thread
                    throw new InterruptedException(); // Stop processing
                }

                // Check for current thread interruption
                if (Thread.currentThread().isInterrupted()) {
                    mainThread.interrupt(); // Propagate back to main thread
                    throw new InterruptedException();
                }

                boolean independent = test.checkIndependence(x, y, Z).isIndependent();

                // Check for main thread interruption
                if (mainThread.isInterrupted()) {
                    Thread.currentThread().interrupt(); // Propagate to current thread
                    throw new InterruptedException(); // Stop processing
                }

                // Check for current thread interruption
                if (Thread.currentThread().isInterrupted()) {
                    mainThread.interrupt(); // Propagate back to main thread
                    throw new InterruptedException();
                }

                boolean noEdgeRequired = knowledge.noEdgeRequired(x.getName(), y.getName());

                if (independent && noEdgeRequired) {
                    adjacencies.get(x).remove(y);
                    adjacencies.get(y).remove(x);

                    sepsets.set(x, y, Z);

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns a list of nodes that are possible parents of a given node, based on the adjacency list of the given node,
     * the knowledge object, and another node.
     *
     * @param x         The node for which to find possible parents.
     * @param adjx      The adjacency list of the node x.
     * @param knowledge The knowledge object that provides information about conditional independencies.
     * @param y         Another node in the graph.
     * @return A list of nodes that are possible parents of the node x.
     * @throws InterruptedException if any
     */
    private static List<Node> possibleParents(Node x, List<Node> adjx, Knowledge knowledge, Node y) throws InterruptedException {
        List<Node> possibleParents = new LinkedList<>();
        String _x = x.getName();

        for (Node z : adjx) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (z == null) continue;
            if (z == x) continue;
            if (z == y) continue;
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    /**
     * Determines if a given node is a possible parent of another node based on the provided knowledge.
     *
     * @param z         The first node.
     * @param x         The second node.
     * @param knowledge The knowledge object that provides information about conditional independencies.
     * @return True if the node z is a possible parent of node x, false otherwise.
     */
    private static boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    /**
     * Performs a search to discover all adjacencies in the graph. The procedure is to remove edges in the graph which
     * connect pairs of variables that are independent, conditional on some other set of variables in the graph (the
     * "sepset"). These edges are removed in tiers. First, edges which are independent conditional on zero other
     * variables are removed, then edges which are independent conditional on one other variable are removed. Then two,
     * then three, and so on, until no more edges can be removed from the graph. The edges which remain in the graph
     * after this procedure are the adjacencies in the data.
     *
     * @return An undirected graph that summarizes the conditional independencies in the data.
     * @throws InterruptedException if any
     */
    @Override
    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent, conditional on some other set of variables in the graph (the "sepset"). These
     * are removed in tiers.  First, edges which are independent, conditional on zero other variables are removed. Then
     * edges which are independent conditional on one other variable are removed, then two, then three, and so on, until
     * no more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @param nodes A list of nodes to search over.
     * @return An undirected graph that summarizes the conditional independencies in the data.
     * @throws InterruptedException if any
     */
    public Graph search(List<Node> nodes) throws InterruptedException {
        Thread mainThread = Thread.currentThread();

        if (startTime <= 0) {
            startTime = System.currentTimeMillis();
        }

        nodes = new ArrayList<>(nodes);

        this.logger.addOutputStream(out);

        if (verbose) {
            this.logger.log("Starting Fast Adjacency Search.");
        }

        this.test.setVerbose(this.verbose);

        int _depth = this.depth;

        if (_depth == -1) {
            _depth = 1000;
        }

        this.sepset = new SepsetMap();

        List<Edge> edges = new ArrayList<>();

        if (this.heuristic == PcCommon.PcHeuristicType.HEURISTIC_1) {
            Collections.sort(nodes);
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                edges.add(Edges.undirectedEdge(nodes.get(i), nodes.get(j)));
            }
        }

        Map<Edge, Double> scores = new HashMap<>();

        class Task implements Callable<Map<Edge, Double>> {
            private final Edge edge;
            private final IndependenceTest test;

            public Task(Edge edge, IndependenceTest test) {
                this.edge = edge;
                this.test = test;
            }

            @Override
            public Map<Edge, Double> call() {
                IndependenceResult result;
                try {
                    result = this.test.checkIndependence(edge.getNode1(), edge.getNode2(), new HashSet<>());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                Map<Edge, Double> map = new HashMap<>();
                map.put(edge, result.getScore());

                return map;
            }
        }

        List<Task> tasks = new ArrayList<>();

        for (Edge edge : edges) {
            tasks.add(new Task(edge, test));
        }

        if (stable) {
            int parallelism = Runtime.getRuntime().availableProcessors();
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            List<Future<Map<Edge, Double>>> theseResults;

            theseResults = pool.invokeAll(tasks);

            for (Future<Map<Edge, Double>> future : theseResults) {
                try {
                    Map<Edge, Double> result = future.get();

                    for (Edge edge : result.keySet()) {
                        scores.put(edge, result.get(edge));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
//                    TetradLogger.getInstance().log(e.getMessage());
                    pool.shutdown();
                    return null;
                }
            }

            pool.shutdown();
        } else {
            System.out.println("Stable option false");

            for (Task task : tasks) {
                long current = System.currentTimeMillis();

                if (getTimeout() > 0 && current > getStartTime() + getTimeout()) {
                    throw new RuntimeException("Timeout time = " + current + " startTime = " + getStartTime()
                                               + " timeout = " + getTimeout());
                }

                Map<Edge, Double> result = task.call();

                for (Edge edge : result.keySet()) {
                    scores.put(edge, result.get(edge));
                }
            }
        }

        if (this.heuristic == PcCommon.PcHeuristicType.HEURISTIC_2 || this.heuristic == PcCommon.PcHeuristicType.HEURISTIC_3) {
            edges.sort(Comparator.comparing(scores::get));
        }

        Map<Node, Set<Node>> adjacencies = new HashMap<>();

        for (Node node : nodes) {
            Set<Node> set = new LinkedHashSet<>();

            for (Node _node : nodes) {
                if (_node == node) continue;
                set.add(_node);
            }

            adjacencies.put(node, set);
        }

        for (Edge edge : new ArrayList<>(edges)) {
            if (scores.get(edge) != null && scores.get(edge) < 0 || (this.knowledge.isForbidden(edge.getNode1().getName(), edge.getNode2().getName()) && (this.knowledge.isForbidden(edge.getNode2().getName(), edge.getNode1().getName())))) {
                edges.remove(edge);
                adjacencies.get(edge.getNode1()).remove(edge.getNode2());
                adjacencies.get(edge.getNode2()).remove(edge.getNode1());
                this.sepset.set(edge.getNode1(), edge.getNode2(), new HashSet<>());
            }
        }

        for (int d = 1; d <= _depth; d++) {
            if (verbose) {
                System.out.println("Depth: " + d);
            }

            boolean more;

            if (this.stable) {
                Map<Node, Set<Node>> adjacenciesCopy = new HashMap<>();

                for (Node node : adjacencies.keySet()) {
                    adjacenciesCopy.put(node, new LinkedHashSet<>(adjacencies.get(node)));
                }

                adjacencies = adjacenciesCopy;
            }

            more = searchAtDepth(scores, edges, this.test, adjacencies, d, mainThread);

            if (!more) {
                break;
            }
        }

        // The search graph. It is assumed going in that all the true adjacencies of x are in this graph for every node
        // x. It is hoped (i.e., true in the large sample limit) that true adjacencies are never removed.
        Graph graph = new EdgeListGraph(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (adjacencies.get(x).contains(y)) {
                    graph.addUndirectedEdge(x, y);
                }
            }
        }

        if (verbose) {
            this.logger.log("Finishing Fast Adjacency Search.");
        }

        this.elapsedTime = System.currentTimeMillis() - startTime;

        return graph;
    }

    /**
     * Sets the maximum depth for the search.
     *
     * @param depth The maximum depth to set.
     * @throws IllegalArgumentException if the depth is less than -1.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    /**
     * Sets the knowledge for this object.
     *
     * @param knowledge The knowledge object to set.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the sepsets that were discovered in the search. A 'sepset' for test X _||_ Y | Z1,...,Zm would be
     * {Z1,...,Zm}
     *
     * @return A map of these sepsets indexed by {X, Y}.
     */
    public SepsetMap getSepsets() {
        return this.sepset;
    }

    /**
     * Sets the verbose mode.
     *
     * @param verbose true if verbose mode is enabled, false otherwise.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the elapsed time of the search.
     *
     * @return This elapsed time.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Retrieves the list of nodes in the graph.
     *
     * @return A List of Node objects representing the nodes in the graph.
     */
    @Override
    public List<Node> getNodes() {
        return this.test.getVariables();
    }

    /**
     * Retrieves the list of ambiguous triples involving the given node.
     *
     * @param node The node for which to retrieve the ambiguous triples.
     * @return A list of Triple objects representing the ambiguous triples involving the node.
     */
    @Override
    public List<Triple> getAmbiguousTriples(Node node) {
        return new ArrayList<>();
    }

    //==============================PRIVATE METHODS======================/

    /**
     * Sets the PrintStream to be used for output.
     *
     * @param out the PrintStream to be used for output
     */
    @Override
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Sets the type of heuristic to be used in the PC algorithm.
     *
     * @param pcHeuristic The type of heuristic to be used.
     */
    public void setPcHeuristicType(PcCommon.PcHeuristicType pcHeuristic) {
        this.heuristic = pcHeuristic;
    }

    /**
     * Sets whether the stable adjacency search should be used. Default is false. Default is false. See the following
     * reference for this:
     * <p>
     * Colombo, D., &amp; Maathuis, M. H. (2014). Order-independent constraint-based causal structure learning. J. Mach.
     * Learn. Res., 15(1), 3741-3782.
     *
     * @param stable True iff the case.
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Calculates the maximum free degree among the nodes in the given adjacency map.
     *
     * @param adjacencies A map containing nodes as keys and sets of adjacent nodes as values.
     * @return The maximum free degree among the nodes.
     */
    private int freeDegree(Map<Node, Set<Node>> adjacencies) {
        int max = 0;

        for (Node x : adjacencies.keySet()) {
            Set<Node> opposites = adjacencies.get(x);

            for (Node y : opposites) {
                Set<Node> adjx = new LinkedHashSet<>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

    /**
     * Searches for adjacencies at a given depth in the graph.
     *
     * @param scores      The map of scores for each edge.
     * @param edges       The list of edges to search over.
     * @param test        The independence test to use.
     * @param adjacencies The map of nodes and their adjacent nodes.
     * @param depth       The maximum depth to search.
     * @param mainThread  The main thread. Needed to try to interrupt the search if it takes too long.
     * @return true if there are adjacencies at the given depth, false otherwise.
     * @throws InterruptedException if any
     */
    private boolean searchAtDepth(Map<Edge, Double> scores, List<Edge> edges, IndependenceTest test, Map<Node, Set<Node>> adjacencies, int depth, Thread mainThread) throws InterruptedException {
        if (mainThread.isInterrupted()) {
            return false;
        }

        // We need to write out the threading longhand here and explicitly check for InterruptedExceptions on the
        // Futures. This is because the ForkJoinPool doesn't allow checked exceptions to be thrown from the
        // ForkJoinTask's compute() method. This is a design flaw in the ForkJoinPool, but it is what it is.
        // This also means that we can't catch the InterruptedException in a try/catch block for a parallel stream
        // or a parallel stream with a collector. We have to use the ForkJoinPool and Futures. For fast independence
        // tests, this is not a big deal, because the overhead of the ForkJoinPool is not that great. For slow
        // independence tests, this is a big deal, because the overhead of the ForkJoinPool is significant.
        // josephramsey 2024-12-10
        class Task implements Callable<Boolean> {
            private final Edge edge;
            private final Map<Edge, Double> scores;
            private final IndependenceTest test;
            private final Map<Node, Set<Node>> adjacencies;
            private final int depth;
            private final PcCommon.PcHeuristicType heuristic;
            private final Knowledge knowledge;
            private final SepsetMap sepset;
            private final Thread mainThread;

            public Task(Edge edge, Map<Edge, Double> scores, IndependenceTest test, Map<Node, Set<Node>> adjacencies, int depth, PcCommon.PcHeuristicType heuristic, Knowledge knowledge, SepsetMap sepset, Thread mainThread) {
                this.edge = edge;
                this.scores = scores;
                this.test = test;
                this.adjacencies = adjacencies;
                this.depth = depth;
                this.heuristic = heuristic;
                this.knowledge = knowledge;
                this.sepset = sepset;
                this.mainThread = mainThread;
            }

            @Override
            public Boolean call() {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                try {
                    boolean b = checkSide(scores, test, adjacencies, depth, x, y, heuristic, knowledge, sepset, mainThread);

                    if (!b) {
                        checkSide(scores, test, adjacencies, depth, y, x, heuristic, knowledge, sepset, mainThread);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return true;
            }
        }

        List<Task> tasks = new ArrayList<>();

        for (Edge edge : edges) {
            tasks.add(new Task(edge, scores, test, adjacencies, depth, heuristic, knowledge, sepset, mainThread));
        }

        if (stable) {
            int parallelism = Runtime.getRuntime().availableProcessors();
            ForkJoinPool pool = new ForkJoinPool(parallelism);
            List<Future<Boolean>> theseResults;
            theseResults = pool.invokeAll(tasks);

            for (Future<Boolean> future : theseResults) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    TetradLogger.getInstance().log(e.getMessage());
                    pool.shutdown();
                    return false;
                }
            }

            pool.shutdown();
        } else {
            for (Task task : tasks) {
                task.call();
            }
        }

        return freeDegree(adjacencies) > depth;
    }

    /**
     * Gets the timeout value for the search operation or algorithm execution. The timeout value determines the maximum
     * duration the process can run before it is terminated.
     *
     * @return The timeout value in milliseconds.
     */
    public long getTimeout() {
        return this.timeout;
    }

    /**
     * Specifies the timeout value for the search operation or algorithm execution. The timeout value is used to
     * determine the maximum allowed duration for the execution, preventing any operation from exceeding this limit.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Represents the start time of a specific operation or process within the algorithm's execution. This variable is
     * used to measure elapsed time or to enforce time constraints during the execution.
     */
    public long getStartTime() {
        return this.startTime;
    }

    /**
     * Sets the start time for a specific operation or process.
     *
     * @param startTime The start time in milliseconds.
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
}

