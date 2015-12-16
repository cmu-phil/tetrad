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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;


/**
 * Stores the parameters needed to initialize a SEM IM. When fixed with a
 * set value, parameters are chosen from that value. Otherwise, parameters
 * (that is, values for the SEM IM, and initial values for SEM estimation)
 * are chosen from a distribution that is uniform over a range of values,
 * in a way that depends on parameter types. For coefficient and covariance
 * parameters, a range of positive numbers is selected (by selecting a low
 * value and a high value) and a random value is chosen uniformly between
 * these extremes. In each case, the distribution may be marked symmetric,
 * in which case once the value is chosen, a coin is flipped to decide whether
 * it will be positive or negative. Variance parameter values are chosen
 * in the same way, except that they cannot be negative, so there is no
 * coin flipping. That's all for the case where values are chosen randomly.
 * The other option is to get values from a previously existing SEM IM,
 * in which case if a parameter is not fixed at a value, and it is of the
 * same type, connecting variables of the same names, in the same direction
 * (for coefficient parameters) can be found, that value is used; otherwise,
 * if it is fixed at a value, that fixed value is used; otherwise, a random
 * value is used.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class SemImParams implements Params, TetradSerializable {
    static final long serialVersionUID = 23L;

    private SemImInitializationParams params = new SemImInitializationParams();

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new parameters object. Must be a blank constructor.
     */
    public SemImParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static SemImParams serializableInstance() {
        return new SemImParams();
    }

    //============================PUBLIC METHODS==========================//

    /**
     * @return the low positive coeffient parameter range for random values.
     */
    public double getCoefLow() {
        return getSemImInitializionParams().getCoefLow();
    }

    /**
     * Sets the low positive coeffient parameter range for random values.
     */
    public void setCoefRange(double coefLow, double coefHigh) {
        getSemImInitializionParams().setCoefRange(coefLow, coefHigh);
    }

    /**
     * @return the high positive coeffient parameter range for random values./
     */
    public double getCoefHigh() {
        return getSemImInitializionParams().getCoefHigh();
    }

    /**
     * @return the low positive covariance parameter range for random values./
     */
    public double getCovLow() {
        return getSemImInitializionParams().getCovLow();
    }

    /**
     * Sets the low positive covariance parameter range for random values.
     */
    public void setCovRange(double covLow, double covHigh) {
        getSemImInitializionParams().setCovRange(covLow, covHigh);
    }

    /**
     * @return the high positive covariance parameter range for random values./
     */
    public double getCovHigh() {
        return getSemImInitializionParams().getCovHigh();
    }

    /**
     * @return the low positive variance parameter range for random values./
     */
    public double getVarLow() {
        return getSemImInitializionParams().getVarLow();
    }

    /**
     * Sets the low positive variance parameter range for random values.
     */
    public void setVarRange(double varLow, double varHigh) {
        getSemImInitializionParams().setVarRange(varLow, varHigh);
    }

    /**
     * @return the high positive variance parameter range for random values./
     */
    public double getVarHigh() {
        return getSemImInitializionParams().getVarHigh();
    }

    /**
     * @return true if random coefficients are chosen from (-high, -low) U (low, high),
     * false if they are chosen from (low, high).
     */
    public boolean isCoefSymmetric() {
        return getSemImInitializionParams().isCoefSymmetric();
    }

    /**
     * Sets true if random coefficients are chosen from (-high, -low) U (low, high),
     * false if they are chosen from (low, high).
     */
    public void setCoefSymmetric(boolean coefSymmetric) {
        getSemImInitializionParams().setCoefSymmetric(coefSymmetric);
    }

    /**
     * @return true if random covariances are chosen form (-high, -low) U (low, high);
     * false if they are chosen from (low, high).
     */
    public boolean isCovSymmetric() {
        return getSemImInitializionParams().isCovSymmetric();
    }

    /**
     * Sets true if random covariances are chosen from (-high, -low) U (low, high),
     * false if they are chosen from (low, high).
     */
    public void setCovSymmetric(boolean covSymmetric) {
        getSemImInitializionParams().setCovSymmetric(covSymmetric);
    }

    /**
     * @return true iff values from the old SEM IM (if available) are retained (where
     * possible).
     */
    public boolean isRetainPreviousValues() {
        return getSemImInitializionParams().isRetainPreviousValues();
    }

    /**
     * Sets whether values from the old SEM IM (if available) are retained (where
     * possible).
     */
    public void setRetainPreviousValues(boolean retainPreviousValues) {
        getSemImInitializionParams().setRetainPreviousValues(retainPreviousValues);
    }

    public SemImInitializationParams getSemImInitializionParams() {
        return params;
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

        if (params == null) {
            params = new SemImInitializationParams();
        }
    }}



