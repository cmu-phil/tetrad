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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Arrays;

/**
 * <p>Implements a term in a polymonial whose variables are mapped to indices in
 * in the set {0, 1, 2, ...}. The term has a coefficient and a freely generated list of variables. For example, if "x"
 * -&gt; 0, "y" -&gt; 1, "z" -&gt; 2, then the following terms are represented as follows, where "Vi" stands for the
 * variable mapped to index i: <ol> <li> 2.5x -&gt; 2.5*(V0)(V0) <li> 1.7xyz^2 -&gt; 1.7*(V0)(V1)(V2)(V2) <li>
 * -5.0z^3y^2 -&gt; -5.0*(V2)(V2)(V2)(V1)(V1)
 * </ol>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PolynomialTerm implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The variables of the term.
     */
    private final int[] variables;

    /**
     * The coefficient of the term.
     */
    private double coefficient;

    //=================================CONSTRUCTORS========================//

    /**
     * Constructs a term.
     *
     * @param coefficient a double
     * @param variables   an array of  objects
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
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.PolynomialTerm} object
     */
    public static PolynomialTerm serializableInstance() {
        return new PolynomialTerm(0.0, new int[0]);
    }

    //==================================PUBLIC METHODS=====================//

    /**
     * Returns the coefficient.
     *
     * @return a double
     */
    public double getCoefficient() {
        return this.coefficient;
    }

    /**
     * Sets the coefficient.
     *
     * @param coefficient a double
     */
    public void setCoefficient(double coefficient) {
        this.coefficient = coefficient;
    }

    /**
     * Returns the number of variables in this term.
     *
     * @return a int
     */
    public int getNumVariables() {
        return this.variables.length;
    }

    /**
     * Returns the index'th variable.
     *
     * @param index a int
     * @return a int
     */
    public int getVariable(int index) {
        return this.variables[index];
    }

    /**
     * Returns true iff the given variable list is equal to the variable list of this term.
     *
     * @param variables an array of  objects
     * @return a boolean
     */
    public boolean isVariableListEqual(int[] variables) {
        return Arrays.equals(variables, this.variables);
    }

    /**
     * Returns the highest variable index in this term.
     *
     * @return a int
     */
    public int getMaxIndex() {
        int max = 0;
        for (int variable : this.variables) {
            if (variable > max) {
                max = variable;
            }
        }
        return max;
    }

    /**
     * Evaluates the term.
     *
     * @param values an array of  objects
     * @return a double
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
     *
     * @return a {@link java.lang.String} object
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






