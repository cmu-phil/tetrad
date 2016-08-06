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

package edu.cmu.tetrad.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;

/**
 * <p>Implements a term in a polymonial whose variables are mapped to indices in
 * in the set {0, 1, 2, ...}. The term has a coefficient and a freely generated
 * list of variables. For example, if "x" -> 0, "y" -> 1, "z" -> 2, then the
 * following terms are represented as follows, where "Vi" stands for the
 * variable mapped to index i:</p> </p> <ol> <li> 2.5x -> 2.5*(V0)(V0) <li>
 * 1.7xyz^2 -> 1.7*(V0)(V1)(V2)(V2) <li> -5.0z^3y^2 -> -5.0*(V2)(V2)(V2)(V1)(V1)
 * </ol>
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class PolynomialTerm implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The coefficient of the term.
     *
     * @serial
     */
    private double coefficient;

    /**
     * The variables of the term.
     *
     * @serial
     */
    private int[] variables;

    //=================================CONSTRUCTORS========================//

    /**
     * Constructs a term.
     */
    public PolynomialTerm(double coefficient, int[] variables) {
        if (variables == null) {
            throw new NullPointerException("Variables cannot be null.");
        }

        this.variables = new int[variables.length];
        System.arraycopy(variables, 0, this.variables, 0, variables.length);

        this.coefficient = coefficient;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static PolynomialTerm serializableInstance() {
        return new PolynomialTerm(0.0, new int[0]);
    }

    //==================================PUBLIC METHODS=====================//

    /**
     * Returns the coefficient.
     */
    public double getCoefficient() {
        return this.coefficient;
    }

    /**
     * Sets the coefficient.
     */
    public void setCoefficient(double coefficient) {
        this.coefficient = coefficient;
    }

    /**
     * Returns the number of variables in this term.
     */
    public int getNumVariables() {
        return this.variables.length;
    }

    /**
     * Returns the index'th variable.
     */
    public int getVariable(int index) {
        return this.variables[index];
    }

    /**
     * Returns true iff the given variable list is equal to the variable list of
     * this term.
     */
    public boolean isVariableListEqual(int[] variables) {
        return Arrays.equals(variables, this.variables);
    }

    /**
     * Returns the highest variable index in this term.
     */
    public int getMaxIndex() {
        int max = 0;
        for (int variable : variables) {
            if (variable > max) {
                max = variable;
            }
        }
        return max;
    }

    /**
     * Evaluates the term.
     */
    public double evaluate(double[] values) {
        double product = this.coefficient;
        for (int variable : this.variables) {
            product *= values[variable];
        }
        return product;
    }

    /**
     * Prints out a representation of the term.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(this.coefficient);
        if (this.variables.length > 0) {
            buf.append("*");
        }
        for (int variable : this.variables) {
            buf.append("(V");
            buf.append(variable);
            buf.append(")");
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

        if (variables == null) {
            throw new NullPointerException();
        }
    }
}





