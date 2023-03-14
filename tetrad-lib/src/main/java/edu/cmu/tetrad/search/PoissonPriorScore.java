///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Implements Poisson prior score (Bryan).
 *
 * @author Joseph Ramsey
 */
public class PoissonPriorScore implements Score {

    private DataSet dataSet;
    // The covariance matrix.
    private ICovarianceMatrix covariances;

    // The variables of the covariance matrix.
    private final List<Node> variables;

    // The sample size of the covariance matrix.
    private final int sampleSize;

    // True if verbose output should be sent to out.
    private boolean verbose;

    // Sample size or equivalent sample size.
    private double N;

    // The data, if it is set.
    private Matrix data;

    // True if row subsets should be calculated.
    private boolean calculateRowSubsets;

    private double lambda = 3.;

    /**
     * Constructs the score using a covariance matrix.
     */
    public PoissonPriorScore(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
    }

    /**
     * Constructs the score using a covariance matrix.
     */
    public PoissonPriorScore(DataSet dataSet, boolean precomputeCovariances) {

        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;

        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();

        DataSet _dataSet = DataUtils.center(dataSet);
        this.data = _dataSet.getDoubleData();

        if (!dataSet.existsMissingValue()) {
            if (!precomputeCovariances) {
                setCovariances(new CovarianceMatrixOnTheFly(dataSet));
            } else {
                setCovariances(new CovarianceMatrix(dataSet));
            }
            this.calculateRowSubsets = false;
        } else {
            this.calculateRowSubsets = true;
        }

    }

    private int[] indices(List<Node> __adj) {
        int[] indices = new int[__adj.size()];
        for (int t = 0; t < __adj.size(); t++) indices[t] = this.variables.indexOf(__adj.get(t));
        return indices;
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }



    /**
     * @param i The index of the node.
     * @param parents The indices of the node's parents.
     * @return The score, or NaN if the score cannot be calculated.
     */
    public double localScore(int i, int... parents) throws RuntimeException {
        int pi = parents.length + 1;
        int k = parents.length;
        double varRy;

        try {
            varRy = SemBicScore.getVarRy(i, parents, this.data, this.covariances, this.calculateRowSubsets);
        } catch (SingularMatrixException e){
            return Double.NaN;
        }

        double r = k * log(lambda);

        // Bryan
        double score = - 0.5 * this.N * log(varRy) - 0.5 * k * log(this.N) + r - Gamma.logGamma(k + 1.);

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    public static double getP(int pn, int m0, double lambda) {
        return 2 - pow(1 + (exp(-(lambda - 1) / 2.)) * sqrt(lambda), (double) pn - m0);
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */





    public ICovarianceMatrix getCovariances() {
        return this.covariances;
    }

    public int getSampleSize() {
        return this.sampleSize;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return (int) ceil(log(this.sampleSize));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = this.variables.indexOf(y);

        int[] k = indices(z);

        double v = localScore(i, k);

        return Double.isNaN(v);
    }

    public DataModel getData() {
        return this.dataSet;
    }

    private void setCovariances(ICovarianceMatrix covariances) {
        CorrelationMatrixOnTheFly correlations = new CorrelationMatrixOnTheFly(covariances);
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


        this.N = covariances.getSampleSize();
    }



    public void setLambda(double lambda) {
        if (lambda < 1.0) throw new IllegalArgumentException("Structure prior can't be < 1: " + lambda);
        this.lambda = lambda;
    }
}


