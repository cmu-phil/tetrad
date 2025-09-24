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

import ai.djl.Device;
import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.Blocks;
import ai.djl.nn.Parameter;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.training.ParameterStore;
import ai.djl.training.initializer.NormalInitializer;
import ai.djl.translate.NoopTranslator;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;

import java.util.List;

/**
 * The MultiLayerPerceptronDjl class provides a customizable implementation of a Multi-Layer Perceptron (MLP) for tasks
 * like regression or classification using the Deep Java Library (DJL). This class allows the user to define the network
 * architecture, including the input dimension, hidden layers, and type of output.
 */
public class MultiLayerPerceptronDjl {

    private final SequentialBlock net;
    private final float inputScale;
    private final NDManager manager;

    /**
     * Constructs a MultiLayerPerceptronDjl object with the specified input dimension, hidden layers, variable type, and
     * input scaling factor. This builds the architecture of a neural network based on provided configurations such as
     * the number of input features, hidden layer specifications, and the output type (e.g., continuous, multinomial, or
     * binary).
     *
     * @param inputDim     the number of input features or dimensions.
     * @param hiddenLayers a list of integers defining the number of neurons in each hidden layer.
     * @param variableType the type of prediction target, such as "continuous", "binary", or "multinomial". For
     *                     multinomial, it should specify the number of categories as "multinomial,numCategories".
     * @param inputScale   a scaling factor applied to the input data.
     */
    public MultiLayerPerceptronDjl(int inputDim, List<Integer> hiddenLayers,
                                   String variableType, float inputScale) {
        this.inputScale = inputScale;
        this.manager = NDManager.newBaseManager(Device.cpu());
        try (Model model = Model.newInstance("lin-reg")) {
            NDArray x = manager.randomUniform(0, 1, new Shape(2, inputDim));

            // Define the network architecture
            net = new SequentialBlock();
            net.add(Blocks.batchFlattenBlock(inputDim));
            net.add(Linear.builder().setUnits(inputDim).build());  // Input layer

            for (Integer hiddenLayer : hiddenLayers) {
                net.add(Linear.builder().setUnits(hiddenLayer).build());
                net.add(Activation::relu);
            }

            // Output layer depends on variable type
            if (variableType.equals("continuous")) {
                net.add(Linear.builder().setUnits(1).build());  // Single output for regression
            } else if (variableType.startsWith("multinomial")) {
                int numCategories = Integer.parseInt(variableType.split(",")[1].trim());
                net.add(Linear.builder().setUnits(numCategories).build());  // Multiclass classification
            } else if (variableType.equals("binary")) {
                net.add(Linear.builder().setUnits(1).build());  // Binary classification (logistic regression)
            }

//            net.setInitializer(new XavierInitializer(), Parameter.Type.WEIGHT);
            net.setInitializer(new NormalInitializer((float) Math.sqrt(2.0)), Parameter.Type.WEIGHT);

            net.initialize(manager, DataType.FLOAT32, x.getShape());

            model.setBlock(net);
        }

        Translator<NDList, NDList> translator = new NoopTranslator();
    }

    /**
     * Computes the forward pass of the neural network for a given input.
     *
     * @param manager the {@code NDManager} used to manage the computational resources.
     * @param input   the input {@code NDArray} to process through the neural network.
     * @return the resulting {@code NDArray} after the forward pass through the network.
     * @throws TranslateException if there is an issue during computation or data translation.
     */
    public NDArray forward(NDManager manager, NDArray input) throws TranslateException {
        // Scale the input if needed
        if (inputScale != 1.0f) {
            input = input.mul(inputScale);
        }

        ParameterStore parameterStore = new ParameterStore(manager, false);

        // Forward pass through the network
        return net.forward(parameterStore, new NDList(input), false).singletonOrThrow();
    }

    /**
     * Returns the NDManager used for managing computational resources.
     *
     * @return the NDManager instance.
     */
    public NDManager getManager() {
        return manager;
    }
}




