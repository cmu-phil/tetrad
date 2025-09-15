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

import java.util.Arrays;
import java.util.List;

/**
 * Created by Erich on 4/29/2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class HsimSchedule {

    /**
     * Private constructor to prevent instantiation.
     */
    private HsimSchedule() {
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        //parameter: set of positive integers, which are resimSize values.
        List<Integer> schedule = Arrays.asList(5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);

        final boolean verbose = false;//set this to true if you want HsimautoRun to report information
        double[] evalTotal;
        evalTotal = new double[5];
        evalTotal[0] = 0;
        evalTotal[1] = 0;
        evalTotal[2] = 0;
        evalTotal[3] = 0;
        evalTotal[4] = 0;

        double[] evalIncrement;
        //evalIncrement = new double[5];

        Integer count = 1;
        for (Integer i : schedule) {
            HsimAutoRun study = new HsimAutoRun("GeMSlim.csv", ',');
            study.setWrite(true);
            study.setFilenameOut("autoout" + i + "-" + count + ".txt");
            evalIncrement = study.run(i);
            evalTotal[0] = evalTotal[0] + evalIncrement[0];
            evalTotal[1] = evalTotal[1] + evalIncrement[1];
            evalTotal[2] = evalTotal[2] + evalIncrement[2];
            evalTotal[3] = evalTotal[3] + evalIncrement[3];
            evalTotal[4] = evalTotal[4] + evalIncrement[4];
            count++;
        }
        evalTotal[0] = evalTotal[0] / (double) (count - 1);
        evalTotal[1] = evalTotal[1] / (double) (count - 1);
        evalTotal[2] = evalTotal[2] / (double) (count - 1);
        evalTotal[3] = evalTotal[3] / (double) (count - 1);
        evalTotal[4] = evalTotal[4] / (double) (count - 1);

        System.out.println("Average eval scores: " + evalTotal[0] + " " + evalTotal[1] + " " + evalTotal[2] + " " + evalTotal[3] + " " + evalTotal[4]);
    }
}

