package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.search.blocks.BlockSpec;

/**
 * Tags an independence test that operates over blocks.
 */
public interface BlockTest {

    /**
     * Retrieves the {@code BlockSpec} associated with this block-based independence test.
     *
     * @return the {@code BlockSpec} containing the block configuration details for the test.
     */
    BlockSpec getBlockSpec();
}
