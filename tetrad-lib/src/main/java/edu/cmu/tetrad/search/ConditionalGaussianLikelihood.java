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

//        if (target instanceof DiscreteVariable && !X.isEmpty()) {
//
//            // A case like P(C | X) = P(X | C) P(C) / P(X). In this case, we assume X is
//            // distributed as conditional Gaussian and calculate P(X) as a mixture.
//            Ret ret1 = getJointLikelihood(X, A, (DiscreteVariable) target);
//            Ret ret2 = getJointLikelihood(X, A, null);
//
//            double lik = ret1.getLik() - ret2.getLik();
//            int dof = ret1.getDof() - ret2.getDof();
//            return new Ret(lik, dof);
//        } else {

            // Handles cases like P(X | C) where X is conditional Gaussian as well as
            // P(A | C) where A, C are both discrete.
            Ret ret1 = getJointLikelihood(XPlus, APlus, null);
            Ret ret2 = getJointLikelihood(X, A, null);

            double lik = ret1.getLik() - ret2.getLik();
            int dof = ret1.getDof() - ret2.getDof();
            return new Ret(lik, dof);
//        }
    }

    // The likelihood of the joint over all of these variables, continuous and discrete.
    private Ret getJointLikelihood(List<ContinuousVariable> X, List<DiscreteVariable> A, DiscreteVariable B) {
        int p = X.size();
        final int minSample = Math.min(p, 5);

        int[] continuousCols = new int[p];
        for (int j = 0; j < p; j++) continuousCols[j] = nodesHash.get(X.get(j));
        int N = dataSet.getNumRows();

        final double C = -0.5 * N * p * (1.0 + Math.log(2.0 * Math.PI));

        if (B == null) {
            List<List<Integer>> cells = adTree.getCellLeaves(A);
            double c1 = 0, c2 = C;
            List<Integer> missing = new ArrayList<>();

            for (List<Integer> cell : cells) {
                if (cell.isEmpty()) continue;
                int n = cell.size();

                c1 += n * Math.log((double) n);

                if (X.size() > 0) {
                    if (n > minSample) {
                        TetradMatrix Sigma = getDataSubsample(p, continuousCols, cell);
                        double det = Sigma.det();
                        c2 -= 0.5 * n * Math.log(det);
                    } else {
                        missing.addAll(cell);

//                        if (missing.size() > minSample) {
//                            TetradMatrix Sigma = getDataSubsample(p, continuousCols, missing);
//                            double det = Sigma.det();
//                            c2 -= 0.5 * missing.size() * Math.log(det);
//                            missing.clear();
//                        }
                    }
                }
            }

            if (X.size() > 0) {
                if (missing.size() > minSample) {
                    TetradMatrix Sigma = getDataSubsample(p, continuousCols, missing);
                    double det = Sigma.det();
                    c2 -= 0.5 * missing.size() * Math.log(det);
                }
            }

            c1 -= N * Math.log((double) N);

            double lik = c1 + c2;
            int dof = f(A) * h(X) + f(A);

            return new Ret(lik, dof);
        } else { // B supplied.
            List<List<Integer>> cells = adTree.getCellLeaves(A);
            double c1 = C, c2 = 0, c3 = C;

            List<Integer> missing1 = new ArrayList<>();

            for (List<Integer> cell : cells) {
                if (cell.isEmpty()) continue;
                int n = cell.size();

                if (X.size() > 0) {
                    if (n > minSample) {
                        TetradMatrix Sigma = getDataSubsample(p, continuousCols, cell);
                        double det = Sigma.det();
                        c1 -= 0.5 * n * Math.log(det);
                    } else {
                        missing1.addAll(cell);

//                        if (missing1.size() > minSample) {
//                            TetradMatrix Sigma = getDataSubsample(p, continuousCols, missing1);
//                            double det = Sigma.det();
//                            c2 -= 0.5 * missing1.size() * Math.log(det);
//                            missing1.clear();
//                        }
                    }
                }
            }

            if (X.size() > 0) {
                if (missing1.size() > minSample) {
                    TetradMatrix Sigma = getDataSubsample(p, continuousCols, missing1);
                    double det = Sigma.det();
                    c1 -= 0.5 * missing1.size() * Math.log(det);
                }
            }

            List<DiscreteVariable> APlus = new ArrayList<>(A);
            APlus.add(B);
            cells = adTree.getCellLeaves(APlus);

            List<Integer> missing2 = new ArrayList<>();

            for (List<Integer> cell : cells) {
                if (cell.isEmpty()) continue;
                int n = cell.size();

                c2 += n * Math.log((double) n);

                if (X.size() > 0) {
                    if (n > minSample) {
                        TetradMatrix Sigma = getDataSubsample(p, continuousCols, cell);
                        double det = Sigma.det();
                        c3 -= 0.5 * n * Math.log(det);
                    } else {
                        missing2.addAll(cell);

//                        if (missing2.size() > minSample) {
//                            TetradMatrix Sigma = getDataSubsample(p, continuousCols, missing2);
//                            double det = Sigma.det();
//                            c2 -= 0.5 * missing2.size() * Math.log(det);
//                            missing2.clear();
//                        }
                    }
                }

//                if (X.size() > 0) {
//                    if (missing2.size() > minSample) {
//                        TetradMatrix Sigma = getDataSubsample(p, continuousCols, missing2);
//                        double det = Sigma.det();
//                        c3 -= 0.5 * missing2.size() * Math.log(det);
//                    }
//                }
            }

            c2 -= N * Math.log((double) N);

            double lik = Math.max(c1, c3) + c2;
            int dof = f(APlus) * h(X) + g(APlus);

            return new Ret(lik, dof);
        }
    }

    private TetradMatrix getDataSubsample(int p, int[] continuousCols, List<Integer> cell) {
        int n = cell.size();
        TetradMatrix subset = new TetradMatrix(n, p);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                subset.set(i, j, continuousData[continuousCols[j]][cell.get(i)]);
            }
        }

        return new TetradMatrix(new Covariance(subset.getRealMatrix(),
                false).getCovarianceMatrix());
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

    private int g(List<DiscreteVariable> A) {
        int f = 1;

        for (int i = 0; i < A.size() - 1; i++) {
            f *= A.get(i).getNumCategories();
        }

        if (!A.isEmpty()) {
            f *= A.get(A.size() - 1).getNumCategories() - 1;
        }

        return f;
    }

    // Degrees of freedom for a multivariate Gaussian distribution is p * (p + 1) / 2, where p is the number
    // of variables. This is the number of unique entries in the covariance matrix over X.
    private int h(List<ContinuousVariable> X) {
        int p = X.size();
        return p * (p + 1) / 2;
    }
}



