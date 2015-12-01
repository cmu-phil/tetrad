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
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements the ION (Integration of Overlapping Networks) algorithm for distributed causal inference. The algorithm
 * takes as input a set of PAGs (presumably learned using a local learning algorithm) over variable sets that may have
 * some variables in common and others not in common. The algorithm returns a complete set of PAGs over every variable
 * form an input PAG that are consistent (same d-separations and d-connections) with every input PAG.
 *
 * @author Robert Tillman
 */

public class IonJoeModifications {

    // prune using path length
    private boolean pathLengthSearch = true;

    // prune using adjacencies
    private boolean adjacencySearch = false;

    /**
     * The input PAGs being to be intergrated, possibly FCI outputs.
     */
    private List<Graph> input = new ArrayList<Graph>();

    /**
     * The output PAGs over all variables consistent with the input PAGs
     */
    private List<Graph> output = new ArrayList<Graph>();

    /**
     * All the variables being integrated from the input PAGs
     */
    private List<String> variables = new ArrayList<String>();

    /**
     * Definite noncolliders
     */
    private Set<Triple> definiteNoncolliders = new HashSet<Triple>();

    /**
     * separations and associations found in the input PAGs
     */
    private Set<IonIndependenceFacts> separations;
    private Set<IonIndependenceFacts> associations;

    /**
     * tracks changes for final orientations orientation methods
     */
    private boolean changeFlag = true;
    private Set<Graph> discrimGraphs = new HashSet<Graph>();
    private Set<Graph> finalResult = new HashSet<Graph>();

    // running runtime and time and size information for hitting sets
    private List<Integer> recGraphs = new ArrayList<Integer>();
    private List<Double> recHitTimes = new ArrayList<Double>();
    private double runtime;

    // maximum memory usage
    private double maxMemory;

    // knowledge if available.
    private IKnowledge knowledge = new Knowledge2();

    //============================= Constructor ============================//


