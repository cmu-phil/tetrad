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

package edu.pitt.dbmi.algo.bayesian.constraint.inference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 *
 * Feb 22, 2014 3:26:17 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class BayesianConstraintInference {

    private static final String CAS_FLAG = "--cas";

    private static final int NUM_REQ_ARGS = 2;

    private static final String USAGE = "java -jar bci.jar --cas <cas-file>";

    private static File casFile;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args == null || args.length != NUM_REQ_ARGS) {
            System.err.println(USAGE);
            System.exit(127);
        }

        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (flag.equals(CAS_FLAG)) {
                casFile = new File(args[++i]);
                checkFile(casFile, false);
            } else {
                System.out.printf("Unknown switch %s.\n", flag);
                System.exit(-1);
            }
        }

        try {
            int[][] dataset = readInDataset(casFile);
            int[] nodeDimension = readInNodeDimension(casFile);
            BCInference bci = new BCInference(dataset, nodeDimension);

            BCInference.OP constraint = BCInference.OP.dependent;
            int x = 3;
            int y = 5;
            int[] z = {0};  // empty set
            double pc = bci.probConstraint(constraint, x, y, z);  // returns P(node3 dependent node5 given {} | data)
            System.out.printf("Probability constraint: %7.5f\n", pc);  // if fn = 1 then 0.76510; if fn = 2 then 0.91311

            constraint = BCInference.OP.independent;
            x = 1;
            y = 4;
            z = new int[3];
            z[1] = 2;
            z[2] = 3;
            z[0] = 2;
            pc = bci.probConstraint(constraint, x, y, z);  // returns P(node1 independent node4 given {node2, node3} | data)
            System.out.printf("Probability constraint: %7.5f\n", pc);  // if fn = 1 then 0.34093; if fn = 2 then 0.35806

            constraint = BCInference.OP.independent;
            x = 1;
            y = 5;
            z = new int[2];
            z[1] = 3;
            z[0] = 1; //this is the length of the set represented by array Z.
            pc = bci.probConstraint(constraint, x, y, z);  // returns P(node1 independent node5 given {node3} | data)
            System.out.printf("Probability constraint: %7.5f\n", pc);  // if fn = 1 then 0.93535; if fn = 2 then 0.70853

//            BCInference.OP constraint = BCInference.OP.independent;
//            int x = 1;
//            int y = 5;
//            int[] z = new int[2];
//            z[0] = 1;
//            z[1] = 3;
//            double pc = bci.probConstraint(constraint, x, y, z);  // returns P(node1 independent node5 given {node3} | data)
//            System.out.printf("Probability constraint: %7.5f\n", pc);
//
//            constraint = BCInference.OP.dependent;
//            x = 3;
//            y = 5;
//            z = new int[1];
//            z[0] = 0;  // empty set
//            pc = bci.probConstraint(constraint, x, y, z);  // returns P(node3 dependent node5 given {} | data)
//            System.out.printf("Probability constraint: %7.5f\n", pc);
//
//            constraint = BCInference.OP.independent;
//            x = 1;
//            y = 4;
//            z = new int[]{2, 2, 3};
//            pc = bci.probConstraint(constraint, x, y, z);  // returns P(node1 independent node4 given {node2, node3} | data)
//            System.out.printf("Probability constraint: %7.5f\n", pc);
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    private static int[] readInNodeDimension(File dataFile) throws IOException {
        int[] nodeDimension = null;

        Pattern spaceDelim = Pattern.compile("\\s+");
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(dataFile));

            int numberOfNodes = Integer.parseInt(reader.readLine().trim());
            nodeDimension = new int[numberOfNodes + 2];
            String[] data = spaceDelim.split(reader.readLine().trim());
            int i = 0;
            for (String d : data) {
                nodeDimension[++i] = Integer.parseInt(d);
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException exception) {
                    exception.printStackTrace(System.err);
                }
            }
        }

        return nodeDimension;
    }

    private static int[][] readInDataset(File dataFile) throws IOException {
        int[][] dataset = null;

        Pattern spaceDelim = Pattern.compile("\\s+");
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(dataFile));

            int numberOfNodes = Integer.parseInt(reader.readLine().trim());
            reader.readLine();  // skip node dimension
            int numberOfCases = Integer.parseInt(reader.readLine().trim());

            dataset = new int[numberOfCases + 1][numberOfNodes + 2];
            for (int i = 1; i <= numberOfCases; i++) {
                String[] data = spaceDelim.split(reader.readLine().trim());
                int j = 0;
                for (String d : data) {
                    dataset[i][++j] = Integer.parseInt(d);
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException exception) {
                    exception.printStackTrace(System.err);
                }
            }
        }

        return dataset;
    }

    private static void checkFile(File file, boolean checkDirectory) {
        if (file.exists()) {
            if (checkDirectory) {
                if (file.isFile()) {
                    System.err.printf("%s is not a directory.", file.getName());
                    System.exit(-1);
                }
            } else {
                if (file.isDirectory()) {
                    System.err.printf("%s is not a file.", file.getName());
                    System.exit(-1);
                }
            }
        } else {
            System.err.printf("%s does not exist.", file.getName());
            System.exit(-1);
        }
    }
}


