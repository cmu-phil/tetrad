///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines,    //
// Joseph Ramsey, and Clark Glymour.                                         //
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
import edu.cmu.tetrad.util.dist.Discrete;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Calculates Mixed Variables Polynomial likelihood.
 *
 * @author Bryan Andrews
 */


public class MVPLikelihood {

    private DataSet dataSet;

    // The variables of the continuousData set.
    private List<Node> variables;

    // Indices of variables.
    private Map<Node, Integer> nodesHash;

    // Continuous data only.
    private double[][] continuousData;

    // Discrete data only.
    private int[][] discreteData;

    // Partitions
    private AdLeafTree adTree;

    // Fix degree
    private int fDegree;

    // Structure Prior
    private double structurePrior;

    public MVPLikelihood(DataSet dataSet, double structurePrior, int fDegree) {

        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.structurePrior = structurePrior;
        this.fDegree = fDegree;

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

        this.adTree = new AdLeafTree(dataSet);

    }

    private double multipleRegression(TetradVector Y, TetradMatrix X) {

        int n = X.rows();
        TetradVector r;

        try {
            TetradMatrix Xt = X.transpose();
            TetradMatrix XtX = Xt.times(X);
            r = X.times(XtX.inverse().times(Xt.times(Y))).minus(Y);
        } catch (Exception e) {
            TetradVector ones = new TetradVector(n);
            for (int i = 0; i < n; i++) ones.set(i,1);
            r = ones.scalarMult(ones.dotProduct(Y)/(double)n).minus(Y);
        }

        double sigma2 = r.dotProduct(r) / n;

        if(sigma2 <= 0) {
            TetradVector ones = new TetradVector(n);
            for (int i = 0; i < n; i++) ones.set(i,1);
            r = ones.scalarMult(ones.dotProduct(Y)/(double)Math.max(n,2)).minus(Y);
            sigma2 = r.dotProduct(r) / n;
        }

        double lik = -(n / 2) * (Math.log(2 * Math.PI) + Math.log(sigma2) + 1);

        if(Double.isInfinite(lik) || Double.isNaN(lik)) {
            System.out.println(lik);
        }

        return lik;
    }

