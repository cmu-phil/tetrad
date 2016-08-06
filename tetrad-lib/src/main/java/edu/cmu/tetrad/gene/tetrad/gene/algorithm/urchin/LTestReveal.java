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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.urchin;

import java.io.*;
import java.util.StringTokenizer;

public class LTestReveal {
    static int ngenes = 6;
    static int ntimes = 400;

    static int[][] cases = new int[ntimes][ngenes];

    public static void main(String[] argv) {

        String fileName = argv[0];

        InputStream s;
        StringTokenizer st;

        try {
            s = new FileInputStream(fileName);
        }
        catch (IOException e) {
            System.out.println("Cannot open file " + fileName);
            return;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(s));
        for (int k = 0; k < ntimes; k++) {
            try {
                st = new StringTokenizer(in.readLine());
                for (int j = 0; j < ngenes; j++) {
                    cases[k][j] = Integer.parseInt(st.nextToken("\t"));
                }
            }
            catch (IOException e) {
                System.out.println("Read error in " + fileName);
                return;
            }
        }

        System.out.println("case 0 " + cases[0][0] + " " + cases[0][1] + " " +
                cases[0][2] + " " + cases[0][3] + " " + cases[0][4]);
        for (int k = 0; k < ntimes; k++) {
            for (int j = 0; j < ngenes; j++) {
                if (cases[k][j] == -1) {
                    cases[k][j] = 0;
                }
            }
        }

        int lag = 1;
        //One parent cases
        int[] p = new int[1];
        for (int child = 0; child < ngenes; child++) {
            System.out.println("For gene " + child);
            for (int i = 0; i < ngenes; i++) {
                //if(i == child) continue;
                p[0] = i;
                double m = mutualInformation(child, p, lag);
                System.out.println("for parent = " + i + " m = " + m);
            }
        }

        //Two parent cases
        int[] pp = new int[2];
        for (int child = 0; child < ngenes; child++) {
            System.out.println("For gene " + child);
            for (int p1 = 0; p1 < ngenes; p1++) {
                for (int p2 = 0; p2 < ngenes && p1 != p2; p2++) {
                    pp[0] = p1;
                    pp[1] = p2;

                    double mm = mutualInformation(child, pp, lag);
                    System.out.println(
                            "for parents = " + p1 + "," + p2 + " m = " + mm);
                }
            }
        }

        //Three parent cases
        int[] ppp = new int[3];
        for (int child = 0; child < ngenes; child++) {
            System.out.println("For gene " + child);
            for (int p1 = 0; p1 < ngenes; p1++) {
                for (int p2 = 0; p2 < ngenes && p2 != p1; p2++) {
                    for (int p3 = 0; p3 < ngenes && p3 != p2 && p3 != p1; p3++)
                    {
                        ppp[0] = p1;
                        ppp[1] = p2;
                        ppp[2] = p3;
                        double mmm = mutualInformation(child, ppp, lag);
                        System.out.println("for parents = " + p1 + "," + p2 +
                                "," + p3 + " m = " + mmm);
                    }
                }
            }
        }
    }

    public static double mutualInformation(int child, int[] parents, int lag) {

        //make sure child is not in parents etc.

        double M = 0.0;

        //H(child)
        int[] c = new int[ntimes - lag];
        for (int i = lag; i < ntimes; i++) {
            c[i - lag] = cases[i][child];
        }
        //double hchild = entropy(c);
        double hchild = entropy(child, lag);

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

    public static double entropy(int[] x) {
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

    public static double entropy(int g, int lag) {
        double h = 0.0;
        int n = cases.length - lag;

        double ln2 = Math.log(2.0);

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

    public static double jointEntropy(int[] x, int[] y) {
        double h = 0.0;
        int[][] ns = new int[2][2];
        int n = x.length;
        double ln2 = Math.log(2.0);

        ns[0][0] = 0;
        ns[0][1] = 0;
        ns[1][0] = 0;
        ns[1][1] = 0;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                ns[x[i]][y[i]]++;
            }
        }

        int ntot = ns[0][0] + ns[0][1] + ns[1][0] + ns[1][1];

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

    public static double jointEntropy(int[] x, int[][] y) {
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




