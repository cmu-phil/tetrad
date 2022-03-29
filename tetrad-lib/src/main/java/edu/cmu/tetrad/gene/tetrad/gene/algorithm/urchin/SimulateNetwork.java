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

public class SimulateNetwork {
    public static void main(String[] argv) {

        NbComponent matCBeta =
                new NbFunction(10.0, 1.0, null, null, "Mat CBeta");

        NbComponent liCl = new NbFunction(10.0, 1.0, null, null, "LiCl");

        NbComponent matOtx = new NbFunction(1.0, 1.0, null, null, "Mat Otx");

        NbComponent TCF = new NbFunction(10.0, 1.0, null, null, "TCF");

        NbComponent[] chiparents = {matCBeta};
        int[] chicauses = {1};
        NbComponent chi =
                new NbFunctionAnd(1.0, 1.0, chiparents, chicauses, "Chi");

        NbComponent chiSwitch =
                new NbFunction(10.0, 1.0, null, null, "ChiSwitch");

        NbComponent[] pcparents = {chi, chiSwitch};
        int[] pccauses = {1, 1};
        NbComponent postChi =
                new NbFunctionOr(1.0, 1.0, pcparents, pccauses, "Post Chi");

        NbComponent[] nBparents = {TCF, postChi};
        int[] nBcauses = {1, 1};
        NbComponent nB = new NbFunctionAnd(1.0, 1.0, nBparents, nBcauses, "nB");

        NbComponent[] nBmodparents = {nB};
        int[] nBmodcauses = {1};
        NbComponent nBmod =
                new NbFunctionSV(10.0, 1.0, nBmodparents, nBmodcauses, "nBmod");

        NbComponent[] wnt8parents = {nBmod};
        int[] wnt8causes = {1};
        NbComponent wnt8 = new NbGeneAnd(1.0, 1.0, wnt8parents, wnt8causes,
                "Wnt8", 0.1); // Added a s.d. JR

        NbComponent[] krlparents = {nBmod};
        int[] krlcauses = {1};
        NbComponent krl = new NbGeneAnd(100.0, 1.0, krlparents, krlcauses,
                "Krl", 0.1); // Added a s.d. JR

        NbComponent[] soxb1parents = {krl};
        int[] soxb1causes = {-1};
        NbComponent soxB1 = new NbGeneAnd(1.0, 1.0, soxb1parents, soxb1causes,
                "SoxB1", 0.1); // Added a s.d. JR

        NbComponent[] matotxmodparents = {matOtx};
        int[] matotxmodcauses = {1};
        NbComponent matOtxMod = new NbFunctionSV(10.0, 1.0, matotxmodparents,
                matotxmodcauses, "MatOtxMod");

        NbComponent[] kroxparents = {nBmod};
        int[] kroxcauses = {1};
        NbComponent krox = new NbGeneOr(100.0, 1.0, kroxparents, kroxcauses,
                "Krox", 0.1); // Added a s.d. JR

        krox.addParent(krox, 1);
        wnt8.addParent(krox, 1);

        NbComponent[] otxparents = {krox};
        int[] otxcauses = {1};
        NbComponent otx = new NbGeneOr(100.0, 1.0, otxparents, otxcauses, "Otx",
                0.1); // Added a s.d. JR

        NbComponent[] otxsumparents = {matOtxMod, otx};
        int[] otxsumcauses = {1, 1};
        NbComponent otxSum = new NbFunctionSum(1.0, 1.0, otxsumparents,
                otxsumcauses, "Otx Sum");

        otx.addParent(otxSum, 1);
        krox.addParent(otxSum, 1);

        NbComponent[] eveparents = {krox, nBmod};
        int[] evecauses = {1, 1};
        NbComponent eve = new NbGeneAnd(100.0, 1.0, eveparents, evecauses,
                "Eve", 0.1); // Added a s.d. JR

        NbComponent[] gsk3parents = {liCl, wnt8};
        int[] gsk3causes = {-1, -1};
        NbComponent GSK3 =
                new NbFunctionAnd(1.0, 1.0, gsk3parents, gsk3causes, "GSK-3");

        NbComponent[] gsk3modparents = {GSK3};
        int[] gsk3modcauses = {1};
        NbComponent GSK3Mod = new NbFunctionSV(1.0, 10.0, gsk3modparents,
                gsk3modcauses, "GSK3 Mod");

        NbComponent[] soxb1modparents = {soxB1};
        int[] soxb1modcauses = {1};
        NbComponent soxB1Mod = new NbFunctionSV(1.0, 10.0, soxb1modparents,
                soxb1causes, "SoxB1 Mod");

        NbComponent[] prechiparents = {GSK3Mod, soxB1Mod};
        int[] prechicauses = {-1, -1};
        NbComponent preChi = new NbFunctionAnd(1.0, 1.0, prechiparents,
                prechicauses, "Pre Chi");

        chi.addParent(preChi, 1);

        NbComponent[] components = {matCBeta, liCl, matOtx, TCF, chi, chiSwitch,
                postChi, nB, nBmod, wnt8, krl, soxB1, matOtxMod, otxSum, krox,
                otx, eve, GSK3, GSK3Mod, soxB1Mod, preChi};

        matCBeta.setValue(1.0);
        liCl.setValue(0.0);
        matOtx.setValue(1.0);
        TCF.setValue(1.0);

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

        double[][] data = new double[21][6];

        for (int i = 0; i < components.length; i++) {
            data[i][0] = components[i].getValue();
            System.out.print(components[i].getName() + "\t");
        }
        System.out.println();

        for (int hour = 1; hour < 6; hour++) {
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

            for (int i = 0; i < components.length; i++) {
                data[i][hour] = components[i].getValue();
            }

        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        for (int i = 0; i < components.length; i++) {
            System.out.print(components[i].getName() + "\t");
            for (int hour = 0; hour < 5; hour++) {
                String dat = nf.format(data[i][hour]);
                System.out.print(dat + "\t");
            }
            System.out.println();
        }
    }
}




