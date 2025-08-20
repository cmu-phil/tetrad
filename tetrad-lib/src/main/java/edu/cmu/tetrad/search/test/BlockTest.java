package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.search.blocks.BlockSpec;

/**
 * Tags an independence test that operates over blocks.
 */
public interface BlockTest {
    BlockSpec getBlockSpec();
}
