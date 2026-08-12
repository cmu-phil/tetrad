///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.data.Discretizer.Discretization;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import edu.cmu.tetrad.util.TMath;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import static edu.cmu.tetrad.data.Discretizer.discretize;
import static edu.cmu.tetrad.data.Discretizer.getEqualFrequencyBreakPoints;
import static edu.cmu.tetrad.util.TMath.abs;
import static edu.cmu.tetrad.util.TMath.log;

/**
 * Implements a conditional Gaussian likelihood. Please note that this likelihood will be maximal only if the continuous
 * variables are jointly Gaussian conditional on the discrete variables; in all other cases, it will be less than
 * maximal. The reference is here:
 * <p>
 * Andrews, B., Ramsey, J., &amp; Cooper, G. F. (2018). Scoring Bayesian networks of mixed variables. International
 * journal of data science and analytics, 6, 3-18.
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 */
public class ConditionalGaussianLikelihood {

    /**
     * A constant.
     */
    private static final double LOG2PI = log(2.0 * TMath.PI);
    /**
     * The data set. May contain continuous and/or discrete mixedVariables.
     */
    private final DataSet mixedDataSet;
    /**
     * The data set with all continuous mixedVariables discretized.
     */
    private DataSet dataSet;
    /**
     * The mixedVariables of the mixed data set.
     */
    private final List<Node> mixedVariables;
    /**
     * Continuous data only.
     */
    private final double[][] continuousData;
    /**
     * Indices of mixedVariables.
     */
    private Map<Node, Integer> nodesHash;
    /**
     * Number of categories to use to discretize continuous mixedVariables.
     */
    private int numCategoriesToDiscretize = 3;
    /**
     * "Cell" consisting of all rows.
     */
    private List<Integer> rows;
    /**
     * Discretize the parents
     */
    private boolean discretize;
    /**
     * Minimum sample size per cell.
     */
    private int minSampleSizePerCell = 4;

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param dataSet The continuous dataset to analyze.
     */
    public ConditionalGaussianLikelihood(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.mixedDataSet = dataSet;
        this.mixedVariables = dataSet.getVariables();

        this.continuousData = new double[dataSet.getNumColumns()][];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);

