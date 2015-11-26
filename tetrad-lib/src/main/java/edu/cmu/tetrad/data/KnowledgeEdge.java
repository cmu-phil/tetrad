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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Implements a knowledge edge X-->Y as a simple ordered pair of strings.
 *
 * @author Joseph Ramsey
 */
public final class KnowledgeEdge implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * @serial
     */
    private String from;

    /**
     * @serial
     */
    private String to;

    //===============================CONSTRUCTORS=======================//

    /**
     * Constructs a knowledge edge for from-->to.
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
     */
    public static KnowledgeEdge serializableInstance() {
        return new KnowledgeEdge("X", "Y");
    }

    //===============================PUBLIC METHODS======================//

    /**
     * @return the tail node of the edge.
     */
    public final String getFrom() {
        return from;
    }

    /**
     * @return the head node of the edge.
     */
    public final String getTo() {
        return to;
    }

    /**
     * Reteurns true if (from1, to1) == (from2, to2).
     */
    public final boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        if (!(object instanceof KnowledgeEdge)) {
            return false;
        }

        KnowledgeEdge pair = (KnowledgeEdge) object;
        return from.equals(pair.from) && to.equals(pair.to);
    }

    /**
     * @return a good hashcode.
     */
    public final int hashCode() {
        int hashCode = 31 + from.hashCode();
        return 37 * hashCode + to.hashCode();
    }

    public String toString() {
        return from + "-->" + to;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (from == null) {
            throw new NullPointerException();
        }

        if (to == null) {
            throw new NullPointerException();
        }
    }
}





