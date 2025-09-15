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

package edu.cmu.tetrad.simulation;

/**
 * Created by Erich on 3/28/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HsimStudy {

    /**
     * Private constructor to prevent instantiation.
     */
    private HsimStudy() {
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        //***!!!!===!!!=== Parameters for the User to fill in! !!!===!!!===***
        final String readfilename = "DataHsimTest2.txt";//"GeMSlim.csv";
        final String filenameOut = "dataOut2.txt";//"dataOutGeM.txt";
        final char delimiter = '\t';//',';
        String[] resimNodeNames = {"X1", "X2", "X3", "X4"};

        final boolean verbose = true;//set verbose to false to suppress System.out reports

        HsimRun.run(readfilename, filenameOut, delimiter, resimNodeNames, verbose);

    }
}


