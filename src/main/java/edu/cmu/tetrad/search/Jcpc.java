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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the JCPC algorithm.
 *
 * @author Joseph Ramsey (this version).
 */
public class Jcpc implements GraphSearch {
    private int numAdded;
    private int numRemoved;

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

        Graph graph;

        if (startFromEmptyGraph) {
            graph = new EdgeListGraph(test.getVariables());
        } else {
            if (initialGraph != null) {
                graph = initialGraph;
            } else {
                Cpc search = new Cpc(test);
                search.setKnowledge(getKnowledge());
                search.setDepth(getCpcDepth());
                search.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
                graph = search.search();
            }
        }

//        undirectedGraph(graph);

        // This is the list of all changed nodes from the last iteration
        List<Node> nodes = graph.getNodes();

        int count = -1;

        int minNumErrors = Integer.MAX_VALUE;
        Graph outGraph = null;

        LOOP:
        while (++count < getMaxIterations()) {
            TetradLogger.getInstance().log("info", "Round = " + (count + 1));
            System.out.println("Round = " + (count + 1));
            numAdded = 0;
            numRemoved = 0;
            int index = 0;

            int indexBackwards = 0;
            int numEdgesBackwards = graph.getNumEdges();

            int numEdges = nodes.size() * (nodes.size() - 1) / 2;

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    index++;

                    if (index % 10000 == 0) {
                        TetradLogger.getInstance().log("info", index + " of " + numEdges);
                        System.out.println(index + " of " + numEdges);
                    }

                    tryAddingEdge(test, graph, nodes, graph, i, j);

                    Node x = nodes.get(i);
                    Node y = nodes.get(j);

                    if (graph.getAdjacentNodes(x).size() > getSoftmaxAdjacencies()) {
                        for (Node w : graph.getAdjacentNodes(x)) {
                            if (w == y) continue;
                            tryRemovingEdge(test, graph, graph, graph.getEdge(x, w));
                        }
                    }

                    if (graph.getAdjacentNodes(y).size() > getSoftmaxAdjacencies()) {
                        for (Node w : graph.getAdjacentNodes(y)) {
                            if (w == x) continue;
                            tryRemovingEdge(test, graph, graph, graph.getEdge(y, w));
                        }
                    }
                }
            }

            if (getSoftmaxAdjacencies() > 0) {
                for (Edge edge : graph.getEdges()) {
                    if (++indexBackwards % 10000 == 0) {
                        TetradLogger.getInstance().log("info", index + " of " + numEdgesBackwards);
                        System.out.println(index + " of " + numEdgesBackwards);
                    }

                    tryRemovingEdge(test, graph, graph, edge);
                }
            }

            System.out.println("Num added = " + numAdded);
            System.out.println("Num removed = " + numRemoved);
            TetradLogger.getInstance().log("info", "Num added = " + numAdded);
            TetradLogger.getInstance().log("info", "Num removed = " + numRemoved);

            System.out.println("(Reorienting...)");

            int numErrors = numAdded + numRemoved;

            // Keep track of the last graph with the fewest changes; this is returned.
            final EdgeListGraph graph1 = new EdgeListGraph(graph);

            if (numErrors <= minNumErrors) {
                minNumErrors = numErrors;
                outGraph = graph1;
            }

            orientCpc(graph, getKnowledge(), getOrientationDepth(), test);
            graphs.add(graph1);

            for (int i = graphs.size() - 2; i >= 0; i--) {
                if (graphs.get(graphs.size() - 1).equals(graphs.get(i))) {
                    System.out.println("Recognized previous graph.");
                    outGraph = graph1;
                    break LOOP;
                }
            }
        }

        this.logger.log("graph", "\nReturning this graph: " + graph);

        long time2 = System.currentTimeMillis();
        this.elapsedTime = time2 - time1;

        orientCpc(outGraph, getKnowledge(), getOrientationDepth(), test);

        return outGraph;
    }

    private void tryAddingEdge(IndependenceTest test, Graph graph, List<Node> _changedNodes, Graph oldGraph, int i, int j) {
        Node x = _changedNodes.get(i);
        Node y = _changedNodes.get(j);

        if (graph.isAdjacentTo(x, y)) {
            return;
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
        } else if (getPathBlockingSet() == PathBlockingSet.SMALL) {
            sepsetX = pathBlockingSetSmall(test, oldGraph, x, y);
            sepsetY = pathBlockingSetSmall(test, oldGraph, y, x);
            existsSepset = sepsetX != null || sepsetY != null;
        } else {
            throw new IllegalStateException("Unrecognized sepset type.");
        }


        if (!existsSepset) {
            if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                return;
            }

            graph.addUndirectedEdge(x, y);
            numAdded++;
        }
    }

    private void tryRemovingEdge(IndependenceTest test, Graph graph, Graph oldGraph, Edge edge) {
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
        } else if (getPathBlockingSet() == PathBlockingSet.SMALL) {
            sepsetX = pathBlockingSetSmall(test, oldGraph, x, y);
            sepsetY = pathBlockingSetSmall(test, oldGraph, y, x);
            existsSepset = sepsetX != null || sepsetY != null;
        } else {
            throw new IllegalStateException("Unrecognized sepset type.");
        }

        if (existsSepset) {
            if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                return;
            }

            graph.removeEdges(edge.getNode1(), edge.getNode2());
            numRemoved++;
        }
    }

    //================================PRIVATE METHODS=======================//

    private List<Node> pathBlockingSet(IndependenceTest test, Graph graph, Node x, Node y) {
        List<Node> commonAdjacents = graph.getAdjacentNodes(x);
        commonAdjacents.retainAll(graph.getAdjacentNodes(y));

        DepthChoiceGenerator generator = new DepthChoiceGenerator(commonAdjacents.size(), commonAdjacents.size());
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Node> colliders = new HashSet<Node>(GraphUtils.asList(choice, commonAdjacents));

            List<Node> _descendants = graph.getDescendants(new ArrayList<Node>(colliders));
            Set<Node> descendants = new HashSet<Node>(_descendants);

            Set<Node> sepset = pathBlockingSetExcluding(graph, x, y, colliders, descendants);
            ArrayList<Node> _sepset = new ArrayList<Node>(sepset);

            if (test.isIndependent(x, y, _sepset)) {
                return _sepset;
            }
        }

        return null;
    }

    private List<Node> pathBlockingSet2(IndependenceTest test, Graph graph, Node x, Node y) {
        Set<Node> boundary = markovBoundaryWithoutXY(graph, x, y);

        ArrayList<Node> _boundary = new ArrayList<Node>(boundary);

        if (!_boundary.contains(y)) {
            if (test.isIndependent(x, y, _boundary)) {
                return _boundary;
            }
        }
        else {
            _boundary.remove(y);

            DepthChoiceGenerator gen = new DepthChoiceGenerator(_boundary.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _boundary);

                if (test.isIndependent(x, y, cond)) {
                    return cond;
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

    private Set<Node> pathBlockingSetExcluding(Graph graph, Node x, Node y, Set<Node> colliders, Set<Node> descendants) {
        Set<Node> condSet = new HashSet<Node>();

        for (Node b : graph.getAdjacentNodes(x)) {
            if (b == y) continue;

            if (!colliders.contains(b) && !descendants.contains(b)) {
                if (graph.getAdjacentNodes(b).size() > 1) {
                    condSet.add(b);
                }
            }

            if (!graph.isParentOf(b, x)) {
                for (Node c : graph.getParents(b)) {
                    if (!colliders.contains(c) && !descendants.contains(c)) {
                        condSet.add(c);
                    }
                }
            }
        }

        for (Node c : graph.getParents(y)) {
            if (!colliders.contains(c) && !descendants.contains(c)) {
                condSet.add(c);
            }
        }

        condSet.remove(x);
        condSet.remove(y);

        return condSet;
    }

    private Set<Node> markovBoundaryWithoutXY(Graph graph, Node x, Node y) {
        Set<Node> condSet = new HashSet<Node>();

        for (Node b : graph.getAdjacentNodes(x)) {
            if (b != y) {
                condSet.add(b);

                if (!graph.isParentOf(b, x)) {
                    for (Node c : graph.getAdjacentNodes(b)) {
                        condSet.add(c);
                    }
                }
            }
        }

        condSet.remove(x);

        return condSet;
    }


    private void orientCpc(Graph graph, IKnowledge knowledge, int depth, IndependenceTest test) {
        undirectedGraph(graph);
        SearchGraphUtils.pcOrientbk(knowledge, graph, graph.getNodes());
        orientUnshieldedTriples(graph, test, depth, knowledge);
        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);
    }

    private void undirectedGraph(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            edge.setEndpoint1(Endpoint.TAIL);
            edge.setEndpoint2(Endpoint.TAIL);
        }
    }

    /**
     * Assumes a graph with only required knowledge orientations.
     */
    private Set<Node> orientUnshieldedTriples(Graph graph, IndependenceTest test, int depth, IKnowledge knowledge) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();
        Set<Node> colliderNodes = new HashSet<Node>();


        for (Node y : nodes) {
            orientCollidersAboutNode(graph, test, depth, knowledge, colliderNodes, y);
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");

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
                TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
//                    System.out.println(SearchLogUtils.colliderOrientedMsg(x, y, z));
            } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                Triple triple = new Triple(x, y, z);
                graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }
        }
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


