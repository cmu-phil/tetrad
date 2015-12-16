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

import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;


/**
 * Stores the parameters needed to initialize a BayesPm.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class BayesPmParams implements Params {
    static final long serialVersionUID = 23L;

    /**
     * The initialization mode in which each variable is initialized with a
     * fixed number of values.
     */
    public static final int MANUAL = 0;

    /**
     * The initialization mode in which each variable is initialized with a
     * random number of values selected uniformly within a given range.
     */
    public static final int AUTOMATIC = 1;

    /**
     * @serial Must be MANUAL or AUTOMATIC.
     */
    private int initializationMode = MANUAL;

    /**
     * The lower bound on the number of values that a node may be initialized
     * with, if the initialization mode is AUTOMATIC.
     *
     * @serial Must be greater than or equal to 2.
     */
    private int lowerBoundNumVals = 2;

    /**
     * The upper bound on the number of values that a node may be initialized
     * with, if the initialization mode is AUTOMATIC.
     *
     * @serial Must be greater than lowerBoundNumVals
     */
    private int upperBoundNumVals = 4;

    //===========================CONSTRUCTORS=============================// ImParams())

    /**
     * Constructs a new parameters object. Must be a blank constructor.
     */
    public BayesPmParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static BayesPmParams serializableInstance() {
        return new BayesPmParams();
    }

    //============================PUBLIC METHODS=========================//

    public int getLowerBoundNumVals() {
        return lowerBoundNumVals;
    }

    public void setLowerBoundNumVals(int lowerBoundNumVals) {
        if (!(lowerBoundNumVals >= 2)) {
            throw new IllegalArgumentException(
                    "Number of values Must be greater than or equal to 2: " + lowerBoundNumVals);
        }

        if (!(lowerBoundNumVals <= upperBoundNumVals)) {
            throw new IllegalArgumentException(
                    "Lower bound must be <= upper bound.");
        }

        this.lowerBoundNumVals = lowerBoundNumVals;
    }

    public int getUpperBoundNumVals() {
        return upperBoundNumVals;
    }

    public void setUpperBoundNumVals(int upperBoundNumVals) {
        if (upperBoundNumVals < 2) {
            throw new IllegalArgumentException(
                    "Number of values Must be greater than or equal to 2: " + upperBoundNumVals);
        }

        if (upperBoundNumVals < lowerBoundNumVals) {
            throw new IllegalArgumentException(
                    "Upper bound Must be greater than or equal to lower bound.");
        }

        this.upperBoundNumVals = upperBoundNumVals;
    }

    public int getInitializationMode() {
        return initializationMode;
    }

    public void setInitializationMode(int initializationMode) {
        if (initializationMode != MANUAL && initializationMode != AUTOMATIC) {
            throw new IllegalArgumentException(
                    "Initialization mode must be " + "MANUAL or AUTOMATIC.");
        }

        this.initializationMode = initializationMode;
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

        switch (initializationMode) {
            case MANUAL:
                // Falls through.
            case AUTOMATIC:
                break;
            default:
                throw new IllegalStateException(
                        "Illegal value: " + initializationMode);
        }

        if (!(lowerBoundNumVals >= 2)) {
            throw new IllegalStateException(
                    "LowerBoundNumVals out of range: " + lowerBoundNumVals);
        }

        if (!(lowerBoundNumVals <= upperBoundNumVals)) {
            throw new IllegalStateException("LowerBoundNumVals > " +
                    "upperBoundNumVals: " + lowerBoundNumVals + " > " +
                    upperBoundNumVals);
        }
    }
}





