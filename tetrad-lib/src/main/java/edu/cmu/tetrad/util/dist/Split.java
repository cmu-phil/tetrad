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
 * Wraps a chi square distribution for purposes of drawing random samples.
 * Methods are provided to allow parameters to be manipulated in an interface.
 *
 * @author Joseph Ramsey
 */
@SuppressWarnings({"WeakerAccess"})
public class Split implements Distribution {
    static final long serialVersionUID = 23L;

    /**
     * @serial
     */
    private double a;

    /**
     * @serial
     */
    private double b;

    //===========================CONSTRUCTORS===========================//

    /**
     * Creates a new split distribution, drawing uniformly from [-b, -a] U [a, b], where a and
     * b are positive real numbers.
     *
     * @param a Ibid.
     * @param b Ibid.
     */
    public Split(double a, double b) {
        if (a < 0) {
            throw new IllegalArgumentException(
                    "The value of a must be >= 0: " + a);
        }

        if (b <= a) {
            throw new IllegalArgumentException(
                    "The value of b must be > a: " + b);
        }

        this.a = a;
        this.b = b;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return the exemplar.
     */
    public static Split serializableInstance() {
        return new Split(0.5, 1.5);
    }

    //==========================PUBLIC METHODS==========================//

    /**
     * @return a random value from [-b, -a] U [a, b].
     */
    public double nextRandom() {
        double c = RandomUtil.getInstance().nextDouble();
        double value = getA() + c * (getB() - getA());

        if (RandomUtil.getInstance().nextDouble() < 0.5) {
            value *= -1.0;
        }

        return value;
    }

    public double getA() {
        return a;
    }

    public double getB() {
        return b;
    }


    public String getName() {
        return "Split Distribution";
    }

    public String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        return "Split(" + nf.format(a) + ", " + nf.format(b) + ", " + ")";
    }


    public void setParameter(int index, double value) {
        if (index == 0 && value < b) {
            a = value;
        } else if (index == 1 && value > a) {
            b = value;
        } else {
            throw new IllegalArgumentException("Cannot set value of " + index + " to " + value);
        }
    }

    public double getParameter(int index) {
        if (index == 0) {
            return a;
        } else if (index == 1) {
            return b;
        } else {
            throw new IllegalArgumentException("There is no parameter " + index);
        }
    }

    public String getParameterName(int index) {
        if (index == 0) {
            return "Lower bound (> 0)";
        } else if (index == 1) {
            return "Upper bound (> 0)";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    public int getNumParameters() {
        return 2;
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
     * @param s the stream to read from.
     * @throws java.io.IOException    If the stream cannot be read.
     * @throws ClassNotFoundException If the class of an object in the stream is not
     *                                in the project.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (a < 0) {
            throw new IllegalStateException();
        }

        if (b <= a) {
            throw new IllegalStateException();
        }
    }
}




