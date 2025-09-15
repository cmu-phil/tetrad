package edu.cmu.tetrad.util;

import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Taylor series expansion for a mathematical function. A Taylor series approximates a function as a sum of
 * terms calculated from the derivatives at a specific center. This class is thread-safe and immutable.
 *
 * @author josephramsey
 */
public class TaylorSeries {
    /**
     * Cache for logGamma values.
     */
    private static final Map<Integer, Double> logGammaCache = new HashMap<>();
    /**
     * Derivatives for the Taylor series.
     */
    private final double[] derivatives;
    /**
     * Center of the Taylor series.
     */
    private double center;

    /**
     * Constructs a TaylorSeries object with the specified derivatives and center. Private constructor.
     *
     * @param derivatives An array containing the derivatives of the function at the expansion point.
     * @param a           The center point of the Taylor series.
     */
    private TaylorSeries(double[] derivatives, double a) {
        if (derivatives == null || derivatives.length == 0) {
            throw new IllegalArgumentException("Derivatives array must not be null or empty.");
        }

        this.derivatives = Arrays.copyOf(derivatives, derivatives.length);
        this.center = a;
    }

    /**
     * Get the Taylor series with the given derivatives and center. The length of the derivative array determines the
     * degree of the Taylor series.
     *
     * @param derivatives Derivatives for the Taylor series.
     * @param a           Center of the Taylor series.
     * @return Taylor series with the given derivatives and center.
     */
    public static TaylorSeries get(double[] derivatives, double a) {
        return new TaylorSeries(derivatives, a);
    }

    /**
     * Memoized version of the logGamma function.
     *
     * @param n The value to compute the logGamma of.
     * @return The logGamma of n.
     */
    private static double logGamma(int n) {
        return logGammaCache.computeIfAbsent(n, Gamma::logGamma);
    }

    /**
     * Calculates the nth term of center Taylor series at center given point x, based on the derivatives provided and
     * the expansion point center.
     *
     * @param derivatives An array of derivatives, where the nth element represents the nth derivative at a center
     *                    point.
     * @param x           The value at which the Taylor series term is evaluated.
     * @param center      The point about which the Taylor series is expanded.
     * @param n           The index of the term in the Taylor series to calculate.
     * @return The nth term of the Taylor series.
     * @throws IllegalArgumentException If n is greater than or equal to the length of the derivative array.
     */
    private static double taylorTerm(double[] derivatives, double x, double center, int n) {
        if (n >= derivatives.length) {
            throw new IllegalArgumentException("Index exceeds the number of derivatives provided.");
        }

        double derivative = derivatives[n]; // f^(n)(center)

        if (derivative == 0) {
            return 0;
        }

        if (x == center && n > 0) {
            return 0.0; // Higher-order terms vanish when x = the center
        }

        if (x == center && n == 0) {
            return derivative; // f(center) = f^(0)(center)
        }

        double logTerm = Math.log(Math.abs(derivative)) + n * Math.log(Math.abs(x - center)) - logGamma(n + 1);

        // Restore sign of derivative to avoid log of negatives
        return Math.exp(logTerm) * Math.signum(derivative);
    }

    /**
     * Creates a new TaylorSeries object with the same derivatives but a specified new center.
     *
     * @param newCenter The new center point of the Taylor series.
     * @return A new TaylorSeries object with the updated center.
     */
    public TaylorSeries withNewCenter(double newCenter) {
        return new TaylorSeries(this.derivatives, newCenter);
    }

    /**
     * Creates a new TaylorSeries instance with modified derivatives while maintaining the same center.
     *
     * @param newDerivatives An array of new derivatives to be used in the TaylorSeries.
     * @return A new TaylorSeries object with the updated derivatives and the existing center.
     */
    public TaylorSeries withModifiedDerivatives(double[] newDerivatives) {
        return new TaylorSeries(newDerivatives, this.center);
    }

    /**
     * Retrieves a copy of the array containing the derivatives of the Taylor series. The derivatives represent the
     * coefficients of the Taylor series expansion about its center.
     *
     * @return A copy of the array containing the derivatives of the Taylor series.
     */
    public double[] getDerivatives() {
        return Arrays.copyOf(derivatives, derivatives.length);
    }

    /**
     * Retrieves the center point about which the Taylor series is expanded.
     *
     * @return The center point of the Taylor series.
     */
    public double getCenter() {
        return center;
    }

    /**
     * Sets the center point of the Taylor series. This does not change the derivatives of the series, only the point
     * about which the series is expanded.
     *
     * @param center The new center point of the Taylor series.
     */
    public void setCenter(double center) {
        this.center = center;
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
            result += taylorTerm(derivatives, x, center, n);
        }

        return result;
    }

    /**
     * Print the Taylor series.
     */
    public void printSeries() {
        StringBuilder builder = new StringBuilder("f(x) = ");
        for (int n = 0; n < derivatives.length; n++) {
            if (n > 0 && derivatives[n] != 0) {
                builder.append(" + ");
            }
            if (derivatives[n] != 0) {
                builder.append(derivatives[n])
                        .append(" * (x - ").append(center).append(")^").append(n)
                        .append(" / ").append(n).append("!");
            }
        }
        System.out.println(builder);
    }

}
