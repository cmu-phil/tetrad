package edu.cmu.tetrad.util;

import org.apache.commons.math3.util.FastMath;

/**
 * Unified math interface for Tetrad.
 *
 * Allows switching between Math, StrictMath, and Math implementations
 * from a single place for experimentation and benchmarking.
 *
 * If IMPL is static final the JVM will constant-fold the switch and inline
 * the selected implementation.
 */
public final class TMath {

    public enum Impl {
        MATH,
        STRICT,
        FAST
    }

    private static final Impl IMPL = Impl.MATH;

    private TMath() {}

    // ----------------------------------------------------------------
    // constants
    // ----------------------------------------------------------------

    public static final double PI = Math.PI;
    public static final double E = Math.E;

    // ----------------------------------------------------------------
    // abs
    // ----------------------------------------------------------------

    public static int abs(int x) {
        switch (IMPL) {
            case STRICT: return StrictMath.abs(x);
            case FAST: return FastMath.abs(x);
            default: return Math.abs(x);
        }
    }

    public static long abs(long x) {
        switch (IMPL) {
            case STRICT: return StrictMath.abs(x);
            case FAST: return FastMath.abs(x);
            default: return Math.abs(x);
        }
    }

    public static float abs(float x) {
        switch (IMPL) {
            case STRICT: return StrictMath.abs(x);
            case FAST: return FastMath.abs(x);
            default: return Math.abs(x);
        }
    }

