package edu.cmu.tetradapp.model.block;

import edu.cmu.tetrad.algcomparison.score.BlocksBicScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.Parameters;

/**
 * Factory to create a block score from a BlockSpec.
 */
public final class BlockScoreFactory {

    /**
     * Private constructor to prevent instantiation.
     */
    private BlockScoreFactory() {
    }

    /**
     * Constructs a ScoreWrapper using the provided data, block specification, and parameters.
     *
     * @param data   the data model containing the data to be analyzed
     * @param spec   the block specification defining the structure of the blocks
     * @param params the parameters controlling the behavior of the score calculation
     * @return a ScoreWrapper instance configured with the provided data, block specification, and parameters
     */
    public static ScoreWrapper build(DataModel data, BlockSpec spec, Parameters params) {
        BlocksBicScore blocksBicScore = new BlocksBicScore();
        blocksBicScore.setBlockSpec(spec);
        return blocksBicScore; // minimal; adapt as needed
    }
}