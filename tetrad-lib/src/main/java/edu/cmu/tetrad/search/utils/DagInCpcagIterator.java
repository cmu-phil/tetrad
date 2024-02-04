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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Given a CPDAG, lists all the DAGs in that DPCAG. In the form of an iterator-- call hasNext() to see if there's
 * another one and next() to get it. next() will return null if there are no more.
 *
 * @author josephramsey
 */
public class DagInCpcagIterator {

    // The stack of graphs, with annotations as to the arbitrary undirected edges chosen in them and whether
    // these edges have already been oriented left and/or right.
    private final LinkedList<DecoratedGraph> decoratedGraphs = new LinkedList<>();
    // The set of colliders in the original CPDAG.
    private final LinkedList<Triple> colliders;
    // Whether to allow new colliders in the graphs.
    private final boolean allowNewColliders;
    // The graph to be returned by next() if it is not null.
    private Graph storedGraph;
    // Whether the first graph has been returned.
    private boolean returnedOne;

    /**
     * The given CPDAG must be a CPDAG. If it does not consist entirely of directed and undirected edges and if it is
     * not acyclic, it is rejected.
     *
     * @param CPDAG The CPDAG for which DAGS are wanted. May result in cyclic outputs.
     * @throws IllegalArgumentException if the CPDAG is not a CPDAG.
     */
    public DagInCpcagIterator(Graph CPDAG) {
        this(CPDAG, new Knowledge(), false, true);
    }

    /**
     * The given CPDAG must be a CPDAG. If it does not consist entirely of directed and undirected edges and if it is
     * not acyclic, it is rejected.
     *
     * @param CPDAG     The CPDAG for which DAGS are wanted. May result in cyclic outputs.
     * @param knowledge The knowledge to be used to constrain the DAGs.
     * @throws IllegalArgumentException if the CPDAG is not a CPDAG.
     */
    public DagInCpcagIterator(Graph CPDAG, Knowledge knowledge) {
        this(CPDAG, knowledge, false, true);
    }

    /**
     * The given CPDAG must be a CPDAG. If it does not consist entirely of directed and undirected edges and if it is
     * not acyclic, it is rejected.
     *
     * @param CPDAG                      The CPDAG for which DAGS are wanted.
     * @param knowledge                  The knowledge to be used to constrain the DAGs.
     * @param allowArbitraryOrientations True if arbitrary orientations are allowable when reasonable ones cannot be
     *                                   made. May result in cyclic outputs.
     * @param allowNewColliders          True if new colliders are allowed in teh graphs.
     * @throws IllegalArgumentException if the CPDAG is not a CPDAG.
     */
    public DagInCpcagIterator(Graph CPDAG, Knowledge knowledge, boolean allowArbitraryOrientations,
                              boolean allowNewColliders) {
        Knowledge knowledge1;
        if (knowledge == null) {
            knowledge1 = new Knowledge();
        } else {
            knowledge1 = knowledge;
        }

        this.allowNewColliders = allowNewColliders;

        assert knowledge != null;

        if (knowledge.isViolatedBy(CPDAG)) {
            throw new IllegalArgumentException("The CPDAG already violates that knowledge.");
        }

        HashMap<Graph, Set<Edge>> changedEdges = new HashMap<>();
        changedEdges.put(CPDAG, new HashSet<>());

        this.decoratedGraphs.add(new DecoratedGraph(CPDAG, knowledge1, changedEdges,
                allowArbitraryOrientations));
        this.colliders = GraphUtils.listColliderTriples(CPDAG);
    }

    /**
     * Successive calls to this method return successive DAGs in the CPDAG, in a more or less natural enumeration of
     * them in which an arbitrary undirected edge is picked, oriented one way, Meek rules applied, then a remaining
     * unoriented edge is picked, oriented one way, and so on, until a DAG is obtained, and then by backtracking the
     * other orientation of each chosen edge is tried. Nonrecursive, obviously.
     * <p>
     *
     * @return a Graph instead of a DAG because sometimes, due to faulty CPDAGs, a cyclic graph is produced, and the
     * end-user may need to decide what to do with it. The simplest thing is to construct a DAG (Dag(graph)) and catch
     * an exception.
     */
    public Graph next() {
        if (this.storedGraph != null) {
            Graph temp = this.storedGraph;
            this.storedGraph = null;

            return temp;
        }

        if (this.decoratedGraphs.size() == 1 && this.decoratedGraphs.getLast().getEdge() == null
                && !this.returnedOne) {
            this.returnedOne = true;
            return new EdgeListGraph(this.decoratedGraphs.getLast().getGraph());
        }

        do {
            while (!this.decoratedGraphs.isEmpty()) {
                DecoratedGraph graph = this.decoratedGraphs.removeLast();

                if (graph.isOrientable()) {
                    this.decoratedGraphs.addLast(graph);
                    break;
                }
            }

            if (this.decoratedGraphs.isEmpty()) {
                return null;
            }

            DecoratedGraph graph;

            while ((graph = this.decoratedGraphs.getLast().orient()) != null) {
                this.decoratedGraphs.addLast(graph);
            }
        } while (this.decoratedGraphs.getLast().getEdge() == null && !this.allowNewColliders &&
                !GraphUtils.listColliderTriples(this.decoratedGraphs.getLast().getGraph()).equals(this.colliders));

        return new EdgeListGraph(this.decoratedGraphs.getLast().getGraph());
    }

