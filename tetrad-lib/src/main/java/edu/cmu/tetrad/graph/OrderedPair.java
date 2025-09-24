///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableExcluded;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * An ordered pair of objects. This does not serialize well, unfortunately.
 *
 * @param <E> The type of the objects in the pair.
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class OrderedPair<E> implements TetradSerializable, TetradSerializableExcluded {
    private static final long serialVersionUID = 23L;

    /**
     * The "First" node.
     */
    private final E first;


    /**
     * The "second" node.
     */
    private final E second;


    /**
     * <p>Constructor for OrderedPair.</p>
     *
     * @param first  a E object
     * @param second a E object
     */
    public OrderedPair(E first, E second) {
        if (first == null) {
            throw new NullPointerException("First node must not be null.");
        }
        if (second == null) {
            throw new NullPointerException("Second node must not be null.");
        }
        this.first = first;
        this.second = second;
    }

    //============================== Public methods =============================//

    /**
     * <p>Getter for the field <code>first</code>.</p>
     *
     * @return a E object
     */
    public E getFirst() {
        return this.first;
    }

    /**
     * <p>Getter for the field <code>second</code>.</p>
     *
     * @return a E object
     */
    public E getSecond() {
        return this.second;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        return 13 * this.first.hashCode() + 67 * this.second.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (!(o instanceof OrderedPair)) throw new IllegalArgumentException();
        OrderedPair<E> that = (OrderedPair<E>) o;
        return this.first.equals(that.first) && this.second.equals(that.second);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "<" + this.first + ", " + this.second + ">";
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




