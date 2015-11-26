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
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements a test for simultaneously zero tetrads in Bollen, K. (1990).  Sociological Methods and Research 19, 80-92 and Bollen and Ting, Confirmatory Tetrad
 * Analysis.
 *
 * @author Joseph Ramsey
 */
public class DeltaSextadTest implements IDeltaSextadTest {
    static final long serialVersionUID = 23L;

    private DataSet dataSet;
    private double[][] data;
    private int N;
    private ICovarianceMatrix cov;
    private int df;
    private double chisq;
    private double[][][][] fourthMoment;
    private int numVars;
    private double[] means;
    private List<Node> variables;
    private Map<Node, Integer> variablesHash;
    private boolean cacheFourthMoments = true;
    private double storedValue;
    private Sextad[] storedSextads;
    private transient NumberFormat nf = new DecimalFormat("0.000000");


    // As input we require a data set and a list of non-redundant Tetrads.

    // Need a method to remove Tetrads from the input list until what's left is
    // non-redundant. Or at least to check for non-redundancy. Maybe simply
    // checking to see whether a matrix exception is thrown is sufficient.
    // Peter suggests looking at Modern Factor Analysis by Harmon, at triplets.

    /**
     * Constructs a test using a given data set. If a data set is provided (that is, a tabular data set), fourth moment
     * statistics can be calculated (p. 160); otherwise, it must be assumed that the data are multivariate Gaussian.
     */
    public DeltaSextadTest(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException();
        }

        this.cov = new CovarianceMatrix(dataSet);

        List<DataSet> data1 = new ArrayList<DataSet>();
        data1.add(dataSet);
        List<DataSet> data2 = DataUtils.center(data1);

        this.dataSet = data2.get(0);

        this.data = this.dataSet.getDoubleData().transpose().toArray();
        this.N = dataSet.getNumRows();
        this.variables = dataSet.getVariables();
        this.numVars = dataSet.getNumColumns();

        this.variablesHash = new HashMap<Node, Integer>();

        for (int i = 0; i < variables.size(); i++) {
            variablesHash.put(variables.get(i), i);
        }

        this.means = new double[numVars];

