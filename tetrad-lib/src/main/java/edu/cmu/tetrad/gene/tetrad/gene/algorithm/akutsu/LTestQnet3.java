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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.akutsu;

import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.NumberFormatUtil;

import java.io.*;
import java.text.NumberFormat;
import java.util.StringTokenizer;

public class LTestQnet3 {
    public static void main(String argv[]) {

        String fileName = argv[0];

        InputStream s;
        StringTokenizer st;

        int ngenes = 5;
        int ntimes = 400;
        int nrecords = 5;
        int nchips = 4;

        //        double[] chisq = {3.84, 5.99, 7.81, 9.49, 11.1, 12.6, 14.1, 15.5,
        //                          16.9, 18.3, 19.7, 21.0, 22.4, 23.7, 25.0, 26.3,
        //                          27.6, 28.9, 30.1, 31.4, 32.7, 33.9, 35.2, 36.4,
        //                          37.7, 38.9, 40.1, 41.3, 42.6, 43.8};

        double[][] cases = new double[4][2004];

        try {
            s = new FileInputStream(fileName);
        }
        catch (IOException e) {
            System.out.println("Cannot open file " + fileName);
            return;
        }

        //DataInputStream in = new DataInputStream(s);
        BufferedReader in = new BufferedReader(new InputStreamReader(s));
        for (int k = 0; k < nrecords; k++) {
            try {
                st = new StringTokenizer(in.readLine());
                if (k == 0) {
                    continue;
                }
                //                int idish = Integer.parseInt(st.nextToken("\t"));
                //                int ichip = Integer.parseInt(st.nextToken("\t"));
                for (int j = 0; j < ntimes * ngenes; j++) {
                    cases[k - 1][j] = Double.parseDouble(st.nextToken("\t"));
                }
            }
            catch (IOException e) {
                System.out.println("Read error in " + fileName);
                return;
            }
        }
        //System.out.println("Read " + cases[0][0] + " " + cases[1][0] + " " +
        //                  cases[2][0] + " " + cases[3][0]);

        double[][] gene = new double[ntimes][ngenes];
        double[][] deriv = new double[ntimes][ngenes];
        double[] sum = new double[ngenes];
        //double[] prevSum = new double [ngenes];

        for (int j = 0; j < ntimes; j++) {
            for (int g = 0; g < ngenes; g++) {
                int icol = j * ngenes + g;

                sum[g] = 0.0;

                for (int c = 0; c < nchips; c++)
                //sum[g] += cases[c][icol]*cases[c][icol];
                {
                    sum[g] += cases[c][icol];
                }

                gene[j][g] = sum[g];
                //if(sum[g] > 0.0) gene[j][g] = +1;
                //if(sum[g] > chisq[nchips - 1]) gene[j][g] = +1;
                //  else gene[j][g] = -1;

                //System.out.print(gene[j][g] + " ");


                if (j != 0) {

                    deriv[j][g] = (gene[j][g] - gene[j - 1][g]) / 10.0;
                }

            }
            /*
            if(j != 0) {
              System.out.println();
              for(int k = 0; k < ngenes; k++)
                System.out.print(deriv[j][k] + " ");
              System.out.println();
            }
            */
        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        for (int g = 0; g < ngenes; g++) {
            System.out.println("For gene " + g);
            int k = 5;
            ChoiceGenerator cg = new ChoiceGenerator(ngenes, k);
            int[] regs = new int[k];

            while ((regs = cg.next()) != null) {
                System.out.println("Sets of " + k + " regulators are:");
                System.out.println(regs[0] + " " + regs[1] + " " + regs[2] +
                        " " + regs[3] + " " + regs[4]);

                for (int t = 1; t < ntimes; t++) {
                    String g0 = nf.format(gene[t][regs[0]]);
                    String g1 = nf.format(gene[t][regs[1]]);
                    String g2 = nf.format(gene[t][regs[2]]);
                    String g3 = nf.format(gene[t][regs[3]]);
                    String g4 = nf.format(gene[t][regs[4]]);

                    if (deriv[t][g] > 0.3) {

                        System.out.println(g0 + "a0+" + g1 + "a1+" + g2 +
                                "a2+" + g3 + "a3+" + g4 + "a4+b > 0");
                    }
                    else if (deriv[t][g] < -0.3) {
                        System.out.println(g0 + "a0+" + g1 + "a1+" + g2 +
                                "a2+" + g3 + "a3+" + g4 + "a4+b< 0");
                    }
                }

            }
        }

        /*
        double[] p = new double[ngenes];
        for(int g = 0; g < ngenes; g++) {
          for(int j = 0; j < ntimes; j++)
            if(gene[j][g] > 0) p[g]++;
          p[g] /= ntimes;
          //System.out.println(" gene " + g + " p = " + p[g]);
        }
        */
    }

}





