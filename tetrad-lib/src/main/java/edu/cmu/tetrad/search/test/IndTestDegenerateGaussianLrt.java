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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Double.NaN;
import static org.apache.commons.math3.util.FastMath.*;

/**
 * Implements a degenerate Gaussian score as a LRT. The reference is here:
 * <p>
 * Andrews, B., Ramsey, J., &amp; Cooper, G. F. (2019, July). Learning high-dimensional directed acyclic graphs with
 * mixed data-types. In The 2019 ACM SIGKDD Workshop on Causal Discovery (pp. 4-21). PMLR.
 *
 * @author Bryan Andrews
 * @version $Id: $Id
 */
public class IndTestDegenerateGaussianLrt implements IndependenceTest, RowsSettable {

    /**
     * A constant.
     */
    private static final double L2PE = log(2.0 * PI * E);
    /**
     * The data set.
     */
    private final BoxDataSet ddata;
    private final Matrix dcov;
    /**
     * The data set.
     */
    private final double[][] _ddata;
    /**
     * A hash of nodes to indices.
     */
    private final Map<Node, Integer> nodeHash;
    /**
     * The data set.
     */
    private final DataSet dataSet;
    /**
     * The mixed variables of the original dataset.
     */
    private final List<Node> variables;
    /**
     * The embedding map.
     */
    private final Map<Integer, List<Integer>> embedding;
    /**
     * A cache of results for independence facts.
     */
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    /**
     * The alpha level.
     */
    private double alpha = 0.001;
    /**
     * The p value.
     */
    private double pValue = NaN;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The rows used in the test.
     */
    private List<Integer> rows = null;

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param dataSet The data being analyzed.
     */
    public IndTestDegenerateGaussianLrt(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        // The number of instances.
        int n = dataSet.getNumRows();
        this.embedding = new HashMap<>();

        List<Node> A = new ArrayList<>();
        List<double[]> B = new ArrayList<>();

        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int j = 0; j < this.variables.size(); j++) {
            nodesHash.put(this.variables.get(j), j);
        }

        this.nodeHash = nodesHash;

        int index = 0;

        int i = 0;
        int i_ = 0;
        while (i_ < this.variables.size()) {

            Node v = this.variables.get(i_);

            if (v instanceof DiscreteVariable) {

                Map<List<Integer>, Integer> keys = new HashMap<>();
                Map<Integer, List<Integer>> keysReverse = new HashMap<>();
                for (int j = 0; j < n; j++) {
                    List<Integer> key = new ArrayList<>();
                    key.add(this.dataSet.getInt(j, i_));
                    if (!keys.containsKey(key)) {
                        keys.put(key, i);
                        keysReverse.put(i, key);
                        Node v_ = new ContinuousVariable("V__" + ++index);
                        A.add(v_);
                        B.add(new double[n]);
                        i++;
                    }
                    B.get(keys.get(key))[j] = 1;
                }

                /*
                 * Remove a degenerate dimension.
                 */
                i--;
                keys.remove(keysReverse.get(i));
                A.remove(i);
                B.remove(i);

                this.embedding.put(i_, new ArrayList<>(keys.values()));

            } else {

                A.add(v);
                double[] b = new double[n];
                for (int j = 0; j < n; j++) {
                    b[j] = this.dataSet.getDouble(j, i_);
                }

                B.add(b);
                List<Integer> index2 = new ArrayList<>();
                index2.add(i);
                this.embedding.put(i_, index2);
                i++;

            }
            i_++;
        }

        double[][] B_ = new double[n][B.size()];
        for (int j = 0; j < B.size(); j++) {
            for (int k = 0; k < n; k++) {
                B_[k][j] = B.get(j)[k];
            }
        }

