package edu.cmu.tetradapp.editor.blocks;

import edu.cmu.tetrad.search.blocks.BlockSpec;

/**
 * Represents a functional interface for listening to events where a BlockSpec becomes available.
 * <p>
 * It can be utilized to handle scenarios where a BlockSpec is produced as a result of an operation, such as a search
 * completion or an application of some functionality.
 */
@FunctionalInterface
public interface BlockSpecListener {

    /**
     * Fired when a BlockSpec is available (from Search done or Apply).
     *
     * @param spec The BlockSpec that is available.
     */
    void onBlockSpec(BlockSpec spec);
}