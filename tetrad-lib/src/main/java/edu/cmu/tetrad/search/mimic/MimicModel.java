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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores a generated MIMIC model together with the designated input, latent, and output
 * variables.
 *
 * <p>The model graph may contain only the following edge types:</p>
 *
 * <ul>
 *   <li>measured input to latent,</li>
 *   <li>latent to latent,</li>
 *   <li>latent to measured output.</li>
 * </ul>
 *
 * <p>In singly-connected mode, the underlying undirected graph is intended to be a tree,
 * or at least to have at most one undirected path between any pair of nodes.</p>
 *
 * @author josephramsey
 */
public final class MimicModel {

    /**
     * The full true graph, including latent variables.
     */
    private final Graph graph;

    /**
     * The measured input variables.
     */
    private final List<Node> inputs;

    /**
     * The latent variables.
     */
    private final List<Node> latents;

    /**
     * The measured output variables.
     */
    private final List<Node> outputs;

    /**
     * Constructs a new MIMIC model container.
     *
     * @param graph the full true graph
     * @param inputs the measured inputs
     * @param latents the latent variables
     * @param outputs the measured outputs
     */
    public MimicModel(Graph graph, List<Node> inputs, List<Node> latents, List<Node> outputs) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        if (inputs == null || latents == null || outputs == null) {
            throw new NullPointerException("Inputs, latents, and outputs must not be null.");
        }

        this.graph = new EdgeListGraph(graph);
        this.inputs = new ArrayList<>(inputs);
        this.latents = new ArrayList<>(latents);
        this.outputs = new ArrayList<>(outputs);
    }

    /**
     * Returns a defensive copy of the full graph.
     *
     * @return the graph
     */
    public Graph getGraph() {
        return new EdgeListGraph(this.graph);
    }

    /**
     * Returns a defensive copy of the measured input list.
     *
     * @return the inputs
     */
    public List<Node> getInputs() {
        return new ArrayList<>(this.inputs);
    }

    /**
     * Returns a defensive copy of the latent list.
     *
     * @return the latents
     */
    public List<Node> getLatents() {
        return new ArrayList<>(this.latents);
    }

    /**
     * Returns a defensive copy of the measured output list.
     *
     * @return the outputs
     */
    public List<Node> getOutputs() {
        return new ArrayList<>(this.outputs);
    }

    /**
     * Returns all measured variables, with inputs listed first and outputs second.
     *
     * @return the measured variables
     */
    public List<Node> getMeasuredNodes() {
        List<Node> measured = new ArrayList<>(this.inputs);
        measured.addAll(this.outputs);
        return measured;
    }

    /**
     * Returns knowledge placing all inputs in tier 0 and all outputs in tier 1.
     *
     * @return the tier knowledge
     */
    public Knowledge getTierKnowledge() {
        Knowledge knowledge = new Knowledge();

        for (Node input : this.inputs) {
            knowledge.addToTier(0, input.getName());
        }

        for (Node output : this.outputs) {
            knowledge.addToTier(1, output.getName());
        }

        return knowledge;
    }

    /**
     * Returns the measured input parents of the given latent in the true graph.
     *
     * @param latent the latent node
     * @return the measured input parents
     */
    public Set<Node> getInputParents(Node latent) {
        Set<Node> parents = new LinkedHashSet<>();

        for (Node parent : this.graph.getParents(latent)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                parents.add(parent);
            }
        }

        return parents;
    }

    /**
     * Returns the measured output children of the given latent in the true graph.
     *
     * @param latent the latent node
     * @return the measured output children
     */
    public Set<Node> getOutputChildren(Node latent) {
        Set<Node> children = new LinkedHashSet<>();

        for (Node child : this.graph.getChildren(latent)) {
            if (child.getNodeType() != NodeType.LATENT) {
                children.add(child);
            }
        }

        return children;
    }
}