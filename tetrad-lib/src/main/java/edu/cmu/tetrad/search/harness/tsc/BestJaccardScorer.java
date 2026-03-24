///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsc;

import java.util.Collection;
import java.util.Set;

/**
 * Computes <em>best-Jaccard</em> precision and recall between a set of true clusters
 * and a set of recovered clusters.
 *
 * <h2>Metric definition</h2>
 *
 * <p>Let {@code T = {T_1, ..., T_k}} be the true clusters and
 * {@code R = {R_1, ..., R_m}} be the recovered clusters.  The Jaccard similarity
 * between two sets is
 * <pre>
 *   J(A, B) = |A ∩ B| / |A ∪ B|  ∈ [0, 1]
 * </pre>
 *
 * <p><b>Recall</b> measures how well each true cluster is captured by the best
 * matching recovered cluster:
 * <pre>
 *   Recall = (1/k) Σ_{i=1}^{k}  max_{j} J(T_i, R_j)
 * </pre>
 * If {@code R} is empty, recall is 0.
 *
 * <p><b>Precision</b> measures how well each recovered cluster corresponds to the
 * best matching true cluster:
 * <pre>
 *   Precision = (1/m) Σ_{j=1}^{m}  max_{i} J(T_i, R_j)
 * </pre>
 * If {@code R} is empty, precision is defined as 1 (no spurious clusters were
 * returned).  Callers may prefer to treat empty {@code R} as a special case;
 * see {@link ClusterScore#recoveredCount()}.
 *
 * <h2>Edge cases</h2>
 * <ul>
 *   <li>Empty true clusters: both precision and recall are {@code NaN}.</li>
 *   <li>Empty recovered clusters: recall = 0, precision = 1 (by convention).</li>
 *   <li>Perfect recovery: both = 1.0.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   List<Set<Node>> trueClusters  = TrueClusterExtractor.extractClusters(graph);
 *   List<Set<Node>> recovered     = runAlgorithm(...);
 *   ClusterScore    score         = BestJaccardScorer.score(trueClusters, recovered);
 *   System.out.println("P=" + score.precision() + "  R=" + score.recall());
 * }</pre>
 *
 * @author josephramsey
 */
public final class BestJaccardScorer {

    private BestJaccardScorer() {
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Computes the best-Jaccard precision and recall.
     *
     * @param <T>          element type (typically {@link edu.cmu.tetrad.graph.Node}
     *                     or {@link String})
     * @param trueClusters the ground-truth partition; must not be {@code null}.
     * @param recovered    the clusters returned by an algorithm; must not be {@code null}.
     * @return a {@link ClusterScore} containing precision, recall, and counts.
     */
    public static <T> ClusterScore score(
            Collection<? extends Set<T>> trueClusters,
            Collection<? extends Set<T>> recovered) {

        if (trueClusters == null) throw new IllegalArgumentException("trueClusters must not be null.");
        if (recovered    == null) throw new IllegalArgumentException("recovered must not be null.");

        int k = trueClusters.size();
        int m = recovered.size();

        if (k == 0) {
            // No ground truth: scores are undefined.
            return new ClusterScore(Double.NaN, Double.NaN, k, m);
        }

        if (m == 0) {
            // No clusters recovered: recall = 0, precision = 1 by convention.
            return new ClusterScore(1.0, 0.0, k, m);
        }

        // Pre-box collections to arrays for indexed access
        @SuppressWarnings("unchecked")
        Set<T>[] trueArr = trueClusters.toArray(new Set[0]);
        @SuppressWarnings("unchecked")
        Set<T>[] recArr  = recovered.toArray(new Set[0]);

        // ---- Recall: for each true cluster find its best recovered match ----
        double recallSum = 0.0;
        for (Set<T> t : trueArr) {
            double best = 0.0;
            for (Set<T> r : recArr) {
                double j = jaccard(t, r);
                if (j > best) best = j;
            }
            recallSum += best;
        }
        double recall = recallSum / k;

        // ---- Precision: for each recovered cluster find its best true match ----
        double precisionSum = 0.0;
        for (Set<T> r : recArr) {
            double best = 0.0;
            for (Set<T> t : trueArr) {
                double j = jaccard(t, r);
                if (j > best) best = j;
            }
            precisionSum += best;
        }
        double precision = precisionSum / m;

        return new ClusterScore(precision, recall, k, m);
    }

    /**
     * Computes the Jaccard similarity between two sets.
     *
     * @param a first set; must not be {@code null}.
     * @param b second set; must not be {@code null}.
     * @return {@code |a ∩ b| / |a ∪ b|}, or {@code 0.0} if both sets are empty.
     */
    public static <T> double jaccard(Set<T> a, Set<T> b) {
        if (a == null || b == null) throw new IllegalArgumentException("Sets must not be null.");
        if (a.isEmpty() && b.isEmpty()) return 0.0;

        int intersection = 0;
        // Iterate over the smaller set for efficiency
        Set<T> smaller = (a.size() <= b.size()) ? a : b;
        Set<T> larger  = (a.size() <= b.size()) ? b : a;
        for (T element : smaller) {
            if (larger.contains(element)) intersection++;
        }
        int union = a.size() + b.size() - intersection;
        return (union == 0) ? 0.0 : (double) intersection / union;
    }

    // -----------------------------------------------------------------------
    // Result record
    // -----------------------------------------------------------------------

    /**
     * Immutable result of a best-Jaccard evaluation.
     *
     * @param precision      mean best-Jaccard precision in [0, 1], or {@code NaN}
     *                       if no ground-truth clusters exist.
     * @param recall         mean best-Jaccard recall in [0, 1], or {@code NaN}
     *                       if no ground-truth clusters exist.
     * @param trueCount      number of true clusters.
     * @param recoveredCount number of recovered clusters (0 means the algorithm
     *                       found nothing).
     */
    public record ClusterScore(
            double precision,
            double recall,
            int    trueCount,
            int    recoveredCount) {

        /**
         * Returns {@code true} if the algorithm returned no clusters at all.
         * This is a distinct failure mode from returning wrong clusters and
         * should be tracked separately in simulation summaries.
         */
        public boolean isEmpty() {
            return recoveredCount == 0;
        }

        /**
         * Returns the F1 score (harmonic mean of precision and recall), or
         * {@code NaN} if either component is {@code NaN} or both are zero.
         */
        public double f1() {
            if (Double.isNaN(precision) || Double.isNaN(recall)) return Double.NaN;
            double denom = precision + recall;
            return (denom == 0.0) ? 0.0 : 2.0 * precision * recall / denom;
        }

        @Override
        public String toString() {
            return String.format("ClusterScore{P=%.4f, R=%.4f, F1=%.4f, true=%d, recovered=%d}",
                    precision, recall, f1(), trueCount, recoveredCount);
        }
    }
}
