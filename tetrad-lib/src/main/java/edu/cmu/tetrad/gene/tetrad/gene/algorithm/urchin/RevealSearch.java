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


/**
 * This class contains as a member variable (cases) the time series data stored
 * in an int array of microarray measurements.  The values are assumed to have
 * been binarized.  The columns of the array correspond to genes and the rows
 * correspond to times.  The class contains methods for conducting searches for
 * causal links where a genes expression is regulated by a set of parent genes.
 * The search method calls the methods for computing entropies and measures of
 * mutual information implemented in the RevealEvaluator class.  This approach
 * is described by Liang et al.
 *
 * @author Frank Wimberly
 */
public class RevealSearch {
    private int[][] cases;
    private int ngenes;
    private int ntimes;
    String[] names;
    RevealEvaluator re;

    public RevealSearch(int[][] cases, String[] names) {
        this.cases = cases;
        this.names = names;
        this.ntimes = cases.length;
        this.ngenes = cases[0].length;
        this.re = new RevealEvaluator(cases);
    }

    /**
     * This method computes m/e values for all single regulators, pairs and
     * triples at a given time lag.  The m/e value is the ratio of the mutual
     * information between the regulated gene and the regulator set divided by
     * the entropy of the regulated gene.
     */
    public RevealOutputGraph exhaustiveSearch(int lag) {

        double[] entropies = new double[ngenes];
        for (int g = 0; g < ngenes; g++) {
            //int[] x = new int[ntimes - lag];
            //for(int i = 0; i < ntimes - lag; i++)
            //  x[i] = cases[i + lag][g];
            //entropies[g] = re.entropy(x);
            entropies[g] = re.entropy(g, lag);
        }

        //Report crosstabulations among all genes
        int[][] ct = null;
        for (int child = 0; child < ngenes; child++) {
            System.out.println("Crosstabs of gene " + child);
            for (int parent = 0; parent < ngenes; parent++) {
                if (parent == child) {
                    continue;
                }
                ct = re.crossTab(child, parent, lag);
                System.out.println("with parent " + parent + " at lag " + lag);
                System.out.println("  " + ct[0][0] + " " + ct[0][1]);
                System.out.println("  " + ct[1][0] + " " + ct[1][1]);
            }
        }

        int[][] parents = new int[ngenes][];
        double[] best1 = new double[ngenes];
        double[] best2 = new double[ngenes];
        double[] best3 = new double[ngenes];

        //One parent cases
        int[] p = new int[1];  //TODO:  Make p parents
        for (int child = 0; child < ngenes; child++) {
            System.out.println("For gene " + child);

            best1[child] = -1.0;

            for (int i = 0; i < ngenes; i++) {
                //if(i == child) continue;
                p[0] = i;
                double m = re.mutualInformation(child, p, lag);
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
        for (int child = 0; child < ngenes; child++) {
            System.out.println("For gene " + child);

            best2[child] = -1.0;

            for (int p1 = 0; p1 < ngenes; p1++) {
                for (int p2 = 0; p2 < ngenes && p1 != p2; p2++) {
                    pp[0] = p1;
                    pp[1] = p2;

                    double mm = re.mutualInformation(child, pp, lag);
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
        for (int child = 0; child < ngenes; child++) {

            best3[child] = -1.0;

            System.out.println("For gene " + child);
            for (int p1 = 0; p1 < ngenes; p1++) {
                for (int p2 = 0; p2 < ngenes && p2 != p1; p2++) {
                    for (int p3 = 0; p3 < ngenes && p3 != p2 && p3 != p1; p3++)
                    {
                        ppp[0] = p1;
                        ppp[1] = p2;
                        ppp[2] = p3;
                        double mmm = re.mutualInformation(child, ppp, lag);
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

        for (int gene = 0; gene < ngenes; gene++) {
            System.out.println("Parents of gene " + gene);
            for (int par = 0; par < parents[gene].length; par++) {
                System.out.print(parents[gene][par] + " ");
            }
            System.out.println();
        }

        int[][] lags = new int[ngenes][];
        for (int i = 0; i < ngenes; i++) {
            int k = parents[i].length;
            lags[i] = new int[k];
            for (int j = 0; j < k; j++) {
                lags[i][j] = 1;
            }
        }

        RevealOutputGraph rog = new RevealOutputGraph(ngenes, parents, lags,
                names, "TestReveal");
        return rog;
    }

    /**
     * This method computes m/e values for all single regulators, pairs and
     * triples between two time lags (inclusively).  The resulting output graph
     * will specify the parents of each gene as well as the time lag associated
     * with each causal link.
     */
    public RevealOutputGraph exhaustiveSearch(int lag1, int lag2) {

        if (lag2 <= lag1 || lag1 <= 0) {
            System.out.println(
                    "2nd lag must be greater than 1st which must be pos");
            return null;
        }

        int[] parents1 = new int[ngenes];
        int[] lags1 = new int[ngenes];
        int[][] parents2 = new int[ngenes][];
        int[][] lags2 = new int[ngenes][];
        int[][] parents3 = new int[ngenes][];
        int[][] lags3 = new int[ngenes][];
        int[] nparents = new int[ngenes];

        int[][] parents = new int[ngenes][];
        int[][] lags = new int[ngenes][];

        for (int gchild = 0; gchild < ngenes; gchild++) {

            double bestme = -1000.0;

            //One parent cases
            int[] parent1 = new int[1];
            for (int gparent = 0; gparent < ngenes; gparent++) {
                parent1[0] = gparent;

                for (int lag = lag1; lag <= lag2; lag++) {
                    double entropyChild = re.entropy(gchild, lag);
                    double mutualInf =
                            re.mutualInformation(gchild, parent1, lag);
                    double mOverE = mutualInf / entropyChild;
                    if (mOverE > bestme) {
                        bestme = mOverE;
                        parents1[gchild] = gparent;
                        lags1[gchild] = lag;
                        nparents[gchild] = 1;
                    }
                }
            }

            //Two parent cases
            int[] parent2 = new int[2];
            parents2[gchild] = new int[2];
            lags2[gchild] = new int[2];
            for (int gparent1 = 0; gparent1 < ngenes; gparent1++) {
                for (int lagp1 = lag1; lagp1 <= lag2; lagp1++) {
                    for (int gparent2 = 0; gparent2 < ngenes &&
                            gparent1 != gparent2; gparent2++) {
                        for (int lagp2 = lag1; lagp2 <= lag2; lagp2++) {
                            parent2[0] = gparent1;
                            parent2[1] = gparent2;
                            int[] lagsa = new int[2];
                            lagsa[0] = lagp1;
                            lagsa[1] = lagp2;
                            int lag = (lagp1 > lagp2) ? lagp1 : lagp2;
                            //lag = 1;
                            double entropyChild = re.entropy(gchild, lag);
                            double mutualInf = re.mutualInformation(gchild,
                                    parent2, lagsa);
                            double mOverE = mutualInf / entropyChild;
                            if (mOverE > bestme) {
                                bestme = mOverE;
                                //parents2[gchild] = new int[2];
                                parents2[gchild][0] = gparent1;
                                parents2[gchild][1] = gparent2;
                                lags2[gchild][0] = lagp1;
                                lags2[gchild][1] = lagp2;
                                nparents[gchild] = 2;
                            }
                        }
                    }
                }
            }

            //Three parent cases
            int[] parent3 = new int[3];
            parents3[gchild] = new int[3];
            lags3[gchild] = new int[3];
            for (int gparent1 = 0; gparent1 < ngenes; gparent1++) {
                for (int lagp1 = lag1; lagp1 <= lag2; lagp1++) {
                    for (int gparent2 = 0; gparent2 < ngenes; gparent2++) {
                        for (int lagp2 = lag1; lagp2 <= lag2; lagp2++) {
                            for (int gparent3 = 0;
                                    gparent3 < ngenes; gparent3++) {
                                for (int lagp3 = lag1; lagp3 <= lag2; lagp3++) {
                                    parent3[0] = gparent1;
                                    parent3[1] = gparent2;
                                    parent3[2] = gparent3;
                                    int[] lagsa = new int[3];
                                    lagsa[0] = lagp1;
                                    lagsa[1] = lagp2;
                                    lagsa[2] = lagp3;
                                    int lag = (lagp1 > lagp2) ? lagp1 : lagp2;
                                    lag = (lag > lagp3) ? lag : lagp3;
                                    double entropyChild =
                                            re.entropy(gchild, lag);
                                    double mutualInf = re.mutualInformation(
                                            gchild, parent3, lagsa);
                                    double mOverE = mutualInf / entropyChild;
                                    if (mOverE > bestme) {
                                        bestme = mOverE;
                                        //parents3[gchild] = new int[3];
                                        parents3[gchild][0] = gparent1;
                                        parents3[gchild][1] = gparent2;
                                        parents3[gchild][2] = gparent3;
                                        lags3[gchild][0] = lagp1;
                                        lags3[gchild][1] = lagp2;
                                        lags3[gchild][2] = lagp3;
                                        nparents[gchild] = 3;
                                    }

                                }
                            }
                        }
                    }
                }
            }

            System.out.println("For gene " + gchild + ":");
            if (nparents[gchild] == 1) {
                parents[gchild] = new int[1];
                lags[gchild] = new int[1];
                System.out.println("best parent, lag = " + parents1[gchild] +
                        " " + lags1[gchild]);
                parents[gchild][0] = parents1[gchild];
                lags[gchild][0] = lags1[gchild];
            }
            else if (nparents[gchild] == 2) {
                parents[gchild] = new int[2];
                lags[gchild] = new int[2];
                System.out.println("best parents, lags = " +
                        parents2[gchild][0] + " " + parents2[gchild][1] + " " +
                        " " + lags2[gchild][0] + " " + lags2[gchild][1]);
                parents[gchild][0] = parents2[gchild][0];
                parents[gchild][1] = parents2[gchild][1];
                lags[gchild][0] = lags2[gchild][0];
                lags[gchild][1] = lags2[gchild][1];
            }
            else if (nparents[gchild] == 3) {
                parents[gchild] = new int[3];
                lags[gchild] = new int[3];
                System.out.println("best parents, lags = " +
                        parents3[gchild][0] + " " + parents3[gchild][1] + " " +
                        parents3[gchild][2] + " " + lags3[gchild][0] + " " +
                        lags3[gchild][1] + " " + lags3[gchild][2]);
                parents[gchild][0] = parents3[gchild][0];
                parents[gchild][1] = parents3[gchild][1];
                parents[gchild][2] = parents3[gchild][2];
                lags[gchild][0] = lags3[gchild][0];
                lags[gchild][1] = lags3[gchild][1];
                lags[gchild][2] = lags3[gchild][2];
            }

        }

        RevealOutputGraph rog = new RevealOutputGraph(ngenes, parents, lags,
                names, "TestReveal");
        return rog;

    }

}








