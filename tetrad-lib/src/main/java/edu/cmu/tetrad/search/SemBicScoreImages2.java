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
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class SemBicScoreImages2 implements Score {

    // The covariance matrix.
    private final List<ICovarianceMatrix> covariances;

    // The variables of the covariance matrix.
    private List<Node> variables;

    // The sample size of the covariance matrix.
    private final int sampleSize;

    // The penalty penaltyDiscount.
    private double penaltyDiscount = 2.0;

    // True if linear dependencies should return NaN for the score, and hence be
    // ignored by FGES
    private boolean ignoreLinearDependent;

    // The printstream output should be sent to.
    private PrintStream out = System.out;

    // True if verbose output should be sent to out.
    private boolean verbose;
    private final Set<Integer> forbidden = new HashSet<>();

    /**
     * Constructs the score using a covariance matrix.
     */
    public SemBicScoreImages2(final List<DataModel> dataModels) {
        if (dataModels == null) {
            throw new NullPointerException();
        }

        this.penaltyDiscount = 2;
        this.variables = dataModels.get(0).getVariables();

        this.covariances = new ArrayList<>();

        for (final DataModel model : dataModels) {
            if (model instanceof DataSet) {
                final DataSet dataSet = (DataSet) model;

                if (!dataSet.isContinuous()) {
                    throw new IllegalArgumentException("Datasets must be continuous.");
                }

                final CovarianceMatrix cov = new CovarianceMatrix(dataSet);
                cov.setVariables(this.variables);
                this.covariances.add(cov);
            } else if (model instanceof ICovarianceMatrix) {
                ((ICovarianceMatrix) model).setVariables(this.variables);
                this.covariances.add((ICovarianceMatrix) model);
            } else {
                throw new IllegalArgumentException("Only continuous data sets and covariance matrices may be used as input.");
            }
        }

        this.sampleSize = this.covariances.get(0).getSampleSize();
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(final int i, int... parents) {
        for (final int p : parents) if (this.forbidden.contains(p)) return Double.NaN;
        double lik = 0.0;

        for (int k = 0; k < this.covariances.size(); k++) {
            double residualVariance = getCovariances(k).getValue(i, i);
            final Matrix covxx = getSelection1(getCovariances(k), parents);

            try {
                final Matrix covxxInv = covxx.inverse();

                final Vector covxy = getSelection2(getCovariances(k), parents, i);
                final Vector b = covxxInv.times(covxy);
                residualVariance -= covxy.dotProduct(b);

                if (residualVariance <= 0) {
                    if (isVerbose()) {
                        this.out.println("Nonpositive residual varianceY: resVar / varianceY = " +
                                (residualVariance / getCovariances(k).getValue(i, i)));
                    }
                    return Double.NaN;
                }

                final int cols = getCovariances(0).getDimension();
                final double q = 2 / (double) cols;
                lik += -this.sampleSize * Math.log(residualVariance);
            } catch (final Exception e) {
                boolean removedOne = true;

                while (removedOne) {
                    final List<Integer> _parents = new ArrayList<>();
                    for (int y = 0; y < parents.length; y++) _parents.add(parents[y]);
                    _parents.removeAll(this.forbidden);
                    parents = new int[_parents.size()];
                    for (int y = 0; y < _parents.size(); y++) parents[y] = _parents.get(y);
                    removedOne = printMinimalLinearlyDependentSet(parents, getCovariances(k));
                }

                return Double.NaN;
            }
        }

        final int p = parents.length;
        final double c = getPenaltyDiscount();
        return 2 * lik - c * (p + 1) * Math.log(this.covariances.size() * this.sampleSize);
    }

    @Override
    public double localScoreDiff(final int x, final int y, final int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(final int x, final int y) {
        return localScore(y, x) - localScore(y);
    }

    private int[] append(final int[] parents, final int extra) {
        final int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */

    public double localScore(final int i, final int parent) {
        return localScore(i, new int[]{parent});
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(final int i) {
        return localScore(i, new int[0]);
    }

    /**
     * True iff edges that cause linear dependence are ignored.
     */
    public boolean isIgnoreLinearDependent() {
        return this.ignoreLinearDependent;
    }

    public void setIgnoreLinearDependent(final boolean ignoreLinearDependent) {
        this.ignoreLinearDependent = ignoreLinearDependent;
    }

    public void setOut(final PrintStream out) {
        this.out = out;
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    private ICovarianceMatrix getCovariances(final int i) {
        return this.covariances.get(i);
    }

    public int getSampleSize() {
        return this.sampleSize;
    }

    @Override
    public boolean isEffectEdge(final double bump) {
        return bump > 0;//-0.25 * getPenaltyDiscount() * Math.log(sampleSize);
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    // Calculates the BIC score.
    private double score(final double residualVariance, final int n, final double logn, final int p, final double c) {
        final int cols = getCovariances(0).getDimension();
        final double q = 2 / (double) cols;
        final double bic = -n * Math.log(residualVariance) - c * (p + 1) * logn;
        final double structPrior = (p * Math.log(q) + (cols - p) * Math.log(1.0 - q));
        return bic;//+ structPrior;
    }

    // Calculates the BIC score.
//    private double score(double residualVariance, int n, double logn, int p, double c) {
//        int cols = getDataModel().getNumColumns();
//        double q = 2 / (double) cols;
//
//        return -n * Math.log(residualVariance) - c * (p + 1) * logn;
//
////        return -n * Math.log(residualVariance) - c * (p + 1) * logn + (p * Math.log(q) + (n - p) * Math.log(1.0 - q));
//    }


    private Matrix getSelection1(final ICovarianceMatrix cov, final int[] rows) {
        return cov.getSelection(rows, rows);
    }

    private Vector getSelection2(final ICovarianceMatrix cov, final int[] rows, final int k) {
        return cov.getSelection(rows, new int[]{k}).getColumn(0);
    }

    // Prints a smallest subset of parents that causes a singular matrix exception.
    private boolean printMinimalLinearlyDependentSet(final int[] parents, final ICovarianceMatrix cov) {
        final List<Node> _parents = new ArrayList<>();
        for (final int p : parents) _parents.add(this.variables.get(p));

        final DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            final int[] sel = new int[choice.length];
            final List<Node> _sel = new ArrayList<>();
            for (int m = 0; m < choice.length; m++) {
                sel[m] = parents[m];
                _sel.add(this.variables.get(sel[m]));
            }

            final Matrix m = cov.getSelection(sel, sel);

            try {
                m.inverse();
            } catch (final Exception e2) {
                this.forbidden.add(sel[0]);
                this.out.println("### Linear dependence among variables: " + _sel);
                this.out.println("### Removing " + _sel.get(0));
                return true;
            }
        }

        return false;
    }

    public void setVariables(final List<Node> variables) {
        for (final ICovarianceMatrix cov : this.covariances) {
            cov.setVariables(variables);
        }
        this.variables = variables;
    }

    @Override
    public Node getVariable(final String targetName) {
        for (final Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(this.sampleSize));
    }

    @Override
    public boolean determines(final List<Node> z, final Node y) {
        return false;
    }
}



