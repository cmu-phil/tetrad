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
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetrad.data.Discretizer.*;
import static java.lang.Math.log;

/**
 * Implements a conditional Gaussian likelihood. Please note that this this likelihood will be maximal only if the
 * <<<<<<< HEAD
 * the continuous mixedVariables are jointly Gaussian conditional on the discrete mixedVariables; in all other cases, it will
 * be less than maximal. For an algorithm like FGS this is fine.
 * =======
 * the continuous variables are jointly Gaussian conditional on the discrete variables; in all other cases, it will
 * be less than maximal. For an algorithm like FGES this is fine.
 * >>>>>>> 0031bda0387f30996b1180ba17a0aeee69ad44f7
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianOtherLikelihood {

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

    // The AD Tree used to count discrete cells.
    private final AdLeafTree adTree;

    // Multiplier on degrees of freedom for the continuous portion of those degrees.
    private double penaltyDiscount = 1;

    // "Cell" consisting of all rows.
    private final ArrayList<Integer> all;

    // A constant.
    private static final double LOGMATH2PI = log(2.0 * Math.PI);

    /**
     * A return value for a likelihood--returns a likelihood value and the degrees of freedom
     * for it.
     */
    public class Ret {
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
    public ConditionalGaussianOtherLikelihood(final DataSet dataSet) {
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
        this.adTree = AdTrees.getAdLeafTree(this.dataSet);

        this.all = new ArrayList<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) this.all.add(i);

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

        if (target instanceof DiscreteVariable && X.size() > 0) {
            return likelihoodMixed(X, A, (DiscreteVariable) target);
        }

        final List<ContinuousVariable> XPlus = new ArrayList<>(X);
        final List<DiscreteVariable> APlus = new ArrayList<>(A);

        if (target instanceof ContinuousVariable) {
            XPlus.add((ContinuousVariable) target);
        } else if (target instanceof DiscreteVariable) {
            APlus.add((DiscreteVariable) target);
        }

        final Ret ret1 = likelihoodJoint(XPlus, APlus, target);
        final Ret ret2 = likelihoodJoint(X, A, target);

        return new Ret(ret1.getLik() - ret2.getLik(), ret1.getDof() - ret2.getDof());
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setNumCategoriesToDiscretize(final int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    // The likelihood of the joint over all of these mixedVariables, assuming conditional Gaussian,
    // continuous and discrete.
    private Ret likelihoodJoint(List<ContinuousVariable> X, List<DiscreteVariable> A, final Node target) {
        A = new ArrayList<>(A);
        X = new ArrayList<>(X);

        if (target instanceof DiscreteVariable) {
            for (final ContinuousVariable x : new ArrayList<>(X)) {
                final Node variable = this.dataSet.getVariable(x.getName());

                if (variable != null) {
                    A.add((DiscreteVariable) variable);
                    X.remove(x);
                }
            }
        }

        final int k = X.size();

        final int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = this.nodesHash.get(X.get(j));
        final int N = this.mixedDataSet.getNumRows();

        double c1 = 0, c2 = 0;

        final List<List<Integer>> cells = this.adTree.getCellLeaves(A);

        for (final List<Integer> cell : cells) {
            final int a = cell.size();
            if (a == 0) continue;

            if (A.size() > 0) {
                c1 += a * multinomialLikelihood(a, N);
            }

            if (X.size() > 0) {
                try {

                    // Determinant will be zero if data are linearly dependent.
                    if (a > continuousCols.length + 10) {
                        final Matrix cov = cov(getSubsample(continuousCols, cell));
                        c2 += a * gaussianLikelihood(k, cov);
                    } else {
                        final Matrix cov = cov(getSubsample(continuousCols, this.all));
                        c2 += a * gaussianLikelihood(k, cov);
                    }
                } catch (final Exception e) {
                    // No contribution.
                }
            }
        }

        final double lnL = c1 + c2;
        final int p = (int) getPenaltyDiscount();

        // Only count dof for continuous cells that contributed to the likelihood calculation.
        final int dof = p * f(A) * h(X) + f(A);
        return new Ret(lnL, dof);
    }

    private double multinomialLikelihood(final int a, final int N) {
        return log(a / (double) N);
    }

    // One record.
    private double gaussianLikelihood(final int k, final Matrix sigma) {
        return -0.5 * logdet(sigma) - 0.5 * k - 0.5 * k * ConditionalGaussianOtherLikelihood.LOGMATH2PI;
    }

    private double logdet(final Matrix m) {
        final RealMatrix M = new BlockRealMatrix(m.toArray());
        final double tol = 1e-9;
        final RealMatrix LT = new org.apache.commons.math3.linear.CholeskyDecomposition(M, tol, tol).getLT();

        double sum = 0.0;

        for (int i = 0; i < LT.getRowDimension(); i++) {
            sum += FastMath.log(LT.getEntry(i, i));
        }

        return 2.0 * sum;
    }

    // For cases like P(C | X). This is a ratio of joints, but if the numerator is conditional Gaussian,
    // the denominator is a mixture of Gaussians.
    private Ret likelihoodMixed(final List<ContinuousVariable> X, final List<DiscreteVariable> A, final DiscreteVariable B) {
        final int k = X.size();
        final double g = Math.pow(2.0 * Math.PI, -0.5 * k) * Math.exp(-0.5 * k);

        final int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = this.nodesHash.get(X.get(j));
        double lnL = 0.0;

        final int N = this.dataSet.getNumRows();

        final List<List<List<Integer>>> cells = this.adTree.getCellLeaves(A, B);

        Matrix defaultCov = null;

        for (final List<List<Integer>> mycells : cells) {
            final List<Matrix> x = new ArrayList<>();
            final List<Matrix> sigmas = new ArrayList<>();
            final List<Matrix> inv = new ArrayList<>();
            final List<Vector> mu = new ArrayList<>();

            for (final List<Integer> cell : mycells) {
                final Matrix subsample = getSubsample(continuousCols, cell);

                try {

                    // Determinant will be zero if data are linearly dependent.
                    if (mycells.size() <= continuousCols.length) throw new IllegalArgumentException();

                    final Matrix cov = cov(subsample);
                    final Matrix covinv = cov.inverse();

                    if (defaultCov == null) {
                        defaultCov = cov;
                    }

                    x.add(subsample);
                    sigmas.add(cov);
                    inv.add(covinv);
                    mu.add(means(subsample));
                } catch (final Exception e) {
                    // No contribution.
                }
            }

            final double[] factors = new double[x.size()];

            for (int u = 0; u < x.size(); u++) {
                factors[u] = g * Math.pow(sigmas.get(u).det(), -0.5);
            }

            final double[] a = new double[x.size()];

            for (int u = 0; u < x.size(); u++) {
                for (int i = 0; i < x.get(u).rows(); i++) {
                    for (int v = 0; v < x.size(); v++) {
                        final Vector xm = x.get(u).getRow(i).minus(mu.get(v));
                        a[v] = prob(factors[v], inv.get(v), xm);
                    }

                    final double num = a[u] * p(x, u, N);
                    double denom = 0.0;

                    for (int v = 0; v < x.size(); v++) {
                        denom += a[v] * (p(x, v, N));
                    }

                    lnL += log(num) - log(denom);
                }
            }
        }

        final int p = (int) getPenaltyDiscount();

        // Only count dof for continuous cells that contributed to the likelihood calculation.
        final int dof = f(A) * B.getNumCategories() + f(A) * p * h(X);
        return new Ret(lnL, dof);
    }

    private Ret likelihoodJointMultinomial(final List<ContinuousVariable> X, final List<DiscreteVariable> A) {
        final List<DiscreteVariable> W = new ArrayList<>(A);

        for (final ContinuousVariable x : X) {
            final DiscreteVariable w = (DiscreteVariable) this.dataSet.getVariable(x.getName());
            W.add(w);
        }

        double lnL = 0;
        final int N = this.dataSet.getNumRows();

        final List<List<Integer>> cells = this.adTree.getCellLeaves(W);

        for (final List<Integer> cell : cells) {
            final int a = cell.size();
            if (a == 0) continue;

            if (W.size() > 0) {
                lnL += a * multinomialLikelihood(a, N);
            }
        }

        final int dof = f(W);
        return new Ret(lnL, dof);
    }

    private double p(final List<Matrix> x, final int u, final double N) {
        return x.get(u).rows() / N;
    }

    private Matrix cov(final Matrix x) {
        return new Matrix(new Covariance(x.toArray(), true).getCovarianceMatrix().getData());
    }

    private double prob(final Double factor, final Matrix inv, final Vector x) {
        return factor * Math.exp(-0.5 * inv.times(x).dotProduct(x));
    }

    // Calculates the means of the columns of x.
    private Vector means(final Matrix x) {
        return x.sum(1).scalarMult(1.0 / x.rows());
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
}