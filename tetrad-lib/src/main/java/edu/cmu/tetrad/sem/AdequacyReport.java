package edu.cmu.tetrad.sem;

import java.util.Collections;
import java.util.List;

public final class AdequacyReport {

    public final double meanImprovement;
    public final double fracNodesImproved;
    public final double mmd2;

    public final List<NodeAdequacySummary> nodeSummaries;

    AdequacyReport(double meanImprovement,
                   double fracNodesImproved,
                   double mmd2,
                   List<NodeAdequacySummary> nodeSummaries) {

        this.meanImprovement = meanImprovement;
        this.fracNodesImproved = fracNodesImproved;
        this.mmd2 = mmd2;
        this.nodeSummaries = Collections.unmodifiableList(nodeSummaries);
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("DAG Adequacy Report\n\n");

        sb.append("Mean improvement over baseline: ")
                .append(meanImprovement).append("\n");
        sb.append("Fraction nodes improved: ")
                .append(fracNodesImproved).append("\n");
        sb.append("Global MMD^2 (real vs simulated): ")
                .append(String.format("mmd2 = %.3e%n", mmd2)).append("\n\n");

        sb.append("Per-node heldout improvement:\n");
        for (NodeAdequacySummary s : nodeSummaries) {
            sb.append("  ").append(s.node)
                    .append("  improvement=").append(s.improvement)
                    .append("\n");
        }

        return sb.toString();
    }
}