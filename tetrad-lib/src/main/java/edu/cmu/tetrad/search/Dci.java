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
    private Set<Graph> output = new HashSet<Graph>();

    /**
     * The independence tests for the datasets.
     */
    private List<IndependenceTest> independenceTests = new ArrayList<IndependenceTest>();

    /**
     * The variables in the datasets.
     */
    private List<Node> variables = new ArrayList<Node>();

    /**
     * The sets of variables in each "marginal" dataset
     */
    private List<Set<Node>> marginalVars = new ArrayList<Set<Node>>();

    /**
     * The SepsetMaps constructed for each dataset
     */
    private List<SepsetMapDci> sepsetMaps = new ArrayList<SepsetMapDci>();
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
    private Set<Triple> definiteNoncolliders = new HashSet<Triple>();

    /**
     * Definite colliders from every dataset
     */
    private Set<Triple> definiteColliders = new HashSet<Triple>();

    /**
     * Graph change status and sets of graphs resulting from initial and final orientation rules
     */
    private boolean changeFlag = true;
    private List<Graph> discrimGraphs = new ArrayList<Graph>();
    private List<Graph> currentDiscrimGraphs = new ArrayList<Graph>();
    private List<Graph> finalGraphs = new ArrayList<Graph>();

    /**
     * Edge sequences ensuring treks
     */
    private Map<List<Node>, Map<Set<Edge>, Map<Triple, List<Set<Edge>>>>> necessaryTreks;
    private List<Map<Set<Edge>, Map<Triple, List<Set<Edge>>>>> currentNecessaryTreks = new ArrayList<Map<Set<Edge>, Map<Triple, List<Set<Edge>>>>>();

    /**
     * Keeps up with getModel graph while searching for possible unshielded sets and
     */
    private Graph currentGraph;
    private Graph oldGraph;

    /**
     * Keeps up with getModel set of colliders being considered
     */
    private Set<Triple> currentPossibleColliders = new HashSet<Triple>();

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
    private Lock lock = new ReentrantLock();
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

    public Dci(List<IndependenceTest> tests) {
        Set<Node> variables = new HashSet<Node>();
        for (IndependenceTest test : tests) {
            if (test == null) {
                throw new NullPointerException();
            }
            this.independenceTests.add(test);
            this.marginalVars.add(new HashSet<Node>(test.getVariables()));
            variables.addAll(test.getVariables());
        }
        this.variables.addAll(variables);
    }

    public Dci(List<IndependenceTest> tests, ResolveSepsets.Method method) {
        Set<Node> variables = new HashSet<Node>();
        for (IndependenceTest test : tests) {
            if (test == null) {
                throw new NullPointerException();
            }
            this.independenceTests.add(test);
            this.marginalVars.add(new HashSet<Node>(test.getVariables()));
            variables.addAll(test.getVariables());
        }
        this.variables.addAll(variables);
        System.out.println("Variables: " + variables);
        this.method = method;
    }

    //============================= Public Methods ============================//

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    // for multithreading

    public void setMaxThreads(int threads) {
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
    public void setPoolingMethod(ResolveSepsets.Method method) {
        this.method = method;
    }

    /**
     * Gets the resulting sepsets
     */
    public List<SepsetMapDci> getSepset() {
        return sepsetMaps;
    }

    /**
     * Begins the DCI search procedure, described at each step
     */
    public List<Graph> search() {
        elapsedTime = System.currentTimeMillis();

        /*
         * Step 1 - Create the complete graph
         */
        Graph graph = new EdgeListGraph(variables);
        graph.fullyConnect(Endpoint.CIRCLE);

        /*
         * Step 2 - Construct a new Fast Adjacency Search for each dataset
         *  to find definite nonadjacencies and sepsetMaps
         */
        // first find sepsetMaps
        System.out.println("Finding sepsets...");
        findSepsets(independenceTests);
        double currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        if (currentUsage > maxMemory) maxMemory = currentUsage;

        // now resolve inconsitencies resulting from independencies that follow
        // from combinations of independence statements that have already been
        // resolved
        if (method != null) {
            System.out.println("Resolving conflicting independence/dependence constraints");
            //resolveResultingIndependencies();
            //resolveResultingIndependenciesB();
            resolveResultingIndependenciesC();
        }
        // now remove edges
        removeNonadjacencies(graph, sepsetMaps);
        System.out.println("Removed edges");

        /*
         * Step 3 - Orient definite colliders using the sepsetMap and propagate
         * these orientations using the rules in propagateInitialOrientations
         */
        orientColliders(graph);
        System.out.println("Oriented Colliders");
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
        System.out.println("Paths ensuring Treks: \n" + necessaryTreks);
        // find all graph skeletons that ensure every trek
        Map<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>> possibleSkeletons = findPossibleSkeletons(graph);
        currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        if (currentUsage > maxMemory) maxMemory = currentUsage;

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
            newGraph.removeEdges(new ArrayList(edgesToRemove));
            oldGraph = newGraph;
            if (colliderSets.isEmpty()) {
                allColliderCombinations(newGraph, edgesToRemove, new HashSet<Triple>(), possibleSkeletons.size());
            } else {
                for (Map<Triple, List<Set<Edge>>> colliderSet : colliderSets) {
                    for (Graph newNewGraph : generateSkeletons(newGraph, colliderSet)) {
                        allColliderCombinations(newNewGraph, edgesToRemove, colliderSet.keySet(), possibleSkeletons.size());
                    }
                }
            }
            System.out.println("Current Size: " + output.size());
            currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            if (currentUsage > maxMemory) maxMemory = currentUsage;
            itr.remove();
        }

        /*
         * Step 6 - returns the output set of consistent graphs
         */
        elapsedTime = System.currentTimeMillis() - elapsedTime;
        System.out.println(output.size());
        return new ArrayList<Graph>(output);
    }

    /**
     * @return maximum memory usage
     */
    public double getMaxMemUsage() {
        return maxMemory;
    }

    //============================= Private Methods ============================//

    private void findSepsets(List<IndependenceTest> independenceTests) {
        for (int k = 0; k < marginalVars.size(); k++) {
            IndependenceTest independenceTest = independenceTests.get(k);
            FasDci adj;
            Graph marginalGraph = new EdgeListGraph(new ArrayList<Node>(marginalVars.get(k)));
            marginalGraph.fullyConnect(Endpoint.CIRCLE);
            if (method != null) {
                adj = new FasDci(marginalGraph, independenceTest,
                        method, marginalVars, independenceTests, null, null);
            } else {
                adj = new FasDci(marginalGraph, independenceTest);
            }
            adj.setDepth(depth);
            sepsetMaps.add(adj.search());
        }
        // set minimalSepsetMaps to sepsetMaps if pooling is not beging used
        if (method == null) {
            minimalSepsetMaps = sepsetMaps;
        } else {
            minimalSepsetMaps = new ArrayList<SepsetMapDci>();
        }
    }

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
        for (int k = 0; k < marginalVars.size(); k++) {
            Set<Node> marginalSet = marginalVars.get(k);
            SepsetMapDci sepset = sepsetMaps.get(k);
            for (Node b : marginalSet) {
                List<Node> adjacentNodes = new ArrayList<Node>();
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
                        definiteNoncolliders.add(new Triple(a, b, c));
                    }
                }
            }
        }
    }

    /*
     * Propagates orientations that are consequences of the definite colliders
     * that are initially oriented
     */

    private void propagateInitialOrientations(Graph graph) {
        while (changeFlag) {
            changeFlag = false;
            initialDoubleTriangle(graph);
            initialAwayFromColliderAncestorCycle(graph);
            initialDiscrimPaths(graph);
        }
        changeFlag = true;
    }

    /**
     * Implements the double-triangle orientation rule for the initial graph with only definite colliders from each
     * dataset oriented, which states that if D*-oB, A*->B<-*C and A*-*D*-*C is a noncollider, which A, B and C jointly
     * measured and A, D and C joinly measured, then D*->B.
     */
    private void initialDoubleTriangle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {

            List<Node> intoBArrows = graph.getNodesInTo(B, Endpoint.ARROW);
            List<Node> intoBCircles = graph.getNodesInTo(B, Endpoint.CIRCLE);

            //possible A's and C's are those with arrows into B
            List<Node> possA = new LinkedList<Node>(intoBArrows);
            List<Node> possC = new LinkedList<Node>(intoBArrows);

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
                        boolean checkADC = false;
                        for (Set<Node> marginalSet : marginalVars) {
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
                        changeFlag = true;
                    }
                }
            }
        }
    }

    private boolean isArrowpointAllowed(Graph graph, Node x, Node y) {
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

    private void initialAwayFromColliderAncestorCycle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {
            List<Node> adj = graph.getAdjacentNodes(B);

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
                for (Set<Node> marginalSet : marginalVars) {
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

    private void awayFromCollider(Graph graph, Node a, Node b, Node c) {
        Endpoint BC = graph.getEndpoint(b, c);
        Endpoint CB = graph.getEndpoint(c, b);

        if (!(graph.isAdjacentTo(a, c)) &&
                (graph.getEndpoint(a, b) == Endpoint.ARROW)) {
            if (CB == Endpoint.CIRCLE || CB == Endpoint.TAIL) {
                if (BC == Endpoint.CIRCLE) {
                    if (!isArrowpointAllowed(graph, b, c)) {
                        return;
                    }

                    graph.setEndpoint(b, c, Endpoint.ARROW);
                    changeFlag = true;
                }
            }

            if (BC == Endpoint.CIRCLE || BC == Endpoint.ARROW) {
                if (CB == Endpoint.CIRCLE) {
                    graph.setEndpoint(c, b, Endpoint.TAIL);
                    changeFlag = true;
                }
            }
        }
    }

    //if a*-oC and either a-->b*->c or a*->b-->c, then a*->c

    private void awayFromAncestor(Graph graph, Node a, Node b, Node c) {
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
                changeFlag = true;
            }
        }
    }

    //if Ao->c and a-->b-->c, then a-->c

    private void awayFromCycle(Graph graph, Node a, Node b, Node c) {
        if ((graph.isAdjacentTo(a, c)) &&
                (graph.getEndpoint(a, c) == Endpoint.ARROW) &&
                (graph.getEndpoint(c, a) == Endpoint.CIRCLE)) {
            if (graph.isDirectedFromTo(a, b) && graph.isDirectedFromTo(b, c)) {
                graph.setEndpoint(c, a, Endpoint.TAIL);
                changeFlag = true;
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
    private void initialDiscrimPaths(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {

            //potential A and C candidate pairs are only those
            // that look like this:   A<-oBo->C  or  A<->Bo->C
            List<Node> possAandC = graph.getNodesOutTo(b, Endpoint.ARROW);

            //keep arrows and circles
            List<Node> possA = new LinkedList<Node>(possAandC);
            possA.removeAll(graph.getNodesInTo(b, Endpoint.TAIL));

            //keep only circles
            List<Node> possC = new LinkedList<Node>(possAandC);
            possC.retainAll(graph.getNodesInTo(b, Endpoint.CIRCLE));

            for (Node a : possA) {
                for (Node c : possC) {
                    if (!graph.isParentOf(a, c)) {
                        continue;
                    }

                    // check only those jointly measured
                    boolean checkABC = false;
                    for (Set<Node> marginalSet : marginalVars) {
                        if (marginalSet.contains(a) && marginalSet.contains(b) &&
                                marginalSet.contains(c)) {
                            checkABC = true;
                        }
                    }
                    if (!checkABC) {
                        continue;
                    }

                    LinkedList<Node> reachable = new LinkedList<Node>();
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
    private void reachablePathFind(Graph graph, Node a, Node b, Node c,
                                   LinkedList<Node> reachable) {
        Set<Node> cParents = new HashSet<Node>(graph.getParents(c));

        // Needed to avoid cycles in failure case.
        Set<Node> visited = new HashSet<Node>();
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
                for (Set<Node> marginalSet : marginalVars) {
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
    private void doDdpOrientation(Graph graph, Node l, Node a, Node b, Node c) {
        Set<Node> sepset = new HashSet<Node>();
        for (SepsetMapDci msepset : sepsetMaps) {
            List<Node> condSet = msepset.get(l, c);
            if (condSet != null) {
                sepset.addAll(condSet);
            }
        }

        if (sepset.contains(b)) {
            graph.setEndpoint(c, b, Endpoint.TAIL);
            changeFlag = true;
        } else {
            if (!isArrowpointAllowed(graph, a, b)) {
                return;
            }

            if (!isArrowpointAllowed(graph, c, b)) {
                return;
            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);
            changeFlag = true;
        }
    }

    /**
     * Finds a minimal spanning set of treks for the nodes in a particular "marginal" dataset
     */
    private Map<List<Node>, Set<Node>> minimalSpanningTreks(Graph graph, Set<Node> marginalNodes) {
        System.out.println("minspan\n" + graph + "\n" + marginalNodes);
        int size = marginalNodes.size();
        System.out.println("Graph now\n" + graph);
        currentGraph = graph;
        currentMarginalSet = marginalNodes;
        allPaths = Collections.synchronizedMap(new HashMap<Integer, Map<List<Node>, Set<Node>>>());
        for (int k = 2; k <= size; k++) {
            allPaths.put(k, new HashMap<List<Node>, Set<Node>>());
        }
        currentNodePairs = allNodePairs(new ArrayList<Node>(marginalNodes));
        totalThreads = currentNodePairs.size();
        List<FindMinimalSpanningTrek> threads = new ArrayList<FindMinimalSpanningTrek>();
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
        currentThread = 0;
        System.out.println("allpaths \n" + allPaths);
        Map<List<Node>, Set<Node>> minimalSpanningTreks = new HashMap<List<Node>, Set<Node>>();
        for (int k = 2; k < size; k++) {
            Map<List<Node>, Set<Node>> smaller = allPaths.get(k);
            Map<List<Node>, Set<Node>> larger = allPaths.get(k + 1);
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
            minimalSpanningTreks.putAll(allPaths.get(2));
        }
        System.out.println("Found minimal spanning treks");
        System.out.println(minimalSpanningTreks);
        return minimalSpanningTreks;
    }

    private int nextThread() {
        lock.lock();
        currentThread++;
        lock.unlock();
        return currentThread;
    }

    private class FindMinimalSpanningTrek implements Runnable {

        Thread thisThread;

        FindMinimalSpanningTrek() {
            thisThread = new Thread(this);
            thisThread.start();
        }

        public void run() {
            while (currentThread < totalThreads) {
                int threadNum = nextThread();
                if (threadNum >= totalThreads) {
                    break;
                }
                int size = currentMarginalSet.size();
                NodePair nodePair = currentNodePairs.get(threadNum);
                System.out.println("Finding minmal spanning treks... " + threadNum + " of " + totalThreads);
                if (currentGraph.isAdjacentTo(currentGraph.getNode(nodePair.getFirst().getName()), currentGraph.getNode(nodePair.getSecond().getName()))) {
                    Set<Node> otherNodes = new HashSet<Node>(currentMarginalSet);
                    List<Node> adjacency = new ArrayList<Node>();
                    adjacency.add(nodePair.getFirst());
                    adjacency.add(nodePair.getSecond());
                    otherNodes.removeAll(adjacency);
                    allPaths.get(2).put(adjacency, otherNodes);
                    continue;
                }
                Map<Integer, Map<List<Node>, Set<Node>>> newPaths = new HashMap<Integer, Map<List<Node>, Set<Node>>>();
                for (int k = 2; k <= size; k++) {
                    newPaths.put(k, new HashMap<List<Node>, Set<Node>>());
                }
                for (List<Node> trek : GraphUtils.treks(currentGraph, currentGraph.getNode(nodePair.getFirst().getName()), currentGraph.getNode(nodePair.getSecond().getName()), -1)) {
                    boolean inMarginal = true;
                    for (Node node : trek) {
                        if (!currentMarginalSet.contains(node)) {
                            inMarginal = false;
                            break;
                        }
                    }
                    if (!inMarginal) {
                        continue;
                    }
                    Set<Node> otherNodes = new HashSet<Node>(currentMarginalSet);
                    otherNodes.removeAll(trek);
//                  allPaths.get(trek.size()).put(trek, otherNodes);
                    newPaths.get(trek.size()).put(trek, otherNodes);
                }
                List<List<Node>> remove = new ArrayList<List<Node>>();
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
                    allPaths.get(k).putAll(newPaths.get(k));
                }
            }
        }
    }

    /**
     * Determines whether one trek is a subtrek of another trek
     */
    public boolean isSubtrek(List<Node> trek, List<Node> subtrek) {
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

    /*
     * @return all triples in a graph
     */

    private Set<Triple> getAllTriples(Graph graph) {
        Set<Triple> triples = new HashSet<Triple>();
        for (Node node : graph.getNodes()) {
            List<Node> adjNodes = graph.getAdjacentNodes(node);
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
        allTriples = getAllTriples(graph);
        for (Triple triple : allTriples) {
            if (graph.isDefCollider(triple.getX(), triple.getY(), triple.getZ())) {
                definiteColliders.add(triple);
            }
        }
    }

    /**
     * Finds all edge sequences that ensure a particular trek. Returns the edge sequence with necessary colliders along
     * the sequence mapped to ancestral undirectedPaths
     */
    private static Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> pathsEnsuringTrek(Graph graph, List<Node> ensureTrek, Set<Node> conditioning) {
        Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> paths = new HashMap<Set<Edge>, Map<Triple, List<Set<Edge>>>>();
        pathsEnsuringTrek(graph, ensureTrek, 1, new LinkedList<Node>(Arrays.asList(ensureTrek.get(0))),
                conditioning, new HashMap<Triple, NodePair>(), paths, new HashSet<Node>(Arrays.asList(ensureTrek.get(0))));
        return paths;
    }

    private static void pathsEnsuringTrek(Graph graph, List<Node> ensureTrek, int index,
                                          LinkedList<Node> path, Set<Node> conditioning, Map<Triple, NodePair> colliders,
                                          Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> paths, Set<Node> visited) {
        if (index == ensureTrek.size()) {
            Map<Triple, List<Set<Edge>>> newColliders = new HashMap<Triple, List<Set<Edge>>>();
            for (Triple triple : colliders.keySet()) {
                List<Set<Edge>> edgeSet = new ArrayList<Set<Edge>>();
                List<List<Node>> treks = new ArrayList<List<Node>>();
                treks.addAll(GraphUtils.treks(graph, triple.getY(), colliders.get(triple).getFirst(), -1));
                treks.addAll(GraphUtils.treks(graph, triple.getY(), colliders.get(triple).getSecond(), -1));
                for (List<Node> trek : treks) {
                    /*             if (trek.get(1).equals(triple.getZ())) {
                                     continue;
                                 }
                    */
                    Set<Edge> edges = new HashSet<Edge>();
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
            Set<Edge> edges = new HashSet<Edge>();
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
                /*    if (next.equals(node2)) {
                        path.removeLast();
                        visited.remove(next);
                        continue;
                    }
                */
                Node node0 = path.get(path.size() - 3);
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
            Set<Node> currentVisited;
            if (next.equals(node2)) {
                index++;
                currentVisited = new HashSet<Node>();
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
            List<Triple> remove = new ArrayList<Triple>();
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
     * For each necessary trek in a marginal graph, finds the treks in the getModel graph that traverse each node in the
     * trek using only intermediary nodes not present in the marginal graph
     */
    private void ensureMinimalSpanningTreks(Graph graph) {
        necessaryTreks = new HashMap<List<Node>, Map<Set<Edge>, Map<Triple, List<Set<Edge>>>>>();
        for (Set<Node> marginalSet : marginalVars) {
            Map<List<Node>, Set<Node>> minimalSpanningTreks = minimalSpanningTreks(graph, marginalSet);
            Set<List<Node>> treks = minimalSpanningTreks.keySet();
            System.out.println(treks);
            int t = 1;
            for (List<Node> trek : treks) {
                System.out.println("Finding ways to ensure minimal spanning treks... " + t + " of " + treks.size());
                necessaryTreks.put(trek, pathsEnsuringTrek(graph, trek, minimalSpanningTreks.get(trek)));
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
        List<Graph> graphs = new ArrayList<Graph>();
        graphs.add(newGraph);
        for (Triple triple : colliderSet.keySet()) {
            generateSkeletons(graphs, triple, colliderSet.get(triple));
        }
        return graphs;
    }

    private void generateSkeletons(List<Graph> graphs, Triple collider, List<Set<Edge>> paths) {
        List<Graph> newGraphs = new ArrayList<Graph>();
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
        List<Node> path = new ArrayList<Node>();
        path.add(start);
        Node next = start;
        Node last = start;
        Node current = start;
        for (int k = 1; k < newGraph.getNumNodes(); k++) {
            List<Edge> adjEdges = newGraph.getEdges(current);
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

    private Map<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>> findPossibleSkeletons(Graph graph) {
        Set<Edge> allEdges = new HashSet<Edge>(graph.getEdges());
        Set<Edge> remove = new HashSet<Edge>();

        for (Edge edge : allEdges) {
            boolean necessary = false;
            for (List<Node> necessaryTrek : necessaryTreks.keySet()) {
                necessary = true;
                Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> possiblePaths = necessaryTreks.get(necessaryTrek);
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
        Map<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>> possibleEdges = new HashMap<Set<Edge>, Set<Map<Triple, List<Set<Edge>>>>>();
        PowerSet<Edge> pset = new PowerSet<Edge>(allEdges);
        for (Set<Edge> set : pset) {
            boolean possible = true;
            Set<Map<Triple, List<Set<Edge>>>> colliderSets = new HashSet<Map<Triple, List<Set<Edge>>>>();
            for (List<Node> necessaryTrek : necessaryTreks.keySet()) {
                Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> possiblePaths = necessaryTreks.get(necessaryTrek);
                boolean okay = false;
                Set<Map<Triple, List<Set<Edge>>>> necessaryColliders = new HashSet<Map<Triple, List<Set<Edge>>>>();
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
                            Map<Triple, List<Set<Edge>>> newColliders = new HashMap<Triple, List<Set<Edge>>>();
                            for (Triple triple : colliders.keySet()) {
                                List<Set<Edge>> newEdges = new ArrayList<Set<Edge>>();
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
     * Tries to orient all collider/noncollider combination of triples accepting
     * only those that ensure all treks and preserve all d-separations
     */

    private void allColliderCombinations(Graph graph, Set<Edge> removedEdges, Set<Triple> newColliders, int skeletonsLeft) {
        currentGraph = graph;
        for (List<Node> necessaryTrek : necessaryTreks.keySet()) {
            Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> possiblePaths = necessaryTreks.get(necessaryTrek);
            Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> newPossiblePaths = new HashMap<Set<Edge>, Map<Triple, List<Set<Edge>>>>();
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
                        if (path.contains(oldGraph.getEdge(triple.getX(), triple.getY())) &&
                                path.contains(oldGraph.getEdge(triple.getZ(), triple.getY()))) {
                            if (!possiblePaths.get(path).containsKey(triple)) {
                                add = false;
                                break;
                            }
                        }
                    }
                }
                if (add) {
                    Map<Triple, List<Set<Edge>>> colliderMap = possiblePaths.get(path);
                    Map<Triple, List<Set<Edge>>> newColliderMap = new HashMap<Triple, List<Set<Edge>>>();
                    for (Triple collider : colliderMap.keySet()) {
                        List<Set<Edge>> pathSet = new ArrayList<Set<Edge>>();
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
            currentNecessaryTreks.add(newPossiblePaths);
        }
        for (Triple triple : allTriples) {
            if (!graph.isAdjacentTo(triple.getX(), triple.getZ()) && !definiteNoncolliders.contains(triple)
                    && !graph.isDefCollider(triple.getX(), triple.getY(), triple.getZ()) &&
                    graph.isAdjacentTo(triple.getX(), triple.getY()) &&
                    graph.isAdjacentTo(triple.getY(), triple.getZ()) &&
                    !graph.isUnderlineTriple(triple.getX(), triple.getY(), triple.getZ())) {
                currentPossibleColliders.add(triple);
            }
        }
        Set<Triple> remove = new HashSet<Triple>();
        for (Triple triple : currentPossibleColliders) {
            boolean necessary = false;
            for (Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> necessaryTrek : currentNecessaryTreks) {
                necessary = true;
                boolean size1 = false;
                if (necessaryTrek.size() == 1) {
                    size1 = true;
                }
                for (Set<Edge> path : necessaryTrek.keySet()) {
                    if (!path.contains(oldGraph.getEdge(triple.getX(), triple.getY())) ||
                            !path.contains(oldGraph.getEdge(triple.getZ(), triple.getY())) ||
                            necessaryTrek.get(path).containsKey(triple)) {
                        necessary = false;
                        break;
                    }
                }
                if (!necessary && size1) {
                    for (Map<Triple, List<Set<Edge>>> tripleMap : necessaryTrek.values()) {
                        for (List<Set<Edge>> path : tripleMap.values()) {
                            if (path.size() == 1) {
                                if (path.get(0).contains(oldGraph.getEdge(triple.getX(), triple.getY())) &&
                                        path.get(0).contains(oldGraph.getEdge(triple.getZ(), triple.getY()))) {
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
        currentPossibleColliders.removeAll(remove);
        simpleColliderIterator(skeletonsLeft);
        currentPossibleColliders.clear();
        currentNecessaryTreks.clear();
    }

    private void simpleColliderIterator(int skeletonsLeft) {
        Set<Set<Triple>> necessaryEdges = new HashSet<Set<Triple>>();
        PowerSet<Triple> pset = new PowerSet<Triple>(currentPossibleColliders);
        int psetsize = (int) java.lang.Math.pow(2, currentPossibleColliders.size());
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
                Graph newGraph = new EdgeListGraph(currentGraph);
                for (Triple triple : set) {
                    newGraph.setEndpoint(triple.getX(), triple.getY(), Endpoint.ARROW);
                    newGraph.setEndpoint(triple.getZ(), triple.getY(), Endpoint.ARROW);
                }
                doFinalOrientation(newGraph);
                for (Graph graph : finalGraphs) {
                    if (!predictsFalseDependence(graph)) {
                        Set<Triple> newColliders = new HashSet<Triple>(allTriples);
                        newColliders.removeAll(definiteColliders);
                        newColliders.removeAll(set);
                        Set<Triple> remove = new HashSet<Triple>();
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
                        if (!graph.existsDirectedCycle()) {
                            graph.setUnderLineTriples(new HashSet<Triple>());
                            output.add(graph);
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
            for (Map<Set<Edge>, Map<Triple, List<Set<Edge>>>> necessaryTrek : currentNecessaryTreks) {
                boolean okay = false;
                for (Set<Edge> path : necessaryTrek.keySet()) {
                    boolean pathOkay = true;
                    for (Triple triple : newSet) {
                        if (path.contains(currentGraph.getEdge(triple.getX(), triple.getY())) && path.contains(currentGraph.getEdge(triple.getY(), triple.getZ()))
                                && !necessaryTrek.get(path).containsKey(triple)) {
                            pathOkay = false;
                            break;
                        } else {
                            for (Triple collider : necessaryTrek.get(path).keySet()) {
                                if (!currentGraph.isDefCollider(collider.getX(), collider.getY(), collider.getZ()) &&
                                        !newSet.contains(collider)) {
                                    pathOkay = false;
                                    break;
                                } else {
                                    pathOkay = false;
                                    for (Set<Edge> adjPath : necessaryTrek.get(path).get(collider)) {
                                        if (adjPath.contains(oldGraph.getEdge(triple.getX(), triple.getY())) &&
                                                adjPath.contains(oldGraph.getEdge(triple.getZ(), triple.getY()))) {
                                            continue;
                                        } else if (adjPath.contains(oldGraph.getEdge(triple.getX(), triple.getY()))) {
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
                                        } else if (adjPath.contains(oldGraph.getEdge(triple.getZ(), triple.getY()))) {
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

    /*
     * Does the final set of orientations after colliders have been oriented
     */

    private void doFinalOrientation(Graph graph) {
        discrimGraphs.clear();
        finalGraphs.clear();
        currentDiscrimGraphs.add(graph);
        while (changeFlag) {
            changeFlag = false;
            currentDiscrimGraphs.addAll(discrimGraphs);
            discrimGraphs.clear();
            for (Graph newGraph : currentDiscrimGraphs) {
                doubleTriangle(newGraph);
                awayFromColliderAncestorCycle(newGraph);
                if (!discrimPaths(newGraph)) {
                    if (changeFlag) {
                        discrimGraphs.add(newGraph);
                    } else {
                        finalGraphs.add(newGraph);
                    }
                }
            }
            currentDiscrimGraphs.clear();
        }
        changeFlag = true;
    }

    /**
     * Implements the double-triangle orientation rule, which states that if D*-oB, A*->B<-*C and A*-*D*-*C is a
     * noncollider, then D*->B.
     */
    private void doubleTriangle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {

            List<Node> intoBArrows = graph.getNodesInTo(B, Endpoint.ARROW);
            List<Node> intoBCircles = graph.getNodesInTo(B, Endpoint.CIRCLE);

            //possible A's and C's are those with arrows into B
            List<Node> possA = new LinkedList<Node>(intoBArrows);
            List<Node> possC = new LinkedList<Node>(intoBArrows);

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

                        //if all of the previous tests pass, orient D*-oB as D*->B
                        if (!isArrowpointAllowed(graph, D, B)) {
                            continue;
                        }

                        graph.setEndpoint(D, B, Endpoint.ARROW);
                        changeFlag = true;
                    }
                }
            }
        }
    }

    // Does only the ancestor and cycle rules of these repeatedly until no changes

    private void awayFromAncestorCycle(Graph graph) {
        while (changeFlag) {
            changeFlag = false;
            List<Node> nodes = graph.getNodes();

            for (Node B : nodes) {
                List<Node> adj = graph.getAdjacentNodes(B);

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
        changeFlag = true;
    }

    // Does all 3 of these rules at once instead of going through all
    // triples multiple times per iteration of doFinalOrientation.

    private void awayFromColliderAncestorCycle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {
            List<Node> adj = graph.getAdjacentNodes(B);

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
            // that look like this:   A<-oBo->C  or  A<->Bo->C
            List<Node> possAandC = graph.getNodesOutTo(b, Endpoint.ARROW);

            //keep arrows and circles
            List<Node> possA = new LinkedList<Node>(possAandC);
            possA.removeAll(graph.getNodesInTo(b, Endpoint.TAIL));

            //keep only circles
            List<Node> possC = new LinkedList<Node>(possAandC);
            possC.retainAll(graph.getNodesInTo(b, Endpoint.CIRCLE));

            for (Node a : possA) {
                for (Node c : possC) {
                    if (!graph.isParentOf(a, c)) {
                        continue;
                    }

                    LinkedList<Node> reachable = new LinkedList<Node>();
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
    private boolean reachablePathFindOrient(Graph graph, Node a, Node b, Node c,
                                            LinkedList<Node> reachable) {
        Set<Node> cParents = new HashSet<Node>(graph.getParents(c));

        // Needed to avoid cycles in failure case.
        Set<Node> visited = new HashSet<Node>();
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
    private void doDdpOrientationFinal(Graph graph, Node l, Node a, Node b, Node c) {
        changeFlag = true;
        List<Node> sepset = new ArrayList<Node>();
        for (SepsetMapDci msepset : sepsetMaps) {
            List<Node> condSet = msepset.get(l, c);
            if (condSet != null) {
                sepset.addAll(condSet);
            }
        }

        Graph newGraph1 = new EdgeListGraph(graph);
        newGraph1.setEndpoint(c, b, Endpoint.TAIL);
        discrimGraphs.add(newGraph1);

        // only add collider graph if not known to be d-separated by some set
        // containing b
        if (sepset.contains(b)) {
            return;
        }

        Graph newGraph2 = new EdgeListGraph(graph);
        if (!isArrowpointAllowed(graph, a, b)) {
            return;
        }

        if (!isArrowpointAllowed(graph, c, b)) {
            return;
        }

        newGraph2.setEndpoint(a, b, Endpoint.ARROW);
        newGraph2.setEndpoint(c, b, Endpoint.ARROW);
        discrimGraphs.add(newGraph2);
    }

    /**
     * Checks to make sure a graph entails d-separations relations from the sepsetMap
     */
    private boolean predictsFalseDependence(Graph graph) {
        for (int k = 0; k < variables.size() - 1; k++) {
            Node x = variables.get(k);
            for (int m = k + 1; m < variables.size(); m++) {
                Node y = variables.get(m);
                for (SepsetMapDci sepset : minimalSepsetMaps) {
                    if (sepset.get(x, y) == null) {
                        continue;
                    }
                    for (List<Node> condSet : sepset.getSet(x, y)) {
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
        List<SepsetMapDci> allSepsets = new ArrayList<SepsetMapDci>();
        Pc fci = new Pc(new IndTestSepset(combineSepsets(sepsetMaps), variables));
        System.out.println("Starting pc...");
        SepsetMapDci consSepset = new SepsetMapDci();
        doSepsetClosure(consSepset, fci.search());
        for (int k = 0; k < marginalVars.size(); k++) {
            SepsetMapDci newSepset = new SepsetMapDci();
            List<NodePair> pairs = allNodePairs(new ArrayList<Node>(marginalVars.get(k)));
            int p = 1;
            for (NodePair pair : pairs) {
                Node x = pair.getFirst();
                Node y = pair.getSecond();
                if (consSepset.getSet(x, y) == null) {
                    continue;
                }
                int c = 1;
                List<List<Node>> conds = consSepset.getSet(x, y);
                for (List<Node> z : conds) {
                    System.out.println("Resolving inconsistencies... " + c + " of " + conds.size() + " (" + p + " of " + pairs.size() + " pairs and )" + (k + 1) + " of " + marginalVars.size() + " datasets)");
                    if (marginalVars.get(k).containsAll(z)) {
                        newSepset.set(x, y, z);
                    }
                    c++;
                }
                p++;
            }
            allSepsets.add(newSepset);
        }
        for (int k = 0; k < marginalVars.size(); k++) {
            List<Node> variables = new ArrayList<Node>(marginalVars.get(k));
            Graph newGraph = new EdgeListGraph(variables);
            newGraph.fullyConnect(Endpoint.CIRCLE);
            FasDci fas = new FasDci(newGraph, new IndTestSepset(allSepsets.get(k), new ArrayList<Node>(marginalVars.get(k))));
            minimalSepsetMaps.add(fas.search());
        }
        sepsetMaps = allSepsets;
        System.out.println(sepsetMaps);
    }

    /**
     * Resolves possibly conflicting independence/dependence statements that result from a set of known independence
     * statements
     */
    private void resolveResultingIndependenciesB() {
        SepsetMapDci combinedSepset = combineSepsets(sepsetMaps);
        Pc pc = new Pc(new IndTestSepset(combinedSepset, variables));
        Graph allInd = pc.search();
        System.out.println("Pc finished...");
        List<Node> overlap = new ArrayList<Node>(marginalVars.get(0));
        System.out.println(marginalVars.get(0).size());
        for (int k = 1; k < marginalVars.size(); k++) {
            System.out.println("Size: " + marginalVars.get(k).size());
            Set<Node> marginal = marginalVars.get(k);
            List<Node> remove = new ArrayList<Node>();
            for (Node node : overlap) {
                if (!marginal.contains(node)) {
                    remove.add(node);
                }
            }
            overlap.removeAll(remove);
        }
        System.out.println("Overlap: " + overlap);
        System.out.println((overlap.size() / (double) variables.size()));
        List<Graph> marginals = new ArrayList<Graph>();
        for (int k = 0; k < marginalVars.size(); k++) {
            Pc mpc = new Pc(independenceTests.get(k));
            marginals.add(mpc.search());
            System.out.println("Pc finished " + (k + 1) + " of " + marginalVars.size());
        }
        List<NodePair> pairs = allNodePairs(variables);
        //List<NodePair> pairs  = allNodePairs(overlap);
        int p = 1;
        for (NodePair pair : pairs) {
            Set<Node> condSet = new HashSet<Node>();
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
                } catch (Exception e) {

                }
                try {
                    for (Node node : graph.getAdjacentNodes(graph.getNode(pair.getSecond().getName()))) {
                        Node newNode = allInd.getNode(node.getName());
                        //    if (overlap.contains(newNode)) {
                        condSet.add(newNode);
                        //    }
                    }
                } catch (Exception e) {

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
            int cs = (int) Math.pow(2, condSet.size());
            for (Set<Node> set : new PowerSet<Node>(condSet)) {
                System.out.println("Resolving inconsistencies... " + c + " of " + cs + " (" + p + " of " + pairs.size() + " pairs)");
                c++;
                List<Node> z = new ArrayList<Node>(set);
                if (allInd.isDConnectedTo(pair.getFirst(), pair.getSecond(), z)) {
                    continue;
                }
                combinedSepset.set(pair.getFirst(), pair.getSecond(), new ArrayList<Node>(set));

            }
            p++;
        }
        sepsetMaps.clear();
        for (int k = 0; k < marginalVars.size(); k++) {
            SepsetMapDci newSepset = new SepsetMapDci();
            List<NodePair> pairs2 = allNodePairs(new ArrayList<Node>(marginalVars.get(k)));
            for (NodePair pair : pairs2) {
                Node x = pair.getFirst();
                Node y = pair.getSecond();
                if (combinedSepset.getSet(x, y) == null) {
                    continue;
                }
                List<List<Node>> conds = combinedSepset.getSet(x, y);
                for (List<Node> z : conds) {
                    if (marginalVars.get(k).containsAll(z)) {
                        newSepset.set(x, y, z);
                    }
                }
            }
            //sepsetMaps.add(newSepset);
            sepsetMaps.add(newSepset);
            List<Node> variables = new ArrayList<Node>(marginalVars.get(k));
            Graph newGraph = new EdgeListGraph(variables);
            newGraph.fullyConnect(Endpoint.CIRCLE);
            FasDci fas = new FasDci(newGraph, new IndTestSepset(newSepset, variables));
            minimalSepsetMaps.add(fas.search());
        }
    }

    /**
     * Resolves possibly conflicting independence/dependence statements that result from a set of known independence
     * statements
     */
    private void resolveResultingIndependenciesC() {
        List<SepsetMapDci> allSepsets = new ArrayList<SepsetMapDci>();
        Pc fci = new Pc(new IndTestSepset(combineSepsets(sepsetMaps), variables));
        System.out.println("Starting pc...");
        SepsetMapDci consSepset = new SepsetMapDci();
        Graph fciResult = fci.search();
        SepsetMap fciSepset = fci.getSepsets();
        for (int k = 0; k < marginalVars.size(); k++) {
            SepsetMapDci newSepset = new SepsetMapDci(sepsetMaps.get(k));
            List<NodePair> pairs = allNodePairs(new ArrayList<Node>(marginalVars.get(k)));
            int p = 1;
            for (NodePair pair : pairs) {
                Node x = pair.getFirst();
                Node y = pair.getSecond();
                if (fciSepset.get(x, y) == null) {
                    continue;
                }
                List<Node> set = fciSepset.get(x, y);
                List<Node> currentset = new ArrayList<Node>();
                if (newSepset.get(x, y) != null) {
                    currentset.addAll(newSepset.get(x, y));
                }
                int c = 1;
                for (Node node : set) {
                    System.out.println("Resolving inconsistencies... " + c + " of " + set.size() + " (" + p + " of " + pairs.size() + " pairs and )" + (k + 1) + " of " + marginalVars.size() + " datasets)");
                    if (currentset.contains(node)) {
                        continue;
                    }
                    List<Node> possibleCond = new ArrayList<Node>(set);
                    possibleCond.remove(node);
                    PowerSet<Node> pset = new PowerSet<Node>(possibleCond);
                    for (Set<Node> inpset : pset) {
                        List<Node> cond = new ArrayList<Node>(inpset);
                        cond.add(node);
                        if (fciResult.isDSeparatedFrom(x, y, cond)) {
                            newSepset.set(x, y, cond);
                        }
                    }
                }
            }
            allSepsets.add(newSepset);
        }
        sepsetMaps = allSepsets;
        System.out.println(sepsetMaps);
    }


    /**
     * Encodes every possible separation in a graph in the sepset
     */
    private void doSepsetClosure(SepsetMapDci sepset, Graph graph) {
        List<Node> nodes = graph.getNodes();
        List<NodePair> pairs = allNodePairs(nodes);
        int p = 1;
        for (NodePair pair : pairs) {
            List<Node> possibleNodes = new ArrayList<Node>(nodes);
            //ist<Node> possibleNodes = new ArrayList<Node>();
            Node x = pair.getFirst();
            Node y = pair.getSecond();
            possibleNodes.remove(x);
            possibleNodes.remove(y);
            possibleNodes.addAll(graph.getAdjacentNodes(x));
            possibleNodes.addAll(graph.getAdjacentNodes(y));
            int c = 1;
            int ps = (int) Math.pow(2, possibleNodes.size());
            for (Set<Node> condSet : new PowerSet<Node>(possibleNodes)) {
                System.out.println("Getting closure set... " + c + " of " + ps + "(" + p + " of " + pairs.size() + " remaining)");
                if (graph.isDSeparatedFrom(x, y, new ArrayList<Node>(condSet))) {
                    sepset.set(x, y, new ArrayList<Node>(condSet));
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
                for (List<Node> condSet : sepset.getSet(x, y)) {
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
        List<NodePair> nodePairs = new ArrayList<NodePair>();
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

        public PowerSet(Collection<E> all) {
            this.all = all;
        }

        /**
         * @return an iterator over elements of type Collection<E> which enumerates the PowerSet of the collection used
         * in the constructor
         */

        public Iterator<Set<E>> iterator() {
            return new PowerSetIterator<E>(this);
        }

        class PowerSetIterator<InE> implements Iterator<Set<InE>> {
            PowerSet<InE> powerSet;
            List<InE> canonicalOrder = new ArrayList<InE>();
            List<InE> mask = new ArrayList<InE>();
            boolean hasNext = true;

            PowerSetIterator(PowerSet<InE> powerSet) {

                this.powerSet = powerSet;
                canonicalOrder.addAll(powerSet.all);
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private boolean allOnes() {
                for (InE bit : mask) {
                    if (bit == null) {
                        return false;
                    }
                }
                return true;
            }

            private void increment() {
                int i = 0;
                while (true) {
                    if (i < mask.size()) {
                        InE bit = mask.get(i);
                        if (bit == null) {
                            mask.set(i, canonicalOrder.get(i));
                            return;
                        } else {
                            mask.set(i, null);
                            i++;
                        }
                    } else {
                        mask.add(canonicalOrder.get(i));
                        return;
                    }
                }
            }

            public boolean hasNext() {
                return hasNext;
            }

            public Set<InE> next() {

                Set<InE> result = new HashSet<InE>();
                result.addAll(mask);
                result.remove(null);

                hasNext = mask.size() < powerSet.all.size() || !allOnes();

                if (hasNext) {
                    increment();
                }

                return result;

            }

        }
    }
}



