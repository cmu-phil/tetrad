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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.List;

import static java.lang.Math.log;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class SemBicScoreDeterministic implements Score {

    // The covariance matrix.
    private ICovarianceMatrix covariances;

    // The variables of the covariance matrix.
    private List<Node> variables;

    // The sample size of the covariance matrix.
    private final int sampleSize;

    // The penalty penaltyDiscount.
    private double penaltyDiscount = 1.0;

    // True if verbose output should be sent to out.
    private boolean verbose;

    private double determinismThreshold = 0.1;

    /**
     * Constructs the score using a covariance matrix.
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

        return -(n) * log(s2) - getPenaltyDiscount() * k * log(n);
    }


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

    @Override
    public double localScoreDiff(int x, int y) {
        return localScoreDiff(x, y, new int[0]);

//        return localScore(y, x) - localScore(y);
    }

    private int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 1, parents.length);
        all[0] = extra;
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

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public ICovarianceMatrix getCovariances() {
        return this.covariances;
    }

    public int getSampleSize() {
        return this.sampleSize;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;//-0.25 * getPenaltyDiscount() * Math.log(sampleSize);
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
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

    private Matrix getSelection(ICovarianceMatrix cov, int[] rows, int[] cols) {
        return cov.getSelection(rows, cols);
    }

    private void setCovariances(ICovarianceMatrix covariances) {
        this.covariances = covariances;
    }

    public void setVariables(List<Node> variables) {
        this.covariances.setVariables(variables);
        this.variables = variables;
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
        return (int) Math.ceil(log(this.sampleSize));
    }

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
            throw new RuntimeException("Singular");
        }

        return false;
    }

    public double getDeterminismThreshold() {
        return this.determinismThreshold;
    }

    public void setDeterminismThreshold(double determinismThreshold) {
        this.determinismThreshold = determinismThreshold;
    }
}



