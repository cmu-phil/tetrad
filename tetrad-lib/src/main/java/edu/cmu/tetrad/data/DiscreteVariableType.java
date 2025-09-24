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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.*;

/**
 * Type-safe enum of discrete variable types. A nominal discrete variable is one in which the categories are in no
 * particular order. An ordinal discrete variable is one in which the categories in a particular order. An indexical
 * discrete variable is an ordinal discrete variable for which relative distances between categories can be specified.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class DiscreteVariableType implements TetradSerializable {
    /**
     * Constant <code>NOMINAL</code>
     */
    public static final DiscreteVariableType NOMINAL =
            new DiscreteVariableType("Nominal");
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constant <code>ORDINAL</code>
     */
    private static final DiscreteVariableType ORDINAL =
            new DiscreteVariableType("Ordinal");

    /**
     * Constant <code>INDEXICAL</code>
     */
    private static final DiscreteVariableType[] TYPES = {DiscreteVariableType.NOMINAL, DiscreteVariableType.ORDINAL};
    // Declarations required for serialization.
    private static int nextOrdinal;

    /**
     * The name of this type.
     */
    private final transient String name;

    /**
     * The ordinal of this type.
     */
    private final int ordinal = DiscreteVariableType.nextOrdinal++;

    /**
     * Protected constructor for the types; this allows for extension in case anyone wants to add formula types.
     */
    private DiscreteVariableType(String name) {
        this.name = name;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.DiscreteVariableType} object
     */
    public static DiscreteVariableType serializableInstance() {
        return DiscreteVariableType.NOMINAL;
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
        return DiscreteVariableType.TYPES[this.ordinal]; // Canonicalize.
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






