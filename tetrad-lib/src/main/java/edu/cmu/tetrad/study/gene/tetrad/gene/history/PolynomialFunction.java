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
import edu.cmu.tetrad.util.dist.Normal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;

/**
 * <p>Implements a polynomial update function, Gi.0 = P(Parents(G0.0)) + ei,
 * where P is a polynomial function and ei is a random noise term.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PolynomialFunction implements UpdateFunction {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The "snapshot" indexed connectivity of the initial lag graph.
     */
    private final IndexedLagGraph connectivity;

    /**
     * The polynomials of each factor given its parents.
     */
    private final Polynomial[] polynomials;

    /**
     * Error distributions from which errors are drawn for each of the factors.
     *
     * @serial
     */
    private final Distribution[] errorDistributions;

    //=============================CONSTRUCTORS===========================//

    /**
     * Constructs a polyomial function where each factor is given a zero polynomial.
     *
     * @param lagGraph a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LagGraph} object
     */
    public PolynomialFunction(LagGraph lagGraph) {

        if (lagGraph == null) {
            throw new NullPointerException("Lag graph must not be null.");
        }

        // Construct the "snapshot" indexed connectivity.
        this.connectivity = new IndexedLagGraph(lagGraph);
        int numFactors = this.connectivity.getNumFactors();

        // Give each factor a zero polynomial.
        this.polynomials = new Polynomial[numFactors];

        for (int i = 0; i < numFactors; i++) {
            this.polynomials[i] = new Polynomial(new ArrayList());
        }

        // Set up error distributions.
        this.errorDistributions = new Distribution[numFactors];

        for (int i = 0; i < this.errorDistributions.length; i++) {
            this.errorDistributions[i] = new Normal(0.0, 0.05);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.PolynomialFunction} object
     */
    public static PolynomialFunction serializableInstance() {
        return new PolynomialFunction(BasicLagGraph.serializableInstance());
    }

    //===============================PUBLIC METHODS=======================//

    /**
     * Returns the value of the function.
     *
     * @param factorIndex a int
     * @param history     an array of {@link double} objects
     * @return a double
     */
    public double getValue(int factorIndex, double[][] history) {

        int numParents = this.connectivity.getNumParents(factorIndex);
        double[] values = new double[numParents];
        for (int i = 0; i < numParents; i++) {
            IndexedParent parent = this.connectivity.getParent(factorIndex, i);
            values[i] = history[parent.getLag()][parent.getIndex()];
        }
        return this.polynomials[factorIndex].evaluate(values) +
               this.errorDistributions[factorIndex].nextRandom();
    }

    /**
     * Returns the indexed connectivity.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.IndexedLagGraph} object
     */
    public IndexedLagGraph getIndexedLagGraph() {
        return this.connectivity;
    }

    /**
     * Method setIntenalNoiseModel
     *
     * @param factor       a int
     * @param distribution a {@link edu.cmu.tetrad.util.dist.Distribution} object
     */
    public void setErrorDistribution(int factor, Distribution distribution) {
        if (distribution != null) {
            this.errorDistributions[factor] = distribution;
        } else {
            throw new NullPointerException();
        }
    }

    /**
     * Returns the error distribution for the <code>factor</code>'th factor.
     *
     * @param factor the factor in question.
     * @return the error distribution for <code>factor</code>.
     */
    public Distribution getErrorDistribution(int factor) {
        return this.errorDistributions[factor];
    }

    /**
     * Sets the polynomial for the given factor.
     *
     * @param factor     a int
     * @param polynomial a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.Polynomial} object
     */
    public void setPolynomial(int factor, Polynomial polynomial) {

        if (polynomial == null) {
            throw new NullPointerException("Polynomial must not be null.");
        }

        this.polynomials[factor] = polynomial;
    }

    /**
     * Returns the polynomial for the given factor.
     *
     * @param factor a int
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.Polynomial} object
     */
    public Polynomial getPolynomial(int factor) {
        return this.polynomials[factor];
    }

    /**
     * Returns the number of factors in the history. This is used to set up the initial history array.
     *
     * @return a int
     */
    public int getNumFactors() {
        return this.connectivity.getNumFactors();
    }

    /**
     * Returns the max lag of the history. This is used to set up the initial history array.
     *
     * @return a int
     */
    public int getMaxLag() {
        int maxLag = 0;
        for (int i = 0; i < this.connectivity.getNumFactors(); i++) {
            for (int j = 0; j < this.connectivity.getNumParents(i); j++) {
                IndexedParent parent = this.connectivity.getParent(i, j);
                if (parent.getLag() > maxLag) {
                    maxLag = parent.getLag();
                }
            }
        }
        return maxLag;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The input stream from which this object is being deserialized.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.connectivity == null) {
            throw new NullPointerException();
        }

        if (this.polynomials == null) {
            throw new NullPointerException();
        }

        if (this.errorDistributions == null) {
            throw new NullPointerException();
        }

    }
}





