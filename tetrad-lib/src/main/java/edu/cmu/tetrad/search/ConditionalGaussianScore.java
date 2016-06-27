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
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.stat.correlation.Covariance;

import java.util.*;

/**
 * Implements a conditional Gaussian BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianScore implements Score {

    private DataSet dataSet;

    // The variables of the continuousData set.
    private List<Node> variables;

    // Continuous data only.
    private double[][] continuousData;

    // Discrete data only.
    private int[][] discreteData;

    // Indices of variables.
    private Map<Node, Integer> nodesHash;

    /**
     * Constructs the score using a covariance matrix.
     */
    public ConditionalGaussianScore(DataSet dataSet) {
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
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        Node b = variables.get(i);

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

        List<ContinuousVariable> X2 = new ArrayList<>(X);
        List<DiscreteVariable> A2 = new ArrayList<>(A);

        if (b instanceof ContinuousVariable) {
            X2.add((ContinuousVariable) b);
        } else if (b instanceof DiscreteVariable) {
            A2.add((DiscreteVariable) b);
        }

        Ret ret1 = getJointLikelihood(X2, A2);
        Ret ret2 = getJointLikelihood(X, A);

        double lik = ret1.getLik() - ret2.getLik();
        double dof = ret1.getDof() - ret2.getDof();
//        double dof = Math.max(ret1.getDof(), ret2.getDof());
//        double dof = ret1.getDof();

        int N = dataSet.getNumRows();

//        if (b instanceof ContinuousVariable) {
//            dof = f(A) * g(X);
//        } else if (b instanceof  DiscreteVariable) {
//            List<DiscreteVariable> _b = Collections.singletonList((DiscreteVariable) b);
//            dof = f(A) * (f(_b) - 1) + f(A) * f(_b) * h(X);
//        } else {
//            throw new IllegalStateException();
//        }

//        return new ChiSquaredDistribution(dof).cumulativeProbability(lik) - 0.001;
//
        return 2 * lik - dof * Math.log(N);
    }

    // The likelihood of the joint over all of these variables, continuous and discrete.
    private Ret getJointLikelihood(List<ContinuousVariable> C, List<DiscreteVariable> D) {
        int c = C.size();
        int d = D.size();

        if (c == 0 && d == 0) return new Ret(0, 0);

        // For each combination of values for the D guys extract a subset of the data.
        int[] discreteCols = new int[d];
        int[] continuousCols = new int[c];
        int[] dims = new int[d];

        for (int i = 0; i < d; i++) discreteCols[i] = nodesHash.get(D.get(i));
        for (int j = 0; j < c; j++) continuousCols[j] = nodesHash.get(C.get(j));
        for (int i = 0; i < d; i++) dims[i] = D.get(i).getNumCategories();

        int s = 1;
        for (int dim : dims) s *= dim;
        List<List<Integer>> cells = new ArrayList<>();
        for (int i = 0; i < s; i++) {
            cells.add(new ArrayList<Integer>());
        }

        int[] values = new int[dims.length];

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dims.length; j++) {
                values[j] = discreteData[discreteCols[j]][i];
            }

            int rowIndex = getRowIndex(values, dims);
            cells.get(rowIndex).add(i);
        }

        int N = dataSet.getNumRows();

        double lik = 0;

        for (int k = 0; k < cells.size(); k++) {
            if (cells.get(k).isEmpty()) continue;

            int r = cells.get(k).size();

            if (r > 0) {
                double prob = r / (double) N;
                lik += r * Math.log(prob);
            }
        }

        // The likelihood of the joint of the discrete variables.
        if (C.size() > 0) {
            for (int k = 0; k < cells.size(); k++) {
                if (cells.get(k).isEmpty()) continue;

                int r = cells.get(k).size();

                if (r > c) {
                    TetradMatrix subset = new TetradMatrix(r, c);

                    for (int i = 0; i < r; i++) {
                        for (int j = 0; j < c; j++) {
                            subset.set(i, j, continuousData[continuousCols[j]][cells.get(k).get(i)]);
                        }
                    }

                    TetradMatrix Sigma = new TetradMatrix(new Covariance(subset.getRealMatrix(),
                            false).getCovarianceMatrix());
                    lik -= 0.5 * r * Math.log(Sigma.det());
                    lik -= 0.5 * r * c * (1.0 + Math.log(2.0 * Math.PI));
                } else {
                    lik -= 0.5 * r * Math.log(c);
                    lik -= 0.5 * r * c * (1.0 + Math.log(2.0 * Math.PI));
                }
            }
        }

        int t = c == 0 ? 1 : c * (c + 1) / 2;
        double dof = t;// + s * c - 1;

//        dof = t;

        return new Ret(lik, dof);
    }

    private int f(List<DiscreteVariable> A) {
        int f = 1;

        for (DiscreteVariable V : A) {
            f *= V.getNumCategories();
        }

        return f;
    }

    private int g(List<ContinuousVariable> X) {
        if (X.isEmpty()) {
            return 1;
        } else {
            return X.size();
        }
    }

    private int h(List<ContinuousVariable> X) {
        int p = X.size();
        return p * (p + 1) / 2;
    }

    private class Ret {
        private double lik;
        private double dof;

        public Ret(double lik, double dof) {
            this.lik = lik;
            this.dof = dof;
        }

        public double getLik() {
            return lik;
        }

        public double getDof() {
            return dof;
        }

        public String toString() {
            return "lik = " + lik + " dof = " + dof;
        }

    }

    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScore(y, x) - localScore(y);
    }

    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */
    public double localScore(int i, int parent) {
        return localScore(i, new int[]{parent});
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(int i) {
        return localScore(i, new int[0]);
    }

    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > -100;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public double getParameter1() {
        return 0;
    }

    @Override
    public void setParameter1(double alpha) {

    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxIndegree() {
        return (int) Math.ceil(Math.log(dataSet.getNumRows()));
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



