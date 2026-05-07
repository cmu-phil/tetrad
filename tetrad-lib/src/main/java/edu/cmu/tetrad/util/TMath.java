package edu.cmu.tetrad.util;

import org.apache.commons.math3.util.FastMath;

/**
 * Unified math interface for Tetrad.
 * <p>
 * Allows switching between Math, StrictMath, and Math implementations
 * from a single place for experimentation and benchmarking.
 * <p>
 * If IMPL is static final the JVM will constant-fold the switch and inline
 * the selected implementation.
 */
public final class TMath {

    /**
     * A constant holding the value of π (pi), the ratio of a circle's circumference
     * to its diameter. This value is approximately 3.14159.
     */
    public static final double PI = Math.PI;

    /**
     * The mathematical constant e, which is the base of the natural logarithm.
     * This constant is approximately equal to 2.718281828459045 and is defined in {@link Math#E}.
     */
    public static final double E = Math.E;

    /**
     * A constant representing the default implementation strategy for
     * mathematical computations in the TMath class.
     * <p>
     * This field is initialized to {@code Impl.MATH}, which specifies
     * the use of the default mathematical implementation strategy.
     * The chosen strategy impacts the behavior and performance of
     * mathematical operations provided by the TMath class, balancing
     * between precision and efficiency.
     */
    private static final Impl IMPL = Impl.FAST;

    // ----------------------------------------------------------------
    // constants
    // ----------------------------------------------------------------

    /**
     * Private constructor to prevent instantiation of the TMath class.
     *
     * This class is designed to provide utility methods for mathematical
     * operations and cannot be instantiated.
     */
    private TMath() {
    }

