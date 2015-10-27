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
 * This graph constraint prohibitting partially oriented edges (o-->) from being
 * added to the graph.
 * <p/>
 * TODO: "Half-directed" edges are named "Partially oriented" edges in CPS 2000.
 * This cannot be renamed currently, because it would break serialization. But
 * it the serialization history can be forgotten at any point in the future, it
 * can be renamed then.
 *
 * @author Joseph Ramsey
 */
public final class NoHalfdirectedEdges implements GraphConstraint {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS===========================//

    public NoHalfdirectedEdges() {

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static NoHalfdirectedEdges serializableInstance() {
        return new NoHalfdirectedEdges();
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
        return !Edges.isPartiallyOrientedEdge(edge);
    }

    /**
     * Checks whether a new node satisfies the constraint in this graph.
     *
     * @param node  the node to check.
     * @param graph the graph to check.
     * @return true if the new node may be added, false if not.
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
     * Returns a string representation of the constraint.
     *
     * @return this representation.
     */
    public String toString() {
        return "<No partially oriented edges.>";
    }
}





