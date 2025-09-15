/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Enumerates the algorithm types for BuildPureClusters, and Purify.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public enum BpcAlgorithmType implements TetradSerializable {

    /**
     * This one will work and does a good job for medium-sized models.
     */
    BUILD_PURE_CLUSTERS,

    /**
     * This will work and does a good job for small models, no more than 4 latents.
     */
    SIMPLIFIED_BPC_DEPTH_0,

    /**
     * This is very slow.
     */
    SIMPLIFIED_BPC_DEPTH_1,

    /**
     * Even slower.
     */
    SIMPLIFIED_BPC,

    /**
     * This option doesn't do any purify.
     */
    TETRAD_PURIFY_WASHDOWN,

    /**
     * FOFC algorithm
     */
    FIND_ONE_FACTOR_CLUSTERS,

    /**
     * FTFC algorithm.
     */
    FIND_TWO_FACTOR_CLUSTERS;

    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    private static final long serialVersionUID = 23L;

    /**
     * Returns the serializable instance of the algorithm type.
     *
     * @return The serializable instance of the algorithm type.
     */
    public static BpcAlgorithmType serializableInstance() {
        return BpcAlgorithmType.BUILD_PURE_CLUSTERS;
    }

    /**
     * Returns the algorithm descriptions.
     *
     * @return The algorithm descriptions.
     */
    public static BpcAlgorithmType[] getAlgorithmDescriptions() {
        return new BpcAlgorithmType[]{
                BpcAlgorithmType.BUILD_PURE_CLUSTERS,
//                BpcAlgorithmType.SIMPLIFIED_BPC,
                BpcAlgorithmType.TETRAD_PURIFY_WASHDOWN,
//                BpcAlgorithmType.FIND_ONE_FACTOR_CLUSTERS,
//                BpcAlgorithmType.FIND_TWO_FACTOR_CLUSTERS
        };
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


