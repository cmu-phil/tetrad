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
 * A graph constraint permitting only directed or undirected edges to be added
 * to a graph.
 *
 * @author Joseph Ramsey
 */
public final class DirectedUndirectedOnly implements GraphConstraint {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS===========================//

    public DirectedUndirectedOnly() {

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static DirectedUndirectedOnly serializableInstance() {
        return new DirectedUndirectedOnly();
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * Checks whether a new edge satisfies the constraint in this graph.
     *
     * @param edge  the edge to check.
     * @param graph the graph to check.
     * @return true if the new edge may be added, false if not.
     */
    public boolean isEdgeAddable(Edge edge, Graph graph) {

        return (Edges.isDirectedEdge(edge) || Edges.isNondirectedEdge(edge) ||
                Edges.isUndirectedEdge(edge));
    }

    /**
     * @param node  the node to check.
     * @param graph the graph to check.
     * @return true.
     */
    public boolean isNodeAddable(Node node, Graph graph) {
        return true;
    }

    /**
     * @param edge  the edge to check.
     * @param graph the graph to check.
     * @return true.
     */
    public boolean isEdgeRemovable(Edge edge, Graph graph) {
        return true;
    }

    /**
     * @param node
     * @param graph the graph to check.
     * @return true.
     */
    public boolean isNodeRemovable(Node node, Graph graph) {
        return true;
    }

    /**
     * @return this representation.
     */
    public String toString() {
        return "<Directed or undirected edges only.>";
    }
}





