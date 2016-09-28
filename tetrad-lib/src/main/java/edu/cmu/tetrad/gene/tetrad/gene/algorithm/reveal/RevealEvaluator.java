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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.reveal;


/**
 * Provides the methods for computing mutual information between expression
 * levels between genes and, for a given gene, between points in time determined
 * by a lag value. </p> The methods implemented here follow the definitions
 * given in the paper "Reveal, a  General Reverse Engineering Algorithm for
 * Inference of Genetic Network Architectures" by Liang, Fuhrman and Somogyi,
 * Pacific Symposium on Biocomputing 3:18-29 (1998).
 *
 * @author Frank Wimberly
 */
public class RevealEvaluator {
    private int ngenes;   //The number of genes
    private int ntimes;   //The number of time steps

    private int[][] cases = new int[ntimes][ngenes];

    public RevealEvaluator(int[][] cases) {

        this.ntimes = cases.length;
        this.ngenes = cases[0].length;
        this.cases = cases;

    }

    /**
     * This method computes the cross tablulation (table) of values of a gene
     * and its possible parent.  This should help evaluate whether the two are
     * related by a canalyzing function.  The third argument is the time lag
     * between cause and effect, as usual.
     */
    public int[][] crossTab(int child, int parent, int lag) {
        int[][] ns = new int[2][2];
        //        int n = ntimes - lag;

        ns[0][0] = 0;
        ns[0][1] = 0;
        ns[1][0] = 0;
        ns[1][1] = 0;

        int j;
        for (int i = lag; i < ntimes; i++) {
            j = i - lag;
            ns[cases[i][child]][cases[j][parent]]++;
        }

        return ns;
    }

    /**
     * This method computes the mutual information between a gene and a set of
     * presumptive causes (other genes).  There must be at least one cause but
     * there may be only one.  The third argument is the time lag between the
     * cause(s) and the effect.  See Fig. 5 in the Liang et al. paper.
     */
    public double mutualInformation(int child, int[] parents, int lag) {

        //make sure child is not in parents etc.

        double M = 0.0;

        //H(child)
        int[] c = new int[ntimes - lag];
        for (int i = lag; i < ntimes; i++) {
            c[i - lag] = cases[i][child];
        }
        double hchild = entropy(c);

        int[] p1 = new int[ntimes - lag];  //1 parent
        int[][] pm = new int[parents.length][ntimes - lag];  //multiple parents

        //H(parents)
        double hparents = 0.0;

        for (int i = 0; i < ntimes - lag; i++) {
            p1[i] = cases[i][parents[0]];
        }
        hparents = entropy(p1);

        if (parents.length > 1) {
            for (int i = 0; i < ntimes - lag; i++) {
                for (int j = 1; j < parents.length; j++) {
                    pm[j - 1][i] = cases[i][parents[j]];
                }
            }
            hparents = jointEntropy(p1, pm);
        }

        //H(child + parents)
        double hjoint = 0.0;
        if (parents.length == 1) {
            hjoint = jointEntropy(c, p1);
        }
        else {
            int[][] p1pm = new int[parents.length][ntimes - lag];
            for (int i = 0; i < ntimes - lag; i++) {
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
     * This method computes the entropy of a binary signal stored in an int
     * array.  It assume that the values in the array are 0's and 1's. (Actually
     * 1's may be replaced by any nonzero value)  See page 20 of the Liang
     * paper.
     */
    public double entropy(int[] x) {
        double h = 0.0;
        int n = x.length;
        double ln2 = Math.log(2.0);

        int n0 = 0;
        for (int i = 0; i < n; i++) {
            if (x[i] == 0) {
                n0++;
            }
        }

        double p;
        if (n0 == 0 || n0 == n) {
            return h;
        }
        else {
            p = (double) n0 / (double) n;
            h = -(p * Math.log(p) + (1.0 - p) * Math.log(1.0 - p)) / ln2;
        }
        return h;
    }

    /**
     * This method implements the same definition of entropy as above but this
     * specialized version is intended to be used by the mutualInformation
     * method (viz).  This method computes the entropy of a gene's binarized
     * expressions from a point in time until the end of the data signal.  This
     * is useful in the normalization of the mutual information.
     */
    public double entropy(int g, int lag) {
        double h = 0.0;
        int n = cases.length - lag;

        double ln2 = Math.log(2.0);  //TODO:  move outside

        int n0 = 0;
        for (int i = 0; i < n; i++) {
            if (cases[i + lag][g] == 0) {
                n0++;
            }
        }

        double p;
        if (n0 == 0 || n0 == n) {
            return h;
        }
        else {
            p = (double) n0 / (double) n;
            h = -(p * Math.log(p) + (1.0 - p) * Math.log(1.0 - p)) / ln2;
        }
        return h;
    }

    /**
     * This method computes the joint entropy of two arrays. The values stored
     * in those arrays are assumed to be restricted to {0,1}.
     */
    public double jointEntropy(int[] x, int[] y) {
        double h = 0.0;
        int[][] ns = new int[2][2];
        int n = x.length;
        double ln2 = Math.log(2.0);

        ns[0][0] = 0;
        ns[0][1] = 0;
        ns[1][0] = 0;
        ns[1][1] = 0;

        for (int i = 0; i < n; i++) {
            ns[x[i]][y[i]]++;
        }

        //int ntot = ns[0][0] + ns[0][1] + ns[1][0] + ns[1][1];
        int ntot = n;

        double[][] p = new double[2][2];
        double lp00, lp01, lp10, lp11;

        p[0][0] = (double) ns[0][0] / (double) ntot;
        p[0][1] = (double) ns[0][1] / (double) ntot;
        p[1][0] = (double) ns[1][0] / (double) ntot;
        p[1][1] = (double) ns[1][1] / (double) ntot;

        if (p[0][0] == 0.0) {
            lp00 = 0.0;
        }
        else {
            lp00 = -p[0][0] * Math.log(p[0][0]);
        }
        if (p[0][1] == 0.0) {
            lp01 = 0.0;
        }
        else {
            lp01 = -p[0][1] * Math.log(p[0][1]);
        }
        if (p[1][0] == 0.0) {
            lp10 = 0.0;
        }
        else {
            lp10 = -p[1][0] * Math.log(p[1][0]);
        }
        if (p[1][1] == 0.0) {
            lp11 = 0.0;
        }
        else {
            lp11 = -p[1][1] * Math.log(p[1][1]);
        }

        h = lp00 + lp01 + lp10 + lp11;
        h /= ln2;
        return h;
    }

    public double jointEntropy(int[] x, int[][] y) {
        double h = 0.0;
        int m = y.length;
        //System.out.println("m = " + m);
        int n = x.length;
        double ln2 = Math.log(2.0);

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

        int config = 0;
        int ntot = 0;
        int power;
        for (int i = 0; i < n; i++) {
            power = 1;
            config = x[i] * power;
            for (int j = 0; j < m; j++) {
                power *= 2;
                config += y[j][i] * power;
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
            h -= p * Math.log(p);
        }

        h /= ln2;
        return h;
    }

    /**
     * Computes a byte vector which corresponds to the argument ind.  rep[0] is
     * the high order bit. E.g.  if n=3 and ind=6 the vector will be (1, 1, 0).
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




