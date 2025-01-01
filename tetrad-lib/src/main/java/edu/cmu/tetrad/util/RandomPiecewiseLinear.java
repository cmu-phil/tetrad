package edu.cmu.tetrad.util;

import java.util.Arrays;

/**
 * Generates a random piecewise linear function that is monotonic (invertible).
 */
public class RandomPiecewiseLinear {

    /**
     * Number of linear segments to divide the range into.
     */
    private final int numSegments;

    /**
     * Breakpoints (x-values) for the linear segments.
     */
    private final double[] breakpoints;

    /**
     * Slopes for each segment.
     */
    private final double[] slopes;

    /**
     * Intercepts for each segment.
     */
    private final double[] intercepts;

    /**
     * Constructs a random piecewise linear function that is monotonic (invertible) within the specified x-range and
     * splits the range into the given number of segments.
     *
     * @param xMin        the minimum x-value for the range
     * @param xMax        the maximum x-value for the range
     * @param numSegments the number of linear segments to divide the range into
     */
    private RandomPiecewiseLinear(double xMin, double xMax, int numSegments) {
        this.numSegments = numSegments;

        // Generate random breakpoints (segments) in the x-range
        breakpoints = new double[numSegments + 1];
        breakpoints[0] = xMin;
        breakpoints[numSegments] = xMax;

        for (int i = 1; i < numSegments; i++) {
            breakpoints[i] = xMin + (xMax - xMin) * Math.random();
        }
        Arrays.sort(breakpoints);

        // Generate random slopes for each segment (positive for monotonicity)
        slopes = new double[numSegments];
        for (int i = 0; i < numSegments; i++) {
            slopes[i] = 0.5 + Math.random() * 1.5; // Slopes between 0.5 and 2.0
        }

        // Calculate intercepts for each segment
        intercepts = new double[numSegments];
        intercepts[0] = 0; // Start at y = 0 for the first breakpoint
        for (int i = 1; i < numSegments; i++) {
            double dx = breakpoints[i] - breakpoints[i - 1];
            intercepts[i] = intercepts[i - 1] + slopes[i - 1] * dx;
        }
    }

    /**
     * Returns a new instance of RandomPiecewiseLinear with the specified x-range and number of segments.
     *
     * @param xMin        the minimum x-value for the range
     * @param xMax        the maximum x-value for the range
     * @param numSegments the number of linear segments to divide the range into
     * @return a new instance of RandomPiecewiseLinear
     */
    public static RandomPiecewiseLinear get(double xMin, double xMax, int numSegments) {
        return new RandomPiecewiseLinear(xMin, xMax, numSegments);
    }

    /**
     * The main method demonstrates the usage of the RandomPiecewiseLinear class.
     *
     * @param args the command-line arguments (not used in this implementation)
     */
    public static void main(String[] args) {
        // Example usage
        double[] xValues = {-10, -5, 0, 5, 10}; // Input points

        // Get the min and max of the x values
        double xMin = Arrays.stream(xValues).min().orElseThrow();
        double xMax = Arrays.stream(xValues).max().orElseThrow();

        RandomPiecewiseLinear piecewiseLinear = RandomPiecewiseLinear.get(xMin, xMax, 3);

        // Print the output
        for (double xValue : xValues) {
            System.out.printf("x: %f, y: %f%n", xValue, piecewiseLinear.evaluate(xValue));
        }
    }

    /**
     * Evaluates the piecewise linear function at the specified x-value.
     *
     * @param x the input x-value for which the function is evaluated
     * @return the corresponding y-value of the function at the specified x
     */
    public double evaluate(double x) {
        // Find the segment for this x-value
        int segment = 0;
        while (segment < numSegments && x > breakpoints[segment + 1]) {
            segment++;
        }

        // Compute y based on the slope and intercept of the segment
        double dx = x - breakpoints[segment];
        return intercepts[segment] + slopes[segment] * dx;
    }
}
