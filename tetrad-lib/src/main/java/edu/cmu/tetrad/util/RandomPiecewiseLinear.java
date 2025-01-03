package edu.cmu.tetrad.util;

import java.util.Arrays;

/**
 * Generates a random piecewise linear function that is monotonic (invertible).
 */
public class RandomPiecewiseLinear {

    private double xMin;
    private double xMax;
    private double yMin;
    private double yMax;
    private final int numSegments;
    private double[] breakpoints;
    private double[] slopes;
    private double[] intercepts;

    private final boolean isDefaultRange; // True for (0,0) -> (1,1), False for (0,1) -> (1,0)

    /**
     * Constructs a random piecewise linear function with specified parameters.
     */
    private RandomPiecewiseLinear(double xMin, double xMax, double yMin, double yMax, int numSegments, boolean isDefaultRange) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.numSegments = numSegments;
        this.yMin = yMin;
        this.yMax = yMax;
        this.isDefaultRange = isDefaultRange;

        initialize(xMin, xMax, numSegments, yMin, yMax);
    }

    private void initialize(double xMin, double xMax, int numSegments, double yMin, double yMax) {
        breakpoints = new double[numSegments + 1];
        breakpoints[0] = xMin;
        breakpoints[numSegments] = xMax;

        for (int i = 1; i < numSegments; i++) {
            breakpoints[i] = xMin + (xMax - xMin) * Math.random();
        }
        Arrays.sort(breakpoints);

        slopes = new double[numSegments];
        for (int i = 0; i < numSegments; i++) {
            slopes[i] = 0.5 + Math.random() * 1.5; // Slopes between 0.5 and 2.0
        }

        intercepts = new double[numSegments];
        if (isDefaultRange) {
            intercepts[0] = yMin; // Start at yMin
        } else {
            intercepts[0] = yMax; // Start at yMax
        }

        for (int i = 1; i < numSegments; i++) {
            double dx = breakpoints[i] - breakpoints[i - 1];
            intercepts[i] = intercepts[i - 1] + slopes[i - 1] * dx;
        }

        double initialYMin = intercepts[0];
        double initialYMax = intercepts[numSegments - 1]
                             + slopes[numSegments - 1] * (xMax - breakpoints[numSegments - 1]);

        double scale = (yMax - yMin) / (initialYMax - initialYMin);
        double offset = yMin - initialYMin * scale;

        for (int i = 0; i < numSegments; i++) {
            slopes[i] *= scale;
            intercepts[i] = intercepts[i] * scale + offset;
        }
    }

    public static RandomPiecewiseLinear get(double xMin, double xMax, double yMin, double yMax, int numSegments, boolean isDefaultRange) {
        return new RandomPiecewiseLinear(xMin, xMax, yMin, yMax, numSegments, isDefaultRange);
    }

    public double evaluate(double x) {
        int segment = 0;
        while (segment < numSegments && x > breakpoints[segment + 1]) {
            segment++;
        }

        double dx = x - breakpoints[segment];
        return intercepts[segment] + slopes[segment] * dx;
    }

    public void setScale(double xMin, double xMax, double yMin, double yMax) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        initialize(xMin, xMax, numSegments, yMin, yMax);
    }

    public static void main(String[] args) {
        RandomPiecewiseLinear piecewiseLinear = RandomPiecewiseLinear.get(0, 1, 0, 1, 5, true);
        piecewiseLinear.setScale(-10, 10, 0, 100);

        double[] xValues = {-10, -5, 0, 5, 10};
        for (double x : xValues) {
            System.out.printf("x: %f, y: %f%n", x, piecewiseLinear.evaluate(x));
        }
    }
}