    public static double abs(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.abs(x);
            case FAST: return FastMath.abs(x);
            default: return Math.abs(x);
        }
    }

    // ----------------------------------------------------------------
    // max / min
    // ----------------------------------------------------------------

    public static int max(int a, int b) {
        switch (IMPL) {
            case STRICT: return StrictMath.max(a, b);
            case FAST: return FastMath.max(a, b);
            default: return Math.max(a, b);
        }
    }

    public static long max(long a, long b) {
        switch (IMPL) {
            case STRICT: return StrictMath.max(a, b);
            case FAST: return FastMath.max(a, b);
            default: return Math.max(a, b);
        }
    }

    public static float max(float a, float b) {
        switch (IMPL) {
            case STRICT: return StrictMath.max(a, b);
            case FAST: return FastMath.max(a, b);
            default: return Math.max(a, b);
        }
    }

    public static double max(double a, double b) {
        switch (IMPL) {
            case STRICT: return StrictMath.max(a, b);
            case FAST: return FastMath.max(a, b);
            default: return Math.max(a, b);
        }
    }

    public static int min(int a, int b) {
        switch (IMPL) {
            case STRICT: return StrictMath.min(a, b);
            case FAST: return FastMath.min(a, b);
            default: return Math.min(a, b);
        }
    }

    public static long min(long a, long b) {
        switch (IMPL) {
            case STRICT: return StrictMath.min(a, b);
            case FAST: return FastMath.min(a, b);
            default: return Math.min(a, b);
        }
    }

    public static float min(float a, float b) {
        switch (IMPL) {
            case STRICT: return StrictMath.min(a, b);
            case FAST: return FastMath.min(a, b);
            default: return Math.min(a, b);
        }
    }

    public static double min(double a, double b) {
        switch (IMPL) {
            case STRICT: return StrictMath.min(a, b);
            case FAST: return FastMath.min(a, b);
            default: return Math.min(a, b);
        }
    }

    // ----------------------------------------------------------------
    // exponentials and logs
    // ----------------------------------------------------------------

    public static double exp(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.exp(x);
            case FAST: return FastMath.exp(x);
            default: return Math.exp(x);
        }
    }

    public static double expm1(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.expm1(x);
            case FAST: return FastMath.expm1(x);
            default: return Math.expm1(x);
        }
    }

    public static double log(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.log(x);
            case FAST: return FastMath.log(x);
            default: return Math.log(x);
        }
    }

    public static double log10(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.log10(x);
            case FAST: return FastMath.log10(x);
            default: return Math.log10(x);
        }
    }

    public static double log1p(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.log1p(x);
            case FAST: return FastMath.log1p(x);
            default: return Math.log1p(x);
        }
    }

    public static double pow(double a, double b) {
        switch (IMPL) {
            case STRICT: return StrictMath.pow(a, b);
            case FAST: return FastMath.pow(a, b);
            default: return Math.pow(a, b);
        }
    }

    public static double sqrt(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.sqrt(x);
            case FAST: return FastMath.sqrt(x);
            default: return Math.sqrt(x);
        }
    }

    public static double cbrt(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.cbrt(x);
            case FAST: return FastMath.cbrt(x);
            default: return Math.cbrt(x);
        }
    }

    public static double hypot(double x, double y) {
        switch (IMPL) {
            case STRICT: return StrictMath.hypot(x, y);
            case FAST: return FastMath.hypot(x, y);
            default: return Math.hypot(x, y);
        }
    }

    // ----------------------------------------------------------------
    // trig
    // ----------------------------------------------------------------

    public static double sin(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.sin(x);
            case FAST: return FastMath.sin(x);
            default: return Math.sin(x);
        }
    }

    public static double cos(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.cos(x);
            case FAST: return FastMath.cos(x);
            default: return Math.cos(x);
        }
    }

    public static double tan(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.tan(x);
            case FAST: return FastMath.tan(x);
            default: return Math.tan(x);
        }
    }

    public static double asin(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.asin(x);
            case FAST: return FastMath.asin(x);
            default: return Math.asin(x);
        }
    }

    public static double acos(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.acos(x);
            case FAST: return FastMath.acos(x);
            default: return Math.acos(x);
        }
    }

    public static double atan(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.atan(x);
            case FAST: return FastMath.atan(x);
            default: return Math.atan(x);
        }
    }

    public static double atan2(double y, double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.atan2(y, x);
            case FAST: return FastMath.atan2(y, x);
            default: return Math.atan2(y, x);
        }
    }

    // ----------------------------------------------------------------
    // hyperbolic
    // ----------------------------------------------------------------

    public static double sinh(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.sinh(x);
            case FAST: return FastMath.sinh(x);
            default: return Math.sinh(x);
        }
    }

    public static double cosh(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.cosh(x);
            case FAST: return FastMath.cosh(x);
            default: return Math.cosh(x);
        }
    }

    public static double tanh(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.tanh(x);
            case FAST: return FastMath.tanh(x);
            default: return Math.tanh(x);
        }
    }

    // ----------------------------------------------------------------
    // rounding
    // ----------------------------------------------------------------

    public static double floor(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.floor(x);
            case FAST: return FastMath.floor(x);
            default: return Math.floor(x);
        }
    }

    public static double ceil(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.ceil(x);
            case FAST: return FastMath.ceil(x);
            default: return Math.ceil(x);
        }
    }

    public static double rint(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.rint(x);
            case FAST: return FastMath.rint(x);
            default: return Math.rint(x);
        }
    }

    public static long round(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.round(x);
            case FAST: return FastMath.round(x);
            default: return Math.round(x);
        }
    }

    public static int round(float x) {
        switch (IMPL) {
            case STRICT: return StrictMath.round(x);
            case FAST: return FastMath.round(x);
            default: return Math.round(x);
        }
    }

    // ----------------------------------------------------------------
    // angle conversions
    // ----------------------------------------------------------------

    public static double toRadians(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.toRadians(x);
            case FAST: return FastMath.toRadians(x);
            default: return Math.toRadians(x);
        }
    }

    public static double toDegrees(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.toDegrees(x);
            case FAST: return FastMath.toDegrees(x);
            default: return Math.toDegrees(x);
        }
    }

    // ----------------------------------------------------------------
    // floating-point helpers
    // ----------------------------------------------------------------

    public static double ulp(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.ulp(x);
            case FAST: return FastMath.ulp(x);
            default: return Math.ulp(x);
        }
    }

    public static double nextUp(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.nextUp(x);
            case FAST: return FastMath.nextUp(x);
            default: return Math.nextUp(x);
        }
    }

    public static double nextDown(double x) {
        switch (IMPL) {
            case STRICT: return StrictMath.nextDown(x);
            case FAST: return FastMath.nextDown(x);
            default: return Math.nextDown(x);
        }
    }

    public static double nextAfter(double start, double direction) {
        switch (IMPL) {
            case STRICT: return StrictMath.nextAfter(start, direction);
            case FAST: return FastMath.nextAfter(start, direction);
            default: return Math.nextAfter(start, direction);
        }
    }

    public static double scalb(double d, int scaleFactor) {
        switch (IMPL) {
            case STRICT: return StrictMath.scalb(d, scaleFactor);
            case FAST: return FastMath.scalb(d, scaleFactor);
            default: return Math.scalb(d, scaleFactor);
        }
    }

    public static int getExponent(double d) {
        switch (IMPL) {
            case STRICT: return StrictMath.getExponent(d);
            case FAST: return FastMath.getExponent(d);
            default: return Math.getExponent(d);
        }
    }

    public static double signum(double d) {
        switch (IMPL) {
            case STRICT: return StrictMath.signum(d);
            case FAST: return FastMath.signum(d);
            default: return Math.signum(d);
        }
    }

    public static double random() {
        switch (IMPL) {
            case STRICT: return StrictMath.random();
            case FAST: return FastMath.random();
            default: return Math.random();
        }
    }

    public static double copySign(double magnitude, double sign) {
        switch (IMPL) {
            case STRICT: return StrictMath.copySign(magnitude, sign);
            case FAST: return FastMath.copySign(magnitude, sign);
            default: return Math.copySign(magnitude, sign);
        }
    }
}