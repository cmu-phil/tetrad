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
 * A normal distribution that allows its parameters to be set and allows random sampling. The parameters are 0 = mean, 1
 * = standard deviation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Normal implements Distribution {
    private static final long serialVersionUID = 23L;

    /**
     * The mean of the distribution.
     *
     * @serial
     */
    private double mean;

    /**
     * The standard deviation of the distribution.
     *
     * @serial
     */
    private double sd;

    /**
     * <p>Constructor for Normal.</p>
     *
     * @param mean a double
     * @param sd   a double
     */
    public Normal(double mean, double sd) {
        setParameter(0, mean);
        setParameter(1, sd);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    public static Normal serializableInstance() {
        return new Normal(0, 1);
    }


    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return "N";
    }

    /**
     * {@inheritDoc}
     */
    public void setParameter(int index, double value) {
        if (index == 0) {
            this.mean = value;
        } else if (index == 1 && value >= 0) {
            this.sd = value;
        } else {
            throw new IllegalArgumentException("Illegal value for parameter " +
                                               index + ": " + value);
        }
    }

    /**
     * {@inheritDoc}
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.mean;
        } else if (index == 1) {
            return this.sd;
        } else {
            throw new IllegalArgumentException("Illegal index: " + index);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "Mean";
        } else if (index == 1) {
            return "Standard Deviation";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * <p>getNumParameters.</p>
     *
     * @return the number of parameters = 2.
     */
    public int getNumParameters() {
        return 2;
    }

    /**
     * <p>nextRandom.</p>
     *
     * @return the next random sample from the distribution.
     */
    public double nextRandom() {
        return RandomUtil.getInstance().nextNormal(this.mean, this.sd);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        return "N(" + nf.format(this.mean) + ", " + nf.format(this.sd) + ")";
    }

    //========================PRIVATE METHODS===========================//

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}





