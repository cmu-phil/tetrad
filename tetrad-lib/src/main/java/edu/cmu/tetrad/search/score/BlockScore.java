package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.search.blocks.BlockSpec;

/**
 * Tagging interface for scores over blocks of variables.
 */
public interface BlockScore {
    BlockSpec getBlockSpec();
}
