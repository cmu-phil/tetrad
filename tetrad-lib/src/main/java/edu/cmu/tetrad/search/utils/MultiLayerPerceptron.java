package edu.cmu.tetrad.search.utils;

import java.util.Random;
import java.util.function.Function;

public class MultiLayerPerceptron {
    private final double[][][] weights; // Weights for all layers
    private final double[][] biases;   // Biases for all layers
    private final Function<Double, Double> activation; // Activation function
    private final double inputScale;   // Input scaling for bumpiness

    /**
     * Constructor to initialize a random multi-layer perceptron.
     *
     * @param inputDim   Number of input dimensions (R^n).
     * @param hiddenLayers Array specifying the number of neurons in each hidden layer.
     * @param activation Activation function (e.g., Math::tanh or Math::sin).
     * @param inputScale Scaling factor for the input to create bumpiness.
     * @param seed       Random seed for reproducibility.
     */
    public MultiLayerPerceptron(int inputDim, int[] hiddenLayers, Function<Double, Double> activation, double inputScale, long seed) {
        Random random = new Random(seed);

        int numLayers = hiddenLayers.length;
        this.weights = new double[numLayers + 1][][]; // Includes input-to-hidden and hidden-to-output
        this.biases = new double[numLayers + 1][];    // Includes all layer biases
        this.activation = activation;
        this.inputScale = inputScale;

        // Initialize weights and biases for input-to-hidden layers
        int prevLayerSize = inputDim;
        for (int layer = 0; layer < numLayers; layer++) {
            int currentLayerSize = hiddenLayers[layer];
            weights[layer] = new double[currentLayerSize][prevLayerSize];
            biases[layer] = new double[currentLayerSize];
            for (int i = 0; i < currentLayerSize; i++) {
                for (int j = 0; j < prevLayerSize; j++) {
                    weights[layer][i][j] = random.nextGaussian();
                }
                biases[layer][i] = random.nextGaussian();
            }
            prevLayerSize = currentLayerSize;
        }

        // Initialize weights and biases for hidden-to-output layer
        weights[numLayers] = new double[1][prevLayerSize];
        biases[numLayers] = new double[1];
        for (int j = 0; j < prevLayerSize; j++) {
            weights[numLayers][0][j] = random.nextGaussian();
        }
        biases[numLayers][0] = random.nextGaussian();
    }

    /**
     * Evaluates the MLP for a given input vector.
     *
     * @param x Input vector in R^n.
     * @return Output value in R.
     */
    public double evaluate(double[] x) {
        double[] layerInput = scaleInput(x);

        for (int layer = 0; layer < weights.length; layer++) {
            double[] layerOutput = new double[weights[layer].length];
            for (int i = 0; i < weights[layer].length; i++) {
                double z = biases[layer][i];
                for (int j = 0; j < layerInput.length; j++) {
                    z += weights[layer][i][j] * layerInput[j];
                }
                layerOutput[i] = layer == weights.length - 1 ? z : activation.apply(z); // No activation in output layer
            }
            layerInput = layerOutput;
        }

        return layerInput[0]; // Single output
    }

    /**
     * Scales the input vector.
     *
     * @param x Input vector.
     * @return Scaled input vector.
     */
    private double[] scaleInput(double[] x) {
        double[] scaledInput = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            scaledInput[i] = x[i] * inputScale;
        }
        return scaledInput;
    }

    public static void main(String[] args) {
        // Example usage
        MultiLayerPerceptron mlp = new MultiLayerPerceptron(
                3, // Input dimension (R^3 -> R)
                new int[]{10, 15, 5}, // Three hidden layers with specified neurons
                Math::tanh, // Activation function
                10.0, // Input scale for bumpiness
                42 // Random seed
        );

        double[][] sampleInputs = {
                {1.0, 0.5, -1.2},
                {0.2, -0.3, 0.8},
                {-1.0, 1.5, 0.0},
                {0.0, 0.0, 0.0}
        };

        for (double[] input : sampleInputs) {
            double output = mlp.evaluate(input);
            System.out.printf("f(%s) = %.5f%n", java.util.Arrays.toString(input), output);
        }
    }
}
