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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements the ION (Integration of Overlapping Networks) algorithm for distributed causal inference. The algorithm
 * takes as input a set of PAGs (presumably learned using a local learning algorithm) over variable sets that may have
 * some variables in common and others not in common. The algorithm returns a complete set of PAGs over every variable
 * form an input PAG_of_the_true_DAG that are consistent (same d-separations and d-connections) with every input PAG_of_the_true_DAG.
 *
 * @author Robert Tillman
 */

public class Ion {

    // prune using path length
    private boolean pathLengthSearch = true;

    // prune using adjacencies
    private boolean adjacencySearch;

    /**
     * The input PAGs being to be intergrated, possibly FCI outputs.
     */
    private final List<Graph> input = new ArrayList<>();

    /**
     * The output PAGs over all variables consistent with the input PAGs
     */
    private final List<Graph> output = new ArrayList<>();

    /**
     * All the variables being integrated from the input PAGs
     */
    private final List<String> variables = new ArrayList<>();

    /**
     * Definite noncolliders
     */
    private final Set<Triple> definiteNoncolliders = new HashSet<>();

    /**
     * separations and associations found in the input PAGs
     */
    private Set<IonIndependenceFacts> separations;
    private Set<IonIndependenceFacts> associations;

    /**
     * tracks changes for final orientations orientation methods
     */
    private boolean changeFlag = true;
    private final Set<Graph> discrimGraphs = new HashSet<>();
    private final Set<Graph> finalResult = new HashSet<>();

    // running runtime and time and size information for hitting sets
    private final List<Integer> recGraphs = new ArrayList<>();
    private final List<Double> recHitTimes = new ArrayList<>();
    private double runtime;

    // maximum memory usage
    private double maxMemory;

    //============================= Constructor ============================//


    /**
     * Constructs a new instance of the ION search from the input PAGs
     *
     * @param pags The PAGs to be integrated
     */
    public Ion(final List<Graph> pags) {
        for (final Graph pag : pags) {
            this.input.add(pag);

        }
        for (final Graph pag : this.input) {
            for (final Node node : pag.getNodes()) {
                if (!this.variables.contains(node.getName())) {
                    this.variables.add(node.getName());
                }
            }
            for (final Triple triple : getAllTriples(pag)) {
                if (pag.isDefNoncollider(triple.getX(), triple.getY(), triple.getZ())) {
                    pag.addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
                }
            }
        }
    }

    //============================= Public Methods ============================//

    /**
     * Sets path length search on or off
     */
    public void setPathLengthSearch(final boolean b) {
        this.pathLengthSearch = b;
    }

    /**
     * Sets adjacency search on or off
     */
    public void setAdjacencySearch(final boolean b) {
        this.adjacencySearch = b;
    }

