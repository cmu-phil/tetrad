package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.List;

/**
 * Immutable block specification: dataset + blocks of variable indices + one Node per block.
 */
public record BlockSpec(DataSet dataSet, List<List<Integer>> blocks,
                        List<Node> blockVariables) implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    public BlockSpec {
        if (dataSet == null)
            throw new NullPointerException("Data set is null");
        if (blocks == null || blockVariables == null)
            throw new IllegalArgumentException("blocks and blockVariables must be non-null");
        if (blocks.size() != blockVariables.size())
            throw new IllegalArgumentException("blocks and blockVariables must have same size");
    }

    @Override
    public @NotNull String toString() {
        return "BlockSpec[" +
               "dataSet=" + dataSet.getName() + ", " +
               "blocks=" + blocks + ", " +
               "blockVariables=" + blockVariables + ']';
    }

}