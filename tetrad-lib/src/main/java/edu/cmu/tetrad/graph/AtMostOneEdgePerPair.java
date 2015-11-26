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
 * A graph constraint prohibitting more than one edge per node pair from being
 * added to the graph.
 *
 * @author Joseph Ramsey
 */
public final class AtMostOneEdgePerPair implements GraphConstraint {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS===========================//

    public AtMostOneEdgePerPair() {

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static AtMostOneEdgePerPair serializableInstance() {
        return new AtMostOneEdgePerPair();
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * @return true iff the new edge may be added.
     */
    public boolean isEdgeAddable(Edge edge, Graph graph) {
        Node node1 = edge.getNode1();
        Node node2 = edge.getNode2();

        return graph.getEdges(node1, node2).isEmpty();
    }

    /**
     * @return true iff the node may be added.
     */
    public boolean isNodeAddable(Node node, Graph graph) {
        return true;
    }

    /**
     * @return true;
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
     * @return a string representation of the constraint.
     *
     * @return this representation.
     */
    public String toString() {
        return "<At most one edge per node pair.>";
    }
}





