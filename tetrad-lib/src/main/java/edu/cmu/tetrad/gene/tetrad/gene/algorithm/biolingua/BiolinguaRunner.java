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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.biolingua;

import edu.cmu.tetrad.gene.tetrad.gene.algorithm.util.SymMatrixF;

/**
 * Client of the Biolingua class, can be used to easily
 * run the algorithm with different inputs.<p>
 *
 * @author Raul Saavedra, rsaavedr@ai.uwf.edu
 */
public class BiolinguaRunner {
    static final String dashes =
            "===================================================================\n";
    static final String welcomeMsg = "\n" + BiolinguaRunner.dashes +
            "edu.cmu.gene.algorithm.biolingua.BiolinguaRunner.java    IHMC-UWF    rsaavedr@ai.uwf.edu    Jan/2002\n" +
            "Runs the Biolingua algorithm\n" + BiolinguaRunner.dashes;

    // Default inputs for the run
    static String mfname = "ltm.txt";
    static String gfname = "g.txt";
    static float ka = (float) .1;
    static float ke = (float) 3;
    static float kl = (float) 4;
    static float kp = (float) 3;

    /**
     * Displays usage information for this program (moves line options)
     */
    static void usageInfo(boolean welcome) {
        if (welcome) {
            System.out.println("\n" + BiolinguaRunner.welcomeMsg + "\n");
        }
        System.out.println(
                "\nUsage  : java edu.cmu.gene.algorithm.biolingua.BiolinguaRunner [parameters]\n\n" +
                        "Parameters:\n" +
                        "    -mfile    Name of file containing the Correlation Matrix       (def=cm.txt)\n" +
                        "    -gfile    Name of File containing the initial Graph            (def=g.txt)\n" +
                        "    -ka#      Coefficient for Annotations in eval. metric function (def=.1)\n" +
                        "    -ke#      Coefficient for Errors      in eval. metric function (def=3)\n" +
                        "    -kl#      Coefficient for Links       in eval. metric function (def=4)\n" +
                        "    -kp#      Coefficient for Predictions in eval. metric function (def=3)\n\n" +
                        "Example: java edu.cmu.gene.algorithm.biolingua.BiolinguaRunner  -mcm.txt  -gg.txt  -ka.1  -ke3  -kl4  -kp3\n\n" +
                        "  Runs biolingua with the correlation matrix in file cm.txt, starting search\n" +
                        "  from graph in file g.txt, and using coefficients .1, 3, 4, and 3.");
    }

    /**
     * Used to display the usage info and an error message
     */
    static void bailout(String errorMsg, int exitCode) {
        final String highlight =
                "***************************************************************";
        if (true) {
            BiolinguaRunner.usageInfo(false);
        }
        System.out.println(highlight + "\nError: " + errorMsg);
        if (true) {
            System.out.println("See program parameters above");
        }
        System.out.println(highlight);
        System.exit(exitCode);
    }

    public static void main(String[] args) {
        if ((args.length > 0) && (args[0].equals("/?"))) {
            // Help invoked, or no arguments -> show usage info
            BiolinguaRunner.usageInfo(true);
            System.exit(0);
        }
        // Get and validate parameters from moves line
        for (String arg : args) {
            String varg = arg.toLowerCase();
            try {
                if (varg.startsWith("-m")) {
                    BiolinguaRunner.mfname = varg.substring(2);
                    continue;
                }
                if (varg.startsWith("-g")) {
                    BiolinguaRunner.gfname = varg.substring(2);
                    continue;
                }
                if (varg.startsWith("-ka")) {
                    BiolinguaRunner.ka = Float.parseFloat(varg.substring(3));
                    continue;
                }
                if (varg.startsWith("-ke")) {
                    BiolinguaRunner.ke = Float.parseFloat(varg.substring(3));
                    continue;
                }
                if (varg.startsWith("-kl")) {
                    BiolinguaRunner.kl = Float.parseFloat(varg.substring(3));
                    continue;
                }
                if (varg.startsWith("-kp")) {
                    BiolinguaRunner.kp = Float.parseFloat(varg.substring(3));
                    continue;
                }
                BiolinguaRunner.bailout("Unrecognized parameter  " + arg, 2);
            } catch (Exception xcp) {
                BiolinguaRunner.bailout("Unable to parse value from parameter  " + arg,
                        1);
            }
        }

        try {
            SymMatrixF cm = new SymMatrixF(BiolinguaRunner.mfname);
            BiolinguaDigraph g = new BiolinguaDigraph(BiolinguaRunner.gfname);

            System.out.println(BiolinguaRunner.welcomeMsg);
            System.out.println("Inputs:");
            System.out.println("**** ka = " + BiolinguaRunner.ka);
            System.out.println("**** ke = " + BiolinguaRunner.ke);
            System.out.println("**** kl = " + BiolinguaRunner.kl);
            System.out.println("**** kp = " + BiolinguaRunner.kp);
            System.out.println("**** Correlation matrix:\n" + cm);
            System.out.println("**** Initial graph:\n" + g);

            System.out.println("Running Biolingua");
            BiolinguaDigraph result =
                    Biolingua.BiolinguaAlgorithm(cm, g, BiolinguaRunner.ka, BiolinguaRunner.ke, BiolinguaRunner.kl, BiolinguaRunner.kp);

            System.out.println("\nFinal Graph:\n" + result);
        } catch (Exception xcp) {
            System.out.println("Watch out!!!!  There was an exception:");
            xcp.printStackTrace();
        }
    }

}




