package edu.cmu.tetrad.util;

/**
 * Represents a Taylor series with a given list of derivatives.
 *
 * <p>Example usage:
 * <pre>
 *     double[] derivatives = {1.0, 0.0, -1.0, 0.0}; // f(x) = 1 - x^2
 *     TaylorSeries taylor = new TaylorSeries(derivatives);
 *
 *     // Evaluate at some point
 *     double x = 0.5;
 *     System.out.println("f(" + x + ") = " + taylor.evaluate(x));
 *
 *     // Print the Taylor series
 *     taylor.printSeries();
 *
 *     // Output:
 *     // f(0.5) = 0.75
 *     // f(x) = 0.0 + 1.0x^1/1! + 0.0x^2/2! + -1.0x^3/3!
 * </pre>
 *
 * @author josephramsey
 */
public class TaylorSeries {
    /**
     * Derivatives for the Taylor series.
     */
    private final double[] derivatives;

    /**
     * Constructor for Taylor series with a given set of derivatives.
     *
     * @param derivatives Derivatives for the Taylor series.
     */
    public TaylorSeries(double[] derivatives) {
        this.derivatives = derivatives;
    }

    /**
     * Generate a random Taylor series of a given degree. f(a) is always set to 0.
     *
     * @param degree Order of the Taylor series.
     * @return Random Taylor series.
     */
    public static TaylorSeries randomTaylorSeries(int degree) {
        double[] derivatives = new double[1 + degree];

        for (int i = 1; i <= degree; i++) {
            derivatives[i] = RandomUtil.getInstance().nextUniform(0, 1);
        }

        derivatives[0] = 0;

        return new TaylorSeries(derivatives);
    }

    /**
     * Evaluate the Taylor series at a given point x.
     *
     * @param x Point to evaluate the Taylor series.
     * @return Result of the Taylor series at x.
     */
    public double evaluate(double x) {
        double result = 0.0;
        double term = 1.0;
        for (int n = 0; n < derivatives.length; n++) {
            result += derivatives[n] * term / factorial(n);
            term *= x;
        }

        return result;
    }

    /**
     * Print the Taylor series.
     */
    public void printSeries() {
        StringBuilder builder = new StringBuilder("f(x) = ");
        for (int n = 0; n < derivatives.length; n++) {
            if (n > 0) builder.append(" + ");
            builder.append(derivatives[n]).append("x^").append(n).append("/").append(n).append("!");
        }
        System.out.println(builder);
    }

    /**
     * Main method to demonstrate the Taylor series.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        // Example usage
        double[] derivatives = {1.0, 0.0, -1.0, 0.0}; // f(x) = 1 - x^2
        TaylorSeries taylor = new TaylorSeries(derivatives);

        // Evaluate at some point
        double x = 0.5;
        System.out.println("f(" + x + ") = " + taylor.evaluate(x));

        // Print the Taylor series
        taylor.printSeries();
    }

    /**
     * Factorial calculation.
     *
     * @param n Number to calculate factorial.
     * @return Factorial of n.
     */
    private static long factorial(int n) {
        if (n == 0 || n == 1) return 1;
        long fact = 1;
        for (int i = 2; i <= n; i++) {
            fact *= i;
        }
        return fact;
    }
}
