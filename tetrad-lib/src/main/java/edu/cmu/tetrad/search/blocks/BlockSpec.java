package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a block specification that organizes data into blocks, each associated with a variable and a rank. The
 * class is designed for immutability and serialization.
 * <p>
 * A block specification consists of: - A dataset ({@link DataSet}). - A list of blocks, where each block is a list of
 * indices. - A list of variables ({@link Node}) corresponding to each block. - A list of ranks, corresponding to each
 * block and variable, where each rank is an integer greater than or equal to 1.
 * <p>
 * Constructor validation ensures the integrity of the input, including size consistency between blocks, variables, and
 * ranks, as well as ensuring non-null and well-formed inputs.
 * <p>
 * This class satisfies the {@link TetradSerializable} interface, adhering to restrictions on serializable fields.
 */
public final class BlockSpec implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents the data set associated with the block specification. This field is immutable and initialized during
     * the construction of a {@code BlockSpec} instance. It serves as the core data associated with block operations and
     * validations.
     * <p>
     * The {@code dataSet} field cannot be null and remains constant throughout the lifecycle of the {@code BlockSpec}
     * object. It is integral to the functionality of the class, providing necessary contextual or operational data for
     * blocks, block variables, and ranks within the instance.
     */
    private final DataSet dataSet;
    /**
     * Represents a list of blocks where each block is represented as a list of integers. Each inner list corresponds to
     * a block and contains a series of integer values defining the elements of that block. This field is immutable,
     * ensuring that once it is initialized, its content cannot be modified.
     */
    private final List<List<Integer>> blocks;
    /**
     * Represents a list of {@link Node} objects that correspond to the block variables defined in a {@code BlockSpec}
     * instance. Each {@code Node} in this list is associated with a specific block, and the size of this list must
     * match the size of the blocks list in the corresponding {@code BlockSpec}.
     * <p>
     * This field is immutable and initialized during the construction of the {@code BlockSpec} instance.
     * <p>
     * It provides a way to define and access the variables that are relevant to each block within the block
     * specification.
     */
    private final List<Node> blockVariables;
    /**
     * Represents a list of ranks associated with the blocks in the BlockSpec instance. Each rank corresponds to a block
     * and denotes its designated or functional order in the context of the block specification.
     * <p>
     * The size of this list is expected to match the sizes of other block-specification-related lists such as blocks
     * and block variables to ensure consistency. All rank values are guaranteed to be integers greater than or equal to
     * 1.
     * <p>
     * This field is immutable and initialized by the constructor during the instantiation of a BlockSpec object.
     */
    private final List<Integer> ranks;

    /**
     * Constructs an instance of BlockSpec with the specified data set, blocks, and block variables. Validates input
     * parameters to ensure they are non-null, and the sizes of blocks and block variables are equal.
     *
     * @param dataSet        the data set associated with this block specification; must not be null
     * @param blocks         a list of lists of integers representing the blocks; must not be null and must match the
     *                       size of blockVariables
     * @param blockVariables a list of Node objects corresponding to the block variables; must not be null and must
     *                       match the size of blocks
     * @throws NullPointerException     if the data set is null
     * @throws IllegalArgumentException if blocks or blockVariables are null, or if the sizes of blocks and
     *                                  blockVariables do not match
     */
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

    /**
     * Constructs an instance of BlockSpec with the specified data set, blocks, block variables, and their corresponding
     * ranks. Validates the input parameters to ensure they are non-null, and that the sizes of the blocks, block
     * variables, and ranks are consistent. Ensures all rank values are greater than or equal to 1.
     *
     * @param dataSet        the data set associated with this block specification; must not be null
     * @param blocks         a list of lists of integers representing the blocks; must not be null, must match the size
     *                       of blockVariables and ranks
     * @param blockVariables a list of Node objects corresponding to the block variables; must not be null and must
     *                       match the size of blocks and ranks
     * @param ranks          a list of integers representing the rank of each block; must not be null and all values
     *                       must be >= 1
     * @throws NullPointerException     if the dataSet is null
     * @throws IllegalArgumentException if blocks, blockVariables, or ranks are null, the sizes of blocks,
     *                                  blockVariables, and ranks do not match, or if any rank is less than 1
     */
    public BlockSpec(DataSet dataSet, List<List<Integer>> blocks, List<Node> blockVariables, List<Integer> ranks) {
        if (dataSet == null) throw new NullPointerException("Data set is null");
        if (blocks == null || blockVariables == null || ranks == null)
            throw new IllegalArgumentException("blocks, blockVariables, and ranks must be non-null");
        if (blocks.size() != blockVariables.size() || blocks.size() != ranks.size())
            throw new IllegalArgumentException("blocks, blockVariables, and ranks must have same size");
        for (int r : ranks) if (r < 0) throw new IllegalArgumentException("Rank must be >= 1");

        this.dataSet = dataSet;
        this.blocks = unmodifiableNestedCopy(blocks);
        this.blockVariables = List.copyOf(blockVariables);
        this.ranks = List.copyOf(ranks);

        for (int i = 0; i < blocks.size(); i++) {
            blockVariables.get(i).setRank(ranks.get(i));
        }
    }

    private static List<List<Integer>> unmodifiableNestedCopy(List<List<Integer>> src) {
        List<List<Integer>> out = new ArrayList<>(src.size());
        for (List<Integer> b : src) out.add(List.copyOf(b));
        return List.copyOf(out);
    }

    /**
     * Returns the data set associated with this block specification.
     *
     * @return the DataSet object representing the data set associated with this BlockSpec instance
     */
    public DataSet dataSet() {
        return dataSet;
    }

    /**
     * Returns the blocks associated with this BlockSpec instance.
     *
     * @return a list of lists of integers, where each inner list represents a block
     */
    public List<List<Integer>> blocks() {
        return blocks;
    }

    /**
     * Returns the list of block variables associated with this BlockSpec instance.
     *
     * @return a list of Node objects representing the block variables
     */
    public List<Node> blockVariables() {
        return blockVariables;
    }

    /**
     * Returns the list of ranks associated with this BlockSpec instance.
     *
     * @return a list of integers representing the ranks of the blocks
     */
    public List<Integer> ranks() {
        return ranks;
    }

    @Override
    public @NotNull String toString() {
        return "BlockSpec[" +
               "dataSet=" + dataSet.getName() + ", " +
               "blocks=" + blocks + ", " +
               "blockVariables=" + blockVariables + ", " +   // <-- comma added
               "ranks=" + ranks + ']';
    }

    /**
     * Indicates whether some other object is "equal to" this one. Two BlockSpec instances are considered equal if their
     * respective dataSet, blocks, blockVariables, and ranks fields are equal.
     *
     * @param obj the reference object with which to compare
     * @return true if this object is the same as the obj argument, or if obj is a BlockSpec instance with the same
     * dataSet, blocks, blockVariables, and ranks; false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof BlockSpec that)) return false;
        return Objects.equals(this.dataSet, that.dataSet)
               && Objects.equals(this.blocks, that.blocks)
               && Objects.equals(this.blockVariables, that.blockVariables)
               && Objects.equals(this.ranks, that.ranks);
    }

    /**
     * Computes the hash code for this object. The hash code is calculated based on the dataSet, blocks, blockVariables,
     * and ranks fields of this instance.
     *
     * @return an integer representing the hash code value for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(dataSet, blocks, blockVariables, ranks);
    }
}