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

import edu.cmu.tetrad.calculator.CalculatorParams;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.prefs.Preferences;


/**
 * Stores the parameters used to simulate data from a OldSem net.
 *
 * @author Joseph Ramsey
 */
public class SemDataParams implements Params, HasCalculatorParams {
    static final long serialVersionUID = 23L;

    /**
     * The sample size to generate.
     *
     * @serial Range greater than 0.
     */
    private int sampleSize = 1000;


    /**
     * States whether data should be gerenated for latent variables also.
     *
     * @serial
     */
    private boolean includeLatents = false;

    /**
     * True iff only positive data should be simulated.
     */
    private boolean positiveDataOnly = false;

    /**
     * Calculator params--stores equations from the calculator.
     */
    private CalculatorParams calculatorParams1 = new CalculatorParams();

    // Backwards compatibility.
    private edu.cmu.tetradapp.model.calculator.CalculatorParams calculatorParams;

    /**
     * Stores the number of data sets to be simulated.
     */
    private int numDataSets = 1;

    //==========================CONSTRUCTORS============================//

    /**
     * Constructs a new parameters object. Must be a blank constructor.
     */
    public SemDataParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static SemDataParams serializableInstance() {
        return new SemDataParams();
    }

    //==========================PUBLIC METHODS=========================//

    /**
     * @return the number of samples to simulate.
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Sets the number of samples to generate.
     */
    public void setSampleSize(int sampleSize) {
        if (sampleSize <= 0) {
            throw new IllegalArgumentException();
        }

        this.sampleSize = sampleSize;
        Preferences.userRoot().putInt("sampleSize", sampleSize);
    }


    public boolean isLatentDataSaved(){
        return this.includeLatents;
    }


    public void setIncludeLatents(boolean includeLatents){
        this.includeLatents = includeLatents;
    }

    public void setPositiveDataOnly(boolean positiveDataOnly) {
        this.positiveDataOnly = positiveDataOnly;
    }


    public boolean isPositiveDataOnly() {
        return positiveDataOnly;
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

        if (sampleSize < 1) {
            throw new IllegalStateException("Sample size < 1: " + sampleSize);
        }
    }

    public CalculatorParams getCalculatorParams() {
        return this.calculatorParams1;
    }

    public void setNumDataSets(int numDataSets) {
        if (numDataSets < 1) {
            throw new IllegalArgumentException("Must simulate at least 1 data set.");
        }

        this.numDataSets = numDataSets;
    }

    public int getNumDataSets() {
        return numDataSets;
    }
}





