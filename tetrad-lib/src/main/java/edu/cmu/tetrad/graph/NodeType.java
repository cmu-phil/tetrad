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

import java.io.ObjectStreamException;
import java.io.Serial;

/**
 * A typesafe enum of the types of the types of nodes in a graph (MEASURED, LATENT, ERROR).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class NodeType implements TetradSerializable {
    /**
     * Constant <code>MEASURED</code>
     */
    public static final NodeType MEASURED = new NodeType("Measured");
    /**
     * Constant <code>LATENT</code>
     */
    public static final NodeType LATENT = new NodeType("Latent");
    /**
     * Constant <code>ERROR</code>
     */
    public static final NodeType ERROR = new NodeType("Error");
    /**
     * Constant <code>SESSION</code>
     */
    public static final NodeType SESSION = new NodeType("Session");
    /**
     * Constant <code>RANDOMIZE</code>
     */
    public static final NodeType RANDOMIZE = new NodeType("Randomize");
    /**
     * Constant <code>LOCK</code>
     */
    public static final NodeType LOCK = new NodeType("Lock");
    /**
     * Constant <code>NO_TYPE</code>
     */
    public static final NodeType NO_TYPE = new NodeType("No type");
    /**
     * Constant <code>TYPES</code>
     */
    public static final NodeType[] TYPES = {NodeType.MEASURED, NodeType.LATENT, NodeType.ERROR, NodeType.NO_TYPE, NodeType.RANDOMIZE, NodeType.LOCK};
    private static final long serialVersionUID = 23L;
    // Declarations required for serialization.
    private static int nextOrdinal;
    /**
     * The name of this type.
     */
    private final transient String name;

    /**
     * The ordinal of this type.
     */
    private final int ordinal = NodeType.nextOrdinal++;

    /**
     * Protected constructor for the types; this allows for extension in case anyone wants to add formula types.
     */
    private NodeType(String name) {
        this.name = name;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.graph.NodeType} object
     */
    public static NodeType serializableInstance() {
        return NodeType.MEASURED;
    }

    /**
     * Prints out the name of the type.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.name;
    }

    /**
     * Returns the ordinal of this type.
     *
     * @return a int
     * @throws java.io.ObjectStreamException if any.
     */
    @Serial
    Object readResolve() throws ObjectStreamException {
        return NodeType.TYPES[this.ordinal]; // Canonicalize.
    }
}





