package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.search.blocks.BlockSpec;

/**
 * A tagging interface for algorithms like Mimbuild that are latent structure algorithms
 * but do not use tests or scores.
 */
public interface ExtraLatentStructureAlgorithm {
    void setBlockSpec(BlockSpec blockSpec);
}
