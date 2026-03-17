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

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates random MIMIC models.
 *
 * <p>In singly-connected mode, the latent subgraph is generated as a directed tree, and
 * each measured input and output is attached to exactly one latent. This guarantees that
 * the underlying undirected graph is singly connected.</p>
 *
 * <p>In general mode, the latent graph is a random DAG, and measured inputs and outputs
 * may be attached to multiple latents.</p>
 *
 * <p>The generator recognizes the following parameter names if the
 * {@link #generate(Parameters)} method is used:</p>
 *
 * <ul>
 *   <li>{@code mimicNumInputs}</li>
 *   <li>{@code mimicNumLatents}</li>
 *   <li>{@code mimicNumOutputs}</li>
 *   <li>{@code mimicSinglyConnected}</li>
 *   <li>{@code mimicLatentEdgeProb}</li>
 *   <li>{@code mimicInputAttachProb}</li>
 *   <li>{@code mimicOutputAttachProb}</li>
 * </ul>
 *
 * @author josephramsey
 */
public final class RandomMimicGraphGenerator {

    /**
     * Default latent-edge probability for general mode.
     */
    private static final double DEFAULT_LATENT_EDGE_PROB = 0.25;

    /**
     * Default input attachment probability for general mode.
     */
    private static final double DEFAULT_INPUT_ATTACH_PROB = 0.35;

    /**
     * Default output attachment probability for general mode.
     */
    private static final double DEFAULT_OUTPUT_ATTACH_PROB = 0.35;

    /**
     * Generates a random MIMIC model using values read from the given parameters object.
     *
     * @param parameters the parameters
     * @return the generated model
     */
    public MimicModel generate(Parameters parameters) {
        int numInputs = parameters.getInt("mimicNumInputs");
        int numLatents = parameters.getInt("mimicNumLatents");
        int numOutputs = parameters.getInt("mimicNumOutputs");
        boolean singlyConnected = parameters.getBoolean("mimicSinglyConnected");

        double latentEdgeProb = parameters.getDouble("mimicLatentEdgeProb", DEFAULT_LATENT_EDGE_PROB);
        double inputAttachProb = parameters.getDouble("mimicInputAttachProb", DEFAULT_INPUT_ATTACH_PROB);
        double outputAttachProb = parameters.getDouble("mimicOutputAttachProb", DEFAULT_OUTPUT_ATTACH_PROB);

        return generate(numInputs, numLatents, numOutputs, singlyConnected,
                latentEdgeProb, inputAttachProb, outputAttachProb);
    }

    /**
     * Generates a random MIMIC model.
     *
     * @param numInputs number of measured inputs
     * @param numLatents number of latents
     * @param numOutputs number of measured outputs
     * @param singlyConnected whether singly-connected structure should be enforced
     * @param latentEdgeProb latent-edge probability in general mode
     * @param inputAttachProb input attachment probability in general mode
     * @param outputAttachProb output attachment probability in general mode
     * @return the generated model
     */
    public MimicModel generate(int numInputs, int numLatents, int numOutputs,
                               boolean singlyConnected,
                               double latentEdgeProb,
                               double inputAttachProb,
                               double outputAttachProb) {
        if (numInputs < 1) {
            throw new IllegalArgumentException("Need at least one input.");
        }

        if (numLatents < 1) {
            throw new IllegalArgumentException("Need at least one latent.");
        }

        if (numOutputs < 1) {
            throw new IllegalArgumentException("Need at least one output.");
        }

        Graph graph = new EdgeListGraph();
        List<Node> inputs = new ArrayList<>();
        List<Node> latents = new ArrayList<>();
        List<Node> outputs = new ArrayList<>();

        for (int i = 0; i < numInputs; i++) {
            Node input = new GraphNode("X" + (i + 1));
            graph.addNode(input);
            inputs.add(input);
        }

        for (int i = 0; i < numLatents; i++) {
            Node latent = new GraphNode("L" + (i + 1));
            latent.setNodeType(NodeType.LATENT);
            graph.addNode(latent);
            latents.add(latent);
        }

        for (int i = 0; i < numOutputs; i++) {
            Node output = new GraphNode("Y" + (i + 1));
            graph.addNode(output);
            outputs.add(output);
        }

        if (singlyConnected) {
            generateSinglyConnected(graph, inputs, latents, outputs);
        } else {
            generateGeneral(graph, inputs, latents, outputs, latentEdgeProb, inputAttachProb, outputAttachProb);
        }

        return new MimicModel(graph, inputs, latents, outputs);
    }

    /**
     * Generates a singly-connected MIMIC model.
     *
     * @param graph the graph to update
     * @param inputs the measured inputs
     * @param latents the latents
     * @param outputs the measured outputs
     */
    private void generateSinglyConnected(Graph graph,
                                         List<Node> inputs,
                                         List<Node> latents,
                                         List<Node> outputs) {
        buildLatentTree(graph, latents);
        attachInputsSingly(graph, inputs, latents);
//        double extraInputLatentProb = 0.4;
//        addExtraInputToLatentEdges(graph, inputs, latents, extraInputLatentProb);
        attachOutputsSingly(graph, latents, outputs);
    }

    /**
     * Generates a more general MIMIC model.
     *
     * @param graph the graph to update
     * @param inputs the measured inputs
     * @param latents the latents
     * @param outputs the measured outputs
     * @param latentEdgeProb latent-edge probability
     * @param inputAttachProb input attachment probability
     * @param outputAttachProb output attachment probability
     */
    private void generateGeneral(Graph graph,
                                 List<Node> inputs,
                                 List<Node> latents,
                                 List<Node> outputs,
                                 double latentEdgeProb,
                                 double inputAttachProb,
                                 double outputAttachProb) {
        buildRandomLatentDag(graph, latents, latentEdgeProb);

        for (Node latent : latents) {
            ensureLatentHasMeasuredInput(graph, inputs, latent);
            ensureLatentHasMeasuredOutput(graph, outputs, latent);
        }

        for (Node input : inputs) {
            boolean attached = false;

            for (Node latent : latents) {
                if (RandomUtil.getInstance().nextDouble() < inputAttachProb) {
                    if (!graph.isParentOf(input, latent)) {
                        graph.addDirectedEdge(input, latent);
                    }
                    attached = true;
                }
            }

            if (!attached) {
                Node latent = latents.get(RandomUtil.getInstance().nextInt(latents.size()));
                if (!graph.isParentOf(input, latent)) {
                    graph.addDirectedEdge(input, latent);
                }
            }
        }

        for (Node output : outputs) {
            boolean attached = false;

            for (Node latent : latents) {
                if (RandomUtil.getInstance().nextDouble() < outputAttachProb) {
                    if (!graph.isParentOf(latent, output)) {
                        graph.addDirectedEdge(latent, output);
                    }
                    attached = true;
                }
            }

            if (!attached) {
                Node latent = latents.get(RandomUtil.getInstance().nextInt(latents.size()));
                if (!graph.isParentOf(latent, output)) {
                    graph.addDirectedEdge(latent, output);
                }
            }
        }
    }

    /**
     * Builds a directed latent tree.
     *
     * @param graph the graph to update
     * @param latents the latent nodes
     */
    private void buildLatentTree(Graph graph, List<Node> latents) {
        for (int i = 1; i < latents.size(); i++) {
            Node child = latents.get(i);
            Node parent = latents.get(RandomUtil.getInstance().nextInt(i));

            if (!graph.isParentOf(parent, child)) {
                graph.addDirectedEdge(parent, child);
            }
        }
    }

    /**
     * Builds a random latent DAG by only allowing edges from lower index to higher index.
     *
     * @param graph the graph to update
     * @param latents the latent nodes
     * @param latentEdgeProb the edge probability
     */
    private void buildRandomLatentDag(Graph graph, List<Node> latents, double latentEdgeProb) {
        for (int i = 0; i < latents.size(); i++) {
            for (int j = i + 1; j < latents.size(); j++) {
                if (RandomUtil.getInstance().nextDouble() < latentEdgeProb) {
                    Node from = latents.get(i);
                    Node to = latents.get(j);

                    if (!graph.isParentOf(from, to)) {
                        graph.addDirectedEdge(from, to);
                    }
                }
            }
        }
    }

    /**
     * Attaches each input to exactly one latent, ensuring that every latent receives at least
     * one measured input whenever possible.
     *
     * @param graph the graph to update
     * @param inputs the measured inputs
     * @param latents the latents
     */
    private void attachInputsSingly(Graph graph, List<Node> inputs, List<Node> latents) {
        List<Node> shuffledInputs = shuffled(inputs);
        List<Node> shuffledLatents = shuffled(latents);

        int k = Math.min(shuffledInputs.size(), shuffledLatents.size());

        for (int i = 0; i < k; i++) {
            Node input = shuffledInputs.get(i);
            Node latent = shuffledLatents.get(i);

            if (!graph.isParentOf(input, latent)) {
                graph.addDirectedEdge(input, latent);
            }
        }

        for (int i = k; i < shuffledInputs.size(); i++) {
            Node input = shuffledInputs.get(i);
            Node latent = shuffledLatents.get(RandomUtil.getInstance().nextInt(shuffledLatents.size()));

            if (!graph.isParentOf(input, latent)) {
                graph.addDirectedEdge(input, latent);
            }
        }
    }

    private void addExtraInputToLatentEdges(Graph graph,
                                            List<Node> inputs,
                                            List<Node> latents,
                                            double extraInputLatentProb) {
        for (Node input : inputs) {
            if (RandomUtil.getInstance().nextDouble() >= extraInputLatentProb) {
                continue;
            }

            List<Node> currentChildren = new ArrayList<>();

            for (Node child : graph.getChildren(input)) {
                if (child.getNodeType() == NodeType.LATENT) {
                    currentChildren.add(child);
                }
            }

            List<Node> candidates = new ArrayList<>(latents);
            candidates.removeAll(currentChildren);

            if (candidates.isEmpty()) {
                continue;
            }

            Node extraLatent = candidates.get(RandomUtil.getInstance().nextInt(candidates.size()));

            if (!graph.isParentOf(input, extraLatent)) {
                graph.addDirectedEdge(input, extraLatent);
            }
        }
    }

    /**
     * Attaches each output to exactly one latent, ensuring that every latent receives at least
     * one measured output whenever possible.
     *
     * @param graph the graph to update
     * @param latents the latents
     * @param outputs the measured outputs
     */
    private void attachOutputsSingly(Graph graph, List<Node> latents, List<Node> outputs) {
        List<Node> shuffledOutputs = shuffled(outputs);
        List<Node> shuffledLatents = shuffled(latents);

        int k = Math.min(shuffledOutputs.size(), shuffledLatents.size());

        for (int i = 0; i < k; i++) {
            Node output = shuffledOutputs.get(i);
            Node latent = shuffledLatents.get(i);

            if (!graph.isParentOf(latent, output)) {
                graph.addDirectedEdge(latent, output);
            }
        }

        for (int i = k; i < shuffledOutputs.size(); i++) {
            Node output = shuffledOutputs.get(i);
            Node latent = shuffledLatents.get(RandomUtil.getInstance().nextInt(shuffledLatents.size()));

            if (!graph.isParentOf(latent, output)) {
                graph.addDirectedEdge(latent, output);
            }
        }
    }

    /**
     * Ensures that the given latent has at least one measured input parent.
     *
     * @param graph the graph to update
     * @param inputs the measured inputs
     * @param latent the latent
     */
    private void ensureLatentHasMeasuredInput(Graph graph, List<Node> inputs, Node latent) {
        boolean hasMeasuredInput = false;

        for (Node parent : graph.getParents(latent)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                hasMeasuredInput = true;
                break;
            }
        }

        if (!hasMeasuredInput) {
            Node input = inputs.get(RandomUtil.getInstance().nextInt(inputs.size()));
            if (!graph.isParentOf(input, latent)) {
                graph.addDirectedEdge(input, latent);
            }
        }
    }

    /**
     * Ensures that the given latent has at least one measured output child.
     *
     * @param graph the graph to update
     * @param outputs the measured outputs
     * @param latent the latent
     */
    private void ensureLatentHasMeasuredOutput(Graph graph, List<Node> outputs, Node latent) {
        boolean hasMeasuredOutput = false;

        for (Node child : graph.getChildren(latent)) {
            if (child.getNodeType() != NodeType.LATENT) {
                hasMeasuredOutput = true;
                break;
            }
        }

        if (!hasMeasuredOutput) {
            Node output = outputs.get(RandomUtil.getInstance().nextInt(outputs.size()));
            if (!graph.isParentOf(latent, output)) {
                graph.addDirectedEdge(latent, output);
            }
        }
    }

    /**
     * Returns a shuffled copy of the given list.
     *
     * @param nodes the input list
     * @return the shuffled copy
     */
    private List<Node> shuffled(List<Node> nodes) {
        List<Node> copy = new ArrayList<>(nodes);

        for (int i = copy.size() - 1; i > 0; i--) {
            int j = RandomUtil.getInstance().nextInt(i + 1);

            Node temp = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, temp);
        }

        return copy;
    }
}