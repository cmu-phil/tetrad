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
import ai.djl.training.initializer.XavierInitializer;
import ai.djl.translate.NoopTranslator;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import edu.cmu.tetrad.data.DataTransforms;

import java.util.List;

public class MultiLayerPerceptronDjl {

    private final SequentialBlock net;
    private final float inputScale;
    private final NDManager manager;

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

    public static void main(String[] args) {
        try (NDManager manager = NDManager.newBaseManager()) {
            // Define the MLP: input dimension 3, hidden layers [10, 10], ReLU activation, and continuous output
            MultiLayerPerceptronDjl mlp = new MultiLayerPerceptronDjl(3, List.of(10, 10), "continuous", 1.0f);

            // Create dummy input data
            NDArray input = manager.create(new float[]{1.0f, 2.0f, 3.0f});

            // Perform forward pass
            NDArray output = mlp.forward(manager, input);
            System.out.println("Output: " + output);
        } catch (TranslateException e) {
            e.printStackTrace();
        }
    }

    public NDArray forward(NDManager manager, NDArray input) throws TranslateException {
        // Scale the input if needed
        if (inputScale != 1.0f) {
            input = input.mul(inputScale);
        }

        ParameterStore parameterStore = new ParameterStore(manager, false);

        // Forward pass through the network
        return net.forward(parameterStore, new NDList(input), false).singletonOrThrow();
    }

    public NDManager getManager() {
        return manager;
    }
}



