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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.utils.ResolveSepsets;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import org.apache.commons.math3.util.FastMath;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implements the DCI (Distributed Causal Inference) algorithm for learning causal structure over a set of variable from
 * multiple datasets that each may only measure proper overlapping subsets of that sets, or datasets with some variables
 * in common and others not. The algorithm currently takes as input a set of PAGs (presumably learned using a local
 * learning algorithm such as FCI) and returns a complete set of PAGs over every variable included a dataset that are
 * consistent with all the PAGs (same d-separations and d-connections)
 *
 * @author Robert Tillman
 * @version $Id: $Id
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
     * Definite noncolliders from every dataset
     */
    private final Set<Triple> definiteNoncolliders = new HashSet<>();
    /**
     * Definite colliders from every dataset
     */
    private final Set<Triple> definiteColliders = new HashSet<>();
    private final List<Graph> discrimGraphs = new ArrayList<>();
    private final List<Graph> currentDiscrimGraphs = new ArrayList<>();
    private final List<Graph> finalGraphs = new ArrayList<>();
    private final List<Map<Set<Edge>, Map<Triple, List<Set<Edge>>>>> currentNecessaryTreks = new ArrayList<>();
    /**
     * Keeps up with getModel set of colliders being considered
     */
    private final Set<Triple> currentPossibleColliders = new HashSet<>();
    private final Lock lock = new ReentrantLock();
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
     * Graph change status and sets of graphs resulting from initial and final orientation rules
     */
    private boolean changeFlag = true;
    /**
     * Edge sequences ensuring treks
     */
    private Map<List<Node>, Map<Set<Edge>, Map<Triple, List<Set<Edge>>>>> necessaryTreks;
    /**
     * Keeps up with getModel graph while searching for possible unshielded sets and
     */
    private Graph currentGraph;
    private Graph oldGraph;
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
    private int totalThreads;
    private int currentThread;
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

    /**
     * <p>Constructor for Dci.</p>
     *
     * @param tests a {@link java.util.List} object
     */
    public Dci(List<IndependenceTest> tests) {
        Set<Node> variables = new HashSet<>();
        for (IndependenceTest test : tests) {
            if (test == null) {
                throw new NullPointerException();
            }
            independenceTests.add(test);
            marginalVars.add(new HashSet<>(test.getVariables()));
            variables.addAll(test.getVariables());
        }
        this.variables.addAll(variables);
    }

    /**
     * <p>Constructor for Dci.</p>
     *
     * @param tests  a {@link java.util.List} object
     * @param method a {@link edu.cmu.tetrad.search.utils.ResolveSepsets.Method} object
     */
    public Dci(List<IndependenceTest> tests, ResolveSepsets.Method method) {
        Set<Node> variables = new HashSet<>();
        for (IndependenceTest test : tests) {
            if (test == null) {
                throw new NullPointerException();
            }
            independenceTests.add(test);
            marginalVars.add(new HashSet<>(test.getVariables()));
            variables.addAll(test.getVariables());
        }
        this.variables.addAll(variables);
        System.out.println("Variables: " + variables);
        this.method = method;
    }

    //============================= Public Methods ============================//

    /**
     * Finds all edge sequences that ensure a particular trek. Returns the edge sequence with necessary colliders along
     * the sequence mapped to ancestral undirectedPaths
     */
    private static Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> pathsEnsuringTrek(Graph graph, List<Node> ensureTrek, Set<Node> conditioning) {
        Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> paths = new HashMap<>();
        Dci.pathsEnsuringTrek(graph, ensureTrek, 1, new LinkedList<>(Collections.singletonList(ensureTrek.get(0))),
                conditioning, new HashMap<>(), paths, new HashSet<>(Collections.singletonList(ensureTrek.get(0))));
        return paths;
    }

    private static void pathsEnsuringTrek(Graph graph, List<Node> ensureTrek, int index,
                                          LinkedList<Node> path, Set<Node> conditioning, Map<Triple, NodePair> colliders,
                                          Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> paths, Set<Node> visited) {
        if (index == ensureTrek.size()) {
            Map<Triple, List<Set<Edge>>> newColliders = new HashMap<>();
            for (Triple triple : colliders.keySet()) {
                List<Set<Edge>> edgeSet = new ArrayList<>();
                List<List<Node>> treks = new ArrayList<>();
                treks.addAll(graph.paths().treks(triple.getY(), colliders.get(triple).getFirst(), -1));
                treks.addAll(graph.paths().treks(triple.getY(), colliders.get(triple).getSecond(), -1));
                for (List<Node> trek : treks) {
                    Set<Edge> edges = new HashSet<>();
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
            Set<Edge> edges = new HashSet<>();
            for (int k = 0; k < path.size() - 1; k++) {
                edges.add(graph.getEdge(path.get(k), path.get(k + 1)));
            }
            paths.put(edges, newColliders);
            return;
        }
        Node node1 = path.getLast();
        Node node2 = ensureTrek.get(index);

        for (Edge edge : graph.getEdges(node1)) {
            Node next = Edges.traverse(node1, edge);

            if (next == null) {
                continue;
            }

            if (path.size() > 1) {
                Node node0 = path.get(path.size() - 2);
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
                Node node0 = path.get(path.size() - 3);
                if (graph.getEndpoint(node0, node1).equals(Endpoint.TAIL) ||
                    graph.getEndpoint(next, node1).equals(Endpoint.TAIL)) {
                    path.removeLast();
                    visited.remove(next);
                    continue;
                }
                if (!graph.paths().possibleAncestor(next, node2)) {
                    path.removeLast();
                    visited.remove(next);
                    continue;
                }
                colliders.put(new Triple(node0, node1, next), new NodePair(node2, ensureTrek.get(index - 1)));
            }
            Set<Node> currentVisited;
            if (next.equals(node2)) {
                index++;
                currentVisited = new HashSet<>();
                currentVisited.add(node2);
            } else {
                currentVisited = visited;
            }
            Dci.pathsEnsuringTrek(graph, ensureTrek, index, path, conditioning, colliders, paths, currentVisited);
            path.removeLast();
            if (next == node2) {
                index--;
            }
            visited.remove(next);
            List<Triple> remove = new ArrayList<>();
            for (Triple triple : colliders.keySet()) {
                if (triple.getY().equals(node1)) {
                    remove.add(triple);
                }
            }
            for (Triple triple : remove) {
                colliders.remove(triple);
            }
        }
    }

    /**
     * <p>Getter for the field <code>depth</code>.</p>
     *
     * @return a int
     */
    public int getDepth() {
        return depth;
    }

    /**
     * <p>Setter for the field <code>depth</code>.</p>
     *
     * @param depth a int
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    /**
     * <p>Getter for the field <code>elapsedTime</code>.</p>
     *
     * @return a long
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    //============================= Private Methods ============================//

    /**
     * Gets the resulting sepsets
     *
     * @return a {@link java.util.List} object
     */
    public List<SepsetMapDci> getSepset() {
        return this.sepsetMaps;
    }

    /**
     * Begins the DCI search procedure, described at each step
     *
     * @return a {@link java.util.List} object
     */
    public List<Graph> search() {
        this.elapsedTime = MillisecondTimes.timeMillis();

        /*
         * Step 1 - Create the complete graph
         */
        Graph graph = new EdgeListGraph(this.variables);
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
        Map<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>> possibleSkeletons = findPossibleSkeletons(graph);
        currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        if (currentUsage > this.maxMemory) this.maxMemory = currentUsage;

        /*
         * Step 5 - For each possible skeleton from Step 4 finds every combination
         * of orienting unshield possible colliders and possible discriminating path
         * colliders which preserve every d-connection and add only those that
         * preserve every d-separation to the output set.
         */
        Iterator<Set<Edge>> itr = possibleSkeletons.keySet().iterator();
        while (itr.hasNext()) {
            Set<Edge> edgesToRemove = itr.next();
            Set<Map<Triple, List<Set<Edge>>>> colliderSets = possibleSkeletons.get(edgesToRemove);
            Graph newGraph = new EdgeListGraph(graph);
            newGraph.removeEdges(new ArrayList<>(edgesToRemove));
            this.oldGraph = newGraph;
            if (colliderSets.isEmpty()) {
                allColliderCombinations(newGraph, edgesToRemove, new HashSet<>(), possibleSkeletons.size());
            } else {
                for (Map<Triple, List<Set<Edge>>> colliderSet : colliderSets) {
                    for (Graph newNewGraph : generateSkeletons(newGraph, colliderSet)) {
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
        this.elapsedTime = MillisecondTimes.timeMillis() - this.elapsedTime;
        System.out.println(this.output.size());
        return new ArrayList<>(this.output);
    }

    private void findSepsets(List<IndependenceTest> independenceTests) {
        for (int k = 0; k < this.marginalVars.size(); k++) {
            IndependenceTest independenceTest = independenceTests.get(k);
            FasDci adj;
            Graph marginalGraph = new EdgeListGraph(new ArrayList<>(this.marginalVars.get(k)));
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

    /*
     * Propagates orientations that are consequences of the definite colliders
     * that are initially oriented
     */

    /**
     * Removes edges between variables independent in some dataset
     */
    private void removeNonadjacencies(Graph graph, List<SepsetMapDci> sepsetMaps) {
        List<Node> nodes = graph.getNodes();
        ChoiceGenerator cg = new ChoiceGenerator(nodes.size(), 2);
        int[] combination;
        while ((combination = cg.next()) != null) {
            Node x = graph.getNode(nodes.get(combination[0]).getName());
            Node y = graph.getNode(nodes.get(combination[1]).getName());
            for (SepsetMapDci sepset : sepsetMaps) {
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
    private void orientColliders(Graph graph) {
        for (int k = 0; k < this.marginalVars.size(); k++) {
            Set<Node> marginalSet = this.marginalVars.get(k);
            SepsetMapDci sepset = this.sepsetMaps.get(k);
            for (Node b : marginalSet) {
                List<Node> adjacentNodes = new ArrayList<>();
                for (Node node : graph.getAdjacentNodes(graph.getNode(b.getName()))) {
                    if (marginalSet.contains(node)) {
                        adjacentNodes.add(node);
                    }
                }
                if (adjacentNodes.size() < 2) {
                    continue;
                }
                ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
                int[] combination;
                while ((combination = cg.next()) != null) {
                    Node a = adjacentNodes.get(combination[0]);
                    Node c = adjacentNodes.get(combination[1]);
                    // Skip triples that are shielded.
                    if (sepset.get(a, c) == null) {
                        continue;
                    }
                    if (!sepset.get(a, c).contains(b)) {
                        if (!isArrowheadAllowed(graph, a, b)) {
                            continue;
                        }
                        if (!isArrowheadAllowed(graph, c, b)) {
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

    private void propagateInitialOrientations(Graph graph) {
        while (this.changeFlag) {
            this.changeFlag = false;
            initialDoubleTriangle(graph);
            initialAwayFromColliderAncestorCycle(graph);
            initialDiscrimPaths(graph);
        }
        this.changeFlag = true;
    }

    // Does all 3 of these rules at once instead of going through all
    // triples multiple times per iteration of doFinalOrientation.

    /**
     * Implements the double-triangle orientation rule for the initial graph with only definite colliders from each
     * dataset oriented, which states that if D*-oB, A*-&gt;B&lt;-*C and A*-*D*-*C is a noncollider, which A, B and C
     * jointly measured and A, D and C joinly measured, then D*-&gt;B.
     */
    private void initialDoubleTriangle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {

            List<Node> intoBArrows = graph.getNodesInTo(B, Endpoint.ARROW);
            List<Node> intoBCircles = graph.getNodesInTo(B, Endpoint.CIRCLE);

            //possible A's and C's are those with arrows into B
            List<Node> possA = new LinkedList<>(intoBArrows);
            List<Node> possC = new LinkedList<>(intoBArrows);

            //possible D's are those with circles into B
            for (Node D : intoBCircles) {
                for (Node A : possA) {
                    for (Node C : possC) {
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
                        for (Set<Node> marginalSet : this.marginalVars) {
                            if (marginalSet.contains(A) && marginalSet.contains(B) &&
                                marginalSet.contains(C)) {
                                checkABC = true;
                            }
                            if (marginalSet.contains(A) && marginalSet.contains(D) &&
                                marginalSet.contains(C)) {
                                checkABC = true;
                            }
                        }
                        if (true) {
                            continue;
                        }

                        //if all of the previous tests pass, orient D*-oB as D*-&gt;B
                        if (!isArrowheadAllowed(graph, D, B)) {
                            continue;
                        }

                        graph.setEndpoint(D, B, Endpoint.ARROW);
                        this.changeFlag = true;
                    }
                }
            }
        }
    }

    // if a*-&gt;Bo-oC and not a*-*c, then a*-&gt;b-->c
    // (orient either circle if present, don't need both)

    private boolean isArrowheadAllowed(Graph graph, Node x, Node y) {
        if (graph.getEndpoint(x, y) == Endpoint.ARROW) {
            return true;
        }

        if (graph.getEndpoint(x, y) == Endpoint.TAIL) {
            return false;
        }

        if (graph.getEndpoint(y, x) == Endpoint.ARROW) {
            graph.getEndpoint(x, y);
        }
        return true;
    }

    //if a*-oC and either a-->b*-&gt;c or a*-&gt;b-->c, then a*-&gt;c

    private void initialAwayFromColliderAncestorCycle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {
            List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(B));

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node A = adj.get(combination[0]);
                Node C = adj.get(combination[1]);

                //choice gen doesnt do diff orders, so must switch A & C around.
                //only do awayFromCollider if A,B,C measured jointly
                boolean checkABC = false;
                for (Set<Node> marginalSet : this.marginalVars) {
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

    //if Ao->c and a-->b-->c, then a-->c

    private void awayFromCollider(Graph graph, Node a, Node b, Node c) {
        Endpoint BC = graph.getEndpoint(b, c);
        Endpoint CB = graph.getEndpoint(c, b);

        if (!(graph.isAdjacentTo(a, c)) &&
            (graph.getEndpoint(a, b) == Endpoint.ARROW)) {
            if (CB == Endpoint.CIRCLE || CB == Endpoint.TAIL) {
                if (BC == Endpoint.CIRCLE) {
                    if (!isArrowheadAllowed(graph, b, c)) {
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

    private void awayFromAncestor(Graph graph, Node a, Node b, Node c) {
        if ((graph.isAdjacentTo(a, c)) &&
            (graph.getEndpoint(a, c) == Endpoint.CIRCLE)) {

            if ((graph.getEndpoint(a, b) == Endpoint.ARROW) &&
                (graph.getEndpoint(b, c) == Endpoint.ARROW) && (
                        (graph.getEndpoint(b, a) == Endpoint.TAIL) ||
                        (graph.getEndpoint(c, b) == Endpoint.TAIL))) {

                if (!isArrowheadAllowed(graph, a, c)) {
                    return;
                }

                graph.setEndpoint(a, c, Endpoint.ARROW);
                this.changeFlag = true;
            }
        }
    }

    private void awayFromCycle(Graph graph, Node a, Node b, Node c) {
        if ((graph.isAdjacentTo(a, c)) &&
            (graph.getEndpoint(a, c) == Endpoint.ARROW) &&
            (graph.getEndpoint(c, a) == Endpoint.CIRCLE)) {
            if (graph.paths().isDirected(a, b) && graph.paths().isDirected(b, c)) {
                graph.setEndpoint(c, a, Endpoint.TAIL);
                this.changeFlag = true;
            }
        }
    }

    /**
     * Finds the discriminating undirectedPaths relative only to variables measured jointly after the initial definite
     * colliders have been oriented.
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
    private void initialDiscrimPaths(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {

            //potential A and C candidate pairs are only those
            // that look like this:   A&lt;-oBo->C  or  A&lt;-&gt;Bo->C
            List<Node> possAandC = graph.getNodesOutTo(b, Endpoint.ARROW);

            //keep arrows and circles
            List<Node> possA = new LinkedList<>(possAandC);
            possA.removeAll(graph.getNodesInTo(b, Endpoint.TAIL));

            //keep only circles
            List<Node> possC = new LinkedList<>(possAandC);
            possC.retainAll(graph.getNodesInTo(b, Endpoint.CIRCLE));

            for (Node a : possA) {
                for (Node c : possC) {
                    if (!graph.isParentOf(a, c)) {
                        continue;
                    }

                    // check only those jointly measured
                    boolean checkABC = false;
                    for (Set<Node> marginalSet : this.marginalVars) {
                        if (marginalSet.contains(a) && marginalSet.contains(b) &&
                            marginalSet.contains(c)) {
                            checkABC = true;
                            break;
                        }
                    }
                    if (!checkABC) {
                        continue;
                    }

                    LinkedList<Node> reachable = new LinkedList<>();
                    reachable.add(a);
                    reachablePathFind(graph, a, b, c, reachable);
                }
            }
        }
    }

    /**
     * a method to search "back from a" to find a DDP. It is called with a reachability list (first consisting only of
     * a). This is breadth-first, utilizing "reachability" concept from Geiger, Verma, and Pearl 1990. The body of a DDP
     * consists of colliders that are parents of c. This only considers discriminating undirectedPaths that are
     * completely jointly measured.
     */
    private void reachablePathFind(Graph graph, Node a, Node b, Node c,
                                   LinkedList<Node> reachable) {
        Set<Node> cParents = new HashSet<>(graph.getParents(c));

        // Needed to avoid cycles in failure case.
        Set<Node> visited = new HashSet<>();
        visited.add(b);
        visited.add(c);

        // We don't want to include a,b,or c on the path, so they are added to
        // the "visited" set.  b and c are added explicitly here; a will be
        // added in the first while iteration.
        while (reachable.size() > 0) {
            Node x = reachable.removeFirst();
            visited.add(x);

            // Possible DDP path endpoints.
            List<Node> pathExtensions = graph.getNodesInTo(x, Endpoint.ARROW);
            pathExtensions.removeAll(visited);

            for (Node l : pathExtensions) {

                // check only those jointly measured
                boolean checkABCL = false;
                for (Set<Node> marginalSet : this.marginalVars) {
                    if (marginalSet.contains(a) && marginalSet.contains(b) &&
                        marginalSet.contains(c) && marginalSet.contains(l)) {
                        checkABCL = true;
                        break;
                    }
                }
                if (!checkABCL) {
                    continue;
                }

                // If l is reachable and not adjacent to c, its a DDP
                // endpoint, so do DDP orientation. Otherwise, if l &lt;-&gt; c,
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
    private void doDdpOrientation(Graph graph, Node l, Node a, Node b, Node c) {
        Set<Node> sepset = new HashSet<>();
        for (SepsetMapDci msepset : this.sepsetMaps) {
            Set<Node> condSet = msepset.get(l, c);
            if (condSet != null) {
                sepset.addAll(condSet);
            }
        }

        if (sepset.contains(b)) {
            graph.setEndpoint(c, b, Endpoint.TAIL);
        } else {
            if (!isArrowheadAllowed(graph, a, b)) {
                return;
            }

            if (!isArrowheadAllowed(graph, c, b)) {
                return;
            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);
        }
        this.changeFlag = true;
    }

    /**
     * Finds a minimal spanning set of treks for the nodes in a particular "marginal" dataset
     */
    private Map<List<Node>, Set<Node>> minimalSpanningTreks(Graph graph, Set<Node> marginalNodes) {
        System.out.println("minspan\n" + graph + "\n" + marginalNodes);
        int size = marginalNodes.size();
        System.out.println("Graph now\n" + graph);
        this.currentGraph = graph;
        this.currentMarginalSet = marginalNodes;
        this.allPaths = Collections.synchronizedMap(new HashMap<>());
        for (int k = 2; k <= size; k++) {
            this.allPaths.put(k, new HashMap<>());
        }
        this.currentNodePairs = allNodePairs(new ArrayList<>(marginalNodes));
        this.totalThreads = this.currentNodePairs.size();
        List<FindMinimalSpanningTrek> threads = new ArrayList<>();
        /**
         * For multithreading
         */
        int maxThreads = 1;
        for (int k = 0; k < maxThreads; k++) {
            threads.add(new FindMinimalSpanningTrek());
        }
        for (FindMinimalSpanningTrek thread : threads) {
            if (thread.thisThread.isAlive()) {
                try {
                    thread.thisThread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
        }
        this.currentThread = 0;
        System.out.println("allpaths \n" + this.allPaths);
        Map<List<Node>, Set<Node>> minimalSpanningTreks = new HashMap<>();
        for (int k = 2; k < size; k++) {
            Map<List<Node>, Set<Node>> smaller = this.allPaths.get(k);
            Map<List<Node>, Set<Node>> larger = this.allPaths.get(k + 1);
            for (List<Node> subpath : smaller.keySet()) {
                boolean isSubpath = false;
                for (List<Node> path : larger.keySet()) {
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

    /**
     * Determines whether one trek is a subtrek of another trek
     *
     * @param trek    a {@link java.util.List} object
     * @param subtrek a {@link java.util.List} object
     * @return a boolean
     */
    public boolean isSubtrek(List<Node> trek, List<Node> subtrek) {
        int l = 0;
        for (Node node : subtrek) {
            while (!node.equals(trek.get(l))) {
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

    /*
     * @return all triples in a graph
     */

    /**
     * Checks where one open path is a subpath of another open path
     */
    private boolean isSubpath(List<Node> path, List<Node> subpath) {
        boolean isSubpath = false;
        int subpathLast = subpath.size() - 1;
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

    private Set<Triple> getAllTriples(Graph graph) {
        Set<Triple> triples = new HashSet<>();
        for (Node node : graph.getNodes()) {
            List<Node> adjNodes = new ArrayList<>(graph.getAdjacentNodes(node));
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
    private void getTriplesDefiniteColliders(Graph graph) {
        this.allTriples = getAllTriples(graph);
        for (Triple triple : this.allTriples) {
            if (graph.isDefCollider(triple.getX(), triple.getY(), triple.getZ())) {
                this.definiteColliders.add(triple);
            }
        }
    }

    /**
     * For each necessary trek in a marginal graph, finds the treks in the getModel graph that traverse each node in the
     * trek using only intermediary nodes not present in the marginal graph
     */
    private void ensureMinimalSpanningTreks(Graph graph) {
        this.necessaryTreks = new HashMap<>();
        for (Set<Node> marginalSet : this.marginalVars) {
            Map<List<Node>, Set<Node>> minimalSpanningTreks = minimalSpanningTreks(graph, marginalSet);
            Set<List<Node>> treks = minimalSpanningTreks.keySet();
            System.out.println(treks);
            int t = 1;
            for (List<Node> trek : treks) {
                System.out.println("Finding ways to ensure minimal spanning treks... " + t + " of " + treks.size());
                this.necessaryTreks.put(trek, Dci.pathsEnsuringTrek(graph, trek, minimalSpanningTreks.get(trek)));
                t++;
            }
        }
    }

    /**
     * Generates a possible skeleton removing edges and orienting colliders
     */
    private List<Graph> generateSkeletons(Graph graph, Map<Triple, List<Set<Edge>>> colliderSet) {
        Graph newGraph = new EdgeListGraph(graph);
        for (Triple triple : colliderSet.keySet()) {
            newGraph.setEndpoint(triple.getX(), triple.getY(), Endpoint.ARROW);
            newGraph.setEndpoint(triple.getZ(), triple.getY(), Endpoint.ARROW);
        }
        List<Graph> graphs = new ArrayList<>();
        graphs.add(newGraph);
        for (Triple triple : colliderSet.keySet()) {
            generateSkeletons(graphs, triple, colliderSet.get(triple));
        }
        return graphs;
    }

    private void generateSkeletons(List<Graph> graphs, Triple collider, List<Set<Edge>> paths) {
        List<Graph> newGraphs = new ArrayList<>();
        for (Graph graph : graphs) {
            for (Set<Edge> edges : paths) {
                List<Node> path = getPathFromEdges(edges, collider.getY());
                Graph newGraph = new EdgeListGraph(graph);
                for (int k = 0; k < path.size() - 1; k++) {
                    Node node1 = path.get(k);
                    Node node2 = path.get(k + 1);
                    newGraph.setEndpoint(node1, node2, Endpoint.ARROW);
                    newGraph.setEndpoint(node2, node1, Endpoint.TAIL);
                }
                newGraphs.add(newGraph);
            }
        }
        graphs = newGraphs;
    }

    private List<Node> getPathFromEdges(Set<Edge> edges, Node start) {
        Graph newGraph = new EdgeListGraph();
        for (Edge edge : edges) {
            newGraph.addNode(edge.getNode1());
            newGraph.addNode(edge.getNode2());
            newGraph.addEdge(edge);
        }
        List<Node> path = new ArrayList<>();
        path.add(start);
        Node next = start;
        Node last = start;
        Node current = start;
        for (int k = 1; k < newGraph.getNumNodes(); k++) {
            List<Edge> adjEdges = new ArrayList<>(newGraph.getEdges(current));
            next = adjEdges.get(0).getDistalNode(current);
            assert next != null;
            if (next.equals(last)) {
                next = adjEdges.get(1).getDistalNode(current);
            }
            path.add(next);
            last = current;
            current = next;
        }
        return path;
    }

    private Map<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>> findPossibleSkeletons(Graph graph) {
        Set<Edge> allEdges = new HashSet<>(graph.getEdges());
        Set<Edge> remove = new HashSet<>();

        for (Edge edge : allEdges) {
            boolean necessary = false;
            for (List<Node> necessaryTrek : this.necessaryTreks.keySet()) {
                necessary = true;
                Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> possiblePaths = this.necessaryTreks.get(necessaryTrek);
                for (Set<Edge> path : possiblePaths.keySet()) {
                    if (!path.contains(edge)) {
                        Map<Triple, List<Set<Edge>>> tripleMap = possiblePaths.get(path);
                        if (tripleMap.isEmpty()) {
                            necessary = false;
                        }
                        for (Triple triple : tripleMap.keySet()) {
                            necessary = true;
                            for (Set<Edge> ancpath : tripleMap.get(triple)) {
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
        Map<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>> possibleEdges = new HashMap<>();
        PowerSet<Edge> pset = new PowerSet<>(allEdges);
        for (Set<Edge> set : pset) {
            boolean possible = true;
            Set<Map<Triple, List<Set<Edge>>>> colliderSets = new HashSet<>();
            for (List<Node> necessaryTrek : this.necessaryTreks.keySet()) {
                Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> possiblePaths = this.necessaryTreks.get(necessaryTrek);
                boolean okay = false;
                Set<Map<Triple, List<Set<Edge>>>> necessaryColliders = new HashSet<>();
                for (Set<Edge> path : possiblePaths.keySet()) {
                    boolean pathOkay = true;
                    for (Edge edge : set) {
                        if (path.contains(edge)) {
                            pathOkay = false;
                            break;
                        }
                    }
                    if (pathOkay) {
                        Map<Triple, List<Set<Edge>>> colliders = possiblePaths.get(path);
                        if (colliders.isEmpty()) {
                            okay = true;
                            necessaryColliders.clear();
                            break;
                        } else {
                            Map<Triple, List<Set<Edge>>> newColliders = new HashMap<>();
                            for (Triple triple : colliders.keySet()) {
                                List<Set<Edge>> newEdges = new ArrayList<>();
                                for (Set<Edge> edges : colliders.get(triple)) {
                                    pathOkay = true;
                                    for (Edge edge : set) {
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
     * Finds all skeletons for which some orientation of the edges preserves
     * every d-connection present in an input graph using the skeletonSearch
     * procedure described below
     */

    private void allColliderCombinations(Graph graph, Set<Edge> removedEdges, Set<Triple> newColliders, int skeletonsLeft) {
        this.currentGraph = graph;
        for (List<Node> necessaryTrek : this.necessaryTreks.keySet()) {
            Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> possiblePaths = this.necessaryTreks.get(necessaryTrek);
            Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> newPossiblePaths = new HashMap<>();
            for (Set<Edge> path : possiblePaths.keySet()) {
                boolean add = true;
                for (Edge edgeRemoved : removedEdges) {
                    if (path.contains(edgeRemoved)) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    for (Triple triple : newColliders) {
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
                    Map<Triple, List<Set<Edge>>> colliderMap = possiblePaths.get(path);
                    Map<Triple, List<Set<Edge>>> newColliderMap = new HashMap<>();
                    for (Triple collider : colliderMap.keySet()) {
                        List<Set<Edge>> pathSet = new ArrayList<>();
                        for (Set<Edge> edges : colliderMap.get(collider)) {
                            add = true;
                            for (Edge edgeRemoved : removedEdges) {
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
        for (Triple triple : this.allTriples) {
            if (!graph.isAdjacentTo(triple.getX(), triple.getZ()) && !this.definiteNoncolliders.contains(triple)
                && !graph.isDefCollider(triple.getX(), triple.getY(), triple.getZ()) &&
                graph.isAdjacentTo(triple.getX(), triple.getY()) &&
                graph.isAdjacentTo(triple.getY(), triple.getZ()) &&
                !graph.isUnderlineTriple(triple.getX(), triple.getY(), triple.getZ())) {
                this.currentPossibleColliders.add(triple);
            }
        }
        Set<Triple> remove = new HashSet<>();
        for (Triple triple : this.currentPossibleColliders) {
            boolean necessary = false;
            for (Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> necessaryTrek : this.currentNecessaryTreks) {
                necessary = true;
                boolean size1 = necessaryTrek.size() == 1;
                for (Set<Edge> path : necessaryTrek.keySet()) {
                    if (!path.contains(this.oldGraph.getEdge(triple.getX(), triple.getY())) ||
                        !path.contains(this.oldGraph.getEdge(triple.getZ(), triple.getY())) ||
                        necessaryTrek.get(path).containsKey(triple)) {
                        necessary = false;
                        break;
                    }
                }
                if (!necessary && size1) {
                    for (Map<Triple, List<Set<Edge>>> tripleMap : necessaryTrek.values()) {
                        for (List<Set<Edge>> path : tripleMap.values()) {
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

    /*
     * Tries to orient all collider/noncollider combination of triples accepting
     * only those that ensure all treks and preserve all d-separations
     */

    private void simpleColliderIterator(int skeletonsLeft) {
        Set<Set<Triple>> necessaryEdges = new HashSet<>();
        PowerSet<Triple> pset = new PowerSet<>(this.currentPossibleColliders);
        int psetsize = (int) org.apache.commons.math3.util.FastMath.pow(2, this.currentPossibleColliders.size());
        for (Set<Triple> set : pset) {
            System.out.println("Searching Possible PAGs: " + psetsize + " (" + skeletonsLeft + " Skeletons Remaining)");
            psetsize--;
            boolean stop = false;
            for (Set<Triple> necSet : necessaryEdges) {
                if (set.containsAll(necSet)) {
                    stop = true;
                    break;
                }
            }
            if (stop) {
                continue;
            }
            if (checkCollider(set, necessaryEdges)) {
                Graph newGraph = new EdgeListGraph(this.currentGraph);
                for (Triple triple : set) {
                    newGraph.setEndpoint(triple.getX(), triple.getY(), Endpoint.ARROW);
                    newGraph.setEndpoint(triple.getZ(), triple.getY(), Endpoint.ARROW);
                }
                doFinalOrientation(newGraph);
                for (Graph graph : this.finalGraphs) {
                    if (!predictsFalseDependence(graph)) {
                        Set<Triple> newColliders = new HashSet<>(this.allTriples);
                        newColliders.removeAll(this.definiteColliders);
                        newColliders.removeAll(set);
                        Set<Triple> remove = new HashSet<>();
                        for (Triple triple : newColliders) {
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
                        if (!graph.paths().existsDirectedCycle()) {
                            graph.setUnderLineTriples(new HashSet<>());
                            this.output.add(graph);
                        }
                    }
                }
            } else {
                necessaryEdges.add(set);
            }
        }
    }

    private boolean checkCollider(Set<Triple> newSet, Set<Set<Triple>> necessaryEdges) {
        boolean possible = true;
        for (Set<Triple> necessarySet : necessaryEdges) {
            if (newSet.containsAll(necessarySet)) {
                possible = false;
                break;
            }
        }
        if (possible) {
            for (Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> necessaryTrek : this.currentNecessaryTreks) {
                boolean okay = false;
                for (Set<Edge> path : necessaryTrek.keySet()) {
                    boolean pathOkay = true;
                    for (Triple triple : newSet) {
                        if (path.contains(this.currentGraph.getEdge(triple.getX(), triple.getY())) && path.contains(this.currentGraph.getEdge(triple.getY(), triple.getZ()))
                            && !necessaryTrek.get(path).containsKey(triple)) {
                            pathOkay = false;
                            break;
                        } else {
                            for (Triple collider : necessaryTrek.get(path).keySet()) {
                                if (!this.currentGraph.isDefCollider(collider.getX(), collider.getY(), collider.getZ()) &&
                                    !newSet.contains(collider)) {
                                    pathOkay = false;
                                    break;
                                } else {
                                    pathOkay = false;
                                    for (Set<Edge> adjPath : necessaryTrek.get(path).get(collider)) {
                                        if (adjPath.contains(this.oldGraph.getEdge(triple.getX(), triple.getY())) &&
                                            adjPath.contains(this.oldGraph.getEdge(triple.getZ(), triple.getY()))) {
                                            continue;
                                        } else if (adjPath.contains(this.oldGraph.getEdge(triple.getX(), triple.getY()))) {
                                            List<Node> findPath = getPathFromEdges(adjPath, collider.getY());
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
                                            List<Node> findPath = getPathFromEdges(adjPath, collider.getY());
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

    private void doFinalOrientation(Graph graph) {
        this.discrimGraphs.clear();
        this.finalGraphs.clear();
        this.currentDiscrimGraphs.add(graph);
        while (this.changeFlag) {
            this.changeFlag = false;
            this.currentDiscrimGraphs.addAll(this.discrimGraphs);
            this.discrimGraphs.clear();
            for (Graph newGraph : this.currentDiscrimGraphs) {
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

    /*
     * Does the final set of orientations after colliders have been oriented
     */

    /**
     * Implements the double-triangle orientation rule, which states that if D*-oB, A*-&gt;B&lt;-*C and A*-*D*-*C is a
     * noncollider, then D*-&gt;B.
     */
    private void doubleTriangle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {

            List<Node> intoBArrows = graph.getNodesInTo(B, Endpoint.ARROW);
            List<Node> intoBCircles = graph.getNodesInTo(B, Endpoint.CIRCLE);

            //possible A's and C's are those with arrows into B
            List<Node> possA = new LinkedList<>(intoBArrows);
            List<Node> possC = new LinkedList<>(intoBArrows);

            //possible D's are those with circles into B
            for (Node D : intoBCircles) {
                for (Node A : possA) {
                    for (Node C : possC) {
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

                        //if all of the previous tests pass, orient D*-oB as D*-&gt;B
                        if (!isArrowheadAllowed(graph, D, B)) {
                            continue;
                        }

                        graph.setEndpoint(D, B, Endpoint.ARROW);
                        this.changeFlag = true;
                    }
                }
            }
        }
    }

    private void awayFromAncestorCycle(Graph graph) {
        while (this.changeFlag) {
            this.changeFlag = false;
            List<Node> nodes = graph.getNodes();

            for (Node B : nodes) {
                List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(B));

                if (adj.size() < 2) {
                    continue;
                }

                ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node A = adj.get(combination[0]);
                    Node C = adj.get(combination[1]);

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

    // Does only the ancestor and cycle rules of these repeatedly until no changes

    private void awayFromColliderAncestorCycle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {
            List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(B));

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node A = adj.get(combination[0]);
                Node C = adj.get(combination[1]);

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

    // Does all 3 of these rules at once instead of going through all
    // triples multiple times per iteration of doFinalOrientation.

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
    private boolean discrimPaths(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {

            //potential A and C candidate pairs are only those
            // that look like this:   A&lt;-oBo->C  or  A&lt;->Bo->C
            List<Node> possAandC = graph.getNodesOutTo(b, Endpoint.ARROW);

            //keep arrows and circles
            List<Node> possA = new LinkedList<>(possAandC);
            possA.removeAll(graph.getNodesInTo(b, Endpoint.TAIL));

            //keep only circles
            List<Node> possC = new LinkedList<>(possAandC);
            possC.retainAll(graph.getNodesInTo(b, Endpoint.CIRCLE));

            for (Node a : possA) {
                for (Node c : possC) {
                    if (!graph.isParentOf(a, c)) {
                        continue;
                    }

                    LinkedList<Node> reachable = new LinkedList<>();
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
     * a). This is breadth-first, utilizing "reachability" concept from Geiger, Verma, and Pearl 1990. The body of a DDP
     * consists of colliders that are parents of c.
     */
    private boolean reachablePathFindOrient(Graph graph, Node a, Node b, Node c,
                                            LinkedList<Node> reachable) {
        Set<Node> cParents = new HashSet<>(graph.getParents(c));

        // Needed to avoid cycles in failure case.
        Set<Node> visited = new HashSet<>();
        visited.add(b);
        visited.add(c);

        // We don't want to include a,b,or c on the path, so they are added to
        // the "visited" set.  b and c are added explicitly here; a will be
        // added in the first while iteration.
        while (reachable.size() > 0) {
            Node x = reachable.removeFirst();
            visited.add(x);

            // Possible DDP path endpoints.
            List<Node> pathExtensions = graph.getNodesInTo(x, Endpoint.ARROW);
            pathExtensions.removeAll(visited);

            for (Node l : pathExtensions) {

                // If l is reachable and not adjacent to c, its a DDP
                // endpoint, so do DDP orientation. Otherwise, if l &lt;-> c,
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
    private void doDdpOrientationFinal(Graph graph, Node l, Node a, Node b, Node c) {
        this.changeFlag = true;
        List<Node> sepset = new ArrayList<>();
        for (SepsetMapDci msepset : this.sepsetMaps) {
            Set<Node> condSet = msepset.get(l, c);
            if (condSet != null) {
                sepset.addAll(condSet);
            }
        }

        Graph newGraph1 = new EdgeListGraph(graph);
        newGraph1.setEndpoint(c, b, Endpoint.TAIL);
        this.discrimGraphs.add(newGraph1);

        // only add collider graph if not known to be d-separated by some set
        // containing b
        if (sepset.contains(b)) {
            return;
        }

        Graph newGraph2 = new EdgeListGraph(graph);
        if (!isArrowheadAllowed(graph, a, b)) {
            return;
        }

        if (!isArrowheadAllowed(graph, c, b)) {
            return;
        }

        newGraph2.setEndpoint(a, b, Endpoint.ARROW);
        newGraph2.setEndpoint(c, b, Endpoint.ARROW);
        this.discrimGraphs.add(newGraph2);
    }

    /**
     * Checks to make sure a graph entails d-separations relations from the sepsetMap
     */
    private boolean predictsFalseDependence(Graph graph) {
        for (int k = 0; k < this.variables.size() - 1; k++) {
            Node x = this.variables.get(k);
            for (int m = k + 1; m < this.variables.size(); m++) {
                Node y = this.variables.get(m);
                for (SepsetMapDci sepset : this.minimalSepsetMaps) {
                    if (sepset.get(x, y) == null) {
                        continue;
                    }
                    for (Set<Node> condSet : sepset.getSet(x, y)) {
                        if (!graph.paths().isMSeparatedFrom(x, y, condSet, false)) {
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
        List<SepsetMapDci> allSepsets = new ArrayList<>();
        Pc fci = new Pc(new IndTestSepsetDci(combineSepsets(this.sepsetMaps), this.variables));
        System.out.println("Starting pc...");
        SepsetMapDci consSepset = new SepsetMapDci();
        doSepsetClosure(consSepset, fci.search());
        for (int k = 0; k < this.marginalVars.size(); k++) {
            SepsetMapDci newSepset = new SepsetMapDci();
            List<NodePair> pairs = allNodePairs(new ArrayList<>(this.marginalVars.get(k)));
            int p = 1;
            for (NodePair pair : pairs) {
                Node x = pair.getFirst();
                Node y = pair.getSecond();
                if (consSepset.getSet(x, y) == null) {
                    continue;
                }
                int c = 1;
                Set<Set<Node>> conds = consSepset.getSet(x, y);
                for (Set<Node> z : conds) {
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
            List<Node> variables = new ArrayList<>(this.marginalVars.get(k));
            Graph newGraph = new EdgeListGraph(variables);
            newGraph.fullyConnect(Endpoint.CIRCLE);
            FasDci fas = new FasDci(newGraph, new IndTestSepsetDci(allSepsets.get(k), new ArrayList<>(this.marginalVars.get(k))));
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
        SepsetMapDci combinedSepset = combineSepsets(this.sepsetMaps);
        Pc pc = new Pc(new IndTestSepsetDci(combinedSepset, this.variables));
        Graph allInd = pc.search();
        System.out.println("PC finished...");
        List<Node> overlap = new ArrayList<>(this.marginalVars.get(0));
        System.out.println(this.marginalVars.get(0).size());
        for (int k = 1; k < this.marginalVars.size(); k++) {
            System.out.println("Size: " + this.marginalVars.get(k).size());
            Set<Node> marginal = this.marginalVars.get(k);
            List<Node> remove = new ArrayList<>();
            for (Node node : overlap) {
                if (!marginal.contains(node)) {
                    remove.add(node);
                }
            }
            overlap.removeAll(remove);
        }
        System.out.println("Overlap: " + overlap);
        System.out.println((overlap.size() / (double) this.variables.size()));
        List<Graph> marginals = new ArrayList<>();
        for (int k = 0; k < this.marginalVars.size(); k++) {
            Pc mpc = new Pc(this.independenceTests.get(k));
            marginals.add(mpc.search());
            System.out.println("PC finished " + (k + 1) + " of " + this.marginalVars.size());
        }
        List<NodePair> pairs = allNodePairs(this.variables);
        //List<NodePair> pairs  = allNodePairs(overlap);
        int p = 1;
        for (NodePair pair : pairs) {
            Set<Node> condSet = new HashSet<>();
            condSet.addAll(allInd.getAdjacentNodes(pair.getFirst()));
            condSet.addAll(allInd.getAdjacentNodes(pair.getSecond()));
            for (Graph graph : marginals) {
                try {
                    for (Node node : graph.getAdjacentNodes(graph.getNode(pair.getFirst().getName()))) {
                        Node newNode = allInd.getNode(node.getName());
                        //    if (overlap.contains(newNode)) {
                        condSet.add(newNode);
                        //    }
                    }
                } catch (Exception ignored) {

                }
                try {
                    for (Node node : graph.getAdjacentNodes(graph.getNode(pair.getSecond().getName()))) {
                        Node newNode = allInd.getNode(node.getName());
                        //    if (overlap.contains(newNode)) {
                        condSet.add(newNode);
                        //    }
                    }
                } catch (Exception ignored) {

                }
            }
//            condSet.remove(remove);
            int c = 1;
            int cs = (int) FastMath.pow(2, condSet.size());
            for (Set<Node> set : new PowerSet<>(condSet)) {
                System.out.println("Resolving inconsistencies... " + c + " of " + cs + " (" + p + " of " + pairs.size() + " pairs)");
                c++;
                Set<Node> z = new HashSet<>(set);
                if (allInd.paths().isMConnectedTo(pair.getFirst(), pair.getSecond(), z, false)) {
                    continue;
                }
                combinedSepset.set(pair.getFirst(), pair.getSecond(), new HashSet<>(set));

            }
            p++;
        }
        this.sepsetMaps.clear();
        for (Set<Node> marginalVar : this.marginalVars) {
            SepsetMapDci newSepset = new SepsetMapDci();
            List<NodePair> pairs2 = allNodePairs(new ArrayList<>(marginalVar));
            for (NodePair pair : pairs2) {
                Node x = pair.getFirst();
                Node y = pair.getSecond();
                if (combinedSepset.getSet(x, y) == null) {
                    continue;
                }
                Set<Set<Node>> conds = combinedSepset.getSet(x, y);
                for (Set<Node> z : conds) {
                    if (marginalVar.containsAll(z)) {
                        newSepset.set(x, y, z);
                    }
                }
            }
            //sepsetMaps.add(newSepset);
            this.sepsetMaps.add(newSepset);
            List<Node> variables = new ArrayList<>(marginalVar);
            Graph newGraph = new EdgeListGraph(variables);
            newGraph.fullyConnect(Endpoint.CIRCLE);
            FasDci fas = new FasDci(newGraph, new IndTestSepsetDci(newSepset, variables));
            this.minimalSepsetMaps.add(fas.search());
        }
    }

    /**
     * Resolves possibly conflicting independence/dependence statements that result from a set of known independence
     * statements
     */
    private void resolveResultingIndependenciesC() {
        List<SepsetMapDci> allSepsets = new ArrayList<>();
        Pc fci = new Pc(new IndTestSepsetDci(combineSepsets(this.sepsetMaps), this.variables));
        System.out.println("Starting pc...");
        SepsetMapDci consSepset = new SepsetMapDci();
        Graph fciResult = fci.search();
        SepsetMap fciSepset = fci.getSepsets();
        for (int k = 0; k < this.marginalVars.size(); k++) {
            SepsetMapDci newSepset = new SepsetMapDci(this.sepsetMaps.get(k));
            List<NodePair> pairs = allNodePairs(new ArrayList<>(this.marginalVars.get(k)));
            final int p = 1;
            for (NodePair pair : pairs) {
                Node x = pair.getFirst();
                Node y = pair.getSecond();
                if (fciSepset.get(x, y) == null) {
                    continue;
                }
                Set<Node> set = fciSepset.get(x, y);
                List<Node> currentset = new ArrayList<>();
                if (newSepset.get(x, y) != null) {
                    currentset.addAll(newSepset.get(x, y));
                }
                final int c = 1;
                for (Node node : set) {
                    System.out.println("Resolving inconsistencies... " + c + " of " + set.size() + " (" + p + " of " + pairs.size() + " pairs and )" + (k + 1) + " of " + this.marginalVars.size() + " datasets)");
                    if (currentset.contains(node)) {
                        continue;
                    }
                    List<Node> possibleCond = new ArrayList<>(set);
                    possibleCond.remove(node);
                    PowerSet<Node> pset = new PowerSet<>(possibleCond);
                    for (Set<Node> inpset : pset) {
                        Set<Node> cond = new HashSet<>(inpset);
                        cond.add(node);
                        if (fciResult.paths().isMSeparatedFrom(x, y, cond, false)) {
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
    private void doSepsetClosure(SepsetMapDci sepset, Graph graph) {
        List<Node> nodes = graph.getNodes();
        List<NodePair> pairs = allNodePairs(nodes);
        int p = 1;
        for (NodePair pair : pairs) {
            List<Node> possibleNodes = new ArrayList<>(nodes);
            //ist<Node> possibleNodes = new ArrayList<Node>();
            Node x = pair.getFirst();
            Node y = pair.getSecond();
            possibleNodes.remove(x);
            possibleNodes.remove(y);
            possibleNodes.addAll(graph.getAdjacentNodes(x));
            possibleNodes.addAll(graph.getAdjacentNodes(y));
            int c = 1;
            int ps = (int) FastMath.pow(2, possibleNodes.size());
            for (Set<Node> condSet : new PowerSet<>(possibleNodes)) {
                System.out.println("Getting closure set... " + c + " of " + ps + "(" + p + " of " + pairs.size() + " remaining)");
                if (graph.paths().isMSeparatedFrom(x, y, new HashSet<>(condSet), false)) {
                    sepset.set(x, y, new HashSet<>(condSet));
                }
                c++;
            }
            p++;
        }
    }

    /**
     * Combines independences from a set of set of sepsets into a single sepset
     */
    private SepsetMapDci combineSepsets(List<SepsetMapDci> sepsets) {
        SepsetMapDci allSepsets = new SepsetMapDci();
        for (SepsetMapDci sepset : sepsets) {
            for (Set<Node> pair : sepset.getSeparatedPairs()) {
                Object[] pairArray = pair.toArray();
                Node x = (Node) pairArray[0];
                Node y = (Node) pairArray[1];
                for (Set<Node> condSet : sepset.getSet(x, y)) {
                    allSepsets.set(x, y, condSet);
                }
            }
        }
        return allSepsets;
    }

    /**
     * Generates NodePairs of all possible pairs of nodes from given list of nodes.
     */
    private List<NodePair> allNodePairs(List<Node> nodes) {
        List<NodePair> nodePairs = new ArrayList<>();
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
    private static class PowerSet<E> implements Iterable<Set<E>> {
        Collection<E> all;

        public PowerSet(Collection<E> all) {
            this.all = all;
        }

        /**
         * @return an iterator over elements of type Collection<E> which enumerates the PowerSet of the collection used
         * in the constructor
         */

        public Iterator<Set<E>> iterator() {
            return new PowerSetIterator<>(this);
        }

        static class PowerSetIterator<InE> implements Iterator<Set<InE>> {
            PowerSet<InE> powerSet;
            List<InE> canonicalOrder = new ArrayList<>();
            List<InE> mask = new ArrayList<>();
            boolean hasNext = true;

            PowerSetIterator(PowerSet<InE> powerSet) {

                this.powerSet = powerSet;
                this.canonicalOrder.addAll(powerSet.all);
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private boolean allOnes() {
                for (InE bit : this.mask) {
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
                        InE bit = this.mask.get(i);
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

                Set<InE> result = new HashSet<>(this.mask);
                result.remove(null);

                this.hasNext = this.mask.size() < this.powerSet.all.size() || !allOnes();

                if (this.hasNext) {
                    increment();
                }

                return result;

            }

        }
    }

    private class FindMinimalSpanningTrek implements Runnable {

        Thread thisThread;

        FindMinimalSpanningTrek() {
            this.thisThread = new Thread(this);
            this.thisThread.start();
        }

        public void run() {
            while (Dci.this.currentThread < Dci.this.totalThreads) {
                int threadNum = nextThread();
                if (threadNum >= Dci.this.totalThreads) {
                    break;
                }
                int size = Dci.this.currentMarginalSet.size();
                NodePair nodePair = Dci.this.currentNodePairs.get(threadNum);
                System.out.println("Finding minmal spanning treks... " + threadNum + " of " + Dci.this.totalThreads);
                if (Dci.this.currentGraph.isAdjacentTo(Dci.this.currentGraph.getNode(nodePair.getFirst().getName()), Dci.this.currentGraph.getNode(nodePair.getSecond().getName()))) {
                    Set<Node> otherNodes = new HashSet<>(Dci.this.currentMarginalSet);
                    List<Node> adjacency = new ArrayList<>();
                    adjacency.add(nodePair.getFirst());
                    adjacency.add(nodePair.getSecond());
                    otherNodes.removeAll(adjacency);
                    Dci.this.allPaths.get(2).put(adjacency, otherNodes);
                    continue;
                }
                Map<Integer, Map<List<Node>, Set<Node>>> newPaths = new HashMap<>();
                for (int k = 2; k <= size; k++) {
                    newPaths.put(k, new HashMap<>());
                }
                for (List<Node> trek : Dci.this.currentGraph.paths().treks(Dci.this.currentGraph.getNode(nodePair.getFirst().getName()), Dci.this.currentGraph.getNode(nodePair.getSecond().getName()), -1)) {
                    boolean inMarginal = true;
                    for (Node node : trek) {
                        if (!Dci.this.currentMarginalSet.contains(node)) {
                            inMarginal = false;
                            break;
                        }
                    }
                    if (!inMarginal) {
                        continue;
                    }
                    Set<Node> otherNodes = new HashSet<>(Dci.this.currentMarginalSet);
                    trek.forEach(otherNodes::remove);
//                  allPaths.get(trek.size()).put(trek, otherNodes);
                    newPaths.get(trek.size()).put(trek, otherNodes);
                }
                List<List<Node>> remove = new ArrayList<>();
                for (int k = 2; k < size; k++) {
                    Map<List<Node>, Set<Node>> trekMap = newPaths.get(k);
                    for (int l = k + 1; l <= size; l++) {
                        Map<List<Node>, Set<Node>> trekMapNext = newPaths.get(l);
                        for (List<Node> trek : trekMap.keySet()) {
                            for (List<Node> trekNext : trekMapNext.keySet()) {
                                if (trekMap.get(trek).containsAll(trekMapNext.get(trekNext)) &&
                                    isSubtrek(trekNext, trek)) {
                                    remove.add(trekNext);
                                }
                            }
                        }
                        for (List<Node> nodes : remove) {
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
}



