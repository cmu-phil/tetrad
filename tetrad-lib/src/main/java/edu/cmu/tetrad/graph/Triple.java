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

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores a triple (x, y, z) of nodes. Note that (x, y, z) = (z, y, x). Useful for marking graphs.
 *
 * @author josephramsey, after Frank Wimberly.
 * @version $Id: $Id
 */
public final class Triple implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    // Note: Switching all uses of Underline to Triple, since they did the
    // same thing, and this allows for some useful generalizations, especially
    // since for triples it is always the case that (x, y, z) = (z, y, x).

    /**
     * The first node in the triple.
     */
    private final Node x;

    /**
     * The second node in the triple.
     */
    private final Node y;

    /**
     * The third node in the triple.
     */
    private final Node z;

    /**
     * Constructs a triple of nodes.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Triple(Node x, Node y, Node z) {
        if (x == null || y == null || z == null) {
            throw new NullPointerException();
        }

        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.graph.Triple} object
     */
    public static Triple serializableInstance() {
        return new Triple(new GraphNode("X"), new GraphNode("Y"), new GraphNode("Z"));
    }

    /**
     * <p>pathString.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param x     a {@link edu.cmu.tetrad.graph.Node} object
     * @param y     a {@link edu.cmu.tetrad.graph.Node} object
     * @param z     a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.lang.String} object
     */
    public static String pathString(Graph graph, Node x, Node y, Node z) {
        List<Node> path = new ArrayList<>();
        path.add(x);
        path.add(y);
        path.add(z);
        return GraphUtils.pathString(graph, path);
    }

    /**
     * <p>Getter for the field <code>x</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getX() {
        return this.x;
    }

    /**
     * <p>Getter for the field <code>y</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getY() {
        return this.y;
    }

    /**
     * <p>Getter for the field <code>z</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getZ() {
        return this.z;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hash = 17;
        hash += 19 * this.x.hashCode() * this.z.hashCode();
        hash += 23 * this.y.hashCode();
        return hash;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof Triple triple)) {
            return false;
        }

        return (this.x == triple.x && this.y == triple.y &&
                this.z == triple.z)
               || (this.x == triple.z && this.y == triple.y &&
                   this.z == triple.x);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "<" + this.x + ", " + this.y + ", " + this.z + ">";
    }

    /**
     * <p>alongPathIn.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a boolean
     */
    public boolean alongPathIn(Graph graph) {
        return graph.isAdjacentTo(this.x, this.y) && graph.isAdjacentTo(this.y, this.z) && this.x != this.z;
    }
}



