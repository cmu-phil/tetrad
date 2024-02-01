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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static edu.cmu.tetrad.util.MatrixUtils.convertCovToCorr;
import static java.lang.Double.NaN;
import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.log;

/**
 * Implements the linear, Gaussian BIC score, with a 'penalty discount' multiplier on the BIC penalty. The formula used
 * for the score is BIC = 2L - ck ln n, where c is the penalty discount and L is the linear, Gaussian log
 * likelihood--that is, the sum of the log likelihoods of the individual records, which are assumed to be i.i.d.
 * <p>
 * For FGES, Chickering uses the standard linear, Gaussian BIC score, so we will for lack of a better reference give his
 * paper:
 * <p>
 * Chickering (2002) "Optimal structure identification with greedy search" Journal of Machine Learning Research.
 * <p>
 * The version of the score due to Nandy et al. is given in this reference:
 * <p>
 * Nandy, P., Hauser, A., &amp; Maathuis, M. H. (2018). High-dimensional consistency in score-based and hybrid structure
 * learning. The Annals of Statistics, 46(6A), 3151-3183.
 * <p>
 * This score may be used anywhere though where a linear, Gaussian score is needed. Anecdotally, the score is fairly
 * robust to non-Gaussianity, though with some additional unfaithfulness over and above what the score would give for
 * Gaussian data, a detriment that can be overcome to an extent by use a permutation algorithm such as SP, GRaSP, or
 * BOSS.
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.Fges
 * @see edu.cmu.tetrad.search.Sp
 * @see edu.cmu.tetrad.search.Grasp
 * @see edu.cmu.tetrad.search.Boss
 */
public class SemBicScore implements Score {

    // The sample size of the covariance matrix.
    private final int sampleSize;
    // A  map from variable names to their indices.
    private final Map<Node, Integer> indexMap;
    // The log of the sample size.
    private final double logN;
    // True if row subsets should be calculated.
    private boolean calculateRowSubsets;
    // The dataset.
    private DataModel dataModel;
    // .. as matrix
    private Matrix data;
    // The correlation matrix.
    private ICovarianceMatrix covariances;
    // The variables of the covariance matrix.
    private List<Node> variables;
    // True if verbose output should be sent to out.
    private boolean verbose;
    // The penalty penaltyDiscount, 1 for standard BIC.
    private double penaltyDiscount = 1.0;
    // The structure prior, 0 for standard BIC.
    private double structurePrior;
    // The covariance matrix.
    private Matrix matrix;
    // The rule type to use.
    private RuleType ruleType = RuleType.CHICKERING;
    // True iff the pseudo-inverse should be used instead of the inverse to avoid exceptions.
    private boolean usePseudoInverse = false;

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param covariances The covariance matrix.
     */
    public SemBicScore(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
        this.indexMap = indexMap(this.variables);
        this.logN = log(sampleSize);
    }

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param dataSet               The dataset.
     * @param precomputeCovariances Whether the covariances should be precomputed or computed on the fly. True if
     */
    public SemBicScore(DataSet dataSet, boolean precomputeCovariances) {

        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataModel = dataSet;
        this.data = dataSet.getDoubleData();

        if (!dataSet.existsMissingValue()) {
            setCovariances(getCovarianceMatrix(dataSet, precomputeCovariances));

            this.variables = this.covariances.getVariables();
            this.sampleSize = this.covariances.getSampleSize();
            this.indexMap = indexMap(this.variables);
            this.calculateRowSubsets = false;
            this.logN = log(sampleSize);
            return;
        }

        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        this.indexMap = indexMap(this.variables);
        this.calculateRowSubsets = true;
        this.logN = log(sampleSize);
    }

    /**
     * Returns the variance of the residual of the regression of the ith variable on its parents.
     *
     * @param i                   The index of the variable.
     * @param parents             The indices of the parents.
     * @param covariances         The covariance matrix.
     * @param calculateRowSubsets True if row subsets should be calculated.
     * @return The variance of the residual of the regression of the ith variable on its parents.
     */
    public static double getVarRy(int i, int[] parents, Matrix data, ICovarianceMatrix covariances,
                                  boolean calculateRowSubsets, boolean usePseudoInverse)
            throws SingularMatrixException {
        CovAndCoefs covAndcoefs = getCovAndCoefs(i, parents, data, covariances, calculateRowSubsets, usePseudoInverse);
        return (bStar(covAndcoefs.b()).transpose().times(covAndcoefs.cov()).times(bStar(covAndcoefs.b())).get(0, 0));
    }

