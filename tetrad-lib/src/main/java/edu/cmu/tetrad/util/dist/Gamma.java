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
 * Wraps a chi square distribution for purposes of drawing random samples. Methods are provided to allow parameters to
 * be manipulated in an interface.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Gamma implements Distribution {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The shape parameter.
     */
    private double alpha;

    /**
     * The rate parameter.
     */
    private double lambda;

    private Gamma() {
        this.alpha = .5;
        this.lambda = .7;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    public static Gamma serializableInstance() {
        return new Gamma();
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
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return "Gamma";
    }

    /**
     * {@inheritDoc}
     */
    public void setParameter(int index, double value) {
        if (index == 0) {
            this.alpha = value;
        } else if (index == 1) {
            this.lambda = value;
        }

        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.alpha;
        } else if (index == 1) {
            return this.lambda;
        }

        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "Alpha";
        } else if (index == 1) {
            return "Lambda";
        }

        throw new IllegalArgumentException();
    }

    /**
     * <p>nextRandom.</p>
     *
     * @return a double
     */
    public double nextRandom() {
        return RandomUtil.getInstance().nextGamma(this.alpha, this.lambda);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "Gamma(" + this.alpha + ", " + this.lambda + ")";
    }
}




