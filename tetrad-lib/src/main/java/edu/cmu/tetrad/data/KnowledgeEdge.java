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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Implements a knowledge edge X--&gt;Y as a simple ordered pair of strings.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class KnowledgeEdge implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The tail node of the edge.
     */
    private final String from;

    /**
     * The head node of the edge.
     */
    private final String to;

    /**
     * Constructs a knowledge edge for from--&gt;to.
     *
     * @param from a {@link java.lang.String} object
     * @param to   a {@link java.lang.String} object
     */
    public KnowledgeEdge(String from, String to) {
        if (from == null || to == null) {
            throw new NullPointerException();
        }

        this.from = from;
        this.to = to;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.KnowledgeEdge} object
     */
    public static KnowledgeEdge serializableInstance() {
        return new KnowledgeEdge("X", "Y");
    }

    /**
     * <p>Getter for the field <code>from</code>.</p>
     *
     * @return the tail node of the edge.
     */
    public String getFrom() {
        return this.from;
    }

    /**
     * <p>Getter for the field <code>to</code>.</p>
     *
     * @return the head node of the edge.
     */
    public String getTo() {
        return this.to;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reteurns true if (from1, to1) == (from2, to2).
     */
    public boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        if (!(object instanceof KnowledgeEdge pair)) {
            return false;
        }

        return this.from.equals(pair.from) && this.to.equals(pair.to);
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a good hashcode.
     */
    public int hashCode() {
        int hashCode = 31 + this.from.hashCode();
        return 37 * hashCode + this.to.hashCode();
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.from + "-->" + this.to;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}





