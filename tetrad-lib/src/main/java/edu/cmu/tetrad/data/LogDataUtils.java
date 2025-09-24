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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradLogger;

/**
 * Sundry methods for logging data.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LogDataUtils {

    /**
     * Private constructor.
     */
    private LogDataUtils() {

    }

    /**
     * <p>logDataModelList.</p>
     *
     * @param info a {@link java.lang.String} object
     * @param list a {@link edu.cmu.tetrad.data.DataModelList} object
     */
    public static void logDataModelList(String info, DataModelList list) {
        TetradLogger.getInstance().log(info);

        if (list.size() == 1) {
            TetradLogger.getInstance().log("\nThere is one data set in this box.");
        } else {
            TetradLogger.getInstance().log("\nThere are " + list.size() + " data sets in this box.");
        }
    }
}




