package edu.cmu.tetrad.search.utils;

import java.util.Random;

/**
 * A class that represents a Random Radial Basis Function (RBF) system.
 * This implementation generates RBFs using randomly chosen centers and amplitudes,
 * with a specified width parameter (sigma).
 */
public class RandomRBF {
    private final double[] centers;
    private final double[] amplitudes;
    private final double sigma;

    /**
     * Constructs a RandomRBF instance with randomly initialized centers and amplitudes.
     *
     * @param numCenters the number of centers for the radial basis functions.
     * @param sigma the width parameter of the radial basis functions, controlling the spread.
     */
    public RandomRBF(int numCenters, double sigma) {
        this.centers = new double[numCenters];
        this.amplitudes = new double[numCenters];
        this.sigma = sigma;

        Random random = new Random();
        for (int i = 0; i < numCenters; i++) {
            centers[i] = -2 + 4 * random.nextDouble(); // Random values in range [-2, 2]
            amplitudes[i] = -2 + 4 * random.nextDouble(); // Random amplitudes in range [-2, 2]
        }
    }

    /**
     * The entry point of the application. Demonstrates the usage of the RandomRBF class by
     * creating an instance with specified parameters and printing computed outputs for a
     * range of input values. Also includes a verification step to ensure that the adjusted
     * RBF function satisfies f(0) ≈ 0.
     *
     * @param args command-line arguments passed to the program. Not used in this implementation.
     */
    public static void main(String[] args) {
        int numCenters = 5;
        double sigma = 0.5;

        // Create a RandomRBF instance
        RandomRBF rbf = new RandomRBF(numCenters, sigma);

        // Test the adjusted RBF
        for (double x = -2; x <= 2; x += 0.1) {
            double y = rbf.computeAdjusted(x);
            System.out.printf("f(%5.2f) = %5.2f%n", x, y);
        }

        // Verify f(0) is approximately 0
        double zeroCheck = rbf.computeAdjusted(0);
        System.out.printf("f(0) = %5.2f (should be 0)%n", zeroCheck);
    }

    /**
     * Computes the Radial Basis Function (RBF) value for the given input using pre-initialized
     * centers, amplitudes, and width parameter (sigma).
     *
     * @param x the input value for which the RBF value is to be computed
     * @return the computed RBF value for the given input
     */
    public double compute(double x) {
        double result = 0.0;
        for (int i = 0; i < centers.length; i++) {
            double distanceSquared = Math.pow(x - centers[i], 2);
            result += amplitudes[i] * Math.exp(-distanceSquared / (2 * Math.pow(sigma, 2)));
        }
        return result;
    }

    /**
     * Computes the adjusted value of the Radial Basis Function (RBF) for the given input.
     * The adjustment ensures that the RBF value is relative to its value at x = 0.
     *
     * @param x the input value for which the adjusted RBF value is to be computed
     * @return the adjusted RBF value for the given input
     */
    public double computeAdjusted(double x) {
        double fAtZero = compute(0); // Compute the value at x = 0
        return compute(x) - fAtZero; // Subtract f(0) to adjust the function
    }
}

