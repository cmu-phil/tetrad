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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a nonlinear additive noise causal model (Hoyer et al., 2008).
 * <p>
 * The form of the recursive model is Xi = fi(Pa(Xi)) + Ni
 * <p>
 * Hoyer, P., Janzing, D., Mooij, J. M., Peters, J., &amp; SchÃ¶lkopf, B. (2008). Nonlinear causal discovery with
 * additive noise models. Advances in neural information processing systems, 21.
 * <p>
 * Zhang, K., &amp; HyvÃ¤rinen, A. (2009). Causality discovery with additive disturbances: An information-theoretical
 * perspective. In Machine Learning and Knowledge Discovery in Databases: European Conference, ECML PKDD 2009, Bled,
 * Slovenia, September 7-11, 2009, Proceedings, Part II 20 (pp. 570-585). Springer Berlin Heidelberg.
 * <p>
 * Zhang, K., &amp; Hyvarinen, A. (2012). On the identifiability of the post-nonlinear causal model. arXiv preprint
 * arXiv:1205.2599.
 * <p>
 * Chu, T., Glymour, C., &amp; Ridgeway, G. (2008). Search for Additive Nonlinear Time Series Causal Models. Journal of
 * Machine Learning Research, 9(5).
 * <p>
 * BÃ¼hlmann, P., Peters, J., &amp; Ernest, J. (2014). "CAM: Causal Additive Models, high-dimensional order search and
 * penalized regression". The Annals of Statistics.
 * <p>
 * Peters, J., Mooij, J. M., Janzing, D., &amp; SchÃ¶lkopf, B. (2014). "Causal Discovery with Continuous Additive Noise
 * Models". Journal of Machine Learning Research.
 * <p>
 * Hastie, T., &amp; Tibshirani, R. (1986). "Generalized Additive Models".
 * <p>
 * Hyvarinen, A., &amp; Pajunen, P. (1999). "Nonlinear Independent Component Analysis: Existence and Uniqueness
 * Results"
 */
public class NonlinearAdditiveNoiseModel {
    /**
     * The directed acyclic graph (DAG) that defines the causal relationships among variables within the simulation.
     * This graph serves as the primary structure for defining causal interactions and dependencies between variables.
     * It must be acyclic for the simulation to be valid.
     * <p>
     * The `graph` is used to generate synthetic data under the assumption of additive noise models, where causal
     * mechanisms are modeled as functions of their parent variables in the graph, with noise added to capture
     * non-deterministic influences. The graph's structure is critical in determining these causal mechanisms and the
     * relationships among variables.
     */
    private final Graph graph;
    /**
     * Represents the number of samples to be generated in the additive noise simulation. This variable determines how
     * many synthetic data points will be created based on the causal relationships in the provided directed acyclic
     * graph (DAG).
     * <p>
     * Constraints: Must be a positive integer.
     */
    private final int numSamples;
    /**
     * Represents the noise distribution used in the additive simulation framework. This distribution is used to
     * introduce randomness into the simulated data, reflecting inherent noise in causal relationships. The noise is
     * applied during data generation, ensuring variability and realism in the synthetic dataset. Exogenous variables
     * are assumed to be independent and identically distributed (i.i.d) with the specified noise distribution.
     */
    private final RealDistribution noiseDistribution;
    /**
     * The lower bound used for rescaling data during the simulation process. This value is used to ensure that the
     * synthetic data is scaled within a specific range before further processing or transformations.
     */
    private final double rescaleMin;
    /**
     * The upper bound used for rescaling data during the simulation process. This value determines the maximum scale
     * applied to normalized data, ensuring it fits within the specified range during synthetic data generation.
     */
    private final double rescaleMax;
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
     * Represents the non-linear activation function used in the model for applying transformations to the data during
     * the simulation. This function operates on Double inputs and outputs, and defines a mathematical operation that
     * introduces non-linearity to the model.
     * <p>
     * The default activation function is set to `Math::tanh`.
     * <p>
     * This field can be customized by using the provided setter method to apply other non-linear transformations based
     * on the requirements of the model simulation.
     */
    private Function<Double, Double> activationFunction = Math::tanh;

