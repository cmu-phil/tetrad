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
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

import static edu.cmu.tetrad.data.Discretizer.*;
import static java.lang.Math.log;

/**
 * Implements a conditional Gaussian likelihood. Please note that this this likelihood will be maximal only if the
 <<<<<<< HEAD
 * the continuous mixedVariables are jointly Gaussian conditional on the discrete mixedVariables; in all other cases, it will
 * be less than maximal. For an algorithm like FGS this is fine.
 =======
 * the continuous variables are jointly Gaussian conditional on the discrete variables; in all other cases, it will
 * be less than maximal. For an algorithm like FGES this is fine.
 >>>>>>> 0031bda0387f30996b1180ba17a0aeee69ad44f7
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianOtherLikelihood {

    // The data set. May contain continuous and/or discrete mixedVariables.
    private DataSet mixedDataSet;

    // The data set with all continuous mixedVariables discretized.
    private DataSet dataSet;

    // Number of categories to use to discretize continuous mixedVariables.
    private int numCategoriesToDiscretize = 3;

    // The mixedVariables of the mixed data set.
    private List<Node> mixedVariables;

    // Indices of mixedVariables.
    private Map<Node, Integer> nodesHash;

    // Continuous data only.
    private double[][] continuousData;

    // The AD Tree used to count discrete cells.
    private AdLeafTree adTree;

    // Multiplier on degrees of freedom for the continuous portion of those degrees.
    private double penaltyDiscount = 1;

    // "Cell" consisting of all rows.
    private final ArrayList<Integer> all;

    // A constant.
    private static double LOGMATH2PI = log(2.0 * Math.PI);

    /**
     * A return value for a likelihood--returns a likelihood value and the degrees of freedom
     * for it.
     */
    public class Ret {
        private double lik;
        private int dof;

        private Ret(double lik, int dof) {
            this.lik = lik;
            this.dof = dof;
        }

        public double getLik() {
            return lik;
        }

        public int getDof() {
            return dof;
        }

        public String toString() {
            return "lik = " + lik + " dof = " + dof;
        }
    }

    /**
     * Constructs the score using a covariance matrix.
     */
    public ConditionalGaussianOtherLikelihood(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.mixedDataSet = dataSet;
        this.mixedVariables = dataSet.getVariables();

        continuousData = new double[dataSet.getNumColumns()][];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);

            if (v instanceof ContinuousVariable) {
                double[] col = new double[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getDouble(i, j);
                }

                continuousData[j] = col;
            }
        }

        nodesHash = new HashMap<>();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);
            nodesHash.put(v, j);
        }

        this.dataSet = useErsatzVariables();
        this.adTree = AdTrees.getAdLeafTree(this.dataSet);

        all = new ArrayList<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) all.add(i);

    }

    private DataSet useErsatzVariables() {
        List<Node> nodes = new ArrayList<>();
        int numCategories = numCategoriesToDiscretize;

        for (Node x : mixedVariables) {
            if (x instanceof ContinuousVariable) {
                nodes.add(new DiscreteVariable(x.getName(), numCategories));
            } else {
                nodes.add(x);
            }
        }

        DataSet replaced = new BoxDataSet(new VerticalIntDataBox(mixedDataSet.getNumRows(), mixedDataSet.getNumColumns()), nodes);

        for (int j = 0; j < mixedVariables.size(); j++) {
            if (mixedVariables.get(j) instanceof DiscreteVariable) {
                for (int i = 0; i < mixedDataSet.getNumRows(); i++) {
                    replaced.setInt(i, j, mixedDataSet.getInt(i, j));
                }
            } else {
                double[] column = continuousData[j];

                double[] breakpoints = getEqualFrequencyBreakPoints(column, numCategories);

                List<String> categoryNames = new ArrayList<>();

                for (int i = 0; i < numCategories; i++) {
                    categoryNames.add("" + i);
                }

                Discretization d = discretize(column, breakpoints, mixedVariables.get(j).getName(), categoryNames);

                for (int i = 0; i < mixedDataSet.getNumRows(); i++) {
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
    public Ret getLikelihood(int i, int[] parents) {
        Node target = mixedVariables.get(i);

        List<ContinuousVariable> X = new ArrayList<>();
        List<DiscreteVariable> A = new ArrayList<>();

        for (int p : parents) {
            Node parent = mixedVariables.get(p);

            if (parent instanceof ContinuousVariable) {
                X.add((ContinuousVariable) parent);
            } else {
                A.add((DiscreteVariable) parent);
            }
        }

        if (target instanceof DiscreteVariable && X.size() > 0) {
            return likelihoodMixed(X, A, (DiscreteVariable) target);
        }

        List<ContinuousVariable> XPlus = new ArrayList<>(X);
        List<DiscreteVariable> APlus = new ArrayList<>(A);

        if (target instanceof ContinuousVariable) {
            XPlus.add((ContinuousVariable) target);
        } else if (target instanceof DiscreteVariable) {
            APlus.add((DiscreteVariable) target);
        }

        Ret ret1 = likelihoodJoint(XPlus, APlus, target);
        Ret ret2 = likelihoodJoint(X, A, target);

        return new Ret(ret1.getLik() - ret2.getLik(), ret1.getDof() - ret2.getDof());
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setNumCategoriesToDiscretize(int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    // The likelihood of the joint over all of these mixedVariables, assuming conditional Gaussian,
    // continuous and discrete.
    private Ret likelihoodJoint(List<ContinuousVariable> X, List<DiscreteVariable> A, Node target) {
        A = new ArrayList<>(A);
        X = new ArrayList<>(X);

        if (target instanceof DiscreteVariable) {
            for (ContinuousVariable x : new ArrayList<>(X)) {
                final Node variable = dataSet.getVariable(x.getName());

                if (variable != null) {
                    A.add((DiscreteVariable) variable);
                    X.remove(x);
                }
            }
        }

        int k = X.size();

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = nodesHash.get(X.get(j));
        int N = mixedDataSet.getNumRows();

        double c1 = 0, c2 = 0;

        List<List<Integer>> cells = adTree.getCellLeaves(A);

        for (List<Integer> cell : cells) {
            int a = cell.size();
            if (a == 0) continue;

            if (A.size() > 0) {
                c1 += a * multinomialLikelihood(a, N);
            }

            if (X.size() > 0) {
                try {

                    // Determinant will be zero if data are linearly dependent.
                    if (a > continuousCols.length + 10) {
                        TetradMatrix cov = cov(getSubsample(continuousCols, cell));
                        c2 += a * gaussianLikelihood(k, cov);
                    } else {
                        TetradMatrix cov = cov(getSubsample(continuousCols, all));
                        c2 += a * gaussianLikelihood(k, cov);
                    }
                } catch (Exception e) {
                    // No contribution.
                }
            }
        }

        final double lnL = c1 + c2;
        int p = (int) getPenaltyDiscount();

        // Only count dof for continuous cells that contributed to the likelihood calculation.
        final int dof = p * f(A) * h(X) + f(A);
        return new Ret(lnL, dof);
    }

    private double multinomialLikelihood(int a, int N) {
        return log(a / (double) N);
    }

    // One record.
    private double gaussianLikelihood(int k, TetradMatrix sigma) {
        return -0.5 * logdet(sigma) - 0.5 * k - 0.5 * k * LOGMATH2PI;
    }

    private double logdet(TetradMatrix m) {
        RealMatrix M = m.getRealMatrix();
        final double tol = 1e-9;
        RealMatrix LT = new org.apache.commons.math3.linear.CholeskyDecomposition(M, tol, tol).getLT();

        double sum = 0.0;

        for (int i = 0; i < LT.getRowDimension(); i++) {
            sum += FastMath.log(LT.getEntry(i, i));
        }

        return 2.0 * sum;
    }

    // For cases like P(C | X). This is a ratio of joints, but if the numerator is conditional Gaussian,
    // the denominator is a mixture of Gaussians.
    private Ret likelihoodMixed(List<ContinuousVariable> X, List<DiscreteVariable> A, DiscreteVariable B) {
        final int k = X.size();
        final double g = Math.pow(2.0 * Math.PI, -0.5 * k) * Math.exp(-0.5 * k);

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = nodesHash.get(X.get(j));
        double lnL = 0.0;

        int N = dataSet.getNumRows();

        List<List<List<Integer>>> cells = adTree.getCellLeaves(A, B);

        TetradMatrix defaultCov = null;

        for (List<List<Integer>> mycells : cells) {
            List<TetradMatrix> x = new ArrayList<>();
            List<TetradMatrix> sigmas = new ArrayList<>();
            List<TetradMatrix> inv = new ArrayList<>();
            List<TetradVector> mu = new ArrayList<>();

            for (List<Integer> cell : mycells) {
                TetradMatrix subsample = getSubsample(continuousCols, cell);

                try {

                    // Determinant will be zero if data are linearly dependent.
                    if (mycells.size() <= continuousCols.length) throw new IllegalArgumentException();

                    TetradMatrix cov = cov(subsample);
                    TetradMatrix covinv = cov.inverse();

                    if (defaultCov == null) {
                        defaultCov = cov;
                    }

                    x.add(subsample);
                    sigmas.add(cov);
                    inv.add(covinv);
                    mu.add(means(subsample));
                } catch (Exception e) {
                    // No contribution.
                }
            }

            double[] factors = new double[x.size()];

            for (int u = 0; u < x.size(); u++) {
                factors[u] = g * Math.pow(sigmas.get(u).det(), -0.5);
            }

            double[] a = new double[x.size()];

            for (int u = 0; u < x.size(); u++) {
                for (int i = 0; i < x.get(u).rows(); i++) {
                    for (int v = 0; v < x.size(); v++) {
                        final TetradVector xm = x.get(u).getRow(i).minus(mu.get(v));
                        a[v] = prob(factors[v], inv.get(v), xm);
                    }

                    double num = a[u] * p(x, u, N);
                    double denom = 0.0;

                    for (int v = 0; v < x.size(); v++) {
                        denom += a[v] * (p(x, v, N));
                    }

                    lnL += log(num) - log(denom);
                }
            }
        }

        int p = (int) getPenaltyDiscount();

        // Only count dof for continuous cells that contributed to the likelihood calculation.
        int dof = f(A) * B.getNumCategories() + f(A) * p * h(X);
        return new Ret(lnL, dof);
    }

    private Ret likelihoodJointMultinomial(List<ContinuousVariable> X, List<DiscreteVariable> A) {
        List<DiscreteVariable> W = new ArrayList<>(A);

        for (ContinuousVariable x : X) {
            DiscreteVariable w = (DiscreteVariable) dataSet.getVariable(x.getName());
            W.add(w);
        }

        double lnL = 0;
        int N = dataSet.getNumRows();

        List<List<Integer>> cells = adTree.getCellLeaves(W);

        for (List<Integer> cell : cells) {
            int a = cell.size();
            if (a == 0) continue;

            if (W.size() > 0) {
                lnL += a * multinomialLikelihood(a, N);
            }
        }

        final int dof = f(W);
        return new Ret(lnL, dof);
    }

    private double p(List<TetradMatrix> x, int u, double N) {
        return x.get(u).rows() / N;
    }

    private TetradMatrix cov(TetradMatrix x) {
        return new TetradMatrix(new Covariance(x.getRealMatrix(), true).getCovarianceMatrix());
    }

    private double prob(Double factor, TetradMatrix inv, TetradVector x) {
        return factor * Math.exp(-0.5 * inv.times(x).dotProduct(x));
    }

    // Calculates the means of the columns of x.
    private TetradVector means(TetradMatrix x) {
        return x.sum(1).scalarMult(1.0 / x.rows());
    }

    // Subsample of the continuous mixedVariables conditioning on the given cell.
    private TetradMatrix getSubsample(int[] continuousCols, List<Integer> cell) {
        TetradMatrix subset = new TetradMatrix(cell.size(), continuousCols.length);

        for (int i = 0; i < cell.size(); i++) {
            for (int j = 0; j < continuousCols.length; j++) {
                subset.set(i, j, continuousData[continuousCols[j]][cell.get(i)]);
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
}