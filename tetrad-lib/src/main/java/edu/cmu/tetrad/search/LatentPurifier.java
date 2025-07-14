package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * Implements purification step of Silva et al.'s BuildPureClusters algorithm.
 * Ensures that each measured variable has only one latent parent.
 */
public class LatentPurifier {

    private final Graph modelGraph;
    private final List<Node> latentVariables;
    private final List<Node> measuredVariables;

    public LatentPurifier(Graph modelGraph, List<Node> latentVariables, List<Node> measuredVariables) {
        this.modelGraph = Objects.requireNonNull(modelGraph, "Model graph cannot be null.");
        this.latentVariables = new ArrayList<>(latentVariables);
        this.measuredVariables = new ArrayList<>(measuredVariables);
    }

    /**
     * Executes the purification step. Returns a graph where each measured variable
     * has a unique latent parent (if any).
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

    private List<Node> getLatentParents(Graph graph, Node measured) {
        List<Node> parents = graph.getParents(measured);
        parents.retainAll(latentVariables);
        return parents;
    }

    private void removeLatentAndEdges(Graph graph, Node latent) {
        for (Node neighbor : new ArrayList<>(graph.getAdjacentNodes(latent))) {
            graph.removeEdge(latent, neighbor);
        }
        graph.removeNode(latent);
    }
}