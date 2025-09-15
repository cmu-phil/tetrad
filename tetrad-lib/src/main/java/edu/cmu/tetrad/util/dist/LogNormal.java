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
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * Represents a lognormal distribution for purposes of sampling.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LogNormal implements Distribution {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The standard deviation.
     */
    private double sd;

    /**
     * <p>Constructor for LogNormal.</p>
     *
     * @param sd a double
     */
    public LogNormal(double sd) {
        this.sd = sd;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    public static LogNormal serializableInstance() {
        return new LogNormal(.5);
    }

    /**
     * <p>getNumParameters.</p>
     *
     * @return a int
     */
    public int getNumParameters() {
        return 1;
    }

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return "LogNormal";
    }

    /**
     * {@inheritDoc}
     */
    public void setParameter(int index, double value) {
        if (index == 0) {
            this.sd = value;
        }

        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.sd;
        }

        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    public String getParameterName(int index) {
        return "Standard Deviation";
    }

    /**
     * <p>nextRandom.</p>
     *
     * @return a double
     */
    public double nextRandom() {
        double random = RandomUtil.getInstance().nextGaussian(0, this.sd);
        return FastMath.exp(random);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "LogNormal";
    }
}





