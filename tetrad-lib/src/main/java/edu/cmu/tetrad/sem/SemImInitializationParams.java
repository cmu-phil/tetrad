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

import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;


/**
 * Stores the freeParameters needed to initialize a SEM IM. When fixed with a
 * set value, freeParameters are chosen from that value. Otherwise, freeParameters
 * (that is, values for the SEM IM, and initial values for SEM estimation)
 * are chosen from a distribution that is uniform over a range of values,
 * in a way that depends on parameter types. For coefficient and covariance
 * freeParameters, a range of positive numbers is selected (by selecting a low
 * value and a high value) and a random value is chosen uniformly between
 * these extremes. In each case, the distribution may be marked symmetric,
 * in which case once the value is chosen, a coin is flipped to decide whether
 * it will be positive or negative. Variance parameter values are chosen
 * in the same way, except that they cannot be negative, so there is no
 * coin flipping. That's all for the case where values are chosen randomly.
 * The other option is to get values from a previously existing SEM IM,
 * in which case if a parameter is not fixed at a value, and it is of the
 * same type, connecting variables of the same names, in the same direction
 * (for coefficient freeParameters) can be found, that value is used; otherwise,
 * if it is fixed at a value, that fixed value is used; otherwise, a random
 * value is used.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class SemImInitializationParams implements Params, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * @serial Must be MANUAL_RETAIN, MANUAL_RETAIN, or RANDOM_OVERWRITE.
     */
    private boolean retainPreviousValues = false;

    /**
     * The low positive range of random coefficient values.
     */
    private double coefLow = 0.5;

    /**
     * The high positive range of random coefficient values.
     */
    private double coefHigh = 1.5;

    /**
     * The low positive range of random covariance values.
     */
    private double covLow = 0.2;

    /**
     * The high positive range of random covariance values.
     */
    private double covHigh = 0.3;

    /**
     * The low positive range of random variance values.
     */
    private double varLow = 1.0;

    /**
     * The high positive range of random variance values.
     */
    private double varHigh = 3.0;

    /**
     * True if coefficient values are chosen from (-high, -low) U (low, high);
     * false if they are chosen from (low, high).
     */
    private boolean coefSymmetric = true;

    /**
     * True if covariance values are chosen from (-high, -low) U (low, high);
     * false if they are chosen from (low, high).
     */
    private boolean covSymmetric = true;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new freeParameters object. Must be a blank constructor.
     */
    public SemImInitializationParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemImInitializationParams serializableInstance() {
        return new SemImInitializationParams();
    }

    //============================PUBLIC METHODS==========================//

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

    /**
     * @return the low positive coeffient parameter range for random values.
     */
    public double getCoefLow() {
        return coefLow;
    }

    /**
     * Sets the low positive coeffient parameter range for random values.
     */
    public void setCoefRange(double coefLow, double coefHigh) {
        if (coefLow < 0) {
            throw new IllegalArgumentException("Coef low must be >= 0.");
        }

        if (coefLow > coefHigh) {
            throw new IllegalArgumentException("Coef low must be < coef high.");
        }

        this.coefLow = coefLow;
        this.coefHigh = coefHigh;
    }

    /**
     * @return the high positive coeffient parameter range for random values./
     */
    public double getCoefHigh() {
        return coefHigh;
    }

    /**
     * @return the low positive covariance parameter range for random values./
     */
    public double getCovLow() {
        return covLow;
    }

    /**
     * Sets the low positive covariance parameter range for random values.
     */
    public void setCovRange(double covLow, double covHigh) {
        if (covLow < 0) {
            throw new IllegalArgumentException("Cov low must be >= 0.");
        }

        if (covLow > covHigh) {
            throw new IllegalArgumentException("Cov low must be < cov high.");
        }

        this.covLow = covLow;
        this.covHigh = covHigh;
    }

    /**
     * @return the high positive covariance parameter range for random values./
     */
    public double getCovHigh() {
        return covHigh;
    }

    /**
     * @return the low positive variance parameter range for random values./
     */
    public double getVarLow() {
        return varLow;
    }

    /**
     * Sets the low positive variance parameter range for random values.
     */
    public void setVarRange(double varLow, double varHigh) {
        if (varLow < 0) {
            throw new IllegalArgumentException("Var low must be >= 0.");
        }

        if (varLow > varHigh) {
            throw new IllegalArgumentException("Var low must be < ar high.");
        }

        this.varLow = varLow;
        this.varHigh = varHigh;
    }

    /**
     * @return the high positive variance parameter range for random values./
     */
    public double getVarHigh() {
        return varHigh;
    }

    /**
     * @return true if random coefficients are chosen from (-high, -low) U (low, high),
     * false if they are chosen from (low, high).
     */
    public boolean isCoefSymmetric() {
        return coefSymmetric;
    }

    /**
     * Sets true if random coefficients are chosen from (-high, -low) U (low, high),
     * false if they are chosen from (low, high).
     */
    public void setCoefSymmetric(boolean coefSymmetric) {
//        System.out.println("Setting coefSymmetric to " + coefSymmetric);
        this.coefSymmetric = coefSymmetric;
    }

    /**
     * @return true if random covariances are chosen form (-high, -low) U (low, high);
     * false if they are chosen from (low, high).
     */
    public boolean isCovSymmetric() {
        return covSymmetric;
    }

    /**
     * Sets true if random covariances are chosen from (-high, -low) U (low, high),
     * false if they are chosen from (low, high).
     */
    public void setCovSymmetric(boolean covSymmetric) {
        this.covSymmetric = covSymmetric;
    }

    /**
     * @return true iff values from the old SEM IM (if available) are retained (where
     * possible).
     */
    public boolean isRetainPreviousValues() {
        return retainPreviousValues;
    }

    /**
     * True iff values from the old SEM IM (if available) are retained (where
     * possible).
     */
    public void setRetainPreviousValues(boolean retainPreviousValues) {
        this.retainPreviousValues = retainPreviousValues;
    }
}



