package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.search.blocks.BlockSpec;

/**
 * A tagging interface for algorithms that can be used for latent structure search.
 */
public interface ExtraLatentStructureAlgorithm extends LatentStructureAlgorithm {

    /**
     * Sets the block specification for configuring the algorithm.
     *
     * @param blockSpec the block specification to be applied, which defines the structure and parameters required by
     *                  the algorithm.
     */
    void setBlockSpec(BlockSpec blockSpec);
}
