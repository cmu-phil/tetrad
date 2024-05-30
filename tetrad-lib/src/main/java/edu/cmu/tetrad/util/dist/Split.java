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

package edu.cmu.tetrad.util.dist;

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.NumberFormat;

/**
 * Wraps a chi square distribution for purposes of drawing random samples. Methods are provided to allow parameters to
 * be manipulated in an interface.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@SuppressWarnings("WeakerAccess")
public class Split implements Distribution {
    private static final long serialVersionUID = 23L;

    /**
     * Represents the variable 'a' in the Split distribution.
     */
    private double a;

    /**
     * A private variable representing the value of `b`.
     */
    private double b;

    /**
     * Creates a new split distribution, drawing uniformly from [-b, -a] U [a, b], where a and b are positive real
     * numbers.
     *
     * @param a Ibid.
     * @param b Ibid.
     */
    public Split(double a, double b) {
        if (a < 0) {
            throw new IllegalArgumentException(
                    "When asking for a range from a to b, the value of a must be >= 0: a = " + a + " b = " + b);
        }

        if (b <= a) {
            throw new IllegalArgumentException(
                    "When asking for a range from a to b, the value of b must be > a: a = " + a + " b = " + b);
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
        return new Split(0.0, 1.0);
    }

    /**
     * <p>nextRandom.</p>
     *
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

    /**
     * <p>Getter for the field <code>a</code>.</p>
     *
     * @return a double
     */
    public double getA() {
        return this.a;
    }

    /**
     * <p>Getter for the field <code>b</code>.</p>
     *
     * @return a double
     */
    public double getB() {
        return this.b;
    }


    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return "Split Distribution";
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        return "Split(" + nf.format(this.a) + ", " + nf.format(this.b) + ", " + ")";
    }


    /**
     * {@inheritDoc}
     */
    public void setParameter(int index, double value) {
        if (index == 0 && value < this.b) {
            this.a = value;
        } else if (index == 1 && value > this.a) {
            this.b = value;
        } else {
            throw new IllegalArgumentException("Cannot set value of " + index + " to " + value);
        }
    }

    /**
     * {@inheritDoc}
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.a;
        } else if (index == 1) {
            return this.b;
        } else {
            throw new IllegalArgumentException("There is no parameter " + index);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "Lower bound (> 0)";
        } else if (index == 1) {
            return "Upper bound (> 0)";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * <p>getNumParameters.</p>
     *
     * @return a int
     */
    public int getNumParameters() {
        return 2;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }
}




