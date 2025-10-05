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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetrad.util.Version;
import edu.cmu.tetradapp.util.TetradMetadataIndirectRef;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Date;

/**
 * Represents the Tetrad session that is serialized oud loaded back in. Basically, it's a SessionWrapper with metadata.
 * These fields have been encapsulated into their own class so that more metadata fields can be added in the future
 * without disturbing serialization. It must be the case that metadata can load even if the SessionWrapper cannot load.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TetradMetadata
        implements TetradSerializable, TetradMetadataIndirectRef {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The version of Tetrad that saved this session out.
     *
     * @serial Cannot be null.
     */
    private final Version version;

    /**
     * The date and time this Tetrad session was created.
     *
     * @serial Cannot be null.
     */
    private final Date date;

    //===========================CONSTRUCTORS=============================//

    /**
     * <p>Constructor for TetradMetadata.</p>
     */
    public TetradMetadata() {
        try {
            this.version = Version.currentViewableVersion();
        } catch (Exception e) {
            throw new RuntimeException("Can't retrive the current version of this tetrad release.");
        }

        this.date = new Date();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.TetradMetadata} object
     * @see TetradSerializableUtils
     */
    public static TetradMetadata serializableInstance() {
        return new TetradMetadata();
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * <p>Getter for the field <code>version</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Version} object
     */
    public Version getVersion() {
        return this.version;
    }

    /**
     * <p>Getter for the field <code>date</code>.</p>
     *
     * @return a {@link java.util.Date} object
     */
    public Date getDate() {
        return this.date;
    }

    //============================PRIVATE METHODS=======================//

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






