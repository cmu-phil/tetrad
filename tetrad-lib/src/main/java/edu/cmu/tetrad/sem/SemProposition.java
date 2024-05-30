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
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;

/**
 * Represents propositions over the variables of a particular BayesIm describing and event of a fairly general
 * sort--namely, conjunctions of propositions that particular variables take on values from a particular disjunctive
 * list of categories. For example, X1 = 1 or 2 and X2 = 3 and X3 = 1 or 3 and X4 = 2 or 3 or 5. The proposition is
 * created by allowing or disallowing particular categories. Notice that "knowing nothing" about a variable is the same
 * as saying that all categories for that variable are allowed, so the proposition by default allows all categories for
 * all variables--i.e. it is a tautology.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SemProposition implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents a final variable semIm.
     */
    private final SemIm semIm;

    /**
     * Represents a private final array of double values.
     * <p>
     * This variable is part of the SemProposition class in the Tetrad API. SemProposition is a class that represents a
     * Proposition for a Bayes IM (Bayesian Inference Model). It allows for semantic checks when deserializing.
     * <p>
     * The values array contains the double values associated with the Proposition.
     */
    private final double[] values;

    /**
     * Creates a new Proposition which allows all values.
     *
     * @param semIm a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemProposition(SemIm semIm) {
        if (semIm == null) {
            throw new NullPointerException();
        }

        this.semIm = semIm;
        this.values = new double[semIm.getVariableNodes().size()];

        Arrays.fill(this.values, Double.NaN);
    }

    /**
     * Creates a new SemProposition object by copying the values and semIm from the given proposition.
     *
     * @param proposition the SemProposition object to be copied
     */
    public SemProposition(SemProposition proposition) {
        this.semIm = proposition.semIm;
        this.values = Arrays.copyOf(proposition.values, proposition.values.length);
    }

    /**
     * Creates a tautology by wrapping the given SemIm object.
     *
     * @param semIm the SemIm object to be wrapped
     * @return a SemProposition object representing the tautology
     */
    public static SemProposition tautology(SemIm semIm) {
        return new SemProposition(semIm);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemProposition} object
     */
    public static SemProposition serializableInstance() {
        return new SemProposition(SemIm.serializableInstance());
    }

    /**
     * Retrieves the SemIm object associated with this SemProposition.
     *
     * @return the SemIm object
     */
    public SemIm getSemIm() {
        return this.semIm;
    }

    /**
     * Returns the number of variables in the current object.
     *
     * @return the number of variables
     */
    public int getNumVariables() {
        return this.values.length;
    }

    /**
     * Returns the index of the node.
     *
     * @return the index of the node
     */
    public int getNodeIndex() {

        return -1;
    }

    /**
     * Compares this SemProposition object with the specified object for equality.
     *
     * @param o the object to be compared with this SemProposition
     * @return true if the given object is equal to this SemProposition, false otherwise
     * @throws IllegalArgumentException if the given object is not an instance of SemProposition
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof SemProposition proposition)) {
            throw new IllegalArgumentException();
        }

        if (!(this.semIm == proposition.semIm)) {
            return false;
        }

        for (int i = 0; i < this.values.length; i++) {
            if (!(Double.isNaN(this.values[i]) && Double.isNaN(proposition.values[i]))) {
                if (this.values[i] != proposition.values[i]) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Calculates the hash code for this object.
     *
     * @return the hash code value for this object
     */
    public int hashCode() {
        int hashCode = 37;
        hashCode = 19 * hashCode + this.semIm.hashCode();
        hashCode = 19 * hashCode + Arrays.hashCode(this.values);
        return hashCode;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    public String toString() {
        List<Node> nodes = this.semIm.getVariableNodes();
        StringBuilder buf = new StringBuilder();
        buf.append("\nProposition: ");

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            buf.append("\n").append(node).append(" = ").append(this.values[i]);
        }

        return buf.toString();
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves the value at the specified index.
     *
     * @param i the index of the value to retrieve
     * @return the value at the specified index
     */
    public double getValue(int i) {
        return this.values[i];
    }

    /**
     * Sets the value at the specified index in the array of values.
     *
     * @param i     the index of the value to set
     * @param value the value to set
     */
    public void setValue(int i, double value) {
        this.values[i] = value;
    }

    /**
     * Retrieves the value associated with the given node.
     *
     * @param node the node for which the value is retrieved
     * @return the value associated with the given node
     */
    public double getValue(Node node) {
        List<Node> nodes = this.semIm.getVariableNodes();
        return this.values[nodes.indexOf(node)];
    }

    /**
     * Sets the value for a given node in the SemProposition object.
     *
     * @param node  the node for which the value is to be set
     * @param value the value to be assigned to the node
     */
    public void setValue(Node node, double value) {
        List<Node> nodes = this.semIm.getVariableNodes();
        this.values[nodes.indexOf(node)] = value;
    }
}





