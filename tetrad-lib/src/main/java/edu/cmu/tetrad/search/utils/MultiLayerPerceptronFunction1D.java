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
 * This class implements a 1-dimensional Multi-Layer Perceptron (MLP) function. It consists of a single hidden layer and
 * scales the input to introduce variability or "bumpiness" as needed. The activation function for the hidden layer and
 * weights/biases for the neural network are initialized during construction.
 */
public class MultiLayerPerceptronFunction1D {
    private final double[][] W1; // Weights for input to hidden layer
    private final double[] b1;  // Biases for hidden layer
    private final double[] W2;  // Weights for hidden to output layer
    private final double b2;    // Bias for output layer
    private final Function<Double, Double> activation; // Activation function
    private final double inputScale; // Input scaling for bumpiness

    /**
     * Constructor to initialize a random function.
     *
     * @param hiddenDimension Number of neurons in the hidden layer.
     * @param inputScale      Scaling factor for the input to create bumpiness.
     * @param activation      Activation function (e.g., Math::sin or Math::tanh).
     * @param seed            Random seed for reproducibility.
     */
    public MultiLayerPerceptronFunction1D(int hiddenDimension, double inputScale, Function<Double, Double> activation, long seed) {
        Random random;

        if (seed == -1L) {
            random = new Random();
        } else {
            random = new Random(seed);
        }

        this.W1 = new double[hiddenDimension][1];
        this.b1 = new double[hiddenDimension];
        this.W2 = new double[hiddenDimension];
        this.b2 = random.nextDouble() * 2 - 1; // Random value in [-1, 1]
        this.activation = activation;
        this.inputScale = inputScale;

        // Initialize weights and biases randomly
        for (int i = 0; i < hiddenDimension; i++) {
            this.W1[i][0] = random.nextGaussian(); // Gaussian weights
            this.b1[i] = random.nextGaussian();   // Gaussian biases
            this.W2[i] = random.nextGaussian();   // Gaussian weights
        }
    }

    /**
     * The entry point of the application that demonstrates the usage of the MultiLayerPerceptronFunction1D class by
     * defining a random function with specific parameters and evaluating it over a range of inputs.
     *
     * @param args Command-line arguments passed to the program.
     */
    public static void main(String[] args) {
        // Define a random function with 20 hidden neurons, sine activation, and high bumpiness
        MultiLayerPerceptronFunction1D randomFunction = new MultiLayerPerceptronFunction1D(
                20, // Number of hidden neurons
                10.0, Math::sin, // Activation function
                // Input scale for bumpiness
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

