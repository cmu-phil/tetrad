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

import edu.cmu.tetrad.sem.SemUpdater;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Wraps a Bayes Updater for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemUpdaterWrapper implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The wrapped Bayes Updater.
     */
    private final SemUpdater semUpdater;

    /**
     * The name of the model.
     */
    private String name;

    //=============================CONSTRUCTORS============================//

    /**
     * <p>Constructor for SemUpdaterWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     */
    public SemUpdaterWrapper(SemEstimatorWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.semUpdater = new SemUpdater(wrapper.getEstimatedSemIm());

    }

    /**
     * <p>Constructor for SemUpdaterWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     */
    public SemUpdaterWrapper(SemImWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.semUpdater = new SemUpdater(wrapper.getSemIm());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
//        return new SemUpdaterWrapper(SemImWrapper.serializableInstance());
    }

    //==============================PUBLIC METHODS========================//

    /**
     * <p>Getter for the field <code>semUpdater</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemUpdater} object
     */
    public SemUpdater getSemUpdater() {
        return this.semUpdater;
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

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }
}






