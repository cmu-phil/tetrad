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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import org.apache.commons.math3.stat.correlation.Covariance;

import java.util.*;

/**
 * Implements a conditional Gaussian likelihood. Please note that this this likelihood will be maximal only if the
 * the continuous variables are jointly Gaussian conditional on the discrete variables; in all other cases, it will
 * be less than maximal. For an algorithm like FGS this is fine.
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianLikelihood {

    // The data set. May contain continuous and/or discrete variables.
    private DataSet dataSet;

    // The variables of the data set.
    private List<Node> variables;

    // Indices of variables.
    private Map<Node, Integer> nodesHash;

    // Continuous data only.
    private double[][] continuousData;

    //The AD Tree used to count discrete cells.
    private AdLeafTree adTree;

    // The minimum size of a cell for continuous calculations.
    final int minSample = 2;


    /**
     * A return value for a likelihood--a pair of <likelihood, degrees of freedom>.
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
    public ConditionalGaussianLikelihood(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();

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

        this.adTree = AdTrees.getAdLeafTree(dataSet);
    }

    /**
     * Returns the likelihood of variable i conditional on the given parents, assuming the continuous variables
     * index by i or by the parents are jointly Gaussian conditional on the discrete comparison.
     *
     * @param i       The index of the conditioned variable.
     * @param parents The indices of the conditioning variables.
     * @return The likelihood.
     */
    public Ret getLikelihood(int i, int[] parents) {
        Node target = variables.get(i);

        List<ContinuousVariable> X = new ArrayList<>();
        List<DiscreteVariable> A = new ArrayList<>();

        for (int p : parents) {
            Node parent = variables.get(p);

            if (parent instanceof ContinuousVariable) {
                X.add((ContinuousVariable) parent);
            } else {
                A.add((DiscreteVariable) parent);
            }
        }

        List<ContinuousVariable> XPlus = new ArrayList<>(X);
        List<DiscreteVariable> APlus = new ArrayList<>(A);

        if (target instanceof ContinuousVariable) {
            XPlus.add((ContinuousVariable) target);
        } else if (target instanceof DiscreteVariable) {
            APlus.add((DiscreteVariable) target);
        }

//        if (target instanceof ContinuousVariable || X.isEmpty()) {
            Ret ret1 = getJointLikelihood(XPlus, APlus);
            Ret ret2 = getJointLikelihood(X, A);

            double lik = ret1.getLik() - ret2.getLik();

            System.out.println(lik);

            int dof = ret1.getDof() - ret2.getDof();
            return new Ret(lik, dof);
//        } else if (target instanceof DiscreteVariable) {
////            Ret ret1 = getJointLikelihood(X, APlus, null);
////            Ret ret2 = getJointLikelihood2(X, A, APlus);
//////            Ret ret2 = getJointLikelihood(X, A, null);
////
////            double lik = ret1.getLik() - ret2.getLik();
////            int dof = ret1.getDof() - ret2.getDof();
////            return new Ret(lik, dof);
//
//            return getJointLikelihood3(X, A, (DiscreteVariable) target);
//        }
//
//        throw new IllegalArgumentException();
    }

    // The likelihood of the joint over all of these variables, continuous and discrete.
    private Ret getJointLikelihood(List<ContinuousVariable> X, List<DiscreteVariable> A) {
        int k = X.size();
        final int minSample = Math.min(this.minSample, 5);

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = nodesHash.get(X.get(j));
        int N = dataSet.getNumRows();

        double c1 = 0, c2 = 0;

        List<List<Integer>> cells = adTree.getCellLeaves(A);

        for (List<Integer> cell : cells) {
            int a = cell.size();
            if (a == 0) continue;

            if (A.size() > 0) {
                c1 += a * Math.log(a);
            }

            if (a > minSample) {
                if (X.size() > 0) {
                    TetradMatrix Sigma = getCov(k, continuousCols, cell);
                    c2 += -0.5 * a * Math.log(Sigma.det());
                }
            }
        }

        if (A.size() > 0) {
            c1 += -N * Math.log(N);
        }

        if (X.size() > 0) {
            c2 += -0.5 * N * k * (1.0 + Math.log(2.0 * Math.PI));
        }

        double lik = c1 + c2;
        int dof = f(A) * h(X) + f(A);

        return new Ret(lik, dof);
    }

    private Ret getJointLikelihood2(List<ContinuousVariable> X, List<DiscreteVariable> A, List<DiscreteVariable> APlus) {
        int k = X.size();
        final int minSample = Math.min(this.minSample, 5);

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = nodesHash.get(X.get(j));
        int N = dataSet.getNumRows();

        double c1 = 0, c2 = 0, c3 = 0, c4 = 0;

        double lik1, lik2;
        int dof1, dof2;

        {
            List<List<Integer>> cells = adTree.getCellLeaves(A);

            for (List<Integer> cell : cells) {
                int a = cell.size();
                if (a == 0) continue;

                if (A.size() > 0) {
                    c1 += a * Math.log(a);
                }

                if (a > minSample) {
                    if (X.size() > 0) {
                        TetradMatrix Sigma = getCov(k, continuousCols, cell);
                        c2 += -0.5 * a * Math.log(Sigma.det());
                    }
                }
            }

            if (A.size() > 0) {
                c1 += -N * Math.log(N);
            }

            if (X.size() > 0) {
                c2 += -0.5 * N * k * (1.0 + Math.log(2.0 * Math.PI));
            }

            lik1 = c1 + c2;
        }

        {
            List<List<Integer>> cells = adTree.getCellLeaves(APlus);

            for (List<Integer> cell : cells) {
                int a = cell.size();
                if (a == 0) continue;

                if (A.size() > 0) {
                    c3 += a * Math.log(a);
                }

                if (X.size() > 0) {
                    if (a > minSample) {
                        TetradMatrix Sigma = getCov(k, continuousCols, cell);
                        c4 += -0.5 * a * Math.log(Sigma.det());
                    }
                }
            }

            if (APlus.size() > 0) {
                c3 += -N * Math.log(N);
            }

            if (X.size() > 0) {
                c4 += -0.5 * N * k * (1.0 + Math.log(2.0 * Math.PI));
            }

            lik2 = c2 + c4;
        }

        if (c1 + c2 > c3 + c4) {
            return new Ret(c1 + c2, f(A) * h(X) + f(A));
        } else {
            return new Ret(c1 + c4, f(APlus) * h(X) + f(A));
        }
    }

    private Ret getJointLikelihood3(List<ContinuousVariable> X, List<DiscreteVariable> A, DiscreteVariable B) {
        int k = X.size();
        final int minSample = Math.min(this.minSample, 2);

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = nodesHash.get(X.get(j));
        double lnL = 0.0;

        int N = dataSet.getNumRows();

        List<List<List<Integer>>> cells = adTree.getCellLeaves(A, B);

        for (List<List<Integer>> mycells : cells) {
            List<List<List<Double>>> a = new ArrayList<>();

            // Get the subsample for each cell.
            List<TetradMatrix> x = new ArrayList<>();

            for (List<Integer> cell : mycells) {
                final TetradMatrix subsample = getSubsample(k, continuousCols, cell);
                if (subsample.rows() < minSample) continue;
                x.add(subsample);
            }

            // For each cell, calculate det sigma.
            List<TetradMatrix> sigmas = new ArrayList<>();

            for (int j = 0; j < x.size(); j++) {
                sigmas.add(cov(x, j));
            }

            // For each cell calculate k \sigma| ^ -1/2.
            List<Double> factor1 = new ArrayList<>();

            for (int u = 0; u < x.size(); u++) {
                factor1.add(k * Math.pow(2 * Math.PI * sigmas.get(u).det(), -0.5));
            }

            // For each cell calculate sigma^-1.
            List<TetradMatrix> inv = new ArrayList<>();

            for (int u = 0; u < x.size(); u++) {
                inv.add(sigmas.get(u).inverse());
            }

            // Within each cell, for each guy, calculate (x - mu)' Sigma^-1 (x - mu)

            for (int u = 0; u < x.size(); u++) {
                a.add(new ArrayList<List<Double>>());

                for (int i = 0; i < x.size(); i++) {
                    a.get(u).add(new ArrayList<Double>());
                }
            }

            for (int u = 0; u < x.size(); u++) {
                for (int i = 0; i < x.get(u).rows(); i++) {
                    for (int v = 0; v < x.size(); v++) {
                        final TetradMatrix x0 = center(x.get(u));
                        double g4 = prob(factor1.get(u), inv.get(u), x0);
                        final List<List<Double>> lists = a.get(u);
                        final List<Double> doubles = lists.get(v);
                        doubles.add(g4);
                    }
                }
            }

            for (int u = 0; u < x.size(); u++) {
                for (int i = 0; i < x.get(u).rows(); i++) {
                    double aui = a.get(u).get(u).get(i) * x.get(u).rows() / (double) N;
                    double denom = 0;

                    for (int v = 0; v < x.size(); v++) {
                        denom += a.get(u).get(v).get(i) * (x.get(v).rows() / (double) N);
                    }

                    if (aui != 0 && denom != 0) {
                        lnL += Math.log(aui) - Math.log(denom);
                    }
                }
            }
        }

        System.out.println("lnL = " + lnL);

        return new Ret(lnL, f(A) * h(X) + f(A));
    }

    private TetradMatrix cov(List<TetradMatrix> x, int j) {
        return new TetradMatrix(new Covariance(x.get(j).getRealMatrix(),
                            false).getCovarianceMatrix());
    }

    private double prob(Double factor, TetradMatrix inv, TetradMatrix x0) {
        final TetradMatrix g1 = x0.times(inv).times(x0.transpose());
        final double g2 = g1.get(0, 0);
        final double g3 = Math.exp(-0.5 * g2);
        return factor * g3;
    }

    private TetradMatrix center(TetradMatrix x0) {
        double[] mu0 = new double[x0.columns()];

        for (int j = 0; j < mu0.length; j++) {
            mu0[j] = StatUtils.mean(x0.getColumn(j).toArray());
        }

        for (int c = 0; c < x0.columns(); c++) {
            for (int r2 = 0; r2 < x0.rows(); r2++) {
                x0.set(r2, c, x0.get(r2, c) - mu0[c]);
            }
        }
        return x0;
    }

    private TetradMatrix getCov(int p, int[] continuousCols, List<Integer> cell) {
        TetradMatrix subset = getSubsample(p, continuousCols, cell);
        return new TetradMatrix(new Covariance(subset.getRealMatrix(),
                false).getCovarianceMatrix());
    }

    private TetradMatrix getSubsample(int p, int[] continuousCols, List<Integer> cell) {
        int n = cell.size();
        TetradMatrix subset = new TetradMatrix(n, p);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
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
    // of variables. This is the number of unique entries in the covariance matrix over X.
    private int h(List<ContinuousVariable> X) {
        int p = X.size();
        return p * (p + 1) / 2;
    }

    private double lognormal(TetradMatrix M, double[] x) {
        TetradMatrix Sigma = new TetradMatrix(new Covariance(M.getRealMatrix(),
                false).getCovarianceMatrix());
        TetradMatrix inv = Sigma.inverse();
        double det = Sigma.scalarMult(2.0 * Math.PI).det();

        double[] means = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            means[i] = StatUtils.mean(M.getColumn(i).toArray());
        }

        x = Arrays.copyOf(x, x.length);

        for (int i = 0; i < x.length; i++) {
            x[i] = x[i] - means[i];
        }

        TetradVector _x = new TetradVector(x);

        return -0.5 * Math.log(det) - 0.5 * inv.times(_x).dotProduct(_x);
    }
}



