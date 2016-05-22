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
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.Math.sqrt;

/**
 * Implements the continuous BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class MixedBicScore implements Score {

    // The covariance matrix.
    private ICovarianceMatrix covariances;

    // The standardizedData set. Variables should be binary or Gaussian.
//    private DataSet dataSet;

    // The variables of the standardizedData set.
    private List<Node> variables;

    // The sample size of the covariance matrix.
    private int sampleSize;

    // The penalty penaltyDiscount.
    private double penaltyDiscount = 2.0;

    // True if linear dependencies should return NaN for the score, and hence be
    // ignored by FGS
    private boolean ignoreLinearDependent = false;

    // The printstream output should be sent to.
    private PrintStream out = System.out;

    // True if verbose output should be sent to out.
    private boolean verbose = false;
    private Set<Integer> forbidden = new HashSet<>();
    private final double logn;
    private boolean[] isBoolean;
    private double[][] standardizedData;
    private int[][] intData;
    private double[][] doubleIntColumns;

    /**
     * Constructs the score using a covariance matrix.
     */
    public MixedBicScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        setDataSet(dataSet);
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        this.penaltyDiscount = 2;
        logn = Math.log(sampleSize);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        if (!isBoolean[i]) {
            return getBicLinear(i, parents);
        } else {
            return getBicLogistic(i, parents);
        }
    }

    private double getBicLogistic(int i, int[] parents) {
        if (!isBoolean[i]) throw new IllegalArgumentException();

        double[][] regressors = new double[parents.length][];
        for (int j = 0; j < parents.length; j++) {
            regressors[j] = standardizedData[parents[j]];
        }

        int[] target = intData[i];

        LogisticRegression logisticRegression = new LogisticRegression();
        logisticRegression.regress(target, regressors);
        double ll = logisticRegression.getLikelihood();

        int k = parents.length + 1 + 1;

        double score = 2.0 * ll - getPenaltyDiscount() * k * Math.log(sampleSize);

//        System.out.println("score = " + score);

        return score;
    }

    private double getBicLinear(int i, int[] parents) {
        double residualVariance = getCovariances().getValue(i, i);
        int n = getSampleSize();
        int p = parents.length;
        TetradMatrix covxx = getSelection1(getCovariances(), parents);

        try {
            TetradMatrix covxxInv = covxx.inverse();

            TetradVector covxy = getSelection2(getCovariances(), parents, i);
            TetradVector b = covxxInv.times(covxy);
            residualVariance -= covxy.dotProduct(b);

            if (residualVariance <= 0) {
                if (isVerbose()) {
                    out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / getCovariances().getValue(i, i)));
                }
                return Double.NaN;
            }

            double c = getPenaltyDiscount();
            double score = score(residualVariance, n, logn, p, c);

//            System.out.println("score = " + score);

            return score;
        } catch (Exception e) {
            boolean removedOne = true;

            while (removedOne) {
                List<Integer> _parents = new ArrayList<>();
                for (int y = 0; y < parents.length; y++) _parents.add(parents[y]);
                _parents.removeAll(forbidden);
                parents = new int[_parents.size()];
                for (int y = 0; y < _parents.size(); y++) parents[y] = _parents.get(y);
                removedOne = printMinimalLinearlyDependentSet(parents, getCovariances());
            }

            return Double.NaN;
        }
    }

    @Override
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

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

//    public DataSet getDataSet() {
//        return dataSet;
//    }

    public int getSampleSize() {
        return sampleSize;
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

    // Calculates the BIC score.
    private double score(double residualVariance, int n, double logn, int p, double c) {
        return -n * Math.log(residualVariance) - c * (p + 1) * logn;
    }

    private TetradMatrix getSelection1(ICovarianceMatrix cov, int[] rows) {
        return cov.getSelection(rows, rows);
    }

    private TetradVector getSelection2(ICovarianceMatrix cov, int[] rows, int k) {
        return cov.getSelection(rows, new int[]{k}).getColumn(0);
    }

    // Prints a smallest subset of parents that causes a singular matrix exception.
    private boolean printMinimalLinearlyDependentSet(int[] parents, ICovarianceMatrix cov) {
        List<Node> _parents = new ArrayList<>();
        for (int p : parents) _parents.add(variables.get(p));

        DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            int[] sel = new int[choice.length];
            List<Node> _sel = new ArrayList<>();
            for (int m = 0; m < choice.length; m++) {
                sel[m] = parents[m];
                _sel.add(variables.get(sel[m]));
            }

            TetradMatrix m = cov.getSelection(sel, sel);

            try {
                m.inverse();
            } catch (Exception e2) {
                forbidden.add(sel[0]);
                out.println("### Linear dependence among variables: " + _sel);
                out.println("### Removing " + _sel.get(0));
                return true;
            }
        }

        return false;
    }

    private void setDataSet(DataSet dataSet) {
        this.variables = dataSet.getVariables();
        isBoolean = new boolean[variables.size()];

        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i) instanceof DiscreteVariable) {
                isBoolean[i] = true;
            }
        }

        double[][] data = new double[dataSet.getNumColumns()][dataSet.getNumRows()];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                data[j][i] = dataSet.getDouble(i, j);
            }
        }

        for (int i = 0; i < data.length; i++) {
            standardize(data[i]);
        }

        this.standardizedData = data;

        this.intData = new int[dataSet.getNumColumns()][];
//        this.doubleIntColumns = new double[dataSet.getNumColumns()][];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            intData[j] = new int[dataSet.getNumRows()];
//            doubleIntColumns[j] = new double[dataSet.getNumRows()];
            if (variables.get(j) instanceof DiscreteVariable) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    intData[j][i] = dataSet.getInt(i, j);
//                    doubleIntColumns[j][i] = dataSet.getInt(i, j);
                }
            } else {
//                standardize(data[j]);
            }
        }

        TetradMatrix mTranspose = new TetradMatrix(standardizedData);
        TetradMatrix m = mTranspose.transpose();
        DataSet dataSet1 = ColtDataSet.makeContinuousData(variables, m);
        this.covariances = new CovarianceMatrix(dataSet1);
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
        return (int) Math.ceil(Math.log(sampleSize));
    }

    public ICovarianceMatrix getCovariances() {
        return covariances;
    }

    private void standardize(double[] data) {
        double sum = 0.0;

        for (double d : data) {
            sum += d;
        }

        double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data[i] = data[i] - mean;
        }

        double var = 0.0;

        for (double d : data) {
            var += d * d;
        }

        var /= (data.length);
        double sd = sqrt(var);

        for (int i = 0; i < data.length; i++) {
            data[i] /= sd;
        }
    }
}



