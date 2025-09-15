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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;

/**
 * Stores information for a BayesIm about evidence we have for each variable as well as whether each variable has been
 * manipulated.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SemManipulation implements TetradSerializable {
    private static final long serialVersionUID = 23L;

    /**
     * Bayes IM that this is evidence for.
     *
     * @serial Cannot be null.
     */
    private final SemIm semIm;

    /**
     * An array indicating whether each variable in turn is manipulated.
     *
     * @serial Cannot be null.
     */
    private final boolean[] manipulated;

    /**
     * Constructs a container for evidence for the given Bayes IM.
     *
     * @param semIm a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemManipulation(SemIm semIm) {
        if (semIm == null) {
            throw new NullPointerException();
        }

        this.semIm = semIm;
        this.manipulated = new boolean[semIm.getVariableNodes().size()];
    }

    /**
     * <p>Constructor for SemManipulation.</p>
     *
     * @param manipulation a {@link edu.cmu.tetrad.sem.SemManipulation} object
     */
    public SemManipulation(SemManipulation manipulation) {
        this.semIm = manipulation.semIm;
        this.manipulated = Arrays.copyOf(manipulation.manipulated, manipulation.manipulated.length);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemManipulation} object
     */
    public static SemManipulation serializableInstance() {
        return new SemManipulation(SemIm.serializableInstance());
    }

    /**
     * <p>getNodeIndex.</p>
     *
     * @param nodeName a {@link java.lang.String} object
     * @return a int
     */
    public int getNodeIndex(String nodeName) {
        List<Node> nodes = this.semIm.getSemPm().getVariableNodes();

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (node.getName().equals(nodeName)) {
                return i;
            }
        }

        return -1;
    }

    private int getNumNodes() {
        return this.semIm.getVariableNodes().size();
    }

    /**
     * <p>getNode.</p>
     *
     * @param nodeIndex a int
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getNode(int nodeIndex) {
        return this.semIm.getVariableNodes().get(nodeIndex);
    }

    /**
     * <p>isManipulated.</p>
     *
     * @param nodeIndex a int
     * @return a boolean
     */
    public boolean isManipulated(int nodeIndex) {
        return this.manipulated[nodeIndex];
    }

    /**
     * <p>Setter for the field <code>manipulated</code>.</p>
     *
     * @param nodeIndex   a int
     * @param manipulated a boolean
     */
    public void setManipulated(int nodeIndex, boolean manipulated) {
        this.manipulated[nodeIndex] = manipulated;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append("\nManipulation:");
        buf.append("\n");

        for (int i = 0; i < getNumNodes(); i++) {
            buf.append(isManipulated(i) ? "(Man)" : "     ");
            buf.append("\t");
        }

        return buf.toString();
    }

    /**
     * Compares the current instance with the specified object for equality.
     *
     * @param o the object to be compared with the current instance
     * @return true if the specified object is equal to the current instance, false otherwise.
     * @throws IllegalArgumentException if the specified object is not an instance of SemManipulation.
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof SemManipulation evidence)) {
            throw new IllegalArgumentException();
        }

        if (!(this.semIm == evidence.semIm)) {
            return false;
        }

        for (int i = 0; i < this.manipulated.length; i++) {
            if (this.manipulated[i] != evidence.manipulated[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hashCode = 37;
        hashCode = 19 * hashCode + this.semIm.hashCode();
        hashCode = 19 * hashCode + Arrays.hashCode(this.manipulated);
        return hashCode;
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
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization to restore the
     * state of the object.
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







