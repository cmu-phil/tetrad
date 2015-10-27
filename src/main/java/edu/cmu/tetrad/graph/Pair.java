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

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Stores a pair (x, y) of nodes. Note that (x, y) = (y, x). Useful
 * for marking graphs.
 *
 * @author Joseph Ramsey, after Frank Wimberly.
 */
public final class Pair implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private Node x;
    private Node y;

    /**
     * Constructs a triple of nodes.
     */
    public Pair(Node x, Node y) {
        if (x == null || y == null) {
            throw new NullPointerException();
        }

        this.x = x;
        this.y = y;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static Pair serializableInstance() {
        return new Pair(new GraphNode("X"), new GraphNode("Y"));
    }

    public final Node getX() {
        return x;
    }

    public final Node getY() {
        return y;
    }

    public final int hashCode() {
        int hash = 17;
        hash += 19 * (x.hashCode() + y.hashCode());
        return hash;
    }

    public final boolean equals(Object obj) {
        if (!(obj instanceof Pair)) {
            return false;
        }

        Pair triple = (Pair) obj;
        return (x.equals(triple.x) && y.equals(triple.y))
                || (y.equals(triple.x) && x.equals(triple.y));
    }

    public String toString() {
        return "<" + x + ", " + y + ">";
    }
}