            if (v instanceof ContinuousVariable) {
                double[] col = new double[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getDouble(i, j);
                }

                this.continuousData[j] = col;
            }
        }

        this.nodesHash = new ConcurrentSkipListMap<>();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);
            this.nodesHash.put(v, j);
        }

        this.dataSet = useErsatzVariables();

        this.rows = new ArrayList<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) this.rows.add(i);
    }

    /**
     * Sets the rows to be used in the table. If the rows are null, the table will use all the rows in the data set.
     * Otherwise, the table will use only the rows specified.
     *
     * @param rows the rows to be used in the table.
     */
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            // null means "all rows"
            this.rows = new ArrayList<>();
            for (int i = 0; i < mixedDataSet.getNumRows(); i++) this.rows.add(i);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) == null) throw new NullPointerException("Row " + i + " is null.");
            if (rows.get(i) < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (rows.get(i) >= mixedDataSet.getNumRows()) throw new IllegalArgumentException("Row " + i + " is out of bounds.");
        }

        // defensive copy is a good idea
        this.rows = new ArrayList<>(rows);
    }

    /**
     * Returns the likelihood of variable i conditional on the given parents, assuming the continuous mixedVariables
     * index by i or by the parents are jointly Gaussian conditional on the discrete comparison.
     *
     * @param i       The index of the conditioned variable.
     * @param parents The indices of the conditioning mixedVariables.
     * @return The likelihood.
     */
    public Ret getLikelihood(int i, int[] parents) {
        Node target = this.mixedVariables.get(i);

        List<ContinuousVariable> X0 = new ArrayList<>();
        List<DiscreteVariable> A0 = new ArrayList<>();

        for (int p : parents) {
            Node parent = this.mixedVariables.get(p);

            if (parent instanceof ContinuousVariable) {
                X0.add((ContinuousVariable) parent);
            } else {
                A0.add((DiscreteVariable) parent);
            }
        }

        List<ContinuousVariable> X1 = new ArrayList<>(X0);
        List<DiscreteVariable> A1 = new ArrayList<>(A0);

        if (target instanceof ContinuousVariable) {
            X1.add((ContinuousVariable) target);
        } else if (target instanceof DiscreteVariable) {
            A1.add((DiscreteVariable) target);
        }

        Ret ret0 = likelihoodJoint(X0, A0, target, this.rows);
        Ret ret1 = likelihoodJoint(X1, A1, target, this.rows);

        return new Ret(ret1.getLik() - ret0.getLik(), ret1.getDof() - ret0.getDof());
    }

    /**
     * Returns the likelihood-ratio ingredients for the nested comparison of target i given parents0 (the smaller
     * model) versus target i given parents1 (the larger model; parents0 should be a subset of parents1). The returned
     * Ret holds lik = [conditional log-likelihood under the larger model] minus [conditional log-likelihood under the
     * smaller model] and dof = [dof of the larger conditional] minus [dof of the smaller conditional], both computed
     * on a COMMON row support, so that 2 * lik is a genuine nested LRT statistic referable to chi-square(dof). Added
     * 2026-8 for IndTestConditionalGaussianLrt; see the calibration notes below.
     *
     * <p>Motivation (changes from the pre-2026-8 test computation, which differenced two {@link #getLikelihood}
     * calls): getLikelihood prunes cells with fewer than minSampleSizePerCell rows and renormalizes the multinomial
     * over the surviving rows, PER MODEL. Since the larger model partitions rows more finely than the smaller one, the
     * two models could be fit on different row supports with differently renormalized likelihoods, so their
     * difference was not a nested LRT: whenever pruning fired (rare cells, or the discretize path multiplying cell
     * counts), the statistic picked up an O(droppedRows) term and the test rejected true nulls at far above nominal
     * rates (harness: 100% rejection regimes at n = 200-1000). Additionally, dof counted all f(A) category
     * combinations including empty and pruned cells, and the covariance used the unbiased (n-1) estimator rather than
     * the MLE, neither of which cancels across partitions of different sizes.
     *
     * <p>This method instead (1) determines eligibility ONCE, on the larger model's CONDITIONING partition (its
     * discrete conditioning set after the discretize swap; the target joins the eligibility partition only in the
     * estimability-forced discretize == false discrete-target case, since selecting rows on target-including cells
     * truncates the joint and breaks a true null, while selecting on conditioning cells does not), keeping cells with
     * at least max(minSampleSizePerCell, kMax + 1) rows, where kMax is the largest continuous dimension fit by either
     * model; (2) evaluates all four joints on exactly those common rows with no further pruning; (3) counts dof over
     * OBSERVED (nonempty) cells of each model's partition of the common rows; and (4) uses the MLE covariance inside
     * the per-cell Gaussian likelihoods.
     *
     * @param i        The index of the target variable.
     * @param parents0 The indices of the smaller model's conditioning variables.
     * @param parents1 The indices of the larger model's conditioning variables (superset of parents0).
     * @return lik and dof differences (larger minus smaller conditional model) on the common support.
     */
    public Ret getLikelihoodRatio(int i, int[] parents0, int[] parents1) {
        Node target = this.mixedVariables.get(i);

        List<ContinuousVariable> x0 = new ArrayList<>();
        List<DiscreteVariable> a0 = new ArrayList<>();
        split(parents0, x0, a0);

        List<ContinuousVariable> x1 = new ArrayList<>();
        List<DiscreteVariable> a1 = new ArrayList<>();
        split(parents1, x1, a1);

        // Apply the discretize swap exactly as likelihoodJoint would, so the
        // partitions used here match the models actually fit.
        x0 = new ArrayList<>(x0);
        a0 = new ArrayList<>(a0);
        x1 = new ArrayList<>(x1);
        a1 = new ArrayList<>(a1);
        maybeDiscretizeSwap(target, x0, a0);
        maybeDiscretizeSwap(target, x1, a1);

        List<ContinuousVariable> x0t = new ArrayList<>(x0);
        List<DiscreteVariable> a0t = new ArrayList<>(a0);
        List<ContinuousVariable> x1t = new ArrayList<>(x1);
        List<DiscreteVariable> a1t = new ArrayList<>(a1);

        if (target instanceof ContinuousVariable) {
            x0t.add((ContinuousVariable) target);
            x1t.add((ContinuousVariable) target);
        } else {
            a0t.add((DiscreteVariable) target);
            a1t.add((DiscreteVariable) target);
        }

        // Common support: eligibility must be decided by cells the null is
        // ALLOWED to condition on. Selecting rows by cells of the conditioning
        // partition (the larger model's discrete conditioning set, post-swap)
        // leaves f(target | x, z) untouched, so a true null still holds on the
        // retained rows. Selecting by cells that include the TARGET truncates
        // the joint and induces spurious target-x dependence under the null, so
        // the target enters the eligibility partition only when estimability
        // forces it: a discrete target with continuous variables left in the
        // larger model (discretize == false), where per-(target, conditioning)
        // cell Gaussians must be fittable. In that configuration a residual
        // truncation bias is unavoidable in this parameterization whenever
        // pruning actually fires; it is the price of estimability there.
        int kMax = Math.max(Math.max(x0.size(), x0t.size()), Math.max(x1.size(), x1t.size()));
        int minCell = Math.max(this.minSampleSizePerCell, kMax + 1);

        boolean targetInEligibility = target instanceof DiscreteVariable && !x1.isEmpty();
        List<DiscreteVariable> eligibilityPartition = targetInEligibility ? a1t : a1;

        List<Integer> commonRows = new ArrayList<>();
        for (List<Integer> cell : partition(eligibilityPartition, this.rows)) {
            if (cell.size() >= minCell) commonRows.addAll(cell);
        }

        if (commonRows.isEmpty()) {
            return new Ret(Double.NaN, 1);
        }

        double lik1 = likOnRows(x1t, a1t, commonRows) - likOnRows(x1, a1, commonRows);
        double lik0 = likOnRows(x0t, a0t, commonRows) - likOnRows(x0, a0, commonRows);

        int dof1 = dofObserved(a1t, x1t, commonRows) - dofObserved(a1, x1, commonRows);
        int dof0 = dofObserved(a0t, x0t, commonRows) - dofObserved(a0, x0, commonRows);

        return new Ret(lik1 - lik0, dof1 - dof0);
    }

    private void split(int[] parents, List<ContinuousVariable> x, List<DiscreteVariable> a) {
        for (int p : parents) {
            Node parent = this.mixedVariables.get(p);
            if (parent instanceof ContinuousVariable) {
                x.add((ContinuousVariable) parent);
            } else {
                a.add((DiscreteVariable) parent);
            }
        }
    }

    /** The discretize swap from likelihoodJoint, factored so getLikelihoodRatio partitions consistently. */
    private void maybeDiscretizeSwap(Node target, List<ContinuousVariable> x, List<DiscreteVariable> a) {
        if (this.discretize && target instanceof DiscreteVariable) {
            for (ContinuousVariable v : new ArrayList<>(x)) {
                Node variable = this.dataSet.getVariable(v.getName());
                if (variable != null) {
                    a.add((DiscreteVariable) variable);
                    x.remove(v);
                }
            }
        }
    }

    /**
     * Joint log-likelihood of (X, A) over exactly the given rows: no pruning, multinomial normalized over
     * rows.size(), per-cell Gaussian with MLE covariance. Cells here are cells of the common support, so every cell a
     * model sees meets the eligibility threshold established in getLikelihoodRatio.
     */
    private double likOnRows(List<ContinuousVariable> X, List<DiscreteVariable> A, List<Integer> rows) {
        int k = X.size();

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) {
            int col = mixedDataSet.getColumnIndex(X.get(j));
            if (col < 0) col = mixedDataSet.getColumnIndex(X.get(j).getName());
            if (col < 0) throw new IllegalArgumentException("Cannot find continuous variable in dataset: " + X.get(j));
            continuousCols[j] = col;
        }

        int N = rows.size();
        double c1 = 0, c2 = 0;

        List<List<Integer>> cells = partition(A, rows);

        if (!A.isEmpty()) {
            for (List<Integer> cell : cells) {
                c1 += cell.size() * multinomialLikelihood(cell.size(), N);
            }
        }

        if (!X.isEmpty()) {
            for (List<Integer> cell : cells) {
                Matrix subsample = getSubsample(continuousCols, cell);
                double gl = gaussianLikelihood(k, covMle(subsample));
                if (Double.isNaN(gl)) return Double.NaN;
                c2 += cell.size() * gl;
            }
        }

        return c1 + c2;
    }

    /** dof counted over the observed (nonempty) cells of A's partition of the given rows. */
    private int dofObserved(List<DiscreteVariable> A, List<ContinuousVariable> X, List<Integer> rows) {
        int m = A.isEmpty() ? 1 : partition(A, rows).size();
        return m * h(X) + (m - 1);
    }

    /** MLE covariance (divide by n), for use inside likelihood ratios. */
    private Matrix covMle(Matrix x) {
        int n = x.getNumRows();
        Matrix c = new Matrix(new Covariance(x.toArray(), true).getCovarianceMatrix().getData());
        return c.scalarMult((n - 1) / (double) n);
    }

    /**
     * Sets whether to discretize child variables to avoid integration. An optimization.
     *
     * @param discretize True, if so.
     * @see #setNumCategoriesToDiscretize(int)
     */
    public void setDiscretize(boolean discretize) {
        this.discretize = discretize;
    }

    /**
     * Sets the number of categories to use to discretize child variables to avoid integration
     *
     * @param numCategoriesToDiscretize This number.
     * @see #setDiscretize(boolean)
     */
    public void setNumCategoriesToDiscretize(int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
        this.dataSet = useErsatzVariables(); // rebuild ersatz + nodesHash
    }

    private DataSet useErsatzVariables() {
        List<Node> nodes = new ArrayList<>();
        int numCategories = this.numCategoriesToDiscretize;

        for (Node x : this.mixedVariables) {
            if (x instanceof ContinuousVariable) {
                nodes.add(new DiscreteVariable(x.getName(), numCategories));
            } else {
                nodes.add(x);
            }
        }

        DataSet replaced = new BoxDataSet(new VerticalIntDataBox(this.mixedDataSet.getNumRows(), this.mixedDataSet.getNumColumns()), nodes);

        for (int j = 0; j < this.mixedVariables.size(); j++) {
            if (this.mixedVariables.get(j) instanceof DiscreteVariable) {
                for (int i = 0; i < this.mixedDataSet.getNumRows(); i++) {
                    replaced.setInt(i, j, this.mixedDataSet.getInt(i, j));
                }
            } else {
                double[] column = this.continuousData[j];

                double[] breakpoints = getEqualFrequencyBreakPoints(column, numCategories);

                List<String> categoryNames = new ArrayList<>();

                for (int i = 0; i < numCategories; i++) {
                    categoryNames.add("" + i);
                }

                Discretization d = discretize(column, breakpoints, this.mixedVariables.get(j).getName(), categoryNames);

                for (int i = 0; i < this.mixedDataSet.getNumRows(); i++) {
                    replaced.setInt(i, j, d.getData()[i]);
                }
            }
        }

        this.nodesHash = new ConcurrentSkipListMap<>();

        for (int j = 0; j < replaced.getNumColumns(); j++) {
            Node v = replaced.getVariable(j);
            this.nodesHash.put(v, j);
        }

        return replaced;
    }

    // The likelihood of the joint over all of these mixedVariables, assuming conditional Gaussian,
    // continuous and discrete.
    private Ret likelihoodJoint(List<ContinuousVariable> X, List<DiscreteVariable> A, Node target, List<Integer> rows) {

        A = new ArrayList<>(A);
        X = new ArrayList<>(X);

        if (this.discretize) {
            if (target instanceof DiscreteVariable) {
                for (ContinuousVariable x : new ArrayList<>(X)) {
                    Node variable = this.dataSet.getVariable(x.getName());

                    if (variable != null) {
                        A.add((DiscreteVariable) variable);
                        X.remove(x);
                    }
                }
            }
        }

        int k = X.size();

//        int[] continuousCols = new int[k];
//        for (int j = 0; j < k; j++) continuousCols[j] = this.nodesHash.get(X.get(j));

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) {
            // Use original dataset columns for continuous data
            int col = mixedDataSet.getColumnIndex(X.get(j));
            if (col < 0) col = mixedDataSet.getColumnIndex(X.get(j).getName()); // you added this default method
            if (col < 0) throw new IllegalArgumentException("Cannot find continuous variable in dataset: " + X.get(j));
            continuousCols[j] = col;
        }

        double c1 = 0, c2 = 0;

        List<List<Integer>> cells = partition(A, rows);

        // choose eligible cells once
        List<List<Integer>> eligible = new ArrayList<>();
        int totalEligibleRows = 0;

        for (List<Integer> cell : cells) {
            int a = cell.size();
            if (a < minSampleSizePerCell) continue;
            if (!X.isEmpty() && a < k) continue; // need at least k rows to estimate k-dim covariance
            eligible.add(cell);
            totalEligibleRows += a;
        }

        if (eligible.isEmpty()) {
            return new Ret(Double.NaN, dof(A, X));
        }

        // Discrete term over same support
        if (!A.isEmpty()) {
            for (List<Integer> cell : eligible) {
                int a = cell.size();
                c1 += a * multinomialLikelihood(a, totalEligibleRows);
            }
        }

        // Continuous term over same support
        if (!X.isEmpty()) {
            for (List<Integer> cell : eligible) {
                Matrix subsample = getSubsample(continuousCols, cell);

                int nRows = subsample.getNumRows();
                int nCols = subsample.getNumColumns();

                if (nRows < minSampleSizePerCell || nCols < 1) continue;
                if (nRows < nCols) continue;

                double gl = gaussianLikelihood(k, cov(subsample));
                if (Double.isNaN(gl)) return new Ret(Double.NaN, dof(A, X));

                c2 += nRows * gl; // (nRows == cell.size())
            }
        }

        double lnL = c1 + c2;

