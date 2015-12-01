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

import java.util.*;

/**
 * Given a pattern, lists all of the DAGs in that pattern. In the form of an iterator--call hasNext() to see if there's
 * another one and next() to get it. next() will return null if there are no more.
 *
 * @author Joseph Ramsey
 */
public class DagInPatternIterator {

    /**
     * The stack of graphs, with annotations as to the arbitrary undirected edges chosen in them and whether or not
     * these edges have already been oriented left and/or right.
     */
    private LinkedList<DecoratedGraph> decoratedGraphs = new LinkedList<DecoratedGraph>();
    private Graph storedGraph;
    private boolean returnedOne = false;
    private IKnowledge knowledge = new Knowledge2();
    private LinkedList<Triple> colliders;
    private boolean allowNewColliders = true;

    public DagInPatternIterator(Graph pattern) {
        this(pattern, new Knowledge2(), false, true);
    }

    public DagInPatternIterator(Graph pattern, IKnowledge knowledge) {
        this(pattern, knowledge, false, true);
    }

    public DagInPatternIterator(Graph pattern, boolean allowArbitraryOrientations) {
        this(pattern, new Knowledge2(), allowArbitraryOrientations, true);
    }

    /**
     * The given pattern must be a pattern. If it does not consist entirely of directed and undirected edges and if it
     * is not acyclic, it is rejected.
     *
     * @param pattern                    The pattern for which DAGS are wanted.
     * @param knowledge
     * @param allowArbitraryOrientations True if arbitrary orientations are allowable when reasonable ones cannot be
     *                                   made. May result in cyclic outputs.
     * @throws IllegalArgumentException if the pattern is not a pattern.
     */
    public DagInPatternIterator(Graph pattern, IKnowledge knowledge, boolean allowArbitraryOrientations,
                                boolean allowNewColliders) {
        if (knowledge == null) {
            this.knowledge = new Knowledge2();
        } else {
            this.knowledge = knowledge;
        }

//        if (pattern.existsDirectedCycle()) {
//            System.out.println("Pattern already has a cycle!");
//        }

        this.allowNewColliders = allowNewColliders;

        if (knowledge.isViolatedBy(pattern)) {
            throw new IllegalArgumentException("The pattern already violates that knowledge.");
        }

        for (Edge edge : pattern.getEdges()) {
            if (Edges.isDirectedEdge(edge) || Edges.isUndirectedEdge(edge)) {
                continue;
            }

            if (Edges.isBidirectedEdge(edge)) {
                continue;
            }
//            throw new IllegalArgumentException("A pattern consists only of " +
//                    "directed and undirected edges: " + edge);
        }

        HashMap<Graph, Set<Edge>> changedEdges = new HashMap<Graph, Set<Edge>>();
        changedEdges.put(pattern, new HashSet<Edge>());

        decoratedGraphs.add(new DecoratedGraph(pattern, getKnowledge(), changedEdges,
                allowArbitraryOrientations));
        this.colliders = GraphUtils.listColliderTriples(pattern);
    }

    /**
     * Successive calls to this method return successive DAGs in the pattern, in a more or less natural enumeration of
     * them in which an arbitrary undirected edge is picked, oriented one way, Meek rules applied, then a remaining
     * unoriented edge is picked, oriented one way, and so on, until a DAG is obtained, and then by backtracking the
     * other orientation of each chosen edge is tried. Nonrecursive, obviously.
     * <p>
     *
     * @return a Graph instead of a DAG because sometimes, due to faulty patterns, a cyclic graph is produced, and the
     * end-user may need to decide what to do with it. The simplest thing is to construct a DAG (Dag(graph)) and catch
     * an exception.
     */
    public Graph next() {
        if (storedGraph != null) {
            Graph temp = storedGraph;
            storedGraph = null;

            return temp;
        }

        if (decoratedGraphs.size() == 1 && decoratedGraphs.getLast().getEdge() == null
                && !returnedOne) {
            returnedOne = true;
            return new EdgeListGraph(decoratedGraphs.getLast().getGraph());
        }

        do {
            while (!decoratedGraphs.isEmpty()) {
                DecoratedGraph graph = decoratedGraphs.removeLast();

                if (graph.isOrientable()) {
                    decoratedGraphs.addLast(graph);
                    break;
                }
            }

            if (decoratedGraphs.isEmpty()) {
                return null;
            }

            DecoratedGraph graph;

            while ((graph = decoratedGraphs.getLast().orient()) != null) {
                decoratedGraphs.addLast(graph);
            }
        } while (decoratedGraphs.getLast().getEdge() == null && !allowNewColliders &&
                !GraphUtils.listColliderTriples(decoratedGraphs.getLast().getGraph()).equals(colliders));

        return new EdgeListGraph(decoratedGraphs.getLast().getGraph());
    }

