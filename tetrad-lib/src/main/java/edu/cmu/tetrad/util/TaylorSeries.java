package edu.cmu.tetrad.util;

import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;

/**
 * Represents a Taylor series expansion for a mathematical function. A Taylor series approximates a function as a sum of
 * terms calculated from the derivatives at a specific expansion point.
 *
 * @author josephramsey
 */
public class TaylorSeries {
    /**
     * Derivatives for the Taylor series.
     */
    private final double[] derivatives;
    /**
     * Center of the Taylor series.
     */
    private final double a;

    /**
     * Constructs a TaylorSeries object with the specified derivatives and center. Private constructor.
     *
     * @param derivatives An array containing the derivatives of the function at the expansion point.
     * @param a           The center point of the Taylor series.
     */
    private TaylorSeries(double[] derivatives, double a) {
        this.derivatives = derivatives;
        this.a = a;
    }

    /**
     * Get the Taylor series with the given derivatives and center.
     *
     * @param derivatives Derivatives for the Taylor series.
     * @param a           Center of the Taylor series.
     * @return Taylor series with the given derivatives and center.
     */
    public static TaylorSeries get(double[] derivatives, double a) {
        return new TaylorSeries(derivatives, a);
    }

    /**
     * Generate a random Taylor series of a given degree.
     *
     * @param degree Order of the Taylor series.
     * @return Random Taylor series.
     */
    public static TaylorSeries random(int degree) {
        double[] derivatives = new double[1 + degree];

        for (int i = 1; i <= degree; i++) {
            derivatives[i] = RandomUtil.getInstance().nextUniform(-1, 1);
        }

        return new TaylorSeries(derivatives, 0);
    }

    /**
     * Calculates the nth term of a Taylor series at a given point x, based on the derivatives provided and the
     * expansion point a.
     *
     * @param derivatives An array of derivatives, where the nth element represents the nth derivative at point a.
     * @param x           The value at which the Taylor series term is evaluated.
     * @param a           The point about which the Taylor series is expanded.
     * @param n           The index of the term in the Taylor series to calculate.
     * @return The nth term of the Taylor series.
     * @throws IllegalArgumentException If n is greater than or equal to the length of the derivatives array.
     */
    public static double taylorTerm(double[] derivatives, double x, double a, int n) {
        if (n >= derivatives.length) {
            throw new IllegalArgumentException("Index exceeds the number of derivatives provided.");
        }

        double derivative = derivatives[n]; // f^(n)(a)

        if (derivative == 0) {
            return 0;
        }

        double logTerm = Math.log(Math.abs(derivative)) + n * Math.log(Math.abs(x - a)) - Gamma.logGamma(n + 1);

        // Restore sign of derivative to avoid log of negatives
        return Math.exp(logTerm) * Math.signum(derivative);
    }

    /**
     * Main method to demonstrate the Taylor series.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {

        // Example 1: Derivatives for f(x) = 1 - x^2 at a = 0
        double[] derivatives = {1.0, 0.0, -2.0, 0.0}; // f(x) = 1 - x^2

        double x = 0.5;
        System.out.println("f(" + x + ") = " + TaylorSeries.get(derivatives, 0).evaluate(x) + ", actual value: " + (1 - x * x));

        // Print the Taylor series
        TaylorSeries.get(derivatives, 0).printSeries();

        // Example 2: Derivatives for e^x at a = 1 (f^(n)(a) = e^1 = e for all n)
        int numTerms = 30;
        double[] derivatives2 = new double[numTerms];
        Arrays.fill(derivatives2, Math.exp(1));

        double x2 = 1.5; // Point to evaluate the Taylor series
        double a2 = 1.0; // Center of the series

        System.out.println("f(" + x + ") = " + TaylorSeries.get(derivatives2, a2).evaluate(x2));

        double result = TaylorSeries.get(derivatives2, a2).evaluate(x2);
        System.out.println("Taylor series approximation: " + result + ", actual value: " + Math.exp(x2));

        TaylorSeries.get(derivatives2, a2).printSeries();
    }

    /**
     * Evaluates the Taylor series at a given point x by summing the terms up to the length of the derivative array.
     *
     * @param x The value at which to evaluate the Taylor series.
     * @return The computed value of the series at the specified point x.
     */
    public double evaluate(double x) {
        double result = 0.0;
        for (int n = 0; n < derivatives.length; n++) {
            result += taylorTerm(derivatives, x, a, n);
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
}
