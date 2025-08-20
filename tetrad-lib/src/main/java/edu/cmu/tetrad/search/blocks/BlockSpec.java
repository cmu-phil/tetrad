package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/**
 * Immutable block specification: blocks of variable indices + one Node per block.
 */
public record BlockSpec(List<List<Integer>> blocks, List<Node> blockVariables) implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    public BlockSpec {
        if (blocks == null || blockVariables == null)
            throw new IllegalArgumentException("blocks and blockVariables must be non-null");
        if (blocks.size() != blockVariables.size())
            throw new IllegalArgumentException("blocks and blockVariables must have same size");
    }

    @Override
    public String toString() {
        return "BlockSpec[" +
               "blocks=" + blocks + ", " +
               "blockVariables=" + blockVariables + ']';
    }

}