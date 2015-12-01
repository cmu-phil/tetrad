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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally. Uses the Pc-Stable adjacency search.
 *
 * @author Joseph Ramsey (this version).
 */
public final class CpcStable implements GraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The maximum number of nodes conditioned on in the search.
     */
    private int depth = 1000;

    private Graph graph;

    /**
     * Elapsed time of last search.
     */
    private long elapsedTime;

    /**
     * The list of all unshielded triples.
     */
    private Set<Triple> allTriples;

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles = false;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * The sepsets.
     */
    private SepsetMap sepsets;

    /**
     * True iff orientation should be done.
     */
    private boolean doOrientation = true;

    /**
     * Whether verbose output about independencies is output.
     */
    private boolean verbose = false;
    private PrintStream out = System.out;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public CpcStable(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //==============================PUBLIC METHODS========================//

    /**
     * @return true just in case edges will not be added if they would create cycles.
     */
    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    /**
     * Sets to true just in case edges will not be added if they would create cycles.
     */
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multi-core systems.
     */
    public final void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0: " + depth);
        }

        if (depth == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Depth must not be Integer.MAX_VALUE, " +
                    "due to a known bug.");
        }

        this.depth = depth;
    }

    /**
     * @return the elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public final long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge specification used in the search. Non-null.
     */
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * @return the independence test used in the search, set in the constructor. This is not returning a copy, for fear
     * of duplicating the data set!
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    /**
     * @return the depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @return the set of all triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAllTriples() {
        return new HashSet<Triple>(allTriples);
    }

    public Set<Edge> getAdjacencies() {
        Set<Edge> adjacencies = new HashSet<Edge>();
        for (Edge edge : graph.getEdges()) {
            adjacencies.add(edge);
        }
        return adjacencies;
    }

    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(graph);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<Edge>(nonAdjacencies);
    }

    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     * See PC for caveats. The number of possible cycles and bidirected edges is far less with CPC than with PC.
     */
    public final Graph search() {
        return search(independenceTest.getVariables());
    }

    public Graph search(List<Node> nodes) {
        graph = new EdgeListGraphSingleConnections(nodes);


        final FasStableConcurrent fas = new FasStableConcurrent(getIndependenceTest());
        fas.setOut(out);
        return search(fas, nodes);
    }

    public Graph search(IFas fas, List<Node> nodes) {
        this.logger.log("info", "Starting CPC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");
        this.allTriples = new HashSet<Triple>();

//        this.logger.log("info", "Variables " + independenceTest.getVariables());

        long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }


        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(verbose);
        fas.setOut(out);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        graph = fas.search();
        sepsets = fas.getSepsets();

//        for (int i = 0; i < nodes.size(); i++) {
//            for (int j = i+1; j < nodes.size(); j++) {
//                List<Edge> edges = graph.getEdges(nodes.get(i), nodes.get(j));
//                if (edges.size() > 1) {
//                    graph.removeEdge(edges.get(0));
////                    out.println();
//                }
//            }
//        }

        if (isDoOrientation()) {
            if (verbose) {
                out.println("CPC orientation...");
            }

            SearchGraphUtils.pcOrientbk(knowledge, getGraph(), nodes);

//            orientUnshieldedTriplesConcurrent(knowledge, getIndependenceTest(), getDepth());
            orientUnshieldedTriples(knowledge, getIndependenceTest(), getDepth());

            System.out.println("Meek rules");

            MeekRules meekRules = new MeekRules();
            meekRules.setOut(out);

            meekRules.orientImplied(getGraph());

            System.out.println("Meek rules done");
        }

//        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + getGraph());

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;

        TetradLogger.getInstance().log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");

        TetradLogger.getInstance().flush();
        return getGraph();
    }

    //==========================PRIVATE METHODS===========================//

    private void orientUnshieldedTriples(IKnowledge knowledge,
                                         IndependenceTest test, int depth) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();

        for (Node y : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (this.graph.isAdjacentTo(x, z)) {
                    continue;
                }

                List<List<Node>> sepsetsxz = getSepsets(x, z, graph);

                if (isColliderSepset(x, y, z, graph, sepsetsxz)) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);

                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
                    }
                } else {
                    Triple triple = new Triple(x, y, z);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                }

                getAllTriples().add(new Triple(x, y, z));
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private List<List<Node>> getSepsets(Node i, Node k, Graph g) {
        List<Node> adji = g.getAdjacentNodes(i);
        List<Node> adjk = g.getAdjacentNodes(k);
        List<List<Node>> sepsets = new ArrayList<List<Node>>();

        for (int d = 0; d <= Math.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }

            if (adjk.size() >= 2 && d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adjk);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }
        }

        return sepsets;
    }


    private boolean isColliderSepset(Node i, Node j, Node k, Graph graph, List<List<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (List<Node> sepset : sepsets) {
            if (sepset.contains(j)) return false;
        }

        return true;
    }


    private void orientUnshieldedTriplesConcurrent(final IKnowledge knowledge,
                                                   final IndependenceTest test, final int depth) {
        ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        final List<Node> nodes = getGraph().getNodes();

        final int chunk = 50;

        class TripleTask extends RecursiveTask<Boolean> {
            private int chunk;
            private int from;
            private int to;

            public TripleTask(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        if ((i + 1) % 1000 == 0) System.out.println((i + 1) + " triple task");
                        final Node y = nodes.get(i);

                        List<Node> adjacentNodes = getGraph().getAdjacentNodes(y);

                        if (adjacentNodes.size() < 2) {
                            continue;
                        }

                        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
                        int[] combination;

                        while ((combination = cg.next()) != null) {
                            final Node x = adjacentNodes.get(combination[0]);
                            final Node z = adjacentNodes.get(combination[1]);

                            if (graph.isAdjacentTo(x, z)) {
                                continue;
                            }

                            SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(x, y, z, test, depth, getGraph(), verbose);

                            if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                                if (colliderAllowed(x, y, z, knowledge)) {
                                    getGraph().setEndpoint(x, y, Endpoint.ARROW);
                                    getGraph().setEndpoint(z, y, Endpoint.ARROW);

                                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
                                }
                            } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                                Triple triple = new Triple(x, y, z);
                                getGraph().addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                            }
                        }
                    }

                    return true;
                } else {
                    List<TripleTask> tasks = new ArrayList<TripleTask>();

                    final int mid1 = (this.to - from) / 4;
                    final int mid2 = mid1 + mid1;
                    final int mid3 = mid1 + mid1 + mid1;

                    tasks.add(new TripleTask(chunk, from, from + mid1));
                    tasks.add(new TripleTask(chunk, from + mid1, from + mid2));
                    tasks.add(new TripleTask(chunk, from + mid2, from + mid3));
                    tasks.add(new TripleTask(chunk, from + mid3, to));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        pool.invoke(new TripleTask(chunk, 0, nodes.size()));
        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return CpcStable.isArrowpointAllowed1(x, y, knowledge) &&
                CpcStable.isArrowpointAllowed1(z, y, knowledge);
    }

    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               IKnowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public SepsetMap getSepsets() {
        return sepsets;
    }

    public boolean isDoOrientation() {
        return doOrientation;
    }

    public void setDoOrientation(boolean doOrientation) {
        this.doOrientation = doOrientation;
    }

    /**
     * The graph that's constructed during the search.
     */
    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public PrintStream getOut() {
        return out;
    }
}


