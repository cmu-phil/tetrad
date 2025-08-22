package edu.cmu.tetradapp.editor.blocks;

import edu.cmu.tetrad.search.blocks.BlockSpec;

@FunctionalInterface
public interface BlockSpecListener {
    /** Fired when a BlockSpec is available (from Search done or Apply). */
    void onBlockSpec(BlockSpec spec);
}