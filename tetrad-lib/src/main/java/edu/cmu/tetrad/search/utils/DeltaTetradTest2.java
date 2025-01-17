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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;

import java.util.*;

/**
 * Implements a test for simultaneously zero sextads in the style of Bollen, K. (1990). Sociological Methods and
 * Research 19, 80-92 and Bollen and Ting, Confirmatory Tetrad Analysis.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DeltaTetradTest2 {
    private static final long serialVersionUID = 23L;
    private final int N;
    private final ICovarianceMatrix cov;
    private final List<Node> variables;
    private DataSet dataSet = null;
    private double[][] data;
    private int numTetrads;

    // As input we require a data set and a list of non-redundant Tetrads.

    // Need a method to remove Tetrads from the input list until what's left is
    // non-redundant. Or at least to check for non-redundancy. Maybe simply
    // checking to see whether a matrix exception is thrown is sufficient.
    // Peter suggests looking at Modern Factor Analysis by Harmon, at triplets.

    /**
     * Constructs a test using a given data set. If a data set is provided (that is, a tabular data set), fourth moment
     * statistics can be calculated (p. 160); otherwise, it must be assumed that the data are multivariate Gaussian.
     *
     * @param dataSet The dataset to use.
     */
    public DeltaTetradTest2(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException();
        }

        this.cov = new CorrelationMatrix(dataSet);
        this.dataSet = dataSet;

        Matrix centered = DataTransforms.centerData(dataSet.getDoubleData());
        this.data = centered.transpose().toArray();
        this.N = dataSet.getNumRows();
        this.variables = dataSet.getVariables();
    }

    /**
     * Constructs a test using the given covariance matrix. Fourth moment statistics are not caculated; it is assumed
     * that the data are distributed as multivariate Gaussian.
     *
     * @param cov The covariance matrix to use.
     */
    public DeltaTetradTest2(ICovarianceMatrix cov) {
        if (cov == null) {
            throw new NullPointerException();
        }

        this.cov = new CorrelationMatrix(cov);
        this.N = cov.getSampleSize();
        this.variables = cov.getVariables();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return This instance.
     */
    public static DeltaTetradTest2 serializableInstance() {
        return new DeltaTetradTest2(BoxDataSet.serializableInstance());
    }

    /**
     * Takes a list of tetrads for the given data set and returns the chi square value for the test. We assume that the
     * tetrads are non-redundant; if not, a matrix exception will be thrown.
     * <p>
     * Calculates the T statistic (Bollen and Ting, p. 161). This is significant if tests as significant using the Chi
     * Square distribution with degrees of freedom equal to the number of nonredundant tetrads tested.
     *
     * @param tetrads The tetrads for which a p-value is needed.
     * @return The p-value.
     */
    public double getPValue(TetradInt... tetrads) {
        Set<TetradInt> boldTetradSet = new LinkedHashSet<>();
        Collections.addAll(boldTetradSet, tetrads);
        int df = boldTetradSet.size();
        double chisq = calcChiSquare(boldTetradSet);
        return StatUtils.getChiSquareP(df, chisq);
    }

    public double calcChiSquare(Set<TetradInt> tetrads) {
        List<TetradInt> _tetrads = new ArrayList<>(tetrads);

        this.numTetrads = _tetrads.size();

        // Need a list of symbolic covariances--i.e. covariances that appear in tetrads.
        Set<DeltaTetradTest2.Sigma> boldSigmaSet = new LinkedHashSet<>();

        for (TetradInt tetrad : _tetrads) {
            boldSigmaSet.add(new DeltaTetradTest2.Sigma(tetrad.i(), tetrad.k()));
            boldSigmaSet.add(new DeltaTetradTest2.Sigma(tetrad.i(), tetrad.l()));
            boldSigmaSet.add(new DeltaTetradTest2.Sigma(tetrad.j(), tetrad.k()));
            boldSigmaSet.add(new DeltaTetradTest2.Sigma(tetrad.j(), tetrad.l()));
        }

        List<DeltaTetradTest2.Sigma> boldSigma = new ArrayList<>(boldSigmaSet);

        // Need a matrix of variances and covariances of sample covariances.
        Matrix sigma_ss = new Matrix(boldSigma.size(), boldSigma.size());

        for (int i = 0; i < boldSigma.size(); i++) {
            for (int j = 0; j < boldSigma.size(); j++) {
                DeltaTetradTest2.Sigma sigmaef = boldSigma.get(i);
                DeltaTetradTest2.Sigma sigmagh = boldSigma.get(j);

                int e = sigmaef.getA();
                int f = sigmaef.getB();
                int g = sigmagh.getA();
                int h = sigmagh.getB();

                if (this.cov != null && dataSet == null && this.cov instanceof CorrelationMatrix) {


                    // Formula 23 (assumed multinormality):
                    double rr_23 = 0.5 * (sxy(e, f) * sxy(g, h))
                                   * (sxy(e, g) * sxy(e, g) + sxy(e, h) * sxy(e, h) + sxy(f, g) * sxy(f, g) + sxy(f, h) * sxy(f, h))
                                   + sxy(e, g) * sxy(f, h) + sxy(e, h) * sxy(f, g)
                                   - sxy(e, f) * (sxy(f, g) * sxy(f, h) + sxy(e, g) * sxy(e, h))
                                   - sxy(g, h) * (sxy(f, g) * sxy(e, g) + sxy(f, h) * sxy(e, h));

                    sigma_ss.set(i, j, rr_23);
                } else if (this.cov != null && this.dataSet != null && this.cov instanceof CorrelationMatrix) {

                    // Formula 22 (arbitrary distributions):
                    double rr_22 = sxyzw(e, f, g, h) + 0.25 * sxy(e, f) * sxy(g, h)
                                                       * (sxyzw(e, e, g, g) + sxyzw(f, f, g, g) + sxyzw(e, e, h, h) + sxyzw(f, f, h, h))
                                   - 0.5 * sxy(e, f) * (sxyzw(e, e, g, h) + sxyzw(f, f, g, h))
                                   - 0.5 * sxy(g, h) * (sxyzw(e, f, g, g) + sxyzw(e, f, h, h));

                    // Assumes multinormality--see p. 160.
                    sigma_ss.set(i, j, rr_22);
                } else {
                    throw new IllegalArgumentException("Not implemented.");
                }
            }
        }

        // Need a matrix of of population estimates of partial derivatives of tetrads
        // with respect to covariances in boldSigma.w
        Matrix del = new Matrix(boldSigma.size(), _tetrads.size());

        for (int i = 0; i < boldSigma.size(); i++) {
            for (int j = 0; j < _tetrads.size(); j++) {
                DeltaTetradTest2.Sigma sigma = boldSigma.get(i);
                TetradInt tetrad = _tetrads.get(j);

                int e = tetrad.i();
                int f = tetrad.j();
                int g = tetrad.k();
                int h = tetrad.l();

                double derivative = getDerivative(e, f, g, h, sigma.getA(), sigma.getB());
                del.set(i, j, derivative);
            }
        }

        // Need a vector of population estimates of the tetrads.
        Matrix t = new Matrix(_tetrads.size(), 1);

        for (int i = 0; i < _tetrads.size(); i++) {
            TetradInt tetrad = _tetrads.get(i);

            int e = tetrad.i();
            int f = tetrad.j();
            int g = tetrad.k();
            int h = tetrad.l();

            double d1 = sxy(e, f);
            double d2 = sxy(g, h);
            double d3 = sxy(e, g);
            double d4 = sxy(f, h);

            double value = d1 * d2 - d3 * d4;
            t.set(i, 0, value);
        }

        // Now multiply to get Sigma_tt
        Matrix w1 = del.transpose().times(sigma_ss);
        Matrix sigma_tt = w1.times(del);

        // And now invert and multiply to get T.
        Matrix v0 = sigma_tt.inverse();
        Matrix v1 = t.transpose().times(v0);
        Matrix v2 = v1.times(t);
        return this.N * v2.get(0, 0);
    }

    /**
     * Returns the variables of the data being used.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    private double getDerivative(int node1, int node2, int node3, int node4, int a, int b) {
        if (node1 == a && node2 == b) {
            return sxy(node3, node4);
        }

        if (node1 == b && node2 == a) {
            return sxy(node3, node4);
        }

        if (node3 == a && node4 == b) {
            return sxy(node1, node2);
        }

        if (node3 == b && node4 == a) {
            return sxy(node1, node2);
        }

        if (node1 == a && node3 == b) {
            return -sxy(node2, node4);
        }

        if (node1 == b && node3 == a) {
            return -sxy(node2, node4);
        }

        if (node2 == a && node4 == b) {
            return -sxy(node1, node3);
        }

        if (node2 == b && node4 == a) {
            return -sxy(node1, node3);
        }

        return 0.0;
    }

    private double sxy(int i, int j) {
        if (this.cov != null) {
            return this.cov.getValue(i, j);
        } else {
            double[] arr1 = this.data[i];
            double[] arr2 = this.data[j];
            return sxy(arr1, arr2, arr1.length);
        }
    }

    private double sxy(double[] array1, double[] array2, int N) {
        int i;
        double sum = 0.0;

        for (i = 0; i < N; i++) {
            sum += array1[i] * array2[i];
        }

        return (1.0 / N) * sum;
    }

    private double sxyzw(int x, int y, int z, int w) {
        double sxyzw = 0.0;

        double[] _x = this.data[x];
        double[] _y = this.data[y];
        double[] _z = this.data[z];
        double[] _w = this.data[w];

        int N = _x.length;

        for (int j = 0; j < N; j++) {
            sxyzw += _x[j] * _y[j] * _z[j] * _w[j];
        }

        return (1.0 / N) * sxyzw;
    }

    // Represents a single covariance symbolically.
    private static class Sigma {
        private final int a;
        private final int b;

        public Sigma(int a, int b) {
            this.a = a;
            this.b = b;
        }

        public int getA() {
            return this.a;
        }

        public int getB() {
            return this.b;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Sigma _o)) {
                throw new IllegalArgumentException();
            }

            return (_o.getA() == (getA()) && _o.getB() == (getB())) || (_o.getB() == (getA()) && _o.getA() == (getB()));
        }

        public int hashCode() {
            return this.a + this.b;
        }

        public String toString() {
            return "Sigma(" + getA() + ", " + getB() + ")";
        }
    }
}



