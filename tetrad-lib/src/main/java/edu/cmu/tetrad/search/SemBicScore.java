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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.*;
import static org.apache.commons.math3.stat.StatUtils.percentile;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class SemBicScore implements Score {

    // The dataset.
    private DataSet dataSet;

    // The covariances.
    private ICovarianceMatrix covariances;

    // The variables of the covariance matrix.
    private List<Node> variables;

    // The sample size of the covariance matrix.
    private int sampleSize;

    // True if linear dependencies should return NaN for the score, and hence be
    // ignored by FGES
    private boolean ignoreLinearDependent = false;

    // The printstream output should be sent to.
    private PrintStream out = System.out;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    // Variables that caused computational problems and so are to be avoided.
    private Set<Integer> forbidden = new HashSet<>();

    // A  map from variable names to their indices.
    private Map<String, Integer> indexMap;

    // The penalty penaltyDiscount, 1 for standard BIC.
    private double penaltyDiscount = 1.0;

    // The structure prior, 0 for standard BIC.
    private double structurePrior = 0.0;

    // A number subtracted from score differences.
    private double threshold = 0.0;

    // True if forward search, false if backward search.
    private boolean forward = true;

    // The amount by which the score should be adjusted due to error about the true bump scores.
    private double bias = Double.NaN;

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemBicScore(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
        this.indexMap = indexMap(this.variables);

//        printBiases();
    }

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemBicScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;

        ICovarianceMatrix cov = dataSet instanceof ICovarianceMatrix ? (ICovarianceMatrix) dataSet
                : new CovarianceMatrix(dataSet);

        setCovariances(cov);

        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
        this.indexMap = indexMap(this.variables);

//        printBiases();

    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        Node _x = variables.get(x);
        Node _y = variables.get(y);
        List<Node> _z = getVariableList(z);
        double r = partialCorrelation(_x, _y, _z);
        double sp1 = getStructurePrior(z.length + 1);
        double sp2 = getStructurePrior(z.length);
        int n = covariances.getSampleSize();

        if (false) {
            return -n * Math.log(1.0 - r * r) - getPenaltyDiscount() * log(n) - getBias()
                    + signum(getStructurePrior()) * (sp1 - sp2);
        } else {
            return (localScore(x, append(z, y)) - localScore(x, z)) - getPenaltyDiscount() * log(n) - getBias()
                    + signum(getStructurePrior()) * (sp1 - sp2);
        }

    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScoreDiff(x, y, new int[0]);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {

        try {
            double s2 = getCovariances().getValue(i, i);
            final int p = parents.length;
            int k = p + 1;
            double n = getSampleSize();

            TetradMatrix covxx = getCovariances().getSelection(parents, parents);
            TetradVector covxy = (getCovariances().getSelection(parents, new int[]{i})).getColumn(0);
            TetradVector coefs = (covxx.inverse()).times(covxy);
            s2 -= coefs.dotProduct(covxy);

            if (s2 <= 0) {
                if (isVerbose()) {
                    out.println("Nonpositive residual varianceY: resVar / varianceY = " + (s2 / getCovariances().getValue(i, i)));
                }

                return Double.NaN;
            }

            return -n * log(s2) - getPenaltyDiscount() * k * log(n) + signum(getStructurePrior()) * getStructurePrior(parents.length);
        } catch (Exception e) {
            boolean removedOne = true;

            while (removedOne) {
                List<Integer> _parents = new ArrayList<>();
                for (int parent : parents) _parents.add(parent);
                _parents.removeAll(forbidden);
                parents = new int[_parents.size()];
                for (int y = 0; y < _parents.size(); y++) parents[y] = _parents.get(y);
                removedOne = printMinimalLinearlyDependentSet(parents, getCovariances());
            }

            return Double.NaN;
        }
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

    public ICovarianceMatrix getCovariances() {
        return covariances;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    public DataSet getDataSet() {
        return dataSet;
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

    public void setVariables(List<Node> variables) {
        covariances.setVariables(variables);
        this.variables = variables;
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
    public int getMaxDegree() {
        return (int) Math.ceil(log(sampleSize));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);

        int[] k = new int[z.size()];

        for (int t = 0; t < z.size(); t++) {
            k[t] = variables.indexOf(z.get(t));
        }

        double v = localScore(i, k);

        return Double.isNaN(v);
    }

    public double getStructurePrior() {
        return structurePrior;
    }

    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    private void setCovariances(ICovarianceMatrix covariances) {
        this.covariances = covariances;
    }


    private static double LOG2PI = log(2.0 * Math.PI);

    // One record.
    private double gaussianLikelihood(int k, TetradMatrix sigma) {
        return -0.5 * logdet(sigma) - 0.5 * k * (1 + LOG2PI);
    }

    private double logdet(TetradMatrix m) {
        if (m.columns() == 0) {
            return 1;
        }

        RealMatrix M = m.getRealMatrix();
        final double tol = 1e-9;
        RealMatrix LT = new org.apache.commons.math3.linear.CholeskyDecomposition(M, tol, tol).getLT();

        double sum = 0.0;

        for (int i = 0; i < LT.getRowDimension(); i++) {
            sum += FastMath.log(LT.getEntry(i, i));
        }

        return sum;
    }

    private double getStructurePrior(int parents) {
        if (abs(getStructurePrior()) <= 0) {
            return 0;
        } else {
            int c = covariances.getDimension();
            double p = abs(getStructurePrior()) / (double) c;
            return (parents * Math.log(p) + (c - parents) * Math.log(1.0 - p));
        }
    }

    private List<Node> getVariableList(int[] indices) {
        List<Node> variables = new ArrayList<>();
        for (int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }

    private double partialCorrelation(Node x, Node y, List<Node> z) throws SingularMatrixException {
        int[] indices = new int[z.size() + 2];
        indices[0] = indexMap.get(x.getName());
        indices[1] = indexMap.get(y.getName());
        for (int i = 0; i < z.size(); i++) indices[i + 2] = indexMap.get(z.get(i).getName());
        TetradMatrix submatrix = covariances.getSubmatrix(indices).getMatrix();
        return StatUtils.partialCorrelationPrecisionMatrix(submatrix);
    }

    private Map<String, Integer> indexMap(List<Node> variables) {
        Map<String, Integer> indexMap = new HashMap<>();

        for (int i = 0; variables.size() > i; i++) {
            indexMap.put(variables.get(i).getName(), i);
        }

        return indexMap;
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

    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    public double getBias() {
        if (getThreshold() < 0.0 || getThreshold() >= 1.0) {
            throw new IllegalArgumentException("Bias threshold needs to be in [0, 1).");
        }

        if (getThreshold() == 0.0) {
            bias = 0.0;
        } else if (Double.isNaN(bias)) {
            int n = covariances.getSampleSize();

            ChiSquaredDistribution ch = new ChiSquaredDistribution(n - 1);

            int numSamples = 5000;

            double[] e = new double[numSamples];

            for (int i = 0; i < numSamples; i++) {
//                e[i] = -n * log(ch.sample() / (n - 1)) + n * log(ch.sample() / (n - 1));
                e[i] = -n * log(ch.sample() / (ch.sample()));
            }


            double percentile = 100.0 * (getThreshold() / 2.0 + 0.5);
            percentile = percentile < 50.0 ? 50.0 : percentile;
            percentile = percentile > 100.0 ? 100.0 : percentile;

            this.bias = percentile(e, percentile);

            System.out.println("Bias = " + bias);
        }

        return bias;
    }

    public void printBiases() {
        NumberFormat nf = new DecimalFormat("0.00");

        for (double threshold = 0.0; threshold <= 1.00; threshold += 0.1) {
            int n = covariances.getSampleSize();

            ChiSquaredDistribution ch = new ChiSquaredDistribution(n - 1);

            int numSamples = 5000;

            double[] e = new double[numSamples];

            for (int i = 0; i < numSamples; i++) {
                e[i] = -n * log(ch.sample() / (n - 1)) + n * log(ch.sample() / (n - 1));
            }


            double percentile = 100.0 * (threshold / 2.0 + 0.5);
            percentile = percentile < 50.0 ? 50.0 : percentile;
            percentile = percentile > 100.0 ? 100.0 : percentile;

            double bias = percentile(e, percentile);

            System.out.println("Threshold = " + nf.format(threshold) + " Bias = " + nf.format(bias) + "; corresponding penalty discount = 1 + bias / ln n = " + (1.0 + bias / log(n)));

        }
    }


    public boolean isForward() {
        return forward;
    }

    public void setForward(boolean forward) {
        this.forward = forward;
    }
}



