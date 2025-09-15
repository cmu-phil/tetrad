package edu.cmu.tetrad.search.utils;

import java.util.Random;

/**
 * Represents a linear function in an n-dimensional space (R^n -> R). The function is defined as f(x) = c1*x1 + c2*x2 +
 * ... + cn*xn + intercept, where c1, c2, ..., cn are the coefficients, and intercept is a constant.
 */
public class LinearFunctionND {

    private final double[] coefficients; // Coefficients for the linear function
    private final double intercept;     // Intercept for the linear function

    /**
     * Constructor to create a linear function.
     *
     * @param inputDim      Number of input dimensions (R^n).
     * @param coefLow       Lower bound for the coefficients.
     * @param coefHigh      Upper bound for the coefficients.
     * @param coefSymmetric If true, coefficients may randomly switch sign.
     * @param seed          Random seed for reproducibility.
     */
    public LinearFunctionND(int inputDim, double coefLow, double coefHigh, boolean coefSymmetric, long seed) {
        if (coefLow > coefHigh) {
            throw new IllegalArgumentException("coefLow must be less than or equal to coefHigh.");
        }

        Random random;

        if (seed == -1) {
            random = new Random();
        } else {
            random = new Random(seed);
        }

        this.coefficients = new double[inputDim];
        this.intercept = coefLow + (coefHigh - coefLow) * random.nextDouble(); // Random intercept

        // Initialize coefficients randomly
        for (int i = 0; i < inputDim; i++) {
            double coef = coefLow + (coefHigh - coefLow) * random.nextDouble(); // Random coefficient
            if (coefSymmetric && random.nextBoolean()) {
                coef *= -1; // Randomly switch sign
            }
            this.coefficients[i] = coef;
        }
    }

    /**
     * The main method demonstrates the creation of a linear function with specified parameters, prints its coefficients
     * and intercept, and evaluates the function for a set of sample inputs.
     *
     * @param args Command-line arguments (not used in this demonstration).
     */
    public static void main(String[] args) {
        // Example usage: R^3 -> R with coefficients in [-2, 2], symmetric
        LinearFunctionND linearFunction = new LinearFunctionND(
                3, // Input dimension
                -2.0, // CoefLow
                2.0, // CoefHigh
                true, // CoefSymmetric
                42 // Random seed
        );

        // Print coefficients and intercept
        System.out.println("Coefficients: " + java.util.Arrays.toString(linearFunction.getCoefficients()));
        System.out.println("Intercept: " + linearFunction.getIntercept());

        // Evaluate the function for some sample inputs
        double[][] sampleInputs = {
                {1.0, 0.5, -1.2},
                {0.2, -0.3, 0.8},
                {-1.0, 1.5, 0.0},
                {0.0, 0.0, 0.0}
        };

        for (double[] input : sampleInputs) {
            double output = linearFunction.evaluate(input);
            System.out.printf("f(%s) = %.5f%n", java.util.Arrays.toString(input), output);
        }
    }

    /**
     * Evaluates the linear function for a given input vector.
     *
     * @param x Input vector in R^n.
     * @return Output value in R.
     */
    public double evaluate(double[] x) {
        if (x.length != coefficients.length) {
            throw new IllegalArgumentException("Input vector dimension does not match the expected dimension.");
        }

        double result = intercept;
        for (int i = 0; i < x.length; i++) {
            result += coefficients[i] * x[i];
        }
        return result;
    }

    /**
     * Evaluates the linear function with the given input vector and calculates the resulting adjusted value for
     * intercept == 0.0.
     *
     * @param x Input vector in R^n, where n is the number of dimensions. The length of this array must match the number
     *          of coefficients.
     * @return The computed value after evaluating the linear function using the input vector and coefficients.
     * @throws IllegalArgumentException If the dimension of the input vector does not match the expected number of
     *                                  coefficients.
     */
    public double evaluateAdjusted(double[] x) {
        if (x.length != coefficients.length) {
            throw new IllegalArgumentException("Input vector dimension does not match the expected dimension.");
        }

        double result = 0.0;
        for (int i = 0; i < x.length; i++) {
            result += coefficients[i] * x[i];
        }
        return result;
    }

    /**
     * Returns the coefficients of the linear function.
     *
     * @return Array of coefficients.
     */
    public double[] getCoefficients() {
        return coefficients;
    }

    /**
     * Returns the intercept of the linear function.
     *
     * @return Intercept value.
     */
    public double getIntercept() {
        return intercept;
    }
}
