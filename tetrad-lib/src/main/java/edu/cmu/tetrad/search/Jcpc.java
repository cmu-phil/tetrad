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
 */
public class Jcpc implements GraphSearch {
    private int numAdded;
    private int numRemoved;
    private Map<Node, Set<Node>> adjacents = new HashMap();

    public enum PathBlockingSet {
        LARGE, SMALL
    }

    private PathBlockingSet pathBlockingSet = PathBlockingSet.LARGE;

    /**
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
    private int softmaxAdjacencies = 8;

    /**
     * The maximum number of iterations of the algorithm, in the major loop.
     */
    private int maxIterations = 20;

    /**
     * True if the algorithm should be started from an empty graph.
     */
    private boolean startFromEmptyGraph = false;

    /**
     * An initial graph, if there is one.
     */
    private Graph initialGraph;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    /**
     * The depth of the original CPC search.
     */
    private int cpcDepth = -1;

    /**
     * The depth of CPC orientation.
     */
    private int orientationDepth = -1;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a JPC search with the given independence oracle.
     */
    public Jcpc(IndependenceTest independenceTest) {
        if (independenceTest == null) {
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

    public int getSoftmaxAdjacencies() {
        return softmaxAdjacencies;
    }

    /**
     * Sets the number of adjacencies beyond which aggressive edge removal is done for particular nodes.
     */
    public void setSoftmaxAdjacencies(int softmaxAdjacencies) {
        if (softmaxAdjacencies < 0) {
            throw new IllegalArgumentException("Adjacencies softmax must be at least 0.");
        }

        this.softmaxAdjacencies = softmaxAdjacencies;
    }

    public void setStartFromEmptyGraph(boolean startFromEmptyGraph) {
        this.startFromEmptyGraph = startFromEmptyGraph;
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
    public Graph search() {
        long time1 = System.currentTimeMillis();

        List<Graph> graphs = new ArrayList<Graph>();
        IndependenceTest test = getIndependenceTest();

        Graph graph = new EdgeListGraph(test.getVariables());

        // This is the list of all changed nodes from the last iteration
        List<Node> nodes = graph.getNodes();

        Graph outGraph = null;

        int numEdges = nodes.size() * (nodes.size() - 1) / 2;
        int index = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                ++index;

                if (index % 100 == 0) {
                    log("info", index + " of " + numEdges);
                }

                tryAddingEdge(test, graph, nodes, graph, i, j);

                Node x = nodes.get(i);
                Node y = nodes.get(j);

                for (Node w : graph.getAdjacentNodes(x)) {
                    tryRemovingEdge(test, graph, graph, graph.getEdge(w, x));
                }

                for (Node w : graph.getAdjacentNodes(y)) {
                    tryRemovingEdge(test, graph, graph, graph.getEdge(w, y));
                }
            }
        }

        index = 0;

        for (Edge edge : graph.getEdges()) {
            ++index;

            if (index % 10 == 0) {
                log("info", "Backwards " + index + " of " + numEdges);
            }

            tryRemovingEdge(test, graph, graph, edge);
        }

        outGraph = graph;

        this.logger.log("graph", "\nReturning this graph: " + graph);

        long time2 = System.currentTimeMillis();
        this.elapsedTime = time2 - time1;

        orientCpc(outGraph, getKnowledge(), getOrientationDepth(), test);
//        orientPcMax(graph);

        return outGraph;
    }

    private void orientPcMax(Graph graph) {
        SepsetsMaxScore sepsetProducer = new SepsetsMaxScore(graph, independenceTest, null, -1);

        addColliders(graph, sepsetProducer, knowledge);

        MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        rules.orientImplied(graph);
    }

    private void addColliders(Graph graph, final SepsetProducer sepsetProducer, IKnowledge knowledge) {
        final Map<Triple, Double> collidersPs = findCollidersUsingSepsets(sepsetProducer, graph, false);

        List<Triple> colliders = new ArrayList<>(collidersPs.keySet());

        Collections.shuffle(colliders);

        Collections.sort(colliders, new Comparator<Triple>() {
            public int compare(Triple o1, Triple o2) {
                return -Double.compare(collidersPs.get(o1), collidersPs.get(o2));
            }
        });

        for (Triple collider : colliders) {
//            if (collidersPs.get(collider) < 0.2) continue;

            Node a = collider.getX();
            Node b = collider.getY();
            Node c = collider.getZ();

            if (!(isArrowpointAllowed(a, b, knowledge) && isArrowpointAllowed(c, b, knowledge))) {
                continue;
            }

            if (!graph.getEdge(a, b).pointsTowards(a) && !graph.getEdge(b, c).pointsTowards(c)) {
                graph.setEndpoint(a, b, Endpoint.ARROW);
                graph.setEndpoint(c, b, Endpoint.ARROW);
            }
        }
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-> y <-* z just in
     * case y is in Sepset({x, z}).
     */
    public Map<Triple, Double> findCollidersUsingSepsets(SepsetProducer sepsetProducer, Graph graph, boolean verbose) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        Map<Triple, Double> colliders = new HashMap<>();

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                List<Node> sepset = sepsetProducer.getSepset(a, c);

                if (sepset == null) continue;

                if (!sepset.contains(b)) {
                    if (verbose) {
                        System.out.println("\nCollider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                    }

                    colliders.put(new Triple(a, b, c), sepsetProducer.getScore());

                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
        return colliders;
    }


    private void log(String info, String message) {
        TetradLogger.getInstance().log(info, message);
        if ("info".equals(info)) {
            System.out.println(message);
        }
    }

    private Set<Node> reapplyOrientation(Node x, Node y, Set<Node> newArrows, Graph graph) {
        Set<Node> toProcess = new HashSet<>();
        toProcess.add(x);
        toProcess.add(y);

        if (newArrows != null) {
            toProcess.addAll(newArrows);
        }

        return meekOrientRestricted(new ArrayList<>(toProcess), getKnowledge(), graph);
    }

    // Runs Meek rules on just the changed adj.
    private Set<Node> meekOrientRestricted(List<Node> nodes, IKnowledge knowledge, Graph graph) {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        rules.setUndirectUnforcedEdges(true);
        rules.orientImplied(graph, nodes);
        return rules.getVisited();
    }

    private Edge tryAddingEdge(IndependenceTest test, Graph graph, List<Node> _changedNodes, Graph oldGraph, int i, int j) {
        Node x = _changedNodes.get(i);
        Node y = _changedNodes.get(j);

        if (graph.isAdjacentTo(x, y)) {
            return null;
        }

        List<Node> sepsetX, sepsetY;
        boolean existsSepset = false;

        if (getPathBlockingSet() == PathBlockingSet.LARGE) {
            sepsetX = pathBlockingSet(test, oldGraph, x, y);

            if (sepsetX != null) {
                existsSepset = true;
            } else {
                sepsetY = pathBlockingSet(test, oldGraph, y, x);

                if (sepsetY != null) {
                    existsSepset = true;
                }
            }
        } else {
            throw new IllegalStateException("Unrecognized sepset type.");
        }


        if (!existsSepset) {
            if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                return null;
            }

            Edge edge = Edges.undirectedEdge(x, y);
            graph.addEdge(edge);
            numAdded++;
            return edge;
        }

        return null;
    }

    private Edge tryRemovingEdge(IndependenceTest test, Graph graph, Graph oldGraph, Edge edge) {
        Node x = edge.getNode1();
        Node y = edge.getNode2();

        List<Node> sepsetX, sepsetY;
        boolean existsSepset = false;

        if (getPathBlockingSet() == PathBlockingSet.LARGE) {
            sepsetX = pathBlockingSet(test, oldGraph, x, y);

            if (sepsetX != null) {
                existsSepset = true;
            } else {
                sepsetY = pathBlockingSet(test, oldGraph, y, x);

                if (sepsetY != null) {
                    existsSepset = true;
                }
            }
        } else {
            throw new IllegalStateException("Unrecognized sepset type.");
        }

        if (existsSepset) {
            if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                return null;
            }

            graph.removeEdges(x, y);
            numRemoved++;
            return Edges.undirectedEdge(x, y);
        }

        return null;
    }

    //================================PRIVATE METHODS=======================//

    private List<Node> pathBlockingSet(IndependenceTest test, Graph graph, Node x, Node y) {
        List<Node> commonAdjacents = graph.getAdjacentNodes(x);
        commonAdjacents.retainAll(graph.getAdjacentNodes(y));

        DepthChoiceGenerator generator = new DepthChoiceGenerator(commonAdjacents.size(), commonAdjacents.size());
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Node> notConditioned = new HashSet<>(GraphUtils.asList(choice, commonAdjacents));

            List<Node> _descendants = graph.getDescendants(new ArrayList<Node>(notConditioned));
            Set<Node> descendants = new HashSet<>(_descendants);

            Set<Node> sepset = pathBlockingSetExcluding(graph, x, y, notConditioned, descendants);
            ArrayList<Node> _sepset = new ArrayList<>(sepset);

            if (test.isIndependent(x, y, _sepset)) {
                return _sepset;
            }
        }

        return null;
    }

    private Set<Node> pathBlockingSetExcluding(Graph graph, Node x, Node y, Set<Node> notConditioned, Set<Node> descendants) {
        Set<Node> condSet = new HashSet<Node>();

        for (Node b : graph.getAdjacentNodes(x)) {
            if (b == y) continue;

            if (!notConditioned.contains(b) /*&& !descendants.contains(b)*/) {
                if (graph.getAdjacentNodes(b).size() > 1) {
                    condSet.add(b);
                }
            }

//            if (!graph.isParentOf(b, x)) {
//                for (Node c : graph.getParents(b)) {
//                    if (!notConditioned.contains(c) /*&& !descendants.contains(c)*/) {
//                        condSet.add(c);
//                    }
//                }
//            }
        }

//        for (Node c : graph.getParents(y)) {
//            if (!notConditioned.contains(c) && !descendants.contains(c)) {
//                condSet.add(c);
//            }
//        }

        condSet.remove(x);
        condSet.remove(y);

        return condSet;
    }

    private void orientCpc(Graph graph, IKnowledge knowledge, int depth, IndependenceTest test) {
        SearchGraphUtils.pcOrientbk(knowledge, graph, graph.getNodes());
        orientUnshieldedTriples(graph, test, depth, knowledge);
        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);
    }

