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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.dist.Distribution;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Implements a linear update function, Gi.0 = L(Parents(G0.0)) + ei, where P
 * is a polynomial function and ei is a random noise term.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LinearFunction implements UpdateFunction {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The wrapped polynomial function that's doing all the work.
     */
    private final PolynomialFunction polynomialFunction;

    //=========================CONSTRUCTORS=============================//

    /**
     * Constructs a polyomial function where each factor is given a zero polynomial.
     *
     * @param lagGraph a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LagGraph} object
     */
    public LinearFunction(LagGraph lagGraph) {
        if (lagGraph == null) {
            throw new NullPointerException("Lag graph must not be null.");
        }

        this.polynomialFunction = new PolynomialFunction(lagGraph);
        IndexedLagGraph connectivity = this.polynomialFunction.getIndexedLagGraph();

        for (int i = 0; i < connectivity.getNumFactors(); i++) {
            List terms = new ArrayList();

            // Intercept.
            terms.add(new PolynomialTerm(0.0, new int[0]));

            int numParents = connectivity.getNumParents(i);
            for (int j = 0; j < numParents; j++) {

                int[] vars = {j};
                terms.add(new PolynomialTerm(1.0 / (double) numParents, vars));
            }
            Polynomial p = new Polynomial(terms);
            this.polynomialFunction.setPolynomial(i, p);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LinearFunction} object
     */
    public static LinearFunction serializableInstance() {
        return new LinearFunction(BasicLagGraph.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    /**
     * Returns the value of the function.
     *
     * @param factorIndex a int
     * @param history     an array of {@link double} objects
     * @return a double
     */
    public double getValue(int factorIndex, double[][] history) {
        return this.polynomialFunction.getValue(factorIndex, history);
    }

    /**
     * Returns the indexed connectivity.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.IndexedLagGraph} object
     */
    public IndexedLagGraph getIndexedLagGraph() {
        return this.polynomialFunction.getIndexedLagGraph();
    }

    /**
     * Sets the intercept for the given factor.
     *
     * @param factor    a {@link java.lang.String} object
     * @param intercept a double
     * @return a boolean
     */
    public boolean setIntercept(String factor, double intercept) {
        IndexedLagGraph connectivity =
                this.polynomialFunction.getIndexedLagGraph();

        int factorIndex = connectivity.getIndex(factor);

        return setIntercept(factorIndex, intercept);
    }

    /**
     * Sets the intercept for the given factor.
     *
     * @param factor    a int
     * @param intercept a double
     * @return a boolean
     */
    public boolean setIntercept(int factor, double intercept) {
        Polynomial p = this.polynomialFunction.getPolynomial(factor);
        PolynomialTerm term = p.findTerm(new int[0]);
        if (term == null) {
            return false;
        }

        term.setCoefficient(intercept);
        return true;
    }

    /**
     * Sets the intercept for the given factor.
     *
     * @param factor    a {@link java.lang.String} object
     * @param parent    a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor} object
     * @param intercept a double
     * @return a boolean
     */
    public boolean setCoefficient(String factor, LaggedFactor parent,
                                  double intercept) {
        IndexedLagGraph connectivity = this.polynomialFunction.getIndexedLagGraph();

        int factorIndex = connectivity.getIndex(factor);
        int parentIndex = connectivity.getIndex(factor, parent);

        return setCoefficient(factorIndex, parentIndex, intercept);
    }

    /**
     * Sets the coefficient for the given parent of the given factor.
     *
     * @param factor      a int
     * @param parent      a int
     * @param coefficient a double
     * @return a boolean
     */
    public boolean setCoefficient(int factor, int parent, double coefficient) {

        Polynomial p = this.polynomialFunction.getPolynomial(factor);
        PolynomialTerm term = p.findTerm(new int[]{parent});

        if (term == null) {
            return false;
        }

        term.setCoefficient(coefficient);
        return true;
    }

    /**
     * Method setIntenalNoiseModel
     *
     * @param factor       a int
     * @param distribution a {@link edu.cmu.tetrad.util.dist.Distribution} object
     */
    public void setErrorDistribution(int factor, Distribution distribution) {
        this.polynomialFunction.setErrorDistribution(factor, distribution);
    }

    /**
     * Returns the error distribution for the <code>factor</code>'th factor.
     *
     * @param factor the factor in question.
     * @return the error distribution for <code>factor</code>.
     */
    public Distribution getErrorDistribution(int factor) {
        return this.polynomialFunction.getErrorDistribution(factor);
    }

    /**
     * Prints out the linear function of each factor of its parents.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        IndexedLagGraph connectivity = this.polynomialFunction.getIndexedLagGraph();
        buf.append("\n\nLinear Function:");
        for (int i = 0; i < connectivity.getNumFactors(); i++) {
            buf.append("\n\tFactor ").append(connectivity.getFactor(i)).append(" --> ").append(this.polynomialFunction.getPolynomial(i));
        }
        return buf.toString();
    }

    /**
     * Returns the number of factors in the history. This is used to set up the initial history array.
     *
     * @return a int
     */
    public int getNumFactors() {
        return this.polynomialFunction.getNumFactors();
    }

    /**
     * Returns the max lag of the history. This is used to set up the initial history array.
     *
     * @return a int
     */
    public int getMaxLag() {
        return this.polynomialFunction.getMaxLag();
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s an {@link java.io.ObjectInputStream} object
     * @throws IOException            if any.
     * @throws ClassNotFoundException if any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.polynomialFunction == null) {
            throw new NullPointerException();
        }
    }
}