    private double approxMultinomialRegression(TetradMatrix Y, TetradMatrix X) {

        int n = X.rows();
        int d = Y.columns();
        double lik = 0.0;
        TetradMatrix P;


        if(d >= n) {
            TetradMatrix ones = new TetradMatrix(n, 1);
            for (int i = 0; i < n; i++) ones.set(i, 0, 1);
            P = ones.times(ones.transpose().times(Y).scalarMult(1 / (double) n));
        } else {
            try {
                TetradMatrix Xt = X.transpose();
                TetradMatrix XtX = Xt.times(X);
                P = X.times(XtX.inverse().times(Xt.times(Y)));
            } catch (Exception e) {
                TetradMatrix ones = new TetradMatrix(n, 1);
                for (int i = 0; i < n; i++) ones.set(i, 0, 1);
                P = ones.times(ones.transpose().times(Y).scalarMult(1 / (double) n));
            }

            for (int i = 0; i < n; i++) {
                double min = 1;
                double center = 1 / (double) d;
                double bound = 1 / (double) n;
                for (int j = 0; j < d; j++) {
                    min = Math.min(min, P.get(i, j));
                }
                if (X.columns() > 1 && min < bound) {
                    min = (bound - center) / (min - center);
                    for (int j = 0; j < d; j++) {
                        P.set(i, j, min * P.get(i, j) + center * (1 - min));
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            lik += Math.log(P.getRow(i).dotProduct(Y.getRow(i)));
        }

        if(Double.isInfinite(lik) || Double.isNaN(lik)) {
            System.out.println(lik);
        }

        return lik;
    }

    public double getLik(int child_index, int[] parents) {

        double lik = 0;
        Node c = variables.get(child_index);
        List<ContinuousVariable> continuous_parents = new ArrayList<>();
        List<DiscreteVariable> discrete_parents = new ArrayList<>();
        for (int p : parents) {
            Node parent = variables.get(p);
            if (parent instanceof ContinuousVariable) {
                continuous_parents.add((ContinuousVariable) parent);
            } else {
                discrete_parents.add((DiscreteVariable) parent);
            }
        }

        int p = continuous_parents.size();

        List<List<Integer>> cells = adTree.getCellLeaves(discrete_parents);

        int[] continuousCols = new int[p];
        for (int j = 0; j < p; j++) continuousCols[j] = nodesHash.get(continuous_parents.get(j));

        for (List<Integer> cell : cells) {
            int r = cell.size();
            if (r > 1) {

                double[] mean = new double[p];
                double[] var = new double[p];
                for (int i = 0; i < p; i++) {
                    for (int j = 0; j < r; j++) {
                        mean[i] += continuousData[continuousCols[i]][cell.get(j)];
                        var[i] += Math.pow(continuousData[continuousCols[i]][cell.get(j)], 2);
                    }
                    mean[i] /= r;
                    var[i] /= r;
                    var[i] -= Math.pow(mean[i], 2);
                    var[i] = Math.sqrt(var[i]);

                    if (Double.isNaN(var[i])) {
                        System.out.println(var[i]);
                    }
                }

                int degree = fDegree;
                if (fDegree < 1) { degree = (int) Math.floor(Math.log(r)); }
                TetradMatrix subset = new TetradMatrix(r, p * degree + 1);
                for (int i = 0; i < r; i++) {
                    subset.set(i, p * degree, 1);
                    for (int j = 0; j < p; j++) {
                        for (int d = 0; d < degree; d++) {
                            subset.set(i, p * d + j, Math.pow((continuousData[continuousCols[j]][cell.get(i)] - mean[j]) / var[j], d + 1));
                        }
                    }
                }

                if (c instanceof ContinuousVariable) {
                    TetradVector target = new TetradVector(r);
                    for (int i = 0; i < r; i++) {
                        target.set(i, continuousData[child_index][cell.get(i)]);
                    }
                    lik += multipleRegression(target, subset);
                } else {
                    TetradMatrix target = new TetradMatrix(r, ((DiscreteVariable) c).getNumCategories());
                    for (int i = 0; i < r; i++) {
                        target.set(i, discreteData[child_index][cell.get(i)], 1);
                    }
                    lik += approxMultinomialRegression(target, subset);
                }
            }
        }

        return lik;
    }

    public double getDoF(int child_index, int[] parents) {

        double dof = 0;
        Node c = variables.get(child_index);
        List<ContinuousVariable> continuous_parents = new ArrayList<>();
        List<DiscreteVariable> discrete_parents = new ArrayList<>();
        for (int p : parents) {
            Node parent = variables.get(p);
            if (parent instanceof ContinuousVariable) {
                continuous_parents.add((ContinuousVariable) parent);
            } else {
                discrete_parents.add((DiscreteVariable) parent);
            }
        }

        int p = continuous_parents.size();

        List<List<Integer>> cells = adTree.getCellLeaves(discrete_parents);

        int[] continuousCols = new int[p];
        for (int j = 0; j < p; j++) continuousCols[j] = nodesHash.get(continuous_parents.get(j));

        for (List<Integer> cell : cells) {
            int r = cell.size();
            if (r > 0) {

                int degree = fDegree;
                if (fDegree < 1) { degree = (int) Math.floor(Math.log(r)); }
                if (c instanceof ContinuousVariable) {
                    dof += degree * continuous_parents.size() + 1;
                } else {
                    dof += ((degree * continuous_parents.size()) + 1) * (((DiscreteVariable) c).getNumCategories() - 1);
                }
            }
        }

        return dof;
    }

    public double getStructurePrior(int k) {

        double n = dataSet.getNumColumns() - 1;
        double p = structurePrior/n;

        if (structurePrior <= 0) { return 0; }
        return k*Math.log(p) + (n - k)*Math.log(1 - p);

    }
}
