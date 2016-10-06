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

public class LTestSsys1 {
    public static void main(String argv[]) {

        String fileName = argv[0];

        InputStream s;
        StringTokenizer st;

        int ngenes = 5;
        int ntimes = 400;
        int nrecords = 5;
        int nchips = 4;

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

        double[][] gene = new double[ntimes][ngenes];
        double[][] deriv = new double[ntimes][ngenes];
        double[] sum = new double[ngenes];
        double baseLevel = 15.0;

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        double min = 1000.0;

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
                if (gene[j][g] < min) {
                    min = gene[j][g];
                }

                if (j != 0) {

                    deriv[j][g] = (gene[j][g] - gene[j - 1][g]) / 10.0;
                }

            }

            /*  DEBUG Prints
            System.out.println("At time " + j);
            for(int g = 0; g < ngenes; g++) {
              String expression = nf.format(gene[j][g]);
              System.out.print(expression + " ");
            }
            System.out.println();
            */
        }
        System.out.println("minimum value = " + min);

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
                    String g0 =
                            nf.format(Math.log(gene[t][regs[0]] + baseLevel));
                    String g1 =
                            nf.format(Math.log(gene[t][regs[1]] + baseLevel));
                    String g2 =
                            nf.format(Math.log(gene[t][regs[2]] + baseLevel));
                    String g3 =
                            nf.format(Math.log(gene[t][regs[3]] + baseLevel));
                    String g4 =
                            nf.format(Math.log(gene[t][regs[4]] + baseLevel));

                    if (deriv[t][g] > 0.35) {

                        System.out.println("lnamlnb+" + g0 + "gmh0+" + g1 +
                                "gmh1+" + g2 + "gmh2+" + g3 + "gmh3+" + g4 +
                                "gmh4 > 0");
                    }
                    else if (deriv[t][g] < -0.35) {
                        System.out.println("lnamlnb+" + g0 + "gmh0+" + g1 +
                                "gmh1+" + g2 + "gmh2+" + g3 + "gmh3+" + g4 +
                                "gmh4 < 0");
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





