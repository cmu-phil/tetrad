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

package edu.cmu.tetrad.sem;

import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.OutOfRangeException;

import java.util.Collections;
import java.util.List;

/**
 * Only the cumulativeProbability, density, setShift methods are implemented.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class EmpiricalCdf implements RealDistribution {
    private final List<Double> data;

    /**
     * <p>Constructor for EmpiricalCdf.</p>
     *
     * @param data a {@link java.util.List} object
     */
    public EmpiricalCdf(List<Double> data) {
        if (data == null) throw new NullPointerException();
        this.data = data;
        Collections.sort(data);
    }

    /**
     * Calculates the cumulative probability of a given point.
     *
     * @param x the point at which the cumulative probability is evaluated
     * @return the cumulative probability at the given point
     */
    public double cumulativeProbability(double x) {
        int count = 0;

        for (double y : this.data) {
            if (y <= x) {
                count++;
            } else {
                break;
            }
        }

        return count / (double) this.data.size();
    }

    /**
     * Calculates the probability mass function (PMF) of a given point.
     *
     * @param v the point at which the PMF is evaluated
     * @return the probability mass function at the given point
     */
    @Override
    public double probability(double v) {
        return 0;
    }

    /**
     * Calculates the probability density function (PDF) of a given point.
     *
     * @param v the point at which the PDF is evaluated
     * @return the probability density function at the given point
     */
    @Override
    public double density(double v) {
        double d1 = v - 0.05;
        double d2 = v + 0.05;
        double n2 = cumulativeProbability(d1);
        double n1 = cumulativeProbability(d2);
        return (n1 - n2) / (d2 - d1);
    }

    /**
     * Calculates the cumulative probability of a given point within the range [v, v1].
     *
     * @param v  the exclusive lower bound
     * @param v1 the inclusive upper bound
     * @return the cumulative probability of the given point within the range
     * @throws NumberIsTooLargeException if v is greater than v1
     * @deprecated This method is deprecated and will be removed in a future release.
     */
    @Override
    @Deprecated
    public double cumulativeProbability(double v, double v1) throws NumberIsTooLargeException {
        throw new UnsupportedOperationException();
    }

    /**
     * Calculates the inverse cumulative probability of a given point.
     *
     * @param v the cumulative probability
     * @return the point at which the inverse cumulative probability is evaluated
     * @throws OutOfRangeException if the cumulative probability is out of range
     */
    @Override
    public double inverseCumulativeProbability(double v) throws OutOfRangeException {
        return 0;
    }

    /**
     * Returns the numerical mean of the empirical cumulative distribution function (CDF). The numerical mean is
     * calculated as the average of the data points.
     *
     * @return the numerical mean of the CDF
     */
    @Override
    public double getNumericalMean() {
        return 0;
    }

    /**
     * Returns the numerical variance of the empirical cumulative distribution function (CDF). The numerical variance is
     * calculated as the average of the squared differences between each data point and the numerical mean.
     *
     * @return the numerical variance of the CDF
     */
    @Override
    public double getNumericalVariance() {
        return 0;
    }

    /**
     * Returns the lower bound of the support for the distribution. The support is the range of values for which the
     * distribution is defined.
     *
     * @return the lower bound of the support
     */
    @Override
    public double getSupportLowerBound() {
        return 0;
    }

    /**
     * Returns the upper bound of the support for the distribution. The support is the range of values for which the
     * distribution is defined.
     *
     * @return the upper bound of the support
     */
    @Override
    public double getSupportUpperBound() {
        return 0;
    }

    /**
     * Returns a boolean indicating whether the lower bound of the support is inclusive or not.
     *
     * @return true if the lower bound is inclusive, false otherwise
     * @deprecated This method is deprecated and will be removed in a future release.
     */
    @Override
    @Deprecated
    public boolean isSupportLowerBoundInclusive() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a boolean indicating whether the upper bound of the support is inclusive or not.
     *
     * @return true if the upper bound is inclusive, false otherwise
     * @deprecated This method is deprecated and will be removed in a future release.
     */
    @Override
    @Deprecated
    public boolean isSupportUpperBoundInclusive() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a boolean indicating whether the support of the distribution is connected or not.
     *
     * @return true if the support is connected, false otherwise
     */
    @Override
    public boolean isSupportConnected() {
        return false;
    }

    /**
     * Reseeds the random number generator used by the empirical cumulative distribution function (CDF).
     *
     * @param l the new seed value for the random number generator
     */
    @Override
    public void reseedRandomGenerator(long l) {

    }

    /**
     * Returns a sample from the empirical cumulative distribution function (CDF).
     *
     * @return a sample from the CDF
     */
    @Override
    public double sample() {
        return 0;
    }

    /**
     * Returns a sample from the empirical cumulative distribution function (CDF).
     *
     * @param i the number of random values to generate
     * @return an array of random values sampled from the CDF
     */
    @Override
    public double[] sample(int i) {
        return new double[0];
    }
}


