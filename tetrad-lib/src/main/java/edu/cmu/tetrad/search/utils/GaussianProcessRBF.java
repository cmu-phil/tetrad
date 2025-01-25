package edu.cmu.tetrad.search.utils;

import java.util.Random;

/**
 * GaussianProcessRBF simulates a Gaussian Process (GP) with a Radial Basis Function (RBF) kernel.
 * It provides functionality for generating simulated function values based on the covariance matrix
 * of the RBF kernel and evaluating the function at arbitrary points using interpolation.
 */
public class GaussianProcessRBF {
    private final double[] xValues;        // Input points
    private final double[][] covarianceMatrix; // Covariance matrix
    private final double[] functionValues;     // Simulated function values
    private final Random random;               // Random number generator

    private final double lengthScale;  // Length scale parameter for the RBF kernel
    private final double amplitude;    // Amplitude parameter for the RBF kernel
    private final double noiseStd;     // Noise standard deviation for numerical stability

    /**
     * Constructor for Gaussian Process simulation using an RBF kernel.
     *
     * @param xValues     Input points for simulation.
     * @param lengthScale Length scale parameter for the RBF kernel.
     * @param amplitude   Amplitude parameter for the RBF kernel.
     * @param noiseStd    Small noise for numerical stability.
     */
    public GaussianProcessRBF(double[] xValues, double lengthScale, double amplitude, double noiseStd) {
        this.xValues = xValues;
        this.lengthScale = lengthScale;
        this.amplitude = amplitude;
        this.noiseStd = noiseStd;
        this.random = new Random();

        // Compute the covariance matrix
        this.covarianceMatrix = computeCovarianceMatrix(xValues);

        // Simulate function values
        this.functionValues = simulateFunction();
    }

    /**
     * The main method serves as the entry point for the Gaussian Process simulation demonstration.
     * It creates a series of input points, initializes the simulation parameters,
     * and uses those to create and evaluate a Gaussian Process with an RBF kernel.
     *
     * @param args Command-line arguments passed to the program, not used in this demonstration.
     */
    public static void main(String[] args) {
        // Input points for the GP
        double[] xValues = new double[100];
        for (int i = 0; i < 100; i++) {
            xValues[i] = -5 + i * 0.1; // Generate points in the range [-5, 5]
        }

        // GP parameters
        double lengthScale = 1.0;  // Smoothness of the function
        double amplitude = 1.0;   // Magnitude of the function
        double noiseStd = 1e-6;   // Stability noise

        // Create a Gaussian Process simulator
        GaussianProcessRBF gp = new GaussianProcessRBF(xValues, lengthScale, amplitude, noiseStd);

        // Test the function at various points
        System.out.println("Simulated function values at selected points:");
        for (double x = -5; x <= 5; x += 0.5) {
            System.out.printf("f(%5.2f) = %5.2f%n", x, gp.evaluate(x));
        }
    }

    /**
     * Computes the RBF kernel matrix.
     *
     * @param xValues Input points.
     * @return The covariance matrix for the RBF kernel.
     */
    private double[][] computeCovarianceMatrix(double[] xValues) {
        int n = xValues.length;
        double[][] matrix = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double sqDist = Math.pow(xValues[i] - xValues[j], 2);
                matrix[i][j] = amplitude * Math.exp(-sqDist / (2 * Math.pow(lengthScale, 2)));
            }
        }

        // Add noise to the diagonal for numerical stability
        for (int i = 0; i < n; i++) {
            matrix[i][i] += Math.pow(noiseStd, 2);
        }

        return matrix;
    }

    /**
     * Simulates random function values from the Gaussian Process.
     *
     * @return Simulated function values.
     */
    private double[] simulateFunction() {
        int n = xValues.length;

        // Generate random Gaussian noise
        double[] noise = new double[n];
        for (int i = 0; i < n; i++) {
            noise[i] = random.nextGaussian();
        }

        // Simulate function values by applying the covariance matrix
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                values[i] += covarianceMatrix[i][j] * noise[j];
            }
        }

        return values;
    }

    /**
     * Evaluates the simulated function at a given point using interpolation.
     *
     * @param x The input value.
     * @return The interpolated function value.
     */
    public double evaluate(double x) {
        double result = 0.0;

        for (int i = 0; i < xValues.length; i++) {
            double sqDist = Math.pow(x - xValues[i], 2);
            double kernelValue = amplitude * Math.exp(-sqDist / (2 * Math.pow(lengthScale, 2)));
            result += kernelValue * functionValues[i];
        }

        return result;
    }

    /**
     * Evaluates the adjusted value of the simulated function at a given point. The adjustment is done by subtracting
     * the function value at 0.
     *
     * @param x The input value at which the adjusted function is evaluated.
     * @return The adjusted function value at the input point.
     */
    public double adjustedEvaluate(double x) {
        double zeroValue = evaluate(0);
        return evaluate(x) - zeroValue;
    }
}
