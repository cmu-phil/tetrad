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
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements the JCPC algorithm.
 *
 * @author Joseph Ramsey (this version).
 * @deprecated JCPC is better.
 */
public class Jpc implements GraphSearch {
    private int numAdded;
    private int numRemoved;
    private boolean verbose = false;

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The types of underlying algorithms for JPC.
     */
    public enum AlgorithmType {
        PC, CPC
    }

    public enum PathBlockingSet {
        LARGE, SMALL
    }

    private AlgorithmType algorithmType = AlgorithmType.PC;

    private PathBlockingSet pathBlockingSet = PathBlockingSet.LARGE;

    /**e
     * The independence test used for the PC search.
     */
    private IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles = false;

    /**
     * The maximum number of adjacencies that may ever be added to any node. (Note, the initial search may already have
     * greater degree.)
     */
    private int maxAdjacencies = 8;

    /**
     * The maximum number of iterations of the algorithm, in the major loop.
     */
    private int maxIterations = 20;

    /**
     * True if the algorithm should be started from an empty graph.
     */
    private boolean startFromEmptyGraph = false;

    /**
     * The maximum length of a descendant path. Descendant undirectedPaths must be checked in the common collider search.
     */
    private int maxDescendantPath = 20;

    /**
     * An initial graph, if there is one.
     */
    private Graph initialGraph;

    /**
     * The constantly updated map of sepsets. This is initialized (for PC) when the algorithm starts and updated
     * whenever new sepsets are found (or not found).
     */
    private SepsetMap sepsets;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();


    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    private int pcDepth = -1;


    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a JPC search with the given independence oracle.
     */
    public Jpc(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //==============================PUBLIC METHODS========================//

    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }


    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public int getMaxAdjacencies() {
        return maxAdjacencies;
    }

    public AlgorithmType getAlgorithmType() {
        return algorithmType;
    }

    public void setAlgorithmType(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }

    /**
     * Sets the maximum number of adjacencies.
     */
    public void setSoftmaxAdjacencies(int maxAdjacencies) {
        if (maxAdjacencies < 1) {
            throw new IllegalArgumentException("Max adjacencies needs to be at " +
                    "least one, preferably at least 3");
        }

        this.maxAdjacencies = maxAdjacencies;
    }

    public List<Node> getSemidirectedDescendants(Graph graph, List<Node> nodes) {
        HashSet<Node> descendants = new HashSet<Node>();

        for (Object node1 : nodes) {
            Node node = (Node) node1;
            collectSemidirectedDescendantsVisit(graph, node, descendants);
        }

        return new LinkedList<Node>(descendants);
    }

    public void setStartFromEmptyGraph(boolean startFromEmptyGraph) {
        this.startFromEmptyGraph = startFromEmptyGraph;
    }

    public int getMaxDescendantPath() {
        return maxDescendantPath;
    }

    public void setMaxDescendantPath(int maxDescendantPath) {
        this.maxDescendantPath = maxDescendantPath;
    }

    public PathBlockingSet getPathBlockingSet() {
        return pathBlockingSet;
    }

