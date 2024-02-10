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

import edu.cmu.tetrad.util.RandomUtil;

/**
 * <p>GaussianPower class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GaussianPower implements Distribution {
    private static final long serialVersionUID = 23L;

    private final double sd;
    private final String name;
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
        double value = RandomUtil.getInstance().nextNormal(0, 1);
        double poweredValue = org.apache.commons.math3.util.FastMath.pow(org.apache.commons.math3.util.FastMath.abs(value), this.power);
        return (value >= 0) ? poweredValue : -poweredValue;
    }
}




