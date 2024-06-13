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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Holds an ordered pair (index, lag) to represent a causal parent of a factor, where the factor at the given index is
 * independently known.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class IndexedParent implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The index of the parent.
     */
    private final int index;

    /**
     * The lag of the parent.
     */
    private final int lag;

    //================================CONSTRUCTORS========================//

    /**
     * Constructs a new index parent.
     *
     * @param index a int
     * @param lag   a int
     */
    public IndexedParent(int index, int lag) {

        if (index < 0) {
            throw new IllegalArgumentException("Index must be >= 0: " + index);
        }

        if (lag < 0) {
            throw new IllegalArgumentException("Lag must be >= 0: " + lag);
        }

        this.index = index;
        this.lag = lag;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.IndexedParent} object
     */
    public static IndexedParent serializableInstance() {
        return new IndexedParent(0, 1);
    }

    //===============================PUBLIC METHODS======================//

    /**
     * Returns the index of the parent.
     *
     * @return a int
     */
    public int getIndex() {
        return this.index;
    }

    /**
     * Returns the lag of the parent.
     *
     * @return a int
     */
    public int getLag() {
        return this.lag;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns true iff the lags and indices are equal.
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof IndexedParent c)) {
            return false;
        }
        return c.getIndex() == this.getIndex() && c.getLag() == this.getLag();
    }

    /**
     * Prints out the factor index and lag.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "IndexedParent, index = " + getIndex() + ", lag = " + getLag();
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





