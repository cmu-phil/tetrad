///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data.missing;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out what dropping every variable whose missingness rate exceeds a threshold would do to a dataset,
 * without dropping anything. Supports a preview-then-act control: the useful threshold is the one where the
 * complete-case count turns, and that is data-specific, so it has to be looked at rather than assumed.
 *
 * <h2>What this remedy is and is not</h2>
 *
 * <p>Dropping a high-missingness column is decided on the missingness indicator alone, never on the values,
 * so under MCAR or MAR it does not bias what remains -- unlike dropping rows, which under MAR does. When a
 * column is MNAR, dropping it also removes the bias it would have contributed to its own family.</p>
 *
 * <p>The cost is specific to structure learning and is not small. Removing a variable that is a common cause
 * of two retained variables turns a causally sufficient system into an insufficient one: the confounding is
 * manufactured by the remedy, and no search run afterwards can tell. The variables most likely to exceed a
 * missingness threshold -- hardest to measure, most often skipped -- are not a random sample with respect to
 * being upstream of others. {@link Candidate#maxAbsCorrelation()} is reported for exactly this reason: a
 * high-missingness variable strongly associated with several retained ones is the dangerous case.</p>
 *
 * <p>Thresholds have no theoretical basis. Twenty percent and fifty percent are conventions, not results.</p>
 *
 * @author josephramsey
 * @see MissingDataAudit
 */
public final class MissingnessThreshold {

    private MissingnessThreshold() {
    }

    /**
     * A variable that a threshold would drop, with what is needed to judge whether dropping it is safe.
     *
     * @param name              The variable's name.
     * @param missingRate       Its missingness rate.
     * @param maxAbsCorrelation The largest absolute pairwise correlation between this variable and any variable
     *                          the threshold would retain, computed on their pairwise-complete cases; NaN when
     *                          no pair has enough overlap to compute one. High values are the warning: this
     *                          variable carries information about the retained system, so removing it may
     *                          confound what is left.
     * @param maxCorrelatedWith The retained variable achieving that correlation, or null when none.
     * @param pairwiseN         The pairwise-complete count behind that correlation, or 0 when none.
     */
    public record Candidate(String name, double missingRate, double maxAbsCorrelation,
                            String maxCorrelatedWith, int pairwiseN) {
    }

    /**
     * The consequence of applying one threshold.
     *
     * @param threshold        The threshold applied; variables with a rate strictly above it are dropped.
     * @param dropped          The variables that would be dropped, worst rate first.
     * @param retained         The count that would remain.
     * @param completeRows     Cases complete in every retained variable.
     * @param minPairwiseCount The smallest pairwise-complete count among retained pairs, or the row count when
     *                         fewer than two variables remain.
     */
    public record Effect(double threshold, List<Candidate> dropped, int retained, int completeRows,
                         int minPairwiseCount) {
    }

    /**
     * The effect of a single threshold.
     *
     * @param dataSet   The data. Not modified.
     * @param audit     An audit of that data, for the per-variable rates.
     * @param threshold Variables with a missingness rate strictly above this are dropped. Must be in [0, 1].
     * @return The effect.
     * @throws IllegalArgumentException if the threshold is out of range.
     */
    public static Effect effectOf(DataSet dataSet, MissingDataAudit audit, double threshold) {
        if (!(threshold >= 0.0 && threshold <= 1.0)) {
            throw new IllegalArgumentException("Threshold must be in [0, 1] but was " + threshold + ".");
        }

        int p = dataSet.getNumColumns();

        List<Integer> dropIdx = new ArrayList<>();
        List<Integer> keepIdx = new ArrayList<>();

        for (int j = 0; j < p; j++) {
            if (audit.getMissingRate(j) > threshold) dropIdx.add(j);
            else keepIdx.add(j);
        }

        // Worst rate first: the order the user will want to read, and the order in which removal is least
        // arguable.
        dropIdx.sort((a, b) -> Double.compare(audit.getMissingRate(b), audit.getMissingRate(a)));

        List<Candidate> dropped = new ArrayList<>(dropIdx.size());

        for (int j : dropIdx) {
            double best = Double.NaN;
            String bestName = null;
            int bestN = 0;

            for (int k : keepIdx) {
                int n = pairwiseCompleteCount(dataSet, j, k);
                if (n < MIN_PAIRS_FOR_CORRELATION) continue;

                double r = Math.abs(pairwiseCorrelation(dataSet, j, k));
                if (!Double.isFinite(r)) continue;

                if (Double.isNaN(best) || r > best) {
                    best = r;
                    bestName = dataSet.getVariable(k).getName();
                    bestN = n;
                }
            }

            dropped.add(new Candidate(dataSet.getVariable(j).getName(), audit.getMissingRate(j),
                    best, bestName, bestN));
        }

        int completeRows = completeRows(dataSet, keepIdx);
        int minPairwise = minPairwise(dataSet, keepIdx);

        return new Effect(threshold, dropped, keepIdx.size(), completeRows, minPairwise);
    }

    /**
     * The effect of each of a series of thresholds, for reading off where the complete-case count turns.
     *
     * @param dataSet    The data. Not modified.
     * @param audit      An audit of that data.
     * @param thresholds The thresholds to evaluate.
     * @return One effect per threshold, in the order given.
     */
    public static List<Effect> sweep(DataSet dataSet, MissingDataAudit audit, double... thresholds) {
        List<Effect> effects = new ArrayList<>(thresholds.length);
        for (double t : thresholds) effects.add(effectOf(dataSet, audit, t));
        return effects;
    }

    /**
     * The distinct missingness rates present in the data, ascending, as the thresholds worth sweeping: the
     * dropped set only changes as the threshold crosses one of these, so nothing between them is informative.
     *
     * @param audit An audit of the data.
     * @param p     The number of columns.
     * @return The candidate thresholds.
     */
    public static double[] candidateThresholds(MissingDataAudit audit, int p) {
        Set<Double> rates = new LinkedHashSet<>();
        rates.add(0.0);
        for (int j = 0; j < p; j++) rates.add(audit.getMissingRate(j));

        double[] sorted = rates.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return sorted;
    }

    /**
     * Minimum pairwise-complete count needed before a correlation is reported at all. Below this the estimate
     * is too noisy to base a removal decision on, and NaN is the honest answer.
     */
    private static final int MIN_PAIRS_FOR_CORRELATION = 10;

    private static int completeRows(DataSet dataSet, List<Integer> keep) {
        int count = 0;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            boolean ok = true;

            for (int j : keep) {
                if (MissingDataAudit.isMissing(dataSet, i, j)) { ok = false; break; }
            }

            if (ok) count++;
        }

        return count;
    }

    private static int minPairwise(DataSet dataSet, List<Integer> keep) {
        if (keep.size() < 2) return dataSet.getNumRows();

        int min = dataSet.getNumRows();

        for (int a = 0; a < keep.size(); a++) {
            for (int b = a + 1; b < keep.size(); b++) {
                min = Math.min(min, pairwiseCompleteCount(dataSet, keep.get(a), keep.get(b)));
            }
        }

        return min;
    }

    private static int pairwiseCompleteCount(DataSet dataSet, int j, int k) {
        int count = 0;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            if (!MissingDataAudit.isMissing(dataSet, i, j) && !MissingDataAudit.isMissing(dataSet, i, k)) count++;
        }

        return count;
    }

    /**
     * Pearson correlation on the pairwise-complete cases, reading discrete columns by their integer codes. For a
     * discrete variable that is a bare label this is not a meaningful association measure; it is a screening
     * number for a warning column, not an analysis.
     */
    private static double pairwiseCorrelation(DataSet dataSet, int j, int k) {
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        int n = 0;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            if (MissingDataAudit.isMissing(dataSet, i, j) || MissingDataAudit.isMissing(dataSet, i, k)) continue;

            double x = dataSet.getDouble(i, j);
            double y = dataSet.getDouble(i, k);
            if (!Double.isFinite(x) || !Double.isFinite(y)) continue;

            sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y;
            n++;
        }

        if (n < MIN_PAIRS_FOR_CORRELATION) return Double.NaN;

        double cov = sxy / n - (sx / n) * (sy / n);
        double vx = sxx / n - (sx / n) * (sx / n);
        double vy = syy / n - (sy / n) * (sy / n);

        if (!(vx > 0) || !(vy > 0)) return Double.NaN;

        return cov / Math.sqrt(vx * vy);
    }

    /**
     * The names a threshold would drop, in the order {@link #effectOf} reports them.
     *
     * @param effect An effect.
     * @return The names.
     */
    public static List<String> droppedNames(Effect effect) {
        List<String> names = new ArrayList<>(effect.dropped().size());
        for (Candidate c : effect.dropped()) names.add(c.name());
        return names;
    }

    /**
     * Whether a dropped variable looks risky to drop: strongly associated with something retained, on enough
     * pairwise cases to believe it.
     *
     * @param candidate A candidate.
     * @return True when the association is at or above 0.5.
     */
    public static boolean isConfoundingRisk(Candidate candidate) {
        return Double.isFinite(candidate.maxAbsCorrelation()) && candidate.maxAbsCorrelation() >= 0.5;
    }

    /**
     * Resolves the dropped names to the dataset's variables, skipping any that are no longer present.
     *
     * @param dataSet The data.
     * @param names   The names.
     * @return The variables.
     */
    public static List<Node> resolve(DataSet dataSet, List<String> names) {
        List<Node> nodes = new ArrayList<>(names.size());

        for (String name : names) {
            Node v = dataSet.getVariable(name);
            if (v != null) nodes.add(v);
        }

        return nodes;
    }
}
