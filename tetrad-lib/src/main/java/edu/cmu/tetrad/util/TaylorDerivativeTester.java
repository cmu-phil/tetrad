package edu.cmu.tetrad.util;

import java.util.stream.DoubleStream;

/**
 * Tests if the derivative f'(c) remains positive for a range of c values,
 * given a Taylor series expansion about 0.
 */
public class TaylorDerivativeTester {

    public static void main(String[] args) {
        // Example Taylor series derivatives: f(0), f'(0), f''(0), f'''(0), ...
        double[] derivatives = {1, 1, 0.5, 0.1666667, 0.0416667}; // Example for e^x

        // Ensure f'(0) > 0
        if (derivatives[1] <= 0) {
            System.out.println("Error: f'(0) must be positive.");
            return;
        }

        // Test f'(c) for c in the range [-1, 1]
        boolean allPositive = testDerivativePositivity(derivatives, -1.0, 1.0, 0.1);

        if (allPositive) {
            System.out.println("f'(c) is positive for all c in the range [-1, 1].");
        } else {
            System.out.println("f'(c) is not positive for all c in the range [-1, 1].");
        }
    }

    /**
     * Tests if the first derivative f'(c) is positive for all c in the given range.
     *
     * @param derivatives The array of Taylor series derivatives expanded about 0.
     *                    The first element is f(0), the second is f'(0), etc.
     * @param cMin        The minimum value of c to test.
     * @param cMax        The maximum value of c to test.
     * @param step        The step size for testing c in the range.
     * @return True if f'(c) > 0 for all c in the range, false otherwise.
     */
    public static boolean testDerivativePositivity(double[] derivatives, double cMin, double cMax, double step) {
        return DoubleStream.iterate(cMin, c -> c <= cMax, c -> c + step)
                .allMatch(c -> computeDerivativeAtC(derivatives, c) > 0);
    }

    /**
     * Computes the first derivative f'(c) at a given c, using the Taylor series derivatives expanded about 0.
     *
     * @param derivatives The array of Taylor series derivatives expanded about 0.
     * @param c           The center at which to evaluate the derivative.
     * @return The value of f'(c).
     */
    public static double computeDerivativeAtC(double[] derivatives, double c) {
        double derivative = 0.0;

        // Start summing from the first derivative term (n = 1)
        for (int n = 1; n < derivatives.length; n++) {
            derivative += n * derivatives[n] * Math.pow(c, n - 1);
        }

        return derivative;
    }
}
