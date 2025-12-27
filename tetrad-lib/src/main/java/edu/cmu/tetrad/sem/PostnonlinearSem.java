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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.MultiLayerPerceptronFunctionND;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a Post-nonlinear Causal Model (Zhang and Hyvarinen, 2009, 2012).
 * <p>
 * The form of the recursive model is
 * X_i = f_{2,i}( f_{1,i}(Pa(X_i)) + N_i ),
 * where N_i is an exogenous noise term independent across variables.
 * <p>
 * Zhang, K., &amp; Hyvarinen, A. (2012). On the identifiability of the post-nonlinear causal model. arXiv preprint
 * arXiv:1205.2599.
 * <p>
 * Chu, T., Glymour, C., &amp; Ridgeway, G. (2008). Search for Additive Nonlinear Time Series Causal Models. Journal of
 * Machine Learning Research, 9(5).
 * <p>
 * Bühlmann, P., Peters, J., &amp; Ernest, J. (2014). "CAM: Causal Additive Models, high-dimensional order search and
 * penalized regression". The Annals of Statistics.
 * <p>
 * Peters, J., Mooij, J. M., Janzing, D., &amp; Schölkopf, B. (2014). "Causal Discovery with Continuous Additive Noise
 * Models". Journal of Machine Learning Research.
 * <p>
 * Hastie, T., &amp; Tibshirani, R. (1986). "Generalized Additive Models".
 * <p>
 * Hyvarinen, A., &amp; Pajunen, P. (1999). "Nonlinear Independent Component Analysis: Existence and Uniqueness
 * Results"
 */
public class PostnonlinearSem {

    /**
     * The directed acyclic graph (DAG) that defines the causal relationships among variables within the simulation.
     * This graph serves as the primary structure for defining causal interactions and dependencies between variables.
     * It must be acyclic for the simulation to be valid.
     * <p>
     * The {@code graph} is used to generate synthetic data under a post-nonlinear functional causal model, where
     * each variable is computed as X_i = f_{2,i}( f_{1,i}(Pa(X_i)) + N_i ), with N_i an exogenous noise term.
     */
    private final Graph graph;

    /**
     * Represents the number of samples to be generated in the post-nonlinear simulation. This variable determines how
     * many synthetic data points will be created based on the causal relationships in the provided DAG.
     * <p>
     * Constraints: Must be a positive integer.
     */
    private final int numSamples;

    /**
     * Represents the noise distribution used in the simulation framework. This distribution is used to
     * introduce randomness into the simulated data, reflecting inherent noise in causal relationships. The noise is
     * applied during data generation, ensuring variability and realism in the synthetic dataset. Exogenous variables
     * are assumed to be independent and identically distributed (i.i.d.) with the specified noise distribution.
     */
    private final RealDistribution noiseDistribution;

    /**
     * Represents the number of hidden neurons in a multilayer perceptron (MLP) function. This variable determines the
     * dimensionality of the hidden layer, which can affect the model's capacity to approximate complex functions in the
     * causal simulation.
     */
    private final int hiddenDimension;

    /**
     * A scaling factor applied to the input data in the simulation, used to introduce variability and adjust the
     * "bumpiness" of the generated causal relationships. This parameter determines how sensitive the inputs are when
     * passed through the data generation process.
     * <p>
     * It plays a critical role in shaping the nonlinearity and complexity of the causal mechanisms applied to the input
     * variables, influencing the statistical properties of the generated data.
     */
    private final double inputScale;

    /**
     * The activation function used in the post-nonlinear causal model to introduce nonlinearity to the relationships
     * between variables. This typically applies a mathematical transformation to the data, and by default, it is set to
     * the hyperbolic tangent function (Math::tanh).
     * <p>
     * This function is utilized in the data generation process, where the causal dependencies among variables are
     * influenced by the nonlinear transformation applied by this function.
     * <p>
     * Users can customize the activation function to implement alternative nonlinearities by providing their own
     * implementation through {@link #setActivationFunction(Function)}.
     */
    private Function<Double, Double> activationFunction = Math::tanh;

    /**
     * Random source used to generate independent function parameters (MLPs) per variable and per layer (f1, f2).
     * Using a single Random instance here avoids hardcoding a seed and ensures different mechanisms per node.
     */
    private final Random functionRng;

