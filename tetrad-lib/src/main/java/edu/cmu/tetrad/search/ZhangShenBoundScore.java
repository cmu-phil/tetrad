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
import edu.cmu.tetrad.util.Matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.*;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class ZhangShenBoundScore implements Score {

    // The variables of the covariance matrix.
    private final List<Node> variables;
    private DataSet dataSet;
    private double riskBound;
    // The running maximum score, for estimating the true minimal model.
    double[] maxScores;
    // The running estimate of the number of parents in the true minimal model.
    int[] estMaxParents;
    // The running estimate of the residual variance of the true minimal model.
    double[] estMaxVarRys;
    // The covariance matrix.
    private ICovarianceMatrix covariances;
    // The sample size of the covariance matrix.
    private int sampleSize;
    // True if verbose output should be sent to out.
    private boolean verbose = false;
    // A recpord of lambdas for each m0.
    private List<Double> lambdas;
    // The data, if it is set.
    private Matrix data;

    // True if row subsets should be calculated.
    private boolean calculateRowSubsets = false;
    private boolean changed = false;

    /**
     * Constructs the score using a covariance matrix.
     */
    public ZhangShenBoundScore(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
        this.estMaxParents = new int[variables.size()];
        Arrays.fill(this.estMaxParents, 0);
        this.maxScores = new double[variables.size()];
        this.estMaxVarRys = new double[variables.size()];

        this.riskBound = 3.0 / covariances.getDimension();
    }

    /**
     * Constructs the score using a covariance matrix.
     */
    public ZhangShenBoundScore(DataSet dataSet) {
        this(new CovarianceMatrix(dataSet));

        this.dataSet = dataSet;

//        if (dataSet == null) {
//            throw new NullPointerException();
//        }
//
//        this.variables = dataSet.getVariables();
//        this.sampleSize = dataSet.getNumRows();
//
//        DataSet _dataSet = DataUtils.center(dataSet);
//        this.data = _dataSet.getDoubleData();
//
//        if (!dataSet.existsMissingValue()) {
//            setCovariances(new CovarianceMatrix(dataSet));
//            calculateRowSubsets = false;
//        } else {
//            calculateRowSubsets = true;
//        }
//
//        this.riskBound = 3.0 / dataSet.getNumColumns();
    }

    public static double zhangShenLambda(int m0, int pn, double riskBound) {
//        if (pn == m0) throw new IllegalArgumentException("m0 should not equal pn");
//        int sn = min(pn, 12);
        int sn = pn;//max(sn, 0);

        double high = 10000;
        double low = 0.0;

        while (high - low > 1e-10) {
            double lambda = (high + low) / 2.0;

            double p = getP(sn, m0, lambda);

            if (p < 1.0 - riskBound) {
                low = lambda;
            } else {
                high = lambda;
            }
        }

        return low;
    }

    public static double getP(int pn, int m0, double lambda) {
        return 2 - pow((1 + (exp(-(lambda - 1) / 2.)) * sqrt(lambda)), pn - m0);
    }

    private static int[] append(int[] z, int x) {
        int[] _z = Arrays.copyOf(z, z.length + 1);
        _z[z.length] = x;
        return _z;
    }

    private int[] indices(List<Node> __adj) {
        int[] indices = new int[__adj.size()];
        for (int t = 0; t < __adj.size(); t++) indices[t] = variables.indexOf(__adj.get(t));
        return indices;
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScoreDiff(x, y, new int[0]);
    }

    public double localScore(int i, int... parents) throws RuntimeException {
        int pn = variables.size() - 1;

        if (this.estMaxParents == null) {
            this.estMaxParents = new int[variables.size()];
            this.maxScores = new double[variables.size()];
            this.estMaxVarRys = new double[variables.size()];

            for (int j = 0; j < variables.size(); j++) {
                this.estMaxParents[j] = 0;
                this.maxScores[j] = localScore(j, new int[0]);
                this.estMaxVarRys[j] = SemBicScore.getVarRy(j, new int[0], data, covariances, calculateRowSubsets);
            }
        }

        final int pi = parents.length;
        double varRy = SemBicScore.getVarRy(i, parents, data, covariances, calculateRowSubsets);

        int m0 = estMaxParents[i];

        double score = -(sampleSize * log(varRy) + getLambda(m0, pn) * pi * 2);

        if (score > maxScores[i]) {
            maxScores[i] = score;
            estMaxParents[i] = parents.length;
            estMaxVarRys[i] = varRy;
        }

        return score;
    }

    private double getLambda(int m0, int pn) {
        if (lambdas == null) {
            lambdas = new ArrayList<>();
        }

        if (lambdas.size() - 1 < m0) {
            for (int t = lambdas.size(); t <= m0; t++) {
                double lambda = zhangShenLambda(t, pn, riskBound);
                lambdas.add(lambda);
            }
        }

        return lambdas.get(m0);
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

    public ICovarianceMatrix getCovariances() {
        return covariances;
    }

    private void setCovariances(ICovarianceMatrix covariances) {
        CorrelationMatrix correlations = new CorrelationMatrix(covariances);
//        this.covariances = correlations;
        this.covariances = covariances;

        boolean exists = false;

        double correlationThreshold = 1.0;
        for (int i = 0; i < correlations.getSize(); i++) {
            for (int j = 0; j < correlations.getSize(); j++) {
                if (i == j) continue;
                double r = correlations.getValue(i, j);
                if (abs(r) > correlationThreshold) {
                    System.out.println("Absolute correlation too high: " + r);
                    exists = true;
                }
            }
        }

        if (exists) {
            throw new IllegalArgumentException("Some correlations are too high (> " + correlationThreshold
                    + ") in absolute value.");
        }


        this.sampleSize = covariances.getSampleSize();
    }

    public int getSampleSize() {
        return sampleSize;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
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
        return (int) ceil(log(sampleSize));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);

        int[] k = indices(z);

        double v = localScore(i, k);

        return Double.isNaN(v);
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean b) {
        changed = b;
    }

    public void setRiskBound(double riskBound) {
        this.riskBound = riskBound;
    }
}


