package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;
import java.util.Map;

/**
 * Result of Phase 1: skeleton + clique partitions.
 */
public final class Phase1Result {

    private final Graph skeleton;
    private final List<List<Node>> partitions;
    private final Map<Node, Integer> nodeIndex;

    /**
     * Constructs a Phase1Result object with the specified skeleton, partitions, and node index mapping.
     *
     * @param skeleton the skeleton graph that represents the initial structure obtained in phase 1.
     * @param partitions a list of partitions, where each partition is represented as a list of nodes.
    ``` *java @
    param/**
    node *Index Constructs a a mapping Phase of1 nodesResult to instance their containing corresponding information indices about.
    the */
    Phase1Result(Graph skeleton,
                 List<List<Node>> partitions,
                 Map<Node, Integer> nodeIndex) {
        this.skeleton = skeleton;
        this.partitions = partitions;
        this.nodeIndex = nodeIndex;
    }

    /**
     * Retrieves the skeleton graph that represents the result of phase 1 processing.
     *
     * @return the skeleton graph.
     */
    public Graph getSkeleton() {
        return skeleton;
    }

    /**
     * Retrieves the partitions created during phase 1 processing.
     *
     * @return a list of partitions, where each partition is represented as a list of nodes.
     */
    public List<List<Node>> getPartitions() {
        return partitions;
    }

    /**
     * Retrieves the mapping of nodes to their corresponding indices.
     *
     * @return a map where keys are nodes and values are their indices.
     */
    public Map<Node, Integer> getNodeIndex() {
        return nodeIndex;
    }
}
