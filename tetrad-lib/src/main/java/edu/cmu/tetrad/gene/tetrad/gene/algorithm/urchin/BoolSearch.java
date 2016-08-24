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
 * The methods implemented here are described in the paper "Algorithms for
 * Inferring Qualitative Models of Biological Networks" by Akutsu, Miyano and
 * Kuhara in Proceeding of the Pacific Symposium on Biocomputing (2000).
 *
 * @author Frank Wimberly
 */
public class BoolSearch {
    private int[][] cases;
    private int ngenes;
    private int ntimes;
    String[] names;

    public BoolSearch(int[][] cases, String[] names) {
        this.cases = cases;
        this.names = names;
        this.ntimes = cases.length;
        this.ngenes = cases[0].length;
    }

    /**
     * Implements the BOOL-2 algorithm of Akutsu, et al, found in section 2.2 of
     * their paper "Algorithms for Inferring Qualitative Models of Biological
     * Networks". </p> The int k is the number of number of regulators of a
     * given gene and corresponds to K in the paper.
     */
    public RevealOutputGraph bool2(int k) {

        int[][] parents = new int[ngenes][];
        int[][] lags = new int[ngenes][];
        int[] f = new int[ngenes];

        int numberTotalInputs = 1;
        for (int i = 0; i < ngenes; i++) {
            numberTotalInputs *= 2;
        }

        int numberInputCombinations = 1;
        for (int i = 0; i < k; i++) {
            numberInputCombinations *= 2;
        }

        double theta0 =
                1.0 / (2.0 * numberInputCombinations * numberInputCombinations);
        //theta = 0.1;
        double theta = theta0;
        System.out.println("Theta = " + theta);

        int numberBooleanFunctions = 1;
        for (int i = 0; i < numberInputCombinations; i++) {
            numberBooleanFunctions *= 2;
        }

        System.out.println("nrows = " + numberInputCombinations +
                " number functions = " + numberBooleanFunctions);

        //for i = 1 to n do...
        for (int gchild = 0; gchild < ngenes; gchild++) {
            System.out.println("Child gene " + gchild);
            TH:
            for (int m = 1; m <= 60; m++) {
                theta = theta0 * m;
                System.out.println("Theta = " + theta);
                int count = 0;
                int[] pars = new int[k];
                pars[0] = -1;
                pars[1] = -1;
                pars[2] = -1;

                //For all combinations of k nodes do...
                //The array inputs has a 1 in position i if inputs[i] is a parent
                for (int input = 0; input < numberTotalInputs; input++) {
                    byte[] inputs = booleanRepresentation(input, ngenes);
                    if (sumBits(inputs) == k) {
                        int j = 0;
                        for (int i = 0; i < ngenes; i++) {
                            if (inputs[i] == 1) {
                                pars[j] = i;
                                j++;
                            }
                        }

                        //for(int parent = 0; parent < k; parent++)
                        //  System.out.println("Parent " + parent + " = " + pars[parent]);
                    }
                    else {
                        //System.out.println("Not k bits for " + input);
                        continue;
                    }

                    //System.out.println("pars " + pars[0] + " " + pars[1] + " " + pars[2]);
                    //For every boolean function with k inputs do...
                    for (int function = 0;
                            function < numberBooleanFunctions; function++) {
                        byte[] fi = booleanRepresentation(function,
                                numberInputCombinations);
                        int mismatch = 0;
                        //for j = 1 to m do ...
                        for (int j = 0; j < ntimes - 1; j++) {
                            //input = values of k genes at time j - 1
                            boolean match = true;
                            int argument = 0;
                            int power = 1;
                            for (int i = 0; i < k; i++) {
                                argument += power * cases[j][pars[k - i - 1]];
                                //argument += power*cases[j][pars[i]];
                                power *= 2;
                            }
                            int finput = fi[argument];
                            //if Oj(vi) != f(Ij(vi1),...,Ij(vik)) then...
                            if (finput != cases[j + 1][gchild]) {
                                mismatch++;
                            }
                        }

                        if (mismatch < theta * ntimes) {
                            System.out.println("update parents");
                            parents[gchild] = new int[k];
                            lags[gchild] = new int[k];
                            f[gchild] = function;
                            for (int i = 0; i < k; i++) {
                                parents[gchild][i] = pars[i];
                                lags[gchild][i] = 1;
                            }
                            count = 1;          //TEST
                            break TH;        //TEST
                            //count++;            TEST
                        }
                    }

                }
                if (count != 1) {
                    System.out.println(
                            "Regulators not identified count = " + count);
                    parents[gchild] = new int[0];
                    lags[gchild] = new int[0];
                }
                else {
                    System.out.println("Regulators are:  ");
                    for (int i = 0; i < parents[gchild].length; i++) {
                        System.out.println(
                                "  i = " + i + " par = " + parents[gchild][i]);
                    }
                }
            }
            //if(count != 1) {
            //  System.out.println("Regulators not identified count = " + count);
            //  parents[gchild] = new int[0];
            //  lags[gchild] = new int[0];;
            //}
            //else {
            System.out.println("regulators are:  ");
            for (int i = 0; i < parents[gchild].length; i++) {
                System.out.println(
                        "  i = " + i + " par = " + parents[gchild][i]);
            }
            //}

        }
        System.out.println("Returning");
        return null;
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

    public int sumBits(byte[] b) {
        int sum = 0;

        for (int i = 0; i < b.length; i++) {
            sum += b[i];
        }

        return sum;
    }

}








