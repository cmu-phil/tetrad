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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
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
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }
}






