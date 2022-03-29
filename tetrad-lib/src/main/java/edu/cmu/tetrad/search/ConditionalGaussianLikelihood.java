///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.stat.correlation.Covariance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetrad.data.Discretizer.*;
import static java.lang.Math.log;

/**
 * Implements a conditional Gaussian likelihood. Please note that this this likelihood will be maximal only if the
 * the continuous mixedVariables are jointly Gaussian conditional on the discrete mixedVariables; in all other cases, it will
 * be less than maximal. For an algorithm like FGS this is fine.
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianLikelihood {

    // The data set. May contain continuous and/or discrete mixedVariables.
    private final DataSet mixedDataSet;

    // The data set with all continuous mixedVariables discretized.
    private final DataSet dataSet;

    // Number of categories to use to discretize continuous mixedVariables.
    private int numCategoriesToDiscretize = 3;

    // The mixedVariables of the mixed data set.
    private final List<Node> mixedVariables;

    // Indices of mixedVariables.
    private final Map<Node, Integer> nodesHash;

    // Continuous data only.
    private final double[][] continuousData;

    // Multiplier on degrees of freedom for the continuous portion of those degrees.
    private double penaltyDiscount = 1;

    // "Cell" consisting of all rows.
    private List<Integer> rows;

    // Discretize the parents
    private boolean discretize = false;

    // A constant.
    private static final double LOG2PI = log(2.0 * Math.PI);

    public void setRows(final List<Integer> rows) {
        this.rows = rows;
    }

    /**
     * A return value for a likelihood--returns a likelihood value and the degrees of freedom
     * for it.
     */
    public static class Ret {
        private final double lik;
        private final int dof;

        private Ret(final double lik, final int dof) {
            this.lik = lik;
            this.dof = dof;
        }

        public double getLik() {
            return this.lik;
        }

        public int getDof() {
            return this.dof;
        }

        public String toString() {
            return "lik = " + this.lik + " dof = " + this.dof;
        }
    }

    /**
     * Constructs the score using a covariance matrix.
     */
    public ConditionalGaussianLikelihood(final DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.mixedDataSet = dataSet;
        this.mixedVariables = dataSet.getVariables();

        this.continuousData = new double[dataSet.getNumColumns()][];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final Node v = dataSet.getVariable(j);

            if (v instanceof ContinuousVariable) {
                final double[] col = new double[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getDouble(i, j);
                }

                this.continuousData[j] = col;
            }
        }

        this.nodesHash = new HashMap<>();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final Node v = dataSet.getVariable(j);
            this.nodesHash.put(v, j);
        }

        this.dataSet = useErsatzVariables();

        this.rows = new ArrayList<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) this.rows.add(i);
    }

    private DataSet useErsatzVariables() {
        final List<Node> nodes = new ArrayList<>();
        final int numCategories = this.numCategoriesToDiscretize;

        for (final Node x : this.mixedVariables) {
            if (x instanceof ContinuousVariable) {
                nodes.add(new DiscreteVariable(x.getName(), numCategories));
            } else {
                nodes.add(x);
            }
        }

        final DataSet replaced = new BoxDataSet(new VerticalIntDataBox(this.mixedDataSet.getNumRows(), this.mixedDataSet.getNumColumns()), nodes);

        for (int j = 0; j < this.mixedVariables.size(); j++) {
            if (this.mixedVariables.get(j) instanceof DiscreteVariable) {
                for (int i = 0; i < this.mixedDataSet.getNumRows(); i++) {
                    replaced.setInt(i, j, this.mixedDataSet.getInt(i, j));
                }
            } else {
                final double[] column = this.continuousData[j];

                final double[] breakpoints = getEqualFrequencyBreakPoints(column, numCategories);

                final List<String> categoryNames = new ArrayList<>();

                for (int i = 0; i < numCategories; i++) {
                    categoryNames.add("" + i);
                }

                final Discretization d = discretize(column, breakpoints, this.mixedVariables.get(j).getName(), categoryNames);

                for (int i = 0; i < this.mixedDataSet.getNumRows(); i++) {
                    replaced.setInt(i, j, d.getData()[i]);
                }
            }
        }

        return replaced;
    }

    /**
     * Returns the likelihood of variable i conditional on the given parents, assuming the continuous mixedVariables
     * index by i or by the parents are jointly Gaussian conditional on the discrete comparison.
     *
     * @param i       The index of the conditioned variable.
     * @param parents The indices of the conditioning mixedVariables.
     * @return The likelihood.
     */
    public Ret getLikelihood(final int i, final int[] parents) {
        final Node target = this.mixedVariables.get(i);

        final List<ContinuousVariable> X = new ArrayList<>();
        final List<DiscreteVariable> A = new ArrayList<>();

        for (final int p : parents) {
            final Node parent = this.mixedVariables.get(p);

            if (parent instanceof ContinuousVariable) {
                X.add((ContinuousVariable) parent);
            } else {
                A.add((DiscreteVariable) parent);
            }
        }

        final List<ContinuousVariable> XPlus = new ArrayList<>(X);
        final List<DiscreteVariable> APlus = new ArrayList<>(A);

        if (target instanceof ContinuousVariable) {
            XPlus.add((ContinuousVariable) target);
        } else if (target instanceof DiscreteVariable) {
            APlus.add((DiscreteVariable) target);
        }

        final Ret ret1 = likelihoodJoint(XPlus, APlus, target, this.rows);
        final Ret ret2 = likelihoodJoint(X, A, target, this.rows);

        return new Ret(ret1.getLik() - ret2.getLik(), ret1.getDof() - ret2.getDof());
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setDiscretize(final boolean discretize) {
        this.discretize = discretize;
    }

    public void setNumCategoriesToDiscretize(final int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    // The likelihood of the joint over all of these mixedVariables, assuming conditional Gaussian,
    // continuous and discrete.
    private Ret likelihoodJoint(List<ContinuousVariable> X, List<DiscreteVariable> A, final Node target, final List<Integer> rows) {

        A = new ArrayList<>(A);
        X = new ArrayList<>(X);

        if (this.discretize) {
            if (target instanceof DiscreteVariable) {
                for (final ContinuousVariable x : new ArrayList<>(X)) {
                    final Node variable = this.dataSet.getVariable(x.getName());

                    if (variable != null) {
                        A.add((DiscreteVariable) variable);
                        X.remove(x);
                    }
                }
            }
        }

        final int k = X.size();

        final int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = this.nodesHash.get(X.get(j));

        double c1 = 0, c2 = 0;

        final List<List<Integer>> cells = partition(A, rows);

        for (final List<Integer> cell : cells) {
            final int a = cell.size();

            if (a == 0) continue;

            if (A.size() > 0) {
                c1 += a * multinomialLikelihood(a, rows.size());
            }

            if (X.size() > 0) {
                try {

                    // Determinant will be zero if data are linearly dependent.
                    final double gl = gaussianLikelihood(k, cov(getSubsample(continuousCols, cell)));

                    if (!Double.isNaN(gl)) {
                        c2 += a * gl;
                    }
                } catch (final Exception e) {
                    // No contribution.
                }
            }
        }

        final double lnL = c1 + c2;

        final int dof = f(A) * h(X) + f(A);

        return new Ret(lnL, dof);
    }

    private double multinomialLikelihood(final int a, final int N) {
        return log(a / (double) N);
    }

    // One record.
    private double gaussianLikelihood(final int k, final Matrix sigma) {
        return -0.5 * log(sigma.det()) - 0.5 * k * (1 + ConditionalGaussianLikelihood.LOG2PI);
    }

    private Matrix cov(final Matrix x) {
        return new Matrix(new Covariance(x.toArray(), true).getCovarianceMatrix().getData());
    }

    // Subsample of the continuous mixedVariables conditioning on the given cell.
    private Matrix getSubsample(final int[] continuousCols, final List<Integer> cell) {
        final Matrix subset = new Matrix(cell.size(), continuousCols.length);

        for (int i = 0; i < cell.size(); i++) {
            for (int j = 0; j < continuousCols.length; j++) {
                subset.set(i, j, this.continuousData[continuousCols[j]][cell.get(i)]);
            }
        }

        return subset;
    }

    // Degrees of freedom for a discrete distribution is the product of the number of categories for each
    // variable.
    private int f(final List<DiscreteVariable> A) {
        int f = 1;

        for (final DiscreteVariable V : A) {
            f *= V.getNumCategories();
        }

        return f;
    }

    // Degrees of freedom for a multivariate Gaussian distribution is p * (p + 1) / 2, where p is the number
    // of mixedVariables. This is the number of unique entries in the covariance matrix over X.
    private int h(final List<ContinuousVariable> X) {
        final int p = X.size();
        return p * (p + 1) / 2;
    }

    private List<List<Integer>> partition(final List<DiscreteVariable> discrete_parents, final List<Integer> rows) {
        final List<List<Integer>> cells = new ArrayList<>();
        final HashMap<List<Integer>, Integer> keys = new HashMap<>();

        for (final int i : rows) {
            final List<Integer> key = new ArrayList<>();

            for (final DiscreteVariable discrete_parent : discrete_parents) {
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
}