//        int dof = f(A) * h(X) + f(A);
        int dof = dof(A, X);

        return new Ret(lnL, dof);
    }

    private int dof(List<DiscreteVariable> A, List<ContinuousVariable> X) {
        int fA = f(A);
        return fA * h(X) + (fA - 1);
    }

    // Degrees of freedom for a multivariate Gaussian distribution is p * (p + 1) / 2, where p is the number
    // of mixedVariables. This is the number of unique entries in the covariance matrix over X.
    private int h(List<ContinuousVariable> X) {
        int p = X.size();
        return p + (p * (p + 1)) / 2; // mean (p) + covariance unique entries
    }

    private double multinomialLikelihood(int a, int N) {
        return log(a / (double) N);
    }

    // One record.
    private double gaussianLikelihood(int k, Matrix sigma) {
        double det = sigma.det();

//        if (det == 0) {
//            return Double.NaN;
//        }

        if (abs(det) < 1e-12) return Double.NaN;

        return -0.5 * log(abs(det)) - 0.5 * k * (1 + ConditionalGaussianLikelihood.LOG2PI);
    }

    private Matrix cov(Matrix x) {
        return new Matrix(new Covariance(x.toArray(), true).getCovarianceMatrix().getData());
    }

    // Subsample of the continuous mixedVariables conditioning on the given cell.
    private Matrix getSubsample(int[] continuousCols, List<Integer> cell) {
        Matrix subset = new Matrix(cell.size(), continuousCols.length);

        for (int i = 0; i < cell.size(); i++) {
            for (int j = 0; j < continuousCols.length; j++) {
                subset.set(i, j, this.continuousData[continuousCols[j]][cell.get(i)]);
            }
        }

        return subset;
    }

    // Degrees of freedom for a discrete distribution is the product of the number of categories for each
    // variable.
    private int f(List<DiscreteVariable> A) {
        int f = 1;

        for (DiscreteVariable V : A) {
            f *= V.getNumCategories();
        }

        return f;
    }

    // Degrees of freedom for a multivariate Gaussian distribution is p * (p + 1) / 2, where p is the number
    // of mixedVariables. This is the number of unique entries in the covariance matrix over X.
