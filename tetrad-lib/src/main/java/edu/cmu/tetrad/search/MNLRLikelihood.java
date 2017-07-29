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

import de.bwaldvogel.liblinear.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;


/**
 * Calculates Mixed Variables Polynomial likelihood.
 *
 * @author Bryan Andrews
 */


public class MNLRLikelihood {

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

    private PrintStream original = System.out;

    private PrintStream nullout = new PrintStream(new OutputStream() { public void write(int b) {
        //DO NOTHING
    }
    });

    public MNLRLikelihood(DataSet dataSet, double structurePrior, int fDegree) {

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
        //List<List<Integer>> cells = partition(discrete_parents);

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
                    ArrayList<Integer> temp = new ArrayList<>();
                    TetradMatrix target = new TetradMatrix(r, ((DiscreteVariable) c).getNumCategories());
                    for (int i = 0; i < r; i++) {
                        for (int j = 0; j < ((DiscreteVariable) c).getNumCategories(); j++) {
                            target.set(i, j, -1);
                        }
                        target.set(i, discreteData[child_index][cell.get(i)], 1);
                    }
                    lik += MultinomialLogisticRegression(target, subset);
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
        //List<List<Integer>> cells = partition(discrete_parents, 0).cells;

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

        if (structurePrior < 0) { return getEBICprior(); }

        double n = dataSet.getNumColumns() - 1;
        double p = structurePrior/n;

        if (structurePrior == 0) { return 0; }
        return k*Math.log(p) + (n - k)*Math.log(1 - p);

    }

    public double getEBICprior() {

        double n = dataSet.getNumColumns();
        double gamma = -structurePrior;
        return gamma * Math.log(n);

    }

    private double MultinomialLogisticRegression(TetradMatrix targets, TetradMatrix subset) {

        Problem problem = new Problem();
        problem.l = targets.rows(); // number of training examples
        problem.n = subset.columns(); // number of features
        problem.x = new FeatureNode[problem.l][problem.n]; // feature nodes
        problem.bias = 0;
        for (int i = 0; i < problem.l; i ++) {
            for (int j = 0; j < problem.n; j++) {
                problem.x[i][j] = new FeatureNode(j+1, subset.get(i,j));
            }
        }
        SolverType solver = SolverType.L2R_LR; // -s 0
        double C = 1.0;    // cost of constraints violation
        double eps = 1e-4; // stopping criteria
        Parameter parameter = new Parameter(solver, C, eps);
        ArrayList<Model> models = new ArrayList<>();
        double lik = 0;
        double num;
        double den;

        for (int i = 0; i < targets.columns(); i++) {
            System.setOut(nullout);
            problem.y = targets.getColumn(i).toArray(); // target values
            models.add(i, Linear.train(problem, parameter));
            System.setOut(original);
        }

        for (int j = 0; j < problem.l; j++) {
            num = 0;
            den = 0;
            for (int i = 0; i < targets.columns(); i++) {
                double[] p = new double[models.get(i).getNrClass()];
                Linear.predictProbability(models.get(i), problem.x[j], p);
                if (targets.get(j,i) == 1) {
                    num = p[0];
                    den += p[0];
                } else if (p.length > 1) {
                    den += p[0];
                }
            }
            lik += Math.log(num/den);
        }

        return lik;

    }

}
