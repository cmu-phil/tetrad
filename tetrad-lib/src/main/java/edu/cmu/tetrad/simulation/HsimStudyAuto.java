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
public class HsimStudyAuto {

    /**
     * Private constructor to prevent instantiation.
     */
    private HsimStudyAuto() {
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        //***!!!!===!!!=== Parameters for the User to fill in! !!!===!!!===***
        final String readfilename = "YeastNoDupe2Slim.csv";
        final String filenameOut = "dataOutGeM.txt";
        final char delimiter = ','; //'\t';
        final int resimSize = 2;//number of variables to be resimmed
        HsimAutoRun study = new HsimAutoRun(readfilename, delimiter);
        study.setVerbose(false);//set this to true if you want HsimAutoRun to report information
        study.setWrite(true);//set this to true if you want HsimAutoRun to write the hsim data to a file
        study.setFilenameOut(filenameOut);
        study.setDelimiter(delimiter);
        study.run(resimSize);
    }
}


