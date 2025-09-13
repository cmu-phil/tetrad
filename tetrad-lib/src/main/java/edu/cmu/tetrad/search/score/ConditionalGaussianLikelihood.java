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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.data.Discretizer.Discretization;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import static edu.cmu.tetrad.data.Discretizer.discretize;
import static edu.cmu.tetrad.data.Discretizer.getEqualFrequencyBreakPoints;
import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.log;

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
    private static final double LOG2PI = log(2.0 * FastMath.PI);
    /**
     * The data set. May contain continuous and/or discrete mixedVariables.
     */
    private final DataSet mixedDataSet;
    /**
     * The data set with all continuous mixedVariables discretized.
     */
    private final DataSet dataSet;
    /**
     * The mixedVariables of the mixed data set.
     */
    private final List<Node> mixedVariables;
    /**
     * Indices of mixedVariables.
     */
    private Map<Node, Integer> nodesHash;
    /**
     * Continuous data only.
     */
    private final double[][] continuousData;
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
            this.rows = null;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) == null) throw new NullPointerException("Row " + i + " is null.");
                if (rows.get(i) < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            }

            this.rows = rows;
        }
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

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = this.nodesHash.get(X.get(j));

        double c1 = 0, c2 = 0;

        List<List<Integer>> cells = partition(A, rows);

        for (List<Integer> cell : cells) {
            int a = cell.size();

            if (a < minSampleSizePerCell) continue;

            if (!A.isEmpty()) {
                c1 += a * multinomialLikelihood(a, rows.size());
            }

            if (!X.isEmpty()) {
                // Determinant will be zero if data are linearly dependent.
                Matrix subsample = getSubsample(continuousCols, cell);

                int nRows = subsample.getNumRows();
                int nCols = subsample.getNumColumns();

                if (nRows < minSampleSizePerCell || nCols < 1) {
                    continue;
                }

                if (nRows < nCols) {
                    continue;
                }

                double gl = gaussianLikelihood(k, cov(subsample));

                if (Double.isNaN(gl)) {
                    continue;
                }

                c2 += a * gl;
            }
        }

        double lnL = c1 + c2;

        int dof = f(A) * h(X) + f(A);

        return new Ret(lnL, dof);
    }

    private double multinomialLikelihood(int a, int N) {
        return log(a / (double) N);
    }

    // One record.
    private double gaussianLikelihood(int k, Matrix sigma) {
        double det = sigma.det();

        if (det == 0) {
            return Double.NaN;
        }

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
    private int h(List<ContinuousVariable> X) {
        int p = X.size();
        return p * (p + 1) / 2;
    }

    private List<List<Integer>> partition(List<DiscreteVariable> discrete_parents, List<Integer> rows) {
        List<List<Integer>> cells = new ArrayList<>();
        HashMap<List<Integer>, Integer> keys = new HashMap<>();

        for (int i : rows) {
            List<Integer> key = new ArrayList<>();

            for (DiscreteVariable discrete_parent : discrete_parents) {
                key.add((this.dataSet.getInt(i, this.dataSet.getColumn(discrete_parent))));
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
