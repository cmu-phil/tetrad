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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.work_in_progress.MagSemBicScore;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.List;

import static org.apache.commons.math3.util.FastMath.ceil;
import static org.apache.commons.math3.util.FastMath.log;

/**
 * <p>Implements the extended BIC (EBIC) score. The reference is here:</p>
 *
 * <p>Chen, J., & Chen, Z. (2008). Extended Bayesian information criteria for
 * model selection with large model spaces. Biometrika, 95(3), 759-771.</p>
 *
 * <p>As for all scores in Tetrad, higher scores mean more dependence, and negative
 * scores indicate independence.</p>
 *
 * @author josephramsey
 */
public class EbicScore implements Score {
    private final List<Node> variables;
    private final int sampleSize;
    private DataSet dataSet;
    private ICovarianceMatrix covariances;
    private double N;
    private Matrix data;
    private boolean calculateRowSubsets;
    private double gamma = 1;

    /**
     * Constructs the score using a covariance matrix.
     */
    public EbicScore(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
    }

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param dataSet               The continuous dataset to analyze.
     * @param precomputeCovariances Whether the covariances should be precomputed or computed on the fly. True if
     *                              precomputed.
     */
    public EbicScore(DataSet dataSet, boolean precomputeCovariances) {

        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;

        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();

        DataSet _dataSet = DataUtils.center(dataSet);
        this.data = _dataSet.getDoubleData();

        if (!dataSet.existsMissingValue()) {
            setCovariances(SimpleDataLoader.getCovarianceMatrix(dataSet, precomputeCovariances));
            this.calculateRowSubsets = false;
        } else {
            this.calculateRowSubsets = true;
        }

    }

    /**
     * @return localScore(y | z, x) - localScore(y | z).
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Returns the score of the node i given its parents.
     *
     * @param i       The index of the node.
     * @param parents The indices of the node's parents.
     * @return The score, or NaN if the score cannot be calculated.
     */
    public double localScore(int i, int... parents) throws RuntimeException {
        int pi = parents.length;
        double varRy;

        try {
            varRy = SemBicScore.getVarRy(i, parents, this.data, this.covariances, this.calculateRowSubsets);
        } catch (SingularMatrixException e) {
            System.out.println("Singularity encountered when scoring " +
                    LogUtilsSearch.getScoreFact(i, parents, variables));
            return Double.NaN;
        }

        double gamma = this.gamma;//  1.0 - riskBound;

        double score = -(this.N * log(varRy) + (pi * log(this.N)
                + 2 * pi * gamma * ChoiceGenerator.logCombinations(this.variables.size() - 1, pi)));

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    /**
     * Returns the sample size.
     *
     * @return This size.
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Returns a judgement for FGES of whether the given bump implies an effect edge.
     *
     * @param bump The bump
     * @return True if so
     * @see Fges
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Returns the variables for this score.
     *
     * @return Thsi list.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns an estimate of max degree of the graph for some algorithms.
     *
     * @return This max degree.
     * @see Fges
     * @see MagSemBicScore
     */
    @Override
    public int getMaxDegree() {
        return (int) ceil(log(this.sampleSize));
    }

    /**
     * Return a judgment of whether the variable in z determine y exactly.
     *
     * @return This judgment
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = this.variables.indexOf(y);

        int[] k = indices(z);

        double v = localScore(i, k);

        return Double.isNaN(v);
    }

    /**
     * Sets the gamma parameter for EBIC.
     *
     * @param gamma The gamma parameter.
     */
    public void setGamma(double gamma) {
        this.gamma = gamma;
    }

    private void setCovariances(ICovarianceMatrix covariances) {
//        CorrelationMatrix correlations = new CorrelationMatrix(covariances);
        this.covariances = covariances;

//        boolean exists = false;
//
//        double correlationThreshold = 1.0;
//        for (int i = 0; i < correlations.getSize(); i++) {
//            for (int j = 0; j < correlations.getSize(); j++) {
//                if (i == j) continue;
//                double r = correlations.getValue(i, j);
//                if (abs(r) > correlationThreshold) {
//                    System.out.println("Absolute correlation too high: " + r);
//                    exists = true;
//                }
//            }
//        }
//
//        if (exists) {
//            throw new IllegalArgumentException("Some correlations are too high (> " + correlationThreshold
//                    + ") in absolute value.");
//        }


        this.N = covariances.getSampleSize();
    }

    private int[] indices(List<Node> __adj) {
        int[] indices = new int[__adj.size()];
        for (int t = 0; t < __adj.size(); t++) indices[t] = this.variables.indexOf(__adj.get(t));
        return indices;
    }
}


