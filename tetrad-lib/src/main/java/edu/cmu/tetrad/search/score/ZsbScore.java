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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * <p>Implements the Zhang-Shen bound score. This is an adaptation of
 * Theorem 1 in the following:</p>
 *
 * <p>Zhang, Y., &amp; Shen, X. (2010). Model selection procedure for
 * high‐dimensional data. Statistical Analysis and Data Mining: The
 * ASA Data Science Journal, 3(5), 350-358</p>
 *
 * <p>The score uses Theorem 1 in the above to numerically search
 * for a lambda value that is bounded by a given probability risk,
 * between 0 and 1, if outputting a local false positive parent for
 * a variable. There is a parameter m0, which is a maximum number
 * of parents for a particular variable, which is free. The solution
 * of this score is to increase m0 from 0 upward, re-evaluating
 * with each scoring that is done using that variable as a target
 * node. Thus, over time, a lower bound on m0 is estimated with
 * more and more precision. So as the score is used in the context
 * of FGES or GRaSP, for instance, so long as the score for a given
 * node is visited more than once, the scores output by the procedure
 * can be expected to improve, though setting m0 to 0 for all variables
 * does not give bad results even by itself.</p>
 *
 * <p>As for all scores in Tetrad, higher scores mean more dependence, and negative
 * scores indicates independence.</p>
 *
 * @author josephramsey
 */
public class ZsbScore implements Score {

    // The variables of the covariance matrix.
    private final List<Node> variables;
    private double riskBound = 0.001;
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
    // A record of lambdas for each m0.
    private List<Double> lambdas;
    // The data, if it is set.
    private Matrix data;

    private boolean changed = false;

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param covMatrix The covariance matrix.
     */
    public ZsbScore(ICovarianceMatrix covMatrix) {
        if (covMatrix == null) {
            throw new NullPointerException();
        }

        setCovariances(covMatrix);
        this.variables = covMatrix.getVariables();
        this.sampleSize = covMatrix.getSampleSize();
    }

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param dataSet The data set.
     */
    public ZsbScore(DataSet dataSet) {
        this(SimpleDataLoader.getCovarianceMatrix(dataSet));
        this.data = dataSet.getDoubleData();
    }

    /**
     * Returns the score for the child given the parents.
     *
     * @param i       The index of the node.
     * @param parents The indices of the node's parents.
     * @return The score
     */
    public double localScore(int i, int... parents) {
        int pn = variables.size() - 1;

        // True if row subsets should be calculated.
        boolean calculateRowSubsets = false;

        if (this.estMaxParents == null) {
            this.estMaxParents = new int[variables.size()];
            this.maxScores = new double[variables.size()];
            this.estMaxVarRys = new double[variables.size()];

            for (int j = 0; j < variables.size(); j++) {
                this.estMaxParents[j] = 0;
                this.maxScores[j] = Double.NEGATIVE_INFINITY;
                this.estMaxVarRys[j] = Double.NaN;
            }
        }

        final int pi = parents.length;
        double varRy = SemBicScore.getVarRy(i, parents, data, covariances, calculateRowSubsets);

        int m0 = estMaxParents[i];

        double score = -(0.5 * sampleSize * log(varRy) + getLambda(m0, pn) * pi);

        if (score >= maxScores[i]) {
            maxScores[i] = score;
            estMaxParents[i] = parents.length;
            estMaxVarRys[i] = varRy;
        }

        return score;
    }

    /**
     * Returns localScore(y | z, x) - localScore(y | z).
     *
     * @param x Node 1
     * @param y Node 2
     * @param z The conditioning nodes.
     * @return The score.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    public ICovarianceMatrix getCovariances() {
        return covariances;
    }

    private void setCovariances(ICovarianceMatrix covariances) {
        CorrelationMatrix correlations = new CorrelationMatrix(covariances);
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

    /**
     * Returns the sample size.
     * @return This size.
     */
    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * Returns a judgement for FGES for whether a certain bump in score gives
     * efidence of an effect edges.
     * @param bump The bump.
     * @return True if so.
     * @see Fges
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Returns the variables.
     * @return This list.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Returns a judgment of max degree for some algorithms.
     * @return This maximum.
     * @see Fges
     */
    @Override
    public int getMaxDegree() {
        return (int) ceil(log(sampleSize));
    }

    /**
     * Returns true if the variable in Z determine y.
     * @return True if so.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);

        int[] k = indices(z);

        double v = localScore(i, k);

        return Double.isNaN(v);
    }

    /**
     * Sets the risk bound for the Zhang Shen Bound score.
     * @param riskBound The risk bound.
     */
    public void setRiskBound(double riskBound) {
        this.riskBound = riskBound;
    }

    private static double zhangShenLambda(int m0, double pn, double riskBound) {
        if (m0 > pn) throw new IllegalArgumentException("m0 should not be > pn; m0 = " + m0 + " pn = " + pn);

        double high = 10000.0;
        double low = 0.0;

        while (high - low > 1e-13) {
            double lambda = (high + low) / 2.0;

            double p = getP(pn, m0, lambda);

            if (p < 1.0 - riskBound) {
                low = lambda;
            } else {
                high = lambda;
            }
        }

        return low;
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

    private static double getP(double pn, double m0, double lambda) {
        return 2. - pow((1. + (exp(-(lambda - 1.) / 2.)) * sqrt(lambda)), pn - m0);
    }

    private int[] indices(List<Node> __adj) {
        int[] indices = new int[__adj.size()];
        for (int t = 0; t < __adj.size(); t++) indices[t] = variables.indexOf(__adj.get(t));
        return indices;
    }
}