    public void setPathBlockingSet(PathBlockingSet pathBlockingSet) {
        if (pathBlockingSet == null) throw new NullPointerException();
        this.pathBlockingSet = pathBlockingSet;
    }


    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     */
//    public Graph search() {
//        long time1 = System.currentTimeMillis();
//
//        List<Graph> graphs = new ArrayList<Graph>();
//        IndependenceTest test = getIndependenceTest();
//
//        Graph graph;
//        sepsets = new SepsetMap();
//
//        if (startFromEmptyGraph) {
//            graph = new EdgeListGraph(test.getVariables());
//        } else {
//            if (initialGraph != null) {
//                graph = initialGraph;
//            } else {
//                if (getAlgorithmType() == AlgorithmType.PC) {
//                    Pc search = new Pc(test);
//                    search.setKnowledge(getKnowledge());
//                    search.setDepth(getCpcDepth());
//                    search.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
//                    graph = search.search();
//                    this.sepsets = search.getSepsets();
//                } else if (getAlgorithmType() == AlgorithmType.CPC) {
//                    Cpc search = new Cpc(test);
//                    search.setKnowledge(getKnowledge());
//                    search.setDepth(getCpcDepth());
//                    search.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
//                    graph = search.search();
//                } else {
//                    throw new IllegalArgumentException("Unspecified algorithm.");
//                }
//            }
//        }
//
////        System.out.println("Initial search finished.");
////        System.out.println("Degree of graph = " + DataGraphUtils.getDegree(graph));
//
//        // This makes a list of all possible edges.
//        Graph fullGraph = new EdgeListGraph(graph.getNodes());
//        fullGraph.fullyConnect(Endpoint.CIRCLE);
//        List<Edge> edges = fullGraph.getEdges();
//
//        boolean changed = true;
//        int count = -1;
//
//        Collections.shuffle(edges);
//
//        LOOP:
//        while (changed && ++count < getMaxIterations()) {
////            System.out.println("\nRound = " + (count + 1));
//
//            TetradLogger.getInstance().log("info", "Round = " + (count + 1));
//            int numAdded = 0;
//            int numRemoved = 0;
//            int index = 0;
//
//            Graph graph2 = new EdgeListGraph(graph);
//
//            for (Edge adj : edges) {
//                index++;
//
//                if (index % 500 == 0) {
//                    TetradLogger.getInstance().log("info", index + " of " + edges.size());
////                    System.out.println(index + " of " + edges.size());
//                }
//
//                Node x = adj.getNode1();
//                Node y = adj.getNode2();
//
//                List<Node> sepsetX, sepsetY;
//
//                if (getPathBlockingSet() == PathBlockingSet.LARGE) {
//                    sepsetX = pathBlockingSet(test, graph2, x, y);
//                    sepsetY = pathBlockingSet(test, graph2, y, x);
//                }
//
//                else if (getPathBlockingSet() == PathBlockingSet.SMALL) {
//                    sepsetX = pathBlockingSetSmall(test, graph2, x, y);
//                    sepsetY = pathBlockingSetSmall(test, graph2, y, x);
//                }
//                else {
//                    throw new IllegalStateException("Unrecognized sepset type.");
//                }
//
//                if (sepsetX != null && sepsetY != null) {
//                    if (sepsetX.size() > sepsetY.size()) sepsets.set(x, y, sepsetX);
//                    else sepsets.set(x, y, sepsetY);
//                } else if (sepsetX != null) {
//                    sepsets.set(x, y, sepsetX);
//                } else if (sepsetY != null) {
//                    sepsets.set(x, y, sepsetY);
//                } else {
//                    sepsets.set(x, y, null);
//                }
//
//                Edge edge = graph.getEdge(x, y);
//
//
//                if (edge == null) {
//                    if (sepsets.get(x, y) == null) {
//                        if (graph.getAdjacentNodes(x).size() >= getSoftmaxAdjacencies()) {
//                            continue;
//                        }
//
//                        if (graph.getAdjacentNodes(y).size() >= getSoftmaxAdjacencies()) {
//                            continue;
//                        }
//
//                        if (knowledge.edgeForbidden(x.getName(), y.getName()) && knowledge.edgeForbidden(y.getName(), x.getName())) {
//                            continue;
//                        }
//
//                        graph.addUndirectedEdge(x, y);
//                        numAdded++;
//                    }
//                } else {
//                    if (sepsets.get(x, y) != null) {
//                        if (!knowledge.noEdgeRequired(x.getName(), y.getName())) {
//                            continue;
//                        }
//
//                        graph.removeEdge(edge);
//                        numRemoved++;
//                    }
//                }
//            }
//
//            graph = orient(graph, test, sepsets, startFromEmptyGraph && count == 0);
//            TetradLogger.getInstance().log("info", "Num added = " + numAdded);
//            TetradLogger.getInstance().log("info", "Num removed = " + numRemoved);
////            System.out.println("Num added = " + numAdded);
////            System.out.println("Num removed = " + numRemoved);
//
//            graphs.add(new EdgeListGraph(graph));
//
//            for (int i = graphs.size() - 2; i >= 0; i--) {
//                if (graphs.get(graphs.size() - 1).equals(graphs.get(i))) {
//                    changed = false;
//                    continue LOOP;
//                }
//            }
//
//            changed = true;
//        }
//
//        this.logger.log("graph", "\nReturning this graph: " + graph);
//
//        long time2 = System.currentTimeMillis();
//        this.elapsedTime = time2 - time1;
//
//        return graph;
//    }
    public Graph search() {
        long time1 = System.currentTimeMillis();

        List<Graph> graphs = new ArrayList<Graph>();
        IndependenceTest test = getIndependenceTest();

        Graph graph;

        if (startFromEmptyGraph) {
            graph = new EdgeListGraph(test.getVariables());
        } else {
            if (initialGraph != null) {
                graph = initialGraph;
                sepsets = new SepsetMap();
            } else {
                Pc search = new Pc(test);
                search.setKnowledge(getKnowledge());
                search.setDepth(getPcDepth());
                search.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
                graph = search.search();
                sepsets = search.getSepsets();
            }
        }

//        System.out.println("Initial search finished.");
//        System.out.println("Degree of graph = " + DataGraphUtils.getDegree(graph));

        // This makes a list of all possible edges.
        List<Node> _changedNodes = graph.getNodes();
        Set<Node> changedNodes = new HashSet<Node>();

        boolean changed = true;
        int count = -1;

        int storedMaxAdjacencies = getMaxAdjacencies();

        LOOP:
        while (changed && ++count < getMaxIterations()) {
//            System.out.println("\nRound = " + (count + 1));

            if (this.startFromEmptyGraph && count < 2) {
                setSoftmaxAdjacencies(4);
            } else {
                setSoftmaxAdjacencies(storedMaxAdjacencies);
            }

            TetradLogger.getInstance().log("info", "Round = " + (count + 1));
            numAdded = 0;
            numRemoved = 0;
            int index = 0;

            Graph graph2 = new EdgeListGraph(graph);
//            Graph graph2 = graph;

            int numEdges = _changedNodes.size() * (_changedNodes.size() - 1) / 2;

            for (int i = 0; i < _changedNodes.size(); i++) {
                for (int j = i + 1; j < _changedNodes.size(); j++) {
                    index++;

                    if (index % 10000 == 0) {
                        TetradLogger.getInstance().log("info", index + " of " + numEdges);
//                        System.out.println(index + " of " + numEdges);
                    }

                    Node x = _changedNodes.get(i);
                    Node y = _changedNodes.get(j);

                    List<Node> sepsetX, sepsetY;

                    if (getPathBlockingSet() == PathBlockingSet.LARGE) {
                        sepsetX = pathBlockingSet(test, graph2, x, y);
                        sepsetY = pathBlockingSet(test, graph2, y, x);
                    } else if (getPathBlockingSet() == PathBlockingSet.SMALL) {
                        sepsetX = pathBlockingSetSmall(test, graph2, x, y);
                        sepsetY = pathBlockingSetSmall(test, graph2, y, x);
                    } else {
                        throw new IllegalStateException("Unrecognized sepset type.");
                    }

                    if (sepsetX != null && sepsetY != null) {
                        if (sepsetX.size() > sepsetY.size()) sepsets.set(x, y, sepsetX);
                        else sepsets.set(x, y, sepsetY);
                    } else if (sepsetX != null) {
                        sepsets.set(x, y, sepsetX);
                    } else if (sepsetY != null) {
                        sepsets.set(x, y, sepsetY);
                    } else {
                        sepsets.set(x, y, null);
                    }

                    boolean existsSepset = sepsetX != null || sepsetY != null;
                    Edge edge = graph.getEdge(x, y);

                    if (edge == null) {
                        if (!existsSepset) {
                            if (graph.getAdjacentNodes(x).size() >= getMaxAdjacencies()) {
                                continue;
                            }

                            if (graph.getAdjacentNodes(y).size() >= getMaxAdjacencies()) {
                                continue;
                            }

                            if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                                continue;
                            }

                            graph.addUndirectedEdge(x, y);
                            changedNodes.addAll(graph.getAdjacentNodes(x));
                            changedNodes.addAll(graph.getAdjacentNodes(y));
                            numAdded++;
                        }
                    } else {
                        if (existsSepset) {
                            if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                                continue;
                            }

                            changedNodes.addAll(graph.getAdjacentNodes(x));
                            changedNodes.addAll(graph.getAdjacentNodes(y));
                            graph.removeEdge(edge);
                            numRemoved++;
                        }
                    }

//                    if (edge == null) {
//                        if (sepsets.get(x, y) == null) {
//                            if (graph.getAdjacentNodes(x).size() >= getSoftmaxAdjacencies()) {
//                                continue;
//                            }
//
//                            if (graph.getAdjacentNodes(y).size() >= getSoftmaxAdjacencies()) {
//                                continue;
//                            }
//
//                            if (knowledge.edgeForbidden(x.getName(), y.getName()) && knowledge.edgeForbidden(y.getName(), x.getName())) {
//                                continue;
//                            }
//
//                            graph.addUndirectedEdge(x, y);
//                            numAdded++;
//                        }
//                    } else {
//                        if (sepsets.get(x, y) != null) {
//                            if (!knowledge.noEdgeRequired(x.getName(), y.getName())) {
//                                continue;
//                            }
//
//                            graph.removeEdge(edge);
//                            numRemoved++;
//                        }
//                    }

                }
            }

//            System.out.println("changed nodes in main loop: " + changedNodes.size());

