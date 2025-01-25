package edu.cmu.tetrad.search.utils;

import java.util.Random;
import java.util.stream.DoubleStream;

/**
 * The RandomFourier class generates a random Fourier series with specified cosine and sine amplitudes and frequency
 * terms. The series is defined as:
 * <p>
 * f(x) = Σ [a_i * cos(b_i * x) + c_i * sin(b_i * x)]
 * <p>
 * where: - a_i and c_i are randomly chosen amplitudes in the range [-2, 2], - b_i are randomly chosen frequencies
 * scaled by a given factor.
 * <p>
 * This class also provides functionality to adjust the series so that its value at x = 0 is zero.
 */
public class RandomFourier {
    private final double[] cosAmplitudes; // Coefficients for cosines
    private final double[] sinAmplitudes; // Coefficients for sines
    private final double[] frequencies;  // Frequencies for each term
    private final int numTerms;

    /**
     * Initializes a RandomFourier instance with the specified number of terms and frequency scale.
     *
     * @param numTerms       The number of terms in the Fourier series.
     * @param frequencyScale The scale factor applied to the randomly generated frequencies.
     */
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

    /**
     * The main method demonstrates the usage of the RandomFourier class. It creates an instance of RandomFourier,
     * evaluates the adjusted Fourier series over a given range of x values, and verifies that the series is adjusted
     * such that its value at x = 0 is approximately zero.
     *
     * @param args Command-line arguments (not used in this implementation).
     */
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

    /**
     * Computes the value of the Fourier series at a given input x.
     *
     * @param x The input value for which the Fourier series is evaluated.
     * @return The calculated result of the Fourier series at the given input x.
     */
    public double compute(double x) {
        double result = 0.0;
        for (int i = 0; i < numTerms; i++) {
            result += cosAmplitudes[i] * Math.cos(frequencies[i] * x)
                      + sinAmplitudes[i] * Math.sin(frequencies[i] * x);
        }
        return result;
    }

    /**
     * Computes the adjusted value of the Fourier series at the given input x. The adjustment ensures that the computed
     * value is shifted such that the Fourier series value at x = 0 is zero.
     *
     * @param x The input value for which the adjusted Fourier series is evaluated.
     * @return The adjusted result of the Fourier series at the given input x.
     */
    public double computeAdjusted(double x) {
        double fAtZero = compute(0); // Compute the value at x = 0
        return compute(x) - fAtZero; // Subtract f(0) to adjust the function
    }
}
