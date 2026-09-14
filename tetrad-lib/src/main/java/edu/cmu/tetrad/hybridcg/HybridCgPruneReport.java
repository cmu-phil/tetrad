///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of {@link HybridCgEdgeSignificance#backwardPrune}: a per-child backward elimination of edges whose per-edge
 * likelihood-ratio test could not distinguish the reduced local model from the full one at the given level.
 * <p>
 * Each {@link Deletion} records the test values <i>at the time the edge was removed</i>. Because every removal
 * changes the families the surviving edges are re-tested against, and because the removals themselves were chosen by
 * looking at the data, these recorded values are not valid p-values for the final graph — no p-value computed after
 * data-dependent selection is. Likewise, edges the data could never test (0 identifiable degrees of freedom) are
 * kept, not removed, and listed here: an untestable edge is one the data are silent about, which is not evidence of
 * absence. The proposed graph should be reviewed against domain knowledge, not silently adopted, and any fit
 * statistics recomputed on the same data after adopting it are optimistic.
 */
public final class HybridCgPruneReport implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * One proposed edge deletion.
     */
    public static final class Deletion implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Name of the parent variable (tail of the deleted edge). */
        private final String parentName;

        /** Name of the child variable (head of the deleted edge). */
        private final String childName;

        /** The LRT p-value at the time of removal. */
        private final double pValue;

        /** The LRT statistic at the time of removal. */
        private final double statistic;

        /** The sparse-adjusted degrees of freedom at the time of removal. */
        private final int df;

        /** The available-case count of the family the test was run on. */
        private final int n;

        /** Elimination round within this child at which the edge fell (0-based). */
        private final int step;

        /**
         * Constructs a deletion record.
         *
         * @param parentName name of the parent variable
         * @param childName  name of the child variable
         * @param pValue     the LRT p-value at removal
         * @param statistic  the LRT statistic at removal
         * @param df         the degrees of freedom at removal
         * @param n          the available-case count of the tested family
         * @param step       the elimination round within the child, 0-based
         */
        public Deletion(String parentName, String childName, double pValue, double statistic, int df, int n,
                        int step) {
            this.parentName = parentName;
            this.childName = childName;
            this.pValue = pValue;
            this.statistic = statistic;
            this.df = df;
            this.n = n;
            this.step = step;
        }

        /**
         * Name of the parent variable (tail of the deleted edge).
         *
         * @return the parent name
         */
        public String getParentName() {
            return parentName;
        }

        /**
         * Name of the child variable (head of the deleted edge).
         *
         * @return the child name
         */
        public String getChildName() {
            return childName;
        }

        /**
         * The LRT p-value at the time of removal; not a valid p-value for the final graph (see class Javadoc).
         *
         * @return the p-value at removal
         */
        public double getPValue() {
            return pValue;
        }

        /**
         * The LRT statistic at the time of removal.
         *
         * @return the statistic
         */
        public double getStatistic() {
            return statistic;
        }

        /**
         * The sparse-adjusted degrees of freedom at the time of removal.
         *
         * @return the degrees of freedom
         */
        public int getDf() {
            return df;
        }

        /**
         * The available-case count of the family the test was run on.
         *
         * @return the case count
         */
        public int getN() {
            return n;
        }

        /**
         * The elimination round within this child at which the edge fell, 0-based.
         *
         * @return the round
         */
        public int getStep() {
            return step;
        }

        @Override
        public String toString() {
            return String.format("%s -> %s removed at step %d: p = %.4g, G = %.4g, df = %d, n = %d",
                    parentName, childName, step, pValue, statistic, df, n);
        }
    }

    /**
     * The list of edges that were deleted.
     */
    private final List<Deletion> deletions;
    /**
     * The list of edges that were not tested.
     */
    private final List<String> untested;
    /**
     * The pruned graph.
     */
    private final Graph prunedGraph;
    /**
     * The significance level the elimination used.
     */
    private final double alpha;
    /**
     * Indicates whether continuous-child fits shared a single residual variance
     * across strata in the context of graph pruning and statistical testing.
     *
     * This value is determined during the construction of a Prune Report and
     * reflects if the shared variance assumption was used when fitting continuous
     * child variables in the model.
     */
    private final boolean shareVariance;

    /**
     * Constructs a prune report.
     *
     * @param deletions     the proposed deletions, in the order they were made
     * @param untested      descriptions of edges that were kept because they were never testable on this data
     * @param prunedGraph   the proposed graph: the input graph minus the deleted edges
     * @param alpha         the significance level the elimination used
     * @param shareVariance whether continuous-child fits shared one residual variance across strata
     */
    public HybridCgPruneReport(List<Deletion> deletions, List<String> untested, Graph prunedGraph, double alpha,
                               boolean shareVariance) {
        this.deletions = new ArrayList<>(deletions);
        this.untested = new ArrayList<>(untested);
        this.prunedGraph = prunedGraph;
        this.alpha = alpha;
        this.shareVariance = shareVariance;
    }

    /**
     * The proposed deletions, in the order they were made.
     *
     * @return an unmodifiable list of deletions
     */
    public List<Deletion> getDeletions() {
        return Collections.unmodifiableList(deletions);
    }

    /**
     * Descriptions of edges that were kept because they were never testable on this data.
     *
     * @return an unmodifiable list of descriptions
     */
    public List<String> getUntested() {
        return Collections.unmodifiableList(untested);
    }

    /**
     * The proposed graph: the input graph minus the deleted edges.
     *
     * @return the pruned graph
     */
    public Graph getPrunedGraph() {
        return prunedGraph;
    }

    /**
     * The significance level the elimination used.
     *
     * @return alpha
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Whether continuous-child fits shared one residual variance across strata.
     *
     * @return true if variance was shared
     */
    public boolean isShareVariance() {
        return shareVariance;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Hybrid CG prune proposal (backward elimination by per-edge LRT, alpha = ")
                .append(alpha).append(shareVariance ? ", shared variance" : "").append(")\n");
        if (deletions.isEmpty()) {
            sb.append("  No edges proposed for removal.\n");
        } else {
            sb.append("  Proposed removals (values are as of each removal; not valid p-values after selection):\n");
            for (Deletion d : deletions) sb.append("    ").append(d).append('\n');
        }
        if (!untested.isEmpty()) {
            sb.append("  Kept without a verdict (never testable on this data):\n");
            for (String u : untested) sb.append("    ").append(u).append('\n');
        }
        sb.append("  Review before adopting; fit statistics recomputed on the same data afterward are optimistic.");
        return sb.toString();
    }
}
