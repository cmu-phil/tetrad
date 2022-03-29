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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implements the DCI (Distributed Causal Inference) algorithm for learning causal structure over a set of variable from
 * multiple datasets that each may only measure proper overlapping subsets subsets of that sets, or datasets with some
 * variables in common and others not. The algorithm currently takes as input a set of PAGs (presumably learned using a
 * local learning algorithm such as FCI) and returns a complete set of PAGs over every variable included a dataset that
 * are consistent with all of the PAGs (same d-separations and d-connections)
 *
 * @author Robert Tillman
 */

public class Dci {

    /**
     * The resulting class of graphs constructed using the datasets.
     */
    private final Set<Graph> output = new HashSet<>();

    /**
     * The independence tests for the datasets.
     */
    private final List<IndependenceTest> independenceTests = new ArrayList<>();

    /**
     * The variables in the datasets.
     */
    private final List<Node> variables = new ArrayList<>();

    /**
     * The sets of variables in each "marginal" dataset
     */
    private final List<Set<Node>> marginalVars = new ArrayList<>();

    /**
     * The SepsetMaps constructed for each dataset
     */
    private List<SepsetMapDci> sepsetMaps = new ArrayList<>();
    private List<SepsetMapDci> minimalSepsetMaps;

    /**
     * Search depth for the fast adjacency search
     */
    private int depth = 3;

    /**
     * All triples of nodes
     */
    private Set<Triple> allTriples;

    /**
     * Definite noncolliders from every dataset
     */
    private final Set<Triple> definiteNoncolliders = new HashSet<>();

    /**
     * Definite colliders from every dataset
     */
    private final Set<Triple> definiteColliders = new HashSet<>();

    /**
     * Graph change status and sets of graphs resulting from initial and final orientation rules
     */
    private boolean changeFlag = true;
    private final List<Graph> discrimGraphs = new ArrayList<>();
    private final List<Graph> currentDiscrimGraphs = new ArrayList<>();
    private final List<Graph> finalGraphs = new ArrayList<>();

    /**
     * Edge sequences ensuring treks
     */
    private Map<List<Node>, Map<Set<Edge>, Map<Triple, List<Set<Edge>>>>> necessaryTreks;
    private final List<Map<Set<Edge>, Map<Triple, List<Set<Edge>>>>> currentNecessaryTreks = new ArrayList<>();

    /**
     * Keeps up with getModel graph while searching for possible unshielded sets and
     */
    private Graph currentGraph;
    private Graph oldGraph;

    /**
     * Keeps up with getModel set of colliders being considered
     */
    private final Set<Triple> currentPossibleColliders = new HashSet<>();

    /**
     * Current set of nodePairs
     */
    private List<NodePair> currentNodePairs;
    /**
     * Current marginal set of variables
     */
    private Set<Node> currentMarginalSet;

    /**
     * The pooling method to use to resolve inconsitencies
     */
    private ResolveSepsets.Method method;

    /**
     * For multithreading
     */
    private int maxThreads = 1;
    private int totalThreads = 0;
    private int currentThread = 0;
    private final Lock lock = new ReentrantLock();
    private Map<Integer, Map<List<Node>, Set<Node>>> allPaths;


    /**
     * The total runtime of the last search
     */
    private long elapsedTime;

    /**
     * Max memory used during the last search
     */
    private double maxMemory;

    //=============================CONSTRUCTORS==========================//

    public Dci(final List<IndependenceTest> tests) {
        final Set<Node> variables = new HashSet<>();
        for (final IndependenceTest test : tests) {
            if (test == null) {
                throw new NullPointerException();
            }
            this.independenceTests.add(test);
            this.marginalVars.add(new HashSet<>(test.getVariables()));
            variables.addAll(test.getVariables());
        }
        this.variables.addAll(variables);
    }

    public Dci(final List<IndependenceTest> tests, final ResolveSepsets.Method method) {
        final Set<Node> variables = new HashSet<>();
        for (final IndependenceTest test : tests) {
            if (test == null) {
                throw new NullPointerException();
            }
            this.independenceTests.add(test);
            this.marginalVars.add(new HashSet<>(test.getVariables()));
            variables.addAll(test.getVariables());
        }
        this.variables.addAll(variables);
        System.out.println("Variables: " + variables);
        this.method = method;
    }

