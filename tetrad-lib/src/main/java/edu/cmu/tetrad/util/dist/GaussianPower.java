///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util.dist;

import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;

/**
 * <p>GaussianPower class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GaussianPower implements Distribution {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The standard deviation of the Gaussian distribution.
     */
    private final double sd;

    /**
     * The name.
     */
    private final String name;

    /**
     * The power.
     */
    private double power;

    /**
     * <p>Constructor for GaussianPower.</p>
     *
     * @param power a double
     */
    public GaussianPower(double power) {
        this.sd = 1;
        this.power = power;
        this.name = "N^" + power + "(" + 0 + "," + (double) 1 + ")";
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    public static GaussianPower serializableInstance() {
        return new GaussianPower(2);
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setParameter(int index, double value) {
        if (index == 0) {
            this.power = value;
        }

        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.sd;
        } else if (index == 1) {
            return this.power;
        }

        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "Standard Deviation";
        } else if (index == 1) {
            return "Power";
        }

        throw new IllegalArgumentException();
    }

    /**
     * <p>getNumParameters.</p>
     *
     * @return a int
     */
    public int getNumParameters() {
        return 2;
    }

    /**
     * <p>nextRandom.</p>
     *
     * @return a double
     */
    public double nextRandom() {
        double value = RandomUtil.getInstance().nextGaussian(0, 1);
        double poweredValue = org.apache.commons.math3.util.FastMath.pow(org.apache.commons.math3.util.FastMath.abs(value), this.power);
        return (value >= 0) ? poweredValue : -poweredValue;
    }
}