        // The continuous variables of the post-embedding dataset.
        RealMatrix D = MatrixUtils.createRealMatrix(B_);
        this.ddata = new BoxDataSet(new DoubleDataBox(D.getData()), A);
        this.dcov = new CovarianceMatrix(ddata).getMatrix();
        this._ddata = ddata.getDoubleData().toArray();
    }

    /**
     * Subsets the variables used in the independence test.
     *
     * @param vars The sublist of variables.
     * @return The IndependenceTest object with subset of variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Returns an independence result specifying whether x _||_ y | Z and what its p-values are.
     *
     * @param x  a {@link edu.cmu.tetrad.graph.Node} object
     * @param y  a {@link edu.cmu.tetrad.graph.Node} object
     * @param _z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        if (facts.containsKey(new IndependenceFact(x, y, _z))) {
            return facts.get(new IndependenceFact(x, y, _z));
        }

        List<Node> allNodes = new ArrayList<>();
        allNodes.add(x);
        allNodes.add(y);
        allNodes.addAll(_z);

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        List<Integer> rows = getRows(allNodes, this.nodeHash);

        if (rows.isEmpty()) return new IndependenceResult(new IndependenceFact(x, y, _z),
                true, NaN, NaN);

        int _x = this.nodeHash.get(x);
        int _y = this.nodeHash.get(y);

        int[] list0 = new int[z.size()];
        int[] list1 = new int[z.size() + 1];

        list1[0] = _x;

        for (int i = 0; i < z.size(); i++) {
            int __z = this.nodeHash.get(z.get(i));
            list0[i] = __z;
            list1[i + 1] = __z;
        }

        Ret ret0 = getlldof(rows, _y, list0);
        Ret ret1 = getlldof(rows, _y, list1);

        double lik_diff = ret0.getLik() - ret1.getLik();
        double dof_diff = ret1.getDof() - ret0.getDof();

        if (dof_diff <= 0) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);
        if (this.alpha == 0) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);
        if (this.alpha == 1) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);
        if (lik_diff == Double.POSITIVE_INFINITY) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);

        double pValue;

        if (Double.isNaN(lik_diff)) {
            throw new RuntimeException("Undefined likelihood encountered for test: " + LogUtilsSearch.independenceFact(x, y, _z));
        } else {
            try {
                pValue = 1.0 - new ChiSquaredDistribution(dof_diff).cumulativeProbability(-2 * lik_diff);
            } catch (Exception e) {
                throw new RuntimeException("Exception when trying to determine " + LogUtilsSearch.independenceFact(x, y, _z), e);
            }
        }

        this.pValue = pValue;

        boolean independent = this.pValue > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, _z),
                independent, pValue, alpha - pValue);
        facts.put(new IndependenceFact(x, y, _z), result);
        return result;
    }

    /**
     * Returns the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for this test.
     *
     * @return This p-value.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Returns the list of searchVariables over which this independence checker is capable of determinining independence
     * relations.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return this level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns the dataset being analyzed.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Degenerate Gaussian, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns true iff verbose output should be printed.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Calculates the sample log likelihood
     */
    private Ret getlldof(List<Integer> rows, int i, int... parents) {
        int N = rows.size();

        List<Integer> B = new ArrayList<>();
        List<Integer> A = new ArrayList<>(this.embedding.get(i));
        for (int i_ : parents) {
            B.addAll(this.embedding.get(i_));
        }

        int[] A_ = new int[A.size() + B.size()];
        int[] B_ = new int[B.size()];
        for (int i_ = 0; i_ < A.size(); i_++) {
            A_[i_] = A.get(i_);
        }
        for (int i_ = 0; i_ < B.size(); i_++) {
            A_[A.size() + i_] = B.get(i_);
            B_[i_] = B.get(i_);
        }

        double dof = (A_.length * (A_.length + 1) - B_.length * (B_.length + 1)) / 2.0;
        double ldetA = log(abs(SemBicScore.getCov(rows, A_, A_, ddata, dcov).det()));
        double ldetB = log(abs(SemBicScore.getCov(rows, B_, B_, ddata, dcov).det()));

        double lik = N * (ldetB - ldetA) + IndTestDegenerateGaussianLrt.L2PE * (B_.length - A_.length);

        return new Ret(lik, dof);
    }

    /**
     * Returns a list of row indices that satisfy the given conditions.
     *
     * @param allVars   A list of nodes representing the variables to be checked.
     * @param nodesHash A map that associates each node with its corresponding index.
     * @return A list of integers representing the row indices that satisfy the conditions.
     */
    private List<Integer> getRows(List<Node> allVars, Map<Node, Integer> nodesHash) {
        if (this.rows != null) {
            return this.rows;
        }

        List<Integer> rows = new ArrayList<>();

        for (int k = 0; k < this.dataSet.getNumRows(); k++) {
            rows.add(k);
        }

        return rows;
    }

    /**
     * Calculates the covariance matrix for the given rows and columns.
     *
     * @param rows The list of row indices to include in the covariance calculation.
     * @param cols The array of column indices to include in the covariance calculation.
     * @return The covariance matrix for the selected rows and columns.
     */
    private Matrix getCov(List<Integer> rows, int[] cols) {
        Matrix cov = new Matrix(cols.length, cols.length);

        for (int i = 0; i < cols.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                double mui = 0.0;
                double muj = 0.0;

                for (int k : rows) {
                    mui += this._ddata[k][cols[i]];
                    muj += this._ddata[k][cols[j]];
                }

                mui /= rows.size() - 1;
                muj /= rows.size() - 1;

                double _cov = 0.0;

                for (int k : rows) {
                    _cov += (this._ddata[k][cols[i]] - mui) * (this._ddata[k][cols[j]] - muj);
//                    _cov += (ddata.getDouble(k, cols[i]) - mui) * (ddata.getDouble(k, cols[j]) - muj);
                }

                double mean = _cov / (rows.size());
                cov.set(i, j, mean);
            }
        }

        return cov;
    }

    /**
     * Returns the rows used in the test.
     *
     * @return The rows used in the test.
     */
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Allows the user to set which rows are used in the test. Otherwise, all rows are used, except those with missing
     * values.
     */
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) == null) throw new NullPointerException("Row " + i + " is null.");
                if (rows.get(i) < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            }

            this.rows = rows;
        }
    }

    /**
     * Stores a return value for a likelihood--i.e., a likelihood value and the degrees of freedom for it.
     */
    public static class Ret {

        /**
         * The likelihood.
         */
        private final double lik;

        /**
         * The degrees of freedom.
         */
        private final double dof;

        /**
         * Constructs a return value.
         *
         * @param lik The likelihood.
         * @param dof The degrees of freedom.
         */
        private Ret(double lik, double dof) {
            this.lik = lik;
            this.dof = dof;
        }

        /**
         * Returns the likelihood.
         *
         * @return This likelihood.
         */
        public double getLik() {
            return this.lik;
        }

        /**
         * Returns the degrees of freedom.
         *
         * @return These degrees of freedom.
         */
        public double getDof() {
            return this.dof;
        }

        /**
         * Returns a string representation of this object.
         *
         * @return This string.
         */
        public String toString() {
            return "lik = " + this.lik + " dof = " + this.dof;
        }
    }
}
