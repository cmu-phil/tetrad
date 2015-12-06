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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements the PC ("Peter/Clark") algorithm, as specified in Chapter 6 of Spirtes, Glymour, and Scheines, "Causation,
 * Prediction, and Search," 2nd edition, with a modified rule set in step D due to Chris Meek. For the modified rule
 * set, see Chris Meek (1995), "Causal inference and causal explanation with background knowledge."
 *
 * @author Joseph Ramsey (this version).
 */
public class PcPattern implements GraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * Sepset information accumulated in the search.
     */
    private SepsetMap sepset;

    /**
     * The maximum number of nodes conditioned on in the search.
     */
    private int depth = Integer.MAX_VALUE;

    /**
     * The graph that's constructed during the search.
     */
    private Graph graph;

    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;


    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles = false;

    /**
     * Count of independence tests.
     */
    private int numIndependenceTests = 0;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();


    private Set<Triple> unshieldedColliders;
    private Set<Triple> unshieldedNoncolliders;

    //=============================CONSTRUCTORS==========================//

    public PcPattern(IndependenceTest independenceTest) {
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

    public SepsetMap getSepset() {
        return sepset;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public Graph getPartialGraph() {
        return new EdgeListGraph(graph);
    }

    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     */
    public Graph search() {
        return search(independenceTest.getVariables());
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Runs PC on just the given variables, all of which must be in the domain of the independence test.
     */
    public Graph search(List<Node> nodes) {
        this.logger.log("info", "Starting PC PATTERN algorithm");
        this.logger.log("info", "Independence test = " + independenceTest + ".");

        long startTime = System.currentTimeMillis();
        numIndependenceTests = 0;

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

//        Fas fas = new Fas(graph, getIndependenceTest());
        FasStable fas = new FasStable(getIndependenceTest());
        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        graph = fas.search();
        this.sepset = fas.getSepsets();
        this.numIndependenceTests = fas.getNumIndependenceTests();

        enumerateTriples();

        SearchGraphUtils.pcOrientbk(getKnowledge(), graph, nodes);
        orientCollidersUsingSepsetsPattern(sepset, getKnowledge(), graph);
        handleDirectableCycles(graph);
        MeekRulesPattern rules = new MeekRulesPattern();
//        rules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
//        rules.setKnowledge(knowledge);
        rules.orientImplied(graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.elapsedTime = System.currentTimeMillis() - startTime;

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.log("info", "Finishing PC PATTERN Algorithm.");
        this.logger.flush();

        return graph;
    }

    private void enumerateTriples() {
        this.unshieldedColliders = new HashSet<Triple>();
        this.unshieldedNoncolliders = new HashSet<Triple>();

        for (Node y : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(y);

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                Node x = adj.get(choice[0]);
                Node z = adj.get(choice[1]);

//                if (graph.isAdjacentTo(x, z)) {
//                    continue;
//                }

                List<Node> nodes = sepset.get(x, z);

                // Note that checking adj(x, z) does not suffice when knowledge
                // has been specified.
                if (nodes == null) {
                    continue;
                }

                if (nodes.contains(y)) {
                    getUnshieldedNoncolliders().add(new Triple(x, y, z));
                } else {
                    getUnshieldedColliders().add(new Triple(x, y, z));
                }
            }
        }
    }

    public Set<Triple> getUnshieldedColliders() {
        return unshieldedColliders;
    }

    public Set<Triple> getUnshieldedNoncolliders() {
        return unshieldedNoncolliders;
    }

    /**
     * Step C of PC; orients colliders using specified sepset, without orienting bidirected edges. That is, orients x
     * *-* y *-* z as x *-> y <-* z just in case y is in Sepset({x, z}), unless such an orientation would create a
     * bidirected edge.
     */
    public void orientCollidersUsingSepsetsPattern(SepsetMap set,
                                                   IKnowledge knowledge, Graph graph) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                List<Node> sepset = set.get(b, c);
                if (sepset != null && !sepset.contains(a) &&
                        isArrowpointAllowedPattern(b, a, knowledge, graph) &&
                        isArrowpointAllowedPattern(c, a, knowledge, graph) &&
                        !createsCycle(b, a, graph) && !createsCycle(c, a, graph)) {
                    graph.setEndpoint(b, a, Endpoint.ARROW);
                    graph.setEndpoint(c, a, Endpoint.ARROW);
                    TetradLogger.getInstance().log("colliderOrientations",
                            SearchLogUtils.colliderOrientedMsg(b, a, c, sepset));
                    new MeekRulesPattern().meekR2(graph, knowledge);
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    private static boolean isArrowpointAllowedPattern(Node from, Node to,
                                                      IKnowledge knowledge, Graph graph) {
        Edge edge = graph.getEdge(from, to);

        if (knowledge == null) {
            return true;
        } else if (graph.getEndpoint(from, to) == Endpoint.ARROW
                || graph.getEndpoint(to, from) == Endpoint.ARROW) {
            return false;
        } else {
            return !knowledge.isRequired(to.toString(), from.toString()) &&
                    !knowledge.isForbidden(from.toString(), to.toString());
        }
    }

    /**
     * @param graph The graph in which a directed cycle is sought.
     */
    public void handleDirectableCycles(Graph graph) {
        TetradLogger.getInstance().log("info", "Starting Handling of Directable Cycles:");

        for (Node node : graph.getNodes()) {
            directablePathFromTo(graph, node, node);
        }

        TetradLogger.getInstance().log("info", "Finishing Handling of Directable Cycles:");
    }

    /**
     * @param graph The graph in which a directed path is sought.
     * @param node1 The 'from' node.
     * @param node2 The 'to'node.
     */
    public void directablePathFromTo(Graph graph, Node node1, Node node2) {
        directablePathVisit(graph, node1, node2, new LinkedList<Node>());
    }

    private void directablePathVisit(Graph graph, Node node1, Node node2,
                                     LinkedList<Node> path) {
        path.addLast(node1);
//        System.out.println("PATH " + pathString(path, graph));
//        System.out.println("EDGES " + graph.getEdges(node1));


        for (Edge edge : new LinkedList<Edge>(graph.getEdges(node1))) {
            Node child = Edges.traverse(node1, edge);
//            System.out.println("edge = " + edge + "child = " + child + ", node2 = " + node2);

            if (child == null) {
                continue;
            }

            // Skip this path if the path to child would contain a collider.
            if (path.size() >= 2) {
                Node a1 = path.get(path.size() - 2);
                Node a2 = path.get(path.size() - 1);

                if (a1 != child & graph.isDefCollider(a1, a2, child)) {
                    continue;
                }
            }

            // A path to self of length 4 has 3 links.
            if (child == node2 && path.size() >= 4) {
                if (graph.isDefCollider(path.getLast(), child, path.get(1))) {
                    continue;
                }

                if (containsChord(path, graph)) {
                    continue;
                }

                orientSomeCollider(path, graph);
//                TetradLogger.getInstance().log("info", "Directable cycle! " + pathString(path, graph));
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            directablePathVisit(graph, child, node2, path);
        }

        path.removeLast();
    }

    /**
     * @return true just in case the given undirected cycle contains a chord.
     */
    private boolean containsChord(LinkedList<Node> path, Graph graph) {
        for (int i = 0; i < path.size() / 2 + 1; i++) {
            for (int j = 2; j < path.size() - 1; j++) {
                int _j = (i + j) % path.size();
                Node node1 = path.get(i);
                Node node2 = path.get(_j);

                if (graph.isAdjacentTo(node1, node2)) {
//                    Edge chordEdge = graph.getEdge(node1, node2);
//                    TetradLogger.getInstance().log("info", "Chord on path " +
//                            pathString(path, graph)
//                            + " (" + chordEdge + ")");
                    return true;
                }
            }
        }

        return false;
    }

    private void orientSomeCollider(LinkedList<Node> path, Graph graph) {
        LinkedList<Node> _path = new LinkedList<Node>(path);
        _path.add(_path.get(0));

        double storedP = Double.POSITIVE_INFINITY;
        int storedI = -1;

        for (int i = 1; i < _path.size() - 1; i++) {
            Node node1 = _path.get(i - 1);
            Node node2 = _path.get(i);
            Node node3 = _path.get(i + 1);

            if (graph.getEdge(node1, node2) == null) {
                throw new NullPointerException();
            }

            if (graph.getEdge(node3, node2) == null) {
                throw new NullPointerException();
            }

            if (graph.getEndpoint(node2, node1) == Endpoint.ARROW
                    || graph.getEndpoint(node2, node3) == Endpoint.ARROW) {
                continue;
            }

            independenceTest.isIndependent(node1, node3, Arrays.asList(node2));
//            independenceTest.isIndependent(node1, node3, new LinkedList<Node>());
            double p = independenceTest.getPValue();

            if (p < storedP) {
                storedP = p;
                storedI = i;
            }
        }

        if (storedI == -1) {
            return;
        }

        Node node1 = _path.get(storedI - 1);
        Node node2 = _path.get(storedI);
        Node node3 = _path.get(storedI + 1);

        if (createsCycle(node1, node2, graph) || createsCycle(node3, node2, graph)) {
            return;
        }

        graph.setEndpoint(node1, node2, Endpoint.ARROW);
        graph.setEndpoint(node3, node2, Endpoint.ARROW);

        TetradLogger.getInstance().log("details", "Orienting " + node1 + "->" +
                node2 + "<-" + node3
                + " along path " + pathString(path, graph));
    }

    private String pathString(LinkedList<Node> path, Graph graph) {
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < path.size() - 1; i++) {
            buf.append(path.get(i));
            Edge edge = graph.getEdge(path.get(i), path.get(i + 1));

            Endpoint leftEnd = edge.getProximalEndpoint(path.get(i));
            buf.append(leftEnd == Endpoint.ARROW ? "<" : "-");
            buf.append("-");
            Endpoint rightEnd = edge.getProximalEndpoint(path.get(i + 1));
            buf.append(rightEnd == Endpoint.ARROW ? ">" : "-");
        }

        buf.append(path.getLast());
        Edge edge = graph.getEdge(path.getLast(), path.getFirst());

        if (edge != null) {
            Endpoint leftEnd = edge.getProximalEndpoint(path.getLast());
            buf.append(leftEnd == Endpoint.ARROW ? "<" : "-");
            buf.append("-");
            Endpoint rightEnd = edge.getProximalEndpoint(path.getFirst());
            buf.append(rightEnd == Endpoint.ARROW ? ">" : "-");
            buf.append(path.getFirst());
        }

        return buf.toString();
    }

    /**
     * @return true if orienting x-->y would create a cycle.
     */
    private boolean createsCycle(Node x, Node y, Graph graph) {
        return graph.isAncestorOf(y, x);
    }

}




