package edu.cmu.tetrad.search.blocks;

/**
 * An interface for discovering clusters or "blocks" of indices based on data and algorithmic techniques. The discovered
 * blocks are represented as a {@code BlockSpec}, encapsulating clusters of observed indices and any associated metadata
 * or latent structures.
 * <p>
 * Implementations of this interface act as adapters for various clustering or block discovery algorithms, such as FTFC,
 * FOFC, BPC, and TSC, with additional functionality including block validation, canonicalization, and policy-specific
 * adjustments.
 * <p>
 * Key functionalities: - Discovering clusters or blocks of variables from a dataset using algorithm-specific logic. -
 * Ensuring the resulting blocks adhere to validation criteria and are formatted consistently. - Applying single-cluster
 * policies when handling complex overlapping or conflicting blocks.
 */
public interface BlockDiscoverer {

    /**
     * Discovers and returns clusters or blocks of indices based on the underlying block discovery algorithm. The result
     * is encapsulated in a {@code BlockSpec}, which contains the identified clusters and any associated metadata.
     * <p>
     * The method implements algorithmic techniques to analyze the input data and generate meaningful groupings, with
     * optional validation and canonicalization. It may also apply predefined policies to adjust the discovered blocks
     * in cases of overlaps or conflicts.
     *
     * @return a {@code BlockSpec} object representing the discovered clusters or blocks, including relevant metadata or
     * latent structures when applicable.
     */
    BlockSpec discover();
}