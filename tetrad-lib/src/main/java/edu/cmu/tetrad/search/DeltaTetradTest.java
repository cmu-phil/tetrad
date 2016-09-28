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
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.util.*;

/**
 * Implements a test for simultaneously zero tetrads in Bollen, K. (1990). "Outlier screening and distribution-free test
 * for vanishing tetrads." Sociological Methods and Research 19, 80-92 and Bollen and Ting, Confirmatory Tetrad
 * Analysis.
 *
 * @author Joseph Ramsey
 */
public class DeltaTetradTest {
    private DataSet dataSet;
    private double[][] data;
    private int N;
    private ICovarianceMatrix cov;
    private int df;
    private double chisq;
    //    private double[][][][] fourthMoment;
//    private int numVars;
//    private double[] means;
    private List<Node> variables;
    private Map<Node, Integer> variablesHash;
//    private boolean cacheFourthMoments = false;


    // As input we require a data set and a list of non-redundant Tetrads.

    // Need a method to remove Tetrads from the input list until what's left is
    // non-redundant. Or at least to check for non-redundancy. Maybe simply
    // checking to see whether a matrix exception is thrown is sufficient.
    // Peter suggests looking at Modern Factor Analysis by Harmon, at triplets.

    /**
     * Constructs a test using a given data set. If a data set is provided (that is, a tabular data set), fourth moment
     * statistics can be calculated (p. 160); otherwise, it must be assumed that the data are multivariate Gaussian.
     */
    public DeltaTetradTest(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException();
        }

        this.cov = new CovarianceMatrix(dataSet);

        List<DataSet> data1 = new ArrayList<>();
        data1.add(dataSet);
        List<DataSet> data2 = DataUtils.center(data1);

        this.dataSet = data2.get(0);

        this.data = this.dataSet.getDoubleData().transpose().toArray();
        this.N = dataSet.getNumRows();
        this.variables = dataSet.getVariables();
//        this.numVars = dataSet.getNumColumns();

        this.variablesHash = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            variablesHash.put(variables.get(i), i);
        }

//        this.means = new double[numVars];
//
//        for (int i = 0; i < numVars; i++) {
//            means[i] = mean(data[i], N);
//        }
    }

    /**
     * Constructs a test using the given covariance matrix. Fourth moment statistics are not caculated; it is assumed
     * that the data are distributed as multivariate Gaussian.
     */
    public DeltaTetradTest(ICovarianceMatrix cov) {
        if (cov == null) {
            throw new NullPointerException();
        }

        this.cov = cov;
        this.N = cov.getSampleSize();
        this.variables = cov.getVariables();

        this.variablesHash = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            variablesHash.put(variables.get(i), i);
        }
    }

