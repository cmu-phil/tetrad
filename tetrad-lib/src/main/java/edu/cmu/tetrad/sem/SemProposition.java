///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import java.util.List;

/**
 * Represents propositions over the variables of a particular BayesIm describing
 * and event of a fairly general sort--namely, conjunctions of propositions that
 * particular variables take on values from a particular disjunctive list of
 * categories. For example, X1 = 1 or 2 & X2 = 3 & X3 = 1 or 3 & X4 = 2 or 3 or
 * 5. The proposition is created by allowing or disallowing particular
 * categories. Notice that "knowing nothing" about a variable is the same as
 * saying that all categories for that variable are allowed, so the proposition
 * by default allows all categories for all variables--i.e. it is a tautology.
 *
 * @author Joseph Ramsey
 */
public final class SemProposition implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private SemIm semIm;

    /**
     * @serial Cannot be null.
     */
    private double[] values;

    //===========================CONSTRUCTORS===========================//

    /**
     * Creates a new Proposition which allows all values.
     */
    private SemProposition(SemIm semIm) {
        if (semIm == null) {
            throw new NullPointerException();
        }

        this.semIm = semIm;
        this.values = new double[semIm.getVariableNodes().size()];

        for (int i = 0; i < values.length; i++) {
            values[i] = Double.NaN;
        }
    }

    public static SemProposition tautology(SemIm semIm) {
        return new SemProposition(semIm);
    }

    /**
     * Copies the info out of the old proposition into a new proposition for the
     * new BayesIm.
     */
    public SemProposition(SemIm semIm, SemProposition proposition) {
        this(semIm);

        if (proposition == null) {
            throw new NullPointerException();
        }
    }

    /**
     * Copies the info out of the old proposition into a new proposition for the
     * new BayesIm.
     */
    public SemProposition(SemProposition proposition) {
        this.semIm = proposition.semIm;
        this.values = new double[proposition.values.length];
        System.arraycopy(proposition.values, 0, this.values, 0, values.length);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemProposition serializableInstance() {
        return new SemProposition(SemIm.serializableInstance());
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * @return the Bayes IM that this is a proposition for.
     */
    public SemIm getSemIm() {
        return this.semIm;
    }

    /**
     * @return the number of variables for the proposition.
     */
    public int getNumVariables() {
        return values.length;
    }

    /**
     * @return the index of the variable with the given name, or -1 if such a
     * variable does not exist.
     */
    public int getNodeIndex(String name) {

        return -1;
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        SemProposition proposition = (SemProposition) o;

        if (!(semIm == proposition.semIm)) {
            return false;
        }

        for (int i = 0; i < values.length; i++) {
            if (Double.isNaN(values[i]) && Double.isNaN(proposition.values[i])) {
                continue;
            } else if (values[i] != proposition.values[i]) {
                return false;
            }
        }

        return true;
    }

    public int hashCode() {
        int hashCode = 37;
        hashCode = 19 * hashCode + semIm.hashCode();
        hashCode = 19 * hashCode + values.hashCode();
        return hashCode;
    }

    public String toString() {
        List nodes = semIm.getVariableNodes();
        StringBuilder buf = new StringBuilder();
        buf.append("\nProposition: ");

        for (int i = 0; i < nodes.size(); i++) {
            Node node = (Node) nodes.get(i);
            buf.append("\n").append(node).append(" = ").append(values[i]);
        }

        return buf.toString();
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    public double getValue(int i) {
        return values[i];
    }

    public void setValue(int i, double value) {
        values[i] = value;
    }

    public double getValue(Node node) {
        List nodes = semIm.getVariableNodes();
        return values[nodes.indexOf(node)];
    }

    public void setValue(Node node, double value) {
        List nodes = semIm.getVariableNodes();
        values[nodes.indexOf(node)] = value;
    }
}





