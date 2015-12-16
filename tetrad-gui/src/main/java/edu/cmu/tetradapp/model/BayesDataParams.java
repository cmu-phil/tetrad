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
 * Stores the parameters needed to simulate discrete data from a Bayes net.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class BayesDataParams implements Params, HasCalculatorParams {
    static final long serialVersionUID = 23L;

    /**
     * The number of cases to generate for the new data set.
     * @serial Must be greater than 0.
     */
    private int sampleSize = Preferences.userRoot().getInt("sampleSize", 1000);

    /**
     * True iff data for latent variables should be saved.
     */
    private boolean latentDataSaved = Preferences.userRoot().getBoolean("latentDataSaved", false);

    /**
     * True iff only positive data will be simulated.
     */
    private boolean positiveDataOnly = Preferences.userRoot().getBoolean("positiveDataOnly", false);

    /**
     * Calculator params--stores equations from the calculator.
     */
    private CalculatorParams calculatorParams;

    private int numDataSets = 1;

    //===============================CONSTRUCTORS=========================//

    /**
     * Blank constructor to construct a new parameters object for generating
     * discrete data from a Bayes network.
     */
    public BayesDataParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static BayesDataParams serializableInstance() {
        return new BayesDataParams();
    }

    //==============================PUBLIC METHODS========================//

    /**
     * @return the number of samples to simulate (greater than 0).
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Sets the number of samples to generate to <code>sampleSize</code>.
     */
    public void setSampleSize(int sampleSize) {
        if (!(sampleSize > 0)) {
            throw new IllegalArgumentException(
                    "Sample size Must be greater than 0: " + sampleSize);
        }

        this.sampleSize = sampleSize;
    }

    /**
     * True iff data for latent variables should be saved when simulating
     * new datasets.
     */
    public boolean isLatentDataSaved() {
        return latentDataSaved;
    }

    /**
     * Sets whether data for latent variables should be saved when simulating
     * new datasets.
     * @param latentDataSaved True iff latent data should be saved.
     */
    public void setLatentDataSaved(boolean latentDataSaved) {
        this.latentDataSaved = latentDataSaved;
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

        if (!(sampleSize > 0)) {
            throw new IllegalStateException(
                    "Sample size out of range: " + sampleSize);
        }
    }

    public CalculatorParams getCalculatorParams() {
        return this.calculatorParams;
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





