package edu.cmu.tetrad.search.utils;

import java.util.Random;

public class RandomRBF {
    private final double[] centers;
    private final double[] amplitudes;
    private final double sigma;

    // Constructor to initialize centers, amplitudes, and sigma
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

    // Main method for testing
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

    // Method to compute the RBF value at a given x
    public double compute(double x) {
        double result = 0.0;
        for (int i = 0; i < centers.length; i++) {
            double distanceSquared = Math.pow(x - centers[i], 2);
            result += amplitudes[i] * Math.exp(-distanceSquared / (2 * Math.pow(sigma, 2)));
        }
        return result;
    }

    // Method to compute the adjusted RBF value, ensuring f(0) = 0
    public double computeAdjusted(double x) {
        double fAtZero = compute(0); // Compute the value at x = 0
        return compute(x) - fAtZero; // Subtract f(0) to adjust the function
    }
}