    //============================= Public Methods ============================//

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(final int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    // for multithreading

    public void setMaxThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("There must be at least 1 thread running");
        }
        this.maxThreads = threads;
    }

    public int getMaxThreads() {
        return this.maxThreads;
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Sets the pooling method used to resolve inconsistenceis (optional)
     */
    public void setPoolingMethod(final ResolveSepsets.Method method) {
        this.method = method;
    }

    /**
     * Gets the resulting sepsets
     */
    public List<SepsetMapDci> getSepset() {
        return this.sepsetMaps;
    }

    /**
     * Begins the DCI search procedure, described at each step
     */
    public List<Graph> search() {
        this.elapsedTime = System.currentTimeMillis();

        /*
         * Step 1 - Create the complete graph
         */
        final Graph graph = new EdgeListGraph(this.variables);
        graph.fullyConnect(Endpoint.CIRCLE);

        /*
         * Step 2 - Construct a new Fast Adjacency Search for each dataset
         *  to find definite nonadjacencies and sepsetMaps
         */
        // first find sepsetMaps
        System.out.println("Finding sepsets...");
        findSepsets(this.independenceTests);
        double currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        if (currentUsage > this.maxMemory) this.maxMemory = currentUsage;

        // now resolve inconsitencies resulting from independencies that follow
        // from combinations of independence statements that have already been
        // resolved
        if (this.method != null) {
            System.out.println("Resolving conflicting independence/dependence constraints");
            //resolveResultingIndependencies();
            //resolveResultingIndependenciesB();
            resolveResultingIndependenciesC();
        }
        // now remove edges
        removeNonadjacencies(graph, this.sepsetMaps);
        System.out.println("Removed edges");

        /*
         * Step 3 - Orient definite colliders using the sepsetMap and propagate
         * these orientations using the rules in propagateInitialOrientations
         */
        orientColliders(graph);
        System.out.println("Oriented ColliderDiscovery");
        propagateInitialOrientations(graph);
        System.out.println("Propagated initial orientations");
        System.out.println(graph);

        /*
         * Step 4 - Finds every graph skeleton for which there is some orienation
         * of the edges such that every d-connection in an input PAG is preserved.
         */
        getTriplesDefiniteColliders(graph);
        // find set of minmal spanning treks and for each trek, its ensuring undirectedPaths
        ensureMinimalSpanningTreks(graph);
        System.out.println("Found ways of ensuring minimal spanning treks");
        System.out.println("Paths ensuring Treks: \n" + this.necessaryTreks);
        // find all graph skeletons that ensure every trek
        final Map<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>> possibleSkeletons = findPossibleSkeletons(graph);
        currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        if (currentUsage > this.maxMemory) this.maxMemory = currentUsage;

        /*
         * Step 5 - For each possible skeleton from Step 4 finds every combination
         * of orienting unshield possible colliders and possible discriminating path
         * colliders which preserve every d-connection and add only those that
         * preserve every d-separation to the output set.
         */
        final Iterator<Set<Edge>> itr = possibleSkeletons.keySet().iterator();
        while (itr.hasNext()) {
            final Set<Edge> edgesToRemove = itr.next();
            final Set<Map<Triple, List<Set<Edge>>>> colliderSets = possibleSkeletons.get(edgesToRemove);
            final Graph newGraph = new EdgeListGraph(graph);
            newGraph.removeEdges(new ArrayList(edgesToRemove));
            this.oldGraph = newGraph;
            if (colliderSets.isEmpty()) {
                allColliderCombinations(newGraph, edgesToRemove, new HashSet<Triple>(), possibleSkeletons.size());
            } else {
                for (final Map<Triple, List<Set<Edge>>> colliderSet : colliderSets) {
                    for (final Graph newNewGraph : generateSkeletons(newGraph, colliderSet)) {
                        allColliderCombinations(newNewGraph, edgesToRemove, colliderSet.keySet(), possibleSkeletons.size());
                    }
                }
            }
            System.out.println("Current Size: " + this.output.size());
            currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            if (currentUsage > this.maxMemory) this.maxMemory = currentUsage;
            itr.remove();
        }

        /*
         * Step 6 - returns the output set of consistent graphs
         */
        this.elapsedTime = System.currentTimeMillis() - this.elapsedTime;
        System.out.println(this.output.size());
        return new ArrayList<>(this.output);
    }

    /**
     * @return maximum memory usage
     */
    public double getMaxMemUsage() {
        return this.maxMemory;
    }

    //============================= Private Methods ============================//

    private void findSepsets(final List<IndependenceTest> independenceTests) {
        for (int k = 0; k < this.marginalVars.size(); k++) {
            final IndependenceTest independenceTest = independenceTests.get(k);
            final FasDci adj;
            final Graph marginalGraph = new EdgeListGraph(new ArrayList<>(this.marginalVars.get(k)));
            marginalGraph.fullyConnect(Endpoint.CIRCLE);
            if (this.method != null) {
                adj = new FasDci(marginalGraph, independenceTest,
                        this.method, this.marginalVars, independenceTests, null, null);
            } else {
                adj = new FasDci(marginalGraph, independenceTest);
            }
            adj.setDepth(this.depth);
            this.sepsetMaps.add(adj.search());
        }
        // set minimalSepsetMaps to sepsetMaps if pooling is not beging used
        if (this.method == null) {
            this.minimalSepsetMaps = this.sepsetMaps;
        } else {
            this.minimalSepsetMaps = new ArrayList<>();
        }
    }

    /**
     * Removes edges between variables independent in some dataset
     */
    private void removeNonadjacencies(final Graph graph, final List<SepsetMapDci> sepsetMaps) {
        final List<Node> nodes = graph.getNodes();
        final ChoiceGenerator cg = new ChoiceGenerator(nodes.size(), 2);
        int[] combination;
        while ((combination = cg.next()) != null) {
            final Node x = graph.getNode(nodes.get(combination[0]).getName());
            final Node y = graph.getNode(nodes.get(combination[1]).getName());
            for (final SepsetMapDci sepset : sepsetMaps) {
                if (sepset.get(x, y) != null && graph.isAdjacentTo(x, y)) {
                    graph.removeEdge(x, y);
                    break;
                }
            }
        }
    }

    /**
     * Orients colliders using the sepsetMap
     */
    private void orientColliders(final Graph graph) {
        for (int k = 0; k < this.marginalVars.size(); k++) {
            final Set<Node> marginalSet = this.marginalVars.get(k);
            final SepsetMapDci sepset = this.sepsetMaps.get(k);
            for (final Node b : marginalSet) {
                final List<Node> adjacentNodes = new ArrayList<>();
                for (final Node node : graph.getAdjacentNodes(graph.getNode(b.getName()))) {
                    if (marginalSet.contains(node)) {
                        adjacentNodes.add(node);
                    }
                }
                if (adjacentNodes.size() < 2) {
                    continue;
                }
                final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
                int[] combination;
                while ((combination = cg.next()) != null) {
                    final Node a = adjacentNodes.get(combination[0]);
                    final Node c = adjacentNodes.get(combination[1]);
                    // Skip triples that are shielded.
                    if (sepset.get(a, c) == null) {
                        continue;
                    }
                    if (!sepset.get(a, c).contains(b)) {
                        if (!isArrowpointAllowed(graph, a, b)) {
                            continue;
                        }
                        if (!isArrowpointAllowed(graph, c, b)) {
                            continue;
                        }
                        System.out.println("Check here " + k + "\n " + sepset.get(a, c) +
                                "\n" + graph + "\n" + b);
                        graph.setEndpoint(a, graph.getNode(b.getName()), Endpoint.ARROW);
                        graph.setEndpoint(c, graph.getNode(b.getName()), Endpoint.ARROW);
                        //logger.colliderOrientations(SearchLogUtils.colliderOrientedMsg(a, b, c));
                    } else {
                        this.definiteNoncolliders.add(new Triple(a, b, c));
                    }
                }
            }
        }
    }

    /*
     * Propagates orientations that are consequences of the definite colliders
     * that are initially oriented
     */

    private void propagateInitialOrientations(final Graph graph) {
        while (this.changeFlag) {
            this.changeFlag = false;
            initialDoubleTriangle(graph);
            initialAwayFromColliderAncestorCycle(graph);
            initialDiscrimPaths(graph);
        }
        this.changeFlag = true;
    }

    /**
     * Implements the double-triangle orientation rule for the initial graph with only definite colliders from each
     * dataset oriented, which states that if D*-oB, A*->B<-*C and A*-*D*-*C is a noncollider, which A, B and C jointly
     * measured and A, D and C joinly measured, then D*->B.
     */
    private void initialDoubleTriangle(final Graph graph) {
        final List<Node> nodes = graph.getNodes();

        for (final Node B : nodes) {

            final List<Node> intoBArrows = graph.getNodesInTo(B, Endpoint.ARROW);
            final List<Node> intoBCircles = graph.getNodesInTo(B, Endpoint.CIRCLE);

            //possible A's and C's are those with arrows into B
            final List<Node> possA = new LinkedList<>(intoBArrows);
            final List<Node> possC = new LinkedList<>(intoBArrows);

            //possible D's are those with circles into B
            for (final Node D : intoBCircles) {
                for (final Node A : possA) {
                    for (final Node C : possC) {
                        if (C == A) {
                            continue;
                        }

                        //skip anything not a double triangle
                        if (!graph.isAdjacentTo(A, D) ||
                                !graph.isAdjacentTo(C, D)) {
                            continue;
                        }

                        //skip if A,D,C is a collider
                        if (graph.isDefCollider(A, D, C)) {
                            continue;
                        }

                        //skip if A,B,C and A,D,C not measured jointly
                        boolean checkABC = false;
                        final boolean checkADC = false;
                        for (final Set<Node> marginalSet : this.marginalVars) {
                            if (marginalSet.contains(A) && marginalSet.contains(B) &&
                                    marginalSet.contains(C)) {
                                checkABC = true;
                            }
                            if (marginalSet.contains(A) && marginalSet.contains(D) &&
                                    marginalSet.contains(C)) {
                                checkABC = true;
                            }
                        }
                        if (!checkABC || !checkADC) {
                            continue;
                        }

                        //if all of the previous tests pass, orient D*-oB as D*->B
                        if (!isArrowpointAllowed(graph, D, B)) {
                            continue;
                        }

                        graph.setEndpoint(D, B, Endpoint.ARROW);
                        this.changeFlag = true;
                    }
                }
            }
        }
    }

    private boolean isArrowpointAllowed(final Graph graph, final Node x, final Node y) {
        if (graph.getEndpoint(x, y) == Endpoint.ARROW) {
            return true;
        }

        if (graph.getEndpoint(x, y) == Endpoint.TAIL) {
            return false;
        }

        if (graph.getEndpoint(y, x) == Endpoint.ARROW &&
                graph.getEndpoint(x, y) == Endpoint.CIRCLE) {
            return true;
        }
        return true;
    }

    // Does all 3 of these rules at once instead of going through all
    // triples multiple times per iteration of doFinalOrientation.

    private void initialAwayFromColliderAncestorCycle(final Graph graph) {
        final List<Node> nodes = graph.getNodes();

        for (final Node B : nodes) {
            final List<Node> adj = graph.getAdjacentNodes(B);

            if (adj.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node A = adj.get(combination[0]);
                final Node C = adj.get(combination[1]);

                //choice gen doesnt do diff orders, so must switch A & C around.
                //only do awayFromCollider if A,B,C measured jointly
                boolean checkABC = false;
                for (final Set<Node> marginalSet : this.marginalVars) {
                    if (marginalSet.contains(A) && marginalSet.contains(B) &&
                            marginalSet.contains(C)) {
                        checkABC = true;
                        break;
                    }
                }
                if (checkABC) {
                    awayFromCollider(graph, A, B, C);
                    awayFromCollider(graph, C, B, A);
                }
                awayFromAncestor(graph, A, B, C);
                awayFromAncestor(graph, C, B, A);
                awayFromCycle(graph, A, B, C);
                awayFromCycle(graph, C, B, A);
            }
        }
    }

    // if a*->Bo-oC and not a*-*c, then a*->b-->c
    // (orient either circle if present, don't need both)

    private void awayFromCollider(final Graph graph, final Node a, final Node b, final Node c) {
        final Endpoint BC = graph.getEndpoint(b, c);
        final Endpoint CB = graph.getEndpoint(c, b);

        if (!(graph.isAdjacentTo(a, c)) &&
                (graph.getEndpoint(a, b) == Endpoint.ARROW)) {
            if (CB == Endpoint.CIRCLE || CB == Endpoint.TAIL) {
                if (BC == Endpoint.CIRCLE) {
                    if (!isArrowpointAllowed(graph, b, c)) {
                        return;
                    }

                    graph.setEndpoint(b, c, Endpoint.ARROW);
                    this.changeFlag = true;
                }
            }

            if (BC == Endpoint.CIRCLE || BC == Endpoint.ARROW) {
                if (CB == Endpoint.CIRCLE) {
                    graph.setEndpoint(c, b, Endpoint.TAIL);
                    this.changeFlag = true;
                }
            }
        }
    }

    //if a*-oC and either a-->b*->c or a*->b-->c, then a*->c

    private void awayFromAncestor(final Graph graph, final Node a, final Node b, final Node c) {
        if ((graph.isAdjacentTo(a, c)) &&
                (graph.getEndpoint(a, c) == Endpoint.CIRCLE)) {

            if ((graph.getEndpoint(a, b) == Endpoint.ARROW) &&
                    (graph.getEndpoint(b, c) == Endpoint.ARROW) && (
                    (graph.getEndpoint(b, a) == Endpoint.TAIL) ||
                            (graph.getEndpoint(c, b) == Endpoint.TAIL))) {

                if (!isArrowpointAllowed(graph, a, c)) {
                    return;
                }

                graph.setEndpoint(a, c, Endpoint.ARROW);
                this.changeFlag = true;
            }
        }
    }

    //if Ao->c and a-->b-->c, then a-->c

    private void awayFromCycle(final Graph graph, final Node a, final Node b, final Node c) {
        if ((graph.isAdjacentTo(a, c)) &&
                (graph.getEndpoint(a, c) == Endpoint.ARROW) &&
                (graph.getEndpoint(c, a) == Endpoint.CIRCLE)) {
            if (graph.isDirectedFromTo(a, b) && graph.isDirectedFromTo(b, c)) {
                graph.setEndpoint(c, a, Endpoint.TAIL);
                this.changeFlag = true;
            }
        }
    }

    /**
     * Finds the discriminating undirectedPaths relative only to variables measured jointly after the initial definite colliders
     * have been oriented.
     * <p>
     * The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     * the dots are a collider path from L to A with each node on the path (except L) a parent of C.
     * <pre>
     *          B
     *         xo           x is either an arrowhead or a circle
     *        /  \
     *       v    v
     * L....A --> C
     * </pre>
     */
    private void initialDiscrimPaths(final Graph graph) {
        final List<Node> nodes = graph.getNodes();

        for (final Node b : nodes) {

            //potential A and C candidate pairs are only those
            // that look like this:   A<-oBo->C  or  A<->Bo->C
            final List<Node> possAandC = graph.getNodesOutTo(b, Endpoint.ARROW);

            //keep arrows and circles
            final List<Node> possA = new LinkedList<>(possAandC);
            possA.removeAll(graph.getNodesInTo(b, Endpoint.TAIL));

            //keep only circles
            final List<Node> possC = new LinkedList<>(possAandC);
            possC.retainAll(graph.getNodesInTo(b, Endpoint.CIRCLE));

            for (final Node a : possA) {
                for (final Node c : possC) {
                    if (!graph.isParentOf(a, c)) {
                        continue;
                    }

                    // check only those jointly measured
                    boolean checkABC = false;
                    for (final Set<Node> marginalSet : this.marginalVars) {
                        if (marginalSet.contains(a) && marginalSet.contains(b) &&
                                marginalSet.contains(c)) {
                            checkABC = true;
                        }
                    }
                    if (!checkABC) {
                        continue;
                    }

                    final LinkedList<Node> reachable = new LinkedList<>();
                    reachable.add(a);
                    reachablePathFind(graph, a, b, c, reachable);
                }
            }
        }
    }

    /**
     * a method to search "back from a" to find a DDP. It is called with a reachability list (first consisting only of
     * a). This is breadth-first, utilizing "reachability" concept from Geiger, Verma, and Pearl 1990. </p> The body of
     * a DDP consists of colliders that are parents of c. This only considers discriminating undirectedPaths that are completely
     * jointly measured.
     */
    private void reachablePathFind(final Graph graph, final Node a, final Node b, final Node c,
                                   final LinkedList<Node> reachable) {
        final Set<Node> cParents = new HashSet<>(graph.getParents(c));

        // Needed to avoid cycles in failure case.
        final Set<Node> visited = new HashSet<>();
        visited.add(b);
        visited.add(c);

        // We don't want to include a,b,or c on the path, so they are added to
        // the "visited" set.  b and c are added explicitly here; a will be
        // added in the first while iteration.
        while (reachable.size() > 0) {
            final Node x = reachable.removeFirst();
            visited.add(x);

            // Possible DDP path endpoints.
            final List<Node> pathExtensions = graph.getNodesInTo(x, Endpoint.ARROW);
            pathExtensions.removeAll(visited);

            for (final Node l : pathExtensions) {

                // check only those jointly measured
                boolean checkABCL = false;
                for (final Set<Node> marginalSet : this.marginalVars) {
                    if (marginalSet.contains(a) && marginalSet.contains(b) &&
                            marginalSet.contains(c) && marginalSet.contains(l)) {
                        checkABCL = true;
                    }
                }
                if (!checkABCL) {
                    continue;
                }

                // If l is reachable and not adjacent to c, its a DDP
                // endpoint, so do DDP orientation. Otherwise, if l <-> c,
                // add l to the list of reachable nodes.
                if (!graph.isAdjacentTo(l, c)) {

                    // Check whether <a, b, c> should be reoriented given
                    // that l is not adjacent to c; if so, orient and stop.
                    doDdpOrientation(graph, l, a, b, c);
                    return;
                } else if (cParents.contains(l)) {
                    if (graph.getEndpoint(x, l) == Endpoint.ARROW) {
                        reachable.add(l);
                    }
                }
            }
        }
    }

    /**
     * Orients the edges inside the definte discriminating path triangle. Takes the left endpoint, and a,b,c as
     * arguments.
     */
    private void doDdpOrientation(final Graph graph, final Node l, final Node a, final Node b, final Node c) {
        final Set<Node> sepset = new HashSet<>();
        for (final SepsetMapDci msepset : this.sepsetMaps) {
            final List<Node> condSet = msepset.get(l, c);
            if (condSet != null) {
                sepset.addAll(condSet);
            }
        }

        if (sepset.contains(b)) {
            graph.setEndpoint(c, b, Endpoint.TAIL);
            this.changeFlag = true;
        } else {
            if (!isArrowpointAllowed(graph, a, b)) {
                return;
            }

            if (!isArrowpointAllowed(graph, c, b)) {
                return;
            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);
            this.changeFlag = true;
        }
    }

    /**
     * Finds a minimal spanning set of treks for the nodes in a particular "marginal" dataset
     */
    private Map<List<Node>, Set<Node>> minimalSpanningTreks(final Graph graph, final Set<Node> marginalNodes) {
        System.out.println("minspan\n" + graph + "\n" + marginalNodes);
        final int size = marginalNodes.size();
        System.out.println("Graph now\n" + graph);
        this.currentGraph = graph;
        this.currentMarginalSet = marginalNodes;
        this.allPaths = Collections.synchronizedMap(new HashMap<Integer, Map<List<Node>, Set<Node>>>());
        for (int k = 2; k <= size; k++) {
            this.allPaths.put(k, new HashMap<List<Node>, Set<Node>>());
        }
        this.currentNodePairs = allNodePairs(new ArrayList<>(marginalNodes));
        this.totalThreads = this.currentNodePairs.size();
        final List<FindMinimalSpanningTrek> threads = new ArrayList<>();
        for (int k = 0; k < this.maxThreads; k++) {
            threads.add(new FindMinimalSpanningTrek());
        }
        for (final FindMinimalSpanningTrek thread : threads) {
            if (thread.thisThread.isAlive()) {
                try {
                    thread.thisThread.join();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
        }
        this.currentThread = 0;
        System.out.println("allpaths \n" + this.allPaths);
        final Map<List<Node>, Set<Node>> minimalSpanningTreks = new HashMap<>();
        for (int k = 2; k < size; k++) {
            final Map<List<Node>, Set<Node>> smaller = this.allPaths.get(k);
            final Map<List<Node>, Set<Node>> larger = this.allPaths.get(k + 1);
            for (final List<Node> subpath : smaller.keySet()) {
                boolean isSubpath = false;
                for (final List<Node> path : larger.keySet()) {
                    if (isSubpath(path, subpath)) {
                        isSubpath = true;
                        break;
                    }
                }
                if (!isSubpath) {
                    minimalSpanningTreks.put(subpath, smaller.get(subpath));
                }
            }
            if (larger.isEmpty()) {
                break;
            } else if (k == size - 1) {
                minimalSpanningTreks.putAll(larger);
            }
        }
        if (size == 2) {
            minimalSpanningTreks.putAll(this.allPaths.get(2));
        }
        System.out.println("Found minimal spanning treks");
        System.out.println(minimalSpanningTreks);
        return minimalSpanningTreks;
    }

    private int nextThread() {
        this.lock.lock();
        this.currentThread++;
        this.lock.unlock();
        return this.currentThread;
    }

    private class FindMinimalSpanningTrek implements Runnable {

        Thread thisThread;

        FindMinimalSpanningTrek() {
            this.thisThread = new Thread(this);
            this.thisThread.start();
        }

        public void run() {
            while (Dci.this.currentThread < Dci.this.totalThreads) {
                final int threadNum = nextThread();
                if (threadNum >= Dci.this.totalThreads) {
                    break;
                }
                final int size = Dci.this.currentMarginalSet.size();
                final NodePair nodePair = Dci.this.currentNodePairs.get(threadNum);
                System.out.println("Finding minmal spanning treks... " + threadNum + " of " + Dci.this.totalThreads);
                if (Dci.this.currentGraph.isAdjacentTo(Dci.this.currentGraph.getNode(nodePair.getFirst().getName()), Dci.this.currentGraph.getNode(nodePair.getSecond().getName()))) {
                    final Set<Node> otherNodes = new HashSet<>(Dci.this.currentMarginalSet);
                    final List<Node> adjacency = new ArrayList<>();
                    adjacency.add(nodePair.getFirst());
                    adjacency.add(nodePair.getSecond());
                    otherNodes.removeAll(adjacency);
                    Dci.this.allPaths.get(2).put(adjacency, otherNodes);
                    continue;
                }
                final Map<Integer, Map<List<Node>, Set<Node>>> newPaths = new HashMap<>();
                for (int k = 2; k <= size; k++) {
                    newPaths.put(k, new HashMap<List<Node>, Set<Node>>());
                }
                for (final List<Node> trek : GraphUtils.treks(Dci.this.currentGraph, Dci.this.currentGraph.getNode(nodePair.getFirst().getName()), Dci.this.currentGraph.getNode(nodePair.getSecond().getName()), -1)) {
                    boolean inMarginal = true;
                    for (final Node node : trek) {
                        if (!Dci.this.currentMarginalSet.contains(node)) {
                            inMarginal = false;
                            break;
                        }
                    }
                    if (!inMarginal) {
                        continue;
                    }
                    final Set<Node> otherNodes = new HashSet<>(Dci.this.currentMarginalSet);
                    otherNodes.removeAll(trek);
//                  allPaths.get(trek.size()).put(trek, otherNodes);
                    newPaths.get(trek.size()).put(trek, otherNodes);
                }
                final List<List<Node>> remove = new ArrayList<>();
                for (int k = 2; k < size; k++) {
                    final Map<List<Node>, Set<Node>> trekMap = newPaths.get(k);
                    for (int l = k + 1; l <= size; l++) {
                        final Map<List<Node>, Set<Node>> trekMapNext = newPaths.get(l);
                        for (final List<Node> trek : trekMap.keySet()) {
                            for (final List<Node> trekNext : trekMapNext.keySet()) {
                                if (trekMap.get(trek).containsAll(trekMapNext.get(trekNext)) &&
                                        isSubtrek(trekNext, trek)) {
                                    remove.add(trekNext);
                                }
                            }
                        }
                        for (final List<Node> nodes : remove) {
                            trekMapNext.remove(nodes);
                        }
                        remove.clear();
                    }
                }
                for (int k = 2; k <= size; k++) {
                    Dci.this.allPaths.get(k).putAll(newPaths.get(k));
                }
            }
        }
    }

    /**
     * Determines whether one trek is a subtrek of another trek
     */
    public boolean isSubtrek(final List<Node> trek, final List<Node> subtrek) {
        int l = 0;
        for (int k = 0; k < subtrek.size(); k++) {
            while (!subtrek.get(k).equals(trek.get(l))) {
                l++;
                if (l >= trek.size()) {
                    return false;
                }
            }
            if (l >= trek.size()) {
                return false;
            }
            l++;
        }
        return true;
    }

    /**
     * Checks where one open path is a subpath of another open path
     */
    private boolean isSubpath(final List<Node> path, final List<Node> subpath) {
        boolean isSubpath = false;
        final int subpathLast = subpath.size() - 1;
        for (int k = 0; k < path.size() - subpathLast; k++) {
            if (path.get(k).equals(subpath.get(0))) {
                isSubpath = true;
                for (int m = 1; m < subpath.size(); m++) {
                    if (!path.get(k + m).equals(subpath.get(m))) {
                        isSubpath = false;
                        break;
                    }
                }
                if (isSubpath) {
                    break;
                }
            }
            if (path.get(k).equals(subpath.get(subpathLast))) {
                isSubpath = true;
                for (int m = subpathLast - 1; m >= 0; m--) {
                    if (!path.get(k + subpathLast - m).equals(subpath.get(m))) {
                        isSubpath = false;
                        break;
                    }
                }
                if (isSubpath) {
                    break;
                }
            }
        }
        return isSubpath;
    }

    /*
     * @return all triples in a graph
     */

    private Set<Triple> getAllTriples(final Graph graph) {
        final Set<Triple> triples = new HashSet<>();
        for (final Node node : graph.getNodes()) {
            final List<Node> adjNodes = graph.getAdjacentNodes(node);
            for (int i = 0; i < adjNodes.size() - 1; i++) {
                for (int j = i + 1; j < adjNodes.size(); j++) {
                    triples.add(new Triple(adjNodes.get(i), node, adjNodes.get(j)));
                }
            }
        }
        return triples;
    }

    /**
     * Initializes set of all triples of nodes and finds definite colliders
     */
    private void getTriplesDefiniteColliders(final Graph graph) {
        this.allTriples = getAllTriples(graph);
        for (final Triple triple : this.allTriples) {
            if (graph.isDefCollider(triple.getX(), triple.getY(), triple.getZ())) {
                this.definiteColliders.add(triple);
            }
        }
    }

    /**
     * Finds all edge sequences that ensure a particular trek. Returns the edge sequence with necessary colliders along
     * the sequence mapped to ancestral undirectedPaths
     */
    private static Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> pathsEnsuringTrek(final Graph graph, final List<Node> ensureTrek, final Set<Node> conditioning) {
        final Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> paths = new HashMap<>();
        pathsEnsuringTrek(graph, ensureTrek, 1, new LinkedList<>(Arrays.asList(ensureTrek.get(0))),
                conditioning, new HashMap<Triple, NodePair>(), paths, new HashSet<>(Arrays.asList(ensureTrek.get(0))));
        return paths;
    }

    private static void pathsEnsuringTrek(final Graph graph, final List<Node> ensureTrek, int index,
                                          final LinkedList<Node> path, final Set<Node> conditioning, final Map<Triple, NodePair> colliders,
                                          final Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> paths, final Set<Node> visited) {
        if (index == ensureTrek.size()) {
            final Map<Triple, List<Set<Edge>>> newColliders = new HashMap<>();
            for (final Triple triple : colliders.keySet()) {
                final List<Set<Edge>> edgeSet = new ArrayList<>();
                final List<List<Node>> treks = new ArrayList<>();
                treks.addAll(GraphUtils.treks(graph, triple.getY(), colliders.get(triple).getFirst(), -1));
                treks.addAll(GraphUtils.treks(graph, triple.getY(), colliders.get(triple).getSecond(), -1));
                for (final List<Node> trek : treks) {
                    /*             if (trek.get(1).equals(triple.getZ())) {
                                     continue;
                                 }
                    */
                    final Set<Edge> edges = new HashSet<>();
                    boolean okay = true;
                    for (int k = 0; k < trek.size() - 1; k++) {
                        if (graph.getEndpoint(trek.get(k + 1), trek.get(k)).equals(Endpoint.ARROW)) {
                            okay = false;
                            break;
                        }
                        edges.add(graph.getEdge(trek.get(k + 1), trek.get(k)));
                    }
                    if (okay) {
                        edgeSet.add(edges);
                    }
                }
                if (edgeSet.isEmpty()) {
                    return;
                }
                newColliders.put(triple, edgeSet);
            }
            final Set<Edge> edges = new HashSet<>();
            for (int k = 0; k < path.size() - 1; k++) {
                edges.add(graph.getEdge(path.get(k), path.get(k + 1)));
            }
            paths.put(edges, newColliders);
            return;
        }
        final Node node1 = path.getLast();
        final Node node2 = ensureTrek.get(index);

        for (final Edge edge : graph.getEdges(node1)) {
            final Node next = Edges.traverse(node1, edge);

            if (next == null) {
                continue;
            }

            if (path.size() > 1) {
                final Node node0 = path.get(path.size() - 2);
                if (!next.equals(node0) && graph.isDefCollider(node0, node1, next) && !conditioning.contains(node1)) {
                    continue;
                }
            }

            if (visited.contains(next)) {
                continue;
            }

            path.add(next);
            visited.add(next);

            if (conditioning.contains(node1)) {
                /*    if (next.equals(node2)) {
                        path.removeLast();
                        visited.remove(next);
                        continue;
                    }
                */
                final Node node0 = path.get(path.size() - 3);
                if (graph.getEndpoint(node0, node1).equals(Endpoint.TAIL) ||
                        graph.getEndpoint(next, node1).equals(Endpoint.TAIL)) {
                    path.removeLast();
                    visited.remove(next);
                    continue;
                }
                if (!graph.possibleAncestor(next, node2)) {
                    path.removeLast();
                    visited.remove(next);
                    continue;
                }
                colliders.put(new Triple(node0, node1, next), new NodePair(node2, ensureTrek.get(index - 1)));
            }
            final Set<Node> currentVisited;
            if (next.equals(node2)) {
                index++;
                currentVisited = new HashSet<>();
                currentVisited.add(node2);
            } else {
                currentVisited = visited;
            }
            pathsEnsuringTrek(graph, ensureTrek, index, path, conditioning, colliders, paths, currentVisited);
            path.removeLast();
            if (next == node2) {
                index--;
            }
            visited.remove(next);
            final List<Triple> remove = new ArrayList<>();
            for (final Triple triple : colliders.keySet()) {
                if (triple.getY().equals(node1)) {
                    remove.add(triple);
                }
            }
            for (final Triple triple : remove) {
                colliders.remove(triple);
            }
        }
    }


    /**
     * For each necessary trek in a marginal graph, finds the treks in the getModel graph that traverse each node in the
     * trek using only intermediary nodes not present in the marginal graph
     */
    private void ensureMinimalSpanningTreks(final Graph graph) {
        this.necessaryTreks = new HashMap<>();
        for (final Set<Node> marginalSet : this.marginalVars) {
            final Map<List<Node>, Set<Node>> minimalSpanningTreks = minimalSpanningTreks(graph, marginalSet);
            final Set<List<Node>> treks = minimalSpanningTreks.keySet();
            System.out.println(treks);
            int t = 1;
            for (final List<Node> trek : treks) {
                System.out.println("Finding ways to ensure minimal spanning treks... " + t + " of " + treks.size());
                this.necessaryTreks.put(trek, pathsEnsuringTrek(graph, trek, minimalSpanningTreks.get(trek)));
                t++;
            }
        }
    }

    /**
     * Generates a possible skeleton removing edges and orienting colliders
     */
    private List<Graph> generateSkeletons(final Graph graph, final Map<Triple, List<Set<Edge>>> colliderSet) {
        final Graph newGraph = new EdgeListGraph(graph);
        for (final Triple triple : colliderSet.keySet()) {
            newGraph.setEndpoint(triple.getX(), triple.getY(), Endpoint.ARROW);
            newGraph.setEndpoint(triple.getZ(), triple.getY(), Endpoint.ARROW);
        }
        final List<Graph> graphs = new ArrayList<>();
        graphs.add(newGraph);
        for (final Triple triple : colliderSet.keySet()) {
            generateSkeletons(graphs, triple, colliderSet.get(triple));
        }
        return graphs;
    }

    private void generateSkeletons(List<Graph> graphs, final Triple collider, final List<Set<Edge>> paths) {
        final List<Graph> newGraphs = new ArrayList<>();
        for (final Graph graph : graphs) {
            for (final Set<Edge> edges : paths) {
                final List<Node> path = getPathFromEdges(edges, collider.getY());
                final Graph newGraph = new EdgeListGraph(graph);
                for (int k = 0; k < path.size() - 1; k++) {
                    final Node node1 = path.get(k);
                    final Node node2 = path.get(k + 1);
                    newGraph.setEndpoint(node1, node2, Endpoint.ARROW);
                    newGraph.setEndpoint(node2, node1, Endpoint.TAIL);
                }
                newGraphs.add(newGraph);
            }
        }
        graphs = newGraphs;
    }

    private List<Node> getPathFromEdges(final Set<Edge> edges, final Node start) {
        final Graph newGraph = new EdgeListGraph();
        for (final Edge edge : edges) {
            newGraph.addNode(edge.getNode1());
            newGraph.addNode(edge.getNode2());
            newGraph.addEdge(edge);
        }
        final List<Node> path = new ArrayList<>();
        path.add(start);
        Node next = start;
        Node last = start;
        Node current = start;
        for (int k = 1; k < newGraph.getNumNodes(); k++) {
            final List<Edge> adjEdges = newGraph.getEdges(current);
            next = adjEdges.get(0).getDistalNode(current);
            if (next.equals(last)) {
                next = adjEdges.get(1).getDistalNode(current);
            }
            path.add(next);
            last = current;
            current = next;
        }
        return path;
    }

    /*
     * Finds all skeletons for which some orientation of the edges preserves
     * every d-connection present in an input graph using the skeletonSearch
     * procedure described below
     */

    private Map<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>> findPossibleSkeletons(final Graph graph) {
        final Set<Edge> allEdges = new HashSet<>(graph.getEdges());
        final Set<Edge> remove = new HashSet<>();

        for (final Edge edge : allEdges) {
            boolean necessary = false;
            for (final List<Node> necessaryTrek : this.necessaryTreks.keySet()) {
                necessary = true;
                final Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> possiblePaths = this.necessaryTreks.get(necessaryTrek);
                for (final Set<Edge> path : possiblePaths.keySet()) {
                    if (!path.contains(edge)) {
                        final Map<Triple, List<Set<Edge>>> tripleMap = possiblePaths.get(path);
                        if (tripleMap.isEmpty()) {
                            necessary = false;
                        }
                        for (final Triple triple : tripleMap.keySet()) {
                            necessary = true;
                            for (final Set<Edge> ancpath : tripleMap.get(triple)) {
                                if (!ancpath.contains(edge)) {
                                    necessary = false;
                                    break;
                                }
                            }
                            if (necessary) {
                                break;
                            }
                        }
                        if (!necessary) {
                            break;
                        }
                    }
                }
                if (necessary) {
                    remove.add(edge);
                    break;
                }
            }
        }
        allEdges.removeAll(remove);
        final Map<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>> possibleEdges = new HashMap<>();
        final PowerSet<Edge> pset = new PowerSet<>(allEdges);
        for (final Set<Edge> set : pset) {
            boolean possible = true;
            final Set<Map<Triple, List<Set<Edge>>>> colliderSets = new HashSet<>();
            for (final List<Node> necessaryTrek : this.necessaryTreks.keySet()) {
                final Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> possiblePaths = this.necessaryTreks.get(necessaryTrek);
                boolean okay = false;
                final Set<Map<Triple, List<Set<Edge>>>> necessaryColliders = new HashSet<>();
                for (final Set<Edge> path : possiblePaths.keySet()) {
                    boolean pathOkay = true;
                    for (final Edge edge : set) {
                        if (path.contains(edge)) {
                            pathOkay = false;
                            break;
                        }
                    }
                    if (pathOkay) {
                        final Map<Triple, List<Set<Edge>>> colliders = possiblePaths.get(path);
                        if (colliders.isEmpty()) {
                            okay = true;
                            necessaryColliders.clear();
                            break;
                        } else {
                            final Map<Triple, List<Set<Edge>>> newColliders = new HashMap<>();
                            for (final Triple triple : colliders.keySet()) {
                                final List<Set<Edge>> newEdges = new ArrayList<>();
                                for (final Set<Edge> edges : colliders.get(triple)) {
                                    pathOkay = true;
                                    for (final Edge edge : set) {
                                        if (path.contains(edge)) {
                                            pathOkay = false;
                                            break;
                                        }
                                    }
                                    if (pathOkay) {
                                        newEdges.add(edges);
                                    }
                                }
                                if (newEdges.isEmpty()) {
                                    break;
                                } else {
                                    newColliders.put(triple, newEdges);
                                }
                            }
                            if (colliders.size() == newColliders.size()) {
                                okay = true;
                                necessaryColliders.add(newColliders);
                            }
                        }
                    }
                }
                if (!okay) {
                    possible = false;
                    break;
                }
                if (!necessaryColliders.isEmpty()) {
                    colliderSets.addAll(necessaryColliders);
                }
            }
            if (possible) {
                possibleEdges.put(set, colliderSets);
            }
        }
        return possibleEdges;
    }

    /*
     * Tries to orient all collider/noncollider combination of triples accepting
     * only those that ensure all treks and preserve all d-separations
     */

    private void allColliderCombinations(final Graph graph, final Set<Edge> removedEdges, final Set<Triple> newColliders, final int skeletonsLeft) {
        this.currentGraph = graph;
        for (final List<Node> necessaryTrek : this.necessaryTreks.keySet()) {
            final Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> possiblePaths = this.necessaryTreks.get(necessaryTrek);
            final Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> newPossiblePaths = new HashMap<>();
            for (final Set<Edge> path : possiblePaths.keySet()) {
                boolean add = true;
                for (final Edge edgeRemoved : removedEdges) {
                    if (path.contains(edgeRemoved)) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    for (final Triple triple : newColliders) {
                        if (path.contains(this.oldGraph.getEdge(triple.getX(), triple.getY())) &&
                                path.contains(this.oldGraph.getEdge(triple.getZ(), triple.getY()))) {
                            if (!possiblePaths.get(path).containsKey(triple)) {
                                add = false;
                                break;
                            }
                        }
                    }
                }
                if (add) {
                    final Map<Triple, List<Set<Edge>>> colliderMap = possiblePaths.get(path);
                    final Map<Triple, List<Set<Edge>>> newColliderMap = new HashMap<>();
                    for (final Triple collider : colliderMap.keySet()) {
                        final List<Set<Edge>> pathSet = new ArrayList<>();
                        for (final Set<Edge> edges : colliderMap.get(collider)) {
                            add = true;
                            for (final Edge edgeRemoved : removedEdges) {
                                if (path.contains(edgeRemoved)) {
                                    add = false;
                                    break;
                                }
                            }
                            if (add) {
                                pathSet.add(edges);
                            }
                        }
                        if (pathSet.isEmpty()) {
                            add = false;
                            break;
                        }
                        newColliderMap.put(collider, pathSet);
                    }
                    if (add) {
                        newPossiblePaths.put(path, newColliderMap);
                    }
                }
            }
            this.currentNecessaryTreks.add(newPossiblePaths);
        }
        for (final Triple triple : this.allTriples) {
            if (!graph.isAdjacentTo(triple.getX(), triple.getZ()) && !this.definiteNoncolliders.contains(triple)
                    && !graph.isDefCollider(triple.getX(), triple.getY(), triple.getZ()) &&
                    graph.isAdjacentTo(triple.getX(), triple.getY()) &&
                    graph.isAdjacentTo(triple.getY(), triple.getZ()) &&
                    !graph.isUnderlineTriple(triple.getX(), triple.getY(), triple.getZ())) {
                this.currentPossibleColliders.add(triple);
            }
        }
        final Set<Triple> remove = new HashSet<>();
        for (final Triple triple : this.currentPossibleColliders) {
            boolean necessary = false;
            for (final Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> necessaryTrek : this.currentNecessaryTreks) {
                necessary = true;
                boolean size1 = false;
                if (necessaryTrek.size() == 1) {
                    size1 = true;
                }
                for (final Set<Edge> path : necessaryTrek.keySet()) {
                    if (!path.contains(this.oldGraph.getEdge(triple.getX(), triple.getY())) ||
                            !path.contains(this.oldGraph.getEdge(triple.getZ(), triple.getY())) ||
                            necessaryTrek.get(path).containsKey(triple)) {
                        necessary = false;
                        break;
                    }
                }
                if (!necessary && size1) {
                    for (final Map<Triple, List<Set<Edge>>> tripleMap : necessaryTrek.values()) {
                        for (final List<Set<Edge>> path : tripleMap.values()) {
                            if (path.size() == 1) {
                                if (path.get(0).contains(this.oldGraph.getEdge(triple.getX(), triple.getY())) &&
                                        path.get(0).contains(this.oldGraph.getEdge(triple.getZ(), triple.getY()))) {
                                    necessary = true;
                                    break;
                                }
                            }
                        }
                        if (necessary) {
                            break;
                        }
                    }
                }
                if (necessary) {
                    remove.add(triple);
                    break;
                }
            }
        }
        this.currentPossibleColliders.removeAll(remove);
        simpleColliderIterator(skeletonsLeft);
        this.currentPossibleColliders.clear();
        this.currentNecessaryTreks.clear();
    }

    private void simpleColliderIterator(final int skeletonsLeft) {
        final Set<Set<Triple>> necessaryEdges = new HashSet<>();
        final PowerSet<Triple> pset = new PowerSet<>(this.currentPossibleColliders);
        int psetsize = (int) java.lang.Math.pow(2, this.currentPossibleColliders.size());
        for (final Set<Triple> set : pset) {
            System.out.println("Searching Possible PAGs: " + psetsize + " (" + skeletonsLeft + " Skeletons Remaining)");
            psetsize--;
            boolean stop = false;
            for (final Set<Triple> necSet : necessaryEdges) {
                if (set.containsAll(necSet)) {
                    stop = true;
                    break;
                }
            }
            if (stop) {
                continue;
            }
            if (checkCollider(set, necessaryEdges)) {
                final Graph newGraph = new EdgeListGraph(this.currentGraph);
                for (final Triple triple : set) {
                    newGraph.setEndpoint(triple.getX(), triple.getY(), Endpoint.ARROW);
                    newGraph.setEndpoint(triple.getZ(), triple.getY(), Endpoint.ARROW);
                }
                doFinalOrientation(newGraph);
                for (final Graph graph : this.finalGraphs) {
                    if (!predictsFalseDependence(graph)) {
                        final Set<Triple> newColliders = new HashSet<>(this.allTriples);
                        newColliders.removeAll(this.definiteColliders);
                        newColliders.removeAll(set);
                        final Set<Triple> remove = new HashSet<>();
                        for (final Triple triple : newColliders) {
                            if (!graph.isDefCollider(triple.getX(), triple.getY(), triple.getZ())) {
                                remove.add(triple);
                            }
                        }
                        newColliders.removeAll(remove);
                        if (!newColliders.isEmpty()) {
                            newColliders.addAll(set);
                            if (!checkCollider(newColliders, necessaryEdges)) {
                                continue;
                            }
                        }
                        if (!graph.existsDirectedCycle()) {
                            graph.setUnderLineTriples(new HashSet<Triple>());
                            this.output.add(graph);
                        }
                    }
                }
            } else {
                necessaryEdges.add(set);
            }
        }
    }

    private boolean checkCollider(final Set<Triple> newSet, final Set<Set<Triple>> necessaryEdges) {
        boolean possible = true;
        for (final Set<Triple> necessarySet : necessaryEdges) {
            if (newSet.containsAll(necessarySet)) {
                possible = false;
                break;
            }
        }
        if (possible) {
            for (final Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> necessaryTrek : this.currentNecessaryTreks) {
                boolean okay = false;
                for (final Set<Edge> path : necessaryTrek.keySet()) {
                    boolean pathOkay = true;
                    for (final Triple triple : newSet) {
                        if (path.contains(this.currentGraph.getEdge(triple.getX(), triple.getY())) && path.contains(this.currentGraph.getEdge(triple.getY(), triple.getZ()))
                                && !necessaryTrek.get(path).containsKey(triple)) {
                            pathOkay = false;
                            break;
                        } else {
                            for (final Triple collider : necessaryTrek.get(path).keySet()) {
                                if (!this.currentGraph.isDefCollider(collider.getX(), collider.getY(), collider.getZ()) &&
                                        !newSet.contains(collider)) {
                                    pathOkay = false;
                                    break;
                                } else {
                                    pathOkay = false;
                                    for (final Set<Edge> adjPath : necessaryTrek.get(path).get(collider)) {
                                        if (adjPath.contains(this.oldGraph.getEdge(triple.getX(), triple.getY())) &&
                                                adjPath.contains(this.oldGraph.getEdge(triple.getZ(), triple.getY()))) {
                                            continue;
                                        } else if (adjPath.contains(this.oldGraph.getEdge(triple.getX(), triple.getY()))) {
                                            final List<Node> findPath = getPathFromEdges(adjPath, collider.getY());
                                            boolean adjPathOkay = true;
                                            for (int k = 0; k < findPath.size() - 1; k++) {
                                                if (findPath.get(k).equals(triple.getY()) &&
                                                        findPath.get(k + 1).equals(triple.getX())) {
                                                    adjPathOkay = false;
                                                    break;
                                                }
                                            }
                                            if (adjPathOkay) {
                                                pathOkay = true;
                                            }
                                        } else if (adjPath.contains(this.oldGraph.getEdge(triple.getZ(), triple.getY()))) {
                                            final List<Node> findPath = getPathFromEdges(adjPath, collider.getY());
                                            boolean adjPathOkay = true;
                                            for (int k = 0; k < findPath.size() - 1; k++) {
                                                if (findPath.get(k).equals(triple.getY()) &&
                                                        findPath.get(k + 1).equals(triple.getZ())) {
                                                    adjPathOkay = false;
                                                    break;
                                                }
                                            }
                                            if (adjPathOkay) {
                                                pathOkay = true;
                                            }
                                        } else {
                                            pathOkay = true;
                                        }
                                    }
                                }
                                if (!pathOkay) {
                                    break;
                                }
                            }
                        }
                        if (!pathOkay) {
                            break;
                        }
                    }
                    if (pathOkay) {
                        okay = true;
                        break;
                    }
                }
                if (!okay) {
                    possible = false;
                    break;
                }
            }
        }
        return possible;
    }

    /*
     * Does the final set of orientations after colliders have been oriented
     */

    private void doFinalOrientation(final Graph graph) {
        this.discrimGraphs.clear();
        this.finalGraphs.clear();
        this.currentDiscrimGraphs.add(graph);
        while (this.changeFlag) {
            this.changeFlag = false;
            this.currentDiscrimGraphs.addAll(this.discrimGraphs);
            this.discrimGraphs.clear();
            for (final Graph newGraph : this.currentDiscrimGraphs) {
                doubleTriangle(newGraph);
                awayFromColliderAncestorCycle(newGraph);
                if (!discrimPaths(newGraph)) {
                    if (this.changeFlag) {
                        this.discrimGraphs.add(newGraph);
                    } else {
                        this.finalGraphs.add(newGraph);
                    }
                }
            }
            this.currentDiscrimGraphs.clear();
        }
        this.changeFlag = true;
    }

    /**
     * Implements the double-triangle orientation rule, which states that if D*-oB, A*->B<-*C and A*-*D*-*C is a
     * noncollider, then D*->B.
     */
    private void doubleTriangle(final Graph graph) {
        final List<Node> nodes = graph.getNodes();

        for (final Node B : nodes) {

            final List<Node> intoBArrows = graph.getNodesInTo(B, Endpoint.ARROW);
            final List<Node> intoBCircles = graph.getNodesInTo(B, Endpoint.CIRCLE);

            //possible A's and C's are those with arrows into B
            final List<Node> possA = new LinkedList<>(intoBArrows);
            final List<Node> possC = new LinkedList<>(intoBArrows);

            //possible D's are those with circles into B
            for (final Node D : intoBCircles) {
                for (final Node A : possA) {
                    for (final Node C : possC) {
                        if (C == A) {
                            continue;
                        }

                        //skip anything not a double triangle
                        if (!graph.isAdjacentTo(A, D) ||
                                !graph.isAdjacentTo(C, D)) {
                            continue;
                        }

                        //skip if A,D,C is a collider
                        if (graph.isDefCollider(A, D, C)) {
                            continue;
                        }

                        //if all of the previous tests pass, orient D*-oB as D*->B
                        if (!isArrowpointAllowed(graph, D, B)) {
                            continue;
                        }

                        graph.setEndpoint(D, B, Endpoint.ARROW);
                        this.changeFlag = true;
                    }
                }
            }
        }
    }

    // Does only the ancestor and cycle rules of these repeatedly until no changes

    private void awayFromAncestorCycle(final Graph graph) {
        while (this.changeFlag) {
            this.changeFlag = false;
            final List<Node> nodes = graph.getNodes();

            for (final Node B : nodes) {
                final List<Node> adj = graph.getAdjacentNodes(B);

                if (adj.size() < 2) {
                    continue;
                }

                final ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    final Node A = adj.get(combination[0]);
                    final Node C = adj.get(combination[1]);

                    //choice gen doesnt do diff orders, so must switch A & C around.
                    awayFromAncestor(graph, A, B, C);
                    awayFromAncestor(graph, C, B, A);
                    awayFromCycle(graph, A, B, C);
                    awayFromCycle(graph, C, B, A);
                }
            }
        }
        this.changeFlag = true;
    }

    // Does all 3 of these rules at once instead of going through all
    // triples multiple times per iteration of doFinalOrientation.

    private void awayFromColliderAncestorCycle(final Graph graph) {
        final List<Node> nodes = graph.getNodes();

        for (final Node B : nodes) {
            final List<Node> adj = graph.getAdjacentNodes(B);

            if (adj.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node A = adj.get(combination[0]);
                final Node C = adj.get(combination[1]);

                //choice gen doesnt do diff orders, so must switch A & C around.
                awayFromCollider(graph, A, B, C);
                awayFromCollider(graph, C, B, A);
                awayFromAncestor(graph, A, B, C);
                awayFromAncestor(graph, C, B, A);
                awayFromCycle(graph, A, B, C);
                awayFromCycle(graph, C, B, A);
            }
        }
    }


    /**
     * The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     * the dots are a collider path from L to A with each node on the path (except L) a parent of C.
     * <pre>
     *          B
     *         xo           x is either an arrowhead or a circle
     *        /  \
     *       v    v
     * L....A --> C
     * </pre>
     */
    private boolean discrimPaths(final Graph graph) {
        final List<Node> nodes = graph.getNodes();

        for (final Node b : nodes) {

            //potential A and C candidate pairs are only those
            // that look like this:   A<-oBo->C  or  A<->Bo->C
            final List<Node> possAandC = graph.getNodesOutTo(b, Endpoint.ARROW);

            //keep arrows and circles
            final List<Node> possA = new LinkedList<>(possAandC);
            possA.removeAll(graph.getNodesInTo(b, Endpoint.TAIL));

            //keep only circles
            final List<Node> possC = new LinkedList<>(possAandC);
            possC.retainAll(graph.getNodesInTo(b, Endpoint.CIRCLE));

            for (final Node a : possA) {
                for (final Node c : possC) {
                    if (!graph.isParentOf(a, c)) {
                        continue;
                    }

                    final LinkedList<Node> reachable = new LinkedList<>();
                    reachable.add(a);
                    if (reachablePathFindOrient(graph, a, b, c, reachable)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * a method to search "back from a" to find a DDP. It is called with a reachability list (first consisting only of
     * a). This is breadth-first, utilizing "reachability" concept from Geiger, Verma, and Pearl 1990. </p> The body of
     * a DDP consists of colliders that are parents of c.
     */
    private boolean reachablePathFindOrient(final Graph graph, final Node a, final Node b, final Node c,
                                            final LinkedList<Node> reachable) {
        final Set<Node> cParents = new HashSet<>(graph.getParents(c));

        // Needed to avoid cycles in failure case.
        final Set<Node> visited = new HashSet<>();
        visited.add(b);
        visited.add(c);

        // We don't want to include a,b,or c on the path, so they are added to
        // the "visited" set.  b and c are added explicitly here; a will be
        // added in the first while iteration.
        while (reachable.size() > 0) {
            final Node x = reachable.removeFirst();
            visited.add(x);

            // Possible DDP path endpoints.
            final List<Node> pathExtensions = graph.getNodesInTo(x, Endpoint.ARROW);
            pathExtensions.removeAll(visited);

            for (final Node l : pathExtensions) {

                // If l is reachable and not adjacent to c, its a DDP
                // endpoint, so do DDP orientation. Otherwise, if l <-> c,
                // add l to the list of reachable nodes.
                if (!graph.isAdjacentTo(l, c)) {

                    // Check whether <a, b, c> should be reoriented given
                    // that l is not adjacent to c; if so, orient and stop.
                    doDdpOrientationFinal(graph, l, a, b, c);
                    return true;
                } else if (cParents.contains(l)) {
                    if (graph.getEndpoint(x, l) == Endpoint.ARROW) {
                        reachable.add(l);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Orients the edges inside the definte discriminating path triangle. Takes the left endpoint, and a,b,c as
     * arguments.
     */
    private void doDdpOrientationFinal(final Graph graph, final Node l, final Node a, final Node b, final Node c) {
        this.changeFlag = true;
        final List<Node> sepset = new ArrayList<>();
        for (final SepsetMapDci msepset : this.sepsetMaps) {
            final List<Node> condSet = msepset.get(l, c);
            if (condSet != null) {
                sepset.addAll(condSet);
            }
        }

        final Graph newGraph1 = new EdgeListGraph(graph);
        newGraph1.setEndpoint(c, b, Endpoint.TAIL);
        this.discrimGraphs.add(newGraph1);

        // only add collider graph if not known to be d-separated by some set
        // containing b
        if (sepset.contains(b)) {
            return;
        }

        final Graph newGraph2 = new EdgeListGraph(graph);
        if (!isArrowpointAllowed(graph, a, b)) {
            return;
        }

        if (!isArrowpointAllowed(graph, c, b)) {
            return;
        }

        newGraph2.setEndpoint(a, b, Endpoint.ARROW);
        newGraph2.setEndpoint(c, b, Endpoint.ARROW);
        this.discrimGraphs.add(newGraph2);
    }

    /**
     * Checks to make sure a graph entails d-separations relations from the sepsetMap
     */
    private boolean predictsFalseDependence(final Graph graph) {
        for (int k = 0; k < this.variables.size() - 1; k++) {
            final Node x = this.variables.get(k);
            for (int m = k + 1; m < this.variables.size(); m++) {
                final Node y = this.variables.get(m);
                for (final SepsetMapDci sepset : this.minimalSepsetMaps) {
                    if (sepset.get(x, y) == null) {
                        continue;
                    }
                    for (final List<Node> condSet : sepset.getSet(x, y)) {
                        if (!graph.isDSeparatedFrom(x, y, condSet)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Resolves possibly conflicting independence/dependence statements that result from a set of known independence
     * statements
     */
    private void resolveResultingIndependencies() {
        final List<SepsetMapDci> allSepsets = new ArrayList<>();
        final Pc fci = new Pc(new IndTestSepset(combineSepsets(this.sepsetMaps), this.variables));
        System.out.println("Starting pc...");
        final SepsetMapDci consSepset = new SepsetMapDci();
        doSepsetClosure(consSepset, fci.search());
        for (int k = 0; k < this.marginalVars.size(); k++) {
            final SepsetMapDci newSepset = new SepsetMapDci();
            final List<NodePair> pairs = allNodePairs(new ArrayList<>(this.marginalVars.get(k)));
            int p = 1;
            for (final NodePair pair : pairs) {
                final Node x = pair.getFirst();
                final Node y = pair.getSecond();
                if (consSepset.getSet(x, y) == null) {
                    continue;
                }
                int c = 1;
                final List<List<Node>> conds = consSepset.getSet(x, y);
                for (final List<Node> z : conds) {
                    System.out.println("Resolving inconsistencies... " + c + " of " + conds.size() + " (" + p + " of " + pairs.size() + " pairs and )" + (k + 1) + " of " + this.marginalVars.size() + " datasets)");
                    if (this.marginalVars.get(k).containsAll(z)) {
                        newSepset.set(x, y, z);
                    }
                    c++;
                }
                p++;
            }
            allSepsets.add(newSepset);
        }
        for (int k = 0; k < this.marginalVars.size(); k++) {
            final List<Node> variables = new ArrayList<>(this.marginalVars.get(k));
            final Graph newGraph = new EdgeListGraph(variables);
            newGraph.fullyConnect(Endpoint.CIRCLE);
            final FasDci fas = new FasDci(newGraph, new IndTestSepset(allSepsets.get(k), new ArrayList<>(this.marginalVars.get(k))));
            this.minimalSepsetMaps.add(fas.search());
        }
        this.sepsetMaps = allSepsets;
        System.out.println(this.sepsetMaps);
    }

    /**
     * Resolves possibly conflicting independence/dependence statements that result from a set of known independence
     * statements
     */
    private void resolveResultingIndependenciesB() {
        final SepsetMapDci combinedSepset = combineSepsets(this.sepsetMaps);
        final Pc pc = new Pc(new IndTestSepset(combinedSepset, this.variables));
        final Graph allInd = pc.search();
        System.out.println("PC finished...");
        final List<Node> overlap = new ArrayList<>(this.marginalVars.get(0));
        System.out.println(this.marginalVars.get(0).size());
        for (int k = 1; k < this.marginalVars.size(); k++) {
            System.out.println("Size: " + this.marginalVars.get(k).size());
            final Set<Node> marginal = this.marginalVars.get(k);
            final List<Node> remove = new ArrayList<>();
            for (final Node node : overlap) {
                if (!marginal.contains(node)) {
                    remove.add(node);
                }
            }
            overlap.removeAll(remove);
        }
        System.out.println("Overlap: " + overlap);
        System.out.println((overlap.size() / (double) this.variables.size()));
        final List<Graph> marginals = new ArrayList<>();
        for (int k = 0; k < this.marginalVars.size(); k++) {
            final Pc mpc = new Pc(this.independenceTests.get(k));
            marginals.add(mpc.search());
            System.out.println("PC finished " + (k + 1) + " of " + this.marginalVars.size());
        }
        final List<NodePair> pairs = allNodePairs(this.variables);
        //List<NodePair> pairs  = allNodePairs(overlap);
        int p = 1;
        for (final NodePair pair : pairs) {
            final Set<Node> condSet = new HashSet<>();
            condSet.addAll(allInd.getAdjacentNodes(pair.getFirst()));
            condSet.addAll(allInd.getAdjacentNodes(pair.getSecond()));
            for (final Graph graph : marginals) {
                try {
                    for (final Node node : graph.getAdjacentNodes(graph.getNode(pair.getFirst().getName()))) {
                        final Node newNode = allInd.getNode(node.getName());
                        //    if (overlap.contains(newNode)) {
                        condSet.add(newNode);
                        //    }
                    }
                } catch (final Exception e) {

                }
                try {
                    for (final Node node : graph.getAdjacentNodes(graph.getNode(pair.getSecond().getName()))) {
                        final Node newNode = allInd.getNode(node.getName());
                        //    if (overlap.contains(newNode)) {
                        condSet.add(newNode);
                        //    }
                    }
                } catch (final Exception e) {

                }
            }
/*            condSet.remove(pair.getFirst());
            condSet.remove(pair.getSecond());
            List<Node> remove = new ArrayList<Node>();
            for (Node node : condSet) {
                boolean include = false;
                for (Graph graph : marginals) {
                    if (graph.isAdjacentTo(node, pair.getFirst())||
                            graph.isAdjacentTo(node, pair.getSecond())) {
                        include = true;
                        break;
                    }
                }
                if (!include) {
                    remove.add(node);
                }
            }
*/
//            condSet.remove(remove);
            int c = 1;
            final int cs = (int) Math.pow(2, condSet.size());
            for (final Set<Node> set : new PowerSet<>(condSet)) {
                System.out.println("Resolving inconsistencies... " + c + " of " + cs + " (" + p + " of " + pairs.size() + " pairs)");
                c++;
                final List<Node> z = new ArrayList<>(set);
                if (allInd.isDConnectedTo(pair.getFirst(), pair.getSecond(), z)) {
                    continue;
                }
                combinedSepset.set(pair.getFirst(), pair.getSecond(), new ArrayList<>(set));

            }
            p++;
        }
        this.sepsetMaps.clear();
        for (int k = 0; k < this.marginalVars.size(); k++) {
            final SepsetMapDci newSepset = new SepsetMapDci();
            final List<NodePair> pairs2 = allNodePairs(new ArrayList<>(this.marginalVars.get(k)));
            for (final NodePair pair : pairs2) {
                final Node x = pair.getFirst();
                final Node y = pair.getSecond();
                if (combinedSepset.getSet(x, y) == null) {
                    continue;
                }
                final List<List<Node>> conds = combinedSepset.getSet(x, y);
                for (final List<Node> z : conds) {
                    if (this.marginalVars.get(k).containsAll(z)) {
                        newSepset.set(x, y, z);
                    }
                }
            }
            //sepsetMaps.add(newSepset);
            this.sepsetMaps.add(newSepset);
            final List<Node> variables = new ArrayList<>(this.marginalVars.get(k));
            final Graph newGraph = new EdgeListGraph(variables);
            newGraph.fullyConnect(Endpoint.CIRCLE);
            final FasDci fas = new FasDci(newGraph, new IndTestSepset(newSepset, variables));
            this.minimalSepsetMaps.add(fas.search());
        }
    }

    /**
     * Resolves possibly conflicting independence/dependence statements that result from a set of known independence
     * statements
     */
    private void resolveResultingIndependenciesC() {
        final List<SepsetMapDci> allSepsets = new ArrayList<>();
        final Pc fci = new Pc(new IndTestSepset(combineSepsets(this.sepsetMaps), this.variables));
        System.out.println("Starting pc...");
        final SepsetMapDci consSepset = new SepsetMapDci();
        final Graph fciResult = fci.search();
        final SepsetMap fciSepset = fci.getSepsets();
        for (int k = 0; k < this.marginalVars.size(); k++) {
            final SepsetMapDci newSepset = new SepsetMapDci(this.sepsetMaps.get(k));
            final List<NodePair> pairs = allNodePairs(new ArrayList<>(this.marginalVars.get(k)));
            final int p = 1;
            for (final NodePair pair : pairs) {
                final Node x = pair.getFirst();
                final Node y = pair.getSecond();
                if (fciSepset.get(x, y) == null) {
                    continue;
                }
                final List<Node> set = fciSepset.get(x, y);
                final List<Node> currentset = new ArrayList<>();
                if (newSepset.get(x, y) != null) {
                    currentset.addAll(newSepset.get(x, y));
                }
                final int c = 1;
                for (final Node node : set) {
                    System.out.println("Resolving inconsistencies... " + c + " of " + set.size() + " (" + p + " of " + pairs.size() + " pairs and )" + (k + 1) + " of " + this.marginalVars.size() + " datasets)");
                    if (currentset.contains(node)) {
                        continue;
                    }
                    final List<Node> possibleCond = new ArrayList<>(set);
                    possibleCond.remove(node);
                    final PowerSet<Node> pset = new PowerSet<>(possibleCond);
                    for (final Set<Node> inpset : pset) {
                        final List<Node> cond = new ArrayList<>(inpset);
                        cond.add(node);
                        if (fciResult.isDSeparatedFrom(x, y, cond)) {
                            newSepset.set(x, y, cond);
                        }
                    }
                }
            }
            allSepsets.add(newSepset);
        }
        this.sepsetMaps = allSepsets;
        System.out.println(this.sepsetMaps);
    }


    /**
     * Encodes every possible separation in a graph in the sepset
     */
    private void doSepsetClosure(final SepsetMapDci sepset, final Graph graph) {
        final List<Node> nodes = graph.getNodes();
        final List<NodePair> pairs = allNodePairs(nodes);
        int p = 1;
        for (final NodePair pair : pairs) {
            final List<Node> possibleNodes = new ArrayList<>(nodes);
            //ist<Node> possibleNodes = new ArrayList<Node>();
            final Node x = pair.getFirst();
            final Node y = pair.getSecond();
            possibleNodes.remove(x);
            possibleNodes.remove(y);
            possibleNodes.addAll(graph.getAdjacentNodes(x));
            possibleNodes.addAll(graph.getAdjacentNodes(y));
            int c = 1;
            final int ps = (int) Math.pow(2, possibleNodes.size());
            for (final Set<Node> condSet : new PowerSet<>(possibleNodes)) {
                System.out.println("Getting closure set... " + c + " of " + ps + "(" + p + " of " + pairs.size() + " remaining)");
                if (graph.isDSeparatedFrom(x, y, new ArrayList<>(condSet))) {
                    sepset.set(x, y, new ArrayList<>(condSet));
                }
                c++;
            }
            p++;
        }
    }

    /**
     * Combines independences from a set of set of sepsets into a single sepset
     */
    private SepsetMapDci combineSepsets(final List<SepsetMapDci> sepsets) {
        final SepsetMapDci allSepsets = new SepsetMapDci();
        for (final SepsetMapDci sepset : sepsets) {
            for (final Set<Node> pair : sepset.getSeparatedPairs()) {
                final Object[] pairArray = pair.toArray();
                final Node x = (Node) pairArray[0];
                final Node y = (Node) pairArray[1];
                for (final List<Node> condSet : sepset.getSet(x, y)) {
                    allSepsets.set(x, y, condSet);
                }
            }
        }
        return allSepsets;
    }

    /**
     * Generates NodePairs of all possible pairs of nodes from given list of nodes.
     */
    private List<NodePair> allNodePairs(final List<Node> nodes) {
        final List<NodePair> nodePairs = new ArrayList<>();
        for (int j = 0; j < nodes.size() - 1; j++) {
            for (int k = j + 1; k < nodes.size(); k++) {
                nodePairs.add(new NodePair(nodes.get(j), nodes.get(k)));
            }
        }
        return nodePairs;
    }

    /**
     * Constructs a powerset for an arbitrary collection
     */
    private class PowerSet<E> implements Iterable<Set<E>> {
        Collection<E> all;

        public PowerSet(final Collection<E> all) {
            this.all = all;
        }

        /**
         * @return an iterator over elements of type Collection<E> which enumerates the PowerSet of the collection used
         * in the constructor
         */

        public Iterator<Set<E>> iterator() {
            return new PowerSetIterator<>(this);
        }

        class PowerSetIterator<InE> implements Iterator<Set<InE>> {
            PowerSet<InE> powerSet;
            List<InE> canonicalOrder = new ArrayList<>();
            List<InE> mask = new ArrayList<>();
            boolean hasNext = true;

            PowerSetIterator(final PowerSet<InE> powerSet) {

                this.powerSet = powerSet;
                this.canonicalOrder.addAll(powerSet.all);
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private boolean allOnes() {
                for (final InE bit : this.mask) {
                    if (bit == null) {
                        return false;
                    }
                }
                return true;
            }

            private void increment() {
                int i = 0;
                while (true) {
                    if (i < this.mask.size()) {
                        final InE bit = this.mask.get(i);
                        if (bit == null) {
                            this.mask.set(i, this.canonicalOrder.get(i));
                            return;
                        } else {
                            this.mask.set(i, null);
                            i++;
                        }
                    } else {
                        this.mask.add(this.canonicalOrder.get(i));
                        return;
                    }
                }
            }

            public boolean hasNext() {
                return this.hasNext;
            }

            public Set<InE> next() {

                final Set<InE> result = new HashSet<>();
                result.addAll(this.mask);
                result.remove(null);

                this.hasNext = this.mask.size() < this.powerSet.all.size() || !allOnes();

                if (this.hasNext) {
                    increment();
                }

                return result;

            }

        }
    }
}



