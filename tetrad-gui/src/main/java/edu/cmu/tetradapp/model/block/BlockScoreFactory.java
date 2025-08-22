package edu.cmu.tetradapp.model.block;

import edu.cmu.tetrad.algcomparison.score.BlocksBicScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.Parameters;

/** Factory to create a block score from a BlockSpec. */
public final class BlockScoreFactory {
    private BlockScoreFactory() {}

    public static ScoreWrapper build(DataModel data, BlockSpec spec, Parameters params) {
        BlocksBicScore blocksBicScore = new BlocksBicScore();
        blocksBicScore.setBlockSpec(spec);
        return blocksBicScore; // minimal; adapt as needed
    }
}