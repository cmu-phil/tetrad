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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.ideker;

import edu.cmu.tetrad.util.NumberFormatUtil;

import java.io.*;
import java.text.NumberFormat;
import java.util.StringTokenizer;

/**
 * <p>ReadControl class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ReadControl {

    /**
     * <p>Constructor for ReadControl.</p>
     */
    public ReadControl() {

    }

    /**
     * <p>main.</p>
     *
     * @param argv an array of {@link java.lang.String} objects
     */
    public static void main(String[] argv) {

        String fileName = argv[0];

        InputStream s;
        StringTokenizer st;

        final int ngenes = 9;
        final int nperturbations = 20;
        final int readingsPerPerturbation = 4;

        final int nrecords = 7;

        int[][] rawData = new int[ngenes][80];
        int[] nvaluesRecord = {10, 12, 12, 12, 12, 12, 10};
        int[] offSet = {0, 10, 22, 34, 46, 58, 70};
        String[] name = new String[ngenes];
        String[] code = new String[ngenes];

        double[][] expressions = new double[nperturbations][ngenes];
        //        int[][] binaryExpression = new int[10][ngenes];
        double[] meanExpression = new double[ngenes];

        try {
            s = new FileInputStream(fileName);
        } catch (IOException e) {
            System.out.println("Cannot open file " + fileName);
            return;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(s));
        for (int k = 0; k < nrecords; k++) {
            for (int j = 0; j < 10; j++) {  //Lines per record
                try {
                    st = new StringTokenizer(in.readLine());
                    if (j == 0) {
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
                    } else {
                        //int id = Integer.parseInt(st.nextToken(" "));
                        for (int i = 0; i < nvaluesRecord[k]; i++) {
                            rawData[j - 1][i + offSet[k]] =
                                    Integer.parseInt(st.nextToken(" "));
                        }
                        //System.out.println("first raw data = " + rawData[j-1][0]);
                    }
                } catch (IOException e) {
                    System.out.println("Read error in " + fileName);
                    return;
                }

            }

        }

        //for(int i = 0; i < nperturbations; i++) {
        //for(int i = 0; i < 10; i++) {  //Galactose
        for (int i = 10; i < 20; i++) {  //Raffinose
            for (int j = 0; j < ngenes; j++) {
                //System.out.println("i,j " + i + " " + j);
                double sum = 0;
                int n = 0;
                for (int k = 0; k < readingsPerPerturbation; k++) {
                    if (rawData[j][i * readingsPerPerturbation + k] > -900) {
                        n++;
                        sum += rawData[j][i * readingsPerPerturbation + k];
                    }
                }

                expressions[i][j] = sum / n;

            }
        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        //for(int p = 0; p < nperturbations; p++) {
        //for(int p = 0; p < 10; p++) {  //Galactose
        for (int p = 10; p < 20; p++) {  //Raffinose
            for (int g = 0; g < 5; g++) {
                String exp = nf.format(expressions[p][g]);
                System.out.print(exp + "  ");
            }
            System.out.println();
        }

        for (int g = 0; g < ngenes; g++) {
            double sum = 0.0;
            int n = 0;
            //for(int p = 0; p < nperturbations; p++) {
            //for(int p = 0; p < 10; p++) {  //Galactose
            for (int p = 10; p < 20; p++) {  //Raffinose
                //if(p-1 == g) continue;
                sum += expressions[p][g];
                n++;
            }
            meanExpression[g] = sum / n;
        }

        System.out.println("Mean expression for each gene");
        for (int g = 0; g < ngenes; g++) {
            String mean = nf.format(meanExpression[g]);
            System.out.print(mean + " ");
        }
        System.out.println();

    }
}






