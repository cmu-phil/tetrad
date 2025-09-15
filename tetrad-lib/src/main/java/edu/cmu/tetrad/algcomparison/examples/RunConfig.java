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

package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.Comparison;

/**
 * An example script to save out data files and graphs from a simulation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RunConfig {

    /**
     * Constructs a new instance of the Save.
     */
    public RunConfig() {

    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
        new Comparison().configuration("comparison/Config");
    }
}