    /**
     * Constructs a new instance of the ION search from the input PAGs
     *
     * @param pags The PAGs to be integrated
     */
    public IonJoeModifications(List<Graph> pags) {
        for (Graph pag : pags) {
            this.input.add(pag);

        }
        for (Graph pag : input) {
            for (Node node : pag.getNodes()) {
                if (!variables.contains(node.getName())) {
                    this.variables.add(node.getName());
                }
            }
            for (Triple triple : getAllTriples(pag)) {
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
    public void setPathLengthSearch(boolean b) {
        pathLengthSearch = b;
    }

    /**
     * Sets adjacency search on or off
     */
    public void setAdjacencySearch(boolean b) {
        adjacencySearch = b;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = knowledge;
    }

    /**
     * Begins the ION search procedure, described at each step
     */
    public List<Graph> search() {

        long start = System.currentTimeMillis();
        TetradLogger.getInstance().log("info", "Starting ION Search.");
        logGraphs("\nInitial Pags: ", this.input);
        TetradLogger.getInstance().log("info", "Transfering local information.");
        long steps = System.currentTimeMillis();

        /**
         * Step 1 - Create the empty graph
         */
        List<Node> varNodes = new ArrayList<Node>();
        for (String varName : variables) {
            varNodes.add(new GraphNode(varName));
        }
        Graph graph = new EdgeListGraph(varNodes);

        /**
         * Step 2 - Transfer local information from the PAGs (adjacencies
         * and edge orientations)
         */
        // transfers edges from each graph and finds definite noncolliders
        transferLocal(graph);
        // adds edges for variables never jointly measured
        for (NodePair pair : nonIntersection(graph)) {
            graph.addEdge(new Edge(pair.getFirst(), pair.getSecond(), Endpoint.CIRCLE, Endpoint.CIRCLE));
        }
        TetradLogger.getInstance().log("info", "Steps 1-2: " + (System.currentTimeMillis() - steps) / 1000. + "s");
        System.out.println("step2");
        System.out.println(graph);

        /**
         * Step 3
         *
         * Branch and prune step that blocks problematic undirectedPaths, possibly d-connecting undirectedPaths
         */
        steps = System.currentTimeMillis();
        Queue<Graph> searchPags = new LinkedList<Graph>();
        // place graph constructed in step 2 into the queue
        searchPags.offer(graph);
        // get d-separations and d-connections
        List<Set<IonIndependenceFacts>> sepAndAssoc = findSepAndAssoc(graph);
        this.separations = sepAndAssoc.get(0);
        this.associations = sepAndAssoc.get(1);
        Map<Collection<Node>, List<PossibleDConnectingPath>> paths;
//        Queue<Graph> step3PagsSet = new LinkedList<Graph>();
        HashSet<Graph> step3PagsSet = new HashSet<Graph>();
        Set<Graph> reject = new HashSet<Graph>();
        // if no d-separations, nothing left to search
        if (separations.isEmpty()) {
            // makes orientations preventing definite noncolliders from becoming colliders
            // do final orientations
//            doFinalOrientation(graph);
            step3PagsSet.add(graph);
        }
        // sets length to iterate once if search over path lengths not enabled, otherwise set to 2
        int numNodes = graph.getNumNodes();
        int pl = numNodes - 1;
        if (pathLengthSearch) {
            pl = 2;
        }
        // iterates over path length, then adjacencies
        for (int l = pl; l < numNodes; l++) {
            if (pathLengthSearch) {
                TetradLogger.getInstance().log("info", "Braching over path lengths: " + l + " of " + (numNodes - 1));
            }
            int seps = separations.size();
            int currentSep = 1;
            int numAdjacencies = separations.size();
            for (IonIndependenceFacts fact : separations) {
                if (adjacencySearch) {
                    TetradLogger.getInstance().log("info", "Braching over path nonadjacencies: " + currentSep + " of " + numAdjacencies);
                }
                seps--;
                // uses two queues to keep up with which PAGs are being iterated and which have been
                // accepted to be iterated over in the next iteration of the above for loop
                searchPags.addAll(step3PagsSet);
                recGraphs.add(searchPags.size());
                step3PagsSet.clear();
                while (!searchPags.isEmpty()) {
                    System.out.println("ION Step 3 size: " + searchPags.size());
                    double currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    if (currentUsage > maxMemory) maxMemory = currentUsage;
                    // deques first PAG from searchPags
                    Graph pag = searchPags.poll();
                    // Part 3.a - finds possibly d-connecting undirectedPaths between each pair of nodes
                    // known to be d-separated
                    List<PossibleDConnectingPath> dConnections = new ArrayList<PossibleDConnectingPath>();
                    // checks to see if looping over adjacencies
                    if (adjacencySearch) {
                        for (Collection<Node> conditions : fact.getZ()) {
                            // checks to see if looping over path lengths
                            if (pathLengthSearch) {
                                dConnections.addAll(PossibleDConnectingPath.findDConnectingPathsOfLength
                                        (pag, fact.getX(), fact.getY(), conditions, l));
                            } else {
                                dConnections.addAll(PossibleDConnectingPath.findDConnectingPaths
                                        (pag, fact.getX(), fact.getY(), conditions));
                            }
                        }
                    } else {
                        for (IonIndependenceFacts allfact : separations) {
                            for (Collection<Node> conditions : allfact.getZ()) {
                                // checks to see if looping over path lengths
                                if (pathLengthSearch) {
                                    dConnections.addAll(PossibleDConnectingPath.findDConnectingPathsOfLength
                                            (pag, allfact.getX(), allfact.getY(), conditions, l));
                                } else {
                                    dConnections.addAll(PossibleDConnectingPath.findDConnectingPaths
                                            (pag, allfact.getX(), allfact.getY(), conditions));
                                }
                            }
                        }
                    }
                    // accept PAG go to next PAG if no possibly d-connecting undirectedPaths
                    if (dConnections.isEmpty()) {
//                        doFinalOrientation(pag);
//                        Graph p = screenForKnowledge(pag);
//                        if (p != null) step3PagsSet.add(p);
                        step3PagsSet.add(pag);
                        continue;
                    }
                    // maps conditioning sets to list of possibly d-connecting undirectedPaths
                    paths = new HashMap<Collection<Node>, List<PossibleDConnectingPath>>();
                    for (PossibleDConnectingPath path : dConnections) {
                        List<PossibleDConnectingPath> p = paths.get(path.getConditions());
                        if (p == null) {
                            p = new LinkedList<PossibleDConnectingPath>();
                        }
                        p.add(path);
                        paths.put(path.getConditions(), p);
                    }
                    // Part 3.b - finds minimal graphical changes to block possibly d-connecting undirectedPaths
                    List<Set<GraphChange>> possibleChanges = new ArrayList<Set<GraphChange>>();
                    for (Set<GraphChange> changes : findChanges(paths)) {
                        Set<GraphChange> newChanges = new HashSet<GraphChange>();
                        for (GraphChange gc : changes) {
                            boolean okay = true;
                            for (Triple collider : gc.getColliders()) {

                                if (pag.isUnderlineTriple(collider.getX(), collider.getY(), collider.getZ())) {
                                    okay = false;
                                    break;
                                }

                            }
                            if (!okay) {
                                continue;
                            }
                            for (Triple collider : gc.getNoncolliders()) {
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
                    float starthitset = System.currentTimeMillis();
                    Collection<GraphChange> hittingSets = IonHittingSet.findHittingSet(possibleChanges);
                    recHitTimes.add((System.currentTimeMillis() - starthitset) / 1000.);
                    // Part 3.c - checks the newly constructed graphs from 3.b and rejects those that
                    // cycles or produce independencies known not to occur from the input PAGs or
                    // include undirectedPaths from definite nonancestors
                    for (GraphChange gc : hittingSets) {
                        boolean badhittingset = false;
                        for (Edge edge : gc.getRemoves()) {
                            Node node1 = edge.getNode1();
                            Node node2 = edge.getNode2();
                            Set<Triple> triples = new HashSet<Triple>();
                            triples.addAll(gc.getColliders());
                            triples.addAll(gc.getNoncolliders());
                            if (triples.size() != (gc.getColliders().size() + gc.getNoncolliders().size())) {
                                badhittingset = true;
                                break;
                            }
                            for (Triple triple : triples) {
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
                            for (NodePair pair : gc.getOrients()) {
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
                            for (NodePair pair : gc.getOrients()) {
                                for (Triple triple : gc.getNoncolliders()) {
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
                        Graph changed = gc.applyTo(pag);
                        // if graph change has already been rejected move on to next graph
                        if (reject.contains(changed)) {
                            continue;
                        }
                        // if graph change has already been accepted move on to next graph
                        if (step3PagsSet.contains(changed)) {
                            continue;
                        }
                        // reject if null, predicts false independencies or has cycle
                        if (predictsFalseIndependence(associations, changed)
                                || changed.existsDirectedCycle()) {
                            reject.add(changed);
                        }
                        // makes orientations preventing definite noncolliders from becoming colliders
                        // do final orientations
//                        doFinalOrientation(changed);
                        // now add graph to queue

//                        Graph p = screenForKnowledge(changed);
//                        if (p != null) step3PagsSet.add(p);
                        step3PagsSet.add(changed);
                    }
                }
                // exits loop if not looping over adjacencies
                if (!adjacencySearch) {
                    break;
                }
            }
        }
        TetradLogger.getInstance().log("info", "Step 3: " + (System.currentTimeMillis() - steps) / 1000. + "s");
        Queue<Graph> step3Pags = new LinkedList<Graph>(step3PagsSet);

        /**
         * Step 4
         *
         * Finds redundant undirectedPaths and uses this information to expand the list
         * of possible graphs
         */
        steps = System.currentTimeMillis();
        Map<Edge, Boolean> necEdges;
        Set<Graph> outputPags = new HashSet<Graph>();

        while (!step3Pags.isEmpty()) {
            Graph pag = step3Pags.poll();
            necEdges = new HashMap<Edge, Boolean>();
            // Step 4.a - if x and y are known to be unconditionally associated and there is
            // exactly one trek between them, mark each edge on that trek as necessary and
            // make the tiples on the trek definite noncolliders
            // initially mark each edge as not necessary
            for (Edge edge : pag.getEdges()) {
                necEdges.put(edge, false);
            }
            // look for unconditional associations
            for (IonIndependenceFacts fact : associations) {
                for (List<Node> nodes : fact.getZ()) {
                    if (nodes.isEmpty()) {
                        List<List<Node>> treks = treks(pag, fact.x, fact.y);
                        if (treks.size() == 1) {
                            List<Node> trek = treks.get(0);
                            List<Triple> triples = new ArrayList<Triple>();
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
            List<Graph> possRemovePags = possRemove(pag, necEdges);
            double currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            if (currentUsage > maxMemory) maxMemory = currentUsage;
            for (Graph newPag : possRemovePags) {
                elimTreks = false;
                // looks for unconditional associations
                for (IonIndependenceFacts fact : associations) {
                    for (List<Node> nodes : fact.getZ()) {
                        if (nodes.isEmpty()) {
                            if (treks(newPag, fact.x, fact.y).isEmpty()) {
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
//        outputPags = applyKnowledge(outputPags);

        TetradLogger.getInstance().log("info", "Step 4: " + (System.currentTimeMillis() - steps) / 1000. + "s");

        /**
         * Step 5
         *
         * Generate the Markov equivalence classes for graphs and accept only
         * those that do not predict false d-separations
         */
        steps = System.currentTimeMillis();
        Set<Graph> outputSet = new HashSet<Graph>();
        for (Graph pag : outputPags) {
            Set<Triple> unshieldedPossibleColliders = new HashSet<Triple>();
            for (Triple triple : getPossibleTriples(pag)) {
                if (!pag.isAdjacentTo(triple.getX(), triple.getZ())) {
                    unshieldedPossibleColliders.add(triple);
                }
            }

            PowerSet<Triple> pset = new PowerSet<Triple>(unshieldedPossibleColliders);
            for (Set<Triple> set : pset) {
                Graph newGraph = new EdgeListGraph(pag);
                for (Triple triple : set) {
                    newGraph.setEndpoint(triple.getX(), triple.getY(), Endpoint.ARROW);
                    newGraph.setEndpoint(triple.getZ(), triple.getY(), Endpoint.ARROW);
                }
                doFinalOrientation(newGraph);
            }
            for (Graph outputPag : finalResult) {
                if (!predictsFalseIndependence(associations, outputPag)) {
                    Set<Triple> underlineTriples = new HashSet<Triple>(outputPag.getUnderLines());
                    for (Triple triple : underlineTriples) {
                        outputPag.removeUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
                    }
                    outputSet.add(outputPag);
                }
            }
        }

//        outputSet = applyKnowledge(outputSet);
        outputSet = checkPaths(outputSet);

        output.addAll(outputSet);
        TetradLogger.getInstance().log("info", "Step 5: " + (System.currentTimeMillis() - steps) / 1000. + "s");
        runtime = ((System.currentTimeMillis() - start) / 1000.);
        logGraphs("\nReturning output (" + output.size() + " Graphs):", output);
        double currentUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        if (currentUsage > maxMemory) maxMemory = currentUsage;
        return output;
    }

    // returns total runtime and times for hitting set calculations

    public List<String> getRuntime() {
        double totalhit = 0;
        double longesthit = 0;
        double averagehit = 0;
        for (Double i : recHitTimes) {
            totalhit += i;
            averagehit += i / recHitTimes.size();
            if (i > longesthit) {
                longesthit = i;
            }
        }
        List<String> list = new ArrayList<String>();
        list.add(Double.toString(runtime));
        list.add(Double.toString(totalhit));
        list.add(Double.toString(longesthit));
        list.add(Double.toString(averagehit));
        return list;
    }

    // returns the maximum memory used in a run of ION

    public double getMaxMemUsage() {
        return maxMemory;
    }

    // return hitting set sizes

    public List<Integer> getIterations() {
        int totalit = 0;
        int largestit = 0;
        int averageit = 0;
        for (Integer i : recGraphs) {
            totalit += i;
            averageit += i / recGraphs.size();
            if (i > largestit) {
                largestit = i;
            }
        }
        List<Integer> list = new ArrayList<Integer>();
        list.add(totalit);
        list.add(largestit);
        list.add(averageit);
        return list;
    }

    // summarizes time and hitting set time and size information for latex

    public String getStats() {
        String stats = "Total running time:  " + runtime + "\\\\";
        int totalit = 0;
        int largestit = 0;
        int averageit = 0;
        for (Integer i : recGraphs) {
            totalit += i;
            averageit += i;
            if (i > largestit) {
                largestit = i;
            }
        }
        averageit /= recGraphs.size();
        double totalhit = 0;
        double longesthit = 0;
        double averagehit = 0;
        for (Double i : recHitTimes) {
            totalhit += i;
            averagehit += i / recHitTimes.size();
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
    private void logGraphs(String message, List<? extends Graph> graphs) {
        if (message != null) {
            TetradLogger.getInstance().log("graph", message);
        }
        for (Graph graph : graphs) {
            TetradLogger.getInstance().log("graph", graph.toString());
        }
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
     * Finds all node pairs that are not adjacent in an input graph
     */
    private Set<NodePair> nonadjacencies(Graph graph) {
        Set<NodePair> nonadjacencies = new HashSet<NodePair>();
        for (Graph inputPag : input) {
            for (NodePair pair : allNodePairs(inputPag.getNodes())) {
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

    private void transferLocal(Graph graph) {
        Set<NodePair> nonadjacencies = nonadjacencies(graph);
        for (Graph pag : input) {
            for (Edge edge : pag.getEdges()) {
                NodePair graphNodePair = new NodePair(graph.getNode(edge.getNode1().getName()), graph.getNode(edge.getNode2().getName()));
                if (nonadjacencies.contains(graphNodePair)) {
                    continue;
                }
                if (!graph.isAdjacentTo(graphNodePair.getFirst(), graphNodePair.getSecond())) {
                    graph.addEdge(new Edge(graphNodePair.getFirst(), graphNodePair.getSecond(), edge.getEndpoint1(), edge.getEndpoint2()));
                    continue;
                }
                Endpoint first = edge.getEndpoint1();
                Endpoint firstCurrent = graph.getEndpoint(graphNodePair.getSecond(), graphNodePair.getFirst());
                if (!first.equals(Endpoint.CIRCLE)) {
                    if ((first.equals(Endpoint.ARROW) && firstCurrent.equals(Endpoint.TAIL)) ||
                            (first.equals(Endpoint.TAIL) && firstCurrent.equals(Endpoint.ARROW))) {
                        graph.setEndpoint(graphNodePair.getSecond(), graphNodePair.getFirst(), Endpoint.CIRCLE);
                    } else {
                        graph.setEndpoint(graphNodePair.getSecond(), graphNodePair.getFirst(), edge.getEndpoint1());
                    }
                }
                Endpoint second = edge.getEndpoint2();
                Endpoint secondCurrent = graph.getEndpoint(graphNodePair.getFirst(), graphNodePair.getSecond());
                if (!second.equals(Endpoint.CIRCLE)) {
                    if ((second.equals(Endpoint.ARROW) && secondCurrent.equals(Endpoint.TAIL)) ||
                            (second.equals(Endpoint.TAIL) && secondCurrent.equals(Endpoint.ARROW))) {
                        graph.setEndpoint(graphNodePair.getFirst(), graphNodePair.getSecond(), Endpoint.CIRCLE);
                    } else {
                        graph.setEndpoint(graphNodePair.getFirst(), graphNodePair.getSecond(), edge.getEndpoint2());
                    }
                }
            }
            for (Triple triple : pag.getUnderLines()) {
                Triple graphTriple = new Triple(graph.getNode(triple.getX().getName()), graph.getNode(triple.getY().getName()), graph.getNode(triple.getZ().getName()));
                if (graphTriple.alongPathIn(graph)) {
                    graph.addUnderlineTriple(graphTriple.getX(), graphTriple.getY(), graphTriple.getZ());
                    definiteNoncolliders.add(graphTriple);
                }
            }
        }
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

    /*
     * @return variable pairs that are not in the intersection of the variable
     * sets for any two input PAGs
     */

    private List<NodePair> nonIntersection(Graph graph) {
        List<Set<String>> varsets = new ArrayList<Set<String>>();
        for (Graph inputPag : input) {
            Set<String> varset = new HashSet<String>();
            for (Node node : inputPag.getNodes()) {
                varset.add(node.getName());
            }
            varsets.add(varset);
        }
        List<NodePair> pairs = new ArrayList();

        for (int i = 0; i < variables.size() - 1; i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                boolean intersection = false;
                for (Set<String> varset : varsets) {
                    if (varset.containsAll(Arrays.asList(variables.get(i), variables.get(j)))) {
                        intersection = true;
                        break;
                    }
                }
                if (!intersection) {
                    pairs.add(new NodePair(graph.getNode(variables.get(i)), graph.getNode(variables.get(j))));
                }
            }
        }
        return pairs;
    }

    /**
     * Finds the association or seperation sets for every pair of nodes.
     */
    private List<Set<IonIndependenceFacts>> findSepAndAssoc(Graph graph) {
        Set<IonIndependenceFacts> separations = new HashSet<IonIndependenceFacts>();
        Set<IonIndependenceFacts> associations = new HashSet<IonIndependenceFacts>();
        List<NodePair> allNodes = allNodePairs(graph.getNodes());

        for (NodePair pair : allNodes) {
            Node x = pair.getFirst();
            Node y = pair.getSecond();

            List<Node> variables = new ArrayList<Node>(graph.getNodes());
            variables.remove(x);
            variables.remove(y);

            List<Set<Node>> subsets = SearchGraphUtils.powerSet(variables);

            IonIndependenceFacts indep = new IonIndependenceFacts(x, y, new HashSet<List<Node>>());
            IonIndependenceFacts assoc = new IonIndependenceFacts(x, y, new HashSet<List<Node>>());
            boolean addIndep = false;
            boolean addAssoc = false;

            for (Graph pag : input) {
                for (Set<Node> subset : subsets) {
                    if (containsAll(pag, subset, pair)) {
                        Node pagX = pag.getNode(x.getName());
                        Node pagY = pag.getNode(y.getName());
                        ArrayList<Node> pagSubset = new ArrayList<Node>();
                        for (Node node : subset) {
                            pagSubset.add(pag.getNode(node.getName()));
                        }
                        if (pag.isDSeparatedFrom(pagX, pagY, new ArrayList<Node>(pagSubset))) {
                            if (!pag.isAdjacentTo(pagX, pagY)) {
                                addIndep = true;
                                indep.addMoreZ(new ArrayList<Node>(subset));
                            }
                        } else {
                            addAssoc = true;
                            assoc.addMoreZ(new ArrayList<Node>(subset));
                        }
                    }
                }
            }
            if (addIndep) separations.add(indep);
            if (addAssoc) associations.add(assoc);

        }

        List<Set<IonIndependenceFacts>> facts = new ArrayList<Set<IonIndependenceFacts>>(2);
        facts.add(0, separations);
        facts.add(1, associations);
        return facts;
    }

    /**
     * States whether the given graph contains the nodes in the given set and the node pair.
     */
    private boolean containsAll(Graph g, Set<Node> nodes, NodePair pair) {
        List<String> nodeNames = g.getNodeNames();
        if (!nodeNames.contains(pair.getFirst().getName()) || !nodeNames.contains(pair.getSecond().getName())) {
            return false;
        }
        for (Node node : nodes) {
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
    private boolean predictsFalseIndependence(Set<IonIndependenceFacts> associations, Graph pag) {
        for (IonIndependenceFacts assocFact : associations)
            for (List<Node> conditioningSet : assocFact.getZ())
                if (pag.isDSeparatedFrom(
                        assocFact.getX(), assocFact.getY(), conditioningSet))
                    return true;
        return false;
    }

    /**
     * @return all the triples in the graph that can be either oriented as a collider or non-collider.
     */
    private Set<Triple> getPossibleTriples(Graph pag) {
        Set<Triple> possibleTriples = new HashSet<Triple>();
        for (Triple triple : getAllTriples(pag)) {
            if (pag.isAdjacentTo(triple.getX(), triple.getY()) && pag.isAdjacentTo(triple.getY(), triple.getZ())
                    && !pag.isUnderlineTriple(triple.getX(), triple.getY(), triple.getZ()) &&
                    !definiteNoncolliders.contains(triple) &&
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
    private List<Set<GraphChange>> findChanges(Map<Collection<Node>, List<PossibleDConnectingPath>> paths) {
        List<Set<GraphChange>> pagChanges = new ArrayList<Set<GraphChange>>();

        Set<Map.Entry<Collection<Node>, List<PossibleDConnectingPath>>> entries = paths.entrySet();
        /* Loop through each entry, ie each conditioned set of variables. */
        for (Map.Entry<Collection<Node>, List<PossibleDConnectingPath>> entry : entries) {
            Collection<Node> conditions = entry.getKey();
            List<PossibleDConnectingPath> dConnecting = entry.getValue();

            /* loop through each path */
            for (PossibleDConnectingPath possible : dConnecting) {
                List<Node> possPath = possible.getPath();
                /* Created with 2*# of undirectedPaths as appoximation. might have to increase size once */
                Set<GraphChange> pathChanges = new HashSet<GraphChange>(2 * possPath.size());

                /* find those conditions which are not along the path (used in colider) */
                List<Node> outsidePath = new ArrayList<Node>(conditions.size());
                for (Node condition : conditions) {
                    if (!possPath.contains(condition))
                        outsidePath.add(condition);
                }

                /* Walk through path, node by node */
                for (int i = 0; i < possPath.size() - 1; i++) {
                    Node current = possPath.get(i);
                    Node next = possPath.get(i + 1);
                    GraphChange gc;

                    /* for each pair of nodes, add the operation to remove their edge */
                    gc = new GraphChange();
                    gc.addRemove(possible.getPag().getEdge(current, next));
                    pathChanges.add(gc);

                    /* for each triple centered on a node which is an element of the conditioning
                     * set, add the operation to orient as a nonColider around that node */
                    if (conditions.contains(current) && i > 0) {
                        gc = new GraphChange();
                        Triple nonColider = new Triple(possPath.get(i - 1), current, next);
                        gc.addNonCollider(nonColider);
                        pathChanges.add(gc);
                    }

                    /* for each node on the path not in the conditioning set, make a colider. It
                     * is necessary though to ensure that there are no undirectedPaths implying that a
                     * conditioned variable (even outside the path) is a decendant of a colider */
                    if ((!conditions.contains(current)) && i > 0) {
                        Triple colider = new Triple(possPath.get(i - 1), current, next);

                        if (possible.getPag().isUnderlineTriple(possPath.get(i - 1), current, next))
                            continue;

                        Edge edge1 = possible.getPag().getEdge(colider.getX(), colider.getY());
                        Edge edge2 = possible.getPag().getEdge(colider.getZ(), colider.getY());

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
                        for (Node outside : outsidePath) {

                            /* list of possible decendant undirectedPaths */

                            List<PossibleDConnectingPath> decendantPaths = new ArrayList<PossibleDConnectingPath>();
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
                            for (PossibleDConnectingPath decendantPDCPath : decendantPaths) {
                                List<Node> decendantPath = decendantPDCPath.getPath();

                                /* walk down path checking orientation (path may already
                                 * imply non-decendency) and creating changes if need be*/
                                boolean impliesDecendant = true;
                                Set<GraphChange> colideChanges = new HashSet<GraphChange>();
                                for (int j = 0; j < decendantPath.size() - 1; j++) {
                                    Node from = decendantPath.get(j);
                                    // chaneges from +1
                                    Node to = decendantPath.get(j + 1);
                                    Edge currentEdge = possible.getPag().getEdge(from, to);

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
    private List<Graph> possRemove(Graph pag, Map<Edge, Boolean> necEdges) {
        // list of edges that can be removed
        List<Edge> remEdges = new ArrayList<Edge>();
        for (Edge remEdge : necEdges.keySet()) {
            if (!necEdges.get(remEdge))
                remEdges.add(remEdge);
        }
        // powerset of edges that can be removed
        PowerSet<Edge> pset = new PowerSet<Edge>(remEdges);
        List<Graph> possRemove = new ArrayList<Graph>();
        // for each set of edges in the powerset remove edges from graph and add to PossRemove
        for (Set<Edge> set : pset) {
            Graph newPag = new EdgeListGraph(pag);
            for (Edge edge : set) {
                newPag.removeEdge(edge);
            }
            possRemove.add(newPag);
        }
        return possRemove;
    }

    /*
     * Does the final set of orientations after colliders have been oriented
     */

    private void doFinalOrientation(Graph graph) {
        discrimGraphs.clear();
        Set<Graph> currentDiscrimGraphs = new HashSet<Graph>();
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
                        finalResult.add(newGraph);
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
//                awayFromCollider(graph, A, B, C);
//                awayFromCollider(graph, C, B, A);
//                awayFromAncestor(graph, A, B, C);
//                awayFromAncestor(graph, C, B, A);
//                awayFromCycle(graph, A, B, C);
//                awayFromCycle(graph, C, B, A);
                ruleR1(A, B, C, graph);
                ruleR1(C, B, A, graph);
                ruleR2(A, B, C, graph);
                ruleR2(C, B, A, graph);

            }
        }
    }

    /// R1, away from collider

    private void ruleR1(Node a, Node b, Node c, Graph graph) {
        if (graph.isAdjacentTo(a, c)) {
            return;
        }

        if (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
            if (!isArrowpointAllowed(graph, b, c)) {
                return;
            }

            graph.setEndpoint(c, b, Endpoint.TAIL);
            graph.setEndpoint(b, c, Endpoint.ARROW);
        }
    }

    //if Ao->c and a-->b-->c, then a-->c
    // Zhang's rule R2, awy from ancestor.

    private void ruleR2(Node a, Node b, Node c, Graph graph) {
        if (!graph.isAdjacentTo(a, c)) {
            return;
        }

        if (graph.getEndpoint(b, a) == Endpoint.TAIL && graph.getEndpoint(a, b) == Endpoint.ARROW
                && graph.getEndpoint(b, c) == Endpoint.ARROW && graph.getEndpoint(a, c) == Endpoint.CIRCLE) {
            if (!isArrowpointAllowed(graph, a, c)) {
                return;
            }

            graph.setEndpoint(a, c, Endpoint.ARROW);
        } else if (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.TAIL
                && graph.getEndpoint(b, c) == Endpoint.ARROW && graph.getEndpoint(a, c) == Endpoint.CIRCLE
                ) {
            if (!isArrowpointAllowed(graph, a, c)) {
                return;
            }

            graph.setEndpoint(a, c, Endpoint.ARROW);
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
    private void doDdpOrientation(Graph graph, Node l, Node a, Node b, Node c) {
        changeFlag = true;
        for (IonIndependenceFacts iif : separations) {
            if ((iif.getX().equals(l) && iif.getY().equals(c)) ||
                    iif.getY().equals(l) && iif.getX().equals(c)) {
                for (List<Node> condSet : iif.getZ()) {
                    if (condSet.contains(b)) {
                        graph.setEndpoint(c, b, Endpoint.TAIL);
                        discrimGraphs.add(graph);
                        return;
                    }
                }
                break;
            }
        }
        Graph newGraph1 = new EdgeListGraph(graph);
        newGraph1.setEndpoint(a, b, Endpoint.ARROW);
        newGraph1.setEndpoint(c, b, Endpoint.ARROW);
        discrimGraphs.add(newGraph1);
        Graph newGraph2 = new EdgeListGraph(graph);
        newGraph2.setEndpoint(c, b, Endpoint.TAIL);
        discrimGraphs.add(newGraph2);
    }

    private Set<Graph> removeMoreSpecific(Set<Graph> outputPags) {
        Set<Graph> moreSpecific = new HashSet<Graph>();
        // checks for and removes PAGs tht are more specific, same skeleton and orientations
        // except for one or more arrows or tails where another graph has circles, than other
        // pags in the output graphs that may be produced from the edge removes in step 4
        for (Graph pag : outputPags) {
            for (Graph pag2 : outputPags) {
                // if same pag
                if (pag.equals(pag2)) {
                    continue;
                }
                // if different number of edges then continue
                if (pag.getEdges().size() != pag2.getEdges().size()) {
                    continue;
                }
                boolean sameAdjacencies = true;
                for (Edge edge1 : pag.getEdges()) {
                    if (!pag2.isAdjacentTo(edge1.getNode1(), edge1.getNode2())) {
                        sameAdjacencies = false;
                    }
                }
                if (sameAdjacencies) {
                    // checks to see if pag2 has same arrows and tails
                    boolean arrowstails = true;
                    boolean circles = true;
                    for (Edge edge2 : pag2.getEdges()) {
                        Edge edge1 = pag.getEdge(edge2.getNode1(), edge2.getNode2());
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
        for (Graph pag : moreSpecific) {
            outputPags.remove(pag);
        }
        return outputPags;
    }

    private Set<Graph> checkPaths(Set<Graph> pags) {
        HashSet<Graph> pagsOut = new HashSet<Graph>();

        for (Graph pag : pags) {
            boolean allAccountFor = true;

            GRAPH:
            for (Graph inGraph : input) {
                for (Edge edge : inGraph.getEdges()) {
                    Node node1 = pag.getNode(edge.getNode1().getName());
                    Node node2 = pag.getNode(edge.getNode2().getName());

                    if (Edges.isDirectedEdge(edge)) {
                        if (!pag.existsSemiDirectedPathFromTo(node1, Collections.singleton(node2))) {
                            allAccountFor = false;
                            break GRAPH;
                        }
                    }
                    if (/*!pag.existsTrek(node1, node2) ||*/ Edges.isPartiallyOrientedEdge(edge)) {
                        if (pag.existsSemiDirectedPathFromTo(node2, Collections.singleton(node1))) {
                            allAccountFor = false;
                            break GRAPH;
                        }
                    }
//                    if (Edges.isNondirectedEdge(edge)) {
//                        if (!pag.existsTrek(node1, node2)) {
//                            allAccountFor = false;
//                            break GRAPH;
//                        }
//                    }
                }
            }

            if (allAccountFor) {
                pagsOut.add(pag);
            }
        }

        return pagsOut;
    }

    /**
     * Exactly the same as edu.cmu.tetrad.graph.IndependenceFact excepting this class allows for multiple conditioning
     * sets to be associated with a single pair of nodes, which is necessary for the proper ordering of iterations in
     * the ION search.
     */
    private final class IonIndependenceFacts {
        private Node x;
        private Node y;
        private Collection<List<Node>> z;

        /**
         * Constructs a triple of nodes.
         */
        public IonIndependenceFacts(Node x, Node y, Collection<List<Node>> z) {
            if (x == null || y == null || z == null) {
                throw new NullPointerException();
            }

            this.x = x;
            this.y = y;
            this.z = z;
        }

        public final Node getX() {
            return x;
        }

        public final Node getY() {
            return y;
        }

        public final Collection<List<Node>> getZ() {
            return z;
        }

        public void addMoreZ(List<Node> moreZ) {
            z.add(moreZ);
        }

        public final int hashCode() {
            int hash = 17;
            hash += 19 * x.hashCode() * y.hashCode();
            hash += 23 * z.hashCode();
            return hash;
        }

        public final boolean equals(Object obj) {
            if (!(obj instanceof IonIndependenceFacts)) {
                return false;
            }

            IonIndependenceFacts fact = (IonIndependenceFacts) obj;
            return (x.equals(fact.x) && y.equals(fact.y) &&
                    z.equals(fact.z))
                    || (x.equals(fact.y) & y.equals(fact.x) &&
                    z.equals(fact.z));
        }

        public String toString() {
            return "I(" + x + ", " + y + " | " + z + ")";
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

    public static List<List<Node>> treks(Graph graph, Node node1, Node node2) {
        List<List<Node>> paths = new LinkedList<List<Node>>();
        treks(graph, node1, node2, new LinkedList<Node>(), paths);
        return paths;
    }

    /**
     * @return the path of the first directed path found from node1 to node2, if any.
     */
    private static void treks(Graph graph, Node node1, Node node2,
                              LinkedList<Node> path, List<List<Node>> paths) {
        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node next = Edges.traverse(node1, edge);

            if (next == null) {
                continue;
            }

            if (path.size() > 1) {
                Node node0 = path.get(path.size() - 2);

                if (next == node0) {
                    continue;
                }

                if (graph.isDefCollider(node0, node1, next)) {
                    continue;
                }
            }

            if (next == node2) {
                LinkedList<Node> _path = new LinkedList<Node>(path);
                _path.add(next);
                paths.add(_path);
                continue;
            }

            if (path.contains(next)) {
                continue;
            }

            treks(graph, next, node2, path, paths);
        }

        path.removeLast();
    }

    private Graph screenForKnowledge(Graph pag) {
        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge next = it.next();
            Node y = pag.getNode(next.getFrom());
            Node x = pag.getNode(next.getTo());

            if (x == null || y == null) {
                continue;
            }

            Edge edge = pag.getEdge(x, y);

            if (edge == null) {
                continue;
            }

            if (edge.getProximalEndpoint(x) == Endpoint.ARROW && edge.getProximalEndpoint(y) == Endpoint.TAIL) {
                return null;
            } else if (edge.getProximalEndpoint(x) == Endpoint.ARROW && edge.getProximalEndpoint(y) == Endpoint.CIRCLE) {
                pag.removeEdge(edge);
                pag.addEdge(Edges.bidirectedEdge(x, y));
            } else if (edge.getProximalEndpoint(x) == Endpoint.CIRCLE && edge.getProximalEndpoint(y) == Endpoint.CIRCLE) {
                pag.removeEdge(edge);
                pag.addEdge(Edges.partiallyOrientedEdge(x, y));
            }
        }


        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge next = it.next();
            Node x = pag.getNode(next.getFrom());
            Node y = pag.getNode(next.getTo());

            if (x == null || y == null) {
                continue;
            }

            Edge edge = pag.getEdge(x, y);

            if (edge == null) {
                return null;
            } else if (edge.getProximalEndpoint(x) == Endpoint.ARROW && edge.getProximalEndpoint(y) == Endpoint.TAIL) {
                return null;
            } else if (edge.getProximalEndpoint(x) == Endpoint.ARROW && edge.getProximalEndpoint(y) == Endpoint.CIRCLE) {
                return null;
            } else if (edge.getProximalEndpoint(x) == Endpoint.CIRCLE && edge.getProximalEndpoint(y) == Endpoint.ARROW) {
                pag.removeEdge(edge);
                pag.addEdge(Edges.directedEdge(x, y));
            } else if (edge.getProximalEndpoint(x) == Endpoint.CIRCLE && edge.getProximalEndpoint(y) == Endpoint.CIRCLE) {
                pag.removeEdge(edge);
                pag.addEdge(Edges.directedEdge(x, y));
            }
        }


//        doFinalOrientation(pag);
        return pag;
    }

    private Set<Graph> applyKnowledge(Set<Graph> outputSet) {
        Set<Graph> _out = new HashSet<Graph>();

        for (Graph graph : outputSet) {
            Graph _graph = screenForKnowledge(graph);

            if (_graph != null) {
                _out.add(_graph);
            }
        }

        return _out;
    }


}



