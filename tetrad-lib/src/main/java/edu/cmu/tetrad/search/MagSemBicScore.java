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
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static edu.cmu.tetrad.util.MatrixUtils.convertCovToCorr;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.NaN;
import static java.lang.Math.abs;
import static java.lang.Math.log;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class MagSemBicScore implements Score {

    private boolean calculateRowSubsets = false;

    // The dataset.
    private DataModel dataModel;

    // .. as matrix
    private Matrix data;

    // The correlation matrix.
    private ICovarianceMatrix covariances;

    // The variables of the covariance matrix.
    private List<Node> variables;

    // The sample size of the covariance matrix.
    private final int sampleSize;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    // A  map from variable names to their indices.
    private final Map<Node, Integer> indexMap;

    // The penalty penaltyDiscount, 1 for standard BIC.
    private double penaltyDiscount = 1.0;

    // The structure prior, 0 for standard BIC.
    private double structurePrior = 0.0;

    // Equivalent sample size
    private Matrix matrix;

    // The rule type to use.
    private RuleType ruleType = RuleType.CHICKERING;

    /**
     * Constructs the score using a covariance matrix.
     */
    public MagSemBicScore(final ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
        this.indexMap = indexMap(this.variables);
    }

    /**
     * Constructs the score using a covariance matrix.
     */
    public MagSemBicScore(final DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataModel = dataSet;
        this.data = dataSet.getDoubleData();

        if (!dataSet.existsMissingValue()) {
            setCovariances(new CovarianceMatrix(dataSet, false));
            this.variables = this.covariances.getVariables();
            this.sampleSize = this.covariances.getSampleSize();
            this.indexMap = indexMap(this.variables);
            this.calculateRowSubsets = false;
            return;
        }

        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        this.indexMap = indexMap(this.variables);
        this.calculateRowSubsets = true;
    }

    public static double getVarRy(final int i, final int[] parents, final Matrix data, final ICovarianceMatrix covariances, final boolean calculateRowSubsets) {
        try {
            final int[] all = MagSemBicScore.concat(i, parents);
            final Matrix cov = MagSemBicScore.getCov(MagSemBicScore.getRows(i, parents, data, calculateRowSubsets), all, all, data, covariances);
            final int[] pp = MagSemBicScore.indexedParents(parents);
            final Matrix covxx = cov.getSelection(pp, pp);
            final Matrix covxy = cov.getSelection(pp, new int[]{0});
            final Matrix b = (covxx.inverse().times(covxy));
            final Matrix bStar = MagSemBicScore.bStar(b);
            return (bStar.transpose().times(cov).times(bStar).get(0, 0));
        } catch (final SingularMatrixException e) {
            final List<Node> variables = covariances.getVariables();
            final List<Node> p = new ArrayList<>();
            for (final int _p : parents) p.add(variables.get(_p));
            System.out.println("Singularity " + variables.get(i) + " | " + p);
            return NEGATIVE_INFINITY;
        }
    }

    @NotNull
    public static Matrix bStar(final Matrix b) {
        final Matrix byx = new Matrix(b.rows() + 1, 1);
        byx.set(0, 0, 1);
        for (int j = 0; j < b.rows(); j++) byx.set(j + 1, 0, -b.get(j, 0));
        return byx;
    }

    private static int[] indexedParents(final int[] parents) {
        final int[] pp = new int[parents.length];
        for (int j = 0; j < pp.length; j++) pp[j] = j + 1;
        return pp;
    }

    private static int[] concat(final int i, final int[] parents) {
        final int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static Matrix getCov(final List<Integer> rows, final int[] _rows, final int[] cols, final Matrix data, final ICovarianceMatrix covarianceMatrix) {
        if (rows == null) {
            return covarianceMatrix.getSelection(_rows, cols);
        }

        final Matrix cov = new Matrix(_rows.length, cols.length);

        for (int i = 0; i < _rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                double mui = 0.0;
                double muj = 0.0;

                for (final int k : rows) {
                    mui += data.get(k, _rows[i]);
                    muj += data.get(k, cols[j]);
                }

                mui /= rows.size() - 1;
                muj /= rows.size() - 1;

                double _cov = 0.0;

                for (final int k : rows) {
                    _cov += (data.get(k, _rows[i]) - mui) * (data.get(k, cols[j]) - muj);
                }

                final double mean = _cov / (rows.size());
                cov.set(i, j, mean);
            }
        }

        return cov;
    }

    private static List<Integer> getRows(final int i, final int[] parents, final Matrix data, final boolean calculateRowSubsets) {
        if (!calculateRowSubsets) {
            return null;
        }

        final List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < data.rows(); k++) {
            if (Double.isNaN(data.get(k, i))) continue;

            for (final int p : parents) {
                if (Double.isNaN(data.get(k, p))) continue K;
            }

            rows.add(k);
        }

        return rows;
    }

    @Override
    public double localScoreDiff(final int x, final int y, final int[] z) {
        if (this.ruleType == RuleType.NANDY) {
            return nandyBic(x, y, z);
        } else {
            return localScore(y, MagSemBicScore.append(z, x)) - localScore(y, z);
        }
    }

    public double nandyBic(final int x, final int y, final int[] z) {
        final double sp1 = getStructurePrior(z.length + 1);
        final double sp2 = getStructurePrior(z.length);

        final Node _x = this.variables.get(x);
        final Node _y = this.variables.get(y);
        final List<Node> _z = getVariableList(z);

        final List<Integer> rows = getRows(x, z);

        if (rows != null) {
            rows.retainAll(Objects.requireNonNull(getRows(y, z)));
        }

        final double r = partialCorrelation(_x, _y, _z, rows);

        final double c = getPenaltyDiscount();

        return -this.sampleSize * log(1.0 - r * r) - c * log(this.sampleSize)
                - 2.0 * (sp1 - sp2);
    }

    @Override
    public double localScoreDiff(final int x, final int y) {
        return localScoreDiff(x, y, new int[0]);
    }

    public double localScore(final int i, final int... parents) {
        final int k = parents.length;

        // Only do this once.
        final double n = this.sampleSize;

        final double varey;

        varey = MagSemBicScore.getVarRy(i, parents, this.data, this.covariances, this.calculateRowSubsets);

        final double c = getPenaltyDiscount();

        if (this.ruleType == RuleType.CHICKERING || this.ruleType == RuleType.NANDY) {

            // Standard BIC, with penalty discount and structure prior.
            return -c * k * log(n) - n * log(varey);// - 2 * getStructurePrior(k);
        } else {
            throw new IllegalStateException("That rule type is not implemented: " + this.ruleType);
        }
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

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public double getStructurePrior() {
        return this.structurePrior;
    }

    public ICovarianceMatrix getCovariances() {
        return this.covariances;
    }

    public int getSampleSize() {
        return this.sampleSize;
    }

    @Override
    public boolean isEffectEdge(final double bump) {
        return bump > 0;
    }

    public DataModel getDataModel() {
        return this.dataModel;
    }

    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setStructurePrior(final double structurePrior) {
        this.structurePrior = structurePrior;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    public void setVariables(final List<Node> variables) {
        if (this.covariances != null) {
            this.covariances.setVariables(variables);
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
        return (int) Math.ceil(log(this.sampleSize));
    }

    @Override
    public boolean determines(final List<Node> z, final Node y) {
        final int i = this.variables.indexOf(y);

        final int[] k = new int[z.size()];

        for (int t = 0; t < z.size(); t++) {
            k[t] = this.variables.indexOf(z.get(t));
        }

        final double v = localScore(i, k);

        return Double.isNaN(v);
    }

//    @Override
    public DataModel getData() {
        return this.dataModel;
    }

    private void setCovariances(final ICovarianceMatrix covariances) {
        this.covariances = covariances;
        this.matrix = this.covariances.getMatrix();

        this.dataModel = covariances;

//        double n = covariances.getSampleSize();
//        double ess = DataUtils.getEss(covariances);
//
//        System.out.println("n = " + n + " ess = " + ess);
    }

    private static int[] append(final int[] z, final int x) {
        final int[] _z = Arrays.copyOf(z, z.length + 1);
        _z[z.length] = x;
        return _z;
    }

    private double getStructurePrior(final int parents) {
        if (abs(getStructurePrior()) <= 0) {
            return 0;
        } else {
            final double p = (getStructurePrior()) / (this.variables.size());
            return -((parents) * Math.log(p) + (this.variables.size() - (parents)) * Math.log(1.0 - p));
        }
    }

    private List<Node> getVariableList(final int[] indices) {
        final List<Node> variables = new ArrayList<>();
        for (final int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }

    private Map<Node, Integer> indexMap(final List<Node> variables) {
        final Map<Node, Integer> indexMap = new HashMap<>();

        for (int i = 0; variables.size() > i; i++) {
            indexMap.put(variables.get(i), i);
        }

        return indexMap;
    }

    private List<Integer> getRows(final int i, final int[] parents) {
        if (this.dataModel == null) {
            return null;
        }

        final List<Integer> rows = new ArrayList<>();

        final DataSet dataSet = (DataSet) this.dataModel;

        K:
        for (int k = 0; k < dataSet.getNumRows(); k++) {
            if (Double.isNaN(dataSet.getDouble(k, i))) continue;

            for (final int p : parents) {
                if (Double.isNaN(dataSet.getDouble(k, p))) continue K;
            }

            rows.add(k);
        }

        return rows;
    }

    private double partialCorrelation(final Node x, final Node y, final List<Node> z, final List<Integer> rows) {
        try {
            return StatUtils.partialCorrelation(convertCovToCorr(getCov(rows, indices(x, y, z))));
        } catch (final Exception e) {
            return NaN;
        }
    }

    private int[] indices(final Node x, final Node y, final List<Node> z) {
        final int[] indices = new int[z.size() + 2];
        indices[0] = this.indexMap.get(x);
        indices[1] = this.indexMap.get(y);
        for (int i = 0; i < z.size(); i++) indices[i + 2] = this.indexMap.get(z.get(i));
        return indices;
    }

    private Matrix getCov(final List<Integer> rows, final int[] cols) {
        if (this.dataModel == null) {
            return this.matrix.getSelection(cols, cols);
        }

        final DataSet dataSet = (DataSet) this.dataModel;

        final Matrix cov = new Matrix(cols.length, cols.length);

        for (int i = 0; i < cols.length; i++) {
            for (int j = i + 1; j < cols.length; j++) {
                double mui = 0.0;
                double muj = 0.0;

                for (final int k : rows) {
                    mui += dataSet.getDouble(k, cols[i]);
                    muj += dataSet.getDouble(k, cols[j]);
                }

                mui /= rows.size() - 1;
                muj /= rows.size() - 1;

                double _cov = 0.0;

                for (final int k : rows) {
                    _cov += (dataSet.getDouble(k, cols[i]) - mui) * (dataSet.getDouble(k, cols[j]) - muj);
                }

                final double mean = _cov / (rows.size());
                cov.set(i, j, mean);
                cov.set(j, i, mean);
            }
        }

        for (int i = 0; i < cols.length; i++) {
            double mui = 0.0;

            for (final int k : rows) {
                mui += dataSet.getDouble(k, cols[i]);
            }

            mui /= rows.size();

            double _cov = 0.0;

            for (final int k : rows) {
                _cov += (dataSet.getDouble(k, cols[i]) - mui) * (dataSet.getDouble(k, cols[i]) - mui);
            }

            final double mean = _cov / (rows.size());
            cov.set(i, i, mean);
        }

        return cov;
    }

    public void setRuleType(final RuleType ruleType) {
        this.ruleType = ruleType;
    }

    public enum RuleType {CHICKERING, NANDY}
}


