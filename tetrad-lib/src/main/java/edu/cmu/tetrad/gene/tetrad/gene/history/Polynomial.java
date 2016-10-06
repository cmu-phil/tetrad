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
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a polynomial as a sum of a list of terms whose variables are
 * identified as integers in the set {0, 1, 2, ...}.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class Polynomial implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The terms of the polynomial.
     *
     * @serial
     */
    private List<PolynomialTerm> terms;

    //==============================CONSTRUCTOR===========================//

    /**
     * Constructs a polynomial from a list of terms.
     */
    public Polynomial(List<PolynomialTerm> terms) {

        if (terms == null) {
            throw new NullPointerException("Terms list cannot be null.");
        }

        this.terms = new ArrayList<>(terms);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static Polynomial serializableInstance() {
        return new Polynomial(new ArrayList<PolynomialTerm>());
    }

    //==============================PUBLIC METHODS========================//

    /**
     * Returns the number of terms.
     */
    public int getNumTerms() {
        return this.terms.size();
    }

    /**
     * Returns the coefficient.
     */
    public PolynomialTerm getTerm(int index) {
        return this.terms.get(index);
    }

    /**
     * Finds the first term matching the given profile.
     */
    public PolynomialTerm findTerm(int[] variables) {
        for (PolynomialTerm term1 : this.terms) {
            if (term1.isVariableListEqual(variables)) {
                return term1;
            }
        }

        return null;
    }

    /**
     * Returns the highest variable index in any term.
     */
    public int getMaxIndex() {
        int max = 0;
        for (PolynomialTerm term1 : this.terms) {
            int termMax = term1.getMaxIndex();
            if (termMax > max) {
                max = termMax;
            }
        }
        return max;
    }

    /**
     * Evaluates the term.
     */
    public double evaluate(double[] values) {
        double sum = 0.0;
        for (PolynomialTerm term1 : this.terms) {
            sum += term1.evaluate(values);
        }
        return sum;
    }

    /**
     * Prints out a representation of the term.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < this.terms.size(); i++) {
            buf.append(this.terms.get(i));
            if (i < this.terms.size() - 1) {
                buf.append(" + ");
            }
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

        if (terms == null) {
            throw new NullPointerException();
        }
    }
}





