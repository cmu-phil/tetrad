package edu.cmu.tetrad.search.utils;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Arrays;
import java.util.Random;

public class RandomFunctions {

    private static final Random RANDOM = new Random();

    /**
     * Generates a random polynomial function and evaluates it at x. Optionally passes through (0, 0) by setting the
     * constant term to 0.
     */
    public static double randomPolynomial(double x, int degree, double scale, boolean passThroughOrigin) {
        double[] coefficients = new double[degree + 1];
        for (int i = 0; i < coefficients.length; i++) {
            coefficients[i] = (passThroughOrigin && i == 0) ? 0 : scale * (2 * RANDOM.nextDouble() - 1);
        }
        double result = 0;
        for (int i = 0; i < coefficients.length; i++) {
            result += coefficients[i] * Math.pow(x, i);
        }
        return result;
    }

    /**
     * Generates a random Fourier series and evaluates it at x. Optionally passes through (0, 0) by adjusting the
     * constant sine term.
     */
    public static double randomFourier(double x, int numTerms, double scale, boolean passThroughOrigin) {
        double[] a = new double[numTerms];
        double[] b = new double[numTerms];
        for (int i = 0; i < numTerms; i++) {
            a[i] = scale * (2 * RANDOM.nextDouble() - 1);
            b[i] = scale * (2 * RANDOM.nextDouble() - 1);
        }
        if (passThroughOrigin) {
            b[0] = 0; // Adjust to pass through origin
        }
        double result = 0;
        for (int k = 1; k <= numTerms; k++) {
            result += a[k - 1] * Math.cos(k * x) + b[k - 1] * Math.sin(k * x);
        }
        return result;
    }

    /**
     * Generates a random piecewise linear function. Optionally passes through (0, 0) by including (0, 0) in the
     * points.
     */
    public static double randomPiecewiseLinear(double x, double min, double max, int numPoints, boolean passThroughOrigin) {
        double[] xPoints = new double[numPoints];
        double[] yPoints = new double[numPoints];

        for (int i = 0; i < numPoints; i++) {
            xPoints[i] = min + (max - min) * RANDOM.nextDouble();
            yPoints[i] = 2 * (RANDOM.nextDouble() - 0.5); // Random values in [-1, 1]
        }

        if (passThroughOrigin) {
            xPoints[0] = 0;
            yPoints[0] = 0;
        }

        Arrays.sort(xPoints);

        // Linear interpolation
        for (int i = 1; i < xPoints.length; i++) {
            if (x >= xPoints[i - 1] && x <= xPoints[i]) {
                double slope = (yPoints[i] - yPoints[i - 1]) / (xPoints[i] - xPoints[i - 1]);
                return yPoints[i - 1] + slope * (x - xPoints[i - 1]);
            }
        }

        return 0; // Default return (shouldn't happen with valid input)
    }

    /**
     * Generates a random Radial Basis Function (RBF) and evaluates it at x. Optionally passes through (0, 0) by
     * adjusting the function.
     */
    public static double randomRBF(double x, double min, double max, int numCenters, double sigma, boolean passThroughOrigin) {
        double[] centers = new double[numCenters];
        double[] amplitudes = new double[numCenters];

        for (int i = 0; i < numCenters; i++) {
            centers[i] = min + (max - min) * RANDOM.nextDouble();
            amplitudes[i] = 2 * (RANDOM.nextDouble() - 0.5); // Random values in [-1, 1]
        }

        double fAtZero = 0;
        for (int i = 0; i < numCenters; i++) {
            fAtZero += amplitudes[i] * Math.exp(-Math.pow(centers[i], 2) / (2 * Math.pow(sigma, 2)));
        }

        double result = 0;
        for (int i = 0; i < numCenters; i++) {
            result += amplitudes[i] * Math.exp(-Math.pow(x - centers[i], 2) / (2 * Math.pow(sigma, 2)));
        }

        if (passThroughOrigin) {
            result -= fAtZero;
        }

        return result;
    }

    /**
     * Compute the RBF (Radial Basis Function) kernel value for two points.
     */
    private static double rbfKernel(double x1, double x2, double lengthScale) {
        return Math.exp(-Math.pow(x1 - x2, 2) / (2 * Math.pow(lengthScale, 2)));
    }

    /**
     * Generate the covariance matrix using the RBF kernel.
     */
    private static RealMatrix computeCovarianceMatrix(double[] x, double lengthScale) {
        int n = x.length;
        double[][] covariance = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                covariance[i][j] = rbfKernel(x[i], x[j], lengthScale);
            }
        }
        return new Array2DRowRealMatrix(covariance);
    }

    /**
     * Generate the covariance matrix using the RBF kernel.
     */
    private static RealMatrix computeCovarianceMatrix(double[] x, double lengthScale, double jitter) {
        int n = x.length;
        double[][] covariance = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                covariance[i][j] = rbfKernel(x[i], x[j], lengthScale);
            }
        }

        // Add jitter to the diagonal
        for (int i = 0; i < n; i++) {
            covariance[i][i] += jitter;
        }

        return new Array2DRowRealMatrix(covariance);
    }

    /**
     * Sample a Gaussian Process function and optionally ensure it passes through (0, 0).
     */
    public static double[] sampleGaussianProcess(double[] x, double lengthScale, double jitter, boolean passThroughOrigin) {
        int n = x.length;

        // Step 1: Compute the covariance matrix
        RealMatrix covarianceMatrix = computeCovarianceMatrix(x, lengthScale, jitter);

        // Step 2: Perform Cholesky decomposition to ensure positive semi-definiteness
        CholeskyDecomposition cholesky = new CholeskyDecomposition(covarianceMatrix);

        // Step 3: Sample from a multivariate normal distribution
        double[] mean = new double[n];
        MultivariateNormalDistribution mvn = new MultivariateNormalDistribution(mean, covarianceMatrix.getData());
        double[] sampledValues = mvn.sample();

        // Step 4: Adjust to ensure the function passes through (0, 0) if needed
        if (passThroughOrigin) {
            // Find the index of the closest value to 0
            int closestIndex = 0;
            double minDistance = Math.abs(x[0]);
            for (int i = 1; i < n; i++) {
                if (Math.abs(x[i]) < minDistance) {
                    minDistance = Math.abs(x[i]);
                    closestIndex = i;
                }
            }
            // Subtract the value at the closest index to shift the function
            double adjustment = sampledValues[closestIndex];
            for (int i = 0; i < n; i++) {
                sampledValues[i] -= adjustment;
            }
        }

        return sampledValues;
    }

    public static void main(String[] args) {
        double x = 1.0; // Test point
        double min = -2.0, max = 2.0;

        System.out.println("Random Polynomial: " + randomPolynomial(x, 3, 1.0, true));
        System.out.println("Random Fourier: " + randomFourier(x, 5, 1.0, true));
        System.out.println("Random Piecewise Linear: " + randomPiecewiseLinear(x, min, max, 5, true));
        System.out.println("Random RBF: " + randomRBF(x, min, max, 5, 0.5, true));

        int numPoints = 200;
        double[] x2 = new double[numPoints];
        double step = (max - min) / (numPoints - 1);
        for (int i = 0; i < numPoints; i++) {
            x2[i] = min + i * step;
        }

        // Sample a Gaussian Process function
        double lengthScale = 0.5; // Smoothness parameter
        boolean passThroughOrigin = true;
        double[] y = sampleGaussianProcess(x2, lengthScale, 1e-3, passThroughOrigin);

        // Print results
        System.out.println("x2: " + Arrays.toString(x2));
        System.out.println("y: " + Arrays.toString(y));
    }
}

