package edu.cmu.tetrad.sem;

import java.util.List;

/**
 * Represents the adequacy summary of a specific node in a model with information about its
 * characteristics, performance metrics, and improvement over a baseline.
 *
 * This class is immutable and holds the metrics and metadata associated with a node.
 * It is used to evaluate the performance of a node compared to a baseline.
 */
public final class NodeAdequacySummary {

    /**
     * The name or identifier of the node being evaluated within the model.
     *
     * This field uniquely represents the specific node whose adequacy and performance metrics
     * are summarized in the containing class. It is typically used as a reference to evaluate
     * its characteristics in relation to its parents and associated metrics.
     *
     * This value is immutable and set during the construction of the object.
     */
    public final String node;
    /**
     * Indicates whether the node is discrete or continuous.
     */
    public final boolean discrete;
    /**
     * List of parent nodes for the node being evaluated.
     */
    public final List<String> parents;
    /**
     * The loss value obtained from the holdout set.
     */
    public final double holdoutLoss;
    /**
     * The loss value
     */
    public final double baselineLoss;
    /**
     * The improvement in loss value from the baseline to the holdout set.
     */
    public final double improvement; // baseline - model

    /**
     * Constructs a new instance of NodeAdequacySummary, representing the adequacy and
     * performance summary for a specific node in a model.
     *
     * @param node The name or identifier of the node being evaluated.
     * @param discrete A boolean indicating whether the node is discrete (true) or continuous (false).
     * @param parents A list of parent node identifiers for the node being evaluated.
     * @param holdoutLoss The loss value obtained from the evaluation of the node on the holdout set.
     * @param baselineLoss The loss value representing the baseline performance of the node.
     */
    NodeAdequacySummary(String node,
                        boolean discrete,
                        List<String> parents,
                        double holdoutLoss,
                        double baselineLoss) {
        this.node = node;
        this.discrete = discrete;
        this.parents = parents;
        this.holdoutLoss = holdoutLoss;
        this.baselineLoss = baselineLoss;
        this.improvement = baselineLoss - holdoutLoss;
    }
}