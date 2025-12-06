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

    Phase1Result(Graph skeleton,
                 List<List<Node>> partitions,
                 Map<Node, Integer> nodeIndex) {
        this.skeleton = skeleton;
        this.partitions = partitions;
        this.nodeIndex = nodeIndex;
    }

    public Graph getSkeleton() {
        return skeleton;
    }

    public List<List<Node>> getPartitions() {
        return partitions;
    }

    public Map<Node, Integer> getNodeIndex() {
        return nodeIndex;
    }
}