        for (int i = 0; i < numVars; i++) {
            means[i] = mean(data[i], N);
        }
    }

    /**
     * Constructs a test using the given covariance matrix. Fourth moment statistics are not caculated; it is assumed
     * that the data are distributed as multivariate Gaussian.
     */
    public DeltaSextadTest(ICovarianceMatrix cov) {
        if (cov == null) {
            throw new NullPointerException();
        }

        this.cov = cov;
        this.N = cov.getSampleSize();
        this.numVars = cov.getVariables().size();
        this.variables = cov.getVariables();

        this.variablesHash = new HashMap<Node, Integer>();

        for (int i = 0; i < variables.size(); i++) {
            variablesHash.put(variables.get(i), i);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static IDeltaSextadTest serializableInstance() {
        return new DeltaSextadTest(ColtDataSet.serializableInstance());
    }

    private void initializeForthMomentMatrix(List<Node> variables) {
        int n = variables.size();
        fourthMoment = new double[n][n][n][n];
    }

    /**
     * Takes a list of tetrads for the given data set and returns the chi square value for the test. We assume that the
     * tetrads are non-redundant; if not, a matrix exception will be thrown.
     * <p/>
     * Calculates the T statistic (Bollen and Ting, p. 161). This is significant if tests as significant using the Chi
     * Square distribution with degrees of freedom equal to the number of nonredundant tetrads tested.
     */
    @Override
    //    public double calcChiSquare1(Sextad... sextads) {
//        this.storedSextads = sextads;
//
//        this.df = sextads.length;
//
//        // Need a list of symbolic covariances--i.e. covariances that appear in tetrads.
//        Set<Sigma> boldSigmaSet = new LinkedHashSet<Sigma>();
//        List<Sigma> boldSigma = new ArrayList<Sigma>();
//
//        for (Sextad sextad : sextads) {
//            boldSigmaSet.add(new Sigma(sextad.getI(), sextad.getL()));
//            boldSigmaSet.add(new Sigma(sextad.getI(), sextad.getM()));
//            boldSigmaSet.add(new Sigma(sextad.getI(), sextad.getN()));
//
//            boldSigmaSet.add(new Sigma(sextad.getJ(), sextad.getL()));
//            boldSigmaSet.add(new Sigma(sextad.getJ(), sextad.getM()));
//            boldSigmaSet.add(new Sigma(sextad.getJ(), sextad.getN()));
//
//            boldSigmaSet.add(new Sigma(sextad.getK(), sextad.getL()));
//            boldSigmaSet.add(new Sigma(sextad.getK(), sextad.getM()));
//            boldSigmaSet.add(new Sigma(sextad.getK(), sextad.getN()));
//        }
//
//        for (Sigma sigma : boldSigmaSet) {
//            boldSigma.add(sigma);
//        }
//
//        // Need a matrix of variances and covariances of sample covariances.
//        TetradMatrix sigma_ss = TetradMatrix.instance(boldSigma.size(), boldSigma.size());
//
//        for (int i = 0; i < boldSigma.size(); i++) {
//            for (int j = 0; j < boldSigma.size(); j++) {
//                Sigma sigmaef = boldSigma.get(i);
//                Sigma sigmagh = boldSigma.get(j);
//
//                Node e = sigmaef.getA();
//                Node f = sigmaef.getB();
//                Node g = sigmagh.getA();
//                Node h = sigmagh.getB();
//
//                if (cov != null && cov instanceof CorrelationMatrix) {
//
////                Assumes multinormality. Using formula 23. (Not implementing formula 22 because that case
////                does not come up.)
//                    double rr = 0.5 * (r(e, f) * r(g, h))
//                            * (r(e, g) * r(e, g) + r(e, h) * r(e, h) + r(f, g) * r(f, g) + r(f, h) * r(f, h))
//                            + r(e, g) * r(f, h) + r(e, h) * r(f, g)
//                            - r(e, f) * (r(f, g) * r(f, h) + r(e, g) * r(e, h))
//                            - r(g, h) * (r(f, g) * r(e, g) + r(f, h) * r(e, h));
//
//                    sigma_ss.set(i, j, rr);
//                } else if (cov != null && dataSet == null) {
//
//                    // Assumes multinormality--see p. 160.
//                    double _ss = r(e, g) * r(f, h) + r(e, h) * r(f, g);   // + or -? Different advise. + in the code.
//                    sigma_ss.set(i, j, _ss);
//                } else {
//                    double _ss = sxyzw(e, f, g, h) - r(e, f) * r(g, h);
//                    sigma_ss.set(i, j, _ss);
//                }
//            }
//        }
//
//        // Need a matrix of of population estimates of partial derivatives of tetrads
//        // with respect to covariances in boldSigma.
//        TetradMatrix del = TetradMatrix.instance(boldSigma.size(), sextads.length);
//
//        for (int i = 0; i < boldSigma.size(); i++) {
//            for (int j = 0; j < sextads.length; j++) {
//                Sigma sigma = boldSigma.get(i);
//                Sextad sextad = sextads[j];
//
//                Node m1 = sextad.getI();
//                Node m2 = sextad.getJ();
//                Node m3 = sextad.getK();
//                Node m4 = sextad.getL();
//                Node m5 = sextad.getM();
//                Node m6 = sextad.getN();
//
//                double derivative = getDerivative(m1, m2, m3, m4, m5, m6, sigma.getA(), sigma.getB());
//                del.set(i, j, derivative);
//            }
//        }
//
//        // Need a vector of population estimates of the sextads.
//        TetradMatrix t = TetradMatrix.instance(sextads.length, 1);
//
//        for (int i = 0; i < sextads.length; i++) {
//            Sextad sextad = sextads[i];
//
//            List<Node> nodes = new ArrayList<Node>();
//
//            nodes.add(sextad.getI());
//            nodes.add(sextad.getJ());
//            nodes.add(sextad.getK());
//            nodes.add(sextad.getL());
//            nodes.add(sextad.getM());
//            nodes.add(sextad.getN());
//
//            TetradMatrix m = TetradMatrix.instance(3, 3);
//
//            for (int k1 = 0; k1 < 3; k1++) {
//                for (int k2 = 0; k2 < 3; k2++) {
//                    m.set(k1, k2, r(nodes.get(k1), nodes.get(3+k2)));
//                }
//            }
//
//            double value = TetradAlgebra.det(m);
//            t.set(i, 0, value);
//            this.storedValue = value;
//        }
//
//        // Now multiply to get Sigma_tt
//        TetradMatrix w1 = TetradAlgebra.times(del.transpose(), sigma_ss);
//        TetradMatrix sigma_tt = TetradAlgebra.times(w1, del);
//
//        // And now invert and multiply to get T.
//        TetradMatrix v0 = TetradAlgebra.inverse(sigma_tt);
//        TetradMatrix v1 = TetradAlgebra.times(t.transpose(), v0);
//        TetradMatrix v2 = TetradAlgebra.times(v1, t);
//        double chisq = N * v2.get(0, 0);
//
//        this.chisq = chisq;
//        return chisq;
//    }
    public double calcChiSquare(Sextad... sextads) {
        this.storedSextads = sextads;

        this.df = 4; //sextads.length;

        List<Sigma> boldSigma = new ArrayList<Sigma>();

        List<Node> _nodes = new ArrayList<Node>(sextads[0].getNodes());

        for (int i = 0; i < _nodes.size(); i++) {
            for (int j = i + 1; j < _nodes.size(); j++) {
                boldSigma.add(new Sigma(_nodes.get(i), _nodes.get(j)));
            }
        }

        // Need a matrix of variances and covariances of sample covariances.
        TetradMatrix sigma_ss = new TetradMatrix(boldSigma.size(), boldSigma.size());

        for (int i = 0; i < boldSigma.size(); i++) {
            for (int j = i; j < boldSigma.size(); j++) {
                Sigma sigmaef = boldSigma.get(i);
                Sigma sigmagh = boldSigma.get(j);

                Node e = sigmaef.getA();
                Node f = sigmaef.getB();
                Node g = sigmagh.getA();
                Node h = sigmagh.getB();

                if (cov != null && cov instanceof CorrelationMatrix) {

//                Assumes multinormality. Using formula 23. (Not implementing formula 22 because that case
//                does not come up.)
                    double rr = 0.5 * (r(e, f) * r(g, h))
                            * (r(e, g) * r(e, g) + r(e, h) * r(e, h) + r(f, g) * r(f, g) + r(f, h) * r(f, h))
                            + r(e, g) * r(f, h) + r(e, h) * r(f, g)
                            - r(e, f) * (r(f, g) * r(f, h) + r(e, g) * r(e, h))
                            - r(g, h) * (r(f, g) * r(e, g) + r(f, h) * r(e, h));

                    sigma_ss.set(i, j, rr);
                    sigma_ss.set(j, i, rr);
                } else if (cov != null && dataSet == null) {

                    // Assumes multinormality--see p. 160.
                    double _ss = r(e, g) * r(f, h) + r(e, h) * r(f, g);   // + or -? Different advise. + in the code.
//                    double _ss = r(e, g) * r(f, h) - r(e, h) * r(g, f);   // shouldn't this be a tetrad?
                    sigma_ss.set(i, j, _ss);
                    sigma_ss.set(j, i, _ss);
                } else {
                    double _ss = sxyzw(e, f, g, h) - r(e, f) * r(g, h);
                    sigma_ss.set(i, j, _ss);
                    sigma_ss.set(j, i, _ss);
                }
            }
        }

        // Need a matrix of of population estimates of partial derivatives of tetrads
        // with respect to covariances in boldSigma.
        TetradMatrix del = new TetradMatrix(boldSigma.size(), sextads.length);

        for (int j = 0; j < sextads.length; j++) {
            Sextad sextad = sextads[j];

            for (int i = 0; i < boldSigma.size(); i++) {
                Sigma sigma = boldSigma.get(i);
                double derivative = getDerivative(sextad, sigma);
                del.set(i, j, derivative);
            }
        }

        // Need a vector of population estimates of the sextads.
        TetradMatrix t = new TetradMatrix(sextads.length, 1);

        for (int i = 0; i < sextads.length; i++) {
            Sextad sextad = sextads[i];
            List<Node> nodes = sextad.getNodes();
            TetradMatrix m = new TetradMatrix(3, 3);

            for (int k1 = 0; k1 < 3; k1++) {
                for (int k2 = 0; k2 < 3; k2++) {
                    m.set(k1, k2, r(nodes.get(k1), nodes.get(3 + k2)));
                }
            }

            double det = m.det();
            t.set(i, 0, det);
            this.storedValue = det; // ?
        }

        //        for (int i = 0; i < sextads.length; i++) {
//            Sextad sextad = sextads[i];
//
//            List<Node> nodes = new ArrayList<Node>();
//
//            nodes.add(sextad.getI());
//            nodes.add(sextad.getJ());
//            nodes.add(sextad.getK());
//            nodes.add(sextad.getL());
//            nodes.add(sextad.getM());
//            nodes.add(sextad.getN());
//
//            TetradMatrix m = TetradMatrix.instance(3, 3);
//
//            for (int k1 = 0; k1 < 3; k1++) {
//                for (int k2 = 0; k2 < 3; k2++) {
//                    m.set(k1, k2, r(nodes.get(k1), nodes.get(3+k2)));
//                }
//            }
//
//            double value = TetradAlgebra.det(m);
//            t.set(i, 0, value);
//            this.storedValue = value;
//        }

        TetradMatrix sigma_tt = del.transpose().times(sigma_ss).times(del);
        try {
            this.chisq = N * t.transpose().times(sigma_tt.inverse()).times(t).get(0, 0);
            return chisq;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * @return the p value for the most recent test.
     */
    @Override
    public double getPValue() {
        double cdf = ProbUtils.chisqCdf(this.chisq, this.df);
        double p = 1.0 - cdf;

        String s = "";

        for (int i = 0; i < storedSextads.length; i++) {
            s += storedSextads[i] + " ";
        }

        s += "value = " + nf.format(storedValue) + " p = " + nf.format(p);

        TetradLogger.getInstance().log("sextadPValues", s);

        return p;
    }

    @Override
    public double getPValue(Sextad... sextads) {
        calcChiSquare(sextads);
        return getPValue();
    }

    private double sxyzw(Node e, Node f, Node g, Node h) {
        if (dataSet == null) {
            throw new IllegalArgumentException("To calculate sxyzw, tabular data is needed.");
        }

        int x = variablesHash.get(e);
        int y = variablesHash.get(f);
        int z = variablesHash.get(g);
        int w = variablesHash.get(h);

        return getForthMoment(x, y, z, w);
    }

    private void setForthMoment(int x, int y, int z, int w, double sxyzw) {
        fourthMoment[x][y][z][w] = sxyzw;
        fourthMoment[x][y][w][z] = sxyzw;
        fourthMoment[x][w][z][y] = sxyzw;
        fourthMoment[x][w][y][z] = sxyzw;
        fourthMoment[x][z][y][w] = sxyzw;
        fourthMoment[x][z][w][y] = sxyzw;

        fourthMoment[y][x][z][w] = sxyzw;
        fourthMoment[y][x][w][z] = sxyzw;
        fourthMoment[y][z][x][w] = sxyzw;
        fourthMoment[y][z][w][x] = sxyzw;
        fourthMoment[y][w][x][z] = sxyzw;
        fourthMoment[y][w][z][x] = sxyzw;

        fourthMoment[z][x][y][w] = sxyzw;
        fourthMoment[z][x][w][y] = sxyzw;
        fourthMoment[z][y][x][w] = sxyzw;
        fourthMoment[z][y][w][x] = sxyzw;
        fourthMoment[z][w][x][y] = sxyzw;
        fourthMoment[z][w][y][x] = sxyzw;

        fourthMoment[w][x][y][z] = sxyzw;
        fourthMoment[w][x][z][y] = sxyzw;
        fourthMoment[w][y][x][z] = sxyzw;
        fourthMoment[w][y][z][x] = sxyzw;
        fourthMoment[w][z][x][y] = sxyzw;
        fourthMoment[w][z][y][x] = sxyzw;
    }

    private double getForthMoment(int x, int y, int z, int w) {
        if (cacheFourthMoments) {
            if (fourthMoment == null) {
                initializeForthMomentMatrix(dataSet.getVariables());
            }

            double sxyzw = fourthMoment[x][y][z][w];

            if (sxyzw == 0.0) {
                sxyzw = sxyzw(x, y, z, w);
                setForthMoment(x, y, z, w, sxyzw);
            }

            return sxyzw;
        } else {
            return sxyzw(x, y, z, w);
        }
    }

    /**
     * If using a covariance matrix or a correlation matrix, just returns the lookups. Otherwise calculates the
     * covariance.
     */
    private double r(Node _node1, Node _node2) {
        int i = variablesHash.get(_node1);
        int j = variablesHash.get(_node2);

        if (cov != null) {
            return cov.getValue(i, j);
        } else {
            double[] arr1 = data[i];
            double[] arr2 = data[j];
            return sxy(arr1, arr2, arr1.length);
        }
    }

    private double getDerivative(Sextad sextad, Sigma sigma) {
        Node a = sigma.getA();
        Node b = sigma.getB();

        Node n1 = sextad.getI();
        Node n2 = sextad.getJ();
        Node n3 = sextad.getK();
        Node n4 = sextad.getL();
        Node n5 = sextad.getM();
        Node n6 = sextad.getN();

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

        // symmetry
        n6 = sextad.getI();
        n5 = sextad.getJ();
        n4 = sextad.getK();
        n3 = sextad.getL();
        n2 = sextad.getM();
        n1 = sextad.getN();

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

    @Override
    public void setCacheFourthMoments(boolean cacheFourthMoments) {
        this.cacheFourthMoments = cacheFourthMoments;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    // Represents a single covariance symbolically.
    private static class Sigma {
        private Node a;
        private Node b;

        public Sigma(Node a, Node b) {
            this.a = a;
            this.b = b;
        }

        public Node getA() {
            return a;
        }

        public Node getB() {
            return b;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Sigma)) {
                throw new IllegalArgumentException();
            }

            Sigma _o = (Sigma) o;
            return (_o.getA().equals(getA()) && _o.getB().equals(getB())) || (_o.getB().equals(getA()) && _o.getA().equals(getB()));
        }

        public int hashCode() {
            return 1; //a.hashCode() + b.hashCode();
        }

        public String toString() {
            return "Sigma(" + getA() + ", " + getB() + ")";
        }
    }

    // Assumes data are mean-centered.
    private double sxyzw(int x, int y, int z, int w) {
        double sxyzw = 0.0;

        double[] _x = data[x];
        double[] _y = data[y];
        double[] _z = data[z];
        double[] _w = data[w];

        int N = _x.length;

        for (int j = 0; j < N; j++) {
            sxyzw += _x[j] * _y[j] * _z[j] * _w[j];
        }

        return (1.0 / N) * sxyzw;
    }

    // Assumes data are mean-centered.
    private double sxy(double array1[], double array2[], int N) {
        int i;
        double sum = 0.0;

        for (i = 0; i < N; i++) {
            sum += array1[i] * array2[i];
        }

        return (1.0 / N) * sum;
    }

    private double mean(double array[], int N) {
        int i;
        double sum = 0;

        for (i = 0; i < N; i++) {
            sum += array[i];
        }

        return sum / N;
    }
}



