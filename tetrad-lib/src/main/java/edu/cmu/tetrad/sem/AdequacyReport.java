package edu.cmu.tetrad.sem;

import java.util.Collections;
import java.util.List;

/**
 * Represents a report evaluating the adequacy of a model's performance relative to a baseline.
 *
 * This class captures key metrics, including the overall mean improvement of nodes, the fraction
 * of nodes that demonstrated improvement, global data alignment metrics, and per-node performance
 * summaries. It is designed to provide a comprehensive assessment of model adequacy.
 *
 * Instances of this class are immutable, ensuring their contents cannot be modified after creation.
 */
public final class AdequacyReport {

    /**
     * Represents the mean improvement of nodes over a baseline metric
     * in the context of an adequacy report.
     * <p>
     * This value indicates the average magnitude of improvement
     * across all nodes, providing a summary measure for assessing
     * overall performance enhancement.
     */
    public final double meanImprovement;
    /**
     * Represents the fraction
     */
    public final double fracNodesImproved;
    /**
     * Represents the global Maximum Mean Discrepancy squared (MMD²) value
     * between the real and simulated data distributions in the context
     * of an adequacy report.
     * <p>
     * This metric provides a quantitative measure of how well the simulated
     * data aligns with the real data, with smaller values indicating closer
     * alignment. It is useful in evaluating the overall representational
     * quality of the simulation process.
     */
    public final double mmd2;
    /**
     * Represents a collection of adequacy summaries for individual nodes within a model.
     * <p>
     * Each element in this list provides detailed metrics and performance insights for a specific
     * node, including its name, parents, and performance improvements relative to a baseline.
     * This field is used as part of the adequacy report to evaluate the performance and improvements
     * of nodes in the context of the overall model.
     * <p>
     * This list is immutable and provides a comprehensive snapshot of per-node evaluation results.
     */
    public final List<NodeAdequacySummary> nodeSummaries;

    /**
     * Constructs an instance of AdequacyReport, which captures key metrics and summaries
     * for evaluating the adequacy of a model relative to a baseline.
     *
     * @param meanImprovement   The mean improvement of nodes over a baseline metric, representing
     *                          the average performance enhancement across all nodes.
     * @param fracNodesImproved The fraction of nodes that demonstrated performance improvement
     *                          relative to the baseline.
     * @param mmd2              The global Maximum Mean Discrepancy squared (MMD²) value, measuring the
     *                          alignment between real and simulated data distributions.
     * @param nodeSummaries     A collection of adequacy summaries for individual nodes, providing
     *                          detailed per-node metrics and insights into performance being evaluated.
     */
    AdequacyReport(double meanImprovement,
                   double fracNodesImproved,
                   double mmd2,
                   List<NodeAdequacySummary> nodeSummaries) {

        this.meanImprovement = meanImprovement;
        this.fracNodesImproved = fracNodesImproved;
        this.mmd2 = mmd2;
        this.nodeSummaries = Collections.unmodifiableList(nodeSummaries);
    }

    /**
     * Converts the adequacy report data into a human-readable textual representation.
     * <p>
     * The resulting text includes global metrics, such as mean improvement over the
     * baseline, the fraction of nodes that showed improvement, and the global MMD^2
     * value. It also provides a per-node breakdown of holdout improvements, listing
     * each node along with its specific improvement value.
     *
     * @return A string representation of the adequacy report, including global and
     * per-node metrics formatted for readability.
     */
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