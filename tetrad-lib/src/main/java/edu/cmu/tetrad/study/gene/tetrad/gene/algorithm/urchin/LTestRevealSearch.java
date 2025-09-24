///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.urchin;

import java.io.*;
import java.util.StringTokenizer;

/**
 * This is merely a main program used to read a binarized measurement data set and to instantiate a RevealSearch and run
 * one or more search methods of that instance.
 *
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public class LTestRevealSearch {
    static int ngenes = 6;
    static int ntimes = 400;

    static int[][] cases = new int[LTestRevealSearch.ntimes][LTestRevealSearch.ngenes];

    /**
     * Private constructor.
     */
    private LTestRevealSearch() {
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

        try {
            s = new FileInputStream(fileName);
        } catch (IOException e) {
            System.out.println("Cannot open file " + fileName);
            return;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(s));
        for (int k = 0; k < LTestRevealSearch.ntimes; k++) {
            try {
                st = new StringTokenizer(in.readLine());
                for (int j = 0; j < LTestRevealSearch.ngenes; j++) {
                    LTestRevealSearch.cases[k][j] = Integer.parseInt(st.nextToken("\t"));
                }
            } catch (IOException e) {
                System.out.println("Read error in " + fileName);
                return;
            }
        }

        for (int k = 0; k < LTestRevealSearch.ntimes; k++) {
            for (int j = 0; j < LTestRevealSearch.ngenes; j++) {
                if (LTestRevealSearch.cases[k][j] == -1) {
                    LTestRevealSearch.cases[k][j] = 0;
                }
            }
        }

        String[] names = {"Gene 0", "Gene 1", "Gene 2", "Gene 3", "Gene 4"};

        RevealSearch rs = new RevealSearch(LTestRevealSearch.cases, names);

        final int lag = 1;
        rs.exhaustiveSearch(lag);

        System.out.println("Result for multiple lag search");
        final int lag1 = 1;
        final int lag2 = 2;
        rs.exhaustiveSearch(lag1, lag2);

    }

}





