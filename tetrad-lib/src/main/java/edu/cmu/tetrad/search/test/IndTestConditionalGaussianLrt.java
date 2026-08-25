/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.ConditionalGaussianLikelihood;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.special.Gamma;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Performs a test of conditional independence X _||_ Y | Z1...Zn where all variables are either continuous or
 * discrete. This test is valid for both ordinal and non-ordinal discrete variables.
 * <p>
 * Assumes a conditional Gaussian model and uses a likelihood ratio test.
 *
 * Drop-in replacement note:
 * - setRows(...) now respects the provided argument (no random subsampling).
 * - Row restriction is intersected with testwise deletion for the specific (x, y | z) variables.
 * - Caches are cleared when row restrictions or discretization knobs change.
 * <p>
 * Effective sample size (added 2026-8-25). When an effective sample size nEff is set (see
 * {@link #setEffectiveSampleSize(int)}), the likelihood-ratio statistic 2 * (l1 - l0) is scaled by r = nEff / N,
 * where N is the number of rows in the dataset, before being referred to the chi-square distribution. The
 * degrees of freedom are unchanged. Since the log-likelihoods are row sums, this is the statistic that nEff
 * independent rows would have produced. The value is relative to the full dataset: if {@link #setRows(List)}
 * restricts the test to m rows, or testwise deletion drops rows for a particular (x, y | z), the test behaves as if
 * it had r * m independent rows, so a subsample or resample inherits the same per-row information content rather
 * than the full nEff. (This differs from {@link IndTestDegenerateGaussianLrt}, which resets nEff to the actual row
 * count on setRows, and from {@link IndTestFisherZ}, which keeps the absolute nEff regardless of setRows.) With
 * nEff unset, r = 1 and the test is unchanged from the previous implementation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IndTestConditionalGaussianLrt implements IndependenceTest, RowsSettable, EffectiveSampleSizeSettable {

    /** The data set. */
    private final DataSet data;

    /** A hash of nodes to indices. */
    private final Map<Node, Integer> nodesHash;

    /** Likelihood function. */
    private final ConditionalGaussianLikelihood likelihood;

    /** The significance level of the independence tests. */
    private double alpha;

    /** True if verbose output should be printed. */
    private boolean verbose;

    /** The number of categories to discretize continuous variables into. */
    private int numCategoriesToDiscretize = 3;

    /** The minimum sample size per cell for discretization. */
    private int minSampleSizePerCell = 4;

    /**
     * Optional user-specified row restriction (indices into the original DataSet).
     * If null, all rows are eligible (subject to testwise deletion).
     */
    private List<Integer> rows = null;

    /** The most recent p-value. */
    private double pValue;

    // Effective sample size relative to the full dataset, or -1 to use the actual row count. See class Javadoc.
    private int nEff = -1;

    /**
     * Constructor.
     *
     * @param data       The data to analyze.
     * @param alpha      The significance level.
     * @param discretize Whether discrete children of continuous parents should be discretized.
     */
    public IndTestConditionalGaussianLrt(DataSet data, double alpha, boolean discretize) {
        this.data = data;
        this.likelihood = new ConditionalGaussianLikelihood(data);
        this.likelihood.setDiscretize(discretize);

        this.nodesHash = new HashMap<>();
        List<Node> variables = data.getVariables();
        for (int i = 0; i < variables.size(); i++) {
            this.nodesHash.put(variables.get(i), i);
        }

        this.alpha = alpha;
    }

    /**
     * This method returns an instance of the IndependenceTest interface that can test the independence of a subset of
     * variables.
     *
     * @param vars The sublist of variables to test for independence.
     * @return An instance of the IndependenceTest interface.
     * @see IndependenceTest
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Returns an independence result that states whether x _||_ y | z and what the p-value of the test is.
     *
     * @param x  a {@link edu.cmu.tetrad.graph.Node} object
     * @param y  a {@link edu.cmu.tetrad.graph.Node} object
     * @param _z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        // Normalize key: same (x,y|z) fact should map consistently.
        IndependenceFact fact = new IndependenceFact(x, y, _z);

        this.likelihood.setNumCategoriesToDiscretize(this.numCategoriesToDiscretize);
        this.likelihood.setMinSampleSizePerCell(this.minSampleSizePerCell);

        List<Node> z = new ArrayList<>(_z);
        z.sort(NaturalSort.naturalComparator());;

        List<Node> allVars = new ArrayList<>(z.size() + 2);
        allVars.addAll(z);
        allVars.add(x);
        allVars.add(y);

        // Row restriction + testwise deletion (for these vars). Rows are passed explicitly to the
        // likelihood-ratio call below rather than via likelihood.setRows: checkIndependence runs
        // concurrently under the parallelized Markov checker and parallel constraint-based
        // searches, and setRows mutated state shared by all threads, silently mixing supports
        // whenever testwise deletion made them differ (i.e., under missing data). Changed 2026-8-12.
        List<Integer> testRows = getRows(allVars, this.nodesHash);

        // Changes from the pre-2026-8 computation: for a mixed pair, the CONTINUOUS member is
        // always the modeled target, regardless of argument order. Modeling the discrete member
        // as the target sends continuous CONDITIONING variables through the discretize path, and
        // coarsening a conditioning variable destroys a true null (x _||_ y | z does not imply
        // x _||_ y | discretize(z)): in the calibration harness, Markov-check facts of the form
        // disc _||_ cont | cont, disc were rejected at 44-52% (n = 1500, nominal 5%) under the
        // old orientation and at ~5% under this one. This also makes mixed-pair tests symmetric
        // in argument order. For pairs where both members are discrete and the conditioning set
        // contains continuous variables, the discretize caveat remains: coarsened conditioning
        // can reject a true null in principle, and no orientation avoids it in the CG family.
        Node target = y, other = x;
        if (x instanceof ContinuousVariable && y instanceof DiscreteVariable) {
            target = x;
            other = y;
        }

        int _x = this.nodesHash.get(other);
        int _y = this.nodesHash.get(target);

        int[] list0 = new int[z.size()];
        int[] list1 = new int[z.size() + 1];

        list1[0] = _x;

        for (int i = 0; i < z.size(); i++) {
            int __z = this.nodesHash.get(z.get(i));
            list0[i] = __z;
            list1[i + 1] = __z;
        }

        ConditionalGaussianLikelihood.Ret ret = likelihood.getLikelihoodRatio(_y, list0, list1, testRows);

        double lik_diff = ret.getLik();
        int dof_diff = ret.getDof();

        double pValue;

        if (Double.isNaN(lik_diff)) {
            // No evaluable common support (every eligibility cell was too small or degenerate),
            // or a residual numerical breakdown past the degeneracy screen. Before 2026-8-12
            // this threw, which aborted entire bootstrap searches whenever one resample produced
            // a degenerate cell; a defined answer is required for search robustness. No
            // estimable evidence against independence on the evaluable support: p = 1, matching
            // the dof_diff <= 0 convention below.
            pValue = 1.0;
        } else {
            // Changes from the pre-2026-8 computation: the statistic now comes from
            // ConditionalGaussianLikelihood.getLikelihoodRatio, which fits both models on a
            // COMMON row support with dof counted over observed cells (see its Javadoc for the
            // calibration failures this repairs). dof_diff <= 0 means the larger model added
            // nothing estimable on the common support (e.g., x constant there): no evidence
            // against independence, p = 1. x1 <= 0 covers roundoff on a true nested ratio. The
            // upper tail uses regularizedGammaQ for numerical stability.
            // Effective-sample-size scaling of the row-sum statistic (r = 1 when nEff is unset).
            double x1 = 2 * lik_diff * effectiveSampleSizeRatio();

            if (dof_diff <= 0) {
                pValue = 1.0;
            } else if (x1 <= 0.0) {
                pValue = 1.0;
            } else if (Double.isInfinite(x1)) {
                pValue = 0.0;
            } else {
                pValue = Gamma.regularizedGammaQ(0.5 * dof_diff, 0.5 * x1);
            }
        }

        this.pValue = pValue;

        boolean independent = pValue > alpha;

        if (this.verbose && independent) {
            TetradLogger.getInstance().log(
                    LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
        }

        return new IndependenceResult(fact, independent, pValue, getAlpha() - pValue);
    }

    /**
     * Returns the probability associated with the most recently executed independence test, or Double.NaN if p value is
     * not meaningful for this test.
     *
     * @return This p-value.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determining independence
     * relations.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(this.data.getVariables());
    }

    /**
     * Determines whether a given list of nodes (z) determines a node (y).
     *
     * @param z The list of nodes to check if they determine y.
     * @param y The node to check if it is determined by z.
     * @return True if z determines y, false otherwise.
     */
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("Determinism method not implemented.");
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return This level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha must be between 0.0 and 1.0.");
        }
        this.alpha = alpha;
    }

    /**
     * Returns the data.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.data;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Conditional Gaussian LRT, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns true iff verbose output should be printed.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of categories used to discretize variables.
     *
     * @param numCategoriesToDiscretize This number, by default 3.
     */
    public void setNumCategoriesToDiscretize(int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    /**
     * Sets the minimum sample size per cell for the independence test.
     *
     * @param minSampleSizePerCell The minimum sample size per cell.
     */
    public void setMinSampleSizePerCell(int minSampleSizePerCell) {
        this.minSampleSizePerCell = minSampleSizePerCell;
    }

    /**
     * Returns the effective sample size: the value set via {@link #setEffectiveSampleSize(int)}, or, if none has
     * been set, the number of rows currently in use (the restriction from {@link #setRows(List)} if any, otherwise
     * the full dataset).
     *
     * @return the effective sample size
     */
    @Override
    public int getEffectiveSampleSize() {
        if (this.nEff >= 0) return this.nEff;
        return this.rows != null ? this.rows.size() : this.data.getNumRows();
    }

    /**
     * Sets the effective sample size, interpreted relative to the full dataset, or -1 (any negative value) to use
     * the actual number of rows. A value of 0 is treated as unset. See the class Javadoc for the interaction with
     * setRows and testwise deletion.
     *
     * <p>Configuration mutator: do not call concurrently with running tests.
     *
     * @param nEff the effective sample size, or a nonpositive value to use the actual number of rows
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff <= 0 ? -1 : nEff;
    }

    /**
     * The ratio nEff / N applied to the likelihood-ratio statistic; 1 when unset.
     */
    private double effectiveSampleSizeRatio() {
        if (this.nEff < 0) return 1.0;
        int n = this.data.getNumRows();
        return n == 0 ? 1.0 : this.nEff / (double) n;
    }

    /**
     * Returns a list of row indices eligible for this test:
     *   (user-specified subset OR all rows) intersected with rows that have no missingness
     *   in the specific variables involved in this test (testwise deletion).
     */
    private List<Integer> getRows(List<Node> allVars, Map<Node, Integer> nodeHash) {
        if (data == null) return Collections.emptyList();

        final List<Integer> candidateRows;
        if (this.rows != null) {
            candidateRows = this.rows;
        } else {
            ArrayList<Integer> all = new ArrayList<>(data.getNumRows());
            for (int i = 0; i < data.getNumRows(); i++) all.add(i);
            candidateRows = all;
        }

        ArrayList<Integer> filtered = new ArrayList<>(candidateRows.size());

        K:
        for (int k : candidateRows) {
            for (Node node : allVars) {
                Integer colObj = nodeHash.get(node);
                if (colObj == null) {
                    // Shouldn't happen if nodes come from this.data.getVariables(), but fail loudly if it does.
                    throw new IllegalArgumentException("Unknown variable (not in dataset): " + node);
                }
                int col = colObj;

                if (node instanceof ContinuousVariable) {
                    if (Double.isNaN(data.getDouble(k, col))) continue K;
                } else if (node instanceof DiscreteVariable) {
                    if (data.getInt(k, col) == -99) continue K;
                } else {
                    throw new IllegalArgumentException("Expecting either continuous or discrete variables.");
                }
            }

            filtered.add(k);
        }

        return filtered;
    }

    /**
     * Returns the user-specified row restriction (or null if none).
     *
     * @return The current row restriction.
     */
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Allows the user to set which rows are used in the test.
     * If null is passed, all rows are eligible (subject to testwise deletion for each test).
     *
     * @param rows Row indices into the original DataSet.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (data == null) return;

        if (rows == null) {
            this.rows = null;
            return;
        }

        ArrayList<Integer> copy = new ArrayList<>(rows.size());
        for (Integer r : rows) {
            if (r == null) throw new IllegalArgumentException("Row index cannot be null.");
            if (r < 0 || r >= data.getNumRows()) {
                throw new IllegalArgumentException("Row index out of bounds: " + r);
            }
            copy.add(r);
        }

        this.rows = copy;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Each likelihood-ratio calculation uses the rows complete on the variables involved in that calculation (the candidate rows are intersected with the rows having no missingness on those variables), i.e., test-wise deletion, unbiased only under MCAR.
     *
     * @return {@link edu.cmu.tetrad.data.missing.MissingValueSupport#TESTWISE}.
     */
    @Override
    public edu.cmu.tetrad.data.missing.MissingValueSupport getMissingValueSupport() {
        return edu.cmu.tetrad.data.missing.MissingValueSupport.TESTWISE;
    }
}