    /**
     * Begins the ION search procedure, described at each step
     */
    public List<Graph> search() {

        final long start = System.currentTimeMillis();
        TetradLogger.getInstance().log("info", "Starting ION Search.");
        logGraphs("\nInitial Pags: ", this.input);
        TetradLogger.getInstance().log("info", "Transfering local information.");
        long steps = System.currentTimeMillis();

        /*
         * Step 1 - Create the empty graph
         */
        final List<Node> varNodes = new ArrayList<>();
        for (final String varName : this.variables) {
            varNodes.add(new GraphNode(varName));
        }
        final Graph graph = new EdgeListGraph(varNodes);

        /*
         * Step 2 - Transfer local information from the PAGs (adjacencies
         * and edge orientations)
         */
        // transfers edges from each graph and finds definite noncolliders
        transferLocal(graph);
        // adds edges for variables never jointly measured
        for (final NodePair pair : nonIntersection(graph)) {
            graph.addEdge(new Edge(pair.getFirst(), pair.getSecond(), Endpoint.CIRCLE, Endpoint.CIRCLE));
        }
        TetradLogger.getInstance().log("info", "Steps 1-2: " + (System.currentTimeMillis() - steps) / 1000. + "s");
        System.out.println("step2");
        System.out.println(graph);

        /*
         * Step 3
         *
         * Branch and prune step that blocks problematic undirectedPaths, possibly d-connecting undirectedPaths
         */
        steps = System.currentTimeMillis();
        final Queue<Graph> searchPags = new LinkedList<>();
        // place graph constructed in step 2 into the queue
        searchPags.offer(graph);
        // get d-separations and d-connections
        final List<Set<IonIndependenceFacts>> sepAndAssoc = findSepAndAssoc(graph);
        this.separations = sepAndAssoc.get(0);
        this.associations = sepAndAssoc.get(1);
        Map<Collection<Node>, List<PossibleDConnectingPath>> paths;
        final Queue<Graph> step3Pags = new LinkedList<>();
        final Set<Graph> reject = new HashSet<>();
        // if no d-separations, nothing left to search
        if (this.separations.isEmpty()) {
            // makes orientations preventing definite noncolliders from becoming colliders
            // do final orientations
//            doFinalOrientation(graph);
            step3Pags.add(graph);
        }
        // sets length to iterate once if search over path lengths not enabled, otherwise set to 2
        final int numNodes = graph.getNumNodes();
        int pl = numNodes - 1;
        if (this.pathLengthSearch) {
            pl = 2;
        }
        // iterates over path length, then adjacencies
        for (int l = pl; l < numNodes; l++) {
            if (this.pathLengthSearch) {
                TetradLogger.getInstance().log("info", "Braching over path lengths: " + l + " of " + (numNodes - 1));
            }
            int seps = this.separations.size();
            final int currentSep = 1;
            final int numAdjacencies = this.separations.size();
            for (final IonIndependenceFacts fact : this.separations) {
                if (this.adjacencySearch) {
                    TetradLogger.getInstance().log("info", "Braching over path nonadjacencies: " + currentSep + " of " + numAdjacencies);
                }
                seps--;
                // uses two queues to keep up with which PAGs are being iterated and which have been
                // accepted to be iterated over in the next iteration of the above for loop
                searchPags.addAll(step3Pags);
                this.recGraphs.add(searchPags.size());
                step3Pags.clear();
                while (!searchPags.isEmpty()) {
                    System.out.println("ION Step 3 size: " + searchPags.size());
                    final double currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    if (currentUsage > this.maxMemory) this.maxMemory = currentUsage;
                    // deques first PAG from searchPags
                    final Graph pag = searchPags.poll();
                    // Part 3.a - finds possibly d-connecting undirectedPaths between each pair of nodes
                    // known to be d-separated
                    final List<PossibleDConnectingPath> dConnections = new ArrayList<>();
                    // checks to see if looping over adjacencies
                    if (this.adjacencySearch) {
                        for (final Collection<Node> conditions : fact.getZ()) {
                            // checks to see if looping over path lengths
                            if (this.pathLengthSearch) {
                                dConnections.addAll(PossibleDConnectingPath.findDConnectingPathsOfLength
                                        (pag, fact.getX(), fact.getY(), conditions, l));
                            } else {
                                dConnections.addAll(PossibleDConnectingPath.findDConnectingPaths
                                        (pag, fact.getX(), fact.getY(), conditions));
                            }
                        }
                    } else {
                        for (final IonIndependenceFacts allfact : this.separations) {
                            for (final Collection<Node> conditions : allfact.getZ()) {
                                // checks to see if looping over path lengths
                                if (this.pathLengthSearch) {
                                    dConnections.addAll(PossibleDConnectingPath.findDConnectingPathsOfLength
                                            (pag, allfact.getX(), allfact.getY(), conditions, l));
                                } else {
                                    dConnections.addAll(PossibleDConnectingPath.findDConnectingPaths
                                            (pag, allfact.getX(), allfact.getY(), conditions));
                                }
                            }
                        }
                    }
                    // accept PAG_of_the_true_DAG go to next PAG_of_the_true_DAG if no possibly d-connecting undirectedPaths
                    if (dConnections.isEmpty()) {
//                        doFinalOrientation(pag);
                        step3Pags.add(pag);
                        continue;
                    }
                    // maps conditioning sets to list of possibly d-connecting undirectedPaths
                    paths = new HashMap<>();
                    for (final PossibleDConnectingPath path : dConnections) {
                        List<PossibleDConnectingPath> p = paths.get(path.getConditions());
                        if (p == null) {
                            p = new LinkedList<>();
                        }
                        p.add(path);
                        paths.put(path.getConditions(), p);
                    }
                    // Part 3.b - finds minimal graphical changes to block possibly d-connecting undirectedPaths
                    final List<Set<GraphChange>> possibleChanges = new ArrayList<>();
                    for (final Set<GraphChange> changes : findChanges(paths)) {
                        final Set<GraphChange> newChanges = new HashSet<>();
                        for (final GraphChange gc : changes) {
                            boolean okay = true;
                            for (final Triple collider : gc.getColliders()) {

                                if (pag.isUnderlineTriple(collider.getX(), collider.getY(), collider.getZ())) {
                                    okay = false;
                                    break;

                                }
                            }
                            if (!okay) {
                                continue;
                            }
                            for (final Triple collider : gc.getNoncolliders()) {
                                if (pag.isDefCollider(collider.getX(), collider.getY(), collider.getZ())) {
                                    okay = false;
                                    break;
                                }
                            }
                            if (okay) {
                                newChanges.add(gc);
                            }
                        }
                        if (!newChanges.isEmpty()) {
                            possibleChanges.add(newChanges);
                        } else {
                            possibleChanges.clear();
                            break;
                        }
                    }
                    final float starthitset = System.currentTimeMillis();
                    final Collection<GraphChange> hittingSets = IonHittingSet.findHittingSet(possibleChanges);
                    this.recHitTimes.add((System.currentTimeMillis() - starthitset) / 1000.);
                    // Part 3.c - checks the newly constructed graphs from 3.b and rejects those that
                    // cycles or produce independencies known not to occur from the input PAGs or
                    // include undirectedPaths from definite nonancestors
                    for (final GraphChange gc : hittingSets) {
                        boolean badhittingset = false;
                        for (final Edge edge : gc.getRemoves()) {
                            final Node node1 = edge.getNode1();
                            final Node node2 = edge.getNode2();
                            final Set<Triple> triples = new HashSet<>();
                            triples.addAll(gc.getColliders());
                            triples.addAll(gc.getNoncolliders());
                            if (triples.size() != (gc.getColliders().size() + gc.getNoncolliders().size())) {
                                badhittingset = true;
                                break;
                            }
                            for (final Triple triple : triples) {
                                if (node1.equals(triple.getY())) {
                                    if (node2.equals(triple.getX()) ||
                                            node2.equals(triple.getZ())) {
                                        badhittingset = true;
                                        break;
                                    }
                                }
                                if (node2.equals(triple.getY())) {
                                    if (node1.equals(triple.getX()) ||
                                            node1.equals(triple.getZ())) {
                                        badhittingset = true;
                                        break;
                                    }
                                }
                            }
                            if (badhittingset) {
                                break;
                            }
                            for (final NodePair pair : gc.getOrients()) {
                                if ((node1.equals(pair.getFirst()) && node2.equals(pair.getSecond())) ||
                                        (node2.equals(pair.getFirst()) && node1.equals(pair.getSecond()))) {
                                    badhittingset = true;
                                    break;
                                }
                            }
                            if (badhittingset) {
                                break;
                            }
                        }
                        if (!badhittingset) {
                            for (final NodePair pair : gc.getOrients()) {
                                for (final Triple triple : gc.getNoncolliders()) {
                                    if (pair.getSecond().equals(triple.getY())) {
                                        if (pair.getFirst().equals(triple.getX()) &&
                                                pag.getEndpoint(triple.getZ(), triple.getY()).equals(Endpoint.ARROW)) {
                                            badhittingset = true;
                                            break;

                                        }
                                        if (pair.getFirst().equals(triple.getZ()) &&
                                                pag.getEndpoint(triple.getX(), triple.getY()).equals(Endpoint.ARROW)) {
                                            badhittingset = true;
                                            break;
                                        }
                                    }
                                    if (badhittingset) {
                                        break;
                                    }
                                }
                                if (badhittingset) {
                                    break;
                                }
                            }
                        }
                        if (badhittingset) {
                            continue;
                        }
                        final Graph changed = gc.applyTo(pag);
                        // if graph change has already been rejected move on to next graph
                        if (reject.contains(changed)) {
                            continue;
                        }
                        // if graph change has already been accepted move on to next graph
                        if (step3Pags.contains(changed)) {
                            continue;
                        }
                        // reject if null, predicts false independencies or has cycle
                        if (predictsFalseIndependence(this.associations, changed)
                                || changed.existsDirectedCycle()) {
                            reject.add(changed);
                        }
                        // makes orientations preventing definite noncolliders from becoming colliders
                        // do final orientations
//                        doFinalOrientation(changed);
                        // now add graph to queue
                        step3Pags.add(changed);
                    }
                }
                // exits loop if not looping over adjacencies
                if (!this.adjacencySearch) {
                    break;
                }
            }
        }
        TetradLogger.getInstance().log("info", "Step 3: " + (System.currentTimeMillis() - steps) / 1000. + "s");

        /*
         * Step 4
         *
         * Finds redundant undirectedPaths and uses this information to expand the list
         * of possible graphs
         */
        steps = System.currentTimeMillis();
        Map<Edge, Boolean> necEdges;
        Set<Graph> outputPags = new HashSet<>();
        while (!step3Pags.isEmpty()) {
            final Graph pag = step3Pags.poll();
            necEdges = new HashMap<>();
            // Step 4.a - if x and y are known to be unconditionally associated and there is
            // exactly one trek between them, mark each edge on that trek as necessary and
            // make the tiples on the trek definite noncolliders
            // initially mark each edge as not necessary
            for (final Edge edge : pag.getEdges()) {
                necEdges.put(edge, false);
            }
            // look for unconditional associations
            for (final IonIndependenceFacts fact : this.associations) {
                for (final List<Node> nodes : fact.getZ()) {
                    if (nodes.isEmpty()) {
                        final List<List<Node>> treks = Ion.treks(pag, fact.x, fact.y);
                        if (treks.size() == 1) {
                            final List<Node> trek = treks.get(0);
                            final List<Triple> triples = new ArrayList<>();
                            for (int i = 1; i < trek.size(); i++) {
                                // marks each edge in trek as necessary
                                necEdges.put(pag.getEdge(trek.get(i - 1), trek.get(i)), true);
                                if (i == 1) {
                                    continue;
                                }
                                // makes each triple a noncollider
                                pag.addUnderlineTriple(trek.get(i - 2), trek.get(i - 1), trek.get(i));
                            }
                        }
                        // stop looping once the empty set is found
                        break;
                    }
                }
            }
            // Part 4.b - branches by generating graphs for every combination of removing
            // redundant undirectedPaths
            boolean elimTreks;
            // checks to see if removing redundant undirectedPaths eliminates every trek between
            // two variables known to be nconditionally assoicated
            final List<Graph> possRemovePags = possRemove(pag, necEdges);
            final double currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            if (currentUsage > this.maxMemory) this.maxMemory = currentUsage;
            for (final Graph newPag : possRemovePags) {
                elimTreks = false;
                // looks for unconditional associations
                for (final IonIndependenceFacts fact : this.associations) {
                    for (final List<Node> nodes : fact.getZ()) {
                        if (nodes.isEmpty()) {
                            if (Ion.treks(newPag, fact.x, fact.y).isEmpty()) {
                                elimTreks = true;
                            }
                            // stop looping once the empty set is found
                            break;
                        }
                    }
                }
                // add new PAG to output unless a necessary trek has been eliminated
                if (!elimTreks) {
                    outputPags.add(newPag);
                }
            }
        }
        outputPags = removeMoreSpecific(outputPags);

        TetradLogger.getInstance().log("info", "Step 4: " + (System.currentTimeMillis() - steps) / 1000. + "s");

        /*
         * Step 5
         *
         * Generate the Markov equivalence classes for graphs and accept only
         * those that do not predict false d-separations
         */
        steps = System.currentTimeMillis();
        final Set<Graph> outputSet = new HashSet<>();
        for (final Graph pag : outputPags) {
            final Set<Triple> unshieldedPossibleColliders = new HashSet<>();
            for (final Triple triple : getPossibleTriples(pag)) {
                if (!pag.isAdjacentTo(triple.getX(), triple.getZ())) {
                    unshieldedPossibleColliders.add(triple);
                }
            }

            final PowerSet<Triple> pset = new PowerSet<>(unshieldedPossibleColliders);
            for (final Set<Triple> set : pset) {
                final Graph newGraph = new EdgeListGraph(pag);
                for (final Triple triple : set) {
                    newGraph.setEndpoint(triple.getX(), triple.getY(), Endpoint.ARROW);
                    newGraph.setEndpoint(triple.getZ(), triple.getY(), Endpoint.ARROW);
                }
                doFinalOrientation(newGraph);
            }
            for (final Graph outputPag : this.finalResult) {
                if (!predictsFalseIndependence(this.associations, outputPag)) {
                    final Set<Triple> underlineTriples = new HashSet<>(outputPag.getUnderLines());
                    for (final Triple triple : underlineTriples) {
                        outputPag.removeUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
                    }
                    outputSet.add(outputPag);
                }
            }
        }
        this.output.addAll(outputSet);
        TetradLogger.getInstance().log("info", "Step 5: " + (System.currentTimeMillis() - steps) / 1000. + "s");
        this.runtime = ((System.currentTimeMillis() - start) / 1000.);
        logGraphs("\nReturning output (" + this.output.size() + " Graphs):", this.output);
        final double currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        if (currentUsage > this.maxMemory) this.maxMemory = currentUsage;
        return this.output;
    }

