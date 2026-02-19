package edu.cmu.tetrad.sem;

import java.util.List;

public final class NodeAdequacySummary {

    public final String node;
    public final boolean discrete;
    public final List<String> parents;

    public final double holdoutLoss;
    public final double baselineLoss;
    public final double improvement; // baseline - model

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