    /**
     * Computes the absolute value of the given integer using the specified implementation.
     *
     * @param x the integer value for which the absolute value is to be computed
     * @return the absolute value of the input integer
     */
    public static int abs(int x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.abs(x);
            case FAST:
                return FastMath.abs(x);
            default:
                return Math.abs(x);
        }
    }

    /**
     * Computes the absolute value of the given long integer using the specified implementation.
     *
     * @param x the long integer value for which the absolute value is to be computed
     * @return the absolute value of the input long integer
     */
    public static long abs(long x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.abs(x);
            case FAST:
                return FastMath.abs(x);
            default:
                return Math.abs(x);
        }
    }

    /**
     * Computes the absolute value of the given float using the specified implementation.
     *
     * @param x the float value for which the absolute value is to be computed
     * @return the absolute value of the input float
     */
    public static float abs(float x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.abs(x);
            case FAST:
                return FastMath.abs(x);
            default:
                return Math.abs(x);
        }
    }

    /**
     * Computes the absolute value of the given double using the specified implementation.
     *
     * @param x the double value for which the absolute value is to be computed
     * @return the absolute value of the input double
     */
    public static double abs(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.abs(x);
            case FAST:
                return FastMath.abs(x);
            default:
                return Math.abs(x);
        }
    }

    /**
     * Computes the maximum of two integers using the specified implementation.
     *
     * @param a the first integer value
     * @param b the second integer value
     * @return the maximum of the two input integers
     */
    public static int max(int a, int b) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.max(a, b);
            case FAST:
                return FastMath.max(a, b);
            default:
                return Math.max(a, b);
        }
    }

    // ----------------------------------------------------------------
    // max / min
    // ----------------------------------------------------------------

    /**
     * Computes the maximum of two long integers using the specified implementation.
     *
     * @param a the first long integer value
     * @param b the second long integer value
     * @return the maximum of the two input long integers
     */
    public static long max(long a, long b) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.max(a, b);
            case FAST:
                return FastMath.max(a, b);
            default:
                return Math.max(a, b);
        }
    }

    /**
     * Computes the maximum of two float values using the specified implementation.
     *
     * @param a the first float value
     * @param b the second float value
     * @return the maximum of the two input float values
     */
    public static float max(float a, float b) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.max(a, b);
            case FAST:
                return FastMath.max(a, b);
            default:
                return Math.max(a, b);
        }
    }

    /**
     * Computes the maximum of two double values using the specified implementation.
     *
     * @param a the first double value
     * @param b the second double value
     * @return the maximum of the two input double values
     */
    public static double max(double a, double b) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.max(a, b);
            case FAST:
                return FastMath.max(a, b);
            default:
                return Math.max(a, b);
        }
    }

    /**
     * Computes the minimum of two integer values using the specified implementation.
     *
     * @param a the first integer value
     * @param b the second integer value
     * @return the minimum of the two input integer values
     */
    public static int min(int a, int b) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.min(a, b);
            case FAST:
                return FastMath.min(a, b);
            default:
                return Math.min(a, b);
        }
    }

    /**
     * Computes the minimum of two long integers using the specified implementation.
     *
     * @param a the first long integer value
     * @param b the second long integer value
     * @return the minimum of the two input long integers
     */
    public static long min(long a, long b) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.min(a, b);
            case FAST:
                return FastMath.min(a, b);
            default:
                return Math.min(a, b);
        }
    }

    /**
     * Computes the minimum of two float values using the specified implementation.
     *
     * @param a the first float value
     * @param b the second float value
     * @return the minimum of the two input float values
     */
    public static float min(float a, float b) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.min(a, b);
            case FAST:
                return FastMath.min(a, b);
            default:
                return Math.min(a, b);
        }
    }

    /**
     * Computes the minimum of two double values using the specified implementation.
     *
     * @param a the first double value
     * @param b the second double value
     * @return the minimum of the two input double values
     */
    public static double min(double a, double b) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.min(a, b);
            case FAST:
                return FastMath.min(a, b);
            default:
                return Math.min(a, b);
        }
    }

    /**
     * Computes the absolute value of a double value using the specified implementation.
     *
     * @param x the input double value
     * @return the absolute value of the input double value
     */
    public static double exp(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.exp(x);
            case FAST:
                return FastMath.exp(x);
            default:
                return Math.exp(x);
        }
    }

    // ----------------------------------------------------------------
    // exponentials and logs
    // ----------------------------------------------------------------

    /**
     * Computes the natural logarithm of a double value using the specified implementation.
     *
     * @param x the input double value
     * @return the natural logarithm of the input double value
     */
    public static double expm1(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.expm1(x);
            case FAST:
                return FastMath.expm1(x);
            default:
                return Math.expm1(x);
        }
    }

    /**
     * Computes the base-2 logarithm of a double value using the specified implementation.
     *
     * @param x the input double value
     * @return the base-2 logarithm of the input double value
     */
    public static double log(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.log(x);
            case FAST:
                return FastMath.log(x);
            default:
                return Math.log(x);
        }
    }

    /**
     * Computes the base-10 logarithm of a double value using the specified implementation.
     *
     * @param x the input double value
     * @return the base-10 logarithm of the input double value
     */
    public static double log10(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.log10(x);
            case FAST:
                return FastMath.log10(x);
            default:
                return Math.log10(x);
        }
    }

    /**
     * Computes the logarithm of a double value using the specified implementation.
     *
     * @param x the input double value
     * @return the logarithm of the input double value
     */
    public static double log1p(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.log1p(x);
            case FAST:
                return FastMath.log1p(x);
            default:
                return Math.log1p(x);
        }
    }

    /**
     * Computes the value of the first argument raised to the power of the second argument
     * using the specified internal implementation.
     *
     * @param a the base value
     * @param b the exponent value
     * @return the result of raising the base value to the power of the exponent
     */
    public static double pow(double a, double b) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.pow(a, b);
            case FAST:
                return FastMath.pow(a, b);
            default:
                return Math.pow(a, b);
        }
    }

    /**
     * Computes the square root of the given double value using the specified implementation.
     *
     * Depending on the underlying implementation, this method may use {@code StrictMath.sqrt},
     * {@code FastMath.sqrt}, or {@code Math.sqrt} to calculate the result.
     *
     * @param x the double value for which the square root is to be computed
     * @return the square root of the input value
     *         or NaN if the input value is negative
     */
    public static double sqrt(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.sqrt(x);
            case FAST:
                return FastMath.sqrt(x);
            default:
                return Math.sqrt(x);
        }
    }

    /**
     * Computes the cube root of a given double value using the specified implementation strategy.
     *
     * @param x the value for which the cube root is to be calculated
     * @return the cube root of the input value
     */
    public static double cbrt(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.cbrt(x);
            case FAST:
                return FastMath.cbrt(x);
            default:
                return Math.cbrt(x);
        }
    }

    /**
     * Calculates the square root of the sum of the squares of two arguments
     * without intermediate overflow or underflow.
     *
     * @param x the first value
     * @param y the second value
     * @return the square root of (x² + y²)
     */
    public static double hypot(double x, double y) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.hypot(x, y);
            case FAST:
                return FastMath.hypot(x, y);
            default:
                return Math.hypot(x, y);
        }
    }

    /**
     * Computes the trigonometric sine of an angle given in radians.
     *
     * @param x the angle in radians for which the sine value is to be calculated
     * @return the sine of the specified angle
     */
    public static double sin(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.sin(x);
            case FAST:
                return FastMath.sin(x);
            default:
                return Math.sin(x);
        }
    }

    // ----------------------------------------------------------------
    // trig
    // ----------------------------------------------------------------

    /**
     * Computes the cosine of a given angle in radians.
     *
     * @param x the angle in radians for which the cosine is to be calculated
     * @return the cosine of the specified angle
     */
    public static double cos(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.cos(x);
            case FAST:
                return FastMath.cos(x);
            default:
                return Math.cos(x);
        }
    }

    /**
     * Computes the trigonometric tangent of an angle.
     *
     * @param x The angle, in radians, for which the tangent is to be calculated.
     * @return The tangent of the specified angle.
     */
    public static double tan(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.tan(x);
            case FAST:
                return FastMath.tan(x);
            default:
                return Math.tan(x);
        }
    }

    /**
     * Computes the arc sine (inverse sine) of the given value. The returned angle is in radians
     * and lies in the range -π/2 through π/2.
     *
     * @param x the value whose arc sine is to be computed, must be in the range -1 to 1
     * @return the arc sine of the argument, in radians
     */
    public static double asin(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.asin(x);
            case FAST:
                return FastMath.asin(x);
            default:
                return Math.asin(x);
        }
    }

    /**
     * Calculates the arc cosine (inverse cosine) of a given value.
     * The method returns the angle in radians whose cosine is the specified value.
     *
     * @param x the value whose arc cosine is to be calculated.
     *          The value must be within the range -1.0 to 1.0, inclusive.
     * @return the arc cosine of the specified value, in the range 0.0 through π (inclusive).
     */
    public static double acos(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.acos(x);
            case FAST:
                return FastMath.acos(x);
            default:
                return Math.acos(x);
        }
    }

    /**
     * Computes the arc tangent of a value. This method returns the angle
     * in radians whose tangent is the given value.
     *
     * @param x the value whose arc tangent is to be calculated.
     * @return the arc tangent of the specified value, in radians.
     */
    public static double atan(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.atan(x);
            case FAST:
                return FastMath.atan(x);
            default:
                return Math.atan(x);
        }
    }

    /**
     * Computes the angle θ (in radians) between the positive x-axis of a plane and the point (x, y) on it.
     * The angle is measured counterclockwise from the positive x-axis, and it is within the range of -π to π.
     *
     * @param y the ordinate (y-coordinate) of the point
     * @param x the abscissa (x-coordinate) of the point
     * @return the angle θ in radians between the positive x-axis and the line segment from the origin to the point (x, y)
     */
    public static double atan2(double y, double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.atan2(y, x);
            case FAST:
                return FastMath.atan2(y, x);
            default:
                return Math.atan2(y, x);
        }
    }

    /**
     * Computes the hyperbolic sine of a given angle in radians.
     *
     * @param x the angle in radians for which the hyperbolic sine is to be computed
     * @return the hyperbolic sine of the specified angle
     */
    public static double sinh(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.sinh(x);
            case FAST:
                return FastMath.sinh(x);
            default:
                return Math.sinh(x);
        }
    }

    // ----------------------------------------------------------------
    // hyperbolic
    // ----------------------------------------------------------------

    /**
     * Computes the hyperbolic cosine of a given value.
     *
     * @param x the value for which the hyperbolic cosine is to be computed
     * @return the hyperbolic cosine of the specified value
     */
    public static double cosh(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.cosh(x);
            case FAST:
                return FastMath.cosh(x);
            default:
                return Math.cosh(x);
        }
    }

    /**
     * Computes the hyperbolic tangent of a given value. The method delegates the computation
     * to one of the available implementations depending on the current setting (STRICT, FAST, or default).
     *
     * @param x the value for which the hyperbolic tangent is to be computed
     * @return the hyperbolic tangent of the specified value
     */
    public static double tanh(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.tanh(x);
            case FAST:
                return FastMath.tanh(x);
            default:
                return Math.tanh(x);
        }
    }

    /**
     * Computes the largest (closest to positive infinity) double value that
     * is less than or equal to the argument and is equal to a mathematical integer.
     *
     * @param x The value to be floored.
     * @return The largest double value less than or equal to {@code x}
     *         that is equal to a mathematical integer.
     */
    public static double floor(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.floor(x);
            case FAST:
                return FastMath.floor(x);
            default:
                return Math.floor(x);
        }
    }

    // ----------------------------------------------------------------
    // rounding
    // ----------------------------------------------------------------

    /**
     * Returns the smallest (closest to negative infinity) double value that is
     * greater than or equal to the argument and is equal to a mathematical integer.
     *
     * @param x the value to be rounded up to the nearest integer
     * @return the smallest double value that is greater than or equal to x
     *         and is equal to a mathematical integer
     */
    public static double ceil(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.ceil(x);
            case FAST:
                return FastMath.ceil(x);
            default:
                return Math.ceil(x);
        }
    }

    /**
     * Returns the closest value to the argument, with ties rounding to the even neighbor.
     * The method behavior depends on the implementation mode being used.
     *
     * @param x the value to be rounded to the nearest integer value
     * @return the value nearest to the argument, with ties rounded to the even neighbor
     */
    public static double rint(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.rint(x);
            case FAST:
                return FastMath.rint(x);
            default:
                return Math.rint(x);
        }
    }

    /**
     * Rounds the specified floating-point value to the nearest long value.
     * The rounding method depends on the current implementation mode: STRICT, FAST, or the default.
     *
     * @param x the double value to be rounded
     * @return the value of the argument rounded to the nearest {@code long} value
     */
    public static long round(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.round(x);
            case FAST:
                return FastMath.round(x);
            default:
                return Math.round(x);
        }
    }

    /**
     * Rounds the given floating-point value to the nearest integer.
     * The rounding behavior depends on the implementation strategy.
     *
     * @param x the floating-point value to be rounded
     * @return the rounded integer value
     */
    public static int round(float x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.round(x);
            case FAST:
                return FastMath.round(x);
            default:
                return Math.round(x);
        }
    }

    /**
     * Converts an angle measured in degrees to an approximately equivalent angle in radians.
     * This method provides flexibility in selecting the underlying implementation for conversion.
     *
     * @param x the angle in degrees
     * @return the angle in radians
     */
    public static double toRadians(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.toRadians(x);
            case FAST:
                return FastMath.toRadians(x);
            default:
                return Math.toRadians(x);
        }
    }

    // ----------------------------------------------------------------
    // angle conversions
    // ----------------------------------------------------------------

    /**
     * Converts an angle measured in radians to an approximately equivalent angle measured in degrees.
     * The conversion from radians to degrees is generally performed by multiplying the input value
     * by the factor of 180 divided by π.
     *
     * @param x the angle in radians to be converted to degrees
     * @return the converted angle in degrees
     */
    public static double toDegrees(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.toDegrees(x);
            case FAST:
                return FastMath.toDegrees(x);
            default:
                return Math.toDegrees(x);
        }
    }

    /**
     * Returns the size of an ulp (unit in the last place) of the argument.
     * The ulp of a floating-point value is the positive distance between
     * this floating-point value and the next larger value representable
     * in the same precision.
     *
     * @param x the floating-point value whose ulp is to be determined
     * @return the size of an ulp of the argument
     */
    public static double ulp(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.ulp(x);
            case FAST:
                return FastMath.ulp(x);
            default:
                return Math.ulp(x);
        }
    }

    // ----------------------------------------------------------------
    // floating-point helpers
    // ----------------------------------------------------------------

    /**
     * Returns the floating-point value adjacent to the input in the direction of positive infinity.
     *
     * @param x the floating-point value for which the next higher value is to be returned
     * @return the smallest floating-point value that is greater than x and representable as a double
     */
    public static double nextUp(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.nextUp(x);
            case FAST:
                return FastMath.nextUp(x);
            default:
                return Math.nextUp(x);
        }
    }

    /**
     * Returns the floating-point value adjacent to the argument in the direction of negative infinity.
     *
     * @param x the floating-point value whose adjacent value in the direction of negative infinity is to be returned
     * @return the adjacent floating-point value in the direction of negative infinity
     */
    public static double nextDown(double x) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.nextDown(x);
            case FAST:
                return FastMath.nextDown(x);
            default:
                return Math.nextDown(x);
        }
    }

    /**
     * Returns the floating-point number that is adjacent to the first argument
     * in the direction of the second argument. This method determines the result
     * based on the current implementation strategy.
     *
     * @param start the starting floating-point value.
     * @param direction the floating-point value indicating the direction of the adjacent value.
     * @return the floating-point number adjacent to {@code start} in the
     *         direction of {@code direction}.
     */
    public static double nextAfter(double start, double direction) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.nextAfter(start, direction);
            case FAST:
                return FastMath.nextAfter(start, direction);
            default:
                return Math.nextAfter(start, direction);
        }
    }

    /**
     * Returns a floating-point value obtained by scaling the specified floating-point value `d`
     * by an integer power of two specified by `scaleFactor`. This operation multiplies `d` by
     * 2 to the power of `scaleFactor`.
     *
     * @param d the floating-point value to be scaled
     * @param scaleFactor the power of two by which to scale the floating-point value
     * @return the scaled floating-point value, which is `d * 2^scaleFactor`
     */
    public static double scalb(double d, int scaleFactor) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.scalb(d, scaleFactor);
            case FAST:
                return FastMath.scalb(d, scaleFactor);
            default:
                return Math.scalb(d, scaleFactor);
        }
    }

    /**
     * Returns the unbiased exponent used in the representation of the floating-point value.
     * The method delegates the computation to different implementations depending on the configured mode.
     *
     * @param d the floating-point value whose exponent is to be extracted
     * @return the unbiased exponent of the given floating-point value
     */
    public static int getExponent(double d) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.getExponent(d);
            case FAST:
                return FastMath.getExponent(d);
            default:
                return Math.getExponent(d);
        }
    }

    /**
     * Calculates the signum function of the specified double value.
     * The signum function returns:
     * - 1.0 if the input is positive.
     * - -1.0 if the input is negative.
     * - 0.0 if the input is zero.
     *
     * @param d the input value whose signum is to be computed
     * @return  the signum of the input value as a double, which can be 1.0, -1.0, or 0.0
     */
    public static double signum(double d) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.signum(d);
            case FAST:
                return FastMath.signum(d);
            default:
                return Math.signum(d);
        }
    }

    /**
     * Generates a random double value between 0.0 (inclusive) and 1.0 (exclusive).
     * The method selected for generating the random value depends on the implementation mode:
     * STRICT, FAST, or the default Math.random().
     *
     * @return a pseudorandom double greater than or equal to 0.0 and less than 1.0
     */
    public static double random() {
        switch (IMPL) {
            case STRICT:
                return StrictMath.random();
            case FAST:
                return FastMath.random();
            default:
                return Math.random();
        }
    }

    /**
     * Returns the first floating-point argument with the sign of the second
     * floating-point argument. This method ensures the sign of the result matches
     * the sign of the second argument, while the magnitude matches the first argument.
     *
     * @param magnitude the value whose magnitude is to be returned
     * @param sign the value whose sign is to be assigned to the result
     * @return a floating-point value with the magnitude of {@code magnitude} and the sign of {@code sign}
     */
    public static double copySign(double magnitude, double sign) {
        switch (IMPL) {
            case STRICT:
                return StrictMath.copySign(magnitude, sign);
            case FAST:
                return FastMath.copySign(magnitude, sign);
            default:
                return Math.copySign(magnitude, sign);
        }
    }

    /**
     * Enumerates the available implementation strategies for mathematical computations.
     * Each strategy represents a distinct approach to balancing precision, performance,
     * and adherence to mathematical standards.
     */
    public enum Impl {

        /**
         * Represents the default mathematical implementation strategy in the system.
         * This option is used when no specific preference between precision and performance is required.
         */
        MATH,

        /**
         * Represents the strict implementation strategy for mathematical computations.
         * This option prioritizes precision and strict adherence to mathematical standards,
         * potentially at the cost of computational performance.
         */
        STRICT,

        /**
         * Represents the fast implementation strategy for mathematical computations.
         * This option is optimized for performance and may prioritize speed over precision
         * or strict adherence to mathematical standards.
         */
        FAST
    }
}