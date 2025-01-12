package edu.cmu.tetrad.search.utils;

import java.util.Random;
import java.util.stream.DoubleStream;

public class RandomFourier {
    private final double[] cosAmplitudes; // Coefficients for cosines
    private final double[] sinAmplitudes; // Coefficients for sines
    private final double[] frequencies;  // Frequencies for each term
    private final int numTerms;

    // Constructor to initialize the amplitudes and frequencies
    public RandomFourier(int numTerms, double frequencyScale) {
        this.numTerms = numTerms;
        this.cosAmplitudes = new double[numTerms];
        this.sinAmplitudes = new double[numTerms];
        this.frequencies = new double[numTerms];

        Random random = new Random();
        for (int i = 0; i < numTerms; i++) {
            cosAmplitudes[i] = -2 + 4 * random.nextDouble(); // Random values in range [-2, 2]
            sinAmplitudes[i] = -2 + 4 * random.nextDouble(); // Random values in range [-2, 2]
            frequencies[i] = frequencyScale * (0.5 + random.nextDouble()); // Random frequencies > 0
        }
    }

    // Main method for testing
    public static void main(String[] args) {
        int numTerms = 5;
        double frequencyScale = 1.0;

        // Create a RandomFourier instance
        RandomFourier fourier = new RandomFourier(numTerms, frequencyScale);

        // Test the adjusted Fourier function
        DoubleStream.iterate(-2, x -> x <= 2, x -> x + 0.1).forEach(x -> {
            double y = fourier.computeAdjusted(x);
            System.out.printf("f(%5.2f) = %5.2f%n", x, y);
        });

        // Verify f(0) is approximately 0
        double zeroCheck = fourier.computeAdjusted(0);
        System.out.printf("f(0) = %5.2f (should be 0)%n", zeroCheck);
    }

    // Method to compute the Fourier value at a given x
    public double compute(double x) {
        double result = 0.0;
        for (int i = 0; i < numTerms; i++) {
            result += cosAmplitudes[i] * Math.cos(frequencies[i] * x)
                      + sinAmplitudes[i] * Math.sin(frequencies[i] * x);
        }
        return result;
    }

    // Method to compute the adjusted Fourier value, ensuring f(0) = 0
    public double computeAdjusted(double x) {
        double fAtZero = compute(0); // Compute the value at x = 0
        return compute(x) - fAtZero; // Subtract f(0) to adjust the function
    }
}