    /**
     * @return true just in case there is still a DAG remaining in the enumeration of DAGs for this pattern.
     */
    public boolean hasNext() {
        if (storedGraph == null) {
            storedGraph = next();
        }

        return storedGraph != null;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

//    private void setKnowledge(IKnowledge knowledge) {
//        if (knowledge == null) throw new IllegalArgumentException();
//        this.knowledge = knowledge;
//    }

    private void fail(Graph graph, String label) {
        if (knowledge.isViolatedBy(graph)) {
//                System.out.println(this.graph);
            throw new IllegalArgumentException("IKnowledge violated: " + label);
        }
    }

    //==============================CLASSES==============================//

    private static class DecoratedGraph {
        private Graph graph;
        private Edge edge;
        private boolean triedLeft = false;
        private boolean triedRight = false;
        private IKnowledge knowledge;
        private Map<Graph, Set<Edge>> changedEdges = new HashMap<Graph, Set<Edge>>();
        private boolean allowArbitraryOrientation = false;

        public DecoratedGraph(Graph graph, IKnowledge knowledge, Map<Graph, Set<Edge>> changedEdges, boolean allowArbitraryOrientation) {
            this.graph = graph;
            this.edge = findUndirectedEdge(graph);
            this.knowledge = knowledge;
            this.setChangedEdges(new HashMap<Graph, Set<Edge>>(changedEdges));
            this.allowArbitraryOrientation = allowArbitraryOrientation;
        }

        //=============================PUBLIC METHODS=======================//

        private Edge findUndirectedEdge(Graph graph) {
            for (Edge edge : graph.getEdges()) {
                if (Edges.isUndirectedEdge(edge) || Edges.isBidirectedEdge(edge)) {
                    return edge;
                }
            }

            return null;
        }

        public Graph getGraph() {
            return graph;
        }

        public Edge getEdge() {
            return edge;
        }

        public String toString() {
            return graph.toString();
        }

        public void triedDirectLeft() {
            triedLeft = true;
        }

        public boolean isOrientable() {
            if (edge == null) {
                return false;
            }

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (!triedLeft && !graph.isAncestorOf(node1, node2) &&
                    !getKnowledge().isForbidden(node2.getName(), node1.getName())) {
                return true;
            }

            if (!triedRight && !graph.isAncestorOf(node2, node1) &&
                    !getKnowledge().isForbidden(node1.getName(), node2.getName())) {
                return true;
            }

            return false;
        }

        public DecoratedGraph orient() {
            if (edge == null) {
                return null;
            }

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (!triedLeft && !graph.isAncestorOf(node1, node2) &&
                    !getKnowledge().isForbidden(node2.getName(), node1.getName())) {
                Set<Edge> edges = new HashSet<Edge>();

                Graph graph = new EdgeListGraph(this.graph);
                graph.removeEdge(edge.getNode1(), edge.getNode2());
                graph.addDirectedEdge(edge.getNode2(), edge.getNode1());

//                System.out.println("Explicitly orienting: " + graph.getEdge(edge.getNode2(), edge.getNode1()));
//
//                if (graph.existsDirectedCycle()) {
//                    System.out.println("Cycle!");
//                }

                edges.add(graph.getEdge(edge.getNode2(), edge.getNode1()));
                edges.addAll(new HashSet<Edge>(getChangedEdges().get(this.graph)));

                MeekRules meek = new MeekRules();
                meek.setKnowledge(getKnowledge());
//                meek.setAggressivelyPreventCycles(true);
                meek.orientImplied(graph);

                // Keep track of changed edges for highlighting
                Set<Edge> changedEdges = meek.getChangedEdges().keySet();

//                System.out.println("Meek oriented: " + changedEdges);
//
//                if (graph.existsDirectedCycle()) {
//                    System.out.println("Cycle!");
//                }

                edges.addAll(changedEdges);
                this.getChangedEdges().put(graph, edges);

                for (Edge edge : edges) {
                    graph.setHighlighted(edge, true);
                }

                triedLeft = true;
                fail(graph, "A");
                return new DecoratedGraph(graph, getKnowledge(), this.getChangedEdges(),
                        isAllowArbitraryOrientation());
            }


            if (!triedRight && !graph.isAncestorOf(node2, node1) &&
                    !getKnowledge().isForbidden(node1.getName(), node2.getName())) {
                Set<Edge> edges = new HashSet<Edge>();


                Graph graph = new EdgeListGraph(this.graph);
                graph.removeEdge(edge.getNode1(), edge.getNode2());
                graph.addDirectedEdge(edge.getNode1(), edge.getNode2());

//                System.out.println("Explicitly orienting: " + graph.getEdge(edge.getNode1(), edge.getNode2()));
//
//                if (graph.existsDirectedCycle()) {
//                    System.out.println("Cycle!");
//                }

                edges.add(graph.getEdge(edge.getNode1(), edge.getNode2()));
                edges.addAll(new HashSet<Edge>(getChangedEdges().get(this.graph)));

                MeekRules meek = new MeekRules();
                meek.setKnowledge(getKnowledge());
//                meek.setAggressivelyPreventCycles(true);
                meek.orientImplied(graph);

                // Keep track of changed edges for highlighting
                Set<Edge> changedEdges = meek.getChangedEdges().keySet();

//                System.out.println("Meek oriented: " + changedEdges);
//
//                if (graph.existsDirectedCycle()) {
//                    System.out.println("Cycle!");
//                }

//                edges.addAll(new HashSet<Edge>(getChangedEdges().get(this.graph)));
//
//                Graph graph = new EdgeListGraph(this.graph);
//                graph.removeEdge(edge.getNode1(), edge.getNode2());
//                graph.addDirectedEdge(edge.getNode1(), edge.getNode2());
//
//                edges.add(graph.getEdge(edge.getNode1(), edge.getNode2()));
//                edges.addAll(new HashSet<Edge>(getChangedEdges().get(this.graph)));
//
//                MeekRules meek = new MeekRules();
//                meek.setKnowledge(knowledge);
//                meek.orientImplied(graph);
//
//                // Keep track of changed edges for highlighting.
//                Set<Edge> changedEdges = meek.getChangedEdges().keySet();
//                edges.addAll(changedEdges);


                this.getChangedEdges().put(graph, edges);

                for (Edge edge : edges) {
                    graph.setHighlighted(edge, true);
                }

                triedRight = true;
                fail(graph, "B");

                return new DecoratedGraph(graph, getKnowledge(), this.getChangedEdges(),
                        isAllowArbitraryOrientation());
            }

            // Pick an arbitrary orientation.

//            if (isAllowArbitraryOrientation()) {
//                boolean right = RandomUtil.getInstance().nextDouble() > 0.5;
//
//                if (right) {
//                    Set<Edge> edges = new HashSet<Edge>();
//                    edges.addAll(new HashSet<Edge>(getChangedEdges().get(this.graph)));
//
//                    Graph graph = new EdgeListGraph(this.graph);
//                    graph.removeEdge(edge.getNode1(), edge.getNode2());
//                    graph.addDirectedEdge(edge.getNode1(), edge.getNode2());
//
//                    edges.add(graph.getEdge(edge.getNode1(), edge.getNode2()));
//                    edges.addAll(new HashSet<Edge>(getChangedEdges().get(this.graph)));
//
//                    MeekRules meek = new MeekRules();
//                    meek.setKnowledge(knowledge);
//                    meek.orientImplied(graph);
//
//                    // Keep track of changed edges for highlighting.
//                    Set<Edge> changedEdges = meek.getChangedEdges().keySet();
//                    edges.addAll(changedEdges);
//                    this.getChangedEdges().put(graph, edges);
//
//                    for (Edge edge : edges) {
//                        graph.setHighlighted(edge, true);
//                    }
//
//                    triedLeft = true;
//                    triedRight = true;
//                    fail(graph, "C");
//                    return new DecoratedGraph(graph, getKnowledge(), this.getChangedEdges(),
//                            isAllowArbitraryOrientation());
//                } else {
//                    Set<Edge> edges = new HashSet<Edge>();
//                    edges.addAll(new HashSet<Edge>(getChangedEdges().get(this.graph)));
//
//                    Graph graph = new EdgeListGraph(this.graph);
//                    graph.removeEdge(edge.getNode1(), edge.getNode2());
//                    graph.addDirectedEdge(edge.getNode2(), edge.getNode1());
//
//                    edges.add(graph.getEdge(edge.getNode1(), edge.getNode2()));
//                    edges.addAll(new HashSet<Edge>(getChangedEdges().get(this.graph)));
//
//                    MeekRules meek = new MeekRules();
//                    meek.setKnowledge(knowledge);
//                    meek.orientImplied(graph);
//
//                    // Keep track of changed edges for highlighting.
//                    Set<Edge> changedEdges = meek.getChangedEdges().keySet();
//                    edges.addAll(changedEdges);
//                    this.getChangedEdges().put(graph, edges);
//
//                    for (Edge edge : edges) {
//                        graph.setHighlighted(edge, true);
//                    }
//
//                    triedLeft = true;
//                    triedRight = true;
//                    fail(graph, "D");
//                    return new DecoratedGraph(graph, getKnowledge(), this.getChangedEdges(),
//                            isAllowArbitraryOrientation());
//                }
//            }


            return null;
        }

        private void fail(Graph graph, String label) {
            if (knowledge.isViolatedBy(graph)) {
//                System.out.println(this.graph);
                throw new IllegalArgumentException("IKnowledge violated: " + label);
            }
        }

        private IKnowledge getKnowledge() {
            return knowledge;
        }

        public Map<Graph, Set<Edge>> getChangedEdges() {
            return changedEdges;
        }

        public void setChangedEdges(Map<Graph, Set<Edge>> changedEdges) {
            this.changedEdges = changedEdges;
        }

        public boolean isAllowArbitraryOrientation() {
            return allowArbitraryOrientation;
        }

        public void setAllowArbitraryOrientation(boolean allowArbitraryOrientation) {
            this.allowArbitraryOrientation = allowArbitraryOrientation;
        }
    }
}