    // returns total runtime and times for hitting set calculations

    public List<String> getRuntime() {
        double totalhit = 0;
        double longesthit = 0;
        double averagehit = 0;
        for (final Double i : this.recHitTimes) {
            totalhit += i;
            averagehit += i / this.recHitTimes.size();
            if (i > longesthit) {
                longesthit = i;
            }
        }
        final List<String> list = new ArrayList<>();
        list.add(Double.toString(this.runtime));
        list.add(Double.toString(totalhit));
        list.add(Double.toString(longesthit));
        list.add(Double.toString(averagehit));
        return list;
    }

    // returns the maximum memory used in a run of ION

    public double getMaxMemUsage() {
        return this.maxMemory;
    }

    // return hitting set sizes

    public List<Integer> getIterations() {
        int totalit = 0;
        int largestit = 0;
        int averageit = 0;
        for (final Integer i : this.recGraphs) {
            totalit += i;
            averageit += i / this.recGraphs.size();
            if (i > largestit) {
                largestit = i;
            }
        }
        final List<Integer> list = new ArrayList<>();
        list.add(totalit);
        list.add(largestit);
        list.add(averageit);
        return list;
    }

    // summarizes time and hitting set time and size information for latex

    public String getStats() {
        String stats = "Total running time:  " + this.runtime + "\\\\";
        int totalit = 0;
        int largestit = 0;
        int averageit = 0;
        for (final Integer i : this.recGraphs) {
            totalit += i;
            averageit += i;
            if (i > largestit) {
                largestit = i;
            }
        }
        averageit /= this.recGraphs.size();
        double totalhit = 0;
        double longesthit = 0;
        double averagehit = 0;
        for (final Double i : this.recHitTimes) {
            totalhit += i;
            averagehit += i / this.recHitTimes.size();
            if (i > longesthit) {
                longesthit = i;
            }
        }
        stats += "Total iterations in step 3:  " + totalit + "\\\\";
        stats += "Largest set of iterations in step 3:  " + largestit + "\\\\";
        stats += "Average iterations set in step 3:  " + averageit + "\\\\";
        stats += "Total hitting sets calculation time:  " + totalhit + "\\\\";
        stats += "Average hitting set calculation time:  " + averagehit + "\\\\";
        stats += "Longest hitting set calculation time:  " + longesthit + "\\\\";
        return stats;
    }

