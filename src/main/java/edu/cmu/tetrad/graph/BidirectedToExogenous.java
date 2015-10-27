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

package edu.cmu.tetrad.graph;

import java.util.Collection;
import java.util.HashSet;

/**
 * A graph constraint permitting bidirected edges to connect only exogenous
 * nodes.
 *
 * @author Joseph Ramsey
 */
public final class BidirectedToExogenous implements GraphConstraint {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS===========================//

    public BidirectedToExogenous() {

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static BidirectedToExogenous serializableInstance() {
        return new BidirectedToExogenous();
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * Checks to make sure that if an added edge is a bidirected edge, the nodes
     * it connects are exogenous, and if an added edge if a directed edges, it
     * does not point to the same node as a bidirected edge.
     *
     * @return true if the edge may be added, false if not.
     */
    public boolean isEdgeAddable(Edge edge, Graph graph) {
        if (Edges.isBidirectedEdge(edge)) {
            Node nodeA = edge.getNode1();
            Node nodeB = edge.getNode2();

            return isExogenous(nodeA, graph) && isExogenous(nodeB, graph);
        }
        else if (Edges.isDirectedEdge(edge)) {
            Node head = getDirectedEdgeHead(edge);

            for (Edge edge1 : graph.getEdges(head)) {
                if (Edges.isBidirectedEdge(edge1)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * @return true.
     */
    public boolean isNodeAddable(Node node, Graph graph) {
        return true;
    }

    /**
     * @return true.
     */
    public boolean isEdgeRemovable(Edge edge, Graph graph) {
        return true;
    }

    /**
     * @return true.
     */
    public boolean isNodeRemovable(Node node, Graph graph) {
        return true;
    }

    /**
     * For a directed edge, returns the node adjacent to the arrow endpoint.
     *
     * @return the node on the edge adjacent to the arrow endpoint.
     */
    private Node getDirectedEdgeHead(Edge edge) {
        if (edge.getEndpoint1() == Endpoint.ARROW) {
            return edge.getNode1();
        }
        else {
            return edge.getNode2();
        }
    }

    /**
     * Returns the number of 'in' arrows leading into a node.
     */
    private int getInDegree(Node node, Graph graph) {
        return getParents(node, graph).size();
    }

    /**
     * Returns the set of parents for a node.
     */
    private Collection<Node> getParents(Node node, Graph graph) {
        Collection<Node> parents = new HashSet<Node>();

        for (Edge edge1 : graph.getEdges(node)) {
            Node sub = Edges.traverseReverseDirected(node, edge1);

            if (sub != null) {
                parents.add(sub);
            }
        }

        return parents;
    }

    /**
     * Determines whether the given node is exogenous in the given graph--that
     * is, if it has no parents. (Error nodes are automatically exogenous.)
     *
     * @return true iff the given node is exogenous in the given graph.
     */
    private boolean isExogenous(Node node, Graph graph) {
        boolean isError = node.getNodeType() == NodeType.ERROR;
        boolean indegreeZero = (getInDegree(node, graph) == 0);
        return isError || indegreeZero;
    }

    /**
     * Returns a string representation of this constraint.
     */
    public String toString() {
        return "<Bidirected edges connect exogenous nodes.>";
    }
}





