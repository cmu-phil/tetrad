package edu.cmu.tetrad.search.utils;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.util.Random;

/**
 * This class generates and evaluates a random piecewise cubic spline function. It uses a set of breakpoints and random
 * y-values to create the spline. A cubic spline interpolator is then used to compute the interpolated values for input
 * x-values.
 */
public class RandomPiecewiseSpline {
    private final double[] xPoints; // Breakpoints for the x-axis
    private final double[] yPoints; // Corresponding y-values
    private final PolynomialSplineFunction splineFunction;

    /**
     * Constructor to initialize a piecewise cubic spline function.
     *
     * @param numPoints Number of breakpoints for the spline.
     * @param xMin      Minimum x-value for the domain.
     * @param xMax      Maximum x-value for the domain.
     * @param yMin      Minimum y-value for the range.
     * @param yMax      Maximum y-value for the range.
     */
    public RandomPiecewiseSpline(int numPoints, double xMin, double xMax, double yMin, double yMax) {
        if (numPoints < 2) {
            throw new IllegalArgumentException("At least two points are required for a piecewise spline function.");
        }

        this.xPoints = new double[numPoints];
        this.yPoints = new double[numPoints];

        Random random = new Random();

        // Step 1: Generate random breakpoints and corresponding values
        for (int i = 0; i < numPoints; i++) {
            xPoints[i] = xMin + i * (xMax - xMin) / (numPoints - 1); // Evenly spaced x-points
            yPoints[i] = yMin + (yMax - yMin) * random.nextDouble(); // Random y-values in the range [yMin, yMax]
        }

        // Step 2: Create the cubic spline interpolator
        SplineInterpolator splineInterpolator = new SplineInterpolator();
        this.splineFunction = splineInterpolator.interpolate(xPoints, yPoints);
    }

    /**
     * The main method serves as the entry point for the program. It creates an instance of RandomPiecewiseSpline and
     * evaluates the spline function for a range of x-values. The results are printed to the console.
     *
     * @param args Command-line arguments passed to the program (not used in this implementation).
     */
    public static void main(String[] args) {
        double xMin = -2.0;
        double xMax = 2.0;
        double yMin = -2.0;
        double yMax = 2.0;
        int numPoints = 10;

        // Create a RandomPiecewiseSpline instance
        RandomPiecewiseSpline spline = new RandomPiecewiseSpline(numPoints, xMin, xMax, yMin, yMax);

        // Test the spline function
        for (double x = xMin; x <= xMax; x += 0.1) {
            double y = spline.compute(x);
            System.out.printf("f(%5.2f) = %5.2f%n", x, y);
        }
    }

    /**
     * Compute the value of the spline function at a given x.
     *
     * @param x The x-value where the function is evaluated.
     * @return The interpolated y-value.
     */
    public double compute(double x) {
        return splineFunction.value(x);
    }

    /**
     * Adjusts the value of the spline function at a given x by subtracting the function's value at x = 0.
     *
     * @param x The x-value where the adjusted computation is performed.
     * @return The adjusted value of the spline function at the given x.
     */
    public double computeAdjusted(double x) {
        double fAtZero = compute(0); // Compute the value at x = 0
        return compute(x) - fAtZero; // Subtract f(0) to adjust the function
    }
}
