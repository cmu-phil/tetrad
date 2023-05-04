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
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.apache.commons.math3.util.FastMath.log;

/**
 * <p></->Implements a degenerate Gaussian BIC score for FGES.</p>
 * <a href="http://proceedings.mlr.press/v104/andrews19a/andrews19a.pdf">...</a>
 *
 * @author Bryan Andrews
 */
public class DegenerateGaussianScore implements Score {

    private final BoxDataSet ddata;
    private final DataSet dataSet;

    // The mixed variables of the original dataset.
    private final List<Node> variables;

    // The penalty discount.
    private double penaltyDiscount = 1.0;

    // The structure prior.
    private double structurePrior;

    // The embedding map.
    private final Map<Integer, List<Integer>> embedding;

    // A constant.
    private static final double L2PE = log(2.0 * FastMath.PI * FastMath.E);

    private final Map<Node, Integer> nodesHash;

    /**
     * Constructs the score using a covariance matrix.
     */
    public DegenerateGaussianScore(DataSet dataSet) {
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
        RealMatrix D = new BlockRealMatrix(B_);
        this.ddata = new BoxDataSet(new DoubleDataBox(D.getData()), A);
        this.nodesHash = new ConcurrentSkipListMap<>();

        List<Node> variables = dataSet.getVariables();

        for (int j = 0; j < variables.size(); j++) {
            this.nodesHash.put(variables.get(j), j);
        }

    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model.
     *
     * @param i       The child indes.
     * @param parents The indices of the parents.
     */
    public double localScore(int i, int... parents) {

        List<Integer> rows = getRows(i, parents);
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

        int dof = (A_.length * (A_.length + 1) - B_.length * (B_.length + 1)) / 2;
        double ldetA = log(getCov(rows, A_).det());
        double ldetB = log(getCov(rows, B_).det());

        double lik = N * (ldetB - ldetA + DegenerateGaussianScore.L2PE * (B_.length - A_.length));
        double score = 2 * lik + 2 * calculateStructurePrior(parents.length) - dof * this.penaltyDiscount * log(N);

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    /**
     * Returns localScore(y | z, x) - localScore(y, z).
     *
     * @param x Node 1.
     * @param y Node 2.
     * @param z The conditioning variables
     * @return This score difference.
     */
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Returns the sample size for the data for this score.
     *
     * @return This sample size.
     */
    public int getSampleSize() {
        return this.dataSet.getNumRows();
    }

    /**
     * Returns a decision whether a given bump counts as an effect edge
     * for this score.
     *
     * @param bump The bump.
     * @return True if it counts as an effect edge.
     * @see Fges
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Returns the variables for this score.
     *
     * @return This list.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns an estimate of the max degree needed for certain algorithms.
     *
     * @return This estimate
     * @see Fges
     * @see MagSemBicScore
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(log(this.dataSet.getNumRows()));
    }

    /**
     * This score does not implement a method to determing whether a given set of parents determine
     * a given child, so an exception is thrown.
     *
     * @throws UnsupportedOperationException Since this method is not implemented.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("The 'determines' methods is not implemented for this score.");
    }

    /**
     * Sets the penalty discount for this score, which is a multiplier on the BIC penalty term.
     *
     * @param penaltyDiscount This penalty.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Sets the structure prior for this score.
     *
     * @param structurePrior This prior.
     */
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    /**
     * Returns a string representation of this score.
     *
     * @return This string.
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Degenerate Gaussian Score Penalty " + nf.format(this.penaltyDiscount);
    }

    private double calculateStructurePrior(int k) {
        if (this.structurePrior <= 0) {
            return 0;
        } else {
            double n = this.variables.size() - 1;
            double p = this.structurePrior / n;
            return k * log(p) + (n - k) * log(1.0 - p);
        }
    }

    // Subsample of the continuous mixedVariables conditioning on the given cols.
    private Matrix getCov(List<Integer> rows, int[] cols) {
        if (rows.isEmpty()) return new Matrix(0, 0);
        Matrix cov = new Matrix(cols.length, cols.length);

        for (int i = 0; i < cols.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                double mui = 0.0;
                double muj = 0.0;

                for (int k : rows) {
                    mui += this.ddata.getDouble(k, cols[i]);
                    muj += this.ddata.getDouble(k, cols[j]);
                }

                mui /= rows.size() - 1;
                muj /= rows.size() - 1;

                double _cov = 0.0;

                for (int k : rows) {
                    _cov += (this.ddata.getDouble(k, cols[i]) - mui) * (this.ddata.getDouble(k, cols[j]) - muj);
                }

                double mean = _cov / (rows.size());
                cov.set(i, j, mean);
            }
        }

        return cov;
    }

    private List<Integer> getRows(int i, int[] parents) {
        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < this.dataSet.getNumRows(); k++) {
            Node ii = this.variables.get(i);

            List<Integer> A = new ArrayList<>(this.embedding.get(this.nodesHash.get(ii)));

            for (int j : A) {
                if (Double.isNaN(this.ddata.getDouble(k, j))) continue K;
            }

            for (int ignored : parents) {
                Node pp = this.variables.get(i);

                List<Integer> AA = new ArrayList<>(this.embedding.get(this.nodesHash.get(pp)));

                for (int j : AA) {
                    if (Double.isNaN(this.ddata.getDouble(k, j))) continue K;
                }
            }

            rows.add(k);
        }

        return rows;
    }
}



