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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.reveal;


/**
 * This class contains as a member variable (cases) the time series data stored in an int array of microarray
 * measurements.  The values are assumed to have been binarized.  The columns of the array correspond to genes and the
 * rows correspond to times.  The class contains methods for conducting searches for causal links where a genes
 * expression is regulated by a set of parent genes. The search method calls the methods for computing entropies and
 * measures of mutual information implemented in the RevealEvaluator class.  This approach is described by Liang et al.
 *
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public class RevealSearch {
    private final int ngenes;
    String[] names;
    RevealEvaluator re;

    /**
     * <p>Constructor for RevealSearch.</p>
     *
     * @param cases an array of {@link int} objects
     * @param names an array of {@link java.lang.String} objects
     */
    public RevealSearch(int[][] cases, String[] names) {
        this.names = names;
        int ntimes = cases.length;
        this.ngenes = cases[0].length;
        this.re = new RevealEvaluator(cases);
    }

    /**
     * This method computes m/e values for all single regulators, pairs and triples at a given time lag.  The m/e value
     * is the ratio of the mutual information between the regulated gene and the regulator set divided by the entropy of
     * the regulated gene.
     *
     * @param lag a int
     */
    public void exhaustiveSearch(int lag) {

        double[] entropies = new double[this.ngenes];
        for (int g = 0; g < this.ngenes; g++) {
            entropies[g] = this.re.entropy(g, lag);
        }

        //Report crosstabulations among all genes
        int[][] ct = null;
        for (int child = 0; child < this.ngenes; child++) {
            System.out.println("Crosstabs of gene " + child);
            for (int parent = 0; parent < this.ngenes; parent++) {
                if (parent == child) {
                    continue;
                }
                ct = this.re.crossTab(child, parent, lag);
                System.out.println("with parent " + parent + " at lag " + lag);
                System.out.println("  " + ct[0][0] + " " + ct[0][1]);
                System.out.println("  " + ct[1][0] + " " + ct[1][1]);
            }
        }

        int[][] parents = new int[this.ngenes][];
        double[] best1 = new double[this.ngenes];
        double[] best2 = new double[this.ngenes];
        double[] best3 = new double[this.ngenes];

        //One parent cases
        int[] p = new int[1];  //TODO:  Make p parents
        for (int child = 0; child < this.ngenes; child++) {
            System.out.println("For gene " + child);

            best1[child] = -1.0;

            for (int i = 0; i < this.ngenes; i++) {
                //if(i == child) continue;
                p[0] = i;
                double m = this.re.mutualInformation(child, p, lag);
                double me = m / entropies[child];
                if (me > best1[child]) {
                    best1[child] = me;
                    parents[child] = new int[1];
                    parents[child][0] = i;
                }
                System.out.println(
                        "for parent = " + i + " m = " + m + " m/e = " + me);
            }
        }

        //Two parent cases
        int[] pp = new int[2];
        for (int child = 0; child < this.ngenes; child++) {
            System.out.println("For gene " + child);

            best2[child] = -1.0;

            for (int p1 = 0; p1 < this.ngenes; p1++) {
                for (int p2 = 0; p2 < this.ngenes && p1 != p2; p2++) {
                    pp[0] = p1;
                    pp[1] = p2;

                    double mm = this.re.mutualInformation(child, pp, lag);
                    double mme = mm / entropies[child];
                    if (mme > best2[child] && mme > best1[child]) {
                        best2[child] = mme;
                        parents[child] = new int[2];
                        parents[child][0] = p1;
                        parents[child][1] = p2;
                    }
                    System.out.println("for parents = " + p1 + "," + p2 +
                                       " m = " + mm + " m/e = " + mme);
                }
            }
        }

        //Three parent cases
        int[] ppp = new int[3];
        for (int child = 0; child < this.ngenes; child++) {

            best3[child] = -1.0;

            System.out.println("For gene " + child);
            for (int p1 = 0; p1 < this.ngenes; p1++) {
                for (int p2 = 0; p2 < this.ngenes && p2 != p1; p2++) {
                    for (int p3 = 0; p3 < this.ngenes && p3 != p2 && p3 != p1; p3++) {
                        ppp[0] = p1;
                        ppp[1] = p2;
                        ppp[2] = p3;
                        double mmm = this.re.mutualInformation(child, ppp, lag);
                        double mmme = mmm / entropies[child];
                        if (mmme > best3[child] && mmme > best2[child] &&
                            mmme > best1[child]) {
                            best3[child] = mmme;
                            parents[child] = new int[3];
                            parents[child][0] = p1;
                            parents[child][1] = p2;
                            parents[child][2] = p3;
                        }
                        System.out.println("for parents = " + p1 + "," + p2 +
                                           "," + p3 + " m = " + mmm + " m/e = " + mmme);

                    }
                }
            }
        }

        for (int gene = 0; gene < this.ngenes; gene++) {
            System.out.println("Parents of gene " + gene);
            for (int par = 0; par < parents[gene].length; par++) {
                System.out.print(parents[gene][par] + " ");
            }
            System.out.println();
        }

        int[][] lags = new int[this.ngenes][];
        for (int i = 0; i < this.ngenes; i++) {
            int k = parents[i].length;
            lags[i] = new int[k];
            for (int j = 0; j < k; j++) {
                lags[i][j] = 1;
            }
        }

        RevealOutputGraph log = new RevealOutputGraph(this.ngenes, parents, lags,
                this.names, "TestReveal");
    }

}




