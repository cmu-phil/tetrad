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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements a test for simultaneously zero sextads in the style of Bollen, K. (1990).
 * Sociological Methods and Research 19, 80-92 and Bollen and Ting, Confirmatory Tetrad
 * Analysis.
 *
 * @author Joseph Ramsey
 */
public class DeltaSextadTest {
    static final long serialVersionUID = 23L;

    private double[][] data;
    private final int N;
    private final ICovarianceMatrix cov;
    private final List<Node> variables;

    // As input we require a data set and a list of non-redundant Tetrads.

    // Need a method to remove Tetrads from the input list until what's left is
    // non-redundant. Or at least to check for non-redundancy. Maybe simply
    // checking to see whether a matrix exception is thrown is sufficient.
    // Peter suggests looking at Modern Factor Analysis by Harmon, at triplets.

    /**
     * Constructs a test using a given data set. If a data set is provided (that is, a tabular data set), fourth moment
     * statistics can be calculated (p. 160); otherwise, it must be assumed that the data are multivariate Gaussian.
     */
    public DeltaSextadTest(final DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException();
        }

        this.cov = new CovarianceMatrix(dataSet);

        final Matrix centered = DataUtils.centerData(dataSet.getDoubleData());
        this.data = centered.transpose().toArray();
        this.N = dataSet.getNumRows();
        this.variables = dataSet.getVariables();
    }

    /**
     * Constructs a test using the given covariance matrix. Fourth moment statistics are not caculated; it is assumed
     * that the data are distributed as multivariate Gaussian.
     */
    public DeltaSextadTest(final ICovarianceMatrix cov) {
        if (cov == null) {
            throw new NullPointerException();
        }

        this.cov = cov;
        this.N = cov.getSampleSize();
        this.variables = cov.getVariables();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static DeltaSextadTest serializableInstance() {
        return new DeltaSextadTest(BoxDataSet.serializableInstance());
    }

    /**
     * Takes a list of tetrads for the given data set and returns the chi square value for the test. We assume that the
     * tetrads are non-redundant; if not, a matrix exception will be thrown.
     * <p>
     * Calculates the T statistic (Bollen and Ting, p. 161). This is significant if tests as significant using the Chi
     * Square distribution with degrees of freedom equal to the number of nonredundant tetrads tested.
     */
    public double getPValue(final IntSextad... sextads) {
//        int df = sextads.length;
        final int df = dofHarman(sextads.length);
        final double chisq = calcChiSquare(sextads);
//        double cdf = ProbUtils.chisqCdf(chisq, df);
        final double cdf = new ChiSquaredDistribution(df).cumulativeProbability(chisq);
        return 1.0 - cdf;
    }

    /**
     * Takes a list of tetrads for the given data set and returns the chi square value for the test. We assume that the
     * tetrads are non-redundant; if not, a matrix exception will be thrown.
     * <p>
     * Calculates the T statistic (Bollen and Ting, p. 161). This is significant if tests as significant using the Chi
     * Square distribution with degrees of freedom equal to the number of nonredundant tetrads tested.
     */
    public double calcChiSquare(final IntSextad[] sextads) {
        final Set<Sigma> boldSigmaSet = new HashSet<>();

        for (final IntSextad sextad : sextads) {
            final List<Integer> _nodes = sextad.getNodes();

//            for (int i = 0; i < _nodes.size(); i++) {
//                for (int j = i; j < _nodes.size(); j++) {
//                    boldSigmaSet.add(new Sigma(_nodes.get(i), _nodes.get(j)));
//                }
//            }
//
            for (int k1 = 0; k1 < 3; k1++) {
                for (int k2 = 0; k2 < 3; k2++) {
                    boldSigmaSet.add(new Sigma(_nodes.get(k1), _nodes.get(3 + k2)));
                }
            }
        }

        final List<Sigma> boldSigma = new ArrayList<>(boldSigmaSet);

        // Need a matrix of variances and covariances of sample covariances.
        final Matrix sigma_ss = new Matrix(boldSigma.size(), boldSigma.size());

        for (int i = 0; i < boldSigma.size(); i++) {
            for (int j = i; j < boldSigma.size(); j++) {
                final Sigma sigmaef = boldSigma.get(i);
                final Sigma sigmagh = boldSigma.get(j);

                final int e = sigmaef.getA();
                final int f = sigmaef.getB();
                final int g = sigmagh.getA();
                final int h = sigmagh.getB();

                if (this.cov != null && this.cov instanceof CorrelationMatrix) {

//                Assumes multinormality. Using formula 23. (Not implementing formula 22 because that case
//                does not come up.)
                    final double rr = 0.5 * (r(e, f) * r(g, h))
                            * (r(e, g) * r(e, g) + r(e, h) * r(e, h) + r(f, g) * r(f, g) + r(f, h) * r(f, h))
                            + r(e, g) * r(f, h) + r(e, h) * r(f, g)
                            - r(e, f) * (r(f, g) * r(f, h) + r(e, g) * r(e, h))
                            - r(g, h) * (r(f, g) * r(e, g) + r(f, h) * r(e, h));

                    // General.
                    final double rr2 = r(e, f, g, h) + 0.25 * r(e, f) * r(g, h) *
                            (r(e, e, g, g) * r(f, f, g, g) + r(e, e, h, h) + r(f, f, h, h))
                            - 0.5 * r(e, f) * (r(e, e, g, h) + r(f, f, g, h))
                            - 0.5 * r(g, h) * (r(e, f, g, g) + r(e, f, h, h));

                    sigma_ss.set(i, j, rr);
                    sigma_ss.set(j, i, rr);
                } else if (this.cov != null && this.data == null) {

                    // Assumes multinormality--see p. 160.
//                    double _ss = r(e, g) * r(f, h) + r(e, h) * r(f, g); // + or -? Different advise. + in the code.
                    final double _ss = r(e, g) * r(f, h) + r(e, h) * r(f, g);
                    sigma_ss.set(i, j, _ss);
                    sigma_ss.set(j, i, _ss);
                } else {
                    final double _ss = r(e, f, g, h) - r(e, f) * r(g, h);
                    sigma_ss.set(i, j, _ss);
                    sigma_ss.set(j, i, _ss);
                }
            }
        }

        // Need a matrix of of population estimates of partial derivatives of tetrads
        // with respect to covariances in boldSigma.
        final Matrix del = new Matrix(boldSigma.size(), sextads.length);

        for (int j = 0; j < sextads.length; j++) {
            final IntSextad sextad = sextads[j];

            for (int i = 0; i < boldSigma.size(); i++) {
                final Sigma sigma = boldSigma.get(i);
                final double derivative = getDerivative(sextad, sigma);
                del.set(i, j, derivative);
            }
        }

        // Need a vector of population estimates of the sextads.
        final Matrix t = new Matrix(sextads.length, 1);

        for (int i = 0; i < sextads.length; i++) {
            final IntSextad sextad = sextads[i];
            final List<Integer> nodes = sextad.getNodes();
            final Matrix m = new Matrix(3, 3);

            for (int k1 = 0; k1 < 3; k1++) {
                for (int k2 = 0; k2 < 3; k2++) {
                    m.set(k1, k2, r(nodes.get(k1), nodes.get(3 + k2)));
                }
            }

            final double det = m.det();
            t.set(i, 0, det);
        }

        final Matrix sigma_tt = del.transpose().times(sigma_ss).times(del);
        final double chisq;
        try {
            chisq = this.N * t.transpose().times(sigma_tt.inverse()).times(t).get(0, 0);
        } catch (final SingularMatrixException e) {
            throw new RuntimeException("Singularity problem.", e);
        }
        return chisq;
    }

    /**
     * If using a covariance matrix or a correlation matrix, just returns the lookups. Otherwise calculates the
     * covariance.
     */
    private double r(final int i, final int j) {
        if (this.cov != null) {
            return this.cov.getValue(i, j);
        } else {
            final double[] arr1 = this.data[i];
            final double[] arr2 = this.data[j];
            return r(arr1, arr2, arr1.length);
        }
    }

    private double getDerivative(final IntSextad sextad, final Sigma sigma) {
        final int a = sigma.getA();
        final int b = sigma.getB();

        final int n1 = sextad.getI();
        final int n2 = sextad.getJ();
        final int n3 = sextad.getK();
        final int n4 = sextad.getL();
        final int n5 = sextad.getM();
        final int n6 = sextad.getN();

        final double x1 = derivative(a, b, n1, n2, n3, n4, n5, n6);
//        double x2 = derivative(a, b, n4, n5, n6, n1, n2, n3);
        final double x2 = derivative(b, a, n1, n2, n3, n4, n5, n6);

        if (x1 == 0) return x2;
        if (x2 == 0) return x1;
        throw new IllegalStateException("Both nonzero at the same time: x1 = " + x1 + " x2 = " + x2);
    }

    private double derivative(final int a, final int b, final int n1, final int n2, final int n3, final int n4, final int n5, final int n6) {
        if (a == n1) {
            if (b == n4) {
                return r(n2, n5) * r(n3, n6) - r(n2, n6) * r(n3, n5);
            } else if (b == n5) {
                return -r(n2, n4) * r(n3, n6) + r(n3, n4) * r(n2, n6);
            } else if (b == n6) {
                return r(n2, n4) * r(n3, n5) - r(n3, n4) * r(n2, n5);
            }

        } else if (a == n2) {
            if (b == n4) {
                return r(n3, n5) * r(n1, n6) - r(n1, n5) * r(n3, n6);
            } else if (b == n5) {
                return r(n1, n4) * r(n3, n6) - r(n3, n4) * r(n1, n6);
            } else if (b == n6) {
                return -r(n1, n4) * r(n3, n5) + r(n3, n4) * r(n1, n5);
            }

        } else if (a == n3) {
            if (b == n4) {
                return r(n1, n5) * r(n2, n6) - r(n2, n5) * r(n1, n6);
            } else if (b == n5) {
                return -r(n1, n4) * r(n2, n6) + r(n2, n4) * r(n1, n6);
            } else if (b == n6) {
                return r(n1, n4) * r(n2, n5) - r(n2, n4) * r(n1, n5);
            }

        }

        return 0.0;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    // Represents a single covariance symbolically.
    private static class Sigma {
        private final int a;
        private final int b;

        public Sigma(final int a, final int b) {
            this.a = a;
            this.b = b;
        }

        public int getA() {
            return this.a;
        }

        public int getB() {
            return this.b;
        }

        public boolean equals(final Object o) {
            if (!(o instanceof Sigma)) {
                throw new IllegalArgumentException();
            }

            final Sigma _o = (Sigma) o;
            return (_o.getA() == (getA()) && _o.getB() == (getB())) || (_o.getB() == (getA()) && _o.getA() == (getB()));
        }

        public int hashCode() {
            return this.a + this.b;
        }

        public String toString() {
            return "Sigma(" + getA() + ", " + getB() + ")";
        }
    }

    // Assumes data are mean-centered.
    private double r(final int x, final int y, final int z, final int w) {
        double sxyzw = 0.0;

        final double[] _x = this.data[x];
        final double[] _y = this.data[y];
        final double[] _z = this.data[z];
        final double[] _w = this.data[w];

        final int N = _x.length;

        for (int j = 0; j < N; j++) {
            sxyzw += _x[j] * _y[j] * _z[j] * _w[j];
        }

        return (1.0 / N) * sxyzw;
    }

    // Assumes data are mean-centered.
    private double r(final double[] array1, final double[] array2, final int N) {
        int i;
        double sum = 0.0;

        for (i = 0; i < N; i++) {
            sum += array1[i] * array2[i];
        }

        return (1.0 / N) * sum;
    }

    private int dofDrton(final int n) {
        int dof = ((n - 2) * (n - 3)) / 2 - 2;
        if (dof < 1) dof = 1;
        return dof;
    }

    private int dofHarman(final int n) {
        int dof = n * (n - 5) / 2 + 1;
        if (dof < 1) dof = 1;
        return dof;
    }

}



