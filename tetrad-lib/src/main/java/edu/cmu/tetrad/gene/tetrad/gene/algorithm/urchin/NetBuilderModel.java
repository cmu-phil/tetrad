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

import edu.cmu.tetrad.util.NumberFormatUtil;

import java.text.NumberFormat;

public class NetBuilderModel {
    public NetBuilderModel(final double[] exogenousInputs, final int nhours) {

        final NbComponent matCBeta =
                new NbFunction(10.0, 1.0, null, null, "Mat CBeta");

        final NbComponent liCl = new NbFunction(10.0, 1.0, null, null, "LiCl");

        final NbComponent matOtx = new NbFunction(1.0, 1.0, null, null, "Mat Otx");

        final NbComponent TCF = new NbFunction(10.0, 1.0, null, null, "TCF");

        final NbComponent[] chiparents = {matCBeta};
        final int[] chicauses = {1};
        final NbComponent chi =
                new NbFunctionAnd(1.0, 1.0, chiparents, chicauses, "Chi");

        final NbComponent chiSwitch =
                new NbFunction(10.0, 1.0, null, null, "ChiSwitch");

        final NbComponent[] pcparents = {chi, chiSwitch};
        final int[] pccauses = {1, 1};
        final NbComponent postChi =
                new NbFunctionOr(1.0, 1.0, pcparents, pccauses, "Post Chi");

        final NbComponent[] nBparents = {TCF, postChi};
        final int[] nBcauses = {1, 1};
        final NbComponent nB = new NbFunctionAnd(1.0, 1.0, nBparents, nBcauses, "nB");

        final NbComponent[] nBmodparents = {nB};
        final int[] nBmodcauses = {1};
        final NbComponent nBmod =
                new NbFunctionSV(10.0, 1.0, nBmodparents, nBmodcauses, "nBmod");

        final NbComponent[] wnt8parents = {nBmod};
        final int[] wnt8causes = {1};
        final NbComponent wnt8 =
                new NbGeneAnd(1.0, 1.0, wnt8parents, wnt8causes, "Wnt8", 0.1);

        final NbComponent[] krlparents = {nBmod};
        final int[] krlcauses = {1};
        final NbComponent krl =
                new NbGeneAnd(100.0, 1.0, krlparents, krlcauses, "Krl", 0.1);

        final NbComponent[] soxb1parents = {krl};
        final int[] soxb1causes = {-1};
        final NbComponent soxB1 = new NbGeneAnd(1.0, 1.0, soxb1parents, soxb1causes,
                "SoxB1", 0.1);

        final NbComponent[] matotxmodparents = {matOtx};
        final int[] matotxmodcauses = {1};
        final NbComponent matOtxMod = new NbFunctionSV(10.0, 1.0, matotxmodparents,
                matotxmodcauses, "MatOtxMod");

        final NbComponent[] kroxparents = {nBmod};
        final int[] kroxcauses = {1};
        final NbComponent krox =
                new NbGeneOr(100.0, 1.0, kroxparents, kroxcauses, "Krox", 0.1);

        krox.addParent(krox, 1);
        wnt8.addParent(krox, 1);

        final NbComponent[] otxparents = {krox};
        final int[] otxcauses = {1};
        final NbComponent otx =
                new NbGeneOr(100.0, 1.0, otxparents, otxcauses, "Otx", 0.1);

        final NbComponent[] otxsumparents = {matOtxMod, otx};
        final int[] otxsumcauses = {1, 1};
        final NbComponent otxSum = new NbFunctionSum(1.0, 1.0, otxsumparents,
                otxsumcauses, "Otx Sum");

        otx.addParent(otxSum, 1);
        krox.addParent(otxSum, 1);

        final NbComponent[] eveparents = {krox, nBmod};
        final int[] evecauses = {1, 1};
        final NbComponent eve =
                new NbGeneAnd(100.0, 1.0, eveparents, evecauses, "Eve", 0.1);

        final NbComponent[] gsk3parents = {liCl, wnt8};
        final int[] gsk3causes = {-1, -1};
        final NbComponent GSK3 =
                new NbFunctionAnd(1.0, 1.0, gsk3parents, gsk3causes, "GSK-3");

        final NbComponent[] gsk3modparents = {GSK3};
        final int[] gsk3modcauses = {1};
        final NbComponent GSK3Mod = new NbFunctionSV(1.0, 10.0, gsk3modparents,
                gsk3modcauses, "GSK3 Mod");

        final NbComponent[] soxb1modparents = {soxB1};
        final int[] soxb1modcauses = {1};
        final NbComponent soxB1Mod = new NbFunctionSV(1.0, 10.0, soxb1modparents,
                soxb1causes, "SoxB1 Mod");

        final NbComponent[] prechiparents = {GSK3Mod, soxB1Mod};
        final int[] prechicauses = {-1, -1};
        final NbComponent preChi = new NbFunctionAnd(1.0, 1.0, prechiparents,
                prechicauses, "Pre Chi");

        chi.addParent(preChi, 1);

        final NbComponent[] components = {matCBeta, liCl, matOtx, TCF, chi, chiSwitch,
                postChi, nB, nBmod, wnt8, krl, soxB1, matOtxMod, otxSum, krox,
                otx, eve, GSK3, GSK3Mod, soxB1Mod, preChi};

        final NbComponent[] genes = {wnt8, krl, soxB1, krox, otx, eve};

        matCBeta.setValue(exogenousInputs[0]);
        liCl.setValue(exogenousInputs[1]);
        matOtx.setValue(exogenousInputs[2]);
        TCF.setValue(exogenousInputs[3]);

        chi.setValue(0.0);
        chiSwitch.setValue(1.0);
        postChi.setValue(0.0);
        nB.setValue(0.0);
        nBmod.setValue(0.0);
        wnt8.setValue(0.0);
        krl.setValue(0.0);
        soxB1.setValue(0.0);
        matOtxMod.setValue(0.0);
        otxSum.setValue(0.0);
        krox.setValue(0.0);
        otx.setValue(0.0);
        eve.setValue(0.0);
        GSK3.setValue(0.0);
        GSK3Mod.setValue(0.0);
        soxB1Mod.setValue(0.0);
        preChi.setValue(0.0);

        //double[][] data = new double[21][nhours];

        final int ngenes = genes.length;
        final double[][] geneData = new double[ngenes][nhours];

        /*
        for(int i = 0; i < components.length; i++) {
          data[i][0] = components[i].getParamValue();
          System.out.print(components[i].getName() + "\t");
        }
        System.out.println();
        */

        for (int i = 0; i < ngenes; i++) {
            geneData[i][0] = genes[i].getValue();
        }

        for (int hour = 1; hour < nhours; hour++) {
            /*
            TCF.update();
            Krox.update();
            Otx.update();
            Wnt8.update();
            Krl.update();
            SoxB1.update();
            GSK3.update();
            Eve.update();
            */

            chi.update();
            //ChiSwitch.update();
            postChi.update();
            nB.update();
            nBmod.update();
            wnt8.update();
            krl.update();
            soxB1.update();
            krox.update();
            matOtxMod.update();
            //OtxSum.update();
            //Krox.update();
            otx.update();
            otxSum.update();
            eve.update();
            GSK3.update();
            GSK3Mod.update();
            soxB1Mod.update();
            preChi.update();

            //for(int i = 0; i < components.length; i++)
            //  System.out.print(components[i].getParamValue() + "\t");
            //System.out.println();

            //for(int i = 0; i < components.length; i++)
            //  data[i][hour] = components[i].getParamValue();

            for (int i = 0; i < ngenes; i++) {
                geneData[i][hour] = genes[i].getValue();
            }
        }

        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        /*
        for(int i = 0; i < components.length; i++) {
          System.out.print(components[i].getName() + "\t");
          for(int hour = 0; hour < 10; hour++) {
            String dat = nf.format(data[i][hour]);
            System.out.print(dat + "\t");
          }
          System.out.println();
        }
        */

        /*
        for(int hour = 1; hour < nhours; hour++) {
          for(int i = 0; i < ngenes; i++) {
            String dat = nf.format(geneData[i][hour]);
            System.out.print(dat + "\t");
          }
        System.out.println();
        }
        */

        final double[] means = new double[ngenes];
        for (int i = 0; i < ngenes; i++) {
            double sum = 0.0;
            for (int hour = 1; hour < nhours; hour++) {
                sum += geneData[i][hour];
            }
            means[i] = sum / (nhours - 1);
        }

        final int[] thresh = new int[ngenes];
        for (int hour = 1; hour < nhours; hour++) {
            for (int i = 0; i < ngenes; i++) {
                if (geneData[i][hour] > means[i]) {
                    thresh[i] = 1;
                } else {
                    thresh[i] = -1;
                }
            }
            if (hour % 5 == 0) {
                for (int i = 0; i < ngenes; i++) {
                    System.out.print(thresh[i] + "\t");
                }
                System.out.println();
            }
        }

    }
}




