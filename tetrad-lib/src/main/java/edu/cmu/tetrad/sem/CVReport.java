package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * Result of k-fold cross-validation performed by NNEstimator#crossValidate(int).
 *
 * <p>Contains:
 * <ul>
 *   <li>Per-node OOS metrics in {@link #nodeSummaries} — one entry per non-root
 *       node, giving OOS MSE / R² (continuous) or OOS cross-entropy (discrete).</li>
 *   <li>A whole-graph OOS MMD² score averaged across folds, measuring how well
 *       the simulator trained on k-1 folds reproduces the joint distribution of
 *       the held-out fold.</li>
 *   <li>Summary statistics: mean OOS R² across continuous non-root nodes, mean
 *       OOS cross-entropy improvement across discrete non-root nodes, and the
 *       fraction of non-root nodes that beat their baseline out of sample.</li>
 * </ul>
 *
 * <p>Node summaries for root nodes are omitted — roots have no parents and
 * therefore no conditional prediction to evaluate.
 */
public final class CVReport implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Number of folds used.
     */
    public final int numFolds;

    /**
     * Per-node OOS summaries, one per non-root node.
     * Root nodes are excluded since they have no conditional mechanism to evaluate.
     */
    public final List<NodeCVSummary> nodeSummaries;

    /**
     * Mean OOS MMD² across folds.
     * Each fold contributes one MMD² value computed between the observed held-out
     * rows and data simulated by the model trained on the remaining folds.
     */
    public final double meanOosMmd2;

    /**
     * Mean OOS R² across continuous non-root nodes.
     * Positive means the NN beats the marginal-mean baseline on average.
     * NaN if there are no continuous non-root nodes.
     */
    public final double meanOosR2;

    /**
     * Mean OOS cross-entropy improvement (baseline − model) across discrete
     * non-root nodes.  Positive means the NN beats the marginal-frequency
     * baseline on average.
     * NaN if there are no discrete non-root nodes.
     */
    public final double meanOosXentImprovement;

    /**
     * Fraction of non-root nodes whose OOS metric beats their respective
     * baseline (R² > 0 for continuous; xent improvement > 0 for discrete).
     * NaN if there are no non-root nodes.
     */
    public final double fracNodesBeatBaseline;

    // ── constructor ───────────────────────────────────────────────────────────

    /**
     * Constructs an immutable cross-validation report, summarizing the results of
     * k-fold cross-validation across multiple nodes.
     *
     * @param numFolds The number of folds used in the cross-validation.
     * @param nodeSummaries A list of per-node cross-validation summaries, where
     *                      each node is represented as a {@link NodeCVSummary}.
     * @param meanOosMmd2 The mean out-of-sample squared maximum mean discrepancy
     *                    (OOS MMD²) across all nodes.
     */
    public CVReport(int numFolds,
                    List<NodeCVSummary> nodeSummaries,
                    double meanOosMmd2) {
        this.numFolds = numFolds;
        this.nodeSummaries = Collections.unmodifiableList(nodeSummaries);
        this.meanOosMmd2 = meanOosMmd2;

        // Derive summary statistics from node summaries.
        double sumR2 = 0.0;
        int countR2 = 0;
        double sumXentImp = 0.0;
        int countXent = 0;
        int beaten = 0;
        int countAll = 0;

        for (NodeCVSummary s : nodeSummaries) {
            if (!Double.isFinite(s.oosMse) && !Double.isFinite(s.oosXent)) {
                continue; // root — skip
            }
            countAll++;

            if (!s.discreteChild && Double.isFinite(s.oosR2)) {
                sumR2 += s.oosR2;
                countR2++;
                if (s.oosR2 > 0) beaten++;
            } else if (s.discreteChild && Double.isFinite(s.oosXent)
                    && Double.isFinite(s.baselineXent)) {
                double imp = s.baselineXent - s.oosXent;
                sumXentImp += imp;
                countXent++;
                if (imp > 0) beaten++;
            }
        }

        this.meanOosR2 = (countR2 > 0) ? sumR2 / countR2 : Double.NaN;
        this.meanOosXentImprovement = (countXent > 0) ? sumXentImp / countXent : Double.NaN;
        this.fracNodesBeatBaseline = (countAll > 0) ? beaten / (double) countAll : Double.NaN;
    }

    // ── display ───────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable multi-line summary of the CV results.
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== NNEstimator Cross-Validation Report (k=").append(numFolds).append(") ===\n\n");

        sb.append(String.format("Whole-graph OOS MMD²        : %.6f%n", meanOosMmd2));

        if (Double.isFinite(meanOosR2)) {
            sb.append(String.format("Mean OOS R² (continuous)    : %.4f%n", meanOosR2));
        }
        if (Double.isFinite(meanOosXentImprovement)) {
            sb.append(String.format("Mean OOS Xent improvement   : %.4f%n", meanOosXentImprovement));
        }
        if (Double.isFinite(fracNodesBeatBaseline)) {
            sb.append(String.format("Nodes beating baseline      : %.0f%%%n",
                    fracNodesBeatBaseline * 100.0));
        }

        sb.append("\nPer-node results:\n");
        for (NodeCVSummary s : nodeSummaries) {
            sb.append("  ").append(s).append("\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toText();
    }

    /**
     * Returns a compact one-line summary suitable for a status bar.
     */
//    public String toStatusLine() {
//        return String.format(
//                "CV k=%d  |  OOS MMD²=%.4f  |  Mean R²=%.4f  |  Nodes beaten=%.0f%%",
//                numFolds,
//                meanOosMmd2,
//                Double.isFinite(meanOosR2) ? meanOosR2 : Double.NaN,
//                Double.isFinite(fracNodesBeatBaseline) ? fracNodesBeatBaseline * 100.0 : Double.NaN);
//    }

    /**
     * Generates a single-line summary of the cross-validation (CV) results for
     * the current instance, including details such as the number of CV folds,
     * mean out-of-sample squared maximum mean discrepancy (MMD²), mean out-of-sample
     * R², and the count of nodes that improved over the baseline.
     *
     * @return A formatted string summarizing the CV results, including the number
     *         of folds, mean OOS MMD², mean R², and the proportion of nodes that
     *         beat the baseline.
     */
    public String toStatusLine() {
        // Count how many non-root nodes beat the baseline.
        int beaten = 0;
        int total  = 0;
        for (NodeCVSummary s : nodeSummaries) {
            total++;
            if (!s.discreteChild && Double.isFinite(s.oosR2) && s.oosR2 > 0) beaten++;
            else if (s.discreteChild && Double.isFinite(s.oosXent)
                    && Double.isFinite(s.baselineXent)
                    && s.baselineXent - s.oosXent > 0) beaten++;
        }

        return String.format(
                "CV k=%d  |  OOS MMD²=%.4f  |  Mean R²=%.4f  |  Nodes beaten=%d/%d",
                numFolds,
                meanOosMmd2,
                Double.isFinite(meanOosR2) ? meanOosR2 : Double.NaN,
                beaten,
                total);
    }
}
