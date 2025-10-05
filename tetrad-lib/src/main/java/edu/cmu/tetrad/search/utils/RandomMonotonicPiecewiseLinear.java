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

package edu.cmu.tetrad.search.utils;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;

/**
 * A class that generates and evaluates a monotonic piecewise linear function defined over a specified domain and range.
 * The y-values of the function are randomly generated while ensuring monotonicity. The function supports both
 * interpolation within the range and extrapolation outside the range.
 */
public class RandomMonotonicPiecewiseLinear {
    private final double[] xPoints; // Breakpoints for the x-axis
    private final double[] yPoints; // Corresponding y-values ensuring monotonicity

    /**
     * Constructor to initialize a monotonic piecewise linear function.
     *
     * @param numPoints Number of points for the piecewise linear function.
     * @param xMin      Minimum x-value for the domain.
     * @param xMax      Maximum x-value for the domain.
     * @param yMin      Minimum y-value for the range.
     * @param yMax      Maximum y-value for the range.
     */
    public RandomMonotonicPiecewiseLinear(int numPoints, double xMin, double xMax, double yMin, double yMax) {
        if (numPoints < 2) {
            throw new IllegalArgumentException("At least two points are required for a piecewise linear function.");
        }

        this.xPoints = new double[numPoints];
        this.yPoints = new double[numPoints];

        // Step 1: Evenly spaced x-points
        for (int i = 0; i < numPoints; i++) {
            xPoints[i] = xMin + i * (xMax - xMin) / (numPoints - 1);
        }

        // Step 2: Generate random monotonic y-values using a chi-square distribution
        ChiSquaredDistribution chiSquare = new ChiSquaredDistribution(1.0); // Degrees of freedom = 1
        double[] randomIncrements = new double[numPoints];
        for (int i = 0; i < numPoints; i++) {
            randomIncrements[i] = chiSquare.sample(); // Chi-square sampled increments
        }

        yPoints[0] = randomIncrements[0];
        for (int i = 1; i < numPoints; i++) {
            yPoints[i] = yPoints[i - 1] + randomIncrements[i]; // Cumulative sum for monotonicity
        }

        // Step 3: Normalize y-points to the range [yMin, yMax]
        double yMinActual = yPoints[0];
        double yMaxActual = yPoints[numPoints - 1];
        for (int i = 0; i < numPoints; i++) {
            yPoints[i] = yMin + (yPoints[i] - yMinActual) / (yMaxActual - yMinActual) * (yMax - yMin);
        }
    }

    /**
     * The main method serves as the entry point for testing the functionality of the RandomMonotonicPiecewiseLinear
     * class. It initializes parameters for creating an instance of the class, generates a monotonic piecewise linear
     * function, and evaluates the function over a specified range of x-values.
     *
     * @param args Command-line arguments passed to the program. Not used in this method.
     */
    public static void main(String[] args) {
        double xMin = -2.0;
        double xMax = 2.0;
        double yMin = -2.0;
        double yMax = 2.0;
        int numPoints = 10;

        // Create a RandomMonotonicPiecewiseLinear instance
        RandomMonotonicPiecewiseLinear piecewiseLinear = new RandomMonotonicPiecewiseLinear(numPoints, xMin, xMax, yMin, yMax);

        // Test the piecewise linear function
        for (double x = xMin; x <= xMax; x += 0.1) {
            double y = piecewiseLinear.computeValue(x);
            System.out.printf("f(%5.2f) = %5.2f%n", x, y);
        }
    }

    /**
     * Compute the value of the monotonic piecewise linear function at a given x.
     *
     * @param x The x-value where the function is evaluated.
     * @return The interpolated y-value.
     */
    public double computeValue(double x) {
        // Handle x outside the range by extrapolating
        if (x <= xPoints[0]) {
            return extrapolate(x, 0, 1);
        } else if (x >= xPoints[xPoints.length - 1]) {
            return extrapolate(x, xPoints.length - 2, xPoints.length - 1);
        }

        // Find the segment containing x
        for (int i = 1; i < xPoints.length; i++) {
            if (x <= xPoints[i]) {
                return interpolate(x, i - 1, i);
            }
        }

        throw new IllegalStateException("Unexpected condition: x not handled correctly.");
    }

    // Linear interpolation between two points
    private double interpolate(double x, int i, int j) {
        double slope = (yPoints[j] - yPoints[i]) / (xPoints[j] - xPoints[i]);
        return yPoints[i] + slope * (x - xPoints[i]);
    }

    // Extrapolation for x outside the range
    private double extrapolate(double x, int i, int j) {
        return interpolate(x, i, j);
    }
}

