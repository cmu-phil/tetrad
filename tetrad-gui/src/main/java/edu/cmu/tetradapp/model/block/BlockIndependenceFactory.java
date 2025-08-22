package edu.cmu.tetradapp.model.block;

import edu.cmu.tetrad.algcomparison.independence.BlockIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.BlocksIndTest;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.Parameters;

/** Factory to create a block independence test from a BlockSpec. */
public final class BlockIndependenceFactory {
    private BlockIndependenceFactory() {}

    public static BlockIndependenceWrapper build(DataModel data, BlockSpec spec, Parameters params) {
        // params may contain choices for the base observed test; adapt as needed
        BlocksIndTest blocksIndTest = new BlocksIndTest();
        blocksIndTest.setBlockSpec(spec);
        return blocksIndTest; // minimal; inject anything else you need
    }
}