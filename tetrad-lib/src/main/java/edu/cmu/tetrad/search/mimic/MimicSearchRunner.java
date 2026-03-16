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

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

/**
 * Interface for running a MIMIC-style search algorithm inside the benchmark harness.
 *
 * @author josephramsey
 */
public interface MimicSearchRunner {

    /**
     * Returns the display name of the search algorithm.
     *
     * @return the display name
     */
    String getName();

    /**
     * Runs the search algorithm on the supplied measured data using the supplied knowledge
     * and parameters.
     *
     * @param data the measured data
     * @param knowledge the tier knowledge
     * @param parameters the runtime parameters
     * @return the estimated graph
     */
    Graph run(DataSet data, Knowledge knowledge, Parameters parameters);
}