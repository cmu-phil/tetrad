package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implements purification step of Silva et al.'s BuildPureClusters algorithm. Ensures that each measured variable has
 * only one latent parent.
 *
 * @author jdramsey
 */
public class LatentPurifier {
    /**
     * The graph representing the model to be purified. It serves as the foundational structure for the purification
     * process in the BuildPureClusters algorithm. This graph contains nodes and edges that represent both latent and
     * measured variables. Modifications made during the purification process involve alterations to this graph. It is a
     * required component for the LatentPurifier, providing the starting point for processing and ensuring the
     * constraints of the algorithm are met.
     */
    private final Graph modelGraph;
    /**
     * Represents a collection of latent variables within a model graph. Latent variables are abstract nodes that do not
     * have direct observational data in the context of the model but may influence measured variables in the graph.
     * <p>
     * This field is part of the representation of the structural graph used during the purification process in Silva et
     * al.'s BuildPureClusters algorithm. It is used to manage and process latent variables for tasks such as
     * identifying latent parent relationships and ensuring compliance with constraints on the graph.
     * <p>
     * It is initialized through the constructor and remains immutable throughout the lifespan of the instance.
     */
    private final List<Node> latentVariables;
    /**
     * Represents the list of measured variables in the model graph. Each entry in this list corresponds to a node that
     * is categorized as a measured variable in the context of the graph's structure and operations. Measured variables
     * are considered observable, as opposed to latent variables, which are unobservable.
     * <p>
     * This field is utilized during the purification step of the algorithm implemented by the containing class to
     * ensure measured variables adhere to specific constraints, such as having at most one latent parent. These
     * variables are integral to the purification process, which modifies the graph to meet the defined structural
     * requirements.
     * <p>
     * This field is immutable and is initialized through the constructor of the containing class.
     */
    private final List<Node> measuredVariables;

    /**
     * Constructor for the LatentPurifier class, which implements the purification step of Silva et al.'s
     * BuildPureClusters algorithm. It initializes the model graph, latent variables, and measured variables for
     * processing.
     *
     * @param modelGraph        the graph that represents the model to be purified; cannot be null
     * @param latentVariables   the list of nodes representing latent variables in the model
     * @param measuredVariables the list of nodes representing measured variables in the model
     */
    public LatentPurifier(Graph modelGraph, List<Node> latentVariables, List<Node> measuredVariables) {
        this.modelGraph = Objects.requireNonNull(modelGraph, "Model graph cannot be null.");
        this.latentVariables = new ArrayList<>(latentVariables);
        this.measuredVariables = new ArrayList<>(measuredVariables);
    }

    /**
     * Purifies the graph by ensuring that each measured variable has at most one latent parent and removes latent
     * variables that have no measured children. It modifies a copy of the given model graph to adhere to these
     * constraints and returns the purified graph.
     * <p>
     * This method is implemented as part of the purification step in Silva et al.'s BuildPureClusters algorithm.
     *
     * @return a purified graph where each measured variable has at most one latent parent, and latent variables with no
     * measured children are removed.
     */
    public Graph purify() {
        Graph purified = GraphUtils.replaceNodes(modelGraph, modelGraph.getNodes());

        boolean changed;
        do {
            changed = false;

            for (Node measured : measuredVariables) {
                List<Node> latentParents = getLatentParents(purified, measured);

                if (latentParents.size() <= 1) continue;

                // Remove edges from all but one latent parent
                for (int i = 1; i < latentParents.size(); i++) {
                    Node latent = latentParents.get(i);
                    purified.removeEdge(latent, measured);
                    changed = true;
                }
            }

            // Remove latents with no measured children
            for (Node latent : new ArrayList<>(latentVariables)) {
                List<Node> children = purified.getChildren(latent);
                children.retainAll(measuredVariables);

                if (children.isEmpty()) {
                    removeLatentAndEdges(purified, latent);
                    latentVariables.remove(latent);
                    changed = true;
                }
            }

        } while (changed);

        return purified;
    }

    /**
     * Retrieves the latent parents of a given measured node from a specified graph.
     * A latent parent is defined as a parent of the measured node that is also within the
     * set of latent variables.
     *
     * @param graph    the graph from which the latent parents are to be identified; cannot be null.
     * @param measured the node representing the measured variable whose latent parents are to be retrieved; cannot be null.
     * @return a list of nodes representing the latent parents of the specified measured node. If no latent parents are found, returns an empty list.
     */
    private List<Node> getLatentParents(Graph graph, Node measured) {
        List<Node> parents = graph.getParents(measured);
        parents.retainAll(latentVariables);
        return parents;
    }

    /**
     * Removes a specified latent node and all its associated edges from the given graph.
     *
     * @param graph  the graph from which the latent node and its edges are to be removed; cannot be null.
     * @param latent the latent node to be removed from the graph along with its associated edges; cannot be null.
     */
    private void removeLatentAndEdges(Graph graph, Node latent) {
        for (Node neighbor : new ArrayList<>(graph.getAdjacentNodes(latent))) {
            graph.removeEdge(latent, neighbor);
        }
        graph.removeNode(latent);
    }
}