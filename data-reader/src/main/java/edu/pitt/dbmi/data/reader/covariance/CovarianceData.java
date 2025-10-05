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

package edu.pitt.dbmi.data.reader.covariance;

import edu.pitt.dbmi.data.reader.Data;

import java.util.List;

/**
 * Nov 19, 2018 2:20:52 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public interface CovarianceData extends Data {

    /**
     * <p>getNumberOfCases.</p>
     *
     * @return the number of cases in the data.
     */
    int getNumberOfCases();

    /**
     * <p>getVariables.</p>
     *
     * @return the number of variables in the data.
     */
    List<String> getVariables();

    /**
     * <p>getData.</p>
     *
     * @return the data in a 2D array.
     */
    double[][] getData();

}

