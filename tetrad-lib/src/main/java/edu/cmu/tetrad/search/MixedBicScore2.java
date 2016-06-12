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
import edu.cmu.tetrad.regression.LogisticRegression2;
import edu.cmu.tetrad.util.CombinationIterator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import edu.cmu.tetrad.util.dist.Discrete;

import java.io.PrintStream;
import java.util.*;

import static java.lang.Math.sqrt;

/**
 * Implements the continuous BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class MixedBicScore2 implements Score {

    // The covariance matrix.
    private DataSet dataSet;

    // The variables of the continuousData set.
    private List<Node> variables;

    private double penaltyDiscount = 2.0;

    // True if linear dependencies should return NaN for the score, and hence be
    // ignored by FGS
    private boolean ignoreLinearDependent = false;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    private BicScore bicScore;

    /**
     * Constructs the score using a covariance matrix.
     */
    public MixedBicScore2(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.penaltyDiscount = 4;
        bicScore = new BicScore(dataSet);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        Node target = variables.get(i);

        List<ContinuousVariable> numeratorContinuous = new ArrayList<>();
        List<DiscreteVariable> numeratorDiscrete = new ArrayList<>();

        for (int parent1 : parents) {
            Node parent = variables.get(parent1);

            if (parent instanceof ContinuousVariable) {
                numeratorContinuous.add((ContinuousVariable) parent);
            } else {
                numeratorDiscrete.add((DiscreteVariable) parent);
            }
        }

        List<ContinuousVariable> denominatorContinuous = new ArrayList<>(numeratorContinuous);
        List<DiscreteVariable> denominatorDiscrete = new ArrayList<>(numeratorDiscrete);

        if (target instanceof ContinuousVariable) {
            numeratorContinuous.add((ContinuousVariable) target);
        } else if (target instanceof DiscreteVariable) {
            numeratorDiscrete.add((DiscreteVariable) target);
        } else {
            throw new IllegalStateException();
        }

        double score;

        if (numeratorContinuous.isEmpty()) {
            score = bicScore.localScore(i, parents);
        } else {
            double numerator = getProb(numeratorContinuous, numeratorDiscrete);
            double denominator = getProb(denominatorContinuous, denominatorDiscrete);

            int c = numeratorContinuous.size();
            int d = numeratorDiscrete.size();
            int N = dataSet.getNumRows();
            double dof = getDof(numeratorContinuous, numeratorDiscrete);
            double lik = numerator - denominator;

            score = 2 * lik - dof * Math.log(N);
        }

        return score;
    }

    private double getProb(List<ContinuousVariable> continuous, List<DiscreteVariable> discrete) {
        if (continuous.isEmpty()) return 0;

        // For each combination of values for the discrete guys extract a subset of the data.
        List<Node> variables = dataSet.getVariables();

        if (continuous.isEmpty()) {
            throw new IllegalArgumentException();
        }

        int[] dims = new int[discrete.size()];
        int[] cols = new int[continuous.size()];

        for (int i = 0; i < discrete.size(); i++) {
            dims[i] = discrete.get(i).getNumCategories();
        }

        for (int j = 0; j < continuous.size(); j++) {
            cols[j] = variables.indexOf(continuous.get(j));
        }

        if (cols.length == 0) {
            throw new IllegalArgumentException();
        }

        CombinationIterator iterator = new CombinationIterator(dims);
        int[] comb;

        TetradMatrix _data = dataSet.getDoubleData();

        double SUM = 0;

        int[] _cols = new int[discrete.size()];
        for (int i = 0; i < discrete.size(); i++) _cols[i] = dataSet.getColumn(discrete.get(i));

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
            int n = subset.rows();
            int p = subset.columns();

            TetradMatrix Sigma = DataUtils.cov2(subset);

            SUM += -.5 * n * Math.log(Sigma.det());
            SUM += -.5 * n * Sigma.times(Sigma.inverse()).trace();
            SUM += -.5 * n * p * Math.log(2 * Math.PI);
        }

        return SUM;
    }

    private double getDof(List<ContinuousVariable> c, List<DiscreteVariable> d) {
        int[] dims = new int[d.size()];

        for (int i = 0; i < d.size(); i++) {
            dims[i] = d.get(i).getNumCategories();
        }

        CombinationIterator iterator = new CombinationIterator(dims);

        int dof = 0;
        int p = c.size();

        while (iterator.hasNext()) {
            iterator.next();
            dof += p * (p + 1) / 2;
        }

        return dof;
    }

    private TetradVector getAvg(TetradMatrix X) {
        TetradVector avg = new TetradVector(X.columns());

        for (int j = 0; j < X.columns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < X.rows(); i++) {
                sum += X.get(i, j);
            }

            double _avg = sum / X.rows();

            avg.set(j, _avg);
        }

        return avg;
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

    /**
     * True iff edges that cause linear dependence are ignored.
     */
    public boolean isIgnoreLinearDependent() {
        return ignoreLinearDependent;
    }

    public void setIgnoreLinearDependent(boolean ignoreLinearDependent) {
        this.ignoreLinearDependent = ignoreLinearDependent;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;//-0.25 * getPenaltyDiscount() * Math.log(sampleSize);
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
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
        return penaltyDiscount;
    }

    @Override
    public void setParameter1(double alpha) {
        this.penaltyDiscount = alpha;
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



