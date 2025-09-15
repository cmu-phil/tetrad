///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;

import java.util.List;

import static org.apache.commons.math3.util.FastMath.log;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemBicScoreDeterministic implements Score {

    // The sample size of the covariance matrix.
    private final int sampleSize;
    // The covariance matrix.
    private ICovarianceMatrix covariances;
    // The variables of the covariance matrix.
    private List<Node> variables;
    // The penalty penaltyDiscount.
    private double penaltyDiscount = 1.0;

    // True if verbose output should be sent to out.
    private boolean verbose;

    private double determinismThreshold = 0.1;

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param covariances a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public SemBicScoreDeterministic(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        this.setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     *
     * @param i       a int
     * @param parents a int
     * @return a double
     */
    public double localScore(int i, int... parents) {
        double small = getDeterminismThreshold();

        double s2 = getCovariances().getValue(i, i);
        int p = parents.length;

        Matrix covxx = getSelection(getCovariances(), parents, parents);
        Vector covxy = getSelection(getCovariances(), parents, new int[]{i}).getColumn(0);

        try {
            s2 -= covxx.inverse().times(covxy).dotProduct(covxy);
        } catch (SingularMatrixException e) {
            System.out.println(LogUtilsSearch.getScoreFact(i, parents, variables));
            s2 = 0;
        }

//        System.out.println(s2);

        int n = getSampleSize();
        int k = 2 * p + 1;

        if (s2 < small) {
            s2 = 0;
        }

        if (s2 == 0) {
            return Double.NaN;
        }

        double score = -(n) * log(s2) - getPenaltyDiscount() * k * log(n);

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {


        double v1 = localScore(y, append(z, x));
        double v2 = localScore(y, z);
        double v3 = localScore(y, x);

        if (Double.isNaN(v1) && !Double.isNaN(v2) && !Double.isNaN(v3)) {
            return Double.NaN;
        } else if (Double.isNaN(v1) || Double.isNaN(v2) || Double.isNaN(v3)) {
            return Double.NEGATIVE_INFINITY;
        }

        return v1 - v2;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     *
     * @return a double
     */
    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    /**
     * <p>Setter for the field <code>penaltyDiscount</code>.</p>
     *
     * @param penaltyDiscount a double
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * <p>Getter for the field <code>covariances</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public ICovarianceMatrix getCovariances() {
        return this.covariances;
    }

    private void setCovariances(ICovarianceMatrix covariances) {
        this.covariances = covariances;
    }

    /**
     * <p>Getter for the field <code>sampleSize</code>.</p>
     *
     * @return a int
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;//-0.25 * getPenaltyDiscount() * FastMath.log(sampleSize);
    }

    /**
     * <p>getDataSet.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>isVerbose.</p>
     *
     * @return a boolean
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * <p>Setter for the field <code>variables</code>.</p>
     *
     * @param variables a {@link java.util.List} object
     */
    public void setVariables(List<Node> variables) {
        this.covariances.setVariables(variables);
        this.variables = variables;
    }

    private Matrix getSelection(ICovarianceMatrix cov, int[] rows, int[] cols) {
        return cov.getSelection(rows, cols);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(log(this.sampleSize));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = this.variables.indexOf(y);

        int[] parents = new int[z.size()];

        for (int t = 0; t < z.size(); t++) {
            parents[t] = this.variables.indexOf(z.get(t));
        }

        double small = getDeterminismThreshold();

        try {
            double s2 = getCovariances().getValue(i, i);

            Matrix covxx = getSelection(getCovariances(), parents, parents);
            Vector covxy = getSelection(getCovariances(), parents, new int[]{i}).getColumn(0);
            s2 -= covxx.inverse().times(covxy).dotProduct(covxy);

            if (s2 <= small) {
                return true;
            }
        } catch (SingularMatrixException ignored) {
            System.out.println("Singularity encountered when scoring " +
                               LogUtilsSearch.getScoreFact(y, z));
            return true;
        }

        return false;
    }

    /**
     * <p>Getter for the field <code>determinismThreshold</code>.</p>
     *
     * @return a double
     */
    public double getDeterminismThreshold() {
        return this.determinismThreshold;
    }

    /**
     * <p>Setter for the field <code>determinismThreshold</code>.</p>
     *
     * @param determinismThreshold a double
     */
    public void setDeterminismThreshold(double determinismThreshold) {
        this.determinismThreshold = determinismThreshold;
    }
}




