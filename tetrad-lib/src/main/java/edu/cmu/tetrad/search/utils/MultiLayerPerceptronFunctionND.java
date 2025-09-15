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

import java.util.Random;
import java.util.function.Function;

/**
 * Represents a random Multi-layer Perceptron (MLP) function from R^n to R.
 */
public class MultiLayerPerceptronFunctionND {
    private final double[][] W1; // Weights for input to hidden layer
    private final double[] b1;  // Biases for hidden layer
    private final double[] W2;  // Weights for hidden to output layer
    private final double b2;    // Bias for output layer
    private final Function<Double, Double> activation; // Activation function
    private final double inputScale; // Input scaling for bumpiness

    /**
     * Constructor to initialize a random function.
     *
     * @param inputDim        Number of input dimensions (R^n).
     * @param hiddenDimension Number of neurons in the hidden layer.
     * @param activation      Activation function (e.g., Math::sin or Math::tanh).
     * @param inputScale      Scaling factor for the input to create bumpiness.
     * @param seed            Random seed for reproducibility.
     */
    public MultiLayerPerceptronFunctionND(int inputDim, int hiddenDimension, Function<Double, Double> activation, double inputScale, long seed) {
        Random random = new Random(seed);

        this.W1 = new double[hiddenDimension][inputDim];
        this.b1 = new double[hiddenDimension];
        this.W2 = new double[hiddenDimension];
        this.b2 = random.nextDouble() * 2 - 1; // Random value in [-1, 1]
        this.activation = activation;
        this.inputScale = inputScale;

        // Initialize weights and biases randomly
        for (int i = 0; i < hiddenDimension; i++) {
            for (int j = 0; j < inputDim; j++) {
                this.W1[i][j] = random.nextGaussian(); // Gaussian weights
            }
            this.b1[i] = random.nextGaussian();       // Gaussian biases
            this.W2[i] = random.nextGaussian();       // Gaussian weights
        }
    }

    /**
     * The main method demonstrating the creation and evaluation of a multi-layer perceptron function with random
     * initialization and specific parameters. It defines the function, evaluates the function on given sample inputs,
     * and prints the results to the console.
     *
     * @param args Command-line arguments passed to the program.
     */
    public static void main(String[] args) {
        // Define a random function with 20 hidden neurons, sine activation, and high bumpiness
        MultiLayerPerceptronFunctionND randomFunction = new MultiLayerPerceptronFunctionND(
                3, // Input dimension (R^3 -> R)
                20, // Number of hidden neurons
                Math::tanh, // Activation function
                10.0, // Input scale for bumpiness
                42 // Random seed
        );

        // Evaluate and print the random function for some sample inputs
        double[][] sampleInputs = {
                {1.0, 0.5, -1.2},
                {0.2, -0.3, 0.8},
                {-1.0, 1.5, 0.0},
                {0.0, 0.0, 0.0}
        };

        for (double[] input : sampleInputs) {
            double output = randomFunction.evaluate(input);
            System.out.printf("f(%s) = %.5f%n", java.util.Arrays.toString(input), output);
        }
    }

    /**
     * Evaluates the random function for a given input vector.
     *
     * @param x Input vector in R^n.
     * @return Output value in R.
     */
    public double evaluate(double[] x) {
        if (x.length != W1[0].length) {
            throw new IllegalArgumentException("Input vector dimension does not match the expected dimension.");
        }

        double[] scaledInput = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            scaledInput[i] = x[i] * inputScale; // Scale the input
        }

        double[] hiddenLayer = new double[W1.length];

        // Compute hidden layer activations
        for (int i = 0; i < W1.length; i++) {
            double z = b1[i];
            for (int j = 0; j < W1[i].length; j++) {
                z += W1[i][j] * scaledInput[j];
            }
            hiddenLayer[i] = activation.apply(z);
        }

        // Compute output layer
        double output = b2;
        for (int i = 0; i < W1.length; i++) {
            output += W2[i] * hiddenLayer[i];
        }

        return output;
    }

    /**
     * Evaluates the adjusted output of the function for a given input vector. The adjustment involves subtracting the
     * output when the input is a zero-filled vector.
     *
     * @param doubles Input vector in R^n.
     * @return The adjusted output value in R, calculated by subtracting the output of the zero-filled vector from the
     * output of the provided input vector.
     */
    public Double evaluateAdjusted(double[] doubles) {
        double zero = evaluate(new double[doubles.length]);
        return evaluate(doubles) - zero;
    }
}

