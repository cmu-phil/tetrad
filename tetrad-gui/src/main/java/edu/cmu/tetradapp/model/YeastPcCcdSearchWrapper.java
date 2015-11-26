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

package edu.cmu.tetradapp.model;

//package

import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.IndTestCramerT;
import edu.cmu.tetrad.search.Pc;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>This version (in the urchin/newdata directory) differs from the version in
 * the batchsearch directory.  It will create both a doc file and an xls file.
 * The latter corresponds to the "verbose = false" segments of the earlier
 * version.  Also this version will deal with the situation where there are many
 * time steps, which raises question about the time interval used to decide
 * whether there is an adjacency between two variables (= gene/time pairs). </p>
 * <p>Also all the GA code has been removed.</p>
 */

public class YeastPcCcdSearchWrapper {
    public static void main(String[] args) {
        int[][] yeastReg = {{1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0},
                {0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0},
                {1, 1, 1, 0, 1, 1, 1, 0, 1, 0, 1},
                {0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1},
                {0, 0, 1, 0, 1, 1, 0, 0, 0, 0, 0},
                {0, 1, 1, 0, 1, 1, 1, 0, 1, 0, 0},
                {0, 0, 1, 0, 0, 1, 1, 0, 1, 0, 1},
                {0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0},
                {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1},
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0},
                {0, 0, 1, 1, 0, 0, 1, 0, 1, 0, 1}};

        List listOfNames = new ArrayList();

        // get the file containing the file names

        DataSet cds = null;

