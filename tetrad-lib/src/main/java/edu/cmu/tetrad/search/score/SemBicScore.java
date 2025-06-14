/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.IntStream;

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
 * Gaussian data, a detriment that can be overcome to an extent by using a permutation algorithm such as SP, GRaSP, or
 * BOSS.
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see edu.cmu.tetrad.search.Fges
 * @see edu.cmu.tetrad.search.Sp
 * @see edu.cmu.tetrad.search.Grasp
 * @see edu.cmu.tetrad.search.Boss
 */
public class SemBicScore implements Score {

    /**
     * The sample size of the covariance matrix.
     */
    private final int sampleSize;
    /**
     * A map from variable names to their indices.
     */
    private final Map<Node, Integer> indexMap;
    /**
     * The log of the sample size.
     */
    private final double logN;
    /**
     * True if row subsets should be calculated.
     */
    private boolean calculateRowSubsets;
    /**
     * The dataset.
     */
    private DataModel dataModel;
    /**
     * .. as matrix
     */
    private Matrix data;
    /**
     * The correlation matrix.
     */
    private ICovarianceMatrix covariances;
    /**
     * The variables of the covariance matrix.
     */
    private List<Node> variables;
    /**
     * True, if verbose output should be sent to out.
     */
    private boolean verbose;
    /**
     * The penalty penaltyDiscount, 1 for standard BIC.
     */
    private double penaltyDiscount;
    /**
     * The structure prior, 0 for standard BIC.
     */
    private double structurePrior;
    /**
     * The covariance matrix.
     */
    private Matrix matrix;
    /**
     * The rule type to use.
     */
    private RuleType ruleType = RuleType.CHICKERING;
    /**
     * Singularity lambda.
     */
    private double lambda = 0.0;

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
        penaltyDiscount = 1.0;
    }

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param covariances     The covariance matrix.
     * @param penaltyDiscount The penalty discount of the score.
     */
    public SemBicScore(ICovarianceMatrix covariances, double penaltyDiscount) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        if (penaltyDiscount <= 0) {
            throw new IllegalArgumentException("Penalty discount must be positive");
        }

        setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
        this.indexMap = indexMap(this.variables);
        this.logN = log(sampleSize);
        this.penaltyDiscount = penaltyDiscount;
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
        this.penaltyDiscount = 1.0;
    }

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param dataSet               The dataset.
     * @param penaltyDiscount       The penalty discount of th e score.
     * @param precomputeCovariances Whether the covariances should be precomputed or computed on the fly. True if
     */
    public SemBicScore(DataSet dataSet, double penaltyDiscount, boolean precomputeCovariances) {

        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (penaltyDiscount <= 0) {
            throw new IllegalArgumentException("Penalty discount must be positive");
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
            this.penaltyDiscount = penaltyDiscount;
            return;
        }

        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        this.indexMap = indexMap(this.variables);
        this.calculateRowSubsets = true;
        this.logN = log(sampleSize);
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Returns the variance of the residual of the regression of the ith variable on its parents.
     *
     * @param i                   The index of the variable.
     * @param parents             The indices of the parents.
     * @param covariances         The covariance matrix.
     * @param calculateRowSubsets True if row subsets should be calculated.
     * @param data                a {@link edu.cmu.tetrad.util.Matrix} object
     * @param lambda              Singularity lambda.
     * @return The variance of the residual of the regression of the ith variable on its parents.
     * @throws org.apache.commons.math3.linear.SingularMatrixException if any.
     */
    public static double getResidualVariance(int i, int[] parents, Matrix data, ICovarianceMatrix covariances, boolean calculateRowSubsets,
                                             double lambda) throws SingularMatrixException {
        CovAndCoefs covAndcoefs = getCovAndCoefs(i, parents, data, covariances, calculateRowSubsets, lambda);
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
     * @param lambda              Singularity lambda.
     * @return The covariance matrix of the regression of the ith variable on its parents and the regression
     * coefficients.
     */
    @NotNull
    public static SemBicScore.CovAndCoefs getCovAndCoefs(int i, int[] parents, Matrix data, ICovarianceMatrix covariances,
                                                         boolean calculateRowSubsets, double lambda) {
        List<Integer> rows = SemBicScore.getRows(data, calculateRowSubsets);
        return getCovAndCoefs(i, parents, data, covariances, lambda, rows);
    }

    /**
     * Returns the covariance matrix of the regression of the ith variable on its parents and the regression
     *
     * @param i           The index of the variable.
     * @param parents     The indices of the parents.
     * @param data        The data matrix.
     * @param covariances The covariance matrix.
     * @param lambda      Singularity lambda.
     * @param rows        The rows to use.
     * @return The covariance matrix of the regression of the ith variable on its parents and the regression
     */
    @NotNull
    public static CovAndCoefs getCovAndCoefs(int i, int[] parents, Matrix data, ICovarianceMatrix covariances,
                                             double lambda, List<Integer> rows) {
        int[] all = SemBicScore.concat(i, parents);
        Matrix cov = SemBicScore.getCov(rows, all, all, data, covariances);
        int[] pp = SemBicScore.indexedParents(parents);

        Matrix covxx = cov.view(pp, pp).mat();
        Matrix covxy = cov.view(pp, new int[]{0}).mat();
        Matrix b = covxx.chooseInverse(lambda).times(covxy);
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

    private static Matrix getCov(List<Integer> rowsInData, int[] rows, int[] cols, Matrix data, ICovarianceMatrix covarianceMatrix) {
        if (rowsInData == null) {
            return covarianceMatrix.getSelection(rows, cols);
        }

        Matrix cov = new Matrix(rows.length, cols.length);

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                double mui = 0.0;
                double muj = 0.0;
                double sampleSize = 0;//data.getNumRows();

                K1:
                for (int k : rowsInData) {
                    for (int c : cols) {
                        if (Double.isNaN(data.get(k, c))) {
                            continue K1;
                        }
                    }

                    for (int c : rows) {
                        if (Double.isNaN(data.get(k, c))) {
                            continue K1;
                        }
                    }

                    mui += data.get(k, cols[i]);
                    muj += data.get(k, cols[j]);
                    sampleSize++;
                }

                mui /= sampleSize;
                muj /= sampleSize;

                double _cov = 0.0;

                K2:
                for (int k : rowsInData) {
                    for (int c : cols) {
                        if (Double.isNaN(data.get(k, c))) {
                            continue K2;
                        }
                    }

                    for (int c : rows) {
                        if (Double.isNaN(data.get(k, c))) {
                            continue K2;
                        }
                    }

                    _cov += (data.get(k, cols[i]) - mui) * (data.get(k, cols[j]) - muj);
                }

                double mean = _cov / (sampleSize - 1);
                cov.set(i, j, mean);
                cov.set(j, i, mean);
            }
        }

        return cov;
    }

    private static List<Integer> getRows(Matrix data, boolean calculateRowSubsets) {
        if (!calculateRowSubsets) {
            return null;
        }

        List<Integer> rows = new ArrayList<>();

        for (int k = 0; k < data.getNumRows(); k++) {
            rows.add(k);
        }

        return rows;
    }

    /**
     * Computes the covariance matrix for the given subset of rows and columns in the provided data set.
     *
     * @param rows    A list of the row indices to consider for computing the covariance.
     * @param cols    An array of the column indices for which to compute the covariance matrix.
     * @param all     An array of all column indices to check for NaN values.
     * @param dataSet The dataset containing the values to be used in computation. If null, the method returns a
     *                selection from the provided covariance matrix.
     * @param cov     If dataSet is null, this covariance matrix is used to return the selected covariances.
     * @return A Matrix representing the covariance computed from the given rows and columns of the dataset or a
     * selection from the provided covariance matrix.
     * @throws IllegalArgumentException If both dataSet and cov are null.
     */
    public static Matrix getCov(List<Integer> rows, int[] cols, int[] all, DataSet dataSet, Matrix cov) {
        if (dataSet == null && cov != null) {
            return cov.view(cols, cols).mat();
        } else if (dataSet != null) {

            Matrix _cov = calcCovWithTestwiseDeletion(rows, cols, all, dataSet);

            return _cov;
        } else {
            throw new IllegalArgumentException("No data set or covariance matrix provided.");
        }
    }

//    private static @NotNull Matrix calcCovWithTestwiseDeletion(List<Integer> rows, int[] cols, int[] all, DataSet dataSet) {
//        Matrix _cov = new Matrix(cols.length, cols.length);
//
//        for (int i = 0; i < cols.length; i++) {
//            for (int j = i; j < cols.length; j++) {
//                double mui = 0.0;
//                double muj = 0.0;
//
//                int sampleSize = rows.size();
//
//                K1:
//                for (int k : rows) {
//                    for (int c : all) {
//                        if (Double.isNaN(dataSet.getDouble(k, c))) {
//                            continue K1;
//                        }
//                    }
//
//                    mui += dataSet.getDouble(k, cols[i]);
//                    muj += dataSet.getDouble(k, cols[j]);
//                }
//
//                mui /= sampleSize - 1;
//                muj /= sampleSize - 1;
//
//                double __cov = 0.0;
//
//                K2:
//                for (int k : rows) {
//                    for (int c : all) {
//                        if (Double.isNaN(dataSet.getDouble(k, c))) {
//                            continue K2;
//                        }
//                    }
//
//                    double v = dataSet.getDouble(k, cols[i]);
//                    double w = dataSet.getDouble(k, cols[j]);
//
//                    __cov += (v - mui) * (w - muj);
//                }
//
//                __cov /= sampleSize;
//                _cov.set(i, j, __cov);
//                _cov.set(j, i, __cov);
//            }
//        }
//        return _cov;
//    }


    private static @NotNull Matrix calcCovWithTestwiseDeletion(List<Integer> rows, int[] cols, int[] all, DataSet dataSet) {
        Matrix _cov = new Matrix(cols.length, cols.length);

        // Precompute valid rows
        List<Integer> validRows = new ArrayList<>();
        for (int k : rows) {
            boolean isValid = true;
            for (int c : all) {
                if (Double.isNaN(dataSet.getDouble(k, c))) {
                    isValid = false;
                    break;
                }
            }
            if (isValid) {
                validRows.add(k);
            }
        }

        // Parallel computation of covariance matrix
        IntStream.range(0, cols.length).parallel().forEach(i -> {
            for (int j = i; j < cols.length; j++) {
                double mui = 0.0, muj = 0.0, __cov = 0.0;
                int validCount = 0;

                for (int k : validRows) {
                    double vi = dataSet.getDouble(k, cols[i]);
                    double vj = dataSet.getDouble(k, cols[j]);

                    mui += vi;
                    muj += vj;

                    __cov += vi * vj;
                    validCount++;
                }

                if (validCount > 1) {
                    mui /= validCount;
                    muj /= validCount;
                    __cov = (__cov - validCount * mui * muj) / validCount;
                } else {
                    __cov = 0.0; // Handle cases with no valid rows
                }

                synchronized (_cov) {
                    _cov.set(i, j, __cov);
                    _cov.set(j, i, __cov);
                }
            }
        });

        return _cov;
    }

    /**
     * Returns the covariance matrix of the regression of the ith variable on its parents and the regression
     * coefficients.
     *
     * @param lambda Singularity lambda.
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        if (this.ruleType == RuleType.NANDY) {
            return nandyBic(x, y, z);
        } else {
            return localScore(y, append(z, x)) - localScore(y, z);
        }
    }

    /**
     * Calculates the BIC score of a partial correlation based on the specified variables.
     *
     * @param x the index of the first variable.
     * @param y the index of the second variable.
     * @param z an array of indices representing conditioning variables.
     * @return the BIC score as a double.
     */
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

        return -this.sampleSize * log(1.0 - r * r) - c * log(this.sampleSize) - 2.0 * (sp1 - sp2);
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
            lik = getLikelihood(i, parents);
        } catch (SingularMatrixException e) {
            System.out.println("Singularity encountered when scoring " + LogUtilsSearch.getScoreFact(i, parents, variables));
            return Double.NaN;
        }


        double c = getPenaltyDiscount();

        if (this.ruleType == RuleType.CHICKERING || this.ruleType == RuleType.NANDY) {

            // Standard BIC, with penalty discount and structure prior.
            double _score = 2 * lik - c * (k) * logN - getStructurePrior(k);

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
     * Computes the likelihood and degrees of freedom (dof) for a given variable and its parent variables. The
     * likelihood is calculated based on the provided variable index and parent indices. In case of a singular matrix
     * during likelihood computation, it returns a result with NaN likelihood and -1 for degrees of freedom.
     *
     * @param i       The index of the variable for which the likelihood is calculated.
     * @param parents The indices of the parent variables of the variable `i`.
     * @return A {@code LikelihoodResult} object containing the likelihood value, the degrees of freedom (dof), and
     * other related penalty and sample size information.
     */
    public LikelihoodResult getLikelihoodAndDof(int i, int... parents) {
        int k = parents.length;
        double lik;

        Arrays.sort(parents);

        try {
            lik = getLikelihood(i, parents);
        } catch (SingularMatrixException e) {
            System.out.println("Singularity encountered when scoring " + LogUtilsSearch.getScoreFact(i, parents, variables));
            return new LikelihoodResult(Double.NaN, -1, penaltyDiscount, sampleSize);
        }

        return new LikelihoodResult(lik, k, penaltyDiscount, sampleSize);
    }

    /**
     * Computes the Akaike Information Criterion (AIC) score for the given variable and its parent variables in a
     * probabilistic graphical model such as a Bayesian network.
     *
     * @param i       The index of the variable for which the AIC score is being computed.
     * @param parents The indices of the parent variables of the variable specified by index i.
     * @return The computed AIC score as a double value. Returns Double.NaN if a singular matrix is encountered or the
     * score is undefined. Throws an exception if the rule type is unsupported.
     */
    public double getAic(int i, int... parents) {
        int k = parents.length;
        double lik;

        Arrays.sort(parents);

        try {
            lik = getLikelihood(i, parents);
        } catch (SingularMatrixException e) {
            System.out.println("Singularity encountered when scoring " + LogUtilsSearch.getScoreFact(i, parents, variables));
            return Double.NaN;
        }


        double c = getPenaltyDiscount();

        if (this.ruleType == RuleType.CHICKERING || this.ruleType == RuleType.NANDY) {

            // AIC score
            double _score = 2 * lik - 2 * k;

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
     * Calculates the likelihood for the given variable and its parent variables based on the provided data and
     * covariance matrices. This method computes the variance for the residuals and uses it to determine the likelihood
     * score.
     *
     * @param i       The index of the variable for which the likelihood is being calculated.
     * @param parents An array of indices representing the parent variables of the variable at index i.
     * @return The negative log-likelihood score for the specified variable and its parent variables.
     * @throws SingularMatrixException if the covariance matrix is singular and cannot be inverted.
     */
    public double getLikelihood(int i, int[] parents) throws SingularMatrixException {
        double sigmaSquared = SemBicScore.getResidualVariance(i, parents, this.data, this.covariances, this.calculateRowSubsets, lambda);
        return -0.5 * this.sampleSize * (Math.log(2 * Math.PI * sigmaSquared) + 1);
//        return -(double) (this.sampleSize / 2.0) * log(sigmaSquared);
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

        if (this.dataModel == null) {
            this.matrix = this.covariances.getMatrix();
        }

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
     * {@inheritDoc}
     * <p>
     * Returns true if the given bump is an effect edge.
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
     * @return True, if verbose output should be sent to out.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be sent to out.
     *
     * @param verbose True, if verbose output should be sent to out.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the variables of the covariance matrix.
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
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(log(this.sampleSize));
    }

    /**
     * Returns true is the variables in z determine the variable y.
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
            TetradLogger.getInstance().log(e.getMessage());
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
            int[] all = new int[z.size() + 2];
            all[0] = this.indexMap.get(x);
            all[1] = this.indexMap.get(y);
            for (int i = 0; i < z.size(); i++) all[i + 2] = this.indexMap.get(z.get(i));

            return StatUtils.partialCorrelation(convertCovToCorr(getCov(rows, indices(x, y, z), all, (DataSet) this.dataModel, this.matrix)), lambda);
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

    private ICovarianceMatrix getCovarianceMatrix(DataSet dataSet, boolean precomputeCovariances) {
        return SimpleDataLoader.getCovarianceMatrix(dataSet, precomputeCovariances);
    }

    /**
     * Gives two options for calculating the BIC score, one describe by Chickering and the other due to Nandy et al.
     */
    public enum RuleType {

        /**
         * The standard linear, Gaussian BIC score.
         */
        CHICKERING,

        /**
         * The formulation of the standard BIC score given in Nandy et al.
         */
        NANDY
    }

    /**
     * A record that encapsulates the result of a likelihood computation. This record stores the likelihood value,
     * degrees of freedom, penalty discount, and the sample size associated with the computation.
     *
     * @param lik             The computed likelihood value.
     * @param dof             The degrees of freedom used in the computation.
     * @param penaltyDiscount The penalty discount applied to the computation.
     * @param sampleSize      The size of the sample used in the likelihood computation.
     */
    public record LikelihoodResult(double lik, int dof, double penaltyDiscount, int sampleSize) {
    }

    /**
     * Represents a covariance matrix and regression coefficients.
     *
     * @param cov The covariance matrix.
     * @param b   The regression coefficients.
     */
    public record CovAndCoefs(Matrix cov, Matrix b) {
    }
}


