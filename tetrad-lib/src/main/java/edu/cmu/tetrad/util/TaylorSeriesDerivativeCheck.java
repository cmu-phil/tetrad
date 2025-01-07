package edu.cmu.tetrad.util;

import java.util.Arrays;
import java.util.stream.DoubleStream;

/**
 * Tests if the derivative f'(x) is positive for a Taylor series function over a range of x given its derivatives at 0.
 *
 * @author GPT 4.o
 * @author josephramsey
 */
public class TaylorSeriesDerivativeCheck {

    public static void main(String[] args) {
        // Example: All derivatives are 1 (e.g., approximating e^x)
        double[] derivatives = new double[20]; // f(0), f'(0), f''(0), ...
        // All derivatives are 1
        Arrays.fill(derivatives, 1);

        // Test f'(x) for x in the range [-1, 1]
        boolean allPositive = testDerivativePositivity(derivatives, -1.0, 1.0, 0.01);

        if (allPositive) {
            System.out.println("f'(x) is positive for all x in the range [-1, 1].");
        } else {
            System.out.println("f'(x) is not positive for all x in the range [-1, 1].");
        }
    }

    /**
     * Tests if the first derivative f'(x) is positive for all x in the given range.
     *
     * @param derivatives The array of Taylor series derivatives expanded about 0.
     * @param xMin        The minimum value of x to test.
     * @param xMax        The maximum value of x to test.
     * @param step        The step size for testing x in the range.
     * @return True if f'(x) > 0 for all x in the range, false otherwise.
     */
    public static boolean testDerivativePositivity(double[] derivatives, double xMin, double xMax, double step) {
        return DoubleStream.iterate(xMin, x -> x <= xMax, x -> x + step).allMatch(x -> {
            double derivative = computeDerivativeAtX(derivatives, x);
            return derivative > 0;
        });
    }

    /**
     * Computes the first derivative f'(x) at a given x, using the Taylor series derivatives expanded about 0.
     *
     * @param derivatives The array of Taylor series derivatives expanded about 0.
     * @param x           The value at which to evaluate the derivative.
     * @return The value of f'(x).
     */
    private static double computeDerivativeAtX(double[] derivatives, double x) {
        double derivative = 0.0;

        // Start summing from the first derivative term (n = 1)
        for (int n = 1; n < derivatives.length; n++) {
            double term = n * derivatives[n] * Math.pow(x, n - 1);
            derivative += term;
        }

        return derivative;
    }
}