    /**
     * Constructs a PostnonlinearCausalModel object. This model generates synthetic data based on a directed acyclic
     * graph (DAG) with causal relationships, utilizing post-nonlinear causal mechanisms of the form
     * X_i = f_{2,i}( f_{1,i}(Pa(X_i)) + N_i ).
     *
     * @param graph             The directed acyclic graph (DAG) containing the causal structure for the model. Must be
     *                          acyclic; otherwise, an exception will be thrown.
     * @param numSamples        The number of samples to generate for the synthetic data. Must be positive.
     * @param noiseDistribution The distribution from which noise values are generated. Often a standard distribution,
     *                          such as Gaussian, but can be user-defined.
     * @param hiddenDimension   The dimensionality of the hidden layer in the MLPs approximating f1 and f2.
     * @param inputScale        A scaling factor applied to the input variables before applying post-nonlinear
     *                          operations.
     * @throws IllegalArgumentException If the provided graph is not acyclic, the number of samples is less than one, or
     *                                  rescaleMin is greater than rescaleMax.
     */
    public PostnonlinearSem(Graph graph, int numSamples, RealDistribution noiseDistribution,
                            int hiddenDimension, double inputScale) {

        if (!graph.paths().isAcyclic()) {
            throw new IllegalArgumentException("Graph contains cycles.");
        }

        if (numSamples < 1) {
            throw new IllegalArgumentException("Number of samples must be positive.");
        }

        this.graph = graph;
        this.numSamples = numSamples;
        this.noiseDistribution = noiseDistribution;
        this.hiddenDimension = hiddenDimension;
        this.inputScale = inputScale;

        // Use a fresh Random instance so each model instance gets its own independent mechanisms.
        this.functionRng = new Random();
    }

    /**
     * Generates synthetic data based on the directed acyclic graph (DAG) with post-nonlinear causal mechanisms.
     * The data generation process involves simulating parent-child relationships in the graph,
     * applying noise, optional rescaling, and then applying an invertible post-nonlinear distortion.
     *
     * @return A DataSet object containing the generated synthetic data, with samples and variables defined by the
     * structure of the provided graph and simulation parameters.
     */
    public DataSet generateData() {
        DataSet data = new BoxDataSet(new DoubleDataBox(numSamples, graph.getNodes().size()), graph.getNodes());

        List<Node> nodes = graph.getNodes();
        Map<Node, Integer> nodeToIndex = IntStream.range(0, nodes.size())
                .boxed()
                .collect(Collectors.toMap(nodes::get, i -> i));

        List<Node> validOrder = graph.paths().getValidOrder(graph.getNodes(), true);

        // STEP 1: f1(Pa) + N, as you already had it
        for (Node node : validOrder) {
            List<Node> parents = graph.getParents(node);
            int colIndex = nodeToIndex.get(node);

            if (parents.isEmpty()) {
                // Root node: inner stage = N_i
                for (int sample = 0; sample < numSamples; sample++) {
                    double value = noiseDistribution.sample();
                    data.setDouble(sample, colIndex, value);
                }
            } else {
                int f1Seed = functionRng.nextInt();

                Function<double[], Double> f1 = new MultiLayerPerceptronFunctionND(
                        parents.size(),           // Input dimension
                        this.hiddenDimension,     // Hidden neurons
                        this.activationFunction,  // Activation
                        this.inputScale,          // Input scale
                        f1Seed                    // Seed
                )::evaluateAdjusted;

                for (int sample = 0; sample < numSamples; sample++) {
                    int finalSample = sample;
                    double[] parentValues = parents.stream()
                            .mapToDouble(parent -> data.getDouble(finalSample, nodeToIndex.get(parent)))
                            .toArray();

                    double value = f1.apply(parentValues) + noiseDistribution.sample();
                    data.setDouble(sample, colIndex, value);
                }
            }
        }

        // STEP 2: Apply invertible post-nonlinear distortion f2 to each variable.
        // f2_i(x) = a_i * g(inputScale * x) + b_i, with a_i > 0 and g strictly monotone.
        for (Node node : validOrder) {
            int colIndex = nodeToIndex.get(node);

            // Draw parameters for this node's f2
            double a = 0.5 + Math.abs(functionRng.nextGaussian()); // ensure strictly positive
            double b = functionRng.nextGaussian();

            // g is the activationFunction (default  cubic-perturbation-of-identity), assumed strictly monotone

            double c = Math.abs(functionRng.nextGaussian()) * 0.3; // e.g. 0..~1
            Function<Double, Double> g = x -> x + c * x * x * x;   // strictly increasing
            Function<Double, Double> f2 = x -> a * g.apply(x) + b;

//            double outerScale = 0.5; // new param, default
//            Function<Double, Double> f2 = x -> a * activationFunction.apply(outerScale * x) + b;

//            Function<Double, Double> f2 = x -> a * Math.tanh(outerScale * x) + b;

            for (int sample = 0; sample < numSamples; sample++) {
                double value = data.getDouble(sample, colIndex);
                value = f2.apply(value);
                data.setDouble(sample, colIndex, value);
            }
        }

        return data;
    }

    /**
     * Sets the activation function used in the model. The activation function is a mathematical function that
     * transforms the input and can influence the relationships and behavior within the causal model.
     *
     * @param activationFunction The function to be used as the activation function. It must be a mapping from a Double
     *                           input to a Double output, representing the non-linear transformation applied within the
     *                           model.
     */
    public void setActivationFunction(Function<Double, Double> activationFunction) {
        this.activationFunction = activationFunction;
    }
}