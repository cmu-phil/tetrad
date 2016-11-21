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

    // True if the exact algorithm should be used (slower).
    private boolean exact = false;

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

        if (exact) {
            if (target instanceof DiscreteVariable && !X.isEmpty()) {
                return new Ret(getLikelihood2(X, A, (DiscreteVariable) target), dofExact(X, A, target));
            } else {
                double lnL1 = getJointLikelihood(XPlus, APlus, target);
                double lnL2 = getJointLikelihood(X, A, target);
                double lik = lnL1 - lnL2;
                return new Ret(lik, dofExact(X, A, target));
            }
        } else { // heuristic.
            double lnL1 = getJointLikelihood(XPlus, APlus, target);
            double lnL2 = getJointLikelihood(X, A, target);
            double lik = lnL1 - lnL2;
            int dof = f(APlus) * h(XPlus) + f(APlus) - (f(A) * h(X) + f(A));
            return new Ret(lik, dof);
        }
    }

    /**
     * @return True if the exact method is being used for P(C | X).
     */
    public boolean isExact() {
        return exact;
    }

    /**
     * @param exact True if the exact method is being used for P(C | X).
     */
    public void setExact(boolean exact) {
        this.exact = exact;
    }

    // The likelihood of the joint over all of these variables, assuming conditional Gaussian,
    // continuous and discrete.
    private double getJointLikelihood(List<ContinuousVariable> X, List<DiscreteVariable> A, Node B) {
        int k = X.size();

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = nodesHash.get(X.get(j));
        int N = dataSet.getNumRows();

        double c1 = 0, c2 = 0;

        List<List<Integer>> cells = adTree.getCellLeaves(A);

        for (List<Integer> cell : cells) {
            int a = cell.size();
            if (a == 0) continue;

            if (A.size() > 0) {
                final double u = a * Math.log(a) - a * Math.log(N);

                if (!Double.isNaN(u)) {
                    c1 += u;
                }
            }

            if (k > 0) {
                double v;

                try {
                    TetradMatrix Sigma = cov(getSubsample(continuousCols, cell));
                    v = -0.5 * a * Math.log(Sigma.det()) - 0.5 * a * k - 0.5 * a * k * Math.log(2.0 * Math.PI);
                } catch (Exception e) {

                    // Use the global covariances for cases where the usual covariances fail.
                    List<Integer> all = new ArrayList<>();
                    for (int i = 0; i < N; i++) all.add(i);
                    TetradMatrix subsample2 = getSubsample(continuousCols, all);
                    TetradMatrix Sigma = cov(subsample2);

                    v = -0.5 * a * Math.log(Sigma.det()) - 0.5 * a * k - 0.5 * a * k * Math.log(2.0 * Math.PI);
                }

                if (!Double.isNaN(v)) {
                    c2 += v;
                }
            }
        }

        return c1 + c2;
    }

    // For cases like P(C | X). This is a ratio of joints, but if the numerator is conditional Gaussian,
    // the denominator is a mixture of Gaussians.
    private double getLikelihood2(List<ContinuousVariable> X, List<DiscreteVariable> A, DiscreteVariable B) {
        int k = X.size();

        int[] continuousCols = new int[k];
        for (int j = 0; j < k; j++) continuousCols[j] = nodesHash.get(X.get(j));
        double lnL = 0.0;

        int N = dataSet.getNumRows();

        List<List<List<Integer>>> cells = adTree.getCellLeaves(A, B);

        for (List<List<Integer>> mycells : cells) {

            // Get the subsample for each cell.
            List<TetradMatrix> x = new ArrayList<>();
            List<TetradMatrix> sigmas = new ArrayList<>();
            List<TetradMatrix> inv = new ArrayList<>();
            List<TetradVector> mu = new ArrayList<>();

            for (List<Integer> cell : mycells) {
                final TetradMatrix subsample = getSubsample(continuousCols, cell);

                try {
                    TetradMatrix cov = cov(subsample);
                    TetradMatrix covinv = cov.inverse();
                    x.add(subsample);
                    sigmas.add(cov);
                    inv.add(covinv);
                    mu.add(means(subsample));
                } catch (Exception e) {

                    // Use the global covariances for cases where the usual covariances fail.
                    List<Integer> all = new ArrayList<>();
                    for (int i = 0; i < N; i++) all.add(i);
                    TetradMatrix subsample2 = getSubsample(continuousCols, all);
                    TetradMatrix cov = cov(subsample2);
                    TetradMatrix covinv = cov.inverse();
                    x.add(subsample);
                    sigmas.add(cov);
                    inv.add(covinv);
                    mu.add(means(subsample2));
                }
            }

            // For each cell calculate k * (2 PI * \sigma|) ^ -1/2.
            List<Double> factors = new ArrayList<>();

            for (int u = 0; u < x.size(); u++) {
                factors.add(Math.pow(Math.pow(2.0 * Math.PI, k) * sigmas.get(u).det(), -0.5));
            }

            // Set up container for all of the a's.
            List<List<List<Double>>> a = new ArrayList<>();

            for (int u = 0; u < x.size(); u++) {
                a.add(new ArrayList<List<Double>>());

                for (int i = 0; i < x.size(); i++) {
                    a.get(u).add(new ArrayList<Double>());
                }
            }

            // Calculate probabilities of all of the pieces, a's.
            for (int u = 0; u < x.size(); u++) {
                for (int v = 0; v < x.size(); v++) {
                    for (int i = 0; i < x.get(u).rows(); i++) {
                        final TetradVector row = x.get(u).getRow(i).minus(mu.get(v));
                        double g = prob(factors.get(v), inv.get(v), row);
                        a.get(u).get(v).add(g);
                    }
                }
            }

            // Calculate numerator and denominator for each record and add the logs of num / denom.
            for (int u = 0; u < x.size(); u++) {
                for (int i = 0; i < x.get(u).rows(); i++) {
                    double num = a.get(u).get(u).get(i) * (x.get(u).rows() / (double) N);
                    double denom = 0;

                    for (int v = 0; v < x.size(); v++) {
                        denom += a.get(u).get(v).get(i) * (x.get(v).rows() / (double) N);
                    }

                    final double v = Math.log(num) - Math.log(denom);

                    if (!Double.isNaN(v)) {
                        lnL += v;
                    }
                }
            }
        }

        return lnL;
    }

    private int dofExact(List<ContinuousVariable> X, List<DiscreteVariable> A, Node target) {
        if (target instanceof ContinuousVariable) {
            return f(A) * g(X);
        } else if (target instanceof DiscreteVariable) {
            List<DiscreteVariable> b = Collections.singletonList((DiscreteVariable) target);
            return f(A) * (f(b) - 1) + f(A) * f(b) * h(X);
        }

        throw new IllegalStateException();
    }

    private TetradMatrix cov(TetradMatrix x) {
        return new TetradMatrix(new Covariance(x.getRealMatrix(),
                false).getCovarianceMatrix());
    }

    private double prob(Double factor, TetradMatrix inv, TetradVector x) {
        return factor * Math.exp(-0.5 * inv.times(x).dotProduct(x));
    }

    // Calculates the means of the columns of x.
    private TetradVector means(TetradMatrix x) {
        return x.sum(1).scalarMult(1.0 / x.rows());
    }

    // Subsample of the continuous variables conditioning on the given cell.
    private TetradMatrix getSubsample(int[] continuousCols, List<Integer> cell) {
        int n = cell.size();
        TetradMatrix subset = new TetradMatrix(n, continuousCols.length);

        for (int i = 0; i < n; i++) {
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

    private int g(List<ContinuousVariable> X) {
        return X.size() + 1;
    }

    // Degrees of freedom for a multivariate Gaussian distribution is p * (p + 1) / 2, where p is the number
    // of variables. This is the number of unique entries in the covariance matrix over X.
    private int h(List<ContinuousVariable> X) {
        int p = X.size();
        return p * (p + 1) / 2;
    }
}



