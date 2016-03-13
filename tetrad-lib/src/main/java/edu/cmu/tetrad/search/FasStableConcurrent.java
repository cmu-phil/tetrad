package edu.cmu.tetrad.search;

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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Implements the "fast adjacency search" used in several causal algorithms in this package. In the fast adjacency
 * search, at a given stage of the search, an edge X*-*Y is removed from the graph if X _||_ Y | S, where S is a subset
 * of size d either of adj(X) or of adj(Y), where d is the depth of the search. The fast adjacency search performs this
 * procedure for each pair of adjacent edges in the graph and for each depth d = 0, 1, 2, ..., d1, where d1 is either
 * the maximum depth or else the first such depth at which no edges can be removed. The interpretation of this adjacency
 * search is different for different algorithms, depending on the assumptions of the algorithm. A mapping from {x, y} to
 * S({x, y}) is returned for edges x *-* y that have been removed.
 * </p>
 * This variant uses the Pc-Stable modification, calculating independencies in parallel within each depth.
 * It uses a slightly different algorithm from FasStableConcurrent, probably better.
 *
 * @author Joseph Ramsey.
 */
public class FasStableConcurrent implements IFas {

    /**
     * The independence test. This should be appropriate to the types
     */
    private IndependenceTest test;

    /**
     * Specification of which edges are forbidden or required.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The maximum number of variables conditioned on in any conditional independence test. If the depth is -1, it will
     * be taken to be the maximum value, which is 1000. Otherwise, it should be set to a non-negative integer.
     */
    private int depth = 1000;

    /**
     * The number of independence tests.
     */
    private int numIndependenceTests;


    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * The ns found during the search.
     */
    private SepsetMap sepsets = new SepsetMap();

    /**
     * The depth 0 graph, specified initially.
     */
    private Graph initialGraph;

    // Number formatter.
    private NumberFormat nf = new DecimalFormat("0.00E0");

    /**
     * Set to true if verbose output is desired.
     */
    private boolean verbose = false;

    // The concurrency pool.
    private ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

    /**
     * Where verbose output is sent.
     */
    private PrintStream out = System.out;

    int chunk = 50;


    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new FastAdjacencySearch.
     */
    public FasStableConcurrent(IndependenceTest test) {
        this.test = test;
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent conditional on some other set of variables in the graph (the "sepset"). These are
     * removed in tiers.  First, edges which are independent conditional on zero other variables are removed, then edges
     * which are independent conditional on one other variable are removed, then two, then three, and so on, until no
     * more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @return a SepSet, which indicates which variables are independent conditional on which other variables
     */
    public Graph search() {
        this.logger.log("info", "Starting Fast Adjacency Search.");

        // The search graph. It is assumed going in that all of the true adjacencies of x are in this graph for every node
        // x. It is hoped (i.e. true in the large sample limit) that true adjacencies are never removed.
        Graph graph = new EdgeListGraphSingleConnections(test.getVariables());

        sepsets = new SepsetMap();

        //this is bad when starting from init graph --AJ
        sepsets.setReturnEmptyIfNotSet(true);

        int _depth = depth;

        if (_depth == -1) {
            _depth = 1000;
        }


        Map<Node, Set<Node>> adjacencies = new ConcurrentSkipListMap<>();
        List<Node> nodes = graph.getNodes();

        for (Node node : nodes) {
            adjacencies.put(node, new HashSet<Node>());
        }


        for (int d = 0; d <= _depth; d++) {
            boolean more;

            if (d == 0) {
                more = searchAtDepth0(nodes, test, adjacencies);
            } else {
                more = searchAtDepth(nodes, test, adjacencies, d);
            }

            if (!more) {
                break;
            }
        }

        if (verbose) {
            out.println("Finished with search, constructing Graph...");
        }

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
            out.println("Finished constructing Graph.");
        }

        if (verbose) {
            this.logger.log("info", "Finishing Fast Adjacency Search.");
        }

        return graph;
    }

