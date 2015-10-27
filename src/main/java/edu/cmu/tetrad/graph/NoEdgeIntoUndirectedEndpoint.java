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

/**
 * A graph constraint prohibitting edges with arrow endpoints from pointing to
 * nodes held in common with undirected edges.  Thus, if A --- B is an
 * undirected edge from A to B, then this constraint prevents there being an
 * edge, e.g., C --> A or C <--> B which shares either the node A or B, with an
 * arrow into the shared endpoint.
 *
 * @author Joseph Ramsey
 */
public final class NoEdgeIntoUndirectedEndpoint implements GraphConstraint {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS===========================//

    public NoEdgeIntoUndirectedEndpoint() {

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static NoEdgeIntoUndirectedEndpoint serializableInstance() {
        return new NoEdgeIntoUndirectedEndpoint();
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * Returns true iff the edge may be added. See main javadoc.
     */
    public boolean isEdgeAddable(Edge edge, Graph graph) {

        if (edge.getEndpoint1() == Endpoint.ARROW) {
            if (existsAttachedUndirectedEdge(edge.getNode1(), graph)) {
                return false;
            }
        }
        else if (edge.getEndpoint2() == Endpoint.ARROW) {
            if (existsAttachedUndirectedEdge(edge.getNode2(), graph)) {
                return false;
            }
        }
        else
        if (Edges.isNondirectedEdge(edge) || Edges.isUndirectedEdge(edge)) {
            if (existsAttachedIntoEdge(edge.getNode1(), graph)) {
                return false;
            }
            else if (existsAttachedIntoEdge(edge.getNode2(), graph)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true.
     */
    public boolean isNodeAddable(Node node, Graph graph) {
        return true;
    }

    /**
     * Returns true.
     */
    public boolean isEdgeRemovable(Edge edge, Graph graph) {
        return true;
    }

    /**
     * Returns true.
     */
    public boolean isNodeRemovable(Node node, Graph graph) {
        return true;
    }

    /**
     * Returns true iff there is an undirected edge attached to the given node.
     */
    private boolean existsAttachedUndirectedEdge(Node node, Graph graph) {
        for (Edge edge1 : graph.getEdges(node)) {
            if (Edges.isNondirectedEdge(edge1) || Edges.isUndirectedEdge(edge1))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true iff there is an into edge attached to the given node.
     */
    private boolean existsAttachedIntoEdge(Node node, Graph graph) {
        for (Edge edge1 : graph.getEdges(node)) {
            Endpoint ept = edge1.getProximalEndpoint(node);

            if (ept == Endpoint.ARROW) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns a string representation of this constraint.
     */
    public String toString() {
        return "<Undirected edges block into edges.>";
    }
}