    /**
     * Constructs a nonlinear additive noise model based on a directed acyclic graph (DAG). This model is used to
     * generate synthetic data where the relationships in the graph are affected by additive noise and potentially
     * undergo nonlinear transformations.
     *
     * @param graph             The directed acyclic graph (DAG) representing the underlying causal structure.
     * @param numSamples        The number of data samples to generate. Must be positive.
     * @param noiseDistribution The distribution used to sample additive noise for each variable.
     * @param rescaleMin        The minimum value of the range for rescaling the generated data. Must be less than or
     *                          equal to rescaleMax.
     * @param rescaleMax        The maximum value of the range for rescaling the generated data. Must be greater than or
     *                          equal to rescaleMin.
     * @param hiddenDimension   The dimensionality of hidden layers or transformations used in data generation.
     * @param inputScale        A scaling factor applied to input data before applying transformations or noise.
     * @throws IllegalArgumentException If the input graph contains cycles or if the provided parameters are invalid.
     */
    public NonlinearAdditiveNoiseModel(Graph graph, int numSamples, RealDistribution noiseDistribution,
                                       double rescaleMin, double rescaleMax, int hiddenDimension, double inputScale) {
        if (!graph.paths().isAcyclic()) {
            throw new IllegalArgumentException("Graph contains cycles.");
        }

        if (numSamples < 1) {
            throw new IllegalArgumentException("Number of samples must be positive.");
        }

        if (rescaleMin > rescaleMax) {
            throw new IllegalArgumentException("Rescale min must be less than or equal to rescale max.");
        }

        if (rescaleMin == rescaleMax) {
            TetradLogger.getInstance().log("Rescale min and rescale max are equal. No rescaling will be applied.");
        }

        this.graph = graph;
        this.numSamples = numSamples;
        this.noiseDistribution = noiseDistribution;
        this.rescaleMin = rescaleMin;
        this.rescaleMax = rescaleMax;
        this.hiddenDimension = hiddenDimension;
        this.inputScale = inputScale;
    }

    /**
     * Generates synthetic data based on a directed acyclic graph (DAG) with causal relationships and post-nonlinear
     * causal mechanisms. The data generation process involves simulating parent-child relationships in the graph,
     * applying noise, rescaling, and applying random piecewise linear transformations.
     *
     * @return A DataSet object containing the generated synthetic data, with samples and variables defined by the
     * structure of the provided graph and simulation parameters.
     */
    public DataSet generateData() {
        DataSet data = new BoxDataSet(new DoubleDataBox(numSamples, graph.getNodes().size()), graph.getNodes());

        List<Node> nodes = graph.getNodes();
        Map<Node, Integer> nodeToIndex = IntStream.range(0, nodes.size()).boxed().collect(Collectors.toMap(nodes::get, i -> i));

        List<Node> validOrder = graph.paths().getValidOrder(graph.getNodes(), true);

        for (Node node : validOrder) {
            List<Node> parents = graph.getParents(node);

            // Define a random function with 20 hidden neurons, sine activation, and high bumpiness
            var f = new MultiLayerPerceptronFunctionND(
                    parents.size(), // Input dimension (R^N -> R)
                    this.hiddenDimension, // Number of hidden neurons
                    this.activationFunction, // Activation function
                    this.inputScale, // Input scale for bumpiness
                    -1 // Random seed
            );

            for (int sample = 0; sample < numSamples; sample++) {
                int _sample = sample;
                double[] array = parents.stream().mapToDouble(parent -> data.getDouble(_sample, nodeToIndex.get(parent))).toArray();
                double value = f.evaluate(array) + noiseDistribution.sample();
                data.setDouble(sample, nodeToIndex.get(node), value);
            }

            if (rescaleMin < rescaleMax) {
                DataTransforms.scale(data, rescaleMin, rescaleMax, node);
            }
        }

        return data;
    }

    /**
     * Sets the activation function for the model. The activation function defines a non-linear transformation applied
     * to the data during the simulation.
     *
     * @param activationFunction A function that takes a Double as input and returns a Double as output, representing
     *                           the non-linear activation function to be applied.
     */
    public void setActivationFunction(Function<Double, Double> activationFunction) {
        this.activationFunction = activationFunction;
    }
}