    @Override
    public Graph search(List<Node> nodes) {
        return null;
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0.");
        }

        this.depth = depth;
    }

    @Override
    public boolean isAggressivelyPreventCycles() {
        return false;
    }

    @Override
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {

    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return test;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }

    //==============================PRIVATE METHODS======================/

    private boolean searchAtDepth0(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies) {
        if (verbose) {
            out.println("Searching at depth 0.");
            System.out.println("Searching at depth 0.");
        }

        final List<Node> empty = Collections.emptyList();

        class Depth0Task extends RecursiveTask<Boolean> {
            private int chunk;
            private int from;
            private int to;

            public Depth0Task(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        if (verbose) {
                            if ((i + 1) % 1000 == 0) System.out.println("i = " + (i + 1));
                        }

                        final Node x = nodes.get(i);

                        for (int j = i + 1; j < nodes.size(); j++) {
                            final Node y = nodes.get(j);

                            if (initialGraph != null) {
                                Node x2 = initialGraph.getNode(x.getName());
                                Node y2 = initialGraph.getNode(y.getName());

                                if (!initialGraph.isAdjacentTo(x2, y2)) {
                                    continue;
                                }
                            }

                            boolean independent;

                            try {
                                independent = test.isIndependent(x, y, empty);
                            } catch (Exception e) {
                                e.printStackTrace();
                                independent = true;
                            }

                            numIndependenceTests++;

                            boolean noEdgeRequired =
                                    knowledge.noEdgeRequired(x.getName(), y.getName());

                            if (independent && noEdgeRequired) {
                                if (!sepsets.isReturnEmptyIfNotSet()) {
                                    getSepsets().set(x, y, empty);
                                }

                                if (verbose) {
                                    TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFact(x, y, empty) + " p = " +
                                            nf.format(test.getPValue()));

                                    out.println(SearchLogUtils.independenceFact(x, y, empty) + " p = " +
                                            nf.format(test.getPValue()));
                                }
                            } else if (!forbiddenEdge(x, y)) {
                                adjacencies.get(x).add(y);
                                adjacencies.get(y).add(x);

                                if (verbose) {
                                    TetradLogger.getInstance().log("dependencies", SearchLogUtils.independenceFact(x, y, empty) + " p = " +
                                            nf.format(test.getPValue()));
                                }
                            }
                        }
                    }

                    return true;
                } else {
                    List<Depth0Task> tasks = new ArrayList<Depth0Task>();

                    final int mid = (to - from) / 2;

                    tasks.add(new Depth0Task(chunk, from, from + mid));
                    tasks.add(new Depth0Task(chunk, from + mid, to));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        pool.invoke(new Depth0Task(chunk, 0, nodes.size()));

        return freeDegree(nodes, adjacencies) > 0;
    }

    private boolean forbiddenEdge(Node x, Node y) {
        String name1 = x.getName();
        String name2 = y.getName();

        if (knowledge.isForbidden(name1, name2) &&
                knowledge.isForbidden(name2, name1)) {
            if (verbose) {
                this.logger.log("edgeRemoved", "Removed " + Edges.undirectedEdge(x, y) + " because it was " +
                        "forbidden by background knowledge.");
            }

            return true;
        }

        return false;
    }

    private int freeDegree(List<Node> nodes, Map<Node, Set<Node>> adjacencies) {
        int max = 0;

        for (Node x : nodes) {
            Set<Node> opposites = adjacencies.get(x);

            for (Node y : opposites) {
                Set<Node> adjx = new HashSet<Node>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

//    private boolean freeDegreeGreaterThanDepth(Map<Node, Set<Node>> adjacencies, int depth) {
//        for (Node x : adjacencies.keySet()) {
//            Set<Node> opposites = adjacencies.get(x);
//
//            if (opposites.size() - 1 > depth) {
//                return true;
//            }
//        }
//
//        return false;
//    }

    private boolean searchAtDepth(final List<Node> nodes, final IndependenceTest test, final Map<Node, Set<Node>> adjacencies,
                                  final int depth) {

        if (verbose) {
            out.println("Searching at depth " + depth);
            System.out.println("Searching at depth " + depth);
        }

        final Map<Node, Set<Node>> adjacenciesCopy = new HashMap<Node, Set<Node>>();

        for (Node node : adjacencies.keySet()) {
            adjacenciesCopy.put(node, new HashSet<>(adjacencies.get(node)));
        }

        class DepthTask extends RecursiveTask<Boolean> {
            private int chunk;
            private int from;
            private int to;

            public DepthTask(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        if (verbose) {
                            if ((i + 1) % 1000 == 0) System.out.println("i = " + (i + 1));
                        }

                        Node x = nodes.get(i);

                        List<Node> adjx = new ArrayList<>(adjacenciesCopy.get(x));

                        EDGE:
                        for (Node y : adjx) {
                            List<Node> _adjx = new ArrayList<>(adjx);
                            _adjx.remove(y);
                            List<Node> ppx = possibleParents(x, _adjx, knowledge);

                            if (ppx.size() >= depth) {
                                ChoiceGenerator cg = new ChoiceGenerator(ppx.size(), depth);
                                int[] choice;

                                while ((choice = cg.next()) != null) {
                                    List<Node> condSet = GraphUtils.asList(choice, ppx);

                                    boolean independent;

                                    try {
                                        numIndependenceTests++;
                                        independent = test.isIndependent(x, y, condSet);
                                    } catch (Exception e) {
                                        independent = false;
                                    }

                                    boolean noEdgeRequired =
                                            knowledge.noEdgeRequired(x.getName(), y.getName());

                                    if (independent && noEdgeRequired) {
                                        adjacencies.get(x).remove(y);
                                        adjacencies.get(y).remove(x);

                                        getSepsets().set(x, y, condSet);

                                        if (verbose) {
                                            TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFact(x, y, condSet) + " p = " +
                                                    nf.format(test.getPValue()));
                                            out.println(SearchLogUtils.independenceFactMsg(x, y, condSet, test.getPValue()));
                                        }

                                        continue EDGE;
                                    }
                                }
                            }
                        }
                    }

                    return true;
                } else {
                    List<DepthTask> tasks = new ArrayList<DepthTask>();

                    final int mid = (to - from) / 2;

                    tasks.add(new DepthTask(chunk, from, from + mid));
                    tasks.add(new DepthTask(chunk, from + mid, to));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        pool.invoke(new DepthTask(chunk, 0, nodes.size()));

        if (verbose) {
            System.out.println("Done with depth");
        }

        return freeDegree(nodes, adjacencies) > depth;
    }

    private List<Node> possibleParents(Node x, List<Node> adjx,
                                       IKnowledge knowledge) {
        List<Node> possibleParents = new LinkedList<Node>();
        String _x = x.getName();

        for (Node z : adjx) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private boolean possibleParentOf(String z, String x, IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    public int getNumIndependenceTests() {
        return numIndependenceTests;
    }

    @Override
    public void setTrueGraph(Graph trueGraph) {

    }

    @Override
    public List<Node> getNodes() {
        return null;
    }

    @Override
    public List<Triple> getAmbiguousTriples(Node node) {
        return null;
    }

    public SepsetMap getSepsets() {
        return sepsets;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    /**
     * The logger, by default the empty logger.
     */
    public TetradLogger getLogger() {
        return logger;
    }

    public void setLogger(TetradLogger logger) {
        this.logger = logger;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public int getNumFalseDependenceJudgments() {
        return 0;
    }

    @Override
    public int getNumDependenceJudgments() {
        return 0;
    }

    public void setOut(PrintStream out) {
        if (out == null) throw new NullPointerException();
        this.out = out;
    }

    public PrintStream getOut() {
        return out;
    }
}