    /**
     * Returns the covariance matrix of the regression of the ith variable on its parents and the regression
     * coefficients.
     *
     * @param i                   The index of the variable.
     * @param parents             The indices of the parents.
     * @param data                The data matrix.
     * @param covariances         The covariance matrix.
     * @param calculateRowSubsets True if row subsets should be calculated.
     * @param usePseudoInverse    True if the pseudo-inverse should be used instead of the inverse to avoid exceptions.
     * @return The covariance matrix of the regression of the ith variable on its parents and the regression
     * coefficients.
     */
    @NotNull
    public static SemBicScore.CovAndCoefs getCovAndCoefs(int i, int[] parents, Matrix data, ICovarianceMatrix covariances, boolean calculateRowSubsets, boolean usePseudoInverse) {
        List<Integer> rows = SemBicScore.getRows(i, parents, data, calculateRowSubsets);
        return getCovAndCoefs(i, parents, data, covariances, usePseudoInverse, rows);
    }

    /**
     * Returns the covariance matrix of the regression of the ith variable on its parents and the regression
     *
     * @param i                The index of the variable.
     * @param parents          The indices of the parents.
     * @param data             The data matrix.
     * @param covariances      The covariance matrix.
     * @param usePseudoInverse True if the pseudo-inverse should be used instead of the inverse to avoid exceptions.
     * @param rows             The rows to use.
     * @return The covariance matrix of the regression of the ith variable on its parents and the regression
     */
    @NotNull
    public static CovAndCoefs getCovAndCoefs(int i, int[] parents, Matrix data, ICovarianceMatrix covariances, boolean usePseudoInverse, List<Integer> rows) {
        int[] all = SemBicScore.concat(i, parents);
        Matrix cov = SemBicScore.getCov(rows, all, all, data, covariances);
        int[] pp = SemBicScore.indexedParents(parents);
        Matrix covxx = cov.getSelection(pp, pp);
        Matrix covxy = cov.getSelection(pp, new int[]{0});

        // The regression coefficient vector.
        Matrix b;

        if (usePseudoInverse) {
            b = new Matrix(MatrixUtils.pseudoInverse(covxx.toArray())).times(covxy);
        } else {
            b = covxx.inverse().times(covxy);
        }

        return new CovAndCoefs(cov, b);
    }

    @NotNull
    private static Matrix bStar(Matrix b) {
        Matrix byx = new Matrix(b.getNumRows() + 1, 1);
        byx.set(0, 0, 1);
        for (int j = 0; j < b.getNumRows(); j++) byx.set(j + 1, 0, -b.get(j, 0));
        return byx;
    }

    private static int[] indexedParents(int[] parents) {
        int[] pp = new int[parents.length];
        for (int j = 0; j < pp.length; j++) pp[j] = j + 1;
        return pp;
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static Matrix getCov(List<Integer> rows, int[] _rows, int[] cols, Matrix data, ICovarianceMatrix covarianceMatrix) {
        if (rows == null) {
            return covarianceMatrix.getSelection(_rows, cols);
        }

        Matrix cov = new Matrix(_rows.length, cols.length);

        for (int i = 0; i < _rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                double mui = 0.0;
                double muj = 0.0;

                for (int k : rows) {
                    mui += data.get(k, _rows[i]);
                    muj += data.get(k, cols[j]);
                }

                mui /= rows.size() - 1;
                muj /= rows.size() - 1;

                double _cov = 0.0;

                for (int k : rows) {
                    _cov += (data.get(k, _rows[i]) - mui) * (data.get(k, cols[j]) - muj);
                }

                double mean = _cov / (rows.size());
                cov.set(i, j, mean);
            }
        }

        return cov;
    }

    private static List<Integer> getRows(int i, int[] parents, Matrix data, boolean calculateRowSubsets) {
        if (!calculateRowSubsets) {
            return null;
        }

        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < data.getNumRows(); k++) {
            if (Double.isNaN(data.get(k, i))) continue;

            for (int p : parents) {
                if (Double.isNaN(data.get(k, p))) continue K;
            }

            rows.add(k);
        }

