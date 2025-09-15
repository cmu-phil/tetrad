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
public class MixtureOfGaussians implements Distribution {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The mixing parameter.
     */
    private double a;

    /**
     * The mean of the first Gaussian.
     */
    private double mean1;

    /**
     * The standard deviation of the first Gaussian.
     */
    private double sd1;

    /**
     * The mean of the second Gaussian.
     */
    private double mean2;

    /**
     * The standard deviation of the second Gaussian.
     */
    private double sd2;

    /**
     * <p>Constructor for MixtureOfGaussians.</p>
     *
     * @param a     a double
     * @param mean1 a double
     * @param sd1   a double
     * @param mean2 a double
     * @param sd2   a double
     */
    public MixtureOfGaussians(double a, double mean1, double sd1, double mean2, double sd2) {
        if (a < 0 || a > 1) {
            throw new IllegalArgumentException();
        }

        if (sd1 <= 0) {
            throw new IllegalArgumentException();
        }

        if (sd2 <= 0) {
            throw new IllegalArgumentException();
        }

        this.a = a;
        this.mean1 = mean1;
        this.sd1 = sd1;
        this.mean2 = mean2;
        this.sd2 = sd2;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static MixtureOfGaussians serializableInstance() {
        return new MixtureOfGaussians(.5, -2, 2, 2, 2);
    }

    /**
     * <p>getNumParameters.</p>
     *
     * @return a int
     */
    public int getNumParameters() {
        return 5;
    }

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return "Mixture of Gaussians";
    }

    /**
     * {@inheritDoc}
     */
    public void setParameter(int index, double value) {
        if (index == 0) {
            this.a = value;
        } else if (index == 1) {
            this.mean1 = value;
        } else if (index == 2) {
            this.sd1 = value;
        } else if (index == 3) {
            this.mean2 = value;
        } else if (index == 5) {
            this.sd2 = value;
        }

        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    public double getParameter(int index) {
        if (index == 0) {
            return this.a;
        } else if (index == 1) {
            return this.mean1;
        } else if (index == 2) {
            return this.sd1;
        } else if (index == 3) {
            return this.mean2;
        } else if (index == 5) {
            return this.sd2;
        }

        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    public String getParameterName(int index) {
        if (index == 0) {
            return "Ratio";
        } else if (index == 1) {
            return "Mean 1";
        } else if (index == 2) {
            return "Standard Deviation 1";
        } else if (index == 3) {
            return "Mean 2";
        } else if (index == 5) {
            return "Standard Deviation 2";
        }

        throw new IllegalArgumentException();
    }

    /**
     * <p>nextRandom.</p>
     *
     * @return a double
     */
    public double nextRandom() {
        double r = RandomUtil.getInstance().nextDouble();

        if (r < this.a) {
            return RandomUtil.getInstance().nextGaussian(this.mean1, this.sd1);
        } else {
            return RandomUtil.getInstance().nextGaussian(this.mean2, this.sd2);
        }
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "MixtureOfGaussians(" + this.a + ", " + this.mean1 + ", " + this.sd1 + ", " + this.mean2 + ", " + this.sd2 + ")";
    }
}





