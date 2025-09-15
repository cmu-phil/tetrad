package edu.cmu.tetrad.search.blocks;

/**
 * Enum representing policies for handling clusters in a single cluster analysis context.
 */
public enum SingleClusterPolicy {

    /**
     * Represents the policy to exclude single variables. This is typically used to disregard the specified cluster from
     * further analysis or processing.
     */
    EXCLUDE,

    /**
     * Represents the policy to include single variables as singleton clusters.
     */
    INCLUDE,

    /**
     * Represents a policy to group all singleton variables into a single cluster named "Noise".
     */
    NOISE_VAR
}
