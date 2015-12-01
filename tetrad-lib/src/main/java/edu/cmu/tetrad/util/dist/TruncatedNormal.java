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

package edu.cmu.tetrad.util.dist;

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;

/**
 * A normal distribution that allows its parameters to be set and allows
 * random sampling. The parameters are 0 = mean, 1 = standard deviation.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class TruncatedNormal implements Distribution {
    static final long serialVersionUID = 23L;

    /**
     * The mean of the distribution.
     *
     * @serial
     */
    private double mean;

    /**
     * The standard devision of the distribution.
     *
     * @serial
     */
    private double sd;

    /**
     * The standard devision of the distribution.
     *
     * @serial
     */
    private double low;

    /**
     * The standard devision of the distribution.
     *
     * @serial
     */
    private double high;

    //=========================CONSTRUCTORS===========================//

    private TruncatedNormal() {
        setParameter(0, (double) 0);
        setParameter(1, (double) 1);
        setParameter(2, Double.NEGATIVE_INFINITY);
        setParameter(3, Double.POSITIVE_INFINITY);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    public static TruncatedNormal serializableInstance() {
        return new TruncatedNormal();
    }

    //=========================PUBLIC METHODS=========================//

    public String getName() {
        return "TruncNormal";
    }

    public void setParameter(int index, double value) {
        if (index == 0) {
            mean = value;
        } else if (index == 1 && value >= 0) {
            sd = value;
        } else if (index == 2) {
            low = value;
        } else if (index == 3) {
            high = value;
        } else {
            throw new IllegalArgumentException("Illegal value for parameter " +
                    index + ": " + value);
        }
    }

    public double getParameter(int index) {
        if (index == 0) {
            return mean;
        } else if (index == 1) {
            return sd;
        } else {
            throw new IllegalArgumentException("Illegal index: " + index);
        }
    }

    public String getParameterName(int index) {
        if (index == 0) {
            return "Mean";
        } else if (index == 1) {
            return "Standard Deviation";
        } else if (index == 2) {
            return "Low";
        } else if (index == 3) {
            return "High";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * @return the number of parameters = 2.
     */
    public int getNumParameters() {
        return 4;
    }

    /**
     * @return the next random sample from the distribution.
     */
    public double nextRandom() {
        return RandomUtil.getInstance().nextTruncatedNormal(mean, sd, low, high);
    }

    public String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        return "TruncNormal(" + nf.format(mean) + ", " + nf.format(sd) + ", " + nf.format(low) + ", " + nf.format(high) + ")";
    }

    //========================PRIVATE METHODS===========================//

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
     * @param s What it says.
     * @throws java.io.IOException    If the stream cannot be read.
     * @throws ClassNotFoundException if a the class of an object in the input cannot
     *                                be found.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (sd <= 0) {
            throw new IllegalStateException();
        }

    }
}





