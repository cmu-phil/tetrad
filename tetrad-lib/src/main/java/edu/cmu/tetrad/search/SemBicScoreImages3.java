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
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import org.apache.commons.math3.stat.correlation.Covariance;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.PI;
import static java.lang.Math.log;

/**
 * Implements the continuous BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class SemBicScoreImages3 implements ISemBicScore, Score {

    // The variables of the covariance matrix.
    private List<Node> variables;

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

    List<TetradMatrix> dataSets = new ArrayList<>();

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemBicScoreImages3(List<DataSet> dataSets) {
        for (DataModel model : dataSets) {
            if (model instanceof DataSet) {
                DataSet dataSet = (DataSet) model;
                this.dataSets.add(dataSet.getDoubleData());

                if (!dataSet.isContinuous()) {
                    throw new IllegalArgumentException("Datasets must be continuous.");
                }
            }
        }

        this.sampleSize = dataSets.get(0).getNumRows();
        this.variables = dataSets.get(0).getVariables();
    }

    private TetradMatrix cov(TetradMatrix x) {
        return new TetradMatrix(new Covariance(x.getRealMatrix(), true).getCovarianceMatrix());
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScore(y, x) - localScore(y);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int... parents) {
        int A = dataSets.size();
        int a = sampleSize;
        int p = parents.length;
        int N = a * A;

        int[] rows = new int[dataSets.get(0).rows()];
        for (int k = 0; k < dataSets.get(0).rows(); k++) rows[k] = k;

        double lik = 0.0;

        for (TetradMatrix dataSet : dataSets) {
            int[] all = append(parents, i);
            final double lik1 = gaussianLikelihood2(dataSet.getSelection(rows, all));
            final double lik2 = gaussianLikelihood2(dataSet.getSelection(rows, parents));
            lik += lik1 - lik2;
        }

        int dof = A * (h(p + 1) - h(p));

        return 2.0 * (lik + 0 * getPriorForStructure(p)) - dof * log(N);
    }

    private int h(int p) {
        return p * (p + 1) / 2;
    }

    private double getPriorForStructure(int numParents) {
        double e = 1;
        int vm = variables.size() - 1;
        return numParents * log(e / (vm)) + (vm - numParents) * log(1.0 - (e / (vm)));
    }

    private double gaussianLikelihood(TetradMatrix sigma) {
        int k = sigma.columns();
        return -0.5 * log(sigma.det()) - k - 0.5 * k * log(2.0 * PI);
    }

    private double gaussianLikelihood2(TetradMatrix x) {
        if (x.rows() == 0 || x.columns() == 0) return 0.0;
        TetradMatrix sigma = cov(x);
        TetradMatrix sigmaInv = sigma.inverse();
        double sigmaDet = sigma.det();
        return gaussianLikelihood2(x, sigmaInv, sigmaDet);
    }

    private double gaussianLikelihood2(TetradMatrix x, TetradMatrix sigmaInv, double sigmaDet) {
        TetradMatrix xm = DataUtils.centerData(x);
        int a = x.rows();
        double g = xm.times(sigmaInv).times(sigmaInv).times(xm.transpose()).get(0, 0);
        int k = x.columns();
        return -0.5 * a * log(sigmaDet) - 0.5 * g - 0.5 * a * k * log(2.0 * PI);
    }

    // For cases like P(C | X). This is a ratio of joints, but if the numerator is conditional Gaussian,
    // the denominator is a mixture of Gaussians.
    private double likelihoodMixed(TetradMatrix subsample) {
        final int k = subsample.columns();
        final double g = Math.pow(2.0 * Math.PI, k);

        double lnL = 0.0;
        TetradMatrix sigma = cov(subsample);
        TetradMatrix sigmaInv = sigma.inverse();
        double sigmaDet = sigma.det();

        for (int i = 0; i < subsample.rows(); i++) {
            lnL += gaussianLikelihood2(subsample, sigmaInv, sigmaDet);
        }

        return lnL;
    }

    private double prob(Double factor, TetradMatrix inv, TetradVector x) {
        return factor * Math.exp(-0.5 * inv.times(x).dotProduct(x));
    }

    // Calculates the means of the columns of x.
    private TetradVector means(TetradMatrix x) {
        return x.sum(1).scalarMult(1.0 / x.rows());
    }


    int[] append(int[] parents, int extra) {
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

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > -0.25 * getPenaltyDiscount() * log(sampleSize);
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
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
    public double getParameter1() {
        return penaltyDiscount;
    }

    @Override
    public void setParameter1(double value) {
        this.penaltyDiscount = value;
    }

    @Override
    public int getSampleSize() {
        return sampleSize;
    }

    // Calculates the BIC score.
    private double score(double residualVariance, int n, int p, double c) {
        return -n * log(residualVariance) - c * (p + 1) * log(n);
    }

    private TetradMatrix getSelection1(ICovarianceMatrix cov, int[] rows) {
        return cov.getSelection(rows, rows);
    }

    private TetradVector getSelection2(ICovarianceMatrix cov, int[] rows, int k) {
        return cov.getSelection(rows, new int[]{k}).getColumn(0);
    }

    // Prints a smallest subset of parents that causes a singular matrix exception.
    private void printMinimalLinearlyDependentSet(int[] parents, ICovarianceMatrix cov) {
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
                out.println("### Linear dependence among variables: " + _sel);
            }
        }
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
        return 1000;
    }
}



