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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.urchin;


import org.apache.commons.math3.util.FastMath;

/**
 * Provides the methods for computing mutual information between expression levels between genes and, for a given gene,
 * between points in time determined by a lag value. The methods implemented here follow the definitions given in the
 * paper "Reveal, a  General Reverse Engineering Algorithm for Inference of Genetic Network Architectures" by Liang,
 * Fuhrman and Somogyi, Pacific Symposium on Biocomputing 3:18-29 (1998).
 *
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public class RevealEvaluator {
    private int ngenes;   //The number of genes
    private int ntimes;   //The number of time steps

    private int[][] cases = new int[this.ntimes][this.ngenes];

    /**
     * <p>Constructor for RevealEvaluator.</p>
     *
     * @param cases an array of {@link int} objects
     */
    public RevealEvaluator(int[][] cases) {

        this.ntimes = cases.length;
        this.ngenes = cases[0].length;
        this.cases = cases;

    }

    /**
     * This method computes the cross tablulation (table) of values of a gene and its possible parent.  This should help
     * evaluate whether the two are related by a canalyzing function.  The third argument is the time lag between cause
     * and effect, as usual.
     *
     * @param child  a int
     * @param parent a int
     * @param lag    a int
     * @return an array of {@link int} objects
     */
    public int[][] crossTab(int child, int parent, int lag) {
        int[][] ns = new int[2][2];

        ns[0][0] = 0;
        ns[0][1] = 0;
        ns[1][0] = 0;
        ns[1][1] = 0;

        int j;
        for (int i = lag; i < this.ntimes; i++) {
            j = i - lag;
            ns[this.cases[i][child]][this.cases[j][parent]]++;
        }

        return ns;
    }

    /**
     * This method computes the mutual information between a gene and a set of presumptive causes (other genes).  There
     * must be at least one cause but there may be only one.  The third argument is the time lag between the cause(s)
     * and the effect.  See Fig. 5 in the Liang et al. paper.
     *
     * @param child   a int
     * @param parents an array of {@link int} objects
     * @param lag     a int
     * @return a double
     */
    public double mutualInformation(int child, int[] parents, int lag) {

        //make sure child is not in parents etc.

        double M;

        //H(child)
        int[] c = new int[this.ntimes - lag];
        for (int i = lag; i < this.ntimes; i++) {
            c[i - lag] = this.cases[i][child];
        }
        double hchild = entropy(c);

        int[] p1 = new int[this.ntimes - lag];  //1 parent
        int[][] pm = new int[parents.length][this.ntimes - lag];  //multiple parents

        //H(parents)
        double hparents;

        for (int i = 0; i < this.ntimes - lag; i++) {
            p1[i] = this.cases[i][parents[0]];
        }
        hparents = entropy(p1);

        if (parents.length > 1) {
            for (int i = 0; i < this.ntimes - lag; i++) {
                for (int j = 1; j < parents.length; j++) {
                    pm[j - 1][i] = this.cases[i][parents[j]];
                }
            }
            hparents = jointEntropy(p1, pm);
        }

        //H(child + parents)
        double hjoint;
        if (parents.length == 1) {
            hjoint = jointEntropy(c, p1);
        } else {
            int[][] p1pm = new int[parents.length][this.ntimes - lag];
            for (int i = 0; i < this.ntimes - lag; i++) {
                p1pm[0][i] = p1[i];
                for (int j = 0; j < parents.length - 1; j++) {
                    p1pm[j + 1][i] = pm[j][i];
                }
            }
            hjoint = jointEntropy(c, p1pm);
        }

        M = hchild + hparents - hjoint;
        return M;
    }

    /**
     * This method computes the mutual information between a gene and a set of presumptive causes (other genes).  There
     * must be at least one cause but there may be only one.  The third argument is the time lag between the cause(s)
     * and the effect.  See Fig. 5 in the Liang et al. paper.
     *
     * @param child   a int
     * @param parents an array of {@link int} objects
     * @param lags    an array of {@link int} objects
     * @return a double
     */
    public double mutualInformation(int child, int[] parents, int[] lags) {

        //make sure child is not in parents etc.

        double M;

        int maxlag = 0;
        for (int lag : lags) {
            if (lag > maxlag) {
                maxlag = lag;
            }
        }

        //H(child)
        int[] c = new int[this.ntimes - maxlag];
        for (int i = maxlag; i < this.ntimes; i++) {
            c[i - maxlag] = this.cases[i][child];
        }
        double hchild = entropy(c);

        int[] p1 = new int[this.ntimes - maxlag];  //1 parent
        int[][] pm =
                new int[parents.length][this.ntimes - maxlag];  //multiple parents

        //for(int i = 0; i < parents.length; i++) {
        //  start[i] = lags[i] - maxlag;
        //  end[i] = ntimes - lags[i];
        //}

        //H(parents)
        double hparents;

        for (int i = 0; i < this.ntimes - maxlag; i++) {
            p1[i] = this.cases[maxlag - lags[0] + i][parents[0]];
        }
        hparents = entropy(p1);

        if (parents.length > 1) {
            for (int i = 0; i < this.ntimes - maxlag; i++) {
                for (int j = 1; j < parents.length; j++) {
                    pm[j - 1][i] = this.cases[maxlag - lags[j] + i][parents[j]];
                }
            }
            hparents = jointEntropy(p1, pm);
        }

        //H(child + parents)
        double hjoint;
        if (parents.length == 1) {
            hjoint = jointEntropy(c, p1);
        } else {
            int[][] p1pm = new int[parents.length][this.ntimes - maxlag];
            for (int i = 0; i < this.ntimes - maxlag; i++) {
                p1pm[0][i] = p1[i];
                for (int j = 0; j < parents.length - 1; j++) {
                    p1pm[j + 1][i] = pm[j][i];
                }
            }
            hjoint = jointEntropy(c, p1pm);
        }

        M = hchild + hparents - hjoint;
        return M;
    }

    /**
     * This method computes the entropy of a binary signal stored in an int array.  It assume that the values in the
     * array are 0's and 1's. (Actually 1's may be replaced by any nonzero value)  See page 20 of the Liang paper.
     *
     * @param x an array of {@link int} objects
     * @return a double
     */
    public double entropy(int[] x) {
        double h = 0.0;
        int n = x.length;
        double ln2 = FastMath.log(2.0);

        int n0 = 0;
        for (int j : x) {
            if (j == 0) {
                n0++;
            }
        }

        double p;
        if (n0 == 0 || n0 == n) {
            return h;
        } else {
            p = (double) n0 / (double) n;
            h = -(p * FastMath.log(p) + (1.0 - p) * FastMath.log(1.0 - p)) / ln2;
        }
        return h;
    }

    /**
     * This method implements the same definition of entropy as above but this specialized version is intended to be
     * used by the mutualInformation method (viz).  This method computes the entropy of a gene's binarized expressions
     * from a point in time until the end of the data signal.  This is useful in the normalization of the mutual
     * information.
     *
     * @param g   a int
     * @param lag a int
     * @return a double
     */
    public double entropy(int g, int lag) {
        double h = 0.0;
        int n = this.cases.length - lag;

        double ln2 = FastMath.log(2.0);  //TODO:  move outside

        int n0 = 0;
        for (int i = 0; i < n; i++) {
            if (this.cases[i + lag][g] == 0) {
                n0++;
            }
        }

        double p;
        if (n0 == 0 || n0 == n) {
            return h;
        } else {
            p = (double) n0 / (double) n;
            h = -(p * FastMath.log(p) + (1.0 - p) * FastMath.log(1.0 - p)) / ln2;
        }
        return h;
    }

    /**
     * This method computes the joint entropy of two arrays. The values stored in those arrays are assumed to be
     * restricted to {0,1}.
     *
     * @param x an array of {@link int} objects
     * @param y an array of {@link int} objects
     * @return a double
     */
    public double jointEntropy(int[] x, int[] y) {
        double h;
        int[][] ns = new int[2][2];
        int n = x.length;
        double ln2 = FastMath.log(2.0);

        ns[0][0] = 0;
        ns[0][1] = 0;
        ns[1][0] = 0;
        ns[1][1] = 0;

        for (int i = 0; i < n; i++) {
            ns[x[i]][y[i]]++;
        }

        //int ntot = ns[0][0] + ns[0][1] + ns[1][0] + ns[1][1];

        double[][] p = new double[2][2];
        double lp00;
        double lp01;
        double lp10;
        double lp11;

        p[0][0] = (double) ns[0][0] / (double) n;
        p[0][1] = (double) ns[0][1] / (double) n;
        p[1][0] = (double) ns[1][0] / (double) n;
        p[1][1] = (double) ns[1][1] / (double) n;

        if (p[0][0] == 0.0) {
            lp00 = 0.0;
        } else {
            lp00 = -p[0][0] * FastMath.log(p[0][0]);
        }
        if (p[0][1] == 0.0) {
            lp01 = 0.0;
        } else {
            lp01 = -p[0][1] * FastMath.log(p[0][1]);
        }
        if (p[1][0] == 0.0) {
            lp10 = 0.0;
        } else {
            lp10 = -p[1][0] * FastMath.log(p[1][0]);
        }
        if (p[1][1] == 0.0) {
            lp11 = 0.0;
        } else {
            lp11 = -p[1][1] * FastMath.log(p[1][1]);
        }

        h = lp00 + lp01 + lp10 + lp11;
        h /= ln2;
        return h;
    }

    /**
     * <p>jointEntropy.</p>
     *
     * @param x an array of {@link int} objects
     * @param y an array of {@link int} objects
     * @return a double
     */
    public double jointEntropy(int[] x, int[][] y) {
        double h = 0.0;
        int m = y.length;
        //System.out.println("m = " + m);
        int n = x.length;
        double ln2 = FastMath.log(2.0);

        if (y[0].length != n) {
            System.out.println("x and rows of y and must have same length");
            System.exit(0);
        }

        int nyconfigs = 1;
        for (int j = 0; j < m; j++) {
            nyconfigs *= 2;              //number of configurations of ys
        }

        int nconfigs = 2 * nyconfigs;        //x also makes a config

        //System.out.println("nconfigs = " + nconfigs);
        int[] counts = new int[nconfigs];
        for (int i = 0; i < nconfigs; i++) {
            counts[i] = 0;
        }

        int config;
        int ntot = 0;
        int power;
        for (int i = 0; i < n; i++) {
            power = 1;
            config = x[i] * power;
            for (int[] ints : y) {
                power *= 2;
                config += ints[i] * power;
            }
            counts[config]++;
            ntot++;
            //System.out.println("For i = " + i + " config = " + config);
        }

        double p;
        for (int i = 0; i < nconfigs; i++) {
            //System.out.println("i = " + i + " count = " + counts[i]);
            p = (double) counts[i] / (double) ntot;
            if (p == 0.0) {
                continue;
            }
            h -= p * FastMath.log(p);
        }

        h /= ln2;
        return h;
    }

    /**
     * Computes a byte vector which corresponds to the argument ind.  rep[0] is the high order bit. E.g.  if n=3 and
     * ind=6 the vector will be (1, 1, 0).
     *
     * @param ind a int
     * @param n   a int
     * @return an array of {@link byte} objects
     */
    public byte[] booleanRepresentation(int ind, int n) {
        byte[] rep = new byte[n];

        for (int i = 0; i < n; i++) {
            rep[i] = (byte) 0;
        }

        for (int i = 0; i < n; i++) {
            int rem = ind % 2;
            if (rem == 1) {
                rep[n - i - 1] = (byte) 1;
                ind -= 1;
            }
            ind /= 2;
        }

        return rep;
    }

}




