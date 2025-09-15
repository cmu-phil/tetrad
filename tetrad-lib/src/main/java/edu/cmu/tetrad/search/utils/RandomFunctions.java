///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Arrays;
import java.util.Random;

/**
 * A utility class for generating and evaluating random mathematical functions, including polynomials, Fourier series,
 * piecewise linear functions, radial basis functions, and sampled Gaussian processes. Provides control over specific
 * properties such as scaling and ensuring the function passes through the origin.
 */
public class RandomFunctions {

    private static final Random RANDOM = new Random();

    private RandomFunctions() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Generates a random polynomial function and evaluates it at a specified input value. The polynomial is constructed
     * using random coefficients for each term, up to the specified degree. Optionally ensures that the polynomial
     * passes through the origin.
     *
     * @param x                 the input value at which the polynomial is evaluated.
     * @param degree            the degree of the polynomial.
     * @param scale             a scaling factor for the random coefficients.
     * @param passThroughOrigin a boolean indicating whether the polynomial should be adjusted to pass through the
     *                          origin.
     * @return the evaluated value of the random polynomial at the given input x.
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
     * Generates a random Fourier series and evaluates it at a given input value x. The series is composed of a
     * specified number of terms, with random coefficients for sine and cosine components. Optionally ensures that the
     * resulted function passes through the origin.
     *
     * @param x                 the input value at which the Fourier series is evaluated.
     * @param numTerms          the number of terms in the Fourier series.
     * @param scale             a scaling factor for the random coefficients.
     * @param passThroughOrigin a boolean indicating whether the Fourier series should be adjusted to pass through (0,
     *                          0).
     * @return the evaluated value of the random Fourier series at the given input x.
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
     * Generates a random piecewise linear function and evaluates it at a specified input value x. The function is
     * constructed using random points within a defined range and optionally passes through the origin.
     *
     * @param x                 the input value at which the piecewise linear function is evaluated.
     * @param min               the minimum value for the range of x-coordinates of the random points.
     * @param max               the maximum value for the range of x-coordinates of the random points.
     * @param numPoints         the number of random points used to construct the piecewise linear function.
     * @param passThroughOrigin a boolean indicating whether the piecewise linear function should be adjusted to pass
     *                          through (0, 0).
     * @return the evaluated value of the piecewise linear function at the given input x.
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
     * Generates a random Radial Basis Function (RBF) and evaluates it at a given input value x. The RBF is constructed
     * using a specified number of centers, with each center assigned a random position and amplitude. Optionally
     * adjusts the function to pass through the origin.
     *
     * @param x                 the point at which the RBF is evaluated.
     * @param min               the minimum value for the range of the RBF centers.
     * @param max               the maximum value for the range of the RBF centers.
     * @param numCenters        the number of RBF centers to generate.
     * @param sigma             the standard deviation parameter defining the width of each RBF (Gaussian).
     * @param passThroughOrigin a boolean indicating whether the RBF should be adjusted to pass through the origin.
     * @return the evaluated value of the RBF at the given input point x.
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
     * Computes the Radial Basis Function (RBF) kernel for two input values. The RBF kernel measures the similarity
     * between the inputs based on the distance and the specified length scale.
     *
     * @param x1          the first input value.
     * @param x2          the second input value.
     * @param lengthScale the length scale parameter which controls the smoothness and extent of the similarity
     *                    measure.
     * @return the computed RBF kernel value as a double.
     */
    private static double rbfKernel(double x1, double x2, double lengthScale) {
        return Math.exp(-Math.pow(x1 - x2, 2) / (2 * Math.pow(lengthScale, 2)));
    }

    /**
     * Computes the covariance matrix for a given set of input values using the Radial Basis Function (RBF) kernel. The
     * computed covariance matrix defines the correlations between points based on the RBF kernel.
     *
     * @param x           an array of input values representing the points at which the covariance is computed.
     * @param lengthScale the length scale parameter of the RBF kernel, which controls the smoothness and correlation
     *                    between points.
     * @return a covariance matrix represented as a RealMatrix object, where each element corresponds to the kernel
     * value between two points.
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
     * Computes the covariance matrix using the Radial Basis Function (RBF) kernel for a given set of input values.
     * Optionally adds a small positive jitter value to the diagonal elements of the matrix to ensure numerical
     * stability.
     *
     * @param x           an array of input values representing the points at which the covariance is computed.
     * @param lengthScale the length scale parameter of the RBF kernel, controlling the correlation between points.
     * @param jitter      a small positive value added to the diagonal to improve numerical stability.
     * @return a covariance matrix represented as a RealMatrix object.
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
     * Samples values from a Gaussian Process defined by an RBF kernel with the given parameters.
     *
     * @param x                 an array of input values representing the points at which to sample the Gaussian
     *                          Process.
     * @param lengthScale       the length scale parameter of the RBF kernel, controlling the smoothness of the
     *                          function.
     * @param jitter            a small positive value added to the diagonal of the covariance matrix to ensure
     *                          numerical stability.
     * @param passThroughOrigin a boolean indicating whether the sampled values should be adjusted to pass through (0,
     *                          0).
     * @return an array of sampled values corresponding to the input points, representing the Gaussian Process
     * realization.
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

    /**
     * Executes a series of demonstrations of random mathematical function generators. The program generates and
     * evaluates random polynomial, Fourier series, piecewise linear, and radial basis function (RBF) at specific
     * points. It also samples a Gaussian Process function and outputs the results.
     *
     * @param args the command-line arguments (not used in this implementation).
     */
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


