package edu.cmu.tetrad.search.utils;

import java.util.Arrays;
import java.util.Random;

/**
 * Represents a piecewise linear function with randomly generated breakpoints and values. This class allows computation
 * of values at any x within or outside the defined range of breakpoints.
 */
public class RandomPiecewiseLinear {
    private final double[] xPoints; // Breakpoints for the x-axis
    private final double[] yPoints; // Corresponding values for the y-axis

    /**
     * Constructs a RandomPiecewiseLinear instance. This method generates a piecewise linear function based on the
     * specified number of points, with x-coordinates randomly distributed within a specified range and y-coordinates
     * randomly assigned in the range [-2, 2].
     *
     * @param numPoints the number of breakpoints to generate for the piecewise linear function; must be greater than or
     *                  equal to 2
     * @param minX      the minimum value in the range for the x-coordinates of the breakpoints
     * @param maxX      the maximum value in the range for the x-coordinates of the breakpoints
     * @throws IllegalArgumentException if numPoints is less than 2
     */
    public RandomPiecewiseLinear(int numPoints, double minX, double maxX) {
        if (numPoints < 2) {
            throw new IllegalArgumentException("At least two points are required for piecewise linear function.");
        }

        this.xPoints = new double[numPoints];
        this.yPoints = new double[numPoints];

        Random random = new Random();

        // Generate random breakpoints within the range [minX, maxX]
        for (int i = 0; i < numPoints; i++) {
            xPoints[i] = minX + (maxX - minX) * random.nextDouble();
        }
        Arrays.sort(xPoints); // Ensure xPoints are sorted

        // Generate random y-values for the breakpoints
        for (int i = 0; i < numPoints; i++) {
            yPoints[i] = -2 + 4 * random.nextDouble(); // Random values in range [-2, 2]
        }
    }

    /**
     * The main method serves as the entry point of the program. It demonstrates the usage of the RandomPiecewiseLinear
     * class by creating an instance and computing the values of a piecewise linear function at various points within a
     * specified range. The results are printed to the console with formatted output for each computed value.
     *
     * @param args the command-line arguments, which are not used in this implementation
     */
    public static void main(String[] args) {
        double minX = -2.0;
        double maxX = 2.0;
        int numPoints = 5;

        // Create a RandomPiecewiseLinear instance
        RandomPiecewiseLinear piecewiseLinear = new RandomPiecewiseLinear(numPoints, minX, maxX);

        // Test the piecewise linear function
        for (double x = minX; x <= maxX; x += 0.1) {
            double y = piecewiseLinear.computeValue(x);
            System.out.printf("f(%5.2f) = %5.2f%n", x, y);
        }
    }

    /**
     * Computes the value of the piecewise linear function for a given x-value. If the x-value falls outside the defined
     * range of xPoints, the method performs extrapolation. Otherwise, it determines the appropriate segment and
     * performs interpolation.
     *
     * @param x the x-coordinate for which to compute the corresponding y-value
     * @return the computed y-value corresponding to the given x-coordinate
     * @throws IllegalStateException if the provided x-value cannot be handled due to an unexpected condition
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
