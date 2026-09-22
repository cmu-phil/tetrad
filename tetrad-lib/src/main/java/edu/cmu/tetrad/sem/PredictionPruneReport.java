package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of {@link NNEstimator#pruneByPredictiveContribution(int, double)}:
 * a per-child backward elimination of parents whose held-out predictive
 * contribution is not distinguishable from zero.
 *
 * <p>For each candidate parent X of a child Y, the current model (Y's
 * mechanism with the current working parent set) and a reduced model
 * (retrained without X) both predict the held-out rows of the same k folds
 * used by {@link NNEstimator#crossValidate(int)}. This yields one paired
 * improvement per fold: ΔR² for a continuous child, Δ cross-entropy in nats
 * for a discrete one. A parent is kept when the fold-mean improvement
 * exceeds {@code threshold} standard errors of the fold spread. While any
 * parent fails, the one with the smallest t-statistic is removed, the
 * child's fold mechanisms are retrained on the surviving parents, and the
 * survivors are re-tested — so a redundant pair loses at most one member.
 *
 * <p>Deletion means "adds no unique out-of-sample predictive information
 * given the surviving parents," which is a statement about predictive
 * redundancy, not causal absence. The proposed graph should be reviewed,
 * not silently adopted; and any metrics computed after re-estimation on the
 * same folds are optimistic, because those folds were consumed by the
 * selection.
 */
public final class PredictionPruneReport implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * One proposed edge deletion.
     */
    public static final class Deletion implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Name of the parent variable (tail of the deleted edge). */
        public final String parentName;

        /** Name of the child variable (head of the deleted edge). */
        public final String childName;

        /** {@code true} if the child variable is discrete. */
        public final boolean discreteChild;

        /**
         * Fold-mean paired improvement from this parent at the time it was
         * removed: ΔR² for a continuous child, Δ cross-entropy in nats for a
         * discrete child.
         */
        public final double meanImprovement;

        /** Standard error of the improvement across folds. */
        public final double seImprovement;

        /** {@code meanImprovement / seImprovement}; NaN when SE is zero. */
        public final double tStat;

        /** Elimination round within this child at which the edge fell (0-based). */
        public final int step;

        Deletion(String parentName, String childName, boolean discreteChild,
                 double meanImprovement, double seImprovement, double tStat,
                 int step) {
            this.parentName      = parentName;
            this.childName       = childName;
            this.discreteChild   = discreteChild;
            this.meanImprovement = meanImprovement;
            this.seImprovement   = seImprovement;
            this.tStat           = tStat;
            this.step            = step;
        }
    }

    /**
     * The deletions.
     */
    private final List<Deletion> deletions;
    /**
     * The pruned graph.
     */
    private final Graph prunedGraph;
    /**
     * The number of folds used in the cross-validation.
     */
    private final int numFolds;
    /**
     * The threshold used to determine whether an edge should be deleted.
     */
    private final double threshold;

    PredictionPruneReport(List<Deletion> deletions, Graph prunedGraph,
                          int numFolds, double threshold) {
        this.deletions   = new ArrayList<>(deletions);
        this.prunedGraph = prunedGraph;
        this.numFolds    = numFolds;
        this.threshold   = threshold;
    }

    /**
     * The proposed deletions, sorted by child name and elimination step.
     * @return an unmodifiable list of proposed deletions
     */
    public List<Deletion> getDeletions() {
        return Collections.unmodifiableList(deletions);
    }

    /**
     * A copy of the input DAG with the proposed deletions removed.
     * @return the pruned graph
     */
    public Graph getPrunedGraph() {
        return prunedGraph;
    }

    /**
     * The number of CV folds used.
     * @return the number of folds
     */
    public int getNumFolds() {
        return numFolds;
    }

    /**
     * The keep threshold in standard errors.
     * @return the threshold
     */
    public double getThreshold() {
        return threshold;
    }

    /**
     * A human-readable summary, one line per proposed deletion.
     * @return a human-readable summary
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "Prediction-based pruning: %d edge(s) proposed for deletion "
                + "(k=%d, keep if mean > %.2f SE).%n",
                deletions.size(), numFolds, threshold));
        for (Deletion d : deletions) {
            sb.append(String.format(
                    "  %s \u2192 %s  |  \u0394 = %.4f  SE = %.4f  t = %.2f  "
                    + "(%s, step %d)%n",
                    d.parentName, d.childName, d.meanImprovement,
                    d.seImprovement, d.tStat,
                    d.discreteChild ? "xent, nats" : "\u0394R\u00b2",
                    d.step));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toText();
    }
}