        try {
            DataReader reader = new DataReader();
            cds = reader.parseTabular(new File(args[0]));
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        String outfileprefix = args[1];
        String outverbosefile = outfileprefix + ".doc";
        String outsummaryfile = outfileprefix + ".xls";

        OutputStream s1 = null;
        OutputStream s2 = null;
        boolean verbose = true;

        try {
            s1 = new FileOutputStream(outverbosefile);
        }
        catch (IOException e) {
            System.out.println("Cannot open file file " + outverbosefile);
            System.exit(0);
        }

        DataOutputStream d1 = new DataOutputStream(s1);

        try {
            s2 = new FileOutputStream(outsummaryfile);
        }
        catch (IOException e) {
            System.out.println("Cannot open file file " + outsummaryfile);
            System.exit(0);
        }

        DataOutputStream d2 = new DataOutputStream(s2);

        try {

            //if(!verbose) {
            d2.writeBytes(
                    "File\tPC05 FP\tPC05 FN\tPC10 FP\tPC10 FN\tPC15 FP\t");
            d2.writeBytes("PC15 FN\tPC20 FP\tPC20 FN\tPC30 FP\tPC30 FN\t");
            d2.writeBytes(
                    "CCD05 FP\tCCD05 FN\tCCD10 FP\tCCD10 FN\tCCD 15 FP\t");
            d2.writeBytes(
                    "CCD15 FN\tCCD20 FP\tCCD20 FN\tCCD30 FP\tCCD 30 FN\n");
            //}

            // read in variable name and set up DataSet.
//            RectangularDataSet dataSet = DataLoaders.loadContinuousData(
//                    new File(args[0]), DelimiterType.WHITESPACE,
//                    "//"
//            );

            //int ngenes = 6;
            int ngenes = Integer.valueOf(args[2]);
            IKnowledge bk = new Knowledge2();
            bk.addToTiersByVarNames(listOfNames);

            //if(verbose) {
            d1.writeBytes("\n \n**Results for data in file yeastTRN**\n \n");
            d1.writeBytes("  Acutal adj matrix: \n");
            printAdjMatrix(yeastReg, listOfNames, d1);
            //}


            int[] PC05Accuracy;
            PC05Accuracy = PCAccuracy(0.05, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            int[] PC10Accuracy;
            PC10Accuracy = PCAccuracy(0.10, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            int[] PC15Accuracy;
            PC15Accuracy = PCAccuracy(0.15, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            int[] PC20Accuracy;
            PC20Accuracy = PCAccuracy(0.20, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            int[] PC30Accuracy;
            PC30Accuracy = PCAccuracy(0.30, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            int[] CCD05Accuracy;
            CCD05Accuracy = CcdAccuracy(0.05, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            int[] CCD10Accuracy;
            CCD10Accuracy = CcdAccuracy(0.10, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            int[] CCD15Accuracy;
            CCD15Accuracy = CcdAccuracy(0.15, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            int[] CCD20Accuracy;
            CCD20Accuracy = CcdAccuracy(0.20, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            int[] CCD30Accuracy;
            CCD30Accuracy = CcdAccuracy(0.30, ngenes, cds, bk, yeastReg,
                    listOfNames, d1, verbose);

            /*
            int[] FCI05Accuracy;
            FCI05Accuracy =
              FCIAccuracy(0.05, ngenes, cds, bk, yeastReg, listOfNames, d1, verbose);

            int[] FCI10Accuracy;
            FCI10Accuracy =
              FCIAccuracy(0.10, ngenes, cds, bk, yeastReg, listOfNames, d1, verbose);

            int[] FCI15Accuracy;
            FCI15Accuracy =
              FCIAccuracy(0.15, ngenes, cds, bk, yeastReg, listOfNames, d1, verbose);

            int[] FCI20Accuracy;
            FCI20Accuracy =
              FCIAccuracy(0.20, ngenes, cds, bk, yeastReg, listOfNames, d1, verbose);

            int[] FCI30Accuracy;
            FCI30Accuracy =
              FCIAccuracy(0.30, ngenes, cds, bk, yeastReg, listOfNames, d1, verbose);


            */

            //if(!verbose) {
            d2.writeBytes("yeastTRN \t");
            d2.writeBytes(PC05Accuracy[0] + "\t");
            d2.writeBytes(PC05Accuracy[1] + "\t");
            //}

            //if(!verbose) {
            d2.writeBytes(PC10Accuracy[0] + "\t");
            d2.writeBytes(PC10Accuracy[1] + "\t");
            //}

            //if(!verbose) {
            d2.writeBytes(PC15Accuracy[0] + "\t");
            d2.writeBytes(PC15Accuracy[1] + "\t");
            //}

            //if(!verbose) {
            d2.writeBytes(PC20Accuracy[0] + "\t");
            d2.writeBytes(PC20Accuracy[1] + "\t");
            //}

            //if(!verbose) {
            d2.writeBytes(PC30Accuracy[0] + "\t");
            d2.writeBytes(PC30Accuracy[1] + "\t");
            //}

            //if(!verbose) {
            d2.writeBytes(CCD05Accuracy[0] + "\t");
            d2.writeBytes(CCD05Accuracy[1] + "\t");
            //}

            //if(!verbose) {
            d2.writeBytes(CCD10Accuracy[0] + "\t");
            d2.writeBytes(CCD10Accuracy[1] + "\t");
            //}

            //if(!verbose) {
            d2.writeBytes(CCD15Accuracy[0] + "\t");
            d2.writeBytes(CCD15Accuracy[1] + "\t");
            //}

            //if(!verbose) {
            d2.writeBytes(CCD20Accuracy[0] + "\t");
            d2.writeBytes(CCD20Accuracy[1] + "\t");
            //}

            //if(!verbose) {
            d2.writeBytes(CCD30Accuracy[0] + "\t");
            d2.writeBytes(CCD30Accuracy[1] + "\n");
            //}

        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static int[] CcdAccuracy(double alpha, int ngenes,
            DataSet cds, IKnowledge bk, int[][] yeastReg, List names,
            DataOutputStream d, boolean v) {

        int[] falsePosNeg = new int[2];

        //CcdSearchV ccd = new CcdSearchV(new IndTestCramerT(cds, alpha), bk);
        //CcdSearchVAC ccd = new CcdSearchVAC(new IndTestCramerT(cds, alpha), bk);
        IndTestCramerT indTestCramerT = new IndTestCramerT(cds, alpha);
        Ccd ccd = new Ccd(indTestCramerT, bk);
        Graph ccdModel = ccd.search();
        int falsePositives = 0;
        int falseNegatives = 0;

        int[][] ccdModelAdj = new int[ngenes][ngenes];

        /*
        for(int i = 0; i < ngenes; i++) {
          String namei1 = (String) names.get(i);
              String namei2 = (String) names.get(i + ngenes);
              for(int j = 0; j < ngenes; j++) {

                String namej1 = (String) names.get(j);
                String namej2 = (String) names.get(j + ngenes);

                //Set adjacency matrix for CCD search
                if(ccdModel.isAdjacent(cds.get(namei1).getVariable(),
                                     cds.get(namej1).getVariable()) ||
                  ccdModel.isAdjacent(cds.get(namei1).getVariable(),
                                     cds.get(namej2).getVariable()) ||
                  ccdModel.isAdjacent(cds.get(namei2).getVariable(),
                                     cds.get(namej1).getVariable()) ||
                  ccdModel.isAdjacent(cds.get(namei2).getVariable(),
                                     cds.get(namej2).getVariable()))
                  ccdModelAdj[i][j] = 1;
                else ccdModelAdj[i][j] = 0;
              }
        }
        */

        int nvariables = names.size();
//        int ntimes = nvariables / ngenes;

        for (int i = 0; i < nvariables; i++) {
            String namei = (String) names.get(i);

            for (int j = 0; j < nvariables; j++) {
                String namej = (String) names.get(j);

                ccdModelAdj[i][j] = 0;

                Node vari = indTestCramerT.getVariable(namei);
                Node varj = indTestCramerT.getVariable(namej);

                if (!ccdModel.isAdjacentTo(vari, varj)) {
                    continue;
                }

                ccdModelAdj[i][j] = 1;

            }
        }

        for (int i = 0; i < ngenes; i++) {
            for (int j = i; j < ngenes; j++) {
                if (yeastReg[i][j] == 0 && ccdModelAdj[i][j] == 1) {
                    falsePositives++;
                }
                if (yeastReg[i][j] == 1 && ccdModelAdj[i][j] == 0) {
                    falseNegatives++;
                }
            }
        }

        falsePosNeg[0] = falsePositives;
        falsePosNeg[1] = falseNegatives;

        if (v) {
            try {
                d.writeBytes("\n \n");
                d.writeBytes("  Results of CCD search with alpha = " + alpha);
                d.writeBytes("  false+ " + falsePositives + "\t");
                d.writeBytes("false- " + falseNegatives + "\n");
                d.writeBytes("  Adjacency matrix of estimated model:  \n");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (v) {
            printAdjMatrix(ccdModelAdj, names, d);
        }

        return falsePosNeg;
    }

    private static int[] PCAccuracy(double alpha, int ngenes,
            DataSet cds, IKnowledge bk, int[][] yeastReg, List names,
            DataOutputStream d, boolean v) {

        int[] falsePosNeg = new int[2];

        IndTestCramerT indTestCramerT = new IndTestCramerT(cds, alpha);
        Pc pcs = new Pc(indTestCramerT);
        pcs.setKnowledge(bk);
        Graph pcModel = pcs.search();
        int falsePositives = 0;
        int falseNegatives = 0;

        int[][] pcModelAdj = new int[ngenes][ngenes];

        /*
        for(int i = 0; i < ngenes; i++) {
          String namei1 = (String) names.get(i);
              String namei2 = (String) names.get(i + ngenes);
              for(int j = 0; j < ngenes; j++) {

                String namej1 = (String) names.get(j);
                String namej2 = (String) names.get(j + ngenes);

                //Set adjacency matrix for PC search
                if(pcModel.isAdjacent(cds.get(namei1).getVariable(),
                                     cds.get(namej1).getVariable()) ||
                  pcModel.isAdjacent(cds.get(namei1).getVariable(),
                                     cds.get(namej2).getVariable()) ||
                  pcModel.isAdjacent(cds.get(namei2).getVariable(),
                                     cds.get(namej1).getVariable()) ||
                  pcModel.isAdjacent(cds.get(namei2).getVariable(),
                                     cds.get(namej2).getVariable()))
                  pcModelAdj[i][j] = 1;
                else pcModelAdj[i][j] = 0;
              }
        }
        */

        int nvariables = names.size();
//        int ntimes = nvariables / ngenes;

        for (int i = 0; i < nvariables; i++) {
            String namei = (String) names.get(i);

            for (int j = 0; j < nvariables; j++) {
                String namej = (String) names.get(j);

                pcModelAdj[i][j] = 0;

                Node vari = indTestCramerT.getVariable(namei);
                Node varj = indTestCramerT.getVariable(namej);

                if (!pcModel.isAdjacentTo(vari, varj)) {
                    continue;
                }

                pcModelAdj[i][j] = 1;

            }
        }

        for (int i = 0; i < ngenes; i++) {
            for (int j = i; j < ngenes; j++) {
                if (yeastReg[i][j] == 0 && pcModelAdj[i][j] == 1) {
                    falsePositives++;
                }
                if (yeastReg[i][j] == 1 && pcModelAdj[i][j] == 0) {
                    falseNegatives++;
                }
            }
        }

        falsePosNeg[0] = falsePositives;
        falsePosNeg[1] = falseNegatives;

        if (v) {
            try {
                d.writeBytes("\n \n");
                d.writeBytes("  Results of PC search with alpha = " + alpha);

                d.writeBytes("  false+ " + falsePositives + "\t");
                d.writeBytes("false- " + falseNegatives + "\n");
                d.writeBytes("  Adjacency matrix of estimated model:  \n");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (v) {
            printAdjMatrix(pcModelAdj, names, d);
        }

        return falsePosNeg;
    }

//    private static int[] FCIAccuracy(double alpha, int ngenes,
//            RectangularDataSet cds, Knowledge bk, int[][] yeastReg, List names,
//            DataOutputStream d, boolean v) {
//
//        int[] falsePosNeg = new int[2];
//
//        IndTestCramerT indTestCramerT = new IndTestCramerT(cds, alpha);
//        FciSearch fcs = new FciSearch(indTestCramerT, bk);
//        Graph fciModel = fcs.search();
//
//        int falsePositives = 0;
//        int falseNegatives = 0;
//
//        int[][] fciModelAdj = new int[ngenes][ngenes];
//
//        /*
//        for(int i = 0; i < ngenes; i++) {
//          String namei1 = (String) names.get(i);
//              String namei2 = (String) names.get(i + ngenes);
//              for(int j = 0; j < ngenes; j++) {
//
//                String namej1 = (String) names.get(j);
//                String namej2 = (String) names.get(j + ngenes);
//
//                //Set adjacency matrix for FCI search
//                if(fciModel.isAdjacent(cds.get(namei1).getVariable(),
//                                     cds.get(namej1).getVariable()) ||
//                  fciModel.isAdjacent(cds.get(namei1).getVariable(),
//                                     cds.get(namej2).getVariable()) ||
//                  fciModel.isAdjacent(cds.get(namei2).getVariable(),
//                                     cds.get(namej1).getVariable()) ||
//                  fciModel.isAdjacent(cds.get(namei2).getVariable(),
//                                     cds.get(namej2).getVariable()))
//                  fciModelAdj[i][j] = 1;
//                else fciModelAdj[i][j] = 0;
//              }
//        }
//        */
//
//        int nvariables = names.size();
//        int ntimes = nvariables / ngenes;
//
//        for (int i = 0; i < nvariables; i++) {
//            String namei = (String) names.get(i);
//            int timei = i / ngenes + 1;
//
//            for (int j = 0; j < nvariables; j++) {
//                String namej = (String) names.get(j);
//                int timej = j / ngenes + 1;
//
//                Variable vari = indTestCramerT.getVariable(namei);
//                Variable varj = indTestCramerT.getVariable(namej);
//
//                if (!fciModel.isAdjacentTo(vari, varj)) {
//                    continue;
//                }
//
//                int genei = i % ngenes;
//                int genej = j % ngenes;
//
//                fciModelAdj[genei][genej] = 0;
//
//                //If timei < timej but variablej -> variablei continue
//                //if(timei < timej && ccdModel.getEndpoint(cds.get(namej).getVariable(),
//                //cds.get(namei).getVariable()) == Endpoint.FISHER_Z) continue;
//                fciModelAdj[genei][genej] = 1;
//            }
//        }
//
//        for (int i = 0; i < ngenes; i++) {
//            for (int j = i; j < ngenes; j++) {
//                if (yeastReg[i][j] == 0 && fciModelAdj[i][j] == 1) {
//                    falsePositives++;
//                }
//                if (yeastReg[i][j] == 1 && fciModelAdj[i][j] == 0) {
//                    falseNegatives++;
//                }
//            }
//        }
//
//        falsePosNeg[0] = falsePositives;
//        falsePosNeg[1] = falseNegatives;
//
//        if (v) {
//            try {
//                d.writeBytes("\n \n");
//                d.writeBytes("  Results of FCI search with alpha = " + alpha);
//                d.writeBytes("  false+ " + falsePositives + "\t");
//                d.writeBytes("false- " + falseNegatives + "\n");
//                d.writeBytes("  Adjacency matrix of estimated model:  \n");
//            }
//            catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//
//        if (v) {
//            printAdjMatrix(fciModelAdj, names, d);
//        }
//
//        return falsePosNeg;
//    }

    private static void printAdjMatrix(int[][] adjMat, List listOfNames,
            DataOutputStream d) {
        for (int i = 0; i < adjMat.length; i++) {
            try {
                d.writeBytes("  " + listOfNames.get(i) + "\t");
                for (int j = 0; j <= i; j++) {
                    d.writeBytes(adjMat[i][j] + "\t");
                }
                d.writeBytes("\n");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}