    /**
     * Returns true just in case there is still a DAG remaining in the enumeration of DAGs for this CPDAG.
     *
     * @return true just in case there is still a DAG remaining in the enumeration of DAGs for this CPDAG.
     */
    public boolean hasNext() {
        if (this.storedGraph == null) {
            this.storedGraph = next();
        }

        return this.storedGraph != null;
    }

    //==============================CLASSES==============================//

    private static class DecoratedGraph {
        private final Graph graph;
        private final Edge edge;
        private final Knowledge knowledge;
        private final boolean allowArbitraryOrientation;
        private boolean triedLeft;
        private boolean triedRight;
        private Map<Graph, Set<Edge>> changedEdges = new HashMap<>();

        public DecoratedGraph(Graph graph, Knowledge knowledge, Map<Graph, Set<Edge>> changedEdges, boolean allowArbitraryOrientation) {
            this.graph = graph;
            this.edge = findUndirectedEdge(graph);
            this.knowledge = knowledge;
            this.setChangedEdges(new HashMap<>(changedEdges));
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
            return this.graph;
        }

        public Edge getEdge() {
            return this.edge;
        }

        public String toString() {
            return this.graph.toString();
        }

        public boolean isOrientable() {
            if (this.edge == null) {
                return false;
            }

            Node node1 = this.edge.getNode1();
            Node node2 = this.edge.getNode2();

            return (!this.triedLeft && !this.graph.paths().isAncestorOf(node1, node2) &&
                    !getKnowledge().isForbidden(node2.getName(), node1.getName())) ||
                    (!this.triedRight && !this.graph.paths().isAncestorOf(node2, node1) &&
                            !getKnowledge().isForbidden(node1.getName(), node2.getName()));

        }

        public DecoratedGraph orient() {
            if (this.edge == null) {
                return null;
            }

            if (!this.triedLeft && !this.graph.paths().isAncestorOf(this.edge.getNode1(), this.edge.getNode2()) &&
                    !getKnowledge().isForbidden(this.edge.getNode2().getName(), this.edge.getNode1().getName())) {
                Set<Edge> edges = new HashSet<>();

                Graph graph = new EdgeListGraph(this.graph);
                graph.removeEdges(this.edge.getNode1(), this.edge.getNode2());

                graph.addDirectedEdge(this.edge.getNode2(), this.edge.getNode1());
                graph.getEdge(this.edge.getNode2(), this.edge.getNode1()).setHighlighted(true);

                edges.add(graph.getEdge(this.edge.getNode2(), this.edge.getNode1()));
                edges.addAll(new HashSet<>(getChangedEdges().get(this.graph)));

                MeekRules meek = new MeekRules();
                meek.setKnowledge(getKnowledge());
                meek.orientImplied(graph);

                // Keep track of changed edges for highlighting
                Set<Edge> changedEdges = meek.getChangedEdges().keySet();

                edges.addAll(changedEdges);
                this.getChangedEdges().put(graph, edges);

                for (Edge edge : edges) {
                    edge.setHighlighted(true);
                }

                this.triedLeft = true;
                fail(graph, "A");
                return new DecoratedGraph(graph, getKnowledge(), this.getChangedEdges(),
                        isAllowArbitraryOrientation());
            }

            if (!this.triedRight && !this.graph.paths().isAncestorOf(this.edge.getNode2(), this.edge.getNode1()) &&
                    !getKnowledge().isForbidden(this.edge.getNode1().getName(), this.edge.getNode2().getName())) {
                Set<Edge> edges = new HashSet<>();

                Graph graph = new EdgeListGraph(this.graph);
                graph.removeEdges(this.edge.getNode1(), this.edge.getNode2());
                graph.addDirectedEdge(this.edge.getNode1(), this.edge.getNode2());
                graph.getEdge(this.edge.getNode1(), this.edge.getNode2()).setHighlighted(true);

                edges.add(graph.getEdge(this.edge.getNode1(), this.edge.getNode2()));
                edges.addAll(new HashSet<>(getChangedEdges().get(this.graph)));

                MeekRules meek = new MeekRules();
                meek.setKnowledge(getKnowledge());
                meek.orientImplied(graph);

                this.getChangedEdges().put(graph, edges);

                for (Edge edge : edges) {
                    edge.setHighlighted(true);
                }

                this.triedRight = true;
                fail(graph, "B");

                return new DecoratedGraph(graph, getKnowledge(), this.getChangedEdges(),
                        isAllowArbitraryOrientation());
            }

            return null;
        }

        private void fail(Graph graph, String label) {
            if (this.knowledge.isViolatedBy(graph)) {
                throw new IllegalArgumentException("Knowledge violated: " + label);
            }
        }

        private Knowledge getKnowledge() {
            return this.knowledge;
        }

        public Map<Graph, Set<Edge>> getChangedEdges() {
            return this.changedEdges;
        }

        public void setChangedEdges(Map<Graph, Set<Edge>> changedEdges) {
            this.changedEdges = changedEdges;
        }

        public boolean isAllowArbitraryOrientation() {
            return this.allowArbitraryOrientation;
        }
    }
}



