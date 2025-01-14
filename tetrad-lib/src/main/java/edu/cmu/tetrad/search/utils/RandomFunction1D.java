package edu.cmu.tetrad.search.utils;

import java.util.Random;
import java.util.function.Function;

public class RandomFunction1D {

    private final double[][] W1; // Weights for input to hidden layer
    private final double[] b1;  // Biases for hidden layer
    private final double[] W2;  // Weights for hidden to output layer
    private final double b2;    // Bias for output layer
    private final Function<Double, Double> activation; // Activation function
    private final double inputScale; // Input scaling for bumpiness

    /**
     * Constructor to initialize a random function.
     *
     * @param hiddenDim  Number of neurons in the hidden layer.
     * @param activation Activation function (e.g., Math::sin or Math::tanh).
     * @param inputScale Scaling factor for the input to create bumpiness.
     * @param seed       Random seed for reproducibility.
     */
    public RandomFunction1D(int hiddenDim, Function<Double, Double> activation, double inputScale, long seed) {
        Random random;

        if (seed == -1L) {
            random = new Random();
        } else {
            random = new Random(seed);
        }

        this.W1 = new double[hiddenDim][1];
        this.b1 = new double[hiddenDim];
        this.W2 = new double[hiddenDim];
        this.b2 = random.nextDouble() * 2 - 1; // Random value in [-1, 1]
        this.activation = activation;
        this.inputScale = inputScale;

        // Initialize weights and biases randomly
        for (int i = 0; i < hiddenDim; i++) {
            this.W1[i][0] = random.nextGaussian(); // Gaussian weights
            this.b1[i] = random.nextGaussian();   // Gaussian biases
            this.W2[i] = random.nextGaussian();   // Gaussian weights
        }
    }

    public static void main(String[] args) {
        // Define a random function with 20 hidden neurons, sine activation, and high bumpiness
        RandomFunction1D randomFunction = new RandomFunction1D(
                20, // Number of hidden neurons
                Math::sin, // Activation function
                10.0, // Input scale for bumpiness
                42 // Random seed
        );

        // Evaluate and print the random function for some inputs
        for (double x = -2.0; x <= 2.0; x += 0.1) {
            System.out.printf("f(%.2f) = %.5f%n", x, randomFunction.evaluate(x));
        }
    }

    /**
     * Evaluates the random function for a given input.
     *
     * @param x Input value in R.
     * @return Output value in R.
     */
    public double evaluate(double x) {
        double scaledInput = x * inputScale; // Scale the input
        double[] hiddenLayer = new double[W1.length];

        // Compute hidden layer activations
        for (int i = 0; i < W1.length; i++) {
            double z = W1[i][0] * scaledInput + b1[i];
            hiddenLayer[i] = activation.apply(z);
        }

        // Compute output layer
        double output = b2;
        for (int i = 0; i < W1.length; i++) {
            output += W2[i] * hiddenLayer[i];
        }

        return output;
    }
}
