package edu.cmu.tetrad.util;

import java.util.*;

/**
 * Represents a bijective, piecewise linear function whose control points are generated randomly. The function is
 * defined within a specified domain and range, and it can map values from one interval to another.
 *
 * @author GPT 4o.
 */
public class RandomPiecewiseLinearBijective {

    /**
     * List of control points defining the piecewise linear function.
     */
    private final List<double[]> controlPoints;
    /**
     * Represents the minimum value for the x-axis in the piecewise linear bijective function.
     * This value defines the lower bound for scaling and evaluating the function and serves
     * as a reference point for determining the input range.
     */
    private double xMin = 0;
    /**
     * Represents the maximum value for the x-axis in the range for scaling and evaluating the
     * piecewise linear bijective function. This value defines the upper limit of the x-domain
     * within which inputs are valid when evaluating or transforming the function.
     */
    private double xMax = 1;
    /**
     * Represents the minimum value for the y-axis in the context of scaling a piecewise linear bijective function.
     * This value defines the lower bound of the function's output range after scaling.
     */
    private double yMin = 0;
    /**
     * Represents the maximum value for the y-axis in the piecewise linear bijective function.
     * This value determines the upper bound of the output range for the function after scaling.
     */
    private double yMax = 1;

    /**
     * Constructs a RandomPiecewiseLinearBijective object with a specified number of segments and a seed for random
     * generation. This constructor generates control points that define the piecewise linear bijective function.
     *
     * @param numSegments the number of segments to divide the function into; must be at least 1
     * @param seed        the seed value for random number generation, ensuring reproducibility
     * @throws IllegalArgumentException if the number of segments is less than 1
     */
    public RandomPiecewiseLinearBijective(int numSegments, long seed) {
        if (numSegments < 1) {
            throw new IllegalArgumentException("Number of segments must be at least 1.");
        }

        this.controlPoints = generateControlPoints(numSegments, seed);
    }

    /**
     * The entry point of the program that demonstrates the use of the RandomPiecewiseLinearBijective class. It
     * initializes a piecewise linear bijective function with randomly generated control points, evaluates it at
     * specific points, and demonstrates scaling the function over a different range.
     *
     * @param args command-line arguments (not used in this program)
     */
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

    /**
     * Generates a list of control points to define a piecewise linear bijective function. The control points are
     * determined randomly based on the provided number of segments and a random seed for reproducibility.
     *
     * @param numSegments the number of segments into which the function is divided; must be at least 1
     * @param seed        the seed value for the random number generator for reproducibility
     * @return a list of control points, where each control point is represented as a two-element double array
     */
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
            yValues.replaceAll(aDouble -> 1.0 - aDouble);
        }

        // Pair the x and y coordinates to form control points
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < xValues.size(); i++) {
            points.add(new double[]{xValues.get(i), yValues.get(i)});
        }

        return points;
    }

    /**
     * Evaluates the piecewise linear bijective function at the given input value. Performs linear interpolation between
     * control points to compute the result.
     *
     * @param x the input value to evaluate the function at; must be in the range [0, 1]
     * @return the evaluated result based on the piecewise linear interpolation
     * @throws IllegalArgumentException if the input x is outside the range [0, 1]
     * @throws IllegalStateException    if evaluation fails, indicating an unexpected internal error
     */
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

    /**
     * Sets the scale of the function by updating the minimum and maximum values of the x-axis and y-axis.
     *
     * @param xMin the minimum value for the x-axis
     * @param xMax the maximum value for the x-axis
     * @param yMin the minimum value for the y-axis
     * @param yMax the maximum value for the y-axis
     */
    public void setScale(double xMin, double xMax, double yMin, double yMax) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    /**
     * Evaluates the piecewise linear bijective function for a given input value. Scales the input value to the range
     * [0, 1], evaluates the function in that range, and then scales the output value back to the specified range [yMin,
     * yMax].
     *
     * @param x the input value to evaluate; must be within the range [xMin, xMax]
     * @return the output value of the function, scaled to the range [yMin, yMax]
     * @throws IllegalArgumentException if the input x is outside the range [xMin, xMax]
     */
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

    /**
     * Retrieves the list of control points defining the piecewise linear bijective function. The control points are
     * immutable and represent key points for the function's segments.
     *
     * @return an unmodifiable list of control points, each represented as a two-element double array
     */
    public List<double[]> getControlPoints() {
        return Collections.unmodifiableList(controlPoints);
    }
}
