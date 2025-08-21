package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BlockSpec implements TetradSerializable {
    @Serial private static final long serialVersionUID = 23L;

    private final DataSet dataSet;
    private final List<List<Integer>> blocks;       // unmodifiable outer + inner
    private final List<Node> blockVariables;        // unmodifiable
    private final List<Integer> ranks;              // unmodifiable

    public BlockSpec(DataSet dataSet, List<List<Integer>> blocks, List<Node> blockVariables) {
        if (dataSet == null) throw new NullPointerException("Data set is null");
        if (blocks == null || blockVariables == null)
            throw new IllegalArgumentException("blocks and blockVariables must be non-null");
        if (blocks.size() != blockVariables.size())
            throw new IllegalArgumentException("blocks and blockVariables must have same size");

        this.dataSet = dataSet;
        this.blocks = unmodifiableNestedCopy(blocks);
        this.blockVariables = List.copyOf(blockVariables);
        // default all ranks to 1
        List<Integer> rs = new ArrayList<>(blockVariables.size());
        for (int i = 0; i < blockVariables.size(); i++) rs.add(1);
        this.ranks = List.copyOf(rs);
    }

    public BlockSpec(DataSet dataSet, List<List<Integer>> blocks, List<Node> blockVariables, List<Integer> ranks) {
        if (dataSet == null) throw new NullPointerException("Data set is null");
        if (blocks == null || blockVariables == null || ranks == null)
            throw new IllegalArgumentException("blocks, blockVariables, and ranks must be non-null");
        if (blocks.size() != blockVariables.size() || blocks.size() != ranks.size())
            throw new IllegalArgumentException("blocks, blockVariables, and ranks must have same size");
        for (int r : ranks) if (r < 1) throw new IllegalArgumentException("Rank must be >= 1");

        this.dataSet = dataSet;
        this.blocks = unmodifiableNestedCopy(blocks);
        this.blockVariables = List.copyOf(blockVariables);
        this.ranks = List.copyOf(ranks);
    }

    private static List<List<Integer>> unmodifiableNestedCopy(List<List<Integer>> src) {
        List<List<Integer>> out = new ArrayList<>(src.size());
        for (List<Integer> b : src) out.add(List.copyOf(b));
        return List.copyOf(out);
    }

    public DataSet dataSet() { return dataSet; }
    public List<List<Integer>> blocks() { return blocks; }
    public List<Node> blockVariables() { return blockVariables; }
    public List<Integer> ranks() { return ranks; }

    @Override
    public @NotNull String toString() {
        return "BlockSpec[" +
               "dataSet=" + dataSet.getName() + ", " +
               "blocks=" + blocks + ", " +
               "blockVariables=" + blockVariables + ", " +   // <-- comma added
               "ranks=" + ranks + ']';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof BlockSpec that)) return false;
        return Objects.equals(this.dataSet, that.dataSet)
               && Objects.equals(this.blocks, that.blocks)
               && Objects.equals(this.blockVariables, that.blockVariables)
               && Objects.equals(this.ranks, that.ranks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSet, blocks, blockVariables, ranks);
    }
}