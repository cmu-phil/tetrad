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
import org.apache.commons.collections4.map.HashedMap;
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

        Ret ret1 = getJointLikelihood(XPlus, APlus);
        Ret ret2 = getJointLikelihood(X, A);

        double lik = ret1.getLik() - ret2.getLik();
        int dof = ret1.getDof() - ret2.getDof();
//        int dof = getDof2(i, parents);

        return new Ret(lik, dof);
    }

    // The likelihood of the joint over all of these variables, continuous and discrete.
    private Ret getJointLikelihood(List<ContinuousVariable> X, List<DiscreteVariable> A) {
        int p = X.size();

//        List<List<Integer>> cells = getCellsOriginal(A);
        List<List<Integer>> cells = getCellsADTreeStyle(A);

        int[] continuousCols = new int[p];
        for (int j = 0; j < p; j++) continuousCols[j] = nodesHash.get(X.get(j));
        int N = dataSet.getNumRows();
        double lik = 0;

        for (List<Integer> cell : cells) {
            int r = cell.size();

            if (A.size() > 0) {
                if (r > 0) {
                    double prob = r / (double) N;
                    lik += r * Math.log(prob);
                }
            }

            if (X.size() > 0) {
                if (r > 3 * p) {
                    TetradMatrix subset = new TetradMatrix(r, p);

                    for (int i = 0; i < r; i++) {
                        for (int j = 0; j < p; j++) {
                            subset.set(i, j, continuousData[continuousCols[j]][cell.get(i)]);
                        }
                    }

                    TetradMatrix Sigma = new TetradMatrix(new Covariance(subset.getRealMatrix(),
                            false).getCovarianceMatrix());
                    double det = Sigma.det();
                    lik -= 0.5 * r * Math.log(det);
                }

                lik -= 0.5 * r * p * (1.0 + Math.log(2.0 * Math.PI));
            }
        }

        int dof;

        if (!A.isEmpty() && !X.isEmpty()) {
            dof = f(A) * h(X) + j(A);
        } else if (!A.isEmpty()) {
            dof = j(A);
        } else if (!X.isEmpty()) {
            dof = h(X);
        } else {
            dof = 0;
        }

        return new Ret(lik, dof);
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

    List<Vary> baseCase;

    private List<List<Integer>> getCellsADTreeStyle(List<DiscreteVariable> A) {
        int d = A.size();

//        // For each combination of values for the A guys extract a subset of the data.
//        int[] discreteCols = new int[d];
//        int[] dims = new int[d];
//        int n = dataSet.getNumRows();

        if (baseCase == null) {
            Vary vary = new Vary();
            this.baseCase = new ArrayList<>();
            baseCase.add(vary);
        }

        List<Vary> varies = baseCase;

        for (DiscreteVariable v : A) {
            varies = getVaries(varies, nodesHash.get(v));
        }

        List<List<Integer>> rows = new ArrayList<>();

        for (Vary vary : varies) {
            rows.addAll(vary.getRows());
        }

        return rows;
    }

    private List<Vary> getVaries(List<Vary> varies, int v) {
        List<Vary> _varies = new ArrayList<>();

        for (Vary vary : varies) {
            for (int i = 0; i < vary.getNumCategories(); i++) {
                _varies.add(vary.getSubvary(v, i));
            }
        }

        return _varies;
    }

    private class Vary {
        int col;
        int numCategories;
        List<List<Integer>> rows = new ArrayList<>();
        List<Map<Integer, Vary>> subVaries = new ArrayList<>();

        // Base case.
        public Vary() {
            List<Integer> _rows = new ArrayList<>();
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                _rows.add(i);
            }

            subVaries.add(new HashMap<Integer, Vary>());

            for (Node node : dataSet.getVariables()) {
                if (node instanceof DiscreteVariable) {
                    DiscreteVariable d = (DiscreteVariable) node;
                    int col = nodesHash.get(d);
                    subVaries.get(0).put(col, new Vary(col, d.getNumCategories(), _rows, discreteData));
                }
            }

            numCategories = 1;
            rows.add(_rows);
            subVaries = new ArrayList<>();
            subVaries.add(new HashedMap<Integer, Vary>());
        }

        public Vary(int col, int numCategories, List<Integer> supRows, int[][] discreteData) {
            this.col = col;
            this.numCategories = numCategories;

            for (int i = 0; i < numCategories; i++) {
                rows.add(new ArrayList<Integer>());
            }

            for (int i = 0; i < numCategories; i++) {
                subVaries.add(new HashedMap<Integer, Vary>());
            }

            for (int i : supRows) {
                int index = discreteData[col][i];
                rows.get(index).add(i);
            }
        }

        public List<List<Integer>> getRows() {
            return rows;
        }

        public Vary getSubvary(int w, int cat) {
            Vary vary = subVaries.get(cat).get(w);

            if (vary == null) {
                DiscreteVariable v = (DiscreteVariable) dataSet.getVariable(w);
                vary = new Vary(w, v.getNumCategories(), rows.get(cat), discreteData);
                subVaries.get(cat).put(w, vary);
            }

            return vary;
        }

        public int getNumCategories() {
            return numCategories;
        }
    }

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