//    private int h(List<ContinuousVariable> X) {
//        int p = X.size();
//        return p * (p + 1) / 2;
//    }

    private List<List<Integer>> partition(List<DiscreteVariable> discrete_parents, List<Integer> rows) {
        List<List<Integer>> cells = new ArrayList<>();
        HashMap<List<Integer>, Integer> keys = new HashMap<>();

        for (int i : rows) {
            List<Integer> key = new ArrayList<>();

            for (DiscreteVariable discrete_parent : discrete_parents) {
                key.add((this.dataSet.getInt(i, this.dataSet.getColumnIndex(discrete_parent))));
            }

            if (!keys.containsKey(key)) {
                keys.put(key, cells.size());
                cells.add(keys.get(key), new ArrayList<>());
            }

            cells.get(keys.get(key)).add(i);
        }

        return cells;
    }

    /**
     * Sets the minimum sample size per cell.
     *
     * @param minSampleSizePerCell The minimum sample size per cell.
     */
    public void setMinSampleSizePerCell(int minSampleSizePerCell) {
        this.minSampleSizePerCell = minSampleSizePerCell;
    }

    /**
     * Gives return value for a conditional Gaussian likelihood, returning a likelihood value and the degrees of freedom
     * for it.
     */
    public static final class Ret {

        /**
         * The likelihood.
         */
        private final double lik;

        /**
         * The degrees of freedom.
         */
        private final int dof;

        /**
         * Constructs a return value for a conditional Gaussian likelihood.
         *
         * @param lik The likelihood.
         * @param dof The degrees of freedom.
         */
        @Contract(pure = true)
        private Ret(double lik, int dof) {
            this.lik = lik;
            this.dof = dof;
        }

        /**
         * Returns the likelihood.
         *
         * @return The likelihood.
         */
        public double getLik() {
            return this.lik;
        }

        /**
         * Returns the degrees of freedom.
         *
         * @return The degrees of freedom.
         */
        public int getDof() {
            return this.dof;
        }

        /**
         * Returns a string representation of this object.
         *
         * @return A string representation of this object.
         */
        public String toString() {
            return "lik = " + this.lik + " dof = " + this.dof;
        }
    }
}