    //============================= Private Methods ============================//

    /**
     * Logs a set of graphs with a corresponding message
     */
    private void logGraphs(final String message, final List<? extends Graph> graphs) {
        if (message != null) {
            TetradLogger.getInstance().log("graph", message);
        }
        for (final Graph graph : graphs) {
            TetradLogger.getInstance().log("graph", graph.toString());
        }
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
     * Finds all node pairs that are not adjacent in an input graph
     */
    private Set<NodePair> nonadjacencies(final Graph graph) {
        final Set<NodePair> nonadjacencies = new HashSet<>();
        for (final Graph inputPag : this.input) {
            for (final NodePair pair : allNodePairs(inputPag.getNodes())) {
                if (!inputPag.isAdjacentTo(pair.getFirst(), pair.getSecond())) {
                    nonadjacencies.add(new NodePair(graph.getNode(pair.getFirst().getName()), graph.getNode(pair.getSecond().getName())));
                }
            }
        }
        return nonadjacencies;
    }

    /*
     * Transfers local information from the input PAGs by adding edges from
     * local PAGs with their orientations and unorienting the edges if there
     * is a conflict and recording definite noncolliders.
     */

    private void transferLocal(final Graph graph) {
        final Set<NodePair> nonadjacencies = nonadjacencies(graph);
        for (final Graph pag : this.input) {
            for (final Edge edge : pag.getEdges()) {
                final NodePair graphNodePair = new NodePair(graph.getNode(edge.getNode1().getName()), graph.getNode(edge.getNode2().getName()));
                if (nonadjacencies.contains(graphNodePair)) {
                    continue;
                }
                if (!graph.isAdjacentTo(graphNodePair.getFirst(), graphNodePair.getSecond())) {
                    graph.addEdge(new Edge(graphNodePair.getFirst(), graphNodePair.getSecond(), edge.getEndpoint1(), edge.getEndpoint2()));
                    continue;
                }
                final Endpoint first = edge.getEndpoint1();
                final Endpoint firstCurrent = graph.getEndpoint(graphNodePair.getSecond(), graphNodePair.getFirst());
                if (!first.equals(Endpoint.CIRCLE)) {
                    if ((first.equals(Endpoint.ARROW) && firstCurrent.equals(Endpoint.TAIL)) ||
                            (first.equals(Endpoint.TAIL) && firstCurrent.equals(Endpoint.ARROW))) {
                        graph.setEndpoint(graphNodePair.getSecond(), graphNodePair.getFirst(), Endpoint.CIRCLE);
                    } else {
                        graph.setEndpoint(graphNodePair.getSecond(), graphNodePair.getFirst(), edge.getEndpoint1());
                    }
                }
                final Endpoint second = edge.getEndpoint2();
                final Endpoint secondCurrent = graph.getEndpoint(graphNodePair.getFirst(), graphNodePair.getSecond());
                if (!second.equals(Endpoint.CIRCLE)) {
                    if ((second.equals(Endpoint.ARROW) && secondCurrent.equals(Endpoint.TAIL)) ||
                            (second.equals(Endpoint.TAIL) && secondCurrent.equals(Endpoint.ARROW))) {
                        graph.setEndpoint(graphNodePair.getFirst(), graphNodePair.getSecond(), Endpoint.CIRCLE);
                    } else {
                        graph.setEndpoint(graphNodePair.getFirst(), graphNodePair.getSecond(), edge.getEndpoint2());
                    }
                }
            }
            for (final Triple triple : pag.getUnderLines()) {
                final Triple graphTriple = new Triple(graph.getNode(triple.getX().getName()), graph.getNode(triple.getY().getName()), graph.getNode(triple.getZ().getName()));
                if (graphTriple.alongPathIn(graph)) {
                    graph.addUnderlineTriple(graphTriple.getX(), graphTriple.getY(), graphTriple.getZ());
                    this.definiteNoncolliders.add(graphTriple);
                }
            }
        }
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

    /*
     * @return variable pairs that are not in the intersection of the variable
     * sets for any two input PAGs
     */

    private List<NodePair> nonIntersection(final Graph graph) {
        final List<Set<String>> varsets = new ArrayList<>();
        for (final Graph inputPag : this.input) {
            final Set<String> varset = new HashSet<>();
            for (final Node node : inputPag.getNodes()) {
                varset.add(node.getName());
            }
            varsets.add(varset);
        }
        final List<NodePair> pairs = new ArrayList();

        for (int i = 0; i < this.variables.size() - 1; i++) {
            for (int j = i + 1; j < this.variables.size(); j++) {
                boolean intersection = false;
                for (final Set<String> varset : varsets) {
                    if (varset.containsAll(Arrays.asList(this.variables.get(i), this.variables.get(j)))) {
                        intersection = true;
                        break;
                    }
                }
                if (!intersection) {
                    pairs.add(new NodePair(graph.getNode(this.variables.get(i)), graph.getNode(this.variables.get(j))));
                }
            }
        }
        return pairs;
    }

    /**
     * Finds the association or seperation sets for every pair of nodes.
     */
    private List<Set<IonIndependenceFacts>> findSepAndAssoc(final Graph graph) {
        final Set<IonIndependenceFacts> separations = new HashSet<>();
        final Set<IonIndependenceFacts> associations = new HashSet<>();
        final List<NodePair> allNodes = allNodePairs(graph.getNodes());

        for (final NodePair pair : allNodes) {
            final Node x = pair.getFirst();
            final Node y = pair.getSecond();

            final List<Node> variables = new ArrayList<>(graph.getNodes());
            variables.remove(x);
            variables.remove(y);

            final List<Set<Node>> subsets = SearchGraphUtils.powerSet(variables);

            final IonIndependenceFacts indep = new IonIndependenceFacts(x, y, new HashSet<List<Node>>());
            final IonIndependenceFacts assoc = new IonIndependenceFacts(x, y, new HashSet<List<Node>>());
            boolean addIndep = false;
            boolean addAssoc = false;

            for (final Graph pag : this.input) {
                for (final Set<Node> subset : subsets) {
                    if (containsAll(pag, subset, pair)) {
                        final Node pagX = pag.getNode(x.getName());
                        final Node pagY = pag.getNode(y.getName());
                        final ArrayList<Node> pagSubset = new ArrayList<>();
                        for (final Node node : subset) {
                            pagSubset.add(pag.getNode(node.getName()));
                        }
                        if (pag.isDSeparatedFrom(pagX, pagY, new ArrayList<>(pagSubset))) {
                            if (!pag.isAdjacentTo(pagX, pagY)) {
                                addIndep = true;
                                indep.addMoreZ(new ArrayList<>(subset));
                            }
                        } else {
                            addAssoc = true;
                            assoc.addMoreZ(new ArrayList<>(subset));
                        }
                    }
                }
            }
            if (addIndep) separations.add(indep);
            if (addAssoc) associations.add(assoc);

        }

        final List<Set<IonIndependenceFacts>> facts = new ArrayList<>(2);
        facts.add(0, separations);
        facts.add(1, associations);
        return facts;
    }

    /**
     * States whether the given graph contains the nodes in the given set and the node pair.
     */
    private boolean containsAll(final Graph g, final Set<Node> nodes, final NodePair pair) {
        final List<String> nodeNames = g.getNodeNames();
        if (!nodeNames.contains(pair.getFirst().getName()) || !nodeNames.contains(pair.getSecond().getName())) {
            return false;
        }
        for (final Node node : nodes) {
            if (!nodeNames.contains(node.getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks given pag against a set of necessary associations to determine if the pag implies an indepedence where one
     * is known to not exist.
     */
    private boolean predictsFalseIndependence(final Set<IonIndependenceFacts> associations, final Graph pag) {
        for (final IonIndependenceFacts assocFact : associations)
            for (final List<Node> conditioningSet : assocFact.getZ())
                if (pag.isDSeparatedFrom(
                        assocFact.getX(), assocFact.getY(), conditioningSet))
                    return true;
        return false;
    }

    /**
     * @return all the triples in the graph that can be either oriented as a collider or non-collider.
     */
    private Set<Triple> getPossibleTriples(final Graph pag) {
        final Set<Triple> possibleTriples = new HashSet<>();
        for (final Triple triple : getAllTriples(pag)) {
            if (pag.isAdjacentTo(triple.getX(), triple.getY()) && pag.isAdjacentTo(triple.getY(), triple.getZ())
                    && !pag.isUnderlineTriple(triple.getX(), triple.getY(), triple.getZ()) &&
                    !this.definiteNoncolliders.contains(triple) &&
                    !pag.isDefCollider(triple.getX(), triple.getY(), triple.getZ())) {
                possibleTriples.add(triple);
            }
        }
        return possibleTriples;
    }

    /**
     * Given a map between sets of conditioned on variables and lists of PossibleDConnectingPaths, finds all the
     * possible GraphChanges which could be used to block said undirectedPaths
     */
    private List<Set<GraphChange>> findChanges(final Map<Collection<Node>, List<PossibleDConnectingPath>> paths) {
        final List<Set<GraphChange>> pagChanges = new ArrayList<>();

        Set<Map.Entry<Collection<Node>, List<PossibleDConnectingPath>>> entries = paths.entrySet();
        /* Loop through each entry, ie each conditioned set of variables. */
        for (final Map.Entry<Collection<Node>, List<PossibleDConnectingPath>> entry : entries) {
            final Collection<Node> conditions = entry.getKey();
            final List<PossibleDConnectingPath> dConnecting = entry.getValue();

            /* loop through each path */
            for (final PossibleDConnectingPath possible : dConnecting) {
                final List<Node> possPath = possible.getPath();
                /* Created with 2*# of undirectedPaths as appoximation. might have to increase size once */
                final Set<GraphChange> pathChanges = new HashSet<>(2 * possPath.size());

                /* find those conditions which are not along the path (used in colider) */
                final List<Node> outsidePath = new ArrayList<>(conditions.size());
                for (final Node condition : conditions) {
                    if (!possPath.contains(condition))
                        outsidePath.add(condition);
                }

                /* Walk through path, node by node */
                for (int i = 0; i < possPath.size() - 1; i++) {
                    final Node current = possPath.get(i);
                    final Node next = possPath.get(i + 1);
                    GraphChange gc;

                    /* for each pair of nodes, add the operation to remove their edge */
                    gc = new GraphChange();
                    gc.addRemove(possible.getPag().getEdge(current, next));
                    pathChanges.add(gc);

                    /* for each triple centered on a node which is an element of the conditioning
                     * set, add the operation to orient as a nonColider around that node */
                    if (conditions.contains(current) && i > 0) {
                        gc = new GraphChange();
                        final Triple nonColider = new Triple(possPath.get(i - 1), current, next);
                        gc.addNonCollider(nonColider);
                        pathChanges.add(gc);
                    }

                    /* for each node on the path not in the conditioning set, make a colider. It
                     * is necessary though to ensure that there are no undirectedPaths implying that a
                     * conditioned variable (even outside the path) is a decendant of a colider */
                    if ((!conditions.contains(current)) && i > 0) {
                        final Triple colider = new Triple(possPath.get(i - 1), current, next);

                        if (possible.getPag().isUnderlineTriple(possPath.get(i - 1), current, next))
                            continue;

                        final Edge edge1 = possible.getPag().getEdge(colider.getX(), colider.getY());
                        final Edge edge2 = possible.getPag().getEdge(colider.getZ(), colider.getY());

                        if (edge1.getNode1().equals(colider.getY())) {
                            if (edge1.getEndpoint1().equals(Endpoint.TAIL)) {
                                continue;
                            }
                        } else if (edge1.getNode2().equals(colider.getY())) {
                            if (edge1.getEndpoint2().equals(Endpoint.TAIL)) {
                                continue;
                            }
                        }

                        if (edge2.getNode1().equals(colider.getY())) {
                            if (edge2.getEndpoint1().equals(Endpoint.TAIL)) {
                                continue;
                            }
                        } else if (edge2.getNode2().equals(colider.getY())) {
                            if (edge2.getEndpoint2().equals(Endpoint.TAIL)) {
                                continue;
                            }
                        }

                        /* Simple case, no conditions outside the path, so just add colider */
                        if (outsidePath.size() == 0) {
                            gc = new GraphChange();
                            gc.addCollider(colider);
                            pathChanges.add(gc);
                            continue;
                        }

                        /* ensure nondecendency in possible path between getModel and each conditioned
                         * variable outside the path */
                        for (final Node outside : outsidePath) {

                            /* list of possible decendant undirectedPaths */

                            List<PossibleDConnectingPath> decendantPaths = new ArrayList<>();
                            decendantPaths
                                    = PossibleDConnectingPath.findDConnectingPaths
                                    (possible.getPag(), current, outside, new ArrayList<Node>());

                            if (decendantPaths.isEmpty()) {
                                gc = new GraphChange();
                                gc.addCollider(colider);
                                pathChanges.add(gc);
                                continue;
                            }

                            /* loop over each possible path which might indicate decendency */
                            for (final PossibleDConnectingPath decendantPDCPath : decendantPaths) {
                                final List<Node> decendantPath = decendantPDCPath.getPath();

                                /* walk down path checking orientation (path may already
                                 * imply non-decendency) and creating changes if need be*/
                                boolean impliesDecendant = true;
                                final Set<GraphChange> colideChanges = new HashSet<>();
                                for (int j = 0; j < decendantPath.size() - 1; j++) {
                                    final Node from = decendantPath.get(j);
                                    // chaneges from +1
                                    final Node to = decendantPath.get(j + 1);
                                    final Edge currentEdge = possible.getPag().getEdge(from, to);

                                    if (currentEdge.getEndpoint1().equals(Endpoint.ARROW)) {
                                        impliesDecendant = false;
                                        break;
                                    }

                                    gc = new GraphChange();
                                    gc.addCollider(colider);
                                    gc.addRemove(currentEdge);
                                    colideChanges.add(gc);

                                    gc = new GraphChange();
                                    gc.addCollider(colider);
                                    gc.addOrient(to, from);
                                    colideChanges.add(gc);
                                }
                                if (impliesDecendant)
                                    pathChanges.addAll(colideChanges);
                            }
                        }
                    }
                }

                pagChanges.add(pathChanges);
            }
        }
        return pagChanges;
    }

    /**
     * Constructs PossRemove, every combination of removing of not removing redudant undirectedPaths
     */
    private List<Graph> possRemove(final Graph pag, final Map<Edge, Boolean> necEdges) {
        // list of edges that can be removed
        final List<Edge> remEdges = new ArrayList<>();
        for (final Edge remEdge : necEdges.keySet()) {
            if (!necEdges.get(remEdge))
                remEdges.add(remEdge);
        }
        // powerset of edges that can be removed
        final PowerSet<Edge> pset = new PowerSet<>(remEdges);
        final List<Graph> possRemove = new ArrayList<>();
        // for each set of edges in the powerset remove edges from graph and add to PossRemove
        for (final Set<Edge> set : pset) {
            final Graph newPag = new EdgeListGraph(pag);
            for (final Edge edge : set) {
                newPag.removeEdge(edge);
            }
            possRemove.add(newPag);
        }
        return possRemove;
    }

    /*
     * Does the final set of orientations after colliders have been oriented
     */

    private void doFinalOrientation(final Graph graph) {
        this.discrimGraphs.clear();
        final Set<Graph> currentDiscrimGraphs = new HashSet<>();
        currentDiscrimGraphs.add(graph);
        while (this.changeFlag) {
            this.changeFlag = false;
            currentDiscrimGraphs.addAll(this.discrimGraphs);
            this.discrimGraphs.clear();
            for (final Graph newGraph : currentDiscrimGraphs) {
                doubleTriangle(newGraph);
                awayFromColliderAncestorCycle(newGraph);
                if (!discrimPaths(newGraph)) {
                    if (this.changeFlag) {
                        this.discrimGraphs.add(newGraph);
                    } else {
                        this.finalResult.add(newGraph);
                    }
                }
            }
            currentDiscrimGraphs.clear();
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
                    doDdpOrientation(graph, l, a, b, c);
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
    private void doDdpOrientation(final Graph graph, final Node l, final Node a, final Node b, final Node c) {
        this.changeFlag = true;
        for (final IonIndependenceFacts iif : this.separations) {
            if ((iif.getX().equals(l) && iif.getY().equals(c)) ||
                    iif.getY().equals(l) && iif.getX().equals(c)) {
                for (final List<Node> condSet : iif.getZ()) {
                    if (condSet.contains(b)) {
                        graph.setEndpoint(c, b, Endpoint.TAIL);
                        this.discrimGraphs.add(graph);
                        return;
                    }
                }
                break;
            }
        }
        final Graph newGraph1 = new EdgeListGraph(graph);
        newGraph1.setEndpoint(a, b, Endpoint.ARROW);
        newGraph1.setEndpoint(c, b, Endpoint.ARROW);
        this.discrimGraphs.add(newGraph1);
        final Graph newGraph2 = new EdgeListGraph(graph);
        newGraph2.setEndpoint(c, b, Endpoint.TAIL);
        this.discrimGraphs.add(newGraph2);
    }

    private Set<Graph> removeMoreSpecific(final Set<Graph> outputPags) {
        final Set<Graph> moreSpecific = new HashSet<>();
        // checks for and removes PAGs tht are more specific, same skeleton and orientations
        // except for one or more arrows or tails where another graph has circles, than other
        // pags in the output graphs that may be produced from the edge removes in step 4
        for (final Graph pag : outputPags) {
            for (final Graph pag2 : outputPags) {
                // if same pag
                if (pag.equals(pag2)) {
                    continue;
                }
                // if different number of edges then continue
                if (pag.getEdges().size() != pag2.getEdges().size()) {
                    continue;
                }
                boolean sameAdjacencies = true;
                for (final Edge edge1 : pag.getEdges()) {
                    if (!pag2.isAdjacentTo(edge1.getNode1(), edge1.getNode2())) {
                        sameAdjacencies = false;
                    }
                }
                if (sameAdjacencies) {
                    // checks to see if pag2 has same arrows and tails
                    boolean arrowstails = true;
                    boolean circles = true;
                    for (final Edge edge2 : pag2.getEdges()) {
                        final Edge edge1 = pag.getEdge(edge2.getNode1(), edge2.getNode2());
                        if (edge1.getNode1().equals(edge2.getNode1())) {
                            if (!edge2.getEndpoint1().equals(Endpoint.CIRCLE)) {
                                if (!edge1.getEndpoint1().equals(edge2.getEndpoint1())) {
                                    arrowstails = false;
                                }
                            } else {
                                if (!edge1.getEndpoint1().equals(edge2.getEndpoint1())) {
                                    circles = false;
                                }
                            }
                            if (!edge2.getEndpoint2().equals(Endpoint.CIRCLE)) {
                                if (!edge1.getEndpoint2().equals(edge2.getEndpoint2())) {
                                    arrowstails = false;
                                }
                            } else {
                                if (!edge1.getEndpoint2().equals(edge2.getEndpoint2())) {
                                    circles = false;
                                }
                            }
                        } else if (edge1.getNode1().equals(edge2.getNode2())) {
                            if (!edge2.getEndpoint1().equals(Endpoint.CIRCLE)) {
                                if (!edge1.getEndpoint2().equals(edge2.getEndpoint1())) {
                                    arrowstails = false;
                                }
                            } else {
                                if (!edge1.getEndpoint2().equals(edge2.getEndpoint1())) {
                                    circles = false;
                                }
                            }
                            if (!edge2.getEndpoint2().equals(Endpoint.CIRCLE)) {
                                if (!edge1.getEndpoint1().equals(edge2.getEndpoint2())) {
                                    arrowstails = false;
                                }
                            } else {
                                if (!edge1.getEndpoint1().equals(edge2.getEndpoint2())) {
                                    circles = false;
                                }
                            }
                        }
                    }
                    if (arrowstails && !circles) {
                        moreSpecific.add(pag);
                        break;
                    }
                }
            }
        }
        for (final Graph pag : moreSpecific) {
            outputPags.remove(pag);
        }
        return outputPags;
    }

    /**
     * Exactly the same as edu.cmu.tetrad.graph.IndependenceFact excepting this class allows for multiple conditioning
     * sets to be associated with a single pair of nodes, which is necessary for the proper ordering of iterations in
     * the ION search.
     */
    private final class IonIndependenceFacts {
        private final Node x;
        private final Node y;
        private final Collection<List<Node>> z;

        /**
         * Constructs a triple of nodes.
         */
        public IonIndependenceFacts(final Node x, final Node y, final Collection<List<Node>> z) {
            if (x == null || y == null || z == null) {
                throw new NullPointerException();
            }

            this.x = x;
            this.y = y;
            this.z = z;
        }

        public final Node getX() {
            return this.x;
        }

        public final Node getY() {
            return this.y;
        }

        public final Collection<List<Node>> getZ() {
            return this.z;
        }

        public void addMoreZ(final List<Node> moreZ) {
            this.z.add(moreZ);
        }

        public final int hashCode() {
            int hash = 17;
            hash += 19 * this.x.hashCode() * this.y.hashCode();
            hash += 23 * this.z.hashCode();
            return hash;
        }

        public final boolean equals(final Object obj) {
            if (!(obj instanceof IonIndependenceFacts)) {
                return false;
            }

            final IonIndependenceFacts fact = (IonIndependenceFacts) obj;
            return (this.x.equals(fact.x) && this.y.equals(fact.y) &&
                    this.z.equals(fact.z))
                    || (this.x.equals(fact.y) & this.y.equals(fact.x) &&
                    this.z.equals(fact.z));
        }

        public String toString() {
            return "I(" + this.x + ", " + this.y + " | " + this.z + ")";
        }
    }

    /**
     * A PowerSet constructed with a collection with elements of type E can construct an Iterator which enumerates all
     * possible subsets (of type Collection<E>) of the collection used to construct the PowerSet.
     *
     * @param <E> The type of elements in the Collection passed to the constructor.
     * @author pingel
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

    public static List<List<Node>> treks(final Graph graph, final Node node1, final Node node2) {
        final List<List<Node>> paths = new LinkedList<>();
        Ion.treks(graph, node1, node2, new LinkedList<Node>(), paths);
        return paths;
    }

    /**
     * Constucts the list of treks between node1 and node2.
     */
    private static void treks(final Graph graph, final Node node1, final Node node2,
                              final LinkedList<Node> path, final List<List<Node>> paths) {
        path.addLast(node1);

        for (final Edge edge : graph.getEdges(node1)) {
            final Node next = Edges.traverse(node1, edge);

            if (next == null) {
                continue;
            }

            if (path.size() > 1) {
                final Node node0 = path.get(path.size() - 2);

                if (next == node0) {
                    continue;
                }

                if (graph.isDefCollider(node0, node1, next)) {
                    continue;
                }
            }

            if (next == node2) {
                final LinkedList<Node> _path = new LinkedList<>(path);
                _path.add(next);
                paths.add(_path);
                continue;
            }

            if (path.contains(next)) {
                continue;
            }

            Ion.treks(graph, next, node2, path, paths);
        }

        path.removeLast();
    }

}



