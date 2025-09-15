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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Specifies how a column (continuous or discrete) should be discretized. For a discrete column the mapping is int[]
 * remap; for a continuous column the mapping is double[] cutoffs. The splitNames are the string labels for the
 * splitNames. This is just a small immutable class that columns can map to in order to remember how discretizations
 * were done so that the user doesn't have to keep typing in information over and over again.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SplitCasesSpec implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Breakpoints, for continuous data.
     */
    private final int[] breakpoints;

    /**
     * Split names.
     */
    private final List<String> splitNames;

    /**
     * Sample size.
     */
    private final int sampleSize;

    /**
     * <p>Constructor for SplitCasesSpec.</p>
     *
     * @param sampleSize  a int
     * @param breakpoints an array of  objects
     * @param splits      a {@link java.util.List} object
     */
    public SplitCasesSpec(int sampleSize, int[] breakpoints,
                          List<String> splits) {
        this.sampleSize = sampleSize;
        this.breakpoints = breakpoints;
        this.splitNames = splits;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.SplitCasesSpec} object
     */
    public static SplitCasesSpec serializableInstance() {
        return new SplitCasesSpec(0, new int[0], new ArrayList<>());
    }

    /**
     * <p>Getter for the field <code>splitNames</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getSplitNames() {
        return this.splitNames;
    }

    /**
     * <p>Getter for the field <code>breakpoints</code>.</p>
     *
     * @return an array of  objects
     */
    public int[] getBreakpoints() {
        return this.breakpoints;
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
     * <p>Getter for the field <code>sampleSize</code>.</p>
     *
     * @return a int
     */
    public int getSampleSize() {
        return this.sampleSize;
    }


}




