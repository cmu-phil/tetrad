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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.ideker;

import edu.cmu.tetrad.util.NumberFormatUtil;

import java.io.*;
import java.text.NumberFormat;
import java.util.StringTokenizer;

public class ReadIdeker {
    public static void main(String argv[]) {

        String fileName = argv[0];

        InputStream s;
        StringTokenizer st;

        int ngenes = 9;
        int nperturbations = 20;
        int readingsPerPerturbation = 4;

        int nrecords = 7;

        int[][] rawData = new int[ngenes][80];
        int[] nvaluesRecord = {10, 13, 12, 13, 13, 14, 5};
        int[] offSet = {0, 10, 23, 35, 48, 61, 75};
        String[] name = new String[ngenes];
        String[] code = new String[ngenes];

        double[][] expressions = new double[nperturbations][ngenes];
        int[][] binaryExpression = new int[10][ngenes];

        //double[] meanExpression = new double[ngenes];
        //Galactose
        double[] meanExpression = {32514.058, 26663.615, 7421.692, 176.9,
                10602.458, 5491.0, 28358.983, 13640.675, 3877.975};
        //Raffinose
        //double[] meanExpression = {40800.233, 33582.275, 7749.183, 170.467,
        //                     9468.083, 6251.825, 38261.592, 16039.475, 4151.067};

        try {
            s = new FileInputStream(fileName);
        }
        catch (IOException e) {
            System.out.println("Cannot open file " + fileName);
            return;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(s));
        for (int k = 0; k < nrecords; k++) {
            for (int j = 0; j < 10; j++) {  //Lines per record
                try {
                    st = new StringTokenizer(in.readLine());
                    if (j == 0) {
                        //                        String lbl = st.nextToken(" ");
                        //System.out.println("label = " + lbl);
                        continue;
                    }
                    if (k == 0) {
                        //int id = Integer.parseInt(st.nextToken(" "));
                        code[j - 1] = st.nextToken(" ");
                        //System.out.println("code = " + code[j-1]);
                        name[j - 1] = st.nextToken(" ");
                        for (int i = 0; i < nvaluesRecord[k]; i++) {
                            rawData[j - 1][i + offSet[k]] =
                                    Integer.parseInt(st.nextToken(" "));
                        }
                    }
                    else {
                        //int id = Integer.parseInt(st.nextToken(" "));
                        for (int i = 0; i < nvaluesRecord[k]; i++) {
                            rawData[j - 1][i + offSet[k]] =
                                    Integer.parseInt(st.nextToken(" "));
                        }
                        //System.out.println("first raw data = " + rawData[j-1][0]);
                    }
                }
                catch (IOException e) {
                    System.out.println("Read error in " + fileName);
                    return;
                }

            }

        }

        for (int i = 0; i < nperturbations; i++) {
            for (int j = 0; j < ngenes; j++) {
                //System.out.println("i,j " + i + " " + j);
                double sum = 0;
                int n = 0;
                for (int k = 0; k < readingsPerPerturbation; k++) {
                    if (rawData[j][i * readingsPerPerturbation + k] > -900) {
                        n++;
                        //if(i ==0 && j ==0)
                        //System.out.println(k + " " + rawData[j][i*readingsPerPerturbation+k]);
                        sum += rawData[j][i * readingsPerPerturbation + k];
                    }
                }

                expressions[i][j] = sum / n;

            }
        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        for (int p = 0; p < nperturbations; p++) {
            for (int g = 0; g < 5; g++) {
                String exp = nf.format(expressions[p][g]);
                System.out.print(exp + "  ");
            }
            System.out.println();
        }

        /*  Compute the means from the experimental data
        for(int g = 0; g < ngenes; g++) {
          double sum = 0.0;
          int n = 0;
          for(int p = 0; p < nperturbations; p++) {
            if(p-1 == g) continue;
            sum += expressions[p][g];
            n++;
          }
          meanExpression[g] = sum/n;
        }
        */

        System.out.println("Mean expression for each gene");
        for (int g = 0; g < ngenes; g++) {
            String mean = nf.format(meanExpression[g]);
            System.out.print(mean + " ");
        }
        System.out.println();

        for (int g = 0; g < ngenes; g++) {
            for (int p = 0; p < 10; p++) {   //Galactose
                //for(int p = 10; p < 20; p++) {  //Raffinose
                int pp = p;  //Galactose
                //int pp = p - 10;  //Raffinose
                if (pp - 1 == g) {
                    binaryExpression[pp][g] = -1;
                }
                else if (expressions[p][g] > meanExpression[g]) {
                    binaryExpression[pp][g] = 1;
                }
                else {
                    binaryExpression[pp][g] = 0;
                }
            }
        }

        for (int p = 0; p < 10; p++) {
            for (int g = 0; g < ngenes; g++) {
                System.out.print(binaryExpression[p][g] + "\t");
            }
            System.out.println();
        }

        ItkPredictorSearch ips =
                new ItkPredictorSearch(ngenes, binaryExpression, name);

        for (int gene = 0; gene < ngenes; gene++) {
            ips.predictor(gene);
        }

    }
}






