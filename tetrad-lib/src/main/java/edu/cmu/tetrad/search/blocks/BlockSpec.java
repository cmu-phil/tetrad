package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.graph.Node;
import java.util.List;

/** Immutable block specification: blocks of variable indices + one Node per block. */
public record BlockSpec(List<List<Integer>> blocks, List<Node> blockVariables) {
    public BlockSpec {
        if (blocks == null || blockVariables == null)
            throw new IllegalArgumentException("blocks and blockVariables must be non-null");
        if (blocks.size() != blockVariables.size())
            throw new IllegalArgumentException("blocks and blockVariables must have same size");
    }
}