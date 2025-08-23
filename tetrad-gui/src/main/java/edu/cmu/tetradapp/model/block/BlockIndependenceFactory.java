package edu.cmu.tetradapp.model.block;

import edu.cmu.tetrad.algcomparison.independence.BlockIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.BlocksIndTest;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.Parameters;

/**
 * Factory to create a block independence test from a BlockSpec.
 */
public final class BlockIndependenceFactory {

    /**
     * Private constructor to prevent instantiation.
     */
    private BlockIndependenceFactory() {
    }

    /**
     * Constructs a BlockIndependenceWrapper using the provided data, block specification, and parameters.
     *
     * @param data   the data model containing the data to be analyzed
     * @param spec   the block specification defining the structure of the blocks
     * @param params the parameters controlling the behavior of the independence test
     * @return a BlockIndependenceWrapper instance configured with the provided data, block specification, and
     * parameters
     */
    public static BlockIndependenceWrapper build(DataModel data, BlockSpec spec, Parameters params) {
        // params may contain choices for the base observed test; adapt as needed
        BlocksIndTest blocksIndTest = new BlocksIndTest();
        blocksIndTest.setBlockSpec(spec);
        return blocksIndTest; // minimal; inject anything else you need
    }
}