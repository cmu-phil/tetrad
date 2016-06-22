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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.CombinationIterator;
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.junit.Test;

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
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        Node target = variables.get(i);

        List<ContinuousVariable> denominatorContinuous = new ArrayList<>();
        List<DiscreteVariable> denominatorDiscrete = new ArrayList<>();

        for (int parent1 : parents) {
            Node parent = variables.get(parent1);

            if (parent instanceof ContinuousVariable) {
                denominatorContinuous.add((ContinuousVariable) parent);
            } else {
                denominatorDiscrete.add((DiscreteVariable) parent);
            }
        }

        List<ContinuousVariable> numeratorContinuous = new ArrayList<>(denominatorContinuous);
        List<DiscreteVariable> numeratorDiscrete = new ArrayList<>(denominatorDiscrete);

        if (target instanceof ContinuousVariable) {
            numeratorContinuous.add((ContinuousVariable) target);
        } else if (target instanceof DiscreteVariable) {
            numeratorDiscrete.add((DiscreteVariable) target);
        } else {
            throw new IllegalStateException();
        }

        int N = dataSet.getNumRows();

        double lik;
        double dof;

        if (numeratorContinuous.isEmpty()) {

            // Discrete target, discrete predictors.
            if (!(target instanceof DiscreteVariable)) throw new IllegalStateException();
            Ret ret = getProb((DiscreteVariable) target, denominatorDiscrete);
            lik = ret.getLik();
            dof = ret.getDof();
        } else if (denominatorContinuous.isEmpty()) {

            // Continuous target, all discrete predictors.
            Ret ret1 = getProb(numeratorContinuous, numeratorDiscrete);
            dof = ret1.getDof();
            lik = ret1.getLik();
        } else if (numeratorContinuous.size() == denominatorContinuous.size()) {

            // Discrete target, mixed predictors.
            Ret ret1 = getProb(numeratorContinuous, numeratorDiscrete);
            Ret ret2 = getProb(numeratorContinuous, denominatorDiscrete);
            dof = ret1.getDof() - ret2.getDof();
            lik = ret1.getLik() - ret2.getLik();
            lik -= 0.5 * N * (1.0 + Math.log(2.0 * Math.PI));
        } else {

            // Continuous target, mixed predictors.
            Ret ret1 = getProb(numeratorContinuous, numeratorDiscrete);
            Ret ret2 = getProb(denominatorContinuous, numeratorDiscrete);
            dof = ret1.getDof() - ret2.getDof();
            lik = ret1.getLik() - ret2.getLik();
        }

        return lik - dof * Math.log(N);
    }

    private Ret getProb(DiscreteVariable target, List<DiscreteVariable> parents) {
        if (parents.contains(target)) throw new IllegalArgumentException();
        int p = target.getNumCategories();

        int d = parents.size();
        int[] dims = new int[d];
        int targetCol = dataSet.getColumn(target);

        for (int i = 0; i < d; i++) {
            dims[i] = parents.get(i).getNumCategories();
        }

        CombinationIterator iterator = new CombinationIterator(dims);
        int[] comb;

        int[] _cols = new int[d];
        for (int i = 0; i < d; i++) _cols[i] = dataSet.getColumn(parents.get(i));

        double lik = 0;
        int t = p - 1;
        int s = 0;
        int N = dataSet.getNumRows();

        while (iterator.hasNext()) {
            comb = iterator.next();
            List<Integer> rows = new ArrayList<>();

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                boolean addRow = true;

                for (int c = 0; c < comb.length; c++) {
                    if (comb[c] != discreteData[_cols[c]][i]) {
                        addRow = false;
                        break;
                    }
                }

                if (addRow) {
                    rows.add(i);
                }
            }

            double[] counts = new double[p];
            int r = 0;

            for (int row : rows) {
                int value = discreteData[targetCol][row];
                counts[value]++;
                r++;
            }

            for (int c = 0; c < p; c++) {
                double count = counts[c];

                if (count > 0) {
                    lik += count * Math.log(count / (double) r) + Math.log(r / (double) N);
                }
            }

            s++;
        }

        int dof = s * t + s - 1;

        return new Ret(lik, dof);
    }

    private Ret getProb(List<ContinuousVariable> continuous, List<DiscreteVariable> discrete) {
        if (continuous.isEmpty()) throw new IllegalArgumentException();
        int p = continuous.size();
        int d = discrete.size();

        int N = dataSet.getNumRows();

        // For each combination of values for the discrete guys extract a subset of the data.
        List<Node> variables = dataSet.getVariables();

        int[] cols = new int[p];
        int[] dims = new int[d];

        for (int i = 0; i < d; i++) {
            dims[i] = discrete.get(i).getNumCategories();
        }

        for (int j = 0; j < p; j++) {
            cols[j] = variables.indexOf(continuous.get(j));
        }

        CombinationIterator iterator = new CombinationIterator(dims);

        int[] _cols = new int[d];
        for (int i = 0; i < d; i++) _cols[i] = dataSet.getColumn(discrete.get(i));

        List<int[]> combs = new ArrayList<>();

        while (iterator.hasNext()) {
            combs.add(iterator.next());
        }

        List<List<Integer>> rows = new ArrayList<>();

        for (int i = 0; i < combs.size(); i++) {
            rows.add(new ArrayList<Integer>());
        }

        int[] values = new int[dims.length];

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dims.length; j++) {
                values[j] = discreteData[_cols[j]][i];
            }

            int rowIndex = getRowIndex(values, dims);

            rows.get(rowIndex).add(i);
        }

        double lik = 0;

        for (int k = 0; k < rows.size(); k++) {
            if (rows.get(k).isEmpty()) continue;

            TetradMatrix subset = new TetradMatrix(rows.get(k).size(), cols.length);

            for (int i = 0; i < rows.get(k).size(); i++) {
                for (int j = 0; j < cols.length; j++) {
                    subset.set(i, j, continuousData[cols[j]][rows.get(k).get(i)]);
                }
            }

            int n = rows.get(k).size();

            if (n > p) {
                TetradMatrix Sigma = new TetradMatrix(new Covariance(subset.getRealMatrix(),
                        true).getCovarianceMatrix());
                lik -= 0.5 * n * Math.log(Sigma.det());
                lik += Math.log(n / (double) N);
            } else {
                lik -= 0.5 * n * Math.log(p + 2); // guestimate--not enough data.
                lik += Math.log(n / (double) N);
            }
        }

        int s = rows.size();
        int t = p * (p + 1) / 2;

        double dof = s * t + s - 1;

        return new Ret(lik, dof);
    }

    private Ret getProb2(List<ContinuousVariable> continuous, List<DiscreteVariable> discrete) {
        if (continuous.isEmpty()) throw new IllegalArgumentException();
        int p = continuous.size();
        int d = discrete.size();

        int t = p * (p + 1) / 2;
        int s = 0;

        int N = dataSet.getNumRows();

        // For each combination of values for the discrete guys extract a subset of the data.
        List<Node> variables = dataSet.getVariables();

        int[] cols = new int[p];
        int[] dims = new int[d];

        for (int i = 0; i < d; i++) {
            dims[i] = discrete.get(i).getNumCategories();
        }

        for (int j = 0; j < p; j++) {
            cols[j] = variables.indexOf(continuous.get(j));
        }

        CombinationIterator iterator = new CombinationIterator(dims);
        int[] comb;

        int[] _cols = new int[d];
        for (int i = 0; i < d; i++) _cols[i] = dataSet.getColumn(discrete.get(i));
        List<Double> logs = new ArrayList<>();

        while (iterator.hasNext()) {
            comb = iterator.next();
            List<Integer> rows = new ArrayList<>();

            ROWS:
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                for (int c = 0; c < comb.length; c++) {
                    if (comb[c] != discreteData[_cols[c]][i]) {
                        continue ROWS;
                    }
                }

                rows.add(i);
            }

            if (rows.isEmpty()) continue;

            TetradMatrix subset = new TetradMatrix(rows.size(), cols.length);

            for (int i = 0; i < rows.size(); i++) {
                for (int j = 0; j < cols.length; j++) {
                    subset.set(i, j, continuousData[cols[j]][rows.get(i)]);
                }
            }

            int n = rows.size();

            if (n > p) {
                TetradMatrix Sigma = new TetradMatrix(new Covariance(subset.getRealMatrix(),
                        true).getCovarianceMatrix());
                double l = -0.5 * n * Math.log(Sigma.det()) - 0.5 * p * n * (1.0 + Math.log(2 * Math.PI));
                l += Math.log(n / (double) N);
                logs.add(l);
            } else {
                double l = -0.5 * n * Math.log(1.4) - 0.5 * p * n * (1.0 - Math.log(2 * Math.PI));
                l += Math.log(n / (double) N);
                logs.add(l);
            }

            s++;
        }

        double lik = logOfSum(logs);

        double dof = s * t + s - 1;

        return new Ret(lik, dof);
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

    // Calculates the log of a list of terms, where the argument consists of the logs of the terms.
    private double logOfSum(List<Double> logs) {

        Collections.sort(logs, new Comparator<Double>() {
            @Override
            public int compare(Double o1, Double o2) {
                return -Double.compare(o1, o2);
            }
        });

        double sum = 0.0;
        int N = logs.size() - 1;
        double loga0 = logs.get(0);

        for (int i = 1; i <= N; i++) {
            sum += Math.exp(logs.get(i) - loga0);
        }

        sum += 1;

        return loga0 + Math.log(sum);
    }

    @Test
    public void test() {
        double a = .9;
        double b = .9;

        double loga = Math.log(a);
        double logb = Math.log(b);

        List<Double> logs = new ArrayList<>();
        logs.add(loga);
        logs.add(logb);

        double sum = logOfSum(logs);

        System.out.println(sum);
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