            graph = orient(graph, test, sepsets, startFromEmptyGraph && count == 0);
//            graph = orientCpc(graph, getKnowledge(), 4, test, changedNodes);
            TetradLogger.getInstance().log("info", "Num added = " + numAdded);
            TetradLogger.getInstance().log("info", "Num removed = " + numRemoved);
//            System.out.println("Num added = " + numAdded);
//            System.out.println("Num removed = " + numRemoved);

//            System.out.println("Copying edge list graph.");
            graphs.add(new EdgeListGraph(graph));
//            System.out.println("Finished copying edge list graph.");

//            System.out.println("Comparing with previous graphs.");
            for (int i = graphs.size() - 2; i >= 0; i--) {
                if (graphs.get(graphs.size() - 1).equals(graphs.get(i))) {
                    changed = false;
                    continue LOOP;
                }
            }
//            System.out.println("Finished comparing with previous graphs.");

            _changedNodes = new ArrayList<Node>(changedNodes);
            changedNodes.clear();

            changed = true;
        }

        this.logger.log("graph", "\nReturning this graph: " + graph);

        long time2 = System.currentTimeMillis();
        this.elapsedTime = time2 - time1;

        return graph;
    }

    private boolean equal(List<Node> sepset1, List<Node> sepset2) {
        if (sepset1 == null && sepset2 == null) {
            return true;
        } else if (sepset1 != null && sepset2 != null && new HashSet<Node>(sepset1).equals(new HashSet<Node>(sepset2))) {
            return true;
        } else {
            return false;
        }
    }

    private Graph orient(Graph graph, IndependenceTest test, SepsetMap sepsets, boolean forceCpc) {
//        if (getAlgorithmType() == AlgorithmType.CPC || forceCpc) {
//            graph = orientCpc(graph, knowledge, -1, test);
//        } else if (getAlgorithmType() == AlgorithmType.PC) {
//        }
        graph = orientPc(graph, knowledge, sepsets);
        return graph;
    }


    //================================PRIVATE METHODS=======================//

    private List<Node> pathBlockingSet(IndependenceTest test, Graph graph, Node x, Node y) {
        List<Node> fullSet = pathBlockingSetExcluding(graph, x, y, new HashSet<Node>());

        List<Node> commonAdjacents = graph.getAdjacentNodes(x);
        commonAdjacents.retainAll(graph.getAdjacentNodes(y));

//        for (Node node : new ArrayList<Node>(commonAdjacents)) {
//            if (graph.isDefNoncollider(x, node, y)) {
//                commonAdjacents.remove(node);
//            }
//        }

        DepthChoiceGenerator generator = new DepthChoiceGenerator(commonAdjacents.size(), commonAdjacents.size());
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Node> definitelyExcluded = new HashSet<Node>(GraphUtils.asList(choice, commonAdjacents));
            Set<Node> perhapsExcluded = new HashSet<Node>();

            for (Node node1 : new ArrayList<Node>(definitelyExcluded)) {
//                if (node1 == x || node1 == y) continue;

                for (Node node2 : fullSet) {
                    if (graph.isParentOf(node2, x)) continue;
                    if (graph.isParentOf(node2, y)) continue;
                    if (node1 == node2) continue;

                    // These calls to proper descendant and semidiriected path are hanging for large searches. jdramsey 5/5/10
                    if (existsDirectedPathFromTo(graph, node1, node2)) {
                        definitelyExcluded.add(node2);
                    } else if (existsSemidirectedPathFromTo(graph, node1, node2)) {
                        if (graph.isParentOf(node2, x)) continue;
                        if (graph.isParentOf(node2, y)) continue;

                        if (!definitelyExcluded.contains(node2)) {
                            perhapsExcluded.add(node2);
                        }
                    }
                }
            }

            List<Node> _perhapsExcluded = new ArrayList<Node>(perhapsExcluded);
            DepthChoiceGenerator generator2 = new DepthChoiceGenerator(_perhapsExcluded.size(), _perhapsExcluded.size());
            int[] choice2;

            while ((choice2 = generator2.next()) != null) {
                List<Node> perhapsExcludedSubset = GraphUtils.asList(choice2, _perhapsExcluded);
                Set<Node> excluded = new HashSet<Node>(definitelyExcluded);
                excluded.addAll(perhapsExcludedSubset);

                List<Node> sepset = pathBlockingSetExcluding(graph, x, y, excluded);

                if (test.isIndependent(x, y, sepset)) {
                    return sepset;
                }
            }
        }

        return null;
    }

    private List<Node> pathBlockingSetSmall(IndependenceTest test, Graph graph, Node x, Node y) {
        List<Node> adjX = graph.getAdjacentNodes(x);
        adjX.removeAll(graph.getParents(x));
        adjX.removeAll(graph.getChildren(x));

        DepthChoiceGenerator gen = new DepthChoiceGenerator(adjX.size(), -1);
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> selection = GraphUtils.asList(choice, adjX);
            Set<Node> sepset = new HashSet<Node>(selection);
            sepset.addAll(graph.getParents(x));

            sepset.remove(x);
            sepset.remove(y);

            ArrayList<Node> sepsetList = new ArrayList<Node>(sepset);


            if (test.isIndependent(x, y, sepsetList)) {
                return sepsetList;
            }
        }

        return null;
    }

    private List<Node> pathBlockingSetExcluding(Graph graph, Node x, Node y, Set<Node> excludedNodes) {
        List<Node> condSet = new LinkedList<Node>();

        for (Node b : graph.getAdjacentNodes(x)) {
            if (!condSet.contains(b) && !excludedNodes.contains(b)) {
                condSet.add(b);
            }

            if (!graph.isParentOf(b, x)) {
                for (Node parent : graph.getParents(b)) {
                    if (!condSet.contains(parent) && !excludedNodes.contains(parent)) {
                        condSet.add(parent);
                    }
                }
            }
        }

        for (Node parent : graph.getParents(y)) {
            if (!condSet.contains(parent) && !excludedNodes.contains(parent)) {
                condSet.add(parent);
            }
        }

        condSet.remove(x);
        condSet.remove(y);

        return condSet;
    }

    private Graph orientCpc(Graph graph, IKnowledge knowledge, int depth, IndependenceTest test) {
        graph = GraphUtils.undirectedGraph(graph);
        SearchGraphUtils.pcOrientbk(knowledge, graph, graph.getNodes());
        graph = orientUnshieldedTriples(graph, knowledge, test, depth);
        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);
        return graph;
    }

    private Graph orientPc(Graph graph, IKnowledge knowledge, SepsetMap sepsets) {
        graph = GraphUtils.undirectedGraph(graph);
        SearchGraphUtils.pcOrientbk(knowledge, graph, graph.getNodes());
        SearchGraphUtils.orientCollidersUsingSepsets(sepsets, knowledge, graph, verbose);
        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);
        return graph;
    }

    @SuppressWarnings({"SameParameterValue"})
    private Graph orientUnshieldedTriples(Graph graph, IKnowledge knowledge,
                                          IndependenceTest test, int depth) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

