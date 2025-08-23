package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.search.blocks.BlockSpec;

/**
 * Tagging interface for scores over blocks of variables.
 */
public interface BlockScore {

    /**
     * Retrieves the specification of the block associated with the score.
     *
     * @return the BlockSpec instance representing the block's specification
     */
    BlockSpec getBlockSpec();
}
