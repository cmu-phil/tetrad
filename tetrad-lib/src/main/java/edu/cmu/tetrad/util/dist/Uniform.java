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
 * For given a, b (a < b), returns a point chosen uniformly from [a, b]. The
 * parameters are 0 = a, 1 = b.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class Uniform implements Distribution {
    static final long serialVersionUID = 23L;

    /**
     * The lower bound of the range from which numbers are drawn uniformly.
     *
     * @serial
     */
    private double a;

    /**
     * The upper bound of the range from which numbers are drawn uniformly.
     *
     * @serial
     */
    private double b;

    //=========================CONSTRUCTORS===========================//

    public Uniform(double a, double b) {
        if (!(a <= b)) {
            throw new IllegalArgumentException("a must be less than or equal to b.");
        }

        this.a = a;
        this.b = b;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Uniform serializableInstance() {
        return new Uniform(0, 1);
    }

    //=========================PUBLIC METHODS=========================//

    /**
     * Sets the value of the i'th parameter.
     */
    public void setParameter(int index, double value) {
        if (index == 0 && value < b) {
            a = value;
        } else if (index == 1 && value > a) {
            b = value;
        } else {
            throw new IllegalArgumentException("Illegal value for parameter " +
                    index + ": " + value);
        }
    }

    /**
     * @return the value of the ith parameter.
     */
    public double getParameter(int index) {
        if (index == 0) {
            return a;
        } else if (index == 1) {
            return b;
        } else {
            throw new IllegalArgumentException("Illegal index: " + index);
        }
    }

    public String getParameterName(int index) {
        if (index == 0) {
            return "Lower Bound";
        } else if (index == 1) {
            return "Upper Bound";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * @return the number of parameters = 2.
     */
    public int getNumParameters() {
        return 2;
    }

    /**
     * @return the next random sample from the distribution.
     */
    public double nextRandom() {
        return RandomUtil.getInstance().nextUniform(a, b);
    }


    public String getName() {
        return "Uniform";
    }

    public String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        return "U(" + nf.format(getParameter(0)) + ", " + nf.format(getParameter(1)) + ")";
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
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (a >= b) {
            throw new IllegalStateException();
        }
    }
}