        return rows;
    }

    /**
     * Returns the covariance matrix of the regression of the ith variable on its parents and the regression
     * coefficients.
     *
     * @param usePseudoInverse True if the pseudo-inverse should be used instead of the inverse to avoid exceptions.
     */
    public void setUsePseudoInverse(boolean usePseudoInverse) {
        this.usePseudoInverse = usePseudoInverse;
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        if (this.ruleType == RuleType.NANDY) {
            return nandyBic(x, y, z);
        } else {
            return localScore(y, append(z, x)) - localScore(y, z);
        }
    }

    public double nandyBic(int x, int y, int[] z) {
        double sp1 = getStructurePrior(z.length + 1);
        double sp2 = getStructurePrior(z.length);

        Node _x = this.variables.get(x);
        Node _y = this.variables.get(y);
        List<Node> _z = getVariableList(z);

        List<Integer> rows = getRows(x, z);

        if (rows != null) {
            rows.retainAll(Objects.requireNonNull(getRows(y, z)));
        }

        double r = partialCorrelation(_x, _y, _z, rows);

        double c = getPenaltyDiscount();

        return -this.sampleSize * log(1.0 - r * r) - c * log(this.sampleSize)
                - 2.0 * (sp1 - sp2);
    }

    /**
     * Returns the score for the given node and its parents.
     *
     * @param i       The index of the node.
     * @param parents The indices of the node's parents.
     * @return The score, or NaN if the score cannot be calculated.
     */
    public double localScore(int i, int... parents) {
        int k = parents.length;
        double lik;

        Arrays.sort(parents);

        try {
            double varey = SemBicScore.getVarRy(i, parents, this.data, this.covariances, this.calculateRowSubsets,
                    usePseudoInverse);
            lik = -(double) (this.sampleSize / 2.0) * log(varey);
        } catch (SingularMatrixException e) {
            System.out.println("Singularity encountered when scoring " +
                    LogUtilsSearch.getScoreFact(i, parents, variables));
            return Double.NaN;
        }


        double c = getPenaltyDiscount();

        if (this.ruleType == RuleType.CHICKERING || this.ruleType == RuleType.NANDY) {

            // Standard BIC, with penalty discount and structure prior.
            double _score = lik - c * (k / 2.0) * logN - getStructurePrior(k);

            if (Double.isNaN(_score) || Double.isInfinite(_score)) {
                return Double.NaN;
            } else {
                return _score;
            }
        } else {
            throw new IllegalStateException("That rule type is not implemented: " + this.ruleType);
        }
    }

    /**
     * Returns the multiplier on the penalty term for this score.
     *
     * @return The multiplier on the penalty term for this score.
     */
    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    /**
     * Sets the multiplier on the penalty term for this score.
     *
     * @param penaltyDiscount The multiplier on the penalty term for this score.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Returns the structure prior for this score.
     *
     * @return The structure prior for this score.
     */
    public double getStructurePrior() {
        return this.structurePrior;
    }

    /**
     * Sets the structure prior for this score.
     *
     * @param structurePrior The structure prior for this score.
     */
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    /**
     * Returns the covariance matrix.
     *
     * @return The covariance matrix.
     */
    public ICovarianceMatrix getCovariances() {
        return this.covariances;
    }

    private void setCovariances(ICovarianceMatrix covariances) {
        this.covariances = covariances;
        this.matrix = this.covariances.getMatrix();

        this.dataModel = covariances;

    }

    /**
     * Returns the sample size.
     *
     * @return The sample size.
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Returns true if the given bump is an effect edge.
     *
     * @param bump The bump.
     * @return True if the given bump is an effect edge.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Returns the data model.
     *
     * @return The data model.
     */
    public DataModel getDataModel() {
        return this.dataModel;
    }

    /**
     * Returns true if verbose output should be sent to out.
     *
     * @return True if verbose output should be sent to out.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be sent to out.
     *
     * @param verbose True if verbose output should be sent to out.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the variables of the covariance matrix.
     *
     * @return The variables of the covariance matrix.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    /**
     * Sets the variables of the covariance matrix.
     *
     * @param variables The variables of the covariance matrix.
     */
    public void setVariables(List<Node> variables) {
        if (this.covariances != null) {
            this.covariances.setVariables(variables);
        }

        this.variables = variables;
    }

    /**
     * Returns the maximum degree of the score.
     *
     * @return The maximum degree of the score.
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(log(this.sampleSize));
    }

    /**
     * Returns true is the variables in z determine the variable y.
     *
     * @param z The set of nodes.
     * @param y The node.
     * @return True is the variables in z determine the variable y.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = this.variables.indexOf(y);

        int[] k = new int[z.size()];

        for (int t = 0; t < z.size(); t++) {
            k[t] = this.variables.indexOf(z.get(t));
        }

        try {
            localScore(i, k);
        } catch (RuntimeException e) {
            TetradLogger.getInstance().forceLogMessage(e.getMessage());
            return true;
        }

        return false;
    }

    /**
     * Returns the data model.
     *
     * @return The data model.
     */
    public DataModel getData() {
        return this.dataModel;
    }

    /**
     * Sets the rule type to use.
     *
     * @param ruleType The rule type to use.
     * @see RuleType
     */
    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    /**
     * Returns a SEM BIC score for the given subset of variables.
     *
     * @param subset The subset of variables.
     * @return A SEM BIC score for the given subset of variables.
     */
    public SemBicScore subset(List<Node> subset) {
        int[] cols = new int[subset.size()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = variables.indexOf(subset.get(i));
        }
        ICovarianceMatrix cov = getCovariances().getSubmatrix(cols);
        return new SemBicScore(cov);
    }

    /**
     * Returns a string representation of this score.
     *
     * @return A string representation of this score.
     */
    public String toString() {
        return "SEM BIC Score";
    }

    private double getStructurePrior(int parents) {
        if (abs(getStructurePrior()) <= 0) {
            return 0;
        } else {
            double p = (getStructurePrior()) / (this.variables.size());
            return -((parents) * log(p) + (this.variables.size() - (parents)) * log(1.0 - p));
        }
    }

    private List<Node> getVariableList(int[] indices) {
        List<Node> variables = new ArrayList<>();
        for (int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }

    private Map<Node, Integer> indexMap(List<Node> variables) {
        Map<Node, Integer> indexMap = new HashMap<>();

        for (int i = 0; variables.size() > i; i++) {
            indexMap.put(variables.get(i), i);
        }

        return indexMap;
    }

    private List<Integer> getRows(int i, int[] parents) {
        if (this.dataModel == null) {
            return null;
        }

        List<Integer> rows = new ArrayList<>();

        DataSet dataSet = (DataSet) this.dataModel;

        K:
        for (int k = 0; k < dataSet.getNumRows(); k++) {
            if (Double.isNaN(dataSet.getDouble(k, i))) continue;

            for (int p : parents) {
                if (Double.isNaN(dataSet.getDouble(k, p))) continue K;
            }

            rows.add(k);
        }

        return rows;
    }

    private double partialCorrelation(Node x, Node y, List<Node> z, List<Integer> rows) {
        try {
            return StatUtils.partialCorrelation(convertCovToCorr(getCov(rows, indices(x, y, z))));
        } catch (Exception e) {
            return NaN;
        }
    }

    private int[] indices(Node x, Node y, List<Node> z) {
        int[] indices = new int[z.size() + 2];
        indices[0] = this.indexMap.get(x);
        indices[1] = this.indexMap.get(y);
        for (int i = 0; i < z.size(); i++) indices[i + 2] = this.indexMap.get(z.get(i));
        return indices;
    }

    private Matrix getCov(List<Integer> rows, int[] cols) {
        if (this.dataModel == null) {
            return this.matrix.getSelection(cols, cols);
        }

        DataSet dataSet = (DataSet) this.dataModel;

        Matrix cov = new Matrix(cols.length, cols.length);

        for (int i = 0; i < cols.length; i++) {
            for (int j = i + 1; j < cols.length; j++) {
                double mui = 0.0;
                double muj = 0.0;

                for (int k : rows) {
                    mui += dataSet.getDouble(k, cols[i]);
                    muj += dataSet.getDouble(k, cols[j]);
                }

                mui /= rows.size() - 1;
                muj /= rows.size() - 1;

                double _cov = 0.0;

                for (int k : rows) {
                    _cov += (dataSet.getDouble(k, cols[i]) - mui) * (dataSet.getDouble(k, cols[j]) - muj);
                }

                double mean = _cov / (rows.size());
                cov.set(i, j, mean);
                cov.set(j, i, mean);
            }
        }

        for (int i = 0; i < cols.length; i++) {
            double mui = 0.0;

            for (int k : rows) {
                mui += dataSet.getDouble(k, cols[i]);
            }

            mui /= rows.size();

            double _cov = 0.0;

            for (int k : rows) {
                _cov += (dataSet.getDouble(k, cols[i]) - mui) * (dataSet.getDouble(k, cols[i]) - mui);
            }

            double mean = _cov / (rows.size());
            cov.set(i, i, mean);
        }

        return cov;
    }

    private ICovarianceMatrix getCovarianceMatrix(DataSet dataSet, boolean precomputeCovariances) {
        return SimpleDataLoader.getCovarianceMatrix(dataSet, precomputeCovariances);
    }

    /**
     * Gives two options for calculating the BIC score, one describe by Chickering and the other due to Nandy et al.
     */
    public enum RuleType {CHICKERING, NANDY}

    public record CovAndCoefs(Matrix cov, Matrix b) {
    }
}