//        Set<Triple> noncolliderTriples = new HashSet<Triple>();
//        Set<Triple> ambiguousTriples = new HashSet<Triple>();
//        Set<Triple> allTriples = new HashSet<Triple>();

        for (Node y : graph.getNodes()) {
//            System.out.println("yyy " + y);
            List<Node> adjacentNodes = graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(x, z)) {
                    continue;
                }

//                allTriples.add(new Triple(x, y, z));

                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(x, y, z, test, depth, graph, verbose);

                if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(y, x, Endpoint.TAIL);
                        graph.setEndpoint(y, z, Endpoint.TAIL);
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);

                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
                    }

//                    colliderTriples.add(new Triple(x, y, z));
                } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                    Triple triple = new Triple(x, y, z);
//                    ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                } else {
//                    noncolliderTriples.add(new Triple(x, y, z));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

        return graph;
    }

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return isArrowpointAllowed1(x, y, knowledge) &&
                isArrowpointAllowed1(z, y, knowledge);
    }

    private static boolean isArrowpointAllowed1(Node from, Node to,
                                                IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }

        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    private void collectSemidirectedDescendantsVisit(Graph graph, Node node, Set<Node> descendants) {
        descendants.add(node);
        List<Node> children = graph.getChildren(node);

        if (!children.isEmpty()) {
            for (Object aChildren : children) {
                Node child = (Node) aChildren;
                doSemidirectedChildClosureVisit(graph, child, descendants);
            }
        }
    }

    /**
     * closure under the child relation
     */
    private void doSemidirectedChildClosureVisit(Graph graph, Node node, Set<Node> closure) {
        if (!closure.contains(node)) {
            closure.add(node);

            for (Edge edge1 : graph.getEdges(node)) {
                Node sub = Edges.traverseUndirected(node, edge1);

                if (sub != null && (edge1.pointsTowards(sub) || Edges.isUndirectedEdge(edge1))) {
//                    System.out.println(edge1);
                    doSemidirectedChildClosureVisit(graph, sub, closure);
                }
            }
        }
    }

    public SepsetMap getSepsets() {
        return sepsets;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        if (maxIterations < 0) {
            throw new IllegalArgumentException("Number of graph correction iterations must be >= 0: " + maxIterations);
        }

        this.maxIterations = maxIterations;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    public Set<Triple> getColliderTriples(Graph graph) {
        Set<Triple> triples = new HashSet<Triple>();

        for (Node node : graph.getNodes()) {
            List<Node> nodesInto = graph.getNodesInTo(node, Endpoint.ARROW);

            if (nodesInto.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(nodesInto.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                triples.add(new Triple(nodesInto.get(choice[0]), node, nodesInto.get(choice[1])));
            }
        }

        return triples;
    }


    public boolean existsDirectedPathFromTo(Graph graph, Node node1, Node node2) {
        return existsDirectedPathVisit(graph, node1, node2, new LinkedList<Node>());
    }

    public boolean existsSemidirectedPathFromTo(Graph graph, Node node1, Node node2) {
        return existsSemiDirectedPathVisit(graph, node1, node2, new LinkedList<Node>());
    }


    private boolean existsDirectedPathVisit(Graph graph, Node node1, Node node2,
                                            LinkedList<Node> path) {
        if (path.size() > getMaxDescendantPath()) {
            return false;
        }

        path.addLast(node1);
        Node previous = null;
        if (path.size() > 1) previous = path.get(path.size() - 2);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

//            if (previous != null && graph.isAmbiguousTriple(previous, node1, child)) {
//                continue;
//            }

            if (existsDirectedPathVisit(graph, child, node2, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    /**
     * @return true iff there is a semi-directed path from node1 to node2
     */
    private boolean existsSemiDirectedPathVisit(Graph graph, Node node1, Node node2,
                                                LinkedList<Node> path) {
        if (path.size() > getMaxDescendantPath()) {
            return false;
        }

        path.addLast(node1);
        Node previous = null;
        if (path.size() > 1) previous = path.get(path.size() - 2);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseSemiDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

//            if (previous != null && graph.isAmbiguousTriple(previous, node1, child)) {
//                continue;
//            }

            if (existsSemiDirectedPathVisit(graph, child, node2, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }


    public void setPcDepth(int pcDepth) {
        if (pcDepth < -1) {
            throw new IllegalArgumentException();
        }

        this.pcDepth = pcDepth;
    }

    public int getPcDepth() {
        return pcDepth;
    }

}


