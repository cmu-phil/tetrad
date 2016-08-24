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
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Normal;

import java.io.IOException;
import java.io.ObjectInputStream;

//import edu.cmu.tetrad.gene.history.function.BooleanGlassFunction;

/**
 * Initializes a history array by setting the value of each variable to basal
 * if it is unregulated (has no parents other than itself one time step back)
 * and to a random value chosen from a N(basal, initStDev) distribution
 * otherwise.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class BasalInitializer implements Initializer, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The update function this is initializing for.
     *
     * @serial
     */
    private UpdateFunction updateFunction;

    /**
     * The average expression level that all unregulated genes are initialized
     * to.
     *
     * @serial
     */
    private double basalExpression;

    /**
     * The standard deviation of a normal distribution N(basalExpression,
     * sem.D.) that random initial values for unregulated genes are set to.
     *
     * @serial
     */
    private double initStDev;

    //============================CONSTRUCTORS===========================//

    /**
     * Constructs a new history that will initialize genes using the given basal
     * expression and initial standard deviation.
     */
    public BasalInitializer(UpdateFunction updateFunction,
                            double basalExpression, double initStDev) {
        if (updateFunction == null) {
            throw new NullPointerException(
                    "Update function must not be " + "null");
        }

        if (initStDev <= 0) {
            throw new IllegalArgumentException("The initialization standard " +
                    "deviation must be positive");
        }

        this.updateFunction = updateFunction;
        this.basalExpression = basalExpression;
        this.initStDev = initStDev;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BasalInitializer serializableInstance() {
        return new BasalInitializer(BooleanGlassFunction.serializableInstance(),
                0.0, 1.0);
    }

    //============================PUBLIC METHODS==========================//

    /**
     * Sets the expression of each unregulated gene in the given history at time
     * step 0 to a random value drawn from N(basalExpression, initStDev),
     * sets the expression level at time step 0 of each regulated gene to zero,
     * and then copies the values at history[0][j] to history[i][j] for all i >
     * 0 less than the maximum time lag.
     *
     * @param history the 2D double array to randomize.
     */
    public void initialize(double[][] history) {

        Distribution initDistribution =
                new Normal(this.basalExpression, this.initStDev);

        // TODO: Make sure normal dist gets nextGaussian, multiplies
        // by st.dev. and adds mean.

        // Modification 10/11/01: If a factor has no parents other
        // than itself, then it is initialized to mean of the
        // initialization distribution. The rationale for this is that
        // these "unregulated genes" will settle to zero anyway under
        // Glass updating, and this lets us avoid waiting for them to
        // settle (an unknown number of time steps). jdramsey
        for (int j = 0; j < history[0].length; j++) {

            IndexedLagGraph connectivity =
                    this.updateFunction.getIndexedLagGraph();

            if (connectivity.getNumParents(j) == 0) {
                history[0][j] = this.basalExpression;
            } else {
                history[0][j] = initDistribution.nextRandom();
            }
        }

        for (int i = 1; i < history.length; i++) {
            System.arraycopy(history[0], 0, history[i], 0, history[0].length);
        }
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

        if (updateFunction == null) {
            throw new NullPointerException();
        }

        if (initStDev <= 0.0) {
            throw new IllegalStateException();
        }
    }
}