//    private void initializeForthMomentMatrix(List<Node> variables) {
//        int n = variables.size();
//        fourthMoment = new double[n][n][n][n];
//    }

    /**
     * Takes a list of tetrads for the given data set and returns the chi square value for the test. We assume that the
     * tetrads are non-redundant; if not, a matrix exception will be thrown.
     * <p>
     * Calculates the T statistic (Bollen and Ting, p. 161). This is significant if tests as significant using the Chi
     * Square distribution with degrees of freedom equal to the number of nonredundant tetrads tested.
     */
    public double calcChiSquare(Tetrad... tetrads) {
        this.df = tetrads.length;

        // Need a list of symbolic covariances--i.e. covariances that appear in tetrads.
        Set<Sigma> boldSigmaSet = new LinkedHashSet<>();
        List<Sigma> boldSigma = new ArrayList<>();

        for (Tetrad tetrad : tetrads) {
            boldSigmaSet.add(new Sigma(tetrad.getI(), tetrad.getK()));
            boldSigmaSet.add(new Sigma(tetrad.getI(), tetrad.getL()));
            boldSigmaSet.add(new Sigma(tetrad.getJ(), tetrad.getK()));
            boldSigmaSet.add(new Sigma(tetrad.getJ(), tetrad.getL()));
        }

        for (Sigma sigma : boldSigmaSet) {
            boldSigma.add(sigma);
        }

        // Need a matrix of variances and covariances of sample covariances.
        TetradMatrix sigma_ss = new TetradMatrix(boldSigma.size(), boldSigma.size());

        for (int i = 0; i < boldSigma.size(); i++) {
            for (int j = 0; j < boldSigma.size(); j++) {
                Sigma sigmaef = boldSigma.get(i);
                Sigma sigmagh = boldSigma.get(j);

                Node e = sigmaef.getA();
                Node f = sigmaef.getB();
                Node g = sigmagh.getA();
                Node h = sigmagh.getB();

                if (cov != null && cov instanceof CorrelationMatrix) {

//                Assumes multinormality. Using formula 23. (Not implementing formula 22 because that case
//                does not come up.)
                    double rr = 0.5 * (sxy(e, f) * sxy(g, h))
                            * (sxy(e, g) * sxy(e, g) + sxy(e, h) * sxy(e, h) + sxy(f, g) * sxy(f, g) + sxy(f, h) * sxy(f, h))
                            + sxy(e, g) * sxy(f, h) + sxy(e, h) * sxy(f, g)
                            - sxy(e, f) * (sxy(f, g) * sxy(f, h) + sxy(e, g) * sxy(e, h))
                            - sxy(g, h) * (sxy(f, g) * sxy(e, g) + sxy(f, h) * sxy(e, h));

                    sigma_ss.set(i, j, rr);
                } else if (cov != null && dataSet == null) {

                    // Assumes multinormality--see p. 160.
                    double _ss = sxy(e, g) * sxy(f, h) - sxy(e, h) * sxy(f, g);   // + or -? Different advise. + in the code.
                    sigma_ss.set(i, j, _ss);
                } else {
                    double _ss = sxyzw(e, f, g, h) - sxy(e, f) * sxy(g, h);
                    sigma_ss.set(i, j, _ss);
                }
            }
        }

        // Need a matrix of of population estimates of partial derivatives of tetrads
        // with respect to covariances in boldSigma.w
        TetradMatrix del = new TetradMatrix(boldSigma.size(), tetrads.length);

        for (int i = 0; i < boldSigma.size(); i++) {
            for (int j = 0; j < tetrads.length; j++) {
                Sigma sigma = boldSigma.get(i);
                Tetrad tetrad = tetrads[j];

                Node e = tetrad.getI();
                Node f = tetrad.getJ();
                Node g = tetrad.getK();
                Node h = tetrad.getL();

                double derivative = getDerivative(e, f, g, h, sigma.getA(), sigma.getB());
                del.set(i, j, derivative);
            }
        }

        // Need a vector of population estimates of the tetrads.
        TetradMatrix t = new TetradMatrix(tetrads.length, 1);

        for (int i = 0; i < tetrads.length; i++) {
            Tetrad tetrad = tetrads[i];

            Node e = tetrad.getI();
            Node f = tetrad.getJ();
            Node g = tetrad.getK();
            Node h = tetrad.getL();

            double d1 = sxy(e, f);
            double d2 = sxy(g, h);
            double d3 = sxy(e, g);
            double d4 = sxy(f, h);

            double value = d1 * d2 - d3 * d4;
            t.set(i, 0, value);
        }

        // Now multiply to get Sigma_tt
        TetradMatrix w1 = del.transpose().times(sigma_ss);
        TetradMatrix sigma_tt = w1.times(del);

        // And now invert and multiply to get T.
        TetradMatrix v0 = sigma_tt.inverse();
        TetradMatrix v1 = t.transpose().times(v0);
        TetradMatrix v2 = v1.times(t);
        double chisq = N * v2.get(0, 0);

        this.chisq = chisq;

        return chisq;
    }

    /**
     * @return the p value for the most recent test.
     */
    public double getPValue() {
        double cdf = new ChiSquaredDistribution(this.df).cumulativeProbability(this.chisq);
        return 1.0 - cdf;
    }

    public double getPValue(Tetrad... tetrads) {
        calcChiSquare(tetrads);
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

//    private void setForthMoment(int x, int y, int z, int w, double sxyzw) {
//        fourthMoment[x][y][z][w] = sxyzw;
//        fourthMoment[x][y][w][z] = sxyzw;
//        fourthMoment[x][w][z][y] = sxyzw;
//        fourthMoment[x][w][y][z] = sxyzw;
//        fourthMoment[x][z][y][w] = sxyzw;
//        fourthMoment[x][z][w][y] = sxyzw;
//
//        fourthMoment[y][x][z][w] = sxyzw;
//        fourthMoment[y][x][w][z] = sxyzw;
//        fourthMoment[y][z][x][w] = sxyzw;
//        fourthMoment[y][z][w][x] = sxyzw;
//        fourthMoment[y][w][x][z] = sxyzw;
//        fourthMoment[y][w][z][x] = sxyzw;
//
//        fourthMoment[z][x][y][w] = sxyzw;
//        fourthMoment[z][x][w][y] = sxyzw;
//        fourthMoment[z][y][x][w] = sxyzw;
//        fourthMoment[z][y][w][x] = sxyzw;
//        fourthMoment[z][w][x][y] = sxyzw;
//        fourthMoment[z][w][y][x] = sxyzw;
//
//        fourthMoment[w][x][y][z] = sxyzw;
//        fourthMoment[w][x][z][y] = sxyzw;
//        fourthMoment[w][y][x][z] = sxyzw;
//        fourthMoment[w][y][z][x] = sxyzw;
//        fourthMoment[w][z][x][y] = sxyzw;
//        fourthMoment[w][z][y][x] = sxyzw;
//    }

    private double getForthMoment(int x, int y, int z, int w) {
//        if (cacheFourthMoments) {
//            if (fourthMoment == null) {
//                initializeForthMomentMatrix(dataSet.getVariables());
//            }
//
//            double sxyzw = fourthMoment[x][y][z][w];
//
//            if (sxyzw == 0.0) {
//                sxyzw = sxyzw(x, y, z, w);
////                setForthMoment(x, y, z, w, sxyzw);
//            }
//
//            return sxyzw;
//        } else {
        return sxyzw(x, y, z, w);
//        }
    }

    /**
     * If using a covariance matrix or a correlation matrix, just returns the lookups. Otherwise calculates the
     * covariance.
     */
    private double sxy(Node _node1, Node _node2) {
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

    private double getDerivative(Node node1, Node node2, Node node3, Node node4, Node a, Node b) {
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

//    public void setCacheFourthMoments(boolean cacheFourthMoments) {
//        this.cacheFourthMoments = cacheFourthMoments;
//    }

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
            return a.hashCode() + b.hashCode();
        }

        public String toString() {
            return "Sigma(" + getA() + ", " + getB() + ")";
        }
    }

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

    private double sxy(double array1[], double array2[], int N) {
        int i;
        double sum = 0.0;

        for (i = 0; i < N; i++) {
//            sum += (array1[i] - meanX) * (array2[i] - meanY);
            sum += array1[i] * array2[i];
        }

        return (1.0 / N) * sum;
    }

//    private double mean(double array[], int N) {
//        int i;
//        double sum = 0;
//
//        for (i = 0; i < N; i++) {
//            sum += array[i];
//        }
//
//        return sum / N;
//    }
}



