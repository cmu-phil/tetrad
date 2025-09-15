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

/**
 * Implements a Beta distribution for purposes of drawing random numbers. The parameters are alpha and beta. See
 * Wikipedia.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Beta implements Distribution {
    private static final long serialVersionUID = 23L;

    /**
     * Ibid.
     */
    private double alpha = 0.5;

    /**
     * Ibid.
     */
    private double beta = 0.5;

    /**
     * Ibid.
     */
    private Beta() {
        this.alpha = .5;
        this.beta = .5;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return Ibid.
     */
    public static Beta serializableInstance() {
        return new Beta();
    }

    /**
     * Returns the next random.
     *
     * @return Ibid.
     */
    public double nextRandom() {
        return RandomUtil.getInstance().nextBeta(this.alpha, this.beta);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The order of parameters is alpha = 0, beta = 1.
     */
    public void setParameter(int index, double value) {
        if (index == 0) {
            this.alpha = value;
        } else if (index == 1 && value >= 0) {
            this.beta = value;
        } else {
            throw new IllegalArgumentException("Illegal value: " + value);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The order of parameters is alpha = 0, beta = 1.
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.alpha;
        } else if (index == 1) {
            return this.beta;
        } else {
            throw new IllegalArgumentException("Illegal index: " + index);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The order of parameters is alpha = 0, beta = 1.
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "Alpha";
        } else if (index == 1) {
            return "Beta";
        } else {
            throw new IllegalArgumentException("Not a parameter index: " + index);
        }
    }

    /**
     * Uh, there are 2 parameters...
     *
     * @return Ibid.
     */
    public int getNumParameters() {
        return 2;
    }

    /**
     * Please don't make me say it...
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return "Beta";
    }

    /**
     * A string representation of the distribution.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "B(" + this.alpha + ", " + this.beta + ")";
    }
}





