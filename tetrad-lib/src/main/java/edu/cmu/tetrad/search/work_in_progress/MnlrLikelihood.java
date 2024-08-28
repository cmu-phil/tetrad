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

package edu.cmu.tetrad.search.work_in_progress;

import de.bwaldvogel.liblinear.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.AdTree;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Calculates Mixed Variables Polynomial likelihood.
 *
 * <p>As for all scores in Tetrad, higher scores mean more dependence, and negative
 * scores indicate independence.</p>
 *
 * @author Bryan Andrews
 * @version $Id: $Id
 */
public class MnlrLikelihood {

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
    private AdTree adTree;

    // Fix degree
    private final int fDegree;

    // Structure Prior
    private final double structurePrior;

    private final transient PrintStream original = System.out;

    private final transient PrintStream nullout = new PrintStream(new OutputStream() {
        public void write(int b) {
            //DO NOTHING
        }
    });

    /**
     * Constructor.
     *
     * @param dataSet        The dataset to analyze.
     * @param structurePrior The structure prior.
     * @param fDegree        The f degree.
     */
    public MnlrLikelihood(DataSet dataSet, double structurePrior, int fDegree) {

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
            Node v = dataSet.getVariable(j);
            if (v instanceof ContinuousVariable) {
                double[] col = new double[dataSet.getNumRows()];
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getDouble(i, j);
                }
                this.continuousData[j] = col;
            } else if (v instanceof DiscreteVariable) {
                int[] col = new int[dataSet.getNumRows()];
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    col[i] = dataSet.getInt(i, j);
                }
                this.discreteData[j] = col;
            }
        }

        this.nodesHash = new HashMap<>();
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node v = dataSet.getVariable(j);
            this.nodesHash.put(v, j);
        }

        this.adTree = new AdTree(dataSet);

    }

    /**
     * Returns the likelihood of a child given its parents.
     *
     * @param child_index The index the child.
     * @param parents     The indices of the parents.
     * @return The likelihood.
     */
    public double getLik(int child_index, int[] parents) {

        double lik = 0;
        Node c = this.variables.get(child_index);
        List<ContinuousVariable> continuous_parents = new ArrayList<>();
        List<DiscreteVariable> discrete_parents = new ArrayList<>();
        for (int p : parents) {
            Node parent = this.variables.get(p);
            if (parent instanceof ContinuousVariable) {
                continuous_parents.add((ContinuousVariable) parent);
            } else {
                discrete_parents.add((DiscreteVariable) parent);
            }
        }

        int p = continuous_parents.size();

        adTree = new AdTree(dataSet);

        this.adTree.buildTable(discrete_parents);

        int[] continuousCols = new int[p];
        for (int j = 0; j < p; j++) continuousCols[j] = this.nodesHash.get(continuous_parents.get(j));

        for (int k = 0; k < adTree.getNumCells(); k++) {
            List<Integer> cell = adTree.getCell(k);
            if (cell == null) cell = new ArrayList<>();
            int r = cell.size();

            if (r > 1) {
                double[] mean = new double[p];
                double[] var = new double[p];
                for (int i = 0; i < p; i++) {
                    for (Integer integer : cell) {
                        mean[i] += this.continuousData[continuousCols[i]][integer];
                        var[i] += FastMath.pow(this.continuousData[continuousCols[i]][integer], 2);
                    }
                    mean[i] /= r;
                    var[i] /= r;
                    var[i] -= FastMath.pow(mean[i], 2);
                    var[i] = FastMath.sqrt(var[i]);

                    if (Double.isNaN(var[i])) {
                        System.out.println(var[i]);
                    }
                }

                int degree = this.fDegree;
                if (this.fDegree < 1) {
                    degree = (int) FastMath.floor(FastMath.log(r));
                }
                Matrix subset = new Matrix(r, p * degree + 1);
                for (int i = 0; i < r; i++) {
                    subset.set(i, p * degree, 1);
                    for (int j = 0; j < p; j++) {
                        for (int d = 0; d < degree; d++) {
                            subset.set(i, p * d + j, FastMath.pow((this.continuousData[continuousCols[j]][cell.get(i)] - mean[j]) / var[j], d + 1));
                        }
                    }
                }

                if (c instanceof ContinuousVariable) {
                    Vector target = new Vector(r);
                    for (int i = 0; i < r; i++) {
                        target.set(i, this.continuousData[child_index][cell.get(i)]);
                    }
                    lik += multipleRegression(target, subset);
                } else {
                    Matrix target = new Matrix(r, ((DiscreteVariable) c).getNumCategories());
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

    /**
     * Returns the degrees of freedom of a child given its parents.
     *
     * @param child_index The index of the child.
     * @param parents     The indices of the parents.
     * @return The degrees of freedom.
     */
    public double getDoF(int child_index, int[] parents) {

        double dof = 0;
        Node c = this.variables.get(child_index);
        List<ContinuousVariable> continuous_parents = new ArrayList<>();
        List<DiscreteVariable> discrete_parents = new ArrayList<>();
        for (int p : parents) {
            Node parent = this.variables.get(p);
            if (parent instanceof ContinuousVariable) {
                continuous_parents.add((ContinuousVariable) parent);
            } else {
                discrete_parents.add((DiscreteVariable) parent);
            }
        }

        this.adTree.buildTable(discrete_parents);

        for (int k = 0; k < adTree.getNumCells(); k++) {
            List<Integer> cell = adTree.getCell(k);
            int r = cell.size();
            if (r > 0) {

                int degree = this.fDegree;
                if (this.fDegree < 1) {
                    degree = (int) FastMath.floor(FastMath.log(r));
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

    /**
     * Returns the structur prior for k parents.
     *
     * @param k a int
     * @return a double
     */
    public double getStructurePrior(int k) {

        if (this.structurePrior < 0) {
            return getEBICprior();
        }

        double n = this.dataSet.getNumColumns() - 1;
        double p = this.structurePrior / n;

        if (this.structurePrior == 0) {
            return 0;
        }
        return k * FastMath.log(p) + (n - k) * FastMath.log(1 - p);

    }

    /**
     * <p>getEBICprior.</p>
     *
     * @return a double
     */
    public double getEBICprior() {

        double n = this.dataSet.getNumColumns();
        double gamma = -this.structurePrior;
        return gamma * FastMath.log(n);

    }

    private double multipleRegression(Vector Y, Matrix X) {

        int n = X.getNumRows();
        Vector r;

        try {
            Matrix Xt = X.transpose();
            Matrix XtX = Xt.times(X);
            r = X.times(XtX.inverse().times(Xt.times(Y))).minus(Y);
        } catch (Exception e) {
            Vector ones = new Vector(n);
            for (int i = 0; i < n; i++) ones.set(i, 1);
            r = ones.scalarMult(ones.dotProduct(Y) / (double) n).minus(Y);
        }

        double sigma2 = r.dotProduct(r) / n;

        if (sigma2 <= 0) {
            Vector ones = new Vector(n);
            for (int i = 0; i < n; i++) ones.set(i, 1);
            r = ones.scalarMult(ones.dotProduct(Y) / (double) FastMath.max(n, 2)).minus(Y);
            sigma2 = r.dotProduct(r) / n;
        }

        double lik = -(n / 2.) * (FastMath.log(2 * FastMath.PI) + FastMath.log(sigma2) + 1);

        if (Double.isInfinite(lik) || Double.isNaN(lik)) {
            System.out.println(lik);
        }

        return lik;
    }

    private double MultinomialLogisticRegression(Matrix targets, Matrix subset) {

        Problem problem = new Problem();
        problem.l = targets.getNumRows(); // number of training examples
        problem.n = subset.getNumColumns(); // number of features
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
        Parameter parameter = new Parameter(solver, C, eps);
        ArrayList<Model> models = new ArrayList<>();
        double lik = 0;
        double num;
        double den;

        for (int i = 0; i < targets.getNumColumns(); i++) {
            System.setOut(this.nullout);
            problem.y = targets.getColumn(i).toArray(); // target values
            models.add(i, Linear.train(problem, parameter));
            System.setOut(this.original);
        }

        for (int j = 0; j < problem.l; j++) {
            num = 0;
            den = 0;
            for (int i = 0; i < targets.getNumColumns(); i++) {
                double[] p = new double[models.get(i).getNrClass()];
                Linear.predictProbability(models.get(i), problem.x[j], p);
                if (targets.get(j, i) == 1) {
                    num = p[0];
                    den += p[0];
                } else if (p.length > 1) {
                    den += p[0];
                }
            }
            lik += FastMath.log(num / den);
        }

        return lik;

    }

}
