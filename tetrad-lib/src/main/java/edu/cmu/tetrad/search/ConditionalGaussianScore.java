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

import java.util.*;

/**
 * Implements a conditional Gaussian BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianScore implements Score {

    // The covariance matrix.
    private DataSet dataSet;

    private TetradMatrix _data;

    // The variables of the continuousData set.
    private List<Node> variables;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    /**
     * Constructs the score using a covariance matrix.
     */
    public ConditionalGaussianScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();

        _data = dataSet.getDoubleData();
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        Node target = variables.get(i);

        // With fgs2 this fails for some reason...need to debug.
        int b = Arrays.binarySearch(parents, i);
        if (b >= 0) {
            throw new IllegalArgumentException();
        }

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

        if (target instanceof DiscreteVariable) {
            if (denominatorDiscrete.contains(target)) {
                throw new IllegalArgumentException();
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

        double C = .5 * N * (1 + Math.log(2 * Math.PI));

        if (numeratorContinuous.isEmpty()) {

            // Discrete target, discrete predictors.
            if (!(target instanceof DiscreteVariable)) throw new IllegalStateException();
            Ret ret = getProb((DiscreteVariable) target, denominatorDiscrete);
            double lik = ret.getLik();
            double dof = ret.getDof();

//            lik += C;

            return lik - dof * Math.log(N);
        } else {
            if (denominatorContinuous.isEmpty()) {

                // Continuous target, all discrete predictors.
                Ret ret1 = getProb(numeratorContinuous, numeratorDiscrete);
                double dof = ret1.getDof();
                double lik = ret1.getLik();

                lik -= C;

                return lik - dof * Math.log(N);
            } else if (numeratorContinuous.size() == denominatorContinuous.size()) {

                // Discrete target, mixed predictors.
                Ret ret1 = getProb(numeratorContinuous, numeratorDiscrete);
                Ret ret2 = getProb(denominatorContinuous, denominatorDiscrete);
                double dof = ret1.getDof() - ret2.getDof();
                double lik = ret1.getLik() - ret2.getLik();

                lik -= C;  // Weird.

                return lik - dof * Math.log(N);
            } else if (numeratorDiscrete.size() == denominatorDiscrete.size()) {

                // Continuous target, mixed predictors.
                Ret ret1 = getProb(numeratorContinuous, numeratorDiscrete);
                Ret ret2 = getProb(denominatorContinuous, denominatorDiscrete);
                double dof = ret1.getDof() - ret2.getDof();
                double lik = ret1.getLik() - ret2.getLik();

                lik -= C;

                return lik - dof * Math.log(N);
            } else {
                throw new IllegalStateException();
            }
        }
    }

    private Ret getProb(List<ContinuousVariable> continuous, List<DiscreteVariable> discrete) {
        if (continuous.isEmpty()) throw new IllegalArgumentException();
        int dof = 0;

        // For each combination of values for the discrete guys extract a subset of the data.
        List<Node> variables = dataSet.getVariables();

        if (continuous.isEmpty()) {
            throw new IllegalArgumentException();
        }

        int p = continuous.size();
        int d = discrete.size();

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

        double lik = 0;

        while (iterator.hasNext()) {
            comb = iterator.next();
            List<Integer> rows = new ArrayList<>();

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                boolean addRow = true;

                for (int c = 0; c < comb.length; c++) {
                    if (comb[c] != dataSet.getInt(i, _cols[c])) {
                        addRow = false;
                        break;
                    }
                }

                if (addRow) {
                    rows.add(i);
                }
            }

            if (rows.isEmpty()) continue;

            int[] _rows = new int[rows.size()];
            for (int k = 0; k < rows.size(); k++) _rows[k] = rows.get(k);

            TetradMatrix subset = _data.getSelection(_rows, cols);
            int n = rows.size();

            if (n > p) {
                TetradMatrix Sigma = new TetradMatrix(new Covariance(subset.getRealMatrix(),
                        false).getCovarianceMatrix());
//                lik -= 0.5 * n * p * Math.log(2 * Math.PI);
//                lik -= 0.5 * n * p;
                lik -= 0.5 * n * Math.log(Sigma.det());
            } else {
                System.out.println("Skipped " + n);
            }

//            lik -= 2 * n * p;

            dof += p * (p + 1);
        }

        return new Ret(lik, dof);
    }

    private Ret getProb(DiscreteVariable target, List<DiscreteVariable> parents) {
        if (parents.contains(target)) throw new IllegalArgumentException();

        int dof = 0;
        int numCategories = target.getNumCategories();

        int d = parents.size();
        int[] dims = new int[d];

        for (int i = 0; i < d; i++) {
            dims[i] = parents.get(i).getNumCategories();
        }

        CombinationIterator iterator = new CombinationIterator(dims);
        int[] comb;

        int[] _cols = new int[d];
        for (int i = 0; i < d; i++) _cols[i] = dataSet.getColumn(parents.get(i));

        double lik = 0;

        while (iterator.hasNext()) {
            comb = iterator.next();
            List<Integer> rows = new ArrayList<>();

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                boolean addRow = true;

                for (int c = 0; c < comb.length; c++) {
                    if (comb[c] != dataSet.getInt(i, _cols[c])) {
                        addRow = false;
                        break;
                    }
                }

                if (addRow) {
                    rows.add(i);
                }
            }

            double[] counts = new double[numCategories];
            double cellPrior = 0;
            Arrays.fill(counts, cellPrior);
            double r = cellPrior * counts.length;

            for (int row : rows) {
                int value = dataSet.getInt(row, dataSet.getColumn(target));
                counts[value]++;
                r++;
            }

            for (int c = 0; c < numCategories; c++) {
                double count = counts[c];

                if (count > 0) {
                    lik += count * Math.log(count / (double) r);
                }
            }

            dof += numCategories - 1;
        }

        return new Ret(lik, dof);
    }

    private class Ret {
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
        return bump > 0;//-0.25 * getPenaltyDiscount() * Math.log(sampleSize);
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public boolean isDiscrete() {
        return false;
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
}