    /**
     * Assumes a graph with only required knowledge orientations.
     */
    private Set<Node> orientUnshieldedTriples(Graph graph, IndependenceTest test, int depth, IKnowledge knowledge) {
        log("info", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();
        Set<Node> colliderNodes = new HashSet<Node>();


        for (Node y : nodes) {
            orientCollidersAboutNode(graph, test, depth, knowledge, colliderNodes, y);
        }

        log("info", "Finishing Collider Orientation.");

        return colliderNodes;
    }

    private void orientCollidersAboutNode(Graph graph, IndependenceTest test, int depth, IKnowledge knowledge,
                                          Set<Node> colliderNodes, Node y) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(y);

        if (adjacentNodes.size() < 2) {
            return;
        }

        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            Node x = adjacentNodes.get(combination[0]);
            Node z = adjacentNodes.get(combination[1]);

            if (graph.isAdjacentTo(x, z)) {
                continue;
            }

            SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, graph);

            if (type == SearchGraphUtils.CpcTripleType.COLLIDER &&
                    isArrowpointAllowed(x, y, knowledge) &&
                    isArrowpointAllowed(z, y, knowledge)) {
                graph.setEndpoint(x, y, Endpoint.ARROW);
                graph.setEndpoint(z, y, Endpoint.ARROW);

                colliderNodes.add(y);
                log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
            } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                Triple triple = new Triple(x, y, z);
                graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }
        }
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

    public void setCpcDepth(int cpcDepth) {
        if (cpcDepth < -1) {
            throw new IllegalArgumentException();
        }

        this.cpcDepth = cpcDepth;
    }

    public int getCpcDepth() {
        return cpcDepth;
    }

    public int getOrientationDepth() {
        return orientationDepth;
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed(Object from, Object to,
                                              IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }
}


