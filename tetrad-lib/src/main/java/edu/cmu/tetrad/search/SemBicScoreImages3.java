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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.util.FastMath;

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

    // Sample sizes of each data set.
    private int[] sampleSizes;

    // Total sample size.
    private final int N;

    // The penalty penaltyDiscount.
    private double penaltyDiscount = 1.0;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    // Covariances for each of the input data sets.
    private List<TetradMatrix> covs = new ArrayList<>();

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemBicScoreImages3(List<DataSet> dataSets) {
        sampleSizes = new int[dataSets.size()];
        int N = 0;

        for (int i = 0; i < dataSets.size(); i++) {
            DataSet dataSet = dataSets.get(i);
            covs.add(cov(dataSet));
            sampleSizes[i] = dataSet.getNumRows();
            N += sampleSizes[i];
        }

        this.variables = dataSets.get(0).getVariables();
        this.N = N;
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
        return score1(i, parents);
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

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > -0.25 * getPenaltyDiscount() * log(N);
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
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

    public boolean getAlternativePenalty() {
        return false;
    }

    public void setAlternativePenalty(double alpha) {
        //
    }

    @Override
    public void setPenaltyDiscount(double value) {
        this.penaltyDiscount = value;
    }

    @Override
    public int getSampleSize() {
        return N;
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

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    private double score1(int i, int[] parents) {
        int p = parents.length;

        double lik = 0.0;
        int dof = 0;

        for (int k = 0; k < covs.size(); k++) {
            TetradMatrix cov = covs.get(k);
            int[] parentsPlus = append(parents, i);
            final int a = sampleSizes[k];
            final double lik1 = gaussianLikelihood(cov.getSelection(parentsPlus, parentsPlus), a);
            final double lik2 = gaussianLikelihood(cov.getSelection(parents, parents), a);
            lik += lik1 - lik2;
            dof += p + 1;
        }

        return 2.0 * lik - getPenaltyDiscount() * dof * log(N); //getPriorForStructure(p, 2);
    }

    public double score2(int i, int[] parents) {
        double lik = 0.0;

        for (int k = 0; k < covs.size(); k++) {
            final int a = sampleSizes[k];
            TetradMatrix cov = covs.get(k);
            double residualVariance = cov.get(i, i);
            TetradMatrix covxx = cov.getSelection(parents, parents);
            TetradMatrix covxxInv = covxx.inverse();
            TetradVector covxy = cov.getSelection(parents, new int[]{i}).getColumn(0);
            TetradVector b = covxxInv.times(covxy);
            residualVariance -= covxy.dotProduct(b);
            lik += - (a / 2.0) * log(residualVariance) - (a / 2.0) - (a / 2.0) * log(2 * PI);
        }

//        System.out.println(lik);

        return 2.0 * lik - getPenaltyDiscount() * (parents.length + 1) * log(N);
    }

    private TetradMatrix cov(DataSet x) {
        TetradMatrix M = x.getDoubleData();
        RealMatrix covarianceMatrix = new Covariance(M.getRealMatrix(), true).getCovarianceMatrix();
        return new TetradMatrix(covarianceMatrix, covarianceMatrix.getRowDimension(), covarianceMatrix.getColumnDimension());
    }

    private int h(int p) {
        return p * (p + 1) / 2;
    }

    private double getPriorForStructure(int numParents, double e) {
        int vm = variables.size() - 1;
        return numParents * log(e / (vm)) + (vm - numParents) * log(1.0 - (e / (vm)));
    }

    private double gaussianLikelihood(TetradMatrix sigma, int n) {
        if (sigma.columns() == 0 || n == 0) return 0;
        int k = sigma.columns();
        return -0.5 * n * log(sigma.det()) - 0.5 * n * k - 0.5 * n * k * log(2.0 * PI);
    }

    private double logdet(TetradMatrix m) {
        RealMatrix M = m.getRealMatrix();
        final double tol = 1e-9;
        RealMatrix LT = new org.apache.commons.math3.linear.CholeskyDecomposition(M, tol, tol).getLT();

        double sum = 0.0;

        for (int i = 0; i < LT.getRowDimension(); i++) {
            sum += FastMath.log(LT.getEntry(i, i));
        }

        return 2.0 * sum;
    }

    private double logdet2(TetradMatrix m) {
        if (m.rows() == 0) return 0.0;

        RealMatrix M = m.getRealMatrix();
        final LUDecomposition luDecomposition = new LUDecomposition(M);
        RealMatrix L = luDecomposition.getL();
        RealMatrix U = luDecomposition.getU();
        RealMatrix P = luDecomposition.getP();

//        System.out.println(new TetradMatrix(L.multiply(U)));
//        System.out.println(new TetradMatrix(m));
//        System.out.println(new TetradMatrix(L));
//        System.out.println(new TetradMatrix(U));
//        System.out.println(new TetradMatrix(P));

        double sum = 0.0;

        for (int i = 0; i < L.getRowDimension(); i++) {
            sum += FastMath.log(L.getEntry(i, i));
        }

        for (int i = 0; i < U.getRowDimension(); i++) {
            sum += FastMath.log(U.getEntry(i, i));
        }

//        for (int i = 0; i < P.getRowDimension(); i++) {
//            sum += log(P.getEntry(i, i));
//        }

        return sum;
    }

    private int[] append(int[] parents, int i) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[all.length - 1] = i;
        return all;
    }
}



