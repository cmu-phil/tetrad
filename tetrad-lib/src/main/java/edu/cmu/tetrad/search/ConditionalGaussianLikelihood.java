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
 * Implements a conditional Gaussian BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianLikelihood {

    private DataSet dataSet;

    // The variables of the continuousData set.
    private List<Node> variables;

    // Indices of variables.
    private Map<Node, Integer> nodesHash;

    // Continuous data only.
    private double[][] continuousData;

    // Discrete data only.
    private int[][] discreteData;

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
        discreteData = new int[dataSet.getNumColumns()][];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);

            if (v instanceof ContinuousVariable) {
                double[] col = new double[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getDouble(i, j);
                }

                continuousData[j] = col;
            } else if (v instanceof DiscreteVariable) {
                int[] col = new int[dataSet.getNumRows()];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getInt(i, j);
                }

                discreteData[j] = col;
            }
        }

        nodesHash = new HashMap<>();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);
            nodesHash.put(v, j);
        }

        this.adTree = AdTrees.getAdLeafTree(dataSet);//   new AdLeafTree(dataSet);
    }

    private int getDof2(int i, int[] parents) {
        Node target = variables.get(i);

        int dof2;

        List<ContinuousVariable> X = new ArrayList<>();
        List<DiscreteVariable> A = new ArrayList<>();

        for (int parent1 : parents) {
            Node parent = variables.get(parent1);

            if (parent instanceof ContinuousVariable) {
                X.add((ContinuousVariable) parent);
            } else {
                A.add((DiscreteVariable) parent);
            }
        }

        if (target instanceof ContinuousVariable) {
            dof2 = f(A) * g(X);
        } else if (target instanceof DiscreteVariable) {
            List<DiscreteVariable> b = Collections.singletonList((DiscreteVariable) target);
            dof2 = f(A) * (f(b) - 1) + f(A) * f(b) * h(X);
        } else {
            throw new IllegalStateException();
        }

        return dof2;
    }

    public Ret getLikelihoodRatio(int i, int[] parents) {
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

        Ret ret1;
        Ret ret2;

        if (target instanceof DiscreteVariable && !XPlus.isEmpty()) {
            ret1 = getJointLikelihood(X, A, (DiscreteVariable) target);
            ret2 = getJointLikelihood(X, A, null);
        } else {
            ret1 = getJointLikelihood(XPlus, APlus, null);
            ret2 = getJointLikelihood(X, A, null);
        }

        double lik = ret1.getLik() - ret2.getLik();
        int dof = ret1.getDof() - ret2.getDof();
//        int dof = getDof2(i, parents);

        return new Ret(lik, dof);
    }

    // The likelihood of the joint over all of these variables, continuous and discrete.
    private Ret getJointLikelihood(List<ContinuousVariable> X, List<DiscreteVariable> A, DiscreteVariable B) {

//        List<List<Integer>> cells = getCellsOriginal(A);
        if (B == null) {
            int p = X.size();

            List<List<Integer>> cells = adTree.getCellLeaves(A);

            int[] continuousCols = new int[p];
            for (int j = 0; j < p; j++) continuousCols[j] = nodesHash.get(X.get(j));
            int N = dataSet.getNumRows();
            double lik = 0;

            for (List<Integer> cell : cells) {
                int n = cell.size();

                if (A.size() > 0) {
                    if (n > 0) {
                        double prob = n / (double) N;
                        lik += n * Math.log(prob);
                    }
                }

                if (X.size() > 0) {
                    if (n > 3 * p) {
                        TetradMatrix subset = new TetradMatrix(n, p);

                        for (int i = 0; i < n; i++) {
                            for (int j = 0; j < p; j++) {
                                subset.set(i, j, continuousData[continuousCols[j]][cell.get(i)]);
                            }
                        }

                        TetradMatrix Sigma = new TetradMatrix(new Covariance(subset.getRealMatrix(),
                                false).getCovarianceMatrix());
                        double det = Sigma.det();
                        lik -= 0.5 * n * Math.log(det);
                    }

                    lik -= 0.5 * n * p * (1.0 + Math.log(2.0 * Math.PI));
                }
            }

            int dof = f(A) * h(X) + f(A);
            return new Ret(lik, dof);
        } else {
            int N = dataSet.getNumRows();

            int p = X.size();

            List<List<List<Integer>>> cells = adTree.getCellLeaves(A, B);

            int[] continuousCols = new int[p];
            for (int j = 0; j < p; j++) continuousCols[j] = nodesHash.get(X.get(j));
            double lik = 0;

            for (List<List<Integer>> cells1 : cells) {

                int cell1Size = 0;
                double mixedProb = 0;

                // times P(A U {B})
                if (A.size() > 0) {
                    for (List<Integer> _cell : cells1) {
                        int n1 = _cell.size();

                        if (n1 > 0) {
                            double prob = n1 / (double) N;
                            mixedProb += n1 * Math.log(prob);
                        }
                    }
                }

                for (List<Integer> cell2 : cells1) {
                    int cell2Size = cell2.size();

                    // For a single record.
                    double s = -0.5 * p * (1.0 + Math.log(2.0 * Math.PI));

                    if (cell2Size > p) {
                        TetradMatrix subset = new TetradMatrix(cell2Size, p);

                        for (int i = 0; i < cell2Size; i++) {
                            for (int j = 0; j < p; j++) {
                                subset.set(i, j, continuousData[continuousCols[j]][cell2.get(i)]);
                            }
                        }

                        TetradMatrix Sigma = new TetradMatrix(new Covariance(subset.getRealMatrix(),
                                false).getCovarianceMatrix());
                        double det = Sigma.det();

                        s = -0.5 * Math.log(det);
                    }

                    double w = cell2Size / (double) cell1Size;
                    mixedProb += w * Math.exp(s);
                }

                lik += cell1Size * Math.log(mixedProb);
            }

            List<DiscreteVariable> A2 = new ArrayList<>(A);
            A2.add(B);

            int dof = 2 * f(A) * h(X) + f(A2);
            return new Ret(lik, dof);
        }
    }

    private List<List<Integer>> getCellsOriginal(List<DiscreteVariable> A) {
        int d = A.size();

        // For each combination of values for the A guys extract a subset of the data.
        int[] discreteCols = new int[d];
        int[] dims = new int[d];
        int n = dataSet.getNumRows();

        for (int i = 0; i < d; i++) discreteCols[i] = nodesHash.get(A.get(i));
        for (int i = 0; i < d; i++) dims[i] = A.get(i).getNumCategories();

        List<List<Integer>> cells = new ArrayList<>();
        for (int i = 0; i < f(A); i++) {
            cells.add(new ArrayList<Integer>());
        }

        int[] values = new int[A.size()];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < A.size(); j++) {
                values[j] = discreteData[discreteCols[j]][i];
            }

            int rowIndex = getRowIndex(values, dims);
            cells.get(rowIndex).add(i);
        }

        return cells;
    }

    private AdLeafTree adTree;

    public class Ret {
        private double lik;
        private int dof;

        public Ret(double lik, int dof) {
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

    private int h(List<ContinuousVariable> X) {
        int p = X.size();
        return p * (p + 1) / 2;
    }

    private int j(List<DiscreteVariable> A) {
        int v = 1;

        for (DiscreteVariable a : A) {
            v *= a.getNumCategories() - 1;
        }

        return v;
    }

    public int getRowIndex(int[] values, int[] dims) {
        int rowIndex = 0;

        for (int i = 0; i < dims.length; i++) {
            rowIndex *= dims[i];
            rowIndex += values[i];
        }

        return rowIndex;
    }
}



