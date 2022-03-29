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
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Calculates Mixed Variables Polynomial likelihood.
 *
 * @author Bryan Andrews
 */


public class MNLRLikelihood {

    private final DataSet dataSet;

    // The variables of the continuousData set.
    private final List<Node> variables;

    // Indices of variables.
    private final Map<Node, Integer> nodesHash;

    // Continuous data only.
    private final double[][] continuousData;

    // Discrete data only.
    private final int[][] discreteData;

    // Partitions
    private final AdLeafTree adTree;

    // Fix degree
    private final int fDegree;

    // Structure Prior
    private final double structurePrior;

    private final PrintStream original = System.out;

    private final PrintStream nullout = new PrintStream(new OutputStream() {
        public void write(final int b) {
            //DO NOTHING
        }
    });

    public MNLRLikelihood(final DataSet dataSet, final double structurePrior, final int fDegree) {

        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.structurePrior = structurePrior;
        this.fDegree = fDegree;

        this.continuousData = new double[dataSet.getNumColumns()][];
        this.discreteData = new int[dataSet.getNumColumns()][];
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final Node v = dataSet.getVariable(j);
            if (v instanceof ContinuousVariable) {
                final double[] col = new double[dataSet.getNumRows()];
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getDouble(i, j);
                }
                this.continuousData[j] = col;
            } else if (v instanceof DiscreteVariable) {
                final int[] col = new int[dataSet.getNumRows()];
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getInt(i, j);
                }
                this.discreteData[j] = col;
            }
        }

        this.nodesHash = new HashMap<>();
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final Node v = dataSet.getVariable(j);
            this.nodesHash.put(v, j);
        }

        this.adTree = new AdLeafTree(dataSet);

    }

    private double multipleRegression(final Vector Y, final Matrix X) {

        final int n = X.rows();
        Vector r;

        try {
            final Matrix Xt = X.transpose();
            final Matrix XtX = Xt.times(X);
            r = X.times(XtX.inverse().times(Xt.times(Y))).minus(Y);
        } catch (final Exception e) {
            final Vector ones = new Vector(n);
            for (int i = 0; i < n; i++) ones.set(i, 1);
            r = ones.scalarMult(ones.dotProduct(Y) / (double) n).minus(Y);
        }

        double sigma2 = r.dotProduct(r) / n;

        if (sigma2 <= 0) {
            final Vector ones = new Vector(n);
            for (int i = 0; i < n; i++) ones.set(i, 1);
            r = ones.scalarMult(ones.dotProduct(Y) / (double) Math.max(n, 2)).minus(Y);
            sigma2 = r.dotProduct(r) / n;
        }

        final double lik = -(n / 2.) * (Math.log(2 * Math.PI) + Math.log(sigma2) + 1);

        if (Double.isInfinite(lik) || Double.isNaN(lik)) {
            System.out.println(lik);
        }

        return lik;
    }

    public double getLik(final int child_index, final int[] parents) {

        double lik = 0;
        final Node c = this.variables.get(child_index);
        final List<ContinuousVariable> continuous_parents = new ArrayList<>();
        final List<DiscreteVariable> discrete_parents = new ArrayList<>();
        for (final int p : parents) {
            final Node parent = this.variables.get(p);
            if (parent instanceof ContinuousVariable) {
                continuous_parents.add((ContinuousVariable) parent);
            } else {
                discrete_parents.add((DiscreteVariable) parent);
            }
        }

        final int p = continuous_parents.size();

        final List<List<Integer>> cells = this.adTree.getCellLeaves(discrete_parents);
        //List<List<Integer>> cells = partition(discrete_parents);

        final int[] continuousCols = new int[p];
        for (int j = 0; j < p; j++) continuousCols[j] = this.nodesHash.get(continuous_parents.get(j));

        for (final List<Integer> cell : cells) {
            final int r = cell.size();
            if (r > 1) {

                final double[] mean = new double[p];
                final double[] var = new double[p];
                for (int i = 0; i < p; i++) {
                    for (final Integer integer : cell) {
                        mean[i] += this.continuousData[continuousCols[i]][integer];
                        var[i] += Math.pow(this.continuousData[continuousCols[i]][integer], 2);
                    }
                    mean[i] /= r;
                    var[i] /= r;
                    var[i] -= Math.pow(mean[i], 2);
                    var[i] = Math.sqrt(var[i]);

                    if (Double.isNaN(var[i])) {
                        System.out.println(var[i]);
                    }
                }

                int degree = this.fDegree;
                if (this.fDegree < 1) {
                    degree = (int) Math.floor(Math.log(r));
                }
                final Matrix subset = new Matrix(r, p * degree + 1);
                for (int i = 0; i < r; i++) {
                    subset.set(i, p * degree, 1);
                    for (int j = 0; j < p; j++) {
                        for (int d = 0; d < degree; d++) {
                            subset.set(i, p * d + j, Math.pow((this.continuousData[continuousCols[j]][cell.get(i)] - mean[j]) / var[j], d + 1));
                        }
                    }
                }

                if (c instanceof ContinuousVariable) {
                    final Vector target = new Vector(r);
                    for (int i = 0; i < r; i++) {
                        target.set(i, this.continuousData[child_index][cell.get(i)]);
                    }
                    lik += multipleRegression(target, subset);
                } else {
                    final Matrix target = new Matrix(r, ((DiscreteVariable) c).getNumCategories());
                    for (int i = 0; i < r; i++) {
                        for (int j = 0; j < ((DiscreteVariable) c).getNumCategories(); j++) {
                            target.set(i, j, -1);
                        }
                        target.set(i, this.discreteData[child_index][cell.get(i)], 1);
                    }
                    lik += MultinomialLogisticRegression(target, subset);
                }
            }
        }

        return lik;
    }

    public double getDoF(final int child_index, final int[] parents) {

        double dof = 0;
        final Node c = this.variables.get(child_index);
        final List<ContinuousVariable> continuous_parents = new ArrayList<>();
        final List<DiscreteVariable> discrete_parents = new ArrayList<>();
        for (final int p : parents) {
            final Node parent = this.variables.get(p);
            if (parent instanceof ContinuousVariable) {
                continuous_parents.add((ContinuousVariable) parent);
            } else {
                discrete_parents.add((DiscreteVariable) parent);
            }
        }

//        int p = continuous_parents.size();

        final List<List<Integer>> cells = this.adTree.getCellLeaves(discrete_parents);
        //List<List<Integer>> cells = partition(discrete_parents, 0).cells;

//        int[] continuousCols = new int[p];
//        for (int j = 0; j < p; j++) continuousCols[j] = nodesHash.get(continuous_parents.get(j));

        for (final List<Integer> cell : cells) {
            final int r = cell.size();
            if (r > 0) {

                int degree = this.fDegree;
                if (this.fDegree < 1) {
                    degree = (int) Math.floor(Math.log(r));
                }
                if (c instanceof ContinuousVariable) {
                    dof += degree * continuous_parents.size() + 1;
                } else {
                    dof += ((degree * continuous_parents.size()) + 1) * (((DiscreteVariable) c).getNumCategories() - 1);
                }
            }
        }

        return dof;
    }

    public double getStructurePrior(final int k) {

        if (this.structurePrior < 0) {
            return getEBICprior();
        }

        final double n = this.dataSet.getNumColumns() - 1;
        final double p = this.structurePrior / n;

        if (this.structurePrior == 0) {
            return 0;
        }
        return k * Math.log(p) + (n - k) * Math.log(1 - p);

    }

    public double getEBICprior() {

        final double n = this.dataSet.getNumColumns();
        final double gamma = -this.structurePrior;
        return gamma * Math.log(n);

    }

    private double MultinomialLogisticRegression(final Matrix targets, final Matrix subset) {

        final Problem problem = new Problem();
        problem.l = targets.rows(); // number of training examples
        problem.n = subset.columns(); // number of features
        problem.x = new FeatureNode[problem.l][problem.n]; // feature nodes
        problem.bias = 0;
        for (int i = 0; i < problem.l; i++) {
            for (int j = 0; j < problem.n; j++) {
                problem.x[i][j] = new FeatureNode(j + 1, subset.get(i, j));
            }
        }
        final SolverType solver = SolverType.L2R_LR; // -s 0
        final double C = 1.0;    // cost of constraints violation
        final double eps = 1e-4; // stopping criteria
        final Parameter parameter = new Parameter(solver, C, eps);
        final ArrayList<Model> models = new ArrayList<>();
        double lik = 0;
        double num;
        double den;

        for (int i = 0; i < targets.columns(); i++) {
            System.setOut(this.nullout);
            problem.y = targets.getColumn(i).toArray(); // target values
            models.add(i, Linear.train(problem, parameter));
            System.setOut(this.original);
        }

        for (int j = 0; j < problem.l; j++) {
            num = 0;
            den = 0;
            for (int i = 0; i < targets.columns(); i++) {
                final double[] p = new double[models.get(i).getNrClass()];
                Linear.predictProbability(models.get(i), problem.x[j], p);
                if (targets.get(j, i) == 1) {
                    num = p[0];
                    den += p[0];
                } else if (p.length > 1) {
                    den += p[0];
                }
            }
            lik += Math.log(num / den);
        }

        return lik;

    }

}
