package edu.cmu.tetrad.util;

import java.util.*;

public class RandomPiecewiseLinearBijective {

    private final List<double[]> controlPoints;
    private double xMin = 0;
    private double xMax = 1;
    private double yMin = 0;
    private double yMax = 1;

    public RandomPiecewiseLinearBijective(int numSegments, long seed) {
        if (numSegments < 1) {
            throw new IllegalArgumentException("Number of segments must be at least 1.");
        }

        this.controlPoints = generateControlPoints(numSegments, seed);
    }

    private List<double[]> generateControlPoints(int numSegments, long seed) {
        Random random = new Random(seed);

        // Generate random x-coordinates in (0, 1), ensuring they are strictly increasing
        List<Double> xValues = new ArrayList<>();
        for (int i = 0; i < numSegments - 1; i++) {
            xValues.add(random.nextDouble());
        }
        xValues.add(0.0);
        xValues.add(1.0);
        Collections.sort(xValues);

        // Generate random y-coordinates in (0, 1), ensuring they are strictly increasing
        List<Double> yValues = new ArrayList<>();
        for (int i = 0; i < numSegments - 1; i++) {
            yValues.add(random.nextDouble());
        }
        yValues.add(0.0);
        yValues.add(1.0);
        Collections.sort(yValues);

        // Determine if the function should be increasing or decreasing
        boolean isIncreasing = random.nextBoolean();
        if (!isIncreasing) {
            for (int i = 0; i < yValues.size(); i++) {
                yValues.set(i, 1.0 - yValues.get(i));
            }
        }

        // Pair the x and y coordinates to form control points
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < xValues.size(); i++) {
            points.add(new double[]{xValues.get(i), yValues.get(i)});
        }

        return points;
    }

    private double evaluateInternal(double x) {
        if (x < 0.0 || x > 1.0) {
            throw new IllegalArgumentException("Input x must be in the range [0, 1].");
        }

        // Find the segment containing x
        for (int i = 1; i < controlPoints.size(); i++) {
            double x1 = controlPoints.get(i - 1)[0];
            double y1 = controlPoints.get(i - 1)[1];
            double x2 = controlPoints.get(i)[0];
            double y2 = controlPoints.get(i)[1];

            if (x >= x1 && x <= x2) {
                // Linear interpolation
                double t = (x - x1) / (x2 - x1);
                return y1 + t * (y2 - y1);
            }
        }

        // This should never happen due to the bounds check at the start
        throw new IllegalStateException("Failed to evaluate x.");
    }

    public void setScale(double xMin, double xMax, double yMin, double yMax) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    public double evaluate(double x) {
        if (x < xMin || x > xMax) {
            throw new IllegalArgumentException("Input x must be in the range [xMin, xMax].");
        }

        // Scale x to [0, 1]
        double scaledX = (x - xMin) / (xMax - xMin);

        // Evaluate the function in [0, 1]
        double scaledY = evaluateInternal(scaledX);

        // Scale y back to [yMin, yMax]
        return yMin + scaledY * (yMax - yMin);
    }

    public List<double[]> getControlPoints() {
        return Collections.unmodifiableList(controlPoints);
    }

    public static void main(String[] args) {
        int numSegments = 5;
        long seed = new Date().getTime();

        RandomPiecewiseLinearBijective function = new RandomPiecewiseLinearBijective(numSegments, seed);
        System.out.println("Control Points:");
        for (double[] point : function.getControlPoints()) {
            System.out.printf("(%.4f, %.4f)%n", point[0], point[1]);
        }

        System.out.println("\nEvaluating function:");
        double[] testValues = {0.0, 0.25, 0.5, 0.75, 1.0};
        for (double x : testValues) {
            System.out.printf("f(%.2f) = %.4f%n", x, function.evaluate(x));
        }

        System.out.println("\nEvaluating function with scaling:");
        double xMin = -1.0, xMax = 2.0, yMin = 10.0, yMax = 20.0;
        for (double x : testValues) {
            double scaledX = xMin + x * (xMax - xMin); // Map test values to [xMin, xMax]
            function.setScale(xMin, xMax, yMin, yMax);
            System.out.printf("f(%.2f) = %.4f%n", scaledX, function.evaluate(scaledX));
        }
    }
}
